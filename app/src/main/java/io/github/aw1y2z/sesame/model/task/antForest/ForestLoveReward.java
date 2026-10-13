package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.DailyTask;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** 按官方流程确认奖励展示并回查，不把展示缓存当作到账凭据。 */
final class ForestLoveReward {
    private static final String LAST_PROBE = "Forest::loveRewardConfirmProbeTime";
    private static final String LAST_CONFIRM = "Forest::loveRewardConfirmAttempt";

    static void run() {
        String stage = "入口";
        String uid = UserIdMap.getCurrentUid();
        String day = String.format(Locale.ROOT, "%1$tY%1$tm%1$td", MyUtils.getInstance());
        try {
            TimeUtil.sleep(0);
            if (uid == null || uid.isEmpty() || ApplicationHook.isOffline()) {
                Log.record("真爱奖励[未执行]：账号不可用或已离线");
                return;
            }
            long now = System.currentTimeMillis();
            long previous = RuntimeInfo.getInstance().getLong(LAST_PROBE, 0);
            if (!DailyTask.isManual() && previous > 0 && now >= previous && now - previous < 1_800_000L) {
                Log.record("真爱奖励[未执行]：测试查询间隔不足30分钟，可点击立即执行重新检查；未标记领取完成");
                return;
            }
            RuntimeInfo.getInstance().put(LAST_PROBE, now);
            Log.record("真爱奖励[开始]：页面请求头测试，不受当天已浇水标记限制；不会再次浇水");
            stage = "调用前背包";
            Map<String, Long> before = inventory(stage, uid);
            checkAccount(uid);
            stage = "读取去领奖状态";
            JSONObject state = response(AntForestRpcCall.loveRewardForestHome(), stage);
            checkAccount(uid);
            if (state == null) return;
            String widget = safeCode(state.opt("widgetStatus"));
            Log.record("真爱奖励[入口状态]：widgetStatus=" + widget);
            if (!"reward".equals(widget)) {
                Log.record("真爱奖励[未调用领奖页面]：入口未处于去领奖状态，未标记领取完成");
                Map<String, Long> after = inventory("入口查询后背包", uid);
                checkAccount(uid);
                recordResult(before, after);
                return;
            }
            stage = "loveHome页面查询";
            JSONObject home = response(AntForestRpcCall.loveRewardHome(), stage);
            checkAccount(uid);
            if (home != null) {
                JSONObject team = home.optJSONObject("teamInfo");
                JSONArray rewards = team == null ? null : team.optJSONArray("popupRewardList");
                Log.record("真爱奖励[页面返回]：展示记录=" + (rewards == null ? "缺失" : rewards.length())
                        + "，展示记录不代表新领取，需完成官方确认流程");
                if (rewards != null) for (int i = 0; i < rewards.length(); i++) {
                    JSONObject reward = rewards.optJSONObject(i);
                    if (reward == null) continue;
                    Log.record("真爱奖励[奖励展示]：rewardCode=" + safeCode(reward.opt("rewardCode"))
                            + "，provideType=" + safeCode(reward.opt("provideType"))
                            + "，rewardRepeat=" + (reward.opt("rewardRepeat") instanceof Boolean
                            ? reward.optBoolean("rewardRepeat") : "未知"));
                }
                JSONObject properties = home.optJSONObject("pageProperties");
                Object subscriptionValue = properties == null ? null : properties.opt("subscribeId");
                String subscription = subscriptionValue instanceof String ? (String) subscriptionValue : "";
                if (!subscription.isEmpty()) {
                    stage = "querySubscribeRelation订阅查询";
                    response(AntForestRpcCall.loveRewardSubscribe(subscription), stage);
                    checkAccount(uid);
                } else Log.record("真爱奖励[订阅查询未执行]：页面没有subscribeId或字段类型无效");
                stage = "奖励展示确认";
                confirm(home, state, uid, day);
            }
            // 即使页面回执无效也回查，避免把接口包装差异误判为未到账。
            stage = "调用后背包";
            Map<String, Long> after = inventory(stage, uid);
            checkAccount(uid);
            if (!increased(before, after)) {
                TimeUtil.sleep(2000);
                checkAccount(uid);
                stage = "延迟回查背包";
                after = inventory(stage, uid);
                checkAccount(uid);
            }
            recordResult(before, after);
        } catch (TaskCancelledException e) { throw e; }
        catch (Throwable t) {
            Log.record("真爱奖励[" + stage + "]异常：" + t.getClass().getSimpleName() + "；未确认领取成功");
        }
    }

    private static void confirm(JSONObject home, JSONObject state, String uid, String day) throws Exception {
        JSONObject team = home.optJSONObject("teamInfo");
        JSONArray rewards = team == null ? null : team.optJSONArray("popupRewardList");
        Object teamValue = team == null ? null : team.opt("teamId");
        String teamId = teamValue instanceof String ? (String) teamValue : "";
        JSONObject reward = rewards != null && rewards.length() == 1 ? rewards.optJSONObject(0) : null;
        String code = reward == null ? "" : reward.optString("rewardCode", "");
        if (!Boolean.TRUE.equals(home.opt("useDailyReward")) || teamId.isEmpty()
                || !teamId.equals(state.opt("teamId")) || !"ACTIVATED".equals(team.opt("teamStatus"))
                || reward == null || !"daily".equals(reward.opt("rewardType")) || !"daily".equals(reward.opt("provideType"))
                || !(reward.opt("rewardRepeat") instanceof Boolean)
                || !("LoveQiXiTu".equals(code) || "LoveDoubleClick".equals(code) || "LoveEnergyShield".equals(code))) {
            Log.record("真爱奖励[确认未执行]：队伍/每日奖励结构无效，或存在其他待展示奖励；保留缓存");
            return;
        }
        checkAccount(uid);
        if (!day.equals(String.format(Locale.ROOT, "%1$tY%1$tm%1$td", MyUtils.getInstance()))) {
            Log.record("真爱奖励[确认未执行]：业务日期已变化，保留缓存等待下一轮");
            return;
        }
        String attempt = day + "|" + teamId + "|" + code;
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        if (attempt.equals(runtime.getString(LAST_CONFIRM)) && !DailyTask.isManual()) {
            Log.record("真爱奖励[确认未重发]：同一每日奖励已尝试确认，未标记到账；先查看回查与抓包结果");
            return;
        }
        if (!runtime.putVerified(LAST_CONFIRM, attempt)) {
            Log.record("真爱奖励[确认未执行]：尝试记录未保存，保留缓存");
            return;
        }
        checkAccount(uid);
        Log.record("真爱奖励[展示确认]：按官方弹窗回调调用deleteRewardCache，仅确认展示，不作为发放凭据");
        response(AntForestRpcCall.loveRewardConfirm(teamId), "deleteRewardCache确认回执");
        checkAccount(uid);
        JSONObject checkedHome = response(AntForestRpcCall.loveRewardHome(), "确认后页面回查");
        checkAccount(uid);
        JSONObject checkedState = response(AntForestRpcCall.loveRewardForestHome(), "确认后入口回查");
        checkAccount(uid);
        JSONObject checkedTeam = checkedHome == null ? null : checkedHome.optJSONObject("teamInfo");
        JSONArray pending = checkedTeam == null ? null : checkedTeam.optJSONArray("popupRewardList");
        boolean sameTeam = checkedTeam != null && teamId.equals(checkedTeam.opt("teamId"))
                && checkedState != null && teamId.equals(checkedState.opt("teamId"));
        String widget = checkedState == null ? "未知" : safeCode(checkedState.opt("widgetStatus"));
        Log.record("真爱奖励[确认后状态]：widgetStatus=" + widget + "，剩余展示="
                + (pending == null ? "未知" : pending.length()) + "，"
                + (sameTeam && pending != null && pending.length() == 0 && widget.equals(checkedState.opt("widgetStatus"))
                && !"reward".equals(widget) ? "领奖提示已消除，资产结果另行回查" : "领奖提示是否消除仍未确认"));
    }

    private static void checkAccount(String uid) {
        TimeUtil.sleep(0);
        if (!uid.equals(UserIdMap.getCurrentUid())) throw new TaskCancelledException();
    }

    private static JSONObject response(String raw, String stage) {
        JSONObject root = MyUtils.newJSONObject(raw);
        JSONObject nested = root.optJSONObject("resData");
        JSONObject result = nested == null ? root : nested;
        boolean success = !RpcRequestGuard.isFailure(root) && !RpcRequestGuard.isFailure(result)
                && (Boolean.TRUE.equals(result.opt("success")) || "SUCCESS".equals(result.opt("resultCode"))
                || "200".equals(String.valueOf(result.opt("resultCode"))));
        Log.record("真爱奖励[" + stage + "]：回执=" + (raw == null || raw.isEmpty() ? "未返回" : "已返回")
                + "，success=" + success + "，resultCode=" + safeCode(result.opt("resultCode"))
                + "，error=" + safeCode(result.opt("error")) + "，errorCode=" + safeCode(result.opt("errorCode")));
        return success ? result : null;
    }

    private static Map<String, Long> inventory(String stage, String uid) throws Exception {
        JSONObject result = response(AntForestRpcCall.queryPropList(false), stage);
        checkAccount(uid);
        JSONArray props = result == null ? null : result.optJSONArray("forestPropVOList");
        if (props == null) {
            Log.record("真爱奖励[" + stage + "]：forestPropVOList缺失或查询未成功，不能确认到账");
            return null;
        }
        if (!Boolean.FALSE.equals(result.opt("hasMore"))) {
            Log.record("真爱奖励[" + stage + "]：普通背包完整性未知，不能确认资产增量");
            return null;
        }
        Map<String, Long> counts = new TreeMap<>();
        for (int i = 0; i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop == null) {
                Log.record("真爱奖励[" + stage + "]：背包项目结构无效，不能确认到账");
                return null;
            }
            Object typeValue = prop.opt("propType");
            if (!(typeValue instanceof String) || ((String) typeValue).isEmpty()) {
                Log.record("真爱奖励[" + stage + "]：背包道具类型缺失或无效，不能确认到账");
                return null;
            }
            String type = (String) typeValue;
            Object count = prop.opt("holdsNum");
            if (!type.matches("[A-Za-z0-9_.-]{1,80}") || !(count instanceof Number)
                    || ((Number) count).longValue() < 0
                    || ((Number) count).doubleValue() != ((Number) count).longValue() || counts.containsKey(type)) {
                Log.record("真爱奖励[" + stage + "]：真爱道具数量无效或类型重复，不能确认到账");
                return null;
            }
            counts.put(type, ((Number) count).longValue());
        }
        JSONObject animalResult = response(AntForestRpcCall.loveRewardAnimals(), stage + "伙伴查询");
        checkAccount(uid);
        JSONArray animals = animalResult == null ? null : animalResult.optJSONArray("animalProps");
        if (animals == null) {
            Log.record("真爱奖励[" + stage + "]：伙伴列表缺失，无法核验七夕兔");
            return null;
        }
        boolean complete = Boolean.FALSE.equals(animalResult.opt("hasMore"));
        for (int i = 0; i < animals.length(); i++) {
            JSONObject animal = animals.optJSONObject(i);
            JSONObject main = animal == null ? null : animal.optJSONObject("main");
            JSONObject partner = animal == null ? null : animal.optJSONObject("partner");
            Object typeValue = main == null ? null : main.opt("propType");
            Object count = main == null ? null : main.opt("holdsNum");
            if (partner == null || !(typeValue instanceof String) || !((String) typeValue).matches("[A-Za-z0-9_.-]{1,80}")
                    || !(count instanceof Number) || ((Number) count).longValue() < 0
                    || ((Number) count).doubleValue() != ((Number) count).longValue()) {
                Log.record("真爱奖励[" + stage + "]：伙伴项目结构无效，无法核验资产");
                return null;
            }
            String type = "animal/" + typeValue;
            if (counts.containsKey(type)) {
                Log.record("真爱奖励[" + stage + "]：伙伴资产类型重复，不能确认增量");
                return null;
            }
            counts.put(type, ((Number) count).longValue());
            Object expiry = main.opt("recentExpireTime");
            if (expiry instanceof Number && ((Number) expiry).longValue() >= 0) {
                counts.put(type + "/expiry", ((Number) expiry).longValue());
            }
        }
        if (!complete) {
            Log.record("真爱奖励[" + stage + "]：伙伴列表完整性未知，保留已观察项目，不能确认资产增量");
            return null;
        }
        return counts;
    }

    static boolean increased(Map<String, Long> before, Map<String, Long> after) {
        if (before == null || after == null) return false;
        for (Map.Entry<String, Long> entry : after.entrySet()) {
            if (entry.getKey().endsWith("/expiry") && !before.containsKey(entry.getKey())) continue;
            if (entry.getValue() > before.getOrDefault(entry.getKey(), 0L)) return true;
        }
        return false;
    }

    private static void recordResult(Map<String, Long> before, Map<String, Long> after) {
        Log.record("真爱奖励[结果]：" + (before == null || after == null ? "背包数据不完整，无法判断到账"
                : increased(before, after) ? "观察到背包资产数量或有效期增加，请结合奖励类型与抓包确认"
                : "未观察到背包资产数量或有效期增加，不能确认本轮新发奖") + "；未写入当天领取完成标记");
    }

    private static String safeCode(Object value) {
        String code = value == null ? "" : String.valueOf(value);
        return code.matches("[A-Za-z0-9_-]{1,64}") ? code : "缺失或非标准";
    }
}
