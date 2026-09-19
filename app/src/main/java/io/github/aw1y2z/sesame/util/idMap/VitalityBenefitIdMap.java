package io.github.aw1y2z.sesame.util.idMap;

import java.util.Map;

import io.github.aw1y2z.sesame.util.FileUtil;

public class VitalityBenefitIdMap {

    private static final StringMapStore STORE = new StringMapStore(FileUtil::getVitalityBenefitIdMap);

    public static Map<String, String> getMap() {
        return STORE.getMap();
    }

    public static void add(String key, String value) {
        STORE.add(key, value);
    }

    public static void remove(String key) {
        STORE.remove(key);
    }

    public static void load(String userId) {
        STORE.load(userId);
    }

    public static boolean save(String userId) {
        return STORE.save(userId);
    }

}