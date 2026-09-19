package io.github.aw1y2z.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * idMap 系列（29 个 `Map&lt;String,String&gt;` 缓存类）的公共实现。
 * <p>原先每个类都各写一遍完全相同的「内存 Map + 只读视图 + get/getMap/add/remove/load/save/clear」，
 * 现在逻辑集中在这里，各 idMap 类退化为只声明"文件从哪来"的门面，方法的静态签名保持不变（调用方无需改动）。
 * <p>同步语义与原先一致：原先是对各自的 Class 对象上锁，现在是对各自的 store 实例上锁——
 * 一个 idMap 类一个实例，互不干扰。
 * <p>不适用者保持原样：{@code AutoBlackListMap}（put/keys/懒加载/私有构造）与
 * {@code UserIdMap}（值为 UserEntity，含账号序号与反射）。
 */
class StringMapStore {

    private final Map<String, String> idMap = new ConcurrentHashMap<>();

    private final Map<String, String> readOnlyIdMap = Collections.unmodifiableMap(idMap);

    /**
     * 文件定位：不带账号的 idMap 忽略入参 userId；带账号的 idMap 用它拼出路径。
     */
    private final Function<String, File> fileProvider;

    StringMapStore(Function<String, File> fileProvider) {
        this.fileProvider = fileProvider;
    }

    Map<String, String> getMap() {
        return readOnlyIdMap;
    }

    String get(String key) {
        return idMap.get(key);
    }

    synchronized void add(String key, String value) {
        idMap.put(key, value);
    }

    synchronized void remove(String key) {
        idMap.remove(key);
    }

    /** 先清空再整份载入；文件不存在或内容为空时保持为空，解析失败只记栈不抛 */
    synchronized void load(String userId) {
        idMap.clear();
        try {
            String body = FileUtil.readFromFile(fileProvider.apply(userId));
            if (!body.isEmpty()) {
                Map<String, String> newMap = JsonUtil.parseObject(body, new TypeReference<Map<String, String>>() {
                });
                idMap.putAll(newMap);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 整份覆盖写回 */
    synchronized boolean save(String userId) {
        return FileUtil.write2File(JsonUtil.toJsonString(idMap), fileProvider.apply(userId));
    }

    synchronized void clear() {
        idMap.clear();
    }

}
