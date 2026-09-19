package io.github.aw1y2z.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 自动拉黑记录：保存由模块自动加入黑名单的任务及其日期，用于"超期自动解禁重试"。
 * <p>
 * 键格式 {@code Module|listTitle|taskTitle}；值格式 {@code hits;lastDay;blackDay}
 * （天序号，{@code blackDay=0} 表示仍在"连续命中观察期"、尚未拉黑）。
 * <p>
 * 只记录模块自动拉黑的任务，用户手动加入的黑名单不在此表内，因此不会被自动解禁。
 */
public class AutoBlackListMap {

    private static final Map<String, String> idMap = new ConcurrentHashMap<>();

    private static final Map<String, String> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    private static volatile boolean loaded = false;

    public static Map<String, String> getMap() {
        return readOnlyIdMap;
    }

    public static String get(String key) {
        return idMap.get(key);
    }

    public static Set<String> keys() {
        return readOnlyIdMap.keySet();
    }

    public static synchronized void put(String key, String value) {
        idMap.put(key, value);
    }

    public static synchronized void remove(String key) {
        idMap.remove(key);
    }

    /** 首次访问时确保已从磁盘加载（记录只由模块自己写入，加载一次即可） */
    public static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    public static synchronized void load() {
        idMap.clear();
        loaded = true;
        try {
            String body = FileUtil.readFromFile(FileUtil.getAutoBlackListMapFile());
            if (!body.isEmpty()) {
                Map<String, String> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, String>>() {
                });
                if (newMap != null) {
                    idMap.putAll(newMap);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static synchronized boolean save() {
        return FileUtil.write2File(JsonUtil.toJsonString(idMap), FileUtil.getAutoBlackListMapFile());
    }

    public static synchronized void clear() {
        idMap.clear();
    }

    private AutoBlackListMap() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
