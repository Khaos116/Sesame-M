package io.github.aw1y2z.sesame.model.task.promoprodRewards;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 只读任务状态；领取动作等确认过接口契约再做。移植自 GR 分支，见 doc/MyFix.md。 */
public final class PromoprodRewards extends IsolatedRewardTask {
    private BooleanModelField inspect;
    private BooleanModelField claimCompleted;
    @Override public String getName() { return "实体红包任务"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(inspect = new BooleanModelField("inspect", "查询任务状态", false));
        fields.addField(claimCompleted = new BooleanModelField("claimCompleted", "领取服务端已完成奖励", false));
    }
    @Override protected void execute(Run run) throws Exception {
        if (!inspect.getValue() && !claimCompleted.getValue()) {
            Log.record(getName() + "：查询和领取开关均未开启"); return;
        }
        JSONObject root = run.query(PromoprodQueryProtocol.METHOD, PromoprodQueryProtocol.ARGS);
        PromoprodQueryProtocol.Summary summary = PromoprodQueryProtocol.summarize(root.toString());
        Log.record(getName() + "：任务总数=" + summary.total + "，TRANSFORMER类型=" + summary.transformer
                + "，报名完成=" + summary.signupComplete
                + "，领取成功状态=" + summary.received + "，其他任务=" + summary.other
                + "，未知条目或状态=" + summary.unknown);
        if (claimCompleted.getValue()) {
            Log.record(getName() + "：领取参数和动作条件尚未确认，本轮仅查询");
        }
    }
}
