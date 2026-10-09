package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONObject;

import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TimeUtil;

/** 每轮请求预算与间隔；持久退避复用RpcRequestGuard，避免一个活动阻断所有其他任务。 */
final class OtherRequestGate {
    /** 覆盖账户查询+宝箱+任务+天赋+图鉴+守护+事件等全部分支的宽松上限，超出说明业务分支异常，不该继续重试。 */
    private static final int MAX_REQUESTS_PER_RUN = 80;

    interface RpcCall { String call() throws Exception; }

    static final class Denied extends RuntimeException { }
    static final class BudgetExhausted extends RuntimeException { }

    private int requests;
    private long lastCallNanos;

    /** 预算耗尽/命中风控都会抛异常，调用方按现有的 try/catch(Throwable) 结构自然结束本轮。 */
    String call(String label, RpcCall rpc) throws Exception {
        TimeUtil.sleep(0);
        if (!RequestBudgetPolicy.mayRequest(requests, MAX_REQUESTS_PER_RUN)) {
            Log.record("其他任务：" + label + "触发本轮请求上限，停止本轮");
            throw new BudgetExhausted();
        }
        long pause = RequestBudgetPolicy.pacingMs(lastCallNanos, System.nanoTime());
        if (pause > 0) TimeUtil.sleep(pause);
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
            probe = MyUtils.newJSONObject(raw);
        } catch (Exception malformed) {
            return raw;
        }
        boolean denied = RpcFailurePolicy.isRiskDenied(probe.optString("error", ""), probe.optString("errorMessage", ""))
                || RpcFailurePolicy.isRiskDenied(probe.optString("resultCode", ""), "")
                || RpcFailurePolicy.isRiskDenied(probe.optString("retCode", ""), "")
                || RpcFailurePolicy.isRiskDenied(probe.optString("errorCode", ""), "");
        if (denied) {
            Log.record("其他任务：" + label + "服务端拒绝，结束本轮；退避由RPC保护按当前账号/接口处理");
            throw new Denied();
        }
        return raw;
    }
}
