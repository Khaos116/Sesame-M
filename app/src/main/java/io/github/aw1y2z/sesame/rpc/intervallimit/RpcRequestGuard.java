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

    public RpcRequestGuard(RpcEntity request) {
        this.request = request;
        reportDirectory = FileUtil.getCurrentUserLogDirectory();
        state = RuntimeInfo.getInstance();
        String method = request.getRequestMethod();
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
        key = "RpcRequestGuard.v1." + identity;
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
            long until = saved.optLong("until");
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
        for (String field : new String[]{"errorMessage", "errorMsg", "resultDesc", "resultMsg", "memo"}) {
            String message = result.optString(field);
            if (!message.isEmpty()) return RpcFailurePolicy.boundedMessage(message);
        }
        return "响应未提供错误原因";
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
            if (RpcFailurePolicy.isRiskDenied(code, message) || message.contains("验证后继续")
                    || message.contains("滑动验证") || message.contains("cheating traffic")) {
                pause = RpcFailurePolicy.RISK_DENIED_MS;
                io.github.aw1y2z.sesame.hook.ApplicationHook.showVerification();
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
