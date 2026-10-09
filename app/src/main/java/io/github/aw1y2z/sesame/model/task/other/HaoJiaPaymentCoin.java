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
    private String lastTaskProgress = "";

    private HaoJiaPaymentCoin(OtherRequestGate gate, String uid, String today, int budget) {
        this.gate = gate; this.uid = uid; this.today = today; this.budget = Math.min(budget, 50);
    }

    static void run(OtherRequestGate gate, boolean sign, boolean browse, boolean rewards, int budget) {
        String uid = UserIdMap.getCurrentUid();
        Log.record("好家缴费金：开始，签到=" + sign + "，浏览=" + browse + "，领奖=" + rewards + "，每日操作预算=" + budget);
        if (gate == null || uid == null || uid.isEmpty() || budget <= 0 || !sign && !browse && !rewards) return;
        try { new HaoJiaPaymentCoin(gate, uid, date(), budget).work(sign, browse, rewards); }
        catch (TaskCancelledException e) { throw e; }
        catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) { }
        catch (Exception e) { Log.record("好家缴费金流程异常（" + e.getClass().getSimpleName() + "），停止本活动，结果未确认"); }
    }

    private void work(boolean sign, boolean browse, boolean rewards) throws Exception {
        BigDecimal startingBalance = balance();
        if (startingBalance == null || !reconcile()) return;
        if (sign && !signIn()) return;
        Map<String, JSONObject> rows = tasks();
        if (rows == null) return;
        int completed = 0, newlyRewarded = 0;
        for (String code : rows.keySet()) {
            JSONObject row = task(code);
            if (row == null) { Log.record("好家缴费金：任务回查缺失，停止本活动"); return; }
            String title = text(row.optJSONObject("displayInfo"), "taskName").replaceAll("[\\r\\n\\t]", " ");
            String label = "好家缴费金｜" + (title.isEmpty() ? "未命名" : title.substring(0, Math.min(title.length(), 80)));
            String state = switch (text(row, "taskStatus")) {
                case "init" -> "待领取";
                case "claim" -> "待浏览";
                case "finish" -> "已完成";
                default -> "状态未知";
            };
            Object reward = row.opt("rewardStatus");
            String rewardState = "success".equals(reward) ? "已领奖"
                    : !row.has("rewardStatus") || "init".equals(reward) || "".equals(reward) ? "未领奖" : "奖励状态未知";
            Log.record(("finish".equals(text(row, "taskStatus")) ? "☑️ " : "📋 ") + label + "：" + state + " · " + rewardState
                    + ("finish".equals(text(row, "taskStatus")) ? "（此前完成）" : browse(row) ? " · 可免费浏览" + browseSeconds(row) + "秒" : " · 需人工或不支持自动执行"));
            if (browse && browse(row) && "init".equals(row.optString("taskStatus"))) {
                JSONObject ack = invoke(TASK + "apply", MyUtils.newJSONObject().put("code", code), true,
                        () -> reserve("apply", code, "", null, null));
                if (ack == null) return;
                JSONObject content = ack == null ? null : ack.optJSONObject("content");
                JSONObject claimed = content == null ? null : content.optJSONObject("claimedTask");
                String record = claimed == null ? "" : text(claimed, "recordNo");
                row = task(code);
                if (!id(record) || row == null || !record.equals(text(row, "recordNo")) || !"claim".equals(row.optString("taskStatus"))) {
                    Log.record(label + "：领取回查未匹配有效记录或claim状态，保留待核对记录"); return;
                }
                if (!clear()) return;
            }
            if (browse && browse(row) && "claim".equals(row.optString("taskStatus"))) {
                String record = text(row, "recordNo");
                long seconds = browseSeconds(row);
                if (!id(record)) { Log.record(label + "：缺少有效recordNo，未上报"); continue; }
                Log.record("⏳ " + label + "：浏览" + seconds + "秒");
                for (long waited = 0; waited < seconds; waited++) { TimeUtil.sleep(1000); if (!current()) return; }
                JSONObject fresh = task(code);
                if (fresh == null || !browse(fresh) || !record.equals(text(fresh, "recordNo"))
                        || !"claim".equals(fresh.optString("taskStatus")) || seconds != browseSeconds(fresh)) {
                    Log.record(label + "：等待后任务记录、状态或时长变化，本轮未上报"); return;
                }
                JSONObject ack = invoke(TASK + "process", MyUtils.newJSONObject().put("code", code).put("recordNo", record), true,
                        () -> reserve("process", code, record, null, null));
                if (ack == null) return;
                row = task(code);
                if (ack == null || row == null || !record.equals(text(row, "recordNo")) || !"finish".equals(row.optString("taskStatus"))) {
                    Log.record(label + "：上报未确认，响应通过=" + (ack != null) + "，同记录回查完成="
                            + (row != null && record.equals(text(row, "recordNo")) && "finish".equals(row.optString("taskStatus"))) + "，保留待核对记录"); return;
                }
                if (!clear()) return;
                completed++;
                Object rewardAfter = row.opt("rewardStatus");
                String message = "✅ " + label + "：浏览完成 · " + ("success".equals(rewardAfter) ? "已领奖"
                        : !row.has("rewardStatus") || "init".equals(rewardAfter) || "".equals(rewardAfter) ? "待领奖" : "奖励状态待确认") + "（已回查）";
                Log.other(message);
                Log.record(message);
            }
            if (rewards && "finish".equals(row.optString("taskStatus"))
                    && Set.of("", "init").contains(row.optString("rewardStatus")) && id(text(row, "recordNo"))) {
                String record = text(row, "recordNo");
                BigDecimal before = balance();
                JSONObject fresh = task(code);
                if (before == null || fresh == null || !record.equals(text(fresh, "recordNo"))
                        || !"finish".equals(fresh.optString("taskStatus")) || !Set.of("", "init").contains(fresh.optString("rewardStatus"))) {
                    Log.record(label + "：领奖前余额或同记录状态未通过，本轮未领奖"); return;
                }
                JSONObject ack = invoke(TASK + "get_reward", MyUtils.newJSONObject().put("code", code).put("recordNo", record), false,
                        () -> reserve("reward", code, record, before, null));
                if (ack == null) return;
                row = task(code);
                BigDecimal after = balance();
                if (ack == null || row == null || !record.equals(text(row, "recordNo")) || !"finish".equals(row.optString("taskStatus"))
                        || !"success".equals(row.optString("rewardStatus")) || after == null || after.compareTo(before) <= 0) {
                    Log.record(label + "：领奖未确认，响应通过=" + (ack != null) + "，同记录奖励完成="
                            + (row != null && record.equals(text(row, "recordNo")) && "success".equals(row.optString("rewardStatus")))
                            + "，余额增加=" + (after != null && after.compareTo(before) > 0) + "，保留待核对记录"); return;
                }
                if (!clear()) return;
                newlyRewarded++;
                String message = "✅ " + label + "：领奖成功 · 余额=" + before.toPlainString() + "→" + after.toPlainString() + "（已回查）";
                Log.other(message);
                Log.record(message);
            }
        }
        BigDecimal endingBalance = balance();
        Log.record("📊 好家缴费金本轮结果：本轮新完成浏览任务=" + completed + "项，本轮新领奖=" + newlyRewarded + "项\n💰 余额=" + startingBalance.toPlainString() + "→"
                + (endingBalance == null ? "未取得有效回查" : endingBalance.toPlainString()));
    }

    private boolean signIn() throws Exception {
        JSONObject order = signOrder();
        if (order == null) return false;
        JSONObject template = order.optJSONObject("playSignInTemplateInfo");
        JSONArray cycles = order.optJSONArray("playSignInCycleInstanceInfoList");
        String code = template == null ? "" : text(template, "code");
        Log.record("好家缴费金签到资格：模板有效=" + id(code) + "，周期数=" + (cycles == null ? -1 : cycles.length()));
        if (!id(code) || order.has("playSignInCycleInstanceInfoList") && !order.isNull("playSignInCycleInstanceInfoList") && cycles == null
                || cycles != null && cycles.length() > 1) return false;
        JSONObject cycle = cycles == null || cycles.length() == 0 ? null : cycles.optJSONObject(0);
        if (cycles != null && cycles.length() == 1 && cycle == null) return false;
        String latest = cycle == null ? "" : cycle.optString("latestSignInDate");
        long count = cycle == null ? 0 : integral(cycle, "accumulativeSignInCount");
        if (today.equals(latest)) { Log.record("好家缴费金：今日已签到，累计签到=" + count); return true; }
        if (count < 0 || !latest.isEmpty() && (!latest.matches("[0-9]{8}") || latest.compareTo(today) >= 0)) return false;
        BigDecimal before = balance();
        if (before == null) return false;
        JSONObject ack = invoke(SIGN, MyUtils.newJSONObject().put("code", code), false,
                () -> reserve("sign", code, "", before, count));
        if (ack == null) return false;
        JSONObject after = signOrder();
        BigDecimal amount = balance();
        if (ack == null || !signed(after, code, today, count) || amount == null || amount.compareTo(before) < 0) {
            Log.record("好家缴费金签到未确认：响应通过=" + (ack != null) + "，同周期今日记录通过=" + signed(after, code, today, count)
                    + "，余额校验通过=" + (amount != null && amount.compareTo(before) >= 0) + "，保留待核对记录"); return false;
        }
        if (!clear()) return false;
        String message = "✅ 好家缴费金｜签到成功 · 余额=" + before.toPlainString() + "→" + amount.toPlainString() + "（已回查）";
        Log.other(message);
        Log.record(message);
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
        if (!current() || used < 0 || used >= budget || Status.hasFlagToday(flag) || !runtime.getString(RECEIPT).isEmpty()) {
            Log.record("好家缴费金[" + stage + "]：未提交，操作预算=" + used + "/" + budget
                    + "，当日已尝试=" + Status.hasFlagToday(flag) + "，待核对记录=" + !runtime.getString(RECEIPT).isEmpty());
            return false;
        }
        JSONObject receipt = MyUtils.newJSONObject().put("stage", stage).put("code", code).put("recordNo", record).put("date", today);
        if (amount != null) receipt.put("balance", amount.toPlainString());
        if (count != null) receipt.put("count", count);
        if (!runtime.putVerified(ATTEMPTS, MyUtils.newJSONObject().put("date", today).put("used", used + 1).toString())) return false;
        if (!current() || !runtime.putVerified(RECEIPT, receipt.toString())) return false;
        TimeUtil.sleep(0);
        if (!current()) return false;
        Status.flagToday(flag);
        Status.setIntFlagToday("other::haojiaCoinAttempts", (int) used + 1);
        Log.record("好家缴费金[" + stage + "]：预留每日操作额度=" + (used + 1) + "/" + budget);
        return true;
    }

    private JSONObject invoke(String component, JSONObject params, boolean source) throws Exception {
        return invoke(component, params, source, null);
    }

    private JSONObject invoke(String component, JSONObject params, boolean source, java.util.concurrent.Callable<Boolean> attempt) throws Exception {
        TimeUtil.sleep(0);
        if (!current()) return null;
        JSONObject args = MyUtils.newJSONObject().put("operationParamIdentify", PROGRAM).put("components", MyUtils.newJSONObject().put(component, params));
        if (source) args.put("source", "jiaofei");
        boolean write = !component.equals(SIGN + "_recall") && !component.equals(TASK + "query");
        boolean[] invoked = {false};
        String raw;
        try {
            raw = gate.call("好家缴费金组件[" + component + "]", () -> {
                String body = new JSONArray().put(args).toString();
                if (attempt != null && !attempt.call()) return null;
                invoked[0] = true;
                return write ? ApplicationHook.requestString("alipay.imasp.program.programInvoke", body, 1, 0)
                        : ApplicationHook.requestString("alipay.imasp.program.programInvoke", body);
            });
        } catch (Exception failure) {
            Log.record("好家缴费金[" + component + "]：" + (invoked[0] ? "已进入RPC，结果未确认" : "未调用RPC，发送前停止")
                    + "（" + failure.getClass().getSimpleName() + "）");
            throw failure;
        }
        if (!invoked[0]) { Log.record("好家缴费金[" + component + "]：未调用RPC，额度或回执未获准"); return null; }
        JSONObject root = MyUtils.newJSONObject(raw);
        JSONObject components = root.optJSONObject("components"), value = components == null ? null : components.optJSONObject(component);
        if (write || !success(root) || !success(value)) Log.record("好家缴费金[" + component + "]：" + (write ? "单次写调用" : "查询调用") + "，根成功校验=" + success(root)
                + "，组件成功校验=" + success(value) + "，resultCode=" + SjActivityTasks.responseField(root, "resultCode")
                + "，error=" + SjActivityTasks.responseField(root, "error"));
        return current() && success(root) && success(value) ? value : null;
    }

    private JSONObject signOrder() throws Exception {
        JSONObject recall = invoke(SIGN + "_recall", MyUtils.newJSONObject(), false);
        JSONObject content = recall == null ? null : recall.optJSONObject("content");
        JSONArray rows = content == null ? null : content.optJSONArray("playSignInOrderInfoList");
        Log.record("好家缴费金签到查询：订单数=" + (rows == null ? -1 : rows.length()));
        return rows != null && rows.length() == 1 ? rows.optJSONObject(0) : null;
    }

    private Map<String, JSONObject> tasks() throws Exception {
        JSONObject query = invoke(TASK + "query", MyUtils.newJSONObject(), false);
        JSONObject content = query == null ? null : query.optJSONObject("content");
        JSONArray rows = content == null ? null : content.optJSONArray("playTaskOrderInfoList");
        if (rows == null || rows.length() > 50) {
            Log.record("好家缴费金：任务目录缺失、类型无效或超过50项，本轮进度未确认");
            return null;
        }
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String code = row == null ? "" : text(row, "code");
            if (!id(code) || result.put(code, row) != null) {
                Log.record("好家缴费金：任务目录包含无效或重复编号，本轮进度未确认");
                return null;
            }
        }
        int completed = 0, rewarded = 0, awaitingReward = 0, free = 0, manual = 0, unknown = 0, unknownReward = 0;
        for (JSONObject row : result.values()) {
            String state = text(row, "taskStatus");
            if ("finish".equals(state)) {
                completed++;
                Object reward = row.opt("rewardStatus");
                if ("success".equals(reward)) rewarded++;
                else if (!row.has("rewardStatus") || "".equals(reward) || "init".equals(reward)) awaitingReward++;
                else unknownReward++;
            } else if (Set.of("init", "claim").contains(state)) {
                if (browse(row)) free++;
                else manual++;
            } else unknown++;
        }
        String progress = "目录总数=" + result.size() + "项，已完成=" + completed + "项，已领奖=" + rewarded
                + "项，待领奖=" + awaitingReward + "项\n剩余未完成=" + (free + manual) + "项，剩余可免费浏览=" + free + "项，需人工或不支持=" + manual
                + "项，任务状态未知=" + unknown + "项，奖励状态未知=" + unknownReward + "项";
        if (!progress.equals(lastTaskProgress)) {
            Log.record("📊 好家缴费金进度：" + progress);
            lastTaskProgress = progress;
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
        BigDecimal amount = current() && success(root) && data != null ? decimal(data, "balance") : null;
        Log.record("好家缴费金余额查询：根成功校验=" + success(root) + "，余额=" + (amount == null ? "缺失或无效" : amount.toPlainString())
                + "，resultCode=" + SjActivityTasks.responseField(root, "resultCode"));
        return amount;
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
        long seconds = browseSeconds(row);
        String name = text(display, "taskName");
        String actionName = name.replace("支付宝", "");
        // ponytail: browse waits above one hour stay manual; supported waits run in full with cancellation checks.
        return row != null && "userPush".equals(row.optString("advanceType")) && seconds > 0 && seconds <= 3600
                && (name.contains("浏览") || name.contains("逛"))
                && Set.of("开通", "办理", "支付", "购买", "理财", "保险", "黄金", "贷款", "借款", "转入", "充值").stream().noneMatch(actionName::contains);
    }

    private static long browseSeconds(JSONObject row) {
        JSONObject display = row == null ? null : row.optJSONObject("displayInfo");
        // SJ h5/a.p uses 15 seconds when browseTime is absent; explicit invalid values remain rejected.
        return display == null ? -1 : display.has("browseTime") ? integral(display, "browseTime") : 15;
    }
}
