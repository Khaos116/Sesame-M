package io.github.aw1y2z.sesame.util.idMap;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.aw1y2z.sesame.util.XHelpers;
import lombok.Getter;

import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserIdMap {
    
    private static final Map<String, UserEntity> userMap = new ConcurrentHashMap<>();
    
    private static final Map<String, UserEntity> readOnlyUserMap = Collections.unmodifiableMap(userMap);
    
    @Getter
    private static String currentUid = null;
    
    public static Map<String, UserEntity> getUserMap() {
        return readOnlyUserMap;
    }
    
    public static Set<String> getUserIdSet() {
        return userMap.keySet();
    }

    /**
     * uid -> 账号序号（从 1 开始）。首次遇到时分配「当前最大序号 + 1」并落盘，
     * 只增不改、账号删除后序号不回收：否则历史日志里的「账号N」会指向别的账号。
     */
    private static final Map<String, Integer> accountIndexMap = new ConcurrentHashMap<>();
    private static volatile boolean accountIndexLoaded = false;

    private static synchronized void ensureAccountIndexLoaded() {
        if (accountIndexLoaded) {
            return;
        }
        try {
            String body = FileUtil.readFromFile(FileUtil.getAccountIndexFile());
            if (!body.isEmpty()) {
                Map<String, Integer> loaded = JsonUtil.parseObject(body, new TypeReference<Map<String, Integer>>() {
                });
                if (loaded != null) {
                    accountIndexMap.putAll(loaded);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        accountIndexLoaded = true;
    }

    public static synchronized int getAccountIndex(String userId) {
        if (userId == null || userId.isEmpty()) {
            return -1;
        }
        ensureAccountIndexLoaded();
        Integer index = accountIndexMap.get(userId);
        if (index != null) {
            return index;
        }
        int next = 1;
        for (Integer used : accountIndexMap.values()) {
            if (used != null && used >= next) {
                next = used + 1;
            }
        }
        accountIndexMap.put(userId, next);
        try {
            FileUtil.write2File(JsonUtil.toJsonString(accountIndexMap), FileUtil.getAccountIndexFile());
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
        return next;
    }

    /**
     * 日志与界面统一使用的账号简称，如「账号2」；uid 为空或分配失败时返回 null。
     * <p>已分配过的走无锁快路径（一次 map 查询），适合每条日志调用。
     */
    public static String getAccountLabel(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        try {
            ensureAccountIndexLoaded();
            Integer cached = accountIndexMap.get(userId);
            if (cached != null) {
                return "账号" + cached;
            }
            int index = getAccountIndex(userId);
            return index > 0 ? "账号" + index : null;
        } catch (Throwable t) {
            return null;
        }
    }
    
    public static Collection<UserEntity> getUserEntityCollection() {
        return userMap.values();
    }
    
    public synchronized static void initUser(String currentUserId) {
        setCurrentUserId(currentUserId);
        ApplicationHook.getMainHandler().post(() -> {
            ClassLoader loader;
            try {
                loader = ApplicationHook.getClassLoader();
            } catch (Exception e) {
                Log.i("Error getting classloader");
                return;
            }
            try {
                UserIdMap.unload();
                String selfId = ApplicationHook.getUserId();
                Class<?> clsUserIndependentCache = loader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.UserIndependentCache");
                Class<?> clsAliAccountDaoOp = loader.loadClass("com.alipay.mobile.socialcommonsdk.bizdata.contact.data.AliAccountDaoOp");
                Object aliAccountDaoOp = XHelpers.callStaticMethod(clsUserIndependentCache, "getCacheObj", clsAliAccountDaoOp);
                List<?> allFriends = (List<?>) XHelpers.callMethod(aliAccountDaoOp, "getAllFriends", new Object[0]);
                if (!allFriends.isEmpty()) {
                    Class<?> friendClass = allFriends.get(0).getClass();
                    Field userIdField = XHelpers.findField(friendClass, "userId");
                    Field accountField = XHelpers.findField(friendClass, "account");
                    Field nameField = XHelpers.findField(friendClass, "name");
                    Field nickNameField = XHelpers.findField(friendClass, "nickName");
                    Field remarkNameField = XHelpers.findField(friendClass, "remarkName");
                    Field friendStatusField = XHelpers.findField(friendClass, "friendStatus");
                    UserEntity selfEntity = null;
                    for (Object userObject : allFriends) {
                        try {
                            String userId = (String) userIdField.get(userObject);
                            String account = (String) accountField.get(userObject);
                            String name = (String) nameField.get(userObject);
                            String nickName = (String) nickNameField.get(userObject);
                            String remarkName = (String) remarkNameField.get(userObject);
                            Integer friendStatus = (Integer) friendStatusField.get(userObject);
                            UserEntity userEntity = new UserEntity(userId, account, friendStatus, name, nickName, remarkName);
                            if (Objects.equals(selfId, userId)) {
                                selfEntity = userEntity;
                            }
                            UserIdMap.add(userEntity);
                        } catch (Throwable t) {
                            Log.i("addUserObject err:");
                            Log.printStackTrace(t);
                        }
                    }
                    UserIdMap.saveSelf(selfEntity);
                }
                UserIdMap.save(selfId);
            } catch (Throwable t) {
                Log.i("checkUnknownId.run err:");
                Log.printStackTrace(t);
            }
        });
    }
    
    public synchronized static void setCurrentUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            currentUid = null;
            Log.setAccountLabel(null);
            return;
        }
        currentUid = userId;
        // 日志前缀只在 uid 变化时算一次（首次会读/写 accountIndex.json），
        // 不再让 Log 每条日志回调 UserIdMap，避免 UI 进程被拖进 libxposed 类
        Log.setAccountLabel(getAccountLabel(userId));
    }
    
    public static String getCurrentMaskName() {
        return getMaskName(currentUid);
    }
    
    public static String getMaskName(String userId) {
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return null;
        }
        return userEntity.getMaskName();
    }
    public static String getShowName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "未知用户";
        }
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return userId; // 返回用户ID作为默认值
        }
        return userEntity.getShowName();
    }
    public static String getFullName(String userId) {
        UserEntity userEntity = userMap.get(userId);
        if (userEntity == null) {
            return null;
        }
        return userEntity.getFullName();
    }
    
    public static UserEntity get(String userId) {
        return userMap.get(userId);
    }
    
    public synchronized static void add(UserEntity userEntity) {
        String userId = userEntity.getUserId();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        userMap.put(userId, userEntity);
    }
    
    public synchronized static void remove(String userId) {
        userMap.remove(userId);
    }
    
    public synchronized static void load(String userId) {
        userMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getFriendIdMapFile(userId));
            if (!body.isEmpty()) {
                Map<String, UserEntity.UserDto> dtoMap = JsonUtil.parseObject(body, new TypeReference<Map<String, UserEntity.UserDto>>() {
                });
                for (UserEntity.UserDto dto : dtoMap.values()) {
                    userMap.put(dto.getUserId(), dto.toEntity());
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    public synchronized static void unload() {
        userMap.clear();
    }
    
    public synchronized static boolean save(String userId) {
        return FileUtil.write2File(JsonUtil.toJsonString(userMap), FileUtil.getFriendIdMapFile(userId));
    }
    
    public synchronized static void loadSelf(String userId) {
        userMap.clear();
        try {
            String body = FileUtil.readFromFile(FileUtil.getSelfIdFile(userId));
            if (!body.isEmpty()) {
                UserEntity.UserDto dto = JsonUtil.parseObject(body, new TypeReference<UserEntity.UserDto>() {
                });
                userMap.put(dto.getUserId(), dto.toEntity());
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }
    
    public synchronized static boolean saveSelf(UserEntity userEntity) {
        return FileUtil.write2File(JsonUtil.toJsonString(userEntity), FileUtil.getSelfIdFile(userEntity.getUserId()));
    }
    
}
