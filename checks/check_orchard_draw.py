"""Run production orchard draw methods/RPC builders with isolated responses; no network/device."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(cache.glob("*/*/json-*.jar"))[-1]
orchard = (SOURCE / "model/task/antOrchard/AntOrchard.java").read_text(encoding="utf-8")
start = orchard.index("    private void orchardChouChouLe()")
end = orchard.index("    private boolean checkOrchardOpen()", start)
flow = orchard[start:end]
rpc_path = "model/task/antOrchard/AntOrchardRpcCall.java"
rpc = (SOURCE / rpc_path).read_text(encoding="utf-8")
rpc_methods = "\n".join(method(rpc_path, signature) for signature in re.findall(
    r"    public static String (?:enterDrawActivityantorchard|listTaskantorchard|receiveDrawTaskAwardantorchard|drawantorchard|batchDrawantorchard|drawSyncantorchard|finishTaskantorchard|finishTaskantorchardV2)\([^\n]+", rpc))
guard = "\n".join(method("rpc/intervallimit/RpcRequestGuard.java", signature) for signature in (
    "    public static boolean isFailure(JSONObject result)",
    "    public static String errorMessage(JSONObject result)",
))
code = r'''
import java.util.*;
import org.json.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class OrchardDrawCheck {
    static class MyUtils {
        static JSONObject newJSONObject(){return new JSONObject();}
        static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }
    static class RpcFailurePolicy { static String boundedMessage(String s){return s;} }
    static class RpcRequestGuard { @@GUARD@@ }
    static class RandomUtil { static String getRandomString(int n){return "12345678";} }
    static class UserIdMap { static String getCurrentUid(){return "USER\"A";} }
    static class TaskCancelledException extends RuntimeException { }
    static class TimeUtil { static boolean cancel; static void sleep(long ms){if(cancel)throw new TaskCancelledException();} }
    static class Field { Set<String> values=new HashSet<>(); Set<String> getValue(){return values;} }
    static class Log {
        static List<String> runtime=new ArrayList<>(), farm=new ArrayList<>();
        static void record(String s){runtime.add(s);} static void farm(String s){farm.add(s);}
        static void err(String a,String b,Throwable t){throw new AssertionError(b,t);}
    }
    static class MessageUtil {
        static boolean checkSuccess(String tag,JSONObject j){return Boolean.TRUE.equals(j.opt("success")) && !RpcRequestGuard.isFailure(j);}
        static void checkResultCodeAndMarkTaskBlackList(String field,String name,JSONObject j){blacklisted.add(name);}
    }
    static Set<String> blacklisted=new HashSet<>();
    static class GameTask {
        static int reports, count; static int result=2;
        static GameTask matchAppId(String id){return "2060170000356601".equals(id)?new GameTask():null;}
        String getTitle(){return "农场上车车";}
        int reportSync(String scene,int n){
            assert !TaskLifecycle.isIdle() && TaskLifecycle.freezeIfIdle()==null;
            reports++;count=n;return result;
        }
    }
    static class AntOrchardRpcCall { @@VERSION@@ @@RPC@@ }
    static class ApplicationHook {
        static List<JSONObject> calls=new ArrayList<>();
        static String fail="", invalid="", enter; static Deque<String> lists=new ArrayDeque<>();
        static Object balance=41; static boolean badBalance;
        static String requestString(String method,String args){
            JSONObject b=new JSONArray(args).optJSONObject(0);assert b!=null;
            String op=method.substring(method.lastIndexOf('.')+1);
            calls.add(new JSONObject().put("op",op).put("body",b));
            if(op.equals(fail))return "{\"success\":false,\"resultCode\":\"102\",\"resultDesc\":\"服务器正在开小差\"}";
            if(op.equals(invalid))return "{\"success\":\"true\"}";
            if(op.equals("enterDrawActivityantorchard"))return enter;
            if(op.equals("listTaskantorchard"))return lists.size()>1?lists.removeFirst():lists.peekFirst();
            if(op.equals("drawSyncantorchard"))return new JSONObject().put("success",true)
                .put("drawAsset",badBalance?new JSONObject():new JSONObject().put("blance",balance)).toString();
            if(op.equals("batchDrawantorchard"))return "{\"success\":true,\"drawResultList\":[{\"prizeVO\":{\"prizeName\":\"肥料100g\"}}]}";
            return "{\"success\":true,\"incAwardCount\":1,\"awardInfo\":{\"deltaAwardCount\":1}}";
        }
    }
    static class Orchard {
        static String TAG="AntOrchard"; String userId=UserIdMap.getCurrentUid();
        Field OrchardChouChouLeTaskList=new Field();
        @@FLOW@@
    }
    static JSONObject task(String type,String status,String appId,boolean tracer){
        JSONObject biz=new JSONObject().put("title","任务"+type).put("targetUrl","alipays://platformapi/startapp?appId="+appId+"&query=test");
        JSONObject base=new JSONObject().put("bizInfo",biz.toString());
        JSONObject task=new JSONObject().put("taskBaseInfo",base);
        if(tracer)task.put("iepTaskTracer","groupId:X~taskType:"+type+"~taskStatus:"+status+"~finishOnceAwardCnt:1");
        else base.put("taskType",type).put("taskStatus",status).put("sceneCode","ANTORCHARD_DRAW_TIMES_TASK");
        return task;
    }
    static String list(JSONObject... tasks){return new JSONObject().put("success",true).put("taskInfoList",new JSONArray(List.of(tasks))).toString();}
    static void reset(){
        ApplicationHook.calls.clear(); ApplicationHook.lists.clear();ApplicationHook.lists.add(list());
        ApplicationHook.fail="";ApplicationHook.invalid="";ApplicationHook.balance=41;ApplicationHook.badBalance=false;
        ApplicationHook.enter="{\"success\":true,\"drawActivity\":{\"activityId\":\"A\\\"B\",\"name\":\"阿肥寻宝记\"}}";
        Log.runtime.clear();Log.farm.clear();blacklisted.clear();GameTask.reports=0;GameTask.count=0;GameTask.result=2;TimeUtil.cancel=false;
    }
    static List<JSONObject> calls(String op){List<JSONObject> found=new ArrayList<>();
        for(JSONObject c:ApplicationHook.calls)if(op.equals(c.optString("op")))found.add(c.optJSONObject("body"));return found;}
    static boolean logged(String part){return Log.runtime.stream().anyMatch(s->s.contains(part));}
    static void run(){new Orchard().orchardChouChouLe();assert TaskLifecycle.isIdle();}
    public static void main(String[] args){
        reset();run();
        assert calls("batchDrawantorchard").size()==1:"flat activity must draw";
        JSONObject batch=calls("batchDrawantorchard").get(0);
        assert batch.optInt("times")==41 && batch.optString("activityId").equals("A\"B") && batch.optString("userId").equals("USER\"A");
        assert calls("drawantorchard").isEmpty() && logged("批量抽奖成功") && logged("肥料100g");
        assert calls("enterDrawActivityantorchard").get(0).optJSONObject("context").optString("appMode").equals("normal") : "entry retained Xu's old student mode";
        assert calls("listTaskantorchard").get(0).optJSONObject("extend").optString("appMode").equals("normal");
        assert calls("drawSyncantorchard").get(0).optJSONObject("context").optString("appMode").equals("normal");
        assert Log.runtime.containsAll(Log.farm);
        System.out.println("PASS flat activity, >30 batch count, escaped RPC values, runtime result logs");

        reset();ApplicationHook.lists.clear();
        ApplicationHook.lists.add(list(task("DRAW_GOLDENBEAN_liulan","TODO","",true),task("GAME","TODO","2060170000356601",true)));
        ApplicationHook.lists.add(list(task("DRAW_GOLDENBEAN_liulan","FINISHED","",true),task("GAME","FINISHED","2060170000356601",true)));
        ApplicationHook.lists.add(list(task("DRAW_GOLDENBEAN_liulan","RECEIVED","",true),task("GAME","RECEIVED","2060170000356601",true)));
        run();assert calls("finishTaskantorchard").size()==1 && GameTask.reports==1 && GameTask.count==1;
        assert calls("receiveTaskAwardantorchard").size()==2;
        for(JSONObject b:calls("receiveTaskAwardantorchard"))assert !b.has("awardCountForReceive") && b.optString("sceneCode").equals("ANTORCHARD_DRAW_TIMES_TASK");
        assert blacklisted.isEmpty() && logged("上报") && logged("领取成功");

        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("GAME","TODO","2060170000356601",true)));
        run();assert GameTask.reports==1 && calls("finishTaskantorchard").isEmpty() && calls("receiveTaskAwardantorchard").isEmpty();
        assert logged("未推进") && blacklisted.isEmpty();

        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("UNREGISTERED","TODO","99999",true)));
        run();assert GameTask.reports==0 && calls("finishTaskantorchard").isEmpty() && blacklisted.isEmpty() && logged("未支持");
        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("GAME","TODO","2060170000356601",true)));
        Orchard denied=new Orchard();denied.OrchardChouChouLeTaskList.values.add("任务GAME");denied.orchardChouChouLe();
        assert GameTask.reports==0 && logged("黑名单");
        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("GAME","TODO","2060170000356601",true)));GameTask.result=0;
        run();assert GameTask.reports==1 && calls("receiveTaskAwardantorchard").isEmpty() && logged("上报失败");
        for(String count:List.of("0","11","2147483648","1.5","bad")){
            reset();JSONObject bad=task("GAME","TODO","2060170000356601",true);
            bad.put("iepTaskTracer",bad.optString("iepTaskTracer").replace("finishOnceAwardCnt:1","finishOnceAwardCnt:"+count));
            ApplicationHook.lists.clear();ApplicationHook.lists.add(list(bad));run();assert GameTask.reports==0:count;
        }
        System.out.println("PASS tracer tasks, registered games, server-state verification, unsupported/blacklisted skips");

        for(String shape:List.of("drawSceneGroups","drawScene")){
            reset();JSONObject scene=new JSONObject().put("drawActivity",new JSONObject().put("activityId","old").put("sceneCode","ANTORCHARD_DRAW_TIMES").put("name","旧活动"));
            ApplicationHook.enter=new JSONObject().put("success",true).put(shape,shape.equals("drawScene")?scene:new JSONArray().put(scene)).toString();
            ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("LEGACY","FINISHED","",false)));
            run();assert calls("receiveTaskAwardantorchard").size()==1 && calls("batchDrawantorchard").size()==1;
        }
        reset();ApplicationHook.balance=0;run();assert calls("batchDrawantorchard").isEmpty() && logged("无剩余");
        reset();ApplicationHook.badBalance=true;run();assert calls("batchDrawantorchard").isEmpty() && logged("次数");
        for(Object invalid:List.of(-1,1.5,true,"1.5","bad","2147483648","4294967337","18446744073709551657")){
            reset();ApplicationHook.balance=invalid;run();assert calls("batchDrawantorchard").isEmpty():invalid;
        }
        reset();ApplicationHook.balance="41";run();assert calls("batchDrawantorchard").get(0).optInt("times")==41;
        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("LEGACY","TODO","",false)));ApplicationHook.fail="finishTaskantorchard";
        run();assert calls("finishTaskantorchard").size()==1 && calls("finishTask").size()==1 && blacklisted.isEmpty();
        for(String failure:List.of("enterDrawActivityantorchard","listTaskantorchard","drawSyncantorchard","batchDrawantorchard")){
            reset();ApplicationHook.fail=failure;run();assert calls("drawantorchard").isEmpty();
            assert logged("102") && logged("服务器正在开小差"):failure;
            assert !logged("批量抽奖成功");
        }
        reset();ApplicationHook.invalid="drawSyncantorchard";run();assert calls("batchDrawantorchard").isEmpty();
        reset();TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle();run();assert ApplicationHook.calls.isEmpty();TaskLifecycle.thaw(owner);
        reset();ApplicationHook.lists.clear();ApplicationHook.lists.add(list(task("DRAW_GOLDENBEAN_liulan","TODO","",true)));TimeUtil.cancel=true;
        try{run();throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}
        assert TaskLifecycle.isIdle() && calls("batchDrawantorchard").isEmpty();
        System.out.println("PASS old layouts, balance validation, failure reasons, no double draw, lifecycle and cancellation");
    }
}
'''
for token, value in (("@@FLOW@@", flow), ("@@RPC@@", rpc_methods), ("@@GUARD@@", guard),
                     ("@@VERSION@@", re.search(r'private static final String VERSION[^;]+;', rpc)[0])):
    code = code.replace(token, value)
with tempfile.TemporaryDirectory(prefix="sesame-orchard-draw-") as tmp:
    java = Path(tmp) / "OrchardDrawCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java),
                    str(SOURCE / "data/task/TaskLifecycle.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "OrchardDrawCheck"], check=True)
