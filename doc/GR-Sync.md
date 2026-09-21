# Sesame-M 与 GR 代码关系及同步候选

日期：2026-09-12。对照项目：`E:/Work/Gr/Sesame-GR2026`。

本页与 `E:/Work/Sure-Xu/doc/GR-Sync.md` 结构一致，但性质不同：Sure-Xu 的文档记录的是**已完成**的迁移与验证；本页仅为**静态审计**，列出的"同步候选"尚未落地到 Sesame-M 代码中，落地前需按第四节的方式单独验证。本次没有修改 Sesame-M 的任何源码、构建脚本或签名文件。

## 代码关系

按业务包目录统计 Java/Kotlin 源文件：Sesame-M 222/8，GR2026 234/3。

Sesame-M 包名 `io.github.aw1y2z.sesame`，GR2026 包名 `io.github.lazyimmortal.sesame`（与 Sesame-M 遗留的 `LibraryUtil`/`BuildConfig` 桥接类同名）。剥离各自包名根目录后按相对路径比较：Sesame-M 228 个源文件，GR2026 237 个，相同相对路径 203 个。

对这 203 个相同路径文件，去除 `package` 声明行与空白行后逐字节比较，完全一致的有 31 个（`data/ModelFields.java`、`entity/RpcEntity.java`、`hook/HttpHandler.java`、`rpc/intervallimit/*` 全部三个文件、`util/TimeUtil.java`、`util/StringUtil.java` 等，多为无状态工具类和 RPC 基础设施）。其余 172 个路径相同但内容已分叉——这只是文本层面的比较结果，不代表这些文件行为不同或存在缺陷；Sesame-M 经历过整体 UI 重写（XML/Support → Compose+Miuix）和 API 102 迁移，同名的 `hook`/`data`/`entity` 文件普遍随之改动过引用和实现细节，分叉是预期结果，不是代码质量信号。此比较为脚本近似（规范化 `package` 行和空行后直接字符串比较），未做语义 diff，也未对 172 个分叉文件逐一确认差异范围。

这只是源码关系指标，不代表功能覆盖率——与 Sure-Xu 文档的说明一致。GR2026 独有 25 个相对路径（`extensions/`、`hook/ext/`、`ui/*`、`ui/dto/*`、`model/task/fish/`、`model/task/consumeGold/`、`model/task/antMember/MerchantService*` 等），Sesame-M 独有约 25 个相对路径（`ui/miuix/*.kt` 七个 Compose Activity、`model/task/goldenbeans/*` 七个金豆模块文件、`util/compat/XC_*`/`XposedBridge.java` 四个 Xposed 兼容层文件、`entity/AlipayGoldenBeansTaskList.java` 等）。

## 同步候选（未落地，需单独评估）

**福气鱼塘（钓鱼任务）已移植，不再是候选**——本节原先列的唯一候选。2026-09-12 已用 Sure-Xu 的 Java 版打底完成移植（`model/task/fish/FishTask.java`/`FishConfig.java`），详见 [doc/MyFix.md](MyFix.md) "福气鱼塘（FishTask）移植" 一节。此表暂无候选；2026-09-14 用更新版 GR 快照重新审计后发现另外一批新增任务模块（14 个独立业务包，如 dailyCash/dayDaySave/videoRewards/youthPrivilege 等），尚未系统性写入本文档，评估进度以对话记录/后续提交为准。

其余 GR2026 独有目录逐一核实后**不构成**同步候选，原因分述于下一节。除鱼塘外，本次未在 GR2026 侧发现其他可举证的、Sesame-M 完全缺失且明确可迁移的功能模块——`model/task/antFarm` 的装扮焕新（[FarmOrnaments.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/FarmOrnaments.java)）、家庭美食/cuisine（[AntFarm.java:663-666](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java#L663)、[AntFarm.java:2365-2377](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java#L2365)）、亲密家庭（`AntFarm.java` 的 `family`/`familyOptions` 字段与 `family()` 方法）在 Sesame-M 中已以整块 Java 方法的形式存在，只是组织方式与 GR2026 拆出的 [model/task/antFarm/AntFarmFamily.kt](../../Gr/Sesame-GR2026/app/src/main/java/io/github/lazyimmortal/sesame/model/task/antFarm/AntFarmFamily.kt) 不同，不属于"缺失功能"。

## 明确不应迁移的内容

- **消费金 / 商家服务**（GR2026: [model/task/consumeGold/ConsumeGold.java](../../Gr/Sesame-GR2026/app/src/main/java/io/github/lazyimmortal/sesame/model/task/consumeGold/ConsumeGold.java)、[model/task/antMember/MerchantService.java](../../Gr/Sesame-GR2026/app/src/main/java/io/github/lazyimmortal/sesame/model/task/antMember/MerchantService.java)）：Sesame-M 提交历史中已有一次专门移除（`4f803e99 移除消费金和商家服务功能`），属于本项目的既定方向性决定，不回迁。
- **版本伪装 Hook**（GR2026: [hook/ext/VersionHook.java](../../Gr/Sesame-GR2026/app/src/main/java/io/github/lazyimmortal/sesame/hook/ext/VersionHook.java)）：通过 hook `PackageManager.getPackageInfo()` 伪造支付宝版本号，属于特定版本/个人偏好类功能，与 Sure-Xu 文档中"GR 的版本伪装...属于具体版本/个人偏好，不直接覆盖"的判断一致，不作为通用能力迁入。（**Sesame-M 已于 2026-09-21 删除**：真机验证伪装版本不能让服务端改发简单滑块，见 CHANGELOG.md）
- **GR2026 的传统 UI 层**（`ui/BaseActivity.java`、`ui/ListAdapter.java`、`ui/ChoiceDialog.java`、`ui/dto/Model*Dto.java` 等）：这是 GR2026 尚未做 Compose 化的旧实现，方向与 Sesame-M 已完成的 Miuix/Compose 重写（`ui/miuix/*Activity.kt`）相反，不应作为参考迁回。

## Native 库（libsesame.so / watermark）现状

Sesame-M 的 `arm64-v8a/libsesame.so` 与 GR2026 同架构文件 SHA-256 完全一致（`5da46bb90e2e599ac4cb59e397cc52e98f542f4812873db8ade4e2e6d4b16049`），与 Sure-Xu 文档记录的同一二进制相同——即包含 JNI 初始化、庄园任务/抽抽乐任务、任务状态检查、AES 加解密及 unlockSesame 入口的那个库，同样未在本次重新反编译或执行确认其内部行为。

与 Sure-Xu（已删除四架构 SO 与旧包名桥接类）和 GR2026（据 Sure-Xu 记录已注释加载、改用 Java 实现庄园任务，但仓库仍保留 SO 文件）不同，**Sesame-M 当前仍在主动加载并使用这个库**：

- [ApplicationHook.java:294](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java#L294) 在支付宝 Service 创建时 `System.load(LibraryUtil.getLibSesamePath(context))`。
- [AntFarm.java:1843](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java#L1843) 调用 `LibraryUtil.doFarmTask(task)`，即庄园普通任务的执行路径仍依赖该 native 方法，与 Sure-Xu"庄园普通任务和抽抽乐主流程已经使用 Java RPC"的现状不同。
- [util/LibraryUtil.java:1](../app/src/main/java/io/github/lazyimmortal/sesame/util/LibraryUtil.java#L1) 文件顶部注释明确写着"为了不让 libsesame.so 崩溃，此类必须保留在旧包名 io.github.lazyimmortal.sesame 下，请勿随意移动"——这是刻意保留的兼容约束，不是遗留误留。

因此 Sesame-M 目前**不具备**移除该 SO 的前提条件：移除前需要先把 `AntFarm.java:1843` 的庄园任务判定逻辑改为不依赖 native 方法的等价实现（可参考 GR2026 的 Java 判定路径，但 GR2026 相应文件本次未逐行核对，不能保证行为完全等价），并验证移除后不影响庄园任务判定，这超出本次审计范围。

`app/src/main/cpp/watermark.cpp` 和 [CMakeLists.txt](../app/src/main/cpp/CMakeLists.txt) 是可以独立处理的另一件事：`app/build.gradle` 中未见 `externalNativeBuild`/`cmake` 配置块（GR2026 的 `app/build.gradle` 同样没有），即两个项目里这份 C++ 源码都没有被 Gradle 实际编译。[WatermarkUtil.java:13](../app/src/main/java/io/github/aw1y2z/sesame/util/WatermarkUtil.java#L13) 调用的 `System.loadLibrary("watermark")` 因此必然抛出 `UnsatisfiedLinkError`，四个 `get*Native()` 方法从未被真正调用，代码始终走 Java 侧默认值（`"免费模块 交流QQ群:694474777"`、透明度 30、字号 16、旋转角度 -30）。这与 Sure-Xu 文档"删除未接入构建的 watermark C++/CMake 文件，水印改为原来的 Java 回退值"记录的是同一类无效文件，Sesame-M 可以安全删除 `watermark.cpp`、`CMakeLists.txt` 及 `WatermarkUtil.java` 里的 `System.loadLibrary`/native 方法声明，只保留 Java 默认值分支——但本次未执行这个删除，只是确认了它是安全的候选项。

## 验证

本次仅做静态审计，具体操作：

- `find` 统计并 `comm -12/-13/-23` 比较 Sesame-M 与 GR2026 两个项目 `app/src/main/java` 下 Java/Kotlin 文件的相对路径集合（已按各自包名根目录归一化）。
- 对 203 个相同相对路径的文件，用 `sed` 去除 `package` 声明行、空行与行尾空白后逐字符串比较，得到 31 个内容一致、172 个已分叉的统计结果；未做语义级 diff。
- `sha256sum` 比较两项目 `arm64-v8a/libsesame.so` 二进制。
- `grep` 确认 `System.loadLibrary`/`System.load`/`LibraryUtil` 在 Sesame-M 源码中的调用点，`grep` 确认两项目 `app/build.gradle` 均无 `cmake`/`externalNativeBuild` 配置。
- 阅读 GR2026 的 `FishTask.java`、`AntFarmFamily.kt`、`VersionHook.java` 源码摘要，核对 Sesame-M 对应目录/关键字是否存在。

**未执行**：Gradle 编译（`gradlew`/`assembleDebug` 等）、任何脚本化行为验证、反编译或运行 `libsesame.so`、连接支付宝或任何真机/模拟器测试。本页列出的"同步候选"与"可删除项"均为静态代码事实陈述，不代表已验证在真实运行环境下的行为；落地前应比照 Sure-Xu 文档模式（编译通过 + 针对具体变更的最小验证脚本 + 明确不做的范围声明）单独处理。
