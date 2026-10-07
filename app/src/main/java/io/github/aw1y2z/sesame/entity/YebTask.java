package io.github.aw1y2z.sesame.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.github.aw1y2z.sesame.util.idMap.YebTaskIdMap;

public final class YebTask extends IdAndName {
    private YebTask(String key, String value) { id = key; name = value; }
    public static List<YebTask> getList() {
        List<YebTask> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : YebTaskIdMap.getMap().entrySet()) result.add(new YebTask(entry.getKey(), entry.getValue()));
        return result;
    }
}
