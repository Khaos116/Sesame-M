package io.github.aw1y2z.sesame.model.task.forestRead;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import javax.net.ssl.SSLException;
import java.nio.charset.StandardCharsets;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.TaskCancelledException;

/** 阅读服务HTTP协议；Token按账号校验复用，客户端实例不跨账号。 */
public final class ReadForestRpcCall {
    public static final String APP_ID = "2021003114652763";
    public static final String VERSION = "0.2.2410251510.53";
    private static final String HOST = "https://m.zhangwenwh.com/api";
    private String token;

    public ReadForestRpcCall() { }

    public void login(String authCode) throws Exception {
        token = null;
        JSONObject body = MyUtils.newJSONObject().put("auth_code", authCode)
                .put("app_id", APP_ID).put("cid", "7001");
        JSONObject data = request("/authorization", body, "", "").optJSONObject("data");
        Object value = data == null ? null : data.opt("token");
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new ReadFailure("登录响应未返回有效 Token，无法继续阅读", true);
        }
        token = (String) value;
    }

    public boolean useCachedToken(String cached) throws Exception {
        if (cached == null || cached.trim().isEmpty()) return false;
        token = cached;
        try {
            if ("success".equals(request("/authorization", MyUtils.newJSONObject(), "", "").opt("data"))) return true;
        } catch (ReadFailure e) {
            if (!e.needsLogin) throw e;
        }
        token = null;
        return false;
    }

    String token() { return token; }

    public JSONObject index() throws Exception {
        return request("/index/index?type=1", null, "", "");
    }

    public JSONObject chapter(String book, String chapter) throws Exception {
        String path = "/book/chapter?book_id=" + encode(book);
        if (!chapter.isEmpty()) path += "&chapter_id=" + encode(chapter) + "&chapter_num=&source=";
        return request(path, null, book, chapter);
    }

    public JSONObject energy() throws Exception {
        return request("/user/forestPro", null, "", "");
    }

    public void read(String book, String chapter, String name, int seconds) throws Exception {
        JSONObject body = MyUtils.newJSONObject().put("book_id", book);
        try { body.put("chapter_id", Long.parseLong(chapter)); }
        catch (NumberFormatException e) { body.put("chapter_id", chapter); }
        request("/user/updateWel", MyUtils.newJSONObject(body.toString()).put("time", seconds), book, chapter);
        request("/book/set_read_recently", body.put("chapter_name", name), book, chapter);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static void checkCancelled() {
        if (RunGeneration.isStale() || Thread.currentThread().isInterrupted()) throw new TaskCancelledException();
    }

    /** 仅此类型携带由本客户端校验/脱敏的可展示原因，其它异常不输出原始消息。 */
    static final class ReadFailure extends IOException {
        final boolean needsLogin;
        ReadFailure(String reason) { this(reason, false); }
        ReadFailure(String reason, boolean needsLogin) { super(reason); this.needsLogin = needsLogin; }
    }

    static String failureReason(Exception error) {
        if (error instanceof ReadFailure) return error.getMessage();
        if (error instanceof SocketTimeoutException) return "网络请求超时，请检查网络后稍后重试";
        if (error instanceof UnknownHostException) return "无法解析阅读服务域名，请检查网络/DNS";
        if (error instanceof SSLException) return "HTTPS 握手或证书验证失败";
        if (error instanceof ConnectException) return "无法连接阅读服务，请检查网络或稍后重试";
        if (error instanceof IOException) return "网络连接/读取异常（" + error.getClass().getSimpleName() + "）";
        return "执行异常（" + error.getClass().getSimpleName() + "）";
    }

    private static String getBaseUserAgent() {
        String systemUa = System.getProperty("http.agent");
        if (systemUa == null || systemUa.isEmpty()) {
            systemUa = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ")";
        }
        // 真实 UA 里 Mobile Safari/537.36 位于 UCBS 之后，系统 UA 末尾自带的那份要去掉
        String base = systemUa.replace(" Mobile Safari/537.36", "").trim();
        String alipayVer = String.valueOf(ApplicationHook.getAlipayVersion());
        return base
                + " MYWeb/1.11.126.260911211123 UWS/3.22.2.9999 UCBS/3.22.2.9999_220000000000"
                + " Mobile Safari/537.36 NebulaSDK/1.8.100112 Nebula"
                + " AlipayDefined(nt:" + networkType() + ",ws:" + screenSpec() + ")"
                + " AliApp(AP/" + alipayVer + ") AlipayClient/" + alipayVer
                + " Language/zh-Hans isConcaveScreen/true"
                + " Region/CNAriver/" + alipayVer + " DTN/2.0";
    }

    private static String networkType() {
        try {
            Context context = ApplicationHook.getContext();
            if (context != null) {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return "WIFI";
                    }
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                                if (tm != null && tm.getDataNetworkType() == TelephonyManager.NETWORK_TYPE_NR) return "5G";
                            }
                        } catch (SecurityException ignored) { }
                        return "4G";
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "WIFI";
    }

    private static String screenSpec() {
        try {
            Context context = ApplicationHook.getContext();
            if (context != null) {
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                if (dm == null || !Float.isFinite(dm.density) || dm.density <= 0 || dm.widthPixels <= 0) return "393|0|2.75";
                int width = Math.round(dm.widthPixels / dm.density);
                float density = Math.round(dm.density * 100) / 100f;
                return width + "|0|" + density;
            }
        } catch (Throwable ignored) {
        }
        return "393|0|2.75";
    }

    private String safeMessage(String message, JSONObject body) {
        for (String secret : new String[]{token, body == null ? null : body.optString("auth_code")}) {
            if (secret != null && !secret.isEmpty()) message = message.replace(secret, "[已隐藏]");
        }
        message = message.replaceAll("(?i)Bearer\\s+[^\\s,;\"'}]+", "Bearer [已隐藏]")
                .replaceAll("(?i)(auth[_-]?code|access[_-]?token|token|authorization|alipayminimark)[\"']?\\s*[:=]\\s*[\"']?[^\\s,;\"'}]+", "$1=[已隐藏]")
                .replaceAll("[\\r\\n\\t]", " ");
        return RpcFailurePolicy.boundedMessage(message);
    }

    private JSONObject request(String path, JSONObject body, String book, String chapter) throws Exception {
        checkCancelled();
        HttpURLConnection conn = (HttpURLConnection) new URL(HOST + path).openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(20_000);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestMethod(body == null ? "GET" : "POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("x-release-type", "ONLINE");
            conn.setRequestProperty("x-requested-with", "com.eg.android.AlipayGphone");
            conn.setRequestProperty("x-mass-tappid", APP_ID);
            conn.setRequestProperty("accept-language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            String referer = "https://" + APP_ID + ".hybrid.alipay-eco.com/" + APP_ID + "/" + VERSION + "/index.html";
            referer += book.isEmpty() ? "#pages/index/index"
                    : "#moduleA/pages/chapter/index?__appxPageId=1&bid=" + encode(book) + "&cid=" + encode(chapter);
            conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("User-Agent", getBaseUserAgent());
            if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new ReadFailure(path.split("\\?")[0] + " 返回 HTTP " + status
                        + (status == 401 || status == 403 ? "，登录凭据失效或服务拒绝访问" : "，服务器未接受请求"),
                        status == 401 || status == 403);
            }
            String raw;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    checkCancelled();
                    // ponytail: 单响应最多 4 MiB；服务端章节超过此大小再调整。
                    if (out.size() + n > 4 * 1024 * 1024) throw new ReadFailure("响应超过 4 MiB 上限");
                    out.write(buffer, 0, n);
                }
                raw = out.toString("UTF-8");
            }
            checkCancelled();
            JSONObject result = MyUtils.newJSONObject(raw);
            Object code = result.opt("code");
            if (!(code instanceof Number) || ((Number) code).doubleValue() != 2000) {
                String message = "";
                // 先脱敏再截断，不能使用已截断的错误文案，否则长 Token 前缀可能漏出。
                for (String field : new String[]{"msg", "errorMessage", "errorMsg", "resultDesc", "resultMsg", "memo", "desc", "message"}) {
                    Object value = result.opt(field);
                    if (value instanceof String && !((String) value).isEmpty()) {
                        message = (String) value;
                        break;
                    }
                }
                if (message.isEmpty()) message = "响应未提供错误原因";
                message = safeMessage(message, body);
                throw new ReadFailure(path.split("\\?")[0] + " 返回无效状态码 " + result.optInt("code", -1)
                        + "，服务端原因=" + message, message.toLowerCase(java.util.Locale.ROOT).contains("token")
                        || message.contains("登录") || message.contains("授权"));
            }
            return result;
        } finally {
            conn.disconnect();
        }
    }
}
