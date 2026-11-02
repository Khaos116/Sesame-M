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
import java.util.*;import java.util.regex.Pattern;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;import org.json.*;
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
 static class Status {static Set<String> flags=new HashSet<>();static final Status INSTANCE=new Status();Set<String> flagLogList=new HashSet<>();static String key(String k){return RuntimeInfo.uid+":"+MyUtils.day+":"+k;}static boolean hasFlagToday(String k){return flags.contains(key(k));}static void flagToday(String k){flags.add(key(k));}static boolean flagTodayVerified(Set<String> tags,String day){for(String tag:tags)flagToday(tag);return true;}
  static void ensureLoadedForCurrentUid(){INSTANCE.flagLogList.clear();String prefix=key("");for(String flag:flags)if(flag!=null&&flag.startsWith(prefix))INSTANCE.flagLogList.add(flag.substring(prefix.length()));}
  @@STATUS_FLAGS@@
 }
 static class DailyTask {static boolean manual;static boolean isManual(){return manual;}}
 static class MessageUtil {static boolean isRetryable(JSONObject j){return "3000".equals(j.optString("resultCode"));}static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("resultCode"));}static int rejected;static String lastList;static void checkResultCodeAndMarkTaskBlackList(String list,String title,JSONObject j){lastList=list;assert Set.of("AntMemberTaskList","AntMemberAdTaskList").contains(list);if("UNSUPPORTED".equals(j.optString("resultCode")))rejected++;}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"))||j.has("error")||"RPC_SKIPPED".equals(j.optString("resultCode"));}static String errorMessage(JSONObject j){return j.optString("errorMessage",j.optString("resultDesc"));}}
 static class RpcFailurePolicy {static boolean isRiskDenied(String code,String message){return "1009".equals(code)||"访问被拒绝".equals(message);}}
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
  static String detailAfterFirst;
  static JSONObject afterAdApply,afterExecute,afterAdFinish;
  static int adApply,adFinish,adQueries,processQueries,failAdRecheckPage;static String adListReply,processListReply,adListAfterApply;static boolean adAdvance=true,cancelAdQuery,processAdAfterApply,zeroAdAfterExecute;static String adApplyReply="{success:true}",adFinishReply="{success:true}";
  static String applyAdTask(String biz,String ad,int award,JSONObject ext){adApply++;assert biz.equals("biz")&&ad.equals("ad")&&award==10&&ext.optString("adId").equals("ad");if(processAdAfterApply)tasks.get("ad").put("processId","pad");if(afterAdApply!=null)tasks.put("ad",afterAdApply);return adApplyReply;}
  static String finishAdTask(String biz){adFinish++;assert biz.equals("biz");if(adAdvance)for(JSONObject row:tasks.values())if(row.optBoolean("adTask"))row.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",5));if(afterAdFinish!=null)tasks.put("ad",afterAdFinish);return adFinishReply;}
  static Map<String,JSONObject> tasks=new LinkedHashMap<>();static List<Integer> pages=new ArrayList<>();static Set<String> sessions=new HashSet<>();static Map<Integer,String> pageReplies=new HashMap<>();static int apply,execute,details;static boolean advance=true,emptyMap,repeatPage,brokenDetail,omitConfig,statusOnly,splitPages,hideAfterAdApply;static String applyReply,pageOverride,detailReply,executeReply="{success:true}";
  static String response(JSONArray rows){return new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVOList",rows)).toString();}
  static String signPageTaskList(int page,String session){pages.add(page);sessions.add(session);if(hideAfterAdApply&&adApply>0)return response(new JSONArray());if(adApply>0&&page==failAdRecheckPage)return "{success:false,resultCode:'102'}";if(pageReplies.containsKey(page))return pageReplies.get(page);if(pageOverride!=null)return pageOverride;if(splitPages)return response(page<=tasks.size()?new JSONArray().put(new ArrayList<>(tasks.values()).get(page-1)):new JSONArray());return response(page==1||repeatPage?new JSONArray(tasks.values()):new JSONArray());}
  static String currentMemberAdTasks(){adQueries++;if(cancelAdQuery)throw new TaskCancelledException();if(adApply>0&&adListAfterApply!=null)return adListAfterApply;return adListReply==null?response(new JSONArray()):adListReply;}static String currentMemberProcesses(){processQueries++;return processListReply==null?response(new JSONArray(tasks.values())):processListReply;}
  static String applyCurrentMemberTask(String id,boolean batch){apply++;if(applyReply!=null)return applyReply;JSONObject t=tasks.get(id);if(!emptyMap)t.put("processId","p"+id);return new JSONObject().put("success",true).put("resultData",new JSONObject().put("resultMap",emptyMap?new JSONObject():new JSONObject().put(id,"SUCCESS")).put("taskProcessVO",batch?JSONObject.NULL:new JSONObject(t.toString()))).toString();}
  static String currentMemberTaskDetail(String id){details++;if(details>1&&detailAfterFirst!=null)return detailAfterFirst;if(detailReply!=null)return detailReply;JSONObject row=tasks.values().stream().filter(t->id.equals(t.optString("processId"))).findFirst().orElse(null);if(row!=null&&omitConfig){row=new JSONObject(row.toString());row.remove("simpleTaskConfig");}return brokenDetail?"{}":new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",row)).toString();}
  static String executeCurrentMemberTask(String type,String param){execute++;if(zeroAdAfterExecute)tasks.get("ad").put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));if(statusOnly)tasks.get(param).put("status","PROCESSING");else if(advance){JSONObject row=tasks.get(param);int n=row.optInt("currentCount")+1;row.put("currentCount",n);if(n>=row.optInt("targetCount"))row.put("status","FINISHED");}if(afterExecute!=null)tasks.put(param,afterExecute);return executeReply;}
 }
 static class AntMemberTaskListMap {static Map<String,String> names=new HashMap<>();static Map<String,String> getMap(){return names;}static void add(String k,String v){names.put(k,v);}static void save(){}}
 static class TaskAlternative { @@TRANSACTION@@ }
 static class RpcContract { @@RPC@@ }
 @@MEMBER@@
 static void reset(){RuntimeInfo.accounts.clear();RuntimeInfo.uid="A";RuntimeInfo.fail=false;ApplicationHook.calls.clear();ApplicationHook.reply="{success:true}";ApplicationHook.transport=false;MyUtils.day=0;TimeUtil.cancel=TimeUtil.cancelWait=TimeUtil.rollDay=false;TimeUtil.waits.clear();GameTask.writes=0;GameTask.result=2;DailyTask.manual=false;Status.flags.clear();MessageUtil.rejected=0;Log.category.clear();AntMemberRpcCall.detailAfterFirst=null;AntMemberRpcCall.afterAdApply=AntMemberRpcCall.afterExecute=AntMemberRpcCall.afterAdFinish=null;}
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
 static void resetMember(){reset();AntMemberRpcCall.processAdAfterApply=AntMemberRpcCall.zeroAdAfterExecute=false;MessageUtil.lastList=null;AntMemberRpcCall.adQueries=AntMemberRpcCall.processQueries=AntMemberRpcCall.details=0;AntMemberRpcCall.adListReply=AntMemberRpcCall.processListReply=AntMemberRpcCall.detailReply=AntMemberRpcCall.adListAfterApply=null;AntMemberRpcCall.failAdRecheckPage=0;AntMemberRpcCall.pageReplies.clear();AntMemberRpcCall.hideAfterAdApply=AntMemberRpcCall.cancelAdQuery=false;AntMemberRpcCall.adApply=AntMemberRpcCall.adFinish=0;AntMemberRpcCall.adAdvance=true;AntMemberRpcCall.adApplyReply=AntMemberRpcCall.adFinishReply="{success:true}";AntMemberRpcCall.tasks.clear();AntMemberRpcCall.pages.clear();AntMemberRpcCall.sessions.clear();AntMemberRpcCall.apply=AntMemberRpcCall.execute=0;AntMemberRpcCall.advance=true;AntMemberRpcCall.emptyMap=AntMemberRpcCall.repeatPage=AntMemberRpcCall.brokenDetail=AntMemberRpcCall.omitConfig=AntMemberRpcCall.statusOnly=AntMemberRpcCall.splitPages=false;AntMemberRpcCall.applyReply=null;AntMemberRpcCall.pageOverride=null;AntMemberRpcCall.executeReply="{success:true}";}
 static JSONObject adTask(){JSONObject row=member("ad",false).put("adTask",true).put("lightsAdExtMap",new JSONObject().put("bizId","biz").put("adId","ad"));row.optJSONObject("simpleTaskConfig").remove("targetBusiness");row.optJSONObject("simpleTaskConfig").put("awardParam",new JSONObject().put("awardParamPoint","10"));return row;}
 static void adChecks()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"ad task did not use dedicated apply/finish flow";
  assert TimeUtil.waits.stream().anyMatch(n->n>=14900)&&Log.category.stream().anyMatch(x->x.contains("#详情奖励积分[5]"))&&Log.category.stream().noneMatch(x->x.contains("#详情奖励积分[10]"));
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());MemberBrowseTasks.run(true, Set.of("taskad"), true, Set.of("taskad"));assert AntMemberRpcCall.adApply==0;assert AntMemberTaskListMap.names.containsKey("taskad");
  for(Object bad:List.of("bad",new JSONObject().put("bizId",1).put("adId","ad"),new JSONObject().put("bizId","biz"))){resetMember();AntMemberRpcCall.tasks.put("ad",adTask().put("lightsAdExtMap",bad));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0;}
  resetMember();JSONObject explicit=adTask().put("targetBusiness","CALL_APP#sub#one");AntMemberRpcCall.tasks.put("ad",explicit);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0;
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&Log.category.isEmpty():"ACK was treated as confirmed points or unknown finish repeated";
  AntMemberRpcCall.tasks.get("ad").put("status","FINISHED");MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==1;
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());TimeUtil.cancelWait=true;try{MemberBrowseTasks.run(true, Set.of(), true, Set.of());throw new AssertionError("ad cancellation swallowed");}catch(TaskCancelledException expected){}assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0;
  TimeUtil.cancelWait=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"cancelled ad wait reapplied task";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());RuntimeInfo.fail=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0;
  resetMember();JSONObject regressed=adTask().put("targetCount",2);AntMemberRpcCall.tasks.put("ad",regressed);Calendar oldNow=MyUtils.getInstance();JSONObject oldRow=new JSONObject(regressed.toString()).put("currentCount",1);
  RuntimeInfo.getInstance().put("memberAd::ad",new JSONObject().put("day",oldNow.get(Calendar.YEAR)+":"+oldNow.get(Calendar.DAY_OF_YEAR)).put("stage",MemberBrowseTasks.progress(oldRow)).put("before",oldRow).put("contract",MemberBrowseTasks.adContract(oldRow).toString()).put("applied",true).put("accepted",true).put("submitted",true).toString());
  MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0&&AntMemberRpcCall.adFinish==0:"regressed counter discarded unknown ad submission and applied again";
  resetMember();JSONObject badTimer=adTask();AntMemberRpcCall.tasks.put("ad",badTimer);Calendar adNow=MyUtils.getInstance();
  RuntimeInfo.getInstance().put("memberAd::ad",new JSONObject().put("day",adNow.get(Calendar.YEAR)+":"+adNow.get(Calendar.DAY_OF_YEAR)).put("stage",MemberBrowseTasks.progress(badTimer)).put("contract",MemberBrowseTasks.adContract(badTimer).toString()).put("applied",true).put("accepted",true).put("readyAt",0).toString());
  MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0&&AntMemberRpcCall.adFinish==0:"invalid saved timer finished advertisement without waiting";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask().put("bizKey","OFFLINE_PAY"));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==0;
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adApplyReply="{}";MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0;
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());TimeUtil.rollDay=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adFinish==0;
  TimeUtil.rollDay=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.adFinish==1;
  resetMember();JSONObject ext=new JSONObject().put("bizId","biz\\quoted").put("adId","ad\"quoted");RpcContract.applyAdTask("biz\\quoted","ad\"quoted",10,ext);RpcContract.finishAdTask("biz\\quoted");
  JSONObject apply=ApplicationHook.calls.get(0);assert apply.optString("adBizId").equals("biz\\quoted")&&apply.optString("adId").equals("ad\"quoted")&&apply.optInt("awardNum")==10&&apply.optString("scene").equals("TASK_WALL")&&apply.optString("spaceCode").equals("ant_member_xlight_task")&&apply.optJSONObject("sourcePassMap").optString("source").equals("mytab");
  assert ApplicationHook.calls.get(1).optString("bizId").equals("biz\\quoted")&&ApplicationHook.calls.get(1).optJSONObject("extendInfo").length()==0;
  List<JSONObject> legacy=MemberBrowseTasks.rows("{success:true,categoryTaskList:[{type:'BROWSE',taskList:[{taskConfigInfo:{id:'legacy',name:'legacyAd'},adTask:true}]}]}");assert legacy!=null&&legacy.size()==1;
  resetMember();JSONObject nameOnly=adTask();nameOnly.optJSONObject("simpleTaskConfig").put("title","").put("name","nameOnly");AntMemberRpcCall.tasks.put("ad",nameOnly);MemberBrowseTasks.run(true, Set.of("nameOnly"), true, Set.of("nameOnly"));assert AntMemberRpcCall.adApply==0&&AntMemberTaskListMap.names.containsKey("nameOnly");
  System.out.println("PASS upstream member ad apply/wait/finish, blacklist candidates, unknown ACK, resume, malformed contracts and legacy categories");
 }
 static void splitAdChecks()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true));
  MemberBrowseTasks.run(false,Set.of(),false,Set.of());assert AntMemberRpcCall.pages.isEmpty()&&AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0:"disabled paths queried tasks";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.execute==1&&AntMemberRpcCall.adApply==0:"member switch executed ads";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"ad switch did not run independently";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true));
  MemberBrowseTasks.run(true,Set.of("taskad"),true,Set.of("taskone"));assert AntMemberRpcCall.execute==1&&AntMemberRpcCall.adApply==1:"one blacklist leaked into the other path";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true));
  MemberBrowseTasks.run(true,Set.of("taskone"),true,Set.of("taskad"));assert AntMemberRpcCall.execute==0&&AntMemberRpcCall.adApply==0&&AntMemberRpcCall.pages.equals(List.of(1,2))&&AntMemberRpcCall.adQueries==1&&AntMemberRpcCall.processQueries==1:"two paths fetched separate snapshots or ignored their blacklist";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adApplyReply="{success:false,resultCode:'UNSUPPORTED'}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert MessageUtil.rejected==1&&"AntMemberAdTaskList".equals(MessageUtil.lastList):"ad rejection went to the ordinary blacklist";
  for(String bad:List.of("{success:false,resultCode:'1009'}")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.pageOverride=bad;
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.pages.equals(List.of(1))&&AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"failed sign page continued later requests";
   AntMemberRpcCall.pageOverride=null;MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"temporary failure poisoned the whole day";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.adQueries==1&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"failed ad list continued later requests";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.processListReply="{}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"known process must use its own matched detail after an independent list failure";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adAdvance=false;
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1;
  RuntimeInfo.uid="B";MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2;
  RuntimeInfo.uid="A";MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:"A/B/A reused another account or repeated an unknown write";
  System.out.println("PASS member/ad switches, independent blacklists, one shared snapshot, failed-list stop and A/B/A isolation");
 }
 static void sourceFailures()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,resultCode:'102'}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.execute==1&&AntMemberRpcCall.adQueries==0:"disabled advertising source blocked ordinary tasks";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,resultCode:'102'}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"independent busy advertising source discarded a valid ordinary process";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.pageReplies.put(2,"{success:false,resultCode:'102'}");AntMemberRpcCall.processListReply="{}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.execute==1&&AntMemberRpcCall.apply==0:"later-page failure discarded a known process from an earlier page";
  for(String failed:List.of("{}","{success:false,resultCode:'102'}","{success:true,resultData:{taskProcessVOList:[null]}}")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.pageOverride=failed;
   MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.execute==1:"valid active process was lost after an independent sign-page failure";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.processListReply="{}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"missing active-process source authorized a potentially duplicate signup";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.processListReply="{success:true,resultData:{taskProcessVOList:[{processId:'unknown'}]}}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"unidentifiable active process authorized a potentially duplicate signup";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("invalid",new JSONObject().put("status","TODO"));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"unrelated dirty ordinary row blocked a valid advertisement";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.processListReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.processQueries==0:"disabled ordinary source blocked advertisement flow";
  for(String code:List.of("error","resultCode","errorCode","retCode","code")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.pageOverride=new JSONObject().put("success",false).put(code,"1009").toString();
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"risk response continued other source requests";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,error:'RPC_SKIPPED',resultCode:'RPC_SKIPPED',causeCode:'102',riskPause:false}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"one endpoint's busy pause blocked an independent valid process";
  for(String paused:List.of("{success:false,error:'RPC_SKIPPED',causeCode:'1009'}","{success:false,error:'RPC_SKIPPED'}","{success:false,error:'RPC_SKIPPED',causeCode:'UNKNOWN'}","{success:false,error:'RPC_SKIPPED',causeCode:'48'}")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply=paused;
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"risk or unknown pause continued other source requests";
  }
  for(String message:List.of("需要验证", "访问被拒绝，请稍后再试", "风控暂停")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply=new JSONObject().put("success",false).put("error","RPC_SKIPPED").put("causeCode","102").put("riskPause",false).put("errorMessage",message).toString();
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"busy pause with verification/risk text continued requests";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,error:'RPC_SKIPPED',localRuleSkip:true}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"known unsupported independent source blocked ordinary tasks";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.tasks.put("two",member("two",true));AntMemberRpcCall.detailReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.details==1&&AntMemberRpcCall.execute==0:"detail risk refusal continued the next task";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.hideAfterAdApply=true;
  AntMemberRpcCall.processListReply=AntMemberRpcCall.response(new JSONArray());
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"missing advertisement snapshot authorized completion or cleared pending signup";
  AntMemberRpcCall.hideAfterAdApply=false;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"resumed advertisement repeated its existing signup";
  for(boolean failedPage:new boolean[]{false,true}){
   resetMember();AntMemberRpcCall.tasks.put("ad",adTask());if(failedPage)AntMemberRpcCall.failAdRecheckPage=2;else AntMemberRpcCall.adListAfterApply="{}";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"matching ad row from incomplete recheck authorized finish";
   AntMemberRpcCall.failAdRecheckPage=0;AntMemberRpcCall.adListAfterApply=null;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"complete recheck recovery reapplied advertisement";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.applyReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(true,Set.of(),false,Set.of());AntMemberRpcCall.applyReply=null;MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==2&&AntMemberRpcCall.execute==1:"known refused signup remained reserved after risk pause";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adApplyReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());AntMemberRpcCall.adApplyReply="{success:true}";MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==1:"known refused advertisement signup remained reserved after risk pause";
  resetMember();RuntimeInfo store=RuntimeInfo.getInstance();store.put("memberAd::missing","{day:'old',submitted:true}");store.put("memberBrowse::missing","{day:'old',submitted:true}");
  AntMemberRpcCall.pageOverride="{}";AntMemberRpcCall.adListReply="{}";AntMemberRpcCall.processListReply="{}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert store.data.containsKey("memberAd::missing")&&store.data.containsKey("memberBrowse::missing"):"incomplete sources cleared unknown submissions";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,resultCode:'102'}";AntMemberRpcCall.advance=false;
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());RuntimeInfo.uid="B";MemberBrowseTasks.run(true,Set.of(),true,Set.of());RuntimeInfo.uid="A";MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==2:"partial-source fallback lost A/B/A pending-write isolation";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.cancel=true;
  try{MemberBrowseTasks.run(true,Set.of(),true,Set.of());throw new AssertionError("source cancellation swallowed");}catch(TaskCancelledException expected){}assert AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.cancelAdQuery=true;
  try{MemberBrowseTasks.run(true,Set.of(),true,Set.of());throw new AssertionError("RPC source cancellation swallowed");}catch(TaskCancelledException expected){}assert AntMemberRpcCall.adQueries==1&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0;
  System.out.println("PASS independent sources/disabled endpoints, partial rows and complete-only cleanup, risk/busy pauses, cancellation and A/B/A");
 }
 static void partialAdContracts()throws Exception{
  for(int missing=1;missing<=3;missing++)for(boolean sparseFirst:new boolean[]{false,true}){
   resetMember();JSONObject full=adTask();full.optJSONObject("simpleTaskConfig").put("browseSeconds",30);JSONObject sparse=new JSONObject(full.toString());JSONObject cfg=sparse.optJSONObject("simpleTaskConfig");
   if((missing&1)!=0)cfg.remove("browseSeconds");if((missing&2)!=0)cfg.remove("awardParam");
   AntMemberRpcCall.tasks.put("ad",sparseFirst?sparse:full);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(sparseFirst?full:sparse));
   Map<String,JSONObject> loaded=MemberBrowseTasks.load(false,true);JSONObject selected=loaded.get("ad:");
   assert selected!=null&&MemberBrowseTasks.adContract(selected).optInt("seconds")==30&&MemberBrowseTasks.adContract(selected).optInt("awardNum")==10:"missing defaults caused a conflict or replaced an explicit contract";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&TimeUtil.waits.stream().anyMatch(n->n>=29000):"sparse duplicate shortened the real wait or prevented completion";
  }
  resetMember();JSONObject left=adTask(),right=adTask();left.optJSONObject("simpleTaskConfig").put("browseSeconds",30).remove("awardParam");right.optJSONObject("simpleTaskConfig").remove("browseSeconds");
  AntMemberRpcCall.tasks.put("ad",left);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(right));RuntimeInfo.getInstance().put("memberAd::ad","{day:'old',submitted:true}");
  JSONObject combined=MemberBrowseTasks.load(false,true).get("ad:");assert combined!=null&&MemberBrowseTasks.adContract(combined).optInt("seconds")==30&&MemberBrowseTasks.adContract(combined).optInt("awardNum")==10:"compatible explicit fields were not resolved into the real contract";
  assert !left.optJSONObject("simpleTaskConfig").has("awardParam")&&!right.optJSONObject("simpleTaskConfig").has("browseSeconds"):"contract resolution changed raw source rows";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&TimeUtil.waits.stream().anyMatch(n->n>=29000):"resolved contract used a default duration or did not complete";
  JSONObject whole=adTask();whole.optJSONObject("simpleTaskConfig").put("browseSeconds",30);AntMemberRpcCall.processListReply=AntMemberRpcCall.response(new JSONArray().put(whole));
  assert MemberBrowseTasks.load(true,true).containsKey("ad:"):"a later complete source could not resolve complementary missing fields";
  for(boolean reverse:new boolean[]{false,true}){
   resetMember();JSONObject timed=adTask(),awarded=adTask();timed.optJSONObject("simpleTaskConfig").put("browseSeconds",30).remove("awardParam");
   JSONObject cfg=awarded.optJSONObject("simpleTaskConfig");cfg.remove("browseSeconds");cfg.remove("awardParam");cfg.put("stageVOList",new JSONArray().put(new JSONObject().put("awardParam",new JSONObject().put("awardParamPoint",10))));awarded.remove("simpleTaskConfig");awarded.put("taskConfigInfo",cfg);
   AntMemberRpcCall.tasks.put("ad",reverse?awarded:timed);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(reverse?timed:awarded));
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&TimeUtil.waits.stream().anyMatch(n->n>=29000):"source order or stage award changed the resolved execution contract";
  }
  for(boolean duration:new boolean[]{true,false}){
   resetMember();left=adTask();right=adTask();JSONObject cfg=right.optJSONObject("simpleTaskConfig");
   if(duration){cfg.remove("awardParam");cfg.put("browseSeconds",30);}else{cfg.remove("browseSeconds");cfg.put("awardParam",new JSONObject().put("awardParamPoint",0));}
   AntMemberRpcCall.tasks.put("ad",left);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(right));assert MemberBrowseTasks.load(false,true).isEmpty():"one missing field masked a conflicting explicit field";
  }
  resetMember();left=adTask();right=adTask();right.optJSONObject("simpleTaskConfig").remove("browseSeconds");right.optJSONObject("simpleTaskConfig").remove("awardParam");right.optJSONObject("lightsAdExtMap").put("adId","other");
  AntMemberRpcCall.tasks.put("ad",left);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(right));assert MemberBrowseTasks.load(false,true).isEmpty():"missing optional fields masked a real advertisement identity conflict";
  resetMember();left=adTask().put("targetCount",2);left.optJSONObject("simpleTaskConfig").put("browseSeconds",30);right=new JSONObject(left.toString()).put("currentCount",1);right.optJSONObject("simpleTaskConfig").remove("browseSeconds");
  AntMemberRpcCall.tasks.put("ad",left);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(right));assert MemberBrowseTasks.load(false,true).isEmpty():"rich parameters overrode a newer partial stage";
  for(Object bad:new Object[]{"broken",JSONObject.NULL}){
   JSONObject invalid=adTask();invalid.optJSONObject("simpleTaskConfig").put("awardParam",bad);assert MemberBrowseTasks.adContract(invalid)==null:"malformed explicit award became an absent default";
  }
  JSONObject staged=adTask();JSONObject cfg=staged.optJSONObject("simpleTaskConfig");cfg.remove("awardParam");cfg.put("stageVOList",new JSONArray().put(new JSONObject().put("awardParam",new JSONObject().put("awardParamPoint",10))));
  assert MemberBrowseTasks.adContract(staged,true).optInt("awardNum")==10:"stage award was not recognized as explicit";
  cfg.put("stageVOList","broken");assert MemberBrowseTasks.adContract(staged)==null:"malformed explicit stages became a default award";
  cfg.remove("stageVOList");cfg.put("awardParam",new JSONObject().put("awardParamPoint",JSONObject.NULL));assert MemberBrowseTasks.adContract(staged)==null:"null explicit award became zero";
  System.out.println("PASS explicit advertisement fields, source order, complete-row selection and partial-stage/identity safety");
 }
 static void pendingAdProcesses()throws Exception{
  for(boolean cancel:new boolean[]{false,true}){
   resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true).put("status","FINISHED"));AntMemberRpcCall.hideAfterAdApply=AntMemberRpcCall.processAdAfterApply=true;
   RuntimeInfo.getInstance().put("memberBrowse::pone","{day:'old',submitted:true}");TimeUtil.cancelWait=cancel;
   if(cancel){try{MemberBrowseTasks.run(false,Set.of(),true,Set.of());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0;TimeUtil.cancelWait=false;}
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.processQueries>=2:"process-only advertisement could not finish/resume without duplicate application";
   assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&RuntimeInfo.getInstance().data.containsKey("memberBrowse::pone")&&AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"advertisement lookup executed or cleared disabled ordinary tasks";
  }
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.hideAfterAdApply=AntMemberRpcCall.processAdAfterApply=true;TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.run(false,Set.of(),true,Set.of());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancelWait=false;MyUtils.day++;
  AntMemberRpcCall.processListReply="{}";MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0:"expired record forced a process lookup instead of complete-snapshot cleanup";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.hideAfterAdApply=AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.processListReply="{success:false,resultCode:'1009'}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"process risk response authorized completion or cleared acceptance";
  AntMemberRpcCall.processListReply=null;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1;
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.hideAfterAdApply=AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.adAdvance=false;
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"process lookup repeated an unknown completion";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.pageReplies.put(1,AntMemberRpcCall.response(new JSONArray().put(adTask())));
  AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'pad'}}}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&!RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"stale display repeated an advertisement after its process confirmed completion";
  AntMemberRpcCall.pageReplies.clear();AntMemberRpcCall.tasks.get("ad").put("status","TODO").put("currentCount",1).put("targetCount",2);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:"confirmed previous stage blocked a new real stage";
  AntMemberRpcCall.processAdAfterApply=false;AntMemberRpcCall.adApplyReply="{success:true}";AntMemberRpcCall.tasks.get("ad").put("status","TODO").put("processId","new-process").put("currentCount",0).put("targetCount",1);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==3&&AntMemberRpcCall.adFinish==3:"confirmation marker blocked a distinct new process";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask().put("processId","pad").put("currentCount",1).put("targetCount",2));AntMemberRpcCall.tasks.put("old",adTask().put("processId","old").put("status","FINISHED"));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"old completed process falsely confirmed the new stage";
  System.out.println("PASS process-only advertisement recovery, cancellation resume, ordinary switch isolation, risk stop and unknown completion deduplication");
 }
 static JSONObject periodZero(JSONObject row){return row.put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));}
 static void oldDayConfirmationChecks()throws Exception{
  for(int mode=0;mode<4;mode++){
   resetMember();JSONObject listed=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   JSONObject reply=new JSONObject().put("taskConfigId","ad").put("processId","new");if(mode!=2)reply.put("currentCount",0).put("targetCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",reply)).toString();TimeUtil.cancelWait=true;
   try{MemberBrowseTasks.perform(listed,"old-ad-pending");throw new AssertionError();}catch(TaskCancelledException expected){}TimeUtil.cancelWait=false;if(mode==1)MemberBrowseTasks.perform(actual,"old-ad-submitted");int finishes=AntMemberRpcCall.adFinish;
   String pending=RuntimeInfo.getInstance().getString("memberAd::ad");assert !pending.isEmpty();MyUtils.day++;actual.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",9));if(mode==3)actual.put("processId","unrelated");AntMemberRpcCall.tasks.put("noise",new JSONObject().put("invalidRow",true));
   MemberBrowseTasks.load(false,true);assert Status.flagsStartingWith("memberAdConfirmed::").isEmpty()&&Log.category.isEmpty():"yesterday's completion wrote today's guards or rewards";
   if(mode==3){assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(pending):"an unrelated completed process cleared old unknown evidence when global cleanup was unsafe";continue;}
   assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"matched old completion was not cleaned when a dirty row blocked global cleanup";
   AntMemberRpcCall.tasks.remove("noise");actual.put("status","TODO");MemberBrowseTasks.perform(actual,"today-valid-ad");assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==finishes+1:"old completion prevented today's valid stage";
  }
  System.out.println("PASS old ad acceptance/submission/missing phase with dirty-page cleanup, no current-day flags/rewards and unrelated-process protection");
 }
 static void oldMemberDayChecks()throws Exception{
  for(boolean submitted:new boolean[]{false,true}){
   resetMember();JSONObject row=member("one",true);AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.advance=false;TimeUtil.cancelWait=!submitted;
   try{MemberBrowseTasks.perform(row,"old-member");assert submitted;}catch(TaskCancelledException expected){assert !submitted;}TimeUtil.cancelWait=false;MyUtils.day++;row.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",7));AntMemberRpcCall.tasks.put("noise",new JSONObject());MemberBrowseTasks.load(true,false);
   assert !RuntimeInfo.getInstance().data.containsKey("memberBrowse::pone")&&Log.category.isEmpty():"old ordinary completion was credited to today's statistics";
   AntMemberRpcCall.tasks.remove("noise");row.put("status","TODO");AntMemberRpcCall.advance=true;MemberBrowseTasks.perform(row,"today-member");assert Log.category.size()==1&&Log.category.get(0).contains("7"):"business-day filtering also suppressed today's valid ordinary reward";
  }
  resetMember();JSONObject detailRow=member("one",true);AntMemberRpcCall.tasks.put("one",detailRow);TimeUtil.cancelWait=true;try{MemberBrowseTasks.perform(detailRow,"old-detail-start");throw new AssertionError();}catch(TaskCancelledException expected){}TimeUtil.cancelWait=false;MyUtils.day++;
  AntMemberRpcCall.detailReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",new JSONObject(detailRow.toString()).put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",7)))).toString();MemberBrowseTasks.perform(detailRow,"old-detail-end");assert Log.category.isEmpty()&&!RuntimeInfo.getInstance().data.containsKey("memberBrowse::pone"):"old detail result bypassed the business-day reward guard";
  resetMember();JSONObject row=member("one",true);AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.detailAfterFirst=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",new JSONObject(row.toString()).put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",7)))).toString();TimeUtil.rollDay=true;
  MemberBrowseTasks.perform(row,"member-midnight");assert MyUtils.day==1&&AntMemberRpcCall.execute==0&&Log.category.isEmpty():"midnight ordinary recheck used the previous timer as today's reward";
  System.out.println("PASS ordinary old-day pending cleanup and midnight recheck without today's reward credit");
 }
 static void adIncompleteReplyPeriodChecks()throws Exception{
  for(boolean listedPeriod:new boolean[]{false,true})for(boolean cancel:new boolean[]{false,true}){
   resetMember();JSONObject listed=adTask().put("processId","old"),actual=adTask().put("processId","new").put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",2).put("PERIOD_TARGET_COUNT",10));if(listedPeriod)listed.put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",2).put("PERIOD_TARGET_COUNT",10));
   AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new',currentCount:0,targetCount:1}}}";TimeUtil.cancelWait=cancel;
   if(cancel){try{MemberBrowseTasks.perform(listed,"partial-reply-period");throw new AssertionError();}catch(TaskCancelledException expected){}JSONObject state=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert !state.has("stage")&&state.optBoolean("accepted"):"reply omitted period counters but became a trusted zero baseline";TimeUtil.cancelWait=false;MemberBrowseTasks.perform(actual,"complete-period-list");}
   else MemberBrowseTasks.perform(listed,"partial-reply-period");
   assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"omitted reply period became false progress and prevented the first completion";
   JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("stage").equals(MemberBrowseTasks.progress(actual))&&saved.optBoolean("submitted"):"full list period did not become the accepted phase";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"false period advancement authorized another application";
  }
  System.out.println("PASS missing reply period never becomes zero progress, with/without source cycle hints and after cancellation");
 }
 static void adCachedCleanupChecks()throws Exception{
  for(boolean originalBound:new boolean[]{false,true})for(boolean replyCounts:new boolean[]{false,true})for(boolean terminal:new boolean[]{false,true})for(boolean expiredRow:new boolean[]{false,true}){
   resetMember();JSONObject listed=adTask().put("processId",originalBound?"old":"").put("currentCount",originalBound?2:0).put("targetCount",originalBound?5:1),actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   String contract=MemberBrowseTasks.adContract(actual).toString();Status.flagToday(terminal?MemberBrowseTasks.adConfirmedKey("ad","new","",contract)+":terminal:0:1:0:0":MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(actual),contract));
   if(expiredRow)actual.put("status","EXPIRED");
   JSONObject reply=new JSONObject().put("taskConfigId","ad").put("processId","new");if(replyCounts)reply.put("currentCount",0).put("targetCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",reply)).toString();
   int start=Log.lines.size();MemberBrowseTasks.perform(listed,"cached-accepted-reply");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&!RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"confirmed unsubmitted reply remained an accepted pending record";
   assert Log.lines.subList(start,Log.lines.size()).stream().filter(s->s.contains("已清理")&&s.contains("未发送完成请求")).count()==1&&Log.category.isEmpty():"cached closure was silent, duplicated or repeated a reward";
   AntMemberRpcCall.afterAdApply=null;AntMemberRpcCall.tasks.put("ad",listed);int queries=AntMemberRpcCall.adQueries;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&AntMemberRpcCall.adQueries==queries+1:"removing the record lost original-trigger deduplication or repeated a full snapshot";
  }
  resetMember();JSONObject display=adTask(),replyRow=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",display);AntMemberRpcCall.afterAdApply=replyRow;AntMemberRpcCall.adAdvance=false;Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(replyRow),MemberBrowseTasks.adContract(replyRow).toString()));
  MemberBrowseTasks.perform(display,"list-bound-cached-reply");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0&&!RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"post-snapshot binding could not close an already confirmed reply";
  resetMember();JSONObject previous=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),today=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",previous);AntMemberRpcCall.afterAdApply=today;AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new',currentCount:0,targetCount:1,extInfo:{PERIOD_CURRENT_COUNT:0,PERIOD_TARGET_COUNT:0}}}}";TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.perform(previous,"prior-day-accepted");throw new AssertionError();}catch(TaskCancelledException expected){}MyUtils.day++;TimeUtil.cancelWait=false;String todayContract=MemberBrowseTasks.adContract(today).toString();Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(today),todayContract));MemberBrowseTasks.load(false,true);
  assert !Status.hasFlagToday(MemberBrowseTasks.adConfirmedKey("ad","old",MemberBrowseTasks.progress(previous),todayContract)):"old business-day request aliases leaked into today's cached closure";
  resetMember();JSONObject actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",actual);AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(actual,"historical-pending");JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad")).put("submitted",false);RuntimeInfo.getInstance().put("memberAd::ad",saved.toString());Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",saved.optString("stage"),saved.optString("contract")));
  MemberBrowseTasks.load(false,true);assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Log.category.isEmpty():"old accepted cached record could not converge directly during load";
  resetMember();actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",actual);AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(actual,"submitted-pending");saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",saved.optString("stage"),saved.optString("contract")));MemberBrowseTasks.load(false,true);assert RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"cached unsubmitted cleanup also erased a submitted unknown request";
  actual.put("status","FINISHED");MemberBrowseTasks.load(false,true);assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Log.category.isEmpty():"a submitted late confirmation duplicated the known reward";
  System.out.println("PASS cached accepted record cleanup, durable original-trigger aliases, no extra snapshot or reward, and submitted-unknown protection");
 }
 static void adPeriodOnlyReplyChecks()throws Exception{
  for(boolean cancel:new boolean[]{false,true}){
   resetMember();JSONObject listed=adTask().put("processId","old"),actual=adTask().put("processId","new").put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",5).put("PERIOD_TARGET_COUNT",10));AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new',extInfo:{PERIOD_CURRENT_COUNT:5,PERIOD_TARGET_COUNT:10}}}}";TimeUtil.cancelWait=cancel;
   if(cancel){try{MemberBrowseTasks.perform(listed,"period-only-reply");throw new AssertionError();}catch(TaskCancelledException expected){}JSONObject pending=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert !pending.has("stage")&&pending.optBoolean("accepted"):"period-only reply became a complete phase";TimeUtil.cancelWait=false;MemberBrowseTasks.perform(actual,"period-only-resume");}
   else MemberBrowseTasks.perform(listed,"period-only-reply");
   JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("stage").equals(MemberBrowseTasks.progress(actual))&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"period-only reply prevented the full list phase from finishing without reapply";
  }
  System.out.println("PASS period-only reply defers phase to the full list, including cancellation and recovery");
 }
 static void adPostReplyGuardChecks()throws Exception{
  for(String guard:List.of("exact","terminal","reject"))for(boolean replyCounts:new boolean[]{false,true}){
   resetMember();JSONObject listed=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   String contract=MemberBrowseTasks.adContract(actual).toString();if(guard.equals("exact"))Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(actual),contract));else if(guard.equals("terminal"))Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new","",contract)+":terminal:0:1:0:0");else Status.flagToday("agReject::memberAd::ad:0:1:0:0");
   JSONObject reply=new JSONObject().put("taskConfigId","ad").put("processId","new");if(replyCounts)reply.put("currentCount",0).put("targetCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",reply)).toString();
   MemberBrowseTasks.perform(listed,"post-reply-guard");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0:"changed reply process bypassed its existing completion or rejection guard";
   if(guard.equals("reject")){JSONObject state=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert state.optBoolean("accepted")&&!state.optBoolean("submitted");DailyTask.manual=true;MemberBrowseTasks.perform(actual,"manual-new-stage");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"new-stage rejection recheck disabled the existing manual retry";}
   else {actual.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",9));MemberBrowseTasks.load(false,true);MemberBrowseTasks.load(false,true);assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Log.category.isEmpty():"already confirmed reply process repeated reward logging or failed to close";}
  }
  System.out.println("PASS reply/list guard rechecks, confirmed cleanup, rejected-state retention, manual retry and no duplicate reward logs");
 }
 static void adReplyStageClosureChecks()throws Exception{
  resetMember();JSONObject display=adTask();AntMemberRpcCall.tasks.put("ad",display);AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'bound'}}}";TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.perform(display,"first-bind");throw new AssertionError("cancellation missing");}catch(TaskCancelledException expected){}
  JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("stage").equals(MemberBrowseTasks.progress(display))&&saved.optJSONObject("before")!=null:"initial reply binding discarded the request's valid starting phase";
  TimeUtil.cancelWait=false;JSONObject completed=new JSONObject(display.toString()).put("processId","bound").put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",7));AntMemberRpcCall.tasks.put("ad",completed);MemberBrowseTasks.load(false,true);
  assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Log.category.size()==1&&Log.category.get(0).contains("7"):"normal first binding could not confirm an already completed process";
  for(boolean terminal:new boolean[]{false,true}){
   resetMember();JSONObject listed=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),actual=adTask().put("processId","new").put("currentCount",terminal?1:0).put("targetCount",1);AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   if(terminal)actual.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",9));
   JSONObject reply=new JSONObject().put("taskConfigId","ad").put("processId","new");if(!terminal){periodZero(actual);reply.put("currentCount",0).put("targetCount",1).put("extInfo",actual.optJSONObject("extInfo"));}
   AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",reply)).toString();TimeUtil.cancelWait=!terminal;
   if(!terminal){try{MemberBrowseTasks.perform(listed,"reply-phase");throw new AssertionError();}catch(TaskCancelledException expected){}saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("stage").equals(MemberBrowseTasks.progress(actual)):"valid reply counters were discarded instead of becoming the new phase";TimeUtil.cancelWait=false;MemberBrowseTasks.perform(actual,"resume-phase");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1;actual.put("status","FINISHED").put("extInfo",new JSONObject().put("awardCurrentPoint",9));MemberBrowseTasks.load(false,true);}
   else assert MemberBrowseTasks.perform(listed,"terminal-before-phase"):"the bound process's own completion could not close a pending phase";
   assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Log.category.size()==1&&Log.category.get(0).contains("9"):"bound terminal process stayed pending or lost its actual reward";
   int finishes=AntMemberRpcCall.adFinish;AntMemberRpcCall.afterAdApply=null;AntMemberRpcCall.tasks.put("ad",listed);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1:"the original request row was re-applied after its reply process completed";
   MemberBrowseTasks.perform(new JSONObject(listed.toString()).put("processId",""),"stale-request-display");assert AntMemberRpcCall.adApply==1:"the original empty-process display re-applied a completed request";
   actual.put("status","TODO").put("currentCount",0);AntMemberRpcCall.tasks.put("ad",actual);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==finishes:"a stale pre-completion row re-applied the terminal reply process";
   actual.put("currentCount",1).put("targetCount",2);AntMemberRpcCall.adApplyReply="{success:true}";MemberBrowseTasks.perform(actual,"real-next-stage");assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==finishes+1:"terminal deduplication froze a genuine higher target on the same process";
   actual.put("status","FINISHED");MemberBrowseTasks.load(false,true);actual.put("status","TODO").put("processId","third").put("currentCount",0).put("targetCount",1);MemberBrowseTasks.perform(actual,"new-process");assert AntMemberRpcCall.adApply==3&&AntMemberRpcCall.adFinish==finishes+2:"terminal deduplication froze a distinct new process";
  }
  for(Object count:List.of(-1,0.5,true,"bad",JSONObject.NULL,"0.0","00")){
   resetMember();JSONObject listed=adTask().put("processId","old");AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",new JSONObject().put("taskConfigId","ad").put("processId","new").put("currentCount",count).put("targetCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0)))).toString();TimeUtil.cancelWait=true;
   try{MemberBrowseTasks.perform(listed,"invalid-reply-count");throw new AssertionError();}catch(TaskCancelledException expected){}saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert !saved.has("stage")&&saved.optBoolean("accepted"):"invalid reply counters became a trusted phase";
  }
  resetMember();JSONObject old=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),actual=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",old);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new',currentCount:0,targetCount:1,extInfo:{PERIOD_CURRENT_COUNT:0,PERIOD_TARGET_COUNT:0}}}}";
  MemberBrowseTasks.perform(old,"reply-phase-same-run");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"valid reply counters did not replace the old phase for the same invocation";
  resetMember();old=adTask().put("processId","old");AntMemberRpcCall.tasks.put("ad",old);AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new',currentCount:0,targetCount:1,extInfo:{PERIOD_CURRENT_COUNT:0,PERIOD_TARGET_COUNT:2}}}}";TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.perform(old,"reply-period");throw new AssertionError();}catch(TaskCancelledException expected){}saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("stage").equals("0:1:0:2")&&saved.optJSONObject("before").optJSONObject("extInfo").optInt("PERIOD_CURRENT_COUNT",-1)==0:"valid reply period counters were lost";
  System.out.println("PASS initial binding phase preservation, validated reply counters, pending terminal closure/reward, stale deduplication and same/new-process recovery");
 }
 static void adUnboundExpiryChecks()throws Exception{
  for(boolean submitted:new boolean[]{false,true}){
   resetMember();JSONObject display=adTask();AntMemberRpcCall.tasks.put("ad",display);AntMemberRpcCall.adAdvance=false;TimeUtil.cancelWait=!submitted;
   try{MemberBrowseTasks.perform(display,"expiry-window");assert submitted:"timer cancellation missing";}catch(TaskCancelledException expected){assert !submitted;}
   TimeUtil.cancelWait=false;String saved=RuntimeInfo.getInstance().getString("memberAd::ad");assert !saved.isEmpty();int writes=AntMemberRpcCall.adFinish;
   JSONObject foreign=new JSONObject(display.toString()).put("processId","expired-other").put("subStatus","expired");AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(foreign));MemberBrowseTasks.load(false,true);
   assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(saved):"another named expired process cleared the unbound application";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==writes:"foreign expiration authorized another apply/finish";
  }
  System.out.println("PASS unbound accepted/submitted records survive another process's expiration without duplicate writes");
 }
 static void adAuthoritativeReplyChecks()throws Exception{
  for(boolean cancel:new boolean[]{false,true})for(boolean resetCounts:new boolean[]{false,true}){
   resetMember();JSONObject old=adTask().put("processId","listed-old").put("currentCount",resetCounts?2:0).put("targetCount",resetCounts?5:1);
   JSONObject actual=new JSONObject(old.toString()).put("processId","applied-new").put("currentCount",0);AntMemberRpcCall.tasks.put("ad",old);AntMemberRpcCall.afterAdApply=actual;AntMemberRpcCall.adAdvance=false;
   AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'applied-new'}}}";TimeUtil.cancelWait=cancel;
   if(cancel){try{MemberBrowseTasks.perform(old,"reply-switch");throw new AssertionError("new reply did not reach the timer");}catch(TaskCancelledException expected){}}
   else MemberBrowseTasks.perform(old,"reply-switch");
   JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));long ready=saved.optLong("readyAt");
   assert saved.optString("processId").equals("applied-new")&&saved.optBoolean("applied")&&saved.optBoolean("accepted")&&ready>0:"successful apply reply left a false unknown application on the listed process";
   TimeUtil.cancelWait=false;if(cancel)MemberBrowseTasks.run(false,Set.of(),true,Set.of());saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
   assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&saved.optBoolean("submitted")&&saved.optLong("readyAt")==ready&&saved.optString("stage").equals(MemberBrowseTasks.progress(actual)):"the new process failed to resume its own stage without reapplying or resetting the timer";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"unknown completion on the reply process was resubmitted";
   actual.put("status","FINISHED");MemberBrowseTasks.load(false,true);assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&!Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"the authoritative process could not confirm its own completion";
  }
  resetMember();JSONObject listed=adTask().put("processId","old").put("currentCount",2).put("targetCount",5),later=new JSONObject(listed.toString()).put("processId","new").put("currentCount",0);AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new'}}}";
  MemberBrowseTasks.perform(listed,"await-new-stage");String pending=RuntimeInfo.getInstance().getString("memberAd::ad");assert new JSONObject(pending).optBoolean("accepted")&&AntMemberRpcCall.adFinish==0;
  AntMemberRpcCall.tasks.put("ad",later);RuntimeInfo.fail=true;MemberBrowseTasks.perform(later,"new-stage-write-failure");assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(pending)&&AntMemberRpcCall.adFinish==0:"phase write failure authorized completion or changed the durable accepted record";
  RuntimeInfo.fail=false;MemberBrowseTasks.perform(later,"new-stage-arrived");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"an initially missing reply process could not converge from a later valid list";
  resetMember();listed=adTask().put("processId","old");later=new JSONObject(listed.toString()).put("processId","new");AntMemberRpcCall.tasks.put("ad",listed);AntMemberRpcCall.afterAdApply=later;AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'new'}}}";TimeUtil.rollDay=true;
  MemberBrowseTasks.perform(listed,"reply-cross-day");assert AntMemberRpcCall.adFinish==0&&!RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"cross-day reply stage resurrected the old checkpoint or finished on its old timer";
  System.out.println("PASS authoritative new apply process, changed phase counters, cancellation/resume, original timer and no duplicate writes");
 }
 static void adUnboundWindowChecks()throws Exception{
  for(boolean replyProcess:new boolean[]{false,true}){
   resetMember();JSONObject row=adTask(),other=new JSONObject(row.toString()).put("processId","other").put("status","FINISHED");AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.adAdvance=false;
   AntMemberRpcCall.adListAfterApply=AntMemberRpcCall.response(new JSONArray().put(other));
   if(replyProcess)AntMemberRpcCall.adApplyReply=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",new JSONObject().put("taskConfigId","ad").put("processId","actual"))).toString();
   assert !MemberBrowseTasks.perform(row,"unbound-window"):"another process completed the newly applied advertisement before binding";
   JSONObject state=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
   assert state.optBoolean("applied")&&state.optBoolean("accepted")&&!state.optBoolean("submitted")&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0:"foreign completion discarded the real application or caused another write";
   assert state.optString("processId").equals(replyProcess?"actual":"")&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty()&&Log.category.isEmpty():"foreign completion created a marker/reward or replaced the reply's process";
   assert AntMemberRpcCall.processQueries>0:"a different completed process suppressed the necessary process lookup";
  }
  resetMember();JSONObject row=adTask();AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.adAdvance=false;
  AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',taskProcessId:'pad'}}}";TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.perform(row,"reply-before-wait");throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}
  JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("processId").equals("pad")&&AntMemberRpcCall.adFinish==0:"apply reply process was not persisted before the first wait";
  TimeUtil.cancelWait=false;MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("ad"),"resume-reply");assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"reply-bound process could not resume without reapplying";
  resetMember();row=adTask();AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'unrelated',processId:'other'}}}";MemberBrowseTasks.perform(row,"wrong-reply-config");
  saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("processId").isEmpty()&&AntMemberRpcCall.adFinish==0:"apply reply for another config bound the current state";
  resetMember();row=adTask().put("processId","known");AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.adApplyReply="{success:true,resultData:{taskProcessVO:{taskConfigId:'ad',processId:'other'}}}";MemberBrowseTasks.perform(row,"new-reply-process");
  saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("processId").equals("other")&&saved.optBoolean("accepted")&&saved.optLong("readyAt")>0&&AntMemberRpcCall.adFinish==0:"the accepted new process was lost or used the old process's stage";
  resetMember();row=adTask();AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.pageReplies.put(1,AntMemberRpcCall.response(new JSONArray().put(new JSONObject(row.toString()))));AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.afterAdFinish=new JSONObject(row.toString()).put("processId","unproven").put("status","FINISHED");
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());MemberBrowseTasks.run(false,Set.of(),true,Set.of());saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
  assert saved.optBoolean("submitted")&&saved.optString("processId").isEmpty()&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"a process seen only after writing was treated as the proven completion or caused a duplicate write";
  resetMember();row=adTask();AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(row,"pending-display");row.put("status","FINISHED");AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(new JSONObject(row.toString()).put("processId","other")));MemberBrowseTasks.load(false,true);
  assert RuntimeInfo.getInstance().data.containsKey("memberAd::ad")&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"a completed display bypassed the unresolved process identity";
  resetMember();row=adTask();AntMemberRpcCall.tasks.put("ad",row);AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adListAfterApply=AntMemberRpcCall.response(new JSONArray().put(new JSONObject(row.toString()).put("processId","first")).put(new JSONObject(row.toString()).put("processId","second")));
  MemberBrowseTasks.perform(row,"ambiguous-live-processes");assert AntMemberRpcCall.adFinish==0&&new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad")).optString("processId").isEmpty():"ambiguous post-apply candidates were arbitrarily bound";
  System.out.println("PASS unbound advertisement window, optional reply binding before waits, foreign completion/source isolation and reply identity validation");
 }
 static void adProcessBindingChecks()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adFinishReply="{}";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
  assert saved.optString("processId").equals("pad")&&saved.optBoolean("submitted")&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"normal post-apply snapshot did not persist the process identity";
  AntMemberRpcCall.tasks.get("ad").put("status","EXPIRED").put("currentCount",1);MemberBrowseTasks.load(false,true);assert !RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"the bound process could not close its own expired checkpoint";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("ad"),"legacy");
  saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));saved.remove("processId");String stage=saved.optString("stage");long ready=saved.optLong("readyAt");RuntimeInfo.getInstance().put("memberAd::ad",saved.toString());
  AntMemberRpcCall.tasks.get("ad").put("processId","pad");RuntimeInfo.fail=true;MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("ad"),"binding-write-failure");assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(saved.toString()):"failed identity write altered the persisted legacy state";RuntimeInfo.fail=false;
  MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("ad"),"legacy-resume");saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
  assert saved.optString("processId").equals("pad")&&saved.optString("stage").equals(stage)&&saved.optLong("readyAt")==ready&&saved.optBoolean("submitted")&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"legacy submitted state was not bound without changing its existing guards";
  String old=saved.toString();AntMemberRpcCall.tasks.get("ad").put("processId","other").put("currentCount",1).put("targetCount",2);MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("ad"),"conflict");
  assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(old)&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"a different process silently replaced the known identity on stage advancement";
  AntMemberRpcCall.tasks.get("ad").put("status","FINISHED");MemberBrowseTasks.load(false,true);assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(old)&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"another process confirmed and cleared the bound unknown state";
  resetMember();JSONObject actual=adTask().put("processId","pad"),other=new JSONObject(actual.toString()).put("processId","other").put("status","FINISHED");AntMemberRpcCall.tasks.put("ad",actual);AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adListAfterApply=AntMemberRpcCall.response(new JSONArray().put(other));
  MemberBrowseTasks.perform(actual,"snapshot-identity");assert AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.processQueries>0&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"another completed process replaced the real post-wait snapshot";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.processAdAfterApply=true;AntMemberRpcCall.adApplyReply="{}";MemberBrowseTasks.run(false,Set.of(),true,Set.of());MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  saved=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));assert saved.optString("processId").equals("pad")&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0:"unknown apply ACK either prevented binding or authorized another write";
  System.out.println("PASS post-apply process binding, read-compatible legacy state, matching expiry and conflicting-process preservation");
 }
 static void filteredActiveProtectionChecks()throws Exception{
  resetMember();JSONObject old=adTask();AntMemberRpcCall.tasks.put("ad",old);AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(old,"legacy-pending");
  JSONObject state=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));state.remove("processId");String pending=state.toString();RuntimeInfo.getInstance().put("memberAd::ad",pending);
  JSONObject expired=new JSONObject(old.toString()).put("processId","pold").put("status","EXPIRED"),broken=adTask().put("processId","pactive");broken.remove("lightsAdExtMap");
  AntMemberRpcCall.tasks.put("ad",expired);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(broken));Map<String,JSONObject> result=MemberBrowseTasks.load(false,true);
  assert result.size()==1&&MemberBrowseTasks.finished(result.values().iterator().next()):"fixture did not filter the unresolved active row while retaining the expired row";
  assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(pending):"filtering an unresolved active process authorized clearing the legacy unknown submission";
  assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"the protection replay issued another write";
  System.out.println("PASS observed-but-filtered active processes still protect uncertain legacy submissions");
 }
 static void expiredLoadChecks()throws Exception{
  for(boolean ad:new boolean[]{true,false})for(String field:List.of("status","subStatus"))for(String value:List.of("expired","EXPIRED","ExPiReD"))for(boolean full:new boolean[]{false,true}){
   resetMember();String id=ad?"ad":"one",key=ad?"memberAd::ad":"memberBrowse::pone";JSONObject row=ad?adTask().put("processId","pad"):member("one",true);AntMemberRpcCall.tasks.put(id,row);AntMemberRpcCall.advance=AntMemberRpcCall.adAdvance=false;
   MemberBrowseTasks.perform(row,"expired-load");assert RuntimeInfo.getInstance().data.containsKey(key):"fixture did not preserve the unknown submission";
   if(full)row.put("currentCount",1);row.put(field,value);MemberBrowseTasks.load(!ad,ad);
   assert MemberBrowseTasks.finished(row):"expired tasks ceased to be terminal";
   assert Log.category.isEmpty()&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"expiration became a success log or advertisement confirmation";
   assert !RuntimeInfo.getInstance().data.containsKey(key):"expired process retained a live submission checkpoint";
   row.remove("subStatus");row.put("status","TODO").put("currentCount",0);MemberBrowseTasks.perform(row,"live-again");
   assert ad?AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:AntMemberRpcCall.execute==2:"expiration blocked a later valid same-day task";
  }
  resetMember();JSONObject live=adTask().put("processId","pnew");AntMemberRpcCall.tasks.put("ad",live);AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.adFinishReply="{}";MemberBrowseTasks.perform(live,"active-process");
  String checkpoint=RuntimeInfo.getInstance().getString("memberAd::ad");JSONObject stale=new JSONObject(live.toString()).put("processId","pold").put("subStatus","expired");AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(stale));
  MemberBrowseTasks.load(false,true);assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(checkpoint):"another expired process cleared the active unknown submission";
  AntMemberRpcCall.tasks.clear();stale.put("processId","pnew").put("lightsAdExtMap",new JSONObject().put("bizId","another-biz").put("adId","ad"));AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(stale));MemberBrowseTasks.load(false,true);
  assert RuntimeInfo.getInstance().getString("memberAd::ad").equals(checkpoint):"an expired different contract cleared the active unknown submission";
  System.out.println("PASS terminal expiration without completion logs/markers across status fields, casing, conflicting success/counts and later live tasks");
 }
 static void expiredFlowChecks()throws Exception{
  for(boolean ad:new boolean[]{false,true})for(int phase=0;phase<3;phase++){
   resetMember();JSONObject row=ad?adTask():member("one",true),expired=new JSONObject(row.toString()).put("subStatus","expired").put("currentCount",1);AntMemberRpcCall.tasks.put(ad?"ad":"one",row);AntMemberRpcCall.advance=AntMemberRpcCall.adAdvance=false;
   if(phase==0){row.put("subStatus","expired").put("currentCount",1);}else if(phase==1){
    if(ad){AntMemberRpcCall.adListAfterApply=AntMemberRpcCall.response(new JSONArray().put(expired));AntMemberRpcCall.processListReply=AntMemberRpcCall.adListAfterApply;}else AntMemberRpcCall.detailAfterFirst=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",expired)).toString();
   }else if(ad)AntMemberRpcCall.afterAdFinish=expired;else AntMemberRpcCall.afterExecute=expired;
   assert !MemberBrowseTasks.perform(row,"expiry-phase-"+phase):"expiration counted as a successful execution or stage change";
   assert Log.category.isEmpty()&&Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"expiration during an operation produced a completion or reward";
   assert ad?AntMemberRpcCall.adApply==(phase==0?0:1)&&AntMemberRpcCall.adFinish==(phase==2?1:0):AntMemberRpcCall.execute==(phase==2?1:0):"a write continued after a known expiration: ad="+ad+", phase="+phase+", apply="+AntMemberRpcCall.adApply+", finish="+AntMemberRpcCall.adFinish+", execute="+AntMemberRpcCall.execute;
  }
  System.out.println("PASS expiration before execution, after waiting and after writes stops member/ad flows without success reporting");
 }
 static void periodAdvancementChecks()throws Exception{
  JSONObject next=new JSONObject().put("currentCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",1));
  for(JSONObject before:List.of(new JSONObject().put("currentCount",1),new JSONObject().put("currentCount",1).put("extInfo",new JSONObject()),new JSONObject().put("currentCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",JSONObject.NULL)),new JSONObject().put("currentCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",""))))
   assert MemberBrowseTasks.advanced(before,next):"missing baseline period failed to recognize zero-to-one progress";
  for(Object invalid:List.of(-1,0.5,"bad",true,new JSONObject())){
   assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",invalid)),next):"invalid baseline period was silently defaulted to zero";
   assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",1),new JSONObject().put("currentCount",1).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",invalid))):"invalid new period advanced the task";
  }
  assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",1).put("extInfo","bad"),next):"malformed baseline extInfo was treated as missing";
  assert !MemberBrowseTasks.advanced(new JSONObject(),new JSONObject().put("currentCount",1)):"missing main count was defaulted to zero";
  assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",1),new JSONObject(next.toString()).put("subStatus","EXPIRED")):"expired periodic progress became success";
  for(boolean ad:new boolean[]{false,true}){
   resetMember();JSONObject row=(ad?adTask():member("one",true)).put("currentCount",1).put("targetCount",5);AntMemberRpcCall.tasks.put(ad?"ad":"one",row);AntMemberRpcCall.advance=AntMemberRpcCall.adAdvance=false;AntMemberRpcCall.executeReply=AntMemberRpcCall.adFinishReply="{}";
   MemberBrowseTasks.perform(row,"old-period");String key=ad?"memberAd::ad":"memberBrowse::pone";JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString(key));assert saved.optJSONObject("before").optJSONObject("extInfo")==null;
   row.put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",1).put("PERIOD_TARGET_COUNT",5));MemberBrowseTasks.perform(row,"new-period");
   assert ad?AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:AntMemberRpcCall.execute==2:"period-only advancement left the legacy unknown checkpoint stuck";
   MemberBrowseTasks.perform(row,"still-unknown");row.optJSONObject("extInfo").put("PERIOD_CURRENT_COUNT",0);MemberBrowseTasks.perform(row,"regressed-period");
   assert ad?AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:AntMemberRpcCall.execute==2:"same or regressed period discarded an unknown submission";
   resetMember();row=(ad?adTask():member("one",true)).put("currentCount",1).put("targetCount",5);AntMemberRpcCall.tasks.put(ad?"ad":"one",row);AntMemberRpcCall.advance=AntMemberRpcCall.adAdvance=false;
   JSONObject progressed=new JSONObject(row.toString()).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",1).put("PERIOD_TARGET_COUNT",5));if(ad)AntMemberRpcCall.afterAdFinish=progressed;else AntMemberRpcCall.afterExecute=progressed;
   assert MemberBrowseTasks.perform(row,"write-period"):"period-only advancement immediately after a write was not confirmed";
  }
  System.out.println("PASS missing/empty legacy period baselines, exact integer validation, member/ad recovery, post-write progress and unknown/regression guards");
 }
 static void memberStatusPriorityChecks()throws Exception{
  for(boolean ad:new boolean[]{false,true})for(String complete:List.of("AWARDED","success","COMPLETE","done","FINISHED"))for(boolean reverse:new boolean[]{false,true}){
   resetMember();JSONObject row=ad?adTask().put("processId","pad"):member("one",true);AntMemberRpcCall.tasks.put(ad?"ad":"one",row);AntMemberRpcCall.advance=AntMemberRpcCall.adAdvance=false;MemberBrowseTasks.perform(row,"pending-completion");
   row.put(reverse?"subStatus":"status",complete).put(reverse?"status":"subStatus","expired").put("extInfo",new JSONObject().put("awardCurrentPoint",7));MemberBrowseTasks.load(!ad,ad);
   assert Log.category.size()==1&&Log.category.get(0).contains("7"):"an explicit completion was suppressed by an expired status";
   assert !ad||!Status.flagsStartingWith("memberAdConfirmed::").isEmpty():"an explicit advertisement completion lost its confirmation marker";
  }
  for(boolean liveLast:new boolean[]{false,true}){
   resetMember();JSONObject live=adTask().put("processId","pad"),ended=new JSONObject(live.toString()).put("status","EXPIRED");
   AntMemberRpcCall.tasks.put("ad",liveLast?ended:live);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(liveLast?live:ended));
   JSONObject chosen=MemberBrowseTasks.load(false,true).values().iterator().next();assert chosen.optString("status").equals(liveLast?"TODO":"EXPIRED"):"an old source overrode the later source's task status";
  }
  System.out.println("PASS explicit completion over expired sub-status, actual reward logging/confirmation and existing later-source precedence");
 }
 static void memberResumeStageChecks()throws Exception{
  for(boolean zeroFirst:new boolean[]{false,true}){
   resetMember();JSONObject old=member("one",true).put("currentCount",1).put("targetCount",5);if(zeroFirst)periodZero(old);AntMemberRpcCall.tasks.put("one",old);
   TimeUtil.cancelWait=true;try{MemberBrowseTasks.perform(old,"resume");throw new AssertionError("ordinary timer cancellation swallowed");}catch(TaskCancelledException expected){}
   JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberBrowse::pone"));String originalStage=saved.optString("stage");long ready=saved.optLong("readyAt");
   JSONObject fresh=member("one",true).put("currentCount",1).put("targetCount",5);if(!zeroFirst)periodZero(fresh);AntMemberRpcCall.tasks.put("one",fresh);AntMemberRpcCall.advance=false;TimeUtil.cancelWait=false;
   MemberBrowseTasks.perform(fresh,"resume");assert AntMemberRpcCall.execute==1:"equivalent period metadata blocked a saved ordinary timer";
   saved=new JSONObject(RuntimeInfo.getInstance().getString("memberBrowse::pone"));assert saved.optString("stage").equals(originalStage)&&saved.optLong("readyAt")==ready&&saved.optBoolean("submitted"):"equivalent metadata reset the legacy stage or timer";
  }
  resetMember();JSONObject row=member("one",true).put("currentCount",1).put("targetCount",5);AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.advance=false;TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.perform(row,"alias");throw new AssertionError();}catch(TaskCancelledException expected){}
  JSONObject saved=new JSONObject(RuntimeInfo.getInstance().getString("memberBrowse::pone")).put("stage","1:5:0.0:00");RuntimeInfo.getInstance().put("memberBrowse::pone",saved.toString());TimeUtil.cancelWait=false;
  MemberBrowseTasks.perform(row,"alias");assert AntMemberRpcCall.execute==1&&new JSONObject(RuntimeInfo.getInstance().getString("memberBrowse::pone")).optString("stage").equals("1:5:0.0:00"):"legacy numeric stage aliases were blocked or rewritten";
  System.out.println("PASS ordinary timer resume across equivalent period metadata with read-only legacy stage compatibility");
 }
 static void memberRecheckStageChecks()throws Exception{
  for(boolean zeroFirst:new boolean[]{false,true}){
   resetMember();JSONObject old=member("one",true);if(zeroFirst)periodZero(old);AntMemberRpcCall.tasks.put("one",old);AntMemberRpcCall.advance=false;
   JSONObject fresh=member("one",true);if(!zeroFirst)periodZero(fresh);AntMemberRpcCall.detailAfterFirst=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",fresh)).toString();
   MemberBrowseTasks.perform(old,"recheck");assert AntMemberRpcCall.execute==1&&TimeUtil.waits.stream().anyMatch(n->n>14000):"equivalent period metadata after waiting suppressed ordinary execution";
  }
  for(JSONObject changed:List.of(member("one",true).put("targetCount",2),periodZero(member("one",true)).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",2)))){
   resetMember();JSONObject row=member("one",true);AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.detailAfterFirst=new JSONObject().put("success",true).put("resultData",new JSONObject().put("taskProcessVO",changed)).toString();
   MemberBrowseTasks.perform(row,"changed");assert AntMemberRpcCall.execute==0:"a real target/period change reused the old ordinary timer";
  }
  System.out.println("PASS ordinary post-wait stage equivalence without ignoring real target or period changes");
 }
 static void memberSubmittedStageChecks()throws Exception{
  resetMember();JSONObject row=member("one",true);AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{}";
  MemberBrowseTasks.perform(row,"unknown");String saved=RuntimeInfo.getInstance().getString("memberBrowse::pone");int start=Log.lines.size();periodZero(row);
  MemberBrowseTasks.perform(row,"unknown");assert AntMemberRpcCall.execute==1&&RuntimeInfo.getInstance().getString("memberBrowse::pone").equals(saved):"equivalent period metadata resubmitted an unknown ordinary execution";
  assert Log.lines.subList(start,Log.lines.size()).stream().anyMatch(s->s.contains("同阶段已提交")):"equivalent period metadata was treated as an unconfirmed stage change";
  row.put("currentCount",1).put("targetCount",2);MemberBrowseTasks.perform(row,"advance");assert AntMemberRpcCall.execute==2:"confirmed ordinary progress failed to release the previous-stage submission";
  row.put("currentCount",0);MemberBrowseTasks.perform(row,"regression");assert AntMemberRpcCall.execute==2:"counter regression discarded an unknown ordinary submission";
  System.out.println("PASS unknown ordinary submission preservation, real advancement and regression guards");
 }
 static void rejectionStageChecks()throws Exception{
  for(boolean finish:new boolean[]{false,true})for(boolean zeroFirst:new boolean[]{false,true}){
   resetMember();AntMemberRpcCall.tasks.put("ad",zeroFirst?periodZero(adTask()):adTask());AntMemberRpcCall.adAdvance=false;
   if(finish)AntMemberRpcCall.adFinishReply="{success:false,resultCode:'DENIED'}";else AntMemberRpcCall.adApplyReply="{success:false,resultCode:'DENIED'}";
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());AntMemberRpcCall.tasks.put("ad",zeroFirst?adTask():periodZero(adTask()));MemberBrowseTasks.run(false,Set.of(),true,Set.of());
   assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==(finish?1:0):"period metadata bypassed a definitive advertisement rejection";
  }
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());Status.flagToday("agReject::memberAd::ad:0:1:0.0:00");MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0:"legacy rejection with numeric zero aliases was ignored";
  DailyTask.manual=true;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"canonical rejection disabled the existing manual retry";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());Status.flagToday("agReject::memberAd::ad:0:1:0.00:0");RuntimeInfo.uid="B";
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"another account inherited a legacy rejection";
  RuntimeInfo.uid="A";AntMemberRpcCall.tasks.put("ad",adTask());MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1:"returning to A forgot its legacy rejection";
  MyUtils.day++;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==2&&AntMemberRpcCall.adFinish==2:"old rejection survived into the next business day";
  System.out.println("PASS normalized apply/finish rejection keys, source changes, legacy zero aliases and explicit manual retry");
 }
 static void attemptedStageChecks()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true).put("targetCount",3));AntMemberRpcCall.zeroAdAfterExecute=true;AntMemberRpcCall.adApplyReply="{success:false,resultCode:'DENIED'}";
  int start=Log.lines.size();MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.execute==3:"source metadata changed the task attempt identity";
  assert Log.lines.subList(start,Log.lines.size()).stream().noneMatch(s->s.contains("当天已被服务端明确拒绝")):"same-run attempt deduplication repeated perform for equivalent period metadata";
  System.out.println("PASS canonical per-run attempt identity across source metadata changes and independent progressing tasks");
 }
 static void memberApplyStageChecks()throws Exception{
  for(boolean zeroFirst:new boolean[]{false,true}){
   resetMember();AntMemberRpcCall.tasks.put("one",zeroFirst?periodZero(member("one",false)):member("one",false));AntMemberRpcCall.applyReply="{}";
   MemberBrowseTasks.run(true,Set.of(),false,Set.of());AntMemberRpcCall.tasks.put("one",zeroFirst?member("one",false):periodZero(member("one",false)));MemberBrowseTasks.run(true,Set.of(),false,Set.of());
   assert AntMemberRpcCall.apply==1:"equivalent period metadata repeated an unknown ordinary signup";
  }
  for(String oldProgress:List.of("0:1:","0:1::","0:1:0.0:00")){
   resetMember();Calendar now=MyUtils.getInstance();String prefix=now.get(Calendar.YEAR)+":"+now.get(Calendar.DAY_OF_YEAR)+":";String old=prefix+oldProgress;
   AntMemberRpcCall.tasks.put("one",periodZero(member("one",false)));AntMemberRpcCall.applyReply="{}";RuntimeInfo.getInstance().put("memberApply::one",old);
   MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==0&&RuntimeInfo.getInstance().getString("memberApply::one").equals(old):"legacy signup proof was ignored or rewritten";
   AntMemberRpcCall.tasks.get("one").put("currentCount",1).put("targetCount",2);MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==1:"real new stage could not sign up";
   MyUtils.day++;MemberBrowseTasks.run(true,Set.of(),false,Set.of());assert AntMemberRpcCall.apply==2:"previous business day blocked today's signup";
  }
  System.out.println("PASS canonical signup checkpoint, read-only legacy compatibility and new-stage/day independence");
 }
 static void adDailyLookupChecks()throws Exception{
  resetMember();JSONObject old=adTask().put("currentCount",1).put("targetCount",2);Calendar yesterday=MyUtils.getInstance();yesterday.add(Calendar.DATE,-1);Calendar now=MyUtils.getInstance();
  String oldDay=yesterday.get(Calendar.YEAR)+":"+yesterday.get(Calendar.DAY_OF_YEAR),day=now.get(Calendar.YEAR)+":"+now.get(Calendar.DAY_OF_YEAR);
  JSONObject state=new JSONObject().put("day",oldDay).put("stage",MemberBrowseTasks.progress(old)).put("before",old).put("contract",MemberBrowseTasks.adContract(old).toString()).put("applied",true).put("accepted",true).put("submitted",true);
  AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.processListReply="{success:false,resultCode:'1009'}";RuntimeInfo.getInstance().put("memberAd::ad",state.toString());
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"yesterday's stage forced a failing source and blocked today's advertisement";
  resetMember();RuntimeInfo.getInstance().put("memberAd::ad",state.toString());AntMemberRpcCall.processListReply="{}";MemberBrowseTasks.load(false,true);
  assert AntMemberRpcCall.processQueries==0&&!RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"expired record locked itself out of complete-snapshot cleanup";
  resetMember();RuntimeInfo.getInstance().put("memberAd::ad",state.toString());AntMemberRpcCall.pageOverride="{}";MemberBrowseTasks.load(false,true);assert RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"date filtering deleted an unknown record without complete sources";
  resetMember();RuntimeInfo.getInstance().put("memberAd::ad",new JSONObject(state.toString()).put("day",day).toString());AntMemberRpcCall.processListReply="{}";MemberBrowseTasks.load(false,true);
  assert AntMemberRpcCall.processQueries==1&&RuntimeInfo.getInstance().data.containsKey("memberAd::ad"):"current-day unknown record lost its required lookup/retention";
  System.out.println("PASS ad lookup dates, today's task independence, complete-only old cleanup and today's unknown retention");
 }
 static void adPeriodStageChecks()throws Exception{
  assert MemberBrowseTasks.sameProgress("1:5:","1:5:0:0"):"missing period fields differed from zero period counters";
  assert MemberBrowseTasks.sameProgress("1:5::","1:5:0.0:00"):"numeric zero representations changed the stage";
  for(String other:List.of("1:5:0:5","1:5:1:5","2:5:0:0","1:5:bad:0","1:5:-1:0"))assert !MemberBrowseTasks.sameProgress("1:5:",other):"meaningful/invalid stage was treated as initial zero: "+other;
  assert !MemberBrowseTasks.sameProgress(":5:","0:5:0:0"):"missing main count was silently changed to zero";
  for(boolean reverse:new boolean[]{false,true}){
   resetMember();JSONObject timed=adTask(),reward=adTask().put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));timed.optJSONObject("simpleTaskConfig").put("browseSeconds",30).remove("awardParam");reward.optJSONObject("simpleTaskConfig").remove("browseSeconds");
   AntMemberRpcCall.tasks.put("ad",reverse?reward:timed);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(reverse?timed:reward));
   MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&TimeUtil.waits.stream().anyMatch(n->n>=29000):"zero period metadata prevented the real merged contract from running";
  }
  System.out.println("PASS absent/zero period equivalence, real/invalid period distinction and source-order merged execution");
 }
 static void adKeyChecks()throws Exception{
  String contract=MemberBrowseTasks.adContract(adTask()).toString(),reordered="{\"awardNum\":10,\"seconds\":15,\"adId\":\"ad\",\"bizId\":\"biz\"}";
  String key=MemberBrowseTasks.adConfirmedKey("ad","p","0:1:",contract);
  assert key.matches("memberAdConfirmed::[0-9a-f]{64}"):"confirmation key persisted full JSON/identifiers instead of a compact digest";
  assert key.equals(MemberBrowseTasks.adConfirmedKey("ad","p","0:1:0:0",reordered)):"key order or equivalent period metadata changed the digest";
  assert !key.equals(MemberBrowseTasks.adConfirmedKey("ad","new","0:1:",contract));assert !key.equals(MemberBrowseTasks.adConfirmedKey("ad","p","1:1:",contract));
  assert !key.equals(MemberBrowseTasks.adConfirmedKey("ad","p","0:1:",new JSONObject(contract).put("seconds",30).toString()));
  assert !MemberBrowseTasks.adConfirmedKey("a:b","c","0:1:",contract).equals(MemberBrowseTasks.adConfirmedKey("a","b:c","0:1:",contract)):"identity separators collided";
  assert !key.equals(MemberBrowseTasks.adConfirmedKey("other","p","0:1:",contract));
  for(String field:List.of("bizId","adId","seconds","awardNum")){
   JSONObject changed=new JSONObject(contract);changed.put(field,field.equals("bizId")||field.equals("adId")?"other":field.equals("seconds")?16:11);
   assert !key.equals(MemberBrowseTasks.adConfirmedKey("ad","p","0:1:",changed.toString())):"digest omitted contract field "+field;
  }
  for(String invalid:List.of("broken",new JSONObject(contract).put("seconds",14).toString(),new JSONObject(contract).put("awardNum",-1).toString())){
   try{MemberBrowseTasks.adConfirmedKey("ad","p","0:1:",invalid);throw new AssertionError("invalid contract produced a confirmation key");}catch(IllegalArgumentException expected){}
  }
  resetMember();JSONObject row=adTask().put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));AntMemberRpcCall.tasks.put("ad",row);
  Status.flagToday("memberAdConfirmed::ad::0:1:"+contract);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0&&Status.hasFlagToday(MemberBrowseTasks.adConfirmedKey("ad","","0:1:",contract)):"legacy daily confirmation was lost instead of migrated";
  MyUtils.day++;MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"yesterday's legacy/compact marker blocked a new day";
  resetMember();JSONObject noCounts=adTask();noCounts.remove("currentCount");noCounts.remove("targetCount");noCounts.put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",0).put("PERIOD_TARGET_COUNT",0));AntMemberRpcCall.tasks.put("ad",noCounts);
  Status.flagToday("memberAdConfirmed::ad:::"+contract);MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0:"legacy marker without optional main counters was lost";
  System.out.println("PASS compact canonical confirmation digests, delimiter isolation and legacy daily marker migration");
 }
 static void adConflictLogs()throws Exception{
  resetMember();JSONObject conflict=adTask();conflict.optJSONObject("simpleTaskConfig").put("browseSeconds",30);AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(conflict));
  int start=Log.lines.size();for(int i=0;i<20;i++)MemberBrowseTasks.load(false,true);
  assert Log.lines.subList(start,Log.lines.size()).stream().filter(s->s.contains("合同冲突")).count()==1:"the same conflict flooded repeated snapshots";
  assert MemberBrowseTasks.load(false,true).isEmpty():"log deduplication removed the execution guard";
  RuntimeInfo.uid="B";MemberBrowseTasks.load(false,true);RuntimeInfo.uid="A";MemberBrowseTasks.load(false,true);MyUtils.day++;MemberBrowseTasks.load(false,true);
  assert Log.lines.subList(start,Log.lines.size()).stream().filter(s->s.contains("合同冲突")).count()==3:"conflict log deduplication leaked across accounts or GMT+8 days";
  System.out.println("PASS advertisement conflict log deduplication, account/day isolation and repeated safety filtering");
 }
 static void adContractIdentities()throws Exception{
  resetMember();JSONObject display=adTask();display.optJSONObject("simpleTaskConfig").remove("browseSeconds");display.optJSONObject("simpleTaskConfig").remove("awardParam");
  JSONObject active=adTask().put("processId","activeAd");JSONObject cfg=active.optJSONObject("simpleTaskConfig");active.remove("simpleTaskConfig");active.put("taskConfigInfo",cfg.put("browseSeconds",30));
  AntMemberRpcCall.tasks.put("display",display);AntMemberRpcCall.tasks.put("active",active);
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());
  assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&TimeUtil.waits.stream().anyMatch(n->n>=29000):"display defaults conflicted with the active process contract";
  resetMember();display=adTask();display.optJSONObject("simpleTaskConfig").put("browseSeconds",30);JSONObject oldDisplay=adTask();
  active=adTask().put("processId","activeAd");AntMemberRpcCall.tasks.put("display",display);AntMemberRpcCall.tasks.put("active",active);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(oldDisplay));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1:"discarded display ambiguity blocked an independent active process";
  resetMember();JSONObject conflict=adTask();conflict.optJSONObject("simpleTaskConfig").put("browseSeconds",30);JSONObject good=adTask().put("taskConfigId","good");good.optJSONObject("simpleTaskConfig").put("configId","good");
  AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("good",good);AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(conflict));
  RuntimeInfo store=RuntimeInfo.getInstance();store.put("memberAd::ad","{day:'old',submitted:true}");store.put("memberAd::gone","{day:'old',submitted:true}");
  int start=Log.lines.size();Map<String,JSONObject> loaded=MemberBrowseTasks.load(false,true);
  assert !loaded.containsKey("ad:")&&loaded.containsKey("good:"):"a conflicting contract hid an independent advertisement";
  assert store.data.containsKey("memberAd::ad")&&!store.data.containsKey("memberAd::gone"):"one ambiguous advertisement blocked unrelated cleanup or lost its unknown submission";
  assert Log.lines.subList(start,Log.lines.size()).stream().anyMatch(s->s.contains("taskad")&&s.contains("合同冲突")):"conflicting advertisement was silently discarded";
  System.out.println("PASS advertisement process identity, active contract precedence, conflict diagnostics and scoped unknown-state cleanup");
 }
 static void candidateAndClassifiedPauses()throws Exception{
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());MemberBrowseTasks.run(true,Set.of(),false,Set.of());
  assert AntMemberTaskListMap.names.containsKey("taskad")&&AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.adApply==0:"disabled advertisement disappeared from blacklist candidates";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.processListReply="{}";MemberBrowseTasks.run(true,Set.of(),false,Set.of());
  assert AntMemberTaskListMap.names.containsKey("taskone")&&AntMemberRpcCall.apply==0:"execution safety filtering removed a valid blacklist candidate";
  resetMember();JSONObject ad=adTask();AntMemberRpcCall.tasks.put("ad",ad);JSONObject contract=MemberBrowseTasks.adContract(ad);Calendar now=MyUtils.getInstance();
  RuntimeInfo store=RuntimeInfo.getInstance();store.put("memberAd::ad",new JSONObject().put("contract",contract.toString()).put("day",now.get(Calendar.YEAR)+":"+now.get(Calendar.DAY_OF_YEAR)).put("submitted",true).put("before",new JSONObject(ad.toString())).toString());
  ad.put("status","FINISHED");AntMemberRpcCall.adListReply="{}";MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  assert AntMemberTaskListMap.names.containsKey("taskad")&&store.data.containsKey("memberAd::ad")&&Log.category.isEmpty():"candidate registration falsely confirmed an incomplete advertisement snapshot";
  for(String code:List.of("48","TRANSPORT_ERROR","3000","SYSTEM_ERROR","1GBM02","UNKNOWN")){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply=new JSONObject().put("success",false).put("error","RPC_SKIPPED").put("causeCode",code).put("riskPause",false).toString();
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.execute==1:"classified non-risk pause blocked independent tasks: "+code;
  }
  for(Object risk:new Object[]{true,"false",JSONObject.NULL}){
   resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply=new JSONObject().put("success",false).put("error","RPC_SKIPPED").put("causeCode","102").put("riskPause",risk).toString();
   MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"risk or malformed pause classification continued independent requests";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,error:'RPC_SKIPPED',causeCode:'102'}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"legacy pause without classification continued requests";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply="{success:false,error:'RPC_SKIPPED',causeCode:'1009',riskPause:false}";
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.processQueries==0&&AntMemberRpcCall.execute==0:"explicit risk code bypassed a contradictory non-risk flag";
  resetMember();JSONObject ambiguous=adTask();ambiguous.optJSONObject("simpleTaskConfig").put("browseSeconds",30);AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.adListReply=AntMemberRpcCall.response(new JSONArray().put(ambiguous));
  MemberBrowseTasks.run(true,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0&&AntMemberRpcCall.execute==1:"conflicting advertisement contracts were executed or blocked independent ordinary tasks";
  resetMember();JSONObject unidentifiedAd=adTask();unidentifiedAd.remove("taskConfigId");unidentifiedAd.optJSONObject("simpleTaskConfig").remove("configId");AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("invalid",unidentifiedAd);
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0:"unidentifiable advertisement authorized another potentially conflicting advertisement";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("invalid",new JSONObject().put("status","TODO"));AntMemberRpcCall.splitPages=true;
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.pages.contains(3):"a page containing only dirty ordinary rows prevented reaching the terminal page";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());String dirtyPage=AntMemberRpcCall.response(new JSONArray().put(new JSONObject().put("status","TODO")));AntMemberRpcCall.pageReplies.put(2,dirtyPage);AntMemberRpcCall.pageReplies.put(3,dirtyPage);
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0&&Collections.max(AntMemberRpcCall.pages)==3:"repeated dirty pages did not stop bounded pagination";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());for(int page=2;page<=64;page++)AntMemberRpcCall.pageReplies.put(page,AntMemberRpcCall.response(new JSONArray().put(new JSONObject().put("ordinaryNoise",page))));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0&&Collections.max(AntMemberRpcCall.pages)==3:"changing dirty rows caused an excessive pagination burst";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());
  for(int page=2;page<=64;page++)AntMemberRpcCall.pageReplies.put(page,AntMemberRpcCall.response(new JSONArray().put(member("valid"+page,true))));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0&&Collections.max(AntMemberRpcCall.pages)==64:"dirty-page budget prematurely capped valid pagination";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());
  AntMemberRpcCall.pageReplies.put(2,dirtyPage);AntMemberRpcCall.pageReplies.put(3,AntMemberRpcCall.response(new JSONArray().put(member("middle",true))));
  AntMemberRpcCall.pageReplies.put(4,dirtyPage);AntMemberRpcCall.pageReplies.put(5,AntMemberRpcCall.response(new JSONArray()));
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==1&&AntMemberRpcCall.pages.contains(5):"a valid page failed to reset the consecutive-dirty-page budget";
  resetMember();AntMemberRpcCall.tasks.put("ad",adTask());AntMemberRpcCall.tasks.put("invalid",unidentifiedAd);AntMemberRpcCall.splitPages=true;
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());assert AntMemberRpcCall.adApply==0:"unidentifiable advertisement on its own page lost the identity barrier";
  System.out.println("PASS candidates before execution filters, classified endpoint pauses, ordinary/ad identity isolation and contract conflicts");
 }
 static void members()throws Exception{
  for(String invalid:List.of("", "not-a-contract", "BROWSE##one", "BROWSE#sub#")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",invalid);AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(true, Set.of(), true, Set.of());
   assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"invalid top-level string hid valid config target: "+invalid;
  }
  for(String invalid:List.of("", "not-a-contract", "BROWSE##one", "BROWSE#sub#")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",invalid);row.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put("BROWSE##one"));AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"invalid primary and fallback targets issued a write";
  }
  resetMember();JSONObject blacklisted=member("one",false).put("targetBusiness","");AntMemberRpcCall.tasks.put("one",blacklisted);MemberBrowseTasks.run(true, Set.of("taskone"), true, Set.of("taskone"));assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"fallback bypassed task blacklist";
  JSONObject priority=member("one",false).put("targetBusiness","BROWSE#top#one");assert MemberBrowseTasks.target(priority)[1].equals("top"):"valid top-level contract lost priority";

  for(String key:List.of("simpleTaskConfig","taskConfigInfo")) {
   resetMember();JSONObject row=member("one",false);JSONObject cfg=row.optJSONObject("simpleTaskConfig");cfg.remove("targetBusiness");
   if(key.equals("taskConfigInfo")){row.remove("simpleTaskConfig");row.put(key,cfg);cfg.put("id","one").put("name","taskone");}
   row.put("targetBusiness","BROWSE#sub#one");AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(true, Set.of(), true, Set.of());
   assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"string top-level target must use the existing verified browse flow";
  }
  resetMember();JSONObject stringConfig=member("one",true);stringConfig.optJSONObject("simpleTaskConfig").put("targetBusiness","BROWSE#sub#one");AntMemberRpcCall.tasks.put("one",stringConfig);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1;
  for(String bad:List.of("CALL_APP#scene","CALL_APP#sub#one","OTHER#sub#one")) {
   resetMember();JSONObject row=member("one",false).put("targetBusiness",bad);AntMemberRpcCall.tasks.put("one",row);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0:"invalid string target sent a write";
  }

  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1;assert AntMemberRpcCall.pages.contains(2);assert TimeUtil.waits.stream().mapToLong(Long::longValue).sum()>=15000;
  resetMember();JSONObject ordinary=member("one",false);ordinary.optJSONObject("simpleTaskConfig").put("sourceBusiness","antmember");AntMemberRpcCall.tasks.put("one",ordinary);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"ordinary apply process must continue only once";
  resetMember();AntMemberRpcCall.repeatPage=true;AntMemberRpcCall.tasks.put("one",member("one",true));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1&&Collections.max(AntMemberRpcCall.pages)==2;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));MemberBrowseTasks.run(true, Set.of("taskone"), true, Set.of("taskone"));assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.emptyMap=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1:"Unknown execute must only requery";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.cancel=true;try{MemberBrowseTasks.perform(AntMemberRpcCall.tasks.get("one"),"test");throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.execute==0;
  TimeUtil.cancel=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.brokenDetail=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));RuntimeInfo.fail=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==0;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.omitConfig=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1:"detail without static config must use matched list config";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.statusOnly=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1:"status text is not numeric progress";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.tasks.put("two",member("two",true));AntMemberRpcCall.splitPages=true;
  assert MemberBrowseTasks.load(true,true).size()==2&&AntMemberRpcCall.pages.equals(List.of(1,2,3))&&AntMemberRpcCall.sessions.size()==1:"all pages must share session";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.cancelWait=true;
  try{MemberBrowseTasks.run(true, Set.of(), true, Set.of());throw new AssertionError();}catch(TaskCancelledException expected){}assert AntMemberRpcCall.execute==0;
  TimeUtil.cancelWait=false;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));TimeUtil.rollDay=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==0:"cross-day browser action";
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true).put("targetCount",3));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==3:"advance stages by verified count";
  resetMember();AntMemberRpcCall.tasks.put("old",member("one",true).put("processId","old").put("status","FINISHED"));AntMemberRpcCall.tasks.put("one",member("one",false));MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==1&&AntMemberRpcCall.execute==1:"finished history must not hide new task";
  assert !MemberBrowseTasks.advanced(new JSONObject().put("currentCount",2),new JSONObject().put("currentCount",1));
  assert MemberBrowseTasks.advanced(new JSONObject().put("currentCount",2),new JSONObject().put("currentCount",3));
  assert MemberBrowseTasks.rows("{success:true,resultData:{taskProcessVOList:[null]}}") == null;
  assert MemberBrowseTasks.rows("{success:true,resultData:{categoryTaskList:[{taskProcessVOList:[]}]}}").isEmpty();
  resetMember();JSONObject two=member("one",true);two.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put("sub#one"));two.put("extInfo",new JSONObject().put("awardCurrentPoint",5));AntMemberRpcCall.tasks.put("one",two);MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1&&!Log.category.isEmpty()&&Log.category.get(0).contains("5");
  assert MemberBrowseTasks.target(member("one",false).put("targetBusiness",new JSONArray().put("CALL_APP#scene")))==null;
  for(String target:new String[]{"CALL_APP#scene", "CALL_APP#sub#param", "OTHER#sub#param", "BROWSE##param"}) {
   resetMember();JSONObject unsupported=member("one",false);unsupported.optJSONObject("simpleTaskConfig").put("targetBusiness",new JSONArray().put(target));
   AntMemberRpcCall.tasks.put("one",unsupported);MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());
   assert AntMemberRpcCall.apply==0&&AntMemberRpcCall.execute==0 : "unsupported member task issued an RPC";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{success:false,resultCode:'UNSUPPORTED'}";MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert MessageUtil.rejected==1;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{}";MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert MessageUtil.rejected==0;
  reset();RpcContract.signPageTaskList(2,"session");JSONObject payload=ApplicationHook.calls.get(0);assert payload.optInt("pageNo")==2&&payload.optString("session").equals("session")&&payload.optBoolean("switchNormal");assert payload.optJSONObject("sourcePassMap").optString("source").equals("mytab");
  RpcContract.currentMemberProcesses();assert ApplicationHook.calls.get(1).optString("rpc").equals("com.alipay.alipaymember.biz.rpc.membertask.h5.queryTaskList");
  RpcContract.applyCurrentMemberTask("id",true);assert ApplicationHook.calls.get(2).optJSONArray("taskConfigIdList").optString(0).equals("id");
  RpcContract.executeCurrentMemberTask("sub","param");assert ApplicationHook.calls.get(3).optString("bizType").equals("BROWSE");
  RpcContract.currentMemberAdTasks();assert ApplicationHook.calls.get(4).length()==2&&!ApplicationHook.calls.get(4).has("sourcePassMap");
 }
 static void reviewRegressions()throws Exception{
  for(boolean resetCounts:new boolean[]{false,true}){
   resetMember();JSONObject row=member("one",true).put("currentCount",2).put("targetCount",5).put("extInfo",new JSONObject().put("PERIOD_CURRENT_COUNT",2).put("PERIOD_TARGET_COUNT",5));AntMemberRpcCall.tasks.put("one",row);AntMemberRpcCall.advance=false;
   MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1;
   MyUtils.day++;if(resetCounts){row.put("currentCount",0);row.optJSONObject("extInfo").put("PERIOD_CURRENT_COUNT",0);}MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==2:"new day must not remain blocked by old submitted/counts";
  }
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",true));AntMemberRpcCall.advance=false;AntMemberRpcCall.executeReply="{success:false,resultCode:'NO_QUALIFICATION'}";
  MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==1;DailyTask.manual=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.execute==2;
  resetMember();AntMemberRpcCall.tasks.put("one",member("one",false));AntMemberRpcCall.applyReply="{success:false,resultCode:'NO_QUALIFICATION'}";MemberBrowseTasks.run(true, Set.of(), true, Set.of());MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==1;DailyTask.manual=true;MemberBrowseTasks.run(true, Set.of(), true, Set.of());assert AntMemberRpcCall.apply==2;
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
  AntMemberRpcCall.repeatPage=true;MemberBrowseTasks.load(true,true);assert a.data.containsKey("memberBrowse::gone"):"incomplete snapshot must preserve unknown writes";
  AntMemberRpcCall.repeatPage=false;AntMemberRpcCall.tasks.put("invalid",new JSONObject().put("status","TODO"));MemberBrowseTasks.load(true,true);assert a.data.containsKey("memberBrowse::gone"):"missing task ID must not authorize cleanup";AntMemberRpcCall.tasks.remove("invalid");
  MemberBrowseTasks.load(true,true);assert !a.data.containsKey("memberBrowse::gone")&&a.data.containsKey("memberBrowse::pone"):"active pending process must survive cleanup";
  AntMemberRpcCall.tasks.get("one").put("status","FINISHED");MemberBrowseTasks.load(true,true);assert !a.data.containsKey("memberBrowse::pone")&&Log.category.isEmpty();MemberBrowseTasks.load(true,true);assert Log.category.isEmpty():"old-day late confirmation must not credit today's reward";
 }
 public static void main(String[] args)throws Exception{if(args.length>0){switch(args[0]){case "logs":adConflictLogs();return;case "processes":pendingAdProcesses();return;case "dates":adDailyLookupChecks();return;case "periods":adPeriodStageChecks();return;case "keys":adKeyChecks();return;case "rejects":rejectionStageChecks();return;case "attempts":attemptedStageChecks();return;case "applyguards":memberApplyStageChecks();return;case "memberresume":memberResumeStageChecks();return;case "memberrecheck":memberRecheckStageChecks();return;case "membersubmitted":memberSubmittedStageChecks();return;case "expiredload":expiredLoadChecks();return;case "expiredflows":expiredFlowChecks();return;case "periodadvance":periodAdvancementChecks();return;case "statuspriority":memberStatusPriorityChecks();return;case "adbinding":adProcessBindingChecks();return;case "filteredactive":filteredActiveProtectionChecks();return;case "adunbound":adUnboundWindowChecks();return;case "adexpirywindow":adUnboundExpiryChecks();return;case "adreplyswitch":adAuthoritativeReplyChecks();return;case "adreplyclosure":adReplyStageClosureChecks();return;case "adperiodonly":adPeriodOnlyReplyChecks();return;case "adpostreplyguards":adPostReplyGuardChecks();return;case "adincompleteperiod":adIncompleteReplyPeriodChecks();return;case "adcachedcleanup":adCachedCleanupChecks();return;case "olddayconfirm":oldDayConfirmationChecks();return;case "oldmemberday":oldMemberDayChecks();return;}}oldDayConfirmationChecks();oldMemberDayChecks();adIncompleteReplyPeriodChecks();adCachedCleanupChecks();adPeriodOnlyReplyChecks();adPostReplyGuardChecks();adReplyStageClosureChecks();adUnboundExpiryChecks();adAuthoritativeReplyChecks();adUnboundWindowChecks();adProcessBindingChecks();filteredActiveProtectionChecks();memberStatusPriorityChecks();expiredLoadChecks();expiredFlowChecks();periodAdvancementChecks();memberResumeStageChecks();memberRecheckStageChecks();memberSubmittedStageChecks();rejectionStageChecks();attemptedStageChecks();memberApplyStageChecks();adDailyLookupChecks();adPeriodStageChecks();adKeyChecks();partialAdContracts();pendingAdProcesses();adConflictLogs();adContractIdentities();candidateAndClassifiedPauses();sourceFailures();games();members();adChecks();splitAdChecks();cleanup();reviewRegressions();System.out.println("PASS AG task contracts/duration/checkpoints/unknown ACK/accounts/cancellation");}
}
'''

# Compile the actual outer AntMember.run too: adapter-local stop tests do not prove the module stops.
outer_run = method('model/task/antMember/AntMember.java', '    public void run()')
outer_fields = set(re.findall(r'\b(\w+)\.getValue\(\)', outer_run))
outer_sets = {'AntMemberTaskList', 'AntMemberAdTaskList', 'beanExchangeRightList', 'yebExpGoldTaskList',
              'yebTrialExchangeCampaigns', 'sesameGrainExchangeList', 'WelfareFundTaskList'}
outer_ints = {'beanExchangeRightBudget', 'yebExpGoldTaskBudget', 'yebVoucherDailyBudget',
              'yebTrialExchangeBudget', 'yebTrialActivationBudget'}
outer = 'static class Field<T>{T value;Field(T v){value=v;}T getValue(){return value;}}\n'
outer += 'static int laterServiceCalls; static class OuterMember {static final String TAG="AntMember";\n'
for name in sorted(outer_fields):
    kind, value = ('Set<String>', 'Set.of()') if name in outer_sets else ('Integer', '0') if name in outer_ints else ('String', '""') if name == 'yebTrialActivationIds' else ('Boolean', 'false')
    outer += f'Field<{kind}> {name}=new Field<>({value});\n'
for name in ('creditGameCenterTaskPrize', 'initMemberTaskListMap', 'memberSign', 'queryPointCert',
             'memberPointExchangeBenefit', 'scheduleMemberExchange', 'CheckInTaskRpcManager', 'collectSesame',
             'handleGrowthGuideTasks', 'queryAndCollect', 'RecommendTask', 'OrdinaryTask', 'goldTicket',
             'checkAndDoSignIn', 'queryAndProcessTaskList', 'gameCenterTaskPrize', 'queryPointBallList'):
    outer += f'void {name}(Object... args){{}}\n'
outer += 'void collectBillStickers(){laterServiceCalls++;}\n' + outer_run + '\n}\n'
for name, methods in {
        'BillBlockWorld': ('run',), 'MerchantService': ('run',), 'SesameAchievements': ('run',),
        'InsuredGold': ('run',), 'BeanRewards': ('run', 'runBrowse', 'runQuiz', 'runDraw', 'runExchange'),
        'YebExpGold': ('runTasks', 'run'), 'YebVouchers': ('run',), 'SesameGrainExchange': ('run',),
        'WelfareFund': ('run',)}.items():
    outer += 'static class ' + name + '{' + ''.join('static void ' + m + '(Object... args){}' for m in methods) + '}\n'
outer += r'''
 static void outerMemberPauseChecks()throws Exception {
  for(String response:List.of("{success:false,resultCode:'1009'}", "{success:false,error:'RPC_SKIPPED',causeCode:'UNKNOWN'}")) {
   resetMember();laterServiceCalls=0;AntMemberRpcCall.pageOverride=response;
   OuterMember flow=new OuterMember();flow.AntMemberTask.value=true;flow.collectStickers.value=true;flow.run();
   assert AntMemberRpcCall.pages.equals(List.of(1));
   assert AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0;
   assert laterServiceCalls==0:"risk/unknown pause escaped adapter and continued outer AntMember services: "+response;
  }
  resetMember();laterServiceCalls=0;AntMemberRpcCall.pageOverride="{success:false,resultCode:'102'}";
  OuterMember busy=new OuterMember();busy.AntMemberTask.value=true;busy.collectStickers.value=true;busy.run();
  assert laterServiceCalls==1:"ordinary endpoint busy failure incorrectly stopped independent outer services";
  resetMember();laterServiceCalls=0;
  OuterMember disabled=new OuterMember();disabled.collectStickers.value=true;disabled.run();
  assert AntMemberRpcCall.pages.isEmpty()&&AntMemberRpcCall.adQueries==0&&AntMemberRpcCall.processQueries==0;
  assert laterServiceCalls==1:"disabled browse switches incorrectly stopped independent outer services";
  System.out.println("PASS actual AntMember.run stops on browse risk/unknown pause and continues ordinary busy/disabled paths");
 }
'''
code = code.replace('static class MessageUtil {', 'static class MessageUtil {static Set<String> presetBlackList(String a,String b){return Set.of();}static void syncTaskBlackList(Object... args){}')
code = code.replace('static class Log {', 'static class Log {static void i(String s){}static void printStackTrace(String s,Throwable t){throw new AssertionError(t);}')
code = code.rstrip()[:-1] + outer + '\n}\n'
code = code.replace('public static void main(String[] args)throws Exception{', 'public static void main(String[] args)throws Exception{outerMemberPauseChecks();')


legacy_source = (SOURCE/'model/task/antGame/GameTask.java').read_text(encoding='utf-8')
legacy_counts = re.findall(r'^    (?:Orchard|Farm|Forest)_.*?, (\d+)\)[,;]', legacy_source, re.M)
assert legacy_counts == ['2', '2', '3', '3']
code = code.replace('@@LEGACY_COUNTS@@', ','.join(legacy_counts))
code = code.replace('@@MEMBER@@', nested('model/task/antMember/MemberBrowseTasks.java', 'MemberBrowseTasks'))
code = code.replace('@@STATUS_FLAGS@@', method('util/Status.java', '    public static synchronized Set<String> flagsStartingWith('))
code = code.replace('@@RPC@@', '\n'.join(method('model/task/antMember/AntMemberRpcCall.java', sig) for sig in ('private static String currentMemberRequest(', 'public static String signPageTaskList(int', 'public static String currentMemberProcesses(', 'public static String currentMemberAdTasks(', 'public static String applyCurrentMemberTask(', 'public static String executeCurrentMemberTask(', 'public static String currentMemberTaskDetail(', 'public static String applyAdTask(', 'public static String finishAdTask(')))
policy=(SOURCE/'model/base/TaskAlternative.java').read_text(encoding='utf-8')
code=code.replace('@@TRANSACTION@@',re.search(r'private static final String\[\] TRANSACTION_KEYWORDS = \{[\s\S]*?\};',policy).group(0)+'\n'+method('model/base/TaskAlternative.java','    public static boolean isTransactionTask('))
code = code.replace('@@GAME@@', nested('model/common/GameTaskAction.java', 'GameTaskAction'))
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = next(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))
with tempfile.TemporaryDirectory(prefix='sesame-ag-task-sync-') as tmp:
    java = Path(tmp)/'AgTaskSyncCheck.java';java.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java)],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'AgTaskSyncCheck',*sys.argv[1:]],check=True)


# Fault-inject actual Status.save for all ad closure paths, preserving the standard fixture above.
durability_code = code
start = durability_code.index(' static class Status {')
end = durability_code.index(' static class DailyTask', start)
real_status = r'''
 static class Status {
  static final Status INSTANCE=new Status();static final String TAG="Status";
  Set<String> flagLogList=new HashSet<>();static Set<String> flags=INSTANCE.flagLogList;
  long saveTime;static boolean saveFailureNotified;
  static String ensureLoadedForCurrentUid(){return RuntimeInfo.uid;}
  static boolean hasFlagToday(String key){return flags.contains(key);}
  static boolean updateDay(Calendar now){return false;}
  static void save(){save(MyUtils.getInstance());}
  static void notifySaveFailure(String uid,String reason,Throwable error){FileUtil.failures++;}
'''
real_status += method('util/Status.java', '    public static synchronized void flagToday(String tag)')
real_status += method('util/Status.java', '    public static synchronized boolean flagTodayVerified(')
real_status += method('util/Status.java', '    public static synchronized boolean save(Calendar nowCalendar)')
real_status += method('util/Status.java', '    public static synchronized Set<String> flagsStartingWith(')
real_status += r'''
 }
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class UserIdMap {static String getCurrentUid(){return RuntimeInfo.uid;}}
 static class FileUtil {
  static boolean fail;static int failures,writes;static Set<String> persisted=new HashSet<>();
  static List<Set<String>> snapshots=new ArrayList<>();
  static java.io.File getStatusFile(String uid){return new java.io.File("unused");}
  static boolean write2File(String ignored,java.io.File unused){writes++;snapshots.add(new HashSet<>(Status.flags));if(fail)return false;persisted=new HashSet<>(Status.flags);return true;}
  static void restart(){Status.flags.clear();Status.flags.addAll(persisted);}
 }
 static class JsonUtil {static String toFormatJsonString(Object ignored){return "ignored";}}
'''
durability_code = durability_code[:start] + real_status + durability_code[end:]
durability_code = durability_code.replace('static class Log {', 'static class Log {static void system(String a,String b){}static void debug(String s){}')
durability_code = durability_code.replace('static class TimeUtil {', 'static class TimeUtil {static Calendar getNow(){return MyUtils.getInstance();}')
durability_code = durability_code.replace('public static void main(String[] args)', 'public static void originalMain(String[] args)')
tests = r'''
 static JSONObject pendingAd(JSONObject row)throws Exception{
  Calendar now=MyUtils.getInstance();return new JSONObject().put("day",now.get(Calendar.YEAR)+":"+now.get(Calendar.DAY_OF_YEAR))
   .put("stage",MemberBrowseTasks.progress(row)).put("before",MemberBrowseTasks.snapshotProgress(row))
   .put("contract",MemberBrowseTasks.adContract(row).toString()).put("processId",MemberBrowseTasks.processId(row))
   .put("applied",true).put("accepted",true).put("submitted",false).put("readyAt",System.currentTimeMillis()-1);
 }
 static void resetDurable(){resetMember();FileUtil.fail=false;FileUtil.failures=FileUtil.writes=0;FileUtil.persisted.clear();FileUtil.snapshots.clear();}
 static void requireConfirmedPending(String path)throws Exception{
  JSONObject pending=new JSONObject(RuntimeInfo.getInstance().getString("memberAd::ad"));
  assert pending.optBoolean("confirmed")&&!pending.optString("confirmedStage").isEmpty():path+" lost durable completion fact";
  assert !pending.optBoolean("submitted"):path+" must exercise accepted but unsubmitted state";
 }
 static void restartAndRetry(String path,JSONObject stale,int applies,int finishes)throws Exception{
  requireConfirmedPending(path);FileUtil.restart();AntMemberRpcCall.afterAdApply=null;AntMemberRpcCall.tasks.put("ad",stale);
  MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  assert AntMemberRpcCall.adApply==applies&&AntMemberRpcCall.adFinish==finishes:path+" repeated a write after restart while flag save still failed";
  requireConfirmedPending(path);FileUtil.fail=false;MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  assert RuntimeInfo.getInstance().getString("memberAd::ad").isEmpty():path+" did not flush and clean after persistence recovered";
  FileUtil.restart();MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  assert AntMemberRpcCall.adApply==applies&&AntMemberRpcCall.adFinish==finishes:path+" aliases were not durable after successful retry";
  System.out.println("PASS "+path+": failed real Status.save, durable confirmed tombstone, restart/stale TODO protection, flush retry");
 }
 public static void main(String[] args)throws Exception{
  // Closure 1: live readback confirms completion, including a terminal alias and old trigger aliases.
  resetDurable();JSONObject active=adTask().put("processId","new"),stale=adTask().put("processId","old").put("currentCount",2).put("targetCount",5);
  JSONObject pending=pendingAd(active).put("requestProcessId","old").put("requestStage",MemberBrowseTasks.progress(stale));
  RuntimeInfo.getInstance().put("memberAd::ad",pending.toString());AntMemberRpcCall.tasks.put("ad",new JSONObject(active.toString()).put("status","FINISHED"));FileUtil.fail=true;
  MemberBrowseTasks.load(false,true);
  assert FileUtil.failures==1&&FileUtil.writes==1:"completion aliases were not one verified flag write";
  assert FileUtil.snapshots.get(0).size()==5&&FileUtil.snapshots.get(0).stream().anyMatch(x->x.contains(":terminal:")):"terminal or request aliases missing from the same write";
  restartAndRetry("normal readback",stale,0,0);

  // Closure 2: load finds a cached completion for an accepted but unsubmitted pending process.
  resetDurable();active=adTask().put("processId","new");pending=pendingAd(active);
  RuntimeInfo.getInstance().put("memberAd::ad",pending.toString());AntMemberRpcCall.tasks.put("ad",active);
  Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(active),MemberBrowseTasks.adContract(active).toString()));FileUtil.fail=true;
  MemberBrowseTasks.load(false,true);restartAndRetry("cached load",adTask(),0,0);

  // Closure 3: perform binds the process only after the snapshot; the final guard closes it.
  resetDurable();JSONObject display=adTask();active=adTask().put("processId","new");AntMemberRpcCall.tasks.put("ad",display);AntMemberRpcCall.afterAdApply=active;AntMemberRpcCall.adAdvance=false;
  Status.flagToday(MemberBrowseTasks.adConfirmedKey("ad","new",MemberBrowseTasks.progress(active),MemberBrowseTasks.adContract(active).toString()));FileUtil.fail=true;
  MemberBrowseTasks.perform(display,"cached-perform");
  assert AntMemberRpcCall.adApply==1&&AntMemberRpcCall.adFinish==0:"post-snapshot guard did not suppress completion write";
  restartAndRetry("cached perform",adTask(),1,0);

  // RuntimeInfo can fail independently; the writable Status batch must still preserve completion.
  resetDurable();active=adTask().put("processId","new");pending=pendingAd(active);
  RuntimeInfo.getInstance().put("memberAd::ad",pending.toString());AntMemberRpcCall.tasks.put("ad",new JSONObject(active.toString()).put("status","FINISHED"));RuntimeInfo.fail=true;
  MemberBrowseTasks.load(false,true);
  assert !FileUtil.persisted.isEmpty()&&FileUtil.writes==1:"RuntimeInfo failure prevented writable Status from preserving completion";
  RuntimeInfo.fail=false;FileUtil.restart();AntMemberRpcCall.tasks.put("ad",adTask());MemberBrowseTasks.run(false,Set.of(),true,Set.of());
  assert AntMemberRpcCall.adApply==0&&AntMemberRpcCall.adFinish==0:"RuntimeInfo-only failure lost restart protection";
  resetDurable();assert !Status.flagTodayVerified(Set.of("wrong-day"),"old")&&FileUtil.writes==0&&Status.flags.isEmpty():"verified flags crossed the business-day boundary";
 }
'''
durability_code = durability_code.rstrip()[:-1] + tests + '\n}\n'
with tempfile.TemporaryDirectory(prefix='sesame-member-durability-') as tmp:
    java = Path(tmp)/'AgTaskSyncCheck.java';java.write_text(durability_code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(java)], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp+os.pathsep+str(jar), 'AgTaskSyncCheck'], check=True)
