"""Replay forest games, bounded gifts and watering cancellation; no device or network."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

package = "io.github.aw1y2z.sesame"
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
stubs = {
    "util.MyUtils": '''import org.json.*;public class MyUtils {
        public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}''',
    "util.Log": '''import java.util.*;public class Log {
        public static List<String> messages=new ArrayList<>();
        public static void record(String s){messages.add(s);}public static void forest(String s){messages.add(s);}
        public static void i(String tag,String s){messages.add(s);}public static void i(String s){messages.add(s);}
        public static void err(String tag,String stage,Throwable t){messages.add(stage+":"+t.getClass().getSimpleName());}
        public static void printStackTrace(String stage,Throwable t){err("test",stage,t);}
        public static void printStackTrace(Throwable t){err("test","error",t);}}''',
    "util.MessageUtil": '''import org.json.*;public class MessageUtil {
        public static boolean checkSuccess(String tag,JSONObject j){return Boolean.TRUE.equals(j.opt("success"));}
        public static boolean checkResultCode(String tag,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}''',
    "util.Statistics": '''public class Statistics {public enum DataType {COLLECTED}
        public static int collected;public static void addData(DataType type,int amount){collected+=amount;}
        public static void save(){}}''',
    "util.Status": '''import java.util.*;public class Status {
        public static Set<String> flags=new HashSet<>();public static Map<String,Integer> counts=new HashMap<>();
        public static Runnable beforeFlag;
        public static boolean hasFlagToday(String key){return flags.contains(key);}
        public static void flagToday(String key){if(beforeFlag!=null){Runnable action=beforeFlag;beforeFlag=null;action.run();}flags.add(key);}
        public static int getIntFlagToday(String key){return counts.getOrDefault(key,0);}
        public static void setIntFlagToday(String key,int value){counts.put(key,value);}}''',
    "util.TimeUtil": '''public class TimeUtil {public static boolean cancel;
        public static void sleep(long delay){if(cancel||RunGeneration.isStale()||Thread.currentThread().isInterrupted())throw new TaskCancelledException();
            try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new TaskCancelledException();}}}''',
    "util.StringUtil": '''public class StringUtil {public static String truncate(String value,int n){return value.substring(0,Math.min(n,value.length()));}}''',
    "hook.Toast": '''public class Toast {public static void show(String value){}}''',
    "model.task.antForest.AntForestRpcCall": '''import java.util.*;import org.json.*;
        import io.github.aw1y2z.sesame.util.*;public class AntForestRpcCall {
        public static Deque<String> starts=new ArrayDeque<>();public static List<Long> hits=new ArrayList<>();
        public static List<String> settled=new ArrayList<>();public static int startCalls,settleCalls;public static boolean rejectSettlement;
        public static long startedAt,settledAt;public static boolean cancelStart;
        public static String startWhackMole(String source){startCalls++;startedAt=System.currentTimeMillis();
            if(cancelStart)throw new TaskCancelledException();return starts.removeFirst();}
        public static String startWhackMole(){return startWhackMole("source");}
        public static String whackMole(long id,String token,String source){assert token.equals("private-token");hits.add(id);return "{success:true}";}
        public static String settlementWhackMole(String token,List<String> ids,String source){
            settled=new ArrayList<>(ids);settleCalls++;settledAt=System.currentTimeMillis();
            return rejectSettlement?"{success:false,resultCode:'FAIL',token:'private-token'}":"{success:true,totalEnergy:7}";}
        public static String oldstartWhackMole(String source){return startWhackMole(source);}
        public static String oldwhackMole(long id,String token,String source){return whackMole(id,token,source);}
        public static String oldsettlementWhackMole(String token,List<String> ids,String source){return settlementWhackMole(token,ids,source);}
        public static String closeWhackMole(){return "{success:true}";}}''',
}

test = r'''
package io.github.aw1y2z.sesame.model.task.antForest;
import java.util.*;import java.util.concurrent.atomic.AtomicLong;import org.json.*;
import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class UpstreamForestCheck {
    static String round(){return new JSONObject().put("success",true).put("canPlayToday",true).put("token","private-token")
        .put("config",new JSONObject().put("duration",1).put("intervalTime",10))
        .put("moleInfo",new JSONArray().put(new JSONObject().put("id",91).put("type","NORMAL"))
            .put(new JSONObject().put("id",304).put("bubbleId",8)).put(new JSONObject().put("id",304).put("bubbleId",8))
            .put(new JSONObject().put("id",705).put("type","BOMB")).put(new JSONObject().put("id",-1))).toString();}
    static void reset(){Status.flags.clear();Status.counts.clear();Status.beforeFlag=null;Log.messages.clear();Statistics.collected=0;
        AntForestRpcCall.starts.clear();AntForestRpcCall.hits.clear();AntForestRpcCall.settled.clear();
        AntForestRpcCall.startCalls=AntForestRpcCall.settleCalls=0;
        AntForestRpcCall.cancelStart=AntForestRpcCall.rejectSettlement=TimeUtil.cancel=false;WhackMole.setTotalGames(1);}
    static void whack(){
        reset();AntForestRpcCall.starts.add(round());Status.beforeFlag=()->WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
        WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
        assert AntForestRpcCall.hits.equals(List.of(304L)):AntForestRpcCall.hits;
        assert AntForestRpcCall.settled.equals(List.of("91")):AntForestRpcCall.settled;
        assert AntForestRpcCall.settledAt-AntForestRpcCall.startedAt>=1000:"settled before server duration";
        assert Statistics.collected==7&&Status.hasFlagToday("forest::whackMole::executed");
        WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);assert AntForestRpcCall.startCalls==1;
        reset();for(int i=0;i<3;i++)AntForestRpcCall.starts.add("{}");
        for(int i=0;i<5;i++)WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
        assert AntForestRpcCall.startCalls==3&&!Status.hasFlagToday("forest::whackMole::executed");
        for(JSONObject bad:List.of(new JSONObject(round()).put("token",true),new JSONObject(round()).put("canPlayToday","false"),
                new JSONObject(round()).put("config",new JSONObject().put("duration",Long.MAX_VALUE)),
                new JSONObject(round()).put("config",new JSONObject().put("duration",1).put("intervalTime",Long.MAX_VALUE)),
                new JSONObject(round()).put("config",new JSONObject().put("duration",true)))){
            reset();AntForestRpcCall.starts.add(bad.toString());WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
            assert AntForestRpcCall.hits.isEmpty()&&AntForestRpcCall.settleCalls==0&&Status.flags.isEmpty();
        }
        reset();AntForestRpcCall.starts.add("{success:true,canPlayToday:false}");WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
        assert Status.hasFlagToday("forest::whackMole::executed")&&AntForestRpcCall.settleCalls==0;
        reset();AntForestRpcCall.starts.add(round());TimeUtil.cancel=true;
        try{WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
        assert Status.flags.isEmpty()&&Status.counts.isEmpty()&&AntForestRpcCall.settleCalls==0;
        TimeUtil.cancel=false;AntForestRpcCall.starts.add(round());WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);
        assert Status.hasFlagToday("forest::whackMole::executed"):"cancel did not reset running state";
        reset();AtomicLong generation=new AtomicLong(1);RunGeneration prior=RunGeneration.bind(0,generation::get);
        try{WhackMole.startSuspend(WhackMole.Mode.AGGRESSIVE);throw new AssertionError("stale generation admitted");}
        catch(TaskCancelledException expected){}finally{RunGeneration.restore(prior);}
        assert AntForestRpcCall.startCalls==0&&Status.flags.isEmpty()&&Status.counts.isEmpty();
        reset();AntForestRpcCall.starts.add(round());WhackMole.start(WhackMole.Mode.AGGRESSIVE);
        assert TaskLifecycle.freezeIfIdle()==null:"queued game escaped lifecycle admission";
        long deadline=System.currentTimeMillis()+5000;while(!TaskLifecycle.isIdle()&&System.currentTimeMillis()<deadline)TimeUtil.sleep(10);
        assert TaskLifecycle.isIdle()&&Status.hasFlagToday("forest::whackMole::executed");
        for(String message:Log.messages)assert !message.contains("private-token");
        WhackMole.shutdown();
        System.out.println("PASS whack server ids/duration, invalid fields, exhausted/retry cap, stale/cancel and queued lifecycle admission");
    }
    static class Rain {
        static final String TAG="rain";static int totalCollected,settlements,starts,homes;
        static Deque<String> responses=new ArrayDeque<>();static boolean reject,continuous;
        static class TimeUtil {static void sleep(long ms){}}
        static class AntForestRpcCall {
            static String startEnergyRain(){starts++;return responses.removeFirst();}
            static String energyRainSettlement(int energy,String token){assert !token.isEmpty();settlements++;
                return reject?"{resultCode:'FAIL'}":"{resultCode:'SUCCESS'}";}
            static String queryEnergyRainHome(){homes++;return "{resultCode:'SUCCESS',canPlayToday:"+(continuous||!responses.isEmpty())+"}";}
        }
        @@RAIN@@
        static String receipt(int sum){return "{resultCode:'SUCCESS',token:'private-token',difficultyInfo:{bubbleEnergyList:["+sum+"]}}";}
        static void reset(){Status.flags.clear();Statistics.collected=0;totalCollected=settlements=starts=homes=0;responses.clear();reject=continuous=false;}
        static void check(){Rain rain=new Rain();
            reset();responses.add(receipt(50));reject=true;assert rain.startEnergyRain()==-1&&Status.flags.isEmpty()&&totalCollected==0;
            responses.add(receipt(50));reject=false;assert rain.startEnergyRain()==50&&Status.hasFlagToday("EnergyRain::PlayGame")&&totalCollected==50;
            for(String invalid:List.of("{}","{resultCode:'SUCCESS',token:'x'}",
                    "{resultCode:'SUCCESS',token:true,difficultyInfo:{bubbleEnergyList:[50]}}",
                    "{resultCode:'SUCCESS',token:'x',difficultyInfo:{bubbleEnergyList:['bad']}}",
                    "{resultCode:'SUCCESS',token:'x',difficultyInfo:{bubbleEnergyList:[2147483647,1]}}")){
                reset();responses.add(invalid);assert rain.startEnergyRain()==-1&&settlements==0&&Status.flags.isEmpty();
            }
            reset();for(int sum:List.of(120,70,50))responses.add(receipt(sum));
            assert rain.drainEnergyRainQuota()==3&&settlements==3&&totalCollected==240&&Status.hasFlagToday("EnergyRain::PlayGame");
            reset();continuous=true;for(int i=0;i<8;i++)responses.add(receipt(1));
            assert rain.drainEnergyRainQuota()==6&&settlements==6&&responses.size()==2:"same-run quota must stay bounded";
            System.out.println("PASS rain quota draining, overflow/malformed guards and settlement-confirmed completion");
        }
    }
    static class Home {
        static final String TAG="home";static String response;static int offsets;
        static class Average {int nextInteger(int value){offsets++;return value;}}
        static final Average offsetTimeMath=new Average();
        static class AntForestRpcCall {static String queryHomePage(){return response;}
            static String queryFriendHomePage(String uid){return response;}}
        static class AntForestTeam {static boolean isTeam(JSONObject home){return home.optJSONObject("teamHomeResult")!=null;}}
        boolean isRevivedSelfQuietTime(){return false;}
        @@HOME@@
        static void check(){Home home=new Home();offsets=0;
            for(String invalid:List.of("bad","{}","{resultCode:'FAIL',now:1}")){
                response=invalid;assert home.querySelfHome()==null&&home.queryFriendHome("friend")==null&&offsets==0;
            }
            response="{resultCode:'SUCCESS'}";assert home.querySelfHome()!=null&&home.queryFriendHome("friend")!=null&&offsets==0;
            response=new JSONObject().put("resultCode","SUCCESS").put("now",System.currentTimeMillis())
                .put("teamHomeResult",new JSONObject().put("mainMember",new JSONObject().put("bubbles",new JSONArray())
                    .put("usingUserProps",new JSONArray()))).toString();
            assert home.querySelfHome().optJSONArray("usingUserPropsNew")!=null&&home.queryFriendHome("friend")!=null&&offsets==2;
            System.out.println("PASS home success validation, empty parse rejection, valid server clock and team normalization");
        }
    }
    static void expectCancel(Runnable action){try{action.run();throw new AssertionError("forest cancellation swallowed");}catch(TaskCancelledException expected){}}
    static class Gifts {
        static final String TAG="gifts";static Deque<String> inventories=new ArrayDeque<>();
        static int queries,gives,waits,cancelWait;static boolean unique,cancelQuery,cancelGive;static String giftReply;
        static class UserIdMap {static String getMaskName(String uid){return "friend";}}
        static class AntForestRpcCall {
            static String queryPropList(boolean onlyGive){
                assert onlyGive;queries++;if(cancelQuery)throw new TaskCancelledException();
                if(queries>110)throw new AssertionError("unbounded gift loop");
                return unique?inventory("p"+queries,2):inventories.size()>1?inventories.removeFirst():inventories.peekFirst();
            }
            static String giveProp(String config,String prop,String uid){
                assert config.equals("config")&&!prop.isEmpty()&&uid.equals("friend");gives++;
                if(cancelGive)throw new TaskCancelledException();return giftReply;
            }
        }
        static class TimeUtil {static void sleep(long ms){assert ms==1500;waits++;if(waits==cancelWait||RunGeneration.isStale())throw new TaskCancelledException();}}
        @@GIVE@@
        @@GIVE_NUMBER@@
        static JSONObject prop(String id,int count){return new JSONObject().put("giveConfigVO",new JSONObject().put("giveConfigId","config"))
            .put("propConfigVO",new JSONObject().put("propName","card")).put("propIdList",new JSONArray().put(id)).put("holdsNum",count);}
        static String inventory(String id,int count){return new JSONObject().put("resultCode","SUCCESS").put("forestPropVOList",new JSONArray().put(prop(id,count))).toString();}
        static void reset(){inventories.clear();queries=gives=waits=cancelWait=0;unique=cancelQuery=cancelGive=false;giftReply="{resultCode:'SUCCESS'}";Log.messages.clear();}
        static void check(){Gifts gifts=new Gifts();
            reset();inventories.add(inventory("stack",2));gifts.giveProp("friend");
            assert queries==2&&gives==1:"unchanged gift inventory was resent: "+queries+" queries / "+gives+" gifts";
            assert Log.messages.stream().anyMatch(s->s.contains("库存未推进"));
            reset();inventories.add(inventory("stack",2));inventories.add(inventory("stack",1));gifts.giveProp("friend");
            assert queries==2&&gives==2:"a decreasing stack must continue";
            reset();inventories.add(inventory("a",2));inventories.add(inventory("b",2));inventories.add(inventory("a",2));gifts.giveProp("friend");
            assert queries==3&&gives==2:"alternating cached ids escaped progress guard";
            reset();inventories.add(inventory("stack",2));inventories.add(inventory("stack",3));gifts.giveProp("friend");assert queries==2&&gives==1;
            reset();unique=true;gifts.giveProp("friend");assert queries==100&&gives==100:"changing inventory escaped round limit";
            assert Log.messages.stream().anyMatch(s->s.contains("100")&&s.contains("上限"));
            reset();inventories.add("{resultCode:'SUCCESS',forestPropVOList:[]}");gifts.giveProp("friend");assert queries==1&&gives==0;
            reset();inventories.add("{resultCode:'FAIL'}");gifts.giveProp("friend");assert queries==1&&gives==0;
            reset();inventories.add(inventory("stack",2));giftReply="{resultCode:'FAIL'}";gifts.giveProp("friend");assert queries==1&&gives==1;
            for(JSONObject invalid:List.of(prop("",2),prop("p",0),prop("p",2).put("holdsNum",true),prop("p",2).put("holdsNum",1.5),
                    prop("p",2).put("propIdList",new JSONArray().put(true)),prop("p",2).put("giveConfigVO",new JSONObject().put("giveConfigId",true)))){
                reset();inventories.add(new JSONObject().put("resultCode","SUCCESS").put("forestPropVOList",new JSONArray().put(invalid)).toString());
                gifts.giveProp("friend");assert gives==0:"invalid gift identity/stock sent an RPC";
            }
            reset();inventories.add(inventory("stack",2));cancelQuery=true;expectCancel(()->gifts.giveProp("friend"));assert queries==1&&gives==0;
            reset();inventories.add(inventory("stack",2));cancelGive=true;expectCancel(()->gifts.giveProp("friend"));assert queries==1&&gives==1;
            reset();inventories.add(inventory("stack",2));cancelWait=1;expectCancel(()->gifts.giveProp("friend"));assert queries==1&&gives==1;
            reset();inventories.add(inventory("stack",2));AtomicLong generation=new AtomicLong(1);RunGeneration prior=RunGeneration.bind(0,generation::get);
            try{expectCancel(()->gifts.giveProp("friend"));assert queries==0&&gives==0:"stale gift run sent a request";}finally{RunGeneration.restore(prior);}
            System.out.println("PASS gift unchanged/cycling stock, decreasing stack, hard cap, invalid fields and query/send/wait cancellation");
        }
    }
    static class Watering {
        static final String TAG="water";static int queries,transfers,waits,cancelWait;static boolean cancelQuery,cancelTransfer,failFirstQuery;static String transferReply;
        static class Field<T>{final T value;Field(T value){this.value=value;}T getValue(){return value;}}
        static class Host {String selfId="self";Field<Integer> waterFriendType=new Field<>(1);Field<Boolean> waterFriendEnergySendChat=new Field<>(false);
            Field<Map<String,Integer>> waterFriendList=new Field<>(new LinkedHashMap<>()),waterFriendGramList=new Field<>(new HashMap<>());}
        final Host host=new Host();
        static class WaterFriendType {static int[] waterEnergy={0,10,18,33,66};}
        static class KVNode<K,V>{final K key;final V value;KVNode(K key,V value){this.key=key;this.value=value;}K getKey(){return key;}V getValue(){return value;}}
        static class UserIdMap {static String getCurrentUid(){return "self";}static String getMaskName(String id){return id;}static String getShowName(String id){return id;}}
        static class Statistics {enum DataType{WATERINGCOUNT,WATERED}static void addData(DataType type,int n){}}
        static class Status {static Map<String,Integer> watered=new HashMap<>();static int getWaterFriendToday(String id){return watered.getOrDefault(id,0);}
            static void waterFriendToday(String id,int n,String taskUid){assert taskUid.equals("self");watered.merge(id,n,Integer::sum);}static void wateringFriendToday(String id){}}
        static class AntForestRpcCall {
            static String queryFriendHomePage(String id){queries++;if(cancelQuery)throw new TaskCancelledException();if(failFirstQuery&&queries==1)throw new IllegalStateException("transport");return "{resultCode:'SUCCESS',bizNo:'biz'}";}
            static String transferEnergy(String id,String biz,int energyId,String chat){transfers++;if(cancelTransfer)throw new TaskCancelledException();return transferReply;}
        }
        static class TimeUtil {static void sleep(long ms){waits++;if(waits==cancelWait||RunGeneration.isStale())throw new TaskCancelledException();}}
        @@WATER@@
        void reset(){host.waterFriendList.value.clear();host.waterFriendList.value.put("a",2);host.waterFriendList.value.put("b",1);Status.watered.clear();
            queries=transfers=waits=cancelWait=0;cancelQuery=cancelTransfer=failFirstQuery=false;transferReply="{resultCode:'SUCCESS',userBaseInfo:{currentEnergy:100}}";}
        static void check(){Watering water=new Watering();
            water.reset();water.waterFriendEnergy();assert queries==2&&transfers==3&&Status.watered.equals(Map.of("a",2,"b",1));
            water.waterFriendEnergy();assert queries==2&&transfers==3:"configured counts were watered twice";
            water.reset();cancelQuery=true;expectCancel(water::waterFriendEnergy);assert queries==1&&transfers==0;
            water.reset();cancelWait=1;expectCancel(water::waterFriendEnergy);assert queries==1&&transfers==0:"cancelled homepage wait reached the next friend";
            water.reset();cancelTransfer=true;expectCancel(water::waterFriendEnergy);assert queries==1&&transfers==1;
            water.reset();cancelWait=2;expectCancel(water::waterFriendEnergy);assert queries==1&&transfers==1:"cancelled transfer wait reached the next friend";
            water.reset();cancelWait=3;expectCancel(water::waterFriendEnergy);assert queries==1&&transfers==2;
            assert Status.watered.equals(Map.of("a",1)):"cancellation discarded previously confirmed watering quota";
            water.reset();cancelWait=1;expectCancel(()->water.returnFriendWater("a","biz",3,10));assert transfers==1;
            water.reset();cancelWait=2;expectCancel(()->water.returnFriendWater("a","biz",3,10));assert transfers==2&&Status.watered.isEmpty():"automatic return watering consumed configured quota";
            water.reset();cancelTransfer=true;expectCancel(()->water.returnFriendWater("a","biz",3,10));assert transfers==1;
            water.reset();failFirstQuery=true;water.waterFriendEnergy();assert queries==2&&transfers==1:"ordinary friend failure stopped unrelated friends";
            water.reset();transferReply="{resultCode:'ENERGY_INSUFFICIENT'}";water.waterFriendEnergy();assert queries==1&&transfers==1;
            System.out.println("PASS watering counts/confirmed quota on cancel, query/transfer/wait cancellation, automatic return separation and ordinary failure");
        }
    }
    public static void main(String[] args){whack();Rain.check();Home.check();Gifts.check();Watering.check();}
}
'''
game = "model/task/antForest/AntForestGame.java"
rain_methods = "\n".join(method(game, signature) for signature in (
    "int startEnergyRain(", "private static String energyRainLabel(", "private int drainEnergyRainQuota("))
rain_methods = rain_methods.replace("AntForestV2.totalCollected", "totalCollected")
test = test.replace("@@RAIN@@", rain_methods)
home_methods = "\n".join(method("model/task/antForest/AntForestEnergy.java", signature)
                         for signature in ("JSONObject querySelfHome(", "JSONObject queryFriendHome("))
test = test.replace("@@HOME@@", home_methods.replace("AntForestV2.offsetTimeMath", "offsetTimeMath"))
test = test.replace("@@GIVE@@", method("model/task/antForest/AntForestProp.java", "void giveProp(String targetUserId)"))
test = test.replace("@@GIVE_NUMBER@@", method("model/task/antForest/AntForestV2.java", "static long forestFeatureLong("))
test = test.replace("@@WATER@@", "\n".join(method("model/task/antForest/AntForestWater.java", signature) for signature in (
    "void waterFriendEnergy(", "KVNode<Integer, Boolean> returnFriendWater(", "private KVNode<Integer, Boolean> returnFriendWater(",
    "int getEnergyId(", "int friendWaterEnergy(")))

with tempfile.TemporaryDirectory(prefix="sesame-upstream-nov16-forest-") as temp:
    out = Path(temp)
    for relative, body in stubs.items():
        qualified = package + "." + relative
        path = out / (qualified.replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package " + qualified.rsplit(".", 1)[0] + ";\n" + body, encoding="utf-8")
    for relative in ("model/task/antForest/WhackMole", "data/task/TaskLifecycle", "util/RunGeneration", "util/TaskCancelledException"):
        target = out / (package.replace(".", "/") + "/" + relative + ".java")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text((SOURCE / (relative + ".java")).read_text(encoding="utf-8"), encoding="utf-8")
    (out / "UpstreamForestCheck.java").write_text(test, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", temp]
                   + [str(path) for path in out.rglob("*.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp + os.pathsep + str(jar),
                    package + ".model.task.antForest.UpstreamForestCheck"], check=True, timeout=30)

forest = (SOURCE / "model/task/antForest/AntForestV2.java").read_text(encoding="utf-8")
for gate in ("!energyOnly && waterFriendEnergyFirst.getValue()", "!energyOnly && ecoLife.getValue()",
             "!energyOnly && youthPrivilege.getValue()", "if (!energyOnly) propHelper.continuousUseCardOptions()",
             "!energyOnly && forestTaskGroup.getValue() && forestSign.getValue()"):
    assert gate in forest, gate
assert not (SOURCE / "model/task/antForest/ReadForestTask.java").exists()
assert "import io.github.aw1y2z.sesame.model.task.forestRead.ReadForestTask;" in forest
print("PASS energy-only ancillary task gates and M reading module boundary")
