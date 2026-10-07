"""Compile actual optional animal signup task; intercept HTTPS without real accounts/network."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[1]
pkg='io.github.aw1y2z.sesame'
source=root/'app/src/main/java'
task=source/pkg.replace('.','/')/'model/task/antAnimalGuess/AntAnimalGuess.java'
assert task.is_file(), 'Missing optional animal sign-in task'
assert 'AntAnimalGuess.class' in (source/pkg.replace('.','/')/'model/base/ModelOrder.java').read_text(encoding='utf-8')
stubs={
 'data.ModelGroup':'public enum ModelGroup {OTHER}',
 'data.ModelFields':'''import java.util.*; import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
 public class ModelFields {public Map<String,BooleanModelField> fields=new HashMap<>();public void addField(BooleanModelField f){fields.put(f.id,f);}}''',
 'data.modelFieldExt.BooleanModelField':'''public class BooleanModelField {public String id;public boolean value;public BooleanModelField(String i,String n,boolean b){id=i;value=b;}public Boolean getValue(){return value;}}''',
 'data.task.ModelTask':'''import io.github.aw1y2z.sesame.data.*;public abstract class ModelTask {public boolean enabled=true;public Boolean isEnable(){return enabled;}public abstract String getName();public abstract ModelGroup getGroup();public abstract ModelFields getFields();public abstract Boolean check();public abstract void run();}''',
 'model.base.TaskCommon':'public class TaskCommon {public static boolean IS_ENERGY_TIME;}',
 'hook.ApplicationHook':'public class ApplicationHook {public static boolean offline;public static boolean isOffline(){return offline;}}',
 'hook.AuthCodeHelper':'''import java.util.*;public class AuthCodeHelper {public static String code="secret-code";public static List<String> apps=new ArrayList<>();public static String getAuthCode(String a){apps.add(a);return code;}}''',
 'util.MyUtils':'''import org.json.*;import java.util.*;public class MyUtils {public static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}public static JSONObject newJSONObject(){return new JSONObject();}public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}''',
 'util.TimeUtil':'public class TimeUtil {public static boolean cancel;public static void sleep(long n){if(cancel)throw new TaskCancelledException();}}',
 'util.idMap.UserIdMap':'public class UserIdMap {public static String uid="A";public static String getCurrentUid(){return uid;}}',
 'util.Status':'''import java.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;public class Status {public static Set<String> flags=new HashSet<>();public static boolean hasFlagToday(String s){return flags.contains(UserIdMap.uid+s);}public static void flagToday(String s,String uid){assert uid.equals(UserIdMap.uid);flags.add(uid+s);}}''',
 'util.Log':'public class Log {public static String messages="";public static void other(String s){messages+=s;}public static void record(String s){messages+=s;}}',
}
test=r'''
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
import io.github.aw1y2z.sesame.model.task.antAnimalGuess.AntAnimalGuess;import io.github.aw1y2z.sesame.data.*;
import io.github.aw1y2z.sesame.hook.*;import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class AnimalGuessCheck {
 static int opened,closed,signs,configs,races,logins;static int http=200;static String failure="";static boolean noEffect,switchAccount,cancelAfterSign,huge;
 static class Fake extends HttpURLConnection {
  ByteArrayOutputStream sent=new ByteArrayOutputStream();Fake(URL u){super(u);opened++;assert u.getProtocol().equals("https");assert u.getHost().equals("qumaoxianapi.dongwuyouxi.com")||u.getHost().equals("quchongciapi.dongwuyouxi.com");}
  public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed++;}
  public OutputStream getOutputStream(){return sent;}
  public int getResponseCode(){assert !getInstanceFollowRedirects();assert getConnectTimeout()>0&&getReadTimeout()>0;return http;}
  public InputStream getInputStream(){String p=url.getPath();JSONObject result=new JSONObject().put("code",0);Object data;
   if(p.endsWith("zfb-mini-app-login")){logins++;assert getRequestMethod().equals("POST");assert getRequestProperty("Authorization")==null;assert new JSONObject(sent.toString(StandardCharsets.UTF_8)).optString("loginCode").equals(AuthCodeHelper.code);data=new JSONObject().put("accessToken","secret-token");}
   else {assert getRequestProperty("Authorization").equals("Bearer secret-token");
    if(p.endsWith("/user/get"))data=new JSONObject().put("point",100);
    else if(p.endsWith("/config/list")){configs++;data=new JSONArray().put(new JSONObject().put("status",signs>0&&!noEffect?1:0)).put(new JSONObject().put("status",0));if(failure.equals("config"))data=new JSONArray().put(new JSONObject());}
    else if(p.endsWith("/record/create")){signs++;data=1;if(switchAccount)UserIdMap.uid="B";if(cancelAfterSign)TimeUtil.cancel=true;}
    else if(p.endsWith("/guess-race/get")){races++;data=new JSONObject().put("raceNumber","race").put("startTime","start").put("endTime","end");}
    else throw new AssertionError(p);
   }
   result.put("data",data);if(failure.equals("code"))result.remove("code");if(failure.equals("fraction"))result.put("code",0.1);if(failure.equals("leak"))result.put("code",500).put("msg","secret-code secret-token");
   String raw=huge?"x".repeat(1024*1024+1):result.toString();return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
  }
 }
 static void reset(){opened=closed=signs=configs=races=logins=0;http=200;failure="";noEffect=switchAccount=cancelAfterSign=huge=false;TimeUtil.cancel=false;UserIdMap.uid="A";Status.flags.clear();Log.messages="";AuthCodeHelper.apps.clear();AuthCodeHelper.code="secret-code";}
 public static void main(String[] args)throws Exception {
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Fake(u);}}:null);
  AntAnimalGuess task=new AntAnimalGuess();ModelFields fields=task.getFields();reset();task.run();assert opened==0;
  fields.fields.get("adventureSign").value=true;task.run();assert signs==1&&races==1&&logins==1&&configs==2&&opened==closed;task.run();assert signs==1&&races==2;assert AuthCodeHelper.apps.size()==2:"must authorize fresh each run";
  for(String mode:new String[]{"code","fraction","config","leak"}){reset();failure=mode;task.run();assert signs==0&&opened==closed;assert !Log.messages.contains("secret-code")&&!Log.messages.contains("secret-token");}
  reset();noEffect=true;task.run();assert signs==1;task.run();assert signs==1:"unconfirmed sign repeated";
  reset();http=302;task.run();assert opened==1&&closed==1&&signs==0;
  reset();huge=true;task.run();assert signs==0&&opened==closed;
  reset();AuthCodeHelper.code="";task.run();assert opened==0;
  reset();switchAccount=true;try{task.run();throw new AssertionError("account switch swallowed");}catch(TaskCancelledException expected){}assert races==0&&opened==closed;
  reset();cancelAfterSign=true;try{task.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert signs==1&&races==0&&opened==closed;
  reset();fields.fields.get("adventureSign").value=false;fields.fields.get("sprintSign").value=true;task.run();assert AuthCodeHelper.apps.equals(Arrays.asList("2021006114686014"));assert signs==1&&opened==closed;
  System.out.println("PASS optional/fresh authorization, sign readback/daily guard, unknown responses, no secrets/redirect, HTTP bound/disconnect, account switch/cancel and both projects");
 }
}
'''
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-animal-') as tmp:
 files=[]
 for name,body in stubs.items():
  full=pkg+'.'+name;path=Path(tmp)/(full.replace('.','/')+'.java');path.parent.mkdir(parents=True,exist_ok=True);path.write_text('package '+full.rsplit('.',1)[0]+';\n'+body,encoding='utf-8');files.append(str(path))
 path=Path(tmp)/'AnimalGuessCheck.java';path.write_text(test,encoding='utf-8');files.extend([str(path),str(task),str(source/pkg.replace('.','/')/'util/TaskCancelledException.java')])
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp]+files,check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'AnimalGuessCheck'],check=True)
