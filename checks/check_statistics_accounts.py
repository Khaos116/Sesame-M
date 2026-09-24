"""Compile real account-state stores with isolated files; switches must not mix users."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
LOMBOK = next((CACHE / "org.projectlombok/lombok").glob("*/*/lombok-*.jar"))
JSON = next((CACHE / "org.json/json").glob("*/*/json-*.jar"))
JACKSON = [next((CACHE / "com.fasterxml.jackson.core" / name / "2.18.2").glob(f"*/{name}-2.18.2.jar"))
           for name in ("jackson-databind", "jackson-core", "jackson-annotations")]

with tempfile.TemporaryDirectory(prefix="sesame-statistics-") as directory:
    base = Path(directory)

    def write(name, source):
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")

    write("io/github/aw1y2z/sesame/util/Statistics.java", (SOURCE / "util/Statistics.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/entity/FriendWatch.java", (SOURCE / "entity/FriendWatch.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/util/idMap/AutoBlackListMap.java", (SOURCE / "util/idMap/AutoBlackListMap.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/entity/IdAndName.java", """package io.github.aw1y2z.sesame.entity;
public class IdAndName implements Comparable<IdAndName> { public String id, name; public int compareTo(IdAndName other) { return id.compareTo(other.id); } }""")
    write("android/content/Context.java", "package android.content; public class Context { public String getString(int id) { return \"\"; } }")
    write("androidx/annotation/Keep.java", "package androidx.annotation; public @interface Keep {}")
    write("io/github/aw1y2z/sesame/R.java", """package io.github.aw1y2z.sesame;
public final class R { public static final class string {
    public static final int year=1, month=2, day=3, collected=4, helped=5, watered=6, wateredcount=7, wateringcount=8;
} }""")
    write("io/github/aw1y2z/sesame/util/AccountFolderName.java", """package io.github.aw1y2z.sesame.util;
public class AccountFolderName { public static boolean isValidUid(String uid) { return uid != null && uid.matches("[A-Za-z0-9_-]{1,128}"); } }""")
    write("io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", """package io.github.aw1y2z.sesame.util.idMap;
public class UserIdMap { public static String uid; public static String getCurrentUid() { return uid; } public static String getMaskName(String id) { return id; } }""")
    write("io/github/aw1y2z/sesame/util/FileUtil.java", """package io.github.aw1y2z.sesame.util;
import java.io.*; import java.nio.file.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class FileUtil {
    public static File root;
    public static File getStatisticsFile(String uid) { return new File(new File(new File(root, "config"), uid), "statistics.json"); }
    public static File getStatisticsFile() { return getStatisticsFile(UserIdMap.getCurrentUid()); }
    public static File getFriendWatchFile(String uid) { return new File(new File(new File(root, "config"), uid), "friendWatch.json"); }
    public static File getFriendWatchFile() { return getFriendWatchFile(UserIdMap.getCurrentUid()); }
    public static File getAutoBlackListMapFile(String uid) { return new File(new File(new File(root, "config"), uid), "AutoBlackList.json"); }
    public static File getAutoBlackListMapFile() { return getAutoBlackListMapFile(UserIdMap.getCurrentUid()); }
    public static String readFromFile(File file) { try { return file.isFile() ? Files.readString(file.toPath()) : ""; } catch (Exception e) { throw new RuntimeException(e); } }
    public static boolean write2File(String text, File file) { try { file.getParentFile().mkdirs(); Files.writeString(file.toPath(), text); return true; } catch (Exception e) { throw new RuntimeException(e); } }
}""")
    write("io/github/aw1y2z/sesame/util/JsonUtil.java", """package io.github.aw1y2z.sesame.util;
import com.fasterxml.jackson.databind.ObjectMapper;
public class JsonUtil {
    public static ObjectMapper copyMapper() { return new ObjectMapper(); }
    public static String toFormatJsonString(Object value) { try { return copyMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); } }
    public static boolean isRewriteLossless(String before, String after) { return true; }
    public static <T> T parseObject(String body, com.fasterxml.jackson.core.type.TypeReference<T> type) { try { return copyMapper().readValue(body, type); } catch (Exception e) { throw new RuntimeException(e); } }
    public static String toJsonString(Object value) { return toFormatJsonString(value); }
}""")
    write("io/github/aw1y2z/sesame/util/Log.java", """package io.github.aw1y2z.sesame.util;
public class Log { public static void i(String a, String b) {} public static void printStackTrace(String a, Throwable b) { throw new RuntimeException(b); }
public static void printStackTrace(Throwable b) { throw new RuntimeException(b); } public static void err(String a, String b, Throwable c) { throw new RuntimeException(c); }
public static void system(String a, String b) {} public static void debug(String a) {} }""")
    write("io/github/aw1y2z/sesame/util/TimeUtil.java", """package io.github.aw1y2z.sesame.util;
public class TimeUtil { public static java.util.Calendar getNow() { return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8")); }
public static String getDateStr() { return "2026-09-24"; } }""")
    write("io/github/aw1y2z/sesame/util/MyUtils.java", """package io.github.aw1y2z.sesame.util;
public class MyUtils { public static org.json.JSONObject newJSONObject(String text) { try { return new org.json.JSONObject(text); } catch (Exception e) { return new org.json.JSONObject(); } }
public static java.util.Calendar getInstance() { return TimeUtil.getNow(); } }""")
    write("io/github/aw1y2z/sesame/util/StringUtil.java", """package io.github.aw1y2z.sesame.util;
public class StringUtil { public static boolean isEmpty(String text) { return text == null || text.isEmpty(); } }""")
    write("io/github/aw1y2z/sesame/util/StatisticsAccountCheck.java", """package io.github.aw1y2z.sesame.util;
import java.nio.file.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap; import io.github.aw1y2z.sesame.entity.FriendWatch;
public class StatisticsAccountCheck {
    static void eq(int expected) { assert Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.COLLECTED) == expected; }
    public static void main(String[] args) throws Exception {
        FileUtil.root = Path.of(args[0]).toFile();
        Files.writeString(Path.of(args[0], "statistics.json"), "{\\"day\\":{\\"collected\\":99}}");
        UserIdMap.uid = "A";
        Statistics.load("A"); Statistics.updateDay(TimeUtil.getNow()); eq(0); Statistics.addData(Statistics.DataType.COLLECTED, 7); Statistics.save();
        UserIdMap.uid = "B";
        Statistics.load("B"); Statistics.updateDay(TimeUtil.getNow()); eq(0); Statistics.addData(Statistics.DataType.COLLECTED, 3); Statistics.save();
        UserIdMap.uid = "A"; Statistics.load("A"); eq(7);
        UserIdMap.uid = "B"; Statistics.load("B"); eq(3);
        assert Files.readString(Path.of(args[0], "statistics.json")).contains("99");
        assert Files.isRegularFile(FileUtil.getStatisticsFile("A").toPath());
        assert Files.isRegularFile(FileUtil.getStatisticsFile("B").toPath());
        UserIdMap.uid = "A"; FriendWatch.load(); FriendWatch.friendWatch("friend", 5); FriendWatch.save();
        UserIdMap.uid = "B"; FriendWatch.load(); assert FriendWatch.getList("B").isEmpty();
        FriendWatch.friendWatch("friend", 3); FriendWatch.save();
        assert FriendWatch.getList("A").get(0).name.contains("总收:5");
        assert FriendWatch.getList("B").get(0).name.contains("总收:3");
        UserIdMap.uid = "A"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.put("task", "A");
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.save();
        UserIdMap.uid = "B"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        assert io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.get("task") == null;
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.put("task", "B");
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.save();
        UserIdMap.uid = "A"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        assert "A".equals(io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.get("task"));
        System.out.println("PASS: statistics, friend watch and auto-blacklist are isolated by UID; legacy totals remain untouched");
    }
}""")
    cp = os.pathsep.join(map(str, [LOMBOK, JSON, *JACKSON]))
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", cp, "-processorpath", str(LOMBOK),
                    "-d", directory, *map(str, base.rglob("*.java"))], check=True)
    subprocess.run(["java", "-ea", "-cp", directory + os.pathsep + cp,
                    "io.github.aw1y2z.sesame.util.StatisticsAccountCheck", directory], check=True)

file_util = (SOURCE / "util/FileUtil.java").read_text(encoding="utf-8")
activity = (SOURCE / "ui/miuix/MiuixMainActivity.kt").read_text(encoding="utf-8")
hook = (SOURCE / "hook/ApplicationHook.java").read_text(encoding="utf-8")
assert 'getFile(getAccountDataDirectory(userId), "statistics.json")' in file_util
assert 'getFile(getAccountDataDirectory(userId), "friendWatch.json")' in file_util
assert 'getFile(getAccountDataDirectory(userId), "AutoBlackList.json")' in file_util
assert 'if (!AccountFolderName.isValidUid(userId))' in file_util
assert 'String userId = uidOfLogFolder(folder);' in file_util
assert "FileUtil.getPublishedUserId()" in activity
assert "Statistics.load(userId)" in activity
assert "if (folder != previousFolder)" in activity
assert 'FriendWatch.getList(userId)' in (SOURCE / "ui/miuix/MiuixFriendStatsActivity.kt").read_text(encoding="utf-8")
assert hook.index("UserIdMap.initUser(userId)") < hook.index("Statistics.load();", hook.index("UserIdMap.initUser(userId)")) < hook.index("Model.initAllModel();", hook.index("UserIdMap.initUser(userId)"))
