package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.IdAndName;
import io.github.aw1y2z.sesame.entity.OtherEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class YebVouchers {
    private static final String RECEIPT = "memberYebVoucherReceipt", CAMPAIGN = "CP152735172|PZ1144215101";
    private final String uid;
    private final int day;
    private final RuntimeInfo runtime;

    private YebVouchers(String uid) { this.uid = uid; day = day(); runtime = RuntimeInfo.getInstance(); }

    static List<IdAndName> getExchangeOptions() {
        return List.of(new OtherEntity(CAMPAIGN, "余额宝体验金公开兑换活动（可独立开启兑换券直接激活）"));
    }

    static void run(boolean convertAll, int voucherBudget, Set<String> campaigns, int amountBudget, Set<String> trialIds, int activationBudget,
                    boolean activateAllOwned, boolean activateNewVoucher) {
        boolean exchange = campaigns != null && campaigns.contains(CAMPAIGN) && amountBudget > 0;
        boolean activate = (activateAllOwned || trialIds != null && !trialIds.isEmpty()) && activationBudget > 0;
        if ((!convertAll || voucherBudget <= 0) && !exchange && !activate) return;
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return;
        try {
            YebVouchers work = new YebVouchers(uid);
            if (!work.runtime.getString(RECEIPT).isEmpty()) {
                if (activate) work.recoverActivation(trialIds, activateAllOwned);
                if (!work.runtime.getString(RECEIPT).isEmpty()) Log.record("余额宝券旧回执待核实，停止转换、兑换及激活");
                return;
            }
            if (convertAll && voucherBudget > 0) work.convert(Math.min(voucherBudget, 1000));
            if (!work.current() || !work.runtime.getString(RECEIPT).isEmpty()) return;
            if (exchange) work.exchange(amountBudget, activateNewVoucher, Math.min(activationBudget, 1000));
            if (!work.current() || !work.runtime.getString(RECEIPT).isEmpty()) return;
            if (activate) work.activate(trialIds, Math.min(activationBudget, 1000), activateAllOwned);
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.record("余额宝券流程失败，保留已预留的尝试及未确认回执"); }
    }

    private boolean convert(int budget) throws Exception {
        JSONObject before = vouchers();
        long count = number(before, "totalCount");
        if (before == null) return false;
        if (count == 0) return true;
        JSONObject fresh = vouchers();
        if (fresh == null || count != number(fresh, "totalCount") || !before.optJSONArray("certVoucherInfoList").toString()
                .equals(fresh.optJSONArray("certVoucherInfoList").toString())) return false;
        JSONObject receipt = MyUtils.newJSONObject().put("stage", "convert").put("count", count).put("day", day)
                .put("inventoryHash", hash(before.optJSONArray("certVoucherInfoList").toString()));
        if (!reserve("convert", BigDecimal.valueOf(count), budget, "all", receipt, true)) return false;
        JSONObject ack = rpc("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert", MyUtils.newJSONObject().put("convertType", "all").put("isShowExchangeModal", true));
        JSONArray results = ack == null ? null : ack.optJSONArray("convertResults");
        if (results == null || results.length() == 0 || results.length() > 1000) return false;
        for (int i = 0; i < results.length(); i++) {
            JSONObject row = results.optJSONObject(i), value = row == null ? null : row.optJSONObject("value");
            if (row == null || !"fulfilled".equalsIgnoreCase(text(row, "status")) || value == null || !Boolean.TRUE.equals(value.opt("success")) || !success(value)) return false;
        }
        long delay = ack.has("delayRefreshTime") ? number(ack, "delayRefreshTime") : 0;
        if (delay < 0 || delay > 300000) {
            // Do not shorten an unknown/long protocol delay or query before it. Leave the receipt for review.
            if (current()) runtime.putVerified(RECEIPT, (delay < 0 ? receipt.put("refreshDelayInvalid", true) : receipt.put("delayRefreshTime", delay)).toString());
            return false;
        }
        for (long waited = 0; waited < delay;) {
            long part = Math.min(1000, delay - waited); TimeUtil.sleep(part); waited += part;
            if (!current()) return false;
        }
        JSONObject after = vouchers();
        long remaining = number(after, "totalCount");
        if (after == null || remaining < 0 || remaining >= count || !current() || !runtime.putVerified(RECEIPT, null)) return false;
        Log.other(remaining == 0 ? "余额宝体验金券💵逐项转换成功，券库存已清零：" + count
                : "余额宝体验金券💵全量转换请求逐项成功，库存已减少：" + count + "→" + remaining + "，剩余券本日不再提交");
        return true;
    }

    private boolean exchange(int budget, boolean activateVoucher, int activationBudget) throws Exception {
        JSONObject before = balance(), fresh = balance();
        BigDecimal amount = decimal(before, "balance"), threshold = threshold(before);
        if (amount == null || threshold == null || amount.signum() <= 0 || amount.compareTo(threshold) < 0 || fresh == null
                || amount.compareTo(decimalOrNegative(fresh, "balance")) != 0 || threshold.compareTo(thresholdOrNegative(fresh)) != 0) return false;
        String order = uid + System.currentTimeMillis();
        JSONObject receipt = MyUtils.newJSONObject().put("stage", "exchange").put("campId", "CP152735172").put("prizeId", "PZ1144215101")
                .put("bizOrderNo", order).put("balance", amount.toPlainString()).put("exchangeAmount", amount.toPlainString()).put("day", day);
        if (!reserve("exchange", amount, budget, CAMPAIGN, receipt, true)) return false;
        JSONObject ack = rpc("com.alipay.yebscenebff.expgold.index.exchange", MyUtils.newJSONObject().put("bizOrderNo", order)
                .put("campId", "CP152735172").put("prizeId", "PZ1144215101").put("exchangeAmount", amount.toPlainString()));
        JSONObject result = ack == null ? null : ack.optJSONObject("result"); String equity = text(result, "equityNo");
        if (!id(equity) || !current() || !runtime.putVerified(RECEIPT, receipt.put("equityNo", equity).toString())) return false;
        JSONObject after = balance(); BigDecimal remaining = decimal(after, "balance");
        // The source exchanges the entire live balance; its exact expected remainder is zero.
        if (remaining == null || remaining.signum() != 0 || !current()) return false;
        // Voucher activation uses equityNo plus equityType=voucher; it is not a trialId.
        JSONObject grant = MyUtils.newJSONObject(receipt.toString()).put("balanceAfter", remaining.toPlainString()).put("state", "EXCHANGE_CONFIRMED_ACTIVATION_UNVERIFIED");
        if (!runtime.putVerified("memberYebVoucherGrant::" + hash(equity), grant.toString()) || !current() || !runtime.putVerified(RECEIPT, null)) return false;
        Log.other("余额宝体验金💵兑换余额扣减及凭证回查确认");
        if (activateVoucher && activationBudget > 0) activateVoucher(equity, grant, activationBudget);
        return true;
    }

    private void activateVoucher(String equity, JSONObject grant, int budget) throws Exception {
        JSONObject receipt = MyUtils.newJSONObject(grant.toString()).put("stage", "activateVoucher").put("state", "SUBMITTED_UNCONFIRMED");
        if (!reserve("activate", BigDecimal.ONE, budget, "voucher::" + equity, receipt, false)) return;
        JSONObject ack = rpc("alipay.yebprod.promo.yebTrial.active", MyUtils.newJSONObject().put("couponId", equity)
                .put("type", "YEB_TRIAL").put("equityType", "voucher"));
        BigDecimal amount = decimal(ack == null ? null : ack.optJSONObject("amount"), "amount");
        String confirm = text(ack, "confirmDate"), profit = text(ack, "profitDate");
        if (amount == null || amount.signum() <= 0 || !plannedDate(confirm) || !plannedDate(profit) || !current()) return;
        receipt.put("activationAmount", amount.toPlainString()).put("confirmDate", confirm).put("profitDate", profit)
                .put("state", "ACTIVATION_ACKNOWLEDGED_TERMINAL_UNVERIFIED");
        if (!runtime.putVerified(RECEIPT, receipt.toString()) || !current()
                || !runtime.putVerified("memberYebVoucherGrant::" + hash(equity), receipt.toString())) return;
        Log.record("余额宝兑换券💵激活请求已受理，返回金额" + amount.toPlainString() + "元；终态尚无独立回查，不记收益到账，保留回执不重发");
    }

    private static boolean plannedDate(String value) {
        if (value.isEmpty() || value.length() > 64 || value.trim().isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    private void activate(Set<String> selected, int budget, boolean allOwned) throws Exception {
        if (allOwned) {
            Map<String, String> initial = assets(), fresh = assets();
            if (initial == null || !initial.equals(fresh)) return;
            selected = new LinkedHashSet<>();
            for (Map.Entry<String, String> entry : initial.entrySet()) if (!"A".equals(entry.getValue())) {
                selected.add(entry.getKey());
                if (selected.size() >= Math.min(budget, 100)) break;
            }
        }
        if (selected == null || selected.size() > 100) return;
        for (String trial : selected) {
            if (!id(trial)) continue;
            Map<String, String> before = assets(), fresh = assets();
            String status = before == null ? null : before.get(trial);
            // Current SJ yebTrialAsset uses status != A for activation and A as terminal; no invented pending enum.
            if (status == null || "A".equals(status) || fresh == null || !status.equals(fresh.get(trial))) continue;
            JSONObject receipt = MyUtils.newJSONObject().put("stage", "activate").put("trialId", trial).put("beforeStatus", status).put("day", day);
            if (!reserve("activate", BigDecimal.ONE, budget, trial, receipt, false)) return;
            JSONObject ack = rpc("alipay.yebprod.promo.yebTrial.active", MyUtils.newJSONObject().put("couponId", trial).put("type", "YEB_TRIAL"));
            Map<String, String> after = assets();
            if (ack == null || after == null || !"A".equals(after.get(trial)) || !current()) return;
            if (!runtime.putVerified("memberYebTrialActive::" + hash(trial), receipt.put("state", "A").toString())
                    || !current() || !runtime.putVerified(RECEIPT, null)) return;
            Log.other("余额宝已有体验金💵指定资产激活状态A已回查确认");
        }
    }

    private void recoverActivation(Set<String> selected, boolean allOwned) throws Exception {
        JSONObject receipt = MyUtils.newJSONObject(runtime.getString(RECEIPT));
        String trial = text(receipt, "trialId"), before = text(receipt, "beforeStatus");
        if (receipt.length() != 5 || !"activate".equals(text(receipt, "stage")) || !uid.equals(text(receipt, "uid"))
                || !id(trial) || before.isEmpty() || before.length() > 64 || "A".equals(before) || !validLedgerDay(number(receipt, "day"))
                || !allOwned && (selected == null || !selected.contains(trial))) return;
        Map<String, String> first = assets();
        if (first == null || !"A".equals(first.get(trial))) return;
        Map<String, String> second = assets();
        if (!first.equals(second) || !current()) return;
        if (!runtime.putVerified("memberYebTrialActive::" + hash(trial), receipt.put("state", "A").toString())
                || !current() || !runtime.putVerified(RECEIPT, null)) return;
        Log.other("余额宝已有体验金💵旧回执对应资产状态A连续回查确认，未重新提交激活");
    }

    private boolean reserve(String stage, BigDecimal amount, int budget, String identity, JSONObject receipt, boolean once) throws Exception {
        TimeUtil.sleep(0);
        String key = "memberYebVoucherBudget::" + stage, raw = runtime.getString(key);
        JSONObject ledger = raw.isEmpty() ? MyUtils.newJSONObject() : MyUtils.newJSONObject(raw);
        long savedDay = raw.isEmpty() ? 0 : number(ledger, "day"); BigDecimal used = raw.isEmpty() ? BigDecimal.ZERO : decimal(ledger, "used");
        JSONArray ids = raw.isEmpty() ? new JSONArray() : ledger.optJSONArray("attemptIds");
        String action = hash(identity), flag = "member::yebVoucherAttempt::" + stage + "::" + action;
        if (!raw.isEmpty() && !validLedgerDay(savedDay) || used == null || ids == null || ids.length() > 1000
                || Status.getIntFlagToday("member::yebVoucherAttempts::" + stage) < 0) return false;
        if (once && (used.signum() == 0 ? ids.length() != 0 : ids.length() != 1)
                || !once && used.compareTo(BigDecimal.valueOf(ids.length())) != 0) return false;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object previous = ids.opt(i);
            if (!(previous instanceof String) || !((String) previous).matches("[A-Za-z0-9_-]{43}") || !seen.add((String) previous)) return false;
        }
        if (savedDay != day) { used = BigDecimal.ZERO; ids = new JSONArray(); }
        for (int i = 0; i < ids.length(); i++) if (action.equals(ids.optString(i))) return false;
        if (once && used.signum() > 0 || used.add(amount).compareTo(BigDecimal.valueOf(budget)) > 0 || Status.hasFlagToday(flag)
                || !current() || !runtime.getString(RECEIPT).isEmpty()) return false;
        JSONObject next = MyUtils.newJSONObject().put("day", day).put("used", used.add(amount).toPlainString()).put("attemptIds", ids.put(action));
        if (!runtime.putVerified(key, next.toString()) || !current() || !runtime.putVerified(RECEIPT, receipt.put("uid", uid).toString())) return false;
        TimeUtil.sleep(0);
        if (!current()) return false;
        Status.flagToday(flag);
        Status.setIntFlagToday("member::yebVoucherAttempts::" + stage, Status.getIntFlagToday("member::yebVoucherAttempts::" + stage) + 1);
        return true;
    }

    private boolean validLedgerDay(long savedDay) {
        long year = savedDay / 1000;
        if (year < 1 || year > 9999 || savedDay > day) return false;
        try {
            LocalDate.ofYearDay((int) year, (int) (savedDay % 1000));
            return true;
        } catch (DateTimeException invalid) { return false; }
    }

    private JSONObject vouchers() throws Exception {
        JSONObject args = MyUtils.newJSONObject().put("component", "PROMO_ACTIVITY").put("sortType", "drawTime").put("source", "QIANAPP")
                .put("voucherTemplateIdList", new JSONArray().put("202312260007300180780087H5IR").put("2026011300073001807800H1558H"));
        JSONObject root = rpc("alipay.yebprod.query.queryYebTrialCertVoucher", args);
        JSONArray rows = root == null ? null : root.optJSONArray("certVoucherInfoList"); long count = number(root, "totalCount");
        if (rows == null || count < 0 || count > 1000 || count != rows.length()) return null;
        for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) == null) return null;
        return root;
    }

    private JSONObject balance() throws Exception {
        TimeUtil.sleep(0);
        if (!current()) return null;
        JSONObject root = MyUtils.newJSONObject(AntMemberRpcCall.queryYebExpGoldMain(null));
        TimeUtil.sleep(0);
        return current() && success(root) ? root.optJSONObject("resultData") : null;
    }

    private Map<String, String> assets() throws Exception {
        JSONObject root = rpc("alipay.yebprod.promo.yebTrialAsset", MyUtils.newJSONObject());
        JSONArray rows = root == null ? null : root.optJSONArray("trialInfoList");
        if (rows == null || rows.length() > 1000 || root.has("userId") && !uid.equals(text(root, "userId"))) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); String id = text(row, "trialId"), status = text(row, "status");
            if (!id(id) || status.isEmpty() || status.length() > 64 || result.put(id, status) != null
                    || row.has("userId") && !uid.equals(text(row, "userId"))) return null;
        }
        return result;
    }

    private JSONObject rpc(String method, JSONObject args) throws Exception {
        TimeUtil.sleep(0);
        if (!current()) return null;
        String body = new JSONArray().put(args).toString();
        boolean mutation = method.equals("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert")
                || method.equals("com.alipay.yebscenebff.expgold.index.exchange") || method.equals("alipay.yebprod.promo.yebTrial.active");
        JSONObject root = MyUtils.newJSONObject(mutation ? ApplicationHook.requestString(method, body, 1, 0)
                : ApplicationHook.requestString(method, body));
        TimeUtil.sleep(0);
        return current() && success(root) ? root : null;
    }
    private static boolean success(JSONObject root) {
        if (root == null || RpcRequestGuard.isFailure(root) || root.has("success") && !Boolean.TRUE.equals(root.opt("success"))
                || root.has("isSuccess") && !Boolean.TRUE.equals(root.opt("isSuccess")) || root.has("retCode") && !"0".equals(root.optString("retCode"))) return false;
        String result = root.optString("resultCode"), code = root.optString("code");
        if (!result.isEmpty() && !Set.of("100", "SUCCESS").contains(result) || !code.isEmpty() && !"100000000".equals(code)) return false;
        return Boolean.TRUE.equals(root.opt("success")) || "100".equals(result) || "SUCCESS".equals(result) || "100000000".equals(code);
    }
    private static BigDecimal decimal(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        if (!(value instanceof String) && !(value instanceof Number) || value.toString().length() > 128) return null;
        try { BigDecimal n = new BigDecimal(value.toString()); return n.signum() >= 0 && n.precision() <= 32 && n.scale() >= -12 && n.scale() <= 12 ? n : null; }
        catch (NumberFormatException e) { return null; }
    }
    private static long number(JSONObject row, String key) { BigDecimal n = decimal(row, key); try { return n == null ? -1 : n.longValueExact(); } catch (ArithmeticException e) { return -1; } }
    private static BigDecimal threshold(JSONObject row) { return row == null ? null : row.has("subThreshold") ? decimal(row, "subThreshold") : BigDecimal.ZERO; }
    private static BigDecimal decimalOrNegative(JSONObject row, String key) { BigDecimal value = decimal(row, key); return value == null ? BigDecimal.valueOf(-1) : value; }
    private static BigDecimal thresholdOrNegative(JSONObject row) { BigDecimal value = threshold(row); return value == null ? BigDecimal.valueOf(-1) : value; }
    private static String text(JSONObject row, String key) { Object value = row == null ? null : row.opt(key); return value instanceof String ? (String) value : ""; }
    private static boolean id(String value) { return value != null && !value.trim().isEmpty() && value.length() <= 256; }
    private static String hash(String text) throws Exception { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static int day() { Calendar c = MyUtils.getInstance(); return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR); }
    private boolean current() { return uid.equals(UserIdMap.getCurrentUid()) && day == day(); }
}
