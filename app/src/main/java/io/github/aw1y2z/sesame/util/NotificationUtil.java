package io.github.aw1y2z.sesame.util;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;

public class NotificationUtil {
    private static Context context;
    private static final int NOTIFICATION_ID = 99;
    private static final String CHANNEL_ID = "io.github.aw1y2z.sesame.ANTFOREST_NOTIFY_CHANNEL";
    private static NotificationManager mNotifyManager;
    private static Notification.Builder builder;

    @Getter
    private static volatile long lastNoticeTime = 0;
    private static String titleText = "Sesame-M";
    private static String contentText = "";
    /** 活跃任务计数，>0 表示有异步任务仍在执行。由 ModelTask 拿到执行槽后 / finally 里增减 */
    private static final AtomicInteger runningCount = new AtomicInteger(0);

    public static void trackTaskStart() {
        runningCount.incrementAndGet();
    }

    public static void trackTaskEnd() {
        // 不再有实例锁串行化，增减来自不同线程 ⇒ 原子操作
        if (runningCount.decrementAndGet() < 0) {
            runningCount.set(0);
        }
    }

    public static int getRunningCount() {
        return runningCount.get();
    }

    public static void start(Context context) {
        try {
            NotificationUtil.context = context;
            NotificationUtil.stop();
            titleText = "Sesame-M";
            contentText = "启动中";
            mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setData(Uri.parse("alipays://platformapi/startapp?appId="));
            PendingIntent pi = PendingIntent.getActivity(context, 0, it,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, "芝麻粒能量提醒",
                        NotificationManager.IMPORTANCE_LOW);
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationChannel.setShowBadge(false);
                mNotifyManager.createNotificationChannel(notificationChannel);
                builder = new Notification.Builder(context, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(context).setPriority(Notification.PRIORITY_LOW);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setCategory(Notification.CATEGORY_NAVIGATION);
            }
            builder
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.sym_def_app_icon))
                    .setSubText("芝麻粒")
                    .setAutoCancel(false)
                    .setContentIntent(pi);
            if (io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getEnableOnGoing()) {
                builder.setOngoing(true);
            }
            Notification mNotification = builder.build();
            if (context instanceof Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    mNotifyManager.notify(NOTIFICATION_ID, mNotification);
                } else {
                    ((Service) context).startForeground(NOTIFICATION_ID, mNotification);
                }
            } else {
                mNotifyManager.notify(NOTIFICATION_ID, mNotification);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void stop() {
        try {
            if (context instanceof Service) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ((Service) context).stopForeground(Service.STOP_FOREGROUND_REMOVE);
                } else {
                    ((Service) context).stopForeground(true);
                }
            } else {
                if (mNotifyManager != null) {
                    mNotifyManager.cancel(NOTIFICATION_ID);
                } else if (context != null) {
                    NotificationManager systemService = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (systemService != null) {
                        systemService.cancel(NOTIFICATION_ID);
                    }
                }
            }
            mNotifyManager = null;
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void updateStatusText(String status) {
        try {
            long forestPauseTime = RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime);
            if (forestPauseTime > System.currentTimeMillis()) {
                status = "触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime);
            }
            contentText = status;
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 下次执行时间，由 execDelayedHandler 设置，updateLastExecText 时一并写入 */
    private static volatile long nextExecTime = 0;

    public static void setNextExecTime(long nextExecTime) {
        NotificationUtil.nextExecTime = nextExecTime;
    }

    /**
     * 所有任务完成时调用：更新「上次执行」时间，并写入「下次执行」时间。
     * 由 ModelTask.finally 中 runningCount == 0 时统一触发。
     */
    public static void updateLastExecText() {
        try {
            long now = System.currentTimeMillis();
            String lastPart = "上次执行  " + TimeUtil.getTimeStr(now);
            if (nextExecTime > 0) {
                lastPart += "  下次执行 " + TimeUtil.getTimeStr(nextExecTime);
                nextExecTime = 0;
            }
            contentText = lastPart;
            lastNoticeTime = now;
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void setStatusTextExec() {
        try {
            contentText = "自动执行中";
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /**
     * 任务运行中持续刷新通知，防止系统因「长时间无更新」将通知折叠/隐藏。
     * 每次调用只更新 lastNoticeTime，不改变文本内容。
     */
    public static void setRunning() {
        try {
            lastNoticeTime = System.currentTimeMillis();
            sendText();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private static void sendText() {
        try {
            builder.setContentTitle(titleText);
            if (!StringUtil.isEmpty(contentText)) {
                builder.setContentText(contentText);
            }
            mNotifyManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

}
