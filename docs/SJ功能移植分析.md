# SJ APK 核对、农场游戏时长与阅读授权

核对日期：2026-10-06。APK 本体为主要依据；外部导出 Java/smali 仅作待核实材料。

## 材料来源

- APK：`C:\Users\USER\Desktop\SJ\SesameX （SJ）版_3.6.14.apk`。
- SHA-256：`e0ac91838d291b7db8a4da02eee159bf18c06b191991c3c5d2b4f5263f92d15c`。
- 本体 `BuildConfig`：APPLICATION_ID=`leo.xposed.sexsj`、VERSION_NAME=`3.6.14`、VERSION_CODE=`120`、BUILD_TIME=`2026-10-04 02:07:09`。代码包名为 `leo.xposed.sesameX`。
- 本体只有 `classes.dex` 与 `classes2.dex`，未发现内嵌 APK、JAR 或原生库。两个 dex 的字符串表均不存在 `ReadForestTask`、`AntOrchardGameStayTask`、`readForest`、`receiveOrchardGameStay`、阅读服务域名、`FLOAT_BALL_TASK`、`floatBallDuration` 及游戏时长所需的首页/投放位/时长上报 RPC 名称；无纸阅读与完成游戏时长任务的中文开关文字也不存在。
- jadx 输出在桌面 SJ 目录的 `SJ-3.6.14-jadx-analysis`。全 APK 反编译报告 85 个错误，不能把所有还原 Java 当可编译源码；上述功能缺失判断另以原始 dex 字符串核对。
- 外部导出 README 指向 `Sesame-Normal-V4.78.apk` 的 `classes3.dex`，包名为 `io.github.lazyimmortal.sesame`，与本体不符。不能据此声称这两个功能来自 SJ 3.6.14，也无法判定导出文件是否经过人工/AI 修改。
- 导出 Java 与 smali 存在差异：农场 `scanIndexFeeds` 的 Java 第一页后返回，smali 遍历最多三页；阅读 HTTP Java 存在异常变量残缺；阅读连续五章无增长时两份代码均退出整轮，与 README 所称换书不符。

## 农场接入范围

位置：`AntOrchard`，配置 key=`receiveOrchardGameStay`，显示「农场乐园 | 完成游戏时长任务」，默认开启、按账号保存，与现有「游戏宝箱」开关独立。已有账号保存的开关值继续保留。

1. 复用 M 的 `ApplicationHook.requestString` 与统一 `RpcRequestGuard`。已有进入游戏与时长上报 RPC 接入实际任务流程。
2. 新增首页分页、投放位、首帧/加载/游玩事件及结项请求。新接口/结构/版本常量参考待核实的导出 smali；结项参数中的非空当前 userId、source、version 格式对照 SJ 本体现有 `AntOrchardRpcCall.finishTask`。
3. 仅处理 `FLOAT_BALL_TASK`，必须具备 appId、iepTaskId、iepSceneCode。按场景/任务/游戏去重，保留同游戏的不同任务；重复来源取较长时长。
4. 按服务端时长等待，最少 30 秒，单个任务超过 30 分钟则跳过。时长 RPC 用秒，`GAME_ELAPASED_TIME` 用毫秒；不采用导出代码等待 3–4 秒却上报较长时长的模式。等待和事件上报不等于真实前台游玩，服务端是否认可仍待实机验证。
5. 每个步骤验证成功状态。无效响应、显式失败或 RPC_SKIPPED 停止当前任务；仅成功结项后记 `Status` 当日标记。标记随账号隔离并按 M 的 GMT+8 日切清理，不使用设备默认时区的 SharedPreferences 日期键。
6. 同步执行并持有 `TaskLifecycle` 准入；等待通过 `TimeUtil.sleep` 感知取消，取消异常向上传递。无新线程、无新的限流层，不写普通肥料任务黑名单。
7. 成功日志使用结项返回的数值奖励字段；缺少数量时只写结项成功，不捏造 0g。若服务端要求前台行为或返回结构不同，必须依据日志调整，不能将本地回归通过等同于领奖成功。

回归：`python checks/check_orchard_game_stay.py` 隔离编译生产任务、RPC 和 TaskLifecycle，覆盖分页/去重/参数转义、时长单位、账号和日期隔离、各步骤失败、RPC 暂停、取消及切号冻结。模拟依赖不访问支付宝。

## 阅读需要什么授权

以下阅读 appId=`2021003114652763`、域名=`https://m.zhangwenwh.com/api`、版本=`0.2.2410251510.53` **只来自外部导出代码，本体没有佐证**。在确认真实目标小程序前，不把这些标识当已验证配置。

导出链路：获取目标 appId 的支付宝 `authCode` → POST `/authorization`（auth_code/app_id/cid）→ 第三方返回 token → Bearer token 请求书籍、章节和能量进度。miniMark 是另一项请求头，能否为空由第三方服务决定，不能从其它游戏服经验推断。

支付宝小程序官方接口为 `my.getAuthCode`。`auth_base` 是基础静默授权，`auth_user` 等范围需要主动授权；导出代码请求的是前者。因此当前问题首先是模块能否从宿主正确取得授权结果，不是给 M 增加 Android 权限或在 AI Token 配置里填密钥。[官方接口说明](https://miniprogram.alipay.com/docs-alipayconnect/miniprogram_alipayconnect/mpdev/api_openapi_getauthcode)

授权码具有时效性且一次有效，拿到后应立即完成交换，不能让用户填一个 authCode 当长期配置。这里的第三方 token 也不能与支付宝 access_token、app_auth_token 或 M 的 AI Token 混用。[官方授权接入注意事项](https://developer.alibaba.com/docs/doc.htm?articleId=105656&docType=1&treeId=346)

M 原 `AuthCodeHelper.getAuthCode` 自建 `Oauth2AuthCodeServiceImpl`，仓库已有注释记录其内部 facade 未注入导致失败。此次改为从 `RVProxy.get(Oauth2AuthCodeService.class)` 获取宿主注册的服务，仅请求模型自行创建；服务未就绪、类不存在或授权结果为空时返回失败，不再回退到自建实现。共享助手增加 TaskLifecycle 准入及取消检查，供阅读、游戏和 HTTP 路由复用，不缓存服务或授权码。宿主是否在当前支付宝版本中注册并初始化该服务，仍需实测：

- 在真实阅读小程序的正常登录上下文中观察 `my.getAuthCode` → `/authorization`，确认 appId、scope、登录响应、miniMark 是否必需；用户若遇正常授权提示，按小程序原流程完成。正常登录成功不等于 M 主动获取授权码已修复。
- 从宿主正常执行路径取得已初始化的真实授权服务实例，或验证宿主代理是否提供该服务，再让 M 获取新授权码；不重复 new 未注入的实例。实例/令牌必须绑定账号与生命周期，切号后失效，且不在日志中输出授权码或 token。

## 阅读接入范围与使用

森林增加 `readForest`（「无纸阅读」），按用户要求默认开启、按账号保存；只在正常森林任务末尾执行，「只收能量时段」不执行。按现有使用方法退出配置页保存，重启支付宝生效；先打开对应阅读小程序并完成正常登录/授权提示。每轮即时取授权码并交换第三方 token，miniMark 复用现有助手，允许为空尝试登录；授权失败停止，不能借用其它 appId 的授权码。

1. 请求链路：登录 → 检查当日进度 → 首页免费书 → 章节 → updateWel → 最近阅读 → 回读进度。每次 HTTP 要求 200，业务 code 要求数值 2000；缺 token、书籍/章节或有效整数 current/total 停止本轮。total 必须大于 0，取消导出代码的 150 兜底，避免 0/0 或畸形响应误报完成。
2. 时长字段保留导出协议每章 333–1000 秒的随机上报值，提交后等 1 秒，章节间隔 1–3 秒；这是协议上报，不代表前台真实阅读或真实等待该时长，不能据此宣称服务端必然发能量。每轮最多 30 章，重复章节或连续五章无增长停止整轮；不额外重试 HTTP，不把临时失败记为今日完成。
3. 仅选 price 字符串为 0.00 的免费书；同轮去重，章节明确结束后记录已读完书籍。永久记录复用按账号的 RuntimeInfo（key=`readForest.finishedBooks`）；仅服务端有效进度满额后写 Status 当日标记，日切沿用 GMT+8，不写宿主默认时区的日期键。
4. HTTP connect/read 超时为 10/20 秒，禁自动重定向、响应上限 4 MiB、UTF-8、流和连接保证关闭。UA 对照现有 GameTask 使用系统 UA 与实际宿主版本，不冒用导出的固定手机/支付宝版本。token/miniMark 为本轮实例状态，不写文件或日志；诊断写阶段、接口路径、HTTP/业务状态码与脱敏原因（先隐藏凭据，再截断至 96 字）。网络错误按超时、DNS、TLS、连接失败说明，不输出原始异常消息。
5. 同步持有 TaskLifecycle，并在每次 HTTP 前后及响应读取中检查代际/中断；取消异常向上传递，无新线程，不改独立 App 进程。共享授权助手的旧失败注释同步更新，游戏任务的登录行为未另行修改。

回归：`python checks/check_forest_read.py` 编译实际授权助手、HTTP 客户端、任务及生命周期，隔离宿主代理与 HTTPS（不访问任何账号/服务器）；覆盖服务取得、基础 scope、免费筛选/去重、跨书、持久化、满额/账号/日切、五章停止、循环、三十章上限、缺字段/错误类型/0 限额、HTTP/业务失败、授权未就绪、取消与资源释放。

两项功能的状态现均写运行日志：阅读还写森林分类，农场成功还写农场分类；不修改 Log 的全局分类写入规则。阅读在授权失败、登录缺 Token、HTTP 401/403 或服务端提示 Token/登录/授权错误时，附上打开对应小程序完成正常登录/授权后重新运行、由 M 自动换取 Token 的步骤。共享授权助手以线程内原因区分宿主不兼容/服务未就绪/空结果，即使重复错误被助手去重，阅读本轮仍写具体原因。农场写每个任务结项成功/失败（阶段、状态码、RpcRequestGuard.errorMessage 的服务端说明）和整轮数量；缺查询列表与没有可执行任务分别记录，不将查询失败伪装成正常空任务。

当前只连接到 `emulator-5556`，未安装支付宝，无法验证真实宿主服务及小程序；未向阅读服务发送真实登录请求。实际支付宝中的服务注册、miniMark 是否必需、导出 appId/版本/接口是否仍有效、能量是否入账仍待实机验证。安装包含本次改动的构建后，可凭森林日志中的脱敏阶段与状态码定位下一步；本地模拟通过只证明代码分支和请求结构，不能等同于到账。
