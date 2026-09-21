package io.github.aw1y2z.sesame.data.task;

import static io.github.aw1y2z.sesame.model.normal.base.BaseModel.taskRpcRequest;

import android.os.Build;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import lombok.Getter;
import io.github.aw1y2z.sesame.data.Model;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.ModelType;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ModelTask extends Model {

    private static final Map<ModelTask, Thread> MAIN_TASK_MAP = new ConcurrentHashMap<>();
    private static Thread completionWatcher;
    private static long completionGeneration;

    private static final int MODEL_COUNT = Math.max(getModelArray().length, 1);

    /**
     * 主任务线程池：模型主循环是长任务，每个模型占一条线程（core = 模型数）。
     * <p>上限取 2 倍模型数：原先 maximumPoolSize 是 Integer.MAX_VALUE，pool 永远不会饱和，
     * 也就永远是"来一个任务就新建一条线程"，CallerRunsPolicy 形同虚设；改为有限上限后，
     * 极端情况下才会回退到调用线程执行（原来的兜底语义）。
     */
    private static final ThreadPoolExecutor MAIN_THREAD_POOL = new ThreadPoolExecutor(MODEL_COUNT, MODEL_COUNT * 2, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    private final Map<String, ChildModelTask> childTaskMap = new ConcurrentHashMap<>();

    private ChildTaskExecutor childTaskExecutor;

    private final AtomicBoolean mainPending = new AtomicBoolean();

    @Getter
    private final Runnable mainRunnable = new Runnable() {

        private final ModelTask task = ModelTask.this;

        @Override
        public void run() {
            if (MAIN_TASK_MAP.get(task) != null) {
                return;
            }
            MAIN_TASK_MAP.put(task, Thread.currentThread());
            Log.record("执行开始-" + task.getName());
            Log.startModuleLogCount();
            try {
                task.run();
            } catch (Exception e) {
                Log.printStackTrace(e);
            } finally {
                // 本轮模块未产生任何动作时，在运行日志中给出提示
                if (Log.stopModuleLogCount() == 0) {
                    Log.record(task.getName() + "✅本轮无操作");
                }
                Log.record("执行结束-" + task.getName());
                synchronized (MAIN_TASK_MAP) { MAIN_TASK_MAP.remove(task); }
            }
        }

    };

    /** 当前正在执行主任务的模块名，验证码弹窗归因用。 */
    public static java.util.List<String> runningTaskNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ModelTask task : MAIN_TASK_MAP.keySet()) {
            names.add(task.getName());
        }
        return names;
    }

    public ModelTask() {
    }

    @Override
    public final void prepare() {
        childTaskExecutor = newTimedTaskExecutor();
    }

    public String getId() {
        return toString();
    }

    public ModelType getType() {
        return ModelType.TASK;
    }

    public abstract String getName();

    public abstract ModelFields getFields();

    public abstract Boolean check();

    public Boolean isSync() {
        return false;
    }

    public abstract void run();

    public Boolean hasChildTask(String childId) {
        return childTaskMap.containsKey(childId);
    }

    public ChildModelTask getChildTask(String childId) {
        return childTaskMap.get(childId);
    }

    public Boolean addChildTask(ChildModelTask childTask) {
        String childId = childTask.getId();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return childTask == childTaskMap.compute(childId, (key, value) -> {
                if (value != null) {
                    value.cancel();
                }
                childTask.modelTask = this;
                if (childTaskExecutor.addChildTask(childTask)) {
                    return childTask;
                }
                return null;
            });
        } else {
            synchronized (childTaskMap) {
                ChildModelTask oldTask = childTaskMap.get(childId);
                if (oldTask != null) {
                    oldTask.cancel();
                }
                childTask.modelTask = this;
                if (childTaskExecutor.addChildTask(childTask)) {
                    childTaskMap.put(childId, childTask);
                    return true;
                }
                return false;
            }
        }
    }

    public void removeChildTask(String childId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            childTaskMap.compute(childId, (key, value) -> {
                if (value != null) {
                    childTaskExecutor.removeChildTask(value);
                }
                return null;
            });
        } else {
            synchronized (childTaskMap) {
                ChildModelTask childTask = childTaskMap.get(childId);
                if (childTask != null) {
                    childTaskExecutor.removeChildTask(childTask);
                }
                childTaskMap.remove(childId);
            }
        }
    }

    public Integer countChildTask() {
        return childTaskMap.size();
    }

    public Boolean startTask() {
        return startTask(false);
    }

    public synchronized Boolean startTask(Boolean force) {
        if (mainPending.get()) {
            if (force) stopTask();
            return false;
        }
        TaskLifecycle.Work work = TaskLifecycle.enter();
        if (work == null) return false;
        boolean submitted = false;
        try {
            if (isEnable() && check() && mainPending.compareAndSet(false, true)) {
                Runnable admitted = () -> {
                    try { mainRunnable.run(); }
                    finally {
                        mainPending.set(false);
                        work.close();
                    }
                };
                if (isSync()) admitted.run();
                else MAIN_THREAD_POOL.execute(admitted);
                submitted = true;
                return true;
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        } finally {
            if (!submitted) {
                mainPending.set(false);
                work.close();
            }
        }
        return false;
    }

    public synchronized void stopTask() {
        for (ChildModelTask childModelTask : childTaskMap.values()) {
            try {
                childModelTask.cancel();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
        if (childTaskExecutor != null) {
            childTaskExecutor.clearAllChildTask();
        }
        childTaskMap.clear();
        synchronized (MAIN_TASK_MAP) {
            Thread running = MAIN_TASK_MAP.get(this);
            if (running != null) running.interrupt();
        }
        // A cancelled worker remains active until its finally block actually exits.
    }

    public static void startAllTask() {
        startAllTask(false);
    }

    public static void startAllTask(Boolean force) {
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return;
            watchCompletion();
            boolean dispatched = false;
            try {
                dispatchAllTask(force);
                dispatched = !Thread.currentThread().isInterrupted();
            } finally {
                if (!dispatched) cancelCompletion();
            }
        }
    }

    private static void watchCompletion() {
        synchronized (TaskLifecycle.class) {
            long generation = TaskLifecycle.generation();
            if (completionWatcher != null && !completionWatcher.isInterrupted()
                    && completionGeneration == generation) return;
            completionGeneration = generation;
            completionWatcher = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (TaskLifecycle.class) {
                            if (completionWatcher != Thread.currentThread()
                                    || TaskLifecycle.generation() != generation) return;
                            if (TaskLifecycle.isIdle() && !hasReadyChildren()) {
                                Log.record("🏁全部任务已执行完成");
                                completionWatcher = null;
                                return;
                            }
                        }
                        // ponytail: 单个监听器最多延迟 500ms；需要即时通知时改为完成回调。
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    synchronized (TaskLifecycle.class) {
                        if (completionWatcher == Thread.currentThread()) completionWatcher = null;
                    }
                }
            }, "TaskCompletionWatcher");
            completionWatcher.setDaemon(true);
            completionWatcher.start();
        }
    }

    private static boolean hasReadyChildren() {
        long now = System.currentTimeMillis();
        for (Model model : getModelArray()) {
            if (model instanceof ModelTask) {
                for (ChildModelTask child : ((ModelTask) model).childTaskMap.values()) {
                    // 本轮等待已到期子任务，未来的蹲点/定时任务留待后续执行。
                    if (!child.isCancel && child.getExecTime() <= now) return true;
                }
            }
        }
        return false;
    }

    private static void cancelCompletion() {
        synchronized (TaskLifecycle.class) {
            if (completionWatcher != null) completionWatcher.interrupt();
            completionWatcher = null;
        }
    }

    private static void dispatchAllTask(Boolean force) {
        //自动触发备份配置文件
        if (!Status.hasFlagToday("Config::backup")) {
            FileUtil.backupConfigV2WithRolling(UserIdMap.getCurrentUid());
            Status.flagToday("Config::backup");
        }
        //执行BaseModel中自定义执行请求
        taskRpcRequest();
        for (Model model : getModelArray()) {
            if (model != null) {
                if (ModelType.TASK == model.getType()) {
                    if (((ModelTask) model).startTask(force)) {
                        try {
                            Thread.sleep(750);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }
    }

    public static boolean hasPendingMainTask() {
        for (Model model : getModelArray()) {
            if (model instanceof ModelTask && ((ModelTask) model).mainPending.get()) return true;
        }
        return false;
    }

    /** Delayed children are pending; only admitted dispatch and running work block switching. */
    public static boolean isAllTaskIdle() {
        return TaskLifecycle.isIdle();
    }

    /**
     * 只执行指定分组下的任务（{@code groupCode} 取 {@link ModelGroup#getCode()}）。
     * <p>配置页的"执行"按钮必须通过广播交给<strong>注入进程</strong>跑：模块 App 自己的进程没有
     * libxposed 类（{@code ApplicationHook}/{@code hook.Toast}/{@code NotificationUtil} 一碰就是
     * NoClassDefFoundError），而且那是个 UI 进程，把任务循环压在主线程上会直接卡死界面。
     *
     * @return 实际触发的任务数
     */
    public static int startGroupTask(String groupCode) {
        int count = 0;
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType()) {
                continue;
            }
            ModelGroup group = model.getGroup();
            if (group == null || !groupCode.equals(group.getCode())) {
                continue;
            }
            if (((ModelTask) model).startTask(false)) {
                count++;
            }
        }
        return count;
    }

    public static void stopAllTask() {
        cancelCompletion();
        for (Model model : getModelArray()) {
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }
    }

    private ChildTaskExecutor newTimedTaskExecutor() {
        ChildTaskExecutor childTaskExecutor;
        Integer timedTaskModel = BaseModel.getTimedTaskModel().getValue();
        if (timedTaskModel == BaseModel.TimedTaskModel.SYSTEM) {
            childTaskExecutor = new SystemChildTaskExecutor();
        } else if (timedTaskModel == BaseModel.TimedTaskModel.PROGRAM) {
            childTaskExecutor = new ProgramChildTaskExecutor();
        } else {
            throw new RuntimeException("not found childTaskExecutor");
        }
        return childTaskExecutor;
    }

    public static class ChildModelTask implements Runnable {

        @Getter
        private ModelTask modelTask;

        @Getter
        private final String id;

        @Getter
        private final String group;

        private final Runnable runnable;

        @Getter
        private final Long execTime;

        private CancelTask cancelTask;

        @Getter
        private volatile Boolean isCancel = false;

        private final long generation = TaskLifecycle.generation();

        public ChildModelTask() {
            this(null, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id) {
            this(id, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id, String group) {
            this(id, group, () -> {
            }, 0L);
        }

        protected ChildModelTask(String id, long execTime) {
            this(id, null, null, execTime);
        }

        /*protected ChildModelTask(String id, String group, Long time) {
            this(id, group, null, time);
        }*/

        public ChildModelTask(String id, Runnable runnable) {
            this(id, null, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable) {
            this(id, group, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable, Long execTime) {
            if (StringUtil.isEmpty(id)) {
                id = toString();
            }
            if (StringUtil.isEmpty(group)) {
                group = "DEFAULT";
            }
            if (runnable == null) {
                runnable = setRunnable();
            }
            this.id = id;
            this.group = group;
            this.runnable = runnable;
            this.execTime = execTime;
        }

        public Runnable setRunnable() {
            return null;
        }

        public final void run() {
            try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                if (work != null && !isCancel) runnable.run();
            }
        }

        protected synchronized void setCancelTask(CancelTask cancelTask) {
            this.cancelTask = cancelTask;
            if (isCancel) cancelTask.cancel();
        }

        public final synchronized void cancel() {
            isCancel = true;
            if (cancelTask != null) {
                try {
                    cancelTask.cancel();
                    isCancel = true;
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }

    }

    public interface CancelTask {

        void cancel();

    }

}
