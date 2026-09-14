package io.github.aw1y2z.sesame.hook;

import java.util.Locale;

/**
 * 只按当前顶层 Activity 类名判断：登录页/身份验证页/验证码页不允许自动切号，
 * 避免在这些页面上再叠一次登录操作。移植自 GR 分支，见 doc/MyFix.md。
 */
final class AccountSwitchPagePolicy {
    private AccountSwitchPagePolicy() { }

    static boolean blocksClass(String activityClass, String launcherClass) {
        if (activityClass == null || activityClass.isEmpty()) return false;
        // 支付宝已登录首页类名是 com.eg.android.AlipayGphone.AlipayLogin，
        // 单纯按"login"子串匹配会把首页也拦住，必须先排除。
        if (activityClass.equals(launcherClass)) return false;
        String name = activityClass.toLowerCase(Locale.ROOT);
        return name.contains("login") || name.contains("verifyidentity") || name.contains("captcha");
    }
}
