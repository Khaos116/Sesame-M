"""Compile production daily-cap and shared tool methods; replay without Android/network."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / "audit_regressions"))
from run import method, SOURCE

farm = "model/task/antFarm/AntFarm.java"
source = (SOURCE / farm).read_text(encoding="utf-8")
assert 'new IntegerModelField("accelerateToolDailyLimit"' in source, "Missing configurable acceleration cap"
assert "Status.useAccelerateToolToday()" not in method(farm, "private void useAccelerateTool()"), "Count at shared success boundary"
code = r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class FarmLimitCheck {
 static final String TAG="check";
 static class Field { int n=8; Integer getValue(){return n;} }
 static class AntFarm { Field f=new Field(); Field getAccelerateToolDailyLimit(){return f;} }
 static class ModelTask { static AntFarm task=new AntFarm(); static AntFarm getModel(Class<?> c){return task;} }
 static class Status {
   static Status INSTANCE=new Status(); int useAccelerateToolCount;
   static Set<String> flags=new HashSet<>(); static int saves;
   static boolean hasFlagToday(String k){return flags.contains(k);}
   static void flagToday(String k){flags.add(k);}
   static void save(){saves++;}
   @@STATUS@@
 }
 static class Bool {boolean getValue(){return false;}}Bool useFullRewardTool=new Bool();String ownerFarmId="farm";boolean useBigEaterRewardTool(){throw new AssertionError("disabled full-stock policy");}
 enum ToolType { ACCELERATETOOL, NEWEGGTOOL,BIG_EATER_TOOL; String nickName(){return name();} }
 static class MyUtils { static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}} }
 static class MessageUtil { static boolean checkMemo(String t,JSONObject j){return "SUCCESS".equals(j.optString("memo"));} }
 static class TimeUtil { static boolean cancel; static void sleep(long n){if(cancel)throw new TaskCancelledException();} }
 static class Log { static void farm(String s){} static void err(String t,String s,Throwable e){throw new AssertionError(e);} }
 static class AntFarmRpcCall {
   static int reads,uses; static String reply="{\"memo\":\"SUCCESS\"}";
   static String listFarmTool(){reads++;return "{\"memo\":\"SUCCESS\",\"toolList\":[{\"toolType\":\"ACCELERATETOOL\",\"toolCount\":9,\"toolId\":\"a\"},{\"toolType\":\"NEWEGGTOOL\",\"toolCount\":1,\"toolId\":\"b\"}]}";}
   static String useFarmTool(String f,String id,String type){uses++;return reply;}
 }
 @@METHOD@@
 public static void main(String[] args) {
   FarmLimitCheck f=new FarmLimitCheck();
   ModelTask.task.f.n=0; assert !f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert AntFarmRpcCall.reads==0;
   ModelTask.task.f.n=2; assert f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert Status.INSTANCE.useAccelerateToolCount==1;
   assert f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert Status.INSTANCE.useAccelerateToolCount==2;
   assert !f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert AntFarmRpcCall.uses==2;
   ModelTask.task.f.n=-1; assert f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert Status.INSTANCE.useAccelerateToolCount==3;
   Status.flagToday("farm::useFarmToolLimit::ACCELERATETOOL"); assert !f.useFarmTool("farm",ToolType.ACCELERATETOOL);
   assert f.useFarmTool("farm",ToolType.NEWEGGTOOL); assert Status.INSTANCE.useAccelerateToolCount==3;
   Status.INSTANCE=new Status(); Status.flags.clear(); ModelTask.task.f.n=8;
   AntFarmRpcCall.reply="{\"resultCode\":\"3D16\"}"; assert !f.useFarmTool("farm",ToolType.ACCELERATETOOL);
   assert Status.INSTANCE.useAccelerateToolCount==0 && !Status.canUseAccelerateToolToday();
   Status.flags.clear(); AntFarmRpcCall.reply="garbage"; assert !f.useFarmTool("farm",ToolType.ACCELERATETOOL); assert Status.INSTANCE.useAccelerateToolCount==0;
   Status.INSTANCE.useAccelerateToolCount=7; assert Status.canUseAccelerateToolToday(); Status.INSTANCE.useAccelerateToolCount=8; assert !Status.canUseAccelerateToolToday();
   ModelTask.task=null; assert !Status.canUseAccelerateToolToday(); ModelTask.task=new AntFarm();
   Status.INSTANCE=new Status(); assert Status.canUseAccelerateToolToday();
   TimeUtil.cancel=true; int before=AntFarmRpcCall.uses;
   try{f.useFarmTool("farm",ToolType.ACCELERATETOOL);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
   assert AntFarmRpcCall.uses==before;
   System.out.println("PASS configurable cap, default8, unlimited/zero, reset, server limit, success accounting, other tools, malformed response and cancellation");
 }
}
'''
code=code.replace("@@STATUS@@",method("util/Status.java","public static synchronized Boolean canUseAccelerateToolToday()")+method("util/Status.java","public static synchronized void useAccelerateToolToday()"))
code=code.replace("@@METHOD@@",method(farm,"private Boolean useFarmTool("))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-farm-limit-") as tmp:
 java=Path(tmp)/"FarmLimitCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"FarmLimitCheck"],check=True)
