"""Compile real reading client/task/auth helper; fake host services and HTTPS, no account/device/network."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = root / "app/src/main/java"
pkg = "io.github.aw1y2z.sesame"
cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1/org.json/json"
jars = sorted(cache.glob("*/*/json-*.jar"))
assert jars, "Build first to cache org.json"
stubs = {
    "android.os.SystemClock": '''public class SystemClock {
        public static long now; public static long elapsedRealtime(){return now;}}
    ''',
    pkg + ".util.MyUtils": '''import org.json.*; public class MyUtils {
        public static JSONObject newJSONObject(){return new JSONObject();}
        public static JSONObject newJSONObject(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    }''',
    pkg + ".util.Log": '''import java.util.*; public class Log {
        public static List<String> messages=new ArrayList<>(), runtime=new ArrayList<>(), forest=new ArrayList<>();
        public static void forest(String s){messages.add(s);forest.add(s);} public static void record(String s){messages.add(s);runtime.add(s);}
        public static void error(String s){messages.add(s);runtime.add(s);}
    }''',
    pkg + ".util.Status": '''import java.util.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
        public class Status {public static Set<String> flags=new HashSet<>();
        public static boolean hasFlagToday(String k){return flags.contains(UserIdMap.uid+k);}
        public static void flagToday(String k,String uid){assert uid.equals(UserIdMap.uid); flags.add(uid+k);}}
    ''',
    pkg + ".util.TimeUtil": '''import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
        public class TimeUtil {public static void sleep(long ms){assert !TaskLifecycle.isIdle();
        assert TaskLifecycle.freezeIfIdle()==null; if(RunGeneration.isStale())throw new TaskCancelledException();
        android.os.SystemClock.now+=ms;}}
    ''',
    pkg + ".util.idMap.UserIdMap": '''public class UserIdMap {public static String uid="A";
        public static String getCurrentUid(){return uid;}}
    ''',
    pkg + ".data.RuntimeInfo": '''import java.util.*; import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
        public class RuntimeInfo {public static Map<String,RuntimeInfo> accounts=new HashMap<>();
        public Map<String,String> data=new HashMap<>();
        public static RuntimeInfo getInstance(){return accounts.computeIfAbsent(UserIdMap.uid,k->new RuntimeInfo());}
        public String getString(String k){return data.getOrDefault(k,"");}
        public void put(String k,Object v){data.put(k,v.toString());}}
    ''',
    pkg + ".hook.AlipayMiniMarkHelper": '''public class AlipayMiniMarkHelper {
        public static String getAlipayMiniMark(String a,String v){return "mini-mark";}}
    ''',
    pkg + ".hook.ApplicationHook": '''public class ApplicationHook {
        public static String getAlipayVersion(){return "10.6.58.8000";}}
    ''',
    pkg + ".util.XHelpers": '''import java.lang.reflect.*; public class XHelpers {
        public static Class<?> findClass(String n,ClassLoader l)throws Exception{return Class.forName(n,true,l);}
        public static Object newInstance(Class<?> c)throws Exception{return c.getDeclaredConstructor().newInstance();}
        static Object invoke(Class<?> c,Object o,String n,Object[] a)throws Exception{
            for(Method m:c.getMethods())if(m.getName().equals(n)&&m.getParameterCount()==a.length)return m.invoke(o,a);
            throw new NoSuchMethodException(n);}
        public static Object callMethod(Object o,String n,Object...a)throws Exception{return invoke(o.getClass(),o,n,a);}
        public static Object callStaticMethod(Class<?> c,String n,Object...a)throws Exception{return invoke(c,null,n,a);}}
    ''',
    "com.alibaba.ariver.permission.api.proxy.Oauth2AuthCodeService": "public interface Oauth2AuthCodeService {}",
    "com.alibaba.ariver.kernel.common.RVProxy": '''public class RVProxy {
        public static Object service; public static int calls;
        public static Object get(Class<?> c){assert c.getName().endsWith("Oauth2AuthCodeService"); calls++; return service;}}
    ''',
    "com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel": '''import java.util.*;
        public class AuthSkipRequestModel {public String app; public List<String> scopes; public Map<String,String> info;
        public void setAppId(String s){app=s;} public void setScopeNicks(List<String> s){scopes=s;}
        public void setAppExtInfo(Map<String,String> s){info=s;} public void setExtInfo(Map<String,String> s){}
        public void setCurrentPageUrl(String s){assert s.startsWith("https://"+app+".");}
        public void setFromSystem(String s){} public void setState(String s){} public void setIsvAppId(String s){}}
    ''',
}
test = r'''
import java.io.*; import java.net.*; import java.nio.charset.StandardCharsets; import java.util.*; import java.util.concurrent.atomic.AtomicLong;
import org.json.*;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.model.task.forestRead.*;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import com.alibaba.ariver.kernel.common.RVProxy;
import com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel;
public class ForestReadCheck {
    static boolean noCode; static int authCalls, reads, opened, closed; static String networkFailure="";
    static AtomicLong generation=new AtomicLong(); static boolean cancel;
    public static class HostService implements com.alibaba.ariver.permission.api.proxy.Oauth2AuthCodeService {
        public Object getAuthSkipResult(String platform,Object page,AuthSkipRequestModel request){
            assert !TaskLifecycle.isIdle(); assert TaskLifecycle.freezeIfIdle()==null;
            assert platform.equals("AP") && page==null;
            assert request.app.equals(ReadForestRpcCall.APP_ID); assert request.scopes.equals(List.of("auth_base"));
            assert request.info.get("clientAppId").equals(request.app); authCalls++;
            return noCode ? null : new SkipResult();
        }
    }
    public static class SkipResult {public Object getAuthExecuteResult(){return new ExecuteResult();}}
    public static class ExecuteResult {public String getAuthCode(){return "secret-code-"+UserIdMap.uid;}}
    static class Reply {String path,raw; int status; long elapsed; Reply(String p,String r,int s){path=p;raw=r;status=s;}}
    static Queue<Reply> queue=new ArrayDeque<>(); static List<MockConnection> connections=new ArrayList<>();
    static class MockConnection extends HttpURLConnection {
        Reply reply; ByteArrayOutputStream sent=new ByteArrayOutputStream(); boolean disconnected;
        MockConnection(URL u){super(u); reply=queue.remove(); assert u.getHost().equals("m.zhangwenwh.com");
            assert u.getPath().equals("/api"+reply.path):u; opened++; connections.add(this);}
        public void connect(){} public boolean usingProxy(){return false;}
        public OutputStream getOutputStream(){return sent;}
        public int getResponseCode()throws IOException{
            android.os.SystemClock.now+=reply.elapsed;
            assert !getInstanceFollowRedirects(); assert getConnectTimeout()==10000 && getReadTimeout()==20000;
            assert !TaskLifecycle.isIdle(); assert TaskLifecycle.freezeIfIdle()==null;
            assert getRequestProperty("alipayminimark").equals("mini-mark");
            switch(networkFailure){
                case "timeout":throw new SocketTimeoutException("secret-token-A");
                case "DNS":throw new UnknownHostException("secret-code-A");
                case "TLS":throw new javax.net.ssl.SSLException("secret-code-A");
                case "connect":throw new ConnectException("secret-token-A");
            }
            if(reply.path.equals("/authorization")){
                assert method.equals("POST"); assert getRequestProperty("Authorization")==null;
                JSONObject b=new JSONObject(sent.toString(StandardCharsets.UTF_8));
                assert b.optString("auth_code").equals("secret-code-"+UserIdMap.uid);
                assert b.optString("app_id").equals(ReadForestRpcCall.APP_ID) && b.optString("cid").equals("7001");
            }else assert getRequestProperty("Authorization").equals("Bearer secret-token-"+UserIdMap.uid);
            if(reply.path.equals("/user/updateWel")){
                JSONObject b=new JSONObject(sent.toString(StandardCharsets.UTF_8)); reads++;
                assert b.optInt("time")>=333 && b.optInt("time")<=1000;
                assert !b.optString("book_id").equals("paid"); assert b.opt("chapter_id") instanceof Number;
            }
            return reply.status;
        }
        public InputStream getInputStream(){return new ByteArrayInputStream(reply.raw.getBytes(StandardCharsets.UTF_8)){
            public void close()throws IOException{super.close(); closed++; if(cancel)generation.incrementAndGet();}};}
        public void disconnect(){disconnected=true;}
    }
    static void add(String p,Object data){queue.add(new Reply(p,new JSONObject().put("code",2000).put("data",data).toString(),200));}
    static void raw(String p,String r,int s){queue.add(new Reply(p,r,s));}
    static void slowLast(long ms){((ArrayDeque<Reply>)queue).getLast().elapsed=ms;}
    static void login(){add("/authorization",new JSONObject().put("token","secret-token-"+UserIdMap.uid));}
    static void energy(int current,int total){add("/user/forestPro",new JSONObject().put("current",current).put("total",total));}
    static JSONObject book(String id,String price){return new JSONObject().put("book_id",id).put("price",price).put("name",id);}
    static void index(JSONObject...books){add("/index/index",new JSONArray().put(new JSONObject().put("block_resource",new JSONArray(Arrays.asList(books)))));}
    static void chapter(int id,Object next){add("/book/chapter",new JSONObject().put("id",id).put("next_id",next).put("name","chapter"));}
    static void update(){add("/user/updateWel",new JSONObject());add("/book/set_read_recently",new JSONObject());}
    static void reset(){assert queue.isEmpty():queue.size(); queue.clear(); connections.clear(); Status.flags.clear();
        RuntimeInfo.accounts.clear(); Log.messages.clear(); Log.runtime.clear(); Log.forest.clear(); UserIdMap.uid="A"; noCode=false; reads=0; opened=0; closed=0; cancel=false;
        RVProxy.service=new HostService(); generation.set(0); networkFailure=""; android.os.SystemClock.now=0;}
    static void run(){ReadForestTask.execute();assert queue.isEmpty():queue.size(); assert TaskLifecycle.isIdle();
        for(MockConnection c:connections)assert c.disconnected;
        for(String m:Log.messages)assert !m.contains("secret-code") && !m.contains("secret-token");}
    public static void main(String[] args)throws Exception{
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https") ? new URLStreamHandler(){
            protected URLConnection openConnection(URL u){return new MockConnection(u);}} : null);
        AuthCodeHelper.init(ForestReadCheck.class.getClassLoader());
        reset(); login(); energy(0,10); index(book("paid","1.00"),book("one","0.00"),book("one","0.00"),book("two","0.00"));
        chapter(1,JSONObject.NULL); update(); energy(5,10); chapter(2,0); update(); energy(10,10); run();
        assert reads==2 && Status.flags.size()==1;
        assert Log.runtime.stream().anyMatch(s->s.contains("成功，服务端确认今日满额 10/10"));
        assert Log.runtime.stream().anyMatch(s->s.contains("登录成功，已自动获取 Token"));
        assert Log.runtime.containsAll(Log.forest);
        assert new JSONObject(RuntimeInfo.getInstance().getString("readForest.finishedBooks")).has("one");
        int calls=authCalls; run(); assert authCalls==calls;
        UserIdMap.uid="B"; login(); energy(10,10); run(); assert Status.flags.size()==2;
        UserIdMap.uid="A"; Status.flags.clear(); login(); energy(10,10); run(); assert authCalls==calls+2;
        System.out.println("PASS host proxy auth_base, free-book dedup, completion, account/day state and secret isolation");
        for(Object[] pair:List.of(new Object[]{"10","10"},new Object[]{10,"10"},new Object[]{"10",10},new Object[]{10.0,10.0},
                new Object[]{" 0010 "," 0010 "},new Object[]{"2147483647","2147483647"})){
            reset();login();add("/user/forestPro",new JSONObject().put("current",pair[0]).put("total",pair[1]));
            run();assert Status.flags.size()==1 && reads==0 : "valid integer-string progress rejected";
        }
        reset();login();add("/user/forestPro",new JSONObject().put("current","0").put("total","10"));
        index(book("one","0.00"));chapter(1,0);update();
        add("/user/forestPro",new JSONObject().put("current","10").put("total","10"));run();
        assert reads==1 && Status.flags.size()==1;
        assert Log.runtime.stream().anyMatch(s->s.contains("成功获得能量 +10"));
        System.out.println("PASS integer-string/mixed progress and reading-to-full flow");
        reset(); RuntimeInfo.getInstance().put("readForest.finishedBooks","{\"one\":\"done\"}");
        login(); energy(0,10); index(book("one","0.00"),book("two","0.00")); chapter(2,0); update(); energy(1,10); run();
        assert reads==1 && Status.flags.isEmpty();
        reset(); login(); energy(0,100); index(book("one","0.00"));
        for(int i=1;i<=5;i++){chapter(i,i+1); update(); energy(0,100);} run(); assert reads==5 && Status.flags.isEmpty();
        reset(); login(); energy(0,100); index(book("one","0.00"));
        chapter(1,1); update(); energy(1,100); chapter(1,1); run(); assert reads==1 && Status.flags.isEmpty();
        reset(); login(); energy(59,150); index(book("one","0.00"));
        for(int i=1;i<=91;i++){chapter(i,i+1); update(); energy(59+i,150);}
        ReadForestTask.execute();
        assert reads==91 && Status.flags.size()==1 : "reading stopped before 150 despite continued progress: "+reads;
        assert queue.isEmpty() && TaskLifecycle.isIdle();
        assert Log.runtime.stream().anyMatch(s->s.contains("成功，服务端确认今日满额 150/150"));
        System.out.println("PASS continued reading beyond 30 chapters from 59/150 to full");
        reset();login();energy(0,150);index(book("one","0.00"));
        for(int i=1;i<=36;i++){chapter(i,i+1);update();energy(Math.min(i,31),150);}
        run();assert reads==36 && Status.flags.isEmpty();
        assert Log.runtime.stream().anyMatch(s->s.contains("连续 5 章能量未增加") && s.contains("31/150"));
        System.out.println("PASS stagnant stop still works after the former chapter limit");
        reset(); login(); energy(0,100); index(book("one","0.00"));slowLast(15*60_000L);
        run();assert reads==0 && Status.flags.isEmpty();
        assert Log.runtime.stream().anyMatch(s->s.contains("15 分钟") && s.contains("尚未满额"));
        reset(); login(); energy(0,100); index(book("one","0.00"));chapter(1,2);update();energy(1,100);
        slowLast(15*60_000L);run();assert reads==1 && Status.flags.isEmpty();
        assert Log.runtime.stream().anyMatch(s->s.contains("15 分钟") && s.contains("1/100"));
        reset();login();energy(0,1);index(book("one","0.00"));chapter(1,2);update();energy(1,1);
        slowLast(15*60_000L);run();assert reads==1 && Status.flags.size()==1;
        assert Log.runtime.stream().anyMatch(s->s.contains("成功，服务端确认今日满额 1/1"));
        System.out.println("PASS time budget prevents further chapter requests and completion wins at the boundary");
        System.out.println("PASS persisted finished books, stagnant stop and chapter cycle");
        for(String p:List.of("{}","bad","{\"code\":2000,\"data\":{}}","{\"code\":2000,\"data\":{\"current\":0,\"total\":0}}",
                "{\"code\":2000,\"data\":{\"current\":\"ten\",\"total\":10}}","{\"code\":2000,\"data\":{\"current\":-1,\"total\":10}}")){
            reset(); login(); raw("/user/forestPro",p,200); run(); assert reads==0 && Status.flags.isEmpty();
            assert Log.runtime.stream().anyMatch(s->s.contains("查询能量进度失败") && s.contains("原因="));}
        for(Object value:List.of("10.5","1e2","-1","+10","2147483648","","secret-token-A",1.5,2147483648L,
                new java.math.BigDecimal("9.999999999999999999999"),true,
                JSONObject.NULL,new JSONObject().put("token","secret-token-A"),new JSONArray().put("secret-token-A"))){
            reset();login();add("/user/forestPro",new JSONObject().put("current",value).put("total",10));
            run();assert reads==0 && Status.flags.isEmpty();
            assert Log.runtime.stream().anyMatch(s->s.contains("字段类型") && s.contains("current="));
        }
        reset();login();add("/user/forestPro",new JSONObject().put("current","0").put("total","0"));
        run();assert reads==0 && Status.flags.isEmpty();
        reset();login();add("/user/forestPro",new JSONArray().put("secret-token-A"));run();
        assert reads==0 && Status.flags.isEmpty();
        assert Log.runtime.stream().anyMatch(s->s.contains("字段类型") && s.contains("data=数组"));
        System.out.println("PASS malformed/overflow/zero-limit progress remains stopped and diagnostics do not leak values");
        for(String p:List.of("{\"code\":\"2000\",\"data\":{\"token\":\"x\"}}","{\"code\":2000,\"data\":{\"token\":true}}",
                "{\"code\":2000,\"data\":{\"token\":\"\"}}")){
            reset(); raw("/authorization",p,200); run(); assert Status.flags.isEmpty();
            assert Log.runtime.stream().anyMatch(s->s.contains("获取/刷新 Token：支付宝"));}
        for(int status:List.of(302,401,500)){
            reset(); login(); raw("/user/forestPro","{}",status); run(); assert Status.flags.isEmpty();
            assert Log.runtime.stream().anyMatch(s->s.contains("HTTP "+status));
            if(status==401)assert Log.runtime.stream().anyMatch(s->s.contains("获取/刷新 Token"));}
        for(String endpoint:List.of("/user/updateWel","/book/set_read_recently")){
            reset(); login(); energy(0,10); index(book("one","0.00")); chapter(1,0);
            if(endpoint.endsWith("set_read_recently"))add("/user/updateWel",new JSONObject());
            raw(endpoint,"{\"code\":4000}",200);run();assert Status.flags.isEmpty() && RuntimeInfo.getInstance().data.isEmpty();}
        reset(); login(); energy(0,10); index(book("one","0.00")); add("/book/chapter",new JSONObject().put("id",1));
        run(); assert reads==0 && RuntimeInfo.getInstance().data.isEmpty();
        System.out.println("PASS malformed/status/type failures, 0/0, token validation, POST failures and no false daily completion");
        reset(); noCode=true; run(); assert opened==0;
        assert Log.runtime.stream().anyMatch(s->s.contains("授权失败") && s.contains("宿主未返回授权结果"));
        assert Log.runtime.stream().anyMatch(s->s.contains("无需手动复制或填写"));
        reset(); RVProxy.service=null; run(); assert opened==0;
        assert Log.runtime.stream().anyMatch(s->s.contains("授权失败") && s.contains("服务未就绪"));
        reset(); TaskLifecycle.Freeze owner=TaskLifecycle.freezeIfIdle(); run(); assert opened==0; TaskLifecycle.thaw(owner);
        reset(); UserIdMap.uid=""; run(); assert opened==0;
        reset(); int proxyCalls=RVProxy.calls; assert AuthCodeHelper.getAuthCode("bad/id")==null; assert RVProxy.calls==proxyCalls;
        reset(); login(); cancel=true; RunGeneration old=RunGeneration.bind(0,generation::get);
        try{ReadForestTask.execute();throw new AssertionError("cancel swallowed");}catch(TaskCancelledException expected){}
        finally{RunGeneration.restore(old);} assert queue.isEmpty() && Status.flags.isEmpty() && TaskLifecycle.isIdle();
        assert connections.get(0).disconnected && closed==1;
        reset(); login(); Thread.currentThread().interrupt();
        try{ReadForestTask.execute();throw new AssertionError("interrupt swallowed");}catch(TaskCancelledException expected){}
        finally{Thread.interrupted();} queue.clear(); assert opened==0 && TaskLifecycle.isIdle();
        System.out.println("PASS auth unavailable, invalid app/uid, freeze admission, cancellation and stream cleanup");
        reset(); login(); raw("/user/forestPro","{\"code\":4001,\"msg\":\"token已过期 token=secret-token-A auth_code=secret-code-A\"}",200);
        run(); assert Log.runtime.stream().anyMatch(s->s.contains("4001") && s.contains("token已过期"));
        assert Log.runtime.stream().anyMatch(s->s.contains("获取/刷新 Token"));
        assert Log.runtime.containsAll(Log.forest);
        for(String kind:List.of("timeout","DNS","TLS","connect")){
            reset();login();networkFailure=kind;run();
            String expected=kind.equals("timeout")?"请求超时":kind.equals("DNS")?"无法解析":kind.equals("TLS")?"证书验证失败":"无法连接";
            assert Log.runtime.stream().anyMatch(s->s.contains(expected));
        }
        reset(); raw("/authorization",new JSONObject().put("code",4000).put("msg","授权失败 auth_code=secret-code-A alipayminimark=mini-mark").toString(),200);
        run(); assert Log.runtime.stream().anyMatch(s->s.contains("授权失败"));
        for(String s:Log.runtime)assert !s.contains("mini-mark");
        reset(); raw("/authorization",new JSONObject().put("code",4000)
                .put("errorMessage","授权失败 {\"token\":\"unknown-credential\"} auth_code=secret-code-A"+"x".repeat(150)).toString(),200);
        run(); for(String s:Log.runtime)assert !s.contains("unknown-credential") && !s.contains("secret-code");
        System.out.println("PASS runtime/classification status logs, actionable auth/token/HTTP reasons and credential redaction");
    }
}
'''

with tempfile.TemporaryDirectory(prefix="sesame-forest-read-") as temp:
    out = Path(temp)
    for name, body in stubs.items():
        path = out / (name.replace(".", "/") + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package " + name.rsplit(".", 1)[0] + ";\n" + body, encoding="utf-8")
    for relative in ("model/task/forestRead/ReadForestTask", "model/task/forestRead/ReadForestRpcCall",
                     "hook/AuthCodeHelper", "data/task/TaskLifecycle", "util/RunGeneration",
                     "util/TaskCancelledException", "util/RandomUtil", "rpc/intervallimit/RpcFailurePolicy"):
        path = out / (pkg.replace(".", "/") + "/" + relative + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text((source / pkg.replace(".", "/") / (relative + ".java")).read_text(encoding="utf-8"), encoding="utf-8")
    (out / "ForestReadCheck.java").write_text(test, encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", str(jars[-1]), "-d", temp]
                   + [str(p) for p in out.rglob("*.java")], check=True)
    subprocess.run(["java", "-ea", "-cp", temp + os.pathsep + str(jars[-1]), "ForestReadCheck"], check=True, timeout=30)

forest = (source / pkg.replace(".", "/") / "model/task/antForest/AntForestV2.java").read_text(encoding="utf-8")
assert '"readForest", "无纸阅读", true' in forest
assert 'if (readForest.getValue()) {\n                    ReadForestTask.execute();' in forest
print("PASS forest enabled-by-default integration; real production auth/task/HTTP classes exercised")
