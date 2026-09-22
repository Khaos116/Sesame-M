package io.github.aw1y2z.sesame.model.task.forestPlantRewards;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 森林绿植活动只读探测。移植自 GR 分支，见 docs/MyFix.md。 */
public final class ForestPlantRewards extends IsolatedRewardTask {
    private BooleanModelField inspect;
    @Override public String getName() { return "森林绿植活动"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(inspect = new BooleanModelField("inspect", "查询绿植活动", false));
    }
    @Override protected void execute(Run run) throws Exception {
        if (!inspect.getValue()) return;
        JSONObject result = run.query("com.alipay.ugshopping.biz.service.rpc.plant.PlantFacade.queryPlantFeedsItemList",
                ForestPlantProtocol.queryArgs(1));
        JSONArray rows = result.optJSONArray("itemInfoVOList");
        if (rows == null) rows = result.optJSONArray("plantFeeds");
        Log.record(getName() + "：可用条目=" + (rows == null ? 0 : Math.min(rows.length(), 50)) + "，本轮只读");
    }
}
