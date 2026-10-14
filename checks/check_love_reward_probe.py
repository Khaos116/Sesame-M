"""Run production love reward probe/payloads: inventory evidence, headers, GMT+8, retry/account/cancel guards; no live RPC."""
from pathlib import Path
import os, subprocess, tempfile, sys
sys.dont_write_bytecode = True
from audit_regressions.run import method

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/aw1y2z/sesame'
CACHE = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
JSON = next(p for p in (CACHE / 'org.json/json').glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
LOMBOK = next((CACHE / 'org.projectlombok/lombok').glob('*/*/lombok-*.jar'))
RPC = 'model/task/antForest/AntForestRpcCall.java'
probe = (SOURCE / 'model/task/antForest/ForestLoveReward.java').read_text(encoding='utf-8')
assert 'deleteRewardCache' not in '\n'.join(method(RPC, s) for s in ('public static String loveRewardHome(', 'private static String loveRewardRequest('))
run = method('model/task/antForest/AntForestV2.java', 'public void run(')
assert 'loveteam(loveteamWaterNum.getValue());\n                        ForestLoveReward.run();' in run
assert 'FLAG_LOVETEAM_WATER' not in probe and 'flagToday' not in probe

files = {
 'util/MyUtils.java': '''package io.github.aw1y2z.sesame.util;
import java.util.*;import org.json.*;
public class MyUtils {public static long clock=java.time.Instant.parse("2026-02-28T17:00:00Z").toEpochMilli();
 public static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(clock);return c;}
 public static JSONObject newJSONObject(){return new JSONObject();}public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}''',
 'util/Log.java': '''package io.github.aw1y2z.sesame.util;import java.util.*;
public class Log {public static List<String> lines=new ArrayList<>();public static void record(String s){lines.add(s);}}''',
 'util/TimeUtil.java': '''package io.github.aw1y2z.sesame.util;
public class TimeUtil {public static void sleep(long ms){if(Thread.currentThread().isInterrupted())throw new TaskCancelledException();}}''',
 'util/DailyTask.java': '''package io.github.aw1y2z.sesame.util;
public class DailyTask {public static boolean manual;public static boolean isManual(){return manual;}}''',
 'util/idMap/UserIdMap.java': '''package io.github.aw1y2z.sesame.util.idMap;
public class UserIdMap {public static String uid="A";public static String getCurrentUid(){return uid;}}''',
 'data/RuntimeInfo.java': '''package io.github.aw1y2z.sesame.data;import java.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class RuntimeInfo {public static Map<String,Map<String,Object>> values=new HashMap<>();public static boolean writable=true;private final String uid;
 private RuntimeInfo(String id){uid=id;}public static RuntimeInfo getInstance(){return new RuntimeInfo(UserIdMap.uid);}
 public Long getLong(String key,long fallback){Object x=values.computeIfAbsent(uid,k->new HashMap<>()).get(key);return x instanceof Number?((Number)x).longValue():fallback;}
 public void put(String key,Object value){values.computeIfAbsent(uid,k->new HashMap<>()).put(key,value);}
 public String getString(String key){Object x=values.computeIfAbsent(uid,k->new HashMap<>()).get(key);return x instanceof String?(String)x:"";}
 public boolean putVerified(String key,String value){if(!writable)return false;put(key,value);return true;}}''',
 'rpc/intervallimit/RpcRequestGuard.java': 'package io.github.aw1y2z.sesame.rpc.intervallimit;import org.json.*;public class RpcRequestGuard {\n'
     + method('rpc/intervallimit/RpcRequestGuard.java', 'public static boolean isFailure(') + '\n}',
 'model/task/antForest/AntForestRpcCall.java': '''package io.github.aw1y2z.sesame.model.task.antForest;
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;import io.github.aw1y2z.sesame.util.*;
public class AntForestRpcCall {private static final String VERSION="20250818";'''
     + '\n'.join(method(RPC, s) for s in ('public static String loveRewardForestHome(', 'public static String loveRewardHome(',
       'private static String loveRewardRequest(', 'public static String loveRewardSubscribe(', 'public static String loveRewardConfirm(',
       'public static String loveRewardAnimals(', 'public static String queryPropList(boolean onlyGive)')) + '\n}',
 'hook/ApplicationHook.java': r'''package io.github.aw1y2z.sesame.hook;
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.entity.RpcEntity;import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class ApplicationHook {
 public static boolean offline,grant,delayed,failBefore,failAfter,failedHome,duplicate,fraction,switched,cancelled,throwHome,noSubscription,invalidType,invalidSubscription;
 public static boolean cacheCleared,stuck,failedConfirm,skippedConfirm,mixed,badTeam,badAnimal,moreAnimals,grantAnimal,renewAnimal,missingExpiry,crossDay,crossDayAfterConfirm,unknownWidget,failCompletionSave;
 public static int bag=2,reads,homes,subscribes,confirms,animalReads,animalCount=1;public static long animalExpiry=1000;public static String widget="reward",homeCode="LoveEnergyShield";
 public static List<String> calls=new ArrayList<>();public static RpcEntity last;
 public static boolean isOffline(){return offline;}
 public static String requestString(String m,String p){return requestString(new RpcEntity(m,p),1,0);}
 public static String requestString(RpcEntity e,int count,int wait){
  assert count==1&&wait==0;last=e;String m=e.getRequestMethod();calls.add(m);
  if(m.endsWith("queryPropList")){
   reads++;if((failBefore&&reads==1)||(failAfter&&reads>1))return "{}";
   if(delayed&&reads>=3)bag=3;
   JSONObject prop=new JSONObject().put("propType","LOVE2_PLANT_ENERGY_SHIELD").put("holdsNum",fraction?2.5:bag);
   if(invalidType&&reads==1)prop.remove("propType");
   JSONArray list=new JSONArray().put(prop);if(duplicate)list.put(prop);
   return new JSONObject().put("resultCode","SUCCESS").put("hasMore",false).put("forestPropVOList",list).toString();
  }
  JSONObject body=new JSONArray(e.getRequestData()).optJSONObject(0);
  if(m.endsWith("queryAnimalPropList")){
   animalReads++;assert Boolean.FALSE.equals(body.opt("pageQuery"));
   JSONObject main=new JSONObject().put("propType","QIXITU_1.0_1DAYS").put("holdsNum",animalCount);
   if(!(missingExpiry&&animalReads==1))main.put("recentExpireTime",animalExpiry);
   if(badAnimal)main.remove("holdsNum");
   return new JSONObject().put("resultCode","SUCCESS").put("hasMore",moreAnimals).put("animalProps",new JSONArray().put(new JSONObject().put("main",main).put("partner",new JSONObject().put("name","七夕兔")))).toString();
  }
  if(m.endsWith("loveForestHome")||m.endsWith("loveHome")||m.endsWith("love.doAction")){
   JSONObject h=new JSONObject(e.getRequestHeaders());
   assert "chInfo_ch_appcenter__chsub_9patch".equals(h.optString("source"));assert h.optString("source").equals(h.optString("ags-source"));
   assert body.optString("source").equals(h.optString("source"));assert !h.has("nb_url")&&!h.has("nbappid");
  }
  if(m.endsWith("loveForestHome"))return new JSONObject().put("resultCode","SUCCESS").put("teamId","team").put("widgetStatus",cacheCleared?(unknownWidget?"unknown":"callWater"):widget).toString();
  if(m.endsWith("loveHome")){
   homes++;if(cancelled)throw new TaskCancelledException();if(switched)UserIdMap.uid="B";
   if(crossDayAfterConfirm&&homes==2)MyUtils.clock+=86400000;
   if(throwHome)throw new IllegalArgumentException("secret-token");if(grant)bag=3;
   if(grantAnimal)animalCount=2;if(renewAnimal)animalExpiry=2000;if(crossDay&&homes==1)MyUtils.clock+=86400000;
   if(failedHome)return "{\"error\":1009,\"success\":true}";
   JSONArray pending=new JSONArray();if(!cacheCleared)pending.put(new JSONObject().put("rewardCode",homeCode).put("provideType","daily").put("rewardType","daily").put("rewardRepeat",false));
   if(mixed)pending.put(new JSONObject().put("rewardCode","LovePendant3Day").put("rewardType","normal"));
   JSONObject home=new JSONObject().put("resultCode","SUCCESS").put("useDailyReward",true).put("teamInfo",new JSONObject().put("teamId",badTeam?"other":"team").put("teamStatus","ACTIVATED").put("popupRewardList",pending));
   home.put("pageProperties",noSubscription?new JSONObject():new JSONObject().put("subscribeId",invalidSubscription?new JSONObject():"entity"));
   return new JSONObject().put("resData",home).toString();
  }
  if(m.endsWith("love.doAction")){
   if(skippedConfirm)return "{\"resultCode\":\"RPC_SKIPPED\",\"error\":\"RPC_SKIPPED\",\"success\":false}";
   confirms++;assert body.optString("actionCode").equals("deleteRewardCache")&&body.optString("teamId").equals("team");
   if(failedConfirm)return "{\"resultCode\":\"FAIL\",\"success\":false}";
   if(!stuck)cacheCleared=true;if(failCompletionSave)io.github.aw1y2z.sesame.data.RuntimeInfo.writable=false;return "{\"resultCode\":\"SUCCESS\",\"success\":true}";
  }
  if(m.endsWith("querySubscribeRelation")){
   subscribes++;assert e.getRequestHeaders()==null;assert "60000002".equals(body.optString("ownerId"));assert "entity".equals(body.optJSONArray("entityIds").optString(0));
   return "{\"success\":true,\"resultCode\":200}";
  }
  throw new AssertionError("Unexpected RPC "+m);
 }
}''',
 'model/task/antForest/LoveRewardProbeCheck.java': r'''package io.github.aw1y2z.sesame.model.task.antForest;
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.hook.ApplicationHook;import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;import io.github.aw1y2z.sesame.data.RuntimeInfo;
public class LoveRewardProbeCheck {
 static void reset(){RuntimeInfo.values.clear();UserIdMap.uid="A";DailyTask.manual=false;Log.lines.clear();ApplicationHook.calls.clear();ApplicationHook.bag=2;
 RuntimeInfo.writable=true;ApplicationHook.reads=ApplicationHook.homes=ApplicationHook.subscribes=ApplicationHook.confirms=ApplicationHook.animalReads=0;ApplicationHook.animalCount=1;ApplicationHook.animalExpiry=1000;ApplicationHook.widget="reward";ApplicationHook.homeCode="LoveEnergyShield";
 ApplicationHook.cacheCleared=ApplicationHook.stuck=ApplicationHook.failedConfirm=ApplicationHook.skippedConfirm=ApplicationHook.mixed=ApplicationHook.badTeam=ApplicationHook.badAnimal=ApplicationHook.moreAnimals=ApplicationHook.grantAnimal=ApplicationHook.renewAnimal=ApplicationHook.missingExpiry=ApplicationHook.crossDay=ApplicationHook.crossDayAfterConfirm=ApplicationHook.unknownWidget=ApplicationHook.failCompletionSave=false;
 ApplicationHook.offline=ApplicationHook.grant=ApplicationHook.delayed=ApplicationHook.failBefore=ApplicationHook.failAfter=ApplicationHook.failedHome=ApplicationHook.duplicate=ApplicationHook.fraction=ApplicationHook.switched=ApplicationHook.cancelled=ApplicationHook.throwHome=ApplicationHook.noSubscription=ApplicationHook.invalidType=ApplicationHook.invalidSubscription=false;}
 static boolean has(String s){return Log.lines.stream().anyMatch(x->x.contains(s));}
 public static void main(String[] args) throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  reset();AntForestRpcCall.loveRewardHome();JSONObject p=new JSONArray(ApplicationHook.last.getRequestData()).optJSONObject(0);assert p.optString("calenderStart").equals("20260301")&&p.optString("calenderEnd").equals("20260331");
  MyUtils.clock=java.time.Instant.parse("2028-02-28T17:00:00Z").toEpochMilli();AntForestRpcCall.loveRewardHome();p=new JSONArray(ApplicationHook.last.getRequestData()).optJSONObject(0);assert p.optString("calenderStart").equals("20280201")&&p.optString("calenderEnd").equals("20280229");
  reset();ForestLoveReward.run();assert ApplicationHook.homes==2&&ApplicationHook.subscribes==1&&ApplicationHook.reads==3&&ApplicationHook.confirms==1 : Log.lines;assert !has("不能确认本轮新发奖")&&!has("[结果]：观察")&&has("领奖提示已消除");
  assert Log.lines.size()==4&&!has("success=true")&&!has("页面参数") : Log.lines;
  assert !has("普通道具数量=")&&!has("七夕兔")&&!has("QIXITU_1.0_1DAYS")&&!has("LOVE2_PLANT_ENERGY_SHIELD") : "inventory contents leaked into runtime log";
  assert ApplicationHook.calls.get(0).endsWith("loveForestHome")&&ApplicationHook.calls.get(1).endsWith("queryPropList")&&ApplicationHook.calls.get(2).endsWith("queryAnimalPropList")&&ApplicationHook.calls.get(3).endsWith("loveHome");
  int completedCalls=ApplicationHook.calls.size();ForestLoveReward.run();assert ApplicationHook.calls.size()==completedCalls&&!has("间隔不足30分钟");DailyTask.manual=true;ForestLoveReward.run();assert ApplicationHook.confirms==1&&ApplicationHook.reads==3;
  DailyTask.manual=false;RuntimeInfo.getInstance().put("Forest::loveRewardConfirmProbeTime",1L);ForestLoveReward.run();assert ApplicationHook.calls.size()==completedCalls+1:"daily completion must survive the query interval";
  reset();ApplicationHook.stuck=true;ForestLoveReward.run();assert ApplicationHook.confirms==1&&has("仍未确认");
  int waitingCalls=ApplicationHook.calls.size();ForestLoveReward.run();assert ApplicationHook.calls.size()==waitingCalls&&!has("间隔不足30分钟");
  for(int attempt=2;attempt<=4;attempt++){RuntimeInfo.getInstance().put("Forest::loveRewardConfirmProbeTime",1L);ForestLoveReward.run();assert ApplicationHook.confirms==Math.min(3,attempt);}
  assert has("3次");DailyTask.manual=true;ForestLoveReward.run();assert ApplicationHook.confirms==4;
  DailyTask.manual=false;UserIdMap.uid="B";ForestLoveReward.run();assert ApplicationHook.confirms==5;
  reset();ApplicationHook.failedConfirm=true;ForestLoveReward.run();ApplicationHook.failedConfirm=false;RuntimeInfo.getInstance().put("Forest::loveRewardConfirmProbeTime",1L);ForestLoveReward.run();assert ApplicationHook.confirms==2&&has("流程完成"):"failed confirmation must retry automatically";
  reset();ApplicationHook.skippedConfirm=true;ForestLoveReward.run();assert ApplicationHook.confirms==0&&RuntimeInfo.getInstance().getString("Forest::loveRewardConfirmAttempt").isEmpty();ApplicationHook.skippedConfirm=false;RuntimeInfo.getInstance().put("Forest::loveRewardConfirmProbeTime",1L);ForestLoveReward.run();assert ApplicationHook.confirms==1;
  reset();RuntimeInfo.getInstance().put("Forest::loveRewardConfirmAttempt",String.format(Locale.ROOT,"%1$tY%1$tm%1$td",MyUtils.getInstance())+"|team|LoveEnergyShield");ForestLoveReward.run();assert ApplicationHook.confirms==1&&has("流程完成"):"legacy attempt marker must allow a bounded retry";
  reset();ApplicationHook.crossDayAfterConfirm=true;ForestLoveReward.run();assert RuntimeInfo.getInstance().getString("Forest::loveRewardFlowCompleteDay").isEmpty():"cross-day readback must not mark either day completed";
  reset();ApplicationHook.unknownWidget=true;ForestLoveReward.run();assert RuntimeInfo.getInstance().getString("Forest::loveRewardFlowCompleteDay").isEmpty():"unknown non-reward state is not completion proof";
  reset();ForestLoveReward.run();MyUtils.clock+=86400000;ApplicationHook.cacheCleared=false;RuntimeInfo.getInstance().put("Forest::loveRewardConfirmProbeTime",1L);ForestLoveReward.run();assert ApplicationHook.confirms==2:"GMT+8 next-day reward must not be blocked by yesterday's completion";
  reset();ForestLoveReward.run();UserIdMap.uid="B";ApplicationHook.cacheCleared=false;ForestLoveReward.run();assert ApplicationHook.confirms==2:"one account's completion must not suppress another account";
  reset();ApplicationHook.failCompletionSave=true;ForestLoveReward.run();assert RuntimeInfo.getInstance().getString("Forest::loveRewardFlowCompleteDay").isEmpty()&&has("完成记录未保存"):"failed persistence must not retain completion in memory";
  reset();ApplicationHook.grant=true;ForestLoveReward.run();assert has("[结果]：观察")&&ApplicationHook.reads==2;
  reset();ApplicationHook.delayed=true;ForestLoveReward.run();assert has("[结果]：观察")&&ApplicationHook.reads==3;
  reset();ApplicationHook.failBefore=true;ApplicationHook.grant=true;ForestLoveReward.run();assert has("无法判断到账")&&!has("[结果]：观察");
  reset();ApplicationHook.failAfter=true;ForestLoveReward.run();assert has("无法判断到账");
  reset();ApplicationHook.failedHome=true;ForestLoveReward.run();assert ApplicationHook.homes==1&&ApplicationHook.subscribes==0&&ApplicationHook.reads==3&&!has("[结果]：观察");
  reset();ApplicationHook.noSubscription=true;ForestLoveReward.run();assert ApplicationHook.homes==2&&ApplicationHook.subscribes==0&&has("没有subscribeId");
  reset();ApplicationHook.duplicate=true;ForestLoveReward.run();assert has("无法判断到账");
  reset();ApplicationHook.fraction=true;ForestLoveReward.run();assert has("无法判断到账");
  reset();ApplicationHook.invalidType=true;ApplicationHook.grant=true;ForestLoveReward.run();assert has("无法判断到账")&&!has("[结果]：观察");
  reset();ApplicationHook.invalidSubscription=true;ForestLoveReward.run();assert ApplicationHook.subscribes==0&&has("字段类型无效");
  reset();ApplicationHook.homeCode="LoveQiXiTu";ApplicationHook.grantAnimal=true;ForestLoveReward.run();assert ApplicationHook.bag==2&&has("[结果]：观察")&&ApplicationHook.confirms==1;
  reset();ApplicationHook.homeCode="LoveQiXiTu";ApplicationHook.renewAnimal=true;ForestLoveReward.run();assert ApplicationHook.animalCount==1&&has("[结果]：观察");
  reset();ApplicationHook.missingExpiry=true;ForestLoveReward.run();assert !has("[结果]：观察");
  reset();ApplicationHook.mixed=true;ForestLoveReward.run();assert ApplicationHook.confirms==0&&has("保留缓存");
  reset();ApplicationHook.badTeam=true;ForestLoveReward.run();assert ApplicationHook.confirms==0;
  reset();ApplicationHook.crossDay=true;ForestLoveReward.run();assert ApplicationHook.confirms==0&&has("业务日期已变化");
  reset();RuntimeInfo.writable=false;ForestLoveReward.run();assert ApplicationHook.confirms==0&&has("尝试记录未保存");
  reset();ApplicationHook.failedConfirm=true;ForestLoveReward.run();assert ApplicationHook.confirms==1&&!has("领奖提示已消除")&&has("deleteRewardCache确认回执")&&has("success=false");
  reset();DailyTask.manual=true;ForestLoveReward.run();assert has("不能确认本轮新发奖")&&has("流程完成");
  reset();ApplicationHook.badAnimal=true;ForestLoveReward.run();assert has("无法判断到账");
  reset();ApplicationHook.moreAnimals=true;ForestLoveReward.run();assert has("完整性未知")&&!has("[结果]：观察");
  reset();ApplicationHook.widget="callWater";ForestLoveReward.run();assert ApplicationHook.homes==0&&ApplicationHook.reads==0&&ApplicationHook.animalReads==0&&has("未处于去领奖状态")&&Log.lines.size()==2;
  reset();ApplicationHook.offline=true;ForestLoveReward.run();assert ApplicationHook.calls.isEmpty();
  reset();ApplicationHook.switched=true;try{ForestLoveReward.run();throw new AssertionError("switch swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.subscribes==0&&ApplicationHook.reads==1;
  reset();ApplicationHook.cancelled=true;try{ForestLoveReward.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
  reset();ApplicationHook.throwHome=true;ForestLoveReward.run();assert has("异常：IllegalArgumentException")&&!has("secret-token");
  reset();ApplicationHook.homeCode="Love-secret?token=sensitive";ForestLoveReward.run();assert !has("sensitive");
  System.out.println("PASS production official daily confirmation/state readback, rabbit inventory/expiry, no false receipt, mixed-team/date/persistence/repeat guards, headers/GMT+8/account/cancel/redaction");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='sesame-love-reward-') as tmp:
    tmp = Path(tmp)
    for name in ('entity/RpcEntity.java', 'util/TaskCancelledException.java', 'model/task/antForest/ForestLoveReward.java'):
        files[name] = (SOURCE / name).read_text(encoding='utf-8')
    for name, code in files.items():
        f = tmp / 'io/github/aw1y2z/sesame' / name
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(code, encoding='utf-8')
    cp = os.pathsep.join((str(JSON), str(LOMBOK)))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(tmp)] + [str(p) for p in tmp.rglob('*.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', str(tmp) + os.pathsep + str(JSON), 'io.github.aw1y2z.sesame.model.task.antForest.LoveRewardProbeCheck'], check=True)
