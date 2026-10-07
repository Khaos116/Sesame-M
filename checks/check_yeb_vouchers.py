"""Real YEB conversion/exchange, voucher-identity direct submission, selected/all-owned asset activation and single-attempt writes."""
from pathlib import Path
import os, subprocess, sys, tempfile
sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).parent/'audit_regressions'))
from run import method, SOURCE
code = r'''
import org.json.*;import java.util.*;import java.math.BigDecimal;import java.security.MessageDigest;import java.nio.charset.StandardCharsets;import java.time.LocalDate;import java.time.DateTimeException;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
public class YebVouchersCheck {
 static class IdAndName {String id,name;}static class OtherEntity extends IdAndName{OtherEntity(String i,String n){id=i;name=n;}}
 static class MyUtils{static int day=7;static JSONObject newJSONObject(){return new JSONObject();}static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}static Calendar getInstance(){Calendar c=Calendar.getInstance(TimeZone.getTimeZone("GMT+8"));c.clear();c.set(2026,9,day,12,0);return c;}}
 static class UserIdMap{static String uid="self";static String getCurrentUid(){return uid;}}
 static class TimeUtil{static boolean cancel;static long slept;static void sleep(long n){if(cancel)throw new TaskCancelledException();slept+=n;}}
 static class RuntimeInfo{static Map<String,String> data=new HashMap<>();static boolean writable=true;static RuntimeInfo r=new RuntimeInfo();static RuntimeInfo getInstance(){return r;}String getString(String k){return data.getOrDefault(k,"");}boolean putVerified(String k,String v){if(!writable)return false;if(v==null)data.remove(k);else data.put(k,v);return true;}}
 static class Status{static Set<String> flags=new HashSet<>();static Map<String,Integer> counts=new HashMap<>();static boolean hasFlagToday(String k){return flags.contains(MyUtils.day+":"+k);}static void flagToday(String k){flags.add(MyUtils.day+":"+k);}static int getIntFlagToday(String k){return counts.getOrDefault(MyUtils.day+":"+k,0);}static void setIntFlagToday(String k,int n){counts.put(MyUtils.day+":"+k,n);}}
 static class Log{static int ok;static void other(String s){ok++;}static void record(String s){}static void err(String t,String s,Throwable e){throw new AssertionError(e);}}
 static class RpcRequestGuard{@@GUARD@@}
 static class AntMemberRpcCall{@@MAIN@@}
 @@HELPER@@
 static final String RECEIPT="memberYebVoucherReceipt",CAMPAIGN="CP152735172|PZ1144215101";
 static String assetMode="";static int conversions,exchanges,activations,voucherQueries,balanceQueries,assetQueries,count;static long delay;static String uid="self",trial="trial\"\\id",state="I";static BigDecimal balance,threshold;
 static boolean apply,badAck,mixed,badCount,missingCount,unstable,malformed,wrongBalance,missingEquity,duplicateAsset,badStatus,switchUid,crossDay,cancelAfter,negativeDelay,noReduction;static int remaining;static boolean allOwned,newVoucher,singleAttempt,badVoucherAmount,badVoucherDate,voucherFields;static int voucherActivations;static String voucherInterrupt="";static Map<String,String> extraAssets=new LinkedHashMap<>();static List<String> activatedIds=new ArrayList<>();
 static class ApplicationHook{static String requestString(String method,String raw,int tries,int interval){assert tries==1&&interval==0;singleAttempt=true;try{return requestString(method,raw);}finally{singleAttempt=false;}}static String requestString(String method,String raw){if(method.equals("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert")||method.equals("com.alipay.yebscenebff.expgold.index.exchange")||method.equals("alipay.yebprod.promo.yebTrial.active"))assert singleAttempt:"write used retrying bridge";JSONObject p=new JSONArray(raw).optJSONObject(0),r=new JSONObject().put("success",true);
  if(malformed)return "malformed";
  if(method.equals("alipay.yebprod.query.queryYebTrialCertVoucher")){voucherQueries++;assert p.optString("component").equals("PROMO_ACTIVITY")&&p.optString("sortType").equals("drawTime")&&p.optString("source").equals("QIANAPP");JSONArray templates=p.optJSONArray("voucherTemplateIdList");assert templates.length()==2&&templates.optString(0).equals("202312260007300180780087H5IR")&&templates.optString(1).equals("2026011300073001807800H1558H");JSONArray rows=new JSONArray();for(int i=0;i<count;i++)rows.put(new JSONObject().put("fixtureData",unstable&&voucherQueries==2?"changed"+i:"stable"+i));r.put("certVoucherInfoList",rows);if(!missingCount)r.put("totalCount",badCount?count+1:count);}
  else if(method.equals("com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert")){conversions++;assert p.optString("convertType").equals("all")&&Boolean.TRUE.equals(p.opt("isShowExchangeModal"));assert RuntimeInfo.data.containsKey(RECEIPT);JSONArray results=new JSONArray().put(new JSONObject().put("status","fulfilled").put("value",new JSONObject().put("success",true))).put(new JSONObject().put("status",mixed?"rejected":"fulfilled").put("value",new JSONObject().put("success",!mixed)));r.put("convertResults",results).put("delayRefreshTime",negativeDelay?-1:delay);if(apply&&!noReduction)count=remaining;after();}
  else if(method.equals("com.alipay.yebscenebff.needle.yebExpGold.queryMain")){balanceQueries++;r.put("resultData",new JSONObject().put("balance",balance.toPlainString()).put("subThreshold",threshold.toPlainString()));if(unstable&&balanceQueries==2)r.optJSONObject("resultData").put("balance",balance.add(BigDecimal.ONE).toPlainString());}
  else if(method.equals("com.alipay.yebscenebff.expgold.index.exchange")){exchanges++;assert p.optString("campId").equals("CP152735172")&&p.optString("prizeId").equals("PZ1144215101");assert new BigDecimal(p.optString("exchangeAmount")).compareTo(balance)==0;assert !p.optString("bizOrderNo").isEmpty()&&RuntimeInfo.data.containsKey(RECEIPT);if(!missingEquity)r.put("result",new JSONObject().put("equityNo","equity\"\\id"));if(apply)balance=wrongBalance?BigDecimal.ONE:BigDecimal.ZERO;after();}
  else if(method.equals("alipay.yebprod.promo.yebTrialAsset")){assetQueries++;JSONArray rows=new JSONArray().put(new JSONObject().put("trialId",trial).put("status",badStatus?123:unstable&&assetQueries==2?"changed":state));for(Map.Entry<String,String> e:extraAssets.entrySet())rows.put(new JSONObject().put("trialId",e.getKey()).put("status",e.getValue()));if(duplicateAsset)rows.put(rows.optJSONObject(0));if(assetMode.equals("owner"))rows.optJSONObject(0).put("userId","other");if(assetMode.equals("rootOwner"))r.put("userId","other");r.put("trialInfoList",rows);if(assetQueries==2){if(assetMode.equals("uid"))UserIdMap.uid="other";if(assetMode.equals("day"))MyUtils.day++;if(assetMode.equals("cancel"))TimeUtil.cancel=true;}}
  else if(method.equals("alipay.yebprod.promo.yebTrial.active")){activations++;String target=p.optString("couponId");assert p.optString("type").equals("YEB_TRIAL")&&RuntimeInfo.data.containsKey(RECEIPT);if(p.has("equityType")){assert p.optString("equityType").equals("voucher")&&target.equals("equity\"\\id");voucherActivations++;assert exchanges==1&&balance.signum()==0;JSONObject receipt=new JSONObject(RuntimeInfo.data.get(RECEIPT));assert receipt.optString("stage").equals("activateVoucher")&&receipt.optString("equityNo").equals(target);if(voucherFields)r.put("amount",new JSONObject().put("amount",badVoucherAmount?"-1":"1000.00")).put("confirmDate",badVoucherDate?"bad\nheader":"2026-10-09").put("profitDate","2026-10-10");if(voucherInterrupt.equals("uid"))UserIdMap.uid="other";if(voucherInterrupt.equals("day"))MyUtils.day++;if(voucherInterrupt.equals("cancel"))TimeUtil.cancel=true;}else{assert target.equals(trial)||extraAssets.containsKey(target);activatedIds.add(target);if(apply){if(target.equals(trial))state="A";else extraAssets.put(target,"A");}}after();}
  else throw new AssertionError("unexpected "+method);
  if(badAck&&conversions+exchanges+activations>0)r.put("success",false);return r.toString();}
  static void after(){if(switchUid)UserIdMap.uid="other";if(crossDay)MyUtils.day++;if(cancelAfter)TimeUtil.cancel=true;}}
 static void reset(){assetMode="";allOwned=newVoucher=singleAttempt=badVoucherAmount=badVoucherDate=false;voucherFields=true;voucherActivations=0;voucherInterrupt="";extraAssets.clear();activatedIds.clear();RuntimeInfo.data.clear();RuntimeInfo.writable=true;Status.flags.clear();Status.counts.clear();Log.ok=conversions=exchanges=activations=voucherQueries=balanceQueries=assetQueries=0;MyUtils.day=7;UserIdMap.uid="self";TimeUtil.cancel=false;TimeUtil.slept=0;count=2;remaining=0;delay=0;balance=new BigDecimal("12.25");threshold=new BigDecimal("10");state="I";apply=true;badAck=mixed=badCount=missingCount=unstable=malformed=wrongBalance=missingEquity=duplicateAsset=badStatus=switchUid=crossDay=cancelAfter=negativeDelay=noReduction=false;}
 static void run(boolean convertAll,int voucherBudget,Set<String> campaigns,int amountBudget,Set<String> trialIds,int activationBudget){YebVouchers.run(convertAll,voucherBudget,campaigns,amountBudget,trialIds,activationBudget,allOwned,newVoucher);}static void convert(int budget){run(true,budget,Set.of(),0,Set.of(),0);}static void exchange(int budget){run(false,0,Set.of(CAMPAIGN),budget,Set.of(),0);}static void activate(int budget){run(false,0,Set.of(),0,Set.of(trial),budget);}static boolean pending(){return RuntimeInfo.data.containsKey(RECEIPT);}
 static String failedActivation(){reset();apply=false;activate(1);assert activations==1&&pending();assetQueries=0;return RuntimeInfo.data.get(RECEIPT);}
 public static void main(String[] args){
  reset();delay=2500;convert(2);assert conversions==1&&count==0&&Log.ok==1&&!pending()&&voucherQueries==3&&TimeUtil.slept>=2500;
  reset();run(false,10,Set.of(),10,Set.of(),10);assert conversions+exchanges+activations==0&&voucherQueries+balanceQueries+assetQueries==0;convert(0);assert voucherQueries==0;
  reset();run(true,1,Set.of(CAMPAIGN),13,Set.of(trial),1);assert conversions==0&&exchanges==1&&activations==1;
  reset();balance=BigDecimal.ZERO;run(false,0,Set.of(CAMPAIGN),13,Set.of(trial),1);assert exchanges==0&&activations==1;
  reset();convert(1);assert conversions==0;
  reset();count=0;convert(5);assert conversions==0;
  reset();badCount=true;convert(5);assert conversions==0;
  reset();missingCount=true;convert(5);assert conversions==0;
  reset();unstable=true;convert(5);assert conversions==0;
  reset();malformed=true;convert(5);assert conversions==0;
  reset();RuntimeInfo.writable=false;convert(5);assert conversions==0;
  reset();RuntimeInfo.data.put("memberYebVoucherBudget::convert","{\"day\":2026280,\"used\":0,\"attemptIds\":[\"corrupt\"]}");convert(5);assert conversions==0;
  for(long invalidDay:new long[]{-1,0,1,2026000,2026367,2025366,2026281,10000001,Long.MAX_VALUE}){
   for(String stage:new String[]{"convert","exchange","activate"}){
    reset();String key="memberYebVoucherBudget::"+stage;
    String bad=new JSONObject().put("day",invalidDay).put("used",0).put("attemptIds",new JSONArray()).toString();
    RuntimeInfo.data.put(key,bad);
    if(stage.equals("convert"))convert(5);else if(stage.equals("exchange"))exchange(13);else activate(1);
    assert conversions+exchanges+activations==0:stage+" accepted invalid ledger day "+invalidDay;
    assert RuntimeInfo.data.get(key).equals(bad)&&!pending():"invalid ledger was overwritten";
   }
  }
  for(int validOldDay:new int[]{2024366,2025365,2026279}){
   reset();RuntimeInfo.data.put("memberYebVoucherBudget::convert",new JSONObject().put("day",validOldDay).put("used",0).put("attemptIds",new JSONArray()).toString());
   convert(5);assert conversions==1: "valid old ledger rejected "+validOldDay;
  }
  reset();mixed=true;convert(5);assert conversions==1&&pending()&&Log.ok==0;MyUtils.day++;convert(5);assert conversions==1&&pending();
  reset();noReduction=true;convert(5);assert conversions==1&&pending()&&Log.ok==0;
  reset();remaining=1;convert(5);assert conversions==1&&Log.ok==1&&!pending();Status.flags.clear();Status.counts.clear();convert(5);assert conversions==1;
  reset();negativeDelay=true;convert(5);assert conversions==1&&pending()&&voucherQueries==2;
  reset();delay=300001;convert(5);assert conversions==1&&pending()&&voucherQueries==2&&TimeUtil.slept==0;
  reset();badAck=true;convert(5);assert conversions==1&&pending()&&Log.ok==0;
  reset();switchUid=true;convert(5);assert conversions==1&&pending()&&Log.ok==0;
  reset();crossDay=true;convert(5);assert conversions==1&&pending()&&Log.ok==0;
  reset();cancelAfter=true;try{convert(5);throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}assert conversions==1&&pending();
  reset();exchange(13);assert exchanges==1&&balance.signum()==0&&Log.ok==1&&!pending();assert RuntimeInfo.data.values().stream().anyMatch(s->s.contains("equityNo")&&s.contains("CP152735172"));assert activations==0;
  reset();exchange(12);assert exchanges==0;
  reset();threshold=new BigDecimal("20");exchange(100);assert exchanges==0;
  reset();run(false,0,Set.of("unknown"),100,Set.of(),0);assert exchanges==0;
  reset();unstable=true;exchange(100);assert exchanges==0;
  reset();wrongBalance=true;exchange(100);assert exchanges==1&&pending()&&Log.ok==0;MyUtils.day++;exchange(100);assert exchanges==1&&pending();
  reset();missingEquity=true;exchange(100);assert exchanges==1&&pending()&&Log.ok==0;
  reset();badAck=true;exchange(100);assert exchanges==1&&pending()&&Log.ok==0;
  reset();exchange(100);Status.flags.clear();Status.counts.clear();balance=new BigDecimal("12.25");exchange(100);assert exchanges==1;
  reset();activate(1);assert activations==1&&state.equals("A")&&Log.ok==1&&!pending();
  state="I";Status.flags.clear();Status.counts.clear();activate(10);assert activations==1;
  reset();activate(0);assert activations==0&&assetQueries==0;
  reset();state="A";activate(1);assert activations==0;
  reset();duplicateAsset=true;activate(1);assert activations==0;
  reset();RuntimeInfo.data.put("memberYebVoucherBudget::activate","{\"day\":2026280,\"used\":0.5,\"attemptIds\":[]}");activate(1);assert activations==0;
  reset();badStatus=true;activate(1);assert activations==0;
  reset();unstable=true;activate(1);assert activations==0;
  reset();apply=false;activate(1);assert activations==1&&pending()&&Log.ok==0;MyUtils.day++;activate(1);assert activations==1;
  reset();cancelAfter=true;try{activate(1);throw new AssertionError("activation cancel swallowed");}catch(TaskCancelledException expected){}assert activations==1&&pending();
  reset();RuntimeInfo.data.put(RECEIPT,"malformed");run(true,10,Set.of(CAMPAIGN),100,Set.of(trial),10);assert conversions+exchanges+activations==0;
  reset();allOwned=true;extraAssets.put("owned-one","I");extraAssets.put("owned-two","A");run(false,0,Set.of(),0,Set.of(),2);assert activations==2&&state.equals("A")&&extraAssets.get("owned-one").equals("A")&&!activatedIds.contains("owned-two")&&Log.ok==2&&!pending();
  reset();allOwned=true;extraAssets.put("owned-one","I");run(false,0,Set.of(),0,Set.of("not-owned"),1);assert activations==1&&state.equals("A")&&extraAssets.get("owned-one").equals("I");Status.flags.clear();Status.counts.clear();run(false,0,Set.of(),0,Set.of(),1);assert activations==1;MyUtils.day++;run(false,0,Set.of(),0,Set.of(),1);assert activations==2&&extraAssets.get("owned-one").equals("A");
  reset();allOwned=true;unstable=true;run(false,0,Set.of(),0,Set.of(),2);assert activations==0;
  reset();allOwned=true;duplicateAsset=true;run(false,0,Set.of(),0,Set.of(),2);assert activations==0;
  reset();allOwned=true;run(false,0,Set.of(),0,Set.of(),0);assert activations==0&&assetQueries==0;
  reset();newVoucher=true;run(false,0,Set.of(),100,Set.of(),2);assert exchanges==0&&activations==0&&assetQueries==0;
  reset();newVoucher=true;run(false,0,Set.of(CAMPAIGN),13,Set.of(),0);assert exchanges==1&&activations==0&&!pending();
  reset();newVoucher=true;run(false,0,Set.of(CAMPAIGN),13,Set.of(trial),2);assert exchanges==1&&voucherActivations==1&&activations==1&&assetQueries==0&&Log.ok==1&&pending();JSONObject vr=new JSONObject(RuntimeInfo.data.get(RECEIPT));assert vr.optString("state").equals("ACTIVATION_ACKNOWLEDGED_TERMINAL_UNVERIFIED")&&vr.optString("activationAmount").equals("1000.00")&&vr.optString("confirmDate").equals("2026-10-09");int queries=balanceQueries;Status.flags.clear();Status.counts.clear();MyUtils.day++;run(true,10,Set.of(CAMPAIGN),100,Set.of(trial),10);assert exchanges==1&&activations==1&&balanceQueries==queries;
  for(String failure:new String[]{"missing","amount","date","wrongBalance","missingEquity","ack","uid","day","cancel"}){reset();newVoucher=true;switch(failure){case "missing"->voucherFields=false;case "amount"->badVoucherAmount=true;case "date"->badVoucherDate=true;case "wrongBalance"->wrongBalance=true;case "missingEquity"->missingEquity=true;case "ack"->badAck=true;case "uid"->switchUid=true;case "day"->crossDay=true;case "cancel"->cancelAfter=true;}try{run(false,0,Set.of(CAMPAIGN),13,Set.of(),2);}catch(TaskCancelledException expected){assert failure.equals("cancel");}assert pending():failure;assert voucherActivations==(Set.of("missing","amount","date").contains(failure)?1:0):failure;assert !RuntimeInfo.data.get(RECEIPT).contains("ACTIVATION_ACKNOWLEDGED_TERMINAL_UNVERIFIED"):failure;}
  for(String interruption:new String[]{"uid","day","cancel"}){
   reset();newVoucher=allOwned=true;voucherInterrupt=interruption;boolean cancelled=false;
   try{run(false,0,Set.of(CAMPAIGN),13,Set.of(trial),2);}catch(TaskCancelledException expected){cancelled=true;}
   assert cancelled==interruption.equals("cancel"):"voucher cancellation was swallowed";
   assert exchanges==1&&activations==1&&voucherActivations==1&&balanceQueries==3&&assetQueries==0&&Log.ok==1;
   JSONObject receipt=new JSONObject(RuntimeInfo.data.get(RECEIPT));
   assert receipt.optString("stage").equals("activateVoucher")&&receipt.optString("state").equals("SUBMITTED_UNCONFIRMED")&&!receipt.has("activationAmount");
   assert receipt.optString("equityNo").equals("equity\"\\id")&&new JSONObject(RuntimeInfo.data.get("memberYebVoucherBudget::activate")).optInt("used")==1;
   UserIdMap.uid="self";TimeUtil.cancel=false;MyUtils.day++;Status.flags.clear();Status.counts.clear();voucherInterrupt="";
   run(true,10,Set.of(CAMPAIGN),100,Set.of(trial),10);
   assert conversions==0&&exchanges==1&&activations==1&&voucherQueries==0&&balanceQueries==3&&assetQueries==0&&pending():"interrupted voucher attempt retried across day";
  }
  String held=failedActivation();state="A";MyUtils.day++;String budgetBefore=RuntimeInfo.data.get("memberYebVoucherBudget::activate");run(true,10,Set.of(CAMPAIGN),100,Set.of(trial),10);assert activations==1&&conversions+exchanges==0&&assetQueries==2&&voucherQueries+balanceQueries==0&&Log.ok==1&&!pending();assert RuntimeInfo.data.get("memberYebVoucherBudget::activate").equals(budgetBefore);assert RuntimeInfo.data.values().stream().anyMatch(v->v.contains("beforeStatus")&&v.contains("\"state\":\"A\""));
  failedActivation();allOwned=true;state="A";run(false,0,Set.of(),0,Set.of(),1);assert activations==1&&assetQueries==2&&!pending()&&Log.ok==1;
  for(String failure:new String[]{"pending","unstable","malformed","duplicate","badStatus","owner","rootOwner","uid","day","cancel","persist"}){String original=failedActivation();state="A";switch(failure){case "pending"->state="I";case "unstable"->unstable=true;case "malformed"->malformed=true;case "duplicate"->duplicateAsset=true;case "badStatus"->badStatus=true;case "persist"->RuntimeInfo.writable=false;default->assetMode=failure;}try{activate(1);assert !failure.equals("cancel");}catch(TaskCancelledException expected){assert failure.equals("cancel");}assert activations==1&&pending()&&Log.ok==0&&RuntimeInfo.data.get(RECEIPT).equals(original):failure;}
  for(String field:new String[]{"stage","uid","day","beforeStatus","trialId","extra","missingUid"}){String original=failedActivation();JSONObject receipt=new JSONObject(original);switch(field){case "stage"->receipt.put(field,"activateVoucher");case "uid"->receipt.put(field,"other");case "day"->receipt.put(field,2099001);case "beforeStatus"->receipt.put(field,"A");case "trialId"->receipt.put(field,"another");case "extra"->receipt.put("equityNo",trial);case "missingUid"->receipt.remove("uid");}String corrupt=receipt.toString();RuntimeInfo.data.put(RECEIPT,corrupt);state="A";activate(1);assert assetQueries==0&&activations==1&&pending()&&Log.ok==0&&RuntimeInfo.data.get(RECEIPT).equals(corrupt):field;}
  failedActivation();state="A";run(false,0,Set.of(),0,Set.of("not-selected"),1);assert assetQueries==0&&activations==1&&pending();activate(0);assert assetQueries==0&&pending();
  failedActivation();state="A";run(true,10,Set.of(CAMPAIGN),100,Set.of(),0);assert assetQueries+voucherQueries+balanceQueries==0&&activations==1&&conversions+exchanges==0&&pending();
  reset();newVoucher=true;run(false,0,Set.of(CAMPAIGN),13,Set.of(trial),2);int total=activations;String voucherReceipt=RuntimeInfo.data.get(RECEIPT);allOwned=true;state="A";activate(2);assert activations==total&&assetQueries==0&&pending()&&RuntimeInfo.data.get(RECEIPT).equals(voucherReceipt):"voucher cannot be recovered through trialId status";
  System.out.println("PASS production YEB all-voucher stable batch/budget/all-result/delay/readback, explicit public exchange balance/equity receipt, selected/all-owned trialId->A activation and read-only old-receipt recovery, equityNo/voucher-only submission with unresolved ACK receipt, single-attempt writes and durable account/day/cancel/unknown guards");
 }
}
'''
helper = SOURCE/'model/task/antMember/YebVouchers.java'
text=helper.read_text(encoding='utf-8');body='static '+text[text.index('final class YebVouchers'):]
code=code.replace('@@HELPER@@',body).replace('@@GUARD@@',method('rpc/intervallimit/RpcRequestGuard.java','public static boolean isFailure(')).replace('@@MAIN@@',method('model/task/antMember/AntMemberRpcCall.java','public static String queryYebExpGoldMain('))
cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/modules-2/files-2.1/org.json/json'
jar=sorted(p for p in cache.glob('*/*/json-*.jar') if not p.name.endswith(('-sources.jar','-javadoc.jar')))[-1]
with tempfile.TemporaryDirectory(prefix='sesame-yeb-vouchers-') as tmp:
    source=Path(tmp)/'YebVouchersCheck.java';source.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',tmp,str(source),str(SOURCE/'util/TaskCancelledException.java')],check=True)
    subprocess.run(['java','-ea','-cp',tmp+os.pathsep+str(jar),'YebVouchersCheck'],check=True)
