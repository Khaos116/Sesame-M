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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Log {

    static {
        XLog.init(LogLevel.ALL);
    }

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }

    };

    public static final ThreadLocal<SimpleDateFormat> DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        }

    };

    public static final ThreadLocal<SimpleDateFormat> OTHER_DATE_TIME_FORMAT_THREAD_LOCAL = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
        }

    };

    /**
     * runtime 日志文件的共用打印机。
     * <p>5 个 tag（RUNTIME / FOREST / GOLDENBEANS / FARM / OTHER）共写同一个 runtime 文件，
     * 共用一组缓冲与后台 worker，避免多个 printer 同时写同一文件带来的行交错与线程浪费。
     */
    private static final Printer RUNTIME_FILE_PRINTER = new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
            .fileNameGenerator(new CustomDateFileNameGenerator("runtime"))
            .backupStrategy(new NeverBackupStrategy())
            .cleanStrategy(new NeverCleanStrategy())
            .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
            .build();

    /** 通用运行日志（用于 system/i 调用），tag 固定为 RUNTIME */
    private static final Logger runtimeLogger = XLog.tag("RUNTIME").printers(RUNTIME_FILE_PRINTER).build();

    /** 各模块向 runtime.log 写入时使用的专用 logger（不同 tag，同文件） */
    private static final Logger runtimeForestLogger = XLog.tag("FOREST").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeGoldenBeansLogger = XLog.tag("GOLDENBEANS").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeFarmLogger = XLog.tag("FARM").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger runtimeOtherLogger = XLog.tag("OTHER").printers(RUNTIME_FILE_PRINTER).build();

    private static final Logger debugLogger = XLog.tag("DEBUG").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("debug"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger forestLogger = XLog.tag("FOREST").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("forest"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger goldenBeansLogger = XLog.tag("GOLDENBEANS").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("goldenbeans"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger farmLogger = XLog.tag("FARM").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("farm"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger otherLogger = XLog.tag("OTHER").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("other"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

    private static final Logger errorLogger = XLog.tag("ERROR").printers(
            new FilePrinter.Builder(FileUtil.LOG_DIRECTORY_FILE.getPath())
                    .fileNameGenerator(new CustomDateFileNameGenerator("error"))
                    .backupStrategy(new NeverBackupStrategy())
                    .cleanStrategy(new NeverCleanStrategy())
                    .flattener(new PatternFlattener("{d HH:mm:ss.SSS} {t}: {m}"))
                    .build()).build();

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
    private static void writeModuleLog(String s, boolean toRuntime, Logger runtimeTarget,
                                       boolean toFile, Logger fileTarget) {
        if (!toRuntime && !toFile) {
            return;
        }
        String msg = withUser(s);
        if (toRuntime) {
            runtimeTarget.i(msg);
        }
        if (toFile) {
            fileTarget.i(msg);
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
            errorLogger.i(msg);
        }
        if (toRuntime) {
            runtimeLogger.i(msg);
        }
    }

    public static void i(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog()) {
            return;
        }
        runtimeLogger.i(withUser(s));
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
            runtimeLogger.i(withUser(str));
        }
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
        runtimeLogger.i(withUser(tag + ", " + s));
    }

    public static void forest(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeForestLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableForestLog(), forestLogger);
    }

    public static void goldenBeans(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeGoldenBeansLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableGoldenBeansLog(), goldenBeansLogger);
    }

    public static void farm(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeFarmLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableFarmLog(), farmLogger);
    }

    public static void other(String s) {
        countModuleLog();
        writeModuleLog(s, io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableViewRuntimeLog(), runtimeOtherLogger,
                io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableOtherLog(), otherLogger);
    }

    public static void debug(String s) {
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableDebugLog()) {
            return;
        }
        debugLogger.d(withUser(s));
    }

    public static void error(String s) {
        writeError(s);
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

    public static String getLogFileName(String logName) {
        SimpleDateFormat sdf = DATE_FORMAT_THREAD_LOCAL.get();
        if (sdf == null) {
            sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        }
        return logName + "." + sdf.format(new Date()) + ".log";
    }

    public static String getFormatDateTime() {
        SimpleDateFormat simpleDateFormat = DATE_TIME_FORMAT_THREAD_LOCAL.get();
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
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
                simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault());
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
                return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
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
                sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            }
            return name + "." + sdf.format(new Date(timestamp)) + ".log";
        }
    }

}
