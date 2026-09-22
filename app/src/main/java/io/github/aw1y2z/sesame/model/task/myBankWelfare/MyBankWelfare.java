package io.github.aw1y2z.sesame.model.task.myBankWelfare;

import org.json.JSONObject;
import java.util.List;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 网商银行福利金查询与会员签到，各动作独立开关。移植自 GR 分支，见 docs/MyFix.md。 */
public final class MyBankWelfare extends IsolatedRewardTask {
    private BooleanModelField signIn;
    private BooleanModelField inspectPoints;
    private BooleanModelField inspectCoupons;
    private BooleanModelField useCoupon;
    private BooleanModelField inspectBenefits;
    private BooleanModelField useBenefits;
    private IntegerModelField maxBenefits;
    @Override public String getName() { return "网商银行福利签到"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(signIn = new BooleanModelField("signIn", "会员签到", false));
        fields.addField(inspectPoints = new BooleanModelField("inspectPoints", "查询福利金及到期情况", false));
        fields.addField(inspectCoupons = new BooleanModelField("inspectCoupons", "查询福利券", false));
        fields.addField(useCoupon = new BooleanModelField("useCoupon", "使用已有福利券(每日最多一次)", false));
        fields.addField(inspectBenefits = new BooleanModelField("inspectBenefits", "查询可用福利金权益", false));
        fields.addField(useBenefits = new BooleanModelField("useBenefits", "使用已有福利金权益", false));
        fields.addField(maxBenefits = new IntegerModelField("maxBenefits", "单次使用权益数量上限", 10, 1, 20));
    }
    @Override protected void execute(Run run) throws Exception {
        if (inspectPoints.getValue()) {
            String cutoff = MyBankPointsProtocol.nextMonthFirstDay(System.currentTimeMillis());
            JSONObject queried = run.query(MyBankPointsProtocol.METHOD, MyBankPointsProtocol.arguments(cutoff));
            MyBankPointsProtocol.Balance balance = MyBankPointsProtocol.parse(queried.toString(), cutoff);
            Log.record(getName() + "：福利金余额状态=" + presence(balance.total)
                    + "，本年到期状态=" + presence(balance.yearExpiring)
                    + "，截至" + cutoff + "到期状态=" + presence(balance.dateExpiring));
        }
        if (inspectCoupons.getValue() || useCoupon.getValue()) {
            JSONObject queried = run.query(MyBankCouponProtocol.QUERY_METHOD, MyBankCouponProtocol.QUERY_ARGS);
            int available = MyBankCouponProtocol.available(queried.toString());
            Log.record(getName() + "：可用福利券=" + available);
            if (available > 0 && useCoupon.getValue()) {
                JSONObject used = run.onceToday("useCoupon", MyBankCouponProtocol.USE_METHOD,
                        MyBankCouponProtocol.USE_ARGS, () -> useCoupon.getValue());
                if (used != null) Log.record(getName() + "：福利券使用接口返回成功，请核对官方记录");
            }
        }
        if (inspectBenefits.getValue() || useBenefits.getValue()) {
            JSONObject queried = run.query(MyBankBenefitProtocol.QUERY_METHOD, MyBankBenefitProtocol.queryArguments());
            List<String> ids = MyBankBenefitProtocol.usableIds(queried.toString(), maxBenefits.getValue());
            Log.record(getName() + "：本轮可处理福利金权益=" + ids.size());
            if (useBenefits.getValue() && !ids.isEmpty()) {
                JSONObject used = run.onceToday("useBenefits", MyBankBenefitProtocol.USE_METHOD,
                        MyBankBenefitProtocol.useArguments(ids),
                        () -> useBenefits.getValue() && ids.size() <= maxBenefits.getValue());
                if (used != null) Log.record(getName() + "：权益使用接口返回成功，请核对官方福利记录");
            }
        }
        if (!signIn.getValue()) { Log.record(getName() + "：签到开关未开启"); return; }
        JSONObject result = run.onceToday("signinPlay", "com.alipay.loanpromoweb.member.play.signinPlay",
                "[{\"channel\":\"miniApp\",\"needMultiple\":false,\"operation\":\"signApply\",\"playId\":\"PLAY100177545\"}]",
                () -> signIn.getValue());
        if (result != null) Log.record(getName() + "：签到接口返回成功");
    }

    private static String presence(Long amount) {
        return amount == null ? "未知" : amount == 0L ? "无" : "有";
    }
}
