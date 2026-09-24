"""Run after :app:compileNormalDebugJavaWithJavac; uses real model fields/Jackson and isolated I/O doubles."""
from pathlib import Path
import os
import re
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
    ("@@AUTO@@", "data/AppConfig.java", "    public static synchronized boolean shouldAutoPuzzleSlider("),
    ("@@LEGACY_BOOL@@", "data/AppConfig.java", "    private static boolean legacyModelBoolean("),
    ("@@RANK@@", "model/task/antFarm/AntFarm.java", "    private boolean isCompetitionRoundActive("),
    ("@@BATCH@@", "model/task/antOrchard/AntOrchard.java", "    private static boolean shouldBatchSpread("),
    ("@@RELOAD@@", "hook/ApplicationHook.java", "    private void scheduleAccountReload("),
    ("@@LIFECYCLE@@", "data/task/TaskLifecycle.java", "public final class TaskLifecycle"),
):
    code = code.replace(key, method(path, signature).replace("public final class TaskLifecycle", "static final class TaskLifecycle"))
ui = (SOURCE / "ui/miuix/MiuixSettingsActivity.kt").read_text(encoding="utf-8")
assert "val current = field.configValue.toIntOrNull()" in ui
assert "field.setConfigValue(parsed.toString())" in ui
selection_ui = (SOURCE / "ui/miuix/MiuixSelectionEditActivity.kt").read_text(encoding="utf-8")
assert "sel = setOf(opt.id)" in selection_ui, "single selection must replace the previous ID"
assert "sel.forEach { id -> csmf?.add(id, counts[id] ?: 1) }" in selection_ui, \
    "save must only persist counts for currently selected IDs"
with tempfile.TemporaryDirectory(prefix="sesame-merge-check-") as temporary:
    source = Path(temporary) / "MergeConfigCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", temporary, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + classpath, "MergeConfigCheck"], check=True)

# Exercise the actual preload guard with isolated model/config storage.
preload_check = '''
import java.util.*;
public class PreloadCheck {
    static class Model {
        static Map<String, String> fields = new HashMap<>();
        static Map<String, String> getModelConfigMap() { return fields; }
        static void initAllModel() { fields.clear(); fields.put("field", "default"); }
    }
    static class ConfigV2 {
        static final ConfigV2 INSTANCE = new ConfigV2();
        boolean init;
        boolean isInit() { return init; }
    }
    static class StringUtil {
        static boolean isEmpty(String value) { return value == null || value.isEmpty(); }
    }
    static class UserIdMap {
        static String uid;
        static String getCurrentUid() { return uid; }
    }
    static int loads;
    static void prepare(String uid) {
        loads++;
        UserIdMap.uid = StringUtil.isEmpty(uid) ? null : uid;
        ConfigV2.INSTANCE.init = true;
        Model.fields.put("field", "saved");
    }
    @@GUARD@@
    public static void main(String[] args) {
        ensurePrepared("A");
        assert loads == 1 && Model.fields.get("field").equals("saved");
        Model.fields.put("field", "unsaved");
        ensurePrepared("A");
        assert loads == 1 && Model.fields.get("field").equals("unsaved");
        ensurePrepared("B");
        assert loads == 2 && UserIdMap.uid.equals("B");
        Model.fields.clear(); // Models lost, even if config's init flag remains set.
        ensurePrepared("B");
        assert loads == 3 && !Model.fields.isEmpty();
        ConfigV2.INSTANCE.init = false;
        ensurePrepared("B");
        assert loads == 4;
        ensurePrepared(null);
        ensurePrepared("");
        assert loads == 5 && UserIdMap.uid == null;
        System.out.println("PASS: cold restore, account change, default config and unsaved edits");
    }
}
'''.replace("@@GUARD@@", method("data/ConfigPreload.java", "    public static void ensurePrepared("))
for activity in ("MiuixGroupFieldsActivity", "MiuixSelectionEditActivity"):
    on_create = method(f"ui/miuix/{activity}.kt", "    override fun onCreate(")
    assert on_create.index("ConfigPreload.ensurePrepared(userId)") < on_create.index("setAppContent")
with tempfile.TemporaryDirectory(prefix="sesame-preload-check-") as temporary:
    source = Path(temporary) / "PreloadCheck.java"
    source.write_text(preload_check, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temporary, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary, "PreloadCheck"], check=True)

# Run the real Kotlin initial-state parsing and save branches, without Compose.
selection_path = "ui/miuix/MiuixSelectionEditActivity.kt"
selection_check = '''
data class IdAndName(val id: String)
data class KVNode<K, V>(val key: K, val value: V)
open class Field(val type: String, val code: String, var value: Any?) {
    val expandValue = emptyList<IdAndName>()
    fun setObjectValue(v: Any?) { value = v }
}
class SelectAndCountModelField(code: String, v: Map<String, Int>, val valueRangeMin: Float = 0f) : Field("SELECT_AND_COUNT", code, v) {
    fun clear() { value = emptyMap<String, Int>() }
    fun add(id: String, count: Int) { value = (value as Map<String, Int>) + (id to count) }
}
class SelectAndCountOneModelField(v: KVNode<String, Int>) : Field("SELECT_AND_COUNT_ONE", "single", v) {
    fun clear() { value = null }
    fun add(id: String, count: Int) { value = KVNode(id, count) }
}
fun <T> remember(vararg keys: Any?, block: () -> T): T = block()
fun edit(liveField: Field, add: String? = null, newCount: Int? = null) {
    val field = liveField
    val modelCode = "model"
    val smf = liveField
    @@WITH_COUNT@@
    check(withCount == liveField.type.startsWith("SELECT_AND_COUNT"))
    @@USE_INPUT_BOX@@
    @@DEFAULT_COUNT@@
    // 默认构造函数下 valueRangeMin 是 0（真实生产代码同款），非 useInputBox 字段绝不能把新勾选默认值也带成 0，
    // 否则等于勾了等于没勾（业务侧把 0 次当"今日已达上限"直接跳过）
    if (!useInputBox) check(defaultCount == 1) { "非 useInputBox 字段的默认值必须是 1，实际是 $defaultCount" }
    @@INITIAL@@
    var sel = initialState.second
    var counts = sel.associateWith { initialState.third[it] ?: 1 }
    if (add != null) {
        sel = if (liveField.type == "SELECT_AND_COUNT_ONE") setOf(add) else sel + add
        counts = counts + (add to (newCount ?: defaultCount))
    }
    val configField = liveField
    @@SAVE@@
}
fun main() {
    for (code in listOf("waterFriendList", "wateredFriendList", "orchardSpreadManureSceneList",
                        "feedFriendAnimalList", "gameCenterBuyMallItemList", "rpcRequestList")) {
        val field = SelectAndCountModelField(code, mapOf("A" to 7, "B" to 3, "zero" to 0, "large" to 200))
        edit(field, "C")
        check(field.value == mapOf("A" to 7, "B" to 3, "zero" to 0, "large" to 200, "C" to 1))
        edit(field, "B", 9)
        check((field.value as Map<*, *>)["B"] == 9)
    }
    val single = SelectAndCountOneModelField(KVNode("A", 8))
    edit(single)
    check(single.value == KVNode("A", 8))
    edit(single, "B", 6)
    check(single.value == KVNode("B", 6))
    edit(Field("SELECT", "plain", setOf("A")))
    edit(Field("SELECT_ONE", "plainSingle", "A"))
    // 合种浇水（useInputBox）：valueRangeMin=0 是本来就允许的默认值，跟其它字段区分开验证
    val cooperate = SelectAndCountModelField("cooperateWaterList", mapOf("A" to 5))
    edit(cooperate, "C")
    check((cooperate.value as Map<*, *>)["C"] == 0) { "合种浇水新勾选默认值应为 0，实际是 ${(cooperate.value as Map<*, *>)["C"]}" }
    println("PASS: all count fields retain counts, edit counts and replace single selection")
}
'''.replace("@@WITH_COUNT@@", re.search(r"val withCount = [^\n]+", selection_ui)[0]) \
    .replace("@@USE_INPUT_BOX@@", re.search(r"val useInputBox = [^\n]+", selection_ui)[0]) \
    .replace("@@DEFAULT_COUNT@@", re.search(r"val defaultCount = [^\n]+", selection_ui)[0]) \
    .replace("@@INITIAL@@", method(selection_path, "    val initialState = remember(")) \
    .replace("@@SAVE@@", method(selection_path, "        when (configField.type)"))
assert "if (withCount && isChecked)" in selection_ui
coordinates = [("org.jetbrains.kotlin", name, "2.4.0") for name in
               ("kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-stdlib",
                "kotlin-script-runtime", "kotlin-daemon-embeddable")] + [
    ("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"),
    ("org.jetbrains", "annotations", "13.0")]
kotlin_classpath = os.pathsep.join(str(next((cache / group / name / version).glob(f"*/{name}-{version}.jar")))
                                 for group, name, version in coordinates)
with tempfile.TemporaryDirectory(prefix="sesame-selection-check-") as temporary:
    source = Path(temporary) / "SelectionCheck.kt"
    source.write_text(selection_check, encoding="utf-8")
    subprocess.run(["java", "-cp", kotlin_classpath, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-no-stdlib", "-no-reflect", "-classpath", kotlin_classpath,
                    "-d", temporary, str(source)], check=True)
    subprocess.run(["java", "-cp", temporary + os.pathsep + kotlin_classpath, "SelectionCheckKt"], check=True)
