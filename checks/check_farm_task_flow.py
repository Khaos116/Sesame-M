"""Replay real farm list/sign/claim loops: daily signs cannot suppress TODO tasks."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE
from daily_task_fixture import with_daily_task

farm = 'model/task/antFarm/AntFarm.java'
code = r'''
import org.json.*;import java.util.*;
public class FarmFlowCheck {
 static final String TAG="farm";static final int MAX_FARM_TASK_ROUNDS=20;
 boolean farmTaskAwardBusy;Set<String> farmTaskAttempted;
 int foodStock=0,foodStockLimit=1800,unReceiveTaskAward;
 enum TaskStatus {TODO,FINISHED,RECEIVED}
 static class Field {Set<String> value=new HashSet<>();Set<String> getValue(){return value;}}
 Field AntFarmDoFarmTaskList=new Field();
 static class Status {static String uid="A";static Set<String> flags=new HashSet<>();
  static boolean hasFlagToday(String s){return flags.contains(uid+s);}static void flagToday(String s){flags.add(uid+s);}
  static void clearFlag(String s){flags.remove(uid+s);}}
 static class Log {static List<String> lines=new ArrayList<>();static void record(String s){lines.add(s);}static void farm(String s){lines.add(s);}
  static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class MyUtils {static JSONObject newJSONObject(String s){return new JSONObject(s);}}
 static class TimeUtil {static void sleep(int n){}}
 static class MessageUtil {static boolean checkMemo(String t,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}
  static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}
  static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){}}
 static class TaskAttemptPolicy {enum ProbeResult {UNKNOWN}}
 static class TaskAward {static boolean confirmReceivedOrBlackList(String a,java.util.function.Function<String,TaskAttemptPolicy.ProbeResult> p,String b,String c,Runnable r,java.util.function.Consumer<String> l){return false;}}
 TaskAttemptPolicy.ProbeResult probeFarmStatus(String s){return TaskAttemptPolicy.ProbeResult.UNKNOWN;}
 void add2FoodStock(int n){foodStock+=n;}
 static class AntFarmRpcCall {
  static JSONArray rows;static int reads,claims,signs;static boolean busy;
  static String listFarmTask(){reads++;return new JSONObject().put("memo","SUCCESS").put("farmTaskList",rows)
   .put("signList",new JSONObject().put("currentSignKey","today").put("signList",new JSONArray().put(new JSONObject().put("signKey","today").put("signed",true)))).toString();}
  static String sign(){signs++;return "{}";}
  static String receiveFarmTaskAward(String id,String type){claims++;if(busy)return "{\"memo\":\"FAIL\",\"resultCode\":\"102\"}";
   for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(id.equals(row.optString("taskId")))row.put("taskStatus","RECEIVED");}return "{\"memo\":\"SUCCESS\"}";}
 }
 int completions;
 boolean doFarmTask(JSONObject task){completions++;for(int i=0;i<AntFarmRpcCall.rows.length();i++){JSONObject r=AntFarmRpcCall.rows.optJSONObject(i);if(r.optString("taskId").equals(task.optString("taskId")))r.put("taskStatus","FINISHED");}return true;}
 @@METHODS@@
 static JSONObject task(String id,String state){return new JSONObject().put("taskId",id).put("bizKey",id).put("title",id).put("taskStatus",state).put("awardType","ALLPURPOSE").put("awardCount",30);}
 public static void main(String[] args){
  FarmFlowCheck f=new FarmFlowCheck();Status.flagToday("farm::sign");DailyTask.done("farm::familySign");
  AntFarmRpcCall.rows=new JSONArray().put(task("browse","TODO"));f.runFarmTaskRounds();
  assert f.completions==1&&AntFarmRpcCall.claims==1&&AntFarmRpcCall.signs==0 : "signed today must not stop ordinary feed tasks";
  AntFarmRpcCall.rows=new JSONArray().put(task("newBrowse","TODO"));f.runFarmTaskRounds();assert f.completions==2 : "later tasks still run";
  AntFarmRpcCall.rows=new JSONArray().put(task("manualBrowse","TODO"));FarmFlowCheck manualFarm=f;DailyTask.manual(()->{manualFarm.runFarmTaskRounds();return null;});assert f.completions==3;
  Status.uid="B";assert !Status.hasFlagToday("farm::sign");AntFarmRpcCall.rows=new JSONArray().put(task("accountB","TODO"));f.runFarmTaskRounds();assert f.completions==4;
  f=new FarmFlowCheck();AntFarmRpcCall.busy=true;AntFarmRpcCall.claims=0;
  AntFarmRpcCall.rows=new JSONArray().put(task("claimBusy","FINISHED")).put(task("stillTodo","TODO")).put(task("otherClaim","FINISHED"));
  f.runFarmTaskRounds();assert f.completions==1 : "busy claim suppressed later TODO";
  assert AntFarmRpcCall.claims==1 : "busy claim must not be repeated";
  f=new FarmFlowCheck();AntFarmRpcCall.busy=false;f.AntFarmDoFarmTaskList.value.add("blocked");
  AntFarmRpcCall.rows=new JSONArray().put(task("blocked","TODO")).put(task("allowed","TODO"));f.runFarmTaskRounds();
  assert f.completions==1&&Log.lines.stream().anyMatch(s->s.contains("blocked")&&s.contains("黑名单"));
  System.out.println("PASS daily signs/manual/account isolation, later TODO after busy claim, bounded claim retries and blacklist diagnostics");
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(farm,s) for s in (
 'private void runFarmTaskRounds(', 'private boolean alreadyTried(', 'private static int pendingAward(',
 'private static boolean multiStagePending(', 'private static boolean isUnsupportedFarmTask(',
 'private int[] listFarmTask(', 'private Boolean sign(', 'private Boolean receiveFarmTaskAward(')))
code=with_daily_task(code)
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=next(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
with tempfile.TemporaryDirectory(prefix='sesame-farm-flow-') as tmp:
 p=Path(tmp)/'FarmFlowCheck.java';p.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(p)],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'FarmFlowCheck'],check=True)
