package io.github.aw1y2z.sesame.hook;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 在任意 WebView 类层级上解析 evaluateJavascript(String, callback)。
 * <p>
 * 有的宿主 WebView 实际 View 不继承 android.webkit.WebView，且方法可能为非 public
 * 声明；只用 Class#getMethods() 找不到会导致页面状态探针全部失败。此解析器先按
 * public 方法查找，再沿父类层级用 getDeclaredMethods() 并 setAccessible 查找，
 * 仍找不到时返回有界的候选方法名用于诊断。移植自 GR 分支，见 docs/MyFix.md。
 */
final class EvaluateJavascriptResolver {

    private static final int MAX_DIAGNOSTIC_NAMES = 12;
    private static final Pattern DIAGNOSTIC_NAME = Pattern.compile("^(?:evaluate|execute|execJavaScript|loadJavaScript|runJavaScript)", Pattern.CASE_INSENSITIVE);

    private EvaluateJavascriptResolver() {
    }

    static final class Binding {
        final Object receiver;
        final Method method;
        Binding(Object receiver, Method method) { this.receiver = receiver; this.method = method; }
    }

    /** Touch content 和 script API 可能分别在不同 View 上，只沿这条 owner 链走。 */
    static Binding resolveOwner(Object start, java.util.function.Function<Object, Object> parent) {
        java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Object candidate = start;
        for (int depth = 0; candidate != null && depth < 8 && seen.add(candidate); depth++) {
            Method method = resolve(candidate.getClass());
            if (method != null) return new Binding(candidate, method);
            candidate = parent.apply(candidate);
        }
        return null;
    }

    /** 返回匹配的方法；找不到返回 null。 */
    static Method resolve(Class<?> type) {
        if (type == null) return null;
        for (Method m : type.getMethods()) {
            if (matches(m)) return m;
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (matches(m)) {
                    try {
                        m.setAccessible(true);
                    } catch (Throwable ignored) {
                        // 目标进程无 SecurityManager；忽略仅防御性处理。
                    }
                    return m;
                }
            }
        }
        return null;
    }

    /** 有界列出疑似脚本执行方法名，仅用于失败诊断日志；不输出参数或内容。 */
    static String describeCandidates(Class<?> type) {
        if (type == null) return "none";
        List<String> names = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class && names.size() < MAX_DIAGNOSTIC_NAMES; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                String name = m.getName();
                if (DIAGNOSTIC_NAME.matcher(name).find() && !names.contains(name)) {
                    names.add(name);
                    if (names.size() >= MAX_DIAGNOSTIC_NAMES) break;
                }
            }
        }
        return names.isEmpty() ? "none" : String.join(",", names);
    }

    private static boolean matches(Method m) {
        Class<?>[] p = m.getParameterTypes();
        return "evaluateJavascript".equals(m.getName()) && p.length == 2
                && p[0] == String.class && p[1].isInterface();
    }

    static String describe(Method method) {
        if (method == null) return "null";
        return String.format(Locale.ROOT, "%s#%s(access=%d)",
                method.getDeclaringClass().getName(), method.getName(),
                method.getModifiers() & 0x7);
    }
}
