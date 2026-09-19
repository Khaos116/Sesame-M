package io.github.aw1y2z.sesame.model.task.antFarm;

import android.os.Build;

import io.github.aw1y2z.sesame.entity.AlipayAntFarmDoFarmTaskList;
import io.github.aw1y2z.sesame.entity.AlipayAntFarmDrawMachineTaskList;
import io.github.aw1y2z.sesame.entity.GameCenterMallItem;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.GameCenterMallItemMap;
import lombok.Getter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.*;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.data.modelFieldExt.*;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.entity.CustomOption;
import io.github.aw1y2z.sesame.entity.FarmOrnaments;
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
    private BooleanModelField useNewEggTool;
    private BooleanModelField harvestProduce;
    private ChoiceModelField donationType;
    private IntegerModelField donationAmount;
    private BooleanModelField receiveFarmTaskAward;
    private BooleanModelField useAccelerateTool;
    private SelectModelField useAccelerateToolOptions;
    private BooleanModelField feedFriendAnimal;
    private SelectAndCountModelField feedFriendAnimalList;
    private ChoiceModelField notifyFriendType;
    private SelectModelField notifyFriendList;
    private BooleanModelField acceptGift;
    private SelectAndCountModelField visitFriendList;
    private BooleanModelField chickenDiary;
    private BooleanModelField drawMachine;
    private BooleanModelField AutoAntFarmDrawMachineTaskList;
    private SelectModelField AntFarmDrawMachineTaskList;
    private BooleanModelField IPexchangeBenefit;
    private BooleanModelField ornamentsDressUp;
    private SelectModelField ornamentsDressUpList;
    private IntegerModelField ornamentsDressUpDays;
    private ChoiceModelField hireAnimalType;
    private SelectModelField hireAnimalList;
    private BooleanModelField drawGameCenterAward;
    private BooleanModelField competition;
    private IntegerModelField competitionStarNum;
    private IntegerModelField competitionLeadEggs;      // 领先第一名的蛋数
    private IntegerModelField competitionDailyLimit;    // 每日捐蛋上限
    private BooleanModelField competitionStealRank;     // 霸榜开关
    private IntegerModelField competitionStealMinutes;  // 霸榜提前分钟数(0=整天)
    private BooleanModelField stealRankEnable;          // 偷榜开关
    private IntegerModelField stealRankMinutes;         // 偷榜提前分钟数(0=20:00准时)
    private BooleanModelField useBigEaterTool;
    //private ChoiceModelField getFeedType;
    private SelectModelField getFeedList;
    private BooleanModelField family;
    private SelectModelField familyOptions;
    private SelectModelField notInviteList; // 新增：不邀请列表

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(receiveFarmTaskAward = new BooleanModelField("receiveFarmTaskAward", "饲料任务及奖励", false));
        modelFields.addField(AutoAntFarmDoFarmTaskList = new BooleanModelField("AutoAntFarmDoFarmTaskList", "庄园饲料 | 自动黑名单", true).setDependsOn("receiveFarmTaskAward"));
        modelFields.addField(AntFarmDoFarmTaskList = new SelectModelField("AntFarmDoFarmTaskList", "庄园饲料 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDoFarmTaskList::getList).setDependsOn("receiveFarmTaskAward"));
        modelFields.addField(useNewEggTool = new BooleanModelField("useNewEggTool", "新蛋卡 | 使用", false));
        modelFields.addField(useAccelerateTool = new BooleanModelField("useAccelerateTool", "加速卡 | 使用", false));
        modelFields.addField(useAccelerateToolOptions = new SelectModelField("useAccelerateToolOptions", "加速卡 | 选项", new LinkedHashSet<>(), CustomOption::getUseAccelerateToolOptions).setDependsOn("useAccelerateTool"));
        modelFields.addField(useBigEaterTool = new BooleanModelField("useBigEaterTool", "加饭卡 | 使用", false));
        modelFields.addField(useSpecialFood = new BooleanModelField("useSpecialFood", "特殊食品 | 使用", false));
        modelFields.addField(useSpecialFoodCountLimit = new IntegerModelField("useSpecialFoodCountLimit", "特殊食品 | " + "使用上限(无限:0)", 0).setDependsOn("useSpecialFood"));
        modelFields.addField(rewardFriend = new BooleanModelField("rewardFriend", "打赏好友", false));
        modelFields.addField(recallAnimalType = new ChoiceModelField("recallAnimalType", "召回小鸡", RecallAnimalType.ALWAYS, RecallAnimalType.nickNames));
        modelFields.addField(feedAnimal = new BooleanModelField("feedAnimal", "投喂小鸡", false));
        modelFields.addField(feedFriendAnimal = new BooleanModelField("feedFriendAnimal", "帮喂小鸡 | 开启", true));
        modelFields.addField(feedFriendAnimalList = new SelectAndCountModelField("feedFriendAnimalList", "帮喂小鸡 | " + "好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写帮喂次数(每日)"));
        modelFields.addField(hireAnimalType = new ChoiceModelField("hireAnimalType", "雇佣小鸡 | 动作", HireAnimalType.NONE, HireAnimalType.nickNames));
        modelFields.addField(hireAnimalList = new SelectModelField("hireAnimalList", "雇佣小鸡 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("hireAnimalType"));
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
        modelFields.addField(AntFarmDrawMachineTaskList = new SelectModelField("AntFarmDrawMachineTaskList", "抽抽乐 | 黑名单列表", new LinkedHashSet<>(), AlipayAntFarmDrawMachineTaskList::getList).setDependsOn("drawMachine"));
        modelFields.addField(IPexchangeBenefit = new BooleanModelField("IPexchangeBenefit", "抽抽乐兑换 | 开启", false));
        modelFields.addField(donationType = new ChoiceModelField("donationType", "每日捐蛋 | 方式", DonationType.ZERO, DonationType.nickNames));
        modelFields.addField(donationAmount = new IntegerModelField("donationAmount", "每日捐蛋 | 倍数(每项)", 1).setDependsOn("donationType"));
        modelFields.addField(competition = new BooleanModelField("competition", "排位赛 | 自动捐蛋领奖", false));
        modelFields.addField(competitionStarNum = new IntegerModelField("competitionStarNum", "保底模式 | 目标星星数", 2, 0, 5).setDependsOn("competition"));
        modelFields.addField(competitionDailyLimit = new IntegerModelField("competitionDailyLimit", "自动捐蛋 | 每日捐蛋上限(0不限)", 10, 0, 1000).setDependsOn("competition"));
        modelFields.addField(competitionLeadEggs = new IntegerModelField("competitionLeadEggs", "激进模式 | 捐至榜首领先蛋数", 1, 0, 1000).setDependsOn("competition"));
        modelFields.addField(competitionStealRank = new BooleanModelField("competitionStealRank", "激进模式 | 霸榜", false).setDependsOn("competition"));
        modelFields.addField(competitionStealMinutes = new IntegerModelField("competitionStealMinutes", "激进模式 | 霸榜提前分钟数(0=整天)", 0, 0, 1200).setDependsOn("competitionStealRank"));
        modelFields.addField(stealRankEnable = new BooleanModelField("stealRankEnable", "激进模式 | 偷榜", false).setDependsOn("competition"));
        modelFields.addField(stealRankMinutes = new IntegerModelField("stealRankMinutes", "激进模式 | 偷榜提前分钟数(0=20:00准时)", 0, 0, 1200).setDependsOn("stealRankEnable"));
        modelFields.addField(family = new BooleanModelField("family", "亲密家庭 | 开启", false));
        modelFields.addField(familyOptions = new SelectModelField("familyOptions", "亲密家庭 | 选项", new LinkedHashSet<>(), CustomOption::getAntFarmFamilyOptions).setDependsOn("family"));
        modelFields.addField(notInviteList = new SelectModelField("notInviteList", "亲密家庭 | 不邀请列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("family"));
        modelFields.addField(enableSleep = new BooleanModelField("enableSleep", "小鸡起床 | 自动起床（自动睡觉已禁用）", false));
        modelFields.addField(sleepTime = new StringModelField("sleepTime", "小鸡起床 | 入睡参考时间", "2001").setDependsOn("enableSleep"));
        modelFields.addField(sleepMinutes = new IntegerModelField("sleepMinutes", "小鸡起床 | 睡眠时长(分钟)", 10 * 59, 1, 10 * 60).setDependsOn("enableSleep"));
        modelFields.addField(recordFarmGame = new BooleanModelField("recordFarmGame", "小鸡乐园 | 游戏改分(星星球、登山赛、飞行赛、揍小鸡)", false));
        List<String> farmGameTimeList = new ArrayList<>();
        farmGameTimeList.add("2200-2400");
        modelFields.addField(farmGameTime = new ListModelField.ListJoinCommaToStringModelField("farmGameTime", "小鸡乐园 " + "| 游戏时间(范围)", farmGameTimeList));
        modelFields.addField(drawGameCenterAward = new BooleanModelField("drawGameCenterAward", "小鸡乐园 | 游戏宝箱", false));
        modelFields.addField(gameCenterBuyMallItem = new BooleanModelField("gameCenterBuyMallItem", "小鸡乐园 | 乐园集市", false));
        modelFields.addField(gameCenterBuyMallItemList = new SelectAndCountModelField("gameCenterBuyMallItemList", "小鸡乐园 | 兑奖", new LinkedHashMap<>(), GameCenterMallItem::getList, "请填写兑奖次数(每日)").setDependsOn("gameCenterBuyMallItem"));
        modelFields.addField(kitchen = new BooleanModelField("kitchen", "小鸡厨房", false));
        modelFields.addField(chickenDiary = new BooleanModelField("chickenDiary", "小鸡日记", false));
        modelFields.addField(harvestProduce = new BooleanModelField("harvestProduce", "收取爱心鸡蛋", false));
        modelFields.addField(receiveFarmToolReward = new BooleanModelField("receiveFarmToolReward", "收取道具奖励", false));
        //modelFields.addField(getFeedType = new ChoiceModelField("getFeedType", "一起拿饲料 | 动作", GetFeedType.NONE, GetFeedType.nickNames));
        //modelFields.addField(getFeedList = new SelectModelField("getFeedList", "一起拿饲料 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
        modelFields.addField(acceptGift = new BooleanModelField("acceptGift", "收麦子", false));
        modelFields.addField(visitFriendList = new SelectAndCountModelField("visitFriendList", "送麦子 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList, "请填写赠送次数(每日)"));
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
            Log.farm("任务暂停⏸️蚂蚁庄园:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
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

            step("收爱心鸡蛋", () -> {
                if (harvestProduce.getValue() && benevolenceScore >= 1) {
                    Log.record("有可收取的爱心鸡蛋");
                    harvestProduce(ownerFarmId);
                }
            });

            step("捐蛋", () -> {
                if (competition.getValue()) {
                    if (!competition()) {
                        // 排位赛不存在时，fallback 到公益捐蛋
                        if (donationType.getValue() != DonationType.ZERO) {
                            donation();
                        }
                    }
                } else if (donationType.getValue() != DonationType.ZERO) {
                    donation();
                }
            });

            step("饲料任务", () -> {
                if (receiveFarmTaskAward.getValue()) {
                    listFarmTask(TaskStatus.TODO);
                    listFarmTask(TaskStatus.FINISHED);
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

            // 小鸡睡觉&起床
            step("小鸡睡觉&起床", () -> {
                animalSleepAndWake();
            });

        } catch (Throwable t) {
            Log.err(TAG, "AntFarm.start.run err:", t);
        }
    }

    // 单个子任务抛异常只跳过自己，不影响 run() 后面的其它庄园任务
    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log.err(TAG, "run[" + name + "] err:", t);
        }
    }

    public static void initAntFarmTaskListMap(boolean AutoAntFarmDoFarmTaskList, boolean AutoAntFarmDrawMachineTaskList, boolean drawMachine) {
        try {
            //初始化AntFarmDoFarmTaskListMap
            AntFarmDoFarmTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            blackList.add("到店付款");
            blackList.add("线上支付");
            blackList.add("逛闪购外卖1元起吃");
            blackList.add("用花呗完成一笔支付");
            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
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
                MessageUtil.syncTaskBlackList("庄园饲料任务", blackList, whiteList, AntFarmDoFarmTaskList);
            }

            //初始化AntFarmDrawMachineTaskListMap
            AntFarmDrawMachineTaskListMap.load();
            // 注：游戏/开宝箱类不再预置拉黑，交由自动拉黑机制判定；
            // "伸出援手，点亮希望"（需真实捐赠）与"消耗饲料换机会"（需消耗资源）保留
            blackList = new HashSet<>();
            blackList.add("伸出援手，点亮希望");
            blackList.add("消耗饲料换机会");

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
                    MessageUtil.syncTaskBlackList("庄园装扮抽抽乐任务", blackList, whiteList, AntFarmDrawMachineTaskList);
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
        syncAnimalStatus(ownerFarmId);
        if (!AnimalFeedStatus.EATING.name().equals(ownerAnimal.animalFeedStatus)) {
            return;
        }
        double foodHaveEatten = 0d;
        double consumeSpeed = 0d;
        long nowTime = System.currentTimeMillis();
        for (Animal animal : animals) {
            foodHaveEatten += (nowTime - animal.startEatTime) / 1000 * animal.consumeSpeed;
            consumeSpeed += animal.consumeSpeed;
        }
        long nextFeedTime = nowTime + (long) ((foodInTrough - foodHaveEatten) / consumeSpeed) * 1000;
        String taskId = "FA|" + ownerFarmId;
        if (hasChildTask(taskId)) {
            removeChildTask(taskId);
        }
        addChildTask(new ChildModelTask(taskId, "FA", () -> feedAnimal(ownerFarmId), nextFeedTime));
        Log.record("添加蹲点投喂🥣[" + UserIdMap.getCurrentMaskName() + "]在[" + TimeUtil.getCommonDate(nextFeedTime) + "]执行");
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


    /** 家庭空间睡觉要用的 groupId：优先用本次响应里的，其次用已缓存的 ownerGroupId */
    private String resolveFamilyGroupId(JSONObject loveCabin) {
        String groupId = loveCabin.optString("groupId");
        return StringUtil.isEmpty(groupId) ? ownerGroupId : groupId;
    }

    private Boolean animalWakeUpNow() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONObject ownAnimal = jo.optJSONObject("ownAnimal");
            JSONObject sleepInfo = ownAnimal != null ? ownAnimal.optJSONObject("sleepInfo") : null;
            if (sleepInfo == null) {
                return false;
            }
            if (sleepInfo.optInt("countDown") == 0) {
                return false;
            }
            if (sleepInfo.optLong("sleepBeginTime") + TimeUnit.MINUTES.toMillis(sleepMinutes.getValue()) <= System.currentTimeMillis()) {
                if (jo.has("spaceType")) {
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
        } catch (Throwable t) {
            Log.err(TAG, "sendBackAnimal err:", t);
        }
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
                ToolType toolType = ToolType.valueOf(awardType);
                boolean isFull = false;
                for (FarmTool farmTool : farmTools) {
                    if (farmTool.toolType == toolType) {
                        if (farmTool.toolCount == farmTool.toolHoldLimit) {
                            isFull = true;
                        }
                        break;
                    }
                }
                if (isFull) {
                    if (toolType.equals(ToolType.NEWEGGTOOL)) {
                        useFarmTool(ownerFarmId, ToolType.NEWEGGTOOL);
                    } else {
                        Log.record("领取道具[" + toolType.nickName() + "]#已满，暂不领取");
                        continue;
                    }
                }
                if (!bizInfo.has("awardCount")) {
                    continue;
                }
                int awardCount = bizInfo.optInt("awardCount", 0);
                String taskType = joItem.optString("taskType", "");
                String taskTitle = bizInfo.optString("taskTitle", "");
                jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveToolTaskReward(awardType, awardCount, taskType));
                if (MessageUtil.checkMemo(TAG, jo)) {
                    Log.farm("领取道具🎖️[" + taskTitle + "-" + toolType.nickName() + "]#" + awardCount + "张");
                }
            }
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "donation err:", t);
        }
        return false;
    }

    private boolean competition() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            if (jo.has("exitDonationCompetition")) {
                boolean exitDonationCompetition = jo.optBoolean("exitDonationCompetition");
                //开启排位赛
                if (exitDonationCompetition) {
                    JSONObject joOpen = MyUtils.newJSONObject(AntFarmRpcCall.setDonationCompetitionConf("OPEN"));
                    if (MessageUtil.checkMemo(TAG, joOpen)) {
                        String memo = joOpen.optString("memo");
                        Log.farm("捐蛋排位🥚开启：" + memo);
                    }
                } else {
                    //Log.record("捐蛋排位🥚已在排位赛中，跳过加入操作");
                }
            }

            receiveReward();
            PreviousCompetitionInfo();

            String desUserId = null;
            String desNickName = null;
            int desStarRank = 0;
            int desDonation = 0;
            int[] desDonationSub = new int[4];  // 少1~4颗星的捐蛋数
            String[] desUserIdSub = new String[4];// 少1~4颗星的用户名
            int desStarNum = competitionStarNum.getValue();
            int dailyLimit = competitionDailyLimit.getValue();
            int myDonation = 0;
            int myRank = 0;
            int myStar = 0;
            boolean isNovDonation = false;
            String CurrentUserId = UserIdMap.getCurrentUid();

            if (!jo.has("donationRankHomeInfo")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return false;
            }
            JSONObject donationRankHomeInfo = jo.optJSONObject("donationRankHomeInfo");
            if (donationRankHomeInfo == null || !donationRankHomeInfo.has("userDonationRankList")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return false;
            }
            JSONArray userDonationRankList = donationRankHomeInfo.optJSONArray("userDonationRankList");
            if (userDonationRankList == null || userDonationRankList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return false;
            }
            if (desStarNum == 0) {
                Log.record("捐蛋排位🥚目标星级为0跳过保底捐蛋逻辑");
            } else {
                for (int i = 0; i < userDonationRankList.length(); i++) {
                    JSONObject userDonationRank = userDonationRankList.optJSONObject(i);
                    if (userDonationRank == null) {
                        continue;
                    }
                    String userId = userDonationRank.optString("userId");
                    String nickName = userDonationRank.optString("nickName");
                    int rewardStarNum = userDonationRank.optInt("rewardStarNum");
                    int donationNum = userDonationRank.optInt("donationNum");
                    int rankOrder = userDonationRank.optInt("rankOrder");
                    if (CurrentUserId.equals(userId)) {
                        myDonation = donationNum;
                        myRank = rankOrder;
                        myStar = rewardStarNum;
                        Log.record("捐蛋排位🥚当前排名" + myRank + "已捐蛋" + myDonation + "预计星星" + myStar);
                    }
                    if (rewardStarNum == desStarNum) {
                        desDonation = donationNum;
                        desStarRank = rankOrder;
                        desNickName = nickName;
                        desUserId = userId;
                    }
                    // 整合：收集少1~4颗星的捐蛋数
                    for (int j = 0; j < 4; j++) {
                        int targetStar = desStarNum - (j + 1);
                        if (targetStar > 0 && rewardStarNum == targetStar) {
                            desDonationSub[j] = donationNum;
                            desUserIdSub[j] = nickName;
                        }
                    }
                }
                if (!CurrentUserId.equals(desUserId)) {
                    Log.record("捐蛋排位🥚保底模式目标星级" + desStarNum + "[" + desNickName + "]" + "已捐蛋" + desDonation);
                }

                // 每天20:01-23:59不执行，按北京时间判断（TimeUtil.getNow() 现在固定 GMT+8）
                java.util.Calendar now = TimeUtil.getNow();
                int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
                int minute = now.get(java.util.Calendar.MINUTE);
                if (hour > 20 || (hour == 20 && minute >= 1)) {
                    Log.record("捐蛋排位🥚每日20:01后不执行捐蛋操作");
                    return true;
                }


                //根据目标星星排名最后的捐蛋数及每日捐蛋上限捐蛋
                if (myRank > desStarRank && desDonation > 0) {
                    int DonationEggNum = desDonation - myDonation + 1;
                    if (desDonation < dailyLimit) {
                        if (DonationEggNum > 0) {
                            Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "尝试再捐蛋" + DonationEggNum);
                            competitionDonation("养老保底模式", DonationEggNum);
                            isNovDonation = true;
                        }

                    } else if (dailyLimit == 0) {
                        if (DonationEggNum > 0) {
                            Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "(无捐蛋上限)尝试再捐蛋" + DonationEggNum);
                            competitionDonation("养老保底模式", DonationEggNum);
                            isNovDonation = true;
                        }
                    } else {
                        if (dailyLimit > myDonation) {
                            Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋限制" + dailyLimit + "尝试减少目标星级捐蛋");
                            // 整合：遍历少1~4颗星的选项
                            for (int j = 0; j < 4; j++) {
                                if (desDonationSub[j] > 0 && desDonationSub[j] < dailyLimit && (4 - j) > myStar) {
                                    DonationEggNum = desDonationSub[j] - myDonation + 1;
                                    if (DonationEggNum < 1) {
                                        continue;
                                    }
                                    Log.farm("捐蛋排位🥚[在捐蛋上限" + dailyLimit + "范围内]比目标星级" + desStarNum + "少" + (j + 1) + "颗星的[" + desUserIdSub[j] + "]捐了" + desDonationSub[j] + "当前捐蛋" + myDonation + "尝试再捐蛋" + DonationEggNum);
                                    competitionDonation("养老保底模式", DonationEggNum);
                                    isNovDonation = true;
                                    break;
                                }
                            }
                            if (!isNovDonation) {
                                Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                            }
                        } else {
                            Log.record("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "您的账号已捐蛋" + myDonation + "捐蛋限制" + dailyLimit + "(停止捐蛋)");

                        }
                    }
                }
                //在每日凌晨目标星级还没有人达到且自己捐蛋也为0
                if (myDonation == 0 && desDonation == 0) {
                    Log.farm("捐蛋排位🥚目标星级" + desStarNum + "捐蛋" + desDonation + "当前捐蛋" + myDonation + "尝试首次捐蛋1");
                    competitionDonation("养老保底模式", 1);
                }
            }
            //霸榜时间
            if (competitionStealRank.getValue()) {
                int stealMinutes = competitionStealMinutes.getValue();
                if (isStealRankTime(stealMinutes)) {
                    stealRank(stealMinutes > 0 ? stealMinutes : 1200, "霸榜");
                }
            }

            //设置偷榜时间定时执行
            if (stealRankEnable.getValue()) {
                int minutes = Math.max(stealRankMinutes.getValue(), 0);
                // 计算今天 20:00 的时间戳，按北京时间
                java.util.Calendar targetTime = TimeUtil.getNow();
                targetTime.set(java.util.Calendar.HOUR_OF_DAY, 20);
                targetTime.set(java.util.Calendar.MINUTE, 0);
                targetTime.set(java.util.Calendar.SECOND, 0);
                targetTime.set(java.util.Calendar.MILLISECOND, 0);

                // 偷榜时间 = 20:00 - minutes
                long stealRankTime = targetTime.getTimeInMillis() - minutes * 60 * 1000L;
                long now = System.currentTimeMillis();

                // 如果偷榜时间已过，设置为明天
                if (stealRankTime <= now) {
                    targetTime.add(java.util.Calendar.DAY_OF_MONTH, 1);
                    stealRankTime = targetTime.getTimeInMillis() - minutes * 60 * 1000L;
                }

                // 添加定时任务
                String taskId = "stealRank_" + minutes;
                if (!hasChildTask(taskId)) {
                    addChildTask(new ChildModelTask(taskId, "STEALRANK", () -> stealRank(minutes, "偷榜"), stealRankTime));
                    Log.record("捐蛋排位🥚已设置偷榜[定时任务]将在 " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(stealRankTime) + " 执行");
                }
            }
            // 先解析赛季捐蛋数
            int seasonDonationNum = 0;
            JSONObject seasonDonationProgress0 = jo.optJSONObject("seasonDonationProgress");
            if (seasonDonationProgress0 != null) {
                seasonDonationNum = seasonDonationProgress0.optInt("seasonDonationNum");
            }
            
            // 复用前面已获取的 userDonationRankList，解析当前用户的数据
            if (userDonationRankList != null && userDonationRankList.length() > 0) {
                for (int i = 0; i < userDonationRankList.length(); i++) {
                    JSONObject userDonationRank = userDonationRankList.optJSONObject(i);
                    if (userDonationRank != null) {
                        String userId = userDonationRank.optString("userId");
                        if (CurrentUserId.equals(userId)) {
                            String nickName = userDonationRank.optString("nickName");
                            int totalStarNum = userDonationRank.optInt("totalStarNum");
                            String levelName = userDonationRank.optString("levelName");
                            int donationTotal = userDonationRank.optInt("donationTotal");
                            Log.record("捐蛋排位🥚[" + nickName + "]星星数" + totalStarNum + "等级[" + levelName + "]累计捐蛋" + donationTotal + "赛季捐蛋" + seasonDonationNum);
                            break;
                        }
                    }
                }
            }
            
            // 检查领取赛季进度奖励
            JSONObject seasonDonationProgress = jo.optJSONObject("seasonDonationProgress");
            if (seasonDonationProgress != null) {
                JSONArray nodes = seasonDonationProgress.optJSONArray("nodes");
                boolean hasUnreceived = false;
                if (nodes != null) {
                    for (int i = 0; i < nodes.length(); i++) {
                        JSONObject node = nodes.optJSONObject(i);
                        if (node != null && "UNRECEIVED".equals(node.optString("status"))) {
                            hasUnreceived = true;
                            break;
                        }
                    }
                }
                if (hasUnreceived) {
                    receiveDonationCompetitionProgressAward();
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "competition err:", t);
        }
        return true;
    }

    //偷榜捐蛋
    private void stealRank(int stealRankMinutes, String worKType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (jo.has("exitDonationCompetition")) {
                boolean exitDonationCompetition = jo.optBoolean("exitDonationCompetition");
                //开启排位赛
                if (exitDonationCompetition) {
                    JSONObject joOpen = MyUtils.newJSONObject(AntFarmRpcCall.setDonationCompetitionConf("OPEN"));
                    if (MessageUtil.checkMemo(TAG, joOpen)) {
                        String memo = joOpen.optString("memo");
                        Log.farm("捐蛋排位🥚开启：" + memo);
                    }
                } else {
                    Log.record("捐蛋排位🥚已在排位赛中，跳过加入操作");
                }
            }
            int myDonation = 0;
            int myRank = 0;
            int myStar = 0;
            int dailyLimit = competitionDailyLimit.getValue();
            String CurrentUserId = UserIdMap.getCurrentUid();
            int firstDonation = 0;
            int secondDonation = 0;
            jo = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (!jo.has("donationRankHomeInfo")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONObject donationRankHomeInfo = jo.optJSONObject("donationRankHomeInfo");
            if (donationRankHomeInfo == null || !donationRankHomeInfo.has("userDonationRankList")) {
                Log.record("捐蛋排位🥚未查询到捐赠排行信息");
                return;
            }
            JSONArray userDonationRankList = donationRankHomeInfo.optJSONArray("userDonationRankList");
            if (userDonationRankList == null || userDonationRankList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return;
            }
            for (int i = 0; i < userDonationRankList.length(); i++) {
                JSONObject userDonationRank = userDonationRankList.optJSONObject(i);
                if (userDonationRank == null) {
                    continue;
                }
                String userId = userDonationRank.optString("userId");
                int rewardStarNum = userDonationRank.optInt("rewardStarNum");
                int donationNum = userDonationRank.optInt("donationNum");
                int rankOrder = userDonationRank.optInt("rankOrder");
                if (rankOrder == 1) {
                    firstDonation = donationNum;
                }
                if (rankOrder == 2) {
                    secondDonation = donationNum;
                }
                if (CurrentUserId.equals(userId)) {
                    myDonation = donationNum;
                    myRank = rankOrder;
                    myStar = rewardStarNum;
                }
            }
            int leadEggs = competitionLeadEggs.getValue();
            //第1名时判断领先第2名捐蛋数
            if (myRank == 1) {
                if (myDonation - secondDonation >= leadEggs) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件,不用捐蛋");
                } else {
                    int DonationEggNum = secondDonation + leadEggs - myDonation;
                    if (dailyLimit == 0) {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件且无捐蛋上限尝试再捐蛋" + DonationEggNum);
                        competitionDonation("激进模式", DonationEggNum);
                    } else if (DonationEggNum + myDonation > dailyLimit) {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "需再捐蛋" + DonationEggNum + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                    } else {
                        Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第2名捐蛋" + secondDonation + ",满足领先" + leadEggs + "条件尝试再捐蛋" + DonationEggNum);
                        competitionDonation("激进模式", DonationEggNum);
                    }
                }
            }
            //非第1名判断领先目前第1名捐蛋数
            else {
                int DonationEggNum = firstDonation + leadEggs - myDonation;
                if (dailyLimit == 0) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "条件且无捐蛋上限尝试再捐蛋" + DonationEggNum);
                    competitionDonation("激进模式", DonationEggNum);
                } else if (DonationEggNum + myDonation > dailyLimit) {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "需再捐蛋" + DonationEggNum + "捐蛋限制" + dailyLimit + "(停止捐蛋)");
                } else {
                    Log.record("捐蛋排位🥚" + worKType + "时间段(提前" + stealRankMinutes + "分钟)目前排名" + myRank + "捐蛋" + myDonation + ",第1名捐蛋" + firstDonation + ",满足领先" + leadEggs + "条件还需尝试捐蛋" + DonationEggNum);
                    competitionDonation("激进模式", DonationEggNum);
                }
            }

        } catch (Throwable t) {
            Log.err(TAG, "stealRank err:", t);
        }
    }

    private boolean isStealRankTime(int stealMinutes) {
        java.util.Calendar calendar = TimeUtil.getNow();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = calendar.get(java.util.Calendar.MINUTE);
        int totalMinutes = hour * 60 + minute;
        int targetTime = 20 * 60;
        int startTime = stealMinutes > 0 ? targetTime - stealMinutes : 0;
        return totalMinutes >= startTime && totalMinutes < targetTime;
    }

    private void competitionDonation(String competitionType, int DonationEggNum) {

        if (DonationEggNum > 0) {
            int currentEgg = (int) harvestBenevolenceScore;
            if (currentEgg <= 0) {
                Log.record("捐蛋排位🥚当前无蛋可捐");
            } else {
                if (DonationEggNum > harvestBenevolenceScore) {
                    Log.record("捐蛋排位🥚满足" + competitionType + "需捐蛋" + DonationEggNum + "当前可捐" + harvestBenevolenceScore + "放弃捐蛋");
                } else {
                    Log.farm("捐蛋排位🥚" + competitionType + "开始捐蛋" + DonationEggNum + "枚");
                    donation(DonationEggNum);
                }
            }
        }
    }

    private void receiveReward() {
        try {
            //领取奖励
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.enterCompetitionAwardPage());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONArray levelAwardInfoList = jo.optJSONArray("levelAwardInfoList");
            if (levelAwardInfoList == null || levelAwardInfoList.length() == 0) {
                Log.record("捐蛋排位🥚奖励列表为空");
                return;
            }
            for (int i = 0; i < levelAwardInfoList.length(); i++) {
                JSONObject award = levelAwardInfoList.optJSONObject(i);
                if (award == null) {
                    continue;
                }
                String status = award.optString("status");
                if (!status.equals("unreceived")) {
                    continue;
                }
                String rightsId = award.optString("rightsId");
                JSONObject result = MyUtils.newJSONObject(AntFarmRpcCall.receiveDonationLevelReward(rightsId));
                if (MessageUtil.checkMemo(TAG, result)) {
                    JSONArray levelAwardList = result.optJSONArray("levelAwardList");
                    if (levelAwardList == null || levelAwardList.length() == 0) {
                        Log.record("捐蛋排位🥚奖励为空");
                        continue;
                    }
                    String levelName = result.optString("levelName");
                    for (int j = 0; j < levelAwardList.length(); j++) {
                        JSONObject levelAward = levelAwardList.optJSONObject(j);
                        if (levelAward == null) {
                            continue;
                        }
                        String awardName = levelAward.optString("awardName");
                        int awardNum = levelAward.optInt("awardNum");
                        Log.farm("捐蛋排位🥚领取" + levelName + "段位奖励" + awardNum + awardName);
                    }
                }
                TimeUtil.sleep(500);
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveReward err:", t);
        }
    }

    private void receiveDonationCompetitionProgressAward() {
        try {
            //领取捐蛋对应星星数进度奖励
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveDonationCompetitionProgressAward());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            if (jo.has("totalAddStarNum")) {
                int totalAddStarNum = jo.optInt("totalAddStarNum");
                Log.farm("捐蛋排位🥚累计领取进度奖励星星" + totalAddStarNum);
            }
            
            // 提取 userStarNum 变化
            JSONObject beforeLevelInfo = jo.optJSONObject("beforeLevelInfo");
            JSONObject afterLevelInfo = jo.optJSONObject("afterLevelInfo");
            if (beforeLevelInfo != null && afterLevelInfo != null) {
                int beforeStarNum = beforeLevelInfo.optInt("userStarNum");
                int afterStarNum = afterLevelInfo.optInt("userStarNum");
                String beforeLevelName = beforeLevelInfo.optString("levelName");
                String afterLevelName = afterLevelInfo.optString("levelName");
                int starChange = afterStarNum - beforeStarNum;
                Log.farm("捐蛋排位🥚排位情况["+beforeLevelName+"]("+beforeStarNum + ")→["+afterLevelName+"](" + afterStarNum + ")(+"+starChange+")");
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveDonationCompetitionProgressAward err:", t);
        }
    }

    private void PreviousCompetitionInfo() {

        try {
            //查询上期比赛情况
            //JSONObject joPrevious = MyUtils.newJSONObject(AntFarmRpcCall.queryCompetitionEntranceInfo());
            JSONObject joPrevious = MyUtils.newJSONObject(AntFarmRpcCall.enterDonationCompetitionRank());
            if (MessageUtil.checkMemo(TAG, joPrevious)) {
                JSONObject previousRoundSettleAwardInfo = joPrevious.optJSONObject("previousRoundSettleAwardInfo");
                if (previousRoundSettleAwardInfo == null) {
                    Log.record("捐蛋排位🥚无法获取上期排名");
                } else {
                    String levelName = previousRoundSettleAwardInfo.optString("levelName");
                    int previousRankOrder = previousRoundSettleAwardInfo.optInt("rankOrder", 0);
                    int previousRewardStarNum = previousRoundSettleAwardInfo.optInt("rewardStarNum", 0);
                    Log.record("捐蛋排位🥚上期排名" + previousRankOrder + "奖励星星" + previousRewardStarNum + "段位等级[" + levelName + "]");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "PreviousCompetitionInfo err:", t);
        }
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

    private Boolean canDonationToday() {
        if (Status.hasFlagToday("farm::donation")) {
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
            Status.flagToday("farm::donation");
        } catch (Throwable t) {
            Log.err(TAG, "canDonationToday err:", t);
        }
        return false;
    }

    private void recordFarmGame(GameType gameType) {
        try {
            do {
                try {
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
        } catch (Throwable t) {
            Log.err(TAG, "recordFarmGame err:", t);
        }
    }

    private void listFarmTask(TaskStatus Mode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.listFarmTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return;
            }
            JSONObject signList = jo.optJSONObject("signList");
            if (signList != null && sign(signList)) {
                TimeUtil.sleep(1000);
            }
            JSONArray ja = jo.optJSONArray("farmTaskList");
            if (ja == null) {
                return;
            }
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
                        receiveFarmTaskAward(taskJo);
                    }
                    continue;
                }
                if (taskStatus == TaskStatus.RECEIVED || taskStatus != Mode) {
                    continue;
                }
                if (taskStatus == TaskStatus.TODO && !doFarmTask(taskJo)) {
                    continue;
                }
                if (taskStatus == TaskStatus.FINISHED && !receiveFarmTaskAward(taskJo)) {
                    continue;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable t) {
            Log.err(TAG, "listFarmTask err:", t);
        }
    }

    private Boolean sign(JSONObject SignList) {
        if (Status.hasFlagToday("farm::sign")) {
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

    private Boolean doVideoTask() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryTabVideoUrl());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            String videoUrl = jo.optString("videoUrl");
            String contentId = videoUrl.substring(videoUrl.indexOf("&contentId=") + 1, videoUrl.indexOf("&refer"));
            jo = MyUtils.newJSONObject(AntFarmRpcCall.videoDeliverModule(contentId));
            if (jo.optBoolean("success")) {
                TimeUtil.sleep(15100);
                jo = MyUtils.newJSONObject(AntFarmRpcCall.videoTrigger(contentId));
                if (jo.optBoolean("success")) {
                    return true;
                } else {
                    Log.record(jo.optString("resultMsg"));
                    Log.i(jo.toString());
                }
            } else {
                Log.record(jo.optString("resultMsg"));
                Log.i(jo.toString());
            }
        } catch (Throwable t) {
            Log.err(TAG, "doVideoTask err:", t);
        }
        return false;
    }

    private Boolean doAnswerTask() {
        try {
            JSONObject jo = MyUtils.newJSONObject(DadaDailyRpcCall.home("100"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject question = jo.optJSONObject("question");
            if (question == null) {
                return false;
            }
            long questionId = question.optLong("questionId");
            JSONArray labels = question.optJSONArray("label");
            if (labels == null) {
                return false;
            }
            String answer = AnswerAI.getAnswer(question.optString("title"), JsonUtil.jsonArrayToList(labels));
            if (answer == null || answer.isEmpty()) {
                answer = labels.optString(0);
            }
            jo = MyUtils.newJSONObject(DadaDailyRpcCall.submit("100", answer, questionId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject extInfo = jo.optJSONObject("extInfo");
            boolean correct = jo.optBoolean("correct");
            String award = extInfo != null ? extInfo.optString("award") : "";
            Log.record("庄园答题📝回答" + (correct ? "正确" : "错误") + "#获得[" + award + "g饲料]");
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

    private Boolean doFarmTask(JSONObject task) {
        boolean isDoTask = false;
        try {
            String title = task.optString("title");
            String bizKey = task.optString("bizKey");
            if (bizKey.contains("HEART_DONAT") || bizKey.equals("BAIDUJS_202512") || bizKey.equals("BABAFARM_TB")) {
                return false;
            }
            if (Objects.equals(title, "庄园小视频")) {
                isDoTask = doVideoTask();
            } else if (Objects.equals(title, "庄园小课堂")) {
                isDoTask = doAnswerTask();
            } else {
                JSONObject jodoFarmTask = MyUtils.newJSONObject(AntFarmRpcCall.doFarmTask(bizKey));
                //检查并标记黑名单任务（此处是庄园饲料任务，应写入饲料黑名单而非抽抽乐）
                MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList", title, jodoFarmTask);
                if (MessageUtil.checkResultCode(TAG, jodoFarmTask)) {
                    isDoTask=true;
                }
            }

            if (isDoTask) {
                Log.farm("饲料任务🧾完成[" + title + "]");
            } else {
                //Log.record("任务执行失败或跳过: " + title);
            }
        } catch (Throwable t) {
            Log.err(TAG, "doFarmTask err:", t);
        }
        return isDoTask;
    }

    private Boolean receiveFarmTaskAward(JSONObject task) {
        try {
            String taskId = task.optString("taskId");
            String awardType = task.optString("awardType", "");
            int awardCount = task.optInt("awardCount", 0);
            if (Objects.equals(awardType, "ALLPURPOSE")) {
                if (awardCount + foodStock > foodStockLimit) {
                    unReceiveTaskAward++;
                    // Log.record("领取" + awardCount + "克饲料后将超过[" + foodStockLimit + "克]上限，终止领取");
                    return false;
                }
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveFarmTaskAward(taskId));
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            if (awardType.equals("ALLPURPOSE")) {
                add2FoodStock(awardCount);
                String title = task.optString("title", "");
                Log.farm("饲料领取🎖️任务[" + title + "]奖励#获得[" + awardCount + "g]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveFarmTaskAward err:", t);
        }
        return false;
    }

    private void checkUnReceiveTaskAward() {
        if (unReceiveTaskAward > 0) {
            Log.record("还有待领取的饲料");
            unReceiveTaskAward = 0;
            listFarmTask(TaskStatus.FINISHED);
        }
    }

    private void feedAnimal(String farmId) {
        try {
            syncAnimalStatus(ownerFarmId);
            if (foodStock < 180) {
                Log.record("剩余饲料不足以投喂小鸡");
                return;
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.feedAnimal(farmId));
            if (MessageUtil.checkMemo(TAG, jo)) {
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
        if (!Status.canUseAccelerateToolToday()) {
            return;
        }
        syncAnimalStatus(ownerFarmId);
        if ((!useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue") && AnimalBuff.ACCELERATING.name().equals(ownerAnimal.animalBuff)) || (useAccelerateToolOptions.getValue().contains("useAccelerateToolWhenMaxEmotion") && finalScore != 100)) {
            return;
        }
        double consumeSpeed = 0d;
        double foodHaveEatten = 0d;
        long nowTime = System.currentTimeMillis() / 1000;
        for (Animal animal : animals) {
            if (animal.masterFarmId.equals(ownerFarmId)) {
                consumeSpeed = animal.consumeSpeed;
            }
            foodHaveEatten += animal.consumeSpeed * (nowTime - animal.startEatTime / 1000);
        }
        // consumeSpeed: g/s
        // AccelerateTool: -1h = -60m = -3600s
        while (foodInTrough - foodHaveEatten >= consumeSpeed * 3600 && useFarmTool(ownerFarmId, ToolType.ACCELERATETOOL)) {
            TimeUtil.sleep(1000);
            foodHaveEatten += consumeSpeed * 3600;
            Status.useAccelerateToolToday();
            if (!Status.canUseAccelerateToolToday()) {
                break;
            }
            if (!useAccelerateToolOptions.getValue().contains("useAccelerateToolContinue")) {
                break;
            }
        }
    }

    private Boolean useFarmTool(String targetFarmId, ToolType toolType) {
        try {
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
                    Log.farm("使用道具🎭[" + toolType.nickName() + "]#剩余" + (toolCount - 1) + "张");
                    return true;
                } else if (Objects.equals("3D16", jo.optString("resultCode"))) {
                    Status.flagToday("farm::useFarmToolLimit::" + toolType);
                }
                break;
            }
        } catch (Throwable t) {
            Log.err(TAG, "useFarmTool err:", t);
        }
        return false;
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
                    Status.flagToday("farm::feedFriendAnimalLimit");
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
        if (!Status.canUseSpecialFoodToday()) {
            return false;
        }
        try {
            String cookbookId = cuisine.optString("cookbookId");
            String cuisineId = cuisine.optString("cuisineId");
            String name = cuisine.optString("name");
            int count = cuisine.optInt("count");
            for (int j = 0; j < count; j++) {
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
        } catch (Throwable t) {
            Log.err(TAG, "useFarmFood err:", t);
        }
        return false;
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

    //乐园限定活动
    private void queryOptionalPlay() {
        try {
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
            if (taskList == null) {
                return;
            }
            for (int j = 0; j < taskList.length(); j++) {
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
                        JSONObject joReceived = MyUtils.newJSONObject(AntFarmRpcCall.receiveTaskAwardantfarm(awardCountForReceive, sceneCode, taskType));
                        if (MessageUtil.checkSuccess(TAG, joReceived)) {
                            int incAwardCount = joReceived.optInt("incAwardCount");
                            JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                            String awardType = taskConfigResultVO == null ? "null" : taskConfigResultVO.optString("awardType");
                            Log.farm("小鸡乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    //小鸡乐园兑奖
    // skuId, sku
    Map<String, JSONObject> skuInfo = new HashMap<>();

    private void gameCenterBuyMallItem() {
        try {
            getAllSkuInfo();
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

    // 获取乐园商店所有商品信息
    private void getAllSkuInfo() {
        try {
            JSONArray mallItemSimpleList = getGameCenterMallItemList("ANTFARM_GAME_CENTER");
            if (mallItemSimpleList == null) {
                return;
            }
            for (int i = 0; i < mallItemSimpleList.length(); i++) {
                JSONObject itemInfoVO = mallItemSimpleList.optJSONObject(i);
                if (itemInfoVO != null) {
                    getSkuInfoByItemInfoVO(itemInfoVO);
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "getAllSkuInfo err:", th);
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

    private void getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            String spuId = spuItem.optString("spuId");
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.getMallItemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject mallItemDetail = jo.optJSONObject("mallItemDetail");
            if (mallItemDetail == null || !mallItemDetail.has("mallSubItemDetailList")) {
                return;
            }
            JSONArray mallSubItemDetailList = mallItemDetail.optJSONArray("mallSubItemDetailList");
            if (mallSubItemDetailList == null) {
                return;
            }
            for (int i = 0; i < mallSubItemDetailList.length(); i++) {
                JSONObject skuModel = mallSubItemDetailList.optJSONObject(i);
                if (skuModel == null) {
                    continue;
                }
                String skuId = skuModel.optString("skuId");
                String skuName = skuModel.optString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                skuInfo.put(skuId, skuModel);
                GameCenterMallItemMap.add(skuId, skuName);
            }
            GameCenterMallItemMap.save(UserIdMap.getCurrentUid());
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoByItemInfoVO err:", th);
        }
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

    private void drawMachineGroups() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.queryLoveCabin(UserIdMap.getCurrentUid()));
            if (MessageUtil.checkMemo(TAG, jo)) {
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
                            } catch (Throwable t) {
                                Log.err(TAG, "drawMachine err:", t);
                            }

                        }
                    }
                }
            }
        } catch (Throwable t) {
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
        } catch (Throwable t) {
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
            for (int i = 0; i < farmTaskList.length(); i++) {
                jo = farmTaskList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String taskStatus = jo.optString("taskStatus");
                String title = jo.optString("title");
                if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                    continue;
                }
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    String taskId = jo.optString("taskId");
                    String awardType = jo.optString("awardType");
                    receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
                    continue;
                }
                //黑名单任务跳过
                if (AntFarmDrawMachineTaskList.getValue().contains(title)) {
                    continue;
                }

                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    int rightsTimesLimit = jo.optInt("rightsTimesLimit");
                    int rightsTimes = jo.optInt("rightsTimes");

                    if (jo.optString("taskId").contains("EXCHANGE") || jo.optString("taskId").contains("FKDWChuodong") || jo.optString("taskId").contains("GYG2") || jo.optString("taskId").equals("jiatingdongrirongrongwu")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jodoFarmTask = MyUtils.newJSONObject(AntFarmRpcCall.doFarmTask(jo.optString("bizKey"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jodoFarmTask);
                        }
                        TimeUtil.sleep(1000);
                    }
                    if (jo.optString("taskId").contains("SHANGYEHUA")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jofinishTask = MyUtils.newJSONObject(AntFarmRpcCall.finishTask(jo.optString("taskId"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jofinishTask);
                        }
                        TimeUtil.sleep(2000);
                    }

                    //完成浏览类游戏任务
                    if ((jo.optString("title").contains("玩"))&&jo.optString("desc").contains("玩") && jo.optString("desc").contains("s")) {
                        for (int j = 0; j < (rightsTimesLimit - rightsTimes); j++) {
                            JSONObject jofinishTask = MyUtils.newJSONObject(AntFarmRpcCall.finishTask(jo.optString("taskId"), taskSceneCode));
                            //检查并标记黑名单任务
                            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDrawMachineTaskList", title, jofinishTask);
                        }
                        TimeUtil.sleep(2000);
                    }
                    TimeUtil.sleep(1000);
                }
                TimeUtil.sleep(2000);
                String taskId = jo.optString("taskId");
                String awardType = jo.optString("awardType");
                receiveFarmDrawTaskAward(taskId, title, awardType, taskSceneCode);
            }
        } catch (Throwable t) {
            Log.err(TAG, "doFarmDrawActivityTimeTask err:", t);
        }
    }

    private void receiveFarmDrawTaskAward(String taskId, String title, String awardType, String taskSceneCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.receiveFarmDrawTimesTaskAward(taskId, awardType, taskSceneCode));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("装扮抽奖🎖️领取[" + title + "]奖励");
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveFarmDrawTimesTaskAward err:", t);
        }
    }

    private Boolean drawMachine(String scene) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.drawMachine(scene));
            if (MessageUtil.checkMemo(TAG, jo)) {
                if (!jo.has("title")) {
                    jo = jo.optJSONObject("drawMachinePrize");
                }
                String title = jo.optString("title");
                Log.farm("装扮抽奖🎁抽中[" + title + "]");
                return true;
            }
        } catch (Throwable t) {
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
                        Log.record("兑换[" + labelType + "]类最高价值[" + skuName + "]碎片不足(持有" + (totalCent / 100) + "需" + (cent / 100) + ")");
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
                        Log.record("剩余碎片不足以兑换[" + labelType + "]类优先级项 [" + skuName + "] (需 " + (cent / 100) + ")，停止后续兑换任务");
                        return false;
                    }

                    int sessionExchangedCount = 0;
                    while (sessionExchangedCount < limitCount) {
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
        } catch (Exception e) {
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
                                Log.record("小鸡乐园开启宝箱失败: " + drawRes.optString("desc"));
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
                        GameTask.Farm_ddply.report("庄园", remainToTask);
                    } else if (remainToTask <= 0) {
                        Log.record("今日 " + limit + " 个金蛋任务已全部满额");
                    }
                }

                // 异步任务完成
                TimeUtil.sleep(3000);
                
                
                /*
                JSONObject gameDrawAwardActivity = jo.getJSONObject("gameDrawAwardActivity");
                int canUseTimes = gameDrawAwardActivity.getInt("canUseTimes");
                while (canUseTimes > 0) {
                    try {
                        jo = MyUtils.newJSONObject(AntFarmRpcCall.drawGameCenterAward());
                        if (jo.optBoolean("success")) {
                            canUseTimes = jo.getInt("drawRightsTimes");
                            JSONArray gameCenterDrawAwardList = jo.getJSONArray("gameCenterDrawAwardList");
                            ArrayList<String> awards = new ArrayList<String>();
                            for (int i = 0; i < gameCenterDrawAwardList.length(); i++) {
                                JSONObject gameCenterDrawAward = gameCenterDrawAwardList.getJSONObject(i);
                                int awardCount = gameCenterDrawAward.getInt("awardCount");
                                String awardName = gameCenterDrawAward.getString("awardName");
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
                String bizTraceId = jo.getString("bizTraceId");
                JSONArray p2pCanInvitePersonDetailList = jo.getJSONArray("p2pCanInvitePersonDetailList");
                
                int canInviteCount = 0;
                int hasInvitedCount = 0;
                List<String> userIdList = new ArrayList<>(); // 保存 userId
                for (int i = 0; i < p2pCanInvitePersonDetailList.length(); i++) {
                    JSONObject personDetail = p2pCanInvitePersonDetailList.getJSONObject(i);
                    String inviteStatus = personDetail.getString("inviteStatus");
                    String userId = personDetail.getString("userId");
                    
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
                                //Log.record("邀请失败：" + jo);
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
                            Log.record("邀请失败：" + jo);
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
            JSONArray friendUserIds = new JSONArray();
            for (int i = 0; i < familyAnimals.length(); i++) {
                jo = familyAnimals.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String animalId = jo.optString("animalId");
                String userId = jo.optString("userId");
                familyUserIds.add(userId);
                friendUserIds.put(userId);
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
                    if (familyOptions.getValue().contains("familyFeed")) {
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

            // 帮家庭成员喂鸡
            if (familyOptions.getValue().contains("feedFamilyAnimal") && !feedFriendLimit) {
                familyFeedFriendAnimal(familyAnimals);
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
            userIds.remove(UserIdMap.getCurrentUid());
            if (userIds.isEmpty()) {
                return;
            }
            String beAssignUser = userIds.get(RandomUtil.nextInt(0, userIds.size() - 1));
            JSONArray assignConfigList = jsonObject.optJSONArray("assignConfigList");
            if (assignConfigList == null || assignConfigList.length() == 0) {
                // 对齐 Sesame-AG AntFarmFamily.kt#assignFamilyMember 的空列表保护：
                // 原来的 getJSONArray + nextInt(0, length()-1) 在列表为空时会传入非法区间抛异常，
                // 虽然外层 try/catch 会吞掉，但不如直接判断跳过更清楚
                Log.record("家庭任务🏡[使用顶梁柱特权] assignConfigList 为空，跳过");
                return;
            }
            JSONObject assignConfig = assignConfigList.optJSONObject(RandomUtil.nextInt(0, assignConfigList.length() - 1));
            if (assignConfig == null) {
                return;
            }
            JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.assignFamilyMember(assignConfig.optString("assignAction"), beAssignUser));
            if (MessageUtil.checkMemo(TAG, jo)) {
                Log.farm("家庭任务🏡[使用顶梁柱特权] " + assignConfig.optString("assignDesc"));
            }
        } catch (Throwable t) {
            Log.err(TAG, "assignFamilyMember err:", t);
        }
    }

    /**
     * 帮家庭成员喂鸡
     */
    private void familyFeedFriendAnimal(JSONArray animals) {
        try {
            for (int i = 0; i < animals.length(); i++) {
                JSONObject animal = animals.optJSONObject(i);
                if (animal == null) {
                    continue;
                }
                JSONObject status = animal.optJSONObject("animalStatusVO");
                if (status == null) {
                    continue;
                }
                String interactStatus = status.optString("animalInteractStatus");
                String feedStatus = status.optString("animalFeedStatus");

                if (!AnimalInteractStatus.HOME.name().equals(interactStatus) || !AnimalFeedStatus.HUNGRY.name().equals(feedStatus)) {
                    continue;
                }

                String groupId = animal.optString("groupId");
                String farmId = animal.optString("farmId");
                String userId = animal.optString("userId");

                if (!UserIdMap.getUserIdSet().contains(userId)) {
                    Log.record(userId + " 不是你的好友！ 跳过家庭喂食");
                    continue;
                }

                String flagKey = "farm::feedFriendLimit::" + userId;
                if (Status.hasFlagToday(flagKey)) {
                    Log.record("[" + userId + "] 今日喂鸡次数已达上限（已记录）🥣，跳过");
                    continue;
                }

                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.feedFriendAnimal(farmId, groupId));
                if (!jo.optBoolean("success", false)) {
                    String code = jo.optString("resultCode");
                    if ("391".equals(code)) {
                        Status.flagToday(flagKey);
                        Log.record("[" + userId + "] 今日帮喂次数已达上限🥣，已记录为当日限制");
                    } else {
                        Log.record("喂食失败 user=" + userId + " code=" + code + " msg=" + jo.optString("memo"));
                    }
                    continue;
                }

                int foodStockAfter = jo.optInt("foodStock");
                String maskName = UserIdMap.getMaskName(userId);
                Log.farm("家庭任务🏠帮喂好友🥣[" + maskName + "]的小鸡180g #剩余" + foodStockAfter + "g");
            }
        } catch (Throwable t) {
            Log.err(TAG, "familyFeedFriendAnimal err:", t);
        }
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
                Log.farm("家庭任务🌈[道早安]" + content);
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
            List<AlipayUser> allUser = AlipayUser.getList();
            if (allUser.isEmpty()) {
                Log.record("allUser is empty");
                return;
            }

            // 打乱顺序，实现随机选取
            List<AlipayUser> shuffledUsers = new ArrayList<>(allUser);
            Collections.shuffle(shuffledUsers);
            JSONArray inviteList = new JSONArray();
            for (AlipayUser user : shuffledUsers) {
                if (!familyUserIds.contains(user.getId()) && !notInviteSet.contains(user.getId()) && (user.getId() != UserIdMap.getCurrentUid())) {
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
            for (int i = 0; i < inviteList.length(); i++) {
                String inviteUID = inviteList.optString(i);
                JSONObject jo = MyUtils.newJSONObject(AntFarmRpcCall.batchInviteP2P(ownerGroupId, inviteUID));
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    Log.farm("家庭任务🏠分享给好友[" + UserIdMap.getShowName(inviteUID) + "]");
                }
            }
            Status.flagToday("antFarm::familyShareToFriends");
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
        NORMAL, GUEST, PIRATE, WORK
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
