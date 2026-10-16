"""Replay Oct 15 upstream plant browsing and native box actions; no device or real RPC."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

plant = (SOURCE / "model/task/antForest/GreenPlantBrowse.java").read_text(encoding="utf-8")
plant = re.sub(r"^(?:package|import) .*?;\s*", "", plant, flags=re.M)
plant = plant.replace("public class GreenPlantBrowse", "static class GreenPlantBrowse")
code = r'''
import java.util.*;import java.io.File;import org.json.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class UpstreamOct15Check {
 static final String TAG="test";static final int MAX_GAME_DRAW_ROUND=12;
 enum Outcome {DONE,RETRY,UNABLE}
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static int checks,cancelAt;static void sleep(long ms){if(cancel||++checks==cancelAt)throw new TaskCancelledException();}}
 static class StringUtil {static String truncate(String s,int n){return s.substring(0,Math.min(n,s.length()));}static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class AccountFolderName {static boolean isValidUid(String s){return s!=null&&s.matches("[A-Za-z0-9_-]{1,128}");}}
 static final File MAIN_DIRECTORY_FILE=new File("default"),CONFIG_DIRECTORY_FILE=new File("accounts");
 @@DIR@@
 static class Log {static boolean unresolved;static void markUnresolved(){unresolved=true;}static void record(String s){}static void forest(String s){}static void farm(String s){}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class RpcRequestGuard {@@GUARD@@}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}}
 static class MessageUtil {static boolean checkResultCode(String tag,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean isRetryable(JSONObject j){return j.optBoolean("retryable");}static boolean isServerBusy(JSONObject j){return false;}}
 static class ApplicationHook {
  static int reads,feeds,awards;static boolean initialDone,afterDone=true,failedQuery,invalidState,failedFeeds,missingFeeds;
  static String requestString(String method,String args){
   if(method.endsWith("queryPlantTaskList")){reads++;JSONObject task=new JSONObject().put("taskCode","GUANG_TASK");if(!invalidState)task.put("completed",awards>0?afterDone:initialDone);
    return new JSONObject().put("success",!failedQuery).put("taskList",new JSONArray().put(task)).toString();}
   if(method.endsWith("queryPlantFeedsItemList")){feeds++;JSONObject out=new JSONObject().put("success",!failedFeeds);if(!missingFeeds)out.put("itemDTOList",new JSONArray().put(new JSONObject().put("id","item")));return out.toString();}
   if(method.endsWith("plantAward")){awards++;assert new JSONArray(args).optJSONObject(0).optString("awardType").equals("GUANG_TASK");return "{success:true,awardResult:200}";}
   throw new AssertionError(method);
  }
 }
 @@PLANT@@
 static class AntFarmRpcCall {static String home,award;static int writes;static String queryGameList(){return home;}static String drawGameCenterAward(int count){writes++;return award;}}
 static class AntForestRpcCall {static String home,award;static int writes;static String queryGameList(){return home;}static String drawGameCenterAward(int count){writes++;return award;}}
 @@BOXES@@
 static String rights(int quota,int perTime){return new JSONObject().put("success",true).put("resultCode","SUCCESS").put("gameCenterDrawRights",new JSONObject().put("quotaCanUse",quota).put("quotaPerTime",perTime)).toString();}
 static void reset(){Status.flags.clear();TimeUtil.cancel=false;TimeUtil.checks=TimeUtil.cancelAt=0;Log.unresolved=false;ApplicationHook.reads=ApplicationHook.feeds=ApplicationHook.awards=0;
  ApplicationHook.initialDone=ApplicationHook.failedQuery=ApplicationHook.invalidState=ApplicationHook.failedFeeds=ApplicationHook.missingFeeds=false;ApplicationHook.afterDone=true;AntFarmRpcCall.writes=AntForestRpcCall.writes=0;}
 public static void main(String[] args){
  assert getUserDataDirectoryFile(null).equals(MAIN_DIRECTORY_FILE)&&getUserDataDirectoryFile("").equals(MAIN_DIRECTORY_FILE);
  assert getUserDataDirectoryFile("20880001").equals(new File(CONFIG_DIRECTORY_FILE,"20880001"));
  try{getUserDataDirectoryFile("../other");throw new AssertionError("Invalid UID fell back to shared configuration");}catch(IllegalArgumentException expected){}
  try{getUserDataDirectoryFile("A".repeat(129));throw new AssertionError("Oversized UID accepted");}catch(IllegalArgumentException expected){}
  reset();GreenPlantBrowse.run();assert Status.flags.contains("forest::marketBrowseDone")&&ApplicationHook.awards==1&&ApplicationHook.feeds==2;
  GreenPlantBrowse.run();assert ApplicationHook.awards==1&&ApplicationHook.reads==2:"Completed plant task must stop for the day";
  reset();ApplicationHook.initialDone=true;GreenPlantBrowse.run();assert ApplicationHook.awards==0&&Status.flags.size()==1;
  reset();ApplicationHook.failedQuery=true;GreenPlantBrowse.run();assert ApplicationHook.feeds==0&&ApplicationHook.awards==0;
  reset();ApplicationHook.invalidState=true;GreenPlantBrowse.run();assert ApplicationHook.awards==0&&Status.flags.isEmpty();
  reset();ApplicationHook.failedFeeds=true;GreenPlantBrowse.run();assert ApplicationHook.feeds==1&&ApplicationHook.awards==0;
  reset();ApplicationHook.missingFeeds=true;GreenPlantBrowse.run();assert ApplicationHook.awards==0;
  reset();ApplicationHook.afterDone=false;GreenPlantBrowse.run();assert ApplicationHook.awards==1&&Status.flags.isEmpty()&&Log.unresolved;
  reset();TimeUtil.cancel=true;try{GreenPlantBrowse.run();throw new AssertionError("Plant cancellation swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.awards==0;
  UpstreamOct15Check c=new UpstreamOct15Check();
  reset();AntFarmRpcCall.home=rights(1,1);AntFarmRpcCall.award=rights(0,1);assert c.openFarmLeyuanBoxes("test")==Outcome.DONE&&AntFarmRpcCall.writes==1;
  reset();AntFarmRpcCall.home=rights(1,1);AntFarmRpcCall.award="{success:true}";assert c.openFarmLeyuanBoxes("test")==Outcome.RETRY&&AntFarmRpcCall.writes==1&&Log.unresolved;
  reset();AntFarmRpcCall.home=rights(1,1);AntFarmRpcCall.award=rights(1,1);assert c.openFarmLeyuanBoxes("test")==Outcome.RETRY&&AntFarmRpcCall.writes==1;
  reset();AntFarmRpcCall.home=rights(0,1);assert c.openFarmLeyuanBoxes("test")==Outcome.UNABLE&&AntFarmRpcCall.writes==0;
  reset();AntForestRpcCall.home=rights(200,100);AntForestRpcCall.award=rights(0,100);assert c.openForestLeyuanBoxes("test")==Outcome.DONE&&AntForestRpcCall.writes==1;
  reset();AntForestRpcCall.home=rights(200,100);AntForestRpcCall.award="{resultCode:'SUCCESS'}";assert c.openForestLeyuanBoxes("test")==Outcome.RETRY&&AntForestRpcCall.writes==1&&Log.unresolved;
  reset();AntForestRpcCall.home=rights(0,100);assert c.openForestLeyuanBoxes("test")==Outcome.UNABLE&&AntForestRpcCall.writes==0;
  reset();TimeUtil.cancelAt=2;AntForestRpcCall.home=rights(100,100);try{c.openForestLeyuanBoxes("test");throw new AssertionError("Box cancellation swallowed");}catch(TaskCancelledException expected){}assert AntForestRpcCall.writes==0;
  System.out.println("PASS upstream plant: query/state/feed/award recheck/cancellation; native boxes: quota, missing/stalled receipts, cancellation");
 }
}
'''
code = code.replace("@@PLANT@@", plant).replace("@@GUARD@@", method("rpc/intervallimit/RpcRequestGuard.java", "public static boolean isFailure("))
code = code.replace("@@DIR@@", method("util/FileUtil.java", "private static File getUserDataDirectoryFile(") + method("util/FileUtil.java", "private static boolean isSafeUserId("))
code = code.replace("@@BOXES@@", method("model/task/antFarm/AntFarm.java", "private Outcome openFarmLeyuanBoxes(") + method("model/task/antForest/AntForestV2.java", "private Outcome openForestLeyuanBoxes("))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-upstream-oct15-") as tmp:
    java = Path(tmp) / "UpstreamOct15Check.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java), str(SOURCE / "util/TaskCancelledException.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "UpstreamOct15Check"], check=True, timeout=30)

# Run the real delayed gesture dispatch and HTTP token comparison with isolated host doubles.
host = r'''
import java.util.*;import java.util.concurrent.atomic.AtomicLong;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;
public class UpstreamHostCheck {
 static final String TAG="test";static final Handler MAIN_HANDLER=new Handler();static final AtomicLong GESTURE_GENERATION=new AtomicLong();static InFlight inFlight;
 @@LIFECYCLE@@
 static class View {List<Integer> events=new ArrayList<>();boolean isShown(){return true;}boolean isEnabled(){return true;}}
 static class Handler {
  record Pending(long at,int sequence,Runnable runnable){}
  static long now;static int sequence;static PriorityQueue<Pending> queue=new PriorityQueue<>(Comparator.comparingLong(Pending::at).thenComparingInt(Pending::sequence));
  void postDelayed(Runnable r,long delay){queue.add(new Pending(now+delay,sequence++,r));}
  static void one(){Pending p=queue.remove();now=p.at();p.runnable().run();}static void all(){while(!queue.isEmpty())one();}
 }
 static class Log {static void i(String a,String b){}static void d(String a,String b){}static void e(String a,String b){}}
 static class MotionEvent {static final int ACTION_DOWN=0,ACTION_MOVE=1,ACTION_UP=2;}
 static class RandomUtil {static long nextLong(long min,long max){return min;}static int nextInt(int min,int max){return 0;}}
 static class SystemClock {static long uptimeMillis(){return Handler.now;}}
 static void dispatchTouchEvent(View v,int action,float x,float y,long down,long event){v.events.add(action);}
 @@GESTURES@@
 static class NanoHTTPD {interface IHTTPSession {Map<String,String> getHeaders();}}
 @@TOKEN@@
 public static void main(String[] args){
  View first=new View(),second=new View();simulateSwipe(first,0,0,10,0,800);simulateSwipe(second,0,0,20,0,800);Handler.all();
  assert first.events.isEmpty()&&second.events.size()==17&&second.events.get(0)==0&&second.events.get(16)==2:"Superseded gesture must not interleave";
  View switched=new View();simulateSwipe(switched,0,0,10,0,800);Handler.one();assert switched.events.equals(List.of(0));
  TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);Handler.all();assert switched.events.equals(List.of(0,2)):"Account switch must release DOWN and cancel remaining MOVE events";
  View delayed=new View();simulateSwipe(delayed,0,0,10,0,800);freeze=TaskLifecycle.freezeIfIdle();TaskLifecycle.thaw(freeze);Handler.all();assert delayed.events.isEmpty();
  simulateTap(delayed,1,1);freeze=TaskLifecycle.freezeIfIdle();TaskLifecycle.thaw(freeze);Handler.all();assert delayed.events.isEmpty():"Delayed tap crossed accounts";
  assert !verifyToken(null,()->Map.of("authorization","secret"))&&!verifyToken(" ",()->Map.of("authorization","secret"));
  assert !verifyToken("secret",null)&&!verifyToken("secret",Map::of)&&!verifyToken("secret",()->Map.of("authorization","Bearer wrong"));
  assert verifyToken("secret",()->Map.of("authorization","bEaReR secret"))&&verifyToken("secret",()->Map.of("authorization","secret"));
  System.out.println("PASS host: gesture replacement/account cancellation/UP cleanup, delayed tap, fail-closed HTTP authentication");
 }
}
'''
host = host.replace("@@LIFECYCLE@@", method("data/task/TaskLifecycle.java", "public final class TaskLifecycle").replace("public final class", "static final class", 1))
gesture_path = "hook/MotionEventSimulator.java"
host = host.replace("@@GESTURES@@", "\n".join(method(gesture_path, signature) for signature in (
    "private static final class InFlight", "public static void simulateSwipe(View view, float startX, float startY, float endX, float endY, long duration)",
    "private static void startSwipe(", "private static void postGesture(", "private static void releaseInFlight(", "public static void simulateTap(")))
host = host.replace("@@TOKEN@@", method("hook/BaseHandler.java", "protected static boolean verifyToken("))
with tempfile.TemporaryDirectory(prefix="sesame-upstream-host-") as tmp:
    java = Path(tmp) / "UpstreamHostCheck.java"
    java.write_text(host, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp, "UpstreamHostCheck"], check=True, timeout=30)

# Compile the real file helpers under a non-UTF-8 JVM default to catch asymmetric I/O.
codec = r'''
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
public class FileCodecCheck {
 static final String TAG="codec";
 static class android {static class os {static class Process {static int myPid(){return 1;}}}}
 static class Toast {static void show(String s,boolean b){}}
 static class Log {static void debug(String s){}static void printStackTrace(String tag,Throwable t){throw new AssertionError(t);}static void printStackTrace(Throwable t){throw new AssertionError(t);}}
 @@FILES@@
 public static void main(String[] args)throws Exception {
  Path dir=Files.createTempDirectory("sesame-codec-");File file=dir.resolve("日志.txt").toFile();
  String first="{\"name\":\"中文🌱\"}\n",extra="追加€\r\n";
  try{
   assert write2File(first,file);assert Arrays.equals(Files.readAllBytes(file.toPath()),first.getBytes(StandardCharsets.UTF_8));
   assert readFromFile(file).equals(first);
   assert append2File(extra,file);assert Arrays.equals(Files.readAllBytes(file.toPath()),(first+extra).getBytes(StandardCharsets.UTF_8));
   assert readFromFile(file).equals(first+extra);
   assert write2File(extra,file)&&readFromFile(file).equals(extra):"Atomic replacement did not preserve UTF-8";
   assert clearFile(file)&&file.length()==0&&readFromFile(file).isEmpty();
   assert Objects.requireNonNull(dir.toFile().list()).length==1:"Atomic write left temporary artifacts";
  }finally{Files.deleteIfExists(file.toPath());Files.delete(dir);}
  System.out.println("PASS UTF-8 atomic write/read/append/clear under non-UTF-8 default charset");
 }
}
'''
codec = codec.replace("@@FILES@@", "\n".join(method("util/FileUtil.java", signature) for signature in (
    "public static String readFromFile(", "public static boolean write2File(", "public static boolean append2File(",
    "public static Boolean clearFile(", "public static void close(Closeable")))
with tempfile.TemporaryDirectory(prefix="sesame-codec-check-") as tmp:
    java = Path(tmp) / "FileCodecCheck.java"
    java.write_text(codec, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-Dfile.encoding=ISO-8859-1", "-cp", tmp, "FileCodecCheck"], check=True, timeout=30)
