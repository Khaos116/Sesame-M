package io.github.aw1y2z.sesame.model.task.videoRewards;

import java.util.Calendar;
import java.util.TimeZone;

/** 兼容旧模型存的预约成功时间戳。移植自 GR 分支，见 doc/MyFix.md。 */
public final class VideoReservationPolicy {
    private VideoReservationPolicy() { }

    /** 保留GMT+8同一天的预约记录；未来时间戳视为时钟回退，抑制重试。 */
    public static boolean blocksLegacyAttempt(long now, long previous) {
        if (previous <= 0L) return false;
        if (previous >= now) return true;
        Calendar current = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        Calendar saved = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        current.setTimeInMillis(now);
        saved.setTimeInMillis(previous);
        return current.get(Calendar.ERA) == saved.get(Calendar.ERA)
                && current.get(Calendar.YEAR) == saved.get(Calendar.YEAR)
                && current.get(Calendar.DAY_OF_YEAR) == saved.get(Calendar.DAY_OF_YEAR);
    }
}
