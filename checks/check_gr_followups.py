"""Exercise production nickname and gift-loop methods with isolated storage/RPC boundaries."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
json_jar = next(cache.glob("*/*/json-*.jar"))
code = r'''
import java.util.*;
import org.json.*;
public class FollowupCheck {
    static class TextUtils { static boolean isEmpty(String s) { return s == null || s.isEmpty(); } }
    record UserEntity(String getUserId, String getNickName) { }
    static class SharedPreferences {
        Map<String,String> values = new HashMap<>(); int writes;
        String getString(String k, String def) { return values.getOrDefault(k, def); }
        SharedPreferences edit() { return this; }
        SharedPreferences putString(String k, String v) { values.put(k,v); writes++; return this; }
        void apply() { }
    }
    static class UserIdMap {
        static Map<String,UserEntity> userMap = new HashMap<>();
        static UserEntity get(String uid) { return userMap.get(uid); }
        static String getCurrentUid() { return "self"; }
        static String getMaskName(String uid) { return uid; }
        @@ADD@@
    }
    static class MyUtils {
        static SharedPreferences sp = new SharedPreferences();
        static SharedPreferences getMySp() { return sp; }
        static JSONObject newJSONObject(String s) { return new JSONObject(s); }
        @@CACHE@@
        @@NAME@@
    }
    static class RpcRequestGuard { @@NONFRIEND@@ }
    static class Log {
        static int errors;
        static void record(String s) { }
        static void farm(String s) { }
        static void i(String s, String msg) { errors++; }
        static void printStackTrace(String tag, Throwable t) { throw new AssertionError(t); }
        static void err(String tag, String msg, Throwable t) { errors++; }
    }
    static class MessageUtil {
        static boolean checkMemo(String tag, JSONObject jo) {
            if ("SUCCESS".equals(jo.optString("memo"))) return true;
            Log.errors++; return false;
        }
    }
    static class TimeUtil { static void sleep(long ms) { } }
    static class Status {
        static Set<String> visited = new HashSet<>();
        static boolean canVisitFriendToday(String uid, int limit) { return !visited.contains(uid); }
        static void visitFriendToday(String uid) { visited.add(uid); }
        static void flagToday(String key) { }
    }
    static class AntFarmRpcCall {
        static List<String> entered = new ArrayList<>(), gifts = new ArrayList<>();
        static String enterFarm(String uid) {
            entered.add(uid);
            if (uid.equals("gone")) return "{\"success\":false,\"memo\":\"非好友\",\"resultCode\":\"302\"}";
            return "{\"memo\":\"SUCCESS\",\"farmVO\":{\"foodStock\":100,\"subFarmVO\":{\"visitedToday\":false,\"farmId\":\"good\"}}}";
        }
        static String visitFriend(String farm) {
            gifts.add(farm);
            return "{\"memo\":\"SUCCESS\",\"foodStock\":90,\"giveFoodNum\":10,\"isReachLimit\":true}";
        }
    }
    static class Farm {
        String TAG = "Farm"; int foodStock;
        static class Field { Map<String,Integer> map = new LinkedHashMap<>(); Map<String,Integer> getValue() { return map; } }
        Field visitFriendList = new Field();
        @@VISITS@@
        @@VISIT@@
    }
    public static void main(String[] args) {
        UserIdMap.add(new UserEntity("A", "小明"));
        assert MyUtils.recordUserName("A").equals(":小明");
        int writes = MyUtils.sp.writes;
        assert MyUtils.recordUserName("A").equals(":小明") && MyUtils.sp.writes == writes;
        UserIdMap.userMap.clear();
        assert MyUtils.recordUserName("A").equals(":小明"); // before profile reload
        UserIdMap.add(new UserEntity("A", "新昵称"));
        UserIdMap.add(new UserEntity("B", "小红"));
        assert MyUtils.recordUserName("A").equals(":新昵称");
        assert MyUtils.recordUserName("B").equals(":小红");
        UserIdMap.add(new UserEntity("A", ""));
        assert MyUtils.recordUserName("A").equals(":新昵称");
        assert MyUtils.recordUserName("unknown").equals(":unknown");
        assert MyUtils.recordUserName(null).isEmpty();
        MyUtils.sp = null;
        assert MyUtils.recordUserName("B").equals(":小红");
        assert MyUtils.recordUserName("unknown").equals(":unknown");
        Farm farm = new Farm();
        farm.visitFriendList.map.put("gone", 1);
        farm.visitFriendList.map.put("good", 1);
        farm.visitFriend();
        assert AntFarmRpcCall.entered.equals(List.of("gone", "good"));
        assert AntFarmRpcCall.gifts.equals(List.of("good"));
        assert Log.errors == 0;
        System.out.println("PASS nickname refresh/cache/account fallback and silent non-friend skip followed by next gift");
    }
}
'''
for token, path, signature in (
    ("@@ADD@@", "util/idMap/UserIdMap.java", "    public synchronized static void add("),
    ("@@CACHE@@", "util/MyUtils.java", "    public static void cacheUserName("),
    ("@@NAME@@", "util/MyUtils.java", "    public static String recordUserName("),
    ("@@NONFRIEND@@", "rpc/intervallimit/RpcRequestGuard.java", "    public static boolean isNonFriend("),
    ("@@VISITS@@", "model/task/antFarm/AntFarm.java", "    private void visitFriend()"),
    ("@@VISIT@@", "model/task/antFarm/AntFarm.java", "    private void visitFriend(String"),
):
    code = code.replace(token, method(path, signature).replace("@Nullable ", ""))
with tempfile.TemporaryDirectory(prefix="sesame-followup-") as tmp:
    java = Path(tmp) / "FollowupCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(json_jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(json_jar), "FollowupCheck"], check=True)

farm = (SOURCE / "model/task/antFarm/AntFarm.java").read_text(encoding="utf-8")
assert "AntFarmRpcCall.sleep(" not in farm and "AntFarmRpcCall.familySleep(" not in farm
assert "this::animalSleepNow" not in farm and '"AS|"' not in farm
assert "this::animalWakeUpNow" in farm
user_map = (SOURCE / "util/idMap/UserIdMap.java").read_text(encoding="utf-8")
assert user_map.count("add(dto.toEntity())") == 2
print("PASS no automatic sleep RPC/scheduling; wake-up and both cached-profile loading paths retained")
