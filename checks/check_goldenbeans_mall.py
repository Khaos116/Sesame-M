"""Replay the real mall implementation with unpaid/malformed/unknown exchange responses."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

code = r'''
import org.json.*;import java.util.*;import java.math.BigDecimal;
public class MallCheck {
 static class System {static long now=java.time.Instant.parse("2026-10-09T04:00:00Z").toEpochMilli();
  static long currentTimeMillis(){return now;}static final java.io.PrintStream out=java.lang.System.out;}
 static class TaskCancelledException extends RuntimeException {}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean isRetryable(JSONObject j){return j.optBoolean("retryable");}
  static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("code"));}}
 static class GoldenBeansSupport {static String TAG="mall";static int lastInterval;static void pause(int i){lastInterval=i;}
  static JSONObject parse(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
  static boolean ok(JSONObject j){return j.optBoolean("success");}static String describe(JSONObject j){return "failed";}
  static JSONObject findObject(JSONObject j,String k){return j.optJSONObject(k);}}
 static class Log {static void goldenBeans(String s){}static void record(String s){}static void i(String a,String b){}
  static void printStackTrace(String a,Throwable t){throw new AssertionError(t);}}
 static class Status {static Map<String,Integer> counts=new HashMap<>();static Set<String> flags=new HashSet<>();
  static void flagToday(String k){flags.add(k);}static boolean hasFlagToday(String k){return flags.contains(k);}
  static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int n){counts.put(k,n);}}
 static class RuntimeInfo {static RuntimeInfo instance=new RuntimeInfo();Map<String,String> receipts=new HashMap<>();boolean writable=true;
  static RuntimeInfo getInstance(){return instance;}String getString(String k){return receipts.getOrDefault(k,"");}
  boolean putVerified(String k,String s){if(!writable)return false;receipts.put(k,s);return true;}}
 static class GoldenBeansMallItemMap {static void load(){}static String get(String k){return null;}
  static void add(String a,String b){}static boolean save(){return true;}}
 static class AlipayGoldenBeansMallItem {static void clear(){}}
 static class ApplicationHook {static JSONObject args;static int writes,reads;
  static String requestString(String rpc,String body){return requestString(rpc,body,3,-1);}
  static String requestString(String rpc,String body,int tries,int pause){args=new JSONArray(body).optJSONObject(0);
   if("com.alipay.antcommonweal.exchange.h5.exchangeBenefit".equals(rpc)){
    assert tries==1&&pause==0:"mall exchange used default write retries";writes++;
   }else{assert tries==3&&pause==-1:"mall query retry policy changed";reads++;}return "{}";}}
 static class TaskAlternative {static String request(String a,String b,String c){throw new AssertionError("unexpected fallback");}}
 @@ACTUAL_RPC@@
 static class goldenbeansRpcCall {
  static String items,reply;static int writes,queries,catalogues,jars=5,history=0,failedPage=0;
  static boolean orderFailure,delayed,repeatPages,crossDayOnOrders;static Object rawJars;
  static String mallItems(int a,int b){catalogues++;return items;}
  static String home(){return new JSONObject().put("success",true).put("jarInfo",new JSONObject().put("jarCount",rawJars==null?jars:rawJars)).toString();}
  static String mallOrders(int a,int b){queries++;if(orderFailure||a==failedPage)return "broken";
   if(crossDayOnOrders){System.now+=86400000L;crossDayOnOrders=false;}
   if(repeatPages)a=1;JSONArray orders=new JSONArray();int count=history+(!delayed?writes:0);
   for(int i=(a-1)*b;i<Math.min(a*b,count);i++)orders.put(new JSONObject().put("orderNo",i<history?"old"+i:"new"+(i-history+1)));
   return new JSONObject().put("success",true).put("orderInfos",orders).toString();}
  static String mallExchange(String a,String b){writes++;return reply.replace("\"new\"","\"new"+writes+"\"");}
 }
 @@MALL@@
 static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
 static class GoldenModule {
  Field<Integer> executeInterval=new Field<>(null);
  Field<Map<String,Integer>> GoldenBeansMallItemList=new Field<>(Map.of("coupon",1));
  @@SUPPORTS@@
  @@MANUAL@@
 }
 static JSONObject sku;
 static void reset(){goldenbeansRpcCall.writes=0;goldenbeansRpcCall.queries=0;goldenbeansRpcCall.jars=5;
  goldenbeansRpcCall.orderFailure=false;goldenbeansRpcCall.delayed=false;goldenbeansRpcCall.history=0;
  goldenbeansRpcCall.failedPage=0;goldenbeansRpcCall.catalogues=0;goldenbeansRpcCall.repeatPages=false;goldenbeansRpcCall.crossDayOnOrders=false;goldenbeansRpcCall.rawJars=null;
  goldenbeansRpcCall.reply="{\"success\":true,\"canBuy\":true,\"orderNo\":\"new\"}";
  RuntimeInfo.instance=new RuntimeInfo();Status.counts.clear();Status.flags.clear();
  sku=new JSONObject().put("skuId","sku").put("price",new JSONObject().put("cent",100)).put("userDayLeftAmount",2);}
 static void run(boolean enabled){goldenbeansRpcCall.items=new JSONObject().put("success",true).put("itemInfoVOList",new JSONArray()
  .put(new JSONObject().put("spuId","spu").put("spuName","coupon").put("skuModelList",new JSONArray().put(sku)))).toString();
  GoldenBeansMall.run(0,Map.of("coupon",1),enabled);}
 public static void main(String[] args)throws Exception{
  ActualMallRpc.mallItems(0,20);assert ApplicationHook.args.optInt("startIndex")==0
   &&"babafarm".equals(ApplicationHook.args.optString("subChannel"));
  ActualMallRpc.mallOrders(2,20);assert ApplicationHook.args.optInt("pageNum")==2
   &&"antorchard".equals(ApplicationHook.args.optString("source"));
  String escaped="quoted\"\\\n";ActualMallRpc.mallExchange(escaped,escaped);
  assert ApplicationHook.writes==1&&ApplicationHook.reads==2;
  assert escaped.equals(ApplicationHook.args.optString("spuId"))&&escaped.equals(ApplicationHook.args.optString("skuId"));
  reset();run(false);assert goldenbeansRpcCall.writes==0;
  run(false);assert goldenbeansRpcCall.catalogues==1;
  reset();run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.values().contains(1);
  run(true);assert goldenbeansRpcCall.writes==1;
  for(Object money:List.of(new JSONObject().put("cent",1),new JSONObject(),"malformed")){
   reset();sku.put("moneyPrice",money);run(true);assert goldenbeansRpcCall.writes==0;}
  for(Object invalid:List.of(0.5,"0.5",4294967296L,"4294967296",true,JSONObject.NULL)){
   reset();sku.put("moneyPrice",new JSONObject().put("cent",invalid));run(true);
   assert goldenbeansRpcCall.writes==0:"invalid money price submitted: "+invalid;
   reset();sku.optJSONObject("price").put("cent",invalid);run(true);
   assert goldenbeansRpcCall.writes==0:"invalid jar price submitted: "+invalid;
   reset();sku.put("userDayLeftAmount",invalid);run(true);
   assert goldenbeansRpcCall.writes==0:"invalid daily eligibility submitted: "+invalid;
   reset();goldenbeansRpcCall.rawJars=invalid;run(true);
   assert goldenbeansRpcCall.writes==0:"invalid jar balance submitted: "+invalid;
  }
  reset();sku.optJSONObject("price").put("cent",100.5);run(true);assert goldenbeansRpcCall.writes==0;
  reset();goldenbeansRpcCall.rawJars=1.5;run(true);assert goldenbeansRpcCall.writes==0;
  reset();sku.put("moneyPrice",new JSONObject().put("cent","0"));sku.optJSONObject("price").put("cent","100");
  sku.put("userDayLeftAmount","2");goldenbeansRpcCall.rawJars="5";run(true);assert goldenbeansRpcCall.writes==1;
  reset();sku.remove("price");run(true);assert goldenbeansRpcCall.writes==0;
  reset();sku.optJSONObject("price").put("cent",-1);run(true);assert goldenbeansRpcCall.writes==0;
  reset();sku.remove("userDayLeftAmount");run(true);assert goldenbeansRpcCall.writes==0;
  reset();goldenbeansRpcCall.jars=-1;run(true);assert goldenbeansRpcCall.writes==0;
  reset();goldenbeansRpcCall.orderFailure=true;run(true);assert goldenbeansRpcCall.writes==0;
  reset();RuntimeInfo.instance.writable=false;run(true);assert goldenbeansRpcCall.writes==0;
  for(int history:List.of(20,25)){reset();goldenbeansRpcCall.history=history;run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.values().contains(1);}
  reset();goldenbeansRpcCall.history=25;goldenbeansRpcCall.failedPage=2;run(true);assert goldenbeansRpcCall.writes==0;
  reset();goldenbeansRpcCall.history=25;goldenbeansRpcCall.repeatPages=true;run(true);assert goldenbeansRpcCall.writes==0;
  reset();goldenbeansRpcCall.delayed=true;run(true);assert Status.counts.isEmpty();
  goldenbeansRpcCall.delayed=false;run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.values().contains(1);
  run(true);assert Status.counts.values().contains(1)&&goldenbeansRpcCall.writes==1;
  reset();goldenbeansRpcCall.delayed=true;run(true);String key="goldenBeans.mall.pending::spu::sku";
  JSONObject old=new JSONObject(RuntimeInfo.instance.getString(key));old.put("day",old.optLong("day")-1);
  RuntimeInfo.instance.putVerified(key,old.toString());goldenbeansRpcCall.delayed=false;run(true);
  assert Status.counts.isEmpty()&&RuntimeInfo.instance.getString(key).isEmpty()&&goldenbeansRpcCall.writes==1;
  reset();goldenbeansRpcCall.reply="{\"success\":false,\"code\":\"NO_BALANCE\"}";run(true);
  assert RuntimeInfo.instance.receipts.values().stream().allMatch(String::isEmpty);run(true);assert goldenbeansRpcCall.writes==1;
  Status.flags.clear();run(true);assert goldenbeansRpcCall.writes==2;
  reset();goldenbeansRpcCall.reply="{\"success\":false,\"code\":\"102\"}";run(true);run(true);assert goldenbeansRpcCall.writes==1;
  reset();goldenbeansRpcCall.reply="broken";run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.isEmpty();
  run(true);assert goldenbeansRpcCall.writes==1;Status.counts.clear();run(true);assert goldenbeansRpcCall.writes==1;
  sku.put("userDayLeftAmount",1);run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.values().contains(1);
  // Unchanged counts across midnight cannot establish non-submission; only explicit manual recovery releases it.
  reset();goldenbeansRpcCall.reply="broken";goldenbeansRpcCall.delayed=true;run(true);
  int queries=goldenbeansRpcCall.queries;
  System.now+=86400000L;run(true);assert goldenbeansRpcCall.writes==1&&!RuntimeInfo.instance.getString(key).isEmpty();
  goldenbeansRpcCall.history=1;run(true);assert goldenbeansRpcCall.writes==1&&goldenbeansRpcCall.queries==queries;
  String pending=RuntimeInfo.instance.getString(key);
  GoldenBeansMall.clearSelectedReceipts(0,Map.of("other",1));assert RuntimeInfo.instance.getString(key).equals(pending);
  RuntimeInfo.instance.writable=false;GoldenBeansMall.clearSelectedReceipts(0,Map.of("coupon",1));
  assert RuntimeInfo.instance.getString(key).equals(pending);RuntimeInfo.instance.writable=true;
  GoldenBeansMall.clearSelectedReceipts(0,Map.of("coupon",1));assert goldenbeansRpcCall.writes==1&&RuntimeInfo.instance.getString(key).isEmpty();
  run(true);assert goldenbeansRpcCall.writes==2;
  reset();run(false);RuntimeInfo.instance.putVerified(key,"submitted");
  GoldenBeansMall.clearSelectedReceipts(0,Map.of("coupon",1));assert goldenbeansRpcCall.writes==0&&RuntimeInfo.instance.getString(key).isEmpty();
  run(true);assert goldenbeansRpcCall.writes==1;
  // Two successful items need one initial baseline and two fresh confirmations, not four complete scans.
  reset();run(false);JSONObject catalogue=new JSONObject(goldenbeansRpcCall.items);
  catalogue.optJSONArray("itemInfoVOList").put(new JSONObject().put("spuId","spu2").put("spuName","coupon2")
   .put("skuModelList",new JSONArray().put(sku)));goldenbeansRpcCall.items=catalogue.toString();
  GoldenBeansMall.run(0,Map.of("coupon",1,"coupon2",1),true);
  assert goldenbeansRpcCall.writes==2&&goldenbeansRpcCall.queries==3;
  JSONArray[] stale={new JSONArray()};goldenbeansRpcCall.orderFailure=true;
  assert !GoldenBeansMall.reconcile(0,new JSONObject().put("spuName","coupon"),key,new JSONObject().put("orderNo","unknown"),stale);
  assert stale[0]==null;
  reset();goldenbeansRpcCall.history=1;goldenbeansRpcCall.crossDayOnOrders=true;
  JSONObject receipt=new JSONObject().put("orderNo","old0").put("day",Math.floorDiv(System.now+28800000L,86400000L));
  assert GoldenBeansMall.reconcile(0,new JSONObject().put("spuName","coupon"),key,receipt,stale);
  assert Status.counts.isEmpty();
  // The five-submission cap applies even when every response is unknown.
  reset();run(false);JSONArray many=new JSONArray();Map<String,Integer> selected=new LinkedHashMap<>();
  for(int i=0;i<13;i++){String name="coupon"+i;selected.put(name,1);many.put(new JSONObject().put("spuId","spu"+i).put("spuName",name).put("skuModelList",new JSONArray().put(sku)));}
  goldenbeansRpcCall.items=new JSONObject().put("success",true).put("itemInfoVOList",many).toString();
  goldenbeansRpcCall.reply="broken";goldenbeansRpcCall.delayed=true;GoldenBeansMall.run(0,selected,true);
  assert goldenbeansRpcCall.writes==5:"unknown responses bypassed single-run submission cap";
  // Six old receipts only require readback, leaving the five new submissions available.
  reset();goldenbeansRpcCall.items=new JSONObject().put("success",true).put("itemInfoVOList",many).toString();goldenbeansRpcCall.history=1;
  for(int i=0;i<6;i++)RuntimeInfo.instance.putVerified("goldenBeans.mall.pending::spu"+i+"::sku",new JSONObject()
   .put("orderNo","old0").put("day",Math.floorDiv(System.now+28800000L,86400000L)).put("doneBefore",0).toString());
  GoldenBeansMall.run(0,selected,true);assert goldenbeansRpcCall.writes==5;
  assert Status.counts.get("goldenBeans::mallExchange::coupon5")==1;
  reset();run(false);GoldenModule module=new GoldenModule();
  for(Integer interval:Arrays.asList(null,-1,1200)) {
   module.executeInterval.value=interval;RuntimeInfo.instance.putVerified(key,"submitted");
   module.runManualAction("clearMallReceipts");assert RuntimeInfo.instance.getString(key).isEmpty();
   assert goldenbeansRpcCall.writes==0&&GoldenBeansSupport.lastInterval==(interval!=null&&interval>500?interval:500);
  }
  RuntimeInfo.instance.putVerified(key,"submitted");module.runManualAction("invalid");assert RuntimeInfo.instance.getString(key).equals("submitted");
  System.out.println("PASS real mall: opt-in, catalogue caching, paid/unknown price exclusion, full paginated orders, explicit rejection, delayed receipt recovery and durable unknown receipt");
 }
}
'''.replace("@@MALL@@", method("model/task/goldenbeans/GoldenBeansMall.java", "public final class GoldenBeansMall").replace(
    "public final class GoldenBeansMall", "static final class GoldenBeansMall", 1))
code=code.replace("@@ACTUAL_RPC@@",method("model/task/goldenbeans/goldenbeansRpcCall.java","public class goldenbeansRpcCall").replace(
    "public class goldenbeansRpcCall", "static class ActualMallRpc", 1).replace("private goldenbeansRpcCall()", "private ActualMallRpc()"))
code=code.replace("@@SUPPORTS@@",method("model/task/goldenbeans/goldenbeans.java","protected boolean supportsManualAction("))
code=code.replace("@@MANUAL@@",method("model/task/goldenbeans/goldenbeans.java","protected void runManualAction("))
ui=(SOURCE / "ui/miuix/MiuixGroupFieldsActivity.kt").read_text(encoding="utf-8")
assert '"goldenbeans" -> listOf(' in ui and '"clearMallReceipts"' in ui and '.setNegativeButton("取消", null)' in ui
assert 'selectedNames.size' in ui and 'selectedNames.joinToString("\\n")' in ui and 'currentNames != selectedNames' in ui
assert '"clearMallReceipts".equals(action)' in method("model/task/goldenbeans/goldenbeans.java", "protected boolean supportsManualAction(")
assert "GoldenBeansMall.clearSelectedReceipts" in method("model/task/goldenbeans/goldenbeans.java", "protected void runManualAction(")
jar = next(p for p in (Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
    "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
    if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-mall-") as tmp:
    java = Path(tmp) / "MallCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "MallCheck"], check=True)
