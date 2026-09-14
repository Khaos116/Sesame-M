package io.github.aw1y2z.sesame.util.diagnostics;

/** 固定诊断词汇表，不透传任何宿主文本/标识/响应体。移植自 GR 分支，见 doc/MyFix.md。 */
public final class ValidationDiagnosticEvent {
    private ValidationDiagnosticEvent() { }

    public static String format(String eventId, String windowId, String phase, String outcome,
            String kind, boolean focused, int pending, String image) {
        return "SESAME_VALIDATION_EVENT eventId=" + id(eventId) + " windowId=" + id(windowId)
                + " phase=" + allowed(phase, "SUBMITTED|RESULT|IMAGE|DISCARDED")
                + " outcome=" + allowed(outcome, "RESPONSE_ACCEPTED|RESPONSE_NOT_ACCEPTED|REQUEST_FAILED|UNKNOWN|CANCELLED|TIMEOUT|AMBIGUOUS|ACCOUNT_CHANGED")
                + " inputKind=" + allowed(kind, "AUTO|MANUAL|UNKNOWN")
                + " association=WINDOW_TIME_CANDIDATE focused=" + focused
                + " pendingCount=" + Math.max(0, Math.min(2, pending))
                + " image=" + imageName(image) + " finalUiResult=UNCONFIRMED";
    }

    public static String id(String value) {
        return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
                ? value : "unknown";
    }

    public static String imageName(String value) {
        if (value != null && value.matches("failure-[0-9]{13}-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.png")) return value;
        return allowed(value, "PENDING|NONE|SAVED|DISCARDED|UNAVAILABLE|WRITE_FAILED|CLEANUP_DEFERRED");
    }

    private static String allowed(String value, String pattern) {
        return value != null && value.matches(pattern) ? value : "UNKNOWN";
    }
}
