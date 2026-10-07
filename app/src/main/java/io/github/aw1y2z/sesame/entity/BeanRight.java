package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.aw1y2z.sesame.util.idMap.BeanRightIdMap;

public final class BeanRight extends IdAndName {
    private BeanRight(String key, String value) { id = key; name = value; }
    public static List<BeanRight> getList() {
        List<BeanRight> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : BeanRightIdMap.getMap().entrySet()) result.add(new BeanRight(entry.getKey(), entry.getValue()));
        return result;
    }
}
