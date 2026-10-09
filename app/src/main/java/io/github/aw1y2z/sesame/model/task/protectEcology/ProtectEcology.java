package io.github.aw1y2z.sesame.model.task.protectEcology;

import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.math.BigDecimal;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAnimal;
import io.github.aw1y2z.sesame.entity.AlipayBeach;
import io.github.aw1y2z.sesame.entity.AlipayMarathon;
import io.github.aw1y2z.sesame.entity.AlipayNewAncientTree;
import io.github.aw1y2z.sesame.entity.AlipayReserve;
import io.github.aw1y2z.sesame.entity.AlipayTree;
import io.github.aw1y2z.sesame.entity.CooperateUser;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.*;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;

public class ProtectEcology extends ModelTask {
    private static final String TAG = ProtectEcology.class.getSimpleName();
    
    @Override
    public String getName() {
        return "保护";
    }
    
    @Override
    public ModelGroup getGroup() {
        return ModelGroup.PROTECT;
    }
    
    private static BooleanModelField cooperateWater;
    private static BooleanModelField cooperateSendCooperateBeckon;
    private static SelectModelField cooperateBeckonList;
    private static SelectAndCountModelField cooperateWaterList;
    private static SelectAndCountModelField cooperateWaterTotalLimitList;
    private static ChoiceModelField protectMarathonType;
    private static SelectAndCountModelField protectMarathonList;
    private static ChoiceModelField protectNewAncientTreeType;
    private static SelectAndCountModelField protectNewAncientTreeList;
    private static BooleanModelField protectTree;
    private static SelectAndCountModelField protectTreeList;
    private static BooleanModelField protectReserve;
    private static SelectAndCountModelField protectReserveList;
    private static BooleanModelField protectReserveMinNum;
    private IntegerModelField protectReserveNum;
    private static BooleanModelField protectBeachMinNum;
    private IntegerModelField protectBeachNum;
    private static BooleanModelField protectBeach;
    private static SelectAndCountModelField protectBeachList;
    private static BooleanModelField protectAnimal;
    private static SelectModelField protectAnimalList;
    
    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(cooperateWater = new BooleanModelField("cooperateWater", "合种 | 浇水", false));
        modelFields.addField(cooperateSendCooperateBeckon = new BooleanModelField("cooperateSendCooperateBeckon", "合种 | 队长召唤队友浇水", false));
        cooperateSendCooperateBeckon.setDescription("独立开关，向选中的合种队友发送官方召唤通知；东八区18点后、仅队长且队友可召唤时执行，每人每合种每天一次、总计最多20次；默认关闭。");
        modelFields.addField(cooperateBeckonList = new SelectModelField("cooperateBeckonList", "合种 | 召唤合种列表", new HashSet<>(), CooperateUser::getList).setDependsOn("cooperateSendCooperateBeckon"));
        modelFields.addField(cooperateWaterList = new SelectAndCountModelField("cooperateWaterList", "合种 | 日浇水量列表", new LinkedHashMap<>(), CooperateUser::getList, "请填写浇水克数(每日)", 0, 1000).setDependsOn("cooperateWater"));
        modelFields.addField(cooperateWaterTotalLimitList = new SelectAndCountModelField("cooperateWaterTotalLimitList", "合种 | 总浇水量列表", new LinkedHashMap<>(), CooperateUser::getList, "请填写浇水克数(上限总量)", 0, 1000).setDependsOn("cooperateWater"));
        modelFields.addField(protectMarathonType = new ChoiceModelField("protectMarathonType", "碳中和 | 马拉松", ProtectType.NONE, ProtectType.nickNames));
        modelFields.addField(protectMarathonList = new SelectAndCountModelField("protectMarathonList", "碳中和 | 马拉松列表", new LinkedHashMap<>(), AlipayMarathon::getList, "请填写助力能量克数(上限总量)").setDependsOn("protectMarathonType"));
        modelFields.addField(protectNewAncientTreeType = new ChoiceModelField("protectNewAncientTreeType", "碳中和 | " + "古树医生", ProtectType.NONE, ProtectType.nickNames));
        modelFields.addField(protectNewAncientTreeList = new SelectAndCountModelField("protectNewAncientTreeList", "碳中和 | 古树医生列表", new LinkedHashMap<>(), AlipayNewAncientTree::getList, "请填写助力能量克数(上限总量)").setDependsOn("protectNewAncientTreeType"));
        modelFields.addField(protectTree = new BooleanModelField("protectTree", "保护森林 | 植树", false));
        modelFields.addField(protectTreeList = new SelectAndCountModelField("protectTreeList", "保护森林 | 植树列表", new LinkedHashMap<>(), AlipayTree::getList, "请填写保护次数(上限总量)").setDependsOn("protectTree"));
        modelFields.addField(protectReserve = new BooleanModelField("protectReserve", "保护动物 | 保护地", false));
        modelFields.addField(protectReserveList = new SelectAndCountModelField("reserveList", "保护动物 | 保护地列表", new LinkedHashMap<>(), AlipayReserve::getList, "请填写保护次数(每日)", 1, 100).setDependsOn("protectReserve"));
        modelFields.addField(protectReserveMinNum = new BooleanModelField("protectReserveMinNum", "保护地 | 最少保护", false));
        modelFields.addField(protectReserveNum = new IntegerModelField("protectReserveNum", "保护地 |最少保护下限", 1).setDependsOn("protectReserveMinNum"));
        modelFields.addField(protectAnimal = new BooleanModelField("protectAnimal", "保护动物 | 护林员", false));
        modelFields.addField(protectAnimalList = new SelectModelField("protectAnimalList", "保护动物 | 护林员列表", new HashSet<>(), AlipayAnimal::getList, "请选择需要点亮的护林员").setDependsOn("protectAnimal"));
        modelFields.addField(protectBeachMinNum = new BooleanModelField("protectBeachMinNum", "保护海洋 | 单个海滩保护", false));
        modelFields.addField(protectBeachNum = new IntegerModelField("protectBeachNum", "保护海洋 |海滩保护下限", 1).setDependsOn("protectBeachMinNum"));
        modelFields.addField(protectBeach = new BooleanModelField("protectBeach", "保护海洋 | 海滩", false));
        modelFields.addField(protectBeachList = new SelectAndCountModelField("protectOceanList", "保护海洋 | 海滩列表", new LinkedHashMap<>(), AlipayBeach::getList, "请填写保护次数(上限总量)").setDependsOn("protectBeach"));
        return modelFields;
    }
    
    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️生态保护:当前为仅收能量时间");
            return false;
        }
        return true;
    }
    
    @Override
    public void run() {
        if (cooperateSendCooperateBeckon.getValue()) cooperateBeckon();
        if (cooperateWater.getValue()) {
            cooperateWater();
        }
        if (protectMarathonType.getValue() != ProtectType.NONE || protectNewAncientTreeType.getValue() != ProtectType.NONE) {
            protectCarbon();
        }
        if (protectTree.getValue()) {
            protectTree();
        }
        if (protectReserve.getValue()) {
            protectReserve();
        }

        if (protectReserveMinNum.getValue()) {
            protectReserveMinNum(protectReserveNum.getValue());
        }

        if (protectAnimal.getValue()) {
            protectAnimal();
        }
        
        if (protectBeachMinNum.getValue()) {
            protectBeachMinNum(protectBeachNum.getValue());
        }
        
        if (protectBeach.getValue()) {
            protectBeach();
        }
    }
    
    public static void initForest() {
        try {
            JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "project");
            if (treeItems == null) {
                return;
            }
            ReserveIdMap.load();
            for (int i = 0; i < treeItems.length(); i++) {
                JSONObject jo = treeItems.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String itemId = jo.optString("itemId");
                String itemName = jo.optString("itemName");
                if (Objects.equals("TREE", jo.optString("projectType"))) {
                    String organization = jo.optString("organization");
                    String region = jo.optString("region");
                    itemName = itemName + "[" + region + "|" + organization + "]";
                    TreeIdMap.add(itemId, itemName + "(" + jo.optInt("energy") + "g)");
                }
                else if (Objects.equals("RESERVE", jo.optString("projectType"))) {
                    ReserveIdMap.add(itemId, itemName + "(" + jo.optInt("energy") + "g)");
                }
                else if (Objects.equals("ANIMAL", jo.optString("projectType"))) {
                    AnimalIdMap.add(itemId, itemName + "(" + jo.optInt("energy") + "g)");
                }
            }
            TreeIdMap.save();
            ReserveIdMap.save();
            AnimalIdMap.save();
            
        }
        catch (Throwable t) {
            Log.err(TAG, "initForest err:", t);
        }
    }
    
    public static void initOcean() {
        try {
            JSONArray cultivationList = queryCultivationList();
            if (cultivationList == null) {
                return;
            }
            BeachIdMap.load();
            for (int i = 0; i < cultivationList.length(); i++) {
                JSONObject jo = cultivationList.optJSONObject(i);
                if (jo == null || !Objects.equals("AVAILABLE", jo.optString("applyAction"))) {
                    continue;
                }
                if (Objects.equals("BEACH", jo.optString("templateSubType")) || Objects.equals("COOPERATE_PLANT", jo.optString("templateType")) || Objects.equals("PROTECT", jo.optString("templateType"))) {
                    BeachIdMap.add(jo.optString("templateCode"), jo.optString("cultivationName") + "(" + jo.optInt("energy") + "g)");
                }
            }
            BeachIdMap.save();
        }
        catch (Throwable t) {
            Log.err(TAG, "initOcean err:", t);
        }
    }
    
    private static JSONObject beckonResponse(String raw) {
        JSONObject root = MyUtils.newJSONObject(raw);
        if (RpcRequestGuard.isFailure(root) || root.has("success") && !Boolean.TRUE.equals(root.opt("success"))
                || root.has("resultCode") && !Set.of("SUCCESS", "100", "200").contains(root.optString("resultCode"))
                || !MessageUtil.checkResultCode(TAG, root)) return null;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return root;
        if (RpcRequestGuard.isFailure(data) || data.has("success") && !Boolean.TRUE.equals(data.opt("success"))
                || data.has("resultCode") && !Set.of("SUCCESS", "100", "200").contains(data.optString("resultCode"))) return null;
        return data;
    }

    private static JSONObject beckonPlant(String id) throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = beckonResponse(CooperateRpcCall.queryCooperatePlant(id));
        JSONObject plant = data == null ? null : data.optJSONObject("cooperatePlant");
        if (plant == null || !id.equals(plant.opt("cooperationId")) || !(plant.opt("admin") instanceof String)
                || plant.optString("admin").isEmpty()) return null;
        return plant;
    }

    private static Map<String, JSONObject> beckonMembers(String id) throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = beckonResponse(CooperateRpcCall.queryCooperateRank("D", id));
        JSONArray rows = data == null ? null : data.optJSONArray("cooperateRankInfos");
        if (rows == null || rows.length() > 200 || data.has("cooperationId") && !id.equals(data.opt("cooperationId"))
                || data.has("hasNext") && !Boolean.FALSE.equals(data.opt("hasNext"))) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("userId") instanceof String) || row.optString("userId").isEmpty()
                    || !(row.opt("canBeckon") instanceof Boolean) || result.put(row.optString("userId"), row) != null) return null;
        }
        return result;
    }

    private static void cooperateBeckon() {
        try {
            TimeUtil.sleep(0);
            if (!cooperateSendCooperateBeckon.getValue()) return;
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) return;
            JSONObject data = beckonResponse(CooperateRpcCall.queryUserCooperatePlantList());
            JSONArray rows = data == null ? null : data.optJSONArray("cooperatePlants");
            if (rows == null || rows.length() > 50) return;
            Map<String, JSONObject> plants = new LinkedHashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !(row.opt("cooperationId") instanceof String) || row.optString("cooperationId").isEmpty()
                        || plants.put(row.optString("cooperationId"), row) != null) return;
            }
            CooperationIdMap.load(uid);
            for (String id : plants.keySet()) {
                JSONObject plant = plants.get(id);
                if (!(plant.opt("name") instanceof String) || plant.optString("name").isEmpty()) plant = beckonPlant(id);
                if (plant != null && plant.opt("name") instanceof String && !plant.optString("name").isEmpty()) CooperationIdMap.add(id, plant.optString("name"));
            }
            TimeUtil.sleep(0);
            if (!uid.equals(UserIdMap.getCurrentUid()) || !CooperationIdMap.save(uid)
                    || MyUtils.getInstance().get(Calendar.HOUR_OF_DAY) < 18) return;
            Set<String> selected = cooperateBeckonList.getValue();
            if (selected == null || selected.isEmpty()) return;
            for (String id : new java.util.ArrayList<>(selected)) {
                if (!plants.containsKey(id)) continue;
                JSONObject plant = beckonPlant(id);
                if (plant == null) return;
                if (!uid.equals(plant.optString("admin"))) continue;
                Map<String, JSONObject> members = beckonMembers(id);
                if (members == null) return;
                for (String target : members.keySet()) {
                    String flag = "cooperate::beckonAttempt::" + id + "::" + target;
                    if (uid.equals(target) || !Boolean.TRUE.equals(members.get(target).opt("canBeckon")) || Status.hasFlagToday(flag)) continue;
                    int attempts = Status.getIntFlagToday("cooperate::beckonAttempts");
                    if (attempts < 0 || attempts >= 20) return;
                    plant = beckonPlant(id);
                    if (plant == null || !uid.equals(plant.optString("admin"))) return;
                    Map<String, JSONObject> fresh = beckonMembers(id);
                    JSONObject current = fresh == null ? null : fresh.get(target);
                    if (fresh == null) return;
                    if (current == null || !Boolean.TRUE.equals(current.opt("canBeckon"))) continue;
                    TimeUtil.sleep(0);
                    if (!cooperateSendCooperateBeckon.getValue() || !cooperateBeckonList.getValue().contains(id)
                            || !uid.equals(UserIdMap.getCurrentUid()) || MyUtils.getInstance().get(Calendar.HOUR_OF_DAY) < 18) return;
                    Status.setIntFlagToday("cooperate::beckonAttempts", attempts + 1);
                    Status.flagToday(flag);
                    JSONObject accepted = beckonResponse(CooperateRpcCall.sendCooperateBeckon(target, id));
                    Map<String, JSONObject> after = beckonMembers(id);
                    JSONObject remaining = after == null ? null : after.get(target);
                    if (accepted == null || remaining == null || !Boolean.FALSE.equals(remaining.opt("canBeckon"))) {
                        Log.record("合种召唤：同队友资格变化未确认，当天不重复");
                        return;
                    }
                    Log.forest("合种🚿[" + plant.optString("name", id) + "]#召唤请求与队友资格回查确认，通知送达以支付宝为准");
                    TimeUtil.sleep(300);
                }
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "cooperateBeckon", t); }
    }

    private static void cooperateWater() {
        try {
            JSONObject jo = MyUtils.newJSONObject(CooperateRpcCall.queryUserCooperatePlantList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            String userId = UserIdMap.getCurrentUid();
            JSONArray cooperatePlants = jo.optJSONArray("cooperatePlants");
            for (int i = 0; cooperatePlants != null && i < cooperatePlants.length(); i++) {
                jo = cooperatePlants.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String cooperationId = jo.optString("cooperationId");
                queryCooperatePlant(userId, cooperationId);
            }
            CooperationIdMap.save(userId);
        }
        catch (Throwable t) {
            Log.err(TAG, "cooperateWater err:", t);
        }
    }
    
    private static void queryCooperatePlant(String userId, String cooperationId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(CooperateRpcCall.queryCooperatePlant(cooperationId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int userCurrentEnergy = jo.optInt("userCurrentEnergy");
            jo = jo.optJSONObject("cooperatePlant");
            if (jo == null) {
                return;
            }
            String name = jo.optString("name");
            CooperationIdMap.add(cooperationId, name);
            Integer waterNum = cooperateWaterList.getValue().get(cooperationId);
            if (waterNum == null) {
                // 未在「合种 | 日浇水量列表」里勾选该合种，静默跳过
                return;
            }
            int waterDayLimit = jo.optInt("waterDayLimit");
            int energyCount = getEnergyCount(userId, cooperationId, waterDayLimit);
            if (energyCount <= 0) {
                Log.record("合种浇水🚿跳过[" + name + "]#可浇量不足10g(日目标" + waterNum + "g,当日上限" + waterDayLimit + "g)");
                return;
            }
            if (energyCount > userCurrentEnergy) {
                Log.record("合种浇水🚿跳过[" + name + "]#能量不足,需" + energyCount + "g,当前" + userCurrentEnergy + "g");
                return;
            }
            if (cooperateWater(userId, cooperationId, energyCount, name)) {
                TimeUtil.sleep(300);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryCooperatePlant err:", t);
        }
    }
    
    private static Boolean cooperateWater(String userId, String cooperationId, int energyCount, String name) {
        try {
            JSONObject jo = MyUtils.newJSONObject(CooperateRpcCall.cooperateWater(userId, cooperationId, energyCount));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("合种浇水🚿[" + name + "]#" + jo.optString("barrageText"));
                Toast.show("合种浇水🚿[" + name + "]#" + jo.optString("barrageText"));
                return true;
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "cooperateWater err:", t);
        }
        return false;
    }
    
    private static int getEnergyCount(String userId, String cooperationId, int waterDayLimit) {
        Integer waterNum = cooperateWaterList.getValue().get(cooperationId);
        if (waterNum == null) {
            return 0;
        }
        // 本次可浇量取三者最小值：日目标剩余、当日上限、总量上限剩余
        int dayWater = getEnergySummation("D", cooperationId, userId);
        if (dayWater < 0) return 0;
        int energyCount = Math.min(waterNum - dayWater, waterDayLimit);
        Integer limitNum = cooperateWaterTotalLimitList.getValue().get(cooperationId);
        if (limitNum != null) {
            int allWater = getEnergySummation("A", cooperationId, userId);
            if (allWater < 0) return 0;
            energyCount = Math.min(energyCount, limitNum - allWater);
        }
        return energyCount < 10 ? 0 : energyCount;
    }
    
    private static int getEnergySummation(String bizType, String cooperationId, String userId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(CooperateRpcCall.queryCooperateRank(bizType, cooperationId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray cooperateRankInfos = jo.optJSONArray("cooperateRankInfos");
                if (cooperateRankInfos == null) return -1;
                for (int i = 0; i < cooperateRankInfos.length(); i++) {
                    jo = cooperateRankInfos.optJSONObject(i);
                    if (jo == null || jo.optString("userId").isEmpty()) return -1;
                    if (Objects.equals(userId, jo.optString("userId"))) {
                        Object value = jo.opt("energySummation");
                        if (!(value instanceof Number) && !(value instanceof String)) return -1;
                        int count = new BigDecimal(value.toString()).intValueExact();
                        return count >= 0 ? count : -1;
                    }
                }
                return 0;
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            if (t instanceof NumberFormatException || t instanceof ArithmeticException) return -1;
            Log.err(TAG, "getEnergySummation err:", t);
        }
        return -1;
    }
    
    private static void protectTree() {
        Map<String, Integer> map = protectTreeList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                continue;
            }
            int projectId = Integer.parseInt(entry.getKey());
            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
            while (exchangeableTree.canExchange && exchangeableTree.certCount < count) {
                String projectName = exchangeableTree.projectName;
                int exchangeCount = exchangeableTree.certCount + 1;
                Log.forest("生态保护🏕️申请[" + projectName + "]#第" + exchangeCount + "次");
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                TimeUtil.sleep(300);
                int before = exchangeableTree.certCount;
                exchangeableTree = queryTreeForExchange(projectId);
                if (exchangeableTree.certCount <= before) {
                    Log.record("生态保护：证书数量未增加，停止本轮申请");
                    break;
                }
            }
        }
    }
    
    private static void protectReserve() {
        Map<String, Integer> map = protectReserveList.getValue();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count < 0) {
                continue;
            }
            int projectId = Integer.parseInt(entry.getKey());
            while (Status.canExchangeReserveToday(projectId, count)) {
                ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
                if (!exchangeableTree.canExchange) {
                    break;
                }
                String projectName = exchangeableTree.projectName;
                int exchangeCount = Status.getExchangeReserveCountToday(projectId) + 1;
                Log.forest("生态保护🏕️申请[" + projectName + "]#第" + exchangeCount + "次");
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                Status.exchangeReserveToday(projectId);
                TimeUtil.sleep(300);
            }
        }
    }
    
    private static void protectAnimal() {
        Set<String> set = protectAnimalList.getValue();
        for (String s : set) {
            int projectId = Integer.parseInt(s);
            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
            while (exchangeableTree.canExchange) {
                String projectName = exchangeableTree.projectName;
                if (!exchangeTree(projectId, projectName)) {
                    break;
                }
                exchangeableTree = queryTreeForExchange(projectId);
                TimeUtil.sleep(300);
            }
        }
    }
    
    public static JSONArray queryTreeItemsForExchange(String applyActions, String itemTypes) {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.queryTreeItemsForExchange(applyActions, itemTypes));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                return jo.optJSONArray("treeItems");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryTreeItemsForExchange err:", t);
        }
        return null;
    }
    
    private static ExchangeableTree queryTreeForExchange(int projectId) {
        ExchangeableTree exchangeableTree = new ExchangeableTree(projectId);
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.queryTreeForExchange(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return exchangeableTree;
            }
            String applyAction = jo.optString("applyAction");
            int currentEnergy = new BigDecimal(String.valueOf(jo.opt("currentEnergy"))).intValueExact();
            JSONArray subTreeVOs = jo.optJSONArray("subTreeVOs");
            jo = jo.optJSONObject("exchangeableTree");
            if (jo == null) {
                return exchangeableTree;
            }
            exchangeableTree.certCount = new BigDecimal(String.valueOf(jo.opt("certCount"))).intValueExact();
            exchangeableTree.projectName = jo.optString("projectName");
            int energy = new BigDecimal(String.valueOf(jo.opt("energy"))).intValueExact();
            if (currentEnergy < 0 || energy < 0 || exchangeableTree.certCount < 0) {
                return exchangeableTree;
            }
            if (!Objects.equals("AVAILABLE", applyAction)) {
                Log.record("生态保护🏕️保护[" + exchangeableTree.projectName + "]停止:数量不足");
                return exchangeableTree;
            }
            if (currentEnergy < energy) {
                Log.record("生态保护🏕️保护[" + exchangeableTree.projectName + "]停止:能量不足");
                return exchangeableTree;
            }
            if (Objects.equals("ANIMAL", jo.optString("type"))) {
                if (exchangeableTree.certCount == 0) {
                    if (subTreeVOs == null || subTreeVOs.length() == 0) return exchangeableTree;
                    for (int i = 0; i < subTreeVOs.length(); i++) {
                        jo = subTreeVOs.optJSONObject(i);
                        if (jo == null) {
                            return exchangeableTree;
                        }
                        int certCountForAlias = new BigDecimal(String.valueOf(jo.opt("certCountForAlias"))).intValueExact();
                        if (certCountForAlias < 0) return exchangeableTree;
                        if (certCountForAlias == 0) {
                            exchangeableTree.canExchange = true;
                            break;
                        }
                    }
                    if (!exchangeableTree.canExchange) {
                        applyGoldAnimalCert(projectId);
                    }
                }
            }
            else {
                exchangeableTree.canExchange = true;
            }
        }
        catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "queryTreeForExchange err:", t);
        }
        return exchangeableTree;
    }
    
    private static Boolean exchangeTree(int projectId, String projectName) {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.exchangeTree(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            int vitalityAmount = jo.optInt("vitalityAmount", 0);
            String str = "";
            if (vitalityAmount > 0) {
                str = "#获得[" + vitalityAmount + "活力值]";
            }
            jo = jo.optJSONObject("userCertificate");
            if (jo != null && Objects.equals("ANIMAL", jo.optString("type"))) {
                str = "#获得[" + jo.optString("projectName") + "]";
            }
            Log.forest("生态保护🏕️保护[" + projectName + "]" + str);
            return true;
        }
        catch (Throwable t) {
            Log.err(TAG, "exchangeTree err:", t);
        }
        return false;
    }
    
    private static void applyGoldAnimalCert(int projectId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.applyGoldAnimalCert(projectId));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("goldAnimalCertVO");
            if (jo != null) {
                Log.forest("生态保护🏕️点亮[" + jo.optString("name") + "]");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "applyGoldAnimalCert err:", t);
        }
    }
    
    private static void protectCarbon() {
        try {
            JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "special");
            if (treeItems == null) {
                return;
            }
            for (int i = 0; i < treeItems.length(); i++) {
                JSONObject jo = treeItems.optJSONObject(i);
                jo = jo != null ? jo.optJSONObject("extendInfo") : null;
                if (jo == null) {
                    continue;
                }
                String activityName = jo.optString("activityName");
                if (Objects.equals("marathon", jo.optString("activityType"))) {
                    String activityId = StringUtil.getSubString(jo.optString("actionUrl"), "activityId%3D", "%26");
                    MarathonIdMap.add(activityId, activityName);
                    if (protectMarathonType.getValue() != ProtectType.NONE) {
                        marathonQueryActivity(activityId);
                    }
                }
                else if (activityName.contains("古树医生")) {
                    String activityId = StringUtil.getSubString(jo.optString("actionUrl"), "activityId%3D", "%26");
                    NewAncientTreeIdMap.add(activityId, activityName);
                    if (protectNewAncientTreeType.getValue() != ProtectType.NONE) {
                        carbonQueryActivity(activityId);
                    }
                }
            }
            MarathonIdMap.save();
            NewAncientTreeIdMap.save();
        }
        catch (Throwable t) {
            Log.err(TAG, "protectCarbon err:", t);
        }
    }
    
    private static void marathonQueryActivity(String activityId) {
        try {
            JSONObject paramMap = MyUtils.newJSONObject();
            paramMap.put("donateQueryActionParam", "marathonWater");
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.doRubickActivity("marathonHome", activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("resultData");
            if (jo == null) {
                return;
            }
            int currentEnergy = jo.optInt("currentEnergy", -1);
            int firstDonated = 0;
            // 如果未曾助力:助力一次
            Integer donateNumber = protectMarathonList.getValue().get(activityId);
            JSONObject donateConfigVO = jo.optJSONObject("donateConfigVO");
            if (donateConfigVO == null || currentEnergy < 0) return;
            if (!jo.optBoolean("certLockStatus", true)) {
                int donateNum = donateConfigVO.optInt("donateNum", -1);
                if (donateNum <= 0) return;
                if (protectMarathonType.getValue() == ProtectType.SELECT) {
                    if (donateNumber == null || donateNumber < donateNum) {
                        return;
                    }
                }
                if (currentEnergy < donateNum || !carbonCharityActivity("marathonWater", activityId, donateNum)) return;
                currentEnergy -= donateNum;
                firstDonated = donateNum;
            }
            if (protectMarathonType.getValue() == ProtectType.COLLECT) {
                // 集邮模式:不再助力
                return;
            }
            JSONObject activityCertVO = jo.optJSONObject("activityCertVO");
            int energy = activityCertVO != null ? activityCertVO.optInt("energy", -1) : -1;
            if (energy < 0 || donateNumber == null) return;
            long remaining = (long) donateNumber - energy - firstDonated;
            int secondDonateMinNum = donateConfigVO.optInt("secondDonateMinNum", -1);
            if (secondDonateMinNum > 0 && remaining >= secondDonateMinNum && currentEnergy >= remaining) {
                carbonCharityActivity("marathonWater", activityId, (int) remaining);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "marathonQueryActivity err:", t);
        }
    }
    
    private static void carbonQueryActivity(String activityId) {
        try {
            JSONObject paramMap = MyUtils.newJSONObject();
            paramMap.put("donateQueryActionParam", "carbonWater");
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.doRubickActivity("carbonHome", activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("resultData");
            if (jo == null) {
                return;
            }
            int currentEnergy = jo.optInt("currentEnergy", -1);
            int firstDonated = 0;
            // 如果未曾助力:助力一次
            Integer donateNumber = protectNewAncientTreeList.getValue().get(activityId);
            JSONObject donateConfigVO = jo.optJSONObject("donateConfigVO");
            if (donateConfigVO == null || currentEnergy < 0) return;
            if (!jo.optBoolean("certLockStatus", true)) {
                int donateNum = donateConfigVO.optInt("donateNum", -1);
                if (donateNum <= 0) return;
                if (protectNewAncientTreeType.getValue() == ProtectType.SELECT) {
                    if (donateNumber == null || donateNumber < donateNum) {
                        return;
                    }
                }
                if (currentEnergy < donateNum || !carbonCharityActivity("carbonWater", activityId, donateNum)) return;
                currentEnergy -= donateNum;
                firstDonated = donateNum;
            }
            if (protectNewAncientTreeType.getValue() == ProtectType.COLLECT) {
                // 集邮模式:不再助力
                return;
            }
            JSONObject activityCertVO = jo.optJSONObject("activityCertVO");
            int energy = activityCertVO != null ? activityCertVO.optInt("energy", -1) : -1;
            if (energy < 0 || donateNumber == null) return;
            long remaining = (long) donateNumber - energy - firstDonated;
            int secondDonateMinNum = donateConfigVO.optInt("secondDonateMinNum", -1);
            if (secondDonateMinNum > 0 && remaining >= secondDonateMinNum && currentEnergy >= remaining) {
                carbonCharityActivity("carbonWater", activityId, (int) remaining);
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "carbonQueryActivity err:", t);
        }
    }
    
    private static Boolean carbonCharityActivity(String actionCode, String activityId, int donateNum) {
        try {
            JSONObject paramMap = MyUtils.newJSONObject();
            paramMap.put("donateNum", donateNum);
            paramMap.put("incrNum", donateNum);
            JSONObject jo = MyUtils.newJSONObject(ProtectTreeRpcCall.doRubickActivity(actionCode, activityId, paramMap));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject resultData = jo.optJSONObject("resultData");
            jo = resultData != null ? resultData.optJSONObject("activityCertVO") : null;
            if (jo == null) {
                return false;
            }
            String name = jo.optString("name");
            int energy = jo.optInt("energy");
            Log.forest("生态保护🏕️助力[" + name + "]#累计[" + energy + "g能量]");
            return true;
        }
        catch (Throwable t) {
            Log.err(TAG, "carbonCharityActivity err:", t);
        }
        return false;
    }
    
    private static JSONArray queryCultivationList() {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectOceanRpcCall.queryCultivationList());
            if (MessageUtil.checkResultCode(TAG, jo)) {
                return jo.optJSONArray("cultivationItemVOList");
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "queryCultivationList err:", t);
        }
        return null;
    }
    private static void protectReserveMinNum(int protectReserveNum) {
        if (protectReserveNum > 0) {
            try {
                JSONArray treeItems = queryTreeItemsForExchange("AVAILABLE", "project");
                if (treeItems == null) {
                    return;
                }
                for (int i = 0; i < treeItems.length(); i++) {
                    JSONObject jo = treeItems.optJSONObject(i);
                    if (jo == null) {
                        continue;
                    }
                    String itemId = jo.optString("itemId");
                    String itemName = jo.optString("itemName");
                    int certCountForAlias=jo.optInt("certCountForAlias");
                    int projectId=jo.optInt("projectId");
                    if (Objects.equals("RESERVE", jo.optString("projectType"))) {

                        if(certCountForAlias>=protectReserveNum)
                        {continue;}
                        while (certCountForAlias<protectReserveNum) {
                            ExchangeableTree exchangeableTree = queryTreeForExchange(projectId);
                            if (!exchangeableTree.canExchange) {
                                break;
                            }
                            String projectName = exchangeableTree.projectName;
                            certCountForAlias = certCountForAlias + 1;
                            Log.forest("生态保护🏕️申请[" + projectName + "]#第" + certCountForAlias + "次");
                            if (!exchangeTree(projectId, projectName)) {
                                break;
                            }
                            Status.exchangeReserveToday(projectId);
                            TimeUtil.sleep(300);
                        }
                    }
                }
            }
            catch (Throwable t) {
                Log.err(TAG, "protectReserveMinNum err:", t);
            }
        }
    }



    private static void protectBeachMinNum(int protectBeachNum) {
        if (protectBeachNum > 0) {
            try {
                JSONArray cultivationList = queryCultivationList();
                if (cultivationList == null) {
                    return;
                }
                for (int i = 0; i < cultivationList.length(); i++) {
                    JSONObject jo = cultivationList.optJSONObject(i);
                    if (jo == null || !Objects.equals("AVAILABLE", jo.optString("applyAction"))) {
                        continue;
                    }
                    String cultivationCode = jo.optString("cultivationCode");
                    JSONObject projectConfigVO = jo.optJSONObject("projectConfigVO");
                    if (projectConfigVO == null) {
                        continue;
                    }
                    String projectCode = projectConfigVO.optString("code");
                    int certNum = jo.optInt("certNum");
                    int energy = jo.optInt("energy", 0);
                    if (energy > 1000) {
                        continue;
                    }
                    while (protectBeachNum > certNum && queryCultivationDetail(cultivationCode, projectCode)) {
                        certNum++;
                        TimeUtil.sleep(300);
                    }
                }
            }
            catch (Throwable t) {
                Log.err(TAG, "protectBeachMinNum err:", t);
            }
        }
        ;
    }
    
    private static void protectBeach() {
        Map<String, Integer> map = protectBeachList.getValue();
        try {
            JSONArray cultivationList = queryCultivationList();
            if (cultivationList == null) {
                return;
            }
            for (int i = 0; i < cultivationList.length(); i++) {
                JSONObject jo = cultivationList.optJSONObject(i);
                if (jo == null || !Objects.equals("AVAILABLE", jo.optString("applyAction"))) {
                    continue;
                }
                String cultivationCode = jo.optString("cultivationCode");
                JSONObject projectConfigVO = jo.optJSONObject("projectConfigVO");
                if (projectConfigVO == null) {
                    continue;
                }
                String projectCode = projectConfigVO.optString("code");
                int certNum = jo.optInt("certNum");
                Integer count = map.get(cultivationCode);
                if (count == null) {
                    continue;
                }
                while (count > certNum && queryCultivationDetail(cultivationCode, projectCode)) {
                    certNum++;
                    TimeUtil.sleep(300);
                }
            }
        }
        catch (Throwable t) {
            Log.err(TAG, "protectBeach err:", t);
        }
    }
    
    private static Boolean queryCultivationDetail(String cultivationCode, String projectCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectOceanRpcCall.queryCultivationDetail(cultivationCode, projectCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject userInfoVO = jo.optJSONObject("userInfoVO");
            int currentEnergy = userInfoVO != null ? userInfoVO.optInt("currentEnergy") : 0;
            jo = jo.optJSONObject("cultivationDetailVO");
            if (jo == null) {
                return false;
            }
            String cultivationName = jo.optString("cultivationName");
            if (!Objects.equals("AVAILABLE", jo.optString("applyAction"))) {
                Log.record("保护海洋🏖️保护[" + cultivationName + "]停止:数量不足");
                return false;
            }
            if (currentEnergy < jo.optInt("energy")) {
                Log.record("保护海洋🏖️保护[" + cultivationName + "]停止:能量不足");
                return false;
            }
            int count = jo.optInt("certNum") + 1;
            Log.forest("保护海洋🏖️申请[" + cultivationName + "]#第" + count + "次");
            return oceanExchangeTree(cultivationCode, projectCode, cultivationName);
        }
        catch (Throwable t) {
            Log.err(TAG, "queryCultivationDetail err:", t);
        }
        return false;
    }
    
    private static Boolean oceanExchangeTree(String cultivationCode, String projectCode, String cultivationName) {
        try {
            JSONObject jo = MyUtils.newJSONObject(ProtectOceanRpcCall.oceanExchangeTree(cultivationCode, projectCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray awardInfos = jo.optJSONArray("rewardItemVOs");
            StringBuilder award = new StringBuilder();
            for (int i = 0; awardInfos != null && i < awardInfos.length(); i++) {
                jo = awardInfos.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (i > 0) {
                    award.append(";");
                }
                award.append(jo.optString("name")).append("*").append(jo.optInt("num"));
            }
            Log.forest("保护海洋🏖️保护[" + cultivationName + "]#获得[" + award + "]");
            return true;
        }
        catch (Throwable t) {
            Log.err(TAG, "oceanExchangeTree err:", t);
        }
        return false;
    }
    
    public static class ExchangeableTree {
        boolean canExchange;
        int projectId;
        String projectName;
        int certCount;
        
        ExchangeableTree(int projectId) {
            canExchange = false;
            this.projectId = projectId;
        }
    }
    
    public interface ProtectType {
        int NONE = 0;
        int COLLECT = 1;
        int SELECT = 2;
        
        String[] nickNames = {"不保护", "集邮模式", "列表模式"};
    }
}
