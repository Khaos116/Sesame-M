package io.github.aw1y2z.sesame.model.task.forestPlantRewards;

import io.github.aw1y2z.sesame.util.JsonUtil;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 纯参数构造；种植奖励在确认资格前故意不启用。移植自 GR 分支，见 docs/MyFix.md。 */
public final class ForestPlantProtocol {
    private ForestPlantProtocol() { }
    public static String queryArgs(int page) {
        if (page < 1 || page > 100) throw new IllegalArgumentException("invalid page");
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("bizType", "forestMemberCorp"); m.put("chInfo", "forestTask");
        m.put("pageNum", page); m.put("pageSize", 20); m.put("sessionId", "forestTask");
        m.put("version", "feeds2.0");
        return JsonUtil.toJsonString(Collections.singletonList(m));
    }
}
