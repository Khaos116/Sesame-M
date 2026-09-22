package io.github.aw1y2z.sesame.model.task.healthIslandRewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 已确认的健康岛阶段奖励协议；解析与参数构造不发起请求。移植自 GR 分支，见 docs/MyFix.md。 */
public final class HealthIslandStageProtocol {
    public static final String BASE_METHOD = "com.alipay.neverland.biz.rpc.queryBaseinfo";
    public static final String QUERY_METHOD = "com.alipay.neverland.biz.rpc.queryMapStageRewardInfo";
    public static final String CLAIM_METHOD = "com.alipay.neverland.biz.rpc.mapStageReward";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();
    private static final int MAX_STAGES = 50;

    private HealthIslandStageProtocol() { }

    public static String mapId(String response) throws IOException {
        JsonNode id = data(response).get("mapId");
        if (id == null || !id.isTextual()) throw new IOException("missing map ID");
        String value = id.textValue();
        validateMap(value);
        return value;
    }

    public static List<Integer> claimableLevels(String response) throws IOException {
        JsonNode stages = data(response).get("specialActivityQueryResults");
        if (stages == null || !stages.isArray() || stages.size() > MAX_STAGES) {
            throw new IOException("invalid stage list");
        }
        List<Integer> levels = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            JsonNode stage = stages.get(i);
            if (!stage.isObject()) throw new IOException("invalid stage entry");
            JsonNode code = stage.path("functionVO").path("code");
            if (code.isTextual() && "TO_RECEIVE".equals(code.textValue())) levels.add(i + 1);
        }
        return Collections.unmodifiableList(levels);
    }

    public static String baseArgs() { return encode(base()); }

    public static String queryArgs(String mapId) {
        Map<String, Object> args = base();
        validateMap(mapId);
        args.put("mapId", mapId);
        return encode(args);
    }

    public static String claimArgs(String mapId, int level) {
        validateMap(mapId);
        if (level < 1 || level > MAX_STAGES) throw new IllegalArgumentException("invalid stage level");
        Map<String, Object> args = base();
        args.put("mapId", mapId);
        args.put("level", level);
        return encode(args);
    }

    private static JsonNode data(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue() || !root.path("data").isObject()) {
            throw new IOException("invalid stage response");
        }
        return root.get("data");
    }

    private static void validateMap(String mapId) {
        if (mapId == null || mapId.trim().isEmpty() || mapId.length() > 128) {
            throw new IllegalArgumentException("invalid map ID");
        }
    }

    private static Map<String, Object> base() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("branchId", "MASTER");
        args.put("source", "jkddicon");
        return args;
    }

    private static String encode(Map<String, Object> args) {
        return JsonUtil.toJsonString(Collections.singletonList(args));
    }
}
