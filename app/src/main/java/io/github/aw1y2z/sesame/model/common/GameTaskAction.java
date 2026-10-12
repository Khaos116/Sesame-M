package io.github.aw1y2z.sesame.model.common;

import android.net.Uri;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** AG 游戏动作分流；这里只确认动作受理，任务完成仍由所属模块回查。 */
public final class GameTaskAction {
    public enum Kind { NONE, DURATION, LEGACY, DEFERRED, COUNTDOWN }
    public final Kind kind;
    public final String appId, source;
    public final int seconds;
    private final GameTask game;

    private GameTaskAction(Kind kind, String appId, String source, int seconds, GameTask game) {
        this.kind = kind; this.appId = appId; this.source = source; this.seconds = seconds; this.game = game;
    }

    public static GameTaskAction parse(JSONObject task) {
        List<JSONObject> objects = new ArrayList<>();
        if (task != null) objects.add(task);
        List<String> urls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < objects.size() && i < 32; i++) {
            JSONObject value = objects.get(i);
            for (String key : new String[]{"bizInfo", "taskBaseInfo", "taskDisplayConfig", "simpleTaskConfig", "taskConfigInfo", "gameInfo", "extend", "extInfo", "floatBallConfig", "taskCategorization"}) {
                JSONObject nested = value.optJSONObject(key);
                if (nested == null && value.opt(key) instanceof String && value.optString(key).trim().startsWith("{")) nested = MyUtils.newJSONObject(value.optString(key));
                if (nested != null && objects.size() < 32) objects.add(nested);
            }
            for (String key : new String[]{"targetUrl", "url", "sourceUrl", "schema", "query"}) {
                String url = value.optString(key);
                if (!url.isEmpty() && seen.add(url)) urls.add(url);
            }
        }
        JSONObject params = MyUtils.newJSONObject();
        try {
            for (int i = 0; i < urls.size() && i < 32; i++) {
                String raw = urls.get(i);
                Uri uri = Uri.parse(!raw.contains(":") && raw.contains("=") ? "https://task.invalid/?" + raw : raw);
                if (!uri.isHierarchical()) continue;
                for (String key : uri.getQueryParameterNames()) {
                    String value = uri.getQueryParameter(key);
                    if (value == null) continue;
                    if (!params.has(key)) params.put(key, value);
                    if (Set.of("url", "sourceUrl", "schema", "query").contains(key) && seen.add(value)) urls.add(value);
                }
            }
        } catch (Exception ignored) { return new GameTaskAction(Kind.DEFERRED, "", "", 0, null); }
        objects.add(params);
        String app = first(objects, "gameAppId", "game_id", "appId");
        String source = first(objects, "source", "chInfo", "oriChInfo", "alipayFarmSource");
        String type = first(objects, "gameTaskType");
        GameTask mapped = GameTask.matchAppId(app);
        boolean isGame = mapped != null || !type.isEmpty() || !first(objects, "gameAppId", "game_id").isEmpty()
                || "Game".equalsIgnoreCase(first(objects, "categorizationSecondLevel"));
        boolean p2e = objects.stream().anyMatch(o -> o.optString("floatingBallTypeList").contains("P2E_GAME_BROWSE_TASK_FLOATING_BALL")
                || o.has("p2ePageRequests") || o.has("p2eHomePageRequest") || o.has("p2eTaskListRequest"));
        int seconds = 0;
        for (JSONObject value : objects) {
            for (String key : new String[]{"browseSeconds", "playTime", "timeCount", "floatBallDuration", "requiredDuration", "duration"}) {
                if (!value.has(key)) continue;
                int n = duration(value.opt(key));
                if (n < 0) return new GameTaskAction(Kind.DEFERRED, app, source, 0, mapped);
                seconds = Math.max(seconds, n);
            }
        }
        if (seconds == 0) for (JSONObject value : objects) {
            if (!value.has("vstTime")) continue;
            try {
                long millis = new java.math.BigDecimal(value.optString("vstTime")).longValueExact();
                if (millis <= 0 || millis > 1800000) throw new IllegalArgumentException();
                seconds = Math.max(seconds, (int)((millis + 999) / 1000 + 1));
            } catch (Exception e) { return new GameTaskAction(Kind.DEFERRED, app, source, 0, mapped); }
        }
        Kind kind = Kind.NONE;
        if (p2e) kind = Kind.DEFERRED;
        else if (isGame) {
            if ("COUNT_DOWN".equals(first(objects, "taskMode")) && objects.stream().anyMatch(o -> duration(o.opt("countDownSeconds")) > 0)) kind = Kind.COUNTDOWN;
            else if ((type.isEmpty() || "shichang".equalsIgnoreCase(type)) && seconds > 0 && seconds <= 1800
                    && app.matches("[0-9]{8,32}") && !source.isEmpty()) kind = Kind.DURATION;
            else if (mapped != null && type.isEmpty() && seconds == 0) kind = Kind.LEGACY;
            else kind = Kind.DEFERRED;
        }
        return new GameTaskAction(kind, app, source, seconds, mapped);
    }

    private static String first(List<JSONObject> objects, String... keys) {
        for (String key : keys) for (JSONObject value : objects) {
            if (value.opt(key) instanceof String && !value.optString(key).trim().isEmpty()) return value.optString(key).trim();
        }
        return "";
    }

    public static int duration(Object value) {
        if (!(value instanceof Number || value instanceof String)) return -1;
        try { int n = new java.math.BigDecimal(value.toString()).intValueExact(); return n >= 0 && n <= 1800 ? n : -1; }
        catch (Exception e) { return -1; }
    }

    public static boolean accepted(JSONObject response) {
        return !RpcRequestGuard.isFailure(response) && (Boolean.TRUE.equals(response.opt("success"))
                || "SUCCESS".equals(response.optString("resultCode")) || "100".equals(response.optString("resultCode")));
    }

    public static boolean isBusinessRejection(JSONObject response) {
        return Boolean.FALSE.equals(response.opt("success")) && !response.optString("resultCode").isEmpty()
                && !"RPC_SKIPPED".equals(response.optString("resultCode")) && !"RPC_SKIPPED".equals(response.optString("error"))
                && !MessageUtil.isRetryable(response) && !MessageUtil.isServerBusy(response);
    }

    public static boolean skipRejected(String key, String label) {
        if (DailyTask.isManual() || !Status.hasFlagToday("agReject::" + key)) return false;
        Log.record(label + "：当天已被服务端明确拒绝，自动调度跳过；可点击立即执行重试");
        return true;
    }

    public static boolean waitCountdown(String key, int seconds, String label) throws Exception {
        if (seconds <= 0 || seconds > 1800) return false;
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return false;
            cleanupDailyRecords();
            RuntimeInfo store = RuntimeInfo.getInstance();
            Calendar now = MyUtils.getInstance();
            String day = now.get(Calendar.YEAR) + "-" + now.get(Calendar.DAY_OF_YEAR);
            String storageKey = "gameAction::countdown:" + key;
            JSONObject state = MyUtils.newJSONObject(store.getString(storageKey));
            if (!day.equals(state.optString("day")) || state.optInt("seconds") != seconds) {
                state = MyUtils.newJSONObject().put("day", day).put("seconds", seconds)
                        .put("readyAt", System.currentTimeMillis() + seconds * 1000L);
                if (!store.putVerified(storageKey, state.toString())) return false;
            }
            long wait = state.optLong("readyAt") - System.currentTimeMillis();
            if (state.optLong("readyAt") <= 0 || wait > seconds * 1000L) return false;
            Log.record(label + "：页面倒计时，剩余" + Math.max(0, (wait + 999) / 1000) + "秒");
            TimeUtil.sleep(Math.max(0, wait));
            now = MyUtils.getInstance();
            return day.equals(now.get(Calendar.YEAR) + "-" + now.get(Calendar.DAY_OF_YEAR));
        }
    }

    public static void cleanupDailyRecords() {
        if (Status.hasFlagToday("agTaskSync::cleanup")) return;
        RuntimeInfo store = RuntimeInfo.getInstance();
        Calendar now = MyUtils.getInstance();
        String gameDay = now.get(Calendar.YEAR) + "-" + now.get(Calendar.DAY_OF_YEAR);
        String applyDay = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR) + ":";
        for (String key : store.keysStartingWith("gameAction::")) {
            if (!gameDay.equals(MyUtils.newJSONObject(store.getString(key)).optString("day"))) store.put(key, null);
        }
        for (String key : store.keysStartingWith("memberApply::")) {
            if (!store.getString(key).startsWith(applyDay)) store.put(key, null);
        }
        Status.flagToday("agTaskSync::cleanup");
    }

    public boolean perform(String key, String label, String channel) throws Exception {
        if (kind != Kind.DURATION && kind != Kind.LEGACY) return false;
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return false;
            cleanupDailyRecords();
            RuntimeInfo store = RuntimeInfo.getInstance();
            Calendar day = MyUtils.getInstance();
            String storageKey = "gameAction::" + key;
            String date = day.get(Calendar.YEAR) + "-" + day.get(Calendar.DAY_OF_YEAR);
            JSONObject state = MyUtils.newJSONObject(store.getString(storageKey));
            if (!date.equals(state.optString("day"))) state = MyUtils.newJSONObject().put("day", date);
            String contract = kind + ":" + appId + ":" + source + ":" + seconds;
            if (state.has("contract") && !contract.equals(state.optString("contract"))) {
                Log.record(label + "：游戏条件发生变化，等待列表回查"); return false;
            }
            state.put("contract", contract);
            if (state.optBoolean("pending")) { Log.record(label + "：游戏动作已提交待回查，不重复上报"); return false; }
            if (state.optBoolean("accepted")) return true;
            if (skipRejected(storageKey, label)) return false;
            int reported = state.optInt("reported");
            do {
                int chunk = kind == Kind.DURATION ? Math.min(30, seconds - reported) : 0;
                if (chunk < 0 || reported < 0) return false;
                if (chunk > 0) {
                    long ready = state.optLong("readyAt");
                    if (ready <= 0) {
                        ready = System.currentTimeMillis() + chunk * 1000L;
                        state.put("readyAt", ready);
                        if (!store.putVerified(storageKey, state.toString())) return false;
                    }
                    Log.record(label + "：游戏计时 " + reported + "/" + seconds + "秒");
                    long wait = ready - System.currentTimeMillis();
                    if (wait > chunk * 1000L) return false;
                    TimeUtil.sleep(Math.max(0, wait));
                }
                TimeUtil.sleep(0);
                Calendar current = MyUtils.getInstance();
                if (!date.equals(current.get(Calendar.YEAR) + "-" + current.get(Calendar.DAY_OF_YEAR))) return false;
                state.put("pending", true);
                if (!store.putVerified(storageKey, state.toString())) return false;
                boolean ok;
                if (kind == Kind.LEGACY) ok = game.reportSync(label, 1, channel) > 0;
                else {
                    Log.record(label + "：调用 submitUserPlayDurationAction，上报" + chunk + "秒，等待任务回查");
                    JSONObject response;
                    try {
                        response = MyUtils.newJSONObject(ApplicationHook.requestString("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
                                new JSONArray().put(MyUtils.newJSONObject().put("gameAppId", appId).put("playTime", chunk).put("source", source).put("statisticTag", "")).toString(), 1, 0));
                    } catch (TaskCancelledException e) { throw e; }
                    catch (Exception e) {
                        Log.record(label + "：submitUserPlayDurationAction 调用异常，保留已提交记录等待回查（" + e.getClass().getSimpleName() + "）");
                        return false;
                    }
                    ok = accepted(response);
                    if (isBusinessRejection(response)) Status.flagToday("agReject::" + storageKey);
                    if (!ok) Log.record(label + "：submitUserPlayDurationAction 返回未受理，code=" + response.optString("resultCode", "缺失"));
                    if (!ok && (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("resultCode")))) {
                        state.put("pending", false); store.putVerified(storageKey, state.toString());
                    }
                }
                if (!ok) { Log.record(label + "：游戏动作未确认受理，保留进度等待回查"); return false; }
                reported += chunk;
                state.put("pending", false).put("reported", reported).put("readyAt", 0)
                        .put("accepted", kind == Kind.LEGACY || reported >= seconds);
                if (!store.putVerified(storageKey, state.toString())) return false;
            } while (kind == Kind.DURATION && reported < seconds);
            Log.record(label + "：游戏动作已受理，任务完成以列表回查为准");
            return true;
        }
    }
}
