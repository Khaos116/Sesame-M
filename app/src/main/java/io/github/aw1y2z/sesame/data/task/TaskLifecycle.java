package io.github.aw1y2z.sesame.data.task;

/** Atomic admission shared by dispatchers, workers and account initialization. */
public final class TaskLifecycle {
    private static long generation;
    private static int active;
    private static Freeze freeze;

    private TaskLifecycle() { }

    public static final class Freeze {
        private Freeze() { }
    }

    public static final class Work implements AutoCloseable {
        private boolean closed;

        private Work() { active++; }

        @Override
        public void close() {
            synchronized (TaskLifecycle.class) {
                if (!closed) {
                    closed = true;
                    active--;
                }
            }
        }
    }

    public static synchronized long generation() { return generation; }

    public static synchronized boolean isOpen() { return freeze == null; }

    public static synchronized boolean isIdle() { return active == 0; }

    public static synchronized Work enter() { return enter(generation); }

    public static synchronized Work enter(long expectedGeneration) {
        return freeze == null && expectedGeneration == generation ? new Work() : null;
    }

    public static synchronized Work enterInitialization(Freeze owner) {
        if (owner == null) return enter();
        return freeze == owner ? new Work() : null;
    }

    public static synchronized Freeze freezeIfIdle() {
        if (freeze != null || active != 0) return null;
        generation++; // Delayed callbacks and children from the old account expire here.
        return freeze = new Freeze();
    }

    public static synchronized void thaw(Freeze owner) {
        if (owner != null && freeze == owner && active == 0) freeze = null;
    }
}
