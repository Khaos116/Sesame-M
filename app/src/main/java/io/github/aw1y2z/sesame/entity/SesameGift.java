package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.aw1y2z.sesame.util.idMap.SesameGiftIdMap;

public final class SesameGift extends IdAndName {
    private SesameGift(String key, String value) { id = key; name = value; }
    public static List<SesameGift> getList() {
        List<SesameGift> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : SesameGiftIdMap.getMap().entrySet()) result.add(new SesameGift(entry.getKey(), entry.getValue()));
        return result;
    }
}
