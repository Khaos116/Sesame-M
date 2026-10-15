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
 static int totalCollected; static final String TAG="check"; static final int MAX_OPTIONAL_PLAY_ROUNDS=12;
 enum TaskStatus {TODO,FINISHED,RECEIVED}
 static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
 Field<Set<String>> AntForestVitalityTaskList=new Field<>(new HashSet<>());
 boolean isUnwrapAction(String type){return "ACC_UNWRAP".equals(type)||"UNWRAP".equals(type);}
 TaskAttemptPolicy.Outcome openForestLeyuanBoxes(String title){return TaskAttemptPolicy.Outcome.UNABLE;}
 TaskAttemptPolicy.Forge forestVitalityForge(String type,String scene,String title){return null;}
 static class MyUtils {static boolean closeUnRpc(){return false;}static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static void sleep(long n){}static boolean isLessThanSecondOfDays(long a,long b){return a/86400000L<b/86400000L;}}
 static class RuntimeInfo {static RuntimeInfo value=new RuntimeInfo();Map<String,String> data=new HashMap<>();
  static RuntimeInfo getInstance(){return value;}String getString(String k){return data.getOrDefault(k,"");}void put(String k,String v){data.put(k,v);}}
 static class Status {static Map<String,Integer> counts=new HashMap<>();static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int n){counts.put(k,n);}static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}
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
  static String blacklistKey;static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){blacklistKey=b;}
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
  static int optionalStage=-1;static boolean drawStages,drawWait;
  static String youthOverride,optionalAfter;static int youthReads;static String awardReply,optionalOverride,home="{}",rain="{}";static int inits;
  static String finishTask(String scene,String type){writes++;if(cancelAttempt)throw new TaskCancelledException();if(drawStages){JSONObject t=snapshot.optJSONObject(0);t.optJSONObject("taskRights").put("rightsTimes",writes);t.optJSONObject("taskBaseInfo").put("taskStatus",drawWait?"FINISHED":"TODO");return "{success:true}";}if(optionalStage>=0){optionalStage++;return "{\"success\":true}";}return replies.getOrDefault(type,"{\"success\":false,\"code\":\"102\"}");}
  static String listTaskopengreen(){reads++;if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("resultCode","SUCCESS").put("taskInfoList",snapshot).toString();}
  static String receiveTaskAward(String scene,String type){awards++;if(cancelAward)throw new TaskCancelledException();return awardReply;}
  static String queryTaskList(JSONObject args){return new JSONObject().put("resultCode","SUCCESS").put("forestTasksNew",new JSONArray().put(new JSONObject().put("taskInfoList",nativeSnapshot))).toString();}
  static String queryYouthPrivilegeTaskList(String first,String source){youthReads++;if(youthOverride!=null)return youthOverride;if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("resultCode","SUCCESS").put("taskInfoList",nativeSnapshot).toString();}
  static String receiveYouthPrivilegeTaskAward(String source,String type){return receiveTaskAward(source,type);}
  static String queryHomePage(){if(cancelProbe)throw new TaskCancelledException();return home;}
  static Deque<String> rainReplies=new ArrayDeque<>();static boolean cancelRain;
  static String queryEnergyRainEndGameList(){if(cancelRain)throw new TaskCancelledException();return rainReplies.isEmpty()?rain:rainReplies.removeFirst();}
  static String initTask(String type){check("GAME_DONE_SLJYD".equals(type),"Initialized unrelated game");inits++;return "{\"resultCode\":\"SUCCESS\"}";}
  static String batchQueryAndTouchopengreen(){return "{}";}
  static String queryOptionalPlay(){optionalReads++;if(cancelProbe)throw new TaskCancelledException();if(optionalStage>=0)return new JSONObject().put("success",true).put("taskTriggerPlayInfo",new JSONObject().put("taskList",new JSONArray().put(new JSONObject().put("taskStatus",optionalStage>=8?"RECEIVED":"TODO").put("sceneCode","scene").put("taskType","unsupported").put("rightsTimes",optionalStage).put("rightsTimesLimit",8).put("bizInfo",new JSONObject().put("title","browse"))))).toString();if(optionalAfter!=null&&writes>0)return optionalAfter;if(optionalOverride!=null)return optionalOverride;return new JSONObject().put("success",true).put("taskTriggerPlayInfo",new JSONObject().put("taskList",new JSONArray().put(new JSONObject().put("taskStatus","TODO").put("sceneCode","scene").put("taskType","unsupported").put("bizInfo",new JSONObject().put("title","browse"))))).toString();}
  static String receiveTaskAwardopengreen(String source,String scene,String type){return "{}";}
  static String listTaskopengreen(String scene,String source){if(cancelProbe)throw new TaskCancelledException();return new JSONObject().put("success",true).put("taskInfoList",snapshot).toString();}
  static String enterDrawActivityopengreen(String id,String scene,String source){return new JSONObject().put("success",true).put("drawSceneGroups",new JSONArray().put(new JSONObject().put("drawActivity",new JSONObject().put("activityId","id").put("sceneCode","activity").put("name","draw")))).toString();}
  static String finishTask4Chouchoule(String type,String scene){return finishTask(scene,type);}
  static String finishTaskopengreen(String type,String scene){return finishTask(scene,type);}
  static String queryVitalityStoreIndex(){return "{}";}
  static String exchangeTimesFromTaskopengreen(String a,String b,String c,String d,String e){return replies.getOrDefault("exchange","{}");}
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
  boolean isUnwrapAction(String type){return "ACC_UNWRAP".equals(type)||"UNWRAP".equals(type);}
  TaskAttemptPolicy.Outcome openFarmLeyuanBoxes(String title){return TaskAttemptPolicy.Outcome.UNABLE;}
  @@FARM_OPTIONAL@@
 }
 static class AntOrchardRpcCall {
  static String queryOptionalPlay(){return AntForestRpcCall.queryOptionalPlay();}
  static String finishTaskantorchard(String type,String scene){return AntForestRpcCall.finishTask(scene,type);}
  static String receiveTaskAwardantorchard(int count,String scene,String type){return AntForestRpcCall.receiveTaskAward(scene,type);}
 }
 static class Orchard {
  Field<Set<String>> AntOrchardTaskList=new Field<>(new HashSet<>());
  @@ORCHARD_OPTIONAL@@
 }
 @@METHODS@@
 static JSONObject task(String type,String status){return new JSONObject().put("taskBaseInfo",new JSONObject().put("sceneCode","scene").put("taskType",type).put("taskStatus",status).put("bizInfo","{\"taskTitle\":\""+type+"\",\"autoCompleteTask\":true}"));}
 static void reset(){AntForestRpcCall.drawStages=false;AntForestRpcCall.optionalStage=-1;AntForestRpcCall.youthOverride=AntForestRpcCall.optionalAfter=null;AntForestRpcCall.youthReads=0;Status.flags.clear();Status.counts.clear();RuntimeInfo.value.data.clear();ApplicationHook.fallback=0;AntForestRpcCall.replies.clear();
  AntForestRpcCall.optionalOverride=null;AntForestRpcCall.snapshot=new JSONArray();AntForestRpcCall.nativeSnapshot=new JSONArray();AntForestRpcCall.awardReply="{\"success\":true,\"returnData\":\"{}\",\"incAwardCount\":1}";
  AntForestRpcCall.reads=AntForestRpcCall.awards=AntForestRpcCall.writes=AntForestRpcCall.optionalReads=0;AntForestRpcCall.cancelProbe=AntForestRpcCall.cancelAttempt=AntForestRpcCall.cancelAward=false;}
 static void expectCancel(Runnable work){try{work.run();check(false,"Task chain swallowed cancellation");}catch(TaskCancelledException expected){}}
 static int failures;static void check(boolean ok,String message){if(!ok){failures++;System.out.println("FAIL "+message);}}
 public static void main(String[] args){ForestTaskPolicyCheck f=new ForestTaskPolicyCheck();
  reset();JSONArray list=new JSONArray().put(task("reward","FINISHED")).put(task("busy","TODO"));AntForestRpcCall.snapshot=list;
  check(f.doForsetTaskList(list),"A later busy TODO must not erase successful reward's requery");
  reset();JSONObject parent=task("ENERGY_XUANJIAO","FINISHED");parent.optJSONObject("taskBaseInfo").put("taskNode","PARENT");
  JSONObject manualChild=task("manual","TODO");manualChild.optJSONObject("taskBaseInfo").put("bizInfo","{taskTitle:'manual',autoCompleteTask:false}");
  parent.put("childTaskTypeList",new JSONArray().put(task("childReward","FINISHED")).put(manualChild));
  check(f.doForsetTaskList(new JSONArray().put(parent))&&AntForestRpcCall.awards==2&&AntForestRpcCall.writes==0,"Scene-card cleanup must keep parent/child awards and skip manual tasks");
  reset();list=new JSONArray().put(task("done","TODO")).put(task("busy","TODO"));AntForestRpcCall.snapshot=new JSONArray().put(task("done","FINISHED")).put(task("busy","TODO"));
  AntForestRpcCall.replies.put("done","{\"success\":true}");check(f.doForsetTaskList(list),"Later retry must not erase confirmed completion's requery");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":false,\"code\":\"400000040\"}");AntForestRpcCall.snapshot=new JSONArray().put(task("unsupported","TODO"));
  f.doChildTask(AntForestRpcCall.snapshot,"parent");check(ApplicationHook.fallback==1,"Child task must retain unsupported-RPC fallback");
  f.doChildTask(AntForestRpcCall.snapshot,"parent");check(AntForestRpcCall.writes==1,"Same-day fallback must not repeat child writes");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":false,\"code\":\"400000040\"}");AntForestRpcCall.snapshot=new JSONArray().put(task("unsupported","TODO"));
  f.queryOptionalPlay();check(ApplicationHook.fallback==1,"Optional play must retain unsupported-RPC fallback");
  reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
  f.queryOptionalPlay();check(AntForestRpcCall.writes==1&&AntForestRpcCall.optionalReads==8,"Forest unchanged TODO must stop after six probes and one list refresh");
  Farm farm=new Farm();reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
  farm.queryOptionalPlay();check(AntForestRpcCall.writes==1&&AntForestRpcCall.optionalReads==8,"Farm unchanged TODO must stop after six probes and one list refresh");
  Orchard orchard=new Orchard();
  for(Runnable run:List.<Runnable>of(farm::queryOptionalPlay,f::queryOptionalPlay,orchard::queryOptionalPlay)) {
   reset();AntForestRpcCall.optionalStage=0;run.run();check(AntForestRpcCall.optionalStage==8&&AntForestRpcCall.writes==8,"Eight-stage optional task stopped early");
   reset();AntForestRpcCall.optionalStage=4;run.run();check(AntForestRpcCall.optionalStage==8&&AntForestRpcCall.writes==4,"Remaining four stages not resumed");
   reset();AntForestRpcCall.replies.put("unsupported","{\"success\":true}");
   run.run();run.run();check(AntForestRpcCall.writes==1,"Unchanged optional TODO resubmitted next run");
   DailyTask.manual(()->{run.run();return null;});check(AntForestRpcCall.writes==2,"Manual optional retry blocked");
   Status.flags.clear();Status.counts.clear();run.run();check(AntForestRpcCall.writes==3,"Next-day optional retry blocked");
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
  Status.flags.clear();Status.counts.clear();f.queryYouthForestTask(route);check(AntForestRpcCall.youthReads==3,"Next-day youth query blocked");
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
  expectCancel(()->f.doChildTask(AntForestRpcCall.snapshot,"parent"));expectCancel(f::queryOptionalPlay);
  reset();AntForestRpcCall.cancelAward=true;AntForestRpcCall.snapshot=new JSONArray().put(task("reward","FINISHED"));expectCancel(()->f.doForsetTaskList(AntForestRpcCall.snapshot));
  expectCancel(()->f.awardFinishedChildren(AntForestRpcCall.snapshot));
  ForestChouChouLe stages=new ForestChouChouLe();reset();
  AntForestRpcCall.replies.put("exchange","{success:true}");
  check(stages.attemptExchangeVitality("id","activity","scene","type","name")==TaskAttemptPolicy.Outcome.RETRY,"missing exchange count marked successful");
  AntForestRpcCall.replies.put("exchange","{success:true,times:1}");
  check(stages.attemptExchangeVitality("id","activity","scene","type","name")==TaskAttemptPolicy.Outcome.DONE,"valid exchange rejected");
  AntForestRpcCall.snapshot=new JSONArray().put(task("stage","TODO").put("taskRights",new JSONObject().put("rightsTimes",4)));
  check(stages.probeChouChouLeStatus("activity","scene","stage",3)==TaskAttemptPolicy.ProbeResult.FINISHED,"advanced draw stage treated as failure");
  check(stages.probeChouChouLeStatus("activity","scene","stage",4)==TaskAttemptPolicy.ProbeResult.TODO,"unchanged draw stage treated as complete");
  for(String type:List.of("FOREST_NORMAL_DRAW_BROWSE","GENERIC_BROWSE"))for(boolean wait:new boolean[]{true,false}){
   reset();AntForestRpcCall.drawStages=true;AntForestRpcCall.drawWait=wait;
   AntForestRpcCall.snapshot=new JSONArray().put(task(type,"TODO").put("taskRights",new JSONObject().put("rightsTimes",0).put("rightsTimesLimit",8)));
   stages.chouChouLe(false,false,Set.of(),false,false,Set.of());
   check(AntForestRpcCall.writes==(wait?1:8),"Draw task must stop for award but continue progressing TODO");
  }
  reset();orchard.attemptFinishOptionalPlayTask("scene","task","browse(3/8)");
  check("browse".equals(MessageUtil.blacklistKey),"Orchard blacklist must use title without count suffix");
  orchard.AntOrchardTaskList.getValue().add(MessageUtil.blacklistKey);AntForestRpcCall.writes=0;
  orchard.queryOptionalPlay();check(AntForestRpcCall.writes==0,"Normalized orchard blacklist key must skip next run");
  orchard.AntOrchardTaskList.getValue().clear();
  reset();AntForestRpcCall.snapshot=new JSONArray().put(task("stage","NEW_STATE"));
  check(stages.probeChouChouLeStatus("activity","scene","stage",-1)==TaskAttemptPolicy.ProbeResult.UNKNOWN,"Unknown draw status must not permit another stage");
  ForestChouChouLe hunt=new ForestChouChouLe();reset();AntForestRpcCall.cancelProbe=true;expectCancel(()->hunt.probeChouChouLeStatus("activity","scene","type",-1));
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
  Status.flags.clear();Status.counts.clear();AntForestRpcCall.rain="{\"resultCode\":\"SUCCESS\",\"energyRainEndGameGroupTask\":false}";
  checkAndDoEndGameTask();check(!Status.hasFlagToday("EnergyRain::PlayGame"),"Malformed energy rain list marked done");
  for(String state:List.of("TODO","NOT_TRIGGER","","NEW_STATE","FINISHED","RECEIVED")) {
   Status.flags.clear();GameTask.reports=0;
   AntForestRpcCall.rain=new JSONObject().put("resultCode","SUCCESS").put("energyRainEndGameGroupTask",new JSONObject().put("taskInfoList",new JSONArray().put(task("GAME_DONE_SLJYD",state)))).toString();
   checkAndDoEndGameTask();check(Status.hasFlagToday("EnergyRain::PlayGame")==List.of("FINISHED","RECEIVED").contains(state),"Unconfirmed rain state marked done: "+state);
  }
  for(String after:List.of("FINISHED","RECEIVED","","NEW_STATE","TODO")) {
   Status.flags.clear();GameTask.reports=0;
   for(String state:List.of("TODO",after)) AntForestRpcCall.rainReplies.add(new JSONObject().put("resultCode","SUCCESS").put("energyRainEndGameGroupTask",new JSONObject().put("taskInfoList",new JSONArray().put(task("GAME_DONE_SLJYD",state)))).toString());
   checkAndDoEndGameTask();check(GameTask.reports==1&&Status.hasFlagToday("EnergyRain::PlayGame")==List.of("FINISHED","RECEIVED").contains(after),"Rain report requires confirmed readback: "+after);
  }
  Status.flags.clear();AntForestRpcCall.rain="{resultCode:'SUCCESS'}";checkAndDoEndGameTask();check(Status.flags.isEmpty(),"Missing group marked done");
  AntForestRpcCall.cancelRain=true;expectCancel(ForestTaskPolicyCheck::checkAndDoEndGameTask);AntForestRpcCall.cancelRain=false;
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
code=code.replace('@@ORCHARD_OPTIONAL@@','\n'.join(method('model/task/antOrchard/AntOrchard.java',sig) for sig in ('private TaskAttemptPolicy.ProbeResult probeOrchardOptionalPlayStatus(', 'private void queryOptionalPlay()', 'private Outcome attemptFinishOptionalPlayTask(')))
methods=('private void recordMonopolyAward(', 'private int queryCurrentEnergy(', 'private static String energyRainSljydStatus(', 'public static void checkAndDoEndGameTask(', 'private Boolean doForsetTaskList(','private static final class YouthForestRoute', 'private void queryYouthForestTask(', 'private String queryYouthForestTaskStatus(', 'private static boolean collectOpenGreenTaskInfos(', 'private static boolean appendTaskInfoList(', 'private static boolean appendTaskGroups(','private Boolean receiveTaskAward(','private static String blackTaskKey(','private Outcome attemptFinishTask(','private TaskAttemptPolicy.ProbeResult probeForestVitalityStatus(','private static TaskAttemptPolicy.ProbeResult forestVitalityStatus(','private boolean awardFinishedChildren(','private boolean doChildTask(','private TaskAttemptPolicy.ProbeResult probeOptionalPlayStatus(', 'private void queryOptionalPlay()', 'private int optionalPlayRoundLimit(')
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in methods))
# The shared class declares the enum itself, so restore that declaration and its inner references.
code=re.sub(r'(?<![.\w])Outcome\b', 'TaskAttemptPolicy.Outcome', code)
code=code.replace('enum TaskAttemptPolicy.Outcome','enum Outcome')
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
from daily_task_fixture import with_daily_task
code=with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-forest-task-policy-') as tmp:
    java=Path(tmp)/'ForestTaskPolicyCheck.java';java.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestTaskPolicyCheck'],check=True)
