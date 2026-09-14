package io.github.aw1y2z.sesame.model.task.videoRewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** vv.record 之前的动态任务详情契约。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoTaskQueryProtocol {
    public static final String METHOD = "alipay.content.interact.task.query";
    public static final String ARGS = "[{\"pageType\":\"index\",\"tab3SpecialVer\":\"normal\","
            + "\"taskExt\":{\"fromTab3BottomBar\":true,\"openTab3\":false,\"retryCount\":0}}]";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();
    private VideoTaskQueryProtocol() { }

    /** 只解析有界的动态任务列表；格式不对的条目直接丢弃，不会变成可写任务。 */
    public static List<VideoTaskDetail> parse(String response) throws IOException {
        if (response == null || response.length() > 524288) throw new IOException("invalid task query response");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("task query failed");
        JsonNode list = root.path("taskList");
        if (!list.isArray() || list.size() > 100) throw new IOException("invalid taskList");
        List<VideoTaskDetail> tasks = new ArrayList<>();
        for (JsonNode row : list) {
            if (!row.isObject()) continue;
            try {
                VideoTaskDetail task = VideoTaskDetail.parse(row.toString());
                if (!task.completed) tasks.add(task);
            } catch (IOException ignored) { }
        }
        return Collections.unmodifiableList(tasks);
    }
}
