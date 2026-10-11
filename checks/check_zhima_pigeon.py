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
    static class System { static long now=1791676800000L; static long currentTimeMillis(){return now;} static final java.io.PrintStream out=java.lang.System.out; }
    static final String TAG="check", VERSION="1.8.2302070202.46";
    @@CONFIG@@
    String ownerFarmId="self";
    static class Field {int value=3;int getValue(){return value;}}Field farmNpcType=new Field();
    static class MyUtils { static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(System.now);return c;} static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}} }
    static class TimeUtil { static boolean cancel; static void sleep(long n){if(cancel)throw new TaskCancelledException();} }
    static class Log { static List<String> success=new ArrayList<>(); static List<String> records=new ArrayList<>(); static void record(String s){records.add(s);} static void farm(String s){success.add(s);} static void err(String tag,String msg,Throwable t){throw new AssertionError(t);} }
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
        static boolean failAfterSend,bindAfterSend; static Map<String,Queue<String>> replies=new HashMap<>(); static List<JSONObject> calls=new ArrayList<>();
        static String requestString(String name,String args){String key=name.substring(name.lastIndexOf('.')+1);JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",key);calls.add(body);if("sendBackAnimal".equals(key)) {
            if(bindAfterSend)RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,"{\"feedbackId\":\"concurrent\"}");
            if(failAfterSend)RuntimeInfo.writable=false;
        }return replies.get(key).remove();}
    }
    static class AntFarmRpcCall { @@FARMRPC@@ }
    static class TaskAlternative { static String request(String id,String scene,String version){return ApplicationHook.requestString("doFarmTask",new JSONArray().put(new JSONObject().put("bizKey",id).put("taskSceneCode",scene).put("version",version)).toString());} }
    static class AntMemberRpcCall { @@MEMBERRPC@@ }
    @@METHODS@@
    static void reply(String name,String... values){ApplicationHook.replies.computeIfAbsent(name,k->new ArrayDeque<>()).addAll(Arrays.asList(values));}
    static void reset(){System.now=1791676800000L;RuntimeInfo.instance=new RuntimeInfo();RuntimeInfo.saved.clear();RuntimeInfo.writable=true;Status.flags.clear();ApplicationHook.replies.clear();ApplicationHook.calls.clear();ApplicationHook.failAfterSend=false;ApplicationHook.bindAfterSend=false;Log.success.clear();Log.records.clear();TimeUtil.cancel=false;}
    static String farm(JSONObject... animals){return new JSONObject().put("memo","SUCCESS").put("subFarmVO",new JSONObject().put("farmId","self").put("animals",new JSONArray(Arrays.asList(animals)))).toString();}
    static JSONObject pigeon(double reward){return new JSONObject().put("animalId",FARM_NPC_IDS[3]).put("subAnimalType","NPC").put("currentFarmId","self").put("masterFarmId","npc").put("npcBizReward",reward).put("reachNpcBizRewardLimit",reward>=88).put("beHiredEndTime",System.currentTimeMillis()+43200000L);}
    static JSONObject task(String record){return new JSONObject().put("templateId",PIGEON_TEMPLATE).put("bizType","LIFE_RECORD").put("recordId",record).put("completedNum",0).put("needCompleteNum",1).put("finishFlag",false);}
    static String tasks(JSONObject task){return new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("toCompleteVOS",new JSONArray().put(task))).toString();}
    static JSONObject credit(String id){return new JSONObject().put("status","UNCLAIMED").put("cateId",PIGEON_CATEGORY).put("creditFeedbackId",id).put("potentialSize","88");}
    static String credits(JSONObject... items){return new JSONObject().put("resultCode","SUCCESS").put("creditFeedbackVOS",new JSONArray(Arrays.asList(items))).toString();}
    static long calls(String rpc){return ApplicationHook.calls.stream().filter(j->rpc.equals(j.optString("rpc"))).count();}
    static void pending(){assert RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,"{\"feedbackId\":\"\"}");}
    public static void main(String[] args)throws Exception {
        PigeonCheck f=new PigeonCheck();
        reset();reply("queryListV3",tasks(task("authSkip")),tasks(task("authSkip")));
        reply("taskFeedback","{\"error\":\"RPC_SKIPPED\",\"code\":\"400000040\"}","{\"resultCode\":\"SUCCESS\"}");
        assert !f.authorizePigeonHire()&&!Status.hasFlagToday("farm::pigeonFeedbackAttempt::authSkip")&&Log.records.isEmpty();
        assert f.authorizePigeonHire()&&calls("taskFeedback")==2;

        reset();pending();expireUnboundPigeonReceipt();
        long first=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY)).optLong("createdAt");
        assert first==System.now : "legacy receipt lacks waiting start";
        System.now+=86399999L;expireUnboundPigeonReceipt();assert !RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        System.now++;expireUnboundPigeonReceipt();assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        assert Log.success.isEmpty() : "timeout reported reward success";
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("bound")));System.now+=172800000L;expireUnboundPigeonReceipt();
        assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).contains("bound") : "bound receipt expired";
        reset();pending();expireUnboundPigeonReceipt();System.now+=86400000L;RuntimeInfo.writable=false;expireUnboundPigeonReceipt();
        assert !RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "failed expiry write lost receipt";
        for(int type=0;type<3;type++) {
            reset();f.farmNpcType.value=type;pending();bindPigeonFeedback(new JSONArray().put(credit("bound")));
            reply("queryCreditFeedback",credits());if(type>0)reply("syncAnimalStatus",farm(pigeon(88)));
            f.manageFarmNpc();assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty()&&calls("queryCreditFeedback")==1&&calls("sendBackAnimal")==0 : "changed/disabled config abandoned receipt";
        }
        f.farmNpcType.value=3;
        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm(pigeon(88)),farm(),farm(),farm(pigeon(0)));
        reply("queryListV3",tasks(task("r")),tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");
        reply("sendBackAnimal","{\"error\":\"RPC_SKIPPED\"}","{\"memo\":\"SUCCESS\"}");
        reply("queryCreditFeedback",credits(credit("reward")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");
        f.manageZhimaPigeon();assert RuntimeInfo.getInstance().getString("farm::npcFirstRunDay").isEmpty()&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty()&&!Status.hasFlagToday("farm::npcActionUnconfirmed");
        assert calls("syncAnimalStatus")==1 : "local skip still queried farm";
        f.manageZhimaPigeon();assert calls("hireAnimal")==1 : "local skip prevented retry after pause";

        reset();RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,new JSONObject().put("feedbackId","").put("createdAt",System.currentTimeMillis()-86400000L).toString());
        assert bindPigeonFeedback(new JSONArray())&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "expired unbound receipt has no exit";

        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm(pigeon(88)));
        reply("queryListV3",tasks(task("rollback")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");
        reply("sendBackAnimal","{\"memo\":\"FAIL\"}");f.manageZhimaPigeon();
        assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "confirmed unchanged pigeon left phantom receipt";
        assert Status.hasFlagToday("farm::npcActionUnconfirmed")&&calls("hireAnimal")==0 : "rollback cleared action guard";
        for(int scenario=0;scenario<8;scenario++) {
            reset();JSONObject after=pigeon(88);
            if(scenario==2)after.put("beHiredEndTime",System.currentTimeMillis()+86400000L);
            if(scenario==3)after.put("npcBizReward",0);
            if(scenario==6)after.put("npcBizReward",89);
            if(scenario==7)after.put("npcBizReward","NaN");
            ApplicationHook.failAfterSend=scenario==1;ApplicationHook.bindAfterSend=scenario==4;
            reply("syncAnimalStatus",farm(pigeon(88)),scenario==0?"{}":farm(after));
            reply("queryListV3",tasks(task("rollback")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");
            reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");f.manageZhimaPigeon();
            assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty()==(scenario==5||scenario==6) : "unsafe rollback scenario "+scenario;
            assert Status.hasFlagToday("farm::npcActionUnconfirmed")&&calls("hireAnimal")==0;
        }
        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm(pigeon(88)));
        reply("queryListV3",tasks(task("r")),tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"FAIL\"}");
        f.manageZhimaPigeon();f.manageZhimaPigeon();
        assert calls("sendBackAnimal")==0&&calls("taskFeedback")==1;
        assert Log.records.stream().anyMatch(x->x.contains("授权反馈失败"));
        assert Log.records.stream().anyMatch(x->x.contains("今日授权已尝试"));
        assert Log.records.stream().noneMatch(x->x.contains("开始请求遣返")||x.contains("遣返后重雇"));
        reset();reply("queryListV3",tasks(task("")));reply("joinActivity","{\"resultCode\":\"FAIL\"}");assert !f.authorizePigeonHire();
        assert Log.records.stream().anyMatch(x->x.contains("加入炼金任务响应失败"));
        reset();reply("queryListV3",tasks(task("")),tasks(task("")));reply("joinActivity","{\"resultCode\":\"SUCCESS\"}");assert !f.authorizePigeonHire();
        assert Log.records.stream().anyMatch(x->x.contains("回查无有效记录"));
        reset();pending();reply("syncAnimalStatus",farm(pigeon(88)),farm(pigeon(88)));reply("queryCreditFeedback",credits(),credits());
        f.manageZhimaPigeon();f.manageZhimaPigeon();bindPigeonFeedback(new JSONArray());bindPigeonFeedback(new JSONArray());
        assert Log.records.size()==1 : "pending receipt logs repeat across farm/member callers: "+Log.records;
        reset();JSONObject worker=new JSONObject().put("animalId","worker").put("subAnimalType","WORK").put("currentFarmId","self");
        reply("syncAnimalStatus",farm(worker,new JSONObject(worker.toString()).put("animalId","worker2")),farm(worker,new JSONObject(worker.toString()).put("animalId","worker2")));
        f.manageZhimaPigeon();f.manageZhimaPigeon();assert calls("hireAnimal")==0&&Log.records.size()==1&&Log.records.get(0).contains("名额已满");


        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm());reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");
        reply("queryCreditFeedback",credits(credit("old")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");
        reply("queryListV3",tasks(task("unavailable").put("finishFlag",true)));
        f.manageZhimaPigeon();assert calls("sendBackAnimal")==0 : "pigeon evicted before checking rehire qualification";
        for(Object end:new Object[]{System.currentTimeMillis()+86400000L,System.currentTimeMillis()+259200000L,JSONObject.NULL,"bad",0,System.currentTimeMillis()/1000}) {
            reset();reply("syncAnimalStatus",farm(pigeon(88).put("beHiredEndTime",end)),farm());
            reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");reply("queryCreditFeedback",credits(credit("id")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");
            f.manageZhimaPigeon();assert calls("sendBackAnimal")==0 : "full pigeon removed with protected/unknown expiry";
        }
        reset();RuntimeInfo.writable=false;assert !RuntimeInfo.getInstance().putVerified("key","new")&&RuntimeInfo.getInstance().getString("key").isEmpty();
        RuntimeInfo.writable=true;assert RuntimeInfo.getInstance().putVerified("key","old");RuntimeInfo.writable=false;assert !RuntimeInfo.getInstance().putVerified("key","new")&&RuntimeInfo.getInstance().getString("key").equals("old");
        reset();reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.authorizePigeonHire();
        JSONObject sent=ApplicationHook.calls.get(1);assert sent.optString("sceneCode").equals("alchemy")&&sent.optString("version").equals("alchemy")&&sent.optString("bizType").equals("LIFE_RECORD");
        reset();reply("queryListV3",tasks(task("")),tasks(task("joined")));reply("joinActivity","{\"resultCode\":\"SUCCESS\",\"data\":{\"recordId\":\"joined\"}}");reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.authorizePigeonHire();
        assert calls("joinActivity")==1;
        reset();reply("queryListV3",tasks(task("r").put("finishFlag",true)));assert !f.authorizePigeonHire()&&calls("taskFeedback")==0;
        reset();reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"FAIL\"}");assert !f.authorizePigeonHire();
        reset();pending();reply("syncAnimalStatus",farm(pigeon(0)),farm(pigeon(0)));reply("queryCreditFeedback",credits());
        reply("listFarmTask","{\"memo\":\"SUCCESS\",\"farmTaskList\":[]}");f.manageZhimaPigeon();
        assert calls("listFarmTask")==1&&calls("sendBackAnimal")==0 : "pending reward blocked independent pigeon tasks";
        assert isUnrelatedPigeonFeedback(credit("other").put("cateId","OTHER"));
        assert !isUnrelatedPigeonFeedback(credit("pigeon"))&&!isUnrelatedPigeonFeedback(new JSONObject())&&!isUnrelatedPigeonFeedback(null);
        reset();pending();assert !bindPigeonFeedback(new JSONArray());
        assert !bindPigeonFeedback(new JSONArray().put(credit("a")).put(credit("b")));
        RuntimeInfo.writable=false;assert !bindPigeonFeedback(new JSONArray().put(credit("id")));RuntimeInfo.writable=true;
        assert bindPigeonFeedback(new JSONArray().put(credit("id")));assert RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).contains("id");
        String disk=RuntimeInfo.saved.get("self");RuntimeInfo.instance=new RuntimeInfo();RuntimeInfo.instance.joAll=new JSONObject(disk);RuntimeInfo.instance.joCurrent=RuntimeInfo.instance.joAll.optJSONObject("self");
        reply("queryCreditFeedback",credits(credit("id")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");assert f.collectPigeonReceipt()&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        assert calls("collectCreditFeedback")==1;
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));Status.flagToday("farm::npcActionUnconfirmed");
        reply("queryCreditFeedback",credits());assert f.collectPigeonReceipt();
        assert Status.hasFlagToday("farm::npcActionUnconfirmed") : "collecting old credit cleared uncertain hire guard";
        reset();pending();reply("queryCreditFeedback",credits(credit("id")),credits(credit("id")));reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");assert !f.collectPigeonReceipt();
        Status.flags.clear();assert !RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "receipt survives day rollover";
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));reply("queryCreditFeedback","{}");assert !f.collectPigeonReceipt();
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));JSONObject[] twenty=new JSONObject[20];for(int i=0;i<20;i++)twenty[i]=credit("other"+i);reply("queryCreditFeedback",credits(twenty));assert !f.collectPigeonReceipt() : "full page is not proof of disappearance";
        reset();pending();bindPigeonFeedback(new JSONArray().put(credit("id")));reply("queryCreditFeedback",credits());RuntimeInfo.writable=false;assert !f.collectPigeonReceipt()&&!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty();
        reset();reply("syncAnimalStatus",farm(),farm(),farm(pigeon(0)));reply("queryListV3",tasks(task("r")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");f.manageZhimaPigeon();assert calls("hireAnimal")==1;
        reset();pending();reply("syncAnimalStatus",farm(),farm(),farm(pigeon(0)));reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");reply("queryCreditFeedback",credits(credit("id")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");
        reply("queryListV3",tasks(task("next")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");
        f.manageZhimaPigeon();assert calls("sendBackAnimal")==0&&calls("hireAnimal")==1&&RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "confirmed old departure receipt must continue to hire";
        reset();reply("syncAnimalStatus",farm(pigeon(88)),farm(),farm(),farm(pigeon(0)));reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");
        reply("queryCreditFeedback",credits(credit("renew")),credits());reply("collectCreditFeedback","{\"resultCode\":\"SUCCESS\"}");
        reply("queryListV3",tasks(task("next")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");
        f.manageZhimaPigeon();assert calls("sendBackAnimal")==1&&calls("hireAnimal")==1 : "expiring pigeon was not renewed on first daily run";
        int authorized=-1,departed=-1;for(int i=0;i<ApplicationHook.calls.size();i++){
            if("taskFeedback".equals(ApplicationHook.calls.get(i).optString("rpc")))authorized=i;
            if("sendBackAnimal".equals(ApplicationHook.calls.get(i).optString("rpc")))departed=i;
        }
        assert authorized>=0&&authorized<departed : "authorization must precede departure";
        reply("syncAnimalStatus",farm(pigeon(88)));new PigeonCheck().manageZhimaPigeon();assert calls("sendBackAnimal")==1 : "same-day restart repeated pigeon renewal";
        reset();reply("syncAnimalStatus",farm(pigeon(0)),farm(pigeon(0)),farm(),farm(),farm(pigeon(0)));
        reply("listFarmTask","{\"memo\":\"SUCCESS\",\"farmTaskList\":[]}");reply("sendBackAnimal","{\"memo\":\"SUCCESS\"}");
        reply("queryListV3",tasks(task("next")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");
        f.manageZhimaPigeon();assert calls("sendBackAnimal")==1&&calls("hireAnimal")==1&&calls("queryCreditFeedback")==0 : "not-full zero-reward pigeon renewal waited on nonexistent receipt";
        reset();RuntimeInfo.writable=false;reply("syncAnimalStatus",farm(pigeon(88)));reply("queryListV3",tasks(task("next")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");f.manageZhimaPigeon();assert calls("sendBackAnimal")==0;
        reset();pending();reply("syncAnimalStatus",farm(),farm(),farm(pigeon(0)));reply("queryCreditFeedback",credits());
        reply("queryListV3",tasks(task("next")));reply("taskFeedback","{\"resultCode\":\"SUCCESS\"}");reply("hireAnimal","{\"memo\":\"SUCCESS\"}");
        f.manageZhimaPigeon();assert calls("hireAnimal")==1&&!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "pending reward blocked restoring pigeon";
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
    "private static long npcTaskNumber(","private static boolean canRenewFarmNpc(","private static boolean beginFarmNpcDay(","private JSONObject queryPigeonAlchemyTask(","private boolean authorizePigeonHire(","private static void logPigeonOnce(","private static void expireUnboundPigeonReceipt(","private static boolean restoreSkippedNpcAction(",
    "public static boolean isUnrelatedPigeonFeedback(","public static boolean bindPigeonFeedback(","private JSONArray queryPigeonFeedback(","private boolean collectPigeonReceipt(",
    "private JSONArray queryPigeonFarmTasks(","private void runPigeonFarmTasks(","private void manageFarmNpc(","private void manageZhimaPigeon(")))
code=code.replace("@@FARMRPC@@","\n".join(method(rpc,s) for s in (
    "public static String queryNpcFarm(","public static String hireNpcAnimal(",
    "public static String sendBackNpcAnimal(String animalId, String currentFarmId, String masterFarmId, String source)",
    "public static String sendBackNpcAnimal(String animalId, String currentFarmId, String masterFarmId)",
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
