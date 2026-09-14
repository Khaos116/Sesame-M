package io.github.aw1y2z.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.aw1y2z.sesame.util.Log;

/** 采样公开的页面状态；不能确定服务端校验响应的含义。移植自 GR 分支，见 doc/MyFix.md。 */
final class PageSubmissionProbe {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<View, Long> BUSY = new WeakHashMap<>();
    private static String script;

    static void sample(View view, long attemptId, String stage) {
        sample(view, attemptId, stage, null);
    }

    /** 只读观察者用的变体：脱敏后的负载也会投递给 sink。 */
    static void sample(View view, long attemptId, String stage, java.util.function.Consumer<String> sink) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> sample(view, attemptId, stage, sink));
            return;
        }
        long requestTime = SystemClock.uptimeMillis();
        if (view == null || !view.isAttachedToWindow() || !view.isShown()) {
            record(attemptId, stage, "view_unavailable");
            return;
        }
        Long pending = BUSY.get(view);
        if (pending != null && requestTime - pending < 1200L) {
            record(attemptId, stage, "previous_sample_pending");
            return;
        }
        AtomicBoolean delivered = new AtomicBoolean();
        BUSY.put(view, requestTime);
        try {
            // 部分宿主 WebView 不继承 android.webkit.WebView，方法可能非 public；
            // 解析器会沿类层级查找声明方法，见 EvaluateJavascriptResolver。
            EvaluateJavascriptResolver.Binding binding = EvaluateJavascriptResolver.resolveOwner(view, node -> {
                if (!(node instanceof View)) return null;
                android.view.ViewParent parent = ((View) node).getParent();
                return parent instanceof View ? parent : null;
            });
            Method evaluate = binding == null ? null : binding.method;
            if (evaluate == null) throw new UnsupportedOperationException(
                    "no evaluateJavascript on " + view.getClass().getName()
                            + " candidates=" + EvaluateJavascriptResolver.describeCandidates(view.getClass()));
            Class<?> callbackType = evaluate.getParameterTypes()[1];
            Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(),
                    new Class<?>[]{callbackType}, (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                            if (method.getName().equals("equals")) return proxy == args[0];
                            return "SesamePageStateCallback";
                        }
                        if (method.getName().equals("onReceiveValue") && args != null && args.length == 1) {
                            Object raw = args[0];
                            MAIN.post(() -> {
                                if (!delivered.compareAndSet(false, true)) return;
                                clear(view, requestTime);
                                try {
                                    String safe = sanitize(raw);
                                    JSONObject page = new JSONObject(safe);
                                    StringBuilder fields = new StringBuilder("stage=" + stage);
                                    for (String key : new String[]{"visible", "failureText", "successText", "loadingText"}) {
                                        if (page.opt(key) instanceof Boolean) fields.append(' ').append(key).append('=').append(page.opt(key));
                                    }
                                    CaptchaDiagnostics.record(view, attemptId, "PROBE", "PAGE_STATE", fields.toString());
                                    if (sink != null) {
                                        try { sink.accept(safe); } catch (Throwable ignored) { }
                                    }
                                    record(attemptId, stage, "requestUptime=" + requestTime
                                            + " callbackUptime=" + SystemClock.uptimeMillis() + " data=" + safe);
                                } catch (Throwable badData) {
                                    record(attemptId, stage, "unreadable_page_state");
                                }
                            });
                        }
                        return null;
                    });
            evaluate.invoke(binding.receiver, loadScript(), callback);
            MAIN.postDelayed(() -> {
                if (delivered.compareAndSet(false, true)) {
                    clear(view, requestTime);
                    CaptchaDiagnostics.record(view, attemptId, "PROBE", "CALLBACK_TIMEOUT", "stage=" + stage);
                    record(attemptId, stage, "callback_timeout");
                }
            }, 1200L);
        } catch (Throwable unavailable) {
            CaptchaDiagnostics.record(view, attemptId, "PROBE", "PROBE_UNAVAILABLE",
                    "stage=" + stage + " exception=" + unavailable.getClass().getSimpleName());
            delivered.set(true);
            clear(view, requestTime);
            String message = unavailable.getMessage();
            // 只保留异常类型和有界消息；类名/候选方法名由本模块生成，不含页面内容。
            record(attemptId, stage, "probe_unavailable type=" + unavailable.getClass().getSimpleName()
                    + (message == null || message.isEmpty() ? "" : " detail=" + bound(message, 200)));
        }
    }

    private static void clear(View view, long requested) {
        Long value = BUSY.get(view);
        if (value != null && value == requested) BUSY.remove(view);
    }

    private static String bound(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String loadScript() throws Exception {
        if (script != null) return script;
        try (InputStream input = PageSubmissionProbe.class.getResourceAsStream("/sesame-page-state.js")) {
            if (input == null) throw new IllegalStateException("missing probe resource");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int size;
            while ((size = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, size);
                if (bytes.size() > 16384) throw new IllegalStateException("oversized probe");
            }
            script = bytes.toString("UTF-8");
            return script;
        }
    }

    // 绝不记录原始回调 JSON：目标可以覆盖页面全局变量或返回任意内容。
    private static String sanitize(Object raw) throws Exception {
        if (!(raw instanceof String) || ((String) raw).length() > 16384) throw new IllegalArgumentException();
        Object value = new JSONTokener((String) raw).nextValue();
        if (value instanceof String) value = new JSONTokener((String) value).nextValue();
        if (!(value instanceof JSONObject)) throw new IllegalArgumentException();
        JSONObject input = (JSONObject) value, out = new JSONObject();
        String ready = input.optString("ready");
        if (ready.matches("loading|interactive|complete")) out.put("ready", ready);
        copyKey(input, out, "pageKey");
        // 视频任务绑定字段：只是有界的标识符，绝不是完整 URL。
        String contentId = input.optString("contentId");
        if (contentId.matches("[A-Za-z0-9_.:-]{1,256}")) out.put("contentId", contentId);
        String pagePath = input.optString("pagePath");
        if (!pagePath.isEmpty() && pagePath.length() <= 256
                && pagePath.indexOf('"') < 0 && pagePath.indexOf('\\') < 0) out.put("pagePath", pagePath);
        for (String key : new String[]{"visible", "failureText", "successText", "loadingText", "resourceTimingAvailable"}) {
            if (input.opt(key) instanceof Boolean) out.put(key, input.opt(key));
        }
        copyNumber(input, out, "timeOrigin"); copyNumber(input, out, "nowMs");
        for (String arrayName : new String[]{"images", "videos", "resources"}) {
            JSONArray a = input.optJSONArray(arrayName), safe = new JSONArray();
            if (a != null) for (int i = 0; i < Math.min(a.length(), arrayName.equals("images") || arrayName.equals("videos") ? 4 : 8); i++) {
                JSONObject row = a.optJSONObject(i); if (row == null) continue;
                JSONObject entry = new JSONObject(); copyKey(row, entry, "key");
                for (String key : arrayName.equals("images") ? new String[]{"width", "height"}
                        : arrayName.equals("videos") ? new String[]{"currentMs", "durationMs"}
                        : new String[]{"startMs", "endMs", "durationMs", "httpStatus"}) copyNumber(row, entry, key);
                for (String key : arrayName.equals("videos") ? new String[]{"paused", "ended"}
                        : new String[]{"complete", "candidate"})
                    if (row.opt(key) instanceof Boolean) entry.put(key, row.opt(key));
                safe.put(entry);
            }
            out.put(arrayName, safe);
        }
        return out.toString();
    }

    private static void copyKey(JSONObject in, JSONObject out, String key) throws Exception {
        String text = in.optString(key);
        if (text.matches("[0-9a-f]{8}")) out.put(key, text);
    }

    private static void copyNumber(JSONObject in, JSONObject out, String key) throws Exception {
        Object v = in.opt(key);
        if (v instanceof Number) {
            double n = ((Number) v).doubleValue();
            if (!Double.isNaN(n) && !Double.isInfinite(n) && n >= 0 && n <= 1e15) out.put(key, n);
        }
    }

    private static void record(long attemptId, String stage, String result) {
        try { Log.record("SESAME_PAGE_STATE attempt=" + attemptId + " stage=" + stage + " " + result); }
        catch (Throwable ignored) { }
    }
}
