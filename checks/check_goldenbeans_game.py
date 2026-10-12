"""Run the actual game center against failed and incomplete post-report snapshots."""
from pathlib import Path
import os, subprocess, tempfile, sys
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method
ROOT=Path(__file__).resolve().parents[1]
src=(ROOT/"app/src/main/java/io/github/aw1y2z/sesame/model/task/goldenbeans/GoldenBeansGameCenter.java").read_text(encoding="utf-8")
body=src[src.index("public final class GoldenBeansGameCenter"):].replace("public final class GoldenBeansGameCenter", "static final class GoldenBeansGameCenter",1)
code=r'''
import java.util.*;import org.json.*;
public class GameCheck {
 static class TaskCancelledException extends RuntimeException {}
 static class Log {static void record(String s){}static void goldenBeans(String s){}static void i(String a,String b){}static void printStackTrace(String a,Throwable t){throw new AssertionError(t);}}
 static class Status {static Set<String> flags=new HashSet<>();static void flagToday(String s){flags.add(s);}static boolean hasFlagToday(String s){return flags.contains(s);}}
 static class GameTask {static int reports;static GameTask matchAppId(String id){return new GameTask();}String getTitle(){return "game";}int reportSync(String s,int count,String channel){reports++;return 1;}}
 static class GoldenBeansSupport {
  static String TAG="game";static void pause(int i){}static String describe(JSONObject j){return "error";}
  static String awardText(JSONObject j){return "";}static String tracerField(String s,String k){return "";}
  static JSONObject parse(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
  @@OK@@
  static JSONObject findObject(JSONObject j,String k){return j.optJSONObject(k);}
 }
 static class goldenbeansRpcCall {static String GAME_CHANNEL="goldenbean",after;static int queries;
  static String fetchGameList(){queries++;return queries==1?initial():after;}
  static String drawLottery(){throw new AssertionError("unexpected draw");}
 }
 static String initial(){return new JSONObject().put("success",true)
 .put("gameCenterDrawRights",new JSONObject().put("quotaCanUse",0).put("usedQuota",0).put("quotaLimit",1))
 .put("games",new JSONArray().put(new JSONObject().put("appId","game").put("deliveryBenefitList",new JSONArray().put(new JSONObject()
 .put("benefitType","IEP_REQUEST").put("iepTaskId","task").put("rightTimes",0).put("rightTimesLimit",1))))).toString();}
 @@BODY@@
 public static void main(String[] args){
  for(String bad:new String[]{"{}","{\"success\":false}","{\"success\":true}",new JSONObject(initial()).put("gameCenterDrawRights",new JSONObject()).toString(),
    new JSONObject(initial()).put("success",false).put("resultCode","SUCCESS").toString(),
    new JSONObject(initial()).put("success",false).put("code","100000000").toString()}){
   Status.flags.clear();GameTask.reports=0;goldenbeansRpcCall.queries=0;goldenbeansRpcCall.after=bad;
   assert !GoldenBeansGameCenter.run(0):"unclear query marked complete";
   assert Status.flags.isEmpty()&&GameTask.reports==1:"query failure poisoned daily flag";
  }
  goldenbeansRpcCall.queries=0;goldenbeansRpcCall.after=initial();assert GoldenBeansGameCenter.run(0);assert Status.flags.size()==1;
  System.out.println("PASS game post-report failed/malformed/missing state stays retryable; verified no-progress still bounded");
 }
}
'''
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=next(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))
with tempfile.TemporaryDirectory(prefix="sesame-game-check-") as tmp:
 p=Path(tmp)/"GameCheck.java";p.write_text(code.replace("@@BODY@@",body).replace("@@OK@@",method("model/task/goldenbeans/GoldenBeansSupport.java","public static boolean ok(")),encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(p)],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"GameCheck"],check=True,timeout=30)
