package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** SJ legacy public activity contracts, qualified by the current account's live queries. */
final class LegacyCardRewards {
    private static final String HUNDRED = "alipay.ofpgrowth.hundredtimesdiscountcard.", HHK = "com.alipay.pcreditbfweb.promo.hhk.index";
    private static final String PRODUCT = "HUA_HUA_CARD_NORMAL_23Y06", SCENE = "HUA_HUA_CARD";
    private final OtherRequestGate gate;
    private final String uid;
    private final int day, budget;

    private LegacyCardRewards(OtherRequestGate gate, int budget) {
        this.gate = gate; this.budget = Math.min(budget, 20);
        uid = UserIdMap.getCurrentUid(); day = day();
    }

    static void run(OtherRequestGate gate, boolean sign, boolean rewards, boolean flip, boolean merge, int budget,
                    boolean selectedTasks, String targets, boolean selectedSignup) {
        run(gate, sign, rewards, flip, merge, budget, selectedTasks, targets, selectedSignup, false);
    }

    static void run(OtherRequestGate gate, boolean sign, boolean rewards, boolean flip, boolean merge, int budget,
                    boolean selectedTasks, String targets, boolean selectedSignup, boolean flowerTasks) {
        run(gate, sign, rewards, flip, merge, budget, selectedTasks, targets, selectedSignup, flowerTasks, false);
    }

    static void run(OtherRequestGate gate, boolean sign, boolean rewards, boolean flip, boolean merge, int budget,
                    boolean selectedTasks, String targets, boolean selectedSignup, boolean flowerTasks, boolean automaticTasks) {
        if (!(sign || rewards || flip || merge || selectedTasks || flowerTasks || automaticTasks) || (budget <= 0 && !selectedTasks)) return;
        LegacyCardRewards worker = new LegacyCardRewards(gate, budget);
        if (worker.uid == null || worker.uid.isEmpty()) return;
        try {
            if (budget > 0 && !worker.pending("hundred")) {
                if (sign) worker.sign();
                if (rewards && !worker.pending("hundred")) worker.rewards();
            }
            if (selectedTasks) worker.selectedTasks(targets, selectedSignup);
            if (budget > 0 && automaticTasks && !worker.pending("hundred")) worker.automaticTasks();
            if (budget > 0 && flowerTasks) worker.flowerTasks();
            if (budget > 0 && (flip || merge) && !worker.pending("huahua")) worker.flower(flip, merge);
        } catch (TaskCancelledException cancelled) { throw cancelled; }
        catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) { }
        catch (Exception error) { Log.err("LegacyCardRewards", "旧卡活动停止，本轮不重试", error); }
    }

    private static int day() {
        Calendar c = MyUtils.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private void current() {
        TimeUtil.sleep(0);
        if (!uid.equals(UserIdMap.getCurrentUid()) || day != day()) throw new TaskCancelledException();
    }

    private JSONObject call(String method, JSONObject args) throws Exception {
        current();
        boolean write = Set.of("alipay.promoprod.applet.trigger", HUNDRED + "task.receive", HHK + ".flopcard", HHK + ".merge",
                "com.alipay.pcreditbfweb.sdk.task.trigger", "com.alipay.pcreditbfweb.sdk.task.award").contains(method);
        String raw = gate.call("旧卡活动[" + method + "]", () -> {
            current();
            String body = new JSONArray().put(args).toString();
            return write ? ApplicationHook.requestString(method, body, 1, 0) : ApplicationHook.requestString(method, body);
        });
        current();
        JSONObject root = MyUtils.newJSONObject(raw);
        Object code = root.opt("errorCode");
        if (code != null && !JSONObject.NULL.equals(code) && !"0".equals(String.valueOf(code))) return null;
        return Boolean.TRUE.equals(root.opt("success")) && !RpcRequestGuard.isFailure(root) ? root : null;
    }

    private boolean pending(String domain) { return !RuntimeInfo.getInstance().getString("legacyCard::" + domain + "Receipt").isEmpty(); }

    private boolean reserve(String domain, String action) throws Exception {
        return reserve(domain, action, null);
    }

    private boolean reserve(String domain, String action, String acceptedReceipt) throws Exception {
        current();
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        String receipt = runtime.getString("legacyCard::" + domain + "Receipt");
        if (acceptedReceipt == null ? !receipt.isEmpty() : !acceptedReceipt.equals(receipt)) return false;
        int next = nextAttempt(runtime);
        if (next < 0) return false;
        if (!runtime.putVerified("legacyCardAttempts", MyUtils.newJSONObject().put("day", day).put("count", next).toString())) return false;
        current();
        return runtime.putVerified("legacyCard::" + domain + "Receipt", MyUtils.newJSONObject().put("action", action).put("uid", uid).put("day", day).toString());
    }

    private int nextAttempt(RuntimeInfo runtime) {
        String raw = runtime.getString("legacyCardAttempts");
        JSONObject ledger = raw.isEmpty() ? MyUtils.newJSONObject() : MyUtils.newJSONObject(raw);
        if (!raw.isEmpty() && (number(ledger, "day") <= 0 || number(ledger, "count") < 0 || number(ledger, "count") > 20)) return -1;
        long savedDay = raw.isEmpty() ? day : number(ledger, "day");
        if (savedDay < 20000101 || savedDay > day) return -1;
        try { java.time.LocalDate.of((int) (savedDay / 10000), (int) (savedDay / 100 % 100), (int) (savedDay % 100)); }
        catch (java.time.DateTimeException invalid) { return -1; }
        int count = !raw.isEmpty() && savedDay == day ? (int) number(ledger, "count") : 0;
        return count < 0 || count >= budget ? -1 : count + 1;
    }

    private boolean confirm(String domain) {
        current();
        return RuntimeInfo.getInstance().putVerified("legacyCard::" + domain + "Receipt", null);
    }

    private static String text(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static long number(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        String raw = String.valueOf(value);
        if (!raw.matches("[0-9]{1,9}")) return -1;
        try { return Long.parseLong(raw); } catch (NumberFormatException invalid) { return -1; }
    }

    private JSONObject todaySign() throws Exception {
        JSONObject root = call(HUNDRED + "signintask.query", MyUtils.newJSONObject());
        JSONObject data = root == null ? null : root.optJSONObject("data"), info = data == null ? null : data.optJSONObject("signInTaskInfo");
        JSONArray rows = info == null ? null : info.optJSONArray("taskDetailList");
        String center = text(info, "taskCenterId");
        if (center.isEmpty() || rows == null || rows.length() > 100) return null;
        JSONObject today = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("hasToday") instanceof Boolean)) return null;
            if (Boolean.TRUE.equals(row.opt("hasToday"))) {
                if (today != null || text(row, "taskId").isEmpty() || text(row, "status").isEmpty()) return null;
                today = MyUtils.newJSONObject(row.toString()).put("_center", center);
            }
        }
        return today;
    }

    private static boolean sameTask(JSONObject a, JSONObject b) {
        return a != null && b != null && text(a, "taskId").equals(text(b, "taskId"))
                && text(a, "_center").equals(text(b, "_center")) && text(a, "status").equals(text(b, "status"));
    }

    private void sign() throws Exception {
        if (DailyTask.skip("hundred::sign", "百次立减卡签到")) return;
        JSONObject before = todaySign();
        if (before == null) return;
        if ("RECEIVED".equals(text(before, "status"))) { DailyTask.done("hundred::sign"); return; }
        String status = text(before, "status"), id = text(before, "taskId");
        boolean send = Set.of("NOT_DONE", "TODO", "NONE_SIGNUP").contains(status);
        if (!send && !Set.of("TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD").contains(status)) return;
        if (!sameTask(before, todaySign())) return;
        if (send) {
            if (!reserve("hundred", "sign-send::" + id)) return;
            JSONObject ack = call("alipay.promoprod.applet.trigger", MyUtils.newJSONObject().put("appletId", id).put("taskCenId", text(before, "_center")).put("stageCode", "send"));
            JSONObject after = todaySign();
            if (ack == null || after == null || !id.equals(text(after, "taskId")) || !text(before, "_center").equals(text(after, "_center"))
                    || !Set.of("TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD", "HAS_COMPLETED", "COMPLETED", "RECEIVED").contains(text(after, "status")) || !confirm("hundred")) return;
            before = after;
        }
        if ("RECEIVED".equals(text(before, "status"))) { DailyTask.done("hundred::sign"); return; }
        if (!reserve("hundred", "sign-receive::" + id)) return;
        JSONObject ack = call(HUNDRED + "task.receive", MyUtils.newJSONObject().put("chInfo", "signInTask").put("taskId", id));
        JSONObject after = todaySign();
        if (ack != null && after != null && id.equals(text(after, "taskId")) && text(before, "_center").equals(text(after, "_center"))
                && "RECEIVED".equals(text(after, "status")) && confirm("hundred")) {
            DailyTask.done("hundred::sign");
            Log.other("百次立减卡🎁今日签到奖励状态回查已领取");
        }
    }

    private JSONArray taskList() throws Exception {
        JSONObject root = call(HUNDRED + "task.listquery", MyUtils.newJSONObject().put("extInfo", MyUtils.newJSONObject().put("needFilterTaskTypeList", new JSONArray().put("").put("")).put("taskId", "")));
        JSONObject data = root == null ? null : root.optJSONObject("data");
        JSONArray rows = data == null ? null : data.optJSONArray("taskList");
        if (rows == null || rows.length() > 100) return null;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (text(row, "taskId").isEmpty() || text(row, "taskCenId").isEmpty() || !seen.add(text(row, "taskId"))
                    || row.has("userId") && !uid.equals(text(row, "userId"))) return null;
        }
        return rows;
    }

    private static JSONObject task(JSONArray rows, String id) {
        for (int i = 0; rows != null && i < rows.length(); i++) if (id.equals(text(rows.optJSONObject(i), "taskId"))) return rows.optJSONObject(i);
        return null;
    }

    private static String taskStatus(JSONObject task) {
        if (task == null) return "";
        Object primary = task.opt("taskStatus"), secondary = task.opt("taskProcessStatus");
        if ((primary != null && !(primary instanceof String)) || (secondary != null && !(secondary instanceof String))) return "";
        String status = text(task, "taskStatus");
        String fallback = text(task, "taskProcessStatus");
        if (!status.isEmpty() && !fallback.isEmpty() && !status.equals(fallback)) return "";
        return status.isEmpty() ? fallback : status;
    }

    private static JSONArray selectedTargets(String raw) {
        if (raw == null || raw.length() > 16384 || raw.trim().isEmpty()) return null;
        try {
            org.json.JSONTokener parser = new org.json.JSONTokener(raw);
            JSONArray rows = new JSONArray(parser);
            if (parser.nextClean() != 0 || rows.length() > 20) return null;
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                String id = text(row, "taskId"), center = text(row, "taskCenId");
                if (row == null || row.length() != 2 || id.isEmpty() || center.isEmpty()
                        || id.length() > 256 || center.length() > 256 || !seen.add(id)) return null;
            }
            return rows;
        } catch (org.json.JSONException malformed) { return null; }
    }

    private static boolean riskySelectedTask(String value) {
        for (String denied : new String[]{"付款", "支付", "充值", "购买", "下单", "交易", "消费", "刷卡", "注册", "邀请", "助力", "开通", "办理",
                "绑卡", "绑定", "申卡", "办卡", "授权", "借款", "投保", "签约", "开户", "转账", "转入", "转出", "提现", "还款", "扣款", "申购", "赎回", "买入", "卖出"}) {
            if (value.contains(denied)) return true;
        }
        Set<String> denied = Set.of("PAY", "PAYMENT", "PURCHASE", "BUY", "ORDER", "TRADE", "TRANSACTION", "REGISTER", "REGISTRATION",
                "SIGNUP", "INVITE", "INVITATION", "ASSIST", "OPEN", "BIND", "AUTHORIZE", "AUTHORIZATION", "TRANSFER", "WITHDRAW", "BORROW", "CONTRACT",
                "CONSUME", "CONSUMPTION", "RECHARGE", "REPAY", "REPAYMENT", "SUBSCRIBE", "ACTIVATE", "ACTIVATION");
        for (String token : value.toUpperCase(java.util.Locale.ROOT).split("[^A-Z]+")) if (denied.contains(token)) return true;
        return false;
    }

    private static boolean optionalObject(JSONObject row, String key) {
        Object value = row.opt(key);
        return value == null || value instanceof JSONObject;
    }

    private static JSONObject selectedMorph(JSONObject ext) {
        Object detail = ext == null ? null : ext.opt("TASK_MORPHO_DETAIL");
        if (!(detail instanceof JSONObject) && !(detail instanceof String)) return null;
        String raw = detail.toString(), upper = raw.toUpperCase(java.util.Locale.ROOT);
        if (raw.length() > 16384 || upper.contains("TRANSFORMER") || upper.contains("COMMON_EVENT_TASK")) return null;
        if (detail instanceof JSONObject) return (JSONObject) detail;
        JSONObject wrapped = MyUtils.newJSONObject("{\"detail\":" + raw + "}");
        return wrapped.length() == 1 ? wrapped.optJSONObject("detail") : null;
    }

    private static long selectedSeconds(JSONObject row, boolean signup) {
        if (row == null || !(Set.of("NOT_DONE", "TODO").contains(taskStatus(row)) || signup && "NONE_SIGNUP".equals(taskStatus(row)))) return -1;
        return selectedDuration(row, signup);
    }

    private static long selectedDuration(JSONObject row, boolean signupAllowed) {
        if (row == null) return -1;
        String raw = row.toString();
        if (raw.length() > 16384 || !optionalObject(row, "taskExtProps") || !optionalObject(row, "taskMaterial")) return -1;
        JSONObject ext = row.optJSONObject("taskExtProps"), material = row.optJSONObject("taskMaterial"), morph = null;
        if (ext != null) {
            Object detail = ext.opt("TASK_MORPHO_DETAIL");
            morph = selectedMorph(ext);
            if (detail != null && morph == null) return -1;
        }
        String declaredType = "";
        for (JSONObject info : new JSONObject[]{row, ext, morph, material}) {
            if (info == null) continue;
            for (String key : new String[]{"taskType", "TASK_TYPE", "taskTitle", "title", "action", "actionType"}) {
                Object value = info.opt(key);
                if (value != null && riskySelectedTask(value.toString())) return -1;
            }
            Object title = info.opt("taskTitle");
            if (title != null && !(title instanceof String)) return -1;
            Object signup = info.opt("needSignUp"), signupType = info.opt("signupType");
            if (signup != null && !Boolean.FALSE.equals(signup) && !Boolean.TRUE.equals(signup)
                    && !(signup instanceof String && Set.of("true", "false").contains(((String) signup).trim().toLowerCase(java.util.Locale.ROOT)))) return -1;
            if (!signupAllowed && (Boolean.TRUE.equals(signup) || "true".equalsIgnoreCase(text(info, "needSignUp")))) return -1;
            if (signupType != null && !(signupType instanceof String)) return -1;
            if (!signupAllowed && "NO_AUTO".equalsIgnoreCase(text(info, "signupType"))) return -1;
            for (String key : new String[]{"taskType", "TASK_TYPE"}) {
                Object type = info.opt(key);
                if (type != null && !(type instanceof String)) return -1;
                String name = text(info, key);
                if ("TRANSFORMER".equalsIgnoreCase(name) || "COMMON_EVENT_TASK".equalsIgnoreCase(name)) return -1;
                if (!name.isEmpty()) {
                    if (!declaredType.isEmpty() && !declaredType.equalsIgnoreCase(name)) return -1;
                    declaredType = name;
                }
            }
        }
        long seconds = number(ext, "browseTime");
        if (ext != null && ext.has("browseTime") && seconds < 0) return -1;
        if (seconds <= 0) seconds = number(material, "browseTime");
        return seconds > 0 && seconds <= 300 ? seconds : -1;
    }

    private void waitSelected(long seconds) {
        long end = System.nanoTime() + seconds * 1000000000L;
        while (true) {
            current();
            long remaining = end - System.nanoTime();
            if (remaining <= 0) return;
            TimeUtil.sleep(Math.min(1000L, (remaining + 999999L) / 1000000L));
            if (Thread.currentThread().isInterrupted()) throw new TaskCancelledException();
        }
    }

    private static String selectedContext(JSONObject row, boolean ignoreSignupFlag) throws Exception {
        JSONObject snapshot = MyUtils.newJSONObject(), ext = row.optJSONObject("taskExtProps"), material = row.optJSONObject("taskMaterial"), morph = null;
        morph = selectedMorph(ext);
        JSONObject[] sources = {row, ext, morph, material};
        String[] names = {"task", "ext", "morph", "material"};
        for (int i = 0; i < sources.length; i++) {
            JSONObject fields = MyUtils.newJSONObject();
            if (sources[i] != null) for (String key : new String[]{"taskType", "TASK_TYPE", "taskTitle", "title", "taskSource", "needSignUp", "signupType",
                    "browseTime", "actionUrl", "targetUrl", "url", "link", "jumpUrl", "action", "actionType", "appId"}) {
                if (ignoreSignupFlag && "needSignUp".equals(key)) continue;
                Object value = sources[i].opt(key);
                if (value != null) fields.put(key, value);
            }
            snapshot.put(names[i], fields);
        }
        return snapshot.toString();
    }

    private boolean sameSelected(JSONObject row, JSONObject before, String center, long seconds, boolean signup) throws Exception {
        return row != null && center.equals(text(row, "taskCenId")) && taskStatus(before).equals(taskStatus(row))
                && seconds == selectedSeconds(row, signup) && selectedContext(before, false).equals(selectedContext(row, false));
    }

    private static boolean signupFlag(JSONObject row) {
        JSONObject ext = row == null ? null : row.optJSONObject("taskExtProps");
        for (JSONObject info : new JSONObject[]{row, ext, selectedMorph(ext), row == null ? null : row.optJSONObject("taskMaterial")}) {
            if (info != null && (Boolean.TRUE.equals(info.opt("needSignUp")) || "true".equalsIgnoreCase(text(info, "needSignUp")))) return true;
        }
        return false;
    }

    private static boolean needsSignup(JSONObject row) {
        JSONObject ext = row == null ? null : row.optJSONObject("taskExtProps");
        return "NONE_SIGNUP".equals(taskStatus(row)) || ext != null && (Boolean.TRUE.equals(ext.opt("needSignUp"))
                || "true".equalsIgnoreCase(text(ext, "needSignUp")) || "NO_AUTO".equalsIgnoreCase(text(ext, "signupType")));
    }

    private String signupKey(String id, String center) { return "legacyCard::hundredSignup::" + center + "::" + id; }

    private boolean registeredTask(JSONObject row, String id, String center) throws Exception {
        JSONObject saved = MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(signupKey(id, center)));
        return Set.of("NOT_DONE", "TODO").contains(text(saved, "status")) && !signupFlag(row)
                && number(saved, "day") == day && uid.equals(text(saved, "uid")) && id.equals(text(saved, "taskId"))
                && center.equals(text(saved, "taskCenId")) && taskStatus(row).equals(text(saved, "status"))
                && selectedContext(row, false).equals(text(saved, "context"));
    }

    private static String selectedTitle(JSONObject row) {
        JSONObject ext = row.optJSONObject("taskExtProps");
        String title = text(ext, "taskTitle");
        if (!title.isEmpty()) return title;
        JSONObject morph = selectedMorph(ext);
        title = text(morph, "taskTitle");
        return title.isEmpty() ? text(row, "taskTitle") : title;
    }

    private void selectedDirectory(boolean signupAllowed) throws Exception {
        JSONArray rows = taskList();
        int shown = 0;
        for (int i = 0; rows != null && i < rows.length() && shown < 20; i++) {
            JSONObject row = rows.optJSONObject(i);
            long seconds = selectedSeconds(row, signupAllowed && needsSignup(row) && ("NONE_SIGNUP".equals(taskStatus(row)) || signupFlag(row)));
            String title = row == null ? "" : selectedTitle(row), id = text(row, "taskId"), center = text(row, "taskCenId");
            if (seconds <= 0 || title.isEmpty() || id.length() > 256 || center.length() > 256
                    || id.contains("://") || center.contains("://") || id.contains(uid) || center.contains(uid)) continue;
            title = title.replaceAll("(?i)[a-z][a-z0-9+.-]*://\\S+", "[链接]").replace(uid, "[账号]");
            if (title.length() > 120) title = title.substring(0, 120);
            current();
            Log.record("百次立减卡📋当前任务候选（请在活动页面确认要求后选择），目标JSON："
                    + new JSONArray().put(MyUtils.newJSONObject().put("taskId", id).put("taskCenId", center))
                    + "，任务说明：" + MyUtils.newJSONObject().put("taskTitle", title).put("browseTime", seconds).put("needsSignup", needsSignup(row)));
            shown++;
        }
    }

    private void selectedTasks(String raw, boolean signupAllowed) throws Exception {
        selectedTasks(raw, signupAllowed, false);
    }

    private void automaticTasks() throws Exception {
        JSONArray rows = taskList(), selected = new JSONArray();
        for (int i = 0; rows != null && i < rows.length() && selected.length() < 20; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (selectedDuration(row, true) <= 0 || selectedTitle(row).isEmpty()
                    || !Set.of("NOT_DONE", "TODO", "NONE_SIGNUP", "TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD").contains(taskStatus(row))) continue;
            selected.put(MyUtils.newJSONObject().put("taskId", text(row, "taskId")).put("taskCenId", text(row, "taskCenId")));
        }
        if (selected.length() > 0) selectedTasks(selected.toString(), true, true);
    }

    private void selectedTasks(String raw, boolean signupAllowed, boolean sourceSignup) throws Exception {
        JSONArray selected = selectedTargets(raw);
        if (selected == null) return;
        if (selected.length() == 0 || budget <= 0 || pending("hundred")) {
            selectedDirectory(signupAllowed);
            return;
        }
        current();
        if (nextAttempt(RuntimeInfo.getInstance()) < 0) return;
        JSONArray rows = taskList();
        for (int i = 0; rows != null && i < selected.length(); i++) {
            current();
            if (nextAttempt(RuntimeInfo.getInstance()) < 0) return;
            JSONObject target = selected.optJSONObject(i);
            String id = text(target, "taskId"), center = text(target, "taskCenId");
            JSONObject before = task(rows, id);
            if (before != null && Set.of("TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD").contains(taskStatus(before))) {
                long seconds = selectedDuration(before, true);
                if (!center.equals(text(before, "taskCenId")) || seconds <= 0 || !sourceSignup && signupFlag(before)) continue;
                if (!receiveSelected(before, id, center, seconds, true)) return;
                rows = taskList();
                continue;
            }
            boolean registered = signupAllowed && before != null && registeredTask(before, id, center);
            boolean signup = signupAllowed && !registered && needsSignup(before) && ("NONE_SIGNUP".equals(taskStatus(before)) || signupFlag(before) || sourceSignup);
            long seconds = selectedSeconds(before, signup || registered);
            if (seconds <= 0 || !center.equals(text(before, "taskCenId"))) continue;
            if (!sameSelected(task(taskList(), id), before, center, seconds, signup || registered)) return;
            String acceptedSignup = null;
            if (signup) {
                if (!reserve("hundred", "selected-signup::" + id)) return;
                String sentReceipt = RuntimeInfo.getInstance().getString("legacyCard::hundredReceipt");
                JSONObject ack = call("alipay.promoprod.applet.trigger", MyUtils.newJSONObject().put("appletId", id).put("taskCenId", center).put("stageCode", "signup"));
                JSONObject after = task(taskList(), id);
                if (sourceSignup) {
                    if (ack == null || after == null || !center.equals(text(after, "taskCenId"))
                            || !(taskStatus(before).equals(taskStatus(after)) || Set.of("NOT_DONE", "TODO").contains(taskStatus(after)))
                            || selectedDuration(after, true) != seconds || !selectedContext(before, true).equals(selectedContext(after, true))
                            || !sentReceipt.equals(RuntimeInfo.getInstance().getString("legacyCard::hundredReceipt"))) return;
                    acceptedSignup = sentReceipt;
                    before = after; registered = true;
                    Log.record("百次立减卡🎟自动任务报名请求已受理，本轮按原流程继续；后续发送和领奖仍回查任务状态");
                } else {
                    if (ack == null || after == null || !center.equals(text(after, "taskCenId")) || signupFlag(after)
                            || !Set.of("NOT_DONE", "TODO").contains(taskStatus(after)) || selectedDuration(after, true) != seconds
                            || !("NONE_SIGNUP".equals(taskStatus(before)) || signupFlag(before))
                            || !selectedContext(before, true).equals(selectedContext(after, true))) return;
                    JSONObject saved = MyUtils.newJSONObject().put("day", day).put("uid", uid).put("taskId", id).put("taskCenId", center)
                            .put("status", taskStatus(after)).put("context", selectedContext(after, false));
                    current();
                    if (!RuntimeInfo.getInstance().putVerified(signupKey(id, center), saved.toString()) || !confirm("hundred")) return;
                    before = after; registered = true;
                }
                if (nextAttempt(RuntimeInfo.getInstance()) < 0) return;
            }
            waitSelected(seconds);
            if (!sameSelected(task(taskList(), id), before, center, seconds, registered)) return;
            if (!reserve("hundred", "selected-send::" + id, acceptedSignup)) return;
            JSONObject ack = call("alipay.promoprod.applet.trigger", MyUtils.newJSONObject().put("appletId", id).put("taskCenId", center).put("stageCode", "send"));
            JSONObject after = task(taskList(), id);
            if (ack == null || after == null || !center.equals(text(after, "taskCenId"))
                    || !Set.of("TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD").contains(taskStatus(after))
                    || selectedDuration(after, registered) != seconds || !selectedContext(before, false).equals(selectedContext(after, false)) || !confirm("hundred")) return;
            if (!receiveSelected(after, id, center, seconds, registered)) return;
            rows = taskList();
        }
    }

    private boolean receiveSelected(JSONObject before, String id, String center, long seconds, boolean registered) throws Exception {
        JSONObject fresh = task(taskList(), id);
        if (fresh == null || !center.equals(text(fresh, "taskCenId")) || !taskStatus(before).equals(taskStatus(fresh))
                || selectedDuration(fresh, registered) != seconds || !selectedContext(before, false).equals(selectedContext(fresh, false))) return false;
        if (!reserve("hundred", "selected-receive::" + id)) return false;
        JSONObject ack = call(HUNDRED + "task.receive", MyUtils.newJSONObject().put("taskId", id));
        JSONObject after = task(taskList(), id);
        if (ack == null || after == null || !center.equals(text(after, "taskCenId")) || !"RECEIVED".equals(taskStatus(after))
                || selectedDuration(after, registered) != seconds || !selectedContext(before, false).equals(selectedContext(after, false)) || !confirm("hundred")) return false;
        Log.other("百次立减卡🎁选定任务奖励状态回查已领取");
        return true;
    }

    private void rewards() throws Exception {
        JSONArray rows = taskList();
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject before = rows.optJSONObject(i);
            if (!Set.of("TO_RECEIVE", "WAIT_RECEIVE", "WAIT_AWARD").contains(taskStatus(before))) continue;
            String id = text(before, "taskId"), center = text(before, "taskCenId");
            JSONObject fresh = task(taskList(), id);
            if (fresh == null || !center.equals(text(fresh, "taskCenId")) || !taskStatus(before).equals(taskStatus(fresh))) return;
            if (!reserve("hundred", "reward::" + id)) return;
            JSONObject ack = call(HUNDRED + "task.receive", MyUtils.newJSONObject().put("taskId", id));
            JSONObject after = task(taskList(), id);
            if (ack == null || after == null || !center.equals(text(after, "taskCenId")) || !"RECEIVED".equals(taskStatus(after)) || !confirm("hundred")) return;
            Log.other("百次立减卡🎁已完成任务奖励状态回查已领取");
        }
    }

    private JSONObject baseArgs() throws Exception { return MyUtils.newJSONObject().put("productCode", PRODUCT).put("sceneCode", SCENE); }

    private JSONArray flowerTaskList() throws Exception {
        JSONObject query = call("com.alipay.pcreditbfweb.sdk.task.queryV2", MyUtils.newJSONObject().put("requestFrom", "pccp").put("scene", SCENE));
        JSONArray rows = query == null ? null : query.optJSONArray("data");
        return rows != null && rows.length() <= 100 ? rows : null;
    }

    private boolean flowerCandidate(JSONObject row) {
        String id = text(row, "taskId"), center = text(row, "taskCenId");
        JSONObject show = row == null ? null : row.optJSONObject("taskShowInfo");
        if (id.isEmpty() || center.isEmpty() || id.length() > 256 || center.length() > 256 || text(row, "taskStatus").isEmpty()
                || text(show, "title").isEmpty() || "SCENE_TASK".equals(text(row, "taskSource"))
                || row.has("taskSource") && !(row.opt("taskSource") instanceof String)
                || row.has("userId") && !uid.equals(text(row, "userId"))) return false;
        for (String key : new String[]{"taskShowInfo", "taskBaseInfo", "taskMaterial", "taskExtProps"}) {
            if (!optionalObject(row, key)) return false;
            JSONObject info = row.optJSONObject(key);
            for (String name : new String[]{"title", "taskTitle", "taskType", "TASK_TYPE", "action", "actionType"}) {
                Object value = info == null ? null : info.opt(name);
                if (value != null && (!(value instanceof String) || riskySelectedTask((String) value))) return false;
            }
        }
        for (String key : new String[]{"taskTitle", "taskType", "action", "actionType"}) {
            Object value = row.opt(key);
            if (value != null && (!(value instanceof String) || riskySelectedTask((String) value))) return false;
        }
        return true;
    }

    private void flowerTasks() throws Exception {
        JSONArray rows = flowerTaskList();
        Set<String> seen = new HashSet<>();
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (text(row, "taskId").isEmpty() || !seen.add(text(row, "taskId"))) return;
        }
        for (int i = 0; rows != null && i < rows.length(); i++) {
            JSONObject before = rows.optJSONObject(i);
            if (!flowerCandidate(before)) continue;
            String id = text(before, "taskId"), center = text(before, "taskCenId");
            String domain = "huahuaTask::" + new JSONArray().put(center).put(id), key = "legacyCard::" + domain + "Receipt";
            if (pending(domain)) continue;
            JSONArray fresh = flowerTaskList();
            int matches = 0;
            for (int j = 0; fresh != null && j < fresh.length(); j++) {
                JSONObject candidate = fresh.optJSONObject(j);
                if (id.equals(text(candidate, "taskId"))) {
                    if (!before.toString().equals(candidate.toString()) || !flowerCandidate(candidate)) return;
                    matches++;
                }
            }
            if (matches != 1) return;
            String accepted = null;
            for (String stage : new String[]{"signup", "send", "award"}) {
                if ("signup".equals(stage) && "SIGNUP_COMPLETE".equals(text(before, "taskStatus"))) continue;
                if (!reserve(domain, stage, accepted)) return;
                String sentReceipt = RuntimeInfo.getInstance().getString(key);
                JSONObject args = "award".equals(stage)
                        ? MyUtils.newJSONObject().put("taskCenIds", new JSONArray().put(center)).put("taskIds", new JSONArray().put(id))
                        : MyUtils.newJSONObject().put("appletId", id).put("taskCenId", center).put("outBizNo", id + System.currentTimeMillis() / 60000L)
                                .put("retryFlag", true).put("stageCode", stage);
                JSONObject ack = call("com.alipay.pcreditbfweb.sdk.task." + ("award".equals(stage) ? "award" : "trigger"), args);
                if (ack == null || !sentReceipt.equals(RuntimeInfo.getInstance().getString(key))) return;
                // Only this round's positive ACK permits the next request; persisted receipts never authorize a replay.
                accepted = sentReceipt;
                if ("send".equals(stage)) { TimeUtil.sleep(600L); current(); }
                if ("award".equals(stage)) {
                    JSONObject receipt = MyUtils.newJSONObject(sentReceipt).put("accepted", true);
                    JSONObject data = ack.optJSONObject("data");
                    JSONArray result = data == null ? null : data.optJSONArray("resultData"), reported = new JSONArray();
                    for (int j = 0; result != null && j < Math.min(result.length(), 100); j++) {
                        JSONObject item = result.optJSONObject(j);
                        JSONArray orders = item == null ? null : item.optJSONArray("prizeSendOrderList");
                        String name = text(orders == null ? null : orders.optJSONObject(0), "prizeName");
                        if (!name.isEmpty() && name.length() <= 120) reported.put(name);
                    }
                    receipt.put("reportedPrizes", reported);
                    current();
                    if (!RuntimeInfo.getInstance().putVerified(key, receipt.toString())) return;
                    Log.record("花花卡🎟任务报名、上报及领奖请求已按原流程受理；奖励以活动页面为准，保留回执防止重复请求");
                }
            }
        }
    }

    private String certId() throws Exception {
        JSONArray rows = flowerTaskList();
        if (rows == null) return "";
        String cert = "";
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i), info = row == null ? null : row.optJSONObject("taskBaseInfo");
            JSONArray prizes = info == null ? null : info.optJSONArray("prizeInfos");
            for (int j = 0; prizes != null && j < prizes.length(); j++) {
                JSONObject prize = prizes.optJSONObject(j), ext = prize == null ? null : prize.optJSONObject("extInfo");
                String id = text(ext, "CERT_TEMPLATE_ID");
                if (id.isEmpty()) continue;
                if (!cert.isEmpty() && !cert.equals(id)) return "";
                cert = id;
            }
        }
        return cert;
    }

    private JSONObject index(String cert) throws Exception {
        JSONObject root = call(HHK, baseArgs().put("certId", cert).put("productCodeFlop", "CARD_HUA_HUA_CARD_23Y06").put("sceneCodeFlop", "CARD_HUA_HUA_CARD"));
        return root == null ? null : root.optJSONObject("data");
    }

    private static JSONObject unopened(JSONObject home) throws Exception {
        if (number(home, "remainingTimes") <= 0) return null;
        JSONArray rows = home.optJSONArray("cardPrizes");
        if (rows == null || rows.length() > 100) return null;
        Set<Long> seen = new HashSet<>(); JSONObject result = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i), position = row == null ? null : row.optJSONObject("position");
            long index = number(position, "index");
            if (index < 0 || !(row.opt("isOpen") instanceof Boolean) || !(row.opt("isNewPageBegin") instanceof Boolean) || !seen.add(index)) return null;
            if (result == null && Boolean.FALSE.equals(row.opt("isOpen"))) result = MyUtils.newJSONObject(row.toString());
        }
        return result;
    }

    private void flower(boolean flip, boolean merge) throws Exception {
        String cert = certId(); if (cert.isEmpty() || !cert.equals(certId())) return;
        JSONObject before = index(cert);
        if (flip) {
            JSONObject choice = before == null ? null : unopened(before);
            JSONObject fresh = index(cert), currentChoice = fresh == null ? null : unopened(fresh);
            if (choice != null && currentChoice != null && choice.toString().equals(currentChoice.toString()) && number(before, "remainingTimes") == number(fresh, "remainingTimes")) {
                long at = number(choice.optJSONObject("position"), "index"), count = number(before, "remainingTimes");
                if (!reserve("huahua", "flop::" + cert + "::" + at + "::" + count)) return;
                JSONObject ack = call(HHK + ".flopcard", MyUtils.newJSONObject().put("productCode", "CARD_HUA_HUA_CARD_23Y06").put("sceneCode", "CARD_HUA_HUA_CARD")
                        .put("lineIndex", at).put("isNewPageBegin", choice.opt("isNewPageBegin")));
                JSONObject after = index(cert);
                JSONArray afterCards = after == null ? null : after.optJSONArray("cardPrizes");
                int matched = 0;
                for (int i = 0; afterCards != null && i < afterCards.length(); i++) {
                    JSONObject card = afterCards.optJSONObject(i);
                    if (card != null && at == number(card.optJSONObject("position"), "index") && Boolean.TRUE.equals(card.opt("isOpen"))) matched++;
                }
                if (ack == null || number(after, "remainingTimes") != count - 1 || matched != 1 || !confirm("huahua")) return;
                Log.other("花花卡🎁免费翻卡次数扣减回查成功，奖品到账以活动页面为准");
                before = after;
            }
        }
        if (merge && !pending("huahua")) merge(cert, before);
    }

    private static boolean fragments(JSONObject home, int expected) {
        JSONArray rows = home == null ? null : home.optJSONArray("fragments");
        if (rows == null || rows.length() < 2 || rows.length() > 32) return false;
        for (int i = 0; i < rows.length(); i++) if (number(rows.optJSONObject(i), "number") != expected) return false;
        return true;
    }

    private void merge(String cert, JSONObject before) throws Exception {
        if (!fragments(before, 1)) return;
        JSONObject fresh = index(cert);
        if (!fragments(fresh, 1) || !before.optJSONArray("fragments").toString().equals(fresh.optJSONArray("fragments").toString())) return;
        if (!reserve("huahua", "merge::" + cert)) return;
        JSONObject ack = call(HHK + ".merge", baseArgs()), data = ack == null ? null : ack.optJSONObject("data");
        String camp = text(data, "campId"), biz = text(data, "bizNo");
        if (camp.isEmpty() || biz.isEmpty()) return;
        JSONObject history = call("com.alipay.pcreditbfweb.drpc.pageQueryPrizeSendOrderLite", MyUtils.newJSONObject().put("args", MyUtils.newJSONObject()
                .put("campIds", new JSONArray().put(camp)).put("outBizNo", biz).put("pageNum", 1).put("perPageSize", 10)));
        JSONObject result = history == null ? null : history.optJSONObject("data"); result = result == null ? null : result.optJSONObject("result");
        result = result == null ? null : result.optJSONObject("resultData");
        JSONArray orders = result == null ? null : result.optJSONArray("dataList");
        JSONObject after = index(cert);
        if (!fragments(after, 0) || after.optJSONArray("fragments").length() != before.optJSONArray("fragments").length()
                || orders == null || orders.length() != 1 || text(orders.optJSONObject(0), "prizeName").isEmpty() || !confirm("huahua")) return;
        Log.other("花花卡🎁合卡碎片及指定订单奖励记录回查成功");
    }
}
