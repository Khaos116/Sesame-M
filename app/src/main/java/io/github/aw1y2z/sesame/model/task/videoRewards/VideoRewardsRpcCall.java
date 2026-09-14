package io.github.aw1y2z.sesame.model.task.videoRewards;

/** 方法名/参数常量；实际请求走 IsolatedRewardTask.Run 的预算+幂等控制，不直接调用。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoRewardsRpcCall {
    public static final String WALLET_METHOD = "alipay.content.interact.task.wallet.v2";
    public static final String WALLET_ARGS = "[{\"pageIndex\":1,\"pageSize\":10,\"walletTab\":\"available\"}]";
    public static final String RESERVE_METHOD = "alipay.content.interact.task.reserve";
    public static final String RESERVE_ARGS = "[{\"sourcePage\":\"\",\"taskType\":\"reserve\"}]";
    private VideoRewardsRpcCall() { }
}
