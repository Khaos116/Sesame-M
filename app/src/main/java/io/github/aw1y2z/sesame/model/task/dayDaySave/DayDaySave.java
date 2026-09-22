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
        if (result == null || !(result.opt("hasSignIn") instanceof Boolean)) {
            Log.record(getName() + "：缺少明确签到状态，停止本轮"); return;
        }
        if (result.optBoolean("hasSignIn")) { Log.record(getName() + "：已经签到"); return; }
        if (!checkIn.getValue()) { Log.record(getName() + "：未签到，签到开关未开启"); return; }
        JSONObject signed = run.onceToday("signIn",
                "com.alipay.ficcscenepromobff.needle.daydaysave.signIn", "[null]", () -> checkIn.getValue());
        if (signed != null) Log.record(getName() + "：签到接口返回成功");
    }
}
