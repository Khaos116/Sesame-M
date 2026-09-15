"""Run the production RPC guard/entity with real org.json and isolated clock/account I/O."""
from pathlib import Path
import os
import subprocess
import tempfile

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
                 "rpc/bridge/NewRpcBridge.java", "rpc/bridge/OldRpcBridge.java", "rpc/bridge/RpcBridge.java", "rpc/bridge/RpcVersion.java"):
        code = (SOURCE / name).read_text(encoding="utf-8")
        write(name, code.replace("System.currentTimeMillis()", "io.github.aw1y2z.sesame.rpc.intervallimit.GuardCheck.now"))
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
}
""")
    write("hook/ApplicationHook.java", """
package io.github.aw1y2z.sesame.hook;
public class ApplicationHook {
    public static boolean offline;
    public static boolean isOffline() { return offline; }
    public static void setOffline(boolean v) { offline = v; }
    public static void reLoginByBroadcast() { }
    public static ClassLoader getClassLoader() { return ApplicationHook.class.getClassLoader(); }
}
""")
    write("model/normal/base/BaseModel.java", """
package io.github.aw1y2z.sesame.model.normal.base;
public class BaseModel {
    public record Value<T>(T getValue) { }
    public static Value<Boolean> getTimeoutRestart() { return new Value<>(false); }
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
            RpcEntity normal = new RpcEntity("com.alipay.antfarm.feedAnimal", "[{}]");
            normal.setResponseObject(json(payload), payload);
            assert RpcLog.responseData(normal).equals(payload);
            assert RpcLog.requestData(normal).equals("[{}]");
            reset(); calls = 0; throwDenied = true;
            RpcEntity denied = new RpcEntity("other.denied", "[{}]");
            if (async) bridge.newAsyncRequest(denied, 3, 0); else bridge.requestObject(denied, 3, 0);
            assert calls == 1 && denied.getHasError();
            now += DAY - 1;
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
        String farm = "com.alipay.antfarm.feedAnimal";
        String forest = "alipay.antmember.forest.h5.collectEnergy";
        String other = "com.alipay.antiep.receiveTaskAward";
        pauses(farm, "[{}]", MIN, MIN, 5*MIN);
        pauses(forest, "[{}]", MIN, MIN, 5*MIN);
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
        now += DAY-1;
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
    cp = os.pathsep.join((str(JSON), str(LOMBOK)))
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-processorpath", str(LOMBOK),
                    "-d", tmp, *map(str, out.rglob("*.java"))], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + cp,
                    "io.github.aw1y2z.sesame.rpc.intervallimit.GuardCheck"], check=True, timeout=30)

# Verify every transport goes through the shared guard before invoking the host RPC.
for path, calls in (("rpc/bridge/NewRpcBridge.java", 2), ("rpc/bridge/OldRpcBridge.java", 1)):
    code = (SOURCE / path).read_text(encoding="utf-8")
    assert code.count("RpcRequestGuard guard = new RpcRequestGuard(rpcEntity)") == calls
    assert code.count("guard.record(") == calls
    assert code.count("if (guard.shouldSkip()) return rpcEntity;") >= calls * 2
print("RPC bridge guard wiring passed")
