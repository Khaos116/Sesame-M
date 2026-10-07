"""Replay actual GMT+8 schedule, independent forest children and shared main-task slot; no RPC."""
from pathlib import Path
import subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE

forest = 'model/task/antForest/AntForestV2.java'
model = 'data/task/ModelTask.java'
code = r'''
package io.github.aw1y2z.sesame.model.task.antForest;
import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.*;
import io.github.aw1y2z.sesame.util.*;
public class ForestScheduleCheck {
 static class Flag {boolean n;Flag(boolean x){n=x;}boolean getValue(){return n;}}
 static class Text {String n="0000-2400";String getValue(){return n;}}
 static class Num {int n=300;int getValue(){return n;}}
 Flag collectEnergy=new Flag(true),enableCycleTakeLook=new Flag(false),enableCycleRankScan=new Flag(false),pkEnergy=new Flag(false);
 Text cycleTakeLookTime=new Text(),cycleRankScanTime=new Text();Num cycleTakeLookInterval=new Num(),cycleRankScanInterval=new Num();
 Flag onlyCollectRevivedSelfEnergy=new Flag(false);Text bubbleBoostTime=new Text();Num bubbleBoostCard=new Num(),bubbleBoostDailyLimit=new Num();String bubbleBoostCheckId;
 static class UsePropType {static int ALL=1,ONLY_LIMIT_TIME=2;}
 static class Status {static Set<String> flags=new HashSet<>();static int attempts;static boolean hasFlagToday(String s){return flags.contains(s);}static int getIntFlagToday(String s){return attempts;}}
 int boosts;void useBubbleBoostCard(){if(collectEnergy.n&&boostTimeAllowed(System.currentTimeMillis())){boosts++;Status.attempts++;String key=boostPointFlag(System.currentTimeMillis());if(!key.isEmpty())Status.flags.add(key);}}
 boolean enabled=true,hasErrorWait,quiet,pause;String selfId="A";final String[] forestCycleIds=new String[2];
 boolean isEnable(){return enabled;} boolean isRevivedSelfQuietTime(){return quiet;}boolean check(){return !pause;}
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class Log {static void record(String s){}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel||RunGeneration.isStale())throw new TaskCancelledException();}}
 AtomicBoolean running=new AtomicBoolean(); volatile long generation;
 @@SLOT@@
 int finds,ranks,pks,smart;void findAndCollectEnergy(){finds++;}void scanForestRankings(){ranks++;}void collectPKEnergy(){pks++;}void useSmartDoubleCard(){smart++;}void scheduleSmartDoubleCheck(){}
 static class ChildModelTask {String id;long at;Runnable action;boolean cancel;ChildModelTask(String x,String g,Runnable r,Long t){id=x;action=r;at=t;}Boolean getIsCancel(){return cancel;}}
 Map<String,ChildModelTask> children=new HashMap<>();Boolean addChildTask(ChildModelTask t){assert t.at>System.currentTimeMillis();children.put(t.id,t);return true;}ChildModelTask getChildTask(String id){return children.get(id);}void removeChildTask(String id){ChildModelTask t=children.remove(id);if(t!=null)t.cancel=true;}
 @@CYCLES@@
 @@BOOST@@
 static long at(String text){return java.time.OffsetDateTime.parse(text+"+08:00").toInstant().toEpochMilli();}
 static void fire(ForestScheduleCheck f,int kind){String id=f.forestCycleIds[kind];assert id!=null;ChildModelTask t=f.children.get(id);t.action.run();f.children.remove(id);}
 static void reset(ForestScheduleCheck f){f.children.clear();Arrays.fill(f.forestCycleIds,null);f.enabled=f.collectEnergy.n=true;f.enableCycleTakeLook.n=f.enableCycleRankScan.n=f.pkEnergy.n=f.hasErrorWait=f.quiet=f.pause=TimeUtil.cancel=false;f.finds=f.ranks=f.pks=0;f.running.set(false);f.cycleTakeLookTime.n=f.cycleRankScanTime.n="0000-2400";UserIdMap.uid="A";}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
  long now=at("2026-10-07T07:00:00"),inside=now+1800000,end=now+3600000;
  assert ForestSchedule.nextWindow("0700-0800",now)==now;
  assert ForestSchedule.nextWindow("0700-0800",inside)==inside;
  assert ForestSchedule.nextWindow("0700-0800",end)==now+86400000;
  assert ForestSchedule.nextWindow("2300-0200",at("2026-10-07T01:30:00"))==at("2026-10-07T01:30:00");
  assert ForestSchedule.nextWindow("2300-0200",at("2026-10-07T02:00:00"))==at("2026-10-07T23:00:00");
  assert ForestSchedule.nextWindow("0900-0930,0700-0800",now)==now;
  for(String bad:new String[]{"","2400-0100","2360-0200","0700-0700","0700-0800,","0900","x","0700-2500"})assert ForestSchedule.nextWindow(bad,now)==-1:bad;
  assert ForestSchedule.nextTrigger("-1",now)==now;
  assert ForestSchedule.nextTrigger("0900,2100",now)==at("2026-10-07T09:00:00");
  assert ForestSchedule.nextTrigger("0900,!0800-1000",now)==-1;
  assert ForestSchedule.nextTrigger("-1,!2300-0200",at("2026-10-07T01:30:00"))==at("2026-10-07T02:00:00");
  assert ForestSchedule.nextTrigger("-1,!0000-2400",now)==-1;
  for(String bad:new String[]{"-1,0900","2560","0900,","!0900-0900"})assert ForestSchedule.nextTrigger(bad,now)==-1;
  assert ForestSchedule.pointKey("0700,0900",now).equals("420");assert ForestSchedule.pointKey("-1",now).isEmpty();
  ForestScheduleCheck f=new ForestScheduleCheck();
  reset(f);f.scheduleForestCycles(0,0);f.scheduleForestCycles(1,0);assert f.children.isEmpty();
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);String first=f.forestCycleIds[0];f.scheduleForestCycles(0,0);assert f.children.size()==1&&first.equals(f.forestCycleIds[0]);fire(f,0);assert f.finds==1&&f.ranks==0&&f.children.size()==1&&!first.equals(f.forestCycleIds[0]):"unique successor survives old child cleanup";
  reset(f);f.enableCycleRankScan.n=true;f.pkEnergy.n=true;f.scheduleForestCycles(1,0);fire(f,1);assert f.ranks==1&&f.pks==1&&f.finds==0;
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);f.running.set(true);fire(f,0);assert f.finds==0&&f.children.size()==1;f.running.set(false);
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);f.enableCycleTakeLook.n=false;fire(f,0);assert f.finds==0&&f.children.isEmpty();
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);UserIdMap.uid="B";fire(f,0);assert f.finds==0&&f.children.isEmpty();
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);f.generation++;fire(f,0);assert f.finds==0&&f.children.isEmpty();
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);TimeUtil.cancel=true;try{fire(f,0);throw new AssertionError();}catch(TaskCancelledException expected){}assert f.finds==0&&f.forestCycleIds[0]==null&&!f.running.get();TimeUtil.cancel=false;
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);f.pause=true;fire(f,0);assert f.finds==0;
  reset(f);f.enableCycleTakeLook.n=true;f.cycleTakeLookTime.n="bad";f.scheduleForestCycles(0,0);assert f.children.isEmpty();
  reset(f);f.enableCycleTakeLook.n=true;f.scheduleForestCycles(0,0);f.collectEnergy.n=false;f.scheduleForestCycles(0,0);assert f.children.isEmpty();
  f.runExclusiveChild(f.generation,()->{assert f.running.get();assert !f.runExclusiveChild(f.generation,()->{throw new AssertionError();});});assert !f.running.get();
  try{f.runExclusiveChild(f.generation,()->{f.generation++;TimeUtil.sleep(0);});throw new AssertionError();}catch(TaskCancelledException expected){}assert !f.running.get();
  CountDownLatch acquired=new CountDownLatch(1),release=new CountDownLatch(1);Thread worker=new Thread(()->f.runExclusiveChild(f.generation,()->{acquired.countDown();try{release.await();}catch(InterruptedException e){throw new RuntimeException(e);}}));worker.start();assert acquired.await(5,TimeUnit.SECONDS);assert !f.running.compareAndSet(false,true):"main and periodic child share the exact slot";release.countDown();worker.join(5000);assert !worker.isAlive()&&!f.running.get();
  reset(f);f.bubbleBoostCard.n=1;f.bubbleBoostDailyLimit.n=10;Status.attempts=0;Status.flags.clear();f.bubbleBoostTime.n="-1";f.scheduleBubbleBoost(0);assert f.bubbleBoostCheckId==null:"default preserves main-round-only boost";
  f.bubbleBoostTime.n="-1,!0000-2400";f.scheduleBubbleBoost(0);assert f.bubbleBoostCheckId==null;
  Calendar time=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));String point=String.format(Locale.ROOT,"%02d%02d",time.get(Calendar.HOUR_OF_DAY),time.get(Calendar.MINUTE));f.bubbleBoostTime.n=point;f.scheduleBubbleBoost(0);String boostId=f.bubbleBoostCheckId;assert boostId!=null;f.scheduleBubbleBoost(0);assert boostId.equals(f.bubbleBoostCheckId);
  f.children.get(boostId).action.run();f.children.remove(boostId);assert f.boosts==1&&Status.attempts==1&&!boostId.equals(f.bubbleBoostCheckId):"single point attempt and unique successor";
  if(f.bubbleBoostCheckId!=null){ChildModelTask next=f.children.get(f.bubbleBoostCheckId);assert next.at>System.currentTimeMillis()+3600000;UserIdMap.uid="B";next.action.run();assert f.boosts==1&&f.bubbleBoostCheckId==null;}
  System.out.println("PASS GMT+8/cross-midnight/invalid/boundary/point/blocked windows, independent/coalesced/unique cycles, pause/off/uid/cancel/generation and main-slot concurrency");
 }
}
'''
code=code.replace('@@SLOT@@',method(model,'protected final long taskGeneration(')+method(model,'protected final boolean runExclusiveChild(')).replace('@@CYCLES@@',method(forest,'private boolean forestCycleEnabled(')+method(forest,'private synchronized void scheduleForestCycles(')).replace('@@BOOST@@',method(forest,'private synchronized void scheduleBubbleBoost(')+method(forest,'private boolean boostTimeAllowed(')+method(forest,'private String boostPointFlag('))
with tempfile.TemporaryDirectory(prefix='sesame-forest-schedule-') as tmp:
    main=Path(tmp)/'ForestScheduleCheck.java';main.write_text(code,encoding='utf-8')
    helper=Path(tmp)/'MyUtils.java';helper.write_text('package io.github.aw1y2z.sesame.util; import java.util.*; public class MyUtils {public static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(main),str(helper),str(SOURCE/'model/task/antForest/ForestSchedule.java'),str(SOURCE/'util/RunGeneration.java'),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp,'io.github.aw1y2z.sesame.model.task.antForest.ForestScheduleCheck'],check=True)
