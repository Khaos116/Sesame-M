package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class HaoJiaPaymentCoin {
    private static final String PROGRAM = "independent_component_program2026062903615094";
    private static final String SIGN = "independent_component_sign_in_03386004_independent_component_sign_in";
    private static final String TASK = "independent_component_task_reward_v2_03385041_independent_component_task_reward_";
    private static final String RECEIPT = "otherHaoJiaPaymentCoinReceipt";
    private static final String ATTEMPTS = "otherHaoJiaPaymentCoinAttempts";
    private final OtherRequestGate gate;
    private final String uid, today;
    private final int budget;

    private HaoJiaPaymentCoin(OtherRequestGate gate, String uid, String today, int budget) {
        this.gate = gate; this.uid = uid; this.today = today; this.budget = Math.min(budget, 50);
    }

    static void run(OtherRequestGate gate, boolean sign, boolean browse, boolean rewards, int budget) {
        String uid = UserIdMap.getCurrentUid();
        if (gate == null || uid == null || uid.isEmpty() || budget <= 0 || !sign && !browse && !rewards) return;
        try { new HaoJiaPaymentCoin(gate, uid, date(), budget).work(sign, browse, rewards); }
        catch (TaskCancelledException e) { throw e; }
        catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) { }
        catch (Exception e) { Log.err("HaoJiaPaymentCoin", "好家缴费金执行失败", e); }
    }

    private void work(boolean sign, boolean browse, boolean rewards) throws Exception {
        if (balance() == null || !reconcile()) return;
        if (sign && !signIn()) return;
        Map<String, JSONObject> rows = tasks();
        if (rows == null) return;
        for (String code : rows.keySet()) {
            JSONObject row = task(code);
            if (row == null) return;
            if (browse && browse(row) && "init".equals(row.optString("taskStatus"))) {
                if (!reserve("apply", code, "", null, null)) return;
                JSONObject ack = invoke(TASK + "apply", MyUtils.newJSONObject().put("code", code), true);
                JSONObject content = ack == null ? null : ack.optJSONObject("content");
                JSONObject claimed = content == null ? null : content.optJSONObject("claimedTask");
                String record = claimed == null ? "" : text(claimed, "recordNo");
                row = task(code);
                if (!id(record) || row == null || !record.equals(text(row, "recordNo")) || !"claim".equals(row.optString("taskStatus"))) return;
                if (!clear()) return;
            }
            if (browse && browse(row) && "claim".equals(row.optString("taskStatus"))) {
                String record = text(row, "recordNo");
                long seconds = integral(row.optJSONObject("displayInfo"), "browseTime");
                if (!id(record)) continue;
                for (long waited = 0; waited < seconds; waited++) { TimeUtil.sleep(1000); if (!current()) return; }
                JSONObject fresh = task(code);
                if (fresh == null || !browse(fresh) || !record.equals(text(fresh, "recordNo"))
                        || !"claim".equals(fresh.optString("taskStatus")) || seconds != integral(fresh.optJSONObject("displayInfo"), "browseTime")) return;
                if (!reserve("process", code, record, null, null)) return;
                JSONObject ack = invoke(TASK + "process", MyUtils.newJSONObject().put("code", code).put("recordNo", record), true);
                row = task(code);
                if (ack == null || row == null || !record.equals(text(row, "recordNo")) || !"finish".equals(row.optString("taskStatus"))) return;
                if (!clear()) return;
                Log.other("好家缴费金⚡浏览任务状态回查成功");
            }
            if (rewards && "finish".equals(row.optString("taskStatus"))
                    && Set.of("", "init").contains(row.optString("rewardStatus")) && id(text(row, "recordNo"))) {
                String record = text(row, "recordNo");
                BigDecimal before = balance();
                JSONObject fresh = task(code);
                if (before == null || fresh == null || !record.equals(text(fresh, "recordNo"))
                        || !"finish".equals(fresh.optString("taskStatus")) || !Set.of("", "init").contains(fresh.optString("rewardStatus"))) return;
                if (!reserve("reward", code, record, before, null)) return;
                JSONObject ack = invoke(TASK + "get_reward", MyUtils.newJSONObject().put("code", code).put("recordNo", record), false);
                row = task(code);
                BigDecimal after = balance();
                if (ack == null || row == null || !record.equals(text(row, "recordNo")) || !"finish".equals(row.optString("taskStatus"))
                        || !"success".equals(row.optString("rewardStatus")) || after == null || after.compareTo(before) <= 0) return;
                if (!clear()) return;
                Log.other("好家缴费金⚡奖励状态及余额回查成功");
            }
        }
    }

    private boolean signIn() throws Exception {
        JSONObject order = signOrder();
        if (order == null) return false;
        JSONObject template = order.optJSONObject("playSignInTemplateInfo");
        JSONArray cycles = order.optJSONArray("playSignInCycleInstanceInfoList");
        String code = template == null ? "" : text(template, "code");
        if (!id(code) || cycles == null || cycles.length() > 1) return false;
        JSONObject cycle = cycles.length() == 0 ? null : cycles.optJSONObject(0);
        String latest = cycle == null ? "" : cycle.optString("latestSignInDate");
        long count = cycle == null ? 0 : integral(cycle, "accumulativeSignInCount");
        if (today.equals(latest)) return true;
        if (count < 0 || !latest.isEmpty() && (!latest.matches("[0-9]{8}") || latest.compareTo(today) >= 0)) return false;
        BigDecimal before = balance();
        if (before == null || !reserve("sign", code, "", before, count)) return false;
        JSONObject ack = invoke(SIGN, MyUtils.newJSONObject().put("code", code), false);
        JSONObject after = signOrder();
        BigDecimal amount = balance();
        if (ack == null || !signed(after, code, today, count) || amount == null || amount.compareTo(before) < 0) return false;
        if (!clear()) return false;
        Log.other("好家缴费金⚡签到记录及余额回查成功");
        return true;
    }

    private boolean reconcile() throws Exception {
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        String raw = runtime.getString(RECEIPT);
        if (raw.isEmpty()) return true;
        JSONObject saved = MyUtils.newJSONObject(raw);
        String stage = saved.optString("stage"), code = text(saved, "code"), record = text(saved, "recordNo");
        if (!id(code)) return false;
        boolean confirmed = false;
        if ("sign".equals(stage)) {
            BigDecimal before = decimal(saved, "balance"), after = balance();
            confirmed = signed(signOrder(), code, saved.optString("date"), integral(saved, "count"))
                    && before != null && after != null && after.compareTo(before) >= 0;
        } else {
            JSONObject row = task(code);
            // Apply has no record until its response; a later day's instance cannot prove this attempt.
            if (row != null && "apply".equals(stage)) confirmed = today.equals(text(saved, "date"))
                    && id(text(row, "recordNo")) && "claim".equals(row.optString("taskStatus"));
            if (row != null && id(record) && record.equals(text(row, "recordNo"))) {
                if ("process".equals(stage)) confirmed = "finish".equals(row.optString("taskStatus"));
                if ("reward".equals(stage)) {
                    BigDecimal before = decimal(saved, "balance"), after = balance();
                    confirmed = "finish".equals(row.optString("taskStatus")) && "success".equals(row.optString("rewardStatus"))
                            && before != null && after != null && after.compareTo(before) > 0;
                }
            }
        }
        if (!confirmed || !current()) { Log.record("好家缴费金旧回执未确认，保留待查"); return false; }
        return clear();
    }

    private boolean reserve(String stage, String code, String record, BigDecimal amount, Long count) throws Exception {
        TimeUtil.sleep(0);
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        String raw = runtime.getString(ATTEMPTS);
        JSONObject ledger = raw.isEmpty() ? MyUtils.newJSONObject() : MyUtils.newJSONObject(raw);
        long persisted = raw.isEmpty() ? 0 : integral(ledger, "used");
        if (!raw.isEmpty() && (persisted < 0 || !text(ledger, "date").matches("[0-9]{8}") || text(ledger, "date").compareTo(today) > 0)) return false;
        if (!today.equals(text(ledger, "date"))) persisted = 0;
        int statusUsed = Status.getIntFlagToday("other::haojiaCoinAttempts");
        if (statusUsed < 0) return false;
        long used = Math.max(persisted, statusUsed);
        String flag = "other::haojiaCoin::" + stage + "::" + code + "::" + record;
        if (!current() || used < 0 || used >= budget || Status.hasFlagToday(flag) || !runtime.getString(RECEIPT).isEmpty()) return false;
        JSONObject receipt = MyUtils.newJSONObject().put("stage", stage).put("code", code).put("recordNo", record).put("date", today);
        if (amount != null) receipt.put("balance", amount.toPlainString());
        if (count != null) receipt.put("count", count);
        if (!runtime.putVerified(ATTEMPTS, MyUtils.newJSONObject().put("date", today).put("used", used + 1).toString())) return false;
        if (!current() || !runtime.putVerified(RECEIPT, receipt.toString())) return false;
        TimeUtil.sleep(0);
        if (!current()) return false;
        Status.flagToday(flag);
        Status.setIntFlagToday("other::haojiaCoinAttempts", (int) used + 1);
        return true;
    }

    private JSONObject invoke(String component, JSONObject params, boolean source) throws Exception {
        TimeUtil.sleep(0);
        if (!current()) return null;
        JSONObject args = MyUtils.newJSONObject().put("operationParamIdentify", PROGRAM).put("components", MyUtils.newJSONObject().put(component, params));
        if (source) args.put("source", "jiaofei");
        JSONObject root = MyUtils.newJSONObject(gate.call("好家缴费金组件", () -> ApplicationHook.requestString("alipay.imasp.program.programInvoke", new JSONArray().put(args).toString())));
        JSONObject components = root.optJSONObject("components"), value = components == null ? null : components.optJSONObject(component);
        return current() && success(root) && success(value) ? value : null;
    }

    private JSONObject signOrder() throws Exception {
        JSONObject recall = invoke(SIGN + "_recall", MyUtils.newJSONObject(), false);
        JSONObject content = recall == null ? null : recall.optJSONObject("content");
        JSONArray rows = content == null ? null : content.optJSONArray("playSignInOrderInfoList");
        return rows != null && rows.length() == 1 ? rows.optJSONObject(0) : null;
    }

    private Map<String, JSONObject> tasks() throws Exception {
        JSONObject query = invoke(TASK + "query", MyUtils.newJSONObject(), false);
        JSONObject content = query == null ? null : query.optJSONObject("content");
        JSONArray rows = content == null ? null : content.optJSONArray("playTaskOrderInfoList");
        if (rows == null || rows.length() > 50) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String code = row == null ? "" : text(row, "code");
            if (!id(code) || result.put(code, row) != null) return null;
        }
        return result;
    }

    private JSONObject task(String code) throws Exception { Map<String, JSONObject> rows = tasks(); return rows == null ? null : rows.get(code); }
    private BigDecimal balance() throws Exception {
        TimeUtil.sleep(0);
        if (!current()) return null;
        JSONObject args = MyUtils.newJSONObject().put("userId", uid);
        JSONObject root = MyUtils.newJSONObject(gate.call("好家缴费金余额", () -> ApplicationHook.requestString(
                "com.alipay.industrydoraemon.biz.paymentcoin.rpc.queryPaymentCoinBalance", new JSONArray().put(args).toString())));
        JSONObject data = root.optJSONObject("data");
        return current() && success(root) && data != null ? decimal(data, "balance") : null;
    }
    private boolean clear() { return current() && RuntimeInfo.getInstance().putVerified(RECEIPT, null); }
    private boolean current() { return uid.equals(UserIdMap.getCurrentUid()) && today.equals(date()); }
    private static String date() { Calendar c = MyUtils.getInstance(); return String.format(Locale.ROOT, "%04d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)); }
    private static boolean id(String value) { return value != null && !value.isEmpty() && value.length() <= 256; }
    private static String text(JSONObject root, String key) { Object value = root == null ? null : root.opt(key); return value instanceof String ? (String) value : ""; }
    private static boolean success(JSONObject root) {
        if (root == null || root.has("success") && !Boolean.TRUE.equals(root.opt("success")) || root.has("isSuccess") && !Boolean.TRUE.equals(root.opt("isSuccess"))) return false;
        if (root.has("error") && !Set.of("", "0").contains(root.optString("error")) || root.has("retCode") && !"0".equals(root.optString("retCode"))) return false;
        if (root.has("resultCode") && !Set.of("SUCCESS", "100", "200").contains(root.optString("resultCode"))) return false;
        return Boolean.TRUE.equals(root.opt("success")) || Boolean.TRUE.equals(root.opt("isSuccess"));
    }
    private static BigDecimal decimal(JSONObject root, String key) {
        Object value = root == null ? null : root.opt(key);
        if (!(value instanceof String) && !(value instanceof Number)) return null;
        try { BigDecimal n = new BigDecimal(value.toString()); return n.signum() >= 0 ? n : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static long integral(JSONObject root, String key) {
        BigDecimal value = decimal(root, key);
        try { return value == null ? -1 : value.longValueExact(); } catch (ArithmeticException e) { return -1; }
    }
    private static boolean signed(JSONObject order, String code, String date, long count) {
        JSONObject template = order == null ? null : order.optJSONObject("playSignInTemplateInfo");
        JSONArray cycles = order == null ? null : order.optJSONArray("playSignInCycleInstanceInfoList");
        JSONObject cycle = cycles == null || cycles.length() != 1 ? null : cycles.optJSONObject(0);
        return count >= 0 && template != null && code.equals(text(template, "code")) && cycle != null
                && date.equals(cycle.optString("latestSignInDate")) && integral(cycle, "accumulativeSignInCount") == count + 1;
    }
    private static boolean browse(JSONObject row) {
        JSONObject display = row == null ? null : row.optJSONObject("displayInfo");
        long seconds = integral(display, "browseTime");
        String name = display == null ? "" : display.optString("taskName");
        // ponytail: browse waits above one hour stay manual; supported waits run in full with cancellation checks.
        return row != null && "userPush".equals(row.optString("advanceType")) && seconds > 0 && seconds <= 3600
                && (name.contains("浏览") || name.contains("逛"))
                && Set.of("开通", "办理", "支付", "购买", "理财", "保险", "黄金", "贷款", "借款", "转入", "充值").stream().noneMatch(name::contains);
    }
}
