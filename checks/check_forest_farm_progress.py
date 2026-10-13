"""Replay production home-reward loops, legacy patrol, farm boxes and whack cancellation."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE

forest = 'model/task/antForest/AntForestV2.java'
farm = 'model/task/antFarm/AntFarm.java'
whack = 'model/task/antForest/WhackMole.java'
run = method(forest, 'public void run()')
home_loop = run[run.index('                boolean hasMore = false;'):run.index('                //JSONArray usingUserProps')]
code = r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestFarmProgressCheck {
 static final String TAG="test",SOURCE="test",EXEC_FLAG="whack",FLAG_GAME_DRAW_TASK_SKIP="farm";
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}
 static class Log {static List<String> logs=new ArrayList<>();static void record(String s){logs.add(s);}static void forest(String s){}static void farm(String s){}static void i(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){if(e instanceof TaskCancelledException)throw (TaskCancelledException)e;throw new AssertionError(e);}static void printStackTrace(String s,Throwable t){if(t instanceof TaskCancelledException)throw (TaskCancelledException)t;throw new AssertionError(t);}}
 static class TimeUtil {static void sleep(long n){}}
 static class MessageUtil {static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean checkResultCodeString(String t,JSONObject j){return checkResultCode(t,j);}}
 static class Toast {static void show(String s){}}static class UserIdMap {static String getShowName(String s){return s;}}
 static class Statistics {enum DataType {COLLECTED,WATEREDCOUNT}static void addData(DataType t,int v){}static void save(){}}
 static class Status {static Set<String> flags=new HashSet<>();static void wateredFriendToday(String s){}static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class Bool {boolean n;Bool(boolean x){n=x;}boolean getValue(){return n;}}
 Bool collectWateringBubble=new Bool(true),onlyCollectRevivedSelfEnergy=new Bool(false),collectEnergy=new Bool(true),collectProp=new Bool(true);
 String selfId="self";int totalCollected;
 static JSONObject home;static int homeQueries,waterCalls,propCalls;static boolean reject,homeNull;
 JSONObject querySelfHome(){if(++homeQueries>4)throw new AssertionError("home loop repeated");return homeNull?null:home;}
 void homeRewards(JSONObject selfHomeObject){@@HOME@@}
 static JSONObject page(int start,String type){JSONArray b=new JSONArray();for(int i=start;i<start+20;i++)b.put(new JSONObject().put("id",i).put("bizType",type));return new JSONObject().put("wateringBubbles",b);}
 static JSONObject props(){JSONArray p=new JSONArray();for(int i=0;i<20;i++)p.put(new JSONObject().put("giveConfigId","c").put("giveId","p"+i));return new JSONObject().put("givenProps",p);}
 static int patrolQueries,switchCalls,goCalls,exchanges,keepCalls,patrolMode;
 static Deque<String> eventReplies=new ArrayDeque<>();
 static String patrolEvent(int node,String type,boolean reacted,boolean going){return new JSONObject().put("resultCode","SUCCESS").put("currentStatus",going?"GOING":"STANDING")
  .put("userPatrol",new JSONObject().put("currentNode",node)).put("events",new JSONArray().put(new JSONObject().put("nodeIndex",node).put("eventType",reacted?"reward":"material").put("reacted",reacted)
  .put("materialInfo",new JSONObject().put("materialType",type).put("materialId",node==30?12400068:400)))).toString();}
 static String ok(){return new JSONObject().put("resultCode","SUCCESS").toString();}
 static class AntSports {void syncStepNow(){}}static <T>T getModel(Class<T> t){return null;}
 static class DailyTask {static boolean skipFlag(String f,String n){return Status.flags.contains(f);}}
 void logLegacyPatrolState(String k,String s){}
 static class AntForestRpcCall {
  static String collectEnergy(String b,String u,long id){waterCalls++;return new JSONObject().put("resultCode",reject?"FAIL":"SUCCESS").put("bubbles",new JSONArray().put(new JSONObject().put("collectedEnergy",1))).toString();}
  static String collectRebornEnergy(){waterCalls++;return new JSONObject().put("resultCode",reject?"FAIL":"SUCCESS").put("energy",1).toString();}
  static String collectProp(String c,String id){propCalls++;return new JSONObject().put("success",!reject).toString();}
  static String queryUserPatrol(){if(++patrolQueries>4)throw new AssertionError("patrol repeated");return new JSONObject().put("resultCode","SUCCESS").put("userPatrol",new JSONObject().put("currentStatus","STANDING").put("patrolId",1).put("currentNode",0).put("chance",new JSONObject().put("leftChance",patrolMode==4?0:1).put("leftStep",3000).put("usedStep",0))).toString();}
  static String queryMyPatrolRecord(){JSONObject j=new JSONObject().put("resultCode","SUCCESS");if(patrolMode<=1)j.put("canSwitch",true).put("records",new JSONArray().put(new JSONObject().put("userPatrol",new JSONObject().put("unreachedNodeCount",3).put("mode","silent")).put("patrolConfig",new JSONObject().put("patrolId","next"))));return j.toString();}
  static String switchUserPatrol(String id){switchCalls++;return patrolMode==0?"{}":ok();}
  static String patrolGo(int n,int p){goCalls++;return patrolMode==2?"{}":ok();}
  static String exchangePatrolChance(int n){exchanges++;return ok();}
  static String patrolKeepGoing(int n,int p,String type){if(++keepCalls>3)throw new AssertionError("patrol event repeated");if(!eventReplies.isEmpty())return eventReplies.removeFirst();return new JSONObject().put("resultCode","SUCCESS").put("currentStatus","GOING").put("userPatrol",new JSONObject().put("currentNode",1)).put("events",new JSONArray().put(new JSONObject().put("materialInfo",new JSONObject().put("materialType","image")))).toString();}
  static String oldstartWhackMole(String s){if(cancelStage==0)throw new TaskCancelledException();return new JSONObject().put("success",true).put("token","private").put("moleInfo",new JSONArray().put(new JSONObject().put("id",1).put("bubbleId",2))).toString();}
  static String oldwhackMole(long id,String t,String s){if(cancelStage==1)throw new TaskCancelledException();if(cancelStage==3)throw new IllegalStateException("secret-token");return new JSONObject().put("success",true).toString();}
  static String oldsettlementWhackMole(String t,List<String> ids,String s){settles++;throw new TaskCancelledException();}
 }
 @@PATROL@@
 static int initialQuota,used,draws,reported,gameQueries,gameMode;static boolean failBefore,failAfter;
 static JSONObject rights(int quota,int count){return new JSONObject().put("quotaCanUse",quota).put("usedQuota",count).put("quotaLimit",5);}
 static class AntFarmRpcCall {
  static String queryGameList(){gameQueries++;if(gameQueries>4)throw new AssertionError("game loop");if(gameQueries==2&&failBefore||gameQueries==3&&failAfter)return "{}";return new JSONObject().put("success",true).put("gameCenterDrawRights",rights(gameQueries==1?initialQuota:0,used)).toString();}
  static String drawGameCenterAward(int n){draws++;used++;return new JSONObject().put("success",true).put("gameCenterDrawRights",rights(gameMode==1?initialQuota:initialQuota-draws,used)).toString();}
 }
 static class GameTask {static class Farm_ddply {static int reportSync(String s,int n){reported+=n;return n;}}}
 @@FARM@@
 enum Mode {COMPATIBLE,AGGRESSIVE}static boolean isRunning;static int moleCount=15,cancelStage,settles;
 static void runAggressiveMode(){}
 @@WHACK@@
 static void reset(){homeQueries=waterCalls=propCalls=0;reject=homeNull=false;Status.flags.clear();patrolQueries=switchCalls=goCalls=exchanges=keepCalls=0;initialQuota=used=draws=reported=gameQueries=gameMode=0;failBefore=failAfter=false;settles=0;Log.logs.clear();}
 public static void main(String[] args){ForestFarmProgressCheck f=new ForestFarmProgressCheck();
  reset();f.onlyCollectRevivedSelfEnergy.n=true;home=page(1,"jiaoshui");f.homeRewards(home);assert homeQueries==0&&waterCalls==0;
  reset();f.onlyCollectRevivedSelfEnergy.n=false;home=page(1,"jiaoshui");reject=true;f.homeRewards(home);assert waterCalls==20&&homeQueries==0;
  reset();home=page(1,"jiaoshui");f.homeRewards(home);assert waterCalls==20&&homeQueries==1:"successful but unchanged page must stop";
  reset();home=page(21,"jiaoshui");f.homeRewards(page(1,"jiaoshui"));assert waterCalls==40&&homeQueries==2:"next actual page must remain collectible";
  reset();home=props();f.homeRewards(home);assert propCalls==20&&homeQueries==1;
  reset();home=props();reject=true;f.homeRewards(home);assert propCalls==20&&homeQueries==0;
  reset();home=page(1,"jiaoshui");homeNull=true;f.homeRewards(home);assert homeQueries==1;
  for(int mode=0;mode<=4;mode++){reset();patrolMode=mode;f.queryUserPatrol();assert Status.flags.isEmpty();assert patrolQueries==(mode==0||mode==2?1:2);assert switchCalls==(mode<=1?1:0);assert goCalls==(mode==2||mode==3?1:0);assert exchanges==(mode==4?1:0);}
  reset();f.patrolKeepGoing(null,0,1);assert keepCalls==2&&Status.flags.isEmpty():"unchanged node/event must stop";
  reset();eventReplies.add(patrolEvent(30,"image",true,true));eventReplies.add(patrolEvent(32,"story",false,true));eventReplies.add(patrolEvent(32,"story",true,false));
  f.patrolKeepGoing(patrolEvent(30,"image",false,true),29,2);
  assert keepCalls==3&&eventReplies.isEmpty():"captured material-to-reward transition at same node/type must continue to patrol completion";
  reset();initialQuota=5;f.drawGameCenterAward();assert draws==5&&reported==0&&Status.flags.isEmpty():"do not report completed quota";
  reset();used=5;f.drawGameCenterAward();assert gameQueries==1&&draws==0&&reported==0:"exhausted initial quota needs no refresh";
  reset();Status.flags.add(FLAG_GAME_DRAW_TASK_SKIP);initialQuota=2;f.drawGameCenterAward();assert draws==2&&gameQueries==1&&reported==0:"skip task refresh but still open available boxes";
  reset();initialQuota=2;f.drawGameCenterAward();assert draws==2&&reported==3;
  reset();failBefore=true;f.drawGameCenterAward();assert reported==0&&Status.flags.isEmpty();
  reset();failAfter=true;f.drawGameCenterAward();assert reported==5&&Status.flags.isEmpty():"unknown query cannot set ineffective flag";
  reset();initialQuota=2;gameMode=1;f.drawGameCenterAward();assert draws==1&&reported==0:"unchanged draw quota must stop";
  for(int stage=0;stage<2;stage++){reset();cancelStage=stage;try{startSuspend(Mode.COMPATIBLE);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert !isRunning&&Status.flags.isEmpty()&&settles==0;}
  reset();cancelStage=2;Thread.currentThread().interrupt();try{startSuspend(Mode.COMPATIBLE);throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}assert Thread.interrupted()&&settles==0&&Status.flags.isEmpty()&&!isRunning;
  System.out.println("PASS forest no-progress/filter/rejection/pages/null, patrol failures and unchanged states, current farm quota, cancellation");
 }
}
'''
code = code.replace('@@HOME@@', home_loop).replace('@@PATROL@@', method(forest, 'private void queryUserPatrol(') + method(forest, 'private void patrolKeepGoing('))
code = code.replace('@@FARM@@', method(farm, 'private void drawGameCenterAward('))
code = code.replace('@@WHACK@@', method(whack, 'public static void startSuspend(') + method(whack, 'private static void runCompatibleMode('))
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-forest-farm-progress-') as tmp:
    f = Path(tmp)/'ForestFarmProgressCheck.java'
    f.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(f), str(SOURCE/'util/TaskCancelledException.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp+os.pathsep+str(jar), 'ForestFarmProgressCheck'], check=True)
