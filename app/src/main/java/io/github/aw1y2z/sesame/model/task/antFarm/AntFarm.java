package io.github.aw1y2z.sesame.model.task.antFarm;

import android.os.Build;

import io.github.aw1y2z.sesame.entity.AlipayAntFarmDoFarmTaskList;
import io.github.aw1y2z.sesame.entity.AlipayAntFarmDrawMachineTaskList;
import io.github.aw1y2z.sesame.entity.GameCenterMallItem;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.model.task.antMember.AntMemberRpcCall;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.GameCenterMallItemMap;
import lombok.Getter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.util.MyUtils;

import io.github.aw1y2z.sesame.data.*;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.data.modelFieldExt.*;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.entity.CustomOption;
import io.github.aw1y2z.sesame.entity.FarmOrnaments;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle;
import io.github.aw1y2z.sesame.model.normal.answerAI.AnswerAI;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.FarmOrnamentsIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AntFarm extends ModelTask {
    private static final String TAG = AntFarm.class.getSimpleName();
    /** 家庭分享：当日累计"邀请全部失败"次数 */
    private static final String FLAG_FAMILY_SHARE_FAIL_COUNT = "antFarm::familyShareToFriends::failCount";
    /** 饲料任务一次执行最多循环几轮（多阶段任务每阶段一轮，如“试玩庄园火爆小游戏”8 阶段×30g，再加领奖轮） */
    private static final int MAX_FARM_TASK_ROUNDS = 20;
    /** 家庭分享：当日最多尝试几次，超过后当天不再重试（避免每轮任务都重发邀请请求） */
    private static final int MAX_FAMILY_SHARE_ATTEMPT = 3;

    /**
     * 公益捐蛋当日标记：当天已经捐过一次公益蛋。只归「每日捐蛋」自己用。
     * <p>持久化 key，**不能改名**。
     */
    private static final String FLAG_CHARITY_DONATION_DONE = "farm::donation";

    /**
     * S2 捐蛋「今日已捐成」标记：只归「爱心鸡结号 | 自动捐蛋」自己用。
     * <p>持久化 key，**不能改名**。
     */
    private static final String FLAG_COMPETITION_DONATED_TODAY = "antFarm::competitionDonatedToday";

    /**
     * S2 捐蛋「今日已尝试」标记：每天最多给 S2 发一次捐蛋请求。
     * <p>有了它，响应没判定成功时不会在后续每轮运行里重发（未知结果也不再回退公益）。
     * <p>持久化 key，**不能改名**。
     */
    private static final String FLAG_COMPETITION_DONATE_TRIED = "antFarm::competitionDonateTried";

    /**
     * 小鸡乐园「刷任务」当日跳过标记：上报后服务端未刷新出新的宝箱次数时当天不再刷，
     * 避免每轮运行都重发一次任务上报。持久化 key，**不能改名**。
     */
    private static final String FLAG_GAME_DRAW_TASK_SKIP = "antFarm::gameDrawTaskSkip";

    /** 投喂被服务端拒绝的「饲料槽已满」结果码（按码判据，不认文案） */
    private static final String CODE_FEED_TROUGH_FULL = "331";
    /** 小鸡所在空间标识：家庭空间。睡觉/起床靠它区分走家庭接口还是个人小屋接口 */
    private static final String SPACE_TYPE_CHICK_FAMILY = "ChickFamily";

    private String ownerFarmId;
    private String ownerUserId;
    private String ownerGroupId;
    private Animal[] animals;
    private Animal ownerAnimal = new Animal();
    private int foodStock;
    private int foodStockLimit;
    private String rewardProductNum;
    private RewardFriend[] rewardList;
    private double benevolenceScore;
    private double harvestBenevolenceScore;
    private int unReceiveTaskAward = 0;
    /**
     * 本轮是否已被服务端限流（领奖返回 102）。
     * <p>命中后本轮不再继续领其余饲料任务：服务端对领奖的 102 是临时性的，
     * 一轮里连着刷 3 次只是白刷（每轮开头重置，见 {@link #run()}）。
     */
    private boolean farmTaskAwardBusy = false;
    /**
     * 本轮投喂时服务端回了「饲料槽已满」(结果码见 CODE_FEED_TROUGH_FULL)：本轮不再投喂；
     * 槽满后每 10 秒级的重试只是白打接口，每轮开头重置 (见 run())。
     */
    private boolean feedTroughFullThisRun = false;
    private double finalScore = 0d;
    private int foodInTrough = 0;

    private FarmTool[] farmTools;

    @Override
    public String getName() {
        return "庄园";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FARM;
    }

    private BooleanModelField AutoAntFarmDoFarmTaskList;
    private SelectModelField AntFarmDoFarmTaskList;
    private StringModelField sleepTime;
    private IntegerModelField sleepMinutes;
    private BooleanModelField enableSleep;      // 小鸡睡觉开关
    private BooleanModelField feedAnimal;
    private BooleanModelField rewardFriend;
    private ChoiceModelField sendBackAnimalWay;
    private ChoiceModelField sendBackAnimalType;
    private SelectModelField sendBackAnimalList;
    private ChoiceModelField recallAnimalType;
    private BooleanModelField receiveFarmToolReward;
    private BooleanModelField recordFarmGame;
    private ListModelField.ListJoinCommaToStringModelField farmGameTime;
    private BooleanModelField gameCenterBuyMallItem;
    private SelectAndCountModelField gameCenterBuyMallItemList;
    private BooleanModelField kitchen;
    private BooleanModelField useSpecialFood;
    @Getter
    private IntegerModelField useSpecialFoodCountLimit;
    private BooleanModelField dynamicSpecialFood;
    private IntegerModelField dynamicFoodDailyLimit;
    private BooleanModelField rankingFoodRefill;
    private boolean rankingFoodRefillBusy;
    private BooleanModelField useNewEggTool;
    private BooleanModelField useFullRewardTool;
    private BooleanModelField rewardStealTool;
    private BooleanModelField rewardShareTool;
    private SelectModelField rewardOrnamentTools;
    private IntegerModelField rewardExtraToolDailyLimit;
    private BooleanModelField harvestProduce;
    private ChoiceModelField donationType;
    private IntegerModelField donationAmount;
    private BooleanModelField receiveFarmTaskAward;
    private BooleanModelField useAccelerateTool;
    @Getter
    private IntegerModelField accelerateToolDailyLimit;
    private SelectModelField useAccelerateToolOptions;
    private BooleanModelField feedFriendAnimal;
    private SelectAndCountModelField feedFriendAnimalList;
    private ChoiceModelField notifyFriendType;
    private SelectModelField notifyFriendList;
    private BooleanModelField acceptGift;
    private SelectAndCountModelField visitFriendList;
    private BooleanModelField chickenDiary;
    private ChoiceModelField collectChickenDiary;
    private BooleanModelField useFenceTool;
    private BooleanModelField useDollTool;
    private ChoiceModelField dollSupplementOrder;
    private BooleanModelField drawMachine;
    private BooleanModelField AutoAntFarmDrawMachineTaskList;
    private SelectModelField AntFarmDrawMachineTaskList;
    private BooleanModelField IPexchangeBenefit;
    private BooleanModelField ornamentsDressUp;
    private SelectModelField ornamentsDressUpList;
    private IntegerModelField ornamentsDressUpDays;
    private ChoiceModelField hireAnimalType;
    private SelectModelField hireAnimalList;
    private ChoiceModelField farmNpcType;
    private static final String[] FARM_NPC_NAMES = {"关闭", "黄金鸡", "农场小鸡", "芝麻大表鸽"};
    private static final String[] FARM_NPC_IDS = {"", "20250725105101013088000000000004", "20250613105101013088000000000002", "20250901105101013088000000000006"};
    private static final String[] FARM_NPC_SOURCES = {"", "licaixiaoji_2025_1", "feiliaoji_202507", "zhimaxiaoji_lianjin"};
    private static final String PIGEON_TEMPLATE = "hjwf_myzy_gyxj_erfang";
    private static final String PIGEON_CATEGORY = "ZMZY#FEED_ZM_CHICKEN";
    private static final String PIGEON_RECEIPT_KEY = "farmNpcPigeonReceipt";
    private BooleanModelField drawGameCenterAward;
    private BooleanModelField competition;                    // 爱心鸡结号(S2) | 开启
    private BooleanModelField competitionReceiveTask;          // 爱心鸡结号 | 领取奖励
    private BooleanModelField competitionDonate;               // 爱心鸡结号 | 自动捐蛋
    private BooleanModelField competitionStealRank;            // 爱心鸡结号 | 偷榜
    private IntegerModelField competitionStealMinutes;         // 爱心鸡结号 | 偷榜提前分钟数
    private IntegerModelField competitionStealLimit;
    private IntegerModelField competitionTargetRank;          // 爱心鸡结号 | 偷榜目标名次
    private IntegerModelField competitionDonateAmount;         // 爱心鸡结号 | 自动捐蛋数量
    private BooleanModelField rankingDonation;
    private BooleanModelField rankingStable;
    private IntegerModelField rankingDailyBudget;
    private IntegerModelField rankingWeeklyBudget;
    private StringModelField rankingDonationTime;
    private BooleanModelField rankingWatch;
    private IntegerModelField rankingWatchInterval;
    private String rankingWatchChildId;
    private BooleanModelField useBigEaterTool;
    //private ChoiceModelField getFeedType;
    private SelectModelField getFeedList;
    private BooleanModelField family;
    private SelectModelField familyOptions;
    private ChoiceModelField familyAssignStrategy, familyShareMode;
    private SelectModelField familyShareList;
    private SelectModelField notInviteList; // 新增：不邀请列表

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(receiveFarmTaskAward = new BooleanModelField("receiveFarmTaskAward", "饲料任务及奖励", false));
        modelFields.addField(AutoAntFarmDoFarmTaskList = new BooleanModelField("AutoAntFarmDoFarmTaskList", "庄园饲料 | 自动黑名单", true).setDependsOn("receiveFarmTaskAward"));
        modelFields.addField(AntFarmDoFarmTaskList = new SelectModelField("AntFarmDoFarmTaskList", "庄园饲料 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDoFarmTaskList::getList).setDependsOn("AutoAntFarmDoFarmTaskList"));
        modelFields.addField(useNewEggTool = new BooleanModelField("useNewEggTool", "新蛋卡 | 使用", false));
        modelFields.addField(useFullRewardTool = new BooleanModelField("useFullRewardTool", "道具奖励 | 满仓先用一张再领", false).setDescription("按已开启工具策略腾一格；每次只用一张，库存或效果未确认时保留奖励。蹭饭/救济须开启下方缺粮策略，装扮须明确选择等级并允许服务端选择新套装"));
        modelFields.addField(rewardStealTool = new BooleanModelField("rewardStealTool", "奖励腾位 | 自家缺粮使用蹭饭卡", false).setDependsOn("useFullRewardTool").setDescription("仅自家小鸡在家饥饿且库存与食槽合计不足一餐180g时提交一张；蹭饭去向由服务端选择，回查粮食增加或小鸡已到其他庄园蹭饭才确认"));
        modelFields.addField(rewardShareTool = new BooleanModelField("rewardShareTool", "奖励腾位 | 自家缺粮使用救济卡", false).setDependsOn("useFullRewardTool").setDescription("仅自家小鸡在家饥饿且粮食不足180g时提交一张；回查粮食增加才确认"));
        modelFields.addField(rewardOrnamentTools = new SelectModelField("rewardOrnamentTools", "奖励腾位 | 服务端选择新套装的卡等级", new LinkedHashSet<>(), () -> Arrays.asList(
                new CustomOption("ORDINARY_ORNAMENT_TOOL", "普通装扮补签卡"), new CustomOption("ADVANCE_ORNAMENT_TOOL", "高级装扮补签卡"),
                new CustomOption("RARE_ORNAMENT_TOOL", "稀有装扮补签卡"))).setDependsOn("useFullRewardTool").setDescription("默认不选；只在实时列表存在未拥有套装且所选卡有库存时尝试，等级资格及具体套装由服务端决定，不保证可用；库存减少一张且原未拥有套装转为已获得才确认"));
        modelFields.addField(rewardExtraToolDailyLimit = new IntegerModelField("rewardExtraToolDailyLimit", "奖励腾位 | 蹭饭/救济/装扮每日总预算(0不用)", 0, 0, 5).setDependsOn("useFullRewardTool").setDescription("上述五类合计份数，包括未确认提交；每类每天最多尝试一张。未知结果持久冻结这些工具的自动腾位，跨重启及跨日保持"));
        modelFields.addField(useFenceTool = new BooleanModelField("useFenceTool", "篱笆卡 | 自动使用", false));
        modelFields.addField(useDollTool = new BooleanModelField("useDollTool", "数字公仔补签卡 | 自动补签", false));
        modelFields.addField(dollSupplementOrder = new ChoiceModelField("dollSupplementOrder", "数字公仔补签 | 顺序", 0, new String[]{"从早到晚", "从晚到早"}).setDependsOn("useDollTool"));
        modelFields.addField(collectChickenDiary = new ChoiceModelField("collectChickenDiary", "小鸡日记 | 点赞范围", 0, new String[]{"关闭", "今日", "当月", "全部历史"}));
        modelFields.addField(useAccelerateTool = new BooleanModelField("useAccelerateTool", "加速卡 | 使用", false));
        modelFields.addField(accelerateToolDailyLimit = new IntegerModelField("accelerateToolDailyLimit", "加速卡 | 每日上限(-1不限,0不用)", 8, -1, 1000).setDependsOn("useAccelerateTool"));
        modelFields.addField(useAccelerateToolOptions = new SelectModelField("useAccelerateToolOptions", "加速卡 | 选项", new LinkedHashSet<>(), CustomOption::getUseAccelerateToolOptions).setDependsOn("useAccelerateTool"));
        modelFields.addField(useBigEaterTool = new BooleanModelField("useBigEaterTool", "加饭卡 | 使用", false));
        modelFields.addField(useSpecialFood = new BooleanModelField("useSpecialFood", "特殊食品 | 使用", false));
        modelFields.addField(useSpecialFoodCountLimit = new IntegerModelField("useSpecialFoodCountLimit", "特殊食品 | " + "使用上限(无限:0)", 0).setDependsOn("useSpecialFood"));
        modelFields.addField(dynamicSpecialFood = new BooleanModelField("dynamicSpecialFood", "特殊食品 | 动态批量与收益学习", false).setDependsOn("useSpecialFood"));
        modelFields.addField(dynamicFoodDailyLimit = new IntegerModelField("dynamicFoodDailyLimit", "动态美食 | 每日份数预算(0不用)", 0, 0, 1000).setDependsOn("dynamicSpecialFood"));
        modelFields.addField(rankingFoodRefill = new BooleanModelField("rankingFoodRefill", "捐蛋排位 | 按缺蛋量补美食", false).setDependsOn("rankingDonation").setDescription("须同时开启特殊食品、动态批量并设置正数美食预算；先单份学习未知收益，再按目标缺额批量，扣减和产蛋收益均须回查确认；不新增自动用卡"));
        modelFields.addField(rewardFriend = new BooleanModelField("rewardFriend", "打赏好友", false));
        modelFields.addField(recallAnimalType = new ChoiceModelField("recallAnimalType", "召回小鸡", RecallAnimalType.ALWAYS, RecallAnimalType.nickNames));
        modelFields.addField(feedAnimal = new BooleanModelField("feedAnimal", "投喂小鸡", false));
        modelFields.addField(feedFriendAnimal = new BooleanModelField("feedFriendAnimal", "帮喂小鸡 | 开启", true));
        modelFields.addField(feedFriendAnimalList = new SelectAndCountModelField("feedFriendAnimalList", "帮喂小鸡 | " + "好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写帮喂次数(每日)", 1, 100).setDependsOn("feedFriendAnimal"));
        modelFields.addField(hireAnimalType = new ChoiceModelField("hireAnimalType", "雇佣小鸡 | 动作", HireAnimalType.NONE, HireAnimalType.nickNames));
        modelFields.addField(hireAnimalList = new SelectModelField("hireAnimalList", "雇佣小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("hireAnimalType"));
        modelFields.addField(farmNpcType = new ChoiceModelField("farmNpcType", "NPC小鸡 | 雇佣类型", 0, FARM_NPC_NAMES));
        modelFields.addField(sendBackAnimalWay = new ChoiceModelField("sendBackAnimalWay", "遣返小鸡 | 方式", SendBackAnimalWay.NORMAL, SendBackAnimalWay.nickNames));
        modelFields.addField(sendBackAnimalType = new ChoiceModelField("sendBackAnimalType", "遣返小鸡 | 动作", SendBackAnimalType.NONE, SendBackAnimalType.nickNames));
        modelFields.addField(sendBackAnimalList = new SelectModelField("sendFriendList", "遣返小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("sendBackAnimalType"));
        modelFields.addField(notifyFriendType = new ChoiceModelField("notifyFriendType", "通知赶鸡 | 动作", NotifyFriendType.NONE, NotifyFriendType.nickNames));
        modelFields.addField(notifyFriendList = new SelectModelField("notifyFriendList", "通知赶鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("notifyFriendType"));
        modelFields.addField(ornamentsDressUp = new BooleanModelField("ornamentsDressUp", "装扮焕新 | 开启", false));
        modelFields.addField(ornamentsDressUpList = new SelectModelField("ornamentsDressUpList", "装扮焕新 | 套装列表", new LinkedHashSet<>(), FarmOrnaments::getList).setDependsOn("ornamentsDressUp"));
        modelFields.addField(ornamentsDressUpDays = new IntegerModelField("ornamentsDressUpDays", "装扮焕新 | 焕新频率(天)", 7, 1, 30).setDependsOn("ornamentsDressUp"));
        modelFields.addField(drawMachine = new BooleanModelField("drawMachine", "装扮抽抽乐", false));
        modelFields.addField(AutoAntFarmDrawMachineTaskList = new BooleanModelField("AutoAntFarmDrawMachineTaskList", "抽抽乐 | 自动黑名单", true).setDependsOn("drawMachine"));
        modelFields.addField(AntFarmDrawMachineTaskList = new SelectModelField("AntFarmDrawMachineTaskList", "抽抽乐 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDrawMachineTaskList::getList).setDependsOn("AutoAntFarmDrawMachineTaskList"));
        modelFields.addField(IPexchangeBenefit = new BooleanModelField("IPexchangeBenefit", "抽抽乐兑换 | 开启", false));
        modelFields.addField(donationType = new ChoiceModelField("donationType", "每日捐蛋 | 方式", DonationType.ZERO, DonationType.nickNames));
        modelFields.addField(donationAmount = new IntegerModelField("donationAmount", "每日捐蛋 | 倍数(每项)", 1).setDependsOn("donationType"));
        modelFields.addField(competition = new BooleanModelField("competition", "爱心鸡结号 | 开启", false));
        modelFields.addField(competitionReceiveTask = new BooleanModelField("competitionReceiveTask", "爱心鸡结号 | 领取奖励", false).setDependsOn("competition"));
        modelFields.addField(competitionDonate = new BooleanModelField("competitionDonate", "爱心鸡结号 | 自动捐蛋", false).setDependsOn("competition"));
        modelFields.addField(competitionDonateAmount = new IntegerModelField("competitionDonateAmount", "爱心鸡结号 | 自动捐蛋数量", 5, 0, 1000).setDependsOn("competitionDonate"));
        modelFields.addField(competitionStealRank = new BooleanModelField("competitionStealRank", "爱心鸡结号 | 偷榜", false).setDependsOn("competition"));
        modelFields.addField(competitionStealMinutes = new IntegerModelField("competitionStealMinutes", "爱心鸡结号 | 偷榜提前分钟数", 30, 0, 240).setDependsOn("competitionStealRank"));
        modelFields.addField(competitionStealLimit = new IntegerModelField("competitionStealLimit", "爱心鸡结号 | 偷榜捐献上限(0不限)", 0, 0, 1000).setDependsOn("competitionStealRank"));
        modelFields.addField(competitionTargetRank = new IntegerModelField("competitionTargetRank", "爱心鸡结号 | 偷榜目标名次", 1, 1, 100)
                .setDescription("默认第1名沿用原策略。已达到目标不捐；只按当前榜单真实目标行计算差额+1，目标行缺失不猜测。仍守原偷榜上限和开启的日周预算。")
                .setDependsOn("competitionStealRank"));
        modelFields.addField(rankingDonation = new BooleanModelField("rankingDonation", "捐蛋排位 | 持久预算与每日排位", false).setDescription("开启后每日排位与现有周赛自动捐蛋/偷榜共享预算；预算0不捐。未知捐赠保留额度与尝试记录，禁止重发；只处理可验证的本人排行"));
        modelFields.addField(rankingDailyBudget = new IntegerModelField("rankingDailyBudget", "捐蛋排位 | 每日总预算", 0, 0, 1000).setDependsOn("rankingDonation"));
        modelFields.addField(rankingWeeklyBudget = new IntegerModelField("rankingWeeklyBudget", "捐蛋排位 | 每周总预算", 0, 0, 10000).setDependsOn("rankingDonation"));
        modelFields.addField(rankingStable = new BooleanModelField("rankingStable", "每日排位 | 稳定星数计划", true).setDependsOn("rankingDonation"));
        modelFields.addField(rankingDonationTime = new StringModelField("rankingDonationTime", "每日排位 | 检查时间(HHmm，20点前)", "1958").setDependsOn("rankingDonation"));
        modelFields.addField(rankingWatch = new BooleanModelField("rankingWatch", "每日排位 | 持续追榜", false).setDependsOn("rankingDonation").setDescription("从检查时间起按间隔回查，20点前2秒最后检查；预算内且上一笔已同轮次回查确认才允许新捐赠"));
        modelFields.addField(rankingWatchInterval = new IntegerModelField("rankingWatchInterval", "每日排位 | 追榜间隔(秒)", 10, 1, 300).setDependsOn("rankingWatch"));
        modelFields.addField(family = new BooleanModelField("family", "亲密家庭 | 开启", false));
        modelFields.addField(familyOptions = new SelectModelField("familyOptions", "亲密家庭 | 选项", new LinkedHashSet<>(), CustomOption::getAntFarmFamilyOptions).setDependsOn("family"));
        modelFields.addField(notInviteList = new SelectModelField("notInviteList", "亲密家庭 | 不邀请列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("family"));
        modelFields.addField(familyAssignStrategy = new ChoiceModelField("familyAssignStrategy", "亲密家庭 | 顶梁柱安排策略", 0,
                new String[]{"随机安排", "优先今日亲密值最低"}).setDependsOn("family"));
        modelFields.addField(familyShareMode = new ChoiceModelField("familyShareMode", "亲密家庭 | 好友分享动作", 1,
                new String[]{"仅邀请选中好友", "不邀请选中好友"}).setDependsOn("family"));
        modelFields.addField(familyShareList = new SelectModelField("familyShareList", "亲密家庭 | 好友分享名单", new LinkedHashSet<>(), AlipayUser::getList)
                .setDescription("仍需家庭选项中的分享开关；仅邀请模式空名单不发送。不邀请模式沿用原行为，原不邀请列表始终优先；只选择当前好友且排除自己和家庭成员，每轮最多2人。")
                .setDependsOn("family"));
        modelFields.addField(enableSleep = new BooleanModelField("enableSleep", "小鸡起床 | 自动起床（自动睡觉已禁用）", false));
        modelFields.addField(sleepTime = new StringModelField("sleepTime", "小鸡起床 | 入睡参考时间", "2001").setDependsOn("enableSleep"));
        modelFields.addField(sleepMinutes = new IntegerModelField("sleepMinutes", "小鸡起床 | 睡眠时长(分钟)", 10 * 59, 1, 10 * 60).setDependsOn("enableSleep"));
        modelFields.addField(recordFarmGame = new BooleanModelField("recordFarmGame", "小鸡乐园 | 游戏改分(星星球、登山赛、飞行赛、揍小鸡)", false));
        List<String> farmGameTimeList = new ArrayList<>();
        farmGameTimeList.add("2200-2400");
        modelFields.addField(farmGameTime = new ListModelField.ListJoinCommaToStringModelField("farmGameTime", "小鸡乐园 " + "| 游戏时间(范围)", farmGameTimeList).setDependsOn("recordFarmGame"));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "小鸡乐园 | 游戏宝箱", false));
        modelFields.addField(gameCenterBuyMallItem = new BooleanModelField("gameCenterBuyMallItem", "小鸡乐园 | 乐园集市", false));
        // 兑奖次数下限设为 1：勾选即至少兑奖 1 次，避免默认 0 导致勾选不生效
        modelFields.addField(gameCenterBuyMallItemList = new SelectAndCountModelField("gameCenterBuyMallItemList", "小鸡乐园 | 兑奖", new LinkedHashMap<>(), GameCenterMallItem::getList, "请填写兑奖次数(每日)", 1, 100).setDependsOn("gameCenterBuyMallItem"));
        modelFields.addField(kitchen = new BooleanModelField("kitchen", "小鸡厨房", false));
        modelFields.addField(chickenDiary = new BooleanModelField("chickenDiary", "小鸡日记", false));
        modelFields.addField(harvestProduce = new BooleanModelField("harvestProduce", "收取爱心鸡蛋", false));
        modelFields.addField(receiveFarmToolReward = new BooleanModelField("receiveFarmToolReward", "收取道具奖励", false));
        //modelFields.addField(getFeedType = new ChoiceModelField("getFeedType", "一起拿饲料 | 动作", GetFeedType.NONE, GetFeedType.nickNames));
        //modelFields.addField(getFeedList = new SelectModelField("getFeedList", "一起拿饲料 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(acceptGift = new BooleanModelField("acceptGift", "收麦子", false));
        modelFields.addField(visitFriendList = new SelectAndCountModelField("visitFriendList", "送麦子 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写赠送次数(每日)", 1, 100));
        return modelFields;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        super.boot(classLoader);
        RpcIntervalLimit.addIntervalLimit("com.alipay.antfarm.enterFarm", 2000);
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️蚂蚁庄园:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    protected boolean supportsManualAction(String action) {
        return "sendBack".equals(action) || "game".equals(action) || "chouchoule".equals(action)
                || "specialFood".equals(action) || "useTool".equals(action);
    }

    private boolean manualFarmOwner(String uid) {
        if (Thread.currentThread().isInterrupted()) throw new TaskCancelledException();
        return uid != null && !uid.isEmpty() && uid.equals(UserIdMap.getCurrentUid()) && isEnable() && check();
    }

    /** Manual actions need fresh state without enterFarm's configured automatic gifts/food/manure. */
    private JSONArray initManualFarm(String uid) throws JSONException {
        if (!manualFarmOwner(uid)) return null;
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", uid));
        JSONObject farm = response.optJSONObject("farmVO");
        JSONObject master = farm == null ? null : farm.optJSONObject("masterUserInfoVO");
        JSONObject sub = farm == null ? null : farm.optJSONObject("subFarmVO");
        JSONArray rows = sub == null ? null : sub.optJSONArray("animals");
        if (!MessageUtil.checkMemo(TAG, response) || !manualFarmOwner(uid) || master == null || !uid.equals(master.opt("userId"))
                || sub == null || !(sub.opt("farmId") instanceof String) || sub.optString("farmId").isEmpty()
                || rows == null || rows.length() > 1000) return null;
        String farmId = sub.optString("farmId");
        Animal[] fresh = new Animal[rows.length()];
        Animal own = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            JSONObject status = row == null ? null : row.optJSONObject("animalStatusVO");
            if (row == null || status == null || !(row.opt("masterFarmId") instanceof String)
                    || row.optString("masterFarmId").isEmpty() || !(row.opt("animalId") instanceof String)
                    || row.optString("animalId").isEmpty() || !(row.opt("currentFarmId") instanceof String)
                    || row.optString("currentFarmId").isEmpty()) return null;
            Animal animal = new Animal();
            animal.animalId = row.optString("animalId"); animal.masterFarmId = row.optString("masterFarmId");
            animal.currentFarmId = row.optString("currentFarmId"); animal.subAnimalType = row.optString("subAnimalType");
            animal.animalBuff = row.optString("animalBuff"); animal.startEatTime = row.optLong("startEatTime");
            animal.consumeSpeed = row.optDouble("consumeSpeed", Double.NaN);
            animal.animalFeedStatus = status.optString("animalFeedStatus"); animal.animalInteractStatus = status.optString("animalInteractStatus");
            fresh[i] = animal;
            if (farmId.equals(animal.masterFarmId)) { if (own != null) return null; own = animal; }
        }
        if (own == null || !manualFarmOwner(uid)) return null;
        ownerUserId = uid; ownerFarmId = farmId; ownerAnimal = own; animals = fresh;
        foodStock = farm.optInt("foodStock"); foodStockLimit = farm.optInt("foodStockLimit");
        foodInTrough = rankingInt(sub, "foodInTrough");
        harvestBenevolenceScore = foodNumber(farm, "harvestBenevolenceScore");
        benevolenceScore = foodNumber(sub.optJSONObject("farmProduce"), "benevolenceScore");
        JSONArray cuisines = response.optJSONArray("cuisineList");
        return cuisines == null ? new JSONArray() : cuisines;
    }

    @Override
    protected void runManualAction(String action) {
        if (!supportsManualAction(action)) return;
        String uid = UserIdMap.getCurrentUid();
        try {
            JSONArray cuisines = initManualFarm(uid);
            if (cuisines == null || !manualFarmOwner(uid)) return;
            switch (action) {
                case "sendBack":
                    if (sendBackAnimalType.getValue() != SendBackAnimalType.NONE) sendBackAnimal();
                    break;
                case "game":
                    if (recordFarmGame.getValue()) {
                        for (GameType game : new GameType[]{GameType.starGame, GameType.jumpGame, GameType.flyGame, GameType.hitGame}) {
                            if (!manualFarmOwner(uid) || !recordFarmGame.getValue()) return;
                            recordFarmGame(game);
                        }
                    }
                    break;
                case "chouchoule":
                    if (drawMachine.getValue()) drawMachineGroups();
                    break;
                case "specialFood":
                    if (useSpecialFood.getValue() && AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)
                            && (AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)
                            || AnimalFeedStatus.HUNGRY.name().equals(ownerAnimal.animalFeedStatus))) {
                        // Manual consumption also requires a finite existing count or dynamic daily budget.
                        if (dynamicSpecialFood.getValue() || useSpecialFoodCountLimit.getValue() > 0) useFarmFood(cuisines);
                        else Log.record("手动美食：请先配置正数使用上限或动态每日预算");
                    }
                    break;
                case "useTool":
                    if (useNewEggTool.getValue() && manualFarmOwner(uid)) useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                    if (useFenceTool.getValue() && manualFarmOwner(uid)) useFenceTool();
                    if (useDollTool.getValue() && manualFarmOwner(uid)) supplementDolls();
                    if (useAccelerateTool.getValue() && manualFarmOwner(uid) && AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)
                            && AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) useAccelerateTool();
                    if (useBigEaterTool.getValue() && manualFarmOwner(uid) && AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)
                            && AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) useFarmTool(ownerFarmId, ToolType.BIG_EATER_TOOL);
                    break;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable e) { Log.err(TAG, "runManualAction", e); }
    }

    @Override
    public void run() {
        try {
            farmTaskAwardBusy = false;
            feedTroughFullThisRun = false;
            if (enterFarm() == null) {
                return;
            }

            //初始任务列表
            step("初始任务列表", () -> {
                if (!Status.hasFlagToday("BlackList::initAntFarm")) {
                    initAntFarmTaskListMap(AutoAntFarmDoFarmTaskList.getValue(), AutoAntFarmDrawMachineTaskList.getValue(), drawMachine.getValue());
                    Status.flagToday("BlackList::initAntFarm");
                }
            });

            step("奖励好友", () -> {
                if (rewardFriend.getValue()) {
                    rewardFriend();
                }
            });

            step("遣返小鸡", () -> {
                if (sendBackAnimalType.getValue() != SendBackAnimalType.NONE) {
                    sendBackAnimal();
                }
            });

            step("小鸡不在家处理", () -> {
                if (!AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)) {
                    if ("ORCHARD".equals(ownerAnimal.locationType)) {
                        Log.farm("庄园通知📣[你家的小鸡给拉去除草了！]");
                        JSONObject joRecallAnimal = MyUtils.newJSONObject(AntFarmRpcCall.orchardRecallAnimal(ownerAnimal.animalId, ownerAnimal.currentFarmMasterUserId));
                        int manureCount = joRecallAnimal.optInt("manureCount");
                        Log.farm("召回小鸡📣收获[" + manureCount + "g肥料]");
                    } else {
                        syncAnimalStatusAtOtherFarm(ownerAnimal.currentFarmId);
                        boolean guest = false;
                        switch (SubAnimalType.valueOf(ownerAnimal.subAnimalType)) {
                            case GUEST:
                                guest = true;
                                Log.record("小鸡到好友家去做客了");
                                break;
                            case NORMAL:
                                Log.record("小鸡太饿，离家出走了");
                                break;
                            case PIRATE:
                                Log.record("小鸡外出探险了");
                                break;
                            case WORK:
                                Log.record("小鸡出去工作啦");
                                break;
                            default:
                                Log.record("小鸡不在庄园" + " " + ownerAnimal.subAnimalType);
                        }

                        boolean hungry = false;
                        String userName = UserIdMap.getMaskName(AntFarmRpcCall.farmId2UserId(ownerAnimal.currentFarmId));
                        switch (AnimalFeedStatus.valueOf(ownerAnimal.animalFeedStatus)) {
                            case HUNGRY:
                                hungry = true;
                                Log.record("小鸡在[" + userName + "]的庄园里挨饿");
                                break;

                            case EATING:
                                Log.record("小鸡在[" + userName + "]的庄园里吃得津津有味");
                                break;
                        }

                        boolean recall = false;
                        switch ((int) recallAnimalType.getValue()) {
                            case RecallAnimalType.ALWAYS:
                                recall = true;
                                break;
                            case RecallAnimalType.WHEN_THIEF:
                                recall = !guest;
                                break;
                            case RecallAnimalType.WHEN_HUNGRY:
                                recall = hungry;
                                break;
                        }
                        if (recall) {
                            recallAnimal(ownerAnimal.animalId, ownerAnimal.currentFarmId, ownerFarmId, userName);
                            syncAnimalStatus(ownerFarmId);
                        }
                    }
                }
            });

            step("道具奖励", () -> {
                if (receiveFarmToolReward.getValue()) {
                    listFarmTool();
                    receiveToolTaskReward();
                }
            });

            step("庄园游戏", () -> {
                if (recordFarmGame.getValue()) {
                    long currentTimeMillis = System.currentTimeMillis();
                    for (String time : farmGameTime.getValue()) {
                        if (TimeUtil.checkInTimeRange(currentTimeMillis, time)) {
                            recordFarmGame(GameType.starGame);
                            recordFarmGame(GameType.jumpGame);
                            recordFarmGame(GameType.flyGame);
                            recordFarmGame(GameType.hitGame);
                            break;
                        }
                    }
                }
            });

            step("游戏中心兑换", () -> {
                if (gameCenterBuyMallItem.getValue()) {
                    gameCenterBuyMallItem();
                }
            });

            step("小鸡厨房", () -> {
                if (kitchen.getValue()) {
                    collectDailyFoodMaterial(ownerUserId);
                    collectDailyLimitedFoodMaterial();
                    // 新增：判断小鸡是否在睡觉，如果在睡觉则跳过厨房操作
                    if (AnimalFeedStatus.SLEEPY.name().equals(ownerAnimal.animalFeedStatus)) {
                        Log.record("小鸡正在睡觉🛌，跳过小鸡厨房👨🏻‍🍳制作");
                    } else {
                        cook(ownerUserId);
                    }
                }
            });

            step("小鸡日记", () -> {
                if (chickenDiary.getValue()) {
                    queryChickenDiary("");
                    queryChickenDiaryList();
                }
            });

            step("新蛋卡", () -> {
                if (useNewEggTool.getValue()) {
                    useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                    syncAnimalStatus(ownerFarmId);
                }
            });
            step("篱笆卡", () -> { if (useFenceTool.getValue()) useFenceTool(); });
            step("公仔补签", () -> { if (useDollTool.getValue()) supplementDolls(); });
            step("日记点赞", () -> { if (collectChickenDiary.getValue() > 0) likeChickenDiaries(); });

            step("收爱心鸡蛋", () -> {
                if (harvestProduce.getValue() && benevolenceScore >= 1) {
                    Log.record("有可收取的爱心鸡蛋");
                    harvestProduce(ownerFarmId);
                }
            });

            step("捐蛋", () -> {
                if (rankingDonation.getValue() && dailyRankingDonation(null)) return;
                if (competition.getValue()) {
                    if (!competition()) {
                        // 仅「确认当天没有排位活动」才回退公益捐蛋；接口异常/数据缺失/20:01 后跳过都返回 true，
                        // 不会走到这里（否则当天已为排位捐过蛋，晚上还会再捐一次公益）
                        if (donationType.getValue() != DonationType.ZERO) {
                            Log.record("捐蛋排位🥚当天无排位活动，回退公益捐蛋");
                            donation();
                        }
                    }
                } else if (donationType.getValue() != DonationType.ZERO) {
                    donation();
                }
            });

            step("饲料任务", () -> {
                if (receiveFarmTaskAward.getValue()) {
                    runFarmTaskRounds();
                } else {
                    Log.record("庄园饲料任务：配置未开启，本轮未查询任务列表");
                }
            });

            step("喂鸡", () -> {
                if (AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)) {
                    if (AnimalFeedStatus.HUNGRY.name().equals(ownerAnimal.animalFeedStatus)) {
                        Log.record("小鸡在挨饿");
                        if (feedAnimal.getValue()) {
                            feedAnimal(ownerFarmId);
                        }
                    } else if (AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
                        if (useAccelerateTool.getValue()) {
                            useAccelerateTool();
                            TimeUtil.sleep(1000);
                        }
                        //使用加饭卡
                        if (useBigEaterTool.getValue()) {
                            useFarmTool(ownerFarmId, AntFarm.ToolType.BIG_EATER_TOOL);
                        }
                        if (feedAnimal.getValue()) {
                            autoFeedAnimal();
                            TimeUtil.sleep(1000);
                        }
                    }

                    checkUnReceiveTaskAward();
                }
            });

            // 小鸡换装
            step("小鸡换装", () -> {
                if (ornamentsDressUp.getValue()) {
                    ornamentsDressUp();
                }
            });

            // 到访小鸡送礼
            step("到访小鸡送礼", () -> {
                visitAnimal();
            });

            // 送麦子
            step("送麦子", () -> {
                visitFriend();
            });

            // 帮好友喂鸡
            step("帮好友喂鸡", () -> {
                if (feedFriendAnimal.getValue()) {
                    feedFriend();
                }
            });

            // 通知好友赶鸡
            step("通知好友赶鸡", () -> {
                if (notifyFriendType.getValue() != NotifyFriendType.NONE) {
                    notifyFriend();
                }
            });

            // 抽抽乐
            step("抽抽乐", () -> {
                if (drawMachine.getValue()) {
                    drawMachineGroups();

                }
            });

            step("NPC小鸡", this::manageFarmNpc);

            // 雇佣小鸡
            step("雇佣小鸡", () -> {
                if (hireAnimalType.getValue() != HireAnimalType.NONE) {
                    hireAnimal();
                }
            });
            
            /*  注释掉有问题的代码
             if (getFeedType.getValue() != GetFeedType.NONE) {
                letsGetChickenFeedTogether();
            }*/

            step("家庭", () -> {
                if (family.getValue()) {
                    family();
                }
            });

            // 开宝箱
            step("开宝箱", () -> {
                if (drawGameCenterAward.getValue()) {
                    drawGameCenterAward();
                }
            });

            // 道具奖励补领：本轮动作（做任务/喂鸡等）可能让新的道具任务变为可领，结尾再查一遍领掉，不必等下次运行
            step("道具奖励补领", () -> {
                if (receiveFarmToolReward.getValue()) {
                    listFarmTool();
                    receiveToolTaskReward();
                }
            });

            step("庄园奖励补领", () -> {
                if (receiveFarmTaskAward.getValue()) {
                    listFarmTask(TaskStatus.FINISHED);
                }
            });

            // 小鸡睡觉&起床
            step("小鸡睡觉&起床", () -> {
                animalSleepAndWake();
            });

        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "AntFarm.start.run err:", t);
        }
    }

    // 普通业务异常只跳过当前步骤；代际取消必须交还 ModelTask 收尾。
    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "run[" + name + "] err:", t);
        }
    }

    public static void initAntFarmTaskListMap(boolean AutoAntFarmDoFarmTaskList, boolean AutoAntFarmDrawMachineTaskList, boolean drawMachine) {
        try {
            //初始化AntFarmDoFarmTaskListMap
            AntFarmDoFarmTaskListMap.load();
            // 预置黑名单登记在 MessageUtil（单一真相，配置页据此标注"默认"）
            Set<String> blackList = MessageUtil.presetBlackList("AntFarm", "AntFarmDoFarmTaskList");
            Set<String> whiteList = new HashSet<>();
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntFarmDoFarmTaskListMap.add(task, task);
            }

            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTask());
            if (MessageUtil.checkMemo(TAG, jo)) {
                JSONArray ja = jo.optJSONArray("farmTaskList");
                for (int i = 0; ja != null && i < ja.length(); i++) {
                    JSONObject item = ja.optJSONObject(i);
                    String title = item == null ? null : item.optString("title", null);
                    if (title != null) {
                        AntFarmDoFarmTaskListMap.add(title, title);
                    }
                }
            }
            //保存任务到配置文件
            AntFarmDoFarmTaskListMap.save();
            Log.record("同步任务🉑庄园饲料任务列表");

            //自动按模块初始化设定调整黑名单和白名单
            if (AutoAntFarmDoFarmTaskList) {
                // 初始化黑白名单（使用集合统一操作）
                ConfigV2 config = ConfigV2.INSTANCE;
                ModelFields AntFarm = config.getModelFieldsMap().get("AntFarm");
                SelectModelField AntFarmDoFarmTaskList = (SelectModelField) AntFarm.get("AntFarmDoFarmTaskList");
                if (AntFarmDoFarmTaskList == null) {
                    return;
                }
                // 2~4. 批量写回黑/白名单并保存
                MessageUtil.syncTaskBlackList("庄园饲料任务", "AntFarmDoFarmTaskList", blackList, whiteList, AntFarmDoFarmTaskList);
            }

            //初始化AntFarmDrawMachineTaskListMap
            AntFarmDrawMachineTaskListMap.load();
            // 注：游戏/开宝箱类不再预置拉黑，交由自动拉黑机制判定；
            // "伸出援手，点亮希望"（需真实捐赠）与"消耗饲料换机会"（需消耗资源）保留
            blackList = MessageUtil.presetBlackList("AntFarm", "AntFarmDrawMachineTaskList");

            whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntFarmDrawMachineTaskListMap.add(task, task);
            }

            if (drawMachine) {
                jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmDrawTask("ANTFARM_DAILY_DRAW_TASK"));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        JSONArray farmTaskList = jo.optJSONArray("farmTaskList");
                        for (int i = 0; farmTaskList != null && i < farmTaskList.length(); i++) {
                            JSONObject item = farmTaskList.optJSONObject(i);
                            String title = item == null ? null : item.optString("title", null);
                            if (title != null) {
                                AntFarmDrawMachineTaskListMap.add(title, title);
                            }
                        }
                        JSONObject queryDrawMachineActivityjo = MyUtils.newJSONObject(AntFarmRpcCall.queryDrawMachineActivity("ipDrawMachine", "dailyDrawMachine"));
                        if (MessageUtil.checkMemo(TAG, queryDrawMachineActivityjo)) {
                            JSONArray otherIds = queryDrawMachineActivityjo.optJSONArray("otherDrawMachineActivityIds");
                            if (otherIds != null && otherIds.length() > 0) {
                                jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmDrawTask("ANTFARM_IP_DRAW_TASK"));
                                if (MessageUtil.checkMemo(TAG, jo)) {
                                    farmTaskList = jo.optJSONArray("farmTaskList");
                                    for (int i = 0; farmTaskList != null && i < farmTaskList.length(); i++) {
                                        JSONObject item = farmTaskList.optJSONObject(i);
                                        String title = item == null ? null : item.optString("title", null);
                                        if (title != null) {
                                            AntFarmDrawMachineTaskListMap.add(title, title);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                //保存任务到配置文件
                AntFarmDrawMachineTaskListMap.save();
                Log.record("同步任务🉑庄园装扮抽抽乐任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntFarmDrawMachineTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntFarm = config.getModelFieldsMap().get("AntFarm");
                    SelectModelField AntFarmDrawMachineTaskList = (SelectModelField) AntFarm.get("AntFarmDrawMachineTaskList");
                    if (AntFarmDrawMachineTaskList == null) {
                        return;
                    }
                    // 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("庄园装扮抽抽乐任务", "AntFarmDrawMachineTaskList", blackList, whiteList, AntFarmDrawMachineTaskList);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initAntFarmTaskListMap err:", t);
        }
    }

    private void animalSleepAndWake() {
        if (!enableSleep.getValue()) {
            Log.record("小鸡自动起床开关已关闭");
            return;
        }
        String sleepTimeStr = sleepTime.getValue();
        if ("-1".equals(sleepTimeStr)) {
            return;
        }
        animalWakeUpNow();
        Calendar animalSleepTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(sleepTimeStr);
        if (animalSleepTimeCalendar == null) {
            return;
        }
        Integer sleepMinutesInt = sleepMinutes.getValue();
        Calendar animalWakeUpTimeCalendar = (Calendar) animalSleepTimeCalendar.clone();
        animalWakeUpTimeCalendar.add(Calendar.MINUTE, sleepMinutesInt);
        long animalSleepTime = animalSleepTimeCalendar.getTimeInMillis();
        long animalWakeUpTime = animalWakeUpTimeCalendar.getTimeInMillis();
        if (animalSleepTime > animalWakeUpTime) {
            Log.record("小鸡起床时间设置有误，请重新设置");
            return;
        }
        Calendar now = TimeUtil.getNow();
        boolean afterSleepTime = now.compareTo(animalSleepTimeCalendar) > 0;
        boolean afterWakeUpTime = now.compareTo(animalWakeUpTimeCalendar) > 0;
        if (afterSleepTime && afterWakeUpTime) {
            // 睡觉时间后
            if (hasSleepToday()) {
                return;
            }
            Log.record("已过小鸡今日起床时间");
            return;
        }
        if (afterSleepTime) {
            // 睡觉时间内
            animalWakeUpTime(animalWakeUpTime);
            return;
        }
        // 睡觉时间前
        animalWakeUpTimeCalendar.add(Calendar.HOUR_OF_DAY, -24);
        if (now.compareTo(animalWakeUpTimeCalendar) <= 0) {
            animalWakeUpTime(animalWakeUpTimeCalendar.getTimeInMillis());
        }
        animalWakeUpTime(animalWakeUpTime);
    }

    private JSONObject enterFarm() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            JSONObject dynamicGlobalConfig = jo.optJSONObject("dynamicGlobalConfig");
            rewardProductNum = dynamicGlobalConfig == null ? null : dynamicGlobalConfig.optString("rewardProductNum", null);
            JSONObject joFarmVO = jo.optJSONObject("farmVO");
            if (joFarmVO == null) {
                Log.record("庄园🏠enterFarm 响应缺少 farmVO，跳过本轮");
                return null;
            }
            foodStock = joFarmVO.optInt("foodStock", 0);
            foodStockLimit = joFarmVO.optInt("foodStockLimit", 0);
            harvestBenevolenceScore = joFarmVO.optDouble("harvestBenevolenceScore", 0d);
            parseSyncAnimalStatusResponse(joFarmVO.toString());
            JSONObject masterUserInfoVO = joFarmVO.optJSONObject("masterUserInfoVO");
            ownerUserId = masterUserInfoVO == null ? null : masterUserInfoVO.optString("userId", null);
            if (StringUtil.isEmpty(ownerUserId)) {
                Log.record("庄园🏠enterFarm 响应缺少 masterUserInfoVO.userId，跳过本轮");
                return null;
            }
            ownerGroupId = getFamilyGroupId(ownerUserId);

            JSONObject activityData = jo.optJSONObject("activityData");
            if (activityData != null) {
                JSONArray springGifts = activityData.optJSONArray("springGifts");
                if (springGifts != null) {
                    for (int i = 0; i < springGifts.length(); i++) {
                        JSONObject springGift = springGifts.optJSONObject(i);
                        if (springGift == null) continue;
                        String foodType = springGift.optString("foodType");
                        int giftIndex = springGift.optInt("giftIndex");
                        String foodSubType = springGift.optString("foodSubType");
                        int foodCount = springGift.optInt("foodCount");
                        AntFarmRpcCall.clickForGiftV2(foodType, giftIndex);
                        if (MessageUtil.checkMemo(TAG, jo)) {
                            Log.farm("惊喜礼包🎁[" + foodSubType + "*" + foodCount + "]");
                        }
                    }
                }
            }

            if (useSpecialFood.getValue()) {
                JSONArray cuisineList = jo.optJSONArray("cuisineList");
                if (cuisineList != null && AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus) && !AnimalFeedStatus.SLEEPY.name().equals(ownerAnimal.animalFeedStatus) && Status.canUseSpecialFoodToday()) {
                    useFarmFood(cuisineList);
                }
            }

            JSONObject lotteryPlusInfo = jo.optJSONObject("lotteryPlusInfo");
            if (lotteryPlusInfo != null) {
                drawLotteryPlus(lotteryPlusInfo);
            }
            JSONObject subFarmVO = joFarmVO.optJSONObject("subFarmVO");
            if (acceptGift.getValue() && subFarmVO != null && subFarmVO.has("giftRecord") && foodStockLimit - foodStock >= 10) {
                acceptGift();
            }
            return jo;
        } catch (Throwable t) {
            Log.err(TAG, "enterFarm err:", t);
        }
        return null;
    }

    private void autoFeedAnimal() {
        String farmId = ownerFarmId;
        if (farmId == null || farmId.isEmpty()) return;
        for (int i = 1; i <= MAX_AUTO_FEED_RECHECKS; i++) {
            if (hasChildTask("UPDATE|FA|" + farmId + "|" + i)) return;
        }
        autoFeedAnimal(farmId, UserIdMap.getCurrentUid(), 0);
    }

    private static final int MAX_AUTO_FEED_RECHECKS = 5;

    private void autoFeedAnimal(String farmId, String uid, int rechecks) {
        if (!feedAnimal.getValue() || uid == null || uid.isEmpty()
                || !uid.equals(UserIdMap.getCurrentUid()) || !farmId.equals(ownerFarmId)) {
            Log.record("自动喂鸡🥣停止重查：开关已关闭或账号/庄园已变化");
            return;
        }
        try {
            // 仅用本次返回计算，查询失败时不能沿用缓存中的进食状态与速度。
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.syncAnimalStatus(farmId));
            JSONObject farm = jo.optJSONObject("subFarmVO");
            JSONArray currentAnimals = farm == null ? null : farm.optJSONArray("animals");
            if (!MessageUtil.checkMemo(TAG, jo) || farm == null
                    || !farmId.equals(farm.optString("farmId")) || currentAnimals == null) {
                retryAutoFeedAnimal(farmId, uid, rechecks, "状态查询失败或庄园数据缺失，code="
                        + jo.optString("resultCode", "未知") + "，" + jo.optString("memo", "无成功状态"));
                return;
            }
            JSONObject ownAnimal = null;
            for (int i = 0; i < currentAnimals.length(); i++) {
                JSONObject animal = currentAnimals.optJSONObject(i);
                if (animal != null && farmId.equals(animal.optString("masterFarmId"))) {
                    ownAnimal = animal;
                    break;
                }
            }
            JSONObject status = ownAnimal == null ? null : ownAnimal.optJSONObject("animalStatusVO");
            String feedStatus = status == null ? "" : status.optString("animalFeedStatus");
            if (!AnimalFeedStatus.EATING.name().equals(feedStatus)) {
                retryAutoFeedAnimal(farmId, uid, rechecks, "小鸡尚未恢复进食，状态="
                        + (feedStatus.isEmpty() ? "缺失" : feedStatus));
                return;
            }
            double food = farm.optDouble("foodInTrough", Double.NaN);
            double eaten = 0d, speed = 0d;
            long now = System.currentTimeMillis();
            for (int i = 0; i < currentAnimals.length(); i++) {
                JSONObject animal = currentAnimals.optJSONObject(i);
                double animalSpeed = animal == null ? Double.NaN : animal.optDouble("consumeSpeed", Double.NaN);
                long start = animal == null ? 0 : animal.optLong("startEatTime");
                if (!Double.isFinite(animalSpeed) || animalSpeed < 0
                        || (animalSpeed > 0 && (start <= 0 || start > now))) {
                    retryAutoFeedAnimal(farmId, uid, rechecks, "进食速度或开始时间缺失/异常");
                    return;
                }
                eaten += (now - start) / 1000d * animalSpeed;
                speed += animalSpeed;
            }
            double remainMs = (food - eaten) / speed * 1000d;
            if (!Double.isFinite(food) || food < 0 || !Double.isFinite(speed) || speed <= 0
                    || !Double.isFinite(remainMs) || remainMs < 1 || remainMs >= Long.MAX_VALUE - now) {
                retryAutoFeedAnimal(farmId, uid, rechecks, "进食速度为零或剩余进食时间异常");
                return;
            }
            long nextFeedTime = now + (long) remainMs;
            String taskId = "FA|" + farmId;
            if (hasChildTask(taskId)) removeChildTask(taskId);
            if (addChildTask(new ChildModelTask(taskId, "FA", () -> {
                if (feedAnimal.getValue() && uid.equals(UserIdMap.getCurrentUid()) && farmId.equals(ownerFarmId)) {
                    feedAnimal(farmId);
                } else {
                    Log.record("自动喂鸡🥣停止投喂：开关已关闭或账号/庄园已变化");
                }
            }, nextFeedTime))) {
                Log.record("自动喂鸡🥣" + (rechecks > 0 ? "重查恢复，" : "") + "添加蹲点投喂["
                        + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(nextFeedTime) + "]执行");
            } else {
                Log.record("自动喂鸡🥣蹲点投喂排期失败，等待下一轮庄园任务");
            }
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Exception e) {
            Log.err(TAG, "autoFeedAnimal err:", e);
            retryAutoFeedAnimal(farmId, uid, rechecks, "状态查询异常：" + e.getClass().getSimpleName());
        }
    }

    private void retryAutoFeedAnimal(String farmId, String uid, int rechecks, String reason) {
        if (hasChildTask("FA|" + farmId)) removeChildTask("FA|" + farmId);
        if (rechecks >= MAX_AUTO_FEED_RECHECKS) {
            Log.record("自动喂鸡🥣停止重查：" + reason + "；达到连续 " + MAX_AUTO_FEED_RECHECKS
                    + " 次重查上限，等待下一轮庄园任务");
            return;
        }
        int next = rechecks + 1;
        // 执行器结束回调后按 ID 清理，下一次必须使用不同 ID，否则新排期也会被旧回调删掉。
        String taskId = "UPDATE|FA|" + farmId + "|" + next;
        if (hasChildTask(taskId)) return;
        if (addChildTask(new ChildModelTask(taskId, "UPDATE", () -> autoFeedAnimal(farmId, uid, next),
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30)))) {
            Log.record("自动喂鸡🥣" + reason + "；30 秒后重查（" + next + "/" + MAX_AUTO_FEED_RECHECKS + "）");
        } else {
            Log.record("自动喂鸡🥣重查排期失败：" + reason + "；等待下一轮庄园任务");
        }
    }


    private void animalWakeUpTime(long animalWakeUpTime) {
        String wakeUpTaskId = "AW|" + animalWakeUpTime;
        if (!hasChildTask(wakeUpTaskId)) {
            addChildTask(new ChildModelTask(wakeUpTaskId, "AW", this::animalWakeUpNow, animalWakeUpTime));
            Log.record("添加定时起床🔆[" + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(animalWakeUpTime) + "]执行");
        }
    }

    private Boolean hasSleepToday() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(ownerUserId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject sleepNotifyInfo = jo.optJSONObject("sleepNotifyInfo");
            return sleepNotifyInfo != null && sleepNotifyInfo.optBoolean("hasSleepToday", false);
        } catch (Throwable t) {
            Log.i(TAG, "hasSleepToday err:");
            Log.printStackTrace(t);
        }
        return false;
    }

    private Boolean animalWakeUpNow() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject ownAnimal = jo.optJSONObject("ownAnimal");
            JSONObject sleepInfo = ownAnimal != null ? ownAnimal.optJSONObject("sleepInfo") : null;
            if (sleepInfo == null || !sleepInfo.has("sleepBeginTime")) {
                return false;
            }
            if (sleepInfo.optLong("sleepBeginTime") + TimeUnit.MINUTES.toMillis(sleepMinutes.getValue()) <= System.currentTimeMillis()) {
                // 亲密家庭开启时优先在"家庭"起床；否则按 spaceType 兜底
                if (family.getValue() || SPACE_TYPE_CHICK_FAMILY.equals(jo.optString("spaceType"))) {
                    return familyWakeUp();
                }
                return animalWakeUp();
            } else {
                Log.record("小鸡无需起床🔆");
            }
        } catch (Throwable t) {
            Log.i(TAG, "animalWakeUpNow err:");
            Log.printStackTrace(t);
        }
        return false;
    }


    private Boolean animalWakeUp() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.wakeUp());
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("小鸡起床🔆");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "animalWakeUp err:", t);
        }
        return false;
    }

    private void syncAnimalStatus(String farmId) {
        try {
            String s = AntFarmRpcCall.syncAnimalStatus(farmId);
            parseSyncAnimalStatusResponse(s);
        } catch (Throwable t) {
            Log.err(TAG, "syncAnimalStatus err:", t);
        }
    }

    private void syncAnimalStatusAtOtherFarm(String farmId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm(farmId, ""));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject farmVO = jo.optJSONObject("farmVO");
            JSONObject subFarmVO = farmVO == null ? null : farmVO.optJSONObject("subFarmVO");
            JSONArray jaAnimals = subFarmVO == null ? null : subFarmVO.optJSONArray("animals");
            for (int i = 0; jaAnimals != null && i < jaAnimals.length(); i++) {
                JSONObject animal = jaAnimals.optJSONObject(i);
                if (animal == null || !animal.optString("masterFarmId", "").equals(ownerFarmId)) {
                    continue;
                }
                Animal newOwnerAnimal = new Animal();
                newOwnerAnimal.animalId = animal.optString("animalId");
                newOwnerAnimal.currentFarmId = animal.optString("currentFarmId");
                newOwnerAnimal.currentFarmMasterUserId = animal.optString("currentFarmMasterUserId");
                newOwnerAnimal.masterFarmId = ownerFarmId;
                newOwnerAnimal.animalBuff = animal.optString("animalBuff");
                newOwnerAnimal.locationType = animal.optString("locationType", "");
                newOwnerAnimal.subAnimalType = animal.optString("subAnimalType");
                JSONObject animalStatusVO = animal.optJSONObject("animalStatusVO");
                if (animalStatusVO != null) {
                    newOwnerAnimal.animalFeedStatus = animalStatusVO.optString("animalFeedStatus");
                    newOwnerAnimal.animalInteractStatus = animalStatusVO.optString("animalInteractStatus");
                }
                ownerAnimal = newOwnerAnimal;
                break;
            }
        } catch (Throwable t) {
            Log.err(TAG, "syncAnimalStatusAtOtherFarm err:", t);
        }
    }

    private void rewardFriend() {
        try {
            if (rewardList != null) {
                for (RewardFriend rewardFriend : rewardList) {
                    JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.rewardFriend(rewardFriend.consistencyKey, rewardFriend.friendId, rewardProductNum, rewardFriend.time));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        double rewardCount = benevolenceScore - jo.optDouble("farmProduct", 0d);
                        benevolenceScore -= rewardCount;
                        Log.farm("打赏好友💰[" + UserIdMap.getMaskName(rewardFriend.friendId) + "]#得" + rewardCount + "颗爱心鸡蛋");
                    }
                }
                rewardList = null;
            }
        } catch (Throwable t) {
            Log.err(TAG, "rewardFriend err:", t);
        }
    }

    private void recallAnimal(String animalId, String currentFarmId, String masterFarmId, String user) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.recallAnimal(animalId, currentFarmId, masterFarmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            double foodHaveStolen = jo.optDouble("foodHaveStolen", 0d);
            Log.farm("召回小鸡📣偷吃[" + user + "]饲料" + foodHaveStolen + "g");
            // 这里不需要加
            // add2FoodStock((int)foodHaveStolen);
        } catch (Throwable t) {
            Log.err(TAG, "recallAnimal err:", t);
        }
    }

    private void sendBackAnimal() {
        if (animals == null) {
            return;
        }
        try {
            for (Animal animal : animals) {
                if (SubAnimalType.NPC.name().equals(animal.subAnimalType)) continue;
                if (AnimalInteractStatus.STEALING.name().equals(animal.animalInteractStatus) && !SubAnimalType.GUEST.name().equals(animal.subAnimalType) && !SubAnimalType.WORK.name().equals(animal.subAnimalType)) {
                    // 赶鸡
                    String user = AntFarmRpcCall.farmId2UserId(animal.masterFarmId);
                    boolean isSendBackAnimal = sendBackAnimalList.getValue().contains(user);
                    if (sendBackAnimalType.getValue() != SendBackAnimalType.BACK) {
                        isSendBackAnimal = !isSendBackAnimal;
                    }
                    if (!isSendBackAnimal) {
                        continue;
                    }
                    int sendTypeInt = sendBackAnimalWay.getValue();
                    user = UserIdMap.getMaskName(user);
                    if (!manualFarmOwner(ownerUserId) || sendBackAnimalType.getValue() == SendBackAnimalType.NONE) return;
                    JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.sendBackAnimal(SendBackAnimalWay.nickNames[sendTypeInt], animal.animalId, animal.currentFarmId, animal.masterFarmId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        String s;
                        if (sendTypeInt == SendBackAnimalWay.HIT) {
                            if (jo.has("hitLossFood")) {
                                s = "胖揍小鸡🤺[" + user + "]，掉落[" + jo.optInt("hitLossFood", 0) + "g]";
                                if (jo.has("finalFoodStorage")) {
                                    foodStock = jo.optInt("finalFoodStorage", foodStock);
                                }
                            } else {
                                s = "[" + user + "]的小鸡躲开了攻击";
                            }
                        } else {
                            s = "驱赶小鸡🧶[" + user + "]";
                        }
                        Log.farm(s);
                    }
                }
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "sendBackAnimal err:", t);
        }
    }

    private static ToolType rewardToolType(String award) {
        String normalized = "DOLL_TOOL".equals(award) ? "DOLLTOOL"
                : "ORNAMENT_ORDINARY_TOOL".equals(award) ? "ORDINARY_ORNAMENT_TOOL"
                : "ORNAMENT_ADVANCE_TOOL".equals(award) ? "ADVANCE_ORNAMENT_TOOL"
                : "ORNAMENT_RARE_TOOL".equals(award) ? "RARE_ORNAMENT_TOOL" : award;
        try { return normalized == null ? null : ToolType.valueOf(normalized); }
        catch (IllegalArgumentException e) { return null; }
    }

    private JSONObject rewardToolStock(ToolType type) throws JSONException {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTool());
        JSONArray rows = response.optJSONArray("toolList");
        if (!farmFeatureOk(response) || rows == null) return null;
        JSONObject result = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            if (!type.name().equals(row.opt("toolType"))) continue;
            long count = npcTaskNumber(row, "toolCount"), limit = npcTaskNumber(row, "toolHoldLimit");
            if (result != null || count < 0 || limit <= 0 || count > limit || !(row.opt("toolId") instanceof String) || row.optString("toolId").isEmpty()) return null;
            result = row;
        }
        return result;
    }

    private static final String EXTRA_REWARD_TOOL_KEY = "farmExtraRewardTools";

    private boolean extraRewardToolAllowed(ToolType type) {
        if (!useFullRewardTool.getValue() || rewardExtraToolDailyLimit.getValue() <= 0) return false;
        if (type == ToolType.STEALTOOL) return rewardStealTool.getValue();
        if (type == ToolType.SHARETOOL) return rewardShareTool.getValue();
        return (type == ToolType.ORDINARY_ORNAMENT_TOOL || type == ToolType.ADVANCE_ORNAMENT_TOOL || type == ToolType.RARE_ORNAMENT_TOOL)
                && rewardOrnamentTools.getValue().contains(type.name());
    }

    private boolean extraRewardToolOwner(String uid, ToolType type) {
        return manualFarmOwner(uid) && uid.equals(ownerUserId) && ownerFarmId != null && !ownerFarmId.isEmpty() && extraRewardToolAllowed(type);
    }

    private static final class RewardFoodState {
        final String animal, currentFarm, home, feed;
        final double food;
        RewardFoodState(String animal, String currentFarm, String home, String feed, double food) {
            this.animal = animal; this.currentFarm = currentFarm; this.home = home; this.feed = feed; this.food = food;
        }
    }

    private RewardFoodState extraRewardFoodState(String uid, ToolType type) {
        if (!extraRewardToolOwner(uid, type)) return null;
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", uid));
        JSONObject farm = response.optJSONObject("farmVO");
        JSONObject master = farm == null ? null : farm.optJSONObject("masterUserInfoVO");
        JSONObject sub = farm == null ? null : farm.optJSONObject("subFarmVO");
        JSONArray rows = sub == null ? null : sub.optJSONArray("animals");
        if (!farmFeatureOk(response) || !extraRewardToolOwner(uid, type) || master == null || !uid.equals(master.opt("userId"))
                || sub == null || !ownerFarmId.equals(sub.opt("farmId")) || rows == null || rows.length() > 1000) return null;
        JSONObject own = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("masterFarmId") instanceof String)) return null;
            if (ownerFarmId.equals(row.opt("masterFarmId"))) { if (own != null) return null; own = row; }
        }
        JSONObject status = own == null ? null : own.optJSONObject("animalStatusVO");
        double stock = foodNumber(farm, "foodStock"), trough = foodNumber(sub, "foodInTrough");
        if (own == null || status == null || !(own.opt("animalId") instanceof String) || own.optString("animalId").isEmpty()
                || !(own.opt("currentFarmId") instanceof String) || own.optString("currentFarmId").isEmpty()
                || !(status.opt("animalInteractStatus") instanceof String) || !(status.opt("animalFeedStatus") instanceof String)
                || !Double.isFinite(stock + trough)) return null;
        return new RewardFoodState(own.optString("animalId"), own.optString("currentFarmId"), status.optString("animalInteractStatus"),
                status.optString("animalFeedStatus"), stock + trough);
    }

    private Map<String, Boolean> extraRewardOrnaments(String uid, ToolType type) {
        if (!extraRewardToolOwner(uid, type)) return null;
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.listOrnaments());
        JSONArray rows = response.optJSONArray("achievementOrnaments");
        if (!farmFeatureOk(response) || !extraRewardToolOwner(uid, type) || rows == null || rows.length() > 1000) return null;
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("resourceKey") instanceof String) || row.optString("resourceKey").isEmpty()
                    || row.optString("resourceKey").length() > 256 || !(row.opt("acquired") instanceof Boolean)
                    || result.put(row.optString("resourceKey"), row.optBoolean("acquired")) != null) return null;
        }
        return result;
    }

    private JSONObject extraRewardToolBudget(String uid) throws JSONException {
        String raw = RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY);
        JSONObject budget = MyUtils.newJSONObject(raw);
        String day = rankingDay(System.currentTimeMillis());
        if (raw.isEmpty()) {
            budget.put("owner", uid); budget.put("day", day); budget.put("used", 0); budget.put("pending", false); budget.put("attempts", MyUtils.newJSONObject());
        } else if (!uid.equals(budget.opt("owner")) || !(budget.opt("day") instanceof String)
                || npcTaskNumber(budget, "used") < 0 || npcTaskNumber(budget, "used") > 5 || !(budget.opt("pending") instanceof Boolean)
                || budget.optJSONObject("attempts") == null || budget.optJSONObject("attempts").length() > 5) return null;
        String savedDay = budget.optString("day");
        if (!savedDay.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        try {
            java.time.LocalDate saved = java.time.LocalDate.parse(savedDay);
            if (saved.getYear() < 1 || saved.isAfter(java.time.LocalDate.parse(day))) return null;
        } catch (java.time.format.DateTimeParseException e) { return null; }
        JSONObject attempts = budget.optJSONObject("attempts");
        if (npcTaskNumber(budget, "used") != attempts.length() || (budget.optBoolean("pending") && attempts.length() == 0)) return null;
        Iterator<String> keys = attempts.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if ((!ToolType.STEALTOOL.name().equals(key) && !ToolType.SHARETOOL.name().equals(key)
                    && !ToolType.ORDINARY_ORNAMENT_TOOL.name().equals(key) && !ToolType.ADVANCE_ORNAMENT_TOOL.name().equals(key)
                    && !ToolType.RARE_ORNAMENT_TOOL.name().equals(key)) || !Boolean.TRUE.equals(attempts.opt(key))) return null;
        }
        if (!day.equals(savedDay) && !budget.optBoolean("pending")) {
            budget.put("day", day); budget.put("used", 0); budget.put("attempts", MyUtils.newJSONObject());
            // A lost result stays frozen across dates and restarts; resetting the daily quota is not proof of an effect.
        }
        return budget;
    }

    private boolean useExtraRewardTool(ToolType type) {
        String uid = ownerUserId;
        if (!extraRewardToolOwner(uid, type)) return false;
        try {
            boolean foodTool = type == ToolType.STEALTOOL || type == ToolType.SHARETOOL;
            RewardFoodState before = extraRewardFoodState(uid, type);
            if (before == null || !AnimalInteractStatus.HOME.name().equals(before.home) || !ownerFarmId.equals(before.currentFarm)) return false;
            if (foodTool && (!AnimalFeedStatus.HUNGRY.name().equals(before.feed) || before.food >= 180)) return false;
            Map<String, Boolean> ornaments = foodTool ? null : extraRewardOrnaments(uid, type);
            if (!foodTool && (ornaments == null || !ornaments.containsValue(false))) return false;
            JSONObject stock = rewardToolStock(type), budget = extraRewardToolBudget(uid);
            if (stock == null || npcTaskNumber(stock, "toolCount") <= 0 || budget == null) return false;
            if (budget.optBoolean("pending")) { Log.record("奖励腾位：之前提交结果未知，持久冻结蹭饭/救济/装扮自动消费"); return false; }
            JSONObject attempts = budget.optJSONObject("attempts");
            if (attempts.has(type.name()) || npcTaskNumber(budget, "used") >= rewardExtraToolDailyLimit.getValue()) return false;
            before = extraRewardFoodState(uid, type);
            if (before == null || !AnimalInteractStatus.HOME.name().equals(before.home) || !ownerFarmId.equals(before.currentFarm)
                    || (foodTool && (!AnimalFeedStatus.HUNGRY.name().equals(before.feed) || before.food >= 180))) return false;
            if (!foodTool) { ornaments = extraRewardOrnaments(uid, type); if (ornaments == null || !ornaments.containsValue(false)) return false; }
            JSONObject freshStock = rewardToolStock(type);
            if (freshStock == null || !stock.optString("toolId").equals(freshStock.optString("toolId"))
                    || npcTaskNumber(stock, "toolCount") != npcTaskNumber(freshStock, "toolCount")
                    || npcTaskNumber(stock, "toolHoldLimit") != npcTaskNumber(freshStock, "toolHoldLimit")) return false;
            if (!extraRewardToolOwner(uid, type)) return false;
            attempts.put(type.name(), true); budget.put("used", npcTaskNumber(budget, "used") + 1); budget.put("pending", true);
            if (!RuntimeInfo.getInstance().putVerified(EXTRA_REWARD_TOOL_KEY, budget.toString()) || !extraRewardToolOwner(uid, type)) return false;
            // Source AG uses only targetFarmId/toolId/toolType here. Ornament eligibility and the new set are decided by the server.
            JSONObject accepted = MyUtils.newJSONObject(AntFarmRpcCall.useFarmTool(ownerFarmId, stock.optString("toolId"), type.name()));
            if (!farmFeatureOk(accepted) || !extraRewardToolOwner(uid, type)) {
                Log.record("奖励腾位：提交未确认，预算保留并冻结后续蹭饭/救济/装扮自动消费"); return false;
            }
            JSONObject afterStock = rewardToolStock(type);
            if (afterStock == null || npcTaskNumber(afterStock, "toolCount") != npcTaskNumber(stock, "toolCount") - 1
                    || npcTaskNumber(afterStock, "toolHoldLimit") != npcTaskNumber(stock, "toolHoldLimit")) {
                Log.record("奖励腾位：库存未确认减少一张，保留原奖励并持久冻结后续自动消费"); return false;
            }
            boolean confirmed = false;
            if (foodTool) {
                RewardFoodState after = extraRewardFoodState(uid, type);
                confirmed = after != null && before.animal.equals(after.animal) && (after.food > before.food
                        || (type == ToolType.STEALTOOL && AnimalInteractStatus.STEALING.name().equals(after.home)
                        && !ownerFarmId.equals(after.currentFarm)));
            } else {
                Map<String, Boolean> after = extraRewardOrnaments(uid, type);
                if (after != null) for (Map.Entry<String, Boolean> target : ornaments.entrySet()) {
                    if (!target.getValue() && Boolean.TRUE.equals(after.get(target.getKey()))) { confirmed = true; break; }
                }
            }
            if (!confirmed || !extraRewardToolOwner(uid, type)) {
                Log.record("奖励腾位：粮食/蹭饭去向/新套装未回查确认，原奖励保留，后续自动消费持久冻结"); return false;
            }
            budget.put("pending", false);
            if (!RuntimeInfo.getInstance().putVerified(EXTRA_REWARD_TOOL_KEY, budget.toString())) return false;
            Log.farm("奖励腾位🎭[" + type.nickName() + "]#已回查库存减少一张及实际效果，原奖励继续领取");
            return true;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable e) { Log.err(TAG, "useExtraRewardTool", e); }
        return false;
    }

    private boolean toolRewardReady(String task, String award, int count) {
        TimeUtil.sleep(0);
        JSONObject root = MyUtils.newJSONObject(AntFarmRpcCall.listToolTaskDetails());
        JSONArray rows = root.optJSONArray("list");
        if (!farmFeatureOk(root) || rows == null || rows.length() > 200) return false;
        int matches = 0;
        boolean ready = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !task.equals(row.opt("taskType"))) continue;
            matches++;
            Object raw = row.opt("bizInfo");
            JSONObject info = raw instanceof JSONObject ? (JSONObject) raw : raw instanceof String ? MyUtils.newJSONObject((String) raw) : null;
            ready = TaskStatus.FINISHED.name().equals(row.opt("taskStatus")) && info != null
                    && award.equals(info.opt("awardType")) && npcTaskNumber(info, "awardCount") == count;
        }
        return matches == 1 && ready;
    }

    private void receiveToolRewardWithSpace(String award, ToolType type, int count, String task) throws JSONException {
        if (count <= 0 || task == null || task.isEmpty()) return;
        String claimKey = "farm::toolRewardAttempt::" + task, releaseKey = "farm::toolReleaseAttempt::" + type + ":" + task;
        if (Status.hasFlagToday(claimKey) || !toolRewardReady(task, award, count)) return;
        JSONObject before = rewardToolStock(type);
        if (before == null) return;
        long stock = npcTaskNumber(before, "toolCount"), limit = npcTaskNumber(before, "toolHoldLimit");
        if (limit - stock < count) {
            if (stock <= 0 || limit - stock + 1 < count || Status.hasFlagToday(releaseKey)) return;
            boolean allowed = type == ToolType.NEWEGGTOOL && useNewEggTool.getValue()
                    || type == ToolType.ACCELERATETOOL && useAccelerateTool.getValue() && Status.canUseAccelerateToolToday()
                    || type == ToolType.FENCETOOL && useFenceTool.getValue()
                    || type == ToolType.BIG_EATER_TOOL && useBigEaterTool.getValue()
                    || type == ToolType.DOLLTOOL && useDollTool.getValue()
                    || extraRewardToolAllowed(type);
            if (!allowed || ownerAnimal == null || !AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)) return;
            TimeUtil.sleep(0);
            Status.flagToday(releaseKey);
            if (type == ToolType.ACCELERATETOOL) useAccelerateTool(true);
            else if (type == ToolType.FENCETOOL) useFenceTool();
            else if (type == ToolType.BIG_EATER_TOOL) { if (!useFarmTool(ownerFarmId, type)) return; }
            else if (type == ToolType.DOLLTOOL) supplementDolls(1);
            else if (extraRewardToolAllowed(type)) { if (!useExtraRewardTool(type)) return; }
            else useFarmTool(ownerFarmId, type);
            JSONObject after = rewardToolStock(type);
            if (after == null || npcTaskNumber(after, "toolHoldLimit") != limit || npcTaskNumber(after, "toolCount") != stock - 1
                    || limit - npcTaskNumber(after, "toolCount") < count) {
                Log.record("道具奖励腾位未确认，本日不重复尝试#" + task);
                return;
            }
        }
        TimeUtil.sleep(0);
        if (!toolRewardReady(task, award, count)) return;
        Status.flagToday(claimKey);
        JSONObject accepted = MyUtils.newJSONObject(AntFarmRpcCall.receiveToolTaskReward(award, count, task));
        TimeUtil.sleep(0);
        JSONObject after = MyUtils.newJSONObject(AntFarmRpcCall.listToolTaskDetails());
        JSONArray rows = after.optJSONArray("list");
        if (!farmFeatureOk(accepted) || !farmFeatureOk(after) || rows == null) return;
        int matches = 0;
        boolean received = false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && task.equals(row.opt("taskType"))) {
                matches++;
                received = TaskStatus.RECEIVED.name().equals(row.opt("taskStatus"));
            }
        }
        if (matches == 1 && received) Log.farm("道具奖励🎖️领取回查成功#" + type.nickName() + " " + count + "张");
        else Log.record("道具奖励领取未确认，本日不重复尝试#" + task);
    }

    private void receiveToolTaskReward() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listToolTaskDetails());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray jaList = jo.optJSONArray("list");
            for (int i = 0; jaList != null && i < jaList.length(); i++) {
                JSONObject joItem = jaList.optJSONObject(i);
                if (joItem == null || !TaskStatus.FINISHED.name().equals(joItem.optString("taskStatus"))) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(joItem.optString("bizInfo", null));
                String awardType = bizInfo.optString("awardType");
                if (awardType.isEmpty()) {
                    // 数据缺失（不是合法的新类型）：rewardType 空的领取请求服务端必失败，直接跳过不发请求
                    Log.record("领取道具⏭️跳过奖励类型缺失[" + bizInfo.optString("taskTitle", "") + "]");
                    continue;
                }
                // 未知奖励类型只跳过“满了”判断，照样直接领：valueOf 抛异常会 abort 整个循环，
                // 后面所有待领道具都领不了（对照 listFarmTask 对未知 taskStatus 的逐项跳过）
                ToolType toolType = rewardToolType(awardType);
                if (toolType == null) Log.record("领取道具🎖️未知类型[" + awardType + "]#直接领取");
                boolean isFull = false;
                if (toolType != null && farmTools != null) {
                    for (FarmTool farmTool : farmTools) {
                        if (farmTool.toolType == toolType) {
                            if (farmTool.toolCount == farmTool.toolHoldLimit) {
                                isFull = true;
                            }
                            break;
                        }
                    }
                }
                long parsedCount = npcTaskNumber(bizInfo, "awardCount");
                String taskType = joItem.optString("taskType", "");
                String taskTitle = bizInfo.optString("taskTitle", "");
                if (parsedCount <= 0 || parsedCount > Integer.MAX_VALUE || !(joItem.opt("taskType") instanceof String) || taskType.isEmpty()) {
                    Log.record("领取道具⏭️跳过[" + taskTitle + "]#数量或任务类型缺失[" + parsedCount + "/" + taskType + "]");
                    continue;
                }
                int awardCount = (int) parsedCount;
                if (useFullRewardTool.getValue()) {
                    if (toolType != null) receiveToolRewardWithSpace(awardType, toolType, awardCount, taskType);
                    continue;
                }
                if (isFull) {
                    if (ToolType.NEWEGGTOOL.equals(toolType)) {
                        useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                    } else {
                        Log.record("领取道具[" + toolType.nickName() + "]#已满，暂不领取");
                        continue;
                    }
                }
                jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveToolTaskReward(awardType, awardCount, taskType));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    CharSequence toolName = toolType != null ? toolType.nickName() : awardType;
                    Log.farm("领取道具🎖️[" + taskTitle + "-" + toolName + "]#" + awardCount + "张");
                } else {
                    Log.record("领取道具⚠️失败[" + taskTitle + "]#类型[" + awardType + "]数量[" + awardCount + "]#memo[" + jo.optString("memo") + "]");
                }
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "receiveToolTaskReward err:", t);
        }
    }

    private void harvestProduce(String farmId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.harvestProduce(farmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            double harvest = jo.optDouble("harvestBenevolenceScore", 0d);
            harvestBenevolenceScore = jo.optDouble("finalBenevolenceScore", harvestBenevolenceScore);
            Log.farm("收取鸡蛋🥚[" + harvest + "颗]#剩余" + harvestBenevolenceScore + "颗");
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "harvestProduce err:", t);
        }
    }

    /* 捐赠爱心鸡蛋 */
    private void donation() {
        if (!canDonationToday()) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listActivityInfo());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray activityInfos = jo.optJSONArray("activityInfos");
            if (activityInfos == null) {
                return;
            }
            for (int i = 0; i < activityInfos.length(); i++) {
                jo = activityInfos.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                int donationTotal = jo.optInt("donationTotal");
                int donationLimit = jo.optInt("donationLimit");

                int donationNum = Math.min(donationAmount.getValue(), donationLimit - donationTotal);
                if (donationNum == 0) {
                    continue;
                }
                String activityId = jo.optString("activityId");
                String projectName = jo.optString("projectName");
                String projectId = jo.optString("projectId");
                int projectDonationNum = getProjectDonationNum(projectId);
                donationNum = Math.min(donationNum, donationAmount.getValue() - projectDonationNum % donationAmount.getValue());
                boolean isDonation;
                if (donationNum == donationAmount.getValue()) {
                    isDonation = donation(activityId, projectName, donationNum, 1);
                } else {
                    isDonation = donation(activityId, projectName, 1, donationNum);
                }
                if (isDonation && donationType.getValue() != DonationType.ALL) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "donation err:", t);
        }
    }

    private void donation(int donateNum) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listActivityInfo());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray activityInfos = jo.optJSONArray("activityInfos");
            if (activityInfos == null) {
                return;
            }

            // 收集可捐蛋项目
            java.util.ArrayList<JSONObject> availableList = new java.util.ArrayList<>();
            for (int i = 0; i < activityInfos.length(); i++) {
                JSONObject activityInfo = activityInfos.optJSONObject(i);
                if (activityInfo == null) {
                    continue;
                }
                int available = activityInfo.optInt("donationLimit") - activityInfo.optInt("donationTotal");
                if (available > 0) {
                    availableList.add(activityInfo);
                }
            }

            if (availableList.isEmpty()) {
                return;
            }

            // 平均分配
            int remaining = donateNum;
            int size = availableList.size();
            for (int i = 0; i < size && remaining > 0; i++) {
                JSONObject activityInfo = availableList.get(i);
                int available = activityInfo.optInt("donationLimit") - activityInfo.optInt("donationTotal");

                // 计算分配数量
                int assign;
                if (i == size - 1) {
                    assign = remaining;
                } else {
                    assign = donateNum / size;
                    if (i < donateNum % size) {
                        assign++;
                    }
                }

                // 不超过可捐限额和剩余数量
                assign = Math.min(assign, available);
                assign = Math.min(assign, remaining);

                if (assign > 0) {
                    String activityId = activityInfo.optString("activityId");
                    String projectName = activityInfo.optString("projectName");
                    donation(activityId, projectName, assign);
                    remaining -= assign;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "donation err:", t);
        }
    }

    private Boolean donation(String activityId, String activityName, int donationAmount, int count) {
        boolean isDonation = false;
        for (int i = 0; i < count; i++) {
            if (!donation(activityId, activityName, donationAmount)) {
                break;
            }
            isDonation = true;
            TimeUtil.sleep(1000L);
        }
        return isDonation;
    }

    private Boolean donation(String activityId, String activityName, int donationAmount) {
        if (harvestBenevolenceScore < donationAmount) {
            return false;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.donation(activityId, donationAmount));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            jo = jo.optJSONObject("donation");
            if (jo == null) {
                return false;
            }
            harvestBenevolenceScore = jo.optDouble("harvestBenevolenceScore", harvestBenevolenceScore);
            int donationTimesStat = jo.optInt("donationTimesStat");
            Log.farm("公益捐赠❤️[捐爱心蛋:" + activityName + "]捐赠" + donationAmount + "颗爱心蛋#累计捐赠" + donationTimesStat + "次");
            // 与 S2 共用当日标记：同一天只捐一次（谁先捐成都算数）
            Status.flagToday(FLAG_CHARITY_DONATION_DONE);
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "donation err:", t);
        }
        return false;
    }

    /**
     * 爱心鸡结号(S2赛季)自动化。
     * <p>
     * S2 为<b>周活动</b>：每周一 00:00:00 ~ 周日 20:00:00 为一轮（旧排位赛为按天）。
     * <p>
     * 返回值 = 「<b>当天的捐蛋是否已经完成</b>」，供调用方决定要不要回退公益捐蛋：
     * <ul>
     *   <li>{@code true}：今天已捐过或 S2 写请求结果未知——S2 优先，不再做公益；</li>
     *   <li>{@code false}：S2 没捐成（无活动 / 「自动捐蛋」没开 / 没蛋 / 取不到项目），
     *       由调用方回退公益捐蛋，保证这一天至少捐出一次。</li>
     * </ul>
     * <p>
     * 活跃判据用「多信号取或」：轮次号、活动数据字段、捐蛋项目三者任一命中即算有活动。
     * 原先只看 {@code rankRoundId} + 单组活动字段，服务端字段一改就永远判成「无活动」，
     * 表现为每天都跳过爱心鸡结号、只做公益捐蛋。
     */
    private boolean competition() {
        boolean s2DonatedToday = Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY)
                || Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE)
                || Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED);
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                Log.record("爱心鸡结号❤️入口信息查询失败，按当日捐蛋记录决定是否回退公益：" + jo);
                return s2DonatedToday;
            }
            Log.i("爱心鸡结号❤️当前爱心值" + jo.optInt("benevolenceScore"));

            String roundId = findCompetitionRoundId(jo);
            String projectId = "";
            String projectName = "";
            boolean s2Active;
            if (!isCompetitionRoundActive()) {
                s2Active = false;
                Log.record("爱心鸡结号❤️已过周活动时间窗口(周日20:00后)，回退公益捐蛋");
            } else if (!roundId.isEmpty() || hasCompetitionActivityData(jo)) {
                s2Active = true;
                Log.i("爱心鸡结号❤️判定 S2 活跃#轮次=" + (roundId.isEmpty() ? "未取到" : roundId));
            } else {
                // 兜底判据：字段对不上时，能取到捐蛋项目也算 S2 有活动
                JSONObject project = queryCompetitionProject();
                projectId = project == null ? "" : project.optString("projectId", "");
                projectName = project == null ? "" : project.optString("projectName", "");
                s2Active = !projectId.isEmpty();
                Log.record(s2Active ? "爱心鸡结号❤️活动字段未命中，但能取到捐蛋项目，按有活动处理"
                        : "爱心鸡结号❤️未确认到活跃轮次，回退公益捐蛋");
            }

            if (s2Active) {
                // 优先捐 S2。「当天合计一次」：S2 自己已捐成、或公益今天已捐过，都不再多捐
                // （两个标记各记各的，这里只是合起来看）
                if (Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY)
                        || Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE)) {
                    s2DonatedToday = true;
                    Log.i("爱心鸡结号❤️今日已捐过蛋，跳过");
                } else {
                    s2DonatedToday = competitionDonate.getValue() && donateToCompetition(competitionDonateAmount.getValue());
                }
                // 偷榜定时任务（周日20:00前 N 分钟执行一次）
                if (competitionStealRank.getValue()) {
                    setupStealRankTask();
                }
            }

            // 领取任务 + 成就奖励（与捐蛋互不影响）
            if (competitionReceiveTask.getValue()) {
                receiveCompetitionAward(jo);
                receiveCompetitionTaskAwards();
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "competition err:", t);
            return true;
        }
        // 一旦写请求已发起，结果未知也不能再回退公益重复消耗。
        return s2DonatedToday || Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED);
    }

    /**
     * S2 轮次号：服务端字段名/层级不唯一，按候选路径探测，取不到返回空串。
     */
    private static String findCompetitionRoundId(JSONObject jo) {
        String[] keys = {"rankRoundId", "roundId"};
        for (String key : keys) {
            String value = jo.optString(key, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        for (String wrapper : new String[]{"rankRoundInfo", "competitionRankInfo", "donationCompetitionInfo"}) {
            JSONObject sub = jo.optJSONObject(wrapper);
            if (sub == null) {
                continue;
            }
            for (String key : keys) {
                String value = sub.optString(key, "");
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    /**
     * S2 活动数据是否存在：已知字段任一命中即算（比只看单个字段更抗字段改名/改层级）。
     */
    private static boolean hasCompetitionActivityData(JSONObject jo) {
        for (String key : new String[]{"competitionTaskInfo", "donationCompetitionLevelConfigs", "benevolenceScore"}) {
            if (jo.has(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查 S2 捐蛋项目（{@code animationInfo.competitionProjectInfo}），失败返回 {@code null}。
     */
    private static JSONObject queryCompetitionProject() {
        try {
            JSONObject info = MyUtils.newJSONObject(AntFarmRpcCall.queryCompetitionEntranceInfo());
            if (!MessageUtil.checkMemo(TAG, info)) return null;
            JSONObject animationInfo = info.optJSONObject("animationInfo");
            return animationInfo == null ? null : animationInfo.optJSONObject("competitionProjectInfo");
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "queryCompetitionProject err:", t);
        }
        return null;
    }

    /**
     * 爱心鸡结号(S2赛季)奖励领取：等级爱心值奖励。
     * <p>
     * 等级奖励列表来自 {@code enterDonationCompetitionRank} 响应的 {@code levelAwardInfoList}，
     * 每项含 {@code rightsId}（形如 "0915_1"）与 {@code status}（unattained/unclaimed/received）。
     * 对 {@code status=="unclaimed"} 的项调用 {@code receiveDonationLevelReward(rightsId)} 领取。
     */
    private void receiveCompetitionAward(JSONObject jo) {
        try {
            JSONArray levelAwardInfoList = jo.optJSONArray("levelAwardInfoList");
            if (levelAwardInfoList == null || levelAwardInfoList.length() == 0) {
                Log.farm("爱心鸡结号❤️领取：无等级奖励列表");
                return;
            }
            int claimed = 0;
            for (int i = 0; i < levelAwardInfoList.length(); i++) {
                JSONObject item = levelAwardInfoList.optJSONObject(i);
                if (item == null) continue;
                String status = item.optString("status");
                // unclaimed=可领；received=已领；unattained=未达成
                if (!"unclaimed".equals(status)) {
                    continue;
                }
                String rightsId = item.optString("rightsId");
                if (rightsId.isEmpty()) {
                    continue;
                }
                JSONObject rjo = MyUtils.newJSONObject(AntFarmRpcCall.receiveDonationLevelReward(rightsId));
                if (MessageUtil.checkMemo(TAG, rjo)) {
                    claimed++;
                    Log.farm("爱心鸡结号❤️领取等级奖励[" + item.optString("levelName") + "] rightsId=" + rightsId);
                }
                TimeUtil.sleep(1000L);
            }
            if (claimed > 0) {
                Log.farm("爱心鸡结号❤️本次领取" + claimed + "个等级爱心值奖励");
            } else {
                Log.farm("爱心鸡结号❤️无待领取的等级爱心值奖励");
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveCompetitionAward err:", t);
        }
    }

    /**
     * 爱心鸡结号(S2赛季)任务奖励领取。
     * <p>
     * 任务列表来自 {@code listCompetitionTask} 响应的 {@code taskList}，
     * 每项含 {@code taskType}（如 "TEAM_TASK_ROUND_1_TASK_500"）与 {@code canReceiveAwardCount}（可领数）。
     * 对 {@code canReceiveAwardCount > 0} 的任务调用 {@code receiveCompetitionTaskAward(taskType, count)} 领取。
     */
    private void receiveCompetitionTaskAwards() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listCompetitionTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray taskList = jo.optJSONArray("taskList");
            if (taskList == null || taskList.length() == 0) {
                Log.record("爱心鸡结号❤️任务奖励：无任务列表");
                return;
            }
            int claimed = 0;
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) continue;
                // AG 仅领取 FINISHED；可领数量不能替代任务状态。
                if (!"FINISHED".equals(task.optString("taskStatus"))) {
                    Log.record("爱心鸡结号❤️跳过非待领奖任务#taskType=" + task.optString("taskType")
                            + "，状态=" + task.optString("taskStatus"));
                    continue;
                }
                int canReceive = task.optInt("canReceiveAwardCount");
                if (canReceive <= 0) {
                    continue;
                }
                String taskType = task.optString("taskType");
                if (taskType.isEmpty()) {
                    continue;
                }
                JSONObject rjo = MyUtils.newJSONObject(AntFarmRpcCall.receiveCompetitionTaskAward(taskType, canReceive));
                if (MessageUtil.checkSuccess(TAG, rjo)) {
                    claimed++;
                    Log.farm("爱心鸡结号❤️领取任务奖励[" + task.optString("title") + "] taskType=" + taskType);
                }
                TimeUtil.sleep(1000L);
            }
            if (claimed > 0) {
                Log.farm("爱心鸡结号❤️本次领取" + claimed + "个任务奖励");
            } else {
                Log.farm("爱心鸡结号❤️无待领取的任务奖励");
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveCompetitionTaskAwards err:", t);
        }
    }

    /**
     * S2 轮次判断：每周一 00:00:00 ~ 周日 20:00:00 为活动轮次内。
     * 周日 20:00 起轮次结束（结算/发奖期），不捐蛋、不偷榜。
     */
    private boolean isCompetitionRoundActive() {
        java.util.Calendar cal = MyUtils.getInstance();
        int dow = cal.get(java.util.Calendar.DAY_OF_WEEK); // SUNDAY=1
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        // 周日 20:00 之后到周一 00:00 之前为轮次外
        return !(dow == java.util.Calendar.SUNDAY && hour >= 20);
    }

    /**
     * 优先给 S2 定向捐蛋（每天一次，数量取「爱心鸡结号 | 自动捐蛋数量」）。
     * <p>返回「今天的捐蛋是否已完成」：捐成功返回 true；「自动捐蛋」没开 / 没蛋 / 取不到项目 /
     * 请求结果未知返回 false，但调用方同时检查尝试标记，不回退公益重复消耗。
     * <p>两道防重：
     * <ul>
     *   <li>① 发请求前落「今日已尝试」标记 {@link #FLAG_COMPETITION_DONATE_TRIED}：
     *       S2 每天最多发一次请求，响应判定不成也不会每轮重发；</li>
     *   <li>② 响应没判定成功时回读项目累计捐赠数（{@code userProjectDonationNum}），
     *       同账号同日增量恰好等于本次捐赠数才确认捐上了，不再回退公益（避免"实际已捐 + 公益再捐一次"）。</li>
     * </ul>
     * <p>确认捐上才落 S2 自己的当日标记 {@link #FLAG_COMPETITION_DONATED_TODAY}：
     * 与公益是「当天合计一次」，但各自记各自的标记。
     */
    private boolean donateToCompetition(int amount) {
        try {
            if (Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY) || Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE)) return true;
            if (Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED)) return false;
            if (amount <= 0) {
                return false;
            }
            if (rankingDonation.getValue() && rankingFoodRefill.getValue() && !rankingFoodRefillBusy && harvestBenevolenceScore < amount) {
                JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
                RankingSnapshot rank = MessageUtil.checkMemo(TAG, response) ? rankingSnapshot(response, ownerUserId, true) : null;
                if (rank != null && rankingOwner(rank.owner) && rankingWindow(rank, System.currentTimeMillis())) {
                    int target = Math.min(amount, rankingQuota(rank));
                    if (target > harvestBenevolenceScore) {
                        rankingFoodRefillBusy = true;
                        try { useDynamicSpecialFood(target, rank); }
                        finally { rankingFoodRefillBusy = false; }
                    }
                }
            }
            int have = (int) harvestBenevolenceScore;
            if (have <= 0) {
                Log.record("爱心鸡结号❤️当前无蛋可捐");
                return false;
            }
            JSONObject info = MyUtils.newJSONObject(AntFarmRpcCall.queryCompetitionEntranceInfo());
            if (!MessageUtil.checkMemo(TAG, info)) return false;
            String projectId = null, projectName = null;
            JSONObject anim = info.optJSONObject("animationInfo");
            if (anim != null) {
                JSONObject cpi = anim.optJSONObject("competitionProjectInfo");
                if (cpi != null) {
                    projectId = cpi.optString("projectId");
                    projectName = cpi.optString("projectName");
                }
            }
            if (projectId == null || projectId.isEmpty()) {
                Log.record("爱心鸡结号❤️未获取到捐蛋项目，跳过自动捐蛋");
                return false;
            }
            int n = Math.min(amount, have);
            String donationDay = rankingDay(System.currentTimeMillis());
            int beforeNum = queryProjectDonationNum(projectId);   // ② 捐前基准（-1=读不到）
            Log.farm("爱心鸡结号❤️自动捐蛋" + n + "枚到项目[" + projectName + "]");
            if (Boolean.TRUE.equals(donationCompetition(projectId, projectName, n, "auto"))) return true;
            if (!rankingOwner(ownerUserId) || !donationDay.equals(rankingDay(System.currentTimeMillis()))) return false;
            int afterNum = queryProjectDonationNum(projectId);
            if (Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED) && beforeNum >= 0 && (long) afterNum - beforeNum == n) {
                Status.flagToday(FLAG_COMPETITION_DONATED_TODAY);
                return true;
            }
            return false;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "donateToCompetition err:", t);
        }
        return false;
    }

    /**
     * 设置偷榜定时任务：在每周日 20:00 前 {@code competitionStealMinutes} 分钟执行一次。
     */
    private void setupStealRankTask() {
        int minutes = competitionStealMinutes.getValue();
        if (minutes <= 0) {
            return;
        }
        java.util.Calendar target = MyUtils.getInstance();
        target.set(java.util.Calendar.HOUR_OF_DAY, 20);
        target.set(java.util.Calendar.MINUTE, 0);
        target.set(java.util.Calendar.SECOND, 0);
        target.set(java.util.Calendar.MILLISECOND, 0);
        int dow = target.get(java.util.Calendar.DAY_OF_WEEK);
        int daysUntilSunday = (java.util.Calendar.SATURDAY - dow + 1) % 7;
        target.add(java.util.Calendar.DAY_OF_MONTH, daysUntilSunday);
        long stealRankTime = target.getTimeInMillis() - (long) minutes * 60 * 1000;
        if (stealRankTime <= System.currentTimeMillis()) {
            target.add(java.util.Calendar.DAY_OF_MONTH, 7);
            stealRankTime = target.getTimeInMillis() - (long) minutes * 60 * 1000;
        }
        String taskId = "competitionStealRank_" + minutes;
        if (!hasChildTask(taskId)) {
            addChildTask(new ChildModelTask(taskId, "COMPETITION_STEAL", this::stealRankS2, stealRankTime));
            Log.record("爱心鸡结号❤️已设置偷榜[定时]在 " + TimeUtil.getCommonDate(stealRankTime) + " 执行");
        }
    }

    /**
     * 偷榜：读取排行，捐赠至超过当前第1名（定向捐到 S2 项目）。
     */
    private void stealRankS2() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject home = jo.optJSONObject("donationRankHomeInfo");
            if (home == null) {
                Log.record("爱心鸡结号❤️偷榜：无排行信息");
                return;
            }
            JSONArray list = home.optJSONArray("userDonationRankList");
            if (list == null || list.length() == 0 || list.length() > 1000) {
                Log.record("爱心鸡结号❤️偷榜：排行榜为空");
                return;
            }
            String myId = UserIdMap.getCurrentUid();
            int targetRank = competitionTargetRank.getValue();
            if (targetRank < 1 || targetRank > 100) return;
            int myDonation = 0, myRank = 0, targetDonation = 0;
            boolean hasTarget = false;
            Set<Integer> positions = new HashSet<>();
            Set<String> users = new HashSet<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject u = list.optJSONObject(i);
                if (u == null) return;
                int rank = rankingInt(u, "rankOrder"), dn = rankingInt(u, "donationNum");
                if (rank <= 0 || dn < 0 || !(u.opt("userId") instanceof String) || u.optString("userId").isEmpty()
                        || !positions.add(rank) || !users.add(u.optString("userId"))) return;
                if (rank == targetRank) {
                    targetDonation = dn;
                    hasTarget = true;
                }
                if (myId.equals(u.optString("userId"))) {
                    myDonation = dn;
                    myRank = rank;
                }
            }
            if (myRank <= 0) return;
            if (myRank <= targetRank) {
                Log.record("爱心鸡结号❤️偷榜：已达到目标第" + targetRank + "名(捐" + myDonation + ")，无需操作");
                return;
            }
            if (!hasTarget) return;
            long missing = (long) targetDonation - myDonation + 1;
            if (missing > Integer.MAX_VALUE) return;
            int need = (int) missing;
            if (need <= 0) {
                need = 1;
            }
            if (rankingDonation.getValue() && rankingFoodRefill.getValue() && !rankingFoodRefillBusy
                    && harvestBenevolenceScore < need && (competitionStealLimit.getValue() <= 0 || need <= competitionStealLimit.getValue())) {
                RankingSnapshot rank = rankingSnapshot(jo, ownerUserId, true);
                if (rank != null && rankingOwner(rank.owner) && rankingWindow(rank, System.currentTimeMillis()) && rankingQuota(rank) >= need) {
                    rankingFoodRefillBusy = true;
                    try {
                        useDynamicSpecialFood(need, rank);
                        // Re-read the leading row after resource RPCs; one refill pass cannot recurse into another.
                        stealRankS2();
                    } finally { rankingFoodRefillBusy = false; }
                    return;
                }
            }
            int have = (int) harvestBenevolenceScore;
            if (have <= 0) {
                Log.record("爱心鸡结号❤️偷榜：当前无蛋可捐");
                return;
            }
            // 蛋不够就不捐：捐了也超不过目标第" + targetRank + "名
            if (have < need) {
                Log.record("爱心鸡结号❤️偷榜⏭️跳过：手上的蛋不够超过目标第" + targetRank + "名(有" + have + "需" + need
                        + "，当前第" + myRank + "名捐" + myDonation + "，目标第" + targetRank + "名捐" + targetDonation + ")");
                return;
            }
            int n = need;
            // 上限按最终捐献量判定
            int stealLimit = competitionStealLimit.getValue();
            if (stealLimit > 0 && n > stealLimit) {
                Log.record("爱心鸡结号❤️偷榜⏭️跳过：需捐" + n + "超过上限" + stealLimit
                        + "(当前第" + myRank + "名捐" + myDonation + "，目标第" + targetRank + "名捐" + targetDonation + ")");
                return;
            }
            // 定向捐到 S2 项目
            JSONObject info = MyUtils.newJSONObject(AntFarmRpcCall.queryCompetitionEntranceInfo());
            if (!MessageUtil.checkMemo(TAG, info)) return;
            String projectId = null, projectName = null;
            JSONObject anim = info.optJSONObject("animationInfo");
            if (anim != null) {
                JSONObject cpi = anim.optJSONObject("competitionProjectInfo");
                if (cpi != null) {
                    projectId = cpi.optString("projectId");
                    projectName = cpi.optString("projectName");
                }
            }
            if (projectId == null || projectId.isEmpty()) {
                Log.record("爱心鸡结号❤️偷榜：未获取到捐蛋项目，跳过（无法定向到 S2）");
                return;
            }
            Log.i("爱心鸡结号❤️偷榜：当前第" + myRank + "名捐" + myDonation + "，目标第" + targetRank + "名捐" + targetDonation + "，尝试再捐" + n);
            donationCompetition(projectId, projectName, n, "steal");
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "stealRankS2 err:", t);
        }
    }

    /**
     * S2 定向捐蛋：使用 projectId（抓包确认字段），成功后刷新爱心蛋余额。
     */
    private Boolean donationCompetition(String projectId, String projectName, int donationAmount, String purpose) {
        if (donationAmount <= 0 || harvestBenevolenceScore < donationAmount) {
            return false;
        }
        try {
            if (!rankingOwner(ownerUserId)) return false;
            String donationDay = rankingDay(System.currentTimeMillis());
            if ("auto".equals(purpose) && (Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED)
                    || Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE))) return false;
            RankingSnapshot before = null;
            if (rankingDonation.getValue()) {
                JSONObject rank = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
                before = MessageUtil.checkMemo(TAG, rank) ? rankingSnapshot(rank, ownerUserId, true) : null;
                if (before == null || !isCompetitionRoundActive() || !reserveRankingDonation(before, donationAmount, purpose)) return false;
            }
            if (before != null && (!rankingOwner(before.owner) || !rankingWindow(before, System.currentTimeMillis()))) return false;
            if ("auto".equals(purpose)) Status.flagToday(FLAG_COMPETITION_DONATE_TRIED);
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.donationCompetition(projectId, donationAmount));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            if (!rankingOwner(ownerUserId) || !donationDay.equals(rankingDay(System.currentTimeMillis()))) return false;
            if (before != null && !confirmRankingDonation(before, donationAmount)) return false;
            if ("auto".equals(purpose)) Status.flagToday(FLAG_COMPETITION_DONATED_TODAY);
            // 只用响应刷新余额；缺字段时保留原值，不做本地估算（估算会与服务端漂移叠加）
            try {
                JSONObject d = jo.optJSONObject("donation");
                if (d != null && d.has("harvestBenevolenceScore")) {
                    harvestBenevolenceScore = d.optDouble("harvestBenevolenceScore", harvestBenevolenceScore);
                } else {
                    Log.record("爱心鸡结号❤️捐蛋响应缺少 donation 字段，余额暂不更新");
                }
            } catch (Throwable ignore) {
                Log.record("爱心鸡结号❤️捐蛋响应缺少 donation 字段，余额暂不更新");
            }
            Log.farm("爱心鸡结号❤️[捐爱心蛋:" + projectName + "]捐赠" + donationAmount + "颗爱心蛋");
            return true;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "donationCompetition err:", t);
        }
        return false;
    }

    private static final String RANKING_BUDGET_KEY = "farmRankingBudget";

    private static final class RankingSnapshot {
        final String owner, activity, round;
        final boolean weekly;
        final JSONArray rows;
        final int donated, rank, stars;
        final long start, end;
        RankingSnapshot(String owner, String activity, String round, boolean weekly, JSONArray rows,
                        int donated, int rank, int stars, long start, long end) {
            this.owner = owner; this.activity = activity; this.round = round; this.weekly = weekly;
            this.rows = rows; this.donated = donated; this.rank = rank; this.stars = stars;
            this.start = start; this.end = end;
        }
    }

    private static int rankingInt(JSONObject object, String field) {
        Object value = object.opt(field);
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 0 && number <= Integer.MAX_VALUE && number == Math.rint(number) ? (int) number : -1;
    }

    private static long rankingTime(JSONObject object, String field) {
        Object value = object == null ? null : object.opt(field);
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number > 0 && number < Long.MAX_VALUE && number == Math.rint(number) ? ((Number) value).longValue() : -1;
    }

    private static boolean weeklyRankingResponse(JSONObject response) {
        JSONObject conf = response.optJSONObject("donationCompetitionActivityConf");
        JSONObject level = response.optJSONObject("userDonationLevelInfo");
        return (conf != null && conf.has("projectId")) || (level != null && level.has("userContributionNum"))
                || response.has("competitionTaskInfo") || response.has("donationCompetitionLevelConfigs");
    }

    private static RankingSnapshot rankingSnapshot(JSONObject response, String owner, boolean weekly) {
        if (owner == null || owner.isEmpty() || weeklyRankingResponse(response) != weekly) return null;
        JSONObject home = response.optJSONObject("donationRankHomeInfo");
        JSONArray rows = home == null ? null : home.optJSONArray("userDonationRankList");
        if (rows == null || rows.length() == 0 || rows.length() > 1000) return null;
        JSONObject conf = response.optJSONObject("donationCompetitionActivityConf");
        if (weekly ? !(response.opt("rankRoundId") instanceof String) : conf == null || !(conf.opt("activityId") instanceof String)) return null;
        String activity = weekly ? "S2" : conf.optString("activityId");
        String round = weekly ? response.optString("rankRoundId") : rankingDay(System.currentTimeMillis());
        long start = weekly ? 0 : rankingTime(conf, "startTime"), end = weekly ? Long.MAX_VALUE : rankingTime(conf, "endTime");
        if (activity.isEmpty() || activity.length() > 128 || round.isEmpty() || round.length() > 128 || (!weekly && (start <= 0 || end <= start))) return null;
        Set<String> users = new HashSet<>(); Set<Integer> ranks = new HashSet<>();
        int donated = -1, rank = -1, stars = -1;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("userId") instanceof String)) return null;
            String uid = row.optString("userId");
            int position = rankingInt(row, "rankOrder"), amount = rankingInt(row, "donationNum");
            int reward = weekly ? 0 : rankingInt(row, "rewardStarNum");
            if (uid.isEmpty() || !users.add(uid) || position <= 0 || !ranks.add(position) || amount < 0 || reward < 0) return null;
            if (owner.equals(uid)) { donated = amount; rank = position; stars = reward; }
        }
        if (donated < 0 || !ranks.contains(1)) return null;
        return new RankingSnapshot(owner, activity, round, weekly, rows, donated, rank, stars, start, end);
    }

    private static String rankingDay(long now) {
        return java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString();
    }

    private static String rankingWeek(long now) {
        return java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate()
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString();
    }

    private static int rankingRounds(long from, long until) {
        if (from <= 0 || until <= from) return 0;
        java.time.ZonedDateTime first = java.time.Instant.ofEpochMilli(from).atZone(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.ZonedDateTime last = java.time.Instant.ofEpochMilli(until).atZone(java.time.ZoneId.of("Asia/Shanghai"));
        java.time.LocalDate start = first.toLocalDate().plusDays(first.getHour() >= 20 ? 1 : 0);
        java.time.LocalDate end = last.toLocalDate().minusDays(last.getHour() < 20 ? 1 : 0);
        long rounds = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        return rounds < 0 || rounds > 10000 ? 0 : (int) rounds;
    }

    private static int stableRankingStars(JSONObject award, RankingSnapshot rank, long now) {
        JSONObject conf = award.optJSONObject("donationCompetitionActivityConf"), level = award.optJSONObject("userDonationLevelInfo");
        JSONArray awards = award.optJSONArray("levelAwardInfoList");
        if (conf == null || level == null || awards == null || awards.length() == 0 || awards.length() > 1000
                || weeklyRankingResponse(award) || !rank.activity.equals(conf.optString("activityId"))
                || rankingTime(conf, "endTime") != rank.end) return -1;
        int current = rankingInt(level, "levelId"), light = rankingInt(level, "levelLightStarNum");
        if (current < 0 || light < 0) return -1;
        long total = 0, left = 0; boolean found = false;
        for (int i = 0; i < awards.length(); i++) {
            JSONObject item = awards.optJSONObject(i);
            if (item == null) return -1;
            int id = rankingInt(item, "levelId"), up = rankingInt(item, "levelStarUpNum");
            if (id < 0 || up < 0) return -1;
            if (id == current) found = true;
            if (up >= 10000) continue;
            total += up; if (id >= current) left += up;
        }
        if (!found || left < light) return -1;
        left -= light; if (left == 0) return 0;
        int totalRounds = rankingRounds(rank.start, rank.end);
        if (totalRounds <= 0) return -1;
        int buffer = (double) total / totalRounds <= 2 ? 7 : 3;
        int remaining = rankingRounds(now, rank.end - buffer * 86400000L), max = 0;
        for (int i = 0; i < rank.rows.length(); i++) max = Math.max(max, rankingInt(rank.rows.optJSONObject(i), "rewardStarNum"));
        if (max <= 0) return -1;
        return remaining <= 0 ? max : (int) Math.min(max, (left + remaining - 1) / remaining);
    }

    private static int rankingDonationAmount(RankingSnapshot rank, int requiredStars, int quota) {
        if (requiredStars < 0 || quota <= 0 || rank.stars >= requiredStars || rank.rank == 1) return 0;
        int amount = 0;
        for (int i = 0; i < rank.rows.length(); i++) {
            JSONObject row = rank.rows.optJSONObject(i);
            int stars = rankingInt(row, "rewardStarNum");
            long need = (long) rankingInt(row, "donationNum") - rank.donated + 1;
            if (rankingInt(row, "rankOrder") >= rank.rank || stars < requiredStars || need <= 0 || need > quota) continue;
            if (amount == 0 || need < amount) amount = (int) need;
        }
        return amount;
    }

    private static int rankingAggressiveStars(RankingSnapshot rank, int quota) {
        int stars = rank.stars;
        for (int i = 0; i < rank.rows.length(); i++) {
            JSONObject row = rank.rows.optJSONObject(i);
            long need = (long) rankingInt(row, "donationNum") - rank.donated + 1;
            if (rankingInt(row, "rankOrder") < rank.rank && need > 0 && need <= quota) stars = Math.max(stars, rankingInt(row, "rewardStarNum"));
        }
        return stars;
    }

    private boolean rankingOwner(String owner) {
        if (Thread.currentThread().isInterrupted()) throw new TaskCancelledException();
        return rankingDonation.getValue() && owner != null && owner.equals(ownerUserId) && owner.equals(UserIdMap.getCurrentUid()) && check();
    }

    private static boolean rankingWindow(RankingSnapshot rank, long now) {
        java.time.ZonedDateTime local = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Shanghai"));
        return rank.weekly ? !(local.getDayOfWeek() == java.time.DayOfWeek.SUNDAY && local.getHour() >= 20)
                : now >= rank.start && now < rank.end && local.getHour() < 20;
    }

    private JSONObject rankingBudget(RankingSnapshot rank, long now) throws JSONException {
        String raw = RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY);
        JSONObject state = MyUtils.newJSONObject(raw);
        if (!raw.isEmpty() && (!(state.opt("day") instanceof String) || !(state.opt("week") instanceof String)
                || rankingInt(state, "daily") < 0 || rankingInt(state, "weekly") < 0 || state.optJSONObject("attempts") == null)) return null;
        String day = rankingDay(now), week = rankingWeek(now);
        if (!week.equals(state.optString("week"))) {
            state.put("weekly", 0); state.put("attempts", MyUtils.newJSONObject());
        }
        if (!day.equals(state.optString("day"))) state.put("daily", 0);
        state.put("day", day); state.put("week", week);
        if (state.has("pending")) {
            JSONObject pending = state.optJSONObject("pending");
            if (pending == null || !rank.owner.equals(pending.opt("owner")) || !(pending.opt("weekly") instanceof Boolean)
                    || !(pending.opt("activity") instanceof String) || !(pending.opt("round") instanceof String)
                    || rankingInt(pending, "before") < 0 || rankingInt(pending, "amount") <= 0) return null;
            if (rank.weekly == pending.optBoolean("weekly") && rank.activity.equals(pending.optString("activity"))
                    && rank.round.equals(pending.optString("round"))) {
                if ((long) rank.donated < (long) rankingInt(pending, "before") + rankingInt(pending, "amount")) return null;
            } else if (rank.weekly != pending.optBoolean("weekly")) return null;
            state.remove("pending");
            if (!rankingOwner(rank.owner) || !RuntimeInfo.getInstance().putVerified(RANKING_BUDGET_KEY, state.toString())) return null;
        }
        if (rank.weekly) state.put("weekly", Math.max(state.optInt("weekly"), rank.donated));
        else state.put("daily", Math.max(state.optInt("daily"), rank.donated));
        state.put("weekly", Math.max(state.optInt("weekly"), state.optInt("daily")));
        return state;
    }

    private int rankingQuota(RankingSnapshot rank) throws JSONException {
        JSONObject state = rankingBudget(rank, System.currentTimeMillis());
        if (state == null) return 0;
        return Math.max(0, Math.min(rankingDailyBudget.getValue() - state.optInt("daily"), rankingWeeklyBudget.getValue() - state.optInt("weekly")));
    }

    private synchronized boolean reserveRankingDonation(RankingSnapshot rank, int amount, String purpose) throws JSONException {
        if (amount <= 0 || !rankingOwner(rank.owner) || !rankingWindow(rank, System.currentTimeMillis())) return false;
        long now = System.currentTimeMillis();
        JSONObject state = rankingBudget(rank, now);
        if (state == null || amount > rankingDailyBudget.getValue() - state.optInt("daily")
                || amount > rankingWeeklyBudget.getValue() - state.optInt("weekly")) return false;
        JSONObject attempts = state.optJSONObject("attempts");
        if (attempts == null || attempts.length() > 1000) return false;
        String action = (rank.weekly ? "weekly" : "daily") + "|" + rank.activity + "|" + rank.round + "|" + purpose
                + "|" + rankingDay(now)
                + (!rank.weekly && rankingWatch.getValue() ? "|" + rank.donated : "");
        if (attempts.has(action)) return false;
        attempts.put(action, amount);
        state.put("daily", state.optInt("daily") + amount); state.put("weekly", state.optInt("weekly") + amount);
        JSONObject pending = MyUtils.newJSONObject();
        pending.put("owner", rank.owner); pending.put("weekly", rank.weekly); pending.put("activity", rank.activity);
        pending.put("round", rank.round); pending.put("before", rank.donated); pending.put("amount", amount);
        state.put("pending", pending);
        // Reserve before RPC: uncertain failures spend this allowance and never repeat the same action.
        return rankingOwner(rank.owner) && RuntimeInfo.getInstance().putVerified(RANKING_BUDGET_KEY, state.toString());
    }

    private boolean confirmRankingDonation(RankingSnapshot before, int amount) {
        if (!rankingOwner(before.owner)) return false;
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
        RankingSnapshot after = MessageUtil.checkMemo(TAG, response) ? rankingSnapshot(response, before.owner, before.weekly) : null;
        boolean confirmed = after != null && before.activity.equals(after.activity) && before.round.equals(after.round)
                && (long) after.donated >= (long) before.donated + amount && rankingOwner(before.owner);
        if (confirmed) {
            try { confirmed = rankingBudget(after, System.currentTimeMillis()) != null; }
            catch (JSONException e) { confirmed = false; }
        }
        if (!confirmed) Log.record("捐蛋排位🥚捐赠未获同账号同轮次回查确认，保留预算与尝试记录");
        return confirmed;
    }

    private void scheduleRankingWatch(RankingSnapshot rank) {
        try {
            long now = System.currentTimeMillis();
            if (!rankingWatch.getValue() || !rankingOwner(rank.owner) || !rankingWindow(rank, now)) return;
            java.time.ZonedDateTime local = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Shanghai"));
            String clock = rankingDonationTime.getValue();
            if (clock == null || !clock.matches("(?:0[0-9]|1[0-9])[0-5][0-9]")) return;
            long begin = local.toLocalDate().atTime(Integer.parseInt(clock.substring(0, 2)), Integer.parseInt(clock.substring(2)))
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            long finalCheck = Math.min(rank.end, local.toLocalDate().atTime(20, 0).atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()) - 2000;
            if (now < begin || now >= finalCheck || (rankingWatchChildId != null && hasChildTask(rankingWatchChildId))) return;
            boolean pending = MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY)).optJSONObject("pending") != null;
            if (!pending && rankingQuota(rank) <= 0) return;
            long at = Math.min(finalCheck, now + Math.max(1, rankingWatchInterval.getValue()) * 1000L);
            JSONObject plan = MyUtils.newJSONObject();
            plan.put("owner", rank.owner); plan.put("activity", rank.activity); plan.put("day", rankingDay(now)); plan.put("at", at);
            if (!RuntimeInfo.getInstance().putVerified("farmRankingPlan", plan.toString())) return;
            String id = "farmRankingWatch_" + at;
            long generation = io.github.aw1y2z.sesame.data.task.TaskLifecycle.generation();
            rankingWatchChildId = id;
            addChildTask(new ChildModelTask(id, "FARM_RANKING", () -> {
                rankingWatchChildId = null;
                try (io.github.aw1y2z.sesame.data.task.TaskLifecycle.Work work = io.github.aw1y2z.sesame.data.task.TaskLifecycle.enter(generation)) {
                    if (work != null && rankingOwner(rank.owner)) dailyRankingDonation(rank.activity);
                }
            }, at));
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable e) { Log.err(TAG, "scheduleRankingWatch", e); }
    }

    private boolean dailyRankingDonation(String expectedActivity) {
        RankingSnapshot observed = null;
        try {
            if (!rankingOwner(ownerUserId)) return true;
            JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, response)) return true;
            if (weeklyRankingResponse(response)) return false;
            RankingSnapshot rank = rankingSnapshot(response, ownerUserId, false);
            long now = System.currentTimeMillis();
            if (rank == null || now < rank.start || now >= rank.end || (expectedActivity != null && !expectedActivity.equals(rank.activity))) return true;
            observed = rank;
            java.time.ZonedDateTime local = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.of("Asia/Shanghai"));
            if (local.getHour() >= 20 || rankingQuota(rank) <= 0) return true;
            String clock = rankingDonationTime.getValue();
            if (clock == null || !clock.matches("(?:0[0-9]|1[0-9])[0-5][0-9]")) return true;
            long at = local.toLocalDate().atTime(Integer.parseInt(clock.substring(0, 2)), Integer.parseInt(clock.substring(2)))
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            if (now < at) {
                JSONObject plan = MyUtils.newJSONObject();
                plan.put("owner", rank.owner); plan.put("activity", rank.activity); plan.put("day", rankingDay(now)); plan.put("at", at);
                if (!RuntimeInfo.getInstance().putVerified("farmRankingPlan", plan.toString())) return true;
                String id = "farmDailyRanking_" + rankingDay(now);
                long generation = io.github.aw1y2z.sesame.data.task.TaskLifecycle.generation();
                if (!hasChildTask(id)) addChildTask(new ChildModelTask(id, "FARM_RANKING", () -> {
                    try (io.github.aw1y2z.sesame.data.task.TaskLifecycle.Work work = io.github.aw1y2z.sesame.data.task.TaskLifecycle.enter(generation)) {
                        if (work != null && rankingOwner(rank.owner)) dailyRankingDonation(rank.activity);
                    }
                }, at));
                return true;
            }
            int budgetQuota = rankingQuota(rank);
            int quota = Math.min(budgetQuota, (int) Math.min(Integer.MAX_VALUE, harvestBenevolenceScore));
            int required;
            if (rankingStable.getValue()) {
                JSONObject award = MyUtils.newJSONObject(AntFarmRpcCall.enterCompetitionAwardPage());
                if (!MessageUtil.checkMemo(TAG, award)) return true;
                required = stableRankingStars(award, rank, now);
            } else required = rankingAggressiveStars(rank, rankingFoodRefill.getValue() ? budgetQuota : quota);
            int target = rankingDonationAmount(rank, required, budgetQuota);
            if (!rankingFoodRefillBusy && rankingFoodRefill.getValue() && target > harvestBenevolenceScore) {
                rankingFoodRefillBusy = true;
                try {
                    useDynamicSpecialFood(target, rank);
                    // The food loop may take several requests: refresh standings and rebuild the donation plan.
                    return dailyRankingDonation(rank.activity);
                } finally { rankingFoodRefillBusy = false; }
            }
            int amount = rankingDonationAmount(rank, required, quota);
            if (amount == 0 && required > rank.stars) amount = rankingDonationAmount(rank, rankingAggressiveStars(rank, quota), quota);
            if (amount <= 0) return true;
            JSONObject activities = MyUtils.newJSONObject(AntFarmRpcCall.listActivityInfo());
            if (!MessageUtil.checkMemo(TAG, activities)) return true;
            JSONArray projects = activities.optJSONArray("activityInfos");
            if (projects == null) return true;
            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.optJSONObject(i);
                if (project == null || !(project.opt("activityId") instanceof String) || !project.optString("activityId").matches("[A-Za-z0-9_-]{1,128}")
                        || "SOLDBY".equals(project.optString("projectType"))) continue;
                int limit = rankingInt(project, "donationLimit"), donated = rankingInt(project, "donationTotal");
                if (limit < 0 || donated < 0 || (long) limit - donated < amount) continue;
                if (!reserveRankingDonation(rank, amount, "plan") || !rankingOwner(rank.owner) || !rankingWindow(rank, System.currentTimeMillis())) return true;
                JSONObject result = MyUtils.newJSONObject(AntFarmRpcCall.donation(project.optString("activityId"), amount));
                if (MessageUtil.checkMemo(TAG, result) && confirmRankingDonation(rank, amount)) {
                    JSONObject balance = result.optJSONObject("donation");
                    if (balance != null) harvestBenevolenceScore = balance.optDouble("harvestBenevolenceScore", harvestBenevolenceScore);
                    Log.farm("每日排位🥚已回查确认捐赠" + amount + "枚，目标" + required + "星");
                }
                return true;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable e) { Log.err(TAG, "dailyRankingDonation", e); }
        finally { if (observed != null) scheduleRankingWatch(observed); }
        return true;
    }

    private int getProjectDonationNum(String projectId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getProjectInfo(projectId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return 0;
            }
            return jo.optInt("userProjectDonationNum");
        } catch (Throwable t) {
            Log.err(TAG, "getProjectDonationNum err:", t);
        }
        return 0;
    }

    /**
     * 读项目累计捐赠数（{@code userProjectDonationNum}）。
     * <p>与 {@link #getProjectDonationNum} 的区别：查询失败 / 字段缺失返回 {@code -1}，
     * 把「读不到」与「真的是 0 次」区分开（读不到时不做"是否已捐上"的推断）。
     */
    private int queryProjectDonationNum(String projectId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getProjectInfo(projectId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return -1;
            }
            return rankingInt(jo, "userProjectDonationNum");
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "queryProjectDonationNum err:", t);
        }
        return -1;
    }

    private Boolean canDonationToday() {
        if (Status.hasFlagToday(FLAG_CHARITY_DONATION_DONE)
                || Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY)
                || Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED)) {
            return false;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getCharityAccount(ownerUserId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONArray charityRecords = jo.optJSONArray("charityRecords");
            if (charityRecords == null || charityRecords.length() == 0) {
                return true;
            }
            jo = charityRecords.optJSONObject(0);
            if (jo == null) {
                return true;
            }
            long charityTime = jo.optLong("charityTime", System.currentTimeMillis());
            if (TimeUtil.isLessThanNowOfDays(charityTime)) {
                return true;
            }
            Status.flagToday(FLAG_CHARITY_DONATION_DONE);
        } catch (Throwable t) {
            Log.err(TAG, "canDonationToday err:", t);
        }
        return false;
    }

    private void recordFarmGame(GameType gameType) {
        try {
            do {
                try {
                    if (!manualFarmOwner(ownerUserId) || !recordFarmGame.getValue()) return;
                    JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.initFarmGame(gameType.name()));
                    if (!MessageUtil.checkMemo(TAG, jo)) {
                        return;
                    }
                    JSONObject gameAward = jo.optJSONObject("gameAward");
                    if (gameAward != null && gameAward.optBoolean("level3Get")) {
                        return;
                    }
                    if (jo.optInt("remainingGameCount", 1) == 0) {
                        return;
                    }
                    if (!manualFarmOwner(ownerUserId) || !recordFarmGame.getValue()) return;
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.recordFarmGame(gameType.name()));
                    if (!MessageUtil.checkMemo(TAG, jo)) {
                        return;
                    }
                    JSONArray awardInfos = jo.optJSONArray("awardInfos");
                    StringBuilder award = new StringBuilder();
                    if (awardInfos != null) {
                        for (int i = 0; i < awardInfos.length(); i++) {
                            JSONObject awardInfo = awardInfos.optJSONObject(i);
                            if (awardInfo == null) {
                                continue;
                            }
                            award.append(awardInfo.optString("awardName")).append("*").append(awardInfo.optInt("awardCount"));
                        }
                    }
                    if (jo.has("receiveFoodCount")) {
                        award.append(";肥料*").append(jo.optString("receiveFoodCount"));
                    }
                    Log.farm("小鸡乐园🎮游玩[" + gameType.gameName() + "]#获得[" + award + "]");
                    if (jo.optInt("remainingGameCount", 0) > 0) {
                        continue;
                    }
                    break;
                } finally {
                    TimeUtil.sleep(2000);
                }
            } while (true);
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "recordFarmGame err:", t);
        }
    }

    /**
     * 饲料任务按轮执行（对照 AG：多次任务不能只做一次，也不能没做完就当天已完成）。
     * 每轮 listFarmTask 一次，TODO 去做、FINISHED 领奖：
     * ① 没有需要处理的任务 → 本轮停止，下次执行仍会查询后来出现的奖励；
     * ② 有任务但本轮一个都没推进（失败/冷却/饲料满领不了）→ 停，下次执行再试；
     * ③ 有推进 → 再来一轮（多次任务每轮推进一次），最多 MAX_FARM_TASK_ROUNDS 轮。
     */
    private void runFarmTaskRounds() {
        farmTaskAttempted = new HashSet<>();
        try {
            for (int round = 0; round < MAX_FARM_TASK_ROUNDS; round++) {
                int[] result = listFarmTask(null);
                if (result == null) {
                    return;
                }
                if (result[0] == 0) {
                    return;
                }
                if (result[1] == 0) {
                    return;
                }
                TimeUtil.sleep(800);
            }
        } finally {
            farmTaskAttempted = null;
        }
    }

    /** 按轮执行期间记录“任务+状态+进度”已试过的组合；null 表示不在按轮执行中（如 checkUnReceiveTaskAward） */
    private Set<String> farmTaskAttempted;

    /**
     * 同一任务在同一状态/进度下本次执行只试一次：服务端返回成功但状态没变的任务（如需要手动完成的）
     * 否则会每轮都重复请求，白跑满 MAX_FARM_TASK_ROUNDS 轮。对照 AG 的 actionKey。
     */
    private boolean alreadyTried(JSONObject task, String action) {
        Set<String> attempted = farmTaskAttempted;
        if (attempted == null) {
            return false;
        }
        return !attempted.add(action + "|" + task.optString("bizKey") + "|" + task.optString("taskId") + "|"
                + task.optString("taskStatus") + "|" + task.optInt("rightsTimes") + "|" + pendingAward(task));
    }

    /**
     * 待领取的奖励饲料。多阶段任务（rightsTimesLimit>1，如“试玩庄园火爆小游戏”每阶段 30g）的 awardCount 是
     * 累计总额，alreadyReceiveStageAwardCount 是已领的部分，待领 = 差值（对照 AG getMultiStageAccumulatedAward）。
     * 原先直接拿 awardCount 做“会不会超过饲料上限”的判断，累计额（如 240g）远大于实际待领额，
     * 会让本来放得下的奖励被误判成超上限而一直领不了；差值为 0 时退回 awardCount，保持单阶段任务原行为。
     */
    private static int pendingAward(JSONObject task) {
        int total = task.optInt("awardCount", 0);
        int pending = total - task.optInt("alreadyReceiveStageAwardCount", 0);
        return pending > 0 ? pending : total;
    }

    /** 多阶段任务（rightsTimesLimit>1，如“试玩庄园火爆小游戏”8 阶段×30g）且阶段还没做满。 */
    private static boolean multiStagePending(JSONObject task) {
        return task.optInt("rightsTimesLimit", 1) > 1 && task.optInt("rightsTimes", 0) < task.optInt("rightsTimesLimit", 1);
    }

    /** 服务端不支持用 RPC 完成、必须手动做的饲料任务，不算“需要处理”。 */
    private static boolean isUnsupportedFarmTask(String bizKey) {
        return bizKey.contains("HEART_DONAT") || bizKey.equals("BAIDUJS_202512");
    }

    /**
     * 处理一遍饲料任务列表。Mode 为 null 时 TODO（去做）和 FINISHED（领奖）一起处理。
     *
     * @return {需要处理的任务数, 本遍成功推进的任务数}；查询失败或异常返回 null
     */
    private int[] listFarmTask(TaskStatus Mode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            JSONObject signList = jo.optJSONObject("signList");
            if (signList != null && sign(signList)) {
                TimeUtil.sleep(1000);
            }
            JSONArray ja = jo.optJSONArray("farmTaskList");
            if (ja == null) {
                return null;
            }
            int actionable = 0;
            int progressed = 0;
            for (int i = 0; i < ja.length(); i++) {
                JSONObject taskJo = ja.optJSONObject(i);
                if (taskJo == null) {
                    continue;
                }
                TaskStatus taskStatus;
                try {
                    taskStatus = TaskStatus.valueOf(taskJo.optString("taskStatus"));
                } catch (IllegalArgumentException e) {
                    // 服务端返回了枚举里没有的未知状态：只跳过这一项，不要让 valueOf 抛异常
                    // 被外层 catch 吞掉，导致本轮剩余任务全部不处理（XU 自己也记录过这个坑）
                    Log.record("庄园任务🥕未知状态[" + taskJo.optString("taskStatus") + "]#跳过[" + taskJo.optString("title") + "]");
                    continue;
                }
                String title = taskJo.optString("title");
                //黑名单任务跳过
                if (AntFarmDoFarmTaskList.getValue().contains(title)) {
                    if (taskStatus == TaskStatus.FINISHED) {
                        actionable++;
                        if (!alreadyTried(taskJo, "receive") && receiveFarmTaskAward(taskJo)) {
                            progressed++;
                        }
                    } else if (taskStatus == TaskStatus.TODO && !alreadyTried(taskJo, "blacklistLog")) {
                        Log.record("庄园饲料任务⏭️[" + title + "]#命中当前账号饲料任务黑名单，未发送完成请求；立即执行仍保留黑名单");
                    }
                    continue;
                }
                if (taskStatus == TaskStatus.RECEIVED || (Mode != null && taskStatus != Mode)) {
                    continue;
                }
                // 多阶段任务（如 8 阶段×30g）把进度打出来：界面上的“180/240”是已领取额，做完但没领的部分显示在
                // 右边“可领取”，光看界面分不清阶段有没有做满。同一状态只打一次
                if (Mode == null && taskJo.optInt("rightsTimesLimit", 1) > 1 && !alreadyTried(taskJo, "log")) {
                    Log.record("庄园饲料任务[" + title + "]阶段 " + taskJo.optInt("rightsTimes", 0) + "/"
                            + taskJo.optInt("rightsTimesLimit", 1) + "，待领 " + pendingAward(taskJo) + "g，状态 " + taskStatus);
                }
                if (taskStatus == TaskStatus.TODO) {
                    if (isUnsupportedFarmTask(taskJo.optString("bizKey"))) {
                        if (!alreadyTried(taskJo, "unsupportedLog")) {
                            Log.record("庄园饲料任务⏭️[" + title + "]#已知不支持通用RPC完成，需按活动要求操作，未发送完成请求");
                        }
                        continue;
                    }
                    actionable++;
                    if (alreadyTried(taskJo, "do") || !doFarmTask(taskJo)) {
                        continue;
                    }
                } else if (taskStatus == TaskStatus.FINISHED) {
                    actionable++;
                    boolean handled = false;
                    // 按轮执行时，多阶段任务先把所有阶段做完，奖励累积，不做一阶段就领一阶段（对照 AG，最终 240/240 一次领）；
                    // 阶段做不了（服务端不允许有待领奖励时继续做）才退回领奖，避免卡死
                    if (farmTaskAttempted != null && multiStagePending(taskJo) && !alreadyTried(taskJo, "stage")) {
                        handled = doFarmTask(taskJo);
                    }
                    if (!handled) {
                        handled = !alreadyTried(taskJo, "receive") && receiveFarmTaskAward(taskJo);
                    }
                    if (!handled) {
                        continue;
                    }
                } else {
                    continue;
                }
                progressed++;
                TimeUtil.sleep(1000);
            }
            return new int[]{actionable, progressed};
        } catch (Throwable t) {
            Log.err(TAG, "listFarmTask err:", t);
        }
        return null;
    }

    private Boolean sign(JSONObject SignList) {
        if (DailyTask.skipFlag("farm::sign", "庄园签到")) {
            return false;
        }
        boolean signed = false;
        try {
            String currentSignKey = SignList.optString("currentSignKey");
            JSONArray signList = SignList.optJSONArray("signList");
            if (signList == null) {
                return false;
            }
            for (int i = 0; i < signList.length(); i++) {
                JSONObject jo = signList.optJSONObject(i);
                if (jo == null || !currentSignKey.equals(jo.optString("signKey"))) {
                    continue;
                }
                if (jo.optBoolean("signed")) {
                    Log.record("庄园今日已签到");
                    signed = true;
                    return false;
                }
                int awardCount = jo.optInt("awardCount");
                if (awardCount + foodStock > foodStockLimit) {
                    return false;
                }
                int currentContinuousCount = jo.optInt("currentContinuousCount");
                jo = MyUtils.newJSONObject(AntFarmRpcCall.sign());
                if (MessageUtil.checkMemo(TAG, jo)) {
                    foodStock = jo.optInt("foodStock");
                    Log.farm("饲料任务📅签到[坚持" + currentContinuousCount + "天]#获得[" + awardCount + "g饲料]");
                    signed = true;
                    return true;
                }
                return false;
            }
        } catch (Throwable t) {
            Log.err(TAG, "sign err:", t);
        } finally {
            if (signed) {
                Status.flagToday("farm::sign");
            }
        }
        return false;
    }

    /**
     * 行为伪造：取视频 → 上报播放 → 等 15 秒 → 触发完成。
     * <p>结论按 {@link Outcome} 分类，交由 {@link TaskAttemptPolicy} 决定当天是否再试。
     */
    private Outcome doVideoTask(String title) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryTabVideoUrl());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                //检查并标记黑名单任务
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jo);
                return retryableOrUnable(jo);
            }
            String videoUrl = jo.optString("videoUrl");
            String contentId = videoUrl.substring(videoUrl.indexOf("&contentId=") + 1, videoUrl.indexOf("&refer"));
            jo = MyUtils.newJSONObject(AntFarmRpcCall.videoDeliverModule(contentId));
            if (jo.optBoolean("success")) {
                TimeUtil.sleep(15100);
                jo = MyUtils.newJSONObject(AntFarmRpcCall.videoTrigger(contentId));
                if (jo.optBoolean("success")) {
                    return Outcome.DONE;
                }
            }
            Log.record(jo.optString("resultMsg"));
            Log.i(jo.toString());
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jo);
            return retryableOrUnable(jo);
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "doVideoTask err:", t);
        }
        return Outcome.RETRY;
    }

    /** 临时故障（102/限流/异常）按可重试处理，其余按做不了 */
    private Outcome retryableOrUnable(JSONObject jo) {
        return MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo) ? Outcome.RETRY : Outcome.UNABLE;
    }

    /** 饲料任务普通完成尝试：完成与否以任务列表为准（probeFarmStatus 复核），故此处只转译响应。 */
    private Outcome attemptFarmTask(String title, String bizKey, String route) {
        try {
            JSONObject jo = MyUtils.newJSONObject("ANTFARM_FOOD_TASK".equals(route)
                    ? AntFarmRpcCall.finishTask("SHANGYEHUA_90_1", route) : AntFarmRpcCall.doFarmTask(bizKey));
            //检查并标记黑名单任务（此处是庄园饲料任务，应写入饲料黑名单而非抽抽乐）
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jo);
            if ("ANTFARM_FOOD_TASK".equals(route) ? MessageUtil.checkSuccess(TAG, jo) : MessageUtil.checkResultCode(TAG, jo)) {
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            // 不支持rpc调用（400000040）→ 由 TaskAttemptPolicy 代为伪申报
            if (TaskAlternative.hit(jo, "")) {
                return Outcome.UNSUPPORTED;
            }
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "attemptFarmTask err:", t);
        }
        return Outcome.RETRY;
    }

    /** 列表状态探针：重拉庄园饲料任务列表，按 bizKey 匹配该任务当前状态。 */
    private TaskAttemptPolicy.ProbeResult probeFarmStatus(String bizKey) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTask());
            if (!MessageUtil.checkMemo(TAG, jo) || !jo.has("farmTaskList")) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray farmTaskList = jo.optJSONArray("farmTaskList");
            if (farmTaskList == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            for (int i = 0; i < farmTaskList.length(); i++) {
                JSONObject t = farmTaskList.optJSONObject(i);
                if (t == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                if (!bizKey.equals(t.optString("bizKey"))) {
                    continue;
                }
                String status = t.optString("taskStatus");
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
            Log.err(TAG, "probeFarmStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    private Boolean doAnswerTask(String title) {
        try {
            JSONObject jo = MyUtils.newJSONObject(DadaDailyRpcCall.home("100"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                //检查并标记黑名单任务
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jo);
                return false;
            }
            JSONObject question = jo.optJSONObject("question");
            if (question == null) {
                return false;
            }
            long questionId = question.optLong("questionId");
            JSONArray labels = question.optJSONArray("label");
            if (labels == null || labels.length() == 0) {
                Log.record("庄园答题跳过：选项为空");
                return false;
            }
            // title 用 optString：缺该字段时不应让整条答题失败（AI 仍可凭选项作答，AnswerAI 内部已兜底取第一项）
            String answer = AnswerAI.getAnswer(question.optString("title"), JsonUtil.jsonArrayToList(labels));
            jo = MyUtils.newJSONObject(DadaDailyRpcCall.submit("100", answer, questionId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                //检查并标记黑名单任务
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jo);
                return false;
            }
            JSONObject extInfo = jo.optJSONObject("extInfo");
            boolean correct = jo.optBoolean("correct");
            String award = extInfo != null ? extInfo.optString("award") : "";
            Log.farm("庄园答题📝回答" + (correct ? "正确" : "错误") + "#获得[" + award + "g饲料]");
            if (jo.has("operationConfigList")) {
                JSONArray operationConfigList = jo.optJSONArray("operationConfigList");
                if (operationConfigList != null) {
                    savePreviewQuestion(operationConfigList);
                }
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "doAnswerTask err:", t);
        }
        return false;
    }

    private void savePreviewQuestion(JSONArray operationConfigList) {
        try {
            for (int i = 0; i < operationConfigList.length(); i++) {
                JSONObject jo = operationConfigList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String type = jo.optString("type");
                if (Objects.equals(type, "PREVIEW_QUESTION")) {
                    String question = jo.optString("title");
                    JSONArray ja;
                    try {
                        ja = new JSONArray(jo.optString("actionTitle", "[]"));
                    } catch (JSONException e) {
                        continue;
                    }
                    for (int j = 0; j < ja.length(); j++) {
                        jo = ja.optJSONObject(j);
                        if (jo == null) {
                            continue;
                        }
                        if (jo.optBoolean("correct")) {
                            TokenConfig.saveAnswer(question, jo.optString("title"));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "saveAnswerList err:", t);
        }
    }

    private static String farmTaskRoute(JSONObject task) {
        String type = task.optString("taskId").trim();
        if (type.isEmpty()) type = task.optString("bizKey").trim();
        JSONObject control = task.optJSONObject("deliveryControlItem");
        Map<String, String> tracer = new HashMap<>();
        for (String part : (control == null ? "" : control.optString("iepTaskTracer")).split("~")) {
            int at = part.indexOf(':');
            if (at > 0 && at < part.length() - 1) tracer.put(part.substring(0, at), part.substring(at + 1));
        }
        if (type.isEmpty()) type = tracer.getOrDefault("taskType", "");
        // AG resolveFarmTaskClosureRoute：真实业务由已有流程推进，不能再报成通用浏览任务。
        if ("COOK".equals(type)) return "小鸡厨房";
        if ("SLEEP".equals(type)) return "小鸡睡觉";
        if ("HIRE_LOW_ACTIVITY".equals(type)) return "雇佣小鸡";
        String scene = tracer.getOrDefault("sceneCode", "");
        if (Set.of("ANTFARM_DAILY_DRAW_TASK", "ANTFARM_IP_DRAW_TASK").contains(scene)) return "抽抽乐";
        JSONObject params = task.optJSONObject("categorizationParamModel");
        if (Set.of("XJLY_xxljy", "XJLYKBX1_sl90").contains(type)
                || params != null && "2021005181698249".equals(params.optString("game_id"))
                || "PARADISE".equals(task.optString("innerAction")) && "AccOpenBox".equals(task.optString("categorizationThirdLevel"))) return "小鸡乐园开宝箱";
        return "SHANGYEHUA_90_1".equals(type) && type.equals(tracer.get("taskType")) && "ANTFARM_FOOD_TASK".equals(scene)
                ? "ANTFARM_FOOD_TASK" : "";
    }

    private Boolean doFarmTask(JSONObject task) {
        boolean isDoTask = false;
        try {
            String title = task.optString("title");
            String bizKey = task.optString("bizKey");
            String taskId = task.optString("taskId");
            String route = farmTaskRoute(task);
            if (!route.isEmpty() && !"ANTFARM_FOOD_TASK".equals(route)) {
                Log.record("饲料任务[" + title + "]由" + route + "业务流程推进；本次未发送通用完成请求");
                return false;
            }
            if (bizKey.contains("multistage_gametask")) {
                Log.record("庄园多阶段游戏任务🎮[" + title + "]#bizKey=" + bizKey);
            }
            if (isUnsupportedFarmTask(bizKey)) {
                return false;
            }
            // 按稳定 taskId 分派（2026-09-22 抓包实测）：
            //   视频任务 taskId="VIDEO_TASK"、答题任务 taskId="ANSWER"
            // 注意视频任务的标题实际是「看庄园小视频」——原先写的是 equals("庄园小视频")，
            // 少一个"看"字，导致这两类任务**从来没有走对过接口**（一直落到通用 doFarmTask 分支）
            if ("VIDEO_TASK".equals(taskId)) {
                // 沿用 M 已验证的观看流程；普通申报失败不能先把视频任务拉黑。
                Outcome outcome = TaskAttemptPolicy.handle("farm::video::" + bizKey, title, null,
                        () -> doVideoTask(title), Log::farm,
                        new TaskAttemptPolicy.Site("AntFarmDoFarmTaskList", "庄园饲料任务", bizKey, "",
                                (k) -> probeFarmStatus(bizKey)));
                isDoTask = outcome == Outcome.DONE || outcome == Outcome.TRIGGERED;
            } else if ("ANSWER".equals(taskId)) {
                isDoTask = doAnswerTask(title);
            } else {
                // 完成判定以任务列表为准，不以 doFarmTask 响应为准（见 TaskAttemptPolicy）
                Outcome outcome = TaskAttemptPolicy.handle("farm::task::" + bizKey, title, null,
                        () -> attemptFarmTask(title, bizKey, route), Log::farm,
                        new TaskAttemptPolicy.Site("AntFarmDoFarmTaskList", "庄园饲料任务", bizKey, "",
                                (k) -> probeFarmStatus(bizKey)));
                isDoTask = outcome == Outcome.DONE || outcome == Outcome.TRIGGERED;
            }

            if (isDoTask) {
                Log.farm("饲料任务🧾完成[" + title + "]");
            } else {
                //Log.record("任务执行失败或跳过: " + title);
            }
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Throwable t) {
            Log.err(TAG, "doFarmTask err:", t);
        }
        return isDoTask;
    }

    private Boolean receiveFarmTaskAward(JSONObject task) {
        // 领奖繁忙只暂停领奖，不能中断列表中尚未执行的任务。
        if (farmTaskAwardBusy) {
            Log.record("庄园饲料任务⏭️[" + task.optString("title") + "]#本轮领奖繁忙，未发送领奖请求；待办任务继续处理");
            return false;
        }
        try {
            String taskId = task.optString("taskId");
            String awardType = task.optString("awardType", "");
            int awardCount = pendingAward(task);
            // 小额奖励（1-3 个，如美食按个数计）：饲料奖励都是 30 的倍数，1-3 不可能是饲料；
            // 该判断同时用于领取前容量检查和领取后库存更新
            boolean isSmallPieceReward = awardCount >= 1 && awardCount <= 3;
            if (Objects.equals(awardType, "ALLPURPOSE")) {
                if (awardCount + foodStock > foodStockLimit) {
                    if (isSmallPieceReward) {
                        Log.record("饲料领取🎖️任务[" + task.optString("title", "") + "]小额[" + awardCount + "个]直接领取");
                    } else {
                        unReceiveTaskAward++;
                        Log.record("饲料领取：跳过[" + task.optString("title", "") + "]，待领=" + awardCount
                                + "g，库存=" + foodStock + "g，上限=" + foodStockLimit + "g；本次未发送领奖请求");
                        return false;
                    }
                }
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveFarmTaskAward(taskId, awardType));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                // 领奖收口：先按任务列表复核"已领到"，未确认才交自动拉黑（顺序由 TaskAward 固定）
                if (TaskAward.confirmReceivedOrBlackList("饲料领取🎖️任务",
                        k -> probeFarmStatus(task.optString("bizKey")), task.optString("bizKey"),
                        task.optString("title", ""),
                        () -> MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList",
                                task.optString("title", ""), jo),
                        msg -> Log.farm(msg))) {
                    return true;
                }
                // 服务端繁忙(102)：本轮不再继续领其余任务，避免整轮反复白刷（请求本身已经发出过）
                if (MessageUtil.isServerBusy(jo) && !farmTaskAwardBusy) {
                    farmTaskAwardBusy = true;
                    Log.record("服务端繁忙🌧️本轮跳过剩余饲料任务领取");
                }
                // 留痕：非饲料奖励（美食/道具等）领失败时，把任务定位信息打出来，
                // 否则只能看到 checkMemo 的通用报错，不知道是哪类奖励调不通
                Log.record("饲料任务⚠️领取失败[" + task.optString("title", "") + "]#类型[" + awardType
                        + "]数量[" + awardCount + "]#memo[" + jo.optString("memo") + "]#resultCode[" + jo.optString("resultCode") + "]");
                //检查并标记黑名单任务
                return false;
            }
            String title = task.optString("title", "");
            if (awardType.equals("ALLPURPOSE") && !isSmallPieceReward) {
                add2FoodStock(awardCount);
                Log.farm("饲料领取🎖️任务[" + title + "]奖励#获得[" + awardCount + "g]");
            } else if (awardType.equals("ALLPURPOSE")) {
                // 兼容小额按个数计的奖励，不计入饲料库存
                Log.farm("饲料领取🎖️任务[" + title + "]奖励#获得[" + awardCount + "个]");
            } else if (awardType.equals("CUISINE")) {
                String amount = awardCount > 0 ? "×" + awardCount + "个" : "（数量未返回）";
                Log.farm("美食领取🍱任务[" + title + "]奖励#获得[爱心美食" + amount + "]");
            } else {
                // 非饲料奖励（工具等）：RPC 已成功，原先返回 false 会让按轮执行把它一直当成“没做完”
                Log.farm("饲料领取🎖️任务[" + title + "]奖励#类型[" + awardType + "]");
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "receiveFarmTaskAward err:", t);
        }
        return false;
    }

    private void checkUnReceiveTaskAward() {
        if (unReceiveTaskAward > 0) {
            Log.farm("还有待领取的饲料");
            unReceiveTaskAward = 0;
            listFarmTask(TaskStatus.FINISHED);
        }
    }

    private void feedAnimal(String farmId) {
        if (feedTroughFullThisRun) {
            return;
        }
        try {
            syncAnimalStatus(ownerFarmId);
            if (foodStock < 180) {
                Log.record("剩余饲料不足以投喂小鸡");
                return;
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.feedAnimal(farmId));
            boolean ok = MessageUtil.checkMemo(TAG, jo);
            if (!ok && CODE_FEED_TROUGH_FULL.equals(jo.optString("resultCode"))) {
                // 服务端回读：饲料槽已满，本轮不再投喂 (下一轮再看)
                feedTroughFullThisRun = true;
                Log.record("投喂小鸡⏭️饲料槽已满，本轮不再投喂");
                return;
            }
            if (ok) {
                int feedFood = foodStock - jo.optInt("foodStock");
                add2FoodStock(-feedFood);
                Log.farm("投喂小鸡🥣消耗[" + feedFood + "g]#剩余[" + foodStock + "g饲料]");
                if (useAccelerateTool.getValue()) {
                    TimeUtil.sleep(1000);
                    useAccelerateTool();
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "feedAnimal err:", t);
        } finally {
            long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
            String taskId = "UPDATE|FA|" + farmId;
            addChildTask(new ChildModelTask(taskId, "UPDATE", this::autoFeedAnimal, updateTime));
        }
    }

    private void listFarmTool() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTool());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray jaToolList = jo.optJSONArray("toolList");
            if (jaToolList == null) {
                return;
            }
            farmTools = new FarmTool[jaToolList.length()];
            for (int i = 0; i < jaToolList.length(); i++) {
                jo = jaToolList.optJSONObject(i);
                farmTools[i] = new FarmTool();
                if (jo == null) {
                    continue;
                }
                farmTools[i].toolId = jo.optString("toolId", "");
                farmTools[i].toolType = ToolType.valueOf(jo.optString("toolType"));
                farmTools[i].toolCount = jo.optInt("toolCount");
                farmTools[i].toolHoldLimit = jo.optInt("toolHoldLimit", 20);
            }
        } catch (Throwable t) {
            Log.err(TAG, "listFarmTool err:", t);
        }
    }

    private void useAccelerateTool() {
        useAccelerateTool(false);
    }

    private void useAccelerateTool(boolean releaseOneSlot) {
        if (!Status.canUseAccelerateToolToday()) {
            return;
        }
        syncAnimalStatus(ownerFarmId);
        if (releaseOneSlot && (!AnimalInteractStatus.HOME.name().equals(ownerAnimal.animalInteractStatus)
                || !AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus))) return;
        if ((!useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue") && AnimalBuff.ACCELERATING.name().equals(ownerAnimal.animalBuff)) || (useAccelerateToolOptions.getValue().contains("useAccelerateToolWhenMaxEmotion") && finalScore != 100)) {
            return;
        }
        double consumeSpeed = 0d;
        double foodHaveEatten = 0d;
        long nowTime = System.currentTimeMillis() / 1000;
        for (Animal animal : animals) {
            if (releaseOneSlot && (!Double.isFinite(animal.consumeSpeed) || animal.consumeSpeed < 0 || animal.startEatTime <= 0 || animal.startEatTime / 1000 > nowTime)) return;
            if (animal.masterFarmId.equals(ownerFarmId)) {
                consumeSpeed = animal.consumeSpeed;
            }
            foodHaveEatten += animal.consumeSpeed * (nowTime - animal.startEatTime / 1000);
        }
        if (!Double.isFinite(consumeSpeed) || consumeSpeed <= 0 || !Double.isFinite(foodHaveEatten) || !Double.isFinite(foodInTrough)) return;
        // consumeSpeed: g/s
        // AccelerateTool: -1h = -60m = -3600s
        while (foodInTrough - foodHaveEatten >= consumeSpeed * 3600 && useFarmTool(ownerFarmId, ToolType.ACCELERATETOOL)) {
            TimeUtil.sleep(1000);
            foodHaveEatten += consumeSpeed * 3600;
            if (!Status.canUseAccelerateToolToday()) {
                break;
            }
            if (releaseOneSlot || !useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue")) {
                break;
            }
        }
    }

    private static boolean farmFeatureOk(JSONObject response) {
        String code = response.optString("resultCode");
        if (!code.isEmpty() && !"SUCCESS".equals(code) && !"100".equals(code) && !"200".equals(code)) return false;
        return ("SUCCESS".equals(response.optString("memo")) || "SUCCESS".equals(response.optString("resultCode"))
                || Boolean.TRUE.equals(response.opt("success"))) && !RpcRequestGuard.isFailure(response);
    }

    private JSONObject findFeatureTool(String type) throws JSONException {
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTool());
        JSONArray tools = response.optJSONArray("toolList");
        if (!farmFeatureOk(response) || tools == null) { Log.record("庄园道具：库存查询失败或缺少列表，" + RpcRequestGuard.errorMessage(response)); return null; }
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool != null && type.equals(tool.optString("toolType")) && tool.optInt("toolCount", -1) > 0 && !tool.optString("toolId").isEmpty()) return tool;
        }
        Log.record("庄园道具：没有可用的" + type + "，跳过");
        return null;
    }

    private void useFenceTool() {
        try {
            TimeUtil.sleep(0);
            JSONObject state = MyUtils.newJSONObject(AntFarmRpcCall.syncAnimalStatus(ownerFarmId));
            JSONObject farm = state.optJSONObject("subFarmVO");
            if (!farmFeatureOk(state) || farm == null || !ownerFarmId.equals(farm.optString("farmId"))) { Log.record("篱笆卡：庄园状态查询失败或不符，跳过；" + RpcRequestGuard.errorMessage(state)); return; }
            JSONObject buff = state.optJSONObject("buffInfoVO");
            if (buff != null && "FENCE".equals(buff.optString("buffType"))) {
                if (!(buff.opt("hasBuffEffect") instanceof Boolean)) { Log.record("篱笆卡：效果状态无效，跳过"); return; }
                if (Boolean.TRUE.equals(buff.opt("hasBuffEffect"))) { Log.record("篱笆卡：仍在生效，剩余" + buff.optInt("buffCountDown", 0) / 60 + "分钟，跳过重复使用"); return; }
            }
            String flag = "farm::fenceUnconfirmed";
            if (Status.hasFlagToday(flag)) { Log.record("篱笆卡：本日已提交但结果未确认，避免重复消耗"); return; }
            JSONObject tool = findFeatureTool("FENCETOOL");
            if (tool == null) return;
            Status.flagToday(flag);
            JSONObject used = MyUtils.newJSONObject(AntFarmRpcCall.useFarmTool(ownerFarmId, tool.optString("toolId"), "FENCETOOL"));
            JSONObject after = MyUtils.newJSONObject(AntFarmRpcCall.syncAnimalStatus(ownerFarmId));
            JSONObject afterFarm = after.optJSONObject("subFarmVO"), afterBuff = after.optJSONObject("buffInfoVO");
            if (farmFeatureOk(after) && afterFarm != null && ownerFarmId.equals(afterFarm.optString("farmId")) && afterBuff != null
                    && "FENCE".equals(afterBuff.optString("buffType")) && Boolean.TRUE.equals(afterBuff.opt("hasBuffEffect"))) {
                Status.clearFlag(flag);
                String message = "篱笆卡：使用成功，已确认篱笆生效"; Log.record(message); Log.farm(message);
            } else { Log.record("篱笆卡：使用未确认，code=" + used.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(used)); }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("篱笆卡：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "useFenceTool", t); }
    }

    private void supplementDolls() {
        supplementDolls(Integer.MAX_VALUE);
    }

    private void supplementDolls(int maxUses) {
        try {
            TimeUtil.sleep(0);
            JSONObject tool = findFeatureTool("DOLLTOOL");
            if (tool == null) return;
            JSONObject cabin = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            JSONArray dolls = cabin.optJSONArray("loveCabinDollList");
            if (!farmFeatureOk(cabin) || dolls == null) { Log.record("公仔补签：已拥有公仔查询失败或缺少列表，停止"); return; }
            Set<String> owned = new HashSet<>();
            for (int i = 0; i < dolls.length(); i++) { JSONObject doll = dolls.optJSONObject(i); if (doll != null) owned.add(doll.optString("dollId")); }
            Calendar cal = MyUtils.getInstance();
            int end = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH), start = 2022 * 12 + 9;
            List<String> missing = new ArrayList<>();
            for (int index = start; index <= end; index++) {
                String id = String.format(Locale.CHINA, "%04d_%02d_MONTH_DOLL", index / 12, index % 12 + 1);
                if (!owned.contains(id)) missing.add(id);
            }
            if (dollSupplementOrder.getValue() == 1) Collections.reverse(missing);
            if (missing.isEmpty()) { Log.record("公仔补签：月度公仔已集齐，无须补签"); return; }
            int count = Math.min(tool.optInt("toolCount"), maxUses), confirmed = 0;
            for (String id : missing) {
                TimeUtil.sleep(0);
                if (confirmed >= count) break;
                JSONObject query = MyUtils.newJSONObject(AntFarmRpcCall.queryAntfarmDoll(id));
                JSONObject info = query.optJSONObject("dollInfoVO");
                if (!farmFeatureOk(query) || info == null || !(info.opt("acquired") instanceof Boolean)) { Log.record("公仔补签：公仔详情查询失败或缺少持有状态，停止；" + RpcRequestGuard.errorMessage(query)); return; }
                if (Boolean.TRUE.equals(info.opt("acquired")) || info.optString("achievementId").isEmpty()) continue;
                String flag = "farm::dollUnconfirmed::" + id;
                if (Status.hasFlagToday(flag)) { Log.record("公仔补签：本日该公仔已提交但结果未确认，停止重复补签"); return; }
                Status.flagToday(flag);
                JSONObject used = MyUtils.newJSONObject(AntFarmRpcCall.useDollTool(ownerFarmId, tool.optString("toolId"), info.optString("achievementId"), id));
                JSONObject after = MyUtils.newJSONObject(AntFarmRpcCall.queryAntfarmDoll(id));
                JSONObject acquired = after.optJSONObject("dollInfoVO");
                if (!farmFeatureOk(after) || acquired == null || !Boolean.TRUE.equals(acquired.opt("acquired"))) {
                    Log.record("公仔补签：补签结果未确认，停止；code=" + used.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(used)); return;
                }
                Status.clearFlag(flag); confirmed++;
                String message = "公仔补签：成功获得[" + info.optString("dollName", id) + "]"; Log.record(message); Log.farm(message);
                if (confirmed < count) { tool = findFeatureTool("DOLLTOOL"); if (tool == null) break; }
                TimeUtil.sleep(300);
            }
            Log.record("公仔补签：本轮确认获得" + confirmed + "只公仔");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("公仔补签：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "supplementDolls", t); }
    }

    private boolean likeChickenDiary(String date) throws JSONException {
        if (!date.isEmpty() && !date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) { Log.record("日记点赞：列表日期无效，停止"); return false; }
        JSONObject query = MyUtils.newJSONObject(AntFarmRpcCall.queryChickenDiary(date));
        JSONObject data = query.optJSONObject("data"), diary = data == null ? null : data.optJSONObject("chickenDiary");
        if (!farmFeatureOk(query) || diary == null || !(diary.opt("collectStatus") instanceof Boolean) || diary.optString("diaryId").isEmpty()) {
            Log.record("日记点赞：详情查询失败或关键字段缺失，停止；" + RpcRequestGuard.errorMessage(query)); return false;
        }
        if (Boolean.TRUE.equals(diary.opt("collectStatus"))) return true;
        JSONObject liked = MyUtils.newJSONObject(AntFarmRpcCall.collectChickenDiary(diary.optString("diaryId")));
        JSONObject after = MyUtils.newJSONObject(AntFarmRpcCall.queryChickenDiary(date));
        JSONObject afterData = after.optJSONObject("data"), afterDiary = afterData == null ? null : afterData.optJSONObject("chickenDiary");
        if (farmFeatureOk(after) && afterDiary != null && diary.optString("diaryId").equals(afterDiary.optString("diaryId")) && Boolean.TRUE.equals(afterDiary.opt("collectStatus"))) {
            String message = "日记点赞：成功[" + diary.optString("diaryDateStr", date) + "]"; Log.record(message); Log.farm(message); return true;
        }
        Log.record("日记点赞：结果未确认，停止；code=" + liked.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(liked)); return false;
    }

    private void likeChickenDiaries() {
        try {
            TimeUtil.sleep(0);
            int scope = collectChickenDiary.getValue();
            String flag = "farm::diaryLikeDone::" + scope;
            if (Status.hasFlagToday(flag)) { Log.record("日记点赞：今日所选范围已处理，跳过"); return; }
            if (!likeChickenDiary("")) return;
            if (scope <= 1) { Status.flagToday(flag); Log.record("日记点赞：今日处理完成"); return; }
            Calendar month = MyUtils.getInstance(); month.set(Calendar.DAY_OF_MONTH, 1);
            Set<String> seenDates = new HashSet<>();
            // ponytail: 历史最多回溯120个月，防止服务端错误翻页导致死循环。
            for (int page = 0; page < 120; page++) {
                TimeUtil.sleep(0);
                String key = String.format(Locale.CHINA, "%04d-%02d", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1);
                JSONObject list = MyUtils.newJSONObject(AntFarmRpcCall.queryChickenDiaryList(key));
                JSONObject data = list.optJSONObject("data");
                JSONArray rows = data == null ? null : data.optJSONArray("chickenDiaryBriefList");
                if (!farmFeatureOk(list) || rows == null) { Log.record("日记点赞：月份列表查询失败或缺失，停止；" + RpcRequestGuard.errorMessage(list)); return; }
                for (int i = rows.length() - 1; i >= 0; i--) {
                    TimeUtil.sleep(0);
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null || row.optString("dateStr").isEmpty()) { Log.record("日记点赞：列表缺少日期，停止"); return; }
                    String date = row.optString("dateStr");
                    if (Boolean.TRUE.equals(row.opt("collectStatus")) || !seenDates.add(date)) continue;
                    if (!likeChickenDiary(date)) return;
                    TimeUtil.sleep(200);
                }
                if (scope == 3 && !(data.opt("hasPreviousMore") instanceof Boolean)) { Log.record("日记点赞：历史分页状态缺失，停止；未记完成"); return; }
                if (scope == 2 || Boolean.FALSE.equals(data.opt("hasPreviousMore"))) { Status.flagToday(flag); Log.record("日记点赞：所选范围处理完成"); return; }
                if (rows.length() == 0) { Log.record("日记点赞：历史翻页无数据，停止"); return; }
                month.add(Calendar.MONTH, -1);
            }
            Log.record("日记点赞：达到120个月查询上限，超出范围的历史未处理");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("日记点赞：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "likeChickenDiaries", t); }
    }

    private Boolean useFarmTool(String targetFarmId, ToolType toolType) {
        try {
            TimeUtil.sleep(0);
            if (toolType == ToolType.BIG_EATER_TOOL && useFullRewardTool.getValue()) {
                return targetFarmId != null && targetFarmId.equals(ownerFarmId) && useBigEaterRewardTool();
            }
            if (toolType == ToolType.ACCELERATETOOL && !Status.canUseAccelerateToolToday()) return false;
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTool());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONArray jaToolList = jo.optJSONArray("toolList");
            if (jaToolList == null) {
                return false;
            }
            for (int i = 0; i < jaToolList.length(); i++) {
                jo = jaToolList.optJSONObject(i);
                if (jo == null || !toolType.name().equals(jo.optString("toolType"))) {
                    continue;
                }
                int toolCount = jo.optInt("toolCount");
                if (toolCount == 0) {
                    return false;
                }
                String toolId = jo.optString("toolId");
                jo = MyUtils.newJSONObject(AntFarmRpcCall.useFarmTool(targetFarmId, toolId, toolType.name()));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    if (toolType == ToolType.ACCELERATETOOL) Status.useAccelerateToolToday();
                    Log.farm("使用道具🎭[" + toolType.nickName() + "]#剩余" + (toolCount - 1) + "张");
                    return true;
                } else if (Objects.equals("3D16", jo.optString("resultCode"))) {
                    Status.flagToday("farm::useFarmToolLimit::" + toolType);
                }
                break;
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "useFarmTool err:", t);
        }
        return false;
    }

    private JSONObject queryBigEaterState() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject root = MyUtils.newJSONObject(AntFarmRpcCall.syncAnimalStatus(ownerFarmId));
        JSONObject farm = root.optJSONObject("subFarmVO");
        if (!farmFeatureOk(root) || farm == null || !(farm.opt("farmId") instanceof String)
                || !ownerFarmId.equals(farm.optString("farmId")) || !(farm.opt("useBigEaterTool") instanceof Boolean)) return null;
        JSONArray animals = farm.optJSONArray("animals");
        if (animals == null || animals.length() > 50) return null;
        int owners = 0;
        for (int i = 0; i < animals.length(); i++) {
            JSONObject animal = animals.optJSONObject(i);
            if (animal == null || !(animal.opt("masterFarmId") instanceof String)) return null;
            if (!ownerFarmId.equals(animal.optString("masterFarmId"))) continue;
            owners++;
            JSONObject state = animal.optJSONObject("animalStatusVO");
            if (state == null || !AnimalInteractStatus.HOME.name().equals(state.opt("animalInteractStatus"))
                    || !AnimalFeedStatus.EATING.name().equals(state.opt("animalFeedStatus"))) return null;
        }
        return owners == 1 ? farm : null;
    }

    /** 满仓策略启用时，普通用卡与腾位共用次数和未知结果保护。 */
    private boolean useBigEaterRewardTool() {
        TimeUtil.sleep(0);
        if (!useFullRewardTool.getValue() || !useBigEaterTool.getValue() || ownerFarmId == null || ownerFarmId.isEmpty()
                || ownerUserId == null || !ownerUserId.equals(UserIdMap.getCurrentUid())
                || Status.getIntFlagToday("farm::bigEaterRewardAttempts") >= 2 || Status.hasFlagToday("farm::bigEaterRewardUnconfirmed")) return false;
        try {
            JSONObject state = queryBigEaterState();
            if (state == null || !Boolean.FALSE.equals(state.opt("useBigEaterTool"))) return false;
            JSONObject before = rewardToolStock(ToolType.BIG_EATER_TOOL);
            if (before == null || npcTaskNumber(before, "toolCount") <= 0) return false;
            state = queryBigEaterState();
            if (state == null || !Boolean.FALSE.equals(state.opt("useBigEaterTool")) || !useFullRewardTool.getValue()
                    || !useBigEaterTool.getValue() || !ownerUserId.equals(UserIdMap.getCurrentUid())) return false;
            TimeUtil.sleep(0);
            Status.setIntFlagToday("farm::bigEaterRewardAttempts", Status.getIntFlagToday("farm::bigEaterRewardAttempts") + 1);
            Status.flagToday("farm::bigEaterRewardUnconfirmed");
            JSONObject accepted = MyUtils.newJSONObject(AntFarmRpcCall.useFarmTool(ownerFarmId, before.optString("toolId"), ToolType.BIG_EATER_TOOL.name()));
            JSONObject after = rewardToolStock(ToolType.BIG_EATER_TOOL);
            state = queryBigEaterState();
            if (!farmFeatureOk(accepted) || after == null || state == null || !Boolean.TRUE.equals(state.opt("useBigEaterTool"))
                    || npcTaskNumber(after, "toolHoldLimit") != npcTaskNumber(before, "toolHoldLimit")
                    || npcTaskNumber(after, "toolCount") != npcTaskNumber(before, "toolCount") - 1) return false;
            Status.clearFlag("farm::bigEaterRewardUnconfirmed");
            Log.farm("使用加饭卡🎭#库存扣减与生效确认");
            return true;
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable e) { Log.err(TAG, "useBigEaterRewardTool", e); return false; }
    }

    private void feedFriend() {
        try {
            Map<String, Integer> feedFriendAnimalMap = feedFriendAnimalList.getValue();
            for (Map.Entry<String, Integer> entry : feedFriendAnimalMap.entrySet()) {
                String userId = entry.getKey();
                if (userId.equals(UserIdMap.getCurrentUid())) {
                    continue;
                }
                if (!Status.canFeedFriendToday(userId, entry.getValue())) {
                    continue;
                }
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", userId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    continue;
                }
                JSONObject farmVO = jo.optJSONObject("farmVO");
                jo = farmVO != null ? farmVO.optJSONObject("subFarmVO") : null;
                if (jo == null) {
                    continue;
                }
                String friendFarmId = jo.optString("farmId");
                int foodInTrough = jo.optInt("foodInTrough", 0);

                // 食槽为空时帮喂
                if (foodInTrough == 0) {
                    JSONArray jaAnimals = jo.optJSONArray("animals");
                    if (jaAnimals == null) {
                        continue;
                    }
                    for (int j = 0; j < jaAnimals.length(); j++) {
                        JSONObject animal = jaAnimals.optJSONObject(j);
                        if (animal == null) {
                            continue;
                        }
                        String masterFarmId = animal.optString("masterFarmId");

                        // 只处理好友自己的小鸡
                        if (masterFarmId.equals(friendFarmId)) {
                            // 检查小鸡是否太小
                            if (animal.optBoolean("littleChick", false)) {
                                Log.record("跳过帮喂：好友的小鸡太小");
                                break;
                            }

                            JSONObject animalStatusVO = animal.optJSONObject("animalStatusVO");
                            if (animalStatusVO == null) {
                                break;
                            }
                            String animalInteractStatus = animalStatusVO.optString("animalInteractStatus");
                            String animalFeedStatus = animalStatusVO.optString("animalFeedStatus");

                            // 好友自己的小鸡在家且饥饿 → 帮喂
                            if (AnimalInteractStatus.HOME.name().equals(animalInteractStatus) 
                                && AnimalFeedStatus.HUNGRY.name().equals(animalFeedStatus)) {
                                feedFriendAnimal(friendFarmId);
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "feedFriend err:", t);
        }
    }

    private void feedFriendAnimal(String friendFarmId) {
        // 当日帮喂总数已达上限（服务端 391 已记录）时直接跳过，避免逐个好友白跑请求
        if (Status.hasFlagToday(Status.FLAG_FEED_FRIEND_ANIMAL_LIMIT)) {
            Log.record("今日帮喂次数已达上限🥣，跳过喂养");
            return;
        }
        try {
            String userId = AntFarmRpcCall.farmId2UserId(friendFarmId);
            String maskName = UserIdMap.getMaskName(userId);
            Log.record("[" + maskName + "]的小鸡在挨饿");
            if (foodStock < 180) {
                Log.record("喂鸡饲料不足");
                checkUnReceiveTaskAward();
                if (foodStock < 180) {
                    return;
                }
            }
            String groupId = null;
            if (family.getValue()) {
                groupId = getFamilyGroupId(userId);
                if (StringUtil.isEmpty(groupId) || !Objects.equals(ownerGroupId, groupId)) {
                    groupId = null;
                }
            }
            if (feedFriendAnimal(friendFarmId, groupId)) {
                String s = StringUtil.isEmpty(groupId) ? "帮喂小鸡🥣帮喂好友" : "亲密家庭🏠帮喂成员";
                s = s + "[" + maskName + "]" + "的小鸡#剩余[" + foodStock + "g饲料]";
                Log.farm(s);
                Status.feedFriendToday(AntFarmRpcCall.farmId2UserId(friendFarmId));
            }
        } catch (Throwable t) {
            Log.err(TAG, "feedFriendAnimal err:", t);
        }
    }

    private Boolean feedFriendAnimal(String friendFarmId, String groupId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.feedFriendAnimal(friendFarmId, groupId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                if (Objects.equals("391", jo.optString("resultCode"))) {
                    Status.flagToday(Status.FLAG_FEED_FRIEND_ANIMAL_LIMIT);
                }
                return false;
            }
            int feedFood = foodStock - jo.optInt("foodStock");
            if (feedFood > 0) {
                add2FoodStock(-feedFood);
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "feedFriendAnimal err:", t);
        }
        return false;
    }

    private void notifyFriend() {
        if (foodStock >= foodStockLimit) {
            return;
        }
        try {
            boolean hasNext = false;
            int pageStartSum = 0;
            String s;
            JSONObject jo;
            do {
                s = AntFarmRpcCall.rankingList(pageStartSum);
                jo = MyUtils.newJSONObject(s);
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    break;
                }
                hasNext = jo.optBoolean("hasNext");
                JSONArray jaRankingList = jo.optJSONArray("rankingList");
                if (jaRankingList == null) {
                    break;
                }
                pageStartSum += jaRankingList.length();
                for (int i = 0; i < jaRankingList.length(); i++) {
                    jo = jaRankingList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    String userId = jo.optString("userId");
                    String userName = UserIdMap.getMaskName(userId);
                    boolean isNotifyFriend = notifyFriendList.getValue().contains(userId);
                    if (notifyFriendType.getValue() != NotifyFriendType.NOTIFY) {
                        isNotifyFriend = !isNotifyFriend;
                    }
                    if (!isNotifyFriend || userId.equals(UserIdMap.getCurrentUid())) {
                        continue;
                    }
                    boolean starve = jo.has("actionType") && "starve_action".equals(jo.optString("actionType"));
                    if (jo.optBoolean("stealingAnimal") && !starve) {
                        jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", userId));
                        if (!MessageUtil.checkMemo(TAG, jo)) {
                            continue;
                        }
                        JSONObject farmVO = jo.optJSONObject("farmVO");
                        jo = farmVO != null ? farmVO.optJSONObject("subFarmVO") : null;
                        if (jo == null) {
                            continue;
                        }
                        String friendFarmId = jo.optString("farmId");
                        JSONArray jaAnimals = jo.optJSONArray("animals");
                        if (jaAnimals == null) {
                            continue;
                        }
                        for (int j = 0; j < jaAnimals.length(); j++) {
                            jo = jaAnimals.optJSONObject(j);
                            if (jo == null) {
                                continue;
                            }
                            String animalId = jo.optString("animalId");
                            String masterFarmId = jo.optString("masterFarmId");
                            if (!masterFarmId.equals(friendFarmId) && !masterFarmId.equals(ownerFarmId)) {
                                JSONObject animalStatusVO = jo.optJSONObject("animalStatusVO");
                                if (animalStatusVO != null && notifyFriend(animalStatusVO, friendFarmId, animalId, userName)) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } while (hasNext);
            Log.record("饲料剩余[" + foodStock + "g]");
        } catch (Throwable t) {
            Log.err(TAG, "notifyFriend err:", t);
        }
    }

    private Boolean notifyFriend(JSONObject joAnimalStatusVO, String friendFarmId, String animalId, String user) {
        try {
            if (AnimalInteractStatus.STEALING.name().equals(joAnimalStatusVO.optString("animalInteractStatus")) && AnimalFeedStatus.EATING.name().equals(joAnimalStatusVO.optString("animalFeedStatus"))) {
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.notifyFriend(animalId, friendFarmId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return false;
                }
                int rewardCount = (int) jo.optDouble("rewardCount");
                if (jo.optBoolean("refreshFoodStock")) {
                    foodStock = (int) jo.optDouble("finalFoodStock");
                } else {
                    add2FoodStock(rewardCount);
                }
                Log.farm("通知赶鸡📧提醒[" + user + "]被偷吃#获得[" + rewardCount + "g饲料]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "notifyFriend err:", t);
        }
        return false;
    }

    private void parseSyncAnimalStatusResponse(String resp) {
        try {
            JSONObject jo = MyUtils.newJSONObject(resp);
            if (!jo.has("subFarmVO")) {
                return;
            }
            JSONObject emotionInfo = jo.optJSONObject("emotionInfo");
            if (emotionInfo != null) {
                finalScore = emotionInfo.optDouble("finalScore", finalScore);
            }
            JSONObject subFarmVO = jo.optJSONObject("subFarmVO");
            if (subFarmVO == null) {
                return;
            }
            if (subFarmVO.has("foodStock")) {
                foodStock = subFarmVO.optInt("foodStock");
            }
            if (subFarmVO.has("foodInTrough")) {
                foodInTrough = subFarmVO.optInt("foodInTrough");
            }
            JSONObject manureVO = subFarmVO.optJSONObject("manureVO");
            if (manureVO != null) {
                JSONArray manurePotList = manureVO.optJSONArray("manurePotList");
                if (manurePotList != null) {
                    for (int i = 0; i < manurePotList.length(); i++) {
                        JSONObject manurePot = manurePotList.optJSONObject(i);
                        if (manurePot == null) {
                            continue;
                        }
                        if (manurePot.optInt("manurePotNum") >= 100) {
                            JSONObject joManurePot = MyUtils.newJSONObject(AntFarmRpcCall.collectManurePot(manurePot.optString("manurePotNO")));
                            if (joManurePot.optBoolean("success")) {
                                int collectManurePotNum = joManurePot.optInt("collectManurePotNum");
                                Log.farm("打扫鸡屎🧹获得[" + collectManurePotNum + "g肥料]");
                            }
                        }
                    }
                }
            }
            ownerFarmId = subFarmVO.optString("farmId");
            JSONObject farmProduce = subFarmVO.optJSONObject("farmProduce");
            if (farmProduce != null) {
                benevolenceScore = farmProduce.optDouble("benevolenceScore", benevolenceScore);
            }
            JSONArray jaRewardList = subFarmVO.optJSONArray("rewardList");
            if (jaRewardList != null && jaRewardList.length() > 0) {
                rewardList = new RewardFriend[jaRewardList.length()];
                for (int i = 0; i < rewardList.length; i++) {
                    JSONObject joRewardList = jaRewardList.optJSONObject(i);
                    if (joRewardList == null) {
                        continue;
                    }
                    if (rewardList[i] == null) {
                        rewardList[i] = new RewardFriend();
                    }
                    rewardList[i].consistencyKey = joRewardList.optString("consistencyKey");
                    rewardList[i].friendId = joRewardList.optString("friendId");
                    rewardList[i].time = joRewardList.optString("time");
                }
            }
            JSONArray jaAnimals = subFarmVO.optJSONArray("animals");
            if (jaAnimals == null) {
                return;
            }
            animals = new Animal[jaAnimals.length()];
            for (int i = 0; i < animals.length; i++) {
                Animal animal = new Animal();
                JSONObject animalJsonObject = jaAnimals.optJSONObject(i);
                if (animalJsonObject == null) {
                    animals[i] = animal;
                    continue;
                }
                animal.animalId = animalJsonObject.optString("animalId");
                animal.currentFarmId = animalJsonObject.optString("currentFarmId");
                animal.masterFarmId = animalJsonObject.optString("masterFarmId");
                animal.animalBuff = animalJsonObject.optString("animalBuff");
                animal.subAnimalType = animalJsonObject.optString("subAnimalType");
                animal.currentFarmMasterUserId = animalJsonObject.optString("currentFarmMasterUserId");
                animal.locationType = animalJsonObject.optString("locationType", "");
                JSONObject animalStatusVO = animalJsonObject.optJSONObject("animalStatusVO");
                if (animalStatusVO != null) {
                    animal.animalFeedStatus = animalStatusVO.optString("animalFeedStatus");
                    animal.animalInteractStatus = animalStatusVO.optString("animalInteractStatus");
                }
                animal.startEatTime = animalJsonObject.optLong("startEatTime");
                animal.beHiredEndTime = animalJsonObject.optLong("beHiredEndTime");
                animal.consumeSpeed = animalJsonObject.optDouble("consumeSpeed");
                animal.foodHaveEatten = animalJsonObject.optDouble("foodHaveEatten");
                if (animal.masterFarmId.equals(ownerFarmId)) {
                    ownerAnimal = animal;
                }
                animals[i] = animal;
            }
        } catch (Throwable t) {
            Log.err(TAG, "parseSyncAnimalStatusResponse err:", t);
        }
    }

    private void add2FoodStock(int i) {
        foodStock += i;
        if (foodStock > foodStockLimit) {
            foodStock = foodStockLimit;
        }
        if (foodStock < 0) {
            foodStock = 0;
        }
    }

    private void collectDailyFoodMaterial(String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyFoodMaterial = jo.optBoolean("canCollectDailyFoodMaterial");
            int dailyFoodMaterialAmount = jo.optInt("dailyFoodMaterialAmount");
            int garbageAmount = jo.optInt("garbageAmount", 0);
            JSONObject orchardFoodMaterialStatus = jo.optJSONObject("orchardFoodMaterialStatus");
            if (orchardFoodMaterialStatus != null) {
                if ("FINISHED".equals(orchardFoodMaterialStatus.optString("foodStatus"))) {
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.farmFoodMaterialCollect());
                    if ("100".equals(jo.optString("resultCode"))) {
                        Log.farm("小鸡厨房👨🏻‍🍳农场食材#领取[" + jo.optInt("foodMaterialAddCount") + "g食材]");
                    } else {
                        Log.i(TAG, jo.toString());
                    }
                }
            }
            if (canCollectDailyFoodMaterial) {
                jo = MyUtils.newJSONObject(AntFarmRpcCall.collectDailyFoodMaterial(dailyFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳今日食材#领取[" + dailyFoodMaterialAmount + "g食材]");
                }
            }
            if (garbageAmount > 0) {
                jo = MyUtils.newJSONObject(AntFarmRpcCall.collectKitchenGarbage());
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳收集厨余#获得[" + jo.optInt("recievedKitchenGarbageAmount") + "g肥料]");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectDailyFoodMaterial err:", t);
        }
    }

    private void collectDailyLimitedFoodMaterial() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryFoodMaterialPack());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            boolean canCollectDailyLimitedFoodMaterial = jo.optBoolean("canCollectDailyLimitedFoodMaterial");
            if (canCollectDailyLimitedFoodMaterial) {
                int dailyLimitedFoodMaterialAmount = jo.optInt("dailyLimitedFoodMaterialAmount");
                jo = MyUtils.newJSONObject(AntFarmRpcCall.collectDailyLimitedFoodMaterial(dailyLimitedFoodMaterialAmount));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("小鸡厨房👨🏻‍🍳领取[爱心食材店食材]#" + dailyLimitedFoodMaterialAmount + "g");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectDailyLimitedFoodMaterial err:", t);
        }
    }

    private void cook(String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterKitchen(userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            int cookTimesAllowed = jo.optInt("cookTimesAllowed");
            if (cookTimesAllowed > 0) {
                for (int i = 0; i < cookTimesAllowed; i++) {
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.cook(userId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        JSONObject cuisineVO = jo.optJSONObject("cuisineVO");
                        if (cuisineVO != null) {
                            Log.farm("小鸡厨房👨🏻‍🍳制作[" + cuisineVO.optString("name") + "]");
                        }
                    }
                    TimeUtil.sleep(RandomUtil.delay());
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "cook err:", t);
        }
    }

    private List<JSONObject> getSortedCuisineList(JSONArray cuisineList) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < cuisineList.length(); i++) {
            list.add(cuisineList.optJSONObject(i));
        }
        Collections.sort(list, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                int count1 = jsonObject1.optInt("count");
                int count2 = jsonObject2.optInt("count");
                return count2 - count1;
            }
        });
        return list;
    }

    private void useFarmFood(JSONArray cuisineList) {
        if (dynamicSpecialFood.getValue()) { useDynamicSpecialFood(0, null); return; }
        try {
            List<JSONObject> list = getSortedCuisineList(cuisineList);
            for (int i = 0; i < list.size(); i++) {
                if (!useFarmFood(list.get(i))) {
                    return;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "useFarmFood err:", t);
        }
    }

    private Boolean useFarmFood(JSONObject cuisine) {
        if (Status.hasFlagToday(DYNAMIC_FOOD_PENDING)) return false;
        JSONObject foodLedger = MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY));
        if (rankingDay(System.currentTimeMillis()).equals(foodLedger.optString("day")) && foodLedger.optBoolean("pending")) return false;
        if (!Status.canUseSpecialFoodToday()) {
            return false;
        }
        try {
            String cookbookId = cuisine.optString("cookbookId");
            String cuisineId = cuisine.optString("cuisineId");
            String name = cuisine.optString("name");
            int count = cuisine.optInt("count");
            for (int j = 0; j < count; j++) {
                if (!manualFarmOwner(ownerUserId) || !useSpecialFood.getValue()) return false;
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.useFarmFood(cookbookId, cuisineId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return false;
                }
                JSONObject foodEffect = jo.optJSONObject("foodEffect");
                double deltaProduce = foodEffect != null ? foodEffect.optDouble("deltaProduce") : 0;
                Log.farm("使用美食🍱[" + name + "]#加速" + deltaProduce + "颗爱心鸡蛋");
                Status.useSpecialFoodToday();
                if (!Status.canUseSpecialFoodToday()) {
                    break;
                }
            }
            return true;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "useFarmFood err:", t);
        }
        return false;
    }

    private static final String DYNAMIC_FOOD_KEY = "farmDynamicFood";
    private static final String DYNAMIC_FOOD_PENDING = "farm::dynamicFoodUnconfirmed";
    private static final int SPECIAL_FOOD_BATCH_LIMIT = 10;

    private static final class DynamicFoodState {
        final JSONArray cuisines;
        final double harvested, progress;
        DynamicFoodState(JSONArray cuisines, double harvested, double progress) {
            this.cuisines = cuisines; this.harvested = harvested; this.progress = progress;
        }
    }

    private static double foodNumber(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        if (!(value instanceof Number)) return Double.NaN;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 0 ? number : Double.NaN;
    }

    private boolean dynamicFoodOwner(String uid) {
        if (Thread.currentThread().isInterrupted()) throw new TaskCancelledException();
        return uid != null && uid.equals(UserIdMap.getCurrentUid()) && uid.equals(ownerUserId)
                && isEnable() && check() && useSpecialFood.getValue() && dynamicSpecialFood.getValue()
                && dynamicFoodDailyLimit.getValue() > 0;
    }

    private DynamicFoodState queryDynamicFoodState(String uid) throws JSONException {
        if (!dynamicFoodOwner(uid)) return null;
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", uid));
        JSONObject farm = response.optJSONObject("farmVO");
        JSONObject master = farm == null ? null : farm.optJSONObject("masterUserInfoVO");
        JSONObject sub = farm == null ? null : farm.optJSONObject("subFarmVO");
        if (!MessageUtil.checkMemo(TAG, response) || !dynamicFoodOwner(uid) || master == null
                || !uid.equals(master.opt("userId")) || sub == null || ownerFarmId == null || !ownerFarmId.equals(sub.opt("farmId"))) return null;
        JSONArray animalsNow = sub.optJSONArray("animals");
        if (animalsNow == null || animalsNow.length() > 1000) return null;
        JSONObject own = null;
        for (int i = 0; i < animalsNow.length(); i++) {
            JSONObject animal = animalsNow.optJSONObject(i);
            if (animal != null && ownerFarmId.equals(animal.opt("masterFarmId"))) {
                if (own != null) return null;
                own = animal.optJSONObject("animalStatusVO");
            }
        }
        if (own == null || !AnimalInteractStatus.HOME.name().equals(own.opt("animalInteractStatus"))
                || !(AnimalFeedStatus.EATING.name().equals(own.opt("animalFeedStatus"))
                || AnimalFeedStatus.HUNGRY.name().equals(own.opt("animalFeedStatus")))) return null;
        double harvested = foodNumber(farm, "harvestBenevolenceScore");
        double progress = foodNumber(sub.optJSONObject("farmProduce"), "benevolenceScore");
        JSONArray cuisines = response.optJSONArray("cuisineList");
        if (!Double.isFinite(harvested) || !Double.isFinite(progress) || cuisines == null || cuisines.length() > 1000) return null;
        JSONArray normalized = new JSONArray();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < cuisines.length(); i++) {
            JSONObject cuisine = cuisines.optJSONObject(i);
            if (cuisine == null || !(cuisine.opt("cookbookId") instanceof String) || !(cuisine.opt("cuisineId") instanceof String)) return null;
            String book = cuisine.optString("cookbookId"), id = cuisine.optString("cuisineId");
            if (book.isEmpty() || id.isEmpty() || book.length() > 128 || id.length() > 128
                    || book.contains("|") || id.contains("|") || !keys.add(book + "|" + id)) return null;
            int count = rankingInt(cuisine, cuisine.has("count") ? "count" : "stock");
            if (count < 0) return null;
            JSONObject item = MyUtils.newJSONObject(cuisine.toString()); item.put("count", count); normalized.put(item);
        }
        return new DynamicFoodState(normalized, harvested, progress);
    }

    private JSONObject dynamicFoodBudget() throws JSONException {
        String text = RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY);
        JSONObject state = MyUtils.newJSONObject(text);
        String today = rankingDay(System.currentTimeMillis());
        if (!text.isEmpty() && (!(state.opt("day") instanceof String) || rankingInt(state, "used") < 0
                || !(state.opt("pending") instanceof Boolean) || state.optJSONObject("benefits") == null)) return null;
        if (text.isEmpty()) { state.put("benefits", MyUtils.newJSONObject()); state.put("pending", false); }
        if (!today.equals(state.optString("day"))) { state.put("day", today); state.put("used", 0); state.put("pending", false); }
        return state;
    }

    private int dynamicFoodQuota(JSONObject budget) {
        if (budget == null || budget.optBoolean("pending") || Status.hasFlagToday(DYNAMIC_FOOD_PENDING)) return 0;
        int remaining = Math.max(0, dynamicFoodDailyLimit.getValue() - rankingInt(budget, "used"));
        int legacyLimit = useSpecialFoodCountLimit.getValue();
        if (legacyLimit > 0) remaining = Math.min(remaining, Math.max(0, legacyLimit - Status.INSTANCE.getUseSpecialFoodCount()));
        return remaining;
    }

    private static String foodKey(JSONObject cuisine) { return cuisine.optString("cookbookId") + "|" + cuisine.optString("cuisineId"); }

    private static int foodStock(DynamicFoodState state, String key) {
        for (int i = 0; i < state.cuisines.length(); i++) {
            JSONObject item = state.cuisines.optJSONObject(i);
            if (key.equals(foodKey(item))) return item.optInt("count");
        }
        return 0;
    }

    private JSONObject chooseDynamicFood(DynamicFoodState state, JSONObject benefits, double gap, int quota) {
        JSONObject unknown = null, chosen = null;
        double best = Double.POSITIVE_INFINITY;
        for (JSONObject cuisine : getSortedCuisineList(state.cuisines)) {
            if (cuisine.optInt("count") <= 0) continue;
            double unit = foodNumber(benefits, foodKey(cuisine));
            if (!Double.isFinite(unit) || unit <= 0) { if (unknown == null) unknown = cuisine; continue; }
            if (gap <= 0) return cuisine;
            int count = (int) Math.min(Math.min(SPECIAL_FOOD_BATCH_LIMIT, Math.min(quota, cuisine.optInt("count"))), Math.ceil(gap / unit));
            // ponytail: homogeneous batches make yield learning unambiguous; replan after every readback.
            double score = count * unit >= gap ? count * unit - gap : gap + (gap - count * unit);
            if (count > 0 && score < best) { best = score; chosen = cuisine; }
        }
        return chosen == null ? unknown : chosen;
    }

    private void useDynamicSpecialFood(double targetEggs, RankingSnapshot ranking) {
        boolean forRanking = ranking != null;
        String uid = ownerUserId;
        if (!dynamicFoodOwner(uid) || !Double.isFinite(targetEggs) || targetEggs < 0
                || (forRanking && (!rankingDonation.getValue() || !rankingFoodRefill.getValue()))) return;
        try {
            DynamicFoodState current = queryDynamicFoodState(uid);
            if (current == null) return;
            for (int batch = 0; batch < 1000 && dynamicFoodOwner(uid); batch++) {
                if (forRanking) {
                    if (!rankingDonation.getValue() || !rankingFoodRefill.getValue() || !uid.equals(ranking.owner)
                            || !rankingWindow(ranking, System.currentTimeMillis())) return;
                    JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
                    RankingSnapshot live = MessageUtil.checkMemo(TAG, response) ? rankingSnapshot(response, uid, ranking.weekly) : null;
                    if (live == null || !ranking.activity.equals(live.activity) || !ranking.round.equals(live.round)
                            || !rankingWindow(live, System.currentTimeMillis()) || targetEggs > rankingQuota(live)) return;
                }
                harvestBenevolenceScore = current.harvested; benevolenceScore = current.progress;
                if (forRanking && current.progress >= 1) {
                    double beforeHarvest = current.harvested;
                    harvestProduce(ownerFarmId);
                    current = queryDynamicFoodState(uid);
                    if (current == null || current.harvested <= beforeHarvest) return;
                    harvestBenevolenceScore = current.harvested; benevolenceScore = current.progress;
                }
                if (forRanking && current.harvested >= targetEggs) return;
                double gap = forRanking ? targetEggs - current.harvested - current.progress : 0;
                if (forRanking && gap <= 0) return;
                JSONObject budget = dynamicFoodBudget();
                int quota = dynamicFoodQuota(budget);
                if (quota <= 0) return;
                JSONObject benefits = budget.optJSONObject("benefits");
                JSONObject cuisine = chooseDynamicFood(current, benefits, gap, quota);
                if (cuisine == null) return;
                String key = foodKey(cuisine);
                double unit = foodNumber(benefits, key);
                int count = Double.isFinite(unit) && unit > 0 ? Math.min(SPECIAL_FOOD_BATCH_LIMIT, Math.min(quota, cuisine.optInt("count"))) : 1;
                if (forRanking && Double.isFinite(unit) && unit > 0) count = (int) Math.min(count, Math.ceil(gap / unit));
                if (count <= 0 || !dynamicFoodOwner(uid) || (forRanking && (!rankingFoodRefill.getValue()
                        || !rankingDonation.getValue() || !rankingWindow(ranking, System.currentTimeMillis())))) return;
                budget.put("used", rankingInt(budget, "used") + count); budget.put("pending", true);
                if (!RuntimeInfo.getInstance().putVerified(DYNAMIC_FOOD_KEY, budget.toString())) return;
                Status.flagToday(DYNAMIC_FOOD_PENDING);
                if (!dynamicFoodOwner(uid)) return;
                JSONObject item = MyUtils.newJSONObject();
                item.put("cookbookId", cuisine.optString("cookbookId")); item.put("cuisineId", cuisine.optString("cuisineId"));
                item.put("count", count); item.put("useCuisine", true);
                JSONObject result = MyUtils.newJSONObject(AntFarmRpcCall.useFarmFood(new JSONArray().put(item)));
                if (!MessageUtil.checkMemo(TAG, result) || !dynamicFoodOwner(uid)) return;
                JSONObject effect = result.optJSONObject("foodEffect");
                double delta = foodNumber(effect, "deltaProduce"), target = foodNumber(effect, "targetProduce");
                DynamicFoodState after = queryDynamicFoodState(uid);
                if (after == null || !Double.isFinite(delta) || delta <= 0 || !Double.isFinite(target)
                        || foodStock(current, key) - foodStock(after, key) != count
                        || after.progress + 0.000001 < current.progress + delta || after.progress + 0.000001 < target) return;
                benefits.put(key, delta / count); budget.put("pending", false);
                if (!RuntimeInfo.getInstance().putVerified(DYNAMIC_FOOD_KEY, budget.toString())) return;
                for (int i = 0; i < count; i++) Status.useSpecialFoodToday();
                Status.clearFlag(DYNAMIC_FOOD_PENDING);
                Log.farm("动态美食🍱[" + cuisine.optString("name") + "×" + count + "]#回查确认增加" + delta + "颗产蛋进度");
                current = after;
                TimeUtil.sleep(1000);
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable e) { Log.err(TAG, "useDynamicSpecialFood", e); }
    }

    private void drawLotteryPlus(JSONObject lotteryPlusInfo) {
        try {
            if (!lotteryPlusInfo.has("userSevenDaysGiftsItem")) {
                return;
            }
            String itemId = lotteryPlusInfo.optString("itemId");
            JSONObject jo = lotteryPlusInfo.optJSONObject("userSevenDaysGiftsItem");
            if (jo == null) {
                return;
            }
            JSONArray ja = jo.optJSONArray("userEverydayGiftItems");
            if (ja == null) {
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (jo.optString("itemId").equals(itemId)) {
                    if (!jo.optBoolean("received")) {
                        String singleDesc = jo.optString("singleDesc");
                        int awardCount = jo.optInt("awardCount");
                        if (singleDesc.contains("饲料") && awardCount + foodStock > foodStockLimit) {
                            Log.record("暂停领取[" + awardCount + "]克饲料，上限为[" + foodStockLimit + "]克");
                            break;
                        }
                        jo = MyUtils.newJSONObject(AntFarmRpcCall.drawLotteryPlus());
                        if (MessageUtil.checkMemo(TAG, jo)) {
                            Log.farm("惊喜礼包🎁[" + singleDesc + "*" + awardCount + "]");
                        }
                    } else {
                        Log.record("当日奖励已领取");
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "drawLotteryPlus err:", t);
        }
    }

    private void visitFriend() {
        Map<String, Integer> map = visitFriendList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            String userId = entry.getKey();
            Integer countLimit = entry.getValue();
            if (userId.equals(UserIdMap.getCurrentUid())) {
                continue;
            }
            if (Status.canVisitFriendToday(userId, countLimit)) {
                visitFriend(userId, countLimit);
            }
        }
    }

    private void visitFriend(String userId, int countLimit) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm(userId));
            if (RpcRequestGuard.isNonFriend("com.alipay.antfarm.enterFarm", jo)) return;
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject farmVO = jo.optJSONObject("farmVO");
            if (farmVO == null) {
                return;
            }
            foodStock = farmVO.optInt("foodStock");
            JSONObject subFarmVO = farmVO.optJSONObject("subFarmVO");
            if (subFarmVO == null) {
                return;
            }
            if (subFarmVO.optBoolean("visitedToday", true)) {
                Status.flagToday("farm::visitFriendLimit::" + userId);
                return;
            }
            String farmId = subFarmVO.optString("farmId");
            while (Status.canVisitFriendToday(userId, countLimit) && foodStock >= 10) {
                jo = MyUtils.newJSONObject(AntFarmRpcCall.visitFriend(farmId));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    break;
                }
                TimeUtil.sleep(1000);
                Status.visitFriendToday(userId);
                foodStock = jo.optInt("foodStock");
                Log.farm("赠送麦子🌾赠送[" + UserIdMap.getMaskName(userId) + "]麦子#消耗[" + jo.optInt("giveFoodNum") + "g饲料]");
                if (jo.optBoolean("isReachLimit")) {
                    Log.record("今日给[" + UserIdMap.getMaskName(userId) + "]送麦子已达上限");
                    Status.flagToday("farm::visitFriendLimit::" + userId);
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "visitFriend err:", t);
        }
    }

    private void acceptGift() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.acceptGift());
            if (MessageUtil.checkMemo(TAG, jo)) {
                int receiveFoodNum = jo.optInt("receiveFoodNum");
                Log.farm("收取麦子🌾[" + receiveFoodNum + "g]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "acceptGift err:", t);
        }
    }

    private void queryChickenDiary(String queryDayStr) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryChickenDiary(queryDayStr));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return;
            }
            JSONObject chickenDiary = data.optJSONObject("chickenDiary");
            if (chickenDiary == null) {
                return;
            }
            String diaryDateStr = chickenDiary.optString("diaryDateStr");
            if (data.has("hasTietie")) {
                if (!data.optBoolean("hasTietie", true)) {
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.diaryTietie(diaryDateStr, "NEW"));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        String prizeType = jo.optString("prizeType");
                        int prizeNum = jo.optInt("prizeNum", 0);
                        Log.farm("贴贴小鸡💞奖励[" + prizeType + "*" + prizeNum + "]");
                    }
                    if (!chickenDiary.has("statisticsList")) {
                        return;
                    }
                    JSONArray statisticsList = chickenDiary.optJSONArray("statisticsList");
                    if (statisticsList != null && statisticsList.length() > 0) {
                        for (int i = 0; i < statisticsList.length(); i++) {
                            JSONObject tietieStatus = statisticsList.optJSONObject(i);
                            if (tietieStatus == null) {
                                continue;
                            }
                            String tietieRoleId = tietieStatus.optString("tietieRoleId");
                            jo = MyUtils.newJSONObject(AntFarmRpcCall.diaryTietie(diaryDateStr, tietieRoleId));
                            if (MessageUtil.checkMemo(TAG, jo)) {
                                String prizeType = jo.optString("prizeType");
                                int prizeNum = jo.optInt("prizeNum", 0);
                                Log.farm("贴贴小鸡💞奖励[" + prizeType + "*" + prizeNum + "]");
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryChickenDiary err:", t);
        }
    }

    private void queryChickenDiaryList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryChickenDiaryList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data0 = jo.optJSONObject("data");
            JSONArray chickenDiaryBriefList = data0 != null ? data0.optJSONArray("chickenDiaryBriefList") : null;
            if (chickenDiaryBriefList != null && chickenDiaryBriefList.length() > 0) {
                for (int i = 0; i < chickenDiaryBriefList.length(); i++) {
                    jo = chickenDiaryBriefList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    if (!jo.optBoolean("read", true)) {
                        String dateStr = jo.optString("dateStr");
                        queryChickenDiary(dateStr);
                        TimeUtil.sleep(300);
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryChickenDiaryList err:", t);
        }
    }

    private void visitAnimal() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.visitAnimal());
            if (!MessageUtil.checkMemo(TAG, jo) || !jo.has("talkConfigs")) {
                return;
            }

            JSONArray talkNodes = jo.optJSONArray("talkNodes");
            JSONArray talkConfigs = jo.optJSONArray("talkConfigs");
            if (talkNodes == null || talkConfigs == null || talkConfigs.length() == 0) {
                return;
            }
            JSONObject data = talkConfigs.optJSONObject(0);
            if (data == null) {
                return;
            }
            String farmId = data.optString("farmId");
            jo = MyUtils.newJSONObject(AntFarmRpcCall.feedFriendAnimalVisit(farmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray actionNodes = null;
            for (int i = 0; i < talkNodes.length(); i++) {
                jo = talkNodes.optJSONObject(i);
                if (jo != null && jo.has("actionNodes")) {
                    actionNodes = jo.optJSONArray("actionNodes");
                    break;
                }
            }
            if (actionNodes == null) {
                return;
            }
            for (int i = 0; i < actionNodes.length(); i++) {
                jo = actionNodes.optJSONObject(i);
                if (jo == null || !"FEED".equals(jo.optString("type"))) {
                    continue;
                }
                String consistencyKey = jo.optString("consistencyKey");
                jo = MyUtils.newJSONObject(AntFarmRpcCall.visitAnimalSendPrize(consistencyKey));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    String prizeName = jo.optString("prizeName");
                    String userMaskName = UserIdMap.getMaskName(AntFarmRpcCall.farmId2UserId(farmId));
                    Log.farm("小鸡到访💞投喂[" + userMaskName + "]#获得[" + prizeName + "]");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "visitAnimal err:", t);
        }
    }

    //乐园限定活动（庄园 → 乐园限定活动，bizType=ANTFARM / sceneCode=ANTFARM_COMMON）
    //奖励类型 gameCoin（乐园币），场景码 ANTFARM_LEYUAN_DAILY_TASK
    private void queryOptionalPlay() {
        try {
            // 每个任务本轮最多上报一次：同一次运行内响应"成功"不代表服务端真的落态
            // （实测「抢先试玩爆款新游」这类 COUNT_DOWN 广告任务恒回成功却一直是 TODO），
            // 若不记已试集合会在一轮内无限领奖+无限申报。跨轮幂等交给 Status 标记。
            Set<String> attempted = new HashSet<>();
            int round = 0;
            while (round++ < MAX_OPTIONAL_PLAY_ROUNDS) {
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryOptionalPlay());
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
                if (taskList == null) return;
                // 本轮是否还有新的动作（不含已试过的），没有就收工
                boolean progressed = false;

                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.optJSONObject(j);
                    if (task == null) continue;
                    String taskType = task.optString("taskType");
                    String taskStatus = task.optString("taskStatus");
                    String sceneCode = task.optString("sceneCode");
                    String groupId = task.optString("groupId");
                    int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                    int awardCount = task.optInt("awardCount");
                    int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                    int rightsTimesLimit = task.optInt("rightsTimesLimit");
                    int rightsTimes = task.optInt("rightsTimes");
                    JSONObject bizInfo = task.optJSONObject("bizInfo");
                    if (bizInfo == null) {
                        continue;
                    }
                    String title = bizInfo.optString("title");
                    // 同一 taskType 可能分多阶段（rightsTimes 递增），用序号区分，避免误判为已试
                    if (taskType.isEmpty() || sceneCode.isEmpty() || title.isEmpty()) continue;
                    String attemptKey = sceneCode + "/" + taskType + "#" + rightsTimes;

                    // 黑名单任务跳过
                    if (AntFarmDrawMachineTaskList.getValue().contains(title)) {
                        continue;
                    }
                    // 底线：交易/支付/充值类一律不申报、直接跳过，绝不伪造。
                    // 必须同时查 groupId：充值的 taskType 是 2026cc_1000lyb_A&h（无关键词），
                    // 真正的判据在 groupId=2026cc_cz6ylyb_fz（cz=充值）
                    if (TaskAlternative.isTransactionTask(taskType)
                            || TaskAlternative.isTransactionTask(groupId)
                            || TaskAlternative.isTransactionTask(sceneCode)) {
                        Log.farm("小鸡乐园⏭️交易/履约类[" + title + "]#不申报，未提交请求");
                        continue;
                    }

                    // 已完成待领：领奖。已领满（awardCountForReceive<=0）不再重复请求
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        if (awardCountForReceive > 0 && attempted.add(attemptKey + "@award")) {
                            progressed = true;
                            JSONObject joReceived = MyUtils.newJSONObject(AntFarmRpcCall.receiveTaskAwardantfarm(awardCountForReceive, sceneCode, taskType));
                            if (MessageUtil.checkSuccess(TAG, joReceived)) {
                                int incAwardCount = joReceived.optInt("incAwardCount");
                                JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                                String awardType = taskConfigResultVO == null ? "乐园币" : taskConfigResultVO.optString("awardType", "乐园币");
                                Log.farm("小鸡乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                            }
                        }
                        continue;
                    }

                    // 待完成 / 多阶段未满：上报完成
                    if (TaskStatus.TODO.name().equals(taskStatus) || rightsTimes < rightsTimesLimit) {
                        if (!attempted.add(attemptKey)) {
                            continue;
                        }
                        progressed = true;
                        String label = rightsTimesLimit > 1
                                ? title + "(" + (rightsTimes + 1) + "/" + rightsTimesLimit + ")" : title;
                        TaskAttemptPolicy.handle("farm::optionalplay::" + attemptKey, label, null,
                                () -> finishOptionalPlayTask(sceneCode, taskType, label), Log::farm,
                                new TaskAttemptPolicy.Site("AntFarmDrawMachineTaskList", "庄园乐园限定任务", taskType, sceneCode));
                    }
                }

                // 没有新的可做任务就结束，避免空转
                if (!progressed) {
                    break;
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    /**
     * 乐园限定任务完成上报：{@code com.alipay.antiep.finishTask}。
     * 该接口对"逛一逛/玩游戏"类外部场景常回 400000040（不支持 rpc 调用），
     * 此时用 taskType 作 bizKey 走一次 doFarmTask 伪申报兜底；两条路都失败才判定做不了。
     */
    private Outcome finishOptionalPlayTask(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.finishTask(taskType, sceneCode));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.farm("小鸡乐园🧾上报已受理[" + taskTitle + "]");
                TimeUtil.sleep(500);
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) return Outcome.RETRY;
            if (MessageUtil.isUnsupportedRpc(jo)) return Outcome.UNSUPPORTED;
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", StringUtil.stripCountSuffix(taskTitle), jo);
            Log.farm("小鸡乐园⚠️未完成[" + taskTitle + "]#taskType=" + taskType);
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "finishOptionalPlayTask err:", t);
            return Outcome.RETRY;
        }
    }

    /** 乐园限定任务单轮最大重拉次数（防服务端恒回 TODO 时死循环）。 */
    private static final int MAX_OPTIONAL_PLAY_ROUNDS = 2;

    //小鸡乐园兑奖
    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void gameCenterBuyMallItem() {
        try {
            // 清单当日拉一次即可：一次商城首页 + 每个 SPU 一次详情，且它同时是配置页兑奖选项的来源，
            // 不能完全不拉；拉不到 SKU 就不打标记，留给下一轮重试
            if (!Status.hasFlagToday("farm::mallSkuList") && getAllSkuInfo()) {
                Status.flagToday("farm::mallSkuList");
            }
            Map<String, Integer> buyList = gameCenterBuyMallItemList.getValue();
            for (Map.Entry<String, Integer> entry : buyList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                while (Status.canGameCenterBuyMallItemToday(skuId, count) && BuyMallItem(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
            queryOptionalPlay();
        } catch (Throwable t) {
            Log.err(TAG, "gameCenterBuyMallItem err:", t);
        }
    }

    // 获取乐币购买商店列表
    private JSONArray getGameCenterMallItemList(String bizType) {
        JSONArray mallItemSimpleList = null;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getMallHome(bizType));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                mallItemSimpleList = jo.optJSONArray("mallItemSimpleList");
            }
        } catch (Throwable th) {
            Log.err(TAG, "getGameCenterMallItemList err:", th);
        }
        return mallItemSimpleList;
    }

    /**
     * 获取乐园商店所有商品信息。
     *
     * @return 是否真的取到 SKU（调用方据此决定要不要打当日标记，取不到时留给下一轮重试）
     */
    private boolean getAllSkuInfo() {
        try {
            JSONArray mallItemSimpleList = getGameCenterMallItemList("ANTFARM_GAME_CENTER");
            if (mallItemSimpleList == null) {
                return false;
            }
            boolean got = false;
            for (int i = 0; i < mallItemSimpleList.length(); i++) {
                JSONObject itemInfoVO = mallItemSimpleList.optJSONObject(i);
                if (itemInfoVO != null) {
                    got |= getSkuInfoByItemInfoVO(itemInfoVO);
                }
            }
            return got;
        } catch (Throwable th) {
            Log.err(TAG, "getAllSkuInfo err:", th);
            return false;
        }
    }

    private void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getMallItemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            if (!jo.has("spuItemInfoVo")) {
                return;
            }
            JSONObject spuItemInfoVo = jo.optJSONObject("spuItemInfoVO");
            getSkuInfoByItemInfoVO(spuItemInfoVo);
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoBySpuId err:", th);
        }
    }

    private boolean getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            if (!(spuItem.opt("spuId") instanceof String) || spuItem.optString("spuId").isEmpty()) return false;
            String spuId = spuItem.optString("spuId");
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getMallItemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return false;
            }
            JSONObject mallItemDetail = jo.optJSONObject("mallItemDetail");
            if (mallItemDetail == null || !mallItemDetail.has("mallSubItemDetailList")) {
                return false;
            }
            JSONArray mallSubItemDetailList = mallItemDetail.optJSONArray("mallSubItemDetailList");
            if (mallSubItemDetailList == null) {
                return false;
            }
            boolean got = false;
            for (int i = 0; i < mallSubItemDetailList.length(); i++) {
                JSONObject skuModel = mallSubItemDetailList.optJSONObject(i);
                if (skuModel == null) {
                    continue;
                }
                if (!(skuModel.opt("skuId") instanceof String)) continue;
                String skuId = skuModel.optString("skuId");
                if (skuId.isEmpty()) continue;
                got = true;
                String skuName = skuModel.optString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                GameCenterMallItemMap.add(skuId, skuName);
            }
            GameCenterMallItemMap.save(UserIdMap.getCurrentUid());
            return got;
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoByItemInfoVO err:", th);
        }
        return false;
    }

    private Boolean BuyMallItem(String skuId) {
        if (skuInfo.isEmpty()) {
            getAllSkuInfo();
        }
        JSONObject sku = skuInfo.get(skuId);
        if (sku == null) {
            Log.record("小鸡乐园🎐找不到要兑奖的权益！");
            return false;
        }
        try {
            String skuName = sku.optString("skuName");
            JSONArray itemStatusList = sku.optJSONArray("itemStatusList");
            if (itemStatusList == null) {
                return false;
            }
            for (int i = 0; i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.optString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.REACH_USER_HOLD_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus)) {
                    Log.record("乐币兑奖🎐[" + skuName + "]停止:" + AntFarm.ItemStatus.valueOf(itemStatus).nickName());
                    if (AntFarm.ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("farm::buyLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.optString("spuId");
            if (BuyMallItem(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (Throwable th) {
            Log.err(TAG, "BuyMallItem err:", th);
        }
        return false;
    }

    public static Boolean BuyMallItem(String spuId, String skuId, String skuName) {
        try {
            if (BuyMallItem(spuId, skuId)) {
                Status.gameCenterBuyMallItemToday(skuId);
                int buyedCount = Status.getGameCenterBuyMallItemCountToday(skuId);
                Log.farm("乐币兑奖🎐[" + skuName + "]#第" + buyedCount + "次");
                return true;
            } else {
                // 失败不累加当日次数：该计数用于控制当日兑换额度，成功才算一次，
                // 否则一次可重试的失败会吃掉额度导致当天不再重试（与 AntForestV2.exchangeBenefit 保持一致）
                return false;
            }
        } catch (Throwable th) {
            Log.err(TAG, "BuyMallItem err:", th);
        }
        return false;
    }

    private static Boolean BuyMallItem(String spuId, String skuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.buyMallItem(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                //如果出错今天停止兑换
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    Status.flagToday("farm::buyLimit::" + skuId);
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "BuyMallItem err:", th);
        }
        return false;
    }

    // 抽抽乐任务统计：跨场景累加，整个抽抽乐流程跑完只打一行
    // （原先在 doFarmDrawTask 里打，日场/IP 场各一条，一轮下来同一条统计重复出现）
    private int drawStatScenes;
    private int drawStatTotal;
    private int drawStatReceived;
    private int drawStatFinished;
    private int drawStatTodo;
    private int drawStatDone;
    private int drawStatSkipped;

    private void drawMachineGroups() {
        drawStatScenes = 0;
        drawStatTotal = 0;
        drawStatReceived = 0;
        drawStatFinished = 0;
        drawStatTodo = 0;
        drawStatDone = 0;
        drawStatSkipped = 0;
        try {
            drawMachineGroupsInner();
        } finally {
            if (drawStatScenes > 0) {
                Log.farm("抽抽乐📊任务统计[" + drawStatScenes + "场共" + drawStatTotal + "个]#已领=" + drawStatReceived
                        + "完成待领=" + drawStatFinished + "待做=" + drawStatTodo
                        + "已做=" + drawStatDone + "跳过=" + drawStatSkipped);
            }
        }
    }

    private void drawMachineGroupsInner() {
        try {
            if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue()) return;
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (MessageUtil.checkMemo(TAG, jo)) {
                //乐园限定活动（庄园 → 乐园限定活动，乐园币任务）：先做任务再抽奖
                queryOptionalPlay();

                drawMachine("ANTFARM_DAILY_DRAW_TASK", "dailyDrawMachine", "ipDrawMachine");

                JSONObject queryDrawMachineActivityjo = MyUtils.newJSONObject(AntFarmRpcCall.queryDrawMachineActivity("ipDrawMachine", "dailyDrawMachine"));
                if (MessageUtil.checkMemo(TAG, queryDrawMachineActivityjo)) {
                    JSONArray otherDrawMachineActivityIds = queryDrawMachineActivityjo.optJSONArray("otherDrawMachineActivityIds");
                    if (otherDrawMachineActivityIds == null) {
                        return;
                    }
                    if (otherDrawMachineActivityIds.length() > 0) {
                        drawMachine("ANTFARM_IP_DRAW_TASK", "ipDrawMachine", "dailyDrawMachine");
                        //自动抽奖
                        if (IPexchangeBenefit.getValue()) {
                            try {
                                jo = MyUtils.newJSONObject(AntFarmRpcCall.queryDrawMachineActivity("dailyDrawMachine", "ipDrawMachine"));
                                if (!MessageUtil.checkResultCode(TAG, jo)) {
                                    return;
                                }
                                JSONObject activity = jo.optJSONObject("drawMachineActivity");
                                if (activity == null) {
                                    return;
                                }
                                String activityId = activity.optString("activityId");
                                if (!activityId.isEmpty()) {
                                    //IPexchangeBenefit选择某种类型商品兑换
                                    //返回false表示有兑换的或碎片不足，返回true表示全部兑换完毕
                                    if (IPexchangeBenefit(activityId, "DRESS")) {
                                        if (IPexchangeBenefit(activityId, "REISSUE_CARD")) {
                                            if (IPexchangeBenefit(activityId, "DELICIOUS_FOOD")) {
                                                IPexchangeBenefit(activityId, "ANTFARM_IP_DRAW_MALL");
                                            }
                                        }
                                    }
                                }
                            } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
                                Log.err(TAG, "drawMachine err:", t);
                            }

                        }
                    }
                }
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.i(TAG, "queryLoveCabin err:");
            Log.printStackTrace(t);
        }
    }

    private void drawMachine(String taskSceneCode, String scene, String otherScenes) {
        doFarmDrawTask(taskSceneCode);
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryDrawMachineActivity(otherScenes, scene));
            int drawTimes = jo.optInt("drawTimes", 0);
            for (int i = 0; i < drawTimes; i++) {
                if (!drawMachine(scene)) {
                    return;
                }
                TimeUtil.sleep(5000);
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "drawMachine err:", t);
        }
    }

    private void doFarmDrawTask(String taskSceneCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmDrawTask(taskSceneCode));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray farmTaskList = jo.optJSONArray("farmTaskList");
            if (farmTaskList == null) {
                return;
            }
            int total = farmTaskList.length();
            int received = 0, finished = 0, todo = 0, todoDone = 0, todoSkipped = 0;
            for (int i = 0; i < farmTaskList.length(); i++) {
                if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue()) return;
                jo = farmTaskList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String taskStatus = jo.optString("taskStatus");
                String title = jo.optString("title");
                String taskId = jo.optString("taskId");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    received++;
                    continue;
                }
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    finished++;
                    String awardType = jo.optString("awardType");
                    receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
                    continue;
                }
                //黑名单任务跳过
                if (AntFarmDrawMachineTaskList.getValue().contains(title)) {
                    todoSkipped++;
                    Log.i("抽抽乐⏭️跳过[" + title + "]#黑名单");
                    continue;
                }

                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    todo++;
                    int rightsTimesLimit = jo.optInt("rightsTimesLimit");
                    int rightsTimes = jo.optInt("rightsTimes");
                    int remain = rightsTimesLimit - rightsTimes;
                    boolean matched = false;

                    if (taskId.contains("EXCHANGE") || taskId.contains("FKDWChuodong") || taskId.contains("GYG2") || taskId.equals("jiatingdongrirongrongwu")) {
                        for (int j = 0; j < remain; j++) {
                            if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue()) return;
                            JSONObject jodoFarmTask = MyUtils.newJSONObject(AntFarmRpcCall.doFarmTask(jo.optString("bizKey"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jodoFarmTask);
                        }
                        TimeUtil.sleep(1000);
                        matched = true;
                    }
                    // 兜底：其余任务（商业化/玩游戏/开宝箱/浏览等）统一走完成接口。
                    // 不再按 title/desc 关键字分派：服务端文案一改就会整类任务不执行（列表拿到了却没有任何动作），
                    // 这里改为全部尝试，确实做不了的交给自动拉黑机制剔除
                    if (!matched) {
                        String bizKey = jo.optString("bizKey");
                        // 交易/履约类：finishTask 与 doFarmTask 都会被服务端判风险，直接不申报
                        if (TaskAlternative.isTransactionTask(bizKey)) {
                            todoSkipped++;
                            String skipFlag = "transactionSkip::farmDraw::" + bizKey;
                            if (!Status.hasFlagToday(skipFlag)) {
                                Status.flagToday(skipFlag);
                                Log.farm("抽抽乐⏭️交易/履约类[" + title + "]#不申报，不修改黑名单");
                            }
                            continue;
                        }
                        // 服务端偶发返回 limit==times 的 TODO 任务，此时仍尝试一次，避免有任务却整轮不执行
                        int tryTimes = remain > 0 ? remain : 1;
                        String via = null;
                        for (int j = 0; j < tryTimes; j++) {
                            JSONObject jofinishTask = MyUtils.newJSONObject(AntFarmRpcCall.finishTask(taskId, taskSceneCode));
                            if (MessageUtil.checkSuccess(TAG, jofinishTask)) {
                                via = "finishTask";
                                continue;
                            }
                            // 游戏类任务的 taskType 服务端拒绝走 finishTask（400000040 不支持rpc调用），
                            // 再用任务的 bizKey 走一次 doFarmTask，两条路都失败才判定该任务做不了
                            JSONObject jodoFarmTask = bizKey.isEmpty()
                                    ? null
                                    : MyUtils.newJSONObject(AntFarmRpcCall.doFarmTask(bizKey, taskSceneCode));
                            if (jodoFarmTask != null && MessageUtil.checkSuccess(TAG, jodoFarmTask)) {
                                via = "doFarmTask";
                                continue;
                            }
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jofinishTask);
                        }
                        TimeUtil.sleep(2000);
                        if (via != null) {
                            todoDone++;
                            Log.farm("抽抽乐🧾完成[" + title + "]#" + via);
                        } else {
                            todoSkipped++;
                            Log.farm("抽抽乐⚠️未完成[" + title + "]#taskId=" + taskId + "#remain=" + remain + "，需在支付宝内手动完成");
                        }
                    } else {
                        todoDone++;
                    }
                    TimeUtil.sleep(1000);
                }
                TimeUtil.sleep(2000);
                String awardType = jo.optString("awardType");
                receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
            }
            // 统计累加到 drawStat*，由 drawMachineGroups 在抽抽乐全部场景跑完后统一打一行
            drawStatScenes++;
            drawStatTotal += total;
            drawStatReceived += received;
            drawStatFinished += finished;
            drawStatTodo += todo;
            drawStatDone += todoDone;
            drawStatSkipped += todoSkipped;
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "doFarmDrawActivityTimeTask err:", t);
        }
    }

    private void receiveFarmDrawTaskAward(String taskId, String title, String awardType, String taskSceneCode) {
        try {
            if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue()) return;
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveFarmDrawTimesTaskAward(taskId, awardType, taskSceneCode));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("装扮抽奖🎖️领取[" + title + "]奖励");
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "receiveFarmDrawTimesTaskAward err:", t);
        }
    }

    private Boolean drawMachine(String scene) {
        try {
            if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue()) return false;
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.drawMachine(scene));
            if (MessageUtil.checkMemo(TAG, jo)) {
                if (!jo.has("title")) {
                    jo = jo.optJSONObject("drawMachinePrize");
                }
                String title = jo.optString("title");
                Log.farm("装扮抽奖🎁抽中[" + title + "]");
                return true;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.err(TAG, "drawMachine err:", t);
        }
        return false;
    }

    //返回false表示有兑换的或碎片不足，返回true表示全部兑换完毕
    public boolean IPexchangeBenefit(String activityId, String labelType) {
        try {
            String response = AntFarmRpcCall.getItemList(activityId, 10, 0);
            JSONObject respJson = MyUtils.newJSONObject(response);

            if (respJson.optBoolean("success", false) || "100000000".equals(respJson.optString("code"))) {
                int totalCent = 0;
                JSONObject mallAccount = respJson.optJSONObject("mallAccountInfoVO");
                if (mallAccount != null) {
                    JSONObject holdingCount = mallAccount.optJSONObject("holdingCount");
                    if (holdingCount != null) {
                        totalCent = holdingCount.optInt("cent", 0);
                    }
                }
                //Log.record("当前持有总碎片:" + (totalCent / 100));
                JSONArray itemVOList = respJson.optJSONArray("itemInfoVOList");
                if (itemVOList == null) {
                    return true;
                }

                List<JSONObject> allSkus = new ArrayList<>();
                for (int i = 0; i < itemVOList.length(); i++) {
                    JSONObject item = itemVOList.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    JSONArray labelTypeList = item.optJSONArray("labelTypeList");
                    if (labelTypeList == null) {
                        continue;
                    }
                    boolean isRightItem = false;
                    for (int j = 0; j < labelTypeList.length(); j++) {
                        String itemLabelType = labelTypeList.optString(j);
                        if (itemLabelType.contains(labelType)) {
                            isRightItem = true;
                        }
                    }
                    if (!isRightItem) {
                        continue;
                    }
                    boolean itemReachedLimit = isReachedLimit(item);
                    JSONObject minPriceObj = item.optJSONObject("minPrice");
                    int cent = minPriceObj != null ? minPriceObj.optInt("cent", 0) : 0;

                    JSONArray skuList = item.optJSONArray("skuModelList");
                    if (skuList == null) {
                        continue;
                    }
                    for (int j = 0; j < skuList.length(); j++) {
                        JSONObject sku = skuList.optJSONObject(j);
                        if (sku == null) {
                            continue;
                        }
                        sku.put("_spuId", item.optString("spuId"));
                        sku.put("_spuName", item.optString("spuName"));
                        sku.put("_isReachLimit", itemReachedLimit || isReachedLimit(sku));
                        sku.put("_cent", cent);
                        allSkus.add(sku);
                    }
                }
                // 按价格从高到低排序
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    allSkus.sort((JSONObject a, JSONObject b) -> Integer.compare(b.optInt("_cent", 0), a.optInt("_cent", 0)));
                } else {
                    // 低版本用 Collections.sort 兼容
                    Collections.sort(allSkus, new Comparator<JSONObject>() {
                        @Override
                        public int compare(JSONObject a, JSONObject b) {
                            return Integer.compare(b.optInt("_cent", 0), a.optInt("_cent", 0));
                        }
                    });
                }

                for (JSONObject sku : allSkus) {
                    if (sku.optBoolean("_isReachLimit")) {
                        continue;
                    }
                    int cent = sku.optInt("_cent", 0);
                    String skuName = sku.optString("skuName");

                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.farm("兑换[" + labelType + "]类最高价值[" + skuName + "]碎片不足(持有" + (totalCent / 100) + "需" + (cent / 100) + ")");
                        return false;
                    }
                    break;
                }

                // 执行顺序兑换，按价格从高到低
                for (JSONObject sku : allSkus) {
                    if (sku.optBoolean("_isReachLimit")) {
                        continue;
                    }

                    String skuName = sku.optString("skuName");
                    int cent = sku.optInt("_cent", 0);
                    String extendInfo = sku.optString("skuExtendInfo");
                    int limitCount = extendInfo.contains("20次") ? 20 : (extendInfo.contains("5次") ? 5 : 1);

                    // 【核心逻辑】：如果当前项买不起，直接 return 停止，不再尝试后续更便宜的项目
                    if (isNoEnoughPoint(sku) || (cent > 0 && totalCent < cent)) {
                        Log.farm("剩余碎片不足以兑换[" + labelType + "]类优先级项 [" + skuName + "] (需 " + (cent / 100) + ")，停止后续兑换任务");
                        return false;
                    }

                    int sessionExchangedCount = 0;
                    while (sessionExchangedCount < limitCount) {
                        if (!manualFarmOwner(ownerUserId) || !drawMachine.getValue() || !IPexchangeBenefit.getValue()) return false;
                        // 预检查当前余额
                        if (cent > 0 && totalCent < cent) {
                            break;
                        }

                        String result = AntFarmRpcCall.exchangeBenefit(sku.optString("_spuId"), sku.optString("skuId"), activityId, "ANTFARM_IP_DRAW_MALL", "antfarm_villa");

                        JSONObject resObj = MyUtils.newJSONObject(result);
                        String resultCode = resObj.optString("resultCode");

                        if ("SUCCESS".equals(resultCode)) {
                            sessionExchangedCount++;
                            totalCent -= cent; // 减去花费
                            Log.farm("兑换装扮👔[" + labelType + "]类[" + skuName + "]#剩余碎片" + (totalCent / 100));
                            TimeUtil.sleep(800);
                        } else if ("NO_ENOUGH_POINT".equals(resultCode)) {
                            return false;
                        } else if (resultCode.contains("LIMIT") || resultCode.contains("MAX")) {
                            break;
                        } else {
                            break;
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) {
            Log.printStackTrace("自动兑换异常", e);
        }
        return false;
    }

    private boolean isReachedLimit(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("REACH_LIMIT".equals(jo.optString("itemStatus"))) {
            return true;
        }
        JSONArray list = jo.optJSONArray("itemStatusList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                String status = list.optString(i);
                if ("REACH_LIMIT".equals(status) || status.contains("LIMIT")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isNoEnoughPoint(JSONObject jo) {
        if (jo == null) {
            return false;
        }
        if ("NO_ENOUGH_POINT".equals(jo.optString("itemStatus"))) {
            return true;
        }
        JSONArray list = jo.optJSONArray("itemStatusList");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                if ("NO_ENOUGH_POINT".equals(list.optString(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private JSONObject queryNpcFarm() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.queryNpcFarm(ownerFarmId));
        JSONObject farm = response.optJSONObject("subFarmVO");
        JSONArray list = farm == null ? null : farm.optJSONArray("animals");
        if (!farmFeatureOk(response) || farm == null || !ownerFarmId.equals(farm.optString("farmId")) || list == null) return null;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject animal = list.optJSONObject(i);
            if (animal == null || !(animal.opt("animalId") instanceof String) || animal.optString("animalId").isEmpty()
                    || !ids.add(animal.optString("animalId"))) return null;
            switch (animal.optString("subAnimalType")) {
                case "NORMAL": case "GUEST": case "PIRATE": break;
                case "WORK": case "NPC":
                    if (!ownerFarmId.equals(animal.optString("currentFarmId"))) return null;
                    break;
                default: return null;
            }
        }
        return npcOccupiedSlots(list) <= 2 ? farm : null;
    }

    private static double npcRewardValue(JSONObject animal) {
        Object raw = animal.opt("npcBizReward");
        if (!(raw instanceof Number) && !(raw instanceof String)) return Double.NaN;
        try {
            java.math.BigDecimal parsed = new java.math.BigDecimal(raw.toString());
            double value = parsed.doubleValue();
            return parsed.signum() == 0 ? 0 : Double.isFinite(value) && value > 0 ? value : Double.NaN;
        } catch (NumberFormatException e) { return Double.NaN; }
    }

    private static JSONObject findFarmNpc(JSONArray list, String id) {
        for (int i = 0; i < list.length(); i++) {
            JSONObject animal = list.optJSONObject(i);
            if (animal != null && "NPC".equals(animal.optString("subAnimalType")) && id.equals(animal.optString("animalId"))) return animal;
        }
        return null;
    }

    private static int npcOccupiedSlots(JSONArray list) {
        int count = 0;
        for (int i = 0; i < list.length(); i++) {
            JSONObject animal = list.optJSONObject(i);
            if (animal != null && ("NPC".equals(animal.optString("subAnimalType")) || "WORK".equals(animal.optString("subAnimalType")))) count++;
        }
        return count;
    }

    private void manageFarmNpc() {
        int type = farmNpcType.getValue();
        if (type <= 0 || type >= FARM_NPC_IDS.length) return;
        if (type == 3) { manageZhimaPigeon(); return; }
        if (!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty()) {
            Log.record("NPC小鸡：大表鸽奖励仍待核验，保留当前NPC，暂缓切换"); return;
        }
        try {
            TimeUtil.sleep(0);
            if (ownerFarmId == null || ownerFarmId.isEmpty()) { Log.record("NPC小鸡：庄园尚未就绪，跳过"); return; }
            String pending = "farm::npcActionUnconfirmed";
            // ponytail: 未确认动作当日停止自动操作，次日或人工核验后再处理。
            if (Status.hasFlagToday(pending)) { Log.record("NPC小鸡：本日操作结果未确认，停止重复雇佣/遣返"); return; }
            JSONObject farm = queryNpcFarm();
            if (farm == null) { Log.record("NPC小鸡：庄园/名额状态不明，跳过"); return; }
            JSONArray list = farm.optJSONArray("animals");
            String targetId = FARM_NPC_IDS[type], name = FARM_NPC_NAMES[type];
            JSONObject current = findFarmNpc(list, targetId);
            boolean full = current != null;
            if (current != null) {
                double reward = npcRewardValue(current);
                if (!Double.isFinite(reward) || !(current.opt("reachNpcBizRewardLimit") instanceof Boolean)) {
                    Log.record("NPC小鸡：产出状态不明，保留当前小鸡"); return;
                }
                if (!Boolean.TRUE.equals(current.opt("reachNpcBizRewardLimit"))) {
                    Log.record("NPC小鸡：[" + name + "]工作中，当前产出=" + reward); return;
                }
            } else {
                boolean hasNpc = false;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject animal = list.optJSONObject(i);
                    if (!"NPC".equals(animal.optString("subAnimalType"))) continue;
                    hasNpc = true;
                    String id = animal.optString("animalId");
                    if ((FARM_NPC_IDS[1].equals(id) || FARM_NPC_IDS[2].equals(id)) && npcRewardValue(animal) == 0
                            && Boolean.FALSE.equals(animal.opt("reachNpcBizRewardLimit"))) { current = animal; break; }
                }
                if (hasNpc && current == null) { Log.record("NPC小鸡：现有NPC有待领奖励或类型/状态不明，保留"); return; }
            }
            if (current != null) {
                if (!(current.opt("masterFarmId") instanceof String) || current.optString("masterFarmId").isEmpty()) {
                    Log.record("NPC小鸡：遣返所需庄园标识缺失，跳过"); return;
                }
                String currentId = current.optString("animalId");
                TimeUtil.sleep(0);
                Status.flagToday(pending);
                JSONObject sent = MyUtils.newJSONObject(AntFarmRpcCall.sendBackNpcAnimal(currentId, ownerFarmId, current.optString("masterFarmId")));
                farm = queryNpcFarm();
                if (!farmFeatureOk(sent) || farm == null || findFarmNpc(farm.optJSONArray("animals"), currentId) != null) {
                    Log.record("NPC小鸡：遣返/领奖未确认，保留当日保护标记，" + RpcRequestGuard.errorMessage(sent)); return;
                }
                Status.clearFlag(pending);
                Log.farm("NPC小鸡🤖[" + (full ? name + "满产领取并离场已确认" : "切换离场已确认") + "]");
                list = farm.optJSONArray("animals");
            }
            if (findFarmNpc(list, targetId) != null) return;
            // 名额在动作后可能变化，不遣返好友工人，也不覆盖刚到场的其他NPC。
            for (int i = 0; i < list.length(); i++) {
                if ("NPC".equals(list.optJSONObject(i).optString("subAnimalType"))) { Log.record("NPC小鸡：仍有其他NPC，等待后续回查"); return; }
            }
            if (npcOccupiedSlots(list) >= 2) { Log.record("NPC小鸡：雇佣名额已满，保留好友工人"); return; }
            TimeUtil.sleep(0);
            Status.flagToday(pending);
            JSONObject hired = MyUtils.newJSONObject(AntFarmRpcCall.hireNpcAnimal(targetId, FARM_NPC_SOURCES[type]));
            JSONObject after = queryNpcFarm();
            if (farmFeatureOk(hired) && after != null && findFarmNpc(after.optJSONArray("animals"), targetId) != null) {
                Status.clearFlag(pending);
                Log.farm("NPC小鸡🤖[成功雇佣" + name + "，已回查到场]");
            } else Log.record("NPC小鸡：雇佣到场未确认，保留当日保护标记，" + RpcRequestGuard.errorMessage(hired));
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "manageFarmNpc", t); }
    }

    private static long npcTaskNumber(JSONObject task, String key) {
        Object raw = task.opt(key);
        if (!(raw instanceof Number) && !(raw instanceof String)) return -1;
        try {
            long n = new java.math.BigDecimal(raw.toString()).longValueExact();
            return n >= 0 ? n : -1;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private JSONObject queryPigeonAlchemyTask() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.alchemyQueryTasks());
        JSONObject payload = response.optJSONObject("resData");
        if (payload == null) payload = response;
        JSONObject data = payload.optJSONObject("data");
        if ((!farmFeatureOk(response) && !farmFeatureOk(payload)) || RpcRequestGuard.isFailure(response) || data == null) return null;
        JSONObject daily = data.optJSONObject("dailyTaskListVO");
        JSONArray[] lists = {data.optJSONArray("toCompleteVOS"), daily == null ? null : daily.optJSONArray("waitJoinTaskVOS"), daily == null ? null : daily.optJSONArray("waitCompleteTaskVOS")};
        JSONObject found = null;
        for (JSONArray list : lists) {
            if (list == null) continue;
            for (int i = 0; i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null) return null;
                if (!PIGEON_TEMPLATE.equals(task.optString("templateId"))) continue;
                if (found != null && !found.toString().equals(task.toString())) return null;
                found = task;
            }
        }
        if (found == null || !(found.opt("finishFlag") instanceof Boolean) || found.optBoolean("finishFlag")
                || !"LIFE_RECORD".equals(found.optString("bizType")) || npcTaskNumber(found, "completedNum") < 0
                || npcTaskNumber(found, "needCompleteNum") <= npcTaskNumber(found, "completedNum")) return null;
        return found;
    }

    private boolean authorizePigeonHire() throws JSONException {
        JSONObject task = queryPigeonAlchemyTask();
        if (task == null) { Log.record("大表鸽：炼金列表没有明确的可雇佣任务，等待后续资格"); return false; }
        if (task.optString("recordId").isEmpty()) {
            JSONObject joined = MyUtils.newJSONObject(AntMemberRpcCall.joinPigeonAlchemyTask(PIGEON_TEMPLATE));
            if (!farmFeatureOk(joined)) return false;
            task = queryPigeonAlchemyTask();
            if (task == null || task.optString("recordId").isEmpty()) return false;
        }
        String record = task.optString("recordId");
        String confirmed = "farm::pigeonAuthorized::" + record, attempt = "farm::pigeonFeedbackAttempt::" + record;
        if (Status.hasFlagToday(confirmed)) return true;
        if (Status.hasFlagToday(attempt)) return false;
        TimeUtil.sleep(0); Status.flagToday(attempt);
        JSONObject feedback = MyUtils.newJSONObject(AntMemberRpcCall.feedbackPigeonAlchemyTask(PIGEON_TEMPLATE, task.optString("bizType")));
        if (!farmFeatureOk(feedback)) return false;
        Status.flagToday(confirmed);
        return true;
    }

    /** 通用收取前先绑定待收ID，防一键领取使奖励消失后无法判定闭环。 */
    public static boolean bindPigeonFeedback(JSONArray items) throws JSONException {
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        String saved = runtime.getString(PIGEON_RECEIPT_KEY);
        if (saved.isEmpty()) return true;
        JSONObject pending = MyUtils.newJSONObject(saved);
        if (!(pending.opt("feedbackId") instanceof String) || items == null) return false;
        if (!pending.optString("feedbackId").isEmpty()) return true;
        String id = "";
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || item.optString("status").isEmpty()) return false;
            if (!"UNCLAIMED".equals(item.optString("status")) || !PIGEON_CATEGORY.equals(item.optString("cateId"))) continue;
            if (!(item.opt("creditFeedbackId") instanceof String) || item.optString("creditFeedbackId").isEmpty() || !id.isEmpty()) return false;
            id = item.optString("creditFeedbackId");
        }
        if (id.isEmpty()) { Log.record("大表鸽：奖励反馈尚未生成，暂缓通用收取以保留核验依据"); return false; }
        pending.put("feedbackId", id);
        return runtime.putVerified(PIGEON_RECEIPT_KEY, pending.toString());
    }

    private JSONArray queryPigeonFeedback() {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.queryCreditFeedback());
        if (!farmFeatureOk(response)) return null;
        JSONArray items = response.optJSONArray("creditFeedbackVOS");
        if (items == null && response.optJSONObject("data") != null) items = response.optJSONObject("data").optJSONArray("creditFeedbackVOS");
        if (items == null && response.optJSONObject("resData") != null) items = response.optJSONObject("resData").optJSONArray("creditFeedbackVOS");
        // 原协议size=20；满页不能证明绑定反馈已消失，等待通用收取腾出窗口。
        if (items == null || items.length() >= 20 || response.optBoolean("hasNext")) return null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || !(item.opt("status") instanceof String) || item.optString("status").isEmpty()
                    || !(item.opt("creditFeedbackId") instanceof String) || item.optString("creditFeedbackId").isEmpty()) return null;
        }
        return items;
    }

    private boolean collectPigeonReceipt() throws JSONException {
        JSONArray items = queryPigeonFeedback();
        if (items == null || !bindPigeonFeedback(items)) return false;
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        JSONObject pending = MyUtils.newJSONObject(runtime.getString(PIGEON_RECEIPT_KEY));
        String id = pending.optString("feedbackId");
        if (id.isEmpty()) return false;
        boolean waiting = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (!id.equals(item.optString("creditFeedbackId"))) continue;
            if (!"UNCLAIMED".equals(item.optString("status"))) return false;
            waiting = true;
        }
        if (waiting) {
            String attempt = "farm::pigeonCreditAttempt::" + id;
            if (Status.hasFlagToday(attempt)) return false;
            TimeUtil.sleep(0); Status.flagToday(attempt);
            AntMemberRpcCall.collectCreditFeedback(id);
            items = queryPigeonFeedback();
            if (items == null) return false;
            for (int i = 0; i < items.length(); i++) if (id.equals(items.optJSONObject(i).optString("creditFeedbackId"))) return false;
        }
        if (!runtime.putVerified(PIGEON_RECEIPT_KEY, null)) return false;
        Status.clearFlag("farm::npcActionUnconfirmed");
        Log.farm("大表鸽：绑定芝麻粒反馈已回查消失，奖励收取确认");
        return true;
    }

    private JSONArray queryPigeonFarmTasks() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntFarmRpcCall.listPigeonFarmTasks());
        return farmFeatureOk(response) ? response.optJSONArray("farmTaskList") : null;
    }

    private void runPigeonFarmTasks() throws JSONException {
        JSONArray tasks = queryPigeonFarmTasks();
        int actions = 0;
        for (int i = 0; tasks != null && i < tasks.length() && actions < 20; i++) {
            TimeUtil.sleep(0);
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || task.optString("taskId").isEmpty()) continue;
            String id = task.optString("taskId"), status = task.optString("taskStatus");
            if ("TODO".equals(status) && "ZHIMA_NPC_VISIT_TASK".equals(id) && id.equals(task.optString("bizKey"))) {
                String attempt = "farm::pigeonVisit::" + id;
                if (Status.hasFlagToday(attempt)) continue;
                Status.flagToday(attempt); actions++;
                AntFarmRpcCall.doFarmTask(id, "ANTFARM_ZHIMA_NPC_TASK");
                JSONArray fresh = queryPigeonFarmTasks();
                task = null;
                for (int j = 0; fresh != null && j < fresh.length(); j++) {
                    JSONObject item = fresh.optJSONObject(j);
                    if (item != null && id.equals(item.optString("taskId"))) { task = item; break; }
                }
                if (task == null) return;
                status = task.optString("taskStatus");
            }
            if (!"FINISHED".equals(status) || !(task.opt("awardType") instanceof String) || task.optString("awardType").isEmpty()) continue;
            if (actions >= 20) return;
            String attempt = "farm::pigeonTaskAward::" + id;
            if (Status.hasFlagToday(attempt)) continue;
            Status.flagToday(attempt); actions++;
            JSONObject awarded = MyUtils.newJSONObject(AntFarmRpcCall.receivePigeonFarmAward(id, task.optString("awardType")));
            JSONArray fresh = queryPigeonFarmTasks();
            boolean received = false;
            for (int j = 0; fresh != null && j < fresh.length(); j++) {
                JSONObject item = fresh.optJSONObject(j);
                if (item != null && id.equals(item.optString("taskId")) && "RECEIVED".equals(item.optString("taskStatus"))) received = true;
            }
            if (!farmFeatureOk(awarded) || !received) { Log.record("大表鸽：庄园任务领奖未确认，停止本轮任务"); return; }
            Log.farm("大表鸽：庄园任务奖励已回查确认");
        }
    }

    private void manageZhimaPigeon() {
        try {
            TimeUtil.sleep(0);
            if (ownerFarmId == null || ownerFarmId.isEmpty()) return;
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            String pending = "farm::npcActionUnconfirmed";
            String receipt = runtime.getString(PIGEON_RECEIPT_KEY);
            if (!receipt.isEmpty() && !(MyUtils.newJSONObject(receipt).opt("feedbackId") instanceof String)) return;
            if (Status.hasFlagToday(pending) && runtime.getString(PIGEON_RECEIPT_KEY).isEmpty()) return;
            JSONObject farm = queryNpcFarm();
            if (farm == null) return;
            JSONArray list = farm.optJSONArray("animals");
            JSONObject pigeon = findFarmNpc(list, FARM_NPC_IDS[3]);
            if (!runtime.getString(PIGEON_RECEIPT_KEY).isEmpty() && pigeon == null) { collectPigeonReceipt(); return; }
            if (pigeon != null) {
                double reward = npcRewardValue(pigeon);
                if (!Double.isFinite(reward)) return;
                if (reward < 88 && !Boolean.TRUE.equals(pigeon.opt("reachNpcBizRewardLimit"))) {
                    runPigeonFarmTasks();
                    farm = queryNpcFarm();
                    pigeon = farm == null ? null : findFarmNpc(farm.optJSONArray("animals"), FARM_NPC_IDS[3]);
                    if (pigeon == null || !Double.isFinite(npcRewardValue(pigeon))) return;
                    reward = npcRewardValue(pigeon);
                }
                if (reward < 88 && !Boolean.TRUE.equals(pigeon.opt("reachNpcBizRewardLimit"))) { Log.record("大表鸽：工作中，等待满产"); return; }
                if (Status.hasFlagToday(pending) || !(pigeon.opt("masterFarmId") instanceof String) || pigeon.optString("masterFarmId").isEmpty()) return;
                if (runtime.getString(PIGEON_RECEIPT_KEY).isEmpty() && !runtime.putVerified(PIGEON_RECEIPT_KEY, "{\"feedbackId\":\"\"}")) return;
                TimeUtil.sleep(0); Status.flagToday(pending);
                JSONObject sent = MyUtils.newJSONObject(AntFarmRpcCall.sendBackNpcAnimal(FARM_NPC_IDS[3], ownerFarmId, pigeon.optString("masterFarmId"), FARM_NPC_SOURCES[3]));
                farm = queryNpcFarm();
                if (farmFeatureOk(sent) && farm != null && findFarmNpc(farm.optJSONArray("animals"), FARM_NPC_IDS[3]) == null) {
                    Status.clearFlag(pending);
                    Log.farm("大表鸽：满产遣返已确认，等待芝麻粒反馈收取");
                    collectPigeonReceipt();
                }
                return;
            }
            if (Status.hasFlagToday(pending)) return;
            JSONObject replace = null;
            for (int i = 0; i < list.length(); i++) {
                JSONObject other = list.optJSONObject(i);
                if (!"NPC".equals(other.optString("subAnimalType"))) continue;
                String id = other.optString("animalId");
                if (!(FARM_NPC_IDS[1].equals(id) || FARM_NPC_IDS[2].equals(id)) || npcRewardValue(other) != 0
                        || !Boolean.FALSE.equals(other.opt("reachNpcBizRewardLimit")) || !(other.opt("masterFarmId") instanceof String) || other.optString("masterFarmId").isEmpty()) return;
                if (replace != null) return;
                replace = other;
            }
            if (replace == null && npcOccupiedSlots(list) >= 2 || !authorizePigeonHire()) return;
            // 授权请求期间名额也可能变化；重新读取，保留任何新到场NPC/好友工人。
            farm = queryNpcFarm();
            if (farm == null) return;
            list = farm.optJSONArray("animals");
            if (findFarmNpc(list, FARM_NPC_IDS[3]) != null) return;
            if (replace != null) {
                JSONObject fresh = findFarmNpc(list, replace.optString("animalId"));
                if (fresh == null || npcRewardValue(fresh) != 0 || !Boolean.FALSE.equals(fresh.opt("reachNpcBizRewardLimit"))
                        || !(fresh.opt("masterFarmId") instanceof String) || fresh.optString("masterFarmId").isEmpty()) return;
                TimeUtil.sleep(0); Status.flagToday(pending);
                JSONObject sent = MyUtils.newJSONObject(AntFarmRpcCall.sendBackNpcAnimal(fresh.optString("animalId"), ownerFarmId, fresh.optString("masterFarmId"), FARM_NPC_SOURCES[3]));
                farm = queryNpcFarm();
                if (!farmFeatureOk(sent) || farm == null || findFarmNpc(farm.optJSONArray("animals"), fresh.optString("animalId")) != null) return;
                Status.clearFlag(pending); list = farm.optJSONArray("animals");
            }
            for (int i = 0; i < list.length(); i++) if ("NPC".equals(list.optJSONObject(i).optString("subAnimalType"))) return;
            if (npcOccupiedSlots(list) >= 2) return;
            TimeUtil.sleep(0); Status.flagToday(pending);
            JSONObject hired = MyUtils.newJSONObject(AntFarmRpcCall.hireNpcAnimal(FARM_NPC_IDS[3], FARM_NPC_SOURCES[3]));
            farm = queryNpcFarm();
            if (farmFeatureOk(hired) && farm != null && findFarmNpc(farm.optJSONArray("animals"), FARM_NPC_IDS[3]) != null) {
                Status.clearFlag(pending); Log.farm("大表鸽：炼金授权后雇佣已回查到场");
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "manageZhimaPigeon", t); }
    }

    /* 雇佣好友小鸡 */
    private void hireAnimal() {
        try {
            syncAnimalStatus(ownerFarmId);
            if (!AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
                return;
            }
            int count = 3 - animals.length;
            if (count <= 0) {
                return;
            }
            Log.farm("雇佣小鸡👷[当前可雇佣小鸡数量:" + count + "只]");
            if (foodStock < 50) {
                Log.record("饲料不足，暂不雇佣");
                return;
            }

            boolean hasNext;
            int pageStartSum = 0;
            Set<String> hireAnimalSet = hireAnimalList.getValue();
            do {
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.rankingList(pageStartSum));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    return;
                }
                JSONArray rankingList = jo.optJSONArray("rankingList");
                if (rankingList == null) {
                    return;
                }
                hasNext = jo.optBoolean("hasNext");
                pageStartSum += rankingList.length();
                for (int i = 0; i < rankingList.length() && count > 0; i++) {
                    jo = rankingList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    String userId = jo.optString("userId");
                    boolean isHireAnimal = hireAnimalSet.contains(userId);
                    if (hireAnimalType.getValue() != HireAnimalType.HIRE) {
                        isHireAnimal = !isHireAnimal;
                    }
                    if (!isHireAnimal || userId.equals(UserIdMap.getCurrentUid())) {
                        continue;
                    }
                    JSONArray actionTypeList = jo.optJSONArray("actionTypeList");
                    String actionTypeListStr = actionTypeList != null ? actionTypeList.toString() : "";
                    if (actionTypeListStr.contains("can_hire_action")) {
                        if (hireAnimalAction(userId)) {
                            count--;
                            autoFeedAnimal();
                        }
                    }
                }
            } while (hasNext && count > 0);

            if (count > 0) {
                Log.farm("没有足够的小鸡可以雇佣");
            }
        } catch (Throwable t) {
            Log.err(TAG, "hireAnimal err:", t);
        } finally {
            long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
            String taskId = "UPDATE|HIRE|" + ownerFarmId;
            addChildTask(new ChildModelTask(taskId, "UPDATE", this::autoHireAnimal, updateTime));
        }
    }

    private void autoHireAnimal() {
        try {
            syncAnimalStatus(ownerFarmId);
            for (Animal animal : animals) {
                if (!SubAnimalType.WORK.name().equals(animal.subAnimalType)) {
                    continue;
                }
                String taskId = "HIRE|" + animal.animalId;
                if (!hasChildTask(taskId)) {
                    long beHiredEndTime = animal.beHiredEndTime;
                    addChildTask(new ChildModelTask(taskId, "HIRE", this::hireAnimal, beHiredEndTime));
                    Log.record("添加蹲点雇佣👷在[" + TimeUtil.getCommonDate(beHiredEndTime) + "]执行");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "autoHireAnimal err:", t);
        }
    }

    private Boolean hireAnimalAction(String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFarm("", userId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject farmVO = jo.optJSONObject("farmVO");
            jo = farmVO != null ? farmVO.optJSONObject("subFarmVO") : null;
            if (jo == null) {
                return false;
            }
            String farmId = jo.optString("farmId");
            JSONArray animals = jo.optJSONArray("animals");
            if (animals == null) {
                return false;
            }
            for (int i = 0, len = animals.length(); i < len; i++) {
                JSONObject animal = animals.optJSONObject(i);
                if (animal == null) {
                    continue;
                }
                JSONObject masterUserInfoVO = animal.optJSONObject("masterUserInfoVO");
                if (masterUserInfoVO != null && Objects.equals(masterUserInfoVO.optString("userId"), userId)) {
                    String animalId = animal.optString("animalId");
                    jo = MyUtils.newJSONObject(AntFarmRpcCall.hireAnimal(farmId, animalId));
                    if (MessageUtil.checkMemo(TAG, jo)) {
                        foodStock = jo.optInt("foodStock");
                        int reduceFoodNum = jo.optInt("reduceFoodNum");
                        Log.farm("雇佣小鸡👷雇佣[" + UserIdMap.getMaskName(userId) + "]#消耗[" + reduceFoodNum + "g饲料]");
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "hireAnimalAction err:", t);
        }
        return false;
    }

    private void drawGameCenterAward() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryGameList());
            if (jo.optBoolean("success")) {
                // 2. 获取宝箱领取权限数据
                JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
                if (drawRights != null) {
                    // 3. 处理当前可开启的宝箱
                    int quotaCanUse = drawRights.optInt("quotaCanUse"); // 当前可开宝箱数
                    if (quotaCanUse > 0) {
                        Log.record("当前有 " + quotaCanUse + " 个宝箱待开启...");

                        while (quotaCanUse > 0) {
                            // 调用开启宝箱接口
                            String drawResStr = AntFarmRpcCall.drawGameCenterAward(1);
                            JSONObject drawRes = MyUtils.newJSONObject(drawResStr);

                            if (drawRes.optBoolean("success")) {
                                // 更新剩余可开启次数
                                JSONObject nextRights = drawRes.optJSONObject("gameCenterDrawRights");
                                quotaCanUse = (nextRights != null) ? nextRights.optInt("quotaCanUse") : (quotaCanUse - 1);

                                // 解析奖励列表并拼接日志
                                JSONArray awardList = drawRes.optJSONArray("gameCenterDrawAwardList");
                                List<String> awardStrings = new ArrayList<>();
                                if (awardList != null) {
                                    for (int i = 0; i < awardList.length(); i++) {
                                        JSONObject item = awardList.optJSONObject(i);
                                        if (item == null) {
                                            continue;
                                        }
                                        String awardName = item.optString("awardName");
                                        int awardCount = item.optInt("awardCount");
                                        awardStrings.add(awardName + "*" + awardCount);
                                    }
                                }
                                String awardLog = String.join(",", awardStrings);
                                Log.farm("小鸡乐园🎁开宝箱得[" + awardLog + "]");
                                TimeUtil.sleep(3000);
                            } else {
                                Log.farm("小鸡乐园开启宝箱失败: " + drawRes.optString("desc"));
                                break; // 开启失败则退出循环
                            }
                        }
                    }

                    // 4. 处理剩余任务（判断是否需要刷任务）
                    int limit = drawRights.optInt("quotaLimit"); // 每日上限
                    int used = drawRights.optInt("usedQuota");   // 今日已开数量
                    int remainToTask = limit - used;
                    // 已开数量 < 上限 且 无可用次数 → 触发任务刷取
                    if (remainToTask > 0 && quotaCanUse == 0) {
                        if (!Status.hasFlagToday(FLAG_GAME_DRAW_TASK_SKIP)) {
                            // 本地 quotaCanUse/used 已被本次开箱流程改过，不能当基线，先回读一次真实值
                            JSONObject beforeJo = MyUtils.newJSONObject(AntFarmRpcCall.queryGameList());
                            JSONObject beforeRights = beforeJo.optJSONObject("gameCenterDrawRights");
                            int beforeUsed = beforeRights != null ? beforeRights.optInt("usedQuota", used) : used;
                            int beforeQuota = beforeRights != null ? beforeRights.optInt("quotaCanUse", quotaCanUse) : quotaCanUse;
                            // report 是异步线程，上报完立刻回读会读到旧状态，这里用同步版
                            int successes = GameTask.Farm_ddply.reportSync("庄园", remainToTask);
                            if (successes > 0) {
                                JSONObject afterJo = MyUtils.newJSONObject(AntFarmRpcCall.queryGameList());
                                JSONObject afterRights = afterJo.optJSONObject("gameCenterDrawRights");
                                int afterQuota = afterRights != null ? afterRights.optInt("quotaCanUse", beforeQuota) : beforeQuota;
                                int afterUsed = afterRights != null ? afterRights.optInt("usedQuota", beforeUsed) : beforeUsed;
                                if (afterQuota > beforeQuota || afterUsed > beforeUsed) {
                                    Log.record("小鸡乐园🎁刷任务生效#可用次数[" + beforeQuota + "→" + afterQuota + "]");
                                } else {
                                    Status.flagToday(FLAG_GAME_DRAW_TASK_SKIP);
                                    Log.record("小鸡乐园🎁刷任务未推进#今日不再刷任务");
                                }
                            }
                        }
                    } else if (remainToTask <= 0) {
                        Log.record("今日 " + limit + " 个金蛋任务已全部满额");
                    }
                }

                // 异步任务完成
                TimeUtil.sleep(3000);
                
                
                /*
                JSONObject gameDrawAwardActivity = jo.optJSONObject("gameDrawAwardActivity");
                int canUseTimes = gameDrawAwardActivity.optInt("canUseTimes");
                while (canUseTimes > 0) {
                    try {
                        jo = MyUtils.newJSONObject(AntFarmRpcCall.drawGameCenterAward());
                        if (jo.optBoolean("success")) {
                            canUseTimes = jo.optInt("drawRightsTimes");
                            JSONArray gameCenterDrawAwardList = jo.optJSONArray("gameCenterDrawAwardList");
                            ArrayList<String> awards = new ArrayList<String>();
                            for (int i = 0; i < gameCenterDrawAwardList.length(); i++) {
                                JSONObject gameCenterDrawAward = gameCenterDrawAwardList.optJSONObject(i);
                                int awardCount = gameCenterDrawAward.optInt("awardCount");
                                String awardName = gameCenterDrawAward.optString("awardName");
                                awards.add(awardName + "*" + awardCount);
                            }
                            Log.farm("小鸡乐园🎮开宝箱得[" + StringUtil.collectionJoinString(",", awards) + "]");
                        }
                        else {
                            Log.i(TAG, "drawGameCenterAward falsed result: " + jo.toString());
                        }
                    }
                    catch (Throwable t) {
                        Log.printStackTrace(TAG, t);
                    }
                    finally {
                        TimeUtil.sleep(3000);
                    }
                }*/
            } else {
                Log.i(TAG, "queryGameList falsed result: " + jo.toString());
            }

        } catch (Throwable t) {
            Log.err(TAG, "drawGameCenterAward err:", t);
        }
    }

    // 装扮焕新
    private void ornamentsDressUp() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listOrnaments());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            List<JSONObject> list = new ArrayList<>();
            JSONArray achievementOrnaments = jo.optJSONArray("achievementOrnaments");
            if (achievementOrnaments == null) {
                return;
            }
            long takeOffTime = System.currentTimeMillis();
            for (int i = 0; i < achievementOrnaments.length(); i++) {
                jo = achievementOrnaments.optJSONObject(i);
                if (jo == null || !jo.optBoolean("acquired")) {
                    continue;
                }
                if (jo.has("takeOffTime")) {
                    takeOffTime = jo.optLong("takeOffTime");
                }
                String resourceKey = jo.optString("resourceKey");
                String name = jo.optString("name");
                if (ornamentsDressUpList.getValue().contains(resourceKey)) {
                    list.add(jo);
                }
                FarmOrnamentsIdMap.add(resourceKey, name);
            }
            FarmOrnamentsIdMap.save(UserIdMap.getCurrentUid());
            if (list.isEmpty() || takeOffTime + TimeUnit.DAYS.toMillis(ornamentsDressUpDays.getValue() - 15) > System.currentTimeMillis()) {
                return;
            }

            jo = list.get(RandomUtil.nextInt(0, list.size() - 1));
            if (saveOrnaments(jo)) {
                Log.farm("装扮焕新✨[" + jo.optString("name") + "]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "ornamentsDressUp err:", t);
        }
    }

    private Boolean saveOrnaments(JSONObject ornaments) {
        try {
            String animalId = ownerAnimal.animalId;
            String farmId = ownerFarmId;
            JSONArray sets = ornaments.optJSONArray("sets");
            String ornamentsSets = sets != null ? getOrnamentsSets(sets) : "";
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.saveOrnaments(animalId, farmId, ornamentsSets));
            return MessageUtil.checkMemo(TAG, jo);
        } catch (Throwable t) {
            Log.err(TAG, "saveOrnaments err:", t);
        }
        return false;
    }

    private String getOrnamentsSets(JSONArray sets) {
        StringBuilder ornamentsSets = new StringBuilder();
        try {
            for (int i = 0; i < sets.length(); i++) {
                JSONObject set = sets.optJSONObject(i);
                if (set == null) {
                    continue;
                }
                if (i > 0) {
                    ornamentsSets.append(",");
                }
                ornamentsSets.append(set.optString("id"));
            }
        } catch (Throwable t) {
            Log.err(TAG, "getOrnamentsSets err:", t);
        }
        return ornamentsSets.toString();
    }

    // 一起拿小鸡饲料
  /*  private void letsGetChickenFeedTogether() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.letsGetChickenFeedTogether());
            if (jo.optBoolean("success")) {
                String bizTraceId = jo.optString("bizTraceId");
                JSONArray p2pCanInvitePersonDetailList = jo.optJSONArray("p2pCanInvitePersonDetailList");
                
                int canInviteCount = 0;
                int hasInvitedCount = 0;
                List<String> userIdList = new ArrayList<>(); // 保存 userId
                for (int i = 0; i < p2pCanInvitePersonDetailList.length(); i++) {
                    JSONObject personDetail = p2pCanInvitePersonDetailList.optJSONObject(i);
                    String inviteStatus = personDetail.optString("inviteStatus");
                    String userId = personDetail.optString("userId");
                    
                    if (inviteStatus.equals("CAN_INVITE")) {
                        userIdList.add(userId);
                        canInviteCount++;
                    }
                    else if (inviteStatus.equals("HAS_INVITED")) {
                        hasInvitedCount++;
                    }
                }
                
                int invitedToday = hasInvitedCount;
                
                int remainingInvites = 5 - invitedToday;
                int invitesToSend = Math.min(canInviteCount, remainingInvites);
                
                if (invitesToSend == 0) {
                    return;
                }
                
                Set<String> getFeedSet = getFeedList.getValue();
                
                //if (getFeedType.getValue() == GetFeedType.GIVE) {
                    //for (String userId : userIdList) {
                        //if (invitesToSend <= 0) {
                            //                            Log.record("已达到最大邀请次数限制，停止发送邀请。");
                            //break;
                        //}
                        //if (getFeedSet.contains(userId)) {
                         //   jo = MyUtils.newJSONObject(AntFarmRpcCall.giftOfFeed(bizTraceId, userId));
                            //if (jo.optBoolean("success")) {
                                //Log.record("一起拿小鸡饲料🥡 [送饲料：" + UserIdMap.getMaskName(userId) + "]");
                                //invitesToSend--; // 每成功发送一次邀请，减少一次邀请次数
                            //}
                            //else {
                                //Log.farm("邀请失败：" + jo);
                                //break;
                            //}
                        //}
                        //else {
                            //                            Log.record("用户 " + UserIdMap.getMaskName(userId) + "
                            // 不在勾选的好友列表中，不发送邀请。");
                     //   }
                  //  }
             //   }
                else {
                    Random random = new Random();
                    for (int j = 0; j < invitesToSend; j++) {
                        int randomIndex = random.nextInt(userIdList.size());
                        String userId = userIdList.get(randomIndex);
                        
                        jo = MyUtils.newJSONObject(AntFarmRpcCall.giftOfFeed(bizTraceId, userId));
                        if (jo.optBoolean("success")) {
                            Log.record("一起拿小鸡饲料🥡 [送饲料：" + UserIdMap.getMaskName(userId) + "]");
                        }
                        else {
                            Log.farm("邀请失败：" + jo);
                            break;
                        }
                        userIdList.remove(randomIndex);
                    }
                }
            }
        }
        catch (Throwable t) {
            Log.i(TAG, "letsGetChickenFeedTogether err:");
            Log.printStackTrace(t);
        }
    }  */

    private void family() {
        if (StringUtil.isEmpty(ownerGroupId)) {
            return;
        }
        // 检查 ExtensionsHandle 是否存在
        try {
            Class.forName("io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle");
            ExtensionsHandle.handleAlphaRequest("antFarm", "doFamilyTask", null);
        } catch (ClassNotFoundException e) {
            Log.record("ExtensionsHandle 类未找到，跳过扩展处理");
        }
        try {
            JSONObject joenterFamily = enterFamily();
            JSONObject jo;
            if (joenterFamily == null) {
                return;
            }
            ownerGroupId = joenterFamily.optString("groupId");
            int familyAwardNum = joenterFamily.optInt("familyAwardNum");
            boolean familySignTips = joenterFamily.optBoolean("familySignTips");
            JSONObject assignFamilyMemberInfo = joenterFamily.optJSONObject("assignFamilyMemberInfo");
            boolean feedFriendLimit = joenterFamily.optBoolean("feedFriendLimit", false);
            JSONArray familyAnimals = joenterFamily.optJSONArray("animals");
            if (familyAnimals == null) {
                return;
            }
            JSONArray EatTogetherUserIds = new JSONArray();
            // 修复：创建新的 JSONArray 副本，避免修改原始 familyAnimals
            JSONArray familyAnimalsExceptUser = new JSONArray();
            for (int i = 0; i < familyAnimals.length(); i++) {
                jo = familyAnimals.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String userId = jo.optString("userId");
                EatTogetherUserIds.put(userId);
                if (!userId.equals(UserIdMap.getCurrentUid())) {
                    familyAnimalsExceptUser.put(jo);
                }
            }
            // 获取家庭成员ID列表
            List<String> familyUserIds = new ArrayList<>();
            for (int i = 0; i < familyAnimals.length(); i++) {
                jo = familyAnimals.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String animalId = jo.optString("animalId");
                String userId = jo.optString("userId");
                familyUserIds.add(userId);
                if (animalId.equals(ownerAnimal.animalId)) {
                    continue;
                }
                String farmId = jo.optString("farmId");
                JSONObject animalStatusVO = jo.optJSONObject("animalStatusVO");
                if (animalStatusVO == null) {
                    continue;
                }
                String animalFeedStatus = animalStatusVO.optString("animalFeedStatus");
                String animalInteractStatus = animalStatusVO.optString("animalInteractStatus");
                if (AnimalInteractStatus.HOME.name().equals(animalInteractStatus) && AnimalFeedStatus.HUNGRY.name().equals(animalFeedStatus)) {
                    // feedFriendLimit 是服务端"今日帮喂已达上限"的信号，已满就不必逐个成员再试
                    if (familyOptions.getValue().contains("familyFeed") && !feedFriendLimit) {
                        feedFriendAnimal(farmId);
                    }
                }
            }

            // 家庭签到
            if (familySignTips && familyOptions.getValue().contains("familySign")) {
                familySign();
            }
            // 顶梁柱功能
            JSONObject assignRights = assignFamilyMemberInfo != null ? assignFamilyMemberInfo.optJSONObject("assignRights") : null;
            if (assignRights != null && familyOptions.getValue().contains("assignRights") && !"USED".equals(assignRights.optString("status"))) {
                if (UserIdMap.getCurrentUid().equals(assignRights.optString("assignRightsOwner"))) {
                    assignFamilyMember(assignFamilyMemberInfo, familyUserIds);
                }
                /*else {
                    Log.record("家庭任务🏡[使用顶梁柱特权] 不是家里的顶梁柱！");
                     移除选项，避免重复检查
                    familyOptions.getValue().remove("assignRights");
                }*/
            }

            // 领取家庭奖励
            if (familyOptions.getValue().contains("familyClaimReward") && familyAwardNum > 0) {
                familyAwardList();
            }

            JSONArray familyInteractActions = joenterFamily.optJSONArray("familyInteractActions");
            JSONObject eatTogetherConfig = joenterFamily.optJSONObject("eatTogetherConfig");
            //家庭请客吃饭
            boolean canEatTogether = true;
            if (familyInteractActions != null) {
                for (int i = 0; i < familyInteractActions.length(); i++) {
                    JSONObject familyInteractAction = familyInteractActions.optJSONObject(i);
                    if (familyInteractAction != null && "EatTogether".equals(familyInteractAction.optString("familyInteractType"))) {
                        canEatTogether = false;
                    }
                }
            }
            // 一起吃饭
            if (canEatTogether && familyOptions.getValue().contains("familyEatTogether") && eatTogetherConfig != null && eatTogetherConfig.has("periodItemList")) {
                familyEatTogether(ownerGroupId, EatTogetherUserIds);
            }

            // 道早安
            if (familyOptions.getValue().contains("deliverMsgSend")) {
                deliverMsgSend(familyAnimalsExceptUser, familyUserIds);
            }

            // 分享给好友
            if (familyOptions.getValue().contains("shareToFriends")) {
                familyShareToFriends(ownerGroupId, familyUserIds, notInviteList);
            }

            // 兑换家庭装饰（消耗装修金）
            if (familyOptions.getValue().contains("ExchangeFamilyDecoration")) {
                autoExchangeFamilyDecoration();
            }
        } catch (TaskCancelledException cancelled) { throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "family err:", t);
        }
    }

    /**
     * 家庭装扮：分类遍历装修金商城家具列表，逐个兑换当前余额买得起、尚未拥有的家具。
     * 对齐 GR2026 main_my AntFarmFamily.kt#autoExchangeFamilyDecoration（提交 c9287afd）。
     */
    private void autoExchangeFamilyDecoration() {
        Log.record(TAG, "[家庭装扮] 启动分类购买任务...");
        try {
            JSONObject familyJo = MyUtils.newJSONObject(AntFarmRpcCall.enterFamily());
            if (!ResChecker.checkRes(TAG, familyJo)) return;

            String activityId = familyJo.optString("decorationCoinActivityId", "20250808");
            Log.record(TAG, "[家庭装扮] 当前活动 ID: " + activityId);

            String[] labelTypes = {
                    "", "recentlyAdded", "sofa", "seat2", "seat4", "seat5", "seat3",
                    "curtain", "table", "carpet", "mattress", "bed3", "bed4",
                    "bed5", "ceiling", "windowView", "firstFloor", "firstWall", "secondFloor",
                    "secondWall", "leftWallDecoration", "rightWallDecoration", "treadmill", "slide"
            };

            int currentBalance = 0;

            for (String label : labelTypes) {
                int startIndex = 0;
                boolean hasMore = true;
                Log.record(TAG, "[家庭装扮] 正在检查分类: " + (label.isEmpty() ? "新品" : label));

                while (hasMore) {
                    JSONObject itemJo = MyUtils.newJSONObject(AntFarmRpcCall.getFitmentItemList(activityId, 10, label, startIndex));
                    if (!ResChecker.checkRes(TAG, itemJo)) break;

                    JSONObject accountInfo = itemJo.optJSONObject("mallAccountInfoVO");
                    currentBalance = accountInfo == null ? 0 : accountInfo.optJSONObject("holdingCount") == null ? 0 : accountInfo.optJSONObject("holdingCount").optInt("cent", 0);

                    JSONArray items = itemJo.optJSONArray("itemInfoVOList");
                    if (items == null || items.length() == 0) break;

                    for (int j = 0; j < items.length(); j++) {
                        JSONObject item = items.optJSONObject(j);
                        if (item == null) {
                            continue;
                        }
                        String spuId = item.optString("spuId");
                        String spuName = item.optString("spuName");
                        JSONObject minPrice = item.optJSONObject("minPrice");
                        int price = minPrice == null ? 9999999 : minPrice.optInt("cent", 9999999);

                        JSONArray itemStatusList = item.optJSONArray("itemStatusList");
                        boolean canBuy = itemStatusList == null || itemStatusList.length() == 0;

                        if (canBuy && currentBalance >= price) {
                            JSONArray skuList = item.optJSONArray("skuModelList");
                            JSONObject firstSku = skuList != null && skuList.length() > 0 ? skuList.optJSONObject(0) : null;
                            if (firstSku != null) {
                                String skuId = firstSku.optString("skuId");
                                Log.record(TAG, "[家庭装扮] 发现未拥有家具: " + spuName);

                                JSONObject exchangeJo = MyUtils.newJSONObject(AntFarmRpcCall.exchangeBenefit(spuId, skuId, activityId));
                                if (ResChecker.checkRes(TAG, exchangeJo)) {
                                    Log.farm("家庭装扮💸#成功购买[" + spuName + "]#消耗[" + (price / 100) + "装修金]");
                                    currentBalance -= price;
                                }
                                TimeUtil.sleep(2000);
                            }
                        }
                    }

                    int nextIndex = itemJo.optInt("nextStartIndex", 0);
                    boolean hasMoreField = itemJo.optBoolean("hasMore", false);
                    if (hasMoreField && nextIndex > startIndex) {
                        startIndex = nextIndex;
                    } else {
                        hasMore = false;
                    }
                }

                // seat3 分类处理完后余额不足 49 装修金，终止后续更贵分类的遍历
                if (currentBalance < 4900 && "seat3".equals(label)) {
                    Log.record(TAG, "[家庭装扮] 装修金不足 49 且已完成 seat3 遍历，终止任务");
                    break;
                }
            }
            Log.record(TAG, "[家庭装扮] 全量检查任务执行完毕");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "autoExchangeFamilyDecoration 失败", t);
        }
    }

    /**
     * 顶梁柱功能
     */
    private void assignFamilyMember(JSONObject jsonObject, List<String> userIds) {
        try {
            String uid = UserIdMap.getCurrentUid(), day = rankingDay(System.currentTimeMillis());
            List<String> candidates = new ArrayList<>(new LinkedHashSet<>(userIds));
            candidates.removeIf(id -> id == null || id.trim().isEmpty() || id.equals(uid));
            if (candidates.isEmpty()) {
                return;
            }
            // RandomUtil.nextInt 是右开区间 [min, max)，这里要传 size()/length() 才能取到最后一个
            String beAssignUser = candidates.get(RandomUtil.nextInt(0, candidates.size()));
            if (familyAssignStrategy.getValue() == 1) {
                TimeUtil.sleep(0);
                JSONObject contribution = MyUtils.newJSONObject(AntFarmRpcCall.familyTreadMill());
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || !day.equals(rankingDay(System.currentTimeMillis()))) return;
                String lowest = MessageUtil.checkMemo(TAG, contribution) ? lowestFamilyContributor(contribution, candidates, uid) : null;
                if (lowest != null) beAssignUser = lowest;
                else Log.record("家庭顶梁柱🏠贡献资料不完整，按原随机策略安排");
            } else if (familyAssignStrategy.getValue() != 0) return;
            JSONArray assignConfigList = jsonObject.optJSONArray("assignConfigList");
            if (assignConfigList == null || assignConfigList.length() == 0) {
                Log.record("家庭任务🏡[使用顶梁柱特权] assignConfigList 为空，跳过");
                return;
            }
            JSONObject assignConfig = assignConfigList.optJSONObject(RandomUtil.nextInt(0, assignConfigList.length()));
            if (assignConfig == null) {
                return;
            }
            String action = assignConfig.optString("assignAction"), flag = "antFarm::familyAssignAttempt";
            if (!(assignConfig.opt("assignAction") instanceof String) || action.isEmpty() || Status.hasFlagToday(flag)) return;
            TimeUtil.sleep(0);
            if (!uid.equals(UserIdMap.getCurrentUid()) || !day.equals(rankingDay(System.currentTimeMillis()))) return;
            Status.flagToday(flag);
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.assignFamilyMember(action, beAssignUser));
            TimeUtil.sleep(0);
            if (!uid.equals(UserIdMap.getCurrentUid()) || !day.equals(rankingDay(System.currentTimeMillis()))) return;
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("家庭任务🏡[使用顶梁柱特权] " + assignConfig.optString("assignDesc"));
            }
        } catch (TaskCancelledException cancelled) { throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "assignFamilyMember err:", t);
        }
    }

    private static String lowestFamilyContributor(JSONObject state, List<String> candidates, String uid) {
        JSONArray rows = state.optJSONArray("familyMemberInfoList");
        if (rows == null || rows.length() > 100) return null;
        Map<String, JSONObject> known = new HashMap<>();
        Comparator<JSONObject> order = Comparator.comparingInt((JSONObject row) -> rankingInt(row, "todayIntimateNum"))
                .thenComparingInt(row -> rankingInt(row, "totalIntimateNum")).thenComparingInt(row -> rankingInt(row, "userDonateCount"))
                .thenComparing(row -> row.optString("userId"));
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("userId") instanceof String) || row.optString("userId").isEmpty()
                    || row.has("currentUser") && !(row.opt("currentUser") instanceof Boolean)) return null;
            String id = row.optString("userId");
            if (id.equals(uid) || Boolean.TRUE.equals(row.opt("currentUser")) || !candidates.contains(id)) continue;
            if (rankingInt(row, "todayIntimateNum") < 0 || rankingInt(row, "totalIntimateNum") < 0 || rankingInt(row, "userDonateCount") < 0
                    || known.put(id, row) != null) return null;
        }
        if (known.size() != candidates.size()) return null;
        return known.values().stream().min(order).map(row -> row.optString("userId")).orElse(null);
    }

    private void familyEatTogether(String groupId, JSONArray EatTogetherUserIds) {
        // 按北京时间判断餐段：TimeUtil.isAfterTimeStr/isBeforeTimeStr 内部用系统默认时区的
        // Calendar.getInstance() 构造时间边界，宿主设备时区不是东八区时会算错餐段窗口，
        // 与 deliverMsgSend 的 GMT+8 时间窗问题同一类，这里直接取 GMT+8 小时数比较，不经过 TimeUtil。
        int hourOfDayGmt8 = MyUtils.getInstance().get(Calendar.HOUR_OF_DAY);
        String periodName;
        if (hourOfDayGmt8 >= 6 && hourOfDayGmt8 < 11) {
            periodName = "早餐";
        } else if (hourOfDayGmt8 >= 11 && hourOfDayGmt8 < 16) {
            periodName = "午餐";
        } else if (hourOfDayGmt8 >= 16 && hourOfDayGmt8 < 20) {
            periodName = "晚餐";
        } else {
            return;
        }
        try {
            JSONArray cuisines = queryRecentFarmFood(EatTogetherUserIds.length());
            if (cuisines == null) {
                return;
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.familyEatTogether(groupId, cuisines, EatTogetherUserIds));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠" + periodName + "请客#消耗美食" + EatTogetherUserIds.length() + "份");
                syncFamilyStatus(groupId);
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyEatTogether err:", t);
        }
    }

    /**
     * 家庭「道早安」任务
     * <p>
     * <p>
     * <p>
     * 1）先通过 familyTaskTips 判断今日是否还有「道早安」任务：
     * - 请求方法：com.alipay.antfarm.familyTaskTips
     * - 请求体关键字段：
     * animals      -> 直接复用 enterFamily 返回的家庭 animals 列表
     * taskSceneCode-> "ANTFARM_FAMILY_TASK"
     * sceneCode    -> "ANTFARM"
     * source       -> "H5"
     * requestType  -> "NORMAL"
     * timeZoneId   -> "Asia/Shanghai"
     * - 响应 familyTaskTips 数组中存在 bizKey="GREETING" 且 taskStatus="TODO" 时，说明可以道早安
     * <p>
     * 2）未完成早安任务时，按顺序调用以下 RPC 获取 AI 文案并发送：
     * a. com.alipay.antfarm.deliverSubjectRecommend
     * -> 入参：friendUserIds（家庭其他成员 userId 列表），sceneCode="ChickFamily"，source="H5"
     * -> 取出：ariverRpcTraceId、eventId、eventName、sceneId、sceneName 等上下文
     * b. com.alipay.antfarm.DeliverContentExpand
     * -> 入参：上一步取到的 ariverRpcTraceId / eventId / eventName / sceneId / sceneName 等 + friendUserIds
     * -> 返回：AI 生成的 content 以及 deliverId
     * c. com.alipay.antfarm.QueryExpandContent
     * -> 入参：deliverId
     * -> 用于再次确认 content 与场景（可选安全校验）
     * d. com.alipay.antfarm.DeliverMsgSend
     * -> 入参：content、deliverId、friendUserIds、groupId（家庭 groupId）、sceneCode="ANTFARM"、spaceType="ChickFamily" 等
     * <p>
     * 额外增加保护：
     * - 仅在每天 06:00~10:00 之间执行
     * - 每日仅发送一次（本地 Status 标记 + 远端 familyTaskTips 双重判断）
     * - 自动从家庭成员列表中移除自己，避免接口报参数错误
     *
     * @param familyUserIds 家庭成员 userId 列表（包含自己，方法内部会移除当前账号）
     */
    /**
     * 从道早安相关响应里尽量取出可发送的文案：依次尝试常见字段名，顶层取不到再进 data 里找一遍。
     * 对齐 Sesame-AG AntFarmFamily.kt#extractGreetingContent，用于 QueryExpandContent 字段名不稳定/
     * 响应结构变化时不至于直接判定整次道早安失败。
     */
    private static String extractGreetingContent(JSONObject response) {
        if (response == null) {
            return "";
        }
        String[] directKeys = {"content", "expandContent", "deliverContent", "msgContent", "text"};
        for (String key : directKeys) {
            String value = response.optString(key, "").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        JSONObject data = response.optJSONObject("data");
        if (data != null) {
            for (String key : directKeys) {
                String value = data.optString(key, "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private void deliverMsgSend(JSONArray familyAnimalsExceptUser, List<String> familyUserIds) {
        try {
            // 时间窗口控制：仅允许在「早安时间段」内自动发送（06:00 ~ 10:00，按北京时间判断，
            // 对齐 Sesame-AG AntFarmFamily.kt#deliverMsgSend 用 MyUtils.getInstance()（GMT+8）而不是
            // 系统默认时区——宿主设备时区不是东八区时，原来的 Calendar.getInstance() 会算错窗口）
            Calendar now = MyUtils.getInstance();
            Calendar startTime = MyUtils.getInstance();
            startTime.set(Calendar.HOUR_OF_DAY, 6);
            startTime.set(Calendar.MINUTE, 0);
            startTime.set(Calendar.SECOND, 0);
            startTime.set(Calendar.MILLISECOND, 0);

            Calendar endTime = MyUtils.getInstance();
            endTime.set(Calendar.HOUR_OF_DAY, 10);
            endTime.set(Calendar.MINUTE, 0);
            endTime.set(Calendar.SECOND, 0);
            endTime.set(Calendar.MILLISECOND, 0);

            if (now.before(startTime) || now.after(endTime)) {
                //Log.record("家庭任务🏠道早安#当前时间不在 06:00-10:00，跳过");
                return;
            }

            if (StringUtil.isEmpty(ownerGroupId)) {
                Log.record("家庭任务🏠道早安#未检测到家庭 groupId，可能尚未加入家庭，跳过");
                return;
            }

            // 本地去重：一天只发送一次
            if (Status.hasFlagToday("antFarm::deliverMsgSend")) {
                //Log.record("家庭任务🏠道早安#今日已在本地发送过，跳过");
                return;
            }

            // 远端任务状态校验
            try {
                JSONObject taskTipsRes = MyUtils.newJSONObject(AntFarmRpcCall.familyTaskTips(familyAnimalsExceptUser));
                if (!MessageUtil.checkMemo(TAG, taskTipsRes)) {
                    Log.record("家庭任务🏠道早安#familyTaskTips 调用失败，跳过");
                    return;
                }

                JSONArray taskTips = taskTipsRes.optJSONArray("familyTaskTips");
                if (taskTips == null || taskTips.length() == 0) {
                    Log.record("家庭任务🏠道早安#远端无 GREETING 任务，可能今日已完成，跳过");

                    Status.flagToday("antFarm::deliverMsgSend");
                    return;
                }

                boolean hasGreetingTodo = false;
                for (int i = 0; i < taskTips.length(); i++) {
                    JSONObject item = taskTips.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String bizKey = item.optString("bizKey");
                    String taskStatus = item.optString("taskStatus");
                    if ("GREETING".equals(bizKey) && "TODO".equals(taskStatus)) {
                        hasGreetingTodo = true;
                        break;
                    }
                }

                if (!hasGreetingTodo) {
                    Log.record("家庭任务🏠道早安#GREETING 任务非 TODO 状态，跳过");
                    Status.flagToday("antFarm::deliverMsgSend");
                    return;
                }
            } catch (Throwable e) {
                Log.printStackTrace("familyTaskTips 解析失败，出于安全考虑跳过道早安：", e);
                return;
            }

            // 构建好友 userId 列表（去掉自己）
            List<String> userIdsCopy = new ArrayList<>(familyUserIds);
            userIdsCopy.remove(UserIdMap.getCurrentUid());
            if (userIdsCopy.isEmpty()) {
                Log.record("家庭任务🏠道早安#家庭成员仅自己一人，跳过");
                return;
            }

            JSONArray userIds = new JSONArray();
            for (String userId : userIdsCopy) {
                userIds.put(userId);
            }

            // 确认 AI 隐私协议
            JSONObject resp0 = MyUtils.newJSONObject(AntFarmRpcCall.OpenAIPrivatePolicy());
            if (!MessageUtil.checkMemo(TAG, resp0)) {
                Log.record("家庭任务🏠道早安#OpenAIPrivatePolicy 调用失败");
                return;
            }

            // 请求推荐早安场景
            JSONObject resp1 = MyUtils.newJSONObject(AntFarmRpcCall.deliverSubjectRecommend(userIds));
            if (!MessageUtil.checkMemo(TAG, resp1)) {
                Log.record("家庭任务🏠道早安#deliverSubjectRecommend 调用失败");
                return;
            }

            String ariverRpcTraceId = resp1.optString("ariverRpcTraceId");
            String eventId = resp1.optString("eventId");
            String eventName = resp1.optString("eventName");
            String memo = resp1.optString("memo");
            String resultCode = resp1.optString("resultCode");
            String sceneId = resp1.optString("sceneId");
            String sceneName = resp1.optString("sceneName");
            boolean success = resp1.optBoolean("success", true);

            // 调用 DeliverContentExpand
            JSONObject resp2 = MyUtils.newJSONObject(AntFarmRpcCall.deliverContentExpand(ariverRpcTraceId, eventId, eventName, memo, resultCode, sceneId, sceneName, success, userIds));
            if (!MessageUtil.checkMemo(TAG, resp2)) {
                Log.record("家庭任务🏠道早安#DeliverContentExpand 调用失败");
                return;
            }

            String deliverId = resp2.optString("deliverId");
            //String deliverId = System.currentTimeMillis()+UserIdMap.getCurrentUid();

            // 使用 deliverId 确认扩展内容；QueryExpandContent 只是可选的二次校验，
            // 失败或字段缺失时回退到上一步 DeliverContentExpand 已生成的文案，不直接放弃本次道早安
            JSONObject resp3 = MyUtils.newJSONObject(AntFarmRpcCall.QueryExpandContent(deliverId));
            String content = MessageUtil.checkMemo(TAG, resp3) ? extractGreetingContent(resp3) : "";
            if (StringUtil.isEmpty(content)) {
                String fallbackContent = extractGreetingContent(resp2);
                if (StringUtil.isEmpty(fallbackContent)) {
                    Log.record("家庭任务🏠道早安#未获取到可发送文案，跳过");
                    return;
                }
                Log.record("家庭任务🏠道早安#QueryExpandContent 调用失败，已回退到 DeliverContentExpand 文案");
                content = fallbackContent;
            }

            // 最终发送早安消息
            JSONObject resp4 = MyUtils.newJSONObject(AntFarmRpcCall.deliverMsgSend(ownerGroupId, userIds, content, deliverId));
            if (MessageUtil.checkMemo(TAG, resp4)) {
                Log.farm("家庭任务🌈[道早安]" + StringUtil.truncate(content, 200));
                Status.flagToday("antFarm::deliverMsgSend");
            }
        } catch (Throwable t) {
            Log.err(TAG, "deliverMsgSend err:", t);
        }
    }

    /**
     * 好友分享家庭
     */
    private void familyShareToFriends(String ownerGroupId, List<String> familyUserIds, SelectModelField notInviteList) {
        try {
            if (Status.hasFlagToday("antFarm::familyShareToFriends")) {
                return;
            }

            Set<String> notInviteSet = notInviteList.getValue();
            String uid = UserIdMap.getCurrentUid(), day = rankingDay(System.currentTimeMillis());
            Set<String> selected = familyShareList.getValue();
            int mode = familyShareMode.getValue();
            if (familyUserIds.size() >= 6 || mode < 0 || mode > 1 || mode == 0 && selected.isEmpty()) return;
            List<AlipayUser> allUser = AlipayUser.getList();
            if (allUser.isEmpty()) {
                Log.record("allUser is empty");
                return;
            }

            // 打乱顺序，实现随机选取
            List<AlipayUser> shuffledUsers = new ArrayList<>(allUser);
            Collections.shuffle(shuffledUsers);
            JSONArray inviteList = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (AlipayUser user : shuffledUsers) {
                String id = user.getId();
                if (id != null && !id.isEmpty() && seen.add(id) && !familyUserIds.contains(id) && !notInviteSet.contains(id) && !id.equals(uid)
                        && (mode == 0 ? selected.contains(id) : !selected.contains(id)) && !Status.hasFlagToday("antFarm::familyInviteAttempt::" + id)) {
                    inviteList.put(user.getId());
                    if (inviteList.length() >= 2) {
                        break;
                    }
                }
            }

            if (inviteList.length() == 0) {
                Log.record("没有符合分享条件的好友");
                return;
            }
            Log.record("家庭分享🏠邀请:" + inviteList);

            //JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.inviteFriendVisitFamily(inviteList));
            int invitedCount = 0;
            for (int i = 0; i < inviteList.length(); i++) {
                String inviteUID = inviteList.optString(i);
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || !day.equals(rankingDay(System.currentTimeMillis()))) return;
                Status.flagToday("antFarm::familyInviteAttempt::" + inviteUID);
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.batchInviteP2P(ownerGroupId, inviteUID));
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || !day.equals(rankingDay(System.currentTimeMillis()))) return;
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("家庭任务🏠分享给好友[" + UserIdMap.getShowName(inviteUID) + "]");
                    invitedCount++;
                }
            }
            // 全部失败时累计失败次数，达到次数上限才置标记：既不会"一次失败就整天不试"，也不会每轮都重发邀请
            if (invitedCount > 0) {
                Status.flagToday("antFarm::familyShareToFriends");
            } else {
                int failCount = Status.getIntFlagToday(FLAG_FAMILY_SHARE_FAIL_COUNT) + 1;
                if (failCount >= MAX_FAMILY_SHARE_ATTEMPT) {
                    Status.flagToday("antFarm::familyShareToFriends");
                    Log.farm("家庭分享🏠邀请已连续失败" + failCount + "次，今日不再尝试");
                } else {
                    Status.setIntFlagToday(FLAG_FAMILY_SHARE_FAIL_COUNT, failCount);
                    Log.farm("家庭分享🏠邀请全部失败(第" + failCount + "/" + MAX_FAMILY_SHARE_ATTEMPT + "次)，稍后重试");
                }
            }
        } catch (TaskCancelledException cancelled) { throw cancelled;
        } catch (Throwable t) {
            Log.err(TAG, "familyShareToFriends err:", t);
        }
    }

    /**
     * 时间差格式化
     */
    private String formatDuration(long diffMillis) {
        long absSeconds = Math.abs(diffMillis) / 1000;

        long value;
        String unit;
        if (absSeconds < 60) {
            value = absSeconds;
            unit = "秒";
        } else if (absSeconds < 3600) {
            value = absSeconds / 60;
            unit = "分钟";
        } else if (absSeconds < 86400) {
            value = absSeconds / 3600;
            unit = "小时";
        } else if (absSeconds < 2592000) {
            value = absSeconds / 86400;
            unit = "天";
        } else if (absSeconds < 31536000) {
            value = absSeconds / 2592000;
            unit = "个月";
        } else {
            value = absSeconds / 31536000;
            unit = "年";
        }

        if (absSeconds < 1) {
            return "刚刚";
        } else if (diffMillis > 0) {
            return value + unit + "后";
        } else {
            return value + unit + "前";
        }
    }

    private String getFamilyGroupId(String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(userId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                return jo.optString("groupId");
            }
        } catch (Throwable t) {
            Log.i(TAG, "getGroupId err:");
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject enterFamily() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterFamily());
            if (MessageUtil.checkMemo(TAG, jo)) {
                return jo;
            }
        } catch (Throwable t) {
            Log.err(TAG, "enterFamily err:", t);
        }
        return null;
    }


    private Boolean familyWakeUp() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.familyWakeUp());
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠小鸡起床");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyWakeUp err:", t);
        }
        return false;
    }

    private void familyAwardList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.familyAwardList());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray ja = jo.optJSONArray("familyAwardRecordList");
            if (ja == null) {
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (jo.optBoolean("expired") || jo.optBoolean("received", true) || jo.has("linkUrl") || (jo.has("operability") && !jo.optBoolean("operability"))) {
                    continue;
                }
                String rightId = jo.optString("rightId");
                String awardName = jo.optString("awardName");
                int count = jo.optInt("count", 1);
                receiveFamilyAward(rightId, awardName, count);
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyAwardList err:", t);
        }
    }

    private void receiveFamilyAward(String rightId, String awardName, int count) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveFamilyAward(rightId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠领取奖励[" + awardName + "*" + count + "]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyAwardList err:", t);
        }
    }

    private void familyReceiveFarmTaskAward(String taskId, String title) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.familyReceiveFarmTaskAward(taskId));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("亲密家庭🏠提交任务[" + title + "]");
                if ("FAMILY_SIGN_TASK".equals(taskId)) DailyTask.done("farm::familySign");
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyReceiveFarmTaskAward err:", t);
        }
    }

    private JSONArray queryRecentFarmFood(int needCount) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.syncAnimalStatus(ownerFarmId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return null;
            }
            JSONArray cuisineList = jo.optJSONArray("cuisineList");
            if (cuisineList == null || cuisineList.length() == 0) {
                return null;
            }
            List<JSONObject> list = getSortedCuisineList(cuisineList);
            JSONArray result = new JSONArray();
            int count = 0;
            for (int i = 0; i < list.size() && count < needCount; i++) {
                jo = list.get(i);
                int countTemp = jo.optInt("count");
                if (count + countTemp >= needCount) {
                    countTemp = needCount - count;
                    jo.put("count", countTemp);
                }
                count += countTemp;
                result.put(jo);
            }
            if (count == needCount) {
                return result;
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryRecentFarmFood err:", t);
        }
        return null;
    }

    private void familySign() {
        if (DailyTask.skip("farm::familySign", "庄园家庭签到")) return;
        familyReceiveFarmTaskAward("FAMILY_SIGN_TASK", "每日签到");
    }

    private void syncFamilyStatus(String groupId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.syncFamilyStatus(groupId, "INTIMACY_VALUE", ownerUserId));
            MessageUtil.checkMemo(TAG, jo);
        } catch (Throwable t) {
            Log.err(TAG, "syncFamilyStatus err:", t);
        }
    }

    public interface RecallAnimalType {

        int ALWAYS = 0;
        int WHEN_THIEF = 1;
        int WHEN_HUNGRY = 2;
        int NEVER = 3;

        String[] nickNames = {"始终召回", "偷吃召回", "饥饿召回", "暂不召回"};
    }

    public interface SendBackAnimalWay {

        int HIT = 0;
        int NORMAL = 1;

        String[] nickNames = {"攻击", "常规"};
    }

    public interface SendBackAnimalType {

        int NONE = 0;
        int BACK = 1;
        int NOT_BACK = 2;

        String[] nickNames = {"不遣返小鸡", "遣返已选好友", "遣返未选好友"};
    }

    public enum AnimalBuff {
        ACCELERATING, INJURED, NONE
    }

    public enum AnimalFeedStatus {
        HUNGRY, EATING, SLEEPY
    }

    public enum AnimalInteractStatus {
        HOME, GOTOSTEAL, STEALING
    }

    public enum SubAnimalType {
        NORMAL, GUEST, PIRATE, WORK, NPC
    }

    public enum ToolType {
        STEALTOOL, ACCELERATETOOL, SHARETOOL, FENCETOOL, NEWEGGTOOL, DOLLTOOL, BIG_EATER_TOOL, ADVANCE_ORNAMENT_TOOL, ORDINARY_ORNAMENT_TOOL, RARE_ORNAMENT_TOOL;

        public static final CharSequence[] nickNames = {"蹭饭卡", "加速卡", "救济卡", "篱笆卡", "新蛋卡", "公仔补签卡", "加饭卡", "高级装扮补签", "普通装扮补签卡", "稀有装扮补签卡"};

        public CharSequence nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum GameType {
        starGame, jumpGame, flyGame, hitGame;

        public static final CharSequence[] gameNames = {"星星球", "登山赛", "飞行赛", "欢乐揍小鸡"};

        public CharSequence gameName() {
            return gameNames[ordinal()];
        }
    }

    private static class Animal {
        public String animalId, currentFarmId, masterFarmId, animalBuff, subAnimalType, animalFeedStatus, animalInteractStatus;
        public String locationType;

        public String currentFarmMasterUserId;

        public Long startEatTime, beHiredEndTime;

        public Double consumeSpeed;

        public Double foodHaveEatten;
    }

    public enum TaskStatus {
        TODO, FINISHED, RECEIVED
    }

    private static class RewardFriend {
        public String consistencyKey, friendId, time;
    }

    private static class FarmTool {
        public ToolType toolType;
        public String toolId;
        public int toolCount, toolHoldLimit;
    }

    public interface HireAnimalType {

        int NONE = 0;
        int HIRE = 1;
        int NOT_HIRE = 2;

        String[] nickNames = {"不雇佣小鸡", "雇佣已选好友", "雇佣未选好友"};
    }

    //  public interface GetFeedType {

    //      int NONE = 0;
    //      int GIVE = 1;
    //     int RANDOM = 2;

    //    String[] nickNames = {"不赠送饲料", "赠送已选好友", "赠送随机好友"};
    // }

    public interface NotifyFriendType {

        int NONE = 0;
        int NOTIFY = 1;
        int NOT_NOTIFY = 2;

        String[] nickNames = {"不通知赶鸡", "通知已选好友", "通知未选好友"};
    }

    public interface DonationType {

        int ZERO = 0;
        int ONE = 1;
        int ALL = 2;

        String[] nickNames = {"不捐赠", "捐赠一个项目", "捐赠所有项目"};
    }

    public enum ItemStatus {
        NO_ENOUGH_POINT, REACH_LIMIT, REACH_USER_HOLD_LIMIT;

        public static final String[] nickNames = {"乐园币不足", "兑换达到上限", "达到用户持有上限"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }
}
