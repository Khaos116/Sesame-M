package io.github.aw1y2z.sesame.model.task.greenFinance;

import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TreeMap;

/**
 * @author Constanline
 * @since 2023/09/08
 */
public class GreenFinance extends ModelTask {
    private static final String TAG = GreenFinance.class.getSimpleName();

    private BooleanModelField greenFinanceLsxd;
    private BooleanModelField greenFinanceLsbg;
    private BooleanModelField greenFinanceLscg;
    private BooleanModelField greenFinanceLswl;
    private BooleanModelField greenFinanceWdxd;
    private BooleanModelField greenFinanceDonation;
    /**
     * 是否收取好友金币
     */
    private BooleanModelField greenFinancePointFriend;

    @Override
    public String getName() {
        return "经营";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.GREEN_FINANCE;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(greenFinanceLsxd = new BooleanModelField("greenFinanceLsxd", "打卡 | 绿色行动", false));
        modelFields.addField(greenFinanceLscg = new BooleanModelField("greenFinanceLscg", "打卡 | 绿色采购", false));
        modelFields.addField(greenFinanceLsbg = new BooleanModelField("greenFinanceLsbg", "打卡 | 绿色办公", false));
        modelFields.addField(greenFinanceWdxd = new BooleanModelField("greenFinanceWdxd", "打卡 | 绿色销售", false));
        modelFields.addField(greenFinanceLswl = new BooleanModelField("greenFinanceLswl", "打卡 | 绿色物流", false));
        modelFields.addField(greenFinancePointFriend = new BooleanModelField("greenFinancePointFriend", "收取 | 好友金币", false));
        modelFields.addField(greenFinanceDonation = new BooleanModelField("greenFinanceDonation", "捐助 | 快过期金币", false));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️绿色经营:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void  run() {
        String s = GreenFinanceRpcCall.greenFinanceIndex();
        try {
            JSONObject jo = MyUtils.newJSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.i(TAG, jo.optString("resultDesc"));
                return;
            }
            JSONObject result = jo.optJSONObject("result");
            if (result == null || !result.optBoolean("greenFinanceSigned")) {
                Log.i("绿色经营📊未开通");
                return;
            }
            JSONObject mcaGreenLeafResult = result.optJSONObject("mcaGreenLeafResult");
            JSONArray greenLeafList = mcaGreenLeafResult != null ? mcaGreenLeafResult.optJSONArray("greenLeafList") : null;
            String currentCode = "";
            JSONArray bsnIds = new JSONArray();
            for (int i = 0; greenLeafList != null && i < greenLeafList.length(); i++) {
                JSONObject greenLeaf = greenLeafList.optJSONObject(i);
                if (greenLeaf == null) {
                    continue;
                }
                String code = greenLeaf.optString("code");
                String bsnId = greenLeaf.optString("bsnId");
                if (code.isEmpty() || bsnId.isEmpty()) {
                    continue;
                }
                if (!currentCode.equals(code) && bsnIds.length() > 0) {
                    batchSelfCollect(bsnIds);
                    bsnIds = new JSONArray();
                }
                currentCode = code;
                bsnIds.put(bsnId);
            }
            if (bsnIds.length() > 0) {
                batchSelfCollect(bsnIds);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "index err:", th);
        }

        signIn("PLAY102632271");
//            signIn("PLAY102932217");
        signIn("PLAY102232206");

        if(TimeUtil.isNowAfterOrCompareTimeStr("0836")) {
            //执行打卡
            behaviorTick();
            //收好友金币
            batchStealFriend();
        }
        //捐助
        donation();
        //评级奖品（CP14664674 已结束，暂时注释）
        // prizes();
        //绿色经营
        GreenFinanceRpcCall.doTask("AP13159535", TAG, "绿色经营📊");
        TimeUtil.sleep(500);
    }

    /**
     * 批量收取
     *
     * @param bsnIds Ids
     */
    private void batchSelfCollect(final JSONArray bsnIds) {
        String s = GreenFinanceRpcCall.batchSelfCollect(bsnIds);
        try {
            JSONObject joSelfCollect = MyUtils.newJSONObject(s);
            if (joSelfCollect.optBoolean("success")) {
                JSONObject collectResult = joSelfCollect.optJSONObject("result");
                int totalCollectPoint = collectResult != null ? collectResult.optInt("totalCollectPoint") : 0;
                Log.other("绿色经营📊收集获得" + totalCollectPoint);
            } else {
                Log.i(TAG + ".batchSelfCollect", joSelfCollect.optString("resultDesc"));
            }
        } catch (Throwable th) {
            Log.err(TAG, "batchSelfCollect err:", th);
        }
    }

    /**
     * 签到
     *
     * @param sceneId sceneId
     */
    private void signIn(final String sceneId) {
        String signFlag = "greenFinance::signIn::" + sceneId;
        if (DailyTask.skipFlag(signFlag, "绿色经营签到[" + sceneId + "]")) {
            return;
        }
        try {
            String s = GreenFinanceRpcCall.signInQuery(sceneId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (!jo.optBoolean("success")) {
                Log.i(TAG + ".signIn.signInQuery", jo.optString("resultDesc"));
                return;
            }
            JSONObject result = jo.optJSONObject("result");
            if (result == null || !(result.opt("isTodaySignin") instanceof Boolean)) return;
            if (result.optBoolean("isTodaySignin")) {
                Status.flagToday(signFlag);
                return;
            }
            s = GreenFinanceRpcCall.signInTrigger(sceneId);
            TimeUtil.sleep(300);
            jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                Log.other("绿色经营📊签到成功");
                // 只认回读结果，不认「我发过 trigger」
                JSONObject verify = MyUtils.newJSONObject(GreenFinanceRpcCall.signInQuery(sceneId));
                JSONObject verifyResult = verify.optJSONObject("result");
                if (Boolean.TRUE.equals(verify.opt("success")) && verifyResult != null
                        && Boolean.TRUE.equals(verifyResult.opt("isTodaySignin"))) {
                    Status.flagToday(signFlag);
                }
            } else {
                Log.i(TAG + ".signIn.signInTrigger", jo.optString("resultDesc"));
            }
        } catch (Throwable th) {
            Log.err(TAG, "signIn err:", th);
        }
    }

    /**
     * 打卡
     */
    private void behaviorTick() {
        //绿色行动
        if (greenFinanceLsxd.getValue() && !DailyTask.skipFlag("greenFinance::tick::lsxd", "绿色经营打卡[lsxd]")) {
            TimeUtil.sleep(1000);
            doTick("lsxd");
            TimeUtil.sleep(1500);
        }
        //绿色采购
        if (greenFinanceLscg.getValue() && !DailyTask.skipFlag("greenFinance::tick::lscg", "绿色经营打卡[lscg]")) {
            TimeUtil.sleep(1000);
            doTick("lscg");
            TimeUtil.sleep(1500);
        }
        //绿色物流
        if (greenFinanceLswl.getValue() && !DailyTask.skipFlag("greenFinance::tick::lswl", "绿色经营打卡[lswl]")) {
            TimeUtil.sleep(1000);
            doTick("lswl");
            TimeUtil.sleep(1500);
        }
        //绿色办公
        if (greenFinanceLsbg.getValue() && !DailyTask.skipFlag("greenFinance::tick::lsbg", "绿色经营打卡[lsbg]")) {
            TimeUtil.sleep(1000);
            doTick("lsbg");
            TimeUtil.sleep(1500);
        }
        //绿色销售
        if (greenFinanceWdxd.getValue() && !DailyTask.skipFlag("greenFinance::tick::wdxd", "绿色经营打卡[wdxd]")) {
            TimeUtil.sleep(1000);
            doTick("wdxd");
            TimeUtil.sleep(1500);
        }
    }

    /**
     * 打卡绿色行为
     *
     * @param type 打开类型
     */
    private void doTick(final String type) {
        String tickFlag = "greenFinance::tick::" + type;
        if (DailyTask.skipFlag(tickFlag, "绿色经营打卡[" + type + "]")) {
            return;
        }
        try {
            String str = GreenFinanceRpcCall.queryUserTickItem(type);
            JSONObject jsonObject = MyUtils.newJSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".doTick.queryUserTickItem", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray jsonArray = jsonObject.optJSONArray("result");
            for (int i = 0; jsonArray != null && i < jsonArray.length(); i++) {
                jsonObject = jsonArray.optJSONObject(i);
                if (jsonObject == null || "Y".equals(jsonObject.optString("status"))) {
                    continue;
                }
                str = GreenFinanceRpcCall.submitTick(type, jsonObject.optString("behaviorCode"));
                TimeUtil.sleep(1500);
                JSONObject object = MyUtils.newJSONObject(str);
                if (!object.optBoolean("success")
                        || !String.valueOf(true).equals(JsonUtil.getValueByPath(object, "result.result"))) {
                    Log.other("绿色经营📊[" + jsonObject.optString("title") + "]打卡失败");
                    break;
                }
                Log.other("绿色经营📊[" + jsonObject.optString("title") + "]打卡成功");
//                Thread.sleep(executeIntervalInt);
            }
            // 回读确认：服务端返回的行为项全部为 Y（今日已打卡）才落当日标记
            JSONObject verified = MyUtils.newJSONObject(GreenFinanceRpcCall.queryUserTickItem(type));
            if (verified.optBoolean("success")) {
                JSONArray verifiedArray = verified.optJSONArray("result");
                if (verifiedArray != null && verifiedArray.length() > 0) {
                    boolean allTicked = true;
                    for (int i = 0; i < verifiedArray.length(); i++) {
                        JSONObject item = verifiedArray.optJSONObject(i);
                        if (item == null || !"Y".equals(item.optString("status"))) {
                            allTicked = false;
                            break;
                        }
                    }
                    if (allTicked) {
                        Status.flagToday(tickFlag);
                        Log.other("绿色经营📊[行为打卡:" + type + "]今日全部完成");
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "doTick err:", th);
        }
    }

    /**
     * 捐助
     */
    private void donation() {
        if (!greenFinanceDonation.getValue()) {
            return;
        }
        if (Status.hasFlagToday("greenFinance::donation")) {
            return;
        }
        try {
            String str = GreenFinanceRpcCall.queryExpireMcaPoint(1);
            TimeUtil.sleep(300);
            JSONObject jsonObject = MyUtils.newJSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".donation.queryExpireMcaPoint", jsonObject.optString("resultDesc"));
                return;
            }
            String strAmount = JsonUtil.getValueByPath(jsonObject, "result.expirePoint.amount");
            if (strAmount.isEmpty() || !strAmount.matches("-?\\d+(\\.\\d+)?")) {
                return;
            }
            double amount = Double.parseDouble(strAmount);
            if (amount <= 0) {
                // 服务端回读：已无过期金币可捐 → 落当日标记，后续运行不再查询
                Status.flagToday("greenFinance::donation");
                return;
            }
            //不管是否可以捐小于非100的倍数了，，第一次捐200，最后按amount-200*n
            Log.i("绿色经营📊1天内过期的金币[" + amount + "]");
            str = GreenFinanceRpcCall.queryAllDonationProjectNew();
            TimeUtil.sleep(300);
            jsonObject = MyUtils.newJSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".donation.queryAllDonationProjectNew", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray result = jsonObject.optJSONArray("result");
            TreeMap<String, String> dicId = new TreeMap<>();
            for (int i = 0; result != null && i < result.length(); i++) {
                JSONObject resultItem = result.optJSONObject(i);
                if (resultItem == null) {
                    continue;
                }
                jsonObject = (JSONObject) JsonUtil.getValueByPathObject(resultItem,
                        "mcaDonationProjectResult.[0]");
                if (jsonObject == null) {
                    continue;
                }
                String pId = jsonObject.optString("projectId");
                if (pId.isEmpty()) {
                    continue;
                }
                dicId.put(pId, jsonObject.optString("projectName"));
            }
            int[] r = calculateDeductions((int) amount, dicId.size());
            String am = "200";
            for (int i = 0; i < r[0]; i++) {
                String id = new ArrayList<>(dicId.keySet()).get(i);
                String name = dicId.get(id);
                if (i == r[0] - 1) {
                    am = String.valueOf(r[1]);
                }
                str = GreenFinanceRpcCall.donation(id, am);
                TimeUtil.sleep(1000);
                jsonObject = MyUtils.newJSONObject(str);
                if (!jsonObject.optBoolean("success")) {
                    Log.i(TAG + ".donation." + id, jsonObject.optString("resultDesc"));
                    return;
                }
                Log.other("绿色经营📊成功捐助[" + name + "]" + am + "金币");
            }
            // 回读确认：过期金币清零才认为「今日该捐的已捐完」
            JSONObject afterJo = MyUtils.newJSONObject(GreenFinanceRpcCall.queryExpireMcaPoint(1));
            if (afterJo.optBoolean("success")) {
                String afterAmount = JsonUtil.getValueByPath(afterJo, "result.expirePoint.amount");
                if (afterAmount.matches("-?\\d+(\\.\\d+)?")) {
                    if (Double.parseDouble(afterAmount) <= 0) {
                        Status.flagToday("greenFinance::donation");
                        Log.other("绿色经营📊今日捐助完成#过期金币已清零");
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "donation err:", th);
        }
    }

    /**
     * 评级奖品
     */
    private void prizes() {
        try {
            if (!Status.canGreenFinancePrizesMap()) {
                return;
            }
            String campId = "CP14664674";
            String str = GreenFinanceRpcCall.queryPrizes(campId);
            JSONObject jsonObject = MyUtils.newJSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".prizes.queryPrizes", jsonObject.optString("resultDesc"));
                return;
            }
            JSONArray prizes = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "result.prizes");
            if (prizes != null) {
                for (int i = 0; i < prizes.length(); i++) {
                    jsonObject = prizes.optJSONObject(i);
                    if (jsonObject == null) {
                        continue;
                    }
                    String bizTime = jsonObject.optString("bizTime");
                    // 使用 SimpleDateFormat 解析字符串
                    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH);
                    formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    Date dateTime = formatter.parse(bizTime);
                    if (TimeUtil.getWeekNumber(dateTime) == TimeUtil.getWeekNumber(new Date())) {
                        //本周已完成
                        Status.greenFinancePrizesMap();
                        return;
                    }
                }
            }
            str = GreenFinanceRpcCall.campTrigger(campId);
            jsonObject = MyUtils.newJSONObject(str);
            if (!jsonObject.optBoolean("success")) {
                Log.i(TAG + ".prizes.campTrigger", jsonObject.optString("resultDesc"));
                return;
            }
            JSONObject object = (JSONObject) JsonUtil.getValueByPathObject(jsonObject, "result.prizes.[0]");
            if (object == null) {
                return;
            }
            Log.other("绿色经营🍬评级奖品[" + object.optString("prizeName") + "]" + object.optString("price"));
        } catch (Throwable th) {
            Log.err(TAG, "prizes err:", th);
        }
    }

    /**
     * 收好友金币
     */
    private void batchStealFriend() {
        try {
            if (!Status.canGreenFinancePointFriend() || !greenFinancePointFriend.getValue()) {
                return;
            }
            int n = 0;
            while (true) {
                try {
                    String str = GreenFinanceRpcCall.queryRankingList(n);
                    TimeUtil.sleep(1500);
                    JSONObject jsonObject = MyUtils.newJSONObject(str);
                    if (!jsonObject.optBoolean("success")) {
                        Log.other("绿色经营🙋，好友金币巡查失败");
                        break;
                    }
                    JSONObject result = jsonObject.optJSONObject("result");
                    if (result == null) {
                        break;
                    }
                    JSONArray list = result.optJSONArray("rankingList");
                    for (int i = 0; list != null && i < list.length(); i++) {
                        JSONObject object = list.optJSONObject(i);
                        if (object == null || !object.optBoolean("collectFlag")) {
                            continue;
                        }
                        String friendId = object.optString("uid");
                        if (friendId.isEmpty()) {
                            continue;
                        }
                        str = GreenFinanceRpcCall.queryGuestIndexPoints(friendId);
                        TimeUtil.sleep(1000);
                        jsonObject = MyUtils.newJSONObject(str);
                        if (!jsonObject.optBoolean("success")) {
                            Log.i(TAG + ".batchStealFriend.queryGuestIndexPoints", jsonObject.optString("resultDesc"));
                            continue;
                        }
                        JSONArray points = (JSONArray) JsonUtil.getValueByPathObject(jsonObject, "result.pointDetailList");
                        if (points == null) {
                            continue;
                        }
                        JSONArray jsonArray = new JSONArray();
                        for (int j = 0; j < points.length(); j++) {
                            jsonObject = points.optJSONObject(j);
                            if (jsonObject != null && !jsonObject.optBoolean("collectFlag")) {
                                jsonArray.put(jsonObject.optString("bsnId"));
                            }
                        }
                        if (jsonArray.length() == 0) {
                            continue;
                        }
                        str = GreenFinanceRpcCall.batchSteal(jsonArray, friendId);
                        TimeUtil.sleep(1000);
                        jsonObject = MyUtils.newJSONObject(str);
                        if (!jsonObject.optBoolean("success")) {
                            Log.i(TAG + ".batchStealFriend.batchSteal", jsonObject.optString("resultDesc"));
                            continue;
                        }
                        Log.other("绿色经营🤩收[" + object.optString("nickName") + "]" +
                                JsonUtil.getValueByPath(jsonObject, "result.totalCollectPoint") + "金币");
                    }
                    if (result.optBoolean("lastPage")) {
                        Log.other("绿色经营🙋，好友金币巡查完成");
                        Status.greenFinancePointFriend();
                        return;
                    }
                    int next = result.optInt("nextStartIndex", -1);
                    if (next <= n) {
                        Log.record("绿色经营：分页游标缺失或未前进，停止好友金币巡查");
                        break;
                    }
                    n = next;
                } catch (Exception e) {
                    Log.printStackTrace(e);
                    break;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "batchStealFriend err:", th);
        }
    }

    /**
     * 计算次数和金额
     *
     * @param amount        最小金额
     * @param maxDeductions 最大次数
     * @return [次数，最后一次的金额]
     */
    private int[] calculateDeductions(int amount, int maxDeductions) {
        if (amount < 200) {
            // 小于 200 时特殊处理
            return new int[]{1, 200};
        }
        // 实际扣款次数，不能超过最大次数
        int actualDeductions = Math.min(maxDeductions, (int) Math.ceil((double) (amount) / 200));
        // 剩余金额
        int remainingAmount = amount - actualDeductions * 200;
        // 调整剩余金额为 100 的倍数，且不小于 200
        if (remainingAmount % 100 != 0) {
            // 向上取整到最近的 100 倍数
            remainingAmount = ((remainingAmount + 99) / 100) * 100;
        }
        if (remainingAmount < 200) {
            remainingAmount = 200;
        }
        // 如果调整后的剩余金额需要扣除更多次数，则调整实际扣款次数
        if (remainingAmount < amount - actualDeductions * 200) {
            actualDeductions = (amount - remainingAmount) / 200;
        }
        return new int[]{actualDeductions, remainingAmount};
    }
}
