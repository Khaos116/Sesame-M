"""Replay the production SJ worker: state advances, budgets, uncertainty and exclusions."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/aw1y2z/sesame'
code = r'''
import org.json.*;import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
public class SjActivityCheck {
 static class MyUtils{static int day=8;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day);return c;}static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class UserIdMap{static String uid="self";static String getCurrentUid(){return uid;}}
 static class System{static long nanos;static final java.io.PrintStream out=java.lang.System.out;static long nanoTime(){return nanos;}static long currentTimeMillis(){return java.lang.System.currentTimeMillis();}}
 static class TimeUtil{static boolean cancel,switchWait,crossWait;static long waited;static void sleep(long n){if(cancel)throw new TaskCancelledException();System.nanos+=n*1000000L;if(n==1000){waited++;if(switchWait)UserIdMap.uid="other";if(crossWait)MyUtils.day++;}}}
 static class RuntimeInfo{static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String k){return data.getOrDefault(k,"");}long getLong(String k,long d){try{return Long.parseLong(getString(k));}catch(Exception e){return d;}}void put(String k,Object v){data.put(k,String.valueOf(v));}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Status{static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}}
 static class Log{static int confirmed;static void other(String s){confirmed++;}static void record(String s){}}
 static class RpcRequestGuard{static boolean isFailure(JSONObject o){return Boolean.FALSE.equals(o.opt("success"))||o.has("errorCode");}}
 @@GATE@@
 @@WORKER@@
 static int queries,writes,signups,sends,draws,remaining,consumed,points,days;static String taskState,title,type,signState;static boolean componentOnly,componentFailure,signed,advance,badAck,missing,duplicate,switchWrite,cancelWrite,crossWrite,risk;
 static JSONObject ok(){return new JSONObject().put("success",true).put("code","10000001");}
 static JSONObject lotteryRow(){return new JSONObject().put("playId","play\"\\").put("taskType",type).put("taskStatus",taskState).put("taskExtProps",new JSONObject().put("taskTitle",title).put("taskTemplate","BROWSE").put("browseTime",3));}
 static JSONObject hbRow(){return new JSONObject().put("taskId","task\"\\").put("taskCenId","center").put("taskType","APPLET").put("taskShowStatus",taskState).put("taskTitle",title).put("browseTime",3);}
 static JSONObject modularRow(){return new JSONObject().put("taskId","rideTask").put("title",title).put("taskType",type).put("browseTime",3).put("taskStatus",taskState);}
 static JSONArray rows(JSONObject row){JSONArray a=new JSONArray().put(row);if(duplicate)a.put(new JSONObject(row.toString()));return a;}
 static class ApplicationHook{
  static boolean offline;static boolean isOffline(){return offline;}
  static String requestString(String op,String raw)throws Exception{return rpc(op,raw,false);}
  static String requestString(String op,String raw,int attempts,int retries)throws Exception{assert attempts==1&&retries==0;return rpc(op,raw,true);}
  static String rpc(String op,String raw,boolean write)throws Exception{
   JSONArray payload=new JSONArray(raw);assert payload.length()==1;JSONObject a=payload.optJSONObject(0),root=ok();assert a!=null;
   if(write){writes++;if(advance){if(op.endsWith("checkIn"))signed=true;else if(op.endsWith("play.trigger"))taskState="TO_RECEIVE";else if(op.endsWith("prize.receive")){taskState="RECEIVED";remaining++;}else if(op.endsWith("lottery-machine.receive")){remaining--;consumed++;draws++;}else if(op.endsWith("p2e.signIn")){assert a.optString("signSequenceId").equals("seq")&&a.optLong("index")==3;signState="SIGNED";days++;}else if(op.equals("alipay.promoprod.applet.trigger")){assert a.optString("appletId").equals("task\"\\");if(a.optString("stageCode").equals("signup"))signups++;else{assert TimeUtil.waited==3;taskState="COMPLETED";sends++;}}else if(op.endsWith("finishModularTask")){assert TimeUtil.waited==3;taskState="FINISHED";}}
    if(badAck)root.put("success",false);if(risk)root.put("retCode","1009");if(switchWrite)UserIdMap.uid="other";if(crossWrite)MyUtils.day++;if(cancelWrite)TimeUtil.cancel=true;
   }else queries++;
   if(op.endsWith("camp.query"))root.put("data",new JSONObject().put("active",true).put("remainingCount",remaining).put("dayConsumeCount",consumed).put("extInfo",new JSONObject().put("hasCheckedIn",signed)));
   else if(op.endsWith("checkInConsult"))root.put("data",new JSONObject().put("triggerCheckIn",true));
   else if(op.endsWith("taskConsult"))root.put("data",new JSONObject().put("taskList",rows(lotteryRow())));
   else if(op.endsWith("lottery-machine.receive"))root.put("data",new JSONObject().put("prizeList",new JSONArray()));
   else if(op.endsWith("p2e.queryHomePage")){JSONObject sign=new JSONObject().put("signSequenceId","seq").put("date",String.format("2026-10-%02d",MyUtils.day)).put("completedSignUpDays",days).put("signRecordVOList",rows(new JSONObject().put("isToday",true).put("displayIndex",3).put("signUpStatus",signState)));root.put("data",new JSONObject().put("signUpModuleVO",sign));}
   else if(op.endsWith("queryIntimacyTaskListCard"))root.put("result",new JSONObject().put("taskDetailDtos",rows(hbRow())));
   else if(op.endsWith("queryModularTaskList"))root.put("result",new JSONObject().put("modularTaskList",new JSONArray().put(new JSONObject().put("taskList",rows(modularRow())))));
   else if(op.equals("alipay.imasp.program.programInvoke")){assert a.optString("cityCode").equals("510500");JSONObject components=a.optJSONObject("components");String key=components.keys().next();JSONObject content=new JSONObject();if(key.equals("mileage_point_query_detail"))content.put("mileagePointInfo",new JSONObject().put("currentPoint",points));else{assert write&&key.endsWith("exchange_sell_goods_exchange")&&components.optJSONObject(key).optString("code").equals("EXP1");if(advance){points-=10;content.put("exchangePrize",new JSONObject().put("prizeName","券"));}}root.put("components",new JSONObject().put(key,new JSONObject().put("isSuccess",true).put("content",content)));}
   if(op.equals("alipay.imasp.program.programInvoke")){JSONObject component=root.optJSONObject("components").optJSONObject(a.optJSONObject("components").keys().next());if(componentOnly){root.remove("success");component.remove("isSuccess");}if(componentFailure)component.put("isSuccess",false);}
   if(missing)root.remove("data");return root.toString();
  }
 }
 static SjActivityTasks worker(int budget){return new SjActivityTasks(new OtherRequestGate(),budget);}
 static boolean pending(String domain){return !RuntimeInfo.instance.getString("sjActivityReceipt::"+domain).isEmpty();}
 static void reset(){MyUtils.day=8;UserIdMap.uid="self";RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Log.confirmed=queries=writes=signups=sends=draws=consumed=0;remaining=2;points=100;days=2;taskState="INIT";title="浏览活动页面";type="BROWSE";signState="UNSIGNED";signed=false;advance=true;componentOnly=componentFailure=false;badAck=missing=duplicate=switchWrite=cancelWrite=crossWrite=risk=TimeUtil.cancel=TimeUtil.switchWait=TimeUtil.crossWait=ApplicationHook.offline=false;TimeUtil.waited=0;}
 static void lottery(boolean sign,boolean tasks,boolean draw,int budget)throws Exception{worker(budget).shenQuan(sign,tasks,draw,"{\"city\":\"泸州市\",\"cityAdcode\":\"510500\",\"district\":\"龙马潭区\",\"province\":\"四川省\",\"provinceAdcode\":\"510000\",\"latitude\":\"28.893704\",\"longitude\":\"105.421453\",\"poiNameForTitle\":\"金诺·御景山居\",\"walletVersion\":\"12.12.20\"}");}
 public static void main(String[] args)throws Exception{
  reset();lottery(true,true,true,5);assert writes==5&&draws==2&&Log.confirmed==5&&TimeUtil.waited==3&&!pending("shenQuan");
  reset();lottery(true,true,true,0);assert queries+writes==0;
  reset();lottery(false,false,false,5);assert queries+writes==0;
  reset();title="浏览开通余额宝";lottery(false,true,false,5);assert writes==0;
  reset();title="浏览支付活动";lottery(false,true,false,5);assert writes==0;
  reset();type="SUBSCRIBE";lottery(false,true,false,5);assert writes==0;
  reset();duplicate=true;lottery(false,true,false,5);assert writes==0;
  reset();missing=true;lottery(true,true,true,5);assert writes==0;
  reset();RuntimeInfo.writable=false;lottery(true,true,true,5);assert writes==0;
  reset();RuntimeInfo.data.put("sjActivityAttempts","broken");lottery(true,true,true,5);assert writes==0;
  reset();advance=false;lottery(false,true,false,5);assert writes==1&&pending("shenQuan")&&Log.confirmed==0;MyUtils.day++;lottery(false,true,false,5);assert writes==1;
  reset();badAck=true;lottery(true,false,false,5);assert writes==1&&pending("shenQuan")&&Log.confirmed==0;
  reset();lottery(true,true,true,1);assert writes==1&&!pending("shenQuan");lottery(true,true,true,1);assert writes==1;
  reset();worker(1).p2eSign();assert writes==1&&days==3&&Log.confirmed==1&&!pending("p2e");worker(1).p2eSign();assert writes==1;
  reset();duplicate=true;worker(1).p2eSign();assert writes==0;
  reset();advance=false;worker(1).p2eSign();assert writes==1&&pending("p2e")&&Log.confirmed==0;
  reset();worker(2).mileage("EXP1","510500");assert writes==1&&points==90&&Log.confirmed==1&&!pending("mileage");worker(2).mileage("EXP1","510500");assert writes==1;
  reset();componentOnly=true;worker(2).mileage("EXP1","510500");assert writes==1&&points==90&&Log.confirmed==1;
  reset();componentFailure=true;worker(2).mileage("EXP1","510500");assert writes==0;
  reset();worker(2).mileage("","510500");worker(2).mileage("EXP1","abroad");assert queries+writes==0;
  reset();advance=false;worker(2).mileage("EXP1","510500");assert writes==1&&pending("mileage");
  reset();taskState="AVAILABLE";worker(2).intimacy();assert signups==1&&sends==1&&TimeUtil.waited==3&&Log.confirmed==1&&!pending("intimacy");
  reset();taskState="AVAILABLE";worker(1).intimacy();assert signups==1&&sends==0&&pending("intimacy");worker(10).intimacy();assert signups==1;
  reset();taskState="AVAILABLE";title="浏览借款产品";worker(2).intimacy();assert writes==0;
  reset();taskState="AVAILABLE";TimeUtil.switchWait=true;try{worker(2).intimacy();throw new AssertionError("switch swallowed");}catch(TaskCancelledException expected){}assert signups==1&&sends==0&&pending("intimacy");
  reset();taskState="TODO";title="乐游记浏览任务";worker(1).leiYouJiTasks();assert writes==1&&TimeUtil.waited==3&&Log.confirmed==1&&!pending("leiYouJi");
  reset();taskState="TODO";title="乐游记购买任务";worker(1).leiYouJiTasks();assert writes==0;
  reset();switchWrite=true;try{lottery(true,false,false,5);throw new AssertionError("switch swallowed");}catch(TaskCancelledException expected){}assert writes==1&&pending("shenQuan");
  reset();crossWrite=true;try{lottery(true,false,false,5);throw new AssertionError("cross day swallowed");}catch(TaskCancelledException expected){}assert writes==1&&pending("shenQuan");
  reset();cancelWrite=true;try{lottery(true,false,false,5);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert writes==1&&pending("shenQuan");
  reset();risk=true;try{lottery(true,false,false,5);throw new AssertionError("risk swallowed");}catch(OtherRequestGate.Denied expected){}assert writes==1&&pending("shenQuan");
  assert SjActivityTasks.drawLocation("{}") == null;assert SjActivityTasks.count(new JSONObject().put("n",1.5),"n")==-1;
  System.out.println("PASS production SJ activities: sign/tasks/draw/intimacy/P2E/mileage/modular, readback, single sends, persistent quotas/uncertainty, financial exclusions and account/day/cancel guards");
 }
}
'''
for marker, filename in [('@@GATE@@', 'OtherRequestGate.java'), ('@@WORKER@@', 'SjActivityTasks.java')]:
    source = (SOURCE / 'model/task/other' / filename).read_text(encoding='utf-8')
    start = source.index('final class ')
    code = code.replace(marker, 'static ' + source[start:])
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-sj-activities-') as tmp:
    java = Path(tmp) / 'SjActivityCheck.java'
    java.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(java),
                    str(SOURCE / 'util/TaskCancelledException.java'), str(SOURCE / 'rpc/intervallimit/RequestBudgetPolicy.java'),
                    str(SOURCE / 'rpc/intervallimit/RpcFailurePolicy.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), 'SjActivityCheck'], check=True)
