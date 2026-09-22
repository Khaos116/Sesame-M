package io.github.aw1y2z.sesame.hook;

import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.XHelpers;

/**
 * 只反射读支付宝自己的账号服务，不碰密码/凭证，不做加载期自动登录。
 * 移植自 GR 分支，把 XposedHelpers 换成 M 自己的 XHelpers 反射封装，见 docs/MyFix.md。
 */
final class HostAccountSwitchBridge {
    static final class Account {
        final String uid;
        final String loginId;
        final String label;
        Account(String uid, String loginId, String label) {
            this.uid = uid; this.loginId = loginId; this.label = label;
        }
    }

    private static Object service(String name) throws Exception {
        ClassLoader loader = ApplicationHook.getClassLoader();
        if (loader == null) throw new IllegalStateException("HOST_NOT_READY");
        Class<?> agent = Class.forName("com.alipay.mobile.framework.LauncherApplicationAgent", false, loader);
        Object instance = XHelpers.callStaticMethod(agent, "getInstance");
        Object micro = XHelpers.callMethod(instance, "getMicroApplicationContext");
        Object result = XHelpers.callMethod(micro, "findServiceByInterface",
                "com.alipay.mobile.framework.service.ext.security." + name);
        if (result == null) throw new IllegalStateException("HOST_SERVICE_MISSING");
        return result;
    }

    String currentUid() throws Exception {
        Object info = XHelpers.callMethod(service("AuthService"), "getUserInfo");
        return info == null ? null : value(XHelpers.callMethod(info, "getUserId"));
    }

    List<Account> accounts() throws Exception {
        Object result = XHelpers.callMethod(service("AccountService"), "getLoginedAlipayUser");
        if (!(result instanceof List) || ((List<?>) result).size() > 32) throw new IllegalStateException("HISTORY_UNAVAILABLE");
        Map<String, Account> unique = new LinkedHashMap<>();
        for (Object row : (List<?>) result) {
            if (row == null) continue;
            String uid = value(XHelpers.callMethod(row, "getUserId"));
            String login = value(XHelpers.callMethod(row, "getLogonId"));
            if (!AccountSwitchState.validUid(uid) || login == null || login.length() > 256) continue;
            String display = "";
            try { display = value(XHelpers.callMethod(row, "getDisplayName")); } catch (Throwable ignored) { }
            Account previous = unique.get(uid);
            if (previous != null && !previous.loginId.equals(login)) throw new IllegalStateException("AMBIGUOUS_HISTORY");
            unique.put(uid, new Account(uid, login, masked(display) + " (" + masked(login) + ")"));
        }
        // 部分支付宝版本的可切换列表不含当前账号，补一条本地已认证记录，
        // 不然三个账号会退化成在另外两个之间反复横跳。
        Object active = XHelpers.callMethod(service("AuthService"), "getUserInfo");
        if (active != null) {
            String uid = value(XHelpers.callMethod(active, "getUserId"));
            if (AccountSwitchState.validUid(uid) && !unique.containsKey(uid)) {
                String login = value(XHelpers.callMethod(active, "getLogonId"));
                if (login == null || login.length() > 256) throw new IllegalStateException("CURRENT_ACCOUNT_UNAVAILABLE");
                unique.put(uid, new Account(uid, login, "当前账号 (" + masked(login) + ")"));
            }
        }
        if (unique.size() > 32) throw new IllegalStateException("HISTORY_TOO_LARGE");
        return new ArrayList<>(unique.values());
    }

    /** 只读签名探测，切换调用前先确认能找到这个方法。 */
    static Method loginMethod(Object loginService) throws Exception {
        Method found = null;
        for (Method method : loginService.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (!"login".equals(method.getName()) || p.length != 7 || p[0] != String.class
                    || p[2] != String.class || p[5] != boolean.class || p[6] != Bundle.class
                    || p[1].isPrimitive() || p[3].isPrimitive() || p[4].isPrimitive()) continue;
            if (found != null && !java.util.Arrays.equals(found.getParameterTypes(), p))
                throw new IllegalStateException("AMBIGUOUS_LOGIN_METHOD");
            found = method;
        }
        if (found == null) throw new NoSuchMethodException("LOGIN_METHOD_UNAVAILABLE");
        found.setAccessible(true);
        return found;
    }

    void probe() throws Exception { loginMethod(service("LoginService")); }

    boolean switchTo(Account account) throws Exception {
        Object login = service("LoginService");
        Bundle extras = new Bundle();
        extras.putString("targetUid", account.uid);
        Object result = loginMethod(login).invoke(login, account.loginId, null, "switchAccount", null, null, true, extras);
        if (result == null) return false;
        Object status = property(result, "resultStatus", "getResultStatus");
        Object flag = property(result, "loginFlag", "isLoginFlag");
        return (status instanceof Number && ((Number) status).intValue() == 1000) || Boolean.TRUE.equals(flag);
    }

    private static Object property(Object object, String field, String getter) {
        try { return XHelpers.getObjectField(object, field); }
        catch (Throwable missing) {
            try { return XHelpers.callMethod(object, getter); }
            catch (Throwable unavailable) { return null; }
        }
    }

    private static String value(Object value) {
        if (value == null) return null;
        String text = value.toString();
        return text.isEmpty() ? null : text;
    }

    private static String masked(String text) {
        if (text == null || text.isEmpty()) return "账号";
        text = text.replaceAll("[\\r\\n\\t]", " ");
        if (text.length() <= 4) return "***";
        return text.substring(0, 2) + "***" + text.substring(text.length() - 2);
    }
}
