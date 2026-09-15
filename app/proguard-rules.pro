# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the proguard-android-optimize.txt shipped with AGP.
#
# 对齐 Sure-Xu app/proguard-rules.pro（同为 libxposed 102 架构，已验证过反射面），
# 不是照抄 GR2026 那种"整包 keep 全部代码"的偷懒做法——那样等于没混淆业务逻辑。
# 见 doc/MyFix.md 的混淆开启记录。

# 保留异常堆栈的源文件名与行号，方便排错
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Jackson/TypeReference/ModelField<T> 依赖的反射元数据
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Xposed 模块入口：META-INF/xposed/java_init.list 里按字符串加载。之前只 keep ApplicationHook
# 一个类、allowobfuscation 让它可改名，hook 包下其余类（各种 XC_MethodHook 匿名回调、
# AccountSwitchController、NewRpcBridge 里的 Proxy.newProxyInstance 动态代理等）完全暴露给
# R8 混淆——真机上出现过点击触发某个 hook 回调时 NoClassDefFoundError（跨类引用没跟着改名
# 正确同步，ART 懒加载验证到用的时候才炸，不是启动期）。hook 包整体不是性能热点，混淆增益
# 换不回这种概率性崩溃，改成整包保留，只精细混淆业务逻辑（model/util 等）。
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class io.github.aw1y2z.sesame.hook.** { *; }
-keep class * implements io.github.libxposed.api.XposedModule { *; }

# Model.initAllModel() 用反射调无参构造函数实例化每个任务模块，简单类名对应配置 key
-keep class io.github.aw1y2z.sesame.model.** extends io.github.aw1y2z.sesame.data.Model {
    public <init>();
}
# AntOrchard.getWua() 现在是 public 直接调用（不再是 AntMember 反射跨类调的私有方法，
# 见 Model 系统改造记录），但 Jackson 仍可能按名反射，保留这条不算浪费
-keepclassmembers class io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchard {
    *** getWua(...);
}
# ExtensionsHandle 用 Class.forName 探测可选的 Alpha 扩展类是否存在
-keep class io.github.aw1y2z.sesame.model.extensions.ExtensionsHandle { *; }
-dontwarn io.github.aw1y2z.sesame.model.extensions.ExtensionsHandleAlpha

# Jackson 持久化这些 bean 靠字段/getter/setter 名字（含 @JsonIgnore），保留数据边界，
# 不保留具体任务实现和 UI 依赖
-keep class io.github.aw1y2z.sesame.data.* { *; }
-keep class io.github.aw1y2z.sesame.data.modelFieldExt.** { *; }
-keep class io.github.aw1y2z.sesame.entity.** { *; }
-keep class io.github.aw1y2z.sesame.util.Status { *; }
-keep class io.github.aw1y2z.sesame.util.Statistics { *; }
-keep class io.github.aw1y2z.sesame.util.Statistics$* { *; }

# Jackson 2.x 内部依赖反射构造函数和枚举字段
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { <init>(...); }
-keep enum com.fasterxml.jackson.** { *; }
-keep class com.fasterxml.jackson.core.type.TypeReference { *; }
-keep,allowobfuscation class * extends com.fasterxml.jackson.core.type.TypeReference { *; }

# 桌面版 JavaBeans 注解在 Android 上本来就没有，Jackson 自己会处理缺失，只是消警告
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

#-------------- okhttp3 / okio -------------
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.squareup.**

# NanoHTTPD 本地调试服务器
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# compileOnly 的 libxposed API，运行时由宿主框架提供，混淆期不在场，只消警告
-dontwarn io.github.libxposed.api.**
