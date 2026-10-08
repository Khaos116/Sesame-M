package io.github.aw1y2z.sesame.data.task;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ChildTaskCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Worker extends ThreadPoolExecutor {
        final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        boolean inline, reject;
        Worker() { super(1, 1, 1, TimeUnit.SECONDS, new SynchronousQueue<>()); }
        @Override public void execute(Runnable command) {
            if (reject) throw new java.util.concurrent.RejectedExecutionException();
            if (inline) command.run(); else pending.add(command);
        }
        void next() { pending.remove().run(); Thread.interrupted(); }
    }

    @SuppressWarnings("unchecked")
    private static void check(ChildTaskExecutor executor) throws Exception {
        ModelTask task = new ModelTask() {
            public String getName() { return "child check"; }
            public ModelFields getFields() { return null; }
            public Boolean check() { return false; }
            public void run() { }
        };
        Field owner = ModelTask.class.getDeclaredField("childTaskExecutor");
        owner.setAccessible(true);
        owner.set(task, executor);
        Field pools = executor.getClass().getDeclaredField("groupChildTaskExecutorMap");
        pools.setAccessible(true);
        Worker worker = new Worker();
        ((Map<String, ThreadPoolExecutor>) pools.get(executor)).put("check", worker);
        try {
            worker.inline = true;
            task.addChildTask(new ModelTask.ChildModelTask("instant", "check", () -> { }));
            Thread.interrupted();
            require(!task.hasChildTask("instant"), "completed inline child remained registered");

            worker.inline = false;
            var replacement = new ModelTask.ChildModelTask("repeat", "check", () -> { });
            task.addChildTask(new ModelTask.ChildModelTask("repeat", "check", () -> task.addChildTask(replacement)));
            worker.next();
            require(task.getChildTask("repeat") == replacement && !replacement.getIsCancel(),
                    "old child completion removed its replacement");
            worker.next();
            require(!task.hasChildTask("repeat"), "replacement completion remained registered");

            worker.reject = true;
            try {
                task.addChildTask(new ModelTask.ChildModelTask("rejected", "check", () -> { }));
                throw new AssertionError("rejected submission unexpectedly succeeded");
            } catch (java.util.concurrent.RejectedExecutionException expected) { }
            require(task.countChildTask() == 0, "failed submission remained registered");
        } finally {
            task.stopTask();
            worker.shutdownNow();
            Thread.interrupted();
        }
    }

    public static void main(String[] args) throws Exception {
        check(new ProgramChildTaskExecutor());
        check(new SystemChildTaskExecutor());
        System.out.println("PASS: both real child executors preserve replacements and remove inline/rejected tasks");
    }
}
