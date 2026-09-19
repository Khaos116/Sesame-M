package io.github.aw1y2z.sesame.data;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.Objects;

/**
 * @author Constanline
 * @since 2023/08/18
 */
public class RuntimeInfo {
    private static final String TAG = RuntimeInfo.class.getSimpleName();

    private static volatile RuntimeInfo instance;

    private final String userId;

    private JSONObject joAll;

    private JSONObject joCurrent;

    public enum RuntimeInfoKey {
        ForestPauseTime
    }

    /**
     * 原先的"判断 + 新建并赋值"不是原子的：两个线程可能各自 new 一个实例，
     * 后写入的那个会把先写入的那份（含刚 put 的字段）整体丢掉，表现为配置莫名回退。
     * 改为「常用路径不加锁 + 只在需要重建时加锁」的双重检查。
     */
    public static RuntimeInfo getInstance() {
        RuntimeInfo local = instance;
        if (local != null && Objects.equals(local.userId, UserIdMap.getCurrentUid())) {
            return local;
        }
        synchronized (RuntimeInfo.class) {
            if (instance == null || !Objects.equals(instance.userId, UserIdMap.getCurrentUid())) {
                instance = new RuntimeInfo();
            }
            return instance;
        }
    }

    private RuntimeInfo() {
        userId = UserIdMap.getCurrentUid();
        String content = FileUtil.readFromFile(FileUtil.runtimeInfoFile(userId));
        joAll = MyUtils.newJSONObject(content);
        try {
            if (!joAll.has(userId)) {
                joAll.put(userId, new JSONObject());
            }
        } catch (Exception ignored) {
        }
        JSONObject current = joAll.optJSONObject(userId);
        joCurrent = current != null ? current : new JSONObject();
    }

    public synchronized void save() {
        FileUtil.write2File(joAll.toString(), FileUtil.runtimeInfoFile(userId));
    }

    public Object get(RuntimeInfoKey key) throws JSONException {
        return joCurrent.opt(key.name());
    }

    public String getString(String key) {
        return joCurrent.optString(key);
    }

    public Long getLong(String key, long def) {
        return joCurrent.optLong(key, def);
    }

    public boolean getBool(String key, boolean def) {
        return joCurrent.optBoolean(key, def);
    }

    public String getString(RuntimeInfoKey key) {
        return joCurrent.optString(key.name());
    }

    public Long getLong(RuntimeInfoKey key) {
        return joCurrent.optLong(key.name(), 0L);
    }

    public void put(RuntimeInfoKey key, Object value) {
        put(key.name(), value);
    }

    public synchronized void put(String key, Object value) {
        try {
            joCurrent.put(key, value);
            joAll.put(userId, joCurrent);
        } catch (JSONException e) {
            Log.err(TAG, "put err:", e);
        }
        save();
    }

    /** 删除所有以 prefix 开头的 key，给切版本后清冷却记录用。 */
    public void clearPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return;
        java.util.Iterator<String> keys = joCurrent.keys();
        java.util.ArrayList<String> remove = new java.util.ArrayList<>();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(prefix)) remove.add(key);
        }
        for (String key : remove) joCurrent.remove(key);
        try { joAll.put(userId, joCurrent); } catch (JSONException ignored) { }
        save();
    }
}
