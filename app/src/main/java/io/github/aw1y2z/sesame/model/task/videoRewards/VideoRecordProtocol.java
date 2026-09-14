package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 只有真实播放达标后才构造服务端记录请求。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoRecordProtocol {
    public static final String METHOD = "alipay.content.interact.task.vv.record";
    private VideoRecordProtocol() { }
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    /** 不合成任何钱包/播放器数据，字段全部由调用方提供。 */
    public static String arguments(String contentId, String sourcePage, String rewardParams) throws Exception {
        if (contentId == null || !contentId.matches("[A-Za-z0-9_.:-]{1,256}"))
            throw new IllegalArgumentException("invalid contentId");
        if (sourcePage == null || sourcePage.length() > 512 || sourcePage.indexOf('"') >= 0)
            throw new IllegalArgumentException("invalid sourcePage");
        if (rewardParams == null || rewardParams.isEmpty() || rewardParams.length() > 65536)
            throw new IllegalArgumentException("missing rewardParams");
        // 校验 rewardParams 是合法 JSON，但保持服务端原始形状不动。
        JSON.readTree(rewardParams);
        ObjectNode row = JSON.createObjectNode();
        row.put("contentId", contentId);
        row.put("sourcePage", sourcePage);
        row.put("rewardParams", rewardParams);
        ArrayNode array = JSON.createArrayNode();
        array.add(row);
        return JSON.writeValueAsString(array);
    }
}
