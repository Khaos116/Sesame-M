package io.github.aw1y2z.sesame.entity;

import org.json.JSONObject;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * @author Constanline
 * @since 2023/08/08
 */
public class FriendWatch extends IdAndName {

    private static final String TAG = FriendWatch.class.getSimpleName();

    private static JSONObject joFriendWatch;
    private static String loadedUserId;

    /** 本次加载是否解析失败：内存是空对象时不得落盘，否则历史统计被抹成 {} */
    private static volatile boolean loadFailed;

    private String startTime;

    private int allGet;

    private int weekGet;

    public FriendWatch(String id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public int compareTo(IdAndName o) {
        FriendWatch another = (FriendWatch) o;
        if (this.weekGet > another.weekGet) {
            return -1;
        } else if (this.weekGet < another.weekGet) {
            return 1;
        }
        return super.compareTo(o);
    }

    public static synchronized void friendWatch(String id, int collectedEnergy) {
        try {
            String userId = UserIdMap.getCurrentUid();
            if (!AccountFolderName.isValidUid(userId)) return;
            if (!userId.equals(loadedUserId)) load();
            if (loadFailed || joFriendWatch == null) return;
            String maskName = UserIdMap.getMaskName(id);
            if (UserIdMap.isFriendListLoaded() && maskName == null) return;
            JSONObject joSingle = joFriendWatch.optJSONObject(id);
            if (joSingle == null) {
                joSingle = MyUtils.newJSONObject();
                joSingle.put("name", maskName);
                joSingle.put("allGet", 0);
                joSingle.put("startTime", TimeUtil.getDateStr());
                joFriendWatch.put(id, joSingle);
            }
            joSingle.put("weekGet", joSingle.optInt("weekGet", 0) + collectedEnergy);
        } catch (Throwable th) {
            Log.err(TAG, "friendWatch err:", th);
        }
    }

    /**
     * 剔除没有名字的记录：老版本不区分好友关系，把 PK 好友也记进了统计，表现为 name 缺失或空白。
     * 真正的通讯录好友记录时一定带着 {@code UserEntity#getMaskName()}（形如「昵称|*实名」），不会是空串。
     *
     * @return 是否发生了删除
     */
    private static boolean pruneNonFriends(JSONObject jo) {
        boolean changed = false;
        List<String> toRemove = new ArrayList<>();
        Iterator<String> ids = jo.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject friend = jo.optJSONObject(id);
            if (friend == null || friend.optString("name").trim().isEmpty()) {
                toRemove.add(id);
            }
        }
        for (String id : toRemove) {
            jo.remove(id);
            changed = true;
        }
        return changed;
    }

    public static synchronized void save() {
        try {
            String userId = UserIdMap.getCurrentUid();
            if (!AccountFolderName.isValidUid(userId)) return;
            if (!userId.equals(loadedUserId)) load();
            if (loadFailed || joFriendWatch == null) return;
            FileUtil.write2File(joFriendWatch.toString(), FileUtil.getFriendWatchFile(userId));
        } catch (Exception e){
            Log.err(TAG, "friendWatch save err:", e);
        }
    }

    public static synchronized void updateDay() {
        String userId = UserIdMap.getCurrentUid();
        if (!AccountFolderName.isValidUid(userId)) return;
        if (!userId.equals(loadedUserId)) load();
        if (loadFailed || joFriendWatch == null) return;
        if (!needUpdateAll(FileUtil.getFriendWatchFile().lastModified())) {
            return;
        }
        JSONObject joSingle;
        try {
            String dateStr = TimeUtil.getDateStr();
            Iterator<String> ids = joFriendWatch.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                joSingle = joFriendWatch.optJSONObject(id);
                if (joSingle == null) {
                    continue;
                }
                joSingle.put("name", joSingle.optString("name"));
                joSingle.put("allGet", joSingle.optInt("allGet", 0) + joSingle.optInt("weekGet", 0));
                joSingle.put("weekGet", 0);
                if (!joSingle.has("startTime")) {
                    joSingle.put("startTime", dateStr);
                }
                joFriendWatch.put(id, joSingle);
            }
            FileUtil.write2File(joFriendWatch.toString(), FileUtil.getFriendWatchFile());
        } catch (Throwable th) {
            Log.err(TAG, "friendWatchNewWeek err:", th);
        }
    }

    public static synchronized Boolean load() {
        String userId = UserIdMap.getCurrentUid();
        unload();
        if (!AccountFolderName.isValidUid(userId)) return false;
        loadedUserId = userId;
        String raw = FileUtil.readFromFile(FileUtil.getFriendWatchFile(userId));
        joFriendWatch = raw.isEmpty() ? MyUtils.newJSONObject() : MessageUtil.newJSONObject(raw);
        loadFailed = joFriendWatch == null;
        if (loadFailed) {
            joFriendWatch = MyUtils.newJSONObject();
            Log.record("好友统计解析失败，本次不写盘，原文件保留");
            return false;
        }
        if (pruneNonFriends(joFriendWatch)) save();
        return true;
    }

    public static synchronized void unload() {
        joFriendWatch = MyUtils.newJSONObject();
        loadedUserId = null;
        loadFailed = false;
    }

    public static boolean needUpdateAll(long last) {
        if (last == 0L) {
            return true;
        }
        // 按北京时间判断跨天/星期一，同类问题见 AntFarm.deliverMsgSend / familyEatTogether 的修复记录（docs/MyFix.md）
        Calendar cLast = MyUtils.getInstance();
        cLast.setTimeInMillis(last);
        Calendar cNow = MyUtils.getInstance();
        if (cLast.get(Calendar.DAY_OF_YEAR) == cNow.get(Calendar.DAY_OF_YEAR)) {
            return false;
        }
        return cNow.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY;
    }

    public String getStartTime() {
        return startTime;
    }

    public int getWeekGet() {
        return weekGet;
    }

    public int getAllGet() {
        return allGet;
    }

    public static List<FriendWatch> getList() {
        return getList(UserIdMap.getCurrentUid());
    }

    public static List<FriendWatch> getList(String userId) {
        ArrayList<FriendWatch> list = new ArrayList<>();
        if (!AccountFolderName.isValidUid(userId)) return list;
        String strFriendWatch = FileUtil.readFromFile(FileUtil.getFriendWatchFile(userId));
        try {
            JSONObject joFriendWatch;
            if (StringUtil.isEmpty(strFriendWatch)) {
                joFriendWatch = MyUtils.newJSONObject();
            } else {
                joFriendWatch = MessageUtil.newJSONObject(strFriendWatch);
                if (joFriendWatch == null) return list;
            }
            // 打开统计页时也清一次：这一步在模块 App 进程里跑，不依赖注入进程是否已启动
            pruneNonFriends(joFriendWatch);
            Iterator<String> ids = joFriendWatch.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                JSONObject friend = joFriendWatch.optJSONObject(id);
                if (friend == null) {
                    friend = MyUtils.newJSONObject();
                }
                String name = friend.optString("name");
                FriendWatch friendWatch = new FriendWatch(id, name);
                friendWatch.startTime = friend.optString("startTime", "无");
                friendWatch.weekGet = friend.optInt("weekGet", 0);
                friendWatch.allGet = friend.optInt("allGet", 0) + friendWatch.weekGet;
                list.add(friendWatch);
            }
        } catch (Throwable t) {
            // 只跳过本次读取：覆写成 {} 会把历史统计抹掉
            Log.err(TAG, "FriendWatch getList: ", t);

        }
        return list;
    }
}
