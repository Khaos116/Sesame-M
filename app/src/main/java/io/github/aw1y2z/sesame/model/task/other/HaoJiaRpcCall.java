package io.github.aw1y2z.sesame.model.task.other;

import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.hook.ApplicationHook;

/** 好家无忧卡活动用到的 RPC。移植自 GR 分支，见 docs/MyFix.md。 */
public final class HaoJiaRpcCall {
    private static final String CHANNEL = "jiaofei_card_promo";
    private static final String OPERATION_PARAM_ID = "independent_component_program2023082800847098";

    private HaoJiaRpcCall() {
    }

    public static String querySignIn() throws JSONException {
        return request("independent_component_sign_in_00966139_independent_component_sign_in_recall", null);
    }

    public static String doSignIn(String code) throws JSONException {
        JSONObject content = new JSONObject();
        content.put("code", code);
        return request("independent_component_sign_in_00966139_independent_component_sign_in", content);
    }

    public static String queryTaskList() throws JSONException {
        return request("independent_component_task_reward_00793835_independent_component_task_reward_query", null);
    }

    public static String applyTask(String taskCode) throws JSONException {
        JSONObject content = new JSONObject();
        content.put("code", taskCode);
        return request("independent_component_task_reward_00793835_independent_component_task_reward_apply", content);
    }

    private static String request(String componentId, JSONObject content) throws JSONException {
        JSONObject payload = new JSONObject();
        payload.put("channel", CHANNEL);
        if (content != null) {
            Iterator<String> keys = content.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                payload.put(key, content.get(key));
            }
        }
        JSONObject components = new JSONObject();
        components.put(componentId, payload);
        JSONObject request = new JSONObject();
        request.put("channel", CHANNEL);
        request.put("components", components);
        request.put("operationParamIdentify", OPERATION_PARAM_ID);
        request.put("source", "jiaofei");
        return ApplicationHook.requestString("alipay.imasp.program.programInvoke", "[" + request + "]");
    }
}
