package io.github.aw1y2z.sesame.hook;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.util.Locale;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.compat.XC_MethodHook;

/**
 * 发现宿主打开“风险/验证”类 H5 页面：写一条验证记录（只记域名+路径，不记参数，避免带出令牌），并让
 * {@link PuzzleCaptchaSolver} 开始监视窗口。用来覆盖不经过接口报错、也不是 CaptchaDialog 的验证码出现方式
 * （例如做任务时宿主自己拉起验证页）。思路来自 GR 的 H5RiskOpenHook，但那边是纯诊断，这里额外触发拼图处理。
 */
final class H5RiskTrigger {
    private static final String TAG = "H5RiskTrigger";
    private static final String SDK_WEBVIEW = "com.alipay.mywebview.sdk.WebView";
    private static boolean installed;

    private H5RiskTrigger() {
    }

    static synchronized void setup(ClassLoader classLoader) {
        if (installed || classLoader == null) {
            return;
        }
        installed = true;
        int hooks = 0;
        hooks += hookLoadUrl(android.webkit.WebView.class, "android.webkit.WebView");
        try {
            hooks += hookLoadUrl(XHelpers.findClass(SDK_WEBVIEW, classLoader), SDK_WEBVIEW);
        } catch (Throwable t) {
            Log.record(TAG + "：找不到 " + SDK_WEBVIEW + "，只监视 android.webkit.WebView");
        }
        hooks += hookStartActivity("startActivity", new Class<?>[]{Intent.class});
        hooks += hookStartActivity("startActivityForResult", new Class<?>[]{Intent.class, int.class});
        Log.record(TAG + "：H5 验证页监视已挂载 " + hooks + " 个钩子");
    }

    private static int hookLoadUrl(Class<?> webView, String label) {
        try {
            XHelpers.findAndHookMethod(webView, "loadUrl", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    inspect(label + ".loadUrl", param.args != null && param.args.length > 0
                            ? String.valueOf(param.args[0]) : "");
                }
            });
            return 1;
        } catch (Throwable t) {
            Log.record(TAG + "：挂钩 " + label + ".loadUrl 失败：" + t.getClass().getSimpleName());
            return 0;
        }
    }

    private static int hookStartActivity(String method, Class<?>[] types) {
        try {
            Object[] args = new Object[types.length + 1];
            System.arraycopy(types, 0, args, 0, types.length);
            args[types.length] = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object first = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    if (first instanceof Intent) {
                        inspect("Activity." + method, String.valueOf(((Intent) first).getDataString()));
                    }
                }
            };
            XHelpers.findAndHookMethod(Activity.class, method, args);
            return 1;
        } catch (Throwable t) {
            Log.record(TAG + "：挂钩 Activity." + method + " 失败：" + t.getClass().getSimpleName());
            return 0;
        }
    }

    /** 命中风险/验证关键词才处理；同一页面 30 秒内只记一次（CaptchaTriggerStats 内去重）。 */
    private static void inspect(String source, String url) {
        try {
            if (StringUtil.isEmpty(url) || !isRiskUrl(url)) {
                return;
            }
            String summary = summarize(url);
            CaptchaTriggerStats.recordH5(source, summary);
            PuzzleCaptchaSolver.arm(source + " " + summary);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    static boolean isRiskUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("captcha") || lower.contains("slider") || lower.contains("risk")
                || lower.contains("verify") || lower.contains("validate");
    }

    /** 只留域名+路径。 */
    static String summarize(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String summary = host + path;
            if (summary.isEmpty()) {
                summary = url.length() > 80 ? url.substring(0, 80) : url;
                int query = summary.indexOf('?');
                summary = query >= 0 ? summary.substring(0, query) : summary;
            }
            return StringUtil.truncate(summary, 160);
        } catch (Throwable t) {
            return "unparseable";
        }
    }
}
