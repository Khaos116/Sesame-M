package io.github.aw1y2z.sesame.hook.ext;

import android.content.Context;
import android.content.pm.PackageInfo;

import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.entity.AlipayVersion;
import io.github.aw1y2z.sesame.util.ClassUtil;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

/**
 * 版本伪装 Hook - 拦截 PackageManager.getPackageInfo() 向支付宝服务端伪造版本号。
 * 对齐 GR2026 main_my hook/ext/VersionHook.java（提交 3013cb36），API 从传统 Xposed
 * （XposedHelpers/XC_MethodHook）换成 M 自己的兼容层（XHelpers/compat.XC_MethodHook）。
 * <p>
 * 默认开启（版本 10.6.58.8000，可在扩展页关闭/修改，重启支付宝生效）。启用后会让支付宝服务端认为客户端是伪装的低版本，用于规避高版本才有的
 * 拼图验证码风控——这是主动欺骗服务端的行为，不是单纯跳过本地判断分支，用户需知悉
 * 风险，见 doc/MyFix.md 的移植记录。
 */
public class VersionHook {

    /** 默认伪装版本（对齐 GR2026 AppConfig 默认值 10.6.58.8000 / 1881，高于新接口最低支持的 10.3.96.8100） */
    private static final String DEFAULT_VERSION_NAME = "10.6.58.8000";
    private static final long DEFAULT_VERSION_CODE = 1881L;

    private static String sCachedVersionName = DEFAULT_VERSION_NAME;
    private static long sCachedVersionCode = DEFAULT_VERSION_CODE;
    // 配置文件加载前保持 false：早于 loadVersionConfig 的 getPackageInfo 不应被误伪装；默认开启由配置文件承载
    private static boolean sEnableVersionHook = false;
    private static boolean isVersionHookRegistered = false;

    private static AlipayVersion sAlipayVersion;

    private VersionHook() {
    }

    // ==================== 配置初始化 ====================

    public static void ensureVersionConfig(Context context) {
        try {
            File configDir = FileUtil.MAIN_DIRECTORY_FILE;
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            File configFile = new File(configDir, "version_config.json");
            if (!configFile.exists()) {
                JSONObject defaultConfig = MyUtils.newJSONObject();
                defaultConfig.put("enableVersionHook", true);
                defaultConfig.put("versionName", DEFAULT_VERSION_NAME);
                defaultConfig.put("versionCode", DEFAULT_VERSION_CODE);
                FileUtil.write2File(defaultConfig.toString(), configFile);
                Log.record("已创建版本配置文件");
            }
        } catch (Throwable t) {
            Log.record("创建版本配置失败: " + t.getMessage());
        }
    }

    public static void loadVersionConfig() {
        try {
            File configFile = new File(FileUtil.MAIN_DIRECTORY_FILE, "version_config.json");
            if (!configFile.exists()) {
                return;
            }
            String content = FileUtil.readFromFile(configFile);
            if (content == null || content.isEmpty()) {
                return;
            }
            JSONObject config = MyUtils.newJSONObject(content);

            sEnableVersionHook = config.optBoolean("enableVersionHook", true);
            sCachedVersionName = config.optString("versionName", DEFAULT_VERSION_NAME);
            sCachedVersionCode = config.optLong("versionCode", DEFAULT_VERSION_CODE);

            Log.i("VersionHook", "配置加载完成: enabled=" + sEnableVersionHook
                    + ", name=" + sCachedVersionName + ", code=" + sCachedVersionCode);
        } catch (Throwable t) {
            Log.i("VersionHook", "加载配置失败: " + t.getMessage());
        }
    }

    public static void saveVersionConfig() {
        try {
            File configFile = new File(FileUtil.MAIN_DIRECTORY_FILE, "version_config.json");
            JSONObject config = MyUtils.newJSONObject();
            config.put("enableVersionHook", sEnableVersionHook);
            config.put("versionName", sCachedVersionName);
            config.put("versionCode", sCachedVersionCode);
            FileUtil.write2File(config.toString(), configFile);
        } catch (Throwable t) {
            Log.record("保存版本配置失败: " + t.getMessage());
        }
    }

    // ========== Getter/Setter ==========

    public static void setAlipayVersion(AlipayVersion version) {
        sAlipayVersion = version;
    }

    public static String getCachedVersionName() {
        return sCachedVersionName;
    }

    public static long getCachedVersionCode() {
        return sCachedVersionCode;
    }

    public static boolean isVersionHookEnabled() {
        return sEnableVersionHook;
    }

    public static void setEnableVersionHook(boolean enable) {
        sEnableVersionHook = enable;
    }

    public static void setVersionName(String name) {
        sCachedVersionName = (name != null) ? name : "";
    }

    public static void setVersionCode(long code) {
        sCachedVersionCode = code;
    }

    public static String getFakeVersionName() {
        if (!sEnableVersionHook) {
            return "";
        }
        return (sCachedVersionName != null && !sCachedVersionName.isEmpty())
                ? sCachedVersionName : DEFAULT_VERSION_NAME;
    }

    public static long getFakeVersionCode() {
        if (!sEnableVersionHook) {
            return 0;
        }
        return sCachedVersionCode > 0 ? sCachedVersionCode : DEFAULT_VERSION_CODE;
    }

    // ==================== 版本伪装 Hook ====================

    /**
     * 注册 PackageManager.getPackageInfo() Hook，拦截所有对支付宝包名的版本查询。
     * classLoader 用宿主（支付宝）进程的 classloader 加载框架类，不能像传统 Xposed
     * 那样传 null——M 的 {@link XHelpers#findClass} 没有对 null classloader 做特殊兜底。
     */
    public static void initVersionHook(ClassLoader classLoader) {
        if (isVersionHookRegistered) {
            Log.i("VersionHook", "Hook 已注册，跳过重复注册");
            return;
        }

        try {
            XHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfo",
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        private boolean logged = false;

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!sEnableVersionHook) {
                                return;
                            }

                            Object info = param.getResult();
                            if (info == null) {
                                return;
                            }

                            String pkg = (String) XHelpers.getObjectField(info, "packageName");
                            if (!ClassUtil.PACKAGE_NAME.equals(pkg)) {
                                return;
                            }

                            String versionName = getFakeVersionName();
                            long versionCode = getFakeVersionCode();
                            XHelpers.setObjectField(info, "versionName", versionName);
                            try {
                                PackageInfo.class.getMethod("setLongVersionCode", long.class)
                                        .invoke(info, versionCode);
                            } catch (Throwable ignored) {
                                XHelpers.setObjectField(info, "versionCode", (int) versionCode);
                            }

                            param.setResult(info);

                            if (!logged) {
                                Log.record("版本伪装已生效: " + versionName
                                        + " (code=" + versionCode + ")");
                                logged = true;
                            }
                        }
                    }
            );

            isVersionHookRegistered = true;
            Log.i("VersionHook", "getPackageInfo Hook 注册成功");
        } catch (Throwable t) {
            Log.i("VersionHook", "版本Hook注册失败: " + t.getMessage());
            Log.printStackTrace("VersionHook", t);
        }
    }

    /** 获取用于展示/上报的版本号：伪装开启时返回伪装版本，否则返回真实版本。 */
    public static String getDisplayVersion() {
        if (sEnableVersionHook) {
            return getFakeVersionName();
        }
        if (sAlipayVersion != null) {
            return sAlipayVersion.getVersionString();
        }
        return "";
    }
}
