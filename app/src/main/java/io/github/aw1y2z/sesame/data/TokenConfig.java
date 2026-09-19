package io.github.aw1y2z.sesame.data;

import android.os.Build;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import lombok.Data;

@Data
public class TokenConfig {
    
    private static final String TAG = TokenConfig.class.getSimpleName();
    
    public static final TokenConfig INSTANCE = new TokenConfig();
    
    @JsonIgnore
    private boolean init;
    
    // sports
    private final Queue<String> customWalkPathIdQueue = new LinkedList<>();
    
    // farm
    private final Map<String, String> answerList = new HashMap<>();
    
    // ecoLife
    private final Set<Map<String, String> > dishImageList = new HashSet<>();
    
    public static synchronized String getCustomWalkPathId(Set<String> customWalkPathIdListSet) {
        String pathId = INSTANCE.customWalkPathIdQueue.poll();
        if (pathId != null) {
            save();
            return pathId;
        }
        List<String> list = new ArrayList<>(customWalkPathIdListSet);
        if (!list.isEmpty()) {
            return list.get(RandomUtil.nextInt(0, list.size() - 1));
        }
        return null;
    }
    
    public static synchronized Boolean addCustomWalkPathIdQueue(String pathId) {
        INSTANCE.customWalkPathIdQueue.add(pathId);
        return save();
    }
    
    public static synchronized Boolean clearCustomWalkPathIdQueue() {
        TokenConfig tokenConfig = INSTANCE;
        if (!tokenConfig.customWalkPathIdQueue.isEmpty()) {
            tokenConfig.customWalkPathIdQueue.clear();
            return save();
        }
        return true;
    }
    
    public static synchronized String getAnswer(String question) {
        Calendar calendar = TimeUtil.getToday();
        long timeMillis = calendar.getTimeInMillis();
        return  INSTANCE.answerList.get(timeMillis + "::" + question);
    }
    
    public static synchronized void saveAnswer(String question, String answer) {
        Calendar todayCalendar = TimeUtil.getToday();
        long todayTimeMillis = todayCalendar.getTimeInMillis();
        long tomorrowTimeMillis = todayTimeMillis + TimeUnit.DAYS.toMillis(1);
        String todayTimeMillisStr = String.valueOf(todayTimeMillis);
        String tomorrowTimeMillisStr = String.valueOf(tomorrowTimeMillis);
        
        question = tomorrowTimeMillis + "::" + question;
        TokenConfig tokenConfig = INSTANCE;
        if (Objects.equals(tokenConfig.answerList.get(question), answer)) {
            return;
        }
        tokenConfig.answerList.put(question, answer);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tokenConfig.answerList.entrySet().removeIf(
                    entry -> !entry.getKey().startsWith(todayTimeMillisStr)
                             && !entry.getKey().startsWith(tomorrowTimeMillisStr));
        } else {
            Iterator<Map.Entry<String, String>> iterator = tokenConfig.answerList.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                if (!entry.getKey().startsWith(todayTimeMillisStr)
                    && !entry.getKey().startsWith(tomorrowTimeMillisStr)) {
                    iterator.remove();
                }
            }
        }
        save();
    }
    
    public static synchronized Map<String, String> getRandomDishImage() {
        List<Map<String, String> > list = new ArrayList<>(INSTANCE.dishImageList);
        if (list.isEmpty()) {
            return null;
        }
        int pos = RandomUtil.nextInt(0, list.size() - 1);
        Map<String, String> dishImage = list.get(pos);
        return checkDishImage(dishImage) ? dishImage : null;
    }
    
    public static synchronized void saveDishImage(Map<String, String> dishImage) {
        if (!checkDishImage(dishImage)) {
            return;
        }
        // 先按磁盘内容刷新：「清空光盘行动图片」在 UI 进程执行，不重读会把刚清掉的又写回去
        reloadDishImageList();
        TokenConfig tokenConfig = INSTANCE;
        if (!tokenConfig.dishImageList.contains(dishImage)) {
            tokenConfig.dishImageList.add(dishImage);
            save();
        }
    }
    
    /**
     * 手动写入一组光盘行动图片 ID，跳过"需要先手动完成一次真实打卡"这一步。
     * 对齐 GR2026 main_my TokenConfig.java#writeDishImage（提交 2a6d496b）。
     */
    public static Boolean writeDishImage(String beforeMealsId, String afterMealsId) {
        Map<String, String> dishImage = new HashMap<>();
        dishImage.put("BEFORE_MEALS", beforeMealsId);
        dishImage.put("AFTER_MEALS", afterMealsId);
        if (!checkDishImage(dishImage)) {
            Log.record("写入光盘图片失败: ID 为空或餐前餐后 ID 相同");
            return false;
        }
        Log.record("写入光盘图片: 餐前=" + beforeMealsId + ", 餐后=" + afterMealsId);
        saveDishImage(dishImage);
        return true;
    }

    /** 随机生成一组不冲突的图片 ID 并写入，供不想手动填 ID 时使用。 */
    public static Boolean writeDishImageWithRandomIds() {
        String beforeMealsId = java.util.UUID.randomUUID().toString().replace("-", "");
        String afterMealsId = java.util.UUID.randomUUID().toString().replace("-", "");
        return writeDishImage(beforeMealsId, afterMealsId);
    }

    public static synchronized int getDishImageCount() {
        // 以磁盘为准：UI 询问数量时本进程的副本可能已被另一进程的「清空」改过
        reloadDishImageList();
        return INSTANCE.dishImageList.size();
    }
    
    public static synchronized Boolean clearDishImage() {
        TokenConfig.INSTANCE.dishImageList.clear();
        return save();
    }
    
    public static Boolean checkDishImage(Map<String, String> dishImage) {
        if (dishImage == null) {
            return false;
        }
        String beforeMealsImageId = dishImage.get("BEFORE_MEALS");
        String afterMealsImageId = dishImage.get("AFTER_MEALS");
        return !StringUtil.isEmpty(beforeMealsImageId)
               && !StringUtil.isEmpty(afterMealsImageId)
               && !Objects.equals(beforeMealsImageId, afterMealsImageId);
    }
    
    /**
     * 用磁盘上的内容刷新内存里的 dishImageList。
     * <p>
     * 「清空光盘行动图片」由模块 App 的 UI 进程执行，而图片的写入发生在注入进程（支付宝），
     * 两个进程各持一份内存副本：不重新读盘的话，注入进程后续 save() 会把 UI 侧刚清掉的
     * 图片又整份写回去，表现为「清空无效」。
     * <p>
     * 这里显式 clear + addAll，不依赖 Jackson 更新已有对象时的集合合并语义（后者只增不减）。
     */
    private static void reloadDishImageList() {
        try {
            File tokenConfigFile = FileUtil.getTokenConfigFile();
            if (!tokenConfigFile.exists()) {
                return;
            }
            TokenConfig fromDisk = JsonUtil.parseObject(FileUtil.readFromFile(tokenConfigFile), TokenConfig.class);
            if (fromDisk == null) {
                return;
            }
            Set<Map<String, String>> diskList = fromDisk.getDishImageList();
            INSTANCE.dishImageList.clear();
            if (diskList != null) {
                INSTANCE.dishImageList.addAll(diskList);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }
    
    public static synchronized Boolean save() {
        Log.record("保存Token配置");
        return FileUtil.setTokenConfigFile(toSaveStr());
    }
    
    public static synchronized TokenConfig load() {
        File tokenConfigFile = FileUtil.getTokenConfigFile();
        try {
            if (tokenConfigFile.exists()) {
                String json = FileUtil.readFromFile(tokenConfigFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化Token配置");
                    FileUtil.write2File(formatted, tokenConfigFile);
                }
            } else {
                unload();
                Log.i(TAG, "初始Token配置");
                FileUtil.write2File(toSaveStr(), tokenConfigFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            Log.i(TAG, "重置Token配置");
            try {
                unload();
                FileUtil.write2File(toSaveStr(), tokenConfigFile);
            } catch (Exception e) {
                Log.printStackTrace(TAG, t);
            }
        }
        INSTANCE.setInit(true);
        return INSTANCE;
    }
    
    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new TokenConfig());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }
    
    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }
}
