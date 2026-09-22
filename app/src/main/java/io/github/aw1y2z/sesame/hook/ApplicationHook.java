package io.github.aw1y2z.sesame.hook;

import static io.github.aw1y2z.sesame.hook.SimplePageManager.addHandler;
import static io.github.aw1y2z.sesame.hook.SimplePageManager.enableWindowMonitoring;
import io.github.aw1y2z.sesame.hook.CaptchaHook;

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

import java.util.Objects;

import java.text.SimpleDateFormat;
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
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ApplicationHook extends XposedModule {

    private static final String TAG = ApplicationHook.class.getSimpleName();

    @Getter
    private static final String modelVersion = BuildConfig.VERSION_NAME;

    private static final Map<Object, Object[]> rpcHookMap = new ConcurrentHashMap<>();

    private static final Map<String, PendingIntent> wakenAtTimeAlarmMap = new ConcurrentHashMap<>();

    @Getter
    private static ClassLoader classLoader = null;

    @Getter
    private static Object microApplicationContextObject = null;

    // 新增：全局静态变量，存储当前进程名
    public static String processName; // 供其他方法（如 startIfNeeded）调用

    /** 模块 App 自己的包名：须与 app/build.gradle 的 applicationId 一致，用于校验广播发送方 */
    private static final String MODULE_PACKAGE_NAME = "io.github.aw1y2z.sesame";
    /** adb shell 的 uid：`adb shell am broadcast` 以它发送，放行以便命令行调试 */
    private static final int SHELL_UID = 2000;

    @Getter
    private static Context context = null; // 全局上下文，对应 Kotlin 的 appContext
    @SuppressLint("StaticFieldLeak")
    private static Service service; // 目标 Service 实例，也是 Context 子类

    @Getter
    private static AlipayVersion alipayVersion = new AlipayVersion("");

    @Getter
    private static volatile boolean hooked = false;

    private static volatile boolean init = false;

    /** 标记一次重载是否正在进行，避免重载期间被主线程反复丢后台线程造成重复初始化 */
    private static volatile boolean initializing = false;

    private static volatile Calendar dayCalendar;

    @Getter
    private static volatile boolean offline = false;

    @Getter
    private static final AtomicInteger reLoginCount = new AtomicInteger(0);

    @Getter
    private static Handler mainHandler;
    private static Runnable pendingAccountReload;

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

    public static void setOffline(boolean offline) {
        ApplicationHook.offline = offline;
    }

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        XHelpers.init(this);
        log(4, TAG, "event=module_loaded api=" + getApiVersion()
                + " framework=" + getFrameworkName() + " version=" + getFrameworkVersion());
        try {
            // 读取与 App 共享的日志开关配置，使各分项开关在本进程真正生效
            AppConfig.load();
            // 模块已在 LSPosed 中启用：onModuleLoaded 被调用即代表已启用，
            // 直接标记为已激活（与是否打开 / hook 支付宝无关）
            ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode());
            // 若 UI 已启动，发同进程广播实时刷新界面
            Application app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app != null) {
                app.sendBroadcast(new Intent("io.github.aw1y2z.sesame.status"));
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

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 先提取进程名并赋值给全局变量
        processName = lpparam.processName; // 新增：将 Xposed 提供的进程名赋值给全局变量
        if (ClassUtil.PACKAGE_NAME.equals(lpparam.packageName) && ClassUtil.PACKAGE_NAME.equals(lpparam.processName)) {
            if (hooked) {
                return;
            }
            classLoader = lpparam.classLoader;

            XHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    context = (Context) param.args[0];
                    alipayVersion = new AlipayVersion(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName);
                    try {
                        AlipayMiniMarkHelper.init(classLoader);
                        AuthCodeHelper.init(classLoader);
                        // 启动时不再调用 getAuthCode：返回值本就被丢弃，而它在当前支付宝版本上必然失败
                        //（自建实例未走宿主依赖注入，内部 facade 为 null），只会在日志里留下噪音
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    super.afterHookedMethod(param);
                }
            });
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.nebulaappproxy.api.rpc.H5AppRpcUpdate", classLoader, "matchVersion", classLoader.loadClass(ClassUtil.H5PAGE_NAME), Map.class, String.class, XC_MethodReplacement.returnConstant(false));
                Log.i(TAG, "hook matchVersion successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook matchVersion err:", t);
            }
            try {
                XHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity", classLoader, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Activity onResume");
                        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
                        if (work == null) return;
                        String targetUid = getUserId();
                        if (targetUid == null) {
                            Log.record("用户未登录");
                            Toast.show("用户未登录");
                            return;
                        }
                        if (!init) {
                            // 重载在后台线程执行，加载成功后会自行置 init=true；此处无需依赖返回值
                            initHandler(true);
                            return;
                        }
                        String currentUid = UserIdMap.getCurrentUid();
                        if (!targetUid.equals(currentUid)) {
                            if (currentUid != null) {
                                scheduleAccountReload();
                                return;
                            }
                            UserIdMap.initUser(targetUid);
                        }
                        if (offline) {
                            offline = false;
                            execHandler();
                            ((Activity) param.thisObject).finish();
                            Log.i(TAG, "Activity reLogin");
                        }
                        }
                    }
                });
                Log.i(TAG, "hook login successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook login err:", t);
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
                        try {
                            appService.sendBroadcast(new Intent("io.github.aw1y2z.sesame.status"));
                        } catch (Throwable t) {
                            // 广播失败会导致 UI 迟迟显示「未激活」，留一行便于排查
                            Log.i(TAG, "发送激活状态广播失败: " + t);
                        }

                        Log.i(TAG, "Service onCreate");
                        context = appService.getApplicationContext();
                        // 庄园饲料任务已全部走 AntFarmRpcCall 的 Java RPC 实现（见 AntFarm.java），
                        // 不再需要加载 libsesame.so；已移除强制加载，jniLibs/util/LibraryUtil 一并删除，见 doc/MyFix.md
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
                                if (!init) {
                                    return;
                                }
                                if (AccountSwitchController.isBusy()) {
                                    execDelayedHandler(BaseModel.getCheckInterval().getValue());
                                    return;
                                }
                                Log.record("应用版本：" + alipayVersion.getVersionString());
                                Log.record("模块版本：" + modelVersion + "（交流更新QQ群：694474777）");
                                Log.record("编译时间：" + BuildConfig.BUILD_TIME);
                                String captchaSummary = CaptchaTriggerStats.summary();
                                if (!captchaSummary.isEmpty()) {
                                    Log.record(captchaSummary);
                                }
                                Log.record("开始执行" + MyUtils.recordUserName(getUserId()));
                                try {
                                    int checkInterval = BaseModel.getCheckInterval().getValue();
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
                                        if (!checkTask.get(30, TimeUnit.SECONDS)) {
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

                                    execDelayedHandler(checkInterval);
                                    FileUtil.clearLog();
                                } catch (Exception e) {
                                    Log.record("执行异常:");
                                    Log.printStackTrace(e);
                                }
                            }
                        });
                        dayCalendar = MyUtils.getInstance();
                        Statistics.load();
                        FriendWatch.load();
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
                Log.i(TAG, "hook service onCreate successfully");
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
                        restartByBroadcast();
                    }
                });
            } catch (Throwable t) {
                Log.err(TAG, "hook service onDestroy err:", t);
            }
            // ---- 宿主的前后台询问：默认仍按原逻辑"谎报"，唯独风控/滑块链路在真实后台时如实回答 ----
            // 原先这四个 hook 一律哄宿主"你在前台"，模块的后台任务（H5/RPC）才跑得动；
            // 副作用是滑块验证也被判定为可展示，而后台拿不到可见窗口 →
            // 滑块界面出不来、验证流程一直等用户滑动 → 切回支付宝即卡死。
            // 现在改为：先问宿主自己拿真值（callOriginal），只有"真在后台 + 询问方是风控/滑块链路"才说实话。
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
                Log.i(TAG, "hook MiscUtils successfully");
            } catch (Throwable t) {
                Log.err(TAG, "hook MiscUtils err:", t);
            }
            hooked = true;
            Log.i(TAG, "load success: " + lpparam.packageName);
        }
    }

    private static void setWakenAtTimeAlarm() {
        try {
            unsetWakenAtTimeAlarm();
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                // 按北京时间算"明天 00:00"，否则宿主设备非东八区时这个固定唤醒会偏移到错误的本地时刻
                Calendar calendar = MyUtils.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                if (setAlarmTask(calendar.getTimeInMillis(), pendingIntent)) {
                    alarm0Pi = pendingIntent;
                    Log.record("设置定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.err(TAG, "setWakenAt0 err:", e);
            }
            List<String> wakenAtTimeList = BaseModel.getWakenAtTimeList().getValue();
            if (wakenAtTimeList != null && !wakenAtTimeList.isEmpty()) {
                // 之前这里特意没跟 TimeUtil.getTodayCalendarByTimeStr 用同一时区（那时 TimeUtil 还是系统时区），
                // 现在 TimeUtil 已经整体改成 GMT+8（见 doc/MyFix.md），这里改用 TimeUtil.getNow() 保持一致，
                // 不会再产生比较错位
                Calendar nowCalendar = TimeUtil.getNow();
                for (int i = 1, len = wakenAtTimeList.size(); i < len; i++) {
                    try {
                        String wakenAtTime = wakenAtTimeList.get(i);
                        Calendar wakenAtTimeCalendar = TimeUtil.getTodayCalendarByTimeStr(wakenAtTime);
                        if (wakenAtTimeCalendar != null) {
                            if (wakenAtTimeCalendar.compareTo(nowCalendar) > 0) {
                                PendingIntent wakenAtTimePendingIntent = PendingIntent.getBroadcast(context, i, new Intent("com.eg.android.AlipayGphone.sesame.execute"), getPendingIntentFlag());
                                if (setAlarmTask(wakenAtTimeCalendar.getTimeInMillis(), wakenAtTimePendingIntent)) {
                                    String wakenAtTimeKey = i + "|" + wakenAtTime;
                                    wakenAtTimeAlarmMap.put(wakenAtTimeKey, wakenAtTimePendingIntent);
                                    Log.record("设置定时唤醒:" + wakenAtTimeKey);
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
                        Log.record("取消定时唤醒:" + wakenAtTimeKey);
                    }
                } catch (Exception e) {
                    Log.err(TAG, "unsetWakenAtTime err:", e);
                }
            }
            try {
                if (unsetAlarmTask(alarm0Pi)) {
                    alarm0Pi = null;
                    Log.record("取消定时唤醒:0|000000");
                }
            } catch (Exception e) {
                Log.err(TAG, "unsetWakenAt0 err:", e);
            }
        } catch (Exception e) {
            Log.err(TAG, "unsetWakenAtTimeAlarm err:", e);
        }
    }

    private Boolean initHandler(Boolean force) {
        return initHandler(force, null);
    }

    /**
     * 切换账号 / 首启的重载入口。
     * 重载（force=true）包含大量文件 IO、整份配置 JSON 反序列化、反射建 Model、逐 Model 装 Hook，
     * 这些若在「主线程」同步执行会把支付宝界面卡住（表现为"切号卡死不动"）。
     * 因此这里只做需要 UI 反馈的快速前置检查，真正的重活统一交给 {@link #runInit} 在后台线程执行；
     * 后台线程里仍然走 TaskLifecycle 守卫，避免和切号并发。
     */
    private Boolean initHandler(Boolean force, TaskLifecycle.Freeze owner) {
        if (service == null) {
            return false;
        }
        // 快速前置检查：未登录 / 无闹钟权限，留在调用线程同步返回（Toast 内部已切主线程，后台调用也安全）
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
        // 主线程调用则丢到后台线程执行，避免卡 UI；广播重启等已在后台线程的场景直接同步执行
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (initializing) {
                return false;
            }
            initializing = true;
            final Boolean f = force;
            new Thread(() -> {
                try {
                    runInit(f, owner);
                } catch (Throwable t) {
                    initializing = false;
                    Log.err(TAG, "Sesame-InitHandler err:", t);
                }
            }, "Sesame-InitHandler").start();
            return null;
        }
        return runInit(force, owner);
    }

    /**
     * 真正执行重载，必须在非主线程调用。UI 相关（Toast / 权限提示）已内部切回主线程，
     * 故整体跑在后台线程是安全的。
     * synchronized 保证同一时刻只有一处重载，防止切号与首启 / 广播重启并发触发重复初始化；
     * TaskLifecycle 守卫防止和账号切换窗口重叠。
     */
    private synchronized Boolean runInit(Boolean force, TaskLifecycle.Freeze owner) {
        try (TaskLifecycle.Work work = TaskLifecycle.enterInitialization(owner)) {
            if (work == null) {
                Log.i(TAG, "重载被 TaskLifecycle 拒绝（账号切换窗口期），本次跳过，等下次触发");
                return false;
            }
            boolean initialized = initializeHandler(force);
            if (initialized && owner == null) BaseModel.initData();
            return initialized;
        } finally {
            initializing = false;
        }
    }

    @SuppressLint("WakelockTimeout")
    private Boolean initializeHandler(Boolean force) {
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
                ModuleHttpServerManager.getInstance().startIfNeeded(8080, "ET3vB^#td87sQqKaY*eMUJXP", processName, "com.eg.android.AlipayGphone");

                UserIdMap.initUser(userId);
                Model.initAllModel();
                Log.record("模块版本：" + modelVersion);
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
                Log.record("加载完成");
                Toast.show("芝麻粒-M加载成功:" + modelVersion);
            }
            offline = false;
            init = true;
            execHandler();
            return true;
        } catch (Throwable th) {
            Log.err(TAG, "startHandler err:", th);
            Toast.show("芝麻粒-M加载失败");
            return false;
        }
    }

    /**
     * 安装抓包钩子（请求 + 返回各一个）。
     * 开关统一为日志页的「抓包记录」({@code AppConfig.enableDebugLog})，全局生效；
     * 老接口没有 RpcBridgeExtension.rpc / DefaultBridgeCallback.sendJSONResponse，故仍要求「使用新接口」开着。
     * 重复调用不会叠加 hook。
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

    private void scheduleAccountReload() {
        if (mainHandler == null) return;
        if (pendingAccountReload != null) mainHandler.removeCallbacks(pendingAccountReload);
        long generation = TaskLifecycle.generation();
        pendingAccountReload = () -> {
            pendingAccountReload = null;
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work == null) return;
                String targetUid = getUserId();
                if (targetUid == null || targetUid.equals(UserIdMap.getCurrentUid())) return;
                // 主线程调用，重载已转后台线程执行，返回值不能拆箱判断（可能为 null）；
                // 加载成功/失败 initializeHandler 自己会 Toast，这里只在真正触发了重载时记录切换
                // （false 是前置校验没过——未登录/无权限/已在初始化中——initHandler 内部已经自己提示过，不重复）
                Boolean triggered = initHandler(true);
                if (!Boolean.FALSE.equals(triggered)) {
                    Log.record("用户已切换");
                    Toast.show("用户已切换");
                }
            }
        };
        mainHandler.postDelayed(pendingAccountReload, 1000);
    }

    private static void execHandler() {
        if (init && mainTask != null && !AccountSwitchController.isBusy()) mainTask.startTask(false);
    }

    private static void execDelayedHandler(long delayMillis) {
        if (mainHandler == null || !TaskLifecycle.isOpen()) return;
        long generation = TaskLifecycle.generation();
        mainHandler.postDelayed(() -> {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work != null) execHandler();
            }
        }, delayMillis);
        try {
            NotificationUtil.updateNextExecText(System.currentTimeMillis() + delayMillis);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
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
        if (pendingAccountReload != null && mainHandler != null) mainHandler.removeCallbacks(pendingAccountReload);
        pendingAccountReload = null;
        mainTask.stopTask();
        ModelTask.stopAllTask();
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
            Log.i("setAlarmTask triggerAtMillis:" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(triggerAtMillis) + " operation:" + (operation == null ? "" : operation.toString()));
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
     * 替换 RPC 实现（离线模式、诊断、单元测试注入替身用）；传 null 表示回到默认的支付宝 RPC 桥。
     * <p>不注入时行为与原先完全一致：一律转发给 startHandler 里创建的 {@code rpcBridge}。
     * <p>注入替身后，各 RpcCall 构造出的请求体（method + data）会原样交给替身，
     * 因此可以在不连真机的情况下检查请求体本身是否正确。
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

    public static void reLoginByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.reLogin"));
        } catch (Throwable th) {
            Log.err(TAG, "sesame sendBroadcast reLogin err:", th);
        }
    }

    public static void restartByBroadcast() {
        try {
            context.sendBroadcast(new Intent("com.eg.android.AlipayGphone.sesame.restart"));
        } catch (Throwable th) {
            Log.err(TAG, "sesame sendBroadcast restart err:", th);
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
        if (mainHandler == null) return;
        long generation = TaskLifecycle.generation();
        mainHandler.post(() -> {
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
            context.startActivity(intent);
            }
        });
    }

    private class AlipayBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Receiver 为运行时注册，Android 13+ 必须 RECEIVER_EXPORTED（模块 App 与支付宝是不同
            // UID，跨进程送达只能靠导出），所以"任意应用都能触发 restart/reLogin"只能在此按 uid 拦
            if (!isTrustedBroadcastSender(context, this)) {
                Log.record("广播来源不在白名单，已忽略#" + action);
                return;
            }
            Log.i("sesame broadcast action:" + action + " intent:" + intent);
            if (action != null) {
                switch (action) {
                    case "com.eg.android.AlipayGphone.sesame.restart":
                        String userId = intent.getStringExtra("userId");
                        if (StringUtil.isEmpty(userId) || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                            BroadcastReceiver.PendingResult r = goAsync();
                            new Thread(() -> {
                                try {
                                    initHandler(true);
                                } catch (Throwable th) {
                                    Log.printStackTrace(TAG, th);
                                }
                                r.finish();
                            }, "Sesame-Restart").start();
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.execute":
                        // 配置页"执行"按钮会带 group（ModelGroup 的 code）：BASE＝全部任务，
                        // 其余只跑该分组的任务；不带 group 时保持原行为（整轮执行）。
                        String groupCode = intent.getStringExtra("group");
                        BroadcastReceiver.PendingResult r2 = goAsync();
                        new Thread(() -> {
                            try {
                                if (StringUtil.isEmpty(groupCode)) {
                                    initHandler(false);
                                } else if (ModelGroup.BASE == ModelGroup.getByCode(groupCode)) {
                                    ModelTask.stopAllTask();
                                    ModelTask.startAllTask(false);
                                    Log.record("开始执行全部任务");
                                } else {
                                    ModelTask.stopAllTask();
                                    int count = ModelTask.startGroupTask(groupCode);
                                    Log.record("开始执行分组【" + ModelGroup.getName(groupCode) + "】任务: " + count + " 个");
                                }
                            } catch (Throwable th) {
                                Log.printStackTrace(TAG, th);
                            }
                            r2.finish();
                        }, "Sesame-Execute").start();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reLogin":
                        reLogin();
                        break;
                    case "com.eg.android.AlipayGphone.sesame.status":
                        try {
                            Log.i(TAG, "broadcast: recv query, send active status");
                            context.sendBroadcast(new Intent("io.github.aw1y2z.sesame.status"));
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame sendBroadcast status err:", th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.rpctest":
                        try {
                            String method = intent.getStringExtra("method");
                            String data = intent.getStringExtra("data");
                            String type = intent.getStringExtra("type");
                            // Log.record("收到测试消息:\n方法:" + method + "\n数据:" + data + "\n类型:" + type);
                            TestRpc.start(method, data, type);
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame rpctest err:", th);
                        }
                        break;
                    case "com.eg.android.AlipayGphone.sesame.reloadConfig":
                        // UI 侧修改日志开关等共享配置后通知本进程重载,使开关即时生效
                        try {
                            AppConfig.load();
                            // 「抓包记录」开关即时装卸钩子（原来只在 initHandler 判定，改了必须重启进程才生效）
                            if (AppConfig.INSTANCE.getEnableDebugLog()) {
                                installRpcRecordHook();
                            } else {
                                uninstallRpcRecordHook();
                            }
                            Log.i(TAG, "reload AppConfig from UI");
                        } catch (Throwable th) {
                            Log.err(TAG, "sesame reloadConfig err:", th);
                        }
                        break;
                }
            }
        }
    }

    /**
     * 校验广播发送方是否可信。
     * <p>
     * 该 Receiver 由运行时注册，Android 13 起必须带 {@code RECEIVER_EXPORTED}：模块 App
     * （{@code io.github.aw1y2z.sesame}）与支付宝是两个不同 UID，跨进程送达只能靠导出，
     * 所以"任意应用都能触发 restart / reLogin"只能在收到广播后按发送方 uid 拦。
     * <p>
     * 白名单：本进程（支付宝自己发的，含由系统代发的 PendingIntent）、模块 App、adb shell（调试用）。
     * <p>
     * 发送方 uid 取自 {@code BroadcastReceiver.getSentFromUid()}——该 API 自 Android 14（API 34）起
     * 提供。低版本无从判定，直接放行以免误伤；Android 14+ 上若返回 {@code Process.INVALID_UID}
     * （广播由系统代发时取不到来源）同样放行并记日志，因为闹钟触发的定时执行走的就是这条路径，
     * 收紧会让定时任务失效。
     *
     * @return true 表示可信，继续处理
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
                    Log.i(TAG, "hook unregisterBroadcastReceiver successfully");
                } catch (Throwable t) {
                    Log.err(TAG, "hook unregisterBroadcastReceiver err:", t);
                }
            }

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.restart");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.execute");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reLogin");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.status");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.rpctest");
            intentFilter.addAction("com.eg.android.AlipayGphone.sesame.reloadConfig");

            broadcastReceiver = new AlipayBroadcastReceiver();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastReceiver, intentFilter);
            }
            broadcastReceiverRegistered = true;
            Log.i(TAG, "hook registerBroadcastReceiver successfully");
        } catch (Throwable th) {
            Log.err(TAG, "hook registerBroadcastReceiver err:", th);
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

    /**
     * 检查目标应用版本是否需要启用SimplePageManager功能
     *
     * @return true表示版本低于等于10.6.58.99999，需要启用；false表示不需要
     */
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
        // 用 other 日志：该事件是"宿主在后台要展示风控/滑块界面"的直接证据，而 other 日志默认开启、便于核对；
        // 运行日志（Log.record）受「查看运行日志」开关控制，很多用户是关着的，写在那里等于看不见
        Log.other("风控/滑块链路在后台询问前后台状态：已如实回答，避免在后台创建滑块界面（界面出不来、切回支付宝卡死）");
    }
}
