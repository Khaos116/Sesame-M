package io.github.aw1y2z.sesame.model.task.dailyCash;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.TimeZone;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** SIGN_TASK_CENTER 签到与可选的现有余额领取。移植自 GR 分支，见 doc/MyFix.md。 */
public final class DailyCash extends IsolatedRewardTask {
    private static final String PREFIX = "alipay.membertangram.biz.rpc.newtaskcenter.";
    private static final String QUERY = "[{\"activityId\":\"SIGN_TASK_CENTER\",\"source\":\"sousuo\"}]";
    private BooleanModelField checkIn;
    private BooleanModelField receiveCash;

    @Override public String getName() { return "天天赚现金"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(checkIn = new BooleanModelField("checkIn", "每日签到", false));
        fields.addField(receiveCash = new BooleanModelField("receiveCash", "累计满5元领取(每日最多一次)", false));
    }

    private JSONObject args() throws Exception {
        return new JSONObject().put("activityId", "SIGN_TASK_CENTER").put("source", "sousuo");
    }

    @Override protected void execute(Run run) throws Exception {
        JSONObject response = run.query(PREFIX + "signStatusQuery", QUERY);
        JSONObject info = response.optJSONObject("signInfo");
        JSONArray days = info == null ? null : info.optJSONArray("signDayInfos");
        if (days == null || days.length() > 62) {
            Log.record(getName() + "：缺少有效签到日历，停止本轮"); return;
        }
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        JSONObject today = null;
        for (int i = 0; i < days.length(); i++) {
            JSONObject candidate = days.optJSONObject(i);
            if (candidate != null && candidate.optLong("signDate", -1) == day.getTimeInMillis()) {
                if (today != null) { Log.record(getName() + "：签到日历重复，停止本轮"); return; }
                today = candidate;
            }
        }
        if (today == null || !(today.opt("todaySignFlag") instanceof Boolean)) {
            Log.record(getName() + "：今日签到状态不明确，停止本轮"); return;
        }
        boolean signed = today.optBoolean("todaySignFlag");
        if (!signed && checkIn.getValue()) {
            String scene = today.optString("sceneCode");
            if (scene.isEmpty() || scene.length() > 128) {
                Log.record(getName() + "：缺少签到场景，停止本轮"); return;
            }
            JSONObject signResult = run.onceToday("doSign", PREFIX + "doSign",
                    new JSONArray().put(args().put("signSceneCode", scene)).toString(), () -> checkIn.getValue());
            if (signResult != null) Log.record(getName() + "：签到接口返回成功");
        } else {
            Log.record(getName() + (signed ? "：今日已签到" : "：签到开关未开启"));
        }

        JSONObject benefits = run.query(PREFIX + "benefitInfosQuery", QUERY);
        String amount = benefits.optString("signTotalAmount", "");
        if (!amount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) {
            Log.record(getName() + "：金额格式不明确，停止本轮"); return;
        }
        BigDecimal balance = new BigDecimal(amount);
        if (balance.compareTo(new BigDecimal("5")) < 0) {
            Log.record(getName() + "：尚未达到领取门槛"); return;
        }
        if (!receiveCash.getValue()) { Log.record(getName() + "：达到门槛，领取开关未开启"); return; }
        JSONObject collected = run.onceToday("receiveCash", PREFIX + "receiveCash", new JSONArray()
                .put(args().put("cashAmount", "5.0").put("totalAmount", balance.toPlainString())).toString(),
                () -> receiveCash.getValue());
        if (collected != null) Log.record(getName() + "：领取接口返回成功，请在官方页面核对到账");
    }
}
