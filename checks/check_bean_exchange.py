"""Replay bean catalogue, native pure-bean exchange, reserved budget and readback."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/BeanRewards.java';member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert 'static void runExchange(' in (SOURCE/service).read_text(encoding='utf-8'),'Missing bean catalogue/exchange'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BeanExchangeCheck {
 static String TAG="check",id="rights\"\\";
 static class MyUtils {static int day=7;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day);return c;}static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> ints=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static int getIntFlagToday(String s){return ints.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){ints.put(s,n);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class UserIdMap {static String uid="uid";static String getCurrentUid(){return uid;}}
 static class RuntimeInfo {static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo instance=new RuntimeInfo();static RuntimeInfo getInstance(){return instance;}String getString(String k){return data.getOrDefault(k,"");}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class BeanRightIdMap {static Map<String,String> map=new HashMap<>();static void load(String s){}static void add(String k,String v){map.put(k,v);}static boolean save(String s){return save;}static Map<String,String> getMap(){return map;}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@PAYLOAD@@}
 static class SesameAchievements {@@NUMBER@@}
 static int exchanges,details,pages,balance=100,count;static boolean save=true,change=true,unknown,cash,order,redirect,hasNext,conflict,noGain,fail,cancelAfter,wrongHistory,switchUid,rollover;static String type="GOLD_TICKET",name="gold ticket";static Object price=20;
 static JSONObject item(){JSONObject r=new JSONObject().put("rightsId",id).put("title",name).put("showType","OTHER").put("rightsMetaSubType",type).put("cash",cash?1:0).put("needOrder",order?1:0).put("assetAmount",price).put("lackPointNum",0).put("exchangeTotalNum",count);if(unknown)r.remove("cash");if(redirect)r.put("rightsUseLink","https://external.invalid/order");return r;}
 static class ApplicationHook {static String requestString(String method,String raw,int tries,int pause){assert method.endsWith("rightsExchange")&&tries==1&&pause==0;return call(method,raw);}static String requestString(String method,String raw){assert !method.endsWith("rightsExchange");return call(method,raw);}static String call(String method,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),result=new JSONObject();
  if(method.endsWith("filterValidBizProperty")){assert Boolean.FALSE.equals(p.opt("userAccountFilter"));result.put("validBizPropertyList",new JSONArray());}
  else if(method.endsWith("rightsRecommend")||method.endsWith("queryRightsPreExchangeFlows")){pages++;assert p.optString("bizScene").equals("BLUE_BEAN_POSITION")&&p.optJSONObject("factors").optString("entrance").equals("insplatform_mine_anxindou");result.put("rightsList",new JSONArray().put(item())).put("hasNext",hasNext).put("pageEndIndex",1);}
  else if(method.endsWith("queryRightsDetail")){details++;assert p.optString("rightsCode").equals(id);result=item();if(conflict)result.put("rightsId","other");}
  else if(method.endsWith("queryRightsExchangeFlows")){assert p.optString("exchangeType").equals("ONLY_BLUE_BEAN");JSONArray rows=new JSONArray();if(count>0){JSONObject row=item();if(wrongHistory)row.put("rightsId","other");rows.put(row);}result.put("flowList",rows).put("hasNext",false);}
  else if(method.endsWith("queryAccountSummaryPoint"))result.put("effectPoint",balance);
  else if(method.endsWith("rightsExchange")){exchanges++;assert p.optString("rightsId").equals(id)&&p.optInt("assetAmount")==20&&p.optInt("needOrder")==0;if(change){count++;if(!noGain)balance-=20;}if(cancelAfter)TimeUtil.cancel=true;if(switchUid)UserIdMap.uid="other";if(rollover)MyUtils.day++;assert !RuntimeInfo.data.isEmpty();}
  else throw new AssertionError("mixed currency/unexpected RPC: "+method);
  return new JSONObject().put("success",!fail).put("result",result).toString();}}
 @@METHODS@@
 static class AntMemberRpcCall {@@RPC@@}
 static void reset(){RuntimeInfo.data.clear();RuntimeInfo.writable=true;UserIdMap.uid="uid";MyUtils.day=7;switchUid=rollover=false;exchanges=details=pages=count=Log.ok=0;balance=100;save=change=true;unknown=cash=order=redirect=hasNext=conflict=noGain=fail=cancelAfter=wrongHistory=TimeUtil.cancel=false;type="GOLD_TICKET";name="gold ticket";price=20;Status.flags.clear();Status.ints.clear();BeanRightIdMap.map.clear();}
 public static void main(String[] args){
  for(String nativeType:new String[]{"VIRTUAL_BENEFIT","MEMBER_BENEFIT",""}){reset();type=nativeType;name="原生会员权益";runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==1&&RuntimeInfo.data.isEmpty():nativeType;}
  for(String rejected:new String[]{"COUPON","CASH","GOODS","ORDER"}){reset();type=rejected;runExchange(Set.of(id),40);assert exchanges==0:rejected;}
  reset();change=false;runExchange(Set.of(id),40);assert exchanges==1&&!RuntimeInfo.data.isEmpty();Status.flags.clear();Status.ints.clear();MyUtils.day++;runExchange(Set.of(id),40);assert exchanges==1:"unknown receipt survives day/restart";
  reset();RuntimeInfo.writable=false;runExchange(Set.of(id),40);assert exchanges==0;
  reset();switchUid=true;runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==0&&!RuntimeInfo.data.isEmpty();
  reset();rollover=true;runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==0&&!RuntimeInfo.data.isEmpty();

  reset();runExchange(Set.of(),0);assert exchanges==0&&BeanRightIdMap.map.containsKey(id);
  reset();runExchange(Set.of(id),40);runExchange(Set.of(id),40);assert exchanges==1&&balance==80&&Log.ok==1&&Status.getIntFlagToday("member::beanExchangeSpent")==20;
  reset();runExchange(Set.of(id),10);assert exchanges==0;
  reset();balance=10;runExchange(Set.of(id),40);assert exchanges==0;
  reset();runExchange(Set.of("not-selected"),40);assert exchanges==0&&details==0;
  reset();unknown=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();cash=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();order=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();redirect=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();type="COUPON";runExchange(Set.of(id),40);assert exchanges==0;
  reset();price="0.1";runExchange(Set.of(id),40);assert exchanges==0;
  reset();assert beanRightCost(item().put("exchangeRequest",new JSONObject().put("assetAmount",21).put("needOrder",0)))==-1;
  assert beanRightCost(item().put("status","UNKNOWN"))==-1;
  assert beanRightCost(item().put("rightsCode","conflicting-id"))==-1;
  assert beanRightCost(item().put("lackPointNum",1))==-1;
  assert beanRightRows(new JSONObject().put("rightsList",JSONObject.NULL))==null;
  assert beanRightRows(new JSONObject().put("rightsList",new JSONArray().put(new JSONObject())))==null;
  reset();conflict=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();hasNext=true;runExchange(Set.of(id),40);assert exchanges==0&&pages==2:"pagination must progress";
  reset();save=false;runExchange(Set.of(id),40);assert exchanges==0;
  reset();change=false;runExchange(Set.of(id),40);runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==0&&Status.getIntFlagToday("member::beanExchangeSpent")==20;
  reset();noGain=true;runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==0;
  reset();wrongHistory=true;runExchange(Set.of(id),40);assert exchanges==1&&Log.ok==0;
  reset();fail=true;runExchange(Set.of(id),40);assert exchanges==0;
  reset();cancelAfter=true;try{runExchange(Set.of(id),40);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancel=false;runExchange(Set.of(id),40);assert exchanges==1;
  reset();TimeUtil.cancel=true;try{runExchange(Set.of(id),40);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert exchanges==0;
  System.out.println("PASS separate bean catalogue/currency, selected native pure-bean benefits, single write and durable unknown receipts, exact cost/budget reserve, detail/history/balance readback, cash/order/redirect/unknown safeguards, pagination/save/day/cancel and escaping");
 }
}
'''
code=code.replace('@@PAYLOAD@@',method(member,'static JSONObject memberFeaturePayload(')).replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
code=code.replace('@@METHODS@@','\n'.join(method(service,s) for s in ('private static JSONObject response(','private static long balance(','private static String beanRightId(','private static List<JSONObject> beanRightRows(','private static Map<String, JSONObject> beanCatalogue(','private static long beanRightCost(','private static int beanExchangeDay(','private static long beanHistoryCount(','static void runExchange(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('private static JSONObject beanPositionFactors(','public static String filterValidBizProperty(','public static String rightsRecommend(','public static String queryRightsPreExchangeFlows(','public static String queryRightsExchangeFlows(','public static String queryRightsDetail(','public static String rightsExchange(','public static String queryAccountSummaryPoint(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-bean-exchange-') as tmp:
 f=Path(tmp)/'BeanExchangeCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'BeanExchangeCheck'],check=True)
