"""Replay production bean sign/grade reward methods and RPC without network."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
member='model/task/antMember/AntMember.java';service='model/task/antMember/BeanRewards.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert (SOURCE/service).exists(),'Missing bean sign and guardian flow'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BeanSignCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@PAYLOAD@@}
 static class SesameAchievements {@@NUMBER@@}
 static class ApplicationHook {
  static int signs,rewards,balance=10;static boolean signed,awarded,change=true,unknown,duplicate,noGain,fail;
  static String requestString(String method,String args){return requestRaw(method,args,3,-1);}
  static String requestString(String method,String args,int tries,int pause){return requestRaw(method,args,tries,pause);}
  static String requestRaw(String method,String args,int tries,int pause){assert !(method.endsWith("signInTrigger")||method.endsWith("awardSend"))||tries==1&&pause==0:"mutation used default retries: "+method;JSONObject p=new JSONArray(args).optJSONObject(0),result=new JSONObject();
   if(method.endsWith("querySignInProcess"))result.put("canPush",!signed);else if(method.endsWith("signInTrigger")){signs++;if(change)signed=true;}
   else if(method.endsWith("queryGradeAwards")){JSONObject award=new JSONObject().put("skuId","sku\"\\").put("spuType","MARKETING_PRIZE").put("status",awarded?"MONTH_COUNT_LIMIT":"AVAILABLE").put("beanQuantity",5);JSONArray rows=new JSONArray().put(award);if(duplicate)rows.put(award);if(unknown)award.remove("beanQuantity");result.put("gradeSkuAwardsList",new JSONArray().put(new JSONObject().put("skuAwardList",rows)));}
   else if(method.endsWith("awardSend")){assert p.optString("skuId").equals("sku\"\\");rewards++;if(change){awarded=true;if(!noGain)balance+=5;}}
   else if(method.endsWith("queryAccountSummaryPoint"))result.put("effectPoint",balance);
   else throw new AssertionError(method);
   return new JSONObject().put("success",!fail).put("result",result).toString();
  }
 }
 static class AntMemberRpcCall {@@RPC@@}
 static class BeanRewards {@@WORKER@@}
 static void reset(){ApplicationHook.signs=ApplicationHook.rewards=Log.ok=0;ApplicationHook.balance=10;ApplicationHook.signed=ApplicationHook.awarded=ApplicationHook.unknown=ApplicationHook.duplicate=ApplicationHook.noGain=ApplicationHook.fail=TimeUtil.cancel=false;ApplicationHook.change=true;Status.flags.clear();}
 public static void main(String[] args){
  reset();BeanRewards.run();assert ApplicationHook.signs==1&&ApplicationHook.rewards==1&&Log.ok==2&&ApplicationHook.balance==15;BeanRewards.run();assert ApplicationHook.signs==1&&ApplicationHook.rewards==1;
  reset();ApplicationHook.change=false;BeanRewards.run();BeanRewards.run();assert ApplicationHook.signs==1&&ApplicationHook.rewards==1&&Log.ok==0;
  reset();ApplicationHook.noGain=true;BeanRewards.run();assert ApplicationHook.rewards==1&&Log.ok==1:"changed eligibility without balance gain is not proof of reward";
  reset();ApplicationHook.unknown=true;BeanRewards.run();assert ApplicationHook.rewards==0;
  reset();ApplicationHook.duplicate=true;BeanRewards.run();assert ApplicationHook.rewards==0;
  reset();ApplicationHook.fail=true;BeanRewards.run();assert ApplicationHook.signs==0&&ApplicationHook.rewards==0;
  reset();TimeUtil.cancel=true;try{BeanRewards.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.signs==0&&ApplicationHook.rewards==0;
  System.out.println("PASS bean sign/readback and grade eligibility+balance, unknown/duplicate/failure/daily protection, cancellation and escaped requests");
 }
}
'''
code=code.replace('@@PAYLOAD@@',method(member,'static JSONObject memberFeaturePayload(')).replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
code=code.replace('@@WORKER@@','static String TAG="check";\n'+'\n'.join(method(service,s) for s in ('private static JSONObject response(','private static Map<String, JSONObject> awards(','private static long balance(','private static void sign(','private static void guardian(','static void run()')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('querySignInProcess','signInTrigger','queryGuardianGradeAwards','guardianAwardSend','queryAccountSummaryPoint')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
from daily_task_fixture import with_daily_task
code = with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-bean-sign-') as tmp:
 f=Path(tmp)/'BeanSignCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'BeanSignCheck'],check=True)
