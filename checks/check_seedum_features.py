"""Replay Seedum 5406/Semper 20261012 credit boxes and AI fish project exchange.

Fixtures model bytecode-proven fields, not captured live responses. No device/RPC use.
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

MEMBER = "model/task/antMember/AntMember.java"
OCEAN = "model/task/antOcean/AntOcean.java"

code = r'''
import org.json.*;import java.util.*;
public class SeedumCheck {
 static final String TAG="test";
 static class TaskCancelledException extends RuntimeException {}
 static class TimeUtil {static boolean cancel;static int checks,cancelAt;static void sleep(long ms){if(cancel||++checks==cancelAt)throw new TaskCancelledException();}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Log {static String messages="",runtime="";static boolean unresolved;static void markUnresolved(){unresolved=true;}static void other(String s){messages+=s;}static void record(String s){messages+=s;runtime+=s;}
  static void error(String tag,String s){}static void err(String tag,String s,Throwable t){throw new AssertionError(t);}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class RpcRequestGuard {@@GUARD@@}
 static class ApplicationHook {static String method,args;static String requestString(String m,String a){method=m;args=a;return "{}";}}
 static class AntMemberRpcCall {
  static Deque<String> homes=new ArrayDeque<>();static String award="{}";static int awards;
  static String creditGameCenterHomePage(){return homes.remove();}static String creditBatchReceiveTaskPrize(){awards++;return award;}
 }
 static class AntOceanRpcCall {
  static Deque<String> projects=new ArrayDeque<>();static String exchange="{}";static int exchanges;
  static String antfishQueryProjectDetail(){return projects.remove();}static String antfishExchangeProject(){exchanges++;return exchange;}
 }
 @@METHODS@@
 @@RPCS@@
 static String getAntfishUniqueId(){return "testUniqueId";}
 static String home(Object pending){return new JSONObject().put("success",true).put("data",new JSONObject().put("taskModule",new JSONObject().put("needReceive",pending))).toString();}
 static String phases(String... statuses){JSONArray a=new JSONArray();for(String status:statuses)a.put(new JSONObject().put("exchangeStatus",status));return new JSONObject().put("resultCode","SUCCESS").put("project",new JSONObject().put("phaseList",a)).toString();}
 static void reset(){Log.messages="";Log.runtime="";Log.unresolved=false;AntMemberRpcCall.homes.clear();AntMemberRpcCall.awards=0;AntOceanRpcCall.projects.clear();AntOceanRpcCall.exchanges=0;TimeUtil.cancel=false;TimeUtil.checks=0;TimeUtil.cancelAt=0;}
 public static void main(String[] args)throws Exception {
  SeedumCheck c=new SeedumCheck();
  List<String> emptyCredit=List.of("{\"success\":true,\"data\":{}}","{\"success\":true,\"data\":{\"taskModule\":null}}","{\"success\":true,\"data\":{\"taskModule\":{}}}",home(JSONObject.NULL));
  for(String s:emptyCredit){reset();AntMemberRpcCall.homes.add(s);c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==0&&!Log.unresolved&&Log.runtime.contains("暂无待领宝箱"):"Successful empty credit query must not mark the module unresolved";}
  for(String s:List.of("{}","{\"success\":false,\"data\":{}}","{\"success\":true,\"data\":{\"success\":false}}","{\"success\":true,\"data\":{\"taskModule\":\"invalid\"}}",home("true"))){reset();AntMemberRpcCall.homes.add(s);c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==0&&Log.unresolved:"Failed queries and wrong field types stay unresolved";}
  for(String s:emptyCredit){reset();AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add(s);AntMemberRpcCall.award="{\"success\":true}";c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==1&&Log.unresolved&&!Log.runtime.contains("领取成功"):"Missing post-claim state is not proof of a successful claim";}
  for(String s:List.of("bad","{}","{\"success\":true,\"taskModule\":{\"needReceive\":true}}","{\"success\":false,\"data\":{\"taskModule\":{\"needReceive\":true}}}",home("true"),home(false))){reset();AntMemberRpcCall.homes.add(s);c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==0;}
  reset();AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add(home(false));AntMemberRpcCall.award="{\"success\":true,\"data\":{\"prizePopup\":{\"prizeItems\":[{\"prizeAmount\":\"3\"}]}}}";
  c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==1&&Log.runtime.contains("领取成功");
  // Empty rewards do not turn an invalid recheck into a successful claim.
  reset();AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add("{}");AntMemberRpcCall.award="{\"success\":true}";c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==1&&!Log.messages.contains("领取成功")&&Log.messages.contains("回查");
  reset();AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add(home(true));c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==1&&!Log.messages.contains("领取成功")&&Log.unresolved;
  // A timeout/failed receipt can still have committed: query is authoritative.
  reset();AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add(home(false));AntMemberRpcCall.award="{\"success\":false}";c.creditGameCenterTaskPrize();assert Log.messages.contains("领取成功");
  // No day-complete flag: a newly earned box after the earlier run is claimable.
  AntMemberRpcCall.homes.add(home(true));AntMemberRpcCall.homes.add(home(false));c.creditGameCenterTaskPrize();assert AntMemberRpcCall.awards==2;
  reset();AntMemberRpcCall.homes.add(home(true));TimeUtil.cancel=true;try{c.creditGameCenterTaskPrize();throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.awards==0;
  reset();AntMemberRpcCall.homes.add(home(true));TimeUtil.cancelAt=2;try{c.creditGameCenterTaskPrize();throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.awards==0:"Stale after query must not write";
  for(String s:List.of("bad","{}","{\"resultCode\":\"SUCCESS\"}",phases("waiting"),"{\"resultCode\":\"SUCCESS\",\"project\":{\"phaseList\":[{\"exchangeStatus\":true}]}}")){reset();AntOceanRpcCall.projects.add(s);c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==0;}
  reset();AntOceanRpcCall.projects.add(phases("canExchange","canExchange"));AntOceanRpcCall.projects.add(phases("exchanged","canExchange"));AntOceanRpcCall.projects.add(phases("exchanged","exchanged"));AntOceanRpcCall.exchange="{\"resultCode\":\"SUCCESS\",\"properties\":{\"protectContent\":\"test\",\"certificateName\":\"test\"}}";c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==2&&Log.runtime.contains("放生成功");
  // Completing one phase may unlock the next: the pending count can stay at one.
  reset();AntOceanRpcCall.projects.add(phases("canExchange","waiting"));AntOceanRpcCall.projects.add(phases("exchanged","canExchange"));AntOceanRpcCall.projects.add(phases("exchanged","exchanged"));c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==2&&Log.messages.contains("放生成功");
  reset();AntOceanRpcCall.projects.add(phases("canExchange"));AntOceanRpcCall.projects.add(phases("canExchange"));c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==1&&!Log.messages.contains("放生成功")&&Log.messages.contains("未推进")&&Log.unresolved;
  reset();AntOceanRpcCall.projects.add(phases("canExchange"));AntOceanRpcCall.projects.add("{}");c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==1&&Log.messages.contains("回查");
  reset();AntOceanRpcCall.projects.add(phases("canExchange"));TimeUtil.cancelAt=2;try{c.antfishScientificRelease();throw new AssertionError();}catch(TaskCancelledException expected){}assert AntOceanRpcCall.exchanges==0;
  reset();AntOceanRpcCall.projects.add(phases("canExchange"));AntOceanRpcCall.projects.add(phases());c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==1&&!Log.messages.contains("放生成功"):"Changed phase count is not proof of exchange";
  reset();String[] many=new String[11];Arrays.fill(many,"canExchange");AntOceanRpcCall.projects.add(phases(many));for(int i=0;i<10;i++){many[i]="exchanged";AntOceanRpcCall.projects.add(phases(many));}c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==10;
  reset();AntOceanRpcCall.projects.add(phases("canExchange"));AntOceanRpcCall.projects.add(phases("waiting"));AntOceanRpcCall.exchange="{\"resultCode\":\"FAIL\"}";c.antfishScientificRelease();assert AntOceanRpcCall.exchanges==1&&!Log.messages.contains("放生成功");
  creditGameCenterRequest("queryHomePage");JSONObject body=new JSONArray(ApplicationHook.args).optJSONObject(0);assert body.length()==4&&body.optString("sceneId").equals("zmyxzx_XCJNCE")&&body.optString("source").equals("zmxy_mc_xddpcz00");assert ApplicationHook.method.endsWith(".queryHomePage");
  creditGameCenterRequest("batchReceiveTaskPrize");assert ApplicationHook.method.endsWith(".batchReceiveTaskPrize");
  antfishProjectRequest("queryProjectDetail");body=new JSONArray(ApplicationHook.args).optJSONObject(0);assert body.length()==2&&body.optString("source").equals("ANT_OCEAN")&&body.optString("uniqueId").equals("testUniqueId");
  antfishProjectRequest("exchangeProject");assert ApplicationHook.method.equals("alipay.antaifish.h5.exchangeProject");
  System.out.println("PASS credit/fish: eligibility, malformed states, repeat runs, cancellation, recheck/no progress, protocol payloads");
 }
}
'''
methods = method(MEMBER, "static JSONObject memberFeaturePayload(")
methods += method(MEMBER, "private void creditGameCenterTaskPrize(")
methods += method(MEMBER, "private static Boolean creditGameCenterPending(")
methods += method(OCEAN, "private void antfishScientificRelease(")
methods += method(OCEAN, "private static List<String> antfishProjectPhases(")
code = code.replace("@@METHODS@@", methods).replace("@@GUARD@@", method("rpc/intervallimit/RpcRequestGuard.java", "public static boolean isFailure("))
code = code.replace("@@RPCS@@", method("model/task/antMember/AntMemberRpcCall.java", "private static String creditGameCenterRequest(") + method("model/task/antOcean/AntOceanRpcCall.java", "private static String antfishProjectRequest("))
for path, field in [(MEMBER, "creditGameCenter"), (OCEAN, "antfishScientificRelease")]:
    source = (SOURCE / path).read_text(encoding="utf-8")
    assert f'new BooleanModelField("{field}",' in source
    assert f"if ({field}.getValue())" in source
member_run = method(MEMBER, "public void run(")
ocean_run = method(OCEAN, "public void run(")
assert member_run.index("creditGameCenterTaskPrize();") < member_run.index("MemberBrowseTasks.run("), "independent credit claim must not wait for member browse tasks"
credit_entry = method(MEMBER, "if (creditGameCenter.getValue())")
assert "信用宝箱[入口]：当前账号开关=true" in credit_entry and "creditGameCenterTaskPrize();" in credit_entry
assert member_run.count("信用宝箱[入口]") == 1 and "科学放生[入口]" not in ocean_run
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-seedum-") as tmp:
    source = Path(tmp)/"SeedumCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp+os.pathsep+str(jar), "SeedumCheck"], check=True, timeout=30)
