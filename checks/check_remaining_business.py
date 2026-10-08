"""Replay active green-finance, sports, stall, ecology and fish production methods; no live RPC."""
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
import org.json.*;
import java.math.BigDecimal;
import java.util.*;
public class RemainingCheck {
    static class Field<T> { T value; Field(T value) { this.value=value; } T getValue() { return value; } }
    static class TaskCancelledException extends RuntimeException { }
    static class MyUtils { static JSONObject newJSONObject(String raw) { return new JSONObject(raw); } }
    static class TimeUtil { static boolean stale; static void sleep(long delay) { if(stale) throw new TaskCancelledException(); } static boolean isNowAfterOrCompareTimeStr(String time) { return false; } }
    static class Log { static void i(String text) { } static void i(String tag,String text) { }
        static void record(String text) { } static void forest(String text) { } static void other(String text) { }
        static void err(String tag,String text,Throwable error) { if (error instanceof AssertionError) throw (AssertionError)error; } }
    static class MessageUtil { static boolean checkResultCode(String tag,JSONObject value) { return "SUCCESS".equals(value.optString("resultCode")); } }
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
    static class AntStallRpcCall { static String detail; static String projectDetail(String id) { return detail; } }
    static class UserIdMap { static String uid="uid"; static String getCurrentUid() { return uid; } }
    static class ApplicationHook { static String requestString(String method,String args) { throw new TaskCancelledException(); } }
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
        String TAG="stall"; int donations;
        boolean projectDonate(String id) { donations++; return true; }
        @@STALL@@
    }
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
    public static void main(String[] args) {
        switch (args[0]) { case "green": green(); break; case "green_cancel": greenCancel(); break;
            case "sports": sports(); break; case "stall": stall(); break; case "ecology": ecology(); break;
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
):
    code = code.replace(token, method("model/task/" + path, signature))
with tempfile.TemporaryDirectory(prefix="sesame-remaining-") as temporary:
    source = Path(temporary) / "RemainingCheck.java"
    source.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temporary, str(source)], check=True)
    failures = []
    for scenario in sys.argv[1:] or ("green", "green_cancel", "sports", "stall", "ecology", "ecology_stalled", "ecology_cancel", "fish_cancel"):
        result = subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + str(jar), "RemainingCheck", scenario])
        if result.returncode:
            failures.append(scenario)
    assert not failures, failures
