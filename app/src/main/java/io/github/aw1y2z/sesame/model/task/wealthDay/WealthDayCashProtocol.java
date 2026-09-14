package io.github.aw1y2z.sesame.model.task.wealthDay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 从 WealthDayBff 恢复的现金卡协议；不会自动完成活动任务。移植自 GR 分支，见 doc/MyFix.md。 */
public final class WealthDayCashProtocol {
    public static final String QUERY_METHOD = "com.alipay.wealthdaybff.open2025.query";
    public static final String QUERY_ARGS = "[{\"cardIds\":[\"cash\"],\"pageParams\":{}}]";
    public static final String CLAIM_METHOD = "com.alipay.wealthdaybff.open2025.drawCash";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    private WealthDayCashProtocol() { }

    /** 服务端返回的校验过的领取值，只在本轮内使用。 */
    public static final class Claim {
        private final String amount;
        private final String businessNumber;
        public final long amountFen;

        private Claim(String amount, String businessNumber, long amountFen) {
            this.amount = amount;
            this.businessNumber = businessNumber;
            this.amountFen = amountFen;
        }

        public String arguments() {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("extractAmount", amount);
            args.put("extractBizNo", businessNumber);
            return JsonUtil.toJsonString(Collections.singletonList(args));
        }
    }

    /** 只有明确可提取现金余额、且在配置上限内才返回领取对象。 */
    public static Claim claim(String response, int maxFen) throws IOException {
        if (maxFen <= 0) throw new IllegalArgumentException("invalid cash limit");
        if (response == null || response.length() > 262144) throw new IOException("invalid response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("query not successful");
        JsonNode cash = root.path("result").path("cash");
        if (!cash.isObject()) throw new IOException("missing cash card");
        JsonNode canExtract = cash.get("canExtract");
        if (canExtract == null || !canExtract.isBoolean()) throw new IOException("unknown availability");
        if (!canExtract.booleanValue()) return null;
        JsonNode amount = cash.get("extractableAmount"), business = cash.get("extractBizNo");
        if (amount == null || !amount.isTextual() || !amount.textValue().matches("[0-9]{1,9}(\\.[0-9]{1,2})?")
                || business == null || !business.isTextual() || business.textValue().trim().isEmpty()
                || business.textValue().length() > 256) throw new IOException("invalid cash claim fields");
        long fen = new BigDecimal(amount.textValue()).movePointRight(2).longValueExact();
        if (fen <= 0 || fen > maxFen) return null;
        return new Claim(amount.textValue(), business.textValue(), fen);
    }
}
