"""Run production YEB promo/main grouping with fake RPC: no account/transfer/exchange calls."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE
code = r"""
import org.json.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class YebTasksCheck {
 static class MyUtils {static int day=6;static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day,12,0);return c;}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class UserIdMap {static String uid="a";static String getCurrentUid(){return uid;}}
 static class RuntimeInfo {static Map<String,String> values=new HashMap<>();static boolean writable=true;static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String key){return values.getOrDefault(key,"");}boolean putVerified(String key,String value){if(!writable)return false;if(value==null)values.remove(key);else values.put(key,value);return true;}}
 static class YebTaskIdMap {static Map<String,String> rows=new HashMap<>();static void load(String s){}static void clear(){}static void add(String k,String v){rows.put(k,v);}static boolean save(String uid){return true;}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(MyUtils.day+":"+s);}static void flagToday(String s){flags.add(MyUtils.day+":"+s);}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@HELPER@@}
 static String financialTitle="",financialCategory="";static boolean financialDrift;
 static String mainId="main\"\\",promoId="promo\"\\",order="";static int actions,queries;static boolean promoEmpty,writeFail;static String initialStatus="NOT_DONE",simplified="";static String forwardPath="";static boolean change,pending,browse,duplicate,conflict,switchAccount,rollover,mutate,extra,readbackFailure;
 static JSONObject row(String id, boolean promo){JSONObject r=new JSONObject().put("taskId",id).put("title",id.equals("second")?"second":"browse").put("link",id.equals("second")?"https://example.com/second":"https://example.com/view").put("taskProcessStatus",!pending&&change&&actions>0&&!id.equals("second")?"RECEIVE_SUCCESS":initialStatus);if(!financialTitle.isEmpty())r.put("title",financialTitle);if(!financialCategory.isEmpty()&&(!financialDrift||queries>2))r.put("taskCategory",financialCategory);if(!simplified.isEmpty())r.put("simplifiedStatus",simplified);if(browse)r.put("taskDisplayInfo",new JSONObject().put("customInfo",new JSONObject().put("taskType","BROWSE_PAGE").put("taskOperationType","BROWSE_TASK")));if(mutate&&queries>2)r.put("link","https://example.com/new");return r;}
 static class ApplicationHook {static String requestString(String name,String args){assert name.endsWith("queryMain")||name.endsWith(".query")||name.endsWith(".queryTaskByTaskId"):"write used retrying default bridge: "+name;return call(name,args,false);}static String requestString(String name,String args,int tries,int interval){assert tries==1&&interval==0:"write retried";assert name.endsWith(".signIn")||name.endsWith(".forward")||name.endsWith(".task.complete"):name;return call(name,args,true);}static String call(String name,String args,boolean mutation){JSONObject p=new JSONArray(args).optJSONObject(0),r=new JSONObject().put("success",!(mutation&&writeFail));queries++;
  if(name.endsWith(".queryMain")){JSONArray todo=new JSONArray().put(row(mainId,false)),done=new JSONArray();if(pending)done.put(new JSONObject().put("taskId",mainId).put("title",financialTitle).put("taskCategory",financialCategory));if(extra)todo.put(row("second",false));r.put("resultData",new JSONObject().put("taskData",new JSONObject().put("taskList",todo).put("completeList",done)));}
  else if(name.endsWith(".query")||name.endsWith(".queryTaskByTaskId")){JSONArray rows=new JSONArray();if(name.endsWith(".query")){assert !p.optBoolean("needTriggerPrize");assert p.optString("playActionCode").equals("TASK_LIST_CONSULT");if(!promoEmpty)rows.put(row(promoId,true));if(duplicate)rows.put(row(promoId,true));if(extra)rows.put(row("second",true));if(switchAccount&&queries>2)UserIdMap.uid="b";if(rollover&&queries>2)MyUtils.day++;}else{assert !promoEmpty&&p.optString("taskId").equals(promoId);assert p.optString("playActionCode").equals("TASK_STATUS_QUERY");rows.put(row(promoId,true));if(readbackFailure)r.put("success",false);}assert p.optString("playEntrance").equals("HYQ_TASK_LIST_ENTRANCE_2");r.put("result",new JSONObject().put("taskDetailList",rows));}
  else if(name.endsWith(".task.complete")){actions++;assert p.optString("taskId").equals(promoId);assert p.optString("appName").equals("yebpromobff");assert p.optString("playActionCode").equals("TASK_COMPLETE");order=p.optString("outBizNo");assert !order.isEmpty();assert RuntimeInfo.values.values().stream().anyMatch(s->s.contains(order));if(change&&!writeFail)pending=false;if(conflict)r.put("resultCode","FAIL");}
  else if(name.endsWith(".forward")){actions++;assert promoEmpty;JSONObject params=p.optJSONObject("params");assert params.optString("taskId").equals(mainId)&&params.optString("appletId").equals("AP12183159")&&params.optInt("version")==2;forwardPath=p.optString("path");assert forwardPath.equals(pending||!initialStatus.equals("NOT_DONE")||!simplified.isEmpty()?"task.trigger":"task.complete");if(change&&!writeFail)pending=false;}
  else throw new AssertionError("unexpected financial action "+name);
  return r.toString();}}
 static class AntMemberRpcCall {@@RPC@@}
 @@SERVICE@@
 static void reset(){financialTitle=financialCategory="";financialDrift=false;initialStatus="NOT_DONE";simplified="";promoEmpty=writeFail=false;forwardPath="";RuntimeInfo.values.clear();RuntimeInfo.writable=true;Status.flags.clear();YebTaskIdMap.rows.clear();change=pending=true;browse=duplicate=conflict=switchAccount=rollover=mutate=extra=readbackFailure=false;actions=queries=Log.ok=0;TimeUtil.cancel=false;UserIdMap.uid="a";MyUtils.day=6;}
 static void run(int budget){YebExpGold.runTasks(new HashSet<>(Arrays.asList(mainId,"second")),Collections.emptySet(),budget);}
 public static void main(String[] args){
  for(String excluded:new String[]{"开通余额宝", "开户领体验金", "转账领福利", "付款奖励", "充值奖励", "签约领奖"})for(boolean reward:new boolean[]{false,true}){reset();financialTitle=excluded;pending=reward;browse=true;initialStatus="not_sign";run(10);assert actions==0&&RuntimeInfo.values.isEmpty():excluded;YebExpGold.run(false,true,Collections.emptySet());assert actions==0:excluded+" reward";}
  for(String category:new String[]{"TRANSFER","transfer","OPEN_ACCOUNT","PAYMENT","ACCOUNT_OPEN","PURCHASE"}){reset();financialCategory=category;pending=false;browse=true;run(10);assert actions==0&&RuntimeInfo.values.isEmpty():category;}
  reset();financialCategory="TRANSFER";financialDrift=true;pending=false;browse=true;run(10);assert actions==0:"fresh financial metadata must stop writes";

  for(String state:new String[]{"not_sign","sign","NONE_SIGNUP","UN_SIGNUP","SIGNUP_EXPIRED"})for(boolean promo:new boolean[]{false,true}){reset();pending=false;browse=true;initialStatus=state;promoEmpty=!promo;run(1);assert actions==1&&Log.ok==1&&RuntimeInfo.values.isEmpty():state+promo;if(!promo)assert forwardPath.equals("task.trigger");}
  for(String state:new String[]{"not_sign","sign"}){reset();pending=false;browse=true;promoEmpty=true;simplified=state;run(1);assert actions==1&&Log.ok==1&&forwardPath.equals("task.trigger"):"source simplifiedStatus takes precedence";}
  reset();pending=false;browse=true;promoEmpty=true;initialStatus="not_sign";writeFail=true;run(1);assert actions==1&&Log.ok==0&&!RuntimeInfo.values.isEmpty();MyUtils.day++;run(1);assert actions==1;

  reset();run(1);assert actions==1&&Log.ok==1&&RuntimeInfo.values.isEmpty();run(1);assert actions==1;
  for(boolean reward:new boolean[]{false,true})for(boolean failed:new boolean[]{false,true}){reset();promoEmpty=true;writeFail=failed;pending=reward;browse=!reward;run(1);assert actions==1&&forwardPath.equals(reward?"task.trigger":"task.complete")&&Log.ok==(failed?0:1);run(1);assert actions==1;}
  reset();writeFail=true;run(1);assert actions==1&&Log.ok==0&&!RuntimeInfo.values.isEmpty();run(1);assert actions==1;MyUtils.day++;run(1);assert actions==1;
  reset();run(0);assert actions==0&&YebTaskIdMap.rows.containsKey(mainId)&&YebTaskIdMap.rows.containsKey(promoId);
  reset();YebExpGold.runTasks(Collections.emptySet(),Collections.emptySet(),1);assert actions==0&&!YebTaskIdMap.rows.isEmpty();
  reset();pending=false;browse=true;run(1);assert actions==1&&Log.ok==1&&RuntimeInfo.values.isEmpty();
  reset();extra=true;browse=true;run(1);assert actions==1;run(1);assert actions==1;
  reset();pending=false;change=false;run(1);assert actions==0; // unknown type: selected ID cannot authorize an action
  reset();pending=false;change=false;browse=true;run(1);assert actions==1&&Log.ok==0&&!RuntimeInfo.values.isEmpty();MyUtils.day++;run(1);assert actions==1;
  reset();change=false;run(1);assert actions==1&&Log.ok==0;MyUtils.day++;YebExpGold.run(false,true,Collections.emptySet());assert actions==1;run(1);assert actions==1;
  reset();RuntimeInfo.writable=false;run(1);assert actions==0;
  reset();duplicate=true;run(1);assert actions==0;
  reset();conflict=true;run(1);assert actions==1&&Log.ok==0&&!RuntimeInfo.values.isEmpty();run(1);assert actions==1;
  reset();YebExpGold.runTasks(Collections.singleton(mainId),Collections.singleton(promoId),1);assert actions==0;
  reset();YebExpGold.runTasks(Collections.singleton(mainId),Collections.singleton("browse"),1);assert actions==0;
  reset();switchAccount=true;run(1);assert actions==0;
  reset();rollover=true;run(1);assert actions==0;
  reset();mutate=true;run(1);assert actions==0;
  reset();readbackFailure=true;run(1);assert actions==1&&Log.ok==0&&!RuntimeInfo.values.isEmpty();
  reset();TimeUtil.cancel=true;try{run(1);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert actions==0;
  System.out.println("PASS production YEB source signup/not_sign/sign trigger routing and simplified status precedence; promo/main aliases, selected typed actions, durable unknown receipts, terminal readback, conflict/duplicate/blacklist/budget/discovery/account/day/cancel guards, normal/failed promo complete and main trigger/complete use exactly one 1/0 call");
 }
}
"""
member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
body=(SOURCE/'model/task/antMember/YebExpGold.java').read_text(encoding='utf-8');body=body[body.index('final class YebExpGold'):]
code=code.replace('@@SERVICE@@','static '+body).replace('@@HELPER@@',method(member,'static Map<String, JSONObject> memberRowsById(')).replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('public static String queryYebExpGoldMain(','public static String signInYebExpGold(','public static String triggerYebExpGoldReward(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xms16m -Xmx192m')
from daily_task_fixture import with_daily_task
code=with_daily_task(code)
with tempfile.TemporaryDirectory(prefix='sesame-yeb-tasks-') as tmp:
 f=Path(tmp)/'YebTasksCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True,env=env)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'YebTasksCheck'],check=True,env=env)
