"""Production host profile/file and dialog callback replays; no Android device required."""
from pathlib import Path
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

hook = "hook/ApplicationHook.java"
code = r'''
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class HostUiIoCheck {
 static final String TAG="test",PENDING_PROFILE_FILE="pending_profile.txt";
 static final long PENDING_PROFILE_TTL=20000L;
 static final Map<String,String> files=new ConcurrentHashMap<>();static volatile String blocking;static CountDownLatch entered,release;
 static final AtomicBoolean mainIo=new AtomicBoolean();static final AtomicInteger reads=new AtomicInteger(),deletes=new AtomicInteger(),opens=new AtomicInteger();
 static final ThreadLocal<Boolean> isMain=ThreadLocal.withInitial(()->false);
 static void io(String operation){if(isMain.get())mainIo.set(true);if(operation.equals(blocking)){entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("I/O release timed out");}catch(InterruptedException e){throw new AssertionError(e);}}}
 static class ProbeFile {final String path;ProbeFile(String path){this.path=path;}ProbeFile(ProbeFile root,String path){this.path=path;}boolean exists(){io("exists");return files.containsKey(path);}boolean delete(){io("delete");deletes.incrementAndGet();return files.remove(path)!=null;}boolean renameTo(ProbeFile target){io("rename");synchronized(files){String text=files.remove(path);if(text==null)return false;files.put(target.path,text);return true;}}}
 static class FileUtil {static final ProbeFile MAIN_DIRECTORY_FILE=new ProbeFile("root");static String readFromFile(ProbeFile file){io("read");reads.incrementAndGet();return files.get(file.path);}}
 static class Context {
  Context getApplicationContext(){return this;}
  static final int RECEIVER_EXPORTED=2;BroadcastReceiver receiver;Handler scheduler;
  void unregisterReceiver(BroadcastReceiver r){receiver=null;}
  void registerReceiver(BroadcastReceiver r,IntentFilter f){receiver=r;}
  void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receiver=r;}
  void registerReceiver(BroadcastReceiver r,IntentFilter f,String permission,Handler handler){receiver=r;scheduler=handler;}
  void registerReceiver(BroadcastReceiver r,IntentFilter f,String permission,Handler handler,int flags){receiver=r;scheduler=handler;}
  void deliver(Intent i,CountDownLatch done){Runnable callback=()->{inReceiver.set(true);try{receiver.onReceive(this,i);}finally{inReceiver.set(false);done.countDown();}};if(scheduler==null)callback.run();else scheduler.post(callback);}
 }
 static class BroadcastReceiver {void onReceive(Context c,Intent i){}}
 static class IntentFilter {void addAction(String s){}}
 static class Intent {final String action;String token="valid",op="reloadConfig";Intent(String a){action=a;}String getAction(){return action;}String getStringExtra(String key){return key.equals("token")?token:key.equals("op")?op:null;}boolean getBooleanExtra(String key,boolean fallback){return fallback;}}
 static class Build {static class VERSION {static int SDK_INT=33;}static class VERSION_CODES {static final int TIRAMISU=33;}}
 static class HandlerThread {final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r);t.setDaemon(true);return t;});HandlerThread(String name){}void start(){}Object getLooper(){return executor;}}
 static final ThreadLocal<Boolean> inReceiver=ThreadLocal.withInitial(()->false);
 static final String ACTION_PAIR_REQUEST="pair",ACTION_ALARM_EXECUTE="alarm",ACTION_CONTROL="control";
 static Handler controlHandler;static HandlerThread controlThread;static boolean broadcastReceiverRegistered;static BroadcastReceiver broadcastReceiver;
 static final AtomicInteger controls=new AtomicInteger(),pairs=new AtomicInteger(),alarms=new AtomicInteger();
 static boolean isTrustedBroadcastSender(Context c,BroadcastReceiver r){assert inReceiver.get():"sender UID checked outside receiver callback";return true;}
 static boolean verifyControl(Intent i){return "valid".equals(i.token);}static boolean verifyAlarmNonce(Intent i){return "valid".equals(i.token);}static boolean controlRateLimited(String op){return false;}
 static String dispatchControl(String op,String uid,String group,String data,boolean exec,String model,String action){io("config");controls.incrementAndGet();return null;}
 static void handlePairRequest(Context c,Intent i){pairs.incrementAndGet();}static void executeGroup(String group){alarms.incrementAndGet();}
 @@RECEIVER@@
 @@REGISTER@@
 static class Activity extends Context {boolean isFinishing(){return false;}boolean isDestroyed(){return false;}}
 static class UserIdMap {static volatile String uid="A";static String getCurrentUid(){return uid;}}
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class Handler {static final Queue<Runnable> pending=new ConcurrentLinkedQueue<>();final ExecutorService executor;Handler(){executor=null;}Handler(Object looper){executor=looper instanceof ExecutorService?(ExecutorService)looper:null;}void post(Runnable r){if(executor==null)pending.add(r);else executor.execute(r);}void postDelayed(Runnable r,long delay){pending.add(r);} }
 static class Looper {static Object getMainLooper(){return null;}}
 static final Handler mainHandler=new Handler();
 static class Log {static void err(String tag,String s,Throwable t){if(!s.equals("profile probe err:"))throw new AssertionError(t);}static void d(String tag,String s){}static void record(String s){}static void i(String s){}static void i(String t,String s){}}
 static final java.util.regex.Pattern USER_ID_PATTERN=java.util.regex.Pattern.compile("[0-9]{1,32}");
 static String lastProfileUserId;static long lastProfileTime,profileOpenExpectAt;
 static final List<String> opened=new CopyOnWriteArrayList<>();static volatile String hostUid="A";
 static String getUserId(){io("host");return hostUid;}
 static boolean startProfileInProcess(String uid){return false;}static boolean startProfileEventInProcess(String uid){return false;}
 static boolean startProfileScheme(Context c,String uid){assert isMain.get():"profile opened off UI thread";assert !files.containsKey("pending_profile.consuming"):"UI opened before claim cleanup";opens.incrementAndGet();opened.add(uid);return true;}
 static final Object profileProbeLock=new Object();static Runnable pendingProfileProbe;static boolean profileProbeRunning,pendingProfileExplicit;static long pendingProfileGeneration;static String pendingProfileAccountUid;
 static final AtomicInteger workerThreads=new AtomicInteger();static final ExecutorService profileProbeWorker=Executors.newSingleThreadExecutor(r->{workerThreads.incrementAndGet();Thread t=new Thread(r);t.setDaemon(true);return t;});
 @@OPEN@@
 @@LAUNCH@@
 @@LIFECYCLE@@
 @@PROBE@@
 @@EXTRA@@
 static class Dialog {}
 static final List<WeakReference<Dialog>> dialogs=new CopyOnWriteArrayList<>();
 static void triggerDialogProcessing(){}
 @@DIALOGGET@@
 @@DIALOGADD@@
 static void mainCall(Runnable r)throws Exception {AtomicReference<Throwable> failed=new AtomicReference<>();Thread thread=new Thread(()->{isMain.set(true);try{r.run();}catch(Throwable t){failed.set(t);}});thread.start();thread.join(2000);assert !thread.isAlive():"main call blocked";if(failed.get()!=null)throw new AssertionError(failed.get());}
 static void awaitProbe()throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(System.nanoTime()<until){synchronized(profileProbeLock){if(!profileProbeRunning)return;}Thread.sleep(1);}throw new AssertionError("profile worker stuck");}
 static void flushUi()throws Exception {mainCall(()->{Runnable r;while((r=Handler.pending.poll())!=null)r.run();});}
 static void resetProfile()throws Exception {awaitProbe();Handler.pending.clear();files.clear();blocking=null;UserIdMap.uid="A";hostUid="A";lastProfileUserId=null;lastProfileTime=0;opened.clear();opens.set(0);reads.set(0);deletes.set(0);mainIo.set(false);}
 static void mark(String uid){files.put(PENDING_PROFILE_FILE,uid+" "+System.currentTimeMillis());}
 static void advanceGeneration(){TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);}
 static void profile()throws Exception {
  for(String op:List.of("exists","read","delete")){
   files.put(PENDING_PROFILE_FILE,"2088 "+System.currentTimeMillis());blocking=op;entered=new CountDownLatch(1);release=new CountDownLatch(1);CountDownLatch returned=new CountDownLatch(1);mainIo.set(false);
   Thread ui=new Thread(()->{isMain.set(true);try{checkPendingProfile(new Context());}finally{returned.countDown();}});ui.start();
   assert entered.await(2,TimeUnit.SECONDS):"file boundary not reached";boolean prompt=returned.await(200,TimeUnit.MILLISECONDS);release.countDown();ui.join(2000);
   awaitProbe();flushUi();
   assert prompt&&!mainIo.get():"profile "+op+" blocked main thread";
  }
  resetProfile();mark("2088");blocking="read";entered=new CountDownLatch(1);release=new CountDownLatch(1);
  mainCall(()->checkPendingProfile(new Context()));assert entered.await(2,TimeUnit.SECONDS);mark("2099");release.countDown();awaitProbe();
  assert files.containsKey(PENDING_PROFILE_FILE)&&files.get(PENDING_PROFILE_FILE).startsWith("2099 "):"claim cleanup deleted a concurrent new click";
  blocking=null;flushUi();assert opened.equals(List.of("2088"));mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();assert opened.equals(List.of("2088","2099"));
  resetProfile();mark("2088");mainCall(()->openFriendProfile(new Context(),"2099"));awaitProbe();flushUi();
  assert opened.equals(List.of("2099"))&&files.isEmpty():"explicit target was overwritten by old pending marker";
  mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();assert opened.equals(List.of("2099")):"focus reopened discarded old marker";
  for(boolean broadcastFirst:List.of(true,false)){
   resetProfile();mark("2088");
   if(broadcastFirst)mainCall(()->openFriendProfile(new Context(),"2088"));else mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();
   if(broadcastFirst)mainCall(()->checkPendingProfile(new Context()));else mainCall(()->openFriendProfile(new Context(),"2088"));awaitProbe();flushUi();
   assert opened.equals(List.of("2088"))&&!files.containsKey(PENDING_PROFILE_FILE)&&!files.containsKey("pending_profile.consuming"):"fallback/probe duplicated the same click";
   mark("2099");mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();assert opened.equals(List.of("2088","2099")):"later different click was suppressed";
  }
  resetProfile();mark("2088");blocking="read";entered=new CountDownLatch(1);release=new CountDownLatch(1);mainCall(()->checkPendingProfile(new Context()));assert entered.await(2,TimeUnit.SECONDS);
  mainCall(()->{openFriendProfile(new Context(),"2099");for(int i=0;i<100;i++)checkPendingProfile(new Context());});release.countDown();awaitProbe();blocking=null;flushUi();
  assert workerThreads.get()==1&&reads.get()==1&&opened.equals(List.of("2088","2099")):"focus storm queued unbounded work or replaced explicit target";
  for(String malformed:List.of("2088", "2088 nope", "../bad 12", "2088 1", "2088 "+(System.currentTimeMillis()+60000))){
   resetProfile();files.put(PENDING_PROFILE_FILE,malformed);mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();assert opened.isEmpty()&&files.isEmpty():"invalid/expired marker was launched or retained";
  }
  resetProfile();files.put("pending_profile.consuming","2088 "+System.currentTimeMillis());mainCall(()->checkPendingProfile(new Context()));awaitProbe();flushUi();assert opened.equals(List.of("2088"))&&files.isEmpty():"crash claim was not recovered";
  resetProfile();mark("2088");CountDownLatch queued=new CountDownLatch(1),unblock=new CountDownLatch(1);profileProbeWorker.execute(()->{queued.countDown();try{unblock.await();}catch(InterruptedException e){throw new AssertionError(e);}});queued.await();
  mainCall(()->checkPendingProfile(new Context()));advanceGeneration();unblock.countDown();awaitProbe();flushUi();assert reads.get()==0&&opened.isEmpty()&&files.containsKey(PENDING_PROFILE_FILE):"stale queued probe consumed marker";
  for(String changed:List.of("generation","uid")){
   resetProfile();mark("2088");mainCall(()->checkPendingProfile(new Context()));awaitProbe();
   if(changed.equals("generation"))advanceGeneration();else UserIdMap.uid="B";flushUi();assert opened.isEmpty():"stale UI launch survived "+changed;
  }
  resetProfile();UserIdMap.uid=null;mark("2088");mainCall(()->checkPendingProfile(new Context()));awaitProbe();UserIdMap.uid="A";flushUi();assert opened.equals(List.of("2088")):"cold-start UID publication lost pending click";
  assert !mainIo.get():"cold-start profile queried host on main";
  resetProfile();CountDownLatch executorEntered=new CountDownLatch(1),executorRelease=new CountDownLatch(1),probeEntered=new CountDownLatch(1),probeRelease=new CountDownLatch(1);
  profileProbeWorker.execute(()->{executorEntered.countDown();try{executorRelease.await();}catch(InterruptedException e){throw new AssertionError(e);}});executorEntered.await();
  mainCall(()->checkPendingProfile(new Context()));
  synchronized(profileProbeLock){pendingProfileProbe=()->{probeEntered.countDown();try{probeRelease.await();}catch(InterruptedException e){throw new AssertionError(e);}throw new IllegalStateException("injected probe failure");};}
  executorRelease.countDown();assert probeEntered.await(2,TimeUnit.SECONDS);
  mainCall(()->openFriendProfile(new Context(),"2099"));probeRelease.countDown();awaitProbe();flushUi();assert opened.equals(List.of("2099")):"pending request was lost when active probe threw";
  mainCall(()->openFriendProfile(new Context(),"2100"));awaitProbe();flushUi();assert opened.equals(List.of("2099","2100"))&&workerThreads.get()==1:"probe failure wedged queue or killed worker";
 }
 static void dialog()throws Exception {
  dialogs.clear();Dialog live=new Dialog();WeakReference<Dialog> expired=new WeakReference<>(new Dialog());expired.clear();dialogs.add(expired);
  addDialogIfNotExists(live,"test");assert dialogs.size()==1:"dialog add retained expired weak shell";
  WeakReference<Dialog> expired2=new WeakReference<>(new Dialog());expired2.clear();dialogs.add(expired2);
  List<WeakReference<Dialog>> snapshot=getDialogs();assert snapshot.size()==1&&dialogs.size()==1&&snapshot.get(0).get()==live:"dialog snapshot retained expired weak shell";
  snapshot.clear();assert getDialogs().size()==1:"dialog snapshot exposed live backing list";
  CountDownLatch ready=new CountDownLatch(12),go=new CountDownLatch(1);List<Thread> threads=new ArrayList<>();Dialog same=new Dialog();
  for(int i=0;i<12;i++){Thread thread=new Thread(()->{ready.countDown();try{go.await();}catch(InterruptedException e){throw new AssertionError(e);}addDialogIfNotExists(same,"parallel");});threads.add(thread);thread.start();}
  ready.await();go.countDown();for(Thread t:threads)t.join();assert getDialogs().size()==2:"concurrent dialog constructors duplicated live window";
 }
 static void broadcast()throws Exception {
  for(int api:List.of(26,33)){
   Build.VERSION.SDK_INT=api;HostUiIoCheck hook=new HostUiIoCheck();Context context=new Context();hook.registerBroadcastReceiver(context);
   blocking="config";entered=new CountDownLatch(1);release=new CountDownLatch(1);mainIo.set(false);CountDownLatch returned=new CountDownLatch(1),done=new CountDownLatch(1);
   Thread ui=new Thread(()->{isMain.set(true);try{context.deliver(new Intent(ACTION_CONTROL),done);}finally{returned.countDown();}});ui.start();
   assert entered.await(2,TimeUnit.SECONDS);boolean prompt=returned.await(200,TimeUnit.MILLISECONDS);release.countDown();ui.join(2000);assert done.await(2,TimeUnit.SECONDS);
   assert prompt&&!mainIo.get():"broadcast config read blocked main";
   blocking=null;int before=controls.get();Intent rejected=new Intent(ACTION_CONTROL);rejected.token="wrong";CountDownLatch denied=new CountDownLatch(1);context.deliver(rejected,denied);assert denied.await(2,TimeUnit.SECONDS)&&controls.get()==before;
   CountDownLatch paired=new CountDownLatch(1);context.deliver(new Intent(ACTION_PAIR_REQUEST),paired);assert paired.await(2,TimeUnit.SECONDS);
   CountDownLatch alarm=new CountDownLatch(1);context.deliver(new Intent(ACTION_ALARM_EXECUTE),alarm);assert alarm.await(2,TimeUnit.SECONDS);
  }
  assert pairs.get()==2&&alarms.get()==2;
 }
 public static void main(String[] args)throws Exception {if(args.length==0||args[0].equals("broadcast"))broadcast();if(args.length==0||args[0].equals("profile"))profile();if(args.length==0||args[0].equals("dialogs"))dialog();System.out.println("PASS host UI I/O and dialog pruning");}
}
'''
source = (SOURCE / hook).read_text(encoding="utf-8")
extra = "".join(method(hook, signature) for signature in ("private static void requestFriendProfile(", "private static void startProfileProbeWorker(", "private static void drainProfileProbes(", "private static void consumePendingProfile("))
code = code.replace("@@EXTRA@@", extra)
for key, path, signature in (
    ("@@OPEN@@", hook, "private static void openFriendProfile("),
    ("@@LAUNCH@@", hook, "private static void launchFriendProfile("),
    ("@@REGISTER@@", hook, "private void registerBroadcastReceiver("),
    ("@@RECEIVER@@", hook, "private class AlipayBroadcastReceiver"),
    ("@@PROBE@@", hook, "private static void checkPendingProfile("),
    ("@@LIFECYCLE@@", "data/task/TaskLifecycle.java", "public final class TaskLifecycle"),
    ("@@DIALOGGET@@", "hook/SimplePageManager.java", "public static List<WeakReference<Dialog>> getDialogs("),
    ("@@DIALOGADD@@", "hook/SimplePageManager.java", "private static void addDialogIfNotExists("),
):
    code = code.replace(key, method(path, signature).replace("public final class TaskLifecycle", "static final class TaskLifecycle"))
code = code.replace("ApplicationHook::drainProfileProbes", "HostUiIoCheck::drainProfileProbes").replace("java.io.File", "ProbeFile").replace("android.os.Handler", "Handler").replace("android.os.Looper", "Looper").replace("android.os.HandlerThread", "HandlerThread")
with tempfile.TemporaryDirectory(prefix="sesame-host-io-") as temporary:
    java = Path(temporary) / "HostUiIoCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", temporary, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary, "HostUiIoCheck", *sys.argv[1:]], check=True)
