"""Replay farm manual action dispatch and fresh read-only initializer against fake RPC."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
farm="model/task/antFarm/AntFarm.java"
for signature in ["private void sendBackAnimal(", "private void recordFarmGame(",
                  "private void drawMachineGroupsInner(", "private void doFarmDrawTask(",
                  "private void receiveFarmDrawTaskAward(", "private Boolean drawMachine(String scene)",
                  "public boolean IPexchangeBenefit(", "private Boolean useFarmFood(JSONObject"]:
    assert "catch (TaskCancelledException e) { throw e;" in method(farm, signature), signature
signatures=["protected boolean supportsManualAction(","private boolean manualFarmOwner(","private JSONArray initManualFarm(","protected void runManualAction(","private static double foodNumber(","private static int rankingInt("]
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
class Base {protected boolean supportsManualAction(String s){return false;}protected void runManualAction(String s){}}
public class FarmManualCheck extends Base {
 static String TAG="check";boolean enabled=true,allowed=true;boolean isEnable(){return enabled;}Boolean check(){return allowed;}
 String ownerUserId="old",ownerFarmId="old";Animal ownerAnimal;Animal[] animals;int foodStock,foodStockLimit,foodInTrough;double harvestBenevolenceScore,benevolenceScore;
 static class Bool {boolean n;boolean getValue(){return n;}}static class Int {int n;int getValue(){return n;}}
 Bool recordFarmGame=new Bool(),drawMachine=new Bool(),useSpecialFood=new Bool(),dynamicSpecialFood=new Bool(),useNewEggTool=new Bool(),useFenceTool=new Bool(),useDollTool=new Bool(),useAccelerateTool=new Bool(),useBigEaterTool=new Bool();Int sendBackAnimalType=new Int(),useSpecialFoodCountLimit=new Int();
 static class SendBackAnimalType {static final int NONE=0,BACK=1;}
 enum GameType {starGame,jumpGame,flyGame,hitGame}enum ToolType {NEWEGGTOOL,BIG_EATER_TOOL}enum AnimalInteractStatus {HOME,STEALING}enum AnimalFeedStatus {EATING,HUNGRY,SLEEPY}
 static class Animal {String animalId,masterFarmId,currentFarmId,subAnimalType,animalBuff,animalFeedStatus,animalInteractStatus;long startEatTime;double consumeSpeed;}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkMemo(String t,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}}
 static class Log {static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 List<String> actions=new ArrayList<>();boolean switchAfterGame,cancelAction;
 void action(String s){actions.add(s);assert ownerFarmId.equals("farmA")&&ownerUserId.equals("A");if(cancelAction)throw new TaskCancelledException();}
 void sendBackAnimal(){action("sendBack");}void recordFarmGame(GameType t){action(t.name());if(switchAfterGame)UserIdMap.uid="B";}void drawMachineGroups(){action("chouchoule");}void useFarmFood(JSONArray items){assert items.length()==1;action("specialFood");}void useFarmTool(String farm,ToolType type){action(type.name());}void useFenceTool(){action("fence");}void supplementDolls(){action("doll");}void useAccelerateTool(){action("accelerate");}
 static class AntFarmRpcCall {static int reads;static String owner="A",feed="EATING",home="HOME";static boolean malformed,duplicate,switchOwner,cancel;
  static JSONObject animal(){return new JSONObject().put("animalId","own").put("masterFarmId","farmA").put("currentFarmId","farmA").put("subAnimalType","NORMAL").put("animalBuff","NONE").put("startEatTime",100).put("consumeSpeed",0.01).put("animalStatusVO",new JSONObject().put("animalFeedStatus",feed).put("animalInteractStatus",home));}
  static String enterFarm(String farmId,String uid){reads++;assert farmId.isEmpty()&&uid.equals("A");if(cancel)throw new TaskCancelledException();if(switchOwner)UserIdMap.uid="B";if(malformed)return "broken";
   JSONArray animals=new JSONArray().put(animal());if(duplicate)animals.put(animal());
   return new JSONObject().put("memo","SUCCESS").put("cuisineList",new JSONArray().put(new JSONObject().put("count",2))).put("farmVO",new JSONObject().put("masterUserInfoVO",new JSONObject().put("userId",owner)).put("foodStock",100).put("foodStockLimit",200).put("harvestBenevolenceScore",4)
    .put("subFarmVO",new JSONObject().put("farmId","farmA").put("foodInTrough",40).put("farmProduce",new JSONObject().put("benevolenceScore",0.5)).put("animals",animals))).toString();}
 }
 @@METHODS@@
 static void reset(FarmManualCheck f){f.enabled=f.allowed=true;f.actions.clear();UserIdMap.uid="A";AntFarmRpcCall.owner="A";AntFarmRpcCall.reads=0;AntFarmRpcCall.malformed=AntFarmRpcCall.duplicate=AntFarmRpcCall.switchOwner=AntFarmRpcCall.cancel=false;AntFarmRpcCall.feed="EATING";AntFarmRpcCall.home="HOME";f.recordFarmGame.n=f.drawMachine.n=f.useSpecialFood.n=f.dynamicSpecialFood.n=f.useNewEggTool.n=f.useFenceTool.n=f.useDollTool.n=f.useAccelerateTool.n=f.useBigEaterTool.n=false;f.sendBackAnimalType.n=f.useSpecialFoodCountLimit.n=0;f.switchAfterGame=f.cancelAction=false;}
 public static void main(String[] args)throws Exception {FarmManualCheck f=new FarmManualCheck();reset(f);
  for(String s:List.of("sendBack","game","chouchoule","specialFood","useTool"))assert f.supportsManualAction(s);assert !f.supportsManualAction("sleep")&&!f.supportsManualAction("steal");f.runManualAction("sleep");assert AntFarmRpcCall.reads==0;
  f.runManualAction("useTool");assert f.actions.isEmpty()&&AntFarmRpcCall.reads==1;assert f.ownerFarmId.equals("farmA")&&f.foodStock==100&&f.foodInTrough==40&&f.harvestBenevolenceScore==4;
  reset(f);f.runManualAction("sendBack");assert f.actions.isEmpty();f.sendBackAnimalType.n=1;f.runManualAction("sendBack");assert f.actions.equals(List.of("sendBack"));
  reset(f);f.recordFarmGame.n=true;f.runManualAction("game");assert f.actions.equals(List.of("starGame","jumpGame","flyGame","hitGame"));
  reset(f);f.recordFarmGame.n=true;f.switchAfterGame=true;f.runManualAction("game");assert f.actions.equals(List.of("starGame"));
  reset(f);f.drawMachine.n=true;f.runManualAction("chouchoule");assert f.actions.equals(List.of("chouchoule"));
  reset(f);f.useSpecialFood.n=true;f.runManualAction("specialFood");assert f.actions.isEmpty();f.useSpecialFoodCountLimit.n=2;f.runManualAction("specialFood");assert f.actions.equals(List.of("specialFood"));
  reset(f);f.useSpecialFood.n=f.dynamicSpecialFood.n=true;f.runManualAction("specialFood");assert f.actions.equals(List.of("specialFood"));
  reset(f);f.useSpecialFood.n=f.dynamicSpecialFood.n=true;AntFarmRpcCall.feed="SLEEPY";f.runManualAction("specialFood");assert f.actions.isEmpty();
  reset(f);f.useNewEggTool.n=f.useFenceTool.n=f.useDollTool.n=f.useAccelerateTool.n=f.useBigEaterTool.n=true;f.runManualAction("useTool");assert f.actions.equals(List.of("NEWEGGTOOL","fence","doll","accelerate","BIG_EATER_TOOL"));
  reset(f);f.useAccelerateTool.n=f.useBigEaterTool.n=true;AntFarmRpcCall.feed="HUNGRY";f.runManualAction("useTool");assert f.actions.isEmpty();
  for(String reason:List.of("owner","malformed","duplicate","switch","disabled","pause")){
   reset(f);f.recordFarmGame.n=true;switch(reason){case "owner":AntFarmRpcCall.owner="B";break;case "malformed":AntFarmRpcCall.malformed=true;break;case "duplicate":AntFarmRpcCall.duplicate=true;break;case "switch":AntFarmRpcCall.switchOwner=true;break;case "disabled":f.enabled=false;break;case "pause":f.allowed=false;}
   f.runManualAction("game");assert f.actions.isEmpty():reason;
  }
  reset(f);AntFarmRpcCall.cancel=true;try{f.runManualAction("game");throw new AssertionError("RPC cancellation swallowed");}catch(TaskCancelledException expected){}
  reset(f);f.recordFarmGame.n=true;f.cancelAction=true;try{f.runManualAction("game");throw new AssertionError("action cancellation swallowed");}catch(TaskCancelledException expected){}
  reset(f);Thread.currentThread().interrupt();try{f.runManualAction("game");throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}
  java.lang.System.out.println("PASS farm manual actions: allowlist, fresh own-account state, no unrelated automatic tasks, configured switches/bounds, pause/account changes and cancellation");
 }
}
'''.replace("@@METHODS@@","\n".join(method(farm,s) for s in signatures))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-farm-manual-") as directory:
 java=Path(directory)/"FarmManualCheck.java";java.write_text(code,encoding="utf-8")
 env=dict(os.environ,JAVA_TOOL_OPTIONS="-Xms16m -Xmx192m")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",directory,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True,env=env)
 subprocess.run(["java","-ea","-cp",directory+os.pathsep+str(jar),"FarmManualCheck"],check=True,env=env)
