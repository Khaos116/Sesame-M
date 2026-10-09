package io.github.aw1y2z.sesame.util;

/** 当日已完成的查询流程；不清写回执、尝试次数或服务端冷却。 */
public final class DailyTask {
    private static final String PREFIX = "dailyTask::";
    private static final ThreadLocal<Boolean> MANUAL = new ThreadLocal<>();
    private DailyTask() { }

    public static boolean isManual() { return Boolean.TRUE.equals(MANUAL.get()); }

    public static boolean skip(String key, String label) {
        if (isManual()) { Status.clearFlag(PREFIX + key); return false; }
        if (!Status.hasFlagToday(PREFIX + key)) return false;
        Log.record(label + "：当天已经成功执行，自动调度跳过；可点击立即执行重新检查");
        return true;
    }

    public static void done(String key) { Status.flagToday(PREFIX + key); }

    /** 复用业务已确认的成功标记；不适用于尝试、限额或失败标记。 */
    public static boolean skipFlag(String flag, String label) {
        if (isManual() || !Status.hasFlagToday(flag)) return false;
        Log.record(label + "：当天已经成功执行，自动调度跳过；可点击立即执行重新检查");
        return true;
    }

    public static <T> T manual(java.util.function.Supplier<T> action) {
        Boolean previous = MANUAL.get();
        MANUAL.set(true);
        try { return action.get(); }
        finally { if (previous == null) MANUAL.remove(); else MANUAL.set(previous); }
    }

    /** 只传给本轮主任务，定时子任务不继承手动重查。 */
    public static Runnable capture(Runnable action) {
        boolean manual = isManual();
        return () -> {
            Boolean previous = MANUAL.get();
            MANUAL.set(manual);
            try { action.run(); }
            finally { if (previous == null) MANUAL.remove(); else MANUAL.set(previous); }
        };
    }
}
