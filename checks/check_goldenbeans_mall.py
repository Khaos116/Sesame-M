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
import org.json.*;import java.util.*;
public class MallCheck {
 static class TaskCancelledException extends RuntimeException {}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean isRetryable(JSONObject j){return j.optBoolean("retryable");}
  static boolean isServerBusy(JSONObject j){return "102".equals(j.optString("code"));}}
 static class GoldenBeansSupport {static String TAG="mall";static void pause(int i){}
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
 static class goldenbeansRpcCall {
  static String items,reply;static int writes,queries,catalogues,jars=5,history=0,failedPage=0;
  static boolean orderFailure,delayed,repeatPages;
  static String mallItems(int a,int b){catalogues++;return items;}
  static String home(){return new JSONObject().put("success",true).put("jarInfo",new JSONObject().put("jarCount",jars)).toString();}
  static String mallOrders(int a,int b){queries++;if(orderFailure||a==failedPage)return "broken";
   if(repeatPages)a=1;JSONArray orders=new JSONArray();int count=history+(writes>0&&!delayed?1:0);
   for(int i=(a-1)*b;i<Math.min(a*b,count);i++)orders.put(new JSONObject().put("orderNo",i<history?"old"+i:"new"));
   return new JSONObject().put("success",true).put("orderInfos",orders).toString();}
  static String mallExchange(String a,String b){writes++;return reply;}
 }
 @@MALL@@
 static JSONObject sku;
 static void reset(){goldenbeansRpcCall.writes=0;goldenbeansRpcCall.queries=0;goldenbeansRpcCall.jars=5;
  goldenbeansRpcCall.orderFailure=false;goldenbeansRpcCall.delayed=false;goldenbeansRpcCall.history=0;
  goldenbeansRpcCall.failedPage=0;goldenbeansRpcCall.catalogues=0;goldenbeansRpcCall.repeatPages=false;
  goldenbeansRpcCall.reply="{\"success\":true,\"canBuy\":true,\"orderNo\":\"new\"}";
  RuntimeInfo.instance=new RuntimeInfo();Status.counts.clear();Status.flags.clear();
  sku=new JSONObject().put("skuId","sku").put("price",new JSONObject().put("cent",100)).put("userDayLeftAmount",2);}
 static void run(boolean enabled){goldenbeansRpcCall.items=new JSONObject().put("success",true).put("itemInfoVOList",new JSONArray()
  .put(new JSONObject().put("spuId","spu").put("spuName","coupon").put("skuModelList",new JSONArray().put(sku)))).toString();
  GoldenBeansMall.run(0,Map.of("coupon",1),enabled);}
 public static void main(String[] args){
  reset();run(false);assert goldenbeansRpcCall.writes==0;
  run(false);assert goldenbeansRpcCall.catalogues==1;
  reset();run(true);assert goldenbeansRpcCall.writes==1&&Status.counts.values().contains(1);
  run(true);assert goldenbeansRpcCall.writes==1;
  for(Object money:List.of(new JSONObject().put("cent",1),new JSONObject(),"malformed")){
   reset();sku.put("moneyPrice",money);run(true);assert goldenbeansRpcCall.writes==0;}
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
  System.out.println("PASS real mall: opt-in, catalogue caching, paid/unknown price exclusion, full paginated orders, explicit rejection, delayed receipt recovery and durable unknown receipt");
 }
}
'''.replace("@@MALL@@", method("model/task/goldenbeans/GoldenBeansMall.java", "public final class GoldenBeansMall").replace(
    "public final class GoldenBeansMall", "static final class GoldenBeansMall", 1))
jar = next(p for p in (Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
    "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
    if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-mall-") as tmp:
    java = Path(tmp) / "MallCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "MallCheck"], check=True)
