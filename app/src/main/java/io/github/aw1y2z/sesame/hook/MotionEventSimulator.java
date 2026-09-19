package io.github.aw1y2z.sesame.hook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import io.github.aw1y2z.sesame.util.RandomUtil;

/**
 * 模拟系统级 MotionEvent 的工具类.
 * 用于执行如滑动等自动化操作.
 */
public class MotionEventSimulator {
    private static final String TAG = "MotionEventSimulator";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    
    /**
     * 异步模拟一个从起点到终点的滑动操作.
     *
     * <p>手势在主线程派发，但**全程不 sleep 主线程**：按下后立即返回，后续每一步移动与抬起都用
     * {@code postDelayed} 排队。原实现是在主线程的同一个 Runnable 里边睡边发（按下后 30~80ms
     * + 15 步 × 约 30ms），一次滑动就占住主线程 0.5~0.8s；验证码失败时调用方还会重试十来次，
     * 叠加起来足以让宿主界面无响应。延迟排队的效果与真实人手一致，且不阻塞。
     *
     * @param view     要在其上执行滑动操作的视图 (通常是滑块本身).
     * @param startX   滑动的屏幕绝对 X 坐标起点.
     * @param startY   滑动的屏幕绝对 Y 坐标起点.
     * @param endX     滑动的屏幕绝对 X 坐标终点.
     * @param endY     滑动的屏幕绝对 Y 坐标终点.
     * @param duration 滑动动画的总时长 (毫秒).
     */
    public static void simulateSwipe(View view, float startX, float startY, float endX, float endY, long duration) {
        // 确保所有UI操作都在主线程执行（替换协程，适配项目纯Java风格）
        MAIN_HANDLER.post(() -> startSwipe(view, startX, startY, endX, endY, duration));
    }
    
    /** 主线程：派发"按下"，并把后续移动/抬起用 postDelayed 排好（不阻塞主线程） */
    private static void startSwipe(View view, float startX, float startY, float endX, float endY, long duration) {
        Log.i(TAG, "准备在视图 " + view.getClass().getSimpleName() + " 上模拟滑动");
        Log.d(TAG, "从 (" + startX + ", " + startY + ") -> (" + endX + ", " + endY + ")，持续时间: " + duration + "ms");
        
        if (!view.isShown() || !view.isEnabled()) {
            Log.e(TAG, "滑动失败: 目标视图不可见或未启用.");
            return;
        }
        
        // 1. 发送 ACTION_DOWN 事件，标志着手指按下
        final long downTime = SystemClock.uptimeMillis();
        dispatchTouchEvent(view, MotionEvent.ACTION_DOWN, startX, startY, downTime, downTime);
        
        // 2. 模拟 ACTION_MOVE 事件序列，构造滑动轨迹
        final int steps = 15; // 将滑动轨迹分为 15 步
        final long pressDelay = RandomUtil.nextLong(30, 80); // 按下后短暂延迟，更像人操作
        final long stepDuration = Math.max(1L, (duration - 100) / steps);
        final float xStep = (endX - startX) / steps;
        final float yStep = (endY - startY) / steps;
        
        for (int i = 1; i <= steps; i++) {
            final int step = i;
            MAIN_HANDLER.postDelayed(() -> {
                float currentX = startX + xStep * step + RandomUtil.nextInt(-3, 4); // 增加微小随机抖动
                float currentY = startY + yStep * step + RandomUtil.nextInt(-2, 3);
                dispatchTouchEvent(view, MotionEvent.ACTION_MOVE, currentX, currentY, downTime,
                        downTime + stepDuration * step);
            }, pressDelay + stepDuration * (step - 1));
        }
        
        // 3. 发送 ACTION_UP 事件，标志着手指抬起（无论中间步骤如何，抬起必须发出去，
        //    否则宿主会以为手指一直按着）
        MAIN_HANDLER.postDelayed(() -> {
            dispatchTouchEvent(view, MotionEvent.ACTION_UP, endX, endY, downTime, downTime + duration);
            Log.i(TAG, "模拟滑动事件序列发送完毕.");
        }, pressDelay + stepDuration * steps);
    }
    
    /**
     * 重载方法，使用默认滑动时长
     */
    public static void simulateSwipe(View view, float startX, float startY, float endX, float endY) {
        simulateSwipe(view, startX, startY, endX, endY, 800L);
    }
    
    /**
     * 辅助函数，用于创建和派发 MotionEvent.
     * <p>异常一律在这里吞掉并留痕：本函数跑在主线程的消息队列里，抛出去会直接崩掉宿主进程；
     * 而且单步失败也不能影响后续步骤与最终的"抬起"。
     */
    private static void dispatchTouchEvent(View view, int action, float x, float y, long downTime, long eventTime) {
        MotionEvent event = null;
        try {
            MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
            MotionEvent.PointerProperties pointerProps = new MotionEvent.PointerProperties();
            pointerProps.id = 0;
            pointerProps.toolType = MotionEvent.TOOL_TYPE_FINGER;
            properties[0] = pointerProps;
            
            MotionEvent.PointerCoords[] cords = new MotionEvent.PointerCoords[1];
            MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
            pointerCoords.x = x;
            pointerCoords.y = y;
            pointerCoords.pressure = 1f;
            pointerCoords.size = 1f;
            cords[0] = pointerCoords;
            
            event = MotionEvent.obtain(
                    downTime,
                    eventTime,
                    action,
                    1,
                    properties,
                    cords,
                    0,
                    0,
                    1f,
                    1f,
                    0,
                    0,
                    0,
                    0
            );
            
            view.dispatchTouchEvent(event);
        } catch (Throwable e) {
            Log.e(TAG, "派发触摸事件时发生异常");
        } finally {
            if (event != null) {
                event.recycle(); // 回收事件，避免内存泄漏
            }
        }
    }
}