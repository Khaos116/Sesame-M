package io.github.aw1y2z.sesame.model.task.dayDaySave;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 理财稳当当签到：仅签到，不涉及储蓄/投资/自动扣款/任务完成请求。
 * 移植自 GR 分支，见 docs/MyFix.md。
 */
public final class DayDaySave extends IsolatedRewardTask {
    private BooleanModelField checkIn;
    @Override public String getName() { return "理财稳当当签到"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(checkIn = new BooleanModelField("checkIn", "每日签到", false));
    }
    @Override protected void execute(Run run) throws Exception {
        JSONObject response = run.query("com.alipay.ficcscenepromobff.needle.daydaysave.index",
                "[{\"bizScenario\":\"huangjinpiao\"}]");
        JSONObject result = response.optJSONObject("result");
        Object signedState = result == null ? null : result.opt("hasSignIn");
        if (!(signedState instanceof Boolean) && !"true".equals(signedState) && !"false".equals(signedState)) {
            run.stop("缺少明确签到状态（result对象=" + (result != null) + "，hasSignIn字段="
                    + (signedState != null && signedState != JSONObject.NULL) + "），根结构=" + responseShape(response)
                    + "，result结构=" + responseShape(result) + "，degradePage="
                    + (result != null && result.opt("degradePage") instanceof Boolean ? result.optBoolean("degradePage") : "缺失或类型异常")); return;
        }
        if (result.optBoolean("hasSignIn")) { Log.record(getName() + "：已经签到"); run.completed(); return; }
        if (!checkIn.getValue()) { Log.record(getName() + "：未签到，签到开关未开启"); return; }
        JSONObject signed = run.onceToday("signIn",
                "com.alipay.ficcscenepromobff.needle.daydaysave.signIn", "[null]", () -> checkIn.getValue());
        if (signed != null) { Log.record(getName() + "：签到接口返回成功"); run.completed(); }
    }
}
