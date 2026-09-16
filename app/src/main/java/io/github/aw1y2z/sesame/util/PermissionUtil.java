package io.github.aw1y2z.sesame.util;

import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;

public class PermissionUtil {
    private static final String TAG = AntForestRpcCall.class.getSimpleName();

    private static final int REQUEST_EXTERNAL_STORAGE = 1;

    private static final String[] PERMISSIONS_STORAGE = {
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
    };

    public static Boolean checkOrRequestAllPermissions(AppCompatActivity activity) {
        return checkOrRequestFilePermissions(activity) && checkOrRequestAlarmPermissions(activity);
    }

    public static boolean checkFilePermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //判断是否有管理外部存储的权限
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : PERMISSIONS_STORAGE) {
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        } else {
            return true;
        }
    }

    public static Boolean checkOrRequestFilePermissions(AppCompatActivity activity) {
        try {
            if (checkFilePermissions(activity)) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appIntent.setData(Uri.parse("package:" + activity.getPackageName()));
                //appIntent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                try {
                    activity.startActivity(appIntent);
                } catch (ActivityNotFoundException ex) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    activity.startActivity(intent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    public static boolean checkAlarmPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //判断是否有使用闹钟的权限
            AlarmManager systemService = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (systemService != null) {
                return systemService.canScheduleExactAlarms();
            }
            return true;
        }
        return true;
    }

    public static Boolean checkOrRequestAlarmPermissions(Context context) {
        try {
            if (checkAlarmPermissions()) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                //appIntent.setData(Uri.fromParts("package", ClassUtil.PACKAGE_NAME, null));
                try {
                    context.startActivity(appIntent);
                } catch (ActivityNotFoundException ex) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        return false;
    }

    public static boolean checkBatteryPermissions() {
        Context context;
        try {
            if (!ApplicationHook.isHooked()) {
                return false;
            }
            context = ApplicationHook.getContext();
            if (context == null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return checkBatteryPermissions(context);
    }

    /**
     * 独立 App 进程调这个重载，不碰 {@link ApplicationHook}——那个类继承 compileOnly 的
     * {@code XposedModule}，只有真正被 LSPosed 注入进支付宝进程时宿主才提供这个类；独立 App
     * 自己的进程里引用它会在类校验时抛 NoClassDefFoundError（是 Error 不是 Exception，
     * try/catch(Exception) 包不住），点一下设置页的电量权限按钮就直接闪退。
     */
    public static boolean checkBatteryPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //判断是否有始终在后台运行的权限
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(ClassUtil.PACKAGE_NAME);
            }
            return false;
        }
        return true;
    }

    public static Boolean checkOrRequestBatteryPermissions(Context context) {
        try {
            if (checkBatteryPermissions(context)) {
                return true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                //跳转到权限页，请求权限
                Intent appIntent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                appIntent.setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
                try {
                    context.startActivity(appIntent);
                } catch (Exception ex) {
                    Log.printStackTrace(TAG, ex);
                    openBatterySettings(context);
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
            openBatterySettings(context);
        }
        return false;
    }

    /** 标准申请弹窗不可用或被厂商系统静默拦截时，提供手动设置入口。 */
    public static void openBatterySettings(Context context) {
        Intent details = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + ClassUtil.PACKAGE_NAME));
        try {
            details.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(details);
            ToastUtil.show(context, "请允许支付宝后台运行或设为不优化；若未打开，请在系统设置中手动调整");
            return;
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
        }
        ToastUtil.show(context, "无法打开设置，请在系统设置中找到支付宝，允许后台运行或关闭电池优化");
    }
}
