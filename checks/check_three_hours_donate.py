"""Compile the production three-hour donation helper; fake HTTPS and authorization only."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
pkg = 'io.github.aw1y2z.sesame'
source = root / 'app/src/main/java'
task = source / pkg.replace('.', '/') / 'model/task/antSports/ThreeHoursDonate.java'
assert task.is_file(), 'ThreeHoursDonate production helper is missing'
sys.dont_write_bytecode = True
sys.path.insert(0, str(root / 'checks/audit_regressions'))
from run import method
host_run = method('model/task/antSports/AntSports.java', '    public void run()')
assert 'ThreeHoursDonate.run(threeHoursDonate.getValue())' in host_run
cancel_catch = host_run.index('catch (TaskCancelledException cancelled)')
assert host_run.index('throw cancelled;', cancel_catch) < host_run.index('catch (Throwable', cancel_catch)
host_step = method('model/task/antSports/AntSports.java', '    private void step(String name, Runnable action)')

stubs = {
    'hook.AuthCodeHelper': r'''import java.util.*; public class AuthCodeHelper {
 public static int calls; public static boolean empty; public static List<String> apps=new ArrayList<>();
 public static String getAuthCode(String app){apps.add(app);return empty?"":"private-code-"+(++calls)+"\"\\&";}}''',
    'util.idMap.UserIdMap': 'public class UserIdMap {public static String uid="A";public static String getCurrentUid(){return uid;}}',
    'util.MyUtils': r'''import org.json.*;import java.util.*;public class MyUtils {
 public static int offset;public static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,7+offset,12,0);return c;}
 public static JSONObject newJSONObject(){return new JSONObject();}public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}''',
    'util.TimeUtil': 'public class TimeUtil {public static boolean cancel;public static void sleep(long n){if(cancel)throw new TaskCancelledException();}}',
    'data.RuntimeInfo': r'''import java.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;public class RuntimeInfo {
 public static Map<String,RuntimeInfo> users=new HashMap<>();public static boolean writable=true;public Map<String,String> data=new HashMap<>();
 public static RuntimeInfo getInstance(){return users.computeIfAbsent(UserIdMap.uid,k->new RuntimeInfo());}public String getString(String k){return data.getOrDefault(k,"");}
 public boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}''',
    'util.Status': r'''import java.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;public class Status {
 public static Set<String> flags=new HashSet<>();public static Map<String,Integer> counts=new HashMap<>();static String key(String k){return UserIdMap.uid+":"+MyUtils.offset+":"+k;}
 public static boolean hasFlagToday(String k){return flags.contains(key(k));}public static void flagToday(String k){flags.add(key(k));}public static void flagToday(String k,String uid){assert uid.equals(UserIdMap.uid);flagToday(k);}
 public static int getIntFlagToday(String k){return counts.getOrDefault(key(k),0);}public static void setIntFlagToday(String k,int n){counts.put(key(k),n);}}''',
    'util.Log': r'''public class Log {public static String messages="";public static void other(String s){messages+=s;}public static void record(String s){messages+=s;}
 public static void err(String t,String s,Throwable e){messages+=t+s;}public static void error(String t,String s){messages+=t+s;}public static void printStackTrace(String t,Throwable e){messages+=t;}}''',
}

test = r'''
package io.github.aw1y2z.sesame.model.task.antSports;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class ThreeHoursDonateCheck {
 static class SportsHost {static final String TAG="AntSports";@@HOST_STEP@@}
 static final String RECEIPT="threeHoursDonateReceipt",SID="private-session",TOKEN="private-csrf",ANON="private-anonymous-csrf",COOKIE="private-anonymous-cookie",CONTEXT="private-anonymous-context";
 static int opened,closed,logins,reads,donations,http,donateOutputs,closedDonateOutputs;static ByteArrayOutputStream donationBody;static long today,remaining,donated;static boolean allDonated,reachLimit;
 static String failure,uuid;static boolean effect,unstable,huge,partial,cancelAfter,switchAfter,dayAfter,switchRead,dayRead;
 static Set<String> usedCodes=new HashSet<>();
 static Map<String,String> query(URL u)throws Exception{Map<String,String> m=new HashMap<>();String raw=u.getQuery();if(raw!=null)for(String item:raw.split("&")){String[] kv=item.split("=",2);m.put(URLDecoder.decode(kv[0],"UTF-8"),kv.length==2?URLDecoder.decode(kv[1],"UTF-8"):"");}return m;}
 static JSONObject body(ByteArrayOutputStream b){return new JSONObject(b.toString(StandardCharsets.UTF_8));}
 static void fresh(String code){assert code!=null&&code.startsWith("private-code-")&&code.endsWith("\"\\&"):"authCode was not safely encoded";assert usedCodes.add(code):"authorization code reused";}
 static class Fake extends HttpURLConnection {
  ByteArrayOutputStream sent=new ByteArrayOutputStream();boolean counted;Fake(URL u){super(u);opened++;assert u.getProtocol().equals("https");assert Set.of("3hours.taobao.com","m.3hours.taobao.com").contains(u.getHost()):u;
   if(u.getPath().endsWith("/donate"))assert !RuntimeInfo.getInstance().getString(RECEIPT).isEmpty():"mutation opened before durable reservation";}
  public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed++;}
  public OutputStream getOutputStream(){
   if(url.getPath().endsWith("/donate")){
    donateOutputs++;donationBody=sent;
    if(failure.equals("switchOutput"))UserIdMap.uid="B";
    if(failure.equals("cancelOutput"))TimeUtil.cancel=true;
    return new FilterOutputStream(sent){public void close()throws IOException{closedDonateOutputs++;super.close();}};
   }
   return sent;
  }
  public Map<String,List<String>> getHeaderFields(){
   if(!url.getPath().equals("/"))return Map.of();
   String cookie=failure.equals("cookieControl")?COOKIE+"\r":COOKIE;
   return Map.of("Set-Cookie",List.of("c_csrf="+cookie+"; Domain=.taobao.com; Path=/; Secure; HttpOnly", "csr-context="+CONTEXT+"; Domain=.taobao.com; Path=/; Secure", "unrelated_cookie=not-forwarded; Path=/; Secure"));
  }
  public int getResponseCode(){assert !getInstanceFollowRedirects():"redirects may leak headers";assert getConnectTimeout()==5000&&getReadTimeout()==8000;return failure.equals("mutationRedirect")&&url.getPath().endsWith("/donate")?302:http;}
  public InputStream getInputStream()throws IOException{assert !counted;counted=true;JSONObject result=new JSONObject().put("success",true).put("code",200),data=new JSONObject();String p=url.getPath();
   try {
    String currentUuid=getRequestProperty("csr-uuid");assert currentUuid!=null&&currentUuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"):"UUID must be locally generated v4";
    if(uuid==null)uuid=currentUuid;else assert uuid.equals(currentUuid);
    assert "true".equals(getRequestProperty("csr-account-v2"))&&"1".equals(getRequestProperty("csr-front-v"));
    assert getRequestProperty("Authorization")==null||getRequestProperty("Authorization").isEmpty();
    if(p.equals("/")){
     assert url.getHost().equals("3hours.taobao.com")&&getRequestMethod().equals("GET");assert getRequestProperty("Cookie")==null||getRequestProperty("Cookie").isEmpty();assert getRequestProperty("csr-token")==null;
     String html="<html><script>window.th_csrf=\""+ANON+"\";</script></html>";
     if(failure.equals("htmlMissing"))html="<html></html>";if(failure.equals("htmlDuplicate"))html+="<script>window.th_csrf=\"ambiguous\";</script>";if(huge)html+="x".repeat(65536);
     return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }
    String cookies=getRequestProperty("Cookie");assert cookies!=null&&cookies.contains("c_csrf="+COOKIE)&&cookies.contains("csr-context="+CONTEXT)&&!cookies.contains("unrelated_cookie"):"unexpected anonymous cookie scope";
    if(p.equals("/user/v2/login")){
     assert url.getHost().equals("3hours.taobao.com")&&getRequestMethod().equals("POST");assert getRequestProperty("csr-token")==null&&ANON.equals(getRequestProperty("csr-csrf"));assert ANON.equals(query(url).get("p_csrf"));
     logins++;JSONObject b=body(sent);fresh(b.optString("authCode"));assert b.optInt("namespace")==50&&Boolean.FALSE.equals(b.opt("isAuthorized"));
     data.put("sid",failure.equals("header")?SID+"\n":SID).put("token",failure.equals("csrfHeader")?TOKEN+"\r":TOKEN).put("sessionExpireTime",failure.equals("expired")?System.currentTimeMillis()-1000:System.currentTimeMillis()+600000);
     if(failure.equals("isoExpiry"))data.put("sessionExpireTime",java.time.Instant.ofEpochMilli(System.currentTimeMillis()+600000).toString());
     if(failure.equals("login"))data.remove("sid");if(failure.equals("emptyToken"))data.put("token","");if(failure.equals("badExpiry"))data.put("sessionExpireTime",new JSONObject());
    }else{
     assert url.getHost().equals("m.3hours.taobao.com");assert SID.equals(getRequestProperty("csr-token"))&&TOKEN.equals(getRequestProperty("csr-csrf")):"wrong session headers";
     if(p.endsWith("/getTodayRemainStep")){
      reads++;assert getRequestMethod().equals("GET");Map<String,String> q=query(url);fresh(q.get("authCode"));assert "Asia/Shanghai".equals(q.get("timeZone"));
      data.put("todaySteps",today).put("remainSteps",unstable&&reads==2?remaining+1:remaining).put("donatedSteps",donated).put("allDonated",allDonated).put("reachLimit",reachLimit);
      if(failure.equals("missing"))data.remove("todaySteps");if(failure.equals("fraction"))data.put("remainSteps",1.5);if(failure.equals("negative"))data.put("donatedSteps",-1);if(failure.equals("boolean"))data.put("allDonated","false");
      if(failure.equals("reachLimitType"))data.put("reachLimit","false");
      if(failure.equals("todayDrift")&&reads==2)data.put("todaySteps",today+1);if(failure.equals("donatedDrift")&&reads==2)data.put("donatedSteps",donated+1);if(failure.equals("allDrift")&&reads==2)data.put("allDonated",true);if(failure.equals("limitDrift")&&reads==2)data.put("reachLimit",true);
      if(failure.equals("todayAfter")&&reads==3)data.put("todaySteps",today+1);
      if(switchRead&&reads==2)UserIdMap.uid="B";if(dayRead&&reads==2)MyUtils.offset++;
     }else if(p.endsWith("/donate")){
      assert getRequestMethod().equals("POST");JSONObject b=body(sent);assert b.optLong("todayExerciseSteps",-1)==today&&TOKEN.equals(query(url).get("p_csrf"));assert !b.has("authCode");
      donations++;assert !RuntimeInfo.getInstance().getString(RECEIPT).isEmpty();data.put("success",!failure.equals("ack"));
      long delta=partial?remaining/2:remaining;data.put("donatedSteps",delta).put("totalDonatedToday",donated+delta).put("remainStepsAfter",remaining-delta).put("allDonated",remaining==delta).put("reachLimit",false);
      if(failure.equals("ackTotal"))data.put("totalDonatedToday",donated+delta+1);if(failure.equals("ackRemaining"))data.put("remainStepsAfter",remaining-delta+1);if(failure.equals("ackType"))data.put("success","true");
      if(effect){remaining-=delta;donated+=failure.equals("wrongDelta")?delta-1:delta;allDonated=remaining==0;}if(cancelAfter)TimeUtil.cancel=true;if(switchAfter)UserIdMap.uid="B";if(dayAfter)MyUtils.offset++;
     }else throw new AssertionError("unproven endpoint "+p);
    }
   }catch(Exception e){throw new IOException(e);}
   result.put("data",data);if(failure.equals("code"))result.remove("code");if(failure.equals("codeFraction"))result.put("code",200.1);if(failure.equals("success"))result.put("success","true");if(failure.equals("leak"))result.put("success",false).put("msg",SID+TOKEN+"private-code-");
   if(huge)result.put("padding","x".repeat(65536));
   return new ByteArrayInputStream(result.toString().getBytes(StandardCharsets.UTF_8));
  }
 }
 static void reset(){opened=closed=logins=reads=donations=donateOutputs=closedDonateOutputs=0;donationBody=null;http=200;today=15000;remaining=12000;donated=3000;allDonated=reachLimit=false;failure="";uuid=null;effect=true;unstable=huge=partial=cancelAfter=switchAfter=dayAfter=switchRead=dayRead=false;usedCodes.clear();AuthCodeHelper.calls=0;AuthCodeHelper.empty=false;AuthCodeHelper.apps.clear();RuntimeInfo.users.clear();RuntimeInfo.writable=true;UserIdMap.uid="A";MyUtils.offset=0;TimeUtil.cancel=false;Status.flags.clear();Status.counts.clear();Log.messages="";}
 static void run(){ThreeHoursDonate.run(true);assert opened==closed:"HTTP connection leaked";}
 static void noSecrets(){for(String secret:new String[]{SID,TOKEN,ANON,COOKIE,CONTEXT,"private-code-"}){assert !Log.messages.contains(secret);for(RuntimeInfo r:RuntimeInfo.users.values())for(String s:r.data.values())assert !s.contains(secret):"credential persisted";}}
 public static void main(String[] args)throws Exception{
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));URL.setURLStreamHandlerFactory(p->p.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Fake(u);}}:null);
  reset();ThreeHoursDonate.run(false);assert opened==0&&AuthCodeHelper.calls==0;
  reset();UserIdMap.uid="";run();assert opened==0&&AuthCodeHelper.calls==0;
  reset();TimeUtil.cancel=true;try{run();throw new AssertionError("initial cancel swallowed");}catch(TaskCancelledException expected){}assert opened==0;
  reset();int[] dispatched={0};TimeUtil.cancel=true;try{new SportsHost().step("donate",()->dispatched[0]++);throw new AssertionError("sports step swallowed initial cancel");}catch(TaskCancelledException expected){}assert dispatched[0]==0;
  reset();try{new SportsHost().step("donate",()->{throw new TaskCancelledException();});throw new AssertionError("sports step swallowed action cancel");}catch(TaskCancelledException expected){}
  reset();run();assert logins==1&&reads==3&&donations==1&&remaining==0&&donated==15000;assert AuthCodeHelper.calls==4;assert AuthCodeHelper.apps.stream().allMatch(a->a.equals("2019052265312523"));assert RuntimeInfo.getInstance().getString(RECEIPT).isEmpty();noSecrets();remaining=12000;donated=3000;allDonated=false;Status.flags.clear();Status.counts.clear();run();assert donations==1&&logins==1:"persisted daily guard did not stop a second attempt";
  reset();failure="isoExpiry";run();assert donations==1;
  reset();partial=true;run();assert donations==1&&remaining==6000&&donated==9000&&RuntimeInfo.getInstance().getString(RECEIPT).isEmpty();run();assert donations==1;
  for(String mode:new String[]{"htmlMissing","htmlDuplicate","cookieControl","login","header","csrfHeader","expired","emptyToken","badExpiry","missing","fraction","negative","boolean","reachLimitType","code","codeFraction","success","leak","todayDrift","donatedDrift","allDrift","limitDrift"}){reset();failure=mode;run();assert donations==0:mode;noSecrets();}
  reset();remaining=0;allDonated=true;run();assert donations==0;
  reset();today=0;remaining=0;donated=0;run();assert donations==0;
  reset();reachLimit=true;run();assert donations==0;
  reset();unstable=true;run();assert donations==0;
  reset();RuntimeInfo.writable=false;run();assert donations==0;
  reset();AuthCodeHelper.empty=true;run();assert logins==0&&donations==0;
  reset();http=302;run();assert donations==0&&opened==1;assert Log.messages.contains("3hours.taobao.com/")&&Log.messages.contains("HTTP=302")&&Log.messages.contains("尚未提交")&&!Log.messages.contains("保留未确认回执"):"pre-donation failure incorrectly claimed a receipt";
  reset();http=429;run();assert donations==0;
  reset();huge=true;run();assert donations==0;
  for(String mode:new String[]{"ack","ackType","ackTotal","ackRemaining","todayAfter","wrongDelta"}){reset();failure=mode;run();assert donations==1&&!RuntimeInfo.getInstance().getString(RECEIPT).isEmpty():mode;MyUtils.offset++;Status.flags.clear();Status.counts.clear();run();assert donations==1;noSecrets();}
  reset();failure="mutationRedirect";run();assert donations==0&&!RuntimeInfo.getInstance().getString(RECEIPT).isEmpty();int calls=opened;MyUtils.offset++;run();assert opened==calls;
  reset();effect=false;run();assert donations==1&&!RuntimeInfo.getInstance().getString(RECEIPT).isEmpty();MyUtils.offset++;Status.flags.clear();Status.counts.clear();run();assert donations==1;
  reset();RuntimeInfo.getInstance().data.put(RECEIPT,"corrupt");run();assert opened==0&&donations==0;
  for(String mode:new String[]{"switchOutput","cancelOutput"}){
   reset();failure=mode;
   try{run();throw new AssertionError("output-stream interruption swallowed: "+mode);}catch(TaskCancelledException expected){}
   assert donateOutputs==1&&closedDonateOutputs==1&&donationBody!=null&&donationBody.size()==0:"body written after output-stream interruption: "+mode;
   assert donations==0&&reads==2&&opened==closed:"request continued or connection leaked: "+mode;
   RuntimeInfo owner=RuntimeInfo.users.get("A");assert owner!=null&&!owner.getString(RECEIPT).isEmpty()&&owner.getString("threeHoursDonateDoneDay").isEmpty();
   UserIdMap.uid="A";TimeUtil.cancel=false;failure="";MyUtils.offset++;int previousOpened=opened;
   run();assert opened==previousOpened&&donateOutputs==1:"unknown output-stream attempt retried across day";noSecrets();
  }
  reset();cancelAfter=true;try{run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert donations==1&&reads==2&&opened==closed;assert !RuntimeInfo.users.get("A").getString(RECEIPT).isEmpty();
  reset();switchAfter=true;try{run();}catch(TaskCancelledException expected){}assert donations==1&&reads==2&&opened==closed&&!RuntimeInfo.users.get("A").getString(RECEIPT).isEmpty();
  reset();dayAfter=true;try{run();}catch(TaskCancelledException expected){}assert donations==1&&reads==2&&opened==closed&&!RuntimeInfo.users.get("A").getString(RECEIPT).isEmpty();
  reset();switchRead=true;try{run();}catch(TaskCancelledException expected){}assert donations==0&&opened==closed;
  reset();dayRead=true;try{run();}catch(TaskCancelledException expected){}assert donations==0&&opened==closed;
  System.out.println("PASS production three-hour dynamic login/fresh read auth/stable live steps/exact donation readback, durable daily/unknown guards, HTTP/header/redirect/bounds, account/day/cancel and no credential persistence");
 }
}
'''
test = test.replace('@@HOST_STEP@@', host_step)

cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar', '-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-three-hours-') as temp:
    files = []
    for name, body in stubs.items():
        full = pkg + '.' + name
        path = Path(temp) / (full.replace('.', '/') + '.java')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('package ' + full.rsplit('.', 1)[0] + ';\n' + body, encoding='utf-8')
        files.append(str(path))
    path = Path(temp) / 'ThreeHoursDonateCheck.java'
    path.write_text(test, encoding='utf-8')
    files += [str(path), str(task), str(source / pkg.replace('.', '/') / 'util/TaskCancelledException.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', temp] + files, check=True)
    subprocess.run(['java', '-ea', '-cp', temp + os.pathsep + str(jar), pkg + '.model.task.antSports.ThreeHoursDonateCheck'], check=True)
