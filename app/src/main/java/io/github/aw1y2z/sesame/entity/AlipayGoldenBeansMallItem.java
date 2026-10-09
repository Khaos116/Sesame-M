package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.idMap.GoldenBeansMallItemMap;

public class AlipayGoldenBeansMallItem extends IdAndName {
    private static List<AlipayGoldenBeansMallItem> list;

    public AlipayGoldenBeansMallItem(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayGoldenBeansMallItem> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : GoldenBeansMallItemMap.getMap().entrySet()) {
                list.add(new AlipayGoldenBeansMallItem(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    public static void clear() {
        list = null;
    }

}