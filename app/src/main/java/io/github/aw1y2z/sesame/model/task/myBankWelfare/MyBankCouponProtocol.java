package io.github.aw1y2z.sesame.model.task.myBankWelfare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 福利券查询与使用参数。移植自 GR 分支，见 docs/MyFix.md。 */
public final class MyBankCouponProtocol {
    public static final String QUERY_METHOD = "com.alipay.loanpromoweb.promo.cert.query";
    public static final String QUERY_ARGS = "[{\"certTemplateIdSet\":[\"CT02048186\",\"CT32675397\"]}]";
    public static final String USE_METHOD = "com.alipay.loanpromoweb.promo.playcenter.playTrigger.trigger";
    public static final String USE_ARGS = "[{\"extInfo\":{},\"operation\":\"MYBK_DACU_INTERACTIVE_ZHB\",\"playId\":\"PLAY100576638\"}]";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    private MyBankCouponProtocol() { }

    public static int available(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid coupon response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("coupon query failed");
        JsonNode cert = root.path("result").path("cert");
        if (!cert.isObject() || cert.size() > 2) throw new IOException("invalid coupon mapping");
        int available = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = cert.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!"CT02048186".equals(entry.getKey()) && !"CT32675397".equals(entry.getKey())) {
                throw new IOException("unexpected coupon template");
            }
            JsonNode count = entry.getValue();
            if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0
                    || count.intValue() > 1000) throw new IOException("invalid coupon count");
            available += count.intValue();
        }
        return available;
    }
}
