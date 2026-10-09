"""Compile actual stall completion/list/award methods; verify unsupported TODO skips and completed rewards.

--baseline <revision> runs the same assertions against a previous commit to reproduce the unwanted RPC.
No Android device or real RPC is used.
"""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from task_policy_fixture import POLICY, award
from run import SOURCE, ROOT, method

for java in SOURCE.rglob("*.java"):
    text = java.read_text(encoding="utf-8")
    assert not re.search(r"Credit2101|credit2101|信用2101", text), java
other = (SOURCE / "model/task/other/OtherTask.java").read_text(encoding="utf-8")
for java in SOURCE.rglob("*.java"):
    text = java.read_text(encoding="utf-8")
    for removed in ("DailyCash", "WealthDayRewards", "WealthDayCashProtocol", "SIGN_TASK_CENTER",
                    "com.alipay.wealthdaybff.open2025.drawCash"):
        assert removed not in text, (java, removed)
member = (SOURCE / "model/task/antMember/AntMember.java").read_text(encoding="utf-8")
for retained in ("YebVouchers.run(", "enableGoldTicketConsume"):
    assert retained in member, retained
assert "triggerYebMoneyTree" in (SOURCE / "model/task/antOrchard/AntOrchardRpcCall.java").read_text(encoding="utf-8")
assert "clazzList.add(OtherTask.class)" in (SOURCE / "model/base/ModelOrder.java").read_text(encoding="utf-8")
for key in ("haojiaWuyou", "haojiaCoinSign", "shenQuanSign", "shenQuanTasks", "shenQuanDraw"):
    assert '"' + key + '"' in other, key

path = "model/task/antStall/AntStall.java"
fish = (SOURCE / "model/task/fish/FishConfig.java").read_text(encoding="utf-8")
for key in ("enableFishAuto", "enableFishTaskAuto"):
    assert re.search(r'BooleanModelField\("' + key + r'",\s*"[^"]+",\s*false\)', fish), key
assert (SOURCE / "model/task/fish/FishTask.java").is_file()
assert "clazzList.add(FishTask.class)" in (SOURCE / "model/base/ModelOrder.java").read_text(encoding="utf-8")
finish = method(path, "    private static Boolean finishTask(")
if "--baseline" in sys.argv:
    index = sys.argv.index("--baseline") + 1
    if index >= len(sys.argv):
        raise SystemExit("Usage: --baseline <revision>")
    previous = subprocess.check_output(
        ["git", "show", sys.argv[index] + ":app/src/main/java/io/github/aw1y2z/sesame/" + path],
        cwd=ROOT, encoding="utf-8")
    finish = previous[previous.index("    private static Boolean finishTask("):
                      previous.index("    private Boolean inviteRegister(")].strip()
jar = next(p for p in (Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
                      "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
           if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
code = r'''
import org.json.*; import java.util.*; import java.util.function.Consumer;
public class StallCheck {
    static final String TAG="Stall";
    static class MyUtils {
        static boolean skip=true;
        static boolean closeUnRpc(){return skip;}
        static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }
    static class Log {
        static List<String> lines=new ArrayList<>();
        static void record(String s){lines.add(s);}static void farm(String s){lines.add(s);}
        static void err(String a,String b,Throwable t){throw new AssertionError(b,t);}
    }
    static class MessageUtil {
        static int marks;
        static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}
        static boolean isRetryable(JSONObject j){return false;}static boolean isServerBusy(JSONObject j){return false;}static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}
        static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){marks++;}
    }
    static class TaskAlternative {
        static int attempts;static boolean hit(JSONObject j,String scene){return true;}
        static void trigger(String a,String b,String c,String d,String e,String f,Consumer<String> log){attempts++;}
    }
    static class AntStallRpcCall {
        static int finishes,awards;static String list;static boolean fail;
        static String taskList(){return list;}
        static String finishTask(String id){finishes++;return new JSONObject().put("resultCode",fail?"FAIL":"SUCCESS").put("success",!fail).toString();}
        static String receiveTaskAward(String id){awards++;return "{\"success\":true,\"incAwardCount\":1}";}
    }
    enum TaskStatus {TODO,RECEIVED}
    static class TimeUtil {static void sleep(long ms){}}
    static class Status { static boolean hasFlagToday(String s){return false;}static void flagToday(String s){}}
    static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
    Field<Set<String>> AntStallTaskList=new Field<>(new HashSet<>());
    Field<Boolean> doTaskOnce=new Field<>(false);
    void signToday(){}
    Boolean doStallTask(JSONObject task,String title){return finishTask(task.optString("taskType"),title);}
    @@METHODS@@
    static void reset(String id,String state){
        MyUtils.skip=true;AntStallRpcCall.finishes=0;AntStallRpcCall.awards=0;AntStallRpcCall.fail=false;
        MessageUtil.marks=0;TaskAlternative.attempts=0;Log.lines.clear();
        JSONObject task=new JSONObject().put("taskType",id).put("taskStatus",state).put("bizInfo",new JSONObject().put("title","test task").toString());
        AntStallRpcCall.list=new JSONObject().put("resultCode","SUCCESS").put("taskModels",new JSONArray().put(task)).toString();
    }
    public static void main(String[] args){
        StallCheck s=new StallCheck();
        for(String id: new String[]{"ANTSTALL_ELEME_VISIT","ANTSTALL_TASK_XCXYX_langmancanting","ANTSTALL_TASK_XCXYX_qingyunjue","ANTSTALL_TASK_XCXYX_sijiwuyu"}){
            reset(id,"TODO");s.taskList();assert AntStallRpcCall.finishes==0&&AntStallRpcCall.awards==0&&TaskAlternative.attempts==0&&MessageUtil.marks==0 : "Unsupported TODO made RPC: "+id;
            assert Log.lines.stream().anyMatch(x->x.contains("通用接口不能自动完成"));
            reset(id,"FINISHED");s.taskList();assert AntStallRpcCall.finishes==0&&AntStallRpcCall.awards==1;
            reset(id,"RECEIVED");s.taskList();assert AntStallRpcCall.finishes==0&&AntStallRpcCall.awards==0;
            reset(id,"TODO");MyUtils.skip=false;s.taskList();assert AntStallRpcCall.finishes==1&&AntStallRpcCall.awards==1;
        }
        reset("ANTSTALL_NORMAL_OPEN_NOTICE","TODO");s.taskList();assert AntStallRpcCall.finishes==1&&AntStallRpcCall.awards==1;
        reset("ANTSTALL_NORMAL_OPEN_NOTICE","TODO");AntStallRpcCall.fail=true;s.taskList();assert AntStallRpcCall.finishes==1&&AntStallRpcCall.awards==0&&TaskAlternative.attempts==1;
        System.out.println("PASS unsupported TODO skips primary/fallback/auto-blacklist; completed rewards, received skips, explicit override and other tasks preserved");
    }
}
'''.replace("@@METHODS@@", finish + "\n" + method(path, "    private void taskList()") +
            "\n" + method(path, "    private static void receiveTaskAward("))

code=code.replace("public class StallCheck {", "public class StallCheck {\n"+POLICY+award(method))
code=code.replace("    public static void main", method(path,"private static Outcome attemptFinishTask(")+method(path,"private static TaskAttemptPolicy.ProbeResult probeStallStatus(")+"\n    public static void main")

with tempfile.TemporaryDirectory(prefix="sesame-ag-retirements-") as tmp:
    java = Path(tmp) / "StallCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "StallCheck"], check=True)
