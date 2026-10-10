"""Replay merged production policy, blacklist buffering, ownership, quest and nested-list checks."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

code = r'''
import org.json.*; import java.util.*; import java.util.function.*; import java.util.regex.Pattern;
public class MergePolicyCheck {
 static final String TAG="farm";
 static class AntFarmRpcCall {static String response;static int calls;static String doFarmTask(String key){calls++;return response;}
  static String finishTask(String type,String scene){return doFarmTask(type);}}
 @@FARM_ATTEMPT@@
 static class System {static long now=java.time.Instant.parse("2026-10-09T15:59:00Z").toEpochMilli();
  static long currentTimeMillis(){return now;}static final java.io.PrintStream out=java.lang.System.out;}
 static class TaskCancelledException extends RuntimeException {}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}static boolean isLessThanSecondOfDays(long a,long b){
  return java.time.Instant.ofEpochMilli(a).atZone(java.time.ZoneOffset.ofHours(8)).toLocalDate()
    .isBefore(java.time.Instant.ofEpochMilli(b).atZone(java.time.ZoneOffset.ofHours(8)).toLocalDate());}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){
  try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Log {static void i(String s){}static void i(String a,String b){}static void system(String a,String b){}
  static void other(String s){}static void record(String s){}static void err(String a,String b,Throwable t){}}
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class Status {
  static final String TAG="status";static Status INSTANCE=new Status();static String loadedUid;
  Set<String> flagLogList=new HashSet<>();Map<String,Integer> intFlagLogList=new HashMap<>();
  static Map<String,Status> disk=new HashMap<>();
  static void load(){loadedUid=UserIdMap.uid;INSTANCE=disk.computeIfAbsent(loadedUid,k->new Status());}
  static void save(){disk.put(UserIdMap.uid,INSTANCE);}
  @@STATUS@@
 }
 static class RuntimeInfo {static RuntimeInfo instance=new RuntimeInfo();Map<String,String> map=new HashMap<>();
  static RuntimeInfo getInstance(){return instance;}String getString(String k){return map.getOrDefault(k,"");}
  void put(String k,String v){map.put(k,v);}}
 static class MessageUtil {
  @@RETRY@@
  static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}
  static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}
  static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}
  static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){}
  static int marks,permanent;static final String TAG="black";
  static boolean isUnsupportedRpc(JSONObject jo){return jo!=null&&"400000040".equals(jo.optString("code"));}
  static final ThreadLocal<List<Runnable>> DEFER_BLACKLIST=new ThreadLocal<>();
  @@BUFFER@@
  static String[] autoBlackListTarget(String k){return new String[]{"model","list"};}
  static void MarkTaskBlackList(String a,String b,String c,String d){if(!deferBlackList(()->marks++))marks++;}
  static void MarkTaskBlackListPermanent(String a,String b,String c,String d){permanent++;}
 }
 static class ApplicationHook {static int writes;static boolean cancel;static String args,reply="{\"resultCode\":\"102\"}";
  static String requestString(String rpc,String body){if(cancel)throw new TaskCancelledException();writes++;args=body;return reply;}}
 @@ALTERNATIVE@@
 @@POLICY@@
 @@AWARD@@
 @@FOREST@@
 @@QUEST@@
 static List<String> logs=new ArrayList<>();static int attempts;
 static TaskAttemptPolicy.Site site(TaskAttemptPolicy.ProbeResult state){return new TaskAttemptPolicy.Site(
   "field","task","browse","scene",k->state);}
 static TaskAttemptPolicy.Outcome attempt(TaskAttemptPolicy.Outcome result){attempts++;
  MessageUtil.MarkTaskBlackList("a","b","c","d");return result;}
 static JSONObject task(String type,String status){return new JSONObject().put("taskBaseInfo",
  new JSONObject().put("sceneCode","scene").put("taskType",type).put("taskStatus",status));}
 public static void main(String[] args)throws Exception {
  Status.disk.put("A",new Status());Status.disk.put("B",new Status());
  Status.disk.get("A").intFlagLogList.put("count",7);Status.disk.get("B").intFlagLogList.put("count",2);
  assert Status.getIntFlagToday("count")==7;UserIdMap.uid="B";Status.setIntFlagToday("count",3);
  assert Status.getIntFlagToday("count")==3&&Status.disk.get("A").intFlagLogList.get("count")==7;
  UserIdMap.uid="A";assert Status.getIntFlagToday("count")==7;
  MergePolicyCheck farm=new MergePolicyCheck();
  Status.flagToday("attempt::farm__task__legacy");
  for(String raw:new String[]{"", "{}", "{error:'RPC_SKIPPED'}", "{error:'TRANSPORT_ERROR'}", "{error:1009}", "{error:48}", "{resultDesc:'请进行验证后继续'}"}){
   AntFarmRpcCall.response=raw;int previous=AntFarmRpcCall.calls;
   for(int round=0;round<2;round++) assert TaskAttemptPolicy.handle("farm::task::legacy","farm",null,
    ()->farm.attemptFarmTask("farm","browse",""),logs::add,site(TaskAttemptPolicy.ProbeResult.TODO))==TaskAttemptPolicy.Outcome.RETRY;
   assert AntFarmRpcCall.calls==previous+2 && !Status.hasFlagToday("attemptV2::farm__task__legacy") : "transient response persisted daily failure: "+raw;
  }
  assert !MessageUtil.isRetryable(new JSONObject().put("error","RPC_SKIPPED").put("code","400000040")) : "unsupported fallback lost";
  var done=TaskAttemptPolicy.handle("done","done",null,()->attempt(TaskAttemptPolicy.Outcome.RETRY),
   logs::add,site(TaskAttemptPolicy.ProbeResult.FINISHED));
  assert done==TaskAttemptPolicy.Outcome.DONE&&MessageUtil.marks==0;
  var unknown=TaskAttemptPolicy.handle("unknown","unknown",null,()->attempt(TaskAttemptPolicy.Outcome.UNSUPPORTED),
   logs::add,site(TaskAttemptPolicy.ProbeResult.UNKNOWN));
  assert unknown==TaskAttemptPolicy.Outcome.RETRY&&ApplicationHook.writes==0&&MessageUtil.marks==0;
  assert !Status.hasFlagToday("attemptV2::unknown");
  var unable=TaskAttemptPolicy.handle("unable","unable",null,()->attempt(TaskAttemptPolicy.Outcome.DONE),
   logs::add,site(TaskAttemptPolicy.ProbeResult.TODO));
  assert unable==TaskAttemptPolicy.Outcome.UNABLE&&MessageUtil.marks==1;
  int before=attempts;TaskAttemptPolicy.handle("unable","unable",null,()->attempt(TaskAttemptPolicy.Outcome.DONE),
   logs::add,site(TaskAttemptPolicy.ProbeResult.TODO));assert attempts==before;
  var triggered=TaskAttemptPolicy.handle("fallback","fallback",null,()->TaskAttemptPolicy.Outcome.UNSUPPORTED,
   logs::add,site(TaskAttemptPolicy.ProbeResult.TODO));assert triggered==TaskAttemptPolicy.Outcome.TRIGGERED;
  assert ApplicationHook.writes==1;
  TaskAttemptPolicy.handle("fallback","fallback",null,()->TaskAttemptPolicy.Outcome.DONE,
   logs::add,site(TaskAttemptPolicy.ProbeResult.TODO));assert MessageUtil.marks==1;
  System.now+=120000;Status.INSTANCE.flagLogList.clear();
  var tomorrow=TaskAttemptPolicy.handle("fallback","fallback",null,()->TaskAttemptPolicy.Outcome.DONE,
   logs::add,site(TaskAttemptPolicy.ProbeResult.FINISHED));assert tomorrow==TaskAttemptPolicy.Outcome.DONE;
  ApplicationHook.reply="bad";
  assert TaskAttemptPolicy.handle("bad","bad",null,()->TaskAttemptPolicy.Outcome.UNSUPPORTED,logs::add,
   site(TaskAttemptPolicy.ProbeResult.TODO))==TaskAttemptPolicy.Outcome.RETRY;
  assert !Status.hasFlagToday("attemptV2::bad");
  before=attempts;TaskAttemptPolicy.handle("pay","pay",null,()->attempt(TaskAttemptPolicy.Outcome.DONE),logs::add,
   new TaskAttemptPolicy.Site("field","task","OFFLINE_PAY","scene"));assert attempts==before&&MessageUtil.permanent==0;
  int[] probes={0};
  int logCount=logs.size();TaskAttemptPolicy.handle("pay","pay",null,()->attempt(TaskAttemptPolicy.Outcome.DONE),logs::add,
   new TaskAttemptPolicy.Site("field","task","OFFLINE_PAY","scene"));assert logs.size()==logCount+1;
  int[] retryCalls={0};
  TaskAttemptPolicy.handle("manualRetry","browse",null,()->{retryCalls[0]++;return TaskAttemptPolicy.Outcome.UNABLE;},logs::add,site(TaskAttemptPolicy.ProbeResult.TODO));
  Status.load(); // restart restores the same account's daily attempt flag
  assert TaskAttemptPolicy.handle("manualRetry","browse",null,()->{retryCalls[0]++;return TaskAttemptPolicy.Outcome.DONE;},logs::add,site(TaskAttemptPolicy.ProbeResult.FINISHED))==TaskAttemptPolicy.Outcome.TRIED_TODAY;
  assert retryCalls[0]==1;
  assert DailyTask.manual(()->TaskAttemptPolicy.handle("manualRetry","browse",null,()->{retryCalls[0]++;return TaskAttemptPolicy.Outcome.DONE;},logs::add,site(TaskAttemptPolicy.ProbeResult.FINISHED)))==TaskAttemptPolicy.Outcome.DONE;
  assert retryCalls[0]==2 && !Status.hasFlagToday("attemptV2::manualRetry") && !DailyTask.isManual();
  DailyTask.manual(()->TaskAttemptPolicy.handle("pay","pay",null,()->{retryCalls[0]++;return TaskAttemptPolicy.Outcome.DONE;},logs::add,new TaskAttemptPolicy.Site("field","task","OFFLINE_PAY","scene")));
  assert retryCalls[0]==2 : "manual must retain transaction exclusion";
  TaskAttemptPolicy.handle("pendingManual","pending",null,()->TaskAttemptPolicy.Outcome.FORGED,logs::add,null);
  assert DailyTask.manual(()->TaskAttemptPolicy.handle("pendingManual","pending",null,()->{retryCalls[0]++;return TaskAttemptPolicy.Outcome.DONE;},logs::add,site(TaskAttemptPolicy.ProbeResult.TODO)))==TaskAttemptPolicy.Outcome.TRIED_TODAY;
  assert retryCalls[0]==2 : "manual must retain unconfirmed receipt";
  Status.flagToday("attemptV2::accountRetry");UserIdMap.uid="B";
  assert !Status.hasFlagToday("attemptV2::accountRetry");
  UserIdMap.uid="A";assert Status.hasFlagToday("attemptV2::accountRetry");
  int unsupportedMarks=MessageUtil.marks;
  TaskAttemptPolicy.handle("noFallback","noFallback",null,()->attempt(TaskAttemptPolicy.Outcome.UNSUPPORTED),logs::add,
   new TaskAttemptPolicy.Site("field","task",null,"scene",k->TaskAttemptPolicy.ProbeResult.TODO));
  assert MessageUtil.marks==unsupportedMarks+1;
  assert TaskAttemptPolicy.handle("award","award",()->true,null,logs::add,
   new TaskAttemptPolicy.Site("field","task","browse","scene",k->{probes[0]++;return TaskAttemptPolicy.ProbeResult.FINISHED;}))
   ==TaskAttemptPolicy.Outcome.AWARDED;
  assert probes[0]==0;
  assert TaskAttemptPolicy.handle("award","award",()->false,null,logs::add,site(TaskAttemptPolicy.ProbeResult.RECEIVED))
   ==TaskAttemptPolicy.Outcome.AWARDED;
  int marksBefore=MessageUtil.marks;
  assert !TaskAward.confirmReceivedOrBlackList("task",k->TaskAttemptPolicy.ProbeResult.UNKNOWN,"k","title",()->MessageUtil.marks++,logs::add);
  assert MessageUtil.marks==marksBefore;
  TaskAttemptPolicy.handle("video","video",null,()->attempt(TaskAttemptPolicy.Outcome.UNSUPPORTED),logs::add,
   new TaskAttemptPolicy.Site("field","task","video","scene","source",()->true,k->TaskAttemptPolicy.ProbeResult.TODO));
  assert MessageUtil.marks==marksBefore;
  JSONObject parent=task("parent","RECEIVED").put("childTaskTypeList",new JSONArray().put(task("child","TODO")));
  assert forestVitalityStatus(new JSONArray().put(parent),"scene","child")==TaskAttemptPolicy.ProbeResult.TODO;
  assert forestVitalityStatus(new JSONArray().put(parent),"scene","missing")==TaskAttemptPolicy.ProbeResult.GONE;
  assert forestVitalityStatus(new JSONArray().put(1),"scene","child")==TaskAttemptPolicy.ProbeResult.UNKNOWN;
  assert questDone(new JSONObject().put("done","false"))==null;
  assert questDone(new JSONObject().put("status","NEW_STATE"))==null;
  assert Boolean.FALSE.equals(questDone(new JSONObject().put("done",false)));
  TaskAlternative.request("quote\"\\","scene","source");
  assert "quote\"\\".equals(new JSONArray(ApplicationHook.args).optJSONObject(0).optString("bizKey"));
  int writes=ApplicationHook.writes;try{TaskAlternative.request("","scene","source");throw new AssertionError();}
  catch(IllegalArgumentException expected){}assert ApplicationHook.writes==writes;
  Locale.setDefault(Locale.forLanguageTag("tr-TR"));
  assert TaskAlternative.isTransactionTask("OFFLINE_PAY")&&!TaskAlternative.isTransactionTask("ORCHARD_NORMAL_TAOBAOTAOLIPAI_VISIT");
  assert !TaskAlternative.isTransactionTask("2060170000359285");
  for(String key:List.of("ALIPAY_BROWSE","ORCHARD_NORMAL_YUEBAO_VISIT","KUAIDI_VISIT","HUISHOU_BROWSE","rechargeable_view","x_czz_view"))
   assert !TaskAlternative.isTransactionTask(key):key;
  for(String key:List.of("OFFLINE_PAY","MYZY_pay_1","ORCHARD_NCLY_CHARGE1_XDDQ","GOLDENBEAN_GAME_CZ_XDDQ_AI","2026cc_cz6ylyb_fz"))
   assert TaskAlternative.isTransactionTask(key):key;
  ApplicationHook.cancel=true;
  try {TaskAlternative.trigger(null,"id","title","browse","scene","prefix",logs::add);throw new AssertionError("trigger swallowed cancellation");}
  catch(TaskCancelledException expected){}finally{ApplicationHook.cancel=false;}
  Map<String,String> pending=new HashMap<>();pending.put("id","title");TimeUtil.cancel=true;
  try {TaskAlternative.verify(pending,new TaskAlternative.VerifyConfig("model","field","display","prefix","done",true,logs::add),unknownStates->new HashSet<>());
   throw new AssertionError("verify swallowed cancellation");}
  catch(TaskCancelledException expected){}finally{TimeUtil.cancel=false;}
  int previousMarks=MessageUtil.marks;
  try {TaskAttemptPolicy.handle("cancelAttempt","title",null,()->{
   MessageUtil.MarkTaskBlackList("a","b","c","d");throw new TaskCancelledException();},logs::add,null);throw new AssertionError();}
  catch(TaskCancelledException expected){}
  assert MessageUtil.marks==previousMarks&&MessageUtil.DEFER_BLACKLIST.get()==null:"cancelled attempt committed blacklist";
  assert !Status.hasFlagToday("attemptV2::cancelAttempt");
  try {TaskAttemptPolicy.handle("cancelAward","title",()->{
   MessageUtil.MarkTaskBlackList("a","b","c","d");throw new TaskCancelledException();},null,logs::add,null);throw new AssertionError();}
  catch(TaskCancelledException expected){}
  assert MessageUtil.marks==previousMarks&&MessageUtil.DEFER_BLACKLIST.get()==null:"cancelled award committed blacklist";
  System.out.println("PASS real policy/list confirmation, deferred blacklist, retries, GMT+8 rollover, account counts, nested children, quest states and escaped payload");
 }
}
'''
replacements = {
    "@@RETRY@@": method("util/MessageUtil.java", "public static boolean isRetryable("),
    "@@FARM_ATTEMPT@@": method("model/task/antFarm/AntFarm.java", "private Outcome attemptFarmTask(").replace("Outcome", "TaskAttemptPolicy.Outcome"),
    "@@STATUS@@": "\n".join(method("util/Status.java", s) for s in (
        "private static void ensureLoadedForCurrentUid(", "public static synchronized Boolean hasFlagToday(",
        "public static synchronized void clearFlag(",
        "public static synchronized void flagToday(String tag)", "public static synchronized int getIntFlagToday(",
        "public static synchronized void setIntFlagToday(")),
    "@@BUFFER@@": "\n".join(method("util/MessageUtil.java", s) for s in (
        "public static void beginDeferBlackList(", "public static void endDeferBlackList(",
        "private static boolean deferBlackList(")),
    "@@ALTERNATIVE@@": method("model/base/TaskAlternative.java", "public final class TaskAlternative").replace(
        "public final class TaskAlternative", "static final class TaskAlternative", 1),
    "@@POLICY@@": method("data/task/TaskAttemptPolicy.java", "public class TaskAttemptPolicy").replace(
        "public class TaskAttemptPolicy", "static class TaskAttemptPolicy", 1),
    "@@AWARD@@": method("data/task/TaskAward.java", "public final class TaskAward").replace(
        "public final class TaskAward", "static final class TaskAward", 1),
    "@@FOREST@@": method("model/task/antForest/AntForestV2.java", "private static TaskAttemptPolicy.ProbeResult forestVitalityStatus("),
    "@@QUEST@@": method("model/task/omegakoiTown/OmegakoiTown.java", "private static Boolean questDone("),
}
for token, value in replacements.items():
    code = code.replace(token, value)
from daily_task_fixture import with_daily_task
code = with_daily_task(code)
jar = next(p for p in (Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
    "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
    if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-policy-") as tmp:
    java = Path(tmp) / "MergePolicyCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "MergePolicyCheck"], check=True)
