package io.github.aw1y2z.sesame.model.task.fish;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.entity.IdAndName;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntFishpondTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 福气鱼塘任务。对齐 Sure-Xu model/task/fish/FishTask.java（Sure-Xu 已从 GR 原版修过若干缺陷，
 * 见 doc/MyFix.md 的移植记录），机械翻译包名与 M 的日志/状态工具类调用，未改动业务逻辑本身——
 * 抓包字段、任务类型判断、等待时长都照抄，没有 M 自己的真机验证依据去改。
 */
public class FishTask extends ModelTask {
    private static final String API_BATCH_INVITE = "com.alipay.antiep.batchInviteP2P";
    private static final String API_EXCHANGE_REWARD = "com.alipay.antfishpond.fishpondExchangeReward";
    private static final String API_FINISH_TASK = "com.alipay.antiep.finishTask";
    private static final String API_FISHPOND_AD_NOTICE = "com.alipay.antfishpond.fishpondAdNotice";
    private static final String API_FISHPOND_ANGLE = "com.alipay.antfishpond.fishpondAngle";
    private static final String API_FISHPOND_SYNC_INDEX = "com.alipay.antfishpond.fishpondSyncIndex";
    private static final String API_FISHPOND_INDEX = "com.alipay.antfishpond.fishpondIndex";
    private static final String API_LIST_TASK = "com.alipay.antfishpond.listTask";
    private static final String API_QUERY_SUBPLOTS = "com.alipay.antfishpond.querySubplotsActivity";
    private static final String API_RECEIVE_AWARD = "com.alipay.antiep.receiveTaskAward";
    private static final String API_REFINED_OPERATION = "com.alipay.antfishpond.refinedOperation";
    private static final String API_ROD_POSITIONING = "com.alipay.antfishpond.fishpondAngleRodPositioning";
    private static final String API_SIGN = "com.alipay.antfishpond.sign";
    private static final String API_TRIGGER_SUBPLOTS = "com.alipay.antfishpond.triggerSubplotsActivity";
    private static final String SCENE_AD_RESULT = "ANTFISHPOND_ACTIVITY_RESULT_AD";
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
    };
    private static final int DELAY_BABA_FARM_BASE = 2000;
    private static final int DELAY_BABA_FARM_FLOAT = 1000;
    private static final int DELAY_FLOAT = 3000;
    private static final int DELAY_LOOK_BASE = 15000;
    private static final int DELAY_MEDIUM = 1000;
    private static final int DELAY_SHORT = 500;
    private static final int REEL_IN_BASE = 2800;
    private static final int REEL_IN_FLOAT = 600;
    private static final String REQUEST_TYPE_NORMAL = "NORMAL";
    private static final String REQUEST_TYPE_RPC = "RPC";
    private static final String SCENE_FISH_TASK = "ANTFISHPOND_TASK";
    private static final String SCENE_GAME_CENTER = "GameCenter";
    private static final String SOURCE_AD_BASIC_LIB = "ADBASICLIB";
    private static final String SOURCE_FARM_POOL = "farmpool";
    private static final String SOURCE_RECENTLY_USED = "ch_appcollect__chsub_my-recentlyUsed";
    private static final String TASK_STATUS_FINISHED = "FINISHED";
    private static final String TASK_STATUS_RECEIVED = "RECEIVED";
    private static final String TASK_STATUS_TODO = "TODO";
    private static final String TASK_TYPE_GOFISH = "GOFISH";
    private static final String TASK_TYPE_OFFLINE_SHARE = "OFFLINE_SHARE";
    private static final Map<String, Integer> TASK_WAIT_TIME;
    private static final String VERSION = "20260211.01";
    private static final Set<String> autoTaskIds;
    private static final Map<String, String> displayNameCache;
    private static volatile int fishTaskCount;
    private static volatile String fishTaskData;
    private static volatile double lastFishWeight;
    private static volatile int lastRodCount;
    private static final Set<String> processedTasks;
    private static final Set<String> failedTasks;
    private boolean todayRewardEnd = false;
    private boolean firstQueryDone = false;
    private boolean tomorrowRodTriggered = false;
    private boolean queryingStatus;
    private String runningUid;

    static {
        autoTaskIds = new CopyOnWriteArraySet<>();
        displayNameCache = new ConcurrentHashMap<>();
        TASK_WAIT_TIME = new ConcurrentHashMap<>();
        fishTaskCount = 0;
        fishTaskData = "";
        lastRodCount = -1;
        lastFishWeight = 0.0d;
        processedTasks = new HashSet<>();
        failedTasks = new HashSet<>();
        Collections.addAll(autoTaskIds, "GYG_XLIGHT_JX_BUSINEES_3", "NORMAL_TAOBAO_1_ROD", "FISH_TASK_14", "ANTFISHPOND_WECHAT_SHARE", "FISHPOND_NCLY_GAME_WPJZ_30S", "FISHPOND_NCLY_GAME_NCDDP_PLAY1", "FISHPOND_NCLY_GAME_XDDQ_30S", "FISHPOND_NCLY_GAME_MSQYJ_PLAY", "EXC_MANURE_4_ROD", "FISH_ACTIVITY_RESULT_AD");
        TASK_WAIT_TIME.put("GYG_XLIGHT_JX_BUSINEES_3", DELAY_LOOK_BASE);
        TASK_WAIT_TIME.put("GYG_XLIGHT_JX_BUSINEES", DELAY_LOOK_BASE);
        TASK_WAIT_TIME.put("NORMAL_TAOBAO_1_ROD", DELAY_LOOK_BASE);
        TASK_WAIT_TIME.put("FISH_ACTIVITY_RESULT_AD", DELAY_LOOK_BASE);
        AntFishpondTaskListMap.load();
        for (Map.Entry<String, String> entry : AntFishpondTaskListMap.getMap().entrySet()) {
            autoTaskIds.add(entry.getKey());
            displayNameCache.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public String getName() {
        return "鱼塘";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        FishConfig.registerFields(modelFields);
        return modelFields;
    }

    @Override
    public Boolean check() {
        return !TaskCommon.IS_ENERGY_TIME && isAllowedTime()
                && (FishConfig.isEnableFishAuto() || FishConfig.isEnableFishTaskAuto());
    }

    @Override
    public void run() {
        String taskUid = UserIdMap.getCurrentUid();
        if (taskUid == null || taskUid.isEmpty() || !check()) return;
        runningUid = taskUid;
        if (!isAllowedTime()) {
            Log.other("鱼塘⏰不在运行时段（05:00-23:00）");
            return;
        }

        long now = System.currentTimeMillis();
        long fishInterval = FishConfig.getFishCheckInterval();
        long lastExecTime = Status.INSTANCE.getFishLastExecTime();
        if (lastExecTime > 0 && now - lastExecTime < fishInterval) {
            long remainingMinutes = (fishInterval - (now - lastExecTime)) / 60_000;
            Log.other("鱼塘⏰独立间隔未到，还需等待约" + remainingMinutes + "分钟");
            return;
        }

        String validToken = getValidToken();
        processedTasks.clear();
        failedTasks.clear();
        this.todayRewardEnd = false;
        this.firstQueryDone = false;
        this.tomorrowRodTriggered = false;
        lastRodCount = -1;
        lastFishWeight = 0;
        try {
            if (Status.hasFlagToday("fish_exchange_fail_" + getToday())) {
                Log.other("鱼塘⚠️今日兑换失败，已暂停所有任务");
                return;
            }

            enterFishpond();

            if (FishConfig.isEnableFishTaskAuto()) {
                if (!checkExchangeReward()) {
                    return;
                }
                Log.other("鱼塘🎣开始做任务领钓竿");
                triggerSubplotsActivity(true);
                executeTasks();
            }

            if (FishConfig.isEnableFishAuto()) {
                if (!checkExchangeReward()) {
                    Log.other("鱼塘⚠️钓竿兑换失败，跳过钓鱼");
                    return;
                }

                startFishing(validToken);
            }
        } catch (Throwable th) {
            Log.printStackTrace("鱼塘🪝执行异常", th);
        } finally {
            if (taskUid.equals(UserIdMap.getCurrentUid())) {
                Status.INSTANCE.setFishLastExecTime(System.currentTimeMillis());
                Status.save();
            }
        }
    }

    private String requestString(String method, String args) {
        if (Thread.currentThread().isInterrupted()
                || (runningUid != null && !runningUid.equals(UserIdMap.getCurrentUid()))) {
            throw new CancellationException("Fish task stopped or account changed");
        }
        return ApplicationHook.requestString(method, args);
    }

    private String getValidToken() {
        return FishConfig.getFishpondToken();
    }

    public static void onTaskDiscovered(String str) {
        if (str == null || str.isEmpty()) {
            return;
        }
        if (autoTaskIds.add(str)) {
            Log.other("鱼塘✅发现新任务[" + str + "]");
        }
        AntFishpondTaskListMap.add(str, str);
        AntFishpondTaskListMap.save();
    }

    public static Set<String> getAllDiscoveredTasks() {
        return autoTaskIds;
    }

    public static class FishTaskItem extends IdAndName {
        public FishTaskItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static List<FishTaskItem> getTaskList() {
        final List<FishTaskItem> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : AntFishpondTaskListMap.getMap().entrySet()) {
            list.add(new FishTaskItem(entry.getKey(), entry.getValue()));
        }
        return list;
    }

    private static String getTaskDisplayName(String str) {
        String result = displayNameCache.get(str);
        if (result == null) {
            String mapped = AntFishpondTaskListMap.get(str);
            result = mapped != null ? mapped : str;
            displayNameCache.put(str, result);
        }
        return result;
    }

    private String getToday() {
        return DATE_FORMAT.get().format(new Date());
    }

    private boolean isSuccess(Object obj) {
        try {
            JSONObject jSONObject = obj instanceof String ? MyUtils.newJSONObject((String) obj) : (JSONObject) obj;
            if (jSONObject == null) {
                return false;
            }
            if (jSONObject.optInt("error", 0) != 0) return false;
            if (jSONObject.has("success")) return jSONObject.optBoolean("success", false);
            if (!jSONObject.optBoolean("success", false)) {
                if (jSONObject.optInt("resultCode", -1) != 100) {
                    return false;
                }
            }
            return true;
        } catch (Exception unused) {
            return false;
        }
    }

    private void sleep(long ms) {
        try {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            Thread.sleep(ms);
        } catch (InterruptedException unused) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Fish task interrupted");
        }
    }

    private String buildBaseRequest(String str) {
        return String.format("[{\"appMode\":\"normal\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]", REQUEST_TYPE_NORMAL, str, SOURCE_RECENTLY_USED, VERSION);
    }

    private String buildRequestWithSyncType(String str) {
        return String.format("[{\"appMode\":\"normal\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\",\"syncTypeList\":[\"FISH_ACTIVITY\",\"TASK_DISPLAY\",\"TOMORROW_ROD\",\"LOTTERY_PLUS\"]}]", REQUEST_TYPE_NORMAL, str, SOURCE_RECENTLY_USED, VERSION);
    }

    /**
     * 构建 finishTask 请求。基于真实客户端抓包数据；注意真实请求没有 version 字段。
     */
    private String buildFinishTaskRequest(String sceneCode, String taskId, String adBizNo) throws org.json.JSONException {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null) throw new CancellationException("No current account");
        String outBizNo = taskId + "_" + System.currentTimeMillis() + "_" + uid.substring(Math.max(0, uid.length() - 8));
        JSONObject request = new JSONObject().put("outBizNo", outBizNo)
                .put("requestType", REQUEST_TYPE_RPC).put("sceneCode", sceneCode)
                .put("source", SOURCE_AD_BASIC_LIB).put("taskType", taskId);
        if (adBizNo != null && !adBizNo.isEmpty()) {
            request.put("finishBusinessInfo", new JSONObject().put("pwPreBizId", adBizNo));
        }
        return new JSONArray().put(request).toString();
    }

    private String buildReceiveAwardRequest(String str, String str2) {
        return String.format("[{\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\",\"taskType\":\"%s\",\"ignoreLimit\":false}]", REQUEST_TYPE_NORMAL, str, SOURCE_RECENTLY_USED, VERSION, str2);
    }

    private void executeTasks() {
        doSign();
        sleep(ThreadLocalRandom.current().nextInt(1000) + 1000);

        Log.record("鱼塘🎣开始执行任务");

        checkAndReceiveGiftBox();
        sleep(DELAY_BABA_FARM_BASE + ThreadLocalRandom.current().nextInt(DELAY_BABA_FARM_FLOAT));

        listTask();

        Log.record("鱼塘✅任务执行完成");
        sleep(ThreadLocalRandom.current().nextInt(1000) + 2000);
    }

    /**
     * 是否在允许的运行时段内：05:00-23:00，按北京时间判断。原版用 TimeUtil.getNow()（系统默认
     * 时区），跟本次会话里其它几处 GMT+8 bug 根因相同，这里一并改成 MyUtils.getInstance()，见 doc/MyFix.md。
     */
    private boolean isAllowedTime() {
        Calendar calendar = MyUtils.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return hour >= 5 && hour < 23;
    }

    private void doSign() {
        String flag = "fish_sign_today_" + getToday();
        if (Status.hasFlagToday(flag)) {
            return;
        }
        try {
            String response = requestString(API_SIGN, String.format("[{\"appMode\":\"normal\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"signKey\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]", REQUEST_TYPE_NORMAL, SCENE_GAME_CENTER, getToday(), SOURCE_RECENTLY_USED, VERSION));
            if (response == null) {
                return;
            }
            JSONObject jo = MyUtils.newJSONObject(response);
            if (isSuccess(jo)) {
                JSONObject signTaskInfo = jo.optJSONObject("signTaskInfo");
                if (signTaskInfo != null && signTaskInfo.optBoolean("signed", false)) {
                    Log.other("鱼塘✅签到成功");
                    Status.flagToday(flag);
                }
            } else {
                String resultCode = jo.optString("resultCode");
                if ("SIGNED".equals(resultCode) || "ALREADY_SIGN".equals(resultCode)) {
                    Status.flagToday(flag);
                } else if (!"C20".equals(resultCode)) {
                    Log.other("鱼塘❌签到失败[" + resultCode + "]");
                }
            }
        } catch (Throwable unused) {
        }
    }

    private void listTask() {
        try {
            String response = requestString(API_LIST_TASK, buildBaseRequest(SCENE_GAME_CENTER));
            JSONArray taskList = response == null ? null : MyUtils.newJSONObject(response).optJSONArray("taskList");
            if (taskList == null) {
                return;
            }
            int changed = 0;
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                changed += saveTaskIfNeeded(task);

                String taskStatus = task.optString("taskStatus", "");
                String taskId = task.optString("taskId", "");
                String actionType = task.optString("actionType", "");
                String displayName = getTaskDisplayName(taskId);

                Set<String> fishTaskBlacklist = FishConfig.getFishTaskBlacklist();
                if (fishTaskBlacklist != null && fishTaskBlacklist.contains(taskId)) {
                    Log.record("鱼塘⚠️跳过黑名单任务[" + displayName + "]");
                    if (TASK_STATUS_FINISHED.equals(taskStatus)) {
                        receiveTaskAward(taskId, taskId);
                    }
                    continue;
                }

                if (TASK_STATUS_RECEIVED.equals(taskStatus)) {
                    continue;
                }

                if (TASK_TYPE_GOFISH.equals(actionType)) {
                    if (TASK_STATUS_TODO.equals(taskStatus)) {
                        continue;
                    }
                }

                if (TASK_STATUS_TODO.equals(taskStatus)) {
                    if (!doTaskActionAndFinish(task)) {
                        continue;
                    }
                }

                if (TASK_STATUS_FINISHED.equals(taskStatus)) {
                    Log.other("鱼塘🎁领取奖励[" + getTaskDisplayName(taskId) + "]");
                    if (!receiveTaskAward(taskId, taskId)) {
                        Log.other("鱼塘❌领取失败[" + getTaskDisplayName(taskId) + "]");
                        addToBlacklist(taskId);
                    } else {
                        Log.other("鱼塘✅领取成功[" + getTaskDisplayName(taskId) + "]");
                    }
                }

                int taskInterval = DELAY_BABA_FARM_BASE + ThreadLocalRandom.current().nextInt(DELAY_BABA_FARM_FLOAT);
                Log.record("鱼塘⏳等待" + taskInterval + "ms");
                sleep(taskInterval);
            }
            if (changed > 0) {
                AntFishpondTaskListMap.save();
            }
        } catch (Throwable th) {
            Log.other("鱼塘❌获取任务列表失败[" + th.getMessage() + "]");
        }
    }

    /** 执行任务并标记完成，借鉴庄园 doFarmTask 的结构。 */
    private boolean doTaskActionAndFinish(JSONObject taskJson) {
        try {
            String taskId = taskJson.optString("taskId", "");
            String actionType = taskJson.optString("actionType", "");
            String displayName = getTaskDisplayName(taskId);

            Log.other("鱼塘🚀开始任务[" + displayName + "]");

            boolean actionSuccess = doTaskAction(taskId, actionType);

            if (!actionSuccess) {
                failedTasks.add(taskId);
                addToBlacklist(taskId);
                return false;
            }

            if (isSpecialHandledTask(taskId)) {
                Log.other("鱼塘✅完成任务[" + displayName + "]");
                return true;
            }

            if (!isBrowseAdTask(taskId)) {
                Integer waitTime = TASK_WAIT_TIME.get(taskId);
                if (waitTime != null) {
                    Log.other("鱼塘⏳等待" + waitTime + "ms");
                    sleep(waitTime.longValue());
                    sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));
                } else {
                    sleep(DELAY_MEDIUM);
                }

                finishTaskById(taskId);
                sleep(DELAY_SHORT);
            }

            if (!receiveTaskAward(taskId, taskId)) {
                Log.other("鱼塘❌领取失败[" + displayName + "]");
                addToBlacklist(taskId);
                return false;
            }

            Log.other("鱼塘✅完成任务[" + displayName + "]");
            return true;

        } catch (Exception e) {
            Log.record("鱼塘❌执行任务异常[" + e.getMessage() + "]");
            return false;
        }
    }

    /** 内部已处理 finishTask 的特殊任务类型（handleBrowseAdTask/handleGameTask 内部已调用）。 */
    private boolean isSpecialHandledTask(String taskId) {
        return "NORMAL_TAOBAO_1_ROD".equals(taskId) ||
                "GYG_XLIGHT_JX_BUSINEES_3".equals(taskId) ||
                "GYG_XLIGHT_JX_BUSINEES".equals(taskId) ||
                "FISH_ACTIVITY_RESULT_AD".equals(taskId) ||
                containsWaitTime(taskId) ||
                isBrowseAdTask(taskId) ||
                isExchangeTask(taskId);
    }

    private int saveTaskIfNeeded(JSONObject task) {
        String taskId = task.optString("taskId", "");
        if (taskId.isEmpty()) {
            return 0;
        }
        JSONObject taskDisplayConfig = task.optJSONObject("taskDisplayConfig");
        AntFishpondTaskListMap.add(taskId, taskDisplayConfig != null ? taskDisplayConfig.optString("title", taskId) : taskId);
        return 1;
    }

    private boolean doTaskAction(String taskId, String actionType) {
        try {
            if ("FISH_ACTIVITY_RESULT_AD".equals(taskId)) {
                Log.other("鱼塘📺浏览[钓鱼活动广告]");
                handleFishActivityResultAd(taskId);
                return true;
            } else if ("NORMAL_TAOBAO_1_ROD".equals(taskId) ||
                    "GYG_XLIGHT_JX_BUSINEES_3".equals(taskId) ||
                    "GYG_XLIGHT_JX_BUSINEES".equals(taskId)) {
                Log.other("鱼塘📺浏览[" + getTaskDisplayName(taskId) + "]");
                return handleBrowseAdTask(taskId);
            } else if (taskId.startsWith("FISHPOND_NCLY_GAME")) {
                if (containsWaitTime(taskId)) {
                    Log.other("鱼塘🎮游戏[" + getTaskDisplayName(taskId) + "]");
                    return handleGameTask(taskId);
                } else {
                    Log.other("鱼塘🎮游戏[" + getTaskDisplayName(taskId) + "]#需实际游玩，加入黑名单");
                    addToBlacklist(taskId);
                    return false;
                }
            } else if (containsWaitTime(taskId)) {
                Log.other("鱼塘🎮游戏[" + getTaskDisplayName(taskId) + "]");
                return handleGameTask(taskId);
            } else if (isExchangeTask(taskId)) {
                Log.other("鱼塘💰兑换[" + getTaskDisplayName(taskId) + "]");
                return handleExchangeTask(taskId);
            } else if (isBrowseAdTask(taskId)) {
                Log.other("鱼塘📺浏览[" + getTaskDisplayName(taskId) + "]");
                return handleBrowseAdTask(taskId);
            }
            Log.other("鱼塘❌未知任务[" + getTaskDisplayName(taskId) + "]#无法处理，加入黑名单");
            addToBlacklist(taskId);
            return false;
        } catch (Exception e) {
            Log.record("鱼塘⚠️执行异常[" + e.getMessage() + "]");
            return false;
        }
    }

    private boolean containsWaitTime(String taskId) {
        String displayName = getTaskDisplayName(taskId);
        return displayName != null && displayName.matches(".*\\d+s.*");
    }

    private int extractWaitTime(String taskId) {
        String displayName = getTaskDisplayName(taskId);
        if (displayName == null) {
            return 30;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)s");
        java.util.regex.Matcher matcher = pattern.matcher(displayName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 30;
            }
        }
        return 30;
    }

    private void handleFishActivityResultAd(String taskId) {
        try {
            Log.other("鱼塘📺浏览[钓鱼活动广告]");

            String adBizNo = getAdBizNoForTask(taskId);
            if (adBizNo != null && !adBizNo.isEmpty()) {
                Log.other("鱼塘📡发送广告通知");
                notifyAdStart(adBizNo);
                sleep(DELAY_SHORT);
            }

            sleep(DELAY_LOOK_BASE);
            sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));

            finishTaskById(taskId, adBizNo);
            sleep(DELAY_SHORT);

            Log.other("鱼塘✅浏览完成[钓鱼活动广告]");
        } catch (Exception e) {
            Log.record("鱼塘❌处理广告失败[" + e.getMessage() + "]");
        }
    }

    private boolean isBrowseAdTask(String taskId) {
        String displayName = getTaskDisplayName(taskId);
        if (displayName == null) {
            return false;
        }
        return displayName.contains("看") ||
                displayName.contains("浏览") ||
                displayName.contains("广告") ||
                displayName.contains("商品") ||
                displayName.contains("视频");
    }

    private boolean isExchangeTask(String taskId) {
        String displayName = getTaskDisplayName(taskId);
        if (displayName == null) {
            return false;
        }
        return displayName.contains("消耗") ||
                displayName.contains("兑换") ||
                displayName.contains("肥料");
    }

    private String getTaskStatus(String taskId) {
        try {
            String result = requestString(API_LIST_TASK, buildBaseRequest(SCENE_GAME_CENTER));
            if (result == null) {
                return "";
            }
            JSONObject json = MyUtils.newJSONObject(result);
            JSONArray taskList = json.optJSONArray("taskList");
            if (taskList == null) {
                return "";
            }
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task != null && taskId.equals(task.optString("taskId", ""))) {
                    return task.optString("taskStatus", "");
                }
            }
        } catch (Exception e) {
            Log.record("鱼塘⚠️查询任务状态失败[" + e.getMessage() + "]");
        }
        return "";
    }

    private boolean handleExchangeTask(String taskId) {
        try {
            String displayName = getTaskDisplayName(taskId);
            Log.other("鱼塘💰兑换[" + displayName + "]");

            boolean finishSuccess = finishTaskByIdWithResult(taskId);
            if (!finishSuccess) {
                Log.other("鱼塘❌兑换失败[" + displayName + "]#finishTask未成功");
                return false;
            }
            sleep(DELAY_SHORT);

            Log.other("鱼塘⏳等待同步...");
            sleep(DELAY_MEDIUM);
            sleep(ThreadLocalRandom.current().nextInt(500));

            Log.other("鱼塘✅兑换完成[" + displayName + "]");
            return true;
        } catch (Exception e) {
            Log.record("鱼塘❌兑换失败[" + e.getMessage() + "]");
            return false;
        }
    }

    private boolean handleGameTask(String taskId) {
        try {
            String displayName = getTaskDisplayName(taskId);
            Log.other("鱼塘🎮游戏[" + displayName + "]");

            int waitSeconds = extractWaitTime(taskId);
            if (waitSeconds > 0) {
                Log.other("鱼塘⏳进入游戏等待" + waitSeconds + "s[" + displayName + "]");
                sleep(waitSeconds * 1000L);
                sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));
            }

            boolean finishSuccess = finishTaskByIdWithResult(taskId);
            sleep(DELAY_SHORT);

            if (!finishSuccess) {
                Log.other("鱼塘❌游戏失败[" + displayName + "]#finishTask未成功");
                return false;
            }

            Log.other("鱼塘⏳等待同步...");
            sleep(DELAY_MEDIUM);
            sleep(ThreadLocalRandom.current().nextInt(500));

            Log.other("鱼塘✅游戏完成[" + displayName + "]");
            return true;
        } catch (Exception e) {
            Log.record("鱼塘❌游戏失败[" + e.getMessage() + "]");
            return false;
        }
    }

    private boolean handleBrowseAdTask(String taskId) {
        try {
            String displayName = getTaskDisplayName(taskId);
            Log.other("鱼塘📺浏览[" + displayName + "]");

            int maxCount = extractMaxCount(displayName);
            Log.other("鱼塘📺浏览[" + displayName + "]#最多" + maxCount + "次");

            int maxLoops = Math.min(Math.max(maxCount, 1), 10);
            int loopCount = 0;

            while (loopCount < maxLoops) {
                loopCount++;

                String adBizNo = getAdBizNoForTask(taskId);
                if (adBizNo == null || adBizNo.isEmpty()) {
                    Log.other("鱼塘❌浏览失败[" + displayName + "]#无adBizNo");
                    addToBlacklist(taskId);
                    return false;
                }

                Log.other("鱼塘📡发送广告通知");
                notifyAdStart(adBizNo);
                sleep(DELAY_SHORT);

                Log.other("鱼塘⏳浏览15s...");
                sleep(DELAY_LOOK_BASE);
                sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));

                Log.other("鱼塘✅提交完成状态[" + displayName + "]#第" + loopCount + "次");
                boolean finishSuccess = finishTaskByIdWithResult(taskId, adBizNo);
                sleep(DELAY_SHORT);

                if (!finishSuccess) {
                    Log.other("鱼塘❌浏览失败[" + displayName + "]#第" + loopCount + "次finishTask未成功");
                    return false;
                }

                sleep(DELAY_MEDIUM);
                String taskStatus = getTaskStatus(taskId);

                if (TASK_STATUS_TODO.equals(taskStatus)) {
                    Log.other("鱼塘🔄浏览继续[" + displayName + "]#剩余次数");
                    sleep(DELAY_BABA_FARM_BASE + ThreadLocalRandom.current().nextInt(DELAY_BABA_FARM_FLOAT));
                    continue;
                } else if (TASK_STATUS_FINISHED.equals(taskStatus)) {
                    Log.other("鱼塘✅浏览完成[" + displayName + "]#全部" + loopCount + "次");
                    return true;
                } else {
                    Log.other("鱼塘✅浏览完成[" + displayName + "]#状态[" + taskStatus + "]");
                    return true;
                }
            }

            Log.other("鱼塘⚠️浏览任务[" + displayName + "]#达到最大循环次数" + maxLoops);
            return true;

        } catch (Exception e) {
            Log.record("鱼塘❌浏览失败[" + e.getMessage() + "]");
            return false;
        }
    }

    private int extractMaxCount(String displayName) {
        if (displayName == null) {
            return 1;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\(\\d+/(\\d+)\\)");
        java.util.regex.Matcher matcher = pattern.matcher(displayName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    private String getAdBizNoForTask(String taskId) {
        try {
            String result = requestString(API_LIST_TASK, buildBaseRequest(SCENE_GAME_CENTER));
            if (result == null) {
                return null;
            }

            JSONObject json = MyUtils.newJSONObject(result);
            JSONArray taskList = json.optJSONArray("taskList");
            if (taskList == null) {
                return null;
            }

            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String currentTaskId = task.optString("taskId", "");
                if (taskId.equals(currentTaskId)) {
                    return task.optString("adBizNo", "");
                }
            }
        } catch (Exception e) {
            Log.record("鱼塘⚠️获取adBizNo失败[" + e.getMessage() + "]");
        }
        return null;
    }

    private void notifyAdStart(String adBizNo) {
        try {
            String request = String.format(
                    "[{\"adBizNo\":\"%s\",\"appMode\":\"normal\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                    adBizNo, REQUEST_TYPE_NORMAL, SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
            );

            String result = requestString(API_FISHPOND_AD_NOTICE, request);
            if (result != null && isSuccess(result)) {
                Log.record("鱼塘✅广告通知成功");
            } else {
                Log.record("鱼塘⚠️广告通知失败");
            }
        } catch (Exception e) {
            Log.record("鱼塘⚠️广告通知异常[" + e.getMessage() + "]");
        }
    }

    private void finishTaskById(String taskId) {
        finishTaskById(taskId, null);
    }

    private void finishTaskById(String taskId, String adBizNo) {
        try {
            String sceneCode = getTaskSceneCode(taskId);

            String request = buildFinishTaskRequest(sceneCode, taskId, adBizNo);
            String result = requestString(API_FINISH_TASK, request);

            if (result != null && isSuccess(result)) {
                JSONObject jo = MyUtils.newJSONObject(result);
                JSONObject finishAwardResultVO = jo.optJSONObject("finishAwardResultVO");
                if (finishAwardResultVO != null) {
                    int deltaAwardCount = finishAwardResultVO.optInt("deltaAwardCount", 0);
                    Log.other("鱼塘✅完成[" + getTaskDisplayName(taskId) + "]" + (deltaAwardCount > 0 ? "#deltaAwardCount[" + deltaAwardCount + "]" : ""));
                } else {
                    Log.other("鱼塘✅完成[" + getTaskDisplayName(taskId) + "]");
                }
            } else {
                if (result != null) {
                    try {
                        JSONObject errorJson = MyUtils.newJSONObject(result);
                        String code = errorJson.optString("code", "");
                        String desc = errorJson.optString("desc", errorJson.optString("memo", ""));
                        Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#code[" + code + "]#desc[" + desc + "]");
                        if ("400000001".equals(code)) {
                            addToBlacklist(taskId);
                            Log.other("鱼塘⚠️任务配置不存在，已加入黑名单[" + getTaskDisplayName(taskId) + "]");
                        }
                    } catch (Exception parseEx) {
                        Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#结果[" + result + "]");
                    }
                } else {
                    Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#接口无返回");
                }
            }
        } catch (Exception e) {
            Log.record("鱼塘❌finishTask异常[" + e.getMessage() + "]");
        }
    }

    private boolean finishTaskByIdWithResult(String taskId) {
        return finishTaskByIdWithResult(taskId, null);
    }

    private boolean finishTaskByIdWithResult(String taskId, String adBizNo) {
        try {
            String sceneCode = getTaskSceneCode(taskId);
            String request = buildFinishTaskRequest(sceneCode, taskId, adBizNo);
            String result = requestString(API_FINISH_TASK, request);

            if (result != null && isSuccess(result)) {
                JSONObject jo = MyUtils.newJSONObject(result);
                JSONObject finishAwardResultVO = jo.optJSONObject("finishAwardResultVO");
                if (finishAwardResultVO != null) {
                    int deltaAwardCount = finishAwardResultVO.optInt("deltaAwardCount", 0);
                    Log.other("鱼塘✅完成[" + getTaskDisplayName(taskId) + "]" + (deltaAwardCount > 0 ? "#deltaAwardCount[" + deltaAwardCount + "]" : ""));
                } else {
                    Log.other("鱼塘✅完成[" + getTaskDisplayName(taskId) + "]");
                }
                return true;
            } else {
                if (result != null) {
                    try {
                        JSONObject errorJson = MyUtils.newJSONObject(result);
                        String code = errorJson.optString("code", "");
                        String desc = errorJson.optString("desc", errorJson.optString("memo", ""));
                        Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#code[" + code + "]#desc[" + desc + "]");
                        if ("400000001".equals(code)) {
                            addToBlacklist(taskId);
                            Log.other("鱼塘⚠️任务配置不存在，已加入黑名单[" + getTaskDisplayName(taskId) + "]");
                        }
                    } catch (Exception parseEx) {
                        Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#结果[" + result + "]");
                    }
                } else {
                    Log.record("鱼塘❌finishTask失败[" + getTaskDisplayName(taskId) + "]#接口无返回");
                }
                return false;
            }
        } catch (Exception e) {
            Log.record("鱼塘❌finishTask异常[" + e.getMessage() + "]");
            return false;
        }
    }

    private String getTaskSceneCode(String taskId) {
        if ("FISH_ACTIVITY_RESULT_AD".equals(taskId)) {
            return SCENE_AD_RESULT;
        }
        return SCENE_FISH_TASK;
    }

    private boolean receiveTaskAward(String taskId, String taskType) {
        try {
            String sceneCode = getTaskSceneCode(taskType);

            String response = requestString(API_RECEIVE_AWARD, buildReceiveAwardRequest(sceneCode, taskType));
            if (response == null) {
                Log.other("鱼塘❌领取失败[" + getTaskDisplayName(taskType) + "]#接口无返回");
                addToBlacklist(taskId);
                return false;
            }

            JSONObject jo = MyUtils.newJSONObject(response);
            String resultCode = jo.optString("resultCode");

            if ("TASK_NOT_FINISHED".equals(resultCode)) {
                Log.other("鱼塘❌领取失败[" + getTaskDisplayName(taskType) + "]#任务未完成");
                addToBlacklist(taskId);
                return false;
            }

            if (isSuccess(jo)) {
                JSONObject awardInfo = jo.optJSONObject("awardInfo");
                if (awardInfo != null) {
                    int rodCount = awardInfo.optInt("rodCount", 0);
                    Log.other("鱼塘🎁领取[" + getTaskDisplayName(taskType) + "]#获得[钓竿*" + rodCount + "]");
                } else {
                    Log.other("鱼塘🎁领取成功[" + getTaskDisplayName(taskType) + "]");
                }
                return true;
            } else {
                Log.other("鱼塘❌领取失败[" + getTaskDisplayName(taskType) + "][" + resultCode + "]");
                addToBlacklist(taskId);
                return false;
            }
        } catch (Throwable th) {
            Log.other("鱼塘❌领取异常[" + getTaskDisplayName(taskType) + "][" + th.getMessage() + "]");
            addToBlacklist(taskId);
            return false;
        }
    }

    private void addToBlacklist(String taskId) {
        if (!FishConfig.isAutoFishTaskBlacklist() || taskId == null || taskId.isEmpty()) return;
        Set<String> fishTaskBlacklist = FishConfig.getFishTaskBlacklist();
        if (fishTaskBlacklist != null && !fishTaskBlacklist.contains(taskId)) {
            FishConfig.addToFishTaskBlacklist(taskId);
            Log.other("鱼塘⚠️加入黑名单[" + getTaskDisplayName(taskId) + "]");
        }
    }

    private void triggerSubplotsActivity(boolean verbose) {
        try {
            if (verbose) {
                Log.other("鱼塘🎁处理活动任务");
            }

            String queryResult = requestString(API_QUERY_SUBPLOTS, buildBaseRequest(SCENE_GAME_CENTER));

            if (queryResult == null || !isSuccess(queryResult)) {
                Log.record("鱼塘⚠️查询活动失败");
                return;
            }

            JSONObject queryJson = MyUtils.newJSONObject(queryResult);
            JSONArray activitiesArray = queryJson.optJSONArray("subplotsActivityList");

            if (activitiesArray == null || activitiesArray.length() == 0) {
                Log.record("鱼塘📊无待处理活动");
                return;
            }

            Log.other("鱼塘📊检测到活动任务");

            for (int i = 0; i < activitiesArray.length(); i++) {
                JSONObject activity = activitiesArray.optJSONObject(i);
                if (activity == null) {
                    continue;
                }
                String activityId = activity.optString("activityId", "");
                String status = activity.optString("status", "");
                String activityType = activity.optString("activityType", "");

                if (activityType.isEmpty() || "DYNAMIC_LIST".equals(activityType)) {
                    continue;
                }

                if ("GIFT_BOX".equals(activityType)) {
                    if ("FINISHED".equals(status)) {
                        Log.other("鱼塘🎁每日宝箱已领取");
                    } else if ("TODO".equals(status)) {
                        Log.other("鱼塘🎁触发每日宝箱");
                        String triggerRequest = String.format(
                                "[{\"actionType\":\"receiveAward\",\"activityType\":\"GIFT_BOX\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                                SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
                        );
                        String triggerResult = requestString(API_TRIGGER_SUBPLOTS, triggerRequest);
                        if (triggerResult != null && isSuccess(triggerResult)) {
                            JSONObject triggerJson = MyUtils.newJSONObject(triggerResult);
                            JSONObject triggerActivity = triggerJson.optJSONObject("triggerSubplotsActivity");
                            if (triggerActivity != null) {
                                String extend = triggerActivity.optString("extend", "");
                                if (!extend.isEmpty()) {
                                    try {
                                        JSONObject extendJson = MyUtils.newJSONObject(extend);
                                        String awardCount = extendJson.optString("awardCount", "0");
                                        String awardType = extendJson.optString("awardType", "");
                                        Log.other("鱼塘🎁领取[每日宝箱]#获得[" + toAwardChineseName(awardType) + "*" + awardCount + "]");
                                    } catch (Exception e) {
                                        Log.other("鱼塘🎁领取[每日宝箱]#成功");
                                    }
                                } else {
                                    Log.other("鱼塘🎁领取[每日宝箱]#成功");
                                }
                            } else {
                                Log.other("鱼塘🎁触发[每日宝箱]#成功");
                            }
                        }
                    }
                    sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT) + DELAY_BABA_FARM_BASE);
                    continue;
                }

                if ("FISH_ACTIVITY".equals(activityType)) {
                    String extendStr = activity.optString("extend", "");
                    if ("FINISHED".equals(status)) {
                        try {
                            JSONObject extendJson = MyUtils.newJSONObject(extendStr);
                            String taskType = extendJson.optString("taskType", "");
                            String awardCount = extendJson.optString("awardCount", "0");
                            String awardType = extendJson.optString("awardType", "");
                            Log.other(String.format("鱼塘🎣钓鱼活动已完成[%s]#获得[%s*%s]", taskType, toAwardChineseName(awardType), awardCount));
                            String triggerRequest = String.format(
                                    "[{\"actionType\":\"receiveAward\",\"activityType\":\"FISH_ACTIVITY\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                                    SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION);
                            String triggerResult = requestString(API_TRIGGER_SUBPLOTS, triggerRequest);
                            if (triggerResult != null && isSuccess(triggerResult)) {
                                Log.other("鱼塘🎣领取[钓鱼活动奖励]#成功");
                                JSONObject triggerJson = MyUtils.newJSONObject(triggerResult);
                                JSONObject triggerActivity = triggerJson.optJSONObject("triggerSubplotsActivity");
                                if (triggerActivity != null) {
                                    String adExtend = triggerActivity.optString("extend", "");
                                    if (!adExtend.isEmpty()) {
                                        try {
                                            JSONObject adExtendJson = MyUtils.newJSONObject(adExtend);
                                            JSONObject adInfo = adExtendJson.optJSONObject("adInfo");
                                            if (adInfo != null) {
                                                String adBizNo = adInfo.optString("adBizNo", "");
                                                String adTaskType = adInfo.optString("taskType", "");
                                                Log.other("鱼塘📺钓鱼活动广告[" + adTaskType + "]#adBizNo[" + adBizNo + "]");
                                                if (!adBizNo.isEmpty()) {
                                                    notifyAdStart(adBizNo);
                                                    sleep(DELAY_SHORT);
                                                }
                                                sleep(DELAY_LOOK_BASE);
                                                sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));
                                                finishTaskById(adTaskType, adBizNo);
                                                sleep(DELAY_SHORT);
                                                Log.other("鱼塘✅钓鱼活动广告完成[" + adTaskType + "]");
                                            }
                                        } catch (Exception adEx) {
                                            Log.record("鱼塘⚠️解析广告信息失败[" + adEx.getMessage() + "]");
                                        }
                                    }
                                }
                            } else {
                                Log.other("鱼塘❌领取钓鱼活动奖励失败");
                            }
                        } catch (Exception e) {
                            Log.record("鱼塘⚠️解析钓鱼活动失败[" + e.getMessage() + "]");
                        }
                    } else if ("TODO".equals(status)) {
                        if (!extendStr.isEmpty()) {
                            try {
                                JSONObject extendJson = MyUtils.newJSONObject(extendStr);
                                String taskType = extendJson.optString("taskType", "");
                                int leftFishTimes = extendJson.optInt("leftFishTimes", -1);
                                Log.other(String.format("鱼塘🎣钓鱼活动[%s]#剩余%d次", taskType, leftFishTimes));
                            } catch (Exception e) {
                                Log.record("鱼塘⚠️解析活动失败[" + e.getMessage() + "]");
                            }
                        }
                    }
                    continue;
                }

                if ("TOMORROW_ROD".equals(activityType)) {
                    if ("TODAY_FINISH".equals(status) || "FINISHED".equals(status)) {
                        Log.other("鱼塘🌅明日钓竿已完成");
                    } else if ("TODO".equals(status) || "TODAY_TODO".equals(status)) {
                        Log.other("鱼塘🌅触发明日钓竿");
                        triggerTomorrowRodAward();
                        sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT) + DELAY_BABA_FARM_BASE);
                    }
                    continue;
                }

                if (activityId.isEmpty()) {
                    Log.record("鱼塘⚠️缺少activityId[" + activityType + "]");
                    continue;
                }

                if ("TODO".equals(status)) {
                    Log.other("鱼塘🚀触发活动[" + activityType + "]");

                    String triggerRequest = String.format(
                            "[{\"activityId\":\"%s\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                            activityId, SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
                    );
                    String triggerResult = requestString(API_TRIGGER_SUBPLOTS, triggerRequest);

                    if (triggerResult != null && isSuccess(triggerResult)) {
                        Log.other("鱼塘✅触发成功[" + activityType + "]");
                        sleep(DELAY_MEDIUM);

                        if (!"TOMORROW_ROD".equals(activityType)) {
                            receiveActivityAward(activityId);
                        }
                    }

                    sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT) + DELAY_BABA_FARM_BASE);
                }
            }

            Log.other("鱼塘✅活动处理完成");
        } catch (Exception e) {
            Log.record("鱼塘❌处理活动异常[" + e.getMessage() + "]");
        }
    }

    private void receiveActivityAward(String activityId) {
        try {
            String request = String.format(
                    "[{\"activityId\":\"%s\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                    activityId, SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
            );

            String result = requestString(API_RECEIVE_AWARD, request);

            if (result != null && isSuccess(result)) {
                JSONObject json = MyUtils.newJSONObject(result);
                JSONObject awardInfo = json.optJSONObject("awardInfo");
                if (awardInfo != null) {
                    int rodCount = awardInfo.optInt("rodCount", 0);
                    Log.other("鱼塘🎁领取[活动奖励]#获得[钓竿*" + rodCount + "]");
                } else {
                    Log.other("鱼塘🎁领取[活动奖励]#成功");
                }
            } else {
                Log.record("鱼塘⚠️领取活动奖励失败");
            }
        } catch (Exception e) {
            Log.record("鱼塘❌领取活动奖励异常[" + e.getMessage() + "]");
        }
    }

    /** 检查并领取 GIFT_BOX 每日宝箱奖励（独立于 triggerSubplotsActivity）。 */
    private void checkAndReceiveGiftBox() {
        try {
            Log.other("鱼塘🔍检查每日宝箱");

            String queryResult = requestString(API_QUERY_SUBPLOTS, buildBaseRequest(SCENE_GAME_CENTER));

            if (queryResult == null) {
                Log.record("鱼塘⚠️查询宝箱失败");
                return;
            }

            JSONObject queryJson = MyUtils.newJSONObject(queryResult);
            JSONArray activitiesArray = queryJson.optJSONArray("subplotsActivityList");

            if (activitiesArray == null || activitiesArray.length() == 0) {
                Log.record("鱼塘📊无活动任务");
                return;
            }

            boolean giftBoxFound = false;
            for (int i = 0; i < activitiesArray.length(); i++) {
                JSONObject activity = activitiesArray.optJSONObject(i);
                if (activity == null) {
                    continue;
                }
                String activityType = activity.optString("activityType", "");
                String status = activity.optString("status", "");

                if ("GIFT_BOX".equals(activityType)) {
                    giftBoxFound = true;
                    Log.other("鱼塘🎁检测到每日宝箱[" + toStatusChineseName(status) + "]");

                    if ("FINISHED".equals(status)) {
                        Log.other("鱼塘✅每日宝箱已领取");
                        return;
                    } else if ("TODO".equals(status)) {
                        Log.other("鱼塘🎁触发每日宝箱");
                        String triggerRequest = String.format(
                                "[{\"actionType\":\"receiveAward\",\"activityType\":\"GIFT_BOX\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                                SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
                        );
                        String triggerResult = requestString(API_TRIGGER_SUBPLOTS, triggerRequest);
                        if (triggerResult != null && isSuccess(triggerResult)) {
                            JSONObject triggerJson = MyUtils.newJSONObject(triggerResult);
                            JSONObject triggerActivity = triggerJson.optJSONObject("triggerSubplotsActivity");
                            if (triggerActivity != null) {
                                String extend = triggerActivity.optString("extend", "");
                                if (!extend.isEmpty()) {
                                    try {
                                        JSONObject extendJson = MyUtils.newJSONObject(extend);
                                        String awardCount = extendJson.optString("awardCount", "0");
                                        String awardType = extendJson.optString("awardType", "");
                                        Log.other("鱼塘🎁领取[每日宝箱]#获得[" + toAwardChineseName(awardType) + "*" + awardCount + "]");
                                    } catch (Exception e) {
                                        Log.other("鱼塘🎁领取[每日宝箱]#成功");
                                    }
                                } else {
                                    Log.other("鱼塘🎁领取[每日宝箱]#成功");
                                }
                            } else {
                                Log.other("鱼塘🎁触发[每日宝箱]#成功");
                            }
                        } else {
                            Log.other("鱼塘❌每日宝箱触发失败");
                        }
                        return;
                    }
                    break;
                }
            }

            if (!giftBoxFound) {
                Log.record("鱼塘📊未检测到每日宝箱");
            }

        } catch (Exception e) {
            Log.record("鱼塘❌检查宝箱异常[" + e.getMessage() + "]");
        }
    }

    /** 触发明日钓竿奖励领取。 */
    private void triggerTomorrowRodAward() {
        try {
            if (tomorrowRodTriggered) {
                Log.other("鱼塘🌅明日钓竿本次已触发，跳过");
                return;
            }
            tomorrowRodTriggered = true;

            String triggerRequest = String.format(
                    "[{\"actionType\":\"FINISH\",\"activityType\":\"TOMORROW_ROD\",\"requestType\":\"NORMAL\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                    SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
            );
            String triggerResult = requestString(API_TRIGGER_SUBPLOTS, triggerRequest);
            if (triggerResult != null && isSuccess(triggerResult)) {
                Log.other("鱼塘🌅领取[明日钓竿]#成功");
                sleep(DELAY_MEDIUM);
                String syncResult = requestString(API_FISHPOND_SYNC_INDEX, buildRequestWithSyncType(SCENE_GAME_CENTER));
                if (syncResult != null && isSuccess(syncResult)) {
                    JSONObject syncJson = MyUtils.newJSONObject(syncResult);
                    JSONObject tomorrowRod = syncJson.optJSONObject("tomorrowRod");
                    if (tomorrowRod != null) {
                        int tomorrowRodCount = tomorrowRod.optInt("tomorrowRodCount", 0);
                        String status = tomorrowRod.optString("status", "");
                        Log.other(String.format("鱼塘🌅明日钓竿[%s]#明日可领%d个钓竿", toStatusChineseName(status), tomorrowRodCount));
                    }
                }
            } else {
                Log.other("鱼塘❌明日钓竿触发失败");
            }
        } catch (Exception e) {
            Log.record("鱼塘❌触发明日钓竿异常[" + e.getMessage() + "]");
        }
    }

    /** 模拟进入鱼塘弹窗，与真实 APP 流程一致：refinedOperation(ENTER_FISHPOND_POP) → querySubplotsActivity → ... */
    private void enterFishpond() {
        try {
            String request = String.format(
                    "[{\"actionId\":\"ENTER_FISHPOND_POP\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]",
                    REQUEST_TYPE_NORMAL, SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION
            );
            requestString(API_REFINED_OPERATION, request);
        } catch (Exception e) {
            Log.record("鱼塘⚠️enterFishpond异常[" + e.getMessage() + "]");
        }
    }

    private boolean checkExchangeReward() {
        try {
            String baseRequest = buildBaseRequest(SCENE_GAME_CENTER);
            String response = requestString(API_FISHPOND_INDEX, baseRequest);
            JSONObject roundInfo = response == null ? null : MyUtils.newJSONObject(response).optJSONObject("roundInfo");
            if (roundInfo != null && roundInfo.optBoolean("canExchange", false)) {
                Log.other("鱼塘💰兑换奖励");
                String exchangeResponse = requestString(API_EXCHANGE_REWARD, baseRequest);
                if (exchangeResponse == null) {
                    Status.flagToday("fish_exchange_fail_" + getToday());
                    Log.other("鱼塘❌兑换失败#接口无返回");
                    return false;
                }
                JSONObject jo = MyUtils.newJSONObject(exchangeResponse);
                boolean success = isSuccess(jo);
                if (success) {
                    Log.other("鱼塘💰兑换成功");
                    queryFishStatus(true);
                    return true;
                }
                Status.flagToday("fish_exchange_fail_" + getToday());
                Log.other("鱼塘❌兑换失败");
                return false;
            }
            return true;
        } catch (Throwable th) {
            Status.flagToday("fish_exchange_fail_" + getToday());
            Log.other("鱼塘❌兑换异常");
            return false;
        }
    }

    private void queryFishStatus(boolean silent) {
        if (queryingStatus) return;
        queryingStatus = true;
        lastRodCount = -1;
        try {
            String response = requestString(API_FISHPOND_SYNC_INDEX, buildRequestWithSyncType(SCENE_GAME_CENTER));
            if (isSuccess(response)) {
                JSONObject jo = MyUtils.newJSONObject(response);
                lastRodCount = jo.optInt("rodSumCount", 0);
                JSONObject roundInfo = jo.optJSONObject("roundInfo");
                JSONObject fishAssetInfo = roundInfo == null ? null : roundInfo.optJSONObject("fishAssetInfo");
                if (fishAssetInfo != null) {
                    lastFishWeight = fishAssetInfo.optDouble("currentFishWeight", 0.0d);
                    if (!silent) {
                        Log.other(String.format("鱼塘📊钓竿%d#鱼获%.2fg/%sg#还需%sg", lastRodCount, lastFishWeight, fishAssetInfo.optString("targetFishWeight", "10000"), fishAssetInfo.optString("diffFishWeight", "0")));
                    }
                }

                JSONObject lastAdInfo = jo.optJSONObject("lastAdInfo");
                if (lastAdInfo != null) {
                    boolean complete = lastAdInfo.optBoolean("complete", false);
                    String adType = lastAdInfo.optString("adType", "");

                    if (!complete && "FISH_ACTIVITY_AD".equals(adType)) {
                        Log.other("鱼塘📺处理钓鱼活动广告");
                        handleLastAdTask(lastAdInfo);
                    }
                }

                JSONObject fishActivity = jo.optJSONObject("fishActivity");
                if (fishActivity != null) {
                    String status = fishActivity.optString("status", "");
                    int leftFishTimes = fishActivity.optInt("leftFishTimes", -1);

                    if ("TODO".equals(status) && leftFishTimes > 0) {
                        Log.other(String.format("鱼塘🎣钓鱼活动#剩余%d次", leftFishTimes));
                    } else if (leftFishTimes == 0) {
                        Log.other("鱼塘🎁领取钓鱼活动奖励");
                        triggerSubplotsActivity();
                        return;
                    }
                }

                JSONObject tomorrowRod = jo.optJSONObject("tomorrowRod");
                if (tomorrowRod != null && !silent) {
                    String rodStatus = tomorrowRod.optString("status", "");
                    int todayRodCount = tomorrowRod.optInt("todayRodCount", 0);
                    int tomorrowRodCount = tomorrowRod.optInt("tomorrowRodCount", 0);
                    int targetCount = tomorrowRod.optInt("targetCount", 0);
                    int remainCount = Math.max(0, targetCount - todayRodCount);

                    if ("TODAY_FINISH".equals(rodStatus)) {
                        Log.other(String.format("鱼塘🌅明日钓竿已完成#今日钓%d次#明日可领%d个钓竿", todayRodCount, tomorrowRodCount));
                        if (tomorrowRodCount > 0) {
                            Log.other("鱼塘🌅触发明日钓竿");
                            triggerTomorrowRodAward();
                        }
                    } else if ("TODAY_TODO".equals(rodStatus)) {
                        Log.other(String.format("鱼塘⏳明日钓竿待完成#还需%d次#明日可领%d个钓竿", remainCount, tomorrowRodCount));
                        if (todayRodCount >= targetCount && targetCount > 0) {
                            Log.other("鱼塘🌅触发领取明日钓竿");
                            triggerTomorrowRodAward();
                        }
                    }
                }
                if (this.todayRewardEnd) {
                    return;
                }
                this.todayRewardEnd = true;
                Log.other("鱼塘📊今日活动奖励已领完");
            }
        } catch (Throwable th) {
            if (!silent) {
                Log.other("鱼塘🪝查询状态失败[" + th.getMessage() + "]");
            }
        } finally {
            queryingStatus = false;
        }
    }

    private void handleLastAdTask(JSONObject lastAdInfo) {
        try {
            String taskId = lastAdInfo.optString("taskId", "");
            String sceneCode = lastAdInfo.optString("sceneCode", "");
            String adBizNo = lastAdInfo.optString("adBizNo", "");

            if (taskId.isEmpty() || sceneCode.isEmpty()) {
                Log.record("鱼塘⚠️lastAdInfo信息不完整");
                return;
            }

            Log.other("鱼塘📺处理钓鱼活动广告");
            sleep(DELAY_LOOK_BASE);
            sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));

            String outBizNo = "FISH_ACTIVITY_RESULT_AD_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(10000);
            String requestData = String.format(
                    "[{\"finishBusinessInfo\":{\"pwPreBizId\":\"%s\"}," +
                            "\"outBizNo\":\"%s\",\"requestType\":\"RPC\"," +
                            "\"sceneCode\":\"%s\",\"source\":\"ADBASICLIB\"," +
                            "\"taskType\":\"%s\"}]",
                    adBizNo, outBizNo, sceneCode, taskId
            );

            String result = requestString(API_FINISH_TASK, requestData);

            if (isSuccess(result)) {
                JSONObject jo = MyUtils.newJSONObject(result);
                JSONObject finishAwardResultVO = jo.optJSONObject("finishAwardResultVO");
                if (finishAwardResultVO != null) {
                    int deltaAwardCount = finishAwardResultVO.optInt("deltaAwardCount", 0);
                    Log.other("鱼塘📺钓鱼活动广告完成" + (deltaAwardCount > 0 ? "#deltaAwardCount[" + deltaAwardCount + "]" : ""));
                } else {
                    Log.other("鱼塘📺钓鱼活动广告完成");
                }
                sleep(DELAY_MEDIUM);
                queryFishStatus(true);
            } else {
                Log.other("鱼塘❌钓鱼活动广告失败");
            }

        } catch (Throwable th) {
            Log.record("鱼塘❌lastAdInfo异常[" + th.getMessage() + "]");
        }
    }

    private void triggerSubplotsActivity() {
        triggerSubplotsActivity(false);
    }

    private FishResult performRodPositioning(String bizNo, String areaType) {
        if (bizNo != null && !bizNo.isEmpty()) {
            try {
                int waitMs = ThreadLocalRandom.current().nextInt(REEL_IN_FLOAT) + REEL_IN_BASE;
                Log.other("鱼塘🎣等待" + waitMs + "ms收杆");
                sleep((long) waitMs);
                String response = requestString(API_ROD_POSITIONING, String.format("[{\"areaType\":\"%s\",\"bizNo\":\"%s\",\"requestType\":\"%s\",\"sceneCode\":\"%s\",\"source\":\"%s\",\"version\":\"%s\"}]", (areaType == null || areaType.isEmpty()) ? "SPECIAL_BIG_ZONE" : areaType, bizNo, REQUEST_TYPE_NORMAL, SCENE_GAME_CENTER, SOURCE_RECENTLY_USED, VERSION));
                if (!isSuccess(response)) {
                    Log.other("鱼塘❌收杆定位失败");
                    return null;
                }
                JSONObject jo = MyUtils.newJSONObject(response);
                JSONObject angleResultInfo = jo.optJSONObject("angleResultInfo");
                if (angleResultInfo == null) {
                    Log.other("鱼塘❌收杆数据异常");
                    return null;
                }
                String fishName = angleResultInfo.optString("fishName", "未知");
                String fishWeight = angleResultInfo.optString("fishWeight", "0");
                Log.other(String.format("鱼塘🐟收杆[%s]#%sg", fishName, fishWeight));
                lastRodCount = jo.optInt("rodSumCount", lastRodCount);
                return FishResult.success(lastRodCount, fishName, fishWeight, bizNo, angleResultInfo.optJSONObject("angleAdInfo"), true);
            } catch (Throwable th) {
                Log.other("鱼塘❌收杆异常[" + th.getMessage() + "]");
            }
        }
        return null;
    }

    private void startFishing(String token) {
        if (token == null || token.isEmpty() || "1".equals(token)) {
            Log.other("鱼塘🪝Token无效，跳过钓鱼");
            return;
        }
        Log.other("鱼塘🎣开始自动钓鱼");
        this.firstQueryDone = false;
        int consecutiveIneffective = 0;
        int fishedCount = 0;
        while (!Thread.currentThread().isInterrupted() && isAllowedTime()) {
            if (consecutiveIneffective >= 5 || fishedCount >= 50) {
                break;
            }
            if (!this.firstQueryDone) {
                queryFishStatus(false);
                this.firstQueryDone = true;
            } else {
                queryFishStatus(true);
            }
            tryClaimRodBeforeFish();
            sleep(ThreadLocalRandom.current().nextInt(1000) + 1000);
            if (lastRodCount <= 0) {
                Log.other("鱼塘🪝钓竿不足，检测可领取钓竿");
                tryClaimRodBeforeFish();
                if (lastRodCount <= 0) {
                    Log.other("鱼塘🪝钓竿不足，停止钓鱼");
                    break;
                }
                Log.other("鱼塘🪝领取到钓竿，继续钓鱼");
            }
            FishResult fishResult = performSingleFish(token);
            if (fishResult.isSuccess()) {
                consecutiveIneffective = 0;
                fishedCount++;
                handleFishSuccess(fishResult);
            } else if (fishResult.isTooSmall()) {
                consecutiveIneffective++;
                if (consecutiveIneffective >= 5) {
                    Log.other("鱼塘🪝连续5次无效，Token已清空");
                    FishConfig.setFishpondToken("");
                    break;
                } else {
                    sleep(ThreadLocalRandom.current().nextInt(1000) + 4000);
                    sleep(ThreadLocalRandom.current().nextInt(5000) + 5000);
                }
            } else if (fishResult.isTokenInvalid()) {
                Log.other("鱼塘🪝Token失效，已清空");
                FishConfig.setFishpondToken("");
                break;
            } else if (fishResult.isNeedPositioning()) {
                consecutiveIneffective = 0;
                fishedCount++;
                handleFishSuccess(fishResult);
            } else {
                consecutiveIneffective++;
                if (consecutiveIneffective >= 5) {
                    Log.other("鱼塘🪝连续5次失败，Token已清空");
                    FishConfig.setFishpondToken("");
                    break;
                } else {
                    sleep(ThreadLocalRandom.current().nextInt(1000) + 4000);
                    sleep(ThreadLocalRandom.current().nextInt(5000) + 5000);
                }
            }
            sleep(ThreadLocalRandom.current().nextInt(5000) + 5000);
        }
        Log.other("鱼塘✅钓鱼完成#共" + fishedCount + "次");
    }

    /** 钓鱼前检测并领取可用的钓竿（如明日钓竿）。 */
    private void tryClaimRodBeforeFish() {
        try {
            String result = requestString(API_QUERY_SUBPLOTS, buildBaseRequest(SCENE_GAME_CENTER));
            if (result != null && isSuccess(result)) {
                JSONObject json = MyUtils.newJSONObject(result);
                JSONArray activityList = json.optJSONArray("subplotsActivityList");
                if (activityList != null) {
                    for (int idx = 0; idx < activityList.length(); idx++) {
                        JSONObject activity = activityList.optJSONObject(idx);
                        if (activity == null) continue;
                        String activityType = activity.optString("activityType", "");
                        String status = activity.optString("status", "");
                        if ("TOMORROW_ROD".equals(activityType) && ("TODO".equals(status) || "TODAY_TODO".equals(status))) {
                            Log.other("鱼塘🌅钓鱼前领取明日钓竿");
                            triggerTomorrowRodAward();
                            String syncResult = requestString(API_FISHPOND_SYNC_INDEX, buildRequestWithSyncType(SCENE_GAME_CENTER));
                            if (syncResult != null && isSuccess(syncResult)) {
                                JSONObject syncJson = MyUtils.newJSONObject(syncResult);
                                lastRodCount = syncJson.optInt("rodSumCount", lastRodCount);
                            }
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 静默忽略，不影响主流程
        }
    }

    private FishResult performSingleFish(String token) {
        if (token == null || token.isEmpty() || "1".equals(token)) {
            return FishResult.tokenInvalid();
        }
        try {
            JSONObject riskToken;
            try {
                riskToken = MyUtils.newJSONObject(token);
            } catch (Exception invalidToken) {
                return FishResult.tokenInvalid();
            }
            if (riskToken.length() == 0) return FishResult.tokenInvalid();
            JSONObject args = new JSONObject().put("bizNo", "").put("requestType", "NORMAL")
                    .put("riskToken", riskToken).put("sceneCode", SCENE_GAME_CENTER)
                    .put("source", SOURCE_FARM_POOL).put("version", "20260211.01");
            String response = requestString(API_FISHPOND_ANGLE, new JSONArray().put(args).toString());
            if (!isSuccess(response)) {
                if ("C21".equals(MyUtils.newJSONObject(response).optString("resultCode"))) {
                    return FishResult.tokenInvalid();
                }
                return FishResult.fail();
            }
            JSONObject jo = MyUtils.newJSONObject(response);
            lastRodCount = jo.optInt("rodSumCount", 0);
            boolean needRodPositioning = jo.optBoolean("needRodPositioning", false);
            JSONObject angleResultInfo = jo.optJSONObject("angleResultInfo");
            if (!needRodPositioning || angleResultInfo == null) {
                if (angleResultInfo == null) {
                    return FishResult.fail();
                }
                if (((float) angleResultInfo.optDouble("fishWeight", 0.0d)) > 0.01f) {
                    return FishResult.success(lastRodCount, angleResultInfo.optString("fishName", "未知"), angleResultInfo.optString("fishWeight", "0"), angleResultInfo.optString("bizNo", ""), angleResultInfo.optJSONObject("angleAdInfo"), false);
                }
                return FishResult.tooSmall();
            }
            float fishWeight = (float) angleResultInfo.optDouble("fishWeight", 0.0d);
            String fishType = angleResultInfo.optString("fishType", "");
            if (fishWeight > 0.0f) {
                return FishResult.success(lastRodCount, angleResultInfo.optString("fishName", "未知"), angleResultInfo.optString("fishWeight", "0"), angleResultInfo.optString("bizNo", ""), angleResultInfo.optJSONObject("angleAdInfo"), false);
            }
            if (fishWeight == 0.0f && "WELFARE_FISH".equals(fishType)) {
                String bizNo = angleResultInfo.optString("bizNo", "");
                if (bizNo.isEmpty()) {
                    return FishResult.needPositioning(bizNo);
                }
                FishResult positioned = performRodPositioning(bizNo, angleResultInfo.optString("areaType", "SPECIAL_BIG_ZONE"));
                return (positioned == null || !positioned.isSuccess()) ? FishResult.needPositioning(bizNo) : positioned;
            }
            return FishResult.tooSmall();
        } catch (Throwable th) {
            Log.other("鱼塘🪝钓鱼异常[" + th.getClass().getSimpleName() + "]");
            return FishResult.fail();
        }
    }

    private void handleFishSuccess(FishResult fishResult) {
        if (fishResult.isNeedPositioning() && fishResult.getBizNo() != null && !fishResult.getBizNo().isEmpty()) {
            FishResult positioned = performRodPositioning(fishResult.getBizNo(), "SPECIAL_BIG_ZONE");
            if (positioned == null || !positioned.isSuccess()) {
                Log.other("鱼塘⚠️收杆失败，放弃");
                return;
            }
            fishResult = positioned;
        }
        if (fishResult.isSuccess()) {
            if (!fishResult.isConverted()) {
                Log.other(String.format("鱼塘🐟钓鱼成功[%s]#%sg", fishResult.getFishName(), fishResult.getFishWeight()));
            }
            String syncResponse = requestString(API_FISHPOND_SYNC_INDEX, buildRequestWithSyncType(SCENE_GAME_CENTER));
            if (isSuccess(syncResponse)) {
                try {
                    JSONObject jo = MyUtils.newJSONObject(syncResponse);
                    int rodCount = jo.optInt("rodSumCount", 0);
                    JSONObject roundInfo = jo.optJSONObject("roundInfo");
                    JSONObject fishAssetInfo = roundInfo == null ? null : roundInfo.optJSONObject("fishAssetInfo");
                    if (fishAssetInfo != null) {
                        Log.other(String.format("鱼塘📊钓竿%d#鱼获%.2fg/%sg#还需%sg", rodCount, fishAssetInfo.optDouble("currentFishWeight", 0.0d), fishAssetInfo.optString("targetFishWeight", "10000"), fishAssetInfo.optString("diffFishWeight", "0")));
                    }
                } catch (Exception unused) {
                }
            }
            if (!this.todayRewardEnd) {
                String syncResponse2 = requestString(API_FISHPOND_SYNC_INDEX, buildRequestWithSyncType(SCENE_GAME_CENTER));
                try {
                    if (isSuccess(syncResponse2)) {
                        JSONObject fishActivity = MyUtils.newJSONObject(syncResponse2).optJSONObject("fishActivity");
                        if (fishActivity != null) {
                            int leftFishTimes = fishActivity.optInt("leftFishTimes", -1);
                            if (leftFishTimes == 0 || leftFishTimes == -1) {
                                Log.other("鱼塘🎁领取钓鱼活动奖励");
                                triggerSubplotsActivity();
                            }
                        } else {
                            this.todayRewardEnd = true;
                            Log.other("鱼塘📊今日活动奖励已领完");
                        }
                    }
                } catch (Exception unused2) {
                }
            }
            if (fishResult.getAngleAdInfo() != null && !fishResult.getAngleAdInfo().optBoolean("complete", false)) {
                handleDoubleAdTask(fishResult.getAngleAdInfo());
            }
            if (fishTaskCount > 0) {
                fishTaskCount--;
                if (fishTaskCount == 0) {
                    sleep(ThreadLocalRandom.current().nextInt(1000) + 4000);
                    isSuccess(requestString(API_FINISH_TASK, fishTaskData));
                    sleep(1000L);
                    listTask();
                }
            }
        }
    }

    private void handleDoubleAdTask(JSONObject angleAdInfo) {
        try {
            if (angleAdInfo == null || angleAdInfo.optBoolean("complete", false)) {
                return;
            }

            String taskId = angleAdInfo.optString("taskId");
            String sceneCode = angleAdInfo.optString("sceneCode");
            String adBizNo = angleAdInfo.optString("adBizNo");

            if (taskId.isEmpty() || sceneCode.isEmpty()) {
                Log.record("鱼塘⚠️双倍广告信息不完整");
                return;
            }

            String taskKey = "double_ad_" + adBizNo;
            if (processedTasks.contains(taskKey)) {
                Log.record("鱼塘⚠️双倍广告已处理");
                return;
            }

            Log.other("鱼塘📺处理双倍奖励广告");

            String clickUrl = extractClickUrl(angleAdInfo);
            if (!clickUrl.isEmpty()) {
                Log.record("鱼塘🚀打开广告小程序");
                openMiniProgram(clickUrl);
                sleep(DELAY_LOOK_BASE);
                sleep(ThreadLocalRandom.current().nextInt(DELAY_FLOAT));
                Log.other("鱼塘✅广告浏览完成");
            } else {
                Log.record("鱼塘⚠️未找到广告链接");
                sleep(DELAY_MEDIUM);
            }

            String outBizNo = taskId + "_" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(10000);
            String requestData = String.format(
                    "[{\"finishBusinessInfo\":{\"pwPreBizId\":\"%s\"}," +
                            "\"outBizNo\":\"%s\",\"requestType\":\"RPC\"," +
                            "\"sceneCode\":\"%s\",\"source\":\"ADBASICLIB\"," +
                            "\"taskType\":\"%s\"}]",
                    adBizNo.isEmpty() ? "" : adBizNo, outBizNo, sceneCode, taskId
            );

            String result = requestString(API_FINISH_TASK, requestData);

            if (isSuccess(result)) {
                JSONObject jo = MyUtils.newJSONObject(result);
                JSONObject finishAwardResultVO = jo.optJSONObject("finishAwardResultVO");
                if (finishAwardResultVO != null) {
                    int deltaAwardCount = finishAwardResultVO.optInt("deltaAwardCount", 0);
                    boolean hasNextStage = finishAwardResultVO.optBoolean("hasNextStage", false);
                    Log.other("鱼塘📺双倍广告完成" + (deltaAwardCount > 0 ? "#deltaAwardCount[" + deltaAwardCount + "]" : "") + (hasNextStage ? "#还有下一阶段" : ""));
                } else {
                    Log.other("鱼塘📺双倍广告完成");
                }

                processedTasks.add(taskKey);
                sleep(DELAY_MEDIUM);
                queryFishStatus(true);
            } else {
                Log.other("鱼塘❌双倍广告失败");
            }

        } catch (Throwable th) {
            Log.record("鱼塘❌双倍广告异常[" + th.getMessage() + "]");
        }
    }

    private String extractClickUrl(JSONObject angleAdInfo) {
        try {
            String schemaJson = angleAdInfo.optString("schemaJson");
            if (!schemaJson.isEmpty()) {
                JSONObject schema = MyUtils.newJSONObject(schemaJson);
                String url = schema.optString("url");
                if (!url.isEmpty()) {
                    return url;
                }
            }

            String url = angleAdInfo.optString("clickThroughUrl");
            if (!url.isEmpty()) {
                return url;
            }

            url = angleAdInfo.optString("xlightDeepLinkUrl");
            if (!url.isEmpty()) {
                return url;
            }

        } catch (Throwable unused) {
        }
        return "";
    }

    private void openMiniProgram(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return;
            }
            Log.record("鱼塘📱打开小程序: " + (url.length() > 50 ? url.substring(0, 50) + "..." : url));
        } catch (Throwable th) {
            Log.record("鱼塘❌打开小程序失败[" + th.getMessage() + "]");
        }
    }

    private static class FishResult {
        private static final int TYPE_FAIL = 2;
        private static final int TYPE_NEED_POSITIONING = 6;
        private static final int TYPE_SUCCESS = 1;
        private static final int TYPE_TOKEN_INVALID = 4;
        private static final int TYPE_TOO_SMALL = 3;
        private static final int TYPE_WELFARE = 5;
        private final JSONObject angleAdInfo;
        private final String bizNo;
        private final boolean converted;
        private final String fishName;
        private final String fishWeight;
        private final boolean needPositioning;
        private final int rodCount;
        private final int type;

        private FishResult(int type, boolean needPositioning, int rodCount, String fishName, String fishWeight, String bizNo, JSONObject angleAdInfo, boolean converted) {
            this.type = type;
            this.needPositioning = needPositioning;
            this.rodCount = rodCount;
            this.fishName = fishName;
            this.fishWeight = fishWeight;
            this.bizNo = bizNo;
            this.angleAdInfo = angleAdInfo;
            this.converted = converted;
        }

        static FishResult success(int rodCount, String fishName, String fishWeight, String bizNo, JSONObject angleAdInfo, boolean converted) {
            return new FishResult(TYPE_SUCCESS, false, rodCount, fishName, fishWeight, bizNo, angleAdInfo, converted);
        }

        static FishResult fail() {
            return new FishResult(TYPE_FAIL, false, 0, "", "", "", null, false);
        }

        static FishResult tokenInvalid() {
            return new FishResult(TYPE_TOKEN_INVALID, false, 0, "", "", "", null, false);
        }

        static FishResult tooSmall() {
            return new FishResult(TYPE_TOO_SMALL, false, 0, "", "", "", null, false);
        }

        static FishResult needPositioning(String bizNo) {
            return new FishResult(TYPE_NEED_POSITIONING, true, 0, "", "0", bizNo, null, false);
        }

        boolean isSuccess() {
            return this.type == TYPE_SUCCESS;
        }

        boolean isTokenInvalid() {
            return this.type == TYPE_TOKEN_INVALID;
        }

        boolean isTooSmall() {
            return this.type == TYPE_TOO_SMALL;
        }

        boolean isNeedPositioning() {
            return this.needPositioning || this.type == TYPE_WELFARE || this.type == TYPE_NEED_POSITIONING;
        }

        boolean isConverted() {
            return this.converted;
        }

        String getFishName() {
            return this.fishName;
        }

        String getFishWeight() {
            return this.fishWeight;
        }

        String getBizNo() {
            return this.bizNo;
        }

        JSONObject getAngleAdInfo() {
            return this.angleAdInfo;
        }
    }

    private static String toAwardChineseName(String awardType) {
        switch (awardType) {
            case "FISHROD":
                return "钓竿";
            case "FISHINGBAIT":
                return "鱼饵";
            case "GOLD":
                return "金币";
            case "DIAMOND":
                return "钻石";
            case "FISH":
                return "鱼苗";
            default:
                return awardType;
        }
    }

    private static String toStatusChineseName(String status) {
        switch (status) {
            case "FINISHED":
                return "已完成";
            case "TODO":
                return "待完成";
            case "TODAY_FINISH":
                return "今日已完成";
            case "TODAY_TODO":
                return "今日待完成";
            default:
                return status;
        }
    }
}
