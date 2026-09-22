# MyFix：合并 fork 代码到本地的规则与背景

本页只放长期规则和背景资料，**不记录变更日志**；每次合并/修复/新增的记录一律写进 [CHANGELOG.md](../CHANGELOG.md)。合并来源 fork：GR2026、Sure-Xu、Sesame-AG，本地路径分别为 `E:\Work\Gr\Sesame-GR2026`、`E:\Work\Sure-Xu`、`E:\Work\Sesame-AG`。背景资料（GR2026 `MyUtils.java` 的完整拆解）放在文末「附录」，供后续合并对照。

## 硬性规则：每次改代码、合并代码、写新代码都要检查

以下规则是长期约束，合并上游代码、修 bug 或新增功能时都必须执行。每次合并必查 GMT+8、JSON 创建、JSON 读取三项，覆盖自动合并成功的新增/修改文件，逐项处理并记录结果；具体检查要求见 [AGENTS.md](../AGENTS.md) 的「每次合并必查」章节。

1. **时间必须按 GMT+8，不能用裸 `Calendar.getInstance()` / 系统默认时区**。用 `MyUtils.getInstance()` 替代 `Calendar.getInstance()`。背景：GR 自己的代码里也反复出现这个 bug（用户不在 GMT+8 时区跑设备时，跨天判断、定时任务会全部错位），Sesame-M 这边已经排查修过好几处（`FriendWatch.needUpdateAll()`、`ApplicationHook` 的 `dayCalendar`/`setWakenAtTimeAlarm`/`updateDay` 等，见 CHANGELOG.md 2026-09-12 记录）。当前 `TimeUtil` 的日历已显式使用 GMT+8；历史记录中的未统一描述不代表当前状态。仍需检查调用链中的日期格式化、解析与跨天判断，服务端 UTC/带偏移时间须按协议解析，不能机械改成 GMT+8。
2. **JSON 读取禁止裸 `.get*()`（`getString`/`getInt`/`getLong`/`getDouble`/`getBoolean`/`getJSONObject`/`getJSONArray`/不带类型后缀的 `get`），一律用对应的 `.opt*()` + 空指针防护**。背景：全仓库约 1986 处调用点的转换任务已在 2026-09-14 完成（见 CHANGELOG.md 记录），裸 `get*()` 在字段缺失/服务端返回结构变化时会直接抛异常导致任务崩掉，`opt*()` 返回 null/默认值后自己判空更稳。新写的代码、从 GR/AG/Sure-Xu 合并进来的代码，只要有 `org.json.JSONObject`/`JSONArray` 取值，一律按这个规范来，不要重新引入裸 `get*()`。
3. **JSON 创建统一按 MyUtils 处理**。业务字符串转对象使用 `MyUtils.newJSONObject(raw)`，并验证必要字段和成功状态；无效输入返回空对象不能视为成功。严格解析路径迁移时必须保留失败语义，确需直接构造时记录位置和理由。数组解析保留异常防护，不机械替换集合/空数组构造。此处指 `org.json`，不是 Gson。

5. **`GeminiAI` 不能删除**（`model/normal/answerAI/GeminiAI.java` 及其 `AnswerAIInterface`）。海外用户正在使用；上游 MIUIX-api102 重构 AI 答题为 `CustomAI` 时删掉了它，合并时如再遇到“上游删除 GeminiAI/TongyiAI”的冲突，必须保留 GeminiAI 并让 `AnswerAI` 继续提供 GEMINI 选项（配置 id `useGeminiAI`=1、`useGeminiAIToken` 不能改，否则用户已选的类型和令牌丢失）。通义千问已随上游移除，不必恢复。

4. **独立 App 进程（`MiuixMainActivity`/`MiuixSettingsActivity` 等 `ui/` 包下的代码，以及它们能直接调用到的 `util/` 工具方法）绝对不能引用 `ApplicationHook`（或任何继承 `io.github.libxposed.api.XposedModule` 的类）**。背景：`XposedModule` 是 `compileOnly` 依赖，运行时类只有真被 LSPosed 注入进支付宝进程后宿主框架才提供；独立 App 自己的进程里这个类根本不存在，一碰就在类校验阶段抛 `NoClassDefFoundError`——这是 `Error` 不是 `Exception`，`catch(Exception e)` 包不住，直接崩溃闪退（见 CHANGELOG.md 2026-09-15 `PermissionUtil.checkBatteryPermissions()` 那次踩坑记录）。独立 App 需要的任何数据/状态，走 `AppConfig`（跨进程共享配置）、直接读账号目录下的文件，或者广播/`Handler`，不要图省事直接调 `ApplicationHook.getXxx()`。

## 附录：GR2026 MyUtils.java 逐项说明（背景参考，非本次改动记录）

`MyUtils.java` 混合三类内容：① GMT+8 日历、null 安全 JSON 构造、版本隔离的异常标记存储等通用工具；② 作者账号上观察到的"已知会失败/已知会触发风控"硬编码开关（`_关闭XX` 系列、`_访问被拒绝*`/`_系统出错正在排查*` key、`_不是有效的入参` 任务 ID 黑名单）；③ GR 品牌相关（`getAppTitleExt`）。GR2026 内 44 个文件引用 `MyUtils.*`，覆盖 `hook/ApplicationHook.java`、几乎所有 `model/task/*` 模块等——任何整段照搬 GR 的 task/model 代码都会连带引入这个依赖。

| 成员 | GR2026 中的实际作用（示例调用点） | 合并到 M 时的处理建议 |
|---|---|---|
| `getInstance()` | 统一 GMT+8 日历入口，`entity/FriendWatch.java:121-123`、`hook/ApplicationHook.java:427,635` 等广泛使用 | 已移植（见上）。M 现有 `TimeUtil` 其余方法仍是系统默认时区，后续合并依赖 GMT+8 的具体业务逻辑时要显式改用 `getInstanceGMT8()`，不要假设默认时区已经是东八区 |
| `newJSONObject(String)` / `newJSONObject()` | 空/非 `{` 开头输入返回空 JSONObject，解析异常吞掉返回空对象；`data/RuntimeInfo.java:41-54` 用于状态文件读取容错 | 已移植，语义原样保留（含吞异常这一点） |
| `getSp功能异常` / `setSp功能异常` | 按 `errorMessage` 是否包含"访问被拒绝"/"系统出错"做一次性熔断，key 按 App 版本隔离；调用点如 `AntMember.java:1521-1525`、`MerchantService.java:20-67`、`ConsumeGold.java:177-182` | 机制已移植（`getSpFunctionError`/`setSpFunctionError`），具体 key 常量未移植——合并对应模块时再按需添加 |
| `closeVerification()` / `closeErrorFunction()` / `closeUnRpc()` | 恒返回 `true`，分别用于跳过拼手速/派遣动物等触发验证码的操作（`AntForestV2.java:633,645,1638`、`AntMember.java:159`）、跳过已知会报错的功能（`AntFarm.java:2495`、`AntMember.java:679`）、跳过不支持 RPC 完成的任务（`AntForestV2.java:2468`） | 已移植但改为读 `AppConfig`（默认 true）而非硬编码——函数名读起来像"可配置开关"，GR 里实际是硬编码，容易被当成已验证的安全跳过直接复制；M 版本是真的可配置 |
| `_不是有效的入参`（任务 ID 黑名单） | 硬编码若干活动任务 ID，`AntMember.java:1290` 命中则跳过 | 未移植，强版本/账号特定，会过期 |
| `getAppTitleExt()` | 按包名前缀 `"kt"` 返回 `"GR"` 品牌后缀，`data/ViewAppInfo.java:34` | 未移植，GR 品牌相关 |
| `recordUserName()` / `mUidMap` | 日志里把 UID 替换成可读昵称，`ApplicationHook.java:346,357,739` | 已移植，SharedPreferences 命名改为 M 自己的 |
| `encryptData()` / `decryptData()` | 在 `MyUtils` 里是直通（no-op）；GR2026 同时存在 `util/AESUtil.java` 里 `//CHANGE BY KT` 标记的同名真实加解密实现（`AESUtil.java:93,107`） | 未移植（无调用对象）。以后合并涉及这两个名字的代码，必须先确认源头是哪个版本，不能等价替换 |

### 每次合并 GR 代码的检查清单

1. `grep -n "MyUtils\." <待合并文件>`：命中的先查上表落在哪一类，已移植的直接接到本项目 `MyUtils.xxx`；未移植的按建议处理，不要囫囵抄。
2. `closeVerification()`/`closeErrorFunction()`/`closeUnRpc()` 在本项目已经是真开关（读 `AppConfig`），合并时确认默认值（`true`）符合预期，不需要额外处理；如果 GR 新增了同类恒真函数（新方法名），要按同样思路加成可配置项，而不是原样抄硬编码。
3. 不要迁移新出现的 `_访问被拒绝*`/`_系统出错正在排查*`/黑名单类硬编码字符串或 ID 常量本身，只迁移使用 `getSpFunctionError`/`setSpFunctionError` 机制的调用方式。
4. 涉及"加密/解密"命名的调用，先确认源头是 GR 的 `MyUtils`（直通）还是 `AESUtil`（真实实现）。
5. 合并后跑一次 `./gradlew compileNormalDebugJavaWithJavac` 确认编译通过，再在 CHANGELOG.md 补一条记录（改了什么、跳过了什么、为什么）。
