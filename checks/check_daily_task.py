"""Production daily completion gates: account/day isolation and manual dispatch context."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/aw1y2z/sesame'
with tempfile.TemporaryDirectory(prefix='sesame-daily-') as temp:
    folder = Path(temp) / 'io/github/aw1y2z/sesame/util'
    folder.mkdir(parents=True)
    (folder / 'DailyTask.java').write_text((SOURCE/'util/DailyTask.java').read_text(encoding='utf-8'), encoding='utf-8')
    (folder / 'Status.java').write_text('''package io.github.aw1y2z.sesame.util;
public class Status {
 static String uid="A"; static int day=10; static java.util.Set<String> saved=new java.util.HashSet<>();
 static String key(String k){return uid+":"+day+":"+k;}
 public static boolean hasFlagToday(String k){return saved.contains(key(k));}
 public static void flagToday(String k){saved.add(key(k));}
 public static void clearFlag(String k){saved.remove(key(k));}
}''', encoding='utf-8')
    (folder / 'Log.java').write_text('''package io.github.aw1y2z.sesame.util;
public class Log {static String last; public static void record(String text){last=text;}}''', encoding='utf-8')
    (folder / 'DailyCheck.java').write_text('''package io.github.aw1y2z.sesame.util;
public class DailyCheck {
 public static void main(String[] args) throws Exception {
  assert !DailyTask.skip("x","任务"); DailyTask.done("x"); assert DailyTask.skip("x","任务");
  assert Log.last.contains("当天已经成功执行");
  Status.uid="B"; assert !DailyTask.skip("x","任务"); DailyTask.done("x");
  Status.uid="A"; Status.day=11; assert !DailyTask.skip("x","任务"); Status.day=10;
  Status.flagToday("receipt"); Status.flagToday("budget");
  DailyTask.manual(()->{assert !DailyTask.skip("x","任务");return null;});
  assert !DailyTask.skip("x","任务") : "failed manual run retained stale completion";
  assert Status.hasFlagToday("receipt")&&Status.hasFlagToday("budget");
  Status.flagToday("confirmedSign"); assert DailyTask.skipFlag("confirmedSign", "签到");
  DailyTask.manual(()->{assert !DailyTask.skipFlag("confirmedSign", "签到"); return null;});
  assert Status.hasFlagToday("confirmedSign");
  Status.uid="B"; assert !DailyTask.skipFlag("confirmedSign", "签到"); Status.uid="A";
  Status.uid="B"; assert DailyTask.skip("x","任务"); Status.uid="A";
  Runnable manual = DailyTask.manual(()->DailyTask.capture(()->{
   assert DailyTask.isManual(); DailyTask.done("x");
  }));
  assert !DailyTask.isManual();
  Thread thread=new Thread(()->{manual.run(); assert !DailyTask.isManual();}); thread.start(); thread.join();
  assert DailyTask.skip("x","任务");
  Runnable automatic=DailyTask.capture(()->{assert !DailyTask.isManual();});
  DailyTask.manual(()->{automatic.run();assert DailyTask.isManual();return null;});
  try{DailyTask.manual(()->{throw new IllegalStateException();});}catch(IllegalStateException expected){}
  assert !DailyTask.isManual();
  System.out.println("PASS daily completion: account/day isolation, manual refresh, thread-pool restoration, preserved receipts/budgets");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, *map(str, folder.glob('*.java'))], check=True)
    subprocess.run(['java', '-ea', '-cp', temp, 'io.github.aw1y2z.sesame.util.DailyCheck'], check=True)

model = (SOURCE/'data/task/ModelTask.java').read_text(encoding='utf-8')
hook = (SOURCE/'hook/ApplicationHook.java').read_text(encoding='utf-8')
assert 'Runnable admitted = DailyTask.capture(' in model
assert 'if (action.isEmpty()) return DailyTask.manual(' in model
assert 'DailyTask.manual(() -> ((ModelTask) model).startTask(false))' in model
manual_all = hook[hook.index('else if (ModelGroup.BASE == ModelGroup.getByCode(groupCode))'):]
assert 'DailyTask.manual(' in manual_all[:400]
