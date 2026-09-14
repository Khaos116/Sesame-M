package io.github.aw1y2z.sesame.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.aw1y2z.sesame.data.Model;
import io.github.aw1y2z.sesame.model.task.videoRewards.VideoRewards;
import io.github.aw1y2z.sesame.model.task.videoRewards.VideoWatchEvidence;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 视频任务页被动观察者：仅在对应 Model 开启时对 H5 WebView 做有界只读采样，
 * 把含 contentId 的脱敏快照写入 {@link VideoWatchEvidence.Store}。
 * <p>
 * 本类不发送任何 RPC。发现疑似达标播放时最多每 contentId/10 分钟请求一次
 * 提前调度，由 Model 在 Run 传输层内完成账号检查、预算与每日幂等后发送记录。
 * 移植自 GR 分支，Xposed API 从传统 de.robv.android.xposed 换成 M 的
 * CompatHelpers/XC_MethodHook 封装，见 doc/MyFix.md。
 */
final class VideoPageObserver {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long SAMPLE_INTERVAL_MS = 5_000L;
    private static final int MAX_SAMPLES = 36; // 约 3 分钟，覆盖最短任务观看窗口
    private static final int MAX_VISITED_NODES = 600;
    /** 同一 contentId 的提速请求最小间隔，防止观察期间反复触发 Model 轮询。 */
    private static final long EXPEDITE_INTERVAL_MS = 10 * 60_000L;

    private static final Map<View, String> SAMPLING_VIEWS = new WeakHashMap<>();
    private static String lastExpediteAccount;
    private static String lastExpediteContentId;
    private static long lastExpediteAtMs;
    private static boolean installed;

    private VideoPageObserver() { }

    static synchronized void install() {
        if (installed) return;
        try {
            CompatHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    MAIN.postDelayed(() -> maybeStart(activity), 150L);
                }
            });
            installed = true;
        } catch (Throwable t) {
            Log.record("视频页观察Hook安装失败: " + t.getClass().getSimpleName());
        }
    }

    private static void maybeStart(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) return;
            if (!watchSamplingRequested()) return;
            String account = UserIdMap.getCurrentUid();
            if (account == null || account.isEmpty()) return;
            View webView = findWebView(activity.getWindow() == null ? null : activity.getWindow().getDecorView());
            if (webView == null || !webView.isAttachedToWindow() || !webView.isShown()) return;
            synchronized (SAMPLING_VIEWS) {
                if (account.equals(SAMPLING_VIEWS.get(webView))) return;
                SAMPLING_VIEWS.put(webView, account);
            }
            scheduleSample(webView, 0, account);
        } catch (Throwable ignored) {
            // 观察失败不影响宿主页面。
        }
    }

    private static void scheduleSample(View webView, int index, String account) {
        if (index >= MAX_SAMPLES || !webView.isAttachedToWindow()) {
            stop(webView, account);
            return;
        }
        MAIN.postDelayed(() -> {
            if (!webView.isAttachedToWindow() || !webView.isShown()
                    || !watchSamplingRequested() || !isCurrentSession(webView, account)) {
                stop(webView, account);
                return;
            }
            long minimumMs = minimumWatchMs();
            PageSubmissionProbe.sample(webView, -1L, "video_watch", sanitized -> {
                try {
                    if (!watchSamplingRequested() || !isCurrentSession(webView, account)) {
                        stop(webView, account);
                        return;
                    }
                    com.fasterxml.jackson.databind.JsonNode page =
                            io.github.aw1y2z.sesame.util.JsonUtil.copyMapper().readTree(sanitized);
                    JsonNode contentId = page.path("contentId");
                    if (!contentId.isTextual() || contentId.textValue().isEmpty()) return;
                    if (!VideoWatchEvidence.Store.capture(
                            account, page, System.currentTimeMillis(), minimumMs)) return;
                    maybeRequestExpedite(account, contentId.textValue());
                } catch (Throwable ignored) {
                }
            });
            scheduleSample(webView, index + 1, account);
        }, SAMPLE_INTERVAL_MS);
    }

    /** 播放进度达到阈值时请求提前调度；限每 contentId 十分钟一次。 */
    private static void maybeRequestExpedite(String account, String contentId) {
        long now = System.currentTimeMillis();
        synchronized (VideoPageObserver.class) {
            if (account.equals(lastExpediteAccount) && contentId.equals(lastExpediteContentId)
                    && now - lastExpediteAtMs < EXPEDITE_INTERVAL_MS) return;
            lastExpediteAccount = account;
            lastExpediteContentId = contentId;
            lastExpediteAtMs = now;
        }
        if (!expediteNextQuery(account)) return;
        Log.record("视频红包：观察到疑似达标真实播放，已请求提前调度核验 contentId长度=" + contentId.length());
    }

    private static boolean isCurrentSession(View webView, String account) {
        if (account == null || !account.equals(UserIdMap.getCurrentUid())) return false;
        synchronized (SAMPLING_VIEWS) {
            return account.equals(SAMPLING_VIEWS.get(webView));
        }
    }

    private static void stop(View webView, String account) {
        synchronized (SAMPLING_VIEWS) {
            if (account.equals(SAMPLING_VIEWS.get(webView))) SAMPLING_VIEWS.remove(webView);
        }
    }

    /** 有界深度遍历，只匹配 WebView 精确类名（含 accessibilityClassName）。 */
    private static View findWebView(View view) {
        return findWebView(view, new int[]{0});
    }

    private static View findWebView(View view, int[] visited) {
        if (view == null || visited[0] > MAX_VISITED_NODES) return null;
        visited[0]++;
        if (isWebView(view)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        int children = group.getChildCount();
        for (int i = 0; i < children; i++) {
            View match = findWebView(group.getChildAt(i), visited);
            if (match != null) return match;
        }
        return null;
    }

    private static boolean isWebView(View view) {
        String className = view.getClass().getName();
        if ("android.webkit.WebView".equals(className)
                || "com.alipay.mywebview.sdk.WebView".equals(className)) return true;
        CharSequence accessibilityClassName = view.getAccessibilityClassName();
        return accessibilityClassName != null && ("android.webkit.WebView".equals(accessibilityClassName)
                || "com.alipay.mywebview.sdk.WebView".equals(accessibilityClassName));
    }

    // 以下三个方法读取目标 Model 的实时开关；Model 未启用时本观察者完全静止。

    private static boolean watchSamplingRequested() {
        try {
            return Model.getModel(VideoRewards.class) != null && VideoRewards.watchSamplingRequested();
        } catch (Throwable unavailable) {
            return false;
        }
    }

    private static long minimumWatchMs() {
        try {
            return VideoRewards.minimumWatchMs();
        } catch (Throwable unavailable) {
            return 15_000L;
        }
    }

    private static boolean expediteNextQuery(String account) {
        try {
            return VideoRewards.expediteNextQuery(account);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
