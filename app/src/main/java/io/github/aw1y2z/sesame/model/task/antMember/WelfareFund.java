package io.github.aw1y2z.sesame.model.task.antMember;

import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.aw1y2z.sesame.entity.AlipayWelfareFundTaskList;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.WelfareFundTaskListMap;

/**
 * 网商银行福利金：签到与任务闭环（报名 → 完成领奖）。
 * <p>权益兑换已移除：{@code member.benefits.queryItemsInMemberV2} 在模块线程里会被宿主判为
 * {@code XRiverNotFound}（同一方法在福利金页面内调用正常），列表与兑换链路不可用。
 */
public class WelfareFund {

    private static final String TAG = WelfareFund.class.getSimpleName();

    /** 当日签到标记：服务端已签后不再重复调用 */
    private static final String FLAG_SIGN = "member::welfareFundSign";

    private static final String FLAG_TASK = "member::welfareFundTask::";

    /**
     * 福利金系接口只回 {@code success:true}（无 desc / resultCode），
     * 而 {@link MessageUtil#checkResultCode} 要求 desc="处理成功"，会把成功响应全判为失败，
     * 故以 success / isSuccess 为准再回退通用判定；勿为此改全局 checkResultCode。
     */
    private static boolean ok(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if (jo.optBoolean("success") || jo.optBoolean("isSuccess")) {
            return true;
        }
        return MessageUtil.checkResultCode(TAG, jo);
    }

    /** requestString 在请求体非法/宿主解析失败时会返回 null，统一在这里兜底 */
    private static JSONObject parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        JSONObject result = MyUtils.newJSONObject(raw);
        if (result.length() == 0) {
            Log.record("福利金：响应为空或结构无效，本轮停止该阶段");
            return null;
        }
        return result;
    }

    public static void run(boolean sign, boolean task, boolean autoBlackList, Set<String> blackList) {
        if (sign) {
            signIn();
        }
        if (task) {
            runTasks(autoBlackList, blackList);
        }
    }

    private static void signIn() {
        if (Status.hasFlagToday(FLAG_SIGN) && !DailyTask.isManual()) {
            Log.record("福利金签到：当天已经成功执行");
            return;
        }
        try {
            JSONObject jo = parse(WelfareFundRpcCall.signConsult());
            if (!ok(jo)) {
                return;
            }
            JSONObject result = jo.optJSONObject("result");
            JSONObject info = result != null ? result.optJSONObject("todaySignInfo") : null;
            if (info == null) {
                Log.record("福利金签到：今日无签到信息");
                return;
            }
            int sent = optPoint(info, "signPrizeSentPoint");
            int expect = optPoint(info, "finalPoint");
            if (info.optBoolean("signApplyDone")) {
                Log.other("福利金📅签到" + (sent > 0 ? "#获得[" + sent + "积分]" : "")
                        + (expect > 0 ? "#本轮可得[" + expect + "积分]" : ""));
                Status.flagToday(FLAG_SIGN);
            } else {
                Log.record("福利金签到未生效");
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "signIn err:", t);
        }
    }

    /** pointVO/finalPoint 等均为 {point, pointShowValue} 结构 */
    private static int optPoint(JSONObject jo, String key) {
        JSONObject po = jo.optJSONObject(key);
        return po != null ? po.optInt("point") : jo.optInt(key);
    }

    private static JSONArray queryTaskDetailList() {
        try {
            JSONObject jo = parse(WelfareFundRpcCall.taskQuery());
            if (!ok(jo)) {
                return null;
            }
            JSONObject result = jo.optJSONObject("result");
            return result != null ? result.optJSONArray("taskDetailList") : null;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "queryTaskDetailList err:", t);
            return null;
        }
    }

    private static void runTasks(boolean autoBlackList, Set<String> blackList) {
        if (DailyTask.skip("welfareFundTasks", "福利金每日任务")) return;
        try {
            JSONArray list = queryTaskDetailList();
            if (list == null) {
                Log.record("福利金任务：列表获取失败或结构无效，本轮未提交任务");
                return;
            }
            if (list.length() == 0) {
                Log.record("福利金任务：今日无可做任务");
                DailyTask.done("welfareFundTasks");
                return;
            }
            syncCandidates(list);
            Map<String, String> submitted = new LinkedHashMap<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                // 任务标题在 taskExtProps.TASK_MORPHO_DETAIL（字符串化 JSON）里
                JSONObject detail = morphDetail(item);
                String title = detail.optString("title");
                String appletId = item.optString("taskId");
                if (appletId.isEmpty() || title.isEmpty()) {
                    continue;
                }
                String state = item.optString("taskProcessStatus");
                if ("RECEIVE_SUCCESS".equals(state)) {
                    if (!Status.hasFlagToday(FLAG_TASK + appletId + "::done")) {
                        boolean sent = Status.hasFlagToday(FLAG_TASK + appletId + "::submitted");
                        Log.other((sent ? "✅ " : "☑️ ") + "福利金任务[" + title + "]｜列表已确认领奖");
                        Status.flagToday(FLAG_TASK + appletId + "::done");
                    }
                    continue;
                }
                if (blackList != null && blackList.contains(title)) {
                    Log.record("福利金任务⏭️跳过[" + title + "]#黑名单");
                    continue;
                }
                String triggerType = item.optString("sendCampTriggerType");
                if (!("USER_TRIGGER".equals(triggerType) || "EVENT_TRIGGER".equals(triggerType))) {
                    if (!Status.hasFlagToday(FLAG_TASK + appletId + "::type::" + triggerType)) {
                        Log.record("福利金任务⏭️[" + title + "]#未支持的触发类型=" + triggerType);
                        Status.flagToday(FLAG_TASK + appletId + "::type::" + triggerType);
                    }
                    continue;
                }
                // AG 的报名/发奖状态分派；SJ 的 USER_TRIGGER 另支持 TO_RECEIVE。
                if (!("NONE_SIGNUP".equals(state) || "SIGNUP_COMPLETE".equals(state)
                        || ("TO_RECEIVE".equals(state) && "USER_TRIGGER".equals(triggerType)))) {
                    if (!Status.hasFlagToday(FLAG_TASK + appletId + "::state::" + state)) {
                        Log.record("福利金任务⏭️[" + title + "]#未知状态=" + state + "，未提交请求");
                        Status.flagToday(FLAG_TASK + appletId + "::state::" + state);
                    }
                    continue;
                }
                if (Status.hasFlagToday(FLAG_TASK + appletId + "::done")) continue;
                int progress = item.optInt("periodCurrentCompleteNum", item.optInt("taskCompleteTimes", -1));
                if ("NONE_SIGNUP".equals(state)) {
                    if (!triggerTask(appletId, "signup", title, autoBlackList, progress)) continue;
                    TimeUtil.sleep(500);
                }
                if (triggerTask(appletId, "TO_RECEIVE".equals(state) ? "receive" : "send", title, autoBlackList, progress)) {
                    Status.flagToday(FLAG_TASK + appletId + "::submitted");
                    submitted.put(appletId, title);
                }
                TimeUtil.sleep(500);
            }
            // 一轮统一回查，避免每个任务单独重拉整张列表。
            if (!submitted.isEmpty()) {
                JSONArray after = queryTaskDetailList();
                for (int i = 0; after != null && i < after.length(); i++) {
                    JSONObject item = after.optJSONObject(i);
                    if (item == null || !"RECEIVE_SUCCESS".equals(item.optString("taskProcessStatus"))) continue;
                    String id = item.optString("taskId");
                    String title = submitted.remove(id);
                    if (title != null) {
                        Status.flagToday(FLAG_TASK + id + "::done");
                        Log.other("✅ 福利金任务[" + title + "]｜已按列表确认领奖");
                    }
                }
                for (String title : submitted.values()) {
                    Log.record("🕓 福利金任务[" + title + "]｜" + (after == null ? "回查失败" : "列表尚未显示已领奖")
                            + "，已受理阶段今日不重发；后续按列表状态继续");
                }
            }
            boolean complete = true;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null || item.optString("taskId").isEmpty()) { complete = false; continue; }
                String title = morphDetail(item).optString("title");
                if (!Status.hasFlagToday(FLAG_TASK + item.optString("taskId") + "::done")
                        && !(blackList != null && !title.isEmpty() && blackList.contains(title))) complete = false;
            }
            if (complete) { TimeUtil.sleep(0); DailyTask.done("welfareFundTasks"); }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "runTasks err:", t);
        }
    }

    /** 任务列表同步为黑名单候选；随每次列表请求刷新，活动任务轮换时不会让候选长期为空 */
    private static void syncCandidates(JSONArray list) {
        try {
            WelfareFundTaskListMap.load();
            int count = 0;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = morphDetail(item).optString("title");
                if (title.isEmpty() || WelfareFundTaskListMap.get(title) != null) {
                    continue;
                }
                WelfareFundTaskListMap.add(title, title);
                count++;
            }
            if (count > 0) {
                WelfareFundTaskListMap.save();
                AlipayWelfareFundTaskList.clear();
                Log.record("同步任务🉑福利金任务列表[新增" + count + "]");
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "syncCandidates err:", t);
        }
    }

    /** TASK_MORPHO_DETAIL 是字符串化 JSON，解析失败按空对象处理 */
    private static JSONObject morphDetail(JSONObject item) {
        JSONObject extProps = item.optJSONObject("taskExtProps");
        String raw = extProps != null ? extProps.optString("TASK_MORPHO_DETAIL") : "";
        if (raw.isEmpty()) {
            return MyUtils.newJSONObject();
        }
        try {
            return MyUtils.newJSONObject(raw);
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            return MyUtils.newJSONObject();
        }
    }

    private static boolean triggerTask(String appletId, String stageCode, String title, boolean autoBlackList, int progress) {
        String flag = FLAG_TASK + appletId + "::" + stageCode + "::" + progress;
        if (Status.hasFlagToday(flag + "::rejected")) return false;
        if (Status.hasFlagToday(flag)) {
            return "signup".equals(stageCode);
        }
        try {
            JSONObject jo = parse(WelfareFundRpcCall.taskTrigger(appletId, stageCode));
            if (!ok(jo)) {
                if (autoBlackList) MessageUtil.checkResultCodeAndMarkTaskBlackList("WelfareFundTaskList", title, jo);
                if (jo != null && !MessageUtil.isRetryable(jo) && !MessageUtil.isServerBusy(jo)) Status.flagToday(flag + "::rejected");
                Log.record("福利金📋[" + title + "]#" + stageCode + "未成功，code="
                        + (jo == null ? "EMPTY" : jo.optString("errorCode", jo.optString("resultCode", "UNKNOWN"))));
                return false;
            }
            JSONObject result = jo.optJSONObject("result");
            JSONObject order = result != null ? result.optJSONObject("campOrder") : null;
            String status = order != null ? order.optString("status") : "";
            if (!status.isEmpty() && !"SUCCESS".equalsIgnoreCase(status)) {
                // 非 SUCCESS 也可能是异步处理中；当天不重发，次日按列表状态继续，不据此拉黑。
                Status.flagToday(flag + "::rejected");
                Log.record("福利金📋" + stageCode + "[" + title + "]#未完成[" + status + "]");
                return false;
            }
            Status.flagToday(flag);
            Log.other("📤 福利金[" + title + "]#" + stageCode + "已受理，待列表确认" + rewardText(result));
            return true;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "taskTrigger err:", t);
            return false;
        }
    }

    /** 奖励按 prizeSendOrderList 的 budgetType/amount 汇总，字段以真机响应为准 */
    private static String rewardText(JSONObject result) {
        if (result == null) {
            return "";
        }
        JSONArray orders = result.optJSONArray("prizeSendOrderList");
        if (orders == null || orders.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orders.length(); i++) {
            JSONObject o = orders.optJSONObject(i);
            if (o != null) {
                sb.append(o.optString("budgetType")).append("=").append(o.optString("amount")).append(" ");
            }
        }
        return sb.length() == 0 ? "" : "#奖励[" + sb.toString().trim() + "]";
    }
}
