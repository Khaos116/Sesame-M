"""Actual remaining reward-tool policies; fake farm/ornament/stock RPCs only."""
from pathlib import Path
import os, re, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
farm="model/task/antFarm/AntFarm.java"
assert "private boolean useExtraRewardTool(" in (SOURCE/farm).read_text(encoding="utf-8"), "RED: remaining five reward-tool policies missing"
source=(SOURCE/farm).read_text(encoding="utf-8")
for field in ("rewardStealTool", "rewardShareTool"):
 assert re.search(r'new BooleanModelField\("'+field+r'", "[^"]+", false\)',source)
assert re.search(r'new IntegerModelField\("rewardExtraToolDailyLimit", "[^"]+", 0, 0, 5\)',source)
assert re.search(r'new SelectModelField\("rewardOrnamentTools", "[^"]+", new LinkedHashSet<>\(\)',source)

signatures=["private boolean extraRewardToolAllowed(","private boolean extraRewardToolOwner(","private static final class RewardFoodState", "private RewardFoodState extraRewardFoodState(","private Map<String, Boolean> extraRewardOrnaments(","private JSONObject extraRewardToolBudget(","private boolean useExtraRewardTool(","private JSONObject rewardToolStock(","private static long npcTaskNumber(","private static boolean farmFeatureOk(","private static double foodNumber(","private static String rankingDay(","private boolean toolRewardReady(","private void receiveToolRewardWithSpace("]
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ExtraRewardCheck {
 static final String TAG="check",EXTRA_REWARD_TOOL_KEY="farmExtraRewardTools";
 String ownerUserId="A",ownerFarmId="farmA";boolean enabled=true;
 static class System {static long now=java.time.OffsetDateTime.parse("2026-10-07T19:00:00+08:00").toInstant().toEpochMilli();static long currentTimeMillis(){return now;}}
 static class Bool {boolean n;boolean getValue(){return n;}}static class Int {int n;int getValue(){return n;}}static class Select {Set<String> n=new HashSet<>();Set<String> getValue(){return n;}}
 Bool useFullRewardTool=new Bool(),rewardStealTool=new Bool(),rewardShareTool=new Bool();Int rewardExtraToolDailyLimit=new Int();Select rewardOrnamentTools=new Select();
 enum ToolType {STEALTOOL,SHARETOOL,ORDINARY_ORNAMENT_TOOL,ADVANCE_ORNAMENT_TOOL,RARE_ORNAMENT_TOOL,ACCELERATETOOL,NEWEGGTOOL,FENCETOOL,BIG_EATER_TOOL,DOLLTOOL;String nickName(){return name();}}
 enum TaskStatus {FINISHED,RECEIVED}
 Bool useNewEggTool=new Bool(),useAccelerateTool=new Bool(),useFenceTool=new Bool(),useBigEaterTool=new Bool(),useDollTool=new Bool();
 static class Animal {String animalInteractStatus="HOME";}Animal ownerAnimal=new Animal();
 void useAccelerateTool(boolean one){throw new AssertionError("unselected old tool");}void useFenceTool(){throw new AssertionError("unselected old tool");}void supplementDolls(int one){throw new AssertionError("unselected old tool");}boolean useFarmTool(String farm,ToolType type){throw new AssertionError("unselected old tool");}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static void clearFlag(String s){flags.remove(s);}static boolean canUseAccelerateToolToday(){return false;}}
 enum AnimalInteractStatus {HOME,STEALING,GOTOSTEAL}enum AnimalFeedStatus {HUNGRY,EATING,SLEEPY}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 boolean manualFarmOwner(String uid){if(Thread.currentThread().isInterrupted())throw new TaskCancelledException();return enabled&&uid!=null&&uid.equals(UserIdMap.uid);}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return "FAIL".equals(j.optString("memo"));}}
 static class TimeUtil {static void sleep(long n){if(Thread.currentThread().isInterrupted())throw new TaskCancelledException();}}
 static class Log {static void record(String s){}static void farm(String s){}static void err(String t,String m,Throwable e){throw new AssertionError(e);}}
 static class RuntimeInfo {static Map<String,RuntimeInfo> accounts=new HashMap<>();static boolean fail;static int writes;Map<String,String> data=new HashMap<>();static RuntimeInfo getInstance(){return accounts.computeIfAbsent(UserIdMap.uid,k->new RuntimeInfo());}String getString(String key){return data.getOrDefault(key,"");}boolean putVerified(String key,String value){writes++;if(fail)return false;data.put(key,value);return true;}}
 static class AntFarmRpcCall {
  static int claims;static boolean claimed;
  static String listToolTaskDetails(){return new JSONObject().put("memo","SUCCESS").put("list",new JSONArray().put(new JSONObject().put("taskType","task").put("taskStatus",claimed?"RECEIVED":"FINISHED").put("bizInfo",new JSONObject().put("awardType",type.name()).put("awardCount",1).toString()))).toString();}
  static String receiveToolTaskReward(String award,int count,String task){assert count==1&&award.equals(type.name())&&task.equals("task");claims++;claimed=true;stock++;return "{\"memo\":\"SUCCESS\"}";}
  static int stock=5,cap=5,uses,reads;static ToolType type;static double food=10,trough=0;static String home="HOME",feed="HUNGRY",currentFarm="farmA",owner="A";
  static int stockReads;static String stockDrift="",toolId="tool\"quoted";
  static boolean acquired,allOwned,stockChange=true,effect=true,failed,unknown,switchOwner,cancel,failSaveAfter,duplicate,missingFood,badOwned,missingTool,wrongFarm;static String newKey="missing";
  static String enterFarm(String farm,String uid){assert farm.isEmpty()&&uid.equals("A");reads++;JSONObject sub=new JSONObject().put("farmId",wrongFarm?"other":"farmA").put("foodInTrough",trough).put("animals",new JSONArray().put(new JSONObject().put("masterFarmId","farmA").put("animalId","chicken").put("currentFarmId",currentFarm).put("animalStatusVO",new JSONObject().put("animalInteractStatus",home).put("animalFeedStatus",feed))));
   JSONObject f=new JSONObject().put("masterUserInfoVO",new JSONObject().put("userId",owner)).put("foodStock",food).put("subFarmVO",sub);if(missingFood)f.remove("foodStock");return new JSONObject().put("memo","SUCCESS").put("farmVO",f).toString();}
  static String listFarmTool(){stockReads++;if(stockReads==2){if(stockDrift.equals("count"))stock--;if(stockDrift.equals("limit"))cap++;if(stockDrift.equals("id"))toolId="changed";}JSONObject tool=new JSONObject().put("toolType",type.name()).put("toolId",toolId).put("toolCount",stock).put("toolHoldLimit",cap);if(missingTool)tool.remove("toolCount");return new JSONObject().put("memo","SUCCESS").put("toolList",new JSONArray().put(tool)).toString();}
  static String listOrnaments(){JSONObject target=new JSONObject().put("resourceKey",newKey).put("acquired",acquired||allOwned);if(badOwned)target.put("acquired","false");JSONArray rows=new JSONArray().put(new JSONObject().put("resourceKey","old").put("acquired",true)).put(target);if(duplicate)rows.put(target);return new JSONObject().put("memo","SUCCESS").put("achievementOrnaments",rows).toString();}
  static String useFarmTool(String farm,String id,String kind){assert farm.equals("farmA")&&id.equals("tool\"quoted")&&kind.equals(type.name());uses++;
   JSONObject state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY));assert state.optBoolean("pending")&&state.optInt("used")>0&&state.optJSONObject("attempts").optBoolean(kind):"durable reserve before send";
   if(stockChange)stock--;if(effect){if(type==ToolType.SHARETOOL)food+=180;else if(type==ToolType.STEALTOOL){home="STEALING";currentFarm="friendFarm";}else acquired=true;}
   if(switchOwner)UserIdMap.uid="B";if(cancel)throw new TaskCancelledException();if(failSaveAfter)RuntimeInfo.fail=true;
   return unknown?"malformed":new JSONObject().put("memo",failed?"FAIL":"SUCCESS").toString();}
 }
 static class ApplicationHook {static JSONObject request;static String requestString(String name,String args){assert name.equals("com.alipay.antfarm.useFarmTool");request=new JSONArray(args).getJSONObject(0);return "{}";}}
 static class ProductionRpc {static String VERSION="v";@@RPC@@}
 @@METHODS@@
 static void reset(ExtraRewardCheck f,ToolType type){System.now=java.time.OffsetDateTime.parse("2026-10-07T19:00:00+08:00").toInstant().toEpochMilli();UserIdMap.uid=f.ownerUserId="A";f.ownerFarmId="farmA";f.enabled=true;f.useFullRewardTool.n=f.rewardStealTool.n=f.rewardShareTool.n=true;f.rewardExtraToolDailyLimit.n=5;f.rewardOrnamentTools.n.clear();f.rewardOrnamentTools.n.addAll(List.of("ORDINARY_ORNAMENT_TOOL","ADVANCE_ORNAMENT_TOOL","RARE_ORNAMENT_TOOL"));RuntimeInfo.accounts.clear();RuntimeInfo.fail=false;RuntimeInfo.writes=0;AntFarmRpcCall.stockReads=0;AntFarmRpcCall.stockDrift="";AntFarmRpcCall.toolId="tool\"quoted";AntFarmRpcCall.type=type;AntFarmRpcCall.stock=AntFarmRpcCall.cap=5;AntFarmRpcCall.uses=AntFarmRpcCall.reads=0;AntFarmRpcCall.food=10;AntFarmRpcCall.trough=0;AntFarmRpcCall.home="HOME";AntFarmRpcCall.feed="HUNGRY";AntFarmRpcCall.currentFarm="farmA";AntFarmRpcCall.owner="A";AntFarmRpcCall.acquired=AntFarmRpcCall.allOwned=AntFarmRpcCall.failed=AntFarmRpcCall.unknown=AntFarmRpcCall.switchOwner=AntFarmRpcCall.cancel=AntFarmRpcCall.failSaveAfter=AntFarmRpcCall.duplicate=AntFarmRpcCall.missingFood=AntFarmRpcCall.badOwned=AntFarmRpcCall.missingTool=AntFarmRpcCall.wrongFarm=false;AntFarmRpcCall.stockChange=AntFarmRpcCall.effect=true;AntFarmRpcCall.newKey="missing";}
 public static void main(String[] args)throws Exception {ExtraRewardCheck f=new ExtraRewardCheck();List<ToolType> types=List.of(ToolType.STEALTOOL,ToolType.SHARETOOL,ToolType.ORDINARY_ORNAMENT_TOOL,ToolType.ADVANCE_ORNAMENT_TOOL,ToolType.RARE_ORNAMENT_TOOL);
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  for(ToolType type:types){reset(f,type);Status.flags.clear();AntFarmRpcCall.claims=0;AntFarmRpcCall.claimed=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert AntFarmRpcCall.uses==1&&AntFarmRpcCall.claims==1&&AntFarmRpcCall.stock==5:type;}
  for(ToolType type:types){reset(f,type);Status.flags.clear();AntFarmRpcCall.claims=0;AntFarmRpcCall.claimed=false;AntFarmRpcCall.effect=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert AntFarmRpcCall.uses==1&&AntFarmRpcCall.claims==0;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert AntFarmRpcCall.uses==1&&AntFarmRpcCall.claims==1:"later free slot claims earned reward without another tool";assert MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY)).optBoolean("pending");}
  for(ToolType type:types){reset(f,type);assert f.useExtraRewardTool(type):type;assert AntFarmRpcCall.uses==1&&AntFarmRpcCall.stock==4;JSONObject state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY));assert state.optInt("used")==1&&!state.optBoolean("pending");assert !f.useExtraRewardTool(type)&&AntFarmRpcCall.uses==1;}
  reset(f,ToolType.SHARETOOL);f.rewardExtraToolDailyLimit.n=0;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.SHARETOOL);f.rewardShareTool.n=false;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.STEALTOOL);f.rewardStealTool.n=false;assert !f.useExtraRewardTool(ToolType.STEALTOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.RARE_ORNAMENT_TOOL);f.rewardOrnamentTools.n.clear();assert !f.useExtraRewardTool(ToolType.RARE_ORNAMENT_TOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.SHARETOOL);f.useFullRewardTool.n=false;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.SHARETOOL);f.enabled=false;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  for(String problem:List.of("food","sleep","eating","away","owner","farm","missingFood","tool","owned","duplicate","badOwned")){
   ToolType type=List.of("owned","duplicate","badOwned").contains(problem)?ToolType.ORDINARY_ORNAMENT_TOOL:ToolType.SHARETOOL;reset(f,type);
   switch(problem){case "food":AntFarmRpcCall.food=180;break;case "sleep":AntFarmRpcCall.feed="SLEEPY";break;case "eating":AntFarmRpcCall.feed="EATING";break;case "away":AntFarmRpcCall.home="STEALING";break;case "owner":AntFarmRpcCall.owner="B";break;case "farm":AntFarmRpcCall.wrongFarm=true;break;case "missingFood":AntFarmRpcCall.missingFood=true;break;case "tool":AntFarmRpcCall.missingTool=true;break;case "owned":AntFarmRpcCall.allOwned=true;break;case "duplicate":AntFarmRpcCall.duplicate=true;break;case "badOwned":AntFarmRpcCall.badOwned=true;}
   assert !f.useExtraRewardTool(type)&&AntFarmRpcCall.uses==0:problem;
  }
  for(ToolType type:types)for(String problem:List.of("stock","effect","unknown","failed","save")){
   reset(f,type);switch(problem){case "stock":AntFarmRpcCall.stockChange=false;break;case "effect":AntFarmRpcCall.effect=false;break;case "unknown":AntFarmRpcCall.unknown=true;break;case "failed":AntFarmRpcCall.failed=true;break;case "save":AntFarmRpcCall.failSaveAfter=true;}
   assert !f.useExtraRewardTool(type)&&AntFarmRpcCall.uses==1:type+" "+problem;assert MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY)).optBoolean("pending");
   ExtraRewardCheck restarted=new ExtraRewardCheck();restarted.useFullRewardTool.n=restarted.rewardStealTool.n=restarted.rewardShareTool.n=true;restarted.rewardExtraToolDailyLimit.n=5;restarted.rewardOrnamentTools.n.addAll(f.rewardOrnamentTools.n);assert !restarted.useExtraRewardTool(type)&&AntFarmRpcCall.uses==1;
   System.now+=86400000L;AntFarmRpcCall.food=10;AntFarmRpcCall.home="HOME";AntFarmRpcCall.currentFarm="farmA";AntFarmRpcCall.acquired=false;assert !f.useExtraRewardTool(type)&&AntFarmRpcCall.uses==1:"unknown must stay frozen across day";
  }
  reset(f,ToolType.SHARETOOL);RuntimeInfo.fail=true;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  reset(f,ToolType.SHARETOOL);RuntimeInfo.getInstance().data.put(EXTRA_REWARD_TOOL_KEY,"bad");assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0;
  for(String badDay:List.of("bad","2026-10-8","2026-02-30","2026-10-08","0000-01-01")){
   reset(f,ToolType.SHARETOOL);JSONObject bad=new JSONObject().put("owner","A").put("day",badDay).put("used",1).put("pending",false).put("attempts",new JSONObject().put("SHARETOOL",true));RuntimeInfo.getInstance().data.put(EXTRA_REWARD_TOOL_KEY,bad.toString());assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0:badDay;
  }
  for(String issue:List.of("used","unknown","false","string","pending")){
   reset(f,ToolType.SHARETOOL);JSONObject attempts=new JSONObject().put("SHARETOOL",true);JSONObject bad=new JSONObject().put("owner","A").put("day","2026-10-07").put("used",1).put("pending",false).put("attempts",attempts);
   switch(issue){case "used":bad.put("used",0);break;case "unknown":attempts.remove("SHARETOOL");attempts.put("FENCETOOL",true);break;case "false":attempts.put("SHARETOOL",false);break;case "string":attempts.put("SHARETOOL","true");break;case "pending":attempts.remove("SHARETOOL");bad.put("used",0).put("pending",true);}
   RuntimeInfo.getInstance().data.put(EXTRA_REWARD_TOOL_KEY,bad.toString());assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0:issue;
  }
  for(String drift:List.of("id","count","limit")){reset(f,ToolType.SHARETOOL);AntFarmRpcCall.stockDrift=drift;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==0&&RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY).isEmpty():drift;}
  reset(f,ToolType.SHARETOOL);f.rewardExtraToolDailyLimit.n=1;assert f.useExtraRewardTool(ToolType.SHARETOOL);AntFarmRpcCall.type=ToolType.RARE_ORNAMENT_TOOL;assert !f.useExtraRewardTool(ToolType.RARE_ORNAMENT_TOOL)&&AntFarmRpcCall.uses==1;
  reset(f,ToolType.SHARETOOL);assert f.useExtraRewardTool(ToolType.SHARETOOL);System.now+=86400000L;AntFarmRpcCall.food=10;assert f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==2;assert MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY)).optInt("used")==1;
  reset(f,ToolType.SHARETOOL);AntFarmRpcCall.switchOwner=true;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==1&&RuntimeInfo.getInstance().getString(EXTRA_REWARD_TOOL_KEY).isEmpty();
  reset(f,ToolType.SHARETOOL);AntFarmRpcCall.cancel=true;try{f.useExtraRewardTool(ToolType.SHARETOOL);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert AntFarmRpcCall.uses==1;AntFarmRpcCall.cancel=false;assert !f.useExtraRewardTool(ToolType.SHARETOOL)&&AntFarmRpcCall.uses==1;
  reset(f,ToolType.SHARETOOL);Thread.currentThread().interrupt();try{f.useExtraRewardTool(ToolType.SHARETOOL);throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}assert AntFarmRpcCall.uses==0;
  ProductionRpc.useFarmTool("farm\"quoted","tool\"quoted","RARE_ORNAMENT_TOOL");assert ApplicationHook.request.optString("targetFarmId").equals("farm\"quoted")&&ApplicationHook.request.optString("toolId").equals("tool\"quoted");assert !ApplicationHook.request.has("achievementId")&&!ApplicationHook.request.has("ornamentId"):"no invented targets";
  java.lang.System.out.println("PASS remaining five reward tools: opt-in selected types, hungry/no-food eligibility, server-chosen new outfit, exact stock/effect/record readback, per-type/day and shared budgets, durable unknown/restart/day freeze, owner and cancellation");
 }
}
'''.replace("@@METHODS@@","\n".join(method(farm,s) for s in signatures)).replace("@@RPC@@",method("model/task/antFarm/AntFarmRpcCall.java","public static String useFarmTool("))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-extra-reward-") as directory:
 java=Path(directory)/"ExtraRewardCheck.java";java.write_text(code,encoding="utf-8")
 stub=Path(directory)/"MyUtils.java"
 stub.write_text('package io.github.aw1y2z.sesame.util; public class MyUtils { public static org.json.JSONObject newJSONObject(){return new org.json.JSONObject();}}',encoding="utf-8")
 env=dict(os.environ,JAVA_TOOL_OPTIONS="-Xms16m -Xmx192m")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",directory,str(java),str(stub),str(SOURCE/"util/TaskCancelledException.java")],check=True,env=env)
 subprocess.run(["java","-ea","-cp",directory+os.pathsep+str(jar),"ExtraRewardCheck"],check=True,env=env)
