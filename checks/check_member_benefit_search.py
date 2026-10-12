"""Compile actual benefit-search collection and its escaped RPC; exchange stays selection-based."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).parent/"audit_regressions"))
from run import method,SOURCE
member="model/task/antMember/AntMember.java";rpc="model/task/antMember/AntMemberRpcCall.java"
src=(SOURCE/member).read_text(encoding="utf-8")
assert 'new StringModelField("memberBenefitSearchKeywords"' in src,"Missing search-supplement entry"
assert "selectedIds.contains(benefitId)" in method(member,"private void memberPointExchangeBenefit(boolean timed)"),"Search must not bypass selected goods"
code=r'''
import org.json.*;
import java.util.*;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class BenefitSearchCheck {
 static final String TAG="check";
 static class Field {String s="";String getValue(){return s;}}Field memberBenefitSearchKeywords=new Field();
 static class MyUtils {static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}}
 static class TimeUtil {static boolean cancel;static void sleep(long n){if(cancel)throw new TaskCancelledException();}}
 static class RpcRequestGuard {static boolean isFailure(JSONObject j){return Boolean.FALSE.equals(j.opt("success"));}}
 static class Log {static void i(String t,String s){}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class ApplicationHook {static List<JSONObject> calls=new ArrayList<>();static String reply;
  static class Version {String getVersionString(){return "current-version";}}static Version getAlipayVersion(){return new Version();}
  static String requestString(String name,String args){JSONObject body=new JSONArray(args).optJSONObject(0);calls.add(body);return reply;}
 }
 static class MessageUtil {static boolean checkResultCode(String tag,JSONObject j){return "SUCCESS".equals(j.optString("resultCode"));}}
 static class MemberBenefitIdMap {static Map<String,String> map=new HashMap<>();static Map<String,String> getMap(){return map;}static void add(String id,String name){map.put(id,name);}}
 static class AntMemberRpcCall {
 static int mode,calls; static List<Integer> pages=new ArrayList<>();
 static String queryIndexNaviBenefitFlowV2(String uid,String navi,int page){
  calls++;if(navi.equals("14"))pages.add(page);
  if(mode==4)throw new TaskCancelledException();
  if(mode==3)return "{}";
  int next=mode==1?page:mode==2?page+1:page<3?page+1:0;
  JSONObject j=new JSONObject().put("resultCode","SUCCESS").put(mode==5?"nextAdPageNum":"nextPageNum",next);
  return j.put("entityInfoList",new JSONArray().put(new JSONObject().put("benefitInfo",new JSONObject().put("benefitId","p"+page).put("name","item").put("pricePresentation",new JSONObject().put("strategyType","POINT_PAY"))))).toString();
 }
 @@RPC@@}
 @@METHODS@@
 public static void main(String[] args){
  BenefitSearchCheck f=new BenefitSearchCheck();ApplicationHook.reply="{\"resultCode\":\"SUCCESS\",\"entityInfoList\":[{\"benefitInfo\":{\"benefitId\":\"id\",\"name\":\"Cup\",\"itemId\":\"item\",\"pricePresentation\":{\"strategyType\":\"POINT_PAY\"}}}]}";
  assert f.queryMemberBenefitSearchResults().length()==0&&ApplicationHook.calls.isEmpty();
  f.memberBenefitSearchKeywords.s="cup,cup,coffee";assert f.queryMemberBenefitSearchResults().length()==1&&ApplicationHook.calls.size()==2;
  assert ApplicationHook.calls.get(0).optString("clientVersion").equals("current-version")&&ApplicationHook.calls.get(0).optString("cityCode").isEmpty();
  ApplicationHook.calls.clear();f.memberBenefitSearchKeywords.s="quote\"item";f.queryMemberBenefitSearchResults();assert ApplicationHook.calls.get(0).optString("query").equals("quote\"item");
  ApplicationHook.reply="{}";assert f.queryMemberBenefitSearchResults().length()==0;
  ApplicationHook.reply="{\"success\":false,\"entityInfoList\":[{}]}";assert f.queryMemberBenefitSearchResults().length()==0;
  ApplicationHook.calls.clear();f.memberBenefitSearchKeywords.s="a,b,c,d,e,f,g,h,i,j,k,l";f.queryMemberBenefitSearchResults();assert ApplicationHook.calls.size()==10;
  f.fetchBenefitsFromNavi("uid");assert AntMemberRpcCall.pages.equals(List.of(1,2,3))&&MemberBenefitIdMap.map.containsKey("p3");
  AntMemberRpcCall.mode=1;AntMemberRpcCall.calls=0;f.fetchBenefitsFromNavi("uid");assert AntMemberRpcCall.calls==7;
  AntMemberRpcCall.mode=2;AntMemberRpcCall.calls=0;f.fetchBenefitsFromNavi("uid");assert AntMemberRpcCall.calls==350;
  AntMemberRpcCall.mode=3;AntMemberRpcCall.calls=0;f.fetchBenefitsFromNavi("uid");assert AntMemberRpcCall.calls==7;
  AntMemberRpcCall.mode=5;AntMemberRpcCall.pages.clear();f.fetchBenefitsFromNavi("uid");assert AntMemberRpcCall.pages.equals(List.of(1,2,3));
  AntMemberRpcCall.mode=4;try{f.fetchBenefitsFromNavi("uid");throw new AssertionError("navigation swallowed cancellation");}catch(TaskCancelledException expected){}
  TimeUtil.cancel=true;try{f.queryMemberBenefitSearchResults();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
  System.out.println("PASS empty/default, keyword/result dedup, ten-query bound, current version/empty city, failed/malformed data, escaped keywords and cancellation");
 }
}
'''
code=code.replace("@@METHODS@@",method(member,"static JSONObject memberFeaturePayload(")+method(member,"private JSONArray queryMemberBenefitSearchResults(")+method(member,"private void fetchBenefitsFromNavi("))
code=code.replace("@@RPC@@",method(rpc,"public static String searchMemberBenefit("))
cache=Path(os.environ.get("GRADLE_USER_HOME",Path.home()/".gradle"))/"caches/modules-2/files-2.1/org.json/json"
jar=sorted(p for p in cache.glob("*/*/json-*.jar") if not p.name.endswith(("-sources.jar","-javadoc.jar")))[-1]
with tempfile.TemporaryDirectory(prefix="sesame-benefit-search-") as tmp:
 java=Path(tmp)/"BenefitSearchCheck.java";java.write_text(code,encoding="utf-8")
 subprocess.run(["javac","-encoding","UTF-8","-cp",str(jar),"-d",tmp,str(java),str(SOURCE/"util/TaskCancelledException.java")],check=True)
 subprocess.run(["java","-ea","-cp",tmp+os.pathsep+str(jar),"BenefitSearchCheck"],check=True)
