package io.github.aw1y2z.sesame.util.diagnostics;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.AtomicConfigFile;
import io.github.aw1y2z.sesame.util.MyUtils;

/** 每账号每日的失败请求摘要；只记录任务定位字段，不保存完整请求或响应。 */
public final class RpcFailureJournal {
    private RpcFailureJournal() { }

    public static File fileFor(File directory, long now) {
        return new File(directory, "rpc-failures." + format(now, "yyyy-MM-dd")
                + "." + directory.getName() + ".json");
    }

    private static String format(long now, String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return format.format(new Date(now));
    }

    private static String value(JSONObject object, String field) {
        Object value = object.opt(field);
        if (!(value instanceof String) && !(value instanceof Number)) return "";
        String text = value.toString().replaceAll("2088\\d{12}", "[UID]");
        return text.substring(0, Math.min(text.length(), 256));
    }

    public static synchronized void record(File directory, String method, String request,
                                           JSONObject result, long now) throws Exception {
        if (!RpcRequestGuard.isFailure(result)
                || "RPC_SKIPPED".equals(result.optString("error"))
                || "RPC_SKIPPED".equals(result.optString("resultCode"))) return;
        JSONObject args = null;
        try { args = new JSONArray(request).optJSONObject(0); } catch (Exception ignored) { }
        if (args == null) args = MyUtils.newJSONObject("{}");
        JSONObject identity = MyUtils.newJSONObject("{}");
        identity.put("method", method);
        for (String field : new String[]{"sceneCode", "taskSceneCode", "taskType", "taskId",
                "bizKey", "bizkey", "bizSubType", "recordId", "groupId", "activityId", "awardType"}) {
            String text = value(args, field);
            if (!text.isEmpty()) identity.put(field, text);
        }
        for (String field : new String[]{"error", "resultCode", "retCode"}) {
            String text = value(result, field);
            if (!text.isEmpty()) identity.put(field, text);
        }
        String message = RpcRequestGuard.errorMessage(result).replaceAll("2088\\d{12}", "[UID]");
        identity.put("message", message);
        // 字段顺序固定的数组作为聚合键，避免 JSONObject 序列化顺序影响重启后的匹配。
        JSONArray keyParts = new JSONArray();
        java.util.List<String> names = new java.util.ArrayList<>();
        identity.keys().forEachRemaining(names::add);
        java.util.Collections.sort(names);
        for (String name : names) keyParts.put(name).put(identity.opt(name));
        String key = keyParts.toString();
        File file = fileFor(directory, now);
        if (file.length() > 4L * 1024 * 1024) throw new java.io.IOException("failure report exceeds size limit");
        JSONObject report = MyUtils.newJSONObject(file.isFile()
                ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "{}");
        JSONArray entries = report.optJSONArray("entries");
        if (entries == null) entries = new JSONArray();
        JSONObject entry = null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject candidate = entries.optJSONObject(i);
            if (candidate != null && key.equals(candidate.optString("key"))) { entry = candidate; break; }
        }
        String time = format(now, "yyyy-MM-dd HH:mm:ss.SSS");
        if (entry == null && entries.length() < 1000) {
            entry = identity.put("key", key).put("firstTime", time);
            entries.put(entry);
        }
        // ponytail: 每天最多 1000 类错误；达到上限明确计数，避免异常风暴使报告无限增长。
        if (entry == null) report.put("unlistedFailureCount", report.optLong("unlistedFailureCount") + 1);
        else entry.put("count", entry.optLong("count") + 1).put("lastTime", time);
        report.put("date", format(now, "yyyy-MM-dd")).put("timezone", "GMT+8")
                .put("totalFailures", report.optLong("totalFailures") + 1).put("entries", entries);
        AtomicConfigFile.write(report.toString(2), file);
    }
}
