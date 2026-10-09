"""Replay welfare stages, escaped RPC fields and incomplete point-certificate pagination."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

code = r'''
import org.json.*;import java.util.*;
public class MergeReviewCheck {
 static final String TAG="review";
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static long slept,max;static boolean cancelled;static void sleep(long ms){
  if(cancelled)throw new TaskCancelledException();slept+=ms;max=Math.max(max,ms);}}
 static class TaskCancelledException extends RuntimeException {}
 static class Log {static List<String> lines=new ArrayList<>();static void record(String s){lines.add(s);}
  static void other(String s){lines.add(s);}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}
  static void flagToday(String k){flags.add(k);}}
 static class MessageUtil {static int marks;static boolean checkResultCode(String t,JSONObject j){return j!=null&&j.optBoolean("success");}
  static boolean isRetryable(JSONObject j){return j.optBoolean("retryable");}static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("code"));}
  static void checkResultCodeAndMarkTaskBlackList(String l,String t,JSONObject j){marks++;}
  @@PRESETS@@
 }
 static class AlipayWelfareFundTaskList {static void clear(){}}
 static class WelfareFundTaskListMap {static void load(){}static String get(String k){return k;}
  static void add(String k,String v){}static void save(){}}
 static class WelfareFundRpcCall {
  static String state,type,reply;static int signups,sends,receives,reads,progress=-1;static boolean delayed,signupFailure,sendBusy;
  static String signConsult(){return "{}";}
  static String taskQuery(){reads++;String current=!delayed&&(sends>0||receives>0)&&reply.contains("true")?"RECEIVE_SUCCESS":state;
   JSONObject item=new JSONObject().put("taskId","id").put("taskProcessStatus",current).put("sendCampTriggerType",type)
    .put("taskExtProps",new JSONObject().put("TASK_MORPHO_DETAIL","{\"title\":\"browse\"}"));
   if(progress>=0)item.put("periodCurrentCompleteNum",progress);
   return new JSONObject().put("success",true).put("result",new JSONObject().put("taskDetailList",new JSONArray().put(item))).toString();}
  static String taskTrigger(String id,String stage){if(stage.equals("signup")){signups++;return signupFailure?"{\"success\":false,\"errorCode\":\"10000005\"}":reply;}
   if(stage.equals("send"))sends++;else receives++;return sendBusy?"{\"success\":false,\"code\":\"102\"}":reply;}
 }
 @@WELFARE@@
 static class ApplicationHook {static String args;static int writes;
  static String requestString(String rpc,String body){args=body;writes++;return "{}";}}
 static class WelfareRpc {static final String TASK_CENTER_APPLET_ID="AP1269301";@@TRIGGER@@}
 static class TownRpc {@@QUEST@@}
 static class TaskAttemptPolicy {enum ProbeResult {TODO,FINISHED,RECEIVED,GONE,UNKNOWN}}
 static class AntMemberRpcCall {static int failPage;static boolean malformed,found;
  static String queryPointCert(int page,int size){if(page==failPage)return "{\"success\":false}";
   JSONArray a=new JSONArray();if(malformed)a.put(7);if(found&&page==2)a.put(new JSONObject().put("id","cert"));
   return new JSONObject().put("success",true).put("certList",a).toString();}}
 @@PROBE@@
 static class Sports {static class Field {int getValue(){return 50;}}Field walkPaceMs=new Field();@@PACE@@}
 static class TaskAlternative {static boolean isTransactionTask(String key){return false;}}
 static class AntOrchardVisitTask {static int floats;static void floatBall(JSONObject task,int budget){floats++;}}
 static class Orchard {
  enum TaskStatus {TODO,FINISHED,RECEIVED}
  static class Field<T> {T value;Field(T value){this.value=value;}T getValue(){return value;}}
  Field<Set<String>> AntOrchardTaskList=new Field<>(Set.of());Field<Boolean> orchardFloatBallTask=new Field<>(true);
  Field<Integer> orchardVisitDailyBudget=new Field<>(1);int finished,extras;
  static class Log {static void farm(String s){}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
  @@ORCHARD_BLACK@@
  @@ORCHARD_EXTRA@@
  @@ORCHARD_HANDLE@@
  boolean finishOrchardTask(JSONObject task){finished++;return true;}void runExtraOrchardTask(JSONObject task,boolean star){extras++;}
  void verifyPendingTasksByList(){}
 }
 static void reset(){Status.flags.clear();Log.lines.clear();MessageUtil.marks=0;WelfareFundRpcCall.state="NONE_SIGNUP";
  WelfareFundRpcCall.type="USER_TRIGGER";WelfareFundRpcCall.reply="{\"success\":true}";
  WelfareFundRpcCall.signups=0;WelfareFundRpcCall.sends=0;WelfareFundRpcCall.receives=0;WelfareFundRpcCall.reads=0;
  WelfareFundRpcCall.delayed=false;WelfareFundRpcCall.signupFailure=false;WelfareFundRpcCall.sendBusy=false;WelfareFundRpcCall.progress=-1;}
 public static void main(String[] args){
  reset();WelfareFund.run(false,true,true,Set.of());
  assert WelfareFundRpcCall.signups==1&&WelfareFundRpcCall.sends==1&&WelfareFundRpcCall.reads==2;
  assert Log.lines.stream().anyMatch(s->s.contains("✅"));WelfareFund.run(false,true,true,Set.of());
  assert WelfareFundRpcCall.signups==1&&WelfareFundRpcCall.sends==1;
  reset();WelfareFundRpcCall.signupFailure=true;WelfareFund.run(false,true,false,Set.of());
  assert WelfareFundRpcCall.sends==0&&MessageUtil.marks==0;WelfareFund.run(false,true,false,Set.of());assert WelfareFundRpcCall.signups==1;
  reset();WelfareFundRpcCall.signupFailure=true;WelfareFund.run(false,true,true,Set.of());assert MessageUtil.marks==1;
  reset();WelfareFundRpcCall.delayed=true;WelfareFund.run(false,true,true,Set.of());
  assert Log.lines.stream().noneMatch(s->s.contains("✅"));WelfareFund.run(false,true,true,Set.of());
  assert WelfareFundRpcCall.sends==1&&MessageUtil.marks==0;
  WelfareFundRpcCall.delayed=false;WelfareFund.run(false,true,true,Set.of());assert Status.hasFlagToday("member::welfareFundTask::id::done");
  reset();WelfareFundRpcCall.delayed=true;WelfareFundRpcCall.sendBusy=true;WelfareFund.run(false,true,false,Set.of());
  WelfareFundRpcCall.sendBusy=false;WelfareFund.run(false,true,false,Set.of());assert WelfareFundRpcCall.signups==1&&WelfareFundRpcCall.sends==2;
  reset();WelfareFundRpcCall.delayed=true;WelfareFundRpcCall.state="SIGNUP_COMPLETE";WelfareFundRpcCall.progress=0;
  WelfareFund.run(false,true,true,Set.of());WelfareFund.run(false,true,true,Set.of());assert WelfareFundRpcCall.sends==1;
  WelfareFundRpcCall.progress=1;WelfareFund.run(false,true,true,Set.of());assert WelfareFundRpcCall.sends==2;
  reset();WelfareFundRpcCall.state="SIGNUP_COMPLETE";WelfareFundRpcCall.type="EVENT_TRIGGER";WelfareFund.run(false,true,true,Set.of());
  assert WelfareFundRpcCall.signups==0&&WelfareFundRpcCall.sends==1;
  reset();WelfareFundRpcCall.state="TO_RECEIVE";WelfareFund.run(false,true,true,Set.of());assert WelfareFundRpcCall.receives==1&&WelfareFundRpcCall.sends==0;
  for(String state:List.of("UNKNOWN","RECEIVE_SUCCESS")){reset();WelfareFundRpcCall.state=state;WelfareFund.run(false,true,true,Set.of());assert WelfareFundRpcCall.sends==0;}
  reset();WelfareFundRpcCall.type="UNKNOWN";WelfareFund.run(false,true,true,Set.of());assert WelfareFundRpcCall.signups==0;
  reset();WelfareFund.run(false,true,true,Set.of("browse"));assert WelfareFundRpcCall.signups==0;
  String escaped="quoted\"\\\n";WelfareRpc.taskTrigger(escaped,"signup");
  assert escaped.equals(new JSONArray(ApplicationHook.args).optJSONObject(0).optString("appletId"));
  TownRpc.completeQuest(escaped,escaped);JSONObject request=new JSONArray(ApplicationHook.args).optJSONObject(0);
  assert escaped.equals(request.optString("questId"))&&escaped.equals(request.optString("scenarioId"));
  int writes=ApplicationHook.writes;try{TownRpc.completeQuest("","");throw new AssertionError();}catch(IllegalArgumentException expected){}
  assert ApplicationHook.writes==writes;
  AntMemberRpcCall.failPage=2;assert probePointCertStatus(2,8,"cert")==TaskAttemptPolicy.ProbeResult.UNKNOWN;
  AntMemberRpcCall.failPage=0;AntMemberRpcCall.malformed=true;assert probePointCertStatus(2,8,"cert")==TaskAttemptPolicy.ProbeResult.UNKNOWN;
  AntMemberRpcCall.malformed=false;AntMemberRpcCall.found=true;assert probePointCertStatus(2,8,"cert")==TaskAttemptPolicy.ProbeResult.TODO;
  AntMemberRpcCall.found=false;assert probePointCertStatus(2,8,"cert")==TaskAttemptPolicy.ProbeResult.RECEIVED;
  TimeUtil.slept=0;TimeUtil.max=0;new Sports().paceWalk(22000);assert TimeUtil.slept==1100000&&TimeUtil.max<=5000;
  TimeUtil.cancelled=true;try{new Sports().paceWalk(100);throw new AssertionError();}catch(TaskCancelledException expected){}
  TimeUtil.cancelled=false;
  assert MessageUtil.presetBlackList("AntForestV2","AntForestVitalityTaskList").size()==7;
  assert MessageUtil.presetBlackList("AntDodo","AntDodoTaskList").contains("每日任务：帮好友抽卡");
  assert MessageUtil.presetBlackList("AntFarm","AntFarmDoFarmTaskList").contains("到店付款");
  assert MessageUtil.presetBlackList("AntOrchard","AntOrchardTaskList").contains("下载蚂蚁阿福看健康攻略");
  Orchard orchard=new Orchard();JSONObject display=new JSONObject().put("title","browse");
  JSONObject blocked=new JSONObject().put("taskStatus","TODO").put("taskId","ORCHARD_NORMAL_KUAISHOU_MAX").put("taskDisplayConfig",display);
  orchard.handleTaskList(new JSONArray().put(blocked));assert orchard.finished==0;
  display.put("floatBallConfig",new JSONObject());orchard.handleTaskList(new JSONArray().put(blocked));assert AntOrchardVisitTask.floats==0;
  blocked.put("taskId","normal");orchard.handleTaskList(new JSONArray().put(blocked));assert AntOrchardVisitTask.floats==1;
  display.remove("floatBallConfig");blocked.put("taskId","TAOBAO2").put("actionType","VISIT").put("taskPlantType","TAOBAO");
  orchard.handleTaskList(new JSONArray().put(blocked));assert orchard.extras==1&&orchard.finished==0;
  System.out.println("PASS real welfare stages/dedup/blacklist switch, JSON escaping and partial-page receipt rejection");
 }
}
'''
for token, path, signature in (
    ("@@WELFARE@@", "model/task/antMember/WelfareFund.java", "public class WelfareFund"),
    ("@@TRIGGER@@", "model/task/antMember/WelfareFundRpcCall.java", "public static String taskTrigger("),
    ("@@QUEST@@", "model/task/omegakoiTown/OmegakoiTownRpcCall.java", "public static String completeQuest("),
    ("@@PROBE@@", "model/task/antMember/AntMember.java", "private static TaskAttemptPolicy.ProbeResult probePointCertStatus("),
    ("@@PACE@@", "model/task/antSports/AntSports.java", "private void paceWalk("),
    ("@@ORCHARD_EXTRA@@", "model/task/antOrchard/AntOrchard.java", "private static boolean isExtraOrchardBrowse("),
    ("@@ORCHARD_HANDLE@@", "model/task/antOrchard/AntOrchard.java", "private void handleTaskList("),
):
    code = code.replace(token, method(path, signature).replace("public class WelfareFund", "static class WelfareFund", 1))

preset_source = (SOURCE / "util/MessageUtil.java").read_text(encoding="utf-8")
presets = re.search(r"private static final Map<String, Set<String>> PRESET_BLACKLIST[\s\S]*?\n    }", preset_source)[0]
presets += method("util/MessageUtil.java", "private static Set<String> setOf(")
presets += method("util/MessageUtil.java", "public static Set<String> presetBlackList(")
code = code.replace("@@PRESETS@@", presets)
orchard_source = (SOURCE / "model/task/antOrchard/AntOrchard.java").read_text(encoding="utf-8")
code = code.replace("@@ORCHARD_BLACK@@", re.search(
    r"private static final Set<String> ORCHARD_TASK_BLACKLIST[\s\S]*?\n    }", orchard_source)[0])

jar = next(p for p in (Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
    "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
    if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-review-") as tmp:
    java = Path(tmp) / "MergeReviewCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "MergeReviewCheck"], check=True)

# Restored M exclusions must precede every dispatch, including floatBall.
orchard = method("model/task/antOrchard/AntOrchard.java", "private void handleTaskList(")
assert orchard.index("ORCHARD_TASK_BLACKLIST.contains") < orchard.index("AntOrchardVisitTask.floatBall")
assert "attemptVideoTask" not in method("model/task/antFarm/AntFarm.java", "private Boolean doFarmTask(")
for path in (SOURCE / "model/task").rglob("*.java"):
    assert "MarkTaskBlackListPermanent(" not in path.read_text(encoding="utf-8"), path
