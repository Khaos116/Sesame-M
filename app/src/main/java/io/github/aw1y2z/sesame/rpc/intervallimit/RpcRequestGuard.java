package io.github.aw1y2z.sesame.rpc.intervallimit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.diagnostics.RpcFailureJournal;

/** Shared by all RPC transports; state belongs to the account that sent the request. */
public final class RpcRequestGuard {
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final Set<String> INVALID_RECORDS = new HashSet<>(Arrays.asList(
            "2026010358596942583", "2026012058541320399", "2026012058542045915",
            "2026012058542176083", "2026012058543269012", "2026012058542511985"));

    private final RpcEntity request;
    private final java.io.File reportDirectory;
    private final RuntimeInfo state;
    private final String key;
    private final boolean core;
    private final boolean knownUnsupported;

    /**
     * “请验证后继续”的暂停只放内存，不写 RuntimeInfo：重启支付宝（进程重建）或切换账号（TaskLifecycle 代数变化）
     * 就失效，请求重新发出即可再次弹出验证；持久化会把它带过重启，用户没法通过重启/换号重新验证。
     */
    private static final java.util.Map<String, long[]> VERIFY_PAUSE = new java.util.HashMap<>();
    private static final long VERIFY_PAUSE_MS = 5 * MINUTE;

    /** 验证已通过（自动滑块成功）时调用：立即解除所有验证暂停。 */
    public static void clearVerifyPause() {
        synchronized (RpcRequestGuard.class) {
            VERIFY_PAUSE.clear();
        }
    }

    private long verifyUntil() {
        long[] item = VERIFY_PAUSE.get(key);
        return item != null && item[1] == io.github.aw1y2z.sesame.data.task.TaskLifecycle.generation() ? item[0] : 0L;
    }

    private static final java.util.ArrayList<Object[]> RECENT = new java.util.ArrayList<>();
    private static final int RECENT_MAX = 8;

    private static void noteRecent(String method) {
        synchronized (RECENT) {
            RECENT.add(new Object[]{method, System.currentTimeMillis()});
            if (RECENT.size() > RECENT_MAX) {
                RECENT.remove(0);
            }
        }
    }

    /** 最近发出的 n 个请求（新的在前），如 “a.b.c(2秒前) < d.e.f(9秒前)”，给验证码弹窗归因用。 */
    public static String recentRequests(int n) {
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        synchronized (RECENT) {
            for (int i = RECENT.size() - 1; i >= 0 && RECENT.size() - i <= n; i--) {
                if (sb.length() > 0) {
                    sb.append(" < ");
                }
                Object[] item = RECENT.get(i);
                sb.append(item[0]).append('(').append(Math.max(0, (now - (Long) item[1]) / 1000)).append("秒前)");
            }
        }
        return sb.length() == 0 ? "无" : sb.toString();
    }

    public RpcRequestGuard(RpcEntity request) {
        this.request = request;
        reportDirectory = FileUtil.getCurrentUserLogDirectory();
        state = RuntimeInfo.getInstance();
        String method = request.getRequestMethod();
        noteRecent(method);
        JSONObject args;
        try {
            args = new JSONArray(request.getRequestData()).optJSONObject(0);
        } catch (Exception ignored) {
            args = null;
        }
        if (args == null) args = MyUtils.newJSONObject("{}");
        String scene = args.optString("sceneCode");
        core = method.contains("antforest") || method.contains(".forest.")
                || (method.startsWith("com.alipay.antfarm.") && !method.contains("orchard"))
                || "com.alipay.antfarm.orchardRecallAnimal".equals(method)
                || (method.startsWith("com.alipay.reading.game.dadaDaily.") && "100".equals(args.optString("activityId")))
                || scene.startsWith("ANTFOREST") || scene.startsWith("ANTFARM");
        // Random outBizNo / timestamps must not let retries escape the same task's pause.
        JSONArray identity = new JSONArray().put(method);
        for (String field : new String[]{"sceneCode", "taskSceneCode", "taskType", "bizKey",
                "bizkey", "bizSubType", "taskId", "recordId", "groupId", "activityId"}) {
            identity.put(args.optString(field));
        }
        // Preserve existing keys for requests without sceneId.
        String sceneId = args.optString("sceneId");
        if (!sceneId.isEmpty()) identity.put("sceneId").put(sceneId);
        // enterFarm serves own farm and every friend farm; a friend's failure must not pause own farm.
        if ("com.alipay.antfarm.enterFarm".equals(method)) {
            identity.put("userId").put(args.optString("userId")).put("farmId").put(args.optString("farmId"));
        }
        // v2：换前缀让旧版本写入的 24 小时暂停（风控/验证、人气大爆发误判等）整体作废，不再读取
        key = "RpcRequestGuard.v2." + identity;
        knownUnsupported = isKnownUnsupported(method, args);
    }

    static boolean isKnownUnsupported(String method, JSONObject args) {
        if (MyUtils.closeUnRpc()) {
            if ("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity".equals(method)
                    && INVALID_RECORDS.contains(args.optString("recordId"))) return true;
            if ("alipay.antmember.biz.rpc.membertask.h5.executeTask".equals(method)
                    && "ngfe_tag__ptr3o4eriu".equals(args.optString("bizSubType"))) return true;
            if ("com.alipay.antfarm.doFarmTask".equals(method)
                    && ("_chouchoulechoukuan".equals(args.optString("bizKey"))
                    || "TAO_GOLDEN_V2".equals(args.optString("bizKey")))) return true;
            if ("com.alipay.antiep.finishTask".equals(method)
                    && "ANTOCEAN_TASK".equals(args.optString("sceneCode"))
                    && "mokuai_senlin_hydrw".equals(args.optString("taskType"))) return true;
        }
        if (MyUtils.closeVerification()) {
            if ("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.queryCoinTaskPanel".equals(method)
                    || "com.alipay.sportshealth.biz.rpc.sportsHealthHomeRpc.queryEnergyBubbleModule".equals(method)
                    || "alipay.mobile.ipsponsorprod.consume.gold.task.signin.calendar".equals(method)) return true;
            if ("com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask".equals(method)
                    && "SHOW_AD".equals(args.optString("taskAction"))
                    && "AP12300610".equals(args.optString("taskId"))) return true;
        }
        return MyUtils.closeErrorFunction()
                && ("alipay.antmember.biz.rpc.membertask.h5.signPageTaskList".equals(method)
                || "com.alipay.wealthgoldtwa.goldbill.v2.index.collect".equals(method)
                || "alipay.tiyubiz.wenti.walk.participate".equals(method));
    }

    public boolean shouldSkip() {
        synchronized (RpcRequestGuard.class) {
            JSONObject saved = MyUtils.newJSONObject(state.getString(key));
            long until = Math.max(saved.optLong("until"), verifyUntil());
            if (!knownUnsupported && until <= System.currentTimeMillis()) return false;
            String reason = knownUnsupported ? "跳过GR已知异常任务" : "请求异常暂停中，剩余"
                    + Math.max(1, (until - System.currentTimeMillis()) / 1000) + "秒";
            JSONObject result = MyUtils.newJSONObject("{\"success\":false,\"error\":\"RPC_SKIPPED\",\"resultCode\":\"RPC_SKIPPED\"}");
            try {
                result.put("resultDesc", reason).put("errorMessage", reason).put("memo", reason);
            } catch (Exception ignored) { }
            request.setResponseObject(result, result.toString());
            request.setError();
            return true;
        }
    }

    public static String errorMessage(JSONObject result) {
        for (String field : new String[]{"errorMessage", "errorMsg", "resultDesc", "resultMsg", "memo", "resultView"}) {
            String message = result.optString(field);
            if (!message.isEmpty()) return RpcFailurePolicy.boundedMessage(message);
        }
        return "响应未提供错误原因";
    }

    /** 非验证类的风控拒绝（如“访问被拒绝”）：30 分钟起，一天内连续出现再加长。 */
    private static long riskPause(int failures) {
        return failures <= 1 ? 30 * MINUTE : failures == 2 ? 120 * MINUTE : 360 * MINUTE;
    }

    private static long busyPause(int failures) {
        return failures < 3 ? 5 * MINUTE : 30 * MINUTE;
    }

    static boolean isBusy(String message) {
        return message.contains("人气大爆发") || message.contains("系统繁忙") || message.contains("请稍后再试");
    }

    /** 提示当前账号需要去开通/认证才能用的文案（如庄园肥料罐 G04“肥料已经存满了，去开通芭芭农场种果树吧”）。只匹配对用户本人的提示，不含“好友未开通”这类针对他人的状态。 */
    static boolean isNotOpened(String message) {
        // 好友互动接口的请求键不含目标好友，“对方未实名认证”若命中会把整个接口对所有好友停一天
        if (message.contains("好友") || message.contains("对方")) return false;
        for (String marker : new String[]{"去开通", "请先开通", "请先认证", "请先实名", "未认证", "未实名"}) {
            if (message.contains(marker)) return true;
        }
        return false;
    }

    public static boolean isFailure(JSONObject result) {
        String error = result.optString("error");
        if (!error.isEmpty() && !"0".equals(error)) return true;
        if (result.has("success") || result.has("isSuccess")) {
            return !result.optBoolean("success") && !result.optBoolean("isSuccess");
        }
        if (result.has("retCode")) return !"0".equals(result.optString("retCode"));
        if (result.has("resultCode")) {
            String code = result.optString("resultCode");
            return !"SUCCESS".equalsIgnoreCase(code) && !"100".equals(code) && !"200".equals(code);
        }
        // Unrecognized response shapes are left to the business parser, not blacklisted.
        return result.length() == 0;
    }

    public static boolean isNonFriend(String method, JSONObject result) {
        return "com.alipay.antfarm.enterFarm".equals(method)
                && "非好友".equals(result.optString("memo"))
                && "302".equals(result.optString("resultCode"))
                && "0".equals(result.optString("error", "0"));
    }

    public void recordTransportFailure() {
        record(MyUtils.newJSONObject("{\"error\":\"TRANSPORT_ERROR\",\"errorMessage\":\"RPC请求失败或超时\"}"));
    }

    public void recordTransportFailure(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        String message = cause.getMessage();
        JSONObject result = MyUtils.newJSONObject("{\"error\":\"TRANSPORT_ERROR\"}");
        try {
            result.put("errorMessage", message == null ? cause.getClass().getSimpleName() : message);
            if (message != null && (message.contains("[1009]") || message.contains("访问被拒绝"))) {
                result.put("error", "1009");
            } else if (message != null && message.contains("登录超时")) {
                result.put("error", "2000");
            }
        } catch (Exception ignored) { }
        record(result);
    }

    public void record(JSONObject result) {
        synchronized (RpcRequestGuard.class) {
            long now = System.currentTimeMillis();
            try {
                RpcFailureJournal.record(reportDirectory, request.getRequestMethod(),
                        request.getRequestData(), result, now);
            } catch (Exception e) {
                Log.i("异常请求统计写入失败：" + e.getClass().getSimpleName());
            }
            if (verifyUntil() > now) return;
            JSONObject saved = MyUtils.newJSONObject(state.getString(key));
            // An older in-flight success must not cancel a pause imposed by a newer failure.
            if (saved.optLong("until") > now) return;
            if (!isFailure(result)) {
                if (saved.length() > 0 && (result.has("success") || result.has("isSuccess")
                        || result.has("retCode") || result.has("resultCode"))) state.put(key, "{}");
                return;
            }
            String code = result.optString("error");
            if (code.isEmpty() || "0".equals(code)) code = result.optString("resultCode");
            if ("2000".equals(code) || "RPC_SKIPPED".equals(code)) return;
            String message = errorMessage(result);
            int failures = now - saved.optLong("last") < DAY ? saved.optInt("failures") + 1 : 1;
            long pause = 0;
            boolean needVerify = message.contains("验证") || message.contains("cheating traffic");
            if (RpcFailurePolicy.isRiskDenied(code, message) || message.contains("验证后继续")
                    || message.contains("滑动验证") || message.contains("cheating traffic")) {
                if (needVerify) {
                    // 只在提示要验证时拉起支付宝；09-18 日报里 neverland 的 1009 是“系统繁忙”，不需要验证
                    VERIFY_PAUSE.put(key, new long[]{now + VERIFY_PAUSE_MS,
                            io.github.aw1y2z.sesame.data.task.TaskLifecycle.generation()});
                    io.github.aw1y2z.sesame.hook.CaptchaTriggerStats.recordRisk(request.getRequestMethod(), message);
                    io.github.aw1y2z.sesame.hook.ApplicationHook.showVerification();
                    Log.record("请求保护⏸️" + request.getRequestMethod() + "#" + message
                            + "#暂停" + VERIFY_PAUSE_MS / MINUTE + "分钟（仅本次运行，重启支付宝或切换账号后恢复）");
                    return;
                } else {
                    pause = isBusy(message) ? busyPause(failures) : riskPause(failures);
                }
            } else if (isNotOpened(message)) {
                // 该账号没开通/未认证的功能（小号开不了），服务端每次都会拒绝，一天只请求一次
                pause = DAY;
            } else if ("48".equals(code) || "TRANSPORT_ERROR".equals(code)) {
                pause = core ? (failures < 3 ? MINUTE : 5 * MINUTE)
                        : (failures == 1 ? 5 * MINUTE : failures == 2 ? 30 * MINUTE : DAY);
            } else if (RpcFailurePolicy.kind(code) == RpcFailurePolicy.Kind.SYSTEM_ERROR
                    || ("com.alipay.antfarm.receiveFarmTaskAward".equals(request.getRequestMethod())
                    && "102".equals(code) && message.startsWith("服务器正在开小差"))) {
                // Same task id busy 5+ times in a day (seen daily on DAILY_DRAW_TIMES tasks): retry every 6h, not every run.
                boolean farmAward = "com.alipay.antfarm.receiveFarmTaskAward".equals(request.getRequestMethod());
                pause = core ? (failures < 3 ? 5 * MINUTE : farmAward && failures >= 5 ? 6 * 60 * MINUTE : 30 * MINUTE)
                        : RpcFailurePolicy.SYSTEM_ERROR_MS;
            } else if (!core && isBusy(message)) {
                // 服务端临时繁忙（“人气大爆发，请稍后再试”等）：短退避，不能因为连续 3 次就停一天；核心接口本就不因普通失败暂停
                pause = busyPause(failures);
            } else if (!core && failures >= 3) {
                pause = DAY;
            }
            // Ordinary farm/forest failures (full feed, already claimed, etc.) stay eligible.
            if (core && pause == 0) return;
            try {
                saved.put("last", now).put("failures", failures).put("until", now + pause);
                state.put(key, saved.toString());
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            if (pause > 0) Log.record("请求保护⏸️" + request.getRequestMethod() + "#" + message
                    + "#暂停" + pause / MINUTE + "分钟（仅当前账号对应请求）");
        }
    }
}
