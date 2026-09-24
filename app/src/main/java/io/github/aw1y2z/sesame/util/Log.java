package io.github.aw1y2z.sesame.util;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.clean.NeverCleanStrategy;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class Log {

    static {
        XLog.init(LogLevel.ALL);
    }

    private static SimpleDateFormat newDateFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
        return format;
    }

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return newDateFormat("yyyy-MM-dd");
        }

    };

    public static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return newDateFormat("yyyy-MM-dd HH:mm:ss");
        }

    };

    public static final ThreadLocal<SimpleDateFormat> OTHER_DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return newDateFormat("yyyy.MM.dd HH:mm:ss");
        }

    };

    /**
     * 每个日志类型 × 账号一个 Logger，懒加载缓存。账号切换后 {@link UserIdMap#getCurrentUid()}
     * 变化，下一次取 Logger 时 key 跟着变，自动落到新账号的日志目录（{@code log/<userId或default>/}）。
     */
    private static final Map<String, Logger> LOGGER_CACHE = new ConcurrentHashMap<>();

    /**
     * 同一账号同一类型（如 runtime 的 5 个 tag）共用一个 FilePrinter：多个 printer 写同一文件会行交错、浪费后台线程。
     */
    private static final Map<String, Printer> PRINTER_CACHE = new ConcurrentHashMap<>();

    private static Logger getUserLogger(String type, String tag, String pattern) {
        String userId = UserIdMap.getCurrentUid();
        String user = userId == null || userId.isEmpty() ? "default" : userId;
        return LOGGER_CACHE.computeIfAbsent(type + "::" + tag + "::" + user, k -> XLog.tag(tag).printers(
                PRINTER_CACHE.computeIfAbsent(type + "::" + user, pk -> new FilePrinter.Builder(FileUtil.getUserLogDirectory(userId).getPath())
                        .fileNameGenerator(new CustomDateFileNameGenerator(type))
                        .backupStrategy(new NeverBackupStrategy())
                        .cleanStrategy(new NeverCleanStrategy())
                        .flattener(new PatternFlattener(pattern))
                        .build())).build());
    }

    private static Logger runtimeLogger() {
        return getUserLogger("runtime", "RUNTIME", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    /** 各模块向 runtime.log 写入时使用的专用 logger（不同 tag，同文件），供日志页按 tag 过滤 */
    private static Logger runtimeForestLogger() {
        return getUserLogger("runtime", "FOREST", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger runtimeGoldenBeansLogger() {
        return getUserLogger("runtime", "GOLDENBEANS", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger runtimeCaptchaLogger() {
        return getUserLogger("runtime", "CAPTCHA", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger runtimeFarmLogger() {
        return getUserLogger("runtime", "FARM", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger runtimeOtherLogger() {
        return getUserLogger("runtime", "OTHER", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger recordLogger() {
        return getUserLogger("record", "RECORD", "{d HH:mm:ss.SSS} {m}");
    }

    private static Logger debugLogger() {
        return getUserLogger("debug", "DEBUG", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger forestLogger() {
        return getUserLogger("forest", "FOREST", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger goldenBeansLogger() {
        return getUserLogger("goldenbeans", "GOLDENBEANS", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger captchaLogger() {
        return getUserLogger("captcha", "CAPTCHA", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger farmLogger() {
        return getUserLogger("farm", "FARM", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger otherLogger() {
        return getUserLogger("other", "OTHER", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    private static Logger errorLogger() {
        return getUserLogger("error", "ERROR", "{d HH:mm:ss.SSS} {t}: {m}");
    }

    /**
     * 当前账号简称（账号1、账号2…），由 {@code UserIdMap} 在 uid 变化时写入；未知时为 null。
     * <p>刻意缓存成普通字段，而不是每条日志回头去问 UserIdMap：本模块的 UI 进程里没有
     * libxposed API，而 UserIdMap 引用了 ApplicationHook（继承 XposedModule），在 UI 进程里
     * 触达它可能抛 NoClassDefFoundError 把界面搞崩。日志是全项目最高频的调用，
     * 不能背这个依赖。
     */
    private static volatile String accountLabel = null;

    public static void setAccountLabel(String label) {
        accountLabel = StringUtil.isEmpty(label) ? null : label;
    }

    /**
     * 统一日志写入口：在消息前加上账号简称（账号1、账号2…），便于多账号下区分日志来源。
     * <p>序号由 {@code UserIdMap} 首次出现时分配并持久化，与配置页显示的账号序号一致；
     * 日志里**不写 uid、也不写昵称**，避免日志被分享/导出时把账号信息带出去。
     * <p>查看器按「时间 tag: 正文」解析，前缀会落在正文里，不影响解析。
     */
    private static String withUser(String msg) {
        String label = accountLabel;
        return label == null ? msg : "[" + label + "]" + msg;
    }

    /**
     * 模块日志双写（运行日志 + 分类文件）：只写开关打开的一侧，消息统一带 uid 前缀。
     */
    private static void writeModuleLog(String s, boolean toRuntime, Supplier<Logger> runtimeTarget,
                                       boolean toFile, Supplier<Logger> fileTarget) {
        if (!toRuntime && !toFile) {
            return;
        }
        String msg = withUser(s);
        if (toRuntime) {
            runtimeTarget.get().i(msg);
        }
        if (toFile) {
            fileTarget.get().i(msg);
        }
    }

    /** 本线程在「代际作废」期间被静音的错误日志条数 */
    private static final ThreadLocal<Integer> staleLogDropped = ThreadLocal.withInitial(() -> 0);

    /** 取出并清零本线程被静音的错误日志条数 */
    public static int takeDroppedStaleLogCount() {
        int dropped = staleLogDropped.get();
        staleLogDropped.set(0);
        return dropped;
    }

    /** 还原上层残留计数：嵌套执行时与 {@link RunGeneration#restore} 一样分层，数字不串 */
    public static void restoreDroppedStaleLogCount(int prev) {
        if (prev == 0) {
            staleLogDropped.remove();
        } else {
            staleLogDropped.set(prev);
        }
    }

    /**
     * 错误日志双写（异常日志 + 运行日志）：消息统一带 uid 前缀。
     */
    private static void writeError(String s) {
        // 本代已作废：此后本线程的错误日志都是收尾噪音（成片 catch(Throwable) 各打一行），整段静音；
        // 丢弃条数累计，任务收尾时报告，避免把这段时间的真故障无声吞掉
        if (RunGeneration.isStale()) {
            staleLogDropped.set(staleLogDropped.get() + 1);
            return;
        }
        boolean toError = io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog();
        boolean toRuntime = io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog();
        if (!toError && !toRuntime) {
            return;
        }
        String msg = withUser(s);
        if (toError) {
            errorLogger().i(msg);
        }
        if (toRuntime) {
            runtimeLogger().i(msg);
        }
    }

    public static void i(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger().i(withUser(s));
    }

    public static void i(String tag, String s) {
        i(tag + ", " + s);
    }

    /**
     * 当前任务线程的模块日志计数：用于判断某模块本轮是否产生了实际动作。
     * 计数与各日志开关无关，开关关闭时同样计数。
     */
    private static final ThreadLocal<int[]> MODULE_LOG_COUNTER = new ThreadLocal<>();

    /**
     * 开始统计当前线程的模块日志条数（由 ModelTask 在模块 run() 前调用）
     */
    public static void startModuleLogCount() {
        MODULE_LOG_COUNTER.set(new int[]{0});
    }

    /**
     * 结束统计并返回当前线程的模块日志条数
     */
    public static int stopModuleLogCount() {
        int[] counter = MODULE_LOG_COUNTER.get();
        MODULE_LOG_COUNTER.remove();
        return counter == null ? 0 : counter[0];
    }

    private static void countModuleLog() {
        int[] counter = MODULE_LOG_COUNTER.get();
        if (counter != null) {
            counter[0]++;
        }
    }

    public static void record(String str) {
        countModuleLog();
        // 记录日志(record)已停用,只按「查看运行日志」开关写入运行日志
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeLogger().i(withUser(str));
        }
    }

    public static void record(String TAG, String msg) {
        record("[" + TAG + "]: " + msg);
    }

    /**
     * system 记录(配置加载/保存/重置等)：统一并入运行日志，不再单独写 system.&lt;date&gt;.log。
     * <p>这些调用点旁边本就有一条内容相同的 Log.i，单独建文件只是重复副本，
     * 而且查看器里也没有对应的日志类目。仍受「查看运行日志」开关控制。
     */
    public static void system(String tag, String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger().i(withUser(tag + ", " + s));
    }

    public static void forest(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), Log::runtimeForestLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableForestLog(), Log::forestLogger);
    }

    public static void goldenBeans(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), Log::runtimeGoldenBeansLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableGoldenBeansLog(), Log::goldenBeansLogger);
    }

    /**
     * 验证码弹窗触发记录（类型、来源、运行中模块、最近请求、界面文字），单独一类日志便于统计哪些功能会触发验证。
     * 不调用 countModuleLog：验证弹窗不是模块动作，不能让“本轮无操作”提示失效。
     */
    public static void captcha(String s) {
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), Log::runtimeCaptchaLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableCaptchaLog(), Log::captchaLogger);
    }

    public static void farm(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), Log::runtimeFarmLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableFarmLog(), Log::farmLogger);
    }

    public static void other(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), Log::runtimeOtherLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableOtherLog(), Log::otherLogger);
    }

    public static void debug(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableDebugLog()) {
            return;
        }
        debugLogger().d(withUser(s));
    }

    public static void error(String s) {
        writeError(s);
    }

    public static void error(String TAG, String msg) {
        error("[" + TAG + "]: " + msg);
    }

    public static void printStackTrace(Throwable t) {
        // 代际作废不是故障：不写堆栈，避免切号/重载时在每个剩余动作上刷屏
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(android.util.Log.getStackTraceString(t));
    }

    public static void printStackTrace(String tag, Throwable t) {
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(tag + ", " + android.util.Log.getStackTraceString(t));
    }

    /**
     * 记录异常：一次调用同时写异常日志与运行日志，替代成对出现的
     * {@code Log.i(TAG, "xxx err:"); Log.printStackTrace(TAG, t);}。
     * <p>原先那种写法会占两行、只写其中一行时不易察觉，且运行日志里同一个异常会出现两行。
     *
     * @param tag 标签（通常传 TAG）
     * @param msg 说明，如 "answerQuestion err:"
     * @param t   异常
     */
    public static void err(String tag, String msg, Throwable t) {
        if (t instanceof TaskCancelledException) {
            return;
        }
        writeError(tag + ", " + msg + "\n" + android.util.Log.getStackTraceString(t));
    }

    public static void printStackTrace(String TAG, String msg, Throwable th) {
        writeError("[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(th) + "[" + msg + "]");
    }

    public static String getLogFileName(String logName) {
        SimpleDateFormat sdf = DATE_FORMAT_THREAD_LOCAL.get();
        if (sdf == null) {
            sdf = newDateFormat("yyyy-MM-dd");
        }
        return logName + "." + sdf.format(new Date()) + ".log";
    }

    public static String getFormatDateTime() {
        SimpleDateFormat simpleDateFormat = DATE_TIME_FORMAT_THREAD_LOCAL.get();
        if (simpleDateFormat == null) {
            simpleDateFormat = newDateFormat("yyyy-MM-dd HH:mm:ss");
        }
        return simpleDateFormat.format(new Date());
    }

    public static String getFormatDate() {
        return getFormatDateTime().split(" ")[0];
    }

    public static String getFormatTime() {
        return getFormatDateTime().split(" ")[1];
    }

    /* //日期转换为时间戳 */
    public static long timeToStamp(String timers) {
        Date d = new Date();
        long timeStamp;
        try {
            SimpleDateFormat simpleDateFormat = OTHER_DATE_TIME_FORMAT_THREAD_LOCAL.get();
            if (simpleDateFormat == null) {
                simpleDateFormat = newDateFormat("yyyy.MM.dd HH:mm:ss");
            }
            Date newD = simpleDateFormat.parse(timers);
            if (newD != null) {
                d = newD;
            }
        } catch (ParseException ignored) {
        }
        timeStamp = d.getTime();
        return timeStamp;
    }

    public static class CustomDateFileNameGenerator implements FileNameGenerator {

        ThreadLocal<SimpleDateFormat> mLocalDateFormat = new ThreadLocal<SimpleDateFormat>() {

            @Override
            protected SimpleDateFormat initialValue() {
                return newDateFormat("yyyy-MM-dd");
            }

        };

        private final String name;

        public CustomDateFileNameGenerator(String name) {
            this.name = name;
        }

        @Override
        public boolean isFileNameChangeable() {
            return true;
        }

        /**
         * Generate a file name which represent a specific date.
         */
        @Override
        public String generateFileName(int logLevel, long timestamp) {
            SimpleDateFormat sdf = mLocalDateFormat.get();
            if (sdf == null) {
                sdf = newDateFormat("yyyy-MM-dd");
            }
            return name + "." + sdf.format(new Date(timestamp)) + ".log";
        }
    }

}
