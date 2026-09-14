package io.github.aw1y2z.sesame.rpc.intervallimit;

/**
 * Pure request-budget and pacing rules for the bounded opt-in reward layer:
 * at most six requests per run and at least 1500 ms between calls. Process-wide
 * per-method intervals for the legacy modules stay with RpcIntervalLimit.
 * 移植自 GR 分支，见 doc/MyFix.md。
 */
public final class RequestBudgetPolicy {
    public static final int MAX_REQUESTS = 6;
    public static final long MIN_INTERVAL_MS = 1500L;

    private RequestBudgetPolicy() { }

    /** True only while the run still has a request slot left. */
    public static boolean mayRequest(int used, int cap) {
        return used >= 0 && used < cap;
    }

    /**
     * Milliseconds to wait before the next call: 0 for the first call
     * (lastCallNanos == 0) and once the interval has elapsed, otherwise the
     * remaining share of the interval. The wait is clamped to one full interval.
     */
    public static long pacingMs(long lastCallNanos, long nowNanos) {
        if (lastCallNanos == 0) return 0L;
        long remaining = MIN_INTERVAL_MS - (nowNanos - lastCallNanos) / 1_000_000L;
        return Math.min(Math.max(remaining, 0L), MIN_INTERVAL_MS);
    }

    /** Absolute cooldown timestamp applied on top of the current time. */
    public static long cooldownUntil(long now, long cooldownMs) {
        return now + cooldownMs;
    }
}
