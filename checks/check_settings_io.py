"""Replay actual settings Kotlin persistence entry points with slow/failing storage."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/ui/miuix"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

def artifact(group, name, version=None):
    if version is None:
        version = max((p.name for p in (CACHE / group / name).iterdir()), key=lambda v: tuple(map(int, v.split('.'))))
    return next(p for p in (CACHE / group / name / version).glob("*/*.jar")
                if not re.search(r"-(sources|javadoc)\.", p.name))

def block(text, start):
    begin = text.index(start)
    masked = re.sub(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"', lambda m: " " * len(m[0]), text)
    body_start = begin
    if 'fun ' in start:
        paren = masked.index('(', begin)
        nesting = 1
        body_start = paren + 1
        while nesting:
            nesting += (masked[body_start] == '(') - (masked[body_start] == ')')
            body_start += 1
    opening = masked.index("{", body_start)
    depth = 0
    for end in range(opening, len(masked)):
        depth += (masked[end] == "{") - (masked[end] == "}")
        if depth == 0:
            return text[begin:end + 1]
    raise AssertionError(start)

group = (UI / "MiuixGroupFieldsActivity.kt").read_text(encoding="utf-8")
settings = (UI / "MiuixSettingsActivity.kt").read_text(encoding="utf-8")
selection = (UI / "MiuixSelectionEditActivity.kt").read_text(encoding="utf-8")
base = (UI / "MiuixConfigActivity.kt").read_text(encoding="utf-8")
queue = (UI / "SettingsIo.kt").read_text(encoding="utf-8")
for source in (settings, group, selection):
    assert 'setEditorContent(userId' in block(source, 'override fun onCreate(')
    assert 'ConfigPreload.' not in block(source, 'override fun onCreate(')
    assert ': MiuixConfigActivity()' in source
assert 'activity.importConfig(uri)' in block(settings, 'val importLauncher =')
assert 'activity.exportConfig(uri)' in block(settings, 'val exportLauncher =')
assert 'onExport = { activity.prepareExport { exportLauncher.launch(it) } }' in settings
assert 'setEditorContent(userId, forceReload = true)' in block(settings, 'override fun onCreate(')
assert 'setCancelable(false)' in block(base, 'private fun updateProgress(')
assert 'SettingsIo.listeners.remove(ioListener)' in block(base, 'override fun onDestroy(')
code = r'''
import java.io.File
import java.util.concurrent.*
import com.fasterxml.jackson.databind.ObjectMapper

object Main {
    val thread = Thread.currentThread()
    val queue = LinkedBlockingQueue<Runnable>()
    fun checkMain() = check(Thread.currentThread() === thread)
    fun pump() { checkMain(); while (true) (queue.poll() ?: break).run() }
    fun settle() {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (SettingsIo.busy) {
            check(System.nanoTime() < end) { "worker failed to finish" }
            queue.poll(100, TimeUnit.MILLISECONDS)?.run()
        }
        pump()
    }
}
class Looper {
    companion object {
        val main = Looper()
        fun getMainLooper() = main
        fun myLooper() = if (Thread.currentThread() === Main.thread) main else null
    }
}
class Handler(looper: Looper) { fun post(task: Runnable) { Main.queue.add(task) } }
object UserIdMap { @Volatile var uid: String? = null; fun getCurrentUid() = uid }
object Storage {
    @Volatile var entered = CountDownLatch(0)
    @Volatile var release = CountDownLatch(0)
    @Volatile var fail = false
    @Volatile var writes = 0
    var lastUid: String? = null
    fun slow() { entered = CountDownLatch(1); release = CountDownLatch(1) }
    fun touch() {
        check(Thread.currentThread() !== Main.thread) { "configuration I/O on main" }
        entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
    }
}
object ConfigV2 {
    val INSTANCE = this
    @Volatile var isInit = true
    fun didLastLoadFail() = ConfigPreload.realLoad && LoadReplay.didLastLoadFail()
    var dirty = true
    fun hasFieldChanges(): Boolean { Storage.touch(); return dirty }
    fun save(uid: String?, force: Boolean): Boolean {
        Storage.touch()
        if (Storage.fail) return false
        Storage.writes++; Storage.lastUid = uid
        return true
    }
    val field = ModelField("SELECT")
    fun getModelFields(code: String) = mapOf("test" to field)
}
open class ModelField(val type: String, val code: String = "test") {
    var value: Any? = null
    fun setObjectValue(v: Any?) { value = v }
}
class SelectAndCountModelField: ModelField("SELECT_AND_COUNT") { fun clear() {}; fun add(id: String, n: Int) {} }
class SelectAndCountOneModelField: ModelField("SELECT_AND_COUNT_ONE") { fun clear() {}; fun add(id: String, n: Int) {} }
object ConfigPreload {
    val folder = java.nio.file.Files.createTempDirectory("settings-data-").toFile()
    var fail = false
    var realLoad = false
    var disk = "old"
    var memory = ""
    var loads = 0
    fun getConfigFile(uid: String?) = File(folder, (uid ?: "default") + ".json")
    fun prepare(uid: String?) { Storage.touch(); if (fail) error("prepare failed"); if(realLoad) LoadReplay.load(uid); UserIdMap.uid = uid; ConfigV2.isInit = true; memory = disk; loads++ }
    fun ensurePrepared(uid: String?) = prepare(uid)
}
object Model { fun initAllModel() { Storage.touch() } }
object FileUtil {
    var backups = 0
    var deletes = 0
    fun accountLabel(uid: String): String = io.github.aw1y2z.sesame.util.AccountFolderName.displayLabel(uid)
    fun backupConfigV2BeforeWrite(uid: String) { Storage.touch(); backups++ }
    fun backupConfigV2WithRolling(uid: String) { Storage.touch() }
    fun deleteFile(f: File): Boolean { Storage.touch(); deletes++; return f.delete() }
}
object JsonUtil { fun toNode(text: String): Any? = ObjectMapper().readTree(text); fun copyMapper() = ObjectMapper() }
class Resolver {
    var text = ""
    var output: java.io.OutputStream? = java.io.ByteArrayOutputStream()
    var outputCalls = 0
    fun openInputStream(uri: String): java.io.InputStream? { Storage.touch(); return text.byteInputStream() }
    fun openOutputStream(uri: String): java.io.OutputStream? { Storage.touch(); outputCalls++; return output }
}
class Context { val contentResolver = Resolver() }
object ToastUtil { val messages = mutableListOf<String>(); fun show(a: Any, text: String) { Main.checkMain(); messages += text } }
object HostBridge {
    var executions = 0
    var restarts = 0
    fun send(context: Any, cmd: String, uid: String?, extra: Any?, force: Boolean) { restarts++ }
    fun executeModule(a: Any, uid: String?, model: String, action: String) { Main.checkMain(); executions++ }
}
object AndroidLog { var errors = 0; fun e(tag: String, message: String) { errors++ } }
@@QUEUE@@
open class Editor(var editorUid: String?) {
    var isFinishing = false
    var isDestroyed = false
    var finishes = 0
    val applicationContext = Context()
    var resumed = true
    var waitingForPrepare = false
    var forcePrepare = false
    var ioVersion = 0
    fun updateProgress() {}
    @@LISTENER@@
    init { SettingsIo.listeners.add(ioListener) }
    fun prepare() = prepareEditor()
    fun finish() { Main.checkMain(); finishes++; isFinishing = true }
    @@BASE@@
}
class Group(uid: String?): Editor(uid) {
    var userId = uid
    @@EXECUTE@@
    @@CLOSE@@
}
class Settings(uid: String?): Editor(uid) {
    var userId = uid
    var fieldsVersion = 0
    @@IMPORT@@
    @@EXPORT@@
    @@DELETE@@
    @@EXPORT_NAME@@
}
class Selection(uid: String?): Editor(uid) {
    val activity = this
    var dirty = true
    var sel = setOf("friend")
    var counts = mapOf("friend" to 2)
    val modelCode = "test"
    val field = ConfigV2.field
    @@APPLY@@
}
fun load(uid: String?) {
    check(SettingsIo.submit(uid, true, { ConfigPreload.prepare(uid); true }, { check(it) }))
    Main.settle()
}
fun main() {
    Main.checkMain()
    load("A")
    io.github.aw1y2z.sesame.util.AccountFolderName.install({ uid ->
        Storage.touch()
        val self = File(ConfigPreload.folder, "$uid.self")
        if (self.exists()) self.readLines().toTypedArray() else null
    }, null)
    File(ConfigPreload.folder, "A.self").writeText("C176\naccount")
    val exportNames = mutableListOf<String>()
    Storage.slow()
    val naming = Settings("A")
    naming.prepareExport { Main.checkMain(); exportNames += it }
    check(Storage.entered.await(2, TimeUnit.SECONDS) && exportNames.isEmpty())
    Storage.release.countDown(); Main.settle()
    check(exportNames == listOf("[C176]-config_v2.json"))
    load(null)
    Settings(null).prepareExport { Main.checkMain(); exportNames += it }; Main.settle()
    load("missing")
    Settings("missing").prepareExport { Main.checkMain(); exportNames += it }; Main.settle()
    check(exportNames == listOf("[C176]-config_v2.json", "[默认]-config_v2.json", "[missing]-config_v2.json"))
    val rejectedNames = ToastUtil.messages.size
    naming.prepareExport { error("stale owner opened file picker") }
    check(ToastUtil.messages.size == rejectedNames + 1)
    var failures = 0
    var successes = 0
    naming.runEditorIo(work = { error("stale work ran") }, onSuccess = { successes++ }, onFailure = { failures++ })
    check(failures == 1 && successes == 0)
    naming.isDestroyed = true
    naming.runEditorIo(work = { error("destroyed work ran") }, onSuccess = { successes++ }, onFailure = { failures++ })
    check(failures == 1 && successes == 0)
    naming.isDestroyed = false; naming.isFinishing = true
    naming.runEditorIo(work = { error("finishing work ran") }, onSuccess = { successes++ }, onFailure = { failures++ })
    check(failures == 1 && successes == 0)
    load("A")
    Settings("A").runEditorIo(work = { true }, onSuccess = { successes++ }, onFailure = { failures++ })
    Main.settle(); check(successes == 1 && failures == 1)
    // A rejected import must not restore the old owner while another same-UID reload owns the slot.
    Storage.slow()
    check(SettingsIo.submit("A", true, { Storage.touch(); true }, { check(it) }))
    check(Storage.entered.await(2, TimeUnit.SECONDS) && !SettingsIo.loaded)
    val refusedImport = Settings("A")
    val importFailures = ToastUtil.messages.size
    refusedImport.importConfig("uri")
    check(SettingsIo.busy && !SettingsIo.loaded && ToastUtil.messages.size == importFailures + 1)
    Storage.release.countDown(); Main.settle()
    check(SettingsIo.owns("A"))
    // Real prepare/listener: a paused A waiting behind B must not take B's owner.
    Storage.slow()
    check(SettingsIo.submit("B", true, { ConfigPreload.prepare("B"); true }, { check(it) }))
    check(Storage.entered.await(2, TimeUnit.SECONDS))
    val pausedA = Editor("A")
    pausedA.prepare(); pausedA.resumed = false
    Storage.release.countDown(); Main.settle()
    check(SettingsIo.owns("B") && pausedA.waitingForPrepare)
    pausedA.resumed = true; pausedA.prepare(); Main.settle()
    check(SettingsIo.owns("A") && !pausedA.waitingForPrepare)
    // Top-level reopening refreshes disk once; a child/resume keeps unsaved fields.
    ConfigPreload.memory = "unsaved"
    Editor("A").prepare(); Main.settle()
    check(ConfigPreload.memory == "unsaved")
    ConfigPreload.disk = "host changed"
    val reopened = Editor("A").apply { forcePrepare = true }
    val loadsBefore = ConfigPreload.loads
    Storage.slow()
    reopened.prepare()
    check(Storage.entered.await(2, TimeUnit.SECONDS))
    reopened.prepare() // onResume while first load is pending
    Storage.release.countDown(); Main.settle()
    check(ConfigPreload.memory == "host changed" && ConfigPreload.loads == loadsBefore + 1)
    Storage.slow()
    val group = Group("A")
    group.executeModule("FOREST")
    check(SettingsIo.busy && Storage.entered.await(2, TimeUnit.SECONDS))
    val blockedSettings = Settings("A")
    val failureCount = ToastUtil.messages.size
    blockedSettings.exportConfig("uri")
    blockedSettings.deleteConfig()
    check(ToastUtil.messages.size == failureCount + 2) { "busy picker/delete requests disappeared without failure feedback" }
    check(blockedSettings.applicationContext.contentResolver.outputCalls == 0 && FileUtil.deletes == 0 && blockedSettings.finishes == 0)
    check(HostBridge.executions == 0 && Storage.writes == 0)
    check(!group.canEditConfig())
    group.executeModule("FOREST")
    var loadedB = false
    check(!SettingsIo.submit("B", true, { loadedB = true; true }, {}))
    Storage.release.countDown(); Main.settle()
    check(Storage.writes == 1 && HostBridge.executions == 1 && !loadedB)
    check(Storage.lastUid == "A")

    // Listener, work and completion failures must not strand the single admitted slot.
    val badListener: () -> Unit = { error("injected listener") }
    SettingsIo.listeners.add(badListener)
    check(SettingsIo.submit("A", work = { error("injected work") }, complete = { check(!it); error("injected callback") }))
    Main.settle()
    SettingsIo.listeners.remove(badListener)
    check(AndroidLog.errors >= 3)
    var recovered = false
    check(SettingsIo.submit("A", work = { true }, complete = { recovered = it }))
    Main.settle(); check(recovered)

    Storage.fail = true
    group.saveAndFinish(); Main.settle()
    check(group.finishes == 0)
    group.executeModule("FOREST"); Main.settle()
    check(HostBridge.executions == 1)
    val selected = Selection("A")
    selected.applyAndSave(); Main.settle()
    check(selected.finishes == 0 && selected.dirty)
    Storage.fail = false
    selected.applyAndSave(); Main.settle()
    check(selected.finishes == 1 && !selected.dirty)

    // Destroying the old screen does not cancel an admitted write or call finish on it.
    Storage.slow()
    val destroyed = Group("A")
    destroyed.saveAndFinish()
    check(Storage.entered.await(2, TimeUnit.SECONDS))
    destroyed.isDestroyed = true
    val before = Storage.writes
    Storage.release.countDown(); Main.settle()
    check(Storage.writes == before + 1 && destroyed.finishes == 0)

    load("B")
    val stale = Group("A")
    stale.executeModule("FOREST"); Main.settle()
    check(HostBridge.executions == 1)
    ConfigV2.isInit = false
    check(!SettingsIo.owns("B"))
    load("B")
    var deleteComplete = false
    Storage.slow()
    check(SettingsIo.submitExternal({ Storage.touch(); ConfigV2.isInit = false; true }, { deleteComplete = it }))
    check(Storage.entered.await(2, TimeUnit.SECONDS))
    check(!SettingsIo.submit("B", work = { error("save overlapped delete") }, complete = {}))
    Storage.release.countDown(); Main.settle()
    check(deleteComplete && !SettingsIo.owns("B"))
    load("B")
    UserIdMap.uid = "C"
    Group("B").executeModule("FOREST"); Main.settle()
    check(HostBridge.executions == 1)
    load("B")
    Group("B").executeModule("FOREST")
    val completionDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (Main.queue.isEmpty()) {
        check(System.nanoTime() < completionDeadline)
        Thread.yield()
    }
    UserIdMap.uid = "C" // save completed, but its UI callback has not run
    Main.settle()
    check(HostBridge.executions == 1)
    load(null)
    val default = Group(null)
    default.saveAndFinish(); Main.settle()
    check(default.finishes == 1 && Storage.lastUid == null)

    load("A")
    val settings = Settings("A")
    val file = ConfigPreload.getConfigFile("A")
    val original = "{\"modelFieldsMap\":{\"old\":{}}}"
    val imported = "{\"modelFieldsMap\":{\"new\":{}}}"
    file.writeText(original)
    ConfigPreload.memory = "unsaved before import"
    for (invalid in listOf("", "[]", "{}", "{\"modelFieldsMap\":null}", "{\"modelFieldsMap\":3}", "{\"modelFieldsMap\":{\"broken\":42}}")) {
        val backups = FileUtil.backups
        settings.applicationContext.contentResolver.text = invalid
        settings.importConfig("uri"); Main.settle()
        check(file.readText() == original && FileUtil.backups == backups)
        check(settings.canEditConfig() && ConfigPreload.memory == "unsaved before import")
    }
    load("A")
    settings.applicationContext.contentResolver.text = imported
    io.github.aw1y2z.sesame.util.SettingsAtomicBoundary.failReplace = true
    settings.importConfig("uri"); Main.settle()
    check(file.readText() == original && settings.canEditConfig())
    io.github.aw1y2z.sesame.util.SettingsAtomicBoundary.failReplace = false
    load("A")
    Storage.slow()
    settings.importConfig("uri")
    check(Storage.entered.await(2, TimeUnit.SECONDS) && SettingsIo.busy)
    check(file.readText() == original)
    Storage.release.countDown(); Main.settle()
    check(file.readText() == imported && settings.fieldsVersion == 1)
    ConfigPreload.fail = true
    settings.applicationContext.contentResolver.text = original
    settings.importConfig("uri"); Main.settle()
    check(file.readText() == original && ToastUtil.messages.last().contains("文件已导入") && !settings.canEditConfig())
    ConfigPreload.fail = false
    load("A")
    settings.applicationContext.contentResolver.output = null
    settings.exportConfig("uri"); Main.settle()
    check(ToastUtil.messages.last() == "导出失败！")
    // Actual ConfigV2.load + setModelFieldsMap and real IntegerModelField/Jackson:
    // object-as-integer survives generic envelope decoding, but typed conversion fails.
    LoadReplay.root = ConfigPreload.folder
    LoadReplay.setup()
    ConfigPreload.realLoad = true
    val restarts = HostBridge.restarts
    val typedBad = "{\"modelFieldsMap\":{\"Typed\":{\"count\":{\"value\":{\"bad\":1}}}}}"
    settings.applicationContext.contentResolver.text = typedBad
    settings.importConfig("uri"); Main.settle()
    check(LoadReplay.INSTANCE.isInit && LoadReplay.didLastLoadFail())
    check(HostBridge.restarts == restarts && !settings.canEditConfig())
    check(ToastUtil.messages.last().contains("文件已导入"))
    // Normal reopening retains legacy tolerant load and permits a later valid import.
    val recoveryPage = Editor("A").apply { forcePrepare = true }
    recoveryPage.prepare(); Main.settle()
    check(recoveryPage.canEditConfig())
    val typedGood = "{\"modelFieldsMap\":{\"Typed\":{\"count\":{\"value\":7}}}}"
    settings.applicationContext.contentResolver.text = typedGood
    settings.importConfig("uri"); Main.settle()
    check(!LoadReplay.didLastLoadFail() && LoadReplay.count() == 7 && HostBridge.restarts == restarts + 1)
    // Successful backup recovery still reports the original load failure to import.
    File(ConfigPreload.folder, "backup.json").writeText(typedGood)
    LoadReplay.failRead = true
    settings.importConfig("uri"); Main.settle()
    check(LoadReplay.INSTANCE.isInit && LoadReplay.didLastLoadFail() && LoadReplay.count() == 7)
    check(HostBridge.restarts == restarts + 1 && !settings.canEditConfig())
    // The next clean load resets the diagnostic; it is not persisted in config JSON.
    recoveryPage.forcePrepare = true; recoveryPage.prepare(); Main.settle()
    check(!LoadReplay.didLastLoadFail() && recoveryPage.canEditConfig())
    check(!file.readText().contains("lastLoadFailed"))
    ConfigPreload.folder.deleteRecursively()
    println("PASS real Kotlin settings queue/save/execute/selection/import/export: worker I/O, failure, UID, default and destroyed-screen guards")
}
'''
queue_body = queue[queue.index('internal object SettingsIo'):]
base_methods = '\n'.join(block(base, signature) for signature in (
    'private fun prepareEditor(', 'internal fun canEditConfig(', 'internal fun closeEditor(', 'internal fun runEditorIo(', 'internal fun saveConfig('))
for marker, value in {
    '@@QUEUE@@': queue_body, '@@BASE@@': base_methods,
    '@@LISTENER@@': block(base, 'private val ioListener:'),
    '@@EXECUTE@@': block(group, 'fun executeModule('), '@@CLOSE@@': block(group, 'fun saveAndFinish('),
    '@@IMPORT@@': block(settings, 'internal fun importConfig('), '@@EXPORT@@': block(settings, 'internal fun exportConfig('),
    '@@DELETE@@': block(settings, 'internal fun deleteConfig('),
    '@@EXPORT_NAME@@': block(settings, 'internal fun prepareExport('),
    '@@APPLY@@': block(selection, 'fun applyAndSave('),
}.items():
    code = code.replace(marker, value)
code = code.replace('android.net.Uri', 'String').replace('android.util.Log', 'AndroidLog').replace('util.AtomicConfigFile.write', 'util.SettingsAtomicBoundary.write')

# Real loader and typed setter; only model registration/files/logging/environment are isolated.
config_source = (UI.parents[1] / "data/ConfigV2.java").read_text(encoding="utf-8")
load_replay = r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.aw1y2z.sesame.data.ModelField;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.util.JsonUtil;
public class LoadReplay {
    static final String TAG = "load-replay";
    public static File root;
    public static boolean failRead;
    public static final LoadReplay INSTANCE = new LoadReplay();
    @JsonIgnore private boolean init;
    @JsonIgnore private static volatile boolean lastLoadFailed;
    static String lastSyncedText;
    final Map<String,ModelFields> modelFieldsMap = new LinkedHashMap<>();
    public void setInit(boolean value) {init=value;}
    @@INIT@@
    @@OUTCOME@@
    record ModelConfig(String code, ModelFields fields) {
        String getCode(){return code;} ModelFields getFields(){return fields;}
    }
    static class ModelTask {
        static final Map<String,ModelConfig> configs = new LinkedHashMap<>();
        static Map<String,ModelConfig> getModelConfigMap(){return configs;}
    }
    static class Model {static boolean isRegistrationComplete(){return true;}}
    public static void setup() {
        ModelFields fields = new ModelFields();
        fields.addField(new IntegerModelField("count", "count", 1));
        ModelTask.configs.put("Typed",new ModelConfig("Typed",fields));
        INSTANCE.setModelFieldsMap(null);
    }
    public static int count(){return (Integer)INSTANCE.modelFieldsMap.get("Typed").get("count").getValue();}
    public String toSaveStr(){return JsonUtil.toJsonString(Map.of("modelFieldsMap",modelFieldsMap));}
    public Set<String> collectKnownFieldPaths(){return null;}
    static void clampAllFields(){} static void captureBaseline(){}
    static class UserEntity {String getShowName(){return "A";}}
    static class UserIdMap {static UserEntity get(String uid){return null;}}
    static class StringUtil {static boolean isEmpty(String text){return text==null||text.isEmpty();}}
    static class Log {
        static void printStackTrace(Throwable t){} static void printStackTrace(String tag,Throwable t){}
        static void record(String text){} static void i(String tag,String text){}
    }
    static class FileUtil {
        static File getConfigV2File(String uid){return new File(root,uid+".json");}
        static File getDefaultConfigV2File(){return getConfigV2File("default");}
        static File findLatestBackupFile(String uid){return new File(root,"backup.json");}
        static String readFromFile(File file){
            if(failRead && file.getName().equals("A.json")){failRead=false;throw new IllegalStateException("injected read failure");}
            try{return Files.readString(file.toPath());}catch(IOException e){throw new IllegalStateException(e);}
        }
        static boolean write2File(String text,File file){try{Files.writeString(file.toPath(),text);return true;}catch(IOException e){throw new IllegalStateException(e);}}
        static void backupConfigV2WithRolling(String uid){}
    }
    @@SETTER@@
    @@LOAD@@
    @@RESTORE@@
    @@UNLOAD@@
}
'''
for marker, signature in {
    '@@INIT@@': 'public boolean isInit(', '@@OUTCOME@@': 'public static boolean didLastLoadFail(',
    '@@SETTER@@': 'public void setModelFieldsMap(', '@@LOAD@@': 'public static synchronized ConfigV2 load(',
    '@@RESTORE@@': 'private static boolean restoreFromBackup(', '@@UNLOAD@@': 'public static synchronized void unload(',
}.items():
    load_replay = load_replay.replace(marker, block(config_source, signature).replace('ConfigV2 load', 'LoadReplay load'))

def replay(code):
    with tempfile.TemporaryDirectory(prefix="sesame-settings-io-") as temporary:
        work = Path(temporary)
        atomic = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/AtomicConfigFile.java"
        account_folder = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/AccountFolderName.java"
        java_replay = work / "LoadReplay.java"
        java_replay.write_text(load_replay, encoding="utf-8")
        boundary = work / "SettingsAtomicBoundary.java"
        boundary.write_text('''package io.github.aw1y2z.sesame.util;
import java.io.*;
public final class SettingsAtomicBoundary {
    public static boolean failReplace;
    public static void write(String text, File file) throws IOException {
        AtomicConfigFile.write(text, file, new AtomicConfigFile.Operations() {
            public OutputStream open(File f) throws IOException { return AtomicConfigFile.FILES.open(f); }
            public void sync(OutputStream s) throws IOException { AtomicConfigFile.FILES.sync(s); }
            public byte[] read(File f) throws IOException { return AtomicConfigFile.FILES.read(f); }
            public boolean replace(File temp, File target) throws IOException {
                if (failReplace) throw new IOException("injected replace failure");
                return AtomicConfigFile.FILES.replace(temp, target);
            }
        });
    }
}''', encoding="utf-8")
        stdlib = artifact("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.0")
        compiler = [artifact("org.jetbrains.kotlin", name, version) for name, version in [
            ("kotlin-compiler-embeddable", "2.4.0"), ("kotlin-reflect", "1.6.10"),
            ("kotlin-script-runtime", "2.4.0"), ("kotlin-build-tools-api", "2.4.0")]]
        compiler += [stdlib, artifact("org.jetbrains", "annotations", "13.0"),
                     artifact("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2")]
        path = work / "SettingsCheck.kt"
        path.write_text(code, encoding="utf-8")
        jackson = [artifact("com.fasterxml.jackson.core", name) for name in ("jackson-databind", "jackson-core", "jackson-annotations")]
        classes = ROOT / "app/build/intermediates/javac/normalDebug/compileNormalDebugJavaWithJavac/classes"
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            sdk = next(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\")
                       for line in (ROOT / "local.properties").read_text().splitlines() if line.startswith("sdk.dir="))
        android = next((Path(sdk) / "platforms").glob("android-37*/android.jar"))
        cp = os.pathsep.join(map(str, [work, stdlib, classes, android] + jackson))
        subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-d", str(work), str(atomic), str(boundary), str(java_replay), str(account_folder)], check=True, timeout=30)
        subprocess.run(["java", "-Xmx256m", "-cp", os.pathsep.join(map(str, compiler)),
                        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect",
                        "-classpath", cp, "-d", str(work), str(path)], check=True, timeout=90)
        subprocess.run(["java", "-Xmx128m", "-cp", cp, "SettingsCheckKt"], check=True, timeout=30)

replay(code)
