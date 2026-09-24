package io.github.aw1y2z.sesame.util;

/**
 * 代际作废时从检查点（{@link TimeUtil#sleep}、RPC 桥入口）抛出，由 {@code ModelTask.mainRunnable} 收尾。
 * <p>用异常而非返回值：检查点散落在成百上千处调用点，无法逐一改造。
 */
public class TaskCancelledException extends RuntimeException {

    public TaskCancelledException() {
        super("任务代际已作废");
    }
}
