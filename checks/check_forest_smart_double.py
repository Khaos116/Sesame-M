"""Replay future-waiting-count double-card decisions and existing child scheduling."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
forest='model/task/antForest/AntForestV2.java';rpc='model/task/antForest/AntForestRpcCall.java'
assert 'private void useSmartDoubleCard(' in (SOURCE/forest).read_text(encoding='utf-8'),'Missing smart double waiting count'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestSmartDoubleCheck {
 static String TAG="check";String selfId="self",smartDoubleCheckId;Object usePropLockObj=new Object();boolean hasErrorWait,quiet;int advanceTimeInt;
 static class Bool {boolean n;boolean getValue(){return n;}}static class Num {int n;int getValue(){return n;}}static class Select {Set<String> n=new HashSet<>();Set<String> getValue(){return n;}}
 Bool smartDoubleCard=new Bool(),collectEnergy=new Bool();Num smartDoubleCardThreshold=new Num(),smartDoubleCardDailyLimit=new Num();Select continuousUseCardOptions=new Select();Set<String> dontCollectMap=new HashSet<>();Set<String> blocked=new HashSet<>();
 static class UserIdMap {static String getCurrentUid(){return "self";}}
 Bool smartDoublePermanent=new Bool(),smartDouble31Days=new Bool(),forestPropRefill=new Bool();int advanced;
 void useAdvancedSmartDouble(List<BubbleTimerTask> targets){advanced++;assert targets.size()>=smartDoubleCardThreshold.getValue();}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static void clearFlag(String s){flags.remove(s);}static int getIntFlagToday(String s){return counts.getOrDefault(s,0);}static void setIntFlagToday(String s,int n){counts.put(s,n);}}
 static class Log {static int ok;static void forest(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ChildModelTask {String id;long exec;boolean cancelled;Runnable work;ChildModelTask(String id,long exec){this(id,null,null,exec);}ChildModelTask(String id,String group,Runnable work,Long exec){this.id=id;this.exec=exec;this.work=work;}String getId(){return id;}Long getExecTime(){return exec;}Boolean getIsCancel(){return cancelled;}}
 static class AntForestV2 {static String getBubbleTimerTid(String u,long id){return "BT|"+u+"|"+id;}}
 class BubbleTimerTask extends ChildModelTask {String userId,userName;long bubbleId,produceTime;boolean canDouble;@@CTOR@@}
 Map<String,ChildModelTask> childTaskMap=new HashMap<>();@@SNAPSHOT@@
 boolean hasChildTask(String id){return childTaskMap.containsKey(id);}ChildModelTask getChildTask(String id){return childTaskMap.get(id);}void removeChildTask(String id){childTaskMap.remove(id);}boolean addChildTask(ChildModelTask c){childTaskMap.put(c.id,c);return true;}
 boolean allowCollectByWhiteList(String u){return !blocked.contains(u);}boolean isRevivedSelfQuietTime(){return quiet;}
 static long now=System.currentTimeMillis();static int uses,queries,bagCase;static long end;static boolean wrongUid,drift,consumeReject;static boolean eligible=true,stock=true,effect=true,fail,unknown,duplicate,shield,cancelAfter;static String type="LIMIT_TIME_ENERGY_DOUBLE_CLICK";
 JSONObject queryFriendHome(String uid){queries++;JSONArray b=new JSONArray();for(int i=0;i<2;i++)b.put(new JSONObject().put("id",i+1).put("fullEnergy",20).put("produceTime",now+120000+i*1000).put("collectStatus","WAITING").put("canBeRobbedTwice",eligible));if(duplicate)b.put(b.optJSONObject(0));JSONObject h=new JSONObject().put("success",true).put("bubbles",b).put("userInfo",new JSONObject().put("userId",uid));if(unknown)h.remove("bubbles");if(wrongUid)h.optJSONObject("userInfo").put("userId","other");if(drift)b.optJSONObject(0).put("produceTime",now+130000);return h;}
 boolean hasActiveProp(JSONObject home,String group){return shield;}
 static JSONArray bag(){JSONObject row=new JSONObject().put("propGroup","doubleClick").put("propType",type).put("holdsNum",uses>0&&stock?0:1).put("recentExpireTime",now+600000).put("propIdList",uses>0&&stock?new JSONArray():new JSONArray().put("card\"\\"));if(bagCase==1)row.put("holdsNum",1.5);if(bagCase==2)row.put("propIdList",new JSONArray().put("x").put("x")).put("holdsNum",2);if(bagCase==3)row.put("propIdList",new JSONArray().put(3));if(bagCase==4)row.put("recentExpireTime",now+5000);return new JSONArray().put(row);}
 static class ApplicationHook {static String requestString(String name,String raw){JSONObject result=new JSONObject().put("resultCode",fail?"FAIL":"SUCCESS");
  if(name.endsWith("queryMiscInfo")){JSONArray a=new JSONArray();if(end>0)a.put(new JSONObject().put("propGroup","doubleClick").put("endTime",end));result.put("combineHandlerVOMap",new JSONObject().put("usingProp",new JSONObject().put("userPropVOS",a)));}
  else if(name.endsWith("queryPropList"))result.put("forestPropVOList",bag());
  else if(name.endsWith("consumeProp")){JSONObject p=new JSONArray(raw).optJSONObject(0);assert p.optString("propGroup").equals("doubleClick")&&p.optString("propId").equals("card\"\\")&&!p.optBoolean("secondConfirm");uses++;if(consumeReject)result.put("resultCode","FAIL");if(effect)end=System.currentTimeMillis()+300000;if(cancelAfter)TimeUtil.cancel=true;}
  else throw new AssertionError(name);return result.toString();}}
 static class ForestExpiringProps { static boolean hasUnconfirmed(String group){ return false; } }
 static class AntForestRpcCall {static String VERSION="check";static String getRandomString(int n){return "nonce";}static String queryMiscInfo(){return ApplicationHook.requestString("queryMiscInfo","[{}]");}static String queryPropList(boolean ignored){return ApplicationHook.requestString("queryPropList","[{}]");}@@RPC@@}
 @@METHODS@@
 void waiting(String uid,int index,long at,boolean doubled){addChildTask(new BubbleTimerTask(uid,index,at,"friend",doubled));}
 static void reset(ForestSmartDoubleCheck f){f.smartDoubleCard.n=f.collectEnergy.n=true;f.smartDoubleCardThreshold.n=2;f.smartDoubleCardDailyLimit.n=6;f.continuousUseCardOptions.n=Set.of("doubleClick");f.hasErrorWait=f.quiet=false;f.childTaskMap.clear();f.smartDoubleCheckId=null;f.dontCollectMap.clear();f.blocked.clear();Status.flags.clear();Status.counts.clear();TimeUtil.cancel=false;now=System.currentTimeMillis();end=0;uses=queries=bagCase=Log.ok=0;wrongUid=drift=consumeReject=false;eligible=stock=effect=true;fail=unknown=duplicate=shield=cancelAfter=false;type="LIMIT_TIME_ENERGY_DOUBLE_CLICK";f.waiting("friend",1,now+120000,true);f.waiting("friend",2,now+121000,true);}
 public static void main(String[] args){ForestSmartDoubleCheck f=new ForestSmartDoubleCheck();
  reset(f);f.useSmartDoubleCard();f.useSmartDoubleCard();assert uses==1&&Log.ok==1&&Status.getIntFlagToday("forest::smartDoubleAttempts")==1;
  reset(f);f.smartDoubleCard.n=false;f.useSmartDoubleCard();assert uses==0&&queries==0;
  reset(f);f.continuousUseCardOptions.n=Set.of();f.useSmartDoubleCard();assert uses==0;
  reset(f);f.smartDoubleCardThreshold.n=3;f.useSmartDoubleCard();assert uses==0&&queries==0;
  reset(f);f.blocked.add("friend");f.useSmartDoubleCard();assert uses==0;
  reset(f);f.dontCollectMap.add("friend");f.useSmartDoubleCard();assert uses==0;
  reset(f);f.quiet=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);end=now+600000;f.useSmartDoubleCard();assert uses==0;
  reset(f);eligible=false;f.useSmartDoubleCard();assert uses==0;
  reset(f);duplicate=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);unknown=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);shield=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);type="ENERGY_DOUBLE_CLICK_31DAYS";f.useSmartDoubleCard();assert uses==0;
  reset(f);effect=false;f.useSmartDoubleCard();f.useSmartDoubleCard();assert uses==1&&Log.ok==0&&Status.hasFlagToday("forest::smartDoubleUnconfirmed");
  reset(f);wrongUid=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);drift=true;f.useSmartDoubleCard();assert uses==0;
  for(int i=1;i<=4;i++){reset(f);bagCase=i;f.useSmartDoubleCard();assert uses==0;}
  reset(f);consumeReject=true;f.useSmartDoubleCard();f.useSmartDoubleCard();assert uses==1&&Log.ok==0&&Status.hasFlagToday("forest::smartDoubleUnconfirmed");
  reset(f);stock=false;f.useSmartDoubleCard();f.useSmartDoubleCard();assert uses==1&&Log.ok==0;
  reset(f);fail=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);Status.setIntFlagToday("forest::smartDoubleAttempts",6);f.useSmartDoubleCard();assert uses==0;
  reset(f);((BubbleTimerTask)f.childTaskMap.get("BT|friend|1")).cancelled=true;f.useSmartDoubleCard();assert uses==0;
  reset(f);f.childTaskMap.clear();f.waiting("self",1,now+120000,true);f.waiting("friend",2,now+400000,true);f.waiting("friend",3,now+120000,false);assert f.smartDoubleTargets(System.currentTimeMillis()).isEmpty();
  reset(f);f.scheduleSmartDoubleCheck();String first=f.smartDoubleCheckId;assert first!=null&&f.childTaskMap.get(first).exec>System.currentTimeMillis();f.scheduleSmartDoubleCheck();assert first.equals(f.smartDoubleCheckId):"coalesce queued check";f.smartDoubleCard.n=false;f.scheduleSmartDoubleCheck();assert f.smartDoubleCheckId==null&&!f.childTaskMap.containsKey(first);
  reset(f);f.scheduleSmartDoubleCheck();first=f.smartDoubleCheckId;f.childTaskMap.get(first).work.run();assert uses==1&&f.smartDoubleCheckId!=null&&!first.equals(f.smartDoubleCheckId):"callback must schedule a distinct ID before old cleanup";
  reset(f);cancelAfter=true;try{f.useSmartDoubleCard();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancel=false;f.useSmartDoubleCard();assert uses==1&&Status.hasFlagToday("forest::smartDoubleUnconfirmed");
  reset(f);TimeUtil.cancel=true;try{f.useSmartDoubleCard();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert uses==0;
  System.out.println("PASS future double waiting count, actual eligibility/identity/white and black list, no self/expired/unknown/protected/31-day stock, active guard/stock+effect/day hold, existing child coalescing/cancel and escaping");
  reset(f);f.smartDoublePermanent.n=true;f.useSmartDoubleCard();assert f.advanced==1&&uses==0;f.smartDoublePermanent.n=false;
 }
}
'''
code=code.replace('@@CTOR@@',method(forest,'BubbleTimerTask(String ui, long bi, long pt, String un, boolean doubled)'))
code=code.replace('@@SNAPSHOT@@',method('data/task/ModelTask.java','protected List<ChildModelTask> getChildTaskSnapshot('))
code=code.replace('@@METHODS@@','\n'.join(method(forest,s) for s in ('private boolean smartDoubleEnabled(','private List<BubbleTimerTask> smartDoubleTargets(','private List<BubbleTimerTask> confirmSmartDoubleTargets(','private long querySmartDoubleEnd(','private JSONArray querySmartDoubleInventory(','private JSONObject chooseSmartDoubleCard(','private static long smartDoubleStock(','private static boolean smartDoubleHasId(','private void useSmartDoubleCard(','private void scheduleSmartDoubleCheck(','static JSONObject forestSignPayload(','static long forestFeatureLong(')))
code=code.replace('@@RPC@@',method(rpc,'public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm)'))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
src=(SOURCE/forest).read_text(encoding='utf-8')
assert 'new BooleanModelField("smartDoubleCard",' in src and ', false)' in next(line for line in src.splitlines() if 'new BooleanModelField("smartDoubleCard",' in line)
assert 'useSmartDoubleCard();' in method(forest,'private void continuousUseAndExchangeCard(')
assert src.count('new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice")))')==3 and 'Boolean.TRUE.equals(canbubble.opt("canBeRobbedTwice"))' in src
with tempfile.TemporaryDirectory(prefix='sesame-smart-double-') as tmp:
 f=Path(tmp)/'ForestSmartDoubleCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestSmartDoubleCheck'],check=True)
