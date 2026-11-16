"""Production replays for agreement, host control/lifecycle, file locks and config preservation."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, ROOT, SOURCE

hook = "hook/ApplicationHook.java"
json_source = (SOURCE / "util/JsonUtil.java").read_text(encoding="utf-8")
rewrite_methods = json_source[json_source.index("    public static boolean isRewriteLossless("):json_source.index("    public static Object toNode(")]
bridge = method("ui/HostBridge.java", "public final class HostBridge").replace("public final class", "static final class", 1)
bridge = bridge.replace("io.github.aw1y2z.sesame.data.Agreement", "Agreement").replace("io.github.aw1y2z.sesame.util.FileUtil", "FileUtil").replace("android.util.Log", "Log")

code = r'''
import java.io.*;import java.nio.file.*;import java.nio.channels.*;import java.util.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.*;
import java.text.SimpleDateFormat;import java.security.SecureRandom;
import com.fasterxml.jackson.core.type.TypeReference;import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.annotation.JsonInclude;
public class InfraCheck {
 static File root;static int fileReadFailures;
 static class Log {
  static List<String> lines=new ArrayList<>();static void i(String s){lines.add(s);}static void i(String t,String s){lines.add(s);}
  static void record(String s){lines.add(s);}static void debug(String s){lines.add(s);}static void error(String s){lines.add(s);}
  static void w(String t,String s){lines.add(s);}static void w(String t,String s,Throwable e){lines.add(s);}
  static void err(String t,String s,Throwable e){lines.add(s);}static void printStackTrace(Throwable e){}static void printStackTrace(String t,Throwable e){}
 }
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}}
 static class AccountFolderName {@@UID@@}
 static class JsonUtil {
  static final ObjectMapper MAPPER=new ObjectMapper();
  @@JSON_INIT@@
  static ObjectMapper copyMapper(){return new ObjectMapper(){@Override public <T>T readValue(File f,TypeReference<T> t)throws IOException{if(fileReadFailures>0){fileReadFailures--;throw new IOException("transient read failure");}return super.readValue(f,t);}};}
  static <T>T parseObject(String s,TypeReference<T> t){try{return MAPPER.readValue(s,t);}catch(Exception e){throw new IllegalArgumentException(e);}}
  static String toJsonString(Object o){try{return MAPPER.writeValueAsString(o);}catch(Exception e){throw new IllegalArgumentException(e);}}
  @@REWRITE@@
 }
 static class FileUtil {
  static final String TAG="file",HOST_LOCK_FILE_NAME="host.lock",BACKUP_FILE_PREFIX="config_v2_";
  static final File MAIN_DIRECTORY_FILE=new File(root,"shared"),CONFIG_DIRECTORY_FILE=new File(MAIN_DIRECTORY_FILE,"config");
  static RandomAccessFile hostLockRaf;static FileLock hostLock;static boolean failWrites,verifyDeleteLease;static int writes;
  static void initPrivateDirectory(Context c){}
  static File getHttpTokenFile(){return new File(root,"private/http_token.txt");}
  static File getLegacyHttpTokenFile(){return new File(MAIN_DIRECTORY_FILE,"http_token.txt");}
  static String readFromFile(File f){if(fileReadFailures>0){fileReadFailures--;throw new IllegalStateException("transient read failure");}try{return f.exists()?Files.readString(f.toPath()):"";}catch(Exception e){throw new IllegalArgumentException(e);}}
  static boolean write2File(String s,File f){writes++;if(failWrites)return false;try{f.getParentFile().mkdirs();Files.writeString(f.toPath(),s);return true;}catch(Exception e){return false;}}
  static File getBackupDirectoryFile(){
   if(verifyDeleteLease)try(RandomAccessFile access=new RandomAccessFile(getHostLockFile(),"rw")){
    try(FileLock wrong=access.getChannel().tryLock()){throw new AssertionError("deletion released host lock before backup cleanup");}
   }catch(OverlappingFileLockException expected){}catch(IOException e){throw new AssertionError(e);}
   File f=new File(MAIN_DIRECTORY_FILE,"bak");f.mkdirs();return f;
  }
  @@SAFEUID@@
  @@DELETE@@
  @@RECURSE@@
  @@LOCKFILE@@
  @@ISRUNNING@@
  @@ACQUIRE@@
 }
 @@STORE@@
 static class AppConfig {
  static final String TAG="app";static final AppConfig INSTANCE=new AppConfig();
  static final File APP_CONFIG_DIRECTORY_FILE=new File(root,"shared");
  static boolean loadFailed;static int loads,diskVersion;
  boolean init=true,debug;Integer agreementVersion=0;Long agreementAcceptedAt=0L;
  boolean isInit(){return init;}Integer getAgreementVersion(){return agreementVersion;}Long getAgreementAcceptedAt(){return agreementAcceptedAt;}
  void setAgreementVersion(Integer n){agreementVersion=n;}void setAgreementAcceptedAt(Long n){agreementAcceptedAt=n;}Boolean getEnableDebugLog(){return debug;}
  static AppConfig load(){loads++;INSTANCE.init=true;INSTANCE.agreementVersion=diskVersion;return INSTANCE;}
  static boolean isLoadFailed(){return loadFailed;}
  static String toSaveStr(){return JsonUtil.toJsonString(Map.of("agreementVersion",INSTANCE.agreementVersion,"agreementAcceptedAt",INSTANCE.agreementAcceptedAt));}
  @@APPSAVE@@
  @@FORCESAVE@@
 }
 @@AGREEMENT@@
 static class ForceSaveReplay {
  static final String TAG="force";static final File APP_CONFIG_DIRECTORY_FILE=new File("root");static boolean loadFailed=true;
  static String toSaveStr(){return "replacement";}
  static class File {
   static Map<String,String> entries=new HashMap<>();static boolean failWrite,failBackup,failReplace,failRestore;
   final String path;File(String p){path=p;}File(File parent,String name){path=parent.path+"/"+name;}
   boolean exists(){return entries.containsKey(path);}String getName(){return path.substring(path.lastIndexOf('/')+1);}
   boolean delete(){return entries.remove(path)!=null;}
   boolean renameTo(File target){
    if(path.endsWith("appConfig.json")&&failBackup||path.endsWith(".tmp")&&failReplace||path.contains("corrupt-")&&failRestore)return false;
    if(!entries.containsKey(path))return false;entries.put(target.path,entries.remove(path));return true;
   }
  }
  static class FileUtil {static boolean write2File(String s,File target){if(File.failWrite)return false;File.entries.put(target.path,s);return true;}}
  static void reset(){File.entries.clear();File.entries.put("root/appConfig.json","original corrupt bytes");File.failWrite=File.failBackup=File.failReplace=File.failRestore=false;loadFailed=true;}
  @@FORCESAVEREPLAY@@
 }
 @@LIFECYCLE@@
 static class Intent {
  String action,pkg;Map<String,Object> extras=new HashMap<>();Intent(String a){action=a;}
  Intent setPackage(String p){pkg=p;return this;}String getPackage(){return pkg;}
  Intent putExtra(String k,String v){extras.put(k,v);return this;}Intent putExtra(String k,boolean v){extras.put(k,v);return this;}
  String getStringExtra(String k){Object v=extras.get(k);return v instanceof String?(String)v:null;}
 }
 static class BroadcastReceiver {public void onReceive(Context c,Intent i){}}
 static class IntentFilter {IntentFilter(String action){}}
 static class SharedPreferences {
  Map<String,String> values=new HashMap<>();String getString(String k,String fallback){return values.getOrDefault(k,fallback);}
  Editor edit(){return new Editor();}class Editor {Editor putString(String k,String v){values.put(k,v);return this;}Editor remove(String k){values.remove(k);return this;}void apply(){}}
 }
 static class Context {
  static final int MODE_PRIVATE=0,RECEIVER_EXPORTED=2;
  SharedPreferences preferences=new SharedPreferences();List<Intent> sent=new ArrayList<>();Set<BroadcastReceiver> receivers=new HashSet<>();
  Context getApplicationContext(){return this;}SharedPreferences getSharedPreferences(String n,int m){return preferences;}
  void registerReceiver(BroadcastReceiver r,IntentFilter f){receivers.add(r);}void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){receivers.add(r);}
  void unregisterReceiver(BroadcastReceiver r){receivers.remove(r);}void sendBroadcast(Intent i){sent.add(i);}
 }
 static class Build {static class VERSION {static int SDK_INT=33;}static class VERSION_CODES {static final int TIRAMISU=33;}}
 static class Looper {static Object getMainLooper(){return null;}}
 static class Handler {static List<Runnable> pending=new ArrayList<>();Handler(Object l){}void postDelayed(Runnable r,long n){pending.add(r);}}
 @@BRIDGE@@
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class AccountSwitchController {static boolean isBusy(){return false;}}
 static class ConfigV2 {static int loads;static void load(String u){assert !TaskLifecycle.isIdle();assert u.equals(UserIdMap.uid);loads++;}}
 static class ModelTask {
  static int stops,named,groups,all;static String model,action;
  static void stopAllTask(){stops++;}static boolean startNamedTask(String m,String u,String a){assert u.equals(UserIdMap.uid);named++;model=m;action=a;return true;}
  static int startGroupTask(String group){groups++;return 1;}static void startAllTask(boolean b){all++;}
 }
 enum ModelGroup {BASE,OTHER;static ModelGroup getByCode(String s){return "BASE".equals(s)?BASE:OTHER;}static String getName(String s){return s;}}
 static class DailyTask {static <T>T manual(Supplier<T> s){return s.get();}}
 static class TaskCancelledException extends RuntimeException {}
 static class NanoHTTPD {
  interface IHTTPSession {Map<String,String> getParms();}
  static class Response {final int code;Response(int code){this.code=code;}}
 }
 static class BaseHandler {
  BaseHandler(String token){}
  protected NanoHTTPD.Response onGet(NanoHTTPD.IHTTPSession session){return methodNotAllowed();}
  protected NanoHTTPD.Response onPost(NanoHTTPD.IHTTPSession session,String body){return methodNotAllowed();}
  NanoHTTPD.Response methodNotAllowed(){return new NanoHTTPD.Response(405);}
  NanoHTTPD.Response badRequest(String message){return new NanoHTTPD.Response(400);}
  NanoHTTPD.Response ok(Map<String,String> body){return new NanoHTTPD.Response(200);}
 }
 @@CONTROLHANDLER@@
 static class ExtensionsHandle {static int calls;static boolean cancel;static void handleRequest(String type,String fun,String data){assert !TaskLifecycle.isIdle();calls++;if(cancel)throw new TaskCancelledException();}}
 static class TestRpc {
  static final String TAG="rpc";
  static class Thread {static Queue<Runnable> jobs=new ArrayDeque<>();public void run(){}void start(){jobs.add(this::run);}}
  @@EXTENSIONSTART@@
 }
 static class ApplicationHook {
  static final String TAG="hook",HOST_PACKAGE_NAME="com.eg.android.AlipayGphone",ACTION_ALARM_EXECUTE="alarm";
  static final Set<String> CONTROL_OPS=Set.of("restart","reloadConfig","execute","status","openProfile","forestQuery","walkPath");
  static final Set<String> FOREST_QUERY_METHODS=Set.of("getTreeItems"),WALK_PATH_METHODS=Set.of("addCustomWalkPathId","addCustomWalkPathIdQueue","clearCustomWalkPathIdQueue");
  static final long CONTROL_MIN_INTERVAL_STATUS=2000,CONTROL_MIN_INTERVAL_RESTART=10000,CONTROL_MIN_INTERVAL_EXECUTE=60000,CONTROL_MIN_INTERVAL_OPEN_PROFILE=1000;
  static final Map<String,AtomicLong> lastControlAt=new ConcurrentHashMap<>();static final java.util.regex.Pattern HTTP_TOKEN_PATTERN=java.util.regex.Pattern.compile("[0-9a-f]{64}");
  static String httpToken;static Context context=new Context();static ApplicationHook self=new ApplicationHook();
  static int initCalls,statusCalls,profileOpens;static boolean lastExec,initAdmitted;
  static class Thread {static Queue<Runnable> jobs=new ArrayDeque<>();Runnable job;Thread(Runnable r,String name){job=r;}void start(){jobs.add(job);}}
  Boolean initHandler(Boolean force){return initHandler(force,null,true);}
  Boolean initHandler(Boolean force,TaskLifecycle.Freeze owner,boolean exec){initCalls++;lastExec=exec;initAdmitted=!TaskLifecycle.isIdle();assert TaskLifecycle.freezeIfIdle()==null:"async initialization not admitted atomically";return true;}
  static void reloadAppConfig(){AppConfig.load();}void replyStatus(){statusCalls++;}static void openFriendProfile(Context c,String user){profileOpens++;}static void reLogin(){}
  @@TOKEN@@
  @@TOKENSHAPE@@
  @@RANDOMTOKEN@@
  @@MIRROR@@
  @@REMOVEMIRROR@@
  @@ALARM@@
  @@NONCE@@
  @@VERIFYALARM@@
  @@VERIFYCONTROL@@
  @@INTERVAL@@
  @@RATE@@
  @@DISPATCHWRAPPER@@
  @@DISPATCH@@
  @@RESTARTWRAPPER@@
  @@RESTART@@
  @@EXECGROUP@@
 }
 static void drain(){while(!ApplicationHook.Thread.jobs.isEmpty())ApplicationHook.Thread.jobs.remove().run();assert TaskLifecycle.isIdle();}
 static void resetHost(){ApplicationHook.initCalls=0;ApplicationHook.initAdmitted=false;ApplicationHook.Thread.jobs.clear();ConfigV2.loads=0;ModelTask.stops=ModelTask.named=ModelTask.groups=ModelTask.all=0;UserIdMap.uid="A";AppConfig.INSTANCE.agreementVersion=Agreement.VERSION;AppConfig.diskVersion=Agreement.VERSION;}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  assert JsonUtil.toJsonString(new Date(0)).equals("\"1970-01-01 08:00:00\"");
  assert JsonUtil.MAPPER.readValue("\"1970-01-01 08:00:00\"",Date.class).getTime()==0;
  root=new File(args[0]);FileUtil.MAIN_DIRECTORY_FILE.mkdirs();
  Set<String> known=Set.of("modelFieldsMap","modelFieldsMap.Current","modelFieldsMap.Current.keep");
  String src="{\"modelFieldsMap\":{\"Current\":{\"keep\":{\"value\":{\"token\":\"private-token-value\",\"n\":2}},\"retired\":{\"value\":1}},\"Retired\":{}},\"obsoleteRoot\":1}";
  String good="{\"modelFieldsMap\":{\"Current\":{\"keep\":{\"value\":{\"token\":\"private-token-value\",\"n\":2}}}},\"obsoleteRoot\":1}";
  assert JsonUtil.isRewriteLossless(src,good,known);assert !JsonUtil.isRewriteLossless(src,good):"strict mode cannot remove unknown fields";
  for(String key:List.of("obsoleteRoot","futureSetting","modelFieldsMap.futureSetting")){
   String original="{\""+key+"\":{\"value\":2},\"modelFieldsMap\":{}}",removed="{\"modelFieldsMap\":{}}";
   assert !JsonUtil.isRewriteLossless(original,removed,known):"unregistered top-level key silently removed";
   assert JsonUtil.describeRewriteDiff(original,removed,known).equals(key+" : 回写后键消失");
  }
  for(String bad:List.of("{\"modelFieldsMap\":{}}","{\"modelFieldsMap\":{\"Current\":{}}}","{\"modelFieldsMap\":{\"Current\":{\"keep\":{}}}}",good.replace(",\"n\":2",""),good.replace("private-token-value","different-secret"))){
   assert !JsonUtil.isRewriteLossless(src,bad,known):bad;String diff=JsonUtil.describeRewriteDiff(src,bad,known);assert !diff.isEmpty()&&!diff.contains("private-token-value")&&!diff.contains("different-secret"):diff;
  }
  assert !JsonUtil.isRewriteLossless("bad",good,known);
  File map=new File(root,"map.json");FileUtil.write2File("{\"old\":\"old\",\"override\":\"old\",\"deleted\":\"old\"}",map);
  StringMapStore store=new StringMapStore(uid->map);store.add("new","new");store.add("override","new");store.remove("deleted");assert store.save("A");
  Map<String,String> result=JsonUtil.parseObject(FileUtil.readFromFile(map),new TypeReference<Map<String,String>>(){});assert result.equals(Map.of("old","old","override","new","new","new"));
  FileUtil.write2File("corrupt raw",map);store=new StringMapStore(uid->map);store.add("new","new");int before=FileUtil.writes;assert !store.save("A")&&FileUtil.writes==before&&FileUtil.readFromFile(map).equals("corrupt raw");
  assert !store.save("A")&&FileUtil.writes==before:"persistent corruption was overwritten";
  FileUtil.write2File("{\"repaired\":\"yes\"}",map);assert store.save("A"):"repaired file never retried after merge failure";
  assert store.getMap().equals(Map.of("repaired","yes","new","new"));
  FileUtil.write2File("{\"old\":\"old\",\"override\":\"old\",\"deleted\":\"old\"}",map);
  store=new StringMapStore(uid->map);store.add("new","new");store.add("override","new");store.remove("deleted");before=FileUtil.writes;fileReadFailures=1;
  assert !store.save("A")&&fileReadFailures==0&&FileUtil.writes==before:"failed read wrote partial memory";
  assert store.save("A"):"transient merge failure permanently disabled saves";
  assert store.getMap().equals(Map.of("old","old","override","new","new","new")):"retry lost additions, overrides or removals";
  FileUtil.write2File("corrupt raw",map);store.load("A");store.add("pending","yes");before=FileUtil.writes;
  assert !store.save("A")&&FileUtil.writes==before&&FileUtil.readFromFile(map).equals("corrupt raw");
  FileUtil.write2File("{\"repaired\":\"yes\"}",map);assert store.save("A"):"load failure never retried after repair";
  assert store.getMap().equals(Map.of("repaired","yes","pending","yes"));
  fileReadFailures=1;store.load("A");store.add("afterReadFailure","yes");assert fileReadFailures==0&&store.save("A"):"transient load failure permanently disabled saves";
  assert store.getMap().equals(Map.of("repaired","yes","pending","yes","afterReadFailure","yes"));
  File emptyMap=new File(root,"empty-map.json");Files.writeString(emptyMap.toPath(),"");StringMapStore emptyStore=new StringMapStore(uid->emptyMap);emptyStore.add("new","new");before=FileUtil.writes;
  assert !emptyStore.save("A")&&FileUtil.writes==before&&Files.readString(emptyMap.toPath()).isEmpty():"empty damaged file was overwritten";
  File missingMap=new File(root,"missing-map.json");StringMapStore missingStore=new StringMapStore(uid->missingMap);missingStore.load("A");missingStore.add("new","new");assert missingStore.save("A")&&missingMap.exists();
  FileUtil.write2File("{\"repaired\":\"yes\"}",map);
  store.clear();store.add("later","yes");assert store.save("A");result=JsonUtil.parseObject(FileUtil.readFromFile(map),new TypeReference<Map<String,String>>(){});
  assert result.equals(Map.of("repaired","yes","later","yes")):"lifecycle cache clear erased disk candidates";
  store.remove("repaired");assert store.save("A")&&store.get("repaired")==null;
  AppConfig.INSTANCE.agreementVersion=0;AppConfig.INSTANCE.agreementAcceptedAt=123L;FileUtil.failWrites=true;assert !Agreement.accept()&&AppConfig.INSTANCE.agreementVersion==0&&AppConfig.INSTANCE.agreementAcceptedAt==123L;
  AppConfig.loadFailed=true;assert !Agreement.accept()&&AppConfig.INSTANCE.agreementVersion==0;FileUtil.failWrites=false;assert Agreement.accept()&&Agreement.isAccepted()&&!AppConfig.loadFailed;
  for(String issue:List.of("write","backup","replace")){
   ForceSaveReplay.reset();switch(issue){case "write":ForceSaveReplay.File.failWrite=true;break;case "backup":ForceSaveReplay.File.failBackup=true;break;case "replace":ForceSaveReplay.File.failReplace=true;}
   assert !ForceSaveReplay.forceSave()&&ForceSaveReplay.File.entries.get("root/appConfig.json").equals("original corrupt bytes")&&ForceSaveReplay.loadFailed:issue;
  }
  ForceSaveReplay.reset();ForceSaveReplay.File.failReplace=ForceSaveReplay.File.failRestore=true;assert !ForceSaveReplay.forceSave()&&ForceSaveReplay.File.entries.values().contains("original corrupt bytes");
  ForceSaveReplay.reset();assert ForceSaveReplay.forceSave()&&!ForceSaveReplay.loadFailed&&ForceSaveReplay.File.entries.get("root/appConfig.json").equals("replacement")&&ForceSaveReplay.File.entries.values().contains("original corrupt bytes");
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));AppConfig.INSTANCE.agreementAcceptedAt=java.time.Instant.parse("2026-11-15T16:05:00Z").toEpochMilli();assert Agreement.acceptedAtText().equals("2026-11-16 00:05");
  Context app=new Context();AppConfig.INSTANCE.agreementVersion=0;HostBridge.requestPair(app);assert app.sent.isEmpty();AppConfig.INSTANCE.agreementVersion=Agreement.VERSION;
  HostBridge.requestPair(app);assert app.sent.size()==1&&app.sent.get(0).getPackage().equals("com.eg.android.AlipayGphone")&&app.receivers.size()==1;
  String challenge=app.sent.get(0).getStringExtra("challenge"),token="a".repeat(64);BroadcastReceiver receiver=app.receivers.iterator().next();
  receiver.onReceive(app,new Intent("reply").putExtra("challenge","wrong").putExtra("token",token));assert HostBridge.getToken(app)==null&&app.receivers.contains(receiver):"invalid reply canceled valid pairing";
  receiver.onReceive(app,new Intent("reply").putExtra("challenge",challenge).putExtra("token","short"));assert app.receivers.contains(receiver);
  receiver.onReceive(app,new Intent("reply").putExtra("challenge",challenge).putExtra("token",token));assert HostBridge.getToken(app).equals(token)&&app.receivers.isEmpty();
  HostBridge.send(app,"execute","A","FOREST",true);HostBridge.executeModule(app,"A","AntFarm","feed");assert app.sent.stream().allMatch(i->i.getPackage().equals("com.eg.android.AlipayGphone"));assert app.sent.get(2).getStringExtra("taskAction").equals("feed");
  HostBridge.clearToken(app);assert HostBridge.getToken(app)==null;
  resetHost();ApplicationHook.httpToken=null;FileUtil.write2File(token,FileUtil.getLegacyHttpTokenFile());AppConfig.INSTANCE.debug=false;
  assert ApplicationHook.resolveHttpToken().equals(token)&&FileUtil.readFromFile(FileUtil.getHttpTokenFile()).equals(token)&&!FileUtil.getLegacyHttpTokenFile().exists();
  AppConfig.INSTANCE.debug=true;ApplicationHook.syncLegacyHttpToken(token);assert FileUtil.readFromFile(FileUtil.getLegacyHttpTokenFile()).equals(token);AppConfig.INSTANCE.debug=false;ApplicationHook.syncLegacyHttpToken(token);assert !FileUtil.getLegacyHttpTokenFile().exists();
  assert Log.lines.stream().noneMatch(s->s.contains(token)):"token leaked to logs";
  ApplicationHook.httpToken=null;FileUtil.write2File("invalid",FileUtil.getHttpTokenFile());FileUtil.failWrites=true;assert ApplicationHook.resolveHttpToken().isEmpty()&&ApplicationHook.httpToken==null;FileUtil.failWrites=false;
  String generated=ApplicationHook.resolveHttpToken();assert generated.matches("[0-9a-f]{64}")&&!generated.equals(token);
  Intent alarm=ApplicationHook.alarmIntent();assert alarm.getPackage().equals(ApplicationHook.HOST_PACKAGE_NAME)&&ApplicationHook.verifyAlarmNonce(alarm)&&!alarm.getStringExtra("nonce").contains(generated);
  assert !ApplicationHook.verifyAlarmNonce(new Intent("alarm").putExtra("nonce",generated));assert !ApplicationHook.verifyControl(new Intent("control").putExtra("token",generated));
  assert ApplicationHook.verifyControl(new Intent("control").setPackage(ApplicationHook.HOST_PACKAGE_NAME).putExtra("token",generated));
  ApplicationHook.lastControlAt.clear();assert !ApplicationHook.controlRateLimited("status")&&ApplicationHook.controlRateLimited("status")&&!ApplicationHook.controlRateLimited("execute");
  resetHost();AppConfig.INSTANCE.agreementVersion=0;assert ApplicationHook.dispatchControl("status",null,null,null,false)==null&&AppConfig.INSTANCE.agreementVersion==Agreement.VERSION:"accepted App state never reached running host";
  assert ApplicationHook.dispatchControl("rpctest",null,null,null,false).equals("unknown op");assert ApplicationHook.dispatchControl("forestQuery",null,"unknown",null,false).equals("unknown method");
  resetHost();ApplicationHook.dispatchControl("restart","A",null,null,false);drain();assert ApplicationHook.initCalls==1&&ApplicationHook.initAdmitted&&!ApplicationHook.lastExec;
  resetHost();ApplicationHook.dispatchControl("restart","A",null,null,true);TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();TaskLifecycle.thaw(freeze);UserIdMap.uid="B";drain();assert ApplicationHook.initCalls==0:"stale restart ran new account";
  resetHost();ApplicationHook.dispatchControl("execute","A",null,null,false);UserIdMap.uid="B";drain();assert ApplicationHook.initCalls==0:"no-group execute ignored requested account";
  resetHost();ApplicationHook.dispatchControl("execute","A",null,null,false);drain();assert ApplicationHook.initCalls==1&&ApplicationHook.initAdmitted;
  resetHost();ApplicationHook.dispatchControl("execute","A",null,null,false,"AntFarm","feed");drain();assert ModelTask.named==1&&ModelTask.model.equals("AntFarm")&&ModelTask.action.equals("feed");
  resetHost();ApplicationHook.dispatchControl("execute",null,null,null,false,"AntFarm","feed");drain();assert ModelTask.named==0&&ModelTask.stops==0;
  TestRpc.start("walk","path","type");freeze=TaskLifecycle.freezeIfIdle();TaskLifecycle.thaw(freeze);TestRpc.Thread.jobs.remove().run();assert ExtensionsHandle.calls==0:"queued extension ran after account generation changed";
  TestRpc.start("walk","path","type");TestRpc.Thread.jobs.remove().run();assert ExtensionsHandle.calls==1&&TaskLifecycle.isIdle();
  ExtensionsHandle.cancel=true;TestRpc.start("walk","path","type");TestRpc.Thread.jobs.remove().run();assert ExtensionsHandle.calls==2&&TaskLifecycle.isIdle();
  File account=new File(FileUtil.CONFIG_DIRECTORY_FILE,"A/sub/file");FileUtil.write2File("owned",account);File other=new File(FileUtil.CONFIG_DIRECTORY_FILE,"B/keep");FileUtil.write2File("untouched",other);
  File backup=new File(FileUtil.getBackupDirectoryFile(),"config_v2_A_A.json"),otherBackup=new File(FileUtil.getBackupDirectoryFile(),"config_v2_B_A.json");FileUtil.write2File("owned",backup);FileUtil.write2File("untouched",otherBackup);FileUtil.write2File("legacy",new File(FileUtil.CONFIG_DIRECTORY_FILE,"config_v2-A.json"));
  assert !FileUtil.deleteAccount("../B")&&!FileUtil.deleteAccount("")&&!FileUtil.deleteAccount(null)&&account.exists();
  assert !FileUtil.isHostRunning();FileUtil.verifyDeleteLease=true;assert FileUtil.deleteAccount("A");FileUtil.verifyDeleteLease=false;assert !account.exists()&&!backup.exists()&&other.exists()&&otherBackup.exists()&&!FileUtil.isHostRunning();
  FileUtil.write2File("keep",account);try(RandomAccessFile access=new RandomAccessFile(FileUtil.getHostLockFile(),"rw");FileLock lease=access.getChannel().lock()){
   assert FileUtil.isHostRunning()&&!FileUtil.acquireHostLock()&&!FileUtil.deleteAccount("A")&&account.exists();
  }
  assert FileUtil.acquireHostLock()&&FileUtil.acquireHostLock()&&FileUtil.isHostRunning()&&!FileUtil.deleteAccount("A");FileUtil.hostLock.release();FileUtil.hostLockRaf.close();FileUtil.hostLock=null;FileUtil.hostLockRaf=null;
  resetHost();NanoHTTPD.IHTTPSession session=()->Map.of("userId","2088");
  int opens=ApplicationHook.profileOpens,statuses=ApplicationHook.statusCalls;
  ControlHandler profile=new ControlHandler("token","openProfile");
  assert profile.onGet(session).code==405&&ApplicationHook.profileOpens==opens:"GET opened friend profile";
  assert profile.onPost(session,"").code==200&&ApplicationHook.profileOpens==opens+1:"POST profile action unavailable";
  assert new ControlHandler("token","status").onGet(session).code==200&&ApplicationHook.statusCalls==statuses+1;
  for(String op:List.of("reLogin","restart","reloadConfig","execute","forestQuery","walkPath"))assert new ControlHandler("token",op).onGet(session).code==405:op;
  System.out.println("PASS Nov16 infra: known-path preservation, secret-free diagnostics, map read retries/corrupt file protection, agreement rollback/GMT8, directed pairing/token/alarm, generation/account controls, deletion lease and host lock");
 }
}
'''

replace = {
    "@@UID@@": method("util/AccountFolderName.java", "public static boolean isValidUid("),
    "@@REWRITE@@": rewrite_methods,
    "@@JSON_INIT@@": method("util/JsonUtil.java", "    static {"),
    "@@SAFEUID@@": method("util/FileUtil.java", "private static boolean isSafeUserId("),
    "@@DELETE@@": method("util/FileUtil.java", "public static boolean deleteAccount("),
    "@@RECURSE@@": method("util/FileUtil.java", "private static boolean deleteRecursively("),
    "@@LOCKFILE@@": method("util/FileUtil.java", "public static File getHostLockFile("),
    "@@ISRUNNING@@": method("util/FileUtil.java", "public static boolean isHostRunning("),
    "@@ACQUIRE@@": method("util/FileUtil.java", "public static synchronized boolean acquireHostLock("),
    "@@STORE@@": method("util/idMap/StringMapStore.java", "class StringMapStore").replace("class StringMapStore", "static class StringMapStore", 1),
    "@@APPSAVE@@": method("data/AppConfig.java", "public static Boolean save("),
    "@@FORCESAVE@@": method("data/AppConfig.java", "public static synchronized Boolean forceSave("),
    "@@AGREEMENT@@": method("data/Agreement.java", "public final class Agreement").replace("public final class", "static final class", 1),
    "@@FORCESAVEREPLAY@@": method("data/AppConfig.java", "public static synchronized Boolean forceSave("),
    "@@EXTENSIONSTART@@": method("model/extensions/TestRpc.java", "public static void start(").replace("io.github.aw1y2z.sesame.util.Log", "Log"),
    "@@LIFECYCLE@@": method("data/task/TaskLifecycle.java", "public final class TaskLifecycle").replace("public final class", "static final class", 1),
    "@@BRIDGE@@": bridge,
    "@@CONTROLHANDLER@@": method("hook/ControlHandler.java", "public class ControlHandler").replace("public class ControlHandler", "static class ControlHandler", 1),
}
signatures = {
    "TOKEN": "private static synchronized String resolveHttpToken(",
    "TOKENSHAPE": "private static boolean isHttpToken(",
    "RANDOMTOKEN": "private static String randomHttpToken(",
    "MIRROR": "private static void syncLegacyHttpToken(",
    "REMOVEMIRROR": "private static void removeMirrorTokenFile(",
    "ALARM": "private static Intent alarmIntent(",
    "NONCE": "private static String alarmNonce(",
    "VERIFYALARM": "private static boolean verifyAlarmNonce(",
    "VERIFYCONTROL": "private static boolean verifyControl(",
    "INTERVAL": "private static long controlMinInterval(",
    "RATE": "private static boolean controlRateLimited(",
    "DISPATCHWRAPPER": "public static String dispatchControl(String op, String userId, String group, String data, boolean exec)",
    "DISPATCH": "public static String dispatchControl(String op, String userId, String group, String data, boolean exec,",
    "RESTARTWRAPPER": "private void restartSelf(boolean exec)",
    "RESTART": "private void restartSelf(boolean exec, String requestedUid)",
    "EXECGROUP": "private void executeGroup(String groupCode, String userId, String modelCode, String manualAction)",
}
for key, signature in signatures.items():
    replace[f"@@{key}@@"] = method(hook, signature).replace("io.github.aw1y2z.sesame.util.DailyTask", "DailyTask")
for key, value in replace.items():
    code = code.replace(key, value)
assert "@@" not in code
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
dependencies = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
jars = []
for name in ("jackson-databind", "jackson-core", "jackson-annotations"):
    version = re.search(r"name: '" + name + r"', version: '([^']+)'", dependencies).group(1)
    jars.append(str(next((cache / "com.fasterxml.jackson.core" / name / version).glob(f"*/{name}-{version}.jar"))))
classpath = os.pathsep.join(jars)
with tempfile.TemporaryDirectory(prefix="sesame-nov16-infra-") as temporary:
    java = Path(temporary) / "InfraCheck.java"
    java.write_text(code, encoding="utf-8")
    data = Path(temporary) / "data"
    data.mkdir()
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", temporary, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", temporary + os.pathsep + classpath, "InfraCheck", str(data)], check=True)
