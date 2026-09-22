package io.github.aw1y2z.sesame.rpc.intervallimit;

/**
 * Unified failure classification and cooldown durations for the bounded opt-in
 * reward request layer. Risk-control denial gets the longest pause but is never
 * recorded as "unsupported": the cooldown expires and the method stays eligible.
 * 移植自 GR 分支，见 docs/MyFix.md。
 */
public final class RpcFailurePolicy {
    /** Explicit risk-control refusal, confirmed on device for error=1009. */
    public static final long RISK_DENIED_MS = 86_400_000L;
    /** Server-side window/eligibility rejection, confirmed for 1GBM02. */
    public static final long BUSINESS_REJECTED_MS = 6L * 3_600_000L;
    /** Server temporarily unavailable, confirmed for 3000/SYSTEM_ERROR. */
    public static final long SYSTEM_ERROR_MS = 12L * 3_600_000L;
    /** Longest sanitized message kept in local logs. */
    public static final int MAX_MESSAGE_LENGTH = 96;

    private RpcFailurePolicy() { }

    /** Categories that change the model cooldown; unknown failures stay UNCLEAR. */
    public enum Kind { BUSINESS_REJECTED, SYSTEM_ERROR, UNCLEAR }

    /** Exact root markers only; text merely mentioning denial is insufficient. */
    public static boolean isRiskDenied(String code, String errorMessage) {
        return "1009".equals(code) || "访问被拒绝".equals(errorMessage);
    }

    /** Classifies a normalized code; risk denial is checked separately by callers. */
    public static Kind kind(String code) {
        if ("1GBM02".equalsIgnoreCase(code)) return Kind.BUSINESS_REJECTED;
        if ("3000".equals(code) || "SYSTEM_ERROR".equalsIgnoreCase(code)) return Kind.SYSTEM_ERROR;
        return Kind.UNCLEAR;
    }

    public static long cooldownMs(Kind kind) {
        if (kind == Kind.BUSINESS_REJECTED) return BUSINESS_REJECTED_MS;
        if (kind == Kind.SYSTEM_ERROR) return SYSTEM_ERROR_MS;
        return 0L;
    }

    /** Bounded, non-null log message; never carries raw response payloads. */
    public static String boundedMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.length() > MAX_MESSAGE_LENGTH ? raw.substring(0, MAX_MESSAGE_LENGTH) : raw;
    }
}
