package io.github.aw1y2z.sesame.model.task.antOrchard;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.entity.AlipayAntOrchardTaskList;
import io.github.aw1y2z.sesame.entity.AlipayOrchardChouChouLeTaskList;
import io.github.aw1y2z.sesame.entity.AlipayMemberCreditSesameTaskList;
import io.github.aw1y2z.sesame.entity.AlipayPlantScene;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.model.task.antMember.AntMemberRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.util.MyUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.OrchardChouChouLeTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.PlantSceneIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.*;

import android.content.Context;
import android.content.Intent;

public class AntOrchard extends ModelTask {
    private BooleanModelField orchardFloatBallTask, orchardManualVisitAward;
    private IntegerModelField orchardVisitDailyBudget;
    private static final String TAG = "AntOrchard";
    private static final String NAME = "农场";
    private static final ModelGroup GROUP = ModelGroup.ORCHARD;
    private String[] wuaList;
    private String userId;

    // 任务黑名单：某些广告/外跳类任务后端不支持 finishTask 或需要前端行为配合
    //groupId或者title
    private static final Set<String> ORCHARD_TASK_BLACKLIST = new HashSet<>();

    static {
        ORCHARD_TASK_BLACKLIST.add("ORCHARD_NORMAL_KUAISHOU_MAX");  // 逛一逛快手
        ORCHARD_TASK_BLACKLIST.add("ZHUFANG3IN1");                  // 添加农场小组件并访问
        ORCHARD_TASK_BLACKLIST.add("12173");                        // 买好货
        ORCHARD_TASK_BLACKLIST.add("TOUTIAO");                      // 逛一逛今日头条
        ORCHARD_TASK_BLACKLIST.add("ORCHARD_NORMAL_ZADAN10_3000");  // 农场对对碰
        ORCHARD_TASK_BLACKLIST.add("TAOBAO2");                      // 逛一逛闲鱼
        ORCHARD_TASK_BLACKLIST.add("ORCHARD_NORMAL_JIUYIHUISHOU_VISIT");  // 旧衣服回收
        ORCHARD_TASK_BLACKLIST.add("ORCHARD_NORMAL_SHOUJISHUMAHUISHOU");  // 数码回收
        ORCHARD_TASK_BLACKLIST.add("ORCHARD_NORMAL_AQ_XIAZAI");           // 下载AQ
        ORCHARD_TASK_BLACKLIST.add("逛一逛签到领现金");      // 逛一逛签到领现金
    }

    // 模型字段定义
    private IntegerModelField executeInterval;
    private BooleanModelField orchardListTask;
    private BooleanModelField AutoAntOrchardTaskList;
    private SelectModelField AntOrchardTaskList;
    private BooleanModelField orchardSpreadManure;
    private BooleanModelField useBatchSpread;
    private SelectAndCountModelField orchardSpreadManureSceneList;

    private BooleanModelField orchardPlantNew;
    private BooleanModelField drawGameCenterAward;
    private BooleanModelField receiveOrchardGameStay;
    private ChoiceModelField driveAnimalType;
    private SelectModelField driveAnimalList;
    private BooleanModelField batchHireAnimal;
    private SelectModelField doNotHireList;
    private SelectModelField doNotWeedingList;
    private BooleanModelField assistFriend;
    private SelectModelField assistFriendList;
    private BooleanModelField orchardChouChouLe;
    private BooleanModelField AutoOrchardChouChouLeTaskList;
    private SelectModelField OrchardChouChouLeTaskList;
    private static int fertilizerProgress = 0;

    /** 本次施肥是否用一键5次批量（由 canSpreadManure 判定，doSpreadManure 消费） */
    private static boolean spreadUseBatchThisTime = false;

    /** 本轮是否已记过「已达次数上限」：主场景额度用完后每轮还会再查一次，只留一行 */
    private static boolean spreadLimitLoggedThisRun = false;
    private static final ArrayList<String> enableSceneList = new ArrayList<>();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ModelGroup getGroup() {
        return GROUP;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(executeInterval = new IntegerModelField("executeInterval", "执行间隔(毫秒)", 500, 500, null));
        modelFields.addField(orchardListTask = new BooleanModelField("orchardListTask", "农场任务", false));
        modelFields.addField(orchardFloatBallTask = new BooleanModelField("orchardFloatBallTask", "农场任务 | 当前任务列表浮球时长", false).setDependsOn("orchardListTask")
                .setDescription("补充VISIT+floatBallConfig协议，按实际时长分段完整等待后上报并回查；不代表真实打开页面游玩。"));
        modelFields.addField(orchardManualVisitAward = new BooleanModelField("orchardManualVisitAward", "农场访问奖励 | 领取需手动确认的奖励", false));
        modelFields.addField(orchardVisitDailyBudget = new IntegerModelField("orchardVisitDailyBudget", "农场访问扩展 | 每日操作尝试预算（0不执行）", 20, 0, 30));
        modelFields.addField(AutoAntOrchardTaskList = new BooleanModelField("AutoAntOrchardTaskList", "农场任务 | 自动黑名单", true).setDependsOn("orchardListTask"));
        modelFields.addField(AntOrchardTaskList = new SelectModelField("AntOrchardTaskList", "农场任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntOrchardTaskList::getList).setDependsOn("AutoAntOrchardTaskList"));
        modelFields.addField(orchardSpreadManure = new BooleanModelField("orchardSpreadManure", "农场施肥 | 开启", false));
        modelFields.addField(useBatchSpread = new BooleanModelField("useBatchSpread", "一键施肥5次(边界突破)", false)
                .setDependsOn("orchardSpreadManure")
                .setDescription("按场景次数一键施肥 5 次；配置达到每日上限时尝试 199+5 至 204 次，肥料不足时退回单次"));
        modelFields.addField(orchardSpreadManureSceneList = new SelectAndCountModelField("orchardSpreadManureSceneList", "农场施肥 | 场景列表", new LinkedHashMap<>(), AlipayPlantScene::getList, "请填写每日施肥次数")
                .setDependsOn("orchardSpreadManure")
                .setDescription("只对勾选且服务端已下发的场景施肥"));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "农场乐园 | 游戏宝箱", true));
        modelFields.addField(receiveOrchardGameStay = new BooleanModelField("receiveOrchardGameStay", "农场乐园 | 完成游戏时长任务", true)
                .setDescription("尝试完成首页下发的游戏时长任务；按任务要求等待，成功结项后记录当天完成状态"));
        //modelFields.addField(driveAnimalType = new ChoiceModelField("driveAnimalType", "驱赶小鸡 | 动作", DriveAnimalType.NONE, DriveAnimalType.nickNames));
        //modelFields.addField(driveAnimalList = new SelectModelField("driveAnimalList", "驱赶小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        //modelFields.addField(batchHireAnimal = new BooleanModelField("batchHireAnimal", "捉鸡除草 | 开启", false));
        //modelFields.addField(doNotHireList = new SelectModelField("doNotHireList", "捉鸡除草 | 不捉鸡列表", new LinkedHashSet<>(), AlipayUser::getList));
        //modelFields.addField(doNotWeedingList = new SelectModelField("doNotWeedingList", "捉鸡除草 | 不除草列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(assistFriend = new BooleanModelField("assistFriend", "分享助力 | 开启", false));
        modelFields.addField(assistFriendList = new SelectModelField("assistFriendList", "分享助力 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("assistFriend"));
        modelFields.addField(orchardChouChouLe = new BooleanModelField("orchardChouChouLe", "抽抽乐(阿肥寻宝记)", false));
        modelFields.addField(AutoOrchardChouChouLeTaskList = new BooleanModelField("AutoOrchardChouChouLeTaskList", "抽抽乐任务 | 自动黑名单", true).setDependsOn("orchardChouChouLe"));
        modelFields.addField(OrchardChouChouLeTaskList = new SelectModelField("OrchardChouChouLeTaskList", "抽抽乐任务 | 黑名单列表", new LinkedHashSet<>(), AlipayOrchardChouChouLeTaskList::getList).setDependsOn("AutoOrchardChouChouLeTaskList"));
        return modelFields;
    }

    @Override
    public Boolean check() {
        // 假设TaskCommon.IS_ENERGY_TIME存在
        // 如果没有这个字段，可以注释掉或创建
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️芭芭农场:当前为只收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            userId = UserIdMap.getCurrentUid();
            if (!checkOrchardOpen()) {
                return;
            }

            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntOrchard")) {
                initAntOrchardTaskListMap(AutoAntOrchardTaskList.getValue(), orchardListTask.getValue(), orchardChouChouLe.getValue(), AutoOrchardChouChouLeTaskList.getValue());
                Status.flagToday("BlackList::initAntOrchard");
            }
            // 额外信息获取（每日肥料包）
            extraInfoGet();

            if (receiveOrchardGameStay.getValue()) {
                AntOrchardGameStayTask.execute();
            } else {
                Log.record("农场游戏时长：跳过，配置开关已关闭");
            }

            // 执行农场任务
            if (orchardListTask.getValue()) {
                orchardListTask();
                orchardStarTasks();
            }


            // 执行施肥逻辑
            if (orchardSpreadManure.getValue()) {
                orchardSpreadManure();
            }

            // 好友助力
            if (assistFriend.getValue()) {
                orchardAssistFriend();
            }

            // 农场抽抽乐
            if (orchardChouChouLe.getValue()) {
                orchardChouChouLe();
            }

        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "start.run err:", t);
        }
    }

    /**
     * 农场抽抽乐（阿肥寻宝记 / 农场抽抽乐普通版）
     * 兼容活动分组与新版顶层活动；游戏上报后回查任务，按服务端余额批量抽奖。
     */
    private void orchardChouChouLe() {
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) {
                Log.record("农场抽抽乐：跳过，账号正在切换");
                return;
            }
            userId = UserIdMap.getCurrentUid();
            if (userId == null || userId.isEmpty()) {
                Log.record("农场抽抽乐：跳过，当前账号为空");
                return;
            }
            Log.record("农场轮盘（抽抽乐）：开始，查询任务与抽奖次数，appMode=normal");
            String res = AntOrchardRpcCall.enterDrawActivityantorchard("", "ANTORCHARD_DRAW_TIMES", "antorchard");
            JSONObject resData = MyUtils.newJSONObject(res);
            if (!orchardDrawSuccessful(resData, "进入活动")) {
                return;
            }
            JSONArray drawSceneGroups = orchardDrawScenes(resData);
            if (drawSceneGroups == null || drawSceneGroups.length() == 0) {
                Log.record("农场抽抽乐：停止，活动响应缺少有效场景");
                return;
            }
            Set<String> visited = new HashSet<>();
            for (int i = 0; i < drawSceneGroups.length(); i++) {
                JSONObject drawScene = drawSceneGroups.optJSONObject(i);
                if (drawScene == null) {
                    Log.record("农场抽抽乐：跳过，无效活动场景");
                    continue;
                }
                JSONObject drawActivity = drawScene.optJSONObject("drawActivity");
                if (drawActivity == null) {
                    Log.record("农场抽抽乐：跳过，场景缺少活动信息");
                    continue;
                }
                Object id = drawActivity.opt("activityId");
                String activityId = id instanceof String || id instanceof Number ? drawActivity.optString("activityId") : "";
                String drawScenename = drawActivity.optString("name");
                String sceneCode = drawActivity.opt("sceneCode") instanceof String ? drawActivity.optString("sceneCode") : "";
                if (activityId.isEmpty() || sceneCode.isEmpty()) {
                    Log.record("农场抽抽乐：跳过，场景缺少活动 ID 或场景编号");
                    continue;
                }
                if (!visited.add(activityId + ":" + sceneCode)) continue;
                orchardChouChouLeScene(activityId, drawScenename, sceneCode);
            }
            Log.record("农场抽抽乐：本轮结束，已处理 " + visited.size() + " 个活动场景");
        } catch (TaskCancelledException e) {
            Log.record("农场抽抽乐：停止，任务已取消");
            throw e;
        } catch (Throwable t) {
            Log.err(TAG, "orchardChouChouLe err:", t);
            Log.record("农场抽抽乐：失败，异常类型=" + t.getClass().getSimpleName());
        }
    }

    /** 列表状态探针：重拉农场抽抽乐任务列表，按 sceneCode+taskType 匹配该任务当前状态。 */
    private static TaskAttemptPolicy.ProbeResult probeOrchardChouChouLeStatus(String activitySceneCode,
                                                                             String taskSceneCode, String taskType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(
                    AntOrchardRpcCall.listTaskantorchard(activitySceneCode + "_TASK", "antorchard"));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
            if (taskInfoList == null) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                JSONObject taskInfo = taskInfoList.optJSONObject(i);
                JSONObject taskBaseInfo = taskInfo == null ? null : taskInfo.optJSONObject("taskBaseInfo");
                if (taskBaseInfo == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                if (!taskSceneCode.equals(taskBaseInfo.optString("sceneCode"))
                        || !taskType.equals(taskBaseInfo.optString("taskType"))) {
                    continue;
                }
                String status = taskBaseInfo.optString("taskStatus");
                if ("FINISHED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.FINISHED;
                }
                if ("RECEIVED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.RECEIVED;
                }
                return TaskAttemptPolicy.ProbeResult.TODO;
            }
            // 任务已从列表消失：视为已完成且已领
            return TaskAttemptPolicy.ProbeResult.GONE;
        } catch (Throwable t) {
            Log.err(TAG, "probeOrchardChouChouLeStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    private void orchardChouChouLeScene(String activityId, String drawScenename, String sceneCode) {
        try {
            boolean doublecheck;
            int loopCount = 0;
            final int MAX_LOOP = 7;
            Set<String> attempted = new HashSet<>();
            do {
                doublecheck = false;
                String listRes = AntOrchardRpcCall.listTaskantorchard(sceneCode + "_TASK", "antorchard");
                JSONObject listTask = MyUtils.newJSONObject(listRes);
                if (!orchardDrawSuccessful(listTask, "查询[" + drawScenename + "]任务")) {
                    return;
                }
                JSONArray taskList = listTask.optJSONArray("taskInfoList");
                if (taskList == null) {
                    Log.record("农场抽抽乐：停止，任务列表缺失");
                    return;
                }
                for (int i = 0; i < taskList.length(); i++) {
                    JSONObject taskInfo = taskList.optJSONObject(i);
                    if (taskInfo == null) {
                        continue;
                    }
                    String taskStatus = orchardDrawTaskField(taskInfo, "taskStatus");
                    String taskType = orchardDrawTaskField(taskInfo, "taskType");
                    String taskScene = orchardDrawTaskField(taskInfo, "sceneCode");
                    String taskSceneCode = taskScene.isEmpty() ? sceneCode + "_TASK" : taskScene;
                    JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                    JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo == null ? "" : taskBaseInfo.optString("bizInfo"));
                    String taskName = bizInfo.optString("title", taskType);
                    if (taskType.isEmpty() || taskStatus.isEmpty()) {
                        Log.record("农场抽抽乐：跳过[" + taskName + "]，任务类型或状态缺失");
                        continue;
                    }
                    if (OrchardChouChouLeTaskList.getValue().contains(taskName)) {
                        Log.record("农场抽抽乐：跳过[" + taskName + "]，命中黑名单");
                        continue;
                    }
                    JSONObject taskRights = taskInfo.optJSONObject("taskRights");
                    int rightsTimes = taskRights != null ? taskRights.optInt("rightsTimes") : 0;
                    int rightsTimesLimit = taskRights != null ? taskRights.optInt("rightsTimesLimit") : 0;
                    if (!"TODO".equals(taskStatus) && !"FINISHED".equals(taskStatus)) {
                        if (!"RECEIVED".equals(taskStatus)) {
                            Log.record("农场抽抽乐：跳过[" + taskName + "]，未识别的任务状态=" + taskStatus);
                        } else {
                            Log.record("农场抽抽乐：任务[" + taskName + "]已领奖，领取进度=" + rightsTimes + "/" + rightsTimesLimit);
                        }
                        continue;
                    }
                    if (!attempted.add(taskSceneCode + ":" + taskType + ":" + taskStatus + ":" + rightsTimes)) {
                        if ("TODO".equals(taskStatus)) {
                            Log.record("农场抽抽乐：任务[" + taskName + "]状态未推进，本轮不再重复尝试");
                        }
                        continue;
                    }

                    // 已完成任务领取奖励（如每日签到）
                    if ("FINISHED".equals(taskStatus)) {
                        TimeUtil.sleep(2000);
                        String awardRes = AntOrchardRpcCall.receiveDrawTaskAwardantorchard(taskSceneCode, taskType);
                        JSONObject sginRes = MyUtils.newJSONObject(awardRes);
                        if (orchardDrawSuccessful(sginRes, "领取[" + taskName + "]奖励")) {
                            int incAwardCount = sginRes.optInt("incAwardCount", -1);
                            orchardDrawLog("任务[" + taskName + "]领取成功；"
                                    + (incAwardCount >= 0 ? "获得抽奖次数=" + incAwardCount : "奖励次数未返回"));
                            if (rightsTimesLimit - rightsTimes > 0) {
                                doublecheck = true;
                            }
                        } else {
                            // 领奖收口：先按任务列表复核"已领到"，未确认才交自动拉黑（顺序由 TaskAward 固定）
                            TaskAward.confirmReceivedOrBlackList("农场抽抽乐🎖️",
                                    k -> probeOrchardChouChouLeStatus(sceneCode, taskSceneCode, taskType), taskName,
                                    taskName,
                                    () -> MessageUtil.checkResultCodeAndMarkTaskBlackList("OrchardChouChouLeTaskList", taskName, sginRes),
                                    msg -> Log.farm(msg));
                        }
                    } else if ("TODO".equals(taskStatus)) {
                        boolean direct = "DRAW_GOLDENBEAN_liulan".equals(taskType);
                        java.util.regex.Matcher app = java.util.regex.Pattern.compile("[?&]appId=([0-9]+)(?:[&#]|$)")
                                .matcher(bizInfo.optString("targetUrl"));
                        String appId = app.find() ? app.group(1) : "";
                        GameTask game = GameTask.matchAppId(appId);
                        if (game != null && !direct) {
                            String countText = orchardDrawTaskField(taskInfo, "finishOnceAwardCnt");
                            int count;
                            try {
                                count = countText.isEmpty() ? 1 : Integer.parseInt(countText);
                            } catch (NumberFormatException e) {
                                Log.record("农场抽抽乐：跳过[" + taskName + "]，游戏次数无效");
                                continue;
                            }
                            if (count <= 0 || count > 10) {
                                Log.record("农场抽抽乐：跳过[" + taskName + "]，游戏次数不在本轮允许范围 1–10");
                                continue;
                            }
                            Log.record("农场抽抽乐：任务[" + taskName + "]匹配游戏[" + game.getTitle() + "]，开始上报");
                            int reports = game.reportSync("农场抽抽乐", count);
                            Log.record("农场抽抽乐：任务[" + taskName + "]游戏上报"
                                    + (reports > 0 ? "成功 " + reports + " 次，回查服务端任务与抽奖次数" : "失败，未取得有效上报结果"));
                            doublecheck |= reports > 0;
                            continue;
                        }
                        if (!direct && (!taskInfo.optString("iepTaskTracer").isEmpty() || !appId.isEmpty())) {
                            Log.record("农场抽抽乐：跳过[" + taskName + "]，未支持的任务或游戏(appId=" + appId + ")");
                            continue;
                        }
                        TimeUtil.sleep(1000);
                        String finishRes = AntOrchardRpcCall.finishTaskantorchard(taskType, taskSceneCode);
                        JSONObject finishJo = MyUtils.newJSONObject(finishRes);
                        boolean finished = orchardDrawSuccessful(finishJo, "完成[" + taskName + "]任务");
                        if (!finished && !direct) {
                            finishRes = AntOrchardRpcCall.finishTaskantorchardV2(taskType, taskSceneCode, userId);
                            finishJo = MyUtils.newJSONObject(finishRes);
                            finished = orchardDrawSuccessful(finishJo, "兼容接口完成[" + taskName + "]任务");
                        }
                        if (finished) {
                            orchardDrawLog("任务[" + taskName + "]完成接口成功，回查领奖状态");
                            doublecheck = true;
                        } else {
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("OrchardChouChouLeTaskList", taskName, finishJo);
                        }
                    }
                }
            } while (doublecheck && ++loopCount < MAX_LOOP);
            if (doublecheck && loopCount >= MAX_LOOP) {
                Log.record("农场抽抽乐：任务达到本轮 7 次回查上限，剩余任务下轮继续尝试");
            }

            JSONObject jo = MyUtils.newJSONObject(AntOrchardRpcCall.drawSyncantorchard(activityId, sceneCode, "taskaward"));
            if (!orchardDrawSuccessful(jo, "同步抽奖次数")) {
                return;
            }
            JSONObject drawAsset = jo.optJSONObject("drawAsset");
            Object rawBalance = drawAsset == null ? null : drawAsset.opt("blance");
            int blance;
            try {
                blance = rawBalance instanceof Number ? new java.math.BigDecimal(rawBalance.toString()).intValueExact()
                        : rawBalance instanceof String && ((String) rawBalance).matches("[0-9]+")
                        ? Integer.parseInt((String) rawBalance) : -1;
            } catch (ArithmeticException | NumberFormatException e) {
                blance = -1;
            }
            if (blance < 0) {
                Log.record("农场抽抽乐：停止，同步响应缺少有效抽奖次数");
                return;
            }
            if (blance == 0) {
                Log.record("农场抽抽乐：活动[" + drawScenename + "]无剩余抽奖次数");
                return;
            }
            Log.record("农场抽抽乐：活动[" + drawScenename + "]剩余 " + blance + " 次，开始批量抽奖");
            jo = MyUtils.newJSONObject(AntOrchardRpcCall.batchDrawantorchard(activityId, sceneCode, "antorchard", blance, userId));
            if (!orchardDrawSuccessful(jo, "批量抽奖")) return;
            JSONArray results = jo.optJSONArray("drawResultList");
            StringJoiner prizes = new StringJoiner("、");
            if (results != null) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.optJSONObject(i);
                    JSONObject prize = result == null ? null : result.optJSONObject("prizeVO");
                    if (prize != null && !prize.optString("prizeName").isEmpty()) prizes.add(prize.optString("prizeName"));
                }
            }
            orchardDrawLog("活动[" + drawScenename + "]批量抽奖成功；请求 " + blance + " 次；奖励="
                    + (prizes.length() > 0 ? prizes : "奖励明细未返回，请查看活动页面"));
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Throwable t) {
            Log.err(TAG, "orchardChouChouLeScene err:", t);
            Log.record("农场抽抽乐：场景[" + drawScenename + "]失败，异常类型=" + t.getClass().getSimpleName());
        }
    }

    private static JSONArray orchardDrawScenes(JSONObject response) throws org.json.JSONException {
        JSONArray scenes = response.optJSONArray("drawSceneGroups");
        if (scenes != null && scenes.length() > 0) return scenes;
        JSONObject scene = response.optJSONObject("drawScene");
        if (scene != null) return new JSONArray().put(scene);
        JSONObject activity = response.optJSONObject("drawActivity");
        if (activity == null) return null;
        JSONObject normalized = MyUtils.newJSONObject(activity.toString());
        if (normalized.optString("sceneCode").isEmpty()) normalized.put("sceneCode", "ANTORCHARD_DRAW_TIMES");
        return new JSONArray().put(MyUtils.newJSONObject().put("drawActivity", normalized));
    }

    private static String orchardDrawTaskField(JSONObject task, String field) {
        JSONObject base = task.optJSONObject("taskBaseInfo");
        Object raw = base == null ? null : base.opt(field);
        String value = raw instanceof String ? ((String) raw).trim()
                : "finishOnceAwardCnt".equals(field) && raw instanceof Number ? raw.toString() : "";
        if (!value.isEmpty()) return value;
        Object tracer = task.opt("iepTaskTracer");
        if (!(tracer instanceof String)) return "";
        for (String segment : ((String) tracer).split("~")) {
            int colon = segment.indexOf(':');
            if (colon > 0 && field.equals(segment.substring(0, colon))) return segment.substring(colon + 1).trim();
        }
        return "";
    }

    private static boolean orchardDrawSuccessful(JSONObject response, String step) {
        boolean ok = Boolean.TRUE.equals(response.opt("success")) && !RpcRequestGuard.isFailure(response);
        if (!ok) {
            String code = response.optString("resultCode", response.optString("code", response.optString("error", "未返回")));
            String reason = response.length() == 0 ? "响应为空或不是有效 JSON" : RpcRequestGuard.errorMessage(response);
            Log.record("农场抽抽乐：" + step + "失败#状态码=" + code + "#原因=" + reason);
        }
        return ok;
    }

    private static void orchardDrawLog(String message) {
        String text = "农场抽抽乐：" + message;
        Log.record(text);
        Log.farm(text);
    }

    /**
     * 检查农场是否已开启
     */
    private boolean checkOrchardOpen() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntOrchardRpcCall.orchardIndex());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }

            if (!jo.optBoolean("userOpenOrchard")) {
                getEnableField().setValue(false);
                Log.record("请先开启芭芭农场！");
                return false;
            }

            // 处理七日礼包
            JSONObject lotteryPlusInfo = jo.optJSONObject("lotteryPlusInfo");
            if (lotteryPlusInfo != null) {
                drawLotteryPlus(lotteryPlusInfo);
            }

            //获取场景列表
            initPlantScene(jo);

            // 处理可用场景列表
            handleEnableScenes(jo);

            // 处理淘宝数据（果树状态）
            handleTaobaoData(jo.optString("taobaoData"));

            // 处理金蛋
            if (drawGameCenterAward.getValue()) {
                JSONObject goldenEggInfo = jo.optJSONObject("goldenEggInfo");
                if (goldenEggInfo != null) {
                    int unsmashedGoldenEggs = goldenEggInfo.optInt("unsmashedGoldenEggs");
                    int limit = goldenEggInfo.optInt("goldenEggLimit");
                    int smashed = goldenEggInfo.optInt("smashedGoldenEggs");

                    if (unsmashedGoldenEggs > 0) {
                        // 现成的蛋先砸了
                        smashedGoldenEgg(unsmashedGoldenEggs);
                    } else {
                        int remain = limit - smashed;
                        if (remain > 0) {
                            GameTask.Orchard_ncscc.report("农场", remain);
                        }
                    }
                }
                queryOptionalPlay();
            }
            // 处理回访/浏览奖励：官方每次进农场都会调一次（2026-09-22 抓包实证）；
            // 不再加"每日一次"标记——首调可能在奖励还没产生时就把当天标记掉，反而漏领
            receiveOrchardVisitAward();

            return true;
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Throwable t) {
            Log.err(TAG, "orchardIndex err:", t);
            return false;
        }
    }

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntOrchardRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            if (!jo.has("taskTriggerPlayInfo")) {
                return;
            }
            JSONObject taskTriggerPlayInfo = jo.optJSONObject("taskTriggerPlayInfo");
            if (taskTriggerPlayInfo == null || !taskTriggerPlayInfo.has("taskList")) {
                return;
            }
            JSONArray taskList = taskTriggerPlayInfo.optJSONArray("taskList");
            for (int j = 0; taskList != null && j < taskList.length(); j++) {
                JSONObject task = taskList.optJSONObject(j);
                if (task == null) {
                    continue;
                }
                String taskType = task.optString("taskType");
                String taskStatus = task.optString("taskStatus");
                String sceneCode = task.optString("sceneCode");
                int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                int awardCount = task.optInt("awardCount");
                int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                JSONObject bizInfo = task.optJSONObject("bizInfo");
                String title = bizInfo != null ? bizInfo.optString("title") : "";
                if (taskStatus.equals("FINISHED")) {
                    if (awardCountForReceive > 0) {
                        JSONObject joReceived = MyUtils.newJSONObject(AntOrchardRpcCall.receiveTaskAwardantorchard(awardCountForReceive, sceneCode, taskType));
                        if (MessageUtil.checkSuccess(TAG, joReceived)) {
                            int incAwardCount = joReceived.optInt("incAwardCount");
                            JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                            String awardType = taskConfigResultVO != null ? taskConfigResultVO.optString("awardType") : "";
                            Log.farm("农场乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    public static void initAntOrchardTaskListMap(boolean AutoAntOrchardTaskList, boolean orchardListTask, boolean orchardChouChouLe, boolean AutoOrchardChouChouLeTaskList) {
        try {
            //初始化AntOrchardTaskListMap
            AntOrchardTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注：浏览/外跳类、下载APP类不再预置拉黑，交由自动拉黑机制判定；
            // 需真实完成或存在风险的（旧衣回收、数码回收）保留
            // 预置黑名单登记在 MessageUtil（单一真相，配置页据此标注"默认"）
            Set<String> blackList = MessageUtil.presetBlackList("AntOrchard", "AntOrchardTaskList");
            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntOrchardTaskListMap.add(task, task);
            }

            if (orchardListTask) {
                String result = AntOrchardRpcCall.orchardListTask();
                JSONObject jo = MyUtils.newJSONObject(result);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray taskArray = jo.optJSONArray("taskList");
                    for (int i = 0; taskArray != null && i < taskArray.length(); i++) {
                        jo = taskArray.optJSONObject(i);
                        if (jo == null) {
                            continue;
                        }
                        JSONObject displayConfig = jo.optJSONObject("taskDisplayConfig");
                        if (displayConfig != null && displayConfig.has("title")) {
                            String title = displayConfig.optString("title");
                            AntOrchardTaskListMap.add(title, title);
                        }
                    }
                }
                //保存任务到配置文件
                AntOrchardTaskListMap.save();
                Log.record("同步任务🉑农芭芭场肥料任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntOrchardTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntOrchard = config.getModelFieldsMap().get("AntOrchard");
                    SelectModelField AntOrchardTaskList = (SelectModelField) AntOrchard.get("AntOrchardTaskList");
                    if (AntOrchardTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("芭芭农场肥料任务", "AntOrchardTaskList", blackList, whiteList, AntOrchardTaskList);
                }
            }

            // ============ 农场抽抽乐任务黑名单初始化 ============
            OrchardChouChouLeTaskListMap.load();
            // 预置黑名单登记在 MessageUtil（单一真相，配置页据此标注"默认"）
            Set<String> chouChouLeBlackList = MessageUtil.presetBlackList("AntOrchard", "OrchardChouChouLeTaskList");
            Set<String> chouChouLeWhiteList = new HashSet<>();
            if (orchardChouChouLe) {
                String res = AntOrchardRpcCall.enterDrawActivityantorchard("", "ANTORCHARD_DRAW_TIMES", "antorchard");
                JSONObject resData = MyUtils.newJSONObject(res);
                if (MessageUtil.checkSuccess(TAG, resData)) {
                    JSONArray drawSceneGroups = orchardDrawScenes(resData);
                    if (drawSceneGroups != null) {
                        for (int i = 0; i < drawSceneGroups.length(); i++) {
                            JSONObject drawScene = drawSceneGroups.optJSONObject(i);
                            if (drawScene == null) {
                                continue;
                            }
                            JSONObject drawActivity = drawScene.optJSONObject("drawActivity");
                            if (drawActivity == null) {
                                continue;
                            }
                            String sceneCode = drawActivity.optString("sceneCode");
                            if (sceneCode.isEmpty()) continue;
                            String listRes = AntOrchardRpcCall.listTaskantorchard(sceneCode + "_TASK", "antorchard");
                            JSONObject listTask = MyUtils.newJSONObject(listRes);
                            if (MessageUtil.checkSuccess(TAG, listTask)) {
                                JSONArray taskList = listTask.optJSONArray("taskInfoList");
                                if (taskList != null) {
                                    for (int j = 0; j < taskList.length(); j++) {
                                        JSONObject taskInfo = taskList.optJSONObject(j);
                                        if (taskInfo == null) {
                                            continue;
                                        }
                                        JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                                        if (taskBaseInfo == null) {
                                            continue;
                                        }
                                        JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo", "{}"));
                                        String taskName = bizInfo.optString("title");
                                        if (!taskName.isEmpty()) {
                                            OrchardChouChouLeTaskListMap.add(taskName, taskName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                OrchardChouChouLeTaskListMap.save();
                Log.record("同步任务🉑农场抽抽乐任务列表");
                if (AutoOrchardChouChouLeTaskList) {
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntOrchardFields = config.getModelFieldsMap().get("AntOrchard");
                    SelectModelField OrchardChouChouLeTaskListField = (SelectModelField) AntOrchardFields.get("OrchardChouChouLeTaskList");
                    if (OrchardChouChouLeTaskListField == null) {
                        return;
                    }
                    MessageUtil.syncTaskBlackList("农场抽抽乐任务", "OrchardChouChouLeTaskList", chouChouLeBlackList, chouChouLeWhiteList, OrchardChouChouLeTaskListField);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initAntOrchardTaskListMap err:", t);
        }
    }

    /**
     * 处理可用场景列表
     */

    public static void initPlantScene(JSONObject jo) {
        try {
            JSONArray sceneArray = jo.optJSONArray("enableSwitchSceneList");
            if (sceneArray == null) {
                return;
            }
            PlantSceneIdMap.load();
            for (int i = 0; i < sceneArray.length(); i++) {
                String scene = sceneArray.optString(i);
                PlantSceneIdMap.add(scene, getSceneDisplayName(scene));
            }
            PlantSceneIdMap.save();
        } catch (Throwable t) {
            Log.err(TAG, "initPlantScene err:", t);
        }
    }

    private static String getSceneDisplayName(String scene) {
        switch (scene) {
            case "main": return "果树";
            case "yeb": return "金钱树";
            default: return scene;
        }
    }

    private void handleEnableScenes(JSONObject jo) {
        try {

            JSONArray sceneArray = jo.optJSONArray("enableSwitchSceneList");
            enableSceneList.clear();
            for (int i = 0; sceneArray != null && i < sceneArray.length(); i++) {
                String scene = sceneArray.optString(i);
                enableSceneList.add(scene);

                // 主场景处理
                if ("main".equals(scene)) {
                    if (jo.optString("currentPlantScene").equals(scene) || switchPlantScene(PlantScene.main)) {
                        // 处理限时挑战活动
                        //limitedTimeChallenge();
                        //querySubplotsActivity("WISH");
                        //querySubplotsActivity("CAMP_TAKEOVER");
                    }
                }

                // 余额宝场景处理
                if ("yeb".equals(scene)) {
                    JSONObject yebInfo = jo.optJSONObject("yebSceneActivityInfo");
                    if (yebInfo == null) {
                        continue;
                    }
                    if ("NOT_PLANTED".equals(yebInfo.optString("yebSceneStatus"))) {
                        enableSceneList.remove(scene);
                    } else if (yebInfo.optBoolean("revenueNotReceived")) {
                        queryYebRevenueDetail();
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "handleEnableScenes err:", t);
        }
    }

    /**
     * 处理淘宝数据（果树生长状态）
     */
    private void handleTaobaoData(String taobaoData) {
        try {
            JSONObject jo = MyUtils.newJSONObject(taobaoData);
            JSONObject gameInfo = jo.optJSONObject("gameInfo");
            JSONObject plantInfo = gameInfo != null ? gameInfo.optJSONObject("plantInfo") : null;
            JSONObject seedStage = plantInfo != null ? plantInfo.optJSONObject("seedStage") : null;
            if (plantInfo == null || seedStage == null) {
                return;
            }

            // 检查是否可兑换
            if (plantInfo.optBoolean("canExchange")) {
                Log.i("农场果树似乎可以兑换了！");
                Toast.show("芭芭农场果树似乎可以兑换了！");
            }
            // 更新施肥进度
            if (seedStage.has("totalValue")) {
                fertilizerProgress = seedStage.optInt("totalValue");
            }
        } catch (Throwable t) {
            Log.err(TAG, "handleTaoBaoData err:", t);
        }
    }

    /**
     * 农场施肥逻辑
     */
    private void orchardSpreadManure() {
        try {
            spreadLimitLoggedThisRun = false;
            // 轮次上限兜底：每轮最多施肥一次，按「上限/批量步长 + 余量」估算并留足两场景的量
            final int MAX_SPREAD_ROUND = (MAIN_SPREAD_DAILY_LIMIT / BATCH_SPREAD_SIZE + 10) * 8;
            int round = 0;
            for (; round < MAX_SPREAD_ROUND; round++) {
                boolean hasSpread = false;
                boolean anySceneQualified = false;
                // 遍历可用场景进行施肥
                for (PlantScene scene : PlantScene.getEntries()) {
                    if (enableSceneList.contains(scene.name()) && orchardSpreadManureSceneList.contains(scene.name()) && targetSpreadTimes(orchardSpreadManureSceneList.get(scene.name())) > 0) {
                        anySceneQualified = true;
                        // 切换场景
                        if (!switchPlantScene(scene)) {
                            Log.record("农场施肥⏭️切换场景失败[" + scene.name() + "]");
                            continue;
                        }
                        // 检查是否可施肥
                        if (!canSpreadManure(scene)) {
                            continue;
                        }
                        // 执行施肥
                        if (doSpreadManure(scene)) {
                            hasSpread = true;
                            break;
                        }
                    }
                }

                // 场景没对上（服务端未下发该场景/配置里没勾）时静默跳过，留一行便于定位
                if (!anySceneQualified) {
                    Log.record("农场施肥⏭️场景未启用#可用" + enableSceneList + "#配置" + orchardSpreadManureSceneList);
                }

                // 查询施肥活动奖励
                querySpreadManureActivity();

                // 等待间隔时间
                int interval = executeInterval.getValue() != null ? executeInterval.getValue() : 500;
                TimeUtil.sleep(interval);

                if (!hasSpread) {
                    break;
                }
            }
            if (round >= MAX_SPREAD_ROUND) {
                Log.record("农场施肥⏭️已达单轮循环上限[" + MAX_SPREAD_ROUND + "]，本轮停止");
            }
        } catch (Throwable t) {
            Log.err(TAG, "orchardSpreadManure err:", t);
        }
    }

    /**
     * 执行施肥操作
     */
    private boolean doSpreadManure(PlantScene scene) {
        try {
            String sceneName = scene.name();
            String wua = getWua();
            String result = AntOrchardRpcCall.orchardSpreadManure(sceneName, spreadUseBatchThisTime, wua);
            JSONObject jo = MyUtils.newJSONObject(result);

            // 场景侧的业务拒绝（P03 场景未就绪 / P14 摇钱树已达持仓金额上限）再请求也不会变，
            // 打当日标记后当天不再重放批量请求，也避免被当成 error 打印
            String resultCode = jo.optString("resultCode", "");
            if ("P03".equals(resultCode) || "P14".equals(resultCode)) {
                Status.flagToday("spreadManureLimit:" + sceneName, userId);
                Log.record("农场施肥⏭️[" + sceneName + "]被拒：" + jo.optString("memo", resultCode));
                return false;
            }

            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }

            JSONObject taobaoData = MyUtils.newJSONObject(jo.optString("taobaoData"));
            JSONObject stage = taobaoData.optJSONObject("currentStage");
            if (stage == null || !(stage.opt("totalValue") instanceof Number) || stage.optInt("totalValue", -1) < 0) {
                Log.record("施肥响应缺少有效进度，本轮停止施肥");
                return false;
            }
            int cost = taobaoData.optInt("currentCost");
            boolean batch = spreadUseBatchThisTime;
            Log.farm("芭芭农场🌳" + scene.nickname() + "施肥#消耗[" + cost + "g肥料]"
                    + (batch ? "#一键5次" : "") + "#目标[" + targetSpreadTimes(orchardSpreadManureSceneList.get(scene.name())) + "次]");

            // 检查施肥进度：单次只加 0.01%(1) 或 一键5次只加 0.05%(5) 即被限制施肥，当天不再施肥
            int newProgress = stage.optInt("totalValue", fertilizerProgress);
            int delta = newProgress - fertilizerProgress;
            int spreadTimes = batch ? BATCH_SPREAD_SIZE : 1;
            // delta 为负＝跨场景或基线过期，不作判据（判了会把没被限制的场景也停掉）
            if (delta >= 0 && delta <= spreadTimes) {
                Log.record("施肥" + (batch ? "一键5次只加0.05%" : "1次只加0.01%") + "进度今日停止施肥！");
                Status.flagToday("spreadManureLimit:" + sceneName, userId);
            }
            fertilizerProgress = newProgress;
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "doSpreadManure err:", t);
            return false;
        }
    }


    public String getWua() {
        if (wuaList == null) {
            try {
                String content = FileUtil.readFromFile(FileUtil.getWuaFile());
                if (content != null && !content.trim().isEmpty()) {
                    wuaList = content.split("\n");
                } else {
                    wuaList = new String[0];
                }
            } catch (Throwable ignored) {
                wuaList = new String[0];
            }
        }
        if (wuaList.length > 0) {
            // 修复：修正数组索引边界
            int index = RandomUtil.nextInt(0, wuaList.length);
            return wuaList[index];
        }
        return ""; // 返回空字符串而不是null
    }

    /** 服务端每日施肥次数名义上限（单次计，`wateringLeftTimes` 是剩余次数） */
    private static final int MAIN_SPREAD_DAILY_LIMIT = 200;

    /** 一键施肥一次顶 5 次（服务端按单次累加已施肥次数） */
    private static final int BATCH_SPREAD_SIZE = 5;

    /**
     * M 按场景配置操作次数；批量时一次操作算 5 次，达到每日上限时保留上游 199+5 边界。
     */
    private static final int MAIN_SPREAD_BURST_LIMIT = MAIN_SPREAD_DAILY_LIMIT - 1 + BATCH_SPREAD_SIZE;

    private int targetSpreadTimes(Integer configuredTimes) {
        int times = configuredTimes == null ? 0 : Math.max(configuredTimes, 0);
        if (Boolean.TRUE.equals(useBatchSpread.getValue())) {
            times *= BATCH_SPREAD_SIZE;
        }
        return Math.min(times, Boolean.TRUE.equals(useBatchSpread.getValue()) ? MAIN_SPREAD_BURST_LIMIT : MAIN_SPREAD_DAILY_LIMIT);
    }

    private static boolean shouldBatchSpread(int usedTimes, int limit) {
        if (limit > MAIN_SPREAD_DAILY_LIMIT) {
            return usedTimes <= MAIN_SPREAD_DAILY_LIMIT - 1
                    && usedTimes % BATCH_SPREAD_SIZE == BATCH_SPREAD_SIZE - 1;
        }
        return usedTimes + BATCH_SPREAD_SIZE <= limit;
    }

    /**
     * 检查是否可以施肥
     */
    private boolean canSpreadManure(PlantScene scene) {
        // 检查是否达到今日限制
        if (Status.hasFlagToday("spreadManureLimit:" + scene.name())) {
            return false;
        }

        Integer sceneLimit = orchardSpreadManureSceneList.get(scene.name());
        int limit = targetSpreadTimes(sceneLimit);
        if (limit <= 0) {
            return false;
        }

        try {
            switch (scene) {
                case main:
                    // 主场景施肥检查
                    JSONObject mainAccount = MyUtils.newJSONObject(AntOrchardRpcCall.orchardSyncIndex());
                    if (!MessageUtil.checkResultCode(TAG, mainAccount)) {
                        return false;
                    }
                    JSONObject accountInfo = mainAccount.optJSONObject("farmMainAccountInfo");
                    if (accountInfo == null) {
                        return false;
                    }
                    int happyPoint = Integer.parseInt(accountInfo.optString("happyPoint", "0"));
                    int wateringCost = accountInfo.optInt("wateringCost");
                    int leftTimes = accountInfo.optInt("wateringLeftTimes");
                    int usedTimes = MAIN_SPREAD_DAILY_LIMIT - leftTimes;

                    // 仅在每日次数配到 200（命中 199+5 漏洞）时才启用批量突破；其余数值精确施肥不批量
                    boolean batchEnabled = Boolean.TRUE.equals(useBatchSpread.getValue());
                    // 开启一键5次：前 (BATCH_SPREAD_SIZE-1)=4 次用单次把计数补到 ≡4(mod5)，之后持续批量。
                    // 计数序列 4,9,…,194,199,204 恰好命中漏洞（199 处批量 +5 = 204），且不会落在 200~203。
                    boolean batch = batchEnabled && shouldBatchSpread(usedTimes, limit);
                    // 一键5次时服务端一次要消耗 5 倍肥料，余额判据必须按批量算，否则会发出注定失败的请求
                    int needCost = batch ? wateringCost * BATCH_SPREAD_SIZE : wateringCost;
                    if (happyPoint < needCost) {
                        if (batch) {
                            // 肥料不足 5 倍：退回单次（若还能施单次）
                            batch = false;
                            needCost = wateringCost;
                            if (happyPoint < needCost) {
                                Log.record("农场施肥⏭️肥料不足[" + happyPoint + "/" + needCost + "g]");
                                return false;
                            }
                        } else {
                            Log.record("农场施肥⏭️肥料不足[" + happyPoint + "/" + needCost + "g]");
                            return false;
                        }
                    }
                    if (usedTimes >= limit) {
                        if (!spreadLimitLoggedThisRun) {
                            spreadLimitLoggedThisRun = true;
                            Log.record("农场施肥⏭️已达次数上限[" + usedTimes + "/" + limit + "]");
                        }
                        return false;
                    }
                    spreadUseBatchThisTime = batch;
                    return true;

                case yeb:
                    // 余额宝场景施肥检查
                    JSONObject yebProgress = MyUtils.newJSONObject(AntOrchardRpcCall.orchardIndex());
                    if (!MessageUtil.checkResultCode(TAG, yebProgress) || !yebProgress.has("yebScenePlantInfo")) {
                        return false;
                    }
                    JSONObject yebScenePlantInfo = yebProgress.optJSONObject("yebScenePlantInfo");
                    JSONObject progressInfo = yebScenePlantInfo != null ? yebScenePlantInfo.optJSONObject("plantProgressInfo") : null;
                    if (progressInfo == null) {
                        return false;
                    }
                    int currentProgress = progressInfo.optInt("spreadProgress");

                    // 仅在每日次数配到 200（命中 199+5 漏洞）时才启用批量突破；其余数值精确施肥不批量
                    boolean batchEnabledY = Boolean.TRUE.equals(useBatchSpread.getValue());
                    // 余额宝场景同样适用 199+5 漏洞：前 (BATCH_SPREAD_SIZE-1) 次单次补到 ≡4(mod5)，之后持续批量
                    boolean batchY = batchEnabledY && shouldBatchSpread(currentProgress, limit);
                    // yeb 肥料与主账号同池，仅在批量（需 5 倍）时取主账号余额做判据
                    if (batchY) {
                        JSONObject yebMain = MyUtils.newJSONObject(AntOrchardRpcCall.orchardSyncIndex());
                        if (!MessageUtil.checkResultCode(TAG, yebMain)) {
                            // 同步校验失败拿不到余额：保守退回单次，避免发出注定失败的 5 倍批量
                            batchY = false;
                        } else {
                            JSONObject yai = yebMain.optJSONObject("farmMainAccountInfo");
                            int happyPointY = yai != null ? yai.optInt("happyPoint") : 0;
                            int wateringCostY = yai != null ? yai.optInt("wateringCost") : 0;
                            if (yai == null || happyPointY < wateringCostY * BATCH_SPREAD_SIZE) {
                                // 肥料不足 5 倍：退回单次（沿用原 yeb 不判肥料，直接允许单次）
                                batchY = false;
                            }
                        }
                    }
                    // 修正原 `limit < dailyLimit`：允许推进到漏洞上限 204（dailyLimit 名义 200），避免 yeb 完全不施肥
                    if (currentProgress >= limit) {
                        return false;
                    }
                    spreadUseBatchThisTime = batchY;
                    return true;

                default:
                    return false;
            }
        } catch (Throwable t) {
            Log.err(TAG, "canSpreadManure err:", t);
            return false;
        }
    }

    /**
     * 切换种植场景
     */
    private boolean switchPlantScene(PlantScene scene) {
        try {
            String sceneName = scene.name();
            String result = AntOrchardRpcCall.switchPlantScene(sceneName);
            return MessageUtil.checkResultCode(TAG, MyUtils.newJSONObject(result));
        } catch (Throwable t) {
            Log.err(TAG, "switchPlantScene err:", t);
            return false;
        }
    }

    /**
     * 查询施肥活动奖励
     */
    private void querySpreadManureActivity() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntOrchardRpcCall.orchardIndex());
            if (MessageUtil.checkResultCode(TAG, jo) && jo.has("spreadManureActivity")) {
                JSONObject activity = jo.optJSONObject("spreadManureActivity");
                JSONObject stage = activity != null ? activity.optJSONObject("spreadManureStage") : null;
                if (stage != null && "FINISHED".equals(stage.optString("status"))) {
                    String result = AntOrchardRpcCall.receiveTaskAward(stage.optString("sceneCode"), stage.optString("taskType"));
                    JSONObject awardJo = MyUtils.newJSONObject(result);
                    if (MessageUtil.checkResultCode(TAG, awardJo)) {
                        int awardCount = awardJo.optInt("incAwardCount");
                        Log.farm("芭芭农场🎁丰收礼包#获得[" + awardCount + "g肥料]");
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "querySpreadManureActivity err:", t);
        }
    }

    /**
     * 农场任务列表处理
     */
    private static JSONObject orchardExtraDisplay(JSONObject task) {
        JSONObject display = task.optJSONObject("taskDisplayConfig");
        if (display != null) return display;
        JSONObject biz = task.optJSONObject("bizInfo");
        return biz != null ? biz : MyUtils.newJSONObject(task.optString("bizInfo"));
    }

    private static String orchardUrlParam(JSONObject display, String key) {
        List<String> urls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String target = display.optString("targetUrl");
        if (!target.isEmpty()) { urls.add(target); seen.add(target); }
        for (int i = 0; i < urls.size() && i < 12; i++) {
            android.net.Uri uri = android.net.Uri.parse(urls.get(i));
            if (!uri.isHierarchical()) continue;
            String value = uri.getQueryParameter(key);
            if (value != null && !value.isEmpty()) return value;
            for (String nestedKey : new String[]{"url", "sourceUrl", "schema"}) {
                String nested = uri.getQueryParameter(nestedKey);
                if (nested != null && seen.add(nested)) urls.add(nested);
            }
        }
        return "";
    }

    private static String orchardTaskSource(JSONObject display) {
        for (String key : new String[]{"source", "chInfo", "alipayFarmSource"}) {
            String value = orchardUrlParam(display, key);
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static boolean orchardExtraOk(JSONObject result) {
        String code = result.optString("resultCode");
        if (!code.isEmpty() && !"SUCCESS".equals(code) && !"100".equals(code) && !"200".equals(code)) return false;
        return (Boolean.TRUE.equals(result.opt("success")) || "100".equals(result.optString("resultCode"))
                || "SUCCESS".equals(result.optString("resultCode"))) && !RpcRequestGuard.isFailure(result);
    }

    private static JSONObject orchardUnwrapExtra(JSONObject result) {
        JSONObject nested = result.optJSONObject("resData");
        return nested == null || RpcRequestGuard.isFailure(result) ? result : nested;
    }

    private static boolean isExtraOrchardBrowse(JSONObject task) {
        String action = task.optString("actionType");
        return "ANTFARM_ORCHARD_NORMAL_GONGGEFANGWEN".equals(task.optString("taskId")) || "XLIGHT".equals(action)
                || (("VISIT".equals(action) || "SYSTEM_SWITCH".equals(action)) && "TAOBAO".equals(task.optString("taskPlantType")));
    }

    private void orchardStarTasks() {
        try {
            JSONObject response = MyUtils.newJSONObject(AntOrchardRpcCall.listStarTasks());
            JSONArray tasks = response.optJSONArray("taskInfos");
            if (!orchardExtraOk(response) || tasks == null) { Log.record("努力流星：任务查询失败或缺少列表，" + RpcRequestGuard.errorMessage(response)); return; }
            boolean listChanged = false;
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null || task.optString("taskType").isEmpty()) continue;
                String id = task.optString("taskType"), label = "努力流星｜" + orchardExtraDisplay(task).optString("title", id);
                if (!label.equals(AntOrchardTaskListMap.get(id))) { AntOrchardTaskListMap.add(id, label); listChanged = true; }
            }
            if (listChanged && !AntOrchardTaskListMap.save()) Log.record("努力流星：黑名单选项保存失败，请检查存储权限");
            Set<String> seen = new HashSet<>();
            if (tasks.length() == 0) Log.record("努力流星：本轮没有可执行任务");
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null || task.optString("taskType").isEmpty() || !seen.add(task.optString("taskType"))) continue;
                JSONObject display = orchardExtraDisplay(task);
                String id = task.optString("taskType"), title = display.optString("title", id);
                if (AntOrchardTaskList.getValue().contains(id) || AntOrchardTaskList.getValue().contains(title)) { Log.record("努力流星：跳过黑名单任务[" + title + "]"); continue; }
                runExtraOrchardTask(task, true);
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("努力流星：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "orchardStarTasks", t); }
    }

    private static int extraOrchardCount(JSONObject task, String key) {
        Object value = task.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try { int count = new java.math.BigDecimal(value.toString().trim()).intValueExact(); return count >= 0 ? count : -1;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private static String extraOrchardSnapshot(JSONObject task) {
        return task.optString("taskStatus") + ":" + extraOrchardCount(task, "rightsTimes") + ":" + extraOrchardCount(task, "taskProgress");
    }

    private static int extraOrchardStage(String state) {
        if ("RECEIVED".equals(state) || "DONE".equals(state) || "AWARDED".equals(state)) return 2;
        if ("FINISHED".equals(state) || "COMPLETE".equals(state) || "WAIT_RECEIVE".equals(state) || "TO_RECEIVE".equals(state) || "UNLOCKED".equals(state)) return 1;
        return "TODO".equals(state) || "WAIT_COMPLETE".equals(state) ? 0 : -1;
    }

    private static boolean extraOrchardAdvanced(JSONObject before, JSONObject after) {
        int from = extraOrchardStage(before.optString("taskStatus")), to = extraOrchardStage(after.optString("taskStatus"));
        if (from >= 0 && to > from) return true;
        if (to < 0 || to < from) return false;
        for (String key : new String[]{"rightsTimes", "taskProgress"}) {
            int old = extraOrchardCount(before, key), fresh = extraOrchardCount(after, key);
            if (old >= 0 && fresh > old) return true;
        }
        return false;
    }

    private JSONObject refreshExtraOrchardTask(String id, boolean star) throws Exception {
        JSONObject response = MyUtils.newJSONObject(star ? AntOrchardRpcCall.listStarTasks() : AntOrchardRpcCall.orchardListTask());
        JSONArray tasks = response.optJSONArray(star ? "taskInfos" : "taskList");
        if (!orchardExtraOk(response) || tasks == null) return null;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null && id.equals(task.optString(star ? "taskType" : "taskId"))) return task;
        }
        return null;
    }

    private void runExtraOrchardTask(JSONObject initial, boolean star) {
        String label = star ? "努力流星" : "农场浏览";
        String id = initial.optString(star ? "taskType" : "taskId");
        JSONObject task = initial;
        try {
            if (id.isEmpty()) { Log.record(label + "：任务缺少编号，跳过"); return; }
            for (int round = 0; round < 20; round++) {
                TimeUtil.sleep(0);
                JSONObject display = orchardExtraDisplay(task);
                String title = display.optString("title", id), state = task.optString("taskStatus");
                if ("RECEIVED".equals(state) || "DONE".equals(state) || "AWARDED".equals(state)) { Log.record(label + "：已完成并领奖[" + title + "]"); return; }
                String scene = task.optString("sceneCode");
                boolean rewardReady = "FINISHED".equals(state) || "COMPLETE".equals(state) || "WAIT_RECEIVE".equals(state) || "TO_RECEIVE".equals(state) || "UNLOCKED".equals(state);
                JSONObject acted;
                if (rewardReady) {
                    if (Boolean.TRUE.equals(task.opt("directReceiveAward"))) {
                        TimeUtil.sleep(500);
                        JSONObject after = refreshExtraOrchardTask(id, star);
                        if (after != null && extraOrchardAdvanced(task, after)) { task = after; continue; }
                        Log.record(label + "：任务已完成，自动发奖尚未确认[" + title + "]"); return;
                    }
                    String plantType = star ? "ANTIEP" : task.optString("taskPlantType");
                    if (plantType.isEmpty()) { Log.record(label + "：缺少领奖类型，跳过[" + title + "]"); return; }
                    acted = MyUtils.newJSONObject(AntOrchardRpcCall.triggerTbTask(id, plantType));
                } else if ("TODO".equals(state) || "WAIT_COMPLETE".equals(state)) {
                    int limit = extraOrchardCount(task, "rightsTimesLimit");
                    if (limit > 0 && extraOrchardCount(task, "rightsTimes") >= limit) { Log.record(label + "：已达到任务次数上限，领奖以服务端状态为准[" + title + "]"); return; }
                    if (star) {
                        if (scene.isEmpty()) { Log.record(label + "：缺少任务场景，跳过[" + title + "]"); return; }
                        if (!"ORCHARD_NORMAL_STAR".equals(id) && !reportOrchardStarGame(task, display)) return;
                        acted = MyUtils.newJSONObject(AntOrchardRpcCall.finishStarTask(scene, id));
                    } else { acted = performOrchardBrowse(task, display); if (acted == null) return; }
                } else { Log.record(label + "：不支持的任务状态[" + state + "]，跳过[" + title + "]"); return; }
                TimeUtil.sleep(500);
                JSONObject after = refreshExtraOrchardTask(id, star);
                if (after == null) { Log.record(label + "：动作后回查失败或任务缺失，结果未确认[" + title + "]"); return; }
                if (!extraOrchardAdvanced(task, after)) {
                    Log.record(label + "：进度未推进，停止[" + title + "]；code=" + acted.optString("resultCode", acted.optString("code", "缺失")) + "，原因=" + RpcRequestGuard.errorMessage(acted)); return;
                }
                String message = label + "：" + (rewardReady ? "领奖状态已推进" : "完成进度已推进") + "[" + title + "]，" + extraOrchardSnapshot(task) + "→" + extraOrchardSnapshot(after);
                Log.record(message); Log.farm(message); task = after;
            }
            Log.record(label + "：达到本轮20阶段上限，下轮继续");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record(label + "：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "runExtraOrchardTask", t); }
    }

    private boolean reportOrchardStarGame(JSONObject task, JSONObject display) throws Exception {
        JSONObject ball = display.optJSONObject("floatBallConfig");
        int seconds = ball == null ? -1 : extraOrchardCount(ball, "floatBallDuration");
        String app = "", source = "";
        for (JSONObject root : new JSONObject[]{display, task, ball, display.optJSONObject("gameInfo"), display.optJSONObject("extend"), task.optJSONObject("extend")}) {
            if (root == null) continue;
            for (String key : new String[]{"gameAppId", "appId", "game_id"}) if (app.isEmpty() && root.opt(key) instanceof String) app = root.optString(key).trim();
            for (String key : new String[]{"source", "chInfo", "oriChInfo", "alipayFarmSource"}) if (source.isEmpty() && root.opt(key) instanceof String) source = root.optString(key).trim();
        }
        for (String key : new String[]{"gameAppId", "appId", "game_id"}) if (app.isEmpty()) app = orchardUrlParam(display, key);
        for (String key : new String[]{"source", "chInfo", "oriChInfo", "alipayFarmSource"}) if (source.isEmpty()) source = orchardUrlParam(display, key);
        if (!"nongchangleyuan".equals(display.optString("type")) || seconds <= 0 || seconds > 1800 || app.isEmpty() || source.isEmpty()) {
            Log.record("努力流星：游戏缺少有效appId、来源或时长，不支持完成；跳过[" + task.optString("taskType") + "]"); return false;
        }
        Log.record("努力流星：按任务要求等待" + (seconds + 1) + "秒[" + display.optString("title", task.optString("taskType")) + "]");
        TimeUtil.sleep((seconds + 1) * 1000L);
        JSONObject result = MyUtils.newJSONObject(AntOrchardRpcCall.submitUserPlayDurationAction(app, source, seconds + 1));
        if (!orchardExtraOk(result)) { Log.record("努力流星：游戏时长上报失败，" + RpcRequestGuard.errorMessage(result)); return false; }
        return true;
    }

    private JSONObject performOrchardBrowse(JSONObject task, JSONObject display) throws Exception {
        String source = orchardTaskSource(display), action = task.optString("actionType");
        if ("ANTFARM_ORCHARD_NORMAL_GONGGEFANGWEN".equals(task.optString("taskId")) || "TAOBAO".equals(task.optString("taskPlantType"))) {
            if (source.isEmpty()) { Log.record("农场浏览：缺少任务提供的访问来源，跳过"); return null; }
            return orchardUnwrapExtra(MyUtils.newJSONObject(AntOrchardRpcCall.orchardVisit(source, "TAOBAO".equals(task.optString("taskPlantType")))));
        }
        if (!"XLIGHT".equals(action)) return null;
        String pageUrl = orchardUrlParam(display, "url");
        if (pageUrl.isEmpty() && display.optString("targetUrl").startsWith("https://")) pageUrl = display.optString("targetUrl");
        String space = orchardUrlParam(display, "spaceCodeFeeds"), token = orchardUrlParam(display, "tokenFeeds");
        String scene = orchardUrlParam(display, "iepTaskSceneCode"), type = orchardUrlParam(display, "iepTaskType");
        if (!pageUrl.startsWith("https://") || space.isEmpty()) { Log.record("农场浏览：广告任务缺少有效页面或spaceCodeFeeds，跳过"); return null; }
        JSONObject ext = MyUtils.newJSONObject(), refer = MyUtils.newJSONObject();
        if (!token.isEmpty()) refer.put("referToken", token);
        String limit = orchardUrlParam(display, "canDoTaskTimesLimit");
        if (token.isEmpty() && !limit.isEmpty()) ext.put("canDoTaskTimesLimit", limit);
        String session = "u_" + RandomUtil.getRandomString(5) + "_" + RandomUtil.getRandomString(5), cursor = "";
        Set<String> seenPages = new HashSet<>();
        for (int page = 1; page <= 5; page++) {
            JSONObject position = MyUtils.newJSONObject().put("extMap", ext).put("referInfo", refer).put("spaceCode", space).put("searchInfo", MyUtils.newJSONObject());
            if (page > 1 && token.isEmpty() && pageUrl.contains("multi-stage-task.html") && !scene.isEmpty() && !type.isEmpty()) position.put("searchInfo", MyUtils.newJSONObject().put("rangeFilter", "goodsPrice:-").put("tabKey", "all"));
            JSONObject sdk = MyUtils.newJSONObject().put("adComponentType", "FEEDS").put("adComponentVersion", "4.30.21").put("enableFusion", true)
                    .put("networkType", "WWAN").put("pageFrom", "ch_url-https://render.alipay.com/p/yuyan/180020010001263018/game.html")
                    .put("pageNo", page).put("pageUrl", pageUrl).put("session", session).put("unionAppId", "2060090000304921")
                    .put("usePlayLink", "true").put("xlightRuntimeSDKversion", "4.30.21").put("xlightSDKType", "h5").put("xlightSDKVersion", "4.30.21");
            if (!cursor.isEmpty()) sdk.put("playingPageInfo", cursor);
            JSONObject envelope = MyUtils.newJSONObject(AntOrchardRpcCall.orchardXlight(position, sdk));
            JSONObject result = orchardUnwrapExtra(envelope);
            JSONObject playing = result.optJSONObject("playingResult");
            if (RpcRequestGuard.isFailure(envelope) || RpcRequestGuard.isFailure(result) || playing == null) { Log.record("农场浏览：广告查询失败或缺少playingResult，" + RpcRequestGuard.errorMessage(result)); return null; }
            String biz = playing.optString("playingBizId");
            JSONObject detail = playing.optJSONObject("eventRewardDetail");
            JSONArray events = detail == null ? null : detail.optJSONArray("eventRewardInfoList");
            JSONObject selected = null;
            if (!biz.isEmpty() && events != null) for (int i = 0; i < events.length(); i++) {
                JSONObject event = events.optJSONObject(i);
                if (event != null && "BROWSE".equals(event.optString("playingEventType")) && (selected == null || event.optInt("order", Integer.MAX_VALUE) < selected.optInt("order", Integer.MAX_VALUE))) selected = event;
            }
            if (selected != null) {
                int seconds = selected.has("eventStep") ? extraOrchardCount(selected, "eventStep") : 15;
                if (seconds <= 0 || seconds > 1800) { Log.record("农场浏览：浏览事件时长无效，停止"); return null; }
                Log.record("农场浏览：按事件要求等待" + seconds + "秒[" + display.optString("title", task.optString("taskId")) + "]");
                TimeUtil.sleep(seconds * 1000L);
                return orchardUnwrapExtra(MyUtils.newJSONObject(AntOrchardRpcCall.finishOrchardBrowse(biz, selected, scene, type)));
            }
            cursor = playing.optString("playingPageInfo");
            if (cursor.isEmpty() || !seenPages.add(cursor)) { Log.record("农场浏览：没有可完成事件或分页重复，停止"); return null; }
        }
        Log.record("农场浏览：达到5页上限，未发现可完成事件"); return null;
    }

    private void orchardListTask() {
        try {
            String result = AntOrchardRpcCall.orchardListTask();
            JSONObject jo = MyUtils.newJSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }

            boolean inTeam = jo.optBoolean("inTeam", false);
            Log.record(inTeam ? "当前为芭芭农场 team 模式（合种/帮帮种已开启）" : "当前为普通单人农场模式");

            // 处理签到任务
            JSONObject signTaskInfo = jo.optJSONObject("signTaskInfo");
            if (signTaskInfo != null) {
                handleSignTask(signTaskInfo);
            }

            // 处理任务列表
            JSONArray taskArray = jo.optJSONArray("taskList");
            if (taskArray != null) {
                handleTaskList(taskArray);
            }

            // 触发已完成任务的奖励
            triggerTbTask();
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "orchardListTask err:", t);
        }
    }

    /**
     * 处理签到任务
     */
    private void handleSignTask(JSONObject signInfo) {
        if (Status.hasFlagToday("orchardSign")) {
            return;
        }

        try {
            JSONObject currentSign = signInfo.optJSONObject("currentSignItem");
            if (currentSign == null) {
                return;
            }
            if (currentSign.optBoolean("signed")) {
                Log.record("农场今日已签到");
                Status.flagToday("orchardSign", userId);
                return;
            }

            // 执行签到
            String result = AntOrchardRpcCall.orchardSign();
            JSONObject signJo = MyUtils.newJSONObject(result);
            if (MessageUtil.checkResultCode(TAG, signJo)) {
                JSONObject newSignTaskInfo = signJo.optJSONObject("signTaskInfo");
                JSONObject newSignInfo = newSignTaskInfo != null ? newSignTaskInfo.optJSONObject("currentSignItem") : null;
                if (newSignInfo == null) {
                    return;
                }
                int continuousDays = newSignInfo.optInt("currentContinuousCount");
                int award = newSignInfo.optInt("awardCount");
                Log.farm("农场任务📅七天签到[第" + continuousDays + "天]#获得[" + award + "g肥料]");
                Status.flagToday("orchardSign", userId);
            }
        } catch (Throwable t) {
            Log.err(TAG, "handleSignTask err:", t);
        }
    }

    /**
     * 处理任务列表
     */
    private void handleTaskList(JSONArray taskArray) {
        try {
            for (int i = 0; i < taskArray.length(); i++) {
                JSONObject jo = taskArray.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String taskStatus = jo.optString("taskStatus");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    continue;
                }

                // 跳过黑名单任务：用户配置/自动拉黑确认
                String groupId = jo.optString("groupId", "");
                String taskId = jo.optString("taskId", "");
                JSONObject displayConfig = jo.optJSONObject("taskDisplayConfig");
                String title = displayConfig != null ? displayConfig.optString("title", "未知任务") : "未知任务";
                if (AntOrchardTaskList.getValue().contains(title) || AntOrchardTaskList.getValue().contains(taskId) || AntOrchardTaskList.getValue().contains(groupId)) continue;
                boolean staticBlocked = ORCHARD_TASK_BLACKLIST.contains(title) || ORCHARD_TASK_BLACKLIST.contains(taskId)
                        || ORCHARD_TASK_BLACKLIST.contains(groupId);
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    // taskId 与 groupId 都查（服务端两处键名不统一，实测 ORCHARD_NORMAL_CHONGZHI9 /
                    // ORCHARD_NCLY_CHARGE1_XDDQ 这类"充值"任务正是靠这两个键识别的）。
                    if (TaskAlternative.isTransactionTask(jo.optString("taskId"))
                            || TaskAlternative.isTransactionTask(groupId)) {

                        String skipFlag = "transactionSkip::orchard::" + taskId + "::" + groupId;
                        if (!Status.hasFlagToday(skipFlag)) {
                            Status.flagToday(skipFlag);
                            Log.farm("肥料任务⏭️交易/履约类[" + title + "]#不申报，不修改黑名单");
                        }
                        continue;
                    }
                }
                if (orchardFloatBallTask.getValue() && displayConfig != null && displayConfig.optJSONObject("floatBallConfig") != null) {
                    if (!staticBlocked) AntOrchardVisitTask.floatBall(jo, orchardVisitDailyBudget.getValue());
                    continue;
                }
                if (isExtraOrchardBrowse(jo)) {
                    runExtraOrchardTask(jo, false);
                    continue;
                }
                if (staticBlocked) continue;

                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (!finishOrchardTask(jo)) {
                        continue;
                    }
                    TimeUtil.sleep(500);
                }

                // 处理已完成的任务奖励（已在triggerTbTask中统一处理）
            }
            // 核对本轮 doFarmTask 的结果（响应不可信，以任务列表为准）
            verifyPendingTasksByList();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "handleTaskList err:", t);
        }
    }

    /**
     * 完成农场任务
     */
    private boolean finishOrchardTask(JSONObject task) {
        try {
            if (!task.has("taskDisplayConfig")) {
                return false;
            }
            JSONObject taskDisplayConfig = task.optJSONObject("taskDisplayConfig");
            if (taskDisplayConfig == null || !taskDisplayConfig.has("title")) {
                return false;
            }
            String title = taskDisplayConfig.optString("title");
            if (MyUtils.closeUnRpc() && "逛一逛一淘".equals(title)) return false;
            String actionType = task.optString("actionType");
            String sceneCode = task.optString("sceneCode");
            String taskId = task.optString("taskId");

            // 处理广告任务（VISIT、XLIGHT类型）
            if ("VISIT".equals(actionType) || "XLIGHT".equals(actionType)) {
                int rightsTimes = task.optInt("rightsTimes", 0);
                int rightsTimesLimit = task.optInt("rightsTimesLimit", 0);

                // 从extend字段获取限制次数
                JSONObject extend = task.optJSONObject("extend");
                if (extend != null && rightsTimesLimit <= 0) {
                    String limitStr = extend.optString("rightsTimesLimit", "");
                    if (!limitStr.isEmpty()) {
                        try {
                            rightsTimesLimit = Integer.parseInt(limitStr);
                        } catch (Exception ignored) {
                        }
                    }
                }

                int timesToDo = (rightsTimesLimit > 0) ? (rightsTimesLimit - rightsTimes) : 1;
                if (timesToDo <= 0) {
                    return true;
                }

                for (int cnt = 0; cnt < timesToDo; cnt++) {
                    // taskId 当作 taskType 传给 finishTask（该 RPC 的参数名就叫 taskType）
                    String via = finishTaskTwice(sceneCode, title, taskId);
                    if (via != null) {
                        Log.farm("肥料任务🧾完成[" + title + "]第" + (rightsTimes + cnt + 1) + "次#" + via);
                    } else {
                        break;
                    }
                    TimeUtil.sleep(500);
                }
                return true;
            }

            // 处理触发型任务
            if ("TRIGGER".equals(actionType) || "ADD_HOME".equals(actionType) || "PUSH_SUBSCRIBE".equals(actionType)) {
                // taskId 当作 taskType 传递
                String via = finishTaskTwice(sceneCode, title, taskId);
                if (via != null) {
                    Log.farm("肥料任务🧾完成[" + title + "]#" + via);
                }
                return true;
            }

            //配合黑名单的兜底操作
            String via = finishTaskTwice(sceneCode, title, taskId);
            if (via != null) {
                Log.farm("肥料任务🧾完成[" + title + "]#" + via);
            }
            return true;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "finishOrchardTask err:", t);
            return false;
        }
    }

    /**
     * `doFarmTask` 已发出、但响应不足以判定成败的任务：{@code taskId -> 标题}。
     * <p>由 {@link #verifyPendingTasksByList()} 在列表处理完后用任务列表状态核对。
     */
    private final LinkedHashMap<String, String> pendingVerifyTasks = new LinkedHashMap<>();

    /** 同轮核对配置（见 TaskAlternative.verify） */
    private static final TaskAlternative.VerifyConfig VERIFY_CFG = new TaskAlternative.VerifyConfig(
            "AntOrchard", "AntOrchardTaskList", "农场肥料任务", "肥料任务", "🧾完成", true, msg -> Log.farm(msg));

    /**
     * 完成任务（两条腿）：先 {@code finishTask}，不成功再用 {@code taskId} 当 bizKey 走 doFarmTask
     * （响应不可信，见 {@link TaskAlternative}）。
     *
     * @return 生效的接口名（finishTask / doFarmTask）；两条都失败或异常返回 null
     */
    private String finishTaskTwice(String sceneCode, String taskTitle, String taskId) {
        try {
            JSONObject finishResponse = MyUtils.newJSONObject(AntOrchardRpcCall.finishTask(sceneCode, taskId));
            if (MessageUtil.checkSuccess(TAG, finishResponse)) {
                return "finishTask";
            }
            JSONObject doFarmResponse = MyUtils.newJSONObject(AntOrchardRpcCall.doFarmTask(taskId, sceneCode));
            if (MessageUtil.checkSuccess(TAG, doFarmResponse)) {
                return "doFarmTask";
            }
            // 400000040 可能已被另一种实现方案做成，不拉黑、记 pending 交给列表核对；其它错误码=真做不了，照常拉黑
            if (!MessageUtil.isUnsupportedRpc(finishResponse)) {
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntOrchardTaskList", taskTitle, finishResponse);
            } else {
                // doFarmTask 的响应对这类任务**不可信**：实测回 102「服务器正在开小差」但任务其实被做成了
                // （rightsTimes 0→1、状态转 RECEIVED）；也有同样回 102 而真没做成的。
                // 所以既不能据响应判失败（会把做成的任务拉黑），也不能判成功（真做不了的会每轮白试）：
                // 记下来，由 handleTaskList 在列表处理完后**按任务列表状态核对**
                pendingVerifyTasks.put(taskId, taskTitle);
            }
            Log.i("肥料任务🕓已触发[" + taskTitle + "]#finishTask=" + finishResponse.optString("code")
                    + "#doFarmTask=" + TaskAlternative.describe(doFarmResponse) + "，结果以任务列表为准");
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "finishTaskTwice err:", t);
        }
        return null;
    }

    /**
     * 核对「已触发但响应不可信」的任务：等几秒后重拉任务列表，**仍未完成**的才计入自动拉黑。
     * <p>为什么以任务列表为准：见 {@link #finishTaskTwice} 的注释——响应会撒谎（回 102 但已做成），
     * 服务端是异步推进状态的，只有任务列表的 {@code taskStatus} 才是最终判据。
     */
    private void verifyPendingTasksByList() {
        TaskAlternative.verify(pendingVerifyTasks, VERIFY_CFG, unknown -> {
            JSONObject jo = MyUtils.newJSONObject(AntOrchardRpcCall.orchardListTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            JSONArray taskArray = jo.optJSONArray("taskList");
            if (taskArray == null) {
                return null;
            }
            Set<String> stillTodo = new HashSet<>();
            for (int i = 0; i < taskArray.length(); i++) {
                JSONObject task = taskArray.optJSONObject(i);
                if (task == null || !(task.opt("taskId") instanceof String)) return null;
                String taskId = task.optString("taskId", "");
                String taskStatus = task.optString("taskStatus");
                if (taskId.isEmpty()) return null;
                if (!(TaskStatus.TODO.name().equals(taskStatus)
                        || TaskStatus.FINISHED.name().equals(taskStatus)
                        || TaskStatus.RECEIVED.name().equals(taskStatus))) {
                    unknown.put(taskId, taskStatus);
                    continue;
                }
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    stillTodo.add(taskId);
                }
            }
            return stillTodo;
        });
    }

    /**
     * 触发淘宝任务奖励（领取所有已完成任务的奖励）
     */
    private void triggerTbTask() {
        try {
            String response = AntOrchardRpcCall.orchardListTask();
            JSONObject jo = MyUtils.newJSONObject(response);

            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray taskList = jo.optJSONArray("taskList");
                for (int i = 0; taskList != null && i < taskList.length(); i++) {
                    JSONObject task = taskList.optJSONObject(i);
                    if (task == null || !"FINISHED".equals(task.optString("taskStatus"))) {
                        continue;
                    }

                    JSONObject taskDisplayConfig = task.optJSONObject("taskDisplayConfig");
                    String title = taskDisplayConfig != null ? taskDisplayConfig.optString("title") : "";
                    int awardCount = task.optInt("awardCount", 0);
                    String taskId = task.optString("taskId");
                    String taskPlantType = task.optString("taskPlantType");

                    // 跳过淘宝类型的任务（需要手动操作）
                    //if ("TAOBAO".equals(taskPlantType)) {
                    //    continue;
                    //}

                    String triggerResponse = AntOrchardRpcCall.triggerTbTask(taskId, taskPlantType);
                    JSONObject triggerJo = MyUtils.newJSONObject(triggerResponse);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntOrchardTaskList", title, triggerJo);
                    if (MessageUtil.checkResultCode(TAG, triggerJo)) {
                        Log.farm("肥料领取🎖️任务[" + title + "]奖励#获得[" + awardCount + "g]");
                    } else {
                        //Log.farm("领取奖励失败: " + triggerJo.toString());
                    }
                }
            } else {
                Log.record("获取任务列表失败: " + jo.optString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.err(TAG, "triggerTbTask err:", t);
        }
    }

    /**
     * 领取七日礼包
     */
    private void drawLotteryPlus(JSONObject lotteryInfo) {
        if (Status.hasFlagToday("orchardLotteryPlus")) {
            return;
        }

        try {
            if (!lotteryInfo.has("userSevenDaysGiftsItem")) {
                return;
            }

            JSONObject giftItem = lotteryInfo.optJSONObject("userSevenDaysGiftsItem");
            JSONArray dailyGifts = giftItem != null ? giftItem.optJSONArray("userEverydayGiftItems") : null;
            String itemId = lotteryInfo.optString("itemId");
            if (dailyGifts == null) {
                return;
            }

            // 检查今日是否已领取
            for (int i = 0; i < dailyGifts.length(); i++) {
                JSONObject daily = dailyGifts.optJSONObject(i);
                if (daily != null && daily.optString("itemId").equals(itemId) && daily.optBoolean("received")) {
                    Log.record("芭芭农场七日礼包当日奖励已领取");
                    Status.flagToday("orchardLotteryPlus", userId);
                    return;
                }
            }

            // 领取礼包
            String result = AntOrchardRpcCall.drawLottery();
            JSONObject drawJo = MyUtils.newJSONObject(result);
            if (MessageUtil.checkResultCode(TAG, drawJo)) {
                JSONObject lotteryPlusInfo = drawJo.optJSONObject("lotteryPlusInfo");
                JSONObject giftsItem = lotteryPlusInfo != null ? lotteryPlusInfo.optJSONObject("userSevenDaysGiftsItem") : null;
                JSONArray awardArray = giftsItem != null ? giftsItem.optJSONArray("userEverydayGiftItems") : null;
                if (awardArray == null) {
                    return;
                }

                for (int i = 0; i < awardArray.length(); i++) {
                    JSONObject award = awardArray.optJSONObject(i);
                    if (award != null && award.optString("itemId").equals(itemId)) {
                        int count = award.optInt("awardCount", 1);
                        Log.farm("芭芭农场🎁七日礼包#获得[" + count + "g肥料]");
                        Status.flagToday("orchardLotteryPlus", userId);
                        return;
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "drawLotteryPlus err:", t);
        }
    }

    /**
     * 获取额外信息（每日肥料包）
     */
    private void extraInfoGet() {
        if (Status.hasFlagToday("orchard::fertilizerPacket")) {
            return;
        }
        try {
            String result = AntOrchardRpcCall.extraInfoGet();
            JSONObject jo = MyUtils.newJSONObject(result);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                JSONObject extraData = data != null ? data.optJSONObject("extraData") : null;
                JSONObject fertilizerPacket = extraData != null ? extraData.optJSONObject("fertilizerPacket") : null;
                if (fertilizerPacket == null) {
                    return;
                }

                if ("todayFertilizerFinish".equals(fertilizerPacket.optString("status"))) {
                    Status.flagToday("orchard::fertilizerPacket", userId);
                    return;
                }
                if ("todayFertilizerWaitTake".equals(fertilizerPacket.optString("status"))) {
                    int fertilizerNum = fertilizerPacket.optInt("todayFertilizerNum");
                    String takeResult = AntOrchardRpcCall.extraInfoSet();
                    if (MessageUtil.checkResultCode(TAG, MyUtils.newJSONObject(takeResult))) {
                        Log.farm("每日肥料💩[" + fertilizerNum + "g]");
                        // 回读确认服务端已离开「待领取」态才落当日标记；状态未知时宁可不打，下轮再查
                        JSONObject verify = MyUtils.newJSONObject(AntOrchardRpcCall.extraInfoGet());
                        JSONObject verifyData = verify.optJSONObject("data");
                        JSONObject verifyExtra = verifyData == null ? null : verifyData.optJSONObject("extraData");
                        JSONObject verifyPacket = verifyExtra == null ? null : verifyExtra.optJSONObject("fertilizerPacket");
                        if (MessageUtil.checkResultCode(TAG, verify) && verifyPacket != null
                                && "todayFertilizerFinish".equals(verifyPacket.optString("status"))) {
                            Status.flagToday("orchard::fertilizerPacket", userId);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "extraInfoGet err:", t);
        }
    }

    /**
     * 好友助力
     */
    private void orchardAssistFriend() {
        if (Status.hasFlagToday("orchardAssistLimit")) {
            return;
        }

        Set<String> friendList = assistFriendList.getValue();
        if (friendList == null || friendList.isEmpty()) {
            return;
        }

        try {
            for (String friendId : friendList) {
                if (Status.hasFlagToday("orchardAssist:" + friendId)) {
                    continue;
                }

                String result = AntOrchardRpcCall.achieveBeShareP2P(friendId);
                JSONObject jo = MyUtils.newJSONObject(result);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("芭芭农场🌳助力好友[" + UserIdMap.getShowName(friendId) + "]");
                } else if ("600000027".equals(jo.optString("code"))) {
                    Status.flagToday("orchardAssistLimit", userId);
                    return;
                }

                Status.flagToday("orchardAssist:" + friendId, userId);
                TimeUtil.sleep(5000);
            }
        } catch (Throwable t) {
            Log.err(TAG, "orchardAssistFriend err:", t);
        }
    }

    /**
     * 查询子场景活动（许愿、营地接管等）
     */
    private void querySubplotsActivity(String activityType) {
        try {
            String result = AntOrchardRpcCall.querySubplotsActivity(activityType);
            JSONObject jo = MyUtils.newJSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }

            JSONArray activityList = jo.optJSONArray("subplotsActivityList");
            for (int i = 0; activityList != null && i < activityList.length(); i++) {
                JSONObject activity = activityList.optJSONObject(i);
                if (activity == null || !activityType.equals(activity.optString("activityType"))) {
                    continue;
                }

                if ("WISH".equals(activityType)) {
                    handleWishActivity(activity);
                } else if ("CAMP_TAKEOVER".equals(activityType)) {
                    handleCampTakeoverActivity(activity);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "querySubplotsActivity err:", t);
        }
    }

    /**
     * 处理许愿活动
     */
    private void handleWishActivity(JSONObject activity) {
        try {
            String activityId = activity.optString("activityId");
            String status = activity.optString("status");

            // 已完成则领取奖励
            if ("FINISHED".equals(status)) {
                String result = AntOrchardRpcCall.receiveOrchardRights(activityId, "WISH");
                JSONObject jo = MyUtils.newJSONObject(result);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    int amount = jo.optInt("amount");
                    Log.farm("农场许愿✨完成承诺#获得[" + amount + "g肥料]");
                    querySubplotsActivity("WISH"); // 重新查询状态
                }
                return;
            }

            // 未开始则许下承诺
            if ("NOT_STARTED".equals(status)) {
                Integer mainCount = orchardSpreadManureSceneList.get("main");
                int targetCount = mainCount != null && mainCount >= 10 ? 10 : (mainCount != null && mainCount >= 3 ? 3 : 0);

                if (targetCount > 0) {
                    JSONObject extend = MyUtils.newJSONObject(activity.optString("extend"));
                    JSONArray options = extend.optJSONArray("wishActivityOptionList");

                    for (int i = 0; options != null && i < options.length(); i++) {
                        JSONObject option = options.optJSONObject(i);
                        if (option != null && option.optInt("taskRequire") == targetCount) {
                            String result = AntOrchardRpcCall.triggerSubplotsActivity(activityId, "WISH", option.optString("optionKey"));
                            if (MessageUtil.checkResultCode(TAG, MyUtils.newJSONObject(result))) {
                                Log.farm("农场许愿✨许下承诺[每日施肥" + targetCount + "次]");
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "handleWishActivity err:", t);
        }
    }

    /**
     * 处理营地接管活动
     */
    private void handleCampTakeoverActivity(JSONObject activity) {
        try {
            JSONObject extend = MyUtils.newJSONObject(activity.optString("extend"));
            JSONObject currentInfo = extend.optJSONObject("currentActivityInfo");
            if (currentInfo == null) {
                return;
            }
            String status = currentInfo.optString("activityStatus");

            // 待选择奖励
            if ("TO_CHOOSE_PRIZE".equals(status)) {
                JSONArray prizes = currentInfo.optJSONArray("recommendPrizeList");
                for (int i = 0; prizes != null && i < prizes.length(); i++) {
                    JSONObject prize = prizes.optJSONObject(i);
                    if (prize != null && "FEILIAO".equals(prize.optString("prizeType"))) {
                        String result = AntOrchardRpcCall.choosePrize(prize.optString("sendOrderId"));
                        JSONObject jo = MyUtils.newJSONObject(result);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            JSONObject curActivityInfo = jo.optJSONObject("currentActivityInfo");
                            JSONObject currentPrize = curActivityInfo != null ? curActivityInfo.optJSONObject("currentPrize") : null;
                            String prizeName = currentPrize != null ? currentPrize.optString("prizeName") : "";
                            Log.farm("速成奖励✨接受挑战#选择[" + prizeName + "]");
                        }
                        break;
                    }
                }
            }

            // 待完成任务
            if ("TO_DO_TASK".equals(status)) {
                JSONArray tasks = currentInfo.optJSONArray("taskList");
                if (tasks != null) {
                    handleTaskList(tasks);
                }
                querySubplotsActivity("CAMP_TAKEOVER"); // 重新查询状态
            }
        } catch (Throwable t) {
            Log.err(TAG, "handleCampTakeoverActivity err:", t);
        }
    }

    /**
     * 查询余额宝收益
     */
    private void queryYebRevenueDetail() {
        try {
            String result = AntOrchardRpcCall.yebPlantSceneRevenuePage();
            JSONObject jo = MyUtils.newJSONObject(result);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                // 服务端常回 error 3000「系统出错，正在排查」⇒ 详情列表拿不到。
                // 但调用点的判据 yebSceneActivityInfo.revenueNotReceived=true 本身就是"有未领收益"的证据，
                // 故不再直接放弃，改为直接触发摇钱树（没收益时服务端会拒绝，不会误领）。
                Log.record("摇钱树收益详情未获取，改走直接触发：" + jo.optString("resultCode"));
                triggerYebMoneyTreeOnce();
                return;
            }

            JSONArray revenueList = jo.optJSONArray("yebRevenueDetailList");
            if (revenueList == null) {
                Log.record("摇钱树收益详情结构异常：" + jo.optString("resultCode"));
                triggerYebMoneyTreeOnce();
                return;
            }
            for (int i = 0; i < revenueList.length(); i++) {
                JSONObject revenue = revenueList.optJSONObject(i);
                if (revenue != null && "I".equals(revenue.optString("orderStatus"))) {
                    triggerYebMoneyTreeOnce();
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryYebRevenueDetail err:", t);
        }
    }

    /**
     * 触发摇钱树领奖：无论成败都留痕（原实现失败时静默，导致"到底是没收益还是接口不通"无法判断）。
     */
    private void triggerYebMoneyTreeOnce() {
        try {
            String triggerResult = AntOrchardRpcCall.triggerYebMoneyTree();
            JSONObject triggerJo = MyUtils.newJSONObject(triggerResult);
            if (!MessageUtil.checkResultCode(TAG, triggerJo)) {
                Log.record("摇钱树触发未成功：" + triggerJo.optString("resultCode"));
                return;
            }
            // 只记"已受理"：金额是触发响应当次返回的值，不作为"已到账收益"上报
            Log.farm("💰领取奖励[摇钱树]");
        } catch (Throwable t) {
            Log.err(TAG, "triggerYebMoneyTree err:", t);
        }
    }

    /**
     * 砸金蛋
     */
    private void smashedGoldenEgg(int unsmashedGoldenEggs) {
        try {
            // 循环砸蛋，因为你的RPC方法不支持批量
            for (int i = 0; i < unsmashedGoldenEggs; i++) {
                String response = AntOrchardRpcCall.smashedGoldenEgg();
                JSONObject jo = MyUtils.newJSONObject(response);

                if (MessageUtil.checkResultCode(TAG, jo)) {

                    JSONObject goldenEggInfoVO = jo.optJSONObject("goldenEggInfoVO");
                    int unsmashedGoldenEggsNow = goldenEggInfoVO != null ? goldenEggInfoVO.optInt("unsmashedGoldenEggs") : 0;
                    JSONArray batchSmashedList = jo.optJSONArray("batchSmashedList");
                    if (batchSmashedList != null && batchSmashedList.length() > 0) {
                        for (int j = 0; j < batchSmashedList.length(); j++) {
                            JSONObject smashedItem = batchSmashedList.optJSONObject(j);
                            if (smashedItem != null) {
                                int manureCount = smashedItem.optInt("manureCount", 0);
                                boolean jackpot = smashedItem.optBoolean("jackpot", false);
                                String unsmashedGoldenEggsString = "";
                                if (unsmashedGoldenEggsNow >= 0) {
                                    unsmashedGoldenEggsString = "[剩蛋" + unsmashedGoldenEggsNow + "个]";
                                }

                                String jackpotMessage = jackpot ? "（触发大奖）" : "";
                                Log.farm("砸出肥料🎖️" + manureCount + "g" + unsmashedGoldenEggsString + jackpotMessage + "");
                            }
                        }
                    }

                } else {
                    Log.farm("砸金蛋失败: " + jo.optString("resultDesc", "未知错误"));
                }

                // 每次砸蛋后等待一下
                TimeUtil.sleep(500);
            }
        } catch (Throwable t) {
            Log.err(TAG, "smashedGoldenEgg err:", t);
        }
    }

    /**
     * 领取小组件回访/浏览奖励。
     * <p>实测（2026-09-22 抓包 logs/chk_orchard 14:20:10，官方每次进入农场都会调）服务端返回
     * {@code {"canCollect":false,"manureCount":0,"needManualReceive":false,"success":true}}——
     * **没有 `orchardVisitAwardList` 结构**。原先按该字段解析，永远走"无奖励"分支并打误导性日志，
     * 调用点也因此被注释掉，导致这块奖励一直没领过。现按真实字段解析。
     */
    private void receiveOrchardVisitAward() {
        try {
            String response = AntOrchardRpcCall.receiveOrchardVisitAward();
            JSONObject jo = MyUtils.newJSONObject(response);
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                Log.farm("领取回访奖励失败: " + response);
                return;
            }
            int manureCount = jo.optInt("manureCount", 0);
            boolean canCollect = jo.optBoolean("canCollect", false);
            boolean needManualReceive = jo.optBoolean("needManualReceive", false);
            if (orchardManualVisitAward.getValue() && canCollect && needManualReceive) {
                AntOrchardVisitTask.manualAward(jo, orchardVisitDailyBudget.getValue());
                return;
            }
            if (manureCount > 0) {
                Log.farm("回访奖励🎖️领取肥料*" + manureCount);
            } else if (canCollect || needManualReceive) {
                Log.i("回访奖励🎖️有待领奖励[canCollect=" + canCollect + "#需手动领取=" + needManualReceive + "]");
            }
            // 无奖励时不打日志：官方每次进农场都会调一次，属正常空返回
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "receiveOrchardVisitAward err:", t);
        }
    }

    /**
     * 限时挑战活动
     */
    private void limitedTimeChallenge() {
        try {
            // 使用无参版本，因为你的RPC方法不支持参数
            String response = AntOrchardRpcCall.orchardSyncIndex();
            JSONObject root = MyUtils.newJSONObject(response);

            if (!MessageUtil.checkResultCode(TAG, root)) {
                Log.record("orchardSyncIndex 查询失败: " + response);
                return;
            }

            JSONObject challenge = root.optJSONObject("limitedTimeChallenge");
            if (challenge == null) {
                Log.record("limitedTimeChallenge 字段不存在或为 null");
                return;
            }

            int currentRound = challenge.optInt("currentRound", 0);
            if (currentRound <= 0) {
                Log.record("currentRound 无效：" + currentRound);
                return;
            }

            JSONArray taskArray = challenge.optJSONArray("limitedTimeChallengeTasks");
            if (taskArray == null) {
                Log.record("limitedTimeChallengeTasks 字段不存在或不是数组");
                return;
            }

            int targetIdx = currentRound - 1;
            if (targetIdx < 0 || targetIdx >= taskArray.length()) {
                Log.record("当前轮数 " + currentRound + " 对应下标 " + targetIdx + " 超出数组长度: " + taskArray.length());
                return;
            }

            JSONObject roundTask = taskArray.optJSONObject(targetIdx);
            if (roundTask == null) {
                Log.record("第 " + currentRound + " 轮任务不存在");
                return;
            }

            boolean ongoing = roundTask.optBoolean("ongoing", false);
            String MtaskStatus = roundTask.optString("taskStatus");
            String MtaskId = roundTask.optString("taskId");
            int MawardCount = roundTask.optInt("awardCount", 0);

            if ("FINISHED".equals(MtaskStatus) && ongoing) {
                Log.record("第 " + currentRound + " 轮 奖励未领取，尝试领取");
                String awardResp = AntOrchardRpcCall.receiveTaskAward("ORCHARD_LIMITED_TIME_CHALLENGE", MtaskId);
                JSONObject joo = MyUtils.newJSONObject(awardResp);
                if (MessageUtil.checkResultCode(TAG, joo)) {
                    Log.farm("第 " + currentRound + " 轮 限时任务🎁[肥料 * " + MawardCount + "]");
                } else {
                    String desc = joo.optString("desc", "未知错误");
                    Log.record("芭芭农场 限时任务 错误：" + desc);
                }
                return;
            }

            if (!"TODO".equals(roundTask.optString("taskStatus"))) {
                Log.record("警告：第 " + currentRound + " 轮任务非 TODO，状态=" + roundTask.optString("taskStatus"));
                return;
            }

            JSONArray childTasks = roundTask.optJSONArray("childTaskList");
            if (childTasks == null) {
                Log.record("警告：第 " + currentRound + " 轮无子任务列表");
                return;
            }

            Log.record("开始处理第 " + currentRound + " 轮的 " + childTasks.length() + " 个子任务");

            for (int i = 0; i < childTasks.length(); i++) {
                JSONObject child = childTasks.optJSONObject(i);
                if (child == null || !"TODO".equals(child.optString("taskStatus"))) {
                    continue;
                }

                String childTaskId = child.optString("taskId", "未知ID");
                String actionType = child.optString("actionType");
                String groupId = child.optString("groupId");
                String sceneCode = child.optString("sceneCode");

                if ("GROUP_1_STEP_3_GAME_WZZT_30s".equals(groupId)) {
                    continue;
                }

                Log.record("------ 开始处理子任务 " + i + " | ID=" + childTaskId + " ------");

                switch (actionType) {
                    case "SPREAD_MANURE":
                        int taskRequire = child.optInt("taskRequire", 0);
                        int taskProgress = child.optInt("taskProgress", 0);
                        int need = taskRequire - taskProgress;
                        if (need > 0) {
                            Log.record("施肥任务需补充 " + need + " 次");
                            for (int j = 0; j < need; j++) {
                                // 修复：传递正确的wua参数
                                String wua = getWua();
                                String spreadResultStr = AntOrchardRpcCall.orchardSpreadManure("main", false, wua);
                                Log.record("施肥第 " + (j + 1) + " 次结果：" + spreadResultStr);
                                JSONObject resultJson = MyUtils.newJSONObject(spreadResultStr);
                                if (!MessageUtil.checkResultCode(TAG, resultJson)) {
                                    Log.record("芭芭农场 orchardSpreadManure 错误：" + resultJson.optString("resultDesc"));
                                    return;
                                }
                            }
                            Log.farm("施肥任务成功完成 " + need + " 次");
                        }
                        break;

                    case "GAME_CENTER":
                        String r = AntOrchardRpcCall.noticeGame("2021004165643274");
                        JSONObject jr = MyUtils.newJSONObject(r);
                        if (MessageUtil.checkResultCode(TAG, jr)) {
                            Log.farm("游戏任务触发成功 → 子任务应当自动完成");
                        } else {
                            Log.farm("游戏任务触发失败，返回: " + r);
                        }
                        break;

                    case "VISIT":
                        // 广告任务处理（简化为直接完成）
                        JSONObject displayCfg = child.optJSONObject("taskDisplayConfig");
                        if (displayCfg == null || displayCfg.optString("targetUrl", "").isEmpty()) {
                            Log.record("任务没有 taskDisplayConfig，无法继续");
                            continue;
                        }

                        // 对于VISIT类型的任务，尝试直接调用finishTask
                        // 注意：这里childTaskId作为taskType参数传递
                        String finishResult = AntOrchardRpcCall.finishTask(sceneCode, childTaskId);
                        JSONObject finishJo = MyUtils.newJSONObject(finishResult);
                        if (MessageUtil.checkResultCode(TAG, finishJo)) {
                            Log.farm("广告任务触发成功");
                        } else {
                            Log.farm("广告任务触发失败: " + finishResult);
                        }
                        break;

                    default:
                        Log.record("无法处理的任务类型：" + childTaskId + " | actionType=" + actionType);
                        break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "limitedTimeChallenge err:", t);
        }
    }

    // 内部枚举定义
    public enum PlantScene {
        main("主场景"), yeb("余额宝场景");

        private final String nickname;

        PlantScene(String nickname) {
            this.nickname = nickname;
        }

        public String nickname() {
            return nickname;
        }

        public static PlantScene[] getEntries() {
            return values();
        }

        // 用于获取选项列表的静态方法
        public static List<String> getList() {
            List<String> list = new ArrayList<>();
            for (PlantScene scene : values()) {
                list.add(scene.name());
            }
            return list;
        }
    }

    public interface DriveAnimalType {
        int NONE = 0;
        int ALL = 1;
        String[] nickNames = {"不操作", "驱赶所有"};
    }

    public enum TaskStatus {
        TODO, FINISHED, RECEIVED
    }
}
