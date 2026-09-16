package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 其他任务（好家无忧卡）。移植自 GR 分支，见 doc/MyFix.md。
 * <p>
 * 原版没有请求预算/风控冷却保护，这次移植按用户要求补上：每轮请求数上限、请求间隔、
 * 命中风控自动暂停 24 小时（见 {@link OtherRequestGate}），不改动原有的业务判断逻辑。
 */
public class OtherTask extends ModelTask {
    private static final String TAG = "OtherTask";

    private BooleanModelField haojiaWuyou;

    private OtherRequestGate gate;

    @Override
    public String getName() {
        return "其他任务";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(haojiaWuyou = new BooleanModelField("haojiaWuyou", "好家无忧卡", false));
        return fields;
    }

    @Override
    public Boolean check() {
        return isEnable() && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME && !OtherRequestGate.isCoolingDown();
    }

    @Override
    public void run() {
        if (!check()) return;
        gate = new OtherRequestGate();
        try {
            if (haojiaWuyou.getValue()) {
                runHaoJia();
            }
        } catch (Throwable t) {
            Log.i(TAG, "其他任务执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private void runHaoJia() {
        Log.record("好家无忧卡开始执行");
        try {
            JSONObject sign = new JSONObject(gate.call("查询好家无忧卡签到", HaoJiaRpcCall::querySignIn));
            if (ok(sign)) {
                JSONObject component = component(sign, "independent_component_sign_in_00966139_independent_component_sign_in_recall");
                JSONObject content = component == null ? null : component.optJSONObject("content");
                JSONArray orders = content == null ? null : content.optJSONArray("playSignInOrderInfoList");
                if (orders != null && orders.length() > 0) {
                    JSONObject order = orders.optJSONObject(0);
                    JSONObject template = order == null ? null : order.optJSONObject("playSignInTemplateInfo");
                    String code = template == null ? "" : template.optString("code");
                    JSONArray records = order == null ? null : order.optJSONArray("signInRecordInfoList");
                    if (!code.isEmpty() && !hasSignedToday(records)) {
                        JSONObject result = new JSONObject(gate.call("好家无忧卡签到", () -> HaoJiaRpcCall.doSignIn(code)));
                        if (ok(result)) Log.record("好家无忧卡签到完成");
                    }
                }
            }
            JSONObject tasks = new JSONObject(gate.call("查询好家无忧卡任务", HaoJiaRpcCall::queryTaskList));
            JSONObject component = component(tasks, "independent_component_task_reward_00793835_independent_component_task_reward_query");
            JSONObject content = component == null ? null : component.optJSONObject("content");
            JSONArray list = content == null ? null : content.optJSONArray("playTaskOrderInfoList");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null || !"init".equals(task.optString("taskStatus")) || "eventPush".equals(task.optString("advanceType"))) continue;
                JSONObject display = task.optJSONObject("displayInfo");
                String name = display == null ? "" : display.optString("activityName");
                if (containsRisk(name)) continue;
                String code = task.optString("code");
                int browseTime = display == null ? 0 : display.optInt("browseTime", 0);
                if (browseTime > 0) sleep(browseTime * 1000L);
                if (!code.isEmpty() && ok(new JSONObject(gate.call("好家无忧卡完成任务", () -> HaoJiaRpcCall.applyTask(code))))) Log.record("好家无忧卡完成任务 " + name);
            }
        } catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) {
            // gate 已经记录过原因，这里不重复打日志。
        } catch (Throwable t) {
            Log.i(TAG, "好家无忧卡执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private static JSONObject component(JSONObject root, String key) {
        JSONObject components = root == null ? null : root.optJSONObject("components");
        return components == null ? null : components.optJSONObject(key);
    }

    private static boolean containsRisk(String name) {
        return name.contains("流量") || name.contains("话费") || name.contains("理财") || name.contains("保险") || name.contains("购车") || name.contains("开通") || name.contains("办理") || name.contains("咨询") || name.contains("黄金");
    }

    private static boolean hasSignedToday(JSONArray records) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        String today = format.format(new Date());
        for (int i = 0; records != null && i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String date = record.optString("date").replaceAll("[^0-9]", "");
            if (today.equals(date)) return true;
        }
        return false;
    }

    private static boolean ok(JSONObject jo) {
        return jo != null && (jo.optBoolean("success") || jo.optBoolean("isSuccess") || "SUCCESS".equalsIgnoreCase(jo.optString("resultCode")) || "200".equals(jo.optString("resultCode")) || "处理成功".equals(jo.optString("desc")));
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
