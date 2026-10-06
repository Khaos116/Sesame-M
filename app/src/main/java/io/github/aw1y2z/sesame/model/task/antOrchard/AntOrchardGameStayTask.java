package io.github.aw1y2z.sesame.model.task.antOrchard;

import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** 协议参考导出 smali；SJ 3.6.14 APK 不包含此功能，尚待实机验证。 */
public final class AntOrchardGameStayTask {
    private static final String TAG = "农场游戏时长";

    private AntOrchardGameStayTask() { }

    public static void execute() {
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) {
                Log.record(TAG + "：跳过，账号切换/初始化中");
                return;
            }
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) {
                Log.record(TAG + "：跳过，当前账号标识为空");
                return;
            }
            Log.record(TAG + "：开始扫描首页与投放位任务");
            boolean discoveryFailed = false;
            Map<String, StayTask> tasks = new LinkedHashMap<>();
            for (int page = 1; page <= 3; page++) {
                JSONObject response = MyUtils.newJSONObject(AntOrchardRpcCall.indexFeeds(page));
                if (!successful(response, "查询首页第 " + page + " 页")) {
                    discoveryFailed = true;
                    break;
                }
                JSONArray feeds = response.optJSONArray("feedsInfoList");
                if (feeds == null) {
                    Log.record(TAG + "：查询首页失败，响应缺少 feedsInfoList 列表");
                    discoveryFailed = true;
                    break;
                }
                if (feeds.length() == 0) break;
                for (int i = 0; i < feeds.length(); i++) {
                    JSONObject feed = feeds.optJSONObject(i);
                    if (feed == null || !"game".equals(feed.optString("itemType"))) continue;
                    JSONObject game = feed.optJSONObject("gameItem");
                    JSONObject ext = feed.optJSONObject("itemOutExtMap");
                    if (game == null || ext == null) continue;
                    addTask(tasks, game.opt("gameItemId"), game.optString("gameName", "未知游戏"),
                            MyUtils.newJSONObject(ext.optString("orchardBenefitInfo")));
                }
            }
            JSONObject response = MyUtils.newJSONObject(AntOrchardRpcCall.orchardIndexDelivery());
            if (successful(response, "查询投放位")) {
                JSONArray deliveries = response.optJSONArray("indexDeliveryList");
                if (deliveries != null) {
                    for (int i = 0; i < deliveries.length(); i++) {
                        JSONObject delivery = deliveries.optJSONObject(i);
                        if (delivery == null) continue;
                        JSONObject display = delivery.optJSONObject("deliveryDisplayInfo");
                        addTask(tasks, delivery.opt("contentId"), display == null ? "未知游戏" : display.optString("btnText", "未知游戏"),
                                delivery.optJSONObject("deliveryBenefitInfo"));
                    }
                } else {
                    Log.record(TAG + "：查询投放位失败，响应缺少 indexDeliveryList 列表");
                    discoveryFailed = true;
                }
            } else {
                discoveryFailed = true;
            }
            int succeeded = 0, failed = 0;
            Log.record(TAG + "：发现 " + tasks.size() + " 个待执行任务");
            for (StayTask task : tasks.values()) {
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid())) {
                    Log.record(TAG + "：已停止，当前账号已变化，不结项旧账号任务");
                    return;
                }
                if (!Status.hasFlagToday(task.key)) {
                    if (runTask(task, uid)) succeeded++;
                    else failed++;
                }
            }
            if (tasks.isEmpty()) {
                Log.record(TAG + (discoveryFailed ? "：本轮查询存在失败，未取得可执行任务；原因见上方查询日志"
                        : "：本轮跳过，无可执行任务（无时长任务、今日已完成或异常字段已跳过）"));
            } else {
                Log.record(TAG + "：本轮结束，成功 " + succeeded + " 个、失败 " + failed + " 个"
                        + (discoveryFailed ? "；部分任务查询失败，原因见上方日志" : ""));
            }
        } catch (TaskCancelledException e) {
            Log.record(TAG + "：已取消，任务停止/代际作废，未结项的不记今日完成");
            throw e;
        } catch (Exception e) {
            Log.err(TAG, "执行异常", e);
        }
    }

    private static void addTask(Map<String, StayTask> tasks, Object appIdValue, String name, JSONObject benefit) {
        if (benefit == null || !"FLOAT_BALL_TASK".equals(benefit.optString("benefitType"))) return;
        if (!(appIdValue instanceof String) || !(benefit.opt("iepTaskId") instanceof String)
                || !(benefit.opt("iepSceneCode") instanceof String)) {
            Log.record(TAG + "：跳过标识类型异常的任务[" + name + "]");
            return;
        }
        String appId = (String) appIdValue;
        String taskId = benefit.optString("iepTaskId");
        String scene = benefit.optString("iepSceneCode");
        JSONObject biz = benefit.optJSONObject("bizInfo");
        if (benefit.has("bizInfo") && biz == null) {
            Log.record(TAG + "：跳过[" + name + "]，bizInfo 缺失或类型异常");
            return;
        }
        int seconds = 60;
        if (biz != null && biz.has("floatBallDuration")) {
            Object duration = biz.opt("floatBallDuration");
            seconds = biz.optInt("floatBallDuration", -1);
            if (!(duration instanceof Number) || ((Number) duration).doubleValue() != seconds) seconds = -1;
        }
        // ponytail: 单个任务最多等待 30 分钟；出现更长任务时再改为分段执行。
        if (appId.trim().isEmpty() || taskId.trim().isEmpty() || scene.trim().isEmpty() || seconds <= 0 || seconds > 1800) {
            Log.record(TAG + "：跳过标识或时长异常的任务[" + name + "]");
            return;
        }
        StayTask task = new StayTask(appId, name, taskId, scene, Math.max(30, seconds));
        if (Status.hasFlagToday(task.key)) {
            Log.record(TAG + "：跳过[" + name + "]，今日已成功结项");
            return;
        }
        StayTask previous = tasks.get(task.key);
        if (previous == null || previous.seconds < task.seconds) tasks.put(task.key, task);
    }

    private static boolean runTask(StayTask task, String uid) throws Exception {
        String label = "[" + task.name + "]#任务=" + task.taskId + "#";
        Log.record(TAG + "：开始[" + task.name + "]#任务=" + task.taskId + "#时长=" + task.seconds + "秒");
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.noticeGameStay(task.appId)), label + "通知进入游戏")) return false;
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.submitUserAction(task.appId)), label + "上报进入游戏")) return false;
        TimeUtil.sleep(500);
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.submitGameStayEvent(task.appId, "GAME_FIRST_FRAME", 0)), label + "上报首帧")) return false;
        TimeUtil.sleep(500);
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.submitGameStayEvent(task.appId, "loading_completed", 0)), label + "上报加载完成")) return false;
        // 上报秒数与 game_play 的毫秒时长保持一致，避免沿用 3–4 秒等待却上报 60 秒的矛盾参数。
        TimeUtil.sleep(task.seconds * 1000L);
        if (!uid.equals(UserIdMap.getCurrentUid())) {
            Log.record(TAG + "：" + label + "停止，当前账号已变化");
            return false;
        }
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.submitUserPlayDurationAction(task.appId, "lianyun_nc_sydb", task.seconds)), label + "上报游戏时长")) return false;
        if (!successful(MyUtils.newJSONObject(AntOrchardRpcCall.submitGameStayEvent(task.appId, "game_play", task.seconds * 1000L)), label + "上报游玩事件")) return false;
        JSONObject result = MyUtils.newJSONObject(AntOrchardRpcCall.finishGameStayTask(task.scene, task.taskId));
        if (!successful(result, label + "结项")) return false;
        Status.flagToday(task.key, uid);
        JSONObject award = result.optJSONObject("finishAwardResultVO");
        Object count = award == null ? null : award.opt("deltaAwardCount");
        String message = TAG + "：" + label + "成功，已结项；"
                + (count instanceof Number && ((Number) count).intValue() >= 0 ? "获得肥料" + ((Number) count).intValue() + "g"
                : "奖励数量未返回");
        Log.record(message);
        Log.farm(message);
        return true;
    }

    private static boolean successful(JSONObject response, String step) {
        boolean ok = !RpcRequestGuard.isFailure(response)
                && (Boolean.TRUE.equals(response.opt("success")) || Boolean.TRUE.equals(response.opt("isSuccess"))
                || "SUCCESS".equals(response.optString("resultCode")) || "100000000".equals(response.optString("code")));
        if (!ok) {
            String code = response.optString("resultCode", response.optString("code", response.optString("error", "未返回")));
            String reason = response.length() == 0 ? "响应为空或不是有效 JSON" : RpcRequestGuard.errorMessage(response);
            Log.record(TAG + "：" + step + "失败#状态码=" + code + "#原因=" + reason);
        }
        return ok;
    }

    private static final class StayTask {
        final String appId, name, taskId, scene, key;
        final int seconds;

        StayTask(String appId, String name, String taskId, String scene, int seconds) {
            this.appId = appId;
            this.name = name;
            this.taskId = taskId;
            this.scene = scene;
            this.seconds = seconds;
            key = "orchard::gameStay::" + scene + ":" + taskId + ":" + appId;
        }
    }
}
