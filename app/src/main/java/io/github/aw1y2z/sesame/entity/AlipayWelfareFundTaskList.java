package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.idMap.WelfareFundTaskListMap;

public class AlipayWelfareFundTaskList extends IdAndName {
    private static List<AlipayWelfareFundTaskList> list;

    public AlipayWelfareFundTaskList(String i, String n) {
        id = i;
        name = n;
    }

    public static List<AlipayWelfareFundTaskList> getList() {
        if (list == null) {
            list = new ArrayList<>();
            for (Map.Entry<String, String> entry : WelfareFundTaskListMap.getMap().entrySet()) {
                list.add(new AlipayWelfareFundTaskList(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    public static void remove(String id) {
        getList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
    }

    /** 候选列表变化后调用，让配置页下次读取时重建选项 */
    public static void clear() {
        list = null;
    }

}
