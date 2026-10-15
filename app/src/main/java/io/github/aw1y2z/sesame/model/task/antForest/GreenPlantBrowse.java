package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;

/**
 * 绿植浏览任务（{@code GUANG_TASK}，页面上叫「浏览精选商品赚水滴」）。
 *
 * <p>三项收益一起发：**15g 森林能量** + 5 会员积分 + 200 水滴，只要 15g 能量就够本，故只做这一条。
 *
 * <p>完成动作分两步，缺一不可：
 * <ol>
 *   <li>翻商品流 {@code queryPlantFeedsItemList}（页面上要滑到列表底部，翻页即为"看过"）；</li>
 *   <li>领奖 {@code plantAward}，且 {@code awardType} 必须传**任务类型**（{@code GUANG_TASK}），
 *       传 {@code WATER} 领到的是浇水的额度、任务不会完成。</li>
 * </ol>
 */
public class GreenPlantBrowse {
    private static final class LocalRuleSkip extends RuntimeException { }

    private static final String TAG = GreenPlantBrowse.class.getSimpleName();

    /** 只处理这一个任务 */
    private static final String TASK_CODE = "GUANG_TASK";

    /** 当日已完成的标记：一天只做一次，重跑不再翻页也不再领奖 */
    private static final String FLAG_DONE = "forest::marketBrowseDone";

    /**
     * 翻页区间：页面进入时**第 1 页已随初始化下发**，用户滑动才请求第 2、3 页，
     * 故从 2 开始（照官方轨迹，避免多请求一页）。
     */
    private static final int FIRST_PAGE = 2;
    private static final int LAST_PAGE = 3;

    /** 每页条数 */
    private static final int PAGE_SIZE = 20;

    /** 两页之间的停留：页面滑到底再滑下一页，官方两次请求间隔约 7 秒 */
    private static final long PAGE_INTERVAL_MS = 7000L;

    /** 看完到领奖之间的停留：页面有 30 秒倒计时，官方在此停留约 21 秒 */
    private static final long STAY_BEFORE_AWARD_MS = 21000L;

    private GreenPlantBrowse() {
    }

    /**
     * 跑一轮；调用方按开关决定是否调用。
     * <p>各提前返回都要留痕：否则「今日已做」「列表里没这个任务」「今日已领过」三种情况
     * 在日志里都是零输出，排查时分不清是没跑、跑了没找到、还是已经做过。
     */
    public static void run() {
        try {
            if (Status.hasFlagToday(FLAG_DONE)) {
                return;
            }
            JSONObject task = queryGuangTask();
            if (task == null) {
                return;
            }
            if (!(task.opt("completed") instanceof Boolean)) {
                Log.markUnresolved();
                Log.record("绿植浏览[查询]：任务完成状态无效，本轮停止");
                return;
            }
            if (task.optBoolean("completed", false)) {
                // 已领过：只是没打本地标记，补上即可
                Status.flagToday(FLAG_DONE);
                Log.record("绿植浏览⏭️服务端已是已完成，跳过");
                return;
            }
            if (!browseFeeds()) {
                Log.markUnresolved();
                Log.record("绿植浏览[翻页]：接口失败或列表结构无效，本轮未领奖");
                return;
            }
            // 页面倒计时（30 秒）走完才可领奖，照官方停留节奏
            TimeUtil.sleep(STAY_BEFORE_AWARD_MS);
            if (receiveAward()) {
                JSONObject after = queryGuangTask();
                if (after != null && Boolean.TRUE.equals(after.opt("completed"))) {
                    Status.flagToday(FLAG_DONE);
                    Log.record("绿植浏览：领奖成功，任务回查已完成");
                } else {
                    Log.markUnresolved();
                    Log.record("绿植浏览[回查]：领奖已受理，完成状态未确认");
                }
            } else {
                // 不落标记：服务端这次没收，下一轮还能再试
                Log.forest("绿植浏览⚠️领奖失败#下次重试");
            }
        } catch (Throwable t) {
            if (t instanceof LocalRuleSkip) return;
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.markUnresolved();
            Log.record("绿植浏览[执行]：异常中止，结果未确认");
            Log.err(TAG, "run err:", t);
        }
    }

    /** 在任务列表里找 {@code GUANG_TASK}；找不到（今日已下线）返回 null */
    private static JSONObject queryGuangTask() {
        JSONObject jo = parse(rpc("com.alipay.ugshopping.biz.service.rpc.plant.PlantFacade.queryPlantTaskList",
                "[{\"bizType\":\"forestMemberCorp\",\"chInfo\":\"forestWater\"}]"));
        JSONArray list = jo == null ? null : jo.optJSONArray("taskList");
        if (list == null) {
            Log.markUnresolved();
            Log.record("绿植浏览[查询/queryPlantTaskList]：接口失败或任务列表无效");
            return null;
        }
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject task = list.optJSONObject(i);
            if (task != null && TASK_CODE.equals(task.optString("taskCode"))) {
                return task;
            }
        }
        Log.record("绿植浏览[查询]：暂无GUANG_TASK任务");
        return null;
    }

    /**
     * 按官方轨迹浏览：翻第 2、3 页（第 1 页已随页面初始化下发），页间停留，
     * 翻完再停留到倒计时结束。
     * <p>{@code blackItemIds} 是页面用于去重的已展示商品，不带也能取到列表，故传空数组。
     */
    private static boolean browseFeeds() {
        for (int page = FIRST_PAGE; page <= LAST_PAGE; page++) {
            JSONObject jo = parse(rpc("com.alipay.ugshopping.biz.service.rpc.plant.PlantFacade.queryPlantFeedsItemList",
                    "[{\"bizType\":\"forestMemberCorp\",\"blackItemIds\":[],\"chInfo\":\"forestWater\","
                            + "\"pageNum\":" + page + ",\"pageSize\":" + PAGE_SIZE + ","
                            + "\"sessionId\":\"forestWater\",\"version\":\"forest_oasis_v2\"}]"));
            JSONArray items = jo == null ? null : jo.optJSONArray("itemDTOList");
            if (items == null) return false;
            if (items.length() == 0) {
                break;
            }
            TimeUtil.sleep(PAGE_INTERVAL_MS);
        }
        return true;
    }

    /** 领奖：{@code awardType} 传任务类型，响应里的 {@code awardResult} 即本次奖励数 */
    private static boolean receiveAward() {
        JSONObject jo = parse(rpc("com.alipay.ugshopping.biz.service.rpc.plant.PlantFacade.plantAward",
                "[{\"awardType\":\"" + TASK_CODE + "\",\"source\":\"forestWater\"}]"));
        if (jo != null && (jo.optBoolean("success") || "SUCCESS".equals(jo.optString("resultCode")))) {
            return true;
        }
        return false;
    }

    private static String rpc(String operationType, String requestData) {
        TimeUtil.sleep(0);
        return ApplicationHook.requestString(operationType, requestData);
    }

    /** 这族接口回 {@code resultCode:SUCCESS} 且不带 desc，故按 resultCode 判定；解析失败返回 null */
    private static JSONObject parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            JSONObject parsed = MyUtils.newJSONObject(raw);
            if (parsed.optBoolean("localRuleSkip") && "RPC_SKIPPED".equals(parsed.optString("error"))) throw new LocalRuleSkip();
            return RpcRequestGuard.isFailure(parsed)
                    || (!Boolean.TRUE.equals(parsed.opt("success")) && !"SUCCESS".equals(parsed.optString("resultCode"))) ? null : parsed;
        } catch (Throwable t) {
            if (t instanceof LocalRuleSkip) throw (LocalRuleSkip) t;
            Log.err(TAG, "响应解析失败", t);
            return null;
        }
    }
}
