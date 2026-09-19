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
     * 统一日志写入口：在消息前加上账号简称（账号1、账号2…），便于多账号下区分日志来源。
     * <p>序号由 {@code UserIdMap} 首次出现时分配并持久化，与配置页显示的账号序号一致；
     * 日志里**不写 uid、也不写昵称**，避免日志被分享/导出时把账号信息带出去。
     * <p>简称在调用线程读取；uid 为空时保持原样。查看器按「时间 tag: 正文」解析，
     * 前缀会落在正文里，不影响解析。
     */
    private static String withUser(String msg) {
        try {
            String label = io.github.aw1y2z.sesame.util.idMap.UserIdMap.getAccountLabel(
                    io.github.aw1y2z.sesame.util.idMap.UserIdMap.getCurrentUid());
            return StringUtil.isEmpty(label) ? msg : "[" + label + "]" + msg;
        } catch (Throwable t) {
            return msg;
        }
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

    /**
     * 错误日志双写（异常日志 + 运行日志）：消息统一带 uid 前缀。
     */
    private static void writeError(String s) {
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
        writeError(android.util.Log.getStackTraceString(t));
    }

    public static void printStackTrace(String tag, Throwable t) {
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
