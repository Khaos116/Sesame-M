"""Run production auto-feed scheduling against isolated RPC/timer boundaries; no network."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE

cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
jar = sorted(p for p in (cache / "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar")
             if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
source = (SOURCE / "model/task/antFarm/AntFarm.java").read_text(encoding="utf-8")
start = source.index("    private void autoFeedAnimal()")
flow = source[start:source.index("    private void animalWakeUpTime", start)]
code = r'''
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class AutoFeedCheck {
    static class TaskCancelledException extends RuntimeException {}
    static class Field { boolean value=true; boolean getValue(){return value;} }
    static class UserIdMap {
        static String uid="self";
        static String getCurrentUid(){return uid;}
        static String getCurrentMaskName(){return uid;}
    }
    static class TimeUtil { static String getCommonDate(long n){return "time";} }
    static class MyUtils {
        static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }
    static class MessageUtil { static boolean checkMemo(String tag,JSONObject j){return "SUCCESS".equals(j.optString("memo"));} }
    static class Log {
        static List<String> lines=new ArrayList<>();
        static void record(String s){lines.add(s);}
        static void farm(String s){lines.add(s);}
        static void err(String tag,String s,Throwable t){lines.add(s);}
    }
    static class AntFarmRpcCall {
        static String response;
        static int queries;
        static boolean cancel;
        static String syncAnimalStatus(String farm){queries++;if(cancel)throw new TaskCancelledException();return response;}
    }
    enum AnimalFeedStatus { HUNGRY,EATING,SLEEPY }
    static class Animal {
        String animalFeedStatus="EATING";
        Long startEatTime=System.currentTimeMillis(); Double consumeSpeed=1d;
    }
    static class ChildModelTask {
        String id;Runnable action;long time;long generation=TaskLifecycle.generation();
        ChildModelTask(String id,String group,Runnable action,Long time){this.id=id;this.action=action;this.time=time;}
        void run(){try(TaskLifecycle.Work work=TaskLifecycle.enter(generation)){if(work!=null)action.run();}}
    }
    static class Farm {
        String TAG="Farm",ownerFarmId="farm";
        Field feedAnimal=new Field(); Animal ownerAnimal=new Animal(); Animal[] animals={ownerAnimal}; int foodInTrough=100;
        Map<String,ChildModelTask> tasks=new LinkedHashMap<>();int feeds;
        boolean hasChildTask(String id){return tasks.containsKey(id);}
        void removeChildTask(String id){tasks.remove(id);}
        boolean addChildTask(ChildModelTask t){tasks.put(t.id,t);return true;}
        void feedAnimal(String farm){assert farm.equals(ownerFarmId);feeds++;}
        void syncAnimalStatus(String farm){AntFarmRpcCall.syncAnimalStatus(farm);}
        @@FLOW@@
        void runNext(){
            ChildModelTask t=tasks.values().iterator().next();t.run();
            // Both production executors remove by ID after the callback: rescheduling must use a new ID.
            tasks.remove(t.id);
        }
    }
    static String snapshot(String status,Object speed,Object start,Object food){
        JSONObject animal=new JSONObject().put("masterFarmId","farm")
            .put("animalStatusVO",new JSONObject().put("animalFeedStatus",status))
            .put("startEatTime",start).put("consumeSpeed",speed);
        return new JSONObject().put("memo","SUCCESS").put("subFarmVO",new JSONObject()
            .put("farmId","farm").put("foodInTrough",food).put("animals",new JSONArray().put(animal))).toString();
    }
    static Farm reset(){UserIdMap.uid="self";AntFarmRpcCall.queries=0;AntFarmRpcCall.cancel=false;Log.lines.clear();
        AntFarmRpcCall.response=snapshot("EATING",1,System.currentTimeMillis(),100);return new Farm();}
    static boolean logged(String s){return Log.lines.stream().anyMatch(x->x.contains(s));}
    static void retry(Farm f){long now=System.currentTimeMillis();f.autoFeedAnimal();
        assert f.tasks.size()==1 : "expected one retry";
        ChildModelTask t=f.tasks.values().iterator().next();
        assert t.id.startsWith("UPDATE|FA|") : "must recheck rather than feed invalid state";
        assert t.time>=now+29000 && t.time<=System.currentTimeMillis()+31000;
    }
    public static void main(String[] args){
        Farm f=reset();AntFarmRpcCall.response=snapshot("HUNGRY",1,System.currentTimeMillis(),100);
        retry(f);
        int before=AntFarmRpcCall.queries;f.autoFeedAnimal();assert AntFarmRpcCall.queries==before && f.tasks.size()==1;
        for(int i=0;i<5;i++)f.runNext();
        assert f.tasks.isEmpty() && AntFarmRpcCall.queries==6 && f.feeds==0;
        assert logged("上限") && logged("HUNGRY");
        f.autoFeedAnimal();assert f.tasks.size()==1 : "next module round must be allowed to retry";
        f=reset();AntFarmRpcCall.response=snapshot("SLEEPY",1,System.currentTimeMillis(),100);retry(f);
        AntFarmRpcCall.response=snapshot("EATING",2,System.currentTimeMillis(),100);f.runNext();
        assert f.tasks.size()==1 && f.tasks.containsKey("FA|farm") && logged("恢复");
        f.runNext();assert f.feeds==1;
        for(Object speed:new Object[]{0,-1,"bad",JSONObject.NULL}){
            f=reset();AntFarmRpcCall.response=snapshot("EATING",speed,System.currentTimeMillis(),100);retry(f);
        }
        for(Object time:new Object[]{0,"bad",JSONObject.NULL,System.currentTimeMillis()+60000}){
            f=reset();AntFarmRpcCall.response=snapshot("EATING",1,time,100);retry(f);
        }
        for(Object food:new Object[]{-1,"bad",JSONObject.NULL,0}){
            f=reset();AntFarmRpcCall.response=snapshot("EATING",1,System.currentTimeMillis(),food);retry(f);
        }
        for(String raw:List.of("bad","{}","{\"memo\":\"FAIL\",\"resultCode\":\"102\"}",
                "{\"memo\":\"SUCCESS\",\"subFarmVO\":{\"farmId\":\"farm\",\"animals\":[]}}")){
            f=reset();AntFarmRpcCall.response=raw;retry(f);assert f.feeds==0;
        }
        f=reset();JSONObject bad=new JSONObject(AntFarmRpcCall.response);
        bad.optJSONObject("subFarmVO").put("farmId","other");AntFarmRpcCall.response=bad.toString();retry(f);
        f=reset();bad=new JSONObject(AntFarmRpcCall.response);bad.optJSONObject("subFarmVO").optJSONArray("animals")
            .optJSONObject(0).put("masterFarmId","other");AntFarmRpcCall.response=bad.toString();retry(f);
        f=reset();long now=System.currentTimeMillis();AntFarmRpcCall.response=snapshot("EATING",1,now,1.5);
        f.autoFeedAnimal();assert f.tasks.get("FA|farm").time>=now+1300 : "fractional seconds lost";
        f=reset();AntFarmRpcCall.response=snapshot("EATING",1e-300,System.currentTimeMillis(),100);retry(f);
        f=reset();AntFarmRpcCall.response=snapshot("HUNGRY",1,System.currentTimeMillis(),100);retry(f);
        UserIdMap.uid="other";before=AntFarmRpcCall.queries;f.runNext();assert f.tasks.isEmpty() && AntFarmRpcCall.queries==before;
        f=reset();f.autoFeedAnimal();UserIdMap.uid="other";f.runNext();assert f.feeds==0;
        f=reset();AntFarmRpcCall.response=snapshot("HUNGRY",1,System.currentTimeMillis(),100);retry(f);
        f.ownerFarmId="other";before=AntFarmRpcCall.queries;f.runNext();assert f.tasks.isEmpty() && AntFarmRpcCall.queries==before;
        f=reset();f.autoFeedAnimal();f.feedAnimal.value=false;f.runNext();assert f.feeds==0;
        f=reset();AntFarmRpcCall.response=snapshot("HUNGRY",1,System.currentTimeMillis(),100);retry(f);
        f.feedAnimal.value=false;before=AntFarmRpcCall.queries;f.runNext();assert f.tasks.isEmpty() && AntFarmRpcCall.queries==before;
        f=reset();f.feedAnimal.value=false;f.autoFeedAnimal();assert f.tasks.isEmpty() && AntFarmRpcCall.queries==0;
        f=reset();AntFarmRpcCall.cancel=true;
        try{f.autoFeedAnimal();throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}
        assert f.tasks.isEmpty();
        f=reset();AntFarmRpcCall.response=snapshot("HUNGRY",1,System.currentTimeMillis(),100);retry(f);
        TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();before=AntFarmRpcCall.queries;f.runNext();
        assert f.tasks.isEmpty() && AntFarmRpcCall.queries==before;TaskLifecycle.thaw(freeze);
        System.out.println("PASS bounded auto-feed retries, fresh snapshots, scheduling precision, logs and cancellation");
    }
}
'''.replace("@@FLOW@@", flow)
with tempfile.TemporaryDirectory(prefix="sesame-auto-feed-") as tmp:
    java = Path(tmp) / "AutoFeedCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java),
                    str(SOURCE / "data/task/TaskLifecycle.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "AutoFeedCheck"], check=True)
