"""Run actual mini-program hooks, Dodo scratch and orchard claim/RPC methods; no device/network."""
from pathlib import Path
import os, subprocess, sys, tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import SOURCE, method

cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
common = r'''
static final String TAG="test";
static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
static class RpcRequestGuard { @@GUARD@@ }
static class MessageUtil {
 static boolean checkResultCode(String tag,JSONObject j){return !RpcRequestGuard.isFailure(j)&&("SUCCESS".equals(j.optString("resultCode"))||Boolean.TRUE.equals(j.opt("success")));}
 static boolean checkSuccess(String tag,JSONObject j){return !RpcRequestGuard.isFailure(j)&&Boolean.TRUE.equals(j.opt("success"));}
 static void checkResultCodeAndMarkTaskBlackList(String a,String b,JSONObject j){blacklists++;}
}
static int blacklists;
static class Log {static List<String> lines=new ArrayList<>(),farm=new ArrayList<>();static void record(String s){lines.add(s);}static void farm(String s){farm.add(s);}static void forest(String s){farm.add(s);}static void i(String a,String b){}static void err(String a,String b,Throwable t){if(t instanceof AssertionError)throw (AssertionError)t;}static void printStackTrace(String a,Throwable t){if(t instanceof AssertionError)throw (AssertionError)t;}}
static class TimeUtil {static void sleep(long n){}}
'''.replace('@@GUARD@@', method('rpc/intervallimit/RpcRequestGuard.java', 'public static boolean isFailure('))

mini = r'''
import java.util.*;import org.json.*;
import io.github.aw1y2z.sesame.hook.MiniProgramHook;
import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;
public class MiniHookCheck {
 static void request(String url,String body){XHelpers.before("processAsyncRequest",new Object[]{Map.of("url",url,"method","POST","data",body)});}
 static void response(String url,String body){XHelpers.before("onHandleResponse",new Object[]{url,body});}
 static List<String> logs(){return io.github.aw1y2z.sesame.util.Log.lines;}
 public static void main(String[] args){
  MiniProgramHook.install(MiniHookCheck.class.getClassLoader(),"test");assert XHelpers.hooks.isEmpty();
  AppConfig.INSTANCE.enabled=true;MiniProgramHook.install(MiniHookCheck.class.getClassLoader(),"test");
  switch(args[0]){
   case "reload":int n=XHelpers.hooks.size();assert n>0;MiniProgramHook.install(MiniHookCheck.class.getClassLoader(),"test");assert XHelpers.hooks.size()==n:"reload duplicated ClassLoader hooks";break;
   case "retry":MiniProgramHook.uninstall();XHelpers.failMembers=true;MiniProgramHook.install(MiniHookCheck.class.getClassLoader(),"test");XHelpers.failMembers=false;
    for(var h:new ArrayList<>(XHelpers.hooks))if(h.name.equals("loadClass")){h.callback.after(ClassForResponse());h.callback.after(com.alipay.android.phone.wallet.aompnetwork.request.AsyncRequestHandler.class);}
    assert XHelpers.hooks.stream().filter(h->h.name.equals("onHandleResponse")).count()==1&&XHelpers.hooks.stream().filter(h->h.name.equals("processAsyncRequest")).count()==1:"failed hook was marked installed and never retried";break;
   case "late":List<XC_MethodHook> late=new ArrayList<>();for(var h:XHelpers.hooks)if(h.name.equals("loadClass"))late.add(h.callback);
    AppConfig.INSTANCE.enabled=false;MiniProgramHook.uninstall();for(var h:late)h.after(ClassForResponse());assert XHelpers.hooks.isEmpty():"late callback reinstalled hooks while disabled";break;
   case "pending":String url="https://game.example/api?timestamp=1";request(url,"{\"oldAccountBody\":123}");MiniProgramHook.uninstall();MiniProgramHook.install(MiniHookCheck.class.getClassLoader(),"test");logs().clear();response(url,"{}");assert logs().size()==1&&!logs().get(0).contains("oldAccountBody"):"old pending request survived disable/re-enable";break;
   case "bounded":final int[] reads={0};Collection<Object> huge=new AbstractCollection<>(){public int size(){return 10000;}public Iterator<Object> iterator(){return new Iterator<>(){public boolean hasNext(){return reads[0]<10000;}public Object next(){if(++reads[0]>300)throw new AssertionError("unbounded model iteration");return 1;}};}};
    XHelpers.before("processAsyncRequest",new Object[]{huge});assert reads[0]<=256:"model traversal ignored item limit";break;
   case "capture":String u="https://game.example/api?timestamp=2";request(u,"{\"value\":\"a+b\",\"sign\":{\"business\":1}}");logs().clear();response(u,"{\"nested\":\"{\\\"ok\\\":true}\"}");assert logs().size()==1;String log=logs().get(0);assert log.contains("a+b")&&log.contains("business")&&log.contains("nested");
    logs().clear();response("https://game.example/assets/a.js","static");assert logs().isEmpty();
    response(u,"{invalid payload");assert logs().get(0).contains("{invalid payload"):"invalid raw response became empty JSON";break;
   default:throw new AssertionError(args[0]);
  }
  System.out.println("PASS mini "+args[0]);
 }
 static Class<?> ClassForResponse(){return com.alipay.android.phone.event.ariver.network.HttpRequestResponseTrigger.class;}
}
'''

dodo_path = 'model/task/antDodo/AntDodo.java'
dodo_rpc = 'model/task/antDodo/AntDodoRpcCall.java'
dodo = r'''
import org.json.*;import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class DodoScratchCheck {
 @@COMMON@@
 static String home,reply="{\"resultCode\":\"SUCCESS\",\"data\":{\"energyGram\":2}}";static boolean cancel;static List<JSONObject> calls=new ArrayList<>();
 static class AntDodoRpcCall {static String homePage(){if(cancel)throw new TaskCancelledException();return home;} @@RPC@@ }
 static class ApplicationHook {static boolean cancelScratch;static String requestString(String op,String args){calls.add(new JSONArray(args).optJSONObject(0));if(cancelScratch)throw new TaskCancelledException();return reply;}}
 static String getAnimalInfo(JSONObject a){return a.optString("name");}
 @@METHODS@@
 static JSONObject card(String id){return new JSONObject().put("cardInstanceId",id).put("scratchable",true).put("scratched",false);}
 static JSONObject collection(String animal,String book,JSONArray cards){return new JSONObject().put("animal",new JSONObject().put("animalId",animal).put("bookId",book).put("name","animal")).put("collectDetail",new JSONObject().put("cardList",cards));}
 static void home(JSONObject... rows){home=new JSONObject().put("resultCode","SUCCESS").put("data",new JSONObject().put("showSpeciesV2",true).put("curCollection",new JSONArray(List.of(rows)))).toString();}
 public static void main(String[] args){DodoScratchCheck d=new DodoScratchCheck();
  switch(args[0]){
   case "cancel":cancel=true;try{d.scratchAnimalCard();throw new AssertionError("scratch swallowed cancellation");}catch(TaskCancelledException expected){}
    cancel=false;home(collection("animal","book",new JSONArray().put(card("one")).put(card("two"))));ApplicationHook.cancelScratch=true;try{d.scratchAnimalCard();throw new AssertionError("scratch RPC swallowed cancellation");}catch(TaskCancelledException expected){}assert calls.size()==1;break;
   case "ids":home(collection("animal","book",new JSONArray().put(card("badType").put("cardInstanceId",new JSONObject()))).put("animal",new JSONObject().put("animalId",new JSONObject()).put("bookId","book")),collection("","book",new JSONArray().put(card("bad"))),collection("animal","",new JSONArray().put(card("bad2"))),collection("animal","book",new JSONArray().put(card("good")).put(card("good")).put(card(" "))));d.scratchAnimalCard();assert calls.size()==1&&calls.get(0).optString("cardInstanceId").equals("good"):"invalid or duplicate scratch identifiers sent";break;
   case "payload":home(collection("a\"b","book",new JSONArray().put(card("c\\d"))));d.scratchAnimalCard();assert calls.size()==1&&calls.get(0).optString("animalId").equals("a\"b")&&calls.get(0).optString("cardInstanceId").equals("c\\d"):"scratch request not JSON escaped";break;
   case "ack":home(collection("animal","book",new JSONArray().put(card("one"))));reply="{\"resultCode\":\"SUCCESS\"}";d.scratchAnimalCard();assert calls.size()==1&&!Log.farm.toString().contains("获得0g"):"missing reward reported as zero energy";
    calls.clear();home="{\"resultCode\":\"SUCCESS\",\"data\":{\"showSpeciesV2\":false}}";d.scratchAnimalCard();assert calls.isEmpty();break;
   default:throw new AssertionError(args[0]);
  }System.out.println("PASS scratch "+args[0]);
 }
}
'''.replace('@@COMMON@@', common).replace('@@RPC@@', method(dodo_rpc, 'public static String scratchCard(')).replace('@@METHODS@@', method(dodo_path, 'private void scratchAnimalCard(') + method(dodo_path, 'private void scratchNewCard('))

orchard_path = 'model/task/antOrchard/AntOrchard.java'
orchard_rpc = 'model/task/antOrchard/AntOrchardRpcCall.java'
orchard = r'''
import org.json.*;import java.util.*;import java.util.function.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class OrchardMergeCheck {
 @@COMMON@@
 static final int MAX_OPTIONAL_PLAY_ROUNDS=8;enum TaskStatus{TODO,FINISHED,RECEIVED}enum Outcome{DONE,TRIGGERED,AWARDED,RETRY}
 static class Field{Set<String> values=new HashSet<>();Set<String> getValue(){return values;}}Field AntOrchardTaskList=new Field();
 static class TaskAlternative{static boolean isTransactionTask(String s){return s.contains("PAY");}}
 static class TaskAttemptPolicy{enum ProbeResult{UNKNOWN}static class Site{Site(String a,String b,String c,String d,Function<String,ProbeResult> f){}Site withScene(String s){return this;}Site withSource(String s){return this;}}static Outcome handle(String a,String b,Object c,Supplier<Outcome> s,Consumer<String> l,Site site){return s.get();}}
 static class TaskAward{static boolean confirmReceivedOrBlackList(String a,Function<String,TaskAttemptPolicy.ProbeResult> f,String k,String t,Runnable r,Consumer<String> l){if(r!=null)r.run();return false;}}
 TaskAttemptPolicy.ProbeResult probeOrchardOptionalPlayStatus(String a,String b,int c){return TaskAttemptPolicy.ProbeResult.UNKNOWN;}
 Outcome attemptFinishOptionalPlayTask(String a,String b,String c){reports++;return Outcome.RETRY;}
 static int claims,reports;static boolean fail,cancel;static JSONArray tasks;static String trigger;static List<String> bodies=new ArrayList<>();
 static class AntOrchardRpcCall{
  static String queryOptionalPlay(){return new JSONObject().put("success",true).put("taskTriggerPlayInfo",new JSONObject().put("taskList",tasks)).toString();}
  static String receiveTaskAwardantorchard(int n,String s,String t){claims++;if(fail)return "{\"success\":false,\"resultCode\":\"102\"}";for(int i=0;i<tasks.length();i++){JSONObject row=tasks.optJSONObject(i);if(t.equals(row.optString("taskType")))row.put("taskStatus","RECEIVED").put("alreadyReceiveAwardCount",2).put("rightsTimes",1);}return "{\"success\":true,\"incAwardCount\":2}";}
  @@RPC@@
 }
 static class ApplicationHook {static String requestString(String op,String args){if(cancel)throw new TaskCancelledException();bodies.add(args);return trigger;}}
 @@METHODS@@
 static JSONObject task(String title,String type,String state){return new JSONObject().put("taskType",type).put("taskStatus",state).put("sceneCode","SCENE").put("groupId","GROUP").put("awardCount",2).put("alreadyReceiveAwardCount",0).put("rightsTimesLimit",1).put("rightsTimes",0).put("bizInfo",new JSONObject().put("title",title));}
 public static void main(String[] args){OrchardMergeCheck o=new OrchardMergeCheck();
  switch(args[0]){
   case "claim":o.AntOrchardTaskList.values.add("blocked");tasks=new JSONArray().put(task("blocked","browse","FINISHED")).put(task("blocked","browse2","TODO")).put(task("paid","PAY_TASK","FINISHED")).put(task("payTodo","PAY_TASK2","TODO"));o.queryOptionalPlay();assert claims==2&&reports==0&&blacklists==0:"completed rewards blocked or TODO transaction reported";break;
   case "retry":tasks=new JSONArray().put(task("reward","browse","FINISHED"));fail=true;o.queryOptionalPlay();assert claims==1&&blacklists==0:"claim failure blacklisted an already completed task";fail=false;o.queryOptionalPlay();assert claims==2;break;
   case "money":for(String s:new String[]{"{\"result\":{}}","{\"success\":false,\"result\":{\"awardInfo\":{}}}","{\"result\":{\"success\":false,\"awardInfo\":{}}}","garbage","{}"}){trigger=s;Log.farm.clear();o.triggerYebMoneyTreeOnce();assert Log.farm.isEmpty():"failure falsely accepted: "+s;}
    for(String s:new String[]{"{\"result\":{\"awardInfo\":{\"totalAmount\":\"0.01\"}}}","{\"resultCode\":\"SUCCESS\"}"}){trigger=s;Log.farm.clear();o.triggerYebMoneyTreeOnce();assert Log.farm.size()==1:"valid trigger rejected";}break;
   case "cancel":cancel=true;try{o.triggerYebMoneyTreeOnce();throw new AssertionError("money tree swallowed cancellation");}catch(TaskCancelledException expected){}break;
   case "queryCancel":cancel=true;try{o.queryYebRevenueDetail();throw new AssertionError("revenue query swallowed cancellation");}catch(TaskCancelledException expected){}break;
   case "rpc":AntOrchardRpcCall.yebPlantSceneRevenuePage();assert new JSONArray(bodies.get(0)).optJSONObject(0).length()==0:"revenue page kept invalid sceneCode";break;
   default:throw new AssertionError(args[0]);
  }System.out.println("PASS orchard "+args[0]);
 }
}
'''.replace('@@COMMON@@', common).replace('@@METHODS@@', method(orchard_path, 'private void queryOptionalPlay(') + method(orchard_path, 'private void triggerYebMoneyTreeOnce(') + method(orchard_path, 'private void queryYebRevenueDetail(')).replace('@@RPC@@', method(orchard_rpc, 'public static String yebPlantSceneRevenuePage(') + method(orchard_rpc, 'public static String triggerYebMoneyTree('))

fakes = {
 'io/github/aw1y2z/sesame/data/AppConfig.java': 'package io.github.aw1y2z.sesame.data;public class AppConfig{public static AppConfig INSTANCE=new AppConfig();public boolean enabled;public boolean getEnableDebugLog(){return enabled;}}',
 'io/github/aw1y2z/sesame/util/MyUtils.java': 'package io.github.aw1y2z.sesame.util;import org.json.*;public class MyUtils{public static JSONObject newJSONObject(){return new JSONObject();}public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}',
 'io/github/aw1y2z/sesame/util/Log.java': 'package io.github.aw1y2z.sesame.util;import java.util.*;public class Log{public static List<String> lines=new ArrayList<>();public static void debug(String s){lines.add(s);}}',
 'io/github/aw1y2z/sesame/util/compat/XC_MethodHook.java': r'''package io.github.aw1y2z.sesame.util.compat;public class XC_MethodHook{public static class MethodHookParam{public Object[] args;public Object thisObject;public Object result;public Object getResult(){return result;}}protected void beforeHookedMethod(MethodHookParam p){}protected void afterHookedMethod(MethodHookParam p){}public void before(Object[] args){MethodHookParam p=new MethodHookParam();p.args=args;beforeHookedMethod(p);}public void after(Object result){MethodHookParam p=new MethodHookParam();p.result=result;afterHookedMethod(p);}public static class Unhook{Runnable r;public Unhook(Runnable r){this.r=r;}public void unhook(){r.run();}}}''',
 'io/github/aw1y2z/sesame/util/XHelpers.java': r'''package io.github.aw1y2z.sesame.util;import java.lang.reflect.*;import java.util.*;import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;public class XHelpers{public static class Entry{public String name;public XC_MethodHook callback;Entry(String n,XC_MethodHook c){name=n;callback=c;}}public static List<Entry> hooks=new ArrayList<>();static XC_MethodHook.Unhook register(String n,XC_MethodHook c){Entry e=new Entry(n,c);hooks.add(e);return new XC_MethodHook.Unhook(()->hooks.remove(e));}public static Class<?> findClassIfExists(String n,ClassLoader loader){try{return Class.forName(n);}catch(Exception e){return null;}}public static boolean failMembers;public static XC_MethodHook.Unhook hookMember(Member m,XC_MethodHook c){if(failMembers)throw new IllegalStateException();return register(m.getName(),c);}public static XC_MethodHook.Unhook findAndHookMethod(Class<?> c,String n,Object... args){return register(n,(XC_MethodHook)args[args.length-1]);}public static void before(String n,Object[] args){for(Entry e:new ArrayList<>(hooks))if(e.name.equals(n))e.callback.before(args);}}''',
 'com/alipay/android/phone/event/ariver/network/HttpRequestResponseTrigger.java': 'package com.alipay.android.phone.event.ariver.network;public class HttpRequestResponseTrigger{public void onHandleResponse(String url,Object response){}}',
 'com/alipay/android/phone/wallet/aompnetwork/request/AsyncRequestHandler.java': 'package com.alipay.android.phone.wallet.aompnetwork.request;public class AsyncRequestHandler{public void processAsyncRequest(Object data){}}',
 'dalvik/system/BaseDexClassLoader.java': 'package dalvik.system;public class BaseDexClassLoader extends ClassLoader{}',
 'MiniHookCheck.java': mini, 'DodoScratchCheck.java': dodo, 'OrchardMergeCheck.java': orchard,
}
cases = {'MiniHookCheck':['reload','late','pending','bounded','capture','retry'], 'DodoScratchCheck':['cancel','ids','payload','ack'], 'OrchardMergeCheck':['claim','retry','money','cancel','queryCancel','rpc']}
with tempfile.TemporaryDirectory(prefix='sesame-upstream-oct20-') as tmp:
    tmp = Path(tmp)
    paths = []
    for name, code in fakes.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(code, encoding='utf-8')
        paths.append(str(path))
    paths.extend([str(SOURCE/'hook/MiniProgramHook.java'), str(SOURCE/'util/TaskCancelledException.java')])
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',str(tmp),*paths],check=True)
    failed = []
    for main, scenarios in cases.items():
        for scenario in scenarios:
            result = subprocess.run(['java','-ea','-cp',str(tmp)+os.pathsep+str(jar),main,scenario])
            if result.returncode:
                failed.append(main+':'+scenario)
    assert not failed, failed
