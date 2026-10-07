"""Replay actual find-energy session closure and reward-only follow-up; no real RPC."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java';rpc='model/task/antForest/AntForestRpcCall.java'
assert 'public static String takeLookEnd(' in (SOURCE/rpc).read_text(encoding='utf-8'), 'Missing find-energy closure'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestFindEndCheck {
 static String TAG="check";String selfId="self";boolean hasErrorWait;Set<String> dontCollectMap=new HashSet<>();
 static class Bool {boolean n=true;boolean getValue(){return n;}}Bool receiveForestTaskAward=new Bool();
 Bool collectEnergy=new Bool(),energyRain=new Bool();boolean quiet;boolean isRevivedSelfQuietTime(){return quiet;}
 boolean allowCollectByWhiteList(String id){return !dontCollectMap.contains(id);}JSONObject queryFriendHome(String id){return new JSONObject().put("id",id);}void collectUserEnergy(String id,JSONObject home,String mode){browsed++;}boolean hasActiveProp(JSONObject home,String type){return false;}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"))||j.has("resultCode")&&!Set.of("SUCCESS","100").contains(j.optString("resultCode"));}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class Status {static Set<String> flags=new HashSet<>();static void flagToday(String s){flags.add(s);}static boolean hasFlagToday(String s){return flags.contains(s);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Log {static int ok,errors;static void record(String s){}static void forest(String s){ok++;}static void printStackTrace(String tag,Throwable t){errors++;}}
 static class Statistics {enum DataType {COLLECTED}static long count;static void addData(DataType t,int n){count+=n;}}
 static int look,end,queries,claims,browsed,pre;static boolean changed=true,duplicate,unknown,throwLook,cancelLook,show=true,preFail,cancelPre,nested;static Object exposed="friend";static String state="FINISHED",sent="";
 static Object rainTitle="";static String endSource="";
 static JSONObject task(String status){return new JSONObject().put("taskBaseInfo",new JSONObject().put("sceneCode","scene\"\\").put("taskType","task\"\\").put("taskStatus",status));}
 static class ApplicationHook {
  static String requestString(String name,String args){JSONObject a=new JSONArray(args).optJSONObject(0);JSONObject p=new JSONObject().put("success",true);
   if(name.endsWith("queryCombineBiz")){pre++;if(cancelPre)throw new TaskCancelledException();assert a.optString("version").equals("20260616");assert a.optJSONObject("extInfo").optInt("takeLookExposedTimes",-1)==0;JSONObject h=new JSONObject().put("combineHandlerVOMap",new JSONObject().put("takeLookExpose",new JSONObject().put("friendUserId",exposed)));if(preFail)return p.put("success",false).toString();return nested?p.put("resData",h).toString():h.put("success",true).toString();}
   if(name.endsWith("takeLook")){look++;sent=a.optString("exposedUserId");if(cancelLook)throw new TaskCancelledException();if(throwLook)throw new IllegalStateException();return p.put("takeLookEnd",true).put("friendId","friend").put("actionType","FRIEND").toString();}
   if(name.endsWith("takeLookEnd")){end++;endSource=a.optString("source");assert a.optString("contactsStatus").equals("N");return p.put("showTaskList",show).put("extInfoInTakeLookEnd",new JSONObject().put("energyPlay",new JSONObject().put("title",rainTitle))).toString();}
   if(name.endsWith("queryTaskList")){queries++;assert a.optString("fromAct").equals("take_look_end_task_list");JSONArray rows=new JSONArray().put(task(state)).put(task("TODO").put("taskBaseInfo",new JSONObject().put("sceneCode","scene").put("taskType","todo").put("taskStatus","TODO")));if(duplicate)rows.put(task(state));if(unknown)p.put("success",false);return p.put("forestTasksNew",new JSONArray().put(new JSONObject().put("taskInfoList",rows))).toString();}
   if(name.endsWith("receiveTaskAward")){claims++;assert a.optString("sceneCode").equals("scene\"\\")&&a.optString("taskType").equals("task\"\\");if(changed)state="RECEIVED";return p.put("incAwardCount",5).put("returnData","Energy").toString();}
   throw new AssertionError(name);
  }
 }
 static class AntForestRpcCall {static String VERSION="test",TAKE_LOOK_VERSION="look",TASK_LIST_VERSION="tasks",TASK_LIST_EXT_VERSION="ext";@@RPC@@}
 @@METHODS@@
 static void reset(ForestFindEndCheck f){look=end=queries=claims=browsed=pre=Log.ok=Log.errors=0;changed=show=true;duplicate=unknown=throwLook=cancelLook=TimeUtil.cancel=preFail=cancelPre=nested=false;exposed="friend";sent="";state="FINISHED";Status.flags.clear();f.dontCollectMap.clear();f.receiveForestTaskAward.n=true;f.hasErrorWait=false;}
 public static void main(String[] args){ForestFindEndCheck f=new ForestFindEndCheck();
  reset(f);f.findAndCollectEnergy();assert look==1&&end==1&&browsed==1&&claims==1&&Log.ok==1&&Statistics.count==5;f.findAndCollectEnergy();assert end==2&&claims==1;
  reset(f);f.receiveForestTaskAward.n=false;f.findAndCollectEnergy();assert end==1&&queries==0&&claims==0;
  reset(f);show=false;f.findAndCollectEnergy();assert end==1&&queries==0;
  reset(f);changed=false;f.findAndCollectEnergy();f.findAndCollectEnergy();assert claims==1&&Log.ok==0:"unknown readback must not repeat or report success";
  reset(f);duplicate=true;f.findAndCollectEnergy();assert claims==0;
  reset(f);unknown=true;f.findAndCollectEnergy();assert claims==0;
  reset(f);throwLook=true;f.findAndCollectEnergy();assert end==1:"ordinary failure still closes the started session";
  reset(f);cancelLook=true;try{f.findAndCollectEnergy();throw new AssertionError("swallowed cancellation");}catch(TaskCancelledException expected){}assert end==0&&claims==0;
  reset(f);TimeUtil.cancel=true;try{f.findAndCollectEnergy();throw new AssertionError("pre-request cancel");}catch(TaskCancelledException expected){}assert look==0&&end==0;
  reset(f);f.findAndCollectEnergy();assert pre==1&&sent.equals("friend");
  reset(f);nested=true;f.findAndCollectEnergy();assert sent.equals("friend");
  for(Object bad:new Object[]{"self","",123," padded ","x\ny"}){reset(f);exposed=bad;f.findAndCollectEnergy();assert sent.isEmpty();}
  reset(f);f.dontCollectMap.add("friend");f.findAndCollectEnergy();assert sent.isEmpty()&&browsed==0;
  reset(f);preFail=true;f.findAndCollectEnergy();assert sent.isEmpty()&&browsed==1:"failed pre-exposure preserves normal recommendation";
  reset(f);cancelPre=true;try{f.findAndCollectEnergy();throw new AssertionError("pre-exposure cancellation");}catch(TaskCancelledException expected){}assert look==0&&end==0;
  reset(f);
  assert new JSONObject(AntForestRpcCall.queryTakeLookCombineBiz(new JSONObject().put("q\"\\", "baohuzhao"))).length()>0;
  AntForestRpcCall.takeLook(new JSONObject(),false,"must-not-repeat");assert sent.isEmpty();
  JSONObject nested=MyUtils.newJSONObject().put("taskInfoList",new JSONArray().put(task("TODO").put("childTaskTypeList",new JSONArray().put(task("FINISHED").put("taskBaseInfo",new JSONObject().put("sceneCode","s").put("taskType","child").put("taskStatus","FINISHED"))))));assert takeLookRewardTasks(nested).size()==2;
  System.out.println("PASS find session closure, reward-only gate/identity/readback/daily guard, grouped/child tasks, duplicate/failure/cancel and RPC escaping");
  reset(f);rainTitle="";f.finishEnergyRainFlow();assert end==1&&look==0&&claims==1&&endSource.equals("backFromEnergyRain");
  reset(f);rainTitle="\u8fd8\u80fd\u6536\u53d6";f.finishEnergyRainFlow();assert end==2&&look==1&&claims==1;
  reset(f);f.collectEnergy.n=false;f.finishEnergyRainFlow();assert look==0&&claims==1;f.collectEnergy.n=true;
  reset(f);f.quiet=true;f.finishEnergyRainFlow();assert look==0;f.quiet=false;
  reset(f);rainTitle=123;f.finishEnergyRainFlow();assert look==0;
  reset(f);f.energyRain.n=false;f.finishEnergyRainFlow();assert end==0;f.energyRain.n=true;
  reset(f);TimeUtil.cancel=true;try{f.finishEnergyRainFlow();throw new AssertionError("rain cancellation");}catch(TaskCancelledException expected){}assert end==0;
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in ('private void findAndCollectEnergy(','private String findEnergyExposedFriend(','static JSONObject forestSignPayload(','static long forestFeatureLong(','private static Map<String, JSONObject> takeLookRewardTasks(','private void finishFindEnergy(','private void receiveFindEnergyRewards(','private void finishEnergyRainFlow(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('takeLook','takeLookEnd','queryTakeLookEndTaskList','receiveTaskAward','queryTakeLookCombineBiz'))+'\n'+method(rpc,'public static String takeLook(JSONObject skipUsers, boolean takeLookStart, String exposedUserId)')+'\n'+method(rpc,'public static String takeLookEnd(String source)')+'\n'+method(rpc,'public static String queryTakeLookEndTaskList(String source)'))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-find-end-') as tmp:
 f=Path(tmp)/'ForestFindEndCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestFindEndCheck'],check=True)
