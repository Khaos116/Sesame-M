"""Runs real HaoJiaPaymentCoin and OtherRequestGate against a fake current-program RPC server."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method
SOURCE = Path(__file__).resolve().parents[1] / 'app/src/main/java/io/github/aw1y2z/sesame'
code = r'''
import org.json.*;import java.util.*;import java.math.BigDecimal;import java.text.SimpleDateFormat;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.rpc.intervallimit.RequestBudgetPolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
public class HaoJiaPaymentCoinCheck {
 static class MyUtils {static int day=7;static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day,12,0);return c;}}
 static class TaskCommon {static boolean IS_ENERGY_TIME;}
 static class UserIdMap {static String uid="self";static String getCurrentUid(){return uid;}}
 static class TimeUtil {static int waited,cancelAt;static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();if(n==1000){waited++;if(cancelAt>0&&waited==cancelAt)throw new TaskCancelledException();}}}
 static class RuntimeInfo {static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo runtime=new RuntimeInfo();static RuntimeInfo getInstance(){return runtime;}String getString(String k){return data.getOrDefault(k,"");}long getLong(String k,long fallback){try{return Long.parseLong(getString(k));}catch(Exception e){return fallback;}}void put(String k,Object v){data.put(k,String.valueOf(v));}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Status {static Map<String,Integer> values=new HashMap<>();static Set<String> flags=new HashSet<>();static int getIntFlagToday(String k){return values.getOrDefault(MyUtils.day+":"+k,0);}static void setIntFlagToday(String k,int n){values.put(MyUtils.day+":"+k,n);}static boolean hasFlagToday(String k){return flags.contains(MyUtils.day+":"+k);}static void flagToday(String k){flags.add(MyUtils.day+":"+k);}}
 static class Log {static int ok;static String messages="",otherMessages="";static void other(String s){ok++;otherMessages+=s;}static void record(String s){messages+=s;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}static void i(String t,String s){}static void printStackTrace(String t,Throwable e){throw new AssertionError(e);}}
 @@GATE@@
 @@WORKER@@
 static class HaoJiaRpcCall {@@LEGACY_RPC@@}
 static class Field<T>{T value;Field(T value){this.value=value;}T getValue(){return value;}}
 static class OtherTask {static final String TAG="OtherTask";OtherRequestGate gate=new OtherRequestGate();boolean isEnable(){return true;}@@FIELDS@@ @@CHECK@@ @@RUN@@ @@LEGACY@@}
 static class LegacyCardRewards {static void run(OtherRequestGate gate,Object... fields)throws Exception{if(!fillPrevious)return;for(int i=0;i<80;i++)gate.call("preceding activity",()->"{}");}}
 static class SjGamePlay {static void ride(SjActivityTasks worker){}}
 static class FriendActivityTasks {FriendActivityTasks(SjActivityTasks worker){}void p2eBrowse(){}void luckyCard(){}}
  static class RpcRequestGuard {@@RPC_FAILURE@@}
 static class SjActivityTasks {OtherRequestGate gate;SjActivityTasks(OtherRequestGate gate,int budget){this.gate=gate;}void shenQuan(boolean sign,boolean tasks,boolean draw,String location)throws Exception{gate.call("next activity",()->{nextActivityCalls++;return "{}";});}void mileage(String codes,String city){}void intimacy(){}void p2eSign(){}void leiYouJiTasks(){}@@RESPONSE_FIELD@@}
 static boolean fillPrevious,transportError,queryFailure;static int nextActivityCalls;
 static int legacySigns,legacyApplies;static boolean legacyRootFailure,legacyComponentFailure,legacyConflict;static String legacyResultCode="",legacyError="",legacyRetCode="",legacyComponentCode="";
 static final String PROGRAM="independent_component_program2026062903615094",SIGN="independent_component_sign_in_03386004_independent_component_sign_in",TASK="independent_component_task_reward_v2_03385041_independent_component_task_reward_",RECEIPT="otherHaoJiaPaymentCoinReceipt";
 static String code="task\"\\",record="",status="init",reward="init",advance="userPush",name="\u6d4f\u89c8\u9875\u9762",latest="20261006",afterStage="";
  static int requests,mutations,applies,processes,rewards,signs,seconds=27,count=4,balance=10;static boolean change=true,badAck,badReadback,wrongBalance,duplicate,invalidCode,deny,switchUid,crossDay,cancelAfter,missingDuration,missingCycle,nullCycle,emptyCycle,invalidCycle,invalidCycleRow,conflict,malformed;
 static JSONObject task(){JSONObject display=new JSONObject().put("taskName",name);if(!missingDuration)display.put("browseTime",seconds);return new JSONObject().put("code",invalidCode?123:code).put("taskStatus",status).put("rewardStatus",reward).put("advanceType",advance).put("recordNo",record).put("displayInfo",display);}
 static List<JSONObject> extraRows=new ArrayList<>();
  static JSONObject signOrder(){JSONObject order=new JSONObject().put("playSignInTemplateInfo",new JSONObject().put("code","sign\"\\")).put("playSignInCycleInstanceInfoList",new JSONArray().put(new JSONObject().put("latestSignInDate",latest).put("accumulativeSignInCount",count)));if(missingCycle&&signs==0)order.remove("playSignInCycleInstanceInfoList");if(nullCycle&&signs==0)order.put("playSignInCycleInstanceInfoList",JSONObject.NULL);if(emptyCycle&&signs==0)order.put("playSignInCycleInstanceInfoList",new JSONArray());if(invalidCycle)order.put("playSignInCycleInstanceInfoList","invalid");if(invalidCycleRow)order.put("playSignInCycleInstanceInfoList",new JSONArray().put("invalid"));return order;}
 static class ApplicationHook {
  static boolean isOffline(){return false;}
  static String requestString(String method,String raw){return requestRaw(method,raw,3,-1);}
  static String requestString(String method,String raw,int tries,int pause){return requestRaw(method,raw,tries,pause);}
  static String requestRaw(String method,String raw,int tries,int pause){requests++;JSONObject args=new JSONArray(raw).optJSONObject(0),root=new JSONObject().put("isSuccess",true);if(malformed)return "bad-json";
  if(method.endsWith("queryPaymentCoinBalance")){assert args.optString("userId").equals("self");root.put("data",new JSONObject().put("balance",balance));if(conflict)root.put("error","1000");return root.toString();}
  if(args.optString("operationParamIdentify").equals("independent_component_program2023082800847098")){
   assert args.optString("channel").equals("jiaofei_card_promo")&&args.optString("source").equals("jiaofei");JSONObject parts=args.optJSONObject("components");assert parts.length()==1;String component=parts.keys().next();JSONObject payload=parts.optJSONObject(component);assert payload.optString("channel").equals("jiaofei_card_promo");JSONObject content=new JSONObject();
   if(component.endsWith("_recall"))content.put("playSignInOrderInfoList",new JSONArray().put(new JSONObject().put("playSignInTemplateInfo",new JSONObject().put("code","legacy\"\\"))));
   else if(component.endsWith("_query"))content.put("playTaskOrderInfoList",new JSONArray().put(new JSONObject().put("code","legacy\"\\").put("taskStatus","init").put("advanceType","userPush").put("displayInfo",new JSONObject().put("activityName","浏览活动").put("browseTime",0))));
   else {assert tries==1&&pause==0:"legacy mutation used default retries";assert payload.optString("code").equals("legacy\"\\");if(component.contains("sign_in"))legacySigns++;else legacyApplies++;}
   if(legacyConflict)root.put("success",false);if(!legacyResultCode.isEmpty())root.put("resultCode",legacyResultCode);if(!legacyError.isEmpty())root.put("error",legacyError);if(!legacyRetCode.isEmpty())root.put("retCode",legacyRetCode);JSONObject item=new JSONObject().put("isSuccess",!legacyComponentFailure).put("content",content);if(!legacyComponentCode.isEmpty())item.put("resultCode",legacyComponentCode);return root.put("isSuccess",!legacyRootFailure).put("components",new JSONObject().put(component,item)).toString();
  }
  assert method.equals("alipay.imasp.program.programInvoke");assert args.optString("operationParamIdentify").equals(PROGRAM);JSONObject components=args.optJSONObject("components");assert components.length()==1;String component=components.keys().next();JSONObject p=components.optJSONObject(component),value=new JSONObject().put("isSuccess",true),content=new JSONObject();root.put("components",new JSONObject().put(component,value));value.put("content",content);
  if(component.equals(TASK+"query")){if(queryFailure)value.put("isSuccess",false);JSONArray rows=new JSONArray().put(task());if(duplicate)rows.put(task());for(JSONObject extra:extraRows)rows.put(extra);content.put("playTaskOrderInfoList",rows);}
  else if(component.equals(SIGN+"_recall"))content.put("playSignInOrderInfoList",new JSONArray().put(signOrder()));
   else {assert tries==1&&pause==0:"mutation used default retries: "+component;mutations++;assert RuntimeInfo.data.containsKey(RECEIPT);if(transportError)throw new IllegalStateException("authCode=SECRET_CODE&token=SECRET_TOKEN");String stage;
   if(component.equals(TASK+"apply")){stage="apply";applies++;assert p.optString("code").equals(code)&&!p.has("recordNo")&&args.optString("source").equals("jiaofei");content.put("claimedTask",new JSONObject().put("recordNo","rec\"\\"));if(change){status="claim";record="rec\"\\";}}
    else if(component.equals(TASK+"process")){stage="process";processes++;assert TimeUtil.waited==(missingDuration?15:seconds);assert p.optString("code").equals(code)&&p.optString("recordNo").equals(record)&&args.optString("source").equals("jiaofei");if(change)status="finish";}
   else if(component.equals(TASK+"get_reward")){stage="reward";rewards++;assert p.optString("code").equals(code)&&p.optString("recordNo").equals(record)&&!args.has("source");if(change){reward="success";if(!wrongBalance)balance++;}}
   else if(component.equals(SIGN)){stage="sign";signs++;assert p.optString("code").equals("sign\"\\")&&!args.has("source");if(change){latest=String.format("202610%02d",MyUtils.day);count++;if(!wrongBalance)balance++;}}
   else throw new AssertionError("Unexpected component "+component);
   if(badReadback){if(stage.equals("sign"))count+=2;else record="wrong";}
   if(badAck)value.put("isSuccess",false);if(deny)root.put("resultCode","1009");if(afterStage.isEmpty()||afterStage.equals(stage)){if(switchUid)UserIdMap.uid="other";if(crossDay)MyUtils.day++;if(cancelAfter)TimeUtil.cancel=true;}
  }return root.toString();}}
  static void reset(){RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.values.clear();Status.flags.clear();Log.messages=Log.otherMessages="";extraRows.clear();transportError=queryFailure=false;Log.ok=TimeUtil.waited=TimeUtil.cancelAt=requests=mutations=applies=processes=rewards=signs=0;TimeUtil.cancel=false;UserIdMap.uid="self";MyUtils.day=7;status="init";reward="init";advance="userPush";name="\u6d4f\u89c8\u9875\u9762";record="";latest="20261006";afterStage="";seconds=27;count=4;balance=10;change=true;badAck=badReadback=wrongBalance=duplicate=invalidCode=deny=switchUid=crossDay=cancelAfter=missingDuration=missingCycle=nullCycle=emptyCycle=invalidCycle=invalidCycleRow=conflict=malformed=false;}
 static void run(boolean sign,boolean browse,boolean rewards,int budget){HaoJiaPaymentCoin.run(new OtherRequestGate(),sign,browse,rewards,budget);}
 static boolean pending(){return RuntimeInfo.data.containsKey(RECEIPT);}
  public static void main(String[] args)throws Exception {
   reset();run(true,true,true,4);
   assert Log.messages.contains("签到成功")&&Log.messages.contains("浏览完成")&&Log.messages.contains("领奖成功") : "confirmed HaoJia results only reached the category log";
   assert Log.messages.contains("✅ 好家缴费金｜签到")&&Log.messages.contains("✅ 好家缴费金｜浏览页面：浏览")&&Log.messages.contains("✅ 好家缴费金｜浏览页面：领奖")&&Log.otherMessages.contains("✅ 好家缴费金") : "confirmed results lack leading success emoji";
   assert Log.messages.contains("已完成=0项，已领奖=0项，待领奖=0项\n剩余未完成=1项，剩余可免费浏览=1项")&&Log.messages.contains("已完成=1项，已领奖=0项，待领奖=1项\n剩余未完成=0项，剩余可免费浏览=0项")&&Log.messages.contains("已完成=1项，已领奖=1项，待领奖=0项\n剩余未完成=0项，剩余可免费浏览=0项") : "progress did not follow finish and reward states";
   assert Log.messages.split("好家缴费金进度：",-1).length-1==3&&!Log.messages.contains("条目数=")&&!Log.messages.contains("查询到任务数=") : "unchanged catalogue queries spammed task totals";
   assert Log.messages.contains("浏览完成 · 待领奖（已回查）")&&Log.messages.contains("领奖成功 · 余额=11→12（已回查）") : "human result state missing";
   assert !Log.messages.contains("查询调用，根成功校验=true") : "successful component queries still spam runtime";
   assert Log.messages.contains("本轮新完成浏览任务=1项") : "new task success was not summarized";
   run(true,true,true,4);assert Log.messages.contains("本轮新完成浏览任务=0项")&&mutations==4 : "previous completion was counted or submitted again";
   reset();
   for(int i=0;i<15;i++)extraRows.add(task().put("code","extra"+i).put("taskStatus",i<8?"finish":"init").put("rewardStatus",i<6?"success":"init").put("recordNo","").put("advanceType","eventPush"));
   run(false,true,true,3);
   assert Log.messages.contains("目录总数=16项，已完成=8项，已领奖=6项，待领奖=2项\n剩余未完成=8项，剩余可免费浏览=1项，需人工或不支持=7项，任务状态未知=0项，奖励状态未知=0项") : "catalogue totals were mistaken for remaining tasks";
   assert Log.messages.contains("目录总数=16项，已完成=9项，已领奖=7项，待领奖=2项\n剩余未完成=7项，剩余可免费浏览=0项，需人工或不支持=7项")&&mutations==3 : "16 unchanged catalogue rows hid real task completion";
   assert Log.messages.split("好家缴费金进度：",-1).length-1==3 : "repeated unchanged snapshots were logged as progress";
   assert Log.messages.contains("☑️ 好家缴费金｜浏览页面：已完成 · 已领奖（此前完成）") : "existing completion not clearly distinguished";
   reset();status="claim";record="existing";reward="wrong";run(false,true,false,1);
   assert Log.messages.contains("浏览完成 · 奖励状态待确认")&&!Log.messages.contains("浏览完成 · 待领奖") : "unknown reward was shown as claimable";
   reset();status="bad";reward="success";
   extraRows.add(task().put("code","unknownReward").put("taskStatus","finish").put("rewardStatus",new JSONObject()));
   extraRows.add(task().put("code","paid").put("taskStatus","init").put("rewardStatus","init").put("displayInfo",new JSONObject().put("taskName","浏览支付宝购买保险")));
   run(false,true,false,3);
   assert Log.messages.contains("目录总数=3项，已完成=1项，已领奖=0项，待领奖=0项\n剩余未完成=1项，剩余可免费浏览=0项，需人工或不支持=1项，任务状态未知=1项，奖励状态未知=1项")&&mutations==0 : "unknown or paid tasks were reported as completed/automatic";
   reset();queryFailure=true;run(false,true,false,3);
   assert mutations==0&&Log.messages.contains("查询调用，根成功校验=true，组件成功校验=false")&&Log.messages.contains("本轮进度未确认") : "query failure lost diagnostic evidence";
   reset();duplicate=true;run(false,true,true,3);
   assert !Log.messages.contains("好家缴费金进度：")&&mutations==0 : "invalid duplicate catalogue produced trusted progress";
   reset();name="逛一逛支付宝职业培训";missingDuration=true;run(false,true,false,2);
   assert applies==1&&processes==1&&TimeUtil.waited==15&&!pending() : "Alipay brand name was mistaken for a payment task";
   for(String paid:new String[]{"逛支付宝购买保险","浏览支付宝支付订单","逛支付宝充值"}){reset();name=paid;run(false,true,false,4);assert mutations==0 : "paid action was enabled: "+paid;}
   assert !HaoJiaPaymentCoin.browse(new JSONObject().put("advanceType","userPush").put("displayInfo",new JSONObject().put("taskName",new JSONObject().put("name","浏览页面")))) : "non-string title was accepted as a free browse task";
   reset();missingCycle=true;count=0;run(true,false,false,1);assert signs==1&&count==1&&!pending()&&Log.ok==1 : "SJ first sign without an existing cycle was rejected";
   reset();emptyCycle=true;count=0;run(true,false,false,1);assert signs==1&&count==1&&!pending()&&Log.ok==1 : "empty first cycle was rejected";
   reset();nullCycle=true;count=0;run(true,false,false,1);assert signs==1&&count==1&&!pending()&&Log.ok==1 : "SJ optional null first cycle was rejected";
   reset();invalidCycle=true;run(true,false,false,1);assert mutations==0 : "invalid sign cycle was treated as a new cycle";
   reset();invalidCycleRow=true;run(true,false,false,1);assert mutations==0 : "invalid cycle row was treated as a first sign";
   reset();fillPrevious=true;nextActivityCalls=0;OtherTask dispatcher=new OtherTask();dispatcher.shenQuanSign.value=true;dispatcher.haojiaWuyou.value=true;legacySigns=legacyApplies=0;dispatcher.run();fillPrevious=false;
   assert nextActivityCalls==1&&legacySigns==1&&legacyApplies==1 : "previous activity's request counter prevented normal SJ/HaOJia entry";
   for(int stage=0;stage<4;stage++){reset();int needed=new int[]{3,3,4,5}[stage];if(stage>=2){status=stage==2?"claim":"finish";record="existing";}OtherRequestGate nearLimit=new OtherRequestGate();for(int i=0;i<80-needed;i++)nearLimit.call("earlier queries",()->"{}");HaoJiaPaymentCoin.run(nearLimit,stage==0,stage==1||stage==2,stage==3,4);
    assert mutations==0&&!pending()&&RuntimeInfo.getInstance().getString("otherHaoJiaPaymentCoinAttempts").isEmpty() : "unsent HaoJia operation left a receipt or charged daily quota: "+stage;
    assert Log.messages.contains("未调用RPC")&&Log.messages.contains("BudgetExhausted"):"unsent operation was not diagnosed: "+stage;
   }
   reset();transportError=true;run(true,false,false,4);assert mutations==1&&pending()&&Log.messages.contains("已进入RPC")&&!Log.messages.contains("SECRET_") : "entered transport failure lost its receipt or exposed credentials";
  reset();legacySigns=legacyApplies=0;legacyRootFailure=legacyComponentFailure=legacyConflict=false;new OtherTask().runHaoJia();assert legacySigns==1&&legacyApplies==1;
  for(int failure=0;failure<7;failure++){reset();legacySigns=legacyApplies=0;legacyRootFailure=failure==0;legacyComponentFailure=failure==1;legacyConflict=failure==2;legacyResultCode=failure==3?"FAIL":"";legacyError=failure==4?"48":"";legacyRetCode=failure==5?"FAIL":"";legacyComponentCode=failure==6?"FAIL":"";new OtherTask().runHaoJia();assert legacySigns+legacyApplies==0:"failed legacy response triggered a write: "+failure;}
  legacyRootFailure=legacyComponentFailure=legacyConflict=false;
  legacyResultCode=legacyError=legacyRetCode=legacyComponentCode="";
  reset();run(true,true,true,4);assert signs==1&&applies==1&&processes==1&&rewards==1&&Log.ok==3&&TimeUtil.waited==27&&!pending();run(true,true,true,4);assert mutations==4;
  reset();run(true,true,true,0);assert requests==0;run(false,false,false,4);assert requests==0;
  reset();run(false,true,true,2);assert applies==1&&processes==1&&rewards==0&&TimeUtil.waited==27&&!pending();Status.values.clear();Status.flags.clear();run(false,false,true,2);assert mutations==2;
  reset();latest="20261007";run(true,false,false,4);assert signs==0;
  reset();status="finish";record="existing";run(false,false,true,1);assert rewards==1&&balance==11&&!pending()&&Log.ok==1;
  reset();status="claim";record="existing";run(false,true,false,1);assert applies==0&&processes==1&&TimeUtil.waited==27;
  reset();advance="eventPush";run(false,true,false,4);assert mutations==0;
  reset();name="\u6d4f\u89c8\u4fdd\u9669";run(false,true,false,4);assert mutations==0;
  reset();name="\u5f00\u901a\u8d26\u6237";run(false,true,false,4);assert mutations==0;
   reset();missingDuration=true;run(false,true,false,4);assert applies==1&&processes==1&&TimeUtil.waited==15&&!pending() : "SJ default 15-second browse was rejected";
  reset();seconds=0;run(false,true,false,4);assert mutations==0;
  reset();seconds=3601;run(false,true,false,4);assert mutations==0;
  reset();duplicate=true;run(false,true,false,4);assert mutations==0;
  reset();invalidCode=true;run(false,true,false,4);assert mutations==0;
  reset();RuntimeInfo.writable=false;run(true,true,true,4);assert mutations==0;
  reset();RuntimeInfo.data.put("otherHaoJiaPaymentCoinAttempts","broken");run(true,true,true,4);assert mutations==0;
  reset();conflict=true;run(true,true,true,4);assert mutations==0;
  reset();malformed=true;run(true,true,true,4);assert mutations==0;
  reset();change=false;run(false,true,true,4);assert applies==1&&pending()&&Log.ok==0;MyUtils.day++;run(false,true,true,4);assert applies==1&&pending();
  reset();change=false;run(false,true,true,4);assert applies==1&&pending();MyUtils.day++;status="claim";record="laterInstance";change=true;run(false,true,true,4);assert mutations==1&&pending();
  reset();Status.setIntFlagToday("other::haojiaCoinAttempts",-1);run(true,true,true,4);assert mutations==0;
  reset();status="finish";record="existing";wrongBalance=true;run(false,false,true,4);assert rewards==1&&pending()&&Log.ok==0;MyUtils.day++;run(false,false,true,4);assert rewards==1&&pending();
  reset();badReadback=true;run(true,false,false,4);assert signs==1&&pending()&&Log.ok==0;
  reset();badAck=true;run(false,true,false,4);assert applies==1&&pending()&&processes==0; // apply readback is insufficient without a positive acknowledgement
  reset();deny=true;run(true,true,true,4);assert signs==1&&pending()&&RuntimeInfo.getInstance().getLong("OtherTask.nextRun",0)==0;
  reset();switchUid=true;run(true,true,true,4);assert signs==1&&pending()&&Log.ok==0;
  reset();crossDay=true;run(true,true,true,4);assert signs==1&&pending()&&Log.ok==0;
  reset();cancelAfter=true;try{run(true,true,true,4);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert signs==1&&pending()&&Log.ok==0;
  reset();TimeUtil.cancelAt=2;try{run(false,true,false,4);throw new AssertionError("browse cancel swallowed");}catch(TaskCancelledException expected){}assert applies==1&&processes==0&&TimeUtil.waited==2;
  reset();OtherRequestGate gate=new OtherRequestGate();for(int i=0;i<80;i++)gate.call("bounded",()->"{}");try{gate.call("bounded",()->"{}");throw new AssertionError("request cap");}catch(OtherRequestGate.BudgetExhausted expected){}
  reset();try{new OtherRequestGate().call("denied",()->"{\"retCode\":\"1009\"}");throw new AssertionError("risk marker");}catch(OtherRequestGate.Denied expected){}assert RuntimeInfo.getInstance().getLong("OtherTask.nextRun",0)==0;
  RuntimeInfo.getInstance().put("OtherTask.nextRun",System.currentTimeMillis()+86400000L);assert new OtherTask().check():"unrelated legacy pause still blocks all other tasks";final int[] admitted={0};new OtherRequestGate().call("unrelated activity",()->{admitted[0]++;return "{}";});assert admitted[0]==1:"duplicate gate blocked unrelated RPC";
  System.out.println("PASS production current HaoJia program: full browse duration, apply/process/reward/sign readback, balance, durable budget/receipt, default-off and account/day/cancel/risk/request guards");
 }
}
'''
def body(filename, marker):
    text = (SOURCE / ('model/task/other/' + filename)).read_text(encoding='utf-8')
    return 'static ' + text[text.index(marker):]
code = code.replace('@@CHECK@@', method('model/task/other/OtherTask.java', 'public Boolean check('))
other_source=(SOURCE/'model/task/other/OtherTask.java').read_text(encoding='utf-8')
import re
fields=[]
for kind,names in re.findall(r'private (BooleanModelField|IntegerModelField|StringModelField) ([^;]+);',other_source):
    java_type,default={'BooleanModelField':('Boolean','false'),'IntegerModelField':('Integer','1'),'StringModelField':('String','""')}[kind]
    fields.extend(f'Field<{java_type}> {name.strip()}=new Field<>({default});' for name in names.split(','))
code=code.replace('@@FIELDS@@','\n'.join(fields)).replace('@@RUN@@',method('model/task/other/OtherTask.java','public void run('))
code = code.replace('@@GATE@@', body('OtherRequestGate.java', 'final class OtherRequestGate'))
code = code.replace('@@WORKER@@', body('HaoJiaPaymentCoin.java', 'final class HaoJiaPaymentCoin'))
legacy_rpc = '\n'.join(method('model/task/other/HaoJiaRpcCall.java', signature) for signature in ('public static String querySignIn(', 'public static String doSignIn(', 'public static String queryTaskList(', 'public static String applyTask(', 'private static String request('))
code = code.replace('@@LEGACY_RPC@@', legacy_rpc + '\nprivate static final String CHANNEL="jiaofei_card_promo",OPERATION_PARAM_ID="independent_component_program2023082800847098";')
code = code.replace('@@LEGACY@@', '\n'.join(method('model/task/other/OtherTask.java', signature) for signature in ('private void runHaoJia(', 'private static JSONObject component(', 'private static boolean containsRisk(', 'private static boolean hasSignedToday(', 'private static boolean ok(', 'private static void sleep(')))
code = code.replace('@@RPC_FAILURE@@', method('rpc/intervallimit/RpcRequestGuard.java', 'public static boolean isFailure('))
code = code.replace('@@RESPONSE_FIELD@@', method('model/task/other/SjActivityTasks.java', 'static String responseField('))
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-haojia-coin-') as tmp:
    source = Path(tmp)/'HaoJiaPaymentCoinCheck.java'
    source.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(source), str(SOURCE/'util/TaskCancelledException.java'), str(SOURCE/'rpc/intervallimit/RequestBudgetPolicy.java'), str(SOURCE/'rpc/intervallimit/RpcFailurePolicy.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp+os.pathsep+str(jar), 'HaoJiaPaymentCoinCheck'], check=True)
