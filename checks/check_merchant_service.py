"""Compile merchant workflow/RPC with isolated replay; no real account or network."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
member="model/task/antMember/AntMember.java"
service="model/task/antMember/MerchantService.java"
rpc="model/task/antMember/AntMemberRpcCall.java"
assert (SOURCE/service).exists(),"Merchant workflow is not ported"
code=r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class MerchantCheck {
 static final String TAG="check";
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,6,hour,0);return c;}}
 static int hour=7;
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}}
 static class Log {static int ok;static void record(String s){}static void other(String s){ok++;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class AntMemberTaskListMap {static void add(String k,String v){}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@PAYLOAD@@}
 static class ApplicationHook {
  static List<JSONObject> calls=new ArrayList<>();static boolean opened=true,changed=true,fail=false,badOrder=false;static String order="";static int queries;
  static boolean signed=false,zcj=false,kmdk=false,enrolled=false,ball=false;static String taskStatus="UNRECEIVED",taskCode="BROWSE";static int current=0;
  static String requestString(String name,String args){JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",name);calls.add(body);JSONObject root=new JSONObject().put("success",!fail),data=new JSONObject();
   if(name.endsWith("transcode.check")){order="";data.put("isOpened",opened);}
   else if(name.endsWith("homepage.v5"))data.put("signIn",!signed);
   else if(name.endsWith("homepage.signin.v1")){if(changed&&!fail)signed=true;data.put("signInResult","SUCCESS");}
   else if(name.endsWith("zcj.view.invoke")){if(body.optString("compId").equals("ZCJ_SIGN_IN_EXECUTE")&&changed&&!fail)zcj=true;data.put("button",new JSONObject().put("status",zcj?"RECEIVED":"UNRECEIVED"));}
   else if(name.endsWith("query.activity")){root.put("activityNo","A_B_20261006").put("signInStatus",kmdk?"SIGN_IN_DISABLE":"SIGN_IN_ENABLE").put("signUpStatus",enrolled?"SIGN_UP":"UN_SIGN_UP");return root.toString();}
   else if(name.endsWith("kmdk.signIn")){if(changed&&!fail)kmdk=true;}
   else if(name.endsWith("kmdk.signUp")){if(changed&&!fail)enrolled=true;}
   else if(name.endsWith("ball.query.v1"))data.put("pointBalls",ball?new JSONArray():new JSONArray().put(new JSONObject().put("id","ball").put("name","Ball")));
   else if(name.endsWith("ball.receive")){if(changed&&!fail){ball=true;taskStatus="RECEIVED";}}
   else if(name.endsWith("task.more.query")||name.endsWith("task.service.query")){
    boolean more=name.endsWith("task.more.query");assert body.optJSONObject("paramMap").optString("orderTaskCode").equals(more?order:""):"MORE query must forward the last server token; SERVICE stays separate";
    if(more){order="next\"\\"+(++queries);data.put("orderTaskCode",badOrder?new JSONObject():order);}
    data.put("planCode",more?"MORE":"SERVICE").put("taskList",more?new JSONArray().put(new JSONObject().put("taskCode",taskCode).put("title","Browse").put("status",taskStatus).put("actionCode","BROWSE_VIEWED").put("current",current).put("target",1).put("pointBallId",taskStatus.equals("NEED_RECEIVE")?"ball":"")):new JSONArray());
   }else if(name.endsWith("task.receive")){if(changed&&!fail)taskStatus="PROCESSING";}
   else if(name.endsWith("query.by.actioncode")){}
   else if(name.endsWith("business.exam.page"))data.put("available",true).put("actionCode","EXAM_ACTION");
  else if(name.endsWith("action.produce")){if(changed&&!fail){current++;taskStatus="NEED_RECEIVE";}}
   else if(name.endsWith("task.finish")){if(changed&&!fail)taskStatus="RECEIVED";}
   else throw new AssertionError(name);
   return root.put("data",data).toString();
  }
 }
 static class AntMemberRpcCall {@@RPC@@}
 @@METHODS@@
 static void reset(){ApplicationHook.calls.clear();ApplicationHook.opened=true;ApplicationHook.changed=true;ApplicationHook.fail=false;ApplicationHook.signed=false;ApplicationHook.zcj=false;ApplicationHook.kmdk=false;ApplicationHook.enrolled=false;ApplicationHook.ball=false;ApplicationHook.taskStatus="UNRECEIVED";ApplicationHook.taskCode="BROWSE";ApplicationHook.current=0;Status.flags.clear();Log.ok=0;TimeUtil.cancel=false;hour=7;}
 static long count(String suffix){return ApplicationHook.calls.stream().filter(j->j.optString("rpc").endsWith(suffix)).count();}
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));reset();run(false,false,false,Collections.emptySet());assert ApplicationHook.calls.isEmpty();
  reset();ApplicationHook.opened=false;run(true,true,true,Collections.emptySet());assert ApplicationHook.calls.size()==1;
  reset();ApplicationHook.fail=true;run(true,true,true,Collections.emptySet());assert ApplicationHook.calls.size()==1;
  reset();run(true,true,false,Collections.emptySet());assert ApplicationHook.signed&&ApplicationHook.zcj&&ApplicationHook.kmdk&&ApplicationHook.enrolled&&ApplicationHook.ball;
  assert count("homepage.signin.v1")==1&&count("kmdk.signIn")==1&&count("kmdk.signUp")==1;
  run(true,true,false,Collections.emptySet());assert count("homepage.signin.v1")==1&&count("kmdk.signIn")==1;
  reset();hour=12;run(false,true,false,Collections.emptySet());assert count("kmdk.signIn")==0&&count("kmdk.signUp")==1;
  reset();ApplicationHook.changed=false;run(true,true,false,Collections.emptySet());run(true,true,false,Collections.emptySet());assert count("homepage.signin.v1")==1&&count("kmdk.signIn")==1&&count("kmdk.signUp")==1&&Log.ok==0;
  reset();run(false,false,true,Collections.emptySet());assert ApplicationHook.taskStatus.equals("RECEIVED")&&count("task.receive")==1&&count("action.produce")==1;
  reset();ApplicationHook.taskCode="JYMWDDJF_TASK";run(false,false,true,Collections.emptySet());assert count("business.exam.page")==1&&ApplicationHook.taskStatus.equals("RECEIVED");
  assert ApplicationHook.calls.stream().filter(j->j.optString("rpc").endsWith("action.produce")).findFirst().orElseThrow().optString("channel").equals("GW_MRCHSERVEBASE_DEFAULT");
  reset();run(false,false,true,Collections.singleton("BROWSE"));assert count("task.receive")==0&&count("action.produce")==0;
  reset();ApplicationHook.changed=false;run(false,false,true,Collections.emptySet());run(false,false,true,Collections.emptySet());assert count("task.receive")==1&&count("action.produce")==0;
  assert AntMember.memberFeaturePayload(new JSONObject().put("success",true).put("errCode","ERROR"))==null;
  reset();TimeUtil.cancel=true;try{run(true,true,true,Collections.emptySet());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.calls.isEmpty();
  reset();ApplicationHook.badOrder=true;run(false,false,true,Collections.emptySet());assert count("task.receive")==0:"malformed query token must prevent mutation";
  System.out.println("PASS merchant qualification/default-off, GMT+8 KMDK window, sign/zcj/ball readback, signup/complete/reward chain, blacklist, stale/failed repeat guards, MORE token forwarding/escaping and cancellation");
 }
}
'''
code=code.replace("@@PAYLOAD@@",method(member,"static JSONObject memberFeaturePayload(")+method(member,"static Map<String, JSONObject> memberRowsById("))
src=(SOURCE/service).read_text(encoding="utf-8")
code=code.replace("@@METHODS@@",src[src.index("    private static JSONObject response("):src.rfind("}")])
signatures=("merchantTranscodeCheck", "merchantHomePage", "merchantSign", "merchantZcj", "merchantActivity", "merchantKmdkAction", "merchantBallQuery", "merchantBallReceive", "merchantTaskQuery", "merchantTaskReceive", "merchantActionQuery", "merchantActionProduce", "merchantTaskFinish", "merchantExamPage")
code=code.replace("@@RPC@@","\n".join(method(rpc,"public static String "+s+"(") for s in signatures))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-merchant-") as tmp:
 java=Path(tmp)/"MerchantCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"MerchantCheck"],check=True)
