"""Compile production orchard game-stay/RPC code and exercise isolated responses; no network/device."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jars = sorted(cache.glob("*/*/json-*.jar"))
assert jars, "Build the project first to cache org.json"
pkg = "io.github.aw1y2z.sesame"
sources = {
    "util/MyUtils": '''import org.json.*; public class MyUtils {
        public static JSONObject newJSONObject(){return new JSONObject();}
        public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }''',
    "util/Status": '''import java.util.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
        public class Status { public static Set<String> flags=new HashSet<>();
        public static boolean hasFlagToday(String k){return flags.contains(UserIdMap.getCurrentUid()+k);}
        public static void flagToday(String k,String uid){if(uid.equals(UserIdMap.getCurrentUid()))flags.add(uid+k);}
    }''',
    "util/Log": '''import java.util.*; public class Log { public static List<String> messages=new ArrayList<>(), runtime=new ArrayList<>(), farm=new ArrayList<>();
        public static void record(String s){messages.add(s);runtime.add(s);} public static void farm(String s){messages.add(s);farm.add(s);}
        public static void err(String t,String m,Throwable e){throw new AssertionError(m,e);}
    }''',
    "util/TimeUtil": '''import java.util.*; import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
        public class TimeUtil { public static List<Long> sleeps=new ArrayList<>(); public static boolean cancel;
        public static void sleep(long ms){assert !TaskLifecycle.isIdle(); assert TaskLifecycle.freezeIfIdle()==null;
            sleeps.add(ms); if(cancel && ms>=30000)throw new TaskCancelledException();}
    }''',
    "util/idMap/UserIdMap": '''public class UserIdMap { public static String uid="A"; public static String getCurrentUid(){return uid;} }''',
    "model/base/TaskAlternative": '''public class TaskAlternative { public static String request(String a,String b,String c){return "{}";} }''',
    "rpc/intervallimit/RpcRequestGuard": "import org.json.*; public class RpcRequestGuard {" + method(
        "rpc/intervallimit/RpcRequestGuard.java", "    public static boolean isFailure(JSONObject result)") + method(
        "rpc/intervallimit/RpcRequestGuard.java", "    public static String errorMessage(JSONObject result)") + "}",
    "hook/ApplicationHook": r'''import java.util.*; import org.json.*;
        public class ApplicationHook {
        public static String fail="", reply="{\"success\":true}", finishReply=null;
        public static List<JSONObject> calls=new ArrayList<>(); public static Map<Integer,JSONArray> pages=new HashMap<>();
        public static JSONArray deliveries=new JSONArray();
        public static String requestString(String method,String args){
            JSONObject body=new JSONArray(args).optJSONObject(0); assert body!=null;
            calls.add(new JSONObject().put("method",method).put("body",body));
            String op=method.substring(method.lastIndexOf('.')+1);
            if(!fail.isEmpty() && (fail.equals(op)||fail.equals(body.optString("eventId"))))return "{\"success\":false,\"resultCode\":\"RPC_SKIPPED\"}";
            if(op.equals("indexFeeds"))return new JSONObject().put("success",true).put("feedsInfoList",pages.getOrDefault(body.optInt("pageNum"),new JSONArray())).toString();
            if(op.equals("orchardIndexDelivery"))return new JSONObject().put("success",true).put("indexDeliveryList",deliveries).toString();
            if(op.equals("finishTask") && finishReply!=null)return finishReply;
            if(op.equals("finishTask") && reply.equals("{\"success\":true}"))return "{\"code\":\"100000000\",\"finishAwardResultVO\":{\"deltaAwardCount\":25}}";
            return reply;
        }
    }''',
}

test = r'''
import java.util.*; import org.json.*;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.model.task.antOrchard.*;
public class OrchardGameStayCheck {
    static final String APP="2021\"\\A";
    static JSONObject benefit(String id,int seconds){return new JSONObject().put("benefitType","FLOAT_BALL_TASK")
        .put("iepTaskId",id).put("iepSceneCode","SCENE\"").put("bizInfo",new JSONObject().put("floatBallDuration",seconds));}
    static JSONObject feed(String task,int seconds){return new JSONObject().put("itemType","game")
        .put("gameItem",new JSONObject().put("gameItemId",APP).put("gameName",task))
        .put("itemOutExtMap",new JSONObject().put("orchardBenefitInfo",benefit(task,seconds).toString()));}
    static JSONObject delivery(String task,int seconds){return new JSONObject().put("contentId",APP)
        .put("deliveryDisplayInfo",new JSONObject().put("btnText",task)).put("deliveryBenefitInfo",benefit(task,seconds));}
    static void reset(){ApplicationHook.calls.clear(); ApplicationHook.pages.clear(); ApplicationHook.deliveries=new JSONArray();
        ApplicationHook.fail=""; ApplicationHook.reply="{\"success\":true}"; ApplicationHook.finishReply=null;
        Status.flags.clear(); TimeUtil.sleeps.clear();
        TimeUtil.cancel=false; UserIdMap.uid="A"; Log.messages.clear(); Log.runtime.clear(); Log.farm.clear();}
    static List<JSONObject> calls(String suffix){List<JSONObject> result=new ArrayList<>();
        for(JSONObject c:ApplicationHook.calls)if(c.optString("method").endsWith(suffix))result.add(c.optJSONObject("body"));return result;}
    static void single(){ApplicationHook.pages.put(1,new JSONArray().put(feed("T1",30)));}
    public static void main(String[] args){
        reset(); single(); ApplicationHook.pages.put(2,new JSONArray().put(feed("T2",45)));
        JSONObject missing=delivery("MISSING",30); missing.optJSONObject("deliveryBenefitInfo").remove("iepSceneCode");
        JSONObject badType=delivery("BAD_TYPE",30).put("contentId",true);
        JSONObject badDuration=delivery("BAD_DURATION",30);
        badDuration.optJSONObject("deliveryBenefitInfo").optJSONObject("bizInfo").put("floatBallDuration","bad");
        JSONObject badBiz=delivery("BAD_BIZ",30);
        badBiz.optJSONObject("deliveryBenefitInfo").put("bizInfo",true);
        ApplicationHook.deliveries=new JSONArray().put(delivery("T1",60)).put(delivery("T3",10))
            .put(delivery("BAD",-1)).put(delivery("TOO_LONG",1801)).put(missing).put(badType).put(badDuration).put(badBiz).put(JSONObject.NULL);
        AntOrchardGameStayTask.execute();
        assert calls("indexFeeds").size()==3;
        assert calls("finishTask").size()==3 && Status.flags.size()==3;
        List<JSONObject> durations=calls("submitUserPlayDurationAction");
        assert durations.get(0).optInt("playTime")==60 && durations.get(1).optInt("playTime")==45 && durations.get(2).optInt("playTime")==30;
        for(JSONObject body:durations)assert APP.equals(body.optString("gameAppId"));
        List<JSONObject> events=calls("submitEvent"); assert events.size()==9;
        assert events.get(2).optJSONObject("eventAttrMap").optLong("GAME_ELAPASED_TIME")==60000;
        assert calls("finishTask").get(0).optString("sceneCode").equals("SCENE\"");
        assert calls("finishTask").get(0).optString("userId").equals("A");
        assert TimeUtil.sleeps.contains(60000L) && TaskLifecycle.isIdle();
        assert Log.runtime.stream().anyMatch(s->s.contains("成功，已结项") && s.contains("25g"));
        assert Log.runtime.stream().anyMatch(s->s.contains("成功 3 个、失败 0 个"));
        assert Log.runtime.containsAll(Log.farm);
        int done=calls("finishTask").size(); AntOrchardGameStayTask.execute(); assert calls("finishTask").size()==done;
        UserIdMap.uid="B"; AntOrchardGameStayTask.execute(); assert calls("finishTask").size()==done+3;
        Status.flags.clear(); AntOrchardGameStayTask.execute(); assert calls("finishTask").size()==done+6;
        System.out.println("PASS pagination, cross-source dedup, distinct same-game tasks, duration units, escaping, account/day isolation");
        for(String failure:List.of("noticeGame","submitUserAction","GAME_FIRST_FRAME","loading_completed","submitUserPlayDurationAction","game_play","finishTask")){
            reset(); single(); ApplicationHook.fail=failure; AntOrchardGameStayTask.execute(); assert Status.flags.isEmpty():failure;
            if(!failure.equals("finishTask"))assert calls("finishTask").isEmpty():failure;
            assert TaskLifecycle.isIdle();
            assert Log.runtime.stream().anyMatch(s->s.contains("失败#状态码=RPC_SKIPPED#原因=")):failure;
            assert Log.runtime.stream().anyMatch(s->s.contains("成功 0 个、失败 1 个")):failure;
        }
        for(String response:List.of("{}","bad","{\"success\":\"true\"}","{\"success\":false,\"code\":\"100000000\"}","{\"success\":true,\"error\":\"RPC_SKIPPED\"}")){
            reset(); single(); ApplicationHook.reply=response; AntOrchardGameStayTask.execute(); assert Status.flags.isEmpty():response;
            reset(); single(); ApplicationHook.finishReply=response; AntOrchardGameStayTask.execute(); assert Status.flags.isEmpty():response;
        }
        reset(); single(); ApplicationHook.finishReply="{\"code\":\"100000000\"}";
        AntOrchardGameStayTask.execute(); assert Status.flags.size()==1;
        assert Log.messages.stream().noneMatch(s->s.contains("0g"));
        assert Log.messages.stream().anyMatch(s->s.contains("奖励数量未返回"));
        reset(); single(); TimeUtil.cancel=true;
        try{AntOrchardGameStayTask.execute();throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}
        assert Status.flags.isEmpty() && calls("finishTask").isEmpty() && TaskLifecycle.isIdle();
        reset(); single(); TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle(); AntOrchardGameStayTask.execute();
        assert ApplicationHook.calls.isEmpty(); TaskLifecycle.thaw(owner);
        reset(); single(); UserIdMap.uid=""; AntOrchardGameStayTask.execute(); assert ApplicationHook.calls.isEmpty();
        System.out.println("PASS stage failures, invalid responses, RPC pause, cancellation and lifecycle admission");
        reset(); ApplicationHook.fail="indexFeeds"; ApplicationHook.deliveries.put(delivery("D",30));
        AntOrchardGameStayTask.execute(); assert calls("finishTask").size()==1;
        System.out.println("PASS independent delivery scan survives failed feeds query");
        reset(); single(); ApplicationHook.finishReply="{\"success\":false,\"resultCode\":\"3000\",\"resultDesc\":\"系统繁忙，请稍后重试\"}";
        AntOrchardGameStayTask.execute();
        assert Log.runtime.stream().anyMatch(s->s.contains("结项失败#状态码=3000#原因=系统繁忙"));
        reset(); AntOrchardGameStayTask.execute(); assert Log.runtime.stream().anyMatch(s->s.contains("本轮跳过，无可执行任务"));
        System.out.println("PASS runtime success/reward/summary logs and named-stage server failure reasons");
    }
}
'''

with tempfile.TemporaryDirectory(prefix="sesame-orchard-stay-") as temp:
    out = Path(temp)
    for relative in ("model/task/antOrchard/AntOrchardGameStayTask", "model/task/antOrchard/AntOrchardRpcCall",
                     "data/task/TaskLifecycle", "util/TaskCancelledException", "util/RandomUtil", "rpc/intervallimit/RpcFailurePolicy"):
        sources[relative] = (SOURCE / (relative + ".java")).read_text(encoding="utf-8")
    for relative, text in sources.items():
        path = out / (pkg.replace(".", "/") + "/" + relative + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        if not text.startswith("package "):
            text = "package " + pkg + "." + relative.rsplit("/", 1)[0].replace("/", ".") + ";\n" + text
        path.write_text(text, encoding="utf-8")
    base64 = out / "android/util/Base64.java"
    base64.parent.mkdir(parents=True)
    base64.write_text("package android.util; public class Base64 {public static int NO_WRAP=2; public static String encodeToString(byte[] a,int b){return \"\";}}", encoding="utf-8")
    (out / "OrchardGameStayCheck.java").write_text(test, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jars[-1]), "-d", temp]
                   + [str(p) for p in out.rglob("*.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp + os.pathsep + str(jars[-1]), "OrchardGameStayCheck"], check=True, timeout=30)

orchard = (SOURCE / "model/task/antOrchard/AntOrchard.java").read_text(encoding="utf-8")
assert '"receiveOrchardGameStay", "农场乐园 | 完成游戏时长任务", true' in orchard
assert 'if (receiveOrchardGameStay.getValue()) {\n                AntOrchardGameStayTask.execute();' in orchard
print("PASS orchard enabled-by-default wiring; production classes compiled and exercised")
