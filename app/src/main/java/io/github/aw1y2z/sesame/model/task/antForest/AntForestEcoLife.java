package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestEcoLife {

    private static final String TAG = AntForestEcoLife.class.getSimpleName();
    private final AntForestV2 host;

    AntForestEcoLife(AntForestV2 host) {
        this.host = host;
    }

    /**
     * 绿色行动
     */
    void ecoLife() {
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
            if (host.ecoLifeOptions.getValue().contains("dish")) {
                photoGuangPan(dayPoint);
            }
            if (host.ecoLifeOptions.getValue().contains("tick")) {
                ecoLifeTick(actionListVO, dayPoint);
            }
        } catch (Throwable th) {
            Log.err(TAG, "ecoLife err:", th);
        }
    }

    /* 绿色行动打卡 */

    void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
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
    void photoGuangPan(String dayPoint) {
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
}
