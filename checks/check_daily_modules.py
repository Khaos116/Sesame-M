"""Real daily entry methods: no repeat RPC after success, manual refresh, failures retry."""
from pathlib import Path
import os
import sys
import subprocess
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method
from daily_task_fixture import with_daily_task

code = r'''
import org.json.*;
import java.util.*;
public class DailyModulesCheck {
 static final String TAG="test";
 static String response="{}"; static int calls;
 static String rpc(){calls++;return response;}
 static class Status {
  static Set<String> flags=new HashSet<>(); static boolean hasFlagToday(String k){return flags.contains(k);}
  static void flagToday(String k){flags.add(k);} static void clearFlag(String k){flags.remove(k);}
 }
 static class Log {static void record(String s){}static void forest(String s){}static void farm(String s){}static void other(String s){}static void i(String s){}static void error(String s){}
  static void err(String a,String b,Throwable t){if(t instanceof AssertionError)throw (AssertionError)t;}
  static void printStackTrace(String a,Throwable t){if(t instanceof AssertionError)throw (AssertionError)t;}}
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static void sleep(long n){}}
 static class MessageUtil {static boolean checkResultCode(String a,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}
  static boolean checkSuccess(String a,JSONObject j){return Boolean.TRUE.equals(j.opt("success"));}
  static boolean checkMemo(String a,JSONObject j){return "SUCCESS".equals(j.optString("memo"));}}
 static class Statistics {enum DataType{COLLECTED}static void addData(DataType t,int n){}}
 static class UserIdMap {static String getCurrentUid(){return "A";}}
 static class AntForestRpcCall {static String queryCommonSign(String k){return rpc();}static String antiepSign(String a,String b,String c){return rpc();}static String vitalitySign(){return rpc();}}
 static class AntMemberRpcCall {static String queryPointBallList(){return rpc();}static String alchemyQueryCheckIn(String s){return rpc();}static String zmCheckInCompleteTask(String a,String b){return rpc();}}
 static class AntOceanRpcCall {static String getQuestion(){return rpc();}static String submitAnswer(String a,String b){return rpc();}}
 static class AntStallRpcCall {static String signToday(){return rpc();}}
 static class AntFarmRpcCall {static String familyReceiveFarmTaskAward(String id){return rpc();}}
 static class OmegakoiTownRpcCall {static String getSignInStatus(){return rpc();}static String signIn(){return rpc();}}
 static class AnswerAI {static String getAnswer(String s,List<String> options){return options.get(0);}}
 static class JsonUtil {static List<String> jsonArrayToList(JSONArray a){return List.of("A");}}
 enum RewardType {gold;String rewardName(){return "gold";}}
 static class Forest {@@FOREST@@}
 static class Member {static boolean dailySignIn(){rpc();return true;}@@MEMBER@@}
 static class Ocean {@@OCEAN@@}
 static class Stall {@@STALL@@}
 static class Farm {@@FARM@@}
 static class Town {@@TOWN@@}
 static void reset(String json){response=json;calls=0;Status.flags.clear();}
 static void replay(Runnable action){action.run();int before=calls;assert before>0;action.run();assert calls==before:"auto repeated RPC";
  DailyTask.manual(()->{action.run();return null;});assert calls>before:"manual did not refresh";}
 public static void main(String[] args){
  reset("{resultCode:'SUCCESS',forestSignVO:{currentSignKey:'today',signRecords:[{signKey:'today',signed:true}]}}");
  replay(()->new Forest().queryCommonSign());assert !Status.hasFlagToday("forest::vantiepSign"):"7-day sign polluted revival sign";
  reset("{resultCode:'SUCCESS'}");replay(()->new Forest().vitalitySign());
  reset("{success:true,data:{signInBallModule:{signInStatus:true}}}");replay(Member::checkAndDoSignIn);
  reset("{success:true,data:{signInBallModule:{}}}");Member.checkAndDoSignIn();assert calls==1&&Status.flags.isEmpty():"missing sign state triggered a write";
  reset("{resultCode:'SUCCESS',answered:true}");replay(()->Ocean.answerQuestion());
  reset("{resultCode:'SUCCESS',signRewardModelList:[]}");replay(()->new Stall().signToday());
  reset("{memo:'SUCCESS'}");replay(()->new Farm().familySign());
  reset("{success:true,result:{signed:true}}");replay(()->new Town().getSignInStatus());
  reset("{success:true,result:{}}");new Town().getSignInStatus();assert calls==1&&Status.flags.isEmpty():"missing town status triggered sign";
  for(Runnable action:List.<Runnable>of(()->new Forest().queryCommonSign(),()->new Forest().vitalitySign(),Member::checkAndDoSignIn,
   ()->Ocean.answerQuestion(),()->new Stall().signToday(),()->new Farm().familySign(),()->new Town().getSignInStatus(),()->new Member().CheckInTaskRpcManager())){
   reset("{success:false,resultCode:'FAIL',memo:'FAIL'}");action.run();int before=calls;action.run();assert calls>before&&Status.flags.isEmpty():"failure cached as success";
  }
  reset("{resultCode:'SUCCESS',data:{currentDateCheckInTaskVO:{status:'CAN_COMPLETE',checkInDate:'20261010'}}}");
  replay(()->new Member().CheckInTaskRpcManager());
  reset("{resultCode:'SUCCESS',data:{}}");new Member().CheckInTaskRpcManager();assert Status.flags.isEmpty():"invalid sign response cached";
  System.out.println("PASS daily production entries: forest/member/ocean/stall/family/town, second-auto zero RPC, manual refresh, failed/missing status not completed");
 }
}
'''
groups = {
    'FOREST': ('antForest/AntForestV2.java', ['private void queryCommonSign()', 'private void vitalitySign()']),
    'MEMBER': ('antMember/AntMember.java', ['public static void checkAndDoSignIn()', 'private void CheckInTaskRpcManager()']),
    'OCEAN': ('antOcean/AntOcean.java', ['private static Boolean answerQuestion()']),
    'STALL': ('antStall/AntStall.java', ['private void signToday()']),
    'FARM': ('antFarm/AntFarm.java', ['private void familySign()', 'private void familyReceiveFarmTaskAward(']),
    'TOWN': ('omegakoiTown/OmegakoiTown.java', ['private void getSignInStatus()']),
}
for key, (path, signatures) in groups.items():
    code = code.replace('@@'+key+'@@', '\n'.join(method('model/task/'+path, s) for s in signatures))
code = with_daily_task(code)
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar = sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-daily-modules-') as tmp:
    java = Path(tmp)/'DailyModulesCheck.java'
    java.write_text(code, encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java)],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'DailyModulesCheck'],check=True)
