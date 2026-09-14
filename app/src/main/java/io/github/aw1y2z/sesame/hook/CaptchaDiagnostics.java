package io.github.aw1y2z.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.diagnostics.ValidationDiagnosticJournal;

/** 纯诊断观测：不代为提交、不截图、不模拟输入。移植自 GR 分支，见 doc/MyFix.md。 */
final class CaptchaDiagnostics {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String SESSION = UUID.randomUUID().toString();
    private static final Map<View, Window> WINDOWS = new WeakHashMap<>();
    private static final AtomicInteger DROPPED = new AtomicInteger();
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), runnable -> {
                Thread thread = new Thread(runnable, "captcha-diagnostics");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    static String windowId(View view) {
        if (view == null) return "unknown";
        View root = view.getRootView();
        synchronized (WINDOWS) {
            Window window = WINDOWS.get(root);
            if (window == null) { window = new Window(); WINDOWS.put(root, window); }
            return window.id;
        }
    }

    static void record(View view, long attempt, String phase, String reason, String fields) {
        long now = System.currentTimeMillis();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> recordOnMain(view, attempt, phase, reason, fields, now));
        } else recordOnMain(view, attempt, phase, reason, fields, now);
    }

    private static void recordOnMain(View view, long attempt, String phase, String reason, String fields, long now) {
        try {
            View root = view == null ? null : view.getRootView();
            String id = windowId(root);
            String state = " phase=" + phase + " reason=" + reason + " focused=" + (root != null && root.hasWindowFocus())
                    + " shown=" + (root != null && root.isShown()) + " attached=" + (root != null && root.isAttachedToWindow())
                    + " foreground=" + SimplePageManager.isAppForeground() + " " + fields;
            // 同一状态反复轮询最多每五秒记一行。
            if (root != null && ("SCAN".equals(phase) || "BLOCKED".equals(phase) || "PRESENCE".equals(phase))) {
                synchronized (WINDOWS) {
                    Window window = WINDOWS.get(root);
                    long uptime = SystemClock.uptimeMillis();
                    if (state.equals(window.lastStates.get(phase)) && uptime - window.lastTimes.getOrDefault(phase, 0L) < 5000) return;
                    window.lastStates.put(phase, state); window.lastTimes.put(phase, uptime);
                }
            }
            String event = "SESAME_PUZZLE_EVENT sessionId=" + SESSION + " windowId=" + id
                    + " version=" + io.github.aw1y2z.sesame.BuildConfig.VERSION_NAME
                    + " attempt=" + attempt + state;
            WRITER.execute(() -> {
                int dropped = DROPPED.getAndSet(0);
                try {
                    ValidationDiagnosticJournal.appendPuzzle(FileUtil.LOG_DIRECTORY_FILE, now,
                            event + " dropped=" + Math.min(999999, dropped));
                } catch (Exception ignored) { DROPPED.addAndGet(dropped + 1); }
            });
        } catch (Exception ignored) { DROPPED.incrementAndGet(); }
    }

    static String finishReason(String reason) {
        if (reason == null) return "UNKNOWN";
        if (reason.contains("超时") || reason.contains("到期")) return "TIMEOUT";
        if (reason.contains("人工") || reason.contains("手动停止")) return "MANUAL_INTERRUPTED";
        if (reason.contains("关闭")) return "WINDOW_CLOSED";
        if (reason.contains("截图") || reason.contains("画面")) return "CAPTURE_FAILED";
        if (reason.contains("位移识别")) return "MATCH_REJECTED";
        if (reason.contains("未复位")) return "SLIDER_NOT_RESET";
        if (reason.contains("轨道终点")) return "TRACK_NOT_FOUND";
        if (reason.contains("未识别到可信滑块") || reason.contains("未找到滑块")) return "NOT_DETECTED";
        if (reason.contains("坐标") || reason.contains("越界") || reason.contains("尺寸")) return "GEOMETRY_INVALID";
        if (reason.contains("拖动完成")) return "INPUT_FINISHED";
        if (reason.contains("等待点击")) return "CONTROL_STOPPED";
        if (reason.contains("失效") || reason.contains("取消") || reason.contains("过期")) return "CANCELLED";
        return "WORK_FAILED";
    }

    private static final class Window {
        final String id = UUID.randomUUID().toString();
        final Map<String, String> lastStates = new java.util.HashMap<>();
        final Map<String, Long> lastTimes = new java.util.HashMap<>();
    }
}
