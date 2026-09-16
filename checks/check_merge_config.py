"""Run after :app:compileNormalDebugJavaWithJavac; uses real model fields/Jackson and isolated I/O doubles."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, ROOT, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
jars = [str(next((cache / "com.fasterxml.jackson.core" / name / "2.18.2").glob(f"*/{name}-2.18.2.jar")))
        for name in ("jackson-databind", "jackson-core", "jackson-annotations")]
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
if not sdk:
    for line in (ROOT / "local.properties").read_text().splitlines():
        if line.startswith("sdk.dir="):
            sdk = line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
android = next((Path(sdk) / "platforms").glob("android-37*/android.jar"))
classes = ROOT / "app/build/intermediates/javac/normalDebug/compileNormalDebugJavaWithJavac/classes"
classpath = os.pathsep.join([str(classes), str(android), *jars])
code = (Path(__file__).parent / "audit_regressions/MergeConfig.java.in").read_text(encoding="utf-8")
for key, path, signature in (
    ("@@CONFIG@@", "data/ConfigV2.java", "    public void setModelFieldsMap("),
    ("@@BATTERY@@", "data/AppConfig.java", "    public static boolean shouldRequestBatteryPermission("),
    ("@@RANK@@", "model/task/antFarm/AntFarm.java", "    private boolean isStealRankTime("),
    ("@@RELOAD@@", "hook/ApplicationHook.java", "    private void scheduleAccountReload("),
    ("@@LIFECYCLE@@", "data/task/TaskLifecycle.java", "public final class TaskLifecycle"),
):
    code = code.replace(key, method(path, signature).replace("public final class TaskLifecycle", "static final class TaskLifecycle"))
ui = (SOURCE / "ui/miuix/MiuixSettingsActivity.kt").read_text(encoding="utf-8")
assert "val current = field.configValue.toIntOrNull()" in ui
assert "field.setConfigValue(parsed.toString())" in ui
assert "sel = if (single) setOf(opt.id) else sel + opt.id" in ui, "single selection must replace the previous ID"
assert "counts.filterKeys { it in sel }" in ui, "save must omit deselected counts"
with tempfile.TemporaryDirectory(prefix="sesame-merge-check-") as temporary:
    source = Path(temporary) / "MergeConfigCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", temporary, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + classpath, "MergeConfigCheck"], check=True)
