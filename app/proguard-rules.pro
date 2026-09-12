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

# Xposed 模块入口：META-INF/xposed/java_init.list 里按字符串加载，类本身和无参构造函数必须
# 保留可发现/可实例化，其余成员仍允许被 R8 优化/混淆（对齐 Sesame-AG 官方推荐写法，
# 比简单粗暴 keep 整个类更精细）
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class io.github.aw1y2z.sesame.hook.ApplicationHook extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Model.initAllModel() 用反射调无参构造函数实例化每个任务模块，简单类名对应配置 key
-keep class io.github.aw1y2z.sesame.model.** extends io.github.aw1y2z.sesame.data.Model {
    public <init>();
}
# AntMember 用反射跨类调用 AntOrchard 的私有方法 getWua()
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
-keep class io.github.aw1y2z.sesame.hook.RpcRequest { *; }
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
