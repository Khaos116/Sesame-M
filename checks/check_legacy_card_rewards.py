"""Run the legacy card worker itself against scoped, current-account fake RPCs."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
SOURCE=Path(__file__).resolve().parents[1]/'app/src/main/java/io/github/aw1y2z/sesame'
worker=SOURCE/'model/task/other/LegacyCardRewards.java'
assert worker.exists(), 'Legacy card reward worker has not been implemented'
code=r'''
import org.json.*;import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
public class LegacyCardRewardsCheck {
 static class MyUtils{static int day=7;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day);return c;}static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Status { static Set<String> flags=new HashSet<>(); static String key(String k){return UserIdMap.uid+MyUtils.day+k;} static boolean hasFlagToday(String k){return flags.contains(key(k));} static void flagToday(String k){flags.add(key(k));} static void clearFlag(String k){flags.remove(key(k));}}
 static class UserIdMap{static String uid="self";static String getCurrentUid(){return uid;}}
 static class System{static long nanos;static final java.io.PrintStream out=java.lang.System.out;static long nanoTime(){return nanos;}static long currentTimeMillis(){return java.lang.System.currentTimeMillis();}}
 static class TimeUtil{static boolean cancelled,cancelWait,switchWait,dayWait;static long waited;static void sleep(long n){if(cancelled)throw new TaskCancelledException();System.nanos+=n*1000000L;if(n==1000||n==600){if(n==1000)waited+=n;if(cancelWait)throw new TaskCancelledException();if(switchWait)UserIdMap.uid="other";if(dayWait)MyUtils.day++;}}}
 static class RuntimeInfo{static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String k){return data.getOrDefault(k,"");}long getLong(String k,long d){try{return Long.parseLong(getString(k));}catch(Exception e){return d;}}void put(String k,Object v){data.put(k,String.valueOf(v));}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Log{static int ok;static List<String> records=new ArrayList<>();static void other(String s){ok++;}static void record(String s){records.add(s);}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard{static boolean isFailure(JSONObject o){return Boolean.FALSE.equals(o.opt("success"))||o.has("errorCode");}}
 @@GATE@@
 @@WORKER@@
 static JSONArray flowerRows;static int flowerQueries;static String failStage;static List<String> flowerStages=new ArrayList<>();static int writeDay=7,writesBeforeDay,signups;static String signupResult="NOT_DONE",signupMode="";static int requests,sends,receives,flips,merges,remaining,listQueries,driftAt,directoryRows;static long requiredWait;static String signStatus,taskStatus,driftKey,sendResult,receiveResult;static Object driftValue;static JSONObject selectedRow;static boolean opened,fragments,change,badAck,switchUid,cancelAfter,duplicate,missing,wrongCount,wrongCard,risk,duplicateTask,sendStateBad,fallbackStatus;
 static JSONObject success(JSONObject data){return new JSONObject().put("success",true).put("data",data);}
 static JSONObject signRow(){return new JSONObject().put("taskId","sign\"\\").put("status",signStatus).put("hasToday",true);}
 static JSONObject taskRow(){JSONObject row=selectedRow==null?new JSONObject().put("taskId","reward\"\\").put("taskCenId","center").put("taskTitle","浏览活动页").put("taskExtProps",new JSONObject().put("browseTime",20)).put("taskMaterial",new JSONObject().put("browseTime",30).put("taskType","COMMON_EVENT_TASK")):new JSONObject(selectedRow.toString());row.put(fallbackStatus?"taskProcessStatus":"taskStatus",taskStatus);if(listQueries==driftAt)row.put(driftKey,driftValue);return row;}
 static JSONArray chars(){return new JSONArray().put(new JSONObject().put("number",fragments?1:0)).put(new JSONObject().put("number",fragments?1:0));}
 static class ApplicationHook{static boolean write(String method){return method.equals("alipay.promoprod.applet.trigger")||method.endsWith("hundredtimesdiscountcard.task.receive")||method.endsWith("index.flopcard")||method.endsWith("index.merge")||method.equals("com.alipay.pcreditbfweb.sdk.task.trigger")||method.equals("com.alipay.pcreditbfweb.sdk.task.award");}static String requestString(String method,String raw){assert !write(method):"write used default retries "+method;return requestRaw(method,raw);}static String requestString(String method,String raw,int tries,int pause){assert write(method)&&tries==1&&pause==0:method;return requestRaw(method,raw);}static String requestRaw(String method,String raw){requests++;JSONObject a=new JSONArray(raw).optJSONObject(0),d=new JSONObject();boolean mutation=false;
  if(method.endsWith("signintask.query")){JSONArray rows=new JSONArray().put(signRow());if(duplicate)rows.put(signRow());d.put("signInTaskInfo",new JSONObject().put("taskCenterId","center").put("taskDetailList",rows));}
  else if(method.endsWith("task.listquery")){listQueries++;JSONArray rows=new JSONArray().put(taskRow());for(int i=1;i<directoryRows;i++)rows.put(taskRow().put("taskId","candidate"+i));if(duplicateTask)rows.put(taskRow());d.put("taskList",rows);}
  else if(method.equals("alipay.promoprod.applet.trigger")&&a.optString("stageCode").equals("signup")){assert a.length()==3&&a.optString("taskCenId").equals("center")&&a.optString("appletId").equals("reward\"\\")&&TimeUtil.waited==0;signups++;mutation=true;if(change){taskStatus=signupResult;selectedRow.optJSONObject("taskExtProps").put("needSignUp",false);if(signupMode.equals("flagRetained"))selectedRow.optJSONObject("taskExtProps").put("needSignUp",true);if(signupMode.equals("contextDrift"))selectedRow.put("taskTitle","different");}}
  else if(method.equals("alipay.promoprod.applet.trigger")){assert a.optString("taskCenId").equals("center")&&a.optString("stageCode").equals("send")&&a.length()==3;sends++;mutation=true;if(a.optString("appletId").equals("sign\"\\")){if(change)signStatus="TO_RECEIVE";}else{assert a.optString("appletId").equals("reward\"\\");assert TimeUtil.waited>=requiredWait;assert RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");if(change)taskStatus=sendStateBad?"HAS_COMPLETED":sendResult;}}
  else if(method.endsWith("hundredtimesdiscountcard.task.receive")){receives++;mutation=true;if(change){if(a.optString("taskId").equals("sign\"\\")){assert a.optString("chInfo").equals("signInTask");signStatus="RECEIVED";}else {assert a.optString("taskId").equals("reward\"\\")&&a.length()==1;taskStatus=receiveResult;}}}
  else if(method.equals("com.alipay.pcreditbfweb.sdk.task.trigger")){String stage=a.optString("stageCode");assert a.length()==5&&a.optString("appletId").equals("flower\"\\")&&a.optString("taskCenId").equals("flower-center")&&Boolean.TRUE.equals(a.opt("retryFlag"))&&a.optString("outBizNo").equals("flower\"\\"+System.currentTimeMillis()/60000L);assert stage.equals("signup")||stage.equals("send");flowerStages.add(stage);if(stage.equals("signup"))signups++;else sends++;mutation=true;}
  else if(method.equals("com.alipay.pcreditbfweb.sdk.task.award")){assert a.length()==2&&a.optJSONArray("taskIds").length()==1&&a.optJSONArray("taskCenIds").length()==1&&a.optJSONArray("taskIds").optString(0).equals("flower\"\\")&&a.optJSONArray("taskCenIds").optString(0).equals("flower-center");flowerStages.add("award");receives++;mutation=true;d.put("resultData",new JSONArray().put(new JSONObject().put("prizeSendOrderList",new JSONArray().put(new JSONObject().put("prizeName","free card")))));}
  else if(method.endsWith("sdk.task.queryV2")){if(flowerRows!=null){assert a.length()==2&&a.optString("requestFrom").equals("pccp")&&a.optString("scene").equals("HUA_HUA_CARD");flowerQueries++;JSONArray live=new JSONArray(flowerRows.toString());if(flowerQueries==driftAt)live.optJSONObject(0).put(driftKey,driftValue);return new JSONObject().put("success",!risk).put("data",live).toString();}return new JSONObject().put("success",true).put("data",new JSONArray().put(new JSONObject().put("taskBaseInfo",new JSONObject().put("prizeInfos",new JSONArray().put(new JSONObject().put("extInfo",new JSONObject().put("CERT_TEMPLATE_ID","CERT"))))))).toString();}
  else if(method.equals("com.alipay.pcreditbfweb.promo.hhk.index")){assert a.optString("certId").equals("CERT");d.put("remainingTimes",wrongCount?remaining+1:remaining).put("fragments",chars()).put("cardPrizes",new JSONArray().put(new JSONObject().put("isOpen",opened).put("isNewPageBegin",true).put("position",new JSONObject().put("index",0))));if(missing)d.remove("remainingTimes");}
  else if(method.endsWith("index.flopcard")){assert a.optInt("lineIndex",-1)==0&&a.optBoolean("isNewPageBegin");assert a.optString("productCode").equals("CARD_HUA_HUA_CARD_23Y06");flips++;mutation=true;if(change){remaining--;opened=!wrongCard;}}
  else if(method.endsWith("index.merge")){merges++;mutation=true;if(change)fragments=false;d.put("campId","CAMP").put("bizNo","BIZ");}
  else if(method.endsWith("pageQueryPrizeSendOrderLite")){JSONObject q=a.optJSONObject("args");assert q.optString("outBizNo").equals("BIZ")&&q.optJSONArray("campIds").optString(0).equals("CAMP");d.put("result",new JSONObject().put("resultData",new JSONObject().put("dataList",new JSONArray().put(new JSONObject().put("prizeName","free")))));}
  else throw new AssertionError(method);
  if(mutation){int writes=signups+sends+receives+flips+merges;if(writeDay!=MyUtils.day){writeDay=MyUtils.day;writesBeforeDay=writes-1;}assert RuntimeInfo.data.keySet().stream().anyMatch(k->k.endsWith("Receipt"));assert new JSONObject(RuntimeInfo.data.get("legacyCardAttempts")).optInt("count")==writes-writesBeforeDay;if(switchUid)UserIdMap.uid="other";if(cancelAfter)TimeUtil.cancelled=true;}
  JSONObject root=success(d);if(mutation&&(badAck||!flowerStages.isEmpty()&&flowerStages.get(flowerStages.size()-1).equals(failStage)))root.put("success",false);if(risk)root.put("errorCode","1009");return root.toString();}}
 static void reset(){Status.flags.clear();flowerRows=null;flowerQueries=0;failStage="";flowerStages.clear();writeDay=7;writesBeforeDay=0;signups=0;signupResult="NOT_DONE";signupMode="";RuntimeInfo.data.clear();RuntimeInfo.writable=true;UserIdMap.uid="self";MyUtils.day=7;TimeUtil.cancelled=TimeUtil.cancelWait=TimeUtil.switchWait=TimeUtil.dayWait=false;System.nanos=TimeUtil.waited=0;requiredWait=20_000;selectedRow=null;driftKey=null;driftValue=null;requests=sends=receives=flips=merges=Log.ok=listQueries=driftAt=0;Log.records.clear();directoryRows=remaining=1;signStatus="NOT_DONE";taskStatus=sendResult="TO_RECEIVE";receiveResult="RECEIVED";opened=fragments=badAck=switchUid=cancelAfter=duplicate=missing=wrongCount=wrongCard=risk=duplicateTask=sendStateBad=fallbackStatus=false;change=true;}
 static void run(boolean sign,boolean rewards,boolean flip,boolean merge,int budget){LegacyCardRewards.run(new OtherRequestGate(),sign,rewards,flip,merge,budget,false,"[]",false);}
 static String target(){return new JSONArray().put(new JSONObject().put("taskId","reward\"\\").put("taskCenId","center")).toString();}
 static void select(){reset();taskStatus="NOT_DONE";selectedRow=new JSONObject().put("taskId","reward\"\\").put("taskCenId","center").put("taskTitle","活动介绍").put("taskExtProps",new JSONObject().put("browseTime",20));}
 static void selected(String raw,int budget){LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,budget,true,raw,false);}
 static void enroll(){select();taskStatus="NONE_SIGNUP";selectedRow.optJSONObject("taskExtProps").put("needSignUp",true).put("signupType","NO_AUTO");}
 static void signup(int budget){LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,budget,true,target(),true);}
 static void flower(){reset();flowerRows=new JSONArray().put(new JSONObject().put("taskId","flower\"\\").put("taskCenId","flower-center").put("taskStatus","NONE_SIGNUP").put("taskSource","TASK_CENTER").put("taskShowInfo",new JSONObject().put("title","活动介绍")));}
 static void flowerRun(int budget){LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,budget,false,"[]",false,true);}
 static JSONObject flowerReceipt(){return new JSONObject(RuntimeInfo.data.get("legacyCard::huahuaTask::"+new JSONArray().put("flower-center").put("flower\"\\")+"Receipt"));}
 static void automatic(int budget){LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,budget,false,"[]",false,false,true);}
 public static void main(String[] args){
  select();automatic(0);assert requests==0;
  select();automatic(2);assert sends==1&&receives==1&&Log.ok==1&&TimeUtil.waited==20_000;automatic(20);assert sends==1&&receives==1;
  enroll();signupMode="flagRetained";automatic(3);assert signups==1&&sends==1&&receives==1&&Log.ok==1&&TimeUtil.waited==20_000:"positive source ACK continues despite static signup flag";
  select();selectedRow.optJSONObject("taskExtProps").put("signupType","NO_AUTO");automatic(3);assert signups==1&&sends==1&&receives==1&&Log.ok==1:"static NO_AUTO is a source signup request, not an unavailable protocol";
  select();selectedRow.optJSONObject("taskExtProps").put("signupType","NO_AUTO");change=false;automatic(3);assert signups==1&&sends==1&&receives==0&&Log.ok==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");MyUtils.day++;automatic(20);assert signups==1&&sends==1;
  enroll();signupMode="flagRetained";automatic(2);assert signups==1&&sends==1&&receives==0;automatic(3);assert signups==1&&sends==1&&receives==1&&Log.ok==1;
  for(String denied:new String[]{"COMMON_EVENT_TASK","TRANSFORMER","PAYMENT_TASK"}){select();selectedRow.put("taskType",denied);automatic(3);assert signups+sends+receives==0:denied;}
  select();selectedRow.put("taskTitle","购买权益");automatic(3);assert signups+sends+receives==0;
  for(Object duration:new Object[]{0,301,1.5,JSONObject.NULL}){select();selectedRow.optJSONObject("taskExtProps").put("browseTime",duration);automatic(3);assert signups+sends+receives==0:duration;}
  enroll();badAck=true;automatic(3);assert signups==1&&sends+receives==0;automatic(20);assert signups==1;

  flower();flowerRun(0);assert requests==0;
  flower();flowerRun(3);assert flowerStages.equals(List.of("signup","send","award"))&&Log.ok==0&&TimeUtil.waited==0;assert Boolean.TRUE.equals(flowerReceipt().opt("accepted"))&&flowerReceipt().optJSONArray("reportedPrizes").optString(0).equals("free card");flowerRun(20);MyUtils.day++;flowerRun(20);assert flowerStages.size()==3:"accepted receipt is not replay authority";
  flower();flowerRows.optJSONObject(0).put("taskStatus","SIGNUP_COMPLETE");flowerRun(2);assert flowerStages.equals(List.of("send","award"))&&Log.ok==0;
  for(int budget:new int[]{1,2}){flower();flowerRun(budget);assert flowerStages.size()==budget&&!Boolean.TRUE.equals(flowerReceipt().opt("accepted"));MyUtils.day++;flowerRun(20);assert flowerStages.size()==budget:"partial source flow is never replayed";}
  for(String stage:new String[]{"signup","send","award"}){flower();failStage=stage;flowerRun(3);int n=List.of("signup","send","award").indexOf(stage)+1;assert flowerStages.size()==n&&!Boolean.TRUE.equals(flowerReceipt().opt("accepted"))&&Log.ok==0;flowerRun(20);assert flowerStages.size()==n;}
  for(String key:new String[]{"taskId","taskCenId","taskStatus","taskShowInfo","taskSource","userId"}){flower();flowerRows.optJSONObject(0).put(key,key.equals("taskSource")?123:JSONObject.NULL);flowerRun(3);assert flowerStages.isEmpty():key;}
  flower();flowerRows.optJSONObject(0).put("taskSource","SCENE_TASK");flowerRun(3);assert flowerStages.isEmpty();
  for(String title:new String[]{"支付订单","开通花呗","PAYMENT_TASK","邀请好友"}){flower();flowerRows.optJSONObject(0).optJSONObject("taskShowInfo").put("title",title);flowerRun(3);assert flowerStages.isEmpty():title;}
  flower();flowerRows.put(new JSONObject(flowerRows.optJSONObject(0).toString()));flowerRun(3);assert flowerStages.isEmpty();
  flower();driftAt=2;driftKey="taskCenId";driftValue="changed";flowerRun(3);assert flowerStages.isEmpty();
  flower();risk=true;flowerRun(3);assert flowerStages.isEmpty();
  flower();RuntimeInfo.writable=false;flowerRun(3);assert flowerStages.isEmpty();
  for(int boundary=0;boundary<5;boundary++){flower();if(boundary==0)cancelAfter=true;if(boundary==1)switchUid=true;if(boundary==2)TimeUtil.cancelWait=true;if(boundary==3)TimeUtil.switchWait=true;if(boundary==4)TimeUtil.dayWait=true;try{flowerRun(3);throw new AssertionError("flower boundary swallowed "+boundary);}catch(TaskCancelledException expected){}assert flowerStages.size()==(boundary<2?1:2)&&Log.ok==0;}

  for(String state:new String[]{"TO_RECEIVE","WAIT_RECEIVE","WAIT_AWARD"})for(boolean nextDay:new boolean[]{false,true}){select();writeDay=7;writesBeforeDay=0;sendResult=state;selected(target(),1);assert sends==1&&receives==0&&TimeUtil.waited==20_000&&!RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");if(nextDay)MyUtils.day++;selected(target(),nextDay?1:2);assert sends==1&&receives==1&&Log.ok==1&&TimeUtil.waited==20_000:state+nextDay;selected(target(),2);assert sends==1&&receives==1;}
  writeDay=7;writesBeforeDay=0;
  enroll();signup(2);signup(3);assert signups==1&&sends==1&&receives==1&&TimeUtil.waited==20_000:"selected switch resumes pending reward without enabling all rewards";
  for(String state:new String[]{"TO_RECEIVE","WAIT_RECEIVE","WAIT_AWARD"}){select();taskStatus=state;selected(target(),1);assert sends==0&&receives==1&&Log.ok==1&&TimeUtil.waited==0:state;}
  select();taskStatus="WAIT_AWARD";selectedRow.optJSONObject("taskExtProps").put("signupType","NO_AUTO").put("needSignUp",false);selected(target(),1);assert signups+sends==0&&receives==1&&TimeUtil.waited==0;
  for(String key:new String[]{"taskCenId","taskTitle","userId","taskExtProps"})for(int at:new int[]{2,3}){select();taskStatus="WAIT_RECEIVE";driftAt=at;driftKey=key;driftValue=key.equals("taskExtProps")?new JSONObject().put("browseTime",25):"changed";selected(target(),1);assert sends==0&&receives==(at==3?1:0)&&Log.ok==0&&TimeUtil.waited==0:at+key;if(at==3)assert RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");}
  select();taskStatus="TO_RECEIVE";selectedRow.optJSONObject("taskExtProps").put("needSignUp",true);selected(target(),1);assert signups+sends+receives==0;
  select();taskStatus="TO_RECEIVE";selectedRow.put("taskTitle","购买商品");selected(target(),1);assert sends+receives==0;
  select();taskStatus="WAIT_AWARD";badAck=true;selected(target(),1);assert receives==1&&Log.ok==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");selected(target(),2);assert receives==1;
  select();taskStatus="WAIT_RECEIVE";cancelAfter=true;try{selected(target(),1);throw new AssertionError();}catch(TaskCancelledException expected){}assert sends==0&&receives==1&&Log.ok==0;
  for(String status:new String[]{"NONE_SIGNUP","NOT_DONE","TODO"})for(Object flag:new Object[]{true,"true"}){enroll();taskStatus=status;selectedRow.optJSONObject("taskExtProps").put("needSignUp",flag);signup(3);assert signups==1&&sends==1&&receives==1&&Log.ok==1&&TimeUtil.waited==20_000:status+flag;assert !RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");signup(3);assert signups==1&&sends==1&&receives==1;}
  enroll();selectedRow.optJSONObject("taskExtProps").remove("needSignUp");signup(3);assert signups==1&&sends==1&&receives==1;
  enroll();fallbackStatus=true;signupResult="TODO";signup(3);assert signups==1&&sends==1&&receives==1;
  enroll();signup(1);assert signups==1&&sends==0&&TimeUtil.waited==0&&!RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");signup(3);assert signups==1&&sends==1&&receives==1&&Log.ok==1;
  enroll();signup(1);MyUtils.day++;signup(3);assert signups==1&&sends==0&&receives==0&&TimeUtil.waited==0;
  enroll();signup(2);assert signups==1&&sends==1&&receives==0&&Log.ok==0;run(false,true,false,false,3);assert signups==1&&sends==1&&receives==1&&Log.ok==1;
  enroll();signup(0);assert signups+sends+receives==0&&RuntimeInfo.data.isEmpty()&&Log.records.size()==1&&Log.records.get(0).contains("needsSignup");
  enroll();LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,3,true,"[]",true);assert signups+sends+receives==0&&RuntimeInfo.data.isEmpty()&&Log.records.size()==1;
  enroll();selected(target(),3);assert signups+sends+receives==0&&TimeUtil.waited==0;
  for(String state:new String[]{"NONE_SIGNUP","SIGNUP_COMPLETE","TO_RECEIVE","RECEIVED","UNKNOWN"}){enroll();signupResult=state;signup(3);assert signups==1&&sends+receives==0&&Log.ok==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt"):state;MyUtils.day++;signup(3);assert signups==1:state;}
  for(String mode:new String[]{"flagRetained","contextDrift"}){enroll();signupMode=mode;signup(3);assert signups==1&&sends+receives==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt"):mode;}
  for(int at:new int[]{2,3,4})for(String key:new String[]{"taskId","taskCenId","taskTitle","userId"}){enroll();driftAt=at;driftKey=key;driftValue="changed";signup(3);assert signups==(at==2?0:1)&&sends+receives==0&&Log.ok==0:at+key;if(at==3)assert RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");}
  for(Object flag:new Object[]{1,"unknown",JSONObject.NULL}){enroll();selectedRow.optJSONObject("taskExtProps").put("needSignUp",flag);signup(3);assert signups+sends+receives==0:flag;}
  for(Object seconds:new Object[]{0,301,1.5,"unknown"}){enroll();selectedRow.optJSONObject("taskExtProps").put("browseTime",seconds);signup(3);assert signups+sends+receives==0:seconds;}
  enroll();selectedRow.put("taskTitle","PAYMENT_TASK");signup(3);assert signups+sends+receives==0;
  enroll();selectedRow.put("taskType","COMMON_EVENT_TASK");signup(3);assert signups+sends+receives==0;
  enroll();selectedRow.put("userId","other");signup(3);assert signups+sends+receives==0;
  enroll();taskStatus="NOT_DONE";selectedRow.optJSONObject("taskExtProps").put("needSignUp",false);signup(3);assert signups+sends+receives==0:"NO_AUTO alone has no independently verifiable transition";
  enroll();RuntimeInfo.writable=false;signup(3);assert signups+sends+receives==0;
  enroll();badAck=true;signup(3);assert signups==1&&sends+receives==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");
  enroll();change=false;signup(3);assert signups==1&&sends+receives==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");MyUtils.day++;signup(3);assert signups==1;
  for(int boundary=0;boundary<5;boundary++){enroll();if(boundary==0)TimeUtil.cancelWait=true;if(boundary==1)TimeUtil.switchWait=true;if(boundary==2)TimeUtil.dayWait=true;if(boundary==3)cancelAfter=true;if(boundary==4)switchUid=true;try{signup(3);throw new AssertionError("signup boundary swallowed "+boundary);}catch(TaskCancelledException expected){}assert signups==1&&sends+receives==0&&Log.ok==0:boundary;}
  enroll();TimeUtil.cancelWait=true;try{signup(3);throw new AssertionError();}catch(TaskCancelledException expected){}TimeUtil.cancelWait=false;signup(3);assert signups==1&&sends==1&&receives==1:"same-day confirmed registration resumes without another signup";

  select();selected(target(),2);assert sends==1&&receives==1&&Log.ok==1&&TimeUtil.waited==20_000:sends+"/"+receives+"/"+Log.ok+"/"+TimeUtil.waited+" queries="+listQueries;assert !RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");
  selected(target(),2);assert sends==1&&receives==1&&TimeUtil.waited==20_000;
  select();taskStatus="TODO";selectedRow.optJSONObject("taskExtProps").put("browseTime",45);selected(target(),2);assert sends==1&&receives==1&&TimeUtil.waited==45_000;
  select();selectedRow.optJSONObject("taskExtProps").remove("browseTime");selectedRow.put("taskMaterial",new JSONObject().put("browseTime","30"));selected(target(),2);assert sends==1&&receives==1&&TimeUtil.waited==30_000;
  for(int seconds:new int[]{1,300}){select();requiredWait=seconds*1000L;selectedRow.optJSONObject("taskExtProps").put("browseTime",seconds);selected(target(),2);assert sends==1&&receives==1&&TimeUtil.waited==requiredWait:seconds;}
  select();fallbackStatus=true;selected(target(),2);assert sends==1&&receives==1;
  for(String pending:new String[]{"TO_RECEIVE","WAIT_RECEIVE","WAIT_AWARD"}){select();sendResult=pending;selected(target(),2);assert sends==1&&receives==1&&Log.ok==1:pending;}
  for(String unknown:new String[]{"WAIT_AWARD","HAS_COMPLETED","UNKNOWN"}){select();receiveResult=unknown;selected(target(),2);assert sends==1&&receives==1&&Log.ok==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt"):unknown;int before=requests;String receipt=RuntimeInfo.data.get("legacyCard::hundredReceipt");MyUtils.day+=2;selected("[]",20);assert sends==1&&receives==1&&requests==before+1&&RuntimeInfo.data.get("legacyCard::hundredReceipt").equals(receipt):unknown;}
  select();LegacyCardRewards.run(new OtherRequestGate(),false,false,false,false,2,false,target(),false);assert requests==0;
  for(String invalid:new String[]{"", "[]", "broken", "{}", "[{}]", "[null]", target()+"[]",new JSONArray().put(new JSONObject().put("taskId",1).put("taskCenId","center")).toString(),new JSONArray().put(new JSONObject().put("taskId","other").put("taskCenId","center")).toString(),new JSONArray().put(new JSONObject().put("taskId","reward\"\\").put("taskCenId","other")).toString(),new JSONArray().put(new JSONObject().put("taskId","reward\"\\").put("taskCenId","center").put("browseTime",20)).toString()}){select();selected(invalid,2);assert sends==0&&receives==0&&TimeUtil.waited==0:invalid;}
  select();selected(new JSONArray().put(new JSONObject(target().substring(1,target().length()-1))).put(new JSONObject(target().substring(1,target().length()-1))).toString(),2);assert sends==0;
  for(Object duration:new Object[]{JSONObject.NULL,false,-1,0,1.5,"20.0","20seconds",301,999999999}){select();selectedRow.optJSONObject("taskExtProps").put("browseTime",duration);selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0:duration;}
  for(String unsafe:new String[]{"支付一笔", "付款", "注册会员", "邀请好友", "开通服务", "购买商品", "消费", "绑卡", "借款", "PAYMENT_TASK", "INVITE_USER"}){select();selectedRow.put("taskTitle",unsafe);selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0:unsafe;}
  select();selectedRow.put("taskTitle","浏览保险知识").put("taskType","INSURANCE");selectedRow.optJSONObject("taskExtProps").put("prizeName","EXCHANGE 支付红包");selected(target(),2);assert sends==1&&receives==1&&Log.ok==1;
  for(String unsafe:new String[]{"投保", "购买保险", "签约", "转账", "还款"}){select();selectedRow.put("taskTitle",unsafe);selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0:unsafe;}
  for(String container:new String[]{"root","taskExtProps","taskMaterial","morphObject","morphString"})for(String key:new String[]{"taskType","TASK_TYPE"})for(String type:new String[]{"TRANSFORMER","COMMON_EVENT_TASK"}){select();JSONObject deny=new JSONObject().put(key,type);if(container.equals("root"))selectedRow.put(key,type);else if(container.equals("morphObject"))selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",deny);else if(container.equals("morphString"))selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",deny.toString());else selectedRow.put(container,deny.put("browseTime",20));selected(target(),2);assert sends==0&&TimeUtil.waited==0:container+key+type;}
  for(Object signup:new Object[]{true,"true",1,"unknown"}){select();selectedRow.optJSONObject("taskExtProps").put("needSignUp",signup);selected(target(),2);assert sends==0&&TimeUtil.waited==0:signup;}
  select();selectedRow.optJSONObject("taskExtProps").put("signupType","NO_AUTO");selected(target(),2);assert sends==0;
  for(boolean serialized:new boolean[]{false,true})for(String field:new String[]{"needSignUp","signupType"}){select();JSONObject detail=new JSONObject().put(field,field.equals("needSignUp")?"true":"NO_AUTO");selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",serialized?detail.toString():detail);selected(target(),2);assert sends==0&&TimeUtil.waited==0:field+serialized;}
  select();selectedRow.put("taskType","FIRST").put("taskMaterial",new JSONObject().put("TASK_TYPE","SECOND"));selected(target(),2);assert sends==0&&TimeUtil.waited==0;
  for(String shell:new String[]{"taskExtProps","taskMaterial"}){select();selectedRow.put(shell,JSONObject.NULL);selected(target(),2);assert sends==0&&TimeUtil.waited==0:shell;}
  for(Object malformed:new Object[]{false,17,"broken","[]"}){select();selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",malformed);selected(target(),2);assert sends==0&&TimeUtil.waited==0:malformed;}
  for(String fragment:new String[]{"{\"taskTitle\":\"x\"},\"other\":\"TRANSFORMER\"", "{\"taskTitle\":\"x\"},\"other\":\"plain\"", "{},\"other\":false", "{\"taskTitle\":\"common_event_task\"}", "{\"taskTitle\":\"x\"} /*"}){select();selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",fragment);selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0:fragment;}
  select();selectedRow.put("taskExtProps",false);selected(target(),2);assert sends==0;
  select();selectedRow.put("taskMaterial",false);selected(target(),2);assert sends==0;
  select();selectedRow.put("taskType",1);selected(target(),2);assert sends==0;
  select();selectedRow.put("taskProcessStatus","RECEIVED");selected(target(),2);assert sends==0&&TimeUtil.waited==0;
  for(Object badState:new Object[]{JSONObject.NULL,false,1}){select();selectedRow.put("taskProcessStatus","NOT_DONE");driftAt=1;driftKey="taskStatus";driftValue=badState;selected(target(),2);assert sends==0&&TimeUtil.waited==0:badState;}
  select();selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL","{\"taskTitle\":\"\\u4ed8\\u6b3e\"}");selected(target(),2);assert sends==0&&TimeUtil.waited==0;
  for(String state:new String[]{"NONE_SIGNUP","SIGNUP_COMPLETE","HAS_COMPLETED","DONE","RECEIVED","UNKNOWN"}){select();taskStatus=state;selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0:state;}
  select();duplicateTask=true;selected(target(),2);assert sends==0&&TimeUtil.waited==0;
  for(int at:new int[]{2,3,4,5,6})for(String key:new String[]{"taskId","taskCenId"}){select();driftAt=at;driftKey=key;driftValue="changed";selected(target(),2);assert sends==(at>=4?1:0)&&receives==(at>=6?1:0)&&Log.ok==0:at+key;if(at==4||at==6){assert RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");MyUtils.day+=2;selected(target(),2);assert sends==1&&receives==(at==6?1:0);}}
  select();driftAt=3;driftKey="taskExtProps";driftValue=new JSONObject().put("browseTime",25);selected(target(),2);assert sends==0&&TimeUtil.waited==20_000;
  for(int at:new int[]{2,3})for(String key:new String[]{"taskType","taskTitle","taskSource","taskMaterial","taskExtProps"}){select();driftAt=at;driftKey=key;driftValue=key.equals("taskMaterial")?new JSONObject().put("url","https://example.invalid/changed"):key.equals("taskExtProps")?new JSONObject().put("browseTime",20).put("taskTitle","另一任务"):"OTHER";selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==(at==3?20_000:0):at+key;}
  select();selectedRow.put("taskMaterial",new JSONObject().put("actionUrl","https://example.invalid/original"));driftAt=3;driftKey="taskMaterial";driftValue=new JSONObject().put("actionUrl","https://example.invalid/changed");selected(target(),2);assert sends==0&&TimeUtil.waited==20_000;
  select();driftAt=3;driftKey="taskExtProps";driftValue=new JSONObject().put("browseTime",20).put("prizeCount",99);selected(target(),2);assert sends==1&&receives==1&&TimeUtil.waited==20_000;
  select();driftAt=5;driftKey="taskTitle";driftValue="付款";selected(target(),2);assert sends==1&&receives==0&&Log.ok==0;
  select();sendStateBad=true;selected(target(),2);assert sends==1&&receives==0&&Log.ok==0&&RuntimeInfo.data.containsKey("legacyCard::hundredReceipt");MyUtils.day+=2;selected(target(),2);assert sends==1;
  select();change=false;selected(target(),2);assert sends==1&&receives==0&&Log.ok==0;MyUtils.day+=2;selected(target(),2);assert sends==1;
  select();badAck=true;selected(target(),2);assert sends==1&&receives==0&&Log.ok==0;
  select();RuntimeInfo.writable=false;selected(target(),2);assert sends==0&&receives==0;
  select();selected(target(),0);assert requests==1&&sends==0&&receives==0&&TimeUtil.waited==0&&RuntimeInfo.data.isEmpty();assert Log.records.size()==1&&Log.records.get(0).contains(target())&&Log.records.get(0).contains("20");
  select();selected("[]",2);assert requests==1&&sends==0&&receives==0&&RuntimeInfo.data.isEmpty()&&Log.records.size()==1&&Log.records.get(0).contains(target());
  select();directoryRows=25;selected("[]",0);assert requests==1&&Log.records.size()==20&&sends==0&&receives==0&&RuntimeInfo.data.isEmpty();
  select();RuntimeInfo.data.put("legacyCardAttempts","broken");selected("[]",0);assert requests==1&&sends==0&&receives==0&&RuntimeInfo.data.size()==1&&RuntimeInfo.data.get("legacyCardAttempts").equals("broken");
  select();selectedRow.remove("taskTitle");selected("[]",2);assert requests==1&&Log.records.isEmpty()&&RuntimeInfo.data.isEmpty();
  select();selectedRow.remove("taskTitle");selectedRow.optJSONObject("taskExtProps").put("TASK_MORPHO_DETAIL",new JSONObject().put("taskTitle","来源说明").toString());selected("[]",2);assert Log.records.size()==1;
  select();selectedRow.put("taskTitle","说明\"\\\nhttps://example.invalid/secret self");selected("[]",0);assert Log.records.size()==1&&!Log.records.get(0).contains("https://")&&!Log.records.get(0).contains("self")&&!Log.records.get(0).contains("secret")&&Log.records.get(0).contains("\\n");
  select();String pending=new JSONObject().put("uid","self").put("action","selected-send").put("day",20261005).toString();RuntimeInfo.data.put("legacyCard::hundredReceipt",pending);MyUtils.day+=2;selected(target(),2);assert requests==1&&sends==0&&receives==0&&TimeUtil.waited==0&&RuntimeInfo.data.get("legacyCard::hundredReceipt").equals(pending)&&Log.records.size()==1;
  select();RuntimeInfo.data.put("legacyCardAttempts",new JSONObject().put("day",20261007).put("count",2).toString());selected(target(),2);assert sends==0&&receives==0&&TimeUtil.waited==0;
  for(int count:new int[]{21,999999999}){select();String corrupt=new JSONObject().put("day",20261006).put("count",count).toString();RuntimeInfo.data.put("legacyCardAttempts",corrupt);selected(target(),20);assert sends==0&&receives==0&&TimeUtil.waited==0&&RuntimeInfo.data.get("legacyCardAttempts").equals(corrupt):count;run(true,true,true,true,20);assert sends+receives+flips+merges==0&&RuntimeInfo.data.get("legacyCardAttempts").equals(corrupt):count;}
  select();selected(target(),1);assert sends==1&&receives==0&&Log.ok==0&&new JSONObject(RuntimeInfo.data.get("legacyCardAttempts")).optInt("count")==1;
  select();LegacyCardRewards.run(new OtherRequestGate(),true,false,false,false,3,true,target(),false);assert sends==2&&receives==1&&Log.ok==1&&new JSONObject(RuntimeInfo.data.get("legacyCardAttempts")).optInt("count")==3;
  for(int boundary=0;boundary<5;boundary++){select();if(boundary==0)TimeUtil.cancelWait=true;if(boundary==1)TimeUtil.switchWait=true;if(boundary==2)TimeUtil.dayWait=true;if(boundary==3)cancelAfter=true;if(boundary==4)switchUid=true;try{selected(target(),2);throw new AssertionError("manual cancel/account/day swallowed "+boundary);}catch(TaskCancelledException expected){}assert sends==(boundary>=3?1:0)&&receives==0&&Log.ok==0;}
  reset();run(false,false,false,false,5);assert requests==0;run(true,true,true,true,0);assert requests==0;
  reset();run(true,true,false,false,3);assert sends==1&&receives==2&&Log.ok==2;run(true,true,false,false,3);assert sends==1&&receives==2;
  reset();run(true,false,false,false,1);assert sends==1&&receives==0;run(true,false,false,false,1);assert sends==1&&receives==0;
  reset();taskStatus="RECEIVED";run(false,true,false,false,3);assert receives==0;
  for(String unresolved:new String[]{"NOT_DONE","TODO","NONE_SIGNUP","SIGNUP_COMPLETE","BROWSE","HAS_COMPLETED"}){reset();taskStatus=unresolved;run(false,true,false,false,3);assert sends==0&&receives==0&&Log.ok==0:unresolved;assert !RuntimeInfo.data.containsKey("legacyCardAttempts"):unresolved;}
  reset();signStatus="MALFORMED";run(true,false,false,false,3);assert sends==0;
  reset();signStatus="COMPLETED";run(true,false,false,false,3);assert sends==0&&receives==0;
  reset();RuntimeInfo.data.put("legacyCardAttempts","broken");run(true,true,true,true,3);assert sends+receives+flips+merges==0;
  for(int corruptDay:new int[]{1,20261000,20260230,20270101}){reset();RuntimeInfo.data.put("legacyCardAttempts",new JSONObject().put("day",corruptDay).put("count",1).toString());run(true,true,true,true,3);assert sends+receives+flips+merges==0:corruptDay;}
  reset();RuntimeInfo.data.put("legacyCard::hundredReceipt","broken");run(true,true,false,false,3);assert requests==0;
  reset();risk=true;run(true,true,false,false,3);assert sends+receives==0&&RuntimeInfo.getInstance().getLong("OtherTask.nextRun",0)==0;
  reset();duplicate=true;run(true,false,false,false,3);assert sends==0;
  reset();RuntimeInfo.writable=false;run(true,true,false,false,3);assert sends==0&&receives==0;
  reset();change=false;run(true,false,false,false,3);assert sends==1&&Log.ok==0;MyUtils.day++;run(true,false,false,false,3);assert sends==1;
  reset();badAck=true;run(true,false,false,false,3);assert sends==1&&receives==0&&Log.ok==0;
  reset();run(false,false,true,false,2);assert flips==1&&Log.ok==1&&remaining==0;
  reset();missing=true;run(false,false,true,false,2);assert flips==0;
  reset();opened=true;run(false,false,true,false,2);assert flips==0;
  reset();wrongCard=true;run(false,false,true,false,2);assert flips==1&&Log.ok==0;MyUtils.day++;run(false,false,true,false,2);assert flips==1;
  reset();change=false;run(false,false,true,false,2);assert flips==1&&Log.ok==0;MyUtils.day++;run(false,false,true,false,2);assert flips==1;
  reset();fragments=true;run(false,false,false,true,2);assert merges==1&&Log.ok==1&&!fragments;
  reset();fragments=true;change=false;run(false,false,false,true,2);assert merges==1&&Log.ok==0;
  reset();switchUid=true;try{run(true,true,false,false,3);throw new AssertionError("account switch continued");}catch(TaskCancelledException expected){}assert sends==1&&receives==0&&Log.ok==0;
  reset();cancelAfter=true;try{run(true,true,false,false,3);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert sends==1&&receives==0;
  System.out.println("PASS source HuaHua signup/send/award ACK chain and automatic Hundred task selection/signup; legacy current-query sign/finished reward/free flop/merge; selected task full 1/20/30/45/300-second wait, typed/risk/context/readback guards, shared durable budgets and account/day/cancel boundaries");
 }
}
'''
def body(path,marker):
 s=(SOURCE/path).read_text(encoding='utf-8');return 'static '+s[s.index(marker):]
code=code.replace('@@GATE@@',body('model/task/other/OtherRequestGate.java','final class OtherRequestGate')).replace('@@WORKER@@',body('model/task/other/LegacyCardRewards.java','final class LegacyCardRewards'))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
from daily_task_fixture import with_daily_task
code=with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-legacy-card-') as temp:
 p=Path(temp)/'LegacyCardRewardsCheck.java';p.write_text(code,encoding='utf-8')
 extra=['util/TaskCancelledException.java','rpc/intervallimit/RequestBudgetPolicy.java','rpc/intervallimit/RpcFailurePolicy.java']
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',temp,str(p),*[str(SOURCE/x) for x in extra]],check=True)
 subprocess.run(['java','-ea','-cp',temp+os.pathsep+str(jar),'LegacyCardRewardsCheck'],check=True)
