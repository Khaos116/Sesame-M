"""Replay energy reserve, task-center progress, and fish reward retry on production methods."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent / 'audit_regressions'))
from run import method
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
jar = next((cache / 'caches/modules-2/files-2.1/org.json/json').glob('*/*/json-*.jar'))
code = r'''
import org.json.*;
import java.util.*;
public class SportsOceanAudit {
 static String TAG="test";
 static class Field<T>{T value; Field(T x){value=x;} T getValue(){return value;}}
 Field<Boolean> WALK_GRID=new Field<>(true),awardspecialActivityReceive=new Field<>(true);
 Field<Integer> WALK_GRID_MAX=new Field<>(0),WALK_GRID_LIMIT=new Field<>(100);
 static int energy,spent,awards,builds,reads,sends,claims,stage;static boolean island,fail,stalled;
 static String fishReply;
 static class MyUtils {static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class MessageUtil {static boolean checkSuccess(String t,JSONObject j){return j.optBoolean("success");}static boolean checkResultCode(String t,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class Log {static void record(String s){}static void other(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class TimeUtil {static void sleep(long n){}}
 static class AntOceanRpcCall {static String antfishReceiveTaskAward(String type){return fishReply;}}
 static class AntSportsRpcCall {
  static String queryBaseinfo(){return new JSONObject().put("success",true).put("data",new JSONObject().put("newGame",island)).toString();}
  static String queryMapDetail(String id){return "{success:true,data:{baseMapInfo:{currentPercent:100,status:FINISH_NOT_REWARD,rewards:[{prizeStatus:'待领取',itemId:x}]}}}";}
  static String mapChooseReward(String a,String b,String c){awards++;return "{}";}
  static String queryTaskCenter(){assert ++reads<=21;JSONObject task=new JSONObject().put("id",stage/2).put("taskType","PROMOKERNEL_TASK").put("taskStatus",stage%2==0?"SIGNUP_COMPLETE":"TO_RECEIVE");return new JSONObject().put("success",true).put("data",new JSONObject().put("taskCenterTaskVOS",stage>=4?new JSONArray():new JSONArray().put(task))).toString();}
 }
 static boolean completeTask(JSONObject t){sends++;if(!stalled)stage++;return true;}
 static boolean receiveTaskReward(JSONObject t){claims++;if(!stalled)stage++;return true;}
 void receiveOfflineReward(){}boolean canWalkGrid(String a,String b){return true;}boolean canBuild(String a){return true;}
 int queryUserEnergy(){return energy;}
 boolean walkGrid(String a,String b,String c){if(fail)return false;spend(1);return true;}
 int build(String a,String b,String c,int count){builds++;if(fail)return 0;spend(count);return 100;}
 static void spend(int n){assert spent<100;spent+=n;energy-=n*5;assert energy>=100:"reserve crossed";}
 @@BASE@@
 @@CENTER@@
 @@FISH@@
 static void reset(){spent=awards=builds=reads=sends=claims=stage=0;fail=stalled=false;}
 public static void main(String[] args){
  SportsOceanAudit s=new SportsOceanAudit();
  for(boolean activity:new boolean[]{false,true})for(int start:new int[]{0,100,104,105,110,130,155,160})for(int max:new int[]{0,1,3,10}){
   reset();island=activity;energy=start;s.WALK_GRID_MAX.value=max;s.queryBaseInfoAndProcess();
   int possible=Math.max(0,(start-100)/5);assert spent==(max==0?possible:Math.min(max,possible));
   assert awards==(activity?1:0):"limit must not skip island reward";
  }
  reset();island=true;energy=160;fail=true;s.queryBaseInfoAndProcess();assert builds==1&&spent==0&&awards==1;
  reset();stalled=true;processTaskCenter();assert sends==1&&reads==2:"ACK with unchanged task must stop";
  reset();processTaskCenter();assert sends==2&&claims==2&&reads==5:"new tasks after claim must progress";
  fishReply="{success:false,resultCode:BUSY}";assert !s.antfishReceiveTaskAward("x");
  fishReply="{resultCode:SUCCESS}";assert s.antfishReceiveTaskAward("x"):"transient failure must not suppress retry";
  fishReply="{success:false,resultCode:SUCCESS}";assert !s.antfishReceiveTaskAward("x");
  fishReply="garbage";assert !s.antfishReceiveTaskAward("x");
  System.out.println("PASS sports/ocean audit: reserves, batches, rewards, task progress, fish retry");
 }
}
'''
for token,path,signature in (
 ('@@BASE@@','antSports/AntSports.java','    public void queryBaseInfoAndProcess()'),
 ('@@CENTER@@','antSports/AntSports.java','    public static void processTaskCenter()'),
 ('@@FISH@@','antOcean/AntOcean.java','    private boolean antfishReceiveTaskAward('),
): code=code.replace(token,method('model/task/'+path,signature))
with tempfile.TemporaryDirectory(prefix='sesame-sports-ocean-') as tmp:
 source=Path(tmp)/'SportsOceanAudit.java';source.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(source)],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'SportsOceanAudit'],check=True)
