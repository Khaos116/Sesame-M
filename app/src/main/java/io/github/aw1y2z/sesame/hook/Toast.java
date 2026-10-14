package io.github.aw1y2z.sesame.hook;

import android.content.Context;
import android.os.Handler;

import io.github.aw1y2z.sesame.util.Log;

public class Toast {
    private static final String TAG = Toast.class.getSimpleName();

    public static void show(CharSequence cs) {
        show(cs, false);
    }

    public static void show(CharSequence cs, boolean force) {
        Context context = ApplicationHook.getContext();
        // mainHandler 只在 Service onCreate hook 内赋值：未就绪时安静跳过，不让 NPE 冒泡成 error 日志
        Handler handler = ApplicationHook.getMainHandler();
        if (context != null && handler != null && (force || io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getShowToast())) {
            show(context, handler, cs);
        }
    }

    public static void show(Context context, Handler handler, CharSequence cs) {
        try {
            handler.post(() -> {
                try {
                    android.widget.Toast toast = android.widget.Toast.makeText(context, cs, android.widget.Toast.LENGTH_SHORT);
                    toast.setGravity(toast.getGravity(), toast.getXOffset(), io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getToastOffsetY());
                    toast.show();
                } catch (Throwable t) {
                    Log.err(TAG, "show.run err:", t);
                }
            });
        } catch (Throwable t) {
            Log.err(TAG, "show err:", t);
        }
    }
}
