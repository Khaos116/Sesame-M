package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** vv.record 请求前，服务端视频任务的严格只读视图。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoTaskDetail {
    public final String contentId;
    public final String rewardParams;
    public final String taskType;
    public final long durationMs;
    public final boolean completed;
    private VideoTaskDetail(String contentId, String rewardParams, String taskType,
            long durationMs, boolean completed) {
        this.contentId = contentId; this.rewardParams = rewardParams; this.taskType = taskType;
        this.durationMs = durationMs; this.completed = completed;
    }

    /** 只接受带正数有界时长与服务端奖励数据的 taskData 对象。 */
    public static VideoTaskDetail parse(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid video task response");
        JsonNode root = JsonUtil.copyMapper().readTree(response);
        JsonNode data = root == null ? null : root.path("taskData");
        if (data == null || !data.isObject()) throw new IOException("missing taskData");
        String contentId = text(data, "contentId");
        String reward = text(data, "rewardParams");
        String taskType = text(data, "taskType");
        long duration = data.path("duration").isIntegralNumber() ? data.path("duration").longValue() : -1L;
        boolean completed = data.path("completed").isBoolean() && data.path("completed").booleanValue();
        if (!contentId.matches("[A-Za-z0-9_.:-]{1,256}") || reward.isEmpty() || reward.length() > 65536
                || duration <= 0 || duration > 24L * 60 * 60) throw new IOException("incomplete video task");
        return new VideoTaskDetail(contentId, reward, taskType, duration * 1000L, completed);
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value != null && value.isTextual() ? value.textValue() : "";
    }
}
