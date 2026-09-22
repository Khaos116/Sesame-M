package io.github.aw1y2z.sesame.model.task.antSports;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.TokenConfig;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayAntSportsTaskList;
import io.github.aw1y2z.sesame.entity.WalkPathThemeMapList;
import io.github.aw1y2z.sesame.entity.AlipayUser;
import io.github.aw1y2z.sesame.entity.WalkPath;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle;
import io.github.aw1y2z.sesame.model.task.antStall.AntStall;
import io.github.aw1y2z.sesame.model.task.antStall.AntStallRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.AntSportsTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntStallTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.PathThemeMapListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class AntSports extends ModelTask {

    private static final String TAG = AntSports.class.getSimpleName();

    private int tmpStepCount = -1;
    // 真实步数超过该值就不再同步/篡改
    private static final int SKIP_SYNC_STEP = 18000;
    private BooleanModelField walk;
    private ChoiceModelField PathThemeMapList;
    private BooleanModelField walkMinimumCompleteCount;
    private BooleanModelField receiveCoinAsset;
    private ChoiceModelField donateCharityCoinType;
    private IntegerModelField donateCharityCoinAmount;
    private BooleanModelField coinExchangeDoubleCard;
    private IntegerModelField minExchangeCount;
    private IntegerModelField earliestSyncStepTime;
    private IntegerModelField latestExchangeTime;
    private IntegerModelField syncStepCount;
    private BooleanModelField tiyubiz;
    private BooleanModelField club;
    private ChoiceModelField clubTrainItemType;
    private ChoiceModelField clubTradeMemberType;
    private SelectModelField clubTradeMemberList;
    private BooleanModelField sportsTasks;
    private BooleanModelField AutoAntSportsTaskList;
    private SelectModelField AntSportsTaskList;
    private BooleanModelField neverLand;

    // 处理签到
    private BooleanModelField QUERY_SIGN;
    // 处理任务中心

    private BooleanModelField QUERY_TASK_CENTER;

    // 处理气泡任务
    private BooleanModelField QUERY_BUBBLE_TASK;

    //能量泵
    private BooleanModelField WALK_GRID;

    private IntegerModelField WALK_GRID_LIMIT;

    private IntegerModelField WALK_GRID_MAX;

    private BooleanModelField MapListSwitch;

    private BooleanModelField awardspecialActivityReceive;

    //private SelectModelField neverLandOptions;
    private ChoiceModelField energyStrategy;

    @Override
    public String getName() {
        return "运动";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.SPORTS;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(walk = new BooleanModelField("walk", "行走路线 | 开启", false));
        // modelFields.addField(PathThemeMapList = new SelectModelField("PathThemeMapList", "行走路线 | 路线主题", new LinkedHashSet<>(), WalkPathThemeMapList::getList, "请选择要行走的主题，选择多条则随机走其中一个主题下的路线"));
        // 确保 nickNames 和 values 已初始化
        WalkPathThemeMapList.getList();
        modelFields.addField(PathThemeMapList = new ChoiceModelField("PathThemeMapList", "行走路线 | 路线主题", 0, WalkPathThemeMapList.nickNames).setDependsOn("walk"));
        modelFields.addField(walkMinimumCompleteCount = new BooleanModelField("walkMinimumCompleteCount", "全主题路线(选最少完成数) | 开启", false).setDependsOn("walk"));
        //modelFields.addField(walkCustomPathIdList = new SelectModelField("walkCustomPathIdList", "行走路线 | 自定义路线列表", new LinkedHashSet<>(), WalkPath::getThemeListFromRpc, "请选择要行走的路线，选择多条则随机走其中一条"));
        modelFields.addField(sportsTasks = new BooleanModelField("sportsTasks", "运动任务", false));
        modelFields.addField(AutoAntSportsTaskList = new BooleanModelField("AutoAntSportsTaskList", "运动任务 | 自动黑名单", true).setDependsOn("sportsTasks"));
        modelFields.addField(AntSportsTaskList = new SelectModelField("AntSportsTaskList", "运动任务 | 黑名单列表", new LinkedHashSet<>(), AlipayAntSportsTaskList::getList).setDependsOn("AutoAntSportsTaskList"));
        modelFields.addField(receiveCoinAsset = new BooleanModelField("receiveCoinAsset", "收运动币", false));
        //modelFields.addField(donateCharityCoinType = new ChoiceModelField("donateCharityCoinType", "捐运动币 | 方式", DonateCharityCoinType.ZERO, DonateCharityCoinType.nickNames));
        //modelFields.addField(donateCharityCoinAmount = new IntegerModelField("donateCharityCoinAmount", "捐运动币 | 数量" + "(每次)", 100));
        //modelFields.addField(coinExchangeDoubleCard = new BooleanModelField("coinExchangeDoubleCard", "运动币兑换限时能量双击卡", false));
        modelFields.addField(club = new BooleanModelField("club", "抢好友 | 开启", false));
        modelFields.addField(clubTrainItemType = new ChoiceModelField("clubTrainItemType", "抢好友 | 训练动作", TrainItemType.NONE, TrainItemType.nickNames).setDependsOn("club"));
        modelFields.addField(clubTradeMemberType = new ChoiceModelField("clubTradeMemberType", "抢好友 | 抢购动作", TradeMemberType.NONE, TradeMemberType.nickNames).setDependsOn("club"));
        modelFields.addField(clubTradeMemberList = new SelectModelField("clubTradeMemberList", "抢好友 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList).setDependsOn("club"));
        modelFields.addField(tiyubiz = new BooleanModelField("tiyubiz", "文体中心", false));
        modelFields.addField(syncStepCount = new IntegerModelField("syncStepCount", "同步步数 | 自定义", 22000, 0, 78000));
        modelFields.addField(earliestSyncStepTime = new IntegerModelField("earliestSyncStepTime", "同步步数 | 最早同步时间(24小时制)", 0, 0, 23));
        modelFields.addField(latestExchangeTime = new IntegerModelField("latestExchangeTime", "行走捐 | 最晚捐步时间(24小时制)", 22));
        modelFields.addField(minExchangeCount = new IntegerModelField("minExchangeCount", "行走捐 | 最小捐步步数", 10));
        modelFields.addField(neverLand = new BooleanModelField("neverLand", "健康岛 | 开启", false));
        modelFields.addField(QUERY_SIGN = new BooleanModelField("QUERY_SIGN", "健康岛 | 每日签到", false).setDependsOn("neverLand"));
        modelFields.addField(QUERY_TASK_CENTER = new BooleanModelField("QUERY_TASK_CENTER", "健康岛 | 做任务 加能量", false).setDependsOn("neverLand"));
        modelFields.addField(QUERY_BUBBLE_TASK = new BooleanModelField("QUERY_BUBBLE_TASK", "健康岛 | 领取能量球奖励", false).setDependsOn("neverLand"));
        modelFields.addField(WALK_GRID = new BooleanModelField("WALK_GRID", "健康岛 | 能量泵", false).setDependsOn("neverLand"));
        modelFields.addField(WALK_GRID_MAX = new IntegerModelField("WALK_GRID_MAX", "健康岛 | 单次执行能量泵最大次数(不限:0)", 5).setDependsOn("neverLand"));
        modelFields.addField(WALK_GRID_LIMIT = new IntegerModelField("WALK_GRID_LIMIT", "健康岛 | 使用能量泵剩余能量值(低于该值停止使用)", 10000).setDependsOn("neverLand"));
        modelFields.addField(MapListSwitch = new BooleanModelField("MapListSwitch", "健康岛 | 自动切岛", false).setDependsOn("neverLand"));
        modelFields.addField(awardspecialActivityReceive = new BooleanModelField("awardspecialActivityReceive", "健康岛 | 领取活动岛奖励", false).setDependsOn("neverLand"));
        return modelFields;
    }

    public static final String DISPLAY_NAME = "悦动健康岛";
    public static final ModelGroup MODULE_GROUP = ModelGroup.SPORTS;

    @Override
    public void boot(ClassLoader classLoader) {
        try {
            XHelpers.findAndHookMethod("com.alibaba.health.pedometer.core.datasource.PedometerAgent", classLoader, "readDailyStep", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
                    int originStep = (Integer) param.getResult();
                    int step = tmpStepCount();
                    if (hour >= earliestSyncStepTime.getValue() && originStep <= SKIP_SYNC_STEP && originStep < step) {
                        param.setResult(step);
                    }
                }
            });
            Log.i(TAG, "hook readDailyStep successfully");
        } catch (Throwable t) {
            Log.err(TAG, "hook readDailyStep err:", t);
        }
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.other("任务暂停⏸️支付宝运动:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);
            // 主动推送使用独立标记 sport::syncStepPush，失败/废弃都不再影响 readDailyStep hook
            step("同步步数", () -> {
                if (!Status.hasFlagToday("sport::syncStepPush") && hour >= earliestSyncStepTime.getValue()) {
                    // 查询失败/被暂停只影响“是否已达标”的判断，不能阻断同步步数和本轮其余运动任务
                    JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryWalkStep());
                    int stepCount = MessageUtil.checkResultCode(TAG, jo) ? jo.optInt("stepCount") : 0;
                    addChildTask(new ChildModelTask("syncStep", () -> {
                        int step = tmpStepCount();
                        if (stepCount <= SKIP_SYNC_STEP && stepCount < step) {
                            // a(int, boolean, String) 是【实例方法】。旧实现为两步：
                            //   XHelpers.callMethod(XHelpers.callStaticMethod(RpcManager.class, "a"), "a", {step, false, "system"})
                            // 即先用静态无参 a() 取单例，再在该实例上调用；此前误写成 Method.invoke(null, …) 传 null 接收者 → null receiver NPE
                            try {
                                ClassLoader classLoader = ApplicationHook.getClassLoader();
                                if (syncStepByRpcManager(classLoader, step)) {
                                    Toast.show("同步步数🏃🏻‍♂️[" + step + "步]");
                                    Log.other("同步步数🏃🏻‍♂️[" + step + "步]");
                                    Status.flagToday("sport::syncStepPush");
                                } else {
                                    Log.record("同步运动步数失败:" + step);
                                }
                            } catch (Throwable t) {
                                // XHelpers 会把 NoSuchMethodException 包装进 RuntimeException，这里统一处理
                                if (t.getCause() instanceof NoSuchMethodException) {
                                    Log.record("同步步数主动推送⚠️接口已不可用（新版支付宝移除），已跳过；readDailyStep hook 不受影响");
                                    // 接口确定不存在才当天不再重试；其余异常保持未标记，下一轮继续同步
                                    Status.flagToday("sport::syncStepPush");
                                } else {
                                    Log.record("同步步数主动推送⚠️异常，下一轮重试");
                                    Log.printStackTrace(TAG, t);
                                }
                            }
                        }
                    }));
                }
            });

            step("行走", () -> {
                if (walk.getValue()) {
                    walk(syncStepCount.getValue());
                }
            });

            //初始任务列表
            step("初始任务列表", () -> {
                if (!Status.hasFlagToday("BlackList::initAntSports")) {
                    initAntSportsTaskListMap(AutoAntSportsTaskList.getValue(), sportsTasks.getValue());
                    Status.flagToday("BlackList::initAntSports");
                }
            });

            //初始化行走主题列表
            step("初始化行走主题列表", () -> {
                if (!Status.hasFlagToday("WalkPathTheme::init")) {
                    initWalkPathThemeMap();
                    Status.flagToday("WalkPathTheme::init");
                }
            });

            //if (donateCharityCoinType.getValue() != DonateCharityCoinType.ZERO) {
            //    queryProjectList();
            //}

            //if (coinExchangeDoubleCard.getValue()) {
            //    coinExchangeItem("AMS2024032927086104");
           // }

            step("行走捐", () -> {
                if (minExchangeCount.getValue() > 0) {
                    queryWalkStep();
                }
            });

            step("文体中心", () -> {
                if (tiyubiz.getValue()) {
                    userTaskGroupQuery("SPORTS_DAILY_SIGN_GROUP");
                    userTaskGroupQuery("SPORTS_DAILY_GROUP");
                    userTaskRightsReceive();
                    pathFeatureQuery();
                    //{"error":3000,"errorMessage":"系统出错，正在排查","errorNo":3,"errorTip":"3000"}
                    //participate();
                }
            });

            step("抢好友", () -> {
                if (club.getValue()) {
                    queryClubHome();
                }
            });

            step("运动任务", () -> {
                if (sportsTasks.getValue()) {
                    sportsTasks();
                }
            });

            step("运动币", () -> {
                if (receiveCoinAsset.getValue()) {
                    receiveCoinAsset();
                    AntSportsRpcCall.pickAllEnergyBall();
                }
            });

            //执行悦动健康岛
            //if (neverLand.getValue() && checkAuth()) {
            step("悦动健康岛", () -> {
                if (neverLand.getValue()) {
                    neverlandrun();
                }
            });

        } catch (Throwable t) {
            Log.err(TAG, "start.run err:", t);
        }
    }

    // 单个子任务抛异常只跳过自己，不影响 run() 后面的其它运动任务
    private void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Log.err(TAG, "run[" + name + "] err:", t);
        }
    }

    // ---------------------------------------------------------------------
    // 同步步数：特征匹配调用 RpcManager，不依赖硬编码的类名/方法名/参数类型
    // 参照 Sesame-AG 的 AntSports.kt#syncStepByRpcManager 移植
    // ---------------------------------------------------------------------

    private static boolean syncStepByRpcManager(ClassLoader loader, int step) {
        String[] candidateClassNames = {
                "com.alibaba.health.pedometer.intergation.rpc.RpcManager",
                "com.alibaba.health.pedometer.integration.rpc.RpcManager"
        };
        for (String className : candidateClassNames) {
            Class<?> rpcManagerClass;
            try {
                rpcManagerClass = loader.loadClass(className);
            } catch (Throwable t) {
                continue;
            }
            List<Method> syncMethods = findSyncStepMethods(rpcManagerClass);
            if (syncMethods.isEmpty()) {
                continue;
            }
            for (Method method : syncMethods) {
                List<Object> targets = Modifier.isStatic(method.getModifiers())
                        ? Collections.singletonList(null)
                        : collectRpcManagerInstances(rpcManagerClass);
                for (Object target : targets) {
                    if (invokeSyncStepMethod(method, target, step)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<Method> findSyncStepMethods(Class<?> clazz) {
        Map<String, Method> unique = new LinkedHashMap<>();
        List<Method> all = new ArrayList<>();
        all.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        all.addAll(Arrays.asList(clazz.getMethods()));
        for (Method method : all) {
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != 3) {
                continue;
            }
            boolean firstArgMatches = paramTypes[0] == int.class || paramTypes[0] == Integer.class
                    || paramTypes[0] == long.class || paramTypes[0] == Long.class;
            boolean secondArgMatches = paramTypes[1] == boolean.class || paramTypes[1] == Boolean.class;
            boolean thirdArgMatches = paramTypes[2] == String.class
                    || CharSequence.class.isAssignableFrom(paramTypes[2])
                    || paramTypes[2] == Object.class;
            if (!firstArgMatches || !secondArgMatches || !thirdArgMatches) {
                continue;
            }
            String key = method.getName() + "#" + paramTypes[0].getName() + "," + paramTypes[1].getName() + "," + paramTypes[2].getName();
            unique.putIfAbsent(key, method);
        }
        List<Method> result = new ArrayList<>(unique.values());
        result.sort((a, b) -> scoreSyncStepMethod(b) - scoreSyncStepMethod(a));
        return result;
    }

    private static int scoreSyncStepMethod(Method method) {
        int score = 0;
        if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
            score += 4;
        }
        if ("a".equals(method.getName())) {
            score += 2;
        }
        if (Modifier.isPublic(method.getModifiers())) {
            score += 1;
        }
        return score;
    }

    private static List<Object> collectRpcManagerInstances(Class<?> clazz) {
        LinkedHashSet<Object> instances = new LinkedHashSet<>();

        try {
            Field instanceField = clazz.getDeclaredField("INSTANCE");
            if (Modifier.isStatic(instanceField.getModifiers())) {
                instanceField.setAccessible(true);
                instances.add(instanceField.get(null));
            }
        } catch (Throwable ignored) {
        }

        Set<String> singletonMethodNames = new HashSet<>(Arrays.asList("a", "getInstance", "instance"));
        List<Method> all = new ArrayList<>();
        all.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        all.addAll(Arrays.asList(clazz.getMethods()));
        for (Method method : all) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            boolean nameMatches = singletonMethodNames.contains(method.getName());
            boolean returnsSelf = clazz.isAssignableFrom(method.getReturnType());
            if (!nameMatches && !returnsSelf) {
                continue;
            }
            try {
                method.setAccessible(true);
                instances.add(method.invoke(null));
            } catch (Throwable ignored) {
            }
        }

        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            instances.add(constructor.newInstance());
        } catch (Throwable ignored) {
        }

        instances.remove(null);
        return new ArrayList<>(instances);
    }

    private static boolean invokeSyncStepMethod(Method method, Object target, int step) {
        try {
            method.setAccessible(true);
            Class<?> firstParamType = method.getParameterTypes()[0];
            Object stepArg = (firstParamType == long.class || firstParamType == Long.class) ? (Object) (long) step : (Object) step;
            Object result = method.invoke(target, stepArg, Boolean.FALSE, "system");
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return result == null && method.getReturnType() == void.class;
        } catch (Throwable t) {
            return false;
        }
    }

    public int tmpStepCount() {
        if (tmpStepCount >= 0) {
            return tmpStepCount;
        }
        tmpStepCount = syncStepCount.getValue();
        if (tmpStepCount > 0) {
            tmpStepCount = RandomUtil.nextInt(tmpStepCount, tmpStepCount + 2000);
            if (tmpStepCount > 78000) {
                tmpStepCount = 78000;
            }
        }
        return tmpStepCount;
    }

    public static void initAntSportsTaskListMap(boolean AutoAntSportsTaskList, boolean sportsTasks) {
        try {
            //初始化AntSportsTaskListMap
            AntSportsTaskListMap.load();
            Set<String> blackList = new HashSet<>();
            blackList.add("下载登录AI健康管家");

            Set<String> whiteList = new HashSet<>();// 从黑名单中移除该任务
            //whiteList.add("逛一逛树");
            for (String task : blackList) {
                AntSportsTaskListMap.add(task, task);
            }

            if (sportsTasks) {
                JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryCoinTaskPanel());
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    jo = jo.optJSONObject("data");
                    if (jo != null && jo.has("taskList")) {
                        JSONArray taskLists = jo.optJSONArray("taskList");
                        for (int i = 0; taskLists != null && i < taskLists.length(); i++) {
                            JSONObject taskList = taskLists.optJSONObject(i);
                            if (taskList == null) {
                                continue;
                            }
                            String taskName = taskList.optString("taskName");
                            AntSportsTaskListMap.add(taskName, taskName);
                        }
                    }
                }

                //保存任务到配置文件
                AntSportsTaskListMap.save();
                Log.record("同步任务🉑运动任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntSportsTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntSports = config.getModelFieldsMap().get("AntSports");
                    SelectModelField AntSportsTaskList = (SelectModelField) AntSports.get("AntSportsTaskList");
                    if (AntSportsTaskList == null) {
                        return;
                    }
                    // 2. 批量添加黑名单任务（确保存在）
                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("运动任务", blackList, whiteList, AntSportsTaskList);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "initSportsTaskListMap err:", t);
        }
    }

    private void initWalkPathThemeMap() {
        //初始化PathThemeMapListMap
        PathThemeMapListMap.load();
        try {
            String result = AntSportsRpcCall.queryThemeList();
            JSONObject jo = MyUtils.newJSONObject(result);
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                JSONArray themeList = data.optJSONArray("themeList");
                if (themeList != null) {
                    for (int i = 0; i < themeList.length(); i++) {
                        JSONObject theme = themeList.optJSONObject(i);
                        String themeId = theme.optString("themeId");
                        String themeName = theme.optString("themeName");
                        if (themeId != null && !themeId.isEmpty() && themeName != null && !themeName.isEmpty()) {
                            PathThemeMapListMap.add(themeId, themeName);
                        }
                    }
                }
            }
            PathThemeMapListMap.save();
            Log.record("同步路线主题" + PathThemeMapListMap.getMap());

        } catch (Throwable t) {
            Log.err(TAG, "initWalkPathThemeMap err:", t);
        }

    }

    // 运动
    private void sportsTasks() {
        try {
            signInCoinTask();
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryCoinTaskPanel());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            if (jo == null || !jo.has("taskList")) {
                return;
            }
            JSONArray taskList = jo.optJSONArray("taskList");
            for (int i = 0; taskList != null && i < taskList.length(); i++) {
                jo = taskList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String taskName = jo.optString("taskName");
                String taskStatus = jo.optString("taskStatus");
                if (TaskStatus.HAS_RECEIVED.name().equals(taskStatus)) {
                    return;
                }

                if (TaskStatus.WAIT_RECEIVE.name().equals(taskStatus)) {
                    String assetId = jo.optString("assetId");
                    int prizeAmount = jo.optInt("prizeAmount");
                    if (receiveCoinAsset(assetId, prizeAmount, taskName)) {
                        TimeUtil.sleep(1000);
                    }
                    continue;
                }
                //黑名单任务跳过
                if (AntSportsTaskList.getValue().contains(taskName)) {
                    continue;
                }
                if (!jo.has("taskAction")) {
                    continue;
                }
                if (TaskStatus.WAIT_COMPLETE.name().equals(taskStatus)) {
                    String taskAction = jo.optString("taskAction");
                    String taskId = jo.optString("taskId");
                    if (jo.optBoolean("multiTask")) {
                        int currentNum = jo.optInt("currentNum") + 1;
                        int limitConfigNum = jo.optInt("limitConfigNum");
                        taskName = taskName.replaceAll("（.*/.*）", "(" + currentNum + "/" + limitConfigNum + ")");
                    }
                    if (jo.optBoolean("needSignUp") && !signUpTask(taskId, taskName)) {
                        continue;
                    }
                    if (completeTask(taskAction, taskId, taskName, jo.optString("sceneCode", ""))) {
                        TimeUtil.sleep(2000);
                    }
                    continue;
                }

                //兜底操作
                String taskAction = jo.optString("taskAction");
                String taskId = jo.optString("taskId");
                completeTask(taskAction, taskId, taskName, jo.optString("sceneCode", ""));
            }
        } catch (Throwable t) {
            Log.err(TAG, "sportsTasks err:", t);
        }
    }

    private Boolean signUpTask(String taskId, String taskName) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.signUpTask(taskId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                return true;
            }
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntSportsTaskList", taskName, jo);
        } catch (Throwable t) {
            Log.err(TAG, "signUpTask err:", t);
        }
        return false;
    }

    private Boolean completeTask(String taskAction, String taskId, String taskName, String sceneCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.completeTask(taskAction, taskId));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntSportsTaskList", taskName, jo);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("运动任务🧾完成[得运动币:" + taskName + "]");
                TimeUtil.sleep(1000);
                return true;
            }
            // 另一种实现方案（见 TaskAlternative）；运动历史上从未出现 400000040，属休眠兜底
            if (TaskAlternative.hit(jo, sceneCode)) {
                TaskAlternative.trigger(null, taskId, taskName, taskId, sceneCode, "运动任务", msg -> Log.other(msg));
            }
        } catch (Throwable t) {
            Log.err(TAG, "completeTask err:", t);
        }
        return false;
    }

    private void signInCoinTask() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.signInCoinTask());

            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return;
            }
            if (!data.optBoolean("signed")) {
                JSONObject subscribeConfig;
                if (data.has("subscribeConfig")) {
                    subscribeConfig = data.optJSONObject("subscribeConfig");
                    if (subscribeConfig != null) {
                        Log.other("运动任务🧾[做任务得运动币:签到" + subscribeConfig.optString("subscribeExpireDays") + "天]奖励" + data.optString("toast") + "运动币");
                    }
                } else {
                    //                        Log.record("没有签到");
                }
            } else {
                Log.record("运动签到今日已签到");
            }
        } catch (Throwable t) {
            Log.err(TAG, "signInCoinTask err:", t);
        }
    }

    private void receiveCoinAsset() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryCoinBubbleModule());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null || !data.has("recBubbleList")) {
                return;
            }
            JSONArray ja = data.optJSONArray("recBubbleList");
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null || jo.optString("assetId").isEmpty()) {
                    continue;
                }
                String assetId = jo.optString("assetId");
                int coinAmount = jo.optInt("coinAmount");
                String simpleSourceName = jo.optString("simpleSourceName");
                if (receiveCoinAsset(assetId, coinAmount, simpleSourceName)) {
                    TimeUtil.sleep(500);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveCoinAsset err:", t);
        }
    }

    private Boolean receiveCoinAsset(String assetId, int coinAmount, String title) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.receiveCoinAsset(assetId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.other("运动中心🧊领取[" + title + "]奖励[" + coinAmount + "运动能量]");
                return true;
            }
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntSportsTaskList", title, jo);
        } catch (Throwable t) {
            Log.err(TAG, "receiveCoinAsset err:", t);
        }
        return false;
    }

    /*
     * 新版行走路线 -- begin
     */
    //选择最小路线逻辑
    private String getWalkPathMinCompleteCount() {
        int minCompleteCount = 0;
        String minPathId = null;
        String minThemeName = null;
        String MinName = null;
        String MinCityPathName = null;
        boolean inited = false;
        try {
            String result = AntSportsRpcCall.queryThemeList();
            JSONObject jo = MyUtils.newJSONObject(result);
            JSONObject data = jo.optJSONObject("data");
            if (data != null) {
                //获取线路主题列表
                JSONArray themeList = data.optJSONArray("themeList");
                if (themeList != null) {
                    for (int i = 0; i < themeList.length(); i++) {
                        JSONObject theme = themeList.optJSONObject(i);
                        String themeId = theme.optString("themeId");
                        String themeName = theme.optString("themeName");
                        //Log.other("  " + themeName + "(" + themeId + ")");
                        JSONObject queryWorldMapJo = MyUtils.newJSONObject(AntSportsRpcCall.queryWorldMap(themeId));
                        if (MessageUtil.checkSuccess(TAG, queryWorldMapJo)) {
                            JSONObject queryWorldMapData = queryWorldMapJo.optJSONObject("data");
                            //获取线路城市列表
                            JSONArray cityList = queryWorldMapData != null ? queryWorldMapData.optJSONArray("cityList") : null;
                            for (int j = 0; cityList != null && j < cityList.length(); j++) {
                                JSONObject city = cityList.optJSONObject(j);
                                if (city == null) {
                                    continue;
                                }
                                String cityId = city.optString("cityId");
                                String name;
                                if (city.has("name")) {
                                    name = city.optString("name");
                                } else {
                                    name = null;
                                }
                                //Log.other("      " + name + "(" + cityId + ")");
                                if (cityId.equals("000000") || cityId.equals("232700") || cityId.equals("620900") || cityId.equals("653100") || cityId.equals("710100")) {
                                    continue;
                                }
                                JSONObject queryCityPathJo = MyUtils.newJSONObject(AntSportsRpcCall.queryCityPath(cityId));
                                if (MessageUtil.checkSuccess(TAG, queryCityPathJo)) {
                                    JSONObject queryCityPathData = queryCityPathJo.optJSONObject("data");
                                    //获取城市包含的路线
                                    JSONArray cityPathList = queryCityPathData != null ? queryCityPathData.optJSONArray("cityPathList") : null;
                                    for (int k = 0; cityPathList != null && k < cityPathList.length(); k++) {
                                        JSONObject cityPath = cityPathList.optJSONObject(k);
                                        if (cityPath == null) {
                                            continue;
                                        }
                                        String pathId = cityPath.optString("pathId");
                                        String queryCityPathName = cityPath.optString("name");
                                        int completeCount = cityPath.optInt("completeCount");
                                        boolean locked = cityPath.optBoolean("locked", true);
                                        if (!inited && !locked) {
                                            minCompleteCount = completeCount;
                                            minThemeName = themeName;
                                            MinName = name;
                                            MinCityPathName = queryCityPathName;
                                            minPathId = pathId;
                                            inited = true;
                                            //Log.other("暂定走第一个主题[" + themeName + "]城市[" + name + "]线路[" + queryCityPathName + "](" + pathId + ")行走" + minCompleteCount + "次");
                                        }
                                        //Log.other("        " + queryCityPathName + "(" + pathId + ")" + completeCount);
                                        if (completeCount < minCompleteCount && !locked) {
                                            minCompleteCount = completeCount;
                                            minThemeName = themeName;
                                            MinName = name;
                                            MinCityPathName = queryCityPathName;
                                            minPathId = pathId;
                                            //Log.other("目前查询到主题[" + themeName + "]城市[" + name + "]线路[" + queryCityPathName + "](" + pathId + ")行走" + minCompleteCount + "次");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Log.other("切换路线🚶🏻‍♂️选择主题[" + minThemeName + "]城市[" + MinName + "]线路[" + MinCityPathName + "](" + minPathId + ")目前" + minCompleteCount + "次");
        } catch (Throwable t) {
            Log.err(TAG, "getWalkPathMinCompleteCount err:", t);
        }
        return minPathId;
    }

    private void walk(int syncStepCount) {
        String goingPathId = queryGoingPathId();
        do {
            String tempPathId = (String) ExtensionsHandle.handleAlphaRequest("antSports", "walk", null);
            if (tempPathId != null) {
                goingPathId = tempPathId;
            }
            TimeUtil.sleep(1000);
            if (isNeedJoinNewPath(goingPathId)) {
                if (walkMinimumCompleteCount.getValue()) {
                    goingPathId = getWalkPathMinCompleteCount();
                } else {
                    String joinPathId = queryJoinPathId();
                    if (checkJoinPathId(joinPathId)) {
                        if (!joinPath(joinPathId)) {
                            return;
                        }
                        goingPathId = joinPathId;
                    }
                }
            }
        } while (walkGo(queryPath(goingPathId), syncStepCount));
    }

    private Boolean isNeedJoinNewPath(String goingPathId) {
        if (goingPathId.isEmpty()) {
            return true;
        }
        try {
            JSONObject jo = queryPath(goingPathId);
            jo = jo != null ? jo.optJSONObject("userPathStep") : null;
            if (jo == null) {
                return true;
            }
            if (jo.optBoolean("dayLimit")) {
                return true;
            }
            String pathCompleteStatus = jo.optString("pathCompleteStatus");
            if (PathCompleteStatus.COMPLETED.name().equals(pathCompleteStatus)) {
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "isNeedJoinNewPath err:", t);
        }
        return false;
    }

    private Boolean hasTreasureBox() {
        if (Status.hasFlagToday("sport::treasureBoxLimit")) {
            return false;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryMailList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray ja = jo.optJSONArray("userMailList");
            int count = 0;
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null || !"SPORTSPROD_GOPATH_AWARD_BOX".equals(jo.optString("templateId"))) {
                    continue;
                }
                if (!TimeUtil.isToday(jo.optLong("receiveTime"))) {
                    break;
                }
                count++;
            }
            if (count < 20) {
                return true;
            }
            Status.flagToday("sport::treasureBoxLimit");
        } catch (Throwable t) {
            Log.err(TAG, "hasTreasureBox err:", t);
        }
        return false;
    }

    private Boolean walkGo(JSONObject pathData, int syncStepCount) {
        //按照每天走路20次收获宝箱奖励得健康能量
        int MIN_STEP_FOR_TREASURE = 500;
        int MAX_STEP_FOR_TREASURE = 1000;
        if (syncStepCount > 20000) {
            int walkcountmax = syncStepCount / 20;
            int walkcountmin = (syncStepCount - 10000) / 20;
            MAX_STEP_FOR_TREASURE = walkcountmax;
            MIN_STEP_FOR_TREASURE = walkcountmin;
        }
        try {
            if (pathData == null || !pathData.has("path")) {
                return false;
            }
            JSONObject path = pathData.optJSONObject("path");
            JSONObject userPathStep = pathData.optJSONObject("userPathStep");
            if (path == null || userPathStep == null) {
                return false;
            }
            int minGoStepCount = path.optInt("minGoStepCount");
            int pathStepCount = path.optInt("pathStepCount");
            if (path.has("dailyMaxGoStepCount")) {
                pathStepCount = path.optInt("dailyMaxGoStepCount");
            }
            int forwardStepCount = userPathStep.optInt("forwardStepCount");
            int remainStepCount = userPathStep.optInt("remainStepCount");
            boolean dayLimit = userPathStep.optBoolean("dayLimit");
            int useStepCount = Math.min(Math.min(remainStepCount, hasTreasureBox() ? RandomUtil.nextInt(MIN_STEP_FOR_TREASURE, MAX_STEP_FOR_TREASURE) : remainStepCount), Math.max(pathStepCount - forwardStepCount % pathStepCount, minGoStepCount));
            if (useStepCount < minGoStepCount || dayLimit) {
                return false;
            }
            String pathId = path.optString("pathId");
            String pathName = path.optString("name");
            return walkGo(pathName, pathId, useStepCount);
        } catch (Throwable t) {
            Log.err(TAG, "walkGo err:", t);
        }
        return false;
    }

    private Boolean walkGo(String pathName, String pathId, int useStepCount) {
        boolean result = false;
        try {
            String date = Log.getFormatDate();
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.walkGo(date, pathId, useStepCount));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                result = true;
                Log.other("行走路线🚶🏻‍♂️行走[" + pathName + "]#前进了" + useStepCount + "步");
                jo = jo.optJSONObject("data");
                if (jo != null && jo.has("completeInfo")) {
                    Log.other("行走路线🚶🏻‍♂️完成[" + pathName + "]");
                }
                parseRewardsByJSONObjectData(jo);
            }
        } catch (Throwable t) {
            Log.err(TAG, "walkGo err:", t);
        }
        return result;
    }

    private JSONObject queryWorldMap(String themeId) {
        JSONObject theme = null;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryWorldMap(themeId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                theme = jo.optJSONObject("data");
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryWorldMap err:", t);
        }
        return theme;
    }

    private JSONObject queryCityPath(String cityId) {
        JSONObject city = null;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryCityPath(cityId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                city = jo.optJSONObject("data");
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryCityPath err:", t);
        }
        return city;
    }

    private static JSONObject queryPath(String pathId) {
        JSONObject path = null;
        try {
            String date = Log.getFormatDate();
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryPath(date, pathId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                path = jo.optJSONObject("data");
                parseRewardsByJSONObjectData(path);
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryPath err:", t);
        }
        return path;
    }

    private static void openTreasureBox(JSONArray treasureBoxList) {
        try {
            for (int i = 0; i < treasureBoxList.length(); i++) {
                JSONObject treasureBox = treasureBoxList.optJSONObject(i);
                if (treasureBox == null) {
                    continue;
                }
                receiveEvent(treasureBox.optString("boxNo"));
                TimeUtil.sleep(1000);
            }
        } catch (Throwable t) {
            Log.err(TAG, "openTreasureBox err:", t);
        }
    }

    private static void receiveEvent(String eventBillNo) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.receiveEvent(eventBillNo));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                jo = jo.optJSONObject("data");
                JSONArray rewards = jo != null ? jo.optJSONArray("rewards") : null;
                if (rewards != null) {
                    parseRewardsByJSONArrayRewards(rewards, 0);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "receiveEvent err:", t);
        }
    }

    private static void parseRewardsByJSONArrayRewards(JSONArray rewards, int rewardsType) {
        String rewardsTypeName;
        switch (rewardsType) {
            case 0:
                rewardsTypeName = "宝箱奖励";
                break;
            case 1:
                rewardsTypeName = "中奖奖励";
                break;
            case 2:
                rewardsTypeName = "终点奖励";
                break;
            default:
                rewardsTypeName = "未知奖励";
                break;
        }
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject jo = rewards.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (jo.has("rewardStatus") && !"SUCCESS".equals(jo.optString("rewardStatus"))) {
                    // rewardStatus : SUCCESS NOT_HIT
                    continue;
                }
                Log.other("行走路线🚶🏻‍♂️收获" + rewardsTypeName + "[" + jo.optString("rewardName") + "*" + jo.optInt("count") + "]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "parseRewardsByJSONArrayRewards err:", t);
        }
    }

    private static void parseRewardsByJSONObjectData(JSONObject data) {
        if (data == null) {
            return;
        }
        try {
            JSONArray treasureBoxList = data.optJSONArray("treasureBoxList");
            if (treasureBoxList != null) openTreasureBox(treasureBoxList);
            if (data.has("brandRewardVOs")) {
                JSONArray brandRewardVOs = data.optJSONArray("brandRewardVOs");
                if (brandRewardVOs != null) {
                    parseRewardsByJSONArrayRewards(brandRewardVOs, 1);
                }
            }
            if (data.has("completeInfo")) {
                data = data.optJSONObject("completeInfo");
                JSONArray completeRewards = data != null ? data.optJSONArray("completeRewards") : null;
                if (completeRewards != null) {
                    parseRewardsByJSONArrayRewards(completeRewards, 2);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "parseRewardsByJSONObjectData err:", t);
        }
    }

    private String queryGoingPathId() {
        String goingPathId = "";
        try {
            String date = Log.getFormatDate();
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryPath(date, ""));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                jo = jo.optJSONObject("data");
                goingPathId = jo != null ? jo.optString("goingPathId") : "";
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryGoingPathId err:", t);
        }
        return goingPathId;
    }

    private String queryJoinPathId() {
        String pathId = null;

        try {
            int index = PathThemeMapList.getValue();
            if (index < 0 || index >= WalkPathThemeMapList.values.length) {
                return pathId;
            }
            String themeId = WalkPathThemeMapList.values[index];
            JSONObject theme = queryWorldMap(themeId);
            if (theme == null) {
                return pathId;
            }
            JSONArray cityList = theme.optJSONArray("cityList");
            for (int i = 0; cityList != null && i < cityList.length(); i++) {
                JSONObject cityObj = cityList.optJSONObject(i);
                if (cityObj == null) {
                    continue;
                }
                String cityId = cityObj.optString("cityId");
                if (cityId.equals("000000") || cityId.equals("232700") || cityId.equals("620900") || cityId.equals("653100") || cityId.equals("710100")) {
                    continue;
                }
                JSONObject city = queryCityPath(cityId);
                if (city == null) {
                    continue;
                }
                JSONArray cityPathList = city.optJSONArray("cityPathList");
                for (int j = 0; cityPathList != null && j < cityPathList.length(); j++) {
                    JSONObject cityPath = cityPathList.optJSONObject(j);
                    if (cityPath == null) {
                        continue;
                    }
                    pathId = cityPath.optString("pathId");
                    String pathCompleteStatus = cityPath.optString("pathCompleteStatus");
                    if (!PathCompleteStatus.COMPLETED.name().equals(pathCompleteStatus)) {
                        return pathId;
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryJoinPathId err:", t);
        }
        return pathId;
    }

    public static Boolean checkJoinPathId(String joinPathId) {
        try {
            JSONObject jo = queryPath(joinPathId);
            if (jo == null) {
                return false;
            }
            String goingPathId = jo.optString("goingPathId");
            if (Objects.equals(goingPathId, joinPathId)) {
                return false;
            }
            jo = jo.optJSONObject("userPathStep");
            return jo != null && !jo.optBoolean("dayLimit");
        } catch (Throwable t) {
            Log.err(TAG, "checkJoinPathId err:", t);
        }
        return false;
    }

    public static Boolean joinPath(String pathId) {
        if (pathId == null) {
            // 守护体育梦
            pathId = "p000202408231708";
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.joinPath(pathId));
            if (MessageUtil.checkSuccess(TAG, jo)) {
                JSONObject pathData = queryPath(pathId);
                JSONObject pathObj = pathData != null ? pathData.optJSONObject("path") : null;
                String pathName = pathObj != null ? pathObj.optString("name") : "";
                Log.other("行走路线🚶🏻‍♂️加入[" + pathName + "]");
                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "joinPath err:", t);
        }
        return false;
    }

    /*
     * 新版行走路线 -- end
     */
    private Boolean canDonateCharityCoinToday() {
        if (Status.hasFlagToday("sport::donateCharityCoin")) {
            return false;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryDonateRecord());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray footballFieldLongModel = jo.optJSONArray("footballFieldLongModel");
            if (footballFieldLongModel == null || footballFieldLongModel.length() == 0) {
                return true;
            }
            jo = footballFieldLongModel.optJSONObject(0);
            jo = jo != null ? jo.optJSONObject("personStatModel") : null;
            if (jo == null) {
                return false;
            }
            long lastDonationTime = jo.optLong("lastDonationTime");
            if (TimeUtil.isLessThanNowOfDays(lastDonationTime)) {
                return true;
            }
            Status.flagToday("sport::donateCharityCoin");
        } catch (Throwable t) {
            Log.err(TAG, "canDonateCharityCoinToday err:", t);
        }
        return false;
    }

    private void queryProjectList() {
        if (!canDonateCharityCoinToday()) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryProjectList(0));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            int charityCoinCount = jo.optInt("charityCoinCount");
            int donateCharityCoin = donateCharityCoinAmount.getValue();
            if (charityCoinCount < donateCharityCoin) {
                return;
            }
            JSONObject projectPage = jo.optJSONObject("projectPage");
            JSONArray ja = projectPage != null ? projectPage.optJSONArray("data") : null;
            for (int i = 0; ja != null && i < ja.length(); i++) {
                JSONObject item = ja.optJSONObject(i);
                jo = item != null ? item.optJSONObject("basicModel") : null;
                if (jo == null) {
                    continue;
                }
                if (jo.optInt("acwProjectStatus") == 0) {
                    // acwProjectStatus: 0 1
                    continue;
                }
                // footballFieldStatus: OPENING_DONATE DONATE_COMPLETED
                if ("DONATE_COMPLETED".equals(jo.optString("footballFieldStatus"))) {
                    break;
                }
                if (donate(donateCharityCoin, jo.optString("projectId"), jo.optString("title"))) {
                    charityCoinCount -= donateCharityCoin;
                    if (donateCharityCoinType.getValue() != DonateCharityCoinType.ALL) {
                        break;
                    }
                    if (charityCoinCount < donateCharityCoin) {
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryProjectList err:", t);
        }
    }

    private Boolean donate(int donateCharityCoin, String projectId, String title) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.donate(donateCharityCoin, projectId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.other("公益捐赠❤️[捐赠运动币:" + title + "]捐赠" + donateCharityCoin + "运动币");

                return true;
            }
        } catch (Throwable t) {
            Log.err(TAG, "donate err:", t);
        }
        return false;
    }

    private Boolean canDonateWalkExchangeToday() {
        if (Status.hasFlagToday("sport::donateWalk")) {
            return false;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.donateExchangeRecord());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userExchangeRecords = jo.optJSONArray("userExchangeRecords");
            if (userExchangeRecords == null || userExchangeRecords.length() == 0) {
                return true;
            }
            jo = userExchangeRecords.optJSONObject(0);
            if (jo == null) {
                return false;
            }
            long gmtCreate = jo.optLong("gmtCreate");
            if (TimeUtil.isLessThanNowOfDays(gmtCreate)) {
                return true;
            }
            Status.flagToday("sport::donateWalk");
        } catch (Throwable t) {
            Log.err(TAG, "canDonateWalkExchangeToday err:", t);
        }
        return false;
    }

    private void queryWalkStep() {
        if (!canDonateWalkExchangeToday()) {
            return;
        }
        if (Status.hasFlagToday("sport::donateWalk")) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryWalkStep());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            //jo = jo.getJSONObject("dailyStepModel");
            //long stepLastTime = jo.getLong("stepLastTime");
            int hour = Integer.parseInt(Log.getFormatTime().split(":")[0]);

            int stepCount = jo.optInt("stepCount");
            if (stepCount < minExchangeCount.getValue() && hour < latestExchangeTime.getValue()) {
                return;
            }
            AntSportsRpcCall.walkDonateSignInfo(stepCount);
            jo = MyUtils.newJSONObject(AntSportsRpcCall.donateWalkHome(stepCount));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject walkDonateHomeModel = jo.optJSONObject("walkDonateHomeModel");
            JSONObject walkUserInfoModel = walkDonateHomeModel != null ? walkDonateHomeModel.optJSONObject("walkUserInfoModel") : null;
            if (walkDonateHomeModel == null || walkUserInfoModel == null || !walkUserInfoModel.has("exchangeFlag")) {
                return;
            }

            String donateToken = walkDonateHomeModel.optString("donateToken");
            JSONObject walkCharityActivityModel = walkDonateHomeModel.optJSONObject("walkCharityActivityModel");
            String activityId = walkCharityActivityModel != null ? walkCharityActivityModel.optString("activityId") : "";

            jo = MyUtils.newJSONObject(AntSportsRpcCall.donateWalkExchange(activityId, stepCount, donateToken));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject donateExchangeResultModel = jo.optJSONObject("donateExchangeResultModel");
            if (donateExchangeResultModel == null) {
                return;
            }
            int userCount = donateExchangeResultModel.optInt("userCount");
            JSONObject userAmount = donateExchangeResultModel.optJSONObject("userAmount");
            double amount = userAmount != null ? userAmount.optDouble("amount") : 0;
            String donateTitle = donateExchangeResultModel.optString("donateTitle");
            Log.other("公益捐赠❤️[捐步做公益:" + donateTitle + "]捐赠" + userCount + "步,兑换" + amount + "元公益金");
            Status.flagToday("sport::donateWalk");

        } catch (Throwable t) {
            Log.err(TAG, "queryWalkStep err:", t);
        }
    }

    /* 文体中心 */
    // SPORTS_DAILY_SIGN_GROUP SPORTS_DAILY_GROUP
    private void userTaskGroupQuery(String groupId) {
        try {
            String s = AntSportsRpcCall.userTaskGroupQuery(groupId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.optJSONObject("group");
                JSONArray userTaskList = jo != null ? jo.optJSONArray("userTaskList") : null;
                for (int i = 0; userTaskList != null && i < userTaskList.length(); i++) {
                    jo = userTaskList.optJSONObject(i);
                    if (jo == null || !"TODO".equals(jo.optString("status"))) {
                        continue;
                    }
                    JSONObject taskInfo = jo.optJSONObject("taskInfo");
                    if (taskInfo == null) {
                        continue;
                    }
                    String bizType = taskInfo.optString("bizType");
                    String taskId = taskInfo.optString("taskId");
                    jo = MyUtils.newJSONObject(AntSportsRpcCall.userTaskComplete(bizType, taskId));
                    if (jo.optBoolean("success")) {
                        String taskName = taskInfo.optString("taskName", taskId);
                        Log.other("文体中心🧾完成任务[" + taskName + "]");
                    } else {
                        Log.record("文体每日任务" + " " + jo);
                    }
                }
            } else {
                Log.record("文体每日任务" + " " + s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "userTaskGroupQuery err:", t);
        }
    }

    private void participate() {
        try {
            String s = AntSportsRpcCall.queryAccount();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                double balance = jo.optDouble("balance");
                if (balance < 100) {
                    return;
                }
                jo = MyUtils.newJSONObject(AntSportsRpcCall.queryRoundList());
                if (jo.optBoolean("success")) {
                    JSONArray dataList = jo.optJSONArray("dataList");
                    for (int i = 0; dataList != null && i < dataList.length(); i++) {
                        jo = dataList.optJSONObject(i);
                        if (jo == null || !"P".equals(jo.optString("status"))) {
                            continue;
                        }
                        if (jo.has("userRecord")) {
                            continue;
                        }
                        JSONArray instanceList = jo.optJSONArray("instanceList");
                        if (instanceList == null) {
                            continue;
                        }
                        int pointOptions = 0;
                        String roundId = jo.optString("id");
                        String InstanceId = null;
                        String ResultId = null;
                        for (int j = instanceList.length() - 1; j >= 0; j--) {
                            jo = instanceList.optJSONObject(j);
                            if (jo == null || jo.optInt("pointOptions") < pointOptions) {
                                continue;
                            }
                            pointOptions = jo.optInt("pointOptions");
                            InstanceId = jo.optString("id");
                            ResultId = jo.optString("instanceResultId");
                        }
                        jo = MyUtils.newJSONObject(AntSportsRpcCall.participate(pointOptions, InstanceId, ResultId, roundId));
                        if (jo.optBoolean("success")) {
                            jo = jo.optJSONObject("data");
                            String roundDescription = jo != null ? jo.optString("roundDescription") : "";
                            int targetStepCount = jo != null ? jo.optInt("targetStepCount") : 0;
                            Log.other("走路挑战🚶🏻‍♂️[" + roundDescription + "]#" + targetStepCount);
                        } else {
                            Log.record("走路挑战赛" + " " + jo);
                        }
                    }
                } else {
                    Log.record("queryRoundList" + " " + jo);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "participate err:", t);
        }
    }

    private void userTaskRightsReceive() {
        try {
            String s = AntSportsRpcCall.userTaskGroupQuery("SPORTS_DAILY_GROUP");
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.optJSONObject("group");
                JSONArray userTaskList = jo != null ? jo.optJSONArray("userTaskList") : null;
                for (int i = 0; userTaskList != null && i < userTaskList.length(); i++) {
                    jo = userTaskList.optJSONObject(i);
                    if (jo == null || !"COMPLETED".equals(jo.optString("status"))) {
                        continue;
                    }
                    String userTaskId = jo.optString("userTaskId");
                    JSONObject taskInfo = jo.optJSONObject("taskInfo");
                    if (taskInfo == null) {
                        continue;
                    }
                    String taskId = taskInfo.optString("taskId");
                    jo = MyUtils.newJSONObject(AntSportsRpcCall.userTaskRightsReceive(taskId, userTaskId));
                    if (jo.optBoolean("success")) {
                        String taskName = taskInfo.optString("taskName", taskId);
                        JSONArray rightsRuleList = taskInfo.optJSONArray("rightsRuleList");
                        StringBuilder award = new StringBuilder();
                        for (int j = 0; rightsRuleList != null && j < rightsRuleList.length(); j++) {
                            jo = rightsRuleList.optJSONObject(j);
                            if (jo == null) {
                                continue;
                            }
                            award.append(jo.optString("rightsName")).append("*").append(jo.optInt("baseAwardCount"));
                        }
                        Log.other("领取奖励🎖️[" + taskName + "]#" + award);
                    } else {
                        Log.record("文体中心领取奖励");
                        Log.i(jo.toString());
                    }
                }
            } else {
                Log.record("文体中心领取奖励");
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "userTaskRightsReceive err:", t);
        }
    }

    private void pathFeatureQuery() {
        try {
            String s = AntSportsRpcCall.pathFeatureQuery();
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                JSONObject path = jo.optJSONObject("path");
                if (path == null) {
                    return;
                }
                String pathId = path.optString("pathId");
                String title = path.optString("title");
                int minGoStepCount = path.optInt("minGoStepCount");
                JSONObject userPath = jo.optJSONObject("userPath");
                if (userPath != null) {
                    String userPathRecordStatus = userPath.optString("userPathRecordStatus");
                    if ("COMPLETED".equals(userPathRecordStatus)) {
                        pathMapHomepage(pathId);
                        pathMapJoin(title, pathId);
                    } else if ("GOING".equals(userPathRecordStatus)) {
                        pathMapHomepage(pathId);
                        String countDate = Log.getFormatDate();
                        jo = MyUtils.newJSONObject(AntSportsRpcCall.stepQuery(countDate, pathId));
                        if (jo.optBoolean("success")) {
                            int canGoStepCount = jo.optInt("canGoStepCount");
                            if (canGoStepCount >= minGoStepCount) {
                                String userPathRecordId = userPath.optString("userPathRecordId");
                                tiyubizGo(countDate, title, canGoStepCount, pathId, userPathRecordId);
                            }
                        }
                    }
                } else {
                    pathMapJoin(title, pathId);
                }
            } else {
                Log.i(TAG, jo.optString("resultDesc"));
            }
        } catch (Throwable t) {
            Log.err(TAG, "pathFeatureQuery err:", t);
        }
    }

    private void pathMapHomepage(String pathId) {
        try {
            String s = AntSportsRpcCall.pathMapHomepage(pathId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                if (!jo.has("userPathGoRewardList")) {
                    return;
                }
                JSONArray userPathGoRewardList = jo.optJSONArray("userPathGoRewardList");
                for (int i = 0; userPathGoRewardList != null && i < userPathGoRewardList.length(); i++) {
                    jo = userPathGoRewardList.optJSONObject(i);
                    if (jo == null || !"UNRECEIVED".equals(jo.optString("status"))) {
                        continue;
                    }
                    String userPathRewardId = jo.optString("userPathRewardId");
                    jo = MyUtils.newJSONObject(AntSportsRpcCall.rewardReceive(pathId, userPathRewardId));
                    if (jo.optBoolean("success")) {
                        jo = jo.optJSONObject("userPathRewardDetail");
                        JSONArray rightsRuleList = jo != null ? jo.optJSONArray("userPathRewardRightsList") : null;
                        StringBuilder award = new StringBuilder();
                        for (int j = 0; rightsRuleList != null && j < rightsRuleList.length(); j++) {
                            JSONObject rightsItem = rightsRuleList.optJSONObject(j);
                            jo = rightsItem != null ? rightsItem.optJSONObject("rightsContent") : null;
                            if (jo == null) {
                                continue;
                            }
                            award.append(jo.optString("name")).append("*").append(jo.optInt("count"));
                        }
                        Log.other("文体宝箱🎁[" + award + "]");
                    } else {
                        Log.record("文体中心开宝箱");
                        Log.i(jo.toString());
                    }
                }
            } else {
                Log.record("文体中心开宝箱");
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "pathMapHomepage err:", t);
        }
    }

    private void pathMapJoin(String title, String pathId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.pathMapJoin(pathId));
            if (jo.optBoolean("success")) {
                Log.other("加入线路🚶🏻‍♂️[" + title + "]");
                pathFeatureQuery();
            } else {
                Log.i(TAG, jo.toString());
            }
        } catch (Throwable t) {
            Log.err(TAG, "pathMapJoin err:", t);
        }
    }

    private void tiyubizGo(String countDate, String title, int goStepCount, String pathId, String userPathRecordId) {
        try {
            String s = AntSportsRpcCall.tiyubizGo(countDate, goStepCount, pathId, userPathRecordId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if (jo.optBoolean("success")) {
                jo = jo.optJSONObject("userPath");
                if (jo == null) {
                    return;
                }
                Log.other("行走线路🚶🏻‍♂️[" + title + "]#前进了" + jo.optInt("userPathRecordForwardStepCount") + "步");
                pathMapHomepage(pathId);
                boolean completed = "COMPLETED".equals(jo.optString("userPathRecordStatus"));
                if (completed) {
                    Log.other("完成线路🚶🏻‍♂️[" + title + "]");
                    pathFeatureQuery();
                }
            } else {
                Log.i(TAG, s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "tiyubizGo err:", t);
        }
    }

    // 抢好友大战
    // 俱乐部首页，执行抢好友、训练动作、抢购等操作（具体逻辑依赖配置的 clubTrainItemType、clubTradeMemberType 等）
    private void queryClubHome() {
        try {
            // 收运动能量
            JSONObject joBubble = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, joBubble)) {
                return;
            }
            JSONObject mainRoom = joBubble.optJSONObject("mainRoom");
            JSONArray bubbleList = mainRoom != null ? mainRoom.optJSONArray("bubbleList") : null;
            for (int k = 0; bubbleList != null && k < bubbleList.length(); k++) {
                JSONObject bubbleItem = bubbleList.optJSONObject(k);
                if (bubbleItem == null) {
                    continue;
                }
                String bubbleId = bubbleItem.optString("bubbleId");
                collectBubble(bubbleId, "[买卖]");
                TimeUtil.sleep(200);
            }

            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray roomList = jo.optJSONArray("roomList");
            for (int i = 0; roomList != null && i < roomList.length(); i++) {
                // 检查可以购买好友的房号i
                JSONObject room = roomList.optJSONObject(i);
                if (room == null) {
                    continue;
                }
                String roomId = room.optString("roomId");

                // 收取训练好友能量
                JSONArray roombubbleList = room.optJSONArray("bubbleList");
                for (int l = 0; roombubbleList != null && l < roombubbleList.length(); l++) {
                    JSONObject roomBubbleItem = roombubbleList.optJSONObject(l);
                    if (roomBubbleItem == null) {
                        continue;
                    }
                    String bubbleId = roomBubbleItem.optString("bubbleId");
                    // 收取第i号房间需要收取训练好友的第l个能量球
                    collectBubble(bubbleId, "[训练]");
                    TimeUtil.sleep(200);
                }

                JSONArray memberList0 = room.optJSONArray("memberList");
                if (memberList0 != null && memberList0.length() != 0) {
                    continue;
                }

                // 购买好友
                if (clubTradeMemberType.getValue() != TradeMemberType.NONE) {
                    queryMemberPriceRanking(roomId);
                    TimeUtil.sleep(200);
                }
            }
            TimeUtil.sleep(200);

            // 训练好友
            JSONObject joTrain = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, joTrain)) {
                return;
            }
            JSONArray roomListTrain = joTrain.optJSONArray("roomList");
            for (int j = 0; roomListTrain != null && j < roomListTrain.length(); j++) {
                JSONObject roomTrain = roomListTrain.optJSONObject(j);
                JSONArray memberList = roomTrain != null ? roomTrain.optJSONArray("memberList") : null;
                if (memberList != null && memberList.length() != 0) {
                    JSONObject member = memberList.optJSONObject(0);
                    if (member != null) {
                        trainMember(member);
                        TimeUtil.sleep(1000);
                    }
                }
            }

            //蹲点训练好友
            JSONObject autoTrain = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, autoTrain)) {
                return;
            }
            roomListTrain = autoTrain.optJSONArray("roomList");
            for (int j = 0; roomListTrain != null && j < roomListTrain.length(); j++) {
                JSONObject roomTrain = roomListTrain.optJSONObject(j);
                if (roomTrain == null) {
                    continue;
                }
                String roomId = roomTrain.optString("roomId");
                JSONArray memberList = roomTrain.optJSONArray("memberList");
                if (memberList != null && memberList.length() != 0) {
                    JSONObject member = memberList.optJSONObject(0);
                    JSONObject trainInfo = member != null ? member.optJSONObject("trainInfo") : null;
                    if (trainInfo == null) {
                        continue;
                    }
                    if (trainInfo.has("gmtEnd")) {
                        Long gmtEnd = trainInfo.optLong("gmtEnd");
                        long updateTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
                        addChildTask(new ChildModelTask(roomId, "", () -> {
                            autoTrainMember(roomId, gmtEnd);
                        }, updateTime));
                    }
                    TimeUtil.sleep(200);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryClubHome err:", t);
        }
    }

    // 抢好友大战-收集运动能量
    private void collectBubble(String bubbleId, String bubbleType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.collectBubble(bubbleId));
            if (jo.optBoolean("success")) {
                JSONObject ja = jo.optJSONObject("data");
                String collectCoin = ja != null ? ja.optString("changeAmount") : "";
                Log.other("好友大战🧊收取" + bubbleType + "获得[" + collectCoin + "运动能量]");
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectBubble err:", t);
        }
    }

    // 抢好友大战-训练好友
    private void trainMember(JSONObject member) {
        try {
            String memberId = member.optString("memberId");
            String originBossId = member.optString("originBossId");
            JSONObject trainInfo = member.optJSONObject("trainInfo");
            if (trainInfo == null) {
                return;
            }

            String userName = UserIdMap.getShowName(originBossId);
            if (!trainInfo.optBoolean("training")) {
                String itemType = TrainItemType.itemTypes[clubTrainItemType.getValue()];
                if (StringUtil.isEmpty(itemType)) {
                    return;
                }

                String name = TrainItemType.nickNames[clubTrainItemType.getValue()];
                JSONObject queryTrainItemjo = MyUtils.newJSONObject(AntSportsRpcCall.queryTrainItem());
                if (!MessageUtil.checkResultCode(TAG, queryTrainItemjo)) {
                    return;
                }

                // 可以翻倍训练
                if (queryTrainItemjo.has("bizId")) {
                    String bizId = queryTrainItemjo.optString("bizId");
                    String taskAction = "SHOW_AD";
                    queryTrainItemjo = queryTrainItemjo.optJSONObject("taskDetail");
                    String taskId = queryTrainItemjo != null ? queryTrainItemjo.optString("taskId") : "";
                    JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.DoubletrainMember(itemType, bizId, memberId, originBossId));
                    Log.other("好友大战💪训练[" + userName + "]" + name);
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    TimeUtil.sleep(7000);
                    jo = MyUtils.newJSONObject(AntSportsRpcCall.duublecompleteTask(bizId, taskAction, taskId));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        return;
                    }
                    Log.other("好友大战💪翻倍训练[" + userName + "]" + name + "");
                } else {
                    JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.trainMember(itemType, memberId, originBossId));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    Log.other("好友大战💪训练[" + userName + "]" + name + "");
                }
            }

        } catch (Throwable t) {
            Log.err(TAG, "trainMember err:", t);
        }
    }

    // 抢好友大战-蹲点训练
    private void autoTrainMember(String roomId, Long gmtEnd) {
        String taskId = "TRAIN|" + roomId;
        if (!hasChildTask(taskId)) {
            addChildTask(new ChildModelTask(taskId, "TRAIN", () -> {
                AntSportsRpcCall.queryClubRoom(roomId);
            }, gmtEnd));
            // 原先直接 substring(2, 8) + parseInt：roomId 长度不足或含非数字都会抛异常，连带整个模块出错；
            // 这里只用于日志展示，取不到就原样打 roomId
            String roomIdText = roomId.length() > 8 ? roomId.substring(2, 8) : roomId;
            Log.record("蹲点训练💪添加[" + roomIdText + "号房]在[" + TimeUtil.getCommonDate(gmtEnd) + "]执行");
        }
    }

    // 抢好友大战-抢购好友
    private void queryMemberPriceRanking(String roomId) {
        int energyBalance;
        try {
            JSONObject jo1 = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
            if (!MessageUtil.checkResultCode(TAG, jo1)) {
                return;
            }
            JSONObject assetsInfo = jo1.optJSONObject("assetsInfo");
            energyBalance = assetsInfo != null ? assetsInfo.optInt("energyBalance") : 0;
            TimeUtil.sleep(200);
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryMemberPriceRankingEnergy(energyBalance));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            energyBalance = jo.optInt("energyBalance");
            jo = jo.optJSONObject("rank");
            JSONArray ja = jo != null ? jo.optJSONArray("data") : null;
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                int price = jo.optInt("price");
                if (price > energyBalance) {
                    continue;
                }
                String originBossId = jo.optString("originBossId");
                String currentBossId = jo.optString("currentBossId");

                // 判断如果老板是当前账号则查找下一个
                if (currentBossId.equals(UserIdMap.getCurrentUid())) {
                    continue;
                }

                // 判断是否为购买列表中的好友
                boolean isTradeMember = clubTradeMemberList.getValue().contains(originBossId);
                // 判断是选中购买还是未选中购买
                if (clubTradeMemberType.getValue() != TradeMemberType.TRADE) {
                    isTradeMember = !isTradeMember;
                }
                if (!isTradeMember) {
                    continue;
                }

                // 标识为可购买的好友，如果在当前账户的训练房间中则标识为false
                boolean canbuyMember = true;
                JSONObject joTrain = MyUtils.newJSONObject(AntSportsRpcCall.queryClubHome());
                if (!MessageUtil.checkResultCode(TAG, joTrain)) {
                    return;
                }
                JSONArray roomListTrain = joTrain.optJSONArray("roomList");
                for (int j = 0; roomListTrain != null && j < roomListTrain.length(); j++) {
                    JSONObject roomTrain = roomListTrain.optJSONObject(j);
                    JSONArray memberList = roomTrain != null ? roomTrain.optJSONArray("memberList") : null;
                    if (memberList != null && memberList.length() != 0) {
                        JSONObject member = memberList.optJSONObject(0);
                        if (member != null && originBossId.equals(member.optString("originBossId"))) {
                            canbuyMember = false;
                        }
                    }
                }
                // 不管是否购买好友成功，都返回继续检测下一个房间
                if (canbuyMember) {
                    buyMember(roomId, queryClubMember(jo));
                    return;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryMemberPriceRanking err:", t);
        }
        return;
    }

    private JSONObject queryClubMember(JSONObject member) {
        try {
            String memberId = member.optString("memberId");
            String originBossId = member.optString("originBossId");
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryClubMember(memberId, originBossId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject memberObj = jo.optJSONObject("member");
                JSONObject priceInfo = memberObj != null ? memberObj.optJSONObject("priceInfo") : null;
                if (priceInfo != null) {
                    member.put("priceInfo", priceInfo);
                }

                return member;
            }
        } catch (Throwable t) {
            Log.err(TAG, "queryClubMember err:", t);
        }
        return null;
    }

    private Boolean buyMember(String roomId, JSONObject member) {
        if (member == null) {
            return false;
        }
        try {
            String currentBossId = member.optString("currentBossId");
            String currentBossShowName = UserIdMap.getShowName(currentBossId) != null ? UserIdMap.getShowName(currentBossId) : currentBossId;
            String memberId = member.optString("memberId");
            String originBossId = member.optString("originBossId");
            JSONObject priceInfo = member.optJSONObject("priceInfo");
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.buyMember(currentBossId, memberId, originBossId, priceInfo, roomId));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                String userName = UserIdMap.getShowName(originBossId);
                int price = member.optInt("price");
                Log.other("好友大战🉐抢购[" + userName + "]来自[" + currentBossShowName + "]花费[" + price + "健康能量]");
                Toast.show("好友大战🉐抢购[" + userName + "]来自[" + currentBossShowName + "]花费[" + price + "健康能量]");
                return true;
            } else {
                return false;
            }
        } catch (Throwable t) {
            Log.err(TAG, "buyMember err:", t);
        }
        return false;
    }

    private void coinExchangeItem(String itemId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.queryItemDetail(itemId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            if (jo == null || !"OK".equals(jo.optString("exchangeBtnStatus"))) {
                return;
            }
            jo = jo.optJSONObject("itemBaseInfo");
            if (jo == null) {
                return;
            }
            String itemTitle = jo.optString("itemTitle");
            int valueCoinCount = jo.optInt("valueCoinCount");
            jo = MyUtils.newJSONObject(AntSportsRpcCall.exchangeItem(itemId, valueCoinCount));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            jo = jo.optJSONObject("data");
            if (jo != null && jo.optBoolean("exgSuccess")) {
                Log.other("运动好礼🎐兑换[" + itemTitle + "]花费" + valueCoinCount + "运动币");
            }
        } catch (Throwable t) {
            Log.err(TAG, "trainMember err:", t);
        }
    }

    /**
     * 领取特殊奖励
     *
     * @param sceneType  场景类型
     * @param rewardName 奖励名称
     */
    public static void receiveSpecialPrize(String sceneType, String rewardName) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.receiveSpecialPrize(sceneType));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                int energy = data != null ? data.optInt("modifyCount") : 0;
                if (energy > 0) {
                    Log.other("悦动健康🚑️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "receiveSpecialPrize err:", e);
        }
    }

    /**
     * 签到
     *
     * @return 是否签到成功
     */
    public static boolean signIn() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.takeSign());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                JSONObject continuousSignInfo = data != null ? data.optJSONObject("continuousSignInfo") : null;
                JSONObject continuousDoSignInVO = data != null ? data.optJSONObject("continuousDoSignInVO") : null;
                int continuousDay = continuousSignInfo != null ? continuousSignInfo.optInt("continuitySignedDayCount") : 0;
                int reward = continuousDoSignInVO != null ? continuousDoSignInVO.optInt("rewardAmount") : 0;
                Log.other("悦动健康🚑️连续签到[第" + continuousDay + "天]#获得[" + reward + "g健康能量]");
                return true;
            }
        } catch (Exception e) {
            Log.err(TAG, "takeSign err:", e);
        }
        return false;
    }

    /**
     * 领取任务奖励
     *
     * @param task 任务JSON对象
     * @return 是否领取成功
     */
    public static boolean receiveTaskReward(JSONObject task) {
        try {
            task.put("scene", "MED_TASK_HALL").put("source", "jkdprizesign");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.neverlandtaskReceive(arg));

            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                String taskName = task.optString("title");
                JSONObject data = jsonResult.optJSONObject("data");
                JSONArray rewards = data != null ? data.optJSONArray("userItems") : null;
                ArrayList<String> rewardList = rewards != null ? parseRewards(rewards) : new ArrayList<>();
                Log.other("悦动健康🚑️领取奖励[" + taskName + "]#获得" + rewardList);
                return true;
            }
        } catch (Exception e) {
            Log.err(TAG, "taskReceive err:", e);
        }
        return false;
    }

    /**
     * 完成任务
     *
     * @param task 任务JSON对象
     * @return 是否完成成功
     */
    public static boolean completeTask(JSONObject task) {
        try {
            task.put("scene", "MED_TASK_HALL");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.neverlandtaskSend(arg));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                String taskName = task.optString("title");
                Log.other("悦动健康🚑️完成任务[" + taskName + "]");
                TimeUtil.sleep(1000);
                return true;
            }
        } catch (Exception e) {
            Log.err(TAG, "taskSend err:", e);
        }
        return false;
    }

    /**
     * 能量泵前进
     *
     * @param branchId 分支ID
     * @param mapId    地图ID
     * @param mapName  地图名称
     * @return 是否继续前进
     */
    public static boolean walkGrid(String branchId, String mapId, String mapName) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.neverlandwalkGrid(branchId, mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                if (data == null) {
                    return false;
                }
                JSONArray mapAwards = data.optJSONArray("mapAwards");
                JSONObject firstAward = mapAwards != null ? mapAwards.optJSONObject(0) : null;
                int step = firstAward != null ? firstAward.optInt("step") : 0;
                int leftCount = data.optInt("leftCount");
                Log.other("悦动健康🚑️能量泵[" + mapName + "]#前进[" + step + "步]");

                JSONArray rewards = data.optJSONArray("userItems");
                ArrayList<String> rewardList = rewards != null ? parseRewards(rewards) : new ArrayList<>();
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️能量泵[" + mapName + "]#获得" + rewardList);
                }

                JSONObject starData = data.optJSONObject("starData");
                int currentStar = starData != null ? starData.optInt("curr") : 0;
                int totalStar = starData != null ? starData.optInt("count") : 0;
                return leftCount >= 5 && currentStar < totalStar;
            }
        } catch (Exception e) {
            Log.err(TAG, "walkGrid err:", e);
        }
        return false;
    }

    public static int build(String branchId, String mapId, String mapName, int multiNum) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.build(branchId, mapId, multiNum));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                if (data == null) return 0;
                JSONObject endStageInfo = data.optJSONObject("endStageInfo");
                if (endStageInfo == null) return 0;
                int buildingEnergyFinal = endStageInfo.optInt("buildingEnergyFinal");
                String buildingId = endStageInfo.optString("buildingId");
                int endbuildingEnergyProcess = endStageInfo.optInt("buildingEnergyProcess");
                Log.other("悦动健康🚑️能量泵[" + mapName + "]建造[" + buildingId + "]进度(" + endbuildingEnergyProcess + "/" + buildingEnergyFinal + ")#消耗" + multiNum * 5 + "g能量");
                JSONArray rewards = data.optJSONArray("rewards");
                ArrayList<String> rewardList = rewards != null ? parseRewards(rewards) : new ArrayList<>();
                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️能量泵[" + mapName + "]#获得" + rewardList);
                }
                return buildingEnergyFinal - endbuildingEnergyProcess;
            }
        } catch (Exception e) {
            Log.err(TAG, "build err:", e);
        }
        return 0;
    }

    /**
     * 领取浏览任务奖励
     *
     * @param task 任务JSON对象
     * @return 是否领取成功
     */
    public static boolean receiveBrowseReward(JSONObject task) {
        if (!task.has("encryptValue") || !task.has("energyNum")) {
            return false;
        }

        try {
            task.put("type", "LIGHT_FEEDS_TASK");
            String arg = "[" + task.toString() + "]";
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.neverlandenergyReceive(arg));

            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                JSONArray prizes = data != null ? data.optJSONArray("prizes") : null;
                int totalEnergy = 0;
                for (int i = 0; prizes != null && i < prizes.length(); i++) {
                    JSONObject prize = prizes.optJSONObject(i);
                    if (prize != null) {
                        totalEnergy += prize.optInt("prizeCount");
                    }
                }

                String taskName = task.optString("title", "浏览商品15s得健康能量");
                Log.other("悦动健康🚑️完成任务[" + taskName + "]#获得[" + totalEnergy + "g健康能量]");
                return true;
            }
        } catch (Exception e) {
            Log.err(TAG, "energyReceive err:", e);
        }
        return false;
    }

    /**
     * 领取离线奖励
     */
    public static void receiveOfflineReward() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.offlineAward());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                JSONArray rewards = data != null ? data.optJSONArray("userItems") : null;
                ArrayList<String> rewardList = rewards != null ? parseRewards(rewards) : new ArrayList<>();

                if (!rewardList.isEmpty()) {
                    Log.other("悦动健康🚑️领取奖励[离线奖励]#获得" + rewardList);
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "offlineAward err:", e);
        }
    }

    /**
     * 解析奖励列表
     *
     * @param rewards 奖励JSON数组
     * @return 格式化后的奖励列表
     */
    public static ArrayList<String> parseRewards(JSONArray rewards) {
        ArrayList<String> rewardList = new ArrayList<>();
        try {
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.optJSONObject(i);
                if (reward == null) {
                    continue;
                }
                int count = reward.optInt("modifyCount");
                if (count <= 0) {
                    continue;
                }

                String unit = "H1".equals(reward.optString("itemId")) ? "g" : "";
                String name = reward.optString("name", "");
                if (name.isEmpty()) continue;
                rewardList.add(count + unit + name);
            }
        } catch (Exception e) {
            Log.err(TAG, "parseRewards err:", e);
        }
        return rewardList;
    }

    /**
     * 领取气泡任务奖励
     *
     * @param recordId   记录ID
     * @param rewardName 奖励名称
     */
    public static void receiveBubbleReward(String recordId, String rewardName) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.neverlandpickBubbleTaskEnergy(recordId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                String energy = data != null ? data.optString("changeAmount") : "";
                Log.other("悦动健康🚑️领取奖励[" + rewardName + "]#获得[" + energy + "g健康能量]");
            }
        } catch (Exception e) {
            Log.err(TAG, "pickBubbleTaskEnergy err:", e);
        }
    }

    /**
     * 查询基础信息并处理相关任务
     */
    public void queryBaseInfoAndProcess() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryBaseinfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject data = jsonResult.optJSONObject("data");
            if (data == null) {
                return;
            }
            // 处理离线奖励
            JSONArray offlineAwards = data.optJSONArray("offlineAwards");
            if (offlineAwards != null && offlineAwards.length() > 0) {
                receiveOfflineReward();
            }

            // 处理普通岛能量泵任务
            if (!data.optBoolean("newGame") && WALK_GRID.getValue()) {
                String branchId = data.optString("branchId");
                String mapId = data.optString("mapId");
                String mapName = data.optString("mapName");
                int walkGridcount = 0;
                if (canWalkGrid(branchId, mapId) && queryUserEnergy() >= 5 && queryUserEnergy() >= WALK_GRID_LIMIT.getValue()) {
                    while (walkGrid(branchId, mapId, mapName)) {
                        TimeUtil.sleep(2000);
                        if (WALK_GRID_MAX.getValue() == 0) {
                            continue;
                        }
                        walkGridcount++;
                        if (walkGridcount >= WALK_GRID_MAX.getValue() || queryUserEnergy() < 5 || queryUserEnergy() <= WALK_GRID_LIMIT.getValue()) {
                            break;
                        }
                    }
                }
            }
            // 处理活动岛能量泵任务
            if (data.optBoolean("newGame") && WALK_GRID.getValue()) {
                String branchId = data.optString("branchId");
                String mapId = data.optString("mapId");
                String mapName = data.optString("mapName");
                int buildcount = 0;
                if (canBuild(mapId) && queryUserEnergy() >= 5 && queryUserEnergy() >= WALK_GRID_LIMIT.getValue()) {
                    int remainBuildingEnergyProcess = build(branchId, mapId, mapName, 1);
                    buildcount++;
                    if (buildcount >= WALK_GRID_MAX.getValue() && WALK_GRID_MAX.getValue() != 0) {
                        return;
                    }
                    while (remainBuildingEnergyProcess > 0 && canBuild(mapId)) {
                        TimeUtil.sleep(2000);
                        if (remainBuildingEnergyProcess >= 50 && ((WALK_GRID_MAX.getValue() - buildcount) >= 10 || WALK_GRID_MAX.getValue() == 0) && queryUserEnergy() >= 50) {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 10);
                            buildcount = buildcount + 10;
                        } else if (remainBuildingEnergyProcess >= 25 && ((WALK_GRID_MAX.getValue() - buildcount) >= 5 || WALK_GRID_MAX.getValue() == 0) && queryUserEnergy() >= 25) {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 5);
                            buildcount = buildcount + 5;
                        } else {
                            remainBuildingEnergyProcess = build(branchId, mapId, mapName, 1);
                            buildcount++;
                        }
                        if (WALK_GRID_MAX.getValue() == 0) {
                            continue;
                        }
                        if (buildcount >= WALK_GRID_MAX.getValue() || queryUserEnergy() < 5 || queryUserEnergy() <= WALK_GRID_LIMIT.getValue()) {
                            break;
                        }
                    }
                }
            }
            if (awardspecialActivityReceive.getValue()) {
                //领取活动岛奖励
                if (data.optBoolean("newGame")) {
                    String branchId = data.optString("branchId");
                    String mapId = data.optString("mapId");
                    String mapName = data.optString("mapName");
                    jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryMapDetail(mapId));
                    if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                        JSONObject dataMapDetail = jsonResult.optJSONObject("data");
                        JSONObject baseMapInfo = dataMapDetail != null ? dataMapDetail.optJSONObject("baseMapInfo") : null;
                        if (baseMapInfo != null && baseMapInfo.optInt("currentPercent") == 100 && baseMapInfo.optString("status").equals("FINISH_NOT_REWARD")) {
                            JSONArray rewards = baseMapInfo.optJSONArray("rewards");
                            for (int i = 0; rewards != null && i < rewards.length(); i++) {
                                JSONObject reward = rewards.optJSONObject(i);
                                if (reward != null && reward.optString("prizeStatus").equals("待领取")) {
                                    String itemId = reward.optString("itemId");
                                    JSONObject mapChooseRewardjo = MyUtils.newJSONObject(AntSportsRpcCall.mapChooseReward(branchId, mapId, itemId));
                                    if (MessageUtil.checkSuccess(TAG, mapChooseRewardjo)) {
                                        data = mapChooseRewardjo.optJSONObject("data");
                                        JSONObject specialActivityReceiveResult = data != null ? data.optJSONObject("specialActivityReceiveResult") : null;
                                        JSONArray prizes = specialActivityReceiveResult != null ? specialActivityReceiveResult.optJSONArray("prizes") : null;
                                        JSONObject prize = prizes != null ? prizes.optJSONObject(0) : null;
                                        if (prize == null) {
                                            continue;
                                        }
                                        String subTitle = prize.optString("subTitle");
                                        String title = prize.optString("title");
                                        Log.other("悦动健康🚑️领取奖励[" + subTitle + "]#获得[" + title + "]");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "queryBaseInfo err:", e);
        }
    }

    /**
     * 查询气泡任务并处理
     */
    public static void queryAndProcessBubbleTasks() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryBubbleTask());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject data = jsonResult.optJSONObject("data");
            JSONArray tasks = data != null ? data.optJSONArray("bubbleTaskVOS") : null;
            boolean needRetry = false;

            for (int i = 0; tasks != null && i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null || !task.has("bubbleTaskStatus")) {
                    continue;
                }
                String title = task.optString("title");
                String bubbleTaskStatus = task.optString("bubbleTaskStatus");

                if (bubbleTaskStatus.equals("INIT")) {
                    if ("AD_BALL".equals(task.optString("taskId"))) {
                        task.put("lightTaskId", "adBubble");
                        if (receiveBrowseReward(task)) {
                            TimeUtil.sleep(1000);
                            needRetry = true;
                        }
                    } else if ("STRATEGY_BALL".equals(task.optString("taskId"))) {
                        receiveSpecialPrize(task.optString("taskId") + "_ACTIVITY", title);
                    } else if ("SIGN_BALL".equals(task.optString("taskId"))) {
                        signIn();
                    }
                    break;
                }
                if (bubbleTaskStatus.equals("TO_RECEIVE")) {
                    // 已完成任务，领取奖励
                    receiveBubbleReward(task.optString("medEnergyBallInfoRecordId"), title);
                    break;
                }
            }
            // 如果有任务触发了状态变更，重试一次
            if (needRetry) {
                queryAndProcessBubbleTasks();
            }
        } catch (Exception e) {
            Log.err(TAG, "queryBubbleTask err:", e);
        }
    }

    /**
     * 检查是否可进行能量泵前进
     *
     * @param branchId 分支ID
     * @param mapId    地图ID
     * @return 是否可前进
     */
    public static boolean canWalkGrid(String branchId, String mapId) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryMapInfo(branchId, mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                JSONObject starData = data != null ? data.optJSONObject("starData") : null;
                if (data == null || starData == null) {
                    return false;
                }
                return data.optBoolean("canWalk") && starData.optInt("curr") < starData.optInt("count");
            }
        } catch (Exception e) {
            Log.err(TAG, "canWalkGrid err:", e);
        }
        return false;
    }

    public static boolean canBuild(String mapId) {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryMapDetail(mapId));
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                JSONObject baseMapInfo = data != null ? data.optJSONObject("baseMapInfo") : null;
                if (baseMapInfo == null) {
                    return false;
                }
                return baseMapInfo.optBoolean("newIsLandFlg") && baseMapInfo.optInt("currentPercent") < 100;
            }
        } catch (Exception e) {
            Log.err(TAG, "canBuild err:", e);
        }
        return false;
    }

    /**
     * 处理签到逻辑
     */
    public static void processSignIn() {
        if (Status.hasFlagToday("NeverLand::SIGN")) {
            return;
        }

        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.querySign());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }

            JSONObject data = jsonResult.optJSONObject("data");
            if (data == null || !data.has("days")) {
                return;
            }

            JSONArray days = data.optJSONArray("days");
            for (int i = 0; days != null && i < days.length(); i++) {
                JSONObject day = days.optJSONObject(i);
                if (day != null && day.optBoolean("current") && !day.optBoolean("signIn")) {
                    if (signIn()) {
                        Status.flagToday("NeverLand::SIGN");
                        return;
                    }
                }
            }

            // 检查连续签到状态
            if (data.has("continuousSignInfo")) {
                JSONObject continuousInfo = data.optJSONObject("continuousSignInfo");
                if (continuousInfo == null) {
                    return;
                }
                if (continuousInfo.optBoolean("signedToday") || signIn()) {
                    Status.flagToday("NeverLand::SIGN");
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "processSignIn err:", e);
        }
    }

    /**
     * 处理任务中心任务
     */
    public static void processTaskCenter() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryTaskCenter());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }

            JSONObject data = jsonResult.optJSONObject("data");
            JSONArray tasks = data != null ? data.optJSONArray("taskCenterTaskVOS") : null;
            boolean needRetry = false;

            for (int i = 0; tasks != null && i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String status = task.optString("taskStatus");

                if ("SIGNUP_COMPLETE".equals(status)) {
                    String taskType = task.optString("taskType");
                    if ("LIGHT_TASK".equals(taskType)) {
                        if (task.has("logExtMap")) {
                            JSONObject logExtMap = task.optJSONObject("logExtMap");
                            //if (TaskHelper.checkTaskCompleted(logExtMap.getString("taskType"), logExtMap.getString("bizId"))) {
                            //
                            //    TimeUtil.sleep(1000);
                            //    needRetry = true;
                            //}
                        }
                    } else if ("PROMOKERNEL_TASK".equals(taskType)) {
                        if (completeTask(task)) {
                            task.put("taskStatus", "TO_RECEIVE");
                            TimeUtil.sleep(2000);
                            needRetry = true;
                        }
                    }
                } else if ("TO_RECEIVE".equals(status)) {
                    if (receiveTaskReward(task)) {
                        TimeUtil.sleep(1000);
                        needRetry = true;
                    }
                }
            }

            if (needRetry) {
                processTaskCenter();
            }
        } catch (Exception e) {
            Log.err(TAG, "processTaskCenter err:", e);
        }
    }

    /**
     * 处理浏览任务
     */
    public static void processBrowseTasks() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryTaskInfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }

            JSONObject data = jsonResult.optJSONObject("data");
            if (data == null || !data.has("taskInfos")) {
                return;
            }

            JSONArray tasks = data.optJSONArray("taskInfos");
            boolean hasNewTask = false;

            for (int i = 0; tasks != null && i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                TimeUtil.sleep(TimeUnit.SECONDS.toMillis(task.optInt("viewSec")));
                if (receiveBrowseReward(task)) {
                    hasNewTask = true;
                }
            }

            if (hasNewTask) {
                processBrowseTasks();
            }
        } catch (Exception e) {
            Log.err(TAG, "processBrowseTasks err:", e);
        }
    }

    /**
     * 查询用户能量值
     *
     * @return 能量值
     */
    public static int queryUserEnergy() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryUserAccount());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject data = jsonResult.optJSONObject("data");
                if (data != null) {
                    return Integer.parseInt(data.optString("balance", "0"));
                }
            }
        } catch (Exception e) {
            Log.err(TAG, "queryUserEnergy err:", e);
        }
        return 0;
    }

    public void neverlandrun() {
        try {
            // 处理签到
            if (QUERY_SIGN.getValue()) {
                processSignIn();
            }
            // 处理任务中心
            if (QUERY_TASK_CENTER.getValue()) {
                processTaskCenter();
            }
            // 处理浏览任务
            processBrowseTasks();
            // 处理气泡任务
            if (QUERY_BUBBLE_TASK.getValue()) {
                queryAndProcessBubbleTasks();
            }
            // 处理基础信息相关任务
            queryBaseInfoAndProcess();
            // 自动切岛
            if (MapListSwitch.getValue()) {
                queryMapListSwitch();
            }
        } catch (Exception e) {
            Log.err(TAG, "run err:", e);
        }
    }

    private void queryMapListSwitch() {
        try {
            //获取当前岛名字
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.queryBaseinfo());
            if (!MessageUtil.checkSuccess(TAG, jsonResult)) {
                return;
            }
            JSONObject thisdata = jsonResult.optJSONObject("data");
            String thismapName = thisdata != null ? thisdata.optString("mapName") : "";

            //获取岛地图
            JSONObject jsonLandMap = MyUtils.newJSONObject(AntSportsRpcCall.queryMapList());
            if (MessageUtil.checkSuccess(TAG, jsonLandMap)) {
                JSONObject data = jsonLandMap.optJSONObject("data");

                JSONArray mapList = data != null ? data.optJSONArray("mapList") : null;
                boolean needSwitch = false;

                for (int i = 0; mapList != null && i < mapList.length(); i++) {
                    JSONObject map = mapList.optJSONObject(i);
                    if (map == null) {
                        continue;
                    }
                    String mapName = map.optString("mapName");
                    String status = map.optString("status");

                    if (mapName.equals(thismapName) && status.contains("FINISH")) {
                        needSwitch = true;
                    }
                }
                if (needSwitch) {
                    for (int i = 0; mapList != null && i < mapList.length(); i++) {
                        JSONObject map = mapList.optJSONObject(i);
                        if (map == null) {
                            continue;
                        }
                        String mapName = map.optString("mapName");
                        String mapId = map.optString("mapId");
                        String status = map.optString("status");
                        String branchId = map.optString("branchId");
                        //boolean newIsLandFlg = map.optBoolean("newIsLandFlg");

                        if (!mapName.equals(thismapName)) {
                            //if (!status.contains("FINISH") && !newIsLandFlg) {
                            if (!status.contains("FINISH")) {
                                JSONObject jo = MyUtils.newJSONObject(AntSportsRpcCall.mapChooseFree(branchId, mapId));
                                if (MessageUtil.checkSuccess(TAG, jo)) {
                                    Log.other("悦动健康🚑️切换到[" + mapName + "](" + mapId + ")");
                                    break;
                                }
                            }
                        }
                    }
                    queryBaseInfoAndProcess();
                }

            }
        } catch (Exception e) {
            Log.err(TAG, "queryMapListSwitch err:", e);
        }
    }

    /**
     * 检查权限
     *
     * @return 是否有权限
     */
    private boolean checkAuth() {
        try {
            JSONObject jsonResult = MyUtils.newJSONObject(AntSportsRpcCall.checkAuth());
            if (MessageUtil.checkSuccess(TAG, jsonResult)) {
                JSONObject resultObj = jsonResult.optJSONObject("resultObj");
                return resultObj != null && resultObj.optBoolean("authStatus");
            }
        } catch (Exception e) {
            Log.err(TAG, "checkAuth err:", e);
        }
        return false;
    }

    // 任务状态枚举
    public enum neverlandTaskStatus {
        TODO, FINISHED, EXPIRED, DISABLED
    }

    // 能量策略枚举
    public interface EnergyStrategy {
        int NONE = 0;
        int CONSERVE = 1;
        int MAXIMIZE = 2;
        String[] nickNames = {"不操作", "保守策略", "最大化收益"};
    }

    // 任务选项接口
    public interface NeverLandOption {
    }

    public enum PathCompleteStatus {
        NOT_JOIN, JOIN, NOT_COMPLETED, COMPLETED, INTERRUPT;
    }

    public enum TaskStatus {
        WAIT_COMPLETE, WAIT_RECEIVE, HAS_RECEIVED;
    }


    public interface WalkPathTheme {
        int DA_MEI_ZHONG_GUO = 0;
        int GONG_YI_YI_XIAO_BU = 1;
        int DENG_DING_ZHI_MA_SHAN = 2;
        int WEI_C_DA_TIAO_ZHAN = 3;
        int LONG_NIAN_QI_FU = 4;
        int SHOU_HU_TI_YU_MENG = 5;

        String[] nickNames = {"大美中国", "公益一小步", "登顶芝麻山", "维C大挑战", "龙年祈福", "守护体育梦"};
        String[] walkPathThemeIds = {"M202308082226", "M202401042147", "V202405271625", "202404221422", "WF202312050200", "V202409061650"};
    }

    public interface DonateCharityCoinType {

        int ZERO = 0;
        int ONE = 1;
        int ALL = 2;

        String[] nickNames = {"不捐赠", "捐赠一个项目", "捐赠所有项目"};
    }

    public interface TradeMemberType {

        int NONE = 0;
        int TRADE = 1;
        int NOT_TRADE = 2;

        String[] nickNames = {"不抢购", "抢购已选好友", "抢购未选好友"};
    }

    public interface TrainItemType {

        int NONE = 0;
        int BALLET = 1;
        int SANDBAG = 2;
        int BARBELL = 3;
        int YANGKO = 4;
        int SKATE = 5;
        int MUD = 6;

        String[] nickNames = {"不训练", "跳芭蕾", "打沙包", "举杠铃", "扭秧歌", "玩滑板", "踩泥坑"};
        String[] itemTypes = {"", "ballet", "sandbag", "barbell", "yangko", "skate", "mud"};
    }
}
