'''Exercise production feeding, scheduling, wake and recall with isolated RPC/timer boundaries.'''
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import SOURCE, method
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))
jar=sorted(p for p in (cache/'caches/modules-2/files-2.1/org.json/json').glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
farm='model/task/antFarm/AntFarm.java'
source=(SOURCE/farm).read_text(encoding='utf-8')
start=source.index('    private void autoFeedAnimal()')
flow=source[start:source.index('    private void animalWakeUpTime',start)]
feeding_start='            step("喂鸡前道具"' if '            step("喂鸡前道具"' in source else '            step("喂鸡"'
feeding_step=source[source.index(feeding_start):source.index('            // 小鸡换装')]
methods=method(farm,'private Boolean receiveFarmTaskAward(')+method(farm,'private static int pendingAward(')+method(farm,'private void add2FoodStock(')+method(farm,'private void feedAnimal(')+method(farm,'private void step(')+method(farm,'private static int rankingInt(')
if 'private void feedHungryAnimal(' in source:
    methods+=method(farm,'private void feedHungryAnimal(')
rpc='model/task/antFarm/AntFarmRpcCall.java'
rpc_methods=method(rpc,'public static String syncAnimalStatus(String farmId)')
if 'public static String syncAnimalStatus(String farmId, boolean queryFoodStockInfo)' in (SOURCE/rpc).read_text(encoding='utf-8'):
    rpc_methods+=method(rpc,'public static String syncAnimalStatus(String farmId, boolean queryFoodStockInfo)')
methods+='\n'.join(method(farm,sig) for sig in ('private Boolean animalWakeUpNow(', 'private Boolean animalWakeUp(', 'private Boolean familyWakeUp('))
code=r'''
import java.util.*;import java.util.concurrent.TimeUnit;import org.json.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class AutoFeedCheck {
 static class TaskCancelledException extends RuntimeException {}
 static class Field {boolean value=true;boolean getValue(){return value;}}
 static class IntField {int getValue(){return 1;}}
 static class UserIdMap {static String uid="self";static String getCurrentUid(){return uid;}static String getCurrentMaskName(){return uid;}}
 static class TimeUtil {static String getCommonDate(long n){return "time";}static void sleep(long n){}}
 static class MyUtils {static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkMemo(String t,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){}}
 static class Log {static List<String> lines=new ArrayList<>();static void record(String s){lines.add(s);}static void farm(String s){lines.add(s);}static void err(String t,String s,Throwable e){lines.add(s);}static void i(String t,String s){lines.add(s);}static void printStackTrace(Throwable e){throw new AssertionError(e);}}
 static class TaskAttemptPolicy {enum ProbeResult {UNKNOWN}}
 static class TaskAward {static boolean confirmReceivedOrBlackList(String a,java.util.function.Function<String,TaskAttemptPolicy.ProbeResult> p,String b,String c,Runnable mark,java.util.function.Consumer<String> log){return false;}}
 enum AnimalFeedStatus {HUNGRY,EATING,SLEEPY}enum AnimalInteractStatus {HOME,STEALING}
 static class AntFarm {enum ToolType {BIG_EATER_TOOL}}
 static class Animal {String animalFeedStatus="EATING",animalInteractStatus="HOME";}
 static class AntFarmRpcCall {
  static String response,feedReply,taskListReply,afterAwardState;static Integer awardDelta;static JSONArray awards;static int taskQueries,claims;static boolean failAward,cancelAward,noAwardEffect,unknownAwardApplied;static List<String> claimedIds=new ArrayList<>();static Runnable onAward;static List<String> order=new ArrayList<>();static boolean stockRequested;static int queries,feeds,wakes;static boolean full,fail,cancelQuery,cancelFeed,throwFeed,switchUid;
  static String syncAnimalStatus(String farm){stockRequested=false;return query(farm);}static String syncAnimalStatus(String farm,boolean stock){stockRequested=stock;return query(farm);}static String query(String farm){order.add("stock");queries++;if(cancelQuery)throw new TaskCancelledException();if(switchUid)UserIdMap.uid="other";return response;}
  static String feedAnimal(String farm){order.add("feed");assert farm.equals("farm");feeds++;if(feedReply!=null)return feedReply;if(cancelFeed)throw new TaskCancelledException();if(throwFeed)throw new IllegalStateException("transport");
   if(full){full=false;return "{\"memo\":\"FAIL\",\"resultCode\":\"331\"}";}if(fail)return "{\"memo\":\"FAIL\",\"resultCode\":\"102\"}";
   int stock=MyUtils.newJSONObject(response).optJSONObject("subFarmVO").optInt("foodStock")-180;
   response=snapshot("EATING",1,System.currentTimeMillis(),180,stock);return new JSONObject().put("memo","SUCCESS").put("foodStock",stock).toString();}
  static String listFarmTask(){taskQueries++;order.add("tasks");return taskListReply!=null?taskListReply:new JSONObject().put("memo","SUCCESS").put("farmTaskList",awards).toString();}
  static String receiveFarmTaskAward(String id,String type){claims++;claimedIds.add(id);order.add("award");assert type.equals("ALLPURPOSE");if(cancelAward)throw new TaskCancelledException();if(failAward)return "{\"memo\":\"FAIL\",\"resultCode\":\"103\"}";
   JSONObject sub=MyUtils.newJSONObject(response).optJSONObject("subFarmVO");for(int i=0;i<awards.length();i++){JSONObject row=awards.optJSONObject(i);if(id.equals(row.optString("taskId"))){if(!noAwardEffect){sub.put("foodStock",sub.optInt("foodStock")+(awardDelta!=null?awardDelta:row.optInt("awardCount")-row.optInt("alreadyReceiveStageAwardCount")));row.put("taskStatus","RECEIVED");}break;}}response=afterAwardState==null?new JSONObject().put("memo","SUCCESS").put("subFarmVO",sub).toString():afterAwardState;if(onAward!=null)onAward.run();return unknownAwardApplied?"{\"memo\":\"FAIL\",\"resultCode\":\"103\"}":"{\"memo\":\"SUCCESS\"}";}
  static String queryLoveCabin(String uid){return new JSONObject().put("memo","SUCCESS").put("ownAnimal",new JSONObject().put("sleepInfo",new JSONObject().put("sleepBeginTime",System.currentTimeMillis()-120000))).toString();}
  static String wakeUp(){wakes++;response=snapshot("HUNGRY",0,0,0,360);return "{\"memo\":\"SUCCESS\"}";}static String familyWakeUp(){return wakeUp();}
 }
 static class ApplicationHook {static String args;static String requestString(String op,String raw){assert op.equals("com.alipay.antfarm.syncAnimalStatus");args=raw;return "{}";}}
 static class RpcContract {static final String VERSION="test";static String farmId2UserId(String id){return "self";}@@RPC@@}
 static class ChildModelTask {String id;Runnable action;long time,generation=TaskLifecycle.generation();ChildModelTask(String id,String group,Runnable r,Long at){this.id=id;action=r;time=at;}
  void run(){try(TaskLifecycle.Work w=TaskLifecycle.enter(generation)){if(w!=null)action.run();}}}
 static class Farm {
  String TAG="Farm",ownerFarmId="farm";static final String CODE_FEED_TROUGH_FULL="331",SPACE_TYPE_CHICK_FAMILY="FAMILY";
  boolean enabled=true,throwTool,farmTaskAwardBusy;int foodStock=360,foodStockLimit=1800,unReceiveTaskAward;TaskAttemptPolicy.ProbeResult probeFarmStatus(String key){return TaskAttemptPolicy.ProbeResult.UNKNOWN;}Field feedAnimal=new Field(),useAccelerateTool=new Field(),useBigEaterTool=new Field(),family=new Field();IntField sleepMinutes=new IntField();Animal ownerAnimal=new Animal();
  Map<String,ChildModelTask> tasks=new LinkedHashMap<>();boolean isEnable(){return enabled;}boolean hasChildTask(String id){return tasks.containsKey(id);}void removeChildTask(String id){tasks.remove(id);}boolean addChildTask(ChildModelTask t){tasks.put(t.id,t);return true;}

  void useAccelerateTool(){if(throwTool)throw new IllegalStateException("tool");}void useFarmTool(String id,AntFarm.ToolType t){}void checkUnReceiveTaskAward(){}
  void runFeedingStep(){@@STEP@@}
  @@FLOW@@
  @@METHODS@@
  void runNext(){ChildModelTask t=tasks.values().iterator().next();t.run();tasks.remove(t.id,t);}
 }
 static String snapshot(String status,Object speed,Object start,Object food,Object stock){return new JSONObject().put("memo","SUCCESS").put("subFarmVO",new JSONObject().put("farmId","farm").put("foodStock",stock).put("foodInTrough",food).put("animals",new JSONArray().put(new JSONObject().put("masterFarmId","farm").put("animalStatusVO",new JSONObject().put("animalFeedStatus",status).put("animalInteractStatus","HOME")).put("consumeSpeed",speed).put("startEatTime",start)))).toString();}
 static Farm reset(){AntFarmRpcCall.awardDelta=null;AntFarmRpcCall.taskQueries=AntFarmRpcCall.claims=0;AntFarmRpcCall.taskListReply=AntFarmRpcCall.afterAwardState=null;AntFarmRpcCall.awards=new JSONArray();AntFarmRpcCall.failAward=AntFarmRpcCall.cancelAward=AntFarmRpcCall.noAwardEffect=AntFarmRpcCall.unknownAwardApplied=false;AntFarmRpcCall.claimedIds.clear();AntFarmRpcCall.onAward=null;AntFarmRpcCall.order.clear();UserIdMap.uid="self";Log.lines.clear();AntFarmRpcCall.feedReply=null;AntFarmRpcCall.queries=AntFarmRpcCall.feeds=AntFarmRpcCall.wakes=0;AntFarmRpcCall.full=AntFarmRpcCall.fail=AntFarmRpcCall.cancelQuery=AntFarmRpcCall.cancelFeed=AntFarmRpcCall.throwFeed=AntFarmRpcCall.switchUid=false;AntFarmRpcCall.response=snapshot("EATING",1,System.currentTimeMillis(),100,360);Farm f=new Farm();f.useAccelerateTool.value=f.useBigEaterTool.value=f.family.value=false;return f;}
 static boolean logged(String s){return Log.lines.stream().anyMatch(v->v.contains(s));}
 static JSONObject award(String id,String status,String type,int total,int received){return new JSONObject().put("taskId",id).put("bizKey",id).put("title",id).put("taskStatus",status).put("awardType",type).put("awardCount",total).put("alreadyReceiveStageAwardCount",received);}
 static void hungry(Object stock){AntFarmRpcCall.response=snapshot("HUNGRY",JSONObject.NULL,0,0,stock);}
 static void retry(Farm f){long before=System.currentTimeMillis();f.autoFeedAnimal();assert f.tasks.size()==1;ChildModelTask t=f.tasks.values().iterator().next();assert t.id.startsWith("UPDATE|FA|")&&t.time>=before+29000&&t.time<=System.currentTimeMillis()+31000;}
 public static void main(String[] args) throws Exception {Farm f=reset();switch(args[0]){
  case "hungry":hungry(180);f.autoFeedAnimal();assert AntFarmRpcCall.stockRequested:"feeding query did not request current food stock";assert AntFarmRpcCall.feeds==1&&f.foodStock==0:"confirmed hungry chicken with 180g was not fed";assert f.tasks.size()==1&&AntFarmRpcCall.taskQueries==0&&AntFarmRpcCall.claims==0:"sufficient stock must feed without task queries";break;
  case "lowStock":for(int stock:new int[]{0,179}){f=reset();hungry(stock);f.autoFeedAnimal();assert AntFarmRpcCall.feeds==0&&f.tasks.isEmpty():"low stock should wait for next main round";}break;
  case "freshStock":f.foodStock=0;hungry(180);f.autoFeedAnimal();assert AntFarmRpcCall.feeds==1:"cached empty stock overrode fresh 180g";f=reset();f.foodStock=360;hungry(179);f.autoFeedAnimal();assert AntFarmRpcCall.feeds==0:"cached stock overrode fresh insufficient stock";break;
  case "mainStale":for(String cached:List.of("EATING","SLEEPY","HUNGRY")){f=reset();f.ownerAnimal.animalFeedStatus=cached;hungry(360);f.runFeedingStep();assert AntFarmRpcCall.feeds==1:"main step missed freshly hungry chicken after cached "+cached;}break;
  case "fullRecovery":hungry(360);AntFarmRpcCall.full=true;f.feedAnimal("farm");assert AntFarmRpcCall.feeds==1&&f.tasks.size()==1;AntFarmRpcCall.response=snapshot("EATING",1,System.currentTimeMillis(),100,360);f.runNext();assert f.tasks.containsKey("FA|farm");hungry(360);f.runNext();assert AntFarmRpcCall.feeds==2&&!f.tasks.isEmpty():"331 latch killed a later hungry feed";break;
  case "sleep":for(int entry=0;entry<3;entry++){f=reset();if(entry==1)f.autoFeedAnimal();if(entry==2){AntFarmRpcCall.response="{}";retry(f);}Log.lines.clear();AntFarmRpcCall.response=snapshot("SLEEPY",0,0,0,0);int queries=AntFarmRpcCall.queries;if(entry==0){for(int round=0;round<3;round++){f.runFeedingStep();assert f.tasks.isEmpty():"sleeping main round created a retry child";}assert AntFarmRpcCall.queries==queries+3:"sleeping main must only query once per round";}else{f.runNext();assert AntFarmRpcCall.queries==queries+1&&f.tasks.isEmpty():"sleeping timer/retry continued polling";}assert AntFarmRpcCall.feeds==0&&AntFarmRpcCall.taskQueries==0&&AntFarmRpcCall.claims==0:"sleep must not feed or collect rewards";assert !logged("30 秒后重查"):"sleep was treated as an abnormal state";}break;
  case "wake":for(boolean family:new boolean[]{false,true}){f=reset();f.family.value=family;f.ownerAnimal.animalFeedStatus="SLEEPY";AntFarmRpcCall.response=snapshot("SLEEPY",0,0,0,360);f.runFeedingStep();assert AntFarmRpcCall.feeds==0&&f.tasks.isEmpty()&&AntFarmRpcCall.queries==1&&AntFarmRpcCall.taskQueries==0:"sleep before wake created polling or reward requests";assert f.animalWakeUpNow();assert AntFarmRpcCall.wakes==1&&AntFarmRpcCall.feeds==1:"wake success did not continue feeding";}break;
  case "away":for(String location:List.of("STEALING","")){f=reset();hungry(360);JSONObject j=MyUtils.newJSONObject(AntFarmRpcCall.response);j.optJSONObject("subFarmVO").optJSONArray("animals").optJSONObject(0).optJSONObject("animalStatusVO").put("animalInteractStatus",location);AntFarmRpcCall.response=j.toString();f.autoFeedAnimal();assert AntFarmRpcCall.feeds==0:"away/unknown location was fed";}break;
  case "badState":for(String raw:List.of("bad","{}","{\"memo\":\"FAIL\",\"resultCode\":\"102\"}","{\"memo\":\"SUCCESS\",\"subFarmVO\":{\"farmId\":\"farm\",\"animals\":[]}}")){f=reset();AntFarmRpcCall.response=raw;retry(f);assert AntFarmRpcCall.feeds==0;}for(int variant=0;variant<3;variant++){f=reset();JSONObject bad=MyUtils.newJSONObject(AntFarmRpcCall.response);JSONObject sub=bad.optJSONObject("subFarmVO");if(variant==0)sub.put("farmId","other");if(variant==1)sub.optJSONArray("animals").optJSONObject(0).put("masterFarmId","other");if(variant==2)sub.optJSONArray("animals").optJSONObject(0).remove("animalStatusVO");AntFarmRpcCall.response=bad.toString();retry(f);assert AntFarmRpcCall.feeds==0;}break;
  case "badStock":for(Object stock:List.of("bad","180",JSONObject.NULL,-1,180.5,4294967476L)){f=reset();hungry(stock);retry(f);assert AntFarmRpcCall.feeds==0;}break;
  case "boundedQuery":AntFarmRpcCall.response="{}";retry(f);int before=AntFarmRpcCall.queries;f.autoFeedAnimal();assert AntFarmRpcCall.queries==before&&f.tasks.size()==1;for(int i=0;i<5;i++)f.runNext();assert f.tasks.isEmpty()&&AntFarmRpcCall.queries==6&&AntFarmRpcCall.feeds==0&&logged("上限");f.autoFeedAnimal();assert f.tasks.size()==1;break;
  case "boundedFeed":for(boolean transport:new boolean[]{false,true}){f=reset();hungry(360);AntFarmRpcCall.fail=!transport;AntFarmRpcCall.throwFeed=transport;f.autoFeedAnimal();for(int i=0;i<12&&!f.tasks.isEmpty();i++)f.runNext();assert AntFarmRpcCall.feeds>0&&AntFarmRpcCall.feeds<=6&&f.tasks.isEmpty():"failed feeding must retry without an unbounded RPC loop";int attempts=AntFarmRpcCall.feeds;f.autoFeedAnimal();assert AntFarmRpcCall.feeds==attempts+1:"next main round must retry hungry feed";}break;
  case "toolFailure":f.ownerAnimal.animalFeedStatus="EATING";f.useAccelerateTool.value=true;f.throwTool=true;hungry(360);f.runFeedingStep();assert AntFarmRpcCall.feeds==1:"tool error prevented hungry feeding";break;
  case "badTiming":AntFarmRpcCall.response=snapshot("EATING",1e-300,System.currentTimeMillis(),100,360);retry(f);for(Object speed:List.of(0,-1,"bad",JSONObject.NULL)){f=reset();AntFarmRpcCall.response=snapshot("EATING",speed,System.currentTimeMillis(),100,360);retry(f);}for(Object start:List.of(0,"bad",JSONObject.NULL,System.currentTimeMillis()+60000)){f=reset();AntFarmRpcCall.response=snapshot("EATING",1,start,100,360);retry(f);}for(Object food:List.of(-1,"bad",JSONObject.NULL,0)){f=reset();AntFarmRpcCall.response=snapshot("EATING",1,System.currentTimeMillis(),food,360);retry(f);}break;
  case "timing":long now=System.currentTimeMillis();AntFarmRpcCall.response=snapshot("EATING",1,now,1.5,360);f.autoFeedAnimal();assert f.tasks.get("FA|farm").time>=now+1300;hungry(360);f.runNext();assert AntFarmRpcCall.feeds==1:"scheduled expiry did not feed fresh hunger";f.runNext();assert f.tasks.containsKey("FA|farm"):"successful feed did not restore next feeding cycle";break;
  case "timerEarly":f.autoFeedAnimal();f.runNext();assert AntFarmRpcCall.feeds==0&&!f.tasks.isEmpty():"predicted timer must refresh before consuming food";break;
  case "scope":for(boolean retry:new boolean[]{false,true})for(int mode=0;mode<4;mode++){f=reset();if(retry)AntFarmRpcCall.response="{}";f.autoFeedAnimal();if(mode==0)UserIdMap.uid="other";if(mode==1)f.ownerFarmId="other";if(mode==2)f.feedAnimal.value=false;if(mode==3)f.enabled=false;int q=AntFarmRpcCall.queries;f.runNext();assert AntFarmRpcCall.queries==q&&AntFarmRpcCall.feeds==0:"stale/disabled child issued an RPC";}break;
  case "midQuerySwitch":hungry(360);AntFarmRpcCall.switchUid=true;f.autoFeedAnimal();assert AntFarmRpcCall.feeds==0:"account changed after query but before feeding";break;
  case "cancelQuery":AntFarmRpcCall.cancelQuery=true;try{f.autoFeedAnimal();throw new AssertionError("query cancellation swallowed");}catch(TaskCancelledException expected){}assert f.tasks.isEmpty();break;
  case "cancelFeed":hungry(360);AntFarmRpcCall.cancelFeed=true;try{f.feedAnimal("farm");throw new AssertionError("feeding cancellation swallowed");}catch(TaskCancelledException expected){}assert f.tasks.isEmpty();break;
  case "generation":f.autoFeedAnimal();TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);int q=AntFarmRpcCall.queries;f.runNext();assert AntFarmRpcCall.queries==q&&AntFarmRpcCall.feeds==0;break;
  case "badAck":for(String raw:List.of("bad","{}","{memo:SUCCESS}","{memo:SUCCESS,foodStock:-1}","{memo:SUCCESS,foodStock:360}","{memo:SUCCESS,foodStock:361}","{memo:SUCCESS,foodStock:180.5}","{memo:SUCCESS,foodStock:'180'}")){f=reset();hungry(360);AntFarmRpcCall.feedReply=raw;f.autoFeedAnimal();assert AntFarmRpcCall.feeds==1&&!logged("消耗[")&&f.tasks.size()==1:"invalid stock acknowledgment was recorded as a successful feed";}break;
  case "refill":hungry(179);AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.feeds==1&&f.foodStock==29:"finished feed reward was not claimed before feeding";assert AntFarmRpcCall.order.equals(List.of("stock","tasks","award","stock","feed")):AntFarmRpcCall.order;break;
  case "refillMultiple":hungry(0);AntFarmRpcCall.awards.put(award("a","FINISHED","ALLPURPOSE",60,0)).put(award("b","FINISHED","ALLPURPOSE",120,0)).put(award("extra","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==2&&AntFarmRpcCall.feeds==1&&f.foodStock==0:"must accumulate to 180g then feed";break;
  case "refillPartial":hungry(60);AntFarmRpcCall.awards.put(award("multi","FINISHED","ALLPURPOSE",240,120));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==1&&f.foodStock==0:"already received stages must not be counted twice";break;
  case "refillInsufficient":hungry(100);AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&f.foodStock==130&&f.tasks.isEmpty();assert AntFarmRpcCall.taskQueries==1&&AntFarmRpcCall.queries==2:"low stock re-entered task claims recursively";break;
  case "refillFilters":hungry(179);AntFarmRpcCall.awards.put(award("todo","TODO","ALLPURPOSE",30,0)).put(award("received","RECEIVED","ALLPURPOSE",30,0)).put(award("meal","FINISHED","CUISINE",30,0)).put(award("piece","FINISHED","ALLPURPOSE",3,0)).put(award("invalid","FINISHED","ALLPURPOSE",30,-1)).put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claimedIds.equals(List.of("food"))&&AntFarmRpcCall.feeds==1:"claimed non-feed/unfinished/invalid rewards";break;
  case "refillNoEffect":hungry(179);AntFarmRpcCall.noAwardEffect=true;AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&AntFarmRpcCall.queries==2&&f.tasks.isEmpty():"claim acknowledgment or estimated stock caused a false feed";break;
  case "refillFailed":hungry(179);AntFarmRpcCall.failAward=true;AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&f.tasks.isEmpty();AntFarmRpcCall.failAward=false;f.autoFeedAnimal();assert AntFarmRpcCall.claims==2&&AntFarmRpcCall.feeds==1:"later main check must retry available rewards";break;
  case "refillScope":for(int mode=0;mode<4;mode++){f=reset();hungry(179);AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));Farm active=f;int mutation=mode;AntFarmRpcCall.onAward=()->{if(mutation==0)UserIdMap.uid="other";if(mutation==1)active.feedAnimal.value=false;if(mutation==2)active.enabled=false;if(mutation==3)active.ownerFarmId="other";};f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&AntFarmRpcCall.queries==1:"scope changed during claim but feeding continued";}break;
  case "refillCancel":hungry(179);AntFarmRpcCall.cancelAward=true;AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));try{f.autoFeedAnimal();throw new AssertionError("reward cancellation swallowed");}catch(TaskCancelledException expected){}assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&f.tasks.isEmpty();break;
  case "refillBadReadback":hungry(179);AntFarmRpcCall.afterAwardState="{}";AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==0&&f.tasks.size()==1:"invalid post-reward snapshot used estimated inventory";break;
  case "refillDuplicate":hungry(0);AntFarmRpcCall.awards.put(award("dup","FINISHED","ALLPURPOSE",60,0)).put(award("dup","FINISHED","ALLPURPOSE",60,0)).put(award("last","FINISHED","ALLPURPOSE",120,0));f.autoFeedAnimal();assert AntFarmRpcCall.claimedIds.equals(List.of("dup","last"))&&AntFarmRpcCall.feeds==1:"duplicate task reward claimed twice";break;
  case "refillBadList":for(String raw:List.of("{}","{memo:SUCCESS}","{memo:FAIL,farmTaskList:[]}")){f=reset();hungry(179);AntFarmRpcCall.taskListReply=raw;f.autoFeedAnimal();assert AntFarmRpcCall.taskQueries==1&&AntFarmRpcCall.claims==0&&AntFarmRpcCall.feeds==0&&f.tasks.isEmpty():"invalid reward list caused claims or a polling loop";}break;
  case "refillUnknownApplied":hungry(179);AntFarmRpcCall.unknownAwardApplied=true;AntFarmRpcCall.awards.put(award("food","FINISHED","ALLPURPOSE",30,0));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==1&&f.foodStock==29:"failed claim reply hid actual sufficient inventory";break;
  case "refillCurrentAward":for(int past:new int[]{30,120}){f=reset();hungry(150);AntFarmRpcCall.awardDelta=30;AntFarmRpcCall.awards.put(award("currentStage","FINISHED","ALLPURPOSE",30,past));f.autoFeedAnimal();assert AntFarmRpcCall.claims==1&&AntFarmRpcCall.feeds==1&&f.foodStock==0:"current-stage award rejected because historical received count is cumulative";}break;
  case "rpcStock":RpcContract.syncAnimalStatus("farm");assert !new JSONArray(ApplicationHook.args).optJSONObject(0).optBoolean("queryFoodStockInfo");try{RpcContract.class.getDeclaredMethod("syncAnimalStatus",String.class,boolean.class).invoke(null,"farm",true);}catch(NoSuchMethodException missing){throw new AssertionError("missing stock-aware sync RPC",missing);}JSONObject request=new JSONArray(ApplicationHook.args).optJSONObject(0);assert request.optBoolean("queryFoodStockInfo")&&request.optString("operType").equals("FEEDSYNC")&&request.optString("farmId").equals("farm");break;
  default:throw new AssertionError(args[0]);
 }System.out.println("PASS "+args[0]);}
}
'''.replace('@@FLOW@@',flow).replace('@@STEP@@',feeding_step).replace('@@METHODS@@',methods).replace('@@RPC@@',rpc_methods)
cases=['hungry','lowStock','freshStock','mainStale','fullRecovery','sleep','wake','away','badState','badStock','boundedQuery','boundedFeed','badTiming','timing','timerEarly','scope','midQuerySwitch','cancelQuery','cancelFeed','generation','rpcStock','toolFailure','badAck','refill','refillMultiple','refillPartial','refillInsufficient','refillFilters','refillNoEffect','refillFailed','refillScope','refillCancel','refillBadReadback','refillDuplicate','refillBadList','refillUnknownApplied','refillCurrentAward']
with tempfile.TemporaryDirectory(prefix='sesame-auto-feed-') as tmp:
    java=Path(tmp)/'AutoFeedCheck.java';java.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java),str(SOURCE/'data/task/TaskLifecycle.java')],check=True)
    failures=[]
    for case in cases:
        result=subprocess.run(['java','-Dfile.encoding=UTF-8','-ea','-cp',tmp+os.pathsep+str(jar),'AutoFeedCheck',case],capture_output=True)
        if result.returncode:
            failures.append(case);print('FAIL '+case+'\n'+(result.stdout+result.stderr).decode('utf-8',errors='replace'),flush=True)
        else:print('PASS '+case,flush=True)
    assert not failures,failures

# Replay the real recall step and delayed retry through the production lifecycle boundary.
from run import method
recall_step = source[source.index('            step("小鸡不在家处理"'):source.index('            step("道具奖励"')]
recall = r'''
import java.util.*;import java.util.concurrent.TimeUnit;import org.json.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class RecallCheck {
 static final String TAG="recall";
 static class TaskCancelledException extends RuntimeException {}
 static class Field {boolean value=true;boolean getValue(){return value;}}
 static class Choice {int value;int getValue(){return value;}}
 static class RecallAnimalType {static final int ALWAYS=0,WHEN_THIEF=1,WHEN_HUNGRY=2;}
 enum SubAnimalType {GUEST,NORMAL,PIRATE,WORK}
 enum AnimalFeedStatus {HUNGRY,EATING}
 enum AnimalInteractStatus {HOME,STEALING}
 static class Animal {String animalId="chicken",currentFarmId="other",locationType="FARM",currentFarmMasterUserId="friend";
  String animalInteractStatus="STEALING",animalFeedStatus="HUNGRY",subAnimalType="NORMAL";}
 static class UserIdMap {static String uid="self";static String getCurrentUid(){return uid;}static String getMaskName(String uid){return uid;}}
 static class MyUtils {static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static long slept;static void sleep(long n){slept+=n;}}
 static class Log {static void record(String s){}static void farm(String s){}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class MessageUtil {static boolean checkMemo(String tag,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}}
 static class AntFarmRpcCall {static boolean ok,cancel;static int calls;
  static String recallAnimal(String id,String from,String to){calls++;if(cancel)throw new TaskCancelledException();return new JSONObject().put("memo",ok?"SUCCESS":"FAIL").put("foodHaveStolen",0).toString();}
  static String farmId2UserId(String id){return id;}static String orchardRecallAnimal(String id,String user){return "{}";}}
 static class ChildModelTask {String id;Runnable runnable;long at,generation=TaskLifecycle.generation();
  ChildModelTask(String id,String group,Runnable r,Long at){this.id=id;this.runnable=r;this.at=at;}
  void run(){try(TaskLifecycle.Work work=TaskLifecycle.enter(generation)){if(work!=null)runnable.run();}}}
 static class Farm {String ownerFarmId="farm";Animal ownerAnimal=new Animal();Field feedAnimal=new Field();Choice recallAnimalType=new Choice();
  int syncs,feeds;boolean homeNext,enabled=true;Map<String,ChildModelTask> tasks=new LinkedHashMap<>();
  boolean isEnable(){return enabled;}boolean hasChildTask(String id){return tasks.containsKey(id);}void removeChildTask(String id){tasks.remove(id);}
  boolean addChildTask(ChildModelTask task){tasks.put(task.id,task);return true;}
  void step(String label,Runnable action){action.run();}void syncAnimalStatusAtOtherFarm(String id){}
  void syncAnimalStatus(String farm){syncs++;if(homeNext)ownerAnimal.animalInteractStatus="HOME";}
  void feedAnimal(String farm){assert farm.equals(ownerFarmId);assert UserIdMap.uid.equals("self");assert ownerAnimal.animalInteractStatus.equals("HOME");feeds++;}
  void runRecallStep(){@@STEP@@}
  @@RECALL@@
  @@RETRY@@
 }
 static Farm reset(){AntFarmRpcCall.ok=false;AntFarmRpcCall.cancel=false;AntFarmRpcCall.calls=0;UserIdMap.uid="self";TimeUtil.slept=0;return new Farm();}
 public static void main(String[] args){
  Farm f=reset();f.ownerAnimal.subAnimalType="NEW_STATE";f.ownerAnimal.animalFeedStatus="NEW_FEED_STATE";
  f.runRecallStep();assert AntFarmRpcCall.calls==1:"Unknown enums must not prevent configured recall";
  f=reset();f.recallAnimal("chicken","other","farm","friend");assert f.tasks.size()==1;
  ChildModelTask retry=f.tasks.values().iterator().next();assert retry.at>=System.currentTimeMillis()+9000;
  f.homeNext=true;AntFarmRpcCall.ok=true;retry.run();assert AntFarmRpcCall.calls==2&&f.syncs>0&&f.feeds==1:"Delayed successful recall must refresh home state and feed";
  f=reset();AntFarmRpcCall.cancel=true;try{f.recallAnimal("chicken","other","farm","friend");throw new AssertionError("Recall swallowed cancellation");}catch(TaskCancelledException expected){}
  assert f.tasks.isEmpty():"Cancellation must not queue a retry";
  for(int mode=0;mode<5;mode++){
   f=reset();f.recallAnimal("chicken","other","farm","friend");retry=f.tasks.values().iterator().next();AntFarmRpcCall.calls=0;
   if(mode==0)UserIdMap.uid="other";if(mode==1)f.ownerFarmId="other";if(mode==2)f.ownerAnimal.animalId="other";if(mode==3)f.enabled=false;
   if(mode==4)f.ownerAnimal.animalInteractStatus="HOME";
   retry.run();assert AntFarmRpcCall.calls==0&&f.feeds==0:"Stale/disabled recall callback issued RPC";
  }
  f=reset();f.recallAnimal("chicken","other","farm","friend");retry=f.tasks.values().iterator().next();f.homeNext=true;f.feedAnimal.value=false;AntFarmRpcCall.ok=true;retry.run();assert f.syncs>0&&f.feeds==0;
  f=reset();f.recallAnimal("chicken","other","farm","friend");retry=f.tasks.values().iterator().next();AntFarmRpcCall.ok=true;retry.run();assert f.feeds==0:"Away state must never be fed";
  f=reset();f.recallAnimal("chicken","other","farm","friend");retry=f.tasks.values().iterator().next();AntFarmRpcCall.calls=0;
  TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);retry.run();assert AntFarmRpcCall.calls==0:"Recall crossed lifecycle generation";
  System.out.println("PASS recall unknown enums, delayed home/feed, cancellation and account/generation/config isolation");
 }
}
'''
recall = recall.replace("@@STEP@@", recall_step).replace("@@RECALL@@", method("model/task/antFarm/AntFarm.java", "private void recallAnimal("))
recall = recall.replace("@@RETRY@@", method("model/task/antFarm/AntFarm.java", "private void retryRecallAnimal("))
with tempfile.TemporaryDirectory(prefix="sesame-recall-") as tmp:
    java = Path(tmp) / "RecallCheck.java"
    java.write_text(recall, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java),
                    str(SOURCE / "data/task/TaskLifecycle.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "RecallCheck"], check=True)
