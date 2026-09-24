package io.github.aw1y2z.sesame.data.task;

import lombok.Getter;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.TaskCancelledException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseTask {

    @Getter
    private volatile Thread thread;

    /** 代际：stopTask 递增；正在跑的这一代在下一个检查点（sleep / RPC 桥）自行结束 */
    private volatile long generation = 0;

    /** 执行槽（只限本实例；重载后是新对象，跨实例靠代际令牌在检查点退出）：抢不到即放弃，由下一次调度重试 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Map<String, BaseTask> childTaskMap = new ConcurrentHashMap<>();

    public BaseTask() {
        this.thread = null;
    }

    public String getId() {
        return toString();
    }

    public abstract Boolean check();

    public abstract void run();

    public synchronized Boolean hasChildTask(String childId) {
        return childTaskMap.containsKey(childId);
    }

    public synchronized BaseTask getChildTask(String childId) {
        return childTaskMap.get(childId);
    }

    public synchronized void addChildTask(BaseTask childTask) {
        String childId = childTask.getId();
        BaseTask oldTask = childTaskMap.put(childId, childTask);
        if (oldTask != null) {
            oldTask.stopTask();
        }
        childTask.startTask();
    }

    public synchronized void removeChildTask(String childId) {
        BaseTask oldTask = childTaskMap.remove(childId);
        if (oldTask != null) {
            // 与 stopTask 一致：只发信号，不在这里等（等待会卡住持锁者）
            oldTask.stopTask();
        }
    }

    public synchronized Integer countChildTask() {
        return childTaskMap.size();
    }

    public Boolean startTask() {
        return startTask(false);
    }

    public synchronized Boolean startTask(Boolean force) {
        if (force) {
            stopTask();
            if (thread != null && thread.isAlive()) return false;
        }
        TaskLifecycle.Work work = TaskLifecycle.enter();
        if (work == null) return false;
        // 抢执行槽：旧代没退出就放弃本轮，交给下一次调度，而不是在锁里等它跑完
        if (!running.compareAndSet(false, true)) {
            Log.record(getId() + "⏭本实例上一代未退出，本轮跳过");
            work.close();
            return false;
        }
        try {
            if (!check()) {
                running.set(false);
                work.close();
                return false;
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
            running.set(false);
            work.close();
            return false;
        }
        // 起跑前快照代际：stopTask 递增后，本代在下一个检查点作废
        long myGen = generation;
        thread = new Thread(() -> {
            RunGeneration prev = RunGeneration.bind(myGen, () -> generation);
            try {
                run();
            } catch (TaskCancelledException e) {
                // 作废属正常收尾；逃出线程会被系统当成未捕获异常上报
            } finally {
                RunGeneration.restore(prev);
                running.set(false);
                work.close();
            }
        });
        try {
            thread.start();
        } catch (Throwable t) {
            // 线程起不来（如 OOM: Thread too many）也要归还槽，否则该任务永久停摆
            running.set(false);
            work.close();
            throw t;
        }
        for (BaseTask childTask : childTaskMap.values()) {
            if (childTask != null) {
                childTask.startTask();
            }
        }
        return true;
    }

    public synchronized void stopTask() {
        // 递增代际即可让旧代在下一个检查点退出；不 join，避免把等待压给调用方（宿主 onDestroy 在主线程）
        generation++;
        Thread old = thread;
        if (old != null) {
            old.interrupt();
        }
        for (BaseTask childTask : childTaskMap.values()) {
            if (childTask != null) {
                childTask.stopTask();
            }
        }
        if (thread == null || !thread.isAlive()) thread = null;
        childTaskMap.clear();
    }

    public static BaseTask newInstance() {
        return new BaseTask() {
            @Override
            public void run() {
            }

            @Override
            public Boolean check() {
                return true;
            }
        };
    }

    public static BaseTask newInstance(String id) {
        return new BaseTask() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public void run() {
            }

            @Override
            public Boolean check() {
                return true;
            }
        };
    }

    public static BaseTask newInstance(String id, Runnable runnable) {
        return new BaseTask() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public void run() {
                runnable.run();
            }

            @Override
            public Boolean check() {
                return true;
            }
        };
    }

}
