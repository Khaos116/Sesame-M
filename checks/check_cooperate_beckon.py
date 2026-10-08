"""Replay opt-in captain beckoning using real production methods/RPC, without sending messages."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/protectEcology/ProtectEcology.java';rpc='model/task/protectEcology/CooperateRpcCall.java'
assert 'private static void cooperateBeckon(' in (SOURCE/service).read_text(encoding='utf-8'),'Missing captain beckoning'
code=r'''
import java.util.*;import java.math.BigDecimal;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class CooperateBeckonCheck {
 static String TAG="check",coop="coop\"\\&=",friend="friend\"\\";static boolean enabled=true,save=true,eligible=true,change=true,unknown,duplicate,fail,drift,cancelAfter;static String admin="uid";static int hour=18,sends,plantReads;
 static class Field<T>{T value;Field(T v){value=v;}T getValue(){return value;}}
 static Field<Boolean> cooperateSendCooperateBeckon=new Field<>(true);static Field<Set<String>> cooperateBeckonList=new Field<>(Set.of(coop));
 static Field<Map<String,Integer>> cooperateWaterList=new Field<>(Map.of(coop,100)),cooperateWaterTotalLimitList=new Field<>(Map.of());
 static boolean waterRanks;static String dailyRank,totalRank;
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.set(Calendar.HOUR_OF_DAY,hour);return c;}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> ints=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static int getIntFlagToday(String s){return ints.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){ints.put(s,n);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class UserIdMap {static String getCurrentUid(){return "uid";}}
 static class CooperationIdMap {static Map<String,String> map=new HashMap<>();static void load(String s){}static void add(String k,String v){map.put(k,v);}static boolean save(String s){return save;}}
 static class Log {static int ok;static void forest(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class ApplicationHook {static String requestString(String method,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),r=new JSONObject().put("resultCode",fail?"FAIL":"SUCCESS");
  if(waterRanks){assert method.endsWith("queryCooperateRank");if(TimeUtil.cancel)throw new TaskCancelledException();return "D".equals(p.optString("bizType"))?dailyRank:totalRank;}
  if(method.endsWith("queryUserCooperatePlantList"))r.put("cooperatePlants",new JSONArray().put(new JSONObject().put("cooperationId",coop).put("name","team")));
  else if(method.endsWith("queryCooperatePlant")){plantReads++;assert p.optString("cooperationId").equals(coop);r.put("cooperatePlant",new JSONObject().put("cooperationId",coop).put("name","team").put("admin",drift&&plantReads>1?"other":admin));}
  else if(method.endsWith("queryCooperateRank")){assert p.optString("bizType").equals("D")&&p.optString("cooperationId").equals(coop);JSONObject row=new JSONObject().put("userId",friend).put("canBeckon",eligible).put("displayName","friend");if(unknown)row.remove("canBeckon");JSONArray rows=new JSONArray().put(row).put(new JSONObject().put("userId","uid").put("canBeckon",true));if(duplicate)rows.put(row);r.put("cooperateRankInfos",rows);}
  else if(method.endsWith("sendCooperateBeckon")){sends++;assert p.optString("userId").equals(friend)&&p.optString("cooperationId").equals(coop);String url=p.optString("link");assert url.startsWith("alipays://");String inner=java.net.URLDecoder.decode(url.substring(url.indexOf("&url=")+5),java.nio.charset.StandardCharsets.UTF_8);String idEncoded=inner.substring(inner.indexOf("cooperationId=")+14,inner.indexOf("&sourceName="));assert java.net.URLDecoder.decode(idEncoded,java.nio.charset.StandardCharsets.UTF_8).equals(coop):"nested URL ID must survive ampersand and quotes";if(change)eligible=false;if(cancelAfter)TimeUtil.cancel=true;}
  else throw new AssertionError("unexpected watering/send RPC: "+method);return r.toString();}}
 @@METHODS@@
 static class CooperateRpcCall {@@RPC@@}
 static void reset(){enabled=save=eligible=change=true;unknown=duplicate=fail=drift=cancelAfter=TimeUtil.cancel=false;admin="uid";hour=18;sends=plantReads=Log.ok=0;cooperateSendCooperateBeckon.value=true;cooperateBeckonList.value=Set.of(coop);Status.flags.clear();Status.ints.clear();CooperationIdMap.map.clear();}
 static String rank(Object amount){return new JSONObject().put("resultCode","SUCCESS").put("cooperateRankInfos",
  new JSONArray().put(new JSONObject().put("userId","uid").put("energySummation",amount))).toString();}
 static void waterAllowance(){
  reset();waterRanks=true;cooperateWaterTotalLimitList.value=Map.of();
  String empty=new JSONObject().put("resultCode","SUCCESS").put("cooperateRankInfos",new JSONArray()).toString();
  dailyRank=empty;assert getEnergyCount("uid",coop,1000)==100;
  dailyRank=rank(30);assert getEnergyCount("uid",coop,1000)==70;
  dailyRank=rank("30");assert getEnergyCount("uid",coop,1000)==70;
  for(String invalid:new String[]{"{}","{\"resultCode\":\"FAIL\"}","{\"resultCode\":\"SUCCESS\"}",
   "{\"resultCode\":\"SUCCESS\",\"cooperateRankInfos\":[null]}",rank(-1),rank(1.5),rank("bad"),rank(JSONObject.NULL)}){
   dailyRank=invalid;assert getEnergyCount("uid",coop,1000)==0:"unknown daily watering became zero baseline";
   dailyRank=empty;totalRank=invalid;cooperateWaterTotalLimitList.value=Map.of(coop,500);
   assert getEnergyCount("uid",coop,1000)==0:"unknown total watering became zero baseline";
   cooperateWaterTotalLimitList.value=Map.of();
  }
  dailyRank=rank(20);totalRank=rank(450);cooperateWaterTotalLimitList.value=Map.of(coop,500);
  assert getEnergyCount("uid",coop,1000)==50;
  TimeUtil.cancel=true;try{getEnergyCount("uid",coop,1000);throw new AssertionError("watering cancellation swallowed");}catch(TaskCancelledException expected){}
  TimeUtil.cancel=false;waterRanks=false;
  System.out.println("PASS watering daily/total query failures stop spending; empty/safe counts preserve allowance; cancellation propagates");
 }
 public static void main(String[] args){
  reset();cooperateBeckon();cooperateBeckon();assert sends==1&&Log.ok==1;
  reset();hour=17;cooperateBeckon();assert sends==0&&CooperationIdMap.map.containsKey(coop);
  reset();cooperateBeckonList.value=Set.of();cooperateBeckon();assert sends==0&&CooperationIdMap.map.containsKey(coop);
  reset();admin="other";cooperateBeckon();assert sends==0;
  reset();drift=true;cooperateBeckon();assert sends==0;
  reset();eligible=false;cooperateBeckon();assert sends==0;
  reset();unknown=true;cooperateBeckon();assert sends==0;
  reset();duplicate=true;cooperateBeckon();assert sends==0;
  reset();Status.setIntFlagToday("cooperate::beckonAttempts",20);cooperateBeckon();assert sends==0;
  reset();Status.setIntFlagToday("cooperate::beckonAttempts",19);cooperateBeckon();assert sends==1&&Status.getIntFlagToday("cooperate::beckonAttempts")==20;
  assert beckonResponse(new JSONObject().put("resultCode","SUCCESS").put("success",false).toString())==null;
  assert beckonResponse(new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("resultCode","FAIL")).toString())==null;
  reset();change=false;cooperateBeckon();cooperateBeckon();assert sends==1&&Log.ok==0;
  reset();save=false;cooperateBeckon();assert sends==0;
  reset();fail=true;cooperateBeckon();assert sends==0;
  reset();cooperateSendCooperateBeckon.value=false;cooperateBeckon();assert sends==0;
  reset();cancelAfter=true;try{cooperateBeckon();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancel=false;cooperateBeckon();assert sends==1;
  reset();TimeUtil.cancel=true;try{cooperateBeckon();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert sends==0;
  System.out.println("PASS selected teams/captain/current eligibility/GMT+8 after 18, one recipient per day/20 total, authoritative cooldown, unknown/duplicates/identity/save/failure/cancel and JSON/nested URL escaping");
  waterAllowance();
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(service,s) for s in ('private static JSONObject beckonResponse(','private static JSONObject beckonPlant(','private static Map<String, JSONObject> beckonMembers(','private static void cooperateBeckon(','private static int getEnergyCount(','private static int getEnergySummation(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('queryUserCooperatePlantList','queryCooperatePlant','queryCooperateRank','sendCooperateBeckon')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-cooperate-beckon-') as tmp:
 f=Path(tmp)/'CooperateBeckonCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'CooperateBeckonCheck'],check=True)
