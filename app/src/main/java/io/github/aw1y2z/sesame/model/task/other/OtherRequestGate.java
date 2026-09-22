package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.util.Log;

/**
 * OtherTask（信用2101/好家无忧卡）专用的请求预算+风控冷却闸门。
 * 原版（GR）这两个业务直接裸调用 RPC，没有 IsolatedRewardTask 那套限流/冷却保护——
 * 请求次数、事件循环里的固定 ID 遍历都没有上限，命中风控也不会暂停后续轮次。
 * 这两个任务的调用形状（一次 run 里请求次数随服务端数据变化，不是固定 1-2 次查询）
 * 跟 IsolatedRewardTask 的"query/onceToday"模型不匹配，所以没有直接复用那个基类，
 * 而是单独包一层同样基于 RequestBudgetPolicy/RpcFailurePolicy 的闸门。见 docs/MyFix.md。
 */
final class OtherRequestGate {
    /** 覆盖账户查询+宝箱+任务+天赋+图鉴+守护+事件等全部分支的宽松上限，超出说明业务分支异常，不该继续重试。 */
    private static final int MAX_REQUESTS_PER_RUN = 80;
    private static final String COOLDOWN_KEY = "OtherTask.nextRun";

    interface RpcCall { String call() throws Exception; }

    static final class Denied extends RuntimeException { }
    static final class BudgetExhausted extends RuntimeException { }

    private int requests;
    private long lastCallNanos;

    static boolean isCoolingDown() {
        return System.currentTimeMillis() < RuntimeInfo.getInstance().getLong(COOLDOWN_KEY, 0);
    }

    /** 预算耗尽/命中风控都会抛异常，调用方按现有的 try/catch(Throwable) 结构自然结束本轮。 */
    String call(String label, RpcCall rpc) throws Exception {
        if (!RequestBudgetPolicy.mayRequest(requests, MAX_REQUESTS_PER_RUN)) {
            Log.record("其他任务：" + label + "触发本轮请求上限，停止本轮");
            throw new BudgetExhausted();
        }
        long pause = RequestBudgetPolicy.pacingMs(lastCallNanos, System.nanoTime());
        if (pause > 0) Thread.sleep(pause);
        requests++;
        String raw;
        try {
            raw = rpc.call();
        } finally {
            lastCallNanos = System.nanoTime();
        }
        if (raw == null || raw.isEmpty()) return raw;
        JSONObject probe;
        try {
            probe = new JSONObject(raw);
        } catch (Exception malformed) {
            return raw;
        }
        boolean denied = RpcFailurePolicy.isRiskDenied(probe.optString("error", ""), probe.optString("errorMessage", ""));
        if (denied) {
            RuntimeInfo.getInstance().put(COOLDOWN_KEY, RequestBudgetPolicy.cooldownUntil(
                    System.currentTimeMillis(), RpcFailurePolicy.RISK_DENIED_MS));
            Log.record("其他任务：" + label + "触发风控，暂停24小时");
            throw new Denied();
        }
        return raw;
    }
}
