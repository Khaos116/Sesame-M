"""Replay actual trial-gold sign-in/pending-reward flow; never fund, convert, or activate."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/YebExpGold.java';rpc='model/task/antMember/AntMemberRpcCall.java';member='model/task/antMember/AntMember.java'
assert (SOURCE/service).exists(),'Missing trial-gold sign/reward flow'
code=r'''
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class YebExpGoldCheck {
 static class UserIdMap {static String uid="account-a";static String getCurrentUid(){return uid;}}
 static class YebTaskIdMap {static void load(String s){}static void clear(){}static void add(String k,String v){}static boolean save(String s){return true;}}
 static class RuntimeInfo {static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String k){return "";}boolean putVerified(String k,String v){return true;}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,6,12,0);return c;}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@HELPERS@@}
 static boolean writeFail=false,fail=false,change=true,duplicate=false,unknown=false,conflict=false;static int signs,claims;static String signStatus="UNSIGNED",taskId="id\"\\";static boolean pending=true;
 static class ApplicationHook {static String requestString(String name,String args){assert name.endsWith("queryMain")||name.endsWith(".query")||name.endsWith(".queryTaskByTaskId"):"write used retrying default bridge: "+name;return call(name,args,false);}static String requestString(String name,String args,int tries,int interval){assert tries==1&&interval==0:"write retried";assert name.endsWith(".signIn")||name.endsWith(".forward")||name.endsWith(".task.complete"):name;return call(name,args,true);}static String call(String name,String args,boolean mutation){JSONObject p=new JSONArray(args).optJSONObject(0),r=new JSONObject().put("success",!fail&&!(mutation&&writeFail));
  if(conflict)r.put("resultCode","FAIL");
  if(name.endsWith("queryMain")){assert !p.optJSONObject("task").optBoolean("queryComplete")||p.optJSONObject("task").optString("taskId").equals(taskId);JSONArray list=new JSONArray().put(new JSONObject().put("signInfo",new JSONObject().put("signDateDesc","TODAY").put("signStatus",signStatus)));if(duplicate)list.put(list.opt(0));JSONArray tasks=new JSONArray();if(pending)tasks.put(new JSONObject().put("taskId",taskId));JSONObject d=new JSONObject().put("signInData",new JSONObject().put("list",list)).put("taskData",new JSONObject().put("completeList",tasks));if(unknown)d.remove("taskData");r.put("resultData",d);}
  else if(name.endsWith(".signIn")){signs++;assert p.optString("signInPlayId").equals("PLAY102253251");if(change&&!fail&&!writeFail)signStatus="SIGNED";}
  else if(name.endsWith(".forward")){claims++;assert p.optString("path").equals("task.trigger");assert p.optJSONObject("params").optString("taskId").equals(taskId);assert p.optJSONObject("params").optString("appletId").equals("AP12183159");if(change&&!fail&&!writeFail)pending=false;}
  else throw new AssertionError("unexpected money/task action: "+name);
  return r.toString();}}
 static class AntMemberRpcCall {@@RPC@@}
 @@SERVICE@@
 static void reset(){writeFail=false;fail=duplicate=unknown=conflict=false;change=pending=true;signs=claims=Log.ok=0;signStatus="UNSIGNED";TimeUtil.cancel=false;Status.flags.clear();}
 public static void main(String[] args){
  reset();YebExpGold.run(true,true,Collections.emptySet());assert signs==1&&claims==1&&Log.ok==2;YebExpGold.run(true,true,Collections.emptySet());assert signs==1&&claims==1;
  reset();YebExpGold.run(false,true,Collections.emptySet());assert signs==0&&claims==1;
  reset();YebExpGold.run(true,false,Collections.emptySet());assert signs==1&&claims==0;
  reset();change=false;YebExpGold.run(true,true,Collections.emptySet());assert signs==1&&claims==1&&Log.ok==0;YebExpGold.run(true,true,Collections.emptySet());assert signs==1&&claims==1;
  reset();writeFail=true;YebExpGold.run(true,false,Collections.emptySet());assert signs==1&&claims==0&&Log.ok==0;YebExpGold.run(true,false,Collections.emptySet());assert signs==1;
  reset();writeFail=true;YebExpGold.run(false,true,Collections.emptySet());assert signs==0&&claims==1&&Log.ok==0;YebExpGold.run(false,true,Collections.emptySet());assert claims==1;
  reset();fail=true;YebExpGold.run(true,true,Collections.emptySet());assert signs==0&&claims==0;
  reset();conflict=true;YebExpGold.run(true,true,Collections.emptySet());assert signs==0&&claims==0;
  reset();duplicate=true;YebExpGold.run(true,false,Collections.emptySet());assert signs==0;
  reset();unknown=true;YebExpGold.run(false,true,Collections.emptySet());assert claims==0;
  reset();YebExpGold.run(false,true,Collections.singleton(taskId));assert claims==0;
  reset();signStatus="UNKNOWN";YebExpGold.run(true,false,Collections.emptySet());assert signs==0;
  reset();TimeUtil.cancel=true;try{YebExpGold.run(true,true,Collections.emptySet());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert signs==0&&claims==0;
  System.out.println("PASS opt-in sign/pending-only claim, TODAY/terminal readback, conflict/unknown/duplicate/day guard, blacklist, no money action, cancellation, RPC escaping and normal/failed writes use exactly one 1/0 call");
 }
}
'''
body=(SOURCE/service).read_text(encoding='utf-8');body=body[body.index('final class YebExpGold'):]
code=code.replace('@@SERVICE@@','static '+body).replace('@@HELPERS@@',method(member,'static Map<String, JSONObject> memberRowsById(')).replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('public static String queryYebExpGoldMain(','public static String signInYebExpGold(','public static String triggerYebExpGoldReward(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xms16m -Xmx192m')
from daily_task_fixture import with_daily_task
code = with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-trial-gold-') as tmp:
 f=Path(tmp)/'YebExpGoldCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True,env=env)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'YebExpGoldCheck'],check=True,env=env)
