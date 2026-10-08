"""Replay actual sesame medal workflow and RPC; reject unknown eligibility and false success."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/SesameAchievements.java'
member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert (SOURCE/service).exists(),'Missing sesame achievement flow'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class SesameAchievementsCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@HELPERS@@}
 static boolean change=true,fail=false,duplicate=false,unknown=false;static int claims,details;static long value=30,claimed;static String series="series\"\\";
 static JSONObject medal(int level,long at,long threshold){JSONObject m=new JSONObject().put("levelNo",level).put("claimedAt",at).put("threshold",threshold).put("currentValue",value).put("displayState","UPGRADEABLE");if(unknown)m.remove("claimedAt");return m;}
 static class ApplicationHook {static String requestString(String name,String args){return requestRaw(name,args,3,-1);}
  static String requestString(String name,String args,int tries,int pause){return requestRaw(name,args,tries,pause);}
  static String requestRaw(String name,String args,int tries,int pause){assert !(name.endsWith("claimAccomplishmentV2"))||tries==1&&pause==0:"mutation used default retries: "+name;JSONObject p=new JSONArray(args).optJSONObject(0),d=new JSONObject();
  if(name.endsWith("queryAccomplishmentHomeV2")) {
   if(!p.has("tabCode"))d.put("tabs",new JSONArray().put(new JSONObject().put("tabCode","tab")));
   else {JSONObject m=medal(1,1,10).put("nextThreshold",20).put("medalSeriesCode",series);JSONArray rows=new JSONArray().put(m);if(duplicate)rows.put(m);d.put("themeCategories",new JSONArray().put(new JSONObject().put("seriesList",rows)));}
  }else if(name.endsWith("enterAccomplishmentDetailV2")){details++;assert p.optString("medalSeriesCode").equals(series);d.put("levels",new JSONArray().put(medal(1,1,10)).put(medal(2,claimed,20)).put(medal(3,0,40)));}
  else if(name.endsWith("claimAccomplishmentV2")){claims++;assert p.optString("medalSeriesCode").equals(series);if(change&&!fail)claimed=100;}
  else throw new AssertionError(name);
  return new JSONObject().put("success",!fail).put("data",d).toString();}}
 static class AntMemberRpcCall {@@RPC@@}
 @@SERVICE@@
 static void reset(){change=true;fail=duplicate=unknown=false;TimeUtil.cancel=false;claims=details=Log.ok=0;claimed=0;value=30;Status.flags.clear();}
 public static void main(String[] args){
  reset();SesameAchievements.run();assert claims==1&&Log.ok==1&&claimed==100;SesameAchievements.run();assert claims==1;
  reset();value=15;SesameAchievements.run();assert claims==0:"UPGRADEABLE not necessarily eligible";
  reset();change=false;SesameAchievements.run();assert claims==1&&Log.ok==0;SesameAchievements.run();assert claims==1;
  reset();fail=true;SesameAchievements.run();assert claims==0&&Log.ok==0;
  reset();duplicate=true;SesameAchievements.run();assert claims==0;
  reset();unknown=true;SesameAchievements.run();assert claims==0;
  assert SesameAchievements.exactNonNegative("0.1")==-1&&SesameAchievements.exactNonNegative("9223372036854775808")==-1;
  reset();TimeUtil.cancel=true;try{SesameAchievements.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert claims==0;
  System.out.println("PASS initial/next-level medal eligibility, highest reachable level/readback, duplicate/unknown/failure protection, strict numbers, daily attempt/cancel and RPC escaping");
 }
}
'''
body=(SOURCE/service).read_text(encoding='utf-8');body=body[body.index('final class SesameAchievements'):]
code=code.replace('@@SERVICE@@','static '+body).replace('@@HELPERS@@','\n'.join(method(member,s) for s in ('static JSONObject memberFeaturePayload(','static Map<String, JSONObject> memberRowsById('))).replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('public static String queryAccomplishmentHome(','public static String queryAccomplishmentDetail(','public static String claimAccomplishment(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-medals-') as tmp:
 f=Path(tmp)/'SesameAchievementsCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'SesameAchievementsCheck'],check=True)
