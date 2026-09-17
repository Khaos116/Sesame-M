package io.github.aw1y2z.sesame.util;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.Logger;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
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

    private static Logger getUserLogger(String type, String tag, String pattern) {
        String userId = UserIdMap.getCurrentUid();
        String key = type + "::" + tag + "::" + (userId == null || userId.isEmpty() ? "default" : userId);
        return LOGGER_CACHE.computeIfAbsent(key, k -> XLog.tag(tag).printers(
                new FilePrinter.Builder(FileUtil.getUserLogDirectory(userId).getPath())
                        .fileNameGenerator(new CustomDateFileNameGenerator(type))
                        .backupStrategy(new NeverBackupStrategy())
                        .cleanStrategy(new NeverCleanStrategy())
                        .flattener(new PatternFlattener(pattern))
                        .build()).build());
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

    private static Logger systemLogger() {
        return getUserLogger("system", "SYSTEM", "{d HH:mm:ss.SSS} {t}: {m}");
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

    public static void i(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger().i(s);
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
            runtimeLogger().i(str);
        }
    }

    public static void record(String TAG, String msg) {
        record("[" + TAG + "]: " + msg);
    }

    public static void system(String tag, String s) {
        // system 记录(配置加载/保存/重置等)同样受「查看运行日志」开关控制,
        // 避免关闭运行日志后仍持续写入 system 日志
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        systemLogger().i(tag + ", " + s);
    }

    public static void forest(String s) {
        countModuleLog();
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeForestLogger().i(s);
        }
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableForestLog()) {
            forestLogger().i(s);
        }
    }

    public static void goldenBeans(String s) {
        countModuleLog();
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeGoldenBeansLogger().i(s);
        }
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableGoldenBeansLog()) {
            goldenBeansLogger().i(s);
        }
    }

    public static void farm(String s) {
        countModuleLog();
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeFarmLogger().i(s);
        }
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableFarmLog()) {
            farmLogger().i(s);
        }
    }

    public static void other(String s) {
        countModuleLog();
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            runtimeOtherLogger().i(s);
        }
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableOtherLog()) {
            otherLogger().i(s);
        }
    }

    public static void debug(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableDebugLog()) {
            return;
        }
        debugLogger().d(s);
    }

    public static void error(String s) {
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog()) {
            errorLogger().i(s);
        }
        i(s);
    }

    public static void error(String TAG, String msg) {
        error("[" + TAG + "]: " + msg);
    }

    public static void printStackTrace(Throwable t) {
        String str = android.util.Log.getStackTraceString(t);
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog()) {
            errorLogger().i(str);
        }
        i(str);
    }

    public static void printStackTrace(String tag, Throwable t) {
        String str = tag + ", " + android.util.Log.getStackTraceString(t);
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog()) {
            errorLogger().i(str);
        }
        i(str);
    }

    public static void printStackTrace(String TAG, String msg, Throwable th) {
        String str = "[" + TAG + "] Throwable error: " + android.util.Log.getStackTraceString(th);
        if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewErrorLog()) {
            errorLogger().i(str + "[" + msg + "]");
        }
        i(str);
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
