"""Replay production market/rental gates and shared fertilizer balance; no live RPC."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
jar = next((cache / "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar"))
code = r'''
import java.util.*;
import org.json.*;
public class EnergyManureCheck {
    static final String TAG="check";
    static class MyUtils {
        static JSONObject newJSONObject(String s) { try { return new JSONObject(s); } catch(Exception e) { return new JSONObject(); } }
    }
    static class Log {
        static List<String> lines=new ArrayList<>();
        static void i(String tag,String s) { lines.add(s); }
        static void record(String s) { lines.add(s); }
        static void forest(String s) { lines.add(s); }
        static void farm(String s) { lines.add(s); }
        static void err(String t,String s,Throwable e) { throw new AssertionError(s,e); }
    }
    static class TimeUtil { static void sleep(long n) {} }
    static class Toast { static void show(String s) {} }
    static class MessageUtil {
        static boolean checkSuccess(String t,JSONObject j) { return Boolean.TRUE.equals(j.opt("success")); }
        static boolean checkResultCode(String t,JSONObject j) { return "SUCCESS".equals(j.optString("resultCode")); }
    }
    static class ErrorMessages {
        static final String CODE_UNSUPPORTED_RPC="400000040";
        static int failures;
        static void recordFailure(String tag,String memo) { failures++; }
        static boolean shouldLogServerBusy(String tag) { return true; }
        @@ERRORS@@
    }
    static class Statistics {
        enum DataType { COLLECTED }
        static int total;
        static void addData(DataType t,int n) { total+=n; }
    }
    static int totalCollected;
    static class GreenLifeRpcCall {
        static Queue<String> consult=new ArrayDeque<>(), send=new ArrayDeque<>();
        static int sent;
        static String consultForSendEnergyByAction(String type) { return consult.remove(); }
        static String sendEnergyByAction(String type) { sent++; return send.remove(); }
    }
    static class AntForestRpcCall {
        static String chance; static int generated;
        static String checkUserSecondSceneChance() { return chance; }
        static String generateEnergy() { generated++; return new JSONObject().put("success",true).put("resultObject",
            new JSONObject().put("energyGenerated",new JSONObject().put("zulinshangpinliulan",5))).toString(); }
    }
    @@FOREST@@
    static class Field { boolean value; Boolean getValue() { return value; } }
    enum PlantScene { main,yeb; String nickname() { return name(); } }
    static class Status {
        static boolean hasFlagToday(String s) { return false; }
        static void flagToday(String s,String uid) {}
    }
    static class AntOrchardRpcCall {
        static String balance; static int queries,progress;
        static String orchardSyncIndex() { queries++; return balance; }
        static String orchardIndex() { return new JSONObject().put("resultCode","SUCCESS").put("yebScenePlantInfo",
            new JSONObject().put("plantProgressInfo",new JSONObject().put("spreadProgress",progress))).toString(); }
        static String orchardSpreadManure(String s,boolean b,String w) { return "{}"; }
    }
    static class Orchard {
        static final int MAIN_SPREAD_DAILY_LIMIT=200,BATCH_SPREAD_SIZE=5,MAIN_SPREAD_BURST_LIMIT=204;
        static int spreadHappyPoint=-1,spreadWateringCost=-1;
        static boolean spreadShortageLoggedThisRun,spreadLimitLoggedThisRun;
        boolean spreadUseBatchThisTime; int fertilizerProgress; String userId="self";
        Field useBatchSpread=new Field(); Map<String,Integer> orchardSpreadManureSceneList=new HashMap<>();
        String getWua() { return ""; }
        @@ORCHARD@@
    }
    static String response(Object allowed,int amount) {
        return new JSONObject().put("success",true).put("data",new JSONObject().put("canSendEnergy",allowed)
            .put("receivedEnergyAmount",amount).put("token","PRIVATE_PAYLOAD")).toString();
    }
    static void resetMarket() { GreenLifeRpcCall.consult.clear(); GreenLifeRpcCall.send.clear();
        GreenLifeRpcCall.sent=0; Statistics.total=0; totalCollected=0; Log.lines.clear(); }
    static void balance(int have,int cost,int left) {
        AntOrchardRpcCall.balance=new JSONObject().put("resultCode","SUCCESS").put("farmMainAccountInfo",
            new JSONObject().put("happyPoint",Integer.toString(have)).put("wateringCost",cost).put("wateringLeftTimes",left)).toString();
    }
    public static void main(String[] args) {
        Log.lines.clear();
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("error","RPC_SKIPPED").put("code","400000040"),"memo");
        assert Log.lines.isEmpty() && ErrorMessages.failures==0 : "local unsupported check wrote runtime log";
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("code","400000040").put("memo","PRIVATE_PAYLOAD"),"memo");
        assert ErrorMessages.failures==0 && Log.lines.size()==1 && !Log.lines.get(0).contains("PRIVATE_PAYLOAD");
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("code","OTHER").put("memo","failed"),"memo");
        assert ErrorMessages.failures==1;
        resetMarket(); for(int i=0;i<3;i++) { GreenLifeRpcCall.consult.add(response(true,0)); GreenLifeRpcCall.send.add(response(true,10)); }
        sendEnergyByAction("GREEN_LIFE"); assert GreenLifeRpcCall.sent==3 && Statistics.total==30 && totalCollected==30;
        resetMarket(); GreenLifeRpcCall.consult.add(response(true,0)); GreenLifeRpcCall.consult.add(response(false,0));
        GreenLifeRpcCall.send.add(response(true,8)); sendEnergyByAction("GREEN_LIFE"); assert GreenLifeRpcCall.sent==1 && totalCollected==8;
        for(String invalid : List.of("{}","invalid", "{\"success\":true}",response("true",0),response(JSONObject.NULL,0))) {
            resetMarket(); GreenLifeRpcCall.consult.add(invalid); sendEnergyByAction("GREEN_LIFE"); assert GreenLifeRpcCall.sent==0;
        }
        for(String invalid : List.of("{}","{\"success\":true}",response(false,0),response(true,0),response(true,-1))) {
            resetMarket(); GreenLifeRpcCall.consult.add(response(true,0)); GreenLifeRpcCall.send.add(invalid);
            sendEnergyByAction("GREEN_LIFE"); assert GreenLifeRpcCall.sent==1 && totalCollected==0;
            assert Log.lines.stream().noneMatch(s->s.contains("PRIVATE_PAYLOAD"));
        }
        for(Object flag : new Object[]{false,"true",JSONObject.NULL,true}) {
            AntForestRpcCall.generated=0;
            AntForestRpcCall.chance=new JSONObject().put("success",true).put("resultObject",new JSONObject().put("canSendEnergy",flag)).toString();
            greenRent(); assert AntForestRpcCall.generated==(Boolean.TRUE.equals(flag)?1:0);
        }
        AntForestRpcCall.generated=0; AntForestRpcCall.chance="{\"success\":true}"; greenRent(); assert AntForestRpcCall.generated==0;
        Orchard o=new Orchard(); o.orchardSpreadManureSceneList.put("main",100); o.orchardSpreadManureSceneList.put("yeb",100);
        balance(9,10,200); assert !o.canSpreadManure(PlantScene.main); int queries=AntOrchardRpcCall.queries;
        assert !o.canSpreadManure(PlantScene.yeb) && AntOrchardRpcCall.queries==queries;
        Orchard.spreadHappyPoint=-1; balance(9,10,200); assert !o.canSpreadManure(PlantScene.yeb);
        Orchard.spreadHappyPoint=-1; balance(10,10,200); assert o.canSpreadManure(PlantScene.yeb);
        o.doSpreadManure(PlantScene.yeb); assert Orchard.spreadHappyPoint==-1;
        balance(0,10,200); assert !o.canSpreadManure(PlantScene.yeb);
        for(String invalid : List.of("{}","{\"resultCode\":\"SUCCESS\"}","{\"resultCode\":\"SUCCESS\",\"farmMainAccountInfo\":{}}")) {
            Orchard.spreadHappyPoint=-1; AntOrchardRpcCall.balance=invalid; assert !o.canSpreadManure(PlantScene.yeb);
            assert !o.canSpreadManure(PlantScene.main);
        }
        o.useBatchSpread.value=true; AntOrchardRpcCall.progress=4;
        Orchard.spreadHappyPoint=-1; balance(49,10,196); assert o.canSpreadManure(PlantScene.yeb) && !o.spreadUseBatchThisTime;
        Orchard.spreadHappyPoint=-1; balance(50,10,196); assert o.canSpreadManure(PlantScene.yeb) && o.spreadUseBatchThisTime;
        balance(50,0,196); assert !o.canSpreadManure(PlantScene.main);
        balance(50,10,-1); assert !o.canSpreadManure(PlantScene.main);
        System.out.println("PASS market rounds, rental gates, invalid responses, redaction, shared balance and batch fallback");
    }
}
'''
for key, path, signatures in (
    ("@@ERRORS@@", "util/MessageUtil.java", ("public static void printErrorMessage(", "public static boolean isUnsupportedRpc(", "public static boolean isServerBusy(")),
    ("@@FOREST@@", "model/task/antForest/AntForestV2.java", ("private static void sendEnergyByAction(", "private static void greenRent(")),
    ("@@ORCHARD@@", "model/task/antOrchard/AntOrchard.java", ("private boolean canSpreadManure(", "private boolean doSpreadManure(",
        "private static void logSpreadShortage(", "private int targetSpreadTimes(", "private static boolean shouldBatchSpread(")),
):
    code = code.replace(key, "\n".join(method(path, signature) for signature in signatures))
with tempfile.TemporaryDirectory(prefix="sesame-energy-manure-") as tmp:
    source = Path(tmp) / "EnergyManureCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "EnergyManureCheck"], check=True)
