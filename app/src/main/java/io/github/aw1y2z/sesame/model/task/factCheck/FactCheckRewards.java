package io.github.aw1y2z.sesame.model.task.factCheck;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.model.task.rewardSupport.IsolatedRewardTask;
import io.github.aw1y2z.sesame.util.Log;

/** 支真假题目查询：只读探测。移植自 GR 分支，见 doc/MyFix.md。 */
public final class FactCheckRewards extends IsolatedRewardTask {
    private BooleanModelField inspect;
    @Override public String getName() { return "支真假题目查询"; }
    @Override protected void addFields(ModelFields fields) {
        fields.addField(inspect = new BooleanModelField("inspect", "查询答题任务", false));
    }
    @Override protected void execute(Run run) throws Exception {
        if (!inspect.getValue()) return;
        JSONObject result = run.query(FactCheckProtocol.INDEX_METHOD, FactCheckProtocol.args());
        JSONObject data = result.optJSONObject("data");
        if (data == null) { Log.record(getName() + "：未返回题目数据"); return; }
        String contentId = data.optString("contentId", "");
        String title = data.optString("title", "");
        int answerCount = Math.max(0, Math.min(100, data.optInt("answerCount", 0)));
        boolean energy = data.optBoolean("energy", false);
        Log.record(getName() + "：待答题数=" + answerCount + "，energy=" + energy
                + "，contentId有效=" + FactCheckProtocol.validIdentifier(contentId)
                + (title.isEmpty() ? "" : "，题目已返回"));
        // 会话获取仅用于诊断字段映射，本轮不提交答案。
        if (answerCount > 0 && energy) {
            JSONObject session = run.query(FactCheckProtocol.SESSION_METHOD, FactCheckProtocol.args());
            JSONObject sessionData = session.optJSONObject("data");
            JSONObject message = sessionData == null ? null : sessionData.optJSONObject("messageInfo");
            String chatId = message == null ? "" : message.optString("chatId", "");
            Log.record(getName() + "：会话字段已返回，chatId有效=" + FactCheckProtocol.validIdentifier(chatId)
                    + "；本轮不提交答案");
        }
    }
}
