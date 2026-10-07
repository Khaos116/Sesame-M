"""Replay the production single-module dispatcher with account/lifecycle gates; no RPC."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method, SOURCE

code = r'''
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
public class ManualTaskCheck {
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class NotificationUtil {static int rounds;static void startRound(){rounds++;}}
 static class Model {}
 static class Group {String getCode(){return "other";}}
 static class Log {static void record(String s){}}
 static class ChildModelTask {Runnable action;ChildModelTask(String id,String group,Runnable r,long time){action=r;}}
 static List<ChildModelTask> pending=new java.util.ArrayList<>();
 static class ModelTask extends Model {boolean enabled=true,busy;int starts,actions;long generation=1;boolean startTask(boolean force){assert !force;starts++;return enabled;}
 boolean isEnable(){return enabled;}boolean check(){return enabled;}boolean checkManualAction(String a){return enabled;}boolean supportsManualAction(String a){return "angle".equals(a);}void runManualAction(String a){actions++;}
 long taskGeneration(){return generation;}Group getGroup(){return new Group();}boolean addChildTask(ChildModelTask t){pending.add(t);return true;}boolean runExclusiveChild(long g,Runnable r){if(busy||g!=generation)return false;r.run();return true;}}
 static class AntFishpond extends ModelTask {}
 static class AntForestV2 extends ModelTask {}
 static int stops;static void stopAllTask(){stops++;assert !TaskLifecycle.isIdle();}
 static Model[] models;static Model[] getModelArray(){return models;}
 @@METHOD@@
 public static void main(String[] args){AntFishpond fish=new AntFishpond();AntForestV2 forest=new AntForestV2();models=new Model[]{null,new Model(),fish,forest};
  assert !startNamedTask("AntFishpond",null)&&!startNamedTask("AntFishpond","")&&!startNamedTask("AntFishpond","B")&&!startNamedTask("Unknown","A");assert stops==0;
  assert startNamedTask("AntFishpond","A")&&fish.starts==1&&forest.starts==0&&stops==1&&NotificationUtil.rounds==1&&TaskLifecycle.isIdle();
  fish.enabled=false;assert !startNamedTask("AntFishpond","A")&&fish.starts==2;
  TaskLifecycle.Freeze freeze=TaskLifecycle.freezeIfIdle();assert freeze!=null;int before=stops;assert !startNamedTask("AntFishpond","A")&&stops==before;TaskLifecycle.thaw(freeze);
  fish.enabled=true;assert !startNamedTask("AntFishpond","A","deleteAnything")&&pending.isEmpty();
  assert startNamedTask("AntFishpond","A","angle")&&pending.size()==1;pending.remove(0).action.run();assert fish.actions==1;
  assert startNamedTask("AntFishpond","A","angle");UserIdMap.uid="B";pending.remove(0).action.run();assert fish.actions==1;UserIdMap.uid="A";
  assert startNamedTask("AntFishpond","A","angle");fish.generation++;pending.remove(0).action.run();assert fish.actions==1;
  System.out.println("PASS registered module selection, UID/lifecycle gate, cancellation of previous run, disabled strategy and idle release");
 }
}
'''.replace('@@METHOD@@', method('data/task/ModelTask.java','public static boolean startNamedTask(String code, String uid)')+'\n'+method('data/task/ModelTask.java','public static boolean startNamedTask(String code, String uid, String action)')).replace('static List<','static java.util.List<')
ui=(SOURCE/'ui/miuix/MiuixGroupFieldsActivity.kt').read_text(encoding='utf-8')
assert '.putExtra("model", modelCode)' in ui and '.putExtra("userId", userId)' in ui and '.putExtra("taskAction", action)' in ui
assert '.setPackage("com.eg.android.AlipayGphone")' in ui
assert 'ModelTask' not in ui and 'ApplicationHook' not in ui.replace('ApplicationHook/hook.Toast/NotificationUtil','')
with tempfile.TemporaryDirectory(prefix='sesame-manual-') as tmp:
 f=Path(tmp)/'ManualTaskCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(f),str(SOURCE/'data/task/TaskLifecycle.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp,'ManualTaskCheck'],check=True)

fish_code=r'''
import java.util.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class FishManualCheck {
 static class UserIdMap {static String uid="A";static String getCurrentUid(){return uid;}}
 static class TaskCommon {static boolean IS_ENERGY_TIME;}
 static class FishConfig {static boolean autoFish,autoTasks;static boolean isEnableFishAuto(){return autoFish;}static boolean isEnableFishTaskAuto(){return autoTasks;}static long getFishCheckInterval(){return 3600000;}}
 static class Status {static Status INSTANCE=new Status();long last=System.currentTimeMillis();static boolean failed;static boolean hasFlagToday(String key){return failed;}long getFishLastExecTime(){return last;}void setFishLastExecTime(long n){last=n;}static void save(){}}
 static class Log {static void other(String s){}static void printStackTrace(String s,Throwable t){throw new AssertionError(t);}}
 String runningUid;boolean todayRewardEnd,firstQueryDone,tomorrowRodTriggered,allowed=true,cancel;static int lastRodCount;static double lastFishWeight;
 Set<String> processedTasks=new HashSet<>(),failedTasks=new HashSet<>();int enters,exchanges,tasks,angles;
 boolean check(){return allowed&&!TaskCommon.IS_ENERGY_TIME&&(FishConfig.autoFish||FishConfig.autoTasks);}boolean isAllowedTime(){return allowed;}String getValidToken(){return "token";}String getToday(){return "day";}
 void enterFishpond(){if(cancel)throw new TaskCancelledException();enters++;}boolean checkExchangeReward(){exchanges++;return true;}void triggerSubplotsActivity(boolean b){}void executeTasks(){tasks++;}void startFishing(String token){assert token.equals("token");angles++;}
 @@METHODS@@
 public static void main(String[] args){FishManualCheck f=new FishManualCheck();FishConfig.autoFish=FishConfig.autoTasks=true;f.runFish("all",false);assert f.enters==0;
  FishConfig.autoFish=FishConfig.autoTasks=false;f.runFish("angle",true);assert f.enters==1&&f.exchanges==1&&f.angles==1&&f.tasks==0;
  f.runFish("exchange",true);assert f.enters==2&&f.exchanges==2&&f.angles==1;
  f.runFish("all",true);assert f.enters==3&&f.tasks==1&&f.angles==2;
  f.allowed=false;f.runFish("all",true);assert f.enters==3;f.allowed=true;
  TaskCommon.IS_ENERGY_TIME=true;f.runFish("all",true);assert f.enters==3;TaskCommon.IS_ENERGY_TIME=false;
  Status.failed=true;f.runFish("all",true);assert f.enters==3;Status.failed=false;
  f.cancel=true;try{f.runFish("all",true);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert f.angles==2;
  System.out.println("PASS actual fish manual all/angle/exchange reuse, independent interval bypass, quiet/time/failure restrictions and cancellation");
 }
}
'''.replace('@@METHODS@@',method('model/task/fish/FishTask.java','private void runFish(')+'\n'+method('model/task/fish/FishTask.java','@Override protected Boolean checkManualAction(').replace('@Override ',''))
with tempfile.TemporaryDirectory(prefix='sesame-fish-manual-') as tmp:
 f=Path(tmp)/'FishManualCheck.java';f.write_text(fish_code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp,'FishManualCheck'],check=True)
