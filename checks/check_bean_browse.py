"""Replay persisted bean browse orders, server identity and same-order prize confirmation."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/BeanRewards.java';member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert 'static void runBrowse(' in (SOURCE/service).read_text(encoding='utf-8'),'Missing bean browse order flow'
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BeanBrowseCheck {
 static String TAG="check";
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class RuntimeInfo {static RuntimeInfo instance=new RuntimeInfo();Map<String,String> values=new HashMap<>();static boolean fail;static RuntimeInfo getInstance(){return instance;}String getString(String s){return values.getOrDefault(s,"");}boolean putVerified(String k,String v){if(fail)return false;if(v==null)values.remove(k);else values.put(k,v);return true;}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class AntMember {@@PAYLOAD@@}
 static int signup,send;static boolean changed=true,mismatch,duplicate,manual,cancelAfterSignup;static String order="",state="NONE_SIGNUP";
 static JSONObject task(){JSONObject row=new JSONObject().put("taskId","server-task").put("taskCenterId","AP15241780").put("taskOrderId",order).put("taskProcessStatus",state)
  .put("taskConfig",new JSONObject().put("appletId","applet\"\\"))
  .put("taskDisplayInfo",new JSONObject().put("customInfo",new JSONObject().put("taskType",manual?"ISSUED_TASK":"BROWSE_PAGE").put("taskOperationType","BROWSE_TASK").put("taskMainTitle","Browse")));
  if(state.equals("RECEIVED"))row.put("sendPrizeSendOrderList",new JSONArray().put(new JSONObject().put("sendStatus","SUCCESS").put("extInfo",new JSONObject().put("TASK_ORDER_ID",mismatch?"other":order))));return row;}
 static class ApplicationHook {
  static String requestString(String name,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),result=new JSONObject();
   if(name.endsWith("taskCenterConsult")){JSONArray rows=new JSONArray().put(task());if(duplicate)rows.put(task());result.put("taskDetailList",rows).put("doneTaskDetailList",new JSONArray());}
   else if(name.endsWith("taskTrigger")){assert p.optString("appletId").equals("applet\"\\")&&p.optString("taskCenId").equals("AP15241780");if(p.optString("stageCode").equals("signup")){signup++;order="order\"\\";if(changed)state="SIGNUP_COMPLETE";if(cancelAfterSignup)TimeUtil.cancel=true;}else{assert p.optString("stageCode").equals("send");assert RuntimeInfo.instance.values.containsValue(order):"persist order before sending";send++;if(changed)state="RECEIVED";}result.put("taskOrderId",order);}
   else throw new AssertionError(name);
   return new JSONObject().put("success",true).put("result",result).toString();
  }
 }
 static class AntMemberRpcCall {@@RPC@@}
 @@METHODS@@
 static void reset(){signup=send=Log.ok=0;changed=true;mismatch=duplicate=manual=cancelAfterSignup=TimeUtil.cancel=RuntimeInfo.fail=false;order="";state="NONE_SIGNUP";RuntimeInfo.instance=new RuntimeInfo();Status.flags.clear();}
 public static void main(String[] args){
  reset();runBrowse(Collections.emptySet());assert signup==1&&send==1&&Log.ok==1&&RuntimeInfo.instance.values.isEmpty();runBrowse(Collections.emptySet());assert signup==1&&send==1;
  reset();RuntimeInfo.fail=true;runBrowse(Collections.emptySet());assert signup==1&&send==0:"failed order persistence prevents send";RuntimeInfo.fail=false;runBrowse(Collections.emptySet());assert signup==1&&send==1;
  reset();mismatch=true;runBrowse(Collections.emptySet());assert send==1&&Log.ok==0&&!RuntimeInfo.instance.values.isEmpty();runBrowse(Collections.emptySet());assert signup==1&&send==1;
  reset();manual=true;runBrowse(Collections.emptySet());assert signup==send&&send==0;
  reset();duplicate=true;runBrowse(Collections.emptySet());assert signup==0&&send==0;
  reset();runBrowse(Collections.singleton("server-task"));assert signup==0&&send==0;
  reset();RuntimeInfo.instance.values.put("memberBeanBrowseOrder::AP15241780::server-task","old-order");runBrowse(Collections.emptySet());assert signup==0&&send==0:"unknown old receipt must block a fresh signup";
  reset();cancelAfterSignup=true;try{runBrowse(Collections.emptySet());throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert signup==1&&send==0;TimeUtil.cancel=cancelAfterSignup=false;runBrowse(Collections.emptySet());assert signup==1&&send==1:"resume same saved/server order after cancellation";
  reset();TimeUtil.cancel=true;try{runBrowse(Collections.emptySet());throw new AssertionError("pre-request cancel");}catch(TaskCancelledException expected){}assert signup==0;
  System.out.println("PASS bean browse signup/order persistence/send/same-order prize, restart/failed save/cancel recovery, duplicate/manual/blacklist and escaping");
 }
}
'''
code=code.replace('@@PAYLOAD@@',method(member,'static JSONObject memberFeaturePayload('))
code=code.replace('@@METHODS@@','\n'.join(method(service,s) for s in ('private static JSONObject response(','private static Map<String, JSONObject> browseTasks(','private static boolean browseOrderConfirmed(','private static boolean beanBrowseContract(','static void runBrowse(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('beanTaskCenterConsult','beanTaskTrigger')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-bean-browse-') as tmp:
 f=Path(tmp)/'BeanBrowseCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'BeanBrowseCheck'],check=True)
