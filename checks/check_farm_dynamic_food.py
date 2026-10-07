"""Replay actual dynamic food policy and RPC payload without Android or network."""
from pathlib import Path
import os, re, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE
farm = "model/task/antFarm/AntFarm.java"
src = (SOURCE / farm).read_text(encoding="utf-8")
assert re.search(r'new BooleanModelField\("dynamicSpecialFood", "[^"]+", false\)', src)
assert re.search(r'new IntegerModelField\("dynamicFoodDailyLimit", "[^"]+", 0, 0, 1000\)', src)
assert re.search(r'new BooleanModelField\("rankingFoodRefill", "[^"]+", false\)', src)
signatures = ["private static final class DynamicFoodState", "private static double foodNumber(",
 "private boolean dynamicFoodOwner(", "private DynamicFoodState queryDynamicFoodState(",
 "private JSONObject dynamicFoodBudget(", "private int dynamicFoodQuota(", "private static String foodKey(",
 "private static int foodStock(", "private JSONObject chooseDynamicFood(", "private void useDynamicSpecialFood(",
 "private List<JSONObject> getSortedCuisineList(", "private static int rankingInt(", "private static String rankingDay("]
code = r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class FarmDynamicFoodCheck {
 static final String TAG="check",DYNAMIC_FOOD_KEY="farmDynamicFood",DYNAMIC_FOOD_PENDING="farm::dynamicFoodUnconfirmed";
 static final int SPECIAL_FOOD_BATCH_LIMIT=10;
 static class System {static long now=java.time.OffsetDateTime.parse("2026-10-07T19:58:00+08:00").toInstant().toEpochMilli();static long currentTimeMillis(){return now;}}
 static class Bool {boolean n=true;boolean getValue(){return n;}}
 static class Int {int n;Int(int n){this.n=n;}int getValue(){return n;}}
 Bool useSpecialFood=new Bool(),dynamicSpecialFood=new Bool(),rankingDonation=new Bool(),rankingFoodRefill=new Bool();
 Int dynamicFoodDailyLimit=new Int(20),useSpecialFoodCountLimit=new Int(0);
 String ownerUserId="A",ownerFarmId="farmA";double harvestBenevolenceScore,benevolenceScore;boolean enabled=true;
 boolean isEnable(){return enabled;}boolean check(){return true;}
 enum AnimalInteractStatus {HOME,STEALING}enum AnimalFeedStatus {HUNGRY,EATING,SLEEPY}
 static class RankingSnapshot {String owner="A",activity="season",round="day";boolean weekly;}
 boolean rankingWindow(RankingSnapshot r,long now){return liveWindow;}static boolean liveWindow=true;
 int rankingQuota(RankingSnapshot rank){return rankQuota;}static int rankQuota=20;
 static RankingSnapshot rankingSnapshot(JSONObject response,String owner,boolean weekly){if(badRank)return null;RankingSnapshot r=new RankingSnapshot();r.weekly=weekly;r.activity=liveActivity;r.round=liveRound;return r;}
 static boolean badRank;static String liveActivity="season",liveRound="day";
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class RuntimeInfo {static Map<String,RuntimeInfo> accounts=new HashMap<>();static boolean fail;static int writes;Map<String,String> data=new HashMap<>();static RuntimeInfo getInstance(){return accounts.computeIfAbsent(UserIdMap.uid,k->new RuntimeInfo());}String getString(String key){return data.getOrDefault(key,"");}boolean putVerified(String key,String value){writes++;if(fail)return false;data.put(key,value);return true;}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkMemo(String t,JSONObject o){return "SUCCESS".equals(o.optString("memo"));}}
 static class Log {static void farm(String s){}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class TimeUtil {static void sleep(long n){if(Thread.currentThread().isInterrupted())throw new TaskCancelledException();}}
 static class Status {static Status INSTANCE=new Status();static int used;static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}static void clearFlag(String s){flags.remove(s);}static void useSpecialFoodToday(){used++;}int getUseSpecialFoodCount(){return used;}}
 static JSONObject cuisine(String id,Object count){return new JSONObject().put("cookbookId","book").put("cuisineId",id).put("name",id).put("count",count);}
 static class AntFarmRpcCall {
  static int stock=25,otherStock=1,uses,reads,harvests;static double progress,harvested,unit=0.6;
  static boolean stockChange=true,effectChange=true,unknown,missingEffect,failed,switchOwner,cancel,failSaveAfter;
  static String home="HOME",feed="EATING",responseOwner="A";static Object badCount=null;static boolean duplicate,stockAlias;
  static List<Integer> batches=new ArrayList<>();
  static String enterDonationCompetitionRank(){return "{\"memo\":\"SUCCESS\"}";}
  static String enterFarm(String farmId,String uid){assert farmId.isEmpty()&&uid.equals("A");reads++;
   JSONArray cuisines=new JSONArray().put(cuisine("food",badCount==null?stock:badCount)).put(cuisine("other",otherStock));
   if(stockAlias)for(int i=0;i<cuisines.length();i++){JSONObject item=cuisines.getJSONObject(i);item.put("stock",item.get("count"));item.remove("count");}
   if(duplicate)cuisines.put(cuisine("food",1));
   return new JSONObject().put("memo","SUCCESS").put("cuisineList",cuisines).put("farmVO",new JSONObject().put("masterUserInfoVO",new JSONObject().put("userId",responseOwner)).put("harvestBenevolenceScore",harvested)
    .put("subFarmVO",new JSONObject().put("farmId","farmA").put("farmProduce",new JSONObject().put("benevolenceScore",progress)).put("animals",new JSONArray().put(new JSONObject().put("masterFarmId","farmA").put("animalStatusVO",new JSONObject().put("animalInteractStatus",home).put("animalFeedStatus",feed)))))).toString();}
  static String useFarmFood(JSONArray items){uses++;assert items.length()==1;JSONObject item=items.getJSONObject(0);int count=item.getInt("count");assert count>0&&count<=10&&item.getBoolean("useCuisine");batches.add(count);
   assert RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY).contains("\"pending\":true"):"reserve before RPC";
   assert Status.hasFlagToday(DYNAMIC_FOOD_PENDING);
   if(stockChange){if(item.getString("cuisineId").equals("food"))stock-=count;else otherStock-=count;}
   double delta=count*unit;if(effectChange)progress+=delta;
   if(switchOwner)UserIdMap.uid="B";if(cancel)throw new TaskCancelledException();if(failSaveAfter)RuntimeInfo.fail=true;
   if(unknown)return "malformed";
   JSONObject result=new JSONObject().put("memo",failed?"FAIL":"SUCCESS");if(!missingEffect)result.put("foodEffect",new JSONObject().put("deltaProduce",delta).put("targetProduce",progress));return result.toString();}
 }
 void harvestProduce(String id){assert id.equals(ownerFarmId);AntFarmRpcCall.harvests++;int ready=(int)AntFarmRpcCall.progress;AntFarmRpcCall.harvested+=ready;AntFarmRpcCall.progress-=ready;}
 static class ApplicationHook {static JSONObject args;static String requestString(String rpc,String data){assert rpc.equals("com.alipay.antfarm.useFarmFood");args=new JSONArray(data).getJSONObject(0);return "{}";}}
 static class ProductionRpc {static String VERSION="test";@@RPC@@}
 @@METHODS@@
 static void reset(FarmDynamicFoodCheck f){System.now=java.time.OffsetDateTime.parse("2026-10-07T19:58:00+08:00").toInstant().toEpochMilli();UserIdMap.uid=f.ownerUserId="A";f.ownerFarmId="farmA";f.enabled=f.useSpecialFood.n=f.dynamicSpecialFood.n=f.rankingDonation.n=f.rankingFoodRefill.n=true;f.dynamicFoodDailyLimit.n=20;f.useSpecialFoodCountLimit.n=0;f.harvestBenevolenceScore=f.benevolenceScore=0;
  liveWindow=true;rankQuota=20;badRank=false;liveActivity="season";liveRound="day";RuntimeInfo.accounts.clear();RuntimeInfo.fail=false;RuntimeInfo.writes=0;Status.flags.clear();Status.used=0;AntFarmRpcCall.stock=25;AntFarmRpcCall.otherStock=0;AntFarmRpcCall.uses=AntFarmRpcCall.reads=AntFarmRpcCall.harvests=0;AntFarmRpcCall.progress=AntFarmRpcCall.harvested=0;AntFarmRpcCall.unit=0.6;AntFarmRpcCall.stockChange=AntFarmRpcCall.effectChange=true;AntFarmRpcCall.unknown=AntFarmRpcCall.missingEffect=AntFarmRpcCall.failed=AntFarmRpcCall.switchOwner=AntFarmRpcCall.cancel=AntFarmRpcCall.failSaveAfter=AntFarmRpcCall.duplicate=AntFarmRpcCall.stockAlias=false;AntFarmRpcCall.home="HOME";AntFarmRpcCall.feed="EATING";AntFarmRpcCall.responseOwner="A";AntFarmRpcCall.badCount=null;AntFarmRpcCall.batches.clear();}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));FarmDynamicFoodCheck f=new FarmDynamicFoodCheck();RankingSnapshot rank=new RankingSnapshot();reset(f);
  JSONArray payload=new JSONArray().put(cuisine("x",3).put("useCuisine",true));ProductionRpc.useFarmFood(payload);assert ApplicationHook.args.getJSONArray("cuisineList").getJSONObject(0).getInt("count")==3;assert ApplicationHook.args.getString("requestType").equals("NORMAL");
  f.dynamicFoodDailyLimit.n=0;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);f.dynamicSpecialFood.n=false;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);f.enabled=false;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.batches.equals(List.of(1,10,9)):AntFarmRpcCall.batches;assert Status.used==20&&AntFarmRpcCall.stock==5;
  JSONObject state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY));assert state.getInt("used")==20&&!state.getBoolean("pending");assert Math.abs(state.getJSONObject("benefits").getDouble("book|food")-0.6)<0.000001;
  FarmDynamicFoodCheck restarted=new FarmDynamicFoodCheck();restarted.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==3;
  System.now+=86400000L;Status.used=0;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.batches.equals(List.of(1,10,9,5));
  reset(f);f.useSpecialFoodCountLimit.n=4;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.batches.equals(List.of(1,3))&&Status.used==4;
  reset(f);f.useDynamicSpecialFood(3,rank);assert AntFarmRpcCall.batches.equals(List.of(1,4)):AntFarmRpcCall.batches;assert f.harvestBenevolenceScore==3&&AntFarmRpcCall.stock==20;
  reset(f);AntFarmRpcCall.progress=2;f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0&&f.harvestBenevolenceScore==2;
  reset(f);f.rankingFoodRefill.n=false;f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);rank.weekly=true;f.useDynamicSpecialFood(3,rank);assert AntFarmRpcCall.batches.equals(List.of(1,4))&&f.harvestBenevolenceScore==3;rank.weekly=false;
  reset(f);liveWindow=false;f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);liveActivity="other";f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);liveRound="other";f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);badRank=true;f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);rankQuota=1;f.useDynamicSpecialFood(2,rank);assert AntFarmRpcCall.uses==0;
  reset(f);RuntimeInfo.fail=true;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);RuntimeInfo.getInstance().data.put(DYNAMIC_FOOD_KEY,"malformed");f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  for(String kind:List.of("unknown","missingEffect","stock","effect","failed","save")){
   reset(f);switch(kind){case "unknown":AntFarmRpcCall.unknown=true;break;case "missingEffect":AntFarmRpcCall.missingEffect=true;break;case "stock":AntFarmRpcCall.stockChange=false;break;case "effect":AntFarmRpcCall.effectChange=false;break;case "failed":AntFarmRpcCall.failed=true;break;case "save":AntFarmRpcCall.failSaveAfter=true;}
   f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1:kind;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1:kind;assert MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY)).getBoolean("pending");assert Status.used==0;
  }
  for(Object count:List.of("3",-1,1.5,JSONObject.NULL)) {reset(f);AntFarmRpcCall.badCount=count;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;}
  reset(f);AntFarmRpcCall.duplicate=true;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);AntFarmRpcCall.stockAlias=true;f.dynamicFoodDailyLimit.n=1;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1;
  reset(f);AntFarmRpcCall.feed="SLEEPY";f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);AntFarmRpcCall.home="STEALING";f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);AntFarmRpcCall.responseOwner="B";f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==0;
  reset(f);AntFarmRpcCall.switchOwner=true;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1;assert RuntimeInfo.getInstance().getString(DYNAMIC_FOOD_KEY).isEmpty();UserIdMap.uid="A";f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1;
  reset(f);AntFarmRpcCall.cancel=true;try{f.useDynamicSpecialFood(0,null);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert AntFarmRpcCall.uses==1;f.useDynamicSpecialFood(0,null);assert AntFarmRpcCall.uses==1;
  reset(f);Thread.currentThread().interrupt();try{f.useDynamicSpecialFood(0,null);throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}assert AntFarmRpcCall.uses==0;
  java.lang.System.out.println("PASS dynamic food: batch payload, probe1/learn/batch10, daily/legacy budgets, persistent reservations, strict owner/stock/effect readback, target harvest/replan, malformed/failure/cancellation");
 }
}
'''.replace("@@METHODS@@", "\n".join(method(farm,s) for s in signatures)).replace("@@RPC@@",method("model/task/antFarm/AntFarmRpcCall.java","public static String useFarmFood(JSONArray"))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-dynamic-food-") as directory:
 java=Path(directory)/"FarmDynamicFoodCheck.java";java.write_text(code,encoding="utf-8")
 env=dict(os.environ,JAVA_TOOL_OPTIONS="-Xms16m -Xmx192m")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",directory,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True,env=env)
 subprocess.run(["java","-ea","-cp",directory+os.pathsep+str(jar),"FarmDynamicFoodCheck"],check=True,env=env)
