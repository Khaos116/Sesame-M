"""Replay actual guardian question RPC and per-question/final progress readback."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/'audit_regressions'))
from run import method,SOURCE
service='model/task/antMember/BeanRewards.java';member='model/task/antMember/AntMember.java';rpc='model/task/antMember/AntMemberRpcCall.java'
assert 'static void runQuiz(' in (SOURCE/service).read_text(encoding='utf-8'),'Missing guardian quiz'
code=r'''
import java.util.*;import org.json.*;import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BeanQuizCheck {
 static String TAG="check";
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String s){return flags.contains(s);}static void flagToday(String s){flags.add(s);}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class UserIdMap {static String uid="uid\"\\";static String getCurrentUid(){return uid;}}
 static class Log {static int ok;static void other(String s){if(s.contains("闭环"))ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class AntMember {@@PAYLOAD@@}
 static class SesameAchievements {@@NUMBER@@}
 static int records,answers,queries,finished;static boolean duplicate,unknown,noProgress,failRecord,drift,cancelAfterRecord,wrongNext,finalPending;static String status="ANSWER_PENDING";
 static JSONObject question(int i){JSONObject q=new JSONObject().put("scriptId","q"+i+"\"\\").put("userDramaId","u"+i).put("rightAnswer","A").put("sort",i).put("answerResult",i<finished?"SUCCESS":"").put("awardStatus",i<finished?"SENT":"");if(unknown&&i==0)q.remove("rightAnswer");return q;}
 static class ApplicationHook {static String requestString(String name,String raw){JSONObject p=new JSONArray(raw).optJSONObject(0),result=new JSONObject();boolean ok=true;
  if(name.endsWith("answerConsult")){assert p.optString("consultScene").equals("ANXINDOU");return new JSONObject().put("success",true).put("result",result.put("answerStatus",finished==2&&!finalPending?"ANSWERED":status)).toString();}
  assert p.optString("userId").equals(UserIdMap.uid);
  if(name.endsWith("queryUserQuestionDrama")){queries++;JSONArray rows=new JSONArray().put(question(0)).put(question(1));if(duplicate)rows.put(question(0));result.put("dramaId",drift&&queries>1?"other":"drama\"\\").put("nextScriptId",wrongNext?"unknown":finished<2?"q"+finished+"\"\\":"").put("userQuestionDramaAnswers",rows);}
  else if(name.endsWith("addAskAnswerRecord")){records++;assert p.optString("askAnswerId").equals("q"+finished+"\"\\")&&p.optString("answerResult").equals("rightAnswer");ok=!failRecord;if(cancelAfterRecord)TimeUtil.cancel=true;}
  else if(name.endsWith("answerQuestionDrama")){answers++;assert p.optString("dramaId").equals("drama\"\\")&&p.optString("scriptId").equals("q"+finished+"\"\\")&&p.optString("userDramaId").equals("u"+finished)&&p.optString("answerResult").equals("SUCCESS");if(!noProgress)finished++;result.put("allCorrect",true);}
  else throw new AssertionError(name);
  return new JSONObject().put("success",ok).put("data",result).toString();}}
 @@METHODS@@
 static class AntMemberRpcCall {@@RPC@@}
 static void reset(){records=answers=queries=finished=Log.ok=0;duplicate=unknown=noProgress=failRecord=drift=cancelAfterRecord=wrongNext=finalPending=TimeUtil.cancel=false;status="ANSWER_PENDING";UserIdMap.uid="uid\"\\";Status.flags.clear();}
 public static void main(String[] args){
  reset();runQuiz();runQuiz();assert records==2&&answers==2&&Log.ok==1;
  reset();finished=1;runQuiz();assert records==1&&answers==1&&Log.ok==1;
  reset();duplicate=true;runQuiz();assert records==0;
  reset();unknown=true;runQuiz();assert records==0;
  reset();wrongNext=true;runQuiz();assert records==0;
  reset();drift=true;runQuiz();assert records==0;
  reset();status="UNKNOWN";runQuiz();assert records==0;
  reset();noProgress=true;runQuiz();runQuiz();assert records==1&&answers==1&&Log.ok==0:"allCorrect is not progress proof";
  reset();failRecord=true;runQuiz();runQuiz();assert records==1&&answers==0;
  reset();finalPending=true;runQuiz();assert answers==2&&Log.ok==0;
  reset();cancelAfterRecord=true;try{runQuiz();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}TimeUtil.cancel=false;runQuiz();assert records==1&&answers==0;
  reset();TimeUtil.cancel=true;try{runQuiz();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert records==0;
  System.out.println("PASS guardian sequence, completed skip, explicit answer/identity, duplicate/unknown/drift, record failure/day guard, authoritative progress/final status, cancellation and escaping");
 }
}
'''
code=code.replace('@@PAYLOAD@@',method(member,'static JSONObject memberFeaturePayload(')).replace('@@NUMBER@@',method('model/task/antMember/SesameAchievements.java','static long exactNonNegative('))
code=code.replace('@@METHODS@@','\n'.join(method(service,s) for s in ('private static JSONObject response(','private static boolean quizCompleted(','private static Map<String, JSONObject> quizQuestions(','static void runQuiz(')))
code=code.replace('@@RPC@@','\n'.join(method(rpc,'public static String '+s+'(') for s in ('guardianAnswerConsult','queryUserQuestionDrama','addAskAnswerRecord','answerQuestionDrama')))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-bean-quiz-') as tmp:
 f=Path(tmp)/'BeanQuizCheck.java';f.write_text(code,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(f),str(SOURCE/'util/TaskCancelledException.java')],check=True)
 subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'BeanQuizCheck'],check=True)
