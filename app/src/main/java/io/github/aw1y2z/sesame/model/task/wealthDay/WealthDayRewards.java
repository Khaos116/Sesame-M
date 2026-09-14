package io.github.aw1y2z.sesame.model.task.wealthDay;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 开门红理财节现金卡查询，可选领取。移植自 GR 分支，见 doc/MyFix.md。 */
public final class WealthDayRewards extends IsolatedRewardTask {
    private BooleanModelField collectCash;
    private IntegerModelField maxCashFen;

    @Override public String getName() { return "开门红理财节现金"; }

    @Override protected void addFields(ModelFields fields) {
        fields.addField(collectCash = new BooleanModelField("collectCash", "领取可提取现金", false));
        fields.addField(maxCashFen = new IntegerModelField("maxCashFen", "单次领取上限(分)", 500, 1, 100000));
    }

    /** 只用当下的现金卡；不会开通理财计划或提交任务完成请求。 */
    @Override protected void execute(Run run) throws Exception {
        JSONObject response = run.query(WealthDayCashProtocol.QUERY_METHOD, WealthDayCashProtocol.QUERY_ARGS);
        WealthDayCashProtocol.Claim claim = WealthDayCashProtocol.claim(response.toString(), maxCashFen.getValue());
        if (claim == null) {
            Log.record(getName() + "：当前没有符合领取上限的可提取现金");
            return;
        }
        if (!collectCash.getValue()) {
            Log.record(getName() + "：现金卡满足条件，领取开关未开启");
            return;
        }
        JSONObject collected = run.onceToday("drawCash", WealthDayCashProtocol.CLAIM_METHOD, claim.arguments(),
                () -> collectCash.getValue() && claim.amountFen <= maxCashFen.getValue());
        if (collected != null) Log.record(getName() + "：现金领取接口返回成功，请核对官方到账记录");
    }
}
