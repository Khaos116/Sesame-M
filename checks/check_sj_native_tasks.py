"""Replay real orchard/native workers with local Android surfaces; no device or RPC."""
import ast
import os
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/aw1y2z/sesame'
tree = ast.parse((ROOT / 'checks/check_sj_activities.py').read_text(encoding='utf-8'))
code = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign)
            and any(isinstance(t, ast.Name) and t.id == 'code' for t in n.targets))
code = code[:code.index(' public static void main(String[] args)')]
code = code.replace('int retries)throws Exception', 'int retries)').replace('throws Exception{return rpc', '{return rpc').replace('boolean write)throws Exception{', 'boolean write){')
code = code.replace('public class SjActivityCheck', 'public class SjNativeCheck')
code = code.replace('import org.json.*;', '''import android.app.*;import android.content.*;import android.net.*;import android.os.*;import android.view.*;
import java.lang.reflect.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;import org.json.*;''')
code = code.replace('static void record(String s){}', 'static void record(String s){}static void farm(String s){confirmed++;}static void err(String a,String b,Throwable e){throw new AssertionError(e);}')
code = code.replace('static boolean offline;static boolean isOffline()', 'static Context context=new Context();static Context getContext(){return context;}static boolean offline;static boolean isOffline()')
code = code.replace('if(op.endsWith("camp.query"))', '''if(op.equals("com.alipay.goldenbean.index")){
    assert !write;root.put("taskList",new JSONArray().put(new JSONObject().put("taskId","game").put("taskStatus",beanState).put("taskDisplayConfig",new JSONObject().put("title","这关我很行").put("targetUrl","alipays://platformapi/startapp?appId=2021005132680209"))));
   }else if(op.equals("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction")){
    assert write&&a.optString("source").equals("lianyun_nc_flrw")&&a.optInt("playTime")<=30;
    durationWrites++;assert TimeUtil.waited>=30L*durationWrites;
   }else if(op.equals("com.alipay.antiep.finishTask")){
    assert write&&TimeUtil.waited==60&&a.optString("sceneCode").equals("ANTFARM_ORCHARD_TASK_V2")&&a.optString("source").equals("hysjccl")&&a.optString("version").equals("0.1.2609041617.33");if(advance)farmState="FINISHED";
   }else if(op.equals("com.alipay.antorchard.receiveOrchardVisitAward")){
    assert write&&a.optBoolean("manualReceive")&&a.optString("diversionSource").equals("DEFAULT");if(advance){manual=false;root.put("manureCount",8);}
   }else if(op.endsWith("camp.query"))''')
code += r'''
 static String beanState="TODO";static int durationWrites;static String farmState="TODO";static boolean manual,drift;
 static JSONObject farmRow(){return new JSONObject().put("taskId","visit").put("taskStatus",farmState).put("actionType","VISIT").put("sceneCode","ANTFARM_ORCHARD_TASK_V2")
  .put("taskDisplayConfig",new JSONObject().put("title",drift?"购买商品":"浏览游戏").put("targetUrl","alipays://platformapi/startapp?appId=2021005132680209").put("floatBallConfig",new JSONObject().put("floatBallDuration",60)));}
 static class AntOrchardRpcCall{
  static String orchardListTask(){return new JSONObject().put("resultCode","100").put("taskList",rows(farmRow())).toString();}
  static String receiveOrchardVisitAward(){return new JSONObject().put("success",true).put("canCollect",manual).put("needManualReceive",manual).toString();}
 }
 static class SimplePageManager{
  static Handler handler=new Handler();static Activity activity;
  static Activity getTopActivity(){return activity;}
 }
 static class XRiverActivity extends Activity{}
 public interface ValueCallback {void onReceiveValue(String value);}
 public static class HostWeb extends ViewGroup {
  static int calls;static Runnable beforeCallback;
  public int getChildCount(){return 1;}public View getChildAt(int i){return new View();}
  public void evaluateJavascript(String script,ValueCallback callback){assert script.contains("sesame-dog");calls++;if(beforeCallback!=null)beforeCallback.run();callback.onReceiveValue("\"RIDE\"");}
 }
 public static class MYWebView extends HostWeb {}
 public static class WebViewEx extends HostWeb {}
 public static class AndroidWebView extends HostWeb {}
 @@FARM@@
 @@UI@@
 static void clean(){reset();beanState="TODO";durationWrites=0;farmState="TODO";manual=true;drift=false;Context.interactive=true;Context.locked=false;Handler.before=null;SimplePageManager.activity=null;}
 static boolean farmPending(){return !RuntimeInfo.instance.getString("orchardSjReceipt::float:visit").isEmpty();}
 static void tap()throws Exception{
  Method m=SjGamePlay.class.getDeclaredMethod("tap",String.class,int.class,int.class,String.class,int.class,long.class);m.setAccessible(true);
  try{assert (Boolean)m.invoke(null,"2021005132680209",540,1200,"self",20261008,TaskLifecycle.generation());}
  catch(InvocationTargetException e){if(e.getCause() instanceof Exception)throw (Exception)e.getCause();throw e;}
 }
 public static void main(String[] args)throws Exception{
  clean();AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==3&&durationWrites==2&&TimeUtil.waited==60&&Log.confirmed==1&&!farmPending();
  clean();AntOrchardVisitTask.floatBall(farmRow(),0);assert writes==0&&TimeUtil.waited==0;
  clean();duplicate=true;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==0;
  clean();drift=true;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==0;
  clean();JSONObject row=farmRow();row.optJSONObject("taskDisplayConfig").put("targetUrl","alipays://platformapi/startapp?appId=2021005132680209&appId=2021005132680209");AntOrchardVisitTask.floatBall(row,4);assert writes==0;
  clean();RuntimeInfo.writable=false;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==0;
  clean();RuntimeInfo.data.put("orchardSjAttempts","broken");AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==0;
  clean();advance=false;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==3&&farmPending()&&Log.confirmed==0;MyUtils.day++;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==3;
  clean();badAck=true;AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==1&&farmPending();
  clean();AntOrchardVisitTask.floatBall(farmRow(),1);assert writes==0&&farmPending();AntOrchardVisitTask.floatBall(farmRow(),4);assert writes==0;
  clean();TimeUtil.switchWait=true;try{AntOrchardVisitTask.floatBall(farmRow(),4);throw new AssertionError("switch swallowed");}catch(TaskCancelledException expected){}assert writes==0&&farmPending();
  clean();TimeUtil.crossWait=true;try{AntOrchardVisitTask.floatBall(farmRow(),4);throw new AssertionError("day swallowed");}catch(TaskCancelledException expected){}assert writes==0&&farmPending();
  clean();AntOrchardVisitTask.manualAward(new JSONObject(AntOrchardRpcCall.receiveOrchardVisitAward()),1);assert writes==1&&!manual&&Log.confirmed==1;
  clean();advance=false;AntOrchardVisitTask.manualAward(new JSONObject(AntOrchardRpcCall.receiveOrchardVisitAward()),1);assert writes==1&&manual&&Log.confirmed==0;AntOrchardVisitTask.manualAward(new JSONObject(AntOrchardRpcCall.receiveOrchardVisitAward()),2);assert writes==1;
  clean();assert SjGamePlay.gameApp("alipays://platformapi/startapp?appId=2021005132680209").equals("2021005132680209");assert SjGamePlay.gameApp("https://platformapi/startapp?appId=2021005132680209").isEmpty();assert SjGamePlay.gameApp("alipays://platformapi/startapp?appId=2021005132680209&appId=2021004173661702").isEmpty();
  XRiverActivity a=new XRiverActivity();a.intent=new Intent(Intent.ACTION_VIEW,Uri.parse("alipays://platformapi/startapp?appId=2021005132680209"));SimplePageManager.activity=a;View view=a.window.root;tap();assert view.events.equals(List.of(0,1))&&view.x==270&&view.y==600;
  view.events.clear();Context.locked=true;Method root=SjGamePlay.class.getDeclaredMethod("gameRoot",String.class);root.setAccessible(true);assert root.invoke(null,"2021005132680209")==null;Context.locked=false;
  a.intent=new Intent(Intent.ACTION_VIEW,Uri.parse("alipays://platformapi/startapp?appId=2021004173661702"));assert root.invoke(null,"2021005132680209")==null;
  a.intent=new Intent(Intent.ACTION_VIEW,Uri.parse("alipays://platformapi/startapp?appId=120210051326802090"));assert root.invoke(null,"2021005132680209")==null;
  a.intent=new Intent(Intent.ACTION_VIEW,Uri.parse("alipays://platformapi/startapp?appId=2021005132680209"));view.down=()->UserIdMap.uid="other";try{tap();throw new AssertionError("touch account switch swallowed");}catch(TaskCancelledException expected){}assert view.events.equals(List.of(0,3));
  UserIdMap.uid="self";view.down=null;view.events.clear();TaskLifecycle.Freeze[] freeze=new TaskLifecycle.Freeze[1];Handler.before=()->freeze[0]=TaskLifecycle.freezeIfIdle();try{tap();throw new AssertionError("generation accepted");}catch(TaskCancelledException expected){}assert view.events.isEmpty();Handler.before=null;TaskLifecycle.thaw(freeze[0]);assert TaskLifecycle.isIdle();
  clean();SimplePageManager.activity=a;view.events.clear();view.down=()->{if(view.events.stream().filter(n->n==0).count()==6)beanState="FINISHED";};SjGamePlay.goldenBeans(true,1,Set.of());assert view.events.size()==12&&writes==0&&Log.confirmed==1&&TimeUtil.waited>=49&&!pending("game:2021005132680209");SjGamePlay.goldenBeans(true,1,Set.of());assert view.events.size()==12&&writes==0;
  clean();SimplePageManager.activity=a;view.events.clear();view.down=()->Context.locked=true;try{tap();throw new AssertionError("lock swallowed");}catch(TaskCancelledException expected){}assert view.events.equals(List.of(0,3));view.down=null;view.events.clear();Context.locked=false;
  Context.interactive=false;int before=queries;SjGamePlay.goldenBeans(true,1,Set.of());assert queries==before&&view.events.isEmpty();
  for(HostWeb engine:new HostWeb[]{new MYWebView(),new WebViewEx(),new AndroidWebView()}){
   clean();HostWeb.calls=0;HostWeb.beforeCallback=null;a.intent=new Intent(Intent.ACTION_VIEW,Uri.parse("alipays://platformapi/startapp?appId=2018073060792690"));a.window.root=engine;SimplePageManager.activity=a;
   SjGamePlay.ride(new SjActivityTasks(new OtherRequestGate(),1));assert HostWeb.calls==30&&!pending("ride")&&writes==0:"source host engine was not driven: "+engine.getClass();
  }
  clean();HostWeb.calls=0;HostWeb.beforeCallback=()->UserIdMap.uid="other";a.window.root=new MYWebView();SimplePageManager.activity=a;
  try{SjGamePlay.ride(new SjActivityTasks(new OtherRequestGate(),1));throw new AssertionError("stale JS callback accepted");}catch(TaskCancelledException expected){}assert HostWeb.calls==1&&pending("ride");HostWeb.beforeCallback=null;
  clean();HostWeb.calls=0;a.window.root=new HostWeb();SimplePageManager.activity=a;SjGamePlay.ride(new SjActivityTasks(new OtherRequestGate(),1));assert HostWeb.calls==0&&pending("ride"):"unknown host must stay untouched";
  System.out.println("PASS production orchard/native: full duration, receipts, quotas, manual claims, URI/window ownership, coordinate scale, cancelled gestures and expired callbacks");
 }
}
'''
for marker, directory, filename in [('@@GATE@@', 'other', 'OtherRequestGate.java'), ('@@WORKER@@', 'other', 'SjActivityTasks.java'),
                                    ('@@FARM@@', 'antOrchard', 'AntOrchardVisitTask.java'), ('@@UI@@', 'other', 'SjGamePlay.java')]:
    source = (SOURCE / 'model/task' / directory / filename).read_text(encoding='utf-8')
    start = source.index('public final class ' if marker == '@@UI@@' else 'final class ')
    body = source[start:].replace('public final class ', 'final class ', 1)
    body = body.replace('io.github.aw1y2z.sesame.data.RuntimeInfo', 'RuntimeInfo')
    code = code.replace(marker, 'static ' + body)

stubs = {
 'android/net/Uri.java': '''package android.net;import java.util.*;public class Uri{java.net.URI u;private Uri(String s){u=java.net.URI.create(s);}public static Uri parse(String s){return new Uri(s);}public String getScheme(){return u.getScheme();}public String getHost(){return u.getHost();}public String getPath(){return u.getPath();}public List<String> getQueryParameters(String key){List<String> a=new ArrayList<>();String q=u.getRawQuery();if(q!=null)for(String p:q.split("&")){String[] x=p.split("=",2);if(java.net.URLDecoder.decode(x[0],java.nio.charset.StandardCharsets.UTF_8).equals(key))a.add(x.length>1?java.net.URLDecoder.decode(x[1],java.nio.charset.StandardCharsets.UTF_8):"");}return a;}public String getQueryParameter(String k){List<String>a=getQueryParameters(k);return a.isEmpty()?null:a.get(0);}public String toString(){return u.toString();}}''',
 'android/content/Context.java': '''package android.content;public class Context{public static boolean interactive=true,locked;public static final String POWER_SERVICE="power",KEYGUARD_SERVICE="keyguard";public Object getSystemService(String k){return k.equals(POWER_SERVICE)?new android.os.PowerManager():new android.app.KeyguardManager();}public String getPackageName(){return "com.eg.android.AlipayGphone";}public void startActivity(Intent i){}}''',
 'android/content/Intent.java': '''package android.content;public class Intent{public static final String ACTION_VIEW="view";public static final int FLAG_ACTIVITY_NEW_TASK=1;android.net.Uri u;public Intent(String a,android.net.Uri v){u=v;}public Intent setPackage(String p){return this;}public Intent addFlags(int f){return this;}public String toUri(int f){return u.toString();}}''',
 'android/app/Activity.java': '''package android.app;public class Activity extends android.content.Context{public android.content.Intent intent;public android.view.Window window=new android.view.Window();public boolean isFinishing(){return false;}public boolean hasWindowFocus(){return true;}public android.view.Window getWindow(){return window;}public android.content.Intent getIntent(){return intent;}}''',
 'android/app/KeyguardManager.java': 'package android.app;public class KeyguardManager{public boolean isKeyguardLocked(){return android.content.Context.locked;}}',
 'android/os/PowerManager.java': 'package android.os;public class PowerManager{public boolean isInteractive(){return android.content.Context.interactive;}}',
 'android/os/Looper.java': 'package android.os;public class Looper{static Looper main=new Looper();public static Looper getMainLooper(){return main;}public static Looper myLooper(){return null;}}',
 'android/os/Handler.java': 'package android.os;public class Handler{public static Runnable before;public boolean post(Runnable r){if(before!=null)before.run();r.run();return true;}}',
 'android/os/SystemClock.java': 'package android.os;public class SystemClock{public static long uptimeMillis(){return 1000;}}',
 'android/view/Window.java': 'package android.view;public class Window{public View root=new View();public View getDecorView(){return root;}}',
 'android/view/View.java': '''package android.view;public class View{public java.util.List<Integer> events=new java.util.ArrayList<>();public float x,y;public Runnable down;public boolean isShown(){return true;}public boolean isAttachedToWindow(){return true;}public int getWidth(){return 540;}public int getHeight(){return 1200;}public boolean dispatchTouchEvent(MotionEvent e){events.add(e.action);x=e.x;y=e.y;if(e.action==0&&down!=null)down.run();return true;}}''',
 'android/view/ViewGroup.java': 'package android.view;public class ViewGroup extends View{public int getChildCount(){return 0;}public View getChildAt(int i){return null;}}',
 'android/view/MotionEvent.java': '''package android.view;public class MotionEvent{public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;public int action;public float x,y;public static MotionEvent obtain(long d,long t,int a,float x,float y,int m){MotionEvent e=new MotionEvent();e.action=a;e.x=x;e.y=y;return e;}public void recycle(){}}''',
 'android/webkit/WebView.java': 'package android.webkit;public class WebView extends android.view.View{}',
}
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-sj-native-') as tmp:
    files = []
    for filename, text in {'SjNativeCheck.java': code, **stubs}.items():
        path = Path(tmp) / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
        files.append(str(path))
    files += [str(SOURCE / f) for f in ['util/TaskCancelledException.java', 'data/task/TaskLifecycle.java',
                                      'rpc/intervallimit/RequestBudgetPolicy.java', 'rpc/intervallimit/RpcFailurePolicy.java']]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, *files], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), 'SjNativeCheck'], check=True)
