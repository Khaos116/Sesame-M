package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aw1y2z.sesame.util.JsonUtil;

/**
 * 真实观看证据：把页面探针快照与服务端任务详情绑定后才允许构造 vv.record。
 * <p>
 * 绑定条件（全部满足，任一失败返回 null）：
 * 1. 页面 URL 的 contentId 与任务 contentId 完全一致（页级绑定）；
 * 2. 页面恰好一个 video 元素（信息流播放器复用单个元素；多元素视为歧义）；
 * 3. 观察到的 durationMs 与服务端任务 duration 偏差不超过 3 秒；
 * 4. 未暂停且 currentMs 达到 min(配置阈值, durationMs)，且不超过 durationMs+2s；
 * 5. 快照在新鲜窗口内且账号一致。
 * <p>
 * 本类不发送任何请求；记录仍由 Model 的 Run 传输层执行。移植自 GR 分支，见 doc/MyFix.md。
 */
public final class VideoWatchEvidence {
    static final long DURATION_TOLERANCE_MS = 3_000L;
    static final long CURRENT_OVERRUN_MS = 2_000L;
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    public final String contentId;
    public final String sourcePage;
    public final long currentMs;
    public final long durationMs;
    public final long thresholdMs;
    public final String account;
    public final long recordedAtMs;

    private VideoWatchEvidence(String contentId, String sourcePage, long currentMs, long durationMs,
            long thresholdMs, String account, long recordedAtMs) {
        this.contentId = contentId;
        this.sourcePage = sourcePage;
        this.currentMs = currentMs;
        this.durationMs = durationMs;
        this.thresholdMs = thresholdMs;
        this.account = account;
        this.recordedAtMs = recordedAtMs;
    }

    public static VideoWatchEvidence evaluate(JsonNode page, VideoTaskDetail task,
            long minimumMs, String account, long nowMs) {
        if (page == null || !page.isObject() || task == null) return null;
        JsonNode pageContentId = page.path("contentId");
        if (!pageContentId.isTextual() || !task.contentId.equals(pageContentId.textValue())) return null;
        JsonNode sourcePage = page.path("pagePath");
        if (!sourcePage.isTextual()) return null;
        String source = sourcePage.textValue();
        if (source.isEmpty() || source.length() > 256
                || source.indexOf('"') >= 0 || source.indexOf('\\') >= 0) return null;
        JsonNode videos = page.path("videos");
        if (!videos.isArray() || videos.size() != 1) return null;
        JsonNode row = videos.get(0);
        if (row == null || !row.isObject()) return null;
        JsonNode currentNode = row.path("currentMs");
        JsonNode durationNode = row.path("durationMs");
        if (!currentNode.isNumber() || !durationNode.isNumber()) return null;
        long current = currentNode.asLong();
        long duration = durationNode.asLong();
        if (current < 0L || duration <= 0L) return null;
        if (Math.abs(duration - task.durationMs) > DURATION_TOLERANCE_MS) return null;
        long required = Math.min(Math.max(minimumMs, 0L), duration);
        JsonNode paused = row.path("paused");
        if (paused.isBoolean() && paused.booleanValue()) return null;
        if (current < required || current > duration + CURRENT_OVERRUN_MS) return null;
        if (account == null || account.isEmpty()) return null;
        return new VideoWatchEvidence(task.contentId, source, current, duration,
                required, account, nowMs);
    }

    public ObjectNode toJson() {
        ObjectNode out = JSON.createObjectNode();
        out.put("contentId", contentId);
        out.put("sourcePage", sourcePage);
        out.put("currentMs", currentMs);
        out.put("durationMs", durationMs);
        out.put("thresholdMs", thresholdMs);
        out.put("recordedAtMs", recordedAtMs);
        return out;
    }

    /** 最新快照的有界持有者：单条、按账号、按新鲜窗口覆盖。 */
    public static final class Store {
        private static final Object LOCK = new Object();
        private static Snapshot latest;

        private Store() { }

        /** 观察者在主线程写入；只保留含 contentId 的有效快照。 */
        public static void capture(String account, JsonNode sanitizedPage, long nowMs) {
            if (account == null || account.isEmpty() || sanitizedPage == null
                    || !sanitizedPage.isObject()) return;
            JsonNode contentId = sanitizedPage.path("contentId");
            if (!contentId.isTextual() || contentId.textValue().isEmpty()
                    || contentId.textValue().length() > 256) return;
            if (sanitizedPage.toString().length() > 16384) return;
            synchronized (LOCK) {
                latest = new Snapshot(account, nowMs, sanitizedPage);
            }
        }

        /** 模型侧读取：新鲜窗口外或账号变化返回 null。 */
        public static JsonNode takeFresh(String account, long nowMs, long windowMs) {
            synchronized (LOCK) {
                if (latest == null) return null;
                if (account == null || !account.equals(latest.account)) return null;
                if (nowMs - latest.capturedAtMs > windowMs || nowMs < latest.capturedAtMs) return null;
                return latest.page;
            }
        }

        /** 记录已被消费或失效后清理。 */
        public static void clear() {
            synchronized (LOCK) {
                latest = null;
            }
        }

        private static final class Snapshot {
            final String account;
            final long capturedAtMs;
            final JsonNode page;

            Snapshot(String account, long capturedAtMs, JsonNode page) {
                this.account = account;
                this.capturedAtMs = capturedAtMs;
                this.page = page;
            }
        }
    }
}
