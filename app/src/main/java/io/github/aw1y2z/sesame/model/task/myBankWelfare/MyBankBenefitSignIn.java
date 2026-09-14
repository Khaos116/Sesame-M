package io.github.aw1y2z.sesame.model.task.myBankWelfare;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 独立的福利金签到，跟网商会员权益签到是两回事。移植自 GR 分支，见 doc/MyFix.md。 */
public final class MyBankBenefitSignIn extends IsolatedRewardTask {
    private BooleanModelField signIn;

    @Override public String getName() { return "网商福利金签到"; }

    @Override protected void addFields(ModelFields fields) {
        fields.addField(signIn = new BooleanModelField("signIn", "领取福利金签到奖励", false));
    }

    @Override protected void execute(Run run) throws Exception {
        JSONObject response = run.query(MyBankBenefitProtocol.QUERY_METHOD, MyBankBenefitProtocol.queryArguments());
        if (!MyBankBenefitProtocol.offersSignIn(response.toString())) {
            Log.record(getName() + "：当前未返回签到入口");
            return;
        }
        if (!signIn.getValue()) {
            Log.record(getName() + "：已返回签到入口，动作开关未开启");
            return;
        }
        JSONObject signed = run.onceToday("welfareSignIn", MyBankBenefitProtocol.SIGN_METHOD,
                MyBankBenefitProtocol.SIGN_ARGS, () -> signIn.getValue());
        if (signed != null) Log.record(getName() + "：签到接口返回成功，请核对官方福利记录");
    }
}
