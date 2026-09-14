package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.hook.ApplicationHook;

/** 信用2101活动用到的 RPC。移植自 GR 分支，见 doc/MyFix.md。 */
public final class Credit2101RpcCall {
    private Credit2101RpcCall() {
    }

    public static String queryAccountAsset() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryAccountAsset", "[{}]");
    }

    public static String triggerBenefit() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.triggerBenefit", "[{}]");
    }

    public static String querySignInData() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.querySignInData", "[{}]");
    }

    public static String userSignIn(int day) {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.userSignIn", "[{\"day\":" + day + "}]");
    }

    public static String queryGridEvent(String cityCode, double latitude, double longitude, boolean guideState) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("cityCode", cityCode);
        ext.put("latitude", latitude);
        ext.put("longitude", longitude);
        JSONObject request = new JSONObject();
        request.put("extParams", ext);
        request.put("guideState", guideState);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryGridEvent", "[" + request + "]");
    }

    public static String eventGameStart(String batchNo, String eventId, String stageId) throws JSONException {
        return requestEvent("com.alipay.innovationprod.biz.rpc.eventGameStart", batchNo, eventId, stageId, null);
    }

    public static String eventGameComplete(String batchNo, String eventId, String stageId, JSONObject extParams) throws JSONException {
        JSONObject request = eventRequest(batchNo, eventId, stageId);
        request.put("passed", 1);
        request.put("extParams", extParams == null ? JSONObject.NULL : extParams);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.eventGameComplete", "[" + request + "]");
    }

    public static String eventGameCompleteCollectYj(String batchNo, String eventId, String stageId, int collectedYj) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("collectedYJ", collectedYj);
        return eventGameComplete(batchNo, eventId, stageId, ext);
    }

    public static String collectCredit(String batchNo, String eventId, String cityCode, double latitude, double longitude) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("cityCode", cityCode);
        ext.put("latitude", latitude);
        ext.put("longitude", longitude);
        JSONObject request = new JSONObject();
        request.put("batchNo", batchNo);
        request.put("eventId", eventId);
        request.put("extParams", ext);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.collectCredit", "[" + request + "]");
    }

    public static String queryBlackMarkEvent(String eventId) {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryBlackMarkEvent", "[{\"eventId\":\"" + eventId + "\"}]");
    }

    public static String joinBlackMarkEvent(int creditEnergy, String eventId) throws JSONException {
        return blackMarkRequest("com.alipay.innovationprod.biz.rpc.joinBlackMarkEvent", creditEnergy, eventId);
    }

    public static String chargeBlackMarkEvent(int creditEnergy, String eventId) throws JSONException {
        return blackMarkRequest("com.alipay.innovationprod.biz.rpc.chargeBlackMarkEvent", creditEnergy, eventId);
    }

    public static String exploreGridEvent(String cityCode, double latitude, double longitude) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("cityCode", cityCode);
        ext.put("latitude", latitude);
        ext.put("longitude", longitude);
        JSONObject request = new JSONObject();
        request.put("extParams", ext);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.exploreGridEvent", "[" + request + "]");
    }

    public static String queryUserTask() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryUserTask", "[{}]");
    }

    public static String operateTask(String action, String taskConfigId) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("taskAction", action);
        request.put("taskConfigId", taskConfigId);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.operateTask", "[" + request + "]");
    }

    public static String awardTask(String taskConfigId) {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.awardTask", "[{\"taskConfigId\":\"" + taskConfigId + "\"}]");
    }

    public static String queryEventGate(String batchNo, String eventId, String cityCode, double latitude, double longitude) throws JSONException {
        return eventWithLocation("com.alipay.innovationprod.biz.rpc.queryEventGate", batchNo, eventId, cityCode, latitude, longitude);
    }

    public static String completeEventGate(String batchNo, String eventId, String cityCode, double latitude, double longitude, String storyId) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("cityCode", cityCode);
        ext.put("latitude", latitude);
        ext.put("longitude", longitude);
        ext.put("storyId", storyId);
        JSONObject request = new JSONObject();
        request.put("batchNo", batchNo);
        request.put("eventId", eventId);
        request.put("extParams", ext);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.completeEventGate", "[" + request + "]");
    }

    public static String queryPopupView(String popupId) {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryPopupView", "[{\"popupId\":\"" + popupId + "\"}]");
    }

    public static String queryChapterProgress() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryChapterProgress", "[{}]");
    }

    public static String completeChapterAction(String action, String chapter) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("action", action);
        request.put("chapter", chapter);
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.completeChapterAction", "[" + request + "]");
    }

    public static String queryRelationTalent() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryRelationTalent", "[{}]");
    }

    public static String upgradeTalentAttribute(String attrType, String treeType, int targetLevel) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("roleId", "");
        request.put("talentAttributeType", attrType);
        request.put("talentTreeType", treeType);
        request.put("targetAttributeLevel", String.valueOf(targetLevel));
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.upgradeTalentAttribute", "[" + request + "]");
    }

    public static String queryGuardMarkList() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.queryGuardMarkList", "[{}]");
    }

    public static String claimGuardMarkAward() {
        return ApplicationHook.requestString("com.alipay.innovationprod.biz.rpc.claimGuardMarkAward", "[{}]");
    }

    private static JSONObject eventRequest(String batchNo, String eventId, String stageId) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("batchNo", batchNo);
        request.put("eventId", eventId);
        request.put("miniGameStageId", stageId);
        return request;
    }

    private static String requestEvent(String method, String batchNo, String eventId, String stageId, JSONObject ext) throws JSONException {
        JSONObject request = eventRequest(batchNo, eventId, stageId);
        if (ext != null) {
            request.put("extParams", ext);
        }
        return ApplicationHook.requestString(method, "[" + request + "]");
    }

    private static String blackMarkRequest(String method, int creditEnergy, String eventId) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("creditEnergy", creditEnergy);
        request.put("eventId", eventId);
        return ApplicationHook.requestString(method, "[" + request + "]");
    }

    private static String eventWithLocation(String method, String batchNo, String eventId, String cityCode, double latitude, double longitude) throws JSONException {
        JSONObject ext = new JSONObject();
        ext.put("cityCode", cityCode);
        ext.put("latitude", latitude);
        ext.put("longitude", longitude);
        JSONObject request = new JSONObject();
        request.put("batchNo", batchNo);
        request.put("eventId", eventId);
        request.put("extParams", ext);
        return ApplicationHook.requestString(method, "[" + request + "]");
    }
}
