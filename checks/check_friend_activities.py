"""Replay friend P2E browse / lucky cards against production workers and gate; no network."""
from pathlib import Path
import ast
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/aw1y2z/sesame'
# Share the existing isolated Android/account fixtures, not a second implementation of the worker.
tree = ast.parse((ROOT / 'checks/check_sj_activities.py').read_text(encoding='utf-8'))
base = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'code' for t in n.targets))
code = base[:base.index('  static int queries,')].replace('public class SjActivityCheck', 'public class FriendActivityCheck') + r'''
 static String text(JSONObject row,String key){return SjActivityTasks.text(row,key);}
 static long count(JSONObject row,String key){return SjActivityTasks.count(row,key);}
 @@FRIEND@@
 static class OtherTask{@@MANUAL@@ @@RUN_FRIEND@@}
 static int writes,queries,signups,completes,receives,opens,progress,signs,sends;
 static String pstate,title,action,taskState; static boolean sign,failReceive,failOpen,emptyOpen,malformed,duplicate,failProgress,failSend;
 static String rejectTarget="",unknownTarget="",rejectCode="BUSINESS_REJECTED";static boolean noCoins,listedCoins,notReady,noP2eTasks,noLuckyTasks;
 static Set<String> opened=new HashSet<>();
 static JSONObject card(String id){return new JSONObject().put("id",id).put("cardInfo",new JSONObject().put("title","好运卡红包").put("cardLevel","1").put("amount","0.10"));}
 static JSONArray cards(String... ids){JSONArray a=new JSONArray();for(String id:ids)a.put(card(id));return a;}
 static JSONObject row(){JSONObject r=new JSONObject().put("taskId","task\"\\").put("taskToken","SECRET_TOKEN").put("title",title).put("actionType",action).put("taskStatus",pstate);if(listedCoins)r.put("goldCoinAmount",9);return r;}
 static JSONObject ok(){return new JSONObject().put("success",true);}
 static JSONObject result(JSONObject v){return ok().put("data",new JSONObject().put("result",v));}
 static JSONArray openedCards(){JSONArray a=new JSONArray();for(String id:opened)a.put(card(id));return a;}
 static class ApplicationHook{
  static boolean isOffline(){return false;}
  static String requestString(String op,String raw)throws Exception{return rpc(op,raw,false);}
  static String requestString(String op,String raw,int attempts,int retries)throws Exception{assert attempts==1&&retries==0;return rpc(op,raw,true);}
  static String rpc(String op,String raw,boolean write)throws Exception{
   JSONObject a=new JSONArray(raw).optJSONObject(0);assert a!=null;if(write)writes++;else queries++;
   String stage=op.endsWith("gameplay.rebate")?(a.optString("bizScene").equals("HAOYUNKA_SIGN_IN")?"sign":"progress"):op.endsWith("sdk.task.trigger")?a.optString("stageCode"):op.endsWith("openLuckyCard")?"open":op.endsWith("gameP2eTaskReceive")?"receive":op.endsWith("platformTaskSignUp")?"p2eSignup":op.endsWith("platformTaskComplete")?"p2eComplete":"";
   if(write&&!stage.isEmpty()&&stage.equals(rejectTarget)){rejectTarget="";return new JSONObject().put("success",false).put("resultCode",rejectCode).toString();}
   if(write&&!stage.isEmpty()&&stage.equals(unknownTarget)){unknownTarget="";return "{}";}
   if(op.endsWith("p2e.queryTaskList")){
    assert a.optString("source").equals("ch_appcenter__chsub_9patch");
    JSONArray rows=new JSONArray();if(!noP2eTasks)rows.put(row());if(duplicate)rows.put(row());
    return (malformed?ok():ok().put("data",new JSONObject().put("platformGameTaskModule",new JSONObject().put("platformTaskList",rows)))).toString();
   }
   if(op.endsWith("platformTaskSignUp")){assert a.optString("taskId").equals("task\"\\");signups++;pstate="SIGNUP_COMPLETE";return ok().toString();}
   if(op.endsWith("platformTaskComplete")){assert TimeUtil.waited>=15;completes++;pstate="COMPLETED";return ok().toString();}
   if(op.endsWith("gameP2eTaskReceive")){assert a.optString("taskToken").equals("SECRET_TOKEN");receives++;pstate="RECEIVED";if(failReceive)return "{}";return noCoins?ok().toString():ok().put("data",new JSONObject().put("coinAmount",12)).toString();}
   if(op.endsWith("gameplay.rebate")){
    boolean trigger=a.optString("behavior").equals("trigger"),sg=a.optString("bizScene").equals("HAOYUNKA_SIGN_IN");
    if(sg){if(trigger){sign=true;signs++;}JSONObject r=new JSONObject().put("status",sign?"SIGNED_UP":"NONE_SIGNUP");if(sign)r.put("extInfo",new JSONObject().put("lastSignInTime",String.format("2026-10-%02d",MyUtils.day)).toString()).put("prizeDetails",new JSONObject().put("1",new JSONObject().put("tickets",cards("sign"))).toString());return result(r).toString();}
    assert trigger : "source has no progress consult: extra query blocked the real flow";
    boolean advanced=trigger&&progress<4;
    if(advanced){progress++;if(failProgress){failProgress=false;return "{}";}}
    JSONObject r=new JSONObject().put("status",progress>=4?"REWARDED":progress>0?"SIGNED_UP":"NONE_SIGNUP").put("recentProcess",progress);
    if(progress>=4)r.put("collection",advanced).put("prizeDetails",new JSONObject().put("tickets",cards("progress")).toString());
    return result(r).toString();
   }
   if(op.endsWith("sdk.task.query")){
    if(malformed)return result(new JSONObject().put("taskListResult",new JSONArray().put(new JSONObject().put("taskStatus","TODO")))).toString();
    Object show=taskState.equals("RECEIVED")?cards("task1","task2").toString():new JSONObject().put("title","浏览支付宝领好运卡").put("subTitle","免费好运卡").put("url","alipays://safe").toString();
    return result(new JSONObject().put("taskListResult",noLuckyTasks?new JSONArray():new JSONArray().put(new JSONObject().put("taskId","lucky\"\\").put("taskStatus",taskState).put("taskShowInfo",show)))).toString();
   }
   if(op.endsWith("sdk.task.trigger")){
    assert a.optString("appletId").equals("lucky\"\\");
    if(a.optString("stageCode").equals("signup")){taskState="SIGNUP_COMPLETE";return result(new JSONObject().put("taskShowInfo","{\"needSignUp\":\"true\"}")).toString();}
    sends++;if(notReady)return result(new JSONObject().put("taskShowInfo","{\"needFinish\":true}")).toString();taskState="RECEIVED";if(failSend){failSend=false;return "{}";}return result(new JSONObject().put("taskShowInfo",cards("task1","task2").toString())).toString();
   }
   if(op.endsWith("openLuckyCard")){
    String id=a.optJSONArray("cardIds").optString(0);assert !opened.contains(id):"duplicate card open";opens++;opened.add(id);
    if(failOpen){failOpen=false;throw new java.io.IOException("token=SECRET_TOKEN");}
    return ok().put("result",new JSONObject().put("openCards",emptyOpen?new JSONArray():cards(id))).toString();
   }
   if(op.endsWith("consultLuckyCard"))return ok().put("result",new JSONObject().put("ticketResult",new JSONObject().put("openCards",openedCards()))).toString();
   throw new AssertionError(op);
  }
 }
 static FriendActivityTasks worker(int budget){return new FriendActivityTasks(new SjActivityTasks(new OtherRequestGate(),budget));}
 static void reset(){rejectTarget=unknownTarget="";rejectCode="BUSINESS_REJECTED";noCoins=listedCoins=notReady=noP2eTasks=noLuckyTasks=false;RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Log.lines.clear();Log.confirmed=0;UserIdMap.uid="self";MyUtils.day=8;TimeUtil.cancel=TimeUtil.switchWait=TimeUtil.crossWait=false;TimeUtil.waited=0;writes=queries=signups=completes=receives=opens=progress=signs=sends=0;opened.clear();pstate="UN_SIGNUP";title="浏览支付宝活动";action="VIEW_TASK";taskState="NONE_SIGNUP";sign=failReceive=failOpen=emptyOpen=malformed=duplicate=failProgress=failSend=false;}
 static boolean pending(String domain){return !RuntimeInfo.instance.getString("sjActivityReceipt::"+domain).isEmpty();}
 static void nextDay(){MyUtils.day++;Status.flags.clear();}
 public static void main(String[] args)throws Exception{
  reset();worker(30).p2eBrowse();assert signups==1&&completes==1&&receives==1&&TimeUtil.waited==15;assert Log.lines.stream().anyMatch(s->s.contains("获得12金币"));worker(30).p2eBrowse();assert writes==3;
  reset();worker(1).p2eBrowse();assert signups==1&&completes==0;worker(30).p2eBrowse();assert signups==1&&completes==1&&receives==1:"budget resume";
  reset();pstate="COMPLETED";worker(30).p2eBrowse();assert receives==1&&signups==0&&completes==0;
  reset();failReceive=true;worker(30).p2eBrowse();assert receives==1;worker(30).p2eBrowse();assert receives==1&&RuntimeInfo.data.keySet().stream().noneMatch(k->k.startsWith("sjActivityReceipt::p2eBrowse::"));
  reset();action="INVITE_TASK";worker(30).p2eBrowse();assert writes==0;
  reset();title="开通信用卡领金币";worker(30).p2eBrowse();assert writes==0;
  reset();title="浏览Alipay活动";worker(30).p2eBrowse();assert receives==1:"Alipay title falsely matched PAY";
  reset();pstate="NEW_STATUS";worker(30).p2eBrowse();assert writes==0;
  reset();duplicate=true;worker(30).p2eBrowse();assert writes==0;
  reset();malformed=true;worker(30).p2eBrowse();assert writes==0;
  reset();TimeUtil.switchWait=true;try{worker(30).p2eBrowse();throw new AssertionError();}catch(TaskCancelledException expected){}assert signups==1&&completes==0;
  reset();TimeUtil.crossWait=true;try{worker(30).p2eBrowse();throw new AssertionError();}catch(TaskCancelledException expected){}assert completes==0;
  reset();worker(30).luckyCard();assert signs==1&&progress==4&&sends==1&&opens==4:"normal lucky chain: "+signs+","+progress+","+sends+","+opens;int before=writes;worker(30).luckyCard();assert writes==before:"same day duplicated";
  assert Log.lines.stream().filter(s->s.startsWith("✅ 好运卡开卡成功")).count()==8;
  assert Log.lines.stream().noneMatch(s->s.contains("SECRET_TOKEN"));
  reset();worker(1).luckyCard();assert signs==1&&opens==0;worker(30).luckyCard();assert signs==1&&progress==4&&opens==4:"queue survives budget exhaustion";
  reset();failProgress=true;worker(30).luckyCard();assert progress==1;worker(30).luckyCard();assert progress==1&&pending("luckyProgress"):"unknown progress must not be resent";
  worker(0).clearReceipts();worker(30).luckyCard();assert progress==4&&opens==4:"manual recovery resumes source triggers";
  reset();OtherTask orchestration=new OtherTask();RuntimeInfo.data.put("sjActivityAttempts",new JSONObject().put("day",SjActivityTasks.date()).put("count",30).toString());
  orchestration.runFriendActivity(false,30);orchestration.runFriendActivity(true,30);
  assert signs==1&&progress==4&&opens==4&&receives==1:"shared SJ budget starved independent activities";
  assert count(new JSONObject(RuntimeInfo.instance.getString("sjActivityAttempts")),"count")==30;
  reset();orchestration.runFriendActivity(false,0);assert writes+queries==0;
  orchestration.runFriendActivity(true,1);orchestration.runFriendActivity(false,30);assert receives==0&&signs==1&&opens==4:"browse budget leaked into lucky cards";
  reset();failOpen=true;orchestration.runFriendActivity(false,30);orchestration.runFriendActivity(true,30);assert opens==1&&receives==1:"one activity exception blocked another";
  assert Log.lines.stream().noneMatch(s->s.contains("SECRET_TOKEN"));
  reset();TimeUtil.cancel=true;try{orchestration.runFriendActivity(false,30);throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}
  reset();malformed=true;worker(30).luckyCard();assert sends==0&&Log.lines.stream().anyMatch(s->s.contains("无有效ID=1")):"missing task ID diagnostics";
  reset();failSend=true;worker(30).luckyCard();assert sends==1&&opens==2;worker(30).luckyCard();assert sends==1&&opens==4:"task cards recovered from query";
  reset();failOpen=true;try{worker(30).luckyCard();throw new AssertionError();}catch(java.io.IOException expected){assert !expected.getMessage().contains("SECRET_TOKEN");}assert opens==1;worker(30).luckyCard();assert opens==4:"unknown open queried without repeat";
  reset();emptyOpen=true;worker(30).luckyCard();before=writes;worker(30).luckyCard();assert writes==before&&opens==4:"missing card result must not resend";
  reset();RuntimeInfo.writable=false;worker(30).luckyCard();assert writes==0;
  reset();worker(0).p2eBrowse();worker(0).luckyCard();assert writes+queries==0;
  for(boolean listed:new boolean[]{false,true}){
   reset();noCoins=true;listedCoins=listed;worker(30).p2eBrowse();assert receives==1&&!pending("p2eBrowse::task\"\\");
   assert Log.lines.stream().anyMatch(s->s.contains(listed?"任务标示奖励9金币":"金币数未返回"));noP2eTasks=true;worker(30).p2eBrowse();assert receives==1;
  }
  for(String target:new String[]{"sign","progress","signup","send","open","receive","p2eSignup","p2eComplete"}){
   boolean p2e=target.equals("receive")||target.startsWith("p2e");
   reset();rejectTarget=target;if(p2e)worker(50).p2eBrowse();else worker(50).luckyCard();
   assert RuntimeInfo.data.keySet().stream().noneMatch(k->k.startsWith("sjActivityReceipt::")):"explicit rejection stuck "+target;
   int submitted=writes;String budget=RuntimeInfo.instance.getString("sjActivityAttempts");
   if(p2e)worker(50).p2eBrowse();else worker(50).luckyCard();
   assert writes==submitted&&budget.equals(RuntimeInfo.instance.getString("sjActivityAttempts")):"same-day rejection burned budget "+target;
   nextDay();if(p2e){worker(50).p2eBrowse();assert receives==1;}else{worker(50).luckyCard();assert signs==1&&progress==4&&sends==1&&opens==4:"next-day retry failed "+target;}
  }
  for(String codeValue:new String[]{"102","3000","SYSTEM_BUSY","429","48","REMOTE_INVOKE_EXCEPTION","timeout","SERVER_BUSY"}){
   reset();rejectTarget="receive";rejectCode=codeValue;worker(30).p2eBrowse();assert pending("p2eBrowse::task\"\\"):"temporary failure discarded "+codeValue;
   assert Status.flags.stream().noneMatch(k->k.startsWith("other::friendRejected::")):"temporary failure marked as rejection";
  }
  reset();SjActivityTasks isolated=new SjActivityTasks(new OtherRequestGate(),50);rejectTarget="p2eSignup";
  JSONObject directArgs=new JSONObject().put("taskId","task\"\\").put("taskToken","SECRET_TOKEN");
  isolated.write("first","signup","com.alipay.gamecenteruprod.biz.rpc.platformTaskSignUp",directArgs);
  assert isolated.write("first","receive","com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eTaskReceive",directArgs)!=null:"rejection blocked another action";
  assert isolated.write("second","signup","com.alipay.gamecenteruprod.biz.rpc.platformTaskSignUp",directArgs)!=null:"rejection blocked another domain";
  for(String target:new String[]{"p2eSignup","p2eComplete","receive","signup","send"}){
   boolean p2e=target.equals("receive")||target.startsWith("p2e");reset();unknownTarget=target;
   if(p2e)worker(50).p2eBrowse();else worker(50).luckyCard();
   assert pending(p2e?"p2eBrowse::task\"\\":"luckyTask::lucky\"\\");
   nextDay();pstate="UN_SIGNUP";taskState="NONE_SIGNUP";
   if(p2e){worker(50).p2eBrowse();assert receives==1&&!pending("p2eBrowse::task\"\\");}
   else{worker(50).luckyCard();assert sends==1&&opens==4&&!pending("luckyTask::lucky\"\\");}
  }
  reset();unknownTarget="open";worker(50).luckyCard();assert pending("luckyOpen::sign");nextDay();before=opens;worker(50).luckyCard();assert pending("luckyOpen::sign")&&opens==before:"card receipt expired like a daily task";
  for(String target:new String[]{"sign","progress","send","open","receive"}){
   reset();unknownTarget=target;if(target.equals("receive"))worker(50).p2eBrowse();else worker(50).luckyCard();
   nextDay();
   if(target.equals("sign")||target.equals("progress")){worker(50).luckyCard();assert signs==1&&progress==4:"daily receipt blocked tomorrow "+target;}
   else{
    RuntimeInfo.data.put("sjActivityReceipt::shenQuan", "keep-other-activity");int requestCount=queries+writes;
    new OtherTask().runManualAction("clearFriendReceipts");assert queries+writes==requestCount:"cleanup sent RPC";
    assert RuntimeInfo.instance.getString("sjActivityReceipt::shenQuan").equals("keep-other-activity");
    if(target.equals("receive")){worker(50).p2eBrowse();assert receives==1;}else{worker(50).luckyCard();assert opens==4&&sends==1:"manual recovery failed "+target;}
   }
  }
  reset();notReady=true;worker(50).luckyCard();before=writes;worker(50).luckyCard();assert sends==1&&writes==before:"not-ready task consumes daily budget again";nextDay();worker(50).luckyCard();assert sends==2;
  reset();worker(50).luckyCard();JSONObject queue=new JSONObject(RuntimeInfo.instance.getString("other::luckyCardQueue"));assert queue.length()==4&&queue.optInt("sign")==20261008;
  MyUtils.day=16;before=writes;worker(50).openCards();assert new JSONObject(RuntimeInfo.instance.getString("other::luckyCardQueue")).length()==0&&writes==before:"opened history did not expire";
  RuntimeInfo.data.put("other::luckyCardQueue","{\"legacy\":true,\"waiting\":false}");queue=worker(50).cardQueue();assert queue.optInt("legacy")==20261016&&Boolean.FALSE.equals(queue.opt("waiting"));
  reset();unknownTarget="receive";worker(50).p2eBrowse();nextDay();noP2eTasks=true;worker(50).p2eBrowse();assert !pending("p2eBrowse::task\"\\"):"missing old P2E task receipt retained";
  reset();unknownTarget="send";worker(50).luckyCard();nextDay();noLuckyTasks=true;worker(50).luckyCard();assert !pending("luckyTask::lucky\"\\"):"missing old lucky task receipt retained";
  reset();unknownTarget="receive";worker(50).p2eBrowse();RuntimeInfo.writable=false;new OtherTask().runManualAction("clearFriendReceipts");assert pending("p2eBrowse::task\"\\"):"failed cleanup destroyed receipt";
  reset();FriendActivityTasks stale=worker(0);UserIdMap.uid="other";try{stale.clearReceipts();throw new AssertionError("cleanup crossed account");}catch(TaskCancelledException expected){}
  reset();assert worker(30).signToday(new JSONObject().put("extInfo","{\"lastSignInTime\":\"2026-10-07T18:00:00Z\"}"));
  assert !worker(30).signToday(new JSONObject().put("extInfo","{\"lastSignInTime\":\"2026-10-08T18:00:00Z\"}"));
  reset();TimeUtil.cancel=true;try{worker(30).luckyCard();throw new AssertionError();}catch(TaskCancelledException expected){}assert writes+queries==0;
  System.out.println("PASS friend activity production replay: normal chains, multiple cards, budgets/resume, unknown receipts/readbacks, exclusions, malformed lists, account/day/cancellation and secret-safe logs");
 }
}
'''
for marker, filename in [('@@GATE@@', 'OtherRequestGate.java'), ('@@WORKER@@', 'SjActivityTasks.java'), ('@@FRIEND@@', 'FriendActivityTasks.java')]:
    source = (SOURCE / 'model/task/other' / filename).read_text(encoding='utf-8')
    code = code.replace(marker, 'static ' + source[source.index('final class '):])
guard = (SOURCE / 'rpc/intervallimit/RpcRequestGuard.java').read_text(encoding='utf-8')
message = (SOURCE / 'util/MessageUtil.java').read_text(encoding='utf-8')
helpers = []
for name in ('isRetryable', 'isServerBusy'):
    start = message.index('public static boolean ' + name + '(')
    helpers.append(message[start:message.index('\n    }', start)+6])
code = code.replace('@@RETRY_HELPERS@@', '\n'.join(helpers))
runtime = (SOURCE / 'data/RuntimeInfo.java').read_text(encoding='utf-8')
start = runtime.index('public synchronized java.util.List<String> keysStartingWith(')
keys = runtime[start:runtime.index('\n    }', start)+6].replace('joCurrent', 'new JSONObject(data)')
code = code.replace('static class RuntimeInfo{', 'static class RuntimeInfo{' + keys)
other = (SOURCE / 'model/task/other/OtherTask.java').read_text(encoding='utf-8')
manual = []
for signature in ('protected boolean supportsManualAction(', 'protected void runManualAction('):
    start = other.index(signature)
    end = other.index('}', start)+1 if signature.startswith('protected boolean') else other.index('\n    }', start)+6
    manual.append(other[start:end])
code = code.replace('@@MANUAL@@', '\n'.join(manual))
start = other.index('private void runFriendActivity(')
code = code.replace('@@RUN_FRIEND@@', other[start:other.index('\n    }', start)+6])
ui = (SOURCE / 'ui/miuix/MiuixGroupFieldsActivity.kt').read_text(encoding='utf-8')
assert '"OtherTask" -> listOf("恢复赚金币/好运卡未确认操作" to "clearFriendReceipts")' in ui
assert '.setTitle("已核对赚金币和好运卡记录？")' in ui
code = code.replace('@@FAILURE@@', guard[guard.index('public static boolean isFailure('):guard.index('    public static boolean isNonFriend(')])
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-friend-activities-') as tmp:
    java = Path(tmp) / 'FriendActivityCheck.java'
    java.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(java),
                    str(SOURCE / 'util/TaskCancelledException.java'), str(SOURCE / 'rpc/intervallimit/RequestBudgetPolicy.java'),
                    str(SOURCE / 'rpc/intervallimit/RpcFailurePolicy.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), 'FriendActivityCheck'], check=True)
