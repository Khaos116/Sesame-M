"""Replay selected grain exchange, paginated catalogue, pure-resource validation and budget."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/SesameGrainExchange.java';rpc='model/task/antMember/AntMemberRpcCall.java';member='model/task/antMember/AntMember.java'
assert (SOURCE/service).exists(),'Missing separate sesame-grain exchange'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class SesameGrainExchangeCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> ints=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static int getIntFlagToday(String s){return ints.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){ints.put(s,n);}}
 static class UserIdMap {static String getCurrentUid(){return "A";}}
 static class SesameGiftIdMap {static Map<String,String> map=new LinkedHashMap<>();static Map<String,String> getMap(){return map;}static void add(String i,String n){map.put(i,n);}static void load(String uid){}static boolean save(String uid){return save;}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@HELPERS@@}
 static class SesameAchievements {@@NUMBER@@}
 static boolean change=true,save=true,unknown=false,conflict=false,hasNext=false;static String id="chosen\"\\";static int exchanges,pages,detailCalls;static String name="\u53cc\u51fb\u5361";static String point="20";static boolean taken=false;
 static JSONObject item(){JSONObject j=new JSONObject().put("awardTemplateId",id).put("awardName",name).put("awardProdType","PROP").put("point",point).put("remainingBudget",10).put("hasTaken",taken).put("hasFinished",false).put("extInfo",new JSONObject());if(unknown)j.remove("hasTaken");return j;}
 static class ApplicationHook {static String requestString(String method,String args){JSONObject p=new JSONArray(args).optJSONObject(0),data=new JSONObject();
  if(method.endsWith("queryListV2")){pages++;assert p.optString("formDelivery").equals("false")&&p.optInt("pageSize")==20;data.put("hasNext",hasNext).put("awardTemplateList",new JSONArray().put(item())).put("tabList",new JSONArray());}
  else if(method.endsWith("queryDetail")){detailCalls++;assert p.optString("awardTemplateId").equals(id);data.put("awardTemplateVO",item());}
  else if(method.endsWith("obtainAward")){exchanges++;assert p.optString("awardTemplateId").equals(id);if(change)taken=true;data.put("awardRecordId",change?"record":"");}
  else if(method.endsWith("queryMyAwardDetail")){assert p.optString("awardId").equals("record");data.put("awardId",conflict?"other":"record").put("awardTemplateId",id);}
  else throw new AssertionError("mixed currency or unexpected RPC: "+method);
  return new JSONObject().put("success",true).put("data",data).toString();}}
 static class AntMemberRpcCall {@@RPC@@}
 @@SERVICE@@
 static void reset(){change=save=true;unknown=conflict=hasNext=taken=false;TimeUtil.cancel=false;exchanges=pages=detailCalls=Log.ok=0;name="\u53cc\u51fb\u5361";point="20";Status.flags.clear();Status.ints.clear();SesameGiftIdMap.map.clear();}
 public static void main(String[] args){
  reset();SesameGrainExchange.run(Collections.emptySet(),0);assert pages==1&&exchanges==0&&SesameGiftIdMap.map.containsKey(id);
  reset();SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==1&&Log.ok==1&&Status.getIntFlagToday("member::sesameGrainSpent")==20;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==1;
  for(String bad:new String[]{"\u4f18\u60e0\u5238","\u4e0b\u5355\u5b9e\u7269","ONLINE_SHOPPING"}){reset();name=bad;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==0;}
  reset();unknown=true;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==0;
  reset();point="0.1";SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==0;
  reset();SesameGrainExchange.run(Collections.singleton(id),10);assert exchanges==0;
  reset();SesameGrainExchange.run(Collections.singleton("unselected"),100);assert exchanges==0&&detailCalls==0;
  assert SesameGrainExchange.cost(item().put("cashAmount",1))==-1;
  assert SesameGrainExchange.cost(item().put("cashAmount","unknown"))==-1;
  assert SesameGrainExchange.cost(item().put("remainingBudget",0))==-1;
  assert SesameGrainExchange.cost(item().put("sendStartTime",System.currentTimeMillis()+60000))==-1;
  assert SesameGrainExchange.cost(item().put("sendEndTime",System.currentTimeMillis()-60000))==-1;
  reset();change=false;SesameGrainExchange.run(Collections.singleton(id),100);SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==1&&Log.ok==0&&Status.getIntFlagToday("member::sesameGrainSpent")==20;
  reset();conflict=true;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==1&&Log.ok==0;
  reset();hasNext=true;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==0&&pages==2:"stalled page must stop";
  reset();save=false;SesameGrainExchange.run(Collections.singleton(id),100);assert exchanges==0;
  reset();TimeUtil.cancel=true;try{SesameGrainExchange.run(Collections.singleton(id),100);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert exchanges==0;
  System.out.println("PASS separate catalogue/currency, selected only/detail revalidation, pure grain/budget reserve, unknown/order/stock, returned record identity, stalled pagination/save/cancel and RPC escaping");
 }
}
'''
body=(SOURCE/service).read_text(encoding='utf-8');body=body[body.index('final class SesameGrainExchange'):]
code=code.replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
code=code.replace('@@SERVICE@@','static '+body).replace('@@HELPERS@@','\n'.join(method(member,s) for s in ('static JSONObject memberFeaturePayload(','static Map<String, JSONObject> memberRowsById('))).replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('public static String querySesameGiftList(','public static String querySesameGiftDetail(','public static String obtainSesameGift(','public static String queryMySesameGift(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-grain-exchange-') as tmp:
 f=Path(tmp)/'SesameGrainExchangeCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'SesameGrainExchangeCheck'],check=True)
