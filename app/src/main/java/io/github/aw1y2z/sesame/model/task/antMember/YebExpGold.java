package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.YebTaskIdMap;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

final class YebExpGold {
    static void run(boolean sign, boolean rewards, Set<String> blacklist) {
        if (!sign && !rewards) return;
        String uid = UserIdMap.getCurrentUid();
        int day = businessDay();
        if (uid == null || uid.isEmpty()) return;
        try {
            JSONObject main = queryMain(null);
            if (main == null) return;
            if (sign && !blacklist.contains("余额宝体验金签到")) signIn(main);
            if (!rewards) return;
            Map<String, JSONObject> pending = pendingRewards(queryMain(null));
            if (pending == null || pending.size() > 100) return;
            int count = 0;
            for (Map.Entry<String, JSONObject> entry : pending.entrySet()) {
                String id = entry.getKey();
                JSONObject ext = entry.getValue().optJSONObject("ext");
                JSONObject detail = ext == null ? null : ext.optJSONObject("TASK_MORPHO_DETAIL");
                String title = detail == null ? id : detail.optString("title", id);
                String key = "member::yebRewardAttempt::" + id;
                if (blacklist.contains(id) || blacklist.contains(title) || Status.hasFlagToday(key)
                        || !RuntimeInfo.getInstance().getString("member::yebAliasReceipt::" + id).isEmpty()) continue;
                // ponytail: 每轮最多50项已完成奖励；不完成或报名新任务。
                if (++count > 50) return;
                Map<String, JSONObject> fresh = pendingRewards(queryMain(null));
                if (fresh == null) return;
                if (!fresh.containsKey(id)) continue;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || businessDay() != day) return;
                Status.flagToday(key);
                JSONObject ack = MyUtils.newJSONObject(AntMemberRpcCall.triggerYebExpGoldReward(id));
                Map<String, JSONObject> after = pendingRewards(queryMain(id));
                if (!uid.equals(UserIdMap.getCurrentUid()) || businessDay() != day) return;
                if (success(ack) && after != null && !after.containsKey(id)) {
                    Log.other("余额宝体验金💰已完成任务奖励回查成功#" + title);
                } else {
                    Log.record("余额宝体验金奖励未确认，本日不重复尝试#" + id);
                    return;
                }
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("YebExpGold", "体验金签到/领奖失败", e); }
    }

    static void runTasks(Set<String> selected, Set<String> blacklist, int budget) {
        String uid = UserIdMap.getCurrentUid();
        int day = businessDay();
        if (uid == null || uid.isEmpty()) return;
        try {
            Map<String, List<JSONObject>> groups = taskGroups();
            if (groups == null) return;
            Map<String, JSONObject> pending = pendingRewards(queryMain(null));
            if (pending == null || !uid.equals(UserIdMap.getCurrentUid()) || businessDay() != day) return;
            YebTaskIdMap.load(uid);
            YebTaskIdMap.clear();
            for (List<JSONObject> rows : groups.values()) for (JSONObject row : rows) {
                String id = row.optString("taskId"), title = field(row, "title", "taskMainTitle");
                String eligibility = financialTask(row) ? "已排除：开通/转账/付款类" : pending.containsKey(id) ? "已完成奖励" : browseTask(row) ? "浏览类型" : "仅手动/类型未验证";
                YebTaskIdMap.add(id, (title.isEmpty() ? id : title) + "（" + eligibility + "）");
            }
            if (!uid.equals(UserIdMap.getCurrentUid()) || businessDay() != day) return;
            if (!YebTaskIdMap.save(uid) || selected == null || selected.isEmpty() || budget <= 0) return;
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            for (Map.Entry<String, List<JSONObject>> entry : groups.entrySet()) {
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || businessDay() != day) return;
                String fingerprint = entry.getKey();
                List<JSONObject> aliases = entry.getValue();
                if (!selected(aliases, selected) || blocked(aliases, blacklist)) continue;
                String receiptId = Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
                String key = "member::yebTaskReceipt::" + receiptId;
                JSONObject action = aliases.get(0);
                for (JSONObject alias : aliases) if ("promo".equals(alias.optString("_yebSource"))) action = alias;
                String id = action.optString("taskId"), source = action.optString("_yebSource");
                boolean reward = false;
                for (JSONObject alias : aliases) if (pending.containsKey(alias.optString("taskId"))) reward = true;
                if (received(action)) {
                    if (!clearReceipt(runtime, key, aliases)) return;
                    continue;
                }
                // Explicit browse metadata is required: selecting an ID never authorizes opening accounts or moving money.
                if (!reward && !browseTask(action)) continue;
                if (!reward && !signupState(status(action)) && !Set.of("not_done", "NOT_DONE", "WAIT_COMPLETE", "PROCESSING", "SIGNUP_COMPLETE", "SIGNUP_COMPLETED").contains(status(action))) continue;
                if (!runtime.getString(key).isEmpty()) {
                    Log.record("余额宝体验金任务旧回执未确认，保留待查");
                    continue;
                }
                String flag = "member::yebTaskAttempt::" + receiptId;
                if (Status.hasFlagToday(flag)) continue;
                boolean attempted = false;
                for (JSONObject alias : aliases) if (Status.hasFlagToday("member::yebRewardAttempt::" + alias.optString("taskId"))) attempted = true;
                if (attempted) continue;
                Map<String, List<JSONObject>> fresh = taskGroups();
                List<JSONObject> current = fresh == null ? null : fresh.get(fingerprint);
                JSONObject task = findTask(current, id, source);
                if (task == null || blocked(current, blacklist) || !selected(current, selected) || !status(task).equals(status(action))) return;
                Map<String, JSONObject> freshPending = pendingRewards(queryMain(null));
                if (freshPending == null) return;
                if (reward) {
                    boolean available = false;
                    for (JSONObject alias : current) if (freshPending.containsKey(alias.optString("taskId"))) available = true;
                    if (!available) continue;
                } else if (!browseTask(task)) continue;
                TimeUtil.sleep(0);
                if (businessDay() != day || !uid.equals(UserIdMap.getCurrentUid())) return;
                int slot = 0;
                // ponytail: cap at 50 server actions/day; increase only with a proven campaign allowance.
                while (slot < Math.min(budget, 50) && Status.hasFlagToday("member::yebTaskBudget::" + slot)) slot++;
                if (slot >= Math.min(budget, 50)) return;
                String order = id + "-" + System.currentTimeMillis() + "-";
                JSONObject receipt = MyUtils.newJSONObject().put("taskId", id).put("source", source).put("order", order).put("day", day);
                if (!runtime.putVerified(key, receipt.toString())) return;
                for (JSONObject alias : current) if (!runtime.putVerified("member::yebAliasReceipt::" + alias.optString("taskId"), order)) return;
                TimeUtil.sleep(0);
                if (businessDay() != day || !uid.equals(UserIdMap.getCurrentUid())) return;
                Status.flagToday(flag);
                Status.flagToday("member::yebTaskBudget::" + slot);
                for (JSONObject alias : current) Status.flagToday("member::yebRewardAttempt::" + alias.optString("taskId"));
                JSONObject accepted = actionTask(id, source, order, reward || signupState(status(task)));
                JSONObject after = readTask(id, source);
                TimeUtil.sleep(0);
                if (businessDay() != day || !uid.equals(UserIdMap.getCurrentUid())) return;
                if (success(accepted) && after != null && fingerprint.equals(fingerprint(after)) && received(after)) {
                    if (!clearReceipt(runtime, key, current)) return;
                    Log.other("余额宝体验金💰任务发奖状态回查成功");
                } else {
                    Log.record("余额宝体验金任务发奖未确认，保留回执且不重复提交");
                    return;
                }
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("YebExpGold", "体验金任务失败", e); }
    }

    private static boolean clearReceipt(RuntimeInfo runtime, String key, List<JSONObject> aliases) {
        for (JSONObject alias : aliases) {
            String aliasKey = "member::yebAliasReceipt::" + alias.optString("taskId");
            if (!runtime.getString(aliasKey).isEmpty() && !runtime.putVerified(aliasKey, null)) return false;
        }
        return runtime.getString(key).isEmpty() || runtime.putVerified(key, null);
    }

    private static JSONObject request(String method, JSONObject args, boolean mutation) throws Exception {
        TimeUtil.sleep(0);
        String body = new JSONArray().put(args).toString();
        return MyUtils.newJSONObject(mutation ? ApplicationHook.requestString(method, body, 1, 0)
                : ApplicationHook.requestString(method, body));
    }

    private static JSONObject promoQuery(String id) throws Exception {
        JSONObject args = MyUtils.newJSONObject().put("playEntrance", "HYQ_TASK_LIST_ENTRANCE_2");
        if (id == null) args.put("needTriggerPrize", false).put("playActionCode", "TASK_LIST_CONSULT");
        else args.put("appName", "yebpromobff").put("taskId", id).put("playActionCode", "TASK_STATUS_QUERY");
        JSONObject root = request("com.alipay.yebpromobff.promosdk2024.task." + (id == null ? "query" : "queryTaskByTaskId"), args, false);
        return success(root) ? root.optJSONObject("result") : null;
    }

    private static JSONObject actionTask(String id, String source, String order, boolean trigger) throws Exception {
        if ("promo".equals(source)) return request("com.alipay.yebpromobff.promosdk2024.task.complete", MyUtils.newJSONObject()
                .put("appName", "yebpromobff").put("outBizNo", order).put("playActionCode", "TASK_COMPLETE")
                .put("playEntrance", "HYQ_TASK_LIST_ENTRANCE_2").put("taskId", id), true);
        return request("com.alipay.yebscenebff.promosdk.index.forward", MyUtils.newJSONObject()
                .put("path", trigger ? "task.trigger" : "task.complete")
                .put("params", MyUtils.newJSONObject().put("appletId", "AP12183159").put("taskId", id).put("version", 2)), true);
    }

    private static Map<String, List<JSONObject>> taskGroups() throws Exception {
        Map<String, List<JSONObject>> groups = new LinkedHashMap<>();
        JSONObject promo = promoQuery(null);
        if (promo == null || !collect(promo.optJSONArray("taskDetailList"), "promo", groups, 0)) return null;
        JSONObject main = queryMain(null);
        if (main == null || !collect(main.optJSONObject("taskData"), "main", groups, 0)) return null;
        return groups;
    }

    private static boolean collect(Object node, String source, Map<String, List<JSONObject>> groups, int depth) throws Exception {
        if (node == null || depth > 12 || groups.size() > 100) return false;
        if (node instanceof JSONArray) {
            JSONArray rows = (JSONArray) node;
            if (rows.length() > 100) return false;
            for (int i = 0; i < rows.length(); i++) if (!collect(rows.opt(i), source, groups, depth + 1)) return false;
        } else if (node instanceof JSONObject) {
            JSONObject row = (JSONObject) node;
            String id = row.optString("taskId").trim();
            if (!id.isEmpty() && !status(row).isEmpty()) {
                if (field(row, "title", "taskMainTitle").length() > 512 || field(row, "link", "taskGotoUrl").length() > 2048) return false;
                if (!(row.opt("taskId") instanceof String) || id.length() > 256) return false;
                String fp = fingerprint(row);
                List<JSONObject> list = groups.computeIfAbsent(fp, ignored -> new ArrayList<>());
                for (List<JSONObject> existing : groups.values()) for (JSONObject alias : existing)
                    if (id.equals(alias.optString("taskId")) && (source.equals(alias.optString("_yebSource")) || !fp.equals(fingerprint(alias)))) return false;
                row.put("_yebSource", source);
                list.add(row);
            } else {
                Iterator<String> keys = row.keys();
                while (keys.hasNext()) {
                    Object value = row.opt(keys.next());
                    if ((value instanceof JSONObject || value instanceof JSONArray) && !collect(value, source, groups, depth + 1)) return false;
                }
            }
        }
        return true;
    }

    private static JSONObject readTask(String id, String source) throws Exception {
        Map<String, List<JSONObject>> groups = new LinkedHashMap<>();
        JSONObject data = "promo".equals(source) ? promoQuery(id) : queryMain(id);
        if (data == null || !collect("promo".equals(source) ? data.optJSONArray("taskDetailList") : data.optJSONObject("taskData"), source, groups, 0)) return null;
        for (List<JSONObject> rows : groups.values()) {
            JSONObject row = findTask(rows, id, source);
            if (row != null) return row;
        }
        return null;
    }

    private static JSONObject findTask(List<JSONObject> rows, String id, String source) {
        if (rows != null) for (JSONObject row : rows) if (id.equals(row.optString("taskId")) && source.equals(row.optString("_yebSource"))) return row;
        return null;
    }

    private static String status(JSONObject row) {
        String simplified = row.optString("simplifiedStatus").trim();
        return simplified.isEmpty() ? row.optString("taskProcessStatus").trim() : simplified;
    }

    private static boolean signupState(String status) {
        return Set.of("NOT_SIGN", "SIGN", "NONE_SIGNUP", "UN_SIGNUP", "SIGNUP_EXPIRED").contains(status.toUpperCase(java.util.Locale.ROOT));
    }

    private static boolean received(JSONObject row) {
        return Set.of("RECEIVE_SUCCESS", "HAS_RECEIVED", "RECEIVED").contains(row.optString("taskProcessStatus"));
    }

    private static boolean financialTask(JSONObject row) {
        JSONObject display = row.optJSONObject("taskDisplayInfo"), info = display == null ? null : display.optJSONObject("customInfo");
        JSONObject ext = row.optJSONObject("ext"), detail = ext == null ? null : ext.optJSONObject("TASK_MORPHO_DETAIL");
        StringBuilder text = new StringBuilder();
        for (JSONObject part : new JSONObject[]{row, info, detail, row.optJSONObject("taskExtProps")}) {
            if (part == null) continue;
            for (String key : new String[]{"title", "taskMainTitle", "taskSubTitle", "taskDesc", "description", "taskCategory", "taskCategorize",
                    "taskMainType", "taskType", "taskOperationType"}) text.append(' ').append(part.optString(key));
        }
        String value = text.toString().toLowerCase(java.util.Locale.ROOT);
        for (String word : new String[]{"开通", "开户", "转账", "转帐", "付款", "支付", "充值", "购买", "申购", "签约",
                "transfer", "payment", "pay_task", "open_account", "openaccount", "account_open", "purchase", "subscribe"})
            if (value.contains(word)) return true;
        return false;
    }

    private static boolean browseTask(JSONObject row) {
        JSONObject display = row.optJSONObject("taskDisplayInfo"), info = display == null ? null : display.optJSONObject("customInfo");
        return !financialTask(row) && info != null && "BROWSE_PAGE".equals(info.optString("taskType")) && "BROWSE_TASK".equals(info.optString("taskOperationType"))
                && !"TRANSFER".equals(row.optString("taskCategory")) && !"TRANSFER".equals(info.optString("taskCategorize"))
                && !Set.of("ISSUED_TASK", "EXPLAIN_INTELLIGENCE").contains(row.optString("taskMainType"));
    }

    private static String field(JSONObject row, String name, String alternate) {
        String value = row.optString(name);
        if (value.isEmpty() && alternate != null) value = row.optString(alternate);
        JSONObject ext = row.optJSONObject("taskExtProps");
        if (value.isEmpty() && ext != null) value = ext.optString(name);
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String fingerprint(JSONObject row) {
        TreeSet<String> prizes = new TreeSet<>();
        JSONObject data = row.optJSONObject("prizeData"), base = data == null ? null : data.optJSONObject("prizeBaseInfoDTO");
        if (base != null && !base.optString("prizeId").isEmpty()) prizes.add(base.optString("prizeId"));
        JSONArray ids = row.optJSONArray("validPrizeIdList"), list = row.optJSONArray("prizeList");
        if (ids != null) for (int i = 0; i < Math.min(ids.length(), 100); i++) if (!ids.optString(i).isEmpty()) prizes.add(ids.optString(i));
        if (list != null) for (int i = 0; i < Math.min(list.length(), 100); i++) { JSONObject p = list.optJSONObject(i); if (p != null && !p.optString("prizeId").isEmpty()) prizes.add(p.optString("prizeId")); }
        String title = field(row, "title", "taskMainTitle"), link = field(row, "link", "taskGotoUrl");
        // A title alone cannot establish an alias; retain the ID when neither link nor prize identity is present.
        return title + "|" + link + "|" + (prizes.isEmpty() && link.isEmpty() ? row.optString("taskId") : String.join(",", prizes));
    }

    private static boolean selected(List<JSONObject> aliases, Set<String> selected) {
        for (JSONObject row : aliases) if (selected.contains(row.optString("taskId"))) return true;
        return false;
    }

    private static boolean blocked(List<JSONObject> aliases, Set<String> blacklist) {
        for (JSONObject row : aliases) if (financialTask(row) || blacklist.contains(row.optString("taskId")) || blacklist.contains(field(row, "title", "taskMainTitle"))) return true;
        return false;
    }

    private static boolean success(JSONObject root) {
        if (RpcRequestGuard.isFailure(root)) return false;
        if (root.has("success") && !Boolean.TRUE.equals(root.opt("success"))) return false;
        String resultCode = root.optString("resultCode"), code = root.optString("code");
        if (!resultCode.isEmpty() && !"100".equals(resultCode) && !"SUCCESS".equals(resultCode)) return false;
        if (!code.isEmpty() && !"100000000".equals(code)) return false;
        return Boolean.TRUE.equals(root.opt("success")) || "100".equals(resultCode) || "SUCCESS".equals(resultCode)
                || "100000000".equals(code);
    }

    private static JSONObject queryMain(String taskId) throws Exception {
        TimeUtil.sleep(0);
        JSONObject root = MyUtils.newJSONObject(AntMemberRpcCall.queryYebExpGoldMain(taskId));
        return success(root) ? root.optJSONObject("resultData") : null;
    }

    private static Map<String, JSONObject> pendingRewards(JSONObject data) {
        JSONObject tasks = data == null ? null : data.optJSONObject("taskData");
        Map<String, JSONObject> rows = AntMember.memberRowsById(tasks == null ? null : tasks.optJSONArray("completeList"), "taskId");
        if (rows != null) rows.entrySet().removeIf(entry -> financialTask(entry.getValue()));
        return rows;
    }

    private static JSONObject todaySign(JSONObject data) {
        JSONObject sign = data == null ? null : data.optJSONObject("signInData");
        JSONArray rows = sign == null ? null : sign.optJSONArray("list");
        if (rows == null || rows.length() > 31) return null;
        JSONObject result = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            JSONObject info = row == null ? null : row.optJSONObject("signInfo");
            if (info == null) return null;
            if ("TODAY".equals(info.optString("signDateDesc")) || row.optString("displayDate").contains("今天")) {
                if (result != null) return null;
                result = info;
            }
        }
        return result;
    }

    private static int businessDay() {
        Calendar now = MyUtils.getInstance();
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
    }

    private static void signIn(JSONObject main) throws Exception {
        String key = "member::yebSignAttempt";
        if (Status.hasFlagToday(key)) return;
        int day = businessDay();
        String uid = UserIdMap.getCurrentUid();
        JSONObject today = todaySign(main);
        String status = today == null ? "" : today.optString("signStatus");
        if (!"TO_SIGNED".equals(status) && !"UNSIGNED".equals(status)) return;
        TimeUtil.sleep(0);
        if (businessDay() != day || !uid.equals(UserIdMap.getCurrentUid())) return;
        Status.flagToday(key);
        JSONObject accepted = MyUtils.newJSONObject(AntMemberRpcCall.signInYebExpGold());
        JSONObject after = todaySign(queryMain(null));
        if (!uid.equals(UserIdMap.getCurrentUid())) return;
        if (success(accepted) && businessDay() == day && after != null && "SIGNED".equals(after.optString("signStatus"))) {
            Log.other("余额宝体验金💰签到已回查确认");
        } else Log.record("余额宝体验金签到未确认，本日不重复尝试");
    }
}
