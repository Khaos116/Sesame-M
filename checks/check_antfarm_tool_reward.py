"""Exercise the production AntFarm reward-claim loops with isolated RPC boundaries.

Scope: receiveToolTaskReward (tool-list items -> receiveToolTaskReward claims),
receiveFarmTaskAward (farm-task items -> claim, feed vs non-feed branching) and
the run() wiring that re-queries tool and farm rewards at end of round. Item shapes are
the real structures production parses; farm cuisine rewards use CUISINE.
Claim gating: counts 1-3 bypass the
feed-stock gate regardless of awardType (feed comes in multiples of 30);
all other counts keep the type-based gate (only ALLPURPOSE is gated).
Small-amount claims also skip the post-claim stock update (pieces, not grams).
"""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jars = list(cache.glob("*/*/json-*.jar"))
if not jars:
    raise SystemExit("org.json jar missing in Gradle cache; run the project's Gradle build first")
json_jar = sorted(jars)[-1]
code = r'''
import java.util.*;
import org.json.*;
class ApplicationHook {
    static String args;
    static String requestString(String method, String requestArgs) {
        assert method.equals("com.alipay.antfarm.receiveFarmTaskAward");
        args = requestArgs;
        return "{}";
    }
}
class ProductionFarmRpc {
    static final String VERSION = "test";
    @@FARMRPC@@
}
class Status {
    static boolean allDone;
    static boolean hasFlagToday(String flag) { return allDone; }
    static void flagToday(String flag) { allDone = true; }
}
class TimeUtil {
    static void sleep(int ms) {}
}
public class ToolRewardCheck {
    static final String CUISINE_AWARD_TYPE = "CUISINE";
    static class MyUtils {
        static JSONObject newJSONObject(String s) {
            try {
                return new JSONObject(s);
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }
    static class Log {
        static List<String> records = new ArrayList<>(), farms = new ArrayList<>();
        static int errors;
        static void record(String s) { records.add(s); }
        static void farm(String s) { farms.add(s); }
        static void err(String tag, String msg, Throwable t) { errors++; }
    }
    static class MessageUtil {
        static List<String> blacklists = new ArrayList<>();
        static boolean checkMemo(String tag, JSONObject jo) {
            return "SUCCESS".equals(jo.optString("memo"));
        }
        static boolean isServerBusy(JSONObject jo) { return false; }
        static void checkResultCodeAndMarkTaskBlackList(String list, String title, JSONObject jo) {
            blacklists.add(list + ":" + title);
        }
    }
    static class AntFarmRpcCall {
        static String listResponse = "{}";
        static String failType = null;
        static List<String> received = new ArrayList<>();
        static List<String> farmAwards = new ArrayList<>();
        static String listToolTaskDetails() { return listResponse; }
        static String receiveToolTaskReward(String rewardType, int rewardCount, String taskType) {
            received.add(rewardType + ":" + rewardCount + ":" + taskType);
            if (rewardType.equals(failType)) return "{\"memo\":\"FAIL\",\"resultCode\":\"400\"}";
            return "{\"memo\":\"SUCCESS\"}";
        }
        static String receiveFarmTaskAward(String taskId, String awardType) {
            farmAwards.add(taskId + ":" + awardType);
            return "{\"memo\":\"SUCCESS\"}";
        }
    }
    static class Farm {
        static final String FLAG_FARM_TASK_ALL_DONE = "antFarm::farmTaskAllDone";
        static final int MAX_FARM_TASK_ROUNDS = 20;
        String TAG = "Farm";
        String ownerFarmId = "owner";
        int foodStock, foodStockLimit, unReceiveTaskAward;
        int farmTaskListCalls;
        boolean farmTaskAwardBusy;
        Set<String> farmTaskAttempted;
        @@TOOLTYPE@@
        @@TASKSTATUS@@
        @@FARMTOOL@@
        FarmTool[] farmTools;
        private Boolean useFarmTool(String targetFarmId, ToolType toolType) { return true; }
        @@PENDING@@
        @@ADD2FOOD@@
        @@TOOLMETHOD@@
        @@FARMMETHOD@@
        int[] listFarmTask(TaskStatus mode) { farmTaskListCalls++; return new int[]{0, 0}; }
        @@FARMROUNDS@@
    }
    static String item(String status, String taskType, String bizInfo) {
        return "{\"taskStatus\":\"" + status + "\",\"taskType\":\"" + taskType + "\",\"bizInfo\":" + JSONObject.quote(bizInfo) + "}";
    }
    static String biz(String awardType, Integer awardCount, String title) {
        return "{\"awardType\":\"" + awardType + "\""
            + (awardCount == null ? "" : ",\"awardCount\":" + awardCount)
            + ",\"taskTitle\":\"" + title + "\"}";
    }
    static JSONObject farmTask(String taskId, String title, String awardType, int awardCount) {
        JSONObject task = new JSONObject();
        task.put("taskId", taskId);
        task.put("title", title);
        task.put("awardType", awardType);
        task.put("awardCount", awardCount);
        return task;
    }
    static void reset() {
        AntFarmRpcCall.received.clear();
        AntFarmRpcCall.farmAwards.clear();
        AntFarmRpcCall.failType = null;
        MessageUtil.blacklists.clear();
        Log.records.clear();
        Log.farms.clear();
        Log.errors = 0;
    }
    static boolean logged(String part) {
        for (String s : Log.records) if (s.contains(part)) return true;
        return false;
    }
    public static void main(String[] args) {
        ProductionFarmRpc.receiveFarmTaskAward("CUISINE_TASK", "CUISINE");
        JSONObject request = new JSONArray(ApplicationHook.args).optJSONObject(0);
        assert request != null && request.optString("taskId").equals("CUISINE_TASK")
            && request.optString("awardType").equals("CUISINE");
        ProductionFarmRpc.receiveFarmTaskAward("FEED_TASK", "ALLPURPOSE");
        request = new JSONArray(ApplicationHook.args).optJSONObject(0);
        assert request != null && request.optString("awardType").equals("ALLPURPOSE");
        ProductionFarmRpc.receiveFarmTaskAward("TASK\"1", "CUISINE");
        request = new JSONArray(ApplicationHook.args).optJSONObject(0);
        assert request != null && request.optString("taskId").equals("TASK\"1");
        System.out.println("PASS farm award RPC sends the server awardType");
        Farm farm = new Farm();
        Status.allDone = true;
        farm.runFarmTaskRounds();
        assert farm.farmTaskListCalls == 1 : "late rewards must be rechecked after an earlier empty list";
        System.out.println("PASS earlier all-done flag does not hide later farm rewards");
        // farmTools stays null: listFarmTool RPC outage must not NPE the claim loop.
        AntFarmRpcCall.listResponse = "{\"memo\":\"SUCCESS\",\"list\":["
            + item("FINISHED", "DAILY_TOOL", biz(CUISINE_AWARD_TYPE, 3, "新类型道具")) + ","
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", 1, "每日道具")) + ","
            + item("FINISHED", "DAILY_TOOL", biz("", 1, "类型缺失")) + ","
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", 0, "数量为零")) + ","
            + item("FINISHED", "", biz("STEALTOOL", 1, "任务类型缺失")) + ","
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", null, "数量字段缺失")) + ","
            + item("TODO", "DAILY_TOOL", biz("STEALTOOL", 1, "未完成")) + "]}";
        farm.receiveToolTaskReward();
        assert AntFarmRpcCall.received.equals(List.of(CUISINE_AWARD_TYPE + ":3:DAILY_TOOL", "STEALTOOL:1:DAILY_TOOL"))
            : AntFarmRpcCall.received;
        assert logged("未知类型[" + CUISINE_AWARD_TYPE + "]");
        assert logged("跳过奖励类型缺失");
        assert logged("数量或任务类型缺失");
        assert Log.errors == 0;
        System.out.println("PASS unknown awardType claimed directly without blocking later items; missing fields skipped with no RPC");
        // Full inventory still defers the claim instead of sending it.
        reset();
        Farm.FarmTool full = new Farm.FarmTool();
        full.toolType = Farm.ToolType.STEALTOOL;
        full.toolCount = 5;
        full.toolHoldLimit = 5;
        farm.farmTools = new Farm.FarmTool[]{full};
        AntFarmRpcCall.listResponse = "{\"memo\":\"SUCCESS\",\"list\":["
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", 1, "每日道具")) + "]}";
        farm.receiveToolTaskReward();
        assert AntFarmRpcCall.received.isEmpty();
        assert logged("已满，暂不领取");
        assert Log.errors == 0;
        System.out.println("PASS full tool inventory defers the claim");
        // A failed claim is logged with context and does not stop later items.
        reset();
        farm.farmTools = null;
        AntFarmRpcCall.failType = "SHARETOOL";
        AntFarmRpcCall.listResponse = "{\"memo\":\"SUCCESS\",\"list\":["
            + item("FINISHED", "DAILY_TOOL", biz("SHARETOOL", 1, "救济卡")) + ","
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", 1, "每日道具")) + "]}";
        farm.receiveToolTaskReward();
        assert AntFarmRpcCall.received.equals(List.of("SHARETOOL:1:DAILY_TOOL", "STEALTOOL:1:DAILY_TOOL"));
        assert logged("失败[救济卡]") && logged("#类型[SHARETOOL]") && logged("memo[FAIL]");
        assert Log.farms.size() == 1 && Log.farms.get(0).contains("每日道具");
        assert Log.errors == 0;
        System.out.println("PASS failed claim keeps title/type/count/memo context and later items still claimed");
        // Re-query after completion picks the reward up in the same round.
        reset();
        AntFarmRpcCall.listResponse = "{\"memo\":\"SUCCESS\",\"list\":["
            + item("TODO", "DAILY_TOOL", biz("STEALTOOL", 2, "本轮新完成")) + "]}";
        farm.receiveToolTaskReward();
        assert AntFarmRpcCall.received.isEmpty();
        AntFarmRpcCall.listResponse = "{\"memo\":\"SUCCESS\",\"list\":["
            + item("FINISHED", "DAILY_TOOL", biz("STEALTOOL", 2, "本轮新完成")) + "]}";
        farm.receiveToolTaskReward();
        assert AntFarmRpcCall.received.equals(List.of("STEALTOOL:2:DAILY_TOOL"));
        assert Log.errors == 0;
        System.out.println("PASS re-query claims rewards finished during the round");
        // Non-feed awards (美食 probe, counts 1/2/3) claim even with a full trough:
        // non-ALLPURPOSE types bypass the stock gate at any count.
        reset();
        farm.foodStock = 1000;
        farm.foodStockLimit = 1000;
        farm.unReceiveTaskAward = 0;
        for (int n = 1; n <= 3; n++) {
            assert farm.receiveFarmTaskAward(farmTask("MEISHI_" + n, "美食任务" + n, CUISINE_AWARD_TYPE, n));
        }
        assert AntFarmRpcCall.farmAwards.equals(List.of("MEISHI_1:CUISINE", "MEISHI_2:CUISINE", "MEISHI_3:CUISINE"));
        assert farm.unReceiveTaskAward == 0;
        assert farm.foodStock == 1000;
        assert Log.farms.size() == 3;
        for (int n = 1; n <= 3; n++) {
            assert Log.farms.get(n - 1).contains("爱心美食×" + n + "个");
        }
        assert Log.errors == 0;
        System.out.println("PASS non-feed awards with counts 1/2/3 claim with a full trough and leave feed stock untouched");
        reset();
        assert farm.receiveFarmTaskAward(farmTask("CUISINE_UNKNOWN", "美食数量缺失", "CUISINE", 0));
        assert Log.farms.size() == 1 && Log.farms.get(0).contains("爱心美食（数量未返回）");
        // Small-amount ALLPURPOSE (counts 1/2/3, legacy piece rewards) bypasses
        // the full-trough gate too: feed rewards come in multiples of 30, so 1-3
        // can never be feed. Stock stays capped at the limit.
        reset();
        farm.foodStock = 1000;
        farm.foodStockLimit = 1000;
        farm.unReceiveTaskAward = 0;
        for (int n = 1; n <= 3; n++) {
            assert farm.receiveFarmTaskAward(farmTask("SMALL_" + n, "小额任务" + n, "ALLPURPOSE", n));
        }
        assert AntFarmRpcCall.farmAwards.equals(List.of("SMALL_1:ALLPURPOSE", "SMALL_2:ALLPURPOSE", "SMALL_3:ALLPURPOSE"));
        assert farm.unReceiveTaskAward == 0;
        assert farm.foodStock == 1000;
        assert logged("直接领取");
        assert Log.errors == 0;
        System.out.println("PASS ALLPURPOSE counts 1/2/3 bypass the full-trough gate and leave stock capped");
        // Stock-accounting half of the small-amount rule: at 970g (nothing to clamp)
        // a 1-piece ALLPURPOSE claim must leave the stock at 970g and log pieces,
        // and a following 30g feed claim must still succeed.
        reset();
        farm.foodStock = 970;
        farm.foodStockLimit = 1000;
        farm.unReceiveTaskAward = 0;
        assert farm.receiveFarmTaskAward(farmTask("SMALL_970", "小额任务970", "ALLPURPOSE", 1));
        assert farm.foodStock == 970;
        assert farm.unReceiveTaskAward == 0;
        assert Log.farms.size() == 1 && Log.farms.get(0).contains("1个");
        assert farm.receiveFarmTaskAward(farmTask("FEED_AFTER", "后续饲料", "ALLPURPOSE", 30));
        assert AntFarmRpcCall.farmAwards.equals(List.of("SMALL_970:ALLPURPOSE", "FEED_AFTER:ALLPURPOSE"));
        assert farm.foodStock == 1000;
        assert farm.unReceiveTaskAward == 0;
        assert Log.errors == 0;
        System.out.println("PASS small-amount claim leaves 970g stock untouched so a following 30g feed still claims");
        // Feed behavior unchanged: over-limit still deferred, fitting claims still counted.
        reset();
        JSONObject feed = farmTask("FEED_1", "饲料任务", "ALLPURPOSE", 30);
        assert !farm.receiveFarmTaskAward(feed);
        assert AntFarmRpcCall.farmAwards.isEmpty();
        assert farm.unReceiveTaskAward == 1;
        farm.foodStock = 100;
        farm.unReceiveTaskAward = 0;
        assert farm.receiveFarmTaskAward(feed);
        assert AntFarmRpcCall.farmAwards.equals(List.of("FEED_1:ALLPURPOSE"));
        assert farm.foodStock == 130;
        assert farm.unReceiveTaskAward == 0;
        assert Log.errors == 0;
        System.out.println("PASS feed over-limit deferred and fitting feed claimed with stock accounting");
        // Multiples of 30 above one unit (60g) keep the old gate: still deferred when full.
        reset();
        farm.foodStock = 1000;
        farm.foodStockLimit = 1000;
        farm.unReceiveTaskAward = 0;
        assert !farm.receiveFarmTaskAward(farmTask("FEED_60", "饲料任务60", "ALLPURPOSE", 60));
        assert AntFarmRpcCall.farmAwards.isEmpty();
        assert farm.unReceiveTaskAward == 1;
        assert Log.errors == 0;
        System.out.println("PASS feed 60g over-limit still deferred");
    }
}
'''
for token, path, signature in (
    ("@@FARMRPC@@", "model/task/antFarm/AntFarmRpcCall.java", "    public static String receiveFarmTaskAward(String taskId, String awardType)"),
    ("@@TOOLMETHOD@@", "model/task/antFarm/AntFarm.java", "    private void receiveToolTaskReward()"),
    ("@@FARMMETHOD@@", "model/task/antFarm/AntFarm.java", "    private Boolean receiveFarmTaskAward(JSONObject task)"),
    ("@@FARMROUNDS@@", "model/task/antFarm/AntFarm.java", "    private void runFarmTaskRounds()"),
    ("@@PENDING@@", "model/task/antFarm/AntFarm.java", "    private static int pendingAward(JSONObject task)"),
    ("@@ADD2FOOD@@", "model/task/antFarm/AntFarm.java", "    private void add2FoodStock(int i)"),
    ("@@TOOLTYPE@@", "model/task/antFarm/AntFarm.java", "    public enum ToolType {"),
    ("@@TASKSTATUS@@", "model/task/antFarm/AntFarm.java", "    public enum TaskStatus {"),
    ("@@FARMTOOL@@", "model/task/antFarm/AntFarm.java", "    private static class FarmTool {"),
):
    code = code.replace(token, method(path, signature))
with tempfile.TemporaryDirectory(prefix="sesame-tool-reward-") as tmp:
    java = Path(tmp) / "ToolRewardCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(json_jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(json_jar), "ToolRewardCheck"], check=True)

farm = (SOURCE / "model/task/antFarm/AntFarm.java").read_text(encoding="utf-8")
assert farm.index('step("饲料任务"') < farm.index('step("道具奖励补领"')
assert farm.index('step("道具奖励补领"') < farm.index('step("庄园奖励补领"')
print("PASS end-of-round tool and farm award rechecks run after the feed tasks")
