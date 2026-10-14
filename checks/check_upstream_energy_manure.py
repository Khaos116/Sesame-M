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
    static final int ENERGY_RECORD_PAGE_SIZE=200,ENERGY_RECORD_MAX_PAGES=20;
    static class TaskCancelledException extends RuntimeException {}
    static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
    static class TaskAlternative {static String describe(JSONObject j){return "invalid";}}
    static class MyUtils {
        static int dayOffset;static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.add(Calendar.DAY_OF_YEAR,dayOffset);return c;}
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
        static int updates;
        static int setDayCollectedFromServer(int n){updates++;int delta=n-total;total=n;return delta;}
        static void save(){}
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
        static Queue<String> records=new ArrayDeque<>();static boolean cancelRecords,switchAccount;
        static String pagingQueryEnergyRecord(long id,int size){
            if(cancelRecords)throw new TaskCancelledException();
            if(switchAccount)UserIdMap.uid="B";
            return records.remove();
        }
        static String chance; static int generated;
        static String checkUserSecondSceneChance() { return chance; }
        static String generateEnergy() { generated++; return new JSONObject().put("success",true).put("resultObject",
            new JSONObject().put("energyGenerated",new JSONObject().put("zulinshangpinliulan",5))).toString(); }
    }
    @@FOREST@@
    static class Field { boolean value; Boolean getValue() { return value; } }
    enum PlantScene { main,yeb; String nickname() { return name(); } }
    static class Status {
        static Set<String> flags=new HashSet<>();
        static String key(String uid,String s){return uid+"|"+MyUtils.getInstance().get(Calendar.DAY_OF_YEAR)+"|"+s;}
        static boolean hasFlagToday(String s) { return flags.contains(key(UserIdMap.uid,s)); }
        static void flagToday(String s,String uid) { flags.add(key(uid,s)); }
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
    static JSONObject record(long date,int energy){return new JSONObject().put("bizDate",date).put("energy",energy);}
    static String page(boolean more,long next,Object... rows){return new JSONObject().put("success",true)
        .put("hasMore",more).put("nextId",next).put("recordVOList",new JSONArray(Arrays.asList(rows))).toString();}
    static void records(String... pages){AntForestRpcCall.records.clear();Collections.addAll(AntForestRpcCall.records,pages);
        Statistics.total=99;Statistics.updates=0;UserIdMap.uid="A";AntForestRpcCall.switchAccount=false;}
    static void energyRecords(){
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        Calendar day=MyUtils.getInstance();day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        long today=day.getTimeInMillis();EnergyManureCheck work=new EnergyManureCheck();
        records(page(true,10,record(today+1,5)),page(false,0,record(today+2,7)));work.syncTodayCollectedFromServer();assert Statistics.total==12&&Statistics.updates==1;
        records(page(true,10,record(today,5),record(today-1,100)));work.syncTodayCollectedFromServer();assert Statistics.total==5;
        records(page(true,10,record(today,5)),page(false,0,record(today,-7),record(today,0),record(today,3)));work.syncTodayCollectedFromServer();assert Statistics.total==8:"income only, ignore spending and zero";
        for(String bad:List.of("invalid","{}",page(true,10,record(today,7)),page(false,0,new JSONObject()),page(false,0,record(today,1).put("energy","invalid")))){
            records(page(true,10,record(today,5)),bad);work.syncTodayCollectedFromServer();assert Statistics.total==99&&Statistics.updates==0:bad;
        }
        records(page(false,0,record(today,Integer.MAX_VALUE),record(today,1)));work.syncTodayCollectedFromServer();assert Statistics.updates==0;
        records(page(false,0,record(today,1).put("energy",2147483648L)));work.syncTodayCollectedFromServer();assert Statistics.updates==0:"large income must not wrap to negative and be skipped";
        records();for(int i=1;i<=20;i++)AntForestRpcCall.records.add(page(true,i,record(today,1)));
        work.syncTodayCollectedFromServer();assert Statistics.updates==0:"truncated pages overwrote totals";
        records(page(false,0,record(today,8)));AntForestRpcCall.switchAccount=true;work.syncTodayCollectedFromServer();assert Statistics.updates==0;
        Status.flags.clear();Log.lines.clear();
        records(page(false,0));work.syncTodayCollectedFromServer();
        records(page(false,0,record(today-1,5)));work.syncTodayCollectedFromServer();
        assert Log.lines.isEmpty()&&Statistics.updates==0:"Normal empty day must be silent and retain totals";
        for(int i=0;i<2;i++){records("{success:true,recordVOList:[]}");work.syncTodayCollectedFromServer();}
        assert Log.lines.size()==1&&Log.lines.get(0).contains("分页标记"):"Repeated bad pagination must log once";
        records("{success:true,recordVOList:[]}");UserIdMap.uid="B";work.syncTodayCollectedFromServer();
        assert Log.lines.size()==2:"Different account must retain its own diagnostic";
        records(page(false,0,new JSONObject()));work.syncTodayCollectedFromServer();
        assert Log.lines.size()==3&&Log.lines.get(2).contains("明细字段"):"Different reason must be logged";
        records(page(false,0,new JSONObject()));AntForestRpcCall.switchAccount=true;work.syncTodayCollectedFromServer();
        assert Log.lines.size()==3:"Stale account must not emit a diagnostic";
        MyUtils.dayOffset=1;
        records("{success:true,recordVOList:[]}");work.syncTodayCollectedFromServer();
        assert Log.lines.size()==4:"Next GMT+8 day must allow diagnostics again";
        MyUtils.dayOffset=0;
        AntForestRpcCall.cancelRecords=true;try{work.syncTodayCollectedFromServer();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}AntForestRpcCall.cancelRecords=false;
    }
    public static void main(String[] args) {
        energyRecords();
        Log.lines.clear();
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("error","RPC_SKIPPED").put("code","400000040"),"memo");
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("error","RPC_SKIPPED").put("localRuleSkip",true).put("memo","GR local rule"),"memo");
        assert Log.lines.isEmpty() && ErrorMessages.failures==0 : "local unsupported check wrote runtime log";
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("code","400000040").put("memo","PRIVATE_PAYLOAD"),"memo");
        assert ErrorMessages.failures==0 && Log.lines.size()==1 && !Log.lines.get(0).contains("PRIVATE_PAYLOAD");
        ErrorMessages.printErrorMessage(TAG,new JSONObject().put("code","OTHER").put("memo","failed"),"memo");
        assert ErrorMessages.failures==1;
        resetMarket(); for(int i=0;i<3;i++) { GreenLifeRpcCall.consult.add(response(true,0)); GreenLifeRpcCall.send.add(response(true,10)); }
        sendEnergyByAction("GREEN_LIFE"); assert GreenLifeRpcCall.sent==1 && Statistics.total==10 && totalCollected==10;
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
        System.out.println("PASS single market send, rental gates, invalid responses, redaction, shared balance and batch fallback");
    }
}
'''
for key, path, signatures in (
    ("@@ERRORS@@", "util/MessageUtil.java", ("public static void printErrorMessage(", "public static boolean isUnsupportedRpc(", "public static boolean isServerBusy(")),
    ("@@FOREST@@", "model/task/antForest/AntForestV2.java", ("private static void sendEnergyByAction(", "private static void greenRent(", "private void syncTodayCollectedFromServer(", "private void logEnergyRecordIssue(")),
    ("@@ORCHARD@@", "model/task/antOrchard/AntOrchard.java", ("private boolean canSpreadManure(", "private boolean doSpreadManure(",
        "private static void logSpreadShortage(", "private int targetSpreadTimes(", "private static boolean shouldBatchSpread(")),
):
    code = code.replace(key, "\n".join(method(path, signature) for signature in signatures))
with tempfile.TemporaryDirectory(prefix="sesame-energy-manure-") as tmp:
    source = Path(tmp) / "EnergyManureCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(source)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "EnergyManureCheck"], check=True)
