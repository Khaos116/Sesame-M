package io.github.aw1y2z.sesame.model.task.antAnimalGuess;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class AntAnimalGuess extends ModelTask {
    private BooleanModelField adventureSign, sprintSign;

    @Override public String getName() { return "动物竞猜"; }
    @Override public ModelGroup getGroup() { return ModelGroup.OTHER; }
    @Override public ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(adventureSign = new BooleanModelField("adventureSign", "去冒险｜签到与当前局查询", false));
        fields.addField(sprintSign = new BooleanModelField("sprintSign", "去冲刺｜签到与当前局查询", false));
        return fields;
    }
    @Override public Boolean check() {
        return isEnable() && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME;
    }
    @Override public void run() {
        if (!check()) return;
        if (adventureSign.getValue()) runProject("去冒险", "2021006128683670", "https://qumaoxianapi.dongwuyouxi.com");
        if (sprintSign.getValue()) runProject("去冲刺", "2021006114686014", "https://quchongciapi.dongwuyouxi.com");
    }

    private void runProject(String name, String appId, String host) {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return;
        try {
            checkAccount(uid);
            String code = AuthCodeHelper.getAuthCode(appId);
            checkAccount(uid);
            if (code == null || code.trim().isEmpty()) {
                Log.record("动物竞猜[" + name + "]未取得当前账号授权，跳过");
                return;
            }
            JSONObject login = request(host, "/app-api/member/auth/zfb-mini-app-login",
                    MyUtils.newJSONObject().put("loginCode", code).put("state", 1), null, uid);
            JSONObject data = login.optJSONObject("data");
            Object rawToken = data == null ? null : data.opt("accessToken");
            if (!(rawToken instanceof String) || ((String) rawToken).trim().isEmpty()) throw new IOException();
            String token = (String) rawToken;
            JSONObject user = request(host, "/app-api/member/user/get", null, token, uid).optJSONObject("data");
            if (user == null) throw new IOException();
            signIn(name, appId, host, token, uid);
            JSONObject race = request(host, "/app-api/member/guess-race/get", null, token, uid).optJSONObject("data");
            Object number = race == null ? null : race.opt("raceNumber");
            if ((number instanceof String && !((String) number).isEmpty()) || number instanceof Number) {
                Log.other("动物竞猜[" + name + "]当前局=" + number);
            } else Log.record("动物竞猜[" + name + "]当前局信息未确认");
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) {
            // 第三方错误消息及异常可能带凭据，只输出异常类型。
            Log.record("动物竞猜[" + name + "]停止：授权、网络或响应未通过校验（" + e.getClass().getSimpleName() + "）");
        }
    }

    private void signIn(String name, String appId, String host, String token, String uid) throws Exception {
        String key = "animalGuess::signAttempt::" + appId;
        if (Status.hasFlagToday(key)) return;
        int day = businessDay();
        JSONArray before = request(host, "/app-api/member/sign-in/config/list", null, token, uid).optJSONArray("data");
        int signed = signedCount(before);
        if (signed < 0 || signed == before.length()) return;
        checkAccount(uid);
        if (businessDay() != day) return;
        Status.flagToday(key, uid);
        request(host, "/app-api/member/sign-in/record/create", MyUtils.newJSONObject(), token, uid);
        JSONArray after = request(host, "/app-api/member/sign-in/config/list", null, token, uid).optJSONArray("data");
        if (businessDay() == day && after != null && before.length() == after.length() && signedCount(after) == signed + 1) {
            Log.other("动物竞猜[" + name + "]签到已回查确认");
        } else Log.record("动物竞猜[" + name + "]签到未确认，本日不重复提交");
    }

    private static int signedCount(JSONArray rows) {
        if (rows == null || rows.length() == 0 || rows.length() > 31) return -1;
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            Object value = row == null ? null : row.opt("status");
            if (!(value instanceof Number)) return -1;
            double status = ((Number) value).doubleValue();
            if (status == 1) count++;
            else if (status != 0) return -1;
        }
        return count;
    }

    private static int businessDay() {
        Calendar now = MyUtils.getInstance();
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
    }

    private static void checkAccount(String uid) {
        TimeUtil.sleep(0);
        if (!uid.equals(UserIdMap.getCurrentUid())) throw new TaskCancelledException();
    }

    private static JSONObject request(String host, String path, JSONObject body, String token, String uid) throws Exception {
        checkAccount(uid);
        HttpURLConnection conn = (HttpURLConnection) new URL(host + path).openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestMethod(body == null ? "GET" : "POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("x-release-type", "ONLINE");
            if (token != null) conn.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                checkAccount(uid);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
            String raw;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    checkAccount(uid);
                    // ponytail: 单响应上限1 MiB；服务端配置超过此大小再调整。
                    if (out.size() + n > 1024 * 1024) throw new IOException();
                    out.write(buffer, 0, n);
                }
                raw = out.toString("UTF-8");
            }
            checkAccount(uid);
            JSONObject result = MyUtils.newJSONObject(raw);
            Object code = result.opt("code");
            if (!(code instanceof Number) || ((Number) code).doubleValue() != 0) throw new IOException();
            return result;
        } finally { conn.disconnect(); }
    }
}
