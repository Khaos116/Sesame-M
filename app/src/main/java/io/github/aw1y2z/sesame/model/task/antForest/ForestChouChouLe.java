package io.github.aw1y2z.sesame.model.task.antForest;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.ForestHuntIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class ForestChouChouLe {

    private static final String TAG = ForestChouChouLe.class.getSimpleName();

    /** 能量奖励的数值写在 prizeName 里（如 188g能量），prizeNum 只是份数 */
    private static final Pattern ENERGY_PRIZE_PATTERN = Pattern.compile("(\\d+)g能量");

    void chouChouLe(Boolean ForestHuntDraw, Boolean ForestHuntHelp, Set<String> shareIds, Boolean NORMALForestHuntHelp, Boolean ACTIVITYForestHuntHelp, Set<String> AntForestHuntTaskList) {
        try {
            ForestHuntIdMap.load();
            // String source = "task_entry";
            // String source = "guide";
            // String source = "forestchouchoule";
            JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.enterDrawActivityopengreen("", "ANTFOREST_NORMAL_DRAW", "task_entry"));
            if (!MessageUtil.checkSuccess(TAG, resData)) {
                return;
            }
            // 提取drawSceneGroups数组
            JSONArray drawSceneGroups = resData.optJSONArray("drawSceneGroups");
            for (int i = 0; drawSceneGroups != null && i < drawSceneGroups.length(); i++) {
                JSONObject drawScene = drawSceneGroups.optJSONObject(i);
                JSONObject drawActivity = drawScene != null ? drawScene.optJSONObject("drawActivity") : null;
                if (drawActivity == null) {
                    continue;
                }

                String activityId = drawActivity.optString("activityId");
                String drawScenename = drawActivity.optString("name");
                String sceneCode = drawActivity.optString("sceneCode");

                chouChouLescene(ForestHuntDraw, activityId, drawScenename, sceneCode, ForestHuntHelp, shareIds, NORMALForestHuntHelp, ACTIVITYForestHuntHelp, AntForestHuntTaskList);
            }
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    void chouChouLescene(Boolean ForestHuntDraw, String activityId, String drawScenename, String sceneCode, Boolean ForestHuntHelp, Set<String> shareIds, Boolean NORMALForestHuntHelp, Boolean ACTIVITYForestHuntHelp, Set<String> AntForestHuntTaskList) {
        String taskUid = UserIdMap.getCurrentUid();
        try {
            boolean doublecheck;

            // =====================================================

            int loopCount = 0; // 循环次数计数
            final int MAX_LOOP = 7; // 最大循环次数，避免死循环
            // 本轮已做过的任务：重拉列表后不再重做（服务端状态推进慢，重拉时它可能仍是待办，
            // 不去重会把每批任务重跑 MAX_LOOP 遍；跨场景不共享，各场景独立计数）
            Set<String> handledThisRun = new LinkedHashSet<>();

      /*
      sceneCode
      ANTFOREST_NORMAL_DRAW_TASK
      ANTFOREST_ACTIVITY_DRAW_TASK

      taskType
      FOREST_NORMAL_DRAW_SHARE
      FOREST_ACTIVITY_DRAW_SHARE

      p2pSceneCode
      FOREST_NORMAL_20250829_SHARE
      FOREST_NORMAL_20251024_SHARE
       */
            do {
                doublecheck = false;
                JSONObject listTaskopengreen = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
                if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                    JSONArray taskList = listTaskopengreen.optJSONArray("taskInfoList");
                    for (int i = 0; taskList != null && i < taskList.length(); i++) {
                        JSONObject taskInfo = taskList.optJSONObject(i);
                        JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                        if (taskBaseInfo == null) {
                            continue;
                        }
                        JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                        String taskName = bizInfo.optString("title");
                        String desc = bizInfo.optString("desc");

                        String taskSceneCode = taskBaseInfo.optString("sceneCode");
                        String taskStatus = taskBaseInfo.optString("taskStatus");
                        String taskType = taskBaseInfo.optString("taskType");

                        JSONObject taskRights = taskInfo.optJSONObject("taskRights");
                        int rightsTimes = taskRights != null ? taskRights.optInt("rightsTimes") : 0;
                        int rightsTimesLimit = taskRights != null ? taskRights.optInt("rightsTimesLimit") : 0;
                        boolean vitalityExchange = taskType.equals("NORMAL_DRAW_EXCHANGE_VITALITY");
                        if (vitalityExchange && (taskSceneCode.isEmpty() || taskRights == null
                                || !(taskRights.opt("rightsTimes") instanceof Number)
                                || !(taskRights.opt("rightsTimesLimit") instanceof Number)
                                || rightsTimes < 0 || rightsTimesLimit <= 0 || rightsTimes > rightsTimesLimit)) {
                            Log.forest("森林寻宝🧾[" + taskName + "]兑换进度缺失或无效，本轮不兑换/领奖");
                            continue;
                        }
                        String claimKey = "forest::chouchoule::exchange::award::" + taskSceneCode
                                + "/" + taskType + "#" + rightsTimes;

                        // 已完成任务领取奖励
                        if (taskStatus.equals("FINISHED")) {
                            if (vitalityExchange && (Status.hasFlagToday(claimKey) || handledThisRun.contains(taskType)
                                    || !handledThisRun.add("award::" + taskType + "#" + rightsTimes))) continue;
                            TimeUtil.sleep(2000);
                            JSONObject sginRes = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAwardopengreen("task_entry", taskSceneCode, taskType));
                            boolean claimed;
                            if (MessageUtil.checkSuccess(TAG, sginRes)) {
                                claimed = true;
                                int incAwardCount = sginRes.optInt("incAwardCount");
                                Log.forest("森林寻宝🎖️[" + taskName + "]获得抽奖*" + incAwardCount);
                                if (rightsTimesLimit - rightsTimes > 0) {
                                    doublecheck = true;
                                }
                            } else {
                                // 领奖收口：先按任务列表复核"已领到"，未确认才交自动拉黑（顺序由 TaskAward 固定）
                                claimed = TaskAward.confirmReceivedOrBlackList("森林寻宝🎖️",
                                        k -> probeChouChouLeStatus(sceneCode, taskSceneCode, taskType, -1), taskName,
                                        taskName,
                                        () -> MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestHuntTaskList", taskName, sginRes),
                                        msg -> Log.forest(msg));
                            }
                            if (vitalityExchange && claimed) Status.flagToday(claimKey, taskUid);
                            continue;
                        }

                        //黑名单任务跳过
                        if (AntForestHuntTaskList.contains(taskName)) {
                            continue;
                        }

                        if (taskType.contains("_DRAW_SHARE") && ForestHuntHelp) {
                            // if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                            int forestHuntHelpTodayCount = Status.getforestHuntHelpToday(taskType);
                            if (forestHuntHelpTodayCount < shareIds.size()) {
                                JSONObject prodPlayParam = MyUtils.newJSONObject(taskBaseInfo.optString("prodPlayParam"));
                                String p2pSceneCode = prodPlayParam.optString("p2pSceneCode");
                                Log.forest("森林寻宝🎰️执行[" + drawScenename + "]助力好友");
                                DoForestHuntHelp(shareIds, activityId, p2pSceneCode, taskType);
                            }
                        }
                        // 在最后一个任务时强制开启助力
                        if (i == (taskList.length() - 1)) {
                            if (NORMALForestHuntHelp && sceneCode.equals("ANTFOREST_NORMAL_DRAW")) {
                                int forestHuntHelpTodayCount = Status.getforestHuntHelpToday("FOREST_NORMAL_DRAW_SHARE");
                                if (forestHuntHelpTodayCount < shareIds.size()) {
                                    // if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                                    Log.forest("森林寻宝🎰️执行[普通场景]助力好友(薅羊毛，如果助力结果不返回成功请关闭配置项)");
                                    DoForestHuntHelp(shareIds, activityId, "FOREST_NORMAL_20250829_SHARE", "FOREST_NORMAL_DRAW_SHARE");
                                }
                            }
                            if (ACTIVITYForestHuntHelp && sceneCode.equals("ANTFOREST_ACTIVITY_DRAW")) {
                                int forestHuntHelpTodayCount = Status.getforestHuntHelpToday("FOREST_ACTIVITY_DRAW_SHARE");
                                if (forestHuntHelpTodayCount < shareIds.size()) {
                                    // if (!Status.hasFlagToday("Forest::" + sceneCode)) {
                                    Log.forest("森林寻宝🎰️执行[活动场景]助力好友(薅羊毛，如果助力结果不返回成功请关闭配置项)");
                                    DoForestHuntHelp(shareIds, activityId, "FOREST_NORMAL_20251024_SHARE", "FOREST_ACTIVITY_DRAW_SHARE");
                                }
                            }
                        }
                        // 周期/持续型任务：跳过，不尝试也不拉黑
                        if (TaskAttemptPolicy.isCyclicTask(taskBaseInfo)) {
                            // 周期任务常驻列表、由真实行为推进，每轮结果相同；静默跳过
                            continue;
                        }
                        // 本轮已处理过：重拉列表时不再重做（见 handledThisRun 说明）
                        if (!handledThisRun.add(taskType)) {
                            continue;
                        }
                        // ==================== 活力值兑换任务 =====================
                        if (vitalityExchange && (taskStatus.equals("TODO") || taskStatus.equals("RECEIVED"))) {
                            // 一次补齐：按剩余次数连做。额度以服务端 rightsTimes/rightsTimesLimit 为准
                            // （它才是权威，本任务每天固定 2 次），本地只记"本轮已做几次"用于换 key 去重。
                            // 每次兑换消耗 20 活力值，故每次前都重查活力值。
                            String exchangeKey = "forest::chouchoule::exchangeTotal::" + taskSceneCode + "/" + taskType;
                            int doneToday = Math.max(rightsTimes, Status.getIntFlagToday(exchangeKey));
                            int remain = Math.min(60, Math.max(0, rightsTimesLimit - doneToday));
                            for (int cnt = 0; cnt < remain; cnt++) {
                                if (cnt > 0) {
                                    TaskAttemptPolicy.ProbeResult state = probeChouChouLeStatus(sceneCode, taskSceneCode, taskType, -1);
                                    if (state != TaskAttemptPolicy.ProbeResult.TODO && state != TaskAttemptPolicy.ProbeResult.RECEIVED) {
                                        Log.forest("森林寻宝🧾[" + taskName + "]上一档状态=" + state + "，本轮暂停后续兑换");
                                        break;
                                    }
                                }
                                int totalVitalityAmount = 0;
                                try {
                                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryVitalityStoreIndex());
                                    if (!MessageUtil.checkResultCode(TAG, jo) || !jo.has("userVitalityInfoVO")) {
                                        break;
                                    }
                                    JSONObject userVitalityInfo = jo.optJSONObject("userVitalityInfoVO");
                                    if (userVitalityInfo == null) break;
                                    totalVitalityAmount = userVitalityInfo.optInt("totalVitalityAmount", 0);
                                } catch (Throwable th) {
                                    if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
                                    Log.err(TAG, "chouChouLesceneEXCHANGE err:", th);
                                    break;
                                }
                                if (totalVitalityAmount < 20) {
                                    Log.forest("森林寻宝🧾活力值" + totalVitalityAmount + "不能兑换");
                                    break;
                                }
                                Outcome exchangeOutcome = attemptExchangeVitality(activityId, sceneCode, taskSceneCode, taskType, taskName);
                                if (exchangeOutcome != Outcome.DONE) {
                                    break; // 做不了就收手，不再空转剩余次数
                                }
                                doublecheck = true;
                                doneToday++;
                                Status.setIntFlagToday(exchangeKey, doneToday);
                                // 每档先领奖再继续兑换；1/2已领取是中间态，不是整项完成。
                                String loopClaimKey = "forest::chouchoule::exchange::award::" + taskSceneCode
                                        + "/" + taskType + "#" + doneToday;
                                if (Status.hasFlagToday(loopClaimKey)) continue;
                                TimeUtil.sleep(1500);
                                JSONObject award = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAwardopengreen(
                                        "task_entry", taskSceneCode, taskType));
                                if (MessageUtil.checkSuccess(TAG, award)) {
                                    int count = award.optInt("incAwardCount", -1);
                                    Log.forest("森林寻宝🎖️[" + taskName + "]"
                                            + (count > 0 ? "获得抽奖*" + count : "领奖接口已受理"));
                                } else if (!TaskAward.confirmReceivedOrBlackList("森林寻宝🎖️",
                                        k -> probeChouChouLeStatus(sceneCode, taskSceneCode, taskType, -1),
                                        taskName, taskName, null, Log::forest)) {
                                    Log.forest("森林寻宝🧾[" + taskName + "]兑换后领奖未确认，本轮不继续兑换");
                                    break;
                                }
                                Status.flagToday(loopClaimKey, taskUid);
                                TimeUtil.sleep(1000);
                            }
                            continue; // 防止进入下面的 FOREST_NORMAL_DRAW 分支
                        }

                        // 统一处理 FOREST_NORMAL_DRAW 和 FOREST_ACTIVITY_DRAW开头任务
                        if ((taskType.startsWith("FOREST_NORMAL_DRAW") || taskType.startsWith("FOREST_ACTIVITY_DRAW")) && taskStatus.equals("TODO")) {
                            // 一次补齐：同一轮内按剩余次数连做，打满即止
                            int remain = rightsTimesLimit > 0 ? Math.min(60, Math.max(0, rightsTimesLimit - rightsTimes)) : 1;
                            for (int cnt = 0; cnt < remain; cnt++) {
                                // 待领奖、已领取或状态不明时交回外层处理，不继续上报。
                                if (cnt > 0 && probeChouChouLeStatus(sceneCode, taskSceneCode, taskType, -1) != TaskAttemptPolicy.ProbeResult.TODO) break;
                                TimeUtil.sleep(1000);
                                // 三条腿见 chouChouLeFinishTask（XLIGHT=广告类先走 4Chouchoule，其余先走 opengreen）
                                if (!chouChouLeFinishTask(taskType, taskSceneCode, taskName, taskType.contains("XLIGHT"), sceneCode, rightsTimes + cnt)) {
                                    break; // 做不了就收手，不再空转剩余次数
                                }
                                doublecheck = true;
                            }
                            continue;
                        }
                        if (taskStatus.equals("TODO")) {
                            // 一次补齐：同一轮内按剩余次数连做，打满即止（与庄园抽抽乐同一形态）
                            // 兜底完成任务操作（三条腿见 chouChouLeFinishTask，非 XLIGHT 先走 opengreen）
                            int remain = rightsTimesLimit > 0 ? Math.min(60, Math.max(0, rightsTimesLimit - rightsTimes)) : 1;
                            for (int cnt = 0; cnt < remain; cnt++) {
                                // 待领奖、已领取或状态不明时交回外层处理，不继续上报。
                                if (cnt > 0 && probeChouChouLeStatus(sceneCode, taskSceneCode, taskType, -1) != TaskAttemptPolicy.ProbeResult.TODO) break;
                                TimeUtil.sleep(1000);
                                if (!chouChouLeFinishTask(taskType, taskSceneCode, taskName, false, sceneCode, rightsTimes + cnt)) {
                                    break; // 做不了就收手，不再空转剩余次数
                                }
                                doublecheck = true;
                            }
                        }
                    }
                }
            } while (doublecheck && ++loopCount < MAX_LOOP);

            // ==================== 执行抽奖 ====================
            if (ForestHuntDraw) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.enterDrawActivityopengreen(activityId, sceneCode, "task_entry"));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject drawAsset = jo.optJSONObject("drawAsset");
                    int blance = drawAsset != null ? drawAsset.optInt("blance") : 0;

                    final int MAX_DRAW_LOOP = 100;
                    int drawLoop = 0;
                    // 成功但余额不降时也要能退出，避免无上限连发
                    while (blance > 0 && ++drawLoop <= MAX_DRAW_LOOP) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.drawopengreen(activityId, sceneCode, "task_entry", UserIdMap.getCurrentUid()));
                        if (MessageUtil.checkSuccess(TAG, jo)) {
                            drawAsset = jo.optJSONObject("drawAsset");
                            blance = drawAsset != null ? drawAsset.optInt("blance") : 0;
                            JSONObject prizeVO = jo.optJSONObject("prizeVO");
                            if (prizeVO == null) {
                                break;
                            }
                            String prizeName = prizeVO.optString("prizeName");
                            int prizeNum = prizeVO.optInt("prizeNum");
                            Log.forest("森林寻宝🎁领取[" + prizeName + "*" + prizeNum + "]");
                            Toast.show("森林寻宝🎁领取[" + prizeName + "*" + prizeNum + "]");
                            if (prizeName.contains("g能量")) {
                                // 记能量值而非份数，否则寻宝奖励会按"个数"入账（实测每天少记数百克）
                                Matcher energyMatcher = ENERGY_PRIZE_PATTERN.matcher(prizeName);
                                if (energyMatcher.find()) {
                                    Statistics.addData(Statistics.DataType.COLLECTED, Integer.parseInt(energyMatcher.group(1)) * prizeNum);
                                }
                            }
                        } else {
                            blance--;
                        }
                    }
                }
            }

            // ==============================================

        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    // kuzVe2lrSrXFdacxxi3KWjxx-4O7FEYDgn0xx0OehP5jt9-YINZOkxgPDkvWvkwkQXSDbZ
    // -77VUJcjlcZsjGio6MsAtmwxkxkx(FOREST_NORMAL_DRAW_SHARE)
    // kuzVe2lrSrXFdacxxi3KWjxx-4O7FEYDgn0xx0OehP5jt9-bxgpIW643h4FnWRjs9uZzng
    // -77VUJcjlcZsjGio6MsAtmwxkxkx(FOREST_ACTIVITY_DRAW_SHARE)
    void DoForestHuntHelp(Set<String> shareIds, String activityId, String p2pSceneCode, String taskType) {
        String taskUid = UserIdMap.getCurrentUid();
        try {
            int forestHuntHelpTodayCount;

            for (String shareUserId : shareIds) {
                forestHuntHelpTodayCount = Status.getforestHuntHelpToday(taskType);
                // if (!Status.canForestHuntHelpToday(taskType + "::" + shareUserId)) {
                // 判断当天是否助力过
                if (Status.hasFlagToday(taskType + "::" + shareUserId)) {
                    continue;
                }
                String shareId;
                if ((shareUserId.length() > 20 && shareUserId.length() < 28) && taskType.equals("FOREST_NORMAL_DRAW_SHARE")) {
                    shareId = shareUserId + "4O7FEYDgn0xx0OehP5jt9" + "YINZOkxgPDkvWvkwkQXSDbZ" + "77VUJcjlcZsjGio6MsAtmwxkxkx";
                } else if ((shareUserId.length() > 20 && shareUserId.length() < 28) && taskType.equals("FOREST_ACTIVITY_DRAW_SHARE")) {
                    shareId = shareUserId + "4O7FEYDgn0xx0OehP5jt9" + "bxgpIW643h4FnWRjs9uZzng" + "77VUJcjlcZsjGio6MsAtmwxkxkx";
                } else {
                    Log.forest("森林寻宝🎰️[错误分享用户]#" + UserIdMap.getShowName(shareUserId));
                    continue;
                }
                String userId = shareComponentRecall(p2pSceneCode, shareId);
                if (userId.equals("解析userID失败")) {
                    continue;
                }
                TimeUtil.sleep(1500);
                //能成功返回，但解析不了UID
                if (userId.length() != 16) {
                    continue;
                }
                String resconfirmShareRecall = confirmShareRecall(activityId, p2pSceneCode, shareId, userId);
                TimeUtil.sleep(1500);

                String userName = UserIdMap.getShowName(userId) != null ? UserIdMap.getShowName(userId) : userId;
                Log.forest("森林寻宝👊助力[" + userName + "]" + resconfirmShareRecall);
                // 标记助力成功
                Status.flagToday(taskType + "::" + shareUserId, taskUid);
                forestHuntHelpTodayCount++;
                // 统计场景助力次数
                Status.forestHuntHelpToday(taskType, forestHuntHelpTodayCount, taskUid);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 抽抽乐任务的三条腿：4Chouchoule ↔ opengreen 互备（XLIGHT 先走前者），都被拒时再换
     * doFarmTask（见 {@link TaskAlternative}）。
     *
     * @param stage 当前档位（调用方的 rightsTimes）：多次任务每推进一档都是**新的**尝试，
     *              策略键必须带上它，否则第一档打上的当日标记会把后续档位全挡下、任务停在第一档。
     *              单次任务传 0。
     * @return 已成功或已触发（true 时调用方应 {@code doublecheck = true} 重拉列表核对）
     */
    private boolean chouChouLeFinishTask(String taskType, String taskSceneCode, String taskName, boolean xlight,
                                         String activitySceneCode, int stage) {
        // 做不了的当天只试一次、临时故障留待下一轮（见 TaskAttemptPolicy）
        // 完成判定以任务列表为准（探针复核），响应不可信
        String policyKey = "forest::chouchoule::" + taskSceneCode + "/" + taskType + "#" + stage;
        Outcome outcome = TaskAttemptPolicy.handle(policyKey, taskName, null,
                () -> attemptChouChouLeTask(taskType, taskSceneCode, taskName, xlight), Log::forest,
                new TaskAttemptPolicy.Site("AntForestHuntTaskList", "森林寻宝", taskType, taskSceneCode,
                        (k) -> probeChouChouLeStatus(activitySceneCode, taskSceneCode, taskType, stage)));
        return outcome == Outcome.DONE || outcome == Outcome.TRIGGERED;
    }

    /** 活力值兑换抽奖次数：{@code exchangeTimesFromTaskopengreen}。 */
    private Outcome attemptExchangeVitality(String activityId, String sceneCode, String taskSceneCode,
                                            String taskType, String taskName) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.exchangeTimesFromTaskopengreen(
                    activityId, sceneCode, "task_entry", taskSceneCode, taskType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int times = jo.optInt("times", -1);
                if (times <= 0) return Outcome.RETRY;
                Log.forest("森林寻宝🎖️[" + taskName + "]获得抽奖*" + times);
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "attemptExchangeVitality err:", t);
        }
        return Outcome.RETRY;
    }

    /** 列表状态探针：用与本场景相同的表达式重拉寻宝任务列表，按 sceneCode+taskType 匹配该任务当前状态。 */
    private TaskAttemptPolicy.ProbeResult probeChouChouLeStatus(String activitySceneCode, String taskSceneCode,
                                                                String taskType, int previousRights) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen(activitySceneCode + "_TASK", "task_entry"));
            if (!MessageUtil.checkSuccess(TAG, jo) || !jo.has("taskInfoList")) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray taskList = jo.optJSONArray("taskInfoList");
            if (taskList == null) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                JSONObject taskBaseInfo = task == null ? null : task.optJSONObject("taskBaseInfo");
                if (taskBaseInfo == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                if (!taskSceneCode.equals(taskBaseInfo.optString("sceneCode"))
                        || !taskType.equals(taskBaseInfo.optString("taskType"))) {
                    continue;
                }
                JSONObject taskRights = task.optJSONObject("taskRights");
                String status = taskBaseInfo.optString("taskStatus");
                if ("TODO".equals(status) && previousRights >= 0 && taskRights != null && taskRights.optInt("rightsTimes", -1) > previousRights)
                    return TaskAttemptPolicy.ProbeResult.FINISHED;
                if ("FINISHED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.FINISHED;
                }
                if ("RECEIVED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.RECEIVED;
                }
                return "TODO".equals(status) ? TaskAttemptPolicy.ProbeResult.TODO : TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            // 任务已从列表消失：视为已完成且已领
            return TaskAttemptPolicy.ProbeResult.GONE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "probeChouChouLeStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    /** 森林寻宝/抽抽乐完成上报：两种接口互为兜底，做不了不拉黑 */
    private Outcome attemptChouChouLeTask(String taskType, String taskSceneCode, String taskName, boolean xlight) {
        try {
            JSONObject result = MyUtils.newJSONObject(xlight
                    ? AntForestRpcCall.finishTask4Chouchoule(taskType, taskSceneCode)
                    : AntForestRpcCall.finishTaskopengreen(taskType, taskSceneCode));
            if (!MessageUtil.checkSuccess(TAG, result)) {
                result = MyUtils.newJSONObject(xlight
                        ? AntForestRpcCall.finishTaskopengreen(taskType, taskSceneCode)
                        : AntForestRpcCall.finishTask4Chouchoule(taskType, taskSceneCode));
            }
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestHuntTaskList", taskName, result);
            if (MessageUtil.checkSuccess(TAG, result)) {
                Log.forest("森林寻宝🧾上报已受理[" + taskName + "]");
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(result) || MessageUtil.isServerBusy(result)) {
                return Outcome.RETRY;
            }
            // 不支持rpc调用（400000040）→ 由 TaskAttemptPolicy 代为伪申报
            if (TaskAlternative.hit(result, taskSceneCode)) {
                return Outcome.UNSUPPORTED;
            }
            Log.other("森林寻宝⚠️未上报已受理[" + taskName + "]#taskType=" + taskType);
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "chouChouLeFinishTask err:", t);
        }
        return Outcome.RETRY;
    }

    private String shareComponentRecall(String sceneCode, String shareId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.shareComponentRecall(sceneCode, shareId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return "解析shareID失败";
            }
            if (jo.has("inviterInfoVo")) {
                jo = jo.optJSONObject("inviterInfoVo");
                if (jo != null) {
                    return jo.optString("userId");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "shareComponentRecall err:", t);
        }
        return "解析userID失败";
    }

    private String confirmShareRecall(String activityId, String p2pSceneCode, String shareId, String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.confirmShareRecall(activityId, p2pSceneCode, shareId, userId));
            return jo.optString("desc");
        } catch (Throwable t) {
            Log.err(TAG, "confirmShareRecall err:", t);
        }
        return "FALSE end";
    }
}
