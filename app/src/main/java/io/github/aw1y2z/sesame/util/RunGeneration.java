package io.github.aw1y2z.sesame.util;

import java.util.function.LongSupplier;

/**
 * 协作式「代际令牌」：线程起跑时绑定自己那一代，停止时由所属任务递增代际；线程在检查点
 * （{@link TimeUtil#sleep}、RPC 桥入口）判断本代是否作废并结束本轮，避免与新一代并发踩踏共享状态。
 * <p>未绑定的线程一律视为「未作废」，不受影响。
 */
public final class RunGeneration {

    private static final ThreadLocal<RunGeneration> CURRENT = new ThreadLocal<>();

    private final long myGen;
    private final LongSupplier ownerGen;

    private RunGeneration(long myGen, LongSupplier ownerGen) {
        this.myGen = myGen;
        this.ownerGen = ownerGen;
    }

    /** 绑定本代令牌（{@code myGen} 为快照、{@code ownerGen} 取当前代际）；返回此前的令牌供 {@link #restore} 还原 */
    public static RunGeneration bind(long myGen, LongSupplier ownerGen) {
        RunGeneration prev = CURRENT.get();
        CURRENT.set(new RunGeneration(myGen, ownerGen));
        return prev;
    }

    /** 还原 {@link #bind} 之前的令牌；嵌套执行（isSync 会在同一线程内联起跑）时不能直接清空 */
    public static void restore(RunGeneration prev) {
        if (prev == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(prev);
        }
    }

    /** 本线程所属的这一代是否已作废；未绑定（非受管任务线程）时恒为 false */
    public static boolean isStale() {
        RunGeneration token = CURRENT.get();
        return token != null && token.myGen != token.ownerGen.getAsLong();
    }
}
