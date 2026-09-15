package io.github.aw1y2z.sesame.util;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;

/** Summarizes advertising payloads only at the logging boundary. */
public final class RpcLog {
    private RpcLog() { }

    private static boolean isAdvertising(RpcEntity rpc) {
        String method = rpc.getRequestMethod();
        return method != null && method.startsWith("com.alipay.adexchange.");
    }

    public static String requestData(RpcEntity rpc) {
        return isAdvertising(rpc) ? "[广告请求参数已省略]" : rpc.getRequestData();
    }

    public static String responseData(RpcEntity rpc) {
        String raw = rpc.getResponseString();
        if (!isAdvertising(rpc) || raw == null) return raw;
        JSONObject response = MyUtils.newJSONObject(raw);
        JSONObject summary = new JSONObject();
        try {
            for (String field : new String[]{"success", "isSuccess", "retCode", "resultCode",
                    "error", "errorNo", "errorMessage", "errorMsg", "resultDesc"}) {
                Object value = response.opt(field);
                if (value instanceof String) {
                    summary.put(field, RpcFailurePolicy.boundedMessage((String) value));
                } else if (value instanceof Number || value instanceof Boolean) {
                    summary.put(field, value);
                }
            }
            JSONArray ads = response.optJSONArray("adList");
            if (ads != null) summary.put("adCount", ads.length());
            summary.put("原始字符数", raw.length());
            summary.put("详情已省略", true);
        } catch (Exception ignored) { }
        return summary.toString();
    }
}
