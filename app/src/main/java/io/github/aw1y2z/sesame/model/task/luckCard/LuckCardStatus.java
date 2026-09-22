package io.github.aw1y2z.sesame.model.task.luckCard;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 好运卡任务状态查询；任务动作要明确校验过负载才会真正执行。移植自 GR 分支，见 docs/MyFix.md。 */
public final class LuckCardStatus extends IsolatedRewardTask {
    private BooleanModelField inspect;
    private BooleanModelField triggerTasks;

    @Override public String getName() { return "好运卡任务状态"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(inspect = new BooleanModelField("inspect", "查询任务状态", false));
        fields.addField(triggerTasks = new BooleanModelField("triggerTasks", "执行明确好运卡任务", false));
    }

    @Override protected void execute(Run run) throws Exception {
        if (!inspect.getValue()) { Log.record(getName() + "：查询开关未开启"); return; }
        JSONObject home = run.query("com.alipay.pcreditcardweb.activity.LuckCard.consult", "[]");
        JSONObject result = home.optJSONObject("result");
        JSONObject taskInfo = result == null ? null : result.optJSONObject("taskInfo");
        String centerId = taskInfo == null ? "" : taskInfo.optString("taskCenterId", "");
        if (centerId.isEmpty()) { Log.record(getName() + "：当前没有任务中心"); return; }
        JSONObject list = run.query("com.alipay.pcreditcardweb.activity.LuckCard.queryTaskList", "[]");
        JSONArray tasks = list.optJSONArray("result");
        if (tasks == null || tasks.length() > 100) { Log.record(getName() + "：任务列表格式不明确"); return; }
        int total = 0, done = 0, signup = 0, other = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) { other++; continue; }
            total++;
            String status = task.optString("taskProcessStatus");
            if ("RECEIVE_SUCCESS".equals(status)) done++;
            else if ("NONE_SIGNUP".equals(status) || "SIGNUP_COMPLETE".equals(status)) signup++;
            else other++;
        }
        Log.record(getName() + "：任务=" + total + "，已完成=" + done + "，待处理=" + signup + "，其他=" + other);
        if (triggerTasks.getValue()) {
            for (int i = 0; i < Math.min(tasks.length(), 30); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) continue;
                String status = task.optString("taskProcessStatus", "");
                if (!"NONE_SIGNUP".equals(status) && !"SIGNUP_COMPLETE".equals(status)) continue;
                String taskId = task.optString("taskId", "");
                if (!taskId.matches("[A-Za-z0-9_.:-]{1,128}")) continue;
                final String action = taskId;
                String stage = "NONE_SIGNUP".equals(status) ? "receive" : "send";
                String userCategory = "NONE_SIGNUP".equals(status) ? "toDayNewUser" : "tomorrowUser";
                String args = "[{\"pzConfig\":{\"name\":\"任务奖励\"},\"taskCamp\":{\"appletId\":\""
                        + action + "\",\"stageCode\":\"" + stage + "\",\"taskCenId\":\"" + centerId
                        + "\"},\"userCategory\":\"" + userCategory + "\"}]";
                JSONObject actionResult = run.onceToday("taskTrigger:" + action, "com.alipay.pcreditcardweb.activity.LuckCard.taskTrigger",
                        args, () -> triggerTasks.getValue());
                if (actionResult != null) Log.record(getName() + "：任务" + action + "动作返回成功");
            }
        }
    }
}
