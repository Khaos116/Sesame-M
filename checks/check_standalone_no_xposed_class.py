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
