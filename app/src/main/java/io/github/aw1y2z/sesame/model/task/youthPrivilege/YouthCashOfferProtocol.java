package io.github.aw1y2z.sesame.model.task.youthPrivilege;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 只读现金活动库存；出现在列表里不代表兑换资格。移植自 GR 分支，见 doc/MyFix.md。 */
public final class YouthCashOfferProtocol {
    public static final String METHOD = "alipay.membertangram.biz.rpc.student.queryCashExchangeInfoResult";
    public static final String ARGS = "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    private YouthCashOfferProtocol() { }

    public static final class Inventory {
        public final int entries, uniqueOffers, unknown, duplicates;
        private Inventory(int entries, int uniqueOffers, int unknown, int duplicates) {
            this.entries = entries; this.uniqueOffers = uniqueOffers;
            this.unknown = unknown; this.duplicates = duplicates;
        }
    }

    /** 只统计服务端返回的标识数，不导出ID也不推断写权限。 */
    public static Inventory inspect(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid cash-offer response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("cash-offer query failed");
        JsonNode groups = root.path("cashExchangeInfoVOList");
        if (!groups.isArray() || groups.size() > 20) throw new IOException("invalid cash-offer groups");
        Set<String> ids = new HashSet<>();
        int entries = 0, unknown = 0, duplicates = 0;
        for (JsonNode group : groups) {
            JsonNode prizes = group.path("prizeInfoVOList");
            if (!group.isObject() || !prizes.isArray() || prizes.size() > 100 - entries) {
                throw new IOException("invalid cash-offer list");
            }
            for (JsonNode prize : prizes) {
                entries++;
                JsonNode id = prize.path("benefitId");
                if (!prize.isObject() || !id.isTextual()
                        || !id.textValue().matches("[A-Za-z0-9_.:-]{1,128}")) { unknown++; continue; }
                if (!ids.add(id.textValue())) duplicates++;
            }
        }
        return new Inventory(entries, ids.size(), unknown, duplicates);
    }
}
