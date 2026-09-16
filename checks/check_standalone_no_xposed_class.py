"""Static guard: code paths reachable from the standalone App UI (not the injected Alipay
process) must not reference ApplicationHook. ApplicationHook extends the compileOnly
io.github.libxposed.api.XposedModule, which only exists at runtime when LSPosed actually
injects the module into Alipay. Referencing it from the App's own process throws
NoClassDefFoundError (an Error, not an Exception -- catch(Exception) doesn't stop it), crashing
the app the moment such a method is invoked. Regression: PermissionUtil.checkBatteryPermissions()
used to call ApplicationHook.isHooked()/getContext() even when the caller (the Settings screen's
"立即申请权限" button, running in the App's own process) already had a perfectly good Context to
use instead.
"""
from pathlib import Path
import subprocess
import tempfile
import sys

sys.dont_write_bytecode = True
from audit_regressions.run import method

ROOT = Path(__file__).resolve().parent.parent
permission_util = (ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/PermissionUtil.java").read_text(encoding="utf-8")

start = permission_util.index("public static Boolean checkOrRequestBatteryPermissions(")
end = permission_util.index("\n    }\n", start)
body = permission_util[start:end]
assert "ApplicationHook" not in body, (
    "checkOrRequestBatteryPermissions() (called from the standalone App's Settings screen) "
    "must not reference ApplicationHook -- it crashes with NoClassDefFoundError in the App's "
    "own process. Use the Context passed in instead."
)
assert "checkBatteryPermissions(context)" in body, \
    "checkOrRequestBatteryPermissions() should call the Context-taking overload, not the no-arg one"

print("PASS: standalone-app battery-permission path never touches ApplicationHook")

methods = "\n".join(method("util/PermissionUtil.java", signature) for signature in (
    "    public static boolean checkBatteryPermissions(Context context)",
    "    public static Boolean checkOrRequestBatteryPermissions(Context context)",
    "    public static void openBatterySettings(Context context)"))
assert "ApplicationHook" not in methods
code = r'''
import java.util.*;
public class BatteryPermissionCheck {
    static final String TAG = "test";
    static class Build {
        static class VERSION { static int SDK_INT = 33; } static class VERSION_CODES { static int M = 23; } }
    static class Settings {
        static String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS = "request";
        static String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS = "battery";
        static String ACTION_APPLICATION_DETAILS_SETTINGS = "details";
    }
    static class ClassUtil { static String PACKAGE_NAME = "com.eg.android.AlipayGphone"; }
    static class Uri { static String parse(String s) { return s; } }
    static class Intent {
        static int FLAG_ACTIVITY_SINGLE_TOP = 1, FLAG_ACTIVITY_NEW_TASK = 2;
        String action, data; int flags;
        Intent(String a) { action = a; }
        Intent addFlags(int f) { flags |= f; return this; }
        Intent setData(String s) { data = s; return this; }
    }
    static class PowerManager { boolean allowed;
        boolean isIgnoringBatteryOptimizations(String p) { assert p.equals(ClassUtil.PACKAGE_NAME); return allowed; } }
    static class Context {
        static String POWER_SERVICE = "power";
        PowerManager pm = new PowerManager();
        Set<String> reject = new HashSet<>(); List<Intent> attempts = new ArrayList<>(); String toast;
        Object getSystemService(String s) { return pm; }
        void startActivity(Intent i) {
            attempts.add(i);
            assert (i.flags & Intent.FLAG_ACTIVITY_NEW_TASK) != 0;
            if (i.action.equals("battery")) assert i.data == null;
            else assert i.data.equals("package:" + ClassUtil.PACKAGE_NAME);
            if (reject.contains(i.action)) throw new SecurityException("unavailable");
        }
    }
    static class Log { static void printStackTrace(String s, Throwable e) { } }
    static class ToastUtil { static void show(Context c, String s) { c.toast = s; } }
    @@METHODS@@
    public static void main(String[] args) {
        Context c = new Context();
        assert !checkOrRequestBatteryPermissions(c);
        assert c.attempts.size() == 1 && c.attempts.get(0).action.equals("request");
        c = new Context(); c.pm.allowed = true;
        assert checkOrRequestBatteryPermissions(c) && c.attempts.isEmpty();
        c = new Context(); c.reject.add("request");
        assert !checkOrRequestBatteryPermissions(c);
        assert c.attempts.size() == 2 && c.attempts.get(1).action.equals("details") && c.toast != null;
        c = new Context(); c.reject.addAll(Arrays.asList("request", "battery", "details"));
        assert !checkOrRequestBatteryPermissions(c);
        assert c.attempts.size() == 2 && c.toast.contains("无法打开");
        c = new Context();
        openBatterySettings(c);
        assert c.attempts.size() == 1 && c.attempts.get(0).action.equals("details") && c.toast != null;
        c = new Context(); c.pm = null; c.reject.add("request");
        assert !checkOrRequestBatteryPermissions(c);
        assert c.attempts.size() == 2 && c.attempts.get(1).action.equals("details");
        System.out.println("PASS battery settings: common app-details fallback, manual entry, denial, missing service, target package and feedback");
    }
}
'''.replace("@@METHODS@@", methods)
with tempfile.TemporaryDirectory(prefix="sesame-battery-check-") as directory:
    source = Path(directory) / "BatteryPermissionCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", directory, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", directory, "BatteryPermissionCheck"], check=True)
