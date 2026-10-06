package io.github.aw1y2z.sesame.hook;

import io.github.aw1y2z.sesame.util.XHelpers;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import java.util.HashMap;
import java.util.Collections;

/**
 * OAuth2 授权码服务助手类
 * 调用宿主代理提供的授权服务。
 */
public class AuthCodeHelper {
    private static final String TAG = "Oauth2AuthCodeHelper";
    private static ClassLoader classLoader;

    /**
     * 同一失败类型只记一次，不输出可能包含凭据的宿主异常消息。
     */
    private static volatile String lastFailDesc;
    private static final ThreadLocal<String> failureReason = new ThreadLocal<>();
    
    /**
     * 初始化 Oauth2AuthCodeHelper
     * @param loader 应用类加载器
     */
    public static void init(ClassLoader loader) {
        classLoader = loader;
        Log.record("Oauth2AuthCodeHelper 初始化完成");
    }
    
    /**
     * 主动调用获取授权码
     * 通过反射调用 Oauth2AuthCodeService.getAuthSkipResult 方法获取授权码
     *
     * @param appId 应用ID
     * @return code，失败返回null
     */
    public static String getAuthCode(String appId) {
        failureReason.remove();
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null || appId == null || !appId.matches("[0-9]+")) return null;
            if (RunGeneration.isStale()) throw new TaskCancelledException();
            if (classLoader == null) {
                return fail("授权助手未初始化，请重启支付宝后重试");
            }
            // 1. 使用宿主代理提供的服务，不能自行 new 未注入 facade 的实现类。
            Class<?> serviceClass = XHelpers.findClass(
                    "com.alibaba.ariver.permission.api.proxy.Oauth2AuthCodeService",
                    classLoader
            );
            Class<?> proxyClass = XHelpers.findClass("com.alibaba.ariver.kernel.common.RVProxy", classLoader);
            Object oauth2AuthCodeServiceImpl = XHelpers.callStaticMethod(proxyClass, "get", serviceClass);
            if (oauth2AuthCodeServiceImpl == null) {
                throw new IllegalStateException("宿主授权服务未就绪");
            }
            
            // 2. 获取并实例化 AuthSkipRequestModel 类
            Class<?> authSkipRequestModelClass = XHelpers.findClass(
                    "com.alibaba.ariver.permission.openauth.model.request.AuthSkipRequestModel",
                    classLoader
            );
            Object authSkipRequestModel = XHelpers.newInstance(authSkipRequestModelClass);
            
            // 3. 设置 AuthSkipRequestModel 的参数
            XHelpers.callMethod(authSkipRequestModel, "setAppId", appId);
            XHelpers.callMethod(
                    authSkipRequestModel,
                    "setCurrentPageUrl",
                    "https://" + appId + ".hybrid.alipay-eco.com/index.html"
            );
            XHelpers.callMethod(authSkipRequestModel, "setFromSystem", "mobilegw_android");
            // Java 中 List.of 是不可变列表，对应 Kotlin 的 listOf
            XHelpers.callMethod(authSkipRequestModel, "setScopeNicks", Collections.singletonList("auth_base"));
            XHelpers.callMethod(
                    authSkipRequestModel,
                    "setState",
                    "QnJpbmcgc21hbGwgYW5kIGJlYXV0aWZ1bCBjaGFuZ2VzIHRvIHRoZSB3b3JsZA=="
            );
            XHelpers.callMethod(authSkipRequestModel, "setIsvAppId", "");
            XHelpers.callMethod(authSkipRequestModel, "setExtInfo", new HashMap<String, String>());
            
            // 构建并设置 appExtInfo 参数
            HashMap<String, String> appExtInfo = new HashMap<>();
            appExtInfo.put("channel", "tinyapp");
            appExtInfo.put("clientAppId", appId);
            XHelpers.callMethod(authSkipRequestModel, "setAppExtInfo", appExtInfo);

            // 4. 调用 getAuthSkipResult 方法获取授权结果
            Object authSkipResult = XHelpers.callMethod(
                    oauth2AuthCodeServiceImpl,
                    "getAuthSkipResult",
                    "AP",
                    null,
                    authSkipRequestModel
            );
            if (RunGeneration.isStale()) throw new TaskCancelledException();
            
            // 5. 解析返回结果中的授权码
            if (authSkipResult != null) {
                Object authExecuteResult = XHelpers.callMethod(authSkipResult, "getAuthExecuteResult");
                if (authExecuteResult != null) {
                    Object authCodeObj = XHelpers.callMethod(authExecuteResult, "getAuthCode");
                    if (authCodeObj instanceof String && !((String) authCodeObj).trim().isEmpty()) {
                        lastFailDesc = null;
                        return (String) authCodeObj;
                    }
                    return fail("宿主返回的授权码为空或类型无效，请先完成目标小程序登录/授权");
                }
            }
            
            return fail("宿主未返回授权结果，请先打开目标小程序完成登录/授权");
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Throwable e) {
            // 不输出宿主响应/异常消息，避免其中包含授权码。
            Throwable cause = e;
            for (int i = 0; i < 8 && cause.getCause() != null && cause.getCause() != cause; i++) cause = cause.getCause();
            String reason = cause instanceof ClassNotFoundException ? "当前支付宝缺少授权服务类，宿主版本不兼容"
                    : cause instanceof NoSuchMethodException ? "当前支付宝授权方法不兼容"
                    : cause instanceof IllegalStateException ? "宿主授权服务未就绪或尚未初始化，请先打开目标小程序"
                    : "宿主授权调用异常（" + cause.getClass().getSimpleName() + "）";
            return fail(reason);
        }
    }

    private static String fail(String reason) {
        failureReason.set(reason);
        if (!reason.equals(lastFailDesc)) {
            lastFailDesc = reason;
            Log.error(TAG + " 获取授权码失败：" + reason);
        }
        return null;
    }

    /** 仅当前调用线程的脱敏原因，避免其它游戏的并发授权覆盖阅读诊断。 */
    public static String getFailureReason() {
        String reason = failureReason.get();
        return reason == null ? "宿主未返回有效授权码" : reason;
    }
    
    /**
     * 私有化构造方法，避免类被实例化（对应 Kotlin 的 object 单例）
     */
    private AuthCodeHelper() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
