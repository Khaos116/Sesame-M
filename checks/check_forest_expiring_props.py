"""Production valid near-expiry category consumption with live stock/effect/bubble readback."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent/'audit_regressions'))
from run import method, SOURCE
code = r'''
import org.json.*;import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestExpiringPropsCheck {
 static class IdAndName{String id,name;}static class OtherEntity extends IdAndName{OtherEntity(String i,String n){id=i;name=n;}}
 static class MyUtils{static int day=7,hour=6,minute=0;static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day,hour,minute);return c;}}
 static class UserIdMap{static String uid="self";static String getCurrentUid(){return uid;}}
 static class TimeUtil{static boolean cancel;static void sleep(long ms){if(cancel)throw new TaskCancelledException();}}
 static class RuntimeInfo{static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo r=new RuntimeInfo();static RuntimeInfo getInstance(){return r;}String getString(String k){return data.getOrDefault(k,"");}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Status{static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String k){return flags.contains(MyUtils.day+":"+k);}static void flagToday(String k){flags.add(MyUtils.day+":"+k);}static int getIntFlagToday(String k){return counts.getOrDefault(MyUtils.day+":"+k,0);}static void setIntFlagToday(String k,int n){counts.put(MyUtils.day+":"+k,n);}}
 static class Log{static int ok;static void forest(String s){ok++;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard{@@GUARD@@}
 static class AntForestV2{@@PARSERS@@}
 static class ForestPropSupport{static long DAY=86400000L;static long number(JSONObject o,String k){return AntForestV2.forestFeatureLong(o,k);}@@SUPPORT@@}
 static class AntForestRpcCall{static String VERSION="check";static String getRandomString(int n){return "nonce";}@@RPC@@}
 @@HELPER@@
 static Map<String,Long> ends=new HashMap<>();static Map<String,Double> factors=new HashMap<>();static List<String> ids=new ArrayList<>();static String group,type;static long expiry,duration,bubbleAt;static double factor;static int uses,confirms;static boolean apply,badAck,noEffect,noStock,duplicate,conflictType,needConfirm,switchUid,crossDay,cancelAfter,noBubbles,rootType,badState;
 static JSONObject prop(){JSONObject config=new JSONObject().put("propType",conflictType?"NOT_SHIELD":type).put("durationTime",duration).put("detail",new JSONObject().put("factor",factor));JSONObject p=new JSONObject().put("propGroup",group).put("holdsNum",ids.size()).put("propIdList",new JSONArray(ids)).put("recentExpireTime",expiry).put("propConfigVO",config);if(rootType)p.put("propType",type);return p;}
 static class ApplicationHook{static String requestString(String method,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),r=new JSONObject().put("success",true);
  if(method.endsWith("queryPropList")){JSONArray a=new JSONArray().put(prop());if(duplicate)a.put(prop());r.put("forestPropVOList",a);}
  else if(method.endsWith("queryMiscInfo")){JSONArray a=new JSONArray();for(Map.Entry<String,Long> e:ends.entrySet())a.put(new JSONObject().put("propGroup",e.getKey()).put("endTime",e.getValue()).put("detail",new JSONObject().put("factor",factors.getOrDefault(e.getKey(),0d))));if(badState)a.put(new JSONObject().put("propGroup","shield").put("endTime","broken"));r.put("combineHandlerVOMap",new JSONObject().put("usingProp",new JSONObject().put("userPropVOS",a)));}
  else if(method.endsWith("queryHomePage")){JSONArray b=new JSONArray();if(!noBubbles)b.put(new JSONObject().put("id",1).put("collectStatus","WAITING").put("produceTime",bubbleAt).put("fullEnergy",10));r.put("now",System.currentTimeMillis()).put("bubbles",b);}
  else if(method.endsWith("consumeProp")){uses++;assert p.optString("propGroup").equals(group)&&p.optString("propType").equals(type)&&p.optString("propId").equals(ids.get(0));assert RuntimeInfo.data.containsKey("forestExpiringPropReceipt");boolean confirm=p.optBoolean("secondConfirm");if(confirm)confirms++;if(needConfirm&&!confirm)r.put("usePropStatus","NEED_CONFIRM_RENEW");else if(apply){if(!noStock)ids.remove(0);if(!noEffect){if(group.equals("boost"))bubbleAt-=60000;else{ends.put(group,Math.max(System.currentTimeMillis(),ends.getOrDefault(group,0L))+duration*1000);factors.put(group,factor);}}}if(badAck)r.put("resultCode","FAIL");if(switchUid)UserIdMap.uid="other";if(crossDay)MyUtils.day++;if(cancelAfter)TimeUtil.cancel=true;}
  else throw new AssertionError(method);return r.toString();}static String requestString(String m,String p,int count,int wait){return requestString(m,p);}}
 static void reset(){RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Status.counts.clear();UserIdMap.uid="self";MyUtils.day=7;MyUtils.hour=6;MyUtils.minute=0;TimeUtil.cancel=false;Log.ok=uses=confirms=0;ends.clear();factors.clear();ids.clear();ids.add("card\"\\1");group="shield";type="LIMIT_TIME_ENERGY_SHIELD";expiry=System.currentTimeMillis()+3600000;duration=7*86400;factor=0;bubbleAt=System.currentTimeMillis()+600000;apply=true;badAck=noEffect=noStock=duplicate=conflictType=needConfirm=switchUid=crossDay=cancelAfter=noBubbles=rootType=badState=false;}
 static int use(){return ForestExpiringProps.consume(true,Set.of(group),24,3);}static boolean pending(){return RuntimeInfo.data.containsKey("forestExpiringPropReceipt");}
 public static void main(String[] args){
  reset();assert ForestExpiringProps.getOptions().size()==6;ids.add("card2");assert use()==2&&uses==2&&Log.ok==2&&!pending();
  reset();assert ForestExpiringProps.consume(false,Set.of(group),24,3)==0&&uses==0;assert ForestExpiringProps.consume(true,Set.of(),24,3)==0;assert ForestExpiringProps.consume(true,Set.of(group),24,0)==0;assert ForestExpiringProps.consume(true,Set.of(group),25,3)==0;
  reset();expiry=System.currentTimeMillis()-1;assert use()==0&&uses==0;
  reset();expiry=0;assert use()==0&&uses==0;
  reset();expiry=System.currentTimeMillis()+25*3600000;assert use()==0&&uses==0;
  reset();type="ENERGY_SHIELD";assert use()==0&&uses==0;
  reset();type="NOT_SHIELD";assert use()==0&&uses==0;
  reset();type="FUTURE_LIMIT_TIME_ENERGY_SHIELD";assert use()==0&&uses==0;
  reset();rootType=true;conflictType=true;assert use()==0&&uses==0;
  reset();duplicate=true;assert use()==0&&uses==0;
  reset();badState=true;assert use()==0&&uses==0;
  reset();ends.put(group,System.currentTimeMillis()+8L*86400000);assert use()==0&&uses==0;
  reset();ends.put("energyBombCard",System.currentTimeMillis()+86400000);assert use()==0&&uses==0;
  reset();ends.put(group,System.currentTimeMillis()+86400000);duration=3600;assert use()==0&&uses==0;
  reset();ends.put(group,System.currentTimeMillis()+86400000);needConfirm=true;assert use()==1&&uses==2&&confirms==1&&!pending();
  reset();group="stealthCard";type="LIMIT_TIME_STEALTH_CARD";duration=86400;assert use()==1&&uses==1;
  reset();group="energyBombCard";type="ENERGY_BOMB_CARD";duration=86400;assert use()==1&&uses==1;
  reset();group="energyBombCard";type="ENERGY_BOMB_CARD";ends.put("shield",System.currentTimeMillis()+86400000);assert use()==0&&uses==0;
  reset();group="energyBombCard";type="ENERGY_BOMB_CARD";ends.put(group,System.currentTimeMillis()+86400000);assert use()==0&&uses==0;
  reset();group="robExpandCard";type="SHAMO_ROB_EXPAND_CARD_1.5_1DAYS";factor=1.5;duration=86400;assert use()==1&&uses==1;
  reset();group="robExpandCard";type="VITALITY_ROB_EXPAND_CARD_1.1_3DAYS";factor=1.1;ends.put(group,System.currentTimeMillis()+86400000);factors.put(group,1.5);assert use()==0&&uses==0;
  reset();group="doubleClick";type="ENERGY_DOUBLE_CLICK_31DAYS";duration=31*86400;ends.put(group,System.currentTimeMillis()+86400000);needConfirm=true;assert use()==1&&uses==2&&confirms==1&&Status.getIntFlagToday("forest::smartDoubleAttempts")==1;
  reset();group="doubleClick";type="ENERGY_DOUBLE_CLICK_31DAYS";ends.put(group,System.currentTimeMillis()+32L*86400000);assert use()==0&&uses==0;
  reset();group="doubleClick";type="ENERGY_DOUBLE_CLICK_31DAYS";RuntimeInfo.data.put("forestPropDoubleReceipt","unknown");assert use()==0&&uses==0;
  reset();group="doubleClick";type="LIMIT_TIME_ENERGY_DOUBLE_CLICK";assert use()==0&&uses==0;
  reset();group="boost";type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";assert use()==1&&uses==1&&bubbleAt<System.currentTimeMillis()+550000;
  reset();group="boost";type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";MyUtils.hour=7;MyUtils.minute=11;assert use()==0&&uses==0;
  reset();group="boost";type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";noBubbles=true;assert use()==0&&uses==0;
  reset();group="boost";type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";noEffect=true;assert use()==0&&uses==1&&pending();
  reset();RuntimeInfo.writable=false;assert use()==0&&uses==0;
  reset();RuntimeInfo.data.put("forestExpiringPropAttempts","broken");assert use()==0&&uses==0;
  reset();assert ForestExpiringProps.consume(true,Set.of(group),24,1)==1;Status.flags.clear();Status.counts.clear();ids.add("another");ends.clear();assert ForestExpiringProps.consume(true,Set.of(group),24,1)==0&&uses==1;
  reset();apply=false;assert use()==0&&uses==1&&pending()&&ForestExpiringProps.hasUnconfirmed(group);MyUtils.day++;assert use()==0&&uses==1&&pending();
  reset();badAck=true;assert use()==0&&uses==1&&pending()&&Log.ok==0;
  reset();noStock=true;assert use()==0&&uses==1&&pending();
  reset();noEffect=true;assert use()==0&&uses==1&&pending();
  reset();switchUid=true;assert use()==0&&uses==1&&pending();
  reset();crossDay=true;assert use()==0&&uses==1&&pending();
  reset();cancelAfter=true;try{use();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert uses==1&&pending();
  reset();RuntimeInfo.data.put("forestExpiringPropReceipt","broken");assert ForestExpiringProps.hasUnconfirmed("shield")&&ForestExpiringProps.hasUnconfirmed("doubleClick")&&use()==0;
  reset();RuntimeInfo.data.put("forestExpiringPropReceipt","{\"group\":\"NOT_SHIELD\"}");assert ForestExpiringProps.hasUnconfirmed("shield")&&ForestExpiringProps.hasUnconfirmed("doubleClick")&&use()==0;
  System.out.println("PASS production selected valid near-expiry categories: exact types, expiry/cutoff, active effect safeguards, confirmations, stock/effect/bubble readback, durable budget/unknown/account/day/cancel guards");
 }
}
'''
forest = 'model/task/antForest/AntForestV2.java'
rpc = 'model/task/antForest/AntForestRpcCall.java'
support = 'model/task/antForest/ForestPropSupport.java'
text = (SOURCE/'model/task/antForest/ForestExpiringProps.java').read_text(encoding='utf-8')
code = code.replace('@@HELPER@@', 'static '+text[text.index('final class ForestExpiringProps'):])
code = code.replace('@@GUARD@@', method('rpc/intervallimit/RpcRequestGuard.java','public static boolean isFailure('))
code = code.replace('@@PARSERS@@', '\n'.join(method(forest,s) for s in ('static JSONObject forestSignPayload(', 'static long forestFeatureLong(')))
code = code.replace('@@SUPPORT@@', '\n'.join(method(support,s) for s in ('static JSONArray usableInventory(', 'static boolean doubleRenewAllowed(')))
code = code.replace('@@RPC@@', '\n'.join(method(rpc,s) for s in ('public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm)', 'public static String queryPropList(boolean onlyGive)', 'public static String queryMiscInfo(', 'public static String queryHomePage(')))
cache = Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-expiring-props-') as tmp:
    source = Path(tmp)/'ForestExpiringPropsCheck.java';source.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(source),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestExpiringPropsCheck'],check=True)
