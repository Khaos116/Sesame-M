package io.github.aw1y2z.sesame.hook;

/**
 * 设置弹窗里"切换间隔"输入框的取值规则：留空按开关状态给默认值/保留旧值，
 * 关闭时永远不会因为输入框内容非法而保存失败。移植自 GR 分支，见 doc/MyFix.md。
 */
public final class AccountSwitchIntervalDraft {
    /** 切号后至少间隔 2 小时才允许下一轮自动切换，避免频繁切号触发风控。 */
    public static final int MIN_SECONDS = 7200;
    public static final int MAX_SECONDS = 86400;
    public static final int DEFAULT_SECONDS = MIN_SECONDS;
    private AccountSwitchIntervalDraft() { }

    public static int resolve(boolean enabled, String input, int previousSeconds) {
        int previous = previousSeconds >= MIN_SECONDS && previousSeconds <= MAX_SECONDS
                ? previousSeconds : DEFAULT_SECONDS;
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) return enabled ? DEFAULT_SECONDS : previous;
        try {
            int value = Integer.parseInt(text);
            if (value < MIN_SECONDS || value > MAX_SECONDS) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            if (!enabled) return previous;
            throw invalid;
        }
    }
}
