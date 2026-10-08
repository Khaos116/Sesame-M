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
            return false;
        }
        return true;
    }

    boolean reserve(String domain, String action) throws Exception {
        return reserve(domain, action, null);
    }

    private boolean reserve(String domain, String action, String acceptedReceipt) throws Exception {
        if (acceptedReceipt == null ? !enabled(domain) : budget <= 0) return false;
        current();
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        if (acceptedReceipt != null && !acceptedReceipt.equals(runtime.getString(RECEIPT + domain))) return false;
        String raw = runtime.getString(attemptsKey);
        JSONObject ledger = MyUtils.newJSONObject(raw);
        long used = raw.isEmpty() ? 0 : count(ledger, "count");
        long savedDay = raw.isEmpty() ? day : count(ledger, "day");
        if (used < 0 || savedDay < 20000101 || savedDay > day) return false;
        if (savedDay != day) used = 0;
        if (used >= budget) return false;
        if (!runtime.putVerified(attemptsKey, MyUtils.newJSONObject().put("day", day).put("count", used + 1).toString())) return false;
        current();
        return runtime.putVerified(RECEIPT + domain, MyUtils.newJSONObject().put("uid", uid).put("day", day).put("action", action).toString());
    }

    boolean confirmed(String domain, String label) {
        current();
        if (!RuntimeInfo.getInstance().putVerified(RECEIPT + domain, null)) return false;
        Log.other(label + "：服务端状态回查确认");
        return true;
    }

    JSONObject call(String method, JSONObject args, boolean write) throws Exception {
        current();
        String raw = gate.call("SJ活动", () -> {
            current();
            String body = new JSONArray().put(args).toString();
            return write ? ApplicationHook.requestString(method, body, 1, 0) : ApplicationHook.requestString(method, body);
        });
        current();
        JSONObject result = MyUtils.newJSONObject(raw);
        if ("1009".equals(result.optString("errorCode"))) {
            RuntimeInfo.getInstance().put("OtherTask.nextRun", System.currentTimeMillis() + 86400000L);
            throw new OtherRequestGate.Denied();
        }
        if (RpcRequestGuard.isFailure(result)) return null;
        // programInvoke reports results per component, not necessarily at the root.
        if ("alipay.imasp.program.programInvoke".equals(method) && result.optJSONObject("components") != null) return result;
        return Boolean.TRUE.equals(result.opt("success")) || Boolean.TRUE.equals(result.opt("isSuccess")) ? result : null;
    }

    private JSONObject lottery(String suffix, JSONObject args, boolean write) throws Exception {
        JSONObject root = call(LOTTERY + suffix, args, write);
        return root != null && "10000001".equals(root.optString("code")) ? root.optJSONObject("data") : null;
    }

    private JSONObject camp() throws Exception {
        JSONObject data = lottery("camp.query", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
        return data != null && Boolean.TRUE.equals(data.opt("active")) ? data : null;
    }

    private JSONObject lotteryTask(String play) throws Exception {
        JSONObject data = lottery("taskConsult", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
        return unique(data == null ? null : data.optJSONArray("taskList"), "playId", play);
    }

    void shenQuan(boolean sign, boolean tasks, boolean draw, String location) throws Exception {
        if (!enabled("shenQuan") || !(sign || tasks || draw)) return;
        JSONObject camp = camp();
        if (camp == null) return;
        if (sign && !checked(camp)) {
            JSONObject consult = lottery("checkInConsult", MyUtils.newJSONObject(), false);
            if (consult != null && Boolean.TRUE.equals(consult.opt("triggerCheckIn"))) {
                if (!reserve("shenQuan", "sign")) return;
                JSONObject ack = call(LOTTERY + "checkIn", MyUtils.newJSONObject(), true);
                JSONObject after = camp();
                if (ack == null || !"10000001".equals(ack.optString("code")) || after == null || !checked(after) || !confirmed("shenQuan", "神券团购签到")) return;
            }
        }
        if (tasks) {
            JSONObject data = lottery("taskConsult", MyUtils.newJSONObject().put("hitLotteryMachineV2Delivery", true), false);
            JSONArray list = data == null ? null : data.optJSONArray("taskList");
            if (list == null || list.length() > 100) return;
            for (int i = 0; i < list.length(); i++) {
                JSONObject initial = list.optJSONObject(i);
                String id = text(initial, "playId");
                JSONObject row = id.isEmpty() ? null : unique(list, "playId", id);
                if (row == null || !lotterySafe(row)) continue;
                row = lotteryTask(id);
                if (row == null || !lotterySafe(row) || !lotteryContract(initial).equals(lotteryContract(row))) continue;
                JSONObject props = row.optJSONObject("taskExtProps");
                long seconds = count(props, "browseTime");
                if ("INIT".equals(row.optString("taskStatus"))) {
                    if (seconds < 1 || seconds > 300) continue;
                    if (!reserve("shenQuan", "trigger:" + id)) return;
                    JSONObject ack = call("alipay.asset.promplaymatrix.play.trigger", MyUtils.newJSONObject().put("bizNo", UUID.randomUUID().toString()).put("playId", id), true);
                    waitSeconds(seconds);
                    JSONObject after = lotteryTask(id);
                    if (ack == null || after == null || !lotteryContract(row).equals(lotteryContract(after))
                            || !"TO_RECEIVE".equals(after.optString("taskStatus")) || !confirmed("shenQuan", "神券团购任务")) return;
                    row = after;
                }
                if ("TO_RECEIVE".equals(row.optString("taskStatus"))) {
                    JSONObject before = camp();
                    long remaining = count(before, "remainingCount");
                    if (remaining < 0 || !reserve("shenQuan", "reward:" + id)) return;
                    JSONObject ack = call("alipay.asset.promplaymatrix.play.prize.receive", MyUtils.newJSONObject().put("bizNo", UUID.randomUUID().toString()).put("playId", id), true);
                    JSONObject after = lotteryTask(id), amount = camp();
                    if (ack == null || after == null || !lotteryContract(row).equals(lotteryContract(after))
                            || !Set.of("RECEIVED", "DONE").contains(after.optString("taskStatus"))
                            || count(amount, "remainingCount") <= remaining || !confirmed("shenQuan", "神券团购抽奖机会")) return;
                }
            }
        }
        if (!draw) return;
        JSONObject args = drawLocation(location);
        if (args == null) { Log.record("神券团购抽奖：定位JSON缺少必要字段或格式不正确，本轮跳过"); return; }
        for (int i = 0; i < budget; i++) {
            JSONObject before = camp();
            long remaining = count(before, "remainingCount"), consumed = count(before, "dayConsumeCount");
            if (remaining <= 0 || consumed < 0 || !reserve("shenQuan", "draw:" + consumed)) return;
            args.put("timestamp", System.currentTimeMillis());
            JSONObject ack = lottery("receive", args, true), after = camp();
            if (ack == null || count(after, "remainingCount") != remaining - 1
                    || count(after, "dayConsumeCount") != consumed + 1 || !confirmed("shenQuan", "神券团购抽奖")) return;
        }
    }

    private static boolean checked(JSONObject camp) {
        JSONObject ext = camp == null ? null : camp.optJSONObject("extInfo");
        return ext != null && (Boolean.TRUE.equals(ext.opt("hasCheckedIn")) || "true".equals(ext.opt("hasCheckedIn")));
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
        if (title.isEmpty() || type.isEmpty() || unsafe(title + " " + type + " " + template)) return false;
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
