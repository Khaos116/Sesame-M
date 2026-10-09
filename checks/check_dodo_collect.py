"""Replay actual Dodo daily collection methods; success flags require server confirmation."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

code = r'''
import org.json.*;import java.util.*;import java.util.concurrent.TimeUnit;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class DodoCollectCheck {
 static final String TAG="check";
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class TaskAttemptPolicy {enum ProbeResult {TODO,FINISHED,RECEIVED,GONE,UNKNOWN}}
 static class Log { static void record(String s) {}static void forest(String s){}static void err(String t,String s,Throwable e){}static long timeToStamp(String s){try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss");f.setTimeZone(TimeZone.getTimeZone("GMT+8"));return f.parse(s).getTime();}catch(Exception e){return System.currentTimeMillis();}}}
 static class AntDodoRpcCall {
  static String taskList(){throw new TaskCancelledException();}
  static Queue<String> states=new ArrayDeque<>();static boolean fail,cancel,homeFail;static int queries,draws;static int quota=1;static String date="";
  static String queryAnimalStatus(){queries++;return states.remove();}
  static String homePage(){return new JSONObject().put("resultCode",homeFail?"FAIL":"SUCCESS").put("data",new JSONObject().put("animalBook",new JSONObject().put("endDate",date)).put("limit",new JSONArray().put(new JSONObject().put("actionCode","DAILY_COLLECT").put("leftFreeQuota",quota)))).toString();}
  static String collect(){draws++;if(cancel)throw new TaskCancelledException();return new JSONObject().put("resultCode",fail?"FAIL":"SUCCESS").put("data",new JSONObject().put("animal",new JSONObject().put("name","card"))).toString();}
  static String propList(){if(++propQueries>3)throw new AssertionError("Unbounded unchanged inventory loop");return new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("propList",new JSONArray().put(prop))).toString();}
 }
 private static String getAnimalInfo(JSONObject animal){return "";}
 private void checkAnimalAndGiftToFriend(JSONObject animal){}
 static class Field<T>{T value;Field(T v){value=v;}T getValue(){return value;}}
 Field<Set<String>> usePropList=new Field<>(new HashSet<>());Field<Integer> useCollectTimingType=new Field<>(0);
 enum PropGroup {UNIVERSAL_CARD,COLLECT_ANIMAL,COLLECT_HISTORY_ANIMAL}static class TimingType {static final int LAST_DAY=1;}
 static JSONObject prop;static int consumptions,propQueries;
 private Boolean consumeProp(String id,String type){consumptions++;return true;}
 private Boolean usePropUniversalCard(String id,String type){return consumeProp(id,type);}
 static JSONObject prop(long expiry,int stock){return new JSONObject().put("propType","history").put("propConfig",new JSONObject().put("propGroup","COLLECT_HISTORY_ANIMAL")).put("propIdList",new JSONArray().put("id")).put("recentExpireTime",expiry).put("holdsNum",stock);}
 @@METHODS@@
 static String state(boolean done){return new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("collect",done)).toString();}
 static void reset(){Status.flags.clear();AntDodoRpcCall.states.clear();AntDodoRpcCall.fail=AntDodoRpcCall.cancel=false;AntDodoRpcCall.queries=AntDodoRpcCall.draws=0;AntDodoRpcCall.quota=1;}
 public static void main(String[] args){DodoCollectCheck d=new DodoCollectCheck();
  try{d.probeDodoStatus("scene","task");throw new AssertionError("Task probe swallowed cancellation");}catch(TaskCancelledException expected){}
  reset();AntDodoRpcCall.states.add(state(true));d.collect();d.collect();assert Status.hasFlagToday("dodo::collect")&&AntDodoRpcCall.queries==1&&AntDodoRpcCall.draws==0;
  reset();AntDodoRpcCall.fail=true;AntDodoRpcCall.quota=3;AntDodoRpcCall.states.addAll(Arrays.asList(state(false),state(false)));d.collect();assert !Status.hasFlagToday("dodo::collect"):"Failed draw must not suppress daily collection";assert AntDodoRpcCall.draws==1:"First failed draw stops the remaining quota";
  AntDodoRpcCall.fail=false;AntDodoRpcCall.quota=1;AntDodoRpcCall.states.addAll(Arrays.asList(state(false),state(true)));d.collect();assert Status.hasFlagToday("dodo::collect")&&AntDodoRpcCall.draws==2;
  reset();AntDodoRpcCall.states.addAll(Arrays.asList(state(false),state(false)));d.collect();assert !Status.hasFlagToday("dodo::collect"):"Draw ACK alone does not confirm day completion";
  reset();AntDodoRpcCall.states.add("{\"resultCode\":\"SUCCESS\"}");d.collect();assert !Status.hasFlagToday("dodo::collect")&&AntDodoRpcCall.draws==0:"Missing status data must not initiate draw";
  reset();AntDodoRpcCall.states.add("{\"resultCode\":\"SUCCESS\",\"data\":{}}");d.collect();assert !Status.hasFlagToday("dodo::collect")&&AntDodoRpcCall.draws==0:"Missing collect flag must not become false";
  reset();AntDodoRpcCall.cancel=true;AntDodoRpcCall.states.add(state(false));try{d.collect();throw new AssertionError("Cancellation swallowed");}catch(TaskCancelledException expected){}assert !Status.hasFlagToday("dodo::collect");
  AntDodoRpcCall.homeFail=true;assert !d.isLastDay():"Failed end-date query must not authorize last-day prop use";
  AntDodoRpcCall.homeFail=false;for(String s:new String[]{"","2026.02.30","garbage"}){AntDodoRpcCall.date=s;assert d.getEndDateTime()==0&&!d.isLastDay():"Invalid date must not become current time";}
  TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));AntDodoRpcCall.date="2026.10.08";assert d.getEndDateTime()==java.time.LocalDate.of(2026,10,8).atTime(23,59,59).atZone(java.time.ZoneId.of("GMT+8")).toInstant().toEpochMilli():"End date uses GMT+8";
  java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("GMT+8"));java.time.format.DateTimeFormatter f=java.time.format.DateTimeFormatter.ofPattern("uuuu.MM.dd");AntDodoRpcCall.date=today.plusDays(2).format(f);assert !d.isLastDay();AntDodoRpcCall.date=today.format(f);assert d.isLastDay();AntDodoRpcCall.date=today.minusDays(2).format(f);assert !d.isLastDay():"Expired book is not current book's last day";
  consumptions=propQueries=0;prop=prop(0,1);d.propList();assert consumptions==0:"Permanent unselected card must not be treated as expiring";
  prop.remove("recentExpireTime");d.propList();assert consumptions==0:"Unknown expiry must not authorize unselected consumption";
  propQueries=0;prop=prop(System.currentTimeMillis()+TimeUnit.HOURS.toMillis(1),1);d.propList();assert consumptions==1:"Keep actual expiry override";
  d.usePropList.value.add("history");consumptions=propQueries=0;prop=prop(0,2);d.propList();assert consumptions==1&&propQueries==2:"Same unchanged prop ID must not be consumed repeatedly";
  System.out.println("PASS Dodo collection confirmation/failure retry/cancel, strict GMT+8 last-day dates, expiry override and unchanged inventory termination");
 }
}
'''
code = code.replace("@@METHODS@@", "\n".join(method("model/task/antDodo/AntDodo.java", s) for s in ("private void collect()", "private void collectAnimalCard()", "private long getEndDateTime()", "private boolean isLastDay()", "private void propList()", "private TaskAttemptPolicy.ProbeResult probeDodoStatus(")))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
from daily_task_fixture import with_daily_task
code = with_daily_task(code)
with tempfile.TemporaryDirectory(prefix="sesame-dodo-collect-") as tmp:
    java = Path(tmp) / "DodoCollectCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java), str(SOURCE / "util/TaskCancelledException.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "DodoCollectCheck"], check=True)
