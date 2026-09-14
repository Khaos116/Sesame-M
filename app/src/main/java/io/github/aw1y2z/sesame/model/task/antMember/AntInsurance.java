package io.github.aw1y2z.sesame.model.task.antMember;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;

public class AntInsurance {
    private static final String TAG = AntInsurance.class.getSimpleName();

    public static void executeTask(Set<String> options) {
        if (options.contains("beanSignIn")) {
            beanSignIn();
        } if (options.contains("beanExchangeBubbleBoost")) {
            beanExchange("IT20230214000700069722");
        } if (options.contains("beanExchangeGoldenTicket")) {
            beanExchange("IT20240322000100086304");
        } if (options.contains("gainSumInsured")) {
            lotteryDraw(); gainSumInsured();
        }
    }

    // 保障金领取
    private static void gainSumInsured() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntInsuranceRpcCall.queryMultiSceneWaitToGainList());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.optJSONObject("data"); if (jo == null) {
                return;
            } Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                String key = keys.next(); Object jsonDTO = jo.opt(key);
                if (jsonDTO instanceof JSONArray) {
                    // 如eventToWaitDTOList、helpChildSumInsuredDTOList
                    JSONArray jsonArray = ((JSONArray) jsonDTO);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject item = jsonArray.optJSONObject(i);
                        if (item != null) {
                            gainMyAndFamilySumInsured(item);
                        }
                    }
                } else if (jsonDTO instanceof JSONObject) {
                    // 如signInDTO、priorityChannelDTO
                    JSONObject jsonObject = ((JSONObject) jsonDTO); if (jsonObject.length() == 0) {
                        continue;
                    } gainMyAndFamilySumInsured(jsonObject);
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "gainSumInsured err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void gainMyAndFamilySumInsured(JSONObject giftData) {
        if (giftData == null || giftData.optInt("sendType", 2) != 1) {
            return;
        } try {
            giftData.put("entrance", "jkj_zhima_dairy66");
            JSONObject jo = MyUtils.newJSONObject(AntInsuranceRpcCall.gainMyAndFamilySumInsured(giftData));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } JSONObject data = jo.optJSONObject("data"); jo = data != null ? data.optJSONObject("gainSumInsuredDTO") : null;
            if (jo == null) {
                return;
            }
            Log.other("蚂蚁保障🛡️领取保障金#获得[" + jo.optString("gainSumInsuredYuan") + "元保额]");
        } catch (Throwable t) {
            Log.i(TAG, "gainMyAndFamilySumInsured err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 天天领取保障福利
    private static void lotteryDraw() {
        if (Status.hasFlagToday("insurance::lotteryDraw")) {
            return;
        } try {
            JSONObject jo = MyUtils.newJSONObject(AntInsuranceRpcCall.queryAvailableNum());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } jo = jo.optJSONObject("result"); if (jo != null && jo.optInt("num") == 3) {
                jo = MyUtils.newJSONObject(AntInsuranceRpcCall.lotteryDraw());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return;
                } JSONArray ja = jo.optJSONArray("result"); for (int i = 0; ja != null && i < ja.length(); i++) {
                    jo = ja.optJSONObject(i); if (jo == null) {
                        continue;
                    } String prizeName = jo.optString("prizeName");
                    Log.other("蚂蚁保障🛡️天天领取保障福利#获得[" + prizeName + "]");
                }
            } Status.flagToday("insurance::lotteryDraw");
        } catch (Throwable t) {
            Log.i(TAG, "lotteryDraw err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 安心豆签到
    private static void beanSignIn() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntInsuranceRpcCall.beanQuerySignInProcess());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } JSONObject signResult = jo.optJSONObject("result"); if (signResult != null && signResult.optBoolean("canPush")) {
                jo = MyUtils.newJSONObject(AntInsuranceRpcCall.beanSignInTrigger());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONObject triggerResult = jo.optJSONObject("result");
                    JSONArray prizeList = triggerResult != null ? triggerResult.optJSONArray("prizeSendOrderDTOList") : null;
                    JSONObject firstPrize = prizeList != null ? prizeList.optJSONObject(0) : null;
                    String prizeName = firstPrize != null ? firstPrize.optString("prizeName") : "";
                    Log.other("蚂蚁保障🛡️安心豆签到#获得[" + prizeName + "]");
                }
            }
        } catch (Throwable t) {
            Log.i(TAG, "beanSignIn err:"); Log.printStackTrace(TAG, t);
        }
    }

    // 安心豆兑换
    private static void beanExchange(String itemId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntInsuranceRpcCall.queryUserAccountInfo("INS_BLUE_BEAN"));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            } JSONObject accountResult = jo.optJSONObject("result");
            int userCurrentPoint = accountResult != null ? accountResult.optInt("userCurrentPoint") : 0;
            jo = MyUtils.newJSONObject(AntInsuranceRpcCall.beanExchangeDetail(itemId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject exchangeResult = jo.optJSONObject("result");
            JSONObject rspContext = exchangeResult != null ? exchangeResult.optJSONObject("rspContext") : null;
            JSONObject params = rspContext != null ? rspContext.optJSONObject("params") : null;
            jo = params != null ? params.optJSONObject("exchangeDetail") : null;
            if (jo == null) {
                return;
            }
            String itemName = jo.optString("itemName");
            jo = jo.optJSONObject("itemExchangeConsultDTO");
            if (jo == null) {
                return;
            }
            int realConsumePointAmount = jo.optInt("realConsumePointAmount");
            if (!jo.optBoolean("canExchange") || realConsumePointAmount > userCurrentPoint) {
                return;
            } jo = MyUtils.newJSONObject(AntInsuranceRpcCall.beanExchange(itemId, realConsumePointAmount));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("蚂蚁保障🛡️安心豆兑换[" + itemName + "]#消耗[" + realConsumePointAmount + "安心豆]");
            }
        } catch (Throwable t) {
            Log.i(TAG, "beanExchange err:"); Log.printStackTrace(TAG, t);
        }
    }
}
