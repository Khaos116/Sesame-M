package io.github.aw1y2z.sesame.data.task;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountLifecycleCheck {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void await(CountDownLatch latch) {
        boolean interrupted = false;
        for (;;) {
            try {
                require(latch.await(5, TimeUnit.SECONDS), "latch timed out");
                break;
            } catch (InterruptedException ignored) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private static void idle() throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!TaskLifecycle.isIdle() && System.nanoTime() < until) Thread.yield();
        require(TaskLifecycle.isIdle(), "work did not finish");
    }

    private static ModelTask model(Runnable run) {
        return new ModelTask() {
            public String getName() { return "check"; }
            public ModelFields getFields() { return null; }
            public Boolean check() { return true; }
            public void run() { run.run(); }
        };
    }

    public static void main(String[] args) throws Exception {
        try {
            CountDownLatch checking = new CountDownLatch(1), allowStart = new CountDownLatch(1);
            BaseTask dispatcher = new BaseTask() {
                public Boolean check() { checking.countDown(); await(allowStart); return true; }
                public void run() { }
            };
            Thread submit = new Thread(dispatcher::startTask);
            submit.start();
            await(checking);
            require(TaskLifecycle.freezeIfIdle() == null, "queued MAIN_TASK escaped admission");
            allowStart.countDown();
            submit.join();
            dispatcher.getThread().join();

            CountDownLatch secondCheck = new CountDownLatch(1), allowSecond = new CountDownLatch(1);
            AtomicInteger ran = new AtomicInteger();
            Model.models = new Model[] { model(ran::incrementAndGet), new ModelTask() {
                public String getName() { return "second"; }
                public ModelFields getFields() { return null; }
                public Boolean check() { secondCheck.countDown(); await(allowSecond); return true; }
                public void run() { ran.incrementAndGet(); }
            }};
            Thread dispatch = new Thread(ModelTask::startAllTask);
            dispatch.start();
            await(secondCheck);
            require(!ModelTask.isAllTaskIdle(), "dispatcher disappeared between model runs");
            require(TaskLifecycle.freezeIfIdle() == null, "switch raced running dispatcher");
            allowSecond.countDown();
            dispatch.join();
            idle();
            require(ran.get() == 2, "dispatch did not complete");

            CountDownLatch running = new CountDownLatch(1), finish = new CountDownLatch(1);
            ModelTask worker = model(() -> { running.countDown(); await(finish); });
            require(worker.startTask(), "worker start failed");
            await(running);
            worker.stopTask();
            require(TaskLifecycle.freezeIfIdle() == null, "cancel released a live worker");
            require(!worker.startTask(true), "force started a duplicate live worker");
            finish.countDown();
            idle();

            CountDownLatch childRunning = new CountDownLatch(1), finishChild = new CountDownLatch(1);
            ModelTask.ChildModelTask child = new ModelTask.ChildModelTask("active", () -> {
                childRunning.countDown(); await(finishChild);
            });
            Thread childThread = new Thread(child);
            childThread.start();
            await(childRunning);
            child.cancel();
            require(TaskLifecycle.freezeIfIdle() == null, "cancel released a live child");
            finishChild.countDown();
            childThread.join();

            AtomicInteger staleRan = new AtomicInteger();
            ModelTask.ChildModelTask pending = new ModelTask.ChildModelTask("pending", staleRan::incrementAndGet);
            long queuedGeneration = TaskLifecycle.generation();
            TaskLifecycle.Freeze freeze = TaskLifecycle.freezeIfIdle();
            require(freeze != null, "pending child prevented switch");
            require(TaskLifecycle.enter(queuedGeneration) == null, "queued dispatch entered freeze");
            require(TaskLifecycle.enterInitialization(null) == null, "external initialization entered freeze");
            require(!dispatcher.startTask(), "MAIN_TASK entered freeze");
            require(!worker.startTask(), "model entered freeze");
            pending.run();
            try (TaskLifecycle.Work init = TaskLifecycle.enterInitialization(freeze)) {
                require(init != null, "controller initialization rejected");
                TaskLifecycle.thaw(freeze);
                require(!TaskLifecycle.isOpen(), "resumed before initialization finished");
                require(TaskLifecycle.enter() == null, "initialization admitted ordinary work");
            }
            require(!TaskLifecycle.isOpen(), "failed initialization resumed implicitly");
            TaskLifecycle.thaw(freeze);
            require(TaskLifecycle.isOpen(), "successful initialization could not resume");
            require(TaskLifecycle.enter(queuedGeneration) == null, "old callback revived after resume");
            pending.run();
            require(staleRan.get() == 0, "old pending child ran on new account");
            require(worker.startTask(), "new dispatch did not resume");
            idle();
            System.out.println("PASS: dispatch admission, live cancellation, pending children, frozen initialization and resume");
        } finally {
            java.lang.reflect.Field pool = ModelTask.class.getDeclaredField("MAIN_THREAD_POOL");
            pool.setAccessible(true);
            ((ThreadPoolExecutor) pool.get(null)).shutdownNow();
        }
    }
}
