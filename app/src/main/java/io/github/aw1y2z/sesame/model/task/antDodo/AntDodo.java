package io.github.aw1y2z.sesame.model.task.antDodo;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAntDodoTaskList;
import io.github.aw1y2z.sesame.entity.AlipayAntDodoTaskList;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.entity.CustomOption;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2;
import io.github.aw1y2z.sesame.model.task.antOcean.AntOceanRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.AntDodoTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class AntDodo extends ModelTask {
    private static final String TAG = AntDodo.class.getSimpleName();

    @Override
    public String getName() {
        return "物种";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.FOREST;
    }

    private BooleanModelField dodoTaskList;
    private BooleanModelField AutoAntDodoTaskList;
    private SelectModelField AntDodoTaskList;
    private BooleanModelField useProp;
    private BooleanModelField useUniversalCardBookCollectedhasCollected;
    private SelectModelField usePropList;
    private ChoiceModelField useCollectTimingType;
    private ChoiceModelField useUniversalCardBookStatusType;
    private ChoiceModelField useUniversalCardBookCollectedStatusType;
    private ChoiceModelField useUniversalCardMedalGenerationStatusType;
    private ChoiceModelField useUniversalCardFantasticLevelType;
    private BooleanModelField bookMedal;
    private SelectModelField bookMedalOptions;
    private ChoiceModelField collectToFriendType;
    private SelectModelField collectToFriendList;
    private BooleanModelField giftToFriend;
    private ChoiceModelField giftToFriendBookStatusType;
    private ChoiceModelField giftToFriendBookCollectedStatusType;
    private ChoiceModelField giftToFriendMedalGenerationStatusType;
    private ChoiceModelField giftToFriendFantasticLevelType;
    private SelectModelField giftToFriendList;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(dodoTaskList = new BooleanModelField("dodoTaskList", "物种任务", false));
        modelFields.addField(AutoAntDodoTaskList = new BooleanModelField("AutoAntDodoTaskList", "物种任务 | 自动黑名单", true).setDependsOn("dodoTaskList"));
        modelFields.addField(AntDodoTaskList = new SelectModelField("AntDodoTaskList", "物种任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntDodoTaskList::getList).setDependsOn("AutoAntDodoTaskList"));
        modelFields.addField(useProp = new BooleanModelField("useProp", "使用道具 | 开启", false));
        modelFields.addField(usePropList = new SelectModelField("usePropList", "使用道具 | 道具列表", new LinkedHashSet<>(), CustomOption::getAntDodoPropList).setDependsOn("useProp"));
        modelFields.addField(useCollectTimingType = new ChoiceModelField("useCollectTimingType", "抽卡道具 | 使用时机", TimingType.EVERY_DAY, TimingType.nickNames).setDependsOn("useProp"));
        modelFields.addField(useUniversalCardBookStatusType = new ChoiceModelField("useUniversalCardBookStatusType", "万能卡片 | 图鉴状态类型", BookStatusType.END, BookStatusType.nickNames).setDependsOn("useProp"));
        modelFields.addField(useUniversalCardBookCollectedStatusType = new ChoiceModelField("useUniversalCardBookCollectedStatusType", "万能卡片 | 图鉴收集状态", BookCollectedStatusType.ALL, BookCollectedStatusType.nickNames).setDependsOn("useProp"));
        modelFields.addField(useUniversalCardBookCollectedhasCollected = new BooleanModelField("useUniversalCardBookCollectedhasCollected", "万能卡片 | 优先兑换未获得状态卡片", false).setDependsOn("useProp"));
        modelFields.addField(useUniversalCardMedalGenerationStatusType = new ChoiceModelField("useUniversalCardMedalGenerationStatusType", "万能卡片 | 勋章合成状态", MedalGenerationStatusType.ALL, MedalGenerationStatusType.nickNames).setDependsOn("useProp"));
        modelFields.addField(useUniversalCardFantasticLevelType = new ChoiceModelField("useUniversalCardFantasticLevelType", "万能卡片 | 最低等级", FantasticLevelType.MAGIC, FantasticLevelType.nickNames).setDependsOn("useProp"));
        modelFields.addField(bookMedal = new BooleanModelField("bookMedal", "图鉴勋章 | 开启", false));
        modelFields.addField(bookMedalOptions = new SelectModelField("bookMedalOptions", "图鉴勋章 | 选项", new LinkedHashSet<>(), CustomOption::getAntDodoBookMedalOptions).setDependsOn("bookMedal"));
        modelFields.addField(collectToFriendType = new ChoiceModelField("collectToFriendType", "帮抽卡片 | 动作", CollectToFriendType.NONE, CollectToFriendType.nickNames));
        modelFields.addField(collectToFriendList = new SelectModelField("collectToFriendList", "帮抽卡片 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("collectToFriendType"));
        modelFields.addField(giftToFriend = new BooleanModelField("giftToFriend", "赠送卡片 | 开启", false));
        modelFields.addField(giftToFriendBookStatusType = new ChoiceModelField("giftToFriendBookStatusType", "赠送卡片 | " + "图鉴状态类型", BookStatusType.ALL, BookStatusType.nickNames).setDependsOn("giftToFriend"));
        modelFields.addField(giftToFriendBookCollectedStatusType = new ChoiceModelField("giftToFriendBookCollectedStatusType", "赠送卡片 | 图鉴收集状态", BookCollectedStatusType.ALL, BookCollectedStatusType.nickNames).setDependsOn("giftToFriend"));
        modelFields.addField(giftToFriendMedalGenerationStatusType = new ChoiceModelField("giftToFriendMedalGenerationStatusType", "赠送卡片 | 勋章合成状态", MedalGenerationStatusType.ALL, MedalGenerationStatusType.nickNames).setDependsOn("giftToFriend"));
        modelFields.addField(giftToFriendFantasticLevelType = new ChoiceModelField("giftToFriendFantasticLevelType", "赠送卡片 | 最低等级", FantasticLevelType.COMMON, FantasticLevelType.nickNames).setDependsOn("giftToFriend"));
        modelFields.addField(giftToFriendList = new SelectModelField("giftToFriendList", "赠送卡片 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList, "会赠送所有满足条件的卡片给已选择的好友").setDependsOn("giftToFriend"));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.forest("任务暂停⏸️神奇物种:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            //初始任务列表
            if (!Status.hasFlagToday("BlackList::initAntDodo")) {
                initAntDodoTaskListMap(AutoAntDodoTaskList.getValue(), dodoTaskList.getValue());
                Status.flagToday("BlackList::initAntDodo");
            }

            collect();
            if (dodoTaskList.getValue()) {
                taskList();
            }
            if (useProp.getValue()) {
                propList();
            }
            if (collectToFriendType.getValue() != CollectToFriendType.NONE) {
                collectToFriend();
            }
            if (bookMedal.getValue()) {
                generateBookMedal();
            }
            if (giftToFriend.getValue()) {
                giftToFriend();
            }
        } catch (Throwable t) {
            Log.err(TAG, "AntoDodo.start.run err:", t);
        }
    }

    public void initAntDodoTaskListMap(boolean AutoAntDodoTaskList, boolean dodoTaskList) {
        try {
            //初始化AntDodoTaskListMap
            AntDodoTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            Set<String> blackList = new HashSet<>();
            blackList.add("惊喜任务：添加森林组件");
            blackList.add("连续访问并主动抽卡7天");
            blackList.add("每日任务：帮好友抽卡");
            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一芝麻树");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntDodoTaskListMap.add(task, task);
            }

            if (dodoTaskList) {
                JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.taskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    jo = jo.optJSONObject("data");
                    JSONArray taskGroupInfoList = jo != null ? jo.optJSONArray("taskGroupInfoList") : null;
                    if (taskGroupInfoList != null) {
                        for (int i = 0; i < taskGroupInfoList.length(); i++) {
                            JSONObject antDodoTask = taskGroupInfoList.optJSONObject(i);
                            JSONArray taskInfoList = antDodoTask != null ? antDodoTask.optJSONArray("taskInfoList") : null;
                            for (int j = 0; taskInfoList != null && j < taskInfoList.length(); j++) {
                                JSONObject taskInfo = taskInfoList.optJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                if (taskBaseInfo == null) {
                                    continue;
                                }
                                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                String taskTitle = bizInfo.optString("taskTitle");
                                AntDodoTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                }

                //保存任务到配置文件
                AntDodoTaskListMap.save();
                Log.record("同步任务🉑神奇物种任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntDodoTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntDodo = config.getModelFieldsMap().get("AntDodo");
                    SelectModelField AntDodoTaskList = (SelectModelField) AntDodo.get("AntDodoTaskList");
                    if (AntDodoTaskList == null) {
                        return;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("神奇物种任务", blackList, whiteList, AntDodoTaskList);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initAntDodoTaskListMap err:", t);
        }
    }


    /*
     * 神奇物种
     */
    private long getEndDateTime() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.homePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return 0;
            }
            jo = jo.optJSONObject("data");
            jo = jo != null ? jo.optJSONObject("animalBook") : null;
            if (jo == null) {
                return 0;
            }
            String endDate = jo.optString("endDate") + " 23:59:59";
            return Log.timeToStamp(endDate);
        } catch (Throwable t) {
            Log.err(TAG, "getEndDateTime err:", t);
        }
        return 0;
    }

    private boolean isLastDay() {
        return getEndDateTime() - TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis();
    }

    private void collect() {
        if (Status.hasFlagToday("dodo::collect")) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryAnimalStatus());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                if (data != null && data.optBoolean("collect")) {
                    Log.record("神奇物种卡片今日收集完成！");
                } else {
                    collectAnimalCard();
                }
                Status.flagToday("dodo::collect");
            }
        } catch (Throwable t) {
            Log.err(TAG, "collect err:", t);
        }
    }

    private void collectAnimalCard() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.homePage());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                JSONArray ja = data != null ? data.optJSONArray("limit") : null;
                int index = -1;
                for (int i = 0; ja != null && i < ja.length(); i++) {
                    jo = ja.optJSONObject(i);
                    if (jo != null && "DAILY_COLLECT".equals(jo.optString("actionCode"))) {
                        index = i;
                        break;
                    }
                }
                if (index >= 0 && jo != null) {
                    int leftFreeQuota = jo.optInt("leftFreeQuota");
                    for (int j = 0; j < leftFreeQuota; j++) {
                        jo = MyUtils.newJSONObject(AntDodoRpcCall.collect());
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            data = jo.optJSONObject("data");
                            JSONObject animal = data != null ? data.optJSONObject("animal") : null;
                            if (animal == null) {
                                continue;
                            }
                            Log.forest("神奇物种🦕每日抽卡" + getAnimalInfo(animal));
                            checkAnimalAndGiftToFriend(animal);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectAnimalCard err:", t);
        }
    }

    private void taskList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.taskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            JSONArray taskGroupInfoList = jo != null ? jo.optJSONArray("taskGroupInfoList") : null;
            if (taskGroupInfoList == null) {
                return;
            }
            for (int i = 0; i < taskGroupInfoList.length(); i++) {
                JSONObject antDodoTask = taskGroupInfoList.optJSONObject(i);
                if (antDodoTask == null) {
                    continue;
                }
                String taskGroupName = antDodoTask.optString("taskGroupName");
                JSONArray taskInfoList = antDodoTask.optJSONArray("taskInfoList");
                for (int j = 0; taskInfoList != null && j < taskInfoList.length(); j++) {
                    JSONObject taskInfo = taskInfoList.optJSONObject(j);
                    JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                    if (taskBaseInfo == null) {
                        continue;
                    }
                    String taskStatus = taskBaseInfo.optString("taskStatus");
                    if (TaskStatus.RECEIVED.name().equals(taskStatus)) {
                        continue;
                    }
                    String sceneCode = taskBaseInfo.optString("sceneCode");
                    String taskType = taskBaseInfo.optString("taskType");
                    JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                    String taskTitle = bizInfo.optString("taskTitle");
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        receiveTaskAward(sceneCode, taskType, taskTitle);
                        continue;
                    }
                    if (TaskStatus.TODO.name().equals(taskStatus)) {
                        if (finishTask(sceneCode, taskType, taskTitle)) {
                            receiveTaskAward(sceneCode, taskType, taskTitle);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "taskList err:", t);
        }
    }

    private Boolean finishTask(String sceneCode, String taskType, String taskTitle) {
        try {
            //黑名单任务跳过
            if (AntDodoTaskList.getValue().contains(taskTitle)) {
                return false;
            }
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntDodoTaskList", taskTitle, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("神奇物种🦕完成[" + taskTitle + "]");
                return true;
            }
            // 另一种实现方案（见 TaskAlternative）；物种暂无游戏类任务，属同型兜底
            if (TaskAlternative.hit(jo, sceneCode)) {
                TaskAlternative.trigger(null, taskType, taskTitle, taskType, sceneCode, "神奇物种", msg -> Log.forest(msg));
                return false;
            }
        } catch (Throwable t) {
            Log.err(TAG, "finishTask err:", t);
        }
        return false;
    }

    private void receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.receiveTaskAward(sceneCode, taskType));
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntDodoTaskList", taskTitle, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("神奇物种🦕领取[" + taskTitle + "]奖励");
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveTaskAward err:", t);
        }
    }

    private void propList() {
        try {
            th:
            do {
                JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.propList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.optJSONObject("data");
                JSONArray propList = jo != null ? jo.optJSONArray("propList") : null;
                for (int i = 0; propList != null && i < propList.length(); i++) {
                    JSONObject prop = propList.optJSONObject(i);
                    if (prop == null) {
                        continue;
                    }
                    String propType = prop.optString("propType");
                    JSONObject propConfig = prop.optJSONObject("propConfig");
                    String propGroup = propConfig != null ? propConfig.optString("propGroup") : "";
                    JSONArray propIdList = prop.optJSONArray("propIdList");
                    if (propIdList == null || propIdList.length() == 0) {
                        continue;
                    }
                    String propId = propIdList.optString(0);
                    long recentExpireTime = prop.optLong("recentExpireTime");
                    boolean willExpireSoon = recentExpireTime - TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis();
                    boolean isUseProp = usePropList.getValue().contains(propType);
                    if (!isUseProp && !willExpireSoon) {
                        continue;
                    }
                    if (PropGroup.UNIVERSAL_CARD.name().equals(propGroup)) {
                        if (!usePropUniversalCard(propId, propType)) {
                            continue;
                        }
                    } else {
                        if (PropGroup.COLLECT_ANIMAL.name().equals(propGroup) && !willExpireSoon && useCollectTimingType.getValue() == TimingType.LAST_DAY && !isLastDay()) {
                            continue;
                        }
                        if (!consumeProp(propId, propType)) {
                            continue;
                        }
                    }
                    if (prop.optInt("holdsNum", 1) > 1) {
                        continue th;
                    }
                }
                break;
            } while (true);
        } catch (Throwable th) {
            Log.err(TAG, "propList err:", th);
        }
    }

    // 使用万能卡
    private Boolean usePropUniversalCard(String propId, String propType) {
        try {
            boolean hasMore;
            int pageStart = 0;
            JSONObject animal = null;
            do {
                JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.optJSONObject("data");
                if (jo == null) {
                    break;
                }
                hasMore = jo.optBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.optJSONArray("bookForUserList");
                for (int i = 0; bookForUserList != null && i < bookForUserList.length(); i++) {
                    jo = bookForUserList.optJSONObject(i);
                    if (jo != null && isQueryBookInfo(jo, 0)) {
                        JSONObject animalBookResult = jo.optJSONObject("animalBookResult");
                        String bookId = animalBookResult != null ? animalBookResult.optString("bookId") : null;
                        if (bookId != null) {
                            animal = queryUniversalAnimal(bookId, animal);
                        }
                    }
                }
            } while (hasMore);
            if (animal != null && consumeProp(propId, propType, animal.optString("animalId"))) {
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "usePropUniversalCard err:", t);
        }
        return false;
    }

    private Boolean isQueryBookInfo(JSONObject bookForUser, int type) {
        int statusType = type == 0 ? useUniversalCardBookStatusType.getValue() : giftToFriendBookStatusType.getValue();
        String bookStatus = bookForUser.optString("bookStatus");
        if (!BookStatus.valueOf(bookStatus).match(BookStatusType.types[statusType])) {
            return false;
        }

        int bookCollectedStatusType = type == 0 ? useUniversalCardBookCollectedStatusType.getValue() : giftToFriendBookCollectedStatusType.getValue();
        String bookCollectedStatus = bookForUser.optString("bookCollectedStatus");
        if (!BookCollectedStatus.valueOf(bookCollectedStatus).match(BookCollectedStatusType.types[bookCollectedStatusType])) {
            return false;
        }

        int medalGenerationStatusType = type == 0 ? useUniversalCardMedalGenerationStatusType.getValue() : giftToFriendMedalGenerationStatusType.getValue();
        String medalGenerationStatus = bookForUser.optString("medalGenerationStatus");
        return MedalGenerationStatus.valueOf(medalGenerationStatus).match(MedalGenerationStatusType.types[medalGenerationStatusType]);
    }

    private JSONObject queryUniversalAnimal(String bookId, JSONObject animal) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryBookInfo(bookId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return animal;
            }
            // data: animalBookResult{}
            // data: animalForUserList[]
            JSONObject data = jo.optJSONObject("data");
            JSONArray animalForUserList = data != null ? data.optJSONArray("animalForUserList") : null;
            for (int i = 0; animalForUserList != null && i < animalForUserList.length(); i++) {
                jo = animalForUserList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                int star = jo.optInt("star");
                if (star < FantasticLevelType.stars[useUniversalCardFantasticLevelType.getValue()]) {
                    break;
                }
                JSONObject collectDetail = jo.optJSONObject("collectDetail");
                if (collectDetail == null) {
                    continue;
                }
                int count = collectDetail.optInt("count", 1 << 30);
                boolean hasCollected = collectDetail.optBoolean("hasCollected", false);
                //hasCollected=true(曾经获取);hasCollected=false(未获取)
                boolean isbetteranimal = false;
                //animal为空直接选该animal
                if (animal == null) {
                    isbetteranimal = true;
                }
                //开启优先搜集“未获取”
                else if (useUniversalCardBookCollectedhasCollected.getValue()) {
                    // 规则1: 如果之前的最优是“已收集”，而当前是“未收集”，则当前更好
                    if (animal.optBoolean("hasCollected", true) && !hasCollected) {
                        isbetteranimal = true;
                    }
                    // 规则2: 如果两者状态相同（都已收集或都未收集），则比较数量和星级
                    if (animal.optBoolean("hasCollected", true) == hasCollected) {
                        if (count < animal.optInt("count") || (count == animal.optInt("count") && star > animal.optInt("star"))) {
                            isbetteranimal = true;
                        }
                    }
                }
                //对比搜集数量和星级，优先选数量少的，数量相同选星级高的
                else {
                    if (count < animal.optInt("count") || (count == animal.optInt("count") && star > animal.optInt("star"))) {
                        isbetteranimal = true;
                    }
                }
                if (isbetteranimal) {
                    animal = jo.optJSONObject("animal");
                    if (animal == null) {
                        continue;
                    }
                    animal.put("star", star);
                    animal.put("count", count);
                    animal.put("hasCollected", hasCollected);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryUniversalAnimal err:", t);
        }
        return animal;
    }

    private Boolean consumeProp(String propId, String propType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.consumeProp(propId, propType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }

            jo = jo.optJSONObject("data");
            JSONObject propConfig = jo != null ? jo.optJSONObject("propConfig") : null;
            String propName = propConfig != null ? propConfig.optString("propName") : "";

            JSONObject useResult = jo != null ? jo.optJSONObject("useResult") : null;
            JSONObject animal = useResult != null ? useResult.optJSONObject("animal") : null;
            Log.forest("使用道具🎭[" + propName + "]" + getAnimalInfo(animal));
            checkAnimalAndGiftToFriend(animal);
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "consumeProp err:", t);
        }
        return false;
    }

    private Boolean consumeProp(String propId, String propType, String animalId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.consumeProp(propId, propType, animalId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            jo = jo.optJSONObject("data");
            JSONObject propConfig = jo != null ? jo.optJSONObject("propConfig") : null;
            String propName = propConfig != null ? propConfig.optString("propName") : "";
            JSONObject useResult = jo != null ? jo.optJSONObject("useResult") : null;
            JSONObject animal = useResult != null ? useResult.optJSONObject("animal") : null;
            Log.forest("使用道具🎭[" + propName + "]" + getAnimalInfo(animal));
            checkAnimalAndGiftToFriend(animal);
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    private void collectToFriend() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryFriend());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int count = 0;
                JSONObject data0 = jo.optJSONObject("data");
                JSONObject extend = data0 != null ? data0.optJSONObject("extend") : null;
                JSONArray limitList = extend != null ? extend.optJSONArray("limit") : null;
                for (int i = 0; limitList != null && i < limitList.length(); i++) {
                    JSONObject limit = limitList.optJSONObject(i);
                    if (limit != null && limit.optString("actionCode").equals("COLLECT_TO_FRIEND")) {
                        if (limit.optLong("startTime") > System.currentTimeMillis()) {
                            return;
                        }
                        count = limit.optInt("leftLimit");
                        break;
                    }

                }
                JSONArray friendList = data0 != null ? data0.optJSONArray("friends") : null;
                for (int i = 0; friendList != null && i < friendList.length() && count > 0; i++) {
                    JSONObject friend = friendList.optJSONObject(i);
                    if (friend == null || friend.optBoolean("dailyCollect")) {
                        continue;
                    }
                    String useId = friend.optString("userId");
                    boolean isCollectToFriend = collectToFriendList.getValue().contains(useId);
                    if (collectToFriendType.getValue() != CollectToFriendType.COLLECT) {
                        isCollectToFriend = !isCollectToFriend;
                    }
                    if (!isCollectToFriend) {
                        continue;
                    }
                    jo = MyUtils.newJSONObject(AntDodoRpcCall.collect(useId));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String userName = UserIdMap.getMaskName(useId);
                        JSONObject collectData = jo.optJSONObject("data");
                        JSONObject animal = collectData != null ? collectData.optJSONObject("animal") : null;
                        Log.forest("帮抽卡片🦕[" + userName + "]" + getAnimalInfo(animal));
                        count--;
                    }
                }

            }
        } catch (Throwable t) {
            Log.err(TAG, "collectHelpFriend err:", t);
        }
    }

    private void generateBookMedal() {
        // 图鉴合成状态 合成 可以合成 不能合成
        // medalGenerationStatus: GENERATED CAN_GENERATE CAN_NOT_GENERATE

        // 卡片收集情况 完成 未完成
        // bookCollectedStatus: COMPLETED NOT_COMPLETED

        // 卡片收集进度
        // collectProgress 10/10 2/10
        try {
            boolean hasMore;
            int pageStart = 0;
            do {
                JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.optJSONObject("data");
                if (jo == null) {
                    break;
                }
                hasMore = jo.optBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.optJSONArray("bookForUserList");
                for (int i = 0; bookForUserList != null && i < bookForUserList.length(); i++) {
                    jo = bookForUserList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    MedalGenerationStatus medalGenerationStatus = MedalGenerationStatus.valueOf(jo.optString("medalGenerationStatus"));
                    if (medalGenerationStatus == MedalGenerationStatus.CAN_GENERATE) {
                        if (bookMedalOptions.getValue().contains("generateBookMedal")) {
                            JSONObject animalBookResult = jo.optJSONObject("animalBookResult");
                            if (animalBookResult == null) {
                                continue;
                            }
                            String bookId = animalBookResult.optString("bookId");
                            String ecosystem = animalBookResult.optString("ecosystem");
                            jo = MyUtils.newJSONObject(AntDodoRpcCall.generateBookMedal(bookId));
                            if (!MessageUtil.checkResultCode(TAG, jo)) {
                                break;
                            }
                            Log.forest("神奇物种🦕合成勋章[" + ecosystem + "]");
                        }
                    } else if (medalGenerationStatus == MedalGenerationStatus.CAN_NOT_GENERATE) {
                        if (bookMedalOptions.getValue().contains("collectHistoryAnimal") && Objects.equals("END", jo.optString("bookStatus")) && usePropList.getValue().contains("COLLECT_HISTORY_ANIMAL_7_DAYS") && useProp.getValue()) {
                            //if (Status.canVitalityExchangeBenefitToday("SK20230518000062", 1)) {
                            //AntForestV2.exchangeBenefit("SP20230518000022", "SK20230518000062", "神奇物种抽历史卡机会");
                            //}
                        }
                    }
                }
            } while (hasMore);
        } catch (Throwable t) {
            Log.err(TAG, "generateBookMedal err:", t);
        }
    }

    private static String getAnimalInfo(JSONObject animal) {
        if (animal == null) {
            return "";
        }
        String ecosystem = animal.optString("ecosystem", "未知专辑");
        String name = animal.optString("name", "未知动物");
        String fantasticLevel = animal.optString("fantasticLevel", "Unknown");
        return "#[" + ecosystem + "]" + name + "[" + FantasticLevel.valueOf(fantasticLevel).nickName() + "]";
    }

    private void checkAnimalAndGiftToFriend(JSONObject animal) {
        if (animal == null || !giftToFriend.getValue() || useCollectTimingType.getValue() != TimingType.LAST_DAY) {
            return;
        }
        String targetUserId = getGiftToFriendTargetUserId();
        if (targetUserId == null) {
            return;
        }
        try {
            if (!FantasticLevel.MAGIC.name().equals(animal.optString("fantasticLevel"))) {
                return;
            }
            String bookId = animal.optString("bookId");
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.homePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            jo = data != null ? data.optJSONObject("animalBook") : null;
            if (jo == null || !bookId.equals(jo.optString("bookId"))) {
                return;
            }
            giftToFriend(animal, targetUserId);
        } catch (Throwable t) {
            Log.err(TAG, "checkAnimalAndGiftToFriend err:", t);
        }
    }

    private String getGiftToFriendTargetUserId() {
        Set<String> set = giftToFriendList.getValue();
        if (set.isEmpty()) {
            return null;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            return userId;
        }
        return null;
    }

    private void giftToFriend() {
        String targetUserId = getGiftToFriendTargetUserId();
        if (targetUserId == null) {
            return;
        }
        giftToFriend(targetUserId);
    }

    private void giftToFriend(String targetUserId) {
        try {
            boolean hasMore;
            int pageStart = 0;
            do {
                JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryBookList(9, pageStart));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    break;
                }
                jo = jo.optJSONObject("data");
                if (jo == null) {
                    break;
                }
                hasMore = jo.optBoolean("hasMore");
                pageStart += 9;
                JSONArray bookForUserList = jo.optJSONArray("bookForUserList");
                for (int i = 0; bookForUserList != null && i < bookForUserList.length(); i++) {
                    jo = bookForUserList.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    String collectProgress = jo.optString("collectProgress");
                    if (collectProgress.startsWith("0/") || !isQueryBookInfo(jo, 1)) {
                        continue;
                    }
                    JSONObject animalBookResult = jo.optJSONObject("animalBookResult");
                    if (animalBookResult == null) {
                        continue;
                    }
                    String bookId = animalBookResult.optString("bookId");
                    giftToFriend(bookId, targetUserId);
                }
            } while (hasMore);
        } catch (Throwable t) {
            Log.err(TAG, "giftToFriend err:", t);
        }
    }

    private void giftToFriend(String bookId, String targetUserId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.queryBookInfo(bookId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject dataObj = jo.optJSONObject("data");
            JSONArray animalForUserList = dataObj != null ? dataObj.optJSONArray("animalForUserList") : null;
            if (animalForUserList == null) {
                return;
            }
            int star = FantasticLevelType.stars[giftToFriendFantasticLevelType.getValue()];
            for (int i = 0; i < animalForUserList.length(); i++) {
                JSONObject animalForUser = animalForUserList.optJSONObject(i);
                if (animalForUser == null || animalForUser.optInt("star") < star) {
                    continue;
                }
                JSONObject collectDetail = animalForUser.optJSONObject("collectDetail");
                int count = collectDetail != null ? collectDetail.optInt("count") : 0;
                if (count <= 0) {
                    continue;
                }
                JSONObject animal = animalForUser.optJSONObject("animal");
                if (animal == null) {
                    continue;
                }
                for (int j = 0; j < count; j++) {
                    if (!giftToFriend(animal, targetUserId)) {
                        return;
                    }
                    TimeUtil.sleep(500L);
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "giftToFriend err:", th);
        }
    }

    private Boolean giftToFriend(JSONObject animal, String targetUserId) {
        try {
            String animalId = animal.optString("animalId");
            if (targetUserId.equals(UserIdMap.getCurrentUid())) {
                return false;
            }
            ;
            JSONObject jo = MyUtils.newJSONObject(AntDodoRpcCall.social(animalId, targetUserId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("赠送卡片🦕[" + UserIdMap.getMaskName(targetUserId) + "]" + getAnimalInfo(animal));
                return true;
            }
        } catch (Throwable th) {
            Log.err(TAG, "giftToFriend err:", th);
        }
        return false;
    }

    public enum PropGroup {
        COLLECT_ANIMAL, COLLECT_HISTORY_ANIMAL, ADD_COLLECT_TO_FRIEND_LIMIT, UNIVERSAL_CARD;

        public final String[] nickNames = {"抽卡道具", "历史图鉴随机卡道具", "抽好友卡道具", "万能卡道具"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public enum BookStatus {
        NOT_START, DOING, END;

        public final String[] nickNames = {"未开启", "进行中", "已结束"};

        public String nickName() {
            return nickNames[ordinal()];
        }

        public Boolean match(String status) {
            if (name().equals(NOT_START.name())) {
                return false;
            }
            return name().equals(status) || "ALL".equals(status);
        }
    }

    public enum BookCollectedStatus {
        NOT_COMPLETED, COMPLETED;

        public Boolean match(String status) {
            return name().equals(status) || "ALL".equals(status);
        }
    }

    public enum MedalGenerationStatus {
        CAN_NOT_GENERATE, CAN_GENERATE, GENERATED;

        public final String[] nickNames = {"收集中", "已集齐", "已合成"};

        public String nickName() {
            return nickNames[ordinal()];
        }

        public Boolean match(String status) {
            return name().equals(status) || "ALL".equals(status);
        }
    }

    public enum FantasticLevel {
        COMMON, RARE, MAGIC;

        public final String[] nickNames = {"普通", "稀有", "神奇"};

        public String nickName() {
            return nickNames[ordinal()];
        }
    }

    public interface TimingType {
        int EVERY_DAY = 0;
        int LAST_DAY = 1;

        String[] nickNames = {"每天使用", "专辑最后一天"};
    }

    public interface CollectToFriendType {

        int NONE = 0;
        int COLLECT = 1;
        int NOT_COLLECT = 2;

        String[] nickNames = {"不帮抽", "帮抽已选好友", "帮抽未选好友"};

    }

    public interface BookStatusType {
        int ALL = 0;
        int END = 1;
        int DOING = 2;

        String[] nickNames = {"全部图鉴", "往期图鉴", "本期图鉴"};
        String[] types = {"ALL", "END", "DOING"};
    }

    public interface BookCollectedStatusType {
        int ALL = 0;
        int NOT_COMPLETED = 1;
        int COMPLETED = 2;

        String[] nickNames = {"全部状态", "未完成收集", "已完成收集"};
        String[] types = {"ALL", "NOT_COMPLETED", "COMPLETED"};
    }

    public interface MedalGenerationStatusType {
        int ALL = 0;
        int CAN_NOT_GENERATE = 1;
        int CAN_GENERATE = 2;
        int GENERATED = 3;

        String[] nickNames = {"全部类型", "未能合成", "可以合成", "已经合成"};
        String[] types = {"ALL", "CAN_NOT_GENERATE", "CAN_GENERATE", "GENERATED"};
    }

    public interface FantasticLevelType {
        int COMMON = 0;
        int RARE = 1;
        int MAGIC = 2;

        String[] nickNames = {"普通", "稀有", "神奇"};
        int[] stars = {1, 2, 3};
    }
}