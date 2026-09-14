package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 提取服务端下发的钱包红包并构造领取请求。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoWalletRewardProtocol {
    public static final String METHOD = "alipay.content.interact.task.reward";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();
    private VideoWalletRewardProtocol() { }

    public static final class Claim {
        public final String taskType, activityId, rewardParams, status;
        private Claim(String taskType, String activityId, String rewardParams, String status) {
            this.taskType = taskType; this.activityId = activityId;
            this.rewardParams = rewardParams; this.status = status;
        }
    }

    /** 只有服务端 radicalRed 钱包分组才符合领取契约。 */
    public static Claim findClaim(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid wallet response");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("wallet query failed");
        JsonNode groups = root.path("envelopeDetailList");
        if (!groups.isArray() || groups.size() > 20) throw new IOException("invalid wallet groups");
        for (JsonNode group : groups) {
            if (!group.isObject() || !"radicalRed".equals(group.path("type").textValue())) continue;
            JsonNode list = group.path("envelopeVOList");
            if (!list.isArray() || list.size() != 1) continue;
            JsonNode item = list.get(0);
            String params = text(item, "rewardParams");
            String activity = text(item, "activityId");
            String type = text(item, "taskType");
            String status = text(item, "status");
            if (params.isEmpty() || params.length() > 65536 || activity.isEmpty() || type.isEmpty()
                    || !activity.matches("[A-Za-z0-9_.:-]{1,256}") || !type.matches("[A-Za-z0-9_.:-]{1,128}"))
                continue;
            if (!("available".equals(status) || "progressing".equals(status)
                    || "TO_RECEIVE".equals(status))) continue;
            // 保留服务端下发的 rewardParams 原样，但要校验是合法 JSON。
            try { JSON.readTree(params); } catch (Exception ignored) { continue; }
            return new Claim(type, activity, params, status);
        }
        return null;
    }

    /** 不在本地生成任何标识符；rewardParams 转义为 JSON 字符串传给 RPC 桥。 */
    public static String arguments(Claim claim) throws IOException {
        if (claim == null) throw new IOException("no eligible wallet claim");
        ObjectNode row = JSON.createObjectNode();
        row.put("taskType", claim.taskType);
        row.put("taskActivityId", claim.activityId);
        row.put("taskSource", "wallet");
        row.put("rewardParams", claim.rewardParams);
        ArrayNode args = JSON.createArrayNode();
        args.add(row);
        return JSON.writeValueAsString(args);
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        return value != null && value.isTextual() ? value.textValue() : "";
    }
}
