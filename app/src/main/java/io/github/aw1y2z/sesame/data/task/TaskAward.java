package io.github.aw1y2z.sesame.data.task;

import java.util.function.Consumer;

/**
 * 领奖统一收口：动作响应不可信，**是否领到以任务列表为准**。
 *
 * <p>唯一职责是把一条不变式固定下来：**列表复核必须发生在自动拉黑之前**。
 * 先落盘拉黑、再复核，复核就等于没做——曾因此在运动任务上把"其实已领到"的任务误拉黑。
 *
 * <p>调用点只提供两件事：探针（怎么按定位键判定"已领到"）与拉黑动作（该模块怎么记账）；
 * 顺序与"已领到"的日志文案由本类负责，调用点无法写反。
 */
public final class TaskAward {

    private TaskAward() {
    }

    /**
     * 领奖未成功时的统一收口：先按任务列表复核"已领到"，未确认才执行自动拉黑。
     *
     * <p>领奖动作本身（含成功判据与成功日志）仍由调用点负责：各模块的判据
     * （{@code checkSuccess/checkMemo/checkResultCode}）与成功文案（含奖励数额）不同，不属于本类职责。
     *
     * @param logPrefix 日志前缀（各模块自己的动作名，如"森林寻宝🎖️"）；日志形如
     *                  {@code <前缀>[标题]#已按列表确认领取}
     * @param probe     列表状态探针；{@code null} 表示该调用点无法复核（直接拉黑）
     * @param key       探针定位键（与完成判定用的键保持一致）
     * @param title     任务展示名（日志与黑名单条目共用）
     * @param blackList 拉黑动作；{@code null} 表示不拉黑（只记日志）
     * @param log       日志出口（传各模块自己的出口，日志归口不变）
     * @return true = 列表确认已领到
     */
    public static boolean confirmReceivedOrBlackList(String logPrefix, TaskAttemptPolicy.StatusProbe probe,
                                                     String key, String title, Runnable blackList,
                                                     Consumer<String> log) {
        if (probe != null && TaskAttemptPolicy.isReceived(probe.probe(key))) {
            if (log != null) {
                log.accept(logPrefix + "[" + title + "]#已按列表确认领取");
            }
            return true;
        }
        if (blackList != null) {
            blackList.run();
        }
        return false;
    }
}
