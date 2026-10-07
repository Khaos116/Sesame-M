package io.github.aw1y2z.sesame.model.task.antOrchard;

import android.util.Base64;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class AntOrchardRpcCall {

    private static final String VERSION = "20250812.01";

    public static String orchardIndex() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardIndex", "[{\"inHomepage\":\"true\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String mowGrassInfo() {
        return ApplicationHook.requestString("com.alipay.antorchard.mowGrassInfo", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"showRanking\":true,\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String batchHireAnimalRecommend(String orchardUserId) {
        return ApplicationHook.requestString("com.alipay.antorchard.batchHireAnimalRecommend", "[{\"orchardUserId\":\"" + orchardUserId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String batchHireAnimal(List<String> recommendGroupList) {
        return ApplicationHook.requestString("com.alipay.antorchard.batchHireAnimal", "[{\"recommendGroupList\":[" + String.join(",", recommendGroupList) + "],\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sceneType\":\"weed\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String extraInfoGet() {
        return ApplicationHook.requestString("com.alipay.antorchard.extraInfoGet", "[{\"from\":\"entry\",\"requestType\":\"NORMAL\",\"sceneCode\":\"FUGUO\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String extraInfoSet() {
        return ApplicationHook.requestString("com.alipay.antorchard.extraInfoSet", "[{\"bizCode\":\"fertilizerPacket\",\"bizParam\":{\"action\":\"queryCollectFertilizerPacket\"},\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String querySubplotsActivity(String treeLevel) {
        return ApplicationHook.requestString("com.alipay.antorchard.querySubplotsActivity", "[{\"activityType\":[\"WISH\",\"BATTLE\",\"HELP_FARMER\",\"DEFOLIATION\",\"CAMP_TAKEOVER\"],\"inHomepage\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," + "\"source" + "\":\"ch_appcenter__chsub_9patch\",\"treeLevel\":\"" + treeLevel + "\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String triggerSubplotsActivity(String activityId, String activityType, String optionKey) {
        return ApplicationHook.requestString("com.alipay.antorchard.triggerSubplotsActivity", "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType + "\",\"optionKey\":\"" + optionKey + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\"," + "\"source" + "\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String receiveOrchardRights(String activityId, String activityType) {
        return ApplicationHook.requestString("com.alipay.antorchard.receiveOrchardRights", "[{\"activityId\":\"" + activityId + "\",\"activityType\":\"" + activityType + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    /* 七日礼包 */
    public static String drawLottery() {
        return ApplicationHook.requestString("com.alipay.antorchard.drawLottery", "[{\"lotteryScene\":\"receiveLotteryPlus\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String orchardSyncIndex() {
        return ApplicationHook.requestString("com.alipay.antorchard.orchardSyncIndex", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"syncIndexTypes\":\"QUERY_MAIN_ACCOUNT_INFO\",\"version\":\"" + VERSION + "\"}]");
    }

    /**
     * 施肥。{@code plantScene} 必须是当前已切换到的场景（{@code main}/{@code yeb}）：
     * 报文里声明成别的场景，服务端会回 P03「平行场景信息异常」，余额宝(摇钱树)场景就施肥失败。
     */
    public static String orchardSpreadManure(String plantScene, Boolean useBatchSpread, String wua) {
        String useBatchSpreadStr = Boolean.TRUE.equals(useBatchSpread) ? "true" : "false";
        String scene = (plantScene == null || plantScene.isEmpty()) ? "main" : plantScene;
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSpreadManure", "[{\"plantScene\":\"" + scene + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"useBatchSpread\":" + useBatchSpreadStr + ",\"version\":\"" + VERSION + "\",\"wua\":\"" + (wua != null ? wua : "") + "\"}]");
    }

    public static String receiveTaskAward(String sceneCode, String taskType) {
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", "[{\"ignoreLimit\":false,\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskType\":\"" + taskType + "\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String orchardListTask() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardListTask", "[{\"plantHiddenMMC\":\"false\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String orchardSign() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSign", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"signScene\":\"ANTFARM_ORCHARD_SIGN_V2\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String finishTask(String sceneCode, String taskType) {
        String userId = UserIdMap.getCurrentUid();
        String args = "[{\"outBizNo\":\"" + userId + System.currentTimeMillis() + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"ANTFARM_ORCHARD\",\"taskType\":\"" + taskType + "\",\"userId\":\"" + userId + "\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", args);
    }

    /**
     * 按 bizKey 完成任务（{@code com.alipay.antfarm.doFarmTask}）。
     * <p>乐园游戏类任务（taskId 形如 {@code ORCHARD_NCLY_*}、groupId {@code ORCHARD_NCLY_GAME_IAA} 等）
     * 服务端拒绝 {@code com.alipay.antiep.finishTask}（400000040 不支持rpc调用），与庄园抽抽乐同型；
     * 庄园的正解就是这条接口（2026-09-22 实测 cclyx / ipccl 前缀的游戏任务走它全部成功）。
     */
    public static String doFarmTask(String bizKey, String taskSceneCode) {
        // 另一种实现方案 payload 只剩一份实现，见 TaskAlternative.request（version 沿用本模块的 VERSION）
        return TaskAlternative.request(bizKey, taskSceneCode, VERSION);
    }

    public static String triggerTbTask(String taskId, String taskPlantType) {
        return ApplicationHook.requestString("com.alipay.antfarm.triggerTbTask", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"taskId\":\"" + taskId + "\",\"taskPlantType\":\"" + taskPlantType + "\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String orchardSeedList() {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSeedList", "[{\"from\":\"SEED_LIST\",\"page\":0,\"pvuuid\":\"" + System.currentTimeMillis() + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcollect__chsub_my-recentlyUsed\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String orchardSelectSeed(String seedCode) {
        return ApplicationHook.requestString("com.alipay.antfarm.orchardSelectSeed", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"seedCode\":\"" + seedCode + "\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    /* 砸金蛋 */
    public static String queryGameCenter() {
        return ApplicationHook.requestString("com.alipay.antorchard.queryGameCenter", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String noticeGame(String appId) {
        return ApplicationHook.requestString("com.alipay.antorchard.noticeGame", "[{\"appId\":\"" + appId + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    public static String submitUserAction(String gameId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction",
                "[{\"actionCode\":\"enterGame\",\"gameId\":" + JSONObject.quote(gameId)
                        + ",\"paladinxVersion\":\"2.1.95\",\"source\":\"gameFramework\"}]");
    }

    public static String submitUserPlayDurationAction(String gameAppId, String source) {
        return submitUserPlayDurationAction(gameAppId, source, 32);
    }

    public static String orchardVisit(String source, boolean taobao) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("requestType", "NORMAL").put("sceneCode", "ORCHARD")
                .put("source", source).put("version", taobao ? "" : VERSION);
        if (!taobao) body.put("inHomepage", true);
        return ApplicationHook.requestString(taobao ? "com.alipay.antorchard.orchardSimple" : "com.alipay.antfarm.orchardIndex", new JSONArray().put(body).toString());
    }

    public static String listStarTasks() throws org.json.JSONException {
        JSONArray ids = new JSONArray();
        for (String id : new String[]{"ORCHARD_NORMAL_STAR", "ORCHARD_NCLY_STAR30s_NCMXY", "ORCHARD_NCLY_STAR30s_NCDDP", "ORCHARD_NCLY_STAR30s_MSQYJ", "ORCHARD_NCLY_STAR30s_NCZPT"}) ids.put(id);
        JSONObject body = MyUtils.newJSONObject().put("extend", MyUtils.newJSONObject().put("taskIdList", ids))
                .put("requestType", "NORMAL").put("sceneCode", "ANTFARM_ORCHARD_TASK_V2").put("source", "ch_appcenter__chsub_9patch").put("version", VERSION);
        return ApplicationHook.requestString("com.alipay.antiep.listTask", new JSONArray().put(body).toString());
    }

    public static String finishStarTask(String scene, String taskId) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("outBizNo", taskId + "_" + System.currentTimeMillis())
                .put("requestType", "NORMAL").put("sceneCode", scene).put("source", "h5").put("taskType", taskId).put("version", VERSION);
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", new JSONArray().put(body).toString());
    }

    public static String orchardXlight(JSONObject position, JSONObject sdk) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("positionRequest", position).put("sdkPageInfo", sdk);
        return ApplicationHook.requestString("com.alipay.adexchange.ad.facade.xlightPlugin", new JSONArray().put(body).toString());
    }

    public static String finishOrchardBrowse(String playBizId, JSONObject event, String scene, String taskType) throws org.json.JSONException {
        JSONObject ext = MyUtils.newJSONObject();
        if (!scene.isEmpty()) ext.put("iepTaskSceneCode", scene);
        if (!taskType.isEmpty()) ext.put("iepTaskType", taskType);
        JSONObject body = MyUtils.newJSONObject().put("extendInfo", ext).put("playBizId", playBizId).put("playEventInfo", event).put("source", "adx");
        return ApplicationHook.requestString("com.alipay.adtask.biz.mobilegw.service.interaction.finish", new JSONArray().put(body).toString());
    }

    public static String submitUserPlayDurationAction(String appId, String source, int seconds) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
                "[{\"gameAppId\":" + JSONObject.quote(appId) + ",\"playTime\":" + seconds
                        + ",\"source\":" + JSONObject.quote(source) + ",\"statisticTag\":\"\"}]");
    }

    // 此版本号来自待核实的导出 smali，仅用于时长任务，避免改变现有农场请求。
    private static JSONObject gameStayBody() throws org.json.JSONException {
        return MyUtils.newJSONObject().put("requestType", "NORMAL").put("sceneCode", "ORCHARD")
                .put("source", "ch_appcenter__chsub_9patch").put("version", "20260721.01");
    }

    public static String indexFeeds(int page) throws org.json.JSONException {
        JSONArray filters = new JSONArray();
        for (String type : new String[]{"qianyi", "game"}) {
            filters.put(MyUtils.newJSONObject().put("itemType", type).put("itemIdList", new JSONArray()));
        }
        JSONObject body = gameStayBody().put("pageNum", page).put("filterItemList", filters)
                .put("commonDegradeResultVO", MyUtils.newJSONObject().put("deviceLevel", "high")
                        .put("resultReason", 0).put("resultType", 0));
        return ApplicationHook.requestString("com.alipay.antorchard.indexFeeds", new JSONArray().put(body).toString());
    }

    public static String orchardIndexDelivery() throws org.json.JSONException {
        JSONObject body = gameStayBody().put("enableTeamType", new JSONArray().put("team"))
                .put("inTeam", true).put("teamPositionSwitch", true).put("teamType", "help")
                .put("commonDegradeResult", MyUtils.newJSONObject().put("deviceLevel", "high")
                        .put("resultReason", 0).put("resultType", 0));
        return ApplicationHook.requestString("com.alipay.antorchard.orchardIndexDelivery", new JSONArray().put(body).toString());
    }

    public static String noticeGameStay(String appId) throws org.json.JSONException {
        return ApplicationHook.requestString("com.alipay.antorchard.noticeGame",
                new JSONArray().put(gameStayBody().put("appId", appId)).toString());
    }

    public static String submitGameStayEvent(String appId, String event, long elapsed) throws org.json.JSONException {
        JSONObject attrs = MyUtils.newJSONObject().put("CH_INFO", "lianyun_nc_sydb").put("CPS_ID", "unknown")
                .put("GAME_VERSION", "6.5.72").put("PALADINX_VERSION", "2.1.95")
                .put("PLAY_SCENE", "NORMAL").put("SCENE_ID", "nongchangleyuan");
        if ("GAME_FIRST_FRAME".equals(event)) attrs.put("ALIVE_ENTER", "0");
        if (elapsed > 0) attrs.put("GAME_ELAPASED_TIME", elapsed);
        String source = "GAME_FIRST_FRAME".equals(event) ? "paladinx_auto"
                : "loading_completed".equals(event) ? "my.reportLoadingCompleted" : "my.reportGamePlay";
        JSONObject body = MyUtils.newJSONObject().put("appId", appId).put("eventAttrMap", attrs)
                .put("eventId", event).put("source", source)
                .put("idempotentNo", "platform_" + System.currentTimeMillis() + "_" + RandomUtil.getRandom(5) + "_" + RandomUtil.getRandom(1));
        return ApplicationHook.requestString("com.alipay.gameevent.biz.rpc.submitEvent", new JSONArray().put(body).toString());
    }

    public static String finishGameStayTask(String scene, String taskId) throws org.json.JSONException {
        // 参数格式以 SJ APK 的 finishTask 为参照，不沿用导出代码中的空 userId。
        JSONObject body = MyUtils.newJSONObject().put("outBizNo", taskId + "_" + System.currentTimeMillis() + "_" + RandomUtil.getRandom(3))
                .put("requestType", "NORMAL").put("sceneCode", scene).put("source", "ch_appcenter__chsub_9patch")
                .put("taskType", taskId).put("userId", UserIdMap.getCurrentUid()).put("version", "0.1.2411251623.29");
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", new JSONArray().put(body).toString());
    }

    public static String smashedGoldenEgg() {
        return ApplicationHook.requestString("com.alipay.antorchard.smashedGoldenEgg", "[{\"requestType\":\"NORMAL\",\"seneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }

    /* 助力好友 */
    public static String achieveBeShareP2P(String friendUserId) {
        String shareId = Base64.encodeToString((friendUserId + "-" + RandomUtil.getRandom(5) + "ANTFARM_ORCHARD_SHARE_P2P").getBytes(), Base64.NO_WRAP);
        String args = "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ANTFARM_ORCHARD_SHARE_P2P\",\"shareId\":\"" + shareId + "\",\"source\":\"share\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.achieveBeShareP2P", args);
    }

    public static String switchPlantScene(String sceneName) {
        return ApplicationHook.requestString("com.alipay.antorchard.switchPlantScene", "[{\"plantScene\":\"" + sceneName + "\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }

    public static String choosePrize(String orderId) {
        return ApplicationHook.requestString("com.alipay.antfarmlite.choosePrize", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"sendOrderId\":\"" + orderId + "\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }

    public static String yebPlantSceneRevenuePage() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.yebPlantSceneRevenuePage", "[{\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ANTFARM_ORCHARD\"}]");
    }

    public static String triggerYebMoneyTree() {
        return ApplicationHook.requestString("com.alipay.yebbffweb.needle.yebHome.moneyTree.trigger", "[{\"sceneType\":\"default\",\"type\":\"trigger\"}]");
    }

    /**
     * 领取回访奖励
     */
    public static String receiveOrchardVisitAward() {
        // 2026-09-22 抓包（logs/chk_orchard 14:20:10）：官方报文带 diversionSource=DEFAULT；
        // 缺这个字段服务端回 102「参数异常」（模块 14:23 实测命中）
        return ApplicationHook.requestString("com.alipay.antorchard.receiveOrchardVisitAward", "[{\"diversionSource\":\"DEFAULT\",\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"ch_appcenter__chsub_9patch\",\"version\":\"" + VERSION + "\"}]");
    }


    public static String queryOptionalPlay() {
        String args1 = "[{\"bizType\":\"ANTORCHARD\",\"commonDegradeFilterRequest\":{\"appMode\":\"normal\",\"deviceLevel\":\"high\",\"initialized\":true,\"platform\":\"Android\",\"unityDeviceLevel\":\"high\"},\"playTypeList\":[\"TASK_TRIGGER\",\"TOP_UP_COUPON\"],\"recentAppRecordList\":[],\"requestType\":\"NORMAL\",\"sceneCode\":\"ORCHARD\",\"source\":\"H5\",\"version\":\"" + VERSION + "\"}]";
        return ApplicationHook.requestString("com.alipay.charitygamecenter.queryOptionalPlay", args1);
    }

    public static String receiveTaskAwardantorchard(int awardCountForReceive, String sceneCode, String taskType) {
        String args1 = "[{\"awardCountForReceive\":" + awardCountForReceive + ",\"ignoreLimit\":true,\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"antorchard\",\"taskType\":\"" + taskType + "\"}]";
        return ApplicationHook.requestString("com.alipay.antieptask.receiveTaskAwardantorchard", args1);
    }

    /* ============ 农场抽抽乐（阿肥寻宝记 / 农场抽抽乐普通版） ============ */

    /**
     * 农场抽抽乐-进入/查询活动
     */
    public static String enterDrawActivityantorchard(String activityId, String sceneCode, String source) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("activityId", activityId)
                .put("context", MyUtils.newJSONObject().put("appMode", "student"))
                .put("requestType", "RPC").put("sceneCode", sceneCode).put("source", source);
        String args = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.enterDrawActivityantorchard", args);
    }

    /**
     * 农场抽抽乐-请求任务列表
     */
    public static String listTaskantorchard(String sceneCode, String source) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("extend", MyUtils.newJSONObject().put("appMode", "student"))
                .put("requestType", "RPC").put("sceneCode", sceneCode).put("source", source);
        String args = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antieptask.listTaskantorchard", args);
    }

    /**
     * 农场抽抽乐-领取任务奖励（与 {@link #receiveTaskAwardantorchard} 区分：抽抽乐场景无 awardCountForReceive 字段）
     */
    public static String receiveDrawTaskAwardantorchard(String sceneCode, String taskType) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("ignoreLimit", true).put("requestType", "RPC")
                .put("sceneCode", sceneCode).put("source", "antorchard").put("taskType", taskType);
        String args = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antieptask.receiveTaskAwardantorchard", args);
    }

    /**
     * 农场抽抽乐-抽奖
     */
    public static String drawantorchard(String activityId, String sceneCode, String source, String userId) {
        String args = "[{\"activityId\":\"" + activityId + "\",\"requestType\":\"RPC\",\"sceneCode\":\"" + sceneCode + "\",\"source\":\"" + source + "\",\"userId\":\"" + userId + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.drawantorchard", args);
    }

    /**
     * 农场抽抽乐-同步抽奖次数
     */
    public static String drawSyncantorchard(String activityId, String source) throws org.json.JSONException {
        return drawSyncantorchard(activityId, "ANTORCHARD_DRAW_TIMES", source);
    }

    public static String drawSyncantorchard(String activityId, String sceneCode, String source) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("activityId", activityId)
                .put("context", MyUtils.newJSONObject().put("appMode", "student"))
                .put("requestType", "RPC").put("sceneCode", sceneCode).put("source", source);
        String args = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.drawSyncantorchard", args);
    }

    public static String batchDrawantorchard(String activityId, String sceneCode, String source, int times, String userId) throws org.json.JSONException {
        JSONObject body = MyUtils.newJSONObject().put("activityId", activityId).put("requestType", "RPC")
                .put("sceneCode", sceneCode).put("source", source).put("times", times).put("userId", userId);
        return ApplicationHook.requestString("com.alipay.antiepdrawprod.batchDrawantorchard", new JSONArray().put(body).toString());
    }

    /**
     * 农场抽抽乐-完成任务（小游戏/广告等 TODO 任务，尝试自动完成，失败由调用方拉黑）
     * 与 listTaskantorchard 同 facade(antieptask)，参数格式参照庄园 finishTaskopengreen
     */
    public static String finishTaskantorchard(String taskType, String sceneCode) throws org.json.JSONException {
        String taskTypeRandom = taskType + "_" + System.currentTimeMillis() + "_" + RandomUtil.getRandomString(8);
        JSONObject body = MyUtils.newJSONObject().put("outBizNo", taskTypeRandom).put("requestType", "RPC")
                .put("sceneCode", sceneCode).put("source", "antorchard").put("taskType", taskType);
        String requestData = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antieptask.finishTaskantorchard", requestData);
    }

    /**
     * 农场抽抽乐-完成任务（互备腿：基于抓包证据，果园任务完成走 com.alipay.antiep.finishTask，NORMAL+userId+version 格式）
     */
    public static String finishTaskantorchardV2(String taskType, String sceneCode, String userId) throws org.json.JSONException {
        String outBizNo = userId + System.currentTimeMillis();
        JSONObject body = MyUtils.newJSONObject().put("outBizNo", outBizNo).put("requestType", "NORMAL")
                .put("sceneCode", sceneCode).put("source", "antorchard").put("taskType", taskType)
                .put("userId", userId).put("version", VERSION);
        String requestData = new JSONArray().put(body).toString();
        return ApplicationHook.requestString("com.alipay.antiep.finishTask", requestData);
    }


    /**
     * 带参数的 orchardSyncIndex（适配第二个文件中的调用）
     * 注意：这里参数被忽略，调用无参版本
     */
    public static String orchardSyncIndex(String param) {
        return orchardSyncIndex();
    }

}
