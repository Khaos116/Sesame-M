"""Compile actual forest makeup flow/RPC and exercise GMT+8/calendar/consumption boundaries."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
forest="model/task/antForest/AntForestV2.java"
rpc="model/task/antForest/AntForestRpcCall.java"
source=(SOURCE/forest).read_text(encoding="utf-8")
assert 'new BooleanModelField("autoMakeUpSign"' in source,"Missing forest makeup option"
code=r'''
import org.json.*;
import java.util.*;
import java.time.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class MakeupCheck {
 static final String TAG="check";
 static class Field {boolean on=true; boolean getValue(){return on;}} Field autoMakeUpSign=new Field();
 static class MyUtils {
   static long now=Instant.parse("2026-02-28T17:00:00Z").toEpochMilli();
   static JSONObject newJSONObject(){return new JSONObject();}
   static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
   static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(now);return c;}
 }
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success")) || "FAIL".equals(j.optString("resultCode"));}}
 static class Log {static int ok;static void record(String s){}static void forest(String s){ok++;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ApplicationHook {
   static List<JSONObject> calls=new ArrayList<>();static boolean changed=true, malformed=false, fail=false;static int cards=2;
   static Map<String,JSONObject> days=new LinkedHashMap<>();
   static boolean crossDay;static int inventoryCalls;
   static String requestString(String name,String args){
     JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",name);calls.add(body);
     if(name.endsWith("listUserSingleSCAssets")){if(++inventoryCalls==2&&crossDay)MyUtils.now+=86400000L;return new JSONObject().put("success",true).put("userSCAssetsVO",new JSONObject().put("canUseSCAssetsIdList",cards>0?new JSONArray().put("a"):new JSONArray())).toString();}
     if(name.endsWith("signListPage"))return malformed?"garbage":new JSONObject().put("success",true).put("resData",new JSONObject().put("success",true).put("signModelList",new JSONArray(days.values()))).toString();
     if(name.endsWith("manualMakeUpSign")){if(changed&&!fail){days.get(body.optString("makeUpDate")).put("makeUpSigned",true);cards--;}return fail?"{\"success\":false}":"{\"success\":true}";}
     throw new AssertionError(name);
   }
 }
 static class AntForestRpcCall {@@RPC@@}
 @@METHODS@@
 static JSONObject day(String d,boolean signed,boolean makeup){return new JSONObject().put("signKey",d).put("signed",signed).put("makeUpSigned",makeup);}
 static void reset(){ApplicationHook.calls.clear();ApplicationHook.days.clear();ApplicationHook.cards=2;ApplicationHook.changed=true;ApplicationHook.malformed=false;ApplicationHook.fail=false;Status.flags.clear();Log.ok=0;TimeUtil.cancel=false;}
 static long mutations(){return ApplicationHook.calls.stream().filter(j->j.optString("rpc").endsWith("manualMakeUpSign")).count();}
 public static void main(String[] args){
   TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
   LocalDate today=LocalDate.of(2026,3,1);
   JSONArray rows=new JSONArray().put(day("2026-02-28",false,false)).put(day("2026-01-30",false,false)).put(day("2026-01-29",false,false))
    .put(day("2026-03-01",false,false)).put(day("2026-03-02",false,false)).put(day("2026-02-27",true,false)).put(day("2026-02-26",false,true))
    .put(day("2026-02-30",false,false)).put(new JSONObject().put("signKey","2026-02-25")).put(day("2026-02-24",false,false).put("canMakeUpSign",false));
   SortedSet<LocalDate> dates=missingForestSignDates(rows,today);assert dates.size()==2&&dates.first().equals(LocalDate.of(2026,1,30));
   assert missingForestSignDates(new JSONArray().put(day("2026-02-28",false,false)).put(day("2026-02-28",true,false)),today)==null;
   MakeupCheck f=new MakeupCheck();reset(); f.autoMakeUpSign.on=false;f.autoMakeUpSign();assert ApplicationHook.calls.isEmpty();f.autoMakeUpSign.on=true;
   reset();ApplicationHook.days.put("2026-01-30",day("2026-01-30",false,false));ApplicationHook.days.put("2026-02-28",day("2026-02-28",false,false));f.autoMakeUpSign();
   assert mutations()==2&&Log.ok==2;assert ApplicationHook.calls.stream().anyMatch(j->"2026-01".equals(j.optString("viewMonth")));
   List<JSONObject> actions=new ArrayList<>();for(JSONObject j:ApplicationHook.calls)if(j.optString("rpc").endsWith("manualMakeUpSign"))actions.add(j);
   assert actions.get(0).optString("makeUpDate").equals("2026-01-30"); assert actions.get(1).optString("sceneCode").equals("ANTFOREST_LIANXU_SIGN_2025");
   f.autoMakeUpSign();assert mutations()==2;
   reset();ApplicationHook.cards=0;f.autoMakeUpSign();assert mutations()==0;
   reset();ApplicationHook.inventoryCalls=0;ApplicationHook.crossDay=true;ApplicationHook.days.put("2026-01-30",day("2026-01-30",false,false));f.autoMakeUpSign();assert mutations()==0:"day changed, oldest date is now outside 30 days";ApplicationHook.crossDay=false;MyUtils.now=Instant.parse("2026-02-28T17:00:00Z").toEpochMilli();
   reset();ApplicationHook.malformed=true;f.autoMakeUpSign();assert mutations()==0;
   reset();ApplicationHook.days.put("2026-02-28",day("2026-02-28",false,false));ApplicationHook.changed=false;f.autoMakeUpSign();f.autoMakeUpSign();assert mutations()==1&&Log.ok==0;
   reset();ApplicationHook.days.put("2026-02-28",day("2026-02-28",false,false));ApplicationHook.fail=true;f.autoMakeUpSign();f.autoMakeUpSign();assert mutations()==1&&Log.ok==0;
   reset();TimeUtil.cancel=true;try{f.autoMakeUpSign();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert mutations()==0;
   reset();ApplicationHook.changed=false;AntForestRpcCall.manualMakeUpSign("quote\"date");assert ApplicationHook.calls.get(0).optString("makeUpDate").equals("quote\"date");
   System.out.println("PASS GMT+8, 30-day inclusive range across three months, oldest-first, eligibility/unknown/duplicates, default-off, inventory, readback, uncertain retry guard, cancellation and RPC escaping");
 }
}
'''
code=code.replace("@@METHODS@@","\n".join(method(forest,s) for s in ("static JSONObject forestSignPayload(","private static java.util.SortedSet<java.time.LocalDate> missingForestSignDates(","private JSONArray queryForestSignMonth(","private int forestMakeupCardCount(","private void autoMakeUpSign(")))
code=code.replace("@@RPC@@","\n".join(method(rpc,s) for s in ("public static String listForestMakeupCards(","public static String queryForestSignMonth(","public static String manualMakeUpSign(")))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-makeup-") as tmp:
 java=Path(tmp)/"MakeupCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"MakeupCheck"],check=True)
