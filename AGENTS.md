# Sesame-M 项目说明

开始任何任务前，先用 UTF-8 编码读取以下两份文档，了解项目背景和历史改动：

- `doc/MyFix.md` —— 每次合并上游/fork 代码、修 bug、加功能的详细记录：改了什么、跳过了什么、为什么。按时间倒序追加，最新的在最上面。文件开头有「硬性规则」一节（GMT+8 时间处理、MyUtils JSON 创建、JSON 使用 `.opt*()` 读取及判空），是长期约束，touch 到相关代码必须遵守。
- `CHANGELOG.md` —— 对应 `doc/MyFix.md` 的一行摘要清单，按时间倒序，标了 commit hash，用于快速对照某次提交改了什么。

这两份文档是本项目当前状态和历史决策取舍的权威来源，比重新审查代码或凭经验猜测更准确、更省时间。

## 每次合并必查：GMT+8、JSON 创建、JSON 读取

合并 `MIUIX-api102`、移植 GR/AG/Sure-Xu 或其它来源代码时，必须检查最终合并结果中的所有新增、修改文件（包括自动合并成功的文件），逐项处理以下三类问题，不能只解决冲突或仅凭编译通过就认定完成。修 bug、新增功能同样适用。

1. **GMT+8**：业务日历统一使用 `MyUtils.getInstance()`（委托 `TimeUtil.getInstanceGMT8()`）；日期格式化、跨天、签到、定时窗口显式使用 GMT+8，禁止依赖设备默认时区。服务端时间若明确带 UTC/偏移量，先按协议正确解析，再转 GMT+8 做业务判断，不能把 UTC 字符串直接当东八区解析。
2. **JSON 创建**：业务字符串转 `JSONObject` 统一通过 `MyUtils.newJSONObject(raw)`，空对象可用 `MyUtils.newJSONObject()`。不要把 Gson 与 `org.json` 混淆，也不要新增 Gson 替代现有工具。`MyUtils` 对无效输入返回空对象，调用方必须校验必要字段/成功状态，不能当作成功继续执行。已有严格解析路径若依赖解析异常中止任务，迁移时必须保留失败语义；确需保留直接构造的，在合并记录注明位置和理由。数组解析无对应 MyUtils 工厂时保留异常处理；创建空数组或用集合构造不作机械替换。
3. **JSON 读取**：`org.json.JSONObject` / `JSONArray` 一律使用 `.opt()` / `.optString()` / `.optInt()` / `.optLong()` / `.optBoolean()` / `.optJSONObject()` / `.optJSONArray()` 等安全读取，禁止裸 `.get*()`。嵌套对象和数组必须判空，必要字段缺失或类型不符应跳过/停止，不能仅替换方法名。Map、SharedPreferences、Bundle 等非 JSON API 不在此禁令范围内。

合并提交前必须复查这三项并运行相关回归；在 `doc/MyFix.md` 分别记录处理结果、例外及遗留项，不能未核对就声称“全部正常”。历史记录描述的是当时状态，当前结论以实际源码为准。

## 提交前的回归检查

`checks/` 目录下是一套本地 JVM 回归检查（编译生产代码本身，配合隔离的模拟依赖跑真实场景，不依赖 Android 设备/模拟器，也不请求支付宝接口）。改完代码、提交前，跑一遍确认没有回归：

```text
python checks/account_lifecycle/run.py
python checks/audit_regressions/run.py
python checks/check_reward_cooldown.py
python checks/check_log_follow.py
python checks/check_merge_config.py
python checks/check_rpc_guard.py
python checks/check_gr_followups.py
python checks/check_manifest_permissions.py
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin --console=plain
```

涉及打包/签名相关改动（`build.gradle`、`proguard-rules.pro`、签名配置）额外跑一遍 `:app:assembleNormalRelease` 确认 R8 混淆和签名没问题。

## 项目是什么

Sesame-M：支付宝自动化脚本的 Xposed 模块（`libxposed` API 102），仅供学习交流。是"芝麻粒"这个同源生态的一个分支，跟以下几个本地路径的 fork 是同源关系，移植/对照代码时会互相参考：

- `E:\Work\Gr\Sesame-GR2026`（GR2026）
- `E:\Work\Sure-Xu`（Sure-Xu）
- `E:\Work\Sesame-AG`（Sesame-AG）

品牌名固定写 **芝麻粒-M**（带 `-M` 后缀）；但代码里大量出现的裸"芝麻粒"字样多数指的是支付宝自己的游戏内货币/资产（会员积分、换金豆用的那个），跟品牌名是两回事，改品牌名字符串前先确认语境，别改错。

## 分支与仓库

- `origin` 指向自己的 fork 仓库（`Khaos116/Sesame-M`），不是上游。
- `MIUIX-api102` 是主线分支（原分支，PR 走这条线），`my_dev` 是日常开发分支。`my_dev` 落后 `MIUIX-api102` 时按 `git merge origin/MIUIX-api102` 处理，不要 rebase（历史上一直用 merge，保留双方提交）。
- 合并冲突的处理经验：先用 `git show <merge-base>:<file>` 查双方在分叉点各自的状态再决定，不要直接二选一。多数冲突要么是"my_dev 在分叉后新加的功能，upstream 那侧其实没变"（纯粹因为改动位置相邻产生的假冲突，取 my_dev 侧），要么是"upstream 做了真清理"（先 grep 确认 my_dev 这边也确实没人用了再跟着删）。详见 `doc/MyFix.md` 里"2026-09-15"那条记录的具体案例。

## 关键架构点

- `TaskLifecycle`（`data/task/TaskLifecycle.java`）：全局账号切换并发准入机制。任何会长时间运行、跨越账号切换窗口的代码（新起的线程、`postDelayed` 延迟回调）都要用 `TaskLifecycle.enter()`/`enter(generation)` 包起来，否则可能在账号切一半的时候继续用旧账号的状态跑，这类 bug 已经踩过好几次（见 MyFix.md）。
- `RpcRequestGuard`（`rpc/intervallimit/RpcRequestGuard.java`）：所有 RPC 请求（新旧两套 `RpcBridge`）统一收口的失败退避层，按账号隔离。新增业务代码走 RPC 不需要自己再实现限流/退避，两套 Bridge 已经接好了。
- `AppConfig` vs `BaseModel`（ModelField）两套配置系统不是一回事：`AppConfig` 是跟 App 独立进程共享的全局配置（存 `appConfig.json`，App 和被注入的支付宝进程都能读），`BaseModel`/各任务模块的 `ModelField` 是按账号存的业务配置（存 `config_v2.json`，只有注入进程里能看到）。哪个字段该放哪边要想清楚，之前把 `batteryPerm` 同时留在两边过，处理迁移花了不少功夫。
- 日志文件按账号分目录（`log/<userId>/`），当前账号通过 `FileUtil.publishCurrentLogUser()` 原子发布到 `current_log_user.txt`，独立 App 进程靠读这个文件名来判断"现在是哪个账号"（App 进程本身不知道支付宝那边登录的是谁）。
