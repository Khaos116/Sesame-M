package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.*;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;

/** Optional merchant workflows; never opens merchant service for an unqualified account. */
public class MerchantService {
    private static final String TAG = "MerchantService";

    private static JSONObject response(String raw) {
        return AntMember.memberFeaturePayload(MyUtils.newJSONObject(raw));
    }

    private static String text(JSONObject object, String key) {
        return object != null && object.opt(key) instanceof String ? object.optString(key) : "";
    }

    private static long progress(JSONObject task) {
        Object raw = task.opt(task.has("current") ? "current" : "currentCount");
        if (!(raw instanceof Number) && !(raw instanceof String)) return -1;
        try { long value = new java.math.BigDecimal(raw.toString()).longValueExact(); return value >= 0 ? value : -1;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private static void sign() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject zcj = response(AntMemberRpcCall.merchantZcj(false));
        JSONObject button = zcj == null ? null : zcj.optJSONObject("button");
        String flag = "member::merchantZcjAttempt";
        if ("UNRECEIVED".equals(text(button, "status")) && !Status.hasFlagToday(flag)) {
            Status.flagToday(flag);
            JSONObject signed = response(AntMemberRpcCall.merchantZcj(true));
            JSONObject after = response(AntMemberRpcCall.merchantZcj(false));
            JSONObject fresh = after == null ? null : after.optJSONObject("button");
            if (signed != null && "RECEIVED".equals(text(fresh, "status"))) Log.other("商家服务🏪招财金签到回查成功");
        }
        JSONObject home = response(AntMemberRpcCall.merchantHomePage());
        flag = "member::merchantSignAttempt";
        if (home == null || !Boolean.TRUE.equals(home.opt("signIn")) || Status.hasFlagToday(flag)) return;
        Status.flagToday(flag);
        JSONObject signed = response(AntMemberRpcCall.merchantSign());
        JSONObject after = response(AntMemberRpcCall.merchantHomePage());
        if (signed != null && after != null && Boolean.FALSE.equals(after.opt("signIn"))) Log.other("商家服务🏪每日签到回查成功");
    }

    private static void kmdk() throws JSONException {
        TimeUtil.sleep(0);
        Calendar day = MyUtils.getInstance();
        String today = String.format(Locale.ROOT, "%04d%02d%02d", day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
        JSONObject activity = response(AntMemberRpcCall.merchantActivity());
        String id = text(activity, "activityNo");
        String[] parts = id.split("_", -1);
        if (parts.length < 3 || !today.equals(parts[2])) return;
        int hour = day.get(Calendar.HOUR_OF_DAY);
        String flag = "member::merchantKmdkSign::" + id;
        if (hour >= 6 && hour < 12 && "SIGN_IN_ENABLE".equals(text(activity, "signInStatus")) && !Status.hasFlagToday(flag)) {
            Status.flagToday(flag);
            JSONObject result = response(AntMemberRpcCall.merchantKmdkAction(id, true));
            JSONObject fresh = response(AntMemberRpcCall.merchantActivity());
            if (result != null && id.equals(text(fresh, "activityNo")) && "SIGN_IN_DISABLE".equals(text(fresh, "signInStatus"))) Log.other("商家服务🏪开门打卡签到回查成功");
            if (fresh == null || !id.equals(text(fresh, "activityNo"))) return;
            activity = fresh;
        }
        flag = "member::merchantKmdkSignup::" + id;
        if (!"UN_SIGN_UP".equals(text(activity, "signUpStatus")) || Status.hasFlagToday(flag)) return;
        Status.flagToday(flag);
        JSONObject result = response(AntMemberRpcCall.merchantKmdkAction(id, false));
        JSONObject after = response(AntMemberRpcCall.merchantActivity());
        if (result != null && id.equals(text(after, "activityNo")) && "SIGN_UP".equals(text(after, "signUpStatus"))) Log.other("商家服务🏪开门打卡报名回查成功");
    }

    private static Map<String, JSONObject> tasks(String[] moreOrder) throws JSONException {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (boolean service : new boolean[]{false, true}) {
            TimeUtil.sleep(0);
            JSONObject data = response(AntMemberRpcCall.merchantTaskQuery(service, service ? "" : moreOrder[0]));
            if (!service && data != null) {
                Object order = data.opt("orderTaskCode");
                if (data.has("orderTaskCode") && (!(order instanceof String) || ((String) order).length() > 512)) return null;
                moreOrder[0] = text(data, "orderTaskCode");
            }
            JSONArray rows = data == null ? null : data.optJSONArray("taskList");
            String group = service ? "SERVICE" : "MORE";
            if (rows == null || rows.length() > 100 || (!text(data, "planCode").isEmpty() && !group.equals(text(data, "planCode")))) return null;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject task = rows.optJSONObject(i);
                String code = text(task, "taskCode"), title = text(task, "title");
                if (code.isEmpty() || text(task, "status").isEmpty() || result.put(group + ":" + code, task) != null) return null;
                if (!title.isEmpty()) AntMemberTaskListMap.add(title, title);
            }
        }
        return result;
    }

    private static String actionCode(JSONObject task) {
        JSONObject button = task.optJSONObject("button"), ext = button == null ? null : button.optJSONObject("extInfo");
        String action = text(ext, "actionCode");
        if (action.isEmpty()) action = text(task, "actionCode");
        if (!action.isEmpty()) return action.endsWith("_VIEWED") ? action : action + "_VIEWED";
        String code = text(task, "taskCode");
        if (task.has("sendPointImmediately")) return code + "_VIEWED";
        switch (code) {
            case "JFLLRW_TASK": return "JFLL_VIEWED";
            case "ZFBHYLLRW_TASK": return "ZFBHYLL_VIEWED";
            case "QQKLLRW_TASK": return "QQKLL_VIEWED";
            case "SSLLRW_TASK": return "SSLL_VIEWED";
            case "CYLLRW_TASK": return "CYLLRW_VIEWED";
            case "ZMXYLLRW_TASK": return "ZMXYLL_VIEWED";
            case "ELMGYLLRW2_TASK": return "ELMGYLL_VIEWED";
            case "ZFYLLLRW_TASK": return "ZFYLLLSJ_VIEWED";
            case "SYH_CPC_DYNAMIC": return "SYH_CPC_DYNAMIC_VIEWED";
            case "RCR_RWZX_LLRW_TASK": return "rcr_llrw_VIEWED";
            case "GXYKPDDYH_TASK": return "xykhkzd_VIEWED";
            case "HHKLLRW_TASK": return "HHKLLX_VIEWED";
            case "TBNCLLRW_TASK": return "TBNCLLRW_TASK_VIEWED";
            default: return "";
        }
    }

    private static String bizId(JSONObject task) {
        JSONObject extend = task.optJSONObject("extendLog"), map = extend == null ? null : extend.optJSONObject("bizExtMap");
        return text(map, "bizId");
    }

    private static void moreTasks(Set<String> blacklist) throws JSONException {
        String[] moreOrder = {""};
        // ponytail: at most 50 verified task transitions per run; next run resumes remaining work.
        for (int round = 0; round < 50; round++) {
            TimeUtil.sleep(0);
            Map<String, JSONObject> snapshot = tasks(moreOrder);
            if (snapshot == null) return;
            boolean advanced = false;
            for (Map.Entry<String, JSONObject> entry : snapshot.entrySet()) {
                JSONObject task = entry.getValue(); String code = text(task, "taskCode"), status = text(task, "status");
                if (blacklist.contains(code) || blacklist.contains(text(task, "title")) || "SYH_RTB_SHOW_TASK_INDEX_1".equals(code)) continue;
                String ball = text(task, "pointBallId"), biz = bizId(task);
                boolean reward = "NEED_RECEIVE".equals(status) || (("PROCESSING".equals(status) || "EXCHANGE_PENDING".equals(status)) && (!ball.isEmpty() || !biz.isEmpty()));
                boolean signup = "UNRECEIVED".equals(status);
                boolean complete = "PROCESSING".equals(status) || "PROCESS".equals(status) || "WAIT_COMPLETE".equals(status) || "EXCHANGE_PENDING".equals(status);
                if (!reward && !signup && !complete) continue;
                String action = actionCode(task);
                boolean exam = "JYMWDDJF_TASK".equals(code);
                if (reward && ball.isEmpty() && biz.isEmpty()) continue;
                if (!reward && !exam && action.isEmpty()) { Log.record("商家积分任务：缺少浏览动作，跳过[" + code + "]"); continue; }
                String flag = "member::merchantTask::" + entry.getKey() + "::" + status + "::" + progress(task);
                if (Status.hasFlagToday(flag)) continue;
                JSONObject result;
                if (!reward && !signup) {
                    if (exam) {
                        JSONObject page = response(AntMemberRpcCall.merchantExamPage(code));
                        if (page == null || !Boolean.TRUE.equals(page.opt("available"))) continue;
                        action = text(page, "actionCode");
                        if (action.isEmpty()) continue;
                    } else if (response(AntMemberRpcCall.merchantActionQuery(action)) == null) continue;
                }
                TimeUtil.sleep(0);
                Status.flagToday(flag);
                if (reward) result = response(ball.isEmpty() ? AntMemberRpcCall.merchantTaskFinish(biz) : AntMemberRpcCall.merchantBallReceive(ball));
                else if (signup) result = response(AntMemberRpcCall.merchantTaskReceive(code));
                else result = response(AntMemberRpcCall.merchantActionProduce(action, exam ? "GW_MRCHSERVEBASE_DEFAULT" : ""));
                Map<String, JSONObject> after = tasks(moreOrder);
                JSONObject fresh = after == null ? null : after.get(entry.getKey());
                String next = text(fresh, "status");
                boolean terminal = "RECEIVED".equals(next) || "DONE".equals(next) || "FINISHED".equals(next) || "COMPLETE".equals(next) || "SUCCESS".equals(next);
                boolean nextActive = "PROCESSING".equals(next) || "PROCESS".equals(next) || "WAIT_COMPLETE".equals(next) || "EXCHANGE_PENDING".equals(next) || "NEED_RECEIVE".equals(next);
                boolean changed = after != null && (reward ? fresh == null || terminal : fresh != null && (terminal || (nextActive && (!status.equals(next) || (progress(task) >= 0 && progress(fresh) > progress(task))))));
                if (result == null || !changed) { Log.record("商家积分任务：操作未确认，当天不重复[" + code + "]"); return; }
                Log.other("商家积分任务🏪状态推进回查成功[" + text(task, "title") + "]");
                TimeUtil.sleep(500);
                advanced = true; break;
            }
            if (!advanced) return;
        }
    }

    private static void collectBalls() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject data = response(AntMemberRpcCall.merchantBallQuery());
        Map<String, JSONObject> balls = AntMember.memberRowsById(data == null ? null : data.optJSONArray("pointBalls"), "id");
        if (balls == null || balls.size() > 100) return;
        for (String id : balls.keySet()) {
            TimeUtil.sleep(0);
            String flag = "member::merchantBall::" + id;
            if (Status.hasFlagToday(flag)) continue;
            Status.flagToday(flag);
            JSONObject result = response(AntMemberRpcCall.merchantBallReceive(id));
            JSONObject after = response(AntMemberRpcCall.merchantBallQuery());
            Map<String, JSONObject> remaining = AntMember.memberRowsById(after == null ? null : after.optJSONArray("pointBalls"), "id");
            if (result == null || remaining == null || remaining.containsKey(id)) return;
            Log.other("商家服务🏪积分球领取回查成功[" + text(balls.get(id), "name") + "]");
        }
    }

    static void run(boolean doSign, boolean doKmdk, boolean doTasks, Set<String> blacklist) {
        if (!doSign && !doKmdk && !doTasks) return;
        try {
            TimeUtil.sleep(0);
            JSONObject eligibility = response(AntMemberRpcCall.merchantTranscodeCheck());
            if (eligibility == null || !Boolean.TRUE.equals(eligibility.opt("isOpened"))) { Log.record("商家服务：未开通或资格未知，跳过"); return; }
            if (doSign) sign();
            if (doKmdk) kmdk();
            if (doTasks) moreTasks(blacklist);
            if (doSign || doTasks) collectBalls();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "run", t); }
    }
}
