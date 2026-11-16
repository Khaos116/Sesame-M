"""Replay Nov16 task merge guards on production code without a device or network."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

code = r'''
import org.json.*;import java.util.*;import java.util.concurrent.TimeUnit;import java.util.function.BooleanSupplier;import java.math.BigDecimal;
public class Nov16Tasks {
 static final String TAG="test";
 static class System {static long now=1800000000000L;static long currentTimeMillis(){return now;}static final java.io.PrintStream out=java.lang.System.out;}
 static class TaskCancelledException extends RuntimeException {}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}static boolean isNowAfterOrCompareTimeStr(String s){return false;}}
 static class TaskLifecycle {static long version;static boolean open=true;static int active;static long generation(){return version;}static boolean isOpen(){return open;}
  static Work enter(){return enter(version);}static Work enter(long n){if(!open||n!=version)return null;active++;return new Work();}static class Work implements AutoCloseable {public void close(){active--;}}}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class Log {static void record(String s){}static void other(String s){}static void goldenBeans(String s){}static void error(String s){}static void i(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}static void printStackTrace(Throwable e){throw new AssertionError(e);}}
 static class Status {static final Set<String> flags=new HashSet<>();static final Map<String,Integer> counts=new HashMap<>();static String key(String k){return UserIdMap.uid+":"+Math.floorDiv(System.now+28800000L,86400000L)+":"+k;}
  static boolean hasFlagToday(String k){return flags.contains(key(k));}static void flagToday(String k){flags.add(key(k));}static int getIntFlagToday(String k){return counts.getOrDefault(key(k),0);}static void setIntFlagToday(String k,int n){counts.put(key(k),n);}static boolean canGreenFinancePointFriend(){return true;}static void greenFinancePointFriend(){flagToday("greenDone");}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return !Boolean.FALSE.equals(j.opt("success"))&&"SUCCESS".equals(j.optString("resultCode"));}static boolean checkSuccess(String t,JSONObject j){return Boolean.TRUE.equals(j.opt("success"));}static boolean isRetryable(JSONObject j){return false;}static boolean isServerBusy(JSONObject j){return false;}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"))||j.has("error");}static String errorMessage(JSONObject j){return j.optString("errorMessage");}}
 static class RpcFailurePolicy {static boolean isRiskDenied(String c,String m){return "1009".equals(c)||m.contains("denied");}}
 static class GameTaskAction {static boolean accepted(JSONObject j){return !RpcRequestGuard.isFailure(j)&&(Boolean.TRUE.equals(j.opt("success"))||"SUCCESS".equals(j.optString("resultCode")));}}
 static class StringUtil {static Integer parseIntOrNull(String s){try{return Integer.valueOf(s);}catch(Exception e){return null;}}}
 static class AntMemberTaskListMap {static Map<String,String> names=new HashMap<>();static Map<String,String> getMap(){return names;}static void add(String k,String v){names.put(k,v);}}
 static class AntMemberAdTaskListMap {static Map<String,String> names=new HashMap<>();static Map<String,String> getMap(){return names;}static void add(String k,String v){names.put(k,v);}}
 static class AntMemberRpcCall {static String[] replies={"{}","{}","{}"};static int[] reads=new int[3];static boolean cancelled;
  static String read(int i){if(cancelled)throw new TaskCancelledException();reads[i]++;return replies[i];}static String signPageTaskList(int p,String s){return read(0);}static String currentMemberAdTasks(){return read(1);}static String currentMemberProcesses(){return read(2);}}
 static class Member {static final class MemberTaskPausedException extends Exception {}
  @@MEMBER@@
 }
 static class Game {private String cachedToken,cachedTokenUid;private long cachedTokenGeneration,cachedTokenAt;private static final long TOKEN_REUSE_MS=300000L;private final int requestsPerEgg=3;private int logins,reports;private boolean fail;
  private String login(Runnable checkpoint){checkpoint.run();logins++;return UserIdMap.uid+":"+logins;}
  private boolean executeSingleReport(String s,int i,int n,String channel,Runnable checkpoint,String token){checkpoint.run();assert token.startsWith(UserIdMap.uid+":");reports++;if(fail){invalidateToken(token);return false;}return true;}
  @@GAME@@
 }
 enum Outcome {DONE,RETRY}
 static class TaskAttemptPolicy {static class Site {Site(String a,String b,String c,String d,java.util.function.Function<String,Boolean> f){}}static Outcome handle(String id,String title,Object unused,java.util.concurrent.Callable<Outcome> action,java.util.function.Consumer<String> log,Site site)throws Exception{return action.call();}}
 static class AntSportsRpcCall {static int reads,writes;static boolean changing;
  static String list(String key){reads++;JSONObject t=new JSONObject().put("title","task").put("taskId","AD_BALL").put("bubbleTaskStatus","INIT").put("taskStatus","TO_RECEIVE").put("viewSec",0);if(changing)t.put("nonce",reads);return new JSONObject().put("success",true).put("data",new JSONObject().put(key,new JSONArray().put(t))).toString();}
  static String queryBubbleTask(){return list("bubbleTaskVOS");}static String queryTaskInfo(){return list("taskInfos");}static String queryTaskCenter(){return list("taskCenterTaskVOS");}}
 static class Sports {static boolean receiveBrowseReward(JSONObject j){AntSportsRpcCall.writes++;return true;}static boolean receiveTaskReward(JSONObject j){AntSportsRpcCall.writes++;return true;}static boolean completeTask(JSONObject j){throw new AssertionError();}static void receiveSpecialPrize(String a,String b){throw new AssertionError();}static void signIn(){throw new AssertionError();}static void receiveBubbleReward(String a,String b){throw new AssertionError();}static boolean probeBrowseStatus(String s){return true;}
  @@SPORTS@@
 }
 static class JsonUtil {static Object getValueByPathObject(JSONObject j,String p){return null;}static String getValueByPath(JSONObject j,String p){return "";}}
 static class Field {boolean getValue(){return true;}}
 static class GreenFinanceRpcCall {static String home;static int reads;static boolean last,stuck;static String greenFinanceIndex(){return home;}static void doTask(String a,String b,String c){}
  static String queryRankingList(int n){reads++;return new JSONObject().put("success",true).put("result",new JSONObject().put("lastPage",last).put("nextStartIndex",stuck?n:n+1).put("rankingList",new JSONArray())).toString();}
  static String queryGuestIndexPoints(String u){throw new AssertionError();}static String batchSteal(JSONArray a,String u){throw new AssertionError();}}
 static class Green {private static final int MAX_STEAL_PAGES=50;Field greenFinancePointFriend=new Field();static final List<String> batches=new ArrayList<>();static int signs;
  void batchSelfCollect(JSONArray a){batches.add(a.toString());}void signIn(String s){signs++;}void behaviorTick(){}void donation(){}
  @@GREEN@@
 }
 static class AntOceanRpcCall {static String list,reply;static int reads,writes;static boolean cancelAfter;
  static String antfishQueryNeedAppraiseFishList(){assert TaskLifecycle.active>0;reads++;return list;}static String antfishFishAppraise(String i,String s,String u){assert TaskLifecycle.active>0&&!i.isEmpty()&&!s.isEmpty()&&!u.isEmpty();writes++;if(cancelAfter)TimeUtil.cancel=true;return reply;}}
 static class Ocean {@@OCEAN@@}
 static class RuntimeInfo {static final RuntimeInfo instance=new RuntimeInfo();final Map<String,String> values=new HashMap<>();static RuntimeInfo getInstance(){return instance;}String getString(String k){return values.getOrDefault(k,"");}boolean putVerified(String k,String v){values.put(k,v);return true;}}
 static class GoldenBeansSupport {static String TAG="mall";static void pause(int n){}static JSONObject parse(String s){return MyUtils.newJSONObject(s);}static boolean ok(JSONObject j){return j!=null&&Boolean.TRUE.equals(j.opt("success"));}static JSONObject findObject(JSONObject j,String k){return j.optJSONObject(k);}static String describe(JSONObject j){return "error";}}
 static class GoldenBeansMallItemMap {static void load(){}static String get(String k){return null;}static void add(String k,String v){}static boolean save(){return true;}}
 static class AlipayGoldenBeansMallItem {static void clear(){}}
 static class goldenbeansRpcCall {static String exchangeReply,ordersReply;static int writes,reads;static String mallItems(int a,int b){return "{}";}static String home(){return "{}";}static String mallOrders(int a,int b){reads++;return ordersReply;}static String mallExchange(String a,String b){writes++;return exchangeReply;}}
 @@MALL@@
 static void reset(){Status.flags.clear();Status.counts.clear();TimeUtil.cancel=false;TaskLifecycle.open=true;UserIdMap.uid="A";AntMemberRpcCall.reads=new int[3];AntMemberRpcCall.cancelled=false;RuntimeInfo.instance.values.clear();AntOceanRpcCall.reads=AntOceanRpcCall.writes=0;AntOceanRpcCall.cancelAfter=false;AntSportsRpcCall.reads=AntSportsRpcCall.writes=0;AntSportsRpcCall.changing=false;goldenbeansRpcCall.reads=goldenbeansRpcCall.writes=0;}
 static void member()throws Exception{
  reset();AntMemberRpcCall.replies=new String[]{"{success:false,resultCode:102}","{availableTaskList:[]}","{success:true,taskProcessVOList:[]}"};
  assert Member.fetchRows(0,1,"s")==null;assert Member.fetchRows(0,1,"s")==null&&AntMemberRpcCall.reads[0]==1;
  assert Member.fetchRows(1,0,"").isEmpty()&&AntMemberRpcCall.reads[1]==1:"ordinary fetch failure stopped independent ad list";
  for(int n=1;n<3;n++){Status.setIntFlagToday("memberTaskList::0::cooldown",0);assert Member.fetchRows(0,1,"s")==null;}
  Status.setIntFlagToday("memberTaskList::0::cooldown",0);assert Member.fetchRows(0,1,"s")==null&&AntMemberRpcCall.reads[0]==3:"failed list was queried beyond the daily limit";
  UserIdMap.uid="B";assert Member.fetchRows(0,1,"s")==null&&AntMemberRpcCall.reads[0]==4:"account A failure leaked into B";
  UserIdMap.uid="A";System.now+=86400000L;AntMemberRpcCall.replies[0]="{success:true,taskProcessVOList:[]}";assert Member.fetchRows(0,1,"s").isEmpty();
  assert Status.getIntFlagToday("memberTaskList::0::failures")==0;
  assert Member.rows("{availableTaskList:[]}").isEmpty();
  for(String bad:new String[]{"{}","{availableTaskList:{}}","{success:false,availableTaskList:[]}","{resultCode:102,availableTaskList:[]}","{availableTaskList:[null]}"})assert Member.rows(bad)==null:bad;
  JSONObject ad=new JSONObject().put("adTask",true).put("taskConfigInfo",new JSONObject().put("name","ad"));
  assert Member.rememberTitle(ad)&&AntMemberAdTaskListMap.names.containsKey("ad")&&!AntMemberTaskListMap.names.containsKey("ad");
  JSONObject ordinary=new JSONObject().put("taskConfigInfo",new JSONObject().put("name","member"));assert Member.rememberTitle(ordinary)&&AntMemberTaskListMap.names.containsKey("member");
  reset();AntMemberRpcCall.replies[0]="{resultCode:1009}";try{Member.fetchRows(0,1,"s");throw new AssertionError();}catch(Member.MemberTaskPausedException expected){}
  assert Status.counts.isEmpty():"risk pause became an ordinary fetch failure";
  reset();AntMemberRpcCall.cancelled=true;try{Member.fetchRows(0,1,"s");throw new AssertionError();}catch(TaskCancelledException expected){}assert Status.counts.isEmpty();
 }
 static void game(){
  reset();Game g=new Game();assert g.reportSync("game",1,null,()->true)==1&&g.logins==1;
  assert g.reportSync("game",1,null,()->true)==1&&g.logins==1:"valid account token was not reused";
  UserIdMap.uid="B";assert g.reportSync("game",1,null,()->true)==1&&g.logins==2;
  TaskLifecycle.version++;assert g.reportSync("game",1,null,()->true)==1&&g.logins==3;
  System.now+=300001;assert g.reportSync("game",1,null,()->true)==1&&g.logins==4;
  g.fail=true;assert g.reportSync("game",1,null,()->false)==0;g.fail=false;assert g.reportSync("game",1,null,()->true)==1&&g.logins==5;
  try{g.reportSync("game",1,null,()->{UserIdMap.uid="C";return false;});throw new AssertionError();}catch(TaskCancelledException expected){}assert TaskLifecycle.active==0;
 }
 static void sports(){
  for(boolean changing:new boolean[]{false,true})for(int source=0;source<3;source++){
   reset();AntSportsRpcCall.changing=changing;
   if(source==0)Sports.queryAndProcessBubbleTasks();else if(source==1)Sports.processBrowseTasks();else Sports.processTaskCenter();
   assert AntSportsRpcCall.writes==(changing?(source==2?20:4):1):"unchanged task was repeated or retry loop was not bounded";
  }
  for(int source=0;source<3;source++){reset();TimeUtil.cancel=true;try{if(source==0)Sports.queryAndProcessBubbleTasks();else if(source==1)Sports.processBrowseTasks();else Sports.processTaskCenter();throw new AssertionError();}catch(TaskCancelledException expected){}assert AntSportsRpcCall.reads==0;}
 }
 static void green(){
  reset();Green.batches.clear();Green.signs=0;GreenFinanceRpcCall.home="{success:true,result:{greenFinanceSigned:true,mcaGreenLeafResult:{greenLeafList:[{code:a,bsnId:1},{code:a,bsnId:2},{code:b,bsnId:3}]}}}";
  new Green().run();assert Green.batches.equals(List.of("[\"1\",\"2\"]","[\"3\"]"))&&Green.signs==2:"mixed leaves were duplicated or merged across codes";
  GreenFinanceRpcCall.home="{success:true,result:{greenFinanceSigned:true}}";new Green().run();assert Green.signs==4:"missing leaf list stopped unrelated sign-in";
  GreenFinanceRpcCall.reads=0;GreenFinanceRpcCall.stuck=false;GreenFinanceRpcCall.last=false;new Green().batchStealFriend();assert GreenFinanceRpcCall.reads==50&&!Status.hasFlagToday("greenDone");
  GreenFinanceRpcCall.reads=0;GreenFinanceRpcCall.stuck=true;new Green().batchStealFriend();assert GreenFinanceRpcCall.reads==1;
  GreenFinanceRpcCall.reads=0;GreenFinanceRpcCall.last=true;new Green().batchStealFriend();assert GreenFinanceRpcCall.reads==1&&Status.hasFlagToday("greenDone");
 }
 static void ocean(){
  String list="{resultCode:SUCCESS,fishList:[{imageId:image,seasonId:season,userId:user,appraiseVO:{canAppraise:true}}]}";
  for(String reply:new String[]{"{resultCode:SUCCESS,appraiseRewardList:[]}","{resultCode:SUCCESS,appraiseRewardRemainNum:0}"}){
   reset();AntOceanRpcCall.list=list;AntOceanRpcCall.reply=reply;new Ocean().antfishAppraiseFish();assert AntOceanRpcCall.writes==1&&Status.hasFlagToday("Ocean::FISH_APPRAISE_LIMIT");
  }
  for(String reply:new String[]{"{resultCode:SUCCESS}","{resultCode:SUCCESS,appraiseRewardList:[{}]}","{resultCode:SUCCESS,appraiseRewardList:[{extInfo:{popup:{rightsNums:-1}}}]}","{resultCode:FAIL}"}){
   reset();AntOceanRpcCall.list=list;AntOceanRpcCall.reply=reply;new Ocean().antfishAppraiseFish();assert AntOceanRpcCall.writes==1&&!Status.hasFlagToday("Ocean::FISH_APPRAISE_LIMIT");
  }
  reset();AntOceanRpcCall.list=list;AntOceanRpcCall.reply="{resultCode:SUCCESS,appraiseRewardList:[{extInfo:{popup:{rightsNums:'1'}}}]}";new Ocean().antfishAppraiseFish();assert AntOceanRpcCall.writes==1&&AntOceanRpcCall.reads==2:"duplicate fish was appraised twice";
  reset();AntOceanRpcCall.list=list.replace("imageId:image","imageId:{}");new Ocean().antfishAppraiseFish();assert AntOceanRpcCall.writes==0;
  reset();AntOceanRpcCall.list=list;AntOceanRpcCall.cancelAfter=true;try{new Ocean().antfishAppraiseFish();throw new AssertionError();}catch(TaskCancelledException expected){}assert TaskLifecycle.active==0&&Status.flags.isEmpty();
 }
 static void mall()throws Exception{
  JSONObject item=new JSONObject().put("spuName","coupon").put("spuId","spu").put("skuId","sku").put("cost",0).put("dayLeft",2);
  reset();goldenbeansRpcCall.exchangeReply="{success:true,data:{canBuy:true,orderNo:new}}";goldenbeansRpcCall.ordersReply="{success:true,data:{orderInfos:[{orderNo:new}]}}";
  assert GoldenBeansMall.exchange(0,item,1,new JSONArray[]{new JSONArray()},new int[1])&&Status.getIntFlagToday("goldenBeans::mallExchange::coupon")==1:"wrapped receipt or order list was ignored";
  for(String reply:new String[]{"{success:true,data:{canBuy:'true',orderNo:new}}","{success:true,data:{orderNo:new}}"}){
   reset();goldenbeansRpcCall.exchangeReply=reply;assert !GoldenBeansMall.exchange(0,item,1,new JSONArray[]{new JSONArray()},new int[1]);assert !RuntimeInfo.instance.getString("goldenBeans.mall.pending::spu::sku").isEmpty();
  }
  reset();goldenbeansRpcCall.exchangeReply="{success:true,canBuy:false,data:{canBuy:true,orderNo:new}}";assert !GoldenBeansMall.exchange(0,item,1,new JSONArray[]{new JSONArray()},new int[1]);assert RuntimeInfo.instance.getString("goldenBeans.mall.pending::spu::sku").isEmpty();
  goldenbeansRpcCall.ordersReply="{success:true,orderInfos:{},data:{orderInfos:[]}}";assert GoldenBeansMall.fetchOrders(0)==null:"invalid root array was hidden by nested data";
 }
 public static void main(String[] args)throws Exception{member();game();sports();green();ocean();mall();assert TaskLifecycle.active==0;System.out.println("PASS Nov16 tasks: independent member retries/ad candidates, isolated game tokens, bounded sports/green loops, ocean saturation and wrapped mall receipts");}
}
'''

groups = {
    "@@MEMBER@@": ("antMember/MemberBrowseTasks.java", ["private static List<JSONObject> fetchRows(", "static boolean rememberTitle(", "static boolean listAccepted(", "private static List<JSONObject> rows(", "private static boolean append(", "private static JSONObject checkedResponse(", "private static void checkPaused(", "private static JSONObject config(", "private static String title(", "private static boolean isAd("]),
    "@@GAME@@": ("antGame/GameTask.java", ["private String ensureToken(", "private synchronized void invalidateToken(", "public int reportSync(String gameType, int eggCount, String channelOverride, BooleanSupplier reached)"]),
    "@@SPORTS@@": ("antSports/AntSports.java", ["public static void queryAndProcessBubbleTasks(", "public static void processBrowseTasks(", "public static void processTaskCenter("]),
    "@@GREEN@@": ("greenFinance/GreenFinance.java", ["public void  run(", "private void batchStealFriend("]),
    "@@OCEAN@@": ("antOcean/AntOcean.java", ["private void antfishAppraiseFish("]),
}
for token, (path, signatures) in groups.items():
    code = code.replace(token, "\n".join(method("model/task/" + path, signature) for signature in signatures))
mall = (SOURCE / "model/task/goldenbeans/GoldenBeansMall.java").read_text(encoding="utf-8")
code = code.replace("@@MALL@@", mall[mall.index("public final class GoldenBeansMall"):].replace("public final class", "static final class", 1))
for path, declaration, fixture in (
    ("antGame/GameTask.java", "private static final long TOKEN_REUSE_MS", "private static final long TOKEN_REUSE_MS=300000L;"),
    ("greenFinance/GreenFinance.java", "private static final int MAX_STEAL_PAGES", "private static final int MAX_STEAL_PAGES=50;"),
):
    production = (SOURCE / ("model/task/" + path)).read_text(encoding="utf-8")
    code = code.replace(fixture, next(line.strip() for line in production.splitlines() if declaration in line))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-nov16-tasks-") as temporary:
    source = Path(temporary) / "Nov16Tasks.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temporary, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + str(jar), "Nov16Tasks"], check=True, timeout=30)
