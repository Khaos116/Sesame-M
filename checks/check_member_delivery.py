"""Replay real delivery task methods: reject invalid snapshots/IDs and false completion logs."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

code = r'''
import org.json.*;import java.util.*;
public class DeliveryCheck {
 static String TAG="delivery";
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static void sleep(long ms){}}
 static class Log {static String messages="";static void other(String s){messages+=s;}static void record(String s){messages+=s;}
  static void i(String a,String b){}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return j.optBoolean("success")||"SUCCESS".equals(j.optString("resultCode"));}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class AntMemberRpcCall {
  static String list="{}",signup="{}",award="{}";static int signs,sends,triggers,awards;
  static String queryRecommendTask(){return list;}static String queryOrdinaryTask(){return list;}
  static String forestTask(){return "{}";}static String oceanTask(){return "{}";}
  static String forestreceiveTaskAward(){awards++;return award;}static String oceanreceiveTaskAward(){awards++;return award;}
  static String trigger(String id){triggers++;return "{}";}static String signuptrigger(String id){signs++;return signup;}
  static String sendtrigger(String id){sends++;return "{}";}
 }
 @@METHODS@@
 static String list(boolean success,String id,String taskCode){return new JSONObject().put("success",success).put("taskDetailList",new JSONArray().put(
  new JSONObject().put("taskId",id).put("taskProcessStatus","TODO").put("canAccess",true)
  .put("taskMaterial",new JSONObject().put("taskCode",taskCode).put("taskId",id))
  .put("taskBaseInfo",new JSONObject().put("appletName","forest")))).toString();}
 public static void main(String[] args){
  DeliveryCheck c=new DeliveryCheck();AntMemberRpcCall.list=list(false,"id","WELFARE_PLUS_ANT_FOREST");c.RecommendTask();
  assert AntMemberRpcCall.triggers==0&&AntMemberRpcCall.awards==0;
  AntMemberRpcCall.list=list(true,"","");c.RecommendTask();c.OrdinaryTask();assert AntMemberRpcCall.triggers==0&&AntMemberRpcCall.signs==0;
  AntMemberRpcCall.list=list(true,"id","");c.OrdinaryTask();assert AntMemberRpcCall.signs==1&&AntMemberRpcCall.sends==0;
  AntMemberRpcCall.signup="{\"success\":true}";c.OrdinaryTask();assert AntMemberRpcCall.sends==1;
  AntMemberRpcCall.list=list(true,"id","WELFARE_PLUS_ANT_FOREST");Log.messages="";c.RecommendTask();
  assert !Log.messages.contains("领取奖励[")&&Log.messages.contains("奖励未确认领取");
  Status.flags.add("antMember::kuaidiForestAward");Log.messages="";int previousAwards=AntMemberRpcCall.awards;c.RecommendTask();
  assert AntMemberRpcCall.awards==previousAwards&&!Log.messages.contains("奖励未确认领取")&&!Log.messages.contains("领取奖励["):"no award request must not log an award result";
  Status.flags.clear();
  AntMemberRpcCall.award="{\"resultCode\":\"SUCCESS\"}";Log.messages="";c.RecommendTask();assert Log.messages.contains("领取奖励[");
  assert deliverySuccess(new JSONObject().put("resultCode","SUCCESS"));
  assert !deliverySuccess(new JSONObject().put("success",false).put("resultCode","SUCCESS"));
  assert !deliverySuccess(new JSONObject());
  System.out.println("PASS delivery failed list, empty ID, failed signup stop writes; award logs require confirmed response");
 }
}
'''
code = code.replace("@@METHODS@@", method("model/task/antMember/AntMember.java", "private static boolean deliverySuccess(")+method("model/task/antMember/AntMember.java", "private void RecommendTask(")
                    + method("model/task/antMember/AntMember.java", "private void OrdinaryTask("))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-delivery-") as tmp:
    source=Path(tmp)/"DeliveryCheck.java";source.write_text(code,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(source)],check=True)
    subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"DeliveryCheck"],check=True,timeout=30)
