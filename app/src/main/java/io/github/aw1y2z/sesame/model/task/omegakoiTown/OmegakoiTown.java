package io.github.aw1y2z.sesame.model.task.omegakoiTown;

import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.DailyTask;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.TimeUtil;

public class OmegakoiTown extends ModelTask {
    private static final String TAG = OmegakoiTown.class.getSimpleName();

    public enum RewardType {
        gold, diamond, dyestuff, rubber, glass, certificate, shipping, tpuPhoneCaseCertificate,
        glassPhoneCaseCertificate, canvasBagCertificate, notebookCertificate, box, paper, cotton;

        public static final CharSequence[] rewardNames = {"金币", "钻石", "颜料", "橡胶", "玻璃", "合格证", "包邮券", "TPU手机壳合格证",
                "玻璃手机壳合格证", "帆布袋合格证", "记事本合格证", "快递包装盒", "纸张", "棉花"};

        public CharSequence rewardName() {
            return rewardNames[ordinal()];
        }
    }

    public enum HouseType {
        houseTrainStation, houseStop, houseBusStation, houseGas, houseSchool, houseService, houseHospital, housePolice,
        houseBank, houseRecycle, houseWasteTreatmentPlant, houseMetro, houseKfc, houseManicureShop, housePhoto, house5g,
        houseGame, houseLucky, housePrint, houseBook, houseGrocery, houseScience, housemarket1, houseMcd,
        houseStarbucks, houseRestaurant, houseFruit, houseDessert, houseClothes, zhiketang, houseFlower, houseMedicine,
        housePet, houseChick, houseFamilyMart, houseHouse, houseFlat, houseVilla, houseResident, housePowerPlant,
        houseWaterPlant, houseDailyChemicalFactory, houseToyFactory, houseSewageTreatmentPlant, houseSports,
        houseCinema, houseCotton, houseMarket, houseStadium, houseHotel, housebusiness, houseOrchard, housePark,
        houseFurnitureFactory, houseChipFactory, houseChemicalPlant, houseThermalPowerPlant, houseExpressStation,
        houseDormitory, houseCanteen, houseAdministrationBuilding, houseGourmetPalace, housePaperMill,
        houseAuctionHouse, houseCatHouse, houseStarPickingPavilion;

        public static final CharSequence[] houseNames = {"火车站", "停车场", "公交站", "加油站", "学校", "服务大厅", "医院", "警察局", "银行",
                "回收站", "垃圾处理厂", "地铁站", "快餐店", "美甲店", "照相馆", "移动营业厅", "游戏厅", "运气屋", "打印店", "书店", "杂货店", "科普馆", "菜场",
                "汉堡店", "咖啡厅", "餐馆", "水果店", "甜品店", "服装店", "支课堂", "花店", "药店", "宠物店", "庄园", "全家便利店", "平房", "公寓", "别墅",
                "居民楼", "风力发电站", "自来水厂", "日化厂", "玩具厂", "污水处理厂", "体育馆", "电影院", "新疆棉花厂", "超市", "游泳馆", "酒店", "商场", "果园",
                "公园", "家具厂", "芯片厂", "化工厂", "火电站", "快递驿站", "宿舍楼", "食堂", "行政楼", "美食城", "造纸厂", "拍卖行", "喵小馆", "神秘研究所"};

        public CharSequence houseName() {
            return houseNames[ordinal()];
        }
    }

    @Override
    public String getName() {
        return "小镇";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }


    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        return modelFields;
    }

    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            return false;
        }
        long executeTime = RuntimeInfo.getInstance().getLong("omegakoiTown", 0);
        return System.currentTimeMillis() - executeTime >= 21600000;
    }

    public void run() {
        try {
            RuntimeInfo.getInstance().put("omegakoiTown", System.currentTimeMillis());
            getUserTasks();
            completeQuests();
            getSignInStatus();
            houseProduct();
        } catch (Throwable t) {
            Log.err(TAG, "start.run err:", t);
        }
    }

    private void getUserTasks() {
        try {
            String s = OmegakoiTownRpcCall.getUserTasks();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject result = jo.optJSONObject("result");
                JSONArray tasks = result != null ? result.optJSONArray("tasks") : null;
                for (int i = 0; tasks != null && i < tasks.length(); i++) {
                    jo = tasks.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    boolean done = jo.optBoolean("done");
                    boolean hasRewarded = jo.optBoolean("hasRewarded");
                    if (done && !hasRewarded) {
                        JSONObject task = jo.optJSONObject("task");
                        if (task == null) {
                            continue;
                        }
                        String name = task.optString("name");
                        String taskId = task.optString("taskId");
                        if ("dailyBuild".equals(taskId))
                            continue;
                        JSONObject reward = task.optJSONObject("reward");
                        if (reward == null) {
                            continue;
                        }
                        int amount = reward.optInt("amount");
                        String itemId = reward.optString("itemId");
                        try {
                            RewardType rewardType = RewardType.valueOf(itemId);
                            jo = MyUtils.newJSONObject(OmegakoiTownRpcCall.triggerTaskReward(taskId));
                            if (jo.optBoolean("success")) {
                                Log.other("小镇任务🌇[" + name + "]#" + amount + "[" + rewardType.rewardName() + "]");
                            }
                        } catch (Throwable th) {
                            Log.i(TAG, "spec RewardType:" + itemId + ";未知的类型");
                        }
                    }
                }
            } else {
                Log.other(jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "getUserTasks err:", t);
        }
    }

    /**
     * 小镇「场景任务」：{@code scenario.getUserQuests} 拉到的 quest 需要
     * {@code scenario.completeQuest} 才算完成/发奖——与 task 那套（getUserTasks / triggerTaskReward）
     * 是两套接口。原先只实现了 task 那套，completeQuest 从未被调用，场景任务一直没人做。
     *
     * <p>安全约定：只为**明确标记未完成**的 quest 提交；识别不到状态字段时记录原因、不提交，
     * 避免结构不符时盲发请求（同类盲发曾触发风控 1009）。
     */
    private void completeQuests() {
        final String scenarioId = "shopNewestTips";
        try {
            String s = OmegakoiTownRpcCall.getUserQuests();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.i(TAG, "getUserQuests 失败#resultCode=" + jo.optString("resultCode") + "#未提交场景任务");
                return;
            }
            JSONObject result = jo.optJSONObject("result");
            JSONArray quests = result == null ? null : result.optJSONArray("quests");
            if (quests == null) {
                Log.i(TAG, "getUserQuests 结构未识别#缺少quests数组，未提交場景任务");
                return;
            }
            for (int i = 0; i < quests.length(); i++) {
                JSONObject quest = quests.optJSONObject(i);
                if (quest == null) {
                    continue;
                }
                Boolean done = questDone(quest);
                if (done == null) {
                    Log.i(TAG, "quest 状态未识别#questId=" + quest.optString("questId") + "#未提交");
                    continue;
                }
                if (done) {
                    continue;
                }
                String questId = quest.optString("questId");
                if (questId.isEmpty()) {
                    continue;
                }
                try {
                    JSONObject res = MyUtils.newJSONObject(OmegakoiTownRpcCall.completeQuest(questId, scenarioId));
                    if (res.optBoolean("success")) {
                        Log.other("小镇任务🌇[场景任务" + quest.optString("name", questId) + "]#完成");
                    } else {
                        Log.i(TAG, "completeQuest 失败:" + questId + "#" + res.optString("resultDesc"));
                    }
                } catch (Throwable th) {
                    Log.err(TAG, "completeQuest err:", th);
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable t) {
            Log.err(TAG, "completeQuests err:", t);
        }
    }

    /** quest 是否已完成；识别不到状态字段返回 {@code null}（调用方据此不提交，避免盲发）。 */
    private static Boolean questDone(JSONObject quest) {
        for (String flag : new String[]{"done", "completed", "hasCompleted", "finishFlag", "hasRewarded"}) {
            if (quest.opt(flag) instanceof Boolean) {
                return (Boolean) quest.opt(flag);
            }
        }
        String status = quest.optString("status");
        if ("DONE".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)
                || "FINISHED".equalsIgnoreCase(status)) return true;
        if ("TODO".equalsIgnoreCase(status) || "NOT_DONE".equalsIgnoreCase(status)) return false;
        return null;
    }

    private void getSignInStatus() {
        if (DailyTask.skip("town::sign", "小镇签到")) return;
        try {
            String s = OmegakoiTownRpcCall.getSignInStatus();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject result = jo.optJSONObject("result");
                if (result == null || !(result.opt("signed") instanceof Boolean)) return;
                boolean signed = result.optBoolean("signed");
                if (signed) { DailyTask.done("town::sign"); return; }
                if (!signed) {
                    jo = MyUtils.newJSONObject(OmegakoiTownRpcCall.signIn());
                    if (!Boolean.TRUE.equals(jo.opt("success"))) return;
                    JSONObject signResult = jo.optJSONObject("result");
                    JSONArray diffItems = signResult != null ? signResult.optJSONArray("diffItems") : null;
                    JSONObject diffItem = diffItems != null ? diffItems.optJSONObject(0) : null;
                    if (diffItem == null) {
                        return;
                    }
                    int amount = diffItem.optInt("amount");
                    String itemId = diffItem.optString("itemId");
                    RewardType rewardType = RewardType.valueOf(itemId);
                    Log.other("小镇签到[" + rewardType.rewardName() + "]#" + amount);
                    DailyTask.done("town::sign");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "getSignInStatus err:", t);
        }
    }

    private void houseProduct() {
        try {
            String s = OmegakoiTownRpcCall.houseProduct();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject result = jo.optJSONObject("result");
                JSONArray userHouses = result != null ? result.optJSONArray("userHouses") : null;
                for (int i = 0; userHouses != null && i < userHouses.length(); i++) {
                    jo = userHouses.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    JSONObject extraInfo = jo.optJSONObject("extraInfo");
                    if (extraInfo == null || !extraInfo.has("toBeCollected"))
                        continue;
                    JSONArray toBeCollected = extraInfo.optJSONArray("toBeCollected");
                    if (toBeCollected != null && toBeCollected.length() > 0) {
                        JSONObject firstCollect = toBeCollected.optJSONObject(0);
                        double amount = firstCollect != null ? firstCollect.optDouble("amount") : 0;
                        if (amount < 500)
                            continue;
                        String houseId = jo.optString("houseId");
                        long id = jo.optLong("id");
                        jo = MyUtils.newJSONObject(OmegakoiTownRpcCall.collect(houseId, id));
                        if (jo.optBoolean("success")) {
                            HouseType houseType = HouseType.valueOf(houseId);
                            JSONObject collectResult = jo.optJSONObject("result");
                            JSONArray rewards = collectResult != null ? collectResult.optJSONArray("rewards") : null;
                            JSONObject firstReward = rewards != null ? rewards.optJSONObject(0) : null;
                            String itemId = firstReward != null ? firstReward.optString("itemId") : "";
                            RewardType rewardType = RewardType.valueOf(itemId);
                            Log.other("小镇收金🌇[" + houseType.houseName() + "]#" + String.format("%.2f", amount)
                                    + rewardType.rewardName());
                        }
                    }
                }
            } else {
                Log.other(jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "getUserTasks err:", t);
        }
    }

}
