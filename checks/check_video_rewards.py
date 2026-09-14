"""Focused, dependency-light regression checks for the video reward fixes."""
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


def jackson_classpath() -> str:
    root = Path.home() / ".gradle/caches/modules-2/files-2.1/com.fasterxml.jackson.core"
    jars = []
    for artifact in ("jackson-annotations", "jackson-core", "jackson-databind"):
        matches = [p for p in (root / artifact).rglob("*.jar")
                   if not p.name.endswith(("-sources.jar", "-javadoc.jar"))]
        if not matches:
            raise RuntimeError(f"missing cached {artifact} jar")
        jars.append(str(max(matches, key=lambda p: p.stat().st_mtime)))
    return os.pathsep.join(jars)


def compile_and_run(base: Path, main: str, classpath: str = "") -> None:
    sources = [str(p) for p in base.rglob("*.java")]
    command = ["javac", "-J-Xmx128m", "-encoding", "UTF-8", "-d", str(base)]
    if classpath:
        command += ["-cp", classpath]
    subprocess.run(command + sources, check=True)
    runtime = str(base) + (os.pathsep + classpath if classpath else "")
    subprocess.run(["java", "-Xms16m", "-Xmx128m", "-ea", "-cp", runtime, main], check=True, timeout=30)


def check_evidence(classpath: str) -> None:
    with tempfile.TemporaryDirectory(prefix="sesame-video-evidence-") as directory:
        out = Path(directory)
        for name in ("VideoWatchEvidence", "VideoTaskDetail"):
            copy(out, f"io/github/aw1y2z/sesame/model/task/videoRewards/{name}.java")
        write(out, "io/github/aw1y2z/sesame/util/JsonUtil.java", """
            package io.github.aw1y2z.sesame.util;
            public final class JsonUtil {
                public static com.fasterxml.jackson.databind.ObjectMapper copyMapper() {
                    return new com.fasterxml.jackson.databind.ObjectMapper();
                }
            }
        """)
        write(out, "io/github/aw1y2z/sesame/model/task/videoRewards/VideoEvidenceCheck.java", """
            package io.github.aw1y2z.sesame.model.task.videoRewards;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import com.fasterxml.jackson.databind.JsonNode;
            import com.fasterxml.jackson.databind.node.ObjectNode;
            import java.lang.reflect.Method;

            public final class VideoEvidenceCheck {
                private static final ObjectMapper JSON = new ObjectMapper();
                private static void require(boolean value, String message) {
                    if (!value) throw new AssertionError(message);
                }
                private static ObjectNode page(long current, boolean paused, boolean ended) {
                    ObjectNode page = JSON.createObjectNode()
                            .put("contentId", "demo").put("pagePath", "https://example.test/watch");
                    page.putArray("videos").addObject().put("currentMs", current)
                            .put("durationMs", 30_000L).put("paused", paused).put("ended", ended);
                    return page;
                }
                private static void capture(String account, ObjectNode page, long now, long minimum) throws Exception {
                    Method fallback = null;
                    for (Method method : VideoWatchEvidence.Store.class.getDeclaredMethods()) {
                        if (!method.getName().equals("capture")) continue;
                        if (method.getParameterCount() == 4) { method.invoke(null, account, page, now, minimum); return; }
                        if (method.getParameterCount() == 3) fallback = method;
                    }
                    if (fallback == null) throw new AssertionError("capture entry is missing");
                    fallback.invoke(null, account, page, now);
                }
                public static void main(String[] args) throws Exception {
                    VideoTaskDetail task = VideoTaskDetail.parse("{\\\"taskData\\\":{\\\"contentId\\\":\\\"demo\\\",\\\"duration\\\":30,\\\"rewardParams\\\":\\\"{}\\\",\\\"taskType\\\":\\\"watch\\\",\\\"completed\\\":false}}");

                    VideoWatchEvidence.Store.clear();
                    capture("A", page(20_000L, false, false), 20_000L, 15_000L);
                    capture("A", page(30_000L, true, true), 30_000L, 15_000L);
                    JsonNode retained = VideoWatchEvidence.Store.takeFresh("A", 31_000L, 600_000L);
                    require(retained != null && retained.path("videos").get(0).path("currentMs").asLong() == 20_000L,
                            "paused/ended sample replaced qualifying progress");
                    require(VideoWatchEvidence.evaluate(retained, task, 15_000L, "A", 31_000L) != null,
                            "retained qualifying progress was rejected");

                    VideoWatchEvidence.Store.clear();
                    capture("A", page(5_000L, false, false), 40_000L, 15_000L);
                    require(VideoWatchEvidence.Store.takeFresh("A", 41_000L, 600_000L) == null,
                            "sub-threshold progress entered the evidence store");

                    capture("A", page(20_000L, false, false), 50_000L, 15_000L);
                    require(VideoWatchEvidence.Store.takeFresh("B", 51_000L, 600_000L) == null,
                            "wrong account consumed evidence");
                    require(VideoWatchEvidence.Store.takeFresh("A", 51_000L, 600_000L) == null,
                            "account-mismatched evidence survived the session boundary");
                    System.out.println("PASS: qualifying video evidence retention and account invalidation");
                }
            }
        """)
        compile_and_run(out, "io.github.aw1y2z.sesame.model.task.videoRewards.VideoEvidenceCheck", classpath)


def check_reward_cooldown() -> None:
    with tempfile.TemporaryDirectory(prefix="sesame-video-cooldown-") as directory:
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


def check_video_model() -> None:
    with tempfile.TemporaryDirectory(prefix="sesame-video-model-") as directory:
        out = Path(directory)
        copy(out, "io/github/aw1y2z/sesame/model/task/videoRewards/VideoRewards.java")
        write(out, "org/json/JSONArray.java", """
            package org.json; import java.util.*; public final class JSONArray {
                private final java.util.List<Object> values=new ArrayList<>(); public int length(){return values.size();}
                public JSONObject optJSONObject(int i){return i>=0&&i<values.size()&&values.get(i) instanceof JSONObject?(JSONObject)values.get(i):null;}
            }
        """)
        write(out, "org/json/JSONObject.java", """
            package org.json; import java.util.*; public final class JSONObject {
                private final Map<String,Object> values=new HashMap<>(); public JSONObject put(String k,Object v){values.put(k,v);return this;}
                public JSONArray optJSONArray(String k){Object v=values.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
                public boolean optBoolean(String k,boolean f){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:f;}
                public String optString(String k){Object v=values.get(k);return v==null?"":String.valueOf(v);} public String toString(){return "{}";}
            }
        """)
        write(out, "io/github/aw1y2z/sesame/data/ModelFields.java", """
            package io.github.aw1y2z.sesame.data; public final class ModelFields { public void addField(Object value){} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/Model.java", """
            package io.github.aw1y2z.sesame.data; public final class Model { public static Object current;
                @SuppressWarnings("unchecked") public static <T>T getModel(Class<T> type){return (T)current;} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/RuntimeInfo.java", """
            package io.github.aw1y2z.sesame.data; import java.util.*; public final class RuntimeInfo {
                private static final RuntimeInfo I=new RuntimeInfo(); private final Map<String,Object> v=new HashMap<>();
                public static RuntimeInfo getInstance(){return I;} public void put(String k,Object x){v.put(k,x);}
                public long getLong(String k,long f){Object x=v.get(k);return x instanceof Number?((Number)x).longValue():f;}
            }
        """)
        write(out, "io/github/aw1y2z/sesame/data/modelFieldExt/BooleanModelField.java", """
            package io.github.aw1y2z.sesame.data.modelFieldExt; import java.util.*; public final class BooleanModelField {
                public static final Map<String,Boolean> VALUES=new HashMap<>(); private final String key;
                public BooleanModelField(String k,String n,boolean d){key=k;} public boolean getValue(){return VALUES.getOrDefault(key,false);} }
        """)
        write(out, "io/github/aw1y2z/sesame/data/modelFieldExt/IntegerModelField.java", """
            package io.github.aw1y2z.sesame.data.modelFieldExt; public final class IntegerModelField {
                public IntegerModelField(String k,String n,int d,int min,int max){} public int getValue(){return 15;} }
        """)
        write(out, "io/github/aw1y2z/sesame/model/task/rewardSupport/IsolatedRewardTask.java", """
            package io.github.aw1y2z.sesame.model.task.rewardSupport; import io.github.aw1y2z.sesame.data.ModelFields; import org.json.JSONObject;
            public abstract class IsolatedRewardTask {
                public boolean isEnable(){return true;} public final ModelFields getFields(){ModelFields f=new ModelFields();addFields(f);return f;}
                public abstract String getName(); protected String nextKey(){return "next";} protected abstract void addFields(ModelFields f);
                protected io.github.aw1y2z.sesame.data.RuntimeInfo cooldownState(){return io.github.aw1y2z.sesame.data.RuntimeInfo.getInstance();}
                protected abstract void execute(Run run)throws Exception;
                public final class Run { public int queries,mutations; public JSONObject query(String method,String args){queries++;return method.equals("wallet")?new JSONObject().put("envelopeDetailList",new org.json.JSONArray()):new JSONObject();}
                    public JSONObject onceToday(String action,String method,String args,Allowed allowed){mutations++;return new JSONObject();} }
                protected interface Allowed{boolean isAllowed();}
            }
        """)
        write(out, "io/github/aw1y2z/sesame/hook/ApplicationHook.java", """
            package io.github.aw1y2z.sesame.hook; public final class ApplicationHook { public static int early;
                public static void requestEarlyTaskRun(){early++;} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/Log.java", """
            package io.github.aw1y2z.sesame.util; public final class Log { public static void record(String value){} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", """
            package io.github.aw1y2z.sesame.util.idMap; public final class UserIdMap { public static String current="A"; public static String getCurrentUid(){return current;} }
        """)
        write(out, "com/fasterxml/jackson/databind/JsonNode.java", """
            package com.fasterxml.jackson.databind; public class JsonNode { }
        """)
        write(out, "io/github/aw1y2z/sesame/model/task/videoRewards/VideoStubs.java", """
            package io.github.aw1y2z.sesame.model.task.videoRewards; import org.json.JSONObject; import java.util.*;
            final class VideoRewardsRpcCall { static final String WALLET_METHOD="wallet",WALLET_ARGS="[]",RESERVE_METHOD="reserve",RESERVE_ARGS="[]"; }
            final class VideoTaskQueryProtocol { static final String METHOD="tasks",ARGS="[]"; static List<VideoTaskDetail> parse(String x){return new ArrayList<>();} }
            final class VideoTaskDetail { String contentId="",rewardParams=""; }
            final class VideoWalletRewardProtocol { static final String METHOD="claim"; static final class Claim{String activityId="";} static Claim findClaim(String x){return null;} static String arguments(Claim c){return "[]";} }
            final class VideoReservationPolicy { static boolean blocksLegacyAttempt(long n,long p){return false;} }
            final class VideoRecordProtocol { static final String METHOD="record"; static String arguments(String a,String b,String c){return "[]";} }
            final class VideoRecordOutcome { String contentId="",rewardState=""; static VideoRecordOutcome parse(String x){return new VideoRecordOutcome();} }
            final class VideoWatchEvidence { String sourcePage=""; long currentMs; static VideoWatchEvidence evaluate(com.fasterxml.jackson.databind.JsonNode p,VideoTaskDetail t,long m,String a,long n){return null;}
                static final class Store { static com.fasterxml.jackson.databind.JsonNode takeFresh(String a,long n,long w){return null;} static void clear(){} } }
        """)
        write(out, "io/github/aw1y2z/sesame/model/task/videoRewards/VideoModelCheck.java", """
            package io.github.aw1y2z.sesame.model.task.videoRewards;
            import java.lang.reflect.Method; import io.github.aw1y2z.sesame.data.*; import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
            import io.github.aw1y2z.sesame.hook.ApplicationHook; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
            public final class VideoModelCheck {
                private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
                public static void main(String[] args)throws Exception{
                    BooleanModelField.VALUES.clear(); BooleanModelField.VALUES.put("reserve",true);
                    VideoRewards reserveOnly=new VideoRewards(); reserveOnly.getFields(); VideoRewards.Run run=reserveOnly.new Run(); reserveOnly.execute(run);
                    require(run.queries==2 && run.mutations==1,"reserve-only skipped task query/reservation");

                    BooleanModelField.VALUES.clear(); BooleanModelField.VALUES.put("watchRecord",true);
                    VideoRewards watched=new VideoRewards(); watched.getFields(); Model.current=watched;
                    RuntimeInfo.getInstance().put("VideoRewards.nextWalletQuery",123L); ApplicationHook.early=0;
                    Method method=null; for(Method candidate:VideoRewards.class.getDeclaredMethods())
                        if(candidate.getName().equals("expediteNextQuery") && candidate.getParameterCount()==1) method=candidate;
                    require(method!=null,"account-bound expedite entry is missing");
                    UserIdMap.current="B"; require(Boolean.FALSE.equals(method.invoke(null,"A")),"stale account expedited query");
                    require(RuntimeInfo.getInstance().getLong("VideoRewards.nextWalletQuery",0L)==123L && ApplicationHook.early==0,"stale account changed scheduling");
                    require(Boolean.TRUE.equals(method.invoke(null,"B")),"current account expedite was rejected");
                    require(RuntimeInfo.getInstance().getLong("VideoRewards.nextWalletQuery",123L)==0L && ApplicationHook.early==1,"qualifying watch did not schedule dispatcher");
                    System.out.println("PASS: reserve-only flow and real account-bound early scheduling");
                }
            }
        """)
        compile_and_run(out, "io.github.aw1y2z.sesame.model.task.videoRewards.VideoModelCheck")


def check_observer_account(classpath: str) -> None:
    with tempfile.TemporaryDirectory(prefix="sesame-video-observer-") as directory:
        out = Path(directory)
        copy(out, "io/github/aw1y2z/sesame/hook/VideoPageObserver.java")
        for name in ("VideoWatchEvidence", "VideoTaskDetail"):
            copy(out, f"io/github/aw1y2z/sesame/model/task/videoRewards/{name}.java")
        write(out, "android/os/Looper.java", """package android.os; public final class Looper { private static final Looper M=new Looper(); public static Looper getMainLooper(){return M;} }""")
        write(out, "android/os/Handler.java", """
            package android.os; import java.util.*; public final class Handler { public static final Queue<Runnable> QUEUE=new ArrayDeque<>();
                public Handler(Looper l){} public boolean postDelayed(Runnable r,long d){QUEUE.add(r);return true;} public boolean post(Runnable r){r.run();return true;}
                public static void runNext(){Runnable r=QUEUE.poll();if(r!=null)r.run();} }
        """)
        write(out, "android/view/View.java", """package android.view; public class View { public boolean isAttachedToWindow(){return true;} public boolean isShown(){return true;} public CharSequence getAccessibilityClassName(){return "android.webkit.WebView";} }""")
        write(out, "android/view/ViewGroup.java", """package android.view; public class ViewGroup extends View { public int getChildCount(){return 0;} public View getChildAt(int i){return null;} }""")
        write(out, "android/view/Window.java", """package android.view; public final class Window { private final View view; public Window(View v){view=v;} public View getDecorView(){return view;} }""")
        write(out, "android/app/Activity.java", """package android.app; import android.view.*; public class Activity { private final Window w=new Window(new View()); public boolean isFinishing(){return false;} public Window getWindow(){return w;} }""")
        write(out, "io/github/aw1y2z/sesame/util/compat/XC_MethodHook.java", """
            package io.github.aw1y2z.sesame.util.compat; public class XC_MethodHook { protected void afterHookedMethod(MethodHookParam p){} public static class MethodHookParam{public Object thisObject;} }
        """)
        write(out, "io/github/aw1y2z/sesame/hook/CompatHelpers.java", """package io.github.aw1y2z.sesame.hook; public final class CompatHelpers { public static void findAndHookMethod(Object... args){} }""")
        write(out, "io/github/aw1y2z/sesame/data/Model.java", """package io.github.aw1y2z.sesame.data; public final class Model {
            public static <T>T getModel(Class<T> c){try{return c.getDeclaredConstructor().newInstance();}catch(Exception e){return null;}} }""")
        write(out, "io/github/aw1y2z/sesame/model/task/videoRewards/VideoRewards.java", """
            package io.github.aw1y2z.sesame.model.task.videoRewards; public final class VideoRewards { public static int expedites;
                public static boolean watchSamplingRequested(){return true;} public static long minimumWatchMs(){return 15_000L;}
                public static void expediteNextQuery(){expedites++;} public static boolean expediteNextQuery(String account){expedites++;return true;} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/JsonUtil.java", """
            package io.github.aw1y2z.sesame.util; public final class JsonUtil { public static com.fasterxml.jackson.databind.ObjectMapper copyMapper(){return new com.fasterxml.jackson.databind.ObjectMapper();} }
        """)
        write(out, "io/github/aw1y2z/sesame/util/Log.java", """package io.github.aw1y2z.sesame.util; public final class Log { public static void record(String v){} }""")
        write(out, "io/github/aw1y2z/sesame/util/idMap/UserIdMap.java", """
            package io.github.aw1y2z.sesame.util.idMap; public final class UserIdMap { public static String current="A"; public static String getCurrentUid(){return current;} }
        """)
        write(out, "io/github/aw1y2z/sesame/hook/PageSubmissionProbe.java", """
            package io.github.aw1y2z.sesame.hook; import android.view.View; import java.util.*; import java.util.function.Consumer;
            public final class PageSubmissionProbe { public static final List<Consumer<String>> SINKS=new ArrayList<>();
                public static void sample(View v,long id,String stage,Consumer<String> sink){SINKS.add(sink);} }
        """)
        write(out, "io/github/aw1y2z/sesame/hook/VideoObserverAccountCheck.java", """
            package io.github.aw1y2z.sesame.hook; import java.lang.reflect.Method; import android.app.Activity; import android.os.Handler;
            import io.github.aw1y2z.sesame.model.task.videoRewards.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
            public final class VideoObserverAccountCheck {
                private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
                public static void main(String[] args)throws Exception{
                    VideoWatchEvidence.Store.clear(); UserIdMap.current="A"; VideoRewards.expedites=0;
                    Method start=VideoPageObserver.class.getDeclaredMethod("maybeStart",Activity.class); start.setAccessible(true); start.invoke(null,new Activity());
                    Handler.runNext(); require(PageSubmissionProbe.SINKS.size()==1,"sample was not requested");
                    UserIdMap.current="B";
                    PageSubmissionProbe.SINKS.get(0).accept("{\\\"contentId\\\":\\\"demo\\\",\\\"pagePath\\\":\\\"watch\\\",\\\"videos\\\":[{\\\"currentMs\\\":20000,\\\"durationMs\\\":30000,\\\"paused\\\":false,\\\"ended\\\":false}]}");
                    require(VideoWatchEvidence.Store.takeFresh("A",System.currentTimeMillis(),600_000L)==null,"old-account callback stored evidence");
                    require(VideoRewards.expedites==0,"old-account callback requested an early run");
                    System.out.println("PASS: delayed observer callbacks cannot cross accounts");
                }
            }
        """)
        compile_and_run(out, "io.github.aw1y2z.sesame.hook.VideoObserverAccountCheck", classpath)


if __name__ == "__main__":
    jackson = jackson_classpath()
    check_evidence(jackson)
    check_reward_cooldown()
    check_video_model()
    check_observer_account(jackson)
    print("PASS: all focused video reward checks")
