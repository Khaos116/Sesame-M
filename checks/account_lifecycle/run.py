"""Compile production task logic with small Android/model stubs; no Gradle or test dependency."""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/data/task"

STUBS = """
package io.github.aw1y2z.sesame.data.task;
class Build { static class VERSION { static int SDK_INT = 24; } static class VERSION_CODES { static int N = 24; } }
class Model { static Model[] models = new Model[2]; static Model[] getModelArray() { return models; }
  public void prepare() {} public ModelType getType() { return ModelType.TASK; } public boolean isEnable() { return true; }
  public ModelGroup getGroup() { return null; } }
class ModelFields {}
enum ModelType { TASK }
class ModelGroup { String getCode() { return ""; } }
class BaseModel { static void taskRpcRequest() {} static Value getTimedTaskModel() { return new Value(); }
 static class Value { int getValue() { return 1; } } static class TimedTaskModel { static int SYSTEM=0, PROGRAM=1; } }
class Log { static final java.util.concurrent.atomic.AtomicInteger completions = new java.util.concurrent.atomic.AtomicInteger();
 static void record(String s) { if (s.equals("🏁全部任务已执行完成")) completions.incrementAndGet(); }
 static void startModuleLogCount() {} static int stopModuleLogCount() { return 1; }
 static void error(String s) { }
 static void printStackTrace(Throwable t) { throw new AssertionError(t); } }
class ThreadUtil { static void shutdownAndWait(Thread t, long n, java.util.concurrent.TimeUnit u) {
 if(t != null) { t.interrupt(); if(n >= 0) try { t.join(u.toMillis(n)); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } } } }
class StringUtil { static boolean isEmpty(String s) { return s == null || s.isEmpty(); } }
class Status { static boolean hasFlagToday(String s) { return true; } static void flagToday(String s) {} }
class UserIdMap { static String getCurrentUid() { return "account"; } }
class FileUtil { static void backupConfigV2WithRolling(String s) {} }
class ProgramChildTaskExecutor implements ChildTaskExecutor {
 public Boolean addChildTask(ModelTask.ChildModelTask t) { return true; }
 public Boolean removeChildTask(ModelTask.ChildModelTask t) { t.cancel(); return true; }
 public Boolean clearGroupChildTask(String g) { return true; } public Boolean clearAllChildTask() { return true; } }
class SystemChildTaskExecutor extends ProgramChildTaskExecutor {}
"""

with tempfile.TemporaryDirectory(prefix="sesame-account-check-") as directory:
    out = Path(directory)
    for name in ("TaskLifecycle", "BaseTask", "ModelTask", "ChildTaskExecutor"):
        if "--baseline" in sys.argv and name in ("BaseTask", "ModelTask"):
            relative = (SOURCE / f"{name}.java").relative_to(ROOT).as_posix()
            source = subprocess.check_output(["git", "show", f"HEAD:{relative}"], cwd=ROOT).decode("utf-8")
        else:
            source = (SOURCE / f"{name}.java").read_text(encoding="utf-8")
        source = re.sub(r"^import (?:static )?(?:android|lombok|io\.github)\..*;\n", "", source, flags=re.M)
        source = source.replace("@Getter", "")
        if name == "BaseTask":
            source = source.replace("public abstract class BaseTask {", "public abstract class BaseTask { public Thread getThread() { return thread; }")
        if name == "ModelTask":
            source = source.replace("taskRpcRequest();", "BaseModel.taskRpcRequest();")
            source = source.replace("public static class ChildModelTask implements Runnable {", """public static class ChildModelTask implements Runnable {
                public String getId() { return id; }
                public String getGroup() { return group; }
                public Long getExecTime() { return execTime; }
                public ModelTask getModelTask() { return modelTask; }
                public Boolean getIsCancel() { return isCancel; }
            """)
        (out / f"{name}.java").write_text(source, encoding="utf-8")
    (out / "Stubs.java").write_text(STUBS, encoding="utf-8")
    shutil.copy(Path(__file__).with_name("AccountLifecycleCheck.java"), out)
    shutil.copy(Path(__file__).with_name("TaskCompletionCheck.java"), out)
    # Compile the actual async entry methods with deterministic queued/rejected workers.
    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "audit_regressions"))
    from run import method
    async_source = Path(__file__).with_name("AsyncGameCheck.java.in").read_text(encoding="utf-8")
    async_source = async_source.replace("/*REPORT*/", method(
        "model/task/antGame/GameTask.java", "public void report("))
    async_source = async_source.replace("/*START*/", method(
        "model/task/antForest/WhackMole.java", "public static void start(Mode mode)"))
    (out / "AsyncGameCheck.java").write_text(async_source, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(out), *map(str, out.glob("*.java"))], check=True)
    subprocess.run(["java", "-cp", str(out), "io.github.aw1y2z.sesame.data.task.AccountLifecycleCheck"], check=True, timeout=30)
    subprocess.run(["java", "-cp", str(out), "io.github.aw1y2z.sesame.data.task.TaskCompletionCheck"], check=True, timeout=30)
    subprocess.run(["java", "-cp", str(out), "io.github.aw1y2z.sesame.data.task.AsyncGameCheck"], check=True, timeout=30)
