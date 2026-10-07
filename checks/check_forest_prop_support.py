"""Production forest props: permanent/31-day usage, selected typed refill, stock/end/points readback."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java';rpc='model/task/antForest/AntForestRpcCall.java'
code=r"""
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestPropSupportCheck {
 static class MyUtils {static int d=6;static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,d,12,0);return c;}}
 static class UserIdMap {static String uid="self";static String getCurrentUid(){return uid;}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class RuntimeInfo {static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo runtime=new RuntimeInfo();static RuntimeInfo getInstance(){return runtime;}String getString(String k){return data.getOrDefault(k,"");}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> values=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(MyUtils.d+":"+s);}static void flagToday(String s){flags.add(MyUtils.d+":"+s);}static void clearFlag(String s){flags.remove(MyUtils.d+":"+s);}static int getIntFlagToday(String s){return values.getOrDefault(MyUtils.d+":"+s,0);}static void setIntFlagToday(String s,int n){values.put(MyUtils.d+":"+s,n);}static boolean canVitalityExchangeBenefitToday(String s,int n){return !hasFlagToday("forest::exchangeLimit::"+s)&&getIntFlagToday("exchange::"+s)<n;}static void vitalityExchangeBenefitToday(String s){setIntFlagToday("exchange::"+s,getIntFlagToday("exchange::"+s)+1);}}
 static class Log {static int ok;static void forest(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {@@GUARD@@}
 static class AntForestV2 {@@PARSERS@@}
 static class AntForestRpcCall {static String VERSION="check";static String getRandomString(int n){return "nonce";}@@RPC@@}
 static String type="ENERGY_DOUBLE_CLICK",group="doubleClick",id="card\"\\";static long end,expiry;static int bagCount,uses,confirms,exchanges,itemCount,points,price;static boolean apply,needConfirm,badAck,unknownType,wrongPoint,wrongCount,wrongStock,duplicate,badState,switchUid,crossDay,cancelAfter,cosmetic;
 static JSONObject prop(){return new JSONObject().put("propGroup",group).put("propType",type).put("holdsNum",bagCount).put("propIdList",bagCount==0?new JSONArray():new JSONArray().put(id)).put("recentExpireTime",expiry);}
 static JSONObject sku(){JSONObject s=new JSONObject().put("skuId","SKU").put("skuName",cosmetic?"\u4fdd\u62a4\u7f69\u76ae\u80a4":"card").put("price",new JSONObject().put("amount",price)).put("exchangedCount",itemCount).put("itemStatusList",badState?new JSONArray().put("UNKNOWN_LIMIT"):new JSONArray());if(!unknownType)s.put("rightsBatchConfigVO",new JSONObject().put("rightsConfigVOList",new JSONArray().put(new JSONObject().put("extend",new JSONObject().put("antiepScAssetsType",type)))));return s;}
 static class ApplicationHook {static String requestString(String method,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),r=new JSONObject().put("success",true);
  if(method.endsWith("queryPropList")){JSONArray b=new JSONArray().put(prop());if(duplicate)b.put(prop());r.put("forestPropVOList",b);}
  else if(method.endsWith("queryMiscInfo")){JSONArray a=new JSONArray();if(end>0)a.put(new JSONObject().put("propGroup","doubleClick").put("endTime",end));r.put("combineHandlerVOMap",new JSONObject().put("usingProp",new JSONObject().put("userPropVOS",a)));}
  else if(method.endsWith("consumeProp")){uses++;assert p.optString("propId").equals(id)&&p.optString("propType").equals(type)&&p.optString("propGroup").equals("doubleClick");if(p.optBoolean("secondConfirm"))confirms++;if(needConfirm&&!p.optBoolean("secondConfirm"))r.put("usePropStatus","NEED_CONFIRM_RENEW");else if(apply){bagCount=0;end=Math.max(end,System.currentTimeMillis())+300000;}if(badAck)r.put("resultCode","FAIL");if(switchUid)UserIdMap.uid="other";if(crossDay)MyUtils.d++;if(cancelAfter)TimeUtil.cancel=true;}
  else if(method.endsWith("itemDetail")){assert p.optString("spuId").equals("SPU");JSONArray a=new JSONArray().put(sku());if(duplicate)a.put(sku());r.put("spuItemInfoVO",new JSONObject().put("spuId","SPU").put("skuModelList",a));}
  else if(method.endsWith("queryVitalityStoreIndex"))r.put("userVitalityInfoVO",new JSONObject().put("totalVitalityAmount",points));
  else if(method.endsWith("exchangeBenefit")){exchanges++;assert p.optString("spuId").equals("SPU")&&p.optString("skuId").equals("SKU")&&p.optString("sceneCode").equals("ANTFOREST_VITALITY");assert !p.optString("requestId").isEmpty();assert !RuntimeInfo.data.isEmpty();if(apply){if(!wrongStock)bagCount=1;if(!wrongPoint)points-=price;if(!wrongCount)itemCount++;}if(badAck)r.put("success",false);if(switchUid)UserIdMap.uid="other";if(crossDay)MyUtils.d++;if(cancelAfter)TimeUtil.cancel=true;}
  else throw new AssertionError("unexpected action "+method);return r.toString();}}
 @@HELPER@@
 static void reset(){RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Status.values.clear();UserIdMap.uid="self";MyUtils.d=6;TimeUtil.cancel=false;Log.ok=uses=confirms=exchanges=itemCount=0;points=100;price=10;bagCount=1;end=0;expiry=System.currentTimeMillis()+3600000;apply=true;needConfirm=badAck=unknownType=wrongPoint=wrongCount=wrongStock=duplicate=badState=switchUid=crossDay=cancelAfter=cosmetic=false;type="ENERGY_DOUBLE_CLICK";group="doubleClick";}
 static boolean use(boolean renewal){return ForestPropSupport.consumeDouble(prop(),end,renewal,2);}
 static JSONArray refill(int budget){return ForestPropSupport.replenish(group,true,Collections.singletonMap("SKU",1),Collections.singletonMap("SKU",new JSONObject().put("spuId","SPU")),budget);}
 public static void main(String[] args){
  assert AntForestV2.forestSignPayload(null)==null;
  reset();assert ForestPropSupport.chooseDouble(new JSONArray().put(prop()),false,false,System.currentTimeMillis(),0)==null;assert ForestPropSupport.chooseDouble(new JSONArray().put(prop()),true,false,System.currentTimeMillis(),0)!=null;assert use(false)&&uses==1&&RuntimeInfo.data.isEmpty();assert !use(false)&&uses==1;
  reset();type="ENERGY_DOUBLE_CLICK_31DAYS";assert ForestPropSupport.chooseDouble(new JSONArray().put(prop()),false,false,System.currentTimeMillis(),0)==null;assert ForestPropSupport.chooseDouble(new JSONArray().put(prop()),false,true,System.currentTimeMillis(),0)!=null;end=System.currentTimeMillis()+86400000;needConfirm=true;assert !use(false)&&uses==0;assert use(true)&&uses==2&&confirms==1;
  reset();end=System.currentTimeMillis()+32L*86400000;type="ENERGY_DOUBLE_CLICK_31DAYS";assert !use(true)&&uses==0;
  reset();expiry=System.currentTimeMillis()-1;assert ForestPropSupport.usableInventory(new JSONArray().put(prop()),System.currentTimeMillis()).length()==0;assert !use(false)&&uses==0;
  reset();duplicate=true;assert !use(false)&&uses==0;
  reset();RuntimeInfo.writable=false;assert !use(false)&&uses==0;
  reset();apply=false;assert !use(false)&&uses==1;MyUtils.d++;assert !use(false)&&uses==1;
  reset();badAck=true;assert !use(false)&&uses==1&&!RuntimeInfo.data.isEmpty();
  reset();switchUid=true;assert !use(false)&&uses==1;
  reset();crossDay=true;assert !use(false)&&uses==1;
  reset();cancelAfter=true;try{use(false);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert uses==1&&!RuntimeInfo.data.isEmpty();
  reset();bagCount=0;assert refill(10)!=null&&exchanges==1&&points==90&&itemCount==1&&Log.ok==1&&RuntimeInfo.data.isEmpty();assert refill(10)==null&&exchanges==1;
  reset();bagCount=0;group="boost";type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";assert refill(10)!=null&&exchanges==1;
  reset();bagCount=0;assert refill(0)==null&&exchanges==0;assert refill(9)==null&&exchanges==0;
  reset();bagCount=0;type="NOT_SHIELD";group="shield";assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;type="FUTURE_ENERGY_DOUBLE_CLICK";assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;Map<String,Integer> nullSelection=new HashMap<>();nullSelection.put(null,1);assert ForestPropSupport.replenish(group,true,nullSelection,Collections.emptyMap(),10)==null&&exchanges==0;
  reset();bagCount=0;unknownType=true;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;cosmetic=true;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;badState=true;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;points=9;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;RuntimeInfo.writable=false;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;wrongPoint=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;MyUtils.d++;assert refill(10)==null&&exchanges==1&&ForestPropSupport.hasUnconfirmedRefill("SKU");
  reset();bagCount=0;wrongCount=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;
  reset();bagCount=0;wrongStock=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;
  reset();bagCount=0;duplicate=true;assert refill(10)==null&&exchanges==0;
  reset();bagCount=0;badAck=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;
  reset();bagCount=0;switchUid=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;
  reset();bagCount=0;crossDay=true;assert refill(10)==null&&exchanges==1&&Log.ok==0;
  reset();bagCount=0;cancelAfter=true;try{refill(10);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert exchanges==1&&!RuntimeInfo.data.isEmpty();
  System.out.println("PASS production permanent/31-day selection and verified renewal, expired-stock filtering, selected typed refill, points/count/stock readback, persistence/account/day/cancel/unknown budget guards");
 }
}
"""
body=(SOURCE/'model/task/antForest/ForestPropSupport.java').read_text(encoding='utf-8');body=body[body.index('final class ForestPropSupport'):]
code=code.replace('@@GUARD@@',method('rpc/intervallimit/RpcRequestGuard.java','public static boolean isFailure('))
code=code.replace('@@HELPER@@','static '+body).replace('@@PARSERS@@','\n'.join(method(forest,s) for s in ('static JSONObject forestSignPayload(','static long forestFeatureLong('))).replace('@@RPC@@','\n'.join(method(rpc,s) for s in ('public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm)','public static String queryPropList(boolean onlyGive)','public static String queryMiscInfo(','public static String itemDetail(','public static String queryVitalityStoreIndex(')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-forest-props-') as tmp:
 f=Path(tmp)/'ForestPropSupportCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestPropSupportCheck'],check=True)
