package io.github.aw1y2z.sesame.model.task.weeklyWelfare;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 黄金票每周福利：只消费服务端当天返回的档位。移植自 GR 分支，见 docs/MyFix.md。 */
public final class WeeklyWelfare extends IsolatedRewardTask {
    private static final String PREFIX = "com.alipay.finaggexpbff.needle.weeklyWelfare.";
    private static final String INDEX = "[{\"chInfo\":\"goldbill\",\"modeBitMask\":513}]";
    private BooleanModelField signIn;
    private BooleanModelField weeklyPrize;

    @Override public String getName() { return "黄金票每周福利"; }
    @Override public ModelGroup getGroup() { return ModelGroup.MEMBER; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(signIn = new BooleanModelField("signIn", "每日签到", false));
        fields.addField(weeklyPrize = new BooleanModelField("weeklyPrize", "第七日签到后领取周奖励", false));
    }

    private WeeklyWelfareFlow.Offer today(JSONObject response) {
        JSONObject result = response.optJSONObject("result");
        JSONObject upsert = result == null ? null : result.optJSONObject("upsertData");
        JSONObject sign = upsert == null ? null : upsert.optJSONObject("sign");
        JSONArray timeline = sign == null ? null : sign.optJSONArray("timeline");
        if (timeline == null || timeline.length() > 31) return null;
        JSONObject selected = null;
        for (int i = 0; i < timeline.length(); i++) {
            JSONObject entry = timeline.optJSONObject(i);
            if (entry != null && Boolean.TRUE.equals(entry.opt("isToday"))) {
                if (selected != null || !(entry.opt("signed") instanceof Boolean)) return null;
                selected = entry;
            }
        }
        if (selected == null) return null;
        Object day = selected.opt("day");
        if (!(day instanceof Number)) return null;
        double number = ((Number) day).doubleValue();
        if (number < 1 || number > 7 || number != Math.rint(number)) return null;
        return new WeeklyWelfareFlow.Offer((int) number, Boolean.TRUE.equals(selected.opt("signed")),
                selected.optString("basePrizeNum", ""), selected.optString("prizeNum", ""));
    }

    @Override protected void execute(Run run) throws Exception {
        WeeklyWelfareFlow.Result result = WeeklyWelfareFlow.run(new WeeklyWelfareFlow.Port() {
            @Override public WeeklyWelfareFlow.Offer query() throws Exception {
                return today(run.query(PREFIX + "index", INDEX));
            }

            @Override public boolean sign(WeeklyWelfareFlow.Offer offer) throws Exception {
                JSONObject signed = run.onceToday("sign", PREFIX + "trigger", new JSONArray()
                        .put(new JSONObject().put("basePrize", new BigDecimal(offer.basePrize))
                                .put("prizeNum", offer.prize).put("type", "SIGN")).toString(),
                        () -> signIn.getValue());
                if (signed != null) Log.record(getName() + "：签到接口返回成功");
                return signed != null;
            }

            @Override public boolean collect() throws Exception {
                return run.onceToday("weeklyPrize", PREFIX + "trigger", "[{\"type\":\"SIGN_PRIZE\"}]",
                        () -> weeklyPrize.getValue()) != null;
            }
        }, () -> signIn.getValue(), () -> weeklyPrize.getValue());
        Log.record(getName() + "：本轮状态=" + result.name());
    }
}
