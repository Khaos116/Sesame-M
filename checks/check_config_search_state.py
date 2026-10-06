"""Run production field-row rendering and switch logic with real Compose state, no device/network.

Removing field identity must fail: filtering/reordering cannot borrow another field's
remembered switch state, and toggling a filtered row must update only that field.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
UI = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/ui/miuix"


def artifact(group, name, version=None, extension="jar"):
    directory = CACHE / group / name
    if version is None:
        versions = [p for p in directory.iterdir() if re.fullmatch(r"\d+(\.\d+)+", p.name)]
        version = max(versions, key=lambda p: tuple(map(int, p.name.split(".")))).name
    paths = [p for p in (directory / version).glob(f"*/*.{extension}")
             if not re.search(r"-(sources|javadoc)\.", p.name)]
    assert paths, f"Missing cached {group}:{name}:{version}; build the project first"
    return paths[0]


def block(text, start):
    begin = text.index(start)
    masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"',
                    lambda m: " " * len(m[0]), text)
    opening = masked.index("{", begin)
    depth = 0
    for end in range(opening, len(masked)):
        depth += (masked[end] == "{") - (masked[end] == "}")
        if depth == 0:
            return text[begin:end + 1], text[opening + 1:end]
    raise AssertionError(start)


rows, _ = block((UI / "MiuixGroupFieldsActivity.kt").read_text(encoding="utf-8"), "fields.forEach {")
_, switch = block((UI / "MiuixSettingsActivity.kt").read_text(encoding="utf-8"), 'field.type == "BOOLEAN" -> {')
code = '''
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*

class Field(val name: String?, var value: Any?) {
    val description: String? = null
    fun setObjectValue(next: Any?) { value = next }
}
data class FieldRow(val key: String, val field: Field)
data class Display(val checked: Boolean, val change: (Boolean) -> Unit)
val displayed = linkedMapOf<String, Display>()

@Composable fun SwitchPreference(title: String, summary: String?, checked: Boolean,
                                 onCheckedChange: (Boolean) -> Unit) {
    DisposableEffect(title) { onDispose { displayed.remove(title) } }
    SideEffect { displayed[title] = Display(checked, onCheckedChange) }
}
@Composable fun GroupFieldRow(activity: Any?, userId: String?, groupCode: String,
                             row: FieldRow, onDependencyChanged: () -> Unit) {
    val field = row.field
    val onFieldChanged: (() -> Unit)? = onDependencyChanged
    @@SWITCH@@
}
@Composable fun RenderFields(fields: List<FieldRow>) {
    val activity: Any? = null
    val userId: String? = "A"
    val groupCode = "FOREST"
    var depVersion by remember { mutableStateOf(0) }
    @@ROWS@@
}
class NoNodes : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}
fun main() = runBlocking {
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + clock)
    val runner = launch(clock, start = CoroutineStart.UNDISPATCHED) { recomposer.runRecomposeAndApplyChanges() }
    val composition = Composition(NoNodes(), recomposer)
    val other = Field("other", true)
    val patrol = Field("monopolyPatrol", false)
    val tasks = Field("monopolyTasks", false)
    val all = listOf(FieldRow("forest:other", other), FieldRow("forest:patrol", patrol),
                     FieldRow("forest:tasks", tasks))
    suspend fun show(rows: List<FieldRow>) {
        Snapshot.sendApplyNotifications()
        composition.setContent { RenderFields(rows) }
        yield()
        clock.sendFrame(1L)
        yield()
        recomposer.awaitIdle()
    }
    show(all)
    check(displayed.getValue("other").checked)
    show(all.drop(1))
    check("other" !in displayed)
    check(!displayed.getValue("monopolyPatrol").checked) {
        "Search borrowed the enabled state of another field; actual patrol value is false"
    }
    check(!displayed.getValue("monopolyTasks").checked)
    displayed.getValue("monopolyPatrol").change(true)
    check(patrol.value == true && tasks.value == false && other.value == true)
    show(all)
    check(displayed.getValue("monopolyPatrol").checked)
    check(!displayed.getValue("monopolyTasks").checked)
    show(listOf(all[2], all[1], all[0]))
    check(!displayed.getValue("monopolyTasks").checked)
    check(displayed.getValue("monopolyPatrol").checked)
    show(emptyList())
    check(displayed.isEmpty())
    show(all.drop(1))
    check(displayed.getValue("monopolyPatrol").checked)
    displayed.getValue("monopolyTasks").change(true)
    check(patrol.value == true && tasks.value == true && other.value == true)
    show(all)
    check(displayed.getValue("monopolyTasks").checked)
    composition.dispose()
    recomposer.close()
    runner.join()
    println("PASS real Compose search/clear/reorder/empty results and isolated config writes")
}
'''.replace("@@SWITCH@@", switch).replace("@@ROWS@@", rows)

with tempfile.TemporaryDirectory(prefix="sesame-config-search-") as temporary:
    work = Path(temporary)
    # Only Android thread/trace/parcel boundaries are replaced; Compose slots/state are real.
    android_stubs = {
        "Looper": "public static Looper getMainLooper(){return new Looper();} public Thread getThread(){return Thread.currentThread();}",
        "Trace": "public static void beginSection(String s){} public static void endSection(){}",
        "Parcel": "",
        "Parcelable": "interface Creator<T>{T createFromParcel(Parcel p); T[] newArray(int n);} interface ClassLoaderCreator<T> extends Creator<T>{T createFromParcel(Parcel p,ClassLoader l);}",
    }
    java_sources = []
    for name, body in android_stubs.items():
        path = work / (name + ".java")
        kind = "interface" if name == "Parcelable" else "class"
        path.write_text(f"package android.os; public {kind} {name} {{{body}}}", encoding="utf-8")
        java_sources.append(str(path))
    subprocess.run(["javac", "-J-Xmx128m", "-d", str(work)] + java_sources, check=True, timeout=30)
    runtime = work / "compose-runtime.jar"
    runtime_aar = artifact("androidx.compose.runtime", "runtime-android", extension="aar")
    runtime_version = runtime_aar.parents[1].name
    with zipfile.ZipFile(runtime_aar) as archive:
        runtime.write_bytes(archive.read("classes.jar"))
    dependencies = [runtime, artifact("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.0"),
                    artifact("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2"),
                    artifact("androidx.collection", "collection-jvm"),
                    artifact("org.jetbrains", "annotations", "13.0")]
    if (CACHE / "androidx.compose.runtime/runtime-annotation-android" / runtime_version).exists():
        annotations = work / "compose-annotations.jar"
        with zipfile.ZipFile(artifact("androidx.compose.runtime", "runtime-annotation-android", runtime_version, "aar")) as archive:
            annotations.write_bytes(archive.read("classes.jar"))
        dependencies.append(annotations)
    classpath = os.pathsep.join(map(str, [work] + dependencies))
    compiler = artifact("org.jetbrains.kotlin", "kotlin-compiler-embeddable", "2.4.0")
    plugin = artifact("org.jetbrains.kotlin", "kotlin-compose-compiler-plugin-embeddable", "2.4.0")
    compiler_path = os.pathsep.join(map(str, [compiler,
        artifact("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
        artifact("org.jetbrains.kotlin", "kotlin-script-runtime", "2.4.0"),
        artifact("org.jetbrains.kotlin", "kotlin-build-tools-api", "2.4.0")]))
    source = work / "ConfigSearchCheck.kt"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["java", "-Xms16m", "-Xmx256m", "-XX:+UseSerialGC", "-cp",
                    compiler_path + os.pathsep + classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", classpath, "-Xplugin=" + str(plugin),
                    "-d", str(work), str(source)], check=True, timeout=90)
    subprocess.run(["java", "-Xms16m", "-Xmx128m", "-XX:+UseSerialGC", "-cp",
                    classpath, "ConfigSearchCheckKt"], check=True, timeout=30)
