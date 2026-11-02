"""Replay upstream response guards on production methods, without device or network."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method

jar = next((Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) /
            'caches/modules-2/files-2.1/org.json/json').glob('*/*/json-*.jar'))
code = r'''
import java.util.*;import org.json.*;
public class UpstreamOct31 {
 static final String TAG="test";
 static String reply;static int writes,aiCalls;
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}static boolean checkMemo(String t,JSONObject j){return checkResultCode(t,j);}static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}}
 static class Log {static void record(String s){}static void forest(String s){}static void farm(String s){}static void other(String s){}static void i(String s){}static void i(String t,String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class TimeUtil {static boolean isLessThanNowOfDays(long n){return false;}static void sleep(long n){}}
 static class Status {static boolean hasFlagToday(String s){return false;}static void flagToday(String s){}}
 static class TaskCancelledException extends RuntimeException {}
 static class Field<T> {T value;Field(T v){value=v;}T getValue(){return value;}}
 static class UserIdMap {static String getMaskName(String s){return s;}}
 static class StringUtil {static boolean isEmpty(String s){return s==null||s.isEmpty();}static String getSubString(String s,String a,String b){return "activity";}}
 static class JsonUtil {static List<String> jsonArrayToList(JSONArray a){return List.of("a");}}
 static class AnswerAI {static String getAnswer(String title,List<String> options){aiCalls++;return "a";}}
 static class ReadingDadaRpcCall {static String getQuestion(String id){return reply;}static String submitAnswer(String a,String b,String id,String answer){assert !id.isEmpty();writes++;return "{resultCode:200}";}}
 static class AntFarmRpcCall {static String queryFoodMaterialPack(){return reply;}static String collectDailyLimitedFoodMaterial(int n){assert n>0;writes++;return "{resultCode:SUCCESS}";}}
 static class AntDodoRpcCall {static String queryFriend(){return reply;}static String collect(String uid){assert !uid.isEmpty()&&uid.equals(uid.trim());writes++;return "{resultCode:SUCCESS}";}}
 static class AntSportsRpcCall {
  static boolean replay,stalled;static int rewardStage,rewardReads,rewardWrites;
  static String donateExchangeRecord(){return reply;}static String userTaskGroupQuery(String s){return reply;}
  static String userTaskComplete(String biz,String id){assert !biz.isEmpty()&&!id.isEmpty();writes++;return "{success:true}";}
  static String userTaskRightsReceive(String id,String user){assert !id.isEmpty()&&!user.isEmpty();writes++;return "{success:true}";}
  static String neverlandtaskReceive(String s){if(replay){rewardWrites++;if(!stalled&&reply.contains("true"))rewardStage++;}return reply;}
  static String queryTaskCenter(){rewardReads++;JSONArray tasks=new JSONArray();if(rewardStage<2)tasks.put(new JSONObject().put("id",rewardStage).put("taskStatus","TO_RECEIVE"));return new JSONObject().put("success",true).put("data",new JSONObject().put("taskCenterTaskVOS",tasks)).toString();}
 }
 static class Sports {
 @@DONATE@@
 @@GROUP@@
 @@RIGHTS@@
 static ArrayList<String> parseRewards(JSONArray a){return new ArrayList<>();}
 @@REWARD@@
 static boolean completeTask(JSONObject t){throw new AssertionError("unexpected completion");}
 @@CENTER@@
 }
 static class Dodo {
  interface CollectToFriendType {int COLLECT=0;}
  Field<Integer> collectToFriendType=new Field<>(1);Field<Set<String>> collectToFriendList=new Field<>(Set.of());
  String getAnimalInfo(JSONObject j){return "animal";}
  @@FRIEND@@
 }
 static class Farm {@@FOOD@@}
 static class Reading {@@QUESTION@@}
 public static void main(String[] args){
  Sports s=new Sports();Farm f=new Farm();
  reply="{resultCode:SUCCESS}";assert !s.canDonateWalkExchangeToday():"missing donation list must not authorize a write";
  reply="{resultCode:SUCCESS,userExchangeRecords:[]}";assert s.canDonateWalkExchangeToday();
  for(String info:new String[]{"{}","{bizType:BROWSE}","{taskId:id}"}){
   reply="{success:true,group:{userTaskList:[{status:TODO,taskInfo:"+info+"}]}}";writes=0;s.userTaskGroupQuery("group");assert writes==0;
  }
  reply="{success:true,group:{userTaskList:[{status:TODO,taskInfo:{bizType:BROWSE,taskId:id}}]}}";s.userTaskGroupQuery("group");assert writes==1;
  for(String row:new String[]{"{status:COMPLETED,taskInfo:{taskId:id}}","{status:COMPLETED,userTaskId:u,taskInfo:{}}"}){
   reply="{success:true,group:{userTaskList:["+row+"]}}";writes=0;s.userTaskRightsReceive();assert writes==0;
  }
  reply="{success:true,group:{userTaskList:[{status:COMPLETED,userTaskId:u,taskInfo:{taskId:id}}]}}";s.userTaskRightsReceive();assert writes==1;
  for(Object bad:new Object[]{new JSONObject(),new JSONArray(),true}){
   for(String key:new String[]{"bizType","taskId"}){
    JSONObject row=new JSONObject().put("status","TODO").put("taskInfo",new JSONObject().put("bizType","BROWSE").put("taskId","id").put(key,bad));
    reply=new JSONObject().put("success",true).put("group",new JSONObject().put("userTaskList",new JSONArray().put(row))).toString();writes=0;s.userTaskGroupQuery("g");assert writes==0;
   }
   JSONObject row=new JSONObject().put("status","COMPLETED").put("userTaskId",bad).put("taskInfo",new JSONObject().put("taskId","id"));
   reply=new JSONObject().put("success",true).put("group",new JSONObject().put("userTaskList",new JSONArray().put(row))).toString();writes=0;s.userTaskRightsReceive();assert writes==0;
   reply=new JSONObject().put("resultCode","200").put("questionId",bad).put("options",new JSONArray().put("a")).toString();writes=aiCalls=0;
   assert !Reading.answerQuestion(new JSONObject().put("taskJumpUrl","url"))&&writes==0&&aiCalls==0;
  }
  reply="{success:true}";assert Sports.receiveTaskReward(new JSONObject()):"accepted receipt without reward details must still trigger a list probe";
  reply="{success:true,data:{userItems:[]}}";assert Sports.receiveTaskReward(new JSONObject());
  AntSportsRpcCall.replay=true;reply="{success:true}";Sports.processTaskCenter();
  assert AntSportsRpcCall.rewardWrites==2&&AntSportsRpcCall.rewardReads==3:"missing reward details stopped later rewards without probing";
  AntSportsRpcCall.rewardStage=AntSportsRpcCall.rewardReads=AntSportsRpcCall.rewardWrites=0;AntSportsRpcCall.stalled=true;Sports.processTaskCenter();
  assert AntSportsRpcCall.rewardWrites==1&&AntSportsRpcCall.rewardReads==2:"ACK must trigger a probe without repeating unchanged reward";
  AntSportsRpcCall.rewardReads=AntSportsRpcCall.rewardWrites=0;reply="{success:false}";Sports.processTaskCenter();
  assert AntSportsRpcCall.rewardWrites==1&&AntSportsRpcCall.rewardReads==1:"failed receipt must not imply task advancement";AntSportsRpcCall.replay=false;
  reply="{resultCode:SUCCESS,data:{extend:{limit:[{actionCode:COLLECT_TO_FRIEND,leftLimit:2}]},friends:[{}, {userId:{}}, {userId:true}, {userId:friend}]}}";
  writes=0;new Dodo().collectToFriend();assert writes==1:"empty friend id must not fall back to self collect";
  reply="{resultCode:SUCCESS,data:{extend:{limit:[{actionCode:COLLECT_TO_FRIEND,leftLimit:1}]},friends:[{userId:' friend '}]}}";
  Dodo d=new Dodo();d.collectToFriendList.value=Set.of("friend");writes=0;d.collectToFriend();assert writes==0:"spaces bypassed friend exclusion list";
  d.collectToFriendList.value=Set.of();d.collectToFriend();assert writes==1:"normalized friend identity was not sent";
  for(String amount:new String[]{"null","0","-1","'invalid'","1.5","4294967297"}){
   reply="{resultCode:SUCCESS,canCollectDailyLimitedFoodMaterial:true,dailyLimitedFoodMaterialAmount:"+amount+"}";
   writes=0;f.collectDailyLimitedFoodMaterial();assert writes==0;
  }
  reply="{resultCode:SUCCESS,canCollectDailyLimitedFoodMaterial:true,dailyLimitedFoodMaterialAmount:5}";f.collectDailyLimitedFoodMaterial();assert writes==1;
  reply="{resultCode:200,options:[a]}";writes=aiCalls=0;assert !Reading.answerQuestion(new JSONObject().put("taskJumpUrl","url"));assert writes==0&&aiCalls==0;
  reply="{resultCode:200,questionId:id,options:[a]}";assert Reading.answerQuestion(new JSONObject().put("taskJumpUrl","url"));assert writes==1;
  System.out.println("PASS upstream Oct31: donation/task IDs, positive food amount and question identity");
 }
}
'''
for token, path, signature in (
    ('@@DONATE@@', 'antSports/AntSports.java', 'private Boolean canDonateWalkExchangeToday('),
    ('@@GROUP@@', 'antSports/AntSports.java', 'private void userTaskGroupQuery('),
    ('@@RIGHTS@@', 'antSports/AntSports.java', 'private void userTaskRightsReceive('),
    ('@@REWARD@@', 'antSports/AntSports.java', 'public static boolean receiveTaskReward('),
    ('@@CENTER@@', 'antSports/AntSports.java', 'public static void processTaskCenter('),
    ('@@FRIEND@@', 'antDodo/AntDodo.java', 'private void collectToFriend('),
    ('@@FOOD@@', 'antFarm/AntFarm.java', 'private void collectDailyLimitedFoodMaterial('),
    ('@@QUESTION@@', 'readingDada/ReadingDada.java', 'public static boolean answerQuestion('),
):
    code = code.replace(token, method('model/task/' + path, signature))
with tempfile.TemporaryDirectory(prefix='sesame-upstream-oct31-') as tmp:
    source = Path(tmp) / 'UpstreamOct31.java'
    source.write_text(code, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', tmp, str(source)], check=True)
    subprocess.run(['java', '-ea', '-cp', tmp + os.pathsep + str(jar), 'UpstreamOct31'], check=True)
