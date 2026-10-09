package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import static io.github.aw1y2z.sesame.model.task.other.SjActivityTasks.text;
import static io.github.aw1y2z.sesame.model.task.other.SjActivityTasks.count;

/** Protocols from 2-2-2-2; source and fixed activity identifiers are recorded in docs. */
final class FriendActivityTasks {
    private static final String P2E = "com.alipay.gamecenteruprod.biz.rpc.", SOURCE = "ch_appcenter__chsub_9patch";
    private static final String CARD = "com.alipay.pcreditbfweb.", APPLET = "PCCP_2024120204226387059";
    private static final String SIGN_PLAY = "PCCP_2025042404228380845", PROGRESS_PLAY = "PCCP_2024091804225609615";
    private static final String QUEUE = "other::luckyCardQueue";
    private static final String RECEIPT = "sjActivityReceipt::";
    private final SjActivityTasks sj;
    private final Set<String> checkedCards = new HashSet<>();
    private boolean luckyRowsComplete;

    FriendActivityTasks(SjActivityTasks sj) { this.sj = sj; }

    void clearReceipts() {
        int cleared = 0;
        sj.current();
        for (String key : RuntimeInfo.getInstance().keysStartingWith(RECEIPT)) {
            String domain = key.substring(RECEIPT.length());
            if (domain.equals("luckySign") || domain.equals("luckyProgress") || domain.startsWith("luckyTask::")
                    || domain.startsWith("luckyOpen::") || domain.startsWith("p2eBrowse::")) {
                if (sj.discard(domain, "用户核对后手动恢复；本操作不发送任务请求")) cleared++;
            }
        }
        Log.record("赚金币/好运卡：已手动清理" + cleared + "条未确认回执，待开卡保留，后续按开关和预算执行");
    }

    private boolean oldReceipt(String domain) {
        JSONObject receipt = MyUtils.newJSONObject(sj.pending(domain));
        long day = count(receipt, "day");
        if (!text(receipt, "uid").equals(UserIdMap.getCurrentUid())) return false;
        if (day < 20000101 || day >= SjActivityTasks.date()) return false;
        try { java.time.LocalDate.of((int) day / 10000, (int) day / 100 % 100, (int) day % 100); return true; }
        catch (java.time.DateTimeException invalid) { return false; }
    }

    private void expireTaskReceipts(String prefix) {
        sj.current();
        for (String key : RuntimeInfo.getInstance().keysStartingWith(RECEIPT + prefix)) {
            String domain = key.substring(RECEIPT.length());
            if (oldReceipt(domain)) sj.discard(domain, "每日任务已跨天，旧回执作废，按今日任务状态继续");
        }
    }

    private static JSONObject data(JSONObject root) { return root == null ? null : root.optJSONObject("data"); }
    private static JSONObject result(JSONObject root) { JSONObject data = data(root); return data == null ? null : data.optJSONObject("result"); }
    private static String label(JSONObject row, String field) {
        String value = text(row, field).replaceAll("[\\r\\n\\t]", " ");
        return value.isEmpty() ? "未命名任务" : value.substring(0, Math.min(80, value.length()));
    }
    private static void success(String message) { Log.other("✅ " + message); Log.record("✅ " + message); }
    private static boolean oneOf(String state, String... values) { for (String value : values) if (value.equals(state)) return true; return false; }
    private static boolean validRows(JSONArray rows, String key) {
        if (rows == null || rows.length() > 100) return false;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            String id = text(rows.optJSONObject(i), key);
            if (id.isEmpty() || !ids.add(id)) return false;
        }
        return true;
    }

    private JSONArray p2eRows() throws Exception {
        JSONObject args = MyUtils.newJSONObject().put("__git", "9e159d58cce04c13a").put("appId", "2021003125685383")
                .put("deviceLevel", "high").put("frontEndVersion", "20260820").put("p2eVersion", "")
                .put("panelLaunchableCheckMap", MyUtils.newJSONObject().put("SET_HEAD_TASK", false))
                .put("sessionId", String.valueOf(System.currentTimeMillis())).put("setHeadPanelCheck", false)
                .put("source", SOURCE).put("subscribePanelCheck", true).put("unityDeviceLevel", "high");
        JSONObject data = data(sj.call(P2E + "p2e.queryTaskList", args, false));
        JSONObject module = data == null ? null : data.optJSONObject("platformGameTaskModule");
        JSONArray rows = module == null ? null : module.optJSONArray("platformTaskList");
        if (!validRows(rows, "taskId")) { Log.record("赚金币浏览：任务列表缺失、重复或结构异常，未提交操作"); return null; }
        return rows;
    }

    private JSONObject p2eArgs(JSONObject row) throws Exception {
        return MyUtils.newJSONObject().put("actionChannel", "taskList").put("activityId", "P2E_PLATFORM_TASK")
                .put("source", SOURCE).put("taskId", text(row, "taskId")).put("taskToken", text(row, "taskToken"));
    }

    void p2eBrowse() throws Exception {
        if (DailyTask.skip("p2eBrowse", "赚金币浏览")) return;
        if (!sj.enabled("p2eBrowse")) return;
        Log.record("赚金币浏览：开始查询，仅处理VIEW_TASK，不兑换现金");
        JSONArray rows = p2eRows();
        if (rows == null) return;
        expireTaskReceipts("p2eBrowse::");
        int eligible = 0, completed = 0;
        boolean completeList = true;
        Set<String> unfinished = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            sj.current();
            JSONObject row = rows.optJSONObject(i);
            String id = text(row, "taskId"), title = label(row, "title"), state = text(row, "taskStatus");
            if ("VIEW_TASK".equals(text(row, "actionType")) && text(row, "taskToken").isEmpty()) completeList = false;
            if (!"VIEW_TASK".equals(text(row, "actionType")) || text(row, "taskToken").isEmpty()
                    || SjActivityTasks.unsafe(title.replace("支付宝", "").replaceAll("(?i)alipay", ""))) continue;
            eligible++;
            unfinished.add(id);
            String domain = "p2eBrowse::" + id, done = "other::p2eBrowseDone::" + id;
            String signup = "other::p2eBrowseSignup::" + id, sent = "other::p2eBrowseSent::" + id;
            if (Status.hasFlagToday(done)) continue;
            JSONObject receipt = MyUtils.newJSONObject(sj.pending(domain));
            String action = text(receipt, "action");
            boolean terminal = oneOf(state, "RECEIVED", "DONE", "SUCCESS");
            boolean ready = oneOf(state, "COMPLETED", "COMPLETE", "FINISHED");
            boolean signed = oneOf(state, "SIGNUP_COMPLETED", "SIGNUP_COMPLETE", "NOT_DONE", "IN_COMPLETE", "PROCESSING");
            if (!sj.pending(domain).isEmpty()) {
                if (terminal || "complete".equals(action) && ready || "signup".equals(action) && (signed || ready)) {
                    if (!sj.accepted(domain, "赚金币[" + title + "]列表确认阶段推进")) continue;
                    if (terminal && "receive".equals(action)) success("赚金币[" + title + "]列表已确认领奖，金币数未返回");
                } else { Log.record("赚金币[" + title + "]：上次" + action + "结果未确认，当前状态=" + SjActivityTasks.responseField(row, "taskStatus") + "，只跳过此任务；核对后可在其他任务手动恢复未确认操作"); continue; }
            }
            if (terminal) { Status.flagToday(done); continue; }
            if (!ready && !signed && !oneOf(state, "UN_SIGNUP", "NONE_SIGNUP")) {
                Log.record("赚金币[" + title + "]：未知状态=" + SjActivityTasks.responseField(row, "taskStatus")); continue;
            }
            if (!ready && !signed && !Status.hasFlagToday(signup)) {
                JSONObject ack = sj.write(domain, "signup", P2E + "platformTaskSignUp", p2eArgs(row));
                if (ack == null || !sj.accepted(domain, "赚金币[" + title + "]报名")) continue;
                Status.flagToday(signup);
            }
            if (!ready && !Status.hasFlagToday(sent)) {
                // shortcut: source uses 15 seconds; prefer an explicit server browseTime when supplied.
                long seconds = row.has("browseTime") ? count(row, "browseTime") : 15;
                if (seconds < 1 || seconds > 300) { Log.record("赚金币[" + title + "]：浏览时长无效，未上报"); continue; }
                Log.record("赚金币[" + title + "]：等待" + seconds + "秒后上报");
                sj.waitSeconds(seconds);
                JSONObject ack = sj.write(domain, "complete", P2E + "platformTaskComplete", p2eArgs(row));
                if (ack == null || !sj.accepted(domain, "赚金币[" + title + "]浏览上报")) continue;
                Status.flagToday(sent);
                // The source receives immediately after an accepted completion; no extra wait or cooldown.
                ready = true;
            }
            if (!ready) { Log.record("赚金币[" + title + "]：已上报，列表仍未到领奖状态，后续轮次继续查询"); continue; }
            JSONObject args = p2eArgs(row).put("__git", "9e159d58cce04c13a").put("appId", "2021003125685383")
                    .put("oriChInfo", SOURCE).put("p2eVersion", "").put("taskType", "PLATFORM_TRAN_TASK");
            JSONObject ack = sj.write(domain, "receive", P2E + "p2e.gameP2eTaskReceive", args);
            long coins = count(data(ack), "coinAmount"), listed = count(row, "goldCoinAmount");
            if (ack != null) {
                if (!sj.accepted(domain, "赚金币[" + title + "]领奖")) continue;
                Status.flagToday(done); completed++; success("赚金币[" + title + "]领奖成功，"
                        + (coins >= 0 ? "获得" + coins + "金币" : listed >= 0 ? "任务标示奖励" + listed + "金币（响应未返回金额）" : "金币数未返回"));
            } else Log.record("赚金币[" + title + "]：领奖未成功确认，后续从列表核对；明确拒绝已解除回执");
        }
        Log.record("赚金币浏览：本轮候选" + eligible + "项，确认领奖" + completed + "项");
        if (!completeList) return;
        for (String id : unfinished) if (!Status.hasFlagToday("other::p2eBrowseDone::" + id) || !sj.pending("p2eBrowse::" + id).isEmpty()) return;
        sj.current();
        DailyTask.done("p2eBrowse");
    }

    private JSONObject rebate(String play, String scene, String action, String domain) throws Exception {
        JSONObject args = MyUtils.newJSONObject().put("behavior", action == null ? "consult" : "trigger").put("bizDomain", "creditCard")
                .put("bizNo", System.currentTimeMillis() + "_" + play + "_" + scene).put("bizScene", scene)
                .put("extInfo", MyUtils.newJSONObject().put("version", 1)).put("playId", play);
        return result(action != null ? sj.write(domain, action, CARD + "gameplay.rebate", args) : sj.call(CARD + "gameplay.rebate", args, false));
    }

    private static JSONArray prizeTickets(JSONObject result) {
        if (result == null) return null;
        Object raw = result.opt("prizeDetails");
        JSONObject prize = raw instanceof JSONObject ? (JSONObject) raw : MyUtils.newJSONObject(raw instanceof String ? (String) raw : "");
        JSONArray direct = prize.optJSONArray("tickets");
        if (direct != null) return direct;
        JSONArray tickets = new JSONArray();
        for (Iterator<String> keys = prize.keys(); keys.hasNext();) {
            JSONObject group = prize.optJSONObject(keys.next());
            JSONArray items = group == null ? null : group.optJSONArray("tickets");
            if (items == null) continue;
            for (int i = 0; i < items.length(); i++) tickets.put(items.opt(i));
        }
        return tickets;
    }

    private JSONObject cardQueue() throws Exception {
        sj.current();
        String raw = RuntimeInfo.getInstance().getString(QUEUE);
        JSONObject queue = MyUtils.newJSONObject(raw);
        if (!raw.isEmpty() && queue.length() == 0 && !raw.matches("\\s*\\{\\s*}\\s*")) {
            Log.record("好运卡：待开卡记录无法解析，未发送开卡"); return null;
        }
        int today = SjActivityTasks.date();
        java.time.LocalDate cutoff = java.time.LocalDate.of(today / 10000, today / 100 % 100, today % 100).minusDays(7);
        int earliest = cutoff.getYear() * 10000 + cutoff.getMonthValue() * 100 + cutoff.getDayOfMonth();
        boolean changed = false;
        for (Iterator<String> ids = queue.keys(); ids.hasNext();) {
            String id = ids.next();
            if (Boolean.TRUE.equals(queue.opt(id))) { queue.put(id, today); changed = true; }
            else if (count(queue, id) >= 20000101 && count(queue, id) < earliest) { ids.remove(); changed = true; }
        }
        if (changed && !RuntimeInfo.getInstance().putVerified(QUEUE, queue.toString())) return null;
        return queue;
    }

    private boolean saveCards(JSONArray tickets) throws Exception {
        if (!validRows(tickets, "id") || tickets.length() == 0) return false;
        JSONObject queue = cardQueue();
        if (queue == null) return false;
        for (int i = 0; i < tickets.length(); i++) {
            String id = text(tickets.optJSONObject(i), "id");
            if (!queue.has(id)) queue.put(id, false);
        }
        return RuntimeInfo.getInstance().putVerified(QUEUE, queue.toString());
    }

    private boolean signToday(JSONObject consult) {
        Object raw = consult == null ? null : consult.opt("extInfo");
        JSONObject ext = raw instanceof JSONObject ? (JSONObject) raw : MyUtils.newJSONObject(raw instanceof String ? (String) raw : "");
        String last = text(ext, "lastSignInTime");
        int day = SjActivityTasks.date();
        String today = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", day / 10000, day / 100 % 100, day % 100);
        if (last.matches(".*(?:Z|[+-][0-9]{2}:[0-9]{2})$")) {
            try { return java.time.OffsetDateTime.parse(last).atZoneSameInstant(java.time.ZoneId.of("GMT+8")).toLocalDate().toString().equals(today); }
            catch (java.time.DateTimeException malformed) { return false; }
        }
        return last.equals(today) || last.matches(today + "[ T][0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,3})?");
    }

    void luckyCard() throws Exception {
        if (!sj.enabled("luckyCard")) return;
        Log.record("好运卡：开始签到、进度领卡及任务开卡");
        for (String daily : new String[]{"luckySign", "luckyProgress"}) {
            if (oldReceipt(daily)) sj.discard(daily, "每日玩法已跨天，旧回执作废，按今日状态继续");
        }
        openCards();
        String domain = "luckySign";
        JSONObject consult = Status.hasFlagToday("other::luckySign") && !DailyTask.isManual() ? null
                : rebate(SIGN_PLAY, "HAOYUNKA_SIGN_IN", null, domain);
        if (consult == null && Status.hasFlagToday("other::luckySign")) Log.record("好运卡签到：当天已经成功执行");
        if (consult != null && !Status.hasFlagToday("other::luckySign")) {
            if (signToday(consult)) {
                if (sj.pending(domain).isEmpty() || saveCards(prizeTickets(consult)) && sj.accepted(domain, "好运卡签到回查"))
                    Status.flagToday("other::luckySign");
                Log.record("好运卡：今日已签到");
            } else if (oneOf(text(consult, "status"), "NONE_SIGNUP", "SIGNED_UP")) {
                JSONObject ack = rebate(SIGN_PLAY, "HAOYUNKA_SIGN_IN", "trigger", domain);
                if (saveCards(prizeTickets(ack)) && sj.accepted(domain, "好运卡签到领卡")) {
                    Status.flagToday("other::luckySign"); success("好运卡签到成功，卡片已加入待开列表");
                } else Log.record("好运卡签到：未取得有效卡片，未记成功；下轮查询签到状态");
            } else Log.record("好运卡签到：未知状态=" + SjActivityTasks.responseField(consult, "status"));
        }
        openCards();
        luckyProgress();
        openCards();
        luckyTasks();
        openCards();
    }

    private void luckyProgress() throws Exception {
        String domain = "luckyProgress", done = "other::luckyProgress";
        if (Status.hasFlagToday(done)) { Log.record("好运卡进度：当天已经成功执行"); return; }
        // 来源仅使用 trigger；额外进度 consult 在实机失败，不能把外推接口设为执行前提。
        if (!sj.pending(domain).isEmpty()) {
            Log.record("好运卡进度：上次推进结果未确认，来源无进度查询接口；核对后可手动恢复，今日不重发"); return;
        }
        JSONObject saved = MyUtils.newJSONObject(RuntimeInfo.getInstance().getString("other::luckyProgressCount"));
        long recent = count(saved, "day") == SjActivityTasks.date() ? count(saved, "count") : 0;
        if (recent < 0 || recent >= 4) { Log.record("好运卡进度：当日已确认满格或记录无效，停止推进"); return; }
        for (int i = 0; i < 4 && recent < 4; i++) {
            JSONObject ack = rebate(PROGRESS_PLAY, "HAOYUNKA", "progress:" + recent, domain);
            if (ack == null) return;
            long next = count(ack, "recentProcess");
            if ("REWARDED".equals(text(ack, "status"))) {
                if (Boolean.TRUE.equals(ack.opt("collection"))) {
                    if (!saveCards(prizeTickets(ack))) { Log.record("好运卡进度：满格发卡信息缺失，保留回执"); return; }
                    success("好运卡进度奖励已发卡");
                }
                if (sj.accepted(domain, "好运卡进度领奖状态")) Status.flagToday(done);
                return;
            }
            if (!"SIGNED_UP".equals(text(ack, "status")) || next <= recent || next > 4) {
                Log.record("好运卡进度：提交后未确认进度增加，保留回执；来源无查询接口，需核对后手动恢复"); return;
            }
            if (!RuntimeInfo.getInstance().putVerified("other::luckyProgressCount",
                    MyUtils.newJSONObject().put("day", SjActivityTasks.date()).put("count", next).toString())) {
                Log.record("好运卡进度：服务端已推进，但本地进度保存失败，保留回执"); return;
            }
            if (!sj.accepted(domain, "好运卡进度" + recent + "→" + next + "/4")) return;
            recent = next;
        }
    }

    private JSONArray luckyRows() throws Exception {
        luckyRowsComplete = false;
        JSONObject result = result(sj.call(CARD + "sdk.task.query", MyUtils.newJSONObject().put("appletId", APPLET)
                .put("bizScene", "HAOYUNKA_DAILY").put("bizSceneFrom", "creditCard").put("extInfo", MyUtils.newJSONObject().put("version", 1))
                .put("requestFrom", "pccp"), false));
        JSONArray rows = result == null ? null : result.optJSONArray("taskListResult");
        if (rows == null || rows.length() > 100) {
            Log.record("好运卡任务：列表校验失败，data.result对象=" + (result != null)
                    + "，taskListResult类型=" + (result == null || result.opt("taskListResult") == null ? "缺失" : result.opt("taskListResult").getClass().getSimpleName())
                    + "，条目数=" + (rows == null ? -1 : rows.length())
                    + "；未提交任务");
            return null;
        }
        java.util.Map<String, JSONObject> tasks = new java.util.LinkedHashMap<>();
        Set<String> conflicts = new HashSet<>();
        int missing = 0, duplicate = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String id = text(row, "taskId");
            if (id.isEmpty()) { missing++; continue; }
            JSONObject previous = tasks.get(id);
            if (previous == null) { tasks.put(id, row); continue; }
            duplicate++;
            // 只有执行所需字段一致才合并，冲突仅隔离该ID，不能阻断其他任务。
            for (String field : new String[]{"taskStatus", "taskType", "taskShowInfo"}) {
                if (!java.util.Objects.toString(previous.opt(field), "").equals(java.util.Objects.toString(row.opt(field), "")))
                    conflicts.add(id);
            }
        }
        JSONArray unique = new JSONArray();
        for (java.util.Map.Entry<String, JSONObject> task : tasks.entrySet()) {
            if (!conflicts.contains(task.getKey())) unique.put(task.getValue());
        }
        Log.record("好运卡任务：查询成功，原始条目=" + rows.length() + "，有效唯一任务=" + unique.length()
                + "，重复条目=" + duplicate + "，无有效ID=" + missing + "，冲突ID=" + conflicts.size()
                + "；重复仅处理一次，无效或冲突仅跳过对应项，其余任务继续");
        luckyRowsComplete = missing == 0 && conflicts.isEmpty();
        return unique;
    }

    private JSONObject trigger(String id, String stage, String domain) throws Exception {
        JSONObject ext = MyUtils.newJSONObject();
        if ("send".equals(stage)) ext.put("name", "任务奖励").put("ticketCampId", "CP182329534").put("version", 1);
        JSONObject args = MyUtils.newJSONObject().put("appletId", id).put("bizScene", "HAOYUNKA_DAILY").put("bizSceneFrom", "creditCard")
                .put("extInfo", ext).put("needleParam", MyUtils.newJSONObject()).put("outBizNo", id + System.currentTimeMillis() % 100000000L)
                .put("pccpId", APPLET).put("retryFlag", true).put("stageCode", stage).put("taskCentId", "AP16247630");
        if ("send".equals(stage)) args.put("triggerRule", "TASK_LUCKY_TICKET_TRIGGER");
        return result(sj.write(domain, stage, CARD + "sdk.task.trigger", args));
    }

    private static JSONArray taskTickets(Object raw) {
        if (raw instanceof JSONArray) return (JSONArray) raw;
        if (raw instanceof String && ((String) raw).trim().startsWith("[")) {
            try { return new JSONArray((String) raw); }
            catch (Exception malformed) { Log.record("好运卡任务：卡片列表解析失败"); }
        }
        return null;
    }

    private void luckyTasks() throws Exception {
        if (DailyTask.skip("luckyTasks", "好运卡每日任务")) return;
        JSONArray rows = luckyRows();
        if (rows == null) return;
        Set<String> unfinished = new HashSet<>();
        expireTaskReceipts("luckyTask::");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            String id = text(row, "taskId"), show = text(row, "taskShowInfo"), state = text(row, "taskStatus");
            String domain = "luckyTask::" + id, done = "other::luckyTask::" + id;
            if (!sj.pending(domain).isEmpty()) unfinished.add(id);
            if (Status.hasFlagToday(done + "::notReady")) continue;
            JSONObject pending = MyUtils.newJSONObject(sj.pending(domain));
            if ("send".equals(text(pending, "action")) && saveCards(taskTickets(row.opt("taskShowInfo")))) {
                if (sj.accepted(domain, "好运卡任务领卡回查")) { Status.flagToday(done); success("好运卡任务领卡已回查确认"); }
                continue;
            }
            JSONObject info = MyUtils.newJSONObject(show);
            String title = (text(info, "title") + " " + text(info, "subTitle")).trim();
            JSONArray titles = info.optJSONArray("titleList");
            if (titles != null) for (int j = 0; j < Math.min(20, titles.length()); j++) {
                Object value = titles.opt(j);
                if (value instanceof String) title += " " + value;
            }
            if (!title.contains("好运卡") || SjActivityTasks.unsafe(title.replace("支付宝", "").replaceAll("(?i)alipay", "") + " " + text(row, "taskType")) || Status.hasFlagToday(done)) continue;
            if (oneOf(state, "RECEIVED", "RECEIVE_SUCCESS", "DONE", "SUCCESS")) continue;
            unfinished.add(id);
            title = title.replaceAll("[\\r\\n\\t]", " ");
            title = title.substring(0, Math.min(title.length(), 80));
            if ("signup".equals(text(pending, "action")) && oneOf(state, "NOT_DONE", "IN_COMPLETE", "SIGNUP_COMPLETE", "TODO"))
                if (!sj.accepted(domain, "好运卡[" + title + "]报名回查")) continue;
            if ("NONE_SIGNUP".equals(state)) {
                JSONObject ack = trigger(id, "signup", domain);
                if (ack == null || !sj.accepted(domain, "好运卡[" + title + "]报名")) continue;
                state = "SIGNUP_COMPLETE";
            }
            if (!oneOf(state, "NOT_DONE", "IN_COMPLETE", "SIGNUP_COMPLETE", "TODO")) continue;
            JSONObject ack = trigger(id, "send", domain);
            Object raw = ack == null ? null : ack.opt("taskShowInfo");
            JSONArray tickets = taskTickets(raw);
            if (saveCards(tickets) && sj.accepted(domain, "好运卡[" + title + "]领卡")) {
                Status.flagToday(done); success("好运卡[" + title + "]获得" + tickets.length() + "张卡");
            } else if (raw instanceof String && ((String) raw).trim().startsWith("{") && MyUtils.newJSONObject((String) raw).length() > 0) {
                if (sj.accepted(domain, "好运卡[" + title + "]未达成任务，服务端未发卡")) Status.flagToday(done + "::notReady");
            } else Log.record("好运卡[" + title + "]：领卡未确认；如有未确认回执，核对后可在其他任务手动恢复");
        }
        if (!luckyRowsComplete) return;
        for (String id : unfinished) if (!Status.hasFlagToday("other::luckyTask::" + id)
                && !Status.hasFlagToday("other::luckyTask::" + id + "::notReady") || !sj.pending("luckyTask::" + id).isEmpty()) return;
        sj.current();
        DailyTask.done("luckyTasks");
    }

    private void openCards() throws Exception {
        sj.current();
        JSONObject queue = cardQueue();
        if (queue == null) return;
        for (Iterator<String> ids = queue.keys(); ids.hasNext();) {
            String id = ids.next(), domain = "luckyOpen::" + id;
            if (!Boolean.FALSE.equals(queue.opt(id)) || !checkedCards.add(id)) continue;
            JSONObject card;
            if (!sj.pending(domain).isEmpty()) {
                JSONObject root = sj.call("com.alipay.pcreditcardmarket.consultLuckyCard", MyUtils.newJSONObject()
                        .put("benefitScene", "").put("bizScene", "HAOYUNKA").put("chInfo", "").put("channel", "test")
                        .put("pccpId", "PCCP_2025062404229513342"), false);
                JSONObject result = root == null ? null : root.optJSONObject("result");
                JSONObject ticket = result == null ? null : result.optJSONObject("ticketResult");
                card = SjActivityTasks.unique(ticket == null ? null : ticket.optJSONArray("openCards"), "id", id);
            } else {
                JSONObject root = sj.write(domain, "open", "com.alipay.pcreditcardmarket.openLuckyCard",
                        MyUtils.newJSONObject().put("cardIds", new JSONArray().put(id)).put("requestFrom", "pcreditcardweb"));
                JSONObject result = root == null ? null : root.optJSONObject("result");
                JSONArray cards = result == null ? null : result.optJSONArray("openCards");
                card = cards == null || cards.length() != 1 ? null : cards.optJSONObject(0);
            }
            JSONObject info = card == null ? null : card.optJSONObject("cardInfo");
            if (info == null || info.length() == 0 || (!text(card, "id").isEmpty() && !id.equals(text(card, "id")))) {
                Log.record("好运卡：开卡未取得对应卡片结果，其他卡继续；未知回执核对后可在其他任务手动恢复"); continue;
            }
            queue.put(id, SjActivityTasks.date());
            sj.current();
            if (!RuntimeInfo.getInstance().putVerified(QUEUE, queue.toString())) return;
            if (!sj.accepted(domain, "好运卡开卡")) return;
            String amount = String.valueOf(info.opt("amount"));
            success("好运卡开卡成功[" + label(info, "title") + "]，等级=" + SjActivityTasks.responseField(info, "cardLevel")
                    + (amount.matches("[0-9]{1,9}(\\.[0-9]{1,2})?") ? "，奖励数值=" + amount : ""));
        }
    }
}
