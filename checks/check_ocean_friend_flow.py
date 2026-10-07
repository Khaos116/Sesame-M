"""Compile recommended sailing and gift methods; assert list/budget/reward boundaries."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
ocean="model/task/antOcean/AntOcean.java"
rpc="model/task/antOcean/AntOceanRpcCall.java"
src=(SOURCE/ocean).read_text(encoding="utf-8")
assert 'new BooleanModelField("recommendedSailing"' in src,"Missing recommended sailing entry"
code=r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class OceanFriendCheck {
 static final String TAG="check",VERSION="20241203";
 static class Field {boolean on;boolean getValue(){return on;}} Field recommendedSailing=new Field(),giveFriendPiece=new Field();
 static class Type {int n=1;int getValue(){return n;}} Type cleanOceanType=new Type();
 static class ListField {Set<String> ids=new HashSet<>();Set<String> getValue(){return ids;}} ListField cleanOceanList=new ListField();
 static class CleanOceanType {static final int NONE=0,CLEAN=1,NOT_CLEAN=2;}
 static class UserIdMap {static String getCurrentUid(){return "self";}static String getMaskName(String s){return s;}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class Log {static void other(String s){}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}static int getIntFlagToday(String k){return counts.getOrDefault(k,0);}static void setIntFlagToday(String k,int n){counts.put(k,n);}}
 int rewards; private void checkReward(JSONArray rows){rewards+=rows.length();}
 static class ApplicationHook {
  static List<JSONObject> calls=new ArrayList<>();static Queue<String> friends=new ArrayDeque<>();static boolean fail=false,limit=false;static String giftCode="SUCCESS";
  static String requestString(String name,String args){JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",name);calls.add(body);JSONObject j=new JSONObject().put("resultCode",fail?"FAIL":"SUCCESS");
   if(name.endsWith("sailingAway"))return j.put("friendId",friends.isEmpty()?"":friends.remove()).toString();
   if(name.endsWith("queryFriendPage"))return j.toString();
   if(name.endsWith("cleanFriendOcean"))return limit?"{\"resultCode\":\"LIMIT\",\"resultDesc\":\"\u6b21\u6570\u4e0a\u9650\"}":j.put("cleanRewardVOS",new JSONArray().put(new JSONObject())).toString();
   if(name.endsWith("giveFriendPiece"))return new JSONObject().put("resultCode",giftCode).put("normalRewardVOS",new JSONArray().put(new JSONObject())).toString();
   throw new AssertionError(name);
  }
 }
 static class AntOceanRpcCall {static String getUniqueId(){return "unique";}@@RPC@@}
 @@METHODS@@
 static long calls(String n){return ApplicationHook.calls.stream().filter(j->j.optString("rpc").endsWith(n)).count();}
 static void reset(OceanFriendCheck f){ApplicationHook.calls.clear();ApplicationHook.friends.clear();ApplicationHook.fail=false;ApplicationHook.limit=false;ApplicationHook.giftCode="SUCCESS";Status.flags.clear();Status.counts.clear();TimeUtil.cancel=false;f.recommendedSailing.on=true;f.giveFriendPiece.on=false;f.cleanOceanType.n=1;f.cleanOceanList.ids.clear();f.rewards=0;}
 public static void main(String[] args) throws Exception {
  OceanFriendCheck f=new OceanFriendCheck();reset(f);f.recommendedSailing.on=false;assert !f.helpCleanRecommendedFriend()&&ApplicationHook.calls.isEmpty();
  reset(f);f.cleanOceanType.n=0;assert !f.helpCleanRecommendedFriend()&&ApplicationHook.calls.isEmpty();
  reset(f);ApplicationHook.friends.addAll(Arrays.asList("self","blocked","allowed"));f.cleanOceanList.ids.add("allowed");assert f.helpCleanRecommendedFriend();assert calls("cleanFriendOcean")==1&&calls("giveFriendPiece")==0;
  JSONObject page=ApplicationHook.calls.stream().filter(j->j.optString("rpc").endsWith("queryFriendPage")).findFirst().orElseThrow();assert page.optString("fromAct").equals("SAIL_AWAY")&&page.optString("currentUserId").equals("self");
  reset(f);f.cleanOceanType.n=2;f.cleanOceanList.ids.add("blocked");ApplicationHook.friends.addAll(Arrays.asList("blocked","allowed"));assert f.helpCleanRecommendedFriend();assert calls("cleanFriendOcean")==1;
  reset(f);f.cleanOceanList.ids.add("allowed");ApplicationHook.friends.addAll(Arrays.asList("allowed","allowed"));ApplicationHook.fail=true;assert !f.helpCleanRecommendedFriend();assert calls("cleanFriendOcean")==0;
  reset(f);f.cleanOceanList.ids.add("allowed");ApplicationHook.friends.addAll(Arrays.asList("allowed","allowed"));ApplicationHook.limit=true;assert !f.helpCleanRecommendedFriend();assert Status.hasFlagToday("Ocean::HELP_CLEAN_ALL_FRIEND_LIMIT");f.helpCleanRecommendedFriend();assert calls("sailingAway")==1;
  reset(f);for(int i=0;i<30;i++)ApplicationHook.friends.add("excluded"+i);assert !f.helpCleanRecommendedFriend();assert calls("sailingAway")==20;f.helpCleanRecommendedFriend();assert calls("sailingAway")==20;
  reset(f);f.giveFriendPiece.on=true;f.giveOceanFriendPiece("friend");f.giveOceanFriendPiece("friend");assert calls("giveFriendPiece")==1&&f.rewards==1;
  reset(f);f.giveFriendPiece.on=true;ApplicationHook.giftCode="PIECE_HAVE_GAVE";f.giveOceanFriendPiece("friend");assert f.rewards==0;
  reset(f);AntOceanRpcCall.cleanFriendOcean("friend\"\\");assert ApplicationHook.calls.get(0).optString("cleanedUserId").equals("friend\"\\");
  reset(f);TimeUtil.cancel=true;try{f.helpCleanRecommendedFriend();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.calls.isEmpty();
  System.out.println("PASS default-off, list modes/self/exclusion, recommended source/identity, 20/day bound, service limits, gift reward/repeat/duplicate and cancellation");
 }
}
'''
code=code.replace("@@METHODS@@","\n".join(method(ocean,s) for s in ("private boolean helpCleanRecommendedFriend(","private void giveOceanFriendPiece(","private Boolean cleanFriendOcean(String userId, boolean recommended)")))
code=code.replace("@@RPC@@","\n".join(method(rpc,s) for s in ("public static String sailingAway(","public static String giveFriendPiece(","public static String queryFriendPage(String userId, boolean recommended)","public static String cleanFriendOcean("))).replace("io.github.aw1y2z.sesame.util.idMap.UserIdMap.getCurrentUid()", "UserIdMap.getCurrentUid()")
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-ocean-friend-") as tmp:
 java=Path(tmp)/"OceanFriendCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"OceanFriendCheck"],check=True)
