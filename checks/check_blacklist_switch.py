"""Replay production automatic blacklist writes: switches, buffering and manual entries."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method

code = r'''
import java.util.*;
public class BlacklistSwitchCheck {
 static final String TAG="test";
 static final int BLACKLIST_CONFIRM_WINDOW_DAYS=3, BLACKLIST_CONFIRM_HITS=3;
 static final Map<String,Set<String>> TASK_WHITE_LIST=new HashMap<>();
 static final ThreadLocal<List<Runnable>> DEFER_BLACKLIST=new ThreadLocal<>();
 static class ModelField<T>{T value;ModelField(T v){value=v;}T getValue(){return value;}}
 static class SelectModelField extends ModelField<Set<String>>{
  SelectModelField(){super(new HashSet<>());}boolean contains(String s){return value.contains(s);}
  void add(String s,int ignored){value.add(s);}}
 static class ModelFields extends HashMap<String,ModelField<?>>{}
 static class ConfigV2 {static ConfigV2 INSTANCE=new ConfigV2();Map<String,ModelFields> fields=new HashMap<>();
  static int saves;Map<String,ModelFields> getModelFieldsMap(){return fields;}
  static boolean save(String uid,boolean force){saves++;return true;}}
 static class UserIdMap {static String getCurrentUid(){return "uid";}}
 static class Log {static void record(String s){}static void err(String a,String b,Throwable t){throw new AssertionError(t);}}
 static class AutoBlackRecord {long lastDay,blackDay;int hits;static AutoBlackRecord parse(String s){return null;}String format(){return "record";}}
 static class AutoBlackListMap {static int saves,puts;static void ensureLoaded(){}static String get(String key){return null;}
  static void put(String k,String v){puts++;}static void save(){saves++;}}
 static String autoBlackKey(String a,String b,String c){return a+"|"+b+"|"+c;}
 static long todayIndex(){return 1;}
 static int records;
 static void recordAutoBlack(String a,String b,String c){records++;}
 static void recordAutoBlackPermanent(String a,String b,String c){records++;}
 @@METHODS@@
 public static void main(String[] args){
  ModelFields fields=new ModelFields();ConfigV2.INSTANCE.fields.put("module",fields);
  SelectModelField list=new SelectModelField();list.add("manual",0);fields.put("TaskList",list);
  ModelField<Boolean> enabled=new ModelField<>(false);fields.put("AutoTaskList",enabled);
  MarkTaskBlackList("module","TaskList","display","failed");
  MarkTaskBlackListPermanent("module","TaskList","display","permanent");
  MarkTaskBlackListConfirm("module","TaskList","display","uncertain");
  assert list.getValue().equals(Set.of("manual"))&&records==0&&ConfigV2.saves==0;
  assert AutoBlackListMap.saves==0&&AutoBlackListMap.puts==0;
  enabled.value=true;beginDeferBlackList();MarkTaskBlackList("module","TaskList","display","deferred");
  enabled.value=false;endDeferBlackList(true);assert list.getValue().equals(Set.of("manual"));
  enabled.value=true;MarkTaskBlackList("module","TaskList","display","failed");
  assert list.contains("failed")&&records==1&&ConfigV2.saves==1;
  MarkTaskBlackListConfirm("module","TaskList","display","uncertain");
  assert AutoBlackListMap.saves==1&&AutoBlackListMap.puts==1&&!list.contains("uncertain");
  enabled.value=null;MarkTaskBlackList("module","TaskList","display","unknown");assert !list.contains("unknown");
  System.out.println("PASS disabled auto blacklist prevents direct/permanent/deferred/observation writes; manual entries retained");
 }
}
'''
signatures = (
    "private static boolean isAutoBlackListEnabled(",
    "private static void doMarkTaskBlackList(",
    "public static void MarkTaskBlackListConfirm(",
    "public static void MarkTaskBlackList(",
    "public static void MarkTaskBlackListPermanent(",
    "public static void beginDeferBlackList(",
    "public static void endDeferBlackList(",
    "private static boolean deferBlackList(",
)
code = code.replace("@@METHODS@@", "\n".join(method("util/MessageUtil.java", s) for s in signatures))
with tempfile.TemporaryDirectory(prefix="sesame-blacklist-switch-") as tmp:
    java = Path(tmp) / "BlacklistSwitchCheck.java"
    java.write_text(code, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(java)], check=True)
    subprocess.run(["java", "-ea", "-cp", tmp, "BlacklistSwitchCheck"], check=True)
