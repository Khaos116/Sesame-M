package io.github.aw1y2z.sesame.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.data.ViewAppInfo;
import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 对齐 GR2026 {@code util/MyUtils.java} 中可迁移的通用部分：方法名与其保持一致，
 * 便于后续合并 fork 代码时按名字直接映射调用点。GR 侧的硬编码风控开关改为读取
 * {@link AppConfig} 的可配置项；GR 已知异常任务过滤和按账号的异常暂停统一由
 * {@link io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard} 处理。
 */
public class MyUtils {

    private static SharedPreferences mSP = null;

    private MyUtils() {
    }

    /** 固定 GMT+8 日历，委托给 {@link TimeUtil#getInstanceGMT8()} 避免重复实现。 */
    public static Calendar getInstance() {
        return TimeUtil.getInstanceGMT8();
    }

    public static JSONObject newJSONObject(String json) {
        try {
            if (json == null || !json.startsWith("{")) {
                return new JSONObject();
            }
            return new JSONObject(json);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    public static JSONObject newJSONObject() {
        return new JSONObject();
    }

    /** 是否跳过会触发验证码/风控的操作，默认沿用 GR 行为（true），可在设置中关闭。 */
    public static boolean closeVerification() {
        return Boolean.TRUE.equals(AppConfig.INSTANCE.getCloseVerification());
    }

    /** 是否跳过已知会报错的功能分支，默认沿用 GR 行为（true），可在设置中关闭。 */
    public static boolean closeErrorFunction() {
        return Boolean.TRUE.equals(AppConfig.INSTANCE.getCloseErrorFunction());
    }

    /** 是否跳过不支持 RPC 完成的任务，默认沿用 GR 行为（true），可在设置中关闭。 */
    public static boolean closeUnRpc() {
        return Boolean.TRUE.equals(AppConfig.INSTANCE.getCloseUnRpc());
    }

    private static @Nullable SharedPreferences getMySp() {
        Context context = ViewAppInfo.getContext();
        if (context == null) return null;
        if (mSP == null) mSP = context.getSharedPreferences("sesame_m_myutils", Context.MODE_PRIVATE);
        return mSP;
    }

    // 功能异常 key 按当前 APP 版本加后缀，避免新版本继承旧版本的错误标记
    private static String getFunctionErrorKey(@NonNull String key) {
        String version = ViewAppInfo.getAppVersion();
        if (version != null && !version.isEmpty()) {
            return key + "_" + version;
        }
        return key;
    }

    /** 某功能此前是否已被标记为异常（访问被拒绝/系统出错），命中后调用方应跳过该功能直到升级版本。 */
    public static boolean getSpFunctionError(@NonNull String key) {
        SharedPreferences sp = getMySp();
        if (sp == null) return true;
        return sp.getBoolean(getFunctionErrorKey(key), false);
    }

    /** 若响应 errorMessage 含“访问被拒绝”或“系统出错”，将该 key 标记为当前版本下的功能异常。 */
    public static void setSpFunctionError(@NonNull String key, JSONObject jo) {
        if (jo == null) return;
        String errorMessage = jo.optString("errorMessage", "");
        boolean isError = errorMessage.contains("访问被拒绝") || errorMessage.contains("系统出错");
        if (isError) {
            SharedPreferences sp = getMySp();
            if (sp != null) {
                sp.edit().putBoolean(getFunctionErrorKey(key), true).apply();
            }
        }
    }

    public static void cacheUserName(@Nullable String uid, @Nullable String name) {
        if (TextUtils.isEmpty(uid) || TextUtils.isEmpty(name)) return;
        SharedPreferences sp = getMySp();
        if (sp != null && !name.equals(sp.getString(uid, ""))) {
            sp.edit().putString(uid, name).apply();
        }
    }

    /** 用户资料尚未加载时，仍可显示上次缓存的昵称。 */
    public static String recordUserName(@Nullable String uid) {
        if (TextUtils.isEmpty(uid)) return "";
        UserEntity user = UserIdMap.get(uid);
        String name = user == null ? null : user.getNickName();
        if (!TextUtils.isEmpty(name)) {
            cacheUserName(uid, name);
            return ":" + name;
        }
        SharedPreferences sp = getMySp();
        String cached = sp == null ? "" : sp.getString(uid, "");
        return ":" + (TextUtils.isEmpty(cached) ? uid : cached);
    }
}
