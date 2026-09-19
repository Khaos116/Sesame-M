package io.github.aw1y2z.sesame.util.compat;

/**
 * API 102 临时迁移兼容层：模拟旧 Xposed {@code XC_MethodHook} 的 before/after 模型。
 * 由 {@link io.github.aw1y2z.sesame.util.XHelpers} 桥接到 libxposed 的 Hook 链。
 */
public abstract class XC_MethodHook {

    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object result = null;
        public boolean hasResult = false;
        public Throwable exception = null;

        /** 调用原方法的能力，由 {@link io.github.aw1y2z.sesame.util.XHelpers} 在挂钩时注入 */
        public interface OriginalCall {
            Object invoke() throws Throwable;
        }

        public OriginalCall originalCall;

        /**
         * 调用原方法并返回真实结果。
         * <p>给"多数时候要改写返回值、少数时候需要真相"的 hook 用：例如宿主询问前后台状态时，
         * 平时要谎报（后台任务依赖它），但风控/滑块链路询问时必须如实回答——真相只能问宿主自己。
         * <p>仅在由 {@link io.github.aw1y2z.sesame.util.XHelpers} 挂上的 hook 内可用。
         */
        public Object callOriginal() throws Throwable {
            if (originalCall == null) {
                throw new IllegalStateException("callOriginal() 仅在 XHelpers 挂载的 hook 内可用");
            }
            return originalCall.invoke();
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
            this.hasResult = true;
        }

        public boolean hasThrowable() {
            return exception != null;
        }

        public Throwable getThrowable() {
            return exception;
        }

        public void setThrowable(Throwable throwable) {
            this.exception = throwable;
        }
    }

    public interface Unhook {
        void unhook();
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public final void callBefore(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    public final void callAfter(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }
}
