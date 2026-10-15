package io.github.aw1y2z.sesame.util;

import android.content.Context;

import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.File;
import java.util.Calendar;
import java.util.List;

import io.github.aw1y2z.sesame.R;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import androidx.annotation.Keep;
import lombok.Data;

@Data
public class Statistics {
    
    private static final String TAG = Statistics.class.getSimpleName();
    
    public static final Statistics INSTANCE = new Statistics();
    private static String loadedUserId;
    
    private TimeStatistics year = new TimeStatistics();
    private TimeStatistics month = new TimeStatistics();
    private TimeStatistics day = new TimeStatistics();

    /** 本轮（一次执行周期）收取的能量。仅内存计数、不落盘（静态字段不进 Jackson） */
    private static volatile int roundCollected = 0;

    /** 本轮收取能量 */
    public static int getRoundCollected() {
        return roundCollected;
    }

    /**
     * 新一轮开始时清零本轮收取计数。
     * <p>synchronized：与 addData 的 {@code roundCollected += i} 互斥，
     * 否则清零点正好落在累加中间会丢掉这一次累加（表现为通知里「本轮收取能量」偏小）。
     */
    public static synchronized void resetRoundCollected() {
        roundCollected = 0;
    }
    
    /**
     * 用服务端权威值**覆盖**今日收取能量（而非累加）。
     * <p>逐点 {@link #addData} 只要漏掉一个入口，当日账目就永久偏小，故改由服务端明细直接定值。
     * <p>month/year 按差值同步追加：这两个是历史累计维度，被覆盖的今日值此前已计入它们。
     *
     * @param todayCollected 服务端给出的今日收取能量；传负数表示无效值、忽略
     * @return 本次覆盖造成的日维度增量（可能为负）
     */
    public static synchronized int setDayCollectedFromServer(int todayCollected) {
        if (todayCollected < 0) {
            return 0;
        }
        String userId = UserIdMap.getCurrentUid();
        if (!AccountFolderName.isValidUid(userId)) return 0;
        if (!userId.equals(loadedUserId)) load(userId);
        updateDay(MyUtils.getInstance());
        int delta = todayCollected - INSTANCE.day.collected;
        if (delta == 0) {
            return 0;
        }
        INSTANCE.day.collected = todayCollected;
        INSTANCE.month.collected += delta;
        INSTANCE.year.collected += delta;
        return delta;
    }

    /**
     * synchronized：与 save/load/unload 共用 Statistics.class 监视器。
     * <p>各个模块跑在各自线程上、都会累加同一个计数器，原先的 += 既会丢更新，
     * 也可能正好撞上 save() 的序列化过程。
     */
    public static synchronized void addData(DataType dt, int i) {
        String userId = UserIdMap.getCurrentUid();
        if (!AccountFolderName.isValidUid(userId)) return;
        if (!userId.equals(loadedUserId)) load(userId);
        Statistics stat = INSTANCE;
        switch (dt) {
            case COLLECTED:
                stat.day.collected += i;
                stat.month.collected += i;
                stat.year.collected += i;
                roundCollected += i;
                break;
            case HELPED:
                stat.day.helped += i;
                stat.month.helped += i;
                stat.year.helped += i;
                break;
            case WATERED:
                stat.day.watered += i;
                stat.month.watered += i;
                stat.year.watered += i;
                break;
            case WATEREDCOUNT:
                stat.day.wateredcount += i;
                stat.month.wateredcount += i;
                stat.year.wateredcount += i;
                break;
            case WATERINGCOUNT:
                stat.day.wateringcount += i;
                stat.month.wateringcount += i;
                stat.year.wateringcount += i;
                break;
        }
    }
    
    public static synchronized int getData(TimeType tt, DataType dt) {
        return INSTANCE.dataOf(tt, dt);
    }

    public int dataOf(TimeType tt, DataType dt) {
        TimeStatistics ts;
        switch (tt) {
            case YEAR:
                ts = year;
                break;
            case MONTH:
                ts = month;
                break;
            default:
                ts = day;
                break;
        }
        switch (dt) {
            case TIME:
                return ts.time;
            case COLLECTED:
                return ts.collected;
            case HELPED:
                return ts.helped;
            case WATERED:
                return ts.watered;
            case WATEREDCOUNT:
                return ts.wateredcount;
            case WATERINGCOUNT:
                return ts.wateringcount;
            default:
                return 0;
        }
    }

    public static Statistics aggregate(List<Statistics> list) {
        Statistics total = new Statistics();
        int yearTime = 0;
        int monthTime = 0;
        int dayTime = 0;
        for (Statistics s : list) {
            if (s == null) {
                continue;
            }
            total.year.add(s.year);
            total.month.add(s.month);
            total.day.add(s.day);
            yearTime = Math.max(yearTime, s.year.time);
            monthTime = Math.max(monthTime, s.month.time);
            dayTime = Math.max(dayTime, s.day.time);
        }
        total.year.time = yearTime;
        total.month.time = monthTime;
        total.day.time = dayTime;
        return total;
    }

    public static Statistics readFrom(String userId) {
        Statistics statistics = new Statistics();
        try {
            if (!AccountFolderName.isValidUid(userId)) return statistics;
            File file = FileUtil.getStatisticsFile(userId);
            if (!file.exists()) {
                return statistics;
            }
            String json = FileUtil.readFromFile(file);
            JsonUtil.copyMapper().readerForUpdating(statistics).readValue(json);
            // 只动内存地按当前时间归零：模块当天还没跑时，首页不该还显示昨天的「今日」
            statistics.updateDayOf(MyUtils.getInstance());
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return new Statistics();
        }
        return statistics;
    }

    public boolean updateDayOf(Calendar nowCalendar) {
        int ye = nowCalendar.get(Calendar.YEAR);
        int mo = nowCalendar.get(Calendar.MONTH) + 1;
        int da = nowCalendar.get(Calendar.DAY_OF_MONTH);
        if (ye != year.time) {
            year.reset(ye);
            month.reset(mo);
            day.reset(da);
        }
        else if (mo != month.time) {
            month.reset(mo);
            day.reset(da);
        }
        else if (da != day.time) {
            day.reset(da);
        }
        else {
            return false;
        }
        return true;
    }
    
    public static String getText() {
        
        StringBuilder table = new StringBuilder();
        // 添加表头
        table.append("今年  收: ").append(getData(TimeType.YEAR, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.YEAR, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.YEAR, DataType.WATERED));
        table.append("\n今月  收: ").append(getData(TimeType.MONTH, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.MONTH, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.MONTH, DataType.WATERED));
        table.append("\n今日  收: ").append(getData(TimeType.DAY, DataType.COLLECTED)).append(" 帮: ").append(getData(TimeType.DAY, DataType.HELPED)).append(" 浇: ").append(getData(TimeType.DAY, DataType.WATERED));
        table.append("\n被水次数  日: ").append(getData(TimeType.DAY, DataType.WATEREDCOUNT)).append(" 月: ").append(getData(TimeType.MONTH, DataType.WATEREDCOUNT)).append(" 年: ").append(getData(TimeType.YEAR, DataType.WATEREDCOUNT));
        table.append("\n浇水次数  日: ").append(getData(TimeType.DAY, DataType.WATERINGCOUNT)).append(" 月: ").append(getData(TimeType.MONTH, DataType.WATERINGCOUNT)).append(" 年: ").append(getData(TimeType.YEAR, DataType.WATERINGCOUNT));
        return table.toString();
    }
    
    public static String getText(Context context) {
        return getText(context.getString(R.string.year), context.getString(R.string.month), context.getString(R.string.day), context.getString(R.string.collected), context.getString(R.string.helped), context.getString(R.string.watered), context.getString(R.string.wateredcount),
                context.getString(R.string.wateringcount));
    }
    
    public static String getText(String year, String month, String day, String collected, String helped, String watered, String wateredcount, String wateringcount) {
        return year + "  " + collected + ": " + getData(TimeType.YEAR, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.YEAR, DataType.HELPED) + " " + watered + ": " + getData(TimeType.YEAR, DataType.WATERED) +
               "\n" + month + "  " + collected + ": " + getData(TimeType.MONTH, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.MONTH, DataType.HELPED) + " " + watered + ": " + getData(TimeType.MONTH, DataType.WATERED) +
               "\n" + day + "  " + collected + ": " + getData(TimeType.DAY, DataType.COLLECTED) + " " + helped + ": " + getData(TimeType.DAY, DataType.HELPED) + " " + watered + ": " + getData(TimeType.DAY, DataType.WATERED) +
               "\n" + wateredcount + "  " + "日" + ": " + getData(TimeType.DAY, DataType.WATEREDCOUNT) + "; " + "月" + ": " + getData(TimeType.MONTH, DataType.WATEREDCOUNT) + ";  " + "年" + ": " + getData(TimeType.YEAR, DataType.WATEREDCOUNT) +
               "\n" + wateringcount + "  " + "日" + ": " + getData(TimeType.DAY, DataType.WATERINGCOUNT) + ";  " + "月" + ": " + getData(TimeType.MONTH, DataType.WATERINGCOUNT) + ";  " + "年" + ": " + getData(TimeType.YEAR, DataType.WATERINGCOUNT);
    }
    
    public static synchronized Statistics load() {
        return load(UserIdMap.getCurrentUid());
    }

    public static synchronized Statistics load(String userId) {
        unload();
        if (!AccountFolderName.isValidUid(userId)) return INSTANCE;
        loadedUserId = userId;
        try {
            File statisticsFile = FileUtil.getStatisticsFile(userId);
            if (statisticsFile.exists()) {
                String json = FileUtil.readFromFile(statisticsFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = JsonUtil.toFormatJsonString(INSTANCE);
                if (formatted != null && !formatted.equals(json)) {
                    // 回写只允许改格式、不许改数据
                    if (JsonUtil.isRewriteLossless(json, formatted)) {
                        Log.i(TAG, "重新格式化 statistics.json");
                        FileUtil.write2File(formatted, statisticsFile);
                    } else {
                        Log.i(TAG, "statistics.json 加载结果与磁盘数据不一致，已跳过重新格式化");
                    }
                }
            }
            else {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
                Log.i(TAG, "初始化 statistics.json");
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), statisticsFile);
            }
        }
        catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "统计文件格式有误，已重置统计文件");
            try {
                JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
                FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatisticsFile(userId));
            }
            catch (JsonMappingException e) {
                Log.printStackTrace(TAG, e);
            }
        }
        return INSTANCE;
    }
    
    public static synchronized void unload() {
        loadedUserId = null;
        resetRoundCollected();
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new Statistics());
        }
        catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static synchronized void save() {
        save(TimeUtil.getNow());
    }
    
    public static synchronized void save(Calendar nowCalendar) {
        String userId = UserIdMap.getCurrentUid();
        if (!AccountFolderName.isValidUid(userId)) return;
        if (!userId.equals(loadedUserId)) load(userId);
        if (updateDay(nowCalendar)) {
            Log.system(TAG, "重置 statistics.json");
        }
        else {
            // 每次落盘都记一行会淹没有效日志（实测约 75 行/天），降为由「抓包记录」开关控制的调试日志
            Log.debug(TAG + ", 保存 statistics.json");
        }
        FileUtil.write2File(JsonUtil.toFormatJsonString(INSTANCE), FileUtil.getStatisticsFile(userId));
    }
    
    public static Boolean updateDay(Calendar nowCalendar) {
        return INSTANCE.updateDayOf(nowCalendar);
    }
    
    public enum TimeType {
        YEAR, MONTH, DAY
    }
    
    public enum DataType {
        TIME, COLLECTED, HELPED, WATERED, WATEREDCOUNT, WATERINGCOUNT
    }
    
    @Data
    @Keep
    public static class TimeStatistics {
        int time;
        int collected, helped, watered, wateredcount, wateringcount;
        
        public TimeStatistics() {
        }
        
        TimeStatistics(int i) {
            reset(i);
        }
        
        void add(TimeStatistics other) {
            collected += other.collected;
            helped += other.helped;
            watered += other.watered;
            wateredcount += other.wateredcount;
            wateringcount += other.wateringcount;
        }

        public void reset(int i) {
            time = i;
            collected = 0;
            helped = 0;
            watered = 0;
            wateredcount = 0;
            wateringcount = 0;
        }
    }
    
}
