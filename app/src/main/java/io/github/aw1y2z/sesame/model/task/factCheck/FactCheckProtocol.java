package io.github.aw1y2z.sesame.model.task.factCheck;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 只读查询参数构造；答题提交这次故意不做。移植自 GR 分支，见 doc/MyFix.md。 */
public final class FactCheckProtocol {
    public static final String INDEX_METHOD = "com.alipay.factcheck.questionnaireIndex";
    public static final String SESSION_METHOD = "com.alipay.factcheck.questionnaire";
    private FactCheckProtocol() { }
    public static String args() {
        Map<String,Object> ext = new LinkedHashMap<>();
        ext.put("lifecycleId", java.util.UUID.randomUUID().toString());
        Map<String,Object> root = new LinkedHashMap<>();
        root.put("businessTypeEnum", "APPLET"); root.put("extInfo", ext);
        root.put("previewDate", System.currentTimeMillis()); root.put("source", "factcheck");
        return JsonUtil.toJsonString(Collections.singletonList(root));
    }
    public static boolean validIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,256}");
    }
}
