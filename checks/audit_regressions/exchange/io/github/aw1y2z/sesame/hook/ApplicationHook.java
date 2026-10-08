package io.github.aw1y2z.sesame.hook;
import org.json.*;
public class ApplicationHook {
 public static int spent,writes;public static boolean unknown,missingDelta;public static Object invalidDelta;
 public static String requestString(String method,String body){return requestString(method,body,3,-1);}
 public static String requestString(String method,String body,int tries,int pause){
  JSONObject args=new JSONArray(body).optJSONObject(0);assert args!=null;
  assert args.has("version")&&args.has("bizType")&&args.has("source");
  if(method.equals("com.alipay.goldenbean.index"))return new JSONObject().put("success",true).put("manureExchangeInfo",new JSONObject().put("farmOpened",true).put("pageOpened",true).put("taobaoBinding",true).put("beanReward","MASTER".equals(args.optString("bizType"))?1:10).put("currentManure",1000).put("effectiveExchangeManure",1000).put("minExchangeAmount",1).put("remainQuota",1000-spent)).toString();
  if(method.equals("com.alipay.goldenbean.sync"))return "{\"success\":false}";
  assert tries==1&&pause==0:"golden bean mutation used default retries: "+method;
  writes++;
  if(method.equals("com.alipay.goldenbean.manureExchange")){
   int n=args.optInt("exchangeBeanAmount");assert n>0;spent+=n;
   if(unknown)return "{}";
   return missingDelta?"{\"success\":true}":new JSONObject().put("success",true).put("beanDelta",invalidDelta==null?n:invalidDelta).toString();
  }
  return "{\"success\":true}";
 }
}
