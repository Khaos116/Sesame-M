package io.github.aw1y2z.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.BooleanSupplier;

/**
 * 拼图滑块的触摸序列：按下、按时间推进的移动、精确停在终点、抬起，全程主线程 postDelayed 排队，不阻塞。
 * 坐标传屏幕绝对值，内部按目标视图当前位置换算成视图内坐标（WebView 不在屏幕原点，直接传绝对坐标会偏）。
 * 中途窗口消失/移动/被取消会立即结束并回调 false；只有 DOWN、MOVE、UP 全部被视图接收才回调 true。
 * 移植自 GR 的 MotionEventSimulator.Swipe，去掉了它依赖的 ManualTouchMonitor（本项目没有手动触摸监视）。
 */
final class PuzzleSwipe {
    interface Completion {
        void onDone(boolean sent, String reason);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Swipe active;

    private PuzzleSwipe() {
    }

    /** 任意线程可调；实际在主线程开始。 */
    static void start(View target, float sx, float sy, float ex, float ey, long duration, float arc,
                      BooleanSupplier valid, Completion completion) {
        Runnable begin = () -> {
            if (active != null) {
                active.finish(false, "CANCELLED");
            }
            Swipe swipe = new Swipe(target, sx, sy, ex, ey, Math.max(300L, duration), arc, valid, completion);
            active = swipe;
            swipe.begin();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            begin.run();
        } else {
            MAIN.post(begin);
        }
    }

    private static final class Swipe {
        final View target;
        final float sx, sy, ex, ey, arc;
        final long duration;
        final BooleanSupplier valid;
        final Completion completion;
        final int[] origin = new int[2];
        final int[] location = new int[2];
        final Runnable next = this::move;
        final Runnable release = this::up;
        long downTime;
        long lastTime;
        boolean down;
        boolean done;

        Swipe(View target, float sx, float sy, float ex, float ey, long duration, float arc,
              BooleanSupplier valid, Completion completion) {
            this.target = target;
            this.sx = sx;
            this.sy = sy;
            this.ex = ex;
            this.ey = ey;
            this.duration = duration;
            this.arc = arc;
            this.valid = valid;
            this.completion = completion;
        }

        boolean available() {
            if (done || active != this || target == null || !target.isAttachedToWindow()
                    || !target.isShown() || !target.isEnabled() || !valid.getAsBoolean()) {
                return false;
            }
            target.getLocationOnScreen(location);
            return !down || (origin[0] == location[0] && origin[1] == location[1]);
        }

        void begin() {
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            target.getLocationOnScreen(origin);
            downTime = SystemClock.uptimeMillis();
            down = true;
            boolean accepted = send(MotionEvent.ACTION_DOWN, sx - origin[0], sy - origin[1], 0f);
            if (done) {
                return;
            }
            if (!accepted) {
                finish(false, "INPUT_REJECTED");
                return;
            }
            MAIN.postDelayed(next, 16L);
        }

        void move() {
            if (done) {
                return;
            }
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (now <= lastTime) {
                MAIN.postDelayed(next, 1L);
                return;
            }
            float progress = Math.min(1f, (now - downTime) / (float) (duration - 32L));
            // 最后一次 MOVE 必须落在精确终点：页面通常只按 MOVE 更新拖动位置，仅靠 UP 不够
            float x = sx - origin[0] + (ex - sx) * progress;
            float y = sy - origin[1] + (ey - sy) * progress;
            if (progress < 1f) {
                y += (float) Math.sin(Math.PI * progress) * arc;
            }
            if (!send(MotionEvent.ACTION_MOVE, x, y, progress)) {
                finish(false, "INPUT_REJECTED");
                return;
            }
            if (done) {
                return;
            }
            MAIN.postDelayed(progress >= 1f ? release : next, progress >= 1f ? 32L : 16L);
        }

        void up() {
            if (done) {
                return;
            }
            if (!available()) {
                finish(false, "WINDOW_UNAVAILABLE");
                return;
            }
            boolean accepted = send(MotionEvent.ACTION_UP, ex - origin[0], ey - origin[1], 1f);
            down = false;
            finish(accepted, accepted ? "INPUT_FINISHED" : "INPUT_REJECTED");
        }

        boolean send(int action, float localX, float localY, float progress) {
            long eventTime = SystemClock.uptimeMillis();
            lastTime = eventTime;
            MotionEvent.PointerProperties props = new MotionEvent.PointerProperties();
            props.id = 0;
            props.toolType = MotionEvent.TOOL_TYPE_FINGER;
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
            coords.x = localX;
            coords.y = localY;
            boolean released = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
            coords.pressure = released ? 0f : 0.65f + 0.30f * (float) Math.sin(Math.PI * progress);
            coords.size = 0.75f;
            MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, 1,
                    new MotionEvent.PointerProperties[]{props}, new MotionEvent.PointerCoords[]{coords},
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
            try {
                return target.dispatchTouchEvent(event);
            } catch (Throwable failed) {
                return false;
            } finally {
                event.recycle();
            }
        }

        void finish(boolean success, String reason) {
            if (done) {
                return;
            }
            done = true;
            MAIN.removeCallbacks(next);
            MAIN.removeCallbacks(release);
            if (down) {
                // 手指还按着就结束：补一个 CANCEL，避免宿主以为一直按着
                send(MotionEvent.ACTION_CANCEL, x(), y(), 1f);
                down = false;
            }
            if (active == this) {
                active = null;
            }
            if (completion != null) {
                completion.onDone(success, reason);
            }
        }

        float x() {
            return ex - origin[0];
        }

        float y() {
            return ey - origin[1];
        }
    }
}
