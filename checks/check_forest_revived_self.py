"""Run the revived-self policy and deferred request guard from production methods."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java';src=(SOURCE/forest).read_text(encoding='utf-8')
assert 'new BooleanModelField("onlyCollectRevivedSelfEnergy"' in src,'Missing revived-self mode'
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestRevivedSelfCheck {
 String selfId="self";static int homeQueries;static JSONObject home;
 static class Bool {boolean n;boolean getValue(){return n;}}static class Num {int n;int getValue(){return n;}}
 Bool onlyCollectRevivedSelfEnergy=new Bool();Num revivedSelfOrdinaryMaxEnergy=new Num();
 static class MyUtils {static int weekday=Calendar.MONDAY,hour=6;static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.set(Calendar.DAY_OF_WEEK,weekday);c.set(Calendar.HOUR_OF_DAY,hour);return c;}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class RpcEntity {String getRequestData(){return request;}String request;RpcEntity(String s){request=s;}}
 static class CollectEnergyEntity {String id;RpcEntity rpc;CollectEnergyEntity(String id,String json){this.id=id;rpc=new RpcEntity(json);}String getUserId(){return id;}RpcEntity getRpcEntity(){return rpc;}}
 JSONObject querySelfHome(){homeQueries++;return home;}
 @@METHODS@@
 static JSONObject bubble(long id,long n,boolean revived){return new JSONObject().put("id",id).put("fullEnergy",n).put("collectStatus","AVAILABLE").put("business",new JSONObject().put("secondScene",revived?"fuhuonengliang":"normal"));}
 static CollectEnergyEntity request(String uid,long...ids){JSONArray array=new JSONArray();for(long id:ids)array.put(id);return new CollectEnergyEntity(uid,new JSONArray().put(new JSONObject().put("userId",uid).put("bubbleIds",array)).toString());}
 public static void main(String[] args){ForestRevivedSelfCheck f=new ForestRevivedSelfCheck();TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  assert f.allowRevivedSelfBubble(null,false)&&f.allowRevivedSelfRequest(request("self",1));assert homeQueries==0;
  f.onlyCollectRevivedSelfEnergy.n=true;assert !f.allowRevivedSelfBubble(bubble(1,100,false),false)&&f.allowRevivedSelfBubble(bubble(1,100,true),false);assert !f.allowRevivedSelfBubble(bubble(1,100,true),true):"do not schedule protected large WAITING bubbles";
  assert !f.allowRevivedSelfBubble(new JSONObject().put("business",new JSONObject().put("secondScene","fuhuonengliang")),false):"unknown energy must not be assumed safe";
  f.revivedSelfOrdinaryMaxEnergy.n=10;assert f.allowRevivedSelfBubble(bubble(1,10,false),false)&&f.allowRevivedSelfBubble(bubble(1,10,false),true)&&!f.allowRevivedSelfBubble(bubble(1,11,false),false);
  home=new JSONObject().put("success",true).put("bubbles",new JSONArray().put(bubble(1,100,true)).put(bubble(2,10,false)).put(bubble(3,11,false)));
  assert f.allowRevivedSelfRequest(request("self",1,2))&&!f.allowRevivedSelfRequest(request("self",1,3))&&!f.allowRevivedSelfRequest(request("self",99));assert f.allowRevivedSelfRequest(request("friend",3));
  f.revivedSelfOrdinaryMaxEnergy.n=0;assert !f.allowRevivedSelfRequest(request("self",2)):"changed options must stop a queued old small-bubble request";
  home.optJSONArray("bubbles").put(bubble(1,100,true));assert !f.allowRevivedSelfRequest(request("self",1)):"duplicate identity must stop";
  home=new JSONObject();assert !f.allowRevivedSelfRequest(request("self",1));assert !f.allowRevivedSelfRequest(new CollectEnergyEntity("self","broken"));
  MyUtils.hour=7;assert f.isRevivedSelfQuietTime()&&!f.allowRevivedSelfRequest(request("friend",3));MyUtils.hour=8;assert !f.isRevivedSelfQuietTime();MyUtils.hour=7;MyUtils.weekday=Calendar.TUESDAY;assert !f.isRevivedSelfQuietTime();
  TimeUtil.cancel=true;try{f.allowRevivedSelfRequest(request("self",1));throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
  System.out.println("PASS revived/ordinary/WAITING policy, default off, small-energy exceptions, fresh deferred/batch/identity guards, GMT+8 Monday boundary and cancellation");
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in ('private boolean isRevivedSelfQuietTime(','private boolean allowRevivedSelfBubble(','private boolean allowRevivedSelfRequest(','static long forestFeatureLong(','static JSONObject forestSignPayload(')))
collect=method(forest,'private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username)')
assert collect.index('allowRevivedSelfRequest(')>collect.index('TimeUtil.sleep(sleep)') and collect.index('allowRevivedSelfRequest(')<collect.index('ApplicationHook.requestObject(')
assert 'catch (TaskCancelledException e)' in collect,'Deferred policy cancellation must reach the lifecycle wrapper'
assert 'allowRevivedSelfBubble(bubble,' in method(forest,'private JSONObject collectUserEnergy(')
assert 'isRevivedSelfQuietTime()' in method(forest,'public void run()')
assert 'isRevivedSelfQuietTime()' in method(forest,'private JSONObject querySelfHome(')
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-revived-self-') as tmp:
 f=Path(tmp)/'ForestRevivedSelfCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestRevivedSelfCheck'],check=True)
