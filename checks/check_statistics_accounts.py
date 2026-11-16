"""Compile real account-state stores with isolated files; switches must not mix users."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
LOMBOK = next((CACHE / "org.projectlombok/lombok").glob("*/*/lombok-*.jar"))
JSON = next((CACHE / "org.json/json").glob("*/*/json-*.jar"))
dependencies = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
JACKSON = []
for name in ("jackson-databind", "jackson-core", "jackson-annotations"):
    version = re.search(r"name: '" + name + r"', version: '([^']+)'", dependencies).group(1)
    JACKSON.append(next((CACHE / "com.fasterxml.jackson.core" / name / version).glob(f"*/{name}-{version}.jar")))

with tempfile.TemporaryDirectory(prefix="sesame-statistics-") as directory:
    base = Path(directory)

    def write(name, source):
        path = base / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding="utf-8")

    write("io/github/aw1y2z/sesame/util/Statistics.java", (SOURCE / "util/Statistics.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/entity/FriendWatch.java", (SOURCE / "entity/FriendWatch.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/entity/UserEntity.java", (SOURCE / "entity/UserEntity.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/util/idMap/AutoBlackListMap.java", (SOURCE / "util/idMap/AutoBlackListMap.java").read_text(encoding="utf-8"))
    write("io/github/aw1y2z/sesame/entity/IdAndName.java", """package io.github.aw1y2z.sesame.entity;
public class IdAndName implements Comparable<IdAndName> { public String id, name; public int compareTo(IdAndName other) { return id.compareTo(other.id); } }""")
    write("android/os/Handler.java", "package android.os; public class Handler {public Runnable pending;public boolean post(Runnable r){pending=r;return true;}public void run(){Runnable r=pending;pending=null;r.run();}}")
    for cls in ("UserIndependentCache", "contact.data.AliAccountDaoOp"):
        package="com.alipay.mobile.socialcommonsdk.bizdata"+(".contact.data" if "." in cls else "")
        name=cls.split(".")[-1]
        write(package.replace(".","/")+"/"+name+".java", "package "+package+";public class "+name+" {}")
    write("io/github/aw1y2z/sesame/hook/ApplicationHook.java", """package io.github.aw1y2z.sesame.hook;
public class ApplicationHook {public static volatile String hostUid="A";public static android.os.Handler handler=new android.os.Handler();public static android.os.Handler getMainHandler(){return handler;}public static ClassLoader getClassLoader(){return ApplicationHook.class.getClassLoader();}public static String getUserId(){return hostUid;}}""")
    write("io/github/aw1y2z/sesame/util/XHelpers.java", """package io.github.aw1y2z.sesame.util;
public class XHelpers {public static boolean fail;public static java.util.List<?> friends=java.util.List.of();
public static Object callStaticMethod(Class<?> c,String n,Object...args){return new Object();}
public static java.util.concurrent.CountDownLatch entered,release;public static boolean changeUid;
public static Object callMethod(Object o,String n,Object...args){assert Thread.currentThread()!=FileUtil.mainThread:"DAO work ran on main";assert !Thread.holdsLock(io.github.aw1y2z.sesame.util.idMap.UserIdMap.class):"DAO holds UserIdMap monitor";if(entered!=null){entered.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}if(changeUid)io.github.aw1y2z.sesame.util.idMap.UserIdMap.uid="B";if(fail)throw new IllegalStateException("DAO failure");return friends;}
public static java.lang.reflect.Field findField(Class<?> c,String n)throws Exception{return c.getField(n);}
public static class Friend {public String userId="friend",account="account",name="name",nickName="Friend",remarkName="remark";public Integer friendStatus=1;}}
""")
    write("android/content/Context.java", "package android.content; public class Context { public String getString(int id) { return \"\"; } }")
    write("androidx/annotation/Keep.java", "package androidx.annotation; public @interface Keep {}")
    write("io/github/aw1y2z/sesame/R.java", """package io.github.aw1y2z.sesame;
public final class R { public static final class string {
    public static final int year=1, month=2, day=3, collected=4, helped=5, watered=6, wateredcount=7, wateringcount=8;
} }""")
    write("io/github/aw1y2z/sesame/util/AccountFolderName.java", """package io.github.aw1y2z.sesame.util;
public class AccountFolderName { public static boolean isValidUid(String uid) { return uid != null && uid.matches("[A-Za-z0-9_-]{1,128}"); } }""")
    user_map = """package io.github.aw1y2z.sesame.util.idMap;
import java.util.*;import java.io.File;import java.lang.reflect.Field;import android.os.Handler;import io.github.aw1y2z.sesame.hook.ApplicationHook;import com.fasterxml.jackson.core.type.TypeReference;
import io.github.aw1y2z.sesame.entity.UserEntity;import io.github.aw1y2z.sesame.util.*;
public class UserIdMap {
 public static String uid;public static boolean realMap;private static boolean friendListLoaded;
 private static final Object userInitLock=new Object();
 private static final Map<String,UserEntity> userMap=new HashMap<>();
 public static String getCurrentUid(){return uid;}
 public static String getMaskName(String id){return realMap?realMaskName(id):id;}
 static void setCurrentUserId(String id){uid=id;}
 static UserEntity buildSelfFromAccountModel(){return new UserEntity(uid,"self-account",null,null,"self",null);}
 static UserEntity mergeSelfEntity(UserEntity a,UserEntity b){return a==null?b:a;}
 @@INIT@@ @@SAVE@@ @@SAVESELF@@ @@LOADED@@ @@LOAD@@ @@ADD@@ @@UNLOAD@@ @@MASK@@
}"""
    for tag, signature in {"INIT":"public static void initUser(","SAVE":"public synchronized static boolean save(","SAVESELF":"public synchronized static boolean saveSelf(","LOADED":"public static boolean isFriendListLoaded(","LOAD":"public synchronized static void load(",
                           "ADD":"public synchronized static void add(","UNLOAD":"public synchronized static void unload(",
                           "MASK":"public static String getMaskName("}.items():
        value = method("util/idMap/UserIdMap.java", signature)
        if tag == "MASK": value = value.replace("getMaskName(String userId)", "realMaskName(String userId)")
        if tag == "LOADED": value = value.replace("return friendListLoaded;", "return realMap && friendListLoaded;")
        user_map = user_map.replace("@@"+tag+"@@", value)
    write("io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", user_map)
    write("io/github/aw1y2z/sesame/util/FileUtil.java", """package io.github.aw1y2z.sesame.util;
import java.io.*; import java.nio.file.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class FileUtil {
    public static File root;
    public static int writes;public static Thread mainThread;public static boolean requireBackground;
    public static void checkThread(){assert !requireBackground||Thread.currentThread()!=mainThread:"init file work ran on main";}
    public static int publishes;public static String publishedUid;
    public static void publishCurrentLogUser(String uid){checkThread();publishes++;publishedUid=uid;}
    public static File getSelfIdFile(String uid){return new File(new File(new File(root,"config"),uid),"self.json");}
    public static File getStatisticsFile(String uid) { return new File(new File(new File(root, "config"), uid), "statistics.json"); }
    public static File getStatisticsFile() { return getStatisticsFile(UserIdMap.getCurrentUid()); }
    public static File getFriendWatchFile(String uid) { return new File(new File(new File(root, "config"), uid), "friendWatch.json"); }
    public static File getFriendWatchFile() { return getFriendWatchFile(UserIdMap.getCurrentUid()); }
    public static File getFriendIdMapFile(String uid) { return new File(new File(new File(root, "config"), uid), "friendIdMap.json"); }
    public static File getAutoBlackListMapFile(String uid) { return new File(new File(new File(root, "config"), uid), "AutoBlackList.json"); }
    public static File getAutoBlackListMapFile() { return getAutoBlackListMapFile(UserIdMap.getCurrentUid()); }
    public static String readFromFile(File file) {checkThread(); try { return file.isFile() ? Files.readString(file.toPath()) : ""; } catch (Exception e) { throw new RuntimeException(e); } }
    public static boolean write2File(String text, File file) {checkThread(); try { file.getParentFile().mkdirs(); Files.writeString(file.toPath(), text); writes++; return true; } catch (Exception e) { throw new RuntimeException(e); } }
}""")
    write("io/github/aw1y2z/sesame/util/JsonUtil.java", """package io.github.aw1y2z.sesame.util;
import com.fasterxml.jackson.databind.ObjectMapper;
public class JsonUtil {
    public static int failFileReads;
    public static ObjectMapper copyMapper() { return new ObjectMapper(){@Override public <T>T readValue(java.io.File file, com.fasterxml.jackson.core.type.TypeReference<T> type)throws java.io.IOException{if(failFileReads>0){failFileReads--;throw new java.io.IOException("transient read failure");}return super.readValue(file,type);}}; }
    public static String toFormatJsonString(Object value) { try { return copyMapper().writerWithDefaultPrettyPrinter().writeValueAsString(value); } catch (Exception e) { throw new RuntimeException(e); } }
    public static boolean isRewriteLossless(String before, String after) { return true; }
    public static <T> T parseObject(String body, com.fasterxml.jackson.core.type.TypeReference<T> type) { try { return copyMapper().readValue(body, type); } catch (Exception e) { throw new RuntimeException(e); } }
    public static String toJsonString(Object value) { return toFormatJsonString(value); }
}""")
    write("io/github/aw1y2z/sesame/util/Log.java", """package io.github.aw1y2z.sesame.util;
public class Log { public static boolean expectedParseFailure; public static void record(String s){} public static void i(String a, String b) {} public static void i(String a){} public static void printStackTrace(String a, Throwable b) { if(!expectedParseFailure)throw new RuntimeException(b); }
public static void printStackTrace(Throwable b) { if(!expectedParseFailure)throw new RuntimeException(b); } public static void err(String a, String b, Throwable c) { throw new RuntimeException(c); }
public static void system(String a, String b) {} public static void debug(String a) {} }""")
    write("io/github/aw1y2z/sesame/util/TimeUtil.java", """package io.github.aw1y2z.sesame.util;
public class TimeUtil { public static java.util.Calendar getNow() { return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8")); }
public static String getDateStr() { return "2026-09-24"; } }""")
    write("io/github/aw1y2z/sesame/util/MyUtils.java", """package io.github.aw1y2z.sesame.util;
public class MyUtils { public static org.json.JSONObject newJSONObject() {return new org.json.JSONObject();} public static org.json.JSONObject newJSONObject(String text) { try { return new org.json.JSONObject(text); } catch (Exception e) { return new org.json.JSONObject(); } }
public static void cacheUserName(String id,String name) {}
public static java.util.Calendar getInstance() { return TimeUtil.getNow(); } }""")
    write("io/github/aw1y2z/sesame/util/MessageUtil.java", """package io.github.aw1y2z.sesame.util;
public class MessageUtil { public static org.json.JSONObject newJSONObject(String text) {try{return new org.json.JSONObject(text);}catch(Exception e){return null;}} }""")
    write("io/github/aw1y2z/sesame/util/StringUtil.java", """package io.github.aw1y2z.sesame.util;
public class StringUtil { public static boolean isEmpty(String text) { return text == null || text.isEmpty(); } }""")
    write("io/github/aw1y2z/sesame/util/StatisticsAccountCheck.java", """package io.github.aw1y2z.sesame.util;
import java.nio.file.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap; import io.github.aw1y2z.sesame.entity.FriendWatch;
public class StatisticsAccountCheck {
    static void eq(int expected) { assert Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.COLLECTED) == expected; }
    static Thread initBackground(String uid, java.util.concurrent.atomic.AtomicReference<Throwable> failure){
        Thread worker=new Thread(()->{try{UserIdMap.initUser(uid);}catch(Throwable t){failure.set(t);}},"init-worker");worker.start();return worker;
    }
    static void initBackground(String uid)throws Exception{
        FileUtil.requireBackground=true;
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            Thread worker=initBackground(uid,failure);worker.join(2000);assert !worker.isAlive():"init did not finish";
            assert failure.get()==null:failure.get();
            assert io.github.aw1y2z.sesame.hook.ApplicationHook.handler.pending==null:"background init posted DAO work to main";
        } finally {FileUtil.requireBackground=false;}
    }
    public static void main(String[] args) throws Exception {
        FileUtil.mainThread=Thread.currentThread();
        FileUtil.root = Path.of(args[0]).toFile();
        Files.writeString(Path.of(args[0], "statistics.json"), "{\\"day\\":{\\"collected\\":99}}");
        UserIdMap.uid = "A";
        Statistics.load("A"); Statistics.updateDay(TimeUtil.getNow()); eq(0); Statistics.addData(Statistics.DataType.COLLECTED, 7); Statistics.save();
        assert Statistics.getRoundCollected() == 7;
        UserIdMap.uid = "B";
        Statistics.load("B"); Statistics.updateDay(TimeUtil.getNow()); eq(0); Statistics.addData(Statistics.DataType.COLLECTED, 3); Statistics.save();
        assert Statistics.getRoundCollected() == 3;
        UserIdMap.uid = "A"; Statistics.load("A"); eq(7);
        assert Statistics.getRoundCollected() == 0;
        UserIdMap.uid = "B"; Statistics.load("B"); eq(3);
        assert Statistics.setDayCollectedFromServer(8)==5;eq(8);Statistics.save();
        assert Statistics.setDayCollectedFromServer(-1)==0;eq(8);
        assert Statistics.getData(Statistics.TimeType.MONTH,Statistics.DataType.COLLECTED)==8;
        UserIdMap.uid="A";assert Statistics.setDayCollectedFromServer(11)==4;eq(11);Statistics.save();
        Statistics a=Statistics.readFrom("A"), b=Statistics.readFrom("B");
        assert a.dataOf(Statistics.TimeType.DAY,Statistics.DataType.COLLECTED)==11;
        assert b.dataOf(Statistics.TimeType.DAY,Statistics.DataType.COLLECTED)==8;
        assert Statistics.aggregate(java.util.List.of(a,b)).dataOf(Statistics.TimeType.DAY,Statistics.DataType.COLLECTED)==19;
        eq(11); // Reading every account for UI must not replace or modify the singleton.
        assert Statistics.readFrom("../invalid").dataOf(Statistics.TimeType.DAY,Statistics.DataType.COLLECTED)==0;
        UserIdMap.uid="B";Statistics.load("B");eq(8);
        assert Files.readString(Path.of(args[0], "statistics.json")).contains("99");
        assert Files.isRegularFile(FileUtil.getStatisticsFile("A").toPath());
        assert Files.isRegularFile(FileUtil.getStatisticsFile("B").toPath());
        UserIdMap.uid = "A"; FriendWatch.load(); FriendWatch.friendWatch("friend", 5); FriendWatch.save();
        UserIdMap.uid = "B"; FriendWatch.load(); assert FriendWatch.getList("B").isEmpty();
        FriendWatch.friendWatch("friend", 3); FriendWatch.save();
        assert FriendWatch.getList("A").get(0).getAllGet() == 5;
        assert FriendWatch.getList("B").get(0).getAllGet() == 3;
        String originalStats=Files.readString(FileUtil.getStatisticsFile("A").toPath());
        Files.writeString(FileUtil.getStatisticsFile("A").toPath(), "corrupt statistics");
        UserIdMap.uid="A";Log.expectedParseFailure=true;Statistics.load("A");Log.expectedParseFailure=false;
        Statistics.addData(Statistics.DataType.COLLECTED,2);Statistics.save();
        assert Files.readString(FileUtil.getStatisticsFile("A").toPath()).equals("corrupt statistics");
        UserIdMap.uid="B";Statistics.load("B");Statistics.save();eq(8);
        Files.writeString(FileUtil.getStatisticsFile("A").toPath(),originalStats);UserIdMap.uid="A";Statistics.load("A");eq(11);
        String originalFriends=Files.readString(FileUtil.getFriendWatchFile("A").toPath());
        Files.writeString(FileUtil.getFriendWatchFile("A").toPath(),"corrupt friends");
        assert !FriendWatch.load();int writes=FileUtil.writes;FriendWatch.friendWatch("friend",99);FriendWatch.save();
        assert FriendWatch.getList("A").isEmpty()&&FileUtil.writes==writes;
        assert Files.readString(FileUtil.getFriendWatchFile("A").toPath()).equals("corrupt friends");
        UserIdMap.uid="B";FriendWatch.save();assert FileUtil.writes>writes:"corrupt A prevented valid B save";
        Files.writeString(FileUtil.getFriendWatchFile("A").toPath(),originalFriends);UserIdMap.uid="A";assert FriendWatch.load();
        assert FriendWatch.getList("A").get(0).getAllGet()==5;
        UserIdMap.uid = "A"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.put("task", "A");
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.save();
        UserIdMap.uid = "B"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        assert io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.get("task") == null;
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.put("task", "B");
        io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.save();
        UserIdMap.uid = "A"; io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.ensureLoaded();
        assert "A".equals(io.github.aw1y2z.sesame.util.idMap.AutoBlackListMap.get("task"));
        UserIdMap.realMap=true;UserIdMap.uid="A";
        Path friendFile=FileUtil.getFriendIdMapFile("A").toPath();
        String friendJson="{\\"friend\\":{\\"userId\\":\\"friend\\",\\"nickName\\":\\"Friend\\"}}";
        Files.writeString(friendFile,friendJson);UserIdMap.load("A");assert UserIdMap.isFriendListLoaded();
        FriendWatch.load();FriendWatch.friendWatch("friend",2);FriendWatch.save();assert FriendWatch.getList("A").get(0).getAllGet()==7;
        Files.writeString(friendFile,"truncated JSON");Log.expectedParseFailure=true;UserIdMap.load("A");Log.expectedParseFailure=false;
        assert !UserIdMap.isFriendListLoaded()&&UserIdMap.getMaskName("friend")==null:"failed friend parse claimed a complete list";
        FriendWatch.friendWatch("friend",3);FriendWatch.save();assert FriendWatch.getList("A").get(0).getAllGet()==10:"failed friend map suppressed real friend statistics";
        assert Files.readString(friendFile).equals("truncated JSON");
        Files.writeString(friendFile,friendJson);UserIdMap.load("A");assert UserIdMap.isFriendListLoaded();
        JsonUtil.failFileReads=1;Log.expectedParseFailure=true;UserIdMap.load("A");Log.expectedParseFailure=false;
        assert !UserIdMap.isFriendListLoaded()&&JsonUtil.failFileReads==0:"friend file I/O failure was hidden";
        UserIdMap.load("A");assert UserIdMap.isFriendListLoaded();
        Files.writeString(friendFile,"");Log.expectedParseFailure=true;UserIdMap.load("A");Log.expectedParseFailure=false;assert !UserIdMap.isFriendListLoaded();
        Files.delete(friendFile);UserIdMap.load("A");assert !UserIdMap.isFriendListLoaded():"missing friend file is not an authoritative empty list";
        Files.writeString(friendFile,"{}");UserIdMap.load("A");assert UserIdMap.isFriendListLoaded()&&UserIdMap.getMaskName("friend")==null;
        FriendWatch.friendWatch("friend",99);FriendWatch.save();assert FriendWatch.getList("A").get(0).getAllGet()==10:"valid complete list stopped filtering strangers";
        UserIdMap.unload();assert !UserIdMap.isFriendListLoaded();
        for(int scenario=0;scenario<4;scenario++){
            Files.writeString(friendFile,friendJson);UserIdMap.load("A");
            XHelpers.fail=scenario==0;
            XHelpers.friends=scenario==1?java.util.List.of(new XHelpers.Friend(),new Object()):scenario==2?java.util.List.of():java.util.List.of(new XHelpers.Friend());
            Log.expectedParseFailure=true;initBackground("A");Log.expectedParseFailure=false;
            assert UserIdMap.isFriendListLoaded()==(scenario>=2):"callback claimed incomplete friend enumeration: "+scenario;
            if(scenario<2)assert Files.readString(friendFile).equals(friendJson):"failed callback overwrote good friend cache";
            else assert (UserIdMap.getMaskName("friend")!=null)==(scenario==3):"complete enumeration did not replace friend cache";
            assert Files.isRegularFile(FileUtil.getSelfIdFile("A").toPath()):"friend failure discarded valid self info";
        }
        XHelpers.fail=false;
        XHelpers.friends=java.util.List.of(new XHelpers.Friend());
        XHelpers.entered=new java.util.concurrent.CountDownLatch(1);XHelpers.release=new java.util.concurrent.CountDownLatch(1);
        FileUtil.requireBackground=true;
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker=initBackground("A",failure);Thread second=null;
        try {
            assert XHelpers.entered.await(2,java.util.concurrent.TimeUnit.SECONDS):"DAO never entered on background caller";
            assert !UserIdMap.isFriendListLoaded():"in-progress DAO retained complete flag";
            java.util.concurrent.CompletableFuture<Void> mainProbe=java.util.concurrent.CompletableFuture.runAsync(()->{UserIdMap.add(new io.github.aw1y2z.sesame.entity.UserEntity("probe","",null,null,null,null));});
            mainProbe.get(1,java.util.concurrent.TimeUnit.SECONDS);
            second=initBackground("A",failure);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
            while(second.getState()!=Thread.State.BLOCKED&&second.isAlive()&&System.nanoTime()<deadline)Thread.sleep(5);
            assert second.getState()==Thread.State.BLOCKED:"concurrent refresh escaped serialization";
        } finally {XHelpers.release.countDown();worker.join(2000);if(second!=null)second.join(2000);FileUtil.requireBackground=false;}
        assert !worker.isAlive()&&second!=null&&!second.isAlive()&&failure.get()==null:failure.get();
        XHelpers.entered=new java.util.concurrent.CountDownLatch(1);XHelpers.release=new java.util.concurrent.CountDownLatch(1);
        worker=initBackground("A",failure);second=null;
        try {
            assert XHelpers.entered.await(2,java.util.concurrent.TimeUnit.SECONDS);
            second=initBackground("A",failure);
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(1);
            while(second.getState()!=Thread.State.BLOCKED&&second.isAlive()&&System.nanoTime()<deadline)Thread.sleep(5);
            assert second.getState()==Thread.State.BLOCKED;
            io.github.aw1y2z.sesame.hook.ApplicationHook.hostUid="B";UserIdMap.uid="B";
            FileUtil.write2File("{}",FileUtil.getFriendIdMapFile("B"));UserIdMap.load("B");
            assert UserIdMap.isFriendListLoaded();
            int publishes=FileUtil.publishes,staleWrites=FileUtil.writes;
            XHelpers.release.countDown();worker.join(2000);second.join(2000);
            assert !worker.isAlive()&&!second.isAlive()&&failure.get()==null:failure.get();
            assert UserIdMap.uid.equals("B")&&FileUtil.publishes==publishes:"queued old UID republished or replaced current account";
            assert UserIdMap.isFriendListLoaded():"queued old UID cleared new account completeness";
            assert FileUtil.writes==staleWrites:"queued old UID wrote account cache";
        } finally {XHelpers.release.countDown();worker.join(2000);if(second!=null)second.join(2000);}
        io.github.aw1y2z.sesame.hook.ApplicationHook.hostUid=null;
        int unavailablePublishes=FileUtil.publishes,unavailableWrites=FileUtil.writes;
        initBackground("A");
        assert UserIdMap.uid.equals("B")&&UserIdMap.isFriendListLoaded():"unavailable host UID changed current-account state";
        assert FileUtil.publishes==unavailablePublishes&&FileUtil.writes==unavailableWrites:"unavailable host UID published unverified account";
        io.github.aw1y2z.sesame.hook.ApplicationHook.hostUid="A";
        XHelpers.entered=XHelpers.release=null;
        initBackground("A");
        assert UserIdMap.uid.equals("A")&&"A".equals(FileUtil.publishedUid)&&UserIdMap.isFriendListLoaded():"host UID recovery failed to publish account";
        Files.writeString(friendFile,friendJson);int writesBeforeChange=FileUtil.writes;
        XHelpers.changeUid=true;initBackground("A");XHelpers.changeUid=false;
        assert FileUtil.writes==writesBeforeChange&&Files.readString(friendFile).equals(friendJson):"UID changed during DAO but callback wrote cache";
        UserIdMap.uid="A";
        System.out.println("PASS: account statistics/blacklists isolated; background friend refresh is serialized without holding UserIdMap monitor over DAO; failed loads preserve caches and statistics");
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
assert "Statistics.readFrom(uid)" in activity and "Statistics.aggregate(" in activity
assert "if (folder != previousFolder)" in activity
assert 'FriendWatch.getList(userId)' in (SOURCE / "ui/miuix/MiuixFriendStatsActivity.kt").read_text(encoding="utf-8")
assert hook.index("UserIdMap.initUser(userId)") < hook.index("Statistics.load();", hook.index("UserIdMap.initUser(userId)")) < hook.index("Model.initAllModel();", hook.index("UserIdMap.initUser(userId)"))
