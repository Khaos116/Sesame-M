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
    "private boolean isGoldTicketKnownAutoTask(", "private int countGoldTicketPendingAutoTasks(",
):
    code += method(member, signature) + "\n"
code += method("model/task/antForest/AntForestV2.java", "private static int teamState(") + "\n"
code += method("model/base/TaskAlternative.java", "public static boolean isTransactionTask(") + "\n"
code += r'''
static final String[] TRANSACTION_KEYWORDS = {"xiadan", "zhifu", "pay", "goumai", "jiaofei", "huankuan", "chongzhi", "taobao", "babafarm_tb", "70000"};
static class MyUtils {
    static JSONObject newJSONObject() { return new JSONObject(); }
    static JSONObject newJSONObject(String raw) { try { return raw == null ? new JSONObject() : new JSONObject(raw); } catch (JSONException e) { return new JSONObject(); } }
}
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
    assert !isTransactionTask("2060170000359285") && isTransactionTask("x_70000_y");
    assert Log.errors == 0;
    System.out.println("PASS: missing/invalid sign-in and consume data, task structure, decimal locale, tri-state teams and transaction guard");
}
}
'''.replace("@@SUBMIT@@", method("model/task/antMember/AntMemberRpcCall.java", "public static String submitConsume("))

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
        static void printStackTrace(String tag, Throwable t) { throw new AssertionError(t); }
    }
    static class TimeUtil { static void sleep(long ms) {} }
    static class StringUtil { static boolean isEmpty(String s) { return s == null || s.isEmpty(); } }
    static class ApplicationHook { static boolean offline; static boolean isOffline() { return offline; } }
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
        static String collectCreditFeedback(String id) { throw new AssertionError("unexpected reward"); }
        static String collectAllCreditFeedback() { throw new AssertionError("unexpected reward"); }
        static String joinSesameTaskNew(String id) { calls.add("join"); return join; }
        static String feedBackSesameTaskNew(String id) { calls.add("feedback"); return ok().toString(); }
        static String finishSesameTask(String id) { calls.add("push:" + id); if(losePushReply) throw new IllegalStateException("lost push reply"); return ok().toString(); }
        static String queryLastOperateTask() { calls.add("last"); if(cancelLast) throw new TaskCancelledException(); return last; }
    }
    static JSONObject task(int done, int need) {
        return new JSONObject().put("templateId", "T").put("title", "task").put("completedNum", done).put("needCompleteNum", need);
    }
    static String tasks(JSONArray list) { return ok().put("data", new JSONObject().put("toCompleteVOS", list)).toString(); }
    static class AntFarm { static boolean bindPigeonFeedback(JSONArray items) { return true; } }
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
        static List<String> pages = new ArrayList<>(); static List<Boolean> switches = new ArrayList<>();
        static String itemList(String label, int offset) {
            pages.add(label + ":" + offset);
            JSONObject item = new JSONObject().put("spuId", "P").put("skuModelList", new JSONArray()
                .put(new JSONObject().put("skuId", label + offset).put("skuName", "name")).put(1).put(new JSONObject()));
            JSONObject data = new JSONObject().put("itemInfoVOList", new JSONArray().put(1).put(brokenItems ? new JSONObject() : item))
                .put("hasMore", forever || offset == 0);
            return new JSONObject().put("success", true).put("resData", data).toString();
        }
        static String queryHomePage() { return ok().put("nextAction", "Cultivate")
            .put("teamHomeResult", new JSONObject().put("teamBaseInfo", new JSONObject().put("teamId", "team")))
            .put("userBaseInfo", new JSONObject().put("currentEnergy", 100)).toString(); }
        static String queryMiscInfo(String type, String id) { return ok().put("combineHandlerVOMap",
            new JSONObject().put("teamCanWaterCount", new JSONObject().put("waterCount", 100))).toString(); }
        static String teamWater(String id, int amount) { waters++; if (cancelled) throw new TaskCancelledException(); return water; }
        static String loveteamWater(String id, int amount) { waters++; if (cancelled) throw new TaskCancelledException(); return water; }
        static String loveteamHome() { return ok().put("userInfo", new JSONObject().put("teamId", "team")).toString(); }
        static String updateUserConfiginTeam(boolean inTeam) { switches.add(inTeam); return ok().toString(); }
    }
    static class Forest {
        static final String[] VITALITY_LABEL_TYPES = {"", "SC_ASSETS", "SKIN", "JEWELRY", "OTHER"};
        static final int VITALITY_ITEM_MAX_PAGES = 10;
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
        AntForestRpcCall.pages.clear(); AntForestRpcCall.forever = true; f.getAllSkuInfo();
        assert AntForestRpcCall.pages.size() == 50;
        AntForestRpcCall.brokenItems = true; assert !f.getAllSkuInfo() : "old cache cannot confirm a fresh fetch";
        f = new Forest(); assert !f.getAllSkuInfo() && f.skuInfo.isEmpty();
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
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        assert GameTask.matchTaskType("GAME_DONE_SLJYD") == GameTask.Forest_sljyd;
        assert GameTask.matchTaskType("game_done_slxcc_xs_3") == GameTask.Forest_slxcc;
        assert GameTask.matchTaskType("GAME_DONE_DDPLY") == GameTask.Farm_ddply;
        assert GameTask.matchTaskType("GAME_DONE_UNKNOWN") == null && GameTask.matchTaskType(null) == null;
        Mall mall=new Mall(); AntFarmRpcCall.detail=new JSONObject().put("success",true).put("mallItemDetail",
            new JSONObject().put("mallSubItemDetailList",new JSONArray().put(new JSONObject().put("skuId","sku").put("skuName","name")))).toString();
        assert mall.getAllSkuInfo() && mall.getAllSkuInfo() && mall.skuInfo.size()==1;
        AntFarmRpcCall.detail="broken"; assert !mall.getAllSkuInfo();
        mall.list=null; assert !mall.getAllSkuInfo();
        Set<String> pending=new HashSet<>();
        for (String bad : new String[]{"broken","{}","{\"data\":{}}","{\"data\":{\"taskModuleList\":[{}]}}",
                "{\"data\":{\"taskModuleList\":[{\"taskList\":[1]}]}}"}) assert !Member.collectNotDoneIds(bad,pending);
        assert Member.collectNotDoneIds("{\"data\":{\"taskModuleList\":[]}}",pending) && pending.isEmpty();
        assert Member.collectNotDoneIds("{\"data\":{\"taskModuleList\":[{\"taskList\":[{\"taskId\":\"T\",\"taskStatus\":\"NOT_DONE\"}]}]}}",pending) && pending.contains("T");
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
    ("@@MEMBER@@", member, ("private void collectSesame(", "private boolean reportSesameTask(", "private String lastOperateRecordId(", "private String activeSesameRecordId(", "public void gameCenterTaskPrize(", "private static boolean collectNotDoneIds(", "private static boolean collectNotDoneFromArray(")),
    ("@@FOREST@@", "model/task/antForest/AntForestV2.java", ("private boolean getAllSkuInfo(", "private static JSONArray optItemInfoVOList(",
        "private static boolean hasMore(", "private boolean getSkuInfoByItemInfoVO(", "private void teamCooperateWater(",
        "private static JSONObject queryTeamHomePage(", "private static String getTeamId(", "private static int getTeamCanWaterCount(",
        "private static boolean updateUserConfiginTeam(", "private static int teamState(", "private static void loveteam(",
        "private static String getLoveteamName(", "private static boolean hasWaterResult(", "private static void loveteamWater(")),
    ("@@MALL@@", "model/task/antFarm/AntFarm.java", ("private boolean getAllSkuInfo(", "private boolean getSkuInfoByItemInfoVO(")),
    ("@@GAME_METHODS@@", "model/task/antGame/GameTask.java", ("GameTask(String title,", "public static GameTask matchTaskType(")),
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
 static class Status {static final String TAG="check";static final Status INSTANCE=new Status();static boolean saveFailureNotified;static int unloads;long saveTime;Set<String> flags=new HashSet<>();static void unload(){unloads++;INSTANCE.flags.clear();}static void ensureLoadedForCurrentUid(){}
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

with tempfile.TemporaryDirectory(prefix="sesame-merge-") as tmp:
    for name, source in (("MergeCheck", code), ("FlowCheck", flows), ("UpstreamFixCheck", fixes)):
        java = Path(tmp) / (name + ".java")
        java.write_text(source, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(json_jar), "-d", tmp, str(java)], check=True)
        subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(json_jar), name], check=True)
