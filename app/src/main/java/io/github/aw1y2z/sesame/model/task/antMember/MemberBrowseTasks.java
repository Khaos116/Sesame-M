package io.github.aw1y2z.sesame.model.task.antMember;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.model.common.GameTaskAction;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** 会员签到页/广告任务的新协议，复用主任务轮询和账号私有计时记录。 */
final class MemberBrowseTasks {
    private static final String TAG = "会员浏览任务";

    static void run(Set<String> blacklist) {
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return;
            GameTaskAction.cleanupDailyRecords();
            Set<String> attempted = new HashSet<>();
            for (int round = 0; round < 12; round++) {
                Map<String, JSONObject> tasks = load();
                boolean changed = false;
                for (JSONObject task : tasks.values()) {
                    TimeUtil.sleep(0);
                    JSONObject config = config(task);
                    String id = configId(task), title = config.optString("title", config.optString("name", id));
                    if (id.isEmpty() || blacklist.contains(id) || blacklist.contains(title) || finished(task)) continue;
                    String snapshot = id + ":" + processId(task) + ":" + progress(task);
                    if (!attempted.add(snapshot)) continue;
                    try { changed |= perform(task, title); }
                    catch (TaskCancelledException e) { throw e; }
                    catch (Exception e) { Log.record(TAG + "[" + title + "]：执行异常，保留回查状态（" + e.getClass().getSimpleName() + "）"); }
                }
                if (!changed) return;
                TimeUtil.sleep(500);
            }
            Log.record(TAG + "：达到本轮阶段上限，后续轮次继续");
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.record(TAG + "：查询异常，后续轮次重试（" + e.getClass().getSimpleName() + "）"); }
    }

    private static Map<String, JSONObject> load() throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        Set<String> pageIds = new HashSet<>();
        String session = UUID.randomUUID().toString();
        boolean complete = false;
        boolean validIds = true;
        for (int page = 1; page <= 64; page++) {
            TimeUtil.sleep(0);
            List<JSONObject> rows = rows(AntMemberRpcCall.signPageTaskList(page, session));
            if (rows == null) { Log.record(TAG + "：签到页第" + page + "页查询失败或结构无效，未标记当天完成"); break; }
            if (rows.isEmpty()) { complete = true; break; }
            boolean advanced = false;
            for (JSONObject row : rows) {
                String id = configId(row) + ":" + processId(row);
                if (configId(row).isEmpty()) { validIds = false; continue; }
                if (pageIds.add(id)) advanced = true;
                add(result, row);
            }
            if (!advanced) { Log.record(TAG + "：签到页重复或缺少有效任务标识，停止翻页"); break; }
            TimeUtil.sleep(300);
        }
        if (!complete) Log.record(TAG + "：分页未完整，保留后续查询");
        for (String raw : new String[]{AntMemberRpcCall.currentMemberAdTasks(), AntMemberRpcCall.currentMemberProcesses()}) {
            List<JSONObject> rows = rows(raw);
            if (rows == null) { complete = false; Log.record(TAG + "：广告/进行中列表查询失败或结构无效"); continue; }
            for (JSONObject row : rows) {
                if (configId(row).isEmpty()) validIds = false;
                add(result, row);
            }
        }
        // 报名后的 process 行替代同配置的未报名展示行，避免重复报名。
        Set<String> active = new HashSet<>();
        for (JSONObject row : result.values()) if (!processId(row).isEmpty() && !finished(row)) active.add(configId(row));
        result.values().removeIf(row -> processId(row).isEmpty() && active.contains(configId(row)));
        boolean newTitles = false;
        for (JSONObject row : result.values()) {
            String title = config(row).optString("title", config(row).optString("name"));
            if (!title.isEmpty() && !AntMemberTaskListMap.getMap().containsKey(title)) {
                AntMemberTaskListMap.add(title, title); newTitles = true;
            }
            String key = "memberBrowse::" + processId(row);
            RuntimeInfo store = RuntimeInfo.getInstance();
            if (!processId(row).isEmpty() && finished(row) && !store.getString(key).isEmpty()) {
                logConfirmed(title, row); store.put(key, null);
            }
        }
        if (newTitles) AntMemberTaskListMap.save();
        if (complete && validIds) {
            Set<String> live = new HashSet<>();
            for (JSONObject row : result.values()) if (!finished(row)) live.add(processId(row));
            Calendar now = MyUtils.getInstance();
            String day = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR);
            RuntimeInfo store = RuntimeInfo.getInstance();
            for (String key : store.keysStartingWith("memberBrowse::")) {
                // 完整列表已无此流程才清旧日记录；活跃流程的未知提交仍须回查。
                if (!live.contains(key.substring("memberBrowse::".length()))
                        && !day.equals(MyUtils.newJSONObject(store.getString(key)).optString("day"))) store.put(key, null);
            }
        }
        return result;
    }

    private static void add(Map<String, JSONObject> target, JSONObject row) {
        String id = configId(row);
        if (!id.isEmpty()) target.put(id + ":" + processId(row), row);
    }

    private static List<JSONObject> rows(String raw) {
        JSONObject response = MyUtils.newJSONObject(raw);
        if (!GameTaskAction.accepted(response)) return null;
        JSONObject data = response.optJSONObject("resultData");
        if (data == null) data = response;
        List<JSONObject> rows = new ArrayList<>();
        boolean found = false;
        for (JSONObject root : data == response ? new JSONObject[]{response} : new JSONObject[]{response, data}) {
            for (String key : new String[]{"availableTaskProcessList", "availableTaskConfigList", "taskProcessVOList", "taskHistoryVOList", "pureTaskList", "adTaskList", "alipayGrowthTaskList"}) {
                if (!root.has(key)) continue;
                found = true;
                if (!append(rows, root.optJSONArray(key))) return null;
            }
            for (String key : new String[]{"categoryTaskList", "categoryTaskVOList"}) {
                if (!root.has(key)) continue;
                found = true;
                JSONArray categories = root.optJSONArray(key);
                if (categories == null) return null;
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.optJSONObject(i);
                    if (category == null || !append(rows, category.optJSONArray("taskProcessVOList"))) return null;
                }
            }
        }
        return found ? rows : null;
    }

    private static boolean append(List<JSONObject> rows, JSONArray array) {
        if (array == null || array.length() > 1000) return false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) return false;
            rows.add(row);
        }
        return true;
    }

    private static JSONObject config(JSONObject task) {
        for (String key : new String[]{"simpleTaskConfig", "taskConfigInfo", "taskConfig"}) {
            JSONObject config = task.optJSONObject(key);
            if (config != null) return config;
        }
        return MyUtils.newJSONObject();
    }

    private static String configId(JSONObject task) {
        for (Object id : new Object[]{task.opt("taskConfigId"), config(task).opt("configId"), config(task).opt("taskConfigId"), config(task).opt("id"), task.opt("id")}) {
            if ((id instanceof String || id instanceof Number) && !id.toString().trim().isEmpty()) return id.toString();
        }
        return "";
    }

    private static String processId(JSONObject task) {
        Object id = task.opt("processId");
        if (!(id instanceof String) || ((String) id).isEmpty()) id = task.opt("taskProcessId");
        return id instanceof String ? (String) id : "";
    }

    private static String progress(JSONObject task) {
        JSONObject ext = task.optJSONObject("extInfo");
        return task.optString("currentCount")
                + ":" + task.optString("targetCount") + ":" + (ext == null ? "" : ext.optString("PERIOD_CURRENT_COUNT") + ":" + ext.optString("PERIOD_TARGET_COUNT"));
    }

    private static boolean advanced(JSONObject before, JSONObject after) {
        if (before == null || after == null) return false;
        for (String key : new String[]{"currentCount", "PERIOD_CURRENT_COUNT"}) {
            JSONObject a = key.equals("currentCount") ? before : before.optJSONObject("extInfo");
            JSONObject b = key.equals("currentCount") ? after : after.optJSONObject("extInfo");
            if (a == null || b == null) continue;
            try {
                long oldCount = new java.math.BigDecimal(a.optString(key)).longValueExact();
                long newCount = new java.math.BigDecimal(b.optString(key)).longValueExact();
                if (oldCount >= 0 && newCount > oldCount) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static boolean finished(JSONObject task) {
        if (task == null) return false;
        for (String key : new String[]{"status", "subStatus"})
            if (Set.of("AWARDED", "SUCCESS", "COMPLETE", "DONE", "FINISHED", "EXPIRED").contains(task.optString(key).toUpperCase(Locale.ROOT))) return true;
        try {
            long current = new java.math.BigDecimal(task.optString("currentCount")).longValueExact();
            long target = new java.math.BigDecimal(task.optString("targetCount")).longValueExact();
            return target > 0 && current >= target;
        } catch (Exception e) { return false; }
    }

    private static JSONObject detail(String id, JSONObject baseline) throws Exception {
        TimeUtil.sleep(0);
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.currentMemberTaskDetail(id));
        if (!GameTaskAction.accepted(response)) return null;
        JSONObject data = response.optJSONObject("resultData");
        JSONObject row = (data == null ? response : data).optJSONObject("taskProcessVO");
        if (row == null || !id.equals(processId(row))) return null;
        if (!configId(row).isEmpty() && !configId(baseline).equals(configId(row))) return null;
        if (config(row).length() == 0) row.put("simpleTaskConfig", config(baseline));
        return row;
    }

    private static String[] target(JSONObject task) {
        JSONArray targets = task.optJSONArray("targetBusiness");
        if (targets == null) targets = config(task).optJSONArray("targetBusiness");
        for (int i = 0; targets != null && i < targets.length(); i++) {
            if (!(targets.opt(i) instanceof String)) continue;
            String[] parts = targets.optString(i).split("#", -1);
            if (parts.length == 3 && "BROWSE".equals(parts[0]) && !parts[1].isEmpty() && !parts[2].isEmpty()) return parts;
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
                    && !Set.of("CALL_APP", "BROWSE").contains(parts[0])) return new String[]{"BROWSE", parts[0], parts[1]};
        }
        return null;
    }

    private static boolean perform(JSONObject listed, String title) throws Exception {
        String label = TAG + "[" + title + "]", id = configId(listed), process = processId(listed);
        RuntimeInfo store = RuntimeInfo.getInstance();
        if (process.isEmpty()) {
            if (target(listed) == null) return false;
            Calendar now = MyUtils.getInstance();
            String key = "memberApply::" + id;
            String stage = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR) + ":" + progress(listed);
            if (stage.equals(store.getString(key))) { Log.record(label + "：已报名待列表确认，不重复报名"); return false; }
            TimeUtil.sleep(0);
            if (!store.putVerified(key, stage)) return false;
            boolean batch = "signInAd".equals(config(listed).optString("sourceBusiness"));
            Log.record(label + "：调用报名接口 " + (batch ? "batchApplyTask" : "applyTask"));
            JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.applyCurrentMemberTask(id, batch));
            JSONObject data = response.optJSONObject("resultData");
            JSONObject resultMap = data == null ? null : data.optJSONObject("resultMap");
            boolean ok = GameTaskAction.accepted(response) && (!batch || resultMap != null && "SUCCESS".equals(resultMap.optString(id)));
            if (!ok) markRejected(title, response);
            if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("resultCode"))) store.putVerified(key, "");
            Log.record(label + (ok ? "：报名已受理，重新查询任务阶段" : "：报名未确认，code=" + response.optString("resultCode", "缺失")));
            JSONObject applied = data == null ? null : data.optJSONObject("taskProcessVO");
            if (ok && !batch && applied != null && !processId(applied).isEmpty()
                    && (configId(applied).isEmpty() || id.equals(configId(applied)))) {
                if (config(applied).length() == 0) applied.put("simpleTaskConfig", config(listed));
                perform(applied, title);
            }
            return ok;
        }
        JSONObject task = detail(process, listed);
        if (task == null) { Log.record(label + "：详情回查失败或任务不匹配，未执行"); return false; }
        String key = "memberBrowse::" + process;
        if (finished(task)) {
            if (!store.getString(key).isEmpty()) logConfirmed(title, task);
            store.put(key, null); return false;
        }
        String[] target = target(task);
        if (target == null) return false;
        String stage = progress(task);
        JSONObject state = MyUtils.newJSONObject(store.getString(key));
        if (state.has("stage") && !stage.equals(state.optString("stage")) && !advanced(state.optJSONObject("before"), task)) {
            Log.record(label + "：进度变化未确认推进，保留回查状态"); return false;
        }
        if (!stage.equals(state.optString("stage"))) {
            JSONObject before = MyUtils.newJSONObject().put("currentCount", task.opt("currentCount"));
            JSONObject ext = task.optJSONObject("extInfo");
            if (ext != null) before.put("extInfo", MyUtils.newJSONObject().put("PERIOD_CURRENT_COUNT", ext.opt("PERIOD_CURRENT_COUNT")));
            state = MyUtils.newJSONObject().put("stage", stage).put("before", before);
        }
        if (state.optBoolean("submitted")) { Log.record(label + "：同阶段已提交，详情尚未确认，不重复执行"); return false; }
        String contract = Arrays.toString(target) + ":" + config(task).optString("browseSeconds");
        if (state.has("contract") && !contract.equals(state.optString("contract"))) {
            store.put(key, null); return false;
        }
        state.put("contract", contract);
        Calendar started = MyUtils.getInstance();
        String day = started.get(Calendar.YEAR) + ":" + started.get(Calendar.DAY_OF_YEAR);
        if (!day.equals(state.optString("day"))) state.put("day", day).put("readyAt", 0);
        GameTaskAction game = GameTaskAction.parse(task);
        if (game.kind == GameTaskAction.Kind.DEFERRED || game.kind == GameTaskAction.Kind.COUNTDOWN) {
            return false;
        }
        if (game.kind != GameTaskAction.Kind.NONE) {
            if (!game.perform("member:" + process + ":" + stage, label, null)) return false;
        } else {
            JSONObject config = config(task);
            int seconds = config.has("browseSeconds") ? GameTaskAction.duration(config.opt("browseSeconds")) : 0;
            if (seconds < 0) { Log.record(label + "：浏览时长无效，未执行"); return false; }
            long readyAt = state.optLong("readyAt");
            if (readyAt <= 0) {
                readyAt = System.currentTimeMillis() + seconds * 1000L;
                state.put("readyAt", readyAt);
                if (!store.putVerified(key, state.toString())) return false;
            }
            long wait = readyAt - System.currentTimeMillis();
            if (wait > seconds * 1000L) { Log.record(label + "：计时记录异常，保留待检查"); return false; }
            Log.record(label + "：浏览计时，剩余" + Math.max(0, (wait + 999) / 1000) + "秒");
            TimeUtil.sleep(Math.max(0, wait));
        }
        JSONObject fresh = detail(process, task);
        if (fresh == null) return false;
        if (finished(fresh) || advanced(task, fresh)) { store.put(key, null); logConfirmed(title, fresh); return true; }
        Calendar now = MyUtils.getInstance();
        if (started.get(Calendar.YEAR) != now.get(Calendar.YEAR) || started.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
                || !stage.equals(progress(fresh)) || !Arrays.equals(target, target(fresh))
                || !config(task).optString("browseSeconds").equals(config(fresh).optString("browseSeconds"))) return false;
        TimeUtil.sleep(0);
        state.put("submitted", true);
        if (!store.putVerified(key, state.toString())) return false;
        Log.record(label + "：计时结束，调用 executeTask，随后回查同一任务详情");
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.executeCurrentMemberTask(target[1], target[2]));
        if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("resultCode"))) {
            state.put("submitted", false); store.putVerified(key, state.toString());
        }
        TimeUtil.sleep(500);
        JSONObject after = detail(process, task);
        if (after != null && (finished(after) || advanced(task, after))) {
            store.put(key, null); logConfirmed(title, after); return true;
        }
        if (after != null && !GameTaskAction.accepted(response)) markRejected(title, response);
        Log.record(label + "：执行后结果未确认，code=" + response.optString("resultCode", "缺失") + "，后续轮次继续回查");
        return false;
    }

    private static void markRejected(String title, JSONObject response) {
        if (!GameTaskAction.accepted(response) && !"RPC_SKIPPED".equals(response.optString("resultCode"))
                && !"RPC_SKIPPED".equals(response.optString("error")))
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntMemberTaskList", title, response);
    }

    private static void logConfirmed(String title, JSONObject task) {
        String result = finished(task) ? "完成" : "阶段推进";
        Log.record(TAG + "[" + title + "]：详情已确认" + result);
        JSONObject ext = task.optJSONObject("extInfo");
        String points = ext == null ? "" : ext.optString("awardCurrentPoint");
        Log.other("会员任务🎖️" + result + "[" + title + "]" + (points.isEmpty() ? "" : "#详情奖励积分[" + points + "]"));
    }
}
