package io.github.aw1y2z.sesame.model.task.promoprodRewards;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 移植自 GR 分支，见 docs/MyFix.md。 */
public final class PromoprodQueryProtocol {
    public static final String METHOD = "alipay.promoprod.task.query.queryTaskList";
    public static final String ARGS = "[{\"consultAccessFlag\":true,\"planId\":\"AP17187348\"}]";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    private PromoprodQueryProtocol() { }

    /** 汇总不含任务ID/凭证/金额/推断出的领取资格。 */
    public static final class Summary {
        public final int total, transformer, signupComplete, received, other, unknown;
        private Summary(int total, int transformer, int signupComplete, int received, int other, int unknown) {
            this.total = total;
            this.transformer = transformer;
            this.signupComplete = signupComplete;
            this.received = received;
            this.other = other;
            this.unknown = unknown;
        }
    }

    /** 只读取明确的根字段；缺失/类型不对不会当成成功的空列表。 */
    public static Summary summarize(String response) throws IOException {
        if (response == null || response.length() > 262144) throw new IOException("invalid task response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue()) throw new IOException("task query not successful");
        JsonNode tasks = root.path("taskDetailList");
        if (tasks.isMissingNode() || tasks.isNull()) return new Summary(0, 0, 0, 0, 0, 0);
        if (!tasks.isArray() || tasks.size() > 100) throw new IOException("invalid task list");
        int transformer = 0, signup = 0, received = 0, other = 0, unknown = 0;
        for (JsonNode task : tasks) {
            if (!task.isObject() || !task.path("taskType").isTextual()) { unknown++; continue; }
            if ("TRANSFORMER".equals(task.path("taskType").textValue())) transformer++;
            else other++;
            JsonNode status = task.path("taskProcessStatus");
            if (status.isTextual() && "SIGNUP_COMPLETE".equals(status.textValue())) signup++;
            else if (status.isTextual() && "RECEIVE_SUCCESS".equals(status.textValue())) received++;
            else unknown++;
        }
        return new Summary(tasks.size(), transformer, signup, received, other, unknown);
    }
}
