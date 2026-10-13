"""Replay formal step hooks, native synchronization and single-timezone legacy patrol requests."""
from pathlib import Path
import subprocess, sys, tempfile, os
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method

source = "model/task/antSports/AntSports.java"
code = r'''
import java.util.*;import java.lang.reflect.*;import checks.stub.*;import org.json.JSONException;import org.json.JSONObject;
public class StepSyncSourcesCheck extends Parent {
 static final String TAG="test";static final int SKIP_SYNC_STEP=18000;
 static class FieldValue {int value;FieldValue(int v){value=v;}int getValue(){return value;}}
 FieldValue earliestSyncStepTime=new FieldValue(8);int target=22004;int tmpStepCount(){return target;}
 static class MyUtils {static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}static int hour=12;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.set(2026,Calendar.OCTOBER,13,hour,0,0);return c;}}
 static class Log {static List<String> logs=new ArrayList<>();static String getFormatTime(){return "12:00:00";}static void record(String s){logs.add(s);}static void i(String t,String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class UserIdMap {static String uid="private credential must not be logged";static String getCurrentUid(){return uid;}}
 static class ApplicationHook {static boolean offline;static boolean isOffline(){return offline;}}
 static class TaskCancelledException extends RuntimeException {}
 static class TimeUtil {static void sleep(long n){}}
 static ThreadLocal<Integer> syncStepTarget=new ThreadLocal<>();
 @@BOOT@@
 @@COLLECT@@
 @@INIT@@
 @@UPLOAD_HOOK@@
 @@REPLY_VALUE@@
 @@PAGE_QUERY@@
 @@NATIVE_ZONE@@
 @@INVOKE@@
 static class LegacyManager {public static final LegacyManager INSTANCE=new LegacyManager();public static LegacyManager getInstance(){return INSTANCE;}}
 static class CancelManager {public boolean a(int step,boolean back,String biz){throw new TaskCancelledException();}}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  StepSyncSourcesCheck s=new StepSyncSourcesCheck();s.boot(StepSyncSourcesCheck.class.getClassLoader());
  String agent="com.alibaba.health.pedometer.core.datasource.PedometerAgent",sensor="com.alibaba.health.pedometer.core.datasource.sensor.core.SensorPedometer";
  check(Hooks.read(agent,0)==22004,"legacy aggregate hook must remain supported");
  check(Hooks.read(sensor,0)==22004,"new stepDataList reads the underlying sensor, not the aggregate step argument");
  check(Hooks.read(sensor,19000)==19000,"preserve the existing high-step boundary");
  MyUtils.hour=0;check(Hooks.read(sensor,1000)==1000,"midnight must not override before configured sync time");
  MyUtils.hour=12;s.target=0;check(Hooks.read(sensor,1000)==1000,"disabled custom steps must remain unchanged");s.target=22004;
  XC_MethodHook.MethodHookParam broken=new XC_MethodHook.MethodHookParam(null,new IllegalStateException("sensor error"));
  Hooks.after(sensor,broken);check(broken.hasThrowable()&&broken.getResult()==null,"a source read failure must not become successful fake steps");
  com.alibaba.health.pedometer.intergation.rpc.RpcManager registered=new com.alibaba.health.pedometer.intergation.rpc.RpcManager();registered.initialized=true;
  com.alibaba.health.pedometer.core.proxy.api.HealthProxy.receiver=registered;
  List<Object> receivers=collectRpcManagerInstances(registered.getClass());
  check(receivers.size()==1&&receivers.get(0)==registered,"reuse the registered native manager rather than a new empty instance");
  check(registered.a(22004,false,"system")&&registered.uploaded==22004,"new multi-source upload must contain configured source steps");
  Log.logs.clear();check(invokeSyncStepMethod(registered.getClass().getMethod("a",int.class,boolean.class,String.class),registered,22004),"instrumented native upload must succeed");
  check(Log.logs.stream().anyMatch(x->x.contains("宿主上传回执")&&x.contains("statusCode=100")&&x.contains("userDailyCount=22004")),"native reply status/count must be observable without response body");
  check(syncStepTarget.get()==null,"native sync logging scope must be released");
  check(Log.logs.stream().noneMatch(x->x.contains("private credential")),"native response memo must not be logged");
  Log.logs.clear();s.target=0;check(!invokeSyncStepMethod(registered.getClass().getMethod("a",int.class,boolean.class,String.class),registered,22004),"server rejection must remain failure");
  check(Log.logs.stream().anyMatch(x->x.contains("success=false")&&x.contains("statusCode=400")),"server rejection must have business status");s.target=22004;
  Log.logs.clear();syncStepTarget.set(22004);Hooks.after("com.alibaba.health.pedometer.intergation.rpc.RpcClient.send",new XC_MethodHook.MethodHookParam(null,null));
  check(Log.logs.stream().anyMatch(x->x.contains("未返回回执")&&x.contains("服务器结果未确认")),"empty upload receipt must not claim server acceptance");
  Log.logs.clear();Hooks.after("com.alibaba.health.pedometer.intergation.rpc.RpcClient.send",new XC_MethodHook.MethodHookParam(null,new IllegalStateException("private credential")));syncStepTarget.remove();
  check(Log.logs.stream().anyMatch(x->x.contains("上传接口抛出异常"))&&Log.logs.stream().noneMatch(x->x.contains("private credential")),"native upload errors must be observable and redacted");
  Log.logs.clear();registered.a(22004,false,"system");check(Log.logs.isEmpty(),"ordinary host calls must not flood automatic-task logs");
  com.alibaba.health.pedometer.core.proxy.api.HealthProxy.receiver=null;
  receivers=collectRpcManagerInstances(registered.getClass());
  check(com.alibaba.health.pedometer.core.PedometerSDK.ready&&com.alibaba.health.pedometer.intergation.PedometerSDKIntegration.calls==1,"before entering any page initialize the native SDK when no registered receiver exists");
  check(receivers.size()==1&&((com.alibaba.health.pedometer.intergation.rpc.RpcManager)receivers.get(0)).a(22004,false,"system"),"first background run must upload source steps using initialized native receiver");
  collectRpcManagerInstances(registered.getClass());check(com.alibaba.health.pedometer.intergation.PedometerSDKIntegration.calls==1,"initialized native SDK must not be restarted each round");
  try{invokeSyncStepMethod(CancelManager.class.getMethod("a",int.class,boolean.class,String.class),new CancelManager(),22004);throw new AssertionError("reflection cancellation swallowed");}catch(TaskCancelledException expected){}
  check(syncStepTarget.get()==null,"cancellation must release logging scope");
  List<Object> legacy=collectRpcManagerInstances(LegacyManager.class);check(legacy.contains(LegacyManager.INSTANCE),"legacy static singleton discovery must remain supported when registration is incompatible");
  ClassLoader loader=StepSyncSourcesCheck.class.getClassLoader();
  com.alipay.mobilelbs.rpc.stepcounter.StepProClientService pageClient=com.alipay.mobile.framework.service.common.RpcService.client;
  ApplicationHook.offline=true;queryLegacyPatrolSteps(loader);check(pageClient.calls==0,"offline patrol page read must not send native RPC");ApplicationHook.offline=false;
  TimeZone.setDefault(TimeZone.getTimeZone("GMT-11"));Log.logs.clear();queryLegacyPatrolSteps(loader);
  check(pageClient.calls==1&&pageClient.request.timezoneId.equals("Asia/Shanghai")&&pageClient.request.countDate.equals("2026-10-13"),"page step query must use a GMT+8 date and matching timezone");
  check(pageClient.request.appKey.equals("60000002")&&pageClient.request.os.equals("android")&&pageClient.request.appVersion.equals("Android")&&pageClient.request.userId.equals(UserIdMap.uid),"preserve the native single-date request contract and current account");
  check(Log.logs.stream().anyMatch(x->x.contains("用户步数=23426")&&x.contains("statusCode=104"))&&Log.logs.stream().noneMatch(x->x.contains("private credential")),"page user counter must be separate from device counter and redacted");
  pageClient.nullReply=true;Log.logs.clear();queryLegacyPatrolSteps(loader);check(Log.logs.stream().anyMatch(x->x.contains("未返回回执")&&x.contains("用户步数=未知")),"empty page reply cannot become zero or success");pageClient.nullReply=false;
  pageClient.missingCount=true;Log.logs.clear();queryLegacyPatrolSteps(loader);check(Log.logs.stream().anyMatch(x->x.contains("statusCode=104")&&x.contains("用户步数=未知")),"missing count cannot erase an actual server receipt or become zero");pageClient.missingCount=false;
  com.alibaba.health.pedometer.intergation.rpc.TimeZoneCompat.zone="Asia/Ho_Chi_Minh";pageClient.zeroShanghai=true;int beforeZoneReads=pageClient.calls;Log.logs.clear();queryLegacyPatrolSteps(loader);
  check(pageClient.calls==beforeZoneReads+1&&pageClient.request.timezoneId.equals("Asia/Ho_Chi_Minh")&&pageClient.request.countDate.equals("2026-10-13"),"formal page read must query the native timezone once, without an initial Shanghai comparison");
  check(Log.logs.stream().noneMatch(x->x.contains("时区=Asia/Shanghai"))&&Log.logs.stream().anyMatch(x->x.contains("时区=Asia/Ho_Chi_Minh")&&x.contains("用户步数=23426")),"only the effective timezone and user count should be logged");
  pageClient.nullReply=true;beforeZoneReads=pageClient.calls;queryLegacyPatrolSteps(loader);check(pageClient.calls==beforeZoneReads+1,"unknown first receipt must not issue another timezone query");pageClient.nullReply=false;
  pageClient.missingCount=true;beforeZoneReads=pageClient.calls;queryLegacyPatrolSteps(loader);check(pageClient.calls==beforeZoneReads+1,"missing first count must not issue another timezone query");pageClient.missingCount=false;
  com.alibaba.health.pedometer.intergation.rpc.TimeZoneCompat.zone="private credential";beforeZoneReads=pageClient.calls;Log.logs.clear();queryLegacyPatrolSteps(loader);check(pageClient.calls==beforeZoneReads+1&&Log.logs.stream().noneMatch(x->x.contains("private credential")),"invalid native timezone must not be logged or sent");
  com.alibaba.health.pedometer.intergation.rpc.TimeZoneCompat.zone="Asia/Shanghai";beforeZoneReads=pageClient.calls;Log.logs.clear();queryLegacyPatrolSteps(loader);check(pageClient.calls==beforeZoneReads+1&&Log.logs.stream().anyMatch(x->x.contains("用户步数=0")),"a valid zero count must remain zero and must not trigger another timezone query");pageClient.zeroShanghai=false;
  ClassLoader noZone=new ClassLoader(loader){@Override public Class<?> loadClass(String name)throws ClassNotFoundException{if(name.endsWith(".TimeZoneCompat"))throw new ClassNotFoundException(name);return super.loadClass(name);}};
  beforeZoneReads=pageClient.calls;Log.logs.clear();queryLegacyPatrolSteps(noZone);check(pageClient.calls==beforeZoneReads+1&&Log.logs.stream().anyMatch(x->x.contains("时区=Asia/Shanghai"))&&Log.logs.stream().noneMatch(x->x.contains("ClassNotFoundException")),"optional timezone lookup cannot suppress the existing page query");
  int oldCalls=pageClient.calls;com.alibaba.health.pedometer.intergation.rpc.CommonUtil.uid="other private account";Log.logs.clear();queryLegacyPatrolSteps(loader);
  check(pageClient.calls==oldCalls&&Log.logs.stream().anyMatch(x->x.contains("未发送查询"))&&Log.logs.stream().noneMatch(x->x.contains("private account")),"account mismatch must not query with another host account");com.alibaba.health.pedometer.intergation.rpc.CommonUtil.uid=UserIdMap.uid;
  ClassLoader noPageQuery=new ClassLoader(loader){@Override public Class<?> loadClass(String name)throws ClassNotFoundException{if(name.contains("StepProSingleQuery"))throw new ClassNotFoundException(name);return super.loadClass(name);}};
  Log.logs.clear();queryLegacyPatrolSteps(noPageQuery);check(pageClient.calls==oldCalls&&Log.logs.isEmpty(),"missing optional native page query entry must stay silent");
  Hooks.uploadError=new TaskCancelledException();try{queryLegacyPatrolSteps(loader);throw new AssertionError("page read cancellation swallowed");}catch(TaskCancelledException expected){}Hooks.uploadError=null;
  Hooks.uploadError=new IllegalStateException("private credential");Log.logs.clear();queryLegacyPatrolSteps(loader);check(Log.logs.stream().anyMatch(x->x.contains("读取异常"))&&Log.logs.stream().noneMatch(x->x.contains("private credential")),"failed page query must leave patrol available and redact exception messages");Hooks.uploadError=null;
  Hooks.missing=sensor;Hooks.callbacks.clear();s.boot(StepSyncSourcesCheck.class.getClassLoader());
  check(Hooks.read(agent,0)==22004,"missing optional source class must not disable legacy aggregate hook");
  System.out.println("PASS native steps: sources, registered receiver, SDK init, single-timezone page read, redacted receipts, cancellation, GMT+8 and legacy compatibility");
 }
}
class Parent {public void boot(ClassLoader loader){}}
'''
code=code.replace("@@INIT@@",method(source,"private static void initializeStepSdk(")).replace("@@BOOT@@",method(source,"public void boot(")).replace("@@COLLECT@@",method(source,"private static List<Object> collectRpcManagerInstances("))
for marker,signature in [("@@NATIVE_ZONE@@","public static String nativeStepTimezone("),("@@PAGE_QUERY@@","public static void queryLegacyPatrolSteps("),("@@UPLOAD_HOOK@@","private static void hookStepUploadResult("),("@@REPLY_VALUE@@","private static String stepReplyValue("),("@@INVOKE@@","private static boolean invokeSyncStepMethod(")]:
 code=code.replace(marker,method(source,signature))
files={
 "com/alibaba/health/pedometer/core/PedometerSDK.java":'package com.alibaba.health.pedometer.core;public class PedometerSDK {public static boolean ready;public static boolean hasInitialized(){return ready;}}',
 "com/alipay/mobile/framework/MicroApplicationContext.java":'package com.alipay.mobile.framework;public class MicroApplicationContext {public Object findServiceByInterface(String name){return new com.alipay.mobile.framework.service.common.RpcService();}}',
 "com/alipay/mobile/framework/LauncherApplicationAgent.java":'package com.alipay.mobile.framework;public class LauncherApplicationAgent {static final LauncherApplicationAgent instance=new LauncherApplicationAgent();public static LauncherApplicationAgent getInstance(){return instance;}public MicroApplicationContext getMicroApplicationContext(){return new MicroApplicationContext();}}',
 "com/alibaba/health/pedometer/intergation/PedometerSDKIntegration.java":'package com.alibaba.health.pedometer.intergation;public class PedometerSDKIntegration {public static int calls;public static void initialize(com.alipay.mobile.framework.MicroApplicationContext c){if(c==null)throw new AssertionError();calls++;com.alibaba.health.pedometer.core.PedometerSDK.ready=true;com.alibaba.health.pedometer.intergation.rpc.RpcManager m=new com.alibaba.health.pedometer.intergation.rpc.RpcManager();m.initialized=true;com.alibaba.health.pedometer.core.proxy.api.HealthProxy.receiver=m;}}',

 "StepSyncSourcesCheck.java":code,
 "checks/stub/XC_MethodHook.java":'''package checks.stub;public abstract class XC_MethodHook {protected void afterHookedMethod(MethodHookParam p){}public final void after(MethodHookParam p){afterHookedMethod(p);}public static class MethodHookParam {Object result;Throwable error;public MethodHookParam(Object r,Throwable t){result=r;error=t;}public Object getResult(){return result;}public boolean hasThrowable(){return error!=null;}public Throwable getThrowable(){return error;}public void setResult(Object r){result=r;error=null;}}}''',
 "checks/stub/Hooks.java":'''package checks.stub;import java.util.*;public class Hooks {public static Map<String,XC_MethodHook> callbacks=new HashMap<>();public static String missing="";public static RuntimeException uploadError;public static int read(String c,int n){XC_MethodHook.MethodHookParam p=new XC_MethodHook.MethodHookParam(n,null);after(c,p);return (Integer)p.getResult();}public static void after(String c,XC_MethodHook.MethodHookParam p){if(callbacks.containsKey(c))callbacks.get(c).after(p);}}''',
 "checks/stub/XHelpers.java":'''package checks.stub;public class XHelpers {public static void findAndHookMethod(Class<?> c,String m,Object... a){Hooks.callbacks.put(c.getName()+"."+m,(XC_MethodHook)a[a.length-1]);}public static void findAndHookMethod(String c,ClassLoader l,String m,XC_MethodHook h)throws Throwable{if(c.equals(Hooks.missing))throw new ClassNotFoundException(c);Hooks.callbacks.put(c,h);}}''',
 "com/alibaba/health/pedometer/core/proxy/SyncStepRecordProxy.java":'''package com.alibaba.health.pedometer.core.proxy;public interface SyncStepRecordProxy {}''',
 "com/alibaba/health/pedometer/core/proxy/api/HealthProxy.java":'''package com.alibaba.health.pedometer.core.proxy.api;public class HealthProxy {public static Object receiver;public static Object get(Class<?> t){return receiver;}}''',
 "com/alibaba/health/pedometer/intergation/rpc/RpcManager.java":'''package com.alibaba.health.pedometer.intergation.rpc;import checks.stub.Hooks;public class RpcManager implements com.alibaba.health.pedometer.core.proxy.SyncStepRecordProxy {public boolean initialized;public int uploaded;public boolean a(int aggregate,boolean back,String biz){if(back||!"system".equals(biz))throw new AssertionError("changed upload contract");uploaded=Hooks.read("com.alibaba.health.pedometer.core.datasource.sensor.core.SensorPedometer",0);return initialized&&RpcClient.send(uploaded,back,biz,null).success;}}''',
 "com/alibaba/health/pedometer/intergation/rpc/StepProSyncResponsePB.java":'''package com.alibaba.health.pedometer.intergation.rpc;public class StepProSyncResponsePB {public Boolean success;public Integer statusCode,authStatus,userDailyCount;public String statusMemo="private credential must not be logged";}''',
 "com/alibaba/health/pedometer/intergation/rpc/CommonUtil.java":'package com.alibaba.health.pedometer.intergation.rpc;public class CommonUtil {public static String uid="private credential must not be logged";public static String getUserId(){return uid;}public static String getDeviceId(){return "private credential";}}',
 "com/alibaba/health/pedometer/intergation/rpc/TimeZoneCompat.java":'package com.alibaba.health.pedometer.intergation.rpc;public class TimeZoneCompat {public static String zone="Asia/Shanghai";public static String a(){return zone;}}',
 "com/alipay/mobile/framework/service/common/RpcService.java":'package com.alipay.mobile.framework.service.common;public class RpcService {public static com.alipay.mobilelbs.rpc.stepcounter.StepProClientService client=new com.alipay.mobilelbs.rpc.stepcounter.StepProClientService();public Object getPBRpcProxy(Class<?> type){return client;}}',
 "com/alipay/mobilelbs/rpc/stepcounter/StepProSingleQueryRequestPB.java":'package com.alipay.mobilelbs.rpc.stepcounter;public class StepProSingleQueryRequestPB {public String appKey,os,appVersion,userId,deviceId,timezoneId,countDate;}',
 "com/alipay/mobilelbs/rpc/stepcounter/StepProSingleQueryResponsePB.java":'package com.alipay.mobilelbs.rpc.stepcounter;public class StepProSingleQueryResponsePB {public Boolean success=true;public Integer statusCode=104;public String statusMemo="private credential";public ProStepDataInfoPB stepDataInfo=new ProStepDataInfoPB();}',
 "com/alipay/mobilelbs/rpc/stepcounter/ProStepDataInfoPB.java":'package com.alipay.mobilelbs.rpc.stepcounter;public class ProStepDataInfoPB {public Integer count=23426;}',
 "com/alipay/mobilelbs/rpc/stepcounter/StepProClientService.java":'package com.alipay.mobilelbs.rpc.stepcounter;public class StepProClientService {public int calls;public boolean nullReply,missingCount,zeroShanghai;public StepProSingleQueryRequestPB request;public StepProSingleQueryResponsePB querySingleDateStep(StepProSingleQueryRequestPB r){calls++;request=r;if(checks.stub.Hooks.uploadError!=null)throw checks.stub.Hooks.uploadError;if(nullReply)return null;StepProSingleQueryResponsePB reply=new StepProSingleQueryResponsePB();if(missingCount)reply.stepDataInfo=null;else if(zeroShanghai&&r.timezoneId.equals("Asia/Shanghai"))reply.stepDataInfo.count=0;return reply;}}',
 "com/alibaba/health/pedometer/intergation/rpc/RpcClient.java":'''package com.alibaba.health.pedometer.intergation.rpc;import checks.stub.*;public class RpcClient { public static StepProSyncResponsePB send(int count,boolean back,String biz,Object base){StepProSyncResponsePB r=new StepProSyncResponsePB();r.success=count>=2000;r.statusCode=r.success?100:400;r.authStatus=1;r.userDailyCount=count;Hooks.after(RpcClient.class.getName()+".send",new XC_MethodHook.MethodHookParam(r,null));return r;}}'''
}
request_code=r'''
import org.json.*;
public class LegacyProtocolRequestCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){return new JSONObject(s);}static java.util.Calendar getInstance(){java.util.Calendar c=java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8"));c.set(java.util.Calendar.HOUR_OF_DAY,12);return c;}}
 static class AntSports {static String zone="Asia/Ho_Chi_Minh";static String nativeStepTimezone(ClassLoader loader){return zone;}}
 static class ApplicationHook {static String method,args;static boolean missingSteps;static ClassLoader getClassLoader(){return LegacyProtocolRequestCheck.class.getClassLoader();}static String requestString(String m,String a){method=m;args=a;if(m.endsWith("steps.query")){JSONObject r=new JSONObject().put("resultCode","SUCCESS").put("success",true);if(!missingSteps)r.put("stepCount",new JSONArray(a).optJSONObject(0).optString("timeZone").equals("Asia/Ho_Chi_Minh")?23739:0);return r.toString();}return "{}";}}
 static class Log {static void record(String s){}static String getFormatTime(){return "12:00:00";}static void other(String s){}static void err(String tag,String message,Throwable t){throw new AssertionError(t);}}
 static class Status {static boolean done;static boolean hasFlagToday(String key){return done;}static void flagToday(String key){done=true;}}
 static class MessageUtil {static boolean checkResultCode(String tag,JSONObject r){return "SUCCESS".equals(r.optString("resultCode"));}}
 static class AntSportsRpcCall {static int donated,reported;static String queryWalkStep()throws JSONException{return LegacyProtocolRequestCheck.queryWalkStep();}static String queryWalkStep(String zone)throws JSONException{return LegacyProtocolRequestCheck.queryWalkStep(zone);}static void walkDonateSignInfo(int step){reported=step;}static String donateWalkHome(int step){return new JSONObject().put("resultCode","SUCCESS").put("walkDonateHomeModel",new JSONObject().put("walkUserInfoModel",new JSONObject().put("exchangeFlag",true))).toString();}static String donateWalkExchange(String activity,int step,String token){donated=step;return new JSONObject().put("resultCode","SUCCESS").put("donateExchangeResultModel",new JSONObject()).toString();}}
 static class DonationFlow {static final String TAG="test";static class Field {int value;Field(int n){value=n;}int getValue(){return value;}}Field minExchangeCount=new Field(20000),latestExchangeTime=new Field(23);boolean canDonateWalkExchangeToday(){return true;}static String nativeStepTimezone(ClassLoader loader){return AntSports.nativeStepTimezone(loader);}@@DONATION_FLOW@@}
 @@REQUESTS@@
 @@WALK_REQUESTS@@
 static JSONObject payload(String operation){assert ApplicationHook.method.endsWith(operation);JSONArray a=new JSONArray(ApplicationHook.args);assert a.length()==1;JSONObject j=a.optJSONObject(0);assert j!=null&&j.optString("source").equals("ant_forest");assert j.optString("timezoneId").equals(AntSports.zone==null?"Asia/Shanghai":AntSports.zone):"patrol read/exchange/advance must use native step timezone";return j;}
 public static void main(String[] ignored)throws JSONException{
  for(String zone:new String[]{"Asia/Ho_Chi_Minh","Asia/Shanghai",null}){
   AntSports.zone=zone;queryUserPatrol();assert payload("queryUserPatrol").length()==2;
   queryMyPatrolRecord();payload("queryMyPatrolRecord");
   switchUserPatrol("12345");assert payload("switchUserPatrol").opt("targetPatrolId") instanceof Number&&payload("switchUserPatrol").optLong("targetPatrolId")==12345;
   patrolGo(29,2);JSONObject go=payload("patrolGo");assert go.optInt("nodeIndex")==29&&go.optInt("patrolId")==2;
   for(String event:new String[]{"video","chase","quiz","image"}){
    patrolKeepGoing(30,2,event);JSONObject p=payload("patrolKeepGoing"),react=p.optJSONObject("reactParam");assert p.optInt("nodeIndex")==30&&p.optInt("patrolId")==2&&react!=null;
    assert event.equals("video")?react.optString("viewed").equals("Y"):event.equals("chase")?react.optString("sendChat").equals("Y"):event.equals("quiz")?react.optString("answer").equals("correct"):react.length()==0;
   }
   exchangePatrolChance(23660);assert payload("exchangePatrolChance").optInt("costStep")==23660;
  }
  for(String bad:new String[]{"bad",null,"12345678901234567890"}){String before=ApplicationHook.args;try{switchUserPatrol(bad);throw new AssertionError("invalid map must not send");}catch(IllegalArgumentException expected){}assert ApplicationHook.args.equals(before);}
  queryWalkStep();assert new JSONArray(ApplicationHook.args).optJSONObject(0).optString("timeZone").equals("Asia/Shanghai"):"original donation query must retain its timezone";
  queryWalkStep("Asia/Ho_Chi_Minh");JSONObject walk=new JSONArray(ApplicationHook.args).optJSONObject(0);assert walk.optString("timeZone").equals("Asia/Ho_Chi_Minh")&&walk.optString("appId").equals("healthstep")&&walk.optString("bizId").equals("donation")&&walk.optString("chInfo").equals("h5_donation_healthstep");
  queryWalkStep((String)null);assert new JSONArray(ApplicationHook.args).optJSONObject(0).optString("timeZone").equals("Asia/Shanghai");
  java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT-11"));AntSports.zone="Asia/Ho_Chi_Minh";new DonationFlow().queryWalkStep();assert AntSportsRpcCall.donated==23739&&AntSportsRpcCall.reported==23739&&Status.done:"donation flow must use the host timezone bucket, independent of the business timezone";
  Status.done=false;AntSportsRpcCall.donated=0;ApplicationHook.missingSteps=true;DonationFlow late=new DonationFlow();late.latestExchangeTime.value=11;late.queryWalkStep();assert AntSportsRpcCall.donated==0&&!Status.done:"missing steps must not become a zero-step donation after deadline";ApplicationHook.missingSteps=false;
  System.out.println("PASS legacy patrol request contract: native timezone for query/exchange/advance/sync read; numeric IDs, reactions, full cost and original donation query preserved");
 }
}
'''
patrol_source="model/task/antForest/AntForestRpcCall.java"
try:
 legacy_helper=method(patrol_source,"private static JSONObject legacyPatrolPayload(")
except ValueError:
 legacy_helper=""
request_methods=["public static String queryUserPatrol(","public static String queryMyPatrolRecord(","public static String switchUserPatrol(","public static String patrolGo(","public static String patrolKeepGoing(","public static String exchangePatrolChance("]
request_code=request_code.replace("@@REQUESTS@@",legacy_helper+"\n"+"\n".join(method(patrol_source,s) for s in request_methods))
request_code=request_code.replace("@@WALK_REQUESTS@@",method("model/task/antSports/AntSportsRpcCall.java","public static String queryWalkStep()")+method("model/task/antSports/AntSportsRpcCall.java","public static String queryWalkStep(String timezoneId)"))
request_code=request_code.replace("@@DONATION_FLOW@@",method(source,"private void queryWalkStep("))
files["LegacyProtocolRequestCheck.java"]=request_code
cache=Path(os.environ.get("GRADLE_USER_HOME",str(Path.home()/".gradle")))
json_jar=str(sorted((cache/"caches/modules-2/files-2.1/org.json/json").glob("*/*/*.jar"))[-1])
with tempfile.TemporaryDirectory(prefix="sesame-step-sources-") as tmp:
 paths=[]
 for name,body in files.items():
  p=Path(tmp)/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,encoding="utf-8");paths.append(str(p))
 subprocess.run(["javac","-encoding","UTF-8","-cp",json_jar,"-d",tmp,*paths],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+json_jar,"StepSyncSourcesCheck"],check=True,timeout=30)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+json_jar,"LegacyProtocolRequestCheck"],check=True,timeout=30)
