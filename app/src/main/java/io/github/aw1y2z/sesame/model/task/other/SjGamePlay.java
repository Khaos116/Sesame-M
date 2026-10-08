package io.github.aw1y2z.sesame.model.task.other;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.SimplePageManager;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** Real foreground interaction, without manufacturing game-completion RPCs. */
public final class SjGamePlay {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final String RIDE_URL = "alipays://platformapi/startapp?appId=2018073060792690&page=pages%2Fsesame-dog-play-page%2Findex&query=chn%253Dfenxiang.leyouji";
    private static final String RIDE_JS = "(function(){if(location.hash.indexOf('sesame-dog')<0)return 'NOT_GAME_PAGE';function vis(e){if(!e)return false;var s=getComputedStyle(e),r=e.getBoundingClientRect();return s.display!='none'&&s.visibility!='hidden'&&s.opacity!='0'&&r.width>0&&r.height>0&&r.top<innerHeight-50&&r.bottom>50;}function tap(e){var r=e.getBoundingClientRect(),t=new Touch({identifier:1,target:e,clientX:r.left+r.width/2,clientY:r.top+r.height/2});e.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true,touches:[t],targetTouches:[t],changedTouches:[t]}));e.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true,touches:[],targetTouches:[],changedTouches:[t]}));}var e=document.querySelector('.play-photo-close')||document.querySelector('.toy-popup-container-close');if(vis(e)){tap(e);return 'CLOSED';}if(vis(document.querySelector('.task-intercept-btn-img')))return 'NEEDS_MANUAL';var a=document.querySelectorAll('div,span,button');for(var i=0;i<a.length;i++){if(vis(a[i])){var t=(a[i].innerText||'').trim();if(t==='到达终点')return 'DESTINATION';if(t==='通行证已到手'||t==='继续出发'){tap(a[i]);return 'RESUME';}}}e=document.querySelector('.in-progress-go')||document.querySelector('.tili-box');if(vis(e)){tap(e);return 'RIDE';}return 'NO_ACTION';})()";
    private SjGamePlay() { }

    static void ride(SjActivityTasks worker) throws Exception {
        if (!worker.enabled("ride") || !ready() || !BUSY.compareAndSet(false, true)) return;
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null || !worker.reserve("ride", "foreground-session")) return;
            String uid = UserIdMap.getCurrentUid(); int day = SjActivityTasks.date(); long generation = TaskLifecycle.generation();
            launch(RIDE_URL, uid, day, generation); worker.waitSeconds(8);
            int steps = 0;
            for (int round = 0; round < 60 && steps < 30; round++) {
                worker.current();
                String result = evaluateRide(uid, day, generation);
                if (result == null || "NOT_GAME_PAGE".equals(result)) return;
                if ("NEEDS_MANUAL".equals(result) || "DESTINATION".equals(result)) {
                    Log.record("乐游记：" + ("DESTINATION".equals(result) ? "已到终点，请手动领取大奖" : "关卡需要人工处理"));
                    finishRide(worker); return;
                }
                if ("RIDE".equals(result)) steps++;
                worker.waitSeconds(1);
            }
            finishRide(worker);
        } finally { BUSY.set(false); }
    }

    private static void finishRide(SjActivityTasks worker) {
        worker.current();
        if (io.github.aw1y2z.sesame.data.RuntimeInfo.getInstance().putVerified("sjActivityReceipt::ride", null))
            Log.record("乐游记：本轮页面操作结束，奖励以游戏页面为准");
    }

    public static void goldenBeans(boolean enabled, int limit, Set<String> blacklist) {
        if (!enabled || limit <= 0 || !ready() || !BUSY.compareAndSet(false, true)) return;
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return;
            SjActivityTasks worker = new SjActivityTasks(new OtherRequestGate(), Math.min(limit, 3), "sjGamePlayAttempts");
            String uid = UserIdMap.getCurrentUid(); int day = SjActivityTasks.date(); long generation = TaskLifecycle.generation();
            JSONArray rows = beanRows(worker);
            if (rows == null) return;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i), display = row == null ? null : row.optJSONObject("taskDisplayConfig");
                String id = SjActivityTasks.text(row, "taskId"), title = SjActivityTasks.text(display, "title"), url = SjActivityTasks.text(display, "targetUrl"), app = gameApp(url);
                if (app.isEmpty() || id.isEmpty() || !"TODO".equals(row.optString("taskStatus")) || SjActivityTasks.unsafe(title)
                        || blacklist != null && (blacklist.contains(id) || blacklist.contains(title)) || SjActivityTasks.unique(rows, "taskId", id) == null) continue;
                JSONObject fresh = SjActivityTasks.unique(beanRows(worker), "taskId", id);
                if (!sameGameIdentity(row, fresh) || !"TODO".equals(fresh.optString("taskStatus")) || !worker.reserve("game:" + app, "play:" + id)) return;
                launch(url, uid, day, generation); worker.waitSeconds(8);
                for (int[] step : script(app)) {
                    worker.current();
                    if (step[0] != 1 && !tap(app, step[1], step[2], uid, day, generation)) return;
                    worker.waitSeconds(step[3]);
                }
                boolean completed = false;
                for (int retry = 0; retry < 7; retry++) {
                    worker.waitSeconds(10); fresh = SjActivityTasks.unique(beanRows(worker), "taskId", id);
                    if (!sameGameIdentity(row, fresh)) return;
                    if (Set.of("FINISHED", "RECEIVED", "DONE").contains(fresh.optString("taskStatus"))) { completed = true; break; }
                }
                if (!completed || !worker.confirmed("game:" + app, "金豆小游戏[" + title + "]")) return;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (OtherRequestGate.Denied | OtherRequestGate.BudgetExhausted stopped) { }
        catch (Exception e) { Log.err("SjGamePlay", "小游戏操作停止，未确认动作不重发", e); }
        finally { BUSY.set(false); }
    }

    private static JSONArray beanRows(SjActivityTasks worker) throws Exception {
        JSONObject root = worker.call("com.alipay.goldenbean.index", MyUtils.newJSONObject().put("source", "h5").put("bizType", "MASTER")
                .put("version", "20260901.01").put("darwinSceneList", new JSONArray().put("indexLayoutTwo").put("indexLoadingOptimization").put("indexPreRequestCacheAB").put("taskFlowHandGuideInf")), false);
        JSONArray rows = root == null ? null : root.optJSONArray("taskList");
        return rows != null && rows.length() <= 100 ? rows : null;
    }

    static String gameApp(String url) {
        try {
            Uri uri = Uri.parse(url);
            if (!"alipays".equals(uri.getScheme()) || !"platformapi".equals(uri.getHost()) || !"/startapp".equals(uri.getPath()) || uri.getQueryParameters("appId").size() != 1) return "";
            String app = uri.getQueryParameter("appId");
            return app != null && Set.of("2060170000362587", "2021005132680209", "2021004173661702").contains(app) ? app : "";
        } catch (RuntimeException e) { return ""; }
    }

    private static boolean sameGameIdentity(JSONObject before, JSONObject after) {
        JSONObject a = before == null ? null : before.optJSONObject("taskDisplayConfig"), b = after == null ? null : after.optJSONObject("taskDisplayConfig");
        return a != null && b != null && SjActivityTasks.text(before, "taskId").equals(SjActivityTasks.text(after, "taskId"))
                && SjActivityTasks.text(a, "targetUrl").equals(SjActivityTasks.text(b, "targetUrl")) && SjActivityTasks.text(a, "title").equals(SjActivityTasks.text(b, "title"));
    }

    static int[][] script(String app) {
        if ("2021005132680209".equals(app)) return new int[][]{{0,986,318,2},{0,540,1850,3},{0,300,1200,3},{0,300,1200,3},{0,300,1200,3},{1,0,0,15},{0,540,1900,2}};
        if ("2021004173661702".equals(app)) {
            int[][] steps = new int[21][4]; for (int i = 0; i < 20; i++) steps[i] = new int[]{0,540,1500,3};
            steps[20] = new int[]{1,0,0,30}; return steps;
        }
        return new int[][]{{0,986,318,2},{0,986,318,2},{0,986,438,2},{0,540,1900,3},{0,300,1150,3},{0,540,1950,4},
                {0,300,1100,2},{0,540,1100,2},{0,780,1100,2},{0,300,1500,2},{0,540,1500,2},{0,780,1500,2},
                {0,300,1100,3},{0,540,1100,3},{0,780,1100,3},{0,300,1500,3},{0,540,1500,3},{0,780,1500,3},{1,0,0,60},{0,540,1900,3},{0,540,1900,2}};
    }

    private static boolean ready() { return Looper.myLooper() != Looper.getMainLooper() && !ApplicationHook.isOffline() && screenReady(); }

    private static boolean screenReady() {
        Context context = ApplicationHook.getContext(); if (context == null) return false;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return power != null && power.isInteractive() && keyguard != null && !keyguard.isKeyguardLocked();
    }

    private static void owner(String uid, int day, long generation) {
        TimeUtil.sleep(0);
        if (uid == null || TaskLifecycle.generation() != generation || !TaskLifecycle.isOpen() || !uid.equals(UserIdMap.getCurrentUid())
                || SjActivityTasks.date() != day || ApplicationHook.isOffline()) throw new TaskCancelledException();
    }

    private static <T> T main(Callable<T> action, String uid, int day, long generation) throws Exception {
        FutureTask<T> task = new FutureTask<>(() -> {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work == null) throw new TaskCancelledException(); owner(uid, day, generation); return action.call();
            }
        });
        SimplePageManager.handler.post(task);
        try { return task.get(4, TimeUnit.SECONDS); }
        catch (ExecutionException e) { if (e.getCause() instanceof TaskCancelledException) throw (TaskCancelledException) e.getCause(); throw e; }
        finally { task.cancel(false); }
    }

    private static void launch(String url, String uid, int day, long generation) throws Exception {
        owner(uid, day, generation);
        main(() -> {
            if (!screenReady()) throw new TaskCancelledException();
            Context context = ApplicationHook.getContext();
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(context.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }, uid, day, generation);
    }

    private static View gameRoot(String app) {
        Activity top = SimplePageManager.getTopActivity();
        if (!screenReady() || top == null || top.isFinishing() || top.getWindow() == null || !top.hasWindowFocus()
                || !top.getClass().getName().contains("XRiver") || !top.getPackageName().equals(ApplicationHook.getContext().getPackageName())) return null;
        String identity = top.getIntent() == null ? "" : top.getIntent().toUri(0);
        if (!java.util.regex.Pattern.compile("(?<![0-9])" + java.util.regex.Pattern.quote(app) + "(?![0-9])").matcher(identity).find()) return null;
        View root = top.getWindow().getDecorView();
        return root.isShown() && root.isAttachedToWindow() && root.getWidth() > 0 && root.getHeight() > 0 ? root : null;
    }

    private static boolean tap(String app, int x, int y, String uid, int day, long generation) throws Exception {
        return main(() -> {
            View root = gameRoot(app); if (root == null) return false;
            float px = x * root.getWidth() / 1080f, py = y * root.getHeight() / 2400f; long down = SystemClock.uptimeMillis();
            MotionEvent begin = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, px, py, 0), end = MotionEvent.obtain(down, down + 80, MotionEvent.ACTION_UP, px, py, 0);
            boolean released = false;
            try { root.dispatchTouchEvent(begin); owner(uid, day, generation);
                if (gameRoot(app) != root) throw new TaskCancelledException();
                root.dispatchTouchEvent(end); released = true; return true; }
            finally {
                if (!released) {
                    MotionEvent cancel = MotionEvent.obtain(down, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, px, py, 0);
                    try { root.dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
                }
                begin.recycle(); end.recycle();
            }
        }, uid, day, generation);
    }

    private static View web(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = group.getChildCount() - 1; i >= 0; i--) { View candidate = web(group.getChildAt(i)); if (candidate != null) return candidate; }
        }
        return root.isShown() && (root instanceof android.webkit.WebView || "com.alipay.mywebview.sdk.WebView".equals(root.getClass().getName())) ? root : null;
    }

    private static String evaluateRide(String uid, int day, long generation) throws Exception {
        CountDownLatch latch = new CountDownLatch(1); AtomicReference<String> result = new AtomicReference<>();
        boolean submitted = main(() -> {
            View root = gameRoot("2018073060792690"), view = root == null ? null : web(root); if (view == null) return false;
            for (Method method : view.getClass().getMethods()) {
                if (!"evaluateJavascript".equals(method.getName()) || method.getParameterCount() != 2 || !method.getParameterTypes()[1].isInterface()) continue;
                Class<?> callback = method.getParameterTypes()[1];
                Object receiver = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (proxy, target, args) -> {
                    if ("onReceiveValue".equals(target.getName())) {
                        try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                            if (work != null) { owner(uid, day, generation); if (args != null && args.length == 1 && args[0] instanceof String) result.set((String) args[0]); }
                        } catch (TaskCancelledException cancelled) { result.set(null); }
                        finally { latch.countDown(); }
                    }
                    return null;
                });
                method.invoke(view, RIDE_JS, receiver); return true;
            }
            return false;
        }, uid, day, generation);
        if (!submitted || !latch.await(3, TimeUnit.SECONDS)) return null; owner(uid, day, generation);
        String raw = result.get(); if (raw == null) return null;
        JSONArray value = new JSONArray("[" + raw + "]"); return value.opt(0) instanceof String ? value.optString(0) : null;
    }
}
