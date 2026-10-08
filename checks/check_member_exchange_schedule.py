"""Compile actual exchange scheduling/budget helpers with a deterministic task clock."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
member="model/task/antMember/AntMember.java"
src=(SOURCE/member).read_text(encoding="utf-8")
assert 'new BooleanModelField("memberPointExchangeSecKill"' in src,"Missing timed exchange option"
code=r'''
import java.util.*;
import org.json.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ExchangeScheduleCheck {
 static final String TAG="check";
 static long now=java.time.Instant.parse("2026-10-06T01:59:00Z").toEpochMilli();
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(now);return c;}static JSONObject newJSONObject(String s){return new JSONObject(s);}}
 static class Bool {boolean n=true;boolean getValue(){return n;}}
 static class Text {String s="10:00,20:00";String getValue(){return s;}}
 static class Num {int n=100;int getValue(){return n;}}
 Bool memberPointExchangeSecKill=new Bool(),memberPointExchangeBenefit=new Bool();Text memberPointExchangeSecKillTimes=new Text();Num memberPointExchangeSecKillBudget=new Num();
 String memberExchangeScheduledId;final Object benefitExchangeLock=new Object();int exchanges;
 static class ChildModelTask {String id,group;long at;Runnable body;ChildModelTask(String i,String g,Runnable b,Long t){id=i;group=g;body=b;at=t;}Long getExecTime(){return at;}}
 Map<String,ChildModelTask> children=new HashMap<>();ChildModelTask getChildTask(String id){return children.get(id);}boolean addChildTask(ChildModelTask task){children.put(task.id,task);return true;}void removeChildTask(String id){children.remove(id);}
 private void memberPointExchangeBenefit(boolean timed){exchanges++;}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class TaskCommon {static boolean IS_ENERGY_TIME;}
 static class Log {static void other(String s){}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class Status {static Map<String,Integer> counts=new HashMap<>();static Set<String> flags=new HashSet<>();static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int n){counts.put(k,n);}static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}static boolean canMemberPointExchangeBenefitToday(String id){return true;}static void memberPointExchangeBenefitToday(String id){} }
 static class AntMemberRpcCall {static int calls;static String response="{\"resultCode\":\"SUCCESS\"}";static List<String> details=new ArrayList<>();static String querySingleBenefitDetail(String id){details.add(id);return new JSONObject().put("success",true).put("benefitDetail",new JSONObject().put("benefitId",id).put("itemId","item").put("name",id).put("pricePresentation",new JSONObject().put("strategyType","POINT_PAY").put("point",50).put("grabHour",id.equals("later")?20:10))).toString();}@@RPC_EXCHANGE@@}
 static class ApplicationHook {static class Version {String getVersionString(){return "12.12.20";}}static Version getAlipayVersion(){return new Version();}static String requestString(String name,String raw){throw new AssertionError("exchange used default retries");}static String requestString(String name,String raw,int tries,int pause){assert name.endsWith("exchangeBenefit")&&tries==1&&pause==0;JSONObject body=new JSONArray(raw).optJSONObject(0);assert body.optString("itemId").equals("item")&&body.optString("exchangeType").equals("POINT_PAY");AntMemberRpcCall.calls++;return AntMemberRpcCall.response;}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 @@METHODS@@
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  long ten=java.time.Instant.parse("2026-10-06T02:00:00Z").toEpochMilli(),twenty=java.time.Instant.parse("2026-10-06T12:00:00Z").toEpochMilli();
  assert nextMemberExchangeTime("bad,25:00,10:60,10:00,20:00",now)==ten;
  assert nextMemberExchangeTime("10:00,20:00",ten+1)==twenty;
  assert nextMemberExchangeTime("10:00,20:00",twenty+1)==ten+86400000L;
  assert nextMemberExchangeTime("invalid",now)==-1;
  ExchangeScheduleCheck f=new ExchangeScheduleCheck();f.scheduleMemberExchange();assert f.children.size()==1;ChildModelTask first=f.children.values().iterator().next();assert first.at==ten;
  f.scheduleMemberExchange();assert f.children.size()==1;
  now=ten;first.body.run();assert f.exchanges==1&&f.children.size()==2;
  assert f.children.values().stream().anyMatch(t->t.at==twenty);first.body.run();assert f.exchanges==1;
  f.memberPointExchangeSecKill.n=false;f.scheduleMemberExchange();assert f.children.size()==1;
  f.memberPointExchangeSecKill.n=true;f.memberPointExchangeSecKillBudget.n=0;f.executeMemberExchange(ten);assert f.exchanges==1;
  JSONObject p=new JSONObject().put("strategyType","POINT_PAY").put("point","50").put("grabHour",10);
  assert memberTimedPrice(p,10)==50&&memberTimedPrice(p,20)==-1;
  assert memberTimedPrice(new JSONObject(p.toString()).put("point","1.5"),10)==-1;
  assert memberTimedPrice(new JSONObject(p.toString()).put("yuan","1"),10)==-1;
  f.memberPointExchangeSecKillBudget.n=100;assert f.exchangeTimedBenefit("benefit","item",50);assert Status.getIntFlagToday("member::secKillSpent")==50;
  assert !f.exchangeTimedBenefit("next","item",60)&&AntMemberRpcCall.calls==1;
  assert !f.exchangeTimedBenefit("empty","",5)&&Status.getIntFlagToday("member::secKillSpent")==50;
  assert !f.exchangeTimedBenefit("benefit","item",50)&&AntMemberRpcCall.calls==1;
  AntMemberRpcCall.response="{}";assert !f.exchangeTimedBenefit("failed","item",40);assert Status.getIntFlagToday("member::secKillSpent")==90;assert !f.exchangeTimedBenefit("failed","item",40);
  Status.counts.clear();Status.flags.clear();AntMemberRpcCall.response="{\"resultCode\":\"SUCCESS\"}";AntMemberRpcCall.calls=0;f.exchangeSelectedTimedBenefits(new LinkedHashSet<>(Arrays.asList("chosen","later")));assert AntMemberRpcCall.calls==1&&AntMemberRpcCall.details.equals(Arrays.asList("chosen","later"));
  TimeUtil.cancel=true;try{f.exchangeTimedBenefit("cancel","item",1);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
  f.children.clear();f.memberExchangeScheduledId="memberBenefitSecKill_"+ten;
  try{f.executeMemberExchange(ten);throw new AssertionError("callback cancel swallowed");}catch(TaskCancelledException expected){}
  assert f.children.isEmpty()&&f.memberExchangeScheduledId==null:"cancelled account callback requeued";
  System.out.println("PASS GMT+8 timetable/invalid/next-day/dedup, callback revalidation/reschedule, shared attempt guard, strict price/cash/grab-hour, reserved daily budget and cancellation");
 }
}
'''
code=code.replace("@@METHODS@@","\n".join(method(member,s) for s in ("private static long nextMemberExchangeTime(","private synchronized void scheduleMemberExchange(","private void executeMemberExchange(","private static int memberTimedPrice(","private boolean exchangeTimedBenefit(","private Boolean exchangeBenefit(","static JSONObject memberFeaturePayload(","private void exchangeSelectedTimedBenefits(")))
code=code.replace('@@RPC_EXCHANGE@@',method('model/task/antMember/AntMemberRpcCall.java','public static String exchangeBenefit('))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-member-timer-") as tmp:
 java=Path(tmp)/"ExchangeScheduleCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"ExchangeScheduleCheck"],check=True)
