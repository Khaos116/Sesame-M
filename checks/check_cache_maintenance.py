"""Replay production cache traversal and GMT+8 planner in a temporary test tree only."""
from pathlib import Path
import subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
task='model/task/cacheMaintenance/CacheMaintenance.java'
code=r'''
import java.io.*;import java.nio.file.*;import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class CacheMaintenanceCheck {
 static class MyUtils {static Calendar getInstance(){return Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 @@METHODS@@
 static long at(String t){return java.time.OffsetDateTime.parse(t+"+08:00").toInstant().toEpochMilli();}
 static File write(Path p,long age)throws Exception{Files.write(p,new byte[]{1,2,3});File f=p.toFile();assert f.setLastModified(age);return f;}
 public static void main(String[] args)throws Exception {
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));long now=at("2026-10-07T04:00:00");
  assert nextTime("0430",now)==now+1800000&&nextTime("0430",now+1800000)==now+1800000&&nextTime("0430",now+3600000)==now+3600000;
  for(String bad:new String[]{null,"","24:00","2400","0060","-1"," 0430"})assert nextTime(bad,now)==-1;
  Path tmp=Path.of(args[0]),cache=Files.createDirectory(tmp.resolve("cache")),data=Files.createDirectory(tmp.resolve("data")),nested=Files.createDirectory(cache.resolve("nested"));
  long cutoff=System.currentTimeMillis()-3600000;File old=write(cache.resolve("old"),cutoff-1000),fresh=write(cache.resolve("new"),cutoff+1000),child=write(nested.resolve("old"),cutoff-1000),account=write(data.resolve("account"),cutoff-1000);
  try {Files.createSymbolicLink(cache.resolve("link"),data);}catch(IOException|UnsupportedOperationException ignored){}
  assert cleanRoots(new File[]{cache.toFile(),null,tmp.resolve("missing").toFile()},cutoff)==6;
  assert !old.exists()&&!child.exists()&&fresh.exists()&&account.exists()&&Files.isDirectory(cache)&&Files.isDirectory(nested);
  old=write(cache.resolve("cancel"),cutoff-1000);TimeUtil.cancel=true;try{cleanRoots(new File[]{cache.toFile()},cutoff);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert old.exists();TimeUtil.cancel=false;
  long[] count={5000,0,System.nanoTime()};clean(old,cache.toFile().getCanonicalPath()+File.separator,cutoff,0,count);assert old.exists()&&count[1]==0;
  count=new long[]{0,0,System.nanoTime()};clean(account,cache.toFile().getCanonicalPath()+File.separator,cutoff,0,count);assert account.exists();
  count=new long[]{0,0,System.nanoTime()};clean(old,cache.toFile().getCanonicalPath()+File.separator,cutoff,33,count);assert old.exists();
  System.out.println("PASS cache-only canonical boundary, symlink exclusion, age/root preservation, byte accounting, bounds/cancellation and GMT8 timing");
 }
}
'''.replace('@@METHODS@@','\n'.join(method(task,s) for s in ('static long nextTime(','static long cleanRoots(','private static void clean(','private static boolean budgetRemaining(')))
source=(SOURCE/task).read_text(encoding='utf-8')
assert 'context.getCacheDir(), context.getExternalCacheDir()' in source
assert '"com.eg.android.AlipayGphone".equals(context.getPackageName())' in source
assert 'Files.isSymbolicLink' in source and 'if (!cancelled && !RunGeneration.isStale()' in source
assert 'runExclusiveChild(generation' in source and 'uid.equals(UserIdMap.getCurrentUid())' in source
with tempfile.TemporaryDirectory(prefix='sesame-cache-check-') as tmp:
 f=Path(tmp)/'CacheMaintenanceCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 tree=Path(tmp)/'tree';tree.mkdir()
 subprocess.run(['java','-ea','-cp',tmp,'CacheMaintenanceCheck',str(tree)],check=True)
