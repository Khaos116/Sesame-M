"""Replay the single daily bean draw with current eligibility and balance readback."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/BeanRewards.java';member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert 'static void runDraw(' in (SOURCE/service).read_text(encoding='utf-8'),'Missing single daily bean draw'
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BeanDrawCheck {
 static String TAG="check";
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@PAYLOAD@@}
 static class SesameAchievements {@@NUMBER@@}
 static int draws,balance=10,consults;static boolean eligible=true,change=true,fail,switchCamp,unknown,won=true;static int cost=1;static Object prize="0.01";
 static class ApplicationHook {static String requestString(String name,String raw){return requestRaw(name,raw,3,-1);}
  static String requestString(String name,String raw,int tries,int pause){return requestRaw(name,raw,tries,pause);}
  static String requestRaw(String name,String raw,int tries,int pause){assert !(name.endsWith("triggerDrawPrize"))||tries==1&&pause==0:"mutation used default retries: "+name;JSONObject p=new JSONArray(raw).optJSONObject(0),result=new JSONObject();
  if(name.endsWith("campConsult")){assert p.optString("planId").equals("INSP29990111");consults++;result.put("consultResult",eligible).put("campId",switchCamp&&consults>1?"other":"camp\"\\");}
  else if(name.endsWith("queryAccountSummaryPoint")){result.put("effectPoint",balance);if(unknown)result.remove("effectPoint");}
  else if(name.endsWith("triggerDrawPrize")){draws++;assert p.optString("campId").equals("camp\"\\")&&p.optString("planId").equals("INSP29990111");if(change)balance-=cost;result.put("triggerResult",won).put("prizeAmount",prize);}
  else throw new AssertionError(name);
  return new JSONObject().put("success",!fail).put("result",result).toString();}}
 @@METHODS@@
 static class AntMemberRpcCall {@@RPC@@}
 static void reset(){draws=consults=Log.ok=0;balance=10;cost=1;eligible=change=won=true;prize="0.01";fail=switchCamp=unknown=TimeUtil.cancel=false;Status.flags.clear();}
 public static void main(String[] args){
  reset();runDraw();runDraw();assert draws==1&&balance==9&&Log.ok==1;
  reset();eligible=false;runDraw();assert draws==0;
  reset();balance=0;runDraw();assert draws==0;
  reset();unknown=true;runDraw();assert draws==0;
  reset();switchCamp=true;runDraw();assert draws==0:"camp changes before spend";
  reset();change=false;runDraw();runDraw();assert draws==1&&Log.ok==0;
  reset();cost=2;runDraw();runDraw();assert draws==1&&Log.ok==0:"unexpected debit must stop and hold attempt";
  reset();won=false;runDraw();assert draws==1&&Log.ok==1;
  for(Object bad:new Object[]{"bad","-0.01","1E100000000",true}){reset();prize=bad;runDraw();assert draws==1&&Log.ok==0;}
  reset();fail=true;runDraw();assert draws==0;
  reset();TimeUtil.cancel=true;try{runDraw();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert draws==0;
  System.out.println("PASS one daily draw/current camp and balance, no eligibility/zero/unknown/config drift, exact debit readback, repeat/failure/cancel and escaping");
 }
}
'''
code=code.replace('@@PAYLOAD@@',method(member,'static JSONObject memberFeaturePayload(')).replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
code=code.replace('@@METHODS@@','\n'.join(method(service,s) for s in ('private static JSONObject response(','private static long balance(','static void runDraw(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('beanCampConsult','beanTriggerDrawPrize','queryAccountSummaryPoint')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-bean-draw-') as tmp:
 f=Path(tmp)/'BeanDrawCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'BeanDrawCheck'],check=True)
