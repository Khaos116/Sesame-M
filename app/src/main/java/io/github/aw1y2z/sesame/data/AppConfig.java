package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.Data;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

import java.io.File;

@Data
public class AppConfig {

    private static final String TAG = AppConfig.class.getSimpleName();

    // 存到与注入进程（支付宝模块）共享的 sesame 目录，确保日志开关在模块进程同样生效
    private static final File APP_CONFIG_DIRECTORY_FILE = FileUtil.MAIN_DIRECTORY_FILE;

    public static final AppConfig INSTANCE = new AppConfig();

    /** 上次 load 解析失败：内存此时只是默认值，必须禁止写盘，否则会把默认值整份固化 */
    private static volatile boolean loadFailed = false;

    @JsonIgnore
    private boolean init;

    private Boolean newUI = true;
    private Boolean languageSimplifiedChinese = true;

    private Boolean darkMode = false;
    private Boolean followSystem = true;

    private Boolean enableForestLog = true;
    private Boolean enableGoldenBeansLog = true;
    private Boolean enableCaptchaLog = true;
    private Boolean enableFarmLog = true;
    private Boolean enableOtherLog = true;
    private Boolean enableDebugLog = false;
    private Boolean enableViewErrorLog = true;
    private Boolean enableViewRuntimeLog = true;
    // null 表示尚未选择全局开关，沿用当前账号的旧设置。
    private Boolean batteryPerm;

    // 对齐 GR2026 MyUtils 的恒真风控跳过函数，这里改为可配置项而非硬编码 true，见 docs/MyFix.md
    private Boolean closeVerification = true;
    private Boolean closeErrorFunction = true;
    private Boolean closeUnRpc = true;

    // 模块级开关：原先是按账号存在 BaseModel 里，现改为全局（注入进程与模块 App 共用同一份）
    private Boolean newRpc = true;
    private Boolean showToast = true;
    private Integer toastOffsetY = 0;
    private Boolean enableOnGoing = false;
    // null 只用于从旧版账号配置做一次迁移；迁移后写入 appConfig.json，后续不再随账号变化。
    private Boolean autoPuzzleSlider;
    private Boolean newPuzzleSlider = true;
    // d1ad4438 由 closeCaptchaDialogVPN 更名而来：旧配置文件仍是老字段名，用别名接住，避免老用户设置被静默重置
    @JsonAlias("closeCaptchaDialogVPN")
    private Boolean closeCaptchaDialog = true;

    public Boolean getNewRpc() {
        return newRpc;
    }

    public void setNewRpc(Boolean value) {
        newRpc = value;
    }

    public Boolean getShowToast() {
        return showToast;
    }

    public void setShowToast(Boolean value) {
        showToast = value;
    }

    public Integer getToastOffsetY() {
        return toastOffsetY;
    }

    public void setToastOffsetY(Integer value) {
        toastOffsetY = value;
    }

    public Boolean getEnableOnGoing() {
        return enableOnGoing;
    }

    public void setEnableOnGoing(Boolean value) {
        enableOnGoing = value;
    }

    public Boolean getCloseCaptchaDialog() {
        return closeCaptchaDialog;
    }

    public void setCloseCaptchaDialog(Boolean value) {
        closeCaptchaDialog = value;
    }

    public Boolean getAutoPuzzleSlider() {
        return autoPuzzleSlider;
    }

    public void setAutoPuzzleSlider(Boolean value) {
        autoPuzzleSlider = value;
    }

    public Boolean getNewPuzzleSlider() {
        return newPuzzleSlider;
    }

    public void setNewPuzzleSlider(Boolean value) {
        newPuzzleSlider = value;
    }

    public Boolean getLanguageSimplifiedChinese() {
        return languageSimplifiedChinese;
    }

    public void setLanguageSimplifiedChinese(Boolean value) {
        languageSimplifiedChinese = value;
    }

    public Boolean getDarkMode() {
        return darkMode;
    }

    public void setDarkMode(Boolean value) {
        darkMode = value;
    }

    public Boolean getFollowSystem() {
        return followSystem;
    }

    public void setFollowSystem(Boolean value) {
        followSystem = value;
    }

    public Boolean getEnableForestLog() { return enableForestLog; }
    public void setEnableForestLog(Boolean value) { enableForestLog = value; }

    public Boolean getEnableGoldenBeansLog() { return enableGoldenBeansLog; }
    public void setEnableGoldenBeansLog(Boolean value) { enableGoldenBeansLog = value; }
    public Boolean getEnableCaptchaLog() { return enableCaptchaLog; }
    public void setEnableCaptchaLog(Boolean value) { enableCaptchaLog = value; }

    public Boolean getEnableFarmLog() { return enableFarmLog; }
    public void setEnableFarmLog(Boolean value) { enableFarmLog = value; }

    public Boolean getEnableOtherLog() { return enableOtherLog; }
    public void setEnableOtherLog(Boolean value) { enableOtherLog = value; }

    public Boolean getEnableDebugLog() { return enableDebugLog; }
    public void setEnableDebugLog(Boolean value) { enableDebugLog = value; }

    public Boolean getEnableViewErrorLog() { return enableViewErrorLog; }
    public void setEnableViewErrorLog(Boolean value) { enableViewErrorLog = value; }

    public Boolean getEnableViewRuntimeLog() { return enableViewRuntimeLog; }
    public void setEnableViewRuntimeLog(Boolean value) { enableViewRuntimeLog = value; }

    public Boolean getCloseVerification() { return closeVerification; }
    public void setCloseVerification(Boolean value) { closeVerification = value; }

    public Boolean getCloseErrorFunction() { return closeErrorFunction; }
    public void setCloseErrorFunction(Boolean value) { closeErrorFunction = value; }

    public Boolean getCloseUnRpc() { return closeUnRpc; }
    public void setCloseUnRpc(Boolean value) { closeUnRpc = value; }

    public Boolean getBatteryPerm() { return batteryPerm; }
    public void setBatteryPerm(Boolean value) { batteryPerm = value; }

    public static synchronized boolean shouldAutoPuzzleSlider() {
        if (INSTANCE.autoPuzzleSlider != null) return INSTANCE.autoPuzzleSlider;
        INSTANCE.autoPuzzleSlider = legacyModelBoolean("autoPuzzleSlider", true);
        save();
        return INSTANCE.autoPuzzleSlider;
    }

    public static boolean shouldRequestBatteryPermission() {
        if (INSTANCE.batteryPerm != null) return INSTANCE.batteryPerm;
        try {
            String userId = FileUtil.getRuntimeLogFile().getParentFile().getName();
            File config = "default".equals(userId) ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (config.isFile()) {
                return JsonUtil.copyMapper().readTree(FileUtil.readFromFile(config))
                        .path("modelFieldsMap").path("BaseModel").path("batteryPerm").path("value").asBoolean(true);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        return true;
    }

    private static boolean legacyModelBoolean(String field, boolean defaultValue) {
        try {
            String userId = FileUtil.getRuntimeLogFile().getParentFile().getName();
            File config = "default".equals(userId) ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (config.isFile()) {
                return JsonUtil.copyMapper().readTree(FileUtil.readFromFile(config))
                        .path("modelFieldsMap").path("BaseModel").path(field).path("value").asBoolean(defaultValue);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        return defaultValue;
    }

    public static Boolean save() {
        if (loadFailed) {
            // 上次加载失败，内存只是默认值：写盘会把用户配置整份固化，拒绝
            Log.i(TAG, "上次APP配置加载失败，本次不写盘");
            return false;
        }
        return FileUtil.write2File(toSaveStr(), new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json"));
    }

    public static synchronized AppConfig load() {
        File appConfigFile = new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json");
        loadFailed = false;
        try {
            if (appConfigFile.exists()) {
                String json = FileUtil.readFromFile(appConfigFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                // 注意：必须先加载文件再打印日志，否则开关判断仍取默认值 true，
                // 导致「运行日志已关闭」时启动进程仍写入加载日志
                Log.i("加载APP配置");
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化APP配置");
                    FileUtil.write2File(formatted, appConfigFile);
                }
            } else {
                unload();
                Log.i(TAG, "初始APP配置");
                FileUtil.write2File(toSaveStr(), appConfigFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            // 解析失败只回落默认值、绝不写盘：瞬时 IO 抖动或半份文件不该把用户配置整份重置
            Log.i(TAG, "解析APP配置失败，本次使用默认值（不写盘）");
            loadFailed = true;
            unload();
        }
        INSTANCE.setInit(true);
        return INSTANCE;
    }

    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new AppConfig());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }

}
