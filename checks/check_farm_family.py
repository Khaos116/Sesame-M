"""Replay real family selection and single-attempt RPCs without an account or device."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE
farm = 'model/task/antFarm/AntFarm.java'
rpc = 'model/task/antFarm/AntFarmRpcCall.java'
source = (SOURCE / farm).read_text(encoding='utf-8')
assert '"familyAssignStrategy"' in source and '"familyShareMode"' in source
assert 'catch (TaskCancelledException cancelled)' in method(farm, 'private void family(')
code = r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class FarmFamilyCheck {
 static String TAG="check",FLAG_FAMILY_SHARE_FAIL_COUNT="fails";static int MAX_FAMILY_SHARE_ATTEMPT=3;
 static class System {static long now=java.time.Instant.parse("2026-10-07T12:00:00Z").toEpochMilli();static long currentTimeMillis(){return now;}}
 static class Int {int value;Int(int v){value=v;}Integer getValue(){return value;}}
 static class SelectModelField {Set<String> values=new HashSet<>();Set<String> getValue(){return values;}}
 Int familyAssignStrategy=new Int(0),familyShareMode=new Int(1);SelectModelField familyShareList=new SelectModelField();
 static class UserIdMap {static String uid="self";static String getCurrentUid(){return uid;}static String getShowName(String id){return id;}}
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String raw){try{return new JSONObject(raw);}catch(Exception e){return new JSONObject();}}}
 static class RandomUtil {static int nextInt(int a,int b){assert b>a;return a;}}
 static class TimeUtil {static boolean cancel;static void sleep(long ms){if(cancel)throw new TaskCancelledException();}}
 static class Status {static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String f){return flags.contains(f);}static void flagToday(String f){flags.add(f);}static int getIntFlagToday(String f){return counts.getOrDefault(f,0);}static void setIntFlagToday(String f,int n){counts.put(f,n);}}
 static class Log {static int successes,fallback;static void record(String s){if(s.contains("随机策略"))fallback++;}static void farm(String s){successes++;}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class MessageUtil {static boolean checkMemo(String t,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class AlipayUser {String id;AlipayUser(String id){this.id=id;}String getId(){return id;}static List<AlipayUser> users;static List<AlipayUser> getList(){return users;}}
 static JSONObject row(String id,int today,int total,int donated){return new JSONObject().put("userId",id).put("todayIntimateNum",today).put("totalIntimateNum",total).put("userDonateCount",donated);}
 static JSONObject state(){return new JSONObject().put("memo","SUCCESS").put("familyMemberInfoList",new JSONArray().put(row("self",0,0,0)).put(row("a",2,1,1)).put(row("b",1,5,1)).put(row("c",1,4,2)));}
 static JSONObject config(){return new JSONObject().put("assignConfigList",new JSONArray().put(new JSONObject().put("assignAction","action\"\\").put("assignDesc","chosen")));}
 static class ApplicationHook {
  static JSONObject contribution;static List<String> assigned=new ArrayList<>(),invited=new ArrayList<>();static boolean fail,switchUid,rollover,cancelAfter;
  static String requestString(String name,String raw){assert name.endsWith("familyTreadMill");JSONObject p=new JSONArray(raw).optJSONObject(0);assert p.optBoolean("openSportsPolicy")&&p.optString("timeZoneId").equals("Asia/Shanghai")&&p.optString("requestType").equals("NORMAL");return contribution.toString();}
  static String requestString(String name,String raw,int tries,int pause){assert tries==1&&pause==0;JSONObject p=new JSONArray(raw).optJSONObject(0);
   if(name.endsWith("assignFamilyMember")){assert p.optString("assignAction").equals("action\"\\")&&Status.hasFlagToday("antFarm::familyAssignAttempt");assigned.add(p.optString("beAssignUser"));}
   else {assert name.endsWith("batchInviteP2P");String id=p.optJSONArray("inviteP2PVOList").optJSONObject(0).optString("beInvitedUserId");assert Status.hasFlagToday("antFarm::familyInviteAttempt::"+id);invited.add(id);assert p.optJSONObject("invitedBizExtendInfo").optString("familyId").equals("group\"\\")&&p.optJSONObject("linkParams").optString("inviteUserId").equals("self");}
   if(switchUid)UserIdMap.uid="other";if(rollover)System.now+=86400000;if(cancelAfter)TimeUtil.cancel=true;
   return fail?"unknown":new JSONObject().put("memo","SUCCESS").put("resultCode","SUCCESS").toString();
  }
 }
 static class AntFarmRpcCall {@@RPC@@}
 @@METHODS@@
 static void reset(FarmFamilyCheck f){Status.flags.clear();Status.counts.clear();ApplicationHook.assigned.clear();ApplicationHook.invited.clear();ApplicationHook.contribution=state();ApplicationHook.fail=ApplicationHook.switchUid=ApplicationHook.rollover=ApplicationHook.cancelAfter=TimeUtil.cancel=false;UserIdMap.uid="self";System.now=java.time.Instant.parse("2026-10-07T12:00:00Z").toEpochMilli();f.familyAssignStrategy.value=0;f.familyShareMode.value=1;f.familyShareList.values.clear();Log.successes=Log.fallback=0;AlipayUser.users=new ArrayList<>();for(String id:List.of("self","family","a","b","b","c","blocked"))AlipayUser.users.add(new AlipayUser(id));}
 public static void main(String[] args){TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));FarmFamilyCheck f=new FarmFamilyCheck();reset(f);List<String> members=new ArrayList<>(List.of("self","a","b","c"));
  assert lowestFamilyContributor(state(),List.of("a","b","c"),"self").equals("c");
  JSONObject tie=state();JSONArray rows=tie.optJSONArray("familyMemberInfoList");rows.optJSONObject(2).put("totalIntimateNum",4).put("userDonateCount",1);assert lowestFamilyContributor(tie,List.of("a","b","c"),"self").equals("b");rows.optJSONObject(3).put("userDonateCount",1);assert lowestFamilyContributor(tie,List.of("a","b","c"),"self").equals("b");
  for(Object invalid:List.of("1",-1,1.5,JSONObject.NULL)){JSONObject bad=state();bad.optJSONArray("familyMemberInfoList").optJSONObject(1).put("todayIntimateNum",invalid);assert lowestFamilyContributor(bad,List.of("a","b","c"),"self")==null;}
  JSONObject duplicate=state();duplicate.optJSONArray("familyMemberInfoList").put(row("a",1,1,1));assert lowestFamilyContributor(duplicate,List.of("a","b","c"),"self")==null;
  assert lowestFamilyContributor(state(),List.of("a","missing"),"self")==null;
  f.familyAssignStrategy.value=1;f.assignFamilyMember(config(),members);assert ApplicationHook.assigned.equals(List.of("c"))&&members.equals(List.of("self","a","b","c"));f.assignFamilyMember(config(),members);assert ApplicationHook.assigned.size()==1;
  reset(f);f.assignFamilyMember(config(),members);assert ApplicationHook.assigned.equals(List.of("a"));
  reset(f);f.familyAssignStrategy.value=1;ApplicationHook.contribution=new JSONObject();f.assignFamilyMember(config(),members);assert ApplicationHook.assigned.equals(List.of("a"))&&Log.fallback==1;
  reset(f);ApplicationHook.fail=true;f.assignFamilyMember(config(),members);f.assignFamilyMember(config(),members);assert ApplicationHook.assigned.size()==1;
  SelectModelField blocked=new SelectModelField();blocked.values.add("blocked");List<String> family=List.of("self","family");
  reset(f);f.familyShareMode.value=0;f.familyShareToFriends("group\"\\",family,blocked);assert ApplicationHook.invited.isEmpty();
  reset(f);f.familyShareMode.value=0;f.familyShareList.values.addAll(List.of("a","b","self","family","blocked"));f.familyShareToFriends("group\"\\",family,blocked);assert new HashSet<>(ApplicationHook.invited).equals(Set.of("a","b"))&&ApplicationHook.invited.size()==2;f.familyShareToFriends("group\"\\",family,blocked);assert ApplicationHook.invited.size()==2;
  reset(f);f.familyShareList.values.addAll(List.of("a","b"));f.familyShareToFriends("group\"\\",family,blocked);assert ApplicationHook.invited.equals(List.of("c"));
  reset(f);f.familyShareToFriends("group\"\\",family,blocked);assert ApplicationHook.invited.size()==2&&new HashSet<>(ApplicationHook.invited).size()==2&&Set.of("a","b","c").containsAll(ApplicationHook.invited);
  reset(f);f.familyShareToFriends("group\"\\",List.of("self","1","2","3","4","5"),blocked);assert ApplicationHook.invited.isEmpty();
  reset(f);f.familyShareMode.value=0;f.familyShareList.values.addAll(List.of("a","b"));ApplicationHook.fail=true;f.familyShareToFriends("group\"\\",family,blocked);f.familyShareToFriends("group\"\\",family,blocked);assert ApplicationHook.invited.size()==2;
  for(int change:new int[]{1,2,3}){reset(f);ApplicationHook.switchUid=change==1;ApplicationHook.rollover=change==2;ApplicationHook.cancelAfter=change==3;try{f.familyShareToFriends("group\"\\",family,blocked);assert change!=3;}catch(TaskCancelledException e){assert change==3;}assert ApplicationHook.invited.size()==1&&Log.successes==0;}
  reset(f);TimeUtil.cancel=true;try{f.assignFamilyMember(config(),members);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert ApplicationHook.assigned.isEmpty();
  java.lang.System.out.println("PASS family contribution ordering/fallback, preserved candidates, selected/excluded friends, caps, single attempts, escaped RPC JSON and UID/day/cancellation guards");
 }
}
'''
code = code.replace('@@RPC@@', '\n'.join(method(rpc,s) for s in ('public static String familyTreadMill(', 'public static String assignFamilyMember(', 'public static String batchInviteP2P(')))
code = code.replace('@@METHODS@@', '\n'.join(method(farm,s) for s in ('private static int rankingInt(', 'private static String rankingDay(', 'private static String lowestFamilyContributor(', 'private void assignFamilyMember(', 'private void familyShareToFriends(')))
cache = Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-family-') as tmp:
    path=Path(tmp)/'FarmFamilyCheck.java';path.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(path),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'FarmFamilyCheck'],check=True)
