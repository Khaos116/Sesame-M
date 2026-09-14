package io.github.aw1y2z.sesame.hook;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.aw1y2z.sesame.util.AtomicConfigFile;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;

/**
 * 自动切号是全局开关，独立存一个文件（不放进 ConfigV2），切换/加载某个账号的配置
 * 不会影响这两个设置。移植自 GR 分支并简化（去掉原版桥接进 ModelField 系统的部分，
 * M 这边改用独立设置弹窗），见 doc/MyFix.md。
 */
public final class AccountSwitchSettings {
    private AccountSwitchSettings() { }

    public static final class Values {
        public final boolean enabled;
        public final int seconds;
        public final long activation;
        Values(boolean enabled, int seconds) { this(enabled, seconds, 0); }
        Values(boolean enabled, int seconds, long activation) {
            this.enabled = enabled; this.seconds = seconds; this.activation = activation;
        }
    }

    private static File file() { return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_settings.json"); }

    public static synchronized Values read() {
        return read(file());
    }

    static synchronized Values read(File file) {
        try {
            if (!file.isFile() || file.length() > 1024) return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
            try (FileInputStream input = new FileInputStream(file);
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    bytes.write(buffer, 0, count);
                    if (bytes.size() > 1024) return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
                }
                return decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (Exception invalid) { return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS); }
    }

    static Values decode(String json) throws Exception {
        JsonNode node = JsonUtil.copyMapper()
                .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(json);
        if (!node.isObject() || !node.path("enabled").isBoolean()
                || !node.path("intervalSeconds").isIntegralNumber()
                || !node.path("intervalSeconds").canConvertToInt()) return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
        int seconds = node.path("intervalSeconds").intValue();
        if (seconds < AccountSwitchIntervalDraft.MIN_SECONDS || seconds > AccountSwitchIntervalDraft.MAX_SECONDS)
            return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
        JsonNode activation = node.get("activation");
        if (activation != null && (!activation.isIntegralNumber() || !activation.canConvertToLong()
                || activation.longValue() < 0)) return new Values(false, AccountSwitchIntervalDraft.DEFAULT_SECONDS);
        return new Values(node.path("enabled").booleanValue(), seconds, activation == null ? 0 : activation.longValue());
    }

    /** 关闭一定能存进去；勾选启用但输入框留空时按默认值算。 */
    public static boolean saveDraft(boolean enabled, String intervalText) {
        return saveDraft(file(), enabled, intervalText);
    }

    static synchronized boolean saveDraft(File destination, boolean enabled, String intervalText) {
        int seconds = AccountSwitchIntervalDraft.resolve(enabled, intervalText, read(destination).seconds);
        return update(destination, enabled, seconds);
    }

    private static synchronized boolean update(File destination, Boolean enabled, Integer seconds) {
        Values previous = read(destination);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled == null ? previous.enabled : enabled);
        data.put("intervalSeconds", seconds == null ? previous.seconds
                : Math.max(AccountSwitchIntervalDraft.MIN_SECONDS, Math.min(AccountSwitchIntervalDraft.MAX_SECONDS, seconds)));
        boolean activate = Boolean.TRUE.equals(enabled) && !previous.enabled;
        data.put("activation", activate ? (previous.activation == Long.MAX_VALUE ? 0 : previous.activation + 1) : previous.activation);
        try {
            AtomicConfigFile.write(JsonUtil.toJsonString(data), destination);
            return true;
        } catch (Exception failed) {
            return false;
        }
    }
}
