package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestGreenLife {

    private static final String TAG = AntForestGreenLife.class.getSimpleName();
    private final AntForestV2 host;

    AntForestGreenLife(AntForestV2 host) {
        this.host = host;
    }

    /* 森林集市 */
    static void greenLife() {
        sendEnergyByAction("GREEN_LIFE");
        retrieveCurrentActivity();
    }

    static void greenRent() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            // 本轮不发能量时再请求生成会被服务端拒（SYSTEM_ERROR）：先看机会字段，直接跳过
            JSONObject chance = jo.optJSONObject("resultObject");
            if (chance == null || !Boolean.TRUE.equals(chance.opt("canSendEnergy"))) {
                Log.i(TAG, "绿色租赁⏭️未确认可发能量，本轮未请求generateEnergy");
                return;
            }
            TimeUtil.sleep(200);
            jo = MyUtils.newJSONObject(AntForestRpcCall.generateEnergy());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            JSONObject resultObject = jo.optJSONObject("resultObject");
            JSONObject energyGenerated = resultObject != null ? resultObject.optJSONObject("energyGenerated") : null;
            if (energyGenerated == null) {
                return;
            }
            int zulinshangpinliulan = energyGenerated.optInt("zulinshangpinliulan");
            Log.forest("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
            Toast.show("绿色租赁🛍️完成[线上逛街]#产生[" + zulinshangpinliulan + "g能量]");
            if (zulinshangpinliulan > 0) {
                AntForestV2.totalCollected += zulinshangpinliulan;
                Statistics.addData(Statistics.DataType.COLLECTED, zulinshangpinliulan);
            }
        } catch (Throwable t) {
            Log.err(TAG, "greenRent err:", t);
        }
    }

    static void retrieveCurrentActivity() {
        try {
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.retrieveCurrentActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            jo = jo.optJSONObject("data");
            if (jo == null || !jo.has("currentActivity")) {
                Log.i(TAG, "森林集市⏭️当前无集市活动或响应缺少data");
                return;
            }
            JSONObject currentActivity = jo.optJSONObject("currentActivity");
            if (currentActivity == null) {
                return;
            }
            int numberOfDaysCompleted = currentActivity.optInt("numberOfDaysCompleted") + 1;
            JSONObject currentTask = jo.optJSONObject("currentTask");
            if (currentTask == null || currentTask.optBoolean("checkInCompleted")) {
                return;
            }
            String taskTemplateId = currentTask.optString("taskTemplateId");
            if (taskTemplateId.isEmpty()) return;
            TaskAttemptPolicy.handle("forest::greenlife::" + taskTemplateId, "森林集市打卡", null,
                    () -> attemptGreenLifeCheckIn(taskTemplateId, numberOfDaysCompleted), Log::forest,
                    new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林集市打卡",
                            taskTemplateId, null, k -> probeGreenLifeCheckIn(taskTemplateId)).withScene("ANTFOREST"));
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "retrieveCurrentActivity err:", t);
        }
    }

    /** 森林集市打卡上报：{@code finishCurrentTask}。 */
    static Outcome attemptGreenLifeCheckIn(String taskTemplateId, int numberOfDaysCompleted) {
        try {
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.finishCurrentTask(taskTemplateId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                JSONArray ja = data == null ? null : data.optJSONArray("prizes");
                StringBuilder award = new StringBuilder();
                for (int i = 0; ja != null && i < ja.length(); i++) {
                    JSONObject prize = ja.optJSONObject(i);
                    if (prize == null) {
                        continue;
                    }
                    if (award.length() > 0) {
                        award.append(";");
                    }
                    award.append(prize.optString("name"));
                }
                Log.forest("森林集市🛍️打卡[坚持" + numberOfDaysCompleted + "天]"
                        + (award.length() > 0 ? "#获得[" + award + "]" : ""));
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            if (TaskAlternative.hit(jo, null)) {
                return Outcome.UNSUPPORTED;
            }
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "attemptGreenLifeCheckIn err:", t);
        }
        return Outcome.RETRY;
    }

    /** 森林集市打卡探针：重拉活动信息，读 {@code currentTask.checkInCompleted}。 */
    static TaskAttemptPolicy.ProbeResult probeGreenLifeCheckIn(String taskTemplateId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.retrieveCurrentActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONObject currentTask = data == null ? null : data.optJSONObject("currentTask");
            if (currentTask == null || currentTask.optString("taskTemplateId").isEmpty()
                    || !(currentTask.opt("checkInCompleted") instanceof Boolean)) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            if (!taskTemplateId.equals(currentTask.optString("taskTemplateId"))) {
                return TaskAttemptPolicy.ProbeResult.GONE;
            }
            return currentTask.optBoolean("checkInCompleted", false)
                    ? TaskAttemptPolicy.ProbeResult.RECEIVED : TaskAttemptPolicy.ProbeResult.TODO;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "probeGreenLifeCheckIn err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    static void sendEnergyByAction(String sourceType) {
        try {
            String dayTag = "forest::greenLifeSendEnergy::" + sourceType;
            if (Status.hasFlagToday(dayTag)) return;
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.consultForSendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !Boolean.TRUE.equals(data.opt("canSendEnergy"))) {
                if (data != null && Boolean.FALSE.equals(data.opt("canSendEnergy"))
                        && "USER_SEND_OVER_LIMIT".equals(data.optString("resultCode"))) {
                    Status.flagToday(dayTag);
                    return;
                }
                // 服务端不允许本轮发送（如当日额度用尽 USER_SEND_OVER_LIMIT）：不再请求发送
                Log.i(TAG, "森林集市⏭️[" + sourceType + "]本次不可发送能量["
                        + (data == null ? "缺少data" : data.optString("resultCode", "")) + "]，跳过");
                return;
            }
            jo = MyUtils.newJSONObject(GreenLifeRpcCall.sendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            data = jo.optJSONObject("data");
            if (data == null || !Boolean.TRUE.equals(data.opt("canSendEnergy"))) {
                // 只记录业务状态，不输出完整响应。
                Log.i(TAG, "森林集市⏭️[" + sourceType + "]本次未确认发能量#"
                        + (data == null ? "缺少data" : data.optString("resultCode", "")));
                return;
            }
            int receivedEnergyAmount = data.optInt("receivedEnergyAmount", -1);
            if (receivedEnergyAmount <= 0) {
                Log.i(TAG, "森林集市本轮未确认有效到账能量，停止后续发送");
                return;
            }
            Log.forest("森林集市🛍️完成[线上逛街]#本次#产生[" + receivedEnergyAmount + "g能量]");
            Toast.show("森林集市🛍️完成[线上逛街]#本次#产生[" + receivedEnergyAmount + "g能量]");
            if (receivedEnergyAmount > 0) {
                AntForestV2.totalCollected += receivedEnergyAmount;
                Statistics.addData(Statistics.DataType.COLLECTED, receivedEnergyAmount);
                // 确认到账后才算当天已完成（线上逛街每天仅可完成一次）
                Status.flagToday(dayTag);
            }
        } catch (Throwable t) {
            Log.err(TAG, "sendEnergyByAction err:", t);
        }
    }
}
