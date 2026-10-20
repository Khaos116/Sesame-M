package io.github.aw1y2z.sesame.hook;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小程序网络抓包（游戏等小程序数据），移植自上游 1.8.9.1。
 *
 * <p>小程序插件 dex 中的网络类延迟加载，故同时 hook 直接类与 {@code ClassLoader.loadClass}。
 * 只读记录，不修改请求参数与响应内容；开关沿用日志页的「抓包记录」({@code AppConfig.enableDebugLog})，
 * 与 RPC 抓包共用同一开关，可随开关即时装卸。
 */
public final class MiniProgramHook {

    private MiniProgramHook() {
    }

    private static final String MINI_RESP_CLASS =
            "com.alipay.android.phone.event.ariver.network.HttpRequestResponseTrigger";
    private static final String ASYNC_REQUEST_HANDLER_CLASS =
            "com.alipay.android.phone.wallet.aompnetwork.request.AsyncRequestHandler";

    /** 单条日志最大长度，防止走子记录等超长内容刷爆日志 */
    private static final int MAX_LOG_LEN = 20000;
    private static final int MAX_MODEL_ITEMS = 256;

    /** 非业务域名：日志埋点/监控/小程序包静态资源 CDN，命中即不记录 */
    private static final String[] MINI_HOST_BLACKLIST = {
            "hybrid.alipay-eco.com", "xxl-log", "mmstat", "amdc.", "arms",
            "alicdn.com", "tbcdn.cn", "alipayobjects.com", "gtimg.cn"
    };

    /** 静态资源后缀（按 ? 之前的路径判断）：命中即不记录 */
    private static final String[] MINI_STATIC_SUFFIX = {
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico",
            ".svg", ".woff", ".woff2", ".ttf", ".html", ".htm", ".mp4", ".mp3", ".json",
            // 游戏引擎资源：Unity/白鹭的 AssetBundle、贴图清单、骨骼动画等，实测占抓包量 90%+
            ".txt", ".ab", ".dat", ".bin", ".bytes", ".atlas", ".skel", ".plist", ".prefab", ".asset"
    };

    /** 游戏资源目录：命中即不记录（CDN 路径里 Assets/ab 包占绝大多数） */
    private static final String[] MINI_STATIC_PATH = {
            "/assets/", "/res/", "/static/", "/textures/", "/audio/"
    };

    /** 已 hook 过的类名（跨 loadClass 多次回调去重） */
    private static final Set<String> deferredHookedClasses =
            Collections.synchronizedSet(new HashSet<String>());

    /** 已暂存未配对请求：精确 URL -> {时间, 方法行, 请求体}（LRU 上限，防止无响应请求堆积） */
    private static final Map<String, String[]> pendingMiniRequests =
            Collections.synchronizedMap(new LinkedHashMap<String, String[]>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
                    return size() > 128;
                }
            });

    /** 各类实例字段缓存（含父类链），避免网络热路径重复反射 */
    private static final Map<Class<?>, List<Field>> modelFieldsCache = new ConcurrentHashMap<>();

    private static volatile ClassLoader sClassLoader = null;
    private static volatile String sProcessName = "";
    private static volatile boolean sCaptureEnabled;
    private static boolean deferredNetworkHookInstalled;

    /** 已安装的 unhook 句柄，便于「抓包记录」关闭时卸载 */
    private static final List<XC_MethodHook.Unhook> sUnhooks =
            Collections.synchronizedList(new ArrayList<XC_MethodHook.Unhook>());

    /**
     * 安装小程序抓包 hook。由 {@code ApplicationHook} 在宿主进程 attach 后调用。
     * 开关沿用日志页的「抓包记录」，未开启时直接返回。
     */
    public static synchronized void install(ClassLoader classLoader, String processName) {
        sClassLoader = classLoader;
        sProcessName = processName == null ? "" : processName;
        if (!miniDebugEnabled()) {
            return;
        }
        if (!sCaptureEnabled) {
            deferredHookedClasses.clear();
        }
        sCaptureEnabled = true;
        tryHookMiniNetworkClass(MINI_RESP_CLASS);
        tryHookMiniNetworkClass(ASYNC_REQUEST_HANDLER_CLASS);
        installDeferredNetworkHook();
    }

    /** 卸载已安装的 hook；「抓包记录」关闭时调用 */
    public static synchronized void uninstall() {
        sCaptureEnabled = false;
        synchronized (sUnhooks) {
            for (XC_MethodHook.Unhook unhook : sUnhooks) {
                try {
                    unhook.unhook();
                } catch (Throwable ignored) {
                }
            }
            sUnhooks.clear();
        }
        deferredHookedClasses.clear();
        deferredNetworkHookInstalled = false;
        pendingMiniRequests.clear();
        modelFieldsCache.clear();
    }

    private static void tryHookMiniNetworkClass(String className) {
        if (deferredHookedClasses.contains(className)) {
            return;
        }
        try {
            Class<?> clazz = XHelpers.findClassIfExists(className, sClassLoader);
            if (clazz != null) {
                hookMiniNetworkClass(clazz);
            }
        } catch (Throwable ignored) {
            // 类尚未加载，交给 loadClass 延迟监听
        }
    }

    /** 请求暂存：域名白名单过滤后按精确 URL 保存，等待 onHandleResponse 配对（只读，不修改原请求） */
    private static void pendingMiniRequest(String url, String method, String data) {
        if (url == null || url.length() == 0 || !isGameApi(url)) {
            return;
        }
        String body = "";
        if (data != null && data.length() > 0) {
            body = data;
            // rantu 走 application/x-www-form-urlencoded：data=<urlencoded JSON>；
            // gamesapi2 等接口直接是裸 JSON，不能 URLDecoder 解码（'+' 会被误转为空格）
            if (body.startsWith("data=")) {
                body = body.substring(5);
                try {
                    body = URLDecoder.decode(body, "UTF-8");
                } catch (Throwable ignored) {
                }
            }
            body = clip(deepPrettyJson(body));
        }
        // timestamp+sign 使每次请求的完整 URL 唯一，天然可作为请求/响应配对键
        pendingMiniRequests.put(url, new String[]{
                String.valueOf(System.currentTimeMillis()),
                "MINI " + (method == null || method.length() == 0 ? "?" : method) + " " + url,
                body
        });
    }

    /** 记录小程序响应：与暂存请求按 URL 配对，按 时间/方法/参数/数据 单条输出，只保留游戏业务接口 */
    private static void logMiniResponse(Object[] args, Object thisObject) {
        if (!sCaptureEnabled || !miniDebugEnabled() || args == null || args.length < 2 || args[0] == null) {
            return;
        }
        String url = args[0].toString();
        if (!isGameApi(url)) {
            return;
        }
        ensureAsyncRequestHook(thisObject != null ? thisObject.getClass().getClassLoader() : sClassLoader);
        String body = args[1] != null ? clip(deepPrettyJson(args[1].toString())) : "";
        String[] record = pendingMiniRequests.remove(url);
        String time;
        String methodLine;
        String params;
        if (record != null) {
            time = record[0];
            methodLine = record[1];
            params = record[2];
        } else {
            // 请求先于 hook 安装等情况下没有暂存，仍单独记录响应
            time = String.valueOf(System.currentTimeMillis());
            methodLine = "MINI ? " + url;
            params = "";
        }
        Log.debug("记录\n时间: " + time + "\n方法: " + methodLine + "\n参数: " + params + "\n数据: " + body + "\n");
    }

    /**
     * 响应首包到达时兜底补装 AsyncRequestHandler hook（正常情况下类加载时已在
     * hookMiniNetworkClass 中装好；此处防止 loadClass 监听漏回调）。
     */
    private static void ensureAsyncRequestHook(ClassLoader loader) {
        if (deferredHookedClasses.contains(ASYNC_REQUEST_HANDLER_CLASS)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(ASYNC_REQUEST_HANDLER_CLASS, false, loader);
            if (!deferredHookedClasses.add(ASYNC_REQUEST_HANDLER_CLASS)) {
                return;
            }
            if (installAsyncRequestHookOnClass(clazz) == 0) {
                deferredHookedClasses.remove(ASYNC_REQUEST_HANDLER_CLASS);
            }
        } catch (Throwable ignored) {
            // 类尚未加载：loadClass 监听命中后会安装，这里不打日志避免噪音
        }
    }

    /**
     * 定点 hook 真实请求入口（由响应调用栈与方法清单确认）：
     * AsyncRequestHandler.processAsyncRequest(ApiContext,App,Page,RequestModel,ParamsModel,...,String,...)
     * paramsJson 在到达此处前已被拆进 RequestModel/ParamsModel 的字段，故递归收集模型中的字符串。
     */
    private static int installAsyncRequestHookOnClass(Class<?> clazz) {
        int hooked = hookAllMethodsByName(clazz, "processAsyncRequest", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam p) {
                try {
                    logRequestFromModels(p.args);
                } catch (Throwable ignored) {
                }
            }
        });
        if (hooked > 0 && miniDebugEnabled()) {
            Log.debug("小程序抓包hook已安装: AsyncRequestHandler.processAsyncRequest 进程=" + sProcessName);
        }
        return hooked;
    }

    /** 从 processAsyncRequest 的模型参数中提取游戏请求（先找完整 paramsJson，再按 url/body 拼装） */
    private static void logRequestFromModels(Object[] args) {
        if (!sCaptureEnabled || !miniDebugEnabled() || args == null) {
            return;
        }
        List<String> strings = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (Object arg : args) {
            collectStrings(arg, 3, visited, strings);
            if (strings.size() >= MAX_MODEL_ITEMS) {
                break;
            }
        }
        // 1) 模型里若仍保留完整 paramsJson
        for (String s : strings) {
            if (s.length() >= 8 && s.indexOf('"') >= 0) {
                try {
                    JSONObject jo = MyUtils.newJSONObject(s);
                    String u = jo.optString("url");
                    if (u.length() > 0 && isGameApi(u)) {
                        pendingMiniRequest(u, jo.optString("method", "GET"), jo.optString("data"));
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        // 2) url 与 body 被拆成独立字段：模型里可能有多个 URL（referer/广告配置等），
        //    给所有候选 URL 登记同一份暂存，响应回来时精确 URL 自然命中真实请求
        String method = null;
        for (String s : strings) {
            if ("GET".equals(s) || "POST".equals(s) || "PUT".equals(s) || "DELETE".equals(s)) {
                method = s;
                break;
            }
        }
        String body = null;
        for (String s : strings) {
            if (s.startsWith("data=")) {
                body = s;
                break;
            }
        }
        if (body == null) {
            for (String s : strings) {
                if (s.startsWith("{") && s.length() > 16 && (body == null || s.length() > body.length())) {
                    body = s;
                }
            }
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (String s : strings) {
            if (s.startsWith("http") && isGameApi(s)) {
                candidates.add(s.trim());
                if (candidates.size() >= 3) {
                    break;
                }
            }
        }
        for (String url : candidates) {
            pendingMiniRequest(url, method, body);
        }
    }

    /**
     * 递归收集对象图中的 String：字段读取含父类链，com.alipay 模型对象继续下钻，
     * Map/Collection 取元素，深度与对象数双重上限，只读。
     */
    private static void collectStrings(Object obj, int depth, Set<Object> visited, List<String> out) {
        if (obj == null || depth < 0 || visited.size() >= 128 || out.size() >= MAX_MODEL_ITEMS) {
            return;
        }
        if (obj instanceof String) {
            String s = (String) obj;
            if (s.length() > 0) {
                out.add(s);
            }
            return;
        }
        Class<?> c = obj.getClass();
        if (c.isPrimitive() || c.isArray() || obj instanceof Number || obj instanceof Boolean
                || obj instanceof Character || obj instanceof Enum) {
            return;
        }
        if (obj instanceof Map) {
            if (!visited.add(obj)) {
                return;
            }
            int count = 0;
            Iterator<?> values = ((Map<?, ?>) obj).values().iterator();
            while (count++ < MAX_MODEL_ITEMS && visited.size() < 128 && out.size() < MAX_MODEL_ITEMS && values.hasNext()) {
                collectStrings(values.next(), depth - 1, visited, out);
            }
            return;
        }
        if (obj instanceof Collection) {
            if (!visited.add(obj)) {
                return;
            }
            int count = 0;
            Iterator<?> values = ((Collection<?>) obj).iterator();
            while (count++ < MAX_MODEL_ITEMS && visited.size() < 128 && out.size() < MAX_MODEL_ITEMS && values.hasNext()) {
                collectStrings(values.next(), depth - 1, visited, out);
            }
            return;
        }
        if (!c.getName().startsWith("com.alipay") || !visited.add(obj)) {
            return;
        }
        for (Field f : modelFieldsCache.computeIfAbsent(c, MiniProgramHook::resolveInstanceFields)) {
            try {
                collectStrings(f.get(obj), depth - 1, visited, out);
            } catch (Throwable ignored) {
            }
        }
    }

    private static List<Field> resolveInstanceFields(Class<?> clazz) {
        List<Field> list = new ArrayList<>();
        Class<?> cur = clazz;
        int guard = 0;
        while (cur != null && cur != Object.class && guard++ < 6) {
            for (Field fld : cur.getDeclaredFields()) {
                int mods = fld.getModifiers();
                if (Modifier.isStatic(mods)) {
                    continue;
                }
                Class<?> t = fld.getType();
                if (t == String.class || t.getName().startsWith("com.alipay")
                        || Map.class.isAssignableFrom(t) || Collection.class.isAssignableFrom(t)) {
                    try {
                        fld.setAccessible(true);
                        list.add(fld);
                    } catch (Throwable ignored) {
                    }
                }
            }
            cur = cur.getSuperclass();
        }
        return list;
    }

    /**
     * 是否记录该小程序请求：抓包开关打开期间默认记录全部小程序 API 请求，
     * 仅排除埋点/日志/监控域名和静态资源，避免白名单域名导致其他游戏抓不到。
     */
    private static boolean isGameApi(String url) {
        if (url == null || url.length() < 8) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        for (String host : MINI_HOST_BLACKLIST) {
            if (lower.contains(host)) {
                return false;
            }
        }
        int query = lower.indexOf('?');
        String path = query >= 0 ? lower.substring(0, query) : lower;
        int slash = path.indexOf('/', 8);
        if (slash >= 0) {
            path = path.substring(slash);
        }
        for (String suffix : MINI_STATIC_SUFFIX) {
            if (path.endsWith(suffix)) {
                return false;
            }
        }
        for (String dir : MINI_STATIC_PATH) {
            if (path.contains(dir)) {
                return false;
            }
        }
        return true;
    }

    /** 把 JSON 中嵌套转义的 JSON 字符串逐层展开为单行紧凑形式；非 JSON 原样返回并折掉换行 */
    private static String deepPrettyJson(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        try {
            Object parsed;
            if (s.startsWith("{")) {
                // 严格解析仅用于抓包格式化：解析失败须保留原文，不能把无效内容变为 {}。
                parsed = deepParseJson(new JSONObject(s));
            } else if (s.startsWith("[")) {
                parsed = deepParseJson(new JSONArray(s));
            } else {
                return raw.replaceAll("\\r?\\n", " ");
            }
            // toString() 为单行紧凑输出，保证每条日志只占一行
            String out = (parsed instanceof JSONObject)
                    ? ((JSONObject) parsed).toString()
                    : ((JSONArray) parsed).toString();
            return out.replaceAll("\\r?\\n", " ");
        } catch (Throwable t) {
            return raw.replaceAll("\\r?\\n", " ");
        }
    }

    private static Object deepParseJson(Object value) {
        try {
            if (value instanceof JSONObject) {
                JSONObject obj = (JSONObject) value;
                JSONObject out = MyUtils.newJSONObject();
                JSONArray names = obj.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String key = names.optString(i);
                        out.put(key, deepParseJson(obj.opt(key)));
                    }
                }
                return out;
            }
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    out.put(deepParseJson(arr.opt(i)));
                }
                return out;
            }
            if (value instanceof String) {
                String str = ((String) value).trim();
                boolean jsonObj = str.startsWith("{") && str.endsWith("}");
                boolean jsonArr = str.startsWith("[") && str.endsWith("]");
                if (jsonObj || jsonArr) {
                    return jsonObj ? deepParseJson(new JSONObject(str)) : deepParseJson(new JSONArray(str));
                }
            }
        } catch (Throwable ignored) {
        }
        return value;
    }

    private static String clip(String s) {
        if (s != null && s.length() > MAX_LOG_LEN) {
            return s.substring(0, MAX_LOG_LEN) + "...(truncated)";
        }
        return s;
    }

    /**
     * 监听 ClassLoader.loadClass：小程序插件 dex 中的网络类被加载时动态安装 hook。
     * 同时 hook ClassLoader 和 BaseDexClassLoader（后者重写了 loadClass）。
     */
    private static void installDeferredNetworkHook() {
        if (deferredNetworkHookInstalled) return;
        boolean installed = false;
        XC_MethodHook loadClassHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                try {
                    Object result = p.getResult();
                    if (result instanceof Class) {
                        hookMiniNetworkClass((Class<?>) result);
                    }
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            installed |= addUnhook(XHelpers.findAndHookMethod(ClassLoader.class, "loadClass", String.class, loadClassHook));
        } catch (Throwable ignored) {
        }
        try {
            Class<?> baseDexClassLoader = XHelpers.findClassIfExists("dalvik.system.BaseDexClassLoader", null);
            if (baseDexClassLoader != null) {
                installed |= addUnhook(XHelpers.findAndHookMethod(baseDexClassLoader, "loadClass", String.class, loadClassHook));
            }
        } catch (Throwable ignored) {
        }
        deferredNetworkHookInstalled = installed;
    }

    /** 对小程序网络请求/响应类安装定点 hook（只记录，不修改参数和返回值） */
    private static void hookMiniNetworkClass(Class<?> clazz) {
        if (!sCaptureEnabled || !miniDebugEnabled()) return;
        String name = clazz.getName();
        if (!MINI_RESP_CLASS.equals(name) && !ASYNC_REQUEST_HANDLER_CLASS.equals(name)) {
            return;
        }
        if (!deferredHookedClasses.add(name)) {
            return;
        }
        try {
            if (MINI_RESP_CLASS.equals(name)) {
                int hooked = hookMethodInHierarchy(clazz, "onHandleResponse", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            logMiniResponse(p.args, p.thisObject);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                if (hooked == 0) deferredHookedClasses.remove(name);
                if (miniDebugEnabled()) {
                    Log.debug("小程序抓包hook" + (hooked > 0 ? "已安装: " : "未匹配: ")
                            + name + " onHandleResponse方法=" + hooked + " 进程=" + sProcessName);
                }
            } else {
                if (installAsyncRequestHookOnClass(clazz) == 0) deferredHookedClasses.remove(name);
            }
        } catch (Throwable t) {
            deferredHookedClasses.remove(name);
            if (miniDebugEnabled()) {
                Log.debug("小程序抓包hook安装失败: " + name + " " + t.getMessage());
            }
        }
    }

    /**
     * 沿继承链逐层 hook 同名方法并返回 hook 到的方法总数。
     * onHandleResponse 可能定义在父类上，必须向上追溯。
     */
    private static int hookMethodInHierarchy(Class<?> clazz, String methodName, XC_MethodHook callback) {
        int count = 0;
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            count += hookAllMethodsByName(current, methodName, callback);
            current = current.getSuperclass();
        }
        return count;
    }

    /** 等价于 XposedBridge.hookAllMethods：遍历本类声明的同名方法逐个挂载 */
    private static int hookAllMethodsByName(Class<?> clazz, String methodName, XC_MethodHook callback) {
        int count = 0;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }
            try {
                m.setAccessible(true);
                if (addUnhook(XHelpers.hookMember(m, callback))) count++;
            } catch (Throwable ignored) {
            }
        }
        return count;
    }

    /** 是否记录小程序抓包：沿用日志页「抓包记录」开关，与 RPC 抓包一致 */
    private static boolean miniDebugEnabled() {
        try {
            return AppConfig.INSTANCE.getEnableDebugLog();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean addUnhook(XC_MethodHook.Unhook unhook) {
        if (unhook != null) {
            synchronized (sUnhooks) {
                if (!sCaptureEnabled || !miniDebugEnabled()) {
                    unhook.unhook();
                    return false;
                }
                sUnhooks.add(unhook);
                return true;
            }
        }
        return false;
    }
}
