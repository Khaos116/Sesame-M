"""Replay production startup dispatch with slow host/config calls and real lifecycle admission."""
from pathlib import Path
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

hook = "hook/ApplicationHook.java"
code = r'''
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class StartupCheck {
 static CountDownLatch entered, release;
 static volatile String slow;
 static volatile boolean throwAccount,accepted=true,permission=true;
 static final AtomicInteger accounts=new AtomicInteger(),configs=new AtomicInteger(),agreements=new AtomicInteger();
 static final AtomicBoolean mainRead=new AtomicBoolean();
 static void read(String kind){
  if(Looper.myLooper()==Looper.getMainLooper())mainRead.set(true);
  if(kind.equals(slow)){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("read never released");}catch(InterruptedException e){throw new AssertionError(e);}}
 }
 static class Looper {
  static final Object MAIN=new Object();static final ThreadLocal<Boolean> IS_MAIN=ThreadLocal.withInitial(()->false);
  static Object myLooper(){return IS_MAIN.get()?MAIN:null;}static Object getMainLooper(){return MAIN;}
 }
 static class Agreement {static boolean isAccepted(){agreements.incrementAndGet();read("agreement");return accepted;}}
 static class AppConfig {static void load(){configs.incrementAndGet();read("config");}}
 static class PermissionUtil {static boolean checkAlarmPermissions(){read("permission");return permission;}static boolean checkOrRequestAlarmPermissions(Object c){return permission;}}
 static class Toast {static void show(String text){}static final int LENGTH_SHORT=0;static Toast makeText(Object c,String s,int n){return new Toast();}void show(){}}
 static class Log {static void record(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){}static void printStackTrace(Throwable e){}}
 static class Handler {
  final Queue<Runnable> jobs=new ConcurrentLinkedQueue<>();void post(Runnable r){jobs.add(r);}void postDelayed(Runnable r,long delay){jobs.add(r);}void removeCallbacks(Runnable r){jobs.remove(r);}
 }
 static class BaseModel {static void initData(){}}
 static class UserIdMap {static volatile String uid="A";static final AtomicInteger initCalls=new AtomicInteger();static String getCurrentUid(){return uid;}static void initUser(String next){read("friends");initCalls.incrementAndGet();uid=next;}}
 static class Activity {boolean finished;boolean isFinishing(){return finished;}boolean isDestroyed(){return false;}void finish(){assert Looper.myLooper()==Looper.getMainLooper();finished=true;}}
 @@LIFECYCLE@@
 static class ApplicationHook {
  static final String TAG="hook";
  static volatile Object service=new Object(),context=new Object();static volatile boolean initializing,checkingLauncherAccount,init,offline;
  static Handler mainHandler=new Handler();static final AtomicReference<Runnable> pendingAccountReload=new AtomicReference<>();
  static volatile String hostUid="A";
  final AtomicInteger initialized=new AtomicInteger();volatile TaskLifecycle.Freeze seenOwner;volatile boolean seenExec;
  static String getUserId(){accounts.incrementAndGet();read("account");if(throwAccount)throw new IllegalStateException("host not ready");return hostUid;}
  private Boolean initializeHandler(Boolean force,boolean exec){assert Looper.myLooper()!=Looper.getMainLooper();assert !TaskLifecycle.isIdle();initialized.incrementAndGet();seenExec=exec;return true;}
  static final List<java.lang.Thread> workers=new CopyOnWriteArrayList<>();static volatile CountDownLatch workerGate,workerStarted;static boolean failStart;
  static class Thread extends java.lang.Thread {
   Thread(Runnable job,String name){super(job,name);workers.add(this);}
   @Override public synchronized void start(){if(failStart)throw new IllegalStateException("thread unavailable");super.start();}
   @Override public void run(){CountDownLatch gate=workerGate;if(gate!=null){workerStarted.countDown();try{gate.await();}catch(InterruptedException e){throw new AssertionError(e);}}super.run();}
  }
  static int executes;static void execHandler(){executes++;}
  @@LAUNCHER@@
  static class MainTask {void stopTask(){}}static MainTask mainTask=new MainTask();static class ModelTask {static void stopAllTask(){}}
  static final Runnable MAIN_TICK=()->{};static boolean tickScheduled;static long scheduledExecAt;
  @@STOP@@
  @@RELOAD@@
  private Boolean initHandler(Boolean force){return initHandler(force,null,true);}
  @@INIT@@
  @@RUNINIT@@
 }
 static void reset(){ApplicationHook.init=false;ApplicationHook.offline=false;UserIdMap.uid="A";UserIdMap.initCalls.set(0);ApplicationHook.executes=0;ApplicationHook.hostUid="A";slow=null;throwAccount=false;accepted=true;permission=true;accounts.set(0);configs.set(0);agreements.set(0);mainRead.set(false);ApplicationHook.service=new Object();ApplicationHook.mainHandler.jobs.clear();}
 static void idle()throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(ApplicationHook.initializing&&System.nanoTime()<until)Thread.sleep(5);assert !ApplicationHook.initializing:"initializing stuck after preflight";}
 static void callMain(Runnable action)throws Exception {AtomicReference<Throwable> failed=new AtomicReference<>();Thread ui=new Thread(()->{Looper.IS_MAIN.set(true);try{action.run();}catch(Throwable t){failed.set(t);}});ui.start();ui.join(3000);assert !ui.isAlive():"main never returned";if(failed.get()!=null)throw new AssertionError(failed.get());}
 static void slowRead(String kind)throws Exception {
  reset();slow=kind;if(kind.equals("config"))accepted=false;entered=new CountDownLatch(1);release=new CountDownLatch(1);
  ApplicationHook hook=new ApplicationHook();CountDownLatch returned=new CountDownLatch(1);
  Thread ui=new Thread(()->{Looper.IS_MAIN.set(true);try{hook.initHandler(true,null,true);}finally{returned.countDown();}});ui.start();
  assert entered.await(2,TimeUnit.SECONDS):"slow dependency not reached";
  boolean prompt=returned.await(300,TimeUnit.MILLISECONDS);
  int before=accounts.get();
  if(prompt)callMain(()->{assert Boolean.FALSE.equals(hook.initHandler(true,null,true));});
  assert accounts.get()==before:"duplicate init queried account";
  release.countDown();ui.join(2000);idle();
  assert prompt&&!mainRead.get():"startup main thread blocked on "+kind;
 }
 static void workersDone()throws Exception {for(java.lang.Thread worker:ApplicationHook.workers){worker.join(3000);assert !worker.isAlive():"startup worker did not stop";}ApplicationHook.workers.clear();}
 static void gateWorker(){ApplicationHook.workerGate=new CountDownLatch(1);ApplicationHook.workerStarted=new CountDownLatch(1);}
 static void releaseWorker()throws Exception {ApplicationHook.workerGate.countDown();workersDone();ApplicationHook.workerGate=null;}
 static void advanceGeneration(){TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);}
 static void launcher()throws Exception {
  reset();ApplicationHook.init=true;ApplicationHook hook=new ApplicationHook();Activity activity=new Activity();
  slow="account";entered=new CountDownLatch(1);release=new CountDownLatch(1);CountDownLatch returned=new CountDownLatch(1);
  Thread ui=new Thread(()->{Looper.IS_MAIN.set(true);try{hook.onLauncherResume(activity);}finally{returned.countDown();}});ui.start();
  assert entered.await(2,TimeUnit.SECONDS);boolean prompt=returned.await(300,TimeUnit.MILLISECONDS);release.countDown();ui.join(2000);workersDone();
  assert prompt&&!mainRead.get():"Launcher account query blocked main";
  reset();ApplicationHook.hostUid="B";
  hook.scheduleAccountReload();Runnable superseded=ApplicationHook.mainHandler.jobs.remove();hook.scheduleAccountReload();
  int beforeOldWorker=ApplicationHook.workers.size();callMain(superseded);workersDone();
  assert beforeOldWorker==0&&accounts.get()==0:"superseded reload callback started worker";
  hook.scheduleAccountReload();assert ApplicationHook.mainHandler.jobs.size()==1:"old callback cleared replacement identity";
  Runnable stopped=ApplicationHook.mainHandler.jobs.remove();ApplicationHook.stopHandler();callMain(stopped);workersDone();assert accounts.get()==0:"stopped reload callback started worker";
  for(String next:List.of("A","B")){
  reset();ApplicationHook.init=true;ApplicationHook.hostUid=next;ApplicationHook.offline=true;slow="account";entered=new CountDownLatch(1);release=new CountDownLatch(1);
  callMain(()->hook.onLauncherResume(new Activity()));assert entered.await(2,TimeUnit.SECONDS);int beforeDuplicate=accounts.get(),beforeWorkers=ApplicationHook.workers.size();
  callMain(()->hook.onLauncherResume(new Activity()));
  boolean coalesced=accounts.get()==beforeDuplicate&&ApplicationHook.workers.size()==beforeWorkers;
  release.countDown();workersDone();assert coalesced&&ApplicationHook.executes==(next.equals("A")?1:0)&&ApplicationHook.mainHandler.jobs.size()==1:"overlapping Launcher checks were not coalesced";
  }
  for(boolean staleGeneration:List.of(true,false)){
   reset();ApplicationHook.init=true;gateWorker();callMain(()->hook.onLauncherResume(new Activity()));assert ApplicationHook.workerStarted.await(2,TimeUnit.SECONDS);
   if(staleGeneration)advanceGeneration();else UserIdMap.uid="B";
   releaseWorker();assert accounts.get()==0&&UserIdMap.initCalls.get()==0:"stale Launcher entered host or friend initialization";
  }
  reset();ApplicationHook.init=true;slow="account";entered=new CountDownLatch(1);release=new CountDownLatch(1);
  callMain(()->hook.onLauncherResume(new Activity()));assert entered.await(2,TimeUnit.SECONDS);UserIdMap.uid="B";release.countDown();workersDone();assert UserIdMap.initCalls.get()==0&&ApplicationHook.executes==0:"account change during host lookup was ignored";
  reset();ApplicationHook.init=true;UserIdMap.uid=null;slow="friends";entered=new CountDownLatch(1);release=new CountDownLatch(1);
  callMain(()->hook.onLauncherResume(new Activity()));assert entered.await(2,TimeUnit.SECONDS);release.countDown();workersDone();assert !mainRead.get()&&UserIdMap.initCalls.get()==1:"friend initialization returned to main";
  for(String stale:List.of("none","generation","uid")){
   reset();ApplicationHook.init=true;ApplicationHook.offline=true;Activity finishing=new Activity();callMain(()->hook.onLauncherResume(finishing));workersDone();assert ApplicationHook.executes==1;
   if(stale.equals("generation"))advanceGeneration();else if(stale.equals("uid"))UserIdMap.uid="B";
   int before=accounts.get();callMain(()->{Runnable task;while((task=ApplicationHook.mainHandler.jobs.poll())!=null)task.run();});
   assert finishing.finished==stale.equals("none"):"finish ignored stale "+stale;assert accounts.get()==before:"finish queried host on main";
  }
  reset();ApplicationHook.init=true;ApplicationHook.hostUid="B";slow="account";entered=new CountDownLatch(1);release=new CountDownLatch(1);
  hook.scheduleAccountReload();callMain(()->ApplicationHook.mainHandler.jobs.remove().run());assert entered.await(2,TimeUnit.SECONDS);release.countDown();workersDone();assert !mainRead.get():"delayed account reload queried host on main";
  for(boolean staleGeneration:List.of(true,false)){
   reset();ApplicationHook.hostUid="B";gateWorker();hook.scheduleAccountReload();callMain(()->ApplicationHook.mainHandler.jobs.remove().run());assert ApplicationHook.workerStarted.await(2,TimeUnit.SECONDS);
   if(staleGeneration)advanceGeneration();else UserIdMap.uid="C";
   releaseWorker();assert accounts.get()==0:"delayed reload ignored stale account/generation";
  }
 }
 public static void main(String[] args)throws Exception {
  launcher();
  for(String dependency:List.of("account","agreement","config","permission"))slowRead(dependency);
  for(String failure:List.of("agreement","service","account","permission","exception")){
   reset();ApplicationHook hook=new ApplicationHook();
   if(failure.equals("agreement"))accepted=false;
   if(failure.equals("service"))ApplicationHook.service=null;
   if(failure.equals("account"))ApplicationHook.hostUid=null;
   if(failure.equals("permission"))permission=false;
   if(failure.equals("exception"))throwAccount=true;
   callMain(()->hook.initHandler(true,null,true));idle();assert hook.initialized.get()==0:failure;
   reset();ApplicationHook.hostUid="A";callMain(()->hook.initHandler(true,null,true));idle();assert hook.initialized.get()==1:"failed preflight prevented retry: "+failure;
  }
  reset();ApplicationHook hook=new ApplicationHook();assert Boolean.TRUE.equals(hook.initHandler(true,null,false));assert hook.initialized.get()==1&&!hook.seenExec:"background lost synchronous return";
  TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle();assert owner!=null;assert Boolean.TRUE.equals(hook.initHandler(true,owner,true));TaskLifecycle.thaw(owner);assert TaskLifecycle.isIdle();
  reset();ApplicationHook.failStart=true;callMain(()->hook.initHandler(true,null,true));assert !ApplicationHook.initializing;ApplicationHook.failStart=false;workersDone();
  reset();ApplicationHook.init=true;ApplicationHook.failStart=true;callMain(()->hook.onLauncherResume(new Activity()));assert !ApplicationHook.checkingLauncherAccount;ApplicationHook.failStart=false;workersDone();
  reset();ApplicationHook.init=true;throwAccount=true;callMain(()->hook.onLauncherResume(new Activity()));workersDone();assert !ApplicationHook.checkingLauncherAccount;throwAccount=false;callMain(()->hook.onLauncherResume(new Activity()));workersDone();assert accounts.get()>=2;
  reset();gateWorker();callMain(()->hook.initHandler(true,null,true));assert ApplicationHook.workerStarted.await(2,TimeUnit.SECONDS);advanceGeneration();releaseWorker();idle();assert agreements.get()==0&&accounts.get()==0:"stale initialization read account/config";
  reset();owner=TaskLifecycle.freezeIfIdle();assert owner!=null;final TaskLifecycle.Freeze capturedOwner=owner;int initializedBefore=hook.initialized.get();callMain(()->hook.initHandler(true,capturedOwner,true));idle();workersDone();assert hook.initialized.get()==initializedBefore+1;TaskLifecycle.thaw(owner);assert TaskLifecycle.isIdle();
  System.out.println("PASS startup threading: Launcher/reload generation and UID checks, worker friend initialization, guarded UI finish, slow dependencies off main, duplicate coalescing, false/exception recovery, synchronous background/freeze initialization");
 }
}
'''
hook_source = (SOURCE / hook).read_text(encoding="utf-8")
launcher = method(hook, "private void onLauncherResume(")
launcher_hook = hook_source[hook_source.index('XHelpers.findAndHookMethod("com.alipay.mobile.quinox.LauncherActivity"'):]
assert "onLauncherResume((Activity) param.thisObject);" in launcher_hook.split("catch (Throwable", 1)[0]
code = code.replace("@@LAUNCHER@@", launcher)
for key, path, signature in (
    ("@@STOP@@", hook, "private static void stopHandler("),
    ("@@RELOAD@@", hook, "private void scheduleAccountReload("),
    ("@@INIT@@", hook, "private Boolean initHandler(Boolean force, TaskLifecycle.Freeze owner, boolean exec)"),
    ("@@RUNINIT@@", hook, "private synchronized Boolean runInit("),
    ("@@LIFECYCLE@@", "data/task/TaskLifecycle.java", "public final class TaskLifecycle"),
):
    source = method(path, signature).replace("public final class TaskLifecycle", "static final class TaskLifecycle")
    code = code.replace(key, source.replace("android.widget.Toast", "Toast"))
with tempfile.TemporaryDirectory(prefix="sesame-startup-") as temporary:
    java = Path(temporary) / "StartupCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temporary, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary, "StartupCheck"], check=True)

# Replay the full production initialization sequence with host-service doubles.
initialization_code = r'''
import java.util.*;
public class InitPublicationCheck {
 static String hostUid="A";static boolean rejectUser,changeDuringBoot;static final List<String> events=new ArrayList<>();
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}static void initUser(String expected){events.add("user");if(rejectUser){hostUid="B";uid="B";return;}uid=expected;}}
 static class Log {static void record(String s){}static void i(String t,String s){}static void printStackTrace(Throwable t){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class Toast {static final int LENGTH_SHORT=0;static void show(String s){}static Toast makeText(Object c,String s,int n){return new Toast();}void show(){}}
 static class Context {static final String POWER_SERVICE="power";Object getSystemService(String s){return new PowerManager();}}
 static class PowerManager {static final int PARTIAL_WAKE_LOCK=1;static class WakeLock {void acquire(){}}WakeLock newWakeLock(int n,String s){return new WakeLock();}}
 static class Handler {void postDelayed(Runnable r,long delay){throw new AssertionError("unexpected permission request");}}
 static class Value<T> {final T value;Value(T v){value=v;}T getValue(){return value;}}
 static class BaseModel {Value<Boolean> getEnableField(){return new Value<>(true);}static Value<Boolean> getStayAwake(){return new Value<>(false);}static Value<Integer> getCheckInterval(){return new Value<>(1000);}static void initRpcRequest(){}static void initData(){events.add("data");}}
 static class Model {static BaseModel getModel(Class<?> c){return new BaseModel();}static void initAllModel(){events.add("models");}static void bootAllModel(Object c){events.add("boot");if(changeDuringBoot)hostUid="B";}}
 static class Statistics {static void save(){}static void load(){events.add("statistics");}}
 static class FriendWatch {static void save(){}static void load(){}}
 static class ConfigV2 {static void load(String uid){events.add("config:"+uid);}}
 static class AppConfig {static final AppConfig INSTANCE=new AppConfig();static boolean shouldRequestBatteryPermission(){return false;}boolean getNewRpc(){return true;}}
 static class PermissionUtil {static boolean checkBatteryPermissions(){return true;}static boolean checkOrRequestBatteryPermissions(Object c){return true;}}
 static class MyUtils {static String recordUserName(String s){return s;}}
 static class ModuleHttpServerManager {static ModuleHttpServerManager getInstance(){return new ModuleHttpServerManager();}void startIfNeeded(int port,String token,String process,String host){}}
 static class NewRpcBridge {void load(){}String getVersion(){return "v";}}static class OldRpcBridge extends NewRpcBridge {}
 static class NotificationUtil {static void start(Object service){}static void updateIdleText(){}}
 static class CaptchaHook {static void setupHook(Object c){}}static class Status {static void load(){}}static class TokenConfig {static void load(){}}
 @@LIFECYCLE@@
 static class Hook {
  static final String TAG="init",modelVersion="test",processName="host";static final long FALLBACK_INTERVAL=1;
  static Context service=new Context(),context=service;static Object classLoader;static Handler mainHandler=new Handler();
  static boolean init,offline;static NewRpcBridge rpcBridge;static String rpcVersion;static PowerManager.WakeLock wakeLock;
  static String getUserId(){return hostUid;}static void destroyHandler(Boolean force){}static String resolveHttpToken(){return "token";}static void logInitializationStart(){}
  static void setWakenAtTimeAlarm(){}static void installRpcRecordHook(){}static void updateDay(){}static void execHandler(){events.add("execute");}static void execDelayedHandler(long n){events.add("delayed");}
  @@INITIALIZE@@
  @@RUNINIT@@
 }
 static void reset(){events.clear();hostUid="A";UserIdMap.uid="A";rejectUser=false;changeDuringBoot=false;Hook.init=false;}
 public static void main(String[] args){
  Hook hook=new Hook();reset();rejectUser=true;assert !hook.runInit(true,null,true);assert !Hook.init&&!events.contains("statistics")&&!events.contains("config:A")&&!events.contains("execute"):"rejected account initialization continued loading old account";
  reset();changeDuringBoot=true;assert !hook.runInit(true,null,true)&&!Hook.init&&!events.contains("execute"):"host changed during boot but initialization published ready";
  reset();assert hook.runInit(true,null,true)&&Hook.init;assert events.indexOf("user")<events.indexOf("statistics")&&events.indexOf("statistics")<events.indexOf("config:A")&&events.indexOf("config:A")<events.indexOf("boot")&&events.indexOf("boot")<events.indexOf("execute");assert events.contains("data");
  reset();TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle();assert owner!=null;assert hook.runInit(true,owner,false)&&Hook.init&&events.contains("delayed")&&!events.contains("execute")&&!events.contains("data");TaskLifecycle.thaw(owner);assert TaskLifecycle.isIdle();
  System.out.println("PASS initialization publication: rejected account and host change never publish ready; normal ordering and freeze-owner path preserved");
 }
}
'''
for key, path, signature in (
    ("@@INITIALIZE@@", hook, "private Boolean initializeHandler("),
    ("@@RUNINIT@@", hook, "private synchronized Boolean runInit("),
    ("@@LIFECYCLE@@", "data/task/TaskLifecycle.java", "public final class TaskLifecycle"),
):
    initialization_code = initialization_code.replace(key, method(path, signature).replace("public final class TaskLifecycle", "static final class TaskLifecycle").replace("android.widget.Toast", "Toast"))
with tempfile.TemporaryDirectory(prefix="sesame-init-publication-") as temporary:
    java = Path(temporary) / "InitPublicationCheck.java"
    java.write_text(initialization_code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temporary, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary, "InitPublicationCheck"], check=True)
