package io.github.aw1y2z.sesame.util.diagnostics;

import java.util.LinkedHashMap;
import java.util.Map;

/** 只对本地生成的诊断做严格投影，绝不透传宿主任意文本。移植自 GR 分支，见 doc/MyFix.md。 */
public final class CaptchaDiagnosticEvent {
    private CaptchaDiagnosticEvent() { }

    public static String matchFailure(String error) {
        if (error == null) return "matchError=UNKNOWN";
        java.util.regex.Matcher ambiguous = java.util.regex.Pattern.compile(
                "ambiguous image match score=(-?[0-9]{1,2}\\.[0-9]{4}) verticalOffset=(-?[0-9]{1,4})").matcher(error);
        if (ambiguous.matches()) return "matchError=AMBIGUOUS score=" + ambiguous.group(1)
                + " verticalOffset=" + ambiguous.group(2);
        java.util.regex.Matcher low = java.util.regex.Pattern.compile("low confidence (-?[0-9]{1,2}\\.[0-9]{4})").matcher(error);
        if (low.matches()) return "matchError=LOW_CONFIDENCE score=" + low.group(1);
        if (error.equals("matching timed out")) return "matchError=TIMEOUT";
        if (error.equals("invalid image") || error.equals("invalid bitmap")) return "matchError=INVALID_IMAGE";
        if (error.equals("captcha image region is outside snapshot bounds")) return "matchError=OUTSIDE_SNAPSHOT";
        if (error.equals("no displacement candidates") || error.equals("no candidates around cluster peak")) return "matchError=NO_CANDIDATES";
        if (error.equals("ambiguous match at search boundary")) return "matchError=SEARCH_BOUNDARY";
        return "matchError=UNKNOWN";
    }

    public static String sanitize(String text) {
        if (text == null || text.length() > 8192) return null;
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) return null;
        boolean validation = "SESAME_VALIDATION_EVENT".equals(tokens[0]);
        if (!validation && !"SESAME_PUZZLE_EVENT".equals(tokens[0])) return null;
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            int equals = tokens[i].indexOf('=');
            if (equals <= 0) continue;
            String key = tokens[i].substring(0, equals), value = tokens[i].substring(equals + 1);
            if (fields.containsKey(key)) return null;
            if (valid(key, value)) fields.put(key, value);
        }
        StringBuilder out = new StringBuilder(tokens[0]);
        for (Map.Entry<String, String> field : fields.entrySet())
            out.append(' ').append(field.getKey()).append('=').append(field.getValue());
        out.append(" finalUiResult=UNCONFIRMED");
        return fields.isEmpty() ? null : out.toString();
    }

    private static boolean valid(String key, String value) {
        switch (key) {
            case "version": return value.matches("[0-9.]{1,20}");
            case "eventId": case "windowId": case "sessionId":
                return value.equals(ValidationDiagnosticEvent.id(value));
            case "phase":
                return value.matches("SUBMITTED|RESULT|IMAGE|DISCARDED|SCAN|BLOCKED|PRESENCE|START|CAPTURE|MATCH|INPUT|END|PROBE|CALLBACK|SCAN_END");
            case "reason":
                return value.matches("OK|UNKNOWN|NO_PROMPT|NO_TARGET|CONTROL_STOPPED|MANUAL_INTERRUPTED|ACTIVE_ATTEMPT|AUTO_DISABLED|BUDGET_USED|WINDOW_STOPPED|WINDOW_UNAVAILABLE|NO_FOCUS|NOT_READY|CAPTURE_FAILED|CAPTURE_DEGRADED|NOT_DETECTED|MATCH_REJECTED|TIMEOUT|CANCELLED|WINDOW_CLOSED|INPUT_FINISHED|INPUT_REJECTED|SLIDER_NOT_RESET|TRACK_NOT_FOUND|GEOMETRY_INVALID|WORK_FAILED|PROBE_UNAVAILABLE|CALLBACK_TIMEOUT|PAGE_STATE|QUEUE_FULL");
            case "outcome":
                return value.matches("RESPONSE_ACCEPTED|RESPONSE_NOT_ACCEPTED|REQUEST_FAILED|UNKNOWN|CANCELLED|TIMEOUT|AMBIGUOUS|ACCOUNT_CHANGED");
            case "inputKind": return value.matches("AUTO|MANUAL|UNKNOWN");
            case "association": return value.matches("WINDOW_TIME_CANDIDATE|UNCONFIRMED");
            case "focused": case "shown": case "attached": case "foreground": case "autoEnabled":
            case "success": case "degraded": case "clamped":
            case "downAccepted": case "upAccepted": case "visible":
            case "failureText": case "successText": case "loadingText":
                return value.matches("true|false");
            case "image": return value.equals(ValidationDiagnosticEvent.imageName(value));
            case "attempt": case "gesture": case "call":
                return value.matches("-?[0-9]{1,10}");
            case "pendingCount": return value.matches("[0-2]");
            case "elapsedMs": case "durationMs": case "width": case "height": case "dropped": case "moveCount":
                return value.matches("[0-9]{1,6}");
            case "businessCode": return value.matches("-?[0-9]{1,5}");
            case "distance": case "touchDistance": case "score": case "verticalOffset":
                return value.matches("-?[0-9]{1,5}(?:\\.[0-9]{1,4})?");
            case "matcher":
                return value.matches("(?:(?:strong|cluster)-best(?:-pixel-refined)?|(?:localized|narrow|compact|interior)-texture-consensus)(?:-extended-range)?|unknown");
            case "matchError":
                return value.matches("UNKNOWN|AMBIGUOUS|LOW_CONFIDENCE|TIMEOUT|INVALID_IMAGE|OUTSIDE_SNAPSHOT|NO_CANDIDATES|SEARCH_BOUNDARY");
            case "stage":
                return value.matches("before_input|input_returned|after_input_500ms|after_input_1500ms|manual_down|manual_up|manual_after_500ms|manual_after_1500ms|video_observe");
            case "exception":
                return value.matches("UnsupportedOperationException|InvocationTargetException|IllegalAccessException|IllegalArgumentException|SecurityException|UNKNOWN");
            default: return false;
        }
    }
}
