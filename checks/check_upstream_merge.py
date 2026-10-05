"""Compile merged production methods: malformed gold-ticket data, locale, team state and transaction guard."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
json_jar = next((cache / "org.json/json").glob("*/*/json-*.jar"))
member = "model/task/antMember/AntMember.java"
code = "import org.json.*; import java.util.*;\npublic class MergeCheck {\n"
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
static final String[] TRANSACTION_BIZ_KEYWORDS = {"xiadan", "zhifu", "pay", "goumai", "jiaofei", "huankuan", "chongzhi", "taobao", "babafarm_tb", "70000"};
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
    assert Log.errors == 0;
    System.out.println("PASS: missing/invalid sign-in and consume data, task structure, decimal locale, tri-state teams and transaction guard");
}
}
'''.replace("@@SUBMIT@@", method("model/task/antMember/AntMemberRpcCall.java", "public static String submitConsume("))

with tempfile.TemporaryDirectory(prefix="sesame-merge-") as tmp:
    java = Path(tmp) / "MergeCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(json_jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(json_jar), "MergeCheck"], check=True)
