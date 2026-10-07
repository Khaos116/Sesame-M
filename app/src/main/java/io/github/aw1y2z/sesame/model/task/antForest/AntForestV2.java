package io.github.aw1y2z.sesame.model.task.antForest;

import static io.github.aw1y2z.sesame.model.normal.base.BaseModel.taskRpcRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.EmptyModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ListModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.TextModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAntForestHuntTaskList;
import io.github.aw1y2z.sesame.entity.AlipayAntForestVitalityTaskList;
import io.github.aw1y2z.sesame.entity.AlipayMonopolyTaskList;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.entity.CollectEnergyEntity;
import io.github.aw1y2z.sesame.entity.CustomOption;
import io.github.aw1y2z.sesame.entity.FriendWatch;
import io.github.aw1y2z.sesame.entity.KVNode;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.entity.VitalityBenefit;
import io.github.aw1y2z.sesame.entity.AlipayForestHunt;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.model.task.forestRead.ReadForestTask;
import io.github.aw1y2z.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.aw1y2z.sesame.ui.ObjReference;
import io.github.aw1y2z.sesame.util.AverageMath;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.ListUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.VitalityBenefitIdMap;
import lombok.Getter;

/**
 * 蚂蚁森林V2
 */
public class AntForestV2 extends ModelTask {

    private static final String TAG = AntForestV2.class.getSimpleName();

    /**
     * 组队合种浇水本地标记：表示"本方法把用户切到了组队模式，还没切回来"。
     * 用于切回失败 / 进程被杀之后的校正，避免用户被永久留在组队模式。
     */
    private static final String FLAG_TEAM_MODE_SWITCHED = "Forest::teamWaterSwitchedToTeam";

    /**
     * 真爱合种浇水的当日标记。持久化 key，**不能改名**（改名等于让所有人当天再浇一次）。
     */
    private static final String FLAG_LOVETEAM_WATER = "Forest::loveteamWater";

    /**
     * 组队合种当日已浇量（克）的持久化 key，**不能改名**。
     */
    private static final String FLAG_TEAM_WATER_DAILY_COUNT = "FLAG_TEAM_WATER_DAILY_COUNT";

    /**
     * 新版保护地任务：只有洪山动物园区域下发了任务场景，其余区域没有任务列表。
     */
    private static final String MONOPOLY_REGION_HSDWY = "hongshandongwuyuan";
    private static final String MONOPOLY_TASK_SCENE_HSDWY = "ANTFOREST_MONOPOLY_TASK_HSDWY";

    /**
     * 新版保护地任务的浏览时长上限（秒）：服务端下发 timeCount 后要等这么久才能提交完成，
     * 超出上限视为异常数据，不阻塞本轮任务。
     */
    private static final long MONOPOLY_MAX_TASK_SECONDS = 300L;

    private static final AverageMath offsetTimeMath = new AverageMath(5);

    private static final Map<String, Long> usingProps = new ConcurrentHashMap<>();

    private static final Map<String, String> dressMap;

    private static final Set<String> AntForestTaskTypeSet;

    static {
        dressMap = new HashMap<>();
        // position To positionType
        dressMap.put("tree__main", "treeMain");
        dressMap.put("bg__sky_0", "bgSky0");
        dressMap.put("bg__sky_cloud", "bgSkyCloud");
        dressMap.put("bg__ground_a", "bgGroundA");
        dressMap.put("bg__ground_b", "bgGroundB");
        dressMap.put("bg__ground_c", "bgGroundC");
        // positionType To position
        dressMap.put("treeMain", "tree__main");
        dressMap.put("bgSky0", "bg__sky_0");
        dressMap.put("bgSkyCloud", "bg__sky_cloud");
        dressMap.put("bgGroundA", "bg__ground_a");
        dressMap.put("bgGroundB", "bg__ground_b");
        dressMap.put("bgGroundC", "bg__ground_c");

        AntForestTaskTypeSet = new HashSet<>();
        AntForestTaskTypeSet.add("VITALITYQIANDAOPUSH"); //
        AntForestTaskTypeSet.add("ONE_CLICK_WATERING_V1"); // 给随机好友一键浇水
        AntForestTaskTypeSet.add("GYG_YUEDU_2"); // 去森林图书馆逛15s
        AntForestTaskTypeSet.add("GYG_TBRS"); // 逛一逛淘宝人生
        AntForestTaskTypeSet.add("TAOBAO_tab2_2023"); // 去淘宝看科普视频
        AntForestTaskTypeSet.add("GYG_diantao"); // 逛一逛点淘得红包
        AntForestTaskTypeSet.add("GYG-taote"); // 逛一逛淘宝特价版
        AntForestTaskTypeSet.add("NONGCHANG_20230818"); // 逛一逛淘宝芭芭农场
        // AntForestTaskTypeSet.add("GYG_haoyangmao_20240103");//逛一逛淘宝薅羊毛
        // AntForestTaskTypeSet.add("YAOYIYAO_0815");//去淘宝摇一摇领奖励
        // AntForestTaskTypeSet.add("GYG-TAOCAICAI");//逛一逛淘宝买菜
    }

    private final AtomicInteger taskCount = new AtomicInteger(0);

    private String selfId;

    private Integer tryCountInt;

    private Integer retryIntervalInt;

    private Integer advanceTimeInt;

    private Integer checkIntervalInt;

    private FixedOrRangeIntervalLimit collectIntervalEntity;

    private FixedOrRangeIntervalLimit doubleCollectIntervalEntity;

    private final AverageMath delayTimeMath = new AverageMath(5);

    private final ObjReference<Long> collectEnergyLockLimit = new ObjReference<>(0L);

    private final Object usePropLockObj = new Object();

    private BooleanModelField collectEnergy;
    private BooleanModelField expiredEnergy;
    private BooleanModelField energyRain;
    private IntegerModelField advanceTime;
    private IntegerModelField tryCount;
    private IntegerModelField retryInterval;
    private SelectModelField dontCollectList;
    private BooleanModelField collectWhiteListMode;
    private BooleanModelField onlyCollectRevivedSelfEnergy;
    private IntegerModelField revivedSelfOrdinaryMaxEnergy;
    private SelectModelField collectWhiteList;

    private BooleanModelField drawGameCenterAward;
    private BooleanModelField readForest;
    private ChoiceModelField CollectSelfEnergyType;

    private IntegerModelField CollectSelfEnergyThreshold;
    private IntegerModelField collectRobExpandEnergy;
    private BooleanModelField collectRobExpandEnergyEnable;
    private BooleanModelField collectWateringBubble;
    private BooleanModelField batchRobEnergy;
    private BooleanModelField balanceNetworkDelay;
    //PK能量
    private BooleanModelField pkEnergy;
    private ChoiceModelField whackModeName;
    private IntegerModelField whackModeGames;
    private IntegerModelField whackModeCount;
    private IntegerModelField earliestwhackMoleTime;

    // 定义运行模式名称数组（需提前声明，与原 Kotlin 中的 whackMoleModeNames 对应）

    private BooleanModelField collectProp;
    private StringModelField queryInterval;
    private StringModelField collectInterval;
    private StringModelField doubleCollectInterval;
    private ChoiceModelField doubleClickType;
    private ListModelField.ListJoinCommaToStringModelField doubleCardTime;
    @Getter
    private IntegerModelField doubleCountLimit;
    private IntegerModelField CollectBombEnergyLimit;
    private BooleanModelField findEnergyCollect;
    private BooleanModelField enableCycleTakeLook;
    private StringModelField cycleTakeLookTime;
    private IntegerModelField cycleTakeLookInterval;
    private BooleanModelField enableCycleRankScan;
    private StringModelField cycleRankScanTime;
    private IntegerModelField cycleRankScanInterval;
    private final String[] forestCycleIds = new String[2];
    private BooleanModelField useEnergyRainLimit;
    private BooleanModelField doubleCardConstant;
    private ChoiceModelField helpFriendCollectType;
    private SelectModelField helpFriendCollectList;

    private IntegerModelField helpFriendCollectListLimit;
    private BooleanModelField returnWater;
    private IntegerModelField returnWater33;
    private IntegerModelField returnWater18;
    private IntegerModelField returnWater10;
    private BooleanModelField receiveForestTaskAward;
    private BooleanModelField energySceneTask;

    private BooleanModelField AutoAntForestVitalityTaskList;
    private SelectModelField AntForestVitalityTaskList;
    private ChoiceModelField waterFriendType;
    private SelectAndCountModelField waterFriendGramList;
    private BooleanModelField waterFriendEnergySendChat;

    private BooleanModelField waterFriendEnergyFirst;
    private SelectAndCountModelField waterFriendList;

    private SelectAndCountModelField wateredFriendList;

    private BooleanModelField doubleWaterFriendEnergy;
    private SelectModelField giveEnergyRainList;
    private BooleanModelField vitalityExchangeBenefit;
    private SelectAndCountModelField vitality_ExchangeBenefitList;
    private BooleanModelField userPatrol;
    private BooleanModelField monopolyPatrol;
    private BooleanModelField monopolyTasks;
    private BooleanModelField monopolyAnimalEnergy;
    private BooleanModelField monopolyDispatchAnimal;
    private ChoiceModelField monopolyDispatchPriority;
    private BooleanModelField monopolyExchangeCertificate;
    private BooleanModelField waterMemberPlant;
    private BooleanModelField AutoMonopolyTaskList;
    private SelectModelField MonopolyTaskList;
    private BooleanModelField collectGiftBox;
    private BooleanModelField medicalHealth;
    private BooleanModelField greenLife;

    private BooleanModelField greenRent;
    private ChoiceModelField consumeAnimalPropType;
    private SelectModelField whoYouWantToGiveTo;
    private BooleanModelField ecoLife;
    private BooleanModelField youthPrivilege;
    private SelectModelField ecoLifeOptions;
    private BooleanModelField dress;
    private TextModelField dressDetailList;

    private static int totalCollected = 0;
    private static int totalHelpCollected = 0;
    private static boolean hasErrorWait = false;

    @Getter
    private Set<String> dontCollectMap = new HashSet<>();

    @Override
    public String getName() {
        return "森林";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    private BooleanModelField loveteamWater;
    private IntegerModelField loveteamWaterNum;

    private BooleanModelField partnerteamWater;
    private IntegerModelField partnerteamWaterNum;
    private BooleanModelField ForestHunt;
    private BooleanModelField AutoAntForestHuntTaskList;
    private SelectModelField AntForestHuntTaskList;
    private BooleanModelField ForestHuntDraw;
    private BooleanModelField ForestHuntHelp;
    private SelectModelField ForestHuntHelpList;

    private SelectModelField continuousUseCardOptions;
    private IntegerModelField robExpandCardReplaceRemainDays;
    private IntegerModelField robExpandCardForceReplaceExpireDays;
    private BooleanModelField autoMakeUpSign;
    private ChoiceModelField bubbleBoostCard;
    private IntegerModelField bubbleBoostDailyLimit;
    private StringModelField bubbleBoostTime;
    private String bubbleBoostCheckId;
    private BooleanModelField smartDoubleCard;
    private IntegerModelField smartDoubleCardThreshold;
    private IntegerModelField smartDoubleCardDailyLimit;
    private BooleanModelField smartDoublePermanent, smartDouble31Days, smartDoubleRenew31;
    private BooleanModelField forestPropRefill;
    private IntegerModelField forestPropRefillBudget;
    private BooleanModelField expiringForestProps;
    private SelectModelField expiringForestPropTypes;
    private IntegerModelField expiringForestPropHours, expiringForestPropDailyLimit;
    private String smartDoubleCheckId;

    private BooleanModelField autoUseShieldCard;
    private IntegerModelField continuousUseShieldHour;
    private BooleanModelField NORMALForestHuntHelp;
    private BooleanModelField ACTIVITYForestHuntHelp;

    private BooleanModelField energyPvp;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(collectEnergy = new BooleanModelField("collectEnergy", "收集能量", false));
        modelFields.addField(findEnergyCollect = new BooleanModelField("findEnergyCollect", "找能量", false).setDependsOn("collectEnergy"));
        modelFields.addField(enableCycleTakeLook = new BooleanModelField("enableCycleTakeLook", "周期找能量 | 开关", false).setDependsOn("collectEnergy"));
        modelFields.addField(cycleTakeLookTime = new StringModelField("cycleTakeLookTime", "周期找能量 | GMT+8时间窗口", "0700-0730")
                .setDescription("HHmm-HHmm，多个窗口逗号分隔，支持跨午夜；周期与主森林任务互斥。"));
        modelFields.addField(cycleTakeLookInterval = new IntegerModelField("cycleTakeLookInterval", "周期找能量 | 间隔秒数", 300, 30, 3600));
        modelFields.addField(enableCycleRankScan = new BooleanModelField("enableCycleRankScan", "周期全量扫榜 | 开关", false).setDependsOn("collectEnergy"));
        modelFields.addField(cycleRankScanTime = new StringModelField("cycleRankScanTime", "周期全量扫榜 | GMT+8时间窗口", "0700-0730")
                .setDescription("HHmm-HHmm，多个窗口逗号分隔，支持跨午夜；PK榜遵守原PK开关。"));
        modelFields.addField(cycleRankScanInterval = new IntegerModelField("cycleRankScanInterval", "周期全量扫榜 | 间隔分钟", 10, 5, 60));
        modelFields.addField(dontCollectList = new SelectModelField("dontCollectList", "不收取能量列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(collectWhiteListMode = new BooleanModelField("collectWhiteListMode", "好友收能量 | 只收白名单", false));
        modelFields.addField(onlyCollectRevivedSelfEnergy = new BooleanModelField("onlyCollectRevivedSelfEnergy", "自己能量 | 只收被复活能量", false)
                .setDescription("小号保留普通能量等待复活，不蹲点大球；GMT+8周一07:00至08:00暂停森林任务。"));
        modelFields.addField(revivedSelfOrdinaryMaxEnergy = new IntegerModelField("revivedSelfOrdinaryMaxEnergy", "自己能量 | 复活模式小球例外上限", 0, 0, 1000000)
                .setDescription("0不收普通球；正数允许收取/蹲点不超过该克数的小球，仍遵守原单球规则。"));
        modelFields.addField(collectWhiteList = new SelectModelField("collectWhiteList", "好友收能量 | 白名单", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(batchRobEnergy = new BooleanModelField("batchRobEnergy", "一键收取", false));
        modelFields.addField(CollectSelfEnergyType = new ChoiceModelField("CollectSelfEnergyType", "收单个能量球 | " + "方式", CollectSelfType.ALL, CollectSelfType.nickNames));
        modelFields.addField(CollectSelfEnergyThreshold = new IntegerModelField("CollectSelfEnergyThreshold", "收单个能量球阈值(0不限制)", 0, 0, 10000));
        modelFields.addField(pkEnergy = new BooleanModelField("pkEnergy", "Pk榜收取 | 开关", false));
        modelFields.addField(energyPvp = new BooleanModelField("energyPvp", "1V1能量挑战 | 开关", false));
        modelFields.addField(collectWateringBubble = new BooleanModelField("collectWateringBubble", "收取金球", false));
        modelFields.addField(wateredFriendList = new SelectAndCountModelField("wateredFriendList", "统计 | 应被好友浇水", new LinkedHashMap<>(), AlipayUser::getList, "请填写被浇水次数(用于核对金球)", 1, 3));
        modelFields.addField(collectRobExpandEnergyEnable = new BooleanModelField("collectRobExpandEnergyEnable", "倍卡额外能量", true));
        modelFields.addField(collectRobExpandEnergy = new IntegerModelField("collectRobExpandEnergy", "倍卡额外能量(大于该值收取)", 0, 0, 1000000).setDependsOn("collectRobExpandEnergyEnable"));
        modelFields.addField(expiredEnergy = new BooleanModelField("expiredEnergy", "收取过期能量", false));
        modelFields.addField(queryInterval = new StringModelField("queryInterval", "查询间隔(毫秒或毫秒范围)", "500-1000"));
        modelFields.addField(collectInterval = new StringModelField("collectInterval", "收取间隔" + "(毫秒或毫秒范围)", "1000" + "-1500"));
        modelFields.addField(doubleCollectInterval = new StringModelField("doubleCollectInterval", "双击间隔(毫秒或毫秒范围)", "50-150"));
        modelFields.addField(balanceNetworkDelay = new BooleanModelField("balanceNetworkDelay", "平衡网络延迟", true));
        modelFields.addField(advanceTime = new IntegerModelField("advanceTime", "提前时间(毫秒)", 0, 0, 500));
        modelFields.addField(tryCount = new IntegerModelField("tryCount", "尝试收取(次数)", 1, 0, 10));
        modelFields.addField(retryInterval = new IntegerModelField("retryInterval", "重试间隔(毫秒)", 1000, 0, 10000));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "森林乐园 | 游戏宝箱", true));
        modelFields.addField(readForest = new BooleanModelField("readForest", "无纸阅读", true));
        modelFields.addField(CollectBombEnergyLimit = new IntegerModelField("CollectBombEnergyLimit", "单个炸弹能量大于该值收取", 0, 0, 100000));
        modelFields.addField(continuousUseCardOptions = new SelectModelField("continuousUseCardOptions", "连续兑换使用道具卡片 | 选项", new LinkedHashSet<>(), CustomOption::getContinuousUseCardOptions));
        modelFields.addField(robExpandCardReplaceRemainDays = new IntegerModelField("robExpandCardReplaceRemainDays", "收好友N倍卡 | 高倍率替换剩余天数(0关闭)", 0, 0, 365));
        modelFields.addField(robExpandCardForceReplaceExpireDays = new IntegerModelField("robExpandCardForceReplaceExpireDays", "收好友N倍卡 | 临期强制替换天数(0关闭)", 0, 0, 365));
        modelFields.addField(autoMakeUpSign = new BooleanModelField("autoMakeUpSign", "连续收能量 | 自动补签", false).setDescription("使用现有补签卡，优先补最近30天最早漏签；不兑换补签卡"));
        modelFields.addField(bubbleBoostCard = new ChoiceModelField("bubbleBoostCard", "时光加速器 | 消耗类型", UsePropType.CLOSE, UsePropType.nickNames));
        modelFields.addField(bubbleBoostDailyLimit = new IntegerModelField("bubbleBoostDailyLimit", "时光加速器 | 每日尝试上限（0不用）", 1, 0, 100));
        modelFields.addField(bubbleBoostTime = new StringModelField("bubbleBoostTime", "时光加速器 | GMT+8时间点/禁止时段", "-1")
                .setDescription("-1沿用每轮尝试；HHmm时间点逗号分隔，可附加!HHmm-HHmm禁止窗口。默认只用库存；缺货补兑需独立开启及预算，每个时间点每天最多一次消费尝试。"));
        modelFields.addField(smartDoubleCard = new BooleanModelField("smartDoubleCard", "双击卡 | 按未来蹲点数量使用", false)
                .setDescription("需勾选连续道具中的双击卡；按未来约5分钟明确可双击的好友球数量触发。默认只用限时库存；永久/31天/续用及指定补兑需分别开启，补兑还需设置预算。"));
        modelFields.addField(smartDoubleCardThreshold = new IntegerModelField("smartDoubleCardThreshold", "双击卡 | 未来5分钟蹲点球阈值", 10, 1, 100).setDependsOn("smartDoubleCard"));
        modelFields.addField(smartDoubleCardDailyLimit = new IntegerModelField("smartDoubleCardDailyLimit", "双击卡 | 智能使用每日尝试上限（0不用）", 6, 0, 100).setDependsOn("smartDoubleCard"));
        modelFields.addField(smartDoublePermanent = new BooleanModelField("smartDoublePermanent", "双击卡 | 智能策略允许永久库存", false).setDependsOn("smartDoubleCard"));
        modelFields.addField(smartDouble31Days = new BooleanModelField("smartDouble31Days", "双击卡 | 智能策略允许31天库存", false).setDependsOn("smartDoubleCard"));
        modelFields.addField(smartDoubleRenew31 = new BooleanModelField("smartDoubleRenew31", "双击卡 | 31天卡允许确认续用", false).setDependsOn("smartDouble31Days"));
        modelFields.addField(forestPropRefill = new BooleanModelField("forestPropRefill", "森林道具 | 缺货时按勾选目录补兑", false));
        modelFields.addField(forestPropRefillBudget = new IntegerModelField("forestPropRefillBudget", "森林道具 | 补兑每日活力值预算（0不兑换）", 0, 0, 1000000).setDependsOn("forestPropRefill"));
        forestPropRefill.setDescription("沿用活力值兑换列表及商品次数；仅当前道具缺货且实时权益明确对应类型才兑换，最多一份/调用，库存、次数和活力值扣减回查。未知结果保留预算。");
        modelFields.addField(expiringForestProps = new BooleanModelField("expiringForestProps", "森林道具 | 集中使用临期库存", false));
        modelFields.addField(expiringForestPropTypes = new SelectModelField("expiringForestPropTypes", "临期道具 | 允许使用类别", new LinkedHashSet<>(), ForestExpiringProps::getOptions).setDependsOn("expiringForestProps"));
        modelFields.addField(expiringForestPropHours = new IntegerModelField("expiringForestPropHours", "临期道具 | 剩余有效小时", 24, 1, 24).setDependsOn("expiringForestProps"));
        modelFields.addField(expiringForestPropDailyLimit = new IntegerModelField("expiringForestPropDailyLimit", "临期道具 | 每日尝试上限（0不用）", 0, 0, 10).setDependsOn("expiringForestProps"));
        modelFields.addField(autoUseShieldCard = new BooleanModelField("autoUseShieldCard", "自动续用保护罩", false));
        modelFields.addField(continuousUseShieldHour = new IntegerModelField("continuousUseShieldHour", "自动续用保护罩(小时)", 24, 1, 168).setDependsOn("autoUseShieldCard"));
        //modelFields.addField(doubleClickType = new ChoiceModelField("doubleClickType", "双击卡 | " + "自动使用", UsePropType.CLOSE, UsePropType.nickNames));
        //modelFields.addField(doubleCountLimit = new IntegerModelField("doubleCountLimit", "双击卡 | " + "使用次数", 6));
        //modelFields.addField(doubleCardTime = new ListModelField.ListJoinCommaToStringModelField("doubleCardTime", "双击卡 | 使用时间(范围)", ListUtil.newArrayList("0700" + "-0730")));
        //modelFields.addField(doubleCardConstant = new BooleanModelField("DoubleCardConstant", "双击卡 | 限时双击永动机", false));
        modelFields.addField(returnWater = new BooleanModelField("returnWater", "返水 | 开启", false));
        modelFields.addField(returnWater10 = new IntegerModelField("returnWater10", "返水 | 10克需收能量(0不限)", 10).setDependsOn("returnWater"));
        modelFields.addField(returnWater18 = new IntegerModelField("returnWater18", "返水 | 18克需收能量(0不限)", 18).setDependsOn("returnWater"));
        modelFields.addField(returnWater33 = new IntegerModelField("returnWater33", "返水 | 33克需收能量(0不限)", 33).setDependsOn("returnWater"));
        modelFields.addField(waterFriendType = new ChoiceModelField("waterFriendType", "浇水 | 动作", WaterFriendType.WATER_00, WaterFriendType.nickNames));
        modelFields.addField(waterFriendList = new SelectAndCountModelField("waterFriendList", "浇水 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写浇水次数(每日)", 1, 3));
        modelFields.addField(waterFriendGramList = new SelectAndCountModelField("waterFriendGramList", "浇水 | 好友独立克数", new LinkedHashMap<>(), AlipayUser::getList, "只填10、18、33、66；未设置或0沿用浇水动作；仍需开启浇水并在好友列表设置次数", 0, 66));
        modelFields.addField(waterFriendEnergySendChat = new BooleanModelField("waterFriendEnergySendChat", "浇水 | 发送已浇水提醒", false));
        modelFields.addField(waterFriendEnergyFirst = new BooleanModelField("waterFriendEnergyFirst", "浇水 | 每次首先执行", false));
        modelFields.addField(doubleWaterFriendEnergy = new BooleanModelField("doubleWaterFriendEnergy", "浇水 | 强制检查重复一次", false));
        modelFields.addField(helpFriendCollectType = new ChoiceModelField("helpFriendCollectType", "复活能量 | 动作", HelpFriendCollectType.NONE, HelpFriendCollectType.nickNames));
        modelFields.addField(helpFriendCollectList = new SelectModelField("helpFriendCollectList", "复活能量 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(helpFriendCollectListLimit = new IntegerModelField("helpFriendCollectListLimit", "复活好友能量下限(大于该值复活,0不限制)", 0, 0, 100000).setDependsOn("helpFriendCollectType"));
        modelFields.addField(vitalityExchangeBenefit = new BooleanModelField("vitalityExchangeBenefit", "活力值 | 兑换权益", false));
        modelFields.addField(vitality_ExchangeBenefitList = new SelectAndCountModelField("vitality_ExchangeBenefitList", "活力值 | 权益列表", new LinkedHashMap<>(), VitalityBenefit::getList, "请填写兑换次数(每日)", 1, 100).setDependsOn("vitalityExchangeBenefit"));
        modelFields.addField(whackModeName = new ChoiceModelField("whackModeName", "6秒拼手速 | 运行模式", whackModeNames.CLOSE, whackModeNames.nickNames));
        modelFields.addField(whackModeGames = new IntegerModelField("whackModeGames", "6秒拼手速 | 激进模式局数", 5).setDependsOn("whackModeName"));
        modelFields.addField(whackModeCount = new IntegerModelField("whackModeCount", "6秒拼手速 | 兼容模式击打数", 15).setDependsOn("whackModeName"));
        modelFields.addField(earliestwhackMoleTime = new IntegerModelField("earliestwhackMoleTime", "6秒拼手速 | 最早执行(24小时制)", 8, 0, 23).setDependsOn("whackModeName"));
        modelFields.addField(collectProp = new BooleanModelField("collectProp", "收集道具", false));
        modelFields.addField(whoYouWantToGiveTo = new SelectModelField("whoYouWantToGiveTo", "赠送道具好友列表", new LinkedHashSet<>(), AlipayUser::getList, "会赠送所有可送道具都给已选择的好友"));
        modelFields.addField(energyRain = new BooleanModelField("energyRain", "收集能量雨", false));
        modelFields.addField(giveEnergyRainList = new SelectModelField("giveEnergyRainList", "赠送能量雨好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(useEnergyRainLimit = new BooleanModelField("useEnergyRainLimit", "兑换使用限时能量雨卡", false));
        modelFields.addField(userPatrol = new BooleanModelField("userPatrol", "旧版保护地巡护", false));
        modelFields.addField(monopolyPatrol = new BooleanModelField("monopolyPatrol", "新版保护地 | 自动前进", false));
        modelFields.addField(monopolyExchangeCertificate = new BooleanModelField("monopolyExchangeCertificate", "新版保护地 | 自动兑换保护证书", false).setDescription("仅兑换当前地图尚未持有的证书，可能消耗森林能量"));
        modelFields.addField(waterMemberPlant = new BooleanModelField("waterMemberPlant", "会员绿植 | 自动浇水", false).setDescription("使用活动水滴浇水，回查进度；未开通活动时跳过"));
        modelFields.addField(monopolyTasks = new BooleanModelField("monopolyTasks", "新版保护地 | 自动任务", false));
        modelFields.addField(monopolyAnimalEnergy = new BooleanModelField("monopolyAnimalEnergy", "新版动物伙伴 | 领取能量", false));
        modelFields.addField(monopolyDispatchAnimal = new BooleanModelField("monopolyDispatchAnimal", "新版动物伙伴 | 自动派遣", false));
        modelFields.addField(monopolyDispatchPriority = new ChoiceModelField("monopolyDispatchPriority", "新版动物伙伴 | 派遣优先级", 0, new String[]{"新版优先", "旧版优先"}).setDependsOn("monopolyDispatchAnimal"));
        modelFields.addField(AutoMonopolyTaskList = new BooleanModelField("AutoMonopolyTaskList", "新版保护地 | 自动黑名单", true).setDependsOn("monopolyTasks"));
        modelFields.addField(MonopolyTaskList = new SelectModelField("MonopolyTaskList", "新版保护地 | 黑名单列表", new LinkedHashSet<>(), AlipayMonopolyTaskList::getList).setDependsOn("AutoMonopolyTaskList"));
        modelFields.addField(consumeAnimalPropType = new ChoiceModelField("consumeAnimalPropType", "派遣动物伙伴", ConsumeAnimalPropType.NONE, ConsumeAnimalPropType.nickNames));
        modelFields.addField(receiveForestTaskAward = new BooleanModelField("receiveForestTaskAward", "森林任务", false));
        modelFields.addField(energySceneTask = new BooleanModelField("energySceneTask", "种树攻略 | 场景任务", false)
                .setDependsOn("receiveForestTaskAward")
                .setDescription("逐个尝试完成场景卡下的行为子任务"));
        modelFields.addField(AutoAntForestVitalityTaskList = new BooleanModelField("AutoAntForestVitalityTaskList", "活力值 | 自动黑名单", true));
        modelFields.addField(AntForestVitalityTaskList = new SelectModelField("AntForestVitalityTaskList", "活力值 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestVitalityTaskList::getList).setDependsOn("AutoAntForestVitalityTaskList"));
        modelFields.addField(collectGiftBox = new BooleanModelField("collectGiftBox", "领取礼盒", false));
        modelFields.addField(medicalHealth = new BooleanModelField("medicalHealth", "医疗健康", false));
        modelFields.addField(greenLife = new BooleanModelField("greenLife", "森林集市", false));
        modelFields.addField(greenRent = new BooleanModelField("greenRent", "绿色租赁", false));
        modelFields.addField(youthPrivilege = new BooleanModelField("youthPrivilege", "青春特权 | 森林道具", false));
        modelFields.addField(ecoLife = new BooleanModelField("ecoLife", "绿色行动 | 开启", false));
        modelFields.addField(ecoLifeOptions = new SelectModelField("ecoLifeOptions", "绿色行动 | 选项", new LinkedHashSet<>(), CustomOption::getEcoLifeOptions, "光盘行动需要先手动完成一次").setDependsOn("ecoLife"));
        modelFields.addField(partnerteamWater = new BooleanModelField("partnerteamWater", "组队合种浇水", false));
        modelFields.addField(partnerteamWaterNum = new IntegerModelField("partnerteamWaterNum", "组队合种浇水" + "(g)", 10, 10, 5000).setDependsOn("partnerteamWater"));
        modelFields.addField(loveteamWater = new BooleanModelField("loveteamWater", "真爱合种浇水", false));
        modelFields.addField(loveteamWaterNum = new IntegerModelField("loveteamWaterNum", "真爱合种浇水" + "(g)", 20, 20, 10000).setDependsOn("loveteamWater"));
        modelFields.addField(ForestHunt = new BooleanModelField("ForestHunt", "森林寻宝", false));
        modelFields.addField(AutoAntForestHuntTaskList = new BooleanModelField("AutoAntForestHuntTaskList", "抽抽乐任务 | 自动黑名单", true).setDependsOn("ForestHunt"));
        modelFields.addField(AntForestHuntTaskList = new SelectModelField("AntForestHuntTaskList", "抽抽乐任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestHuntTaskList::getList).setDependsOn("AutoAntForestHuntTaskList"));
        modelFields.addField(ForestHuntDraw = new BooleanModelField("ForestHuntDraw", "森林寻宝抽奖", false).setDependsOn("ForestHunt"));
        modelFields.addField(ForestHuntHelp = new BooleanModelField("ForestHuntHelp", "森林寻宝助力", false).setDependsOn("ForestHunt"));
        modelFields.addField(NORMALForestHuntHelp = new BooleanModelField("NORMALForestHuntHelp", "普通场景强制助力" + "(助力任务不在列表中时使用，如果日志显示失效请关闭)", false).setDependsOn("ForestHunt"));
        modelFields.addField(ACTIVITYForestHuntHelp = new BooleanModelField("ACTIVITYForestHuntHelp", "活动场景强制助力" + "(同上)", false).setDependsOn("ForestHunt"));
        modelFields.addField(ForestHuntHelpList = new SelectModelField("ForestHuntHelpList", "点击配置寻宝助力列表" + "(填写shareId中开头的22-24位字符在\"4O7FEYDgn\"前的)", new LinkedHashSet<>(), AlipayForestHunt::getList).setDependsOn("ForestHunt"));
        modelFields.addField(dress = new BooleanModelField("dress", "装扮保护 | 开启", false));
        modelFields.addField(dressDetailList = new TextModelField("dressDetailList", "装扮保护 | " + "装扮信息", "").setDependsOn("dress"));
        modelFields.addField(new EmptyModelField("dressDetailListClear", "装扮保护 | 装扮信息清除", () -> dressDetailList.reset()).setDependsOn("dress"));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime) > System.currentTimeMillis()) {
            Log.record("异常等待中，暂不执行检测！");
            return false;
        }
        return true;
    }

    @Override
    public Boolean isSync() {
        return true;
    }

    @Override
    public void boot(ClassLoader classLoader) {
        super.boot(classLoader);
        FixedOrRangeIntervalLimit queryIntervalLimit = new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryHomePage", queryIntervalLimit);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage", queryIntervalLimit);
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.collectEnergy", 0);
        RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.queryEnergyRanking", 100);
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.fillUserRobFlag", 500);
        tryCountInt = tryCount.getValue();
        retryIntervalInt = retryInterval.getValue();
        advanceTimeInt = advanceTime.getValue();
        checkIntervalInt = BaseModel.getCheckInterval().getValue();
        dontCollectMap = dontCollectList.getValue();
        collectIntervalEntity = new FixedOrRangeIntervalLimit(collectInterval.getValue(), 50, 10000);
        doubleCollectIntervalEntity = new FixedOrRangeIntervalLimit(doubleCollectInterval.getValue(), 10, 5000);
        delayTimeMath.clear();
        AntForestRpcCall.init();
    }

    @Override
    public void run() {
        try {
            taskCount.set(0);
            selfId = UserIdMap.getCurrentUid();
            hasErrorWait = false;
            if (isRevivedSelfQuietTime()) {
                Log.record("小号复活模式：GMT+8周一07:00至08:00暂停森林任务");
                return;
            }

            // 组队合种浇水异常中断后，把账号从组队模式恢复回个人模式
            fixTeamModeIfNeeded();

            //GameTask.Orchard_ncscc.report("农场上车车", 1);
            if (waterFriendEnergyFirst.getValue()) {
                waterFriendEnergy();
            }


            if (useEnergyRainLimit.getValue()) {
                useEnergyRainCard();
            }

            if (energyRain.getValue()) {
                energyRain();
            }

            if (ecoLife.getValue()) {
                ecoLife();
            }

            if (youthPrivilege.getValue()) {
                Privilege.youthPrivilege();
                //Privilege.studentSignInRedEnvelope();
            }
            //连续兑换使用道具卡片
            synchronized (usePropLockObj) {
                if (!TaskCommon.IS_ENERGY_TIME && !onlyCollectRevivedSelfEnergy.getValue()) {
                    int used = ForestExpiringProps.consume(expiringForestProps.getValue(), expiringForestPropTypes.getValue(),
                            expiringForestPropHours.getValue(), expiringForestPropDailyLimit.getValue());
                    if (used > 0) {
                        JSONObject refreshed = forestSignPayload(querySelfHome());
                        JSONArray bubbles = refreshed == null ? null : refreshed.optJSONArray("bubbles");
                        if (expiringForestPropTypes.getValue().contains("boost")) {
                            for (int i = 0; bubbles != null && i < bubbles.length(); i++) {
                                JSONObject bubble = bubbles.optJSONObject(i);
                                long id = bubble == null ? -1 : forestFeatureLong(bubble, "id");
                                if (id > 0) removeChildTask(getBubbleTimerTid(selfId, id));
                            }
                        }
                    }
                }
            }
            continuousUseCardOptions();

            if (receiveForestTaskAward.getValue()) {
                vantiepSign();
            }

            JSONObject selfHomeObject = collectSelfEnergy();
            JSONObject boostedHome = useBubbleBoostCard();
            if (boostedHome != null) selfHomeObject = boostedHome;
            if (!TaskCommon.IS_ENERGY_TIME && autoMakeUpSign.getValue()) autoMakeUpSign();
            try {
                scanForestRankings();
                selfHomeObject = collectSelfEnergy();
            } catch (Throwable t) {
                if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
                Log.err(TAG, "queryEnergyRanking err:", t);
            }

            if (findEnergyCollect.getValue() && collectEnergy.getValue()) {
                findAndCollectEnergy();
            }

            useSmartDoubleCard();
            scheduleSmartDoubleCheck();

            if (!TaskCommon.IS_ENERGY_TIME && selfHomeObject != null) {
                String whackMoleStatus = selfHomeObject.optString("whackMoleStatus");
                if (Objects.equals("CAN_PLAY", whackMoleStatus) || Objects.equals("CAN_INITIATIVE_PLAY", whackMoleStatus) || Objects.equals("NEED_MORE_FRIENDS", whackMoleStatus)) {
                    checkAndHandleWhackMole();
                }
                boolean hasMore = false;
                do {
                    if (hasMore) {
                        hasMore = false;
                        selfHomeObject = querySelfHome();
                    }
                    if (collectWateringBubble.getValue() || onlyCollectRevivedSelfEnergy.getValue() && collectEnergy.getValue()) {
                        JSONArray wateringBubbles = selfHomeObject.optJSONArray("wateringBubbles");
                        if (wateringBubbles == null) {
                            wateringBubbles = new JSONArray();
                        }
                        if (wateringBubbles.length() > 0) {
                            int collected = 0;
                            for (int i = 0; i < wateringBubbles.length(); i++) {
                                JSONObject wateringBubble = wateringBubbles.optJSONObject(i);
                                if (wateringBubble == null) {
                                    continue;
                                }
                                String bizType = wateringBubble.optString("bizType");
                                if (onlyCollectRevivedSelfEnergy.getValue() && !"fuhuo".equals(bizType)) continue;
                                String friendShowName = UserIdMap.getShowName(wateringBubble.optString("userId"));
                                switch (bizType) {
                                    case "jiaoshui": {
                                        // collectEnergy 请求失败/离线时可能返回 null，MyUtils.newJSONObject(null) 会抛异常
                                        // 被外层 catch 吞掉、中断本轮剩余金球收取，改用 MyUtils.newJSONObject 容错
                                        JSONObject joEnergy = MyUtils.newJSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.optLong("id")));
                                        if (MessageUtil.checkResultCode("收取[我]的浇水金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.optJSONArray("bubbles");
                                            if (bubbles != null) {
                                                for (int j = 0; j < bubbles.length(); j++) {
                                                    JSONObject b = bubbles.optJSONObject(j);
                                                    if (b != null) {
                                                        collected = b.optInt("collectedEnergy");
                                                    }
                                                }
                                            }

                                            if (collected > 0) {
                                                //记录被浇水次数
                                                Status.wateredFriendToday(wateringBubble.optString("userId"));
                                                Statistics.addData(Statistics.DataType.WATEREDCOUNT, 1);
                                                String msg = "收取金球🍯[" + friendShowName + "]的浇水[" + collected + "g]";
                                                Log.forest(msg + "");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.forest("收取[我]的浇水金球失败");
                                            }
                                        }
                                        break;
                                    }
                                    case "fuhuo": {
                                        JSONObject joEnergy = MyUtils.newJSONObject(AntForestRpcCall.collectRebornEnergy());
                                        if (MessageUtil.checkResultCode("收取[我]的复活金球", joEnergy)) {
                                            collected = joEnergy.optInt("energy");
                                            String msg = "收取金球🍯复活[" + collected + "g]";
                                            Log.forest(msg + "");
                                            Toast.show(msg);
                                            totalCollected += collected;
                                            Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                        }
                                        break;
                                    }
                                    case "baohuhuizeng": {
                                        JSONObject joEnergy = MyUtils.newJSONObject(AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.optLong("id")));
                                        if (MessageUtil.checkResultCodeString("收取[" + friendShowName + "]的复活回赠金球", joEnergy)) {
                                            JSONArray bubbles = joEnergy.optJSONArray("bubbles");
                                            if (bubbles != null) {
                                                for (int j = 0; j < bubbles.length(); j++) {
                                                    JSONObject b = bubbles.optJSONObject(j);
                                                    if (b != null) {
                                                        collected = b.optInt("collectedEnergy");
                                                    }
                                                }
                                            }
                                            if (collected > 0) {
                                                String msg = "收取金球🍯[" + friendShowName + "]复活回赠[" + collected + "g]";
                                                Log.forest(msg + "");
                                                Toast.show(msg);
                                                totalCollected += collected;
                                                Statistics.addData(Statistics.DataType.COLLECTED, collected);
                                            } else {
                                                Log.forest("收取[" + friendShowName + "]的复活回赠金球失败");
                                            }
                                        }
                                        break;
                                    }
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (wateringBubbles.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                    if (collectProp.getValue()) {
                        JSONArray givenProps = selfHomeObject.optJSONArray("givenProps");
                        if (givenProps == null) {
                            givenProps = new JSONArray();
                        }
                        if (givenProps.length() > 0) {
                            for (int i = 0; i < givenProps.length(); i++) {
                                JSONObject jo = givenProps.optJSONObject(i);
                                if (jo == null) {
                                    continue;
                                }
                                String giveConfigId = jo.optString("giveConfigId");
                                String giveId = jo.optString("giveId");
                                JSONObject propConfig = jo.optJSONObject("propConfig");
                                String propName = propConfig != null ? propConfig.optString("propName") : "";
                                jo = MyUtils.newJSONObject(AntForestRpcCall.collectProp(giveConfigId, giveId));
                                if (MessageUtil.checkSuccess(TAG, jo)) {
                                    Log.forest("领取道具🎭[" + propName + "]");
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (givenProps.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                } while (hasMore);
                //JSONArray usingUserProps = selfHomeObject.has("usingUserProps") ? selfHomeObject.getJSONArray("usingUserProps") : new JSONArray();
                //JSONArray usingUserProps = selfHomeObject.has("usingUserPropsNew") ? selfHomeObject.getJSONArray("usingUserPropsNew") : new JSONArray();
                JSONArray usingUserProps = selfHomeObject.optJSONArray("usingUserPropsNew");
                if (usingUserProps == null) {
                    usingUserProps = selfHomeObject.optJSONArray("usingUserProps");
                }
                if (usingUserProps == null) {
                    usingUserProps = new JSONArray();
                }
                boolean canConsumeAnimalProp = true;
                if (usingUserProps.length() > 0) {
                    for (int i = 0; i < usingUserProps.length(); i++) {
                        JSONObject jo = usingUserProps.optJSONObject(i);
                        if (jo == null) {
                            continue;
                        }
                        if (!Objects.equals("animal", jo.optString("propGroup"))) {
                            continue;
                        } else {
                            canConsumeAnimalProp = false;
                        }
                        JSONObject extInfo = MyUtils.newJSONObject(jo.optString("extInfo"));
                        int energy = extInfo.optInt("energy", 0);
                        if (energy > 0 && !extInfo.optBoolean("isCollected")) {
                            String propId = jo.optString("propId");
                            String propType = jo.optString("propType");
                            String shortDay = extInfo.optString("shortDay");
                            JSONObject animalObj = extInfo.optJSONObject("animal");
                            String animalName = animalObj != null ? animalObj.optString("name") : "";
                            jo = MyUtils.newJSONObject(AntForestRpcCall.collectAnimalRobEnergy(propId, propType, shortDay));
                            if (MessageUtil.checkResultCode(TAG, jo)) {
                                Log.forest("动物能量🦩派遣" + animalName + "收取能量[" + energy + "g]");
                            }
                            TimeUtil.sleep(500);
                            break;
                        }
                    }
                }
                //强制重复浇水一次
                if (doubleWaterFriendEnergy.getValue()) {
                    if (!Status.hasFlagToday("Forest::doubleWaterFriendEnergy")) {
                        doubleWaterFriendEnergy();
                    }
                }

                waterFriendEnergy();

                if (pkEnergy.getValue()) {
                    collectPKEnergy();
                }

                //初始任务列表
                if (!Status.hasFlagToday("BlackList::initAntForest")) {
                    initAntForestTaskListMap(AutoAntForestVitalityTaskList.getValue(), AutoAntForestHuntTaskList.getValue(), receiveForestTaskAward.getValue(), ForestHunt.getValue());
                    Status.flagToday("BlackList::initAntForest");
                }
                // 新版保护地任务名单来自服务端下发：本地为空时每轮补一次，开了开关不必等到次日
                if (monopolyTasks.getValue() && MonopolyTaskListMap.getMap().isEmpty()) {
                    initMonopolyTaskListMap();
                }

                // 组队合种浇水
                if (partnerteamWater.getValue()) {
                    teamCooperateWater();
                }
                // 真爱合种浇水
                if (loveteamWater.getValue()) {
                    if (loveteamWaterNum.getValue() >= 20 && loveteamWaterNum.getValue() <= 10000) {
                        loveteam(loveteamWaterNum.getValue());
                    }
                }

                // 森林寻宝
                if (ForestHunt.getValue()) {
                    ForestChouChouLe forestChouChouLe = new ForestChouChouLe();
                    forestChouChouLe.chouChouLe(ForestHuntDraw.getValue(), ForestHuntHelp.getValue(), ForestHuntHelpList.getValue(), NORMALForestHuntHelp.getValue(), ACTIVITYForestHuntHelp.getValue(), AntForestHuntTaskList.getValue());
                }

                if (userPatrol.getValue()) {
                    queryUserPatrol();
                }
                if (monopolyPatrol.getValue()) {
                    monopolyPatrol();
                }
                if (monopolyAnimalEnergy.getValue()) {
                    collectMonopolyAnimalEnergy();
                }
                if (monopolyExchangeCertificate.getValue()) {
                    exchangeMonopolyCertificate();
                }
                if (waterMemberPlant.getValue()) {
                    waterMemberPlant();
                }
                // 动物伙伴自动派遣：新版受独立开关与优先级控制，旧版仍只看"派遣动物伙伴"选择
                Integer monopolyDispatchPriorityValue = monopolyDispatchPriority.getValue();
                boolean newAnimalFirst = monopolyDispatchPriorityValue == null || monopolyDispatchPriorityValue == 0;
                boolean animalDispatched = false;
                if (monopolyDispatchAnimal.getValue() && newAnimalFirst) {
                    animalDispatched = dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (consumeAnimalPropType.getValue() != ConsumeAnimalPropType.NONE) {
                    if (!canConsumeAnimalProp) {
                        Log.record("已经有动物伙伴在巡护森林");
                    } else if (!animalDispatched && !MyUtils.closeVerification()) {
                        animalDispatched = queryAnimalPropList();
                    }
                }
                if (monopolyDispatchAnimal.getValue() && !animalDispatched && !newAnimalFirst) {
                    dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (expiredEnergy.getValue()) {
                    popupTask();
                }

                if (receiveForestTaskAward.getValue()) {
                    queryCommonSign();
                    queryTaskList();
                }

                if (!MyUtils.closeVerification()) {
                    giveProp();
                }
                if (vitalityExchangeBenefit.getValue()) {
                    vitalityExchangeBenefit();
                }
                /* 森林集市 */
                if (greenLife.getValue()) {
                    greenLife();
                }

                // 绿色租赁
                if (greenRent.getValue()) {
                    if (!Status.hasFlagToday("Forest::greenRent")) {
                        greenRent();
                        Status.flagToday("Forest::greenRent");
                    }
                }

                if (medicalHealth.getValue()) {
                    // 医疗健康 绿色医疗 16g*6能量
                    queryForestEnergy("FEEDS");
                    // 医疗健康 电子小票 4g*10能量
                    queryForestEnergy("BILL");
                }
                if (dress.getValue()) {
                    dress();
                }

                checkAndHandleWhackMole();

                //森林乐园
                if (drawGameCenterAward.getValue()) {
                    doforestgame();
                    queryOptionalPlay();
                }

                //1V1能量挑战
                if (energyPvp.getValue()) {
                    if (updateUserConfigEnergyPvp()) {
                        queryPvpHomeInfo();
                        receivePvpRewards();
                    }

                }
                ForestEnergyInfo();
                if (readForest.getValue()) {
                    ReadForestTask.execute();
                } else {
                    Log.record("无纸阅读：跳过，配置开关已关闭");
                }

            } else if (readForest.getValue()) {
                Log.record("无纸阅读：跳过，" + (TaskCommon.IS_ENERGY_TIME ? "当前为只收能量时段" : "未取得森林首页数据"));
            }
        } catch (Throwable t) {
            if (t instanceof io.github.aw1y2z.sesame.util.TaskCancelledException) throw (io.github.aw1y2z.sesame.util.TaskCancelledException) t;
            Log.err(TAG, "AntForestV2.run err:", t);
        } finally {
            scheduleForestCycles(0, 0);
            scheduleForestCycles(1, 0);
            scheduleBubbleBoost(0);
            try {
                synchronized (AntForestV2.this) {
                    int count = taskCount.get();
                    if (count > 0) {
                        AntForestV2.this.wait(TimeUnit.MINUTES.toMillis(30));
                        count = taskCount.get();
                    }
                    if (count > 0) {
                        Log.record("执行超时-蚂蚁森林");
                    } else if (count != 0) {
                        Log.record("执行完成-蚂蚁森林");
                    }
                }
            } catch (InterruptedException ie) {
                Log.i(TAG, "执行中断-蚂蚁森林");
            }
            Statistics.save();
            FriendWatch.save();
        }
    }

    private void ForestEnergyInfo() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray bubbles = jo.optJSONArray("bubbles");
            int bubblesNumber = bubbles != null ? bubbles.length() : 0;

            JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
            if (userBaseInfo == null) {
                return;
            }
            int currentEnergy = userBaseInfo.optInt("currentEnergy", 0);
            int totalCertCount = userBaseInfo.optInt("totalCertCount", 0);
            JSONObject userVitalityInfo = jo.optJSONObject("userVitalityInfo");
            if (userVitalityInfo == null) {
                return;
            }
            int totalVitalityAmount = userVitalityInfo.optInt("totalVitalityAmount", 0);

            jo = MyUtils.newJSONObject(AntForestRpcCall.queryDynamicsIndex());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject todayEnergySummary = jo.optJSONObject("todayEnergySummary");
            if (todayEnergySummary == null) {
                return;
            }
            int obtainTotal = todayEnergySummary.optInt("obtainTotal", 0);
            int robbedTotal = todayEnergySummary.optInt("robbedTotal", 0);

            //获取能量日榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int dayenergySummation = myself.optInt("energySummation", 0);
            int dayrank = myself.optInt("rank", 0);

            //获取能量周榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int weekenergySummation = myself.optInt("energySummation", 0);
            int weekrank = myself.optInt("rank", 0);

            //获取能量总榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "total"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int totalenergySummation = myself.optInt("energySummation", 0);
            int totalrank = myself.optInt("rank", 0);

            //获取偷我日榜top
            String dayenergySummationtop3 = "偷我日榜top3:";
            String userId;
            int energySummation;
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray friendRankings = jo.optJSONArray("friendRanking");
            //friendRankings.length()
            for (int i = 0; friendRankings != null && i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.optJSONObject(i);
                if (friendRanking == null) {
                    continue;
                }
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                dayenergySummationtop3 = dayenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }

            //获取偷我周榜top
            String weekenergySummationtop3 = "偷我周榜top3:";
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            friendRankings = jo.optJSONArray("friendRanking");
            //friendRankings.length()
            for (int i = 0; friendRankings != null && i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.optJSONObject(i);
                if (friendRanking == null) {
                    continue;
                }
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                weekenergySummationtop3 = weekenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }
            String ForestInfo = "森林榜单🌳收取" + obtainTotal + "g;被收" + robbedTotal + "g;能量球" + bubblesNumber + "个;活力值" + totalVitalityAmount + ";当前能量" + currentEnergy + "g;证书" + totalCertCount + ";😡" + dayenergySummationtop3 + weekenergySummationtop3 + "😁日榜第" + dayrank + "名:" + dayenergySummation + "g;周榜第" + weekrank + "名:" + weekenergySummation + "g;总榜第" + totalrank + "名:" + totalenergySummation + "g;";
            //Toast.show(ForestInfo);
            //Log.forest("");
            Log.record(ForestInfo);
            //Log.forest("");

        } catch (Throwable th) {
            Log.err(TAG, "ForestEnergyInfo err:", th);
        }

    }

    private void collectPKEnergy() {
        try {
            JSONObject pkObject = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyChallengeRanking());
            if (!MessageUtil.checkResultCode(TAG + "获取PK排行榜失败:", pkObject)) {
                Log.error("获取PK排行榜失败: " + RpcRequestGuard.errorMessage(pkObject));
            } else {
                if (!"JOIN".equals(pkObject.optString("rankMemberStatus"))) {
                    Log.record("未加入PK排行榜");
                    return;
                }
                collectFriendsEnergy(pkObject, "PK");
                //继续处理靠后的PK好友
                JSONArray totalData = pkObject.optJSONArray("totalData");
                if (totalData == null || totalData.length() == 0) {
                    Log.record("PK好友排行榜为空，跳过");
                    return;
                }
                List<String> pkIdList = new ArrayList<>();
                for (int pos = 20; pos < totalData.length(); pos++) {
                    JSONObject pkFriend = totalData.optJSONObject(pos);
                    if (pkFriend == null) {
                        continue;
                    }
                    String userId = pkFriend.optString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue; //如果是自己则跳过
                    }
                    pkIdList.add(userId);
                    if (pkIdList.size() == 20) {
                        collectFriendsEnergy(pkIdList, "PK");
                        pkIdList.clear();
                    }
                }
                if (!pkIdList.isEmpty()) {
                    collectFriendsEnergy(pkIdList, "PK");
                }
                Log.forest("收取PK能量完成！");
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
    }

    private void notifyMain() {
        if (taskCount.decrementAndGet() < 1) {
            synchronized (AntForestV2.this) {
                AntForestV2.this.notifyAll();
            }
        }
    }

    private JSONObject querySelfHome() {
        if (isRevivedSelfQuietTime()) return null;
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.optLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            Log.i("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
            //兼容组队模式
            if (isTeam(userHomeObject)) {
                JSONObject teamHomeResult = userHomeObject.optJSONObject("teamHomeResult");
                JSONObject mainMember = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
                //取出组队模式下的selfHomeObject
                if (mainMember != null) {
                    Iterator<String> keyIterator = mainMember.keys();
                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        Object value = mainMember.opt(key);
                        //将道具卡详情存为一般森林主页格式，以便统一解析
                        if (key.equals("usingUserProps")) {
                            key = "usingUserPropsNew";
                        }
                        // 核心方法：put()
                        // 效果：存在该 key 则覆盖原值，不存在则新增键值对
                        userHomeObject.put(key, value);
                    }
                }
                //userHomeObject = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
            }
        } catch (Throwable t) {
            if (t instanceof io.github.aw1y2z.sesame.util.TaskCancelledException) throw (io.github.aw1y2z.sesame.util.TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    private JSONObject queryFriendHome(String userId) {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = MyUtils.newJSONObject(AntForestRpcCall.queryFriendHomePage(userId));
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.optLong("now");
            int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            Log.i("服务器时间：" + serverTime + "，本地与服务器时间差：" + offsetTime);
        } catch (Throwable t) {
            if (t instanceof io.github.aw1y2z.sesame.util.TaskCancelledException) throw (io.github.aw1y2z.sesame.util.TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    /** 指定道具（shield 能量罩 / energyBombCard 炸弹卡）是否仍在生效；serverTime 取好友主页的 now，缺失时退回本机时间 */
    private static boolean hasActiveProp(JSONObject userHomeObject, String propGroup) {
        long serverTime = userHomeObject.optLong("now");
        if (serverTime <= 0) {
            serverTime = System.currentTimeMillis();
        }
        JSONArray props = userHomeObject.optJSONArray("usingUserPropsNew");
        for (int i = 0; props != null && i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop != null && propGroup.equals(prop.optString("propGroup")) && prop.optLong("endTime") > serverTime) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找能量：反复请求服务端推荐的好友，进主页交给 collectUserEnergy 收取。
     * 对照 AG AntForest.collectEnergyByTakeLook 与 GR 朋友版 findAndCollectEnergy；
     * 先查询推荐预曝光，扫描结束后上报并领取服务端提示的已完成奖励。
     */
    private void findAndCollectEnergy() {
        boolean started = false, cancelled = false;
        try {
            Log.record("找能量：开始");
            JSONObject skipUsers = MyUtils.newJSONObject();
            for (String userId : dontCollectMap) {
                skipUsers.put(userId, "baohuzhao");
            }
            String exposed = findEnergyExposedFriend(skipUsers);
            Set<String> visited = new HashSet<>();
            int repeat = 0;
            int browsed = 0;
            for (int i = 0; i < 50 && !hasErrorWait; i++) {
                TimeUtil.sleep(0);
                started = true;
                JSONObject result = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLook(skipUsers, i == 0, exposed)));
                if (result == null) {
                    break;
                }
                String friendId = result.opt("friendId") instanceof String ? result.optString("friendId") : "";
                String actionType = result.optString("actionType");
                boolean ended = result.optBoolean("takeLookEnd");
                if (friendId.isEmpty() || (!actionType.isEmpty() && !"FRIEND".equals(actionType))) {
                    break;
                }
                if (Objects.equals(friendId, selfId) || !visited.add(friendId)) {
                    if (ended || ++repeat >= 3) {
                        break;
                    }
                    TimeUtil.sleep(300);
                    continue;
                }
                if (!allowCollectByWhiteList(friendId)) {
                    skipUsers.put(friendId, "baohuzhao");
                    if (ended) break;
                    TimeUtil.sleep(300);
                    continue;
                }
                JSONObject userHomeObject = queryFriendHome(friendId);
                // queryFriendHome 解析失败时返回空对象而非 null，按 length 判断才是真的打开了主页
                if (userHomeObject != null && userHomeObject.length() > 0) {
                    browsed++;
                    repeat = 0; // 只统计连续重复，成功浏览后清零（对照 AG consecutiveEmpty）
                    collectUserEnergy(friendId, userHomeObject, "ordinary");
                    // 能量罩/炸弹卡好友的能量可能没被摘走，不告诉服务端会被反复推荐（对照 AG hasShield||hasBomb）
                    if (hasActiveProp(userHomeObject, "shield") || hasActiveProp(userHomeObject, "energyBombCard")) {
                        skipUsers.put(friendId, "baohuzhao");
                    }
                }
                if (ended) {
                    break;
                }
                TimeUtil.sleep(500);
            }
            Log.record("找能量：完成，共浏览 " + browsed + " 个好友");
        } catch (TaskCancelledException e) {
            cancelled = true;
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            if (started && !cancelled && !hasErrorWait) finishFindEnergy();
        }
    }

    private void scanForestRankings() {
        TimeUtil.sleep(0);
        JSONObject friends = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRanking());
        if (!MessageUtil.checkResultCode(TAG, friends)) return;
        collectFriendsEnergy(friends, "ordinary");
        JSONArray rows = friends.optJSONArray("totalDatas");
        List<String> ids = new ArrayList<>();
        for (int i = 20; rows != null && i < rows.length(); i++) {
            TimeUtil.sleep(0);
            JSONObject friend = rows.optJSONObject(i);
            if (friend != null && friend.opt("userId") instanceof String && !friend.optString("userId").isEmpty()) ids.add(friend.optString("userId"));
            if ((i + 1) % 20 == 0) { collectFriendsEnergy(ids, "ordinary"); ids.clear(); }
        }
        if (!ids.isEmpty()) collectFriendsEnergy(ids, "ordinary");
    }

    private boolean forestCycleEnabled(int kind, String uid) {
        return isEnable() && collectEnergy.getValue() && !hasErrorWait && !isRevivedSelfQuietTime()
                && uid != null && !uid.isEmpty() && uid.equals(UserIdMap.getCurrentUid())
                && (kind == 0 ? enableCycleTakeLook.getValue() : enableCycleRankScan.getValue());
    }

    private synchronized void scheduleForestCycles(int kind, long earliest) {
        if (io.github.aw1y2z.sesame.util.RunGeneration.isStale()) return;
        String uid = UserIdMap.getCurrentUid();
        String oldId = forestCycleIds[kind];
        if (!forestCycleEnabled(kind, uid)) {
            if (oldId != null) removeChildTask(oldId);
            forestCycleIds[kind] = null;
            return;
        }
        long now = System.currentTimeMillis();
        String window = kind == 0 ? cycleTakeLookTime.getValue() : cycleRankScanTime.getValue();
        long at = ForestSchedule.nextWindow(window, Math.max(now + 1000, earliest));
        if (at < 0) {
            if (oldId != null) removeChildTask(oldId);
            forestCycleIds[kind] = null;
            Log.record("森林周期配置无效，本轮不排期");
            return;
        }
        ChildModelTask existing = oldId == null ? null : getChildTask(oldId);
        if (existing != null && Boolean.FALSE.equals(existing.getIsCancel())) return;
        long generation = taskGeneration();
        String id = "FOREST_CYCLE|" + kind + "|" + uid + "|" + System.nanoTime();
        if (addChildTask(new ChildModelTask(id, "antForest", () -> {
            synchronized (this) {
                if (!id.equals(forestCycleIds[kind])) return;
                forestCycleIds[kind] = null;
            }
            boolean cancelled = false;
            try {
                runExclusiveChild(generation, () -> {
                    TimeUtil.sleep(0);
                    if (!forestCycleEnabled(kind, uid) || !check()) return;
                    String currentWindow = kind == 0 ? cycleTakeLookTime.getValue() : cycleRankScanTime.getValue();
                    long started = System.currentTimeMillis();
                    if (ForestSchedule.nextWindow(currentWindow, started) != started) return;
                    selfId = uid;
                    if (kind == 0) findAndCollectEnergy();
                    else { if (pkEnergy.getValue()) collectPKEnergy(); scanForestRankings(); }
                    useSmartDoubleCard(); scheduleSmartDoubleCheck();
                });
            } catch (TaskCancelledException e) {
                cancelled = true;
                throw e;
            } catch (Exception e) {
                Log.record("森林周期执行失败，本轮停止：" + e.getClass().getSimpleName());
            } finally {
                if (!cancelled && taskGeneration() == generation && forestCycleEnabled(kind, uid)) {
                    int value = kind == 0 ? cycleTakeLookInterval.getValue() : cycleRankScanInterval.getValue();
                    long delay = kind == 0 ? Math.max(30, Math.min(3600, value)) * 1000L : Math.max(5, Math.min(60, value)) * 60000L;
                    scheduleForestCycles(kind, System.currentTimeMillis() + delay);
                }
            }
        }, at))) forestCycleIds[kind] = id;
    }

    private String findEnergyExposedFriend(JSONObject skipUsers) {
        if (hasErrorWait) return "";
        try {
            TimeUtil.sleep(0);
            JSONObject payload = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookCombineBiz(skipUsers)));
            JSONObject handlers = payload == null ? null : payload.optJSONObject("combineHandlerVOMap");
            JSONObject expose = handlers == null ? null : handlers.optJSONObject("takeLookExpose");
            Object value = expose == null ? null : expose.opt("friendUserId");
            if (!(value instanceof String)) return "";
            String id = (String) value;
            if (id.isEmpty() || id.length() > 64 || !id.equals(id.trim()) || id.chars().anyMatch(Character::isISOControl)) return "";
            if (selfId.equals(id) || dontCollectMap.contains(id) || !allowCollectByWhiteList(id)) return "";
            return id;
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Exception e) {
            Log.record("找能量预曝光未取得有效目标，继续普通推荐");
            return "";
        }
    }

    private static Map<String, JSONObject> takeLookRewardTasks(JSONObject payload) {
        if (payload == null) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        java.util.ArrayDeque<JSONObject> queue = new java.util.ArrayDeque<>();
        queue.add(payload);
        // ponytail: cap the whole grouped/child tree at 200 nodes; unknown or oversized trees stop.
        int count = 0;
        while (!queue.isEmpty()) {
            if (++count > 200) return null;
            JSONObject node = queue.remove();
            for (String list : new String[]{"forestTasksNew", "taskGroupInfoList", "taskInfoList", "childTaskTypeList"}) {
                if (!node.has(list)) continue;
                JSONArray rows = node.optJSONArray(list);
                if (rows == null || rows.length() > 200 || queue.size() + rows.length() > 200) return null;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) return null;
                    queue.add(row);
                }
            }
            if (!node.has("taskBaseInfo")) continue;
            JSONObject base = node.optJSONObject("taskBaseInfo");
            if (base == null || !(base.opt("sceneCode") instanceof String) || !(base.opt("taskType") instanceof String)
                    || !(base.opt("taskStatus") instanceof String)) return null;
            String scene = base.optString("sceneCode"), type = base.optString("taskType");
            if (scene.isEmpty() || type.isEmpty() || base.optString("taskStatus").isEmpty()
                    || result.put(scene + "#" + type, base) != null) return null;
        }
        if (!payload.has("forestTasksNew") && !payload.has("taskGroupInfoList") && !payload.has("taskInfoList")) return null;
        return result;
    }

    private void finishFindEnergy() {
        try {
            TimeUtil.sleep(0);
            JSONObject end = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLookEnd()));
            receiveFindEnergyRewards(end, "chInfo_ch_appid-60000002");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    private void finishEnergyRainFlow() {
        try {
            TimeUtil.sleep(0);
            if (!energyRain.getValue() || hasErrorWait) return;
            JSONObject end = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLookEnd("backFromEnergyRain")));
            JSONObject ext = end == null ? null : end.optJSONObject("extInfoInTakeLookEnd");
            boolean hint = false;
            for (String name : new String[]{"energyGrant", "energyPlay"}) {
                JSONObject item = ext == null ? null : ext.optJSONObject(name);
                Object title = item == null ? null : item.opt("title");
                if (title instanceof String && (((String) title).contains("还能收取") || ((String) title).contains("还可收取"))) hint = true;
            }
            if (hint && collectEnergy.getValue() && !isRevivedSelfQuietTime()) findAndCollectEnergy();
            receiveFindEnergyRewards(end, "backFromEnergyRain");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    private void receiveFindEnergyRewards(JSONObject end, String source) {
        try {
            if (end == null || !receiveForestTaskAward.getValue() || !Boolean.TRUE.equals(end.opt("showTaskList"))) return;
            TimeUtil.sleep(0);
            Map<String, JSONObject> tasks = takeLookRewardTasks(forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookEndTaskList(source))));
            if (tasks == null) return;
            int attempts = 0;
            for (String key : new ArrayList<>(tasks.keySet())) {
                JSONObject task = tasks.get(key);
                if (task == null || !("FINISHED".equals(task.optString("taskStatus")) || "COMPLETE".equals(task.optString("taskStatus")))) continue;
                String flag = "forest::findRewardAttempt::" + key;
                if (Status.hasFlagToday(flag)) continue;
                if (++attempts > 50) return;
                TimeUtil.sleep(0);
                if (!receiveForestTaskAward.getValue() || hasErrorWait) return;
                Status.flagToday(flag);
                JSONObject accepted = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAward(task.optString("sceneCode"), task.optString("taskType"))));
                TimeUtil.sleep(0);
                tasks = takeLookRewardTasks(forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookEndTaskList(source))));
                JSONObject fresh = tasks == null ? null : tasks.get(key);
                if (accepted == null || fresh == null || !"RECEIVED".equals(fresh.optString("taskStatus"))) {
                    Log.record("找能量奖励：领取未确认，当天不重复[" + task.optString("taskType") + "]");
                    return;
                }
                Log.forest("找能量奖励🎖️领取状态回查成功[" + task.optString("taskType") + "]");
                long energy = forestFeatureLong(accepted, "incAwardCount");
                if (accepted.opt("returnData") instanceof String && accepted.optString("returnData").contains("Energy")
                        && energy > 0 && energy <= Integer.MAX_VALUE) Statistics.addData(Statistics.DataType.COLLECTED, (int) energy);
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    private JSONObject collectSelfEnergy() {
        try {
            JSONObject selfHomeObject = querySelfHome();
            if (selfHomeObject != null) {
                if (whackModeName.getValue() == whackModeNames.CLOSE) {
                    JSONObject propertiesObject = selfHomeObject.optJSONObject("properties");
                    if (propertiesObject != null) {
                        if (Objects.equals("Y", propertiesObject.optString("whackMole"))) {
                            if (io.github.aw1y2z.sesame.model.task.antForest.WhackMole.closeWhackMole()) {
                                Log.forest("6秒拼手速关闭成功");
                            } else {
                                Log.forest("6秒拼手速关闭失败");
                            }
                        }
                    }
                }
                String nextAction = selfHomeObject.optString("nextAction");
                if ("WhackMole".equalsIgnoreCase(nextAction)) {
                    Log.record("检测到6秒拼手速强制弹窗，先执行拼手速");
                    checkAndHandleWhackMole();
                }
                return collectUserEnergy(UserIdMap.getCurrentUid(), selfHomeObject, "ordinary");
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectFriendEnergy(String userId, String getType) {
        if (hasErrorWait) {
            return null;
        }
        try {
            JSONObject userHomeObject = queryFriendHome(userId);
            if (userHomeObject != null) {
                return collectUserEnergy(userId, userHomeObject, getType);
            }
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
        return null;
    }

    private JSONObject collectUserEnergy(String userId, JSONObject userHomeObject, String getType) {
        try {
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) {
                return userHomeObject;
            }

            long serverTime = userHomeObject.optLong("now");
            boolean isSelf = Objects.equals(userId, selfId);
            String userName;
            boolean isCollectEnergy;
            //默认收炸弹能量
            boolean isBombCollectenergy = true;
            if (getType.equals("PK")) {
                JSONObject userBaseInfo = userHomeObject.optJSONObject("userBaseInfo");
                userName = (userBaseInfo != null ? userBaseInfo.optString("displayName") : "") + "(PK森友)";
                isCollectEnergy = allowCollectByWhiteList(userId);
            } else {
                userName = UserIdMap.getMaskName(userId);
                isCollectEnergy = collectEnergy.getValue() && !dontCollectMap.contains(userId) && allowCollectByWhiteList(userId);
            }

            if (isSelf) {
                Log.record("进入[" + userName + "]的蚂蚁森林");
                updateUsingPropsEndTime(userHomeObject);
            } else {
                if (isCollectEnergy) {
                    JSONArray jaProps = userHomeObject.optJSONArray("usingUserPropsNew");
                    for (int i = 0; jaProps != null && i < jaProps.length(); i++) {
                        JSONObject joProp = jaProps.optJSONObject(i);
                        if (joProp == null) {
                            continue;
                        }
                        if (Objects.equals("shield", joProp.optString("propGroup"))) {
                            if (joProp.optLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]能量罩保护到[" + TimeUtil.getCommonDateS(joProp.optLong("endTime")) + "]");
                                isCollectEnergy = false;
                                JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                                for (int ii = 0; jaBubbles != null && ii < jaBubbles.length(); ii++) {
                                    JSONObject canbubble = jaBubbles.optJSONObject(ii);
                                    if (canbubble == null) {
                                        continue;
                                    }
                                    long bubbleId = canbubble.optLong("id");
                                    switch (CollectStatus.valueOf(canbubble.optString("collectStatus"))) {
                                        case AVAILABLE:
                                            break;
                                        case WAITING:
                                            long produceTime = canbubble.optLong("produceTime");
                                            //如果保护罩不能覆盖能量成熟时间
                                            if (produceTime < joProp.optLong("endTime")) {
                                                break;
                                            }
                                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                                if (hasChildTask(AntForestV2.getBubbleTimerTid(userId, bubbleId))) {
                                                    break;
                                                }
                                                addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(canbubble.opt("canBeRobbedTwice"))));
                                                Log.record("[" + userName + "]能量保护罩时间[" + TimeUtil.getCommonDate(joProp.optLong("endTime")) + "]#未覆盖能量球成熟时间[" + TimeUtil.getCommonDate(produceTime) + "]");
                                                Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                            } else {
                                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                                            }
                                            break;
                                    }
                                }
                                break;
                            }
                        }
                        if (Objects.equals("energyBombCard", joProp.optString("propGroup"))) {
                            if (joProp.optLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]使用了炸弹卡");
                                if (userHomeObject.has("bubbles")) {
                                    JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                                    for (int ii = 0; jaBubbles != null && ii < jaBubbles.length(); ii++) {
                                        JSONObject Bombubble = jaBubbles.optJSONObject(ii);
                                        if (Bombubble == null) {
                                            continue;
                                        }
                                        int remainEnergy = Bombubble.optInt("remainEnergy");
                                        //存在小于预设值
                                        if (remainEnergy < CollectBombEnergyLimit.getValue()) {
                                            isBombCollectenergy = false;
                                        } else {
                                            Log.record("[" + userName + "]炸弹能量[" + remainEnergy + "g]>设定值[" + CollectBombEnergyLimit.getValue() + "g]");
                                            isBombCollectenergy = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isBombCollectenergy) {
                isCollectEnergy = false;
            }

            if (isCollectEnergy) {
                JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                List<Long> bubbleIdList = new ArrayList<>();
                for (int i = 0; jaBubbles != null && i < jaBubbles.length(); i++) {
                    JSONObject bubble = jaBubbles.optJSONObject(i);
                    if (bubble == null) {
                        continue;
                    }
                    if (isSelf && !allowRevivedSelfBubble(bubble, "WAITING".equals(bubble.optString("collectStatus")))) {
                        removeChildTask(getBubbleTimerTid(userId, bubble.optLong("id")));
                        continue;
                    }
                    int remainEnergy = bubble.optInt("remainEnergy");
                    long bubbleId = bubble.optLong("id");
                    switch (CollectStatus.valueOf(bubble.optString("collectStatus"))) {
                        case AVAILABLE:
                            //用阈值判断单个能量球需收取情况
                            if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                bubbleIdList.add(bubbleId);
                            } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                bubbleIdList.add(bubbleId);
                            } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                bubbleIdList.add(bubbleId);
                            }
                            break;
                        case WAITING:
                            long produceTime = bubble.optLong("produceTime");
                            if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                                if (hasChildTask(AntForestV2.getBubbleTimerTid(userId, bubbleId))) {
                                    break;
                                }
                                if (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if ((CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= CollectSelfEnergyThreshold.getValue())) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if (((CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= CollectSelfEnergyThreshold.getValue()))) {
                                    addChildTask(new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                }
                            } else {
                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                            }
                            break;
                    }
                }
                // 组队模式下走逐个收取，避免批量收取在合种场景下异常；
                // 直接复用已查询的 userHomeObject 判定组队状态，不再额外请求首页。
                if (batchRobEnergy.getValue() && (CollectSelfEnergyType.getValue() == CollectSelfType.ALL) && teamState(userHomeObject) == 0) {
                    Iterator<Long> iterator = bubbleIdList.iterator();
                    List<Long> batchBubbleIdList = new ArrayList<>();
                    while (iterator.hasNext()) {
                        batchBubbleIdList.add(iterator.next());
                        if (batchBubbleIdList.size() >= 6) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                            batchBubbleIdList = new ArrayList<>();
                        }
                    }
                    int size = batchBubbleIdList.size();
                    if (size > 0) {
                        if (size == 1) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, batchBubbleIdList.get(0))), userName);
                        } else {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                        }
                    }
                } else {
                    for (Long bubbleId : bubbleIdList) {
                        collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
                    }
                }
            }

            return userHomeObject;
        } catch (Throwable t) {
            Log.err(TAG, "collectUserEnergy err:", t);
        }
        return null;
    }

    private void collectFriendsEnergy(List<String> idList, String getType) {
        try {
            if (hasErrorWait) {
                return;
            }
            collectFriendsEnergy(MyUtils.newJSONObject(AntForestRpcCall.fillUserRobFlag(new JSONArray(idList).toString())), getType);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectFriendsEnergy(JSONObject friendsObject, String getType) {
        if (hasErrorWait) {
            return;
        }
        try {
            JSONArray jaFriendRanking = friendsObject.optJSONArray("friendRanking");
            if (jaFriendRanking == null) {
                return;
            }
            for (int i = 0, len = jaFriendRanking.length(); i < len; i++) {
                try {
                    JSONObject friendObject = jaFriendRanking.optJSONObject(i);
                    if (friendObject == null) {
                        continue;
                    }
                    String userId = friendObject.optString("userId");
                    if (Objects.equals(userId, selfId)) {
                        continue;
                    }
                    JSONObject userHomeObject = null;
                    if (getType.equals("PK")) {
                        boolean collectEnergy = true;
                        if (!friendObject.optBoolean("canCollectEnergy")) {
                            long canCollectLaterTime = friendObject.optLong("canCollectLaterTime");
                            if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                collectEnergy = false;
                            }
                        }
                        if (collectEnergy) {
                            userHomeObject = collectFriendEnergy(userId, getType);
                        }
                    } else {
                        if (collectEnergy.getValue() && !dontCollectMap.contains(userId) && allowCollectByWhiteList(userId)) {
                            boolean collectEnergy = true;
                            if (!friendObject.optBoolean("canCollectEnergy")) {
                                long canCollectLaterTime = friendObject.optLong("canCollectLaterTime");
                                if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                                    collectEnergy = false;
                                }
                            }
                            if (collectEnergy) {
                                userHomeObject = collectFriendEnergy(userId, getType);
                            } /* else {
                  Log.i("不收取[" + UserIdMap.getNameById(userId) + "], userId=" + userId);
              }*/
                        }

                        if (helpFriendCollectType.getValue() != HelpFriendCollectType.NONE && friendObject.optBoolean("canProtectBubble") && !Status.hasFlagToday("forest::protectBubble")) {
                            boolean isHelpCollect = helpFriendCollectList.getValue().contains(userId);
                            if (helpFriendCollectType.getValue() != HelpFriendCollectType.HELP) {
                                isHelpCollect = !isHelpCollect;
                            }
                            if (isHelpCollect) {
                                if (userHomeObject == null) {
                                    userHomeObject = queryFriendHome(userId);
                                }
                                if (userHomeObject != null) {
                                    protectFriendEnergy(userHomeObject);
                                }
                            }
                        }
                        if (collectGiftBox.getValue() && friendObject.optBoolean("canCollectGiftBox")) {
                            if (userHomeObject == null) {
                                userHomeObject = queryFriendHome(userId);
                            }
                            if (userHomeObject != null) {
                                collectGiftBox(userHomeObject);
                            }
                        }
                    }
                } catch (Exception t) {
                    Log.err(TAG, "collectFriendEnergy err:", t);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void collectGiftBox(JSONObject userHomeObject) {
        try {
            JSONObject giftBoxInfo = userHomeObject.optJSONObject("giftBoxInfo");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (giftBoxInfo != null) {
                JSONArray giftBoxList = giftBoxInfo.optJSONArray("giftBoxList");
                if (giftBoxList != null && giftBoxList.length() > 0) {
                    for (int ii = 0; ii < giftBoxList.length(); ii++) {
                        try {
                            JSONObject giftBox = giftBoxList.optJSONObject(ii);
                            if (giftBox == null) {
                                continue;
                            }
                            String giftBoxId = giftBox.optString("giftBoxId");
                            String title = giftBox.optString("title");
                            JSONObject giftBoxResult = MyUtils.newJSONObject(AntForestRpcCall.collectFriendGiftBox(giftBoxId, userId));
                            if (!MessageUtil.checkResultCode(TAG, giftBoxResult)) {
                                continue;
                            }
                            int energy = giftBoxResult.optInt("energy", 0);
                            Log.forest("礼盒能量🎁[" + UserIdMap.getMaskName(userId) + "-" + title + "]#" + energy + "g");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } catch (Throwable t) {
                            Log.printStackTrace(t);
                            break;
                        } finally {
                            TimeUtil.sleep(500);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private void protectFriendEnergy(JSONObject userHomeObject) {
        try {
            JSONArray wateringBubbles = userHomeObject.optJSONArray("wateringBubbles");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (wateringBubbles != null && wateringBubbles.length() > 0) {
                for (int j = 0; j < wateringBubbles.length(); j++) {
                    try {
                        JSONObject wateringBubble = wateringBubbles.optJSONObject(j);
                        if (wateringBubble == null) {
                            continue;
                        }
                        if (!Objects.equals("fuhuo", wateringBubble.optString("bizType"))) {
                            continue;
                        }
                        JSONObject extInfo = wateringBubble.optJSONObject("extInfo");
                        if (extInfo != null && extInfo.optInt("restTimes", 0) == 0) {
                            Status.flagToday("forest::protectBubble");
                        }
                        if (!wateringBubble.optBoolean("canProtect")) {
                            continue;
                        }
                        int fullEnergy = wateringBubble.optInt("fullEnergy", 0);
                        if (fullEnergy < helpFriendCollectListLimit.getValue()) {
                            continue;
                        }
                        JSONObject joProtect = MyUtils.newJSONObject(AntForestRpcCall.protectBubble(userId));
                        if (!MessageUtil.checkResultCode(TAG, joProtect)) {
                            continue;
                        }
                        int vitalityAmount = joProtect.optInt("vitalityAmount", 0);

                        String str = "复活能量🚑[" + UserIdMap.getMaskName(userId) + "-" + fullEnergy + "g]" + (vitalityAmount > 0 ? "#活力值+" + vitalityAmount : "");
                        Log.forest(str);
                        totalHelpCollected += fullEnergy;
                        Statistics.addData(Statistics.DataType.HELPED, fullEnergy);

                        break;
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                        break;
                    } finally {
                        TimeUtil.sleep(500);
                    }
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private boolean allowCollectByWhiteList(String userId) {
        if (userId == null || userId.isEmpty()) return false;
        if (!collectWhiteListMode.getValue() || Objects.equals(userId, selfId)) return true;
        Set<String> allowed = collectWhiteList.getValue();
        return allowed != null && allowed.contains(userId) && !dontCollectMap.contains(userId);
    }

    private boolean isRevivedSelfQuietTime() {
        if (!onlyCollectRevivedSelfEnergy.getValue()) return false;
        java.util.Calendar now = MyUtils.getInstance();
        return now.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.MONDAY
                && now.get(java.util.Calendar.HOUR_OF_DAY) == 7;
    }

    private boolean allowRevivedSelfBubble(JSONObject bubble, boolean waiting) {
        if (!onlyCollectRevivedSelfEnergy.getValue()) return true;
        if (bubble == null) return false;
        long energy = forestFeatureLong(bubble, "fullEnergy");
        if (energy <= 0) return false;
        JSONObject business = bubble.optJSONObject("business");
        if (!waiting && business != null && "fuhuonengliang".equals(business.optString("secondScene"))) return true;
        int max = revivedSelfOrdinaryMaxEnergy.getValue();
        return max > 0 && energy <= max;
    }

    private boolean allowRevivedSelfRequest(CollectEnergyEntity entity) {
        if (!onlyCollectRevivedSelfEnergy.getValue()) return true;
        if (isRevivedSelfQuietTime()) return false;
        if (!Objects.equals(entity.getUserId(), selfId)) return true;
        try {
            TimeUtil.sleep(0);
            RpcEntity rpc = entity.getRpcEntity();
            JSONArray args = rpc == null ? null : new JSONArray(rpc.getRequestData());
            JSONObject request = args == null || args.length() != 1 ? null : args.optJSONObject(0);
            JSONArray ids = request == null ? null : request.optJSONArray("bubbleIds");
            if (request == null || !selfId.equals(request.optString("userId")) || ids == null || ids.length() == 0 || ids.length() > 6) return false;
            JSONObject raw = querySelfHome();
            JSONObject home = raw == null ? null : forestSignPayload(raw);
            JSONArray rows = home == null ? null : home.optJSONArray("bubbles");
            if (rows == null) return false;
            Map<Long, JSONObject> current = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long id = row == null ? -1 : forestFeatureLong(row, "id");
                if (id <= 0 || current.put(id, row) != null) return false;
            }
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < ids.length(); i++) {
                Object value = ids.opt(i);
                if (!(value instanceof Number)) return false;
                long id = new java.math.BigDecimal(value.toString()).longValueExact();
                JSONObject bubble = current.get(id);
                if (id <= 0 || !seen.add(id) || bubble == null
                        || !("AVAILABLE".equals(bubble.optString("collectStatus")) || "WAITING".equals(bubble.optString("collectStatus")))
                        || !allowRevivedSelfBubble(bubble, "WAITING".equals(bubble.optString("collectStatus")))) return false;
            }
            TimeUtil.sleep(0);
            return !isRevivedSelfQuietTime();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { return false; }
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, String username) {
        collectEnergy(collectEnergyEntity, false, username);
    }

    private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username) {
        if (hasErrorWait || !allowCollectByWhiteList(collectEnergyEntity.getUserId())) {
            return;
        }
        Runnable runnable = () -> {
            try {
                String userId = collectEnergyEntity.getUserId();
                if (!allowCollectByWhiteList(userId)) return;
                //usePropBeforeCollectEnergy(userId);
                RpcEntity rpcEntity = collectEnergyEntity.getRpcEntity();
                boolean needDouble = collectEnergyEntity.getNeedDouble();
                boolean needRetry = collectEnergyEntity.getNeedRetry();
                int tryCount = collectEnergyEntity.addTryCount();
                int collected = 0;
                long startTime;
                synchronized (collectEnergyLockLimit) {
                    long sleep;
                    if (needDouble) {
                        collectEnergyEntity.unsetNeedDouble();
                        sleep = doubleCollectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else if (needRetry) {
                        collectEnergyEntity.unsetNeedRetry();
                        sleep = retryIntervalInt - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    } else {
                        sleep = collectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
                    }
                    if (sleep > 0) {
                        TimeUtil.sleep(sleep);
                    }
                    startTime = System.currentTimeMillis();
                    collectEnergyLockLimit.setForce(startTime);
                }
                if (!allowCollectByWhiteList(userId)) return;
                if (!allowRevivedSelfRequest(collectEnergyEntity)) return;
                ApplicationHook.requestObject(rpcEntity, 0, 0);
                long spendTime = System.currentTimeMillis() - startTime;
                if (balanceNetworkDelay.getValue()) {
                    delayTimeMath.nextInteger((int) (spendTime / 3));
                }
                JSONObject jo = MyUtils.newJSONObject(rpcEntity.getResponseString());
                String errorCode = jo.optString("error");
                if ("RPC_SKIPPED".equals(errorCode)) return;
                if (rpcEntity.getHasError() && !errorCode.isEmpty()) {
                    if (Objects.equals("1004", errorCode)) {
                        if (BaseModel.getWaitWhenException().getValue() > 0) {
                            long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                            RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                            NotificationUtil.updateStatusText("异常");
                            Log.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                            hasErrorWait = true;
                            return;
                        }
                        TimeUtil.sleep(600 + RandomUtil.delay());
                    }
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }
                String resultCode = jo.optString("resultCode");
                if (!"SUCCESS".equalsIgnoreCase(resultCode)) {
                    if ("PARAM_ILLEGAL2".equals(resultCode)) {
                        Log.forest("[" + username + "]" + "能量已被收取,取消重试 错误:" + jo.optString("resultDesc"));
                        return;
                    }
                    Log.forest("[" + username + "]" + jo.optString("resultDesc"));
                    if (tryCount < tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }

                JSONArray jaBubbles = jo.optJSONArray("bubbles");
                if (jaBubbles == null) {
                    jaBubbles = new JSONArray();
                }

                int jaBubbleLength = jaBubbles.length();
                if (jaBubbleLength > 1) {
                    List<Long> newBubbleIdList = new ArrayList<>();
                    for (int i = 0; i < jaBubbleLength; i++) {
                        JSONObject bubble = jaBubbles.optJSONObject(i);
                        if (bubble == null) {
                            continue;
                        }
                        if (bubble.optBoolean("canBeRobbedAgain")) {
                            newBubbleIdList.add(bubble.optLong("id"));
                        }
                        collected += bubble.optInt("collectedEnergy");
                    }
                    if (collected > 0) {
                        FriendWatch.friendWatch(userId, collected);
                        String str;
                        JSONObject bombCardEffect = jo.optJSONObject("bombCardEffect");
                        if (bombCardEffect != null) {
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "一键收取🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "一键收取🪂[" + username + "]#" + collected + "g";
                        }
                        if (needDouble) {
                            Log.forest(str + "[双击]");
                            Log.i("收取耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str);
                            Log.i("收取耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.forest("一键收取[" + username + "]的能量失败" + " " + "，UserID：" + userId + "，BubbleId：" + newBubbleIdList);
                    }
                    if (!newBubbleIdList.isEmpty()) {
                        collectEnergyEntity.setRpcEntity(AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, newBubbleIdList));
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                    }
                } else if (jaBubbleLength == 1) {
                    JSONObject bubble = jaBubbles.optJSONObject(0);
                    if (bubble == null) {
                        return;
                    }
                    collected += bubble.optInt("collectedEnergy");
                    FriendWatch.friendWatch(userId, collected);
                    if (collected > 0) {
                        String str;
                        JSONObject bombCardEffect = jo.optJSONObject("bombCardEffect");
                        if (bombCardEffect != null) {
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "收取能量🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "收取能量🪂[" + username + "]#" + collected + "g";
                        }

                        if (needDouble) {
                            Log.forest(str + "[双击]");
                            Log.i("收取耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str);
                            Log.i("收取耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.forest("收取[" + username + "]的能量失败");
                        Log.i("，UserID：" + userId + "，BubbleId：" + bubble.optLong("id"));
                    }
                    if (bubble.optBoolean("canBeRobbedAgain")) {
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                        return;
                    }
                    JSONObject userHome = collectEnergyEntity.getUserHome();
                    if (userHome == null) {
                        return;
                    }
                    String bizNo = userHome.optString("bizNo");
                    if (bizNo.isEmpty()) {
                        return;
                    }
                    if (returnWater.getValue()) {
                        int returnCount = 0;
                        if (collected >= returnWater33.getValue()) {
                            returnCount = 33;
                        } else if (collected >= returnWater18.getValue()) {
                            returnCount = 18;
                        } else if (collected >= returnWater10.getValue()) {
                            returnCount = 10;
                        }
                        if (returnCount > 0) {
                            returnFriendWater(userId, bizNo, 1, returnCount);
                        }
                    }
                }
            } catch (TaskCancelledException e) {
                throw e;
            } catch (Exception e) {
                Log.i("collectEnergy err:");
                Log.printStackTrace(e);
            } finally {
                Statistics.save();
                notifyMain();
            }
        };
        taskCount.incrementAndGet();
        if (joinThread) {
            runnable.run();
        } else {
            addChildTask(new ChildModelTask("CE|" + collectEnergyEntity.getUserId() + "|" + runnable.hashCode(), "CE", runnable));
        }
    }

    private void updateUsingPropsEndTime() throws JSONException {
        JSONObject joHomePage = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
        TimeUtil.sleep(100);
        updateUsingPropsEndTime(joHomePage);
    }

    private void updateUsingPropsEndTime(JSONObject joHomePage) {
        try {
            JSONArray ja = joHomePage.optJSONArray("loginUserUsingPropNew");
            if (ja == null || ja.length() == 0) {
                ja = joHomePage.optJSONArray("usingUserPropsNew");
            }
            if (ja == null) {
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String propGroup = jo.optString("propGroup");
                Long endTime = jo.optLong("endTime");
                String propId = jo.optString("propId");
                String propType = jo.optString("propType");
                usingProps.put(propGroup, endTime);
                if (PropGroup.robExpandCard.name().equals(propGroup)) {
                    collectRobExpandEnergy(jo.optString("extInfo"), propId, propType);
                }
            }
            forestExtensions();
        } catch (Throwable th) {
            Log.err(TAG, "updateUsingPropsEndTime err:", th);
        }
    }

    private void collectRobExpandEnergy(String extInfo, String propId, String propType) {
        if (extInfo.isEmpty()) {
            return;
        }
        // 关闭"倍卡额外能量"开关：不收取额外能量
        if (!collectRobExpandEnergyEnable.getValue()) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(extInfo);
            double leftEnergy = Double.parseDouble(jo.optString("leftEnergy", "0"));
            // 开关开启：受数值限制，只有大于"倍卡额外能量"阈值才收取
            if (leftEnergy > collectRobExpandEnergy.getValue()) {
                collectRobExpandEnergy(propId, propType);
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    private void collectRobExpandEnergy(String propId, String propType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.collectRobExpandEnergy(propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int collectEnergy = jo.optInt("collectEnergy");
                Log.forest("额外能量🎄收取[" + collectEnergy + "g]");
                totalCollected += collectEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectEnergy);
            }
        } catch (Throwable th) {
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    private void queryForestEnergy(String scene) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryForestEnergy(scene));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            jo = data != null ? data.optJSONObject("response") : null;
            if (jo == null) {
                return;
            }
            JSONArray ja = jo.optJSONArray("energyGeneratedList");
            if (ja == null) {
                ja = new JSONArray();
            }
            if (ja.length() > 0) {
                harvestForestEnergy(scene, ja);
            }
            int remainBubble = jo.optInt("remainBubble");
            for (int i = 0; i < remainBubble; i++) {
                ja = produceForestEnergy(scene);
                if (ja.length() == 0 || !harvestForestEnergy(scene, ja)) {
                    return;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryForestEnergy err:", th);
        }
    }

    private JSONArray produceForestEnergy(String scene) {
        JSONArray energyGeneratedList = new JSONArray();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.produceForestEnergy(scene));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                jo = data != null ? data.optJSONObject("response") : null;
                if (jo == null) {
                    return energyGeneratedList;
                }
                JSONArray list = jo.optJSONArray("energyGeneratedList");
                energyGeneratedList = list != null ? list : new JSONArray();
                if (energyGeneratedList.length() > 0) {
                    String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                    int cumulativeEnergy = jo.optInt("cumulativeEnergy");
                    Log.forest("医疗健康🚑完成[" + title + "]#产生[" + cumulativeEnergy + "g能量]");
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "produceForestEnergy err:", th);
        }
        return energyGeneratedList;
    }

    private Boolean harvestForestEnergy(String scene, JSONArray bubbles) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.harvestForestEnergy(scene, bubbles));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            jo = data != null ? data.optJSONObject("response") : null;
            if (jo == null) {
                return false;
            }
            int collectedEnergy = jo.optInt("collectedEnergy");
            if (collectedEnergy > 0) {
                String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                Log.forest("医疗健康🚑收取[" + title + "]#获得[" + collectedEnergy + "g能量]");
                totalCollected += collectedEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectedEnergy);
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "harvestForestEnergy err:", th);
        }
        return false;
    }

    /**
     * 检查并处理6秒拼手速逻辑（每天主动执行一次）
     */
    private void whackMole() {
        if (MyUtils.closeVerification()) return;
        try {
            if (whackModeName.getValue() == whackModeNames.CLOSE) {
                // 检查今天是否已执行过打地鼠
                if (Status.hasFlagToday("forest::whackMole::executed")) {
                    Log.record("⏭️ 今天已完成过6秒拼手速，跳过执行");
                } else {
                    // 主动执行打地鼠（今日首次）
                    Log.record("🎮 开始执行6秒拼手速（今日首次）");
                    checkAndHandleWhackMole();
                    Status.flagToday("forest::whackMole::executed");
                    Log.record("✅ 6秒拼手速已完成，今天不再执行");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "whackMole err:", t);
        }
    }

    private void checkAndHandleWhackMole() {
        try {
            // 获取当前选择的索引 (0, 1, 或 2)
            int modeIndex = (whackModeName != null) ? whackModeName.getValue() : 0;

            // 如果索引为 0 (关闭)，直接返回
            if (modeIndex == 0) {
                return;
            }

            // 检查执行时间
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            if (hour >= earliestwhackMoleTime.getValue()) {
                String whackMoleFlag = "forest::whackMole::executed";
                if (Status.hasFlagToday(whackMoleFlag)) {
                    return;
                }

                // 根据索引匹配模式
                switch (modeIndex) {
                    case 1: // 兼容模式
                        Log.record("触发任务🎮拼手速:兼容模式");
                        WhackMole.setTotalGames(1);
                        int defaultMoleCount = (whackModeCount != null) ? whackModeCount.getValue() : 15;
                        WhackMole.setMoleCount(defaultMoleCount);
                        WhackMole.start(WhackMole.Mode.COMPATIBLE);
                        break;

                    case 2: // 激进模式
                        Log.record("触发任务🎮拼手速:激进模式");
                        int configGames = (whackModeGames != null) ? whackModeGames.getValue() : 5;
                        WhackMole.setTotalGames(configGames);
                        WhackMole.start(WhackMole.Mode.AGGRESSIVE);
                        break;
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    public void initAntForestTaskListMap(boolean AutoAntForestVitalityTaskList, boolean AutoAntForestHuntTaskList, boolean receiveForestTaskAward, boolean ForestHunt) {
        try {

            //初始化AntForestVitalityTaskListMap
            AntForestVitalityTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注："三国大冒险过1关征战"（小游戏）不再预置拉黑，交由自动拉黑机制判定；
            // 其余（邀请助力/添加组件/连续7天/到店支付/淘宝花花乐/健康问答）保留
            Set<String> blackList = new HashSet<>();
            blackList.add("邀请1位好友助力");
            blackList.add("添加组件及时收能量");
            blackList.add("到店支付得50g能量");
            blackList.add("践行绿色行为");
            blackList.add("连续7天收自己能量");
            blackList.add("去淘宝花花乐领红包");
            blackList.add("去蚂蚁阿福健康问答");

            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("逛农场得落叶肥料");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestVitalityTaskListMap.add(task, task);
            }

            if (receiveForestTaskAward) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                    if (forestTasksNew != null && forestTasksNew.length() != 0) {
                        for (int i = 0; i < forestTasksNew.length(); i++) {
                            JSONObject forestTask = forestTasksNew.optJSONObject(i);
                            if (forestTask == null) {
                                continue;
                            }
                            JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                            if (taskInfoList != null && taskInfoList.length() != 0) {
                                for (int j = 0; j < taskInfoList.length(); j++) {
                                    JSONObject taskInfo = taskInfoList.optJSONObject(j);
                                    JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                    if (taskBaseInfo == null) {
                                        continue;
                                    }
                                    JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                    String taskType = taskBaseInfo.optString("taskType");
                                    String taskTitle = bizInfo.optString("taskTitle", taskType);
                                    AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                                }
                            }
                        }
                    }
                }

                jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    // 添加安全的空值判断
                    if (jo.has("taskInfoList")) {
                        JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                        if (taskInfoList != null && taskInfoList.length() != 0) {
                            for (int j = 0; j < taskInfoList.length(); j++) {
                                JSONObject taskInfo = taskInfoList.optJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                if (taskBaseInfo == null) {
                                    continue;
                                }
                                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                String taskType = taskBaseInfo.optString("taskType");
                                String taskTitle = bizInfo.optString("taskTitle", taskType);
                                AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                }


                //保存任务到配置文件
                AntForestVitalityTaskListMap.save();
                Log.record("同步任务🉑森林活力值任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestVitalityTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestVitalityTaskList = (SelectModelField) AntForestV2.get("AntForestVitalityTaskList");
                    if (AntForestVitalityTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林活力值任务", "AntForestVitalityTaskList", blackList, whiteList, AntForestVitalityTaskList);
                }
            }

            //初始化AntForestHuntTaskListMap
            AntForestHuntTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注：抽抽乐里的游戏/开宝箱类不再预置拉黑，交由自动拉黑机制判定
            blackList = new HashSet<>();
            // 可继续添加更多黑名单任务

            whiteList = new HashSet<>();// 从黑名单中移除该任务
            whiteList.add("消耗活力值得机会");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestHuntTaskListMap.add(task, task);
            }

            if (ForestHunt) {
                JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.enterDrawActivityopengreen("", "ANTFOREST_NORMAL_DRAW", "task_entry"));
                if (MessageUtil.checkSuccess(TAG, resData)) {
                    JSONArray drawSceneGroups = resData.optJSONArray("drawSceneGroups");
                    for (int i = 0; drawSceneGroups != null && i < drawSceneGroups.length(); i++) {
                        JSONObject drawScene = drawSceneGroups.optJSONObject(i);
                        JSONObject drawActivity = drawScene != null ? drawScene.optJSONObject("drawActivity") : null;
                        if (drawActivity == null) {
                            continue;
                        }
                        String sceneCode = drawActivity.optString("sceneCode");
                        JSONObject listTaskopengreen = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
                        if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                            JSONArray taskList = listTaskopengreen.optJSONArray("taskInfoList");
                            for (int j = 0; taskList != null && j < taskList.length(); j++) {
                                JSONObject taskInfo = taskList.optJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                if (taskBaseInfo == null) {
                                    continue;
                                }
                                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                String taskName = bizInfo.optString("title");
                                AntForestHuntTaskListMap.add(taskName, taskName);
                            }
                        }
                    }
                }
                AntForestHuntTaskListMap.save();
                Log.record("同步任务🉑森林抽抽乐任务列表");
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestHuntTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestHuntTaskList = (SelectModelField) AntForestV2.get("AntForestHuntTaskList");
                    if (AntForestHuntTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林抽抽乐任务", "AntForestHuntTaskList", blackList, whiteList, AntForestHuntTaskList);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initAntForestTaskListMap err:", t);
        }
    }

    /**
     * 新版保护地任务列表初始化：把服务端下发的任务名同步到 {@link MonopolyTaskListMap}，
     * 作为配置页「新版保护地 | 黑名单列表」的候选项。
     * <p>没有预置黑白名单：新版保护地任务名由服务端动态下发，全部交给自动拉黑机制判定。
     */
    public void initMonopolyTaskListMap() {
        try {
            MonopolyTaskListMap.load();
            JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(MONOPOLY_REGION_HSDWY, MONOPOLY_TASK_SCENE_HSDWY));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList != null) {
                    for (int i = 0; i < taskInfoList.length(); i++) {
                        JSONObject task = taskInfoList.optJSONObject(i);
                        JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                        if (base == null || !MONOPOLY_TASK_SCENE_HSDWY.equals(base.optString("sceneCode"))) {
                            continue;
                        }
                        String taskType = base.optString("taskType");
                        if (taskType.isEmpty()) {
                            continue;
                        }
                        String title = blackTaskKey(monopolyTaskTitle(base, taskType));
                        MonopolyTaskListMap.add(title, title);
                    }
                }
            }
            MonopolyTaskListMap.save();
            Log.record("同步任务🉑新版保护地任务列表");
        } catch (Throwable t) {
            Log.err(TAG, "initMonopolyTaskListMap err:", t);
        }
    }

    /* 森林集市 */
    private static void greenLife() {
        sendEnergyByAction("GREEN_LIFE");
        //sendEnergyByAction("ANTFOREST");
        retrieveCurrentActivity();
    }

    // 绿色租赁
    private static void greenRent() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.checkUserSecondSceneChance());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
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
        } catch (Throwable t) {
            Log.err(TAG, "greenRent err:", t);
        }
    }

    private static void retrieveCurrentActivity() {
        try {
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.retrieveCurrentActivity());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }

            jo = jo.optJSONObject("data");
            if (jo == null || !jo.has("currentActivity")) {
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
            jo = MyUtils.newJSONObject(GreenLifeRpcCall.finishCurrentTask(taskTemplateId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            JSONArray ja = jo != null ? jo.optJSONArray("prizes") : null;
            StringBuilder award = new StringBuilder();
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (i > 0) {
                    award.append(";");
                }
                award.append(jo.optString("name"));
            }
            if (award.length() > 0) {
                award = new StringBuilder("#获得[" + award + "]");
            }
            Log.forest("森林集市🛍️打卡[坚持" + numberOfDaysCompleted + "天]" + award);
        } catch (Throwable t) {
            Log.err(TAG, "retrieveCurrentActivity err:", t);
        }
    }

    private static void sendEnergyByAction(String sourceType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(GreenLifeRpcCall.consultForSendEnergyByAction(sourceType));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data != null && data.optBoolean("canSendEnergy", false)) {
                jo = MyUtils.newJSONObject(GreenLifeRpcCall.sendEnergyByAction(sourceType));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    data = jo.optJSONObject("data");
                    if (data != null && data.optBoolean("canSendEnergy", false)) {
                        int receivedEnergyAmount = data.optInt("receivedEnergyAmount");
                        Log.forest("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
                        Toast.show("森林集市🛍️完成[线上逛街]#产生[" + receivedEnergyAmount + "g能量]");
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "sendEnergyByAction err:", t);
        }
    }

    private void popupTask() {
        try {
            JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.popupTask());
            if (!MessageUtil.checkResultCode(TAG, resData)) {
                return;
            }
            JSONArray forestSignVOList = resData.optJSONArray("forestSignVOList");
            if (forestSignVOList != null) {
                for (int i = 0; i < forestSignVOList.length(); i++) {
                    JSONObject forestSignVO = forestSignVOList.optJSONObject(i);
                    if (forestSignVO == null) {
                        continue;
                    }
                    String signId = forestSignVO.optString("signId");
                    String currentSignKey = forestSignVO.optString("currentSignKey");
                    JSONArray signRecords = forestSignVO.optJSONArray("signRecords");
                    for (int j = 0; signRecords != null && j < signRecords.length(); j++) {
                        JSONObject signRecord = signRecords.optJSONObject(j);
                        if (signRecord == null) {
                            continue;
                        }
                        String signKey = signRecord.optString("signKey");
                        if (signKey.equals(currentSignKey)) {
                            if (!signRecord.optBoolean("signed")) {
                                JSONObject resData2 = MyUtils.newJSONObject(AntForestRpcCall.antiepSign(signId, "ANTFOREST_ENERGY_SIGN", UserIdMap.getCurrentUid()));
                                if (MessageUtil.checkSuccess(TAG, resData2)) {
                                    Log.forest("过期能量💊[" + signRecord.optInt("awardCount") + "g]");
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "popupTask err:", t);
        }
    }

    private void waterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (waterCount > 3) {
                waterCount = 3;
            }
            // 只补浇「配置次数 - 当日已浇」的差额，避免部分失败后被重复浇满而超出配置
            int remainCount = waterCount - Status.getWaterFriendToday(uid);
            if (remainCount > 0) {
                try {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryFriendHomePage(uid));
                    TimeUtil.sleep(100);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String bizNo = jo.optString("bizNo");
                        int friendWaterEnergy = friendWaterEnergy(uid, waterEnergy);
                        if (friendWaterEnergy <= 0) continue;
                        KVNode<Integer, Boolean> waterCountKVNode = returnFriendWater(uid, bizNo, remainCount, friendWaterEnergy);
                        int wateredCount = waterCountKVNode.getKey();
                        if (wateredCount > 0) {
                            Status.waterFriendToday(uid, wateredCount, taskUid);
                            Log.record("好友浇水：成功[" + UserIdMap.getMaskName(uid) + "]，本次" + wateredCount + "次，每次" + friendWaterEnergy + "g");
                        }
                        if (!waterCountKVNode.getValue()) {
                            break;
                        }
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "waterFriendEnergy err:", t);
                }
            }
        }
    }

    private KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy) {
        if (bizNo == null || bizNo.isEmpty()) {
            return new KVNode<>(0, true);
        }
        int wateredTimes = 0;
        boolean isContinue = true;
        try {
            String s;
            JSONObject jo;
            int energyId = getEnergyId(waterEnergy);
            label:
            for (int waterCount = 1; waterCount <= count; waterCount++) {
                s = AntForestRpcCall.transferEnergy(userId, bizNo, energyId, waterFriendEnergySendChat.getValue() ? "Y" : "N");
                TimeUtil.sleep(1500);
                jo = MyUtils.newJSONObject(s);

                String resultCode = jo.optString("resultCode");
                switch (resultCode) {
                    case "SUCCESS":
                        //记录浇水次数
                        Status.wateringFriendToday(userId);
                        Statistics.addData(Statistics.DataType.WATERINGCOUNT, 1);
                        JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
                        int currentEnergy = userBaseInfo != null ? userBaseInfo.optInt("currentEnergy") : 0;
                        Log.forest("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g#剩余能量[" + currentEnergy + "g]");
                        Toast.show("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g");
                        wateredTimes++;
                        Statistics.addData(Statistics.DataType.WATERED, waterEnergy);
                        break;
                    case "WATERING_TIMES_LIMIT":
                        Log.forest("好友浇水🚿今日给[" + UserIdMap.getMaskName(userId) + "]浇水已达上限");
                        wateredTimes = 3;
                        break label;
                    case "ENERGY_INSUFFICIENT":
                        // 自己能量不够时后面的好友也必然失败（日报单账号 36 次），本轮停止浇水
                        Log.record("好友浇水🚿能量不足，本轮停止浇水");
                        isContinue = false;
                        break label;
                    case "WATERING_USER_LIMIT":
                        Log.forest("好友浇水🚿给[" + UserIdMap.getMaskName(userId) + "]浇水，" + jo.optString("resultDesc"));
                        wateredTimes = 3;
                        break label;
                    default:
                        // 未知失败不再重发：响应丢失但已生效时，用同一 bizNo 重发会重复扣能量
                        Log.forest("好友浇水🚿" + jo.optString("resultDesc"));
                        Log.i(jo.toString());
                        break label;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "returnFriendWater err:", t);
        }
        return new KVNode<>(wateredTimes, isContinue);
    }

    private int getEnergyId(int waterEnergy) {
        if (waterEnergy <= 0) {
            return 0;
        }
        if (waterEnergy >= 66) {
            return 42;
        }
        if (waterEnergy >= 33) {
            return 41;
        }
        if (waterEnergy >= 18) {
            return 40;
        }
        return 39;
    }

    private void doubleWaterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        boolean reSet = true;
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (Status.canWaterFriendToday(uid, 3)) {
                reSet = false;
            }
        }
        if (reSet) {
            for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
                String uid = friendEntry.getKey();
                if (selfId.equals(uid)) {
                    continue;
                }
                Integer waterCount = friendEntry.getValue();
                if (waterCount == null || waterCount <= 0) {
                    continue;
                }
                //重置浇水次数
                Status.resetWaterFriendToday(uid, taskUid);
            }
            Log.record("好友浇水🚿今日给好友浇水状态已重置！");
            Status.flagToday("Forest::doubleWaterFriendEnergy");
        }
    }

    private void forestExtensions() {
        try {
            ExtensionsHandle.handleAlphaRequest("antForest", "extensions", usingProps);
        } catch (Throwable t) {
            Log.err(TAG, "forestExtensions err:", t);
        }
    }

    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void vitalityExchangeBenefit() {
        try {
            getAllSkuInfo();
            Map<String, Integer> exchangeList = vitality_ExchangeBenefitList.getValue();
            for (Map.Entry<String, Integer> entry : exchangeList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                while (Status.canVitalityExchangeBenefitToday(skuId, count) && exchangeBenefit(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalityExchangeBenefit err:", t);
        }
    }

    static JSONObject forestSignPayload(JSONObject root) {
        if (root == null) return null;
        if (RpcRequestGuard.isFailure(root)) return null;
        JSONObject data = root.optJSONObject("resData");
        if (data == null) data = root;
        if (RpcRequestGuard.isFailure(data)) return null;
        if (Boolean.TRUE.equals(data.opt("success")) || "SUCCESS".equals(data.optString("resultCode"))
                || "100".equals(data.optString("resultCode"))) return data;
        if (data != root && (Boolean.TRUE.equals(root.opt("success")) || "SUCCESS".equals(root.optString("resultCode")))) return data;
        return null;
    }

    private static java.util.SortedSet<java.time.LocalDate> missingForestSignDates(JSONArray rows, java.time.LocalDate today) {
        if (rows == null) return null;
        java.util.SortedSet<java.time.LocalDate> missing = new java.util.TreeSet<>();
        Map<String, String> states = new HashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("signKey") instanceof String)) continue;
            String key = row.optString("signKey");
            java.time.LocalDate date;
            try { date = java.time.LocalDate.parse(key); } catch (java.time.DateTimeException e) { continue; }
            if (!date.toString().equals(key) || !date.isBefore(today) || date.isBefore(today.minusDays(30))) continue;
            String state = String.valueOf(row.opt("signed")) + "/" + row.opt("makeUpSigned") + "/" + row.opt("canMakeUpSign");
            String previous = states.put(key, state);
            if (previous != null && !previous.equals(state)) return null;
            if (Boolean.FALSE.equals(row.opt("signed")) && Boolean.FALSE.equals(row.opt("makeUpSigned"))
                    && (!row.has("canMakeUpSign") || Boolean.TRUE.equals(row.opt("canMakeUpSign")))) missing.add(date);
        }
        return missing;
    }

    private JSONArray queryForestSignMonth(java.time.YearMonth month) throws JSONException {
        TimeUtil.sleep(0);
        java.time.LocalDate first = month.atDay(1);
        java.time.LocalDate begin = first.minusDays(first.getDayOfWeek().getValue() - 1);
        JSONObject data = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryForestSignMonth(month.toString(), begin.toString(), begin.plusDays(41).toString())));
        return data == null ? null : data.optJSONArray("signModelList");
    }

    private int forestMakeupCardCount() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject data = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.listForestMakeupCards()));
        JSONObject stock = data == null ? null : data.optJSONObject("userSCAssetsVO");
        JSONArray ids = stock == null ? null : stock.optJSONArray("canUseSCAssetsIdList");
        if (ids == null) return -1;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object id = ids.opt(i);
            if (!(id instanceof String) || ((String) id).isEmpty() || !unique.add((String) id)) return -1;
        }
        return unique.size();
    }

    private Map<Long, Long> waitingBoostBubbles(JSONObject home) {
        long now = forestFeatureLong(home, "now");
        JSONArray rows = home.optJSONArray("bubbles");
        if (now <= 0 || rows == null) return null;
        Map<Long, Long> result = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            long id = row == null ? -1 : forestFeatureLong(row, "id");
            if (id <= 0 || !seen.add(id)) return null;
            if (!"WAITING".equals(row.optString("collectStatus"))) continue;
            long energy = forestFeatureLong(row, "fullEnergy"), at = forestFeatureLong(row, "produceTime");
            if (energy <= 0 || at <= now) continue;
            int type = CollectSelfEnergyType.getValue(), threshold = CollectSelfEnergyThreshold.getValue();
            if (type != CollectSelfType.ALL && !(type == CollectSelfType.OVER_THRESHOLD && energy >= threshold)
                    && !(type == CollectSelfType.BELOW_THRESHOLD && energy <= threshold)) continue;
            result.put(id, at);
        }
        return result;
    }

    private static JSONObject selectBoostProp(JSONObject bag, int mode, long now) throws JSONException {
        JSONArray rows = bag == null ? null : bag.optJSONArray("forestPropVOList");
        if (rows == null) return null;
        JSONObject best = null;
        int bestRank = Integer.MAX_VALUE;
        long bestExpiry = Long.MAX_VALUE;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject prop = rows.optJSONObject(i);
            if (prop == null) return null;
            if (!"boost".equals(prop.optString("propGroup"))) continue;
            long count = forestFeatureLong(prop, "holdsNum");
            JSONArray ids = prop.optJSONArray("propIdList");
            if (count < 0 || ids == null) return null;
            for (int j = 0; j < ids.length(); j++) {
                Object id = ids.opt(j);
                if (!(id instanceof String) || ((String) id).isEmpty() || !seen.add((String) id)) return null;
            }
            if (count == 0 || ids.length() == 0) continue;
            JSONObject config = prop.optJSONObject("propConfigVO");
            Object rawType = prop.has("propType") ? prop.opt("propType") : config == null ? null : config.opt("propType");
            if (!(rawType instanceof String)) continue;
            String type = (String) rawType;
            boolean limited = "LIMIT_TIME_ENERGY_BUBBLE_BOOST".equals(type);
            if (!limited && !"BUBBLE_BOOST".equals(type)) continue;
            if (mode == UsePropType.ONLY_LIMIT_TIME && !limited) continue;
            long expiry = prop.has("recentExpireTime") ? forestFeatureLong(prop, "recentExpireTime") : 0;
            if (expiry < 0 || limited && expiry <= now || !limited && expiry > 0 && expiry <= now) continue;
            if (expiry == 0) expiry = Long.MAX_VALUE;
            int rank = limited ? 0 : 1;
            if (rank < bestRank || rank == bestRank && expiry < bestExpiry) {
                best = MyUtils.newJSONObject(prop.toString()).put("propType", type);
                bestRank = rank; bestExpiry = expiry;
            }
        }
        return best;
    }

    private static long boostStockForId(JSONObject bag, String id) {
        JSONArray rows = bag == null ? null : bag.optJSONArray("forestPropVOList");
        if (rows == null) return -1;
        long result = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject prop = rows.optJSONObject(i);
            if (prop == null) return -1;
            if (!"boost".equals(prop.optString("propGroup"))) continue;
            long count = forestFeatureLong(prop, "holdsNum");
            JSONArray ids = prop.optJSONArray("propIdList");
            if (count < 0 || ids == null) return -1;
            for (int j = 0; j < ids.length(); j++) {
                Object value = ids.opt(j);
                if (!(value instanceof String) || ((String) value).isEmpty() || !seen.add((String) value)) return -1;
                if (id.equals(value)) result = count;
            }
        }
        return result;
    }

    private JSONObject useBubbleBoostCard() {
        synchronized (usePropLockObj) {
            if (!boostTimeAllowed(System.currentTimeMillis())) return null;
            return useBubbleBoostCardLocked();
        }
    }

    private String boostPointFlag(long now) {
        String point = ForestSchedule.pointKey(bubbleBoostTime.getValue(), now);
        return point.isEmpty() ? "" : "forest::boostTimePoint::" + point;
    }

    private boolean boostTimeAllowed(long now) {
        String flag = boostPointFlag(now);
        return ForestSchedule.nextTrigger(bubbleBoostTime.getValue(), now) == now
                && (flag.isEmpty() || !Status.hasFlagToday(flag));
    }

    private synchronized void scheduleBubbleBoost(long earliest) {
        if (io.github.aw1y2z.sesame.util.RunGeneration.isStale()) return;
        String uid = UserIdMap.getCurrentUid(), old = bubbleBoostCheckId;
        int mode = bubbleBoostCard.getValue();
        long now = System.currentTimeMillis();
        boolean enabled = isEnable() && collectEnergy.getValue() && !onlyCollectRevivedSelfEnergy.getValue()
                && !hasErrorWait && !isRevivedSelfQuietTime() && uid != null && !uid.isEmpty()
                && (mode == UsePropType.ALL || mode == UsePropType.ONLY_LIMIT_TIME)
                && Status.getIntFlagToday("forest::boostAttempts") < bubbleBoostDailyLimit.getValue();
        long from = Math.max(now + 1000, earliest);
        String rules = bubbleBoostTime.getValue();
        long at = enabled ? ForestSchedule.nextTrigger(rules, from) : -1;
        if (at >= 0 && !boostPointFlag(at).isEmpty() && Status.hasFlagToday(boostPointFlag(at)))
            at = ForestSchedule.nextTrigger(rules, at - at % 60000L + 60000L);
        if (at < 0 || !ForestSchedule.hasPoints(rules) && at == from) {
            if (old != null) removeChildTask(old);
            bubbleBoostCheckId = null;
            return;
        }
        ChildModelTask current = old == null ? null : getChildTask(old);
        if (current != null && Boolean.FALSE.equals(current.getIsCancel())) return;
        long generation = taskGeneration();
        String id = "FOREST_BOOST|" + uid + "|" + System.nanoTime();
        if (addChildTask(new ChildModelTask(id, "antForest", () -> {
            synchronized (this) {
                if (!id.equals(bubbleBoostCheckId)) return;
                bubbleBoostCheckId = null;
            }
            boolean cancelled = false;
            try {
                runExclusiveChild(generation, () -> {
                    TimeUtil.sleep(0);
                    if (!uid.equals(UserIdMap.getCurrentUid()) || !isEnable() || hasErrorWait || !check() || isRevivedSelfQuietTime()) return;
                    selfId = uid;
                    useBubbleBoostCard();
                });
            } catch (TaskCancelledException e) {
                cancelled = true;
                throw e;
            } finally {
                if (!cancelled && generation == taskGeneration() && uid.equals(UserIdMap.getCurrentUid())) scheduleBubbleBoost(System.currentTimeMillis() + 30000);
            }
        }, at))) bubbleBoostCheckId = id;
    }

    private JSONObject useBubbleBoostCardLocked() {
        if (ForestExpiringProps.hasUnconfirmed("boost")) return null;
        if (onlyCollectRevivedSelfEnergy.getValue()) return null;
        int mode = bubbleBoostCard.getValue(), limit = bubbleBoostDailyLimit.getValue();
        if ((mode != UsePropType.ALL && mode != UsePropType.ONLY_LIMIT_TIME) || limit <= 0 || !collectEnergy.getValue()
                || Status.getIntFlagToday("forest::boostAttempts") >= limit) return null;
        try {
            TimeUtil.sleep(0);
            JSONObject rawHome = querySelfHome();
            JSONObject home = rawHome == null ? null : forestSignPayload(rawHome);
            if (home == null) return null;
            Map<Long, Long> waiting = waitingBoostBubbles(home);
            if (waiting == null || waiting.isEmpty()) return null;
            JSONObject bag = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
            JSONObject prop = selectBoostProp(bag, mode, forestFeatureLong(home, "now"));
            if (prop == null) {
                JSONArray refilled = refillForestProp("boost");
                if (refilled != null) { bag = MyUtils.newJSONObject().put("forestPropVOList", refilled);prop = selectBoostProp(bag, mode, forestFeatureLong(home, "now")); }
            }
            if (prop == null) return null;
            TimeUtil.sleep(0);
            rawHome = querySelfHome();
            home = rawHome == null ? null : forestSignPayload(rawHome);
            if (home == null) return null;
            waiting = waitingBoostBubbles(home);
            if (waiting == null || waiting.isEmpty()) return null;
            prop = selectBoostProp(bag, mode, forestFeatureLong(home, "now"));
            if (prop == null) return null;
            String id = prop.optJSONArray("propIdList").optString(0);
            String flag = "forest::boostAttempt::" + id;
            if (Status.hasFlagToday(flag)) return null;
            TimeUtil.sleep(0);
            if (bubbleBoostCard.getValue() != mode || !collectEnergy.getValue() || onlyCollectRevivedSelfEnergy.getValue()
                    || !boostTimeAllowed(System.currentTimeMillis())
                    || Status.getIntFlagToday("forest::boostAttempts") >= bubbleBoostDailyLimit.getValue()) return null;
            String pointFlag = boostPointFlag(System.currentTimeMillis());
            if (!pointFlag.isEmpty()) Status.flagToday(pointFlag);
            Status.flagToday(flag);
            Status.setIntFlagToday("forest::boostAttempts", Status.getIntFlagToday("forest::boostAttempts") + 1);
            JSONObject accepted = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.consumeProp("boost", id, prop.optString("propType"), false)));
            TimeUtil.sleep(0);
            JSONObject afterRaw = querySelfHome();
            JSONObject after = afterRaw == null ? null : forestSignPayload(afterRaw);
            JSONObject afterBag = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
            long stock = boostStockForId(afterBag, id);
            if (accepted == null || after == null || stock < 0 || stock >= forestFeatureLong(prop, "holdsNum")) return null;
            long now = forestFeatureLong(after, "now");
            JSONArray rows = after.optJSONArray("bubbles");
            if (rows == null || now < forestFeatureLong(home, "now")) return null;
            Set<Long> seen = new HashSet<>(), changed = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long bubble = row == null ? -1 : forestFeatureLong(row, "id");
                if (bubble <= 0 || !seen.add(bubble)) return null;
                Long original = waiting.get(bubble);
                if (original == null) continue;
                long at = forestFeatureLong(row, "produceTime");
                if ("WAITING".equals(row.optString("collectStatus")) && at > 0 && at < original
                        || "AVAILABLE".equals(row.optString("collectStatus")) && now < original && forestFeatureLong(row, "remainEnergy") > 0) changed.add(bubble);
            }
            if (changed.isEmpty()) {
            Log.record("时光加速器效果未确认，本日不重复尝试此卡");
                return null;
            }
            TimeUtil.sleep(0);
            for (Long bubble : changed) removeChildTask(getBubbleTimerTid(selfId, bubble));
            Log.forest("时光加速器🌪库存及加速效果回查成功#" + changed.size() + "个能量球");
            return collectUserEnergy(selfId, afterRaw, "ordinary");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err(TAG, "useBubbleBoostCard", e); }
        return null;
    }

    private void autoMakeUpSign() {
        if (!autoMakeUpSign.getValue()) return;
        try {
            if (forestMakeupCardCount() <= 0) return;
            Calendar day = MyUtils.getInstance();
            java.time.LocalDate today = java.time.LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
            java.util.SortedSet<java.time.LocalDate> missing = new java.util.TreeSet<>();
            java.time.YearMonth last = java.time.YearMonth.from(today);
            // A 30-day window can span three months (March 1 in a non-leap year).
            for (java.time.YearMonth month = java.time.YearMonth.from(today.minusDays(30)); !month.isAfter(last); month = month.plusMonths(1)) {
                java.util.SortedSet<java.time.LocalDate> dates = missingForestSignDates(queryForestSignMonth(month), today);
                if (dates == null) { Log.record("森林补签：日历状态未知，停止"); return; }
                missing.addAll(dates);
            }
            for (java.time.LocalDate date : missing) {
                TimeUtil.sleep(0);
                String flag = "forest::makeupAttempt::" + date;
                if (Status.hasFlagToday(flag)) continue;
                java.util.SortedSet<java.time.LocalDate> fresh = missingForestSignDates(queryForestSignMonth(java.time.YearMonth.from(date)), today);
                if (fresh == null) return;
                if (!fresh.contains(date)) continue;
                if (forestMakeupCardCount() <= 0) return;
                Calendar current = MyUtils.getInstance();
                if (!today.equals(java.time.LocalDate.of(current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1, current.get(Calendar.DAY_OF_MONTH)))) return;
                Status.flagToday(flag);
                JSONObject response = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.manualMakeUpSign(date.toString())));
                JSONArray after = queryForestSignMonth(java.time.YearMonth.from(date));
                if (response == null || after == null) return;
                int matches = 0;
                boolean confirmed = false;
                for (int i = 0; i < after.length(); i++) {
                    JSONObject row = after.optJSONObject(i);
                    if (row != null && date.toString().equals(row.opt("signKey"))) {
                        matches++;
                        confirmed = Boolean.TRUE.equals(row.opt("signed")) || Boolean.TRUE.equals(row.opt("makeUpSigned"));
                    }
                }
                if (matches != 1 || !confirmed) { Log.record("森林补签：结果未确认，当天不重复提交[" + date + "]"); return; }
                Log.forest("森林补签📅[" + date + "]回查成功");
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "autoMakeUpSign", t); }
    }

    private void vantiepSign() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray forestSignVOList = jo.optJSONArray("forestSignVOList");
            JSONObject forestSignVO = forestSignVOList != null ? forestSignVOList.optJSONObject(0) : null;
            if (forestSignVO == null) {
                return;
            }
            String currentSignKey = forestSignVO.optString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.optString("signId"); // 签到ID
            String sceneCode = forestSignVO.optString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.optJSONArray("signRecords"); // 签到记录
            for (int i = 0; signRecords != null && i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.optJSONObject(i);
                if (signRecord == null) {
                    continue;
                }
                String signKey = signRecord.optString("signKey");
                int awardCount = signRecord.optInt("awardCount");
                if (signKey.equals(currentSignKey) && !signRecord.optBoolean("signed")) {
                    JSONObject joSign = MyUtils.newJSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林签到失败:", joSign)) {
                        int continuousCount = joSign.optInt("continuousCount");
                        Log.forest("森林签到📆拯救第" + continuousCount + "天#复活[" + awardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    private void queryCommonSign() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryCommonSign("ANTFOREST_GIFT7TH_SIGN_202506"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("forestSignVO")) {
                if (!Status.hasFlagToday("forest::CommonSign")) {
                    Status.flagToday("forest::CommonSign");
                    Log.i("森林签到📆尚未检测到[森林7日签到数据]若出现数据立马为大人领取");
                }
                return;
            }
            JSONObject forestSignVO = jo.optJSONObject("forestSignVO");
            if (forestSignVO == null) {
                return;
            }
            String currentSignKey = forestSignVO.optString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.optString("signId"); // 签到ID
            String sceneCode = forestSignVO.optString("sceneCode"); // 场景代码
            JSONArray signRecords = forestSignVO.optJSONArray("signRecords"); // 签到记录
            for (int i = 0; signRecords != null && i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.optJSONObject(i);
                if (signRecord == null) {
                    continue;
                }
                String signKey = signRecord.optString("signKey");
                int awardCount = signRecord.optInt("awardCount");
                String awardType = signRecord.optString("awardType");
                JSONObject extInfo = signRecord.optJSONObject("extInfo");
                String awardName = extInfo != null ? extInfo.optString("awardName") : "";
                if (signKey.equals(currentSignKey) && !signRecord.optBoolean("signed")) {
                    JSONObject joSign = MyUtils.newJSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林7日签到:", joSign)) {
                        int continuousCount = joSign.optInt("continuousCount");
                        Log.forest("森林签到📆第" + continuousCount + "天#7日签到[" + awardName + "*" + awardCount + "]");
                        if (awardType.equals("ENERGY")) {
                            Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        }
                        // return awardCount;
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    private void vitalitySign() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.vitalitySign());
            TimeUtil.sleep(300);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int continuousCount = jo.optInt("continuousCount");
                int signAwardCount = jo.optInt("signAwardCount");
                Log.forest("森林任务📆签到[" + continuousCount + "天]奖励[" + signAwardCount + "活力值]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    private void queryTaskList() {
        queryTaskList("DNHZ_SL_college", "DAXUESHENG_SJK");
        queryTaskList("DXS_BHZ", "NENGLIANGZHAO_20230807");
        queryTaskList("DXS_JSQ", "JIASUQI_20230808");
        try {
            boolean doubleCheck = true;
            while (doubleCheck) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    doubleCheck = false;
                    continue;
                }
                // 添加安全的空值判断
                if (!jo.has("forestTasksNew")) {
                    doubleCheck = false;
                    continue;
                }
                JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                if (forestTasksNew == null || forestTasksNew.length() == 0) {
                    doubleCheck = false;
                    continue;
                }
                for (int i = 0; i < forestTasksNew.length(); i++) {
                    JSONObject forestTask = forestTasksNew.optJSONObject(i);
                    if (forestTask == null) {
                        continue;
                    }
                    JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                    if (taskInfoList == null) {
                        continue;
                    }
                    doubleCheck = doForsetTaskList(taskInfoList);
                }
            }
            doubleCheck = true;
            while (doubleCheck) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                // 添加安全的空值判断
                if (!jo.has("taskInfoList")) {
                    return;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null || taskInfoList.length() == 0) {
                    return;
                }
                doubleCheck = doForsetTaskList(taskInfoList);
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTaskList err:", t);
        }
    }

    private Boolean doForsetTaskList(JSONArray taskInfoList) {
        boolean doubleCheck = false;
        try {
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return doubleCheck;
            }
            for (int j = 0; j < taskInfoList.length(); j++) {
                JSONObject taskInfo = taskInfoList.optJSONObject(j);
                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                if (taskBaseInfo == null) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                String taskStatus = taskBaseInfo.optString("taskStatus");
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                        doubleCheck = true;
                    }
                } else if (TaskStatus.TODO.name().equals(taskStatus)) {
                    //黑名单任务跳过
                    if (AntForestVitalityTaskList.getValue().contains(taskTitle)) {
                        continue;
                    }

                    if ("TEST_LEAF_TASK".equals(taskType)) {
                        JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                        if (childTaskTypeList != null && childTaskTypeList.length() > 0) {
                            doChildTask(childTaskTypeList, taskTitle);
                            doubleCheck = true;
                            continue;
                        }
                    }

                    if ("DAKA_GROUP".equals(taskType)) {
                        JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                        if (childTaskTypeList != null && childTaskTypeList.length() > 0) {
                            doChildTask(childTaskTypeList, taskTitle);
                            continue;
                        }
                    }

                    // 种树攻略场景卡：行为子任务挂在 childTaskTypeList 下，父任务只是容器、没有完成接口
                    if ("ENERGY_XUANJIAO".equals(taskType)) {
                        JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                        int childCount = childTaskTypeList != null ? childTaskTypeList.length() : 0;
                        Log.other("场景卡[ENERGY_XUANJIAO]命中#开关=" + energySceneTask.getValue() + "#子任务数=" + childCount);
                        if (energySceneTask.getValue() && childCount > 0) {
                            doEnergySceneTask(childTaskTypeList);
                        }
                        continue;
                    }

                    doubleCheck = finishTask(sceneCode, taskType, taskTitle);
                }
            }
            //可能是触发限时挑战奖励的
            String touchResp = AntForestRpcCall.batchQueryAndTouchopengreen();
            Log.other("批量领取活力值能量响应#" + (touchResp != null && touchResp.length() > 800 ? touchResp.substring(0, 800) : touchResp));
        } catch (Throwable t) {
            Log.err(TAG, "doForsetTaskList err:", t);
        }
        return doubleCheck;
    }


    private void queryTaskList(String firstTaskType, String taskType) {
        if (Status.hasFlagToday("vitalityTask::" + firstTaskType)) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList(MyUtils.newJSONObject().put("firstTaskType", firstTaskType)));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 添加安全的空值判断
            if (!jo.has("forestTasksNew")) {
                return;
            }
            JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
            if (forestTasksNew == null || forestTasksNew.length() == 0) {
                return;
            }
            JSONObject firstTask = forestTasksNew.optJSONObject(0);
            if (firstTask == null || !firstTask.has("taskInfoList")) {
                return;
            }
            JSONArray taskInfoList = firstTask.optJSONArray("taskInfoList");
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                JSONObject taskInfoItem = taskInfoList.optJSONObject(i);
                jo = taskInfoItem != null ? taskInfoItem.optJSONObject("taskBaseInfo") : null;
                if (jo == null || !Objects.equals(taskType, jo.optString("taskType"))) {
                    continue;
                }
                boolean isReceived = TaskStatus.RECEIVED.name().equals(jo.optString("taskStatus"));
                if (!isReceived && TaskStatus.FINISHED.name().equals(jo.optString("taskStatus"))) {
                    String sceneCode = jo.optString("sceneCode");
                    String taskTitle = MyUtils.newJSONObject(jo.optString("bizInfo")).optString("taskTitle");
                    isReceived = receiveTaskAward(sceneCode, taskType, taskTitle);
                    TimeUtil.sleep(1000);
                }
                if (isReceived) {
                    Status.flagToday("vitalityTask::" + firstTaskType);
                }
                return;
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryTaskList err:", t);
        }
    }

    private Boolean receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAward(sceneCode, taskType));
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.optInt("incAwardCount", 1);
                if (jo.has("returnData")) {
                    String returnData = jo.optString("returnData");
                    if (returnData.contains("Energy")) {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                    } else {
                        Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "活力值]");
                    }
                }
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveTaskAward err:", t);
        }
        return false;
    }

    /**
     * 黑名单键：剥掉标题末尾的 "(n/N)" 次数后缀。
     * <p>权限类任务在调用 {@link #finishTask} 时标题会被拼上 "(2/10)"，
     * 而运行时的黑名单检查用的是纯标题；不处理会导致写进黑名单的键永远匹配不上、拉黑失效。
     */
    private static String blackTaskKey(String taskTitle) {
        return StringUtil.stripCountSuffix(taskTitle);
    }

    private Boolean finishTask(String sceneCode, String taskType, String taskTitle) {
        if (MyUtils.closeUnRpc() && "ANTFOREST_VITALITY_TASK".equals(sceneCode) && taskType != null) {
            if (taskType.startsWith("GYG_BK_XYK")
                    || taskType.startsWith("GYG_jinritoutiao")
                    || taskType.startsWith("GYG_huabeikaitong")) {
                // 这几类任务不支持通过 RPC 完成，直接跳过而不是让请求失败重试
                return false;
            }
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", blackTaskKey(taskTitle), jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                return true;
            }
            // 另一种实现方案（见 TaskAlternative）
            if (TaskAlternative.hit(jo, sceneCode)) {
                TaskAlternative.trigger(null, taskType, taskTitle, taskType, sceneCode, "森林任务", msg -> Log.forest(msg));
                return false;
            }
            Log.forest("完成任务[" + taskTitle + "]失败");
        } catch (Throwable t) {
            Log.err(TAG, "finishTask err:", t);
        }
        return false;
    }

    private void doChildTask(JSONArray childTaskTypeList, String title) {
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.optJSONObject(i);
                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                if (taskBaseInfo == null) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", title);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                String taskStatus = taskBaseInfo.optString("taskStatus");
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (bizInfo.optBoolean("autoCompleteTask")) {
                        finishTask(sceneCode, taskType, taskTitle);
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "doChildTask err:", th);
        }
    }

    /**
     * 场景卡子任务：这类任务（如选教卡下的无纸化阅读）属于真实低碳行为，需用户真实操作后服务端才发能量。
     * FINISHED 的直接调用 receiveTaskAward 领取能量；TODO 的尝试完成接口。
     * finishTask 失败仅代表本次未完成，不代表永远无法完成，故不拉黑，避免污染黑名单后连 FINISHED 的奖励都无法领取。
     */
    private void doEnergySceneTask(JSONArray childTaskTypeList) {
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.optJSONObject(i);
                if (taskInfo == null) continue;
                JSONObject taskBaseInfo = taskInfo.optJSONObject("taskBaseInfo");
                if (taskBaseInfo == null) continue;
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String taskStatus = taskBaseInfo.optString("taskStatus");
                String sceneCode = taskBaseInfo.optString("sceneCode");
                if (taskType.isEmpty() || sceneCode.isEmpty()) continue;
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                        Log.other("场景子任务[" + taskTitle + "]已领取奖励");
                    }
                    continue;
                }
                if (!TaskStatus.TODO.name().equals(taskStatus)) {
                    continue;
                }
                Log.other("场景子任务尝试[" + taskTitle + "]#sceneCode=" + sceneCode + "#taskType=" + taskType);
                String resp = AntForestRpcCall.finishTask(sceneCode, taskType);
                Log.other("场景子任务响应[" + taskTitle + "]#" + (resp != null && resp.length() > 800 ? resp.substring(0, 800) : resp));
                JSONObject jo = MyUtils.newJSONObject(resp);
                TimeUtil.sleep(500);
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    Log.forest("森林任务🧾️完成[" + taskTitle + "]");
                } else if (TaskAlternative.hit(jo, sceneCode)) {
                    TaskAlternative.trigger(null, taskType, taskTitle, taskType, sceneCode, "森林任务", msg -> Log.forest(msg));
                    Log.other("场景子任务[" + taskTitle + "]失败#" + taskInfo);
                } else {
                    Log.other("场景子任务[" + taskTitle + "]失败#" + taskInfo);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "doEnergySceneTask err:", t);
        }
    }

    private void startEnergyRain() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.startEnergyRain());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String token = jo.optString("token");
            JSONObject difficultyInfo = jo.optJSONObject("difficultyInfo");
            JSONArray bubbleEnergyList = difficultyInfo != null ? difficultyInfo.optJSONArray("bubbleEnergyList") : null;
            int sum = 0;
            for (int i = 0; bubbleEnergyList != null && i < bubbleEnergyList.length(); i++) {
                sum += bubbleEnergyList.optInt(i);
            }
            TimeUtil.sleep(5000L);
            if (sum == 50) {
                Status.flagToday("EnergyRain::PlayGame");
            }
            jo = MyUtils.newJSONObject(AntForestRpcCall.energyRainSettlement(sum, token));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Toast.show("获得了[" + sum + "g]能量[能量雨]");
                Log.forest("收能量雨🌧️[" + sum + "g]");
                totalCollected += sum;
                Statistics.addData(Statistics.DataType.COLLECTED, sum);
            }
            TimeUtil.sleep(500);
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "startEnergyRain err:", th);
        }
    }

    // LIMIT_TIME_ENERGY_DOUBLE_CLICK,CR20230516000363
    // LIMIT_TIME_ENERGY_RAIN_CHANCE,SK20250117005985,VITALITY_ENERGYRAIN_3DAYS，限时3天内使用能量雨次卡
    private void useEnergyRainCard() {
        try {
            // 商店兑换 限时能量雨卡
            exchangeBenefit("SK20250117005985");
            TimeUtil.sleep(2000);
            JSONObject jo;
            do {
                TimeUtil.sleep(1000);
                // 背包查找 能量雨卡
                jo = null;
                List<JSONObject> list = getPropGroup(getForestPropVOList(), PropGroup.energyRain.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null) {
                    break;
                }
                // 使用能量雨卡
            } while (consumeProp(jo));
        } catch (Throwable th) {
            Log.err(TAG, "useEnergyRainCard err:", th);
        }
    }

    private void energyRain() {
        try {
            boolean started = false;
            JSONObject joEnergyRainHome = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome)) {
                if (joEnergyRainHome.optBoolean("canPlayToday")) {
                    started = true;
                    startEnergyRain();
                }
                if (joEnergyRainHome.optBoolean("canGrantStatus")) {
                    Log.record("有送能量雨的机会");
                    JSONObject joEnergyRainCanGrantList = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainCanGrantList());
                    TimeUtil.sleep(500);
                    JSONArray grantInfos = joEnergyRainCanGrantList.optJSONArray("grantInfos");
                    Set<String> set = giveEnergyRainList.getValue();
                    String userId;
                    boolean granted = false;
                    for (int j = 0; grantInfos != null && j < grantInfos.length(); j++) {
                        JSONObject grantInfo = grantInfos.optJSONObject(j);
                        if (grantInfo != null && grantInfo.optBoolean("canGrantedStatus")) {
                            userId = grantInfo.optString("userId");
                            if (set.contains(userId)) {
                                JSONObject joEnergyRainChance = MyUtils.newJSONObject(AntForestRpcCall.grantEnergyRainChance(userId));
                                TimeUtil.sleep(500);
                                Log.record("尝试送能量雨给【" + UserIdMap.getMaskName(userId) + "】");
                                granted = true;
                                // 20230724能量雨调整为列表中没有可赠送的好友则不赠送
                                if (MessageUtil.checkResultCode(TAG, joEnergyRainChance)) {
                                    Log.forest("送能量雨🌧️[" + UserIdMap.getMaskName(userId) + "]");
                                    started = true;
                                    startEnergyRain();
                                }
                                break;
                            }
                        }
                    }
                    if (!granted) {
                        Log.record("没有可以送的用户");
                    }
                }
                boolean canPlayGame = joEnergyRainHome.optBoolean("canPlayGame");

                if (canPlayGame) {
                    // 检查今日是否需要执行
                    if (!Status.hasFlagToday("EnergyRain::PlayGame")) {
                        Log.record("是否可以能量雨游戏: " + canPlayGame);
                        // 检查并处理游戏任务
                        checkAndDoEndGameTask();
                        TimeUtil.sleep(4000);
                    }
                }
            }
            joEnergyRainHome = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, joEnergyRainHome) && joEnergyRainHome.optBoolean("canPlayToday")) {
                started = true;
                startEnergyRain();
            }
            if (started) finishEnergyRainFlow();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "energyRain err:", th);
        }
    }

    public static void checkAndDoEndGameTask() {
        try {
            // 1. 查询游戏任务列表
            String response = AntForestRpcCall.queryEnergyRainEndGameList();
            JSONObject jo = MyUtils.newJSONObject(response);
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("energyRainEndGameGroupTask")) {
                Status.flagToday("EnergyRain::PlayGame");
                return;
            }

            // 2. 初始化新任务（需要接入森林救援队）
            if (jo.optBoolean("needInitTask", false)) {
                Log.record("检测到新任务，准备接入[森林救援队]...");
                String initResStr = AntForestRpcCall.initTask("GAME_DONE_SLJYD");
                JSONObject initRes = MyUtils.newJSONObject(initResStr);
                if (!MessageUtil.checkResultCode(TAG, initRes)) {
                    return;
                }
            }

            // 3. 仅当森林救援队(GAME_DONE_SLJYD)未完结时才上报，避免每轮重复发起外部请求
            JSONObject groupTask = jo.optJSONObject("energyRainEndGameGroupTask");
            JSONArray taskInfoList = groupTask != null ? groupTask.optJSONArray("taskInfoList") : null;

            String sljydStatus = null;
            if (taskInfoList != null) {
                for (int i = 0; i < taskInfoList.length(); i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject baseInfo = task != null ? task.optJSONObject("taskBaseInfo") : null;
                    if (baseInfo == null) continue;
                    if ("GAME_DONE_SLJYD".equals(baseInfo.optString("taskType"))) {
                        sljydStatus = baseInfo.optString("taskStatus");
                        break;
                    }
                }
            }

            // 需要初始化、或任务处于待办/未触发时上报；已完结/无任务则仅标记今日已处理
            boolean needInit = jo.optBoolean("needInitTask", false);
            boolean shouldReport = needInit
                    || "TODO".equals(sljydStatus)
                    || "NOT_TRIGGER".equals(sljydStatus);

            if (shouldReport) {
                GameTask.Forest_sljyd.report("森林", 1);
                Status.flagToday("EnergyRain::PlayGame");
                return;
            }
            Log.record("森林救援队🐱无需上报(状态:" + sljydStatus + ")，今日跳过");
            Status.flagToday("EnergyRain::PlayGame");

        } catch (Throwable th) {
            Log.printStackTrace("执行能量雨后续任务出错:", th);
        }
    }

    public void doforestgame() {
        try {
            String response = AntForestRpcCall.queryGameList();
            JSONObject jo = MyUtils.newJSONObject(response);

            // 验证请求是否成功
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.error("queryGameList 失败: " + jo.optString("desc"));
                return;
            }

            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights != null) {
                int perTime = drawRights.optInt("quotaPerTime", 100);

                // 换算实际宝箱次数
                int canUseCount = drawRights.optInt("quotaCanUse") / perTime;
                int limitCount = drawRights.optInt("quotaLimit") / perTime;
                int usedCount = drawRights.optInt("usedQuota") / perTime;

                // 1. 处理待开启奖励 (批量开启)
                if (canUseCount > 0) {
                    Log.record("森林乐园正在一次性开启 " + canUseCount + " 个宝箱...");
                    JSONObject drawJo = MyUtils.newJSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
                    if (!MessageUtil.checkResultCode(drawJo)) {
                        return;
                    }
                    JSONArray awardList = drawJo.optJSONArray("gameCenterDrawAwardList");
                    int totalEnergy = 0;
                    List<String> otherAwards = new ArrayList<>();

                    if (awardList != null) {
                        for (int i = 0; i < awardList.length(); i++) {
                            JSONObject award = awardList.optJSONObject(i);
                            if (award == null) {
                                continue;
                            }
                            String type = award.optString("awardType");
                            String name = award.optString("awardName");
                            int count = award.optInt("awardCount");
                            Log.forest("森林乐园🎁开宝箱得[" + name + "*" + count + "]");
                            if ("ENERGY".equals(type)) {
                                totalEnergy += count;
                            } else {
                                otherAwards.add(name + "x" + count);
                            }
                        }
                    }
                    Statistics.addData(Statistics.DataType.COLLECTED, totalEnergy);
                    // 输出统计结果
                    StringBuilder logMsg = new StringBuilder("森林乐园🎁[开宝箱]共计");
                    if (totalEnergy > 0) {
                        logMsg.append("获得能量").append(totalEnergy).append("g");
                    }
                    if (!otherAwards.isEmpty()) {
                        if (totalEnergy > 0) {
                            logMsg.append(", ");
                        }
                        logMsg.append("其他: ").append(String.join("/", otherAwards));
                    }
                    Log.forest(logMsg.toString());
                    Toast.show(logMsg.toString());

                }

                // 2. 判断是否需要刷任务
                int remainToTask = limitCount - usedCount;
                if (remainToTask > 0) {
                    GameTask.Forest_slxcc.report("森林", remainToTask);
                } else {
                    Log.record("今日森林乐园游戏任务已满额");
                }
            }

        } catch (CancellationException e) {
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace("doforestgame 流程异常", t);
        }
    }

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
            boolean doubleCheck = true;
            while (doubleCheck) {
                doubleCheck = false;
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryOptionalPlay());
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
                if (taskList == null) {
                    return;
                }
                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.optJSONObject(j);
                    if (task == null) {
                        continue;
                    }
                    String taskStatus = task.optString("taskStatus");
                    int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                    int awardCount = task.optInt("awardCount");
                    int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                    int rightsTimesLimit = task.optInt("rightsTimesLimit");
                    int rightsTimes = task.optInt("rightsTimes");
                    String awardType = task.optString("awardType", "能量");
                    JSONObject bizInfo = task.optJSONObject("bizInfo");
                    String title = bizInfo != null ? bizInfo.optString("title") : "";
                    String source = task.optString("source", "ch_appcenter__chsub_9patch");
                    String sceneCode = task.optString("sceneCode", "");
                    String taskType = task.optString("taskType", "");
                    // 记录任务状态
                    if (taskStatus.equals("FINISHED")) {
                        if (awardCountForReceive > 0) {
                            // 领取奖励
                            JSONObject joReceived = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAwardopengreen(source, sceneCode, taskType));
                            if (MessageUtil.checkSuccess(TAG, joReceived)) {
                                int incAwardCount = joReceived.optInt("incAwardCount");
                                JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                                if (taskConfigResultVO != null) {
                                    awardType = taskConfigResultVO.optString("awardType", awardType);
                                }
                                // 能量统计
                                if ("能量".equals(awardType) || "ENERGY".equals(awardType)) {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + incAwardCount + "g能量]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                                } else {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                                }
                            }
                        }
                    }
                    if (TaskStatus.TODO.name().equals(taskStatus) || rightsTimes < rightsTimesLimit) {
                        //黑名单任务跳过
                        if (AntForestVitalityTaskList.getValue().contains(title)) {
                            continue;
                        }
                        if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                            if (finishTask(sceneCode, taskType, title + "(" + (rightsTimes + 1) + "/" + rightsTimesLimit + ")")) {
                                doubleCheck = true;
                            }
                        } else {
                            if (finishTask(sceneCode, taskType, title)) {
                                doubleCheck = true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    private void continuousUseCardOptions() {
        //双击卡
        continuousUseAndExchangeCard("doubleClick", "SK20240805004754");
        //收能量倍卡
        continuousUseAndExchangeCard("robExpandCard", "");
        //保护罩：自动续用为独立开关，不受「连续兑换使用道具卡片」选项限制
        if (autoUseShieldCard.getValue()) {
            continuousUseAndExchangeCard("shield", "CR20230516000370");
        }
        //隐身卡
        continuousUseAndExchangeCard("stealthCard", "SK20230521000206");
        //炸弹卡
        //continuousUseAndExchangeCard("energyBombCard", "SK20250219006517");
    }

    private void continuousUseAndExchangeCard(String propGroupType, String exchangeProp) {
        if (ForestExpiringProps.hasUnconfirmed(propGroupType)) return;
        try {
            if ("doubleClick".equals(propGroupType) && smartDoubleCard.getValue()) {
                useSmartDoubleCard();
                return;
            }
            if (propGroupType.equals("shield") || continuousUseCardOptions.getValue().contains(propGroupType)) {
                if ("robExpandCard".equals(propGroupType) && (robExpandCardReplaceRemainDays.getValue() > 0
                        || robExpandCardForceReplaceExpireDays.getValue() > 0)) {
                    refillForestProp(propGroupType);
                    usePreferredRobExpandCard();
                    return;
                }
                long continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                if (continuousUseCardSecond >= 0) {
                    TimeUtil.sleep(500);
                    JSONObject rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                    if (rightCard == null) {
                        if (forestPropRefill.getValue()) {
                            refillForestProp(propGroupType);
                        } else if (exchangeProp != null) {
                            exchangeBenefit(exchangeProp);
                            TimeUtil.sleep(500);
                        }
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                    }
                    int holdsNum = rightCard.optInt("holdsNum");
                    if (holdsNum == 0) {
                        return;
                    }
                    int loopCount = 0; // 循环次数计数
                    final int MAX_LOOP = 10; // 最大循环次数，避免死循环
                    do {
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                        holdsNum = rightCard.optInt("holdsNum");
                        if (holdsNum == 0) {
                            return;
                        }
                        if (!rightCard.has("propIdList")) {
                            return;
                        }
                        JSONArray propIdList = rightCard.optJSONArray("propIdList");
                        if (propIdList.length() == 0) {
                            return;
                        }
                        String propId = propIdList.optString(0);
                        String propType = rightCard.optString("propType");
                        JSONObject propConfigVO = rightCard.optJSONObject("propConfigVO");
                        String propName = propConfigVO != null ? propConfigVO.optString("propName") : "";
                        JSONObject joResult;
                        switch (propGroupType) {
                            case "doubleClick":
                            case "shield":
                            case "robExpandCard":
                                if (continuousUseCardSecond > 0) {
                                    joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, true));
                                } else {
                                    joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, false));
                                }
                                holdsNum--;
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;

                            case "stealthCard":
                                joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;
                            /*case "energyBombCard":
                                joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType,false));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;*/
                        }
                        continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                        if (continuousUseCardSecond < 0) {
                            return;
                        }
                        TimeUtil.sleep(500);
                    } while (holdsNum > 0 && ++loopCount < MAX_LOOP);
                }
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "continuousUseAndExchangeCard err:", th);
        }
    }

    private boolean smartDoubleEnabled() {
        return smartDoubleCard.getValue() && collectEnergy.getValue() && !hasErrorWait
                && continuousUseCardOptions.getValue().contains("doubleClick") && !isRevivedSelfQuietTime()
                && smartDoubleCardDailyLimit.getValue() > 0;
    }

    private List<BubbleTimerTask> smartDoubleTargets(long now) {
        List<BubbleTimerTask> targets = new ArrayList<>();
        List<ChildModelTask> queued = getChildTaskSnapshot();
        if (queued.size() > 2000) return targets;
        for (ChildModelTask child : queued) {
            if (!(child instanceof BubbleTimerTask) || !Boolean.FALSE.equals(child.getIsCancel())) continue;
            BubbleTimerTask task = (BubbleTimerTask) child;
            if (task.canDouble && !selfId.equals(task.userId) && !dontCollectMap.contains(task.userId)
                    && allowCollectByWhiteList(task.userId) && task.produceTime >= now + 10000
                    && task.produceTime <= now + 290000) targets.add(task);
        }
        return targets;
    }

    private List<BubbleTimerTask> confirmSmartDoubleTargets(List<BubbleTimerTask> queued) {
        Map<String, List<BubbleTimerTask>> friends = new LinkedHashMap<>();
        for (BubbleTimerTask task : queued) friends.computeIfAbsent(task.userId, ignored -> new ArrayList<>()).add(task);
        if (friends.size() > 50) return null;
        List<BubbleTimerTask> targets = new ArrayList<>();
        for (Map.Entry<String, List<BubbleTimerTask>> friend : friends.entrySet()) {
            TimeUtil.sleep(0);
            JSONObject raw = queryFriendHome(friend.getKey());
            JSONObject home = raw == null ? null : forestSignPayload(raw);
            if (home == null) return null;
            for (String key : new String[]{"userInfo", "userBaseInfo"}) {
                if (!home.has(key)) continue;
                JSONObject identity = home.optJSONObject(key);
                if (identity == null || identity.has("userId") && (!(identity.opt("userId") instanceof String)
                        || !friend.getKey().equals(identity.optString("userId")))) return null;
            }
            if (home.has("usingUserPropsNew")) {
                JSONArray props = home.optJSONArray("usingUserPropsNew");
                if (props == null || props.length() > 100) return null;
                for (int i = 0; i < props.length(); i++) {
                    JSONObject prop = props.optJSONObject(i);
                    if (prop == null || !(prop.opt("propGroup") instanceof String) || forestFeatureLong(prop, "endTime") < 0) return null;
                }
            }
            if (hasActiveProp(home, "shield") || hasActiveProp(home, "energyBombCard")) continue;
            JSONArray rows = home.optJSONArray("bubbles");
            if (rows == null || rows.length() > 200) return null;
            Map<Long, JSONObject> bubbles = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long id = row == null ? -1 : forestFeatureLong(row, "id");
                if (id <= 0 || bubbles.put(id, row) != null) return null;
            }
            long now = System.currentTimeMillis();
            for (BubbleTimerTask task : friend.getValue()) {
                JSONObject bubble = bubbles.get(task.bubbleId);
                if (bubble != null && "WAITING".equals(bubble.optString("collectStatus"))
                        && Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice")) && forestFeatureLong(bubble, "fullEnergy") > 0
                        && forestFeatureLong(bubble, "produceTime") == task.produceTime && task.produceTime >= now + 10000
                        && task.produceTime <= now + 290000 && Boolean.FALSE.equals(task.getIsCancel())
                        && !dontCollectMap.contains(task.userId) && allowCollectByWhiteList(task.userId)) targets.add(task);
            }
        }
        return targets;
    }

    private long querySmartDoubleEnd() {
        TimeUtil.sleep(0);
        JSONObject data = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo()));
        JSONObject map = data == null ? null : data.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray rows = using == null ? null : using.optJSONArray("userPropVOS");
        if (rows == null || rows.length() > 100) return -1;
        long active = 0, now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String)) return -1;
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            long end = forestFeatureLong(row, "endTime");
            if (end < 0 || end > now && active > 0) return -1;
            if (end > now) active = end;
        }
        return active;
    }

    private JSONArray querySmartDoubleInventory() {
        TimeUtil.sleep(0);
        JSONObject data = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
        JSONArray rows = data == null ? null : data.optJSONArray("forestPropVOList");
        if (rows == null || rows.length() > 100) return null;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String)) return null;
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            long count = forestFeatureLong(row, "holdsNum");
            JSONArray list = row.optJSONArray("propIdList");
            if (!(row.opt("propType") instanceof String) || row.optString("propType").isEmpty() || count < 0 || count > 1000
                    || list == null && count > 0 || list != null && list.length() != count
                    || row.has("recentExpireTime") && forestFeatureLong(row, "recentExpireTime") < 0) return null;
            for (int j = 0; list != null && j < list.length(); j++) {
                Object id = list.opt(j);
                if (!(id instanceof String) || ((String) id).isEmpty() || ((String) id).length() > 256 || !ids.add((String) id)) return null;
            }
        }
        return rows;
    }

    private JSONObject chooseSmartDoubleCard(JSONArray rows) {
        JSONObject chosen = null;
        long expiry = Long.MAX_VALUE, now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (!"doubleClick".equals(row.optString("propGroup")) || !"LIMIT_TIME_ENERGY_DOUBLE_CLICK".equals(row.optString("propType"))
                    || forestFeatureLong(row, "holdsNum") <= 0) continue;
            long until = row.has("recentExpireTime") ? forestFeatureLong(row, "recentExpireTime") : 0;
            if (until > 0 && until <= now + 10000 || until < 0) continue;
            JSONArray ids = row.optJSONArray("propIdList");
            if (ids == null || ids.length() == 0 || Status.hasFlagToday("forest::smartDoubleCard::" + ids.optString(0))) continue;
            long priority = until == 0 ? Long.MAX_VALUE : until;
            if (chosen == null || priority < expiry) { chosen = row; expiry = priority; }
        }
        return chosen;
    }

    private static long smartDoubleStock(JSONArray rows, String type) {
        if (rows == null) return -1;
        long count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if ("doubleClick".equals(row.optString("propGroup")) && type.equals(row.optString("propType"))) count += forestFeatureLong(row, "holdsNum");
        }
        return count;
    }

    private static boolean smartDoubleHasId(JSONArray rows, String id) {
        for (int i = 0; i < rows.length(); i++) {
            JSONArray ids = rows.optJSONObject(i).optJSONArray("propIdList");
            for (int j = 0; ids != null && j < ids.length(); j++) if (id.equals(ids.opt(j))) return true;
        }
        return false;
    }

    private void useSmartDoubleCard() {
        if (ForestExpiringProps.hasUnconfirmed("doubleClick")) return;
        TimeUtil.sleep(0);
        if (!smartDoubleEnabled()) return;
        synchronized (usePropLockObj) {
            if (!smartDoubleEnabled() || !selfId.equals(UserIdMap.getCurrentUid()) || Status.hasFlagToday("forest::smartDoubleUnconfirmed")
                    || Status.getIntFlagToday("forest::smartDoubleAttempts") >= smartDoubleCardDailyLimit.getValue()) return;
            List<BubbleTimerTask> targets = smartDoubleTargets(System.currentTimeMillis());
            if (targets.size() < smartDoubleCardThreshold.getValue()) return;
            if (smartDoublePermanent.getValue() || smartDouble31Days.getValue() || forestPropRefill.getValue()) {
                useAdvancedSmartDouble(targets);
                return;
            }
            try {
                if (querySmartDoubleEnd() != 0) return;
                targets = confirmSmartDoubleTargets(targets);
                if (targets == null || targets.size() < smartDoubleCardThreshold.getValue()) return;
                JSONArray before = querySmartDoubleInventory();
                JSONObject prop = before == null ? null : chooseSmartDoubleCard(before);
                if (prop == null || querySmartDoubleEnd() != 0) return;
                long now = System.currentTimeMillis();
                long expiry = prop.has("recentExpireTime") ? forestFeatureLong(prop, "recentExpireTime") : 0;
                if (expiry > 0 && expiry <= now + 10000) return;
                targets.removeIf(task -> task.produceTime < now + 10000 || task.produceTime > now + 290000
                        || !Boolean.FALSE.equals(task.getIsCancel()) || !allowCollectByWhiteList(task.userId) || dontCollectMap.contains(task.userId));
                if (!smartDoubleEnabled() || !selfId.equals(UserIdMap.getCurrentUid()) || targets.size() < smartDoubleCardThreshold.getValue()) return;
                String type = prop.optString("propType"), id = prop.optJSONArray("propIdList").optString(0);
                long stock = smartDoubleStock(before, type);
                TimeUtil.sleep(0);
                Status.flagToday("forest::smartDoubleCard::" + id);
                Status.setIntFlagToday("forest::smartDoubleAttempts", Status.getIntFlagToday("forest::smartDoubleAttempts") + 1);
                Status.flagToday("forest::smartDoubleUnconfirmed");
                JSONObject ack = forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.consumeProp("doubleClick", id, type, false)));
                JSONArray after = querySmartDoubleInventory();
                long end = querySmartDoubleEnd();
                if (ack != null && after != null && stock > 0 && smartDoubleStock(after, type) == stock - 1
                        && !smartDoubleHasId(after, id) && end > System.currentTimeMillis()) {
                    Status.clearFlag("forest::smartDoubleUnconfirmed");
                    Log.forest("智能双击卡🎭[未来5分钟" + targets.size() + "个可双击好友球]#库存扣减及生效确认");
                }
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
            } catch (Throwable e) { Log.err(TAG, "smartDoubleCard err:", e); }
        }
    }

    private void scheduleSmartDoubleCheck() {
        TimeUtil.sleep(0);
        synchronized (usePropLockObj) {
            long now = System.currentTimeMillis(), earliest = Long.MAX_VALUE;
            if (smartDoubleEnabled() && selfId.equals(UserIdMap.getCurrentUid()) && !Status.hasFlagToday("forest::smartDoubleUnconfirmed")
                    && Status.getIntFlagToday("forest::smartDoubleAttempts") < smartDoubleCardDailyLimit.getValue()) {
                List<ChildModelTask> queued = getChildTaskSnapshot();
                if (queued.size() <= 2000) for (ChildModelTask child : queued) {
                    if (!(child instanceof BubbleTimerTask) || !Boolean.FALSE.equals(child.getIsCancel())) continue;
                    BubbleTimerTask task = (BubbleTimerTask) child;
                    if (task.canDouble && !selfId.equals(task.userId) && task.produceTime > now + 10000
                            && allowCollectByWhiteList(task.userId) && !dontCollectMap.contains(task.userId)) earliest = Math.min(earliest, task.produceTime);
                }
            }
            long at = earliest == Long.MAX_VALUE ? 0 : Math.max(now + 30000, earliest - 290000);
            ChildModelTask current = smartDoubleCheckId == null ? null : getChildTask(smartDoubleCheckId);
            if (at > 0 && current != null && Boolean.FALSE.equals(current.getIsCancel()) && current.getExecTime() > now && current.getExecTime() <= at) return;
            if (smartDoubleCheckId != null) { removeChildTask(smartDoubleCheckId); smartDoubleCheckId = null; }
            if (at == 0) return;
            String uid = selfId, id = "SMART_DOUBLE|" + uid + "|" + System.nanoTime();
            if (addChildTask(new ChildModelTask(id, "antForest", () -> {
                TimeUtil.sleep(0);
                synchronized (usePropLockObj) {
                    if (!id.equals(smartDoubleCheckId) || !uid.equals(UserIdMap.getCurrentUid())) return;
                    smartDoubleCheckId = null;
                    useSmartDoubleCard();
                    scheduleSmartDoubleCheck();
                }
            }, at))) smartDoubleCheckId = id;
        }
    }

    @Override protected boolean supportsManualAction(String action) { return "energyRain".equals(action) || "whackMole".equals(action); }
    @Override protected void runManualAction(String action) {
        TimeUtil.sleep(0);
        if (!isEnable() || !check()) return;
        selfId = UserIdMap.getCurrentUid();
        if (selfId == null || selfId.isEmpty()) return;
        if ("energyRain".equals(action)) energyRain();
        else if ("whackMole".equals(action)) checkAndHandleWhackMole();
    }

    private JSONArray refillForestProp(String group) {
        if (!forestPropRefill.getValue() || forestPropRefillBudget.getValue() <= 0 || hasErrorWait
                || !selfId.equals(UserIdMap.getCurrentUid())) return null;
        TimeUtil.sleep(0);
        getAllSkuInfo();
        return ForestPropSupport.replenish(group, true, vitality_ExchangeBenefitList.getValue(), skuInfo, forestPropRefillBudget.getValue());
    }

    private void useAdvancedSmartDouble(List<BubbleTimerTask> targets) {
        if (ForestExpiringProps.hasUnconfirmed("doubleClick")) return;
        try {
            long end = querySmartDoubleEnd();
            if (!ForestPropSupport.doubleRenewAllowed(end, smartDouble31Days.getValue() && smartDoubleRenew31.getValue(), System.currentTimeMillis())) return;
            targets = confirmSmartDoubleTargets(targets);
            if (targets == null || targets.size() < smartDoubleCardThreshold.getValue()) return;
            JSONArray rows = querySmartDoubleInventory();
            JSONObject prop = ForestPropSupport.chooseDouble(rows, smartDoublePermanent.getValue(), smartDouble31Days.getValue(), System.currentTimeMillis(), end);
            if (prop == null && end == 0) {
                rows = refillForestProp("doubleClick");
                prop = ForestPropSupport.chooseDouble(rows, smartDoublePermanent.getValue(), smartDouble31Days.getValue(), System.currentTimeMillis(), end);
            }
            long now = System.currentTimeMillis();
            targets.removeIf(t -> t.produceTime < now + 10000 || t.produceTime > now + 290000 || !Boolean.FALSE.equals(t.getIsCancel())
                    || !allowCollectByWhiteList(t.userId) || dontCollectMap.contains(t.userId));
            if (prop == null || !smartDoubleEnabled() || !selfId.equals(UserIdMap.getCurrentUid()) || targets.size() < smartDoubleCardThreshold.getValue()) return;
            if (ForestPropSupport.consumeDouble(prop, end, smartDouble31Days.getValue() && smartDoubleRenew31.getValue(), smartDoubleCardDailyLimit.getValue()))
                Log.forest("智能双击卡🎭[未来5分钟" + targets.size() + "个可双击好友球]#库存及生效回查成功");
        } catch (TaskCancelledException e) { throw e;
        } catch (Exception e) { Log.err(TAG, "advancedSmartDouble", e); }
    }

    /** null 表示查询失败/状态不明；空对象表示确认没有生效中的 N 倍卡。 */
    private JSONObject queryRobExpandCardState() {
        JSONObject response = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
        if (!"SUCCESS".equals(response.optString("resultCode")) || RpcRequestGuard.isFailure(response)) return null;
        JSONObject map = response.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray props = using == null ? null : using.optJSONArray("userPropVOS");
        if (props == null) return null;
        JSONObject active = null;
        long now = System.currentTimeMillis();
        for (int i = 0; i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop == null || prop.optString("propGroup").isEmpty()) return null;
            if (!"robExpandCard".equals(prop.optString("propGroup"))) continue;
            long end = forestFeatureLong(prop, "endTime");
            if (end < 0) return null;
            if (end <= now) continue;
            if (active != null || robExpandCardFactor(prop.optJSONObject("detail")) <= 0) return null;
            active = prop;
        }
        return active == null ? MyUtils.newJSONObject("{}") : active;
    }

    private static double robExpandCardFactor(JSONObject detail) {
        Object value = detail == null ? null : detail.opt("factor");
        if (!(value instanceof Number) && !(value instanceof String)) return 0;
        try {
            double factor = new java.math.BigDecimal(value.toString()).doubleValue();
            return Double.isFinite(factor) && factor > 0 ? factor : 0;
        } catch (NumberFormatException e) { return 0; }
    }

    /** 复用 AG 策略：临期高倍率优先；普通替换不缩短有效期；否则仅同倍率续用。 */
    private static JSONObject choosePreferredRobExpandCard(JSONArray props, JSONObject active, long now,
                                                          int replaceDays, int urgentDays) {
        if (props == null || active == null) return null;
        long remaining = active.length() == 0 ? 0 : forestFeatureLong(active, "endTime") - now;
        double current = robExpandCardFactor(active.optJSONObject("detail"));
        if (active.length() > 0 && (remaining <= 0 || current <= 0)) return null;
        JSONObject best = null;
        int bestRank = -1;
        double bestFactor = 0;
        long bestExpiry = Long.MAX_VALUE, bestDuration = Long.MAX_VALUE;
        for (int i = 0; i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop == null || !"robExpandCard".equals(prop.optString("propGroup"))
                    || forestFeatureLong(prop, "holdsNum") <= 0) continue;
            JSONArray ids = prop.optJSONArray("propIdList");
            JSONObject config = prop.optJSONObject("propConfigVO");
            if (ids == null || !(ids.opt(0) instanceof String) || ids.optString(0).isEmpty() || config == null
                    || !(prop.opt("propType") instanceof String || !prop.has("propType") && config.opt("propType") instanceof String)
                    || prop.optString("propType", config.optString("propType")).isEmpty()) continue;
            double factor = robExpandCardFactor(config.optJSONObject("detail"));
            long expiry = forestFeatureLong(prop, "recentExpireTime");
            long seconds = forestFeatureLong(config, "durationTime");
            long duration = seconds > 0 && seconds <= Long.MAX_VALUE / 1000 ? seconds * 1000 : 0;
            if (factor <= 0 || expiry <= now) continue;
            int rank;
            if (active.length() == 0) rank = 1;
            else if (factor > current + 0.0001) {
                if (urgentDays > 0 && expiry - now <= TimeUnit.DAYS.toMillis(urgentDays)) rank = 2;
                else if (replaceDays > 0 && remaining <= TimeUnit.DAYS.toMillis(replaceDays)
                        && duration >= remaining) rank = 1;
                else continue;
            } else if (Math.abs(factor - current) <= 0.0001 && remaining < TimeUnit.DAYS.toMillis(30)) rank = 0;
            else continue;
            // 临期/同倍率先到期先用，普通替换先倍率；相同条件用较短有效期。
            boolean better = rank > bestRank || rank == bestRank && (rank == 1
                    ? factor > bestFactor + 0.0001 || Math.abs(factor - bestFactor) <= 0.0001 && expiry < bestExpiry
                    : expiry < bestExpiry || expiry == bestExpiry && factor > bestFactor + 0.0001);
            if (!better && rank == bestRank && Math.abs(factor - bestFactor) <= 0.0001 && expiry == bestExpiry)
                better = (duration == 0 ? Long.MAX_VALUE : duration) < bestDuration;
            if (better) {
                best = prop; bestRank = rank; bestFactor = factor; bestExpiry = expiry;
                bestDuration = duration == 0 ? Long.MAX_VALUE : duration;
            }
        }
        return best;
    }

    private void usePreferredRobExpandCard() {
        if (ForestExpiringProps.hasUnconfirmed("robExpandCard")) return;
        try {
            TimeUtil.sleep(0);
            JSONObject before = queryRobExpandCardState();
            if (before == null) { Log.record("N倍卡：生效状态不明，跳过替换"); return; }
            JSONArray props = getForestPropVOList();
            JSONObject card = choosePreferredRobExpandCard(props, before, System.currentTimeMillis(),
                    robExpandCardReplaceRemainDays.getValue(), robExpandCardForceReplaceExpireDays.getValue());
            if (card == null) { Log.record("N倍卡：没有符合替换/续用条件的限时卡"); return; }
            String id = card.optJSONArray("propIdList").optString(0);
            JSONObject config = card.optJSONObject("propConfigVO");
            String type = card.optString("propType", config.optString("propType"));
            String flag = "forest::robExpandCardAttempt::" + id;
            // ponytail: 同一道具编号每天最多提交一次；不确定结果留待次日或人工核验。
            if (Status.hasFlagToday(flag)) { Log.record("N倍卡：本日已提交该道具，避免重复消耗"); return; }
            TimeUtil.sleep(0);
            Status.flagToday(flag);
            JSONObject result = MyUtils.newJSONObject(AntForestRpcCall.consumeProp("robExpandCard", id, type, false));
            JSONObject data = result.optJSONObject("resData");
            String status = (data == null ? result : data).optString("usePropStatus");
            if ("SUCCESS".equals(result.optString("resultCode")) && !RpcRequestGuard.isFailure(result)
                    && (status.startsWith("NEED_CONFIRM") || "REPLACE".equals(status))) {
                // 仅在二次确认协议明确要求且状态未变时覆盖，防止手动用卡/并行任务竞态。
                JSONObject fresh = queryRobExpandCardState();
                if (fresh == null || fresh.length() == 0 || before.length() == 0
                        || forestFeatureLong(fresh, "endTime") != forestFeatureLong(before, "endTime")
                        || Math.abs(robExpandCardFactor(fresh.optJSONObject("detail"))
                        - robExpandCardFactor(before.optJSONObject("detail"))) > 0.0001
                        || choosePreferredRobExpandCard(new JSONArray().put(card), fresh, System.currentTimeMillis(),
                        robExpandCardReplaceRemainDays.getValue(), robExpandCardForceReplaceExpireDays.getValue()) == null) {
                    Log.record("N倍卡：确认前状态改变，停止替换"); return;
                }
                TimeUtil.sleep(0);
                result = MyUtils.newJSONObject(AntForestRpcCall.consumeProp("robExpandCard", id, type, true));
            }
            JSONObject after = queryRobExpandCardState();
            double expected = robExpandCardFactor(config.optJSONObject("detail"));
            if (after != null && after.length() > 0 && Math.abs(robExpandCardFactor(after.optJSONObject("detail")) - expected) <= 0.0001
                    && (before.length() == 0 || robExpandCardFactor(after.optJSONObject("detail"))
                    > robExpandCardFactor(before.optJSONObject("detail")) + 0.0001
                    || forestFeatureLong(after, "endTime") > forestFeatureLong(before, "endTime"))) {
                Log.forest("使用道具🎭[" + config.optString("propName", "N倍卡") + "]，已回查生效");
            } else Log.record("N倍卡：使用结果未确认，本日不重复提交该道具，code=" + result.optString("resultCode", "缺失"));
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "usePreferredRobExpandCard", t); }
    }

    /**
     * 该道具类型在"当前没在使用（接口里没出现 / 已经过期）"时，是否应该启用一张新的。
     * <p>只有保护罩需要这样处理：它的意义是让能量球不被偷，过期后必须再动一张新的，否则等于没保护。
     * 原先这两种情况都返回 -1（不可用），结果**一旦过期就永远不再使用**，这正是"不自动使用保护罩"
     * 的直接原因。其余道具保持原行为（隐身卡固定不使用；双击卡/倍率卡仍按阈值接续，改动其默认行为
     * 会增加不必要的道具消耗，不在本次范围内）。
     */
    private static boolean canStartWhenNotInUse(String propGroupType) {
        return "shield".equals(propGroupType);
    }

    //判断是否可以使用道具卡片
    //返回值-1为不可用，0为可用，大于0为剩余时间
    private long continuousUseCardCheak(String propGroupType) {
        try {
            JSONObject joMiscHomes = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return -1;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return -1;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap == null || !combineHandlerVOMap.has("usingProp")) {
                // 当前没有任何道具在使用：保护罩要能从头用一张，其余道具保持原行为（不可用）
                return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : -1;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (usingProp == null || !usingProp.has("userPropVOS")) {
                return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : -1;
            }
            JSONArray userPropVOS = usingProp.optJSONArray("userPropVOS");
            for (int i = 0; userPropVOS != null && i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.optJSONObject(i);
                if (userPropVO == null) {
                    continue;
                }
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals(propGroupType)) {
                    long endTime = userPropVO.optLong("endTime");
                    long duringTime = endTime - now;
                    if (duringTime < 0) {
                        // 用过的道具已过期（接口里仍留着这条记录）：保护罩应重新启用一张
                        return canStartWhenNotInUse(propGroupType) ? 0 : -1;
                    }
                    switch (propGroupType) {
                        case "doubleClick":
                            if (duringTime / (1000 * 60) < 60 * 24 * 31) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "robExpandCard":
                            if (duringTime / (1000 * 60) < 60 * 24 * 30) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "stealthCard":
                            return -1;
                        case "shield":
                            if (duringTime / (1000 * 60) < 60 * continuousUseShieldHour.getValue()) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        /*case "energyBombCard":
                            if (duringTime / (1000 * 60) < 3*60 * 24) {
                                Log.forest("duringTime");
                                return duringTime;
                            }
                            else {
                                return -1;
                            }*/

                    }
                }
            }
            // 走到这里说明 usingProp 列表里没有该道具：保护罩实测就不在其中，改按主页真实到期时间判断
            return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : 0;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return -1;
    }

    /**
     * 保护罩在道具接口里查不到时的兜底：按个人主页的到期时间判断是否续用
     * （返回值同 {@link #continuousUseCardCheak}：-1 不可用 / 0 无保护可用 / >0 剩余毫秒）。
     */
    private long shieldFallback(long now) {
        long endTime = queryShieldEndTime();
        if (endTime < 0) return -1;
        long duringTime = endTime - now;
        if (duringTime <= 0) {
            return 0;
        }
        return duringTime / (1000 * 60) < 60 * continuousUseShieldHour.getValue() ? duringTime : -1;
    }

    /**
     * 保护罩的真实到期时间（毫秒），未在保护中返回 0。
     * <p>{@code queryMiscInfo} 的 usingProp 实测不下发 shield，只能从个人主页的 usingUserPropsNew 取。
     */
    private long queryShieldEndTime() {
        try {
            JSONObject joHomePage = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, joHomePage)) {
                return -1;
            }
            JSONArray ja = joHomePage.optJSONArray("loginUserUsingPropNew");
            if (ja == null || ja.length() == 0) {
                ja = joHomePage.optJSONArray("usingUserPropsNew");
            }
            if (ja == null) {
                return 0;
            }
            long endTime = 0;
            for (int i = 0; i < ja.length(); i++) {
                JSONObject prop = ja.optJSONObject(i);
                if (prop == null) continue;
                if ("shield".equals(prop.optString("propGroup"))) {
                    if (!(prop.opt("endTime") instanceof Number)) return -1;
                    endTime = Math.max(endTime, prop.optLong("endTime"));
                }
            }
            return endTime;
        } catch (Throwable th) {
            Log.err(TAG, "queryShieldEndTime err:", th);
        }
        return -1;
    }

    private String useRobExpandCardFactor() {
        try {
            JSONObject joMiscHomes = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return null;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return null;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap == null || !combineHandlerVOMap.has("usingProp")) {
                return null;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (usingProp == null || !usingProp.has("userPropVOS")) {
                return null;
            }
            JSONArray userPropVOS = usingProp.optJSONArray("userPropVOS");
            for (int i = 0; userPropVOS != null && i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.optJSONObject(i);
                if (userPropVO == null) {
                    continue;
                }
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals("robExpandCard")) {
                    if (!userPropVO.has("detail")) {
                        return null;
                    }
                    JSONObject detail = userPropVO.optJSONObject("detail");
                    return detail != null ? detail.optString("factor") : null;
                }
            }
            return null;
        } catch (Throwable th) {
            Log.err(TAG, "UserobExpandCardFactor err:", th);
        }
        return null;
    }


    //选出可以使用的限时道具卡片
    private JSONObject chooseContinuousLIMITTIMECard(String propGroupType) {
        try {
            JSONArray forestPropVOList = getForestPropVOList();
            JSONObject rightCard = null;
            String useFactor = useRobExpandCardFactor();
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestBagProp = forestPropVOList.optJSONObject(i);
                if (forestBagProp == null) {
                    continue;
                }
                String propGroup = forestBagProp.optString("propGroup");
                if (forestBagProp.has("recentExpireTime") && propGroup.equals(propGroupType)) {
                    switch (propGroup) {
                        case "stealthCard":
                        case "shield":
                        case "doubleClick":
                            //case "energyBombCard":
                            if (rightCard != null) {
                                long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                    rightCard = forestBagProp;
                                }
                            } else {
                                rightCard = forestBagProp;
                            }
                            break;
                        case "robExpandCard":
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                String factorforestBagProp = JsonUtil.getValueByPath(forestBagProp, "propConfigVO.detail.factor");
                                if (factorforestBagProp == null) continue;
                                if (factorforestBagProp.equals(useFactor)) {
                                    if (rightCard != null) {
                                        String factorrightCard = JsonUtil.getValueByPath(rightCard, "propConfigVO.detail.factor");
                                        long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                        long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                        if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                            if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                                rightCard = forestBagProp;
                                            }
                                        }
                                    } else {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    String factorrightCard = JsonUtil.getValueByPath(rightCard, "propConfigVO.detail.factor");
                                    String factorforestBagProp = JsonUtil.getValueByPath(forestBagProp, "propConfigVO.detail.factor");
                                if (factorforestBagProp == null) continue;
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }
                            /*

                            String factorrightCard = JsonUtil.getValueByPath(rightCard, "propConfigVO.detail.factor");
                            String factorforestBagProp = JsonUtil.getValueByPath(forestBagProp, "propConfigVO.detail.factor");
                                if (factorforestBagProp == null) continue;
                            long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                            long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                            Log.forest("factorrightCard:"+factorrightCard);
                            Log.forest("factorforestBagProp:"+factorforestBagProp);
                            Log.forest("recentExpireTimerightCard:"+recentExpireTimerightCard);
                            Log.forest("recentExpireTimeforestBagProp:"+recentExpireTimeforestBagProp);
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                    if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }*/
                    }
                }
            }
            // 保护罩的永久卡（ENERGY_SHIELD）服务端不下发 recentExpireTime，会被上面按到期时间的筛选排除，
            // 导致背包里有罩也一张都用不了，故补一次兜底；限时保护罩仍优先于它
            if (rightCard == null && "shield".equals(propGroupType)) {
                for (int i = 0; i < forestPropVOList.length(); i++) {
                    JSONObject forestBagProp = forestPropVOList.optJSONObject(i);
                    if (forestBagProp == null) continue;
                    if ("shield".equals(forestBagProp.optString("propGroup")) && !forestBagProp.has("recentExpireTime")) {
                        rightCard = forestBagProp;
                        break;
                    }
                }
            }
            return rightCard;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return null;
    }

    private void usePropBeforeCollectEnergy(String userId) {
        if (Objects.equals(selfId, userId)) {
            return;
        }
        if (needDoubleClick()) {
            synchronized (usePropLockObj) {
                if (needDoubleClick()) {
                    useDoubleCard(getForestPropVOList());
                }
            }
        }
    }

    private Boolean needDoubleClick() {
        // 双击卡配置已停用（addField 被注释）→ 字段为 null，按"关闭"处理，避免 NPE
        if (doubleClickType == null || doubleClickType.getValue() == UsePropType.CLOSE) {
            return false;
        }
        Long doubleClickEndTime = usingProps.get(PropGroup.doubleClick.name());
        if (doubleClickEndTime == null) {
            return true;
        }
        return doubleClickEndTime < System.currentTimeMillis();
    }

    private void useDoubleCard(JSONArray forestPropVOList) {
        try {
            if (hasDoubleCardTime() && Status.canDoubleToday()) {
                // 背包查找 能量双击卡
                JSONObject jo = null;
                List<JSONObject> list = getPropGroup(forestPropVOList, PropGroup.doubleClick.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null || !jo.has("recentExpireTime")) {
                    // 配置已停用则字段为 null，按"关闭"处理
                    if (doubleCardConstant != null && doubleCardConstant.getValue()) {
                        // 商店兑换 限时能量双击卡
                        if (exchangeBenefit("SK20240805004754")) {
                            jo = getForestPropVO(getForestPropVOList(), "ENERGY_DOUBLE_CLICK_31DAYS");
                        } else if (exchangeBenefit("CR20230516000363")) {
                            jo = getForestPropVO(getForestPropVOList(), "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
                        }
                    }
                }
                if (jo == null) {
                    return;
                }
                if (!jo.has("recentExpireTime")
                        && (doubleClickType == null || doubleClickType.getValue() == UsePropType.ONLY_LIMIT_TIME)) {
                    return;
                }
                // 使用能量双击卡
                if (consumeProp(jo)) {
                    JSONObject propConfigVO = jo.optJSONObject("propConfigVO");
                    long durationTime = propConfigVO != null ? propConfigVO.optLong("durationTime") : 0;
                    Long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime);
                    usingProps.put(PropGroup.doubleClick.name(), endTime);
                    Status.DoubleToday();
                } else {
                    updateUsingPropsEndTime();
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
    }

    private boolean hasDoubleCardTime() {
        // 配置已停用（addField 被注释）→ 字段为 null，视为"不在使用时间范围"
        if (doubleCardTime == null) {
            return false;
        }
        long currentTimeMillis = System.currentTimeMillis();
        return TimeUtil.checkInTimeRange(currentTimeMillis, doubleCardTime.getValue());
    }

    /* 赠送道具 */
    private void giveProp() {
        Set<String> set = whoYouWantToGiveTo.getValue();
        if (set.isEmpty()) {
            return;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            giveProp(userId);
            break;
        }
    }

    private void giveProp(String targetUserId) {
        try {
            do {
                try {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPropList(true));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray forestPropVOList = jo.optJSONArray("forestPropVOList");
                    if (forestPropVOList != null && forestPropVOList.length() > 0) {
                        jo = forestPropVOList.optJSONObject(0);
                        if (jo == null) {
                            break;
                        }
                        JSONObject giveConfigVO = jo.optJSONObject("giveConfigVO");
                        JSONObject propConfigVO = jo.optJSONObject("propConfigVO");
                        JSONArray propIdList = jo.optJSONArray("propIdList");
                        if (giveConfigVO == null || propConfigVO == null || propIdList == null || propIdList.length() == 0) {
                            break;
                        }
                        String giveConfigId = giveConfigVO.optString("giveConfigId");
                        int holdsNum = jo.optInt("holdsNum", 0);
                        String propName = propConfigVO.optString("propName");
                        String propId = propIdList.optString(0);
                        jo = MyUtils.newJSONObject(AntForestRpcCall.giveProp(giveConfigId, propId, targetUserId));
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            Log.forest("赠送道具🎭[" + UserIdMap.getMaskName(targetUserId) + "]#" + propName);
                            if (holdsNum > 1 || forestPropVOList.length() > 1) {
                                continue;
                            }
                        }
                    }
                } finally {
                    TimeUtil.sleep(1500);
                }
                break;
            } while (true);
        } catch (Throwable th) {
            Log.err(TAG, "giveProp err:", th);
        }
    }

    /**
     * 绿色行动
     */
    private void ecoLife() {
        try {
            JSONObject jo = MyUtils.newJSONObject(EcoLifeRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return;
            }
            if (!data.optBoolean("openStatus")) {
                Log.forest("绿色任务☘未开通");
                jo = MyUtils.newJSONObject(EcoLifeRpcCall.openEcolife());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                if (!String.valueOf(true).equals(JsonUtil.getValueByPath(jo, "data.opResult"))) {
                    return;
                }
                Log.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了");
                jo = MyUtils.newJSONObject(EcoLifeRpcCall.queryHomePage());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                data = jo.optJSONObject("data");
                if (data == null) {
                    return;
                }
            }
            String dayPoint = data.optString("dayPoint");
            JSONArray actionListVO = data.optJSONArray("actionListVO");
            if (actionListVO == null) {
                return;
            }
            if (ecoLifeOptions.getValue().contains("dish")) {
                photoGuangPan(dayPoint);
            }
            if (ecoLifeOptions.getValue().contains("tick")) {
                ecoLifeTick(actionListVO, dayPoint);
            }
        } catch (Throwable th) {
            Log.err(TAG, "ecoLife err:", th);
        }
    }

    /* 绿色行动打卡 */

    private void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
        try {
            String source = "source";
            for (int i = 0; i < actionListVO.length(); i++) {
                JSONObject actionVO = actionListVO.optJSONObject(i);
                JSONArray actionItemList = actionVO != null ? actionVO.optJSONArray("actionItemList") : null;
                if (actionItemList == null) {
                    continue;
                }
                for (int j = 0; j < actionItemList.length(); j++) {
                    JSONObject actionItem = actionItemList.optJSONObject(j);
                    if (actionItem == null || !actionItem.has("actionId")) {
                        continue;
                    }
                    if (actionItem.optBoolean("actionStatus")) {
                        continue;
                    }
                    String actionId = actionItem.optString("actionId");
                    String actionName = actionItem.optString("actionName");
                    if ("photoguangpan".equals(actionId)) {
                        continue;
                    }
                    JSONObject jo = MyUtils.newJSONObject(EcoLifeRpcCall.tick(actionId, dayPoint, source));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("绿色打卡🍀[" + actionName + "]");
                    }
                    TimeUtil.sleep(500);
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "ecoLifeTick err:", th);
        }
    }

    /**
     * 光盘行动
     */
    private void photoGuangPan(String dayPoint) {
        // if (!TaskCommon.IS_AFTER_6AM) {
        //    return;
        // }
        try {
            String source = "renwuGD";
            // 检查今日任务状态
            JSONObject jo = MyUtils.newJSONObject(EcoLifeRpcCall.queryDish(source, dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 更新光盘照片
            Map<String, String> dishImage = new HashMap<>();
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                String beforeMealsImageUrl = data.optString("beforeMealsImageUrl");
                String afterMealsImageUrl = data.optString("afterMealsImageUrl");
                if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(afterMealsImageUrl)) {
                    Pattern pattern = Pattern.compile("img/(.*)/original");
                    Matcher beforeMatcher = pattern.matcher(beforeMealsImageUrl);
                    if (beforeMatcher.find()) {
                        dishImage.put("BEFORE_MEALS", beforeMatcher.group(1));
                    }
                    Matcher afterMatcher = pattern.matcher(afterMealsImageUrl);
                    if (afterMatcher.find()) {
                        dishImage.put("AFTER_MEALS", afterMatcher.group(1));
                    }
                    TokenConfig.saveDishImage(dishImage);
                }
            }
            if (data != null && Objects.equals("SUCCESS", data.optString("status"))) {
                // Log.forest("光盘行动💿今日打卡已完成");
                return;
            }

            dishImage = TokenConfig.getRandomDishImage();
            if (dishImage == null) {
                Log.forest("光盘行动💿请先完成一次光盘打卡");
                return;
            }
            // 上传餐前照片
            jo = MyUtils.newJSONObject(EcoLifeRpcCall.uploadBeforeMealsDishImage(dishImage.get("BEFORE_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 上传餐后照片
            jo = MyUtils.newJSONObject(EcoLifeRpcCall.uploadAfterMealsDishImage(dishImage.get("AFTER_MEALS"), dayPoint));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            // 提交
            jo = MyUtils.newJSONObject(EcoLifeRpcCall.tick("photoguangpan", dayPoint, source));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data2 = jo.optJSONObject("data");
            String toastMsg = data2 != null ? data2.optString("toastMsg") : "";
            Toast.show("光盘行动💿打卡完成#" + toastMsg);
            Log.forest("光盘行动💿打卡完成#" + toastMsg + "");
        } catch (Throwable t) {
            Log.err(TAG, "photoGuangPan err:", t);
        }
    }

    private void queryUserPatrol() {
        try {
            th:
            do {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryUserPatrol());
                TimeUtil.sleep(500);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.queryMyPatrolRecord());
                TimeUtil.sleep(500);
                if (resData.optBoolean("canSwitch")) {
                    JSONArray records = resData.optJSONArray("records");
                    for (int i = 0; records != null && i < records.length(); i++) {
                        JSONObject record = records.optJSONObject(i);
                        JSONObject userPatrol = record != null ? record.optJSONObject("userPatrol") : null;
                        if (userPatrol == null) {
                            continue;
                        }
                        if (userPatrol.optInt("unreachedNodeCount") > 0) {
                            if ("silent".equals(userPatrol.optString("mode"))) {
                                JSONObject patrolConfig = record.optJSONObject("patrolConfig");
                                String patrolId = patrolConfig != null ? patrolConfig.optString("patrolId") : "";
                                resData = MyUtils.newJSONObject(AntForestRpcCall.switchUserPatrol(patrolId));
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, resData)) {
                                    Log.i("巡护⚖️-切换地图至" + patrolId);
                                }
                                continue th;
                            }
                            break;
                        }
                    }
                }

                JSONObject userPatrol = jo.optJSONObject("userPatrol");
                if (userPatrol == null) {
                    return;
                }
                int currentNode = userPatrol.optInt("currentNode");
                String currentStatus = userPatrol.optString("currentStatus");
                int patrolId = userPatrol.optInt("patrolId");
                JSONObject chance = userPatrol.optJSONObject("chance");
                if (chance == null) {
                    return;
                }
                int leftChance = chance.optInt("leftChance");
                int leftStep = chance.optInt("leftStep");
                int usedStep = chance.optInt("usedStep");
                if ("STANDING".equals(currentStatus)) {
                    if (leftChance > 0) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.patrolGo(currentNode, patrolId));
                        TimeUtil.sleep(500);
                        patrolKeepGoing(jo.toString(), currentNode, patrolId);
                        continue;
                    } else if (leftStep >= 2000 && usedStep < 10000) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.exchangePatrolChance(leftStep));
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            int addedChance = jo.optInt("addedChance", 0);
                            Log.forest("步数兑换⚖️[巡护次数*" + addedChance + "]");
                            continue;
                        }
                    }
                } else if ("GOING".equals(currentStatus)) {
                    patrolKeepGoing(null, currentNode, patrolId);
                }
                break;
            } while (true);
        } catch (Throwable t) {
            Log.err(TAG, "queryUserPatrol err:", t);
        }
    }

    private void patrolKeepGoing(String s, int nodeIndex, int patrolId) {
        try {
            do {
                if (s == null) {
                    s = AntForestRpcCall.patrolKeepGoing(nodeIndex, patrolId, "image");
                }
                JSONObject jo = MyUtils.newJSONObject(s);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray jaEvents = jo.optJSONArray("events");
                if (jaEvents == null || jaEvents.length() == 0) {
                    return;
                }
                JSONObject userPatrol = jo.optJSONObject("userPatrol");
                int currentNode = userPatrol != null ? userPatrol.optInt("currentNode") : nodeIndex;
                JSONObject events = jaEvents.optJSONObject(0);
                if (events == null) {
                    return;
                }
                JSONObject rewardInfo = events.optJSONObject("rewardInfo");
                if (rewardInfo != null) {
                    JSONObject animalProp = rewardInfo.optJSONObject("animalProp");
                    if (animalProp != null) {
                        JSONObject animal = animalProp.optJSONObject("animal");
                        if (animal != null) {
                            Log.forest("巡护森林🏇🏻[" + animal.optString("name") + "碎片]");
                        }
                    }
                }
                if (!"GOING".equals(jo.optString("currentStatus"))) {
                    return;
                }
                JSONObject materialInfo = events.optJSONObject("materialInfo");
                String materialType = materialInfo != null ? materialInfo.optString("materialType", "image") : "image";
                s = AntForestRpcCall.patrolKeepGoing(currentNode, patrolId, materialType);
                TimeUtil.sleep(100);
            } while (true);
        } catch (Throwable t) {
            Log.err(TAG, "patrolKeepGoing err:", t);
        }
    }

    /* 新版保护地：自动前进（领取首页机会 + 掷骰推进 + 事件确认） */
    private void monopolyPatrol() {
        try {
            JSONObject state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            JSONObject mapInfo = state.optJSONObject("mapInfo");
            JSONObject userInfo = state.optJSONObject("userInfo");
            if (regionInfo == null || mapInfo == null || userInfo == null) {
                Log.record("新版保护地🌲入口缺少区域、地图或用户状态，跳过本轮");
                return;
            }
            if (isMonopolyClosed(regionInfo) || isMonopolyClosed(mapInfo)) {
                Log.forest("新版保护地🌲当前区域或地图不在开放期，停止本轮");
                return;
            }
            String regionCode = regionInfo.optString("regionCode");
            boolean tasksEnabled = monopolyTasks.getValue();
            // 掷骰会解锁新任务，所以骰子用完后需要再补跑一次任务
            boolean tasksMayHaveChanged = false;
            if (tasksEnabled) {
                monopolyTask(regionCode);
            }
            // 首次进入地图时服务端要求带引导参数掷骰
            boolean guideRoll = userInfo.optBoolean("firstEnterMonopoly");
            JSONObject pendingEvent = state.optJSONObject("eventInfo");

            // 首页道具：领取当日巡护机会（骰子）
            JSONObject props = MyUtils.newJSONObject(AntForestRpcCall.triggerMonopolyHomeProps());
            if (!MessageUtil.checkResultCode(TAG, props)) {
                return;
            }
            state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            if (pendingEvent == null) {
                pendingEvent = state.optJSONObject("eventInfo");
            }

            Set<String> confirmedEvents = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                JSONObject event = pendingEvent;
                if (event != null && event.optBoolean("needConfirm")) {
                    String eventId = event.optString("eventId");
                    String eventType = event.optString("eventType");
                    String actionKey = "CHARITY".equals(eventType) ? "confirm" : ("SPECIAL".equals(eventType) ? "skip" : null);
                    JSONObject displayInfo = event.optJSONObject("displayInfo");
                    if (eventId.isEmpty() || actionKey == null || displayInfo == null
                            || !"SINGLE_ACTION_CONFIRM".equals(displayInfo.optString("flowType"))) {
                        Log.record("新版保护地🌲事件缺少可执行决策，保留当前事件[" + eventId + "]");
                        return;
                    }
                    if (confirmedEvents.contains(eventId)) {
                        Log.record("新版保护地🌲事件[" + eventId + "]已提交确认，等待后续调度刷新");
                        return;
                    }
                    JSONObject confirmation = MyUtils.newJSONObject(AntForestRpcCall.confirmMonopolyEvent(eventId, actionKey));
                    if (!MessageUtil.checkResultCode(TAG, confirmation)) {
                        return;
                    }
                    confirmedEvents.add(eventId);
                    Log.forest("新版保护地🌲事件[" + eventId + "]已确认");
                    state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                    if (!MessageUtil.checkResultCode(TAG, state)) {
                        return;
                    }
                    pendingEvent = state.optJSONObject("eventInfo");
                    continue;
                }

                int diceCount = state.optInt("totalDiceCount", -1);
                if (diceCount < 0) {
                    Log.record("新版保护地🌲响应缺少可用骰子数量，跳过本轮");
                    return;
                }
                if (diceCount == 0) {
                    if (tasksEnabled && tasksMayHaveChanged) {
                        monopolyTask(regionCode);
                        tasksMayHaveChanged = false;
                        state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                        if (!MessageUtil.checkResultCode(TAG, state)) {
                            return;
                        }
                        pendingEvent = state.optJSONObject("eventInfo");
                        continue;
                    }
                    Log.record("新版保护地🌲当前无可用骰子，后续调度继续查询");
                    return;
                }

                JSONObject currentUserInfo = state.optJSONObject("userInfo");
                int previousSteps = currentUserInfo == null ? -1 : currentUserInfo.optInt("stepCount", -1);
                JSONObject roll = MyUtils.newJSONObject(AntForestRpcCall.rollMonopolyDice(guideRoll));
                if (!MessageUtil.checkResultCode(TAG, roll)) {
                    return;
                }
                guideRoll = false;
                pendingEvent = roll.optJSONObject("eventInfo");
                JSONObject rollUserInfo = roll.optJSONObject("userInfo");
                int steps = rollUserInfo == null ? -1 : rollUserInfo.optInt("stepCount", -1);
                // 骰子数、步数、事件三者都没变化，说明服务端没受理，留到下次调度重试
                if (roll.optInt("totalDiceCount", -1) == diceCount && steps == previousSteps && pendingEvent == null) {
                    Log.record("新版保护地🌲掷骰后未确认状态变化，保留后续调度");
                    return;
                }
                tasksMayHaveChanged = true;
                Log.forest("新版保护地🌲掷骰[" + roll.optInt("diceNumber") + "]剩余" + roll.optInt("totalDiceCount") + "次");
                TimeUtil.sleep(500);
                state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                if (!MessageUtil.checkResultCode(TAG, state)) {
                    return;
                }
                if (pendingEvent == null) {
                    pendingEvent = state.optJSONObject("eventInfo");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyPatrol err:", t);
        }
    }

    /* 新版保护地：区域/地图是否未开放或已结束（缺少日期字段视为一直开放） */
    private boolean isMonopolyClosed(JSONObject activity) {
        long now = System.currentTimeMillis();
        long start = parseMonopolyDate(activity.optString("startDate"));
        long end = parseMonopolyDate(activity.optString("endDate"));
        return (start > 0 && now < start) || (end > 0 && now >= end);
    }

    /* 解析新版保护地的 "yyyy-MM-dd HH:mm:ss" 时间串（东八区），解析失败返回 0 */
    private long parseMonopolyDate(String date) {
        if (StringUtil.isEmpty(date)) {
            return 0L;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    /* 新版保护地部分接口的响应会被包在 resData 里 */
    private JSONObject monopolyResponse(String raw) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(raw);
        JSONObject resData = jo.optJSONObject("resData");
        return resData == null ? jo : resData;
    }

    /* 新版保护地：完成可自动执行的任务并领取奖励 */
    private boolean monopolyTask(String regionCode) {
        String sceneCode;
        if (MONOPOLY_REGION_HSDWY.equals(regionCode)) {
            sceneCode = MONOPOLY_TASK_SCENE_HSDWY;
        } else {
            Log.forest("新版保护地🌲当前区域[" + regionCode + "]没有任务场景，继续地图巡护");
            return false;
        }
        boolean changed = false;
        try {
            // 每轮只处理一个任务，最多 20 轮：服务端状态不变时靠轮次上限收敛
            for (int round = 0; round < 20; round++) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, sceneCode));
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return changed;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null) {
                    Log.record("新版保护地🌲任务列表为空，跳过");
                    return changed;
                }
                boolean acted = false;
                for (int i = 0; i < taskInfoList.length() && !acted; i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                    if (base == null || !sceneCode.equals(base.optString("sceneCode"))) {
                        continue;
                    }
                    String taskType = base.optString("taskType");
                    if (taskType.isEmpty()) {
                        continue;
                    }
                    String title = monopolyTaskTitle(base, taskType);
                    String status = base.optString("taskStatus");
                    // 黑名单任务静默跳过；已完成待领奖的仍把奖励领掉（与森林任务一致）
                    if (isMonopolyTaskBlacklisted(title) && !"FINISHED".equals(status)) {
                        continue;
                    }
                    if ("FINISHED".equals(status)) {
                        acted = true;
                        changed |= monopolyReceiveTaskAward(taskType, sceneCode, title);
                    } else if ("TODO".equals(status) && "NORMAL".equals(base.optString("taskMode"))
                            && "VISIT_FLOAT_BALL".equals(base.optString("taskProdPlayType"))) {
                        // 只自动做"浏览浮球"这类纯等待任务，其余需要真实业务动作的任务不碰
                        acted = true;
                        changed |= monopolyFinishTask(base, taskType, sceneCode, title);
                    }
                }
                if (!acted) {
                    return changed;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyTask err:", t);
        }
        return changed;
    }

    /* 新版保护地任务展示名：bizInfo.title → bizInfo.taskTitle → 任务类型（各接口键名不统一） */
    private String monopolyTaskTitle(JSONObject base, String taskType) {
        String bizInfo = base.optString("bizInfo");
        if (!bizInfo.isEmpty()) {
            try {
                JSONObject biz = MyUtils.newJSONObject(bizInfo);
                String title = biz.optString("title");
                if (title.isEmpty()) {
                    title = biz.optString("taskTitle");
                }
                if (!title.isEmpty()) {
                    return title;
                }
            } catch (Throwable t) {
                Log.err(TAG, "monopolyTaskTitle err:", t);
            }
        }
        return taskType;
    }

    /* 新版保护地任务是否在配置的黑名单里（键统一剥掉标题末尾的 "(n/N)" 次数后缀） */
    private boolean isMonopolyTaskBlacklisted(String title) {
        Set<String> blackList = MonopolyTaskList.getValue();
        return blackList != null && blackList.contains(blackTaskKey(title));
    }

    /* 失败响应按统一判据自动拉黑（可重试错误不会拉黑），受「自动黑名单」开关控制 */
    private void markMonopolyTaskBlackList(String title, JSONObject jo) {
        if (Boolean.TRUE.equals(AutoMonopolyTaskList.getValue())) {
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MonopolyTaskList", blackTaskKey(title), jo);
        }
    }

    /* 新版保护地：按服务端下发的时长等待后提交任务完成 */
    private boolean monopolyFinishTask(JSONObject base, String taskType, String sceneCode, String title) {
        try {
            long seconds = 0L;
            String prodPlayParam = base.optString("prodPlayParam");
            if (!prodPlayParam.isEmpty()) {
                seconds = MyUtils.newJSONObject(prodPlayParam).optLong("timeCount", 0L);
            }
            if (seconds <= 0 || seconds > MONOPOLY_MAX_TASK_SECONDS) {
                Log.record("新版保护地🌲任务[" + title + "]下发时长异常(" + seconds + "s)，跳过");
                return false;
            }
            TimeUtil.sleep(TimeUnit.SECONDS.toMillis(seconds));
            JSONObject jo = monopolyResponse(AntForestRpcCall.finishMonopolyTask(taskType, sceneCode));
            markMonopolyTaskBlackList(title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("新版保护地🌲任务完成[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyFinishTask err:", t);
        }
        return false;
    }

    /* 新版保护地：领取任务奖励 */
    private boolean monopolyReceiveTaskAward(String taskType, String sceneCode, String title) {
        try {
            JSONObject jo = monopolyResponse(AntForestRpcCall.receiveMonopolyTask(taskType, sceneCode));
            markMonopolyTaskBlackList(title, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("新版保护地🌲任务奖励[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "monopolyReceiveTaskAward err:", t);
        }
        return false;
    }

    /* 新版动物伙伴：领取已产生的派遣能量 */
    private void collectMonopolyAnimalEnergy() {
        try {
            String uid = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(uid)) {
                return;
            }
            JSONObject jo = monopolyResponse(AntForestRpcCall.queryUsingCreatureInfo(uid));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject creature = jo.optJSONObject("userCreatureVO");
            if (creature == null) {
                return;
            }
            JSONObject energy = creature.optJSONObject("robEnergyVO");
            if (energy == null) {
                Log.record("新版动物伙伴🦩缺少能量状态，跳过");
                return;
            }
            if (energy.optBoolean("energyIsCollect")) {
                return;
            }
            if (energy.optInt("yesterdayRobEnergy", 0) <= 0) {
                // 昨日没有产出能量，属于正常情况，不打扰用户
                return;
            }
            String creatureCode = creature.optString("creatureCode");
            String shortDay = energy.optString("yesterdayShortDay");
            if (!energy.has("energyIsCollect") || creatureCode.isEmpty() || shortDay.isEmpty()) {
                Log.record("新版动物伙伴🦩能量缺少领取标识，跳过");
                return;
            }
            JSONObject collect = monopolyResponse(AntForestRpcCall.collectMonopolyCreatureEnergy(creatureCode, shortDay));
            if (!MessageUtil.checkResultCode(TAG, collect)) {
                return;
            }
            int collected = collect.optInt("collectedEnergy", -1);
            if (collected < 0) {
                Log.forest("新版动物伙伴🦩领取成功但缺少实际到账量，不计入统计");
                return;
            }
            if (collected > 0) {
                Statistics.addData(Statistics.DataType.COLLECTED, collected);
            }
            Log.forest("新版动物伙伴🦩[" + creature.optString("creatureName", creatureCode) + "]派遣能量[" + collected + "g]");
        } catch (Throwable t) {
            Log.err(TAG, "collectMonopolyAnimalEnergy err:", t);
        }
    }

    private int friendWaterEnergy(String uid, int defaultGrams) {
        Integer grams = waterFriendGramList.getValue().get(uid);
        if (grams == null || grams == 0) return defaultGrams;
        if (grams == 10 || grams == 18 || grams == 33 || grams == 66) return grams;
        Log.record("好友浇水：独立克数无效[" + grams + "]，请填写10、18、33、66；跳过该好友");
        return 0;
    }

    static long forestFeatureLong(JSONObject object, String key) {
        Object value = object.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try {
            long number = new java.math.BigDecimal(value.toString()).longValueExact();
            return number >= 0 ? number : -1;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private void exchangeMonopolyCertificate() {
        try {
            TimeUtil.sleep(0);
            JSONObject entry = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!"SUCCESS".equals(entry.optString("resultCode")) || RpcRequestGuard.isFailure(entry)) { Log.record("保护证书：入口查询失败，" + RpcRequestGuard.errorMessage(entry)); return; }
            JSONObject display = entry.optJSONObject("displayInfo");
            JSONObject map = display == null ? null : display.optJSONObject("mapDisplay");
            JSONObject guide = map == null ? null : map.optJSONObject("protectionGuideDisplay");
            String url = guide == null ? "" : guide.optString("redirectUrl");
            if (url == null || url.isEmpty()) { Log.record("保护证书：当前地图没有证书项目"); return; }
            android.net.Uri uri = android.net.Uri.parse(url);
            String id = uri.getQueryParameter("projectId");
            String nested = uri.getQueryParameter("url");
            if (id == null && nested != null) id = android.net.Uri.parse(nested).getQueryParameter("projectId");
            if (id == null || !id.matches("[0-9]+")) { Log.record("保护证书：入口缺少有效项目编号，跳过"); return; }
            JSONObject before = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyCertificate(id));
            if (!"SUCCESS".equals(before.optString("resultCode")) || RpcRequestGuard.isFailure(before)) { Log.record("保护证书：资格查询失败，" + RpcRequestGuard.errorMessage(before)); return; }
            JSONObject project = before.optJSONObject("exchangeableTree");
            if (project == null || forestFeatureLong(project, "projectId") != Long.parseLong(id)) { Log.record("保护证书：项目数据缺失或不符，跳过"); return; }
            long certCount = forestFeatureLong(project, "certCount");
            if (certCount < 0) { Log.record("保护证书：缺少持有数量，跳过"); return; }
            if (!"AVAILABLE".equals(before.optString("applyAction")) || certCount > 0) { Log.record("保护证书：已持有或当前不可兑换"); return; }
            long cost = forestFeatureLong(project, "energy"), balance = forestFeatureLong(before, "currentEnergy");
            if (cost < 0 || balance < cost || project.optBoolean("overLimit") || !project.optBoolean("hasBudget", true)) {
                Log.record("保护证书：能量或额度不足，成本=" + cost + "g，余额=" + balance + "g"); return;
            }
            String flag = "forest::certificateUnconfirmed::" + id;
            if (Status.hasFlagToday(flag)) { Log.record("保护证书：本日已提交但结果未确认，避免重复扣能量"); return; }
            // 请求发出后即占用一次预算；响应丢失时靠回查确认，不重复提交。
            Status.flagToday(flag);
            JSONObject exchanged = MyUtils.newJSONObject(AntForestRpcCall.exchangeMonopolyCertificate(Long.parseLong(id)));
            JSONObject after = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyCertificate(id));
            JSONObject afterProject = after.optJSONObject("exchangeableTree");
            if ("SUCCESS".equals(after.optString("resultCode")) && !RpcRequestGuard.isFailure(after) && afterProject != null && forestFeatureLong(afterProject, "projectId") == Long.parseLong(id)
                    && forestFeatureLong(afterProject, "certCount") > certCount) {
                String message = "保护证书：兑换成功[" + project.optString("projectName", "当前地图") + "]，成本=" + cost + "g";
                Log.record(message); Log.forest(message);
            } else { Log.record("保护证书：兑换未确认，code=" + exchanged.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(exchanged)); }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("保护证书：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "exchangeMonopolyCertificate", t); }
    }

    private void waterMemberPlant() {
        try {
            JSONObject response = MyUtils.newJSONObject(AntForestRpcCall.memberForestSignin("plant_black_v2"));
            // ponytail: 每轮最多50次浇水；需要更大额度时由下一轮继续。
            for (int round = 0; round < 50; round++) {
                TimeUtil.sleep(0);
                if (!Boolean.TRUE.equals(response.opt("success")) || !"SUCCESS".equals(response.optString("resultCode"))) {
                    Log.record("会员绿植：状态查询失败，" + RpcRequestGuard.errorMessage(response)); return;
                }
                JSONObject data = response.optJSONObject("resultData");
                JSONObject channel = data == null ? null : data.optJSONObject("plantChannelResponse");
                JSONObject plant = data == null ? null : data.optJSONObject("plantInfo");
                if (channel == null || !Boolean.TRUE.equals(channel.opt("access")) || plant == null || !"in_progress".equals(plant.optString("status"))) {
                    Log.record("会员绿植：活动未开通或当前没有待浇水绿植"); return;
                }
                JSONObject info = plant.optJSONObject("plantInfoMap");
                JSONObject water = info == null ? null : info.optJSONObject("WATER");
                JSONObject pot = info == null ? null : info.optJSONObject("WATERING_POT");
                JSONObject ext = pot == null ? null : pot.optJSONObject("extInfo");
                JSONObject progress = info == null ? null : info.optJSONObject("PLANT_PROGRESS_V2");
                progress = progress == null ? null : progress.optJSONObject("extInfo");
                if (water == null || ext == null || progress == null) { Log.record("会员绿植：缺少水滴或进度数据，停止"); return; }
                long cost = forestFeatureLong(ext, "costPerWater"), balance = forestFeatureLong(water, "value"), before = forestFeatureLong(progress, "totalWaterG");
                if (cost <= 0 || balance < 0 || before < 0) { Log.record("会员绿植：水滴或成本数据无效，停止"); return; }
                JSONArray pots = new JSONArray(ext.optString("availablePots", "[]"));
                boolean five = false, one = false;
                for (int i = 0; i < pots.length(); i++) { five |= "five".equals(pots.optString(i)); one |= "one".equals(pots.optString(i)); }
                String speed = five && balance / cost >= 5 ? "five" : one && balance >= cost ? "one" : "";
                if (speed.isEmpty()) { Log.record("会员绿植：水滴不足或无可用浇水档位，结束"); return; }
                JSONObject awarded = MyUtils.newJSONObject(AntForestRpcCall.plantAward(speed));
                response = MyUtils.newJSONObject(AntForestRpcCall.memberForestSignin(water.optString("darwinVersion", "plant_black_v2")));
                JSONObject afterData = response.optJSONObject("resultData");
                JSONObject afterPlant = afterData == null ? null : afterData.optJSONObject("plantInfo");
                JSONObject afterInfo = afterPlant == null ? null : afterPlant.optJSONObject("plantInfoMap");
                JSONObject afterProgress = afterInfo == null ? null : afterInfo.optJSONObject("PLANT_PROGRESS_V2");
                afterProgress = afterProgress == null ? null : afterProgress.optJSONObject("extInfo");
                JSONObject afterWater = afterInfo == null ? null : afterInfo.optJSONObject("WATER");
                long total = afterProgress == null ? -1 : forestFeatureLong(afterProgress, "totalWaterG");
                long remaining = afterWater == null ? -1 : forestFeatureLong(afterWater, "value");
                if (!Boolean.TRUE.equals(response.opt("success")) || !"SUCCESS".equals(response.optString("resultCode")) || total <= before || remaining < 0 || remaining >= balance) {
                    Log.record("会员绿植：浇水后进度或扣水未确认，停止；code=" + awarded.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(awarded)); return;
                }
                String message = "会员绿植：浇水成功，累计" + before + "→" + total + "，水滴" + balance + "→" + remaining;
                Log.record(message); Log.forest(message); TimeUtil.sleep(300);
            }
            Log.record("会员绿植：达到本轮50次上限，下轮继续");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("会员绿植：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "waterMemberPlant", t); }
    }

    /* 在工作的伙伴只有确认到期时才允许替换。 */
    private boolean dispatchMonopolyAnimal(boolean canConsumeAnimalProp) {
        try {
            TimeUtil.sleep(0);
            if (!canConsumeAnimalProp) {
                return false;
            }
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            Object using = jo.opt("usingMonopolyCreature");
            JSONArray creatureList = jo.optJSONArray("creatureList");
            if (!(using instanceof Boolean) || creatureList == null) {
                Log.record("新版动物伙伴：缺少占用状态或动物列表，本轮不派遣");
                return true;
            }
            boolean replacing = Boolean.TRUE.equals(using);
            if (replacing) {
                JSONObject current = null;
                for (int i = 0; i < creatureList.length(); i++) {
                    JSONObject creature = creatureList.optJSONObject(i);
                    if (creature != null && "using".equals(creature.optString("status"))) { current = creature; break; }
                }
                if (!isMonopolyCreatureExpired(current)) {
                    Log.record("新版动物伙伴：当前伙伴未确认到期，保留当前伙伴");
                    return true;
                }
                Log.record("新版动物伙伴：当前伙伴已到期，尝试派遣新伙伴");
            }
            List<JSONObject> candidates = new ArrayList<>();
            boolean allHasInitialRobEnergy = true;
            for (int i = 0; i < creatureList.length(); i++) {
                JSONObject creature = creatureList.optJSONObject(i);
                if (creature == null || !isMonopolyCreatureIdle(creature)) {
                    continue;
                }
                candidates.add(creature);
                if (creature.isNull("initialRobEnergy")) {
                    allHasInitialRobEnergy = false;
                }
            }
            if (candidates.isEmpty()) {
                Log.record("新版动物伙伴🦩当前没有可派遣的动物");
                return replacing;
            }
            // 都能给出预计产出时选最高的，否则按服务端顺序取第一个
            JSONObject selected = candidates.get(0);
            if (allHasInitialRobEnergy) {
                for (JSONObject candidate : candidates) {
                    if (candidate.optInt("initialRobEnergy") > selected.optInt("initialRobEnergy")) {
                        selected = candidate;
                    }
                }
            }
            String creatureCode = selected.optString("creatureCode");
            if (creatureCode.isEmpty()) {
                Log.record("新版动物伙伴🦩候选缺少 creatureCode，跳过派遣");
                return false;
            }
            JSONObject assigned = MyUtils.newJSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode));
            String code = assigned.optString("resultCode");
            if ("MONOPOLY_CREATURE_USE_CONFIRM".equals(code) || "ANIMAL_PROP_IN_USE_CONFIRM".equals(code)) {
                if (!replacing) {
                    Log.record("新版动物伙伴：派遣时占用状态变化，保留现有伙伴，下轮重查");
                    return true;
                }
                assigned = MyUtils.newJSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode, true));
            }
            if (MessageUtil.checkResultCode(TAG, assigned)) {
                JSONObject after = MyUtils.newJSONObject(AntForestRpcCall.queryUsingCreatureInfo(UserIdMap.getCurrentUid()));
                JSONObject active = after.optJSONObject("userCreatureVO");
                if ("SUCCESS".equals(after.optString("resultCode")) && Boolean.TRUE.equals(after.opt("usingMonopolyCreature"))
                        && active != null && creatureCode.equals(active.optString("creatureCode"))) {
                    String message = "新版动物伙伴：" + (replacing ? "到期替换" : "派遣") + "成功[" + selected.optString("creatureName", creatureCode) + "]";
                    Log.record(message); Log.forest(message);
                } else {
                    Log.record("新版动物伙伴：派遣请求已接受，回查未确认新伙伴在工作；下轮重查");
                }
                return true;
            }
            Log.record("新版动物伙伴：派遣失败，code=" + assigned.optString("resultCode", "缺失") + "，原因=" + assigned.optString("resultDesc", "应答无效"));
            if (replacing) return true;
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "dispatchMonopolyAnimal err:", t);
        }
        return false;
    }

    private boolean isMonopolyCreatureExpired(JSONObject creature) {
        JSONObject energy = creature == null ? null : creature.optJSONObject("robEnergyVO");
        if (energy == null) return false;
        Object remain = energy.opt("robRemainDays");
        Object worked = energy.opt("alreadyWorkDays");
        Object max = energy.opt("maxWorkDays");
        return (remain instanceof Number && ((Number) remain).doubleValue() == energy.optInt("robRemainDays") && energy.optInt("robRemainDays") <= 0)
                || (worked instanceof Number && max instanceof Number && ((Number) max).doubleValue() == energy.optInt("maxWorkDays")
                && ((Number) worked).doubleValue() == energy.optInt("alreadyWorkDays") && energy.optInt("maxWorkDays") > 0
                && energy.optInt("alreadyWorkDays") >= energy.optInt("maxWorkDays"));
    }

    /* 新版动物是否空闲可派遣 */
    private boolean isMonopolyCreatureIdle(JSONObject creature) {
        if (StringUtil.isEmpty(creature.optString("creatureCode"))) {
            return false;
        }
        if ("using".equals(creature.optString("status")) || creature.optLong("assignTime", 0L) > 0) {
            return false;
        }
        JSONObject energy = creature.optJSONObject("robEnergyVO");
        if (energy == null) {
            return true;
        }
        if (energy.has("robRemainDays") && energy.optInt("robRemainDays") <= 0) {
            return false;
        }
        int maxWorkDays = energy.optInt("maxWorkDays", 0);
        return maxWorkDays <= 0 || energy.optInt("alreadyWorkDays", 0) < maxWorkDays;
    }

    // 查询可派遣伙伴，返回是否派遣成功
    private boolean queryAnimalPropList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryAnimalPropList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray animalProps = jo.optJSONArray("animalProps");
            JSONObject animalProp = null;
            for (int i = 0; animalProps != null && i < animalProps.length(); i++) {
                jo = animalProps.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (animalProp == null) {
                    animalProp = jo;
                    if (consumeAnimalPropType.getValue() == ConsumeAnimalPropType.SEQUENCE) {
                        break;
                    }
                } else {
                    JSONObject joMain = jo.optJSONObject("main");
                    JSONObject animalPropMain = animalProp.optJSONObject("main");
                    int joHolds = joMain != null ? joMain.optInt("holdsNum") : 0;
                    int animalPropHolds = animalPropMain != null ? animalPropMain.optInt("holdsNum") : 0;
                    if (joHolds > animalPropHolds) {
                        animalProp = jo;
                    }
                }
            }
            return consumeAnimalProp(animalProp);
        } catch (Throwable t) {
            Log.err(TAG, "queryAnimalPropList err:", t);
        }
        return false;
    }

    // 派遣伙伴，返回是否派遣成功
    private boolean consumeAnimalProp(JSONObject animalProp) {
        if (animalProp == null) {
            return false;
        }
        try {
            JSONObject main = animalProp.optJSONObject("main");
            JSONObject partner = animalProp.optJSONObject("partner");
            if (main == null || partner == null) {
                return false;
            }
            String propGroup = main.optString("propGroup");
            String propType = main.optString("propType");
            String name = partner.optString("name");
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroup, propType, false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("巡护派遣🐆[" + name + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "consumeAnimalProp err:", t);
        }
        return false;
    }

    private int forFriendCollectEnergy(String targetUserId, long bubbleId) {
        int helped = 0;
        try {
            String s = AntForestRpcCall.forFriendCollectEnergy(targetUserId, bubbleId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if ("SUCCESS".equals(jo.optString("resultCode"))) {
                JSONArray jaBubbles = jo.optJSONArray("bubbles");
                for (int i = 0; jaBubbles != null && i < jaBubbles.length(); i++) {
                    jo = jaBubbles.optJSONObject(i);
                    if (jo != null) {
                        helped += jo.optInt("collectedEnergy");
                    }
                }
                if (helped > 0) {
                    Log.forest("帮收能量🧺[" + UserIdMap.getMaskName(targetUserId) + "]#" + helped + "g");
                    totalHelpCollected += helped;
                    Statistics.addData(Statistics.DataType.HELPED, helped);
                } else {
                    Log.forest("帮[" + UserIdMap.getMaskName(targetUserId) + "]收取失败");
                    Log.i("，UserID：" + targetUserId + "，BubbleId" + bubbleId);
                }
            } else {
                Log.forest("[" + UserIdMap.getMaskName(targetUserId) + "]" + jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "forFriendCollectEnergy err:", t);
        }
        return helped;
    }

    public static JSONArray getForestPropVOList() {
        JSONArray forestPropVOList = new JSONArray();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray list = jo.optJSONArray("forestPropVOList");
                if (list != null) {
                    forestPropVOList = list;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "getForestPropVOList err:", th);
        }
        return forestPropVOList;
    }

    // 获取道具组全部道具
    public static List<JSONObject> getPropGroup(JSONArray forestPropVOList, String propGroup) {
        List<JSONObject> list = new ArrayList<>();
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.optJSONObject(i);
                if (forestPropVO != null && propGroup.equals(forestPropVO.optString("propGroup"))) {
                    list.add(forestPropVO);
                }
            }
            Collections.sort(list, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                    JSONObject propConfigVO1 = jsonObject1.optJSONObject("propConfigVO");
                    JSONObject propConfigVO2 = jsonObject2.optJSONObject("propConfigVO");
                    int durationTime1 = propConfigVO1 != null ? propConfigVO1.optInt("durationTime") : 0;
                    int durationTime2 = propConfigVO2 != null ? propConfigVO2.optInt("durationTime") : 0;
                    boolean hasExpireTime1 = jsonObject1.has("recentExpireTime");
                    boolean hasExpireTime2 = jsonObject2.has("recentExpireTime");
                    if (hasExpireTime1 && hasExpireTime2) {
                        long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime1);
                        long recentExpireTime = jsonObject2.optLong("recentExpireTime");
                        if (endTime < recentExpireTime) {
                            return -1;
                        } else {
                            return durationTime2 - durationTime1;
                        }
                    } else if (!hasExpireTime1 && !hasExpireTime2) {
                        return durationTime1 - durationTime2;
                    } else {
                        return hasExpireTime1 ? -1 : 1;
                    }
                }
            });
        } catch (Throwable th) {
            Log.err(TAG, "getPropGroup err:", th);
        }
        return list;
    }

    /*
     * 查找背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    private JSONObject getForestPropVO(JSONArray forestPropVOList, String propType) {
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.optJSONObject(i);
                if (forestPropVO != null && propType.equals(forestPropVO.optString("propType"))) {
                    return forestPropVO;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "getForestPropVO err:", th);
        }
        return null;
    }

    /*
     * 使用背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    public static Boolean consumeProp(JSONObject prop) {
        try {
            // 使用道具
            JSONArray propIdList = prop.optJSONArray("propIdList");
            JSONObject propConfigVO = prop.optJSONObject("propConfigVO");
            if (propIdList == null || propIdList.length() == 0 || propConfigVO == null) {
                return false;
            }
            String propId = propIdList.optString(0);
            String propType = prop.optString("propType");
            String propGroup = prop.optString("propGroup");
            String propName = propConfigVO.optString("propName");
            return consumeProp(propGroup, propId, propType, propName);
        } catch (Throwable th) {
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    private static Boolean consumeProp(String propGroup, String propId, String propType, String propName) {
        if (ForestExpiringProps.hasUnconfirmed(propGroup)) return false;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroup, propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("使用道具🎭[" + propName + "]");
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    /** 活力值商店的分类：官方按这 5 个 labelType 分别拉取，少查一个列表就不全 */
    private static final String[] VITALITY_LABEL_TYPES = {"", "SC_ASSETS", "SKIN", "JEWELRY", "OTHER"};

    /** 单分类翻页上限：hasMore 异常一直为真时兜底 */
    private static final int VITALITY_ITEM_MAX_PAGES = 10;

    /**
     * 获取活力值商店所有商品信息。
     * <p>原先只查 SC_ASSETS 的第一页 ⇒ 权益列表只有一小部分；改为按官方实测的 5 个 labelType
     * 分别拉取，并用响应里的 hasMore 翻页。
     */
    private void getAllSkuInfo() {
        try {
            int got = 0;
            for (String labelType : VITALITY_LABEL_TYPES) {
                int cnt = 0;
                for (int page = 0; page < VITALITY_ITEM_MAX_PAGES; page++) {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.itemList(labelType, page * AntForestRpcCall.VITALITY_ITEM_PAGE_SIZE));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        break;
                    }
                    JSONArray itemInfoVOList = optItemInfoVOList(jo);
                    if (itemInfoVOList == null || itemInfoVOList.length() == 0) {
                        break;
                    }
                    for (int i = 0; i < itemInfoVOList.length(); i++) {
                        JSONObject itemInfoVO = itemInfoVOList.optJSONObject(i);
                        if (itemInfoVO != null) {
                            getSkuInfoByItemInfoVO(itemInfoVO);
                            cnt++;
                        }
                    }
                    if (!hasMore(jo)) {
                        break;
                    }
                }
                got += cnt;
                Log.i("活力值商店列表：[" + (labelType.isEmpty() ? "全部" : labelType) + "]取到" + cnt + "条");
            }
            VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
            Log.i("活力值商店列表：共取" + got + "条，清单共" + VitalityBenefitIdMap.getMap().size() + "条");
        } catch (Throwable th) {
            Log.err(TAG, "getAllSkuInfo err:", th);
        }
    }

    /** 商品列表可能在顶层、也可能在 resData 下（抓包两层都出现过），两层都取 */
    private static JSONArray optItemInfoVOList(JSONObject jo) {
        JSONArray list = jo.optJSONArray("itemInfoVOList");
        if (list == null) {
            JSONObject resData = jo.optJSONObject("resData");
            if (resData != null) {
                list = resData.optJSONArray("itemInfoVOList");
            }
        }
        return list;
    }

    /** hasMore 表示还有下一页，同样两层都取 */
    private static boolean hasMore(JSONObject jo) {
        if (jo.has("hasMore")) {
            return jo.optBoolean("hasMore", false);
        }
        JSONObject resData = jo.optJSONObject("resData");
        return resData != null && resData.optBoolean("hasMore", false);
    }

    private void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.itemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject spuItemInfoVo = jo.optJSONObject("spuItemInfoVO");
            if (spuItemInfoVo != null) {
                getSkuInfoByItemInfoVO(spuItemInfoVo);
                VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
            }
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoBySpuId err:", th);
        }
    }

    /** 单个 SPU 的 SKU 入表；落盘由调用方整批结束后统一 save（原先每个 SPU 都写一次文件） */
    private void getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            String spuId = spuItem.optString("spuId");
            JSONArray skuModelList = spuItem.optJSONArray("skuModelList");
            if (spuId.isEmpty() || skuModelList == null) {
                return;
            }
            for (int i = 0; i < skuModelList.length(); i++) {
                JSONObject skuModel = skuModelList.optJSONObject(i);
                if (skuModel == null) {
                    continue;
                }
                String skuId = skuModel.optString("skuId");
                if (skuId.isEmpty()) {
                    continue;
                }
                String skuName = skuModel.optString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                VitalityBenefitIdMap.add(skuId, skuName);
            }
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoByItemInfoVO err:", th);
        }
    }

    /*
     * 兑换活力值商品
     * sku
     * spuId, skuId, skuName, exchangedCount, price[amount]
     * exchangedCount == 0......
     */
    private Boolean exchangeBenefit(String skuId) {
        if (skuId == null || ForestPropSupport.hasUnconfirmedRefill(skuId)) return false;
        if (skuInfo.isEmpty()) {
            getAllSkuInfo();
        }
        JSONObject sku = skuInfo.get(skuId);
        if (sku == null) {
            Log.record("活力兑换🎐找不到要兑换的权益！");
            return false;
        }
        try {
            String skuName = sku.optString("skuName");
            JSONArray itemStatusList = sku.optJSONArray("itemStatusList");
            for (int i = 0; itemStatusList != null && i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.optString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_STOCK.name().equals(itemStatus)) {
                    Log.record("活力兑换🎐[" + skuName + "]停止:" + ItemStatus.valueOf(itemStatus).nickName());
                    if (ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.optString("spuId");
            if (exchangeBenefit(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    public static Boolean exchangeBenefit(String spuId, String skuId, String skuName) {
        try {
            if (exchangeBenefit(spuId, skuId)) {
                Status.vitalityExchangeBenefitToday(skuId);
                int exchangedCount = Status.getVitalityExchangeBenefitCountToday(skuId);
                Log.forest("活力兑换🎐[" + skuName + "]#第" + exchangedCount + "次");
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    private static Boolean exchangeBenefit(String spuId, String skuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.exchangeBenefit(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                //如果出错今天停止兑换
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    Status.flagToday("forest::exchangeLimit::" + skuId);
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    private void teamCooperateWater() {
        // 记录本次是否由本方法切到组队模式，只要是就得切回来
        boolean switchedToTeam = false;
        try {
            int userDailyTarget = Math.min(Math.max(partnerteamWaterNum.getValue(), 10), 5000);
            int todayUsed = Status.getforestHuntHelpToday(FLAG_TEAM_WATER_DAILY_COUNT);
            int userRemainingQuota = userDailyTarget - todayUsed;

            if (userRemainingQuota < 10) {
                Log.record("组队合种今日已达标 (已浇" + todayUsed + "g / 目标" + userDailyTarget + "g)，跳过");
                return;
            }

            JSONObject homeJo = queryTeamHomePage();
            if (homeJo == null) {
                return;
            }

            // 必须先把模式切到组队，个人模式的返回体里没有 teamHomeResult，取不到 teamId
            int state = teamState(homeJo);
            if (state < 0) {
                // 无法判定组队状态（字段缺失/异常）：不主动改账户设置，直接跳过
                Log.record("无法判定组队状态，跳过组队合种浇水");
                return;
            }
            if (state == 0) {
                Log.record("不在队伍模式,已为您切换至组队浇水");
                if (!updateUserConfiginTeam(true)) {
                    Log.record("切换到组队模式失败，跳过组队合种浇水");
                    return;
                }
                switchedToTeam = true;
                Status.flagToday(FLAG_TEAM_MODE_SWITCHED);
                homeJo = queryTeamHomePage();
                if (homeJo == null) {
                    return;
                }
            }

            String teamId = getTeamId(homeJo);
            if (teamId.isEmpty()) {
                Log.record("未获取到组队合种 TeamID");
                return;
            }

            JSONObject userBaseInfo = homeJo.optJSONObject("userBaseInfo");
            int currentEnergy = userBaseInfo == null ? 0 : userBaseInfo.optInt("currentEnergy", 0);
            if (currentEnergy < 10) {
                Log.record("当前能量不足10g(" + currentEnergy + "g)，无法浇水");
                return;
            }

            // 获取服务端限制
            JSONObject miscJo = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo("teamCanWaterCount", teamId));
            if (!MessageUtil.checkResultCode(TAG, miscJo)) {
                Log.record("queryMiscInfo 查询失败");
                return;
            }

            int serverRemaining = getTeamCanWaterCount(miscJo);
            Log.record("组队状态检查:目标剩余" + userRemainingQuota + "g|官方剩余" + serverRemaining + "g|背包能量" + currentEnergy + "g");

            if (serverRemaining < 10) {
                Log.record("官方限制今日无可浇水额度，跳过");
                return;
            }

            // 计算最终浇水量
            int finalWaterAmount = Math.min(userRemainingQuota, Math.min(serverRemaining, currentEnergy));
            if (finalWaterAmount < 10) {
                Log.record("计算后浇水量(" + finalWaterAmount + "g)低于最小限制10g，不执行");
                return;
            }

            // 执行浇水
            JSONObject waterJo = MyUtils.newJSONObject(AntForestRpcCall.teamWater(teamId, finalWaterAmount));
            if (!hasWaterResult(waterJo)) {
                Log.record("组队合种浇水响应为空或无效，保留额度待重试");
                return;
            }
            boolean retryable = MessageUtil.isRetryable(waterJo);
            if (MessageUtil.checkResultCode(TAG, waterJo) || MessageUtil.checkSuccess(TAG, waterJo)) {
                Log.forest("组队合种🚿给合种浇水" + finalWaterAmount + "g");
                Toast.show("组队合种🚿给合种浇水" + finalWaterAmount + "g");
            } else if (retryable) {
                // 限流/远端异常属临时故障：不计当日额度，下一轮再试
                Log.record("组队合种浇水失败(可重试)，下次运行再试");
            } else {
                // 判定不成但非临时故障（多为今日已浇过/额度用尽）：按已浇记账，保留响应原文便于排查。
                // 不记账的话每次自动运行都会重发一次浇水请求，和真爱合种是同一类问题。
                Log.record("组队合种浇水未确认成功，按已浇记账避免重复：" + waterJo);
            }
            if (!retryable) {
                Status.forestHuntHelpToday(FLAG_TEAM_WATER_DAILY_COUNT, todayUsed + finalWaterAmount, UserIdMap.getCurrentUid());
                Log.record("组队合种今日浇水累计: " + (todayUsed + finalWaterAmount) + "g / " + userDailyTarget + "g");
            }
        } catch (Throwable t) {
            Log.err(TAG, "teamCooperateWater err:", t);
        } finally {
            // 任何分支（含异常和提前 return）都要切回个人模式，否则会一直停在组队模式影响其它功能
            if (switchedToTeam) {
                if (updateUserConfiginTeam(false)) {
                    Status.clearFlag(FLAG_TEAM_MODE_SWITCHED);
                    Log.record("已返回个人模式");
                } else {
                    Log.record("组队合种浇水后切回个人模式失败，下次运行会自动重试");
                }
            }
        }
    }

    /** 查询森林首页，失败返回 null */
    private static JSONObject queryTeamHomePage() {
        try {
            JSONObject homeJo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, homeJo)) {
                Log.record("queryHomePage 返回异常");
                return null;
            }
            return homeJo;
        } catch (Throwable t) {
            Log.err(TAG, "queryTeamHomePage err:", t);
        }
        return null;
    }

    /** 取组队合种的 teamId，缺失时返回空串 */
    private static String getTeamId(JSONObject homeJo) {
        JSONObject teamHomeResult = homeJo.optJSONObject("teamHomeResult");
        if (teamHomeResult == null) {
            return "";
        }
        JSONObject teamBaseInfo = teamHomeResult.optJSONObject("teamBaseInfo");
        return teamBaseInfo == null ? "" : teamBaseInfo.optString("teamId", "");
    }

    /** 取官方今日剩余可浇额度，缺失时返回 0 */
    private static int getTeamCanWaterCount(JSONObject miscJo) {
        JSONObject combineHandlerVOMap = miscJo.optJSONObject("combineHandlerVOMap");
        if (combineHandlerVOMap == null) {
            return 0;
        }
        JSONObject teamCanWaterCount = combineHandlerVOMap.optJSONObject("teamCanWaterCount");
        return teamCanWaterCount == null ? 0 : teamCanWaterCount.optInt("waterCount", 0);
    }

    /**
     * 组队合种浇水会临时把账号切到组队模式。若上次切回失败、或进程在切回前被杀，
     * 这里做一次校正，避免用户被永久留在组队模式（会影响个人主页 / 好友页的表现）。
     * <p>
     * 没开「组队合种浇水」也会执行：正是"开了功能 → 切换中途中断 → 用户随后把开关关掉"这种情况最需要校正。
     */
    private static void fixTeamModeIfNeeded() {
        if (!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED)) {
            return;
        }
        if (updateUserConfiginTeam(false)) {
            Status.clearFlag(FLAG_TEAM_MODE_SWITCHED);
            Log.record("检测到上次组队合种浇水未切回，已恢复个人模式");
        } else {
            Log.record("上次组队合种浇水未切回个人模式，本次恢复失败，稍后重试");
        }
    }

    //needReturn:false切回个人模式，true切到组队模式
    private static boolean updateUserConfiginTeam(Boolean needReturn) {
        try {
            String updateStr = AntForestRpcCall.updateUserConfiginTeam(needReturn);
            JSONObject updateJo = MyUtils.newJSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfig 返回异常");
                return false;
            } else {
                Log.record("合种浇水切换成功：" + (needReturn ? "切到组队模式" : "切到个人模式"));
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "updateUserConfig err:", th);
        }
        return false;
    }

    /**
     * 组队状态：1=已在组队，0=确未组队，-1=无法判定（字段缺失/未知取值，如响应异常或新结构）。
     * 「无法判定」与「确未组队」必须区分：只有后者才允许触发模式切换等有副作用的动作。
     */
    private static int teamState(JSONObject homeObj) {
        if (homeObj == null) return -1;
        JSONObject teamHomeResult = homeObj.optJSONObject("teamHomeResult");
        if (teamHomeResult != null && teamHomeResult.optJSONObject("mainMember") != null) return 1;
        String nextAction = homeObj.optString("nextAction", "");
        if ("Cultivate".equals(nextAction)) {
            return 1;
        }
        if ("Team".equals(nextAction)) {
            return 0;
        }
        return -1;
    }

    private static boolean isTeam(JSONObject homeObj) {
        return teamState(homeObj) == 1;
    }

    private static void loveteam(int waterNum) {
        if (Status.hasFlagToday(FLAG_LOVETEAM_WATER)) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.loveteamHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject userInfo = jo.optJSONObject("userInfo");
            String teamId = userInfo == null ? "" : userInfo.optString("teamId", "");
            if (teamId.isEmpty()) {
                Log.record("真爱合种:未加入真爱合种或未取到队伍，跳过");
                return;
            }
            loveteamWater(teamId, getLoveteamName(jo, teamId), waterNum);
        } catch (Throwable th) {
            Log.err(TAG, "loveteam err:", th);
        }
    }

    /**
     * 真爱合种名称：loveHome 返回体里没有稳定已知的名称字段，这里按可能的 key 依次探测，
     * 都取不到时退回 teamId，保证日志始终有可读内容。
     */
    private static String getLoveteamName(JSONObject loveHome, String teamId) {
        JSONObject userInfo = loveHome.optJSONObject("userInfo");
        if (userInfo != null) {
            for (String key : new String[]{"teamName", "name", "treeName"}) {
                String name = userInfo.optString(key, "");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        String name = loveHome.optString("teamName", "");
        return name.isEmpty() ? teamId : name;
    }

    /** 无效响应不能按永久失败记账；两个浇水接口都需明确的状态字段。 */
    private static boolean hasWaterResult(JSONObject jo) {
        if (jo.opt("success") instanceof Boolean || jo.opt("isSuccess") instanceof Boolean) return true;
        for (String key : new String[]{"resultCode", "code", "errorCode"}) {
            Object code = jo.opt(key);
            if ((code instanceof String || code instanceof Number) && !jo.optString(key).isEmpty()) return true;
        }
        return false;
    }

    /**
     * 真爱合种浇水（每日一次）。
     * <p>当日标记**先落再发请求**：这个动作一天只该做一次，不能把标记挂在响应判定上——
     * 响应一旦不被判定为成功（结构不符、或服务端提示今日已浇/额度用尽），标记就落不下去，
     * 每次自动运行都会重发一次浇水请求，表现为"反复浇水"。
     * <p>只有明确的临时故障（网络异常、限流/远端异常）才撤销标记，留到下一轮再试。
     */
    private static void loveteamWater(String teamId, String teamName, int waterNum) {
        Status.flagToday(FLAG_LOVETEAM_WATER);
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.loveteamWater(teamId, waterNum));
            if (!hasWaterResult(jo)) {
                Status.clearFlag(FLAG_LOVETEAM_WATER);
                Log.record("真爱合种浇水响应为空或无效，待下轮重试");
                return;
            }
            if (MessageUtil.checkSuccess(TAG, jo) || MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                Toast.show("真爱浇水🚿给[" + teamName + "]合种浇水" + waterNum + "g");
                return;
            }
            if (MessageUtil.isRetryable(jo)) {
                Status.clearFlag(FLAG_LOVETEAM_WATER);
                Log.record("真爱合种浇水失败(可重试)，下次运行再试");
                return;
            }
            // 其余失败（多为今日已浇过 / 额度用尽）当日不再重发，保留响应原文便于排查
            Log.record("真爱合种浇水未成功，今日不再尝试：" + jo);
        } catch (Throwable th) {
            Status.clearFlag(FLAG_LOVETEAM_WATER);
            Log.err(TAG, "loveteamWater err:", th);
        }
    }

    private static boolean updateUserConfigEnergyPvp() {
        try {
            String Str = AntForestRpcCall.queryUserTag();
            JSONObject Jo = MyUtils.newJSONObject(Str);
            if (!MessageUtil.checkResultCode(TAG, Jo)) {
                Log.record("queryUserTag 查询1V1状态返回异常");
                return false;
            }
            JSONObject tagMap = Jo.optJSONObject("tagMap");
            if (tagMap != null) {
                if (!tagMap.has("energyPvp")) {
                    Log.record("查询1V1无状态返回[无法参加]若出现标识立马为大人开启");
                    return false;
                }
                String energyPvp = tagMap.optString("energyPvp");
                if (energyPvp.equals("Y")) {
                    return true;
                }
            }
            //自动开启挑战
            String updateStr = AntForestRpcCall.updateUserConfigEnergyPvp(true);
            JSONObject updateJo = MyUtils.newJSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfigEnergyPvp 返回异常");
            } else {
                //Log.record("1V1能量挑战切换成功：" + (needReturn ? "开启" : "关闭"));
                Log.forest("比赛情况🆚1V1能量挑战成功开启");
                return true;
            }
            return false;
        } catch (Throwable th) {
            Log.err(TAG, "updateUserConfigEnergyPvp err:", th);
        }
        return false;
    }

    private static void queryPvpHomeInfo() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPvpHomeInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject currentEnergyPvpBattleRecord = jo.optJSONObject("currentEnergyPvpBattleRecord");
            if (currentEnergyPvpBattleRecord != null) {
                //获取1v1比赛情况
                String attackerDisplayName = currentEnergyPvpBattleRecord.optString("attackerDisplayName");
                int attackerEnergy = currentEnergyPvpBattleRecord.optInt("attackerEnergy");
                int attackerWinCount = currentEnergyPvpBattleRecord.optInt("attackerWinCount");
                String battleType = currentEnergyPvpBattleRecord.optString("battleType");
                String defenderDisplayName = currentEnergyPvpBattleRecord.optString("defenderDisplayName");
                int defenderEnergy = currentEnergyPvpBattleRecord.optInt("defenderEnergy");
                int defenderWinCount = currentEnergyPvpBattleRecord.optInt("defenderWinCount");
                String battleStatus = currentEnergyPvpBattleRecord.optString("battleStatus");
                Log.record("比赛情况🆚" + battleType + "赛" + battleStatus + "[" + attackerDisplayName + "]" + attackerWinCount + "胜(" + attackerEnergy + "g)VS[" + defenderDisplayName + "]" + attackerWinCount + "胜(" + defenderEnergy + "g)");
                //领取奖励
                if (currentEnergyPvpBattleRecord.optBoolean("hasReward")) {
                    jo = MyUtils.newJSONObject(AntForestRpcCall.receivePvpRewards());
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray receivedRewards = jo.optJSONArray("receivedRewards");
                    for (int i = 0; receivedRewards != null && i < receivedRewards.length(); i++) {
                        JSONObject reward = receivedRewards.optJSONObject(i);
                        if (reward == null) {
                            continue;
                        }
                        String rewardName = reward.optString("rewardName");
                        String rewardType = reward.optString("rewardType");
                        if ("energy".equals(rewardType)) {
                            int energy = reward.optInt("energy");
                            Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } else {
                            Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                        }
                    }

                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryPvpHomeInfo err:", th);
        }

    }

    private static void receivePvpRewards() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.checkRewardqueryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject combineHandlerVOMap = jo.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap != null) {
                JSONObject energyPvpInfo = combineHandlerVOMap.optJSONObject("energyPvpInfo");
                if (energyPvpInfo != null) {
                    //领取奖励
                    if (energyPvpInfo.optBoolean("hasReward")) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.receivePvpRewards());
                        if (!MessageUtil.checkResultCode(TAG, jo)) {
                            return;
                        }
                        JSONArray receivedRewards = jo.optJSONArray("receivedRewards");
                        if (receivedRewards != null) {
                            for (int i = 0; i < receivedRewards.length(); i++) {
                                JSONObject reward = receivedRewards.optJSONObject(i);
                                if (reward == null) {
                                    continue;
                                }
                                String rewardName = reward.optString("rewardName");
                                String rewardType = reward.optString("rewardType");
                                if ("energy".equals(rewardType)) {
                                    int energy = reward.optInt("energy");
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, energy);
                                } else {
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "receivePvpRewards err:", th);
        }
    }


    private void dress() {
        String dressDetail = dressDetailList.getValue();
        if (dressDetail.isEmpty()) {
            setDressDetail(getDressDetail().toString());
        } else {
            checkDressDetail(dressDetail);
        }
    }

    private JSONObject getDressDetail() {
        JSONObject dressDetail = MyUtils.newJSONObject();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            JSONObject indexDressVO = jo.optJSONObject("indexDressVO");
            JSONArray ja = indexDressVO != null ? indexDressVO.optJSONArray("dressDetailList") : null;
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String position = jo.optString("position");
                String batchType = jo.optString("batchType");
                dressDetail.put(position, batchType);
            }
        } catch (Throwable th) {
            Log.err(TAG, "getDressDetail err:", th);
        }
        return dressDetail;
    }

    private void setDressDetail(String dressDetail) {
        dressDetailList.setValue(dressDetail);
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.forest("装扮保护🔐皮肤保存,芝麻粒-M将为你持续保护!");
        }
    }

    private void removeDressDetail(String position) {
        JSONObject jo = getDressDetail();
        jo.remove(position);
        setDressDetail(jo.toString());
    }

    private void checkDressDetail(String dressDetail) {
        String[] positions = {"tree__main", "bg__sky_0", "bg__sky_cloud", "bg__ground_a", "bg__ground_b", "bg__ground_c"};
        try {
            boolean isDressExchanged = false;
            JSONObject jo = MyUtils.newJSONObject(dressDetail);
            for (String position : positions) {
                String batchType = "";
                if (jo.has(position)) {
                    batchType = jo.optString(position);
                }
                if (queryUserDressForBackpack(dressMap.get(position), batchType)) {
                    isDressExchanged = true;
                }
            }
            if (isDressExchanged) {
                Log.forest("装扮保护🔐皮肤修改,芝麻粒-M已为你自动恢复!");
            }
        } catch (Throwable th) {
            Log.err(TAG, "checkDressDetail err:", th);
        }
    }

    private Boolean queryUserDressForBackpack(String positionType, String batchType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listUserDressForBackpack(positionType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userHoldDressVOList = jo.optJSONArray("userHoldDressVOList");
            boolean isTakeOff = false;
            for (int i = 0; userHoldDressVOList != null && i < userHoldDressVOList.length(); i++) {
                jo = userHoldDressVOList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (jo.optInt("remainNum", 1) == 0) {
                    if (batchType.equals(jo.optString("batchType"))) {
                        return false;
                    }
                    JSONArray posList = jo.optJSONArray("posList");
                    if (posList == null || posList.length() == 0) {
                        continue;
                    }
                    String position = posList.optString(0);
                    isTakeOff = takeOffDress(jo.optString("dressType"), position);
                } else if (batchType.equals(jo.optString("batchType"))) {
                    return wearDress(jo.optString("dressType"));
                }
            }

            if (!batchType.isEmpty()) {
                removeDressDetail(dressMap.get(positionType));
                Log.forest("装扮保护🔐皮肤过期,芝麻粒-M已为你恢复默认!");
            }
            return isTakeOff;
        } catch (Throwable th) {
            Log.err(TAG, "queryUserDressForBackpack err:", th);
        }
        return false;
    }

    private Boolean wearDress(String dressType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.wearDress(dressType));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "wearDress err:", th);
        }
        return false;
    }

    private Boolean takeOffDress(String dressType, String position) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.takeOffDress(dressType, position));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "takeOffDress err:", th);
        }
        return false;
    }

    /**
     * The enum Collect status.
     */
    public enum CollectStatus {
        /**
         * Available collect status.
         */
        AVAILABLE,
        /**
         * Waiting collect status.
         */
        WAITING,
        /**
         * Insufficient collect status.
         */
        INSUFFICIENT,
        /**
         * Robbed collect status.
         */
        ROBBED
    }

    /**
     * The type Bubble timer task.
     */
    private class BubbleTimerTask extends ChildModelTask {

        /**
         * The User id.
         */
        private final String userId;

        /**
         * The Bubble id.
         */
        private final long bubbleId;

        /**
         * The ProduceTime.
         */
        private final long produceTime;
        private final String userName;
        private final boolean canDouble;

        /**
         * Instantiates a new Bubble timer task.
         */
        BubbleTimerTask(String ui, long bi, long pt, String un, boolean doubled) {
            super(AntForestV2.getBubbleTimerTid(ui, bi), pt - advanceTimeInt);
            userId = ui;
            bubbleId = bi;
            produceTime = pt;
            userName = un;
            canDouble = doubled;
        }

        @Override
        public Runnable setRunnable() {
            return () -> {
                //String userName = UserIdMap.getMaskName(userId);
                int averageInteger = offsetTimeMath.getAverageInteger();
                long readyTime = produceTime - advanceTimeInt + averageInteger - delayTimeMath.getAverageInteger() - System.currentTimeMillis() + 70;
                if (readyTime > 0) {
                    try {
                        Thread.sleep(readyTime);
                    } catch (InterruptedException e) {
                        Log.i("终止[" + userName + "]蹲点收取任务, 任务ID[" + getId() + "]");
                        return;
                    }
                }
                Log.record("执行蹲点收取[" + userName + "]" + "时差[" + averageInteger + "]ms" + "提前[" + advanceTimeInt + "]ms");
                collectEnergy(new CollectEnergyEntity(userId, null, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
            };
        }
    }

    public static String getBubbleTimerTid(String ui, long bi) {
        return "BT|" + ui + "|" + bi;
    }

    public enum ItemStatus {
        NO_ENOUGH_POINT, NO_ENOUGH_STOCK, REACH_LIMIT, SECKILL_NOT_BEGIN, SECKILL_HAS_END, HAS_NEVER_EXPIRE_DRESS;

        public static final String[] nickNames = {"活力值不足", "库存量不足", "兑换达上限", "秒杀未开始", "秒杀已结束", "不限时皮肤"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum PropGroup {
        shield, boost, doubleClick, energyRain, vitalitySignDouble, stealthCard, robExpandCard;

        public static final String[] nickNames = {"能量保护罩", "时光加速器", "能量双击卡", "能量雨卡", "活力翻倍卡", "隐身卡", "能量翻倍卡"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public interface WaterFriendType {

        int WATER_00 = 0;
        int WATER_10 = 1;
        int WATER_18 = 2;
        int WATER_33 = 3;
        int WATER_66 = 4;

        String[] nickNames = {"不浇水", "浇水10克", "浇水18克", "浇水33克", "浇水66克"};
        int[] waterEnergy = {0, 10, 18, 33, 66};
    }

    public interface HelpFriendCollectType {

        int NONE = 0;
        int HELP = 1;
        int NOT_HELP = 2;

        String[] nickNames = {"不复活能量", "复活已选好友", "复活未选好友"};
    }

    public interface ConsumeAnimalPropType {

        int NONE = 0;
        int SEQUENCE = 1;
        int QUANTITY = 2;

        String[] nickNames = {"不派遣动物", "按默认顺序派遣", "按最大数量派遣"};
    }

    public interface UsePropType {

        int CLOSE = 0;
        int ALL = 1;
        int ONLY_LIMIT_TIME = 2;

        String[] nickNames = {"关闭", "所有道具", "限时道具"};
    }

    public interface CollectSelfType {
        int ALL = 0;
        int OVER_THRESHOLD = 1;
        int BELOW_THRESHOLD = 2;

        String[] nickNames = {"所有", "大于阈值", "小于阈值"};
    }

    public interface whackModeNames {
        int CLOSE = 0;
        int WHACK_MODE_COMPATIBLE = 1;
        int WHACK_MODE_AGGRESSIVE = 2;
        String[] nickNames = {"关闭", "兼容模式", "激进模式"};
    }
}
