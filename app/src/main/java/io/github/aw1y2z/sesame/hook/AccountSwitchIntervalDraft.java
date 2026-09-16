package io.github.aw1y2z.sesame.hook;

/**
 * 设置弹窗里"切换间隔"输入框的取值规则：留空按开关状态给默认值/保留旧值，
 * 关闭时永远不会因为输入框内容非法而保存失败。移植自 GR 分支，见 doc/MyFix.md。
 */
public final class AccountSwitchIntervalDraft {
    /** 账号之间固定切换缓冲（秒）。 */
    public static final int ACCOUNT_INTERVAL_SECONDS = 15;
    /** 整轮冷却间隔下限（秒），允许短时间用于测试。 */
    public static final int MIN_SECONDS = 15;
    public static final int MAX_SECONDS = 86400;
    /** 默认整轮冷却间隔为 2 小时（7200秒）。 */
    public static final int DEFAULT_SECONDS = 7200;
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
