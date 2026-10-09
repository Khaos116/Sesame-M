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
 static class Log{static int confirmed;static List<String> lines=new ArrayList<>();static void other(String s){confirmed++;lines.add(s);}static void record(String s){lines.add(s);}}
  static class RpcRequestGuard{@@FAILURE@@}
 @@GATE@@
 @@WORKER@@
  static int queries,writes,signups,sends,draws,remaining,consumed,points,days;static Integer browseSeconds=3;static String taskState,title,type,signState;static boolean componentOnly,componentFailure,signed,advance,badAck,signError,missing,missingAfterWrite,stallTrigger,extraTask,duplicate,switchWrite,cancelWrite,crossWrite,risk,transportError;
  static boolean promplayMgw, signUnconfirmed, autoReward, queryFailure;
  static JSONArray drawPrizes;
  static JSONObject ok(){return new JSONObject().put("success",true).put("code","10000001");}
 static JSONObject lotteryRow(){return new JSONObject().put("playId","play\"\\").put("taskType",type).put("taskStatus",taskState).put("taskExtProps",new JSONObject().put("taskTitle",title).put("taskTemplate","BROWSE").put("browseTime",browseSeconds));}
 static JSONObject hbRow(){return new JSONObject().put("taskId","task\"\\").put("taskCenId","center").put("taskType","APPLET").put("taskShowStatus",taskState).put("taskTitle",title).put("browseTime",3);}
 static JSONObject modularRow(){return new JSONObject().put("taskId","rideTask").put("title",title).put("taskType",type).put("browseTime",3).put("taskStatus",taskState);}
 static JSONArray rows(JSONObject row){JSONArray a=new JSONArray().put(row);if(duplicate)a.put(new JSONObject(row.toString()));return a;}
 static class ApplicationHook{
  static boolean offline;static boolean isOffline(){return offline;}
  static String requestString(String op,String raw)throws Exception{return rpc(op,raw,false);}
  static String requestString(String op,String raw,int attempts,int retries)throws Exception{assert attempts==1&&retries==0;return rpc(op,raw,true);}
  static String rpc(String op,String raw,boolean write)throws Exception{
   JSONArray payload=new JSONArray(raw);assert payload.length()==1;JSONObject a=payload.optJSONObject(0),root=ok();assert a!=null;
   if(write){writes++;if(transportError)throw new java.io.IOException("authCode=SECRET_CODE&token=SECRET_TOKEN");if(advance){if(op.endsWith("checkIn")){signed=!signUnconfirmed;if(signUnconfirmed)remaining++;}else if(op.endsWith("play.trigger"))taskState=autoReward?"RECEIVED":"TO_RECEIVE";else if(op.endsWith("prize.receive")){taskState="RECEIVED";remaining++;}else if(op.endsWith("lottery-machine.receive")){remaining--;consumed++;draws++;}else if(op.endsWith("p2e.signIn")){assert a.optString("signSequenceId").equals("seq")&&a.optLong("index")==3;signState="SIGNED";days++;}else if(op.equals("alipay.promoprod.applet.trigger")){assert a.optString("appletId").equals("task\"\\");if(a.optString("stageCode").equals("signup"))signups++;else{assert TimeUtil.waited==3;taskState="COMPLETED";sends++;}}else if(op.endsWith("finishModularTask")){assert TimeUtil.waited==3;taskState="FINISHED";}}
     if(badAck)root.put("success",false).put("message","authCode=SECRET_CODE&token=SECRET_TOKEN");if(signError&&op.endsWith("checkIn"))root=new JSONObject().put("error",3000);if(risk)root.put("retCode","1009");if(switchWrite)UserIdMap.uid="other";if(crossWrite)MyUtils.day++;if(cancelWrite)TimeUtil.cancel=true;
   }else {queries++;if(queryFailure)root.put("success",false).put("error",3000);}
     if(promplayMgw&&op.startsWith("alipay.asset.promplaymatrix.play.")){root.remove("code");root.put("resultCode","MGW200");}
     if(op.endsWith("prize.receive"))root.put("prizeInfoList",new JSONArray().put(new JSONObject().put("prizeName","神券-完成任务得抽奖机会")));
     if(stallTrigger&&op.endsWith("play.trigger"))taskState="INIT";
    if(op.endsWith("camp.query"))root.put("data",new JSONObject().put("active",true).put("remainingCount",remaining).put("dayConsumeCount",consumed).put("extInfo",new JSONObject().put("hasCheckedIn",signed)));
   else if(op.endsWith("checkInConsult"))root.put("data",new JSONObject().put("triggerCheckIn",true));
    else if(op.endsWith("taskConsult")){JSONArray tasks=rows(lotteryRow());if(extraTask)tasks.put(new JSONObject(lotteryRow().toString()).put("playId","other"));root.put("data",new JSONObject().put("taskList",tasks));}
    else if(op.endsWith("lottery-machine.receive"))root.put("data",new JSONObject().put("prizeList",drawPrizes==null?new JSONArray():drawPrizes));
   else if(op.endsWith("p2e.queryHomePage")){JSONObject sign=new JSONObject().put("signSequenceId","seq").put("date",String.format("2026-10-%02d",MyUtils.day)).put("completedSignUpDays",days).put("signRecordVOList",rows(new JSONObject().put("isToday",true).put("displayIndex",3).put("signUpStatus",signState)));root.put("data",new JSONObject().put("signUpModuleVO",sign));}
   else if(op.endsWith("queryIntimacyTaskListCard"))root.put("result",new JSONObject().put("taskDetailDtos",rows(hbRow())));
   else if(op.endsWith("queryModularTaskList"))root.put("result",new JSONObject().put("modularTaskList",new JSONArray().put(new JSONObject().put("taskList",rows(modularRow())))));
   else if(op.equals("alipay.imasp.program.programInvoke")){assert a.optString("cityCode").equals("510500");JSONObject components=a.optJSONObject("components");String key=components.keys().next();JSONObject content=new JSONObject();if(key.equals("mileage_point_query_detail"))content.put("mileagePointInfo",new JSONObject().put("currentPoint",points));else{assert write&&key.endsWith("exchange_sell_goods_exchange")&&components.optJSONObject(key).optString("code").equals("EXP1");if(advance){points-=10;content.put("exchangePrize",new JSONObject().put("prizeName","券"));}}root.put("components",new JSONObject().put(key,new JSONObject().put("isSuccess",true).put("content",content)));}
   if(op.equals("alipay.imasp.program.programInvoke")){JSONObject component=root.optJSONObject("components").optJSONObject(a.optJSONObject("components").keys().next());if(componentOnly){root.remove("success");component.remove("isSuccess");}if(componentFailure)component.put("isSuccess",false);}
    if(missing||(missingAfterWrite&&writes>0&&!write))root.remove("data");return root.toString();
  }
 }
 static SjActivityTasks worker(int budget){return new SjActivityTasks(new OtherRequestGate(),budget);}
  static boolean pending(String domain){return !RuntimeInfo.instance.getString("sjActivityReceipt::"+domain).isEmpty()||domain.equals("shenQuan")&&RuntimeInfo.data.keySet().stream().anyMatch(k->(k.startsWith("sjActivityReceipt::shenQuanTask::")||k.equals("sjActivityReceipt::shenQuanSign"))&&!k.contains("::history::"));}
  static void reset(){queryFailure=signUnconfirmed=autoReward=promplayMgw=false;drawPrizes=null;browseSeconds=3;MyUtils.day=8;UserIdMap.uid="self";RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Log.lines.clear();Log.confirmed=queries=writes=signups=sends=draws=consumed=0;remaining=2;points=100;days=2;taskState="INIT";title="浏览活动页面";type="BROWSE";signState="UNSIGNED";signed=false;advance=true;componentOnly=componentFailure=false;badAck=signError=missing=missingAfterWrite=stallTrigger=extraTask=duplicate=switchWrite=cancelWrite=crossWrite=risk=transportError=TimeUtil.cancel=TimeUtil.switchWait=TimeUtil.crossWait=ApplicationHook.offline=false;TimeUtil.waited=0;}
 static void lottery(boolean sign,boolean tasks,boolean draw,int budget)throws Exception{worker(budget).shenQuan(sign,tasks,draw,"{\"city\":\"泸州市\",\"cityAdcode\":\"510500\",\"district\":\"龙马潭区\",\"province\":\"四川省\",\"provinceAdcode\":\"510000\",\"latitude\":\"28.893704\",\"longitude\":\"105.421453\",\"poiNameForTitle\":\"金诺·御景山居\",\"walletVersion\":\"12.12.20\"}");}
  public static void main(String[] args)throws Exception{
   reset();promplayMgw=true;lottery(false,true,false,5);promplayMgw=false;
   assert writes==2&&TimeUtil.waited==3&&!pending("shenQuan") : "MGW200 success stopped the normal trigger/wait/reward flow";
   assert Log.lines.stream().anyMatch(s->s.contains("完成任务[浏览活动页面]得[神券-完成任务得抽奖机会]")) : "confirmed task title and actual prize name missing from runtime log";
   assert Log.lines.stream().anyMatch(s->s.startsWith("✅ 神券团购完成任务[浏览活动页面]"))&&Log.lines.stream().anyMatch(s->s.startsWith("📤 神券团购任务[浏览活动页面]上报"))&&!Log.lines.stream().anyMatch(s->s.startsWith("✅")&&s.contains("上报")) : "submission was printed as success before reward confirmation";
   assert Log.lines.stream().anyMatch(s->s.contains("本轮新完成并领奖=1项，此前已完成并领奖=0项"));
   reset();title="浏览上报活动页面";lottery(false,true,false,5);
   assert Log.lines.stream().anyMatch(s->s.startsWith("✅ 神券团购完成任务[浏览上报活动页面]")) : "task title changed the completion result emoji";
   reset();taskState="RECEIVED";signed=true;remaining=0;consumed=9;lottery(true,true,true,5);
   assert writes==0&&Log.lines.stream().anyMatch(s->s.contains("本轮新完成并领奖=0项，此前已完成并领奖=1项")) : "previously done tasks were reported as new success";
   assert Log.lines.stream().anyMatch(s->s.contains("剩余机会为0"));
   assert Log.lines.stream().anyMatch(s->s.startsWith("☑️ ")&&s.contains("此前已完成并领奖"))&&Log.lines.stream().noneMatch(s->s.contains("进入查询RPC调用")||s.contains("响应 success=true")) : "completed read-only flow is cluttered by successful RPC diagnostics";
   reset();queryFailure=true;lottery(false,true,false,5);
   assert writes==0&&Log.lines.stream().anyMatch(s->s.contains("camp.query")&&s.contains("success=false")&&s.contains("error=3000")) : "failed read lost the interface/business evidence";
   reset();signUnconfirmed=signError=true;lottery(true,true,true,10);
   assert writes==7&&draws==4&&!signed&&taskState.equals("RECEIVED")&&pending("shenQuan") : "unconfirmed sign blocked independent tasks or existing free draws";
   String unknownSign=RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanSign");assert !unknownSign.isEmpty()&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").isEmpty();
   lottery(true,true,true,10);assert writes==7&&unknownSign.equals(RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanSign")) : "unknown sign was repeated or discarded";
   reset();signUnconfirmed=signError=true;lottery(true,true,true,1);
   assert writes==1&&Log.lines.stream().anyMatch(s->s.contains("查询到任务数"))&&Log.lines.stream().anyMatch(s->s.contains("每日操作预算已用尽（1/1）")) : "shared budget was bypassed or task checks stopped after sign failure";
   reset();String legacySign=new JSONObject().put("uid","self").put("day",20261008).put("action","sign").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",legacySign);lottery(true,true,true,10);
   assert writes==5&&draws==3&&!signed&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanSign").equals(legacySign) : "legacy unknown sign blocked independent work or was replaced";
   reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",legacySign);RuntimeInfo.data.put("sjActivityReceipt::shenQuanSign",legacySign+" ");lottery(true,true,true,10);
   assert writes==0&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").equals(legacySign)&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanSign").equals(legacySign+" ") : "sign migration overwrote conflicting receipt";
   reset();autoReward=true;lottery(false,true,false,5);assert writes==1&&Log.lines.stream().anyMatch(s->s.contains("本轮新完成并领奖=1项"))&&Log.lines.stream().anyMatch(s->s.startsWith("✅ 神券团购任务[浏览活动页面]：本轮完成并领奖"));
   reset();drawPrizes=new JSONArray().put(new JSONObject().put("prizeDisplayInfo",new JSONObject().put("PRIZE_DISPLAY_NAME","神券红包"))).put(new JSONObject().put("prizeDisplayInfo",new JSONObject().put("PRIZE_SHORT_TITLE","浏览券")));
   lottery(false,false,true,1);assert Log.lines.stream().anyMatch(s->s.contains("第1抽得[神券红包,浏览券]"));
   reset();drawPrizes=new JSONArray().put("wrong").put(new JSONObject().put("prizeName",new JSONObject())).put(new JSONObject().put("prizeDisplayInfo",new JSONArray()));
   lottery(false,false,true,1);assert Log.lines.stream().anyMatch(s->s.contains("第1抽得[未提供奖励名称]")) : "malformed prize metadata was guessed";drawPrizes=null;
   reset();stallTrigger=extraTask=true;lottery(false,true,true,10);assert writes==4&&draws==2&&Log.confirmed==2&&pending("shenQuan")&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").isEmpty() : "one unconfirmed task blocked other tasks and existing free draws";
   assert RuntimeInfo.data.containsKey("sjActivityReceipt::shenQuanTask::play\"\\")&&RuntimeInfo.data.containsKey("sjActivityReceipt::shenQuanTask::other");
   lottery(false,true,true,10);assert writes==4 : "unconfirmed per-task requests were resent";
   reset();String legacyTask=new JSONObject().put("uid","self").put("day",20261008).put("action","reward:play\"\\").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",legacyTask);lottery(false,true,true,10);
   assert writes==2&&draws==2&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanTask::play\"\\").equals(legacyTask)&&pending("shenQuan") : "legacy same-day task receipt still blocks free draws or was discarded";
    reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",legacyTask);RuntimeInfo.writable=false;lottery(false,true,true,10);assert writes==0&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").equals(legacyTask) : "failed migration erased the legacy receipt";
    reset();String conflicting=legacyTask+" ";RuntimeInfo.data.put("sjActivityReceipt::shenQuan",legacyTask);RuntimeInfo.data.put("sjActivityReceipt::shenQuanTask::play\"\\",conflicting);lottery(false,true,true,10);
    assert writes==0&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").equals(legacyTask)&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanTask::play\"\\").equals(conflicting) : "receipt migration overwrote a different existing record";
    reset();String foreignTask=new JSONObject().put("uid","other").put("day",20261008).put("action","reward:play\"\\").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuanTask::play\"\\",foreignTask);lottery(false,true,true,10);
    assert writes==2&&draws==2&&RuntimeInfo.instance.getString("sjActivityReceipt::shenQuanTask::play\"\\").equals(foreignTask) : "foreign per-task receipt was used or blocked unrelated draws";
    reset();switchWrite=true;try{lottery(false,true,true,10);throw new AssertionError("task account switch swallowed");}catch(TaskCancelledException expected){}assert writes==1&&draws==0&&pending("shenQuan");
    reset();cancelWrite=true;try{lottery(false,true,true,10);throw new AssertionError("task cancellation swallowed");}catch(TaskCancelledException expected){}assert writes==1&&draws==0&&pending("shenQuan");
  reset();OtherRequestGate full=new OtherRequestGate();for(int i=0;i<78;i++)full.call("fill",()->"");
  SjActivityTasks stopped=new SjActivityTasks(full,5); // A prior activity can exhaust this shared gate before a write.
  try{stopped.shenQuan(true,false,false,"{}");throw new AssertionError("gate limit missing");}catch(OtherRequestGate.BudgetExhausted expected){}
  assert writes==0&&!pending("shenQuan")&&RuntimeInfo.instance.getString("sjActivityAttempts").isEmpty() : "unsent request left a blocking receipt or charged quota";
  assert Log.lines.stream().anyMatch(s->s.contains("未调用RPC")&&s.contains("BudgetExhausted")) : "gate rejection was not diagnosed as unsent";
  reset();signed=true;RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20261008).put("action","sign").toString());
  lottery(true,false,false,1);assert queries>0&&writes==0&&!pending("shenQuan") : "legacy sign receipt skipped server reconciliation";
  assert Log.lines.stream().anyMatch(s->s.contains("查询上次签到"))&&Log.lines.stream().anyMatch(s->s.contains("已签到"));
  reset();transportError=true;try{lottery(true,false,false,1);throw new AssertionError("network exception swallowed");}catch(java.io.IOException expected){assert !expected.getMessage().contains("SECRET_")&&expected.getCause()==null;}
  assert writes==1&&pending("shenQuan")&&Log.lines.stream().anyMatch(s->s.contains("已进入RPC")&&s.contains("IOException"));
  assert Log.lines.stream().noneMatch(s->s.contains("SECRET_CODE")||s.contains("SECRET_TOKEN"));transportError=false;signed=true;lottery(true,false,false,1);assert writes==1&&!pending("shenQuan");
   reset();badAck=true;lottery(true,false,false,1);assert writes==1&&!pending("shenQuan")&&Log.confirmed==1 : "authoritative signed state still waits for the next run after a failed acknowledgement";badAck=false;
  assert Log.lines.stream().anyMatch(s->s.contains("success=false"))&&Log.lines.stream().noneMatch(s->s.contains("SECRET_CODE")||s.contains("SECRET_TOKEN"));
  lottery(true,false,false,1);assert writes==1&&!pending("shenQuan") : "confirmed sign never recovered after lost acknowledgement";
  reset();badAck=true;lottery(false,true,false,1);assert writes==1&&pending("shenQuan");badAck=false;
  lottery(false,true,false,1);assert writes==1&&!pending("shenQuan")&&taskState.equals("TO_RECEIVE") : "task recovery repeated a write or lost quota";
  reset();taskState="TO_RECEIVE";badAck=true;lottery(false,true,false,1);assert writes==1&&pending("shenQuan");badAck=false;
  lottery(false,true,false,1);assert writes==1&&!pending("shenQuan") : "reward receipt did not recover";
  reset();badAck=true;lottery(false,false,true,1);assert writes==1&&pending("shenQuan");badAck=false;
  lottery(false,false,true,1);assert writes==1&&draws==1&&!pending("shenQuan") : "draw recovery repeated a draw";
  reset();advance=false;lottery(true,false,false,1);int beforeQueries=queries;lottery(true,false,false,1);
  assert queries>beforeQueries&&writes==1&&pending("shenQuan") : "unresolved sign was not queried or was repeated";
  assert Log.lines.stream().anyMatch(s->s.contains("未签到"))&&Log.lines.stream().anyMatch(s->s.contains("仍未确认"));
  reset();badAck=true;lottery(false,true,false,1);badAck=false;title="浏览另一个活动";lottery(false,true,false,5);assert writes==1&&pending("shenQuan");
  assert Log.lines.stream().anyMatch(s->s.contains("与提交时资料不一致"));
  reset();badAck=true;lottery(false,false,true,1);badAck=false;remaining++;lottery(false,false,true,5);assert writes==1&&pending("shenQuan");
   reset();advance=false;badAck=true;lottery(true,false,false,1);advance=true;badAck=false;missing=true;lottery(true,false,false,5);assert writes==1&&pending("shenQuan");
  assert Log.lines.stream().anyMatch(s->s.contains("data对象=false"));
   reset();advance=false;badAck=true;lottery(true,false,false,1);signed=true;badAck=false;RuntimeInfo.writable=false;lottery(true,false,false,5);assert writes==1&&pending("shenQuan");
  assert Log.lines.stream().anyMatch(s->s.contains("清除待核对记录失败"));RuntimeInfo.writable=true;lottery(true,false,false,5);assert writes==1&&!pending("shenQuan");
   reset();String old=new JSONObject().put("uid","self").put("day",20261007).put("action","sign").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",old);lottery(true,false,false,1);assert queries>0&&writes==1&&!pending("shenQuan");
   assert RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan::history::20261007").equals(old)&&Log.lines.stream().anyMatch(s->s.contains("历史结果未计作成功"));
   reset();taskState="TO_RECEIVE";old=new JSONObject().put("uid","self").put("day",20261007).put("action","reward:play\"\\").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",old);lottery(true,true,true,5);assert writes==3&&draws==2&&!pending("shenQuan")&&taskState.equals("TO_RECEIVE") : "old reward blocked sign/draw or was sent again";
   lottery(false,true,false,5);assert writes==3 : "old unresolved play was repeated on the next run";
   reset();taskState="RECEIVED";RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20261008).put("action","reward:play\"\\").put("context",new JSONObject().put("contract",SjActivityTasks.lotteryContract(lotteryRow())).put("remaining",99)).toString());lottery(false,true,false,1);assert writes==0&&!pending("shenQuan") : "terminal reward still required an unrelated balance increase";
   reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20261007).put("action","draw:9").toString());lottery(false,false,true,1);assert draws==1&&consumed==1&&!pending("shenQuan") : "old daily draw count was compared against today's reset";
   reset();old=new JSONObject().put("uid","self").put("day",20261007).put("action","sign").toString();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",old);RuntimeInfo.writable=false;lottery(true,false,false,1);assert writes==0&&pending("shenQuan");
   reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20261009).put("action","sign").toString());lottery(true,false,false,1);assert queries+writes==0&&pending("shenQuan");
   for(String invalid:new String[]{"reward:","trigger:","draw:no","draw:-1"}){reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20261007).put("action",invalid).toString());lottery(true,false,false,1);assert queries+writes==0&&pending("shenQuan");}
   reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","self").put("day",20260230).put("action","sign").toString());lottery(true,false,false,1);assert queries+writes==0&&pending("shenQuan");
  reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan","broken");lottery(true,true,true,5);assert queries+writes==0&&pending("shenQuan");
  reset();RuntimeInfo.data.put("sjActivityReceipt::shenQuan",new JSONObject().put("uid","other").put("day",20261008).put("action","sign").toString());lottery(true,true,true,5);assert queries+writes==0&&pending("shenQuan");
  reset();lottery(true,true,true,5);assert writes==5&&draws==2&&Log.confirmed==5&&TimeUtil.waited==3&&!pending("shenQuan");
  reset();lottery(true,true,true,0);assert queries+writes==0;
  reset();lottery(false,false,false,5);assert queries+writes==0;
  reset();title="浏览开通余额宝";lottery(false,true,false,5);assert writes==0;
  reset();browseSeconds=0;lottery(false,true,false,5);assert writes==2&&TimeUtil.waited==4;
  reset();browseSeconds=null;lottery(false,true,false,5);assert writes==2&&TimeUtil.waited==4;
  reset();title="浏览支付宝活动";lottery(false,true,false,5);assert writes==2&&!pending("shenQuan");
  reset();title="浏览支付活动";type="PAY";lottery(false,true,false,5);assert writes==0;
  reset();title="浏览购买商品";lottery(false,true,false,5);assert writes==0;
  reset();title="浏览活动页面";type="";lottery(false,true,false,5);assert writes==2;
  reset();type="SUBSCRIBE";lottery(false,true,false,5);assert writes==0;
  reset();duplicate=true;lottery(false,true,false,5);assert writes==0;
  reset();missing=true;lottery(true,true,true,5);assert writes==0;
  reset();RuntimeInfo.writable=false;lottery(true,true,true,5);assert writes==0;
  reset();RuntimeInfo.data.put("sjActivityAttempts","broken");lottery(true,true,true,5);assert writes==0;
  reset();advance=false;lottery(false,true,false,5);assert writes==1&&pending("shenQuan")&&Log.confirmed==0;MyUtils.day++;lottery(false,true,false,5);assert writes==1;
   reset();signError=true;lottery(true,true,true,5);assert writes==5&&draws==2&&!pending("shenQuan")&&Log.confirmed==5 : "error=3000 plus confirmed sign still blocks tasks and draws";
   assert Log.lines.stream().anyMatch(s->s.contains("error=3000"))&&Log.lines.stream().anyMatch(s->s.contains("提交响应未通过")&&s.contains("回查"));
   reset();advance=false;signError=true;lottery(true,false,false,5);assert writes==1&&pending("shenQuan")&&Log.confirmed==0;
   reset();signError=missingAfterWrite=true;lottery(true,true,true,5);assert writes==1&&pending("shenQuan")&&Log.confirmed==0 : "invalid immediate readback cleared the sign receipt";
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
guard = (SOURCE / 'rpc/intervallimit/RpcRequestGuard.java').read_text(encoding='utf-8')
code = code.replace('@@FAILURE@@', guard[guard.index('public static boolean isFailure('):guard.index('    public static boolean isNonFriend(')])
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-sj-activities-') as tmp:
    java = Path(tmp) / 'SjActivityCheck.java'
    java.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(java),
                    str(SOURCE / 'util/TaskCancelledException.java'), str(SOURCE / 'rpc/intervallimit/RequestBudgetPolicy.java'),
                    str(SOURCE / 'rpc/intervallimit/RpcFailurePolicy.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), 'SjActivityCheck'], check=True)
