package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import io.github.aw1y2z.sesame.util.*;

final class InsuredGold {
    private static final String TAG = "InsuredGold";
    private static final String[][] CENTERS = {
            {"AP16236844", "TASK_LIST", "GIFT_GOLD_NORMAL_TASK_CONTROL"},
            {"AP19236833", "TOP_LIST", "GIFT_GOLD_TOP_TASK_CONTROL"},
            {"AP19301319", "BZJ_SWAP_TASK_CONSULT_CONTROL", "BZJ_SWAP_TASK_CONSULT_CONTROL"},
            {"AP12301346", "BZJ_SOFT_TASK_CONSULT_CONTROL", "BZJ_SOFT_TASK_CONSULT_CONTROL"},
            {"AP14273842", "BZJ_XUBAO_TASK_CONSULT", ""}};

    private static JSONObject response(String raw) {
        return AntMember.memberFeaturePayload(MyUtils.newJSONObject(raw));
    }

    private static String text(JSONObject object, String key) {
        return object != null && object.opt(key) instanceof String ? object.optString(key) : "";
    }

    private static Map<String, JSONObject> available(JSONObject data) {
        JSONArray events = data == null ? null : data.optJSONArray("eventToWaitDTOList");
        if (events == null || events.length() > 100) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        JSONArray rows = new JSONArray();
        if (data.has("signInDTO") && !data.isNull("signInDTO")) {
            JSONObject sign = data.optJSONObject("signInDTO");
            if (sign == null) return null;
            rows.put(sign);
        }
        for (int i = 0; i < events.length(); i++) rows.put(events.opt(i));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String id = text(row, "sendFlowNo");
            long type = row == null ? -1 : SesameAchievements.exactNonNegative(row.opt("sendType"));
            long state = row == null ? -1 : SesameAchievements.exactNonNegative(row.opt("sendFlowStatus"));
            if (row == null || type < 0 || row.has("sendFlowStatus") && state < 0 || !id.isEmpty() && !seen.add(id)) return null;
            boolean sign = row == data.optJSONObject("signInDTO");
            if (sign && type == 1 && state < 0) return null;
            if (type != 1 || sign && state != 1 || !sign && row.has("sendFlowStatus") && state != 1) continue;
            if (id.isEmpty()) return null;
            if (row.has("disabled") && !Boolean.FALSE.equals(row.opt("disabled"))
                    || row.has("helpGain") && !Boolean.FALSE.equals(row.opt("helpGain"))) continue;
            result.put(id, row);
        }
        return result;
    }

    private static boolean collectAvailable() throws Exception {
        int attempts = 0;
        for (int round = 0; round < 3; round++) {
            TimeUtil.sleep(0);
            JSONObject data = response(AntMemberRpcCall.queryAvailableCollectInsuredGold("cfsy"));
            Map<String, JSONObject> balls = available(data);
            if (balls == null) return false;
            if (balls.isEmpty()) return true;
            boolean advanced = false;
            for (Map.Entry<String, JSONObject> entry : balls.entrySet()) {
                String id = entry.getKey(), flag = "member::insuredGoldAttempt::" + id;
                if (Status.hasFlagToday(flag)) continue;
                TimeUtil.sleep(0);
                JSONObject freshData = response(AntMemberRpcCall.queryAvailableCollectInsuredGold("cfsy"));
                Map<String, JSONObject> freshBalls = available(freshData);
                JSONObject ball = freshBalls == null ? null : freshBalls.get(id);
                if (freshBalls == null) return false;
                if (ball == null) continue;
                if (++attempts > 50) return false;
                JSONObject request = MyUtils.newJSONObject(ball.toString());
                String entrance = text(ball, "entrance");
                if (entrance.isEmpty()) entrance = text(freshData, "entrance");
                if (entrance.isEmpty()) entrance = text(freshData, "channelType");
                if (entrance.isEmpty()) entrance = "cfsy";
                if (entrance.length() > 512) return false;
                request.put("entrance", entrance).put("helpGain", false);
                if (!request.has("bizData")) request.put("bizData", MyUtils.newJSONObject());
                if (request.optJSONObject("bizData") == null) return false;
                boolean sign = ball == freshData.optJSONObject("signInDTO");
                if (sign) request.put("isSignIn", true).put("disabled", false).put("isTodayContinuousSignIn", false);
                String yuan = text(ball, "sendSumInsuredYuan");
                if (yuan.isEmpty()) yuan = text(ball, "realSendSumInsuredYuan");
                if (!yuan.isEmpty() && text(request, "showYuan").isEmpty()) request.put("showYuan", yuan);
                TimeUtil.sleep(0);
                Status.flagToday(flag);
                JSONObject accepted = response(AntMemberRpcCall.collectInsuredGold(request));
                TimeUtil.sleep(0);
                JSONObject afterData = response(AntMemberRpcCall.queryAvailableCollectInsuredGold("cfsy"));
                Map<String, JSONObject> after = available(afterData);
                boolean remains = id.equals(text(afterData == null ? null : afterData.optJSONObject("signInDTO"), "sendFlowNo"));
                JSONArray rows = afterData == null ? null : afterData.optJSONArray("eventToWaitDTOList");
                for (int i = 0; rows != null && i < rows.length(); i++) if (id.equals(text(rows.optJSONObject(i), "sendFlowNo"))) remains = true;
                JSONObject gain = accepted == null ? null : accepted.optJSONObject("gainSumInsuredDTO");
                Object amount = gain == null ? accepted == null ? null : accepted.opt("gainSumInsuredYuan") : gain.opt("gainSumInsuredYuan");
                boolean gained = false;
                if (amount instanceof String || amount instanceof Number) {
                    try { gained = new java.math.BigDecimal(amount.toString()).signum() > 0;
                    } catch (NumberFormatException ignored) { }
                }
                boolean identity = gain == null || !gain.has("sendFlowNo") || id.equals(text(gain, "sendFlowNo"));
                if (accepted == null || after == null || after.containsKey(id) || remains && !gained || !identity) {
                    Log.record("保障金：领取未确认，当天不重复[" + id + "]");
                    return false;
                }
                Log.other("保障金🏥领取状态回查成功[" + (sign ? "签到" : text(ball, "title")) + "]");
                advanced = true;
            }
            if (!advanced) return false;
            TimeUtil.sleep(0);
            if (response(AntMemberRpcCall.queryInsuredOpenAndAllow("cfsy")) == null) return false;
        }
        return true;
    }

    private static String taskId(JSONObject task) {
        String id = text(task, "taskId");
        return id.isEmpty() ? text(task == null ? null : task.optJSONObject("taskConfig"), "appletId") : id;
    }

    private static Map<String, JSONObject> tasks(String[] center) throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = response(AntMemberRpcCall.queryInsuredTaskList(center[0], center[1], center[2]));
        JSONArray rows = data == null ? null : data.optJSONArray("taskDetailList");
        if (rows == null || rows.length() > 100) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String id = taskId(row);
            if (id.isEmpty() || text(row, "taskProcessStatus").isEmpty() || result.put(id, row) != null) return null;
        }
        return result;
    }

    private static boolean confirmed(JSONObject task) {
        if (task == null) return false;
        String status = text(task, "taskProcessStatus");
        if (Set.of("SEND_SUCCESS", "RECEIVE_SUCCESS", "HAS_RECEIVED", "RECEIVED", "DONE", "COMPLETED", "COMPLETE_SUCCESS", "SUCCESS").contains(status)) return true;
        JSONArray orders = task.optJSONArray("sendPrizeSendOrderList");
        if (orders == null) return false;
        for (int i = 0; i < orders.length(); i++) if ("SUCCESS".equals(text(orders.optJSONObject(i), "sendStatus"))) return true;
        return false;
    }

    private static boolean browse(JSONObject task) {
        JSONObject display = task.optJSONObject("taskDisplayInfo"), custom = display == null ? null : display.optJSONObject("customInfo");
        String main = text(task, "taskMainType"), type = text(custom, "taskType"), action = text(custom, "taskOperationType");
        if (type.isEmpty()) type = main;
        if (action.isEmpty()) action = text(task, "operationType");
        String category = text(task, "taskCategory");
        if (category.isEmpty()) category = text(custom, "taskCategorize");
        if ("TRANSFER".equals(category) || Set.of("ISSUED_TASK", "EXPLAIN_INTELLIGENCE").contains(main)
                || Set.of("ISSUED_TASK", "EXPLAIN_INTELLIGENCE").contains(type)) return false;
        return Set.of("BROWSE_PAGE", "BROWSE_TASK").contains(main) || Set.of("BROWSE_PAGE", "BROWSE_TASK").contains(type)
                || Set.of("BROWSE_TASK", "CLICK_TASK", "NORMAL_PENDANT_CLICK_TASK").contains(action);
    }

    private static void centers(Set<String> blacklist) throws Exception {
        // ponytail: at most 50 center transitions per run; untouched tasks resume next run.
        int transitions = 0;
        for (String[] center : CENTERS) {
            for (int round = 0; round < 50; round++) {
                Map<String, JSONObject> snapshot = tasks(center);
                if (snapshot == null) return;
                boolean advanced = false;
                for (Map.Entry<String, JSONObject> entry : snapshot.entrySet()) {
                    JSONObject task = entry.getValue();
                    if (confirmed(task)) continue;
                    String id = entry.getKey(), status = text(task, "taskProcessStatus");
                    JSONObject config = task.optJSONObject("taskConfig"), display = task.optJSONObject("taskDisplayInfo");
                    JSONObject custom = display == null ? null : display.optJSONObject("customInfo");
                    String title = text(custom, "taskMainTitle");
                    if (title.isEmpty()) title = text(display, "taskMainTitle");
                    if (title.isEmpty()) title = text(config, "appletName");
                    boolean reward = Set.of("TO_RECEIVE", "WAIT_RECEIVE", "FINISHED", "COMPLETE").contains(status);
                    boolean signup = Set.of("NONE", "NONE_SIGNUP", "SIGNUP_EXPIRED").contains(status);
                    boolean send = Set.of("SIGNUP_COMPLETE", "TODO", "NOT_DONE", "WAIT_COMPLETE").contains(status);
                    if (!reward && (!browse(task) || blacklist.contains(id) || blacklist.contains(title))) continue;
                    if (!reward && !signup && !send) continue;
                    String applet = text(config, "appletId");
                    if (applet.isEmpty()) applet = text(task, "appletId");
                    if (!reward && applet.isEmpty()) continue;
                    String flag = "member::insuredTaskAttempt::" + center[0] + "::" + id + "::" + (reward ? "reward" : signup ? "signup" : "send");
                    if (Status.hasFlagToday(flag)) continue;
                    if (++transitions > 50) return;
                    TimeUtil.sleep(0);
                    Status.flagToday(flag);
                    JSONObject accepted = null;
                    if (!reward) accepted = response(AntMemberRpcCall.triggerInsuredTask(applet, center[0], center[1], signup ? "signup" : "send"));
                    if (!reward && accepted == null) return;
                    if (!signup) {
                        TimeUtil.sleep(0);
                        JSONObject consulted = response(AntMemberRpcCall.consultInsuredTask(center[0], id));
                        JSONObject detail = consulted == null ? null : consulted.optJSONObject("taskDetailWithFilterDTO");
                        if (!id.equals(taskId(detail)) || !confirmed(detail)) return;
                    }
                    Map<String, JSONObject> after = tasks(center);
                    JSONObject fresh = after == null ? null : after.get(id);
                    if (fresh == null || (signup ? !Set.of("SIGNUP_COMPLETE", "TODO", "NOT_DONE", "WAIT_COMPLETE", "TO_RECEIVE", "WAIT_RECEIVE", "FINISHED", "COMPLETE").contains(text(fresh, "taskProcessStatus")) && !confirmed(fresh) : !confirmed(fresh))) return;
                    Log.other("保障金🏥任务状态回查成功[" + id + "]");
                    advanced = true;
                    break;
                }
                if (!advanced) break;
            }
        }
    }

    static void run(Set<String> blacklist) {
        try {
            TimeUtil.sleep(0);
            if (response(AntMemberRpcCall.queryInsuredOpenAndAllowAndUpgrade("cfsy")) == null) return;
            TimeUtil.sleep(0);
            if (response(AntMemberRpcCall.queryInsuredGiftHomeRender("cfsy")) == null) return;
            TimeUtil.sleep(0);
            if (response(AntMemberRpcCall.queryInsuredOpenAndAllow("cfsy")) == null) return;
            if (!collectAvailable()) return;
            centers(blacklist);
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "run", t); }
    }
}
