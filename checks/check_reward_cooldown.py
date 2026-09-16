"""Regression checks for shared reward cooldowns."""
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
        write(out, "org/json/JSONObject.java", """
            package org.json;
            public final class JSONObject {
                private final String raw;
                public JSONObject(String raw) { this.raw = raw; }
                public int optInt(String key, int fallback) { return raw.contains("1009") ? 1009 : fallback; }
                public String optString(String key) { return optString(key, ""); }
                public String optString(String key, String fallback) {
                    if (key.equals("resultCode") && raw.contains("SYSTEM_ERROR")) return "SYSTEM_ERROR";
                    return fallback;
                }
                public Object opt(String key) { return key.equals("success") ? Boolean.FALSE : null; }
                public boolean has(String key) { return raw.contains(key); }
            }
        """)
        write(out, "io/github/aw1y2z/sesame/BuildConfig.java", """
            package io.github.aw1y2z.sesame; public final class BuildConfig { public static final String VERSION_NAME="check"; }
        """)
        write(out, "io/github/aw1y2z/sesame/data/ModelFields.java", """
            package io.github.aw1y2z.sesame.data; public final class ModelFields { public void addField(Object value) {} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/ModelGroup.java", """
            package io.github.aw1y2z.sesame.data; public enum ModelGroup { OTHER }
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
            public final class ApplicationHook { public static String response="{error:1009}";
                public static boolean isOffline(){return false;} public static String requestString(String m,String a,int r,int t){return response;} }
        """)
        write(out, "io/github/aw1y2z/sesame/model/base/TaskCommon.java", """
            package io.github.aw1y2z.sesame.model.base; public final class TaskCommon { public static boolean IS_ENERGY_TIME=false; }
        """)
        write(out, "io/github/aw1y2z/sesame/util/Log.java", """
            package io.github.aw1y2z.sesame.util; public final class Log { public static void record(String value) {} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", """
            package io.github.aw1y2z.sesame.util.idMap; public final class UserIdMap { public static String getCurrentUid(){return "A";} }
        """)
        write(out, "io/github/aw1y2z/sesame/model/task/rewardSupport/RewardCooldownCheck.java", """
            package io.github.aw1y2z.sesame.model.task.rewardSupport;
            import io.github.aw1y2z.sesame.data.*; import io.github.aw1y2z.sesame.hook.ApplicationHook;
            public final class RewardCooldownCheck {
                private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
                private static final class Probe extends IsolatedRewardTask {
                    public String getName(){return "probe";} protected void addFields(ModelFields f){}
                    protected void execute(Run run)throws Exception{run.query("method","[]");}
                }
                public static void main(String[] args) {
                    RuntimeInfo state=RuntimeInfo.getInstance(); Probe task=new Probe(); task.getFields(); long now=System.currentTimeMillis();
                    state.reset(); state.put("Probe.cooldownResetVersion","check"); state.put("Probe.nextQuery",0L); state.put("Probe.cooldownUntil",now+60_000L);
                    require(!task.check(),"durable cooldown gate was ignored");
                    state.put("Probe.cooldownUntil",0L); state.put("Probe.nextQuery",now+60_000L);
                    require(!task.check(),"normal interval gate was ignored");

                    state.reset(); state.put("Probe.nextQuery",now+23L*3_600_000L);
                    require(!task.check(),"upgrade cleared a legacy rejection cooldown");
                    state.put("Probe.nextQuery",0L);
                    require(!task.check(),"expedite cleared the migrated rejection cooldown");
                    state.put("Probe.cooldownResetVersion","previous-version");
                    require(!task.check(),"version reset cleared the rejection cooldown");
                    state.put("Probe.cooldownUntil",0L);
                    require(task.check(),"expired migration kept queries blocked");

                    state.reset(); task.getFields(); ApplicationHook.response="{error:1009}"; long riskStart=System.currentTimeMillis(); task.run();
                    require(state.getLong("Probe.nextQuery",0L) < riskStart+7L*3_600_000L,"risk cooldown overwrote normal interval");
                    require(state.getLong("Probe.cooldownUntil",0L) >= riskStart+23L*3_600_000L,"risk cooldown was not durable");

                    state.reset(); task.getFields(); ApplicationHook.response="{success:false,resultCode:SYSTEM_ERROR}"; long systemStart=System.currentTimeMillis(); task.run();
                    require(state.getLong("Probe.cooldownUntil",0L) >= systemStart+11L*3_600_000L,"system cooldown was not durable");
                    System.out.println("PASS: independent normal interval and server cooldown gates");
                }
            }
        """)
        compile_and_run(out, "io.github.aw1y2z.sesame.model.task.rewardSupport.RewardCooldownCheck")


if __name__ == "__main__":
    check_reward_cooldown()
