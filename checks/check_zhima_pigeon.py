"""Real pigeon authorization, durable receipt and claim verification; no live RPCs."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE
farm="model/task/antFarm/AntFarm.java"
rpc="model/task/antFarm/AntFarmRpcCall.java"
member="model/task/antMember/AntMemberRpcCall.java"
source=(SOURCE/farm).read_text(encoding="utf-8")
assert 'private void manageZhimaPigeon(' in source, 'Pigeon authorization/receipt flow missing'
code=r'''
import org.json.*; import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class PigeonCheck {
    static final String TAG="check", VERSION="1.8.2302070202.46";
    @@CONFIG@@
    String ownerFarmId="self";
    static class MyUtils { static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}} }
    static class TimeUtil { static boolean cancel; static void sleep(long n){if(cancel)throw new TaskCancelledException();} }
    static class Log { static List<String> success=new ArrayList<>(); static void record(String s){} static void farm(String s){success.add(s);} static void err(String tag,String msg,Throwable t){throw new AssertionError(t);} }
    static class RpcRequestGuard { static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));} static String errorMessage(JSONObject j){return j.optString("resultCode");} }
    static class Status { static Set<String> flags=new HashSet<>(); static boolean hasFlagToday(String s){return flags.contains(s);} static void flagToday(String s){flags.add(s);} static void clearFlag(String s){flags.remove(s);} }
    static class RuntimeInfo {
        static RuntimeInfo instance=new RuntimeInfo(); static boolean writable=true;
        static Map<String,String> saved=new HashMap<>();
        JSONObject joCurrent=new JSONObject(),joAll=new JSONObject(); String userId="self";
        static RuntimeInfo getInstance(){return instance;}
        String getString(String key){return joCurrent.optString(key);}
        @@SAVE@@
    }
    static class FileUtil { static String runtimeInfoFile(String uid){return uid;} static boolean write2File(String s,String path){if(!RuntimeInfo.writable)return false;RuntimeInfo.saved.put(path,s);return true;} }
    static class ApplicationHook {
        static Map<String,Queue<String>> replies=new HashMap<>(); static List<JSONObject> calls=new ArrayList<>();
        static String requestString(String name,String args){String key=name.substring(name.lastIndexOf('.')+1);JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",key);calls.add(body);return replies.get(key).remove();}
    }
    static class AntFarmRpcCall { @@FARMRPC@@ }
    static class TaskAlternative { static String request(String id,String scene,String version){return ApplicationHook.requestString("doFarmTask",new JSONArray().put(new JSONObject().put("bizKey",id).put("taskSceneCode",scene).put("version",version)).toString());} }
    static class AntMemberRpcCall { @@MEMBERRPC@@ }
    @@METHODS@@
    static void reply(String name,String... values){ApplicationHook.replies.computeIfAbsent(name,k->new ArrayDeque<>()).addAll(Arrays.asList(values));}
    static void reset(){RuntimeInfo.instance=new RuntimeInfo();RuntimeInfo.saved.clear();RuntimeInfo.writable=true;Status.flags.clear();ApplicationHook.replies.clear();ApplicationHook.calls.clear();Log.success.clear();TimeUtil.cancel=false;}
    static String farm(JSONObject... animals){return new JSONObject().put("memo","SUCCESS").put("subFarmVO",new JSONObject().put("farmId","self").put("animals",new JSONArray(Arrays.asList(animals)))).toString();}
    static JSONObject pigeon(double reward){return new JSONObject().put("animalId",FARM_NPC_IDS[3]).put("subAnimalType","NPC").put("currentFarmId","self").put("masterFarmId","npc").put("npcBizReward",reward).put("reachNpcBizRewardLimit",reward>=88);}
    static JSONObject task(String record){return new JSONObject().put("templateId",PIGEON_TEMPLATE).put("bizType","LIFE_RECORD").put("recordId",record).put("completedNum",0).put("needCompleteNum",1).put("finishFlag",false);}
    static String tasks(JSONObject task){return new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("toCompleteVOS",new JSONArray().put(task))).toString();}
    static JSONObject credit(String id){return new JSONObject().put("status","UNCLAIMED").put("cateId",PIGEON_CATEGORY).put("creditFeedbackId",id).put("potentialSize","88");}
    static String credits(JSONObject... items){return new JSONObject().put("resultCode","SUCCESS").put("creditFeedbackVOS",new JSONArray(Arrays.asList(items))).toString();}
    static long calls(String rpc){return ApplicationHook.calls.stream().filter(j->rpc.equals(j.optString("rpc"))).count();}
    static void pending(){assert RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,"{\"feedbackId\":\"\"}");}
    public static void main(String[] args)throws Exception {
        PigeonCheck f=new PigeonCheck();
        reset();RuntimeInfo.writable=false;assert !RuntimeInfo.getInstance().putVerified("key","new")&&RuntimeInfo.getInstance().getString("key").isEmpty();
        RuntimeInfo.writable=true;assert RuntimeInfo.getInstance().putVerified("key","old");RuntimeInfo.writable=false;assert !RuntimeInfo.getInstance().putVerified("key","new")&&RuntimeInfo.getInstance().getString("key").equals("old");
        reset();reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.authorizePigeonHire();
        JSONObject sent=ApplicationHook.calls.get(1);assert sent.optString("sceneCode").equals("alchemy")&&sent.optString("version").equals("alchemy")&&sent.optString("bizType").equals("LIFE_RECORD");
        reset();reply("queryListV3",tasks(task("")),tasks(task("joined")));reply("joinActivity","{\"resultCode\":\"SUCCESS\",\"data\":{\"recordId\":\"joined\"}}");reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.authorizePigeonHire();
        assert calls("joinActivity")==1;
        reset();reply("queryListV3",tasks(task("r").put("finishFlag",true)));assert !f.authorizePigeonHire()&&calls("taskFeedback")==0;
        reset();reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"FAIL\"}");assert !f.authorizePigeonHire();
        reset();pending();assert !bindPigeonFeedback(new JSONArray());
        assert !bindPigeonFeedback(new JSONArray().put(credit("a")).put(credit("b")));
        RuntimeInfo.writable=false;assert !bindPigeonFeedback(new JSONArray().put(credit("id")));RuntimeInfo.writable=true;
        assert bindPigeonFeedback(new JSONArray().put(credit("id")));assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).contains("id");
        String disk=RuntimeInfo.saved.get("self");RuntimeInfo.instance=new RuntimeInfo();RuntimeInfo.instance.joAll=new JSONObject(disk);RuntimeInfo.instance.joCurrent=RuntimeInfo.instance.joAll.optJSONObject("self");
        reply("queryCreditFeedback",credits(credit("id")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.collectPigeonReceipt()&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        assert calls("collectCreditFeedback")==1;
        reset();pending();reply("queryCreditFeedback",credits(credit("id")),credits(credit("id")));reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");assert !f.collectPigeonReceipt();
        Status.flags.clear();assert !RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "receipt survives day rollover";
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));reply("queryCreditFeedback","{}");assert !f.collectPigeonReceipt();
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));JSONObject[] twenty=new JSONObject[20];for(int i=0;i<20;i++)twenty[i]=credit("other"+i);reply("queryCreditFeedback",credits(twenty));assert !f.collectPigeonReceipt() : "full page is not proof of disappearance";
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));reply("queryCreditFeedback",credits());RuntimeInfo.writable=false;assert !f.collectPigeonReceipt()&&!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        reset();reply("syncAnimalStatus",farm(),farm(),farm(pigeon(0)));reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");f.manageZhimaPigeon();assert calls("hireAnimal")==1;
        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm());reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");reply("queryCreditFeedback",credits(credit("id")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");f.manageZhimaPigeon();assert calls("sendBackAnimal")==1&&calls("hireAnimal")==0&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        reset();RuntimeInfo.writable=false;reply("syncAnimalStatus",farm(pigeon(88)));f.manageZhimaPigeon();assert calls("sendBackAnimal")==0;
        reset();pending();reply("syncAnimalStatus",farm());reply("queryCreditFeedback",credits());f.manageZhimaPigeon();assert calls("hireAnimal")==0&&!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        reset();JSONObject visit=new JSONObject().put("taskId","ZHIMA_NPC_VISIT_TASK").put("bizKey","ZHIMA_NPC_VISIT_TASK").put("taskStatus","TODO").put("awardType","NPC_REWARD");
        reply("listFarmTask",new JSONObject().put("memo","SUCCESS").put("farmTaskList",new JSONArray().put(visit)).toString(),
            new JSONObject().put("memo","SUCCESS").put("farmTaskList",new JSONArray().put(new JSONObject(visit.toString()).put("taskStatus","FINISHED"))).toString(),
            new JSONObject().put("memo","SUCCESS").put("farmTaskList",new JSONArray().put(new JSONObject(visit.toString()).put("taskStatus","RECEIVED"))).toString());
        reply("doFarmTask","{\"memo\":\"SUCCESS\"}");reply("receiveFarmTaskAward","{\"memo\":\"SUCCESS\"}");f.runPigeonFarmTasks();
        assert calls("doFarmTask")==1&&calls("receiveFarmTaskAward")==1;
        assert ApplicationHook.calls.stream().filter(j->"receiveFarmTaskAward".equals(j.optString("rpc"))).findFirst().orElseThrow().optString("source").equals(FARM_NPC_SOURCES[3]);
        reset();RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,"garbage");reply("syncAnimalStatus",farm(pigeon(88)));f.manageZhimaPigeon();assert ApplicationHook.calls.isEmpty();
        reset();TimeUtil.cancel=true;try{f.manageZhimaPigeon();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
        System.out.println("PASS pigeon authorization, safe persisted receipt/rollback, restart/day recovery, binding/ambiguous/paged data, one-click coexistence, credit readback, hire/full/failed persistence and cancellation");
    }
}
'''
config="\n".join(line.strip().replace("private ","") for line in source.splitlines() if "private static final String[] FARM_NPC_" in line or "private static final String PIGEON_" in line)
code=code.replace("@@CONFIG@@",config)
code=code.replace("@@SAVE@@",method("data/RuntimeInfo.java","public synchronized boolean putVerified("))
code=code.replace("@@METHODS@@","\n".join(method(farm,s) for s in (
    "private static boolean farmFeatureOk(","private JSONObject queryNpcFarm(","private static double npcRewardValue(","private static JSONObject findFarmNpc(","private static int npcOccupiedSlots(",
    "private static long npcTaskNumber(","private JSONObject queryPigeonAlchemyTask(","private boolean authorizePigeonHire(",
    "public static boolean bindPigeonFeedback(","private JSONArray queryPigeonFeedback(","private boolean collectPigeonReceipt(",
    "private JSONArray queryPigeonFarmTasks(","private void runPigeonFarmTasks(","private void manageZhimaPigeon(")))
code=code.replace("@@FARMRPC@@","\n".join(method(rpc,s) for s in (
    "public static String queryNpcFarm(","public static String hireNpcAnimal(",
    "public static String sendBackNpcAnimal(String animalId, String currentFarmId, String masterFarmId, String source)",
    "public static String listPigeonFarmTasks(","public static String receivePigeonFarmAward(","public static String doFarmTask(String bizKey, String taskSceneCode)")))
code=code.replace("@@MEMBERRPC@@","\n".join(method(member,s) for s in (
    "public static String alchemyQueryTasks(","public static String queryCreditFeedback(","public static String collectCreditFeedback(",
    "public static String joinPigeonAlchemyTask(","public static String feedbackPigeonAlchemyTask(")))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-pigeon-") as tmp:
    java=Path(tmp)/"PigeonCheck.java";java.write_text(code,encoding="utf-8")
    subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
    subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"PigeonCheck"],check=True)
