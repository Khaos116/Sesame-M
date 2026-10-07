package io.github.aw1y2z.sesame.model.task.cacheMaintenance;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Calendar;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** SJ CacheCleaner cache边界；保留新缓存，不接受用户输入路径。 */
public class CacheMaintenance extends ModelTask {
    private StringModelField cleanCacheTime;
    private IntegerModelField cleanCacheOlderHours;
    private String scheduledId;
    @Override public String getName() { return "支付宝缓存维护"; }
    @Override public ModelGroup getGroup() { return ModelGroup.OTHER; }
    @Override public ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(cleanCacheTime = new StringModelField("cleanCacheTime", "清理缓存时间（HHmm，东八区）", "0430"));
        fields.addField(cleanCacheOlderHours = new IntegerModelField("cleanCacheOlderHours", "仅清理超过多少小时未修改的缓存", 24, 1, 720));
        cleanCacheOlderHours.setDescription("仅支付宝内部/外部cache目录，保留目录根、符号链接、新文件和账号数据；每次最多5000项/2秒。模块默认关闭。");
        return fields;
    }
    @Override public Boolean check() { return isEnable() && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME; }
    @Override public void run() {
        if (!check()) return;
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return;
        long now = System.currentTimeMillis(), next = nextTime(cleanCacheTime.getValue(), now);
        if (next < 0) { Log.record("缓存维护时间无效，跳过"); return; }
        boolean cancelled = false;
        try {
            if (next == now && !Status.hasFlagToday("cache::attempt")) {
                Context context = ApplicationHook.getContext();
                if (context == null || !"com.eg.android.AlipayGphone".equals(context.getPackageName())) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || !check()) return;
                Status.flagToday("cache::attempt", uid);
                long bytes = cleanRoots(new File[]{context.getCacheDir(), context.getExternalCacheDir()}, now - cleanCacheOlderHours.getValue() * 3600000L);
                Log.record("缓存维护：已删除旧缓存文件 " + bytes + " 字节");
            }
        } catch (TaskCancelledException e) { cancelled = true;throw e;
        } catch (Exception e) { Log.record("缓存维护未完成：" + e.getClass().getSimpleName());
        } finally {
            Calendar tomorrow = MyUtils.getInstance();tomorrow.setTimeInMillis(now);tomorrow.add(Calendar.DAY_OF_MONTH, 1);
            tomorrow.set(Calendar.HOUR_OF_DAY, 0);tomorrow.set(Calendar.MINUTE, 0);tomorrow.set(Calendar.SECOND, 0);tomorrow.set(Calendar.MILLISECOND, 0);
            if (!cancelled && !RunGeneration.isStale() && uid.equals(UserIdMap.getCurrentUid()) && isEnable())
                schedule(uid, next > now ? next : nextTime(cleanCacheTime.getValue(), tomorrow.getTimeInMillis()));
        }
    }
    /** 当天时间已到则本轮执行；否则预约。 */
    static long nextTime(String text, long now) {
        if (text == null || !text.matches("(?:[01][0-9]|2[0-3])[0-5][0-9]")) return -1;
        Calendar cal = MyUtils.getInstance();cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(text.substring(0, 2)));
        cal.set(Calendar.MINUTE, Integer.parseInt(text.substring(2)));cal.set(Calendar.SECOND, 0);cal.set(Calendar.MILLISECOND, 0);
        return Math.max(now, cal.getTimeInMillis());
    }
    private synchronized void schedule(String uid, long at) {
        if (at <= System.currentTimeMillis()) return;
        ChildModelTask old = scheduledId == null ? null : getChildTask(scheduledId);
        if (old != null && Boolean.FALSE.equals(old.getIsCancel()) && old.getExecTime() <= at) return;
        if (scheduledId != null) removeChildTask(scheduledId);
        long generation = taskGeneration();String id = "CACHE|" + uid + "|" + System.nanoTime();
        if (addChildTask(new ChildModelTask(id, "cacheMaintenance", () -> {
            synchronized (this) { if (!id.equals(scheduledId)) return;scheduledId = null; }
            if (!uid.equals(UserIdMap.getCurrentUid()) || !check()) return;
            boolean ran = runExclusiveChild(generation, this::run);
            if (!ran && generation == taskGeneration() && uid.equals(UserIdMap.getCurrentUid()) && check()) schedule(uid, System.currentTimeMillis() + 30000);
        }, at))) scheduledId = id;
    }
    static long cleanRoots(File[] roots, long cutoff) throws IOException {
        long[] progress = {0, 0, System.nanoTime()};
        for (File root : roots) {
            if (root == null || !root.isDirectory() || Files.isSymbolicLink(root.toPath())) continue;
            File canonical = root.getCanonicalFile();File[] children = canonical.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (!budgetRemaining(progress)) break;
                clean(child, canonical.getPath() + File.separator, cutoff, 0, progress);
            }
        }
        return progress[1];
    }
    private static void clean(File file, String prefix, long cutoff, int depth, long[] progress) throws IOException {
        TimeUtil.sleep(0);
        // ponytail: 5000项/32层/2秒硬上限，较大缓存留到下一次维护。
        if (!budgetRemaining(progress) || depth > 32) return;
        progress[0]++;
        if (Files.isSymbolicLink(file.toPath()) || !file.getCanonicalPath().startsWith(prefix)) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();if (children == null) return;
            for (File child : children) {
                if (!budgetRemaining(progress)) break;
                clean(child, prefix, cutoff, depth + 1, progress);
            }
            return; // 目录及根保留，不将目录长度算作释放文件字节。
        }
        long modified = file.lastModified(), bytes = file.length();
        if (!file.isFile() || modified <= 0 || modified > cutoff) return;
        if (!file.getCanonicalPath().startsWith(prefix) || Files.isSymbolicLink(file.toPath())) return;
        if (file.delete()) progress[1] += bytes;
    }
    private static boolean budgetRemaining(long[] progress) { return progress[0] < 5000 && System.nanoTime() - progress[2] <= 2000000000L; }
}
