"""Replay merged kitchen/NPC/recipe flows against production Java, with no network or device."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
import re

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

farm = "model/task/antFarm/AntFarm.java"
rpc = "model/task/antFarm/AntFarmRpcCall.java"
source = (SOURCE / farm).read_text(encoding="utf-8")
assert 'new ChoiceModelField("farmNpcType"' in source
assert 'new SelectModelField("hireNpcList"' not in source, "reuse M's saved NPC selection"
assert 'step("小鸡大厨"' in source and 'step("NPC小鸡任务"' in source
assert "awardTroughFullThisRun = false" in method(farm, "public void run(")
code = r'''
import java.util.*;
import java.util.function.*;
import org.json.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class UpstreamFarmCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Log {static List<String> lines=new ArrayList<>();static void i(String t,String s){lines.add(s);}static void record(String s){lines.add(s);}static void farm(String s){lines.add(s);}static void err(String t,String s,Throwable e){throw new AssertionError(s,e);}}
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class TimeUtil {static int calls,cancelAt;static void sleep(long n){if(Thread.currentThread().isInterrupted()||cancelAt>0&&++calls>=cancelAt)throw new TaskCancelledException();}}
 static class MessageUtil {
  static int blacklists;
  static boolean checkSuccess(String t,JSONObject o){return o.optBoolean("success");}
  static boolean checkMemo(String t,JSONObject o){return "SUCCESS".equals(o.optString("memo"));}
  static boolean isRetryable(JSONObject o){return "102".equals(o.optString("resultCode"));}
  static boolean isServerBusy(JSONObject o){return isRetryable(o);}
  static boolean isUnsupportedRpc(JSONObject o){return "400000040".equals(o.optString("resultCode"));}
  static void checkResultCodeAndMarkTaskBlackList(String f,String t,JSONObject o){blacklists++;}
 }
 static class RpcRequestGuard {static boolean isFailure(JSONObject o){return o.has("error")||Boolean.FALSE.equals(o.opt("success"));}static String errorMessage(JSONObject o){return o.optString("memo");}}
 static class TaskAlternative {static boolean isTransactionTask(String id){return id.toUpperCase(Locale.ROOT).contains("RECHARGE");}}
 static class TaskAttemptPolicy {
  enum Outcome {DONE,RETRY,UNSUPPORTED,UNABLE,SKIPPED}
  enum ProbeResult {TODO,FINISHED,RECEIVED,GONE,UNKNOWN;static ProbeResult of(String s){try{return valueOf(s);}catch(Exception e){return UNKNOWN;}}}
  interface StatusProbe {ProbeResult probe(String key);}
  interface Attempt {Outcome run();}
  static boolean isReceived(ProbeResult r){return r==ProbeResult.RECEIVED||r==ProbeResult.GONE;}
  static class Site {String listField,bizKey;StatusProbe probe;Site(String f,String label,String key,String scene,StatusProbe p){assert scene.equals("ANTFARM_AI_KITCHEN_TASK");listField=f;bizKey=key;probe=p;}}
  static int attempts;static ProbeResult lastProbe;static Site lastSite;
  static Outcome handle(String key,String title,Object award,Attempt attempt,Consumer<String> log,Site site){
   assert key.equals("aikitchen::"+site.bizKey);lastSite=site;
   if(TaskAlternative.isTransactionTask(site.bizKey))return Outcome.SKIPPED;
   attempts++;Outcome result=attempt.run();lastProbe=site.probe.probe(site.bizKey);return result;
  }
 }
 @@LIFECYCLE@@
 @@AWARD@@
 static class AntKitchenTaskListMap {
  static Map<String,String> data=new HashMap<>();static java.nio.file.Path file;
  static Map<String,String> getMap(){return data;}static String get(String key){return data.get(key);}
  static void add(String k,String v){data.put(k,v);}static boolean save(){return true;}
  static void clear(){data.clear();}
  static void load(){try{data.clear();for(String title:java.nio.file.Files.readAllLines(file))data.put(title,title);}catch(Exception e){throw new AssertionError(e);}}
 }
 static class IdAndName {String id,name;}
 @@KITCHENENTITY@@
 static void preloadKitchen(){@@KITCHENPRELOAD@@}
 static void destroyKitchen(){@@KITCHENDESTROY@@}
 static class Status {static Set<String> flags=new HashSet<>();static int used;static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static boolean canUseSpecialFoodToday(){return used<100;}static void useSpecialFoodToday(){used++;}}
 static class RuntimeInfo {static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String key){return "";}}
 static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
 static class ApplicationHook {
  static String uid="A",cancelRpc="",enterOverride,listOverride,cookOverride,finishOverride,awardOverride,foodOverride;
  static boolean advance=true,decrease=true,admission;
  static int enters,lists,cooks,finishes,awards,foods;
  static JSONObject state;static JSONArray tasks;static List<JSONObject> sent=new ArrayList<>();
  static String getUserId(){return uid;}
  static JSONObject task(String id){for(int i=0;i<tasks.length();i++){JSONObject t=tasks.optJSONObject(i);if(t!=null&&id.equals(t.optString("taskId")))return t;}return null;}
  static String requestString(String rpc,String data){
   if(admission)assert !TaskLifecycle.isIdle():"kitchen RPC escaped lifecycle admission";
   JSONObject args=new JSONArray(data).optJSONObject(0);sent.add(new JSONObject(args.toString()).put("rpc",rpc));
   if(rpc.equals(cancelRpc))throw new TaskCancelledException();
   if(rpc.endsWith("enterAiKitchen")){enters++;return enterOverride==null?state.toString():enterOverride;}
   if(rpc.endsWith("listFarmTask")){lists++;if(listOverride!=null)return listOverride;return new JSONObject().put("success",true).put("memo","SUCCESS").put("farmTaskList",tasks).toString();}
   if(rpc.endsWith("finishTaskantfarm")||rpc.endsWith("doFarmTask")){
    finishes++;String key=args.optString("taskType",args.optString("bizKey"));JSONObject target=task(key);
    if(advance&&target!=null)target.put("taskStatus","FINISHED");
    return finishOverride==null?"{success:true,memo:SUCCESS}":finishOverride;
   }
   if(rpc.endsWith("receiveTaskAwardantfarm")||rpc.endsWith("receiveFarmTaskAward")){
    awards++;JSONObject target=task(args.optString("taskType",args.optString("taskId")));
    if(advance&&target!=null)target.put("taskStatus","RECEIVED");
    return awardOverride==null?"{success:true,memo:SUCCESS}":awardOverride;
   }
   if(rpc.endsWith("cookAiKitchen")){
    cooks++;if(cookOverride!=null)return cookOverride;
    assert args.optString("seasonId").equals(state.optString("seasonId"));
    JSONArray ids=args.optJSONArray("ingredientIds");assert ids.length()==3;Set<String> unique=new HashSet<>();
    for(int i=0;i<ids.length();i++){String id=ids.optString(i);assert unique.add(id);JSONArray stock=state.optJSONArray("ingredientList");
     for(int j=0;j<stock.length();j++){JSONObject row=stock.optJSONObject(j);if(id.equals(row.optString("ingredientId"))){assert row.optInt("count")>0;if(decrease)row.put("count",row.optInt("count")-1);}}
    }
    if(decrease)state.put("feedStock",state.optInt("feedStock")-state.optInt("feedCost"));
    return new JSONObject(state.toString()).put("cookResultType","NORMAL").put("dishName","dish").toString();
   }
   if(rpc.endsWith("useFarmFood")){foods++;return foodOverride==null?"{memo:SUCCESS,foodEffect:{deltaProduce:0.5}}":foodOverride;}
   throw new AssertionError(rpc);
  }
 }
 static class AntFarmRpcCall {
  static final String VERSION="test";
  @@SEASONRPC@@
  @@KITCHENAWARDRPC@@
  @@NPCRPC@@
 }
 @@KITCHENRPC@@
 @@KITCHEN@@
 static class Food {
  static final String TAG="food",DYNAMIC_FOOD_PENDING="pending",DYNAMIC_FOOD_KEY="ledger",FLAG_UNUSABLE_CUISINE="antFarm::unusableCuisineV2::";
  String ownerUserId="A";Field<Boolean> useSpecialFood=new Field<>(true);
  boolean manualFarmOwner(String uid){return uid.equals(ApplicationHook.uid);}
  static String rankingDay(long now){return "today";}
  @@FOOD@@
  @@NUMBER@@
 }
 static class Npc {
  static final String TAG="npc";String ownerFarmId="farm";int type=1;Field<Integer> farmNpcType=new Field<>(1);
  static final String[] FARM_NPC_NAMES={"off","gold","farm"},FARM_NPC_IDS={"","goldId","farmId"};
  static class Animal {String subAnimalType="NPC",animalId="goldId";}
  Animal[] animals={new Animal()};Field<Set<String>> AntFarmDoFarmTaskList=new Field<>(new HashSet<>());
  JSONObject queryNpcFarm(){return new JSONObject().put("animals",new JSONArray().put(new JSONObject().put("animalId",FARM_NPC_IDS[type]).put("subAnimalType","NPC")));}
  @@FARMOK@@
  @@FINDNPC@@
  @@NPCQUERY@@
  @@NPCTASKS@@
 }
 static JSONObject task(String id,String status){return new JSONObject().put("taskId",id).put("bizKey",id).put("title",id).put("taskStatus",status).put("taskMode","VIEW").put("canReceiveAwardCount",1).put("awardType","NPC_ANIMAL_FOOD");}
 static JSONObject stock(int n){return new JSONObject().put("success",true).put("seasonId","season\"\\quoted").put("canCook",true).put("feedStock",100).put("feedCost",2)
  .put("ingredientList",new JSONArray().put(new JSONObject().put("ingredientId","a").put("count",n)).put(new JSONObject().put("ingredientId","b").put("count",n)).put(new JSONObject().put("ingredientId","c").put("count",n)));}
 static void reset(){
  ApplicationHook.state=stock(2);ApplicationHook.tasks=new JSONArray();ApplicationHook.sent.clear();ApplicationHook.enters=ApplicationHook.lists=ApplicationHook.cooks=ApplicationHook.finishes=ApplicationHook.awards=ApplicationHook.foods=0;
  ApplicationHook.uid="A";ApplicationHook.cancelRpc="";ApplicationHook.enterOverride=ApplicationHook.listOverride=ApplicationHook.cookOverride=ApplicationHook.finishOverride=ApplicationHook.awardOverride=ApplicationHook.foodOverride=null;
  ApplicationHook.advance=ApplicationHook.decrease=true;ApplicationHook.admission=false;TimeUtil.calls=TimeUtil.cancelAt=0;Status.flags.clear();Status.used=0;MessageUtil.blacklists=0;TaskAttemptPolicy.attempts=0;TaskAttemptPolicy.lastProbe=null;Log.lines.clear();
 }
 static void kitchen(boolean cook,boolean tasks,Set<String> denied){ApplicationHook.admission=true;try{AntKitchen.execute(cook,tasks,denied,true);}finally{ApplicationHook.admission=false;}assert TaskLifecycle.isIdle();}
 public static void main(String[] args)throws Exception {
  java.nio.file.Path candidateFile=java.nio.file.Files.createTempFile("kitchen-candidates-", ".txt");
  try {
   AntKitchenTaskListMap.file=candidateFile;
   preloadKitchen();assert AlipayAntKitchenTaskList.getList().isEmpty();
   java.nio.file.Files.writeString(candidateFile,"new kitchen task\n");
   preloadKitchen();assert AlipayAntKitchenTaskList.getList().size()==1:"preload must refresh previously empty kitchen candidates";
   assert AlipayAntKitchenTaskList.getList().get(0).id.equals("new kitchen task");
   destroyKitchen();assert AlipayAntKitchenTaskList.getList().isEmpty():"destroy must invalidate kitchen candidate cache";
  } finally {java.nio.file.Files.deleteIfExists(candidateFile);}
  reset();kitchen(true,false,Set.of());assert ApplicationHook.cooks==2:"stock must drive cook loop";
  reset();ApplicationHook.state=stock(200).put("feedStock",1000);kitchen(true,false,Set.of());assert ApplicationHook.cooks==100:"cook loop must be bounded";
  reset();ApplicationHook.decrease=false;kitchen(true,false,Set.of());assert ApplicationHook.cooks==2:"unchanged inventory must stop";
  for(String key:List.of("canCook","seasonId","feedCost","feedStock","ingredientList")){
   reset();ApplicationHook.state.remove(key);kitchen(true,false,Set.of());assert ApplicationHook.cooks==0:key;
  }
  for(Object bad:List.of(-1,1.5,"2",true,new JSONObject(),4294967297L)){
   reset();ApplicationHook.state.put("feedCost",bad);kitchen(true,false,Set.of());assert ApplicationHook.cooks==0:bad;
  }
  reset();ApplicationHook.state.optJSONArray("ingredientList").put(new JSONObject().put("ingredientId","a").put("count",2));kitchen(true,false,Set.of());assert ApplicationHook.cooks==0;
  reset();ApplicationHook.state.optJSONArray("ingredientList").put(JSONObject.NULL);kitchen(true,false,Set.of());assert ApplicationHook.cooks==0;
  reset();ApplicationHook.state.put("feedStock",1);kitchen(true,false,Set.of());assert ApplicationHook.cooks==0;
  for(String response:List.of("bad JSON","{}","{success:true}","{success:true,cookResultType:NORMAL}")){
   reset();ApplicationHook.cookOverride=response;kitchen(true,false,Set.of());assert ApplicationHook.cooks==1:response;
  }
  reset();ApplicationHook.enterOverride="invalid";kitchen(true,true,Set.of());assert ApplicationHook.cooks+ApplicationHook.finishes+ApplicationHook.awards==0;
  reset();ApplicationHook.state.put("success","true");kitchen(true,true,Set.of());assert ApplicationHook.cooks+ApplicationHook.finishes+ApplicationHook.awards==0;
  for(JSONObject conflict:List.of(new JSONObject().put("success",true).put("error","1009"),new JSONObject().put("success",false).put("isSuccess",true))){
   reset();ApplicationHook.state=conflict;kitchen(true,true,Set.of());assert ApplicationHook.cooks+ApplicationHook.finishes+ApplicationHook.awards==0:"failure accepted as kitchen success";
  }
  reset();ApplicationHook.listOverride="{success:true,farmTaskList:[{taskId:{},taskStatus:FINISHED,canReceiveAwardCount:1}]}";kitchen(false,true,Set.of());assert ApplicationHook.finishes+ApplicationHook.awards==0;
  for(Object mode:List.of(JSONObject.NULL,new JSONObject(),true,"GAME","SOCIAL","RECHARGE")){
   reset();JSONObject unknown=task("unknown","TODO").put("taskMode",mode);if(mode==JSONObject.NULL)unknown.remove("taskMode");ApplicationHook.tasks.put(unknown);kitchen(false,true,Set.of());assert ApplicationHook.finishes+ApplicationHook.awards==0:"unknown task mode authorized a write";
  }
  reset();ApplicationHook.tasks.put(task("browse","TODO")).put(task("RECHARGE_task","TODO")).put(task("game","TODO").put("taskMode","GAME"));kitchen(false,true,Set.of());
  assert ApplicationHook.finishes==1&&ApplicationHook.awards==1&&ApplicationHook.task("browse").optString("taskStatus").equals("RECEIVED");
  assert TaskAttemptPolicy.lastProbe==TaskAttemptPolicy.ProbeResult.FINISHED:"kitchen completion must be probed";
  assert TaskAttemptPolicy.lastSite.listField.equals("AntKitchenTaskList");
  reset();ApplicationHook.tasks.put(task("blocked","TODO")).put(task("alreadyDone","FINISHED"));kitchen(false,true,Set.of("blocked","alreadyDone"));assert ApplicationHook.finishes==0&&ApplicationHook.awards==1:"blacklist must not discard earned reward";
  reset();ApplicationHook.advance=false;ApplicationHook.tasks.put(task("browse","TODO"));kitchen(false,true,Set.of());assert TaskAttemptPolicy.lastProbe==TaskAttemptPolicy.ProbeResult.TODO&&ApplicationHook.awards==0:"accepted ACK is not completion";
  reset();ApplicationHook.tasks.put(task("done","FINISHED"));ApplicationHook.awardOverride="{success:false}";kitchen(false,false,Set.of());assert MessageUtil.blacklists==0:"confirmed reward cannot be blacklisted";
  reset();ApplicationHook.tasks.put(task("done","FINISHED"));ApplicationHook.advance=false;ApplicationHook.awardOverride="{success:false}";kitchen(false,false,Set.of());assert MessageUtil.blacklists==1;
  reset();TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();kitchen(true,true,Set.of());assert ApplicationHook.sent.isEmpty();TaskLifecycle.thaw(freeze);
  for(String rpc:List.of("com.alipay.antfarm.enterAiKitchen","com.alipay.antfarm.cookAiKitchen","com.alipay.antieptask.finishTaskantfarm","com.alipay.antieptask.receiveTaskAwardantfarm")){
   reset();ApplicationHook.cancelRpc=rpc;ApplicationHook.tasks.put(task("todo",rpc.endsWith("receiveTaskAwardantfarm")?"FINISHED":"TODO"));
   try{kitchen(true,true,Set.of());throw new AssertionError("cancel swallowed: "+rpc);}catch(TaskCancelledException expected){}assert TaskLifecycle.isIdle();
  }
  reset();Thread.currentThread().interrupt();try{kitchen(true,true,Set.of());throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}assert ApplicationHook.sent.isEmpty();
  reset();AntKitchenRpcCall.finishTask("id\"\\quoted","user\"quoted");assert ApplicationHook.sent.get(0).optString("taskType").equals("id\"\\quoted")&&ApplicationHook.sent.get(0).optString("userId").equals("user\"quoted");
  reset();Food food=new Food();JSONObject dish=new JSONObject().put("cookbookId","book\"quoted").put("cuisineId","dish").put("name","dish").put("count",3).put("cuisineType","AI_KITCHEN").put("resultLevel","BAD");
  ApplicationHook.foodOverride="{memo:'食谱未配置',resultCode:U05}";assert food.useFarmFood(dish);assert food.useFarmFood(dish)&&ApplicationHook.foods==1&&Status.used==0;
  Status.flags.clear();assert food.useFarmFood(dish)&&ApplicationHook.foods==2:"recipe must retry on new day";
  reset();assert food.useFarmFood(dish);assert ApplicationHook.foods==3&&Status.used==3;JSONObject sent=ApplicationHook.sent.get(0);assert sent.optString("cookbookId").equals("book\"quoted")&&sent.optString("cuisineType").equals("AI_KITCHEN")&&sent.optString("resultLevel").equals("BAD");
  for(String key:List.of("cookbookId","cuisineId","count","cuisineType","resultLevel")){
   reset();JSONObject bad=new JSONObject(dish.toString()).put(key,new JSONObject());assert food.useFarmFood(bad)&&ApplicationHook.foods==0:key;
  }
  reset();Npc npc=new Npc();ApplicationHook.tasks.put(task("view","TODO"));npc.runOrdinaryNpcTasks();assert ApplicationHook.finishes==1&&ApplicationHook.awards==1&&ApplicationHook.lists==3;
  assert ApplicationHook.sent.stream().filter(j->j.optString("rpc").endsWith("listFarmTask")).allMatch(j->j.optString("taskSceneCode").equals("ANTFARM_CAIFU_NPC_TASK"));
  reset();npc.type=2;ApplicationHook.tasks.put(task("view","FINISHED"));npc.runOrdinaryNpcTasks();assert ApplicationHook.awards==1&&ApplicationHook.sent.get(0).optString("taskSceneCode").equals("feiliaoji_202507");npc.type=1;
  reset();ApplicationHook.tasks.put(task("view","TODO"));ApplicationHook.advance=false;npc.runOrdinaryNpcTasks();assert ApplicationHook.finishes==1&&ApplicationHook.lists==2&&ApplicationHook.awards==0;
  reset();ApplicationHook.tasks.put(task("view","TODO")).put(task("view","TODO"));npc.runOrdinaryNpcTasks();assert ApplicationHook.finishes+ApplicationHook.awards==0;
  reset();ApplicationHook.tasks.put(task("RECHARGE_task","TODO")).put(task("game","TODO").put("taskMode","GAME"));npc.runOrdinaryNpcTasks();assert ApplicationHook.finishes==0;
  reset();ApplicationHook.tasks.put(task("done","FINISHED").put("awardType",new JSONObject()));npc.runOrdinaryNpcTasks();assert ApplicationHook.awards==0;
  reset();ApplicationHook.tasks.put(task("view","TODO").put("taskSceneCode","wrong"));npc.runOrdinaryNpcTasks();assert ApplicationHook.finishes+ApplicationHook.awards==0;
  reset();ApplicationHook.tasks.put(task("view","TODO"));ApplicationHook.cancelRpc="com.alipay.antfarm.doFarmTask";try{npc.runOrdinaryNpcTasks();throw new AssertionError("NPC cancellation swallowed");}catch(TaskCancelledException expected){}
  System.out.println("PASS Nov16 farm: production kitchen lifecycle, bounded inventory cooking, malformed state, task completion/reward probes, NPC scenes and protected settings, recipe/day skip and season payload");
 }
}
'''

def kitchen_calls(source):
    source = re.sub(r"/\*[\s\S]*?\*/|//[^\r\n]*", "", source)
    return "\n".join(re.findall(r"(?:AntKitchenTaskListMap|AlipayAntKitchenTaskList)\.(?:load|clear)\(\);", source))


def kitchen_lifecycle_statements(path, signature):
    return kitchen_calls(method(path, signature))


assert kitchen_calls("// AlipayAntKitchenTaskList.clear();\nAntKitchenTaskListMap.load();") == "AntKitchenTaskListMap.load();"
assert kitchen_calls("/* AlipayAntKitchenTaskList.clear(); */") == ""
assert kitchen_calls("") == ""

replacements = {
    "@@KITCHENENTITY@@": method("entity/AlipayAntKitchenTaskList.java", "public class AlipayAntKitchenTaskList").replace("public class", "static class", 1),
    "@@KITCHENPRELOAD@@": kitchen_lifecycle_statements("data/ConfigPreload.java", "public static void prepare("),
    "@@KITCHENDESTROY@@": kitchen_lifecycle_statements("model/normal/base/BaseModel.java", "public static void destroyData("),
    "@@LIFECYCLE@@": method("data/task/TaskLifecycle.java", "public final class TaskLifecycle").replace("public final class", "static final class", 1),
    "@@AWARD@@": method("data/task/TaskAward.java", "public final class TaskAward").replace("public final class", "static final class", 1),
    "@@KITCHEN@@": method("model/task/antFarm/AntKitchen.java", "public class AntKitchen").replace("public class", "static class", 1),
    "@@KITCHENRPC@@": method("model/task/antFarm/AntKitchenRpcCall.java", "public class AntKitchenRpcCall").replace("public class", "static class", 1),
    "@@SEASONRPC@@": method(rpc, "public static String useFarmFood(String cookbookId"),
    "@@KITCHENAWARDRPC@@": method(rpc, "public static String receiveTaskAwardantfarm("),
    "@@NPCRPC@@": "\n".join(method(rpc, s) for s in ("public static String listNpcFarmTasks(", "public static String receiveNpcFarmAward(", "public static String doNpcFarmTask(")),
    "@@FOOD@@": method(farm, "private Boolean useFarmFood(JSONObject"),
    "@@NUMBER@@": method(farm, "private static long npcTaskNumber("),
    "@@FARMOK@@": method(farm, "private static boolean farmFeatureOk("),
    "@@FINDNPC@@": method(farm, "private static JSONObject findFarmNpc("),
    "@@NPCQUERY@@": method(farm, "private JSONArray queryOrdinaryNpcTasks("),
    "@@NPCTASKS@@": method(farm, "private void runOrdinaryNpcTasks("),
}
for key, value in replacements.items():
    code = code.replace(key, value)
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-nov16-farm-") as temp:
    java = Path(temp) / "UpstreamFarmCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temp, str(java), str(SOURCE / "util/TaskCancelledException.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp + os.pathsep + str(jar), "UpstreamFarmCheck"], check=True)
