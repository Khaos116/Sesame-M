package io.github.aw1y2z.sesame.model.task.other;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** SJ 3.6.67 activity contracts, with M's account-bound budget and readbacks. */
final class SjActivityTasks {
    private static final String LOTTERY = "com.alipay.consumecc.ark.promotion.gate.lottery-machine.";
    private static final String P2E = "com.alipay.gamecenteruprod.biz.rpc.p2e.";
    private static final String INTIMACY = "com.alipay.pcreditbfweb.needle.hbRelationship.queryIntimacyTaskListCard";
    private static final String MILEAGE = "mileage_shopping_indep_comp_exchange_sell_goods_exchange";
    private static final String RECEIPT = "sjActivityReceipt::", ATTEMPTS = "sjActivityAttempts";
    private final OtherRequestGate gate;
    private final String attemptsKey;
    private final String uid;
    private final int day, budget;

    SjActivityTasks(OtherRequestGate gate, int budget) {
        this(gate, budget, ATTEMPTS);
    }

    SjActivityTasks(OtherRequestGate gate, int budget, String attemptsKey) {
        this.gate = gate; this.budget = Math.min(Math.max(budget, 0), 50);
        this.attemptsKey = attemptsKey;
        uid = UserIdMap.getCurrentUid(); day = date();
    }

    static int date() {
        Calendar c = MyUtils.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    void current() {
        TimeUtil.sleep(0);
        if (uid == null || uid.isEmpty() || !uid.equals(UserIdMap.getCurrentUid()) || date() != day
                || ApplicationHook.isOffline()) throw new TaskCancelledException();
    }

    boolean enabled(String domain) {
        if (budget <= 0 || uid == null || uid.isEmpty()) return false;
        current();
        if (!RuntimeInfo.getInstance().getString(RECEIPT + domain).isEmpty()) {
            Log.record("SJ活动[" + domain + "]有未确认回执，本轮不重发");
            if ("ride".equals(domain)) Log.record("乐游记：上次前台会话未取得结束回执，当前未发送页面动作；需核对游戏页面进度");
            return false;
        }
        return true;
    }

    boolean reserve(String domain, String action) throws Exception {
        return reserve(domain, action, null);
    }

    private boolean reserve(String domain, String action, String acceptedReceipt) throws Exception {
        return reserve(domain, action, acceptedReceipt, null);
    }

    private boolean reserve(String domain, String action, String acceptedReceipt, JSONObject context) throws Exception {
        if (acceptedReceipt == null ? !enabled(domain) : budget <= 0) return false;
        current();
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        if (acceptedReceipt != null && !acceptedReceipt.equals(runtime.getString(RECEIPT + domain))) return false;
        String raw = runtime.getString(attemptsKey);
        JSONObject ledger = MyUtils.newJSONObject(raw);
        long used = raw.isEmpty() ? 0 : count(ledger, "count");
        long savedDay = raw.isEmpty() ? day : count(ledger, "day");
        if (used < 0 || savedDay < 20000101 || savedDay > day) {
            Log.record("SJ活动[" + domain + "]：每日预算记录无效，未提交操作");
            return false;
        }
        if (savedDay != day) used = 0;
        if (used >= budget) {
            Log.record("SJ活动[" + domain + "]：每日操作预算已用尽（" + used + "/" + budget + "），未提交操作");
            return false;
        }
        if (!runtime.putVerified(attemptsKey, MyUtils.newJSONObject().put("day", day).put("count", used + 1).toString())) {
            Log.record("SJ活动[" + domain + "]：每日预算保存失败，未提交操作");
            return false;
        }
        current();
        JSONObject receipt = MyUtils.newJSONObject().put("uid", uid).put("day", day).put("action", action);
        if (context != null) receipt.put("context", context);
        if (!runtime.putVerified(RECEIPT + domain, receipt.toString())) {
            Log.record("SJ活动[" + domain + "]：待核对记录保存失败，未提交操作");
            return false;
        }
        Log.record("SJ活动[" + domain + "]：已预留每日额度（" + (used + 1) + "/" + budget + "）");
        return true;
    }

    boolean confirmed(String domain, String label) {
        return confirmed(domain, label, false);
    }

    private boolean confirmed(String domain, String label, boolean submittedOnly) {
        current();
        if (!RuntimeInfo.getInstance().putVerified(RECEIPT + domain, null)) {
            Log.record("SJ活动[" + domain + "]：服务端状态已确认，但清除待核对记录失败，停止后续提交");
            return false;
        }
        String prefix = submittedOnly ? "📤 " : "✅ ";
        String message = prefix + label + (submittedOnly ? "：上报已回查，继续核对领奖状态" : "（已回查确认）");
        Log.other(message);
        Log.record(message);
        return true;
    }

    JSONObject call(String method, JSONObject args, boolean write) throws Exception {
        return call(method, args, write, null, null);
    }

    JSONObject write(String domain, String action, String method, JSONObject args) throws Exception {
        return call(method, args, true, action, null, domain);
    }

    String pending(String domain) {
        current();
        return RuntimeInfo.getInstance().getString(RECEIPT + domain);
    }

    boolean accepted(String domain, String label) {
        current();
        if (!RuntimeInfo.getInstance().putVerified(RECEIPT + domain, null)) return false;
        Log.record("📤 " + label + "：接口已确认受理");
        return true;
    }

    boolean discard(String domain, String reason) {
        current();
        boolean cleared = RuntimeInfo.getInstance().putVerified(RECEIPT + domain, null);
        Log.record("活动[" + domain + "]：" + reason + (cleared ? "，已解除回执；未记作成功" : "，清理回执保存失败"));
        return cleared;
    }

    private JSONObject call(String method, JSONObject args, boolean write, String action, JSONObject context) throws Exception {
        return call(method, args, write, action, context, null);
    }

    private JSONObject call(String method, JSONObject args, boolean write, String action, JSONObject context, String domain) throws Exception {
        current();
        String prefix = "SJ活动[" + method + "]：";
        boolean[] invoked = {false};
        String raw;
        try {
            raw = gate.call("SJ活动[" + method + "]", () -> {
                current();
                String body = new JSONArray().put(args).toString();
                // Reserve only after the shared gate admits this write; rejected requests leave no receipt or quota charge.
                if (action != null) {
                    if (!reserve(domain == null ? shenQuanDomain(action) : domain, action, null, context)) return null;
                    Log.record(domain == null ? "神券团购：尝试提交" + shenQuanAction(action) : "活动[" + domain + "]：尝试提交" + action);
                }
                invoked[0] = true;
                if (write) Log.record(prefix + "进入写RPC调用（单次发送）");
                return write ? ApplicationHook.requestString(method, body, 1, 0) : ApplicationHook.requestString(method, body);
            });
            current();
        } catch (Exception failure) {
            Log.record(prefix + (invoked[0] ? "已进入RPC，流程中断" : "未调用RPC，发送前停止")
                    + "（" + failure.getClass().getSimpleName() + "）");
            if (failure instanceof TaskCancelledException || failure instanceof OtherRequestGate.Denied
                    || failure instanceof OtherRequestGate.BudgetExhausted) throw failure;
            // The outer task logs stack traces; never propagate a transport message containing request secrets.
            throw new java.io.IOException(prefix + "RPC流程异常（" + failure.getClass().getSimpleName() + "）");
        }
        if (!invoked[0]) {
            Log.record(prefix + "未调用RPC，操作额度或待核对记录未获准");
            return null;
        }
        JSONObject result = MyUtils.newJSONObject(raw);
        boolean failed = RpcRequestGuard.isFailure(result);
        boolean rejected = domain != null && Boolean.FALSE.equals(result.opt("success"))
                && !MessageUtil.isRetryable(result) && !MessageUtil.isServerBusy(result);
        if (rejected) {
            // A temporary transport/busy failure is ambiguous even when it carries success=false.
            for (String field : new String[]{"code", "resultCode", "retCode", "errorCode", "error"}) {
                String value = result.optString(field).trim().toUpperCase(java.util.Locale.ROOT);
                if (Set.of("48", "102", "3000", "1009", "SYSTEM_ERROR", "REMOTE_INVOKE_EXCEPTION", "TRANSPORT_ERROR", "SYSTEM_BUSY", "SERVER_BUSY", "TOO_MANY_REQUESTS", "TIMEOUT", "REQUEST_TIMEOUT", "RATE_LIMIT", "RATE_LIMITED", "NETWORK_ERROR", "429", "500", "502", "503", "504").contains(value)) rejected = false;
            }
            for (String field : new String[]{"memo", "message", "desc", "resultDesc", "errorMessage"}) {
                String value = result.optString(field).toLowerCase(java.util.Locale.ROOT);
                if (value.contains("繁忙") || value.contains("稍后") || value.contains("开小差") || value.contains("人气大爆发") || value.contains("限流") || value.contains("超时")
                        || value.contains("busy") || value.contains("timeout") || value.contains("timed out") || value.contains("rate limit") || value.contains("try again")) rejected = false;
            }
            if (rejected) discard(domain, "服务端明确拒绝，本轮不重试，下轮可重新执行");
        }
        if (write || failed || !(Boolean.TRUE.equals(result.opt("success")) || Boolean.TRUE.equals(result.opt("isSuccess"))
                || "alipay.imasp.program.programInvoke".equals(method) && result.optJSONObject("components") != null))
            Log.record(prefix + "响应 " + (result.length() == 0 ? "空对象或解析失败，" : "")
                + "success=" + responseField(result, "success") + "，isSuccess=" + responseField(result, "isSuccess")
                + "，code=" + responseField(result, "code") + "，resultCode=" + responseField(result, "resultCode")
                + "，error=" + responseField(result, "error") + "，errorCode=" + responseField(result, "errorCode")
                + "，retCode=" + responseField(result, "retCode"));
        if (failed) {
            Log.record(prefix + "响应明确失败，停止本次操作" + (write && !rejected ? "，待核对记录保留" : ""));
            return null;
        }
        // programInvoke reports results per component, not necessarily at the root.
        if ("alipay.imasp.program.programInvoke".equals(method) && result.optJSONObject("components") != null) return result;
        if (Boolean.TRUE.equals(result.opt("success")) || Boolean.TRUE.equals(result.opt("isSuccess"))) return result;
        Log.record(prefix + "缺少有效成功标记，停止本次操作" + (write ? "，待核对记录保留" : ""));
        return null;
    }

    static String responseField(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        if (value == null || JSONObject.NULL.equals(value)) return "缺失";
        if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) return "类型异常";
        String text = value.toString();
        return text.matches("[0-9]{1,18}|true|false|[A-Z][A-Z0-9_]{0,79}") ? text : "非预期值";
    }

    private JSONObject lottery(String suffix, JSONObject args, boolean write) throws Exception {
        JSONObject root = call(LOTTERY + suffix, args, write);
        if (root == null) return null;
        JSONObject data = root.optJSONObject("data");
        if (!"10000001".equals(root.optString("code")) || data == null) {
            Log.record("神券团购[" + suffix + "]：业务code=" + responseField(root, "code") + "，data对象=" + (data != null) + "，本次校验未通过");
            return null;
        }
        return data;
    }

    private JSONObject camp() throws Exception {
        JSONObject data = lottery("camp.query", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
        if (data == null) return null;
        Log.record("神券团购活动状态：active=" + responseField(data, "active") + "，今日已抽=" + count(data, "dayConsumeCount")
                + "，剩余机会=" + count(data, "remainingCount") + "，已签到=" + responseField(data.optJSONObject("extInfo"), "hasCheckedIn"));
        if (Boolean.TRUE.equals(data.opt("active"))) return data;
        Log.record("神券团购：活动未开启或active字段无效，停止本次操作");
        return null;
    }

    private JSONObject lotteryTask(String play) throws Exception {
        JSONObject data = lottery("taskConsult", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
        return unique(data == null ? null : data.optJSONArray("taskList"), "playId", play);
    }

    void shenQuan(boolean sign, boolean tasks, boolean draw, String location) throws Exception {
        Log.record("神券团购：开始，签到=" + sign + "，任务=" + tasks + "，抽奖=" + draw + "，每日操作预算=" + budget);
        if (!(sign || tasks || draw) || budget <= 0 || uid == null || uid.isEmpty()) return;
        if (!reconcileShenQuan() || !enabled("shenQuan")) return;
        // SJ checks sign, tasks and free draws independently; an unknown sign must only prevent another sign.
        boolean signReady = reconcileShenQuan("shenQuanSign") && enabled("shenQuanSign");
        JSONObject camp = camp();
        if (camp == null) return;
        if (sign && signReady && !checked(camp)) {
            JSONObject consult = lottery("checkInConsult", MyUtils.newJSONObject(), false);
            Log.record("神券团购签到资格：triggerCheckIn=" + responseField(consult, "triggerCheckIn"));
            if (consult != null && Boolean.TRUE.equals(consult.opt("triggerCheckIn"))) {
                JSONObject ack = call(LOTTERY + "checkIn", MyUtils.newJSONObject(), true, "sign", null);
                JSONObject after = camp();
                if (!checked(after)) {
                    Log.record("神券团购签到未确认：提交响应通过=" + (ack != null && "10000001".equals(ack.optString("code")))
                            + "，回查已签到=" + checked(after) + "，待核对记录=" + !RuntimeInfo.getInstance().getString(RECEIPT + "shenQuanSign").isEmpty());
                    Log.record("神券团购：保留未确认签到回执，不重发签到，继续检查其他任务及已有免费抽奖机会");
                } else {
                    if (ack == null || !"10000001".equals(ack.optString("code"))) {
                        Log.record("神券团购签到：提交响应未通过，但本账号今日有效回查已签到；不重发签到，按回查结果继续");
                    }
                    if (!confirmed("shenQuanSign", "神券团购签到")) return;
                }
            } else {
                Log.record("神券团购：签到资格未允许提交，本轮未发送签到请求");
            }
        } else if (sign && checked(camp)) {
            Log.record("神券团购：服务端显示今日已签到，无需提交");
        } else if (sign) {
            Log.record("神券团购：上次签到结果未确认，本轮不重发签到，继续检查其他任务及已有免费抽奖机会");
        }
        if (tasks) {
            JSONObject data = lottery("taskConsult", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
            JSONArray list = data == null ? null : data.optJSONArray("taskList");
            if (list == null || list.length() > 100) {
                Log.record("神券团购：任务列表缺失或数量超限，停止任务处理");
                return;
            }
            Log.record("神券团购：查询到任务数=" + list.length());
            int completed = 0, previouslyCompleted = 0;
            for (int i = 0; i < list.length(); i++) {
                JSONObject initial = list.optJSONObject(i);
                String label = "神券团购任务[" + lotteryTaskTitle(initial) + "]";
                String id = text(initial, "playId");
                JSONObject row = id.isEmpty() ? null : unique(list, "playId", id);
                if (row == null || !lotterySafe(row)) {
                    Log.record(label + "：跳过第" + (i + 1) + "项任务，原因=" + (id.isEmpty() ? "playId缺失"
                            : row == null ? "playId重复" : "不满足免费浏览/搜索规则")
                            + "，类型=" + responseField(initial, "taskType") + "，状态=" + responseField(initial, "taskStatus")
                            + "，标题字段=" + !text(initial == null ? null : initial.optJSONObject("taskExtProps"), "taskTitle").isEmpty());
                    continue;
                }
                if (Status.hasFlagToday("sjActivityShenQuanUnconfirmed::" + id)) {
                    Log.record(label + "：旧回执尚未确认，仅跳过该任务，继续其他操作");
                    continue;
                }
                String taskDomain = shenQuanDomain("trigger:" + id);
                if (!reconcileShenQuan(taskDomain) || !enabled(taskDomain) || Status.hasFlagToday("sjActivityShenQuanUnconfirmed::" + id)) {
                    Log.record(label + "：回执尚未确认，仅跳过本任务，继续其他任务及已有免费次数抽奖");
                    continue;
                }
                if (Set.of("RECEIVED", "DONE").contains(row.optString("taskStatus"))) {
                    previouslyCompleted++;
                    Log.record("☑️ " + label + "：此前已完成并领奖，本轮无需提交");
                    continue;
                }
                row = lotteryTask(id);
                if (row == null || !lotterySafe(row) || !lotteryContract(initial).equals(lotteryContract(row))) {
                    Log.record(label + "：实时查询未匹配原任务资料，未提交");
                    continue;
                }
                JSONObject props = row.optJSONObject("taskExtProps");
                // SJ 对未提供时长或 0 秒的浏览任务等待 4～6 秒；取其下限。
                long seconds = props == null || !props.has("browseTime") ? 4 : count(props, "browseTime");
                if (seconds == 0) seconds = 4;
                Log.record(label + "：状态=" + responseField(row, "taskStatus") + "，等待秒数=" + seconds);
                if ("INIT".equals(row.optString("taskStatus"))) {
                    if (seconds < 1 || seconds > 300) {
                        Log.record(label + "：任务等待时长不在1～300秒内，未提交");
                        continue;
                    }
                    JSONObject ack = call("alipay.asset.promplaymatrix.play.trigger", MyUtils.newJSONObject().put("bizNo", UUID.randomUUID().toString()).put("playId", id), true,
                            "trigger:" + id, MyUtils.newJSONObject().put("contract", lotteryContract(row)));
                    if (ack == null) { Log.record(label + "：上报响应未通过，结果待回查，未记完成"); continue; }
                    Log.record(label + "：上报已受理，开始等待" + seconds + "秒，待回查完成状态");
                    waitSeconds(seconds);
                    JSONObject after = lotteryTask(id);
                    if (after == null || !lotteryContract(row).equals(lotteryContract(after))
                            || !Set.of("TO_RECEIVE", "RECEIVED", "DONE").contains(after.optString("taskStatus"))) {
                        Log.record(label + "：任务上报未确认，回查状态=" + responseField(after, "taskStatus")
                                + "，任务资料匹配=" + (after != null && lotteryContract(row).equals(lotteryContract(after))));
                        continue;
                    }
                    if (!confirmed(taskDomain, label + "上报", true)) continue;
                    row = after;
                    if (Set.of("RECEIVED", "DONE").contains(row.optString("taskStatus"))) {
                        completed++;
                        String message = "✅ " + label + "：本轮完成并领奖，服务端状态回查已确认，未提供奖励名称";
                        Log.other(message);
                        Log.record(message);
                    }
                }
                if ("TO_RECEIVE".equals(row.optString("taskStatus"))) {
                    JSONObject ack = call("alipay.asset.promplaymatrix.play.prize.receive", MyUtils.newJSONObject().put("bizNo", UUID.randomUUID().toString()).put("playId", id), true,
                            "reward:" + id, MyUtils.newJSONObject().put("contract", lotteryContract(row)));
                    JSONObject after = lotteryTask(id);
                    Log.record(label + "：领奖回查状态=" + responseField(after, "taskStatus")
                            + "，任务资料匹配=" + (after != null && lotteryContract(row).equals(lotteryContract(after))));
                    if (ack == null || after == null || !lotteryContract(row).equals(lotteryContract(after))
                            || !Set.of("RECEIVED", "DONE").contains(after.optString("taskStatus"))
                            || !confirmed(taskDomain, "神券团购完成任务[" + lotteryTaskTitle(row) + "]得["
                            + lotteryPrizeNames(ack.optJSONArray("prizeInfoList")) + "]")) continue;
                    completed++;
                }
            }
            Log.record("📊 神券团购任务结果：本轮新完成并领奖=" + completed + "项，此前已完成并领奖=" + previouslyCompleted + "项");
        }
        if (!draw) return;
        JSONObject args = drawLocation(location);
        if (args == null) { Log.record("神券团购抽奖：定位JSON缺少必要字段或格式不正确，本轮跳过"); return; }
        for (int i = 0; i < budget; i++) {
            JSONObject before = camp();
            long remaining = count(before, "remainingCount"), consumed = count(before, "dayConsumeCount");
            if (remaining <= 0 || consumed < 0) {
                Log.record("神券团购：" + (remaining == 0 && consumed >= 0 ? "剩余机会为0，抽奖处理已结束"
                        : "抽奖次数字段无效，停止抽奖（remaining=" + remaining + "，consumed=" + consumed + "）"));
                return;
            }
            args.put("timestamp", System.currentTimeMillis());
            JSONObject ack = call(LOTTERY + "receive", args, true, "draw:" + consumed,
                    MyUtils.newJSONObject().put("remaining", remaining).put("consumed", consumed)), after = camp();
            Log.record("神券团购抽奖回查：已抽=" + consumed + "→" + count(after, "dayConsumeCount")
                    + "，剩余=" + remaining + "→" + count(after, "remainingCount"));
            if (ack == null || !"10000001".equals(ack.optString("code")) || ack.optJSONObject("data") == null || count(after, "remainingCount") != remaining - 1
                    || count(after, "dayConsumeCount") != consumed + 1 || !confirmed("shenQuan", "神券团购第" + (consumed + 1)
                    + "抽得[" + lotteryPrizeNames(ack.optJSONObject("data").optJSONArray("prizeList")) + "]")) return;
        }
    }

    private static String lotteryTaskTitle(JSONObject row) {
        String title = text(row == null ? null : row.optJSONObject("taskExtProps"), "taskTitle").replaceAll("[\\r\\n\\t]", " ");
        return title.isEmpty() ? "未提供任务名称" : title.substring(0, Math.min(title.length(), 80));
    }

    private static String lotteryPrizeNames(JSONArray prizes) {
        StringBuilder names = new StringBuilder();
        // shortcut: 日志最多展示20项奖励；服务端出现更多奖品时再扩展展示。
        for (int i = 0; prizes != null && i < Math.min(prizes.length(), 20); i++) {
            JSONObject prize = prizes.optJSONObject(i);
            JSONObject display = prize == null ? null : prize.optJSONObject("prizeDisplayInfo");
            String name = display == null ? text(prize, "prizeName") : text(display, "PRIZE_DISPLAY_NAME");
            if (name.isEmpty()) name = display == null ? text(prize, "prizeBizSubType") : text(display, "PRIZE_SHORT_TITLE");
            name = name.replaceAll("[\\r\\n\\t]", " ");
            if (name.isEmpty()) continue;
            if (names.length() > 0) names.append(",");
            names.append(name, 0, Math.min(name.length(), 80));
        }
        return names.length() == 0 ? "未提供奖励名称" : names.toString();
    }

    private static boolean checked(JSONObject camp) {
        JSONObject ext = camp == null ? null : camp.optJSONObject("extInfo");
        return ext != null && (Boolean.TRUE.equals(ext.opt("hasCheckedIn")) || "true".equals(ext.opt("hasCheckedIn")));
    }

    private boolean reconcileShenQuan() throws Exception {
        return reconcileShenQuan("shenQuan");
    }

    private static String shenQuanDomain(String action) {
        if ("sign".equals(action)) return "shenQuanSign";
        return action.startsWith("trigger:") || action.startsWith("reward:")
                ? "shenQuanTask::" + action.substring(action.indexOf(':') + 1) : "shenQuan";
    }

    private boolean reconcileShenQuan(String domain) throws Exception {
        current();
        String raw = RuntimeInfo.getInstance().getString(RECEIPT + domain);
        if (raw.isEmpty()) return true;
        JSONObject receipt = MyUtils.newJSONObject(raw), context = receipt.optJSONObject("context");
        String action = text(receipt, "action"), label = shenQuanAction(action);
        long receiptDay = count(receipt, "day");
        boolean validDay = receiptDay >= 20000101 && receiptDay <= day;
        if (validDay) {
            Calendar savedDate = MyUtils.getInstance();
            savedDate.setLenient(false);
            savedDate.set((int) receiptDay / 10000, (int) receiptDay / 100 % 100 - 1, (int) receiptDay % 100);
            try { savedDate.getTimeInMillis(); } catch (IllegalArgumentException invalid) { validDay = false; }
        }
        boolean validAction = "sign".equals(action) || action.matches("draw:[0-9]{1,18}")
                || (action.startsWith("trigger:") || action.startsWith("reward:")) && !action.substring(action.indexOf(':') + 1).isEmpty();
        if (!uid.equals(text(receipt, "uid")) || !validDay || !validAction || receipt.has("context") && context == null
                || !"shenQuan".equals(domain) && !domain.equals(shenQuanDomain(action))) {
            Log.record("神券团购待核对记录：账号匹配=" + uid.equals(text(receipt, "uid")) + "，记录日期=" + count(receipt, "day")
                    + "，当前日期=" + day + "，操作=" + label + "，资料有效=" + (!receipt.has("context") || context != null)
                    + "，日期有效=" + validDay + "，操作有效=" + validAction
                    + "；校验未通过，保留记录，不重复提交");
            return false;
        }
        Log.record("神券团购：查询上次" + label + "的服务端状态");
        JSONObject status = camp();
        if (status == null) {
            Log.record("神券团购：活动状态查询未通过校验，保留待核对记录");
            return false;
        }
        if (receiptDay == day && "shenQuan".equals(domain) && !domain.equals(shenQuanDomain(action))) {
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            String taskKey = RECEIPT + shenQuanDomain(action), saved = runtime.getString(taskKey);
            if (!saved.isEmpty() && !saved.equals(raw) || !runtime.putVerified(taskKey, raw)) {
                Log.record("神券团购：旧" + label + "回执分离保存失败或与已有记录冲突，保留原记录");
                return false;
            }
            current();
            if (!raw.equals(runtime.getString(RECEIPT + domain)) || !runtime.putVerified(RECEIPT + domain, null)) return false;
            Log.record("神券团购：旧" + label + "回执已原样分离，仅限制对应操作，不阻塞其他任务及已有免费次数抽奖，未知结果未记成功");
            return true;
        }
        if (receiptDay != day) {
            // SJ 的机会/签到按日查询；昨日计数不能与今日重置后的计数比较，也不能确认昨日成功。
            if (count(status, "remainingCount") < 0 || count(status, "dayConsumeCount") < 0) return false;
            String id = action.startsWith("trigger:") || action.startsWith("reward:")
                    ? action.substring(action.indexOf(':') + 1) : "";
            JSONObject row = id.isEmpty() ? null : lotteryTask(id);
            String state = text(row, "taskStatus");
            String archiveKey = RECEIPT + domain + "::history::" + receiptDay;
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            String saved = runtime.getString(archiveKey);
            if (!saved.isEmpty() && !saved.equals(raw) || !runtime.putVerified(archiveKey, raw)) {
                Log.record("神券团购：跨日回执归档失败，保留原记录，本轮未提交");
                return false;
            }
            current();
            if (!id.isEmpty() && !(row != null && lotterySafe(row)
                    && (context == null || text(context, "contract").equals(lotteryContract(row)))
                    && (Set.of("RECEIVED", "DONE").contains(state)
                    || action.startsWith("trigger:") && "TO_RECEIVE".equals(state))))
                Status.flagToday("sjActivityShenQuanUnconfirmed::" + id);
            if (!raw.equals(runtime.getString(RECEIPT + domain)) || !runtime.putVerified(RECEIPT + domain, null)) return false;
            Log.record("神券团购：" + receiptDay + "的" + label + "回执已归档，历史结果未计作成功；继续按今日资格处理"
                    + (id.isEmpty() ? "" : "，旧任务状态=" + responseField(row, "taskStatus")));
            return true;
        }
        boolean done = false;
        if ("sign".equals(action)) {
            done = checked(status);
            JSONObject ext = status.optJSONObject("extInfo");
            Object signed = ext == null ? null : ext.opt("hasCheckedIn");
            Log.record("神券团购回查：今日签到状态=" + (done ? "已签到"
                    : Boolean.FALSE.equals(signed) || "false".equals(signed) ? "未签到" : "未知"));
        } else if (action.startsWith("trigger:") || action.startsWith("reward:")) {
            String id = action.substring(action.indexOf(':') + 1);
            JSONObject row = id.isEmpty() ? null : lotteryTask(id);
            String state = text(row, "taskStatus");
            label = "任务[" + lotteryTaskTitle(row) + "]" + shenQuanAction(action);
            Log.record("神券团购" + label + "回查：任务状态=" + (Set.of("INIT", "TO_RECEIVE", "RECEIVED", "DONE").contains(state) ? state : "未知"));
            if (row != null && lotterySafe(row) && (context == null
                    || !text(context, "contract").isEmpty() && text(context, "contract").equals(lotteryContract(row)))) {
                done = action.startsWith("trigger:") ? Set.of("TO_RECEIVE", "RECEIVED", "DONE").contains(state)
                        : Set.of("RECEIVED", "DONE").contains(state);
            } else {
                Log.record("神券团购回查：任务未唯一匹配、规则不满足或与提交时资料不一致");
            }
        } else if (action.startsWith("draw:")) {
            long before;
            try { before = Long.parseLong(action.substring(5)); }
            catch (NumberFormatException invalid) { before = -1; }
            long consumed = count(status, "dayConsumeCount"), remaining = count(status, "remainingCount");
            Log.record("神券团购回查：今日已抽=" + consumed + "，剩余机会=" + remaining);
            done = before >= 0 && before < Long.MAX_VALUE && consumed == before + 1 && remaining >= 0
                    && (context == null || count(context, "consumed") == before
                    && count(context, "remaining") > 0 && remaining == count(context, "remaining") - 1);
        }
        if (done) return confirmed(domain, "神券团购" + label + "待核对记录", action.startsWith("trigger:"));
        Log.record("神券团购：上次" + label + "结果仍未确认，本轮已回查，不重复提交");
        return false;
    }

    private static String shenQuanAction(String action) {
        if ("sign".equals(action)) return "签到";
        if (action.startsWith("trigger:")) return "任务上报";
        if (action.startsWith("reward:")) return "抽奖机会领取";
        if (action.startsWith("draw:")) return "抽奖";
        return "未知操作";
    }

    static JSONObject drawLocation(String raw) throws Exception {
        JSONObject source = MyUtils.newJSONObject(raw), args = MyUtils.newJSONObject();
        for (String key : new String[]{"city", "cityAdcode", "district", "province", "provinceAdcode", "latitude", "longitude"}) {
            String value = text(source, key);
            if (value.isEmpty()) return null;
            args.put(key, value);
        }
        for (String key : new String[]{"poiNameForTitle", "walletVersion"}) {
            String value = text(source, key);
            if (!value.isEmpty()) args.put(key, value);
        }
        double latitude, longitude;
        try { latitude = Double.parseDouble(text(source, "latitude")); longitude = Double.parseDouble(text(source, "longitude")); }
        catch (NumberFormatException e) { return null; }
        if (!Double.isFinite(latitude) || Math.abs(latitude) > 90 || !Double.isFinite(longitude) || Math.abs(longitude) > 180
                || !text(source, "cityAdcode").matches("[0-9]{6}") || !text(source, "provinceAdcode").matches("[0-9]{6}")) return null;
        return args.put("accuracy", 30).put("altitude", 0).put("bearing", 0).put("dataType", "PREFETCH_REALITY")
                .put("isPrefetch", true).put("isRealWithDistrictAdcode", "true").put("osType", "android")
                .put("speed", 0).put("stageCode", "RECOMMEND").put("time", 0);
    }

    static boolean unsafe(String value) {
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        for (String token : new String[]{"ORDER", "BUY", "PAY", "SUBSCRIBE", "INVITE", "SIGNUP_PRODUCT", "TRANSFORMER", "COMMON_EVENT_TASK",
                "付款", "支付", "下单", "购买", "充值", "核销", "转账", "开户", "开通", "签约", "借款", "贷款", "理财", "保险", "余额宝", "订阅", "邀请", "分享", "注册", "绑卡", "授权"}) {
            if (upper.contains(token)) return true;
        }
        return false;
    }

    static boolean lotterySafe(JSONObject row) {
        JSONObject props = row == null ? null : row.optJSONObject("taskExtProps");
        String title = text(props, "taskTitle"), type = text(row, "taskType"), template = text(props, "taskTemplate");
        // SJ 允许“浏览支付宝/支付活动”等标题；实际 PAY/ORDER 类型仍禁止。
        String checkedTitle = title.contains("浏览") || title.contains("逛") ? title.replace("支付", "") : title;
        if (unsafe(checkedTitle + " " + type + " " + template)) return false;
        return type.contains("BROWSE") || type.contains("SEARCH") || template.equals("BROWSE") || template.equals("SEARCH")
                || title.contains("浏览") || title.contains("搜索") || title.contains("逛");
    }

    private static String lotteryContract(JSONObject row) {
        JSONObject props = row.optJSONObject("taskExtProps");
        return text(row, "playId") + "|" + text(row, "taskType") + "|" + text(props, "taskTitle") + "|" + text(props, "taskTemplate") + "|" + count(props, "browseTime");
    }

    private JSONObject p2eHome() throws Exception {
        JSONObject root = call(P2E + "queryHomePage", MyUtils.newJSONObject().put("appId", "2021003125685383").put("deviceLevel", "high")
                .put("source", "alty_mc_xeootk83").put("screenType", 10).put("subscribePanelCheck", true), false);
        return root == null ? null : root.optJSONObject("data");
    }

    void p2eSign() throws Exception {
        if (!enabled("p2e") || Status.hasFlagToday("other::sjP2ESign")) return;
        JSONObject before = p2eHome(), sign = before == null ? null : before.optJSONObject("signUpModuleVO");
        JSONObject today = todaySign(sign);
        if (today == null) return;
        if ("SIGNED".equals(today.optString("signUpStatus"))) { Status.flagToday("other::sjP2ESign"); return; }
        if (!Set.of("UNSIGNED", "NOT_SIGNED", "TO_SIGN", "INIT").contains(today.optString("signUpStatus"))) return;
        String sequence = text(sign, "signSequenceId"), date = text(sign, "date");
        long index = count(today, "displayIndex"), days = count(sign, "completedSignUpDays");
        if (sequence.isEmpty() || !date.replace("-", "").equals(String.valueOf(day)) || index < 1 || index > 366 || days < 0) return;
        if (!reserve("p2e", "sign:" + sequence + ":" + date + ":" + index)) return;
        JSONObject ack = call(P2E + "signIn", MyUtils.newJSONObject().put("appId", "2021003125685383").put("date", date).put("index", index)
                .put("signSequenceId", sequence).put("source", "alty_mc_xeootk83"), true);
        JSONObject after = p2eHome(), fresh = after == null ? null : after.optJSONObject("signUpModuleVO"), record = todaySign(fresh);
        if (ack != null && record != null && sequence.equals(text(fresh, "signSequenceId")) && date.equals(text(fresh, "date"))
                && index == count(record, "displayIndex") && "SIGNED".equals(record.optString("signUpStatus"))
                && count(fresh, "completedSignUpDays") == days + 1 && confirmed("p2e", "游戏中心玩赚签到")) Status.flagToday("other::sjP2ESign");
    }

    private static JSONObject todaySign(JSONObject sign) {
        JSONArray rows = sign == null ? null : sign.optJSONArray("signRecordVOList");
        JSONObject found = null;
        if (rows == null || rows.length() > 366) return null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            if (Boolean.TRUE.equals(row.opt("isToday"))) { if (found != null) return null; found = row; }
        }
        return found;
    }

    private JSONArray intimacyRows() throws Exception {
        JSONObject root = call(INTIMACY, MyUtils.newJSONObject().put("params", MyUtils.newJSONObject()
                .put("activityCommonExtra", MyUtils.newJSONObject().put("chInfo", "ch_INTIMACY__chsub_zfbsousuohyzx").toString())
                .put("channel", "hbRelationshipBenefit").put("queryAllTask", true).put("scene", "home").put("version", "v2")), false);
        JSONObject result = root == null ? null : root.optJSONObject("result");
        return result == null ? null : result.optJSONArray("taskDetailDtos");
    }

    void intimacy() throws Exception {
        if (!enabled("intimacy")) return;
        JSONArray rows = intimacyRows();
        if (rows == null || rows.length() > 100) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject initial = rows.optJSONObject(i);
            String id = text(initial, "taskId");
            JSONObject row = id.isEmpty() ? null : unique(rows, "taskId", id);
            if (!intimacySafe(row) || Status.hasFlagToday("other::sjIntimacy::" + id)) continue;
            JSONObject fresh = unique(intimacyRows(), "taskId", id);
            if (!intimacySafe(fresh) || !intimacyContract(row).equals(intimacyContract(fresh))) continue;
            long seconds = browseSeconds(row);
            if (seconds < 1 || seconds > 300 || !reserve("intimacy", "signup:" + id)) return;
            JSONObject args = MyUtils.newJSONObject().put("appletId", id).put("taskCenId", text(row, "taskCenId")).put("stageCode", "signup");
            JSONObject ack = call("alipay.promoprod.applet.trigger", args, true);
            fresh = unique(intimacyRows(), "taskId", id);
            if (ack == null || !intimacySafe(fresh) || !intimacyContract(row).equals(intimacyContract(fresh))) return;
            // A signup ACK permits this round's send; it is never logged as task completion.
            String signupReceipt = RuntimeInfo.getInstance().getString(RECEIPT + "intimacy");
            waitSeconds(seconds);
            fresh = unique(intimacyRows(), "taskId", id);
            if (!intimacySafe(fresh) || !intimacyContract(row).equals(intimacyContract(fresh)) || !reserve("intimacy", "send:" + id, signupReceipt)) return;
            ack = call("alipay.promoprod.applet.trigger", args.put("stageCode", "send"), true);
            fresh = unique(intimacyRows(), "taskId", id);
            if (ack == null || fresh == null || !intimacyContract(row).equals(intimacyContract(fresh))
                    || !Set.of("COMPLETED", "FINISHED", "RECEIVED", "DONE").contains(fresh.optString("taskShowStatus"))) return;
            if (!confirmed("intimacy", "花呗亲密度任务")) return;
            Status.flagToday("other::sjIntimacy::" + id);
        }
    }

    private static boolean intimacySafe(JSONObject row) {
        if (row == null || !"AVAILABLE".equals(row.optString("taskShowStatus")) || !"APPLET".equals(row.optString("taskType")) || text(row, "taskCenId").isEmpty()) return false;
        String title = text(row, "taskTitle");
        if (title.isEmpty()) title = text(row, "title");
        return !unsafe(title + " " + text(row, "taskTemplate")) && (title.contains("浏览") || title.contains("逛") || title.contains("搜索"));
    }

    private static String intimacyContract(JSONObject row) {
        return text(row, "taskId") + "|" + text(row, "taskCenId") + "|" + text(row, "taskType") + "|" + text(row, "taskTitle") + "|" + text(row, "title")
                + "|" + text(row, "taskTemplate") + "|" + text(row, "targetUrl") + "|" + browseSeconds(row);
    }

    private static long browseSeconds(JSONObject row) {
        long direct = count(row, "browseTime");
        JSONObject props = row == null ? null : row.optJSONObject("taskExtProps");
        long nested = count(props, "browseTime");
        if (direct >= 0 && nested >= 0 && direct != nested) return -1;
        return direct >= 0 ? direct : nested;
    }

    private JSONObject mileage(String component, JSONObject params, String city, boolean write) throws Exception {
        JSONObject root = call("alipay.imasp.program.programInvoke", MyUtils.newJSONObject().put("cityCode", city).put("channel", "")
                .put("components", MyUtils.newJSONObject().put(component, params)).put("operationParamIdentify", "mileage_shopping"), write);
        JSONObject components = root == null ? null : root.optJSONObject("components"), value = components == null ? null : components.optJSONObject(component);
        return value != null && !RpcRequestGuard.isFailure(value) && (!value.has("isSuccess") || Boolean.TRUE.equals(value.opt("isSuccess")))
                ? value.optJSONObject("content") : null;
    }

    private long mileageBalance(String city) throws Exception {
        JSONObject content = mileage("mileage_point_query_detail", MyUtils.newJSONObject().put("queryUnReceivePoint", "false"), city, false);
        return count(content == null ? null : content.optJSONObject("mileagePointInfo"), "currentPoint");
    }

    void mileage(String codes, String city) throws Exception {
        if (!enabled("mileage") || city == null || !city.matches("[0-9]{6}") || codes == null || codes.isBlank()) return;
        Set<String> seen = new HashSet<>();
        String[] targets = codes.split(",");
        if (targets.length > 20) return;
        for (String value : targets) {
            String code = value.trim();
            if (!code.matches("[A-Za-z0-9_-]{1,100}") || !seen.add(code) || Status.hasFlagToday("other::sjMileage::" + city + "::" + code)) continue;
            long before = mileageBalance(city);
            if (before <= 0 || !reserve("mileage", "exchange:" + city + ":" + code)) return;
            JSONObject content = mileage(MILEAGE, MyUtils.newJSONObject().put("code", code), city, true);
            JSONObject prize = content == null ? null : content.optJSONObject("exchangePrize");
            long after = mileageBalance(city);
            if (prize == null || prize.length() == 0 || after < 0 || after >= before || !confirmed("mileage", "里程兑换出行券")) return;
            Status.flagToday("other::sjMileage::" + city + "::" + code);
        }
    }

    void leiYouJiTasks() throws Exception {
        if (!enabled("leiYouJi")) return;
        String operation = "com.alipay.gamecenteruprod.biz.rpc.v3.queryModularTaskList";
        JSONObject args = MyUtils.newJSONObject().put("source", "ch_appcenter__chsub_9patch");
        JSONObject root = call(operation, args, false), result = root == null ? null : root.optJSONObject("result");
        JSONArray rows = modularRows(result);
        if (rows == null) return;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String id = text(row, "taskId"), title = text(row, "title"), type = text(row, "taskType");
            if (id.isEmpty() || unique(rows, "taskId", id) == null || unsafe(title + " " + type)
                    || !(title.contains("乐游记") || title.contains("骑行")) || !"BROWSE".equals(type)) continue;
            long seconds = count(row, "browseTime");
            if (!"TODO".equals(row.optString("taskStatus")) || seconds < 1 || seconds > 300) continue;
            waitSeconds(seconds);
            root = call(operation, args, false); result = root == null ? null : root.optJSONObject("result");
            JSONObject fresh = unique(modularRows(result), "taskId", id);
            if (fresh == null || !row.toString().equals(fresh.toString()) || !reserve("leiYouJi", "modular:" + id)) return;
            JSONObject ack = call("com.alipay.gamecenteruprod.biz.rpc.finishModularTask", MyUtils.newJSONObject().put("source", "ch_appcenter__chsub_9patch")
                    .put("taskId", id).put("taskStatus", "FINISHED"), true);
            root = call(operation, args, false); result = root == null ? null : root.optJSONObject("result");
            fresh = unique(modularRows(result), "taskId", id);
            if (ack == null || fresh == null || !title.equals(text(fresh, "title")) || !type.equals(text(fresh, "taskType"))
                    || seconds != count(fresh, "browseTime") || !Set.of("FINISHED", "RECEIVED").contains(fresh.optString("taskStatus"))
                    || !confirmed("leiYouJi", "乐游记浏览任务")) return;
        }
    }

    static JSONArray modularRows(JSONObject result) {
        if (result == null) return null;
        JSONArray rows = result.optJSONArray("taskList");
        if (rows != null) return rows.length() <= 100 ? rows : null;
        JSONArray modules = result.optJSONArray("modularTaskList"), combined = new JSONArray();
        if (modules == null || modules.length() > 100) return null;
        for (int i = 0; i < modules.length(); i++) {
            JSONObject module = modules.optJSONObject(i);
            JSONArray list = module == null ? null : module.optJSONArray("taskList");
            if (list == null || combined.length() + list.length() > 100) return null;
            for (int j = 0; j < list.length(); j++) combined.put(list.opt(j));
        }
        return combined;
    }

    void waitSeconds(long seconds) {
        for (long i = 0; i < seconds; i++) { TimeUtil.sleep(1000); current(); }
    }

    static JSONObject unique(JSONArray rows, String key, String id) {
        if (rows == null || id.isEmpty() || rows.length() > 100) return null;
        JSONObject found = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            if (id.equals(text(row, key))) { if (found != null) return null; found = row; }
        }
        return found;
    }

    static String text(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    static long count(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try { long number = new java.math.BigDecimal(value.toString()).longValueExact(); return number >= 0 ? number : -1; }
        catch (ArithmeticException | NumberFormatException e) { return -1; }
    }
}
