"""Run real N-card selection/state/consume methods with local JSON and fake RPCs."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

forest = "model/task/antForest/AntForestV2.java"
rpc = "model/task/antForest/AntForestRpcCall.java"
code = r'''
import org.json.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
public class RobCardCheck {
    static final String TAG = "check", VERSION = "20250813";
    static class Field { int n; Field(int n) { this.n=n; } int getValue() { return n; } }
    Field robExpandCardReplaceRemainDays = new Field(7), robExpandCardForceReplaceExpireDays = new Field(2);
    static class MyUtils { static JSONObject newJSONObject(String s) { try { return new JSONObject(s); } catch(Exception e) { return new JSONObject(); } } }
    static class RpcRequestGuard { static boolean isFailure(JSONObject j) { return Boolean.FALSE.equals(j.opt("success")); } }
    static class Status {
        static Set<String> flags = new HashSet<>();
        static boolean hasFlagToday(String s) { return flags.contains(s); }
        static void flagToday(String s) { flags.add(s); }
    }
    static class TimeUtil { static boolean cancel; static void sleep(long n) { if(cancel) throw new io.github.aw1y2z.sesame.util.TaskCancelledException(); } }
    static class Log {
        static int success;
        static void record(String s) { }
        static void forest(String s) { success++; }
        static void err(String tag, String msg, Throwable t) { throw new AssertionError(t); }
    }
    static String getRandomString(int n) { return "abcdefgh"; }
    static class ApplicationHook {
        static List<JSONObject> requests = new ArrayList<>();
        static Queue<String> replies = new ArrayDeque<>();
        static String requestString(String name, String body) {
            assert name.equals("alipay.antforest.forest.h5.consumeProp");
            requests.add(new JSONArray(body).optJSONObject(0)); return replies.remove();
        }
    }
    static class ForestExpiringProps { static boolean hasUnconfirmed(String group){ return false; } }
 static class AntForestRpcCall {
        static Queue<String> states = new ArrayDeque<>();
        static String queryMiscInfo() { return states.remove(); }
        @@RPC@@
    }
    JSONArray bag;
    JSONArray getForestPropVOList() { return bag; }
    @@METHODS@@
    static JSONObject active(double factor, long end) {
        return new JSONObject().put("propGroup","robExpandCard").put("endTime",end).put("detail",new JSONObject().put("factor",factor));
    }
    static JSONObject card(String id, double factor, long expiry, long duration) {
        return new JSONObject().put("propGroup","robExpandCard").put("propType","LIMIT_TIME_ROB_EXPAND")
            .put("holdsNum",1).put("recentExpireTime",expiry).put("propIdList",new JSONArray().put(id))
            .put("propConfigVO",new JSONObject().put("propName",id).put("durationTime",duration)
                .put("detail",new JSONObject().put("factor",factor)));
    }
    static String state(JSONArray props) {
        return new JSONObject().put("resultCode","SUCCESS").put("combineHandlerVOMap",
            new JSONObject().put("usingProp",new JSONObject().put("userPropVOS",props))).toString();
    }
    static void setup(RobCardCheck check, JSONArray bag, String... states) {
        check.bag=bag; Status.flags.clear(); ApplicationHook.requests.clear(); ApplicationHook.replies.clear();
        AntForestRpcCall.states.clear(); AntForestRpcCall.states.addAll(Arrays.asList(states)); Log.success=0;
    }
    public static void main(String[] args) throws Exception {
        RobCardCheck check=new RobCardCheck(); long now=System.currentTimeMillis(), day=TimeUnit.DAYS.toMillis(1);
        JSONObject old=active(2,now+5*day), normal=card("normal",4,now+20*day,6*86400);
        JSONObject urgent=card("urgent",3,now+day,86400), same=card("same",2,now+day,86400);
        assert choosePreferredRobExpandCard(new JSONArray().put(normal),old,now,7,0)==normal;
        assert choosePreferredRobExpandCard(new JSONArray().put(normal),old,now,4,0)==null;
        assert choosePreferredRobExpandCard(new JSONArray().put(urgent),old,now,7,0)==null : "cannot shorten duration";
        assert choosePreferredRobExpandCard(new JSONArray().put(normal).put(urgent),old,now,7,2)==urgent;
        assert choosePreferredRobExpandCard(new JSONArray().put(urgent),active(2,now+100*day),now,0,2)==urgent;
        assert choosePreferredRobExpandCard(new JSONArray().put(card("low",1,now+day,86400)),old,now,7,2)==null;
        assert choosePreferredRobExpandCard(new JSONArray().put(same),old,now,7,2)==same;
        assert choosePreferredRobExpandCard(new JSONArray().put(same),active(2,now+30*day),now,7,2)==null;
        assert choosePreferredRobExpandCard(new JSONArray().put(same).put(normal),new JSONObject(),now,7,2)==normal;
        assert choosePreferredRobExpandCard(new JSONArray().put(normal),null,now,7,2)==null;
        JSONObject invalid=card("bad",10,now+day,86400);
        for (Object bad : new Object[]{0,-1,1.5,"1.5",true,"9223372036854775808"}) {
            invalid.put("holdsNum",bad);
            assert choosePreferredRobExpandCard(new JSONArray().put(invalid),old,now,7,2)==null;
        }
        invalid.put("holdsNum","1").put("recentExpireTime",now-1);
        assert choosePreferredRobExpandCard(new JSONArray().put(invalid),old,now,7,2)==null;
        invalid.remove("recentExpireTime");
        assert choosePreferredRobExpandCard(new JSONArray().put(invalid),old,now,7,2)==null;
        invalid.put("recentExpireTime",now+day).put("propType",true);
        assert choosePreferredRobExpandCard(new JSONArray().put(invalid),old,now,7,2)==null;
        for (Object bad : new Object[]{"NaN","Infinity",true,"1e10000",-1,0}) {
            assert robExpandCardFactor(new JSONObject().put("factor",bad))==0;
        }
        assert robExpandCardFactor(new JSONObject().put("factor","3.5"))==3.5;
        JSONObject huge=card("huge",4,now+10*day,Long.MAX_VALUE);
        assert choosePreferredRobExpandCard(new JSONArray().put(huge),old,now,7,0)==null;
        setup(check,new JSONArray(),"{}",state(new JSONArray()),state(new JSONArray().put(active(2,now-1))),
            state(new JSONArray().put(new JSONObject().put("propGroup","robExpandCard"))),
            state(new JSONArray().put(old).put(old)),state(new JSONArray().put(active(0,now+day))));
        assert check.queryRobExpandCardState()==null;
        assert check.queryRobExpandCardState().length()==0;
        assert check.queryRobExpandCardState().length()==0;
        assert check.queryRobExpandCardState()==null;
        assert check.queryRobExpandCardState()==null;
        assert check.queryRobExpandCardState()==null;
        String before=state(new JSONArray().put(old)), after=state(new JSONArray().put(active(4,now+6*day)));
        setup(check,new JSONArray().put(normal),before,before,after);
        ApplicationHook.replies.add("{\"resultCode\":\"SUCCESS\",\"resData\":{\"usePropStatus\":\"NEED_CONFIRM_REPLACE\"}}");
        ApplicationHook.replies.add("{\"resultCode\":\"SUCCESS\"}");
        check.usePreferredRobExpandCard();
        assert ApplicationHook.requests.size()==2 && !ApplicationHook.requests.get(0).optBoolean("secondConfirm")
            && ApplicationHook.requests.get(1).optBoolean("secondConfirm") && Log.success==1;
        AntForestRpcCall.states.add(before); check.usePreferredRobExpandCard();
        assert ApplicationHook.requests.size()==2 : "same id cannot be consumed twice today";
        setup(check,new JSONArray().put(normal),before,after);
        ApplicationHook.replies.add("{\"resultCode\":\"SUCCESS\",\"usePropStatus\":\"REPLACE\"}");
        check.usePreferredRobExpandCard(); assert ApplicationHook.requests.size()==1 && Log.success==0 : "changed before confirm";
        setup(check,new JSONArray().put(normal),before,"{}");
        ApplicationHook.replies.add("garbage"); check.usePreferredRobExpandCard();
        assert Log.success==0 && Status.flags.size()==1;
        AntForestRpcCall.states.add(before); check.usePreferredRobExpandCard(); assert ApplicationHook.requests.size()==1;
        setup(check,new JSONArray().put(normal),before,before);
        ApplicationHook.replies.add("{\"resultCode\":\"FAIL\",\"usePropStatus\":\"REPLACE\"}");
        check.usePreferredRobExpandCard(); assert ApplicationHook.requests.size()==1 && Log.success==0;
        setup(check,new JSONArray().put(normal),"{}"); check.usePreferredRobExpandCard(); assert ApplicationHook.requests.isEmpty();
        setup(check,new JSONArray().put(normal),state(new JSONArray()),after);
        ApplicationHook.replies.add("{\"resultCode\":\"SUCCESS\"}"); check.usePreferredRobExpandCard(); assert Log.success==1;
        TimeUtil.cancel=true;
        try { check.usePreferredRobExpandCard(); throw new AssertionError("cancellation swallowed"); }
        catch(io.github.aw1y2z.sesame.util.TaskCancelledException expected) { } finally { TimeUtil.cancel=false; }
        ApplicationHook.replies.add("{}");
        AntForestRpcCall.consumeProp("robExpandCard","quoted\"\\id","type",false);
        JSONObject request=ApplicationHook.requests.get(ApplicationHook.requests.size()-1);
        assert request.optString("propId").equals("quoted\"\\id") && request.optString("timezoneId").equals("Asia/Shanghai");
        System.out.println("N-card selection, strict state, confirmation race, uncertain result, cancellation and RPC escaping: PASS");
    }
}
'''
code = code.replace("@@METHODS@@", "\n".join(method(forest, name) for name in (
    "static long forestFeatureLong(", "private JSONObject queryRobExpandCardState(",
    "private static double robExpandCardFactor(", "private static JSONObject choosePreferredRobExpandCard(",
    "private void usePreferredRobExpandCard(")))
code = code.replace("@@RPC@@", method(rpc, "public static String consumeProp(String propGroup, String propId, String propType, Boolean secondConfirm)"))
source = (SOURCE / forest).read_text(encoding="utf-8")
for field in ("robExpandCardReplaceRemainDays", "robExpandCardForceReplaceExpireDays"):
    assert f'new IntegerModelField("{field}"' in source
    assert ", 0, 0, 365)" in next(line for line in source.splitlines() if f'new IntegerModelField("{field}"' in line)
entry = method(forest, "private void continuousUseAndExchangeCard(")
assert entry.index("usePreferredRobExpandCard();") < entry.index("continuousUseCardCheak(propGroupType)")
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = next(p for p in cache.glob("*/*/json-*.jar") if "-sources" not in p.name and "-javadoc" not in p.name)
with tempfile.TemporaryDirectory(prefix="sesame-rob-card-") as tmp:
    work = Path(tmp)
    java = work / "RobCardCheck.java"
    java.write_text(code, encoding="utf-8")
    cancelled = SOURCE / "util/TaskCancelledException.java"
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java), str(cancelled)], check=True)
    subprocess.run(["java", "-ea", "-cp", os.pathsep.join((tmp, str(jar))), "RobCardCheck"], check=True)
