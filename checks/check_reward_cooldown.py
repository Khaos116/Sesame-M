"""Regression checks for successful reward query intervals and retryable failures."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import textwrap


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java"


def write(base: Path, relative: str, source: str) -> None:
    path = base / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(source), encoding="utf-8")


def copy(base: Path, relative: str) -> None:
    path = base / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(JAVA / relative, path)


def compile_and_run(base: Path, main: str, classpath: str = "") -> None:
    sources = [str(p) for p in base.rglob("*.java")]
    command = ["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(base)]
    if classpath:
        command += ["-cp", classpath]
    subprocess.run(command + sources, check=True)
    runtime = str(base) + (os.pathsep + classpath if classpath else "")
    subprocess.run(["java", "-Xms16m", "-Xmx128m", "-ea", "-cp", runtime, main], check=True, timeout=30)


def check_reward_cooldown() -> None:
    with tempfile.TemporaryDirectory(prefix="sesame-reward-cooldown-") as directory:
        out = Path(directory)
        copy(out, "io/github/aw1y2z/sesame/model/task/rewardSupport/IsolatedRewardTask.java")
        copy(out, "io/github/aw1y2z/sesame/model/task/rewardSupport/RewardRunPolicy.java")
        copy(out, "io/github/aw1y2z/sesame/rpc/intervallimit/RequestBudgetPolicy.java")
        copy(out, "io/github/aw1y2z/sesame/rpc/intervallimit/RpcFailurePolicy.java")
        write(out, "io/github/aw1y2z/sesame/BuildConfig.java", """
            package io.github.aw1y2z.sesame; public final class BuildConfig { public static final String VERSION_NAME="check"; }
        """)
        write(out, "io/github/aw1y2z/sesame/data/ModelFields.java", """
            package io.github.aw1y2z.sesame.data; public final class ModelFields { public void addField(Object value) {} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/ModelGroup.java", """
            package io.github.aw1y2z.sesame.data; public enum ModelGroup { OTHER, MEMBER }
        """)
        write(out, "io/github/aw1y2z/sesame/data/RuntimeInfo.java", """
            package io.github.aw1y2z.sesame.data;
            import java.util.HashMap; import java.util.Map;
            public final class RuntimeInfo {
                private static final RuntimeInfo INSTANCE = new RuntimeInfo();
                private final Map<String,Object> values = new HashMap<>();
                public static RuntimeInfo getInstance() { return INSTANCE; }
                public long getLong(String key, long fallback) { Object v=values.get(key); return v instanceof Number ? ((Number)v).longValue() : fallback; }
                public String getString(String key) { Object v=values.get(key); return v == null ? null : String.valueOf(v); }
                public void put(String key, Object value) { values.put(key, value); }
                public void clearPrefix(String prefix) { values.keySet().removeIf(k -> k.startsWith(prefix)); }
                public void reset() { values.clear(); }
            }
        """)
        write(out, "io/github/aw1y2z/sesame/data/modelFieldExt/IntegerModelField.java", """
            package io.github.aw1y2z.sesame.data.modelFieldExt;
            public final class IntegerModelField { public IntegerModelField(String k,String n,int v,int min,int max) {} public int getValue(){return 6;} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/task/ModelTask.java", """
            package io.github.aw1y2z.sesame.data.task;
            import io.github.aw1y2z.sesame.data.ModelFields; import io.github.aw1y2z.sesame.data.ModelGroup;
            public abstract class ModelTask implements Runnable {
                public boolean isEnable(){return true;} public abstract String getName(); public abstract ModelGroup getGroup();
                public abstract ModelFields getFields(); public abstract Boolean check();
            }
        """)
        write(out, "io/github/aw1y2z/sesame/hook/ApplicationHook.java", """
            package io.github.aw1y2z.sesame.hook;
            public final class ApplicationHook { public static String response="{error:1009}"; public static int calls; public static String lastMethod,lastArgs; public static boolean fail;
                public static boolean isOffline(){return false;} public static String requestString(String m,String a,int r,int t) throws Exception {calls++;lastMethod=m;lastArgs=a; if(fail)throw new java.io.IOException(); return response;} }
        """)
        write(out, "io/github/aw1y2z/sesame/model/base/TaskCommon.java", """
            package io.github.aw1y2z.sesame.model.base; public final class TaskCommon { public static boolean IS_ENERGY_TIME=false; }
        """)
        write(out, "io/github/aw1y2z/sesame/util/Log.java", """
            package io.github.aw1y2z.sesame.util; public final class Log { public static String messages=""; public static void record(String value) {messages+=value;} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/MyUtils.java", """
            package io.github.aw1y2z.sesame.util; import org.json.JSONObject;
            public final class MyUtils { public static JSONObject newJSONObject(String raw) {
                try {return new JSONObject(raw);}catch(Exception invalid){return new JSONObject();}
            } }
        """)
        write(out, "io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", """
            package io.github.aw1y2z.sesame.util.idMap; public final class UserIdMap { public static String uid="A"; public static String getCurrentUid(){return uid;} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/modelFieldExt/BooleanModelField.java", """
            package io.github.aw1y2z.sesame.data.modelFieldExt;
            public final class BooleanModelField {public BooleanModelField(String k,String n,boolean v){} public boolean getValue(){return true;}}
        """)
        for file in ["dayDaySave/DayDaySave.java", "luckCard/LuckCardStatus.java", "weeklyWelfare/WeeklyWelfare.java", "weeklyWelfare/WeeklyWelfareFlow.java"]:
            copy(out, "io/github/aw1y2z/sesame/model/task/" + file)
        write(out, "io/github/aw1y2z/sesame/model/task/rewardSupport/RewardCooldownCheck.java", r"""
            package io.github.aw1y2z.sesame.model.task.rewardSupport;
            import io.github.aw1y2z.sesame.data.*; import io.github.aw1y2z.sesame.hook.ApplicationHook;
            public final class RewardCooldownCheck {
                private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
                private static final class Probe extends IsolatedRewardTask {
                    public String getName(){return "probe";} protected void addFields(ModelFields f){}
                    boolean claim, stopAfterQuery, unclearAfterQuery, noQuery, switchAfterQuery;
                    protected void execute(Run run)throws Exception {
                        if(noQuery)return;
                        if(claim)run.onceToday("claim","method","[]",()->true); else run.query("method","[]");
                        if(stopAfterQuery)throw new java.io.IOException();
                        if(unclearAfterQuery)run.stop("必要字段缺失");
                        if(switchAfterQuery)io.github.aw1y2z.sesame.util.idMap.UserIdMap.uid="B";
                    }
                }
                public static void main(String[] args) {
                    RuntimeInfo state=RuntimeInfo.getInstance(); Probe task=new Probe(); task.getFields(); long now=System.currentTimeMillis();
                    state.reset(); state.put("Probe.cooldownResetVersion","check");
                    state.put("Probe.nextQuery",now+23L*3_600_000L); state.put("Probe.cooldownUntil",now+60_000L);
                    require(task.check(),"obsolete pre-request interval or duplicate failure pause still blocks queries");
                    require(state.getLong("Probe.cooldownUntil",0L)==now+60_000L,"legacy state was deleted");
                    state.put("Probe.nextConfirmedQuery",now+60_000L);
                    require(!task.check(),"successful-query interval was ignored");
                    require(io.github.aw1y2z.sesame.util.Log.messages.contains("查询间隔"),"missing interval reason");

                    state.reset(); ApplicationHook.response="{\"success\":true}"; long successStart=System.currentTimeMillis();
                    ApplicationHook.calls=0; task.run();
                    require(state.getLong("Probe.nextConfirmedQuery",0L)>=successStart+5L*3_600_000L,"normal response did not schedule interval");
                    task.run(); require(ApplicationHook.calls==1,"successful queries ignored configured interval");

                    String[] rejected={"", "bad-json", "{\"success\":false,\"resultCode\":\"SYSTEM_ERROR\"}",
                        "{\"success\":false,\"resultCode\":\"BUSINESS_REJECTED\"}", "{\"error\":1009}",
                        "{\"success\":true,\"errorCode\":1009}", "{\"success\":true,\"retCode\":1009}"};
                    for(String raw:rejected){
                        state.reset(); ApplicationHook.response=raw; task.run();
                        require(state.getLong("Probe.nextConfirmedQuery",0L)==0L,"failed query scheduled normal interval: "+raw);
                        require(state.getLong("Probe.cooldownUntil",0L)==0L,"business layer added failure cooldown: "+raw);
                        require(task.check(),"failed query blocked next normal run: "+raw);
                    }
                    state.reset(); ApplicationHook.fail=true; task.run(); ApplicationHook.fail=false;
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L && task.check(),"transport failure scheduled interval");
                    ApplicationHook.response="{\"success\":true}"; task.stopAfterQuery=true; task.run(); task.stopAfterQuery=false;
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L,"failed later phase scheduled interval");
                    task.unclearAfterQuery=true; task.run(); task.unclearAfterQuery=false;
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L && task.check(),"unclear business response scheduled interval");
                    task.noQuery=true; task.run(); task.noQuery=false;
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L,"no RPC scheduled interval");
                    task.switchAfterQuery=true; task.run(); task.switchAfterQuery=false;
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L,"old-account interval written after switch");
                    io.github.aw1y2z.sesame.util.idMap.UserIdMap.uid="A";
                    state.reset(); task.claim=true; ApplicationHook.response="{\"error\":1009}"; task.run();
                    require(state.getString("Probe.attempt.claim")!=null,"uncertain mutation reservation was cleared");
                    require(state.getLong("Probe.nextConfirmedQuery",0L)==0L,"failed mutation scheduled interval");
                    state.reset(); ApplicationHook.response="{\"success\":true,\"result\":{\"token\":\"SECRET_TOKEN_VALUE\",\"userId\":\"SECRET_USER_VALUE\"}}";
                    var daily=new io.github.aw1y2z.sesame.model.task.dayDaySave.DayDaySave();daily.getFields();daily.run();
                    require(state.getLong("DayDaySave.nextConfirmedQuery",0L)==0L&&daily.check(),"missing sign state blocked next run");
                    require(io.github.aw1y2z.sesame.util.Log.messages.contains("token:String")&&!io.github.aw1y2z.sesame.util.Log.messages.contains("SECRET_TOKEN_VALUE")&&!io.github.aw1y2z.sesame.util.Log.messages.contains("SECRET_USER_VALUE"),"schema diagnosis exposed response values");
                    org.json.JSONObject wide=new org.json.JSONObject();for(int i=0;i<100;i++)wide.put("field"+i,"SECRET_TOKEN_VALUE");
                    require(IsolatedRewardTask.responseShape(wide).length()<300,"schema diagnosis was unbounded");
                    for(boolean degraded:new boolean[]{true,false}) {
                        ApplicationHook.response="{\"success\":true,\"result\":{\"degradePage\":"+degraded+",\"responseCarry\":{},\"thorCarry\":{}}}";daily.run();
                        require(state.getLong("DayDaySave.nextConfirmedQuery",0L)==0L&&daily.check(),"degraded/unknown page was guessed as a sign state");
                        require(io.github.aw1y2z.sesame.util.Log.messages.contains("degradePage="+degraded),"degradation flag value missing from diagnosis");
                    }
                    ApplicationHook.response="{\"success\":true,\"result\":{\"hasSignIn\":\"true\"}}";daily.run();
                    require(state.getLong("DayDaySave.nextConfirmedQuery",0L)>0L,"source Boolean string rejected");
                    state.reset();ApplicationHook.response="{\"success\":true,\"result\":{}}";
                    var luck=new io.github.aw1y2z.sesame.model.task.luckCard.LuckCardStatus();luck.getFields();luck.run();
                    require("com.alipay.pcreditcardweb.activity.LuckCard.consult".equals(ApplicationHook.lastMethod)&&"[{}]".equals(ApplicationHook.lastArgs),"SJ empty object envelope lost");
                    state.reset();var weekly=new io.github.aw1y2z.sesame.model.task.weeklyWelfare.WeeklyWelfare();weekly.getFields();weekly.run();
                    require(state.getLong("WeeklyWelfare.nextConfirmedQuery",0L)==0L&&weekly.check(),"UNKNOWN_STATE blocked next run");
                    ApplicationHook.response="{\"success\":true,\"result\":{\"MODE_FLAG\":{},\"MODE_OPTIONAL_FLAG\":{},\"responseCarry\":{},\"thorCarry\":{},\"upsertData\":{\"goldbillInfo\":{}}}}";weekly.run();
                    require(state.getLong("WeeklyWelfare.nextConfirmedQuery",0L)==0L&&weekly.check(),"goldbill-only page was treated as a weekly sign offer");
                    ApplicationHook.response="{\"success\":true,\"result\":{\"upsertData\":{\"sign\":{\"timeline\":[{\"isToday\":\"true\",\"signed\":\"true\",\"day\":\"3\"}]}}}}";weekly.run();
                    require(state.getLong("WeeklyWelfare.nextConfirmedQuery",0L)>0L,"source numeric/Boolean string contract rejected");
                    System.out.println("PASS: confirmed business intervals, SJ LuckCard envelope, source scalar values, retryable failures and retained attempts");
                }
            }
        """)
        cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        jar = next(p for p in (cache / "caches/modules-2/files-2.1/org.json/json").glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))
        compile_and_run(out, "io.github.aw1y2z.sesame.model.task.rewardSupport.RewardCooldownCheck", str(jar))


if __name__ == "__main__":
    check_reward_cooldown()
