package io.github.aw1y2z.sesame.data.task;

import static io.github.aw1y2z.sesame.model.normal.base.BaseModel.taskRpcRequest;

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
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

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

    /** 本模型的代际编号：stopTask 递增，旧代在下一个检查点作废退出，无需 join 阻塞调用方 */
    private volatile long generation = 0;

    /** 执行槽（只限本实例；重载后是新对象，跨实例靠代际令牌在检查点退出）：抢不到即放弃，不用实例锁 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static final int MODEL_COUNT = Math.max(getModelArray().length, 1);

    /**
     * 主任务线程池：每个模型占一条线程（core = 模型数），上限 2 倍模型数，饱和后回退到调用线程执行。
     * <p>SynchronousQueue 不排队，故不存在「撤销已提交但尚未开始的那一轮」这种操作。
     */
    private static final ThreadPoolExecutor MAIN_THREAD_POOL = new ThreadPoolExecutor(MODEL_COUNT, MODEL_COUNT * 2, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    private final Map<String, ChildModelTask> childTaskMap = new ConcurrentHashMap<>();

    private ChildTaskExecutor childTaskExecutor;

    private final AtomicBoolean mainPending = new AtomicBoolean();

    private void runMainTask(long myGen) {
            ModelTask task = this;
            MAIN_TASK_MAP.put(task, Thread.currentThread());
            // 绑定本代令牌：本线程的 sleep / RPC 入口据此感知作废
            RunGeneration prevGen = RunGeneration.bind(myGen, () -> task.generation);
            // 静音计数分层：记下上层残留，本轮只统计自己这段
            int droppedPrev = Log.takeDroppedStaleLogCount();
            // 与执行槽配对：只有真正拿到槽的线程才计入 runningCount
            boolean isFirst = NotificationUtil.getRunningCount() == 0;
            NotificationUtil.trackTaskStart();
            if (isFirst) {
                NotificationUtil.setStatusTextExec();
            }
            Log.record("执行开始-" + task.getName());
            Log.startModuleLogCount();
            try {
                if (RunGeneration.isStale()) throw new TaskCancelledException();
                task.run();
            } catch (TaskCancelledException e) {
                Log.record(task.getName() + "⏹代际已作废，本轮提前结束");
            } catch (Exception e) {
                Log.printStackTrace(e);
            } finally {
                // 作废期间被静音的错误日志条数一并报告，避免真故障无声丢失
                int dropped = Log.takeDroppedStaleLogCount();
                if (dropped > 0) {
                    Log.record(task.getName() + "⏹代际已作废，其间静音错误日志 " + dropped + " 条");
                }
                Log.restoreDroppedStaleLogCount(droppedPrev);
                // 还原而非清空：isSync 会在同一线程嵌套起跑，清掉会抹掉外层令牌
                RunGeneration.restore(prevGen);
                // 本轮模块未产生任何动作时，在运行日志中给出提示
                if (Log.stopModuleLogCount() == 0) {
                    Log.record(task.getName() + "✅本轮无操作");
                }
                Log.record("执行结束-" + task.getName());
                // 身份化移除：只删本线程登记的那条，避免旧代收尾误删新一代条目
                MAIN_TASK_MAP.remove(task, Thread.currentThread());
                task.running.set(false);
                NotificationUtil.trackTaskEnd();
                if (NotificationUtil.getRunningCount() == 0) {
                    NotificationUtil.updateLastExecText();
                }
            }
    }

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
        childTask.modelTask = this;
        // 提交放锁外：池满时 CallerRunsPolicy 会让提交线程就地跑完整段子任务，放进 compute 会卡住整桶
        if (!childTaskExecutor.addChildTask(childTask)) {
            return false;
        }
        ChildModelTask oldTask = childTaskMap.put(childId, childTask);
        if (oldTask != null) {
            oldTask.cancel();
        }
        return true;
    }

    public void removeChildTask(String childId) {
        ChildModelTask childTask = childTaskMap.remove(childId);
        if (childTask != null) {
            // 通知执行器放锁外，别在 CHM 桶锁内调用可能阻塞的代码
            childTaskExecutor.removeChildTask(childTask);
        }
    }

    public Integer countChildTask() {
        return childTaskMap.size();
    }

    public Boolean startTask() {
        return startTask(false);
    }

    /**
     * 启动一轮。不加 {@code synchronized}：互斥由 {@code running} 执行槽（CAS）保证，被挡住的只是执行
     * 线程自己，不像实例锁那样把整轮时间压给 {@link #stopTask()} 的调用方（宿主 onDestroy 在主线程）。
     * <p>抢槽在本方法内完成，故返回值如实反映「是否真的开始跑」：抢不到返回 false。
     */
    public Boolean startTask(Boolean force) {
        if (force) {
            stopTask();
        }
        TaskLifecycle.Work work = TaskLifecycle.enter();
        if (work == null) return false;
        boolean synchronous;
        try {
            if (!isEnable() || !check()) {
                work.close();
                return false;
            }
            synchronous = Boolean.TRUE.equals(isSync());
        } catch (Exception e) {
            Log.printStackTrace(e);
            work.close();
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            Log.record(getName() + "⏭本实例上一代未退出，本轮跳过");
            work.close();
            return false;
        }
        mainPending.set(true);
        long admittedGeneration = generation;
        Runnable admitted = () -> {
            try {
                if (generation == admittedGeneration) runMainTask(admittedGeneration);
                else running.set(false);
            }
            finally {
                mainPending.set(false);
                work.close();
            }
        };
        try {
            if (synchronous) {
                admitted.run();
            } else {
                try {
                    MAIN_THREAD_POOL.execute(admitted);
                } catch (Throwable t) {
                    // 没能提交就要归还槽，否则该模型再也起不来
                    running.set(false);
                    mainPending.set(false);
                    work.close();
                    throw t;
                }
            }
            return true;
        } catch (Exception e) {
            Log.printStackTrace(e);
            return false;
        }
    }

    public synchronized void stopTask() {
        // 递增代际：旧代在下一个检查点自行退出，无需 join 阻塞调用方。
        // 令牌只覆盖模型主线程；子任务线程未绑定，其取消仍依赖 cancel() 与 clearAllChildTask()
        generation++;
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
        // 身份化移除：只删自己登记的那条线程，避免误删新一代条目
        Thread running = MAIN_TASK_MAP.get(this);
        if (running != null) {
            running.interrupt();
            MAIN_TASK_MAP.remove(this, running);
        }
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
            // 已停止就不再启动后续模型，否则 MAIN_TASK 会把整轮跑完
            if (RunGeneration.isStale()) {
                Log.record("⏹已停止，本轮不再启动后续任务");
                return;
            }
            if (model != null) {
                if (ModelType.TASK == model.getType()) {
                    if (((ModelTask) model).startTask(force)) {
                        // 带代际检查的睡眠：停止时在下一个边界结束整轮（未绑定时同 Thread.sleep）
                        try {
                            TimeUtil.sleep(750);
                        } catch (TaskCancelledException e) {
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
