"""Replay production home-reward loops, legacy patrol, farm boxes and whack cancellation."""
from pathlib import Path
import os, re, subprocess, sys, tempfile
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
 static final String TAG="test",SOURCE="test",EXEC_FLAG="whack";
 @@LIMITS@@
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}
 static class Log {static List<String> logs=new ArrayList<>();static void record(String s){logs.add(s);}static void forest(String s){}static void farm(String s){}static void i(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){if(e instanceof TaskCancelledException)throw (TaskCancelledException)e;throw new AssertionError(e);}static void printStackTrace(String s,Throwable t){if(t instanceof TaskCancelledException)throw (TaskCancelledException)t;throw new AssertionError(t);}}
 static class TimeUtil {static void sleep(long n){}}
 static class MessageUtil {static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean checkResultCodeString(String t,JSONObject j){return checkResultCode(t,j);}}
 static class Toast {static void show(String s){}}static class UserIdMap {static String getShowName(String s){return s;}}
 static class Statistics {enum DataType {COLLECTED,WATEREDCOUNT}static void addData(DataType t,int v){}static void save(){}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int n){counts.put(k,n);}static void wateredFriendToday(String s){}static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
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
 static class ApplicationHook {static ClassLoader getClassLoader(){return null;}}
 static class AntSports {void syncStepNow(){}static void queryLegacyPatrolSteps(ClassLoader loader){}}static <T>T getModel(Class<T> t){return null;}
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
 static int initialQuota,used,draws,reported,gameQueries,gameMode;static boolean failBefore,failAfter,cancelGame,failDraw;
 static JSONObject rights(int quota,int count){return new JSONObject().put("quotaCanUse",quota).put("usedQuota",count).put("quotaLimit",gameMode==3?50:5);}
 static class AntFarmRpcCall {
  static String queryGameList(){if(cancelGame)throw new TaskCancelledException();gameQueries++;if(gameQueries>100)throw new AssertionError("game loop");if(gameQueries==2&&failBefore||gameQueries==3&&failAfter)return "{}";return new JSONObject().put("success",true).put("gameCenterDrawRights",rights(initialQuota-draws,used)).toString();}
  static String drawGameCenterAward(int n){draws++;if(failDraw)return "{}";used++;return new JSONObject().put("success",true).put("gameCenterDrawRights",rights(gameMode==1?initialQuota:initialQuota-draws,used)).toString();}
 }
 static class GameTask {static class Farm_ddply {static int reportSync(String s,int n){reported+=n;if(gameMode==2)initialQuota+=n;else if(gameMode==3)initialQuota++;return n;}}}
 @@FARM@@
 enum Mode {COMPATIBLE,AGGRESSIVE}static boolean isRunning;static int moleCount=15,cancelStage,settles;
 static void runAggressiveMode(){}
 @@WHACK@@
 static void reset(){homeQueries=waterCalls=propCalls=0;reject=homeNull=false;Status.flags.clear();Status.counts.clear();patrolQueries=switchCalls=goCalls=exchanges=keepCalls=0;initialQuota=used=draws=reported=gameQueries=gameMode=0;failBefore=failAfter=cancelGame=failDraw=false;settles=0;Log.logs.clear();}
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
  reset();Status.setIntFlagToday(FLAG_GAME_DRAW_TASK_TRY,MAX_GAME_DRAW_TASK_TRY);initialQuota=2;f.drawGameCenterAward();assert draws==2&&gameQueries==1&&reported==0:"skip task refresh but still open available boxes";
  reset();initialQuota=2;f.drawGameCenterAward();assert draws==2&&reported==3;
  reset();failBefore=true;f.drawGameCenterAward();assert reported==0&&Status.flags.isEmpty();
  reset();failAfter=true;f.drawGameCenterAward();assert reported==5&&Status.flags.isEmpty():"unknown query cannot set ineffective flag";
  reset();initialQuota=2;gameMode=1;f.drawGameCenterAward();assert draws==1&&reported==0:"unchanged draw quota must stop";
  reset();for(int i=0;i<MAX_GAME_DRAW_TASK_TRY+2;i++)f.drawGameCenterAward();assert reported==5*MAX_GAME_DRAW_TASK_TRY&&Status.getIntFlagToday(FLAG_GAME_DRAW_TASK_TRY)==MAX_GAME_DRAW_TASK_TRY:"delayed credits allow retries but remain bounded";
  Status.counts.clear();f.drawGameCenterAward();assert reported==5*(MAX_GAME_DRAW_TASK_TRY+1):"next day retries resume";
  reset();gameMode=2;initialQuota=2;Status.setIntFlagToday(FLAG_GAME_DRAW_TASK_TRY,2);f.drawGameCenterAward();assert draws==5&&reported==3&&Status.getIntFlagToday(FLAG_GAME_DRAW_TASK_TRY)==0:"fresh rewards opened same round, report only remaining quota";
  reset();gameMode=3;f.drawGameCenterAward();assert gameQueries==3*MAX_GAME_DRAW_ROUND&&draws==MAX_GAME_DRAW_ROUND-1:"persistent progress must respect same-run round limit";
  reset();failAfter=true;f.drawGameCenterAward();assert Status.counts.isEmpty():"unknown after-query does not consume retry budget";
  reset();initialQuota=2;failDraw=true;f.drawGameCenterAward();assert draws==1&&reported==0:"failed opening must not submit more tasks";
  reset();cancelGame=true;try{f.drawGameCenterAward();throw new AssertionError("farm cancellation swallowed");}catch(TaskCancelledException expected){}
  for(int stage=0;stage<2;stage++){reset();cancelStage=stage;try{startSuspend(Mode.COMPATIBLE);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert !isRunning&&Status.flags.isEmpty()&&settles==0;}
  reset();cancelStage=2;Thread.currentThread().interrupt();try{startSuspend(Mode.COMPATIBLE);throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}assert Thread.interrupted()&&settles==0&&Status.flags.isEmpty()&&!isRunning;
  System.out.println("PASS forest no-progress/filter/rejection/pages/null, patrol failures and unchanged states, current farm quota, cancellation");
 }
}
'''
code = code.replace('@@HOME@@', home_loop).replace('@@PATROL@@', method(forest, 'private void queryUserPatrol(') + method(forest, 'private void patrolKeepGoing('))
code = code.replace('@@FARM@@', method(farm, 'private void drawGameCenterAward('))
farm_source=(SOURCE/farm).read_text(encoding='utf-8')
code = code.replace('@@LIMITS@@', '\n'.join(re.search(r'private static final [^;]*\b'+name+r'\s*=.*?;',farm_source).group(0) for name in ('FLAG_GAME_DRAW_TASK_TRY','MAX_GAME_DRAW_TASK_TRY','MAX_GAME_DRAW_ROUND')))
code = code.replace('@@WHACK@@', method(whack, 'public static void startSuspend(') + method(whack, 'private static void runCompatibleMode('))
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-forest-farm-progress-') as tmp:
    f = Path(tmp)/'ForestFarmProgressCheck.java'
    f.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(f), str(SOURCE/'util/TaskCancelledException.java')], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp+os.pathsep+str(jar), 'ForestFarmProgressCheck'], check=True)

# Keep the real run entry, catch/finally and mode helpers; isolate unrelated task work at body().
priority_run = run[:run.index('            if (waterFriendEnergyFirst.getValue())')] + '            body();\n' + run[run.rindex('        } catch (Throwable t)'):]
priority_run = priority_run.replace('AntForestV2.this', 'ForestTeamPriorityCheck.this')
priority = r'''
import org.json.*;import java.util.*;import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestTeamPriorityCheck {
 static final String TAG="test",FLAG_TEAM_MODE_SWITCHED="Forest::teamWaterSwitchedToTeam",FLAG_TEAM_WATER_DAILY_COUNT="water";
 static class Bool {boolean value;boolean getValue(){return value;}}
 static class Num {int getValue(){return 20;}}
 Bool teamPriority=new Bool();Num partnerteamWaterNum=new Num();
 AtomicInteger taskCount=new AtomicInteger();String selfId;boolean hasErrorWait;
 static boolean mode,quiet,waterInBody,cancelRestore;static int failTeam,failPersonal,bodyError,waters,errors;static String failPhase;
 static Boolean collectionMode;static List<String> events=new ArrayList<>(),requests=new ArrayList<>();
 static class Status {
  static Set<String> flags=new HashSet<>();static int used;
  static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}static void clearFlag(String k){flags.remove(k);}
  static int getforestHuntHelpToday(String k){return used;}static void forestHuntHelpToday(String k,int n,String uid){used=n;}
 }
 static class UserIdMap {static String getCurrentUid(){return "self";}}
 static class ApplicationHook {static boolean isOffline(){return false;}}
 static class MyUtils {static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}}
 static class TimeUtil {static void sleep(long ms){}}
 static class Toast {static void show(String s){}}
 static class Log {static void record(String s){}static void forest(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){errors++;}}
 static class AntForestRpcCall {
  static String updateUserConfiginTeam(Boolean target){
   String event=target?"team":"personal";requests.add(event);events.add(event);
   if(!target&&cancelRestore)throw new TaskCancelledException();
   if(target&&failTeam>0){failTeam--;return "{resultCode:'FAIL'}";}
   if(!target&&failPersonal>0){failPersonal--;return "{resultCode:'FAIL'}";}
   mode=target;return "{resultCode:'SUCCESS'}";
  }
  static String queryHomePage(){JSONObject h=new JSONObject().put("resultCode","SUCCESS").put("nextAction",mode?"Cultivate":"Team")
   .put("userBaseInfo",new JSONObject().put("currentEnergy",100));
   if(mode)h.put("teamHomeResult",new JSONObject().put("mainMember",new JSONObject()).put("teamBaseInfo",new JSONObject().put("teamId","team")));
   return h.toString();}
  static String queryMiscInfo(String kind,String id){return "{resultCode:'SUCCESS',combineHandlerVOMap:{teamCanWaterCount:{waterCount:100}}}";}
  static String teamWater(String id,int amount){assert mode;waters++;return "{resultCode:'SUCCESS'}";}
 }
 static void phase(String name){events.add(name);if(name.equals(failPhase))throw new IllegalStateException(name);}
 static class Statistics {static void save(){phase("statistics");}}
 static class FriendWatch {static void save(){phase("friends");}}
 boolean isRevivedSelfQuietTime(){return quiet;}
 void body(){events.add("body");if(waterInBody)teamCooperateWater();if(bodyError==1)throw new IllegalStateException("body");if(bodyError==2)throw new TaskCancelledException();}
 void scheduleForestCycles(int kind,long delay){phase("schedule");}void scheduleBubbleBoost(long delay){phase("boost");}
 void collectRobExpandEnergy(){if(collectionMode!=null)assert mode==collectionMode:"wrong mode during final energy collection";phase("collect");}
 void syncTodayCollectedFromServer(){phase("calibrate");}
 @@RUN@@
 @@HELPERS@@
 static ForestTeamPriorityCheck reset(boolean enabled){
  mode=quiet=waterInBody=cancelRestore=false;failTeam=failPersonal=bodyError=waters=errors=0;failPhase=null;collectionMode=null;
  Status.flags.clear();Status.used=0;events.clear();requests.clear();ForestTeamPriorityCheck f=new ForestTeamPriorityCheck();f.teamPriority.value=enabled;return f;
 }
 static void enabled(){ForestTeamPriorityCheck f=reset(true);waterInBody=true;collectionMode=true;f.run();
  assert requests.equals(List.of("team","personal"))&&waters==1&&!mode&&Status.flags.isEmpty():"priority mode restored during watering or not restored at end";
  assert events.indexOf("collect")<events.indexOf("calibrate")&&events.indexOf("calibrate")<events.indexOf("personal"):"restored before extra energy/calibration";}
 static void disabled(){ForestTeamPriorityCheck f=reset(false);f.run();assert requests.isEmpty()&&!mode;
  f=reset(false);mode=true;f.run();assert requests.isEmpty()&&mode:"unowned user team mode changed";
  f=reset(false);waterInBody=true;f.run();assert requests.equals(List.of("team","personal"))&&!mode&&waters==1;}
 static void fallback(){ForestTeamPriorityCheck f=reset(true);failTeam=1;waterInBody=true;collectionMode=false;f.run();
  assert requests.equals(List.of("team","team","personal"))&&!mode&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED):"watering fallback leaked team mode after priority entry failed";}
 static void cleanupFailures(){for(String phase:List.of("schedule","boost","collect","calibrate","statistics","friends")){
  ForestTeamPriorityCheck f=reset(true);failPhase=phase;try{f.run();throw new AssertionError("cleanup error hidden");}catch(IllegalStateException expected){}
  assert !mode&&requests.equals(List.of("team","personal"))&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED):"cleanup failure bypassed personal restoration: "+phase;
 }}
 static void cancellation(){ForestTeamPriorityCheck f=reset(true);bodyError=2;try{f.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
  assert !mode&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED);
  f=reset(true);bodyError=1;f.run();assert !mode&&errors==1;
  f=reset(true);cancelRestore=true;try{f.run();throw new AssertionError("restore cancel swallowed");}catch(TaskCancelledException expected){}
  assert mode&&Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED);cancelRestore=false;f.teamPriority.value=false;f.run();assert !mode&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED);}
 static void recovery(){ForestTeamPriorityCheck f=reset(true);mode=true;Status.flagToday(FLAG_TEAM_MODE_SWITCHED);failTeam=1;f.run();
  assert requests.equals(List.of("personal","team"))&&!mode&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED):"stale switch flag was not repaired before failed priority entry";
  f=reset(true);failPersonal=1;f.run();assert mode&&Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED);
  f.teamPriority.value=false;f.run();assert !mode&&!Status.hasFlagToday(FLAG_TEAM_MODE_SWITCHED);
  f=reset(true);quiet=true;f.run();assert requests.isEmpty():"quiet window changed account mode";}
 public static void main(String[] args){Map<String,Runnable> tests=new LinkedHashMap<>();tests.put("enabled",ForestTeamPriorityCheck::enabled);
  tests.put("disabled",ForestTeamPriorityCheck::disabled);tests.put("failed entry/watering fallback",ForestTeamPriorityCheck::fallback);
  tests.put("cleanup failures",ForestTeamPriorityCheck::cleanupFailures);tests.put("cancellation",ForestTeamPriorityCheck::cancellation);tests.put("recovery",ForestTeamPriorityCheck::recovery);
  List<String> failures=new ArrayList<>();for(var t:tests.entrySet())try{t.getValue().run();System.out.println("PASS priority: "+t.getKey());}catch(AssertionError e){failures.add(t.getKey()+": "+e.getMessage());}
  assert failures.isEmpty():String.join("; ",failures);
 }
}
'''
fix_signature=re.search(r'private (?:static )?void fixTeamModeIfNeeded\(', (SOURCE/forest).read_text(encoding='utf-8')).group(0)
priority=priority.replace('@@RUN@@',priority_run).replace('@@HELPERS@@','\n'.join(method(forest,s) for s in (
    fix_signature,'private void teamCooperateWater(','private static JSONObject queryTeamHomePage(', 'private static boolean updateUserConfiginTeam(',
    'private static int teamState(', 'private static String getTeamId(', 'private static int getTeamCanWaterCount(')))
with tempfile.TemporaryDirectory(prefix='sesame-team-priority-') as tmp:
    f=Path(tmp)/'ForestTeamPriorityCheck.java';f.write_text(priority,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestTeamPriorityCheck'],check=True)
