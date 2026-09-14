package io.github.aw1y2z.sesame.model.task.youthPrivilege;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.model.task.rewardSupport.RewardRunPolicy;
import io.github.aw1y2z.sesame.util.Log;

/** 青春特权签到，森林道具类任务保留原有设置不动。移植自 GR 分支，见 doc/MyFix.md。 */
public final class YouthPrivilege extends IsolatedRewardTask {
    private BooleanModelField checkIn;
    private BooleanModelField inspectCashOffers;
    @Override public String getName() { return "青春特权签到"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(checkIn = new BooleanModelField("checkIn", "领取签到奖励", false));
        fields.addField(inspectCashOffers = new BooleanModelField("inspectCashOffers", "查询现金活动状态", false));
    }

    @Override protected void execute(Run run) throws Exception {
        inspectCheckIn(run);
        // 没签到状态不该拦住独立开关的库存查询。
        if (inspectCashOffers.getValue()) {
            JSONObject response = run.query(YouthCashOfferProtocol.METHOD, YouthCashOfferProtocol.ARGS);
            YouthCashOfferProtocol.Inventory inventory = YouthCashOfferProtocol.inspect(response.toString());
            Log.record(getName() + "：现金活动条目=" + inventory.entries + "，去重权益=" + inventory.uniqueOffers
                    + "，重复=" + inventory.duplicates + "，未知=" + inventory.unknown + "，兑换资格尚未确认");
        }
    }

    private void inspectCheckIn(Run run) throws Exception {
        JSONObject response = run.query("alipay.membertangram.biz.rpc.student.queryCheckInModel",
                "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]");
        JSONObject info = response.optJSONObject("studentCheckInInfo");
        if (info == null) { Log.record(getName() + "：未返回签到状态，本轮结束"); return; }
        if (!RewardRunPolicy.mayCheckIn(info.optString("action"))) {
            Log.record(getName() + "：当前没有可执行的签到动作");
            return;
        }
        if (!checkIn.getValue()) { Log.record(getName() + "：可签到，领取开关未开启"); return; }
        JSONObject result = run.onceToday("checkIn", "alipay.membertangram.biz.rpc.student.checkIn",
                "[{\"source\":\"ch_appcenter__chsub_9patch\"}]", () -> checkIn.getValue());
        if (result != null) Log.record(getName() + "：签到接口返回成功");
    }
}
