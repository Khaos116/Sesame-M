"""Run the production RPC guard/entity with real org.json and isolated clock/account I/O."""
from pathlib import Path
import os
import subprocess
import tempfile
import sys

sys.dont_write_bytecode = True
from audit_regressions.run import method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
JSON = next((CACHE / "org.json/json").glob("*/*/json-*.jar"))
LOMBOK = next((CACHE / "org.projectlombok/lombok").glob("*/*/lombok-*.jar"))

with tempfile.TemporaryDirectory(prefix="sesame-rpc-guard-") as tmp:
    out = Path(tmp)

    def write(name, code):
        path = out / "io/github/aw1y2z/sesame" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(code, encoding="utf-8")

    for name in ("rpc/intervallimit/RpcRequestGuard.java", "rpc/intervallimit/RpcFailurePolicy.java", "entity/RpcEntity.java", "util/RpcLog.java",
                 "util/diagnostics/RpcFailureJournal.java", "util/AtomicConfigFile.java",
                 "util/RunGeneration.java", "util/TaskCancelledException.java",
                 "rpc/bridge/NewRpcBridge.java", "rpc/bridge/OldRpcBridge.java", "rpc/bridge/RpcBridge.java", "rpc/bridge/RpcVersion.java"):
        code = (SOURCE / name).read_text(encoding="utf-8")
        if name == "util/AtomicConfigFile.java":
            # Android rename replaces an existing file; Windows File.renameTo does not.
            code = code.replace("public boolean replace(File temporary, File target) { return temporary.renameTo(target); }",
                                "public boolean replace(File temporary, File target) throws IOException { java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); return true; }")
        write(name, code.replace("System.currentTimeMillis()", "io.github.aw1y2z.sesame.rpc.intervallimit.GuardCheck.now"))
    write("model/task/antMember/AntMemberRpcCall.java", """
package io.github.aw1y2z.sesame.model.task.antMember;
import org.json.*;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.MyUtils;
public class AntMemberRpcCall {
""" + method("model/task/antMember/AntMemberRpcCall.java", "    public static Boolean check()") + "\n}")
    write("util/MessageUtil.java", "package io.github.aw1y2z.sesame.util; import org.json.JSONObject; public class MessageUtil {"
          + 'public static final String CODE_UNSUPPORTED_RPC = "400000040";'
          + method("util/MessageUtil.java", "    public static boolean isUnsupportedRpc(") + "}")
    write("model/base/TaskAlternative.java", "package io.github.aw1y2z.sesame.model.base; import org.json.JSONObject;"
          + "import io.github.aw1y2z.sesame.util.MessageUtil; public class TaskAlternative {"
          + method("model/base/TaskAlternative.java", "    public static boolean hit(") + "}")
    write("data/task/TaskLifecycle.java", (SOURCE / "data/task/TaskLifecycle.java").read_text(encoding="utf-8"))
    hook = (SOURCE / "hook/ApplicationHook.java").read_text(encoding="utf-8")
    end = hook.index("                                    TaskCommon.update();")
    start = hook.rindex("                                    lastExecTime = System.currentTimeMillis();", 0, end)
    # Only shorten the production wait constants; execute the real branches and lifecycle accounting.
    assert "private static final long CHECK_TIMEOUT_MS = 30_000;" in hook
    preflight = hook[start:end].replace("get(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)", "get(50, TimeUnit.MILLISECONDS)")
    preflight = preflight.replace("10000 - System.currentTimeMillis()", "0 - System.currentTimeMillis()")
    write("hook/PreflightCheck.java", """
package io.github.aw1y2z.sesame.hook;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.Log;
public class PreflightCheck {
    static final String TAG = "test";
    static final int checkInterval = 60_000;
    static long lastExecTime;
    static int retries, logins, dispatched;
    static AtomicInteger reLoginCount = new AtomicInteger();
    static void execDelayedHandler(long delay) { assert delay == checkInterval; retries++; }
    static void reLogin() { logins++; }
    static class AntMemberRpcCall {
        static int mode;
        static volatile boolean interrupted;
        static Boolean check() throws Exception {
            if (mode == 1) return false;
            if (mode == 2) throw new IllegalStateException("check failed");
            if (mode == 3) {
                try { Thread.sleep(5000); }
                catch (InterruptedException e) { interrupted = true; throw e; }
            }
            return true;
        }
    }
    static void dispatch() throws Exception {
""" + preflight + """
        dispatched++;
    }
    public static void main(String[] args) throws Exception {
        for (int mode = 0; mode <= 3; mode++) {
            retries = logins = dispatched = 0;
            AntMemberRpcCall.mode = mode;
            dispatch();
            assert logins == 0 : "ordinary check failure must not open/close login Activity";
            assert dispatched == (mode == 0 ? 1 : 0);
            assert retries == (mode == 0 ? 0 : 1);
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (!TaskLifecycle.isIdle() && System.nanoTime() < deadline) Thread.yield();
            assert TaskLifecycle.isIdle() : "timed-out check still holds account lifecycle work";
        }
        assert AntMemberRpcCall.interrupted : "timed-out check was not cancelled";
        retries = logins = dispatched = 0;
        AntMemberRpcCall.mode = 3;
        Thread.currentThread().interrupt();
        dispatch();
        assert Thread.interrupted() : "dispatcher swallowed interruption";
        assert retries == 0 && logins == 0 && dispatched == 0;
        System.out.println("Login preflight: retry without Activity launch, timeout cancellation and interrupt passed");
    }
}
""")
    write("data/RuntimeInfo.java", """
package io.github.aw1y2z.sesame.data;
import java.util.*;
public class RuntimeInfo {
    public enum RuntimeInfoKey { ForestPauseTime }
    public static String account = "A";
    public static Map<String, RuntimeInfo> accounts = new HashMap<>();
    public final Map<String,String> values = new HashMap<>();
    public static RuntimeInfo getInstance() { return accounts.computeIfAbsent(account, x -> new RuntimeInfo()); }
    public String getString(String key) { return values.getOrDefault(key, ""); }
    public void put(String key, Object value) { values.put(key, String.valueOf(value)); }
    public void put(RuntimeInfoKey key, Object value) { put(key.name(), value); }
}
""")
    write("util/FileUtil.java", """
package io.github.aw1y2z.sesame.util;
public class FileUtil {
    public static java.io.File root = new java.io.File(System.getProperty("rpc.report.root"));
    public static java.io.File getCurrentUserLogDirectory() {
        return new java.io.File(root, io.github.aw1y2z.sesame.data.RuntimeInfo.account);
    }
}
""")
    write("util/MyUtils.java", """
package io.github.aw1y2z.sesame.util;
import org.json.JSONObject;
public class MyUtils {
    public static java.util.Calendar getInstance() { return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8")); }
    public static boolean enabled = true;
    public static final java.util.Set<String> unsupported = new java.util.HashSet<>();
    public static boolean isUnsupportedRpcRecorded(Object context, String route) { return context != null && unsupported.contains(route); }
    public static boolean recordUnsupportedRpc(Object context, String route) { if(context == null) return false; unsupported.add(route); return true; }
    public static boolean closeUnRpc() { return enabled; }
    public static boolean closeVerification() { return enabled; }
    public static boolean closeErrorFunction() { return enabled; }
    public static JSONObject newJSONObject(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return new JSONObject(); }
    }
}
""")
    write("util/Log.java", """
package io.github.aw1y2z.sesame.util;
public class Log {
    public static String lastDebug, lastError;
    public static final java.util.List<String> records = new java.util.ArrayList<>();
    public static void record(String s) { records.add(s); }
    public static void i(String... s) { }
    public static void error(String s) { lastError = s; }
    public static void debug(String s) { lastDebug = s; }
    public static void printStackTrace(Throwable t) { }
    public static void printStackTrace(String tag, Throwable t) { }
    public static void err(String tag, String msg, Throwable t) { lastError = tag + ", " + msg; }
}
""")
    write("hook/ApplicationHook.java", """
package io.github.aw1y2z.sesame.hook;
public class ApplicationHook {
    public static Object getContext() { return ApplicationHook.class; }
    public static io.github.aw1y2z.sesame.rpc.bridge.RpcBridge bridge;
    public static io.github.aw1y2z.sesame.entity.RpcEntity requestObject(String m, String d, int c, int i) {
        return bridge.requestObject(m, d, c, i);
    }
    public static boolean offline;
    public static boolean isOffline() { return offline; }
    public static void setOffline(boolean v) { offline = v; }
    public static int loginBroadcasts;
    public static void reLoginByBroadcast() { loginBroadcasts++; }
    public static int verificationLaunches;
    public static void showVerification() { verificationLaunches++; }
    public static ClassLoader getClassLoader() { return ApplicationHook.class.getClassLoader(); }
}
""")
    write("hook/CaptchaTriggerStats.java", """
package io.github.aw1y2z.sesame.hook;
public class CaptchaTriggerStats {
    public static int riskRecords;
    public static String lastRiskMethod;
    public static void recordRisk(String method, String message) { riskRecords++; lastRiskMethod = method; }
}
""")
    write("hook/PuzzleCaptchaSolver.java", """
package io.github.aw1y2z.sesame.hook;
public class PuzzleCaptchaSolver {
    public static int arms;
    public static String lastSource;
    public static void arm(String source) { arms++; lastSource = source; }
}
""")
    write("model/normal/base/BaseModel.java", """
package io.github.aw1y2z.sesame.model.normal.base;
public class BaseModel {
    public record Value<T>(T getValue) { }
    public static boolean timeoutRestart;
    public static Value<Boolean> getTimeoutRestart() { return new Value<>(timeoutRestart); }
    public static Value<Long> getWaitWhenException() { return new Value<>(0L); }
}
""")
    write("rpc/intervallimit/RpcIntervalLimit.java", """
package io.github.aw1y2z.sesame.rpc.intervallimit;
public class RpcIntervalLimit {
    public static Runnable onEnter;
    public static void enterIntervalLimit(String method) {
        if (onEnter != null) onEnter.run();
    }
}
""")
    write("util/XHelpers.java", """
package io.github.aw1y2z.sesame.util;
import java.lang.reflect.*;
public class XHelpers {
    public static Class<?> findClass(String n, ClassLoader l) throws Exception { return l.loadClass(n); }
    public static Object callStaticMethod(Class<?> c, String n, Object... args) throws Exception { return null; }
    public static Object callMethod(Object obj, String name, Object... args) throws Exception {
        for (Method m : obj.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(obj, args);
        }
        throw new NoSuchMethodException(name);
    }
}
""")
    for name, body in {
        "ClassUtil": 'public static final String JSON_OBJECT_NAME = "org.json.JSONObject", H5PAGE_NAME = "Object";',
        "NotificationUtil": 'public static void updateStatusText(String s) { }',
        "RandomUtil": 'public static int delay() { return 0; }',
        "TimeUtil": 'public static String getCommonDate(long t) { return ""; }',
        "StringUtil": 'public static boolean isEmpty(String s) { return s == null || s.isEmpty(); }',
    }.items():
        write(f"util/{name}.java", f"package io.github.aw1y2z.sesame.util; public class {name} {{ {body} }}")
    (out / "SystemClock.java").write_text("package android.os; public class SystemClock { public static long elapsedRealtime() { return System.nanoTime()/1000000; } }", encoding="utf-8")
    write("rpc/intervallimit/GuardCheck.java", r'''
package io.github.aw1y2z.sesame.rpc.intervallimit;
import org.json.*;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.RpcLog;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.rpc.bridge.*;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.antMember.AntMemberRpcCall;

public class GuardCheck {
    public static long now = 1_800_000_000_000L;
    static final long MIN = 60_000, DAY = 1440 * MIN;
    static JSONObject json(String s) { return new JSONObject(s); }
    static RpcRequestGuard guard(String method, String args) { return new RpcRequestGuard(new RpcEntity(method, args)); }
    static RpcRequestGuard guard(String method) { return guard(method, "[{}]"); }
    static void reset() { RuntimeInfo.accounts.clear(); RuntimeInfo.account = "A"; MyUtils.enabled = true; MyUtils.unsupported.clear(); }
    public interface Callback { void sendJSONResponse(Reply reply); }
    public static class Reply {
        public String toJSONString() { return payload; }
        public String getString(String key) { return json(payload).optString(key); }
    }
    static String payload;
    static int calls;
    static boolean throwDenied;
    static boolean expireOnCall;
    static final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    public static Object parse(String s) { return json(s); }
    public static void rpc(Object a,Object b,Object c,Object d,Object e,Object f,Object g,Object h,
                           Object i,Object j,Object k,Object l,Object m,Object n,Object o,Object p) {
        calls++;
        if (expireOnCall) { generation.incrementAndGet(); throw new IllegalStateException("generation expired"); }
        if (throwDenied) throw new IllegalStateException("[1009]访问被拒绝");
        ((Callback)p).sendJSONResponse(new Reply());
    }
    public static Object oldRpc(Object a,Object b,Object c,Object d,Object e,Object f,Object g,Object h,
                                Object i,Object j,Object k,Object l) { calls++; return new GuardCheck(); }
    public String getResponse() { return payload; }
    static void field(Object obj, String key, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(key); field.setAccessible(true); field.set(obj, value);
    }
    static void bridges() throws Exception {
        Class<?>[] types = new Class<?>[16]; java.util.Arrays.fill(types, Object.class);
        NewRpcBridge bridge = new NewRpcBridge();
        field(bridge, "loader", GuardCheck.class.getClassLoader());
        field(bridge, "bridgeCallbackClazzArray", new Class<?>[]{Callback.class});
        field(bridge, "parseObjectMethod", GuardCheck.class.getMethod("parse", String.class));
        field(bridge, "newRpcCallMethod", GuardCheck.class.getMethod("rpc", types));
        for (boolean async : new boolean[]{false, true}) {
            reset(); calls = 0; payload = "{\"success\":true,\"resultCode\":\"MGW200\"}";
            RpcEntity promplay = new RpcEntity("alipay.asset.promplaymatrix.play.trigger", "[{}]");
            if (async) bridge.newAsyncRequest(promplay, 3, 0); else bridge.requestObject(promplay, 3, 0);
            assert calls == 1 && !promplay.getHasError() && !guard(promplay.getRequestMethod()).shouldSkip() : "new bridge rejected MGW200";
            reset(); calls = 0; payload = "{\"success\":true}";
            Thread.currentThread().interrupt();
            try {
                if (async) bridge.newAsyncRequest(new RpcEntity("cancelled.entry", "[{}]"), 3, 0);
                else bridge.requestObject(new RpcEntity("cancelled.entry", "[{}]"), 3, 0);
                assert false : "interrupted child without generation token still sent RPC";
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
            finally { assert Thread.interrupted() : "cancellation cleared interrupt"; }
            var previous = io.github.aw1y2z.sesame.util.RunGeneration.bind(0, generation::get);
            generation.set(1);
            calls = 0;
            try {
                if (async) bridge.newAsyncRequest(new RpcEntity("generation.entry", "[{}]"), 3, 0);
                else bridge.requestObject(new RpcEntity("generation.entry", "[{}]"), 3, 0);
                assert false : "stale task must cancel before RPC";
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
            generation.set(0);
            io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit.onEnter = generation::incrementAndGet;
            try {
                if (async) bridge.newAsyncRequest(new RpcEntity("generation.interval", "[{}]"), 3, 0);
                else bridge.requestObject(new RpcEntity("generation.interval", "[{}]"), 3, 0);
                assert false : "task cancelled during interval wait must not send RPC";
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
            io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit.onEnter = null;
            generation.set(0);
            expireOnCall = true;
            try {
                if (async) bridge.newAsyncRequest(new RpcEntity("generation.retry", "[{}]"), 3, 0);
                else bridge.requestObject(new RpcEntity("generation.retry", "[{}]"), 3, 0);
                assert false : "task cancelled during retry must stop";
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 1; }
            expireOnCall = false;
            io.github.aw1y2z.sesame.util.RunGeneration.restore(previous);
            reset(); calls = 0;
            RpcEntity e = new RpcEntity("com.alipay.antfarm.feedAnimal", "[{}]");
            payload = "{\"error\":48}";
            if (async) bridge.newAsyncRequest(e, 3, 0); else bridge.requestObject(e, 3, 0);
            assert calls == 1 && e.getHasError();
            if (async) bridge.newAsyncRequest(e, 3, 0); else bridge.requestObject(e, 3, 0);
            assert calls == 1 && json(e.getResponseString()).getString("error").equals("RPC_SKIPPED");
            now += MIN;
            payload = "{\"success\":true}";
            long start = System.nanoTime();
            if (async) bridge.newAsyncRequest(e, 3, 0); else bridge.requestObject(e, 3, 0);
            assert calls == 2 && !e.getHasError();
            assert System.nanoTime() - start < 1_000_000_000L; // inline callback must not wait 30 seconds
            payload = "{\"retCode\":\"0\",\"errorMsg\":\"ok\",\"adList\":[{\"html\":\"" + "secret".repeat(50_000) + "\"}]}";
            RpcEntity ad = new RpcEntity("com.alipay.adexchange.ad.facade.xlightPlugin", "[{\"session\":\"secret\"}]");
            if (async) bridge.newAsyncRequest(ad, 3, 0); else bridge.requestObject(ad, 3, 0);
            assert !ad.getHasError();
            assert ad.getResponseString().equals(payload); // task still gets full response
            assert Log.lastDebug.length() < 1000 && !Log.lastDebug.contains("secret");
            assert json(RpcLog.responseData(ad)).getInt("adCount") == 1;
            assert json(RpcLog.responseData(ad)).getString("retCode").equals("0");
            payload = "{\"error\":1009,\"errorMessage\":\"访问被拒绝\",\"extInfo\":\"" + "secret".repeat(50_000) + "\"}";
            if (async) bridge.newAsyncRequest(ad, 3, 0); else bridge.requestObject(ad, 3, 0);
            assert Log.lastError.length() < 1000 && !Log.lastError.contains("secret");
            assert Log.lastError.contains("1009") && Log.lastError.contains("访问被拒绝");
            reset();
            payload = "{\"success\":false,\"resultCode\":\"302\",\"memo\":\"非好友\"}";
            RpcEntity nonFriend = new RpcEntity("com.alipay.antfarm.enterFarm", "[{\"userId\":\"gone\"}]");
            Log.lastError = null;
            if (async) bridge.newAsyncRequest(nonFriend, 3, 0); else bridge.requestObject(nonFriend, 3, 0);
            assert Log.lastError == null && nonFriend.getHasError();
            assert !guard("com.alipay.antfarm.enterFarm").shouldSkip();
            assert !RpcRequestGuard.isNonFriend("com.alipay.antfarm.enterFarm", json("{\"error\":48,\"memo\":\"非好友\"}"));
            RpcEntity normal = new RpcEntity("com.alipay.antfarm.feedAnimal", "[{}]");
            normal.setResponseObject(json(payload), payload);
            assert RpcLog.responseData(normal).equals(payload);
            assert RpcLog.requestData(normal).equals("[{}]");
            reset(); calls = 0; throwDenied = true;
            RpcEntity denied = new RpcEntity("other.denied", "[{}]");
            if (async) bridge.newAsyncRequest(denied, 3, 0); else bridge.requestObject(denied, 3, 0);
            assert calls == 1 && denied.getHasError();
            now += 30 * MIN - 1;
            assert guard("other.denied").shouldSkip();
            now++;
            assert !guard("other.denied").shouldSkip();
            throwDenied = false;
        }
        reset(); calls = 0;
        OldRpcBridge old = new OldRpcBridge();
        field(old, "rpcCallMethod", GuardCheck.class.getMethod("oldRpc", java.util.Arrays.copyOf(types, 12)));
        field(old, "getResponseMethod", GuardCheck.class.getMethod("getResponse"));
        payload = "{\"success\":true,\"resultCode\":\"MGW200\"}";
        RpcEntity promplay = new RpcEntity("alipay.asset.promplaymatrix.play.prize.receive", "[{}]");
        old.requestObject(promplay, 3, 0);
        assert calls == 1 && !promplay.getHasError() && !guard(promplay.getRequestMethod()).shouldSkip() : "old bridge rejected MGW200";
        reset(); calls = 0;
        Thread.currentThread().interrupt();
        try { old.requestObject(new RpcEntity("cancelled.old.entry", "[{}]"), 3, 0); assert false : "interrupted child still sent old RPC"; }
        catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
        finally { assert Thread.interrupted(); }
        var previous = io.github.aw1y2z.sesame.util.RunGeneration.bind(0, generation::get);
        generation.set(1);
        calls = 0;
        try { old.requestObject(new RpcEntity("generation.old.entry", "[{}]"), 3, 0); assert false; }
        catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
        generation.set(0);
        io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit.onEnter = generation::incrementAndGet;
        try { old.requestObject(new RpcEntity("generation.old.interval", "[{}]"), 3, 0); assert false; }
        catch (io.github.aw1y2z.sesame.util.TaskCancelledException expected) { assert calls == 0; }
        io.github.aw1y2z.sesame.rpc.intervallimit.RpcIntervalLimit.onEnter = null;
        io.github.aw1y2z.sesame.util.RunGeneration.restore(previous);
        RpcEntity e = new RpcEntity("other.request", "[{}]");
        payload = "{\"error\":48}";
        old.requestObject(e, 3, 0);
        old.requestObject(e, 3, 0);
        assert calls == 1 && e.getHasError();
        now += 5*MIN;
        payload = "{\"retCode\":\"0\"}";
        old.requestObject(e, 3, 0);
        assert calls == 2 && !e.getHasError();
        payload = "{\"success\":false,\"resultCode\":\"302\",\"memo\":\"非好友\"}";
        Log.lastError = null;
        old.requestObject(new RpcEntity("com.alipay.antfarm.enterFarm", "[{}]"), 3, 0);
        assert Log.lastError == null;
        for (RpcBridge transport : new RpcBridge[]{bridge, old}) {
            ApplicationHook.bridge = transport;
            reset(); calls = 0;
            payload = "{\"success\":false,\"resultCode\":\"NOT_CERTIFIED\",\"memo\":\"请先实名认证\"}";
            for (int n = 0; n < 4; n++) {
                assert AntMemberRpcCall.check() : "member business denial/cooldown must not mean offline";
            }
            // “请先实名认证”属于没开通/未认证：一天只请求一次（原为 5/5/30 分钟升级退避共 3 次）
            assert calls == 1 : "member cooldown must remain effective";
            ApplicationHook.offline = true;
            assert !AntMemberRpcCall.check() : "offline account must not pass";
            ApplicationHook.offline = false;
            for (String response : new String[]{"{\"success\":true}", "{\"isSuccess\":false}",
                    "{\"error\":0,\"success\":false,\"resultCode\":\"DENIED\"}"}) {
                reset(); payload = response;
                assert AntMemberRpcCall.check() : response;
            }
            for (String response : new String[]{"{\"error\":1009}", "{\"error\":48}",
                    "{\"error\":2000}", "{}", "not-json"}) {
                reset(); payload = response;
                assert !AntMemberRpcCall.check() : response;
                ApplicationHook.offline = false;
            }
        }
        ApplicationHook.bridge = bridge;
        for (boolean restart : new boolean[]{false, true}) {
            reset(); payload = "{\"error\":2000}";
            io.github.aw1y2z.sesame.model.normal.base.BaseModel.timeoutRestart = restart;
            ApplicationHook.loginBroadcasts = 0;
            assert !AntMemberRpcCall.check();
            assert ApplicationHook.offline : "real login expiry must still mark offline";
            assert ApplicationHook.loginBroadcasts == (restart ? 1 : 0) : "timeout restart preference ignored";
            ApplicationHook.offline = false;
        }
    }
    static void pauses(String method, String args, long... durations) {
        reset();
        for (long duration : durations) {
            RpcRequestGuard g = guard(method, args);
            assert !g.shouldSkip();
            g.record(json("{\"error\":48,\"errorMessage\":\"当前网络不可用，请稍后重试\"}"));
            assert guard(method, args).shouldSkip(); // survives a new request/guard
            now += duration - 1;
            assert guard(method, args).shouldSkip();
            now++;
            assert !guard(method, args).shouldSkip();
        }
    }
    static void dailyReportRules() {
        reset();
        for(String field : new String[]{"appletId", "taskConfigId", "bizId", "playId"}) {
            String a = "[{\""+field+"\":\"TASK_A\",\"stageCode\":\"send\"}]";
            guard("sdk.task.trigger",a).record(json("{\"code\":\"400000040\"}"));
            RuntimeInfo.account="B";
            assert guard("sdk.task.trigger",a).shouldSkip();
            assert !guard("sdk.task.trigger",a.replace("TASK_A","TASK_B")).shouldSkip() : field;
            assert !guard("sdk.task.trigger",a.replace("send","signup")).shouldSkip() : "stage collision";
            reset();
        }
        for(String method : new String[]{"sdk.task.query","sdk.task.consult","sdk.task.getTask","sdk.task.listTasks","sdk.task.trigger"}) {
            String args=method.endsWith("trigger")?"[{\"sceneCode\":\"ONLY_SCENE\"}]":"[{\"appletId\":\"A\"}]";
            guard(method,args).record(json("{\"code\":\"400000040\"}"));
            assert MyUtils.unsupported.isEmpty() : "query or method-wide rule learned";
            assert !guard(method,args).shouldSkip();
        }
        String rebate="[{\"playId\":\"PLAY_A\",\"behavior\":\"trigger\"}]";
        guard("sdk.gameplay.rebate",rebate).record(json("{\"code\":\"400000040\"}"));
        assert guard("sdk.gameplay.rebate",rebate).shouldSkip();
        assert !guard("sdk.gameplay.rebate",rebate.replace("trigger","consult")).shouldSkip();
        guard("sdk.task.trigger","[{\"appletId\":\"query-only\",\"stageCode\":\"query\"}]").record(json("{\"code\":\"400000040\"}"));
        assert MyUtils.unsupported.size()==1 : "query action learned";
        String learnedRoute=MyUtils.unsupported.iterator().next();
        MyUtils.unsupported.clear();MyUtils.unsupported.add(learnedRoute.substring(3));
        assert !guard("sdk.gameplay.rebate",rebate).shouldSkip() : "legacy wide rule retained";
        MyUtils.unsupported.clear();MyUtils.unsupported.add(learnedRoute);
        RuntimeInfo.accounts.clear();Log.records.clear();
        assert guard("sdk.gameplay.rebate",rebate).shouldSkip();
        assert guard("sdk.gameplay.rebate",rebate).shouldSkip();
        assert Log.records.size()==1 && Log.records.get(0).contains("PLAY_A") : "daily skip route diagnostic missing/repeated";
        reset();
        String learnedMethod = "new.task.finish";
        String learnedArgs = "[{\"sceneCode\":\"NEW_SCENE\",\"taskType\":\"NEW_TASK\",\"action\":\"finish\",\"outBizNo\":\"1\"}]";
        RpcRequestGuard inFlight = guard(learnedMethod, learnedArgs);
        assert !inFlight.shouldSkip();
        guard(learnedMethod, learnedArgs).record(json("{\"code\":\"400000040\"}"));
        RuntimeInfo.accounts.clear(); RuntimeInfo.account = "B";
        assert inFlight.shouldSkip() : "already-created guard missed newly learned rule";
        assert guard(learnedMethod, learnedArgs.replace("1", "2")).shouldSkip();
        assert !guard(learnedMethod, learnedArgs.replace("NEW_TASK", "OTHER_TASK")).shouldSkip();
        assert !guard(learnedMethod, learnedArgs.replace("NEW_SCENE", "OTHER_SCENE")).shouldSkip();
        assert !guard(learnedMethod, learnedArgs.replace("finish", "award")).shouldSkip();
        assert !guard("new.task.award", learnedArgs).shouldSkip();
        MyUtils.enabled = false;
        assert !guard(learnedMethod, learnedArgs).shouldSkip();
        for (String failure : new String[]{"{\"success\":false,\"errorMessage\":\"不支持rpc调用\"}", "{\"resultCode\":\"400000040\"}"}) {
            reset(); guard(learnedMethod, learnedArgs).record(json(failure));
            assert guard(learnedMethod, learnedArgs).shouldSkip();
        }
        for (String failure : new String[]{"{\"error\":\"RPC_SKIPPED\",\"code\":\"400000040\"}", "{\"success\":true,\"code\":\"400000040\"}", "{\"error\":\"TRANSPORT_ERROR\",\"errorMessage\":\"不支持rpc调用\"}", "{\"success\":false,\"errorMessage\":\"系统繁忙\"}"}) {
            reset(); guard(learnedMethod, learnedArgs).record(json(failure));
            assert MyUtils.unsupported.isEmpty() : "transient/local/success response learned as unsupported";
        }
        String method = "com.alipay.antiep.finishTask";
        String[][] routes = {
            {"ANTFOREST_VITALITY_TASK", "LSHS_huisho20_202508"},
            {"ANTSTALL_TASK", "ANTSTALL_TASK_XCXYX_zhuzhaishijie"},
            {"ANTSTALL_TASK", "ANTSTALL_TASK_XCXYX_zslxx"},
            {"ANTFARM_DAILY_DRAW_TASK", "cclyx_wdhysj_3c_10"},
            {"ANTFARM_DAILY_DRAW_TASK", "cclyx_sgbhsd_3c_zm10c"},
            {"ANTFARM_DAILY_DRAW_TASK", "cclyx_3bei_zslxx_2"},
            {"ANTAIFISH", "LHS_QDRW_AIFISH"},
            {"ANTSTALL_TASK", "ANTSTALL_XCXYX_mhxcz"},
            {"ANTFARM_DAILY_DRAW_TASK", "cclyx_3bei_xjcmx_2"},
            {"ANTFARM_IP_DRAW_TASK", "ipccl_sgbhsd_zm3c"},
            {"ANTFARM_IP_DRAW_TASK", "ipccl_wdhysj_10"},
            {"ANTFARM_ORCHARD_TASK_V2", "ANTFARM_ORCHARD_NORMAL_CAINIAO_DUAN"},
            {"ANTFARM_ORCHARD_TASK_V2", "ANTFARM_ORCHARD_P2P_SHARER"},
            {"ANTFARM_ORCHARD_TASK_V2", "goldenbean_receive3000bean"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NCLY_ZH_CNXDY"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NCLY_ZH_NLGJ"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NCLY_ZH_SJHH"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NCLY_ZH_XDNSR"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NORMAL_SHANGOUMIANDAN"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_NORMAL_TAOBAOZHIBO_NEW"},
            {"ANTFARM_ORCHARD_TASK_V2", "ORCHARD_TEAM_SPREAD_PERSON_2"}
        };
        for (String[] route : routes) {
            reset();
            String args = new JSONArray().put(new JSONObject().put("sceneCode", route[0])
                    .put("taskType", route[1])).toString();
            RpcEntity request = new RpcEntity(method, args);
            assert new RpcRequestGuard(request).shouldSkip() : "unsupported primary route still sent: " + route[1];
            RuntimeInfo.account = "B";
            assert guard(method, args).shouldSkip() : "another account repeated an unsupported route";
            RuntimeInfo.account = "A";
            JSONObject response = json(request.getResponseString());
            assert "RPC_SKIPPED".equals(response.optString("error"));
            assert response.optString("resultDesc").contains(method) : "skip reason hides the blocked interface";
            assert io.github.aw1y2z.sesame.model.base.TaskAlternative.hit(response, route[0])
                    : "skipping primary must preserve alternative task completion";
            assert !guard(method, args.replace(route[0], "OTHER_SCENE")).shouldSkip();
            assert !guard(method, args.replace(route[1], route[1] + "_other")).shouldSkip();
            assert !guard("other.finishTask", args).shouldSkip();
            assert !guard("com.alipay.antiep.receiveTaskAward", args).shouldSkip();
            assert !guard("com.alipay.antfarm.doFarmTask", new JSONArray().put(new JSONObject()
                    .put("sceneCode", "ANTFARM").put("taskSceneCode", route[0]).put("bizKey", route[1])).toString()).shouldSkip();
            java.io.File folder = new java.io.File(io.github.aw1y2z.sesame.util.FileUtil.root, route[1]);
            try { io.github.aw1y2z.sesame.util.diagnostics.RpcFailureJournal.record(folder, method, args, response, now); }
            catch (Exception e) { throw new AssertionError(e); }
            assert !folder.exists() || folder.list().length == 0 : "local route skip counted as a failure";
            MyUtils.enabled = false;
            assert !guard(method, args).shouldSkip() : "unsupported-RPC preference must remain configurable";
        }
        for (String task : new String[]{"GOLDENBEAN_GAME_ZH_BWXRK", "GOLDENBEAN_GAME_ZH0_NCJYG"}) {
            reset();
            String golden = "com.alipay.antieptask.finishTaskantorchard";
            String args = new JSONArray().put(new JSONObject().put("sceneCode", "GOLDEN_BEAN_MASTER_TASK").put("taskType", task)).toString();
            RpcEntity request = new RpcEntity(golden, args);
            assert new RpcRequestGuard(request).shouldSkip();
            assert "400000040".equals(json(request.getResponseString()).optString("code"));
            RuntimeInfo.account = "B";
            assert guard(golden, args).shouldSkip();
            assert !guard(golden, args.replace(task, "OTHER_TASK")).shouldSkip();
            assert !guard(golden, args.replace("GOLDEN_BEAN_MASTER_TASK", "OTHER_SCENE")).shouldSkip();
            assert !guard(method, args).shouldSkip() : "different finish method was blocked";
            MyUtils.enabled = false;
            assert !guard(golden, args).shouldSkip();
        }
        for (String[] route : java.util.Arrays.copyOf(routes, 3)) {
            reset();
            String args = new JSONArray().put(new JSONObject().put("sceneCode", "ANTFARM")
                    .put("taskSceneCode", route[0]).put("bizKey", route[1]).put("outBizNo", "1")).toString();
            String fallback = "com.alipay.antfarm.doFarmTask";
            JSONObject busy = json("{\"success\":false,\"resultCode\":\"102\",\"memo\":\"服务器正在开小差，请稍后再试～\"}");
            for (long duration : new long[]{5*MIN, 5*MIN, 30*MIN}) {
                assert !guard(fallback, args).shouldSkip();
                guard(fallback, args).record(busy);
                assert guard(fallback, args.replace("\"1\"", "\"2\"")).shouldSkip() : "busy fallback escaped cooldown";
                assert !guard(fallback, args.replace(route[1], "OTHER_TASK")).shouldSkip();
                assert !guard(fallback, args.replace(route[0], "OTHER_SCENE")).shouldSkip();
                RuntimeInfo.account = "B"; assert !guard(fallback, args).shouldSkip(); RuntimeInfo.account = "A";
                now += duration - 1; assert guard(fallback, args).shouldSkip();
                now++; assert !guard(fallback, args).shouldSkip();
            }
        }
        reset();
        String unaffected = "[{\"sceneCode\":\"ANTFARM\",\"taskSceneCode\":\"ANTORCHARD_TASK\",\"bizKey\":\"OTHER_TASK\"}]";
        guard("com.alipay.antfarm.doFarmTask", unaffected).record(json("{\"success\":false,\"resultCode\":\"102\",\"memo\":\"服务器正在开小差，请稍后再试～\"}"));
        assert !guard("com.alipay.antfarm.doFarmTask", unaffected).shouldSkip() : "C158 rules must not change unrelated fallback tasks";
        reset();
        System.out.println("PASS C158 exact primary routes, preserved fallback/awards, local journal exclusion and task/account busy cooldowns");
    }
    public static void main(String[] ignored) throws Exception {
        JSONObject mgw = json("{\"success\":true,\"resultCode\":\"MGW200\",\"resultMsg\":\"成功\",\"triggeredNodeCount\":1}");
        assert !RpcRequestGuard.isFailure(mgw) : "SJ promplay success was classified as failure";
        reset();
        for (int i=0;i<4;i++) guard("alipay.asset.promplaymatrix.play.trigger").record(mgw);
        assert !guard("alipay.asset.promplaymatrix.play.trigger").shouldSkip() : "MGW200 success created a cooldown";
        for (String field : new String[]{"success", "isSuccess", "error", "errorCode", "retCode"}) {
            JSONObject denied = new JSONObject(mgw.toString()).put(field, field.endsWith("Success") || field.equals("success") ? false : "1009");
            assert RpcRequestGuard.isFailure(denied) : "MGW200 masked explicit failure: " + field;
        }
        for (String response : new String[]{"{\"success\":true,\"isSuccess\":false}",
                "{\"success\":true,\"resultCode\":\"FAIL\"}",
                "{\"isSuccess\":true,\"retCode\":\"1\"}",
                "{\"retCode\":\"0\",\"resultCode\":\"DENIED\"}"}) {
            assert RpcRequestGuard.isFailure(json(response)) : "explicit failure masked by success: " + response;
        }
        for (String response : new String[]{"{\"success\":true}", "{\"isSuccess\":true}", "{\"resultCode\":\"MGW200\"}",
                "{\"success\":true,\"isSuccess\":true,\"retCode\":\"0\",\"resultCode\":\"SUCCESS\"}",
                "{\"retCode\":\"0\"}", "{\"resultCode\":\"100\"}", "{\"resultCode\":\"200\"}"}) {
            assert !RpcRequestGuard.isFailure(json(response)) : response;
        }
        for (String field : new String[]{"errorCode", "retCode"}) {
            reset();
            JSONObject denied = json("{\"success\":true}").put(field, "1009");
            assert RpcRequestGuard.isFailure(denied) : "risk denial masked by success: " + field;
            String method = "com.alipay.consumecc.ark.promotion.gate.lottery-machine.camp.query";
            guard(method).record(denied);
            assert guard(method).shouldSkip() : "risk field not handled by shared guard: " + field;
            assert !guard("unrelated.activity.query").shouldSkip() : "denial blocked unrelated activity";
            now += 30 * MIN;
            assert !guard(method).shouldSkip() : "first refusal gained an extra day pause";
        }
        dailyReportRules();
        bridges();
        // Device report: only farm reward 102 + busy message gets task-local transient backoff.
        String awardMethod = "com.alipay.antfarm.receiveFarmTaskAward";
        for (String taskId : new String[]{"cclyx_3bei_xjcmx_2", "cclyx_sgbhsd_1c_zm3c", "IP_chouchoule_juankuan",
                "cclyx_3bei_dgls_2", "cclyx_wdhysj_1cV2"}) {
            reset();
            String scene = taskId.startsWith("IP_") ? "ANTFARM_IP_DRAW_TASK" : "ANTFARM_DAILY_DRAW_TASK";
            String args = new JSONArray().put(new JSONObject().put("sceneCode", "ANTFARM")
                    .put("taskSceneCode", scene).put("taskId", taskId)).toString();
            JSONObject busy = new JSONObject().put("success", false)
                    .put("resultCode", "102").put("memo", "服务器正在开小差，请稍后再试～");
            for (long duration : new long[]{5*MIN, 5*MIN, 30*MIN}) {
                assert !guard(awardMethod, args).shouldSkip();
                guard(awardMethod, args).record(busy);
                assert guard(awardMethod, args).shouldSkip() : "farm busy failure did not pause: " + taskId;
                assert !guard(awardMethod, args.replace(taskId, "another-task")).shouldSkip();
                assert !guard(awardMethod, args.replace(scene, "another-scene")).shouldSkip();
                RuntimeInfo.account = "B";
                assert !guard(awardMethod, args).shouldSkip();
                RuntimeInfo.account = "A";
                now += duration - 1;
                assert guard(awardMethod, args).shouldSkip();
                now++;
                assert !guard(awardMethod, args).shouldSkip();
            }
        }
        reset();
        {
            String args = "[{\"sceneCode\":\"ANTFARM\",\"taskSceneCode\":\"ANTFARM_DAILY_DRAW_TASK\",\"taskId\":\"t\"}]";
            JSONObject busy = new JSONObject().put("success", false).put("resultCode", "102").put("memo", "服务器正在开小差，请稍后再试～");
            for (long duration : new long[]{5*MIN, 5*MIN, 30*MIN, 30*MIN, 6*60*MIN}) {
                assert !guard(awardMethod, args).shouldSkip();
                guard(awardMethod, args).record(busy);
                now += duration - 1;
                assert guard(awardMethod, args).shouldSkip() : "award busy pause too short: " + duration;
                now++;
                assert !guard(awardMethod, args).shouldSkip();
            }
        }
        reset();
        guard("com.alipay.antfarm.feedAnimal").record(json("{\"success\":false,\"resultCode\":102,\"memo\":\"服务器正在开小差，请稍后再试～\"}"));
        assert !guard("com.alipay.antfarm.feedAnimal").shouldSkip();
        for (String response : new String[]{"{\"success\":false,\"resultCode\":102,\"memo\":\"other business reason\"}",
                "{\"success\":false,\"resultCode\":331,\"memo\":\"饲料槽已满\"}"}) {
            guard(awardMethod).record(json(response));
            assert !guard(awardMethod).shouldSkip();
        }
        for (String method : new String[]{"other.fallback", "alipay.antforest.forest.h5.queryHomePage"}) {
            for (String code : new String[]{"1009", "SYSTEM_ERROR", "3000", "48", "2000", "RPC_SKIPPED"}) {
                for (Object error : new Object[]{"", JSONObject.NULL, 0}) {
                    reset();
                    JSONObject missing = new JSONObject().put("success", false).put("resultCode", code);
                    guard(method).record(missing);
                    var expected = new java.util.HashMap<>(RuntimeInfo.getInstance().values);
                    reset();
                    guard(method).record(new JSONObject(missing.toString()).put("error", error));
                    assert RuntimeInfo.getInstance().values.equals(expected)
                            : "empty/null error must use resultCode: " + method + " / " + code;
                }
            }
        }
        reset();
        guard("other.precedence").record(json("{\"success\":false,\"error\":\"1009\",\"resultCode\":\"SYSTEM_ERROR\"}"));
        now += 30 * MIN - 1;
        assert guard("other.precedence").shouldSkip() : "non-empty error must keep precedence";
        now++;
        assert !guard("other.precedence").shouldSkip();
        String farm = "com.alipay.antfarm.feedAnimal";
        String forest = "alipay.antmember.forest.h5.collectEnergy";
        String other = "com.alipay.antiep.receiveTaskAward";
        pauses(farm, "[{}]", MIN, MIN, 5*MIN);
        pauses(forest, "[{}]", MIN, MIN, 5*MIN);
        reset();
        {
            int before = io.github.aw1y2z.sesame.hook.ApplicationHook.verificationLaunches;
            guard("alipay.antforest.forest.h5.startEnergyRain").record(json("{\"error\":\"1009\",\"errorMessage\":\"为保障您的正常访问，请进行验证后继续。\"}"));
            assert io.github.aw1y2z.sesame.hook.ApplicationHook.verificationLaunches == before + 1 : "1009 must bring Alipay to front";
            assert io.github.aw1y2z.sesame.hook.CaptchaTriggerStats.riskRecords == 1 : "verification demand must be recorded once";
            assert "alipay.antforest.forest.h5.startEnergyRain".equals(io.github.aw1y2z.sesame.hook.CaptchaTriggerStats.lastRiskMethod) : "record must name the triggering method";
            assert io.github.aw1y2z.sesame.hook.PuzzleCaptchaSolver.arms == 1
                    && io.github.aw1y2z.sesame.hook.PuzzleCaptchaSolver.lastSource.contains("alipay.antforest.forest.h5.startEnergyRain") : "verification demand must arm the puzzle solver once";
            guard("alipay.antforest.forest.h5.startEnergyRain").record(json("{\"error\":\"1009\"}")); // already paused: no second launch
            assert io.github.aw1y2z.sesame.hook.ApplicationHook.verificationLaunches == before + 1;
            guard("com.alipay.antfarm.feedAnimal").record(json("{\"error\":48}"));
            assert io.github.aw1y2z.sesame.hook.ApplicationHook.verificationLaunches == before + 1 : "network errors must not launch";
            guard("com.alipay.neverland.biz.rpc.queryItemList").record(json("{\"error\":\"1009\",\"errorMessage\":\"系统繁忙，请稍后再试。\"}"));
            assert io.github.aw1y2z.sesame.hook.ApplicationHook.verificationLaunches == before + 1 : "1009 busy must not launch";
            assert io.github.aw1y2z.sesame.hook.CaptchaTriggerStats.riskRecords == 1 : "network/busy must not record";
            assert io.github.aw1y2z.sesame.hook.PuzzleCaptchaSolver.arms == 1 : "network/busy must not arm the solver";
        }
        reset();
        {
            // “请验证后继续”只在内存暂停 5 分钟：不写持久化状态，重启支付宝/切号（代数变化）后立即可再请求
            String risk = "com.alipay.antfarm.cook";
            var before = new java.util.HashMap<>(RuntimeInfo.getInstance().values);
            guard(risk).record(json("{\"error\":1009,\"errorMessage\":\"为了保障您的操作安全，请进行验证后继续。\"}"));
            assert RuntimeInfo.getInstance().values.equals(before) : "verification pause must not be persisted";
            now += 5 * MIN - 1;
            assert guard(risk).shouldSkip() : "verification pause lasts 5 minutes";
            now++;
            assert !guard(risk).shouldSkip() : "verification pause must end after 5 minutes";
            guard(risk).record(json("{\"error\":1009,\"errorMessage\":\"为了保障您的操作安全，请进行验证后继续。\"}"));
            assert guard(risk).shouldSkip();
            var freeze = io.github.aw1y2z.sesame.data.task.TaskLifecycle.freezeIfIdle();
            assert freeze != null;
            assert !guard(risk).shouldSkip() : "account switch must lift the verification pause";
            io.github.aw1y2z.sesame.data.task.TaskLifecycle.thaw(freeze);
            assert !guard(risk).shouldSkip();
            guard(risk).record(json("{\"error\":1009,\"errorMessage\":\"为了保障您的操作安全，请进行验证后继续。\"}"));
            assert guard(risk).shouldSkip();
            RpcRequestGuard.clearVerifyPause();
            assert !guard(risk).shouldSkip() : "successful slide must lift the verification pause";
            // 1009 系统繁忙：按临时繁忙短退避，不当风控
            String neverland = "com.alipay.neverland.biz.rpc.queryItemList2";
            guard(neverland).record(json("{\"error\":\"1009\",\"errorMessage\":\"系统繁忙，请稍后再试。\"}"));
            now += 5 * MIN - 1;
            assert guard(neverland).shouldSkip();
            now++;
            assert !guard(neverland).shouldSkip();
            // 人气大爆发（文案在 resultView，无 errorMessage）：非核心接口反复出现也只短退避，不停一天
            String promo = "com.alipay.loanpromoweb.promo.signin.query";
            String busy = "{\"errorCode\":\"100001\",\"name\":\"BizError\",\"resultView\":\"人气大爆发，请稍后再试\",\"success\":false}";
            assert "人气大爆发，请稍后再试".equals(RpcRequestGuard.errorMessage(json(busy))) : "resultView must be read";
            for (int i = 0; i < 4; i++) {
                guard(promo).record(json(busy));
                now += (i < 2 ? 5 : 30) * MIN;
                assert !guard(promo).shouldSkip() : "busy must never pause a day, round " + i;
            }
        }
        reset();
        {
            // 未开通的功能一天只请求一次（日志里 collectManurePot G04 一天 150 次）
            String pot = "com.alipay.antfarm.collectManurePot";
            guard(pot, "[{\"manurePotNOs\":\"1\"}]").record(json("{\"success\":false,\"resultCode\":\"G04\",\"memo\":\"肥料已经存满了，去开通芭芭农场种果树吧\"}"));
            now += DAY - 1;
            assert guard(pot, "[{\"manurePotNOs\":\"1\"}]").shouldSkip() : "not-opened feature must wait a day";
            now++;
            assert !guard(pot, "[{\"manurePotNOs\":\"1\"}]").shouldSkip();
            String friendOnly = "com.alipay.antfarm.friendOnlyProbe";
            guard(friendOnly).record(json("{\"success\":false,\"resultCode\":\"X1\",\"memo\":\"好友未开通该功能\"}"));
            assert !guard(friendOnly).shouldSkip() : "someone else's state must not pause the method";
            String friendCert = "com.alipay.antmember.forest.h5.friendCertProbe";
            guard(friendCert).record(json("{\"success\":false,\"resultCode\":\"X2\",\"memo\":\"对方未实名认证\"}"));
            assert !guard(friendCert).shouldSkip() : "\"对方未实名\" must not pause the method for every friend";
        }
        reset();
        String join = "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.joinActivity";
        String rebate = "com.alipay.pcreditbfweb.gameplay.rebate";
        guard(rebate,"[{\"playId\":\"progress\",\"bizScene\":\"HAOYUNKA\",\"bizNo\":\"first\"}]")
                .record(json("{\"success\":false,\"resultCode\":\"default\",\"resultView\":\"人气大爆发，请稍后再试\"}"));
        assert guard(rebate,"[{\"playId\":\"progress\",\"bizScene\":\"HAOYUNKA\",\"bizNo\":\"second\"}]").shouldSkip()
                : "random business numbers bypassed the same play's pause";
        assert !guard(rebate,"[{\"playId\":\"sign\",\"bizScene\":\"HAOYUNKA_SIGN_IN\"}]").shouldSkip()
                : "progress failure paused lucky sign-in";
        guard(join,"[{\"templateId\":\"A\"}]").record(json("{\"error\":1009,\"errorMessage\":\"访问被拒绝\"}"));
        assert guard(join,"[{\"templateId\":\"A\"}]").shouldSkip();
        assert !guard(join,"[{\"templateId\":\"B\"}]").shouldSkip() : "one template paused all member tasks";
        reset();
        String enter = "com.alipay.antfarm.enterFarm";
        guard(enter, "[{\"sceneCode\":\"ANTFARM\",\"userId\":\"friend\"}]").record(json("{\"error\":48}"));
        assert guard(enter, "[{\"sceneCode\":\"ANTFARM\",\"userId\":\"friend\"}]").shouldSkip();
        assert !guard(enter, "[{\"sceneCode\":\"ANTFARM\",\"userId\":\"self\"}]").shouldSkip(); // friend failure must not pause own farm
        pauses("com.alipay.antfarm.orchardRecallAnimal", "[{\"sceneCode\":\"ORCHARD\"}]", MIN);
        pauses("com.alipay.reading.game.dadaDaily.submit", "[{\"activityId\":100}]", MIN, MIN, 5*MIN);
        pauses("com.alipay.reading.game.dadaDaily.submit", "[{\"activityId\":200}]", 5*MIN, 30*MIN, DAY);
        pauses(other, "[{\"sceneCode\":\"ANTFOREST\",\"taskType\":\"daily\"}]", MIN, MIN, 5*MIN);
        pauses(other, "[{\"sceneCode\":\"ANTOCEAN\"}]", 5*MIN, 30*MIN, DAY);
        reset();
        RpcRequestGuard pendingA = guard(other);
        RuntimeInfo.account = "B";
        pendingA.record(json("{\"error\":48}"));
        assert !guard(other).shouldSkip(); // late callback stays with original account
        RuntimeInfo.account = "A";
        assert guard(other).shouldSkip();
        assert !guard(farm).shouldSkip();
        assert !guard(other, "[{\"sceneCode\":\"ANTFOREST\"}]").shouldSkip();
        pendingA.record(json("{\"success\":true}"));
        assert guard(other).shouldSkip(); // in-flight success cannot cancel active pause
        now += 5*MIN;
        pendingA.record(json("{\"success\":true}"));
        pendingA.record(json("{\"error\":48}"));
        now += 5*MIN;
        assert !guard(other).shouldSkip(); // success resets escalation
        reset();
        for (int i=0; i<6; i++) {
            guard(farm).record(json("{\"success\":false,\"memo\":\"饲料已满\"}"));
            guard(forest).record(json("{\"resultCode\":\"PARAM_ILLEGAL2\"}"));
        }
        assert !guard(farm).shouldSkip() && !guard(forest).shouldSkip();
        for (int i=0; i<3; i++) guard(other).record(json("{\"success\":false,\"resultCode\":\"FAIL\"}"));
        assert !guard(other).shouldSkip() : "ordinary business failure must not add a day pause";
        reset();
        guard(forest).record(json("{\"error\":1009,\"errorMessage\":\"访问被拒绝\"}"));
        now += 30 * MIN - 1;
        assert guard(forest).shouldSkip();
        now++;
        assert !guard(forest).shouldSkip();
        guard(other).record(json("{\"error\":2000}"));
        assert !guard(other).shouldSkip(); // preserve login handling
        assert !RpcRequestGuard.isFailure(json("{\"retCode\":\"0\",\"errorMsg\":\"ok\",\"adList\":[{}]}"));
        assert RpcRequestGuard.isFailure(json("{\"error\":48,\"success\":true}"));
        assert !RpcRequestGuard.isFailure(json("{\"resultCode\":\"SUCCESS\"}"));
        assert !RpcRequestGuard.isFailure(json("{\"customPayload\":{}}"));
        assert RpcRequestGuard.errorMessage(json("{\"resultDesc\":\"\",\"errorMessage\":\"网络异常\"}")).equals("网络异常");
        reset();
        String finish = "com.alipay.antfarm.doFarmTask";
        assert guard(finish, "[{\"bizKey\":\"TAO_GOLDEN_V2\"}]").shouldSkip();
        assert guard(finish, "[{\"bizKey\":\"_chouchoulechoukuan\"}]").shouldSkip();
        assert !guard(finish, "[{\"bizkey\":\"ANSWER\"}]").shouldSkip();
        String credit = "com.antgroup.zmxy.zmmemberop.biz.rpc.promise.PromiseRpcManager.pushActivity";
        for (String id : new String[]{"2026010358596942583", "2026012058541320399", "2026012058542045915", "2026012058542176083", "2026012058543269012", "2026012058542511985"}) {
            assert guard(credit, "[{\"recordId\":\""+id+"\"}]").shouldSkip();
        }
        assert !guard(credit, "[{\"recordId\":\"new\"}]").shouldSkip();
        String sports = "com.alipay.sportshealth.biz.rpc.SportsHealthCoinTaskRpc.completeTask";
        assert guard(sports, "[{\"taskAction\":\"SHOW_AD\",\"taskId\":\"AP12300610\"}]").shouldSkip();
        assert !guard(sports, "[{\"taskAction\":\"SHOW_AD\",\"taskId\":\"OTHER\"}]").shouldSkip();
        MyUtils.enabled = false;
        assert !guard(finish, "[{\"bizKey\":\"TAO_GOLDEN_V2\"}]").shouldSkip();
        reset();
        guard(other, "[{\"sceneCode\":\"OCEAN\",\"taskType\":\"a\",\"outBizNo\":\"1\"}]").record(json("{\"error\":48}"));
        assert guard(other, "[{\"sceneCode\":\"OCEAN\",\"taskType\":\"a\",\"outBizNo\":\"2\"}]").shouldSkip();
        assert !guard(other, "[{\"sceneCode\":\"OCEAN\",\"taskType\":\"b\"}]").shouldSkip();
        RpcEntity e = new RpcEntity(finish, "[{\"bizKey\":\"TAO_GOLDEN_V2\"}]");
        assert new RpcRequestGuard(e).shouldSkip();
        assert e.getHasError() && e.getHasResult();
        assert json(e.getResponseString()).getString("error").equals("RPC_SKIPPED");
        e.setResponseObject(json("{\"success\":true}"), "{\"success\":true}");
        assert !e.getHasError(); // reused forest request recovers after pause
        System.out.println("RPC guard: core preservation, escalation, isolation, expiry, GR filters, success parsing passed");
    }
}
''')
    write("rpc/intervallimit/RpcFailureJournalCheck.java", (ROOT / "checks/RpcFailureJournalCheck.java").read_text(encoding="utf-8"))
    cp = os.pathsep.join((str(JSON), str(LOMBOK)))
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-processorpath", str(LOMBOK),
                    "-d", tmp, *map(str, out.rglob("*.java"))], check=True)
    subprocess.run(["java", "-ea", "-Drpc.report.root=" + str(out / "reports"), "-cp", tmp + os.pathsep + cp,
                    "io.github.aw1y2z.sesame.rpc.intervallimit.GuardCheck"], check=True, timeout=30)
    subprocess.run(["java", "-ea", "-Drpc.report.root=" + str(out / "reports"), "-cp", tmp + os.pathsep + cp,
                    "io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailureJournalCheck"], check=True, timeout=30)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + cp,
                    "io.github.aw1y2z.sesame.hook.PreflightCheck"], check=True, timeout=15)

# Verify every transport goes through the shared guard before invoking the host RPC.
for path, calls in (("rpc/bridge/NewRpcBridge.java", 2), ("rpc/bridge/OldRpcBridge.java", 1)):
    code = (SOURCE / path).read_text(encoding="utf-8")
    assert code.count("RpcRequestGuard guard = new RpcRequestGuard(rpcEntity)") == calls
    assert code.count("guard.record(") == calls
    assert code.count("if (guard.shouldSkip()) return rpcEntity;") >= calls * 2
print("RPC bridge guard wiring passed")
