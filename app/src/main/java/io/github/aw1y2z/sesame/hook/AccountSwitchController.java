package io.github.aw1y2z.sesame.hook;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 全局唯一的自动切号轮询器：按设定间隔，在所有任务空闲、且当前不在登录/验证码页时，
 * 把支付宝切到本机下一个历史登录账号，切完后调用 {@link Host#initialize} 触发模块重新加载。
 * <p>
 * 移植自 GR 分支，为适配 M 更简单的 {@link ModelTask} 调度模型做了简化：去掉了原版
 * 整套 TaskLifecycle 会话/租约/冻结令牌机制，改成"切号前确认空闲 + stopAllTask() +
 * 一个 BUSY 标志挡住并发切号"，不追求形式化并发证明，但足够覆盖实际场景——M 的主循环
 * 本来就能容忍"当前账号和预期账号不一致"这种情况（见 ApplicationHook 的 mainTask，
 * 遇到不一致会自己 reLogin），不是从零搭建这层保护。见 doc/MyFix.md。
 */
public final class AccountSwitchController {

    interface Host {
        /** "READY" 表示可以切号；其它字符串会直接作为轮询状态展示。 */
        String readiness();
        String currentUid();
        /** 切号确认完成后调用，触发模块按 expectedUid 重新加载配置/任务。 */
        boolean initialize(String expectedUid);
    }

    private static final ScheduledExecutorService CONTROL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Sesame-AccountSwitch");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final AccountSwitchState STATE = new AccountSwitchState();
    private static final HostAccountSwitchBridge BRIDGE = new HostAccountSwitchBridge();
    private static volatile Host host;
    private static boolean watching;
    private static boolean previouslyEnabled;
    private static long lastActivation = -1;
    private static String armedAccount;
    private static long nextHistoryCheck;
    private static long nextCountCheck;
    private static String lastStatus;
    private static AccountSwitchFlight flight;

    private AccountSwitchController() { }

    static synchronized void configure(Host port) {
        host = port;
        if (!watching) {
            watching = true;
            CONTROL.scheduleWithFixedDelay(AccountSwitchController::tick, 1, 1, TimeUnit.SECONDS);
        }
    }

    /** classLoader/宿主服务刚就绪时调用一次，先做只读探测，不实际切号。 */
    static void hostReady() {
        CONTROL.execute(() -> {
            if (isBusy()) return;
            try {
                List<HostAccountSwitchBridge.Account> accounts = BRIDGE.accounts();
                AccountSwitchAccountCount.publish(accounts.size());
                nextCountCheck = SystemClock.elapsedRealtime() + 30000;
                BRIDGE.probe();
                Log.record("自动切号只读检查：接口已就绪，本机历史登录账号数=" + accounts.size());
                nextHistoryCheck = 0;
            } catch (Throwable unavailable) {
                status("宿主切号接口尚未就绪，未执行切换");
            }
        });
    }

    public static boolean isBusy() { return BUSY.get(); }

    private static boolean captchaPending() {
        try {
            android.app.Activity top = SimplePageManager.getTopActivity();
            if (top == null) return false;
            return AccountSwitchPagePolicy.blocksClass(top.getClass().getName(), io.github.aw1y2z.sesame.util.ClassUtil.CURRENT_USING_ACTIVITY);
        } catch (Throwable unavailable) {
            return true; // 拿不到当前页面信息时按"可能在敏感页"处理，宁可多等一轮
        }
    }

    private static void tick() {
        try {
            AccountSwitchSettings.Values settings = AccountSwitchSettings.read();
            Host port = host;
            if (port == null) { phase("WAIT_HOST"); return; }
            if (!settings.enabled) {
                STATE.disabled();
                previouslyEnabled = false;
                if (flight != null) flight.cancelled = true;
            }
            if (flight != null) { phase("CONFIRMING"); advanceFlight(); return; }
            long countNow = SystemClock.elapsedRealtime();
            if (countNow >= nextCountCheck) {
                nextCountCheck = countNow + 30000;
                try { AccountSwitchAccountCount.publish(BRIDGE.accounts().size()); }
                catch (Throwable unavailable) { AccountSwitchAccountCount.publish(-1); }
            }
            if (!settings.enabled) { phase("DISABLED"); return; }
            String readiness = port.readiness();
            if (!"READY".equals(readiness)) { phase(readiness); return; }
            String current = port.currentUid();
            if (!AccountSwitchState.validUid(current) || !Objects.equals(current, UserIdMap.getCurrentUid())) {
                phase("WAIT_IDENTITY");
                return;
            }
            if (!previouslyEnabled || lastActivation != settings.activation || !Objects.equals(armedAccount, current)) {
                if (lastActivation != settings.activation) STATE.disabled();
                STATE.onRound(current);
                armedAccount = current;
                previouslyEnabled = true;
                lastActivation = settings.activation;
            }
            long now = SystemClock.elapsedRealtime();
            boolean idle = ModelTask.isAllTaskIdle();
            if (!STATE.ready(current, true, idle, false, now, settings.seconds)) {
                phase(STATE.waitPhase(now, settings.seconds, idle));
                return;
            }
            if (captchaPending()) { phase("WAIT_CAPTCHA"); STATE.defer(); return; }
            if (now < nextHistoryCheck) { phase("WAIT_HISTORY"); STATE.defer(); return; }
            List<HostAccountSwitchBridge.Account> accounts = BRIDGE.accounts();
            AccountSwitchAccountCount.publish(accounts.size());
            nextCountCheck = now + 30000;
            List<String> ids = new ArrayList<>();
            for (HostAccountSwitchBridge.Account account : accounts) ids.add(account.uid);
            String next = AccountSwitchState.next(ids, current);
            if (next == null) {
                phase("WAIT_HISTORY");
                status("历史登录账号不足两个，保持当前账号");
                STATE.defer();
                nextHistoryCheck = now + 30000;
                return;
            }
            if (!Objects.equals(BRIDGE.currentUid(), current)) {
                STATE.fail();
                status("宿主账号信息未一致，轮询已暂停");
                return;
            }
            BRIDGE.probe();
            // 上面几步（读账号列表/校验一致性）没有加锁，这里再确认一次空闲，
            // 缩小"确认空闲"和"真正 stopAllTask()"之间的时间窗口。
            if (!ModelTask.isAllTaskIdle()) { STATE.defer(); phase("WAIT_TASKS"); return; }
            BUSY.set(true);
            if (!AccountSwitchSettings.read().enabled || captchaPending()
                    || !Objects.equals(port.currentUid(), current) || !Objects.equals(BRIDGE.currentUid(), current)) {
                BUSY.set(false);
                STATE.defer();
                return;
            }
            HostAccountSwitchBridge.Account target = null;
            for (HostAccountSwitchBridge.Account account : accounts) {
                if (next.equals(account.uid)) target = account;
            }
            if (target == null) { BUSY.set(false); STATE.fail(); return; }
            ModelTask.stopAllTask();
            AccountSwitchFlight started = new AccountSwitchFlight(current, next, SystemClock.elapsedRealtime(), AccountSwitchState.timeoutMillis(30));
            flight = started;
            HostAccountSwitchBridge.Account selected = target;
            phase("SWITCHING");
            status("正在切换到下一个本机账号");
            Thread login = new Thread(() -> {
                try { started.accepted = BRIDGE.switchTo(selected); }
                catch (Throwable rejected) { started.accepted = false; }
                finally { started.returned = true; }
            }, "Sesame-AccountLogin");
            login.setDaemon(true);
            try { login.start(); }
            catch (Throwable failed) { started.accepted = false; started.returned = true; }
        } catch (Throwable error) {
            phase("PAUSED");
            STATE.fail();
            status("切号准备失败，轮询已暂停");
            if (flight == null) { BUSY.set(false); }
        }
    }

    private static void advanceFlight() {
        AccountSwitchFlight active = flight;
        if (active.updateTimeout(SystemClock.elapsedRealtime())) {
            STATE.fail();
            status("切号确认超时，保持任务暂停并等待宿主明确结果");
        }
        if (!active.returned) return;
        String auth;
        try { auth = BRIDGE.currentUid(); } catch (Throwable notReady) { active.stable = 0; return; }
        String local = host.currentUid();
        AccountSwitchFlight.Outcome outcome = active.observe(SystemClock.elapsedRealtime(), auth, local, captchaPending());
        if (outcome == AccountSwitchFlight.Outcome.WAIT) return;
        boolean success = outcome == AccountSwitchFlight.Outcome.SUCCESS;
        boolean initialized;
        try { initialized = host.initialize(auth); }
        catch (Throwable failed) { initialized = false; }
        flight = null;
        BUSY.set(false);
        if (success && initialized) {
            status("切换成功，新账号配置已加载");
            STATE.onRound(auth);
            armedAccount = auth;
        } else {
            STATE.fail();
            status(initialized ? "本次切号未完整确认，轮询已暂停" : "新账号初始化失败，轮询已暂停");
        }
    }

    private static void phase(String phaseName) {
        try { AccountSwitchStatus.publish(phaseName); }
        catch (Throwable ignored) { }
    }

    private static void status(String value) {
        if (!value.equals(lastStatus)) {
            lastStatus = value;
            Log.record("自动切号：" + value);
        }
    }
}
