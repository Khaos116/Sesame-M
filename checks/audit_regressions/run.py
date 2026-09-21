"""Plain JVM checks against current production methods. Requires JDK 17+ and cached org.json.

Run from anywhere: python checks/audit_regressions/run.py
Only temporary build files are written; no Android device or RPC calls are used.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame"


def method(path, signature):
    text = (SOURCE / path).read_text(encoding="utf-8")
    start = text.index(signature)
    # Ignore braces inside comments and literals while retaining original offsets.
    masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda m: " " * len(m[0]), text)
    depth = 0
    for end in range(masked.index("{", start), len(text)):
        depth += (masked[end] == "{") - (masked[end] == "}")
        if depth == 0:
            return text[start:end + 1]
    raise AssertionError(signature)


def main():
    cache = Path(os.environ.get("GRADLE_USER_HOME", str(Path.home() / ".gradle")))
    jars = list((cache / "caches/modules-2/files-2.1/org.json/json").glob("*/*/*.jar"))
    if not jars:
        raise SystemExit("org.json jar missing in Gradle cache; run the project's Gradle build first")
    jar = str(sorted(jars)[-1])
    with tempfile.TemporaryDirectory(prefix="sesame-audit-check-") as temporary:
        work = Path(temporary)

        def run(name, filename, template, replacements):
            folder = work / name
            folder.mkdir()
            text = (HERE / template).read_text(encoding="utf-8")
            for key, value in replacements.items():
                assert key in text
                text = text.replace(key, value)
            (folder / filename).write_text(text, encoding="utf-8")
            compile_run(folder, filename.removesuffix(".java"))

        def compile_run(folder, main_class):
            args = ["javac", "-encoding", "UTF-8", "-cp", jar, "-d", str(folder)]
            subprocess.run(args + [str(p) for p in folder.rglob("*.java")], check=True)
            subprocess.run(["java", "-ea", "-cp", str(folder) + os.pathsep + jar, main_class], check=True)

        run("donations", "ReviewCheck.java", "Donations.java.in", {"@@DONATIONS@@": "\n".join(
            method("model/task/protectEcology/ProtectEcology.java", "    private static void " + name)
            for name in ("marathonQueryActivity", "carbonQueryActivity"))})
        run("pagination", "GreenCurrent.java", "Pagination.java.in", {"@@PAGINATION@@": method(
            "model/task/greenFinance/GreenFinance.java", "    private void batchStealFriend")})
        run("tasks", "Review.java", "Tasks.java.in", {"@@TASKS@@": method(
            "model/task/goldenbeans/GoldenBeansTasks.java", "    private boolean runTaskList")})
        run("answers", "AnswerCheck.java", "Answers.java.in", {"@@ANSWER@@": method(
            "model/normal/answerAI/GeminiAI.java", "    public Integer getAnswer(")})
        run("scheduler", "SchedulerCheck.java", "Scheduler.java.in", {
            "@@LIFECYCLE@@": method("data/task/TaskLifecycle.java", "public final class TaskLifecycle")
                .replace("public final class TaskLifecycle", "static final class TaskLifecycle", 1),
            "@@EXEC@@": method("hook/ApplicationHook.java", "    private static void execHandler()"),
            "@@REQUEST@@": method("hook/ApplicationHook.java", "    public static void requestEarlyTaskRun()"),
            "@@EARLY@@": method("hook/ApplicationHook.java", "    private static final Runnable earlyTaskRun =")})
        folder = work / "exchange"
        shutil.copytree(HERE / "exchange", folder)
        relative = "io/github/aw1y2z/sesame/model/task/goldenbeans/GoldenBeansExchange.java"
        shutil.copyfile(SOURCE / "model/task/goldenbeans/GoldenBeansExchange.java", folder / relative)
        compile_run(folder, "Review")
        run("logs", "LogCheck.java", "Logs.java.in", {"@@LOGS@@": "\n".join(
            method("util/FileUtil.java", signature) for signature in (
                "    public static File getCurrentUserLogDirectory()",
                "    private static String logDirectoryName(",
                "    public static void publishCurrentLogUser(",
                "    public static File getUserLogDirectory(",
                "    public static File getLogDirectoryByName(",
                "    private static File getLogFile(",
                "    private static java.util.List<File> getLogFiles()",
                "    public static void clearLog()",
                "    public static void clearLog(String logName)"))})
        assert "FLAG_TASKS_DONE" not in (SOURCE / "model/task/goldenbeans/goldenbeans.java").read_text(encoding="utf-8")
        response = method("model/normal/answerAI/GeminiAI.java", "    public String getAnswerStr(")
        assert "replaceAll" not in response and "return answer.trim();" in response
        boot = method("model/normal/base/BaseModel.java", "    public void boot(")
        assert boot.index("CaptchaHook.setupHook(classLoader)") < boot.index("CaptchaHook.updateHooks(")
        fish = (SOURCE / "model/task/fish/FishTask.java").read_text(encoding="utf-8")
        assert 'format.setTimeZone(TimeZone.getTimeZone("GMT+8"))' in fish
        coins = method("model/task/antSports/AntSports.java", "    private void receiveCoinAsset()")
        assert '!data.has("assetId")' not in coins and 'jo.optString("assetId").isEmpty()' in coins
        print("All audit regression checks passed")


if __name__ == "__main__":
    main()
