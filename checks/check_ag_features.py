"""Compile actual AG migration flows/RPC builders; simulate state changes and uncertain replies, no device/network."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import SOURCE, method

jar = sorted((Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) /
              "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar"))[-1]
forest = "model/task/antForest/AntForestV2.java"
farm = "model/task/antFarm/AntFarm.java"
orchard = "model/task/antOrchard/AntOrchard.java"

def methods(path, names):
    return "\n".join(method(path, name) for name in names)

def rpc_methods(path, names):
    raw = (SOURCE / path).read_text(encoding="utf-8")
    pattern = r"    public static String (?:" + "|".join(names) + r")\([^\n]+"
    return "\n".join(method(path, sig) for sig in re.findall(pattern, raw)).replace(
        "io.github.aw1y2z.sesame.util.MyUtils", "MyUtils")

code = r'''
import org.json.*; import java.util.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class AGCheck {
    static JSONObject ok(){return new JSONObject().put("success",true).put("resultCode","SUCCESS").put("memo","SUCCESS");}
    static class MyUtils {
        static JSONObject newJSONObject(){return new JSONObject();}
        static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}
        static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.set(2026,9,6,12,0,0);return c;}
    }
    static class Log {
        static List<String> lines=new ArrayList<>();
        static void record(String s){lines.add(s);} static void farm(String s){lines.add(s);} static void forest(String s){lines.add(s);}
        static void err(String a,String b,Throwable t){throw new AssertionError(b,t);}
    }
    static class RpcFailurePolicy {static String boundedMessage(String s){return s;}}
    static class RpcRequestGuard {@@GUARD@@}
    static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
    static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
    static class RandomUtil {static String getRandomString(int n){return "token";}}
    static class UserIdMap {static String getCurrentUid(){return "self";}static String getMaskName(String s){return "friend";}}
    static class TimeUtil {static boolean cancel;static List<Long> waits=new ArrayList<>();static void sleep(long ms){if(cancel)throw new TaskCancelledException();waits.add(ms);}}
    static class Status {
        static Set<String> flags=new HashSet<>();static Map<String,Integer> watered=new HashMap<>();
        static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}
        static void clearFlag(String s){flags.remove(s);}static int getWaterFriendToday(String s){return watered.getOrDefault(s,0);}
        static void waterFriendToday(String id,int n,String uid){watered.merge(id,n,Integer::sum);}
    }
    static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
    static class KVNode<A,B> {A a;B b;KVNode(A x,B y){a=x;b=y;}A getKey(){return a;}B getValue(){return b;}}
    static class ApplicationHook {
        static Map<String,Deque<String>> responses=new HashMap<>();static List<JSONObject> calls=new ArrayList<>();
        static String requestString(String op,String raw,int... ignored){
            assert !TaskLifecycle.isIdle():"RPC outside lifecycle scope";
            JSONObject args=new JSONArray(raw).optJSONObject(0);assert args!=null;
            String name=op.substring(op.lastIndexOf('.')+1);calls.add(new JSONObject().put("op",name).put("body",args));
            Deque<String> q=responses.get(name);return q==null?ok().toString():q.size()>1?q.removeFirst():q.peekFirst();
        }
    }
    static class AntForestRpcCall {static final String VERSION="test";@@FORESTRPC@@}
    static class AntFarmRpcCall {static final String VERSION="test";static String farmId2UserId(String s){return "self";}@@FARMRPC@@}
    static class AntOrchardRpcCall {static final String VERSION="test";@@ORCHARDRPC@@}
    static class AntOrchardTaskListMap {
        static Map<String,String> values=new HashMap<>();static int saves;
        static String get(String key){return values.get(key);}static void add(String key,String value){values.put(key,value);}
        static boolean save(){saves++;return true;}
    }
    static class WaterFriendType {static int[] waterEnergy={0,10,18,33,66};}
    static class Forest {
        static String TAG="Forest";String selfId="self";
        Field<Integer> waterFriendType=new Field<>(1);
        Field<Map<String,Integer>> waterFriendGramList=new Field<>(new HashMap<>()),waterFriendList=new Field<>(new HashMap<>());
        List<Integer> grams=new ArrayList<>();
        KVNode<Integer,Boolean> returnFriendWater(String uid,String biz,int count,int gram){grams.add(gram);return new KVNode<>(count,true);}
        @@FOREST@@
    }
    static class Farm {
        static String TAG="Farm";String ownerFarmId="farm";
        Field<Integer> dollSupplementOrder=new Field<>(0),collectChickenDiary=new Field<>(1);
        @@FARM@@
    }
    static class Orchard {
        static String TAG="Orchard";Field<Set<String>> AntOrchardTaskList=new Field<>(new HashSet<>());
        @@ORCHARD@@
    }
    static void reset(){ApplicationHook.responses.clear();ApplicationHook.calls.clear();Status.flags.clear();Status.watered.clear();Log.lines.clear();TimeUtil.waits.clear();TimeUtil.cancel=false;AntOrchardTaskListMap.values.clear();AntOrchardTaskListMap.saves=0;}
    static void replies(String op,JSONObject... values){Deque<String> q=new ArrayDeque<>();for(JSONObject v:values)q.add(v.toString());ApplicationHook.responses.put(op,q);}
    static List<JSONObject> calls(String op){List<JSONObject> list=new ArrayList<>();for(JSONObject c:ApplicationHook.calls)if(op.equals(c.optString("op")))list.add(c.optJSONObject("body"));return list;}
    static boolean logged(String part){return Log.lines.stream().anyMatch(s->s.contains(part));}
    static JSONObject creature(String id,String state,int remain){return new JSONObject().put("creatureCode",id).put("status",state).put("robEnergyVO",new JSONObject().put("robRemainDays",remain).put("maxWorkDays",7).put("alreadyWorkDays",0));}
    static JSONObject entry(boolean using,int remain){return ok().put("usingMonopolyCreature",using).put("creatureList",new JSONArray().put(creature("old","using",remain)).put(creature("new\"one","idle",5)));}
    static JSONObject plant(long total,long water){return ok().put("resultData",new JSONObject().put("plantChannelResponse",new JSONObject().put("access",true))
        .put("plantInfo",new JSONObject().put("status","in_progress").put("plantInfoMap",new JSONObject()
        .put("WATER",new JSONObject().put("value",water)).put("WATERING_POT",new JSONObject().put("extInfo",new JSONObject().put("costPerWater",10).put("availablePots","[\"one\",\"five\"]")))
        .put("PLANT_PROGRESS_V2",new JSONObject().put("extInfo",new JSONObject().put("totalWaterG",total))))));}
    static JSONObject cert(int n){return ok().put("applyAction","AVAILABLE").put("currentEnergy",100).put("exchangeableTree",new JSONObject().put("projectId",7).put("certCount",n).put("energy",20));}
    static void forestChecks(){
        Forest f=new Forest();reset();assert f.isMonopolyCreatureExpired(creature("a","using",0));
        assert !f.isMonopolyCreatureExpired(new JSONObject());assert !f.isMonopolyCreatureExpired(null);
        JSONObject invalid=creature("a","using",1);invalid.optJSONObject("robEnergyVO").put("robRemainDays","bad");assert !f.isMonopolyCreatureExpired(invalid);
        invalid.optJSONObject("robEnergyVO").put("robRemainDays",-0.5);assert !f.isMonopolyCreatureExpired(invalid);
        invalid.optJSONObject("robEnergyVO").put("robRemainDays",2).put("alreadyWorkDays",7);assert f.isMonopolyCreatureExpired(invalid);
        for(Object n:List.of(true,"bad",1.5,"9223372036854775808",new JSONObject()))assert Forest.forestFeatureLong(new JSONObject().put("n",n),"n")==-1;
        assert Forest.forestFeatureLong(new JSONObject().put("n","0018"),"n")==18;
        reset();replies("queryMonopolyEntryInfo",entry(true,2));assert f.dispatchMonopolyAnimal(true);assert calls("assignMonopolyCreature").isEmpty();
        reset();replies("queryMonopolyEntryInfo",entry(true,0));
        replies("assignMonopolyCreature",new JSONObject().put("resultCode","MONOPOLY_CREATURE_USE_CONFIRM"),ok());
        replies("queryUsingCreatureInfo",ok().put("usingMonopolyCreature",true).put("userCreatureVO",new JSONObject().put("creatureCode","new\"one")));
        assert f.dispatchMonopolyAnimal(true);assert calls("assignMonopolyCreature").size()==2;
        assert !calls("assignMonopolyCreature").get(0).optBoolean("secondConfirm")&&calls("assignMonopolyCreature").get(1).optBoolean("secondConfirm");
        assert calls("assignMonopolyCreature").get(1).optString("source").equals("monopoly_home_animal")&&logged("到期替换成功");
        reset();replies("queryMonopolyEntryInfo",entry(false,2));replies("assignMonopolyCreature",new JSONObject().put("resultCode","ANIMAL_PROP_IN_USE_CONFIRM"));
        assert f.dispatchMonopolyAnimal(true);assert calls("assignMonopolyCreature").size()==1&&logged("占用状态变化");
        reset();replies("queryMonopolyEntryInfo",ok());assert f.dispatchMonopolyAnimal(true);assert calls("assignMonopolyCreature").isEmpty();
        reset();assert !f.dispatchMonopolyAnimal(false);assert ApplicationHook.calls.isEmpty();
        reset();f.waterFriendList.value.put("a",3);f.waterFriendGramList.value.put("a",66);Status.watered.put("a",2);
        replies("queryFriendHomePage",ok().put("bizNo","biz"));f.waterFriendEnergy();assert f.grams.equals(List.of(66))&&Status.watered.get("a")==3;
        f.waterFriendEnergy();assert f.grams.size()==1;f.waterFriendGramList.value.put("a",11);assert f.friendWaterEnergy("a",10)==0;
        f.waterFriendGramList.value.put("a",0);assert f.friendWaterEnergy("a",18)==18;f.waterFriendType.value=0;f.waterFriendEnergy();assert f.grams.size()==1;
        reset();replies("memberForestSignin",plant(0,50),plant(5,0));f.waterMemberPlant();assert calls("plantAward").size()==1&&calls("plantAward").get(0).optString("awardSpeed").equals("five")&&logged("浇水成功");
        reset();replies("memberForestSignin",plant(0,50));f.waterMemberPlant();assert calls("plantAward").size()==1&&logged("未确认");
        reset();replies("memberForestSignin",new JSONObject());f.waterMemberPlant();assert calls("plantAward").isEmpty()&&logged("查询失败");
        reset();JSONObject e=ok().put("displayInfo",new JSONObject().put("mapDisplay",new JSONObject().put("protectionGuideDisplay",new JSONObject().put("redirectUrl","https://example.test?url=https%3A%2F%2Fexample.test%3FprojectId%3D7"))));
        replies("queryMonopolyEntryInfo",e);replies("queryTreeForExchange",cert(0),cert(1));f.exchangeMonopolyCertificate();assert calls("exchangeTree").size()==1&&logged("兑换成功");
        f.exchangeMonopolyCertificate();assert calls("exchangeTree").size()==1;
        reset();replies("queryMonopolyEntryInfo",e);replies("queryTreeForExchange",cert(0));f.exchangeMonopolyCertificate();f.exchangeMonopolyCertificate();assert calls("exchangeTree").size()==1&&logged("避免重复扣能量");
        reset();replies("queryMonopolyEntryInfo",e);replies("queryTreeForExchange",cert(0).put("currentEnergy",1));f.exchangeMonopolyCertificate();assert calls("exchangeTree").isEmpty();
        reset();JSONObject fractional=cert(0);fractional.optJSONObject("exchangeableTree").put("energy",0.5);replies("queryMonopolyEntryInfo",e);replies("queryTreeForExchange",fractional);f.exchangeMonopolyCertificate();assert calls("exchangeTree").isEmpty();
        System.out.println("PASS forest: expiry/confirm/occupancy, independent grams/quota/off, plant progress, certificate balance and uncertain debit");
    }
    static JSONObject farmState(boolean fence){return ok().put("subFarmVO",new JSONObject().put("farmId","farm"))
        .put("buffInfoVO",new JSONObject().put("buffType","FENCE").put("hasBuffEffect",fence).put("buffCountDown",800));}
    static JSONObject tools(String type,int count){return ok().put("toolList",new JSONArray().put(new JSONObject().put("toolType",type).put("toolCount",count).put("toolId","tool\"id")));}
    static JSONObject doll(boolean acquired){return ok().put("dollInfoVO",new JSONObject().put("acquired",acquired).put("achievementId","achievement\"id").put("dollName","monthly"));}
    static JSONObject cabin(){JSONArray list=new JSONArray();for(int n=2022*12+9;n<2026*12+9;n++)list.put(new JSONObject().put("dollId",String.format(Locale.ROOT,"%04d_%02d_MONTH_DOLL",n/12,n%12+1)));return ok().put("loveCabinDollList",list);}
    static JSONObject diary(String day,boolean liked){return ok().put("data",new JSONObject().put("chickenDiary",new JSONObject().put("diaryId",day.isEmpty()?"today":day).put("diaryDateStr",day).put("collectStatus",liked)));}
    static void farmChecks() throws Exception {
        Farm f=new Farm();reset();replies("syncAnimalStatus",farmState(true));f.useFenceTool();assert calls("useFarmTool").isEmpty()&&logged("仍在生效");
        reset();replies("syncAnimalStatus",farmState(false),farmState(true));replies("listFarmTool",tools("FENCETOOL",1));f.useFenceTool();f.useFenceTool();assert calls("useFarmTool").size()==1&&logged("使用成功");
        reset();replies("syncAnimalStatus",farmState(false));replies("listFarmTool",tools("FENCETOOL",1));f.useFenceTool();f.useFenceTool();assert calls("useFarmTool").size()==1&&logged("避免重复消耗");
        reset();replies("syncAnimalStatus",new JSONObject());f.useFenceTool();assert calls("useFarmTool").isEmpty();
        reset();replies("listFarmTool",tools("DOLLTOOL",1));replies("queryLoveCabin",cabin());replies("queryAntfarmDoll",doll(false),doll(true));f.supplementDolls();
        assert calls("useFarmTool").size()==1&&calls("useFarmTool").get(0).optString("dollId").equals("2026_10_MONTH_DOLL")&&logged("成功获得");
        assert calls("useFarmTool").get(0).optString("achievementId").equals("achievement\"id");
        reset();replies("listFarmTool",tools("DOLLTOOL",1));replies("queryLoveCabin",cabin());replies("queryAntfarmDoll",doll(false));f.supplementDolls();f.supplementDolls();assert calls("useFarmTool").size()==1&&logged("停止重复补签");
        reset();replies("listFarmTool",tools("DOLLTOOL",0));f.supplementDolls();assert calls("queryAntfarmDoll").isEmpty();
        reset();f.dollSupplementOrder.value=1;JSONObject incomplete=cabin();JSONArray owned=incomplete.optJSONArray("loveCabinDollList");owned.remove(owned.length()-1);
        replies("listFarmTool",tools("DOLLTOOL",1));replies("queryLoveCabin",incomplete);replies("queryAntfarmDoll",doll(false),doll(true));f.supplementDolls();assert calls("useFarmTool").get(0).optString("dollId").equals("2026_10_MONTH_DOLL");f.dollSupplementOrder.value=0;
        reset();replies("queryChickenDiary",diary("",false),diary("",true));f.likeChickenDiaries();assert calls("collectChickenDiary").size()==1&&logged("今日处理完成");
        f.likeChickenDiaries();assert calls("collectChickenDiary").size()==1&&logged("今日所选范围已处理");
        reset();replies("queryChickenDiary",diary("",false));assert !f.likeChickenDiary("")&&logged("结果未确认");
        reset();assert !f.likeChickenDiary("2026\"invalid")&&ApplicationHook.calls.isEmpty();assert !Farm.farmFeatureOk(ok().put("resultCode","102"));
        reset();f.collectChickenDiary.value=3;replies("queryChickenDiary",diary("",true),diary("2026-10-01",false),diary("2026-10-01",true),diary("2026-09-01",false),diary("2026-09-01",true));
        replies("queryChickenDiaryList",ok().put("data",new JSONObject().put("hasPreviousMore",true).put("chickenDiaryBriefList",new JSONArray().put(new JSONObject().put("dateStr","2026-10-01")))),
            ok().put("data",new JSONObject().put("hasPreviousMore",false).put("chickenDiaryBriefList",new JSONArray().put(new JSONObject().put("dateStr","2026-09-01")))));
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));f.likeChickenDiaries();assert calls("collectChickenDiary").size()==2&&calls("queryChickenDiaryList").get(0).optString("queryMonthStr").equals("2026-10")&&calls("queryChickenDiaryList").get(1).optString("queryMonthStr").equals("2026-09");
        System.out.println("PASS farm: fence effect/repeat guard, doll budget/ownership/escaped targets, diary confirmation/GMT+8 history");
    }
    static JSONObject star(String id,String state){return new JSONObject().put("taskType",id).put("taskStatus",state).put("sceneCode","starScene").put("bizInfo",new JSONObject().put("title","star").toString());}
    static JSONObject starList(JSONObject task){return ok().put("taskInfos",new JSONArray().put(task));}
    static JSONObject browse(int count){return new JSONObject().put("taskId","70000").put("actionType","XLIGHT").put("taskStatus","TODO").put("rightsTimes",count).put("rightsTimesLimit",2)
        .put("taskDisplayConfig",new JSONObject().put("title","ad").put("targetUrl","alipays://platformapi/startapp?url=https%3A%2F%2Frender.alipay.com%2Fmulti-stage-task.html%3FspaceCodeFeeds%3Dspace%26iepTaskSceneCode%3Dscene%26iepTaskType%3D70000"));}
    static JSONObject regularList(JSONObject task){return ok().put("taskList",new JSONArray().put(task));}
    static JSONObject adEvent(){return new JSONObject().put("playingEventType","BROWSE").put("eventStep",2).put("order",1).put("rewardId",5);}
    static JSONObject adPage(JSONObject event,String cursor){return new JSONObject().put("resData",new JSONObject().put("playingResult",new JSONObject().put("playingBizId","biz").put("playingPageInfo",cursor)
        .put("eventRewardDetail",new JSONObject().put("eventRewardInfoList",event==null?new JSONArray():new JSONArray().put(event)))));}
    static void orchardChecks() throws Exception {
        Orchard o=new Orchard();reset();replies("listTask",starList(star("ORCHARD_NORMAL_STAR","TODO")),starList(star("ORCHARD_NORMAL_STAR","FINISHED")),starList(star("ORCHARD_NORMAL_STAR","RECEIVED")));
        o.orchardStarTasks();assert calls("finishTask").size()==1&&calls("triggerTbTask").size()==1&&logged("已完成并领奖");assert !calls("finishTask").get(0).has("userId")&&calls("finishTask").get(0).optString("source").equals("h5");
        reset();replies("listTask",starList(star("ORCHARD_NORMAL_STAR","TODO")));o.orchardStarTasks();assert calls("finishTask").size()==1&&logged("进度未推进");
        reset();replies("listTask",starList(star("ORCHARD_NCLY_STAR30s_NCMXY","TODO")));o.orchardStarTasks();assert calls("finishTask").isEmpty()&&calls("submitUserPlayDurationAction").isEmpty()&&logged("不支持完成");
        reset();JSONObject game=star("ORCHARD_NCLY_STAR30s_NCMXY","TODO").put("bizInfo",new JSONObject().put("type","nongchangleyuan").put("gameAppId","app\"id").put("source","gameSource").put("floatBallConfig",new JSONObject().put("floatBallDuration",30)).toString());
        replies("listTask",starList(game),starList(star("ORCHARD_NCLY_STAR30s_NCMXY","FINISHED")),starList(star("ORCHARD_NCLY_STAR30s_NCMXY","RECEIVED")));o.orchardStarTasks();
        assert calls("submitUserPlayDurationAction").get(0).optInt("playTime")==31&&TimeUtil.waits.contains(31000L);
        reset();o.AntOrchardTaskList.value.add("ORCHARD_NORMAL_STAR");replies("listTask",starList(star("ORCHARD_NORMAL_STAR","TODO")));o.orchardStarTasks();assert calls("finishTask").isEmpty();assert AntOrchardTaskListMap.get("ORCHARD_NORMAL_STAR").startsWith("努力流星｜")&&AntOrchardTaskListMap.saves==1;o.orchardStarTasks();assert AntOrchardTaskListMap.saves==1;o.AntOrchardTaskList.value.clear();
        reset();JSONObject visit=new JSONObject().put("taskId","ANTFARM_ORCHARD_NORMAL_GONGGEFANGWEN").put("taskStatus","TODO").put("taskDisplayConfig",new JSONObject().put("targetUrl","https://render.alipay.com?source=provided"));
        replies("orchardListTask",regularList(new JSONObject(visit.toString()).put("taskStatus","RECEIVED")));o.runExtraOrchardTask(visit,false);assert calls("orchardIndex").get(0).optString("source").equals("provided")&&logged("已完成并领奖");
        reset();replies("xlightPlugin",adPage(adEvent(),""));replies("orchardListTask",regularList(browse(1)),regularList(browse(2)));o.runExtraOrchardTask(browse(0),false);
        assert calls("finish").size()==2&&TimeUtil.waits.contains(2000L)&&logged("次数上限");assert calls("finish").get(0).optJSONObject("extendInfo").optString("iepTaskSceneCode").equals("scene");
        reset();replies("xlightPlugin",adPage(adEvent(),""));replies("orchardListTask",regularList(browse(0)));o.runExtraOrchardTask(browse(0),false);assert calls("finish").size()==1&&logged("进度未推进");
        reset();replies("xlightPlugin",adPage(null,"next"),adPage(adEvent(),""));replies("orchardListTask",regularList(browse(2)));o.runExtraOrchardTask(browse(0),false);assert calls("xlightPlugin").size()==2&&calls("finish").size()==1;
        reset();replies("xlightPlugin",adPage(null,"same"));o.runExtraOrchardTask(browse(0),false);assert calls("xlightPlugin").size()==2&&calls("finish").isEmpty()&&logged("分页重复");
        reset();replies("xlightPlugin",new JSONObject().put("error","TRAFFIC").put("errorMessage","limited"));o.runExtraOrchardTask(browse(0),false);assert calls("finish").isEmpty()&&logged("广告查询失败");
        assert !Orchard.extraOrchardAdvanced(browse(1),browse(0));assert !Orchard.extraOrchardAdvanced(browse(0),browse(0).put("rightsTimes",0.5));
        assert Orchard.extraOrchardAdvanced(browse(0).put("rightsTimes","0"),browse(1).put("rightsTimes","1")):"integer-string progress must advance";
        assert !Orchard.orchardExtraOk(ok().put("resultCode","102"));
        reset();TimeUtil.cancel=true;try{o.runExtraOrchardTask(browse(0),false);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert calls("finish").isEmpty();TimeUtil.cancel=false;
        System.out.println("PASS orchard: star query/finish/reward/contracts, blacklist, visit source, real ad events/paging, progress/limits/failure/cancellation");
    }
    public static void main(String[] args) throws Exception {
        try(TaskLifecycle.Work w=TaskLifecycle.enter()){assert w!=null;forestChecks();farmChecks();orchardChecks();}
        assert TaskLifecycle.isIdle();
    }
}
'''
tokens = {
    "@@GUARD@@": methods("rpc/intervallimit/RpcRequestGuard.java", ["    public static boolean isFailure(JSONObject result)", "    public static String errorMessage(JSONObject result)"]),
    "@@FOREST@@": methods(forest, ["    private int friendWaterEnergy(", "    private void waterFriendEnergy()", "    private boolean dispatchMonopolyAnimal(", "    private boolean isMonopolyCreatureExpired(", "    private boolean isMonopolyCreatureIdle(", "    static long forestFeatureLong(", "    private void waterMemberPlant()", "    private void exchangeMonopolyCertificate()"]),
    "@@FARM@@": methods(farm, ["    private static boolean farmFeatureOk(", "    private JSONObject findFeatureTool(", "    private void useFenceTool()", "    private void supplementDolls()", "    private void supplementDolls(int", "    private boolean likeChickenDiary(", "    private void likeChickenDiaries()"]),
    "@@ORCHARD@@": methods(orchard, ["    private static JSONObject orchardExtraDisplay(", "    private static String orchardUrlParam(", "    private static String orchardTaskSource(", "    private static boolean orchardExtraOk(", "    private static JSONObject orchardUnwrapExtra(", "    private static boolean isExtraOrchardBrowse(", "    private void orchardStarTasks()", "    private static int extraOrchardCount(", "    private static String extraOrchardSnapshot(", "    private static int extraOrchardStage(", "    private static boolean extraOrchardAdvanced(", "    private JSONObject refreshExtraOrchardTask(", "    private void runExtraOrchardTask(", "    private boolean reportOrchardStarGame(", "    private JSONObject performOrchardBrowse("]),
    "@@FORESTRPC@@": rpc_methods("model/task/antForest/AntForestRpcCall.java", ["queryMonopolyEntryInfo", "assignMonopolyCreature", "queryUsingCreatureInfo", "queryMonopolyCertificate", "exchangeMonopolyCertificate", "memberForestSignin", "plantAward", "queryFriendHomePage"]) + methods("model/task/antForest/AntForestRpcCall.java", ["    private static String monopolyPayload("]) + 'static final String MONOPOLY_SOURCE="monopoly_home_board";',
    "@@FARMRPC@@": rpc_methods("model/task/antFarm/AntFarmRpcCall.java", ["listFarmTool", "useFarmTool", "useDollTool", "queryAntfarmDoll", "queryLoveCabin", "syncAnimalStatus", "queryChickenDiary", "queryChickenDiaryList", "collectChickenDiary"]),
    "@@ORCHARDRPC@@": rpc_methods("model/task/antOrchard/AntOrchardRpcCall.java", ["orchardVisit", "listStarTasks", "finishStarTask", "orchardXlight", "finishOrchardBrowse", "submitUserPlayDurationAction", "triggerTbTask", "orchardListTask"]),
}
for token, value in tokens.items():
    code = code.replace(token, value)

with tempfile.TemporaryDirectory(prefix="sesame-ag-features-") as tmp:
    root = Path(tmp)
    uri = root / "android/net/Uri.java"
    uri.parent.mkdir(parents=True)
    uri.write_text(r'''package android.net; public final class Uri {
        final String raw; private Uri(String s){raw=s;} public static Uri parse(String s){return new Uri(s);}
        public boolean isHierarchical(){return raw.contains("://");}
        public String getQueryParameter(String key){int q=raw.indexOf('?');if(q<0)return null;String tail=raw.substring(q+1).split("#",2)[0];
            for(String p:tail.split("&")){String[] kv=p.split("=",2);if(java.net.URLDecoder.decode(kv[0],java.nio.charset.StandardCharsets.UTF_8).equals(key))return kv.length<2?"":java.net.URLDecoder.decode(kv[1],java.nio.charset.StandardCharsets.UTF_8);}return null;}
    }''', encoding="utf-8")
    cancel = root / "io/github/aw1y2z/sesame/util/TaskCancelledException.java"
    cancel.parent.mkdir(parents=True)
    cancel.write_text("package io.github.aw1y2z.sesame.util; public class TaskCancelledException extends RuntimeException {}", encoding="utf-8")
    java = root / "AGCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", tmp, str(java), str(uri), str(cancel), str(SOURCE / "data/task/TaskLifecycle.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp + os.pathsep + str(jar), "AGCheck"], check=True)
