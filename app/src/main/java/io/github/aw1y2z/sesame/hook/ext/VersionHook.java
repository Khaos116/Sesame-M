package io.github.aw1y2z.sesame.hook.ext;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aw1y2z.sesame.entity.AlipayVersion;
import io.github.aw1y2z.sesame.util.ClassUtil;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

/**
 * 版本伪装 Hook - 拦截 PackageManager.getPackageInfo() 向支付宝服务端伪造版本号。
 * 对齐 GR2026 main_my hook/ext/VersionHook.java（提交 3013cb36），API 从传统 Xposed
 * （XposedHelpers/XC_MethodHook）换成 M 自己的兼容层（XHelpers/compat.XC_MethodHook）。
 * <p>
 * 默认关闭（1.1.5 曾默认开启，实测无效已改回；见 CHANGELOG）。手动开启后可在扩展页改版本，重启支付宝生效。启用后会让支付宝服务端认为客户端是伪装的低版本，用于规避高版本才有的
 * 拼图验证码风控——这是主动欺骗服务端的行为，不是单纯跳过本地判断分支，用户需知悉
 * 风险，见 doc/MyFix.md 的移植记录。
 */
public class VersionHook {

    /** 默认伪装版本（对齐 GR2026 AppConfig 默认值 10.6.58.8000 / 1881，高于新接口最低支持的 10.3.96.8100） */
    /** 标记“已按 1.1.5 之后的默认关闭处理过”，用于一次性把 1.1.5 自动打开的配置改回关闭 */
    private static final String KEY_DEFAULT_OFF_APPLIED = "defaultOffApplied";
    private static final String DEFAULT_VERSION_NAME = "10.6.58.8000";
    private static final long DEFAULT_VERSION_CODE = 1881L;

    private static String sCachedVersionName = DEFAULT_VERSION_NAME;
    private static long sCachedVersionCode = DEFAULT_VERSION_CODE;
    // 配置文件加载前保持 false：早于 loadVersionConfig 的 getPackageInfo 不应被误伪装；默认开启由配置文件承载
    private static boolean sEnableVersionHook = false;
    private static boolean isVersionHookRegistered = false;

    private static AlipayVersion sAlipayVersion;

    // ---- 诊断：判断支付宝自己读版本号时有没有真的被改写（见 diagnostics()）----
    /** 配置文件加载前支付宝读取自身版本的次数（此时开关还是关，拿到的是真实版本） */
    private static final AtomicInteger sEarlyReads = new AtomicInteger();
    /** 已改写成伪装版本的次数 */
    private static final AtomicInteger sFakedReads = new AtomicInteger();
    /** 配置已加载但开关是关的次数 */
    private static final AtomicInteger sOffReads = new AtomicInteger();
    /** 走 getPackageInfo(String, PackageInfoFlags)（API 33+）重载读取的次数，只统计不改写 */
    private static final AtomicInteger sFlagsReads = new AtomicInteger();
    private static final List<String> sSamples = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_SAMPLES_PER_KIND = 6;
    private static volatile boolean sConfigLoaded = false;
    private static volatile boolean sSamplesPrinted = false;
    private static volatile boolean sFakedLogged = false;
    /**
     * 启动早期（配置未加载）时，是否对日志模块的读取提前伪装。缺省为关：读不到配置就不伪装；
     * 只有配置文件里 enableVersionHook 与 earlyEnable 都为真时 preloadEarlyEnable 才把它打开。
     */
    private static volatile boolean sEarlyFake = false;
    private static final AtomicInteger sEarlyFaked = new AtomicInteger();
    private static final String LOGGING_CALLER_PREFIX = "com.alipay.mobile.common.logging.";
    /** 配置文件里的 "earlyEnable"（缺省为真）：用户手写成 false 时，saveVersionConfig 覆盖写文件要带回去，否则会被抹掉 */
    private static volatile boolean sEarlyEnableConfigured = true;
    /** 当前线程正在读真实版本（readRealVersionName）时不改写 */
    private static final ThreadLocal<Boolean> sReadingReal = new ThreadLocal<>();
    /** 标记当前线程正处在 int 重载里，避免把它内部委托到 Flags 重载的那次重复统计 */
    private static final ThreadLocal<Boolean> sInIntOverload = new ThreadLocal<>();

    private VersionHook() {
    }

    // ==================== 配置初始化 ====================

    public static void ensureVersionConfig(Context context) {
        try {
            File configDir = FileUtil.MAIN_DIRECTORY_FILE;
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            File configFile = new File(configDir, "version_config.json");
            if (!configFile.exists()) {
                JSONObject defaultConfig = MyUtils.newJSONObject();
                defaultConfig.put("enableVersionHook", false);
                defaultConfig.put(KEY_DEFAULT_OFF_APPLIED, true);
                defaultConfig.put("versionName", DEFAULT_VERSION_NAME);
                defaultConfig.put("versionCode", DEFAULT_VERSION_CODE);
                FileUtil.write2File(defaultConfig.toString(), configFile);
                Log.record("已创建版本配置文件");
            }
        } catch (Throwable t) {
            Log.record("创建版本配置失败: " + t.getMessage());
        }
    }

    public static void loadVersionConfig() {
        try {
            File configFile = new File(FileUtil.MAIN_DIRECTORY_FILE, "version_config.json");
            if (!configFile.exists()) {
                return;
            }
            String content = FileUtil.readFromFile(configFile);
            if (content == null || content.isEmpty()) {
                return;
            }
            JSONObject config = MyUtils.newJSONObject(content);

            sEnableVersionHook = config.optBoolean("enableVersionHook", false);
            sCachedVersionName = config.optString("versionName", DEFAULT_VERSION_NAME);
            sCachedVersionCode = config.optLong("versionCode", DEFAULT_VERSION_CODE);
            sEarlyEnableConfigured = config.optBoolean("earlyEnable", true);

            // 1.1.5 曾把“默认开启”写进配置（旧的“关闭且未填写”配置被自动迁移为开启 10.6.58.8000/1881），
            // 实测对验证码类型没有效果，默认改回关闭：没有 defaultOffApplied 标记、且正好是 1.1.5 自动写入的默认值
            // （开启 + 10.6.58.8000 + 1881）的配置视为没被用户主动设置过，一次性改回关闭并打标记；
            // 用户自己改过版本名/版本号或已带标记的配置不动
            if (!config.has(KEY_DEFAULT_OFF_APPLIED)) {
                if (sEnableVersionHook && DEFAULT_VERSION_NAME.equals(sCachedVersionName)
                        && sCachedVersionCode == DEFAULT_VERSION_CODE) {
                    sEnableVersionHook = false;
                    saveVersionConfig();
                    Log.record("版本伪装已改回默认关闭（1.1.5 自动开启的配置，实测无效）；需要请在扩展页手动开启");
                } else {
                    saveVersionConfig();
                }
            }

            Log.i("VersionHook", "配置加载完成: enabled=" + sEnableVersionHook
                    + ", name=" + sCachedVersionName + ", code=" + sCachedVersionCode);
        } catch (Throwable t) {
            Log.i("VersionHook", "加载配置失败: " + t.getMessage());
        } finally {
            sConfigLoaded = true;
        }
    }

    /**
     * 真正的调用方帧（最多 max 个）：扫描整个调用栈，跳过 hook 机制帧（LSPosed/Vector/LSPatch/libxposed）、
     * 反射/系统包管理帧和本模块自己的帧。
     * 不能用“栈顶 N 帧里找最后一个 hook 帧”的做法：多层 hook 嵌套（LSPatch 加载器等）时框架帧能超过 N 帧，
     * 窗口内找不到就会把框架帧当成调用方（真机日志已出现，导致日志模块读取识别失败）；
     * R8 会把本模块的类改成 yb2/ac2 这种不带包名的短名，所以类名不含 '.' 的一律当作本模块帧跳过。
     */
    private static List<StackTraceElement> callerFrames(int max) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        List<StackTraceElement> frames = new ArrayList<>();
        for (int i = 0; i < stack.length && frames.size() < max; i++) {
            String c = stack[i].getClassName();
            if (c.indexOf('.') < 0 || c.contains("LSPHooker") || c.startsWith("LSPatch_")
                    || c.startsWith("org.matrix.vector") || c.startsWith("org.lsposed")
                    || c.startsWith("io.github.libxposed") || c.startsWith("io.github.aw1y2z")
                    || c.startsWith("java.lang.reflect") || c.startsWith("dalvik.system")
                    || c.startsWith("android.app.ApplicationPackageManager")) {
                continue;
            }
            frames.add(stack[i]);
        }
        return frames;
    }

    /** 每种类型各留 MAX_SAMPLES_PER_KIND 条读取来源（相同来源只留一条）；启动早期只缓存不直接打日志，避免日志系统未就绪。 */
    private static void sample(String kind) {
        StringBuilder callers = new StringBuilder();
        for (StackTraceElement e : callerFrames(4)) {
            if (callers.length() > 0) {
                callers.append('<');
            }
            callers.append(e.getClassName()).append('.').append(e.getMethodName());
        }
        String entry = kind + "@" + callers;
        synchronized (sSamples) {
            if (sSamples.contains(entry)) {
                return;
            }
            int same = 0;
            for (String existing : sSamples) {
                if (existing.startsWith(kind + "@")) {
                    same++;
                }
            }
            if (same < MAX_SAMPLES_PER_KIND) {
                sSamples.add(entry);
            }
        }
    }

    /**
     * 读取方是不是支付宝的日志/上下文模块（LogContextImpl 启动时读一次版本并缓存，是发给服务端的“应用版本”的
     * 最可能来源）。只看紧邻的 2 个调用帧，避免把同一启动链上更外层的 quinox UpgradeHelper 等误算进来。
     */
    private static boolean fromLoggingModule() {
        for (StackTraceElement e : callerFrames(2)) {
            if (e.getClassName().startsWith(LOGGING_CALLER_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每轮执行时打印一行：开关/Hook 是否注册、支付宝读取自身版本的各种情况计数，第一次带上读取来源。
     * 判读：“已伪装”为 0 说明支付宝根本没经过我们改写；“开关就绪前”很多说明它启动时就读走了真实版本；
     * “PackageInfoFlags 重载”非 0 说明它走了我们没改写的重载。
     */
    public static String diagnostics() {
        StringBuilder sb = new StringBuilder("版本伪装诊断：开关=").append(sEnableVersionHook ? "开" : "关")
                .append("，Hook=").append(isVersionHookRegistered ? "已注册" : "未注册").append(sEarlyFake ? "，提前伪装=开" : "，提前伪装=关")
                .append("，支付宝读取自身版本：开关就绪前").append(sEarlyReads.get()).append("次(真实版本)")
                .append("，早期伪装(日志模块)").append(sEarlyFaked.get()).append("次")
                .append("，已伪装").append(sFakedReads.get()).append("次")
                .append("，开关关闭时").append(sOffReads.get()).append("次")
                .append("，PackageInfoFlags重载").append(sFlagsReads.get()).append("次");
        if (!sSamplesPrinted && !sSamples.isEmpty()) {
            sSamplesPrinted = true;
            synchronized (sSamples) {
                sb.append("；来源：").append(String.join(" | ", sSamples));
            }
        }
        return sb.toString();
    }

    public static void saveVersionConfig() {
        try {
            File configFile = new File(FileUtil.MAIN_DIRECTORY_FILE, "version_config.json");
            JSONObject config = MyUtils.newJSONObject();
            config.put("enableVersionHook", sEnableVersionHook);
            config.put("versionName", sCachedVersionName);
            config.put("versionCode", sCachedVersionCode);
            config.put(KEY_DEFAULT_OFF_APPLIED, true);
            if (!sEarlyEnableConfigured) {
                config.put("earlyEnable", false);
            }
            FileUtil.write2File(config.toString(), configFile);
        } catch (Throwable t) {
            Log.record("保存版本配置失败: " + t.getMessage());
        }
    }

    // ========== Getter/Setter ==========

    public static void setAlipayVersion(AlipayVersion version) {
        sAlipayVersion = version;
    }

    public static String getCachedVersionName() {
        return sCachedVersionName;
    }

    public static long getCachedVersionCode() {
        return sCachedVersionCode;
    }

    public static boolean isVersionHookEnabled() {
        return sEnableVersionHook;
    }

    public static void setEnableVersionHook(boolean enable) {
        sEnableVersionHook = enable;
    }

    public static void setVersionName(String name) {
        sCachedVersionName = (name != null) ? name : "";
    }

    public static void setVersionCode(long code) {
        sCachedVersionCode = code;
    }

    public static String getFakeVersionName() {
        if (!sEnableVersionHook) {
            return "";
        }
        return (sCachedVersionName != null && !sCachedVersionName.isEmpty())
                ? sCachedVersionName : DEFAULT_VERSION_NAME;
    }

    public static long getFakeVersionCode() {
        if (!sEnableVersionHook) {
            return 0;
        }
        return sCachedVersionCode > 0 ? sCachedVersionCode : DEFAULT_VERSION_CODE;
    }

    // ==================== 版本伪装 Hook ====================

    /**
     * 注册 PackageManager.getPackageInfo() Hook，拦截所有对支付宝包名的版本查询。
     * classLoader 用宿主（支付宝）进程的 classloader 加载框架类，不能像传统 Xposed
     * 那样传 null——M 的 {@link XHelpers#findClass} 没有对 null classloader 做特殊兜底。
     */
    public static void initVersionHook(ClassLoader classLoader) {
        if (isVersionHookRegistered) {
            Log.i("VersionHook", "Hook 已注册，跳过重复注册");
            return;
        }
        preloadEarlyEnable();

        try {
            XHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            // 只对支付宝自己的包名置位：支付宝会频繁探测微信/QQ 等是否安装，未安装时原方法抛
                            // NameNotFoundException，XHelpers 此时不会执行 afterHookedMethod，置位就再也清不掉，
                            // 这个线程之后走 Flags 重载的伪装会被永久跳过。查自己的包不会抛这个异常。
                            if (param.args.length > 0 && ClassUtil.PACKAGE_NAME.equals(param.args[0])) {
                                sInIntOverload.set(Boolean.TRUE);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            sInIntOverload.remove();
                            handleRead(param, false);
                        }
                    }
            );

            isVersionHookRegistered = true;
            Log.i("VersionHook", "getPackageInfo Hook 注册成功");
            hookFlagsOverload(classLoader);
        } catch (Throwable t) {
            Log.i("VersionHook", "版本Hook注册失败: " + t.getMessage());
            Log.printStackTrace("VersionHook", t);
        }
    }

    /**
     * API 33+ 的 getPackageInfo(String, PackageInfoFlags) 重载：新代码走这个，只 hook int 重载会被绕开。
     * int 重载内部委托到这里时跳过（int 重载返回后会改写同一个对象，这里再改是重复）。
     */
    private static void hookFlagsOverload(ClassLoader classLoader) {
        try {
            Class<?> flags = Class.forName("android.content.pm.PackageManager$PackageInfoFlags");
            XHelpers.findAndHookMethod("android.app.ApplicationPackageManager", classLoader, "getPackageInfo",
                    String.class, flags, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(sInIntOverload.get())) {
                                return;
                            }
                            handleRead(param, true);
                        }
                    });
        } catch (Throwable t) {
            // API < 33 没有这个类，属正常
            Log.i("VersionHook", "未挂 PackageInfoFlags 重载: " + t.getClass().getSimpleName());
        }
    }

    /** 两个重载共用：读到的是支付宝自己的包信息时，按开关改写版本名/版本号，并累计诊断计数。 */
    private static void handleRead(XC_MethodHook.MethodHookParam param, boolean viaFlags) {
        if (Boolean.TRUE.equals(sReadingReal.get())) {
            return; // 模块自己要读真实版本（readRealVersionName）
        }
        Object info = param.getResult();
        if (info == null) {
            return;
        }
        String pkg = (String) XHelpers.getObjectField(info, "packageName");
        if (!ClassUtil.PACKAGE_NAME.equals(pkg)) {
            return;
        }
        if (viaFlags) {
            sFlagsReads.incrementAndGet();
        }
        String via = viaFlags ? "/Flags" : "/int";
        boolean earlyFake = false;
        if (!sEnableVersionHook) {
            // 配置还没加载：支付宝日志模块启动时读一次版本并缓存，之后不再读，等配置加载好就来不及了，
            // 所以对它单独提前伪装；其它早读（如 quinox 升级检查）保持真实版本，避免影响支付宝启动
            if (!sConfigLoaded && sEarlyFake && fromLoggingModule()) {
                earlyFake = true;
            } else {
                (sConfigLoaded ? sOffReads : sEarlyReads).incrementAndGet();
                sample((sConfigLoaded ? "关闭" : "早读") + via);
                return;
            }
        }
        String versionName = earlyName();
        long versionCode = earlyCode();
        XHelpers.setObjectField(info, "versionName", versionName);
        try {
            PackageInfo.class.getMethod("setLongVersionCode", long.class).invoke(info, versionCode);
        } catch (Throwable ignored) {
            XHelpers.setObjectField(info, "versionCode", (int) versionCode);
        }
        param.setResult(info);
        if (earlyFake) {
            // 启动早期日志系统可能还没就绪，这里只计数，不直接打日志
            sEarlyFaked.incrementAndGet();
            sample("早期伪装" + via);
            return;
        }
        sFakedReads.incrementAndGet();
        sample("已伪装" + via);
        if (!sFakedLogged) {
            sFakedLogged = true;
            Log.record("版本伪装已生效: " + versionName + " (code=" + versionCode + ")");
        }
    }

    /**
     * 启动早期（Application.attach 之前）按配置文件决定是否“提前伪装”：默认关闭（sEarlyFake 缺省为假），
     * 文件不存在或读取失败都保持关闭；文件存在时要求 enableVersionHook 为真（旧版自动建的“关闭且未填写”配置会在 loadVersionConfig 迁移为开启，
     * 这里同样按开启看待）且 earlyEnable 不为 false。只影响支付宝日志模块的那次读取（见 handleRead），
     * 不像“全部早读都伪装”那样波及 quinox 升级检查等启动逻辑。
     */
    private static void preloadEarlyEnable() {
        try {
            File configFile = new File(FileUtil.MAIN_DIRECTORY_FILE, "version_config.json");
            if (!configFile.exists()) {
                return;
            }
            JSONObject config = MyUtils.newJSONObject(FileUtil.readFromFile(configFile));
            String name = config.optString("versionName", "");
            long code = config.optLong("versionCode", 0);
            // 与 loadVersionConfig 的一次性回退保持一致：没有 defaultOffApplied 标记的配置里，1.1.5 自动写入的
            // 默认值（开启 + 默认版本）在本次启动会被改回关闭，这里同样按关闭看待
            boolean autoEnabledByOldDefault = !config.has(KEY_DEFAULT_OFF_APPLIED)
                    && DEFAULT_VERSION_NAME.equals(name) && code == DEFAULT_VERSION_CODE;
            boolean enabled = config.optBoolean("enableVersionHook", false) && !autoEnabledByOldDefault;
            sEarlyEnableConfigured = config.optBoolean("earlyEnable", true);
            if (!name.isEmpty()) {
                sCachedVersionName = name;
            }
            if (code > 0) {
                sCachedVersionCode = code;
            }
            sEarlyFake = enabled && sEarlyEnableConfigured;
        } catch (Throwable t) {
            Log.i("VersionHook", "提前读取版本伪装配置失败: " + t.getClass().getSimpleName());
        }
    }

    private static String earlyName() {
        return (sCachedVersionName != null && !sCachedVersionName.isEmpty()) ? sCachedVersionName : DEFAULT_VERSION_NAME;
    }

    private static long earlyCode() {
        return sCachedVersionCode > 0 ? sCachedVersionCode : DEFAULT_VERSION_CODE;
    }

    /** 读支付宝真实版本名（绕过伪装），给模块自己记录“实际版本”用。 */
    public static String readRealVersionName(Context context) throws Exception {
        sReadingReal.set(Boolean.TRUE);
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } finally {
            sReadingReal.remove();
        }
    }

    /** 获取用于展示/上报的版本号：伪装开启时返回伪装版本，否则返回真实版本。 */
    public static String getDisplayVersion() {
        if (sEnableVersionHook) {
            return getFakeVersionName();
        }
        if (sAlipayVersion != null) {
            return sAlipayVersion.getVersionString();
        }
        return "";
    }
}
