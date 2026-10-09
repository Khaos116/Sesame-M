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
 static int writes,queries,signups,completes,receives,opens,progress,signs,sends;
 static String pstate,title,action,taskState; static boolean sign,failReceive,failOpen,emptyOpen,malformed,duplicate,failProgress,failSend;
 static Set<String> opened=new HashSet<>();
 static JSONObject card(String id){return new JSONObject().put("id",id).put("cardInfo",new JSONObject().put("title","好运卡红包").put("cardLevel","1").put("amount","0.10"));}
 static JSONArray cards(String... ids){JSONArray a=new JSONArray();for(String id:ids)a.put(card(id));return a;}
 static JSONObject row(){return new JSONObject().put("taskId","task\"\\").put("taskToken","SECRET_TOKEN").put("title",title).put("actionType",action).put("taskStatus",pstate);}
 static JSONObject ok(){return new JSONObject().put("success",true);}
 static JSONObject result(JSONObject v){return ok().put("data",new JSONObject().put("result",v));}
 static JSONArray openedCards(){JSONArray a=new JSONArray();for(String id:opened)a.put(card(id));return a;}
 static class ApplicationHook{
  static boolean isOffline(){return false;}
  static String requestString(String op,String raw)throws Exception{return rpc(op,raw,false);}
  static String requestString(String op,String raw,int attempts,int retries)throws Exception{assert attempts==1&&retries==0;return rpc(op,raw,true);}
  static String rpc(String op,String raw,boolean write)throws Exception{
   JSONObject a=new JSONArray(raw).optJSONObject(0);assert a!=null;if(write)writes++;else queries++;
   if(op.endsWith("p2e.queryTaskList")){
    assert a.optString("source").equals("ch_appcenter__chsub_9patch");
    JSONArray rows=new JSONArray().put(row());if(duplicate)rows.put(row());
    return (malformed?ok():ok().put("data",new JSONObject().put("platformGameTaskModule",new JSONObject().put("platformTaskList",rows)))).toString();
   }
   if(op.endsWith("platformTaskSignUp")){assert a.optString("taskId").equals("task\"\\");signups++;pstate="SIGNUP_COMPLETE";return ok().toString();}
   if(op.endsWith("platformTaskComplete")){assert TimeUtil.waited>=15;completes++;pstate="COMPLETED";return ok().toString();}
   if(op.endsWith("gameP2eTaskReceive")){assert a.optString("taskToken").equals("SECRET_TOKEN");receives++;pstate="RECEIVED";if(failReceive)return "{}";return ok().put("data",new JSONObject().put("coinAmount",12)).toString();}
   if(op.endsWith("gameplay.rebate")){
    boolean trigger=a.optString("behavior").equals("trigger"),sg=a.optString("bizScene").equals("HAOYUNKA_SIGN_IN");
    if(sg){if(trigger){sign=true;signs++;}JSONObject r=new JSONObject().put("status",sign?"SIGNED_UP":"NONE_SIGNUP");if(sign)r.put("extInfo",new JSONObject().put("lastSignInTime",String.format("2026-10-%02d",MyUtils.day)).toString()).put("prizeDetails",new JSONObject().put("1",new JSONObject().put("tickets",cards("sign"))).toString());return result(r).toString();}
    if(trigger){progress++;if(failProgress){failProgress=false;return "{}";}}
    JSONObject r=new JSONObject().put("status",progress>=4?"REWARDED":progress>0?"SIGNED_UP":"NONE_SIGNUP").put("recentProcess",progress);
    if(progress>=4)r.put("collection",trigger).put("prizeDetails",new JSONObject().put("tickets",cards("progress")).toString());
    return result(r).toString();
   }
   if(op.endsWith("sdk.task.query")){
    Object show=taskState.equals("RECEIVED")?cards("task1","task2").toString():new JSONObject().put("title","浏览支付宝领好运卡").put("subTitle","免费好运卡").put("url","alipays://safe").toString();
    return result(new JSONObject().put("taskListResult",new JSONArray().put(new JSONObject().put("taskId","lucky\"\\").put("taskStatus",taskState).put("taskShowInfo",show)))).toString();
   }
   if(op.endsWith("sdk.task.trigger")){
    assert a.optString("appletId").equals("lucky\"\\");
    if(a.optString("stageCode").equals("signup")){taskState="SIGNUP_COMPLETE";return result(new JSONObject().put("taskShowInfo","{\"needSignUp\":\"true\"}")).toString();}
    sends++;taskState="RECEIVED";if(failSend){failSend=false;return "{}";}return result(new JSONObject().put("taskShowInfo",cards("task1","task2").toString())).toString();
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
 static void reset(){RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Log.lines.clear();Log.confirmed=0;UserIdMap.uid="self";MyUtils.day=8;TimeUtil.cancel=TimeUtil.switchWait=TimeUtil.crossWait=false;TimeUtil.waited=0;writes=queries=signups=completes=receives=opens=progress=signs=sends=0;opened.clear();pstate="UN_SIGNUP";title="浏览支付宝活动";action="VIEW_TASK";taskState="NONE_SIGNUP";sign=failReceive=failOpen=emptyOpen=malformed=duplicate=failProgress=failSend=false;}
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
  reset();failProgress=true;worker(30).luckyCard();assert progress==1;worker(30).luckyCard();assert progress==4&&opens==4:"progress recovered from consult";
  reset();failSend=true;worker(30).luckyCard();assert sends==1&&opens==2;worker(30).luckyCard();assert sends==1&&opens==4:"task cards recovered from query";
  reset();failOpen=true;try{worker(30).luckyCard();throw new AssertionError();}catch(java.io.IOException expected){assert !expected.getMessage().contains("SECRET_TOKEN");}assert opens==1;worker(30).luckyCard();assert opens==4:"unknown open queried without repeat";
  reset();emptyOpen=true;worker(30).luckyCard();before=writes;worker(30).luckyCard();assert writes==before&&opens==4:"missing card result must not resend";
  reset();RuntimeInfo.writable=false;worker(30).luckyCard();assert writes==0;
  reset();worker(0).p2eBrowse();worker(0).luckyCard();assert writes+queries==0;
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
