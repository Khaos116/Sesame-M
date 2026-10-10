"""Replay actual forest task orchestration and shared attempt policy, without live RPC."""
from pathlib import Path
import os, re, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE
forest = 'model/task/antForest/AntForestV2.java'
assert 'queryVitalityYouthTaskList' not in method(forest, 'private void queryTaskList()')
assert (SOURCE/forest).read_text(encoding='utf-8').count('queryVitalityYouthTaskList();') == 1
code = r'''
import org.json.*;import java.util.*;import java.util.function.*;import java.util.regex.*;import java.util.concurrent.atomic.AtomicInteger;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestTaskPolicyCheck {
 static int totalCollected; static final String TAG="check"; static final int MAX_OPTIONAL_PLAY_ROUNDS=2;
 enum TaskStatus {TODO,FINISHED,RECEIVED}
 static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
 Field<Set<String>> AntForestVitalityTaskList=new Field<>(new HashSet<>());
 Field<Boolean> energySceneTask=new Field<>(true);
 static class MyUtils {static boolean closeUnRpc(){return false;}static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static void sleep(long n){}static boolean isLessThanSecondOfDays(long a,long b){return a/86400000L<b/86400000L;}}
 static class RuntimeInfo {static RuntimeInfo value=new RuntimeInfo();Map<String,String> data=new HashMap<>();
  static RuntimeInfo getInstance(){return value;}String getString(String k){return data.getOrDefault(k,"");}void put(String k,String v){data.put(k,v);}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}
  static void flagToday(String k,String uid){flagToday(k);}static int getforestHuntHelpToday(String type){return 0;}static void forestHuntHelpToday(String type,int n,String uid){}}
 static class ForestHuntIdMap {static void load(){}}
 static class UserIdMap {static String getCurrentUid(){return "self";}static String getShowName(String id){return id;}}
 static class Toast {static void show(String s){}}
 static class StringUtil {static String stripCountSuffix(String s){return s.replaceAll("\\(\\d+/\\d+\\)$","");}}
 static class Log {static void forest(String s){}static void farm(String s){}static void other(String s){}static void i(String s){}static void i(String a,String b){}
  static void err(String a,String b,Throwable t){}static void record(String s){}static void printStackTrace(Throwable t){}static void printStackTrace(String tag,Throwable t){}}
 static class Statistics {enum DataType {COLLECTED}static void addData(DataType t,int n){}}
 static class MessageUtil {
  static boolean checkSuccess(String tag,JSONObject j){return j.optBoolean("success");}
  static boolean checkResultCode(String tag,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}
  static boolean isRetryable(JSONObject j){return j.optBoolean("retryable");}static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("code"));}
  static boolean isUnsupportedRpc(JSONObject j){return "400000040".equals(j.optString("code"));}
  static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){}
  static void MarkTaskBlackList(String a,String b,String c,String d){}
  static void beginDeferBlackList(){}static void endDeferBlackList(boolean b){}
  static String[] autoBlackListTarget(String f){return null;}
 }
 static class GameTask {static GameTask Forest_sljyd=new GameTask();static int reports;void report(String name,int n){reports++;}}
 static class ApplicationHook {static int fallback;static String requestString(String rpc,String args){fallback++;return "{\"code\":\"102\"}";}}
 @@ALTERNATIVE@@
 @@POLICY@@
 @@AWARD@@
 @@CHOUCHOU@@
 static class AntForestRpcCall {
  static Map<String,String> replies=new HashMap<>();static int reads,awards,writes,optionalReads;
  static boolean cancelProbe,cancelAttempt,cancelAward;static JSONArray snapshot=new JSONArray(),nativeSnapshot=new JSONArray();
  static String youthOverride,optionalAfter;static int youthReads;static String awardReply,optionalOverride,home="{}",rain="{}";static int inits;
  static String finishTask(String scene,String type){writes++;if(cancelAttempt)throw new TaskCancelledException();return replies.getOrDefault(type,"{\"success\":false,\"code\":\"102\"}");}
  static String listTaskopengreen(){reads++;if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("resultCode","SUCCESS").put("taskInfoList",snapshot).toString();}
  static String receiveTaskAward(String scene,String type){awards++;if(cancelAward)throw new TaskCancelledException();return awardReply;}
  static String queryTaskList(JSONObject args){return new JSONObject().put("resultCode","SUCCESS").put("forestTasksNew",new JSONArray().put(new JSONObject().put("taskInfoList",nativeSnapshot))).toString();}
  static String queryYouthPrivilegeTaskList(String first,String source){youthReads++;if(youthOverride!=null)return youthOverride;if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("resultCode","SUCCESS").put("taskInfoList",nativeSnapshot).toString();}
  static String receiveYouthPrivilegeTaskAward(String source,String type){return receiveTaskAward(source,type);}
  static String queryHomePage(){if(cancelProbe)throw new TaskCancelledException();return home;}
  static String queryEnergyRainEndGameList(){return rain;}
  static String initTask(String type){check("GAME_DONE_SLJYD".equals(type),"Initialized unrelated game");inits++;return "{\"resultCode\":\"SUCCESS\"}";}
  static String batchQueryAndTouchopengreen(){return "{}";}
  static String queryOptionalPlay(){optionalReads++;if(cancelProbe)throw new TaskCancelledException();if(optionalAfter!=null&&writes>0)return optionalAfter;if(optionalOverride!=null)return optionalOverride;return new JSONObject().put("success",true).put("taskTriggerPlayInfo",new JSONObject().put("taskList",new JSONArray().put(new JSONObject().put("taskStatus","TODO").put("sceneCode","scene").put("taskType","unsupported").put("bizInfo",new JSONObject().put("title","browse"))))).toString();}
  static String receiveTaskAwardopengreen(String source,String scene,String type){return "{}";}
  static String listTaskopengreen(String scene,String source){if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("success",true).put("taskInfoList",snapshot).toString();}
  static String enterDrawActivityopengreen(String id,String scene,String source){return new JSONObject().put("success",true).put("drawSceneGroups",new JSONArray().put(new JSONObject().put("drawActivity",new JSONObject().put("activityId","id").put("sceneCode","activity").put("name","draw")))).toString();}
  static String finishTask4Chouchoule(String type,String scene){return finishTask(scene,type);}
  static String finishTaskopengreen(String type,String scene){return finishTask(scene,type);}
  static String queryVitalityStoreIndex(){return "{}";}
  static String exchangeTimesFromTaskopengreen(String a,String b,String c,String d,String e){return "{}";}
  static String drawopengreen(String a,String b,String c,String d){return "{}";}
  static String shareComponentRecall(String a,String b){return "{}";}
  static String confirmShareRecall(String a,String b,String c,String d){return "{}";}
 }
 static class AntFarmRpcCall {
  static String queryOptionalPlay(){return AntForestRpcCall.queryOptionalPlay();}
  static String finishTask(String type,String scene){return AntForestRpcCall.finishTask(scene,type);}
  static String receiveTaskAwardantfarm(int count,String scene,String type){return AntForestRpcCall.receiveTaskAward(scene,type);}
 }
 static class Farm {
  Field<Set<String>> AntFarmDrawMachineTaskList=new Field<>(new HashSet<>());
  @@FARM_OPTIONAL@@
 }
 @@METHODS@@
 static JSONObject task(String type,String status){return new JSONObject().put("taskBaseInfo",new JSONObject().put("sceneCode","scene").put("taskType",type).put("taskStatus",status).put("bizInfo","{\"taskTitle\":\""+type+"\",\"autoCompleteTask\":true}"));}
 static void reset(){AntForestRpcCall.youthOverride=AntForestRpcCall.optionalAfter=null;AntForestRpcCall.youthReads=0;Status.flags.clear();RuntimeInfo.value.data.clear();ApplicationHook.fallback=0;AntForestRpcCall.replies.clear();
  AntForestRpcCall.optionalOverride=null;AntForestRpcCall.snapshot=new JSONArray();AntForestRpcCall.nativeSnapshot=new JSONArray();AntForestRpcCall.awardReply="{\"success\":true,\"returnData\":\"{}\",\"incAwardCount\":1}";
  AntForestRpcCall.reads=AntForestRpcCall.awards=AntForestRpcCall.writes=AntForestRpcCall.optionalReads=0;AntForestRpcCall.cancelProbe=AntForestRpcCall.cancelAttempt=AntForestRpcCall.cancelAward=false;}
 static void expectCancel(Runnable work){try{work.run();check(false,"Task chain swallowed cancellation");}catch(TaskCancelledException expected){}}
 static int failures;static void check(boolean ok,String message){if(!ok){failures++;System.out.println("FAIL "+message);}}
 public static void main(String[] args){ForestTaskPolicyCheck f=new ForestTaskPolicyCheck();
  reset();JSONArray list=new JSONArray().put(task("reward","FINISHED")).put(task("busy","TODO"));AntForestRpcCall.snapshot=list;
  check(f.doForsetTaskList(list),"A later busy TODO must not erase successful reward's requery");
  reset();list=new JSONArray().put(task("done","TODO")).put(task("busy","TODO"));AntForestRpcCall.snapshot=new JSONArray().put(task("done","FINISHED")).put(task("busy","TODO"));
  AntForestRpcCall.replies.put("done","{\"success\":true}");check(f.doForsetTaskList(list),"Later retry must not erase confirmed completion's requery");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":false,\"code\":\"400000040\"}");AntForestRpcCall.snapshot=new JSONArray().put(task("unsupported","TODO"));
  f.doChildTask(AntForestRpcCall.snapshot,"parent");check(ApplicationHook.fallback==1,"Child task must retain unsupported-RPC fallback");
  f.doChildTask(AntForestRpcCall.snapshot,"parent");check(AntForestRpcCall.writes==1,"Same-day fallback must not repeat child writes");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":false,\"code\":\"400000040\"}");AntForestRpcCall.snapshot=new JSONArray().put(task("unsupported","TODO"));
  f.queryOptionalPlay();check(ApplicationHook.fallback==1,"Optional play must retain unsupported-RPC fallback");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
  f.queryOptionalPlay();check(AntForestRpcCall.writes==1&&AntForestRpcCall.optionalReads==3,"Forest unchanged TODO must not spin");
  Farm farm=new Farm();reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
  farm.queryOptionalPlay();check(AntForestRpcCall.writes==1&&AntForestRpcCall.optionalReads==3,"Farm unchanged TODO must not spin");
  for(boolean isFarm:new boolean[]{false,true}) {
   Runnable run=isFarm?farm::queryOptionalPlay:f::queryOptionalPlay;
   reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
   run.run();run.run();check(AntForestRpcCall.writes==1,"Unchanged optional TODO resubmitted next run");
   DailyTask.manual(()->{run.run();return null;});check(AntForestRpcCall.writes==2,"Manual optional retry blocked");
   Status.flags.clear();run.run();check(AntForestRpcCall.writes==3,"Next-day optional retry blocked");
   reset();run.run();run.run();check(AntForestRpcCall.writes==2,"Temporary optional failure marked daily");
   reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
   AntForestRpcCall.optionalAfter="{\"success\":true,\"taskTriggerPlayInfo\":{\"taskList\":false}}";
   run.run();AntForestRpcCall.optionalAfter=null;run.run();check(AntForestRpcCall.writes==2,"Malformed readback blocked future retry");
   reset();AntForestRpcCall.cancelProbe=true;expectCancel(run);
  }
  reset();AntForestRpcCall.optionalOverride="{\"success\":true,\"taskTriggerPlayInfo\":{\"taskList\":[{\"sceneCode\":\"scene\",\"taskType\":\"unsupported\",\"taskStatus\":\"TODO\",\"rightsTimes\":1}]}}";
  check(f.probeOptionalPlayStatus("scene","unsupported",0)==TaskAttemptPolicy.ProbeResult.FINISHED,"Advanced stage not recognized");
  check(farm.probeOptionalPlayStatus("scene","unsupported",1)==TaskAttemptPolicy.ProbeResult.TODO,"Same stage falsely confirmed");
  reset();AntForestRpcCall.replies.put("unsupported","{\"code\":\"400000040\"}");farm.queryOptionalPlay();
  check(ApplicationHook.fallback==1,"Farm optional fallback must use shared policy once");
  reset();AntForestRpcCall.cancelAttempt=true;expectCancel(farm::queryOptionalPlay);
  for(String raw:new String[]{"{}","{\"success\":true,\"taskTriggerPlayInfo\":{\"taskList\":false}}",
    "{\"success\":true,\"taskTriggerPlayInfo\":{\"taskList\":[{}]}}",
    "{\"success\":true,\"taskTriggerPlayInfo\":{\"taskList\":[{\"taskType\":\"neutral\",\"groupId\":\"2026cc_cz6ylyb_fz\",\"taskStatus\":\"TODO\",\"sceneCode\":\"scene\",\"bizInfo\":{\"title\":\"recharge\"}}]}}"}){
   reset();AntForestRpcCall.optionalOverride=raw;f.queryOptionalPlay();farm.queryOptionalPlay();
   check(AntForestRpcCall.writes==0&&ApplicationHook.fallback==0,"Malformed/transaction optional task submitted");
  }
  reset();AntForestRpcCall.nativeSnapshot=new JSONArray().put(task("nativeReward","FINISHED"));AntForestRpcCall.awardReply="{\"success\":false,\"code\":\"102\"}";
  f.queryYouthForestTask(new YouthForestRoute("nativeFirst","source","nativeReward","reward"));check(!Status.hasFlagToday("vitalityTask::nativeFirst"),"Absence in vitality list must not confirm failed native-list award");
  f.queryYouthForestTask(new YouthForestRoute("nativeFirst","source","nativeReward","reward"));check(AntForestRpcCall.awards==2,"Failed native award must remain retryable today");
  AntForestRpcCall.nativeSnapshot=new JSONArray().put(task("nativeReward","RECEIVED"));f.queryYouthForestTask(new YouthForestRoute("nativeFirst","source","nativeReward","reward"));check(Status.hasFlagToday("vitalityTask::nativeFirst"),"Explicit matching received state still confirms award");
  reset();YouthForestRoute route=new YouthForestRoute("nativeFirst","source","nativeReward","reward");
  f.queryYouthForestTask(route);f.queryYouthForestTask(route);check(AntForestRpcCall.youthReads==1,"Absent youth tasks queried repeatedly");
  check(!Status.hasFlagToday("vitalityTask::nativeFirst"),"Absent youth task logged as successful reward");
  DailyTask.manual(()->{f.queryYouthForestTask(route);return null;});check(AntForestRpcCall.youthReads==2,"Manual youth recheck blocked");
  Status.flags.clear();f.queryYouthForestTask(route);check(AntForestRpcCall.youthReads==3,"Next-day youth query blocked");
  for(String raw:List.of("{}","{\"success\":false}","{\"success\":true}","{\"success\":true,\"taskInfoList\":[{}]}","{\"success\":true,\"taskGroupList\":[{}]}")) {
   reset();AntForestRpcCall.youthOverride=raw;f.queryYouthForestTask(route);f.queryYouthForestTask(route);
   check(AntForestRpcCall.youthReads==2&&Status.flags.isEmpty(),"Failed/malformed youth query marked absent");
  }
  for(int variant=0;variant<5;variant++) {
   reset();JSONObject response=new JSONObject().put("success",true);
   JSONArray mixed=new JSONArray().put(task("nativeReward","FINISHED"));
   if(variant==0)mixed.put(new JSONObject());
   if(variant==1)mixed.put(false);
   response.put("taskInfoList",mixed);
   if(variant==2)response.put("taskGroupList",new JSONArray().put(new JSONObject()));
   if(variant==3)response.put("result",false);
   if(variant==4)response.put("result",new JSONObject());
   AntForestRpcCall.youthOverride=response.toString();f.queryYouthForestTask(route);
   check(AntForestRpcCall.awards==1,"Unrelated malformed entry blocked valid youth reward variant="+variant);
   check(!Status.hasFlagToday("vitalityTaskAbsent::nativeFirst"),"Present target marked absent");
   mixed.put(0,task("nativeReward","RECEIVED"));AntForestRpcCall.youthOverride=response.toString();f.queryYouthForestTask(route);
   check(Status.hasFlagToday("vitalityTask::nativeFirst"),"Unrelated malformed entry blocked confirmed reward");
   mixed.remove(0);reset();AntForestRpcCall.youthOverride=response.toString();f.queryYouthForestTask(route);f.queryYouthForestTask(route);
   check(Status.flags.isEmpty()&&AntForestRpcCall.youthReads==2,"Incomplete mixed list incorrectly confirmed absence");
  }
  reset();AntForestRpcCall.youthOverride=new JSONObject().put("success",true).put("taskGroupList",new JSONArray().put(new JSONObject()).put(new JSONObject().put("taskInfoList",new JSONArray().put(false).put(task("nativeReward","FINISHED"))))).toString();
  f.queryYouthForestTask(route);check(AntForestRpcCall.awards==1,"Malformed earlier group/entry prevented later target search");
  reset();AntForestRpcCall.youthOverride=new JSONObject().put("success",true).put("taskInfoList",new JSONArray().put(task("nativeReward","FINISHED")).put(task("nativeReward","TODO"))).toString();
  f.queryYouthForestTask(route);check(AntForestRpcCall.awards==0&&Status.flags.isEmpty(),"Conflicting target states accepted");
  reset();AntForestRpcCall.cancelProbe=true;
  try{f.probeForestVitalityStatus("scene","task");check(false,"Forest probe swallowed cancellation");}catch(TaskCancelledException expected){}
  reset();AntForestRpcCall.cancelProbe=true;AntForestRpcCall.snapshot=new JSONArray().put(task("busy","TODO"));expectCancel(()->f.doForsetTaskList(AntForestRpcCall.snapshot));
  reset();AntForestRpcCall.cancelAttempt=true;AntForestRpcCall.snapshot=new JSONArray().put(task("busy","TODO"));
  expectCancel(()->f.doChildTask(AntForestRpcCall.snapshot,"parent"));expectCancel(()->f.doEnergySceneTask(AntForestRpcCall.snapshot,true));expectCancel(f::queryOptionalPlay);
  reset();AntForestRpcCall.cancelAward=true;AntForestRpcCall.snapshot=new JSONArray().put(task("reward","FINISHED"));expectCancel(()->f.doForsetTaskList(AntForestRpcCall.snapshot));
  expectCancel(()->f.awardFinishedChildren(AntForestRpcCall.snapshot));expectCancel(()->f.doEnergySceneTask(AntForestRpcCall.snapshot,false));
  ForestChouChouLe hunt=new ForestChouChouLe();reset();AntForestRpcCall.cancelProbe=true;expectCancel(()->hunt.probeChouChouLeStatus("activity","scene","type"));
  reset();AntForestRpcCall.cancelAttempt=true;AntForestRpcCall.snapshot=new JSONArray().put(task("browse","TODO"));
  expectCancel(()->hunt.chouChouLe(false,false,Set.of(),false,false,Set.of()));assert AntForestRpcCall.writes==1:"Hunt cancellation stops the second completion RPC";
  reset();totalCollected=0;AntForestRpcCall.home="{\"resultCode\":\"SUCCESS\",\"userBaseInfo\":{\"currentEnergy\":110}}";
  f.recordMonopolyAward("reward",100);check(totalCollected==10,"Protection energy delta missing");
  f.recordMonopolyAward("reward",120);f.recordMonopolyAward("reward",-1);check(totalCollected==10,"Invalid/negative energy delta counted");
  AntForestRpcCall.home="{}";f.recordMonopolyAward("reward",100);check(totalCollected==10,"Failed energy query counted");
  for(String type:List.of("GAME_DONE_SLJYD","GAME_DONE_UNKNOWN")){
   GameTask.reports=0;AntForestRpcCall.rain=new JSONObject().put("resultCode","SUCCESS").put("energyRainEndGameGroupTask",new JSONObject().put("taskInfoList",new JSONArray().put(task(type,"TODO")))).toString();
   checkAndDoEndGameTask();check(GameTask.reports==("GAME_DONE_SLJYD".equals(type)?1:0),"Energy rain reported unrelated game");
  }
  Status.flags.clear();AntForestRpcCall.rain="{\"resultCode\":\"SUCCESS\",\"energyRainEndGameGroupTask\":false}";
  checkAndDoEndGameTask();check(!Status.hasFlagToday("EnergyRain::PlayGame"),"Malformed energy rain list marked done");
  if(failures>0)throw new AssertionError(failures+" forest task policy failures");
  System.out.println("PASS forest reward/completion requery, child/optional fallback, same-day prevention and probe cancellation");
 }
}
'''
for marker, path, name in [('@@ALTERNATIVE@@','model/base/TaskAlternative.java','TaskAlternative'),('@@POLICY@@','data/task/TaskAttemptPolicy.java','TaskAttemptPolicy'),('@@AWARD@@','data/task/TaskAward.java','TaskAward'),('@@CHOUCHOU@@','model/task/antForest/ForestChouChouLe.java','ForestChouChouLe')]:
    source=(SOURCE/path).read_text(encoding='utf-8')
    source=re.sub(r'^package .*?;\s*','',source,flags=re.M)
    source=re.sub(r'^import .*?;\s*','',source,flags=re.M)
    source=source.replace('public final class '+name,'static final class '+name).replace('public class '+name,'static class '+name)
    code=code.replace(marker,source)
code=code.replace('@@FARM_OPTIONAL@@','\n'.join(method('model/task/antFarm/AntFarm.java',sig) for sig in ('private TaskAttemptPolicy.ProbeResult probeOptionalPlayStatus(', 'private void queryOptionalPlay()', 'private Outcome finishOptionalPlayTask(')))
methods=('private void recordMonopolyAward(', 'private int queryCurrentEnergy(', 'public static void checkAndDoEndGameTask(', 'private Boolean doForsetTaskList(','private static final class YouthForestRoute', 'private void queryYouthForestTask(', 'private String queryYouthForestTaskStatus(', 'private static boolean collectOpenGreenTaskInfos(', 'private static boolean appendTaskInfoList(', 'private static boolean appendTaskGroups(','private Boolean receiveTaskAward(','private static String blackTaskKey(','private Outcome attemptFinishTask(','private TaskAttemptPolicy.ProbeResult probeForestVitalityStatus(','private static TaskAttemptPolicy.ProbeResult forestVitalityStatus(','private boolean awardFinishedChildren(','private boolean doChildTask(','private boolean doEnergySceneTask(','private Outcome attemptSceneTask(','private TaskAttemptPolicy.ProbeResult probeOptionalPlayStatus(', 'private void queryOptionalPlay()')
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in methods)).replace('Outcome','TaskAttemptPolicy.Outcome')
# The shared class declares the enum itself, so restore that declaration and its inner references.
code=code.replace('enum TaskAttemptPolicy.Outcome','enum Outcome')
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
from daily_task_fixture import with_daily_task
code=with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-forest-task-policy-') as tmp:
    java=Path(tmp)/'ForestTaskPolicyCheck.java';java.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestTaskPolicyCheck'],check=True)
