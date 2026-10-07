# Sen new 与 M：无纸阅读、农场游戏时长

核对日期：2026-10-07。来源仅为当前目录的 `Sen-v26.09.30-r260930-new-arm64-v8a-release.apk` 及其反编译结果；不包含 Sen old。M 对照 `E:\Work\Sesame-M` 当前工作区源码。这里记录静态代码行为，接口现时资格和真实到账仍需实机验证。

## 结论

两项功能在 Sen new 与 M 中都存在，不能据此认定实现完全一致。阅读核心协议相同；入口、随机边界、停止保护不同。农场游戏时长的默认执行策略明显不同：Sen 默认极速上报，M 实际等待任务时长。

## 无纸阅读

| 对比点 | Sen new | M |
|---|---|---|
| 入口与默认值 | “其他”模块，`readForest` 默认关闭，另有 `readForestTime` 时间点；执行处缺值回退09:00 | 森林模块，`readForest` 默认开启；森林首页成功且非只收能量时段后执行，没有独立阅读时间点 |
| 小程序与主机 | `2021003114652763`、`https://m.zhangwenwh.com/api` | 相同 |
| 小程序版本/登录参数 | `0.2.2410251510.53`；`auth_code`、`app_id`、`cid=7001` | 相同；Token 仅本轮实例保存 |
| 主流程 | 登录→首页免费书籍→章节→阅读时长→最近阅读→能量进度，支持多本连读 | 同类流程，免费书籍 `price="0.00"`，已读书记录按账号持久化 |
| 每章上报的 `time` | 随机333～999，`af4.d(333,1000)` 的上界不含1000 | 随机333～1000，`RandomUtil.nextInt(333,1001)` |
| 实际等待 | 上报后1秒；下一章间隔随机1000～3000毫秒，换书另等2秒 | 上报后1秒；下一章间隔随机1000～3001毫秒，没有独立换书2秒等待 |
| 上报时间与真实等待 | 不相等：上述 `time` 是请求字段，不能称为真实等候333秒 | 同样不相等，不能把 M 说成按每章上报秒数实际等待 |
| 满额判定 | 比较 `current>=total`；部分缺失数据路径回退 current=0、total=150，存在字段默认值路径 | 必须有非负整数current及正整数total，支持合法数字字符串；字段异常停止，不写今日满额 |
| 连续无增长 | 连续5章无增长停止；外层收到false会结束整轮，日志“跳过此书”不代表继续换书 | 同样连续5章无增长停止，不写今日完成 |
| 运行边界 | 此阅读工作流未见对应15分钟上限或固定30章上限 | 单轮15分钟；无固定30章上限；重复/循环章节停止 |
| 今日完成 | 只在满额分支记录，外层检查当天标记 | 服务端有效进度确认满额才标记；账号准入、取消与敏感日志脱敏检查 |

上述细小随机边界差异不构成缺少一种阅读功能。定时入口可以作为增强，不能把整个阅读再次登记为新移植。

证据：Sen `task/other/OtherTask.java:87,206`、`task/other/forestRead/ReadForestTask.java:271,310,419,468,810`、`ReadForestRpcCall.java:8,549`、`sources/defpackage/af4.java:68`；M `model/task/forestRead/ReadForestTask.java`、`ReadForestRpcCall.java`、`antForest/AntForestV2.java`。Sen 路径均相对当前目录 `apk-analysis/sen-new/sources/byseven/forest/sen/`（af4除外）。

## 农场游戏时长

这里是蚂蚁农场/芭芭农场首页的 `FLOAT_BALL_TASK`，不是蚂蚁庄园小游戏。

| 对比点 | Sen new | M |
|---|---|---|
| 开关 | `receiveOrchardGameStay`，默认关闭 | 同名，默认开启 |
| 任务发现 | 首页feed和delivery；读取 `floatBallDuration`，缺值默认60秒 | 同类任务源和字段，缺值60秒；标识、字段类型和时长检查更严格 |
| 默认执行模式 | `execute$default` 将 `isFastMode` 设为true，农场入口调用默认参数 | 没有极速模式开关，按任务要求实际等待 |
| 入场事件 | `noticeGame`→`enterGame`→等待500ms→`GAME_FIRST_FRAME`→等待1500ms→`loading_completed` | 同类事件顺序；两次准备等待各500ms |
| 游玩等待 | 极速分支随机3000～5000ms，不包含网络耗时和准备等待 | `max(30,任务秒数)`×1000ms；非法/超过1800秒任务跳过 |
| 时长上报 | 极速分支 `max(31,duration)` 秒 | 实际等待的任务秒数 |
| `game_play.elapsedTime` | 极速分支 `(上报秒数+2)*1000`，与3～5秒游玩等待不一致 | 与上述等待/上报值一致：秒数×1000 |
| 非默认慢速分支 | 源码仍有按31秒阶段上报并补等待至任务时长加随机余量的分支；默认入口不会选它 | 只保留一致等待策略，没有复制该阶段式上报 |
| 结项 | 调用 `com.alipay.antiep.finishTask`，检查成功状态后读取肥料 | 同一结项接口；每个入场/事件/时长请求成功后才继续，成功结项才写当天标记 |
| 去重粒度 | 发现阶段按appId去重，当天标记含appId | 当天键含scene、taskId、appId；同一任务两来源选择较长时长，不误吞同app下不同任务 |
| 生命周期 | 协程取消重新抛出 | 既有TaskLifecycle/代际停止，等待后核对账号；取消不结项/不记成功 |

例如任务要求60秒：Sen默认游玩等3～5秒，报60秒及62000毫秒；M游玩等60秒，报60秒及60000毫秒。此差异是执行策略，不是 M 没有农场游戏时长功能；本次核对不修改为虚报时长。

证据：Sen `task/antOrchard/AntOrchard.java:294,2589`、`AntOrchardGameStayTask.java:315,520,556,595,878,1142`；M `model/task/antOrchard/AntOrchard.java:134,177`、`AntOrchardGameStayTask.java:108,144`、`AntOrchardRpcCall.java:172`。

## 验证及来源记法

M 的 `checks/check_forest_read.py` 和 `checks/check_orchard_game_stay.py` 可离线回放生产代码；不会执行 Sen 的伪代码反编译结果，也不会请求真实账号。两项在本轮比较开始前已存在于 M，这份对照只证明 Sen new 具有同类实现，不能倒推它们原来就是从 Sen 或真实SJ APK迁入。
