package io.github.aw1y2z.sesame.data.task;

import java.util.concurrent.ThreadPoolExecutor;

public class TaskCompletionCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static Thread watcher() throws Exception {
        var field = ModelTask.class.getDeclaredField("completionWatcher");
        field.setAccessible(true);
        synchronized (TaskLifecycle.class) { return (Thread) field.get(null); }
    }

    private static void finished(Thread thread) throws Exception {
        require(thread != null, "completion watcher missing");
        thread.join(3000);
        require(!thread.isAlive(), "completion watcher leaked");
    }

    public static void main(String[] args) throws Exception {
        try {
            ModelTask task = new ModelTask() {
                public String getName() { return "completion check"; }
                public ModelFields getFields() { return null; }
                public Boolean check() { return false; }
                public void run() { }
            };
            task.prepare();
            Model.models = new Model[] { task };
            var delayed = new ModelTask.ChildModelTask("delayed", () -> { });
            task.addChildTask(delayed);
            ModelTask.startAllTask();
            Thread first = watcher();
            Thread.sleep(650);
            require(Log.completions.get() == 0, "pending child was reported complete");
            ModelTask.startAllTask();
            require(watcher() == first, "overlapping dispatch created another watcher");
            // A running child may already have been removed/cancelled in its executor.
            try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
                task.removeChildTask("delayed");
                Thread.sleep(650);
                require(Log.completions.get() == 0, "running child was reported complete");
            }
            finished(first);
            require(Log.completions.get() == 1, "completion must be emitted exactly once");

            try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
                ModelTask.startAllTask();
                first = watcher();
            }
            finished(first);
            require(Log.completions.get() == 2, "next completed round was suppressed");

            task.addChildTask(new ModelTask.ChildModelTask("cancelled", () -> { }));
            ModelTask.startAllTask();
            first = watcher();
            ModelTask.stopAllTask();
            finished(first);
            require(Log.completions.get() == 2, "stopping tasks emitted success");

            task.addChildTask(new ModelTask.ChildModelTask("old-account", () -> { }));
            ModelTask.startAllTask();
            first = watcher();
            TaskLifecycle.Freeze freeze = TaskLifecycle.freezeIfIdle();
            require(freeze != null, "pending child blocked account switching");
            TaskLifecycle.thaw(freeze);
            finished(first);
            require(Log.completions.get() == 2, "old account emitted completion");
            ModelTask.stopAllTask();
            System.out.println("PASS: delayed/running children, one completion per overlapping round, stop and account switch");
        } finally {
            ModelTask.stopAllTask();
            var pool = ModelTask.class.getDeclaredField("MAIN_THREAD_POOL");
            pool.setAccessible(true);
            ((ThreadPoolExecutor) pool.get(null)).shutdownNow();
        }
    }
}
