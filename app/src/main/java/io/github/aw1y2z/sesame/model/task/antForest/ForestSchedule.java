package io.github.aw1y2z.sesame.model.task.antForest;

import java.util.Calendar;
import io.github.aw1y2z.sesame.util.MyUtils;

/** 森林周期与道具排期，所有业务时间均为 GMT+8。 */
final class ForestSchedule {
    private ForestSchedule() { }

    private static int minute(String value, boolean end) {
        if (value == null || !value.matches("[0-9]{4}")) return -1;
        int hour = Integer.parseInt(value.substring(0, 2)), min = Integer.parseInt(value.substring(2));
        return hour < 24 && min < 60 ? hour * 60 + min : end && hour == 24 && min == 0 ? 1440 : -1;
    }

    /** 最早可开始的窗口时刻；非法配置为-1，支持跨午夜及多个窗口。 */
    static long nextWindow(String text, long from) {
        if (text == null || text.isEmpty() || text.length() > 512 || from < 0) return -1;
        String[] ranges = text.split(",", -1);
        if (ranges.length > 20) return -1;
        Calendar day = MyUtils.getInstance();
        day.setTimeInMillis(from);
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0);
        long best = Long.MAX_VALUE;
        for (String range : ranges) {
            String[] parts = range.trim().split("-", -1);
            if (parts.length != 2) return -1;
            int start = minute(parts[0], false), end = minute(parts[1], true);
            if (start < 0 || end < 0 || start == end) return -1;
            for (int offset = -1; offset <= 1; offset++) {
                Calendar left = (Calendar) day.clone(); left.add(Calendar.DATE, offset); left.add(Calendar.MINUTE, start);
                Calendar right = (Calendar) day.clone(); right.add(Calendar.DATE, offset + (end < start ? 1 : 0)); right.add(Calendar.MINUTE, end);
                long candidate = Math.max(from, left.getTimeInMillis());
                if (candidate < right.getTimeInMillis()) best = Math.min(best, candidate);
            }
        }
        return best == Long.MAX_VALUE ? -1 : best;
    }

    /** AG/Sen时间点及禁止窗口：-1为随时，HHmm为时间点，!HHmm-HHmm为禁用窗口。 */
    static long nextTrigger(String text, long from) {
        if (text == null || text.isEmpty() || text.length() > 512 || from < 0) return -1;
        String[] items = text.split(",", -1);
        if (items.length > 20) return -1;
        java.util.Set<Integer> points = new java.util.HashSet<>();
        StringBuilder blocked = new StringBuilder(); boolean always = false;
        for (String item : items) {
            item = item.trim();
            if ("-1".equals(item)) always = true;
            else if (item.startsWith("!")) {
                String range = item.substring(1);
                if (nextWindow(range, from) < 0) return -1;
                if (blocked.length() > 0) blocked.append(','); blocked.append(range);
            } else {
                int point = minute(item, false); if (point < 0) return -1; points.add(point);
            }
        }
        if (always && !points.isEmpty()) return -1;
        if (!always && points.isEmpty()) always = true;
        Calendar time = MyUtils.getInstance(); time.setTimeInMillis(from);
        // ponytail: 有界扫描最多48小时的分钟；规则增加秒级精度时再改为端点算法。
        for (int i = 0; i <= 2880; i++) {
            long candidate = time.getTimeInMillis();
            int nowMinute = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE);
            if ((always || points.contains(nowMinute)) && (blocked.length() == 0 || nextWindow(blocked.toString(), candidate) != candidate)) return candidate;
            time.set(Calendar.SECOND, 0); time.set(Calendar.MILLISECOND, 0); time.add(Calendar.MINUTE, 1);
        }
        return -1;
    }

    static String pointKey(String text, long now) {
        if (text == null) return "";
        for (String item : text.split(",", -1)) if ("-1".equals(item.trim())) return "";
        Calendar time = MyUtils.getInstance(); time.setTimeInMillis(now);
        int value = time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE);
        for (String item : text.split(",", -1)) if (minute(item.trim(), false) == value) return Integer.toString(value);
        return "";
    }

    static boolean hasPoints(String text) {
        if (text == null) return false;
        for (String item : text.split(",", -1)) if (minute(item.trim(), false) >= 0) return true;
        return false;
    }
}
