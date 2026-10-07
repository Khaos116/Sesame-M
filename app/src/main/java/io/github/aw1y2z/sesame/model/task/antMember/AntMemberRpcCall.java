package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.MyUtils;

public class AntMemberRpcCall {

    public static String querySesameGiftList(int page, String tab) throws JSONException {
        JSONArray tabs = new JSONArray();
        if (tab != null && !tab.isEmpty()) tabs.put(tab);
        JSONObject body = MyUtils.newJSONObject().put("currentPage", page).put("formDelivery", "false").put("pageSize", 20)
                .put("privilegeSource", "").put("privilegeTab", "").put("tabList", tabs);
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryListV2", new JSONArray().put(body).toString());
    }

    public static String querySesameGiftDetail(String id) throws JSONException {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryDetail", new JSONArray().put(MyUtils.newJSONObject().put("awardTemplateId", id)).toString());
    }

    public static String obtainSesameGift(String id) throws JSONException {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.obtainAward", new JSONArray().put(MyUtils.newJSONObject().put("awardTemplateId", id)).toString());
    }

    public static String queryMySesameGift(String recordId) throws JSONException {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.award.AwardRpcManager.queryMyAwardDetail", new JSONArray().put(MyUtils.newJSONObject().put("awardId", recordId)).toString());
    }

    public static String queryYebExpGoldMain(String taskId) throws JSONException {
        JSONObject task = MyUtils.newJSONObject().put("downgrade", false).put("queryComplete", taskId != null)
                .put("strategyCode", "YEB_TRIAL_ASSET_TASK_BLOCK_REC");
        if (taskId != null) task.put("taskId", taskId).put("startTime", System.currentTimeMillis());
        JSONArray text = new JSONArray();
        for (String value : new String[]{"持", "续", "签", "到", "可", "领", ""}) text.put(MyUtils.newJSONObject().put("value", value));
        JSONObject sign = MyUtils.newJSONObject().put("daysOfQuerySignInData", 21).put("displaySignInTextList", text)
                .put("downgrade", false).put("todayRedDotText", "戳这里").put("tomorrowRedDotText", "");
        return ApplicationHook.requestString("com.alipay.yebscenebff.needle.yebExpGold.queryMain", new JSONArray().put(MyUtils.newJSONObject()
                .put("chInfo", "ch_url-https://render.alipay.com/p/yuyan/180020010001282160/index.html").put("signIn", sign).put("task", task)).toString());
    }

    public static String signInYebExpGold() throws JSONException {
        return ApplicationHook.requestString("com.alipay.yebscenebff.needle.yebExpGold.signIn", new JSONArray().put(MyUtils.newJSONObject().put("signInPlayId", "PLAY102253251")).toString(), 1, 0);
    }

    public static String triggerYebExpGoldReward(String taskId) throws JSONException {
        JSONObject params = MyUtils.newJSONObject().put("appletId", "AP12183159").put("taskId", taskId).put("version", 2);
        return ApplicationHook.requestString("com.alipay.yebscenebff.promosdk.index.forward", new JSONArray().put(MyUtils.newJSONObject().put("params", params).put("path", "task.trigger")).toString(), 1, 0);
    }

    public static String queryAccomplishmentHome(String tab) throws JSONException {
        JSONObject body = MyUtils.newJSONObject();
        if (tab != null && !tab.isEmpty()) body.put("tabCode", tab);
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.creditidentity.api.CreditIdentityAchievementRpcManager.queryAccomplishmentHomeV2", new JSONArray().put(body).toString());
    }

    public static String queryAccomplishmentDetail(String series) throws JSONException {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.creditidentity.api.CreditIdentityAchievementRpcManager.enterAccomplishmentDetailV2", new JSONArray().put(MyUtils.newJSONObject().put("medalSeriesCode", series)).toString());
    }

    public static String claimAccomplishment(String series) throws JSONException {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.creditidentity.api.CreditIdentityAchievementRpcManager.claimAccomplishmentV2", new JSONArray().put(MyUtils.newJSONObject().put("medalSeriesCode", series)).toString());
    }

    public static String merchantTranscodeCheck() {
        return ApplicationHook.requestString("alipay.mrchservbase.mrchbusiness.sign.transcode.check", "[{}]");
    }

    public static String merchantHomePage() throws JSONException {
        JSONObject context = MyUtils.newJSONObject().put("dispenseTaskItemCode", "ZDH_CONTINUE_QY_ZJ").put("isGuide", "true")
                .put("miniAppVersion", 20260601).put("underTakeTrace", "NULL");
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.sqyj.homepage.v5", new JSONArray().put(MyUtils.newJSONObject().put("context", context)).toString());
    }

    public static String merchantSign() {
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.sqyj.homepage.signin.v1", "[{\"signScene\":\"TASK_LIST_SIGN\"}]");
    }

    public static String merchantZcj(boolean execute) throws JSONException {
        return ApplicationHook.requestString("alipay.mrchservbase.zcj.view.invoke", new JSONArray().put(MyUtils.newJSONObject().put("compId", execute ? "ZCJ_SIGN_IN_EXECUTE" : "ZCJ_SIGN_IN_QUERY")).toString());
    }

    public static String merchantActivity() {
        return ApplicationHook.requestString("alipay.merchant.kmdk.query.activity", "[{\"scene\":\"activityCenter\"}]");
    }

    public static String merchantKmdkAction(String id, boolean signIn) throws JSONException {
        return ApplicationHook.requestString(signIn ? "alipay.merchant.kmdk.signIn" : "alipay.merchant.kmdk.signUp", new JSONArray().put(MyUtils.newJSONObject().put("activityNo", id)).toString());
    }

    public static String merchantBallQuery() throws JSONException {
        JSONObject context = MyUtils.newJSONObject().put("dispenseTaskItemCode", "ZDH_CONTINUE_QY_ZJ").put("isGuide", "true")
                .put("underTakeTrace", "NULL").put("userPath", "undertakeVisit");
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.ball.query.v1", new JSONArray().put(MyUtils.newJSONObject().put("context", context)).toString());
    }

    public static String merchantBallReceive(String id) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("ballIds", new JSONArray().put(id)).put("channel", "MRCH_SELF").put("outBizNo", java.util.UUID.randomUUID().toString());
        return ApplicationHook.requestString("alipay.mrchservbase.mrchpoint.ball.receive", new JSONArray().put(args).toString());
    }

    public static String merchantTaskQuery(boolean service, String orderTaskCode) throws JSONException {
        JSONObject params = MyUtils.newJSONObject().put("orderTaskCode", orderTaskCode).put("platform", "Android").put("version", "2.0");
        if (service) params.put("showFinishStageTask", "true");
        JSONObject args = MyUtils.newJSONObject().put("paramMap", params).put("taskItemCode", "");
        return ApplicationHook.requestString(service ? "alipay.mrchservbase.task.service.query" : "alipay.mrchservbase.task.more.query", new JSONArray().put(args).toString());
    }

    public static String merchantTaskReceive(String code) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("compId", "ZTS_TASK_RECEIVE").put("extInfo", MyUtils.newJSONObject().put("taskCode", code));
        return ApplicationHook.requestString("alipay.mrchservbase.sqyj.task.receive", new JSONArray().put(args).toString());
    }

    public static String merchantActionQuery(String code) throws JSONException {
        return ApplicationHook.requestString("alipay.mrchservbase.task.query.by.actioncode", new JSONArray().put(MyUtils.newJSONObject().put("actionCode", code)).toString());
    }

    public static String merchantActionProduce(String code, String channel) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("actionCode", code);
        if (!channel.isEmpty()) args.put("channel", channel);
        return ApplicationHook.requestString("alipay.mrchservbase.biz.task.action.produce", new JSONArray().put(args).toString());
    }

    public static String merchantExamPage(String code) throws JSONException {
        return ApplicationHook.requestString("alipay.mrchservbase.business.exam.page", new JSONArray().put(MyUtils.newJSONObject().put("taskCode", code)).toString());
    }

    public static String merchantTaskFinish(String bizId) throws JSONException {
        return ApplicationHook.requestString("com.alipay.adtask.biz.mobilegw.service.task.finish", new JSONArray().put(MyUtils.newJSONObject().put("bizId", bizId)).toString());
    }

    public static String queryStickerCanReceiveList(String year, String month) throws JSONException {
        return ApplicationHook.requestString("alipay.memberasset.sticker.queryStickerCanReceive", new JSONArray().put(MyUtils.newJSONObject().put("year", year).put("month", month)).toString());
    }

    public static String receiveSticker(String year, String month, JSONArray ids, JSONArray configs) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("year", year).put("month", month).put("stickerIds", ids).put("stickerCfgIds", configs);
        return ApplicationHook.requestString("alipay.memberasset.sticker.receiveSticker", new JSONArray().put(args).toString());
    }

    public static String queryStickerHomePage(String year, String month, String day) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("year", year).put("month", month).put("day", day).put("gmtBiz", "")
                .put("scene", "").put("source", "").put("stickerConfigId", "");
        return ApplicationHook.requestString("alipay.memberasset.sticker.queryHomePage", new JSONArray().put(args).toString());
    }

    public static String upgradeStickerBatch(JSONArray requests) throws JSONException {
        return ApplicationHook.requestString("alipay.memberasset.sticker.upgradeStickerBatch", new JSONArray().put(MyUtils.newJSONObject().put("upgradeReqList", requests)).toString());
    }

    public static String queryStickerDetailPage(String year, String month, String id) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("year", year).put("month", month).put("stickerConfigId", id).put("stickerStatus", "received");
        return ApplicationHook.requestString("alipay.memberasset.sticker.queryDetailPage", new JSONArray().put(args).toString());
    }

    public static String triggerStickerUpgradePrize(String id) throws JSONException {
        return ApplicationHook.requestString("alipay.memberasset.sticker.triggerUpgradePrize", new JSONArray().put(MyUtils.newJSONObject().put("levelCode", "").put("stickerCfgId", id)).toString());
    }

    public static String queryStickerPrizeHomePage() throws JSONException {
        return ApplicationHook.requestString("alipay.memberasset.sticker.prize.home.page", new JSONArray().put(MyUtils.newJSONObject().put("externParams", MyUtils.newJSONObject())).toString());
    }

    public static String triggerStickerDrawing(String id) throws JSONException {
        return ApplicationHook.requestString("alipay.memberasset.prize.trigger", new JSONArray().put(MyUtils.newJSONObject().put("prizeQuotaRecordId", id).put("type", "Drawing")).toString());
    }

    private static String getUniqueId() {
        return String.valueOf(System.currentTimeMillis()) + RandomUtil.nextLong();
    }

    public static Boolean check() {
        RpcEntity rpcEntity = ApplicationHook.requestObject("alipay.antmember.biz.rpc.member.h5.queryPointCert", "[{\"page\":" + 1 + ",\"pageSize\":" + 8 + "}]", 1, 0);
        if (rpcEntity == null || !rpcEntity.getHasResult() || ApplicationHook.isOffline()) return false;
        try {
            JSONObject response = new JSONObject(rpcEntity.getResponseString());
            String error = response.optString("error");
            // 单接口冷却只限制会员任务，不能阻断其它模块或触发重新登录。
            if ("RPC_SKIPPED".equals(error)) return true;
            if (!error.isEmpty() && !"0".equals(error)) return false;
            // 未实名等业务拒绝仍是有效响应；hasError 同时包含业务失败，不能当作掉线标志。
            return response.length() > 0 && (response.has("success") || response.has("isSuccess")
                    || response.has("resultCode") || response.has("retCode") || !rpcEntity.getHasError());
        } catch (Exception e) {
            return false;
        }
    }

    /* ant member point */
    public static String queryPointCert(int page, int pageSize) {
        String args1 = "[{\"page\":" + page + ",\"pageSize\":" + pageSize + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.queryPointCert", args1);
    }

    public static String receivePointByUser(String certId) {
        String args1 = "[{\"certId\":" + certId + "}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.member.h5.receivePointByUser", args1);
    }

    public static String queryMemberSigninCalendar() {
        return ApplicationHook.requestString("com.alipay.amic.biz.rpc.signin.h5.queryMemberSigninCalendar", "[{\"autoSignIn\":true,\"invitorUserId\":\"\",\"sceneCode\":\"QUERY\"}]");
    }

    /* 会员任务 */
    public static String signPageTaskList() {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.signPageTaskList", "[{\"sourceBusiness\":\"antmember\",\"spaceCode\":\"ant_member_xlight_task\"}]");
    }

    public static String applyTask(String darwinName, Long taskConfigId) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.applyTask", "[{\"darwinExpParams\":{\"darwinName\":\"" + darwinName + "\"},\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"taskConfigId\":" + taskConfigId + "}]");
    }

    public static String executeTask(String bizParam, String bizSubType) {
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.executeTask", "[{\"bizOutNo\":\"" + (System.currentTimeMillis() - 16000L) + "\",\"bizParam\":\"" + bizParam + "\",\"bizSubType\":\"" + bizSubType + "\",\"bizType\":\"BROWSE\"}]");
    }

    public static String queryAllStatusTaskList() {
        String args = "[{\"sourceBusiness\":\"signInAd\"}]";
        return ApplicationHook.requestString("alipay.antmember.biz.rpc.membertask.h5.queryAllStatusTaskList", args);
    }

    /**
     * 黄金票收取
     *
     * @param str signInfo
     * @return 结果
     */
    public static String goldBillCollect(String str) {
        return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", "[{" + str + "\"trigger\":\"Y\"}]");
    }

    /**
     * 黄金票收取（兼容）
     */
    public static String goldBillCollect() {
        return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v2.index.collect", "[{}]");
    }

    /**
     * 黄金票首页数据
     */
    public static String queryGoldTicketHome() {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("bizScene", "ch_alipaysearch__chsub_normal");
            args.put("chInfo", "ch_alipaysearch__chsub_normal");
            args.put("taskId", "");
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.v2.index",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 黄金票首页场景收取（新版）
     */
    public static String goldTicketIndexCollect() {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("directModeDisableCollect", 1);
            args.put("from", "antfarm");
            args.put("trigger", "Y");
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.index.collect",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 福利中心刷新
     */
    public static String welfareCenterUpdate(int modeBitMask) {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("modeBitMask", modeBitMask);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.update",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 查询黄金票提取页信息
     * 用于获取最新的可用数量、基金ID (productId) 和 赠送份数 (bonusAmount)
     */
    public static String queryConsumeHome() {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("tabBubbleDeliverParam", MyUtils.newJSONObject());
            args.put("tabTypeDeliverParam", MyUtils.newJSONObject());
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.consume.query",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 提交提取黄金
     * @param amount 提取数量 (如 100, 200, 2900)
     * @param productId 基金ID
     * @param bonusAmount 额外赠送数量
     */
    public static String submitConsume(int amount, String productId, int bonusAmount) {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("exchangeAmount", amount);
            args.put("exchangeMoney", String.format(java.util.Locale.ROOT, "%.2f", amount / 1000.0));
            args.put("prizeType", "GOLD");
            args.put("productId", productId);
            args.put("bonusAmount", bonusAmount);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.consume.submit",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 任务查询推送
     * @param taskId 任务ID
     */
    public static String taskQueryPush(String taskId) {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("mode", 1);
            args.put("taskId", taskId);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.needle.taskQueryPush",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 任务触发/报名
     * @param taskId 任务ID
     */
    public static String goldBillTaskTrigger(String taskId) {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("taskId", taskId);
            return ApplicationHook.requestString("com.alipay.wealthgoldtwa.goldbill.v4.task.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 福利中心首页
     */
    public static String queryWelfareHome() {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("isResume", true);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.index",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * [新增] 签到 / 领取奖励
     * @param type "SIGN"
     */
    public static String welfareCenterTrigger(String type) {
        try {
            JSONObject args = MyUtils.newJSONObject();
            args.put("type", type);
            return ApplicationHook.requestString("com.alipay.finaggexpbff.needle.welfareCenter.trigger",
                    new JSONArray().put(args).toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 游戏中心签到查询
     */
    public static String querySignInBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.querySignInBall", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }

    /**
     * 游戏中心签到
     */
    public static String continueSignIn() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.continueSignIn", "[{\"sceneId\":\"GAME_CENTER\",\"signType\":\"NORMAL_SIGN\"}]");
    }

    /**
     * 游戏中心查询待领取乐豆列表
     */
    public static String queryPointBallList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]");
    }

    /**
     * 游戏中心全部领取
     */
    public static String batchReceivePointBall() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.batchReceivePointBall", "[{}]");
    }
    
    public static String doTaskSignup(String taskId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSignup", "[{\"taskId\":\"" + taskId + "\"}]");
    }
    
    public static String doTaskSend(String taskId) {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.doTaskSend", "[{\"taskId\":\"" + taskId + "\"}]");
    }
    
    public static String queryModularTaskList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryModularTaskList", "[{\"deviceLevel\":\"high\",\"source\":\"ch_appcollect__chsub_my-recentlyUsed\",\"sourceTab\":\"luckydraw\",\"unityDeviceLevel\":\"high\"}]");
    }
    
    public static String queryTaskList() {
        return ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v4.queryTaskList", "[{\"__git\":\"52f2c9969ae\",\"source\":\"ch_alipaysearch__chsub_normal\"}]");
    }
    
    /**
     * 查询可收取的芝麻粒
     *
     * @return 结果
     */
    public static String queryCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.queryCreditFeedback", "[{\"queryPotential\":false,\"size\":20,\"status\":\"UNCLAIMED\"}]");
    }

    /**
     * 芝麻信用首页
     *
     * @return 结果
     */
    public static String queryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryHome",
                "[{\"invokeSource\":\"zmHome\",\"miniZmGrayInside\":\"\",\"version\":\"week\"}]");
    }

    /**
     * 查询芝麻分进度任务列表
     * @return RPC响应
     */
    public static String queryGrowthBehaviorToDoList() {
        String requestData = "[{\"guideBehaviorId\":\"yuebao_7d\",\"invokeVersion\":\"1.0.2025.10.27\",\"switchNewPage\":true}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.queryToDoList", requestData);
    }

    /**
     * 接受/开启一个行为任务
     * @param behaviorId 任务ID
     * @return RPC响应
     */
    public static String openBehaviorCollect(String behaviorId) {
        String requestData = "[{\"behaviorId\":\"" + behaviorId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.openBehaviorCollect", requestData);
    }

    /**
     * 查询视频答题的题目信息
     * @return RPC响应
     */
    public static String queryDailyQuiz(String behaviorId) {
        String requestData = "[{\"behaviorId\":\""+behaviorId+"\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.queryDailyQuiz", requestData);
    }

    /**
     * 提交视频答题的答案
     * @param bizDate 从queryDailyQuiz获取的bizDate
     * @param questionId 问题ID
     * @param answerId 选择的答案ID
     * @return RPC响应
     */
    public static String pushDailyQuizAnswer(String behaviorId,long bizDate,String answerId, String questionId, String answerStatus) {
        String extInfo = "{\"answerId\":\"" + answerId + "\",\"answerStatus\":\""+answerStatus+"\",\"questionId\":\"" + questionId + "\"}";
        String requestData = "[{\"behaviorId\":\""+behaviorId+"\",\"bizDate\":" + bizDate + ",\"extInfo\":" + extInfo + "}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthtask.api.GrowthTaskRpcManager.pushDailyTask", requestData);
    }

    /**
     * 查询当前可领取的进度球
     * @return RPC响应
     */
    public static String queryScoreProgress() {
        String requestData = "[{\"needTotalProcess\":\"TRUE\",\"queryGuideInfo\":true,\"switchNewPage\":true}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.api.HomeV8RpcManager.queryScoreProgress", requestData);
    }

    /**
     * 收集一个或多个进度球
     * @param ballIdList 包含一个或多个进度球ID的JSONArray
     * @return RPC响应
     */
    public static String collectProgressBall(JSONArray ballIdList) {
        if (ballIdList == null || ballIdList.length() == 0) {
            return "{\"success\":false, \"resultView\":\"ballIdList为空\"}";
        }
        String requestData = "[{\"ballIdList\":" + ballIdList.toString() + "}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.growthbehavior.apiGrowthBehaviorRpcManager.collectProgressBall", requestData);
    }

    /**
     * 收取芝麻粒
     *
     * @param creditFeedbackId creditFeedbackId
     * @return 结果
     */
    //{"chInfo":"ch_zhimahome__chsub_zml_doudi","deliverStatus":"","deliveryTemplateId":"","sceneCode":"DAILY_MUST_DO_CARD","searchAddToHomeTask":true,"searchGuidePopFlag":true,"searchShareAssistTask":true,"searchSubscribeTask":true,"version":"new"}]}
    public static String CreditAccumulateStrategyRpcManager(String creditFeedbackId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{\"chInfo\":\"ch_zhimahome__chsub_zml_doudi\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"sceneCode\":\"DAILY_MUST_DO_CARD\"," +
                "\"searchAddToHomeTask\":true,\"searchGuidePopFlag\":true,\"searchShareAssistTask\":true,\"searchSubscribeTask\":true,\"version\":\"new\"}]");
    }

    public static String collectCreditFeedback(String creditFeedbackId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject("{}");
        args.put("collectAll", false).put("creditFeedbackId", creditFeedbackId).put("status", "UNCLAIMED");
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", new JSONArray().put(args).toString());
    }

    /**
     * 查询生活记录
     *
     * @return 结果
     */
    public static String promiseQueryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryHome", null);
    }

    public static String querySingleTemplate(String templateId) {
        String args = "[{\"templateId\":\"" + templateId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.querySingleTemplate", args);
    }

    public static String promiseJoin(JSONObject data) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.join", "[" + data + "]");
    }

    /**
     * 查询生活记录明细
     *
     * @param recordId recordId
     * @return 结果
     */
    public static String promiseQueryDetail(String recordId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.queryDetail", "[{\"recordId\":\"" + recordId + "\"}]");
    }

    /**
     * 查询会员积分兑换福利列表方法1
     *
     * @param userId     userId
     * @param deliveryId 分类码
     *                   94000SR2023102305988003: 0元起
     *                   94000SR2024011106752003: 0元起/公益道具
     *                   94000SR2024071108523003: 0元起/皮肤
     *                   94000SR2024071808609003: 皮肤
     * @return 分类下商品列表
     * @ param naviCode 导航分类码
     * 皮肤："bb82b"、0元起："全积分"、影音："13"
     */
    public static String queryDeliveryZoneDetail(String userId, String deliveryId) {
        String uniqueId = System.currentTimeMillis() + "全积分0and99999999INTELLIGENT_SORT" + userId;
        String args = "[{\"cityCode\":\"\",\"deliveryId\":\"" + deliveryId + "\",\"pageNum\":1,\"pageSize\":18,\"sourcePassMap\":{\"innerSource\":\"\",\"source\":\"myTab\",\"unid\":\"\"},\"topIdList\":[],\"uniqueId\":\"" + uniqueId + "\"}]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryDeliveryZoneDetail", args);
    }

    /**
     * 查询会员积分兑换福利列表方法2
     *
     * @param userId   userId
     * @param naviCode 导航分类码
     *                 特色："14"、出行："1"、美食："11"、日用："12"、上新：""
     * @return 分类下商品列表
     */
    public static String queryIndexNaviBenefitFlowV2(String userId, String naviCode, int pageNum) {
        String sortStrategy = "INTELLIGENT_SORT";
        String upperPoint = "99999999";
        String uniqueId = System.currentTimeMillis() + naviCode + "0and" + upperPoint + sortStrategy + userId;
        String args =
                "[\n" + "        {\n" + "            \"adCopyId\": \"\",\n" + "            \"benefitFlowSource\": \"REC\",\n" + "            \"cityCode\": \"\",\n" + "            \"excludeIds\": \"\",\n" + "            \"exposeChannel\": \"antmember\",\n" + "            \"fastTag\": \"\"," + "\n" + "            \"lowerPoint\": 0,\n" + "            \"naviCode\": \"" + naviCode + "\",\n" + "            \"pageNum\": " + pageNum + ",\n" + "            \"pageSize\": 50,\n" + "            \"requestSourceInfo\": \"-|feeds\",\n" + "            \"sortStrategy\": \"" + sortStrategy + "\",\n" + "            \"sourcePassMap\": {\n" + "                \"innerSource\": \"\",\n" + "                \"source\": \"myTab\",\n" + "                \"unid\": \"\"\n" + "            },\n" + "            \"stickyIdList\": [],\n" + "            \"tagCodeIdx\": -1,\n" + "            \"uniqueId\": \"" + uniqueId + "\",\n" + "            \"upperPoint\": " + upperPoint + ",\n" + "            \"withPointRange\": false\n" + "        }\n" + "    ]";
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.queryIndexNaviBenefitFlowV2", args);
    }

    /**
     * 会员积分兑换福利
     *
     * @param benefitId benefitId
     * @param itemId    itemId
     * @return 结果
     */
    public static String searchMemberBenefit(String keyword) throws JSONException {
        String stamp = Long.toString(System.currentTimeMillis());
        JSONObject source = MyUtils.newJSONObject().put("innerSource", "").put("source", "").put("unid", "");
        JSONObject args = MyUtils.newJSONObject().put("cityCode", "").put("clientOs", "Android")
                .put("clientVersion", ApplicationHook.getAlipayVersion().getVersionString()).put("pageNum", 1).put("pageSize", 20)
                .put("paramsMap", MyUtils.newJSONObject()).put("prePageAllZeroStock", false).put("previewCopyDbId", "")
                .put("query", keyword).put("recommend", false).put("searchId", stamp).put("sessionId", "session_" + stamp).put("sourcePassMap", source);
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.benefitSearchV2", new JSONArray().put(args).toString());
    }

    public static String querySingleBenefitDetail(String id) throws JSONException {
        JSONObject source = MyUtils.newJSONObject().put("innerSource", "a159.b52659").put("source", "").put("unid", java.util.UUID.randomUUID().toString());
        JSONObject args = MyUtils.newJSONObject().put("benefitId", id).put("cityCode", "").put("miniAppId", "")
                .put("requestSourceInfo", "SID:|5").put("sourcePassMap", source);
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.config.h5.querySingleBenefitDetail", new JSONArray().put(args).toString());
    }

    public static String exchangeBenefit(String benefitId, String itemId) throws JSONException {
        String requestId = "requestId" + System.currentTimeMillis();
        String alipayClientVersion = ApplicationHook.getAlipayVersion().getVersionString();
        JSONObject source = MyUtils.newJSONObject().put("alipayClientVersion", alipayClientVersion).put("innerSource", "")
                .put("mobileOsType", "Android").put("source", "").put("unid", "");
        JSONObject args = MyUtils.newJSONObject().put("benefitId", benefitId).put("cityCode", "").put("exchangeType", "POINT_PAY")
                .put("itemId", itemId).put("miniAppId", "").put("orderSource", "").put("requestId", requestId)
                .put("requestSourceInfo", "").put("sourcePassMap", source).put("userOutAccount", "");
        return ApplicationHook.requestString("com.alipay.alipaymember.biz.rpc.exchange.h5.exchangeBenefit", new JSONArray().put(args).toString());
    }

    // 我的快递任务
    public static String queryRecommendTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"extInfo\":{\"componentCode\":\"musi_test\"},\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iCCWtq%2FRt4kh\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }

    // 积分、肥料
    public static String trigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    // 森林活力值
    public static String queryforestHomePage() {
        String args1 = "[{\"activityParam\":{},\"configVersionMap\":{\"wateringBubbleConfig\":\"0\"},\"skipWhackMole\":false,\"source\":\"kuaidivitality\",\"version\":\"20240606\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryHomePage", args1);
    }

    public static String forestTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"KUAIDI_VITALITY\"},\"fromAct\":\"home_task_list\",\"source\":\"kuaidivitality\",\"version\":\"20240105\"}]";
        return ApplicationHook.requestString("alipay.antforest.forest.h5.queryTaskList", args1);
    }

    public static String forestreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"H5\",\"sceneCode\":\"ANTFOREST_VITALITY_TASK\",\"source\":\"ANTFOREST\",\"taskType\":\"KUAIDI_VITALITY\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }

    // 海洋碎片
    public static String queryoceanHomePage() {
        String args1 = "[{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryHomePage", args1);
    }

    public static String oceanTask() {
        String args1 = "[{\"extend\":{\"firstTaskType\":\"DAOLIU_WODEKUAIDIQUANYI\"},\"fromAct\":\"dynamic_task\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"wodekuaidiquanyi\",\"uniqueId\":\"" + getUniqueId() + "\",\"version\":\"20240115\"}]";
        return ApplicationHook.requestString("alipay.antocean.ocean.h5.queryTaskList", args1);
    }

    public static String oceanreceiveTaskAward() {
        String args1 = "[{\"ignoreLimit\":false,\"requestType\":\"RPC\",\"sceneCode\":\"ANTOCEAN_TASK\",\"source\":\"ANT_FOREST\",\"taskType\":\"DAOLIU_WODEKUAIDIQUANYI\",\"uniqueId\":\"" + getUniqueId() + "\"}]";
        return ApplicationHook.requestString("com.alipay.antiep.receiveTaskAward", args1);
    }

    // 普通任务
    public static String queryOrdinaryTask() {
        String args1 = "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\"}]";
        return ApplicationHook.requestString("alipay.promoprod.task.listQuery", args1);
    }

    public static String signuptrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"signup\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    public static String sendtrigger(String appletId) {
        String args1 = "[{\"appletId\":\"" + appletId + "\",\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\",\"stageCode\":\"send\"}]";
        return ApplicationHook.requestString("alipay.promoprod.applet.trigger", args1);
    }

    /**
     * 芝麻签到 - 通用完成接口（芝麻粒/炼金等）
     * 对应: com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask
     *
     * @param checkInDate yyyyMMdd
     * @param sceneCode   "zml" 对应芝麻粒福利签到, "alchemy" 对应芝麻炼金签到
     */
    public static String zmCheckInCompleteTask(String checkInDate, String sceneCode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"" + sceneCode + "\"}]");
    }

    /**
     * 获取芝麻信用任务列表
     */
    public static String CreditAccumulateStrategyRpcManager() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", "[{}]");
    }

    /**
     * 芝麻信用领取任务
     */
    public static String joinSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"templateId\":\"" + taskTemplateId + "\"}]");
    }

    /**
     * 芝麻信用获取任务回调
     */
    public static String feedBackSesameTask(String taskTemplateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", "[{\"actionType\":\"TO_COMPLETE\",\"templateId\":\"" + taskTemplateId + "\"}]");
    }

    /**
     * 芝麻信用完成任务
     */
    public static String finishSesameTask(String recordId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity", "[{\"recordId\":\"" + recordId + "\"}]");
    }

    /**
     * 查询可收取的芝麻粒
     */

    /**
     * 一键收取芝麻粒
     */
    public static String collectAllCreditFeedback() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmcustprod.biz.rpc.home.creditaccumulate.api.CreditAccumulateRpcManager.collectCreditFeedback", "[{\"collectAll\":true,\"status\":\"UNCLAIMED\"}]");
    }

    public static String alchemyQueryCheckIn(String scenecode) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists", "[{\"sceneCode\":\"" + scenecode + "\",\"version\":\"2025-10-22\"}]");
    }

    // ==================== 新增芝麻信用相关RPC方法 ====================

    /**
     * 芝麻信用-查询签到领粒任务列表
     * @return RPC调用结果字符串
     */
    public static String checkInQueryTaskLists() {
        String requestData = "[{\"version\":\"2025-10-22\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists", requestData);
    }

    /**
     * 芝麻信用-完成签到领粒任务
     * @param checkInDate 签到日期，格式为 "yyyyMMdd"
     * @return RPC调用结果字符串
     */
    public static String checkInCompleteTask(String checkInDate) {
        String requestData = "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"zml\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask", requestData);
    }

    /**
     * 获取芝麻信用任务列表
     */
    public static String queryAvailableSesameTask() {
        String requestData = "[{\"chInfo\":\"ch_zmxy_zmlsy__chsub_zmsy_jingangwei_lianjin\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"sceneCode\":\"DAILY_MUST_DO_CARD\",\"searchGuidePopFlag\":true,\"searchSubscribeTask\":true,\"version\":\"new\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", requestData);
    }

    /**
     * 芝麻信用领取任务
     */
    public static String joinSesameTaskNew(String taskTemplateId) {
        String requestData = "[{\"chInfo\":\"seasameList\",\"joinFromOuter\":false,\"sceneCode\":\"zml\",\"templateId\":\"" + taskTemplateId + "\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", requestData);
    }

    /**
     * 芝麻信用获取任务回调
     */
    public static String feedBackSesameTaskNew(String taskTemplateId) {
        String requestData = "[{\"actionType\":\"TO_COMPLETE\",\"bizType\":\"LIFE_RECORD\",\"sceneCode\":\"zml\",\"templateId\":\"" + taskTemplateId + "\",\"version\":\"new\"}]";
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", requestData);
    }

    /**
     * 另一种实现方案的 version：本模块**原有取值**，收敛到 {@link TaskAlternative} 时原样保留，不改已实测路径的报文。
     */
    public static final String DO_FARM_TASK_VERSION = "20250812.01";

    /**
     * 查询「最近一次操作任务」：join 被 {@code PROMISE_HAS_PROCESSING_TEMPLATE}（存在进行中的生活记录）拒绝时，
     * 用它取回那条记录的 {@code recordId} 继续推完。
     * <p>抓包实测请求体为 {@code [{version:"new"}]}；响应 {@code data.lastOperateTaskVO} 含
     * {@code templateId / recordId / finishFlag / completedNum / needCompleteNum}。
     */
    public static String queryLastOperateTask() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryLastOperateTask", "[{\"version\":\"new\"}]");
    }

    /**
     * 另一种实现方案：按 bizKey 完成任务（{@code com.alipay.antfarm.doFarmTask}）。
     * <p>游戏中心任务与庄园抽抽乐、芭芭农场、金豆乐园同源：`doTaskSend` 常被服务端以
     * 400000040「不支持rpc调用」拒绝，而这条接口能把它们做成（2026-09-22 在三处实测通过）。
     * <p>它的响应**不可信**（可能回 102「服务器正在开小差」而任务其实已生效），
     * 调用方必须用任务列表状态核对，不能据响应判成败。version 经实测不被校验，这里沿用本模块原值。
     */
    public static String doFarmTask(String bizKey, String taskSceneCode) {
        return TaskAlternative.request(bizKey, taskSceneCode, DO_FARM_TASK_VERSION);
    }

    /**
     * 芝麻炼金 - 查询主页信息
     */
    public static String alchemyQueryHome() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.queryHome", "[{}]");
    }

    /**
     * 芝麻炼金 - 查询攒粒日常任务列表
     */
    public static String alchemyQueryTasks() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3",
                "[{\"chInfo\":\"\",\"deliverStatus\":\"\",\"deliveryTemplateId\":\"\",\"searchSubscribeTask\":true,\"version\":\"alchemy\"}]");
    }

    public static String joinPigeonAlchemyTask(String templateId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject("{}");
        args.put("chInfo", "seasameList").put("joinFromOuter", false).put("sceneCode", "alchemy").put("templateId", templateId);
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity", new JSONArray().put(args).toString());
    }

    public static String feedbackPigeonAlchemyTask(String templateId, String bizType) throws JSONException {
        JSONObject args = MyUtils.newJSONObject("{}");
        args.put("actionType", "TO_COMPLETE").put("bizType", bizType).put("sceneCode", "alchemy")
                .put("templateId", templateId).put("version", "alchemy");
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback", new JSONArray().put(args).toString());
    }

    /**
     * 芝麻炼金 - 查询签到任务状态
     */
    public static String alchemyQueryCheckInTasks() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.queryTaskLists",
                "[{\"sceneCode\":\"alchemy\",\"version\":\"2025-10-22\"}]");
    }

    /**
     * 芝麻炼金 - 完成签到任务
     * @param checkInDate YYYYMMDD格式的日期字符串
     */
    public static String completeAlchemyCheckIn(String checkInDate) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.CheckInTaskRpcManager.completeTask",
                "[{\"checkInDate\":\"" + checkInDate + "\",\"sceneCode\":\"alchemy\"}]");
    }

    /**
     * 芝麻炼金 - 领取奖励 (用于领取次日礼包)
     */
    public static String alchemyClaimAward() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.claimAward", "[{}]");
    }

    /**
     * 芝麻炼金 - 查询限时任务(早/中/晚饭)
     */
    public static String alchemyQueryTimeLimitedTask() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.queryTask", "[{}]");
    }

    /**
     * 芝麻炼金 - 完成限时任务(早/中/晚饭)
     * @param templateId 任务模板ID
     */
    public static String alchemyCompleteTimeLimitedTask(String templateId) {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.pointtask.TimeLimitedTaskRpcManager.completeTask",
                "[{\"templateId\":\"" + templateId + "\"}]");
    }

    /**
     * 芝麻炼金 - 执行炼金动作
     */
    public static String doAlchemy() {
        return ApplicationHook.requestString("com.antgroup.zmxy.zmmemberop.biz.rpc.AlchemyRpcManager.alchemy", "[null]");
    }

    /**
     * 芝麻树通用触发器
     * @param operation 操作类型
     * @param extInfoJson 额外信息JSON字符串
     * @return RPC响应
     */
    private static String sesameTreeTrigger(String operation, String extInfoJson) {
        String playInfo = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3";
        String refer = "https://render.alipay.com/p/yuyan/180020010001269849/zmTree.html?caprMode=sync&chInfo=chInfo=ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String requestData = String.format("[{\"operation\":\"%s\",\"playInfo\":\"%s\",\"refer\":\"%s\",\"extInfo\":%s}]",
                operation, playInfo, refer, extInfoJson);
        return ApplicationHook.requestString("alipay.promoprod.play.trigger", requestData);
    }

    /**
     * 获取芝麻树主页信息
     * @return RPC响应
     */
    public static String getSesameTreeHomePage() {
        return sesameTreeTrigger("ZHIMA_TREE_HOME_PAGE", "{}");
    }

    /**
     * 获取芝麻树任务列表
     * @return RPC响应
     */
    public static String getSesameTreeTaskList() {
        String extInfo = "{\"batchId\":\"\",\"chInfo\":\"ch_zmzltf__chsub_xinyongsyyingxiaowei\"}";
        return sesameTreeTrigger("RENT_GREEN_TASK_LIST_QUERY", extInfo);
    }

    /**
     * 净化芝麻树（通过点击按钮，消耗净化次数）
     * @return RPC响应
     */
    public static String cleanSesameTreeByClick() {
        String extInfo = "{\"clickNum\":\"1\",\"treeCode\":\"ZHIMA_TREE\"}";
        return sesameTreeTrigger("ZHIMA_TREE_CLEAN_AND_PUSH", extInfo);
    }

    /**
     * 完成芝麻树任务
     * @param taskId 任务ID
     * @return RPC响应
     */
    public static String finishSesameTreeTask(String taskId) {
        String chInfo = "ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String extInfo = String.format(
                "{\"chInfo\":\"%s\",\"stageCode\":\"send\",\"taskId\":\"%s\"}",
                chInfo, taskId);
        return sesameTreeTrigger("RENT_GREEN_TASK_FINISH", extInfo);
    }

    /**
     * 领取芝麻树任务奖励
     * @param taskId 任务ID
     * @return RPC响应
     */
    public static String receiveSesameTreeTaskReward(String taskId) {
        String chInfo = "ch_zmzltf__chsub_xinyongsyyingxiaowei";
        String extInfo = String.format(
                "{\"chInfo\":\"%s\",\"stageCode\":\"receive\",\"taskId\":\"%s\"}",
                chInfo, taskId);
        return sesameTreeTrigger("RENT_GREEN_TASK_FINISH", extInfo);
    }

    // ================= 保障金相关RPC方法 =================

    /**
     * 获取所有可领取的保障金
     */
    private static JSONArray insuredGoldRights() {
        return new JSONArray().put("UNIVERSAL_ACCIDENT").put("UNIVERSAL_HOSPITAL").put("UNIVERSAL_OUTPATIENT")
                .put("UNIVERSAL_SERIOUSNESS").put("UNIVERSAL_WEALTH").put("UNIVERSAL_TRANS").put("UNIVERSAL_FRAUD_LIABILITY");
    }

    private static JSONObject insuredGoldWaitParams(String product) throws JSONException {
        return MyUtils.newJSONObject().put("giftProdCode", product).put("rightNoList", insuredGoldRights());
    }

    public static String queryAvailableCollectInsuredGold(String entrance) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("entrance", entrance)
                .put("eventToWaitParamDTO", insuredGoldWaitParams("GIFT_UNIVERSAL_COVERAGE"))
                .put("helpChildParamDTO", insuredGoldWaitParams("GIFT_HEALTH_GOLD_CHILD"))
                .put("priorityChannelParamDTO", insuredGoldWaitParams("GIFT_UNIVERSAL_COVERAGE"))
                .put("signInParamDTO", insuredGoldWaitParams("GIFT_UNIVERSAL_COVERAGE"));
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.queryMultiSceneWaitToGainList", new JSONArray().put(args).toString());
    }

    public static String collectInsuredGold(JSONObject goldBallObj) {
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.gainMyAndFamilySumInsured", new JSONArray().put(goldBallObj).toString());
    }

    public static String queryInsuredOpenAndAllowAndUpgrade(String entrance) throws JSONException {
        JSONObject args = insuredGoldWaitParams("GIFT_UNIVERSAL_COVERAGE").put("entrance", entrance)
                .put("pageRenderRequest", MyUtils.newJSONObject().put("channelType", entrance).put("contentKey", "couponId")
                        .put("sceneCode", "INSGIFT_APP").put("templateCode", "INSGIFT_APP_NEW_OPEN"));
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.queryOpenAndAllowAndUpgrade", new JSONArray().put(args).toString());
    }

    public static String queryInsuredOpenAndAllow(String entrance) throws JSONException {
        JSONObject args = insuredGoldWaitParams("GIFT_UNIVERSAL_COVERAGE").put("entrance", entrance);
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.queryOpenAndAllow", new JSONArray().put(args).toString());
    }

    public static String queryInsuredGiftHomeRender(String entrance) throws JSONException {
        JSONObject options = MyUtils.newJSONObject().put("channelType", entrance).put("greatPromoPrefetchRPCFlag", true);
        JSONObject args = MyUtils.newJSONObject()
                .put("configPageRenderParam", MyUtils.newJSONObject().put("pageOptions", options).put("sceneCode", "INSGIFT_APP_CONFIG"))
                .put("pageRenderParam", MyUtils.newJSONObject().put("pageOptions", options).put("sceneCode", "INSGIFT_APP"))
                .put("trackCardParam", MyUtils.newJSONObject().put("pageOptions", options))
                .put("vicePageRenderParam", MyUtils.newJSONObject().put("pageOptions", options).put("sceneCode", "INSGIFT_APP_VICE"))
                .put("voucherQuery", MyUtils.newJSONObject().put("entrance", entrance).put("mktPrizeType", "VOUCHER_QUERY").put("voucherQueryDTO", MyUtils.newJSONObject()));
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftMain.giftHomeRender", new JSONArray().put(args).toString());
    }

    public static String queryInsuredTaskList(String center, String scene, String control) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("bizData", MyUtils.newJSONObject()).put("entrance", "cfsy").put("sceneCode", scene).put("taskCenterId", center);
        if (!control.isEmpty()) args.put("controlSolutionSceneCode", control).put("displayTaskCount", 30);
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftTask.queryTaskListv2", new JSONArray().put(args).toString());
    }

    public static String triggerInsuredTask(String applet, String center, String scene, String stage) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("appletId", applet).put("taskCenId", center).put("sceneCode", scene).put("stageCode", stage);
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftTask.taskTriggerv2", new JSONArray().put(args).toString());
    }

    public static String consultInsuredTask(String center, String task) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("taskCenterId", center).put("taskId", task);
        return ApplicationHook.requestString("com.alipay.insgiftbff.insgiftTask.taskCenterConsultById", new JSONArray().put(args).toString());
    }

    // ================= 安心豆相关RPC方法 =================

    /**
     * 安心豆签到查询
     */
    public static String querySignInProcess(String appletId, String scene) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("appletId", appletId).put("scene", scene)
                .put("bizData", MyUtils.newJSONObject().put("checkMultiAccountFrequency", "true"));
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.querySignInProcess", new JSONArray().put(args).toString());
    }

    /**
     * 安心豆签到触发
     */
    public static String signInTrigger(String appletId, String scene) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("appletId", appletId).put("scene", scene);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.signInTrigger", new JSONArray().put(args).toString());
    }

    public static String queryGuardianGradeAwards() throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("entrance", "insplatform_mine_anxindou").put("queryAwardStatus", true).put("sceneCode", "POSITION");
        return ApplicationHook.requestString("com.alipay.insmarketingbff.guardian.queryGradeAwards", new JSONArray().put(args).toString());
    }

    public static String guardianAwardSend(String skuId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("entrance", "insplatform_mine_anxindou").put("sceneCode", "POSITION").put("skuId", skuId);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.guardian.awardSend", new JSONArray().put(args).toString());
    }

    private static JSONObject beanPositionFactors() throws JSONException {
        return MyUtils.newJSONObject().put("entrance", "insplatform_mine_anxindou");
    }

    public static String filterValidBizProperty() throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("userAccountFilter", false);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.filterValidBizProperty", new JSONArray().put(args).toString());
    }

    public static String rightsRecommend(int offset, String category) throws JSONException {
        boolean first = offset == 0 && category.isEmpty();
        JSONObject args = MyUtils.newJSONObject().put("bizScene", "BLUE_BEAN_POSITION").put("factors", beanPositionFactors())
                .put("pageSize", 6).put("pageStartIndex", offset).put("riskScore", 0).put("strategyId", "feeds1209")
                .put("userAccountConsult", first ? 1 : 0).put("userAccountFilter", first ? 1 : 0);
        if (!category.isEmpty()) args.put("bizProperty", category);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.rightsRecommend", new JSONArray().put(args).toString());
    }

    public static String queryRightsPreExchangeFlows(int offset) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("bizScene", "BLUE_BEAN_POSITION").put("factors", beanPositionFactors()).put("pageSize", 99).put("pageStartIndex", offset);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.queryRightsPreExchangeFlows", new JSONArray().put(args).toString());
    }

    public static String queryRightsExchangeFlows(int offset) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("exchangeType", "ONLY_BLUE_BEAN").put("pageSize", 20).put("pageStartIndex", offset);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.queryRightsExchangeFlows", new JSONArray().put(args).toString());
    }

    public static String queryRightsDetail(String id) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("factors", beanPositionFactors()).put("rightsCode", id);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.queryRightsDetail", new JSONArray().put(args).toString());
    }

    public static String rightsExchange(String id, int amount) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("assetAmount", amount).put("bizScene", "BLUE_BEAN_POSITION")
                .put("factors", beanPositionFactors()).put("needOrder", 0).put("rightsId", id);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.rightsExchange", new JSONArray().put(args).toString(), 1, 0);
    }

    public static String guardianAnswerConsult() throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("consultScene", "ANXINDOU");
        return ApplicationHook.requestString("com.alipay.insmarketingbff.guardian.answerConsult", new JSONArray().put(args).toString());
    }

    public static String queryUserQuestionDrama(String userId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("channel", "ANXINDOU").put("userId", userId);
        return ApplicationHook.requestString("com.alipay.inscontentplatform.question.queryUserQuestionDrama", new JSONArray().put(args).toString());
    }

    public static String addAskAnswerRecord(String askAnswerId, String userId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("answerResult", "rightAnswer").put("askAnswerId", askAnswerId).put("userId", userId);
        return ApplicationHook.requestString("com.alipay.mfinsnsprod.biz.service.gw.qa.api.AskAnswerGwManager.addAskAnswerRecord", new JSONArray().put(args).toString());
    }

    public static String answerQuestionDrama(String dramaId, String scriptId, String userDramaId, String userId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("answerResult", "SUCCESS").put("channel", "ANXINDOU")
                .put("dramaId", dramaId).put("scriptId", scriptId).put("userDramaId", userDramaId).put("userId", userId);
        return ApplicationHook.requestString("com.alipay.inscontentplatform.question.answerQuestionDrama", new JSONArray().put(args).toString());
    }

    public static String beanCampConsult(String planId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("planId", planId);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.campConsult", new JSONArray().put(args).toString());
    }

    public static String beanTriggerDrawPrize(String campId, String planId, long lastDrawTime) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("campId", campId).put("planId", planId).put("lastDrawTime", lastDrawTime);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.triggerDrawPrize", new JSONArray().put(args).toString());
    }

    public static String queryAccountSummaryPoint() throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("bizScene", "POSITION").put("entrance", "insplatform_mine_anxindou");
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.queryAccountSummaryPoint", new JSONArray().put(args).toString());
    }

    public static String beanTaskCenterConsult() throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("bizData", MyUtils.newJSONObject()).put("bizTaskSortParams", MyUtils.newJSONObject())
                .put("displayTaskCount", 30).put("entrance", "insplatform_mine_anxindou").put("sceneCode", "AXD_TAK_LIST").put("taskCenterId", "AP15241780");
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.taskCenterConsult", new JSONArray().put(args).toString());
    }

    public static String beanTaskTrigger(String applet, String stage) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("appletId", applet).put("sceneCode", "AXD_TAK_LIST").put("taskCenId", "AP15241780").put("stageCode", stage);
        return ApplicationHook.requestString("com.alipay.insmarketingbff.bean.taskTrigger", new JSONArray().put(args).toString());
    }

    /**
     * 安心豆兑换详情查询
     */
    public static String beanExchangeDetail(String itemId) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\"}," +
                        "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchangeDetail\"}]");
    }

    /**
     * 安心豆兑换
     */
    public static String beanExchange(String itemId, int pointAmount) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.onestop.planTrigger",
                "[{\"extParams\":{\"itemId\":\"" + itemId + "\",\"pointAmount\":\"" + Integer.toString(pointAmount) + "\"}," +
                        "\"planCode\":\"bluebean_onestop\",\"planOperateCode\":\"exchange\"}]");
    }

    /**
     * 查询用户账户信息
     */
    public static String queryUserAccountInfo(String pointProdCode) {
        return ApplicationHook.requestString("com.alipay.insmarketingbff.point.queryUserAccountInfo",
                "[{\"channel\":\"HiChat\",\"pointProdCode\":\"" + pointProdCode + "\",\"pointUnitType\":\"COUNT\"}]");
    }

    // ================= 年度回顾相关RPC方法 =================

    public static final String ANNUAL_REVIEW_OPERATION_IDENTIFY =
            "independent_component_program2025111803036407";
    public static final String ANNUAL_REVIEW_COMPONENT_PREFIX =
            "independent_component_task_reward_v2_02888775";
    public static final String ANNUAL_REVIEW_QUERY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_query";
    public static final String ANNUAL_REVIEW_APPLY_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_apply";
    public static final String ANNUAL_REVIEW_PROCESS_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_process";
    public static final String ANNUAL_REVIEW_GET_REWARD_COMPONENT =
            ANNUAL_REVIEW_COMPONENT_PREFIX + "_independent_component_task_reward_get_reward";

    /**
     * 年度回顾 - 查询任务列表
     */
    public static String annualReviewQueryTasks() {
        try {
            JSONObject body = MyUtils.newJSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_QUERY_COMPONENT);

            JSONObject components = MyUtils.newJSONObject();
            components.put(ANNUAL_REVIEW_QUERY_COMPONENT, MyUtils.newJSONObject());
            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取单个任务（apply）
     */
    public static String annualReviewApplyTask(String code) {
        try {
            JSONObject body = MyUtils.newJSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_APPLY_COMPONENT);

            JSONObject compBody = MyUtils.newJSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = MyUtils.newJSONObject();
            components.put(ANNUAL_REVIEW_APPLY_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 提交任务完成（process）
     */
    public static String annualReviewProcessTask(String code, String recordNo) {
        try {
            JSONObject body = MyUtils.newJSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_PROCESS_COMPONENT);

            JSONObject compBody = MyUtils.newJSONObject();
            compBody.put("code", code);
            compBody.put("recordNo", recordNo);

            JSONObject components = MyUtils.newJSONObject();
            components.put(ANNUAL_REVIEW_PROCESS_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 年度回顾 - 领取奖励（get_reward）
     */
    public static String annualReviewGetReward(String code, String recordNo) {
        try {
            JSONObject body = MyUtils.newJSONObject();
            body.put("channel", "share");
            body.put("cityCode", "110000");
            body.put("operationParamIdentify", ANNUAL_REVIEW_OPERATION_IDENTIFY);
            body.put("source", ANNUAL_REVIEW_GET_REWARD_COMPONENT);

            JSONObject compBody = MyUtils.newJSONObject();
            compBody.put("code", code);
            compBody.put("consultAfterLuckDraw", "false");
            compBody.put("recordNo", recordNo);
            compBody.put("skipLuckDrawConsult", "true");

            JSONObject components = MyUtils.newJSONObject();
            components.put(ANNUAL_REVIEW_GET_REWARD_COMPONENT, compBody);

            body.put("components", components);

            return ApplicationHook.requestString(
                    "alipay.imasp.program.programInvoke",
                    new JSONArray().put(body).toString()
            );
        } catch (Throwable e) {
            return null;
        }
    }
}
