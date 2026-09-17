"""Run the production refresh policy and log parser on the JVM (no Android UI emulation).

Requires the Kotlin 2.4.0 compiler cached by the project's Gradle build.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

source_path = "ui/miuix/MiuixLogViewerActivity.kt"
source = (SOURCE / source_path).read_text(encoding="utf-8")
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
coordinates = [
    ("org.jetbrains.kotlin", name, "2.4.0") for name in
    ("kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-daemon-embeddable")
] + [("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
     ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
     ("org.jetbrains", "annotations", "13.0")]
jars = [str(next((cache / group / name / version).glob(f"*/{name}-{version}.jar")))
        for group, name, version in coordinates]
classpath = os.pathsep.join(jars)
model = re.search(r"data class LogEntry\([\s\S]*?\n\)", source)[0]
code = "import java.io.*\n" + model + "\n"
code += "private const val MAX_TAIL_BYTES = 1024 * 1024L\nprivate const val MAX_LOG_ENTRIES = 500\n"
code += method(source_path, "private fun readTailText(") + "\n"
code += method(source_path, "private fun loadLogEntries(") + "\n"
code += method("ui/miuix/MiuixMainActivity.kt", "private fun accountDisplayName(") + "\n"
code += '''
// Account I/O and JSON boundary doubles; run the production label/fallback logic.
object FileUtil {
    var profile = "valid"
    fun getSelfIdFile(uid: String) = File(uid)
    fun readFromFile(file: File): String = profile
}
class UserEntity(val showName: String, val account: String) {
    class UserDto { fun toEntity() = UserEntity("测试账号", "test@example.com") }
}
object JsonUtil {
    fun parseObject(body: String, type: Class<UserEntity.UserDto>): UserEntity.UserDto? = when (body) {
        "valid" -> UserEntity.UserDto()
        "null" -> null
        else -> throw IllegalArgumentException("Invalid profile")
    }
}
class ListState {
    var canScrollBackward = false
    var requests = 0
    fun requestScrollToItem(index: Int) { check(index == 0); requests++ }
}
fun main() {
    check(accountDisplayName("20880001") == "测试账号(test@example.com)")
    for (profile in listOf("", "broken", "null")) {
        FileUtil.profile = profile
        check(accountDisplayName("20880001") == "20880001")
    }
    println("PASS: account label and missing/invalid profile UID fallback")
    val file = File.createTempFile("sesame-log-follow", ".log")
    try {
        for (category in listOf("森林", "庄园", "金豆", "其他")) {
            file.writeText("20:00:00.000 $category 旧记录\\n20:00:01.000 $category 新记录\\n续行")
            val displayed = loadLogEntries(file).asReversed()
            check(displayed.size == 2) { "$category records must remain separate" }
            check(displayed[0].time == "20:00:01.000" && displayed[0].tag == null)
            check(displayed[0].body == "$category 新记录\\n续行")
            check(displayed[1].body == "$category 旧记录")
        }
        println("PASS: four untagged categories display newest first and preserve continuation lines")
        file.writeText((0..599).joinToString("\\n") { "12:00:00.000 I: entry $it" })
        var entries = emptyList<LogEntry>()
        val listState = ListState()
        @@UPDATE@@
        updateEntries(loadLogEntries(file))
        check(entries.size == 500 && listState.requests == 1)
        check(entries.asReversed().first().body == "entry 599")
        file.appendText("\\n12:00:01.000 I: latest")
        updateEntries(loadLogEntries(file))
        check(entries.size == 500 && listState.requests == 2)
        file.appendText("\\n" + "continuation\\n".repeat(100))
        updateEntries(loadLogEntries(file))
        check(entries.size == 500 && listState.requests == 3)
        listState.canScrollBackward = true // Older log, including a partially visible last card.
        file.appendText("\\n12:00:02.000 I: newer")
        updateEntries(loadLogEntries(file))
        check(listState.requests == 3)
        listState.canScrollBackward = false
        file.appendText("\\n12:00:03.000 I: newest")
        updateEntries(loadLogEntries(file))
        check(listState.requests == 4)
        listState.canScrollBackward = true
        updateEntries(emptyList(), reset = true) // Clearing / changing account or date.
        check(listState.requests == 5 && entries.isEmpty())
        updateEntries(loadLogEntries(file))
        check(listState.requests == 6)
        println("PASS: empty entry, 500-entry rollover, multiline append, manual reading, resume and reset")
    } finally { file.delete() }
}
'''.replace("@@UPDATE@@", method(source_path, "    fun updateEntries("))
assert "reverseLayout" not in source
assert re.search(r"itemsIndexed\(\s*filteredEntries\.asReversed\(\)", source), \
    "list must render filteredEntries reversed without flipping layout direction"
assert "LaunchedEffect(entries.size)" not in source
with tempfile.TemporaryDirectory(prefix="sesame-log-check-") as temporary:
    work = Path(temporary)
    kotlin = work / "LogFollowCheck.kt"
    kotlin.write_text(code, encoding="utf-8")
    subprocess.run(["java", "-cp", classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", classpath,
                    "-d", str(work), str(kotlin)], check=True)
    subprocess.run(["java", "-cp", str(work) + os.pathsep + classpath, "LogFollowCheckKt"], check=True)
