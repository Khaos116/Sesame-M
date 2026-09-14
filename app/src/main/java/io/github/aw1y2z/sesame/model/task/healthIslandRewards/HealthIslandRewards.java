package io.github.aw1y2z.sesame.model.task.healthIslandRewards;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 只用已获得的即将到期碎片兑换用户指定的礼品。移植自 GR 分支，见 doc/MyFix.md。 */
public final class HealthIslandRewards extends IsolatedRewardTask {
    private static final String PREFIX = "com.alipay.neverland.biz.rpc.";
    private BooleanModelField exchange;
    private StringModelField prizeId;
    private IntegerModelField maxPieces;
    private BooleanModelField inspectStages;
    private BooleanModelField claimStages;

    @Override public String getName() { return "健康岛红包碎片兑换"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(exchange = new BooleanModelField("exchange", "兑换即将到期的碎片", false));
        fields.addField(prizeId = new StringModelField("prizeId", "指定兑换礼品ID", ""));
        fields.addField(maxPieces = new IntegerModelField("maxPieces", "单次最多消耗碎片", 500, 1, 100000));
        fields.addField(inspectStages = new BooleanModelField("inspectStages", "查询地图阶段奖励", false));
        fields.addField(claimStages = new BooleanModelField("claimStages", "领取已达成阶段奖励", false));
    }

    @Override protected void execute(Run run) throws Exception {
        if (inspectStages.getValue() || claimStages.getValue()) collectStageRewards(run);
        JSONObject response = run.query(PREFIX + "queryExchangeModule",
                "[{\"assetType\":\"RED_PACKAGE_PIECE\",\"source\":\"jkddicon\"}]");
        JSONObject data = response.optJSONObject("data");
        JSONObject medium = data == null ? null : data.optJSONObject("mediumModule");
        JSONObject catalog = data == null ? null : data.optJSONObject("exchangePrizeModule");
        if (medium == null || catalog == null) { Log.record(getName() + "：未返回完整兑换信息"); return; }
        String expiring = medium.optString("expiringAmount", "");
        if (!expiring.matches("[0-9]{1,9}")) { Log.record(getName() + "：到期碎片数量格式不明确"); return; }
        long available = Long.parseLong(expiring);
        JSONArray offers = catalog.optJSONArray("exchangePrizes");
        if (offers == null || offers.length() > 100) { Log.record(getName() + "：兑换目录格式不明确"); return; }
        if (available == 0) { Log.record(getName() + "：没有即将到期的红包碎片"); return; }
        if (!exchange.getValue() || prizeId.getValue().isEmpty()) {
            Log.record(getName() + "：有到期碎片，兑换未开启或尚未选择礼品；目录条目=" + offers.length()); return;
        }
        String selectedId = prizeId.getValue();
        if (selectedId.length() > 128) return;
        JSONObject selected = null;
        for (int i = 0; i < offers.length(); i++) {
            JSONObject offer = offers.optJSONObject(i);
            if (offer != null && selectedId.equals(offer.optString("prizeId"))) {
                if (selected != null) { Log.record(getName() + "：礼品ID重复，停止本轮"); return; }
                selected = offer;
            }
        }
        if (selected == null) { Log.record(getName() + "：指定礼品不在当前目录"); return; }
        String status = selected.optString("statusCode", "");
        String costText = selected.optString("consumeMediumAmount", "");
        if (status.isEmpty() || "POINT_NOT_ENOUGH".equals(status) || !costText.matches("[0-9]{1,9}")) {
            Log.record(getName() + "：礼品数量或状态不满足兑换条件"); return;
        }
        long cost = Long.parseLong(costText);
        if (cost <= 0 || cost > available || cost > maxPieces.getValue()) {
            Log.record(getName() + "：兑换消耗超出到期碎片或配置上限"); return;
        }
        String camp = catalog.optString("campId", "");
        if (camp.isEmpty() || camp.length() > 128) return;
        if (!exchange.getValue() || !selectedId.equals(prizeId.getValue())) return;
        JSONObject result = run.onceToday("exchangePieces", PREFIX + "doMediumExchangePrize",
                new JSONArray().put(new JSONObject().put("assetType", "RED_PACKAGE_PIECE")
                        .put("prizeId", selectedId).put("campId", camp).put("source", "jkddicon")).toString(),
                () -> exchange.getValue() && selectedId.equals(prizeId.getValue()) && cost <= maxPieces.getValue());
        if (result != null) Log.record(getName() + "：兑换接口返回成功，请核对官方奖励记录");
    }

    /** 读取地图阶段可领奖数量，每轮最多领两个已达成阶段。 */
    private void collectStageRewards(Run run) throws Exception {
        JSONObject base = run.query(HealthIslandStageProtocol.BASE_METHOD, HealthIslandStageProtocol.baseArgs());
        String map = HealthIslandStageProtocol.mapId(base.toString());
        JSONObject queried = run.query(HealthIslandStageProtocol.QUERY_METHOD, HealthIslandStageProtocol.queryArgs(map));
        List<Integer> levels = HealthIslandStageProtocol.claimableLevels(queried.toString());
        Log.record(getName() + "：当前地图可领取阶段数=" + levels.size());
        if (!claimStages.getValue()) return;
        int attempts = 0;
        for (int level : levels) {
            if (attempts >= 2) break;
            JSONObject claimed = run.onceToday("mapStage:" + map.length() + ":" + map + ":" + level,
                    HealthIslandStageProtocol.CLAIM_METHOD, HealthIslandStageProtocol.claimArgs(map, level),
                    () -> claimStages.getValue());
            if (claimed == null) continue;
            attempts++;
            Log.record(getName() + "：阶段" + level + "领奖接口返回成功，请核对官方奖励记录");
        }
    }
}
