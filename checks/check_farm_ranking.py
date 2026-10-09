"""Replay production daily/weekly ranking plans, budgets and readback against isolated RPCs."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

farm = "model/task/antFarm/AntFarm.java"
source = (SOURCE / farm).read_text(encoding="utf-8")
assert '"rankingDonation", "捐蛋排位 | 持久预算与每日排位", false' in source
assert '"rankingDailyBudget", "捐蛋排位 | 每日总预算", 0' in source
assert '"rankingWeeklyBudget", "捐蛋排位 | 每周总预算", 0' in source
signatures = ["private static final class RankingSnapshot", "private static int rankingInt(",
              "private static long rankingTime(", "private static boolean weeklyRankingResponse(",
              "private static RankingSnapshot rankingSnapshot(", "private static String rankingDay(",
              "private static String rankingWeek(", "private static int rankingRounds(",
              "private static int stableRankingStars(", "private static int rankingDonationAmount(",
              "private static int rankingAggressiveStars(",
              "private boolean rankingOwner(", "private JSONObject rankingBudget(",
              "private static boolean rankingWindow(",
              "private int rankingQuota(", "private synchronized boolean reserveRankingDonation(",
              "private boolean confirmRankingDonation(", "private boolean dailyRankingDonation(",
              "private void scheduleRankingWatch(",
              "private Boolean donationCompetition(", "private boolean isCompetitionRoundActive("]
signatures += ["private boolean donateToCompetition(", "private int queryProjectDonationNum(", "private void stealRankS2(", "private void receiveCompetitionTaskAwards("]

code = r'''
import java.util.*;
import org.json.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class FarmRankingCheck {
 static final String TAG="check",RANKING_BUDGET_KEY="farmRankingBudget";
 static class System {static long now=at("2026-10-07T19:58:00");static long currentTimeMillis(){return now;}}
 static long at(String text){return java.time.OffsetDateTime.parse(text+"+08:00").toInstant().toEpochMilli();}
 static class Int {int n;Int(int x){n=x;}Integer getValue(){return n;}}
 static class Bool {boolean n;Bool(boolean x){n=x;}Boolean getValue(){return n;}}
 static class Str {String n="1958";String getValue(){return n;}}
 Bool rankingDonation=new Bool(true),rankingStable=new Bool(true),rankingWatch=new Bool(false),rankingFoodRefill=new Bool(false);
 boolean rankingFoodRefillBusy; int foodCalls; boolean foodUnderfill, foodMoveLeader;
 void useDynamicSpecialFood(double target,RankingSnapshot rank){foodCalls++;harvestBenevolenceScore=foodUnderfill?3:target;if(foodMoveLeader)AntFarmRpcCall.leader=20;}
 Int rankingWatchInterval=new Int(10);String rankingWatchChildId;
 Int rankingDailyBudget=new Int(20),rankingWeeklyBudget=new Int(50);
 Int competitionStealLimit=new Int(0),competitionTargetRank=new Int(1);
 Str rankingDonationTime=new Str();String ownerUserId="A";double harvestBenevolenceScore=30;
 boolean check(){return true;}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class Status {
   static final Set<String> flags=new HashSet<>();
   static String key(String flag){return UserIdMap.uid+":"+rankingDay(System.now)+":"+flag;}
   static boolean hasFlagToday(String flag){return flags.contains(key(flag));}
   static void flagToday(String flag){flags.add(key(flag));}
 }
 @@FLAGS@@
 static class RuntimeInfo {
   static final Map<String,RuntimeInfo> accounts=new HashMap<>();Map<String,String> data=new HashMap<>();static boolean fail;
   static RuntimeInfo getInstance(){return accounts.computeIfAbsent(UserIdMap.uid,k->new RuntimeInfo());}
   String getString(String k){return data.getOrDefault(k,"");}
   boolean putVerified(String k,String v){if(fail)return false;data.put(k,v);return true;}
 }
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}
   static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
   static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.setTimeInMillis(System.now);return c;}}
 static class TimeUtil {static void sleep(long ms){}}
 static class MessageUtil {static boolean checkSuccess(String t,JSONObject o){return Boolean.TRUE.equals(o.opt("success"));}static boolean checkMemo(String t,JSONObject o){return "SUCCESS".equals(o.optString("memo"));}}
 static class Log {static List<String> lines=new ArrayList<>();static void record(String s){lines.add(s);}static void farm(String s){lines.add(s);}static void i(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ChildModelTask {String id;Runnable work;long at;ChildModelTask(String id,String g,Runnable work,Long at){this.id=id;this.work=work;this.at=at;}}
 Map<String,ChildModelTask> children=new HashMap<>();boolean hasChildTask(String id){return children.containsKey(id);}boolean addChildTask(ChildModelTask task){children.put(task.id,task);return true;}
 static JSONObject row(String owner,int position,int count,int stars){return new JSONObject().put("userId",owner).put("rankOrder",position).put("donationNum",count).put("rewardStarNum",stars);}
 static JSONObject rank(boolean weekly){
   JSONObject response=new JSONObject().put("memo","SUCCESS").put("donationRankHomeInfo",new JSONObject().put("userDonationRankList",
     new JSONArray().put(row("C",1,AntFarmRpcCall.leader,3)).put(row("D",2,4,2)).put(row("A",3,AntFarmRpcCall.donated,1))));
   if(weekly)response.put("competitionTaskInfo",new JSONObject()).put("rankRoundId",AntFarmRpcCall.round);
   else response.put("donationCompetitionActivityConf",new JSONObject().put("activityId",AntFarmRpcCall.activity).put("startTime",at("2026-10-01T00:00:00")).put("endTime",at("2026-11-01T20:00:00")));
   return response;
 }
 static JSONObject award(){return new JSONObject().put("memo","SUCCESS").put("donationCompetitionActivityConf",new JSONObject().put("activityId",AntFarmRpcCall.activity).put("endTime",at("2026-11-01T20:00:00")))
   .put("userDonationLevelInfo",new JSONObject().put("levelId",1).put("levelLightStarNum",0))
   .put("levelAwardInfoList",new JSONArray().put(new JSONObject().put("levelId",1).put("levelStarUpNum",30)).put(new JSONObject().put("levelId",2).put("levelStarUpNum",10000)));}
 static class AntFarmRpcCall {
   static int taskAwards;static String taskStatus="TODO";
   static String listCompetitionTask(){return new JSONObject().put("memo","SUCCESS").put("taskList",new JSONArray().put(new JSONObject().put("taskType","TEAM_TASK").put("taskStatus",taskStatus).put("canReceiveAwardCount",1))).toString();}
   static String receiveCompetitionTaskAward(String type,int count){assert taskStatus.equals("FINISHED")&&count==1;taskAwards++;return "{\"success\":true,\"code\":\"100000000\",\"incAwardCount\":1}";}

   static int donated=0,uses=0,reads=0,leader=8;static boolean weekly,unknown,noChange,switchOwner;static String activity="season",round="week1";
   static String queryCompetitionEntranceInfo(){return new JSONObject().put("memo","SUCCESS").put("animationInfo",new JSONObject().put("competitionProjectInfo",new JSONObject().put("projectId","weeklyProject").put("projectName","project"))).toString();}
   static String getProjectInfo(String id){return new JSONObject().put("memo","SUCCESS").put("userProjectDonationNum",donated).toString();}
   static String enterDonationCompetitionRank(){reads++;return rank(weekly).toString();}
   static String enterCompetitionAwardPage(){return award().toString();}
   static String listActivityInfo(){return new JSONObject().put("memo","SUCCESS").put("activityInfos",new JSONArray().put(new JSONObject().put("activityId","charity").put("donationLimit",100).put("donationTotal",0))).toString();}
   static String donation(String id,int amount){assert id.equals("charity");return consume(amount);}
   static String donationCompetition(String id,int amount){assert id.equals("weeklyProject");return consume(amount);}
   static String consume(int amount){uses++;if(!noChange)donated+=amount;if(switchOwner)UserIdMap.uid="B";
      return unknown?"garbage":new JSONObject().put("memo","SUCCESS").put("donation",new JSONObject().put("harvestBenevolenceScore",30-amount)).toString();}
 }
 @@METHODS@@
 static void reset(FarmRankingCheck f){UserIdMap.uid=f.ownerUserId="A";System.now=at("2026-10-07T19:58:00");RuntimeInfo.accounts.clear();RuntimeInfo.fail=false;Status.flags.clear();
   AntFarmRpcCall.donated=AntFarmRpcCall.uses=AntFarmRpcCall.reads=0;AntFarmRpcCall.weekly=AntFarmRpcCall.unknown=AntFarmRpcCall.noChange=AntFarmRpcCall.switchOwner=false;
   AntFarmRpcCall.activity="season";AntFarmRpcCall.round="week1";AntFarmRpcCall.leader=8;f.foodCalls=0;f.foodUnderfill=f.foodMoveLeader=f.rankingFoodRefillBusy=f.rankingFoodRefill.n=false;f.competitionStealLimit.n=0;f.competitionTargetRank.n=1;f.rankingDonation.n=f.rankingStable.n=true;f.rankingWatch.n=false;f.rankingWatchChildId=null;f.rankingDailyBudget.n=20;f.rankingWeeklyBudget.n=50;f.harvestBenevolenceScore=30;f.children.clear();}
 public static void main(String[] args)throws Exception {
   TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));FarmRankingCheck f=new FarmRankingCheck();reset(f);
   RankingSnapshot r=rankingSnapshot(rank(false),"A",false);assert r!=null&&r.donated==0&&r.rank==3;
   assert rankingSnapshot(rank(true),"A",false)==null&&rankingSnapshot(rank(false),"A",true)==null;
   assert rankingSnapshot(rank(false),"missing",false)==null;
   for(Object invalid:List.of("3",-1,1.5,Long.MAX_VALUE,JSONObject.NULL)){
      JSONObject bad=rank(false);bad.optJSONObject("donationRankHomeInfo").optJSONArray("userDonationRankList").optJSONObject(0).put("donationNum",invalid);assert rankingSnapshot(bad,"A",false)==null;}
   JSONObject bad=rank(false);bad.optJSONObject("donationRankHomeInfo").optJSONArray("userDonationRankList").put(row("A",4,0,0));assert rankingSnapshot(bad,"A",false)==null;
   bad=rank(false);bad.optJSONObject("donationCompetitionActivityConf").put("projectId","weeklyProject");assert rankingSnapshot(bad,"A",false)==null;
   assert rankingDay(at("2026-10-08T00:01:00")).equals("2026-10-08");assert rankingWeek(at("2026-10-11T19:00:00")).equals("2026-10-05");assert rankingWeek(at("2026-10-12T00:00:00")).equals("2026-10-12");
   assert rankingRounds(at("2026-10-07T19:59:00"),at("2026-10-08T20:00:00"))==2;
   assert rankingRounds(at("2026-10-07T20:00:00"),at("2026-10-08T19:59:00"))==0;
   assert rankingWindow(r,at("2026-10-07T19:59:59"))&&!rankingWindow(r,at("2026-10-07T20:00:00"));
   RankingSnapshot week=rankingSnapshot(rank(true),"A",true);assert rankingWindow(week,at("2026-10-11T19:59:59"))&&!rankingWindow(week,at("2026-10-11T20:00:00"));
   int stars=stableRankingStars(award(),r,System.now);assert stars==2:stars;assert rankingDonationAmount(r,stars,20)==5;assert rankingDonationAmount(r,stars,4)==0;
   assert rankingAggressiveStars(r,5)==2&&rankingAggressiveStars(r,4)==1&&rankingAggressiveStars(r,20)==3;
   JSONObject wrongAward=award();wrongAward.optJSONObject("donationCompetitionActivityConf").put("activityId","other");assert stableRankingStars(wrongAward,r,System.now)==-1;
   f.rankingDailyBudget.n=0;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);f.rankingWeeklyBudget.n=0;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);f.rankingDailyBudget.n=4;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1&&AntFarmRpcCall.donated==5;
   assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;
   FarmRankingCheck restored=new FarmRankingCheck();assert restored.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;
   JSONObject state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY));assert state.optInt("daily")==5&&state.optInt("weekly")==5;
   System.now=at("2026-10-08T19:58:00");AntFarmRpcCall.donated=0;r=rankingSnapshot(rank(false),"A",false);assert f.reserveRankingDonation(r,5,"plan");
   state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY));assert state.optInt("daily")==5&&state.optInt("weekly")==10;
   System.now=at("2026-10-12T19:58:00");r=rankingSnapshot(rank(false),"A",false);assert f.reserveRankingDonation(r,5,"plan");
   state=MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY));assert state.optInt("weekly")==5;
   reset(f);RuntimeInfo.fail=true;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);RuntimeInfo.getInstance().data.put(RANKING_BUDGET_KEY,"malformed");assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.unknown=true;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;
   reset(f);AntFarmRpcCall.noChange=true;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;
   reset(f);AntFarmRpcCall.switchOwner=true;assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;f.ownerUserId="B";AntFarmRpcCall.switchOwner=false;
   assert RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY).isEmpty();assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==1;
   reset(f);AntFarmRpcCall.weekly=true;assert !f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   assert f.donationCompetition("weeklyProject","name",3,"auto")&&AntFarmRpcCall.uses==1;
   assert !f.donationCompetition("weeklyProject","name",3,"auto")&&AntFarmRpcCall.uses==1;
   assert f.donationCompetition("weeklyProject","name",2,"steal")&&AntFarmRpcCall.uses==2;
   assert !f.donationCompetition("weeklyProject","name",2,"steal")&&AntFarmRpcCall.uses==2;
   reset(f);f.rankingWatch.n=true;AntFarmRpcCall.noChange=true;f.dailyRankingDonation(null);assert AntFarmRpcCall.uses==1&&f.children.size()==1;
   ChildModelTask watch=f.children.values().iterator().next();System.now=watch.at;watch.work.run();assert AntFarmRpcCall.uses==1:"unconfirmed attempt cannot send again";
   AntFarmRpcCall.noChange=false;AntFarmRpcCall.donated=5;System.now+=10000;f.dailyRankingDonation(null);assert AntFarmRpcCall.uses==2:"confirmed prior baseline permits a new affordable chase";
   assert MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(RANKING_BUDGET_KEY)).optInt("daily")==9;
   reset(f);System.now=at("2026-10-07T10:00:00");assert f.dailyRankingDonation(null)&&f.children.size()==1&&AntFarmRpcCall.uses==0;
   assert !RuntimeInfo.getInstance().getString("farmRankingPlan").isEmpty();assert f.dailyRankingDonation(null)&&f.children.size()==1;
   ChildModelTask scheduled=f.children.values().iterator().next();System.now=scheduled.at;scheduled.work.run();assert AntFarmRpcCall.uses==1;
   reset(f);System.now=at("2026-10-07T10:00:00");f.dailyRankingDonation(null);scheduled=f.children.values().iterator().next();System.now=scheduled.at;
   TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;TaskLifecycle.thaw(freeze);scheduled.work.run();assert AntFarmRpcCall.uses==0;
   reset(f);System.now=at("2026-10-07T20:00:00");assert f.dailyRankingDonation(null)&&AntFarmRpcCall.uses==0;
   reset(f);Thread.currentThread().interrupt();try{f.dailyRankingDonation(null);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}assert AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;Thread.currentThread().interrupt();try{f.donationCompetition("weeklyProject","name",3,"auto");throw new AssertionError("weekly cancel swallowed");}catch(TaskCancelledException expected){}finally{Thread.interrupted();}assert AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;
   assert f.donateToCompetition(3)&&f.foodCalls==1&&AntFarmRpcCall.uses==1&&AntFarmRpcCall.donated==3;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.rankingDailyBudget.n=2;
   assert f.donateToCompetition(3)&&f.foodCalls==1&&AntFarmRpcCall.donated==2;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.rankingWeeklyBudget.n=0;
   assert !f.donateToCompetition(3)&&f.foodCalls==0&&AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;assert !f.donateToCompetition(3)&&f.foodCalls==0;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.stealRankS2();assert f.foodCalls==1&&AntFarmRpcCall.donated==9;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.competitionStealLimit.n=8;f.stealRankS2();assert f.foodCalls==0&&AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.foodUnderfill=true;f.stealRankS2();assert f.foodCalls==1&&AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;f.foodMoveLeader=true;f.stealRankS2();assert f.foodCalls==1&&AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=2;f.stealRankS2();assert AntFarmRpcCall.donated==5&&AntFarmRpcCall.uses==1:"target second row, not leader";
   reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=3;f.stealRankS2();assert AntFarmRpcCall.uses==0:"already at target";
   reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=4;f.stealRankS2();assert AntFarmRpcCall.uses==0:"missing target row cannot guess";
   reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=2;f.competitionStealLimit.n=4;f.stealRankS2();assert AntFarmRpcCall.uses==0;
   reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=2;f.rankingDailyBudget.n=4;f.stealRankS2();assert AntFarmRpcCall.uses==0;
   for(int invalid:new int[]{0,101}){reset(f);AntFarmRpcCall.weekly=true;f.competitionTargetRank.n=invalid;f.stealRankS2();assert AntFarmRpcCall.uses==0;}
   reset(f);AntFarmRpcCall.weekly=true;AntFarmRpcCall.unknown=true;AntFarmRpcCall.noChange=true;
   assert !f.donateToCompetition(3) && Status.hasFlagToday(FLAG_COMPETITION_DONATE_TRIED) && !Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY);
   assert !f.donateToCompetition(3) && AntFarmRpcCall.uses==1;
   reset(f);AntFarmRpcCall.weekly=true;AntFarmRpcCall.unknown=true;
   assert f.donateToCompetition(3) && Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY);
   assert f.donateToCompetition(3) && AntFarmRpcCall.uses==1;
   System.now=at("2026-10-08T19:58:00");AntFarmRpcCall.unknown=false;
   assert f.donateToCompetition(3) && AntFarmRpcCall.uses==2;
   reset(f);Status.flagToday(FLAG_CHARITY_DONATION_DONE);f.harvestBenevolenceScore=0;f.rankingFoodRefill.n=true;
   assert f.donateToCompetition(3) && AntFarmRpcCall.uses==0 && f.foodCalls==0;
   reset(f);AntFarmRpcCall.weekly=true;AntFarmRpcCall.switchOwner=true;
   assert !f.donateToCompetition(3) && AntFarmRpcCall.uses==1 && !Status.hasFlagToday(FLAG_COMPETITION_DONATED_TODAY);
   AntFarmRpcCall.taskAwards=0;for(String status:new String[]{"TODO","RECEIVED","EXPIRED",""}){AntFarmRpcCall.taskStatus=status;f.receiveCompetitionTaskAwards();}assert AntFarmRpcCall.taskAwards==0:"non-FINISHED reward submitted";
   AntFarmRpcCall.taskStatus="FINISHED";Log.lines.clear();f.receiveCompetitionTaskAwards();assert AntFarmRpcCall.taskAwards==1&&Log.lines.stream().anyMatch(x->x.contains("本次领取1个任务奖励")):"success-based reward response rejected as missing memo";
   java.lang.System.out.println("PASS farm ranking: typed daily/S2 schemas, own account, stars/cheapest plan, budget zero/caps, GMT+8 day/week reset, restart persistence, pre-RPC write failure, unknown/readback guards, schedule identity and cancellation");
 }
}
'''.replace("@@METHODS@@", "\n".join(method(farm, signature) for signature in signatures))
code = code.replace("@@FLAGS@@", "\n".join(re.search(r"    private static final String " + flag + r" = [^;]+;", source).group() for flag in
    ("FLAG_CHARITY_DONATION_DONE", "FLAG_COMPETITION_DONATE_TRIED", "FLAG_COMPETITION_DONATED_TODAY")))
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jar = sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-farm-ranking-") as directory:
    java = Path(directory) / "FarmRankingCheck.java"
    java.write_text(code, encoding="utf-8")
    env = dict(os.environ, JAVA_TOOL_OPTIONS="-Xms16m -Xmx192m")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jar), "-d", directory, str(java),
                    str(SOURCE / "util/TaskCancelledException.java"), str(SOURCE / "data/task/TaskLifecycle.java")], check=True, env=env)
    subprocess.run(["java", "-ea", "-cp", directory + os.pathsep + str(jar), "FarmRankingCheck"], check=True, env=env)
