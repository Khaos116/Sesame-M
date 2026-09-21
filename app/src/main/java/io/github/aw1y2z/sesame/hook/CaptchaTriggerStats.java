package io.github.aw1y2z.sesame.hook;

import android.app.Activity;
import android.app.Dialog;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * 验证码弹窗触发统计：弹出验证时记一行运行日志（类型、来源、当时运行中的模块、最近几个 RPC、界面文字），
 * 并按运行中的模块累计次数，每轮执行开头打印汇总，方便判断“哪些功能会触发弹出验证”、如何优化。
 * <p>只做观测：不点击、不拖动、不关闭弹窗。归因是推断——弹窗出现前最近的请求/正在运行的模块是嫌疑对象，
 * 不是服务端明确告知的触发者；手动在支付宝里操作触发的验证，运行中模块会显示为“无”。
 */
public final class CaptchaTriggerStats {
    private static final Pattern PUZZLE = Pattern.compile("拼图|对准|缺口|拖动|滑块|图片");
    private static final Pattern VERIFY = Pattern.compile("验证|拼图|滑块|缺口");
    private static final long DEDUP_MS = 30_000L;
    /** 本进程内按模块累计（进程重启清零；每次事件本身都在运行日志里，可以再统计） */
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();
    /** 去重签名 → 上次记录时间：多个来源（接口/Activity/弹窗）交替出现时各自独立去重 */
    private static final Map<String, Long> LAST_AT = new LinkedHashMap<>();

    private CaptchaTriggerStats() {
    }

    /** CaptchaDialog.show() 之后调用（UI 线程）。 */
    static void recordDialog(Dialog dialog) {
        try {
            StringBuilder texts = new StringBuilder();
            CaptchaHook.collectDialogInfo(dialog, texts);
            record("CaptchaDialog", texts.toString());
        } catch (Throwable t) {
            Log.printStackTrace("CaptchaTriggerStats", t);
        }
    }

    /**
     * 接口返回“请验证后继续”(1009 等)时调用（RpcRequestGuard）。不依赖界面 Hook：支付宝 10.6.58 以上
     * SimplePageManager 整体不启用（拿不到弹窗/Activity），这条是任何版本都能拿到的触发记录，
     * 且直接给出返回验证要求的接口，比弹窗时的“最近请求”更准。
     */
    public static void recordRisk(String method, String message) {
        try {
            record("RPC:" + method, "风控要求验证(接口返回)", message);
        } catch (Throwable t) {
            Log.printStackTrace("CaptchaTriggerStats", t);
        }
    }

    /** 处理器找到“向右滑动验证”文字时调用：这是最常见的验证形态，且不走 scanActivity（那条只在没找到时触发）。 */
    static void recordSlide(Activity activity, String slideText) {
        try {
            record("Activity:" + activity.getClass().getSimpleName(), slideText == null ? "向右滑动验证" : slideText);
        } catch (Throwable t) {
            Log.printStackTrace("CaptchaTriggerStats", t);
        }
    }

    /** 处理器在当前 Activity 里没找到“向右滑动验证”时调用：界面上有验证相关文字就记一条（可能是 H5 里的拼图验证）。 */
    static void scanActivity(Activity activity) {
        try {
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            if (root == null) {
                return;
            }
            StringBuilder texts = new StringBuilder();
            CaptchaHook.collectAllTextViewText(root, texts);
            if (VERIFY.matcher(texts).find()) {
                record("Activity:" + activity.getClass().getSimpleName(), texts.toString());
            }
        } catch (Throwable t) {
            Log.printStackTrace("CaptchaTriggerStats", t);
        }
    }

    private static void record(String via, String texts) {
        record(via, classify(texts), texts);
    }

    private static void record(String via, String type, String texts) {
        long now = System.currentTimeMillis();
        String sig = via + "|" + type;
        List<String> running = ModelTask.runningTaskNames();
        synchronized (CaptchaTriggerStats.class) {
            // 同一来源同一类型 30 秒内只记一次：处理器会反复重试，Activity 也会反复 resume
            Long last = LAST_AT.get(sig);
            if (last != null && now - last < DEDUP_MS) {
                return;
            }
            LAST_AT.values().removeIf(t -> now - t >= DEDUP_MS);
            LAST_AT.put(sig, now);
            if (running.isEmpty()) {
                COUNTS.merge("无运行中模块", 1, Integer::sum);
            } else {
                for (String module : running) {
                    COUNTS.merge(module, 1, Integer::sum);
                }
            }
        }
        Log.captcha("验证码弹窗🔍类型[" + type + "]#来源[" + via + "]#运行中模块["
                + (running.isEmpty() ? "无" : String.join("、", running)) + "]#最近请求["
                + RpcRequestGuard.recentRequests(5) + "]#文字["
                + StringUtil.truncate(texts.replace("\n", " | "), 240) + "]");
    }

    static String classify(String texts) {
        if (texts.contains("向右滑动验证")) {
            return "向右滑动(可自动处理)";
        }
        if (PUZZLE.matcher(texts).find()) {
            return "对准图片拼图(无法自动处理)";
        }
        return "未识别";
    }

    /** 每轮执行开头打印：本进程内各模块运行期间弹出验证的次数；没有则返回空串。 */
    static String summary() {
        synchronized (CaptchaTriggerStats.class) {
            if (COUNTS.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("验证码触发统计(本进程)：");
            boolean first = true;
            for (Map.Entry<String, Integer> e : COUNTS.entrySet()) {
                sb.append(first ? "" : "，").append(e.getKey()).append(' ').append(e.getValue()).append("次");
                first = false;
            }
            return sb.toString();
        }
    }
}
