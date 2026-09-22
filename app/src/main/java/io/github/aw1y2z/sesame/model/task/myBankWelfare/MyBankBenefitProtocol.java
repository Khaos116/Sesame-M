package io.github.aw1y2z.sesame.model.task.myBankWelfare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 查询可用的福利金权益；不会开通储蓄/理财产品。移植自 GR 分支，见 docs/MyFix.md。 */
public final class MyBankBenefitProtocol {
    public static final String QUERY_METHOD = "com.alipay.loanpromoweb.promo.virtualProfit.queryEnableVirtualProfitV2";
    public static final String USE_METHOD = "com.alipay.loanpromoweb.promo.virtualProfit.batchUseVirtualProfit";
    public static final String SIGN_METHOD = "com.alipay.loanpromoweb.promo.signin.trigger";
    public static final String SIGN_ARGS = "[{\"extInfo\":{},\"sceneId\":\"PLAY102815727\"}]";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();
    private static final List<String> SCENES = Collections.unmodifiableList(Arrays.asList(
            "FULICenter_JKJML", "FULICenter_JZN", "BC3_BC3V1", "BC3_BC3V2", "BC3_BC3V3",
            "SQB_SQBV0", "SQB_SQBV1", "SQB_SQBV2", "SQB_SQBV3", "SQB_SQBV4", "SQB_SQBV5",
            "SQB_SQBV6", "SQB_SQBV7", "SQB_SQBV8", "SQB_SQBV9", "SQB_SQBV10", "SQB_SQBV11",
            "SQB_SQBSIGN", "FULICenter_JKJQW", "FULICenter_WSWF", "FULICenter_FLKZS",
            "FULICenter_KGJXBBF", "FULICenter_AXHZXB", "FULICenter_BBF", "FULICenter_V1",
            "FULICenter_V2", "FULICenter_V3", "FULICenter_V4", "FULICenter_V5", "FULICenter_V6",
            "FULICenter_V7", "FULICenter_YulibaoAUM", "FULICenter_PayByMybank", "FULICenter_DepositAUM",
            "FULICenter_YYYYH", "FULICenter_QYZ", "FULICenter_V7PLUS", "FULICenter_V6PLUS",
            "FULICenter_V5PLUS", "FULICenter_V8", "FULICenter_V9", "FULICenter_V10"));

    private MyBankBenefitProtocol() { }

    public static boolean offersSignIn(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid benefit response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("benefit query failed");
        JsonNode list = root.path("result").path("virtualProfitList");
        if (!list.isArray() || list.size() > 100) throw new IOException("invalid benefit list");
        boolean offered = false;
        for (JsonNode entry : list) {
            if (!entry.isObject()) throw new IOException("invalid benefit group");
            JsonNode type = entry.get("type");
            offered |= type != null && type.isTextual() && "signin".equals(type.textValue());
        }
        return offered;
    }

    public static String queryArguments() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("firstSceneCode", Collections.emptyList());
        args.put("profitType", "ANTBANK_WELFARE_POINT");
        args.put("sceneCode", SCENES);
        args.put("signInSceneId", "PLAY102815727");
        return JsonUtil.toJsonString(Collections.singletonList(args));
    }

    public static List<String> usableIds(String response, int maxCount) throws IOException {
        if (maxCount < 1 || maxCount > 20) throw new IllegalArgumentException("invalid benefit limit");
        if (response == null || response.length() > 262144) throw new IOException("invalid benefit response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("benefit query failed");
        JsonNode list = root.path("result").path("virtualProfitList");
        if (!list.isArray() || list.size() > 100) throw new IOException("invalid benefit list");
        Set<String> unique = new LinkedHashSet<>();
        int entries = 0;
        for (JsonNode group : list) {
            if (!group.isObject()) throw new IOException("invalid benefit group");
            JsonNode type = group.get("type");
            if (type == null || !type.isTextual() || type.textValue().isEmpty()) continue;
            if ("signin".equals(type.textValue())) continue;
            JsonNode ids = group.get("virtualProfitIds");
            if (ids == null || ids.isNull()) continue;
            if (!ids.isArray() || ids.size() > 500 - entries) throw new IOException("invalid benefit IDs");
            entries += ids.size();
            for (JsonNode id : ids) {
                if (!id.isTextual() || id.textValue().trim().isEmpty() || id.textValue().length() > 128) {
                    throw new IOException("invalid benefit ID");
                }
                unique.add(id.textValue());
            }
        }
        List<String> selected = new ArrayList<>(unique);
        return Collections.unmodifiableList(new ArrayList<>(selected.subList(0, Math.min(maxCount, selected.size()))));
    }

    public static String useArguments(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 20) throw new IllegalArgumentException("invalid benefit IDs");
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.trim().isEmpty() || id.length() > 128 || !unique.add(id)) {
                throw new IllegalArgumentException("invalid or duplicate benefit ID");
            }
        }
        return JsonUtil.toJsonString(Collections.singletonList(Collections.singletonMap("virtualProfitIdList", ids)));
    }
}
