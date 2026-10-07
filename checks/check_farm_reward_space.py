"""Replay bounded stock-release/claim helpers plus the actual one-card acceleration loop."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
farm='model/task/antFarm/AntFarm.java';src=(SOURCE/farm).read_text(encoding='utf-8')
assert 'new BooleanModelField("useFullRewardTool"' in src,'Missing optional full-stock reward flow'
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class FarmRewardSpaceCheck {
 static String TAG="check";String ownerFarmId="owner",ownerUserId="self";int finalScore=100;double foodInTrough=20;
 static class Bool {boolean n=true;boolean getValue(){return n;}}Bool useNewEggTool=new Bool(),useAccelerateTool=new Bool(),useFenceTool=new Bool(),useBigEaterTool=new Bool(),useFullRewardTool=new Bool(),useDollTool=new Bool();
 static class ListField {Set<String> ids=new HashSet<>();Set<String> getValue(){return ids;}}ListField useAccelerateToolOptions=new ListField();
 enum ToolType {NEWEGGTOOL,ACCELERATETOOL,FENCETOOL,STEALTOOL,SHARETOOL,BIG_EATER_TOOL,DOLLTOOL,ORDINARY_ORNAMENT_TOOL,ADVANCE_ORNAMENT_TOOL,RARE_ORNAMENT_TOOL;String nickName(){return name();}}
 enum TaskStatus {FINISHED,RECEIVED}
 enum AnimalFeedStatus {EATING,HUNGRY}enum AnimalInteractStatus {HOME,GUEST}enum AnimalBuff {ACCELERATING,NONE}
 static class Animal {String masterFarmId="owner",animalFeedStatus="EATING",animalInteractStatus="HOME",animalBuff="NONE";double consumeSpeed=0.001;long startEatTime=System.currentTimeMillis();}
 Animal ownerAnimal=new Animal();Animal[] animals={ownerAnimal};void syncAnimalStatus(String id){}void useFenceTool(){useFarmTool(ownerFarmId,ToolType.FENCETOOL);}
 void supplementDolls(int maxUses){assert maxUses==1;uses++;if(change)stock--;}
 boolean extraEnabled;boolean extraRewardToolAllowed(ToolType t){return extraEnabled&&(t==ToolType.STEALTOOL||t==ToolType.SHARETOOL||t==ToolType.ORDINARY_ORNAMENT_TOOL||t==ToolType.ADVANCE_ORNAMENT_TOOL||t==ToolType.RARE_ORNAMENT_TOOL);}
 boolean useExtraRewardTool(ToolType t){uses++;if(change)stock--;return change;}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class ApplicationHook {static JSONObject request;static String requestString(String method,String args){request=new JSONArray(args).getJSONObject(0);return "{}";}}
 static class ProductionFarmRpc {static String VERSION="test";static String farmId2UserId(String id){return "uid";}@@RPC@@}
 static class MessageUtil {static boolean checkMemo(String tag,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return "FAIL".equals(j.optString("memo"));}}
 static class UserIdMap {static String getCurrentUid(){return "self";}}
 static class Status {static Map<String,Integer> counts=new HashMap<>();static int getIntFlagToday(String s){return counts.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){counts.put(s,n);}static void clearFlag(String s){flags.remove(s);}static Set<String> flags=new HashSet<>();static boolean limit;static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static boolean canUseAccelerateToolToday(){return !limit;}static void useAccelerateToolToday(){}}
 static class Log {static int ok;static void farm(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static int stock=5,capacity=5,uses,claims;static int syncReads;static String liveHome="HOME",liveFeed="EATING";static boolean bigActive,bigEffect=true,missingFlag,wrongFarm,drift,cancelAfter;static boolean change=true,claimed=false,unknown=false;static ToolType type=ToolType.NEWEGGTOOL;
 static class AntFarmRpcCall {
  static String syncAnimalStatus(String id){syncReads++;if(drift&&syncReads==2)bigActive=true;JSONObject animal=new JSONObject().put("masterFarmId","owner").put("animalStatusVO",new JSONObject().put("animalInteractStatus",liveHome).put("animalFeedStatus",liveFeed));JSONObject farm=new JSONObject().put("farmId",wrongFarm?"other":"owner").put("animals",new JSONArray().put(animal));if(!missingFlag)farm.put("useBigEaterTool",bigActive);return new JSONObject().put("memo","SUCCESS").put("subFarmVO",farm).toString();}
  static String listFarmTool(){JSONObject p=new JSONObject().put("toolType",type.name()).put("toolId","tool").put("toolCount",stock).put("toolHoldLimit",capacity);if(unknown)p.remove("toolCount");return new JSONObject().put("memo","SUCCESS").put("toolList",new JSONArray().put(p)).toString();}
  static String useFarmTool(String farm,String id,String t){uses++;if(change)stock--;if(t.equals("BIG_EATER_TOOL")&&bigEffect)bigActive=true;if(cancelAfter)TimeUtil.cancel=true;return "{\"memo\":\"SUCCESS\"}";}
  static String receiveToolTaskReward(String award,int count,String task){claims++;if(change){claimed=true;stock+=count;}return "{\"memo\":\"SUCCESS\"}";}
  static String listToolTaskDetails(){return new JSONObject().put("memo","SUCCESS").put("list",new JSONArray().put(new JSONObject().put("taskType","task").put("taskStatus",claimed?"RECEIVED":"FINISHED").put("bizInfo",new JSONObject().put("awardType",type.name()).put("awardCount",1).toString()))).toString();}
 }
 @@METHODS@@
 static void reset(FarmRewardSpaceCheck f){stock=capacity=5;uses=claims=Log.ok=0;change=true;claimed=unknown=false;Status.flags.clear();Status.counts.clear();Status.limit=false;syncReads=0;bigActive=missingFlag=wrongFarm=drift=cancelAfter=false;bigEffect=true;liveHome="HOME";liveFeed="EATING";TimeUtil.cancel=false;type=ToolType.NEWEGGTOOL;f.useNewEggTool.n=f.useAccelerateTool.n=f.useFenceTool.n=f.useBigEaterTool.n=f.useFullRewardTool.n=f.useDollTool.n=true;f.ownerAnimal.animalFeedStatus="EATING";f.ownerAnimal.animalInteractStatus="HOME";f.ownerAnimal.consumeSpeed=0.001;f.useAccelerateToolOptions.ids.clear();}
 public static void main(String[] args) throws Exception {FarmRewardSpaceCheck f=new FarmRewardSpaceCheck();
  ProductionFarmRpc.receiveToolTaskReward("type\"\\",1,"task\"\\");assert ApplicationHook.request.getString("rewardType").equals("type\"\\")&&ApplicationHook.request.getString("taskType").equals("task\"\\")&&!ApplicationHook.request.getBoolean("ignoreLimit");
  reset(f);f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1&&stock==5;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1;
  reset(f);f.useNewEggTool.n=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);f.receiveToolRewardWithSpace(type.name(),type,2,"task");assert uses==0&&claims==0:"one release cannot fit two rewards";
  reset(f);change=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==0;
  reset(f);unknown=true;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);stock=3;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==1;
  reset(f);claimed=true;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0:"task no longer pending must not waste a card";
  reset(f);type=ToolType.STEALTOOL;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  assert rewardToolType("DOLL_TOOL")==ToolType.DOLLTOOL&&rewardToolType("ORNAMENT_ORDINARY_TOOL")==ToolType.ORDINARY_ORNAMENT_TOOL&&rewardToolType("ORNAMENT_ADVANCE_TOOL")==ToolType.ADVANCE_ORNAMENT_TOOL&&rewardToolType("ORNAMENT_RARE_TOOL")==ToolType.RARE_ORNAMENT_TOOL&&rewardToolType("UNKNOWN")==null;
  reset(f);type=ToolType.DOLLTOOL;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1;
  reset(f);type=ToolType.DOLLTOOL;f.useDollTool.n=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);type=ToolType.DOLLTOOL;change=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==0;
  reset(f);type=ToolType.ADVANCE_ORNAMENT_TOOL;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  for(ToolType t:List.of(ToolType.STEALTOOL,ToolType.SHARETOOL,ToolType.ORDINARY_ORNAMENT_TOOL,ToolType.ADVANCE_ORNAMENT_TOOL,ToolType.RARE_ORNAMENT_TOOL)){
   reset(f);f.extraEnabled=true;type=t;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1&&stock==5;t=type;f.extraEnabled=false;
  }
  reset(f);type=ToolType.ACCELERATETOOL;f.useAccelerateToolOptions.ids.add("useAccelerateToolContinue");f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1:"release mode must use at most one even if continue enabled";
  reset(f);type=ToolType.ACCELERATETOOL;Status.limit=true;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);type=ToolType.ACCELERATETOOL;f.ownerAnimal.consumeSpeed=0;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==1&&stock==5&&Status.getIntFlagToday("farm::bigEaterRewardAttempts")==1;
  reset(f);type=ToolType.BIG_EATER_TOOL;f.useBigEaterTool.n=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==0&&claims==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;bigEffect=false;f.receiveToolRewardWithSpace(type.name(),type,1,"task");assert uses==1&&claims==0&&Status.hasFlagToday("farm::bigEaterRewardUnconfirmed");assert !f.useFarmTool("owner",type)&&uses==1;
  reset(f);type=ToolType.BIG_EATER_TOOL;bigActive=true;assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;missingFlag=true;assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;wrongFarm=true;assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;liveFeed="HUNGRY";assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;liveHome="GUEST";assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;drift=true;assert !f.useFarmTool("owner",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;assert f.useFarmTool("owner",type);bigActive=false;assert f.useFarmTool("owner",type);bigActive=false;assert !f.useFarmTool("owner",type)&&uses==2;
  reset(f);type=ToolType.BIG_EATER_TOOL;assert !f.useFarmTool("other",type)&&uses==0;
  reset(f);type=ToolType.BIG_EATER_TOOL;f.useFullRewardTool.n=false;assert f.useFarmTool("owner",type)&&syncReads==0:"old behavior preserved when off";
  reset(f);type=ToolType.BIG_EATER_TOOL;cancelAfter=true;try{f.useFarmTool("owner",type);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancel=false;assert !f.useFarmTool("owner",type)&&uses==1&&Status.hasFlagToday("farm::bigEaterRewardUnconfirmed");
  ProductionFarmRpc.syncAnimalStatus("farm\"\\");assert ApplicationHook.request.optString("farmId").equals("farm\"\\")&&!ApplicationHook.request.optBoolean("queryFoodStockInfo")&&ApplicationHook.request.optString("operType").equals("FEEDSYNC");
  reset(f);TimeUtil.cancel=true;try{f.receiveToolRewardWithSpace(type.name(),type,1,"task");throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert uses==claims&&uses==0;
  System.out.println("PASS stock room/readback/claim, allowed tools/config, one-slot only/no waste, unknown/daily attempt, acceleration continue/cap/zero-speed and cancellation");
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(farm,s) for s in ('private static ToolType rewardToolType(','private static long npcTaskNumber(','private static boolean farmFeatureOk(','private JSONObject rewardToolStock(','private boolean toolRewardReady(','private void receiveToolRewardWithSpace(','private void useAccelerateTool(boolean releaseOneSlot)','private Boolean useFarmTool(','private JSONObject queryBigEaterState(','private boolean useBigEaterRewardTool(')))
code=code.replace('@@RPC@@',method('model/task/antFarm/AntFarmRpcCall.java','public static String receiveToolTaskReward(')+method('model/task/antFarm/AntFarmRpcCall.java','public static String syncAnimalStatus('))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-reward-space-') as tmp:
 f=Path(tmp)/'FarmRewardSpaceCheck.java';f.write_text(code,encoding='utf-8')
 env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xms16m -Xmx192m')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True,env=env)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'FarmRewardSpaceCheck'],check=True,env=env)
