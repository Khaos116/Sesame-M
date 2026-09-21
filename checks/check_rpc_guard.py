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
public class AntMemberRpcCall {
""" + method("model/task/antMember/AntMemberRpcCall.java", "    public static Boolean check()") + "\n}")
    write("data/task/TaskLifecycle.java", (SOURCE / "data/task/TaskLifecycle.java").read_text(encoding="utf-8"))
    hook = (SOURCE / "hook/ApplicationHook.java").read_text(encoding="utf-8")
    end = hook.index("                                    TaskCommon.update();")
    start = hook.rindex("                                    lastExecTime = System.currentTimeMillis();", 0, end)
    # Only shorten the production wait constants; execute the real branches and lifecycle accounting.
    preflight = hook[start:end].replace("get(30, TimeUnit.SECONDS)", "get(50, TimeUnit.MILLISECONDS)")
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
    public static boolean enabled = true;
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
    public static void record(String s) { }
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
public class RpcIntervalLimit { public static void enterIntervalLimit(String method) { } }
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
    static void reset() { RuntimeInfo.accounts.clear(); RuntimeInfo.account = "A"; MyUtils.enabled = true; }
    public interface Callback { void sendJSONResponse(Reply reply); }
    public static class Reply {
        public String toJSONString() { return payload; }
        public String getString(String key) { return json(payload).optString(key); }
    }
    static String payload;
    static int calls;
    static boolean throwDenied;
    public static Object parse(String s) { return json(s); }
    public static void rpc(Object a,Object b,Object c,Object d,Object e,Object f,Object g,Object h,
                           Object i,Object j,Object k,Object l,Object m,Object n,Object o,Object p) {
        calls++;
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
    public static void main(String[] ignored) throws Exception {
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
        assert guard(other).shouldSkip();
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
