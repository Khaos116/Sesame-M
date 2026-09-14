package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.aw1y2z.sesame.util.JsonUtil;

/**
 * vv.record 成功响应的严格只读视图。没有实际样本可参照的形状一律归为 UNKNOWN，
 * 唯一被允许的后续动作是重新查询任务列表：这个类不决定也不构造领取奖励请求。
 * 移植自 GR 分支，见 doc/MyFix.md。
 */
public final class VideoRecordOutcome {
    public enum RewardState { REWARD_PENDING, REWARD_CLAIMED, TASK_UNFINISHED, UNKNOWN }

    public final String contentId;
    public final RewardState rewardState;

    private VideoRecordOutcome(String contentId, RewardState rewardState) {
        this.contentId = contentId;
        this.rewardState = rewardState;
    }

    public static VideoRecordOutcome parse(String response) {
        if (response == null) return unknown();
        String trimmed = response.trim();
        if (trimmed.isEmpty() || trimmed.length() > 262144) return unknown();
        JsonNode root;
        try {
            root = JsonUtil.copyMapper().readTree(trimmed);
        } catch (Exception parseFailure) {
            return unknown();
        }
        if (root == null || !root.isObject()) return unknown();
        JsonNode data = root.path("data").isObject() ? root.path("data") : root;
        return new VideoRecordOutcome(echoContentId(root, data), rewardState(statusOf(data)));
    }

    private static String statusOf(JsonNode data) {
        for (String field : new String[]{"recordStatus", "rewardStatus", "taskStatus", "status"}) {
            JsonNode value = data.get(field);
            if (value != null && value.isTextual()) return value.textValue();
        }
        return "";
    }

    private static RewardState rewardState(String status) {
        if ("TO_RECEIVE".equals(status) || "available".equals(status)) return RewardState.REWARD_PENDING;
        if ("RECEIVED".equals(status) || "received".equals(status) || "FINISHED".equals(status)) {
            return RewardState.REWARD_CLAIMED;
        }
        if ("progressing".equals(status) || "PROCESSING".equals(status) || "UNFINISHED".equals(status)) {
            return RewardState.TASK_UNFINISHED;
        }
        return RewardState.UNKNOWN;
    }

    private static String echoContentId(JsonNode root, JsonNode data) {
        for (JsonNode node : new JsonNode[]{data, root}) {
            JsonNode value = node.get("contentId");
            if (value != null && value.isTextual()) {
                String id = value.textValue();
                return id.matches("[A-Za-z0-9_.:-]{1,256}") ? id : "";
            }
        }
        return "";
    }

    private static VideoRecordOutcome unknown() { return new VideoRecordOutcome("", RewardState.UNKNOWN); }
}
