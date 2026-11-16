from pathlib import Path
import os, subprocess, sys, tempfile
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
p='model/task/antForest/AntForestTask.java'
caller=(SOURCE/'model/task/antForest/AntForestV2.java').read_text(encoding='utf-8')
start=caller.index('if (!Status.hasFlagToday("BlackList::initAntForest")) {')
opening=caller.index('{',start)
depth=0
for end in range(opening,len(caller)):
 depth+=(caller[end]=='{')-(caller[end]=='}')
 if depth==0: break
caller_block=caller[start:end+1]
code=r'''
import org.json.*;import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class ForestInitSignCheck {
 static final String TAG="test";static class AntForestV2 {} AntForestV2 host=new AntForestV2();
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Log {static void err(String a,String b,Throwable t){}static void record(String s){}static void i(String s){}static void forest(String s){}}
 static class TimeUtil {static void sleep(long n){}} static class UserIdMap {static String getCurrentUid(){return "uid";}}
 static class Statistics {enum DataType{COLLECTED}static void addData(DataType t,int n){}}
 static class DailyTask {static boolean skip(String a,String b){return false;}static boolean skipFlag(String a,String b){return false;}static int done;static void done(String s){done++;}}
 static class Status {static Set<String> flags=new HashSet<>();static void flagToday(String s){flags.add(s);}static boolean hasFlagToday(String s){return flags.contains(s);}}
 static class SelectModelField {} static class ModelFields extends HashMap<String,Object>{} static class ConfigV2 {static ConfigV2 INSTANCE=new ConfigV2();Map<String,ModelFields> getModelFieldsMap(){return new HashMap<>();}}
 static class AntForestVitalityTaskListMap {static void load(){}static void save(){}static void add(String a,String b){}}
 static class AntForestHuntTaskListMap {static void load(){}static void save(){}static void add(String a,String b){}}
 static class MessageUtil {static Set<String> presetBlackList(String a,String b){return new HashSet<>();}static void syncTaskBlackList(String a,String b,Set<String> c,Set<String>d,SelectModelField e){}static boolean checkResultCode(String t,JSONObject o){return "SUCCESS".equals(o.optString("resultCode"));}static boolean checkSuccess(String t,JSONObject o){return o.optBoolean("success");}}
 static class AntForestRpcCall {static int calls,writes;static String cancel="",query="{resultCode:SUCCESS,forestTasksNew:[]}",list="{resultCode:SUCCESS,taskInfoList:[]}",draw="{success:true,drawSceneGroups:[]}",scene="{success:true,taskInfoList:[]}",common="{}";static void step(String s){calls++;if(cancel.equals(s))throw new TaskCancelledException();}
 static String queryTaskList(){step("query");return query;}static String listTaskopengreen(){step("list");return list;}static String listTaskopengreen(String a,String b){step("scene");return scene;}static String enterDrawActivityopengreen(String a,String b,String c){step("draw");return draw;}static String queryCommonSign(String s){step("common");return common;}static String vitalitySign(){step("vitality");return "{}";}static String antiepSign(String a,String b,String c){step("sign");writes++;return "{success:true}";}}
 @@METHODS@@
 static class Field {boolean value;Field(boolean b){value=b;}boolean getValue(){return value;}}
 static class Caller {ForestInitSignCheck taskHelper=new ForestInitSignCheck();Field AutoAntForestVitalityTaskList=new Field(false),AutoAntForestHuntTaskList=new Field(false),forestTaskGroup=new Field(true),receiveForestTaskAward=new Field(true),ForestHunt=new Field(false);void run(){@@CALLER@@}}
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}static void cancel(Runnable r){try{r.run();throw new AssertionError("cancellation swallowed");}catch(TaskCancelledException expected){}}
 static String sign(Object key,Object id,Object scene,Object signed){JSONObject r=new JSONObject().put("signKey","today");if(signed!=null)r.put("signed",signed);JSONObject v=new JSONObject().put("signRecords",new JSONArray().put(r));if(key!=null)v.put("currentSignKey",key);if(id!=null)v.put("signId",id);if(scene!=null)v.put("sceneCode",scene);return v.toString();}
 public static void main(String[] args){ForestInitSignCheck f=new ForestInitSignCheck();
  Caller caller=new Caller();AntForestRpcCall.query="{}";caller.run();check(!Status.hasFlagToday("BlackList::initAntForest"),"caller marks failed init");
  AntForestRpcCall.query="{resultCode:SUCCESS,forestTasksNew:[]}";caller.run();check(Status.hasFlagToday("BlackList::initAntForest"),"caller fails to mark successful retry");
  Status.flags.clear();AntForestRpcCall.cancel="query";cancel(caller::run);check(!Status.hasFlagToday("BlackList::initAntForest"),"caller marks cancelled init");AntForestRpcCall.cancel="";
  caller.receiveForestTaskAward.value=false;Status.flags.clear();int beforeDisabled=AntForestRpcCall.calls;caller.run();check(Status.hasFlagToday("BlackList::initAntForest")&&beforeDisabled==AntForestRpcCall.calls,"disabled caller must complete without RPC");Status.flags.clear();AntForestRpcCall.calls=0;
  check(f.initAntForestTaskListMap(false,false,false,false)&&AntForestRpcCall.calls==0,"disabled lists");
  AntForestRpcCall.query="{}";check(!f.initAntForestTaskListMap(false,false,true,false),"failed query");AntForestRpcCall.query="{resultCode:SUCCESS}";check(!f.initAntForestTaskListMap(false,false,true,false),"missing container");
  AntForestRpcCall.query="{resultCode:SUCCESS,forestTasksNew:[]}";AntForestRpcCall.list="{}";check(!f.initAntForestTaskListMap(false,false,true,false),"failed list response");AntForestRpcCall.list="{resultCode:SUCCESS}";check(!f.initAntForestTaskListMap(false,false,true,false),"missing task list");AntForestRpcCall.list="{resultCode:SUCCESS,taskInfoList:[]}";check(f.initAntForestTaskListMap(false,false,true,true),"empty complete lists and retry");
  AntForestRpcCall.draw="{success:true}";check(!f.initAntForestTaskListMap(false,false,false,true),"missing draw groups");AntForestRpcCall.draw="{success:true,drawSceneGroups:[{drawActivity:{sceneCode:'a'}}]}";AntForestRpcCall.scene="{success:true}";check(!f.initAntForestTaskListMap(false,false,false,true),"missing scene tasks");
  for(String c:List.of("query","list","draw","scene")){AntForestRpcCall.cancel=c;cancel(()->f.initAntForestTaskListMap(false,false,true,true));}AntForestRpcCall.cancel="";
  for(String c:List.of("query","common","vitality")){AntForestRpcCall.cancel=c;cancel(()->{if(c.equals("query"))f.vantiepSign();else if(c.equals("common"))f.queryCommonSign();else f.vitalitySign();});}AntForestRpcCall.cancel="";
  Object[][] invalid={{null,"id","scene",false},{"today",123,"scene",false},{"today","id"," ",false},{"today","id","scene",null},{"today","id","scene","false"}};
  for(Object[] a:invalid){String v=sign(a[0],a[1],a[2],a[3]);AntForestRpcCall.query=new JSONObject().put("resultCode","SUCCESS").put("forestSignVOList",new JSONArray().put(new JSONObject(v))).toString();AntForestRpcCall.common=new JSONObject().put("resultCode","SUCCESS").put("forestSignVO",new JSONObject(v)).toString();int before=AntForestRpcCall.writes;f.vantiepSign();f.queryCommonSign();check(before==AntForestRpcCall.writes,"invalid sign fields");}
  String valid=sign("today","id","scene",false);AntForestRpcCall.query=new JSONObject().put("resultCode","SUCCESS").put("forestSignVOList",new JSONArray().put(new JSONObject(valid))).toString();AntForestRpcCall.common=new JSONObject().put("resultCode","SUCCESS").put("forestSignVO",new JSONObject(valid)).toString();int n=AntForestRpcCall.writes;f.vantiepSign();f.queryCommonSign();check(AntForestRpcCall.writes==n+2,"valid sign");
  valid=sign("today","id","scene",true);AntForestRpcCall.query=new JSONObject().put("resultCode","SUCCESS").put("forestSignVOList",new JSONArray().put(new JSONObject(valid))).toString();AntForestRpcCall.common=new JSONObject().put("resultCode","SUCCESS").put("forestSignVO",new JSONObject(valid)).toString();n=AntForestRpcCall.writes;int d=DailyTask.done;f.vantiepSign();f.queryCommonSign();check(AntForestRpcCall.writes==n&&Status.flags.contains("forest::vantiepSign")&&DailyTask.done==d+1,"confirmed already signed");
  System.out.println("PASS forest init completeness, disabled, cancellation and sign validation");
 }
}
'''
code=code.replace('@@METHODS@@','\n'.join(method(p,s) for s in ('public boolean initAntForestTaskListMap(','void vantiepSign(','void queryCommonSign(','void vitalitySign(')))
code=code.replace('@@CALLER@@',caller_block)
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(x for x in cache.glob('*/*/json-*.jar') if not x.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='forest-init-sign-') as tmp:
 java=Path(tmp)/'ForestInitSignCheck.java';java.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(java),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'ForestInitSignCheck'],check=True)
