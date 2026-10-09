"""Compile the actual bill collage worker and replay free actions with isolated RPC/state stubs."""
from pathlib import Path
import os, re, subprocess, tempfile

root = Path(__file__).resolve().parents[1]
pkg = 'io.github.aw1y2z.sesame'
source = root / 'app/src/main/java'
worker = source / pkg.replace('.', '/') / 'model/task/antMember/BillBlockWorld.java'
stubs = {
    'util.MyUtils': '''import java.util.*;import org.json.*;public class MyUtils {
 public static int offset;public static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(1791300600000L+offset*86400000L);return c;}
 public static JSONObject newJSONObject(){return new JSONObject();}public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}''',
    'util.TimeUtil': '''public class TimeUtil {public static boolean cancel;public static void sleep(long n){if(cancel)throw new TaskCancelledException();}}''',
    'util.Log': '''public class Log {public static int confirmed;public static String messages="";public static void other(String s){confirmed++;messages+=s;}public static void record(String s){}public static void err(String t,String s,Throwable e){throw new AssertionError(e);}}''',
    'util.Status': '''import java.util.*;public class Status {public static void clearFlag(String s){flags.remove(io.github.aw1y2z.sesame.hook.ApplicationHook.uid+MyUtils.offset+s);}public static Set<String> flags=new HashSet<>();public static boolean hasFlagToday(String s){return flags.contains(io.github.aw1y2z.sesame.hook.ApplicationHook.uid+MyUtils.offset+s);}public static void flagToday(String s){flags.add(io.github.aw1y2z.sesame.hook.ApplicationHook.uid+MyUtils.offset+s);}}''',
    'rpc.intervallimit.RpcRequestGuard': '''import org.json.*;public class RpcRequestGuard {public static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"))||"FAIL".equals(j.opt("resultCode"));}}''',
    'data.RuntimeInfo': '''import java.util.*;public class RuntimeInfo {public static boolean writable=true;public static Map<String,RuntimeInfo> users=new HashMap<>();private Map<String,String> values=new HashMap<>();public static RuntimeInfo getInstance(){return users.computeIfAbsent(io.github.aw1y2z.sesame.hook.ApplicationHook.uid,k->new RuntimeInfo());}public String getString(String k){return values.getOrDefault(k,"");}public boolean putVerified(String k,String v){if(!writable)return false;values.put(k,v);return true;}}''',
    'hook.ApplicationHook': '''import org.json.*;import java.util.*;public class ApplicationHook {
 public static String uid="A";public static JSONObject home;public static JSONArray stored;public static List<String> ops=new ArrayList<>();
 public static int mutations,queries;public static boolean effect=true,ack=true,readback=true,cancelAfter,switchAfter,infinite,drift,noChapterEffect,warehouseMalformed;
 public static String malformed="",ackMode="";public static JSONObject response(){return new JSONObject().put("success",true).put("resultCode",200);}
 public static String requestString(String method,String args){return call(method,args,false);}
 public static String requestString(String method,String args,int tries,int delay){assert tries==1&&delay==0:"mutation retries";return call(method,args,true);}
 public static JSONObject block(String id,String config,int level,int x,int y){JSONObject b=new JSONObject().put("blockRecordId",id).put("blockConfigId",config).put("level",level).put("width",1).put("length",1);if(x>=0)b.put("posX",x).put("posY",y);return b;}
 public static int index(JSONArray a,String id){for(int i=0;i<a.length();i++)if(id.equals(a.optJSONObject(i).optString("blockRecordId")))return i;return -1;}
 public static void progress(){if(noChapterEffect)return;JSONArray chapters=home.optJSONArray("chapterTasks");for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if("IN_PROGRESS".equals(c.opt("status"))){c.put("status","COMPLETED").put("completed",true);c.optJSONObject("task").put("currentValue",1).put("status","COMPLETED");break;}}}
 static String call(String method,String args,boolean mutation){
  String op=method.substring(method.lastIndexOf('.')+1);ops.add(op);if(mutation)mutations++;else queries++;
  if(!mutation&&(!readback&&mutations>0||op.equals(malformed)))return "garbage";
  JSONObject result=response();JSONObject body=new JSONArray(args).optJSONObject(0);
  if(op.equals("queryBlockHome"))return result.put("data",new JSONObject(home.toString())).toString();
  if(op.equals("queryWarehouseBlocks")){assert body.opt("seasonId").equals(home.optJSONObject("canvas").opt("seasonId"));JSONArray groups=new JSONArray();for(int i=0;i<stored.length();i++){JSONObject b=new JSONObject(stored.optJSONObject(i).toString());b.remove("posX");b.remove("posY");b.put("blocks",new JSONArray().put(new JSONObject().put("blockRecordId",b.optString("blockRecordId"))));if(warehouseMalformed)b.put("width","1");groups.put(b);}return result.put("blocks",groups).toString();}
  if(op.equals("queryBlockDetail")){Calendar c=io.github.aw1y2z.sesame.util.MyUtils.getInstance();assert body.optString("year").equals(Integer.toString(c.get(Calendar.YEAR)));assert body.optString("month").equals(String.format(Locale.ROOT,"%02d",c.get(Calendar.MONTH)+1));assert body.optString("blockConfigId").equals("detail\\\"id");return result.toString();}
  assert mutation:op;JSONArray placed=home.optJSONArray("placedBlocks"),pending=home.optJSONArray("pendingBlocks");
  if(op.equals("collectDailyProductCoin")){assert args.equals("[null]");if(effect)home.put("coinBalance",home.optInt("coinBalance")+home.optInt("dailyProductAmt")).put("dailyProductAmt",0);result.put("gainedCoinAmt",5);}
  else if(op.equals("batchCollectBlock")){JSONArray items=body.optJSONArray("blockItems"),results=new JSONArray();for(int i=0;i<items.length();i++){JSONObject p=items.optJSONObject(i);String id=p.optString("blockRecordId");results.put(new JSONObject().put("blockRecordId",id).put("outcome","SUCCESS").put("status","PLACED").put("failCode","0"));if(effect){int at=index(pending,id);assert at>=0;JSONObject b=pending.optJSONObject(at);pending.remove(at);b.put("posX",p.opt("posX")).put("posY",p.opt("posY"));placed.put(b);}}result.put("itemResults",results);if(effect)progress();if(infinite)pending.put(block("new"+mutations,"other"+mutations,1,-1,-1));}
  else if(op.equals("placeBlock")){result.put("status","PLACED");if(effect){String id=body.optString("blockRecordId");int at=index(stored,id);assert at>=0;JSONObject b=stored.optJSONObject(at);stored.remove(at);placed.put(b.put("posX",body.opt("posX")).put("posY",body.opt("posY")));progress();}}
  else if(op.equals("syncCanvas")){JSONArray positions=body.optJSONArray("blockPositions");assert positions.length()==placed.length();if(effect)for(int i=0;i<positions.length();i++){JSONObject p=positions.optJSONObject(i);JSONObject b=placed.optJSONObject(index(placed,p.optString("blockRecordId")));b.put("posX",p.opt("posX")).put("posY",p.opt("posY"));}if(effect)progress();}
  else if(op.equals("mergeBlock")){if(effect){JSONObject main=placed.optJSONObject(index(placed,body.optString("mainBlockId")));main.put("level",main.optInt("level")+1).put("posX",body.opt("posX")).put("posY",body.opt("posY"));placed.remove(index(placed,body.optJSONArray("mergedBlockIds").optString(0)));progress();}}
  else if(op.equals("reclaimBlock")){if(effect){int at=index(placed,body.optString("blockRecordId"));JSONObject b=placed.optJSONObject(at);placed.remove(at);b.remove("posX");b.remove("posY");stored.put(b);progress();}}
  else if(op.equals("advanceChapter")){if(effect){JSONArray chapters=home.optJSONArray("chapterTasks");for(int i=0;i<chapters.length();i++){JSONObject c=chapters.optJSONObject(i);if(body.opt("chapterId").equals(c.opt("chapterId")))c.put("status","REWARDED").optJSONObject("task").put("status","REWARDED");}}}
  else if(op.equals("reportBlockViewed")){if(effect)progress();}
  else throw new AssertionError(op);
  if(drift)home.put("coinBalance",home.optInt("coinBalance")+1);
  if(effect&&home.optJSONObject("prosperityInfo")!=null)home.optJSONObject("prosperityInfo").put("progress",mutations).put("stickerCount",placed.length());
  if(cancelAfter)io.github.aw1y2z.sesame.util.TimeUtil.cancel=true;
  if(switchAfter){uid="B";io.github.aw1y2z.sesame.util.TimeUtil.cancel=true;}
  if(!ack)result.put("success",false);
  if(ackMode.equals("missingItems"))result.remove("itemResults");
  if(ackMode.equals("wrongOutcome"))result.optJSONArray("itemResults").optJSONObject(0).put("outcome","FAIL");
  if(ackMode.equals("wrongId"))result.optJSONArray("itemResults").optJSONObject(0).put("blockRecordId",12);
  if(ackMode.equals("failCode"))result.optJSONArray("itemResults").optJSONObject(0).put("failCode","1");
  if(ackMode.equals("placeStatus"))result.put("status","STORED");
  if(ackMode.equals("fractionCode"))result.put("resultCode",200.1);
  if(ackMode.equals("nestedFailure"))result.put("data",new JSONObject().put("resultCode",400).put("success",true));
  return result.toString();
 }
}'''
}

test = r'''
package io.github.aw1y2z.sesame.model.task.antMember;
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.data.RuntimeInfo;
public class WorldCheck {
 static JSONObject block(String id,String cfg,int x,int y){return ApplicationHook.block(id,cfg,1,x,y);}
 static JSONObject chapter(String target,boolean rewarded){return new JSONObject().put("chapterId","ch").put("completed",rewarded).put("status",rewarded?"REWARDED":"IN_PROGRESS").put("task",new JSONObject().put("status",rewarded?"REWARDED":"IN_PROGRESS").put("targetType",target).put("currentValue",0));}
 static void reset(){ApplicationHook.uid="A";ApplicationHook.home=new JSONObject().put("canvas",new JSONObject().put("seasonId","sea\"son").put("currentChapterId","ch").put("canvasWidth",4).put("canvasLength",4)).put("pendingBlocks",new JSONArray()).put("placedBlocks",new JSONArray()).put("chapterTasks",new JSONArray().put(chapter("PLACE_BLOCK",true))).put("dailyProductAmt",0).put("coinBalance",0).put("normalBlockRes",new JSONObject().put("blockDetailList",new JSONArray().put(new JSONObject().put("blockConfigId","detail\"id"))));ApplicationHook.stored=new JSONArray();ApplicationHook.ops.clear();ApplicationHook.mutations=ApplicationHook.queries=0;ApplicationHook.effect=ApplicationHook.ack=ApplicationHook.readback=true;ApplicationHook.cancelAfter=ApplicationHook.switchAfter=ApplicationHook.infinite=ApplicationHook.drift=ApplicationHook.noChapterEffect=ApplicationHook.warehouseMalformed=false;ApplicationHook.malformed=ApplicationHook.ackMode="";TimeUtil.cancel=false;Status.flags.clear();RuntimeInfo.users.clear();RuntimeInfo.writable=true;Log.confirmed=0;MyUtils.offset=0;}
 static JSONArray pending(){return ApplicationHook.home.optJSONArray("pendingBlocks");}static JSONArray placed(){return ApplicationHook.home.optJSONArray("placedBlocks");}
 static void active(String target){ApplicationHook.home.put("chapterTasks",new JSONArray().put(chapter(target,false)));}
 static void run(){BillBlockWorld.run(true);}
 static long count(String op){return ApplicationHook.ops.stream().filter(op::equals).count();}
 static void unknown(){assert !RuntimeInfo.getInstance().getString("member::billWorldUnknown").isEmpty();int calls=ApplicationHook.ops.size();run();assert ApplicationHook.ops.size()==calls;assert !Status.hasFlagToday("member::billWorldDone");}
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));reset();BillBlockWorld.run(false);assert ApplicationHook.ops.isEmpty();
  reset();ApplicationHook.home.put("dailyProductAmt",5);pending().put(block("p\"id","cfg",-1,-1));ApplicationHook.stored.put(block("w","cfg",-1,-1));run();assert count("collectDailyProductCoin")==1&&count("batchCollectBlock")==1&&count("placeBlock")==1&&count("syncCanvas")==2&&count("mergeBlock")==1;assert Status.hasFlagToday("member::billWorldDone");int calls=ApplicationHook.ops.size();run();assert ApplicationHook.ops.size()==calls;
  for(String target:new String[]{"PLACE_BLOCK","MOVE_BLOCK","MERGE_COUNT","RECLAIM_BLOCK","VIEW_BLOCK_DETAIL"}){reset();active(target);if(target.equals("PLACE_BLOCK"))pending().put(block("p","cfg",-1,-1));else if(!target.equals("VIEW_BLOCK_DETAIL"))placed().put(block("a","cfg",0,0));if(target.equals("MERGE_COUNT"))placed().put(block("b","cfg",1,0));run();assert count("advanceChapter")==1:target;assert Status.hasFlagToday("member::billWorldDone"):target;if(target.equals("VIEW_BLOCK_DETAIL"))assert count("queryBlockDetail")==1&&count("reportBlockViewed")==1;if(target.equals("RECLAIM_BLOCK"))assert count("reclaimBlock")==1&&count("placeBlock")==0&&ApplicationHook.stored.length()==1;}
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.effect=false;ApplicationHook.drift=true;run();assert ApplicationHook.mutations==1&&Log.confirmed==0;unknown();
  reset();ApplicationHook.home.put("dailyProductAmt",5);ApplicationHook.effect=false;run();assert ApplicationHook.mutations==1;unknown();
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.ack=false;run();assert ApplicationHook.mutations==1;unknown();
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.readback=false;run();assert ApplicationHook.mutations==1;unknown();
  for(String mode:new String[]{"missingItems","wrongOutcome","wrongId","failCode","fractionCode","nestedFailure","placeStatus"}){reset();ApplicationHook.ackMode=mode;if(mode.equals("placeStatus"))ApplicationHook.stored.put(block("w","cfg",-1,-1));else pending().put(block("p","cfg",-1,-1));run();assert ApplicationHook.mutations==1:mode;unknown();}
  reset();pending().put(block("p","cfg",-1,-1));RuntimeInfo.writable=false;run();assert ApplicationHook.mutations==0;
  for(String malformed:new String[]{"queryBlockHome","queryWarehouseBlocks"}){reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.malformed=malformed;run();assert ApplicationHook.mutations==0&&!Status.hasFlagToday("member::billWorldDone");}
  for(String kind:new String[]{"pendingMissing","placedMissing","dailyType","duplicate","overlap","positionMissing","widthFraction","warehouseType","chapterType","canvasHuge","seasonType","chapterMissing"}){reset();pending().put(block("p","cfg",-1,-1));switch(kind){case "pendingMissing"->ApplicationHook.home.remove("pendingBlocks");case "placedMissing"->ApplicationHook.home.remove("placedBlocks");case "dailyType"->ApplicationHook.home.put("dailyProductAmt","0");case "duplicate"->pending().put(block("p","cfg",-1,-1));case "overlap"->{placed().put(block("a","cfg",0,0));placed().put(block("b","cfg",0,0));}case "positionMissing"->placed().put(block("a","cfg",-1,-1));case "widthFraction"->pending().optJSONObject(0).put("width",1.1);case "warehouseType"->{ApplicationHook.stored.put(block("w","cfg",-1,-1));ApplicationHook.warehouseMalformed=true;}case "chapterType"->ApplicationHook.home.optJSONArray("chapterTasks").optJSONObject(0).put("completed","true");case "canvasHuge"->ApplicationHook.home.optJSONObject("canvas").put("canvasWidth",100000);case "seasonType"->ApplicationHook.home.optJSONObject("canvas").put("seasonId",1);case "chapterMissing"->ApplicationHook.home.remove("chapterTasks");}run();assert ApplicationHook.mutations==0:kind;}
  reset();ApplicationHook.home.remove("dailyProductAmt");run();assert !Status.hasFlagToday("member::billWorldDone");
  reset();active("UNSUPPORTED");run();assert ApplicationHook.mutations==0&&!Status.hasFlagToday("member::billWorldDone");
  reset();active("MOVE_BLOCK");placed().put(block("a","cfg",0,0));ApplicationHook.noChapterEffect=true;run();assert ApplicationHook.mutations==1;unknown();
  reset();pending().put(block("p","cfg",-1,-1));TimeUtil.cancel=true;try{run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.ops.isEmpty();
  for(boolean switchAccount:new boolean[]{false,true}){reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.cancelAfter=!switchAccount;ApplicationHook.switchAfter=switchAccount;try{run();throw new AssertionError("cancel after action swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.mutations==1;ApplicationHook.uid="A";TimeUtil.cancel=false;unknown();}
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.effect=false;run();unknown();MyUtils.offset=1;ApplicationHook.effect=true;run();assert ApplicationHook.mutations==3;
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.infinite=true;run();assert ApplicationHook.mutations==40&&!Status.hasFlagToday("member::billWorldDone");
  reset();ApplicationHook.home.optJSONObject("canvas").put("canvasWidth",3).put("canvasLength",2);placed().put(block("a","ca",1,0));placed().put(block("b","cb",1,1));pending().put(block("big","cg",-1,-1).put("width",2).put("length",2));run();assert count("syncCanvas")>=1&&count("batchCollectBlock")==1&&count("reclaimBlock")==0;
  reset();ApplicationHook.home.optJSONObject("canvas").put("canvasWidth",1).put("canvasLength",1);placed().put(block("old","ca",0,0));pending().put(block("new","cb",-1,-1));run();assert count("reclaimBlock")==1&&count("batchCollectBlock")==1&&count("placeBlock")==0&&Status.hasFlagToday("member::billWorldDone");
  Status.flags.clear();run();assert count("placeBlock")==0&&Status.hasFlagToday("member::billWorldDone"):"reclaimed durable after restart";
  reset();placed().put(block("new","cfg",0,0));RuntimeInfo.getInstance().putVerified("member::billWorldCanvasDirty","sea\"son");MyUtils.offset=1;run();assert count("syncCanvas")==1&&Status.hasFlagToday("member::billWorldDone"):"dirty canvas survives next day";
  reset();active("MOVE_BLOCK");placed().put(block("a","cfg",0,0));ApplicationHook.home.optJSONArray("chapterTasks").put(chapter("VIEW_BLOCK_DETAIL",false).put("chapterId","second"));run();assert count("advanceChapter")==2&&count("reportBlockViewed")==1&&Status.hasFlagToday("member::billWorldDone");
  reset();Log.messages="";ApplicationHook.home.put("prosperityInfo",new JSONObject().put("level",2).put("progress",0).put("stickerCount",0));pending().put(block("p","cfg",-1,-1));run();assert Log.messages.contains("繁荣度回查#等级=2")&&Status.hasFlagToday("member::billWorldDone");
  reset();pending().put(block("p","cfg",-1,-1));ApplicationHook.home.put("prosperityInfo",new JSONObject().put("level","2"));run();assert ApplicationHook.mutations==0;
  System.out.println("PASS actual worker: default-off, free coin/pending/warehouse/sync/merge/reclaim, all five chapter targets, compaction/nonoverlap, strict unknown/duplicate/type guards, durable retry/write failure, GMT+8, cancellation/account switch, 40-action bound");
 }
}
'''
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
env = dict(os.environ, JAVA_TOOL_OPTIONS='-Xms16m -Xmx192m')
properties = root / 'local.properties'
sdk_path = os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', ''))
if properties.exists():
    for line in properties.read_text(encoding='utf-8').splitlines():
        if line.startswith('sdk.dir='):
            sdk_path = line.split('=', 1)[1].replace('\\:', ':').replace('\\\\', '\\')
compile_sdk = re.search(r'compileSdk\s+(\d+)', (root / 'app/build.gradle').read_text(encoding='utf-8')).group(1)
android_jar = Path(sdk_path) / 'platforms' / ('android-' + compile_sdk) / 'android.jar'
if not android_jar.exists():
    android_jar = Path(sdk_path) / 'platforms' / ('android-' + compile_sdk + '.0') / 'android.jar'
if sdk_path and android_jar.exists():
    with tempfile.TemporaryDirectory(prefix='sesame-bill-world-api-') as tmp:
        files = [str(worker), str(source / pkg.replace('.', '/') / 'util/TaskCancelledException.java'), str(source / pkg.replace('.', '/') / 'util/DailyTask.java')]
        for suffix, body in stubs.items():
            if suffix == 'hook.ApplicationHook':
                body = 'public class ApplicationHook {public static String uid="A";public static String requestString(String m,String a){return "";}public static String requestString(String m,String a,int n,int d){return "";}}'
            f = Path(tmp) / ((pkg + '.' + suffix).replace('.', '/') + '.java')
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text('package ' + (pkg + '.' + suffix).rsplit('.', 1)[0] + ';\n' + body, encoding='utf-8')
            files.append(str(f))
        subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(android_jar), '-d', tmp] + files, check=True, env=env)
        print('PASS actual worker Android org.json checked-exception signatures')
with tempfile.TemporaryDirectory(prefix='sesame-bill-world-') as tmp:
    files = [str(worker), str(source / pkg.replace('.', '/') / 'util/TaskCancelledException.java'), str(source / pkg.replace('.', '/') / 'util/DailyTask.java')]
    for suffix, body in stubs.items():
        f = Path(tmp) / ((pkg + '.' + suffix).replace('.', '/') + '.java')
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text('package ' + (pkg + '.' + suffix).rsplit('.', 1)[0] + ';\n' + body, encoding='utf-8')
        files.append(str(f))
    test_file = Path(tmp) / 'WorldCheck.java'
    test_file.write_text(test, encoding='utf-8')
    files.append(str(test_file))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp] + files, check=True, env=env)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), pkg + '.model.task.antMember.WorldCheck'], check=True, env=env)
