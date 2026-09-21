package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.entity.KVNode;

@Data
public class ConfigV2 {

    private static final String TAG = ConfigV2.class.getSimpleName();

    public static final ConfigV2 INSTANCE = new ConfigV2();

    @JsonIgnore
    private boolean init;

    private final Map<String, ModelFields> modelFieldsMap = new ConcurrentHashMap<>();

    /**
     * 最近一次与磁盘同步（load 或 save）后的配置文本。
     * 模块 App 与注入进程各持一份 ConfigV2 快照、都会整份落盘，靠它识别"文件被别的进程改过"。
     */
    @JsonIgnore
    private static String lastSyncedText;

    /** 上次同步时各字段的值快照（key = modelCode|fieldCode），用于挑出本进程真正改过的字段 */
    @JsonIgnore
    private static final Map<String, String> valueBaseline = new ConcurrentHashMap<>();

    public void setModelFieldsMap(Map<String, ModelFields> newModels) {
        modelFieldsMap.clear();
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        if (newModels == null) {
            newModels = new HashMap<>();
        }
        for (ModelConfig modelConfig : modelConfigMap.values()) {
            String modelCode = modelConfig.getCode();
            ModelFields newModelFields = new ModelFields();
            ModelFields configModelFields = modelConfig.getFields();
            ModelFields modelFields = newModels.get(modelCode);
            if (modelFields != null) {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    ModelField<?> modelField = modelFields.get(configModelField.getCode());
                    try {
                        if (modelField != null) {
                            Object value = modelField.getValue();
                            if (value != null) {
                                configModelField.setObjectValue(value);
                            }
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    newModelFields.addField(configModelField);
                }
            } else {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    newModelFields.addField(configModelField);
                }
            }
            modelFieldsMap.put(modelCode, newModelFields);
        }
    }

    public Boolean hasModelFields(String modelCode) {
        return modelFieldsMap.containsKey(modelCode);
    }

    public ModelFields getModelFields(String modelCode) {
        return modelFieldsMap.get(modelCode);
    }

    public void removeModelFields(String modelCode) {
        modelFieldsMap.remove(modelCode);
    }

    /*public void addModelFields(String modelCode, ModelFields modelFields) {
        modelFieldsMap.put(modelCode, modelFields);
    }*/

    public Boolean hasModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return false;
        }
        return modelFields.containsKey(fieldCode);
    }

    /*public ModelField getModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return null;
        }
        return modelFields.get(fieldCode);
    }*/

    /*public void removeModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return;
        }
        modelFields.remove(fieldCode);
    }*/

    /*public Boolean addModelField(String modelCode, ModelField modelField) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return false;
        }
        modelFields.put(modelCode, modelField);
        return true;
    }*/

    /*@SuppressWarnings("unchecked")
    public <T extends ModelField> T getModelFieldExt(String modelCode, String fieldCode) {
        return (T) getModelField(modelCode, fieldCode);
    }*/

    public static synchronized Boolean isModify(String userId) {
        String json = null;
        File configV2File;
        if (StringUtil.isEmpty(userId)) {
            configV2File = FileUtil.getDefaultConfigV2File();
        } else {
            configV2File = FileUtil.getConfigV2File(userId);
        }
        if (configV2File.exists()) {
            json = FileUtil.readFromFile(configV2File);
        }
        if (json != null) {
            String formatted = INSTANCE.toSaveStr();
            return formatted == null || !formatted.equals(json);
        }
        return true;
    }

    public static synchronized Boolean save(String userId, Boolean force) {
        if (!force) {
            // 本进程没有相对「上次同步」的改动时直接返回：此时的 INSTANCE 只是"比磁盘旧"，
            // 落盘只会把别的进程刚写入的配置覆盖回去
            if (!valueBaseline.isEmpty() && collectChangedFields().isEmpty()) {
                return true;
            }
            if (!isModify(userId)) {
                return true;
            }
        }
        // 磁盘在本进程上次同步之后被别的进程改过时，先把磁盘内容合并进来再落盘
        if (mergeDiskChanges(userId)) {
            // 本进程没有可写内容、磁盘上的是更新版本：跳过写入，别把别人的改动覆盖掉
            return true;
        }
        String json = INSTANCE.toSaveStr();
        boolean success;
        if (StringUtil.isEmpty(userId)) {
            userId = "默认";
            success = FileUtil.setDefaultConfigV2File(json);
        } else {
            success = FileUtil.setConfigV2File(userId, json);
        }
        
        // ========== 新增：保存成功后触发滚动备份 ==========
        if (success) {
            lastSyncedText = json;
            captureBaseline();
            FileUtil.backupConfigV2WithRolling(userId);
        }
        
        Log.record("保存配置: " + userId);
        return success;
    }
    
    public static synchronized ConfigV2 load(String userId) {
        Log.i(TAG, "开始加载配置");
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            Log.record("加载配置: " + userName);
            if (configV2File.exists()) {
                String json = FileUtil.readFromFile(configV2File);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = INSTANCE.toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化配置: " + userName);
                    FileUtil.write2File(formatted, configV2File);
                }
            } else {
                File defaultConfigV2File = FileUtil.getDefaultConfigV2File();
                if (defaultConfigV2File.exists()) {
                    String json = FileUtil.readFromFile(defaultConfigV2File);
                    JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                    Log.i(TAG, "复制新配置: " + userName);
                    FileUtil.write2File(json, configV2File);
                } else {
                    INSTANCE.setModelFieldsMap(null);
                    unload();
                    Log.i(TAG, "初始新配置: " + userName);
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            // 解析失败先用滚动备份兜底恢复，恢复不了才重置为默认（原来直接重置，整份配置就没了）
            if (!restoreFromBackup(configV2File, userId)) {
                Log.i(TAG, "重置配置: " + userName);
                INSTANCE.setModelFieldsMap(null);
                unload();
                if (configV2File != null) {
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
            }
        }
        INSTANCE.setInit(true);
        captureBaseline();
        // 记下本次同步到的文本，供 save() 判断磁盘是否被别的进程改过
        lastSyncedText = configV2File != null && configV2File.exists() ? FileUtil.readFromFile(configV2File) : null;
        Log.i(TAG, "加载配置结束");
        return INSTANCE;
    }

    public static synchronized void unload() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.reset();
                }
            }
        }
    }

    /**
     * 主配置文件解析失败时用最近一份滚动备份恢复；恢复失败返回 false，由调用方重置为默认。
     * 备份里存的就是完整的 config_v2 内容，所以恢复后可以直接写回主配置文件。
     */
    private static boolean restoreFromBackup(File configV2File, String userId) {
        try {
            // save() 落盘时把空 userId 归一成"默认"，备份文件名跟着用这个，这里保持一致
            File backupFile = FileUtil.findLatestBackupFile(StringUtil.isEmpty(userId) ? "默认" : userId);
            if (backupFile == null || !backupFile.exists()) {
                return false;
            }
            String json = FileUtil.readFromFile(backupFile);
            if (StringUtil.isEmpty(json)) {
                return false;
            }
            // 先重建为代码里的默认值再套备份，避免主文件"半解析"后的残留值混进来
            INSTANCE.setModelFieldsMap(null);
            unload();
            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
            if (configV2File != null) {
                FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
            }
            Log.record("配置解析失败，已从备份恢复: " + backupFile.getName());
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /** 记录当前各字段的值快照，用于之后判断"本进程改过哪些字段" */
    private static void captureBaseline() {
        Map<String, String> baseline = new HashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field != null && field.getCode() != null) {
                    baseline.put(fieldKey(modelEntry.getKey(), field.getCode()), String.valueOf(field.getValue()));
                }
            }
        }
        valueBaseline.clear();
        valueBaseline.putAll(baseline);
    }

    /**
     * 收集本进程相对上次同步改过的字段（key = {@code modelCode|fieldCode}，值是值的浅拷贝，
     * 便于重载磁盘内容后原样压回）。没有基线的字段一律不算本进程改动——宁可少合并，也不拿旧值覆盖新值。
     */
    private static Map<String, Object> collectChangedFields() {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field == null || field.getCode() == null) {
                    continue;
                }
                String key = fieldKey(modelEntry.getKey(), field.getCode());
                String baseline = valueBaseline.get(key);
                if (baseline == null || baseline.equals(String.valueOf(field.getValue()))) {
                    continue;
                }
                changed.put(key, copyFieldValue(field.getValue()));
            }
        }
        return changed;
    }

    /**
     * 落盘前把「磁盘上更新的内容」和「本进程自己的改动」合到一起：
     * 磁盘被别的进程改过时先重载，再把本进程改过的字段压回去，
     * 这样两边都不会丢（原来是谁最后写谁覆盖）。
     *
     * @return true 表示本进程没有可写内容（已采纳磁盘上的更新），调用方应跳过本次写入
     */
    private static boolean mergeDiskChanges(String userId) {
        try {
            File configV2File = StringUtil.isEmpty(userId) ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (!configV2File.exists() || lastSyncedText == null) {
                return false;
            }
            String disk = FileUtil.readFromFile(configV2File);
            if (disk == null || disk.equals(lastSyncedText)) {
                return false;
            }
            Map<String, Object> changed = collectChangedFields();
            // 先重载，让本进程也看到别的进程刚写入的内容
            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(disk);
            if (changed.isEmpty()) {
                // 本进程没有改动：磁盘上的才是最新状态，直接采纳，
                // 不能拿本进程的旧快照写回去（页面的 save() 只按 isModify 判断，
                // 这种情况会走到这里：文件被别的进程改过，但本进程没动过任何字段）
                lastSyncedText = disk;
                captureBaseline();
                Log.record("配置已被其他进程更新，本进程无改动，已重载并跳过写入");
                return true;
            }
            for (Map.Entry<String, Object> entry : changed.entrySet()) {
                int separator = entry.getKey().indexOf('|');
                ModelFields modelFields = INSTANCE.modelFieldsMap.get(entry.getKey().substring(0, separator));
                ModelField<?> field = modelFields == null ? null : modelFields.get(entry.getKey().substring(separator + 1));
                if (field != null) {
                    field.setObjectValue(entry.getValue());
                }
            }
            Log.record("配置已被其他进程更新，已合并本进程改动后再保存");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private static String fieldKey(String modelCode, String fieldCode) {
        return modelCode + "|" + fieldCode;
    }

    /** 字段值的浅拷贝：集合/Map/KVNode 复制一份，避免重载时被就地改掉；其余按不可变类型处理 */
    private static Object copyFieldValue(Object value) {
        if (value instanceof Set) {
            return new LinkedHashSet<>((Set<?>) value);
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<?, ?>) value);
        }
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof KVNode) {
            KVNode<?, ?> node = (KVNode<?, ?>) value;
            return new KVNode<>(node.getKey(), node.getValue());
        }
        return value;
    }

    public String toSaveStr() {
        return JsonUtil.toFormatJsonString(this);
    }

}
