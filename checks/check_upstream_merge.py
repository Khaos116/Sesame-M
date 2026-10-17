"""Compile merged production flows with isolated RPCs; check malformed data, quotas, task progress and locale."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
json_jar = next(p for p in (cache / "org.json/json").glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
member = "model/task/antMember/AntMember.java"
code = "import org.json.*; import java.util.*; import java.util.regex.Pattern;\npublic class MergeCheck {\n"
code += 'static final String TAG = "test";\n'
for signature in (
    "private static boolean goldTicketOk(", "private JSONObject getGoldTicketAssetInfo(",
    "private boolean doGoldTicketSignIn(", "private JSONArray extractGoldTicketHomeTodoTasks(",
    "private void doGoldTicketConsume(",
    "private int countGoldTicketPendingAutoTasks(",
):
    code += method(member, signature) + "\n"
code += method("model/task/antForest/AntForestV2.java", "private static int teamState(") + "\n"
code += method("model/base/TaskAlternative.java", "public static boolean isTransactionTask(") + "\n"
code += r'''
@@TRANSACTION_WORDS@@
static class MyUtils {
    static JSONObject newJSONObject() { return new JSONObject(); }
    static JSONObject newJSONObject(String raw) { try { return raw == null ? new JSONObject() : new JSONObject(raw); } catch (JSONException e) { return new JSONObject(); } }
}
static class Field { Set<String> value=new HashSet<>(); Set<String> getValue(){return value;} }
Field GoldTicketTaskList=new Field();
static class Log {
    static int errors;
    static void printStackTrace(String tag, Throwable t) { errors++; }
    static void i(String s) {} static void error(String s) {} static void other(String s) {}
}
static class MessageUtil { static boolean checkResultCode(String tag, JSONObject jo) { return "SUCCESS".equals(jo.optString("resultCode")); } }
static class Status { static int flags; static void flagToday(String s) { flags++; } }
JSONObject refreshed;
private JSONObject queryGoldTicketHome() { return refreshed; }
private int doGoldTicketIndexCollect(String source) { return 0; }
private boolean refreshGoldTicketWelfareCenter(String source) { return true; }
static class ApplicationHook {
    static String requestString(String name, String args) { AntMemberRpcCall.calls++; AntMemberRpcCall.args = args; return "{}"; }
}
static class AntMemberRpcCall {
    static String query, args;
    static int calls;
    static String welfareCenterTrigger(String type) { return "broken"; }
    static String queryConsumeHome() { return query; }
    @@SUBMIT@@
}
static JSONObject home(Object canSign) {
    return new JSONObject().put("assetInfo", new JSONObject().put("canSign", canSign));
}
static void consume(MergeCheck check, JSONObject result) {
    AntMemberRpcCall.query = new JSONObject().put("success", true).put("result", result).toString();
    check.doGoldTicketConsume();
}
public static void main(String[] args) throws Exception {
    MergeCheck check = new MergeCheck();
    assert !goldTicketOk(TAG, MyUtils.newJSONObject("broken"));
    assert !check.doGoldTicketSignIn(new JSONObject());
    assert !check.doGoldTicketSignIn(home("false"));
    assert check.doGoldTicketSignIn(home(false));
    check.refreshed = new JSONObject();
    assert !check.doGoldTicketSignIn(home(true));
    check.refreshed = home(false);
    assert check.doGoldTicketSignIn(home(true));
    assert check.extractGoldTicketHomeTodoTasks(new JSONObject()) == null;
    JSONObject tasks = new JSONObject().put("todo", "bad");
    JSONObject taskHome = new JSONObject().put("task", new JSONObject().put("tasks", tasks));
    assert check.extractGoldTicketHomeTodoTasks(taskHome) == null;
    tasks.put("todo", new JSONArray());
    assert check.extractGoldTicketHomeTodoTasks(taskHome).length() == 0;
    assert check.countGoldTicketPendingAutoTasks(new JSONArray().put(1)) == -1;
    assert check.countGoldTicketPendingAutoTasks(new JSONArray().put(new JSONObject())) == -1;
    assert check.countGoldTicketPendingAutoTasks(new JSONArray().put(new JSONObject().put("taskId", "AP10247402"))) == 1;
    assert check.countGoldTicketPendingAutoTasks(new JSONArray().put(new JSONObject().put("taskId", "NEW_SERVER_TASK"))) == 1;
    check.GoldTicketTaskList.value.add("NEW_SERVER_TASK");
    assert check.countGoldTicketPendingAutoTasks(new JSONArray().put(new JSONObject().put("taskId", "NEW_SERVER_TASK"))) == 0;
    JSONObject asset = new JSONObject().put("availableAmount", 2900).put("minExchangeAmount", 100);
    JSONObject result = new JSONObject().put("assetInfo", asset).put("productList", new JSONArray().put(1));
    consume(check, result);
    assert AntMemberRpcCall.calls == 0 && Status.flags == 0;
    result.put("productList", new JSONArray().put(new JSONObject().put("productId", "fund")));
    asset.put("exchangeAmountUnit", 0);
    consume(check, result);
    assert AntMemberRpcCall.calls == 0 && Status.flags == 0;
    asset.put("exchangeAmountUnit", 100).put("availableAmount", "bad");
    consume(check, result);
    assert AntMemberRpcCall.calls == 0 && Status.flags == 0;
    asset.put("availableAmount", 2900);
    Locale.setDefault(Locale.GERMANY);
    consume(check, result);
    JSONObject sent = new JSONArray(AntMemberRpcCall.args).optJSONObject(0);
    assert AntMemberRpcCall.calls == 1;
    assert "2.90".equals(sent.optString("exchangeMoney"));
    assert sent.optInt("exchangeAmount") == 2900;
    assert Status.flags == 0; // Unconfirmed submit must remain retryable.
    assert teamState(new JSONObject()) == -1;
    assert teamState(new JSONObject().put("nextAction", "Team")) == 0;
    assert teamState(new JSONObject().put("nextAction", "Cultivate")) == 1;
    assert teamState(new JSONObject().put("nextAction", "Team").put("teamHomeResult", new JSONObject().put("mainMember", new JSONObject()))) == 1;
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    assert isTransactionTask("XIADAN") && !isTransactionTask("BROWSE");
    assert !isTransactionTask("2060170000359285") && !isTransactionTask("x_70000_y");
    assert Log.errors == 0;
    System.out.println("PASS: missing/invalid sign-in and consume data, task structure, decimal locale, tri-state teams and transaction guard");
}
}
'''.replace("@@SUBMIT@@", method("model/task/antMember/AntMemberRpcCall.java", "public static String submitConsume("))

transaction_source=(SOURCE / "model/base/TaskAlternative.java").read_text(encoding="utf-8")
code=code.replace("@@TRANSACTION_WORDS@@", "static final String[] TRANSACTION_KEYWORDS = " + transaction_source.split("private static final String[] TRANSACTION_KEYWORDS = ",1)[1].split(";",1)[0] + ";")

flows = r'''
import org.json.*; import java.util.*;
public class FlowCheck {
    static class TaskCancelledException extends RuntimeException {}
    static final String TAG = "test";
    static class Field<T> { T value; Field(T v) { value = v; } T getValue() { return value; } }
    static class MyUtils {
        static JSONObject newJSONObject(String raw) {
            try { return raw == null ? new JSONObject() : new JSONObject(raw); }
            catch (JSONException e) { return new JSONObject(); }
        }
    }
    static class Log {
        static List<String> results = new ArrayList<>();
        static void other(String s) { results.add(s); }
        static void forest(String s) {} static void farm(String s) {}
        static void record(String s) { results.add(s); } static void i(String s) {}
        static void err(String tag, String msg, Throwable t) { if(t instanceof IllegalStateException && "lost push reply".equals(t.getMessage())) return; throw new AssertionError(t); }
        static void printStackTrace(String tag, Throwable t) { if("java.lang.IllegalArgumentException".equals(t.getMessage())){assert t.getCause()==null&&t.getStackTrace().length>0;return;}throw new AssertionError(t); }
    }
    static class TimeUtil { static void sleep(long ms) {} }
    static class StringUtil { static boolean isEmpty(String s) { return s == null || s.isEmpty(); } }
    static class ApplicationHook { static boolean offline; static boolean isOffline() { return offline; } }
    static class DailyTask {static boolean manual;static boolean isManual(){return manual;}}
    static class Toast { static void show(String s) {} }
    static class UserIdMap { static String getCurrentUid() { return "self"; } }
    static JSONObject ok() { return new JSONObject().put("resultCode", "SUCCESS"); }
    static class MessageUtil {
        static int blackHits;
        static boolean checkResultCode(String tag, JSONObject jo) { return "SUCCESS".equals(jo.optString("resultCode")); }
        static boolean checkSuccess(String tag, JSONObject jo) { return jo.optBoolean("success") || jo.optBoolean("isSuccess"); }
        static boolean checkMemo(String tag, JSONObject jo) { return "SUCCESS".equals(jo.optString("memo")); }
        static void checkResultCodeAndMarkTaskBlackList(String list, String title, JSONObject jo) {}
        static void MarkTaskBlackListConfirm(String model, String list, String kind, String title) { blackHits++; }
        @@RETRY@@
    }
    static class Status {
        static Set<String> flags = new HashSet<>(); static int used;
        static Map<String,Integer> counts = new HashMap<>();
        static int getIntFlagToday(String key) { return counts.getOrDefault(key,0); }
        static void setIntFlagToday(String key,int n) { counts.put(key,n); }
        static boolean hasFlagToday(String key) { return flags.contains(key); }
        static void flagToday(String key) { flags.add(key); }
        static void flagToday(String key, String uid) { flags.add(uid + ":" + key); }
        static void clearFlag(String key) { flags.remove(key); }
        static int getforestHuntHelpToday(String key) { return used; }
        static void forestHuntHelpToday(String key, int n, String uid) { used = n; }
    }
    static class AntMemberRpcCall {
        static String gameHome; static int prizes;
        static String gameCenterHomePage() { return gameHome; }
        static String batchReceiveTaskPrize() { prizes++; return new JSONObject().put("success",true).toString(); }
        static String first, active, fresh, last, join; static boolean cancelLast, losePushReply; static int reads; static List<String> calls = new ArrayList<>();
        static String queryHome() { return ok().put("entrance", new JSONObject().put("openApp", true)).toString(); }
        static String CreditAccumulateStrategyRpcManager() { return reads++ == 0 ? first : fresh; }
        static String queryAvailableSesameTask() { calls.add("refresh"); return active; }
        static String queryCreditFeedback() { return ok().put("creditFeedbackVOS", new JSONArray()).toString(); }
        static String collectCreditFeedback(String id) { calls.add("collect:"+id); return ok().toString(); }
        static String collectAllCreditFeedback() { throw new AssertionError("unexpected reward"); }
        static String joinSesameTask(String id) { calls.add("join"); return join; }
        static String feedBackSesameTaskNew(String id) { calls.add("feedback"); return ok().toString(); }
        static String finishSesameTask(String id) { calls.add("push:" + id); if(losePushReply) throw new IllegalStateException("lost push reply"); return ok().toString(); }
        static String queryLastOperateTask() { calls.add("last"); if(cancelLast) throw new TaskCancelledException(); return last; }
    }
    static JSONObject task(int done, int need) {
        return new JSONObject().put("templateId", "T").put("title", "task").put("completedNum", done).put("needCompleteNum", need);
    }
    static String tasks(JSONArray list) { return ok().put("data", new JSONObject().put("toCompleteVOS", list)).toString(); }
    static class AntFarm { static boolean isUnrelatedPigeonFeedback(JSONObject item) { return "OTHER".equals(item.optString("cateId")); } static boolean bindPigeonFeedback(JSONArray items) { return true; } }
    static class Member {
        static final int GAME_CENTER_CERT_LIMIT=30;
        Field<Set<String>> MemberCreditSesameTaskList = new Field<>(new HashSet<>());
        Field<Boolean> AutoMemberCreditSesameTaskList = new Field<>(true);
        @@MEMBER@@
    }
    static class VitalityBenefitIdMap {
        static int saves; static Map<String,String> items = new HashMap<>();
        static Map<String,String> getMap() { return items; }
        static void add(String id, String name) { items.put(id, name); }
        static void save(String uid) { saves++; }
    }
    static class AntForestRpcCall {
        static final int VITALITY_ITEM_PAGE_SIZE = 20;
        static String water; static int waters; static boolean forever, brokenItems, cancelled;
        static boolean partial, cancelList;
        static String teamHome;
        static List<String> pages = new ArrayList<>(); static List<Boolean> switches = new ArrayList<>();
        static String itemList(String label, int offset) {
            pages.add(label + ":" + offset);
            if (cancelList) throw new TaskCancelledException();
            if (partial && "SKIN".equals(label)) return "broken";
            JSONObject item = new JSONObject().put("spuId", "P").put("skuModelList", new JSONArray()
                .put(new JSONObject().put("skuId", label + offset).put("skuName", "name")).put(1).put(new JSONObject()));
            JSONObject data = new JSONObject().put("itemInfoVOList", new JSONArray().put(1).put(brokenItems ? new JSONObject() : item))
                .put("hasMore", forever || offset == 0);
            return new JSONObject().put("success", true).put("resData", data).toString();
        }
        static String queryHomePage() { if(teamHome!=null)return teamHome;return ok().put("nextAction", "Cultivate")
            .put("teamHomeResult", new JSONObject().put("teamBaseInfo", new JSONObject().put("teamId", "team")))
            .put("userBaseInfo", new JSONObject().put("currentEnergy", 100)).toString(); }
        static String queryMiscInfo(String type, String id) { return ok().put("combineHandlerVOMap",
            new JSONObject().put("teamCanWaterCount", new JSONObject().put("waterCount", 100))).toString(); }
        static String teamWater(String id, int amount) { waters++; if (cancelled) throw new TaskCancelledException(); return water; }
        static String loveteamWater(String id, int amount) { waters++; if (cancelled) throw new TaskCancelledException(); return water; }
        static int loveReads;static String loveReply;static boolean homeThrows;
        static String loveteamHome() {loveReads++;if(homeThrows)throw new IllegalArgumentException("secret");return loveReply==null?ok().put("userInfo",new JSONObject().put("teamId","team")).toString():loveReply;}

        static String updateUserConfiginTeam(boolean inTeam) { switches.add(inTeam); return ok().toString(); }
    }
    static class Forest {
        static final String[] VITALITY_LABEL_TYPES = {"", "SC_ASSETS", "SKIN", "JEWELRY", "OTHER"};
        static final int VITALITY_ITEM_MAX_PAGES = 10;
        static final int VITALITY_SKU_RETRY_MAX = 3;
        static final String FLAG_VITALITY_SKU_RETRY = "forest::vitalitySkuListRetry";
        static final String FLAG_LOVETEAM_WATER = "Forest::loveteamWater", FLAG_TEAM_MODE_SWITCHED = "Forest::teamWaterSwitchedToTeam";
        static final String FLAG_TEAM_WATER_DAILY_COUNT = "FLAG_TEAM_WATER_DAILY_COUNT";
        Field<Integer> partnerteamWaterNum = new Field<>(20); Map<String,JSONObject> skuInfo = new HashMap<>();
        @@FOREST@@
    }
    enum PlantScene { MAIN; String nickname() { return name(); } }
    static class AntOrchardRpcCall { static String response; static String orchardSpreadManure(String scene, boolean batch, String wua) { return response; } }
    static class Orchard {
        static final int BATCH_SPREAD_SIZE = 5;
        boolean spreadUseBatchThisTime; int fertilizerProgress = 100; String userId = "self";
        int spreadHappyPoint = -1, spreadWateringCost = -1;
        Map<String,Integer> orchardSpreadManureSceneList = Map.of("MAIN", 10);
        String getWua() { return ""; } static int targetSpreadTimes(Integer n) { return n; }
        @@ORCHARD@@
    }
    static class GameCenterMallItemMap { static void add(String id, String name) {} static void save(String uid) {} }
    static class Mall {
        Map<String,JSONObject> skuInfo=new HashMap<>();
        JSONArray list=new JSONArray().put(new JSONObject().put("spuId","P"));
        JSONArray getGameCenterMallItemList(String type) { return list; }
        @@MALL@@
    }
    enum GameTask {
        @@GAMES@@
        String title,appId,gid,action,channel,version; int requestsPerEgg;
        @@GAME_METHODS@@
    }
    static class AntFarmRpcCall {
        static String detail; static String getMallItemDetail(String id) { return detail; }
        static String ranks;
        static String enterDonationCompetitionRank() { return ranks; }
        static String queryCompetitionEntranceInfo() { return new JSONObject().put("memo", "SUCCESS").put("animationInfo",
            new JSONObject().put("competitionProjectInfo", new JSONObject().put("projectId", "S2").put("projectName", "project"))).toString(); }
    }
    static class Farm {
        double harvestBenevolenceScore; Field<Integer> competitionStealLimit = new Field<>(0), competitionTargetRank = new Field<>(1); int donated;
        Field<Boolean> rankingDonation = new Field<>(false), rankingFoodRefill = new Field<>(false);
        boolean rankingFoodRefillBusy; String ownerUserId = "self";
        static class RankingSnapshot { String owner = "self"; }
        static RankingSnapshot rankingSnapshot(JSONObject response, String uid, boolean weekly) { return null; }
        boolean rankingOwner(String uid) { return true; }
        static boolean rankingWindow(RankingSnapshot rank, long now) { return true; }
        int rankingQuota(RankingSnapshot rank) { return 0; }
        void useDynamicSpecialFood(double target, RankingSnapshot rank) { throw new AssertionError("refill disabled"); }
        boolean donationCompetition(String id, String name, int n, String purpose) { donated += n; return true; }
        @@FARM@@
    }
    static void reset() {
        ApplicationHook.offline = false; AntForestRpcCall.cancelled = false;
        DailyTask.manual=false;AntForestRpcCall.homeThrows=false;AntForestRpcCall.loveReads=0;AntForestRpcCall.loveReply=null;
        Status.flags.clear(); Status.used = 0; Log.results.clear(); MessageUtil.blackHits = 0;
        AntMemberRpcCall.prizes = 0; AntMemberRpcCall.cancelLast=false; AntMemberRpcCall.losePushReply=false;
        AntMemberRpcCall.reads = 0; AntMemberRpcCall.calls.clear(); AntForestRpcCall.waters = 0;
        AntMemberRpcCall.first = tasks(new JSONArray().put(task(0,2)));
        AntMemberRpcCall.active = tasks(new JSONArray());
        AntMemberRpcCall.join = ok().put("data", new JSONObject().put("recordId", "R")).toString();
        AntMemberRpcCall.last = ok().put("data", new JSONObject().put("lastOperateTaskVO", task(0,2).put("recordId","R").put("finishFlag",false))).toString();
    }
    static void sesame(String response, int flags, int black, boolean completed) {
        reset(); AntMemberRpcCall.fresh = response; new Member().collectSesame();
        assert AntMemberRpcCall.calls.equals(List.of("join", "feedback", "last", "push:R"));
        assert Status.flags.size() == flags && MessageUtil.blackHits == black;
        assert Log.results.stream().anyMatch(s -> s.contains("完成任务[")) == completed : Log.results;
    }
    static void fertilize(boolean batch, Object progress, boolean accepted, boolean capped) {
        reset(); Orchard o = new Orchard(); o.spreadUseBatchThisTime = batch;
        JSONObject stage = new JSONObject(); if (progress != null) stage.put("totalValue", progress);
        AntOrchardRpcCall.response = ok().put("taobaoData", new JSONObject().put("currentStage", stage).toString()).toString();
        assert o.doSpreadManure(PlantScene.MAIN) == accepted;
        assert Status.hasFlagToday("self:spreadManureLimit:MAIN") == capped;
    }
    public static void main(String[] args) {
        reset();new Member().collectSesameFeedback(new JSONArray()
            .put(new JSONObject().put("status","UNCLAIMED").put("cateId","OTHER").put("creditFeedbackId","safe"))
            .put(new JSONObject().put("status","UNCLAIMED").put("cateId","PIGEON").put("creditFeedbackId","protected"))
            .put(new JSONObject().put("status","UNCLAIMED").put("creditFeedbackId","unknown"))
            .put(new JSONObject().put("status","CLAIMED").put("cateId","OTHER").put("creditFeedbackId","claimed"))
            .put(new JSONObject().put("status","UNCLAIMED").put("cateId","OTHER")), "test", false);
        assert AntMemberRpcCall.calls.equals(List.of("collect:safe")) : "unbound pigeon receipt blocked unrelated rewards or allowed unsafe claims";
        for (String bad : new String[]{"broken", ok().toString(), ok().put("data", new JSONObject()).toString(),
                tasks(new JSONArray().put(1)), tasks(new JSONArray().put(new JSONObject())),
                tasks(new JSONArray().put(task(-1,2))), tasks(new JSONArray().put(task(0,0))),
                tasks(new JSONArray().put(task(0,2).put("finishFlag","true")))}) sesame(bad,0,0,false);
        sesame(tasks(new JSONArray().put(task(0,2))),1,1,false);
        sesame(tasks(new JSONArray().put(task(1,2))),1,0,true);
        sesame(tasks(new JSONArray().put(task(2,2))),0,0,true);
        sesame(tasks(new JSONArray()),0,0,true);
        reset(); Member m = new Member(); JSONObject vo = task(0,2).put("recordId", "old");
        for (Object flag : new Object[]{JSONObject.NULL, "false", true, false}) {
            vo.put("finishFlag",flag);
            AntMemberRpcCall.last = ok().put("data", new JSONObject().put("lastOperateTaskVO", vo)).toString();
            assert Objects.equals(m.lastOperateRecordId("T"), Boolean.FALSE.equals(flag) ? "old" : null);
            assert m.lastOperateRecordId("different") == null;
        }
        AntMemberRpcCall.calls.clear(); AntMemberRpcCall.join = new JSONObject().put("resultCode", "PROMISE_HAS_PROCESSING_TEMPLATE").toString();
        m.reportSesameTask("task", "T", "", "");
        assert AntMemberRpcCall.calls.equals(List.of("join","refresh","last","feedback","last","push:old"));
        vo.put("recordId", new JSONObject());
        AntMemberRpcCall.last = ok().put("data", new JSONObject().put("lastOperateTaskVO", vo)).toString();
        assert m.lastOperateRecordId("T") == null;
        AntMemberRpcCall.calls.clear();
        AntMemberRpcCall.join = ok().put("data", new JSONObject().put("recordId", new JSONObject())).toString();
        m.reportSesameTask("task", "T", "", ""); assert AntMemberRpcCall.calls.equals(List.of("join"));
        reset();
        AntMemberRpcCall.last=ok().put("data",new JSONObject().put("lastOperateTaskVO",task(0,2).put("recordId","R").put("finishFlag",true))).toString();
        assert !m.reportSesameTask("task","T","", "") && AntMemberRpcCall.calls.equals(List.of("join","feedback","last")) : "completed record must not push or mark stale aggregate as failed";
        for(String url:new String[]{"alipays://app?jumpAction=userGrowth", "https://a/?x=1&jumpAction=userGrowth&y=2", "alipays://app?url=https%3A%2F%2Fa%2F%3FjumpAction%3DuserGrowth", "https://a/?x=%ZZ"}) {
            reset(); assert !m.reportSesameTask("task","T",url, "") && AntMemberRpcCall.calls.equals(List.of("join","feedback","last"));
            assert Status.flags.isEmpty() && MessageUtil.blackHits==0;
        }
        for(JSONObject last:new JSONObject[]{new JSONObject(), task(0,2).put("recordId","other").put("finishFlag",true)}) {
            reset();AntMemberRpcCall.last=ok().put("data",new JSONObject().put("lastOperateTaskVO",last)).toString();
            assert m.reportSesameTask("task","T","", "R") && AntMemberRpcCall.calls.equals(List.of("feedback","last","push:R")) : "optional recent task blocked the known current record";
        }
        reset();AntMemberRpcCall.last=ok().put("data",new JSONObject()).toString();
        assert m.reportSesameTask("task","T","", "R")&&AntMemberRpcCall.calls.equals(List.of("feedback","last","push:R")) : "no recent task blocked the known current record";
        reset();AntMemberRpcCall.last=ok().put("data",new JSONObject().put("lastOperateTaskVO",task(0,2).put("recordId","R").put("finishFlag","false"))).toString();
        assert !m.reportSesameTask("task","T","", "R")&&!AntMemberRpcCall.calls.contains("push:R") : "invalid same-record completion flag was trusted";
        reset();AntMemberRpcCall.last=new JSONObject().put("resultCode","FAIL").toString();
        assert !m.reportSesameTask("task","T","", "R")&&!AntMemberRpcCall.calls.contains("push:R") : "failed recent query was ignored";
        reset();AntMemberRpcCall.last=ok().put("data",new JSONObject().put("lastOperateTaskVO",task(0,2).put("templateId",123).put("recordId","R").put("finishFlag",true))).toString();
        assert !m.reportSesameTask("task","123","", "R")&&!AntMemberRpcCall.calls.contains("push:R") : "numeric template ID failed to match a completed record";
        assert "R".equals(m.activeSesameRecordId(new JSONObject().put("toCompleteVOS",new JSONArray().put(task(0,2).put("templateId",123).put("recordId","R").put("finishFlag",false))),"123")) : "numeric template lost the existing record";
        reset();AntMemberRpcCall.join=new JSONObject().put("resultCode","PROMISE_HAS_PROCESSING_TEMPLATE").toString();
        AntMemberRpcCall.last=ok().put("data",new JSONObject().put("lastOperateTaskVO",task(0,2).put("templateId","other").put("recordId","foreign").put("finishFlag",false))).toString();
        m.collectSesame();assert AntMemberRpcCall.calls.equals(List.of("join","refresh","last")) && Status.flags.isEmpty() && MessageUtil.blackHits==0 : "unsubmitted task must not be blacklisted";
        reset();AntMemberRpcCall.first=tasks(new JSONArray().put(task(0,2).put("recordId","R").put("finishFlag",false)));
        AntMemberRpcCall.fresh=tasks(new JSONArray().put(task(1,2)));m.collectSesame();
        assert AntMemberRpcCall.calls.equals(List.of("feedback","last","push:R")) : "existing task record was needlessly joined again";
        for(String listName:new String[]{"toCompleteVOS","waitCompleteTaskVOS"}) {
            reset();AntMemberRpcCall.join=new JSONObject().put("resultCode","PROMISE_HAS_PROCESSING_TEMPLATE").toString();
            JSONArray activeRows=new JSONArray().put(task(0,2).put("recordId","R").put("finishFlag",false));
            JSONObject activeData=listName.equals("toCompleteVOS")?new JSONObject().put(listName,activeRows):new JSONObject().put("dailyTaskListVO",new JSONObject().put(listName,activeRows));
            AntMemberRpcCall.active=ok().put("data",activeData).toString();AntMemberRpcCall.fresh=tasks(new JSONArray().put(task(1,2)));
            m.collectSesame();assert AntMemberRpcCall.calls.equals(List.of("join","refresh","feedback","last","push:R")) : "processing record was not recovered from the full task list";
        }
        reset();AntMemberRpcCall.first=tasks(new JSONArray().put(task(0,2).put("recordId","R").put("finishFlag",false)).put(task(0,2).put("recordId","foreign").put("finishFlag",false)));
        m.collectSesame();assert AntMemberRpcCall.calls.isEmpty() : "conflicting records were submitted";
        for(Object invalid:new Object[]{new JSONObject(),7," "}) {
            reset();AntMemberRpcCall.first=tasks(new JSONArray().put(task(0,2).put("recordId",invalid).put("finishFlag",false)));
            m.collectSesame();assert AntMemberRpcCall.calls.isEmpty() : "invalid existing record was submitted";
        }
        reset();AntMemberRpcCall.first=tasks(new JSONArray().put(task(2,2).put("recordId","R").put("finishFlag",false)));
        m.collectSesame();assert AntMemberRpcCall.calls.isEmpty() : "completed record counters were ignored";
        reset();AntMemberRpcCall.cancelLast=true;
        try {m.reportSesameTask("task","T","", "");throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
        reset();AntMemberRpcCall.losePushReply=true;
        assert m.reportSesameTask("task","T","", "") : "unknown push outcome must still schedule task-list readback";
        assert AntMemberRpcCall.calls.equals(List.of("join","feedback","last","push:R"));
        Forest f = new Forest(); f.getAllSkuInfo();
        assert AntForestRpcCall.pages.size() == 10 && f.skuInfo.size() == 10 && VitalityBenefitIdMap.saves == 1;
        assert AntForestRpcCall.pages.contains("SKIN:20") && AntForestRpcCall.pages.contains("OTHER:20");
        AntForestRpcCall.pages.clear(); f.getAllSkuInfo(); assert AntForestRpcCall.pages.isEmpty() : "same-day direct call reloaded catalogue";
        f = new Forest(); assert f.getAllSkuInfo() && f.skuInfo.size()==10 : "persisted daily flag blocked restart catalogue restore";
        assert AntForestRpcCall.pages.size()==10 : "restart did not reload catalogue";
        AntForestRpcCall.pages.clear();
        Status.flags.clear(); AntForestRpcCall.forever = true; f.getAllSkuInfo();
        assert AntForestRpcCall.pages.size() == 50;
        Status.flags.clear(); AntForestRpcCall.brokenItems = true; assert !f.getAllSkuInfo() : "old cache cannot confirm a fresh fetch";
        f = new Forest(); assert !f.getAllSkuInfo() && f.skuInfo.isEmpty();
        int pageCount = AntForestRpcCall.pages.size();
        assert !f.getAllSkuInfo() && AntForestRpcCall.pages.size() == pageCount : "fourth failed catalogue fetch was not stopped";
        Status.counts.clear(); Status.flags.clear(); AntForestRpcCall.forever = AntForestRpcCall.brokenItems = false;
        AntForestRpcCall.partial = true; f = new Forest();
        assert !f.getAllSkuInfo() && !f.skuInfo.isEmpty() && !Status.hasFlagToday("forest::vitalitySkuList") : "partial catalogue claimed complete";
        AntForestRpcCall.partial = false;
        assert f.getAllSkuInfo() && Status.hasFlagToday("forest::vitalitySkuList") : "transient catalogue error blocked later recovery";
        Status.flags.clear(); Status.counts.clear(); AntForestRpcCall.cancelList = true;
        try { new Forest().getAllSkuInfo(); throw new AssertionError("catalogue cancellation swallowed"); } catch (TaskCancelledException expected) {}
        assert Status.counts.isEmpty() : "cancellation consumed catalogue retry quota";
        AntForestRpcCall.cancelList = false;
        reset(); AntForestRpcCall.switches.clear(); AntForestRpcCall.water=ok().toString();
        AntForestRpcCall.teamHome=ok().put("teamHomeResult",new JSONObject().put("mainMember",new JSONObject())
            .put("teamBaseInfo",new JSONObject().put("teamId","team"))).put("userBaseInfo",new JSONObject().put("currentEnergy",100)).toString();
        f.teamCooperateWater();assert AntForestRpcCall.waters==1&&AntForestRpcCall.switches.isEmpty() : "missing nextAction blocked existing team watering";
        AntForestRpcCall.teamHome=null;
        for (String response : new String[]{"broken", "{}", "{\"data\":true}", "{\"success\":\"false\"}", "{\"resultCode\":{}}",
                "{\"resultCode\":\"REMOTE_INVOKE_EXCEPTION\"}", "{\"resultCode\":\"3000\"}"}) {
            reset(); AntForestRpcCall.water = response; f.teamCooperateWater(); f.teamCooperateWater(); assert Status.used == 20 && AntForestRpcCall.waters == 1;
            Forest.loveteam(20); Forest.loveteam(20); assert Status.hasFlagToday(Forest.FLAG_LOVETEAM_WATER) && AntForestRpcCall.waters == 2;
        }
        for (String response : new String[]{"{\"success\":true}", "{\"resultCode\":\"SUCCESS\"}", "{\"resultCode\":\"LIMIT\"}"}) {
            reset(); AntForestRpcCall.water = response; f.teamCooperateWater(); f.teamCooperateWater();
            assert Status.used == 20 && AntForestRpcCall.waters == 1;
            Forest.loveteam(20); Forest.loveteam(20);
            assert Status.hasFlagToday(Forest.FLAG_LOVETEAM_WATER) && AntForestRpcCall.waters == 2;
        }
        reset(); ApplicationHook.offline = true; f.teamCooperateWater(); Forest.loveteam(20);
        assert Status.used == 0 && AntForestRpcCall.waters == 0 && !Status.hasFlagToday(Forest.FLAG_LOVETEAM_WATER);
        reset(); AntForestRpcCall.cancelled = true;
        try { f.teamCooperateWater(); throw new AssertionError("team cancel swallowed"); } catch (TaskCancelledException expected) {}
        try { Forest.loveteam(20); throw new AssertionError("love cancel swallowed"); } catch (TaskCancelledException expected) {}
        assert Status.used == 20 && Status.hasFlagToday(Forest.FLAG_LOVETEAM_WATER) && AntForestRpcCall.waters == 2;
        reset();Status.flagToday(Forest.FLAG_LOVETEAM_WATER);Forest.loveteam(20);
        DailyTask.manual=true;Forest.loveteam(20);
        assert AntForestRpcCall.loveReads==0&&AntForestRpcCall.waters==0 : "already watered must not query rewards, even manually";
        reset();ApplicationHook.offline=true;Forest.loveteam(20);assert AntForestRpcCall.loveReads==0;
        reset();AntForestRpcCall.loveReply="broken";Forest.loveteam(20);assert AntForestRpcCall.waters==0&&!Status.hasFlagToday(Forest.FLAG_LOVETEAM_WATER);
        reset();AntForestRpcCall.loveReply=new JSONObject().put("resData",ok().put("userInfo",new JSONObject().put("teamId","team"))
          .put("teamInfo",new JSONObject().put("popupRewardList",new JSONArray().put(new JSONObject().put("provideType","daily").put("rewardCode","LoveEnergyShield"))))).toString();
        Forest.loveteam(20);Forest.loveteam(20);assert AntForestRpcCall.loveReads==1&&AntForestRpcCall.waters==1;
        assert Log.results.stream().noneMatch(x->x.contains("真爱奖励"));
        reset();AntForestRpcCall.homeThrows=true;Forest.loveteam(20);
        assert Log.results.stream().anyMatch(x->x.contains("查询浇水队伍"))&&Log.results.stream().noneMatch(x->x.contains("secret"));
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        Mall mall=new Mall(); AntFarmRpcCall.detail=new JSONObject().put("success",true).put("mallItemDetail",
            new JSONObject().put("mallSubItemDetailList",new JSONArray().put(new JSONObject().put("skuId","sku").put("skuName","name")))).toString();
        assert mall.getAllSkuInfo() && mall.getAllSkuInfo() && mall.skuInfo.size()==1;
        AntFarmRpcCall.detail="broken"; assert !mall.getAllSkuInfo();
        mall.list=null; assert !mall.getAllSkuInfo();
        Set<String> pending=new HashSet<>();
        for (String bad : new String[]{"broken","{}","{\"data\":{}}","{\"data\":{\"taskModuleList\":[]}}",
                "{\"success\":false,\"data\":{\"taskModuleList\":[]}}",
                "{\"success\":true,\"data\":{\"taskModuleList\":[{}]}}",
                "{\"success\":true,\"data\":{\"taskModuleList\":[{\"taskList\":[1]}]}}"}) assert !Member.collectNotDoneIds(bad,pending,new HashMap<>());
        assert Member.collectNotDoneIds("{\"success\":true,\"data\":{\"taskModuleList\":[]}}",pending,new HashMap<>()) && pending.isEmpty();
        assert Member.collectNotDoneIds("{\"success\":true,\"data\":{\"taskModuleList\":[{\"taskList\":[{\"taskId\":\"T\",\"taskStatus\":\"NOT_DONE\"}]}]}}",pending,new HashMap<>()) && pending.contains("T");
        for (JSONObject taskModule : new JSONObject[]{new JSONObject(),new JSONObject().put("needReceive","true"),
                new JSONObject().put("needReceiveTaskCertCnt","1"),new JSONObject().put("needReceiveTaskCertCnt",1.5),
                new JSONObject().put("needReceive",true).put("reachTaskCertLimit",true)}) {
            reset(); AntMemberRpcCall.gameHome=new JSONObject().put("success",true).put("data",
                new JSONObject().put("taskModule",taskModule).put("taskPrizePopup",JSONObject.NULL)).toString();
            m.gameCenterTaskPrize(); assert AntMemberRpcCall.prizes==0;
        }
        reset(); AntMemberRpcCall.gameHome="broken";m.gameCenterTaskPrize();assert AntMemberRpcCall.prizes==0;
        for (JSONObject taskModule : new JSONObject[]{new JSONObject().put("needReceive",true),new JSONObject().put("needReceiveTaskCertCnt",1)}) {
            reset(); AntMemberRpcCall.gameHome=new JSONObject().put("success",true).put("data",new JSONObject().put("taskModule",taskModule)).toString();
            m.gameCenterTaskPrize(); assert AntMemberRpcCall.prizes==1;
        }
        fertilize(false,101,true,true); fertilize(true,105,true,true); fertilize(true,106,true,false);
        fertilize(false,99,true,false); fertilize(true,null,false,false); fertilize(true,"bad",false,false);
        reset(); AntFarmRpcCall.ranks = new JSONObject().put("memo","SUCCESS").put("donationRankHomeInfo", new JSONObject()
            .put("userDonationRankList",new JSONArray().put(new JSONObject().put("userId","friend").put("rankOrder",1).put("donationNum",10))
                .put(new JSONObject().put("userId","self").put("rankOrder",2).put("donationNum",5)))).toString();
        Farm farm = new Farm(); farm.harvestBenevolenceScore = 5; farm.stealRankS2(); assert farm.donated == 0;
        farm.harvestBenevolenceScore = 20; farm.competitionStealLimit.value = 5; farm.stealRankS2(); assert farm.donated == 0;
        farm.competitionStealLimit.value = 6; farm.stealRankS2(); assert farm.donated == 6;
        AntFarmRpcCall.ranks = new JSONObject().put("memo","SUCCESS").put("donationRankHomeInfo",new JSONObject()
            .put("userDonationRankList",new JSONArray().put(new JSONObject().put("userId","self").put("rankOrder",2)))).toString();
        farm.stealRankS2(); assert farm.donated == 6;
        System.out.println("PASS: sesame report/readback, record reuse, five-category pagination, watering quotas, fertilizer caps and donation limits");
    }
}
'''
for placeholder, path, signatures in (
    ("@@RETRY@@", "util/MessageUtil.java", ("public static boolean isRetryable(",)),
    ("@@MEMBER@@", member, ("private void collectSesameFeedback(", "private void collectSesame(", "private boolean reportSesameTask(", "private String lastOperateRecordId(", "private String activeSesameRecordId(", "public void gameCenterTaskPrize(", "private static boolean collectNotDoneIds(", "private static boolean collectNotDoneFromArray(")),
    ("@@FOREST@@", "model/task/antForest/AntForestV2.java", ("private boolean getAllSkuInfo(", "private static void accountVitalitySkuRetry(", "private static JSONArray optItemInfoVOList(",
        "private static boolean hasMore(", "private boolean getSkuInfoByItemInfoVO(", "private void teamCooperateWater(",
        "private static JSONObject queryTeamHomePage(", "private static String getTeamId(", "private static int getTeamCanWaterCount(",
        "private static boolean updateUserConfiginTeam(", "private static int teamState(", "private static void loveteam(",
        "private static String getLoveteamName(", "private static boolean hasWaterResult(", "private static void loveteamWater(")),
    ("@@MALL@@", "model/task/antFarm/AntFarm.java", ("private boolean getAllSkuInfo(", "private boolean getSkuInfoByItemInfoVO(")),
    ("@@GAME_METHODS@@", "model/task/antGame/GameTask.java", ("GameTask(String title,",)),
    ("@@ORCHARD@@", "model/task/antOrchard/AntOrchard.java", ("private boolean doSpreadManure(",)),
    ("@@FARM@@", "model/task/antFarm/AntFarm.java", ("private void stealRankS2(", "private static int rankingInt(")),
):
    flows = flows.replace(placeholder, "\n".join(method(path, signature) for signature in signatures))

game_source=(SOURCE / "model/task/antGame/GameTask.java").read_text(encoding="utf-8")
flows=flows.replace("@@GAMES@@",game_source.split("public enum GameTask {",1)[1].split(";",1)[0]+";")


# New upstream fixes run production save/target/claim code, not a copy of their formulas.
fixes = r"""
import java.util.*;import java.io.File;import java.util.regex.*;
public class UpstreamFixCheck {
 static class System {static long now=java.time.Instant.parse("2026-10-08T02:00:00Z").toEpochMilli();static long currentTimeMillis(){return now;}static final java.io.PrintStream out=java.lang.System.out;}
 static class TimeUtil {static Calendar getNow(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(System.now);return c;}static boolean isLessThanSecondOfDays(long a,long b){return java.time.Instant.ofEpochMilli(a).atZone(java.time.ZoneOffset.ofHours(8)).toLocalDate().isBefore(java.time.Instant.ofEpochMilli(b).atZone(java.time.ZoneOffset.ofHours(8)).toLocalDate());}}
 static class UserIdMap {static String getCurrentUid(){return "self";}}
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class Log {static int failures,recovered;static void record(String s){}static void debug(String s){}static void system(String t,String s){if(s.contains("恢复正常"))recovered++;if(s.contains("当日进度可能"))failures++;}static void printStackTrace(String t,Throwable e){}}
 static class Toast {static int shown;static void show(String s,boolean longTime){shown++;}}
 static class FileUtil {static boolean success;static int writes;static File getStatusFile(String s){return new File("never-created-status.json");}static boolean write2File(String s,File f){writes++;return success;}}
 static class JsonUtil {static boolean fail;static String toFormatJsonString(Object o){if(fail)throw new IllegalStateException("serialization");return "{}";}}
 static class MessageUtil {static void sweepExpiredBlackList(){}static void sweepReleasedDefaults(){}}
 static class Status {static final String TAG="check";static final Status INSTANCE=new Status();static boolean saveFailureNotified;static int unloads;long saveTime;Set<String> flags=new HashSet<>();static void unload(){unloads++;INSTANCE.flags.clear();}static String ensureLoadedForCurrentUid(){return UserIdMap.getCurrentUid();}
 @@STATUS@@
 }
 static class Statistics {enum DataType{COLLECTED}static int collected;static void addData(DataType type,int amount){collected+=amount;}}
 static final Pattern ENERGY_PRIZE_PATTERN=Pattern.compile("(\\d+)g能量");
 static int claim(String prizeName,int prizeNum){Statistics.collected=0;@@CLAIM@@ return Statistics.collected;}
 static class Looper{static final Looper main=new Looper();static Looper getMainLooper(){return main;}}
 static class Handler{final Looper looper;Handler(Looper l){looper=l;}}
 static class ApplicationHook{static Handler main;static Handler getMainHandler(){return main;}}
 static class SystemChildTaskExecutor{Handler handler;@@CONSTRUCTOR@@}
 static class Field {boolean value;Boolean getValue(){return value;}}
 static final int MAIN_SPREAD_DAILY_LIMIT=200,BATCH_SPREAD_SIZE=5,MAIN_SPREAD_BURST_LIMIT=MAIN_SPREAD_DAILY_LIMIT-1+BATCH_SPREAD_SIZE;
 Field useBatchSpread=new Field();@@TARGET@@
 public static void main(String[] args){
  Status.INSTANCE.saveTime=System.now-86400000L;Status.save();assert Status.unloads==1&&Log.failures==1&&Toast.shown==1;
  Status.INSTANCE.flags.add("already-sent");Status.save();assert Status.unloads==1&&Status.INSTANCE.flags.contains("already-sent")&&Log.failures==1;
  FileUtil.success=true;Status.save();assert Log.recovered==1&&!Status.saveFailureNotified;
  FileUtil.success=false;Status.save();assert Log.failures==2&&Toast.shown==2&&Status.INSTANCE.flags.contains("already-sent");
  JsonUtil.fail=true;try{Status.save();throw new AssertionError("serialization hidden");}catch(IllegalStateException expected){}assert Status.INSTANCE.saveTime==System.now&&Status.INSTANCE.flags.contains("already-sent");
  assert claim("188g能量",2)==376&&claim("5g能量",1)==5&&claim("200g能量",3)==600&&claim("能量体验卡",1)==0&&claim("g能量",3)==0;
  assert new SystemChildTaskExecutor().handler.looper==Looper.getMainLooper();ApplicationHook.main=new Handler(new Looper());assert new SystemChildTaskExecutor().handler==ApplicationHook.main;
  UpstreamFixCheck c=new UpstreamFixCheck();assert c.targetSpreadTimes(null)==0&&c.targetSpreadTimes(-1)==0&&c.targetSpreadTimes(250)==200;
  c.useBatchSpread.value=true;assert c.targetSpreadTimes(1)==5&&c.targetSpreadTimes(40)==200&&c.targetSpreadTimes(41)==204&&c.targetSpreadTimes(100)==204;
  assert shouldBatchSpread(199,204)&&!shouldBatchSpread(200,204)&&!shouldBatchSpread(204,204)&&!shouldBatchSpread(198,204);
  System.out.println("PASS upstream production fixes: status write failure/recovery/flags, actual energy grams and 204 cap with M per-scene operation counts");
 }
}
"""
fixes=fixes.replace("@@STATUS@@","\n".join(method("util/Status.java",m) for m in ("public static synchronized void save()", "public static synchronized void save(Calendar", "private static void notifySaveFailure(", "public static synchronized Boolean updateDay(")))
fixes=fixes.replace("@@CLAIM@@",method("model/task/antForest/ForestChouChouLe.java", 'if (prizeName.contains("g能量"))'))
fixes=fixes.replace("@@CONSTRUCTOR@@",method("data/task/SystemChildTaskExecutor.java","public SystemChildTaskExecutor()"))
fixes=fixes.replace("@@TARGET@@","\n".join(method("model/task/antOrchard/AntOrchard.java",m) for m in ("private int targetSpreadTimes(","private static boolean shouldBatchSpread(")))

toast = r'''
public class ToastCheck {
 static class Context {}
 static class Handler {}
 static class AppConfig {static final AppConfig INSTANCE=new AppConfig();boolean enabled;boolean getShowToast(){return enabled;}}
 static class ApplicationHook {static Context context;static Handler handler;static Context getContext(){return context;}static Handler getMainHandler(){return handler;}}
 static int shown;
 static void show(Context context,Handler handler,CharSequence text){assert context!=null&&handler!=null;shown++;}
 @@SHOW@@
 public static void main(String[] args){
  show("early",true);assert shown==0;
  ApplicationHook.context=new Context();show("no handler",true);assert shown==0;
  ApplicationHook.handler=new Handler();show("disabled",false);assert shown==0;
  show("forced",true);assert shown==1;
  AppConfig.INSTANCE.enabled=true;show("enabled",false);assert shown==2;
  ApplicationHook.context=null;show("no context",true);assert shown==2;
  System.out.println("PASS upstream toast: missing context/handler skipped, enabled and forced notifications retained");
 }
}
'''
toast = toast.replace("@@SHOW@@", method("hook/Toast.java", "public static void show(CharSequence cs, boolean force)")
                      .replace("io.github.aw1y2z.sesame.data.AppConfig", "AppConfig"))


patrol = r'''
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
public class PatrolSyncCheck {
 static final String TAG="test";
 static List<String> events=Collections.synchronizedList(new ArrayList<>());
 static int pushes, queries, exchanges, left, used, chance, firstCost, observed, delayedReadAt, walkReads, waitMillis;
 static boolean accepted=true, cancel, offline, fail, refreshPatrol=true, refreshWalk=true, missingSync, finishContinuation, invalidWalkReadback, bulkExchange, invalidInitialRead, crossReadback, cancelDelayed;
 static List<String> runtimeLogs=new ArrayList<>();
 static int toasts, diagnostics, hour=12, departures, continuations, pageReads;
 static boolean pageRefresh, nativeBucket;
 static String patrolState="STANDING", responseDay="20261011";static int date=11;static boolean missingChance, badRecord;
 static class Toast {static void show(String s){toasts++;}}
 static CountDownLatch entered, release;
 static class TaskCancelledException extends RuntimeException {}
 static class TimeUtil {static void sleep(int ms){waitMillis+=ms;if(cancelDelayed&&ms>0)cancel=true;if(cancel)throw new TaskCancelledException();}}
 static class MyUtils {
  static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.set(2026,Calendar.OCTOBER,date,hour,0,0);return c;}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
 }
 static class Status {static boolean done;static Set<String> patrolDone=new HashSet<>();static boolean hasFlagToday(String s){return s.startsWith("forest::")?patrolDone.contains(s):done;}static void flagToday(String s){if(s.startsWith("forest::"))patrolDone.add(s);else done=true;}}
 final Map<String,String> legacyPatrolLogs=new HashMap<>();
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class ApplicationHook {static boolean isOffline(){return offline;}static ClassLoader getClassLoader(){return null;}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class Log {static void printStackTrace(String tag,Throwable t){assert t.getCause()==null&&!t.getMessage().contains("secret")&&t.getStackTrace().length>0;diagnostics++;}static void record(String s){runtimeLogs.add(s);}static void i(String s){}static void forest(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class Field {int value;int getValue(){return value;}}
 static class AntSportsRpcCall {static String queryWalkStep(){return queryWalkStep("Asia/Shanghai");}static String queryWalkStep(String zone){if(nativeBucket)return new JSONObject().put("resultCode","SUCCESS").put("stepCount","Asia/Ho_Chi_Minh".equals(zone)?23660:0).put("success",true).toString();if(pushes>0 && ++walkReads==delayedReadAt)observed=22000;if(pushes>0&&crossReadback)date=12;return new JSONObject().put("resultCode","SUCCESS").put("stepCount",observed).put("success",!(invalidInitialRead||(invalidWalkReadback&&pushes>0))).toString();}}
 static class AntSports {
  static String nativeStepTimezone(ClassLoader loader){return nativeBucket?"Asia/Ho_Chi_Minh":null;}
  static void queryLegacyPatrolSteps(ClassLoader loader){pageReads++;events.add("page-read");if(pageRefresh)left=23426;}
  final Object stepSyncLock=new Object();final Map<String,Long> stepSyncRetryAt=new HashMap<>();int stepSyncRetryDay;final int SKIP_SYNC_STEP=18000;Field earliestSyncStepTime=new Field();boolean enabled=true;
  Boolean isEnable(){return enabled;}int tmpStepCount(){return 22000;}
  boolean syncStepByRpcManager(ClassLoader c,int step) throws Exception {
   pushes++;events.add("sync");if(missingSync)throw new NoSuchMethodException("step sync unavailable");if(fail)throw new IllegalStateException("secret",new RuntimeException("secret"));if(entered!=null){entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("sync timeout");}
   if(accepted&&refreshPatrol)left=step;if(accepted&&refreshWalk)observed=step;return accepted;
  }
  @@SYNC@@
 }
 static class DailyTask {static boolean manual;static boolean isManual(){return manual;}@@DAILY_SKIP@@}
 static AntSports sports;
 static <T>T getModel(Class<T> c){return c.cast(sports);}
 static class AntForestRpcCall {
  static String queryUserPatrol(){queries++;events.add("query");return new JSONObject().put("resultCode","SUCCESS").put("userPatrol",new JSONObject().put("currentStatus",patrolState).put("patrolId",2).put("currentNode",0).put("chance",new JSONObject().put("dayString",responseDay).put("leftChance",missingChance?JSONObject.NULL:chance).put("leftStep",left).put("usedStep",used))).toString();}
  static String queryMyPatrolRecord(){return new JSONObject().put("resultCode",badRecord?"FAIL":"SUCCESS").toString();}static String switchUserPatrol(String id){return "{}";}
  static String patrolGo(int n,int p){departures++;chance--;return new JSONObject().put("resultCode","SUCCESS").toString();}
  static String exchangePatrolChance(int cost){if(exchanges++==0)firstCost=cost;events.add("exchange");int added=bulkExchange?5:1;used+=2000*added;left-=2000*added;chance+=added;return new JSONObject().put("resultCode","SUCCESS").put("addedChance",added).toString();}
 }
 void patrolKeepGoing(String s,int n,int p){continuations++;if(finishContinuation)patrolState="STANDING";}
 @@PATROL@@
 static void reset(){sports=new AntSports();pushes=queries=exchanges=left=used=chance=firstCost=observed=delayedReadAt=walkReads=waitMillis=0;accepted=refreshPatrol=refreshWalk=true;finishContinuation=invalidWalkReadback=bulkExchange=invalidInitialRead=crossReadback=cancelDelayed=false;missingSync=false;UserIdMap.uid="A";runtimeLogs.clear();cancel=offline=Status.done=fail=false;toasts=diagnostics=departures=continuations=pageReads=0;pageRefresh=nativeBucket=false;DailyTask.manual=false;hour=12;date=11;responseDay="20261011";missingChance=badRecord=false;Status.patrolDone.clear();patrolState="STANDING";entered=release=null;events.clear();}
 public static void main(String[] args)throws Exception {
  PatrolSyncCheck pageFlow=new PatrolSyncCheck();reset();accepted=false;pageRefresh=true;bulkExchange=true;pageFlow.queryUserPatrol();
  assert pageReads==1&&exchanges==1&&firstCost==23426&&departures==5:"page step read is missing before patrol query";
  assert events.indexOf("sync")<events.indexOf("page-read")&&events.indexOf("page-read")<events.indexOf("query"):"sync then page read then patrol query";
  pageFlow.queryUserPatrol();assert pageReads==1:"completed daily patrol must not repeat native page reads";
  reset();sports.enabled=false;pageRefresh=true;bulkExchange=true;pageFlow.queryUserPatrol();assert pushes==0&&pageReads==1&&departures==5:"read and patrol must run when upload is disabled";
  reset();hour=0;sports.earliestSyncStepTime.value=8;pageRefresh=true;bulkExchange=true;pageFlow.queryUserPatrol();assert pushes==0&&pageReads==1&&departures==5:"read and exchange cannot depend on upload time";
  PatrolSyncCheck f=new PatrolSyncCheck();
  reset();nativeBucket=true;sports.syncStepNow();nativeBucket=false;assert pushes==0&&!Status.done:"sync preflight must read the native timezone bucket before attempting another upload";
  reset();f.queryUserPatrol();
  assert pushes==1&&exchanges==5&&used==10000&&firstCost==22000&&toasts==1;assert events.indexOf("sync")<events.indexOf("query")&&events.indexOf("query")<events.indexOf("exchange");
  reset();fail=true;f.queryUserPatrol();assert diagnostics==1&&toasts==0&&!Status.done&&queries==1;
  reset();used=10000;f.queryUserPatrol();assert pushes==1&&exchanges==0;
  assert Status.patrolDone.size()==1;int oldQueries=queries;Status.done=false;f.queryUserPatrol();assert queries==oldQueries&&pushes==1;
  date=12;responseDay="20261012";used=observed=0;f.queryUserPatrol();assert pushes==2&&exchanges==5;
  reset();sports.enabled=false;used=10000;chance=2;f.queryUserPatrol();assert departures==2&&Status.patrolDone.size()==1;
  reset();sports.enabled=false;used=10000;f.queryUserPatrol();assert Status.patrolDone.size()==1;
  chance=2;DailyTask.manual=true;f.queryUserPatrol();assert departures==2&&chance==0;
  DailyTask.manual=false;oldQueries=queries;f.queryUserPatrol();assert queries==oldQueries;
  reset();sports.enabled=false;used=10000;patrolState="GOING";finishContinuation=false;f.queryUserPatrol();assert continuations==1&&Status.patrolDone.isEmpty();patrolState="STANDING";f.queryUserPatrol();assert Status.patrolDone.size()==1;
  reset();sports.enabled=false;left=23816;patrolState="GOING";finishContinuation=true;f.queryUserPatrol();
  assert continuations==6&&exchanges==5&&departures==5&&Status.patrolDone.size()==1:"resumed patrol must requery and exchange available steps in same run";
  finishContinuation=false;
  reset();missingChance=true;f.queryUserPatrol();assert exchanges==0&&Status.patrolDone.isEmpty();
  reset();used=-1;f.queryUserPatrol();assert exchanges==0&&Status.patrolDone.isEmpty();
  reset();used=10000;responseDay="20261010";f.queryUserPatrol();assert Status.patrolDone.isEmpty();
  reset();used=10000;badRecord=true;f.queryUserPatrol();assert Status.patrolDone.isEmpty();

  reset();accepted=false;f.queryUserPatrol();assert queries==1&&exchanges==0&&!Status.done;sports.syncStepNow();assert pushes==2;
  reset();refreshWalk=false;refreshPatrol=false;f.queryUserPatrol();assert !Status.done&&exchanges==0:"host acceptance alone cannot mark daily synchronization complete";
  sports.syncStepNow();assert pushes==1:"accepted upload must not be repeated by the next module while business counters lag";
  assert !Status.done;left=4000;f.queryUserPatrol();assert exchanges==2:"cooldown must still query and patrol confirmed available steps";
  sports.stepSyncRetryAt.replaceAll((key,value)->0L);sports.syncStepNow();assert pushes==2:"unconfirmed sync must retry after short wait";refreshWalk=true;
  reset();refreshWalk=false;delayedReadAt=2;f.queryUserPatrol();assert Status.done&&pushes==1&&waitMillis>=5000:"delayed readback must confirm without a second upload";
  reset();refreshWalk=false;delayedReadAt=3;sports.syncStepNow();assert Status.done&&pushes==1&&waitMillis==20000:"final delayed readback must stay bounded";
  reset();refreshWalk=false;refreshPatrol=false;f.queryUserPatrol();UserIdMap.uid="B";sports.syncStepNow();assert pushes==2:"short wait must be per account";
  reset();refreshWalk=false;refreshPatrol=false;f.queryUserPatrol();date=12;responseDay="20261012";sports.syncStepNow();assert pushes==2:"new GMT+8 day must reset short wait";
  reset();invalidInitialRead=true;left=4000;f.queryUserPatrol();assert pushes==0&&exchanges==2:"unknown initial sports steps must not upload but patrol confirmed steps remain usable";
  reset();crossReadback=true;sports.syncStepNow();assert !Status.done&&runtimeLogs.stream().anyMatch(x->x.contains("响应跨东八区日期")):"old-day response must not mark new-day synchronization done";
  reset();refreshWalk=false;cancelDelayed=true;try{f.queryUserPatrol();throw new AssertionError("delayed cancellation swallowed");}catch(TaskCancelledException expected){}assert queries==0&&!Status.done;
  reset();invalidWalkReadback=true;f.queryUserPatrol();assert !Status.done&&exchanges==5:"failed sports readback cannot seal sync but patrol must still exchange its confirmed steps";
  reset();sports.enabled=false;left=4000;f.queryUserPatrol();assert pushes==0&&exchanges==2;
  reset();sports.earliestSyncStepTime.value=13;f.queryUserPatrol();assert pushes==0&&queries==1;
  reset();hour=0;sports.earliestSyncStepTime.value=8;chance=3;f.queryUserPatrol();
  assert pushes==0&&exchanges==0&&departures==3&&continuations==3&&chance==0&&!Status.done;
  hour=8;f.queryUserPatrol();assert pushes==1&&exchanges==5&&departures==8;
  reset();hour=0;sports.earliestSyncStepTime.value=8;patrolState="GOING";f.queryUserPatrol();
  assert pushes==0&&exchanges==0&&departures==0&&continuations==1;
  reset();accepted=false;chance=3;f.queryUserPatrol();assert pushes==1&&exchanges==0&&departures==3;
  reset();sports.enabled=false;chance=3;f.queryUserPatrol();assert pushes==0&&exchanges==0&&departures==3;
  reset();Status.done=true;f.queryUserPatrol();assert pushes==0&&queries==1;
  reset();observed=19000;f.queryUserPatrol();assert pushes==0;
  reset();cancel=true;try{f.queryUserPatrol();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert queries==0&&pushes==0;
  reset();accepted=false;left=23816;f.queryUserPatrol();assert exchanges==5&&departures==5&&Status.patrolDone.size()==1;
  assert runtimeLogs.stream().anyMatch(x->x.contains("可用步数=23816"));
  assert runtimeLogs.stream().anyMatch(x->x.contains("exchangePatrolChance成功"));
  reset();sports.enabled=false;bulkExchange=true;left=23816;f.queryUserPatrol();
  assert exchanges==1&&firstCost==23816&&departures==5&&used==10000&&left==13816&&Status.patrolDone.size()==1:"captured batch exchange must consume five chances and patrol all five";
  assert runtimeLogs.stream().anyMatch(x->x.contains("今日5次步数兑换及巡护已完成"));
  reset();f.legacyPatrolLogs.clear();refreshPatrol=false;f.queryUserPatrol();assert Status.done&&exchanges==0&&Status.patrolDone.isEmpty();
  assert runtimeLogs.stream().anyMatch(x->x.contains("可用步数不足2000"));
  left=23816;f.queryUserPatrol();assert pushes==1&&exchanges==5&&departures==5;
  reset();missingSync=true;left=23816;new PatrolSyncCheck().queryUserPatrol();
  assert Status.done&&pushes==1&&exchanges==5&&departures==5;
  sports.syncStepNow();assert pushes==1&&runtimeLogs.stream().filter(x->x.contains("宿主接口不可用")).count()==1;
  assert runtimeLogs.stream().noneMatch(x->x.contains("ClassNotFoundException")||x.contains("RpcManager"));
  reset();sports.enabled=false;PatrolSyncCheck quiet=new PatrolSyncCheck();quiet.queryUserPatrol();quiet.queryUserPatrol();
  assert runtimeLogs.stream().filter(x->x.contains("旧版保护地[查询]")).count()==1;
  assert runtimeLogs.stream().filter(x->x.contains("旧版保护地[未兑换]")).count()==1;
  left=100;quiet.queryUserPatrol();assert runtimeLogs.stream().filter(x->x.contains("可用步数=100")).count()==1;
  DailyTask.manual=true;quiet.queryUserPatrol();quiet.queryUserPatrol();
  assert runtimeLogs.stream().filter(x->x.contains("可用步数=100")).count()==3;
  DailyTask.manual=false;UserIdMap.uid="B";quiet.queryUserPatrol();date=12;quiet.queryUserPatrol();
  assert runtimeLogs.stream().filter(x->x.contains("可用步数=100")).count()==5;
  reset();entered=new CountDownLatch(1);release=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
  try{Future<?> a=pool.submit(()->sports.syncStepNow());assert entered.await(5,TimeUnit.SECONDS);Future<?> b=pool.submit(()->f.queryUserPatrol());assert queries==0;release.countDown();a.get(5,TimeUnit.SECONDS);b.get(5,TimeUnit.SECONDS);assert pushes==1&&exchanges==5;}finally{release.countDown();pool.shutdownNow();}
  System.out.println("PASS patrol: sync before query/exchange, concurrent deduplication, quotas, midnight patrol without exchange, delayed sync, disabled/time/failure/cancellation");
 }
}
'''
patrol = patrol.replace("@@DAILY_SKIP@@", method("util/DailyTask.java", "public static boolean skipFlag("))
patrol = patrol.replace("@@SYNC@@", method("model/task/antSports/AntSports.java", "public void syncStepNow("))
patrol = patrol.replace("@@PATROL@@", method("model/task/antForest/AntForestV2.java", "private void logLegacyPatrolState(") + method("model/task/antForest/AntForestV2.java", "private void queryUserPatrol("))


love_request = r'''
import org.json.*;
public class LoveWaterRequestCheck {
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}}
 static class ApplicationHook {static String method,args;static String requestString(String m,String a){method=m;args=a;return "{}";}}
 @@HOME@@
 public static void main(String[] args)throws Exception {
  loveteamHome();JSONArray requests=new JSONArray(ApplicationHook.args);JSONObject request=requests.optJSONObject(0);
  assert ApplicationHook.method.equals("alipay.greenmatrix.rpc.h5.love.loveHome");
  assert requests.length()==1&&request.length()==1&&request.optString("source").equals("chInfo_ch_appcenter__chsub_9patch");
  System.out.println("PASS true-love watering request: original source only, no reward calendar parameters");
 }
}
'''
love_request = love_request.replace("@@HOME@@", method("model/task/antForest/AntForestRpcCall.java", "public static String loveteamHome("))

sync_probe = r'''
import java.util.*;import java.lang.reflect.*;
public class SyncProbeCheck {
 static List<String> logs=new ArrayList<>();static int calls;static boolean accepted;
 static class Log {static void record(String s){logs.add(s);}}
 public static void sync(int n,boolean b,String s){}
 static List<Method> findSyncStepMethods(Class<?> c){try{return Collections.singletonList(c.getMethod("sync",int.class,boolean.class,String.class));}catch(Exception e){throw new AssertionError(e);}}
 static List<Object> collectRpcManagerInstances(Class<?> c){throw new AssertionError("static probe expected");}
 static boolean invokeSyncStepMethod(Method m,Object target,int step){calls++;return accepted;}
 @@PROBE@@
 public static void main(String[] args)throws Exception{
  ClassLoader absent=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{throw new ClassNotFoundException("secret");}};
  try{syncStepByRpcManager(absent,23000);throw new AssertionError("missing sync not classified");}catch(NoSuchMethodException expected){}
  assert logs.isEmpty()&&calls==0;
  ClassLoader fallback=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{if(n.contains("intergation"))throw new ClassNotFoundException("secret");return SyncProbeCheck.class;}};
  accepted=true;assert syncStepByRpcManager(fallback,23000)&&calls==1;
  assert logs.size()==1&&logs.get(0).contains("调用接口");
  logs.clear();accepted=false;assert !syncStepByRpcManager(fallback,23000)&&calls==2;
  assert logs.stream().noneMatch(x->x.contains("ClassNotFoundException")||x.contains("secret"));
  logs.clear();ClassLoader broken=new ClassLoader(){public Class<?> loadClass(String n)throws ClassNotFoundException{if(n.contains("intergation"))throw new LinkageError("secret");throw new ClassNotFoundException("secret");}};
  assert !syncStepByRpcManager(broken,23000)&&logs.size()==1&&logs.get(0).contains("LinkageError")&&!logs.get(0).contains("secret");
  System.out.println("PASS sync discovery: missing candidates silent, fallback works, genuine loading failures remain visible");
 }
}
'''
sync_probe = sync_probe.replace("@@PROBE@@", method("model/task/antSports/AntSports.java", "private static boolean syncStepByRpcManager("))

reload_check = r'''
import org.json.*;import java.util.*;
public class ReloadCheck {
 static final String TAG="reload";static final int FALLBACK_INTERVAL=180000,VITALITY_EXCHANGE_RETRY_MAX=3;
 boolean initializing=true,init,offline;int executions;long delayed;
 static class Log {static void i(String t,String s){}static void record(String s){}static void printStackTrace(Throwable t){throw new AssertionError(t);}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class BaseModel {static int data;static void initData(){data++;}static Interval getCheckInterval(){return new Interval();}static class Interval{int getValue(){return interval;}}}
 static int interval=300000;static boolean rejectInit,throwInit;
 void execHandler(){executions++;}void execDelayedHandler(long delay){delayed=delay;NotificationUtil.nextExecTime=delay;}
 boolean initializeHandler(Boolean force,boolean exec){if(throwInit)throw new IllegalStateException();if(rejectInit)return false;@@SCHEDULE@@}
 Boolean initHandler(Boolean force,TaskLifecycle.Freeze owner,boolean exec){return runInit(force,owner,exec);}
 @@INIT@@
 @@LIFECYCLE@@
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class TimeUtil {static String getTimeStr(long n){return "NEXT";}}
 static class Statistics {enum TimeType {DAY}enum DataType {COLLECTED}static int getRoundCollected(){return 7;}static int getData(TimeType t,DataType d){return 10;}}
 static class Notification {static class BigTextStyle{String text;BigTextStyle bigText(String s){text=s;return this;}}}
 static class Builder {String title,text,big;void setContentTitle(String s){title=s;}void setContentText(String s){text=s;}void setStyle(Notification.BigTextStyle s){big=s.text;}Builder build(){return this;}}
 static class Manager {void notify(int id,Builder b){}}
 static class NotificationUtil {
  static Builder builder=new Builder();static Manager mNotifyManager=new Manager();static final int NOTIFICATION_ID=99;
  static String titleText="Sesame-M",contentText="";static long nextExecTime,lastNoticeTime;
  @@NOTICE@@
 }
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static void flagToday(String k){flags.add(k);}static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int v){counts.put(k,v);}}
 static class AntForestRpcCall {static String reply="{}";static String exchangeBenefit(String spu,String sku){return reply;}}
 @@EXCHANGE@@
 public static void main(String[] args){ReloadCheck h=new ReloadCheck();
  assert h.runInit(true,null,false)&&h.executions==0&&h.delayed==300000&&h.init&&!h.initializing&&BaseModel.data==1;
  assert NotificationUtil.contentText.equals("等待执行  下次执行 NEXT")&&NotificationUtil.builder.title.contains("本轮7g")&&NotificationUtil.builder.big.contains("本日收取能量 10g");
  interval=0;h.delayed=0;assert h.runInit(true,null,false)&&h.delayed==FALLBACK_INTERVAL;
  assert h.initHandler(true)&&h.executions==1&&TaskLifecycle.isIdle();
  TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle();assert owner!=null;
  int data=BaseModel.data;h.initializing=true;assert !h.runInit(true,null,false)&&!h.initializing&&BaseModel.data==data;
  assert h.initHandler(true,owner)&&h.executions==2&&BaseModel.data==data;TaskLifecycle.thaw(owner);assert TaskLifecycle.isOpen()&&TaskLifecycle.isIdle();
  rejectInit=true;assert !h.runInit(true,null,false)&&BaseModel.data==data;rejectInit=false;
  throwInit=true;h.initializing=true;try{h.runInit(true,null,false);throw new AssertionError();}catch(IllegalStateException expected){}assert !h.initializing&&TaskLifecycle.isIdle();throwInit=false;
  AntForestRpcCall.reply="{errorMessage:'系统繁忙，请稍后再试。'}";
  for(int i=1;i<=VITALITY_EXCHANGE_RETRY_MAX;i++){assert !h.exchangeBenefit("spu","one");assert Status.flags.contains("forest::exchangeLimit::one")== (i==VITALITY_EXCHANGE_RETRY_MAX);}
  assert !h.exchangeBenefit("spu","two")&&!Status.flags.contains("forest::exchangeLimit::two");
  Status.flags.clear();Status.counts.clear();assert !h.exchangeBenefit("spu","one")&&!Status.flags.contains("forest::exchangeLimit::one");
  AntForestRpcCall.reply="{}";assert !h.exchangeBenefit("spu","other")&&!Status.counts.containsKey("forest::exchangeRetry::other");
  System.out.println("PASS config reload scheduling, idle notice, startup/switch execution, lifecycle guard and per-day/SKU busy retry cap");
 }
}
'''
hook = 'hook/ApplicationHook.java'
initialize = method(hook, 'private Boolean initializeHandler(')
schedule = initialize[initialize.index('            offline = false;'):initialize.index('        } catch (Throwable th)')]
reload_check = reload_check.replace('@@SCHEDULE@@', schedule)
reload_check = reload_check.replace('@@INIT@@', '\n'.join(method(hook,s) for s in ('private Boolean initHandler(Boolean force)', 'private Boolean initHandler(Boolean force, TaskLifecycle.Freeze owner)', 'private synchronized Boolean runInit(')))
life = (SOURCE/'data/task/TaskLifecycle.java').read_text(encoding='utf-8').split('\n',1)[1].replace('public final class TaskLifecycle', 'static final class TaskLifecycle')
reload_check = reload_check.replace('@@LIFECYCLE@@',life)
reload_check = reload_check.replace('@@NOTICE@@', '\n'.join(method('util/NotificationUtil.java',s) for s in ('private static String energyText(', 'private static String titleWithEnergy(', 'private static void applyText(', 'private static void sendText(', 'public static void updateIdleText(')))
reload_check = reload_check.replace('@@EXCHANGE@@',method('model/task/antForest/AntForestV2.java','private static Boolean exchangeBenefit('))

cards_check = r'''
import java.util.*; import org.json.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class CardLimitCheck {
    static String TAG="check";
    static class Field<T> { T value; Field(T v){value=v;} T getValue(){return value;} }
    static class Selection extends Field<Map<String,Integer>> {
        Selection(){super(new HashMap<>());} boolean contains(String g){return value.containsKey(g);} Integer get(String g){return value.get(g);}
    }
    Selection continuousUseCardOptions=new Selection(), vitality_ExchangeBenefitList=new Selection();
    Field<Boolean> smartDoubleCard=new Field<>(false), forestPropRefill=new Field<>(false);
    Field<Integer> robExpandCardReplaceRemainDays=new Field<>(0), robExpandCardForceReplaceExpireDays=new Field<>(0);
    static class Status {
        static String uid="A"; static int day=1;
        static Map<String,Integer> counts=new HashMap<>(); static Set<String> flags=new HashSet<>();
        static String key(String s){return uid+":"+day+":"+s;}
        static int getIntFlagToday(String s){return counts.getOrDefault(key(s),0);}
        static void setIntFlagToday(String s,int n){counts.put(key(s),n);}
        static boolean hasFlagToday(String s){return flags.contains(key(s));}
        static void flagToday(String s){flags.add(key(s));}
        static boolean canVitalityExchangeBenefitToday(String s,int n){return true;} // Counter failure must not create an infinite loop.
    }
    static class ForestExpiringProps {static boolean hasUnconfirmed(String g){return false;}}
    static class ForestPropSupport {static boolean hasUnconfirmedRefill(String g){return false;}}
    static class TimeUtil {static void sleep(long n){}}
    static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
    static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
    static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return j!=null&&"SUCCESS".equals(j.optString("resultCode"));}}
    static class Log {static int missing; static void forest(String s){} static void record(String s){missing++;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
    static class AntForestRpcCall {
        static int uses; static boolean fail, confirm;
        static String consumeProp(String g,String id,String type,boolean second){uses++;confirm=second;return "{resultCode:'"+(fail?"FAIL":"SUCCESS")+"'}";}
        static String consumeProp(String g,String id,String type){return consumeProp(g,id,type,false);}
    }
    int queries, exchanges; boolean absent, malformed, loaded=true; long active;
    void useSmartDoubleCard(){throw new AssertionError();} void usePreferredRobExpandCard(){throw new AssertionError();}
    JSONArray refillForestProp(String g){throw new AssertionError("quota must precede refill");}
    long continuousUseCardCheak(String g){queries++;return active;}
    JSONObject chooseContinuousLIMITTIMECard(String g){return absent?null:new JSONObject().put("holdsNum",20).put("propType","card").put("propIdList",malformed?"bad":new JSONArray().put("id"));}
    Map<String,JSONObject> skuInfo=new HashMap<>();
    boolean getAllSkuInfo(){return loaded;} void getSkuInfoBySpuId(String id){}
    boolean exchangeBenefit(String spu,String sku,String name){exchanges++;return true;}
    enum ItemStatus {REACH_LIMIT, NO_ENOUGH_POINT, NO_ENOUGH_STOCK; String nickName(){return name();}}
    @@METHODS@@
    static void reset(){Status.counts.clear();Status.flags.clear();Status.uid="A";Status.day=1;AntForestRpcCall.uses=0;AntForestRpcCall.fail=false;Log.missing=0;}
    public static void main(String[] args){
        reset(); CardLimitCheck f=new CardLimitCheck(); f.continuousUseCardOptions.value.put("doubleClick",1);
        f.continuousUseAndExchangeCard("doubleClick",null);f.continuousUseAndExchangeCard("doubleClick",null);
        assert AntForestRpcCall.uses==1&&Status.getIntFlagToday("continuousUseCard::count::doubleClick")==1;
        f.continuousUseCardOptions.value.put("stealthCard",1);f.continuousUseAndExchangeCard("stealthCard",null);
        assert AntForestRpcCall.uses==2 : "limits leaked between card types";
        Status.uid="B";f.continuousUseAndExchangeCard("doubleClick",null);Status.uid="A";Status.day++;
        f.continuousUseAndExchangeCard("doubleClick",null);assert AntForestRpcCall.uses==4 : "account/day rollover blocked new quota";
        reset();f=new CardLimitCheck();f.continuousUseCardOptions.value.put("shield",1);f.active=3600;
        f.continuousUseAndExchangeCard("shield",null);assert AntForestRpcCall.uses==1&&AntForestRpcCall.confirm : "M shield renewal was disabled";
        f.absent=true;f.forestPropRefill.value=true;f.continuousUseAndExchangeCard("shield",null);assert AntForestRpcCall.uses==1 : "quota checked after refill";
        reset();f=new CardLimitCheck();f.continuousUseCardOptions.value.put("doubleClick",1);AntForestRpcCall.fail=true;
        f.continuousUseAndExchangeCard("doubleClick",null);assert Status.counts.isEmpty() : "failed consumption counted as success";
        reset();f=new CardLimitCheck();f.continuousUseCardOptions.value.put("doubleClick",0);
        f.continuousUseAndExchangeCard("doubleClick",null);assert AntForestRpcCall.uses==10 : "unlimited legacy option or loop guard changed";
        reset();f=new CardLimitCheck();f.continuousUseCardOptions.value.put("doubleClick",1);f.malformed=true;
        f.continuousUseAndExchangeCard("doubleClick",null);assert AntForestRpcCall.uses==0 : "invalid ID array consumed a card";
        reset();f=new CardLimitCheck();f.vitality_ExchangeBenefitList.value.put("sku",4);f.skuInfo.put("sku",new JSONObject().put("spuId","spu"));
        f.vitalityExchangeBenefit();assert f.exchanges==4 : "exchange loop did not honor hard limit";
        assert !f.exchangeBenefit("missing")&&!Status.hasFlagToday("forest::exchangeSkuMissing::missing") : "partial/failed catalogue poisoned SKU";
        Status.flagToday("forest::vitalitySkuList");assert !f.exchangeBenefit("missing");assert !f.exchangeBenefit("missing")&&Log.missing==1;
        Status.day++;Status.flagToday("forest::vitalitySkuList");assert !f.exchangeBenefit("missing")&&Log.missing==2 : "missing SKU remained blocked on next day";
        System.out.println("PASS continuous-card per-type/account/day limits, successful consumption, M renewal, malformed inventory, bounded exchange and missing SKU recovery");
    }
}
'''
cards_check=cards_check.replace('@@METHODS@@','\n'.join(method('model/task/antForest/AntForestV2.java',s) for s in (
    'private void continuousUseAndExchangeCard(', 'private boolean canUseContinuousCard(', 'private void recordContinuousCardUse(',
    'private void vitalityExchangeBenefit(', 'private Boolean exchangeBenefit(String skuId)')))

with tempfile.TemporaryDirectory(prefix="sesame-merge-") as tmp:
    for name, source in (("MergeCheck", code), ("FlowCheck", flows), ("UpstreamFixCheck", fixes), ("ToastCheck", toast), ("PatrolSyncCheck", patrol), ("LoveWaterRequestCheck", love_request), ("SyncProbeCheck", sync_probe), ("ReloadCheck",reload_check), ("CardLimitCheck",cards_check)):
        java = Path(tmp) / (name + ".java")
        java.write_text(source, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(json_jar), "-d", tmp, str(java), str(SOURCE/'util/TaskCancelledException.java')], check=True)
        subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(json_jar), name], check=True)
