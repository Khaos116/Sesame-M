package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;

import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAntMemberTaskList;
import io.github.aw1y2z.sesame.entity.AlipayMemberCreditSesameTaskList;
import io.github.aw1y2z.sesame.entity.MemberBenefit;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle;
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm;
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MemberBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AntMember extends ModelTask {
    private static final String TAG = AntMember.class.getSimpleName();

    /**
     * `doFarmTask` 已发出、但响应不足以判定成败的游戏中心任务：{@code taskId -> 任务标题}。
     * <p>由 {@link #verifyPendingTasks()} 在列表处理完后按任务列表状态核对。
     */
    private final Map<String, String> pendingVerifyTasks = new LinkedHashMap<>();

    /** 同轮核对配置（见 TaskAlternative.verify） */
    private static final TaskAlternative.VerifyConfig VERIFY_CFG = new TaskAlternative.VerifyConfig(
            "AntMember", "AntMemberTaskList", "会员任务", "游戏中心", "🎮完成", true, msg -> Log.other(msg));
    
    @Override
    public String getName() {
        return "会员";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.MEMBER;
    }
    
    private BooleanModelField AntMemberTask;
    private BooleanModelField AutoAntMemberTaskList;
    private SelectModelField AntMemberTaskList;
    private BooleanModelField memberSign;
    private BooleanModelField memberPointExchangeBenefit;
    private SelectModelField memberPointExchangeBenefitList;
    private BooleanModelField memberPointExchangeSecKill;
    private StringModelField memberPointExchangeSecKillTimes;
    private IntegerModelField memberPointExchangeSecKillBudget;
    private String memberExchangeScheduledId;
    private final Object benefitExchangeLock = new Object();
    private StringModelField memberBenefitSearchKeywords;
    
    private BooleanModelField collectSesame;
    private BooleanModelField AutoMemberCreditSesameTaskList;
    private SelectModelField MemberCreditSesameTaskList;
    private BooleanModelField SesameGrowthBehavior;
    private BooleanModelField promise;
    private SelectModelField promiseList;
    private BooleanModelField enableGameCenter;
    private BooleanModelField enableGoldTicket;
    private BooleanModelField enableGoldTicketConsume;
    private BooleanModelField KuaiDiFuLiJia;
    private BooleanModelField collectStickers;
    private BooleanModelField billBlockWorld;
    private BooleanModelField merchantSign;
    private BooleanModelField sesameAchievements;
    private BooleanModelField collectInsuredGold;
    private BooleanModelField beanSignIn;
    private BooleanModelField beanBrowseTasks;
    private BooleanModelField beanDrawPrize;
    private BooleanModelField beanGuardianQuiz;
    private BooleanModelField beanExchangeRight;
    private SelectModelField beanExchangeRightList;
    private IntegerModelField beanExchangeRightBudget;
    private BooleanModelField yebExpGoldSign, yebExpGoldRewards;
    private BooleanModelField yebExpGoldTasks;
    private SelectModelField yebExpGoldTaskList;
    private IntegerModelField yebExpGoldTaskBudget;
    private BooleanModelField yebVoucherConvertAll, yebTrialActivateAllOwned, yebTrialExchangeActivate;
    private IntegerModelField yebVoucherDailyBudget, yebTrialExchangeBudget, yebTrialActivationBudget;
    private SelectModelField yebTrialExchangeCampaigns;
    private StringModelField yebTrialActivationIds;
    private BooleanModelField sesameGrainExchange;
    private SelectModelField sesameGrainExchangeList;
    private IntegerModelField sesameGrainExchangeBudget;
    private BooleanModelField merchantKmdk;
    private BooleanModelField merchantMoreTask;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(AntMemberTask = new BooleanModelField("AntMemberTask", "会员任务", false));
        modelFields.addField(AutoAntMemberTaskList = new BooleanModelField("AutoAntMemberTaskList", "会员任务 | 自动黑名单", true).setDependsOn("AntMemberTask"));
        modelFields.addField(AntMemberTaskList = new SelectModelField("AntMemberTaskList", "会员任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntMemberTaskList::getList).setDependsOn("AutoAntMemberTaskList"));
        modelFields.addField(memberSign = new BooleanModelField("memberSign", "会员签到", false));
        modelFields.addField(memberPointExchangeBenefit = new BooleanModelField("memberPointExchangeBenefit", "会员积分 | 兑换权益", false));
        modelFields.addField(memberPointExchangeBenefitList = new SelectModelField("memberPointExchangeBenefitList", "会员积分 | 权益列表", new LinkedHashSet<>(), MemberBenefit::getList).setDependsOn("memberPointExchangeBenefit"));
        modelFields.addField(memberPointExchangeSecKill = new BooleanModelField("memberPointExchangeSecKill", "会员积分 | 整点抢兑", false).setDependsOn("memberPointExchangeBenefit"));
        modelFields.addField(memberPointExchangeSecKillTimes = new StringModelField("memberPointExchangeSecKillTimes", "会员积分 | 抢兑时间(HH:mm逗号分隔)", "10:00,20:00").setDependsOn("memberPointExchangeSecKill"));
        modelFields.addField(memberPointExchangeSecKillBudget = new IntegerModelField("memberPointExchangeSecKillBudget", "会员积分 | 抢兑每日积分预算(0不兑换)", 0, 0, 1000000).setDependsOn("memberPointExchangeSecKill"));
        modelFields.addField(memberBenefitSearchKeywords = new StringModelField("memberBenefitSearchKeywords", "会员积分 | 搜索补充关键词(逗号分隔)", "").setDescription("只补充权益目录，兑换仍需勾选商品；每轮最多10个关键词，每词首页20项"));
        modelFields.addField(collectSesame = new BooleanModelField("collectSesame", "芝麻粒 | 领取", false));
        modelFields.addField(AutoMemberCreditSesameTaskList = new BooleanModelField("AutoMemberCreditSesameTaskList", "芝麻粒任务 | 自动黑名单", true).setDependsOn("collectSesame"));
        modelFields.addField(MemberCreditSesameTaskList = new SelectModelField("MemberCreditSesameTaskList", "芝麻粒任务 | 黑名单列表", new LinkedHashSet<>(), AlipayMemberCreditSesameTaskList::getList).setDependsOn("AutoMemberCreditSesameTaskList"));
        modelFields.addField(SesameGrowthBehavior = new BooleanModelField("SesameGrowthBehavior", "攒芝麻分进度", false));
        modelFields.addField(enableGameCenter = new BooleanModelField("enableGameCenter", "游戏中心 | 得乐园豆", false));
        //modelFields.addField(promise = new BooleanModelField("promise", "生活记录 | 坚持做", false));
        //modelFields.addField(promiseList = new SelectModelField("promiseList", "生活记录 | 坚持做列表", new LinkedHashSet<>(), PromiseSimpleTemplate::getList));
        modelFields.addField(KuaiDiFuLiJia = new BooleanModelField("KuaiDiFuLiJia", "我的快递 | 福利加", false));
        modelFields.addField(enableGoldTicket = new BooleanModelField("enableGoldTicket", "黄金票 | 签到与收取", false));
        modelFields.addField(enableGoldTicketConsume = new BooleanModelField("enableGoldTicketConsume", "黄金票 | 提取/兑换黄金", false));
        modelFields.addField(collectStickers = new BooleanModelField("CollectStickers", "账单贴纸 | 领取升级与奖励", false));
        modelFields.addField(billBlockWorld = new BooleanModelField("billBlockWorld", "账单积木世界 | 免费积木与章节奖励", false));
        modelFields.addField(merchantSign = new BooleanModelField("merchantSign", "商家服务 | 签到", false));
        modelFields.addField(sesameAchievements = new BooleanModelField("sesameAchievements", "芝麻成就馆 | 领取已达成勋章", false));
        modelFields.addField(collectInsuredGold = new BooleanModelField("collectInsuredGold", "蚂蚁保 | 保障金领取与浏览任务", false));
        modelFields.addField(beanSignIn = new BooleanModelField("beanSignIn", "安心豆 | 签到与等级奖励", false));
        modelFields.addField(beanBrowseTasks = new BooleanModelField("beanBrowseTasks", "安心豆 | 浏览任务", false));
        modelFields.addField(beanDrawPrize = new BooleanModelField("beanDrawPrize", "安心豆 | 抽奖（1豆/天）", false));
        modelFields.addField(beanGuardianQuiz = new BooleanModelField("beanGuardianQuiz", "安心豆 | 保险知识闯关", false));
        modelFields.addField(beanExchangeRight = new BooleanModelField("beanExchangeRight", "安心豆 | 权益目录与兑换", false));
        modelFields.addField(beanExchangeRightList = new SelectModelField("beanExchangeRightList", "安心豆 | 兑换列表", new LinkedHashSet<>(), io.github.aw1y2z.sesame.entity.BeanRight::getList).setDependsOn("beanExchangeRight"));
        modelFields.addField(beanExchangeRightBudget = new IntegerModelField("beanExchangeRightBudget", "安心豆 | 每日兑换预算（0仅刷新目录）", 0, 0, 1000000).setDependsOn("beanExchangeRight"));
        beanDrawPrize.setDescription("默认关闭；每天最多尝试一次，按来源协议消耗1豆，余额扣减异常或结果未知时当天不重试；奖励金额为接口返回，实际到账以支付宝为准。");
        modelFields.addField(yebExpGoldSign = new BooleanModelField("yebExpGoldSign", "余额宝体验金 | 签到", false));
        modelFields.addField(yebExpGoldRewards = new BooleanModelField("yebExpGoldRewards", "余额宝体验金 | 领取已完成任务奖励", false));
        modelFields.addField(yebExpGoldTasks = new BooleanModelField("yebExpGoldTasks", "余额宝体验金 | 任务目录与指定任务", false));
        modelFields.addField(yebExpGoldTaskList = new SelectModelField("yebExpGoldTaskList", "余额宝体验金 | 任务列表", new LinkedHashSet<>(), io.github.aw1y2z.sesame.entity.YebTask::getList).setDependsOn("yebExpGoldTasks"));
        modelFields.addField(yebExpGoldTaskBudget = new IntegerModelField("yebExpGoldTaskBudget", "余额宝体验金 | 每日任务尝试上限（0仅刷新目录）", 0, 0, 50).setDependsOn("yebExpGoldTasks"));
        yebExpGoldTasks.setDescription("只执行勾选且接口明确为浏览类型的任务，领取已完成奖励；类型未验证仅显示目录。结果未知不重试。");
        modelFields.addField(yebVoucherConvertAll = new BooleanModelField("yebVoucherConvertAll", "余额宝体验金 | 使用当前全部体验金券", false)
                .setDescription("来源接口只能整批使用，明确开启后仍需整批张数不超过日预算；不动现金余额，结果未知不重复。"));
        modelFields.addField(yebVoucherDailyBudget = new IntegerModelField("yebVoucherDailyBudget", "体验金券 | 每日使用张数预算（0不用）", 0, 0, 100).setDependsOn("yebVoucherConvertAll"));
        modelFields.addField(yebTrialExchangeCampaigns = new SelectModelField("yebTrialExchangeCampaigns", "余额宝体验金 | 允许兑换活动", new LinkedHashSet<>(), YebVouchers::getExchangeOptions));
        modelFields.addField(yebTrialExchangeBudget = new IntegerModelField("yebTrialExchangeBudget", "余额宝体验金 | 每日兑换额度预算（0不兑）", 0, 0, 1000000));
        modelFields.addField(yebTrialActivationIds = new StringModelField("yebTrialActivationIds", "余额宝体验金 | 激活已有资产trialId（逗号分隔）", "")
                .setDescription("只接受当前账号实时资产目录中的指定ID；不会将新兑换equityNo猜作trialId。还需正数日激活预算。"));
        modelFields.addField(yebTrialActivateAllOwned = new BooleanModelField("yebTrialActivateAllOwned", "余额宝体验金 | 按预算激活全部已有资产", false)
                .setDescription("开启后从当前账号稳定资产目录逐个激活非A资产，替代手填ID；每天受激活张数预算限制，仍需同trialId状态A回查。"));
        modelFields.addField(yebTrialExchangeActivate = new BooleanModelField("yebTrialExchangeActivate", "余额宝体验金 | 直接激活本轮兑换券", false)
                .setDescription("仅本轮勾选活动兑换且余额回查成功后，用实际equityNo及equityType=voucher提交，不当作trialId。共用激活预算；受理不等于收益到账，缺终态回查时保留回执，跨日不重发。"));
        modelFields.addField(yebTrialActivationBudget = new IntegerModelField("yebTrialActivationBudget", "余额宝体验金 | 每日激活资产数（0不激活）", 0, 0, 100));
        modelFields.addField(sesameGrainExchange = new BooleanModelField("sesameGrainExchange", "芝麻粒 | 商品目录与兑换", false));
        modelFields.addField(sesameGrainExchangeList = new SelectModelField("sesameGrainExchangeList", "芝麻粒 | 兑换列表", new LinkedHashSet<>(), io.github.aw1y2z.sesame.entity.SesameGift::getList).setDependsOn("sesameGrainExchange"));
        modelFields.addField(sesameGrainExchangeBudget = new IntegerModelField("sesameGrainExchangeBudget", "芝麻粒 | 每日兑换预算（0仅刷新目录）", 0, 0, 1000000).setDependsOn("sesameGrainExchange"));
        modelFields.addField(merchantKmdk = new BooleanModelField("merchantKmdk", "商家服务 | 开门打卡", false));
        modelFields.addField(merchantMoreTask = new BooleanModelField("merchantMoreTask", "商家服务 | 积分任务", false));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️蚂蚁会员:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        try {
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initMember")) {
                initMemberTaskListMap(AutoAntMemberTaskList.getValue(), AutoMemberCreditSesameTaskList.getValue(), AntMemberTask.getValue(), collectSesame.getValue());
                Status.flagToday("BlackList::initMember");
            }
            
            if (memberSign.getValue()) {
                memberSign();
            }
            
            if (AntMemberTask.getValue()) {
                queryPointCert(1, 8);
                signPageTaskList();
                queryAllStatusTaskList();
            }
            
            memberPointExchangeBenefit();
            scheduleMemberExchange();
            if (collectStickers.getValue()) collectBillStickers();
            BillBlockWorld.run(billBlockWorld.getValue());
            MerchantService.run(merchantSign.getValue(), merchantKmdk.getValue(), merchantMoreTask.getValue(), AntMemberTaskList.getValue());
            if (sesameAchievements.getValue()) SesameAchievements.run();
            if (collectInsuredGold.getValue()) InsuredGold.run(AntMemberTaskList.getValue());
            if (beanSignIn.getValue()) BeanRewards.run();
            if (beanBrowseTasks.getValue()) BeanRewards.runBrowse(AntMemberTaskList.getValue());
            if (beanGuardianQuiz.getValue()) BeanRewards.runQuiz();
            if (beanDrawPrize.getValue()) BeanRewards.runDraw();
            if (beanExchangeRight.getValue()) BeanRewards.runExchange(beanExchangeRightList.getValue(), beanExchangeRightBudget.getValue());
            if (yebExpGoldTasks.getValue()) YebExpGold.runTasks(yebExpGoldTaskList.getValue(), AntMemberTaskList.getValue(), yebExpGoldTaskBudget.getValue());
            YebExpGold.run(yebExpGoldSign.getValue(), yebExpGoldRewards.getValue(), AntMemberTaskList.getValue());
            Set<String> trialIds = new LinkedHashSet<>(java.util.Arrays.asList(yebTrialActivationIds.getValue().trim().split("[,，\\s]+")));
            trialIds.remove("");
            YebVouchers.run(yebVoucherConvertAll.getValue(), yebVoucherDailyBudget.getValue(), yebTrialExchangeCampaigns.getValue(),
                    yebTrialExchangeBudget.getValue(), trialIds, yebTrialActivationBudget.getValue(),
                    yebTrialActivateAllOwned.getValue(), yebTrialExchangeActivate.getValue());
            if (sesameGrainExchange.getValue()) SesameGrainExchange.run(sesameGrainExchangeList.getValue(), sesameGrainExchangeBudget.getValue());
            if (collectSesame.getValue()) {
                CheckInTaskRpcManager();
                collectSesame();
            }
            
            //芝麻积攒进度
            if (SesameGrowthBehavior.getValue()) {
                handleGrowthGuideTasks();
                queryAndCollect();
            }
            // 我的快递任务
            if (KuaiDiFuLiJia.getValue()) {
                RecommendTask();
                OrdinaryTask();
            }
            if (enableGoldTicket.getValue() || enableGoldTicketConsume.getValue()) {
                goldTicket();
            }
            if (enableGameCenter.getValue()) {
                //检查并执行签到
                checkAndDoSignIn();
                //查询并处理任务列表
                queryAndProcessTaskList();
                //游戏任务列表（原先该方法没有任何调用点，游戏类任务从未执行过）
                queryTaskList();

                //查询玩乐豆小球列表，有则领取
                queryPointBallList();
                
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.printStackTrace(TAG, t);
        }
    }
    
    static JSONObject memberFeaturePayload(JSONObject root) {
        if (RpcRequestGuard.isFailure(root)) return null;
        if (root.has("errCode") && !"0".equals(root.optString("errCode"))) return null;
        String code = root.optString("resultCode");
        if (!code.isEmpty() && !"SUCCESS".equals(code) && !"100".equals(code) && !"200".equals(code)) return null;
        JSONObject data = root.optJSONObject("data");
        if (data == null) data = root;
        if (data.length() > 0 && RpcRequestGuard.isFailure(data)) return null;
        if (data.has("errCode") && !"0".equals(data.optString("errCode"))) return null;
        code = data.optString("resultCode");
        if (!code.isEmpty() && !"SUCCESS".equals(code) && !"100".equals(code) && !"200".equals(code)) return null;
        if (Boolean.TRUE.equals(root.opt("success")) || Boolean.TRUE.equals(data.opt("success"))
                || "SUCCESS".equals(root.optString("resultCode")) || "100".equals(root.optString("resultCode"))
                || (root.has("errCode") && "0".equals(root.optString("errCode")))) return data;
        return null;
    }

    static Map<String, JSONObject> memberRowsById(JSONArray rows, String key) {
        if (rows == null) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt(key) instanceof String) || row.optString(key).isEmpty()) return null;
            if (result.put(row.optString(key), row) != null) return null;
        }
        return result;
    }

    private boolean receiveBillStickers(String year, String month) throws JSONException {
        TimeUtil.sleep(0);
        JSONObject data = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerCanReceiveList(year, month)));
        JSONArray pages = data == null ? null : data.optJSONArray("canReceivePageList");
        if (pages == null) return false;
        JSONArray rows = new JSONArray();
        for (int i = 0; i < pages.length(); i++) {
            JSONObject page = pages.optJSONObject(i);
            JSONArray list = page == null ? null : page.optJSONArray("stickerCanReceiveList");
            if (list == null) return false;
            for (int j = 0; j < list.length(); j++) rows.put(list.opt(j));
        }
        Map<String, JSONObject> candidates = memberRowsById(rows, "id");
        if (candidates == null) return false;
        if (candidates.isEmpty()) return true;
        JSONArray ids = new JSONArray(), configs = new JSONArray();
        // ponytail: at most 50 free stickers per run; remaining inventory waits for the next run.
        for (Map.Entry<String, JSONObject> entry : candidates.entrySet()) {
            if (!(entry.getValue().opt("stickerConfigId") instanceof String) || entry.getValue().optString("stickerConfigId").isEmpty()) return false;
            if (Status.hasFlagToday("member::stickerReceive::" + entry.getKey())) return false;
            ids.put(entry.getKey()); configs.put(entry.getValue().optString("stickerConfigId"));
            if (ids.length() == 50) break;
        }
        for (int i = 0; i < ids.length(); i++) Status.flagToday("member::stickerReceive::" + ids.optString(i));
        TimeUtil.sleep(0);
        JSONObject received = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.receiveSticker(year, month, ids, configs)));
        JSONObject after = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerCanReceiveList(year, month)));
        JSONArray afterPages = after == null ? null : after.optJSONArray("canReceivePageList");
        if (received == null || afterPages == null) return false;
        Set<String> remaining = new HashSet<>();
        for (int i = 0; i < afterPages.length(); i++) {
            JSONObject page = afterPages.optJSONObject(i);
            Map<String, JSONObject> pending = memberRowsById(page == null ? null : page.optJSONArray("stickerCanReceiveList"), "id");
            if (pending == null) return false;
            remaining.addAll(pending.keySet());
        }
        for (int i = 0; i < ids.length(); i++) if (remaining.contains(ids.optString(i))) return false;
        Log.other("账单贴纸🎟️领取回查成功#" + ids.length() + "张");
        return remaining.isEmpty();
    }

    private boolean upgradeBillStickers(String year, String month, String day) throws JSONException {
        TimeUtil.sleep(0);
        JSONObject home = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerHomePage(year, month, day)));
        JSONObject common = home == null ? null : home.optJSONObject("commonStickerRes");
        Map<String, JSONObject> stickers = memberRowsById(common == null ? null : common.optJSONArray("stickerDetailList"), "stickerConfigId");
        if (stickers == null || stickers.size() > 100) return false;
        for (Map.Entry<String, JSONObject> entry : stickers.entrySet()) {
            TimeUtil.sleep(0);
            String id = entry.getKey(); JSONObject sticker = entry.getValue();
            if ("upgradable".equalsIgnoreCase(sticker.optString("status"))) {
                JSONObject current = sticker.optJSONObject("currentLevel"), target = sticker.optJSONObject("upgradableLevel");
                if (current == null || target == null || !(current.opt("levelCode") instanceof String) || !(target.opt("levelCode") instanceof String)) return false;
                String from = current.optString("levelCode"), to = target.optString("levelCode");
                if (from.isEmpty() || to.isEmpty() || from.equals(to)) return false;
                String flag = "member::stickerUpgrade::" + year + month + "::" + id + "::" + from;
                if (Status.hasFlagToday(flag)) return false;
                Status.flagToday(flag);
                JSONArray requests = new JSONArray().put(MyUtils.newJSONObject().put("year", year).put("month", month)
                        .put("stickerConfigId", id).put("currentLevelCode", from).put("upgradableLevelCode", to));
                JSONObject result = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.upgradeStickerBatch(requests)));
                JSONObject after = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerHomePage(year, month, day)));
                JSONObject afterCommon = after == null ? null : after.optJSONObject("commonStickerRes");
                Map<String, JSONObject> fresh = memberRowsById(afterCommon == null ? null : afterCommon.optJSONArray("stickerDetailList"), "stickerConfigId");
                JSONObject item = fresh == null ? null : fresh.get(id);
                JSONObject level = item == null ? null : item.optJSONObject("currentLevel");
                JSONArray failures = result == null ? null : result.optJSONArray("failStickerCfgIdList");
                if (result == null || (failures != null && failures.length() > 0) || level == null || !to.equals(level.opt("levelCode"))) return false;
                sticker = item;
                Log.other("账单贴纸⬆️升级回查成功[" + sticker.optString("name", id) + "]");
            }
            if (!Boolean.TRUE.equals(sticker.opt("hasBenefit")) || "notReceived".equalsIgnoreCase(sticker.optString("status"))) continue;
            JSONObject detail = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerDetailPage(year, month, id)));
            Boolean receivable = billStickerBenefitState(detail);
            if (receivable == null) return false;
            if (!receivable) continue;
            String flag = "member::stickerBenefit::" + year + month + "::" + id;
            if (Status.hasFlagToday(flag)) return false;
            Status.flagToday(flag);
            JSONObject result = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.triggerStickerUpgradePrize(id)));
            Boolean pending = billStickerBenefitState(memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerDetailPage(year, month, id))));
            if (result == null || pending == null || pending) return false;
            Log.other("账单贴纸🎁升级权益回查成功[" + sticker.optString("name", id) + "]");
        }
        return true;
    }

    private static Boolean billStickerBenefitState(JSONObject detail) {
        JSONObject res = detail == null ? null : detail.optJSONObject("stickerDetailRes");
        JSONArray rows = res == null ? null : res.optJSONArray("stickerDetailList");
        if (rows == null || rows.length() == 0) return null;
        boolean pending = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i), benefit = row == null ? null : row.optJSONObject("upgradeBenefitModel");
            if (benefit == null || !(benefit.opt("status") instanceof String) || benefit.optString("status").isEmpty()) return null;
            pending |= "can_receive".equalsIgnoreCase(benefit.optString("status"));
        }
        return pending;
    }

    private boolean collectBillStickerPrizes() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject home = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerPrizeHomePage()));
        JSONArray ids = home == null ? null : home.optJSONArray("prizeConsumerIdList");
        if (ids == null || ids.length() > 100) return false;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object raw = ids.opt(i);
            if (!(raw instanceof String) || ((String) raw).isEmpty() || !unique.add((String) raw)) return false;
        }
        for (String id : unique) {
            TimeUtil.sleep(0);
            String flag = "member::stickerDrawing::" + id;
            if (Status.hasFlagToday(flag)) return false;
            Status.flagToday(flag);
            JSONObject drawn = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.triggerStickerDrawing(id)));
            JSONObject after = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryStickerPrizeHomePage()));
            JSONArray remaining = after == null ? null : after.optJSONArray("prizeConsumerIdList");
            if (drawn == null || remaining == null) return false;
            for (int i = 0; i < remaining.length(); i++) {
                if (!(remaining.opt(i) instanceof String) || id.equals(remaining.opt(i))) return false;
            }
            Log.other("账单贴纸🎁免费抽奖回查成功");
        }
        return true;
    }

    private void collectBillStickers() {
        if (!collectStickers.getValue() || Status.hasFlagToday("member::billStickersDone")) return;
        try {
            TimeUtil.sleep(0);
            java.util.Calendar date = MyUtils.getInstance();
            String year = Integer.toString(date.get(java.util.Calendar.YEAR));
            String month = String.format(java.util.Locale.ROOT, "%02d", date.get(java.util.Calendar.MONTH) + 1);
            String day = String.format(java.util.Locale.ROOT, "%02d", date.get(java.util.Calendar.DAY_OF_MONTH));
            if (receiveBillStickers(year, month) && upgradeBillStickers(year, month, day) && collectBillStickerPrizes()) Status.flagToday("member::billStickersDone");
            else Log.record("账单贴纸：未确认全部完成，保留后续查询；已提交项目当天不重复尝试");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "collectBillStickers", t); }
    }

    public static void initMemberTaskListMap(boolean AutoAntMemberTaskList, boolean AutoMemberCreditSesameTaskList, boolean AntMemberTask, boolean collectSesame) {
        try {
            //初始化AntMemberTaskListMap
            AntMemberTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            //blackList.add("去淘金币逛一逛");
            // 可继续添加更多黑名单任务
            
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntMemberTaskListMap.add(task, task);
            }
            
            JSONObject jo;
            if (AntMemberTask) {
                boolean hasNextPage = true;
                int page = 1;
                do {
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.queryPointCert(page, 8));
                    TimeUtil.sleep(500);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        break;
                    }
                    hasNextPage = jo.optBoolean("hasNextPage");
                    page++;
                    JSONArray jaCertList = jo.optJSONArray("certList");
                    for (int i = 0; jaCertList != null && i < jaCertList.length(); i++) {
                        jo = jaCertList.optJSONObject(i);
                        if (jo == null) {
                            continue;
                        }
                        String bizTitle = jo.optString("bizTitle");
                        AntMemberTaskListMap.add(bizTitle, bizTitle);
                    }
                }
                while (hasNextPage);
                
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryAllStatusTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray availableTaskList = jo.optJSONArray("availableTaskList");
                    for (int i = 0; availableTaskList != null && i < availableTaskList.length(); i++) {
                        JSONObject task = availableTaskList.optJSONObject(i);
                        JSONObject taskConfigInfo = task != null ? task.optJSONObject("taskConfigInfo") : null;
                        if (taskConfigInfo == null) {
                            continue;
                        }
                        String name = taskConfigInfo.optString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                    JSONArray taskHistoryList = jo.optJSONArray("taskHistoryList");
                    for (int i = 0; taskHistoryList != null && i < taskHistoryList.length(); i++) {
                        JSONObject task = taskHistoryList.optJSONObject(i);
                        JSONObject taskConfigInfo = task != null ? task.optJSONObject("taskConfigInfo") : null;
                        if (taskConfigInfo == null) {
                            continue;
                        }
                        String name = taskConfigInfo.optString("name");
                        AntMemberTaskListMap.add(name, name);
                    }
                }
                
                // 游戏任务列表（游戏中心）的任务也要进候选，否则用户看不到、也无法手动勾选
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryTaskList());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject gameData = jo.optJSONObject("data");
                    JSONObject gameTaskModule = gameData == null ? null : gameData.optJSONObject("gameTaskModule");
                    JSONArray gameTaskList = gameTaskModule == null ? null : gameTaskModule.optJSONArray("gameTaskList");
                    if (gameTaskList != null) {
                        for (int i = 0; i < gameTaskList.length(); i++) {
                            JSONObject gameTask = gameTaskList.optJSONObject(i);
                            String subTitle = gameTask == null ? "" : gameTask.optString("subTitle");
                            if (!subTitle.isEmpty()) {
                                AntMemberTaskListMap.add(subTitle, subTitle);
                            }
                        }
                    }
                }
                
                //保存任务到配置文件
                AntMemberTaskListMap.save();
                Log.record("同步任务🉑会员任务列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntMemberTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField AntMemberTaskList = (SelectModelField) antMember.get("AntMemberTaskList");
                    if (AntMemberTaskList == null) {
                        return;
                    }
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员任务", "AntMemberTaskList", blackList, whiteList, AntMemberTaskList);
                }
            }
            //初始化MemberCreditSesameTaskListMap
            MemberCreditSesameTaskListMap.load();
            blackList = new HashSet<>();
            // 实测（2026-09-22 抓包 logs/chk_sesame3）：芝麻粒任务走 taskFeedback 后服务端**不校验是否真的参与过**，
            // 未报名的「去玩xx」一次即 success ⇒ 游戏/浏览/签到/组件/施肥类不再预置拉黑，全部交给任务循环自动完成。
            // 仍预置拉黑的只剩**真实交易/履约类**（下单/租赁/订酒店/回收/雇佣/付钱/查车），
            // 这类没真做就申报"完成"属虚假履约，有风控风险
            blackList.add("用额度免押金下单");
            blackList.add("去租赁下单");
            blackList.add("芝麻租赁下单得芝麻粒");
            blackList.add("去飞猪订酒店");
            blackList.add("0.1元起租会员攒粒");
            blackList.add("9.9元抢租3天大疆");
            blackList.add("1分起囤神券茶咖美食");
            blackList.add("完成旧衣回收得现金");
            blackList.add("去雇佣芝麻大表鸽");
            blackList.add("送你10.6元支付红包");
            blackList.add("一键查询爱车估值");
            // 可继续添加更多黑名单任务
            
            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛一逛芝麻树");
            whiteList.add("浏览15秒视频广告");
            whiteList.add("逛15秒商品橱窗");
            whiteList.add("逛一逛集汗滴找现金");
            whiteList.add("去体验先用后付");
            whiteList.add("去抛竿钓鱼");
            whiteList.add("去参与花呗活动");
            whiteList.add("坚持攒保障金");
            whiteList.add("去领支付宝积分");
            whiteList.add("去浏览租赁大促会场");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                MemberCreditSesameTaskListMap.add(task, task);
            }
            
            if (collectSesame) {
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONObject entrance = jo.optJSONObject("entrance");
                    if (entrance != null && entrance.optBoolean("openApp")) {
                        jo = MyUtils.newJSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            if (jo.has("data")) {
                                JSONObject data = jo.optJSONObject("data");
                                if (data != null && data.has("completeVOS")) {
                                    JSONArray completeVOS = data.optJSONArray("completeVOS");
                                    for (int i = 0; completeVOS != null && i < completeVOS.length(); i++) {
                                        JSONObject toCompleteVO = completeVOS.optJSONObject(i);
                                        if (toCompleteVO == null) {
                                            continue;
                                        }
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                                if (data != null && data.has("toCompleteVOS")) {
                                    JSONArray toCompleteVOS = data.optJSONArray("toCompleteVOS");
                                    for (int i = 0; toCompleteVOS != null && i < toCompleteVOS.length(); i++) {
                                        JSONObject toCompleteVO = toCompleteVOS.optJSONObject(i);
                                        if (toCompleteVO == null) {
                                            continue;
                                        }
                                        String title = toCompleteVO.optString("title");
                                        if (title.isEmpty()) {
                                            continue;
                                        }
                                        MemberCreditSesameTaskListMap.add(title, title);
                                    }
                                }
                            }
                        }
                    }
                }
                //保存任务到配置文件
                MemberCreditSesameTaskListMap.save();
                Log.record("同步任务🉑会员芝麻信用任务芝麻粒列表");
                
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoMemberCreditSesameTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields antMember = config.getModelFieldsMap().get("AntMember");
                    SelectModelField MemberCreditSesameTaskList = (SelectModelField) antMember.get("MemberCreditSesameTaskList");
                    if (MemberCreditSesameTaskList == null) {
                        return;
                    }
                    
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("会员芝麻信用任务芝麻粒", "MemberCreditSesameTaskList", blackList, whiteList, MemberCreditSesameTaskList);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "initMemberTaskListMap err:", t);
        }
    }
    
    private void memberSign() {
        try {
            if (!Status.hasFlagToday("member::sign")) {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryMemberSigninCalendar());
                TimeUtil.sleep(500);
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    if (jo.optBoolean("autoSignInSuccess")) {
                        Log.other("会员任务📅签到[坚持" + jo.optString("signinSumDay") + "天]#获得[" + jo.optString("signinPoint") + "积分]");
                    }
                    Status.flagToday("member::sign");
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "memberSign err:", t);
        }
    }
    
    private void queryPointCert(int page, int pageSize) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryPointCert(page, pageSize));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryPointCert page=" + page + " 接口返回失败");
                return;
            }
            boolean hasNextPage = jo.optBoolean("hasNextPage");
            JSONArray jaCertList = jo.optJSONArray("certList");
            for (int i = 0; jaCertList != null && i < jaCertList.length(); i++) {
                jo = jaCertList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String bizTitle = jo.optString("bizTitle");
                //黑名单任务跳过
                if (AntMemberTaskList.getValue().contains(bizTitle)) {
                    continue;
                }
                String id = jo.optString("id");
                int pointAmount = jo.optInt("pointAmount");
                jo = MyUtils.newJSONObject(AntMemberRpcCall.receivePointByUser(id));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.other("会员任务🎖️领取[" + bizTitle + "]奖励#获得[" + pointAmount + "积分]");
                } else {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", bizTitle, jo);
                }
            }
            if (hasNextPage) {
                queryPointCert(page + 1, pageSize);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointCert err:", t);
        }
    }
    
    /**
     * 做任务赚积分
     */
    private void signPageTaskList() {
        try {
            do {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.signPageTaskList());
                TimeUtil.sleep(500);
                boolean doubleCheck = false;
                if (!MessageUtil.checkResultCode(TAG + " signPageTaskList", jo)) {
                    return;
                }
                if (!jo.has("categoryTaskList")) {
                    Log.i(TAG, "signPageTaskList 无 categoryTaskList 字段");
                    return;
                }
                JSONArray categoryTaskList = jo.optJSONArray("categoryTaskList");
                for (int i = 0; categoryTaskList != null && i < categoryTaskList.length(); i++) {
                    jo = categoryTaskList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    JSONArray taskList = jo.optJSONArray("taskList");
                    String type = jo.optString("type");
                    if (taskList == null) {
                        continue;
                    }
                    if (Objects.equals("BROWSE", type)) {
                        doubleCheck = doBrowseTask(taskList);
                    }
                    else {
                        ExtensionsHandle.handleAlphaRequest("antMember", "doMoreTask", jo);
                    }
                }
                if (doubleCheck) {
                    continue;
                }
                break;
            }
            while (true);
        }
        catch (Throwable t) {
            Log.err(TAG, "signPageTaskList err:", t);
        }
    }
    
    /**
     * 查询所有状态任务列表
     */
    private void queryAllStatusTaskList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryAllStatusTaskList());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.i(TAG, "queryAllStatusTaskList 接口返回失败");
                return;
            }
            JSONArray availableTaskList = jo.optJSONArray("availableTaskList");
            if (availableTaskList != null && doBrowseTask(availableTaskList)) {
                queryAllStatusTaskList();
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryAllStatusTaskList err:", t);
        }
    }
    
    // 生活记录
    private void promise() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseQueryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            JSONArray promiseSimpleTemplates = jo != null ? jo.optJSONArray("promiseSimpleTemplates") : null;
            for (int i = 0; promiseSimpleTemplates != null && i < promiseSimpleTemplates.length(); i++) {
                jo = promiseSimpleTemplates.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String templateId = jo.optString("templateId");
                String promiseName = jo.optString("promiseName");
                String status = jo.optString("status");
                if ("un_join".equals(status) && promiseList.getValue().contains(templateId)) {
                    promiseJoin(querySingleTemplate(templateId));
                }
                PromiseSimpleTemplateIdMap.add(templateId, promiseName);
            }
            PromiseSimpleTemplateIdMap.save(UserIdMap.getCurrentUid());
        }
        catch (Throwable t) {
            Log.err(TAG, "promise err:", t);
        }
    }
    
    private JSONObject querySingleTemplate(String templateId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.querySingleTemplate(templateId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            jo = jo.optJSONObject("data");
            if (jo == null) {
                return null;
            }
            JSONObject result = MyUtils.newJSONObject();

            result.put("joinFromOuter", false);
            result.put("templateId", jo.optString("templateId"));
            result.put("autoRenewStatus", Boolean.valueOf(jo.optString("autoRenewStatus")));

            JSONObject joinGuarantyRule = jo.optJSONObject("joinGuarantyRule");
            JSONArray joinGuarantyValues = joinGuarantyRule != null ? joinGuarantyRule.optJSONArray("canSelectValues") : null;
            if (joinGuarantyRule == null || joinGuarantyValues == null || joinGuarantyValues.length() == 0) {
                return null;
            }
            joinGuarantyRule.put("selectValue", joinGuarantyValues.optString(0));
            joinGuarantyRule.remove("canSelectValues");
            result.put("joinGuarantyRule", joinGuarantyRule);

            JSONObject joinRule = jo.optJSONObject("joinRule");
            JSONArray joinRuleValues = joinRule != null ? joinRule.optJSONArray("canSelectValues") : null;
            if (joinRule == null || joinRuleValues == null || joinRuleValues.length() == 0) {
                return null;
            }
            joinRule.put("selectValue", joinRuleValues.optString(0));
            joinRule.remove("joinRule");
            result.put("joinRule", joinRule);

            JSONObject periodTargetRule = jo.optJSONObject("periodTargetRule");
            JSONArray periodTargetValues = periodTargetRule != null ? periodTargetRule.optJSONArray("canSelectValues") : null;
            if (periodTargetRule == null || periodTargetValues == null || periodTargetValues.length() == 0) {
                return null;
            }
            periodTargetRule.put("selectValue", periodTargetValues.optString(0));
            periodTargetRule.remove("canSelectValues");
            result.put("periodTargetRule", periodTargetRule);

            JSONObject dataSourceRule = jo.optJSONObject("dataSourceRule");
            JSONArray dataSourceValues = dataSourceRule != null ? dataSourceRule.optJSONArray("canSelectValues") : null;
            JSONObject firstDataSource = dataSourceValues != null ? dataSourceValues.optJSONObject(0) : null;
            if (dataSourceRule == null || firstDataSource == null) {
                return null;
            }
            dataSourceRule.put("selectValue", firstDataSource.optString("merchantId"));
            dataSourceRule.remove("canSelectValues");
            result.put("dataSourceRule", dataSourceRule);
            return result;
        }
        catch (Throwable t) {
            Log.err(TAG, "querySingleTemplate err:", t);
        }
        return null;
    }
    
    private void promiseJoin(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseJoin(data));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            String promiseName = jo != null ? jo.optString("promiseName") : "";
            Log.other("生活记录📝加入[" + promiseName + "]");
        }
        catch (Throwable t) {
            Log.err(TAG, "promiseJoin err:", t);
        }
    }
    
    // 查询持续做明细任务
    private JSONObject promiseQueryDetail(String recordId) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.promiseQueryDetail(recordId));
        if (!jo.optBoolean("success")) {
            return null;
        }
        return jo;
    }
    
    // 蚂蚁积分-做浏览任务
    private Boolean doBrowseTask(JSONArray taskList) {
        boolean doubleCheck = false;
        try {
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                if (task.optBoolean("hybrid")) {
                    JSONObject extInfo = task.optJSONObject("extInfo");
                    if (extInfo == null) {
                        continue;
                    }
                    int periodCurrentCount = Integer.parseInt(extInfo.optString("PERIOD_CURRENT_COUNT", "0"));
                    int periodTargetCount = Integer.parseInt(extInfo.optString("PERIOD_TARGET_COUNT", "0"));
                    int count = periodTargetCount > periodCurrentCount ? periodTargetCount - periodCurrentCount : 0;
                    if (count > 0) {
                        doubleCheck = doubleCheck || doBrowseTask(task, periodTargetCount, periodTargetCount);
                    }
                }
                else {
                    doubleCheck = doubleCheck || doBrowseTask(task, 1, 1);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private Boolean doBrowseTask(JSONObject task, int left, int right) {
        boolean doubleCheck = false;
        try {
            JSONObject taskConfigInfo = task.optJSONObject("taskConfigInfo");
            if (taskConfigInfo == null) {
                return false;
            }
            String name = taskConfigInfo.optString("name");
            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(name)) {
                return false;
            }
            Long id = taskConfigInfo.optLong("id");
            JSONObject awardParam = taskConfigInfo.optJSONObject("awardParam");
            if (awardParam == null) {
                return false;
            }
            JSONArray targetBusinessArr = taskConfigInfo.optJSONArray("targetBusiness");
            if (targetBusinessArr == null || targetBusinessArr.length() == 0) {
                Log.i("会员任务⏭️跳过[" + name + "]#无 targetBusiness 配置");
                return false;
            }
            String awardParamPoint = awardParam.optString("awardParamPoint");
            String targetBusiness = targetBusinessArr.optString(0);
            for (int i = left; i <= right; i++) {
                JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.applyTask(name, id));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", name, jo);
                    continue;
                }
                String[] targetBusinessArray = targetBusiness.split("#");
                String bizParam;
                String bizSubType;
                if (targetBusinessArray.length > 2) {
                    bizParam = targetBusinessArray[2];
                    bizSubType = targetBusinessArray[1];
                }
                else {
                    bizParam = targetBusinessArray[1];
                    bizSubType = targetBusinessArray[0];
                }
                jo = MyUtils.newJSONObject(AntMemberRpcCall.executeTask(bizParam, bizSubType));
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", name, jo);
                    continue;
                }
                String ex = left == right && left == 1 ? "" : "(" + (i + 1) + "/" + right + ")";
                Log.other("会员任务🎖️完成[" + name + ex + "]#获得[" + awardParamPoint + "积分]");
                doubleCheck = true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doBrowseTask err:", t);
        }
        return doubleCheck;
    }
    
    private void goldTicket() {
        try {
            boolean doSignIn = enableGoldTicket.getValue();
            boolean doConsume = enableGoldTicketConsume.getValue();
            if (!doSignIn && !doConsume) {
                return;
            }
            boolean needSignIn = doSignIn && !Status.hasFlagToday("goldTicket::sign");
            boolean needHomeCheck = doSignIn && !Status.hasFlagToday("goldTicket::home");
            boolean needWelfare = doSignIn && !Status.hasFlagToday("goldTicket::welfare");
            boolean needConsume = doConsume && !Status.hasFlagToday("goldTicket::consume");
            if (!needSignIn && !needHomeCheck && !needWelfare && !needConsume) {
                Log.i("黄金票🙈[今日已处理，跳过]");
                return;
            }
            Log.i("黄金票🙈[开始执行]");
            JSONObject home = null;
            if (needSignIn || needHomeCheck) {
                home = queryGoldTicketHome();
            }
            if (needSignIn) {
                if (home == null) {
                    Log.error("黄金票🙈[首页查询失败]无法判断签到状态");
                } else if (doGoldTicketSignIn(home)) {
                    Status.flagToday("goldTicket::sign");
                }
            }
            if (needHomeCheck) {
                if (home == null) {
                    Log.error("黄金票🙈[首页查询失败]跳过收取与任务扫描");
                } else {
                    doGoldTicketCollect(home);
                    if (handleGoldTicketHomeTasks(home)) {
                        Status.flagToday("goldTicket::home");
                    }
                }
            }
            if (needWelfare) {
                if (handleGoldTicketWelfareTasks()) {
                    Status.flagToday("goldTicket::welfare");
                }
            }
            if (needConsume) {
                doGoldTicketConsume();
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 黄金票系接口只返回 success:true（无 desc / resultCode），而 checkResultCode 要求 desc="处理成功"，
     * 会把这些响应全部误判为失败；故这里以 success/isSuccess 为准，再回退通用判定。
     */
    private static boolean goldTicketOk(String tag, JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if (jo.optBoolean("success") || jo.optBoolean("isSuccess")) {
            return true;
        }
        return MessageUtil.checkResultCode(tag, jo);
    }

    private JSONObject queryGoldTicketHome() {
        try {
            String res = AntMemberRpcCall.queryGoldTicketHome();
            if (res == null || res.isEmpty()) {
                return null;
            }
            JSONObject jo = MyUtils.newJSONObject(res);
            if (!goldTicketOk(TAG, jo)) {
                return null;
            }
            return jo;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    private JSONObject getGoldTicketAssetInfo(JSONObject home) {
        if (home == null) {
            return null;
        }
        // 首页响应在 result.upsertData.assetInfo 下，旧的 result.assetInfo 作兜底
        JSONObject asset = home.optJSONObject("assetInfo");
        if (asset == null) {
            JSONObject result = home.optJSONObject("result");
            if (result != null) {
                JSONObject upsertData = result.optJSONObject("upsertData");
                if (upsertData != null) {
                    asset = upsertData.optJSONObject("assetInfo");
                }
                if (asset == null) {
                    asset = result.optJSONObject("assetInfo");
                }
            }
        }
        return asset;
    }

    private boolean doGoldTicketSignIn(JSONObject home) {
        try {
            JSONObject assetInfo = getGoldTicketAssetInfo(home);
            if (assetInfo == null || !(assetInfo.opt("canSign") instanceof Boolean)) return false;
            boolean canSign = assetInfo.optBoolean("canSign");
            if (!canSign) {
                Log.i("黄金票🙈[今日已签到]");
                return true;
            }
            Log.i("黄金票🙈[准备签到]");
            boolean signSuccess = false;
            int collectCount = doGoldTicketIndexCollect("签到尝试");
            JSONObject refreshed = queryGoldTicketHome();
            if (refreshed != null) {
                JSONObject ra = getGoldTicketAssetInfo(refreshed);
                if (ra != null && Boolean.FALSE.equals(ra.opt("canSign"))) {
                    refreshGoldTicketWelfareCenter("首页收取签到");
                    Log.other(collectCount > 0 ? "黄金票🙈[签到成功]#通过首页收取完成签到" : "黄金票🙈[签到成功]");
                    signSuccess = true;
                }
            }
            if (!signSuccess) {
                String signRes = AntMemberRpcCall.welfareCenterTrigger("SIGN");
                if (signRes != null && !signRes.isEmpty()) {
                    JSONObject signJson = MyUtils.newJSONObject(signRes);
                    if (goldTicketOk(TAG, signJson)) {
                        JSONObject signResult = signJson.optJSONObject("result");
                        String amount = "";
                        if (signResult != null) {
                            JSONObject prize = signResult.optJSONObject("prize");
                            if (prize != null) {
                                amount = prize.optString("amount");
                            }
                        }
                        refreshGoldTicketWelfareCenter("签到");
                        JSONObject refreshed2 = queryGoldTicketHome();
                        JSONObject ra2 = refreshed2 != null ? getGoldTicketAssetInfo(refreshed2) : null;
                        signSuccess = ra2 != null && Boolean.FALSE.equals(ra2.opt("canSign"));
                        if (signSuccess || (amount != null && !amount.isEmpty())) {
                            Log.other(amount != null && !amount.isEmpty()
                                    ? "黄金票🙈[签到成功]#获得[" + amount + "]" : "黄金票🙈[签到成功]");
                            signSuccess = true;
                        }
                    }
                }
            }
            if (!signSuccess) {
                Log.error("黄金票🙈[签到失败]未找到可用签到返回");
            }
            return signSuccess;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int doGoldTicketIndexCollect(String source) {
        String needleResponse = AntMemberRpcCall.goldTicketIndexCollect();
        if (needleResponse != null && !needleResponse.isEmpty()) {
            return logGoldTicketCollectResponse(needleResponse, source);
        }
        return logGoldTicketCollectResponse(AntMemberRpcCall.goldBillCollect(), source + "-旧版兼容");
    }

    private boolean refreshGoldTicketWelfareCenter(String source) {
        try {
            String updateResponse = AntMemberRpcCall.welfareCenterUpdate(9);
            if (updateResponse == null || updateResponse.isEmpty()) {
                Log.error("黄金票🙈[" + source + "]福利中心刷新无返回");
                return false;
            }
            JSONObject updateJson = MyUtils.newJSONObject(updateResponse);
            if (!goldTicketOk(TAG, updateJson)) {
                Log.error("黄金票🙈[" + source + "]福利中心刷新失败："
                        + updateJson.optString("resultDesc", updateJson.optString("memo")));
                return false;
            }
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int logGoldTicketCollectResponse(String response, String source) {
        if (response == null || response.isEmpty()) {
            return 0;
        }
        try {
            JSONObject collectJson = MyUtils.newJSONObject(response);
            if (!goldTicketOk(TAG, collectJson)) {
                String message = collectJson.optString("resultDesc", collectJson.optString("memo"));
                if (message != null && !message.isEmpty()) {
                    Log.other("黄金票🙈[" + source + "]" + message);
                }
                return 0;
            }
            JSONObject result = collectJson.optJSONObject("result");
            if (result == null) {
                return 0;
            }
            JSONArray collectedList = result.optJSONArray("collectedList");
            if (collectedList == null) {
                return 0;
            }
            int count = 0;
            for (int i = 0; i < collectedList.length(); i++) {
                String item = collectedList.optString(i);
                if (item == null || item.isEmpty()) {
                    continue;
                }
                count++;
                Log.other("黄金票🙈[" + source + "]#" + item);
            }
            if (count > 0) {
                JSONObject collectedCamp = result.optJSONObject("collectedCamp");
                String totalAmount = collectedCamp != null ? collectedCamp.optString("amount") : "";
                if (totalAmount != null && !totalAmount.isEmpty()) {
                    Log.other("黄金票🙈[" + source + "]#本次共得[" + totalAmount + "]份");
                }
            }
            return count;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return 0;
        }
    }

    private void doGoldTicketCollect(JSONObject home) {
        try {
            JSONObject assetInfo = getGoldTicketAssetInfo(home);
            JSONObject toBeCollectInfo = assetInfo != null ? assetInfo.optJSONObject("toBeCollectInfo") : null;
            int totalProfitValue = toBeCollectInfo != null ? toBeCollectInfo.optInt("totalProfitValue", 0) : 0;
            if (totalProfitValue <= 0) {
                return;
            }
            int collectCount = doGoldTicketIndexCollect("场景收取");
            if (collectCount == 0) {
                Log.i("黄金票🙈[场景收取]暂无可领取奖励");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean isGoldTicketRewardReady(String status) {
        return "TO_RECEIVE".equals(status) || "WAIT_RECEIVE".equals(status)
                || "FINISHED".equals(status) || "COMPLETE".equals(status);
    }

    private boolean isGoldTicketSignupRequired(String status) {
        return "NONE_SIGNUP".equals(status) || "SIGNUP_EXPIRED".equals(status);
    }

    private boolean isGoldTicketKnownAutoTask(JSONObject task) {
        String taskId = task.optString("taskId");
        return "AP10247402".equals(taskId) || "AP11249033".equals(taskId)
                || "AP13250426".equals(taskId) || "AP19360380".equals(taskId)
                || "AP15280470".equals(taskId) || "AP16338809".equals(taskId);
    }

    private JSONArray extractGoldTicketHomeTodoTasks(JSONObject home) {
        // 首页响应在 result.upsertData.task 下，旧的 result.task 作兜底
        JSONObject task = home.optJSONObject("task");
        if (task == null) {
            JSONObject result = home.optJSONObject("result");
            if (result != null) {
                JSONObject upsertData = result.optJSONObject("upsertData");
                if (upsertData != null) {
                    task = upsertData.optJSONObject("task");
                }
                if (task == null) {
                    task = result.optJSONObject("task");
                }
            }
        }
        if (task == null) {
            return null;
        }
        JSONObject tasks = task.optJSONObject("tasks");
        if (tasks == null) {
            return null;
        }
        JSONArray todo = tasks.optJSONArray("todo");
        return todo != null ? todo : (tasks.has("todo") ? null : new JSONArray());
    }

    private JSONArray queryGoldTicketWelfareTodoTasks() {
        try {
            String welfareResponse = AntMemberRpcCall.queryWelfareHome();
            if (welfareResponse == null || welfareResponse.isEmpty()) {
                return null;
            }
            JSONObject welfareJson = MyUtils.newJSONObject(welfareResponse);
            if (!goldTicketOk(TAG, welfareJson)) {
                return null;
            }
            JSONObject result = welfareJson.optJSONObject("result");
            if (result == null) {
                return null;
            }
            JSONObject goldbillTasks = result.optJSONObject("goldbillTasks");
            if (goldbillTasks == null) {
                return null;
            }
            JSONArray todo = goldbillTasks.optJSONArray("todo");
            return todo != null ? todo : (goldbillTasks.has("todo") ? null : new JSONArray());
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    private boolean pushGoldTicketTask(String taskId, String action) {
        try {
            String response = AntMemberRpcCall.taskQueryPush(taskId);
            if (response == null || response.isEmpty()) {
                return false;
            }
            JSONObject result = MyUtils.newJSONObject(response);
            if (!goldTicketOk(TAG, result)) {
                return false;
            }
            JSONObject r = result.optJSONObject("result");
            JSONObject pushResult = r != null ? r.optJSONObject("pushResult") : null;
            boolean done = pushResult != null ? pushResult.optBoolean("done", true) : true;
            return done;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private int countGoldTicketPendingAutoTasks(JSONArray todo) {
        if (todo == null || todo.length() == 0) {
            return 0;
        }
        int pending = 0;
        for (int i = 0; i < todo.length(); i++) {
            JSONObject task = todo.optJSONObject(i);
            if (task == null || task.optString("taskId").isEmpty()) return -1;
            if (isGoldTicketKnownAutoTask(task)) {
                pending++;
            }
        }
        return pending;
    }

    /**
     * 处理黄金票任务列表（首页 / 福利中心共用）。
     * 仅放开已确认可自动闭环的已知 taskId，避免误判未知任务。
     */
    private int processGoldTicketTasks(JSONArray todo, String source) {
        if (todo == null || todo.length() == 0) {
            return 0;
        }
        int pending = 0;
        for (int i = 0; i < todo.length(); i++) {
            JSONObject task = todo.optJSONObject(i);
            if (task == null) {
                continue;
            }
            if (!isGoldTicketKnownAutoTask(task)) {
                continue;
            }
            String taskId = task.optString("taskId");
            String title = task.optString("title", taskId);
            String status = task.optString("taskProcessStatus");
            if (isGoldTicketRewardReady(status)) {
                if (pushGoldTicketTask(taskId, "receive")) {
                    Log.other("黄金票🙈[" + source + "任务领取成功]#" + title);
                } else {
                    pending++;
                }
            } else if (isGoldTicketSignupRequired(status)) {
                String triggerRes = AntMemberRpcCall.goldBillTaskTrigger(taskId);
                if (triggerRes != null && !triggerRes.isEmpty()) {
                    try {
                        if (goldTicketOk(TAG, MyUtils.newJSONObject(triggerRes))) {
                            Log.other("黄金票🙈[" + source + "任务报名成功]#" + title);
                            if (pushGoldTicketTask(taskId, "send")) {
                                Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                            } else {
                                pending++;
                            }
                        } else {
                            pending++;
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(TAG, e);
                        pending++;
                    }
                } else {
                    pending++;
                }
            } else if ("SIGNUP_COMPLETE".equals(status)) {
                if (pushGoldTicketTask(taskId, "send")) {
                    Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                } else {
                    pending++;
                }
            } else {
                if (pushGoldTicketTask(taskId, "send")) {
                    Log.other("黄金票🙈[" + source + "任务完成]#" + title);
                } else {
                    pending++;
                }
            }
        }
        return pending;
    }

    private boolean handleGoldTicketHomeTasks(JSONObject home) {
        try {
            JSONArray todo = extractGoldTicketHomeTodoTasks(home);
            if (todo == null) return false;
            processGoldTicketTasks(todo, "首页");
            JSONObject refreshed = queryGoldTicketHome();
            if (refreshed == null) {
                Log.other("黄金票🙈[首页任务复查失败]暂不写入今日完成");
                return false;
            }
            JSONArray refreshedTodo = extractGoldTicketHomeTodoTasks(refreshed);
            if (refreshedTodo == null) return false;
            int pending = countGoldTicketPendingAutoTasks(refreshedTodo);
            if (pending > 0) {
                Log.i("黄金票🙈[首页任务]#保留" + pending + "项待重试");
            }
            return pending == 0;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private boolean handleGoldTicketWelfareTasks() {
        try {
            JSONArray todo = queryGoldTicketWelfareTodoTasks();
            if (todo == null) {
                Log.error("黄金票🙈[福利中心任务查询失败]");
                return false;
            }
            processGoldTicketTasks(todo, "福利中心");
            JSONArray refreshed = queryGoldTicketWelfareTodoTasks();
            if (refreshed == null) {
                Log.other("黄金票🙈[福利中心任务复查失败]暂不写入今日完成");
                return false;
            }
            int pendingRetry = countGoldTicketPendingAutoTasks(refreshed);
            if (pendingRetry > 0) {
                Log.i("黄金票🙈[福利中心任务]#保留" + pendingRetry + "项待重试");
            }
            return pendingRetry == 0;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    private void doGoldTicketConsume() {
        boolean consumeDone = false;
        try {
            Log.i("黄金票🙈[准备检查余额及提取]");
            String queryRes = AntMemberRpcCall.queryConsumeHome();
            if (queryRes == null || queryRes.isEmpty()) {
                return;
            }
            JSONObject queryJson = MyUtils.newJSONObject(queryRes);
            if (!goldTicketOk(TAG, queryJson)) {
                return;
            }
            JSONObject result = queryJson.optJSONObject("result");
            if (result == null) {
                return;
            }
            JSONObject assetInfo = result.optJSONObject("assetInfo");
            if (assetInfo == null) {
                return;
            }
            if (!(assetInfo.opt("availableAmount") instanceof Number)
                    || !(assetInfo.opt("minExchangeAmount") instanceof Number)
                    || (assetInfo.has("exchangeAmountUnit") && !(assetInfo.opt("exchangeAmountUnit") instanceof Number))) return;
            int availableAmount = assetInfo.optInt("availableAmount", -1);
            int minExchangeAmount = assetInfo.optInt("minExchangeAmount", 0);
            int exchangeAmountUnit = assetInfo.optInt("exchangeAmountUnit", minExchangeAmount);
            if (availableAmount < 0 || minExchangeAmount < 1 || exchangeAmountUnit < 1) return;
            int extractAmount = (availableAmount / exchangeAmountUnit) * exchangeAmountUnit;
            if (extractAmount < minExchangeAmount) {
                Log.other("黄金票🙈[余额不足]#当前[" + availableAmount + "]最低需[" + minExchangeAmount + "]");
                consumeDone = true;
                return;
            }
            String productId = "";
            JSONObject product = result.optJSONObject("product");
            if (product != null) {
                productId = product.optString("productId");
            } else if (result.has("productList")) {
                JSONArray productList = result.optJSONArray("productList");
                if (productList != null && productList.length() > 0) {
                    JSONObject firstProduct = productList.optJSONObject(0);
                    if (firstProduct == null) return;
                    productId = firstProduct.optString("productId");
                }
            } else if (assetInfo.has("mainExchangePrizeList")) {
                JSONArray list = assetInfo.optJSONArray("mainExchangePrizeList");
                if (list != null && list.length() > 0) {
                    JSONObject firstPrize = list.optJSONObject(0);
                    if (firstPrize == null) return;
                    productId = firstPrize.optString("bizNo");
                }
            } else if (assetInfo.has("footerExchangePrizeList")) {
                JSONArray list = assetInfo.optJSONArray("footerExchangePrizeList");
                if (list != null && list.length() > 0) {
                    JSONObject firstPrize = list.optJSONObject(0);
                    if (firstPrize == null) return;
                    productId = firstPrize.optString("bizNo");
                }
            } else {
                JSONObject backupPrize = assetInfo.optJSONObject("backupPrize");
                if (backupPrize != null && "GOLD".equalsIgnoreCase(backupPrize.optString("prizeType"))) {
                    productId = backupPrize.optString("bizNo");
                }
            }
            if (productId == null || productId.isEmpty()) {
                Log.error("黄金票🙈[提取异常]未找到有效的基金ID");
                return;
            }
            int bonusAmount = 0;
            JSONObject bonusInfo = result.optJSONObject("bonusInfo");
            if (bonusInfo != null) {
                bonusAmount = bonusInfo.optInt("bonusAmount", 0);
            }
            String exchangeMoney = null;
            JSONObject calcInfo = result.optJSONObject("calcInfo");
            if (calcInfo != null) {
                exchangeMoney = calcInfo.optString("exchangeMoney");
            }
            if (exchangeMoney == null || exchangeMoney.isEmpty()) {
                exchangeMoney = String.format(java.util.Locale.ROOT, "%.2f", (double) extractAmount / 1000.0);
            }
            Log.i("黄金票🙈[开始提取]#计划[" + extractAmount + "份]预计[" + exchangeMoney
                    + "元]持有[" + availableAmount + "]");
            String submitRes = AntMemberRpcCall.submitConsume(extractAmount, productId, bonusAmount);
            if (submitRes == null || submitRes.isEmpty()) {
                Log.error("黄金票🙈[提取失败]接口无返回");
                return;
            }
            JSONObject submitJson = MyUtils.newJSONObject(submitRes);
            if (!goldTicketOk(TAG, submitJson)) {
                String desc = submitJson.optString("resultDesc", submitJson.optString("memo"));
                if (desc != null && !desc.isEmpty()) {
                    Log.error("黄金票🙈[提取失败]" + desc);
                }
                return;
            }
            JSONObject submitResult = submitJson.optJSONObject("result");
            String writeOffNo = submitResult != null ? submitResult.optString("writeOffNo") : "";
            String successTitle = submitResult != null ? submitResult.optString("successTitle") : "";
            if ((writeOffNo != null && !writeOffNo.isEmpty())
                    || (successTitle != null && successTitle.contains("成功"))) {
                Log.other("黄金票🙈[提取成功]#" + exchangeMoney + "元#" + extractAmount + "份");
                consumeDone = true;
            } else {
                Log.error("黄金票🙈[提取失败]未返回核销码");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            if (consumeDone) {
                Status.flagToday("goldTicket::consume");
            }
        }
    }
    
    /**
     * 芝麻分任务处理（每日问答、公益任务、芭芭农场施肥等）
     */
    private void handleGrowthGuideTasks() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject root = MyUtils.newJSONObject(AntMemberRpcCall.queryGrowthBehaviorToDoList());
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return;
            }
            
            // 待处理任务列表
            JSONArray toDoList = root.optJSONArray("toDoList");
            int toDoCount = toDoList == null ? 0 : toDoList.length();
            if (toDoList == null || toDoCount == 0) {
                return;
            }
            
            for (int i = 0; i < toDoList.length(); i++) {
                JSONObject task = toDoList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                
                String behaviorId = task.optString("behaviorId", "");
                String title = task.optString("title", "");
                String status = task.optString("status", "");
                String subTitle = task.optString("subTitle", "");
                
                // 公益类任务（待领取）
                if ("wait_receive".equals(status)) {
                    String openResp = AntMemberRpcCall.openBehaviorCollect(behaviorId);
                    JSONObject openJo = MyUtils.newJSONObject(openResp);
                    if (MessageUtil.checkResultCode(TAG, openJo)) {
                        Log.other("攒芝麻分🧾任务领取：" + title);
                    }
                    continue;
                }
                
                // 每日问答
                if ("meiriwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    if (subTitle.contains("今日已参与")) {
                        Log.i("攒芝麻分🧾[每日问答] " + subTitle + "（跳过答题）");
                        continue;
                    }
                    
                    // 查询题目
                    JSONObject quizJo = MyUtils.newJSONObject(AntMemberRpcCall.queryDailyQuiz(behaviorId));
                    if (!MessageUtil.checkSuccess(TAG, quizJo)) {
                        continue;
                    }
                    JSONObject data = quizJo.optJSONObject("data");
                    if (data == null) {
                        continue;
                    }
                    
                    JSONObject qVo = data.optJSONObject("questionVo");
                    if (qVo == null) {
                        continue;
                    }
                    
                    JSONObject rightAnswer = qVo.optJSONObject("rightAnswer");
                    if (rightAnswer == null) {
                        continue;
                    }
                    
                    long bizDate = data.optLong("bizDate", 0L);
                    String questionId = qVo.optString("questionId", "");
                    String questionContent = qVo.optString("questionContent", "");
                    String answerId = rightAnswer.optString("answerId", "");
                    String answerContent = rightAnswer.optString("answerContent", "");
                    
                    if (bizDate <= 0 || questionId.isEmpty() || answerId.isEmpty()) {
                        continue;
                    }
                    
                    // 提交答案
                    JSONObject pushJo = MyUtils.newJSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, "RIGHT"));
                    if (MessageUtil.checkResultCode(TAG, pushJo)) {
                        Log.other("攒芝麻分🎖️[每日答题成功] " + StringUtil.truncate(questionContent, 200)
                                + " | 答案=" + StringUtil.truncate(answerContent, 200) + "(" + answerId + ")"
                                + (subTitle.isEmpty() ? "" : " | " + subTitle));
                    }
                }
                
                // 视频问答
                if ("shipingwenda".equals(behaviorId) && "wait_doing".equals(status)) {
                    long bizDate = System.currentTimeMillis();
                    String questionId = "question3";
                    String answerId = "A";
                    String answerType = "RIGHT";
                    
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.pushDailyQuizAnswer(behaviorId, bizDate, answerId, questionId, answerType));
                    
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("攒芝麻分🎖️[视频问答提交成功]");
                    }
                }
                
                // 芭芭农场施肥
                if ("babanongchang_7d".equals(behaviorId) && "wait_doing".equals(status)) {
                    
                    // 获取WUA
                    String wua = new AntOrchard().getWua();
                    String source = "DNHZ_NC_zhimajingnangSF";
                    
                    JSONObject spreadManureData = MyUtils.newJSONObject(AntOrchardRpcCall.orchardSpreadManure(false, wua));
                    
                    if (!"100".equals(spreadManureData.optString("resultCode"))) {
                        continue;
                    }
                    
                    String taobaoDataStr = spreadManureData.optString("taobaoData", "");
                    if (taobaoDataStr.isEmpty()) {
                        continue;
                    }
                    
                    JSONObject spreadTaobaoData = MyUtils.newJSONObject(taobaoDataStr);
                    
                    JSONObject currentStage = spreadTaobaoData.optJSONObject("currentStage");
                    if (currentStage == null) {
                        Log.error(TAG + "GrowthGuideTasks" + "芭芭农场[缺少currentStage]");
                        continue;
                    }
                    
                    String stageText = currentStage.optString("stageText", "");
                    JSONObject statistics = spreadTaobaoData.optJSONObject("statistics");
                    int dailyAppWateringCount = statistics == null ? 0 : statistics.optInt("dailyAppWateringCount", 0);
                    
                    Log.farm("芭芭农场🌳施肥" + dailyAppWateringCount + "次[" + stageText + "]");
                    Log.other("攒芝麻分🎖️芭芭农场施肥[" + title + "]已施肥" + dailyAppWateringCount + "次");
                    
                }
            }
        }
        catch (Throwable e) {
            Log.printStackTrace(TAG + ".handleGrowthGuideTasks", e);
        }
    }

    
    public static void queryAndCollect() {
        try {
            // 1. 查询进度球状态
            String queryResp = AntMemberRpcCall.queryScoreProgress();
            if (queryResp == null || queryResp.isEmpty()) {
                return;
            }
            
            JSONObject json = MyUtils.newJSONObject(queryResp);
            
            // 检查 success
            if (!MessageUtil.checkSuccess(TAG, json)) {
                return;
            }
            
            JSONObject totalWait = json.optJSONObject("totalWaitProcessVO");
            if (totalWait == null) {
                return;
            }
            
            JSONArray idList = totalWait.optJSONArray("totalProgressIdList");
            if (idList == null || idList.length() == 0) {
                return;
            }
            
            // 直接传 JSONArray
            String collectResp = AntMemberRpcCall.collectProgressBall(idList);
            if (collectResp == null) {
                return;
            }
            
            JSONObject collectJson = MyUtils.newJSONObject(collectResp);
            int collectedAccelerateProgress = collectJson.optInt("collectedAccelerateProgress", -1);
            int currentAccelerateValue = collectJson.optInt("currentAccelerateValue", 0);
            int totalAccelerateProgress = collectJson.optInt("totalAccelerateProgress", 0);
            Log.other("攒芝麻分🎁领取#本次加速进度:" + collectedAccelerateProgress + "(总" + totalAccelerateProgress + "%)加速倍率:" + currentAccelerateValue);
        }
        catch (Exception e) {
            Log.printStackTrace(TAG + "queryAndCollect err", e);
        }
    }
    
    //游戏中心任务
    
    /**
     * 批量领取玩乐豆
     */
    public static void batchReceivePointBall() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.batchReceivePointBall());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.optJSONObject("data");
                String totalAmount = dataObj != null ? dataObj.optString("totalAmount") : "";
                Log.other("游戏中心🎮批量领取#获得[" + totalAmount + "玩乐豆]");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "batchReceivePointBall err:", t);
        }
    }
    
    /**
     * 每日签到
     *
     * @return 签到是否成功
     */
    public static boolean dailySignIn() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.continueSignIn());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject data = jsonObject.optJSONObject("data");
                JSONObject toastModule = data != null ? data.optJSONObject("autoSignInToastModule") : null;
                if (toastModule == null) {
                    return false;
                }
                String desc = toastModule.optString("desc");
                String beanNum = desc.substring(desc.indexOf("玩乐豆+") + 4);
                Log.other("游戏中心🎮每日签到#获得[" + beanNum + "玩乐豆]");
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "continueSignIn err:", t);
        }
        return false;
    }
    
    /**
     * 处理单个任务
     *
     * @param taskObj 任务JSON对象
     */
    public void processTask(JSONObject taskObj) {
        try {
            String actionType = taskObj.optString("actionType");
            String taskId = taskObj.optString("taskId");
            String subTitle = taskObj.optString("subTitle");
            String taskStatus = taskObj.optString("taskStatus");
            int prizeAmount = taskObj.optInt("prizeAmount", 0);

            //黑名单任务跳过
            if (AntMemberTaskList.getValue().contains(subTitle)) {
                return;
            }
            // 任务未完成且需要报名（needSignUp 可能缺字段，用 optBoolean 避免整条任务被异常打断）
            if ("NOT_DONE".equals(taskStatus) && taskObj.optBoolean("needSignUp", false)) {
                JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.doTaskSignup(taskId));
                if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", subTitle, jsonObject);
                    return;
                }
            }

            // 执行任务：原先只处理 actionType=VIEW，其它类型直接 return（列表拿到了却静默不处理、
            // 连日志都没有）。现在各类都尝试一次。
            JSONObject doTaskjo = MyUtils.newJSONObject(AntMemberRpcCall.doTaskSend(taskId));
            if (MessageUtil.checkSuccess(TAG, doTaskjo)) {
                Log.other("游戏中心🎮完成任务[" + subTitle + "]#待领[" + prizeAmount + "玩乐豆]");
            } else {
                // doTaskSend 常被 400000040 拒绝，改用另一种实现方案（见 TaskAlternative）
                String sceneCode = taskObj.optString("sceneCode", "").trim();
                if (TaskAlternative.hit(doTaskjo, sceneCode)) {
                    // 另一种实现方案（见 TaskAlternative）；version 传本模块原值
                    TaskAlternative.trigger(pendingVerifyTasks, taskId, subTitle, taskId, sceneCode,
                            AntMemberRpcCall.DO_FARM_TASK_VERSION, "游戏中心", msg -> Log.other(msg));
                } else {
                    Log.other("游戏中心⚠️未完成[" + subTitle + "]#actionType=" + actionType);
                    //检查并标记黑名单任务
                    MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", subTitle, doTaskjo);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "doTask err:", t);
        }
    }

    /**
     * 核对「已触发但响应不可信」的游戏中心任务：等几秒后重拉任务列表，**仍为 NOT_DONE** 的才计入自动拉黑。
     * <p>为什么以列表为准：{@code doFarmTask} 会回 102「服务器正在开小差」等错码但任务其实已生效，
     * 服务端是异步推进状态的，只有列表里的 {@code taskStatus} 才是最终判据。
     */
    private void verifyPendingTasks() {
        TaskAlternative.verify(pendingVerifyTasks, VERIFY_CFG, () -> {
            Set<String> notDone = new LinkedHashSet<>();
            if (!collectNotDoneIds(AntMemberRpcCall.queryModularTaskList(), notDone)
                    || !collectNotDoneIds(AntMemberRpcCall.queryTaskList(), notDone)) {
                return null;
            }
            return notDone;
        });
    }

    /**
     * 从任务列表响应里收集 {@code taskStatus=NOT_DONE} 的 taskId。
     * <p>两套列表结构不同（v3 {@code data.taskModuleList[].taskList[]}、
     * v4 {@code data.gameTaskModule.gameTaskList[]}），这里都解析一遍，避免漏判导致误拉黑。
     */
    private static boolean collectNotDoneIds(String response, Set<String> out) {
        try {
            JSONObject data = MyUtils.newJSONObject(response).optJSONObject("data");
            if (data == null) {
                return false;
            }
            JSONArray modules = data.optJSONArray("taskModuleList");
            if (modules != null) {
                for (int i = 0; i < modules.length(); i++) {
                    JSONObject module = modules.optJSONObject(i);
                    collectNotDoneFromArray(module == null ? null : module.optJSONArray("taskList"), out);
                }
            }
            JSONObject gameTaskModule = data.optJSONObject("gameTaskModule");
            if (gameTaskModule != null) {
                collectNotDoneFromArray(gameTaskModule.optJSONArray("gameTaskList"), out);
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "collectNotDoneIds err:", t);
            return false;
        }
    }

    private static void collectNotDoneFromArray(JSONArray tasks, Set<String> out) {
        if (tasks == null) {
            return;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || !"NOT_DONE".equals(task.optString("taskStatus", "").trim())) {
                continue;
            }
            String taskId = task.optString("taskId", "").trim();
            if (!taskId.isEmpty()) {
                out.add(taskId);
            }
        }
    }
    
    /**
     * 查询并处理任务列表
     */
    public void queryAndProcessTaskList() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryModularTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            if (!jsonObject.has("data")) {
                return;
            }
            JSONObject data = jsonObject.optJSONObject("data");
            JSONArray taskModuleList = data != null ? data.optJSONArray("taskModuleList") : null;
            for (int i = 0; taskModuleList != null && i < taskModuleList.length(); i++) {
                JSONObject moduleObj = taskModuleList.optJSONObject(i);
                JSONArray taskList = moduleObj != null ? moduleObj.optJSONArray("taskList") : null;
                for (int j = 0; taskList != null && j < taskList.length(); j++) {
                    JSONObject taskItem = taskList.optJSONObject(j);
                    if (taskItem != null) {
                        processTask(taskItem);
                    }
                }
            }
            // 核对本轮 doFarmTask 的结果（响应不可信，以任务列表为准）
            verifyPendingTasks();
        }
        catch (Throwable t) {
            Log.err(TAG, "queryModularTaskList err:", t);
        }
    }
    
    /**
     * 游戏任务列表（游戏中心）
     * <p>原先该方法没有任何调用点（死代码），这里的游戏类任务从未被执行过；
     * 并且 {@code optJSONObject("gameTaskModule")} 为 null 时直接取 optJSONArray 会 NPE，
     * 被 catch 吞掉后整份列表一个任务都处理不了，这里一并修掉。
     */
    public void queryTaskList() {
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryTaskList());
            if (!MessageUtil.checkSuccess(TAG, jsonObject)) {
                return;
            }
            JSONObject data = jsonObject.optJSONObject("data");
            if (data == null) {
                return;
            }
            JSONObject gameTaskModule = data.optJSONObject("gameTaskModule");
            JSONArray gameTaskList = gameTaskModule == null ? null : gameTaskModule.optJSONArray("gameTaskList");
            if (gameTaskList == null || gameTaskList.length() == 0) {
                // 抓包实测(2026-09-22)：该接口当前恒返回 data:{}，且全量抓包里从未出现 gameTaskModule，
                // 说明这个取值路径本身可能就是错的（只是恰好空返回才没报错）。一旦服务端真返回任务，
                // 把顶层字段名打出来，便于按真实结构适配，避免又一次"静默拿不到任务"
                if (data.length() > 0 && gameTaskModule == null) {
                    String raw = data.toString();
                    Log.other("游戏中心⚠️任务列表结构未知#data=" + (raw.length() > 300 ? raw.substring(0, 300) : raw));
                }
                return;
            }
            for (int i = 0; i < gameTaskList.length(); i++) {
                JSONObject taskItem = gameTaskList.optJSONObject(i);
                if (taskItem != null) {
                    processTask(taskItem);
                }
            }
            // 核对本轮 doFarmTask 的结果（响应不可信，以任务列表为准）
            verifyPendingTasks();
        }
        catch (Throwable t) {
            Log.err(TAG, "queryTaskList err:", t);
        }
    }
    
    /**
     * 查询玩乐豆小球列表，有则领取
     */
    public static void queryPointBallList() {
        try {
            String response = ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.queryPointBallList", "[{}]");
            JSONObject jsonObject = MyUtils.newJSONObject(response);
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject data = jsonObject.optJSONObject("data");
                JSONArray pointBallList = data != null ? data.optJSONArray("pointBallList") : null;
                if (pointBallList != null && pointBallList.length() > 0) {
                    batchReceivePointBall();
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryPointBallList err:", t);
        }
    }
    
    /**
     * 检查并执行签到
     */
    public static void checkAndDoSignIn() {
        if (Status.hasFlagToday("gameCenterSignIn")) {
            return;
        }
        
        try {
            JSONObject jsonObject = MyUtils.newJSONObject(AntMemberRpcCall.queryPointBallList());
            if (MessageUtil.checkSuccess(TAG, jsonObject)) {
                JSONObject dataObj = jsonObject.optJSONObject("data");
                if (dataObj != null && dataObj.has("signInBallModule")) {
                    JSONObject signInModule = dataObj.optJSONObject("signInBallModule");
                    if (signInModule != null && !signInModule.optBoolean("signInStatus")) {
                        if (dailySignIn()) {
                            Status.flagToday("gameCenterSignIn");
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "querySignInBall err:", t);
        }
    }
    
    /*
    private void enableGameCenter() {
        try {
            try {
                String str = AntMemberRpcCall.querySignInBall();
                JSONObject jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.querySignInBall", jsonObject.optString("resultDesc"));
                    return;
                }
                str = JsonUtil.getValueByPath(jsonObject, "data.signInBallModule.signInStatus");
                if (String.valueOf(true).equals(str)) {
                    return;
                }
                str = AntMemberRpcCall.continueSignIn();
                TimeUtil.sleep(300);
                jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".signIn.continueSignIn", jsonObject.optString("resultDesc"));
                    return;
                }
                Log.other("游戏中心🎮签到成功");
            }
            catch (Throwable th) {
                Log.err(TAG, "signIn err:", th);
            }
            try {
                String str = AntMemberRpcCall.queryPointBallList();
                JSONObject jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".batchReceive.queryPointBallList", jsonObject.optString("resultDesc"));
                    return;
                }
                JSONArray jsonArray = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "data.pointBallList");
                if (jsonArray == null || jsonArray.length() == 0) {
                    return;
                }
                str = AntMemberRpcCall.batchReceivePointBall();
                TimeUtil.sleep(300);
                jsonObject = MyUtils.newJSONObject(str);
                if (jsonObject.optBoolean("success")) {
                    Log.other("游戏中心🎮全部领取成功[" + JsonUtil.getValueByPath(jsonObject, "data.totalAmount") + "]乐豆");
                }
                else {
                    Log.i(TAG + ".batchReceive.batchReceivePointBall", jsonObject.optString("resultDesc"));
                }
            }
            catch (Throwable th) {
                Log.err(TAG, "batchReceive err:", th);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    */
    // 会员积分兑换 - 获取权益列表（无条件）+ 兑换（受开关控制）
    private static long nextMemberExchangeTime(String times, long now) {
        if (times == null) return -1;
        long best = -1;
        for (String token : times.split("[,，;；]")) {
            String text = token.trim();
            if (!text.matches("\\d{2}:\\d{2}")) continue;
            int hour = Integer.parseInt(text.substring(0, 2)), minute = Integer.parseInt(text.substring(3));
            if (hour > 23 || minute > 59) continue;
            java.util.Calendar date = MyUtils.getInstance(); date.setTimeInMillis(now);
            date.set(java.util.Calendar.HOUR_OF_DAY, hour); date.set(java.util.Calendar.MINUTE, minute);
            date.set(java.util.Calendar.SECOND, 0); date.set(java.util.Calendar.MILLISECOND, 0);
            if (date.getTimeInMillis() <= now) date.add(java.util.Calendar.DAY_OF_MONTH, 1);
            long at = date.getTimeInMillis();
            if (best < 0 || at < best) best = at;
        }
        return best;
    }

    private synchronized void scheduleMemberExchange() {
        long at = memberPointExchangeSecKill.getValue() && memberPointExchangeBenefit.getValue() && memberPointExchangeSecKillBudget.getValue() > 0
                ? nextMemberExchangeTime(memberPointExchangeSecKillTimes.getValue(), MyUtils.getInstance().getTimeInMillis()) : -1;
        String id = at > 0 ? "memberBenefitSecKill_" + at : null;
        if (id != null && id.equals(memberExchangeScheduledId) && getChildTask(id) != null) return;
        if (memberExchangeScheduledId != null) removeChildTask(memberExchangeScheduledId);
        memberExchangeScheduledId = null;
        if (id == null) return;
        if (addChildTask(new ChildModelTask(id, "antMember", () -> executeMemberExchange(at), at))) memberExchangeScheduledId = id;
    }

    private void executeMemberExchange(long at) {
        boolean reschedule = true;
        try {
            TimeUtil.sleep(0);
            long now = MyUtils.getInstance().getTimeInMillis();
            if (TaskCommon.IS_ENERGY_TIME || !memberPointExchangeSecKill.getValue() || !memberPointExchangeBenefit.getValue() || memberPointExchangeSecKillBudget.getValue() <= 0
                    || now < at || now - at > 60000L || nextMemberExchangeTime(memberPointExchangeSecKillTimes.getValue(), at - 1) != at) return;
            String flag = "member::secKillSlot::" + at;
            if (Status.hasFlagToday(flag)) return;
            Status.flagToday(flag);
            memberPointExchangeBenefit(true);
        } catch (TaskCancelledException e) { reschedule = false; throw e;
        } catch (Throwable t) { Log.err(TAG, "executeMemberExchange", t);
        } finally {
            synchronized (this) {
                if (("memberBenefitSecKill_" + at).equals(memberExchangeScheduledId)) memberExchangeScheduledId = null;
            }
            if (reschedule) scheduleMemberExchange();
        }
    }

    private static int memberTimedPrice(JSONObject price, int hour) {
        if (price == null || !"POINT_PAY".equals(price.opt("strategyType"))) return -1;
        try {
            Object points = price.opt("point"), grab = price.opt("grabHour"), cash = price.opt("yuan");
            if (!(points instanceof Number || points instanceof String) || !(grab instanceof Number || grab instanceof String)) return -1;
            int amount = new java.math.BigDecimal(points.toString()).intValueExact();
            if (amount < 0 || new java.math.BigDecimal(grab.toString()).intValueExact() != hour) return -1;
            if (price.has("yuan") && (!(cash instanceof Number || cash instanceof String) || new java.math.BigDecimal(cash.toString()).signum() != 0)) return -1;
            return amount;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private boolean exchangeTimedBenefit(String benefitId, String itemId, int points) {
        synchronized (benefitExchangeLock) {
            TimeUtil.sleep(0);
            int budget = memberPointExchangeSecKillBudget.getValue(), spent = Status.getIntFlagToday("member::secKillSpent");
            if (benefitId == null || benefitId.isEmpty() || itemId == null || itemId.isEmpty() || points < 0 || budget <= 0 || spent < 0 || points > budget - spent || !Status.canMemberPointExchangeBenefitToday(benefitId)
                    || Status.hasFlagToday("member::benefitExchangeAttempt::" + benefitId)) return false;
            // Reserve before the request: an uncertain debit still occupies today's point budget.
            Status.setIntFlagToday("member::secKillSpent", spent + points);
            return exchangeBenefit(benefitId, itemId);
        }
    }

    private void memberPointExchangeBenefit() {
        memberPointExchangeBenefit(false);
    }

    private void memberPointExchangeBenefit(boolean timed) {
        try {
            if (timed) { exchangeSelectedTimedBenefits(memberPointExchangeBenefitList.getValue()); return; }
            JSONArray searched = queryMemberBenefitSearchResults();
            String userId = UserIdMap.getCurrentUid();
            // 依次尝试多个 deliveryId，找到可用的分类
            String[] deliveryIds = {"94000SR2023102305988003", "94000SR2024011106752003", "94000SR2024071108523003", "94000SR2024071808609003"};
            JSONObject jo = null;
            for (String deliveryId : deliveryIds) {
                JSONObject candidate = MyUtils.newJSONObject(AntMemberRpcCall.queryDeliveryZoneDetail(userId, deliveryId));
                JSONArray candidateList = candidate.optJSONArray("entityInfoList");
                if (MessageUtil.checkResultCode(TAG, candidate) && candidateList != null && candidateList.length() > 0) {
                    Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 成功，权益数=" + candidateList.length());
                    jo = candidate;
                    break;
                }
                Log.i(TAG, "queryDeliveryZoneDetail deliveryId=" + deliveryId + " 失败，尝试下一个");
            }
            if (jo == null && searched.length() > 0) jo = MyUtils.newJSONObject().put("resultCode", "SUCCESS").put("entityInfoList", searched);
            if (jo == null) {
                // 所有 deliveryId 都失败，尝试备用接口
                Log.i(TAG, "queryDeliveryZoneDetail 全部失败，尝试备用接口");
                fetchBenefitsFromNavi(userId);
                MemberBenefitIdMap.save(userId);
                return;
            }
            JSONArray entityInfoList = jo.optJSONArray("entityInfoList");
            if (entityInfoList != null && entityInfoList != searched) {
                Set<String> known = new HashSet<>();
                for (int i = 0; i < entityInfoList.length(); i++) {
                    JSONObject entity = entityInfoList.optJSONObject(i), benefit = entity == null ? null : entity.optJSONObject("benefitInfo");
                    if (benefit != null) known.add(benefit.optString("benefitId"));
                }
                for (int i = 0; i < searched.length(); i++) {
                    JSONObject entity = searched.optJSONObject(i), benefit = entity == null ? null : entity.optJSONObject("benefitInfo");
                    if (benefit != null && known.add(benefit.optString("benefitId"))) entityInfoList.put(entity);
                }
            }
            if (entityInfoList == null || entityInfoList.length() == 0) {
                Log.record("会员积分[当前分类无可兑换权益，尝试备用接口]");
                fetchBenefitsFromNavi(userId);
                MemberBenefitIdMap.save(userId);
                return;
            }
            // 无条件保存权益列表（供用户勾选），兑换与否受开关和勾选控制
            java.util.Set<String> selectedIds = memberPointExchangeBenefitList.getValue();
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.optJSONObject(i);
                JSONObject benefitInfo = entityInfo != null ? entityInfo.optJSONObject("benefitInfo") : null;
                JSONObject pricePresentation = benefitInfo != null ? benefitInfo.optJSONObject("pricePresentation") : null;
                if (benefitInfo == null || pricePresentation == null || !"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.optString("name");
                String benefitId = benefitInfo.optString("benefitId");
                MemberBenefitIdMap.add(benefitId, name);
            }
            MemberBenefitIdMap.save(userId);

            // 开关关闭则不兑换
            if (!memberPointExchangeBenefit.getValue()) {
                Log.i(TAG, "会员积分兑换开关已关闭，仅更新权益列表");
                return;
            }
            if (selectedIds.isEmpty()) {
                Log.i(TAG, "会员积分兑换已开启，请在列表中选择要兑换的权益");
            }
            for (int i = 0; i < entityInfoList.length(); i++) {
                JSONObject entityInfo = entityInfoList.optJSONObject(i);
                JSONObject benefitInfo = entityInfo != null ? entityInfo.optJSONObject("benefitInfo") : null;
                JSONObject pricePresentation = benefitInfo != null ? benefitInfo.optJSONObject("pricePresentation") : null;
                if (benefitInfo == null || pricePresentation == null || !"POINT_PAY".equals(pricePresentation.optString("strategyType"))) {
                    continue;
                }
                String name = benefitInfo.optString("name");
                String benefitId = benefitInfo.optString("benefitId");
                // 只兑换用户在列表中勾选的权益
                if (!selectedIds.contains(benefitId)) {
                    continue;
                }
                if (!Status.canMemberPointExchangeBenefitToday(benefitId)) {
                    continue;
                }
                String itemId = benefitInfo.optString("itemId");
                // Selected timed goods use only the scheduled budgeted path when enabled.
                if (!timed && memberPointExchangeSecKill.getValue() && pricePresentation.has("grabHour")) {
                    Object grab = pricePresentation.opt("grabHour");
                    if (grab instanceof Number || grab instanceof String) {
                        try {
                            int hour = new java.math.BigDecimal(grab.toString()).intValueExact();
                            if (hour >= 0 && hour <= 23) continue;
                        } catch (NumberFormatException | ArithmeticException ignored) { continue; }
                    } else continue;
                }
                if (exchangeBenefit(benefitId, itemId)) {
                    String point = pricePresentation.optString("point");
                    Log.other("会员积分🎐兑换[" + name + "]#花费[" + point + "积分]");
                }
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "memberPointExchangeBenefit err:", t);
        }
    }

    private JSONArray queryMemberBenefitSearchResults() {
        JSONArray result = new JSONArray();
        String configured = memberBenefitSearchKeywords.getValue();
        if (configured == null || configured.trim().isEmpty()) return result;
        Set<String> keywords = new LinkedHashSet<>(), ids = new HashSet<>();
        // ponytail: ten keywords, first 20 results each; add pagination only if this ceiling is insufficient.
        for (String part : configured.split("[,，;；]")) {
            String keyword = part.trim();
            if (keyword.isEmpty() || keyword.length() > 64 || !keywords.add(keyword)) continue;
            if (keywords.size() > 10) break;
            try {
                TimeUtil.sleep(0);
                JSONObject data = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.searchMemberBenefit(keyword)));
                JSONArray rows = data == null ? null : data.optJSONArray("entityInfoList");
                if (rows == null) { Log.record("会员权益搜索：查询未确认[" + keyword + "]"); continue; }
                for (int i = 0; i < rows.length() && i < 20; i++) {
                    JSONObject entity = rows.optJSONObject(i), benefit = entity == null ? null : entity.optJSONObject("benefitInfo");
                    JSONObject price = benefit == null ? null : benefit.optJSONObject("pricePresentation");
                    if (benefit == null || price == null || !"POINT_PAY".equals(price.opt("strategyType"))
                            || !(benefit.opt("benefitId") instanceof String) || benefit.optString("benefitId").isEmpty()
                            || !(benefit.opt("name") instanceof String) || benefit.optString("name").isEmpty()) continue;
                    if (ids.add(benefit.optString("benefitId"))) result.put(entity);
                }
            } catch (TaskCancelledException e) { throw e;
            } catch (Throwable t) { Log.err(TAG, "queryMemberBenefitSearchResults", t); }
        }
        return result;
    }

    private void exchangeSelectedTimedBenefits(Set<String> selected) throws JSONException {
        int checked = 0;
        // Query only explicitly selected goods; no full-catalog burst or hardcoded product IDs.
        for (String id : selected) {
            TimeUtil.sleep(0);
            if (++checked > 50 || !memberPointExchangeSecKill.getValue() || !memberPointExchangeBenefit.getValue()
                    || Status.getIntFlagToday("member::secKillSpent") >= memberPointExchangeSecKillBudget.getValue()) return;
            if (id == null || id.isEmpty() || !Status.canMemberPointExchangeBenefitToday(id)
                    || Status.hasFlagToday("member::benefitExchangeAttempt::" + id)) continue;
            JSONObject data = memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.querySingleBenefitDetail(id)));
            JSONObject benefit = data == null ? null : data.optJSONObject("benefitDetail");
            if (benefit == null || !id.equals(benefit.opt("benefitId")) || !(benefit.opt("itemId") instanceof String)) continue;
            int points = memberTimedPrice(benefit.optJSONObject("pricePresentation"), MyUtils.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
            if (points >= 0 && exchangeTimedBenefit(id, benefit.optString("itemId"), points)) Log.other("会员整点抢兑🎐[" + benefit.optString("name", id) + "]#花费[" + points + "积分]");
        }
    }

    // 备用接口：通过导航分类码查询权益列表（支持分页）
    private void fetchBenefitsFromNavi(String userId) {
        try {
            // 依次尝试各导航分类码：特色(14)、出行(1)、美食(11)、日用(12)
            String[] naviCodes = {"14", "1", "11", "12", "13", "bb82b", ""};
            for (String naviCode : naviCodes) {
                int pageNum = 1;
                int totalPages = 1;
                Log.i(TAG, "fetchBenefitsFromNavi start naviCode=" + naviCode);
                do {
                    String raw = AntMemberRpcCall.queryIndexNaviBenefitFlowV2(userId, naviCode, pageNum);
                    JSONObject jo = MyUtils.newJSONObject(raw);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " resultCode=" + jo.optString("resultCode") + " desc=" + jo.optString("desc"));
                        break;
                    }
                    JSONArray benefitList = jo.optJSONArray("entityInfoList");
                    if (benefitList == null || benefitList.length() == 0) {
                        JSONObject data = jo.optJSONObject("data");
                        benefitList = data != null ? data.optJSONArray("entityInfoList") : null;
                    }
                    if (benefitList == null || benefitList.length() == 0) {
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " 无权益数据，raw=" + raw.substring(0, Math.min(300, raw.length())));
                        break;
                    }
                    // 获取总页数
                    if (pageNum == 1) {
                        int next = jo.optInt("nextPageNum", 0);
                        int adNext = jo.optInt("nextAdPageNum", 0);
                        Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " pageNum=" + pageNum + " nextPageNum=" + next + " nextAdPageNum=" + adNext);
                        if (next > 1) {
                            totalPages = next;
                        } else if (adNext > 1) {
                            totalPages = adNext;
                        }
                    }
                    // 从 entityInfoList 解析完整权益信息
                    int saved = 0, skipped = 0;
                    for (int i = 0; i < benefitList.length(); i++) {
                        JSONObject entity = benefitList.optJSONObject(i);
                        JSONObject benefitInfo = entity != null ? entity.optJSONObject("benefitInfo") : null;
                        if (benefitInfo == null) { skipped++; continue; }
                        JSONObject pricePresentation = benefitInfo.optJSONObject("pricePresentation");
                        if (pricePresentation == null) { skipped++; continue; }
                        String strategyType = pricePresentation.optString("strategyType");
                        if (!"POINT_PAY".equals(strategyType)) {
                            Log.i(TAG, "  跳过[" + benefitInfo.optString("name") + "] strategyType=" + strategyType);
                            skipped++;
                            continue;
                        }
                        String name = benefitInfo.optString("name");
                        String benefitId = benefitInfo.optString("benefitId");
                        if (benefitId.isEmpty()) { skipped++; continue; }
                        Log.i(TAG, "  保存[" + name + "] benefitId=" + benefitId);
                        MemberBenefitIdMap.add(benefitId, name);
                        saved++;
                    }
                    // 从 extInfo 补充 entityInfoList 中缺失的 benefitId
                    JSONObject extInfo = jo.optJSONObject("extInfo");
                    if (extInfo != null && extInfo.length() > 0) {
                        int extAdded = 0;
                        for (Iterator<String> it = extInfo.keys(); it.hasNext(); ) {
                            String key = it.next();
                            // 跳过非 benefitId 的元数据 key
                            if ("promoSceneCode".equals(key) || key.startsWith("AMS") == false && key.length() < 10) {
                                continue;
                            }
                            if (!MemberBenefitIdMap.getMap().containsKey(key)) {
                                Log.i(TAG, "  补充[extInfo] benefitId=" + key);
                                MemberBenefitIdMap.add(key, "会员积分权益");
                                extAdded++;
                            }
                        }
                        if (extAdded > 0) {
                            Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " extInfo补充" + extAdded + "个");
                        }
                    }
                    Log.i(TAG, "queryIndexNaviBenefitFlowV2 naviCode=" + naviCode + " 保存" + saved + "个，补充" + (extInfo != null ? extInfo.length() : 0) + "个，累计" + MemberBenefitIdMap.getMap().size());
                    pageNum++;
                } while (pageNum <= totalPages);
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "fetchBenefitsFromNavi err:", t);
        }
    }
    
    private Boolean exchangeBenefit(String benefitId, String itemId) {
        synchronized (benefitExchangeLock) {
        if (benefitId == null || benefitId.isEmpty() || itemId == null || itemId.isEmpty() || !Status.canMemberPointExchangeBenefitToday(benefitId)
                || Status.hasFlagToday("member::benefitExchangeAttempt::" + benefitId)) return false;
        try {
            TimeUtil.sleep(0);
            Status.flagToday("member::benefitExchangeAttempt::" + benefitId);
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.exchangeBenefit(benefitId, itemId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Status.memberPointExchangeBenefitToday(benefitId);
                return true;
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "exchangeBenefit err:", t);
        }
        return false;
        }
    }
    
    private void collectSesame() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject entrance = jo.optJSONObject("entrance");
            if (entrance == null || !entrance.optBoolean("openApp")) {
                Log.other("芝麻信用💌未开通");
                return;
            }

            jo = MyUtils.newJSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("data")) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !data.has("toCompleteVOS")) {
                return;
            }
            JSONArray toCompleteVOS = data.optJSONArray("toCompleteVOS");
            LinkedHashMap<String, String> reported = new LinkedHashMap<>();
            LinkedHashMap<String, Integer> beforeComplete = new LinkedHashMap<>();
            for (int i = 0; toCompleteVOS != null && i < toCompleteVOS.length(); i++) {
                JSONObject toCompleteVO = toCompleteVOS.optJSONObject(i);
                if (toCompleteVO == null) {
                    continue;
                }
                String taskTitle = toCompleteVO.has("title") ? toCompleteVO.optString("title") : "未知任务";
                //黑名单任务跳过
                if (MemberCreditSesameTaskList.getValue().contains(taskTitle)) {
                    continue;
                }
                
                boolean finishFlag = toCompleteVO.optBoolean("finishFlag", false);
                String actionText = toCompleteVO.optString("actionText", "");
                
                // 检查任务是否已完成
                if (finishFlag || "已完成".equals(actionText)) {
                    continue;
                }
                
                if (!(toCompleteVO.opt("templateId") instanceof String)
                        || (toCompleteVO.has("finishFlag") && !(toCompleteVO.opt("finishFlag") instanceof Boolean))) {
                    continue;
                }
                
                String taskTemplateId = toCompleteVO.optString("templateId");
                int needCompleteNum = toCompleteVO.has("needCompleteNum") ? toCompleteVO.optInt("needCompleteNum") : 1;
                int completedNum = toCompleteVO.optInt("completedNum", -1);
                if (taskTemplateId.isEmpty() || needCompleteNum <= 0 || completedNum < 0) {
                    continue;
                }
                // 今日已上报且回读确认状态没推进：不再重复上报（次日再试），避免每轮刷同一批
                if (Status.hasFlagToday("AntMember::sesame::" + taskTitle)) {
                    continue;
                }

                // 链路依据（2026-10-05 抓包）：官方走三段——PromiseRpcManager.joinActivity（拿 data.recordId）
                // → CreditAccumulateStrategyRpcManager.taskFeedback → PromiseRpcManager.pushActivity（带 recordId）。
                // 只发中间的 taskFeedback 时接口照样返回 success（data:true），但任务状态不推进，
                // 于是出现"日志报完成、实际未完成、每轮都报"；原先"官方全程不发 join/push"的结论据此推翻。
                // 上报被受理 ≠ 任务完成：无论走到哪一步都登记回读，真实状态一律以回读为准
                reportSesameTask(taskTitle, taskTemplateId);
                reported.put(taskTemplateId, taskTitle);
                beforeComplete.put(taskTemplateId, completedNum);
                
                jo = MyUtils.newJSONObject(AntMemberRpcCall.queryCreditFeedback());
                TimeUtil.sleep(300);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray ja = jo.optJSONArray("creditFeedbackVOS");
                if (!AntFarm.bindPigeonFeedback(ja)) return;
                for (int j = 0; ja != null && j < ja.length(); j++) {
                    jo = ja.optJSONObject(j);
                    if (jo == null || !"UNCLAIMED".equals(jo.optString("status"))) {
                        continue;
                    }
                    // title 使用当前任务名称。
                    String creditFeedbackId = jo.optString("creditFeedbackId");
                    String potentialSize = jo.optString("potentialSize");
                    jo = MyUtils.newJSONObject(AntMemberRpcCall.collectCreditFeedback(creditFeedbackId));
                    TimeUtil.sleep(300);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.other("收芝麻粒🙇🏻‍♂️领取[" + taskTitle + "]奖励[芝麻粒*" + potentialSize + "]");
                    }
                }
            }

            // 回读校验：上报被受理不等于任务完成，按服务端最新完成度判定，日志只写真实结果
            if (!reported.isEmpty()) {
                TimeUtil.sleep(500);
                JSONObject freshJo = MyUtils.newJSONObject(AntMemberRpcCall.CreditAccumulateStrategyRpcManager());
                LinkedHashMap<String, String> freshProgress = new LinkedHashMap<>();
                boolean freshOk = false;
                JSONObject freshData = freshJo.optJSONObject("data");
                if (MessageUtil.checkResultCode(TAG, freshJo) && freshData != null) {
                    JSONArray freshList = freshData.optJSONArray("toCompleteVOS");
                    freshOk = freshList != null;
                    if (freshList != null) {
                        for (int k = 0; k < freshList.length(); k++) {
                            JSONObject vo = freshList.optJSONObject(k);
                            if (vo == null || !(vo.opt("templateId") instanceof String) || vo.optString("templateId").isEmpty()
                                    || (vo.has("finishFlag") && !(vo.opt("finishFlag") instanceof Boolean))) {
                                freshOk = false;
                                break;
                            }
                            String tid = vo.optString("templateId");
                            int need = vo.has("needCompleteNum") ? vo.optInt("needCompleteNum", -1) : 1;
                            if (vo.optBoolean("finishFlag", false) || "已完成".equals(vo.optString("actionText", ""))) {
                                freshProgress.put(tid, "DONE");
                            } else {
                                int completed = vo.optInt("completedNum", -1);
                                if (need <= 0 || completed < 0) {
                                    freshOk = false;
                                    break;
                                }
                                freshProgress.put(tid, completed + "/" + need);
                            }
                        }
                    }
                }
                for (Map.Entry<String, String> entry : reported.entrySet()) {
                    String tid = entry.getKey();
                    String title = entry.getValue();
                    Integer before = beforeComplete.get(tid);
                    int beforeNum = before == null ? 0 : before;
                    if (!freshOk) {
                        Log.other("芝麻信用💳[" + title + "]已上报，回读校验未执行");
                        continue;
                    }
                    String progress = freshProgress.get(tid);
                    if (progress == null) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#已不在待完成列表");
                        continue;
                    }
                    if ("DONE".equals(progress)) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#已标记完成");
                        continue;
                    }
                    int slash = progress.indexOf('/');
                    int nowNum = slash <= 0 ? 0 : Integer.parseInt(progress.substring(0, slash));
                    int needNum = slash <= 0 ? 1 : Integer.parseInt(progress.substring(slash + 1));
                    if (nowNum <= beforeNum) {
                        Log.other("芝麻信用💳[" + title + "]上报未生效#仍为(" + progress + "天)，今日不再重试");
                        Status.flagToday("AntMember::sesame::" + title);
                        // 这类任务服务端要求真实参与（push 的 promiseActivityExtCheck 校验），自动完成不了：
                        // 走"连续命中确认"（累计 3 次才真拉黑），避免把服务端异步未落状态的正常任务一次误杀
                        if (AutoMemberCreditSesameTaskList.getValue()) {
                            MessageUtil.MarkTaskBlackListConfirm("AntMember", "MemberCreditSesameTaskList", "芝麻粒任务", title);
                        }
                    } else if (nowNum >= needNum) {
                        Log.other("芝麻信用💳完成任务[" + title + "]#(" + progress + "天)");
                    } else {
                        // 多天任务：今天只推进一份，剩下的留给次日
                        Log.other("芝麻信用💳完成任务[" + title + "]#(" + progress + "天)，剩余次日继续");
                        Status.flagToday("AntMember::sesame::" + title);
                    }
                }
            }
            jo = MyUtils.newJSONObject(AntMemberRpcCall.queryCreditFeedback());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray creditFeedbackVOS = jo.optJSONArray("creditFeedbackVOS");
            if (!AntFarm.bindPigeonFeedback(creditFeedbackVOS)) return;
            if (creditFeedbackVOS != null && creditFeedbackVOS.length() != 0) {
                jo = MyUtils.newJSONObject(AntMemberRpcCall.collectAllCreditFeedback());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    String resultCode = jo.optString("resultCode");
                    Log.other("收芝麻粒🙇🏻‍♂️[一键收取]" + resultCode);
                }
            }
            
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    /**
     * 芝麻粒任务上报：官方链路为三段——joinActivity（拿 data.recordId）→ taskFeedback → pushActivity（带 recordId）。
     * <p>push 不是必需的：抓包里 zml_zhimajindou_15s 官方只发了 join + taskFeedback；需真实参与的游戏类任务
     * push 会被服务端以 promiseActivityExtCheck 校验拒掉，而实测这些任务 join + taskFeedback 之后就已经完成，
     * 所以 push 被拒只记一行，完成与否一律由调用方回读任务列表判定。
     * <p>join 被 PROMISE_HAS_PROCESSING_TEMPLATE 拒绝＝上一轮留下的"进行中"记录还挂着：
     * 此时用 queryLastOperateTask 取回那条记录的 recordId 继续推完，否则该任务当天再也进不来。
     */
    private void reportSesameTask(String taskTitle, String taskTemplateId) {
        try {
            String recordId = "";
            JSONObject joinJo = MyUtils.newJSONObject(AntMemberRpcCall.joinSesameTaskNew(taskTemplateId));
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joinJo)) {
                JSONObject joinData = joinJo.optJSONObject("data");
                recordId = joinData != null && joinData.opt("recordId") instanceof String ? joinData.optString("recordId") : "";
            } else {
                recordId = lastOperateRecordId(taskTemplateId);
                if (!StringUtil.isEmpty(recordId)) {
                    Log.other("芝麻信用💳[" + taskTitle + "]沿用进行中的记录#recordId=" + recordId);
                }
            }
            if (StringUtil.isEmpty(recordId)) {
                Log.other("芝麻信用💳上报[" + taskTitle + "]无可用记录#回读后按真实状态处理");
                return;
            }

            JSONObject feedbackJo = MyUtils.newJSONObject(AntMemberRpcCall.feedBackSesameTaskNew(taskTemplateId));
            TimeUtil.sleep(500);
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MemberCreditSesameTaskList", taskTitle, feedbackJo);
            if (!MessageUtil.checkResultCode(TAG, feedbackJo)) {
                Log.other("芝麻信用💳上报[" + taskTitle + "]taskFeedback未受理#回读后按真实状态处理");
                return;
            }

            JSONObject pushJo = MyUtils.newJSONObject(AntMemberRpcCall.finishSesameTask(recordId));
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, pushJo)) {
                Log.other("芝麻信用💳[" + taskTitle + "]push被拒#不影响完成判定");
            }
        } catch (Throwable t) {
            Log.err(TAG, "reportSesameTask err:", t);
        }
    }

    /**
     * 取回「最近一次操作任务」的 recordId：仅当它就是本任务且仍在进行中（finishFlag=false）时返回，否则 null。
     * <p>用途见 {@link #reportSesameTask}：join 被「存在进行中的生活记录」拒绝时，必须用原记录的 recordId 才能推完它。
     */
    private String lastOperateRecordId(String taskTemplateId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntMemberRpcCall.queryLastOperateTask());
            TimeUtil.sleep(300);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return null;
            }
            JSONObject data = jo.optJSONObject("data");
            JSONObject vo = data == null ? null : data.optJSONObject("lastOperateTaskVO");
            if (vo == null || !taskTemplateId.equals(vo.optString("templateId", ""))) {
                return null;
            }
            if (!Boolean.FALSE.equals(vo.opt("finishFlag"))) {
                return null;
            }
            if (!(vo.opt("recordId") instanceof String)) {
                return null;
            }
            String recordId = vo.optString("recordId", "");
            return StringUtil.isEmpty(recordId) ? null : recordId;
        } catch (Throwable t) {
            Log.err(TAG, "lastOperateRecordId err:", t);
        }
        return null;
    }

    private void CheckInTaskRpcManager() {
        if (Status.hasFlagToday("AntMember::zmlCheckIn")) {
            return;
        }
        // 领取是否失败：失败时不置今日标记，留给下一轮重试（否则当天不再重试 → 漏领）
        boolean claimFailed = false;
        try {
            
            String checkInRes = AntMemberRpcCall.alchemyQueryCheckIn("zml");
            JSONObject checkInJo = MyUtils.newJSONObject(checkInRes);
            if (MessageUtil.checkResultCode(TAG, checkInJo)) {
                JSONObject data = checkInJo.optJSONObject("data");
                if (data != null) {
                    JSONObject currentDay = data.optJSONObject("currentDateCheckInTaskVO");
                    if (currentDay != null) {
                        String status = currentDay.optString("status");
                        String checkInDate = currentDay.optString("checkInDate");
                        if ("CAN_COMPLETE".equals(status) && !checkInDate.isEmpty()) {
                            String completeRes = AntMemberRpcCall.zmCheckInCompleteTask(checkInDate, "zml");
                            try {
                                JSONObject completeJo = MyUtils.newJSONObject(completeRes);
                                if (MessageUtil.checkResultCode(TAG, completeJo)) {
                                    JSONObject prize = completeJo.optJSONObject("data");
                                    int num = 0;
                                    if (prize != null) {
                                        num = prize.optInt("zmlNum", prize.optJSONObject("prize") != null ? prize.optJSONObject("prize").optInt("num", 0) : 0);
                                    }
                                    Log.other("收芝麻粒🙇🏻‍♂️领取[每日签到成功]#获得" + num + "粒");
                                }
                                else {
                                    claimFailed = true;
                                    Log.error(".doSesameAlchemy#" + "签到失败:" + completeRes);
                                }
                            }
                            catch (Throwable e) {
                                claimFailed = true;
                                Log.printStackTrace(TAG + ".doSesameAlchemy.alchemyCheckInComplete", e);
                            }
                        }
                    }
                }
            }
            if (claimFailed) {
                Log.other("收芝麻粒🙇🏻‍♂️签到领取失败#本轮不置今日标记，稍后重试");
            }
            else {
                Status.flagToday("AntMember::zmlCheckIn");
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG + ".doSesameZmlCheckIn", t);
        }
    }
    
    // 我的快递任务
    private void RecommendTask() {
        try {
            // 调用 AntMemberRpcCall.queryRecommendTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryRecommendTask();
            JSONObject jsonResponse = MyUtils.newJSONObject(response);
            // 获取 taskDetailList 数组
            JSONArray taskDetailList = jsonResponse.optJSONArray("taskDetailList");
            // 遍历 taskDetailList
            for (int i = 0; taskDetailList != null && i < taskDetailList.length(); i++) {
                JSONObject taskDetail = taskDetailList.optJSONObject(i);
                if (taskDetail == null) {
                    continue;
                }
                // 检查 "canAccess" 的值是否为 true
                boolean canAccess = taskDetail.optBoolean("canAccess", false);
                if (!canAccess) {
                    // 如果 "canAccess" 不为 true，跳过
                    continue;
                }
                // 获取 taskMaterial 对象
                JSONObject taskMaterial = taskDetail.optJSONObject("taskMaterial");
                // 获取 taskBaseInfo 对象
                JSONObject taskBaseInfo = taskDetail.optJSONObject("taskBaseInfo");
                if (taskMaterial == null) {
                    continue;
                }
                // 获取 taskCode
                String taskCode = taskMaterial.optString("taskCode", "");
                // 根据 taskCode 执行不同的操作
                if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode) || "WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                    if ("WELFARE_PLUS_ANT_FOREST".equals(taskCode)) {
                        //String forestHomePageResponse = AntMemberRpcCall.queryforestHomePage();
                        //TimeUtil.sleep(2000);
                        String forestTaskResponse = AntMemberRpcCall.forestTask();
                        TimeUtil.sleep(500);
                        // 设备日报：KUAIDI_VITALITY 领奖每轮都失败（无原因，每天 11+ 次），失败后当天不再重复领
                        if (!Status.hasFlagToday("antMember::kuaidiForestAward")) {
                            JSONObject forestAward = MyUtils.newJSONObject(AntMemberRpcCall.forestreceiveTaskAward());
                            if (RpcRequestGuard.isFailure(forestAward) && !"RPC_SKIPPED".equals(forestAward.optString("error"))) {
                                Status.flagToday("antMember::kuaidiForestAward");
                            }
                        }
                    }
                    else if ("WELFARE_PLUS_ANT_OCEAN".equals(taskCode)) {
                        //String oceanHomePageResponse = AntMemberRpcCall.queryoceanHomePage();
                        //TimeUtil.sleep(2000);
                        String oceanTaskResponse = AntMemberRpcCall.oceanTask();
                        TimeUtil.sleep(500);
                        String oceanreceiveTaskAward = AntMemberRpcCall.oceanreceiveTaskAward();
                    }
                    if (taskBaseInfo != null) {
                        String appletName = taskBaseInfo.optString("appletName", "Unknown Applet");
                        Log.other("我的快递💌完成[" + appletName + "]");
                    }
                }
                if (taskMaterial == null || !taskMaterial.has("taskId")) {
                    // 如果 taskMaterial 为 null 或者不包含 taskId，跳过
                    continue;
                }
                // 获取 taskId
                String taskId = taskMaterial.optString("taskId");
                // 调用 trigger 方法
                String triggerResponse = AntMemberRpcCall.trigger(taskId);
                JSONObject triggerResult = MyUtils.newJSONObject(triggerResponse);
                // 检查 success 字段
                boolean success = triggerResult.optBoolean("success");
                if (success) {
                    // 从 triggerResponse 中获取 prizeSendInfo 数组
                    JSONArray prizeSendInfo = triggerResult.optJSONArray("prizeSendInfo");
                    JSONObject prizeInfo = prizeSendInfo != null && prizeSendInfo.length() > 0 ? prizeSendInfo.optJSONObject(0) : null;
                    JSONObject extInfo = prizeInfo != null ? prizeInfo.optJSONObject("extInfo") : null;
                    if (extInfo != null) {
                        // 获取 promoCampName
                        String promoCampName = extInfo.optString("promoCampName", "Unknown Promo Campaign");
                        // 输出日志信息
                        Log.other("我的快递💌完成[" + promoCampName + "]");
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.err(TAG, "RecommendTask err:", th);
        }
    }
    
    private void OrdinaryTask() {
        try {
            // 调用 AntMemberRpcCall.queryOrdinaryTask() 获取 JSON 数据
            String response = AntMemberRpcCall.queryOrdinaryTask();
            JSONObject jsonResponse = MyUtils.newJSONObject(response);
            // 检查是否请求成功
            if (jsonResponse.optBoolean("success")) {
                // 获取任务详细列表
                JSONArray taskDetailList = jsonResponse.optJSONArray("taskDetailList");
                // 遍历任务详细列表
                for (int i = 0; taskDetailList != null && i < taskDetailList.length(); i++) {
                    // 获取当前任务对象
                    JSONObject task = taskDetailList.optJSONObject(i);
                    if (task == null) {
                        continue;
                    }
                    // 提取任务 ID、处理状态和触发类型
                    String taskId = task.optString("taskId");
                    String taskProcessStatus = task.optString("taskProcessStatus");
                    String sendCampTriggerType = task.optString("sendCampTriggerType");
                    // 检查任务状态和触发类型，执行触发操作
                    if (!"RECEIVE_SUCCESS".equals(taskProcessStatus) && !"EVENT_TRIGGER".equals(sendCampTriggerType)) {
                        // 调用 signuptrigger 方法
                        String signuptriggerResponse = AntMemberRpcCall.signuptrigger(taskId);
                        // 调用 sendtrigger 方法
                        String sendtriggerResponse = AntMemberRpcCall.sendtrigger(taskId);
                        // 解析 sendtriggerResponse
                        JSONObject sendTriggerJson = MyUtils.newJSONObject(sendtriggerResponse);
                        // 判断任务是否成功
                        if (sendTriggerJson.optBoolean("success")) {
                            // 从 sendtriggerResponse 中获取 prizeSendInfo 数组
                            JSONArray prizeSendInfo = sendTriggerJson.optJSONArray("prizeSendInfo");
                            JSONObject firstPrize = prizeSendInfo != null && prizeSendInfo.length() > 0 ? prizeSendInfo.optJSONObject(0) : null;
                            if (firstPrize != null) {
                                // 获取 prizeName
                                String prizeName = firstPrize.optString("prizeName");
                                Log.other("我的快递💌完成[" + prizeName + "]");
                            }
                        }
                        else {
                            Log.i(TAG, "sendtrigger failed for taskId: " + taskId);
                        }
                        TimeUtil.sleep(1000);
                    }
                }
            }
        }
        catch (Throwable th) {
            Log.err(TAG, "OrdinaryTask err:", th);
        }
    }
}
