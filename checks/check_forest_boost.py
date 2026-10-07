"""Replay actual boost-card flow: waiting own bubbles, stock/effect readback and timer repair."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java';rpc='model/task/antForest/AntForestRpcCall.java'
src=(SOURCE/forest).read_text(encoding='utf-8')
assert 'new ChoiceModelField("bubbleBoostCard"' in src,'Missing boost strategy'
code=r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
class ForestBoostCheck {
 final Object usePropLockObj=new Object();static class Text {String n="-1";String getValue(){return n;}}Text bubbleBoostTime=new Text();
 static String TAG="check",VERSION="version";String selfId="self";
 static class Num {int n;Num(int v){n=v;}int getValue(){return n;}}static class Bool {boolean n=true;boolean getValue(){return n;}}
 Num bubbleBoostCard=new Num(1),bubbleBoostDailyLimit=new Num(1),CollectSelfEnergyType=new Num(0),CollectSelfEnergyThreshold=new Num(10);Bool collectEnergy=new Bool(),onlyCollectRevivedSelfEnergy=new Bool();{onlyCollectRevivedSelfEnergy.n=false;}
 static class UsePropType {static int CLOSE=0,ALL=1,ONLY_LIMIT_TIME=2;}static class CollectSelfType {static int ALL=0,OVER_THRESHOLD=1,BELOW_THRESHOLD=2;}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static int getIntFlagToday(String s){return counts.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){counts.put(s,n);}}
 static class Log {static int ok;static void forest(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ApplicationHook {static boolean waiting=true,effect=true,stock=true,fail=false,unknown=false;static int uses;static long now=System.currentTimeMillis();static String chosen;static String type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";
  static JSONObject home(){JSONObject bubble=new JSONObject().put("id",1).put("fullEnergy",20).put("remainEnergy",20).put("collectStatus",waiting?"WAITING":"AVAILABLE").put("produceTime",now+3600000-(uses>0&&effect?1800000:0));if(unknown)bubble.remove("fullEnergy");return new JSONObject().put("success",true).put("now",now).put("bubbles",new JSONArray().put(bubble));}
  static JSONObject bag(){JSONObject p=new JSONObject().put("propGroup","boost").put("propType",type).put("holdsNum",uses>0&&stock?0:1).put("propIdList",uses>0&&stock?new JSONArray():new JSONArray().put("card\"\\")).put("recentExpireTime",now+10000);return new JSONObject().put("success",true).put("forestPropVOList",new JSONArray().put(p));}
  static String requestString(String method,String args){if(method.endsWith("queryPropList"))return bag().toString();if(method.endsWith("consumeProp")){JSONObject p=new JSONArray(args).optJSONObject(0);assert p.optString("propGroup").equals("boost");chosen=p.optString("propId");assert !p.optBoolean("secondConfirm");uses++;return new JSONObject().put("success",!fail).toString();}throw new AssertionError(method);}
 }
 static class ForestExpiringProps { static boolean hasUnconfirmed(String group){ return false; } }
 static class AntForestRpcCall {static String getRandomString(int n){return "nonce";}static String queryPropList(boolean give){return ApplicationHook.bag().toString();}@@RPC@@}
 int homeQueries;boolean matureDuringQueries;
 JSONArray refillForestProp(String group){assert group.equals("boost");return null;}
 JSONObject querySelfHome(){if(++homeQueries==2&&matureDuringQueries)ApplicationHook.waiting=false;return ApplicationHook.home();}List<String> removed=new ArrayList<>();int collected;
 void removeChildTask(String s){removed.add(s);}static String getBubbleTimerTid(String u,long id){return "BT|"+u+"|"+id;}
 JSONObject collectUserEnergy(String uid,JSONObject h,String type){assert uid.equals("self");collected++;return h;}
 @@METHODS@@
 static void reset(ForestBoostCheck f){f.bubbleBoostCard.n=1;f.bubbleBoostDailyLimit.n=1;f.collectEnergy.n=true;f.CollectSelfEnergyType.n=0;f.collected=Log.ok=0;f.removed.clear();Status.flags.clear();Status.counts.clear();TimeUtil.cancel=false;ApplicationHook.waiting=ApplicationHook.effect=ApplicationHook.stock=true;ApplicationHook.fail=ApplicationHook.unknown=false;ApplicationHook.uses=0;ApplicationHook.type="LIMIT_TIME_ENERGY_BUBBLE_BOOST";}
 public static void main(String[] args){ForestBoostCheck f=new ForestBoostCheck();
  reset(f);f.onlyCollectRevivedSelfEnergy.n=true;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;f.onlyCollectRevivedSelfEnergy.n=false;
  reset(f);f.bubbleBoostCard.n=0;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);ApplicationHook.waiting=false;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);assert f.useBubbleBoostCard()!=null&&ApplicationHook.uses==1&&Log.ok==1&&f.collected==1&&f.removed.equals(Arrays.asList("BT|self|1"));assert ApplicationHook.chosen.equals("card\"\\");f.useBubbleBoostCard();assert ApplicationHook.uses==1;
  reset(f);ApplicationHook.effect=false;assert f.useBubbleBoostCard()==null&&Log.ok==0&&f.removed.isEmpty();f.useBubbleBoostCard();assert ApplicationHook.uses==1;
  reset(f);ApplicationHook.stock=false;assert f.useBubbleBoostCard()==null&&Log.ok==0;
  reset(f);ApplicationHook.fail=true;assert f.useBubbleBoostCard()==null&&Log.ok==0;
  reset(f);ApplicationHook.unknown=true;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);f.CollectSelfEnergyType.n=2;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);f.bubbleBoostCard.n=2;ApplicationHook.type="BUBBLE_BOOST";assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);f.bubbleBoostDailyLimit.n=0;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);f.homeQueries=0;f.matureDuringQueries=true;assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0:"became mature while querying inventory";f.matureDuringQueries=false;
  reset(f);TimeUtil.cancel=true;try{f.useBubbleBoostCard();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.uses==0;
  reset(f);f.bubbleBoostTime.n="-1,!0000-2400";assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);f.bubbleBoostTime.n="invalid";assert f.useBubbleBoostCard()==null&&ApplicationHook.uses==0;
  reset(f);java.util.Calendar g=io.github.aw1y2z.sesame.util.MyUtils.getInstance();g.setTimeInMillis(System.currentTimeMillis());f.bubbleBoostTime.n=String.format(java.util.Locale.ROOT,"%02d%02d",g.get(java.util.Calendar.HOUR_OF_DAY),g.get(java.util.Calendar.MINUTE));f.bubbleBoostDailyLimit.n=10;assert f.useBubbleBoostCard()!=null&&ApplicationHook.uses==1;f.useBubbleBoostCard();assert ApplicationHook.uses==1:"one attempt per point/day";
  System.out.println("PASS off/no future/unknown/threshold protection, stock+accelerated effect readback, timer repair, limited-only/daily guard, no refill, cancel and escaped consume RPC");
 }
}
'''
signatures=('static long forestFeatureLong(','static JSONObject forestSignPayload(','private Map<Long, Long> waitingBoostBubbles(','private static JSONObject selectBoostProp(','private static long boostStockForId(','private JSONObject useBubbleBoostCard(','private JSONObject useBubbleBoostCardLocked(','private boolean boostTimeAllowed(','private String boostPointFlag(')
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in signatures)).replace('@@RPC@@',method(rpc,'public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm)'))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-boost-') as tmp:
 code='package io.github.aw1y2z.sesame.model.task.antForest;\n'+code
 helper=Path(tmp)/'MyUtils.java';helper.write_text('package io.github.aw1y2z.sesame.util; import java.util.*; public class MyUtils {public static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}',encoding='utf-8')
 f=Path(tmp)/'ForestBoostCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(helper),str(SOURCE/'model/task/antForest/ForestSchedule.java'),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'io.github.aw1y2z.sesame.model.task.antForest.ForestBoostCheck'],check=True)
