package io.github.aw1y2z.sesame.model.extensions;

import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.TaskCancelledException;

public class TestRpc {
    private static final String TAG = TestRpc.class.getSimpleName();

    public static void start(String broadcastFun, String broadcastData, String testType) {
        long generation = TaskLifecycle.generation();
        new Thread() {
            String broadcastFun;
            String broadcastData;
            String testType;

            public Thread setData(String fun, String data, String type) {
                broadcastFun = fun;
                broadcastData = data;
                testType = type;
                return this;
            }

            @Override
            public void run() {
                // 裸线程抛出的异常无人接，会走到宿主默认处理器直接杀进程
                try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                    if (work == null) return;
                    ExtensionsHandle.handleRequest(testType, broadcastFun, broadcastData);
                } catch (TaskCancelledException ignored) {
                } catch (Throwable th) {
                    io.github.aw1y2z.sesame.util.Log.printStackTrace(TAG, th);
                }
            }
        }.setData(broadcastFun, broadcastData, testType).start();
    }
}
