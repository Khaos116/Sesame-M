# Sesame-M R8 规则
#
# 只保留「名字有语义」的三类，其余交给 R8 收缩/优化/改名：
#   1) 按字符串加载：META-INF/xposed/java_init.list、Class.forName + getMethod
#   2) 磁盘键：ModelConfig.code = 模型类 getSimpleName()，即 config_v2.json 的键
#   3) 泛型签名与 JSON 属性名：R8 只在「签名里引用的类未被改名」时才保留泛型签名，
#      而属性名（无 @JsonProperty）完全由 getter/setter 名推导，改名即旧数据读不回来
#
# 以下能力 AGP 已提供，无需在本文件重复声明：
#   - proguard-common.txt：Signature/注解等 attributes、View 的 get*/set*、
#     Activity 的 *(View) 回调、enum values()/valueOf()、Parcelable.CREATOR、@Keep
#   - aapt_rules.txt：manifest 里的 Application / Activity / Provider / activity-alias
#     以及布局中出现的 View（故 ui 包与 SesameApplication 不需要整包 keep）
# 第三方库自带 consumer 规则的也不要手写整包 keep：okhttp3 自带 okhttp3.pro；
# nanohttpd 全库 0 处引用 java/lang/reflect（org.nanohttpd 包在 2.x 中并不存在）。

# ---------- 1. Xposed 入口：java_init.list 按字符串指名本类，框架还反射调用其生命周期方法 ----------
-keep class io.github.aw1y2z.sesame.hook.ApplicationHook { *; }
-keep class io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# ---------- 2. 扩展钩子：AntFarm 与 ExtensionsHandle 用 Class.forName + getMethod 按名调用 ----------
-keep class io.github.aw1y2z.sesame.model.extensions.** { *; }

# ---------- 3. Model 子类：类名即 config_v2 的键；实例由 Model.initAllModel 反射构造 ----------
-keepnames class io.github.aw1y2z.sesame.model.**
-keepclassmembers class io.github.aw1y2z.sesame.model.** {
    public <init>();
}

# ---------- 4. ModelField 家族：valueType 由 getClass().getGenericSuperclass() 解析，
#            基类或子类被改名会让泛型签名降级成裸签名 → valueType = null → Model 体系崩 ----------
-keep class io.github.aw1y2z.sesame.data.ModelField { *; }
-keep class io.github.aw1y2z.sesame.data.modelFieldExt.** { *; }

# ---------- 5. Jackson 持久化状态：类名同样决定泛型签名能否保留（ConfigV2 的
#            Map<String,ModelFields> 降级后 Jackson 只能造出 LinkedHashMap），
#            属性名即 JSON 键，故类名 + 构造器 + 访问器都要保留 ----------
-keep class io.github.aw1y2z.sesame.util.Status,
           io.github.aw1y2z.sesame.util.Status$*,
           io.github.aw1y2z.sesame.util.Statistics,
           io.github.aw1y2z.sesame.util.Statistics$*,
           io.github.aw1y2z.sesame.data.ConfigV2,
           io.github.aw1y2z.sesame.data.ConfigPreload,
           io.github.aw1y2z.sesame.data.AppConfig,
           io.github.aw1y2z.sesame.data.TokenConfig,
           io.github.aw1y2z.sesame.data.ModelFields,
           io.github.aw1y2z.sesame.entity.**,
           io.github.aw1y2z.sesame.hook.RpcRequest,
           io.github.aw1y2z.sesame.hook.ServerCommon {
    <init>(...);
    <fields>;
    public *** get*();
    public void set*(***);
    public boolean is*();
}

# ---------- 6. Jackson TypeReference 匿名子类：泛型实参只存在于子类签名里，
#            被收缩后即抛 TypeReference constructed without actual type information ----------
-keep class io.github.aw1y2z.sesame.** extends com.fasterxml.jackson.core.type.TypeReference

# ---------- 7. Jackson 本体：未随 jar 提供 consumer 规则且大量反射，暂整包保留 ----------
-keep class com.fasterxml.jackson.** { *; }
-dontwarn java.beans.**
