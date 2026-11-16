package io.github.aw1y2z.sesame.model.task.antForest;

import io.github.aw1y2z.sesame.util.DailyTask;
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
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
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
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.model.task.antSports.AntSports;
import io.github.aw1y2z.sesame.model.task.forestRead.ReadForestTask;
import io.github.aw1y2z.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.aw1y2z.sesame.ui.ObjReference;
import io.github.aw1y2z.sesame.util.AverageMath;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.ListUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.RunGeneration;
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

    final AntForestEnergy energyHelper = new AntForestEnergy(this);
    final AntForestGame gameHelper = new AntForestGame(this);
    final AntForestTask taskHelper = new AntForestTask(this);
    final AntForestPatrol patrolHelper = new AntForestPatrol(this);
    final AntForestProp propHelper = new AntForestProp(this);
    final AntForestTeam teamHelper = new AntForestTeam(this);
    final AntForestWater waterHelper = new AntForestWater(this);
    final AntForestMall mallHelper = new AntForestMall(this);
    final AntForestEcoLife ecoLifeHelper = new AntForestEcoLife(this);
    final AntForestGreenLife greenLifeHelper = new AntForestGreenLife(this);
    final AntForestPvp pvpHelper = new AntForestPvp(this);
    final AntForestDress dressHelper = new AntForestDress(this);

    static final String TAG = AntForestV2.class.getSimpleName();

    /**
     * 组队合种浇水本地标记：表示"本方法把用户切到了组队模式，还没切回来"。
     * 用于切回失败 / 进程被杀之后的校正，避免用户被永久留在组队模式。
     */
    static final String FLAG_TEAM_MODE_SWITCHED = "Forest::teamWaterSwitchedToTeam";

    /**
     * 真爱合种浇水的当日标记。持久化 key，**不能改名**（改名等于让所有人当天再浇一次）。
     */
    static final String FLAG_LOVETEAM_WATER = "Forest::loveteamWater";

    /**
     * 组队合种当日已浇量（克）的持久化 key，**不能改名**。
     */
    static final String FLAG_TEAM_WATER_DAILY_COUNT = "FLAG_TEAM_WATER_DAILY_COUNT";

    /**
     * 新版保护地任务场景码：跨保护地区域固定的场景标识。
     * 保护地区域会轮换（洪山动物园→玉树东仲→…），但 regionCode 由 entryInfo 动态下发、
     * sceneCode 始终复用此值；服务端按 regionCode 返回该区域任务，故区域轮换无需改代码。
     */
    static final String MONOPOLY_TASK_SCENE_HSDWY = "ANTFOREST_MONOPOLY_TASK_HSDWY";

    /**
     * 新版保护地任务的浏览时长上限（秒）：服务端下发 timeCount 后要等这么久才能提交完成，
     * 超出上限视为异常数据，不阻塞本轮任务。
     */
    static final long MONOPOLY_MAX_TASK_SECONDS = 300L;

    /**
     * 活力兑换遇「系统繁忙」当日最多重试几次。
     * <p>「系统繁忙」是临时故障（服务端文案为"请稍后再试"），打整日标记会放弃当天剩余可兑额度，
     * 故按次计数、用尽才当日停。
     */
    static final int VITALITY_EXCHANGE_RETRY_MAX = 3;

    /** 站内行为做完后的列表确认轮询：行为生效有延迟，命中即返回，未确认则留给下一轮再核 */
    static final long VITALITY_PROBE_INTERVAL_MS = 3000L;
    static final int VITALITY_PROBE_ROUNDS = 3;

    static final AverageMath offsetTimeMath = new AverageMath(5);

    static final Map<String, Long> usingProps = new ConcurrentHashMap<>();

    static final Map<String, String> dressMap;

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
    }

    final AtomicInteger taskCount = new AtomicInteger(0);

    String selfId;

    Integer tryCountInt;

    Integer retryIntervalInt;

    Integer advanceTimeInt;

    Integer checkIntervalInt;

    FixedOrRangeIntervalLimit collectIntervalEntity;

    FixedOrRangeIntervalLimit doubleCollectIntervalEntity;

    final AverageMath delayTimeMath = new AverageMath(5);

    final ObjReference<Long> collectEnergyLockLimit = new ObjReference<>(0L);

    final Object usePropLockObj = new Object();

    BooleanModelField collectEnergy;
    BooleanModelField expiredEnergy;
    BooleanModelField energyRain;
    IntegerModelField advanceTime;
    IntegerModelField tryCount;
    IntegerModelField retryInterval;
    SelectModelField dontCollectList;
    BooleanModelField collectWhiteListMode;
    BooleanModelField onlyCollectRevivedSelfEnergy;
    IntegerModelField revivedSelfOrdinaryMaxEnergy;
    SelectModelField collectWhiteList;

    BooleanModelField drawGameCenterAward;
    BooleanModelField readForest;
    BooleanModelField teamPriority;
    ChoiceModelField CollectSelfEnergyType;

    IntegerModelField CollectSelfEnergyThreshold;
    IntegerModelField collectRobExpandEnergy;
    BooleanModelField collectRobExpandEnergyEnable;
    BooleanModelField collectWateringBubble;
    BooleanModelField batchRobEnergy;
    BooleanModelField balanceNetworkDelay;
    //PK能量
    BooleanModelField pkEnergy;
    ChoiceModelField whackModeName;
    IntegerModelField whackModeGames;
    IntegerModelField whackModeCount;
    IntegerModelField earliestwhackMoleTime;

    // 定义运行模式名称数组（需提前声明，与原 Kotlin 中的 whackMoleModeNames 对应）

    BooleanModelField collectProp;
    StringModelField queryInterval;
    StringModelField collectInterval;
    StringModelField doubleCollectInterval;
    ChoiceModelField doubleClickType;
    ListModelField.ListJoinCommaToStringModelField doubleCardTime;
    @Getter
    IntegerModelField doubleCountLimit;
    IntegerModelField CollectBombEnergyLimit;
    BooleanModelField findEnergyCollect;
    BooleanModelField enableCycleTakeLook;
    StringModelField cycleTakeLookTime;
    IntegerModelField cycleTakeLookInterval;
    BooleanModelField enableCycleRankScan;
    StringModelField cycleRankScanTime;
    IntegerModelField cycleRankScanInterval;
    final String[] forestCycleIds = new String[2];
    BooleanModelField useEnergyRainLimit;
    BooleanModelField doubleCardConstant;
    ChoiceModelField helpFriendCollectType;
    SelectModelField helpFriendCollectList;

    IntegerModelField helpFriendCollectListLimit;
    BooleanModelField returnWater;
    IntegerModelField returnWater33;
    IntegerModelField returnWater18;
    IntegerModelField returnWater10;
    BooleanModelField receiveForestTaskAward;
    BooleanModelField forestTaskGroup;
    BooleanModelField forestSign;
    BooleanModelField forest7DaySign;

    BooleanModelField AutoAntForestVitalityTaskList;
    SelectModelField AntForestVitalityTaskList;
    ChoiceModelField waterFriendType;
    SelectAndCountModelField waterFriendGramList;
    BooleanModelField waterFriendEnergySendChat;

    BooleanModelField waterFriendEnergyFirst;
    SelectAndCountModelField waterFriendList;

    SelectAndCountModelField wateredFriendList;

    BooleanModelField doubleWaterFriendEnergy;
    SelectModelField giveEnergyRainList;
    BooleanModelField vitalityExchangeBenefit;
    SelectAndCountModelField vitality_ExchangeBenefitList;
    BooleanModelField userPatrol;
    BooleanModelField monopolyPatrol;
    BooleanModelField monopolyTasks;
    BooleanModelField monopolyAnimalEnergy;
    BooleanModelField monopolyDispatchAnimal;
    ChoiceModelField monopolyDispatchPriority;
    BooleanModelField monopolyExchangeCertificate;
    BooleanModelField waterMemberPlant;
    BooleanModelField AutoMonopolyTaskList;
    SelectModelField MonopolyTaskList;
    BooleanModelField collectGiftBox;
    BooleanModelField medicalHealth;
    BooleanModelField greenLife;
    BooleanModelField marketBrowseTask;

    BooleanModelField greenRent;
    ChoiceModelField consumeAnimalPropType;
    SelectModelField whoYouWantToGiveTo;
    BooleanModelField ecoLife;
    BooleanModelField youthPrivilege;
    SelectModelField ecoLifeOptions;
    BooleanModelField dress;
    TextModelField dressDetailList;

    static int totalCollected = 0;
    static int totalHelpCollected = 0;
    static boolean hasErrorWait = false;

    @Getter
    Set<String> dontCollectMap = new HashSet<>();

    @Override
    public String getName() {
        return "森林";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    BooleanModelField loveteamWater;
    IntegerModelField loveteamWaterNum;

    BooleanModelField partnerteamWater;
    IntegerModelField partnerteamWaterNum;
    BooleanModelField ForestHunt;
    BooleanModelField AutoAntForestHuntTaskList;
    SelectModelField AntForestHuntTaskList;
    BooleanModelField ForestHuntDraw;
    BooleanModelField ForestHuntHelp;
    SelectModelField ForestHuntHelpList;

    SelectAndCountModelField continuousUseCardOptions;
    IntegerModelField robExpandCardReplaceRemainDays;
    IntegerModelField robExpandCardForceReplaceExpireDays;
    BooleanModelField autoMakeUpSign;
    ChoiceModelField bubbleBoostCard;
    IntegerModelField bubbleBoostDailyLimit;
    StringModelField bubbleBoostTime;
    String bubbleBoostCheckId;
    BooleanModelField smartDoubleCard;
    IntegerModelField smartDoubleCardThreshold;
    IntegerModelField smartDoubleCardDailyLimit;
    BooleanModelField smartDoublePermanent, smartDouble31Days, smartDoubleRenew31;
    BooleanModelField forestPropRefill;
    IntegerModelField forestPropRefillBudget;
    BooleanModelField expiringForestProps;
    SelectModelField expiringForestPropTypes;
    IntegerModelField expiringForestPropHours, expiringForestPropDailyLimit;
    String smartDoubleCheckId;

    BooleanModelField autoUseShieldCard;
    IntegerModelField continuousUseShieldHour;
    BooleanModelField NORMALForestHuntHelp;
    BooleanModelField ACTIVITYForestHuntHelp;

    BooleanModelField energyPvp;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(collectEnergy = new BooleanModelField("collectEnergy", "收集能量", false));
        modelFields.addField(teamPriority = new BooleanModelField("teamPriority", "组队收集能量优先", false));
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
        modelFields.addField(continuousUseCardOptions = new SelectAndCountModelField("continuousUseCardOptions", "连续兑换使用道具卡片", new LinkedHashMap<>(), CustomOption::getContinuousUseCardOptions, "请填写使用次数(每日,0不限)"));
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
        modelFields.addField(expiringForestPropDailyLimit = new IntegerModelField("expiringForestPropDailyLimit", "临期道具 | 每日尝试上限（0不用）", 1, 0, 10).setDependsOn("expiringForestProps"));
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
        modelFields.addField(forestTaskGroup = new BooleanModelField("forestTaskGroup", "森林任务", true));
        modelFields.addField(forestSign = new BooleanModelField("forestSign", "森林签到", false).setDependsOn("forestTaskGroup"));
        modelFields.addField(forest7DaySign = new BooleanModelField("forest7DaySign", "森林7日签到", false).setDependsOn("forestTaskGroup"));
        modelFields.addField(receiveForestTaskAward = new BooleanModelField("receiveForestTaskAward", "活力值任务", false).setDependsOn("forestTaskGroup"));
        modelFields.addField(AutoAntForestVitalityTaskList = new BooleanModelField("AutoAntForestVitalityTaskList", "活力值 | 自动黑名单", true));
        modelFields.addField(AntForestVitalityTaskList = new SelectModelField("AntForestVitalityTaskList", "活力值 | 黑名单列表", new LinkedHashSet<>(), AlipayAntForestVitalityTaskList::getList).setDependsOn("AutoAntForestVitalityTaskList"));
        modelFields.addField(collectGiftBox = new BooleanModelField("collectGiftBox", "领取礼盒", false));
        modelFields.addField(medicalHealth = new BooleanModelField("medicalHealth", "医疗健康", false));
        modelFields.addField(greenLife = new BooleanModelField("greenLife", "森林集市", false));
        modelFields.addField(marketBrowseTask = new BooleanModelField("marketBrowseTask", "绿植浏览", false));
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
        // 两个接口各自持有独立的限流实例：IntervalLimit 的锁与时间戳都在实例上，
        // 共用会让好友家查询与首页查询互相抢同一个节流窗口，逐个好友收取时成倍拉长整轮耗时
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryHomePage",
                new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000));
        RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage",
                new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000));
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
        // 组队优先：本次运行是否由本方法切到组队模式（结束时需切回个人）
        boolean prioritySwitchedToTeam = false;
        try {
            taskCount.set(0);
            selfId = UserIdMap.getCurrentUid();
            hasErrorWait = false;
            if (energyHelper.isRevivedSelfQuietTime()) {
                Log.record("小号复活模式：GMT+8周一07:00至08:00暂停森林任务");
                return;
            }

            // 组队合种浇水异常中断后，把账号从组队模式恢复回个人模式
            AntForestTeam.fixTeamModeIfNeeded();

            // 组队优先：主动切到组队模式，让 queryHomePage 返回组队主页（个人能量在 mainMember 里）
            if (teamPriority.getValue()) {
                if (AntForestTeam.updateUserConfiginTeam(true)) {
                    prioritySwitchedToTeam = true;
                    Status.flagToday(FLAG_TEAM_MODE_SWITCHED);
                    Log.record("组队优先：已切换到组队模式");
                } else {
                    // 账号不在任何队伍 / 切换失败：降级为个人模式（原行为）
                    Log.record("组队优先：切换到组队模式失败（可能不在任何队伍），按个人模式继续");
                }
            }

            boolean energyOnly = TaskCommon.IS_ENERGY_TIME;
            if (!energyOnly && waterFriendEnergyFirst.getValue()) {
                waterHelper.waterFriendEnergy();
            }


            if (useEnergyRainLimit.getValue()) {
                gameHelper.useEnergyRainCard();
            }

            if (energyRain.getValue()) {
                gameHelper.energyRain();
            }

            if (!energyOnly && ecoLife.getValue()) {
                ecoLifeHelper.ecoLife();
            }

            if (!energyOnly && youthPrivilege.getValue()) {
                // 青春特权道具（大学生双击卡/能量罩/加速器）与「森林任务」共用同一套实现，
                // 由 queryTaskList 内部按 vitalityTask::<firstTaskType> 逐任务标记
                taskHelper.queryVitalityYouthTaskList();
            }
            //连续兑换使用道具卡片
            synchronized (usePropLockObj) {
                if (!TaskCommon.IS_ENERGY_TIME && !onlyCollectRevivedSelfEnergy.getValue()) {
                    int used = ForestExpiringProps.consume(expiringForestProps.getValue(), expiringForestPropTypes.getValue(),
                            expiringForestPropHours.getValue(), expiringForestPropDailyLimit.getValue());
                    if (used > 0) {
                        JSONObject refreshed = forestSignPayload(energyHelper.querySelfHome());
                        JSONArray bubbles = refreshed == null ? null : refreshed.optJSONArray("bubbles");
                        if (expiringForestPropTypes.getValue().contains("boost")) {
                            for (int i = 0; bubbles != null && i < bubbles.length(); i++) {
                                JSONObject bubble = bubbles.optJSONObject(i);
                                long id = bubble == null ? -1 : forestFeatureLong(bubble, "id");
                                if (id > 0) removeChildTask(AntForestEnergy.getBubbleTimerTid(selfId, id));
                            }
                        }
                    }
                }
            }
            if (!energyOnly) propHelper.continuousUseCardOptions();

            if (!energyOnly && forestTaskGroup.getValue() && forestSign.getValue()) {
                taskHelper.vantiepSign();
            }

            JSONObject selfHomeObject = energyHelper.collectSelfEnergy();
            JSONObject boostedHome = propHelper.useBubbleBoostCard();
            if (boostedHome != null) selfHomeObject = boostedHome;
            if (!energyOnly && autoMakeUpSign.getValue()) taskHelper.autoMakeUpSign();
            try {
                energyHelper.scanForestRankings();
                selfHomeObject = energyHelper.collectSelfEnergy();
            } catch (Throwable t) {
                if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
                Log.err(TAG, "queryEnergyRanking err:", t);
            }

            if (findEnergyCollect.getValue() && collectEnergy.getValue()) {
                energyHelper.findAndCollectEnergy();
            }

            propHelper.useSmartDoubleCard();
            propHelper.scheduleSmartDoubleCheck();

            if (!energyOnly && selfHomeObject != null) {
                String whackMoleStatus = selfHomeObject.optString("whackMoleStatus");
                if (Objects.equals("CAN_PLAY", whackMoleStatus) || Objects.equals("CAN_INITIATIVE_PLAY", whackMoleStatus) || Objects.equals("NEED_MORE_FRIENDS", whackMoleStatus)) {
                    gameHelper.checkAndHandleWhackMole();
                }
                boolean hasMore = false;
                Set<String> attemptedHomeRewards = new HashSet<>();
                do {
                    if (hasMore) {
                        hasMore = false;
                        JSONObject nextHome = energyHelper.querySelfHome();
                        if (nextHome == null) break;
                        selfHomeObject = nextHome;
                    }
                    if (collectWateringBubble.getValue() || onlyCollectRevivedSelfEnergy.getValue() && collectEnergy.getValue()) {
                        JSONArray wateringBubbles = selfHomeObject.optJSONArray("wateringBubbles");
                        if (wateringBubbles == null) {
                            wateringBubbles = new JSONArray();
                        }
                        if (wateringBubbles.length() > 0) {
                            boolean wateringProgress = false;
                            for (int i = 0; i < wateringBubbles.length(); i++) {
                                int collected = 0;
                                JSONObject wateringBubble = wateringBubbles.optJSONObject(i);
                                if (wateringBubble == null) {
                                    continue;
                                }
                                String bizType = wateringBubble.optString("bizType");
                                if (onlyCollectRevivedSelfEnergy.getValue() && !"fuhuo".equals(bizType)) continue;
                                if (!attemptedHomeRewards.add("bubble:" + bizType + ":" + wateringBubble.optLong("id"))) continue;
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
                                                        collected += b.optInt("collectedEnergy");
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
                                                        collected += b.optInt("collectedEnergy");
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
                                wateringProgress |= collected > 0;
                                TimeUtil.sleep(1000L);
                            }
                            if (wateringProgress && wateringBubbles.length() >= 20) {
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
                            boolean propProgress = false;
                            for (int i = 0; i < givenProps.length(); i++) {
                                JSONObject jo = givenProps.optJSONObject(i);
                                if (jo == null) {
                                    continue;
                                }
                                String giveConfigId = jo.optString("giveConfigId");
                                String giveId = jo.optString("giveId");
                                if (giveConfigId.isEmpty() || giveId.isEmpty()
                                        || !attemptedHomeRewards.add("prop:" + giveConfigId + ":" + giveId)) continue;
                                JSONObject propConfig = jo.optJSONObject("propConfig");
                                String propName = propConfig != null ? propConfig.optString("propName") : "";
                                jo = MyUtils.newJSONObject(AntForestRpcCall.collectProp(giveConfigId, giveId));
                                if (MessageUtil.checkSuccess(TAG, jo)) {
                                    propProgress = true;
                                    Log.forest("领取道具🎭[" + propName + "]");
                                }
                                TimeUtil.sleep(1000L);
                            }
                            if (propProgress && givenProps.length() >= 20) {
                                hasMore = true;
                            }
                        }
                    }
                } while (hasMore);
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
                                Statistics.addData(Statistics.DataType.COLLECTED, energy);
                            }
                            TimeUtil.sleep(500);
                            break;
                        }
                    }
                }
                //强制重复浇水一次
                if (doubleWaterFriendEnergy.getValue()) {
                    if (!Status.hasFlagToday("Forest::doubleWaterFriendEnergy")) {
                        waterHelper.doubleWaterFriendEnergy();
                    }
                }

                waterHelper.waterFriendEnergy();

                if (pkEnergy.getValue()) {
                    energyHelper.collectPKEnergy();
                }

                //初始任务列表
                if (!Status.hasFlagToday("BlackList::initAntForest")) {
                    taskHelper.initAntForestTaskListMap(AutoAntForestVitalityTaskList.getValue(), AutoAntForestHuntTaskList.getValue(), forestTaskGroup.getValue() && receiveForestTaskAward.getValue(), ForestHunt.getValue());
                    Status.flagToday("BlackList::initAntForest");
                }
                // 新版保护地任务名单来自服务端下发：每轮同步一次，保证黑名单候选项与当前区域任务一致
                if (monopolyTasks.getValue()) {
                    patrolHelper.initMonopolyTaskListMap();
                }

                // 组队合种浇水
                if (partnerteamWater.getValue()) {
                    teamHelper.teamCooperateWater();
                }
                // 真爱合种浇水
                if (loveteamWater.getValue()) {
                    if (loveteamWaterNum.getValue() >= 20 && loveteamWaterNum.getValue() <= 10000) {
                        AntForestTeam.loveteam(loveteamWaterNum.getValue());
                        ForestLoveReward.run();
                    }
                }

                // 森林寻宝
                if (ForestHunt.getValue()) {
                    ForestChouChouLe forestChouChouLe = new ForestChouChouLe();
                    forestChouChouLe.chouChouLe(ForestHuntDraw.getValue(), ForestHuntHelp.getValue(), ForestHuntHelpList.getValue(), NORMALForestHuntHelp.getValue(), ACTIVITYForestHuntHelp.getValue(), AntForestHuntTaskList.getValue());
                }

                if (userPatrol.getValue()) {
                    patrolHelper.queryUserPatrol();
                }
                if (monopolyPatrol.getValue()) {
                    patrolHelper.monopolyPatrol();
                }
                if (monopolyAnimalEnergy.getValue()) {
                    patrolHelper.collectMonopolyAnimalEnergy();
                }
                // 巡护入口会独立尝试当前证书（含地图失败），这里只补未启用巡护的路径。
                if (monopolyExchangeCertificate.getValue() && !monopolyPatrol.getValue()) {
                    patrolHelper.exchangeMonopolyCertificate();
                }
                if (waterMemberPlant.getValue()) {
                    patrolHelper.waterMemberPlant();
                }
                // 动物伙伴自动派遣：新版受独立开关与优先级控制，旧版仍只看"派遣动物伙伴"选择
                Integer monopolyDispatchPriorityValue = monopolyDispatchPriority.getValue();
                boolean newAnimalFirst = monopolyDispatchPriorityValue == null || monopolyDispatchPriorityValue == 0;
                boolean animalDispatched = false;
                if (monopolyDispatchAnimal.getValue() && newAnimalFirst) {
                    animalDispatched = patrolHelper.dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (consumeAnimalPropType.getValue() != ConsumeAnimalPropType.NONE) {
                    if (!canConsumeAnimalProp) {
                        Log.record("已经有动物伙伴在巡护森林");
                    } else if (!animalDispatched && !MyUtils.closeVerification()) {
                        animalDispatched = patrolHelper.queryAnimalPropList();
                    }
                }
                if (monopolyDispatchAnimal.getValue() && !animalDispatched && !newAnimalFirst) {
                    patrolHelper.dispatchMonopolyAnimal(canConsumeAnimalProp);
                }
                if (expiredEnergy.getValue()) {
                    popupTask();
                }

                if (forestTaskGroup.getValue() && forest7DaySign.getValue()) {
                    taskHelper.queryCommonSign();
                }
                if (forestTaskGroup.getValue() && receiveForestTaskAward.getValue()) {
                    taskHelper.queryTaskList();
                }

                if (!MyUtils.closeVerification()) {
                    propHelper.giveProp();
                }
                if (vitalityExchangeBenefit.getValue()) {
                    vitalityExchangeBenefit();
                }
                /* 森林集市 */
                if (greenLife.getValue()) {
                    AntForestGreenLife.greenLife();
                }

                // 绿植浏览（独立任务，与森林集市无关）
                if (marketBrowseTask.getValue()) {
                    GreenPlantBrowse.run();
                }

                // 绿色租赁
                if (greenRent.getValue()) {
                    if (!Status.hasFlagToday("Forest::greenRent")) {
                        AntForestGreenLife.greenRent();
                        Status.flagToday("Forest::greenRent");
                    }
                }

                if (medicalHealth.getValue()) {
                    // 医疗健康 绿色医疗 16g*6能量
                    energyHelper.queryForestEnergy("FEEDS");
                    // 医疗健康 电子小票 4g*10能量
                    energyHelper.queryForestEnergy("BILL");
                }
                if (dress.getValue()) {
                    dressHelper.dress();
                }

                gameHelper.checkAndHandleWhackMole();

                //森林乐园
                if (drawGameCenterAward.getValue()) {
                    gameHelper.doforestgame();
                    gameHelper.queryOptionalPlay();
                }

                //1V1能量挑战
                if (energyPvp.getValue()) {
                    if (AntForestPvp.updateUserConfigEnergyPvp()) {
                        AntForestPvp.queryPvpHomeInfo();
                        AntForestPvp.receivePvpRewards();
                    }

                }
                energyHelper.ForestEnergyInfo();
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
            try {
                this.scheduleForestCycles(0, 0);
                this.scheduleForestCycles(1, 0);
                this.scheduleBubbleBoost(0);
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
                // 倍卡与校准都放在异步子任务跑完之后（好友能量是异步收取的，先查只能看到旧值）：
                // 先收倍卡，再由校准按服务端明细覆盖今日收取——顺序反了校准就统计不到这笔
                energyHelper.collectRobExpandEnergy();
                syncTodayCollectedFromServer();
                Statistics.save();
                FriendWatch.save();
            } finally {
                // 组队优先：本轮结束切回个人模式（放在倍卡/校准之后，避免它们落到个人版）
                if (prioritySwitchedToTeam) {
                    if (AntForestTeam.updateUserConfiginTeam(false)) {
                        Status.clearFlag(FLAG_TEAM_MODE_SWITCHED);
                        Log.record("组队优先：已切回个人模式");
                    } else {
                        Log.record("组队优先：切回个人模式失败，下次运行自动重试");
                    }
                }
            }
        }
    }

    /** 每页条数：200 条/页；普通账号当日明细 1~2 页即可翻完，翻页在 reachedOlder 处提前结束。 */
    static final int ENERGY_RECORD_PAGE_SIZE = 200;

    /** 明细翻页上限：防服务端异常恒回 hasMore 时死循环；好友多的账号单日明细可超旧上限，故放宽到 100 页（=20000 条）以覆盖整天记录。 */
    static final int ENERGY_RECORD_MAX_PAGES = 100;

    /**
     * 用服务端能量明细**覆盖**今日收取能量，取代逐点累加的口径。
     * <p>只统计**今日 0 点之后**的记录：{@code nextId} 是时间戳游标，从 0 开始倒序翻页，
     * 一旦遇到早于今日起点的记录即可停止（更早的必定更旧）。
     * <p>拉取失败或解析不到记录时保留原值——覆盖是"定值"，不该把已有统计冲成 0。
     */
    void syncTodayCollectedFromServer() {
        try {
            Calendar todayStart = MyUtils.getInstance();
            todayStart.set(Calendar.HOUR_OF_DAY, 0);
            todayStart.set(Calendar.MINUTE, 0);
            todayStart.set(Calendar.SECOND, 0);
            todayStart.set(Calendar.MILLISECOND, 0);
            long todayStartMillis = todayStart.getTimeInMillis();
            String account = UserIdMap.getCurrentUid();

            long nextId = 0;
            long todayTotal = 0;
            int scanned = 0;
            boolean reachedOlder = false;
            boolean complete = false;
            Set<Long> cursors = new HashSet<>();
            for (int page = 0; page < ENERGY_RECORD_MAX_PAGES; page++) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.pagingQueryEnergyRecord(nextId, ENERGY_RECORD_PAGE_SIZE));
                // 响应层级随调用通道而异：内置抓包里明细挂在 resData 下，而模块 requestString
                // 拿到的是解析后的业务体（recordVOList 直接平铺在顶层）。两处都取，避免漏解析。
                JSONObject resData = jo.optJSONObject("resData");
                if (resData == null) {
                    resData = jo;
                }
                if (!MessageUtil.checkSuccess(TAG, resData) && !MessageUtil.checkResultCode(TAG, resData)) return;
                JSONArray records = resData.optJSONArray("recordVOList");
                if (records == null) {
                    // 拉取失败：宁可不更新（保留旧值），也不要把今日统计冲成 0
                    logEnergyRecordIssue(account, todayStartMillis, "缺少明细数组");
                    return;
                }
                if (!(resData.opt("hasMore") instanceof Boolean)) {
                    logEnergyRecordIssue(account, todayStartMillis, "分页标记无效");
                    return;
                }
                if (records.length() == 0) {
                    complete = !resData.optBoolean("hasMore");
                    break;
                }
                for (int i = 0; i < records.length(); i++) {
                    JSONObject record = records.optJSONObject(i);
                    if (record == null || !(record.opt("bizDate") instanceof Number)
                            || !(record.opt("energy") instanceof Number)) {
                        logEnergyRecordIssue(account, todayStartMillis, "明细字段缺失或类型错误");
                        return;
                    }
                    long bizDate = record.optLong("bizDate", 0);
                    if (bizDate <= 0 || bizDate >= todayStartMillis + 86400000L) {
                        logEnergyRecordIssue(account, todayStartMillis, "明细日期无效");
                        return;
                    }
                    if (bizDate < todayStartMillis) {
                        // 已翻过今日边界：更早的记录不必再看
                        reachedOlder = true;
                        break;
                    }
                    long energy = record.optLong("energy", 0);
                    // 客户端今日获得只计收入，浇水等支出不参与校准。
                    if (energy <= 0) continue;
                    scanned++;
                    if (energy > Integer.MAX_VALUE - todayTotal) {
                        logEnergyRecordIssue(account, todayStartMillis, "能量数值无效或总量溢出");
                        return;
                    }
                    todayTotal += energy;
                }
                if (reachedOlder || !resData.optBoolean("hasMore", false)) {
                    complete = true;
                    break;
                }
                long next = resData.optLong("nextId", 0);
                if (next <= 0 || !cursors.add(next)) {
                    logEnergyRecordIssue(account, todayStartMillis, "分页游标无效或重复");
                    return;
                }
                nextId = next;
                TimeUtil.sleep(300);
            }

            // 不完整的分页不能覆盖统计；正常空列表或仅有往日记录静默保留原值。
            if (!complete) {
                logEnergyRecordIssue(account, todayStartMillis, "分页未完整结束");
                return;
            }
            if (scanned == 0) return;
            TimeUtil.sleep(0);
            if (!Objects.equals(account, UserIdMap.getCurrentUid())
                    || MyUtils.getInstance().getTimeInMillis() >= todayStartMillis + 86400000L) return;
            int delta = Statistics.setDayCollectedFromServer((int) todayTotal);
            if (delta != 0) {
                Statistics.save();
                Log.forest("今日能量⚡️[按服务端明细校准]#今日" + todayTotal + "g"
                        + (delta > 0 ? "#补记" + delta : "#修正" + delta) + "g");
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "syncTodayCollectedFromServer err:", t);
        }
    }

    void logEnergyRecordIssue(String account, long dayStartMillis, String reason) {
        if (account == null || account.isEmpty() || !Objects.equals(account, UserIdMap.getCurrentUid())
                || MyUtils.getInstance().getTimeInMillis() >= dayStartMillis + 86400000L) return;
        String key = "forest::energyRecordLog::" + reason;
        if (Status.hasFlagToday(key)) return;
        Status.flagToday(key, account);
        Log.record("能量明细⚠️" + reason + "#今日统计保持原值");
    }

    // 绿色租赁

    void popupTask() {
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
                                    int expiredEnergy = signRecord.optInt("awardCount");
                                    Log.forest("过期能量💊[" + expiredEnergy + "g]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, expiredEnergy);
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

    void forestExtensions() {
        try {
            ExtensionsHandle.handleAlphaRequest("antForest", "extensions", usingProps);
        } catch (Throwable t) {
            Log.err(TAG, "forestExtensions err:", t);
        }
    }

    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();
    Long vitalitySkuLastFailureNanos;

    void vitalityExchangeBenefit() {
        try {
            // 清单当日拉一次即可：单轮最多 5 个分类共 20 多次请求，且它同时是配置页兑换选项的来源，
            // 不能完全不拉；当日标记与重试逻辑都在 getAllSkuInfo() 内部
            if (!mallHelper.getAllSkuInfo() && skuInfo.isEmpty()) return;
            Map<String, Integer> exchangeList = vitality_ExchangeBenefitList.getValue();
            for (Map.Entry<String, Integer> entry : exchangeList.entrySet()) {
                String skuId = entry.getKey();
                Integer count = entry.getValue();
                if (count == null || count < 0) {
                    continue;
                }
                // 失效 skuId（权益下架/换号/服务端改 ID）当日已判过就跳过，避免每轮刷屏并拖累后续任务
                if (Status.hasFlagToday("forest::exchangeSkuMissing::" + skuId)) {
                    continue;
                }
                // 每日次数之外再加硬循环上限：即便出现意外也不会空转
                int loopCount = 0;
                while (loopCount++ < count
                        && Status.canVitalityExchangeBenefitToday(skuId, count)
                        && mallHelper.exchangeBenefit(skuId)) {
                    TimeUtil.sleep(3000);
                }
            }
        } catch (TaskCancelledException e) { throw e;
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

    /**
     * 青春特权森林道具路由。
     * <p>
     * firstTaskType 是查询用的分组标识，与要领取的 awardTaskType 不同；
     * source 需与 firstTaskType 配对传入，不可随意替换。
     */
    static final class YouthForestRoute {
        final String firstTaskType;
        final String source;
        final String awardTaskType;
        final String displayName;

        YouthForestRoute(String firstTaskType, String source, String awardTaskType, String displayName) {
            this.firstTaskType = firstTaskType;
            this.source = source;
            this.awardTaskType = awardTaskType;
            this.displayName = displayName;
        }
    }

    static final YouthForestRoute[] YOUTH_FOREST_ROUTES = {
            new YouthForestRoute("DNHZ_SL_college", "DNHZ_SL_college", "DAXUESHENG_SJK", "双击卡"),
            new YouthForestRoute("DXS_BHZ", "202212TJBRW", "NENGLIANGZHAO_20230807", "能量罩"),
            new YouthForestRoute("DXS_JSQ", "202212TJBRW", "JIASUQI_20230808", "加速器"),
    };

    /**
     * 黑名单键：剥掉标题末尾的 "(n/N)" 次数后缀。
     * <p>权限类任务在上报时标题会被拼上 "(2/10)"，
     * 而运行时的黑名单检查用的是纯标题；不处理会导致写进黑名单的键永远匹配不上、拉黑失效。
     */
    static String blackTaskKey(String taskTitle) {
        return StringUtil.stripCountSuffix(taskTitle);
    }

    // LIMIT_TIME_ENERGY_DOUBLE_CLICK,CR20230516000363
    // LIMIT_TIME_ENERGY_RAIN_CHANCE,SK20250117005985,VITALITY_ENERGYRAIN_3DAYS，限时3天内使用能量雨次卡

    //乐园限定活动（bizType=ANTFOREST / sceneCode=ANTFOREST_COMMON）
    //奖励类型以能量为主，场景码 ANTFOREST_LEYUAN_DAILY_TASK

    @Override protected boolean supportsManualAction(String action) { return "energyRain".equals(action) || "whackMole".equals(action); }
    @Override protected void runManualAction(String action) {
        TimeUtil.sleep(0);
        if (!isEnable() || !check()) return;
        selfId = UserIdMap.getCurrentUid();
        if (selfId == null || selfId.isEmpty()) return;
        if ("energyRain".equals(action)) gameHelper.energyRain();
        else if ("whackMole".equals(action)) gameHelper.checkAndHandleWhackMole();
    }

    //判断是否可以使用道具卡片
    //返回值-1为不可用，0为可用，大于0为剩余时间

    //选出可以使用的限时道具卡片

    final Map<String, String> legacyPatrolLogs = new HashMap<>();

    static long forestFeatureLong(JSONObject object, String key) {
        Object value = object.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try {
            long number = new java.math.BigDecimal(value.toString()).longValueExact();
            return number >= 0 ? number : -1;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    // 查询可派遣伙伴（分页取全，返回是否派遣成功）


    // 动物道具每日可获能量（"按最多能量派遣"按此最高选择，字段取自 partner.robAbility.robEnergyInDaily）


    // 派遣伙伴，返回是否派遣成功

    // 获取道具组全部道具

    /** 活力值商店的分类：官方按这 5 个 labelType 分别拉取，少查一个列表就不全 */
    static final String[] VITALITY_LABEL_TYPES = {"", "SC_ASSETS", "SKIN", "JEWELRY", "OTHER"};

    /** 单分类翻页上限：hasMore 异常一直为真时兜底 */
    static final int VITALITY_ITEM_MAX_PAGES = 10;

    /** 活力值商店列表拉取失败的当日重试上限：服务端持续失败时避免每轮都重拉（单轮最多 5 分类 × 10 页） */
    static final int VITALITY_SKU_RETRY_MAX = 3;

    /** 活力值商店列表拉取失败次数的当日计数 key */
    static final String FLAG_VITALITY_SKU_RETRY = "forest::vitalitySkuListRetry";

    //needReturn:false切回个人模式，true切到组队模式

    /**
     * The enum Collect status.
     */
    boolean forestCycleEnabled(int kind, String uid) {
        return isEnable() && collectEnergy.getValue() && !AntForestV2.hasErrorWait && !energyHelper.isRevivedSelfQuietTime()
                && uid != null && !uid.isEmpty() && uid.equals(UserIdMap.getCurrentUid())
                && (kind == 0 ? enableCycleTakeLook.getValue() : enableCycleRankScan.getValue());
    }

    synchronized void scheduleForestCycles(int kind, long earliest) {
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
                    if (kind == 0) energyHelper.findAndCollectEnergy();
                    else { if (pkEnergy.getValue()) energyHelper.collectPKEnergy(); energyHelper.scanForestRankings(); }
                    propHelper.useSmartDoubleCard(); propHelper.scheduleSmartDoubleCheck();
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

    String boostPointFlag(long now) {
        String point = ForestSchedule.pointKey(bubbleBoostTime.getValue(), now);
        return point.isEmpty() ? "" : "forest::boostTimePoint::" + point;
    }

    boolean boostTimeAllowed(long now) {
        String flag = boostPointFlag(now);
        return ForestSchedule.nextTrigger(bubbleBoostTime.getValue(), now) == now
                && (flag.isEmpty() || !Status.hasFlagToday(flag));
    }

    synchronized void scheduleBubbleBoost(long earliest) {
        if (io.github.aw1y2z.sesame.util.RunGeneration.isStale()) return;
        String uid = UserIdMap.getCurrentUid(), old = bubbleBoostCheckId;
        int mode = bubbleBoostCard.getValue();
        long now = System.currentTimeMillis();
        boolean enabled = isEnable() && collectEnergy.getValue() && !onlyCollectRevivedSelfEnergy.getValue()
                && !AntForestV2.hasErrorWait && !energyHelper.isRevivedSelfQuietTime() && uid != null && !uid.isEmpty()
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
                    if (!uid.equals(UserIdMap.getCurrentUid()) || !isEnable() || AntForestV2.hasErrorWait || !check() || energyHelper.isRevivedSelfQuietTime()) return;
                    selfId = uid;
                    propHelper.useBubbleBoostCard();
                });
            } catch (TaskCancelledException e) {
                cancelled = true;
                throw e;
            } finally {
                if (!cancelled && generation == taskGeneration() && uid.equals(UserIdMap.getCurrentUid())) scheduleBubbleBoost(System.currentTimeMillis() + 30000);
            }
        }, at))) bubbleBoostCheckId = id;
    }

    List<ChildModelTask> forestChildSnapshot() {
        return getChildTaskSnapshot();
    }

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
    class BubbleTimerTask extends ChildModelTask {

        /**
         * The User id.
         */
        final String userId;

        /**
         * The Bubble id.
         */
        final long bubbleId;

        /**
         * The ProduceTime.
         */
        final long produceTime;
        final String userName;
        final boolean canDouble;

        /**
         * Instantiates a new Bubble timer task.
         */
        BubbleTimerTask(String ui, long bi, long pt, String un, boolean doubled) {
            super(AntForestEnergy.getBubbleTimerTid(ui, bi), pt - advanceTimeInt);
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
                energyHelper.collectEnergy(new CollectEnergyEntity(userId, null, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
            };
        }
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

        String[] nickNames = {"不派遣动物", "按默认顺序派遣", "按最多能量派遣"};
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
