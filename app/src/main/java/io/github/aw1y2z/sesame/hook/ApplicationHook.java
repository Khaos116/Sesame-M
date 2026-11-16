package io.github.aw1y2z.sesame.hook;

import static io.github.aw1y2z.sesame.hook.SimplePageManager.addHandler;
import static io.github.aw1y2z.sesame.hook.SimplePageManager.enableWindowMonitoring;
import io.github.aw1y2z.sesame.hook.CaptchaHook;

import io.github.aw1y2z.sesame.data.Agreement;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_LoadPackage;

import java.lang.ref.WeakReference;
import java.util.Objects;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.aw1y2z.sesame.util.compat.XC_MethodReplacement;
import io.github.aw1y2z.sesame.BuildConfig;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.Model;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.RunType;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.data.ViewAppInfo;
import io.github.aw1y2z.sesame.data.task.BaseTask;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.entity.AlipayVersion;
import io.github.aw1y2z.sesame.entity.FriendWatch;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.extensions.TestRpc;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.model.task.antMember.AntMemberRpcCall;
import io.github.aw1y2z.sesame.rpc.bridge.NewRpcBridge;
import io.github.aw1y2z.sesame.rpc.bridge.OldRpcBridge;
import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.rpc.bridge.RpcBridge;
import io.github.aw1y2z.sesame.rpc.bridge.RpcVersion;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit;
import io.github.aw1y2z.sesame.util.ClassUtil;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.PermissionUtil;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import lombok.Getter;

import androidx.annotation.NonNull;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ApplicationHook extends XposedModule {

    private static final String TAG = ApplicationHook.class.getSimpleName();

    /** 好友个人主页所属的小程序 appId（抓包取证）。 */
    private static final String PROFILE_APP_ID = "20002167";
    /** 承载小程序页的容器 Activity 全类名。 */
    private static final String PROFILE_CONTAINER_CLASS = "com.alipay.android.phone.xriver.bundlex.CSGAPushActivity";
    /** 模块 App 点击好友行时写入的待打开标记，与 Kotlin 侧 PENDING_PROFILE_FILE 保持一致。 */
    private static final String PENDING_PROFILE_FILE = "pending_profile.txt";
    /** 待打开标记的有效期：超过这个时间视为用户早已放弃，冷启动后再跳转会很突兀。 */
    private static final long PENDING_PROFILE_TTL = 20_000L;
    private static final Object profileProbeLock = new Object();
    private static Runnable pendingProfileProbe;
    private static boolean profileProbeRunning;
    private static boolean pendingProfileExplicit;
    private static long pendingProfileGeneration;
    private static String pendingProfileAccountUid;
    private static final java.util.concurrent.ExecutorService profileProbeWorker =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "Sesame-ProfileProbe");
                thread.setDaemon(true);
                return thread;
            });
    /** 上次打开的好友主页，用于屏蔽模块 App 补发的重复广播。 */
    private static String lastProfileUserId;
    private static long lastProfileTime;
    /** 上一次打开的好友页容器（CSGAPushActivity）；再打开新好友时先关掉它，返回才会回到主界面而不是上一个好友。 */
    private static WeakReference<Activity> lastProfileActivity = new WeakReference<>(null);
    /** 好友跳转发起时间：紧随其后创建的小程序容器页即视为好友页。 */
    private static long profileOpenExpectAt;
    /** 跳转发起后多久内创建的容器页仍算好友页，需覆盖冷启动耗时。 */
    private static final long PROFILE_CONTAINER_EXPECT_WINDOW = 10_000L;

    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;

    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();

    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();

    @Getter
    private static volatile ClassLoader classLoader = null;

    @Getter
    private static volatile Object microApplicationContextObject = null;

    // 新增：全局静态变量，存储当前进程名
    public static volatile String processName; // 供其他方法（如 startIfNeeded）调用

    /** 模块 App 自己的包名：须与 app/build.gradle 的 applicationId 一致，用于校验广播发送方 */
    private static final String MODULE_PACKAGE_NAME = "io.github.aw1y2z.sesame";
    /** 宿主包名：配对回包与状态回包必须定向到它，否则第三方也能收到 */
    private static final String HOST_PACKAGE_NAME = "com.eg.android.AlipayGphone";
    /** adb shell 的 uid：`adb shell am broadcast` 以它发送，放行以便命令行调试 */
    private static final int SHELL_UID = 2000;

    /** App → 宿主：请求配对。无校验（回包定向且幂等），第三方触发也拿不到令牌 */
    public static final String ACTION_PAIR_REQUEST = HOST_PACKAGE_NAME + ".sesame.pair.request";
    /** 宿主 → App：配对回包，带令牌与 challenge，必须 setPackage 定向 */
    public static final String ACTION_PAIR_RESULT = MODULE_PACKAGE_NAME + ".pair.result";
    /** 系统代发：定时唤醒。靠 alarm_nonce 校验，不依赖发送方 uid */
    public static final String ACTION_ALARM_EXECUTE = HOST_PACKAGE_NAME + ".sesame.alarm.execute";
    /** App → 宿主：带令牌的受控通道（restart/reloadConfig/execute/status/openProfile） */
    public static final String ACTION_CONTROL = HOST_PACKAGE_NAME + ".sesame.control";
    /** 宿主 → App：control 的 status 回包，必须 setPackage 定向 */
    public static final String ACTION_STATUS_REPLY = MODULE_PACKAGE_NAME + ".status";

    /**
     * control 通道接受的 op。{@code rpctest} 绝不入内：那是「任意 method + data 打进 RPC 层」，
     * 只在调试场景走 HTTP；森林查询、走路路径改走 {@code forestQuery}/{@code walkPath}，
     * method 由宿主侧按枚举校验，等价于固定动作。
     */
    private static final java.util.Set<String> CONTROL_OPS = new java.util.HashSet<>(java.util.Arrays.asList(
            "restart", "reloadConfig", "execute", "status", "openProfile", "forestQuery", "walkPath"));
    /** 森林查询允许的 method，与扩展页按钮一一对应 */
    private static final java.util.Set<String> FOREST_QUERY_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "getWateredItems", "getWateringItems", "getTreeItems", "getNewTreeItems",
            "queryAreaTrees", "getUnlockTreeItems", "fillWateredFriendList"));
    /** 自定义走路路径允许的 method */
    private static final java.util.Set<String> WALK_PATH_METHODS = new java.util.HashSet<>(java.util.Arrays.asList(
            "addCustomWalkPathId", "addCustomWalkPathIdQueue", "clearCustomWalkPathIdQueue"));
    /**
     * control 通道频控：按 op 分维度计时。共用一张表时探活（3s 一次）会永远撞窗口，
     * 并让紧接着的 restart/execute 被判为「过于频繁」。
     */
    private static final long CONTROL_MIN_INTERVAL_STATUS = 2_000L;
    private static final long CONTROL_MIN_INTERVAL_RESTART = 10_000L;
    private static final long CONTROL_MIN_INTERVAL_EXECUTE = 60_000L;
    /** 跳转兜底是 App 侧 1.5s 后补发，窗口必须比它短，否则连点不同好友时兜底会被吞掉 */
    private static final long CONTROL_MIN_INTERVAL_OPEN_PROFILE = 1_000L;
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> lastControlAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** 配对请求的频控：第三方刷这条 action 只会让我们多发定向广播 */
    private static final long PAIR_MIN_INTERVAL = 60_000L;
    private static final java.util.concurrent.atomic.AtomicLong lastPairAt =
            new java.util.concurrent.atomic.AtomicLong(0);
    /** 本进程使用的 HTTP 鉴权令牌：配对口令与闹钟 nonce 都由它派生 */
    private static volatile String httpToken;
    /** 当前 hook 实例：initHandler 一族是实例方法，受控通道需要从静态入口回调它们 */
    private static volatile ApplicationHook self;

    @Getter
    private static volatile Context context = null; // 全局上下文，对应 Kotlin 的 appContext
    @SuppressLint("StaticFieldLeak")
    private static volatile Service service; // 目标 Service 实例，也是 Context 子类

    @Getter
    private static volatile AlipayVersion alipayVersion = new AlipayVersion("");

    @Getter
    private static volatile boolean hooked = false;

    private static volatile boolean init = false;

    /** 标记一次重载是否正在进行，避免重载期间被主线程反复丢后台线程造成重复初始化 */
    private static volatile boolean initializing = false;
    private static volatile boolean checkingLauncherAccount = false;

    private static volatile Calendar dayCalendar;

    @Getter
    private static volatile boolean offline = false;

    @Getter
    private static final AtomicInteger reLoginCount = new AtomicInteger(0);

    @Getter
    private static Handler mainHandler;
    private static final java.util.concurrent.atomic.AtomicReference<Runnable> pendingAccountReload =
            new java.util.concurrent.atomic.AtomicReference<>();

    private static BaseTask mainTask;

    private static RpcBridge rpcBridge;

    @Getter
    private static RpcVersion rpcVersion;

    private static PowerManager.WakeLock wakeLock;

    private static PendingIntent alarm0Pi;

    private static XC_MethodHook.Unhook rpcRequestUnhook;

    private static XC_MethodHook.Unhook rpcResponseUnhook;

    private static BroadcastReceiver broadcastReceiver = null;

    private static volatile boolean broadcastReceiverRegistered = false;
    private static android.os.HandlerThread controlThread;
    private static Handler controlHandler;

    /** 主进程"模块初始化开始"标记是否已打印（供日志查看器按轮分组，每个主进程加载周期只打一次） */
    private static volatile boolean initStartLogged = false;

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        self = this;
        XHelpers.init(this);
        log(4, TAG, "event=module_loaded api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " version=" + getFrameworkVersion());
        try {
            // 读取与 App 共享的日志开关配置，使各分项开关在本进程真正生效
            AppConfig.load();
            if (!Agreement.isAccepted()) return;
            // 模块已在 LSPosed 中启用：onModuleLoaded 被调用即代表已启用，
            // 直接标记为已激活（与是否打开 / hook 支付宝无关）
            ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode());
            // 若 UI 已启动，发同进程广播实时刷新界面
            Application app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app != null) {
                sendStatusBroadcast(app);
            }
        } catch (Throwable t) {
            // 模块激活相关步骤（配置加载/激活标记/激活广播）失败必须可见，否则「模块没生效」毫无线索
            Log.printStackTrace(TAG + " onModuleLoaded", t);
        }
    }


    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam();
        lpparam.packageName = param.getPackageName();
        // libxposed 102 的 PackageReadyParam 不直接暴露真实进程名，之前用包名冒充进程名会破坏
        // handleLoadPackage 里"主进程跑业务、子进程只装抓包"的分流判断——同一目标包的 :xxx 子进程
        // 触发这个回调时，判断收到的也是主包名，无法区分。改用真实进程名，取不到时才退回包名兜底。
        lpparam.processName = getRealProcessName(param.getPackageName());
        lpparam.classLoader = param.getClassLoader();
        handleLoadPackage(lpparam);
    }

    /**
     * 取当前进程的真实进程名。优先 ActivityThread.currentProcessName()（API 28+ 标准做法），
     * 取不到时退回读 /proc/self/cmdline（覆盖 minSdk 26-27），两条路都失败才用包名兜底。
     */
    private static String getRealProcessName(String fallbackPackageName) {
        try {
            Object name = Class.forName("android.app.ActivityThread")
                    .getMethod("currentProcessName")
                    .invoke(null);
            if (name instanceof String && !((String) name).isEmpty()) {
                return (String) name;
            }
        } catch (Throwable ignored) {}
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/self/cmdline"))) {
            String cmdline = reader.readLine();
            if (cmdline != null) {
                int nul = cmdline.indexOf('\0');
                String trimmed = (nul >= 0 ? cmdline.substring(0, nul) : cmdline).trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        } catch (Throwable ignored) {}
        return fallbackPackageName;
    }

    private static synchronized void logInitializationStart() {
        if (initStartLogged) return;
        initStartLogged = true;
        Log.i("模块初始化开始");
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 先提取进程名并赋值给全局变量
        processName = lpparam.processName; // 新增：将 Xposed 提供的进程名赋值给全局变量
        if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName) && ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
            // 提前在锁内落标志：onPackageReady 与旧回调 handleLoadPackage 可能并发/重复触发，
            // 旧写法在方法末尾才置位，竞态下会重复执行整套 hook 与初始化
            synchronized (ApplicationHook.class) {
                if (hooked) {
                    return;
                }
                hooked = true;
            }
            try {
                classLoader = lpparam.classLoader;
                if (!FileUtil.acquireHostLock()) throw new IllegalStateException("Account directory is busy");

                XHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        context = (Context) param.args[0];
                        alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                        try {
                            AlipayMiniMarkHelper.init(classLoader);
                            AuthCodeHelper.init(classLoader);
                            // 小程序抓包（游戏等小程序数据）：与日志页「抓包记录」共用开关
                            MiniProgramHook.install(classLoader, processName);
                            // 启动时不再调用 getAuthCode：返回值本就被丢弃，而它在当前支付宝版本上必然失败
                            //（自建实例未走宿主依赖注入，内部 facade 为 null），只会在日志里留下噪音
                        } catch (Exception e) {
                            Log.printStackTrace(e);
                        }
                        super.afterHookedMethod(param);
                    }
                });
            } catch (Throwable t) {
                // 首个 hook 尚未装成才释放准入；后续可选 hook 失败不重装已成功的回调。
                synchronized (ApplicationHook.class) {
                    hooked = false;
                }
                Log.err(TAG, "hook Application.attach 初始化失败，下次回调重试", t);
                return;
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion", classLoader.loadClass(ClassUtil.H5PAGE_NAME), Map.class, String.class, XC_MethodReplacement.returnConstant(false));
            } catch (Throwable t) {
                Log.err(TAG, "hook matchVersion err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        onLauncherResume((Activity) param.thisObject);
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook login err:", t);
            }
            // 只有兜底走 alipays:// 时才会创建这个中转页，设透明主题减轻闪烁。
            // 不要靠改窗口尺寸/alpha 隐藏它：自由窗口下边界由系统按任务计算，压不住；
            // 残留的 alpha=0 窗口盖在主界面上就是黑屏。
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.quinox.SchemeLauncherActivity", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof Activity) {
                                ((Activity) param.thisObject).setTheme(android.R.style.Theme_Translucent_NoTitleBar);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook SchemeLauncherActivity err:", t);
            }
            // 连续打开不同好友时，小程序容器会在同一 task 里叠出多个实例，
            // 返回就会退回上一个好友页。容器本身没有重写 onCreate，这里挂 Activity.onCreate 再按类名筛。
            try {
                XHelpers.findAndHookMethod("android.app.Activity", classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!(param.thisObject instanceof Activity)) {
                                return;
                            }
                            Activity activity = (Activity) param.thisObject;
                            if (!PROFILE_CONTAINER_CLASS.equals(activity.getClass().getName())) {
                                return;
                            }
                            handleProfileContainer(activity);
                        } catch (Throwable th) {
                            Log.err(TAG, "handleProfileContainer err:", th);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook CSGAPushActivity err:", t);
            }
            // 模块 App 点击好友时会把目标好友落盘并立刻拉起支付宝。
            // 任意页面 resume 时在后台消费标记，再投递主线程跳转；冷暖启动均保留广播兜底。
            try {
                XHelpers.findAndHookMethod("android.app.Activity", classLoader, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (param.thisObject instanceof Activity) {
                                checkPendingProfile((Activity) param.thisObject);
                            }
                        } catch (Throwable th) {
                            Log.err(TAG, "checkPendingProfile in onResume err:", th);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook Activity.onResume err:", t);
            }
            // 支付宝以小窗/分屏形态在前台时，被模块 App 拉起只是抢到窗口焦点，不会重新走 onResume，
            // 标记就没人消费，最后落到兜底广播——那条路要在后台任务里启动页面，系统必补一个中转页。
            // 焦点到手就消费，此时支付宝已经在前台，跳转就是普通的前台启动。
            try {
                XHelpers.findAndHookMethod("android.app.Activity", classLoader, "onWindowFocusChanged", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (!(param.thisObject instanceof Activity) || param.args.length == 0) {
                                return;
                            }
                            if (!Boolean.TRUE.equals(param.args[0])) {
                                return;
                            }
                            checkPendingProfile((Activity) param.thisObject);
                        } catch (Throwable th) {
                            Log.err(TAG, "checkPendingProfile in onWindowFocusChanged err:", th);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook Activity.onWindowFocusChanged err:", t);
            }
            try {
                XHelpers.findAndHookMethod("android.app.Service", classLoader, "onCreate", new XC_MethodHook() {

                    @SuppressLint({"WakelockTimeout", "UnsafeDynamicallyLoadedCode"})
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 1. 获取目标 Service 实例（appService 是 Service 子类，也是 Context 类型）
                        Service appService = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(appService.getClass().getCanonicalName())) {
                            return;// 非目标 Service，直接返回，保障只处理支付宝前台服务
                        }

                        // 2. 兜底赋值全局 context（对应 Kotlin appContext 的二次赋值）
                        context = appService.getApplicationContext(); // 获取应用全局上下文，更新全局变量
                        service = appService; // 存储 Service 实例，供后续复用

                        // 3. 调用 registerBroadcastReceiver，传入参数 Context（appService）
                        // 这里的 appService 就是对应 Kotlin registerBroadcastReceiver(appContext!!) 的参数
                        registerBroadcastReceiver(appService);

                        // 主动通知 App 本模块已被 LSPosed 启用并注入支付宝，用于显示「已激活」
                        // 广播失败会导致 UI 迟迟显示「未激活」，sendStatusBroadcast 内部已记日志
                        sendStatusBroadcast(appService);

                        context = appService.getApplicationContext();
                        // 庄园饲料任务已全部走 AntFarmRpcCall 的 Java RPC 实现（见 AntFarm.java），
                        // 不再需要加载 libsesame.so；已移除强制加载，jniLibs/util/LibraryUtil 一并删除，见 docs/MyFix.md
                        // 初始化滑块验证（支付宝版本 > 10.6.58.99999 时 initSimplePageManager 内部会跳过）
                        try {
                            initSimplePageManager();
                        } catch (Exception e) {
                            Log.printStackTrace(e);
                        }
                        service = appService;
                        mainHandler = new Handler(Looper.getMainLooper());
                        mainTask = BaseTask.newInstance("MAIN_TASK", new Runnable() {

                            private volatile long lastExecTime = 0;

                            @Override
                            public void run() {
                                int checkInterval = 0;
                                try {
                                    checkInterval = BaseModel.getCheckInterval().getValue();
                                    if (!init) {
                                        return;
                                    }
                                    if (AccountSwitchController.isBusy()) {
                                        execDelayedHandler(checkInterval);
                                        return;
                                    }
                                    Log.record("应用版本：" + alipayVersion.getVersionString());
                                    Log.record("模块版本：" + modelVersion + "（交流更新QQ群：694474777）");
                                    Log.record("编译时间：" + BuildConfig.BUILD_TIME);
                                    String captchaSummary = CaptchaTriggerStats.summary();
                                    if (!captchaSummary.isEmpty()) Log.record(captchaSummary);
                                    Log.record("开始执行" + MyUtils.recordUserName(getUserId()));
                                    if (lastExecTime + 2000 > System.currentTimeMillis()) {
                                        Log.record("执行间隔较短，跳过执行");
                                        execDelayedHandler(checkInterval);
                                        return;
                                    }
                                    updateDay();
                                    String targetUid = getUserId();
                                    String currentUid = UserIdMap.getCurrentUid();
                                    if (targetUid == null || currentUid == null) {
                                        Log.record("用户为空，放弃执行");
                                        reLogin();
                                        return;
                                    }
                                    if (!targetUid.equals(currentUid)) {
                                        Log.record("开始切换用户");
                                        Toast.show("开始切换用户");
                                        reLogin();
                                        return;
                                    }
                                    lastExecTime = System.currentTimeMillis();
                                    FutureTask<Boolean> checkTask = new FutureTask<>(AntMemberRpcCall::check);
                                    try {
                                        TaskLifecycle.Work checkWork = TaskLifecycle.enter();
                                        if (checkWork == null) return;
                                        Thread checkThread = new Thread(() -> {
                                            try { checkTask.run(); }
                                            finally { checkWork.close(); }
                                        });
                                        try { checkThread.start(); }
                                        catch (Throwable failed) { checkWork.close(); throw failed; }
                                        if (!checkTask.get(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                                            Log.record("执行暂停：检查未通过，等待下次执行");
                                            execDelayedHandler(checkInterval);
                                            return;
                                        }
                                        reLoginCount.set(0);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        Log.record("执行取消：检查中断");
                                        return;
                                    } catch (TimeoutException e) {
                                        Log.record("执行暂停：检查超时，等待下次执行");
                                        execDelayedHandler(checkInterval);
                                        return;
                                    } catch (Exception e) {
                                        Log.record("执行暂停：检查异常，等待下次执行");
                                        execDelayedHandler(checkInterval);
                                        Log.printStackTrace(TAG, e);
                                        return;
                                    } finally {
                                        checkTask.cancel(true);
                                    }
                                    TaskCommon.update();
                                    ModelTask.startAllTask(false);
                                    lastExecTime = System.currentTimeMillis();

                                    try {
                                        List<String> execAtTimeList = BaseModel.getExecAtTimeList().getValue();
                                        if (execAtTimeList != null) {
                                            Calendar lastExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime);
                                            Calendar nextExecTimeCalendar = TimeUtil.getCalendarByTimeMillis(lastExecTime + checkInterval);
                                            for (String execAtTime : execAtTimeList) {
                                                Calendar execAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(execAtTime);
                                                if (execAtTimeCalendar != null && lastExecTimeCalendar.compareTo(execAtTimeCalendar) < 0 && nextExecTimeCalendar.compareTo(execAtTimeCalendar) > 0) {
                                                    Log.record("设置定时执行:" + execAtTime);
                                                    execDelayedHandler(execAtTimeCalendar.getTimeInMillis() - lastExecTime);
                                                    FileUtil.clearLog();
                                                    return;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        Log.err(TAG, "execAtTime err:", e);
                                    }

                                    // 只收能量窗口内要高频重收，不能按执行间隔整段跳过窗口
                                    execDelayedHandler(getNextInterval(checkInterval));
                                    FileUtil.clearLog();
                                } catch (Exception e) {
                                    Log.record("执行异常:");
                                    Log.printStackTrace(e);
                                } finally {
                                    // 单链之下"本轮没人排期"就等于永久停摆：兜住 !init、各处 return 与未捕获异常
                                    if (!tickScheduled) {
                                        execDelayedHandler(checkInterval > 0 ? checkInterval : FALLBACK_INTERVAL);
                                    }
                                }
                            }
                        });
                        dayCalendar = MyUtils.getInstance();
                        AccountSwitchController.configure(new AccountSwitchController.Host() {
                            @Override
                            public String readiness() {
                                BaseModel base = Model.getModel(BaseModel.class);
                                if (service == null || base == null) return "WAIT_HOST";
                                if (!ConfigV2.INSTANCE.isInit()) return "WAIT_HOST";
                                if (!base.isEnable()) return "READY";
                                if (!init) return "WAIT_HOST";
                                return offline ? "WAIT_HOST" : "READY";
                            }

                            @Override
                            public String currentUid() { return getUserId(); }

                            @Override
                            public boolean initialize(String expectedUid, TaskLifecycle.Freeze owner) {
                                if (!Objects.equals(expectedUid, getUserId())) return false;
                                // 这条路径始终在后台线程调用，理论上不会拿到 null，但拆箱到 boolean 前仍显式判空更安全
                                init = Boolean.TRUE.equals(initHandler(true, owner));
                                BaseModel base = Model.getModel(BaseModel.class);
                                return base != null && (init || !base.isEnable())
                                        && Objects.equals(expectedUid, UserIdMap.getCurrentUid());
                            }
                            @Override
                            public void resume() {
                                if (init) BaseModel.initData();
                                requestEarlyTaskRun();
                            }
                        });
                        AccountSwitchController.hostReady();
                        // 主线程调用，重载已转后台线程执行：返回值是 Boolean（可能为 null 代表"已转后台，结果稍后自报"），
                        // 不能拆箱判断；成功后 initializeHandler 会自己置 init=true，此处无需依赖返回值
                        initHandler(true);
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook service onCreate err:", t);
            }
            try {
                XHelpers.findAndHookMethod("android.app.Service", classLoader, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Service service = (Service) param.thisObject;
                        if (!ClassUtil.CURRENT_USING_SERVICE.equals(service.getClass().getCanonicalName())) {
                            return;
                        }
                        Log.record("支付宝前台服务被销毁");
                        NotificationUtil.updateStatusText("支付宝前台服务被销毁");
                        destroyHandler(true);
                        FriendWatch.unload();
                        Statistics.unload();
                        // 前台服务已销毁，紧接着要跑一轮，否则白等一个查询间隔
                        restartSelf(true);
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook service onDestroy err:", t);
            }
            // ---- 宿主的前后台询问：默认谎报"在前台"，唯独风控/滑块链路在真实后台时如实回答 ----
            // 一律谎报能让后台 H5/RPC 跑起来，但滑块会被判定为可展示、后台又拿不到窗口，
            // 验证流程死等用户滑动，切回支付宝即卡死。所以先 callOriginal 取真值，
            // 只有"真在后台 + 询问方是风控/滑块"才说实话。
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 1 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackground", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 2 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.fgbg.FgBgMonitorImpl", classLoader, "isInBackgroundV2", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(answerInBackgroundQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook FgBgMonitorImpl method 3 err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.common.transport.utils.MiscUtils", classLoader, "isAtFrontDesk", classLoader.loadClass("android.content.Context"), new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        param.setResult(answerAtFrontDeskQuestion(param));
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook MiscUtils err:", t);
            }
        }
    }

    private static void setWakenAtTimeAlarm() {
        try {
            // 闹钟口令由令牌派生，令牌没解析出来时排出去的 PendingIntent 一定通不过校验，
            // 到点才发现等于整轮唤醒白等：这里先补一次解析，仍取不到就干脆不排。
            if (alarmNonce() == null) {
                resolveHttpToken();
            }
            if (alarmNonce() == null) {
                Log.record("定时唤醒未设置#令牌不可用，闹钟口令无法派生");
                return;
            }
            unsetWakenAtTimeAlarm();
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, alarmIntent(), getPendingIntentFlag());
                // 按北京时间算"明天 00:00"，否则宿主设备非东八区时这个固定唤醒会偏移到错误的本地时刻
                Calendar calendar = MyUtils.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                }
            } catch (Exception e) {
                Log.err(TAG, "setWakenAt0 err:", e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                // 之前这里特意没跟 TimeUtil.getTodayCalendarByTimeStr 用同一时区（那时 TimeUtil 还是系统时区），
                // 现在 TimeUtil 已经整体改成 GMT+8（见 docs/MyFix.md），这里改用 TimeUtil.getNow() 保持一致，
                // 不会再产生比较错位
                Calendar nowCalendar = TimeUtil.getNow();
                for (int i = 0, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        int seq = i + 1;
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, seq, alarmIntent(), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = seq + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.err(TAG, "setWakenAtTime err:", e);
                    }
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "setWakenAtTimeAlarm err:", e);
        }
    }

    private static void unsetWakenAtTimeAlarm() {
        try {
            for (Map.Entry<String, PendingIntent> entry : wakenAtTimeAlarmMap.entrySet()) {
                try {
                    String wakenAtTimeKey = entry.getKey();
                    PendingIntent wakenAtTimePendingIntent = entry.getValue();
                    if (unsetAlarmTask(wakenAtTimePendingIntent)) {
                        wakenAtTimeAlarmMap.remove(wakenAtTimeKey);
                    }
                } catch (Exception e) {
                    Log.err(TAG, "unsetWakenAtTime err:", e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                }
            } catch (Exception e) {
                Log.err(TAG, "unsetWakenAt0 err:", e);
            }
        } catch (Exception e) {
            Log.err(TAG, "unsetWakenAtTimeAlarm err:", e);
        }
    }

    private Boolean initHandler(Boolean force) {
        return initHandler(force, null, true);
    }

    /**
     * 切换账号 / 首启的重载入口。
     * 重载（force=true）包含大量文件 IO、整份配置 JSON 反序列化、反射建 Model、逐 Model 装 Hook，
     * 这些若在「主线程」同步执行会把支付宝界面卡住（表现为"切号卡死不动"）。
     * 配置、宿主账号与权限查询也可能等待磁盘或宿主锁，必须连同前置检查一起转后台；
     * 后台线程里仍然走 TaskLifecycle 守卫，避免和切号并发。
     */
    private Boolean initHandler(Boolean force, TaskLifecycle.Freeze owner) {
        return initHandler(force, owner, true);
    }

    /** 配置保存只重载并恢复排期；首启、切号及手动执行仍立即运行。 */
    private Boolean initHandler(Boolean force, TaskLifecycle.Freeze owner, boolean exec) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (initializing) return false;
            initializing = true;
            long generation = TaskLifecycle.generation();
            try {
                new Thread(() -> {
                    try (TaskLifecycle.Work work = owner == null
                            ? TaskLifecycle.enter(generation) : TaskLifecycle.enterInitialization(owner)) {
                        if (work != null) initHandler(force, owner, exec);
                    } catch (Throwable t) {
                        Log.err(TAG, "Sesame-InitHandler err:", t);
                    } finally {
                        initializing = false;
                    }
                }, "Sesame-InitHandler").start();
                return null;
            } catch (Throwable t) {
                initializing = false;
                Log.err(TAG, "Sesame-InitHandler start err:", t);
                return false;
            }
        }
        if (!Agreement.isAccepted()) {
            AppConfig.load();
            if (!Agreement.isAccepted()) {
                Log.record("任务未启动#请先在芝麻粒-M同意使用协议");
                return false;
            }
        }
        if (service == null) {
            return false;
        }
        // 后台前置检查；需要交互的权限提示仍投递给主线程。
        if (force) {
            String userId = getUserId();
            if (userId == null) {
                Log.record("用户未登录");
                Toast.show("用户未登录");
                return false;
            }
            if (!PermissionUtil.checkAlarmPermissions()) {
                Log.record("支付宝无闹钟权限");
                mainHandler.postDelayed(() -> {
                    if (!PermissionUtil.checkOrRequestAlarmPermissions(context)) {
                        android.widget.Toast.makeText(context, "请授予支付宝使用闹钟权限", android.widget.Toast.LENGTH_SHORT).show();
                    }
                }, 2000);
                return false;
            }
        }
        return runInit(force, owner, exec);
    }

    /** 令牌是 32 字节随机数的十六进制串；只有符合这个形状的旧文件内容才继承 */
    private static final java.util.regex.Pattern HTTP_TOKEN_PATTERN =
            java.util.regex.Pattern.compile("[0-9a-f]{64}");

    /** 支付宝 userId 是纯数字；它会拼进 alipays:// 查询串，必须挡住其它字符 */
    private static final java.util.regex.Pattern USER_ID_PATTERN =
            java.util.regex.Pattern.compile("[0-9]{1,32}");

    /**
     * 本地 HTTP 鉴权令牌：首次生成后落在宿主私有目录并复用。
     * <p>读写失败返回空串：空令牌在 {@link BaseHandler#verifyToken} 里是拒绝，宁可服务不可用。
     */
    private static boolean isHttpToken(String token) {
        return token != null && HTTP_TOKEN_PATTERN.matcher(token).matches();
    }

    private static String randomHttpToken() {
        byte[] raw = new byte[32];
        new java.security.SecureRandom().nextBytes(raw);
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static synchronized String resolveHttpToken() {
        // 缓存命中直接返回：激活广播要在 Service.onCreate 里发，那时 runInit 还没跑，
        // 不在这里先解析一次，回包就会缺令牌而被 App 当成伪造消息丢掉
        String cached = httpToken;
        if (cached != null) {
            return cached;
        }
        try {
            FileUtil.initPrivateDirectory(context);
            java.io.File tokenFile = FileUtil.getHttpTokenFile();
            String token = FileUtil.readFromFile(tokenFile).trim();
            if (!isHttpToken(token)) {
                // 升级后首次启动：把共享目录里的旧令牌搬进私有目录。值保持不变，
                // PC 侧已经保存的令牌继续有效。
                String legacy = FileUtil.readFromFile(FileUtil.getLegacyHttpTokenFile()).trim();
                token = isHttpToken(legacy) ? legacy : randomHttpToken();
                if (!FileUtil.write2File(token, tokenFile)) {
                    Log.record("HTTP 鉴权令牌写入失败，本次不启动 HTTP 服务");
                    return "";
                }
                Log.record("已生成 HTTP 鉴权令牌（存于私有目录 http_token.txt）");
            }
            syncLegacyHttpToken(token);
            // 配对通道与闹钟 nonce 都要用同一个值：缓存下来，避免每次广播都读一次私有文件
            httpToken = token;
            return token;
        } catch (Throwable t) {
            Log.err(TAG, "resolveHttpToken err:", t);
        }
        return "";
    }

    /**
     * 真正执行重载，必须在非主线程调用。UI 相关（Toast / 权限提示）已内部切回主线程，
     * 故整体跑在后台线程是安全的。
     * synchronized 保证同一时刻只有一处重载，防止切号与首启 / 广播重启并发触发重复初始化；
     * TaskLifecycle 守卫防止和账号切换窗口重叠。
     */
    private synchronized Boolean runInit(Boolean force, TaskLifecycle.Freeze owner, boolean exec) {
        try (TaskLifecycle.Work work = TaskLifecycle.enterInitialization(owner)) {
            if (work == null) {
                Log.i(TAG, "重载被 TaskLifecycle 拒绝（账号切换窗口期），本次跳过，等下次触发");
                return false;
            }
            boolean initialized = initializeHandler(force, exec);
            if (initialized && owner == null) BaseModel.initData();
            return initialized;
        }
    }

    private static void syncLegacyHttpToken(String token) {
        if (!Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableDebugLog())) {
            removeMirrorTokenFile();
            return;
        }
        java.io.File mirrorFile = FileUtil.getLegacyHttpTokenFile();
        if (!token.equals(FileUtil.readFromFile(mirrorFile).trim())) {
            if (FileUtil.write2File(token, mirrorFile)) {
                Log.record("调试模式已开启，令牌副本：" + mirrorFile);
            } else {
                Log.error("令牌副本写入失败：" + mirrorFile);
            }
        }
    }
    private static void removeMirrorTokenFile() {
        java.io.File mirrorFile = FileUtil.getLegacyHttpTokenFile();
        if (mirrorFile.exists() && !mirrorFile.delete()) {
            Log.error("共享目录令牌副本删除失败：" + mirrorFile);
        }
    }

    @SuppressLint("WakelockTimeout")
    private Boolean initializeHandler(Boolean force, boolean exec) {
        destroyHandler(force);
        init = false;
        try {
            if (force) {
                String userId = getUserId();
                if (userId == null) {
                    Log.record("用户未登录");
                    Toast.show("用户未登录");
                    return false;
                }

                //调用 startIfNeeded 方法，参数与 Kotlin 保持一致
                ModuleHttpServerManager.getInstance().startIfNeeded(8080, resolveHttpToken(), processName, "com.eg.android.AlipayGphone");

                if (!Objects.equals(userId, UserIdMap.getCurrentUid())) {
                    try {
                        Statistics.save();
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    FriendWatch.save();
                }
                UserIdMap.initUser(userId);
                if (!userId.equals(UserIdMap.getCurrentUid()) || !userId.equals(getUserId())) {
                    Log.record("初始化已停止#好友初始化期间账号变化，等待当前账号重新加载");
                    return false;
                }
                logInitializationStart();
                Statistics.load();
                FriendWatch.load();
                Model.initAllModel();
                Log.record("开始加载" + MyUtils.recordUserName(userId));
                ConfigV2.load(userId);

                boolean enableModule = Model.getModel(BaseModel.class).getEnableField().getValue();
                if (!enableModule) {
                    Log.record("芝麻粒-M已禁用");
                    Toast.show("芝麻粒-M已禁用");
                    return false;
                }
                if (AppConfig.shouldRequestBatteryPermission() && !init && !PermissionUtil.checkBatteryPermissions()) {
                    Log.record("支付宝无始终在后台运行权限");
                    mainHandler.postDelayed(() -> {
                        if (!PermissionUtil.checkOrRequestBatteryPermissions(context)) {
                            android.widget.Toast.makeText(context, "请授予支付宝终在后台运行权限", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }, 2000);
                }
                if (AppConfig.INSTANCE.getNewRpc()) {
                    rpcBridge = new NewRpcBridge();
                } else {
                    rpcBridge = new OldRpcBridge();
                }
                rpcBridge.load();
                rpcVersion = rpcBridge.getVersion();
                if (BaseModel.getStayAwake().getValue()) {
                    try {
                        PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
                        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, service.getClass().getName());
                        wakeLock.acquire();
                    } catch (Throwable t) {
                        Log.printStackTrace(t);
                    }
                }
                setWakenAtTimeAlarm();
                installRpcRecordHook();
                NotificationUtil.start(service);
                CaptchaHook.setupHook(classLoader);
                Model.bootAllModel(classLoader);
                Status.load();
                TokenConfig.load();
                updateDay();
                BaseModel.initRpcRequest();
                if (!userId.equals(UserIdMap.getCurrentUid()) || !userId.equals(getUserId())) {
                    Log.record("初始化已停止#模型加载期间账号变化，未启动任务");
                    return false;
                }
                Log.record("加载完成");
                Toast.show("芝麻粒-M加载成功:" + modelVersion);
            }
            offline = false;
            init = true;
            if (exec) {
                execHandler();
            } else {
                execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), FALLBACK_INTERVAL));
                NotificationUtil.updateIdleText();
                Log.record("配置已重载#未触发执行，下一跳按间隔排队");
            }
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "startHandler err:", th);
            Toast.show("芝麻粒-M加载失败");
            return false;
        }
    }

    /**
     * 安装抓包钩子（请求 + 返回各一个），开关为「抓包记录」。
     * <p>老接口没有 RpcBridgeExtension.rpc，故要求「使用新接口」开着；重复调用不叠加 hook。
     */
    private static synchronized void installRpcRecordHook() {
        if (!AppConfig.INSTANCE.getEnableDebugLog()) {
            return;
        }
        if (!AppConfig.INSTANCE.getNewRpc()) {
            Log.i(TAG, "抓包需要开启「使用新接口」，已跳过");
            return;
        }
        if (rpcRequestUnhook != null || rpcResponseUnhook != null) {
            return;
        }
        try {
            rpcRequestUnhook = XHelpers.findAndHookMethod("com.alibaba.ariver.commonability.network.rpc.RpcBridgeExtension", classLoader, "rpc", String.class, boolean.class, boolean.class, String.class, classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), String.class, classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), boolean.class, boolean.class, int.class, boolean.class, String.class, classLoader.loadClass("com.alibaba.ariver.app.api.App"), classLoader.loadClass("com.alibaba.ariver.app.api.Page"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.model.ApiContext"), classLoader.loadClass("com.alibaba.ariver.engine.api.bridge.extension" + ".BridgeCallback"), new XC_MethodHook() {

                @SuppressLint("WakelockTimeout")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object[] args = param.args;
                    Object object = args[15];
                    Object[] recordArray = new Object[4];
                    recordArray[0] = System.currentTimeMillis();
                    recordArray[1] = args[0];
                    recordArray[2] = args[4];
                    rpcHookMap.put(object, recordArray);
                }

                @SuppressLint("WakelockTimeout")
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.args[15];
                    Object[] recordArray = rpcHookMap.remove(object);
                    if (recordArray != null) {
                        Log.debug("记录\n时间: " + recordArray[0] + "\n方法: " + recordArray[1] + "\n参数: " + recordArray[2] + "\n数据: " + recordArray[3] + "\n");
                    } else {
                        Log.debug("删除记录ID: " + object.hashCode());
                    }
                }

            });
            Log.i(TAG, "hook record request successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook record request err:", t);
        }
        try {
            rpcResponseUnhook = XHelpers.findAndHookMethod("com.alibaba.ariver.engine.common.bridge.internal.DefaultBridgeCallback", classLoader, "sendJSONResponse", classLoader.loadClass(ClassUtil.JSON_OBJECT_NAME), new XC_MethodHook() {

                @SuppressLint("WakelockTimeout")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.thisObject;
                    Object[] recordArray = rpcHookMap.get(object);
                    if (recordArray != null) {
                        recordArray[3] = String.valueOf(param.args[0]);
                    }
                }

            });
            Log.i(TAG, "hook record response successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook record response err:", t);
        }
    }

    /** 卸载抓包钩子；未安装时是空操作 */
    private static synchronized void uninstallRpcRecordHook() {
        if (rpcResponseUnhook != null) {
            try {
                rpcResponseUnhook.unhook();
            } catch (Throwable e) {
                Log.printStackTrace(e);
            }
            rpcResponseUnhook = null;
        }
        if (rpcRequestUnhook != null) {
            try {
                rpcRequestUnhook.unhook();
            } catch (Throwable e) {
                Log.printStackTrace(e);
            }
            rpcRequestUnhook = null;
        }
        // 卸载后 map 里的残留 entry 会继续强引用 BridgeCallback / 请求体 / 响应体，
        // 而此后已无人 remove，必须在这里清空
        if (!rpcHookMap.isEmpty()) {
            rpcHookMap.clear();
        }
    }

    private synchronized static void destroyHandler(Boolean force) {
        try {
            if (force) {
                if (service != null) {
                    stopHandler();
                    BaseModel.destroyData();
                    Status.unload();
                    NotificationUtil.stop();
                    RpcIntervalLimit.clearIntervalLimit();
                    ConfigV2.unload();
                    Model.destroyAllModel();
                    UserIdMap.unload();
                }
                uninstallRpcRecordHook();
                if (wakeLock != null) {
                    wakeLock.release();
                    wakeLock = null;
                }
                if (rpcBridge != null) {
                    rpcVersion = null;
                    rpcBridge.unload();
                    rpcBridge = null;
                }
            } else {
                ModelTask.stopAllTask();
            }
        } catch (Throwable th) {
            Log.err(TAG, "stopHandler err:", th);
        }
    }

    private void onLauncherResume(Activity activity) {
        if (!init) {
            initHandler(true);
            return;
        }
        if (checkingLauncherAccount) return;
        checkingLauncherAccount = true;
        long generation = TaskLifecycle.generation();
        String currentUid = UserIdMap.getCurrentUid();
        try {
            new Thread(() -> {
                try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                    if (work == null || !Objects.equals(currentUid, UserIdMap.getCurrentUid())) return;
                    String targetUid = getUserId();
                    if (!Objects.equals(currentUid, UserIdMap.getCurrentUid())) return;
                    if (targetUid == null) {
                        Log.record("用户未登录");
                        Toast.show("用户未登录");
                        return;
                    }
                    if (!targetUid.equals(currentUid)) {
                        if (currentUid != null) {
                            scheduleAccountReload();
                            return;
                        }
                        UserIdMap.initUser(targetUid);
                    }
                    if (!targetUid.equals(getUserId()) || !targetUid.equals(UserIdMap.getCurrentUid())) return;
                    if (offline) {
                        offline = false;
                        execHandler();
                        mainHandler.post(() -> {
                            try (TaskLifecycle.Work finishWork = TaskLifecycle.enter(generation)) {
                                if (finishWork == null || !targetUid.equals(UserIdMap.getCurrentUid())) return;
                                if (!activity.isFinishing() && !activity.isDestroyed()) activity.finish();
                            }
                        });
                        Log.i(TAG, "Activity reLogin");
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "Launcher account check err:", t);
                } finally {
                    checkingLauncherAccount = false;
                }
            }, "Sesame-LauncherAccount").start();
        } catch (Throwable t) {
            checkingLauncherAccount = false;
            Log.err(TAG, "Launcher account worker start err:", t);
        }
    }

    private void scheduleAccountReload() {
        if (mainHandler == null) return;
        long generation = TaskLifecycle.generation();
        String currentUid = UserIdMap.getCurrentUid();
        Runnable reload = new Runnable() {
            @Override
            public void run() {
                if (!pendingAccountReload.compareAndSet(this, null)) return;
                new Thread(() -> {
                    try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                        if (work == null || !Objects.equals(currentUid, UserIdMap.getCurrentUid())) return;
                        String targetUid = getUserId();
                        if (targetUid == null || targetUid.equals(currentUid)
                                || !Objects.equals(currentUid, UserIdMap.getCurrentUid())) return;
                        Boolean triggered = initHandler(true);
                        if (!Boolean.FALSE.equals(triggered)) {
                            Log.record("用户已切换");
                            Toast.show("用户已切换");
                        }
                    } catch (Throwable t) {
                        Log.err(TAG, "Account reload err:", t);
                    }
                }, "Sesame-AccountReload").start();
            }
        };
        Runnable previous = pendingAccountReload.getAndSet(reload);
        if (previous != null) mainHandler.removeCallbacks(previous);
        mainHandler.postDelayed(reload, 1000);
    }

    private static void execHandler() {
        if (!init || mainTask == null || AccountSwitchController.isBusy()) return;
        try {
            NotificationUtil.setRunning();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        startMainTask();
    }

    /** 起跳失败或取不到间隔时的兜底排期间隔（下限 1 分钟，避免自旋） */
    private static final long FALLBACK_INTERVAL = 60_000;

    /** M保留30秒检查窗口；失败沿用重排期，不因慢响应反复重登录。 */
    private static final long CHECK_TIMEOUT_MS = 30_000;

    /** 起跳的公共实现：线程起不来等异常不能逃到宿主主线程（执行槽已由 BaseTask 归还） */
    private static void startMainTask() {
        try {
            if (mainTask == null || !init || !TaskLifecycle.isOpen() || AccountSwitchController.isBusy()) return;
            if (Boolean.TRUE.equals(mainTask.startTask(false)) || tickScheduled) {
                return;
            }
            // 没跑起来（check 不通过等）又无人排期：兜底续排，否则链断
            execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), FALLBACK_INTERVAL));
        } catch (Throwable t) {
            Log.printStackTrace(t);
        }
    }

    /** 是否已有待发的下一跳：单链之下既用它兜底续排，又不覆盖本轮更早的显式排期（定时执行、reLogin 快重试） */
    private static volatile boolean tickScheduled = false;

    /** 待发 tick 的预定触发时刻，仅用于"排期被覆盖"的日志归因 */
    private static volatile long scheduledExecAt = 0;
    private static volatile long scheduledGeneration = 0;

    /** 延迟触发的一跳：提成常量才能在下一次排期前 removeCallbacks 掉旧链（重载/切号会重建 mainTask） */
    private static final Runnable MAIN_TICK = () -> {
        tickScheduled = false;
        scheduledExecAt = 0;
        try (TaskLifecycle.Work work = TaskLifecycle.enter(scheduledGeneration)) {
            if (work == null || !init) return;
            if (AccountSwitchController.isBusy()) {
                execDelayedHandler(FALLBACK_INTERVAL);
                return;
            }
            try {
                NotificationUtil.setRunning();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
            startMainTask();
        }
    };

    /** 只收能量窗口内的重收间隔：好友能量 7:00 起陆续成熟，5 分钟一轮足够兜住 */
    private static final long ENERGY_WINDOW_INTERVAL = 5 * 60 * 1000L;

    /**
     * 计算下一轮的排期延迟。平日按执行间隔；「只收能量时间」窗口内压到 5 分钟一轮，
     * 窗口即将开始、下一轮会整段跳过窗口时，提前排到窗口开始后 1 分钟。
     * <p>默认 06:55:30 那轮跑完后要按执行间隔（默认 50 分钟）等到 7:45，
     * 7:00~7:31 陆续成熟的好友能量中间没有任何一次收取机会，
     * 表现就是「早上那波完全不收能量」。
     */
    private static long getNextInterval(long checkInterval) {
        List<String> ranges = BaseModel.getEnergyTime().getValue();
        if (ranges == null || ranges.isEmpty() || checkInterval <= 0) {
            return checkInterval;
        }
        long now = System.currentTimeMillis();
        for (String range : ranges) {
            if (range == null || !range.contains("-")) {
                continue;
            }
            String[] parts = range.trim().split("-");
            if (parts.length != 2) {
                continue;
            }
            Calendar start = TimeUtil.getTodayCalendarByTimeStr(parts[0].trim());
            Calendar end = TimeUtil.getTodayCalendarByTimeStr(parts[1].trim());
            if (start == null || end == null) {
                continue;
            }
            long startMillis = start.getTimeInMillis();
            long endMillis = end.getTimeInMillis();
            if (endMillis <= startMillis) {
                // 跨天窗口（如 2359-0030）：结束时间落在次日
                endMillis += TimeUnit.DAYS.toMillis(1);
            }
            if (now >= startMillis && now < endMillis) {
                return Math.min(checkInterval, ENERGY_WINDOW_INTERVAL);
            }
            if (now < startMillis && startMillis - now < checkInterval) {
                return startMillis - now + 60_000L;
            }
        }
        return checkInterval;
    }

    private static void execDelayedHandler(long delayMillis) {
        if (mainHandler == null || !TaskLifecycle.isOpen()) return;
        scheduledGeneration = TaskLifecycle.generation();
        // 调度时立即记录下次执行时间，所有任务完成时 updateLastExecText 会一并写入
        try {
            NotificationUtil.setNextExecTime(System.currentTimeMillis() + delayMillis);
            NotificationUtil.flushNextExecText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        long newExecAt = System.currentTimeMillis() + delayMillis;
        if (tickScheduled) {
            // 后写覆盖前写（如 reLogin 覆盖"定时执行"）会让原排期静默失效，记一行便于归因
            Log.record("排期被覆盖：原定 " + TimeUtil.getTimeStr(scheduledExecAt) + " → 改为 " + TimeUtil.getTimeStr(newExecAt));
        }
        // 先摘掉上一条排期，否则每次重载/切号都会多留一条幽灵链，每个 interval 触发两次
        mainHandler.removeCallbacks(MAIN_TICK);
        mainHandler.postDelayed(MAIN_TICK, delayMillis);
        scheduledExecAt = newExecAt;
        tickScheduled = true;
    }

    /** Coalesces an early normal dispatch; never interrupts a running dispatcher. */
    public static void requestEarlyTaskRun() {
        Handler handler = mainHandler;
        if (handler == null || !init || offline || !TaskLifecycle.isOpen()
                || AccountSwitchController.isBusy()) return;
        long generation = TaskLifecycle.generation();
        handler.post(() -> {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work == null || !init || offline || AccountSwitchController.isBusy()) return;
                handler.removeCallbacks(earlyTaskRun);
                earlyTaskGeneration = generation;
                handler.postDelayed(earlyTaskRun, 2500);
            }
        });
    }

    private static long earlyTaskGeneration;
    private static final Runnable earlyTaskRun = new Runnable() {
        @Override
        public void run() {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(earlyTaskGeneration)) {
                if (work == null || !init || offline || AccountSwitchController.isBusy()) return;
                Thread running = mainTask == null ? null : mainTask.getThread();
                if ((running != null && running.isAlive()) || ModelTask.hasPendingMainTask())
                    mainHandler.postDelayed(this, 2500);
                else execHandler();
            }
        }
    };

    private static void stopHandler() {
        Runnable reload = pendingAccountReload.getAndSet(null);
        if (reload != null && mainHandler != null) mainHandler.removeCallbacks(reload);
        mainTask.stopTask();
        ModelTask.stopAllTask();
        // 一并清掉待发 tick：否则重载后新周期第一轮会误判「已排期」而跳过续排，下一跳由上一周期的时刻决定
        mainHandler.removeCallbacks(MAIN_TICK);
        tickScheduled = false;
        scheduledExecAt = 0;
    }

    public static void updateDay() {
        // 必须和 dayCalendar 的初始化（onCreate 处 MyUtils.getInstance()）用同一个 GMT+8 时钟，
        // 否则每次跨天重新赋值后 dayCalendar 会静默退回系统时区语义——这是 GR2026 自己都没修全的同类 bug，
        // 这里没有照抄它，两处一起改成 GMT+8 保持自洽
        Calendar nowCalendar = MyUtils.getInstance();
        try {
            int nowYear = nowCalendar.get(Calendar.YEAR);
            int nowMonth = nowCalendar.get(Calendar.MONTH);
            int nowDay = nowCalendar.get(Calendar.DAY_OF_MONTH);
            if (dayCalendar.get(Calendar.YEAR) != nowYear || dayCalendar.get(Calendar.MONTH) != nowMonth || dayCalendar.get(Calendar.DAY_OF_MONTH) != nowDay) {
                dayCalendar = (Calendar) nowCalendar.clone();
                dayCalendar.set(Calendar.HOUR_OF_DAY, 0);
                dayCalendar.set(Calendar.MINUTE, 0);
                dayCalendar.set(Calendar.SECOND, 0);
                Log.record("日期更新为：" + nowYear + "-" + (nowMonth + 1) + "-" + nowDay);
                setWakenAtTimeAlarm();
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Statistics.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            Status.save(nowCalendar);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        try {
            FriendWatch.updateDay();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    @SuppressLint({"ScheduleExactAlarm", "MissingPermission"})
    private static Boolean setAlarmTask(long triggerAtMillis, PendingIntent operation) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation);
            }
        Log.i("setAlarmTask triggerAtMillis:" + Log.DATE_TIME_FORMAT_THREAD_LOCAL.get().format(new java.util.Date(triggerAtMillis)) + " operation:" + (operation == null ? "" : operation.toString()));
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "setAlarmTask err:", th);
        }
        return false;
    }

    private static Boolean unsetAlarmTask(PendingIntent operation) {
        try {
            if (operation != null) {
                AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                alarmManager.cancel(operation);
            }
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "unsetAlarmTask err:", th);
        }
        return false;
    }

    /**
     * 替换 RPC 实现（离线模式、诊断、单元测试注入替身）；传 null 回到默认的支付宝 RPC 桥。
     * <p>注入替身后，RpcCall 构造的请求体（method + data）原样交给替身，
     * 因此不连真机也能检查请求体。
     */
    public static void setRpcBridge(RpcBridge bridge) {
        rpcBridge = bridge;
    }

    public static String requestString(RpcEntity rpcEntity) {
        return rpcBridge.requestString(rpcEntity, 3, -1);
    }

    public static String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        return rpcBridge.requestString(rpcEntity, tryCount, retryInterval);
    }

    public static String requestString(String method, String data) {
        return rpcBridge.requestString(method, data);
    }

    public static String requestString(String method, String data, String relation) {
        return rpcBridge.requestString(method, data, relation);
    }

    public static String requestString(String method, String data, int tryCount, int retryInterval) {
        return rpcBridge.requestString(method, data, tryCount, retryInterval);
    }

    public static String requestString(String method, String data, String relation, int tryCount, int retryInterval) {
        return rpcBridge.requestString(method, data, relation, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(RpcEntity rpcEntity) {
        return rpcBridge.requestObject(rpcEntity, 3, -1);
    }

    public static RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(rpcEntity, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(String method, String data) {
        return rpcBridge.requestObject(method, data);
    }

    public static RpcEntity requestObject(String method, String data, String relation) {
        return rpcBridge.requestObject(method, data, relation);
    }

    public static RpcEntity requestObject(String method, String data, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(method, data, tryCount, retryInterval);
    }

    public static RpcEntity requestObject(String method, String data, String relation, int tryCount, int retryInterval) {
        return rpcBridge.requestObject(method, data, relation, tryCount, retryInterval);
    }

    /**
     * 在本进程内重载 hook，效果等同 restart 广播；同进程自救绕广播只会多依赖一次分发，
     * 而服务刚被销毁时那恰恰是最不可靠的一环。
     */
    private void restartSelf(boolean exec) {
        restartSelf(exec, null);
    }

    private void restartSelf(boolean exec, String requestedUid) {
        long generation = TaskLifecycle.generation();
        new Thread(() -> {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work == null || AccountSwitchController.isBusy()) return;
                if (!StringUtil.isEmpty(requestedUid) && !Objects.equals(requestedUid, UserIdMap.getCurrentUid())) return;
                initHandler(true, null, exec);
            }
        }, "Sesame-Restart").start();
    }

    /** 定时唤醒的 Intent：action 独立于业务广播，并带上由令牌派生的 nonce */
    private static Intent alarmIntent() {
        Intent intent = new Intent(ACTION_ALARM_EXECUTE).setPackage(HOST_PACKAGE_NAME);
        String nonce = alarmNonce();
        if (nonce != null) {
            intent.putExtra("nonce", nonce);
        }
        return intent;
    }

    /**
     * 闹钟口令：由稳定令牌派生，令牌不变则口令不变，PendingIntent 里的快照始终有效。
     * <p>派生而不是存原文：这个 extra 会跟着 PendingIntent 落进系统闹钟，不该放能直接调 HTTP 的凭据。
     */
    private static String alarmNonce() {
        String token = httpToken;
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((token + ":sesame-alarm").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            Log.err(TAG, "alarmNonce err:", t);
            return null;
        }
    }

    private static boolean verifyAlarmNonce(Intent intent) {
        String expected = alarmNonce();
        String given = intent == null ? null : intent.getStringExtra("nonce");
        if (expected == null || given == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                given.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * control 通道鉴权：令牌常数时间比较 + 要求定向投递。
     * <p>{@code getPackage()} 只表示「发送方是否定向」，挡不住伪造；真正拦人的是令牌比较，
     * 定向只决定令牌会不会外泄。
     */
    private static boolean verifyControl(Intent intent) {
        if (intent == null || !HOST_PACKAGE_NAME.equals(intent.getPackage())) {
            return false;
        }
        String token = httpToken;
        String given = intent.getStringExtra("token");
        if (token == null || given == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                given.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** 各 op 的最小间隔：探活快，动作为慢，execute 最慢 */
    private static long controlMinInterval(String op) {
        if (op == null) {
            return CONTROL_MIN_INTERVAL_RESTART;
        }
        switch (op) {
            case "status":
                return CONTROL_MIN_INTERVAL_STATUS;
            case "execute":
                return CONTROL_MIN_INTERVAL_EXECUTE;
            case "openProfile":
                return CONTROL_MIN_INTERVAL_OPEN_PROFILE;
            case "restart":
            case "reloadConfig":
            default:
                return CONTROL_MIN_INTERVAL_RESTART;
        }
    }

    /** 频控：同一 op 两次请求至少间隔各自窗口，挡住令牌泄露后的批量刷；计时按 op 独立 */
    private static boolean controlRateLimited(String op) {
        String key = op == null ? "" : op;
        long min = controlMinInterval(key);
        java.util.concurrent.atomic.AtomicLong slot = lastControlAt.computeIfAbsent(
                key, k -> new java.util.concurrent.atomic.AtomicLong(0));
        long now = System.currentTimeMillis();
        while (true) {
            long last = slot.get();
            if (last != 0 && now - last < min) {
                return true;
            }
            if (slot.compareAndSet(last, now)) {
                return false;
            }
        }
    }

    /**
     * 受控通道统一入口：广播（{@code sesame.control}）与 HTTP 都从这里进，保证语义与副作用一致。
     * @return null 表示已受理，否则是拒绝原因
     */
    public static String dispatchControl(String op, String userId, String group, String data, boolean exec) {
        return dispatchControl(op, userId, group, data, exec, null, null);
    }

    public static String dispatchControl(String op, String userId, String group, String data, boolean exec,
                                         String model, String taskAction) {
        if (!Agreement.isAccepted()) {
            AppConfig.load();
            if (!Agreement.isAccepted()) return "agreement not accepted";
        }
        if (op == null || !CONTROL_OPS.contains(op)) {
            return "unknown op";
        }
        ApplicationHook hook = self;
        if (hook == null) {
            return "hook not loaded";
        }
        switch (op) {
            case "restart":
                // 带 userId 时只在切到该账号后重载，避免旧账号的保存把新账号的进程带跑一遍
                if (StringUtil.isEmpty(userId) || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                    hook.restartSelf(exec, userId);
                }
                return null;
            case "reloadConfig":
                reloadAppConfig();
                return null;
            case "execute":
                // 与 restart 同理：带 userId 时只在切到该账号后才跑，避免切号后旧账号的请求跑新账号任务
                if (StringUtil.isEmpty(userId) || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                    hook.executeGroup(group, userId, model, taskAction);
                }
                return null;
            case "status":
                hook.replyStatus();
                return null;
            case "openProfile":
                openFriendProfile(context, userId);
                return null;
            case "forestQuery":
                // group 承载 method：只接受上面那张表里的值，越界一律拒绝
                if (group == null || !FOREST_QUERY_METHODS.contains(group)) {
                    return "unknown method";
                }
                TestRpc.start("antForest", group, null);
                return null;
            case "walkPath":
                if (group == null || !WALK_PATH_METHODS.contains(group)) {
                    return "unknown method";
                }
                if ("clearCustomWalkPathIdQueue".equals(group)) {
                    TestRpc.start("clearCustomWalkPathIdQueue", null, "setCustomWalkPathIdQueue");
                } else if ("addCustomWalkPathIdQueue".equals(group)) {
                    if (StringUtil.isEmpty(data)) {
                        return "missing data";
                    }
                    TestRpc.start("addCustomWalkPathIdQueue", data, "setCustomWalkPathIdQueue");
                } else {
                    if (StringUtil.isEmpty(data)) {
                        return "missing data";
                    }
                    TestRpc.start("addCustomWalkPathId", data, "setCustomWalkPathIdList");
                }
                return null;
            default:
                return "unknown op";
        }
    }

    /** UI 侧改完共享配置后通知本进程重读：只重读 AppConfig，不重挂 Model hook */
    private static void reloadAppConfig() {
        try {
            AppConfig.load();
            // 「抓包记录」开关即时装卸钩子（原来只在 initHandler 判定，改了必须重启进程才生效）
            if (AppConfig.INSTANCE.getEnableDebugLog()) {
                installRpcRecordHook();
                MiniProgramHook.install(classLoader, processName);
                // 调试模式一开就把令牌镜像到共享目录，PC 侧立刻可读，不必等进程重启或下次解析令牌
                String mirrorToken = resolveHttpToken();
                if (!mirrorToken.isEmpty()) {
                    syncLegacyHttpToken(mirrorToken);
                }
            } else {
                uninstallRpcRecordHook();
                MiniProgramHook.uninstall();
            }
            // 令牌副本跟随抓包开关：关掉调试就立刻删副本，不留到进程重启
            if (!Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableDebugLog())) {
                removeMirrorTokenFile();
            }
            Log.i(TAG, "reload AppConfig from UI");
        } catch (Throwable th) {
            Log.err(TAG, "sesame reloadConfig err:", th);
        }
    }

    /** 跑一轮任务；执行前必须重载配置，否则「刚开启的模块点了执行却不跑」 */
    private void executeGroup(String groupCode) {
        executeGroup(groupCode, null, null, null);
    }

    private void executeGroup(String groupCode, String userId, String modelCode, String manualAction) {
        long generation = TaskLifecycle.generation();
        new Thread(() -> {
            try (TaskLifecycle.Work admission = TaskLifecycle.enter(generation)) {
                if (admission == null) return;
                if (!StringUtil.isEmpty(userId) && !Objects.equals(userId, UserIdMap.getCurrentUid())) return;
                if (StringUtil.isEmpty(modelCode) && StringUtil.isEmpty(groupCode)) {
                    initHandler(false);
                    return;
                }
                try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                    if (work == null) return;
                    String currentUid = UserIdMap.getCurrentUid();
                    if (!StringUtil.isEmpty(userId) && !Objects.equals(userId, currentUid)) return;
                    if (!StringUtil.isEmpty(modelCode) && StringUtil.isEmpty(userId)) return;
                    ModelTask.stopAllTask();
                    ConfigV2.load(currentUid);
                    if (!StringUtil.isEmpty(modelCode)) {
                        boolean started = ModelTask.startNamedTask(modelCode, userId, manualAction == null ? "" : manualAction);
                        Log.record("手动执行【" + modelCode + "】" + (started ? "已启动" : "未启动，请检查开关、账号或任务状态"));
                    } else if (ModelGroup.BASE == ModelGroup.getByCode(groupCode)) {
                        Log.record("开始执行全部任务");
                        io.github.aw1y2z.sesame.util.DailyTask.manual(() -> { ModelTask.startAllTask(false); return null; });
                    } else {
                        Log.record("开始执行分组【" + ModelGroup.getName(groupCode) + "】任务");
                        int count = ModelTask.startGroupTask(groupCode);
                        if (count == 0) Log.record("分组【" + ModelGroup.getName(groupCode) + "】无可执行任务");
                    }
                }
            } catch (Throwable th) {
                Log.printStackTrace(TAG, th);
            }
        }, "Sesame-Execute").start();
    }

    /** 回一条「宿主在线」给模块 App：必须定向，否则第三方伪造也能让 App 误判宿主活着 */
    private void replyStatus() {
        sendStatusBroadcast(context);
    }

    /**
     * 激活/探活的统一回包：定向到模块包 + 带令牌，App 侧据此确认这是宿主发的而不是第三方伪造的。
     * <p>静态是因为 onModuleLoaded 早于 Service.onCreate，那时还拿不到当前实例上的 {@code context}。
     */
    private static void sendStatusBroadcast(Context ctx) {
        if (ctx == null) {
            return;
        }
        try {
            FileUtil.initPrivateDirectory(ctx);
            // 令牌可能尚未解析（Service.onCreate 早于 runInit），先取一次，否则回包缺令牌会被 App 丢掉
            if (httpToken == null) {
                resolveHttpToken();
            }
            String token = httpToken;
            // 空令牌的回包会被 App 当成伪造消息丢掉，宁可不发也要留一条日志
            if (token == null || token.isEmpty()) {
                Log.record("状态回包未发送#令牌不可用，App 会显示未激活");
                return;
            }
            Intent reply = new Intent(ACTION_STATUS_REPLY);
            reply.setPackage(MODULE_PACKAGE_NAME);
            reply.putExtra("token", token);
            ctx.sendBroadcast(reply);
        } catch (Throwable th) {
            Log.err(TAG, "sesame sendBroadcast status err:", th);
        }
    }

    private static int getPendingIntentFlag() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            return PendingIntent.FLAG_UPDATE_CURRENT;
        }
    }

    public static Object getMicroApplicationContext() {
        if (microApplicationContextObject == null) {
            return microApplicationContextObject = XHelpers.callMethod(XHelpers.callStaticMethod(XHelpers.findClass("com.alipay.mobile.framework.AlipayApplication", classLoader), "getInstance"), "getMicroApplicationContext");
        }
        return microApplicationContextObject;
    }

    public static Object getServiceObject(String service) {
        try {
            return XHelpers.callMethod(getMicroApplicationContext(), "findServiceByInterface", service);
        } catch (Throwable th) {
            Log.err(TAG, "getServiceObject err", th);
        }
        return null;
    }

    public static Object getUserObject() {
        try {
            return XHelpers.callMethod(getServiceObject(XHelpers.findClass("com.alipay.mobile.personalbase.service.SocialSdkContactService", classLoader).getName()), "getMyAccountInfoModelByLocal");
        } catch (Throwable th) {
            Log.err(TAG, "getUserObject err", th);
        }
        return null;
    }

    public static String getUserId() {
        try {
            Object userObject = getUserObject();
            if (userObject != null) {
                return (String) XHelpers.getObjectField(userObject, "userId");
            }
        } catch (Throwable th) {
            Log.err(TAG, "getUserId err", th);
        }
        return null;
    }

    private static volatile long lastVerificationLaunch;

    /** 风控要求验证（1009 等）：把支付宝拉到前台让验证界面弹出，由自动滑块处理；10 分钟内只拉一次。 */
    public static void showVerification() {
        if (mainHandler == null || AccountSwitchController.isBusy()) return;
        long now = System.currentTimeMillis();
        if (now - lastVerificationLaunch < 10 * 60_000L) return;
        lastVerificationLaunch = now;
        mainHandler.post(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setClassName(ClassUtil.PACKAGE_NAME, ClassUtil.CURRENT_USING_ACTIVITY);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.record("风控验证🔐已将支付宝切到前台，等待验证界面弹出");
            } catch (Throwable t) {
                Log.err(TAG, "showVerification err:", t);
            }
        });
    }

    public static void reLogin() {
        Handler handler = mainHandler;
        Context ctx = context;
        if (handler == null || ctx == null) {
            Log.record("跳过重登录：mainHandler/context 尚未就绪");
            return;
        }
        long generation = TaskLifecycle.generation();
        handler.post(() -> {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
            if (work == null || AccountSwitchController.isBusy()) return;
            if (reLoginCount.get() < 5) {
                execDelayedHandler(reLoginCount.getAndIncrement() * 5000L);
            } else {
                execDelayedHandler(Math.max(BaseModel.getCheckInterval().getValue(), 180_000));
            }
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setClassName(ClassUtil.PACKAGE_NAME, ClassUtil.CURRENT_USING_ACTIVITY);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            offline = true;
            ctx.startActivity(intent);
            }
        });
    }

    private class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            if (action == null) {
                return;
            }
            switch (action) {
                case ACTION_PAIR_REQUEST:
                    handlePairRequest(context, intent);
                    return;
                case ACTION_ALARM_EXECUTE:
                    // 系统闹钟由 PendingIntent 代发，拿不到发送方 uid，只能靠 extra 里的 nonce 认
                    if (!verifyAlarmNonce(intent)) {
                        Log.record("定时唤醒校验失败，已忽略");
                        return;
                    }
                    executeGroup(null);
                    return;
                case ACTION_CONTROL:
                    // uid 白名单在 API 34 以下取不到发送方，只能当补充；令牌才是这条通道的门
                    if (!isTrustedBroadcastSender(context, this)) {
                        Log.record("受控通道来源不可信，已忽略");
                        return;
                    }
                    if (!verifyControl(intent)) {
                        Log.record("受控通道令牌校验失败，已忽略");
                        return;
                    }
                    String op = intent.getStringExtra("op");
                    // 每个 op 各有窗口（status 2s / restart 10s / execute 60s / openProfile 5s），
                    // 探活不会挤掉动作，动作之间也互不影响
                    if (controlRateLimited(op)) {
                        Log.record("受控通道触发过于频繁，已忽略#" + op);
                        return;
                    }
                    String rejected = dispatchControl(op,
                            intent.getStringExtra("userId"),
                            intent.getStringExtra("group"),
                            intent.getStringExtra("data"),
                            intent.getBooleanExtra("exec", false),
                            intent.getStringExtra("model"), intent.getStringExtra("taskAction"));
                    if (rejected != null) {
                        Log.record("受控通道拒绝#" + op + ":" + rejected);
                    }
                    return;
                default:
                    Log.i("sesame broadcast ignored action:" + action);
            }
        }
    }

    /**
     * 配对：App 发 challenge，宿主把当前令牌定向回给 App 包。
     * <p>故意不校验令牌——App 还没有令牌，这正是它拿令牌的途径。安全性来自回包定向
     * 与令牌只在宿主私有目录，刷屏由 60 秒频控兜住。
     */
    private void handlePairRequest(Context ctx, Intent intent) {
        try {
            long now = System.currentTimeMillis();
            long last = lastPairAt.get();
            if (last != 0 && now - last < PAIR_MIN_INTERVAL) {
                return;
            }
            lastPairAt.set(now);
            String challenge = intent.getStringExtra("challenge");
            if (StringUtil.isEmpty(challenge)) {
                return;
            }
            String token = httpToken;
            if (token == null) {
                token = resolveHttpToken();
            }
            if (StringUtil.isEmpty(token)) {
                Log.record("配对失败：本进程尚无可用令牌");
                return;
            }
            Intent result = new Intent(ACTION_PAIR_RESULT);
            result.setPackage(MODULE_PACKAGE_NAME);
            result.putExtra("token", token);
            result.putExtra("challenge", challenge);
            ctx.sendBroadcast(result);
            Log.record("已响应对口请求");
        } catch (Throwable th) {
            Log.err(TAG, "handlePairRequest err:", th);
        }
    }

    /**
     * 校验广播发送方是否可信。Receiver 必须导出（模块 App 与支付宝不同 UID），
     * 所以「任意应用都能触发 restart」只能在这里按发送方 uid 拦。
     * <p>白名单：本进程、模块 App、adb shell。uid 取自 Android 14+ 的 getSentFromUid()，
     * 低版本与 {@code Process.INVALID_UID}（系统代发，如闹钟触发的定时执行）一律放行，否则定时任务失效。
     * @return true 表示可信
     */
    private static boolean isTrustedBroadcastSender(Context context, BroadcastReceiver receiver) {
        try {
            // 发送方 uid 只有 Android 14+ 的 getSentFromUid 能取到；低版本无从判定，放行保持原行为
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return true;
            }
            int uid = receiver.getSentFromUid();
            if (uid == Process.myUid() || uid == SHELL_UID) {
                return true;
            }
            if (uid == Process.INVALID_UID) {
                // 系统代发的广播（如闹钟到点触发 PendingIntent 的定时执行）取不到来源；
                // 这里放行以免定时任务失效，是本校验唯一的松口
                Log.record("广播来源无法判定，按放行处理");
                return true;
            }
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (String pkg : packages) {
                    if (MODULE_PACKAGE_NAME.equals(pkg)) {
                        return true;
                    }
                }
            }
            Log.record("广播发送方不可信，已忽略#uid=" + uid);
            return false;
        } catch (Throwable t) {
            // 校验本身出错时放行：宁可退回改动前的行为，也不让 restart/reloadConfig 这类正常
            // 流程因校验异常而失效（出错原因已记日志，便于排查）
            Log.err(TAG, "校验广播发送方失败:", t);
            return true;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver(Context context) {
        try {
            if (broadcastReceiverRegistered && broadcastReceiver != null) {
                try {
                    context.unregisterReceiver(broadcastReceiver);
                    broadcastReceiverRegistered = false;
                } catch (Throwable t) {
                    Log.err(TAG, "hook unregisterBroadcastReceiver err:", t);
                }
            }

            // 只留三条：配对（无令牌可用，回包定向）、闹钟（nonce 校验）、受控通道（令牌 + 定向）。
            // 原来的 restart/execute/reLogin/status/rpctest/reloadConfig/openProfile 七条全部并入
            // control，任意应用注册同名 action 也拿不到令牌，触发不了任何动作。
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_PAIR_REQUEST);
            intentFilter.addAction(ACTION_ALARM_EXECUTE);
            intentFilter.addAction(ACTION_CONTROL);

            broadcastReceiver = new AlipayBroadcastReceiver();
            if (controlHandler == null) {
                controlThread = new android.os.HandlerThread("Sesame-Control");
                controlThread.start();
                controlHandler = new Handler(controlThread.getLooper());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, intentFilter, null, controlHandler, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastReceiver, intentFilter, null, controlHandler);
            }
            broadcastReceiverRegistered = true;
        } catch (Throwable th) {
            Log.err(TAG, "hook registerBroadcastReceiver err:", th);
        }
    }

    /**
     * 在宿主（支付宝）进程内打开指定好友的个人主页。
     * <p>必须由本进程发起：模块 App 发的 {@code alipays://} 调用方不是支付宝自身，
     * 会被 {@code appSchemeCheck} 判为不可信而拦掉。
     * @param userId 好友的支付宝 userId
     */
    private static void openFriendProfile(Context context, String userId) {
        requestFriendProfile(context, userId);
    }

    /** 文件消费完成后只在主线程启动页面，广播与焦点探测共用此入口。 */
    private static void launchFriendProfile(Context context, String userId) {
        if (StringUtil.isEmpty(userId)) {
            Log.record("打开好友主页失败#缺少userId");
            return;
        }
        // userId 直接拼进 alipays:// 的查询串，放任任意字符等于把拼串权交给调用方
        if (!USER_ID_PATTERN.matcher(userId).matches()) {
            Log.record("打开好友主页失败#userId 格式非法");
            return;
        }
        // 模块 App 冷启动时会补发几次广播，这里对同一好友做去重，避免重复弹页
        long now = System.currentTimeMillis();
        if (userId.equals(lastProfileUserId) && now - lastProfileTime < 8000) {
            return;
        }
        lastProfileUserId = userId;
        lastProfileTime = now;
        // 标记本次跳转：紧随其后创建的容器页就是好友页，需要接管并关掉上一个好友页
        profileOpenExpectAt = now;
        // 好友主页是 appId=20002167 的小程序页 profilePage。
        // 走 alipays:// scheme 会多起 SchemeLauncherActivity / LauncherActivity 两个空窗口
        // （小窗、分屏下每个 Activity 都是独立窗口），故优先在进程内直接启动。
        if (startProfileInProcess(userId)) {
            // 启动入口没有回执：超时未等到容器页就换下一个入口，都失败才退回 scheme
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            long generation = TaskLifecycle.generation();
            handler.postDelayed(() -> {
                try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work == null) return;
                if (profileOpenExpectAt == 0 || !userId.equals(lastProfileUserId)) {
                    return;
                }
                if (startProfileEventInProcess(userId)) {
                    handler.postDelayed(() -> {
                        try (TaskLifecycle.Work nested = TaskLifecycle.enter(generation)) {
                        if (nested == null) return;
                        if (profileOpenExpectAt != 0 && userId.equals(lastProfileUserId)) {
                            startProfileScheme(context, userId);
                        }
                        }
                    }, 900);
                    return;
                }
                startProfileScheme(context, userId);
                }
            }, 800);
            return;
        }
        startProfileScheme(context, userId);
    }

    /** 走 scheme 跳好友主页，作为进程内启动失败时的退路 */
    private static boolean startProfileScheme(Context context, String userId) {
        try {
            context.startActivity(buildProfileSchemeIntent(userId));
            return true;
        } catch (Throwable th) {
            profileOpenExpectAt = 0;
            Log.err(TAG, "openFriendProfile fallback err#userId=" + userId, th);
            Log.record("打开好友主页失败#userId=" + userId);
            return false;
        }
    }

    private static String buildProfileUri(String userId) {
        return "alipays://platformapi/startApp"
                + "?appId=" + PROFILE_APP_ID
                + "&pageName=profilePage"
                + "&userId=" + userId;
    }

    /** 走 scheme 的跳转 intent，作为进程内 startApp 失败时的退路 */
    private static Intent buildProfileSchemeIntent(String userId) {
        Intent it = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(buildProfileUri(userId)));
        // NEW_TASK + 调用方上下文：支付宝此刻已经在前台，这个 flags 只会复用现有任务
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return it;
    }

    /**
     * 在支付宝进程内直接启动好友主页小程序，返回是否成功发起。
     * <p>
     * 签名是五参数 {@code startApp(from, appId, Bundle params, Bundle extra, activity)}，
     * 启动参数全在第三个 Bundle 里；当成 JSON 字符串传会打开不相干的页面。
     */
    private static boolean startProfileInProcess(String userId) {
        return invokeStartMethod("startApp", userId);
    }


    /** 备选入口：scheme 路径最后拉起容器的是 startAppEvent，签名与参数同上 */
    private static boolean startProfileEventInProcess(String userId) {
        return invokeStartMethod("startAppEvent", userId);
    }

    /**
     * 反射调用框架启动入口。只认「五参数 + 第 2 个是 String(appId) + 第 3 个是 Bundle」的重载：
     * 同名重载很多，只按名字匹配会打错。用 {@code invoke} 而非 {@code XHelpers.callMethod}，
     * 参数为 null 时也能按声明类型匹配。
     */
    private static boolean invokeStartMethod(String methodName, String userId) {
        try {
            Object microApp = getMicroApplicationContext();
            if (microApp == null) {
                return false;
            }
            android.os.Bundle params = buildProfileStartBundle(userId);
            for (Class<?> clazz = microApp.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
                    if (!methodName.equals(method.getName()) || method.getParameterCount() != 5) {
                        continue;
                    }
                    Class<?>[] types = method.getParameterTypes();
                    if (types[2] != android.os.Bundle.class || !types[1].isAssignableFrom(String.class)) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        method.invoke(microApp, null, PROFILE_APP_ID, params, null, null);
                        return true;
                    } catch (Throwable th) {
                        Log.err(TAG, methodName + " invoke err#userId=" + userId, th);
                        return false;
                    }
                }
            }
            Log.i(TAG, "no usable " + methodName + " method on " + microApp.getClass().getName());
            return false;
        } catch (Throwable th) {
            Log.err(TAG, "invokeStartMethod err#" + methodName, th);
            return false;
        }
    }

    /** 好友主页的启动参数，key 与 scheme 路径里的 Bundle 一致 */
    private static android.os.Bundle buildProfileStartBundle(String userId) {
        android.os.Bundle params = new android.os.Bundle();
        params.putString("appId", PROFILE_APP_ID);
        params.putString("pageName", "profilePage");
        params.putString("userId", userId);
        params.putBoolean("recordGestureTime", true);
        params.putBoolean("startFromExternal", false);
        params.putString("ap_framework_sceneId", PROFILE_APP_ID);
        params.putString("ap_framework_scheme", buildProfileUri(userId));
        params.putString("bizLogParam",
                "{\"coldStartup\":false,\"fromScheme\":true,\"showLoginAndGesture\":false}");
        return params;
    }

    /**
     * 接管好友页容器：记下当前实例，并把上一次的好友页关掉。
     * <p>
     * 不关的话多个 {@code CSGAPushActivity} 会叠在同一 task 里，从第二个好友页返回会回到上一个好友。
     */
    private static void handleProfileContainer(Activity activity) {
        if (!isFriendProfileContainer(activity)) {
            return;
        }
        Activity last = lastProfileActivity.get();
        if (last != null && last != activity && !last.isFinishing() && !last.isDestroyed()) {
            last.finish();
        }
        lastProfileActivity = new WeakReference<>(activity);
    }

    /**
     * 判断容器页是否是好友主页：intent 里带好友主页小程序参数，或者是本次跳转后紧接着创建的容器。
     */
    private static boolean isFriendProfileContainer(Activity activity) {
        boolean expect = profileOpenExpectAt > 0
                && System.currentTimeMillis() - profileOpenExpectAt < PROFILE_CONTAINER_EXPECT_WINDOW;
        if (expect) {
            profileOpenExpectAt = 0;
        }
        boolean matched = false;
        try {
            Intent intent = activity.getIntent();
            if (intent != null) {
                String raw = intent.toString() + " " + intent.getDataString();
                matched = raw.contains(PROFILE_APP_ID) || raw.contains("profilePage");
            }
        } catch (Throwable ignored) {
        }
        return matched || expect;
    }

    /**
     * 消费「待打开好友」标记：模块 App 点击好友行时先落盘再拉起支付宝，宿主冷启动后
     * 页面恢复时后台消费，再回主线程跳转，避免文件存储阻塞宿主界面。
     * <p>标记只消费一次，超过 {@link #PENDING_PROFILE_TTL} 视为过期。
     */
    private static void checkPendingProfile(Context context) {
        requestFriendProfile(context, null);
    }

    private static void requestFriendProfile(Context context, String userId) {
        if (context == null) return;
        if (userId != null && !USER_ID_PATTERN.matcher(userId).matches()) {
            Log.record("打开好友主页失败#userId 格式非法");
            return;
        }
        WeakReference<Context> contextRef = new WeakReference<>(context.getApplicationContext());
        long generation = TaskLifecycle.generation();
        String accountUid = UserIdMap.getCurrentUid();
        synchronized (profileProbeLock) {
            // 焦点事件只合并一次探测，不能覆盖尚未处理的显式广播跳转。
            if (userId == null && pendingProfileExplicit && pendingProfileGeneration == generation
                    && Objects.equals(pendingProfileAccountUid, accountUid)) return;
            pendingProfileProbe = () -> consumePendingProfile(contextRef, generation, accountUid, userId);
            pendingProfileExplicit = userId != null;
            pendingProfileGeneration = generation;
            pendingProfileAccountUid = accountUid;
            startProfileProbeWorker();
        }
    }

    /** 调用方持有 profileProbeLock，确保最多一个 drain 在运行或排队。 */
    private static void startProfileProbeWorker() {
        if (profileProbeRunning) return;
        profileProbeRunning = true;
        try {
            profileProbeWorker.execute(ApplicationHook::drainProfileProbes);
        } catch (Throwable t) {
            profileProbeRunning = false;
            Log.err(TAG, "profile probe dispatch err:", t);
        }
    }

    private static void drainProfileProbes() {
        try {
            while (true) {
                Runnable probe;
                synchronized (profileProbeLock) {
                    probe = pendingProfileProbe;
                    pendingProfileProbe = null;
                    pendingProfileExplicit = false;
                }
                if (probe == null) return;
                probe.run();
            }
        } catch (Throwable t) {
            Log.err(TAG, "profile probe err:", t);
        } finally {
            synchronized (profileProbeLock) {
                profileProbeRunning = false;
                // 异常期间或正常退出窗口中新到的请求仍须处理，不能丢失已接受的合并槽。
                if (pendingProfileProbe != null) startProfileProbeWorker();
            }
        }
    }

    private static void consumePendingProfile(WeakReference<Context> contextRef, long generation,
                                               String expectedUid, String explicitUserId) {
        java.io.File claimed = null;
        try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
            if (work == null || (expectedUid != null && !Objects.equals(expectedUid, UserIdMap.getCurrentUid()))) return;
            Context context = contextRef.get();
            if (context == null) return;
            java.io.File file = new java.io.File(FileUtil.MAIN_DIRECTORY_FILE, PENDING_PROFILE_FILE);
            java.io.File consuming = new java.io.File(FileUtil.MAIN_DIRECTORY_FILE, "pending_profile.consuming");
            if (explicitUserId == null && !file.exists() && !consuming.exists()) return;
            // 冷启动时模块 UID 尚未发布，先在后台确认宿主账号；主线程不查宿主服务。
            String accountUid = getUserId();
            if (accountUid == null || (expectedUid != null && !expectedUid.equals(accountUid))) return;
            String userId = explicitUserId;
            if (file.exists()) {
                if (consuming.exists() && !consuming.delete()) return;
                if (!file.renameTo(consuming)) return;
                claimed = consuming;
            } else if (consuming.exists()) {
                claimed = consuming;
            }
            if (claimed != null) {
                String text = FileUtil.readFromFile(claimed);
                String[] parts = text == null ? new String[0] : text.trim().split("\\s+");
                if (parts.length == 2 && USER_ID_PATTERN.matcher(parts[0]).matches()) {
                    try {
                        long age = System.currentTimeMillis() - Long.parseLong(parts[1]);
                        if (explicitUserId == null && age >= 0 && age <= PENDING_PROFILE_TTL) userId = parts[0];
                    } catch (NumberFormatException ignored) { }
                }
            }
            if (userId == null || !USER_ID_PATTERN.matcher(userId).matches()
                    || !accountUid.equals(getUserId())) return;
            String publishedUid = UserIdMap.getCurrentUid();
            if (publishedUid != null && !accountUid.equals(publishedUid)) return;
            if (claimed != null) {
                if (!claimed.delete()) return;
                claimed = null;
            }
            String targetUid = userId;
            new Handler(Looper.getMainLooper()).post(() -> {
                try (TaskLifecycle.Work launchWork = TaskLifecycle.enter(generation)) {
                    if (launchWork == null) return;
                    String currentUid = UserIdMap.getCurrentUid();
                    if (currentUid != null && !accountUid.equals(currentUid)) return;
                    launchFriendProfile(context, targetUid);
                }
            });
        } catch (Throwable th) {
            Log.err(TAG, "checkPendingProfile err:", th);
        } finally {
            // 只清理已领取的文件，不删另一个进程刚写入原路径的新点击。
            if (claimed != null) {
                try {
                    if (!claimed.delete()) Log.record("好友跳转标记清理失败，保留待下次检查");
                } catch (Throwable t) {
                    Log.err(TAG, "profile claim cleanup err:", t);
                }
            }
        }
    }

    // 滑块验证hook注册
    private void initSimplePageManager() {
        // 窗口监控（栈顶 Activity、对话框跟踪）不分版本都要开：图片拼图滑块（PuzzleCaptchaSolver）靠它找验证窗口
        enableWindowMonitoring(classLoader);
        if (shouldEnableSimplePageManager()) {
            addHandler("com.alipay.mobile.nebulax.xriver.activity.XRiverActivity", new Captcha1Handler());
            addHandler("com.eg.android.AlipayGphone.AlipayLogin", new Captcha2Handler());
        }
    }

    /** @return 支付宝版本低于等于 10.6.58.99999 时需启用 SimplePageManager */
    private boolean shouldEnableSimplePageManager() {
        if (alipayVersion.toString().isEmpty()) {
            return false;
        }

        AlipayVersion maxSupported = new AlipayVersion("10.6.58.99999");
        if (alipayVersion.compareTo(maxSupported) > 0) {
            // 只有在不支持时才打印警告
            Log.record("目标应用版本[" + alipayVersion.getVersionString() + "]高于[10.6.58.99999]，不启用“向右滑动”简单滑块处理器；图片拼图滑块由 PuzzleCaptchaSolver 处理");
            return false;
        }

        return true;
    }

    // ----------------------------------------------------------------
    // 宿主前后台询问的回答
    // ----------------------------------------------------------------

    /** 是否已提示过"如实回答"（该事件会反复出现，只留一次痕） */
    private static volatile boolean honestAnswerLogged;
    /** 是否已提示过"取真实状态失败"（失败原因通常固定，避免刷屏） */
    private static volatile boolean originalCallFailedLogged;

    /**
     * 回答宿主的 {@code isInBackground()}：默认仍按原行为谎报 false（"不在后台"），
     * 只有当宿主**真的**在后台、且询问方是风控/滑块链路时如实回答 true
     * ——否则宿主会在后台尝试展示滑块界面，界面出不来、验证流程一直等用户滑动，切回支付宝即卡死。
     */
    private static boolean answerInBackgroundQuestion(XC_MethodHook.MethodHookParam param) {
        Boolean reallyInBackground = originalBoolean(param, "isInBackground");
        if (reallyInBackground != null && reallyInBackground && isRiskControlCaller()) {
            noteHonestAnswerForRiskControl();
            return true;
        }
        return false;
    }

    /**
     * 回答宿主的 {@code isAtFrontDesk()}：默认仍按原行为谎报 true（"在前台"），
     * 只有当宿主**真的**不在前台、且询问方是风控/滑块链路时如实回答 false。
     */
    private static boolean answerAtFrontDeskQuestion(XC_MethodHook.MethodHookParam param) {
        Boolean atFrontDesk = originalBoolean(param, "isAtFrontDesk");
        if (atFrontDesk != null && !atFrontDesk && isRiskControlCaller()) {
            noteHonestAnswerForRiskControl();
            return false;
        }
        return true;
    }

    /**
     * 调用原方法取真实返回值。
     *
     * @return 真值；取不到（异常 / 非布尔）时返回 null，调用方按原行为谎报
     */
    private static Boolean originalBoolean(XC_MethodHook.MethodHookParam param, String what) {
        try {
            Object result = param.callOriginal();
            return result instanceof Boolean ? (Boolean) result : null;
        } catch (Throwable t) {
            if (!originalCallFailedLogged) {
                originalCallFailedLogged = true;
                Log.err(TAG, "取 " + what + " 真实前后台状态失败，继续按原行为谎报:", t);
            }
            return null;
        }
    }

    /**
     * 询问方是否来自风控/滑块链路（{@code com.alipay.rdssecuritysdk} 等）。
     * <p>只在**真实后台**时才会走到这里，因此不影响前台热路径；只看最上面若干帧，够用且便宜。
     */
    private static boolean isRiskControlCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        int limit = Math.min(stack.length, 12);
        for (int i = 3; i < limit; i++) {
            String className = stack[i].getClassName();
            if (className.startsWith("com.alipay.rdssecuritysdk")
                    || className.contains("captcha") || className.contains("Captcha")) {
                return true;
            }
        }
        return false;
    }

    private static void noteHonestAnswerForRiskControl() {
        if (honestAnswerLogged) {
            return;
        }
        honestAnswerLogged = true;
        // 运行事件（非成功记录），写入运行日志便于核对；运行日志受「查看运行日志」开关控制
        Log.i("风控/滑块链路在后台询问前后台状态：已如实回答，避免在后台创建滑块界面（界面出不来、切回支付宝卡死）");
    }
}
