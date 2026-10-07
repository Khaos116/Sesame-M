package io.github.aw1y2z.sesame.model.task.antSports;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** Public 3hours login contract supplies a per-run session; credentials never enter RuntimeInfo. */
final class ThreeHoursDonate {
    private static final String APP_ID = "2019052265312523", RECEIPT = "threeHoursDonateReceipt", DONE = "threeHoursDonateDoneDay";
    private final String uid = UserIdMap.getCurrentUid(), uuid = UUID.randomUUID().toString();
    private final int day = day();
    private final RuntimeInfo runtime = RuntimeInfo.getInstance();
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private String sid, csrf;
    private long expires;

    static void run(boolean enabled) {
        if (!enabled || UserIdMap.getCurrentUid() == null || UserIdMap.getCurrentUid().isEmpty()) return;
        ThreeHoursDonate work = new ThreeHoursDonate();
        if (!work.runtime.getString(RECEIPT).isEmpty()) { Log.record("3小时捐步：此前结果未确认，跨日保留回执，不重复提交"); return; }
        if (Integer.toString(work.day).equals(work.runtime.getString(DONE))) return;
        try { work.donate(); }
        catch (TaskCancelledException cancelled) { throw cancelled; }
        catch (Exception error) { Log.record("3小时捐步：授权、网络或响应校验失败（" + error.getClass().getSimpleName() + "），保留未确认回执"); }
    }

    private static int day() {
        Calendar c = MyUtils.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private void current() {
        TimeUtil.sleep(0);
        if (!uid.equals(UserIdMap.getCurrentUid()) || day != day()) throw new TaskCancelledException();
        if (sid != null && expires <= System.currentTimeMillis()) throw new IllegalStateException();
    }

    private String authCode() throws Exception {
        current();
        String code = AuthCodeHelper.getAuthCode(APP_ID);
        current();
        return credential(code);
    }

    private void donate() throws Exception {
        String page = http("https://3hours.taobao.com/", null);
        Matcher bootstrap = Pattern.compile("window\\.th_csrf\\s*=\\s*[\"']([A-Za-z0-9_-]{1,4096})[\"']").matcher(page);
        if (!bootstrap.find()) return;
        csrf = bootstrap.group(1);
        if (bootstrap.find()) return;
        JSONObject login = request("https://3hours.taobao.com/user/v2/login?p_csrf=" + encode(csrf),
                MyUtils.newJSONObject().put("authCode", authCode()).put("namespace", 50).put("isAuthorized", false));
        JSONObject session = login.optJSONObject("data");
        if (session == null) return;
        String sessionId = credential(session.opt("sid")), token = credential(session.opt("token"));
        long expiry = expiry(session.opt("sessionExpireTime"));
        if (expiry <= System.currentTimeMillis()) return;
        sid = sessionId; csrf = token; expires = expiry;
        current();
        JSONObject before = steps(), fresh = steps();
        if (!sameSteps(before, fresh)) return;
        long today = number(before, "todaySteps"), remaining = number(before, "remainSteps"), donated = number(before, "donatedSteps");
        if (today <= 0 || remaining <= 0 || Boolean.TRUE.equals(before.opt("allDonated")) || Boolean.TRUE.equals(before.opt("reachLimit"))) return;
        current();
        JSONObject receipt = MyUtils.newJSONObject().put("uid", uid).put("day", day).put("todaySteps", today)
                .put("remainSteps", remaining).put("donatedSteps", donated);
        if (!runtime.putVerified(RECEIPT, receipt.toString())) return;
        JSONObject ack = request("https://m.3hours.taobao.com/donateStep/v2/donate?p_csrf=" + encode(csrf),
                MyUtils.newJSONObject().put("todayExerciseSteps", today));
        JSONObject result = ack.optJSONObject("data");
        if (result == null || !Boolean.TRUE.equals(result.opt("success"))) return;
        JSONObject after = steps();
        long left = number(after, "remainSteps"), total = number(after, "donatedSteps"), delta = remaining - left;
        if (!validSteps(after) || number(after, "todaySteps") != today || delta <= 0 || total - donated != delta
                || number(result, "totalDonatedToday") != total || number(result, "remainStepsAfter") != left) return;
        current();
        if (!runtime.putVerified(DONE, Integer.toString(day)) || !runtime.putVerified(RECEIPT, null)) return;
        Log.other("3小时公益捐步❤️已回查捐步增加" + delta + "步，剩余" + left + "步；本日不再提交");
    }

    private JSONObject steps() throws Exception {
        JSONObject root = request("https://m.3hours.taobao.com/donateStep/v2/getTodayRemainStep?authCode=" + encode(authCode()) + "&timeZone=Asia%2FShanghai", null);
        JSONObject data = root.optJSONObject("data");
        return validSteps(data) ? data : null;
    }

    private static boolean validSteps(JSONObject data) {
        long today = number(data, "todaySteps"), remaining = number(data, "remainSteps"), donated = number(data, "donatedSteps");
        return data != null && today >= 0 && remaining >= 0 && donated >= 0 && remaining <= today
                && data.opt("allDonated") instanceof Boolean && (!Boolean.TRUE.equals(data.opt("allDonated")) || remaining == 0)
                && (!data.has("reachLimit") || data.opt("reachLimit") instanceof Boolean);
    }

    private static boolean sameSteps(JSONObject first, JSONObject second) {
        return validSteps(first) && validSteps(second) && number(first, "todaySteps") == number(second, "todaySteps")
                && number(first, "remainSteps") == number(second, "remainSteps") && number(first, "donatedSteps") == number(second, "donatedSteps")
                && first.opt("allDonated").equals(second.opt("allDonated")) && java.util.Objects.equals(first.opt("reachLimit"), second.opt("reachLimit"));
    }

    private static long number(JSONObject row, String key) {
        Object value = row == null ? null : row.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        String raw = value.toString();
        return raw.matches("[0-9]{1,9}") ? Long.parseLong(raw) : -1;
    }

    private static long expiry(Object value) {
        try {
            if (value instanceof Number || value instanceof String && value.toString().matches("[0-9]{13}")) return Long.parseLong(value.toString());
            return value instanceof String ? java.time.Instant.parse((String) value).toEpochMilli() : -1;
        } catch (RuntimeException invalid) { return -1; }
    }

    private static String credential(Object value) throws IOException {
        if (!(value instanceof String) || ((String) value).trim().isEmpty() || ((String) value).length() > 4096) throw new IOException();
        String text = (String) value;
        for (int i = 0; i < text.length(); i++) if (Character.isISOControl(text.charAt(i))) throw new IOException();
        return text;
    }

    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }

    private JSONObject request(String address, JSONObject body) throws Exception {
        JSONObject root = MyUtils.newJSONObject(http(address, body));
        if (!Boolean.TRUE.equals(root.opt("success")) || !"200".equals(root.optString("code"))) throw new IOException();
        return root;
    }

    private String http(String address, JSONObject body) throws Exception {
        current();
        URL url = new URL(address);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(5000); conn.setReadTimeout(8000); conn.setInstanceFollowRedirects(false); conn.setUseCaches(false);
            conn.setRequestMethod(body == null ? "GET" : "POST");
            conn.setRequestProperty("Accept", "application/json"); conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Cookie", ""); conn.setRequestProperty("Authorization", "");
            for (Map.Entry<String, List<String>> header : cookies.get(url.toURI(), java.util.Collections.emptyMap()).entrySet())
                conn.setRequestProperty(header.getKey(), String.join("; ", header.getValue()));
            conn.setRequestProperty("csr-uuid", uuid); conn.setRequestProperty("csr-account-v2", "true"); conn.setRequestProperty("csr-front-v", "1");
            if (sid != null) conn.setRequestProperty("csr-token", sid);
            if (csrf != null) conn.setRequestProperty("csr-csrf", csrf);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 65536) throw new IOException();
                conn.setDoOutput(true); conn.setFixedLengthStreamingMode(bytes.length); current();
                try (OutputStream out = conn.getOutputStream()) { current(); out.write(bytes); }
            }
            current();
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
            List<String> freshCookies = new ArrayList<>();
            for (Map.Entry<String, List<String>> header : conn.getHeaderFields().entrySet()) {
                if (header.getKey() == null || !"Set-Cookie".equalsIgnoreCase(header.getKey()) || header.getValue() == null) continue;
                for (String cookie : header.getValue()) if (cookie != null && cookie.matches("(?is)^\\s*(c_csrf|csr-context)=.*")) {
                    if (cookie.length() > 4096 || freshCookies.size() >= 16) throw new IOException();
                    for (int i = 0; i < cookie.length(); i++) if (Character.isISOControl(cookie.charAt(i))) throw new IOException();
                    freshCookies.add(cookie);
                }
            }
            cookies.put(url.toURI(), java.util.Collections.singletonMap("Set-Cookie", freshCookies));
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int read;
                while ((read = in.read(buffer)) != -1) {
                    current(); if (out.size() + read > 65536) throw new IOException(); out.write(buffer, 0, read);
                }
                current();
                return out.toString("UTF-8");
            }
        } finally { conn.disconnect(); }
    }
}
