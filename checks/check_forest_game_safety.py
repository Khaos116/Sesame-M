"""Replay actual forest game methods with malformed quotas and cancellation."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

game = "model/task/antForest/AntForestGame.java"
code = r'''
import org.json.*;import java.util.*;import java.util.concurrent.CancellationException;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestGameSafety {
 static final String TAG="test";
 static class Field {int value;Field(int n){value=n;}int getValue(){return value;}}
 static class Host {Field whackModeName=new Field(0),earliestwhackMoleTime=new Field(0),whackModeCount=new Field(15),whackModeGames=new Field(5);Map<String,JSONObject> skuInfo=new HashMap<>();Mall mallHelper=new Mall(this);}
 Host host=new Host();
 static class whackModeNames {static final int CLOSE=0;}
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static boolean closeVerification(){return false;}static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}
 static class Log {static int errors;static void record(String s){}static void forest(String s){}static void error(String s){}static void err(String t,String s,Throwable e){errors++;}static void printStackTrace(String s,Throwable t){}static void markUnresolved(){} }
 static String cardCancel="";static int exchangeCalls,propQueries,consumeCalls,sleepCalls,detailCalls;static boolean ordinaryError,exchangeReject;
 static class ForestPropSupport {static boolean hasUnconfirmedRefill(String id){return false;}}
 static class ForestExpiringProps {static boolean hasUnconfirmed(String group){return false;}}
 static class VitalityBenefitIdMap {static void save(String uid){}}
 static class UserIdMap {static String getCurrentUid(){return "self";}}
 static class AntForestV2 {static final int VITALITY_EXCHANGE_RETRY_MAX=3;}
 enum ItemStatus {REACH_LIMIT,NO_ENOUGH_POINT,NO_ENOUGH_STOCK;String nickName(){return name();}}
 static class Mall {Host host;Mall(Host h){host=h;}boolean getAllSkuInfo(){return true;}boolean getSkuInfoByItemInfoVO(JSONObject j){return false;}@@MALL@@}
 enum PropGroup {energyRain}
 static class AntForestProp {
  static List<JSONObject> getPropGroup(JSONArray a,String group){return List.of(a.optJSONObject(0));}
  @@PROP@@
 }
 static class TimeUtil {static void sleep(long n){sleepCalls++;if(n==2000&&"wait2000".equals(cardCancel)||n==1000&&"wait1000".equals(cardCancel))throw new TaskCancelledException();}}
 enum Outcome {RETRY,UNABLE,DONE}
 static class Toast {static void show(String s){}}
 static class Statistics {enum DataType {COLLECTED}static int total,calls;static void addData(DataType t,int n){total+=n;calls++;}}
 static class Status {static boolean flag;static boolean hasFlagToday(String s){return s.equals("forest::vitalitySkuList")||flag;}static void flagToday(String s){flag=true;}static int getIntFlagToday(String s){return 0;}static void setIntFlagToday(String s,int n){}static void vitalityExchangeBenefitToday(String s){}static int getVitalityExchangeBenefitCountToday(String s){return 1;}}
 static class WhackMole {enum Mode {COMPATIBLE,AGGRESSIVE}static void setTotalGames(int n){}static void setMoleCount(int n){}static void start(Mode m){throw new TaskCancelledException();}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean checkResultCode(JSONObject j){return checkResultCode("",j);}static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}static boolean isRetryable(JSONObject j){return false;}static boolean isServerBusy(JSONObject j){return false;}}
 static class AntForestRpcCall {
  static String rights,draw;static String cancel;static int queries,draws;
  static String queryGameList(){queries++;if("query".equals(cancel))throw new TaskCancelledException();return rights;}
  static String drawGameCenterAward(int n){draws++;if("draw".equals(cancel))throw new TaskCancelledException();return draw;}
  static String exchangeBenefit(String a,String b){exchangeCalls++;if("exchange".equals(cardCancel))throw new TaskCancelledException();if(ordinaryError)throw new IllegalStateException();return exchangeReject?"{}":"{resultCode:'SUCCESS'}";}
  static String itemDetail(String id){detailCalls++;if("detail".equals(cardCancel))throw new TaskCancelledException();return "{}";}
  static String queryPropList(boolean x){propQueries++;if("query".equals(cardCancel))throw new TaskCancelledException();return "{resultCode:'SUCCESS',forestPropVOList:[{propIdList:['p'],propType:'t',propGroup:'energyRain',propConfigVO:{propName:'rain'}}]}";}
  static String consumeProp(String a,String b,String c){consumeCalls++;if("consume".equals(cardCancel))throw new TaskCancelledException();return "{}";}
 }
 static class GameTask {static class Forest_slxcc {static int reports,count;static void report(String s,int n){reports++;count=n;if("report".equals(AntForestRpcCall.cancel))throw new TaskCancelledException();}}}
 @@FEATURE_LONG@@
 @@GAME@@
 @@WHACK@@
 @@BOX@@
 @@RAIN_CARD@@
 static void reset(){AntForestRpcCall.queries=AntForestRpcCall.draws=0;AntForestRpcCall.cancel="";Statistics.total=Statistics.calls=GameTask.Forest_slxcc.reports=GameTask.Forest_slxcc.count=0;Status.flag=false;}
 static JSONObject rights(){return new JSONObject().put("quotaPerTime",100).put("quotaCanUse",200).put("quotaLimit",500).put("usedQuota",100);}
 static void response(JSONObject rights){AntForestRpcCall.rights=new JSONObject().put("resultCode","SUCCESS").put("gameCenterDrawRights",rights).toString();AntForestRpcCall.draw="{resultCode:'SUCCESS',gameCenterDrawAwardList:[{awardType:'ENERGY',awardName:'energy',awardCount:7}]}";}
 public static void main(String[] args){ForestGameSafety f=new ForestGameSafety();
  f.host.skuInfo.put("SK20250117005985",new JSONObject().put("skuName","rain").put("spuId","spu"));
  for(String stage:List.of("exchange","detail","wait2000","wait1000","query","consume")){
   if(args.length>0&&args[0].startsWith("card:")&&!args[0].equals("card:"+stage))continue;
   reset();exchangeCalls=propQueries=consumeCalls=sleepCalls=detailCalls=0;cardCancel=stage;
   exchangeReject=stage.equals("detail");
   try{f.useEnergyRainCard();throw new AssertionError("rain card cancellation swallowed: "+stage);}catch(TaskCancelledException expected){}
   assert exchangeCalls==1&&propQueries==(stage.equals("query")||stage.equals("consume")?1:0)&&consumeCalls==(stage.equals("consume")?1:0)&&!Status.flag:stage;
  }
  cardCancel="";exchangeReject=false;exchangeCalls=propQueries=consumeCalls=sleepCalls=0;f.useEnergyRainCard();assert exchangeCalls==1&&propQueries==1&&consumeCalls==1&&sleepCalls==2;
  ordinaryError=true;Log.errors=0;detailCalls=0;f.useEnergyRainCard();ordinaryError=false;assert Log.errors==1&&detailCalls==1:"ordinary exchange failure handling changed";
  if(args.length==0)for(String phase:List.of("query","draw","report")){reset();response(rights());AntForestRpcCall.cancel=phase;try{f.doforestgame();throw new AssertionError("cancel swallowed: "+phase);}catch(TaskCancelledException expected){}assert AntForestRpcCall.draws==(phase.equals("query")?0:1);assert GameTask.Forest_slxcc.reports==(phase.equals("report")?1:0);}
  for(Object invalid:List.of(0,-1,0.5,"100",true,2147483648L)){reset();JSONObject r=rights().put("quotaPerTime",invalid);response(r);f.doforestgame();assert AntForestRpcCall.draws==0&&GameTask.Forest_slxcc.reports==0&&Statistics.calls==0:"invalid quotaPerTime: "+invalid;}
  for(String key:List.of("quotaCanUse","quotaLimit","usedQuota"))for(Object invalid:List.of(-1,0.5,"100",true,2147483648L)){reset();response(rights().put(key,invalid));f.doforestgame();assert AntForestRpcCall.draws==0&&GameTask.Forest_slxcc.reports==0&&Statistics.calls==0:"invalid "+key+":"+invalid;}
  for(String key:List.of("quotaCanUse","quotaLimit","usedQuota")){reset();JSONObject r=rights();r.remove(key);response(r);f.doforestgame();assert AntForestRpcCall.draws==0&&GameTask.Forest_slxcc.reports==0&&Statistics.calls==0:"missing "+key;}
  for(Object invalid:List.of(0,-1,0.5,"100",true,2147483648L)){reset();response(rights().put("quotaPerTime",invalid));assert f.openForestLeyuanBoxes("test")==Outcome.RETRY&&AntForestRpcCall.draws==0;}
  reset();response(rights());AntForestRpcCall.draw="{resultCode:'SUCCESS',gameCenterDrawRights:{quotaCanUse:100.5}}";assert f.openForestLeyuanBoxes("test")==Outcome.RETRY&&AntForestRpcCall.draws==1:"fractional readback confirmed progress";
  reset();response(rights());f.doforestgame();assert AntForestRpcCall.draws==1&&Statistics.total==7&&GameTask.Forest_slxcc.reports==1&&GameTask.Forest_slxcc.count==4;
  reset();JSONObject legacy=rights();legacy.remove("quotaPerTime");response(legacy);f.doforestgame();assert AntForestRpcCall.draws==1&&Statistics.total==7&&GameTask.Forest_slxcc.count==4:"missing quotaPerTime legacy default";
  reset();f.checkAndHandleWhackMole();assert !Status.flag:"closed whack marked complete";
  f.host.whackModeName.value=1;try{f.checkAndHandleWhackMole();throw new AssertionError("whack cancellation swallowed");}catch(TaskCancelledException expected){}assert !Status.flag;
  System.out.println("PASS forest game cancellation, strict quotas, legacy default, rewards and closed whack");
 }
}
'''
code = code.replace("@@GAME@@", method(game, "public void doforestgame("))
code = code.replace("@@WHACK@@", method(game, "void checkAndHandleWhackMole("))
code = code.replace("@@BOX@@", method(game, "Outcome openForestLeyuanBoxes("))
code = code.replace("@@RAIN_CARD@@", method(game, "void useEnergyRainCard("))
prop = "\n".join(method("model/task/antForest/AntForestProp.java", s) for s in ("public static JSONArray getForestPropVOList(", "public static Boolean consumeProp(JSONObject", "static Boolean consumeProp(String"))
mall = "\n".join(method("model/task/antForest/AntForestMall.java", s) for s in ("void getSkuInfoBySpuId(", "Boolean exchangeBenefit(String skuId)", "public static Boolean exchangeBenefit(String spuId, String skuId, String skuName)", "static Boolean exchangeBenefit(String spuId, String skuId)"))
if "--old-helpers" in sys.argv:
    prop = prop.replace("if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;", "")
    mall = mall.replace("if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;", "")
code = code.replace("@@PROP@@", prop).replace("@@MALL@@", mall)
code = code.replace("@@FEATURE_LONG@@", method(game, "private static int gameQuota("))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-forest-game-safety-") as temp:
    source = Path(temp) / "ForestGameSafety.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temp, str(source), str(SOURCE / "util/TaskCancelledException.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp + os.pathsep + str(jar), "ForestGameSafety", *[arg for arg in sys.argv[1:] if arg != "--old-helpers"]], check=True)
