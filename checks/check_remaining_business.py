"""Replay active green-finance, sports, stall, ecology and fish production methods; no live RPC."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
jar = next((cache / "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar"))

code = r'''
import org.json.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;
public class RemainingCheck {
    static class Field<T> { T value; Field(T value) { this.value=value; } T getValue() { return value; } }
    static class TaskCancelledException extends RuntimeException { }
    static class MyUtils { static JSONObject newJSONObject() { return new JSONObject(); } static JSONObject newJSONObject(String raw) { try { return new JSONObject(raw); } catch(Exception error) { return new JSONObject(); } } static boolean closeErrorFunction() { return false; } }
    static class TimeUtil { static boolean stale; static void sleep(long delay) { if(stale) throw new TaskCancelledException(); } static boolean isNowAfterOrCompareTimeStr(String time) { return false; } static boolean isLessThanSecondOfDays(long a,long b) { return false; } }
    static class Log { static int errors;static void i(String text) { } static void i(String tag,String text) { }
        static void record(String text) { } static void forest(String text) { } static void other(String text) { } static void farm(String text) { }
        static void printStackTrace(String tag,Throwable error) { errors++; } static String getFormatDate() { return "2026-10-09"; }
        static void err(String tag,String text,Throwable error) { if (error instanceof AssertionError) throw (AssertionError)error; } }
    static class MessageUtil { static boolean checkResultCode(String tag,JSONObject value) { return "SUCCESS".equals(value.optString("resultCode")); }
        static boolean checkSuccess(String tag,JSONObject value) { return value.optBoolean("success"); }
        static boolean isRetryable(JSONObject value) { return "3000".equals(value.optString("resultCode")); }
        static boolean isServerBusy(JSONObject value) { return "102".equals(value.optString("resultCode")); }
        static void beginDeferBlackList() { } static void endDeferBlackList(boolean apply) { }
        static void MarkTaskBlackList(String a,String b,String c,String d) { } }
    static class Status { static Set<String> flags=new HashSet<>(); static boolean hasFlagToday(String key) { return flags.contains(key); }
        static void flagToday(String key) { flags.add(key); } static void ancientTreeToday(String city) { flags.add("ancient:"+city); } }
    static class RuntimeInfo { static RuntimeInfo value=new RuntimeInfo(); Map<String,String> map=new HashMap<>();
        static RuntimeInfo getInstance() { return value; } String getString(String key) { return map.getOrDefault(key,""); } void put(String key,String item) { map.put(key,item); } }
    static class TaskAlternative { static String DEFAULT_VERSION="v"; static boolean isTransactionTask(String key) { return false; }
        static JSONObject trigger(Map<String,String> p,String id,String title,String key,String scene,String version,String prefix,Consumer<String> log) { return null; } }
    @@POLICY@@
    static class GreenFinanceRpcCall { static String response; static String greenFinanceIndex() { return response; }
        static void doTask(String id,String tag,String text) { } }
    static class Green {
        String TAG="green";
        List<List<String>> collected=new ArrayList<>();
        boolean cancel; int signs;
        void batchSelfCollect(JSONArray values) {
            if (cancel) throw new TaskCancelledException();
            List<String> ids=new ArrayList<>(); for (int i=0;i<values.length();i++) ids.add(values.optString(i));
            collected.add(ids);
        }
        void signIn(String id) { signs++; } void behaviorTick() { } void batchStealFriend() { } void donation() { }
        @@GREEN@@
    }
    static class ExtensionsHandle { static Object handleAlphaRequest(String a,String b,Object c) { return null; } }
    static class Sports {
        Field<Boolean> walkMinimumCompleteCount=new Field<>(true);
        String selected="new", current=""; boolean accept=true;
        List<String> joined=new ArrayList<>(), queried=new ArrayList<>();
        String queryGoingPathId() { return current; }
        boolean isNeedJoinNewPath(String id) { return id.isEmpty(); }
        String getWalkPathMinCompleteCount() { return selected; }
        String queryJoinPathId() { return selected; }
        boolean checkJoinPathId(String id) { assert id!=null && !id.isEmpty() : "invalid route queried"; return true; }
        boolean joinPath(String id) { joined.add(id); return accept; }
        JSONObject queryPath(String id) { queried.add(id); return new JSONObject(); }
        boolean walkGo(JSONObject path,int steps) { return false; }
        @@WALK@@
    }
    static class AntSportsRpcCall { static int claims; static boolean transport; static String claimReply="{\"success\":false,\"resultCode\":\"102\"}";
        static String queryTaskInfo() { return "{\"success\":true,\"data\":{\"taskInfos\":[{\"title\":\"browse\",\"viewSec\":15,\"encryptValue\":\"fixture\",\"energyNum\":1}]}}"; }
        static String neverlandenergyReceive(String raw) { claims++; if(transport) throw new IllegalStateException(); return claimReply; }
        static String walkGo(String date,String path,int count) { return "{\"success\":true,\"data\":{}}"; } }
    static class SportsTasks { static String TAG="sports"; Field<Integer> walkPaceMs=new Field<>(50); void parseRewardsByJSONObjectData(JSONObject data) { }
        @@SPORTS_BROWSE@@ @@SPORTS_PROBE@@ @@SPORTS_RECEIVE@@ @@SPORTS_PACE@@ @@SPORTS_GO@@ }
    static class AntStallRpcCall { static String detail; static String projectDetail(String id) { return detail; }
        static int plugins; static boolean pluginFailure,finishCancel; static String failure="{\"success\":false,\"resultCode\":\"102\"}";
        static String xlightPlugin() { plugins++; return pluginFailure ? failure : "{\"playingResult\":{\"playingBizId\":\"biz\",\"eventRewardDetail\":{\"eventRewardInfoList\":[{}]}}}"; }
        static String finish(String id,JSONObject value) { if(finishCancel) throw new TaskCancelledException(); return failure; }
        static String taskList() { return "{\"resultCode\":\"SUCCESS\",\"taskModels\":[{\"taskType\":\"ANTSTALL_XLIGHT_VARIABLE_AWARD\",\"taskStatus\":\"TODO\"}]}"; }
        static String queryCallAppSchema(String scene) { return "{}"; } }
    static class JsonUtil { static Object getValueByPathObject(JSONObject value,String path) { return value.optJSONObject("eventRewardDetail").optJSONArray("eventRewardInfoList"); }
        static String getValueByPath(JSONObject value,String path) { return ""; } }
    static class ReadingDada { static boolean answerQuestion(JSONObject value) { return false; } }
    static class UserIdMap { static String uid="uid"; static String getCurrentUid() { return uid; } }
    static class ApplicationHook {static boolean capture;static JSONObject request;static String rpc;static String requestString(String method,String args) {if(capture){request=new JSONArray(args).optJSONObject(0);rpc=method;return "{}";} throw new TaskCancelledException(); } }
    static class SportsRpc {static String timeZone="Asia/Shanghai";@@WALK_FEATURES@@ @@WALK_RPC@@}
    static class Fish {
        static String API_RECEIVE_AWARD="award"; String runningUid="uid"; int blacklisted;
        String getTaskSceneCode(String task) { return "scene"; } String buildReceiveAwardRequest(String scene,String task) { return "[]"; }
        String getTaskDisplayName(String id) { return id; } boolean isSuccess(JSONObject json) { return json.optBoolean("success"); }
        void addToBlacklist(String id) { blacklisted++; }
        @@FISH_REQUEST@@
        @@FISH_SLEEP@@
        @@FISH_AWARD@@
    }
    static class Stall {
        static String TAG="stall"; int donations; static Set<String> taskTypeList=Set.of(); Field<Boolean> inviteRegister=new Field<>(false);
        boolean inviteRegister() { return false; } boolean finishTask(String task,String title) { return false; } void querySelfHome() { }
        boolean projectDonate(String id) { donations++; return true; }
        @@STALL@@
        @@STALL_DO@@ @@STALL_XLIGHT@@ @@STALL_PROBE@@
    }
    static class AncientTreeRpcCall { static int writes; static String project,detailReply,homeOverride;
        static String homePage(String city) { if(homeOverride!=null)return homeOverride;return "{\"resultCode\":\"SUCCESS\",\"data\":{\"districtBriefInfoList\":[{\"userCanProtectTreeNum\":1,\"districtInfo\":{\"districtCode\":\"district\"}}]}}"; }
        static String districtDetail(String district) { return detailReply; } static String projectDetail(String id,String city) { return project; }
        static String protect(String activity,String project,String city) { writes++; return "{\"resultCode\":\"SUCCESS\"}"; } }
    static class Ancient { static String TAG="ancient"; @@ANCIENT_HOME@@ @@ANCIENT_DISTRICT@@ }
    static class ProtectTreeRpcCall { static String response; static boolean cancel; static int queries;
        static String queryTreeForExchange(int id) { queries++; if (cancel) throw new TaskCancelledException(); return response; } }
    static class Ecology {
        static String TAG="ecology"; static int exchanges,gold;
        static Field<Map<String,Integer>> protectTreeList=new Field<>(Map.of("1",2));
        static class ExchangeableTree { boolean canExchange; int projectId,certCount; String projectName;
            ExchangeableTree(int id) { projectId=id; } }
        static void applyGoldAnimalCert(int id) { gold++; }
        static boolean exchangeTree(int id,String name) { assert ++exchanges<=2 : "stalled certificate count repeated exchange"; return true; }
        @@TREE_QUERY@@
        @@TREE_LOOP@@
    }
    static JSONObject leaf(String code,String id) { return new JSONObject().put("code",code).put("bsnId",id); }
    static void green() {
        JSONArray leaves=new JSONArray().put(leaf("A","1")).put(leaf("A","2")).put(leaf("B","3"))
            .put(leaf("B","4")).put(leaf("A","5")).put(leaf("A",""));
        GreenFinanceRpcCall.response=new JSONObject().put("success",true).put("result",new JSONObject()
            .put("greenFinanceSigned",true).put("mcaGreenLeafResult",new JSONObject().put("greenLeafList",leaves))).toString();
        Green work=new Green(); work.run();
        assert work.collected.equals(List.of(List.of("1","2"),List.of("3","4"),List.of("5"))) : work.collected;
    }
    static void greenCancel() {
        GreenFinanceRpcCall.response=new JSONObject().put("success",true).put("result",new JSONObject()
            .put("greenFinanceSigned",true).put("mcaGreenLeafResult",new JSONObject().put("greenLeafList",
                new JSONArray().put(leaf("A","1"))))).toString();
        Green stopped=new Green(); stopped.cancel=true;
        try { stopped.run(); throw new AssertionError("collection cancellation swallowed"); } catch (TaskCancelledException expected) { }
        assert stopped.signs==0;
    }
    static void sports() {
        ApplicationHook.capture=true;
        SportsRpc.queryPath("2026-10-09","p\"\\");assert ApplicationHook.request.optString("pathId").equals("p\"\\")&&ApplicationHook.request.optString("timeZone").equals("Asia/Shanghai")&&!ApplicationHook.request.has("timezoneId");
        SportsRpc.joinPath("p\"\\");assert ApplicationHook.request.optString("pathId").equals("p\"\\");
        SportsRpc.walkGo("2026-10-09","p\"\\",1005);assert ApplicationHook.request.opt("useStepCount") instanceof Number&&ApplicationHook.request.optInt("useStepCount")==1005&&ApplicationHook.request.optString("source").equals("ch_othertinyapp")&&ApplicationHook.request.optJSONArray("features").length()==16&&ApplicationHook.request.optString("chInfo").equals("ch_othertinyapp")&&ApplicationHook.request.optString("clientOS").equals("android");ApplicationHook.capture=false;
        for (boolean minimum:new boolean[]{true,false}) {
            Sports work=new Sports(); work.walkMinimumCompleteCount.value=minimum; work.walk(1000);
            assert work.joined.equals(List.of("new")) && work.queried.equals(List.of("new")) : "route must be joined before walking";
            work=new Sports(); work.walkMinimumCompleteCount.value=minimum; work.accept=false; work.walk(1000);
            assert work.joined.equals(List.of("new")) && work.queried.isEmpty() : "join rejection must stop";
            for (String missing:new String[]{null,""}) {
                work=new Sports(); work.walkMinimumCompleteCount.value=minimum; work.selected=missing; work.walk(1000);
                assert work.joined.isEmpty() && work.queried.isEmpty() : "missing route must stop";
            }
        }
        Sports active=new Sports(); active.current="existing"; active.walk(1000);
        assert active.joined.isEmpty() && active.queried.equals(List.of("existing"));
    }
    static JSONObject detail(Object balance,Object expense) {
        return new JSONObject().put("resultCode","SUCCESS").put("astUserInfoVO",new JSONObject()
            .put("currentCoin",new JSONObject().put("cent",balance))).put("astProjectVO",new JSONObject()
            .put("jobModel",new JSONObject().put("donateAmount",new JSONObject().put("cent",expense))));
    }
    static void stall() {
        for (JSONObject response:new JSONObject[]{new JSONObject().put("resultCode","SUCCESS"),
                detail(100,50).put("astProjectVO",new JSONObject()), detail(100,50).put("astUserInfoVO",new JSONObject()),
                detail(100,-1), detail(100,0)}) {
            AntStallRpcCall.detail=response.toString(); Stall work=new Stall();
            assert !work.projectDetail("p") && work.donations==0 : "incomplete donation quote must stop";
        }
        AntStallRpcCall.detail=detail(100,50).toString(); Stall work=new Stall();
        assert work.projectDetail("p") && work.donations==1;
        AntStallRpcCall.detail=detail(10,50).toString(); work=new Stall();
        assert !work.projectDetail("p") && work.donations==0;
        for (Object invalid:new Object[]{-1,100.5,"100.5",4294967396L,"4294967396",true,JSONObject.NULL}) {
            for (boolean balance:new boolean[]{true,false}) {
                AntStallRpcCall.detail=(balance ? detail(invalid,50) : detail(100,invalid)).toString(); work=new Stall();
                assert !work.projectDetail("p") && work.donations==0 : "invalid donation integer "+invalid;
            }
        }
        AntStallRpcCall.detail=detail("100","50").toString(); work=new Stall();
        assert work.projectDetail("p") && work.donations==1 : "valid integer strings must remain supported";
    }
    static JSONObject tree() { return new JSONObject().put("resultCode","SUCCESS").put("applyAction","AVAILABLE")
        .put("currentEnergy",1000).put("exchangeableTree",new JSONObject().put("certCount",0)
            .put("energy",100).put("projectName","tree").put("type","TREE")); }
    static void ecology() {
        for (String field:new String[]{"currentEnergy","energy","certCount"}) {
            JSONObject raw=tree(); if (field.equals("currentEnergy")) raw.remove(field); else raw.optJSONObject("exchangeableTree").remove(field);
            ProtectTreeRpcCall.response=raw.toString(); assert !Ecology.queryTreeForExchange(1).canExchange : "missing "+field;
            for (Object invalid:new Object[]{-1,100.5,"100.5",4294967396L,"4294967396",true,JSONObject.NULL}) {
                raw=tree(); JSONObject owner=field.equals("currentEnergy") ? raw : raw.optJSONObject("exchangeableTree");
                owner.put(field,invalid); ProtectTreeRpcCall.response=raw.toString();
                assert !Ecology.queryTreeForExchange(1).canExchange : "invalid "+field+"="+invalid;
            }
        }
        JSONObject animal=tree(); animal.optJSONObject("exchangeableTree").put("type","ANIMAL");
        animal.put("subTreeVOs",new JSONArray().put(new JSONObject())); ProtectTreeRpcCall.response=animal.toString();
        assert !Ecology.queryTreeForExchange(1).canExchange : "missing animal certificate count";
        assert Ecology.gold==0 : "unknown animal progress must not grant gold certificate";
        for (Object invalid:new Object[]{-1,0.5,"0.5",4294967296L,"4294967296",true,JSONObject.NULL}) {
            animal.put("subTreeVOs",new JSONArray().put(new JSONObject().put("certCountForAlias",invalid)));
            ProtectTreeRpcCall.response=animal.toString();
            assert !Ecology.queryTreeForExchange(1).canExchange && Ecology.gold==0 : "invalid alias count "+invalid;
        }
        animal.put("subTreeVOs",new JSONArray().put(new JSONObject().put("certCountForAlias","0")));
        ProtectTreeRpcCall.response=animal.toString(); assert Ecology.queryTreeForExchange(1).canExchange;
        JSONObject valid=tree().put("currentEnergy","1000"); valid.optJSONObject("exchangeableTree").put("energy","100").put("certCount","0");
        ProtectTreeRpcCall.response=valid.toString(); assert Ecology.queryTreeForExchange(1).canExchange;
        ProtectTreeRpcCall.response=tree().toString(); assert Ecology.queryTreeForExchange(1).canExchange;
    }
    static void ecologyStalled() {
        ProtectTreeRpcCall.response=tree().toString();
        Ecology.exchanges=0; Ecology.protectTree(); assert Ecology.exchanges==1 : "stalled count must stop after one write";
    }
    static void ecologyCancel() {
        ProtectTreeRpcCall.cancel=true;
        try { Ecology.queryTreeForExchange(1); throw new AssertionError("ecology cancellation swallowed"); } catch (TaskCancelledException expected) { }
        ProtectTreeRpcCall.cancel=false;
    }
    static void fishCancel() {
        Fish work=new Fish();
        try { work.receiveTaskAward("task","task"); throw new AssertionError("fish cancellation swallowed"); } catch(TaskCancelledException expected) { }
        assert work.blacklisted==0 : "cancelled task must not be blacklisted";
        work.runningUid="old";
        try { work.requestString("award","[]"); throw new AssertionError("account switch did not stop fish"); } catch(TaskCancelledException expected) { }
        TimeUtil.stale=true;
        try { work.sleep(0); throw new AssertionError("fish sleep did not check generation"); } catch(TaskCancelledException expected) { }
        TimeUtil.stale=false;
    }
    static void sportsRetry() {
        for(boolean transport:new boolean[]{false,true}) {
            Status.flags.clear(); AntSportsRpcCall.claims=0; AntSportsRpcCall.transport=transport;
            SportsTasks.processBrowseTasks(); SportsTasks.processBrowseTasks();
            assert AntSportsRpcCall.claims==2 && Status.flags.isEmpty() : "temporary browse failure blocked today";
        }
    }
    static void sportsPaceCancel() {
        TimeUtil.stale=true;
        try { new SportsTasks().walkGo("route","path",1000); throw new AssertionError("pacing cancellation swallowed"); } catch(TaskCancelledException expected) { }
        TimeUtil.stale=false;
    }
    static void stallRetry() {
        for(boolean pluginFailure:new boolean[]{true,false}) {
            for(String failure:new String[]{"{\"success\":false,\"resultCode\":\"102\"}","{}"}) {
                Status.flags.clear(); AntStallRpcCall.plugins=0; AntStallRpcCall.pluginFailure=pluginFailure; AntStallRpcCall.failure=failure;
                Stall work=new Stall(); JSONObject task=new JSONObject().put("taskType","ANTSTALL_XLIGHT_VARIABLE_AWARD");
                work.doStallTask(task,"xlight"); work.doStallTask(task,"xlight");
                assert AntStallRpcCall.plugins==2 && Status.flags.isEmpty() : "temporary/unknown xlight failure blocked today";
            }
            Status.flags.clear(); AntStallRpcCall.plugins=0; AntStallRpcCall.failure="{\"success\":false,\"resultCode\":\"UNSUPPORTED_ACTION\"}";
            Stall work=new Stall(); JSONObject task=new JSONObject().put("taskType","ANTSTALL_XLIGHT_VARIABLE_AWARD");
            work.doStallTask(task,"xlight"); work.doStallTask(task,"xlight");
            assert AntStallRpcCall.plugins==1 && !Status.flags.isEmpty() : "known inability must retain daily suppression";
        }
        Status.flags.clear(); AntStallRpcCall.pluginFailure=false; AntStallRpcCall.finishCancel=true;
        try { new Stall().doStallTask(new JSONObject().put("taskType","ANTSTALL_XLIGHT_VARIABLE_AWARD"),"xlight"); throw new AssertionError("xlight cancellation swallowed"); } catch(TaskCancelledException expected) { }
        assert Status.flags.isEmpty(); AntStallRpcCall.finishCancel=false;
    }
    static JSONObject ancientProject() { return new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject()
        .put("canProtect",true).put("currentEnergy",1000).put("ancientTree",new JSONObject().put("activityId","activity").put("projectId","tree")
            .put("ancientTreeInfo",new JSONObject().put("cityCode","city").put("protectExpense",100)))); }
    static void ancient() {
        String listReply="{\"resultCode\":\"SUCCESS\",\"data\":{\"districtInfo\":{\"cityCode\":\"city\",\"cityName\":\"city\",\"districtName\":\"district\"},\"ancientTreeList\":[{\"hasProtected\":false,\"projectId\":\"tree\",\"ancientTreeControlInfo\":{\"quota\":1,\"useQuota\":0}}]}}";
        AncientTreeRpcCall.detailReply=listReply;
        for(String field:new String[]{"currentEnergy","protectExpense"}) {
            for(Object invalid:new Object[]{JSONObject.NULL,-1,100.5,"100.5",4294967396L,true}) {
                JSONObject project=ancientProject(), data=project.optJSONObject("data");
                JSONObject owner=field.equals("currentEnergy") ? data : data.optJSONObject("ancientTree").optJSONObject("ancientTreeInfo");
                if(invalid==JSONObject.NULL) owner.remove(field); else owner.put(field,invalid);
                AncientTreeRpcCall.project=project.toString(); AncientTreeRpcCall.writes=0;
                Ancient.districtDetail("district"); assert AncientTreeRpcCall.writes==0 : "invalid ancient protection quote "+field+"="+invalid;
            }
        }
        JSONObject valid=ancientProject(); valid.optJSONObject("data").put("currentEnergy","1000");
        valid.optJSONObject("data").optJSONObject("ancientTree").optJSONObject("ancientTreeInfo").put("protectExpense","100");
        AncientTreeRpcCall.project=valid.toString(); AncientTreeRpcCall.writes=0;
        Ancient.districtDetail("district"); assert AncientTreeRpcCall.writes==1;
        for(String failure:new String[]{"{\"resultCode\":\"102\"}","{\"resultCode\":\"SUCCESS\",\"data\":{\"ancientTreeList\":null}}"}) {
            Status.flags.clear(); AncientTreeRpcCall.detailReply=failure; Ancient.ancientTreeProtect("city");
            assert !Status.flags.contains("ancient:city") : "unknown district marked city done today";
        }
        for(String missing:new String[]{"hasProtected","quota","useQuota","currentEnergy","protectExpense","canProtect","userCanProtectTreeNum"}) {
            JSONObject detail=new JSONObject(listReply), project=ancientProject();
            JSONObject tree=detail.optJSONObject("data").optJSONArray("ancientTreeList").optJSONObject(0);
            if(missing.equals("hasProtected")) tree.remove(missing);
            else if(missing.equals("quota")||missing.equals("useQuota")) tree.optJSONObject("ancientTreeControlInfo").remove(missing);
            else if(missing.equals("protectExpense")) project.optJSONObject("data").optJSONObject("ancientTree").optJSONObject("ancientTreeInfo").remove(missing);
            else if(missing.equals("userCanProtectTreeNum")) {
                JSONObject home=new JSONObject(AncientTreeRpcCall.homePage("city"));
                home.optJSONObject("data").optJSONArray("districtBriefInfoList").optJSONObject(0).remove(missing);
                AncientTreeRpcCall.homeOverride=home.toString();
            } else project.optJSONObject("data").remove(missing);
            AncientTreeRpcCall.detailReply=detail.toString();AncientTreeRpcCall.project=project.toString();
            AncientTreeRpcCall.writes=0;Status.flags.clear();int errors=Log.errors;Ancient.ancientTreeProtect("city");
            assert AncientTreeRpcCall.writes==("hasProtected".equals(missing)?1:0)&&Status.flags.contains("ancient:city")&&Log.errors==errors:"missing protection flag defaults false; other missing fields skip: "+missing;
            AncientTreeRpcCall.homeOverride=null;
        }
        for(String field:new String[]{"userCanProtectTreeNum","quota","useQuota","hasProtected","canProtect"}) {
            for(Object invalid:new Object[]{JSONObject.NULL,-1,0.5,4294967296L,"bad"}) {
                JSONObject detail=new JSONObject(listReply), project=ancientProject(), home=new JSONObject(AncientTreeRpcCall.homePage("city"));
                JSONObject tree=detail.optJSONObject("data").optJSONArray("ancientTreeList").optJSONObject(0);
                if(field.equals("userCanProtectTreeNum"))home.optJSONObject("data").optJSONArray("districtBriefInfoList").optJSONObject(0).put(field,invalid);
                else if(field.equals("hasProtected"))tree.put(field,invalid);
                else if(field.equals("canProtect"))project.optJSONObject("data").put(field,invalid);
                else tree.optJSONObject("ancientTreeControlInfo").put(field,invalid);
                AncientTreeRpcCall.homeOverride=home.toString();AncientTreeRpcCall.detailReply=detail.toString();AncientTreeRpcCall.project=project.toString();
                AncientTreeRpcCall.writes=0;Status.flags.clear();Ancient.ancientTreeProtect("city");
                assert AncientTreeRpcCall.writes==0&&!Status.flags.contains("ancient:city"):"present invalid field must stop: "+field;
                AncientTreeRpcCall.homeOverride=null;
            }
        }
        JSONObject mixed=new JSONObject(listReply);JSONArray rows=mixed.optJSONObject("data").optJSONArray("ancientTreeList");
        JSONObject healthy=new JSONObject(rows.optJSONObject(0).toString());rows.optJSONObject(0).optJSONObject("ancientTreeControlInfo").remove("quota");rows.put(healthy);
        AncientTreeRpcCall.detailReply=mixed.toString();AncientTreeRpcCall.project=ancientProject().toString();
        AncientTreeRpcCall.writes=0;Status.flags.clear();Ancient.ancientTreeProtect("city");
        assert AncientTreeRpcCall.writes==1&&Status.flags.contains("ancient:city"):"missing field must not block healthy next tree";
        AncientTreeRpcCall.detailReply=listReply; TimeUtil.stale=true;
        try { Ancient.ancientTreeProtect("city"); throw new AssertionError("ancient cancellation swallowed"); } catch(TaskCancelledException expected) { }
        TimeUtil.stale=false;
    }
    public static void main(String[] args) {
        switch (args[0]) { case "green": green(); break; case "green_cancel": greenCancel(); break;
            case "sports": sports(); break; case "stall": stall(); break; case "ecology": ecology(); break;
            case "sports_retry": sportsRetry(); break; case "sports_pace_cancel": sportsPaceCancel(); break; case "stall_retry": stallRetry(); break; case "ancient": ancient(); break;
            case "ecology_stalled": ecologyStalled(); break; case "ecology_cancel": ecologyCancel(); break; case "fish_cancel": fishCancel(); break; }
        System.out.println("PASS remaining business "+args[0]);
    }
}
'''
for token, path, signature in (
    ("@@GREEN@@", "greenFinance/GreenFinance.java", "    public void  run()"),
    ("@@WALK@@", "antSports/AntSports.java", "    private void walk(int"),
    ("@@STALL@@", "antStall/AntStall.java", "    private Boolean projectDetail("),
    ("@@FISH_REQUEST@@", "fish/FishTask.java", "    private String requestString("),
    ("@@FISH_SLEEP@@", "fish/FishTask.java", "    private void sleep("),
    ("@@FISH_AWARD@@", "fish/FishTask.java", "    private boolean receiveTaskAward("),
    ("@@TREE_QUERY@@", "protectEcology/ProtectEcology.java", "    private static ExchangeableTree queryTreeForExchange("),
    ("@@TREE_LOOP@@", "protectEcology/ProtectEcology.java", "    private static void protectTree()"),
    ("@@SPORTS_BROWSE@@", "antSports/AntSports.java", "    public static void processBrowseTasks("),
    ("@@SPORTS_PROBE@@", "antSports/AntSports.java", "    private static TaskAttemptPolicy.ProbeResult probeBrowseStatus("),
    ("@@SPORTS_RECEIVE@@", "antSports/AntSports.java", "    public static boolean receiveBrowseReward("),
    ("@@SPORTS_PACE@@", "antSports/AntSports.java", "    private void paceWalk("),
    ("@@SPORTS_GO@@", "antSports/AntSports.java", "    private Boolean walkGo(String"),
    ("@@STALL_DO@@", "antStall/AntStall.java", "    private Boolean doStallTask("),
    ("@@STALL_XLIGHT@@", "antStall/AntStall.java", "    private Outcome attemptXlightTask("),
    ("@@STALL_PROBE@@", "antStall/AntStall.java", "    private static TaskAttemptPolicy.ProbeResult probeStallStatus("),
    ("@@ANCIENT_HOME@@", "ancientTree/AncientTree.java", "    private static void ancientTreeProtect("),
    ("@@ANCIENT_DISTRICT@@", "ancientTree/AncientTree.java", "    private static boolean districtDetail("),
):
    code = code.replace(token, method("model/task/" + path, signature).replace("Outcome", "TaskAttemptPolicy.Outcome"))
code=code.replace("@@POLICY@@",method("data/task/TaskAttemptPolicy.java","public class TaskAttemptPolicy").replace("public class TaskAttemptPolicy","static class TaskAttemptPolicy",1))
import re
rpc_source=(SOURCE/'model/task/antSports/AntSportsRpcCall.java').read_text(encoding='utf-8')
code=code.replace('@@WALK_FEATURES@@',re.search(r'private static final String WALK_FEATURES = .*?;',rpc_source).group())
code=code.replace('@@WALK_RPC@@','\n'.join(method('model/task/antSports/AntSportsRpcCall.java',s) for s in ('public static String queryPath(String date, String pathId)','public static String joinPath(String pathId)','public static String walkGo(String date, String pathId, int useStepCount)')))
from daily_task_fixture import with_daily_task
code=with_daily_task(code)
with tempfile.TemporaryDirectory(prefix="sesame-remaining-") as temporary:
    source = Path(temporary) / "RemainingCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temporary, str(source)], check=True)
    failures = []
    for scenario in sys.argv[1:] or ("green", "green_cancel", "sports", "stall", "ecology", "ecology_stalled", "ecology_cancel", "fish_cancel", "sports_retry", "sports_pace_cancel", "stall_retry", "ancient"):
        result = subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + str(jar), "RemainingCheck", scenario])
        if result.returncode:
            failures.append(scenario)
    assert not failures, failures
