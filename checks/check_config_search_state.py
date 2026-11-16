"""Replay production Compose field state and account-deletion IO callbacks, no device/network.

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
log_source = (UI / "MiuixLogViewerActivity.kt").read_text(encoding="utf-8")
card_state = re.search(r"    var expanded by remember\(collapseAll\) \{ mutableStateOf\(!collapseAll\) }", log_source)[0]
card_click = re.search(r"expanded = !expanded\n\s*if \(expanded\) onExpand\(\)", log_source)[0]
assert '"${file.path}:r-${run.runIndex}"' in log_source
assert 'RunEntryCard(run, collapseAll) { listState.requestScrollToItem(index) }' in log_source
code = '''
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.*

class Field(val name: String?, var value: Any?) {
    val code: String get() = name ?: ""
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
                             row: FieldRow, onDependencyChanged: (() -> Unit)?) {
    val field = row.field
    val onFieldChanged: (() -> Unit)? = onDependencyChanged
    @@SWITCH@@
}
@Composable fun RenderFields(fields: List<FieldRow>) {
    val activity: Any? = null
    val userId: String? = "A"
    val groupCode = "FOREST"
    var depVersion by remember { mutableStateOf(0) }
    val bumpDependency: () -> Unit = remember { { depVersion++ } }
    val dependencyCodes = setOf("monopolyPatrol")
    @@ROWS@@
}
@Composable fun RenderSingle(field: Field) {
    val onFieldChanged: (() -> Unit)? = null
    @@SWITCH@@
}
data class CardState(val expanded: Boolean, val click: () -> Unit)
var cardState: CardState? = null
@Composable fun RenderCard(collapseAll: Boolean, onExpand: () -> Unit) {
    @@CARD_STATE@@
    SideEffect { cardState = CardState(expanded) { @@CARD_CLICK@@ } }
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
    suspend fun settle() {
        Snapshot.sendApplyNotifications()
        yield()
        clock.sendFrame(1L)
        yield()
        recomposer.awaitIdle()
    }
    suspend fun single(field: Field) {
        composition.setContent { RenderSingle(field) }
        settle()
    }
    val first = Field("first", true)
    val second = Field("second", false)
    single(first)
    check(displayed.getValue("first").checked)
    single(second)
    check(!displayed.getValue("second").checked) { "reused Boolean body kept another field's remembered value" }
    var revealed = 0
    suspend fun card(collapse: Boolean, identity: String = "A:round1") {
        composition.setContent { key(identity) { RenderCard(collapse) { revealed++ } } }
        settle()
    }
    card(false)
    check(cardState!!.expanded)
    cardState!!.click(); card(false)
    check(!cardState!!.expanded && revealed == 0)
    card(true)
    check(!cardState!!.expanded)
    cardState!!.click(); card(true)
    check(cardState!!.expanded && revealed == 1)
    card(false)
    check(cardState!!.expanded)
    cardState!!.click(); card(false)
    check(!cardState!!.expanded && revealed == 1)
    card(false, "B:round1")
    check(cardState!!.expanded) { "another account inherited a collapsed card" }
    composition.dispose()
    recomposer.close()
    runner.join()
    println("PASS real Compose search/clear/reorder/empty results and isolated config writes")
    println("PASS standalone Boolean slot identity and run-card collapse/expand/reset with production state and click logic")
}
'''.replace("@@SWITCH@@", switch).replace("@@ROWS@@", rows).replace("@@CARD_STATE@@", card_state).replace("@@CARD_CLICK@@", card_click)

main_source = (UI / "MiuixMainActivity.kt").read_text(encoding="utf-8")
config_tab, _ = block(main_source, "fun ConfigTab(")
forest_state = re.search(r"var forest by remember[^\n]+", main_source)[0]
_, forest_change = block(main_source, '}) {\n            forest = it')
scan_declaration = config_tab[config_tab.index("    var items by remember"):config_tab.index("    // ", config_tab.index("    LaunchedEffect(refreshKey)"))]
account_loader = block(main_source, "private fun loadConfigAccounts(")[0]
save_start = main_source.index("fun saveAppConfig(")
save_body = main_source.index("        val appContext", save_start)
save_open = main_source.rfind("{", save_start, save_body)
config_save = main_source[save_start:save_open] + block(main_source, main_source[save_open:save_body + len("        val appContext")])[0]
writer_declaration = re.search(r"private val appConfigWriter = [^\n]+", main_source)[0]

_, delete_click = block(config_tab, "onDelete = {")
_, delete_confirm = block(config_tab, "onConfirm = {")
io_checks = r'''
object IoBoundary {
    lateinit var uiThread: Thread
    lateinit var post: (() -> Unit) -> Unit
    fun ui() = check(Thread.currentThread() === uiThread) { "UI update left the UI thread" }
    fun io() = check(Thread.currentThread() !== uiThread) { "blocking file I/O ran on the UI thread" }
}
object FileUtil {
    val probes = java.util.concurrent.atomic.AtomicInteger()
    val deletes = java.util.concurrent.atomic.AtomicInteger()
    var running = false
    var deleted = true
    val CONFIG_DIRECTORY_FILE = java.nio.file.Files.createTempDirectory("accounts-ui").toFile().apply { resolve("A").mkdir() }
    val clears=mutableListOf<String>()
    fun clearLog(type: String) { IoBoundary.io();clears.add(type) }
    fun getSelfIdFile(uid: String)=CONFIG_DIRECTORY_FILE.resolve(uid).resolve("self.json")
    fun readFromFile(file: java.io.File): String { IoBoundary.io();val n=UserIdMap.calls.incrementAndGet();if(n==1)check(UserIdMap.gate?.await(5,java.util.concurrent.TimeUnit.SECONDS)!=false);return if(n==1) "old" else "new" }
    var deleteGate: java.util.concurrent.CountDownLatch? = null
    fun isHostRunning(): Boolean { IoBoundary.io(); probes.incrementAndGet(); return running }
    fun deleteAccount(uid: String): Boolean {
        IoBoundary.io(); check(uid == "A"); deletes.incrementAndGet()
        check(deleteGate?.await(5, java.util.concurrent.TimeUnit.SECONDS) != false)
        return deleted
    }
}
object UserIdMap {
    val calls=java.util.concurrent.atomic.AtomicInteger()
    var gate: java.util.concurrent.CountDownLatch? = null
    private val title=ThreadLocal<String>()
    fun loadSelf(uid: String) { IoBoundary.io();val n=calls.incrementAndGet();title.set(if(n==1) "old" else "new");if(n==1)check(gate?.await(5,java.util.concurrent.TimeUnit.SECONDS)!=false) }
    fun get(uid: String): User? = User(title.get())
    fun getAccountLabel(uid: String): String? { IoBoundary.io(); return "account" }
}
class User(val showName: String?) {val account: String? = "account"}
class UserEntity {class UserDto(val name:String) {fun toEntity()=User(name)}}
object JsonUtil {fun parseObject(json:String,cls:Class<UserEntity.UserDto>):UserEntity.UserDto? {IoBoundary.io();return UserEntity.UserDto(json)}}
object Log {fun printStackTrace(e: Exception) {if(e.message=="blocking file I/O ran on the UI thread")throw AssertionError(e)}}
object AppConfig {
    val INSTANCE=this;var enableForestLog=true;var fail=false
    var gate: java.util.concurrent.CountDownLatch?=null
    val entered=java.util.concurrent.atomic.AtomicInteger();val saved=mutableListOf<Boolean>()
    fun save(): Boolean {IoBoundary.io();entered.incrementAndGet();check(gate?.await(5,java.util.concurrent.TimeUnit.SECONDS)!=false);if(fail)return false;saved.add(enableForestLog);return true}
}
class Looper {companion object{fun getMainLooper()=Looper()}}
class Handler(looper: Looper) {fun post(action:()->Unit){IoBoundary.post(action)}}
@@WRITER@@
class MiuixMainActivity {
    val applicationContext=this;var broadcasts=0
    fun broadcastReloadConfig(){IoBoundary.ui();check(AppConfig.saved.isNotEmpty());broadcasts++}
    @@CONFIG_SAVE@@
}
var rebuiltForest: Boolean?=null
@Composable fun ReadForest() { @@FOREST_STATE@@
    val rendered=forest;SideEffect {rebuiltForest=rendered}
}
class ForestFixture(val activity: MiuixMainActivity) {var forest=true;val change:(Boolean)->Unit={@@FOREST_CHANGE@@}}
@@ACCOUNT_LOADER@@
var observedAccounts: List<Triple<String?,String,String?>> = emptyList()
@Composable fun ScanAccounts(refreshKey: Int) { @@SCAN_DECLARATION@@
    val rendered=items
    SideEffect {IoBoundary.ui();observedAccounts=rendered}
}
object ConfigV2 { var unloads = 0; fun unload() { IoBoundary.io(); unloads++ } }
object ToastUtil { val messages = mutableListOf<String>(); fun show(context: Any, text: String) { IoBoundary.ui(); messages.add(text) } }
class DeleteFixture(val scope: CoroutineScope) {
    val context = this; val userId = "A"; val title = "account"; val summary: String? = null
    var revealedUid: String? = null
    var pendingDelete: Triple<String, String, String?>? = null
    var deleteInProgress = false
    var refreshKey = 0
    val onDelete: () -> Unit = { @@DELETE_CLICK@@ }
    fun confirmation(target: Triple<String, String, String?>): () -> Unit = { @@DELETE_CONFIRM@@ }
}
suspend fun accountIoChecks() {
    java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher().use { ui ->
        withContext(ui) { IoBoundary.uiThread = Thread.currentThread() }
        val saveScope=CoroutineScope(SupervisorJob()+ui)
        IoBoundary.post={action->saveScope.launch {action()}}
        suspend fun settleSaves(){withContext(Dispatchers.IO){appConfigWriter.submit {}.get(5,java.util.concurrent.TimeUnit.SECONDS)};withContext(ui){}}
        val activity=MiuixMainActivity();val forest=ForestFixture(activity)
        AppConfig.gate=java.util.concurrent.CountDownLatch(1)
        val departingTab=SupervisorJob()
        withContext(ui){CoroutineScope(departingTab+ui).launch(start=CoroutineStart.UNDISPATCHED){forest.change(false);forest.change(true);forest.change(false);check(!forest.forest)}}
        departingTab.cancel()
        withTimeout(5000){while(AppConfig.entered.get()==0)delay(1)}
        withContext(ui){
            check(activity.broadcasts==0&&AppConfig.saved.isEmpty())
            val nextTab=Composition(NoNodes(),Recomposer(ui))
            nextTab.setContent {ReadForest()};check(rebuiltForest==false) {"new tab restored stale switch state"};nextTab.dispose()
        }
        AppConfig.gate!!.countDown();settleSaves();AppConfig.gate=null
        withContext(ui){check(AppConfig.saved==listOf(false,false,false));check(activity.broadcasts==3);check(FileUtil.clears==listOf("forest","forest"))}
        AppConfig.fail=true
        withContext(ui){forest.change(true)};settleSaves()
        withContext(ui){check(activity.broadcasts==3&&ToastUtil.messages.last().contains("\u4fdd\u5b58\u5931\u8d25"))}
        AppConfig.fail=false
        val scanClock=BroadcastFrameClock()
        val scanRecomposer=Recomposer(ui+scanClock)
        val scanRunner=CoroutineScope(ui+scanClock).launch {scanRecomposer.runRecomposeAndApplyChanges()}
        val framePump=CoroutineScope(ui).launch {while(isActive){scanClock.sendFrame(System.nanoTime());delay(1)}}
        val scanComposition=Composition(NoNodes(),scanRecomposer)
        var scanKey by mutableIntStateOf(0)
        withContext(ui){scanComposition.setContent {ScanAccounts(scanKey)}}
        suspend fun scan(key:Int){withContext(ui){scanKey=key;Snapshot.sendApplyNotifications();yield();scanRecomposer.awaitIdle()}}
        UserIdMap.gate=java.util.concurrent.CountDownLatch(1)
        scan(0);withTimeout(5000){while(UserIdMap.calls.get()==0)delay(1)}
        scan(1)
        try {withTimeout(5000){while(withContext(ui){Snapshot.sendApplyNotifications();yield();scanClock.sendFrame(System.nanoTime());yield();observedAccounts.none {it.third?.startsWith("new")==true}})delay(1)}}
        catch(e:TimeoutCancellationException){error("scan calls="+UserIdMap.calls.get()+" observed="+observedAccounts)}
        UserIdMap.gate!!.countDown();withContext(Dispatchers.IO){Thread.sleep(30)}
        withContext(ui){Snapshot.sendApplyNotifications();scanClock.sendFrame(System.nanoTime());yield()}
        withContext(ui){check(observedAccounts.none {it.third?.startsWith("old")==true});scanComposition.dispose();scanRecomposer.close()};scanRunner.join();framePump.cancelAndJoin()
        saveScope.cancel()
        val owner = SupervisorJob(); val scope = CoroutineScope(owner + ui)
        suspend fun settle() { owner.children.toList().joinAll() }
        val fixture = DeleteFixture(scope)
        withContext(ui) { fixture.onDelete(); fixture.onDelete() }; settle()
        withContext(ui) { check(FileUtil.probes.get() == 1 && fixture.pendingDelete?.first == "A" && !fixture.deleteInProgress) }
        FileUtil.running = true
        withContext(ui) { fixture.pendingDelete = null; fixture.onDelete() }; settle()
        withContext(ui) { check(fixture.pendingDelete == null && ToastUtil.messages.last().contains("支付宝正在运行")) }
        FileUtil.running = false
        withContext(ui) { fixture.onDelete() }; settle()
        val confirm = withContext(ui) { fixture.confirmation(fixture.pendingDelete!!) }
        FileUtil.deleteGate = java.util.concurrent.CountDownLatch(1)
        withContext(ui) { confirm(); confirm() }
        withTimeout(5000) { while (FileUtil.deletes.get() == 0) delay(1) }
        withContext(ui) { check(fixture.deleteInProgress && fixture.pendingDelete == null && fixture.refreshKey == 0) }
        FileUtil.deleteGate!!.countDown(); settle()
        withContext(ui) { check(FileUtil.deletes.get() == 1 && ConfigV2.unloads == 1 && fixture.refreshKey == 1 && !fixture.deleteInProgress) }
        FileUtil.deleted = false; FileUtil.deleteGate = null
        withContext(ui) { fixture.onDelete() }; settle()
        withContext(ui) { fixture.confirmation(fixture.pendingDelete!!)() }; settle()
        withContext(ui) { check(ToastUtil.messages.last().contains("删除失败") && !fixture.deleteInProgress) }
        FileUtil.deleted = true; FileUtil.deleteGate = java.util.concurrent.CountDownLatch(1)
        withContext(ui) { fixture.onDelete() }; settle()
        withContext(ui) { fixture.confirmation(fixture.pendingDelete!!)() }
        withTimeout(5000) { while (FileUtil.deletes.get() < 3) delay(1) }
        val refreshes = fixture.refreshKey; val messages = ToastUtil.messages.size
        owner.cancel(); FileUtil.deleteGate!!.countDown(); owner.join()
        check(ConfigV2.unloads == 3 && fixture.refreshKey == refreshes && ToastUtil.messages.size == messages) { "cancelled UI lost disk cleanup or published stale success" }

    }
    appConfigWriter.shutdown()
    println("PASS serial config saves, off-main account scans and cancelled refresh protection")
    println("PASS production account callbacks: IO dispatch, duplicate clicks, host refusal, failure and cancellation cleanup")
}
'''
for marker, value in {"@@DELETE_CLICK@@": delete_click, "@@DELETE_CONFIRM@@": delete_confirm,"@@FOREST_CHANGE@@":forest_change,"@@SCAN_DECLARATION@@":scan_declaration,"@@WRITER@@":writer_declaration,"@@CONFIG_SAVE@@":config_save,"@@ACCOUNT_LOADER@@":account_loader,"@@FOREST_STATE@@":forest_state}.items():
    io_checks = io_checks.replace(marker, value)
code = code.replace('    println("PASS real Compose search/clear/reorder/empty results and isolated config writes")',
                    '    try { accountIoChecks() } finally { appConfigWriter.shutdownNow() }\n    println("PASS real Compose search/clear/reorder/empty results and isolated config writes")') + io_checks

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
    android_log = work / "Log.java"
    android_log.write_text("package android.util; public class Log {public static int e(String tag,String message,Throwable error){error.printStackTrace();return 0;}}", encoding="utf-8")
    java_sources.append(str(android_log))
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

application = (UI.parents[1] / "SesameApplication.java").read_text(encoding="utf-8")
assert "HostBridge.ensurePaired" not in block(application, "public void onCreate()")[0], "Application startup still eagerly reads external agreement config"
