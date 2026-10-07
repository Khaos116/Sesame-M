package io.github.aw1y2z.sesame.util.idMap;

import java.util.Map;
import io.github.aw1y2z.sesame.util.FileUtil;

public final class SesameGiftIdMap {
    private static final StringMapStore STORE = new StringMapStore(FileUtil::getSesameGiftIdMapFile);
    public static Map<String, String> getMap() { return STORE.getMap(); }
    public static void add(String key, String value) { STORE.add(key, value); }
    public static void load(String uid) { STORE.load(uid); }
    public static boolean save(String uid) { return STORE.save(uid); }
}
