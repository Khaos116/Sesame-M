"""Compile production AG task adapters against isolated RPC/storage fixtures, no live APIs."""
from pathlib import Path
import os, re, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import SOURCE, method

def nested(path, name):
    raw = (SOURCE / path).read_text(encoding='utf-8')
    raw = re.sub(r'^(package|import) .*?;\s*', '', raw, flags=re.M)
    return raw.replace('public final class '+name, 'static final class '+name).replace('final class '+name, 'static final class '+name).replace('static static ', 'static ')

code = r'''
import java.util.*;import org.json.*;
public class AgTaskSyncCheck {
 static class MyUtils {static int day;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.add(Calendar.DATE,day);return c;}
  static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TaskCancelledException extends RuntimeException{}
 static class TaskLifecycle {static class Work implements AutoCloseable{public void close(){}}static Work enter(){return new Work();}}
 static class TimeUtil {static boolean cancel,cancelWait,rollDay;static List<Long> waits=new ArrayList<>();static void sleep(long n){if(cancel||cancelWait&&n>1000)throw new TaskCancelledException();if(rollDay&&n>1000){MyUtils.day++;rollDay=false;}waits.add(n);}}
 static class Log {static List<String> lines=new ArrayList<>();static List<String> category=new ArrayList<>();static void other(String s){category.add(s);}static void record(String s){lines.add(s);}static void err(String a,String b,Throwable t){throw new AssertionError(b,t);}}
 static class RuntimeInfo {static String uid="A";static Map<String,RuntimeInfo> accounts=new HashMap<>();Map<String,String> data=new HashMap<>();static boolean fail;
  static RuntimeInfo getInstance(){return accounts.computeIfAbsent(uid,k->new RuntimeInfo());}String getString(String k){return data.getOrDefault(k,"");}List<String> keysStartingWith(String prefix){return new ArrayList<>(data.keySet().stream().filter(k->k.startsWith(prefix)).toList());}
  boolean putVerified(String k,String v){if(fail)return false;data.put(k,v);return true;}void put(String k,Object v){if(v==null)data.remove(k);else data.put(k,v.toString());}}
 static class Status {static Set<String> flags=new HashSet<>();static String key(String k){return RuntimeInfo.uid+":"+MyUtils.day+":"+k;}static boolean hasFlagToday(String k){return flags.contains(key(k));}static void flagToday(String k){flags.add(key(k));}}
 static class DailyTask {static boolean manual;static boolean isManual(){return manual;}}
 static class MessageUtil {static boolean isRetryable(JSONObject j){return "3000".equals(j.optString("resultCode"));}static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}static int rejected;static void checkResultCodeAndMarkTaskBlackList(String list,String title,JSONObject j){assert list.equals("AntMemberTaskList");if("UNSUPPORTED".equals(j.optString("resultCode")))rejected++;}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"))||j.has("error")||"RPC_SKIPPED".equals(j.optString("resultCode"));}}
 static class GameTask {static int writes,result=2;static GameTask matchAppId(String id){return "2060170000000001".equals(id)?new GameTask():null;}int reportSync(String label,int n,String channel){writes++;return result;}}
 static class Uri {java.net.URI value;static Uri parse(String s){Uri u=new Uri();u.value=java.net.URI.create(s);return u;}boolean isHierarchical(){return !value.isOpaque();}
  Set<String> getQueryParameterNames(){Set<String> names=new LinkedHashSet<>();if(value.getRawQuery()!=null)for(String p:value.getRawQuery().split("&"))names.add(decode(p.split("=",2)[0]));return names;}
  String getQueryParameter(String key){if(value.getRawQuery()!=null)for(String p:value.getRawQuery().split("&")){String[] pair=p.split("=",2);if(decode(pair[0]).equals(key))return pair.length==1?"":decode(pair[1]);}return null;}
  static String decode(String s){return java.net.URLDecoder.decode(s,java.nio.charset.StandardCharsets.UTF_8);}}
 static class ApplicationHook {static List<JSONObject> calls=new ArrayList<>();static String reply="{success:true}";static boolean transport;
  static String requestString(String rpc,String raw,int tries,int delay){assert tries==1&&delay==0;return requestString(rpc,raw);}
  static String requestString(String rpc,String raw){JSONObject j=new JSONArray(raw).optJSONObject(0);calls.add(j.put("rpc",rpc));if(transport)throw new IllegalStateException();return reply;}}
 @@GAME@@
 static class AntMemberRpcCall {
  static Map<String,JSONObject> tasks=new LinkedHashMap<>();static List<Integer> pages=new ArrayList<>();static Set<String> sessions=new HashSet<>();static int apply,execute;static boolean advance=true,emptyMap,repeatPage,brokenDetail,omitConfig,statusOnly,splitPages;static String applyReply,pageOverride,executeReply="{success:true}";
  static String response(JSONArray rows){return new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVOList",rows)).toString();}
  static String signPageTaskList(int page,String session){pages.add(page);sessions.add(session);if(pageOverride!=null)return pageOverride;if(splitPages)return response(page<=tasks.size()?new JSONArray().put(new ArrayList<>(tasks.values()).get(page-1)):new JSONArray());return response(page==1||repeatPage?new JSONArray(tasks.values()):new JSONArray());}
  static String currentMemberAdTasks(){return response(new JSONArray());}static String currentMemberProcesses(){return response(new JSONArray(tasks.values()));}
  static String applyCurrentMemberTask(String id,boolean batch){apply++;if(applyReply!=null)return applyReply;JSONObject t=tasks.get(id);if(!emptyMap)t.put("processId","p"+id);return new JSONObject().put("success",true).put("resultData",new JSONObject().put("resultMap",emptyMap?new JSONObject():new JSONObject().put(id,"SUCCESS")).put("taskProcessVO",batch?JSONObject.NULL:new JSONObject(t.toString()))).toString();}
  static String currentMemberTaskDetail(String id){JSONObject row=tasks.values().stream().filter(t->id.equals(t.optString("processId"))).findFirst().orElse(null);if(row!=null&&omitConfig){row=new JSONObject(row.toString());row.remove("simpleTaskConfig");}return brokenDetail?"{}":new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",row)).toString();}
  static String executeCurrentMemberTask(String type,String param){execute++;if(statusOnly)tasks.get(param).put("status","PROCESSING");else if(advance){JSONObject row=tasks.get(param);int n=row.optInt("currentCount")+1;row.put("currentCount",n);if(n>=row.optInt("targetCount"))row.put("status","FINISHED");}return executeReply;}
 }
 static class AntMemberTaskListMap {static Map<String,String> names=new HashMap<>();static Map<String,String> getMap(){return names;}static void add(String k,String v){names.put(k,v);}static void save(){}}
 static class RpcContract { @@RPC@@ }
 @@MEMBER@@
 static void reset(){RuntimeInfo.accounts.clear();RuntimeInfo.uid="A";RuntimeInfo.fail=false;ApplicationHook.calls.clear();ApplicationHook.reply="{success:true}";ApplicationHook.transport=false;MyUtils.day=0;TimeUtil.cancel=TimeUtil.cancelWait=TimeUtil.rollDay=false;TimeUtil.waits.clear();GameTask.writes=0;GameTask.result=2;DailyTask.manual=false;Status.flags.clear();MessageUtil.rejected=0;Log.category.clear();}
 static JSONObject game(int seconds){return new JSONObject().put("gameAppId","2060170000000002").put("source","channel").put("browseSeconds",seconds);}
 static void games() throws Exception {
  for(int reports:new int[]{@@LEGACY_COUNTS@@}){
   reset();GameTask.result=reports;GameTaskAction legacy=GameTaskAction.parse(new JSONObject().put("appId","2060170000000001"));
   assert legacy.perform("legacy","test",null)&&legacy.perform("legacy","test",null)&&GameTask.writes==1:"reportSync returns requests, not eggs";
  }
  reset();GameTask.result=0;GameTaskAction zero=GameTaskAction.parse(new JSONObject().put("appId","2060170000000001"));assert !zero.perform("zero","test",null)&&!zero.perform("zero","test",null)&&GameTask.writes==1;
  reset();GameTaskAction task=GameTaskAction.parse(game(65));assert task.kind==GameTaskAction.Kind.DURATION;
  assert task.perform("task#0","test","channel");assert ApplicationHook.calls.size()==3;assert ApplicationHook.calls.get(2).optInt("playTime")==5;
  assert TimeUtil.waits.stream().mapToLong(Long::longValue).sum()>=64900;task.perform("task#0","test","channel");assert ApplicationHook.calls.size()==3;
  RuntimeInfo.uid="B";task.perform("task#0","test","channel");assert ApplicationHook.calls.size()==6;
  reset();ApplicationHook.reply="{}";assert !task.perform("task#0","test","channel");assert !task.perform("task#0","test","channel")&&ApplicationHook.calls.size()==1;
  reset();TimeUtil.cancel=true;try{task.perform("task#0","test","channel");throw new AssertionError();}catch(TaskCancelledException expected){}assert ApplicationHook.calls.isEmpty();
  TimeUtil.cancel=false;assert task.perform("task#0","test","channel");assert ApplicationHook.calls.size()==3;
  reset();ApplicationHook.transport=true;assert !task.perform("transport","test",null);assert !task.perform("transport","test",null)&&ApplicationHook.calls.size()==1;
  reset();RuntimeInfo.fail=true;assert !task.perform("task#0","test","channel")&&ApplicationHook.calls.isEmpty();
  reset();TimeUtil.cancelWait=true;try{task.perform("resume","test",null);throw new AssertionError();}catch(TaskCancelledException expected){}assert ApplicationHook.calls.isEmpty();
  TimeUtil.cancelWait=false;assert task.perform("resume","test",null);assert ApplicationHook.calls.size()==3;
  assert !GameTaskAction.parse(game(66)).perform("resume","test",null)&&ApplicationHook.calls.size()==3:"changed contract must not reuse acceptance";
  reset();TimeUtil.rollDay=true;assert !task.perform("midnight","test",null)&&ApplicationHook.calls.isEmpty():"cross-day timer sent stale action";
  for(Object invalid:new Object[]{-1,1801,0.5,"bad",JSONObject.NULL})assert GameTaskAction.parse(game(1).put("browseSeconds",invalid)).kind==GameTaskAction.Kind.DEFERRED;
  assert GameTaskAction.parse(game(30).put("taskMode","COUNT_DOWN").put("countDownSeconds",15)).kind==GameTaskAction.Kind.COUNTDOWN;
  assert GameTaskAction.parse(game(30).put("gameTaskType","chongzhi")).kind==GameTaskAction.Kind.DEFERRED;
  assert GameTaskAction.parse(game(30).put("floatingBallTypeList","P2E_GAME_BROWSE_TASK_FLOATING_BALL")).kind==GameTaskAction.Kind.DEFERRED;
  assert GameTaskAction.parse(new JSONObject().put("appId","20000001")).kind==GameTaskAction.Kind.NONE;
  assert GameTaskAction.parse(new JSONObject().put("appId","2060170000000001")).kind==GameTaskAction.Kind.LEGACY;
  JSONObject nested=new JSONObject().put("bizInfo",new JSONObject().put("targetUrl","alipays://platformapi/startapp?query=gameAppId%3D2060170000000002%26source%3Dchannel%26browseSeconds%3D30").toString());
  assert GameTaskAction.parse(nested).kind==GameTaskAction.Kind.DURATION;
 }
 static JSONObject member(String id,boolean applied){return new JSONObject().put("taskConfigId",id).put("processId",applied?"p"+id:"").put("status","TODO").put("currentCount",0).put("targetCount",1).put("simpleTaskConfig",new JSONObject().put("configId",id).put("title","task"+id).put("sourceBusiness","signInAd").put("browseSeconds",15).put("targetBusiness",new JSONArray().put("BROWSE#sub#"+id)));}
 static void resetMember(){reset();AntMemberRpcCall.tasks.clear();AntMemberRpcCall.pages.clear();AntMemberRpcCall.sessions.clear();AntMemberRpcCall.apply=AntMemberRpcCall.execute=0;AntMemberRpcCall.advance=true;AntMemberRpcCall.emptyMap=AntMemberRpcCall.repeatPage=AntMemberRpcCall.brokenDetail=AntMemberRpcCall.omitConfig=AntMemberRpcCall.statusOnly=AntMemberRpcCall.splitPages=false;AntMemberRpcCall.applyReply=null;AntMemberRpcCall.pageOverride=null;AntMemberRpcCall.executeReply="{success:true}";}
 static void members()throws Exception{
  for(String invalid:List.of("", "not-a-contract", "BROWSE##one", "BROWSE#sub#")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",invalid);AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(Set.of());
   assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"invalid top-level string hid valid config target: "+invalid;
  }
  for(String invalid:List.of("", "not-a-contract", "BROWSE##one", "BROWSE#sub#")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",invalid);row.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put("BROWSE##one"));AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"invalid primary and fallback targets issued a write";
  }
  resetMember();JSONObject blacklisted=member("one",false).put("targetBusiness","");AntMemberRpcCall.tasks.put("one",blacklisted);MemberBrowseTasks.run(Set.of("taskone"));assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"fallback bypassed task blacklist";
  JSONObject priority=member("one",false).put("targetBusiness","BROWSE#top#one");assert MemberBrowseTasks.target(priority)[1].equals("top"):"valid top-level contract lost priority";

  for(String key:List.of("simpleTaskConfig","taskConfigInfo")) {
   resetMember();JSONObject row=member("one",false);JSONObject cfg=row.optJSONObject("simpleTaskConfig");cfg.remove("targetBusiness");
   if(key.equals("taskConfigInfo")){row.remove("simpleTaskConfig");row.put(key,cfg);cfg.put("id","one").put("name","taskone");}
   row.put("targetBusiness","BROWSE#sub#one");AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(Set.of());
   assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"string top-level target must use the existing verified browse flow";
  }
  resetMember();JSONObject stringConfig=member("one",true);stringConfig.optJSONObject("simpleTaskConfig").put("targetBusiness","BROWSE#sub#one");AntMemberRpcCall.tasks.put("one",stringConfig);MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1;
  for(String bad:List.of("CALL_APP#scene","CALL_APP#sub#one","OTHER#sub#one")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",bad);AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"invalid string target sent a write";
  }

  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1;assert AntMemberRpcCall.pages.contains(2);assert TimeUtil.waits.stream().mapToLong(Long::longValue).sum()>=15000;
  resetMember();JSONObject ordinary=member("one",false);ordinary.optJSONObject("simpleTaskConfig").put("sourceBusiness","antmember");AntMemberRpcCall.tasks.put("one",ordinary);MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"ordinary apply process must continue only once";
  resetMember();AntMemberRpcCall.repeatPage=true;AntMemberRpcCall.tasks.put("one",member("one",true));MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1&&Collections.max(AntMemberRpcCall.pages)==2;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));MemberBrowseTasks.run(Set.of("taskone"));assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.emptyMap=true;MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1:"Unknown execute must only requery";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.cancel=true;try{MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("one"),"test");throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.execute==0;
  TimeUtil.cancel=false;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.brokenDetail=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));RuntimeInfo.fail=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.omitConfig=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1:"detail without static config must use matched list config";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.statusOnly=true;MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1:"status text is not numeric progress";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.tasks.put("two",member("two",true));AntMemberRpcCall.splitPages=true;
  assert MemberBrowseTasks.load().size()==2&&AntMemberRpcCall.pages.equals(List.of(1,2,3))&&AntMemberRpcCall.sessions.size()==1:"all pages must share session";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.run(Set.of());throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.execute==0;
  TimeUtil.cancelWait=false;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.rollDay=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==0:"cross-day browser action";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true).put("targetCount",3));MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==3:"advance stages by verified count";
  resetMember();AntMemberRpcCall.tasks.put("old",member("one",true).put("processId","old").put("status","FINISHED"));AntMemberRpcCall.tasks.put("one",member("one",false));MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"finished history must not hide new task";
  assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",2),new JSONObject().put("currentCount",1));
  assert MemberBrowseTasks.advanced(new JSONObject().put("currentCount",2),new JSONObject().put("currentCount",3));
  assert MemberBrowseTasks.rows("{success:true,resultData:{taskProcessVOList:[null]}}") == null;
  assert MemberBrowseTasks.rows("{success:true,resultData:{categoryTaskList:[{taskProcessVOList:[]}]}}").isEmpty();
  resetMember();JSONObject two=member("one",true);two.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put("sub#one"));two.put("extInfo",new JSONObject().put("awardCurrentPoint",5));AntMemberRpcCall.tasks.put("one",two);MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1&&!Log.category.isEmpty()&&Log.category.get(0).contains("5");
  assert MemberBrowseTasks.target(member("one",false).put("targetBusiness",new JSONArray().put("CALL_APP#scene")))==null;
  for(String target:new String[]{"CALL_APP#scene", "CALL_APP#sub#param", "OTHER#sub#param", "BROWSE##param"}) {
   resetMember();JSONObject unsupported=member("one",false);unsupported.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put(target));
   AntMemberRpcCall.tasks.put("one",unsupported);MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());
   assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0 : "unsupported member task issued an RPC";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{success:false,resultCode:'UNSUPPORTED'}";MemberBrowseTasks.run(Set.of());assert MessageUtil.rejected==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{}";MemberBrowseTasks.run(Set.of());assert MessageUtil.rejected==0;
  reset();RpcContract.signPageTaskList(2,"session");JSONObject payload=ApplicationHook.calls.get(0);assert payload.optInt("pageNo")==2&&payload.optString("session").equals("session")&&payload.optBoolean("switchNormal");assert payload.optJSONObject("sourcePassMap").optString("source").equals("mytab");
  RpcContract.currentMemberProcesses();assert ApplicationHook.calls.get(1).optString("rpc").equals("com.alipay.alipaymember.biz.rpc.membertask.h5.queryTaskList");
  RpcContract.applyCurrentMemberTask("id",true);assert ApplicationHook.calls.get(2).optJSONArray("taskConfigIdList").optString(0).equals("id");
  RpcContract.executeCurrentMemberTask("sub","param");assert ApplicationHook.calls.get(3).optString("bizType").equals("BROWSE");
  RpcContract.currentMemberAdTasks();assert ApplicationHook.calls.get(4).length()==2&&!ApplicationHook.calls.get(4).has("sourcePassMap");
 }
 static void reviewRegressions()throws Exception{
  for(boolean resetCounts:new boolean[]{false,true}){
   resetMember();JSONObject row=member("one",true).put("currentCount",2).put("targetCount",5).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",2).put("PERIOD_TARGET_COUNT",5));AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.advance=false;
   MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1;
   MyUtils.day++;if(resetCounts){row.put("currentCount",0);row.optJSONObject("extInfo").put("PERIOD_CURRENT_COUNT",0);}MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==2:"new day must not remain blocked by old submitted/counts";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{success:false,resultCode:'NO_QUALIFICATION'}";
  MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==1;DailyTask.manual=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.execute==2;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.applyReply="{success:false,resultCode:'NO_QUALIFICATION'}";MemberBrowseTasks.run(Set.of());MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==1;DailyTask.manual=true;MemberBrowseTasks.run(Set.of());assert AntMemberRpcCall.apply==2;
  reset();ApplicationHook.reply="{success:false,resultCode:'NO_QUALIFICATION'}";GameTaskAction task=GameTaskAction.parse(game(1));task.perform("denied","test",null);task.perform("denied","test",null);assert ApplicationHook.calls.size()==1;DailyTask.manual=true;task.perform("denied","test",null);assert ApplicationHook.calls.size()==2;
  reset();ApplicationHook.reply="{success:false,resultCode:'102'}";task.perform("busy","test",null);task.perform("busy","test",null);assert ApplicationHook.calls.size()==2:"transient failure belongs to RPC guard";
  reset();assert GameTaskAction.waitCountdown("stall",15,"test");RuntimeInfo store=RuntimeInfo.getInstance();JSONObject timer=MyUtils.newJSONObject(store.getString("gameAction::countdown:stall"));timer.put("readyAt",System.currentTimeMillis()-1);store.put("gameAction::countdown:stall",timer.toString());TimeUtil.waits.clear();assert GameTaskAction.waitCountdown("stall",15,"test")&&TimeUtil.waits.equals(List.of(0L));
  TimeUtil.rollDay=true;assert !GameTaskAction.waitCountdown("crossday",15,"test");
  assert MemberBrowseTasks.rows("{success:true,resultData:{playInstanceId:'unknown'}}")==null:"unverified snapshot cannot authorize cleanup";
 }
 static void cleanup()throws Exception{
  resetMember();Calendar now=MyUtils.getInstance();String gameDay=now.get(Calendar.YEAR)+"-"+now.get(Calendar.DAY_OF_YEAR),day=now.get(Calendar.YEAR)+":"+now.get(Calendar.DAY_OF_YEAR);
  RuntimeInfo a=RuntimeInfo.getInstance();a.put("gameAction::old","{day:'old',pending:true}");a.put("gameAction::today",new JSONObject().put("day",gameDay).put("pending",true).toString());
  a.put("memberApply::old","2020:1:0");a.put("memberApply::today",day+":0");a.put("other","preserve");
  RuntimeInfo.uid="B";RuntimeInfo.getInstance().put("gameAction::old","B");RuntimeInfo.uid="A";GameTaskAction.cleanupDailyRecords();
  assert !a.data.containsKey("gameAction::old")&&!a.data.containsKey("memberApply::old")&&a.data.containsKey("gameAction::today")&&a.data.containsKey("memberApply::today")&&a.data.containsKey("other");assert RuntimeInfo.accounts.get("B").getString("gameAction::old").equals("B");
  a.put("memberBrowse::gone","{day:'old',submitted:true}");a.put("memberBrowse::pone","{day:'old',submitted:true}");AntMemberRpcCall.tasks.put("one",member("one",true));
  AntMemberRpcCall.repeatPage=true;MemberBrowseTasks.load();assert a.data.containsKey("memberBrowse::gone"):"incomplete snapshot must preserve unknown writes";
  AntMemberRpcCall.repeatPage=false;AntMemberRpcCall.tasks.put("invalid",new JSONObject().put("status","TODO"));MemberBrowseTasks.load();assert a.data.containsKey("memberBrowse::gone"):"missing task ID must not authorize cleanup";AntMemberRpcCall.tasks.remove("invalid");
  MemberBrowseTasks.load();assert !a.data.containsKey("memberBrowse::gone")&&a.data.containsKey("memberBrowse::pone"):"active pending process must survive cleanup";
  AntMemberRpcCall.tasks.get("one").put("status","FINISHED");MemberBrowseTasks.load();assert !a.data.containsKey("memberBrowse::pone")&&Log.category.size()==1;MemberBrowseTasks.load();assert Log.category.size()==1:"late confirmation should log once";
 }
 public static void main(String[] args)throws Exception{games();members();cleanup();reviewRegressions();System.out.println("PASS AG task contracts/duration/checkpoints/unknown ACK/accounts/cancellation");}
}
'''
legacy_source = (SOURCE/'model/task/antGame/GameTask.java').read_text(encoding='utf-8')
legacy_counts = re.findall(r'^    (?:Orchard|Farm|Forest)_.*?, (\d+)\)[,;]', legacy_source, re.M)
assert legacy_counts == ['2', '2', '3', '3']
code = code.replace('@@LEGACY_COUNTS@@', ','.join(legacy_counts))
code = code.replace('@@MEMBER@@', nested('model/task/antMember/MemberBrowseTasks.java', 'MemberBrowseTasks'))
code = code.replace('@@RPC@@', '\n'.join(method('model/task/antMember/AntMemberRpcCall.java', sig) for sig in ('private static String currentMemberRequest(', 'public static String signPageTaskList(int', 'public static String currentMemberProcesses(', 'public static String currentMemberAdTasks(', 'public static String applyCurrentMemberTask(', 'public static String executeCurrentMemberTask(', 'public static String currentMemberTaskDetail(')))
code = code.replace('@@GAME@@', nested('model/common/GameTaskAction.java', 'GameTaskAction'))
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = next(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
with tempfile.TemporaryDirectory(prefix='sesame-ag-task-sync-') as tmp:
    java = Path(tmp)/'AgTaskSyncCheck.java';java.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java)],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'AgTaskSyncCheck'],check=True)
