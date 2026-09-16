package io.github.aw1y2z.sesame.rpc.intervallimit;

import java.io.File;
import java.nio.file.Files;
import java.util.TimeZone;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.diagnostics.RpcFailureJournal;

public class RpcFailureJournalCheck {
    static JSONObject read(File dir, long now) throws Exception {
        return MyUtils.newJSONObject(Files.readString(RpcFailureJournal.fileFor(dir, now).toPath()));
    }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        long now = java.time.Instant.parse("2026-09-16T15:59:59Z").toEpochMilli();
        File a = new File(FileUtil.root, "journal-A"), b = new File(FileUtil.root, "journal-B");
        String request = "[{\"taskId\":\"IP_chouchoule_juankuan\",\"sceneCode\":\"ANTFARM\","
                + "\"taskSceneCode\":\"ANTFARM_IP_DRAW_TASK\",\"userId\":\"2088123456789012\",\"token\":\"secret-value\"}]";
        JSONObject failure = MyUtils.newJSONObject("{\"success\":false,\"error\":0,\"resultCode\":102,\"memo\":\"服务器正在开小差，请稍后再试\"}");
        String method = "com.alipay.antfarm.receiveFarmTaskAward";
        RpcFailureJournal.record(a, method, request, failure, now);
        RpcFailureJournal.record(a, method, request, failure, now + 1);
        JSONObject report = read(a, now);
        assert report.optLong("totalFailures") == 2;
        JSONObject entry = report.optJSONArray("entries").optJSONObject(0);
        assert entry.optLong("count") == 2;
        assert entry.optString("resultCode").equals("102") && entry.optString("error").equals("0");
        assert entry.optString("firstTime").startsWith("2026-09-16 23:59:59");
        assert !report.toString().contains("secret-value") && !report.toString().contains("2088123456789012");
        RpcFailureJournal.record(a, method, request.replace("IP_chouchoule_juankuan", "another"), failure, now);
        assert read(a, now).optJSONArray("entries").length() == 2;
        RpcFailureJournal.record(a, method, request, MyUtils.newJSONObject("{\"success\":true}"), now);
        RpcFailureJournal.record(a, method, request, MyUtils.newJSONObject("{\"error\":\"RPC_SKIPPED\"}"), now);
        assert read(a, now).optLong("totalFailures") == 3;
        RpcFailureJournal.record(b, method, request, failure, now);
        assert read(b, now).optLong("totalFailures") == 1;
        RpcFailureJournal.record(a, method, request, failure, now + 1000);
        assert read(a, now + 1000).optString("date").equals("2026-09-17");
        assert read(a, now + 1000).optLong("totalFailures") == 1;
        // 通过生产 guard 验证未知庄园失败也记录，且响应归属请求发出时的账号。
        RuntimeInfo.account = "guard-A";
        File sentDirectory = FileUtil.getCurrentUserLogDirectory();
        RpcRequestGuard guard = new RpcRequestGuard(new RpcEntity(method, request));
        RuntimeInfo.account = "guard-B";
        guard.record(failure);
        assert read(sentDirectory, GuardCheck.now).optLong("totalFailures") == 1;
        assert !RpcFailureJournal.fileFor(FileUtil.getCurrentUserLogDirectory(), GuardCheck.now).exists();
        // 统计存储故障不能中断 RPC 失败处理。
        File blocked = new File(FileUtil.root, "blocked");
        blocked.getParentFile().mkdirs();
        Files.writeString(blocked.toPath(), "not a directory");
        FileUtil.root = blocked;
        new RpcRequestGuard(new RpcEntity(method, request)).record(failure);
        System.out.println("PASS failure reports: aggregation, fields, success/skip exclusion, GMT+8 rollover, account ownership and I/O isolation");
    }
}
