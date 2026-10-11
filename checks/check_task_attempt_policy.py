"""Replay merged production policy, blacklist buffering, ownership and nested-list checks."""
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
 static class AntFarmRpcCall {static String response;static int calls;static JSONObject stage;static JSONArray prefix=new JSONArray();static boolean advance=true;
  static String listFarmTask(){return new JSONObject().put("memo","SUCCESS").put("farmTaskList",stage==null?new JSONArray(prefix.toString()):new JSONArray(prefix.toString()).put(stage)).toString();}
  static String doFarmTask(String key){calls++;if(stage!=null&&advance)stage.put("rightsTimes",stage.optInt("rightsTimes")+1);return response;}
  static String finishTask(String type,String scene){return doFarmTask(type);}}
 static class FarmStageCheck {
  static final int MAX_FARM_TASK_ROUNDS=20;Set<String> farmTaskAttempted;
  enum TaskStatus {TODO,FINISHED,RECEIVED}
  static class Field {Set<String> getValue(){return Collections.emptySet();}}Field AntFarmDoFarmTaskList=new Field();
  boolean sign(JSONObject task){return false;}
  boolean receiveFarmTaskAward(JSONObject task){AntFarmRpcCall.stage.put("taskStatus","RECEIVED");return true;}
  TaskAttemptPolicy.Outcome doVideoTask(String title){return TaskAttemptPolicy.Outcome.DONE;}
  boolean doAnswerTask(String title){return true;}
  @@FARM_STAGE@@
 }
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
  static List<String> records=new ArrayList<>();static void other(String s){}static void record(String s){records.add(s);}static void farm(String s){}static void err(String a,String b,Throwable t){}}
 static class RpcRequestGuard {static String errorMessage(JSONObject j){return j.optString("resultDesc");}}
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
  static boolean checkMemo(String tag,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}
  @@RETRY@@
  static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}
  static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}
  static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}
  @@CHECKBLACK@@
  static int blacklistChecks;static void doCheckResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){blacklistChecks++;}
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
  for(String raw:new String[]{"", "{}", "{error:'TRANSPORT_ERROR'}", "{error:1009}", "{error:48}", "{resultDesc:'请进行验证后继续'}"}){
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
  int blackBeforeLocal=MessageUtil.blacklistChecks;
  JSONObject localSkip=new JSONObject().put("error","RPC_SKIPPED").put("code","400000040");
  for(int i=0;i<3;i++)MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList","local",localSkip);
  assert MessageUtil.blacklistChecks==blackBeforeLocal : "local rejection counted toward blacklist";
  MessageUtil.checkResultCodeAndMarkTaskBlackList("AntFarmDoFarmTaskList","server",new JSONObject().put("code","400000040"));
  assert MessageUtil.blacklistChecks==blackBeforeLocal+1 : "actual server rejection lost blacklist evaluation";

  int logsBeforeSkip=logs.size(),marksBeforeSkip=MessageUtil.marks;
  assert TaskAttemptPolicy.handle("localSkip","title",null,()->{
   MessageUtil.MarkTaskBlackList("a","b","c","d");return TaskAttemptPolicy.Outcome.SKIPPED;},logs::add,
   new TaskAttemptPolicy.Site(null,null,null,null,(TaskAttemptPolicy.StatusProbe)k->{throw new AssertionError("local skip queried list");}))==TaskAttemptPolicy.Outcome.SKIPPED;
  assert logs.size()==logsBeforeSkip&&MessageUtil.marks==marksBeforeSkip&&!Status.hasFlagToday("attemptV2::localSkip");
  int runtimeLogs=Log.records.size();
  AntFarmRpcCall.response=localSkip.toString();assert new MergePolicyCheck().attemptFarmTask("local","key","")==TaskAttemptPolicy.Outcome.SKIPPED;
  assert Log.records.size()==runtimeLogs : "known unsupported local skip logged";
  AntFarmRpcCall.response=new JSONObject().put("error","RPC_SKIPPED").put("localRuleSkip",true).put("resultDesc","跳过GR已知异常任务").toString();
  assert new MergePolicyCheck().attemptFarmTask("GR","TAO_GOLDEN_V2","")==TaskAttemptPolicy.Outcome.SKIPPED;
  assert Log.records.size()==runtimeLogs : "GR local rule misreported as pause";
  AntFarmRpcCall.response=new JSONObject().put("error","RPC_SKIPPED").put("resultDesc","请求异常暂停中，剩余30秒").toString();
  assert new MergePolicyCheck().attemptFarmTask("paused","key","")==TaskAttemptPolicy.Outcome.SKIPPED;
  assert Log.records.size()==runtimeLogs+1&&Log.records.get(runtimeLogs).contains("剩余30秒")&&Log.records.get(runtimeLogs).contains("未发送RPC") : "pause reason missing";

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
  FarmStageCheck stages=new FarmStageCheck();AntFarmRpcCall.response="{\"resultCode\":\"SUCCESS\"}";
  AntFarmRpcCall.stage=new JSONObject().put("taskId","game").put("bizKey","multistage_gametask")
   .put("title","8 x 30g").put("taskStatus","TODO").put("rightsTimes",3).put("rightsTimesLimit",8);
  for(int n=3;n<8;n++){
   assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "stage "+(n+1)+" stopped while next stage remains TODO";
   assert AntFarmRpcCall.stage.optInt("rightsTimes")==n+1;
  }
  AntFarmRpcCall.stage.put("rightsTimes",0).put("taskStatus","FINISHED").put("bizKey","stalled");AntFarmRpcCall.advance=false;
  assert !stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "old unclaimed reward cannot prove next stage completed";
  int stageCalls=AntFarmRpcCall.calls;stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString()));
  assert AntFarmRpcCall.calls==stageCalls : "same failed stage retried";
  AntFarmRpcCall.stage.put("rightsTimes",1);AntFarmRpcCall.advance=true;
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "previous failed stage blocked a new stage";
  for(String state:List.of("TODO","FINISHED","RECEIVED")) {
   AntFarmRpcCall.stage.put("rightsTimes",4).put("taskStatus",state).put("bizKey","multistage_gametask_restart_after_four_"+state)
    .put("awardCount",120).put("alreadyReceiveStageAwardCount",120);
   Status.flagToday("attemptV2::farm__task__multistage_gametask_restart_after_four_"+state);
   stages=new FarmStageCheck();stages.runFarmTaskRounds();
   assert AntFarmRpcCall.stage.optInt("rightsTimes")==8 : "restart after four lost remaining stages: "+state;
   int completeCalls=AntFarmRpcCall.calls;new FarmStageCheck().runFarmTaskRounds();
   assert AntFarmRpcCall.calls==completeCalls : "completed eight stages repeated after restart";
  }
  AntFarmRpcCall.stage.put("rightsTimes",4).put("bizKey","invalid_progress");
  JSONObject beforeStage=new JSONObject(AntFarmRpcCall.stage.toString());
  for(Object invalid:List.of("bad",true,3,9,4.5)) {
   AntFarmRpcCall.stage.put("rightsTimes",invalid);
   assert stages.probeFarmProgress(beforeStage)==TaskAttemptPolicy.ProbeResult.UNKNOWN : "invalid progress accepted: "+invalid;
  }
  AntFarmRpcCall.stage.put("rightsTimes",4).put("taskStatus","FINISHED").put("alreadyReceiveStageAwardCount",0);
  AntFarmRpcCall.advance=false;assert !stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString()));
  AntFarmRpcCall.stage.put("alreadyReceiveStageAwardCount",120);AntFarmRpcCall.advance=true;
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "claiming prior reward did not unblock next stage";
  AntFarmRpcCall.stage=null;
  AntFarmRpcCall.stage=new JSONObject().put("taskId","game").put("bizKey","ordinary_terminal")
   .put("title","terminal").put("taskStatus","RECEIVED").put("rightsTimes",4).put("rightsTimesLimit",8)
   .put("awardCount",120).put("alreadyReceiveStageAwardCount",120);
  int terminalCalls=AntFarmRpcCall.calls;new FarmStageCheck().runFarmTaskRounds();
  assert AntFarmRpcCall.calls==terminalCalls : "unrelated RECEIVED task was reopened";
  assert FarmStageCheck.pendingAward(AntFarmRpcCall.stage)==0 : "received rewards shown as pending";
  AntFarmRpcCall.stage.put("taskStatus","TODO").put("bizKey","missing_zero");AntFarmRpcCall.stage.remove("rightsTimes");
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "missing initial TODO count regressed from zero";
  AntFarmRpcCall.stage.put("bizKey","long_"+"x".repeat(150)).put("rightsTimes",1);AntFarmRpcCall.advance=false;
  assert !stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString()));
  AntFarmRpcCall.stage.put("rightsTimes",2);AntFarmRpcCall.advance=true;
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "stage key suffix was truncated";
  AntFarmRpcCall.stage.put("bizKey","long_"+"x".repeat(150)+"other").put("rightsTimes",1);
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "long tasks with common prefixes share guard";
  AntFarmRpcCall.stage.put("taskId",123).put("bizKey","numeric_id").put("rightsTimes",1);
  assert stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString())) : "numeric task id execution/probe disagree";
  for(Object badId:new Object[]{true,new JSONObject(),new JSONArray(),-1,1.5,JSONObject.NULL}) {
   AntFarmRpcCall.stage.put("taskId",badId).put("rightsTimes",1);
   int idWrites=AntFarmRpcCall.calls;assert !stages.doFarmTask(new JSONObject(AntFarmRpcCall.stage.toString()));
   assert AntFarmRpcCall.calls==idWrites : "invalid task id sent to server";
  }
  AntFarmRpcCall.stage.put("taskId","game");
  JSONObject beforeNoise=new JSONObject(AntFarmRpcCall.stage.toString());
  AntFarmRpcCall.prefix=new JSONArray().put(new JSONObject().put("taskId",123).put("bizKey","other")).put(JSONObject.NULL);
  AntFarmRpcCall.stage.put("rightsTimes",AntFarmRpcCall.stage.optInt("rightsTimes")+1);
  assert stages.probeFarmProgress(beforeNoise)==TaskAttemptPolicy.ProbeResult.FINISHED : "unrelated malformed task blocked target progress";
  JSONObject savedStage=AntFarmRpcCall.stage;AntFarmRpcCall.stage=null;
  assert stages.probeFarmProgress(beforeNoise)==TaskAttemptPolicy.ProbeResult.UNKNOWN : "incomplete identities proved GONE";
  AntFarmRpcCall.stage=savedStage;AntFarmRpcCall.prefix=new JSONArray();
  JSONObject validBefore=new JSONObject(AntFarmRpcCall.stage.toString());AntFarmRpcCall.stage=new JSONObject();
  assert stages.probeFarmProgress(validBefore)==TaskAttemptPolicy.ProbeResult.UNKNOWN : "malformed list proved disappearance";
  AntFarmRpcCall.stage=validBefore;
  JSONObject disappeared=new JSONObject(AntFarmRpcCall.stage.toString());AntFarmRpcCall.stage=null;
  assert stages.probeFarmProgress(disappeared)==TaskAttemptPolicy.ProbeResult.GONE : "complete list disappearance not confirmed";
  System.out.println("PASS real policy/list confirmation, farm stages 4..8, deferred blacklist, retries, GMT+8 rollover, account counts, nested children, quest states and escaped payload");
 }
}
'''
replacements = {
    "@@CHECKBLACK@@":method("util/MessageUtil.java", "public static void checkResultCodeAndMarkTaskBlackList("),
    "@@FARM_STAGE@@": "\n".join(method("model/task/antFarm/AntFarm.java", s) for s in (
        "private Boolean doFarmTask(", "private static String farmTaskRoute(", "private static boolean isUnsupportedFarmTask(",
        "private Outcome attemptFarmTask(", "private TaskAttemptPolicy.ProbeResult probeFarmStatus(",
        "private TaskAttemptPolicy.ProbeResult probeFarmProgress(", "private static long npcTaskNumber(",
        "private void runFarmTaskRounds(", "private int[] listFarmTask(", "private boolean alreadyTried(",
        "private static int pendingAward(", "private static boolean multiStagePending(")).replace("Outcome", "TaskAttemptPolicy.Outcome"),
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
