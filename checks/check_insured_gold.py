"""Compile the insured-gold worker and production RPC against an isolated replay."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
member='model/task/antMember/AntMember.java';service='model/task/antMember/InsuredGold.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert (SOURCE/service).exists(),'Missing insured-gold entry and worker'
member_src=(SOURCE/member).read_text(encoding='utf-8')
assert 'new BooleanModelField("collectInsuredGold", "\u8682\u8681\u4fdd | \u4fdd\u969c\u91d1\u9886\u53d6\u4e0e\u6d4f\u89c8\u4efb\u52a1", false)' in member_src
assert 'if (collectInsuredGold.getValue()) InsuredGold.run(' in member_src
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class InsuredGoldCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@SHARED@@}
 static class SesameAchievements {@@NUMBER@@}
 static class ApplicationHook {
  static int warm,claims,signup,send,consult;static boolean changed=true,duplicate,unknown,disabledAfter,failGain,cancelAfterGain;static String ball="flow\"\\",stage="NONE_SIGNUP",kind="BROWSE_PAGE";
  static JSONObject task(){return new JSONObject().put("taskId","task\"\\").put("taskMainType",kind).put("taskProcessStatus",stage).put("taskConfig",new JSONObject().put("appletId","applet\"\\"));}
  static String requestString(String name,String raw){return requestRaw(name,raw,3,-1);}
  static String requestString(String name,String raw,int tries,int pause){return requestRaw(name,raw,tries,pause);}
  static String requestRaw(String name,String raw,int tries,int pause){assert !(name.endsWith("gainMyAndFamilySumInsured")||name.endsWith("taskTriggerv2"))||tries==1&&pause==0:"mutation used default retries: "+name;JSONObject a=new JSONArray(raw).optJSONObject(0);JSONObject data=new JSONObject();
   if(name.endsWith("queryOpenAndAllowAndUpgrade")||name.endsWith("giftHomeRender")||name.endsWith("queryOpenAndAllow"))warm++;
   else if(name.endsWith("queryMultiSceneWaitToGainList")){assert a.optString("entrance").equals("cfsy");JSONArray list=new JSONArray();if(!ball.isEmpty())list.put(new JSONObject().put("sendFlowNo",ball).put("sendType",1).put("sendFlowStatus",1).put("disabled",disabledAfter&&claims>0));if(duplicate&&list.length()>0)list.put(list.optJSONObject(0));data.put("eventToWaitDTOList",list);if(unknown)data.remove("eventToWaitDTOList");}
   else if(name.endsWith("gainMyAndFamilySumInsured")){assert a.optString("sendFlowNo").equals(ball)&&!a.optBoolean("helpGain");claims++;if(changed&&!disabledAfter)ball="";if(cancelAfterGain)TimeUtil.cancel=true;if(failGain)return "{\"success\":false}";}
   else if(name.endsWith("queryTaskListv2"))data.put("taskDetailList",a.optString("taskCenterId").equals("AP16236844")?new JSONArray().put(task()):new JSONArray());
   else if(name.endsWith("taskTriggerv2")){assert a.optString("appletId").equals("applet\"\\")&&a.optString("taskCenId").equals("AP16236844");if(a.optString("stageCode").equals("signup")){signup++;if(changed)stage="SIGNUP_COMPLETE";}else{assert a.optString("stageCode").equals("send");send++;if(changed)stage="RECEIVED";}}
   else if(name.endsWith("taskCenterConsultById")){consult++;assert a.optString("taskId").equals("task\"\\");if(changed)stage="RECEIVED";data.put("taskDetailWithFilterDTO",task());}
   else throw new AssertionError(name);
   return new JSONObject().put("success",true).put("data",data).toString();
  }
 }
 static class AntMemberRpcCall {@@RPC@@}
 static class InsuredGold {@@WORKER@@}
 static void reset(){ApplicationHook.warm=ApplicationHook.claims=ApplicationHook.signup=ApplicationHook.send=ApplicationHook.consult=Log.ok=0;ApplicationHook.changed=true;ApplicationHook.duplicate=ApplicationHook.unknown=TimeUtil.cancel=false;ApplicationHook.ball="flow\"\\";ApplicationHook.stage="NONE_SIGNUP";ApplicationHook.kind="BROWSE_PAGE";Status.flags.clear();}
 public static void main(String[] args){
  reset();InsuredGold.run(Collections.emptySet());assert ApplicationHook.warm>=3&&ApplicationHook.claims==1&&ApplicationHook.signup==1&&ApplicationHook.send==1&&ApplicationHook.consult==1&&Log.ok>=2;InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==1&&ApplicationHook.signup==1&&ApplicationHook.send==1;
  reset();ApplicationHook.changed=false;InsuredGold.run(Collections.emptySet());InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==1&&Log.ok==0&&ApplicationHook.signup<=1:"unknown state must not repeat";
  reset();ApplicationHook.duplicate=true;InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==0;
  reset();ApplicationHook.unknown=true;InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==0;
  reset();ApplicationHook.failGain=true;InsuredGold.run(Collections.emptySet());assert Log.ok==0;ApplicationHook.failGain=false;InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==1;
  reset();ApplicationHook.changed=false;ApplicationHook.cancelAfterGain=true;try{InsuredGold.run(Collections.emptySet());throw new AssertionError("post-gain cancellation swallowed");}catch(TaskCancelledException expected){}ApplicationHook.cancelAfterGain=TimeUtil.cancel=false;InsuredGold.run(Collections.emptySet());assert ApplicationHook.claims==1:"attempt survives cancellation before readback";
  reset();ApplicationHook.disabledAfter=true;InsuredGold.run(Collections.emptySet());assert Log.ok==0:"disabled is not proof of receipt";ApplicationHook.disabledAfter=false;
  JSONObject sign=new JSONObject().put("sendFlowNo","sign").put("sendType",1);assert InsuredGold.available(new JSONObject().put("signInDTO",sign).put("eventToWaitDTOList",new JSONArray()))==null:"missing sign status is unknown, not a consumed sign";
  reset();ApplicationHook.ball="";ApplicationHook.kind="ISSUED_TASK";InsuredGold.run(Collections.emptySet());assert ApplicationHook.signup==0&&ApplicationHook.send==0:"manual/paid tasks must stay pending";
  reset();ApplicationHook.ball="";InsuredGold.run(Collections.singleton("task\"\\"));assert ApplicationHook.signup==0&&ApplicationHook.send==0;
  reset();ApplicationHook.ball="";ApplicationHook.stage="FINISHED";InsuredGold.run(Collections.emptySet());assert ApplicationHook.signup==0&&ApplicationHook.send==0&&ApplicationHook.consult==1;
  reset();TimeUtil.cancel=true;try{InsuredGold.run(Collections.emptySet());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.warm==0&&ApplicationHook.claims==0;
  System.out.println("PASS insured warmup/available claims/center signup-send-consult/readback, repeat/unknown/duplicate, manual and blacklist guards, cancellation and escaped RPC");
 }
}
'''
code=code.replace('@@SHARED@@',method(member,'static JSONObject memberFeaturePayload('))
code=code.replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
src=(SOURCE/service).read_text(encoding='utf-8');code=code.replace('@@WORKER@@',src[src.index('    private static'):src.rfind('}')])
names=('insuredGoldRights','insuredGoldWaitParams','queryAvailableCollectInsuredGold','collectInsuredGold','queryInsuredOpenAndAllowAndUpgrade','queryInsuredOpenAndAllow','queryInsuredGiftHomeRender','queryInsuredTaskList','triggerInsuredTask','consultInsuredTask')
parts=[]
for name in names:
 signature=('private static JSONArray ' if name=='insuredGoldRights' else 'private static JSONObject ' if name=='insuredGoldWaitParams' else 'public static String ')+name+'('
 parts.append(method(rpc,signature))
code=code.replace('@@RPC@@','\n'.join(parts))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-insured-') as tmp:
 f=Path(tmp)/'InsuredGoldCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'InsuredGoldCheck'],check=True)
