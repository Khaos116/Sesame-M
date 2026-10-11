"""Compile real ordinary-NPC flow/RPCs and replay farm states without network calls."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

farm = "model/task/antFarm/AntFarm.java"
rpc = "model/task/antFarm/AntFarmRpcCall.java"
source = (SOURCE / farm).read_text(encoding="utf-8")
assert 'new ChoiceModelField("farmNpcType"' in source, "NPC type option/entry is missing"
assert 'step("NPC小鸡"' in source and source.index('step("NPC小鸡"') < source.index('step("雇佣小鸡"')
assert 'SubAnimalType.NPC.name()' in method(farm, "private void sendBackAnimal()"), "ordinary send-back must preserve NPC rewards"
assert "catch (TaskCancelledException e) { throw e;" in method(farm, "public void run()"), "farm run must pass cancellation to ModelTask"
code = r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class NpcCheck {
    static class System { static long now=1791676800000L; static long currentTimeMillis(){return now;} static final java.io.PrintStream out=java.lang.System.out; }
    static final String TAG="check", VERSION="1.8.2302070202.46";
    @@CONFIG@@
    String ownerFarmId="self";
    static class Field { int n; Field(int n) {this.n=n;} int getValue(){return n;} }
    Field farmNpcType=new Field(1);
    static class RuntimeInfo {
        static final RuntimeInfo instance=new RuntimeInfo();static String uid="A";static boolean writable=true;
        static Map<String,Map<String,String>> disk=new HashMap<>();
        static RuntimeInfo getInstance(){return instance;}
        String getString(String key){return disk.getOrDefault(uid,Collections.emptyMap()).getOrDefault(key,"");}
        boolean putVerified(String key,String value){if(!writable)return false;if(value==null)disk.computeIfAbsent(uid,k->new HashMap<>()).remove(key);else disk.computeIfAbsent(uid,k->new HashMap<>()).put(key,value);return true;}
    }
    private boolean collectPigeonReceipt(){return false;}
    private void manageZhimaPigeon(){throw new AssertionError("ordinary NPC tests entered pigeon flow");}
    static class MyUtils { static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(System.now);return c;}
        static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }
    static class TimeUtil {
        static int waits, cancelAt=-1;
        static void sleep(long n){if(++waits==cancelAt)throw new io.github.aw1y2z.sesame.util.TaskCancelledException();}
    }
    static class Status {
        static Set<String> flags=new HashSet<>();
        static boolean hasFlagToday(String s){return flags.contains(s);}
        static void flagToday(String s){flags.add(s);}
        static void clearFlag(String s){flags.remove(s);}
    }
    static class RpcRequestGuard {
        static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}
        static String errorMessage(JSONObject j){return j.optString("resultCode");}
    }
    static class Log {
        static List<String> successes=new ArrayList<>(), records=new ArrayList<>();
        static void record(String s){records.add(s);}
        static void farm(String s){successes.add(s);}
        static void err(String tag,String msg,Throwable t){throw new AssertionError(t);}
    }
    static class ApplicationHook {
        static Queue<String> queries=new ArrayDeque<>(), actions=new ArrayDeque<>();
        static List<JSONObject> calls=new ArrayList<>();
        static String requestString(String name,String args){
            JSONObject body=new JSONArray(args).optJSONObject(0); body.put("rpc",name); calls.add(body);
            return name.endsWith("syncAnimalStatus")?queries.remove():actions.remove();
        }
    }
    static class AntFarmRpcCall {
        static int normalSends;
        static String farmId2UserId(String s){return s;}
        static String sendBackAnimal(String way,String id,String current,String master){normalSends++;return "{\"memo\":\"SUCCESS\"}";}
        @@RPC@@
    }
    static class NormalSendBackCheck {
        static class Animal {
            String animalInteractStatus="STEALING",subAnimalType,masterFarmId="master",currentFarmId="self",animalId="id";
            Animal(String type){subAnimalType=type;}
        }
        enum SubAnimalType { NORMAL, GUEST, WORK, NPC }
        enum AnimalInteractStatus { STEALING }
        static class SendBackAnimalType { static final int NONE=0, BACK=1; }
        static class SendBackAnimalWay { static final int HIT=1; static final String[] nickNames={"NORMAL","HIT"}; }
        static class UserIdMap { static String getMaskName(String s){return s;} }
        static class MessageUtil { static boolean checkMemo(String tag,JSONObject j){return "SUCCESS".equals(j.optString("memo"));} }
        static class ListField { Set<String> getValue(){return Collections.emptySet();} }
        Animal[] animals;
        Field sendBackAnimalType=new Field(2),sendBackAnimalWay=new Field(0);
        ListField sendBackAnimalList=new ListField(); int foodStock;
        String ownerUserId="self";
        boolean manualFarmOwner(String uid){return true;}
        @@NORMAL@@
    }
    @@METHODS@@
    @@STEP@@
    static JSONObject animal(String id,String type,double reward,boolean full){
        return new JSONObject().put("animalId",id).put("subAnimalType",type).put("currentFarmId","self")
            .put("masterFarmId","master").put("npcBizReward",reward).put("reachNpcBizRewardLimit",full)
            .put("beHiredEndTime",System.currentTimeMillis()+43200000L);
    }
    static String state(JSONObject... animals){
        return new JSONObject().put("memo","SUCCESS").put("subFarmVO",new JSONObject().put("farmId","self")
            .put("animals",new JSONArray(Arrays.asList(animals)))).toString();
    }
    static long mutations(){return ApplicationHook.calls.stream().filter(j->!j.optString("rpc").endsWith("syncAnimalStatus")).count();}
    static void reset(NpcCheck f,String... states){
        f.farmNpcType.n=1; RuntimeInfo.disk.clear(); RuntimeInfo.uid="A";RuntimeInfo.writable=true;System.now=1791676800000L;Status.flags.clear(); Log.successes.clear(); Log.records.clear();
        ApplicationHook.calls.clear(); ApplicationHook.queries.clear(); ApplicationHook.actions.clear();
        ApplicationHook.queries.addAll(Arrays.asList(states)); TimeUtil.waits=0; TimeUtil.cancelAt=-1;
    }
    static void ack(int n){for(int i=0;i<n;i++)ApplicationHook.actions.add("{\"memo\":\"SUCCESS\"}");}
    public static void main(String[] args) throws Exception {
        NpcCheck f=new NpcCheck(); JSONObject gold=animal(FARM_NPC_IDS[1],"NPC",0,false);
        JSONObject field=animal(FARM_NPC_IDS[2],"NPC",0,false), full=animal(FARM_NPC_IDS[1],"NPC",120,true);
        JSONObject worker=animal("worker","WORK",0,false);
        for(Object end:new Object[]{System.currentTimeMillis()+86400000L,System.currentTimeMillis()+259200000L,
                JSONObject.NULL,"invalid",0,-1,System.currentTimeMillis()/1000,true}) {
            JSONObject protectedNpc=animal(FARM_NPC_IDS[1],"NPC",120,true).put("beHiredEndTime",end);
            reset(f,state(protectedNpc),state(),state(gold));ack(2);f.manageFarmNpc();
            assert mutations()==0 : "full NPC removed despite protected/unknown expiry: "+end;
            assert Log.records.stream().anyMatch(s->s.contains("无法事前确认可再次雇佣")) : "full NPC incorrectly gated by expiry";
            protectedNpc=animal(FARM_NPC_IDS[2],"NPC",0,false).put("beHiredEndTime",end);
            reset(f,state(protectedNpc),state(),state(gold));ack(2);f.manageFarmNpc();
            assert mutations()==0 : "configuration replaced protected/unknown NPC: "+end;
        }
        reset(f);f.farmNpcType.n=0;f.manageFarmNpc();assert ApplicationHook.calls.isEmpty();
        f.farmNpcType.n=99;f.manageFarmNpc();assert ApplicationHook.calls.isEmpty();
        for(String bad:new String[]{"garbage","{}","{\"memo\":\"FAIL\"}",state().replace("self","wrong"),
            "{\"memo\":\"SUCCESS\",\"subFarmVO\":{\"farmId\":\"self\"}}",state().replace("[]","[null]")}){
            reset(f,bad);f.manageFarmNpc();assert mutations()==0;
        }
        reset(f,state(),state(gold));ack(1);f.manageFarmNpc();
        assert mutations()==1 && Status.flags.isEmpty() && Log.successes.size()==1;
        JSONObject hire=ApplicationHook.calls.get(1);
        assert hire.optString("hireActionType").equals("HIRE_IN_SELF_FARM") && hire.optBoolean("isNpcAnimal")
            && hire.optString("hireAnimalId").equals(FARM_NPC_IDS[1]) && hire.optString("source").equals(FARM_NPC_SOURCES[1]);
        reset(f,state(),state(gold));RuntimeInfo.getInstance().putVerified(PIGEON_RECEIPT_KEY,"{\"feedbackId\":\"\"}");ack(1);f.manageFarmNpc();
        assert mutations()==1&&!RuntimeInfo.getInstance().getString(PIGEON_RECEIPT_KEY).isEmpty() : "old pigeon reward blocked empty-slot hire or was discarded";
        reset(f,state(),state(field));f.farmNpcType.n=2;ack(1);f.manageFarmNpc();assert Log.successes.size()==1;
        reset(f,state(worker,animal("worker2","WORK",0,false)));f.manageFarmNpc();assert mutations()==0;
        reset(f,state(animal(FARM_NPC_IDS[1],"NPC",0,false).put("beHiredEndTime",System.currentTimeMillis()+86400000L)));f.manageFarmNpc();assert mutations()==0 && Log.successes.isEmpty();
        for(Object bad:new Object[]{"NaN","Infinity",true,-1,"1e10000","1e-10000"}){
            JSONObject invalid=animal(FARM_NPC_IDS[1],"NPC",0,true).put("npcBizReward",bad);
            reset(f,state(invalid));f.manageFarmNpc();assert mutations()==0;
        }
        JSONObject missing=animal(FARM_NPC_IDS[1],"NPC",0,false);missing.remove("reachNpcBizRewardLimit");
        reset(f,state(missing));f.manageFarmNpc();assert mutations()==0;
        reset(f,state(animal(FARM_NPC_IDS[2],"NPC",1,false)));f.manageFarmNpc();assert mutations()==0 : "pending rewards cannot be discarded";
        reset(f,state(animal("zhima-pigeon","NPC",0,false)));f.manageFarmNpc();assert mutations()==0 : "unknown NPC stays";
        reset(f,state(full),state(full),state(),state(gold));
        ApplicationHook.actions.add("{\"error\":\"RPC_SKIPPED\"}");ack(2);f.manageFarmNpc();
        assert !Status.hasFlagToday("farm::npcActionUnconfirmed")&&RuntimeInfo.getInstance().getString("farm::npcFirstRunDay").isEmpty() : "local skip consumed first run";
        f.manageFarmNpc();assert mutations()==3 : "local pause prevented later renewal";
        reset(f,state(full),state(),state(gold));ack(2);f.manageFarmNpc();
        assert mutations()==2 && Status.flags.isEmpty() : "first daily run must renew expiring special NPC";
        ApplicationHook.queries.add(state(gold));new NpcCheck().manageFarmNpc();
        assert mutations()==2 : "same-day restart must not renew twice";
        reset(f,state(gold),state(),state(gold));ack(2);f.manageFarmNpc();assert mutations()==2 : "not-full expiring NPC must renew";
        reset(f,"{}",state(gold),state(),state(gold));ack(2);f.manageFarmNpc();f.manageFarmNpc();
        assert mutations()==2 : "failed initial farm query consumed first daily assessment";
        reset(f,state(worker,gold),state(worker),state(worker,gold));ack(2);f.manageFarmNpc();assert mutations()==2;
        assert ApplicationHook.calls.stream().noneMatch(j->"worker".equals(j.optString("animalId"))) : "friend worker sent away";
        reset(f,state(gold),state(),state(gold));ApplicationHook.actions.add("{}");ack(1);f.manageFarmNpc();
        assert mutations()==2 && Status.flags.isEmpty() : "confirmed absence after lost departure response must still rehire";
        reset(f,state(animal(FARM_NPC_IDS[1],"NPC",0,false).put("beHiredEndTime",System.currentTimeMillis()+86400000L)),state(gold));
        f.manageFarmNpc();new NpcCheck().manageFarmNpc();assert mutations()==0 : "later daily run must not become first renewal";
        reset(f);TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        System.now=java.time.Instant.parse("2026-10-10T15:59:00Z").toEpochMilli();
        assert beginFarmNpcDay(true)&&!beginFarmNpcDay(true);RuntimeInfo.uid="B";assert beginFarmNpcDay(true);
        RuntimeInfo.uid="A";assert !beginFarmNpcDay(true);System.now+=120000;assert beginFarmNpcDay(true) : "first-run day must roll at GMT+8 midnight";
        reset(f,state(gold));RuntimeInfo.writable=false;f.manageFarmNpc();assert mutations()==0 : "failed first-run persistence must preserve NPC";
        reset(f,state(full),state(full));ack(1);f.manageFarmNpc();assert mutations()==1 && !Status.flags.isEmpty();
        new NpcCheck().manageFarmNpc();assert mutations()==1 : "uncertain departure retried after restart";
        assert !canRenewFarmNpc(worker,true) : "friend worker must never be renewed";
        reset(f,state(field));f.manageFarmNpc();assert mutations()==0 : "configuration cannot evict special NPC";
        reset(f,state(),state());ack(1);f.manageFarmNpc();assert Log.successes.isEmpty() && !Status.flags.isEmpty();
        f.manageFarmNpc();assert mutations()==1 : "accepted hire without arrival cannot repeat";
        reset(f,state());TimeUtil.cancelAt=2;
        try{f.step("NPC",f::manageFarmNpc);throw new AssertionError("cancel swallowed by step");}
        catch(io.github.aw1y2z.sesame.util.TaskCancelledException expected){}
        assert mutations()==0;
        reset(f);ack(1);AntFarmRpcCall.hireNpcAnimal("quoted\"\\id","source");
        assert ApplicationHook.calls.get(0).optString("hireAnimalId").equals("quoted\"\\id");
        NormalSendBackCheck normal=new NormalSendBackCheck();
        normal.animals=new NormalSendBackCheck.Animal[]{new NormalSendBackCheck.Animal("NPC"),
            new NormalSendBackCheck.Animal("WORK"),new NormalSendBackCheck.Animal("GUEST"),new NormalSendBackCheck.Animal("NORMAL")};
        normal.sendBackAnimal();assert AntFarmRpcCall.normalSends==1 : "generic send-back must only act on the ordinary visitor";
        System.out.println("PASS NPC disabled/config, slots, hire/protected full/switch, pending rewards, malformed state, leave/arrival confirmation, uncertainty, cancellation, generic send-back exclusion and RPC args");
    }
}
'''
config = "\n".join(line.strip() for line in source.splitlines() if "private static final String[] FARM_NPC_" in line or "private static final String PIGEON_" in line)
assert len(config.splitlines()) == 6
code = code.replace("@@CONFIG@@", config.replace("private ", ""))
code = code.replace("@@METHODS@@", "\n".join(method(farm, name) for name in (
    "private static boolean farmFeatureOk(", "private static void expireUnboundPigeonReceipt(","private static void logPigeonOnce(","private static boolean restoreSkippedNpcAction(", "private JSONObject queryNpcFarm(",
    "private static double npcRewardValue(", "private static JSONObject findFarmNpc(",
    "private static int npcOccupiedSlots(", "private static long npcTaskNumber(", "private static boolean canRenewFarmNpc(","private static boolean beginFarmNpcDay(", "private void manageFarmNpc(")))
code = code.replace("@@RPC@@", "\n".join(method(rpc, "public static String " + name + "(") for name in (
    "queryNpcFarm", "hireNpcAnimal", "sendBackNpcAnimal")))
code = code.replace("static int normalSends;", method(rpc, "public static String sendBackNpcAnimal(String animalId, String currentFarmId, String masterFarmId, String source)") + "\nstatic int normalSends;")
code = code.replace("@@NORMAL@@", method(farm, "private void sendBackAnimal()"))
code = code.replace("@@STEP@@", method(farm, "private void step("))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-npc-check-") as tmp:
    java = Path(tmp) / "NpcCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java), str(SOURCE / "util/TaskCancelledException.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "NpcCheck"], check=True)
