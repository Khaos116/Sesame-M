package io.github.aw1y2z.sesame.model.task.antOrchard;

import java.util.Calendar;
import java.util.List;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class AntOrchardVisitTask {
    private final String uid = UserIdMap.getCurrentUid();
    private final int day = date(), budget;
    private final String domain;
    private final RuntimeInfo runtime = RuntimeInfo.getInstance();
    private AntOrchardVisitTask(int budget, String domain) { this.budget = Math.min(budget, 30); this.domain = domain; }

    static void floatBall(JSONObject row, int budget) {
        if (budget <= 0) return;
        try { new AntOrchardVisitTask(budget, "float:" + text(row, "taskId")).runBall(row); }
        catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("AntOrchardVisitTask", "浮球任务停止，未确认回执保留", e); }
    }

    static void manualAward(JSONObject before, int budget) {
        if (budget <= 0 || before == null || !Boolean.TRUE.equals(before.opt("canCollect")) || !Boolean.TRUE.equals(before.opt("needManualReceive"))) return;
        try {
            AntOrchardVisitTask task = new AntOrchardVisitTask(budget, "manualAward");
            if (!task.available() || !task.reserve("manual")) return;
            JSONObject args = MyUtils.newJSONObject().put("diversionSource", "DEFAULT").put("requestType", "NORMAL").put("sceneCode", "ORCHARD")
                    .put("source", "ch_appcenter__chsub_9patch").put("version", "20260721.01").put("manualReceive", true);
            JSONObject ack = task.call("com.alipay.antorchard.receiveOrchardVisitAward", args);
            JSONObject after = MyUtils.newJSONObject(AntOrchardRpcCall.receiveOrchardVisitAward()); task.current();
            if (ack != null && number(ack, "manureCount") > 0 && ok(after)
                    && Boolean.FALSE.equals(after.opt("canCollect")) && Boolean.FALSE.equals(after.opt("needManualReceive"))) task.clear("农场访问奖励手动领取");
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("AntOrchardVisitTask", "手动访问奖励停止", e); }
    }

    private void runBall(JSONObject initial) throws Exception {
        if (!available() || !eligible(initial)) return;
        String id = text(initial, "taskId"), contract = contract(initial), app = app(initial);
        JSONObject row = fresh(id);
        if (!eligible(row) || !contract.equals(contract(row))) return;
        long seconds = duration(row);
        boolean draw = text(row.optJSONObject("taskDisplayConfig"), "targetUrl").contains("ANTORCHARD_DRAW_TIMES");
        if (!reserve("start")) return;
        if (draw) {
            if (call("com.alipay.antiepdrawprod.enterDrawActivityantorchard", MyUtils.newJSONObject().put("activityId", "").put("context", MyUtils.newJSONObject().put("appMode", "normal"))
                    .put("requestType", "RPC").put("sceneCode", "ANTORCHARD_DRAW_TIMES").put("source", "antorchard")) == null) return;
        }
        for (long left = seconds; left > 0;) {
            int piece = (int) Math.min(30, left);
            for (int i = 0; i < piece; i++) { TimeUtil.sleep(1000); current(); }
            row = fresh(id);
            if (!eligible(row) || !contract.equals(contract(row)) || !reserve("duration:" + (seconds - left))) return;
            if (call("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction", MyUtils.newJSONObject().put("gameAppId", app)
                    .put("playTime", piece).put("source", "lianyun_nc_flrw").put("statisticTag", "")) == null) return;
            left -= piece;
        }
        row = fresh(id);
        if (!eligible(row) || !contract.equals(contract(row)) || !reserve("finish")) return;
        JSONObject ack = call("com.alipay.antiep.finishTask", MyUtils.newJSONObject().put("outBizNo", uid + System.currentTimeMillis()).put("requestType", "NORMAL")
                .put("sceneCode", "ANTFARM_ORCHARD_TASK_V2").put("source", draw ? "ch_appid-no_app" : "hysjccl")
                .put("taskType", id).put("userId", uid).put("version", "0.1.2609041617.33"));
        row = fresh(id);
        if (ack != null && row != null && contract.equals(contract(row)) && List.of("FINISHED", "RECEIVED").contains(row.optString("taskStatus"))) clear("农场浮球任务");
    }

    private JSONObject fresh(String id) {
        current(); JSONObject root = MyUtils.newJSONObject(AntOrchardRpcCall.orchardListTask()); current();
        JSONArray rows = root.optJSONArray("taskList"); JSONObject found = null;
        if (!ok(root) || rows == null || rows.length() > 100) return null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) return null;
            if (id.equals(text(row, "taskId"))) { if (found != null) return null; found = row; }
        }
        return found;
    }

    static boolean eligible(JSONObject row) {
        if (row == null || !"TODO".equals(row.optString("taskStatus")) || !"VISIT".equals(row.optString("actionType"))
                || !"ANTFARM_ORCHARD_TASK_V2".equals(row.optString("sceneCode")) || text(row, "taskId").isEmpty()) return false;
        JSONObject display = row.optJSONObject("taskDisplayConfig");
        String title = text(display, "title");
        for (String token : new String[]{"开通", "开户", "转账", "付款", "支付", "购买", "充值", "订阅", "邀请", "分享", "注册", "余额宝", "理财", "保险"}) if (title.contains(token)) return false;
        long duration = duration(row);
        return !title.isEmpty() && duration >= 1 && duration <= 300 && !app(row).isEmpty();
    }

    private static String app(JSONObject row) {
        JSONObject display = row == null ? null : row.optJSONObject("taskDisplayConfig");
        try {
            Uri uri = Uri.parse(text(display, "targetUrl"));
            if (!"alipays".equals(uri.getScheme()) || !"platformapi".equals(uri.getHost()) || !"/startapp".equals(uri.getPath()) || uri.getQueryParameters("appId").size() != 1) return "";
            String app = uri.getQueryParameter("appId"); return app != null && app.matches("[0-9]{12,20}") ? app : "";
        } catch (RuntimeException e) { return ""; }
    }

    private static long duration(JSONObject row) {
        JSONObject display = row == null ? null : row.optJSONObject("taskDisplayConfig");
        return number(display == null ? null : display.optJSONObject("floatBallConfig"), "floatBallDuration");
    }

    private static String contract(JSONObject row) {
        JSONObject display = row.optJSONObject("taskDisplayConfig");
        return text(row, "taskId") + "|" + text(row, "actionType") + "|" + text(row, "sceneCode") + "|" + text(display, "targetUrl") + "|" + text(display, "title") + "|" + duration(row);
    }

    private static int date() {
        Calendar c = MyUtils.getInstance(); return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private void current() {
        TimeUtil.sleep(0);
        if (uid == null || uid.isEmpty() || !uid.equals(UserIdMap.getCurrentUid()) || day != date() || ApplicationHook.isOffline()) throw new TaskCancelledException();
    }

    private boolean available() { current(); return runtime.getString("orchardSjReceipt::" + domain).isEmpty(); }

    private boolean reserve(String stage) throws Exception {
        current(); String raw = runtime.getString("orchardSjAttempts"); JSONObject ledger = MyUtils.newJSONObject(raw);
        long used = raw.isEmpty() ? 0 : number(ledger, "count"), saved = raw.isEmpty() ? day : number(ledger, "day");
        if (used < 0 || saved < 20000101 || saved > day) return false;
        if (saved != day) used = 0;
        if (used >= budget || !runtime.putVerified("orchardSjAttempts", MyUtils.newJSONObject().put("day", day).put("count", used + 1).toString())) return false;
        current(); return runtime.putVerified("orchardSjReceipt::" + domain, MyUtils.newJSONObject().put("uid", uid).put("day", day).put("stage", stage).toString());
    }

    private JSONObject call(String method, JSONObject args) {
        current(); JSONObject root = MyUtils.newJSONObject(ApplicationHook.requestString(method, new JSONArray().put(args).toString(), 1, 0)); current();
        return ok(root) ? root : null;
    }

    private void clear(String label) { current(); if (runtime.putVerified("orchardSjReceipt::" + domain, null)) Log.farm(label + "：服务端状态回查确认"); }

    private static boolean ok(JSONObject root) { return root != null && !RpcRequestGuard.isFailure(root) && (Boolean.TRUE.equals(root.opt("success")) || "100".equals(root.optString("resultCode"))); }
    private static String text(JSONObject row, String key) { Object value = row == null ? null : row.opt(key); return value instanceof String ? (String) value : ""; }
    private static long number(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key); if (!(value instanceof Number)) return -1;
        try { long n = new java.math.BigDecimal(value.toString()).longValueExact(); return n >= 0 ? n : -1; } catch (ArithmeticException | NumberFormatException e) { return -1; }
    }
}
