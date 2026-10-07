"""Compile actual bill-sticker workflow and replay receive/upgrade/benefit/drawing readbacks."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
member="model/task/antMember/AntMember.java"
rpc="model/task/antMember/AntMemberRpcCall.java"
source=(SOURCE/member).read_text(encoding="utf-8")
assert 'new BooleanModelField("CollectStickers"' in source,"Missing bill-sticker entry"
code=r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class StickerCheck {
 static final String TAG="check";
 static class Field {boolean on=true;boolean getValue(){return on;}} Field collectStickers=new Field();
 static class MyUtils {
  static JSONObject newJSONObject(){return new JSONObject();}
  static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
  static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,6);return c;}
 }
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}static String errorMessage(JSONObject j){return "failed";}}
 static class Status {static Set<String> flags=new HashSet<>();static boolean hasFlagToday(String k){return flags.contains(k);}static void flagToday(String k){flags.add(k);}}
 static class Log {static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ApplicationHook {
  static List<JSONObject> calls=new ArrayList<>();static boolean fail=false, changed=true, duplicate=false, malformed=false;
  static boolean received=false,upgraded=false,benefit=false,draw=false;
  static String requestString(String name,String args){
   JSONObject body=new JSONArray(args).optJSONObject(0);body.put("rpc",name);calls.add(body);
   JSONObject j=new JSONObject().put("success",!fail);
   if(name.endsWith("queryStickerCanReceive")){
    if(malformed)return "garbage";
    JSONArray list=received?new JSONArray():new JSONArray().put(new JSONObject().put("id","record").put("stickerConfigId","cfg").put("name","Name"));
    if(duplicate&&!received)list.put(new JSONObject().put("id","record").put("stickerConfigId","other"));
    return j.put("canReceivePageList",new JSONArray().put(new JSONObject().put("stickerCanReceiveList",list))).toString();
   }
   if(name.endsWith("receiveSticker")){if(changed&&!fail)received=true;return j.toString();}
   if(name.endsWith("queryHomePage"))return j.put("commonStickerRes",new JSONObject().put("stickerDetailList",new JSONArray().put(new JSONObject().put("stickerConfigId","cfg").put("name","Name").put("status",upgraded?"received":"upgradable").put("hasBenefit",true).put("currentLevel",new JSONObject().put("levelCode",upgraded?"2":"1")).put("upgradableLevel",new JSONObject().put("levelCode","2"))))).toString();
   if(name.endsWith("upgradeStickerBatch")){if(changed&&!fail)upgraded=true;return j.toString();}
   if(name.endsWith("queryDetailPage"))return j.put("stickerDetailRes",new JSONObject().put("stickerDetailList",new JSONArray().put(new JSONObject().put("upgradeBenefitModel",new JSONObject().put("status",benefit?"received":"can_receive"))))).toString();
   if(name.endsWith("triggerUpgradePrize")){if(changed&&!fail)benefit=true;return j.toString();}
   if(name.endsWith("prize.home.page"))return j.put("prizeConsumerIdList",draw?new JSONArray():new JSONArray().put("quota")).toString();
   if(name.endsWith("prize.trigger")){if(changed&&!fail)draw=true;return j.toString();}
   throw new AssertionError(name);
  }
 }
 static class AntMemberRpcCall {@@RPC@@}
 @@METHODS@@
 static long mutations(){return ApplicationHook.calls.stream().filter(j->{String s=j.optString("rpc");return s.endsWith("receiveSticker")||s.endsWith("upgradeStickerBatch")||s.endsWith("triggerUpgradePrize")||s.endsWith("prize.trigger");}).count();}
 static void reset(){ApplicationHook.calls.clear();ApplicationHook.received=false;ApplicationHook.upgraded=false;ApplicationHook.benefit=false;ApplicationHook.draw=false;ApplicationHook.fail=false;ApplicationHook.changed=true;ApplicationHook.duplicate=false;ApplicationHook.malformed=false;Status.flags.clear();Log.ok=0;TimeUtil.cancel=false;}
 public static void main(String[] args){
  TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));StickerCheck f=new StickerCheck();
  reset();f.collectStickers.on=false;f.collectBillStickers();assert ApplicationHook.calls.isEmpty();f.collectStickers.on=true;
  reset();f.collectBillStickers();assert mutations()==4&&ApplicationHook.received&&ApplicationHook.upgraded&&ApplicationHook.benefit&&ApplicationHook.draw&&Log.ok==4;
  assert ApplicationHook.calls.get(0).optString("year").equals("2026")&&ApplicationHook.calls.get(0).optString("month").equals("10");
  f.collectBillStickers();assert mutations()==4;
  reset();ApplicationHook.changed=false;f.collectBillStickers();f.collectBillStickers();assert mutations()==1&&Log.ok==0;
  reset();ApplicationHook.fail=true;f.collectBillStickers();assert mutations()==0;
  reset();ApplicationHook.malformed=true;f.collectBillStickers();assert mutations()==0;
  reset();ApplicationHook.duplicate=true;f.collectBillStickers();assert mutations()==0;
  reset();ApplicationHook.received=true;ApplicationHook.changed=false;f.collectBillStickers();f.collectBillStickers();assert mutations()==1&&Log.ok==0;
  reset();ApplicationHook.received=true;ApplicationHook.upgraded=true;ApplicationHook.changed=false;f.collectBillStickers();f.collectBillStickers();assert mutations()==1&&Log.ok==0;
  reset();ApplicationHook.received=true;ApplicationHook.upgraded=true;ApplicationHook.benefit=true;ApplicationHook.changed=false;f.collectBillStickers();f.collectBillStickers();assert mutations()==1&&Log.ok==0;
  reset();TimeUtil.cancel=true;try{f.collectBillStickers();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert mutations()==0;
  reset();AntMemberRpcCall.receiveSticker("2026","10",new JSONArray().put("quote\"id"),new JSONArray().put("cfg"));assert ApplicationHook.calls.get(0).optJSONArray("stickerIds").optString(0).equals("quote\"id");
  System.out.println("PASS default-off/GMT+8, receive/readback, upgrade/readback, benefit/readback, drawing/readback, stale/failed/malformed/duplicate/retry guards, cancellation and RPC escaping");
 }
}
'''
code=code.replace("@@METHODS@@","\n".join(method(member,s) for s in ("static JSONObject memberFeaturePayload(","static Map<String, JSONObject> memberRowsById(","private boolean receiveBillStickers(","private boolean upgradeBillStickers(","private static Boolean billStickerBenefitState(","private boolean collectBillStickerPrizes(","private void collectBillStickers(")))
code=code.replace("@@RPC@@","\n".join(method(rpc,s) for s in ("public static String queryStickerCanReceiveList(","public static String receiveSticker(","public static String queryStickerHomePage(","public static String upgradeStickerBatch(","public static String queryStickerDetailPage(","public static String triggerStickerUpgradePrize(","public static String queryStickerPrizeHomePage(","public static String triggerStickerDrawing(")))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-sticker-") as tmp:
 java=Path(tmp)/"StickerCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"StickerCheck"],check=True)
