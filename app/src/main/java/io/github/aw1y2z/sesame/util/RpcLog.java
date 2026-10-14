package io.github.aw1y2z.sesame.util;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;

/** Summarizes runtime failures and advertising payloads only at the logging boundary. */
public final class RpcLog {
    private RpcLog() { }

    private static boolean isAdvertising(RpcEntity rpc) {
        String method = rpc.getRequestMethod();
        return method != null && method.startsWith("com.alipay.adexchange.");
    }

    public static String requestData(RpcEntity rpc) {
        return isAdvertising(rpc) ? "[广告请求参数已省略]" : rpc.getRequestData();
    }

    /** 仅供运行日志使用；错误日志沿用原有参数和回执输出。 */
    public static String errorSummary(JSONObject response) {
        JSONObject summary = MyUtils.newJSONObject();
        try {
            for (String field : new String[]{"success", "isSuccess", "resultCode", "retCode", "error", "errorCode", "code"}) {
                Object value = response.opt(field);
                if (value instanceof Number || value instanceof Boolean
                        || value instanceof String && ((String) value).matches("[A-Za-z0-9_.-]{1,64}")) summary.put(field, value);
            }
            String message = RpcRequestGuard.errorMessage(response);
            // 不截断后再脱敏，避免长消息把凭据标记截掉。
            for (String field : new String[]{"errorMessage", "errorMsg", "resultDesc", "resultMsg", "memo", "resultView", "desc"}) {
                String raw = response.optString(field);
                if (raw.isEmpty()) continue;
                if (raw.matches("(?is).*(token|cookie|auth|secret|signature|签名|授权码|令牌|https?://|alipays?://|[{}\\[\\]]|2088[0-9]{12}).*"))
                    message = "[原因含敏感信息，已省略]";
                break;
            }
            summary.put("原因", message.replaceAll("[\\r\\n\\t]", " "));
        } catch (Exception ignored) { }
        return summary.toString();
    }

    public static String responseData(RpcEntity rpc) {
        String raw = rpc.getResponseString();
        if (!isAdvertising(rpc) || raw == null) return raw;
        JSONObject response = MyUtils.newJSONObject(raw);
        JSONObject summary = MyUtils.newJSONObject();
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
