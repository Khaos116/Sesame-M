"""Replay actual optional flower login, request signature and sign-in without network."""
from pathlib import Path
import os, subprocess, tempfile
import hashlib,re
root=Path(__file__).resolve().parents[1];pkg='io.github.aw1y2z.sesame';source=root/'app/src/main/java'
task=source/pkg.replace('.','/')/'model/task/plantingFlowers/PlantingFlowers.java'
assert task.is_file()
assert 'PlantingFlowers.class' in (source/pkg.replace('.','/')/'model/base/ModelOrder.java').read_text(encoding='utf-8')
text=task.read_text(encoding='utf-8')
dictionary=text[text.index('private static final char[] DICT'):text.index('@Override public String getName')]
assert hashlib.sha256(''.join(re.findall("'(.)'",dictionary)).encode()).hexdigest()=='4a9e8e6d40b73d4deb6eec18d2996965f899edea5620fc1e6575de628d91d872'
stubs={'data.ModelGroup': 'public enum ModelGroup {OTHER}',
 'data.ModelField': 'public class ModelField {public String id;public Object value;}',
 'data.ModelFields': 'import java.util.*; public class ModelFields {public Map<String,ModelField> fields=new '
                     'HashMap<>();public void addField(ModelField f){fields.put(f.id,f);}}',
 'data.modelFieldExt.BooleanModelField': 'public class BooleanModelField extends io.github.aw1y2z.sesame.data.ModelField {'
                                         'public BooleanModelField(String i,String n,boolean '
                                         'b){id=i;value=b;}public Boolean getValue(){return (Boolean)value;}public BooleanModelField setDescription(String s){return this;}}',
 'data.RuntimeInfo': 'import java.util.*;public class RuntimeInfo {public static Map<String,RuntimeInfo> users=new HashMap<>();public static boolean writable=true;private Map<String,String> values=new HashMap<>();public static RuntimeInfo getInstance(){return users.computeIfAbsent(io.github.aw1y2z.sesame.util.idMap.UserIdMap.uid,k->new RuntimeInfo());}public String getString(String k){return values.getOrDefault(k,"");}public boolean putVerified(String k,String v){if(!writable)return false;values.put(k,v);return true;}}',
 'data.modelFieldExt.StringModelField': 'public class StringModelField extends io.github.aw1y2z.sesame.data.ModelField {public StringModelField(String i,String n,String v){id=i;value=v;}public String getValue(){return (String)value;}public StringModelField setDescription(String s){return this;}}',
 'data.modelFieldExt.IntegerModelField': 'public class IntegerModelField extends io.github.aw1y2z.sesame.data.ModelField {public IntegerModelField(String i,String n,int v,int a,int b){id=i;value=v;}public Integer getValue(){return (Integer)value;}}',
 'data.task.ModelTask': 'import io.github.aw1y2z.sesame.data.*;public abstract class ModelTask {public '
                        'boolean enabled=true;public Boolean isEnable(){return enabled;}public abstract '
                        'String getName();public abstract ModelGroup getGroup();public abstract ModelFields '
                        'getFields();public abstract Boolean check();public abstract void run();'
                        'public long generation=1;public boolean busy;protected long taskGeneration(){return generation;}'
                        'protected boolean runExclusiveChild(long g,Runnable r){if(busy||g!=generation)return false;r.run();return true;}'
                        'public java.util.Map<String,ChildModelTask> children=new java.util.HashMap<>();'
                        'public ChildModelTask getChildTask(String id){return children.get(id);}public void removeChildTask(String id){children.remove(id);}'
                        'public boolean addChildTask(ChildModelTask c){children.put(c.id,c);return true;}'
                        'public static class ChildModelTask {public String id;public Runnable action;long at;public ChildModelTask(String i,String g,Runnable r,long t){id=i;action=r;at=t;}public Boolean getIsCancel(){return false;}public Long getExecTime(){return at;}}}',
 'model.base.TaskCommon': 'public class TaskCommon {public static boolean IS_ENERGY_TIME;}',
 'hook.ApplicationHook': 'public class ApplicationHook {public static boolean offline;public static boolean '
                         'isOffline(){return offline;}}',
 'hook.AuthCodeHelper': 'import java.util.*;public class AuthCodeHelper {public static String '
                        'code="secret-code";public static List<String> apps=new ArrayList<>();public static '
                        'String getAuthCode(String a){apps.add(a);return code;}}',
 'util.MyUtils': 'import org.json.*;import java.util.*;public class MyUtils {public static Calendar '
                 'getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.add(Calendar.DAY_OF_MONTH,offset);return c;}public static int offset;public static '
                 'JSONObject newJSONObject(){return new JSONObject();}public static JSONObject '
                 'newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new '
                 'JSONObject();}}}',
 'util.TimeUtil': 'public class TimeUtil {public static boolean cancel;public static void sleep(long '
                  'n){waited+=n;if(cancelAt>0&&waited>=cancelAt)cancel=true;if(cancel)throw new TaskCancelledException();}public static long waited,cancelAt;}',
 'util.idMap.UserIdMap': 'public class UserIdMap {public static String uid="A";public static String '
                         'getCurrentUid(){return uid;}}',
 'util.Status': 'import java.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;public class Status '
                '{public static Set<String> flags=new HashSet<>();public static boolean hasFlagToday(String '
                's){return flags.contains(UserIdMap.uid+s);}public static void flagToday(String s,String '
                'uid){assert uid.equals(UserIdMap.uid);flags.add(uid+s);}'
                'public static Map<String,Integer> counts=new HashMap<>();public static int getIntFlagToday(String k){return counts.getOrDefault(UserIdMap.uid+k,0);}public static void setIntFlagToday(String k,int v){counts.put(UserIdMap.uid+k,v);}}',
 'util.Log': 'public class Log {public static String messages="";public static void other(String '
             's){messages+=s;}public static void record(String s){messages+=s;}}'}

test=r'''
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.security.*;import java.util.*;import org.json.*;
import javax.crypto.*;import javax.crypto.spec.*;
import io.github.aw1y2z.sesame.model.task.plantingFlowers.PlantingFlowers;import io.github.aw1y2z.sesame.data.*;
import io.github.aw1y2z.sesame.hook.*;import io.github.aw1y2z.sesame.util.*;import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
public class FlowersCheck {
 static final String UID="2088000000000000";static int opened,closed,signs,queries,logins,rewards,rewardQueries;static int http=200;static String failure="";static double rewardAmount=10;static boolean rewardReceived,rewardEffect=true,rewardDrift;static boolean noEffect,switchAccount,cancelAfterSign,huge,drift,received;
 static int clouds,bugs,cloudQueries,bugQueries;static boolean inactive,doubleActive,configEffect=true,waterEffect=true;static double water=100,feeds=10;
 static int beanCollects,waters,waterActive;static boolean beanEffect=true,wateringEffect=true;static long waterEnd;static double beanBalance=50;
 static int assists,shareQueries,poolGets,ownUploads,recordUploads;static String shareState="success",ownSecret="own-secret",poolResponse="[]";static boolean poolHeaders,externalSwitch,externalCancel;static String recordBody="";static List<String> assisted=new ArrayList<>();
 static int newcomers,weeds,factoryRewards,draws,cardRewards,cardUses,taskClicks,taskFinishes,taskRewards;
 static double cardInventory=2,factoryCount,drawUsed,drawMax=2;static boolean extraEffect=true,cardEligible=true,factoryRepeat;
 static String factoryTime;static Set<Integer> rewardedCards=new HashSet<>();static JSONArray dataTasks;static long clickWait;static String clickedId;
 static JSONObject dataTask(String id,int type,int seconds){return new JSONObject().put("id",id).put("taskType",type).put("visitSeconds",seconds).put("alreadyFinish",false).put("finished",false);}
 static void extraMutation(){if(switchAccount)UserIdMap.uid="2088000000000001";if(cancelAfterSign)TimeUtil.cancel=true;}
 static java.text.SimpleDateFormat dateFormat(String pattern){java.text.SimpleDateFormat f=new java.text.SimpleDateFormat(pattern,Locale.ROOT);f.setTimeZone(TimeZone.getTimeZone("GMT+8"));return f;}
 static String hex(byte[] input){StringBuilder s=new StringBuilder();for(byte b:input)s.append(String.format("%02x",b));return s.toString();}
 static byte[] unhex(String s){byte[] result=new byte[s.length()/2];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return result;}
 static String shifted(String s){StringBuilder b=new StringBuilder();int add=1;for(int i=0;i<s.length();i++)b.append(i%2==0?(char)('0'+(s.charAt(i)-'0'+add++)%10):s.charAt(i));return b.toString();}
 static void checkSignature(String signature)throws Exception{
  StringBuilder clean=new StringBuilder();for(int i=0;i<signature.length();i+=6){int end=Math.min(i+5,signature.length()-1);clean.append(signature,i,end);assert signature.charAt(end)>='a'&&signature.charAt(end)<='z';}
  Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(unhex("77122edad8d75adabe88c2b59fe3b4a1a12247f04cf0d1bac82e66abb6ae5bfa"),"AES"),new IvParameterSpec(unhex("cb252df83e58e8a2fab5a6eda437e9b1")));
  String plain=new String(cipher.doFinal(unhex(clean.toString())),StandardCharsets.UTF_8);assert plain.matches("(?:[0-9a-f]{4}[a-z])+");
  java.lang.reflect.Field mapping=PlantingFlowers.class.getDeclaredField("DICT");mapping.setAccessible(true);char[] dict=(char[])mapping.get(null);StringBuilder digits=new StringBuilder();
  for(int i=0;i<plain.length();i+=5){char glyph=(char)Integer.parseInt(plain.substring(i,i+4),16);int index=-1;for(int j=0;j<dict.length;j++)if(dict[j]==glyph)index=j;assert index>=0;digits.append(String.format("%02d",index));}
  for(int at:new int[]{23,17,11,5})digits.deleteCharAt(at);StringBuilder user=new StringBuilder(),time=new StringBuilder();for(int i=0;i<20;i+=2){user.append(digits.charAt(i));time.append(digits.charAt(i+1));}user.append(digits.substring(20));assert user.toString().equals(shifted(UID));
  long now=System.currentTimeMillis()/1000;assert time.toString().equals(shifted(String.valueOf(now)))||time.toString().equals(shifted(String.valueOf(now-1))):"timestamp signature";
 }
 static class Fake extends HttpURLConnection {
  ByteArrayOutputStream sent=new ByteArrayOutputStream();Fake(URL u){super(u);opened++;assert u.getProtocol().equals("https")&&(u.getHost().equals("plantingflowers.zhisanzhao.com")||u.getHost().equals("datacenter.zhisanzhao.com")||u.getHost().equals("pool.example"));assert u.getHost().equals("datacenter.zhisanzhao.com")==u.getPath().startsWith("/dataCenter/");}
  public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed++;}public OutputStream getOutputStream(){return sent;}
  public int getResponseCode(){assert !getInstanceFollowRedirects()&&getConnectTimeout()>0&&getReadTimeout()>0;return failure.equals("post")&&!url.getPath().endsWith("/getShareSecret")&&(getRequestMethod().equals("POST")||url.getPath().endsWith("/dailyDraw/lottery"))?500:http;}
  public InputStream getInputStream(){try{String p=url.getPath();JSONObject payload=new JSONObject();
   if(url.getHost().equals("pool.example")){assert p.endsWith("/PlantingFlowersShare/"+UID);if(p.startsWith("/upload/")){assert getRequestMethod().equals("POST");JSONObject b=new JSONObject(sent.toString(StandardCharsets.UTF_8));assert b.length()==1&&b.optString("shareId").equals(ownSecret);ownUploads++;}else if(p.startsWith("/records/")){assert getRequestMethod().equals("POST");JSONArray b=new JSONArray(sent.toString(StandardCharsets.UTF_8));assert b.length()>=1&&b.length()<=3;JSONObject row=b.optJSONObject(0);assert row.length()==3&&row.optString("shareId").equals("friend-secret")&&row.optInt("state")==0&&row.optString("userId").equals("friend");recordBody=b.toString();recordUploads++;}else {assert getRequestMethod().equals("GET")&&p.equals("/list/PlantingFlowersShare/"+UID);poolGets++;}if(getRequestMethod().equals("POST")){if(externalSwitch)UserIdMap.uid="2088000000000001";if(externalCancel)TimeUtil.cancel=true;}assert getRequestProperty("token")==null&&getRequestProperty("uid")==null&&getRequestProperty("aesEncryption5")==null&&getRequestProperty("coffeeEncryption")==null&&getRequestProperty("dataCenterEncryption")==null;assert "".equals(getRequestProperty("Authorization"))&&"".equals(getRequestProperty("Cookie"));poolHeaders=true;return new ByteArrayInputStream(poolResponse.getBytes(StandardCharsets.UTF_8));}
   if(p.endsWith("/apiLogin/login")){logins++;assert getRequestMethod().equals("GET")&&getRequestProperty("token")==null;assert url.getQuery().contains("code="+URLEncoder.encode(AuthCodeHelper.code,"UTF-8"));payload.put("token",failure.equals("header")?"secret-token\n": "secret-token").put("user",new JSONObject().put("alipayId",failure.equals("uid")?"other":UID));}
   else {
    assert getRequestProperty("token").equals("secret-token")&&getRequestProperty("uid").equals(UID)&&getRequestProperty("channel").equals("fenxiang");
    String day=dateFormat("yyyy-MM-dd").format(MyUtils.getInstance().getTime());assert getRequestProperty("dataCenterEncryption").equals(hex(MessageDigest.getInstance("MD5").digest((UID+"dj#i9a%u4j^2e#w*n43"+day).getBytes(StandardCharsets.UTF_8))));checkSignature(getRequestProperty("aesEncryption5"));
    if(p.endsWith("/getShareSecret")){shareQueries++;assert getRequestMethod().equals("POST")&&new JSONObject(sent.toString(StandardCharsets.UTF_8)).length()==0;payload.put("_secret",ownSecret);}
    else if(p.endsWith("/clickFriendShare/addFriend")){assists++;JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));assert getRequestMethod().equals("POST")&&body.length()==1&&body.opt("shareSecret") instanceof String;assisted.add(body.optString("shareSecret"));payload.put("state",shareState);extraMutation();}
    else if(p.endsWith("/querySignInStatus")){queries++;payload.put("todayAlreadySign",received||signs>0&&!noEffect||drift&&queries==2);if(failure.equals("status"))payload.put("todayAlreadySign",0);if(failure.equals("missing"))payload.remove("todayAlreadySign");}
    else if(p.endsWith("/finish2")){signs++;assert getRequestMethod().equals("POST")&&new JSONObject(sent.toString(StandardCharsets.UTF_8)).optInt("rewardSource")==3;if(switchAccount)UserIdMap.uid="2088000000000001";if(cancelAfterSign)TimeUtil.cancel=true;}
    else if(p.endsWith("/waterTomorrowReward/query")){rewardQueries++;payload.put("yesterdayFertilizationReward",rewardAmount+(rewardDrift&&rewardQueries==2?1:0)).put("todayIsReceived",rewardReceived||rewards>0&&rewardEffect);if(failure.equals("rewardFlag"))payload.remove("todayIsReceived");}
    else if(p.endsWith("/receivedFeed")){rewards++;assert getRequestMethod().equals("POST")&&new JSONObject(sent.toString(StandardCharsets.UTF_8)).optInt("rewardSource")==3;if(cancelAfterSign)TimeUtil.cancel=true;}
    else if(p.endsWith("/cloudRewardConfig")||p.endsWith("/bugRewardConfig")){boolean cloud=p.endsWith("/cloudRewardConfig");if(cloud)cloudQueries++;else bugQueries++;JSONObject row=new JSONObject().put("currently",!inactive).put(cloud?"isFinish":"alreadyFinish",configEffect&&(cloud?clouds:bugs)>0).put("rewardNum",rewardAmount);if(failure.equals("timedFlag"))row.remove(cloud?"isFinish":"alreadyFinish");JSONArray list=new JSONArray().put(row);if(doubleActive)list.put(new JSONObject(row.toString()));payload.put("currently",list);}
    else if(p.endsWith("/coffeeBeans/develop")){double balance=beanBalance+(beanEffect?beanCollects*5:0);payload.put("numberWater",water+(waterEffect?(clouds+bugs)*feeds:0)-(wateringEffect?waters*100:0)).put("goldenBeansNumber",balance).put("numberCoffeeBeans",balance+(failure.equals("beanConflict")?1:0)).put("isWatering",wateringEffect&&waters>0?1:waterActive).put("endWaterTime",waterEnd).put("acceleratorCardNumber",cardInventory).put("accelerateCard",cardInventory+(failure.equals("cardConflict")?1:0));if(failure.equals("waterMissing"))payload.remove("isWatering");}
    else if(p.endsWith("/collect/goldenBeans")||p.endsWith("/water/coffeeTree")){assert getRequestMethod().equals("POST");JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));if(p.endsWith("goldenBeans")){beanCollects++;assert body.length()==0;}else {waters++;assert Boolean.TRUE.equals(body.opt("isWaterTomorrow"));}if(switchAccount)UserIdMap.uid="2088000000000001";if(cancelAfterSign)TimeUtil.cancel=true;}
    else if(p.endsWith("/bugReward")){JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));int type=body.optInt("rewardType");assert getRequestMethod().equals("POST");if(type==4){clouds++;assert body.optInt("type")==2;}else {assert type==1&&!body.has("type");bugs++;}payload.put("feeds",feeds);if(switchAccount)UserIdMap.uid="2088000000000001";if(cancelAfterSign)TimeUtil.cancel=true;}
    else if(p.endsWith("/acceleratorCard/newUserReward")){assert getRequestMethod().equals("POST")&&new JSONObject(sent.toString(StandardCharsets.UTF_8)).length()==0;newcomers++;if(extraEffect)cardInventory+=2;extraMutation();}
    else if(p.endsWith("/sendWeedingReward")){assert new JSONObject(sent.toString(StandardCharsets.UTF_8)).optInt("taskSource")==1;weeds++;if(extraEffect)water+=feeds;extraMutation();}
    else if(p.endsWith("/feedFactory/info")){payload.put("todayCount",factoryCount).put("endTime",factoryTime);if(failure.equals("factoryType"))payload.put("todayCount","0");}
    else if(p.endsWith("/feedFactory/reward")){assert new JSONObject(sent.toString(StandardCharsets.UTF_8)).optInt("rewardSource")==1;factoryRewards++;payload.put("feeds",feeds);if(extraEffect){factoryCount++;water+=feeds;if(!factoryRepeat)factoryTime=dateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()+3600000));}extraMutation();}
    else if(p.endsWith("/dailyDraw/activity/info")){payload.put("activityUseNumber",drawUsed).put("activityMaxNumber",drawMax);if(failure.equals("drawType"))payload.put("activityMaxNumber","2");}
    else if(p.endsWith("/dailyDraw/lottery")){assert getRequestMethod().equals("GET");draws++;payload.put("awardPrice",feeds).put("awardName","water");if(extraEffect){drawUsed++;water+=feeds;}extraMutation();}
    else if(p.endsWith("/receive/acceleratorCard")){JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));int type=body.optInt("type");assert type>=1&&type<=3;if(Boolean.TRUE.equals(body.opt("isFinish"))){cardRewards++;if(extraEffect){rewardedCards.add(type);cardInventory++;}extraMutation();}payload.put("state",cardEligible).put("alreadyFinish",rewardedCards.contains(type));if(failure.equals("cardTaskType"))payload.put("state","true");}
    else if(p.endsWith("/use/acceleratorCard")){assert new JSONObject(sent.toString(StandardCharsets.UTF_8)).length()==0;cardUses++;if(extraEffect){cardInventory--;waterActive=0;}extraMutation();}
    else if(p.equals("/dataCenter/api/task/list")){assert url.getQuery().contains("uid="+UID)&&url.getQuery().contains("mark=PLANTING_FLOWERS_JJEGG_LIST");payload.put("userId","data-user").put("taskList",new JSONArray(dataTasks.toString()));if(failure.equals("taskOwner"))payload.remove("userId");if(failure.equals("taskArray"))payload=new JSONObject().put("_array",new JSONArray(dataTasks.toString()));}
    else if(p.equals("/dataCenter/api/task/click")){JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));assert body.optString("uid").equals(UID)&&body.optString("mark").equals("PLANTING_FLOWERS_JJEGG_LIST");clickedId=body.optString("taskId");clickWait=TimeUtil.waited;taskClicks++;extraMutation();}
    else if(p.equals("/dataCenter/api/task/action/finish")){String query=url.getQuery();String id=URLDecoder.decode(query.split("taskId=")[1].split("&")[0],"UTF-8");assert id.equals(clickedId)&&query.contains("uid="+UID);for(int i=0;i<dataTasks.length();i++){JSONObject row=dataTasks.optJSONObject(i);if(id.equals(row.optString("id"))){assert TimeUtil.waited-clickWait>=row.optInt("visitSeconds")*1000L:"truncated visit";if(extraEffect)row.put("finished",true);}}taskFinishes++;extraMutation();}
    else if(p.endsWith("/task/record")){JSONObject body=new JSONObject(sent.toString(StandardCharsets.UTF_8));assert body.optString("outUserId").equals("data-user")&&body.optString("positionMark").equals("PLANTING_FLOWERS_JJEGG_LIST")&&Boolean.TRUE.equals(body.opt("isReward"));taskRewards++;if(extraEffect)water+=feeds;extraMutation();}
    else throw new AssertionError(p);
   }
   JSONObject result=new JSONObject().put("code",200).put("obj",payload);
   if(p.endsWith("/getShareSecret")){result.remove("obj");result.put("message",ownSecret);if(failure.equals("shareObj"))result.remove("message");if(failure.equals("shareObj"))result.put("obj",ownSecret);if(failure.equals("shareConflict"))result.put("obj","another-own-secret");if(failure.equals("shareArray"))result.put("obj",new JSONArray());}
   if(p.endsWith("/clickFriendShare/addFriend")){if(failure.equals("assistAck"))result.remove("obj");if(failure.equals("assistMessage")){result.remove("obj");result.put("message",shareState);}if(failure.equals("assistReject"))result.put("code",500);if(failure.equals("assistType"))payload.put("state",1);if(failure.equals("assistConflict"))result.put("message","success");}if(failure.equals("array")&&p.endsWith("RewardConfig"))result.put("obj",payload.optJSONArray("currently"));if(failure.equals("ackEmpty")&&p.endsWith("finish2")||failure.equals("finishAck")&&p.equals("/dataCenter/api/task/action/finish"))result.remove("obj");if(failure.equals("taskArray")&&p.equals("/dataCenter/api/task/list"))result.put("obj",payload.optJSONArray("_array"));if(failure.equals("nested"))result=new JSONObject().put("data",result);if(failure.equals("code"))result.put("code",500).put("message","secret-token secret-code");if(failure.equals("fraction"))result.put("code",200.1);if(failure.equals("conflict"))result.put("data",new JSONObject().put("code",500));
   String raw=huge?"x".repeat(1024*1024+1):result.toString();return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
  }catch(Exception e){throw new AssertionError(e);}}
 }
 static void reset(){opened=closed=signs=queries=logins=rewards=rewardQueries=clouds=bugs=cloudQueries=bugQueries=0;water=100;feeds=rewardAmount=10;inactive=doubleActive=false;configEffect=waterEffect=true;rewardReceived=rewardDrift=false;rewardEffect=true;http=200;failure="";noEffect=switchAccount=cancelAfterSign=huge=drift=received=false;TimeUtil.cancel=false;UserIdMap.uid=UID;Status.flags.clear();Log.messages="";AuthCodeHelper.apps.clear();AuthCodeHelper.code="secret-code\"\\&";ApplicationHook.offline=false;}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Fake(u);}}:null);
  PlantingFlowers task=new PlantingFlowers();ModelFields fields=task.getFields();assert fields.fields.containsKey("plantingFlowersAssist"):"missing explicit assist flow";assert Boolean.FALSE.equals(fields.fields.get("plantingFlowersAssist").value);reset();task.run();assert opened==0;
  fields.fields.get("plantingFlowersSign").value=true;task.run();assert signs==1&&queries==3&&logins==1&&opened==closed;task.run();assert signs==1&&logins==1;assert AuthCodeHelper.apps.equals(Arrays.asList("2021005162668238"));
  for(String mode:new String[]{"code","fraction","status","missing","conflict","header","uid"}){reset();failure=mode;task.run();assert signs==0&&opened==closed;assert !Log.messages.contains("secret-code")&&!Log.messages.contains("secret-token");}
  reset();failure="nested";task.run();assert signs==1&&opened==closed;
  reset();failure="ackEmpty";task.run();assert signs==1&&queries==3&&opened==closed;
  reset();received=true;task.run();assert signs==0;reset();drift=true;task.run();assert signs==0;
  reset();noEffect=true;task.run();task.run();assert signs==1&&logins==1;
  reset();failure="post";task.run();task.run();assert signs==0&&logins==1&&Status.hasFlagToday("flowers::signAttempt");
  reset();http=302;task.run();assert opened==1&&closed==1&&signs==0;
  reset();huge=true;task.run();assert signs==0&&opened==closed;
  reset();AuthCodeHelper.code="";task.run();assert opened==0;
  reset();switchAccount=true;try{task.run();throw new AssertionError("account switch swallowed");}catch(TaskCancelledException expected){}assert signs==1&&opened==closed;
  reset();cancelAfterSign=true;try{task.run();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert signs==1&&opened==closed;
  reset();ApplicationHook.offline=true;task.run();assert opened==0;reset();task.enabled=false;task.run();assert opened==0;
  task.enabled=true;fields.fields.get("plantingFlowersSign").value=false;fields.fields.get("plantingFlowersTomorrowReward").value=true;
  reset();task.run();task.run();assert rewards==1&&rewardQueries==3&&signs==0&&logins==1&&opened==closed;
  reset();rewardEffect=false;task.run();task.run();assert rewards==1&&logins==1;
  for(double amount:new double[]{0,-1,0.5,1000000001}){reset();rewardAmount=amount;task.run();assert rewards==0;}
  reset();rewardReceived=true;task.run();task.run();assert rewards==0&&logins==1;
  reset();rewardDrift=true;task.run();assert rewards==0;
  reset();failure="rewardFlag";task.run();assert rewards==0;
  reset();failure="post";task.run();task.run();assert rewards==0&&logins==1&&Status.hasFlagToday("flowers::tomorrowRewardAttempt");
  reset();cancelAfterSign=true;try{task.run();throw new AssertionError("reward cancel swallowed");}catch(TaskCancelledException expected){}assert rewards==1&&opened==closed;
  fields.fields.get("plantingFlowersTomorrowReward").value=false;
  for(String option:new String[]{"plantingFlowersCloudReward","plantingFlowersBugReward"}){
   fields.fields.get(option).value=true;boolean cloud=option.contains("Cloud");
   reset();task.run();task.run();assert (cloud?clouds:bugs)==1&&logins==1&&Log.messages.contains("\u6c34\u6ef4\u589e\u91cf")&&opened==closed;
   reset();failure="array";task.run();assert (cloud?clouds:bugs)==1;
   reset();failure="nested";task.run();assert (cloud?clouds:bugs)==1;
   reset();inactive=true;task.run();assert clouds+bugs==0;
   reset();doubleActive=true;task.run();assert clouds+bugs==0;
   reset();failure="timedFlag";task.run();assert clouds+bugs==0;
   reset();water=-1;task.run();assert clouds+bugs==0;
   reset();waterEffect=false;task.run();task.run();assert clouds+bugs==1&&logins==1&&!Log.messages.contains("\u6c34\u6ef4\u589e\u91cf");
   reset();configEffect=false;task.run();task.run();assert clouds+bugs==1&&logins==1&&!Log.messages.contains("\u6c34\u6ef4\u589e\u91cf");
   reset();failure="post";task.run();task.run();assert clouds+bugs==0&&logins==1;
   reset();switchAccount=true;try{task.run();throw new AssertionError("timed reward uid switch");}catch(TaskCancelledException expected){}assert clouds+bugs==1&&opened==closed;
   reset();cancelAfterSign=true;try{task.run();throw new AssertionError("timed reward cancel");}catch(TaskCancelledException expected){}assert clouds+bugs==1&&opened==closed;
   fields.fields.get(option).value=false;
  }
  fields.fields.get("plantingFlowersCollectBeans").value=true;
  resetResources(task);task.run();task.run();assert beanCollects==1&&logins==1&&opened==closed;
  resetResources(task);beanEffect=false;task.run();task.run();assert beanCollects==1&&Status.getIntFlagToday("flowers::beansUnconfirmed")==1;
  for(String mode:new String[]{"post","beanConflict"}){resetResources(task);failure=mode;task.run();task.run();assert beanCollects==0;}
  resetResources(task);beanBalance=0.5;task.run();assert beanCollects==0;
  resetResources(task);fields.fields.get("plantingFlowersCollectBeansDailyLimit").value=0;task.run();assert opened==0;
  fields.fields.get("plantingFlowersCollectBeansDailyLimit").value=1;
  resetResources(task);switchAccount=true;try{task.run();throw new AssertionError("beans account switch");}catch(TaskCancelledException expected){}assert beanCollects==1;
  resetResources(task);cancelAfterSign=true;try{task.run();throw new AssertionError("beans cancel");}catch(TaskCancelledException expected){}assert beanCollects==1;
  fields.fields.get("plantingFlowersCollectBeans").value=false;fields.fields.get("plantingFlowersWatering").value=true;
  resetResources(task);task.run();task.run();assert waters==1&&logins==1&&task.children.isEmpty()&&opened==closed;
  resetResources(task);water=99;task.run();assert waters==0;
  resetResources(task);failure="waterMissing";task.run();assert waters==0;
  resetResources(task);wateringEffect=false;task.run();task.run();assert waters==1&&Status.getIntFlagToday("flowers::waterUnconfirmed")==1;
  resetResources(task);failure="post";task.run();task.run();assert waters==0&&Status.getIntFlagToday("flowers::waterUnconfirmed")==1;
  resetResources(task);fields.fields.get("plantingFlowersWateringDailyLimit").value=2;waterActive=1;task.run();task.run();assert waters==0&&task.children.size()==1;
  io.github.aw1y2z.sesame.data.task.ModelTask.ChildModelTask child=task.children.values().iterator().next();task.children.clear();task.busy=true;child.action.run();assert task.children.size()==1&&logins==2;task.busy=false;
  child=task.children.values().iterator().next();task.children.clear();task.generation++;child.action.run();assert task.children.isEmpty()&&logins==2;
  resetResources(task);water=300;task.run();assert waters==1&&task.children.size()==1;child=task.children.values().iterator().next();task.children.clear();waterActive=0;waters=0;child.action.run();assert waters==1&&logins==2&&task.children.isEmpty();
  resetResources(task);waterActive=1;task.run();child=task.children.values().iterator().next();task.children.clear();UserIdMap.uid="2088000000000001";try{child.action.run();throw new AssertionError("scheduled uid switch");}catch(TaskCancelledException expected){}assert waters==0;
  fields.fields.get("plantingFlowersWatering").value=false;
  for(String option:new String[]{"plantingFlowersNewUserReward","plantingFlowersWeeding","plantingFlowersFeedFactory","plantingFlowersDailyDraw","plantingFlowersAcceleratorTasks","plantingFlowersUseAcceleratorCard","plantingFlowersDailyTasks"}){
   assert Boolean.FALSE.equals(fields.fields.get(option).value):"new option default on";fields.fields.get(option).value=true;
   resetExtra(task,option);task.run();int expected=option.equals("plantingFlowersAcceleratorTasks")?3:1;assert extraMutations()==expected:option;assert Log.messages.contains("状态与库存已回查确认")&&opened==closed:option;task.run();assert extraMutations()==expected:option;
   resetExtra(task,option);extraEffect=false;task.run();int failed=extraMutations();assert failed==1:option;task.run();assert extraMutations()==failed:option;assert !Log.messages.contains("状态与库存已回查确认"):option;
   resetExtra(task,option);switchAccount=true;try{task.run();throw new AssertionError("extra uid switch "+option);}catch(TaskCancelledException expectedCancel){}assert extraMutations()==1&&opened==closed:option;
   resetExtra(task,option);cancelAfterSign=true;try{task.run();throw new AssertionError("extra cancelled "+option);}catch(TaskCancelledException expectedCancel){}assert extraMutations()==1&&opened==closed:option;
   resetExtra(task,option);failure="post";task.run();assert extraMutations()==0&&opened==closed:option;
   fields.fields.get(option).value=false;
  }
  fields.fields.get("plantingFlowersFeedFactory").value=true;resetExtra(task,"");factoryRepeat=true;task.run();assert factoryRewards==5&&factoryCount==5;task.run();assert factoryRewards==5;
  for(String invalid:new String[]{"2026-02-30 00:00:00","garbage","2026-10-01T00:00:00Z"}){resetExtra(task,"");factoryTime=invalid;task.run();assert factoryRewards==0;}
  resetExtra(task,"");factoryTime=dateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()+60000));task.run();assert factoryRewards==0;
  resetExtra(task,"");factoryCount=5;task.run();assert factoryRewards==0;
  fields.fields.get("plantingFlowersFeedFactory").value=false;fields.fields.get("plantingFlowersDailyDraw").value=true;
  resetExtra(task,"");fields.fields.get("plantingFlowersDailyDrawLimit").value=0;task.run();assert opened==0;
  fields.fields.get("plantingFlowersDailyDrawLimit").value=5;resetExtra(task,"");drawMax=100;task.run();assert draws==5;task.run();assert draws==5;
  resetExtra(task,"");drawUsed=drawMax;task.run();assert draws==0;resetExtra(task,"");failure="drawType";task.run();assert draws==0;
  fields.fields.get("plantingFlowersDailyDrawLimit").value=1;fields.fields.get("plantingFlowersDailyDraw").value=false;fields.fields.get("plantingFlowersAcceleratorTasks").value=true;
  resetExtra(task,"");cardEligible=false;task.run();assert cardRewards==0;cardEligible=true;task.run();assert cardRewards==3:"qualifies later same day";
  resetExtra(task,"");failure="cardTaskType";task.run();assert cardRewards==0;
  resetExtra(task,"");failure="cardConflict";task.run();assert cardRewards==0;
  fields.fields.get("plantingFlowersAcceleratorTasks").value=false;fields.fields.get("plantingFlowersUseAcceleratorCard").value=true;
  resetExtra(task,"plantingFlowersUseAcceleratorCard");cardInventory=0;task.run();assert cardUses==0;
  resetExtra(task,"plantingFlowersUseAcceleratorCard");waterActive=0;task.run();assert cardUses==0;
  resetExtra(task,"plantingFlowersUseAcceleratorCard");waterEnd=System.currentTimeMillis()-1000;task.run();assert cardUses==0;
  fields.fields.get("plantingFlowersUseAcceleratorCard").value=false;fields.fields.get("plantingFlowersDailyTasks").value=true;
  resetExtra(task,"");failure="finishAck";task.run();assert taskClicks==1&&taskFinishes==1&&taskRewards==1&&TimeUtil.waited>=20000;
  resetExtra(task,"");dataTasks=new JSONArray().put(dataTask("blacklist",17,20)).put(dataTask("tooLong",2,301));task.run();assert extraMutations()==0;
  resetExtra(task,"");dataTasks.put(dataTask("task\"&id",2,20));task.run();assert extraMutations()==0:"duplicate ids";
  resetExtra(task,"");failure="taskOwner";task.run();assert extraMutations()==0;resetExtra(task,"");failure="taskArray";task.run();assert extraMutations()==0;
  resetExtra(task,"");dataTasks.optJSONObject(0).put("alreadyFinish","false");task.run();assert extraMutations()==0;
  resetExtra(task,"");TimeUtil.cancelAt=2000;try{task.run();throw new AssertionError("visit cancel swallowed");}catch(TaskCancelledException expectedCancel){}assert taskClicks==1&&taskFinishes==0&&taskRewards==0;
  resetExtra(task,"");dataTasks=new JSONArray();for(int i=0;i<21;i++)dataTasks.put(dataTask("task"+i,2,1));task.run();assert taskRewards==20;task.run();assert taskRewards==21;
  fields.fields.get("plantingFlowersDailyTasks").value=false;
  assert Boolean.FALSE.equals(fields.fields.get("plantingFlowersAssistPool").value);fields.fields.get("plantingFlowersAssist").value=true;
  fields.fields.get("plantingFlowersManualSecret").value="friend-secret";
  resetAssist(task);task.run();assert assists==1&&shareQueries==1&&assisted.equals(Arrays.asList("friend-secret"));assert Log.messages.contains("\u8bf7\u6c42\u5df2\u63d0\u4ea4")&&Log.messages.contains("\u672a\u786e\u8ba4")&&!Log.messages.contains("\u52a9\u529b\u6210\u529f");String unknown=RuntimeInfo.getInstance().getString("flowers::assistState");assert !new JSONObject(unknown).optString("unknown").isEmpty();int calls=assists;task.run();assert assists==calls&&shareQueries==2:"unknown only queries";assert !Log.messages.contains("friend-secret")&&!Log.messages.contains("own-secret");
  resetAssist(task);task.run();String persisted=RuntimeInfo.getInstance().getString("flowers::assistState");MyUtils.offset=1;task.run();MyUtils.offset=2;fields.fields.get("plantingFlowersManualSecret").value="different-explicit-target";task.run();assert assists==1&&shareQueries==3&&persisted.equals(RuntimeInfo.getInstance().getString("flowers::assistState")):"unknown domain survives midnight and target changes";fields.fields.get("plantingFlowersManualSecret").value="friend-secret";
  resetAssist(task);Status.flags.clear();task.run();Status.flags.clear();task.run();assert assists==1:"unknown survives status restart";
  for(String state:new String[]{"today_have_help","inviter_to_upper_limit","inviter_eq_helper"}){resetAssist(task);shareState=state;task.run();task.run();assert assists==1&&new JSONObject(RuntimeInfo.getInstance().getString("flowers::assistState")).optString("unknown").isEmpty():state;assert !Log.messages.contains("\u52a9\u529b\u6210\u529f");}
  resetAssist(task);shareState="helper_to_limit";task.run();task.run();assert assists==1&&new JSONObject(RuntimeInfo.getInstance().getString("flowers::assistState")).optBoolean("limited");
  for(String mode:new String[]{"assistAck","assistType","assistConflict","post"}){resetAssist(task);failure=mode;task.run();task.run();assert assists==(mode.equals("post")?0:1):mode;assert !new JSONObject(RuntimeInfo.getInstance().getString("flowers::assistState")).optString("unknown").isEmpty():mode;}
  resetAssist(task);failure="assistMessage";shareState="today_have_help";task.run();assert assists==1&&new JSONObject(RuntimeInfo.getInstance().getString("flowers::assistState")).optString("unknown").isEmpty();
  resetAssist(task);failure="assistReject";shareState="today_have_help";task.run();assert assists==1&&new JSONObject(RuntimeInfo.getInstance().getString("flowers::assistState")).optString("unknown").isEmpty();
  for(String mode:new String[]{"shareObj","nested"}){resetAssist(task);failure=mode;task.run();assert assists==1:mode;}
  for(String mode:new String[]{"shareConflict","shareArray"}){resetAssist(task);failure=mode;task.run();assert assists==0:mode;}
  resetAssist(task);RuntimeInfo.getInstance().putVerified("flowers::assistState","garbage");task.run();assert assists==0&&shareQueries==0:"malformed receipt cannot reset protection";
  resetAssist(task);RuntimeInfo.writable=false;task.run();assert assists==0;RuntimeInfo.writable=true;
  resetAssist(task);fields.fields.get("plantingFlowersManualSecret").value=ownSecret;task.run();assert assists==0;
  resetAssist(task);fields.fields.get("plantingFlowersManualSecret").value="a\nb\nc\nd";shareState="today_have_help";task.run();task.run();assert assists==3&&assisted.equals(Arrays.asList("a","b","c"));
  resetAssist(task);fields.fields.get("plantingFlowersAssistDailyLimit").value=1;task.run();assert assists==1;fields.fields.get("plantingFlowersAssistDailyLimit").value=3;
  resetAssist(task);fields.fields.get("plantingFlowersManualSecret").value="";fields.fields.get("plantingFlowersAssistTargets").value="[{\"userId\":\""+UID+"\",\"shareId\":\"self\"},{\"userId\":\"2088000000000001\",\"shareSecret\":\"friend-code\"}]";shareState="today_have_help";task.run();assert assists==1&&assisted.equals(Arrays.asList("friend-code"));
  resetAssist(task);fields.fields.get("plantingFlowersAssistTargets").value="[{\"userId\":\"2088000000000001\",\"shareId\":\"a\"},{\"userId\":\"2088000000000001\",\"shareId\":\"b\"}]";task.run();assert assists==0;
  fields.fields.get("plantingFlowersAssistTargets").value="";fields.fields.get("plantingFlowersManualSecret").value="friend-secret";
  for(boolean uid:new boolean[]{false,true}){resetAssist(task);switchAccount=uid;cancelAfterSign=!uid;try{task.run();throw new AssertionError("assist cancellation swallowed");}catch(TaskCancelledException expectedCancel){}assert assists==1;UserIdMap.uid=UID;TimeUtil.cancel=false;String state=RuntimeInfo.getInstance().getString("flowers::assistState");assert !new JSONObject(state).optString("unknown").isEmpty();}
  fields.fields.get("plantingFlowersAssistPool").value=true;fields.fields.get("plantingFlowersAssistListUrl").value="https://pool.example/list/";
  fields.fields.get("plantingFlowersManualSecret").value="";resetAssist(task);poolResponse="[{\"userId\":\"2088000000000001\",\"shareId\":\"pool-secret\"}]";task.run();assert poolGets==1&&poolHeaders&&assists==1&&assisted.equals(Arrays.asList("pool-secret"));assert !Log.messages.contains("pool-secret");
  fields.fields.get("plantingFlowersAssistPool").value=false;resetAssist(task);task.run();assert poolGets==0&&assists==0;
  fields.fields.get("plantingFlowersAssistPool").value=true;for(String invalid:new String[]{"http://pool.example/list/","https://user:password@pool.example/list/","https://pool.example/list/?token=bad","https://pool.example/list/#fragment"}){fields.fields.get("plantingFlowersAssistListUrl").value=invalid;resetAssist(task);task.run();assert poolGets==0&&assists==0:invalid;}
  fields.fields.get("plantingFlowersAssistListUrl").value="https://pool.example/list/";for(String response:new String[]{"garbage","{}","[]","[{\"shareId\":123}]"}){resetAssist(task);poolResponse=response;task.run();assert assists==0:response;}
  resetAssist(task);poolResponse="x".repeat(65537);task.run();assert assists==0;
  fields.fields.get("plantingFlowersAssistPool").value=false;fields.fields.get("plantingFlowersAssist").value=false;
  assert fields.fields.containsKey("plantingFlowersAssistUpload"):"missing independently authorized publication";assert Boolean.FALSE.equals(fields.fields.get("plantingFlowersAssistUpload").value)&&Boolean.FALSE.equals(fields.fields.get("plantingFlowersAssistRecordUpload").value);
  fields.fields.get("plantingFlowersAssistPushUrl").value="https://pool.example/upload/";fields.fields.get("plantingFlowersAssistUpload").value=true;resetAssist(task);task.run();task.run();assert ownUploads==1&&assists==0&&poolGets==0&&shareQueries==1&&poolHeaders;assert !Log.messages.contains(ownSecret)&&!Log.messages.contains("friend-secret");
  resetAssist(task);RuntimeInfo.writable=false;task.run();assert ownUploads==0;RuntimeInfo.writable=true;
  fields.fields.get("plantingFlowersAssistPushUrl").value="http://pool.example/upload/";resetAssist(task);task.run();assert ownUploads==0;fields.fields.get("plantingFlowersAssistPushUrl").value="https://pool.example/upload/";
  resetAssist(task);task.run();MyUtils.offset=1;task.run();assert ownUploads==2:"explicit publication daily cap";
  for(boolean uid:new boolean[]{false,true}){resetAssist(task);externalSwitch=uid;externalCancel=!uid;try{task.run();throw new AssertionError("publication cancellation swallowed");}catch(TaskCancelledException expectedCancel){}assert ownUploads==1&&opened==closed;UserIdMap.uid=UID;TimeUtil.cancel=false;externalSwitch=externalCancel=false;task.run();assert ownUploads==1:"publication receipt consumed before cancellation";}
  fields.fields.get("plantingFlowersAssistUpload").value=false;fields.fields.get("plantingFlowersAssistRecordUpload").value=true;fields.fields.get("plantingFlowersRecordPushUrl").value="https://pool.example/records/";fields.fields.get("plantingFlowersAssist").value=true;fields.fields.get("plantingFlowersManualSecret").value="friend-secret";resetAssist(task);task.run();task.run();assert assists==1&&recordUploads==1&&new JSONArray(recordBody).optJSONObject(0).optInt("state")==0&&poolHeaders;assert !Log.messages.contains("friend-secret")&&!Log.messages.contains(ownSecret);MyUtils.offset=1;task.run();assert recordUploads==1&&assists==1:"no new record during unknown freeze";
  for(boolean uid:new boolean[]{false,true}){resetAssist(task);externalSwitch=uid;externalCancel=!uid;try{task.run();throw new AssertionError("record publication cancellation swallowed");}catch(TaskCancelledException expectedCancel){}assert assists==1&&recordUploads==1&&opened==closed;UserIdMap.uid=UID;TimeUtil.cancel=false;externalSwitch=externalCancel=false;task.run();assert assists==1&&recordUploads==1:"frozen assist cannot produce another record";}
  fields.fields.get("plantingFlowersAssistRecordUpload").value=false;fields.fields.get("plantingFlowersAssist").value=false;

  System.out.println("PASS default off, source signature+GMT8, existing sign/rewards/beans/watering scheduler, new-user/weeding/factory/draw/cards/dataCenter qualification+inventory+day guards, full visit duration, explicit manual/UID assist targets, independent credential-free HTTPS pool GET, durable cross-day unknown freeze/3-per-day attempt cap, separately authorized own-secret/record uploads with submitted-only state0, source terminal states, dynamic auth, cancellation/account switch and HTTP bounds");
 }
 static void resetAssist(PlantingFlowers task){resetExtra(task,"");assists=shareQueries=poolGets=ownUploads=recordUploads=0;recordBody="";shareState="success";ownSecret="own-secret";poolResponse="[]";poolHeaders=externalSwitch=externalCancel=false;assisted.clear();RuntimeInfo.users.clear();RuntimeInfo.writable=true;MyUtils.offset=0;}
 static void resetResources(PlantingFlowers task){reset();Status.counts.clear();task.children.clear();task.busy=false;beanCollects=waters=waterActive=0;beanEffect=wateringEffect=true;beanBalance=50;waterEnd=System.currentTimeMillis()+120000;}
 static int extraMutations(){return newcomers+weeds+factoryRewards+draws+cardRewards+cardUses+taskClicks;}
 static void resetExtra(PlantingFlowers task,String option){resetResources(task);newcomers=weeds=factoryRewards=draws=cardRewards=cardUses=taskClicks=taskFinishes=taskRewards=0;cardInventory=2;factoryCount=drawUsed=0;drawMax=2;extraEffect=cardEligible=true;factoryRepeat=false;factoryTime=dateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(System.currentTimeMillis()-1000));rewardedCards.clear();dataTasks=new JSONArray().put(dataTask("task\"&id",2,20));TimeUtil.waited=TimeUtil.cancelAt=0;clickedId=null;clickWait=0;if(option.equals("plantingFlowersUseAcceleratorCard"))waterActive=1;}
}
'''
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-flowers-') as tmp:
 files=[]
 for name,body in stubs.items():
  full=pkg+'.'+name;path=Path(tmp)/(full.replace('.','/')+'.java');path.parent.mkdir(parents=True,exist_ok=True);path.write_text('package '+full.rsplit('.',1)[0]+';\n'+body,encoding='utf-8');files.append(str(path))
 path=Path(tmp)/'FlowersCheck.java';path.write_text(test,encoding='utf-8');files.extend([str(path),str(task),str(source/pkg.replace('.','/')/'util/TaskCancelledException.java')])
 env=dict(os.environ,JAVA_TOOL_OPTIONS='-Xms16m -Xmx192m')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp]+files,check=True,env=env)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'FlowersCheck'],check=True,env=env)
