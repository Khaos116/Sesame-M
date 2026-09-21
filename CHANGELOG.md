# 修改记录

简明改动清单，按时间倒序追加，方便快速查看每次改了什么。上半部分为一行摘要 + 对应 commit；
文末「详细记录」为原 `doc/MyFix.md` 迁入的移植/合并原委与取舍理由。`doc/MyFix.md` 只放规则。

## 2026-09-21

- fix：拼图验证触发对齐 GR，修复“验证码弹出但验证记录一条都没有”。对照 GR 的 `CaptchaHook`/`SimplePageManager`：① 我的 `CaptchaDialog.show()` 钩子先 `getDialogInstance` 再 `collectDialogInfo`，任何一步取不到就直接 `return`，一行日志都不写——现在先记录再取细节，取不到弹窗对象/文字也照样写「验证记录」（`来源[CaptchaDialog:类名]`，文字可为空），并把弹窗对象直接交给 `PuzzleCaptchaSolver.arm(source, dialog)`，扫描时优先用它，不再依赖窗口跟踪列表；② GR 在 XRiver 页和登录/首页（`AlipayLogin`）恢复时就挂验证码处理器，我原先只在接口报错/弹窗/H5 URL 才启动——新增 `armPassive`：这两个页面恢复时静默扫描 8 秒（不写验证记录、不存无滑块截图），一旦识别到拼图滑块才转为正常流程并写“被动扫描发现拼图滑块”。仍是推测：不知道用户这次验证码具体走哪条路径，运行日志（1.1.6 11:02–11:14）里没有任何接口验证要求；新增的“钩子已挂载/H5 验证页监视已挂载”运行日志能区分“钩子没挂上”和“没触发”。未真机验证。
- feat `b3026e22`：验证码出现方式补充「H5 验证页监视」并加钩子挂载日志。依据：11:02 起的 1.1.6 真机运行日志到 11:14 没有任何接口返回 1009/“请验证”（也没有风控暂停），用户却在做任务时被弹出验证码——说明这次验证码是宿主自己拉起的页面，不经过 `RpcRequestGuard`，也没有命中 `CaptchaDialog` 记录，验证记录因此为空、拼图处理没被触发。新增 `H5RiskTrigger`（思路来自 GR `H5RiskOpenHook`，那边只做诊断）：挂钩 `android.webkit.WebView.loadUrl`、`com.alipay.mywebview.sdk.WebView.loadUrl`、`Activity.startActivity/startActivityForResult`，URL 含 captcha/slider/risk/verify/validate 时写一条验证记录（`来源[H5:…]`，只记域名+路径，不记参数，避免带出令牌）并 `PuzzleCaptchaSolver.arm`；同一页面 30 秒内去重。`CaptchaHook` 的 `CaptchaDialog.show()` 监视钩子和 `H5RiskTrigger` 现在都会在运行日志留“已挂载/挂载失败”，下次能区分“钩子没挂上”和“没触发”。风险：关键词较宽（含 risk/verify），可能命中无关页面，只会多记几条验证记录并启动 60 秒监视，不会自动拖动（拖动要求识别到滑块并匹配成功）；`loadUrl` 之外的打开方式（如 Nebula 的 startApp/openUrl）没有监视。未真机验证。
- fix `e0a85a88`：拼图验证码截图目录改为 `sesame-M/puzzle/<账号ID>/`（`FileUtil.getCurrentUserPuzzleDirectory`），不再放在 `log/<账号ID>/puzzle/` 下：用户在旧位置找不到文件，且放在 log 下清理日志时会被一起删。同时把之前 CHANGELOG 里写的截图位置更正为新目录。
- fix `ee878329`：拼图自动拖动后 1.5 秒发现验证窗口已关闭时，调用 `RpcRequestGuard.clearVerifyPause()` 解除验证暂停（复查发现遗漏：旧版简单滑块成功后会解除，拼图路径没有，触发验证的接口要多等最多 5 分钟）。窗口关闭不一定是通过，但解除无害：仍需验证时接口会再次返回“请验证”并重新暂停、重新监视。
- feat `fcee963f`：自动处理「对准图片」的拼图滑块验证码（移植 GR2026 `2609141630` 的图像匹配，流程重写为精简版）。核实：GR 的 `libsesame.so` 只有 AES/庄园饲料任务/签名校验，**没有任何验证码识别**，识别全在 Java。新增 `PuzzleSliderMatcherCore`/`PuzzleTextureMatcherCore`/`PuzzleOccludedContourMatcher`/`PuzzleSliderGeometry`/`PuzzleSliderMatcher`（与 GR 两个版本完全一致，纯 Java），`PuzzleCaptchaSolver`（验证被要求后每秒扫描窗口最多 60 次 → 找 `com.alipay.mywebview.sdk.WebView`/`android.webkit.WebView` → `PixelCopy` 截图 → 工作线程按颜色连通块识别滑块按钮与轨道终点 → 图像匹配得缺口位移 → 主线程拖动），`PuzzleSwipe`（触摸序列：带压力/接触面积/轻微弧线，屏幕坐标换算成视图内坐标，最后一次 MOVE 落在精确终点，中途失效补 CANCEL）。触发点：`RpcRequestGuard` 遇“请验证”（与验证记录同一处）、`CaptchaDialog.show()` 常开钩子（`CaptchaHook`，不受“关闭代理/VPN 弹窗”开关影响，含 VPN/代理字样的弹窗跳过）。`ApplicationHook.initSimplePageManager` 现在不分版本都开窗口监控（栈顶 Activity/对话框跟踪），“向右滑动”简单滑块处理器仍只在支付宝 ≤10.6.58 注册。新增配置「自动处理图片拼图滑块验证」（`BaseModel.autoPuzzleSlider`，默认开）。约束沿用 GR 经验：每个验证码窗口最多自动拖动一次，识别不可信不动手，只在验证被要求后的窗口期扫描；延迟回调与工作线程都用 `TaskLifecycle.enter(generation)` 包住。截图保存在 `sesame-M/puzzle/<账号ID>/`（只留最新 6 张；最初放在日志目录下，后改），过程写进「验证记录」日志（`拼图验证🧩…`）。**验证情况**：匹配算法用 GR 记录的 9 个真实脱敏样本离线回放通过（新增 `checks/check_puzzle_matcher.py`，夹具 `checks/fixtures/puzzle-slider/`）；`check_rpc_guard` 补了触发断言。**没有在真机上验证**：滑块按钮识别沿用 GR 参考设备布局（宽 1264、按钮约 (236,1787)）的颜色/位置常量，别的布局可能识别不到——识别不到只记日志并保存截图，不会乱拖；截图发来即可校准。GR 自述“部分图片验证失败问题尚待定位”，成功率不保证。**没移植**：悬浮“点击开始”控制按钮、手动触摸中断监视、H5 风险页 URL 钩子、HTTP 抓包与页面探针（后者用来在没有 1009/CaptchaDialog 的情况下发现验证页；手动触发的验证目前不会自动处理）、后台模式（要求窗口可见）、原生对话框里的拼图滑块路径。GMT+8/JSON：本次新增代码无日历/JSON 使用（截图文件名用毫秒时间戳）。
- refactor `602eca3d`：删除「版本伪装」功能（`VersionHook` 整个类、扩展功能页“版本伪装”卡片、`ApplicationHook` 里的注册/加载/日志、`version_config.json` 读写、`getEffectiveVersion`）。依据：09-19 真机日志（伪装开启、提前伪装生效，`已伪装173次`）弹出的仍是需对准图片的滑块，伪装版本改变不了服务端下发的验证码类型；默认早已改回关闭，用户日志里“开关=关”，`SimplePageManager` 因支付宝 12.x > 10.6.58 本就不启用。`alipayVersion` 现在始终是真实版本，行为与默认关闭一致，上游 `MIUIX-api102` 没有这个类，删除不产生合并冲突。`checks/audit_regressions` 去掉对 `VersionHook.handleRead` 的断言；`doc/GR-Sync.md` 该条标注已删除。旧设备上遗留的 `version_config.json` 无人读取，可手动删除。9 项回归与 debug 编译通过。
- merge `5e34fbb4`：再次合并 `origin/MIUIX-api102`（b1293b40 → 2dda9ba3，5 个上游提交：小鸡睡觉/起床按空间类型取值判断、使用说明与配置项说明文档/README、VPN 弹窗屏蔽功能、翻倍卡额外能量收取简化）。冲突与取舍：① `SimplePageManager`：上游删掉了 `CaptchaDialog.show()` 钩子（VPN 弹窗改由 `CaptchaHook` 统一挂钩），取上游；原先接在那里的 `CaptchaTriggerStats.recordDialog` 移到 `CaptchaHook.hookCaptchaDialogShowAndClose` 的“非 VPN/代理弹窗”分支——该钩子不受支付宝版本限制（只受「关闭请检查是否使用了代理软件或VPN」开关控制，开关关闭时不记弹窗，接口层 `recordRisk` 不受影响）。② `BaseModel`：my_dev 早已在 `137cf239` 修过同一个 boot() 被注释的问题，上游又新增一份同 key 的 `closeCaptchaDialogVPN`（标签“屏蔽VPN/代理弹窗”，默认开）导致重复定义无法编译，删掉上游那份，保留 my_dev 的字段（标签、默认关不变）与 boot 里 `setupHook` + `updateHooks`；`ApplicationHook` 里上游多加的一句 `CaptchaHook.setupHook(classLoader)` 保留（重复调用只重复打印日志）。③ `AntFarm`：my_dev 已整段删除自动睡觉，不引入上游的 `animalSleepNow`；起床处保留 my_dev 的 opt 读取与 `countDown` 判断，只采用上游“比对 `spaceType` 取值 == `ChickFamily`”（上游自述未实机验证，若服务端家庭空间取值不是 ChickFamily，家庭起床将不再触发）。自动合并部分：`CaptchaHook` 上游把 VPN 判断放宽为包含“VPN”或“代理”即关闭弹窗（原为整句精确匹配），照收，注意误关风险；`AntForestV2` 翻倍卡额外能量改为 `leftEnergy > 0` 即收，字段「倍卡额外能量(大于该值收取)」不再生效，字段暂留。GMT+8/JSON 创建/JSON 读取复查：本次合并新增/修改代码无新的 Calendar/裸 JSON 构造/裸 `get*()`，`AntForestV2` 用 `MyUtils.newJSONObject`。9 项回归与 debug 编译通过。
- fix `0530538c`：「验证记录」一条都没有。真机日志（支付宝 12.12.20.8000，伪装=关）显示 05:49 `cook` 返回 1009 后只有“风控验证🔐已将支付宝切到前台”，没有任何验证记录。根因：`initSimplePageManager` 在支付宝版本 > 10.6.58.99999 时整体不启用，`CaptchaDialog.show()` 钩子与 Activity 处理器都没注册，1533a213 的两个记录点在新版支付宝上永远不触发（伪装关闭后更是如此）。修复：`RpcRequestGuard` 遇“需验证”类失败（与 `showVerification` 同条件）时调用 `CaptchaTriggerStats.recordRisk(method, message)`，类型「风控要求验证(接口返回)」，不依赖界面 Hook，直接记录返回验证要求的接口。另补 `BaseCaptchaHandler` 找到滑动文字时的 `recordSlide`（仅低版本/伪装开启时有用）。局限：只能记到接口要求验证的时刻；界面弹窗形态（拼图/滑块）在高版本仍拿不到。未真机验证。
- fix `0530538c`：`RpcRequestGuard` 退避时长修正（用户指出“人气大爆发”不该停 24 小时、风控验证 1009 也不该停 24 小时）。① `errorMessage()` 补读 `resultView`：`loanpromoweb.promo.signin.query` 的“人气大爆发，请稍后再试”原先被当成“响应未提供错误原因”，非核心接口连续 3 次就走 `!core && failures>=3 → 24 小时`；现识别为临时繁忙（`isBusy`：人气大爆发/系统繁忙/请稍后再试），非核心接口按 5 分钟(前 2 次)/30 分钟退避，核心接口行为不变。② “请验证后继续”类（文案含“验证”或 cheating traffic）不再持久化暂停：只在内存暂停 5 分钟（`VERIFY_PAUSE`，按请求键 + `TaskLifecycle.generation()`），不写 `RuntimeInfo`，所以重启支付宝（进程重建）或切换账号（代数变化）后立即失效，请求重发即可重新弹出验证；用户反馈 24 小时（后又改成持久化 30 分钟~6 小时递增）都不对，因为持久化状态会带过重启。复查意见处理：验证暂停 30 分钟缩为 5 分钟（手动验证通过后不必久等；`showVerification` 本身 10 分钟节流），并新增 `RpcRequestGuard.clearVerifyPause()`，`BaseCaptchaHandler` 自动滑动成功后立即清除；`recordSlide` 不再 `String.valueOf` 包 null；`CaptchaTriggerStats` 去重由单变量改为按签名的 Map，接口/Activity/弹窗交替出现时各自去重。未采纳：`isBusy` 增加“活动太火爆”等变体（没有日志证据，日后见到再补）。非验证类的风控拒绝（“访问被拒绝”、无文案的 1009）仍持久化，30 分钟/2 小时/6 小时递增。旧版本已写入 `RuntimeInfo` 的 24 小时暂停：请求键前缀 `RpcRequestGuard.v1.` 改为 `v2.`，旧键不再被读取，整体作废（其余合法暂停会在下一次失败时重新学到）。③ 1009 但文案是“系统繁忙，请稍后再试”（neverland）按临时繁忙 5/30 分钟，不再当风控。`RpcFailurePolicy.RISK_DENIED_MS` 常量保留，`IsolatedRewardTask`/`OtherRequestGate` 的奖励请求层仍用它，未改。`check_rpc_guard.py` 更新原 24 小时断言并新增：验证暂停只在内存 30 分钟且不落盘、换号（代数变化）解除、1009 繁忙短退避、`resultView` 读取、人气大爆发连续 4 次不停一天。未真机验证；1009 若过于频繁重复触发会被 riskPause 递增到 6 小时，时长是按“先短后长”定的默认值，觉得不合适可再调。
- docs `0530538c`：复核 09-21 账号 2088702045701743 日报（53 次，GMT+8 00:40–09:31）+ 运行日志，结论：无需新增跳过规则。① 网络 48 共 18 类各 1 次，集中 01:46–01:49（约 3 分钟断网），守卫已按 1/5 分钟退避；② `receiveFarmTaskAward` 102 “开小差”6 个任务 ID 各 2–5 次，已有 5 分钟→30 分钟→6 小时退避；③ 金豆 `GOLDENBEAN_GAME_ZH0_LYJX_V1`/`ZH0_NCSCC` 已自动拉黑，`WAKUANG` “任务已完结”、`KUAIDI_VITALITY` “任务实例无效”各 1 次（此前每天 11+ 次，已收敛）；④ `walk.go` GO_STEP_NOT_ENOUGH、`signin.query PLAY102632271`（“人气大爆发”）均已暂停 24 小时；⑤ `donation` 218 “自营项目没指定标的物”1 次，紧接着回退到公益捐蛋成功（日志 23:40:23），属预期；⑥ `receiveFamilyAward` V07 “权益已领取”、`collectEnergy` `TARGET_USER_PROTECT_BY_ENERGY_SHIELD`（列表后被加罩的竞态）是良性结果，只占日报计数，不处理；⑦ `cook` 1009（06:49，来源 `antfarmzuofanrw`）今天唯一一次风控验证，已暂停 24 小时。证据不足/暂不处理：⑤⑥ 若日后次数明显增多再考虑不计入失败统计。

## 2026-09-19

- merge `2544c90a`：再次合并 `origin/MIUIX-api102`（93140f06 → b1293b40，2 个上游提交“修复广播来源未校验并清理注释代码与统一日志截断”“修复20:01后回退公益捐蛋导致当天重复捐蛋”）到 `my_dev`，3 个文件冲突（`ApplicationHook`、`BaseModel`、`AntFarm`）；三项必查无问题，十项回归通过。详见详细记录。
- feat `1533a213`：新增独立日志类型「验证记录」，统计哪些功能会触发弹出验证码。`Log.captcha`（不计入 `countModuleLog`）写 `captcha.日期.log`，同时以 `CAPTCHA` tag 写运行日志；日志页与首页开关新增「验证记录」（`AppConfig.enableCaptchaLog` 默认开）。`CaptchaTriggerStats` 在 `CaptchaDialog.show()` 之后（以及处理器在 Activity 里找不到“向右滑动验证”但界面有验证文字时）记一行：类型（向右滑动/对准图片拼图/未识别）、来源、**当时运行中的模块**（`ModelTask.runningTaskNames`）、**最近 5 个 RPC**（`RpcRequestGuard.recentRequests`）、界面文字；同来源同类型 30 秒去重；每轮执行开头打印“验证码触发统计(本进程)：模块 N次”。归因是推断（弹窗前最近的请求/运行中的模块是嫌疑对象），手动在支付宝里操作触发的验证会显示“无运行中模块”；计数进程重启清零，事件本身都在日志里。只观测，不点击/拖动/关闭弹窗。未真机验证。
- fix `7f17e603`：版本伪装默认改回关闭。1.1.5 默认开启并做了定向提前伪装，真机日志（18:01，184719 包）显示日志模块早读被改写（`早期伪装(日志模块)2次`）、之后 173 次读取全部被改写，但弹出的仍是需对准图片的滑块——通过 `PackageManager` 伪装版本对验证码类型无效，主动向服务端谎报版本有风险却无收益。`enableVersionHook` 缺省 `false`、`sEarlyFake` 缺省 `false`；新建配置写 `defaultOffApplied` 标记；`loadVersionConfig` 一次性把没有该标记、且正好是 1.1.5 自动写入的默认值（开启 + 10.6.58.8000 + 1881）的配置改回关闭并打标记（用户改过版本名/版本号的不动；在 1.1.5 手动开启且没改默认值的也会被关一次，需在扩展页重新开启）；移除“旧默认自动迁移为开启”；扩展页说明改为默认关闭、不建议开启。诊断日志保留。**发现的显示问题（未修）**：日志里“实际版本”会显示伪装值，疑似系统缓存了 `PackageInfo` 对象而我们就地改写，只影响该行显示。
- feat `d1d87b26`：庄园多阶段饲料任务每轮打印进度日志 `庄园饲料任务[标题]阶段 x/y，待领 Ng，状态 S`（同一状态只打一次）。用户反馈界面仍显示 180/240、右边“可领取”从 30g 变 60g：界面的 180/240 是**已领取额**，做完没领的显示在“可领取”，180+60=240 即 8 阶段已做满，光看界面分不清阶段是否做满，加日志便于核对。仅日志，无逻辑改动。
- fix `0c409ef7`：庄园多阶段饲料任务先做完所有阶段再领奖，待领额按累计减已领计算（对照 AG）。用户反馈饲料任务停在 180/240、没做完 8 阶段，而 AG 会做到 240/240 一次领 240g。日志（1.1.5，18:18 编译）里庄园阶段只有“还有待领取的饲料”，无任何“饲料任务🧾完成”：上一版按轮执行只是 GR 的“做一阶段→领 30g”加了循环，`receiveFarmTaskAward` 用 `awardCount + foodStock > foodStockLimit` 判断容量，领不了就停，任务卡在“有奖没领、也不做下一阶段”。现：① 按轮执行时，FINISHED 状态的多阶段任务（`rightsTimesLimit>1`、`rightsTimes<limit`）**先继续 `doFarmTask` 把所有阶段做完**，不做一阶段就领一阶段（用户明确要求“完成任务就行，不用马上领，用了饲料再领”，也与 AG 最终 240/240 一次领一致），奖励累积；阶段做不了时才退回领奖，避免服务端不允许时卡死；全部阶段做完（`rightsTimes==limit`）或喂鸡腾出容量后由领奖路径/既有 `checkUnReceiveTaskAward` 一起领；仅按轮执行生效，`checkUnReceiveTaskAward` 的单独领奖遍历行为不变；② 容量判断和入账改用待领额 `pendingAward = awardCount − alreadyReceiveStageAwardCount`（AG `getMultiStageAccumulatedAward`），原先拿累计总额（如 240g）判断，会把放得下的待领奖励误判成超上限；差值为 0 时退回 `awardCount`，单阶段任务行为不变；③ `alreadyTried` 按“动作(do/receive/stage)+状态+进度+待领额”去重；④ 最大轮数 10→20。**不确定**：服务端是否允许有待领奖励时继续 `doFarmTask`（AG 走这条路径，未在 M 实测）；`alreadyReceiveStageAwardCount` 的语义按 AG 的用法推断。未真机验证，无回归覆盖。
- fix `03b52936`：定向提前伪装没生效——调用方识别失败。真机日志（`runtime.2026-09-19.<账号>`，1.1.5，18:18 编译）显示 `提前伪装=开`、开关就绪前早读 12 次、`早期伪装(日志模块)0次`，来源栏又变回 `VectorChain/VectorNativeHooker` 框架帧：`callerFrames` 只在栈顶 24 帧里找最后一个 hook 机制帧，多层 hook 嵌套（LSPatch 加载器等）时框架帧超过窗口，于是把框架帧当成调用方，`fromLoggingModule` 认不出 `com.alipay.mobile.common.logging.`。改为扫描整个栈、按类名跳过 hook 机制帧（`org.matrix.vector`/`org.lsposed`/`LSPatch_`/`libxposed`）、反射/`ApplicationPackageManager` 帧和本模块帧（R8 短名类不含 `.` 一律跳过；`io.github.aw1y2z` 包）；来源样本相同的只留一条，每种类型上限由 3 提到 6，便于看全 12 次早读的来源。仍是假设：日志模块识别出来后是否真能改变验证码类型，取决于 `LogContextImpl` 缓存的版本是否就是那个。未真机验证。
- fix `dddb5807`：日志查看器切换 tag 筛选或修改搜索文本后回到列表顶部。`MiuixLogViewerActivity.LogScreen` 里 `listState` 与筛选条件互不相干，过滤结果变了但滚动位置保留，停在结果中间；加 `LaunchedEffect(selectedTag, searchQuery) { listState.requestScrollToItem(0) }`（列表顶部是最新一条，与既有 `updateEntries` 的“跟回顶部”一致，用 `requestScrollToItem` 也不受列表因结果为空被移除/重建的影响）。搜索输入每敲一个字也会回顶，属预期。未真机验证，无回归覆盖（Compose UI）。
- fix `53744e13`：版本伪装对支付宝日志模块的“早读”做定向提前伪装。真机日志（`runtime176-3`，1.1.5，模块 17:51 编译）的来源栏显示：开关就绪前支付宝读自身版本 11 次，来源是 `com.alipay.mobile.common.logging.ContextInfo.b < LogContextImpl.<init>`、`logging.util.perf.Judge.<init>`（日志/上下文模块，启动时读一次并缓存，最可能就是发给服务端的“应用版本”）和 `com.alipay.mobile.quinox.startup.UpgradeHelper.getUpdatedTimeFromPackageInfo < upgrade`（升级检查）；这些读取发生在配置加载前，此时开关为关，读到的是真实版本 12.12.20.8000，所以“已伪装”只对之后的读取有效，用户实测弹出的仍是需要对准图片的滑块。现 `sEarlyFake` 缺省为开（读不到配置——首次运行、文件读取失败——就按默认开启、默认版本 10.6.58.8000），`preloadEarlyEnable` 只在配置文件明确关闭时才把它关掉，`handleRead` 对“紧邻 2 个调用帧属于 `com.alipay.mobile.common.logging.`”的早读改写版本，其它早读（quinox 升级检查等）保持真实版本，不像全局提前伪装那样波及支付宝启动逻辑。`earlyEnable` 语义随之改为缺省为真、写成 `false` 可关闭（`saveVersionConfig` 仅在为 `false` 时写回）；诊断行新增“提前伪装=开/关”和“早期伪装(日志模块)N次”，早期伪装只计数不打日志（日志系统可能未就绪）；`callerFrames` 兼容 LSPatch/`org.matrix.vector` 帧。`audit_regressions` 中伪装取值的断言改指 `earlyName()/earlyCode()`。**仍是假设**：LogContextImpl 缓存的版本是否就是决定验证码类型的那个，需要下次弹验证码时看是否变成简单滑块；也可能 `Judge`/`quinox` 才是。未真机验证。
- feat `2bedc9d7`：庄园饲料任务按轮执行，全部完成后当天不再查询（对照 AG 的多阶段任务处理）。原先每次执行 `listFarmTask(TODO)` + `listFarmTask(FINISHED)` 各一次，多次任务（如“试玩庄园火爆小游戏”每次 30g，日志里 09:30/09:59/10:04/10:59/11:11/16:17 各做一次）每轮只推进一次、并且完成后每轮仍继续查询。现 `runFarmTaskRounds`：每轮一次 `listFarmTask(null)` 同时处理 TODO 和 FINISHED；服务端列表里没有需要处理的任务 → 记当日标记 `antFarm::farmTaskAllDone`（`Status` 按账号存储、次日清）不再查询；有任务但本轮无推进（失败/冷却/饲料满领不了）→ 停、不记标记、下次再试；有推进 → 再来一轮，最多 10 轮；同一任务同一状态/进度本次只试一次（`alreadyTried`，对照 AG `actionKey`），避免“成功但状态不变”的任务白跑满 10 轮。附带：`receiveFarmTaskAward` 对非饲料奖励（工具等）RPC 成功后改返回 true（原返回 false 会被按轮执行当成一直没做完）；不支持 RPC 完成的 bizKey 抽成 `isUnsupportedFarmTask`，不计入“需要处理”。**代价**：当天新冒出的任务要等次日才会再查；饲料满领不了奖的任务会让标记迟迟不记（与原先每轮都查一致）。范围只有庄园饲料任务，其它模块的一次性任务未动。三项必查：无日历/日期代码，无 JSON 创建，读取全为 `opt*`。未真机验证，无回归覆盖（`AntFarm` 过大）。
- feat `99f33eb7`：导出的日志文件名带账号。`FileUtil.exportFile` 对 `log/<userId>/` 下的文件在扩展名前插入 userId（`runtime.2026-09-19.log` → `runtime.2026-09-19.2088702045701743.log`），多账号导出到同一下载目录不再重名/覆盖，也能看出是谁的；与 `rpc-failures.日期.账号.json` 命名一致；文件名已含账号（异常统计）或取不到账号（`default`）时保持原名。日志**内容**仍不写 uid/昵称（`Log.withUser` 的隐私约定不变），只是文件名带账号，分享文件时要注意。用 uid 而非昵称/序号，因为独立 App 进程读不到昵称映射，而账号目录名就是 uid。未真机验证，无回归覆盖（`exportFile` 依赖 `Environment`）。
- fix `2621d373`：版本伪装诊断的“来源”改进。首轮真机日志（`runtime176-2`，1.1.5）显示：支付宝在开关就绪前读自身版本 11 次（真实版本，全部走 `int` 重载），`PackageInfoFlags` 重载 0 次，91 毫秒后出现“版本伪装已生效”，即配置加载后的读取会被改写；但来源栏 6 条全是 `yb2.callAfter<ac2.intercept<VectorChain`——本模块 hook 框架类被 R8 混淆成 `yb2/ac2`，按包名过滤不掉，真正的调用方没显示。现改为在栈顶 20 帧里找最后一个 hook 机制帧（LSPosed/Vector/libxposed/`ApplicationPackageManager`），取其后 4 帧为调用方，且每种类型（早读/已伪装…）各留 3 条，不再被早读占满名额。仍不能断定服务端验证码类型看的是早读还是之后的读取，需要弹出验证码那一轮的日志对照。未真机验证。
- fix `307c8080`：版本伪装补两处可能的漏洞。① 只 hook 了 `getPackageInfo(String, int)`，API 33+ 的 `getPackageInfo(String, PackageInfoFlags)` 重载被绕开：现两个重载共用 `handleRead` 改写（int 重载内部委托到 Flags 重载时跳过，避免重复）；② 支付宝在 `Application.attach` 就读走并缓存了自身版本，此时配置未加载、开关为关：新增可选开关 `version_config.json` 的 `"earlyEnable": true`（配合 `"enableVersionHook": true`），在 `initVersionHook` 时提前打开开关；**默认不启用**，因为提前伪装会让支付宝启动阶段的版本校验（热修复/容器版本匹配等）也看到假版本，存在让支付宝异常的风险，没有实测前不敢做成默认。模块自己记录“实际版本”改用 `VersionHook.readRealVersionName` 绕过伪装。与上一条诊断日志配套，装包跑一轮后按诊断行判断是否需要打开 `earlyEnable`。`audit_regressions` 里“伪装取 `getFakeVersionName()/getFakeVersionCode()`”的断言随逻辑挪到 `handleRead` 而改指新位置。外部审查后再修两处（核对成立）：`sInIntOverload` 原来对所有包名置位，支付宝探测未安装的微信/QQ 时原方法抛 `NameNotFoundException`，`XHelpers` 此时不执行 `afterHookedMethod`，标志残留在常驻线程上会让该线程之后的 `Flags` 重载伪装被永久跳过，现只对支付宝自己的包名置位；`saveVersionConfig` 覆盖写文件时没带 `earlyEnable`，用户手写的开关会在迁移或扩展页保存时被抹掉，现读取时记下并在保存时写回。**未验证伪装能否让服务端下发“滑到最右”的简单滑块；也可能支付宝的版本并不来自 `PackageManager`（如自带 BuildConfig/元数据），此时无论怎么改这里都无效。**（更新：其中 `earlyEnable` “默认不启用、全局提前伪装”的设计已被最上方“定向提前伪装”取代——缺省为真，且只对日志模块生效。）
- feat `307c8080`：版本伪装诊断日志。`VersionHook.diagnostics()` 每轮执行在“编译时间”后打印一行：开关、Hook 是否注册、支付宝读取自身版本的次数（开关就绪前=拿到真实版本 / 已伪装 / 开关关闭时 / `PackageInfoFlags` 重载，后者只统计不改写并排除 int 重载内部委托），首次附带读取来源（去掉本模块/Xposed/反射帧的前 3 个调用方，最多 6 条，启动早期只缓存不直接打日志）。背景：运行日志（1.1.5）显示“应用版本：10.6.58.8000（实际 12.12.20.8000，已伪装）”，但 0 次“版本伪装已生效”，无法确认支付宝自己读到的是否被改写；可能原因是它在 `Application.attach` 就读走了版本（此时配置未加载、开关为关），或走了未 hook 的 `PackageInfoFlags` 重载。判读见方法注释。每轮执行打印“模块版本”与“编译时间”原本就有（`ApplicationHook`，与 GR 一致），未改。未真机验证。
- fix `307c8080`：版本伪装对老用户不生效——1.1.5 的“默认开启”只写进新建的 `version_config.json`，≤1.1.4 建的旧文件（关闭、版本名空、版本号 0）不会被改，用户反馈庄园使用美食弹出的仍是需对准的滑块；运行日志（模块 1.1.2）里 `应用版本：12.12.20.8000`、无“版本伪装已生效”也印证这点。`VersionHook.loadVersionConfig` 现把“关闭且版本名空、版本号 ≤0”的旧默认视为未改动，迁移为默认开启 10.6.58.8000/1881 并保存；用户改过的配置不动。**伪装是否真能让服务端下发“滑到最右”的简单滑块仍未验证**（GR 声称 ≤10.6.58 可自动过简单滑块，当前真实版本是 12.12.20）。
- fix `96e664f5`：没开通/未认证的功能一天最多请求一次（按账号）。运行日志 `runtime.2026-09-19.log` 的 196 次失败里 150 次是 `com.alipay.antfarm.collectManurePot` 返回 `G04`“肥料已经存满了，去开通芭芭农场种果树吧”（小号没实名开不了芭芭农场，两个肥料罐每轮同步都重复请求）。在所有请求收口的 `RpcRequestGuard` 加统一规则：响应文案含“去开通/请先开通/请先认证/请先实名/未认证/未实名”即该请求暂停 24 小时（按账号隔离，与既有暂停机制一致；只匹配对本人的提示，“好友未开通”这类针对他人的状态不算）。`check_rpc_guard.py` 补充：G04 后一天内跳过、到期恢复，好友类文案不暂停。既有断言“会员 `NOT_CERTIFIED`/“请先实名认证” 4 次调用共发出 3 次”随新规则改为 1 次（属未认证，一天一次）。先前在 `AntOrchard`/`AntFarm` 里逐点加标记的做法已撤回（未提交）。**遗留**：暂停是 24 小时不是自然日；只有日志里出现过的 `G04` 这一种被实测覆盖，其它接口的“未开通”文案（如各模块自己打印的“绿色经营未开通”是本地判断，不经此规则）需要日志里出现后再补关键词。未真机验证。
- merge `e73337e7`：再次合并 `origin/MIUIX-api102`（d51b841f → 93140f06，1 个上游提交“修复光盘行动图片清空失效并收紧异常捕获与日志截断”）到 `my_dev`，2 个文件冲突（`TokenConfig`、`BaseModel`）；三项必查无问题，十项回归通过，`GeminiAI` 未受影响。详见详细记录。
- merge `458b043a`：合并 `origin/MIUIX-api102`（4f975462 → d51b841f，7 个上游提交）到 `my_dev`，9 个文件冲突（含上游删除 `GeminiAI`/`TongyiAI`、新增 `CustomAI` 通用 AI 答题）；三项必查、九项回归 + `check_standalone_no_xposed_class` 通过。详见详细记录。
- fix `a2e8b421`（另 `95a76ac0`、`9e940446` 补测试按钮与日志）：**恢复被合并误删的 `GeminiAI`**（海外用户正在使用，不能删除）。`GeminiAI`/`AnswerAIInterface`/`audit_regressions` 的 `GeminiAI` 检查及 `Answers.java.in` 全部恢复；`AnswerAI` 重新提供「AI类型」选项（字段 id 沿用 `useGeminiAI`，`GEMINI`=1，令牌沿用 `useGeminiAIToken`，已选 Gemini 的配置不丢），`CustomAI` 实现 `AnswerAIInterface` 作为另一选项（`CUSTOM`=0，占旧通义千问的 0 号位，通义千问不恢复）。规则写入 `doc/MyFix.md` 第 5 条与 `AGENTS.md`，合并时不得再删。「测试响应」按钮改为按当前选中的 AI 类型测试（选 GEMINI 测 Gemini 令牌，否则测自定义AI）。按钮文案改为「AI答题 | 测试响应」；`AnswerAI.boot()` 在「AI答」未开启时直接返回，不再打印“接口地址/模型名/令牌未填齐”（上游原有的日志噪音）。两类 AI 字段仍平铺显示，未做按类型折叠。
- fix `a352b1d1`：补看遗漏的第三个账号日报 `rpc-failures.2026-09-18.2088942846628038.json`（50 次）：① 好友浇水 `transferEnergy` `ENERGY_INSUFFICIENT` 36 次——原先落入 default 分支继续浇下一个好友，现在自己能量不足即结束本轮浇水；② 1009“系统繁忙”（`neverland.queryItemList`）不再拉起支付宝，`showVerification()` 只在消息含“验证”/`cheating traffic` 时触发（暂停 24 小时的旧行为不变）。
- feat `a352b1d1`：森林新增「找能量」`findEnergyCollect`（默认关，需同时开「收集能量」）：调用 `alipay.antforest.forest.h5.takeLook` 逐个获取推荐好友，进主页交给现有 `collectUserEnergy` 收取；接口与流程对照 AG，来源见详细记录。朋友文件里的「升级发财树领红包」未移植（见详细记录）。
- fix `ac8624fa`：复核 09-19 两个账号异常日报（70+21 次）。① `receiveFarmTaskAward` 102“服务器正在开小差”（`cclyx_3bei_xjcmx_2`、`cclyx_sgbhsd_1c_zm3c`、`cclyx_3bei_dgls_2`、`cclyx_wdhysj_1cV2`、`IP_chouchoule_juankuan`，连续多日每天 8~12 次）：同任务当天第 5 次起退避改 6 小时，前 4 次仍 5/5/30/30 分钟，不永久拉黑；② 我的快递 `KUAIDI_VITALITY` 领奖（无原因，两账号共 15 次，09-17 为 13 次）：失败后当天不再重复领，成功行为不变。暂不处理：48 网络错误（01:50~01:53 集中，已有退避）；`energyRain*` 1009（风控，已暂停 24 小时）；`donation` 218“自营项目没有指定标的物”（1 次，配置项问题，证据不足）；`walk.go`“走慢一点”（业务限速，3 次）；`B_FREE_SEAT`、`TARGET_USER_PROTECT_BY_ENERGY_SHIELD`（正常业务提示）；金豆/`ORCHARD`/`loanpromoweb signin.query` 无原因各 1~3 次（后者较 09-17 的 19 次已大幅下降），证据不足。
- feat `ac8624fa`：版本伪装 `VersionHook` 默认开启，默认版本 10.6.58.8000 / 1881（对齐 GR2026 `AppConfig` 默认值，高于新接口最低支持 10.3.96.8100；AG 无此功能）。仅对**新建**的 `version_config.json` 生效——已存在的配置文件（含旧默认的 `enableVersionHook=false`）不改，需在扩展页手动打开或删除该文件。改版本后需重启支付宝。（更新：“仅对新建配置生效”的限制已由 `307c8080` 的旧默认配置迁移逻辑取消，老用户会自动迁移为开启。）
- feat `ac8624fa`：`RpcRequestGuard` 遇风控 1009/“验证后继续”暂停时调用 `ApplicationHook.showVerification()`，把支付宝拉到前台让验证界面弹出（账号切换中不拉，10 分钟内只拉一次）；`check_rpc_guard.py` 补充：首次触发拉起、已暂停不重复、48 网络错误不拉起。（更新：`a352b1d1` 起收窄为消息含“验证”/`cheating traffic` 才拉起，普通 `1009` 系统繁忙不再拉起。）
- fix `2b11076a`：庄园 `AntFarm.run()`、运动 `AntSports.run()` 的各子任务分别隔离（新增 `step()`），单个子任务抛出异常只记日志并跳过自己，不再中断本轮后续任务。
- feat `2b11076a`：运动同步步数——当前步数超过 18000 不再同步（readDailyStep hook 与主动推送均跳过）。
- fix `2b11076a`：运动同步步数不再被异常打断——`steps.query` 查询失败/被保护暂停时不再让整轮运动任务提前 return，仍继续推送步数；推送遇到临时异常不再当天放弃，下一轮重试（仅接口不存在才标记当天跳过）。
- fix `2b11076a`：`RpcRequestGuard` 请求键对 `enterFarm` 补充 `userId`/`farmId`，好友庄园 enterFarm 失败（繁忙/网络）不再暂停自己庄园的 enterFarm 导致整轮庄园任务被跳过；补充回归。
- merge `6b8c1236`：合并 `origin/MIUIX-api102`（至 4f975462）到 `my_dev`，解决 20 个文件冲突；修复合并后 `AntFarm.competition()` 在 20:01 后跳过分支 `return;` 缺返回值的编译错误（改为 `return true;`），Java/Kotlin 编译通过。

## 2026-09-17

- fix `302b1ad4`：复核每日异常 123 次，修复绿色经营签到 `sceneId` 缺失导致的跨场景退避串扰及日报合并；补充场景隔离和两个庄园繁忙任务回归，保留既有退避，无新增永久黑名单；九项回归与 Java/Kotlin 编译通过。
- fix `cb636a62`：修复合并后普通计数字段保存重置为 1、单选数量无法编辑，以及三级/四级配置页恢复后未初始化；保留同账号未落盘修改，按 MyFix 三项规则复查，九项回归与 Java/Kotlin 编译通过。
- fix `42b7a1fa`（最终确认）：切回 A 确认成功即开始切号冷却，任务立即恢复且独立运行；到期仍等任务结束、首页空闲15秒才切号，移除等待首轮完成的额外状态，九项回归与编译通过。
- fix `42b7a1fa`：冷却仅限制自动切号；返回 A 立即恢复任务，等 A 本轮完成后开始默认2小时/自定义冷却，期间周期任务正常运行且不重置冷却，到期仍检查首页和空闲15秒；九项回归与编译通过。
- fix `42b7a1fa`：自动切号仅允许支付宝首页处于前台且有焦点时发起；二级页面/未知页面/后台暂停并重置15秒计时，补充发起前复核、等待首页状态及回归，九项检查与编译通过。
- fix `42b7a1fa`：自动切号改为 A→B→C→A 后冷却，到期从 A 开始下一轮；游戏上报/打地鼠在异步提交前计入生命周期，修复提前结束提示和切号竞态，补充控制器与异步入口回归。九项检查及 Java/Kotlin 编译通过，ANR 待真机复测。

## 2026-09-16

- fix `7a1b0865`：根据每日异常报告，为庄园领奖 `102`“服务器正在开小差”补齐按账号/场景/任务隔离的 5/5/30 分钟退避；修正 error=0 遮蔽 resultCode，不新增永久黑名单。
- `3601388e` fix：修复电量权限申请的无效回退与无提示；不区分品牌，启动失败统一进入支付宝应用设置并提示，增加手动入口并补充回归，待实机验证。
- `02381191` feat: 新增按账号/GMT+8 日期聚合的 RPC 异常统计 JSON，异常日志页支持“导出统计”；记录任务 ID、错误及次数供人工完善黑名单，不改变现有请求策略。全部日志分类的单条卡片支持长按自由选择复制。
- `33ae1a56` docs：AI 必读规则新增每次合并必查 GMT+8、MyUtils JSON 创建及 `.opt*()` 安全读取，要求覆盖自动合并文件并记录处理结果与例外。
- `2deb0bf3` Merge `origin/MIUIX-api102` 至 `ea6dd5e8`（四个提交）：合入弹窗搜索、配置/统计加载优化及模块仓库发布工作流；保留本地账号标题与整数单位修复，修正上游单选追加旧选择的问题，九项回归及编译通过。
- `408fdd33` fix: 删除信用2101及视频红包（保留好家无忧卡）；黄金票仅保留每周福利并归入会员分组，青春特权仅保留森林道具入口，清理重复/失效实现及专用 Hook；顺带修复本轮结束日志被未来定时任务阻挡（保留已到期/运行中任务等待和切号隔离，自动切号判断不变）。
- `47370579` fix: 修复会员业务拒绝/请求冷却被误判为全局掉线导致未认证账号反复提示“检查超时”并拉起登录页；检查失败/超时/异常统一改为按执行间隔重试，不再强制拉起登录页，区分超时与中断并取消检查线程；顺带修复 RPC guard 在 `error` 为空串时未回退 `resultCode` 的 bug。

## 2026-09-15

- `55a6a696` fix: 日志解析兼容无 `TAG:` 的分类记录，修复森林/庄园/金豆/其他日志被合并为同一卡片而仍显示正序；补充回归检查。
- `dc009afe` fix: 深色模式/跟随系统开关最终效果没变时不再 `recreate()`，不闪页面（切换前后
  各算一次 `effectiveDark`，一样就跳过重建）。
- `4eed8650` fix: 深色模式和跟随系统设置改成双向互斥（开一个自动关另一个）。
- `c7aa63f3` fix: 深色模式开关本身不生效——`followSystem` 判断优先级比 `darkMode` 高，默认
  跟随系统开着时单独点深色模式没有效果，只会白白 `recreate()` 一次；开深色模式时顺手关闭
  跟随系统。
- `1f6ec478` fix: 电量权限按钮真正的闪退原因——`PermissionUtil.checkBatteryPermissions()`
  在独立 App 进程里引用了只有真被 LSPosed 注入进支付宝进程才存在的 `ApplicationHook`
  （其父类 `XposedModule` 是 compileOnly 依赖），触发 `NoClassDefFoundError`（`Error` 不是
  `Exception`，两层 `catch(Exception)` 都包不住）；加个接收 `Context` 的重载绕开。
- `56cc9a38` fix（诊断方向错误，已被 `1f6ec478` 取代真正修复，规则本身不算错保留未撤）：
  怀疑是 R8 混淆导致的崩溃，把 hook 包从只 keep `ApplicationHook` 一个类改成整包 `-keep`；
  重装后同样的崩溃复现，说明根因不在这里，见上一条真正的修复。
- `330c746d` docs: 补全 2026-09-15 三轮合并/修复记录到 `doc/MyFix.md`/`CHANGELOG.md`；新增
  `AGENTS.md`（Claude Code 和 Codex 都会读的项目须知）；顶栏/配置列表账号显示格式调整
  （`C176: 账号` 改 `C176(账号)`；配置列表 UID 一行改用 `ArrowPreference` 的 `summary`
  小字副标题，不再跟标题同号大小挤在一起）。
- `c12a7be2` fix: 电量权限申请崩溃（缺 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest 声明）、
  切深色模式跳回首页（`selectedTab` 未跨 `recreate()` 存活）、切 tab 顶部账号名闪烁（轮询状态
  提到 `MainScreen` 一级共享）。
- `3acad3db` Merge MIUIX-api102：合入上游 `0651e79a`（4 处 `IntegerModelField` 范围收紧/放宽）。
- `92ec28e6` chore: 本地默认版本号改为 1.0.8。
- `ae2b620c` fix: 运行日志"开始执行"等位置一直显示裸账号 UID、从未显示过昵称（`recordUserName`
  内存缓存自己写自己读，从没被真正写入过）；小鸡"自动睡觉"功能（对应接口已失效）整段删除，只留
  自动起床；访问已取关好友的庄园不再每天稳定刷一条"非好友"错误日志噪音。
- `bf3d67cb` feat/fix: 日志页最新条目改到列表顶部显示；电量权限开关迁移到 `AppConfig` 后未设置
  用户会拆箱 NPE 崩溃（新增 `shouldRequestBatteryPermission()` 兜底读旧账号历史值）；配置保存时
  `BaseModel` 精简掉的字段被静默丢弃、农场施肥场景次数迁移；整数配置编辑框绕过单位换算层导致
  保存值错误；偷榜/霸榜"0分钟"死区改为有意义的边界值；新增 `RpcRequestGuard` 统一 RPC 失败保护
  （按账号隔离退避 + GR 已知异常任务黑名单），顺带修了 `RpcEntity`/两套 `RpcBridge` 的三个既有
  并发 bug（`hasError` 未重置、`wait(30_000)` 虚假唤醒误判超时、线程中断被当 RPC 失败记录）。
- `e1a42342` Merge MIUIX-api102：合入上游 `ad353056`（账号切换延迟 1 秒执行 + 日志查看器优化）、
  `c3bf75da`（R8/proguard 精简、`AntMember` 反射调用改直接调用）。自动合并里发现真回归：延迟
  1 秒的账号切换回调跑在 `TaskLifecycle.Work` 作用域外，完全脱离本次会话加的并发保护，已补同
  代际校验修掉。
- `5268cfcf` Merge MIUIX-api102：合入上游 `bded0848`（删水印原生库/`AntInsurance` 模块/旧 UI
  遗留资源）、`335047d8`（R8 混淆启用、config 保存修复、`AntOrchard.getWua()` 改 public）。
- `81236b9e` feat: 顶栏版本/编译时间下面加当前账号一行，配置列表每个账号条目加 UID；`accountDisplayName()`
  改直接读 `self.json`，不再调用会清空全局共享 `userMap` 的 `UserIdMap.loadSelf()`。
- `2e350ce6` feat: 品牌名"芝麻粒"统一补齐"-M"后缀（4+3 处历史遗漏）；日志页改用
  `reverseLayout` + `canScrollBackward` 判断是否贴底跟随刷新；全部任务执行完成后运行日志打印
  "🏁全部任务已执行完成"（按账号世代跟踪，一轮只打一次）。

## 2026-09-14

- `c30facbc` fix: 修复移植审查确认的 17 项问题（切号任务隔离、金豆额度与领奖、视频冷却与调度、
  日志兼容与账号同步、分页/捐赠边界、VPN Hook 初始化、鱼塘时区、版本默认值、Gemini 答案和运动币气泡）；
  新增本地 JVM 回归检查，详见 `doc/MyFix.md` 对应记录。
- `5759d512` feat: 从新版GR快照移植12个独立小额福利任务（dayDaySave/luckCard/factCheck/
  forestPlantRewards/dailyCash/promoprodRewards/wealthDay/youthPrivilege/weeklyWelfare/
  healthIslandRewards/myBankWelfare/other）+ videoRewards 视频红包真实观看验证
  （含新的 Activity.onResume 观察hook + WebView JS注入探针）
- `322e29ca` feat: 日志详情页支持实时刷新（FileObserver 监听文件写入，边执行边看）
- `137cf239` fix: 修复验证码VPN弹窗拦截开关从未生效的问题（`boot()` 整段被注释掉）
- `6a31c9e1` feat: 新增全局自动切号功能（账号轮询，最小间隔2小时）

## 详细记录（自 doc/MyFix.md 迁移）

### 2026-09-19（续）：合并 MIUIX-api102 至 b1293b40

上游 2 个提交：① 广播来源校验（`ApplicationHook.isTrustedBroadcastSender`：Android 14+ 用 `getSentFromUid` 校验，白名单为本进程/模块 App `io.github.aw1y2z.sesame`/adb shell，取不到来源或低版本一律放行）、清理注释掉的旧代码、`BaseModel`/`FileUtil`/`ConfigV2` 统一日志截断；② 捐蛋排位 `competition()` 语义收紧：只有“接口成功但没有排位首页”才返回 false 触发公益捐蛋回退，接口异常/数据缺失/20:01 后跳过都返回 true，避免当天已为排位捐过蛋、晚上又捐一次公益。

冲突及取舍：
- `ApplicationHook`：上游在 `Application.attach` 里直接同步调用 `initSimplePageManager()`；my_dev 此前已把它挪到 `Service.onCreate`（版本伪装覆盖 `alipayVersion` 之后调用，对齐 GR）。保留 my_dev 做法，不在 attach 里再调一次，否则会调用两次。广播来源校验的新增代码自动合并，已核对 `MODULE_PACKAGE_NAME` 与 `app/build.gradle` 的 `applicationId`（`io.github.aw1y2z.sesame`）一致。
- `BaseModel`：上游 `getString` + `StringUtil.truncate`，my_dev 已是 `optString`；取 `optString` + 上游的截断日志。
- `AntFarm`：my_dev 把 `run()` 拆成了 `step()`，上游改的是老结构里的捐蛋回退，git 把两边错位对齐。整块取 my_dev 的 `step()` 结构，把上游对回退逻辑的改动手工搬进 `step("捐蛋")`（仅“确认当天没有排位活动”才回退，回退时打日志）；`competition()` 内部采用上游新语义，`getJSONObject` 改 `optJSONObject` 并对 null 按“有首页但缺榜单，不回退公益捐蛋”返回 true；20:01 后 `return true` 的处理两边一致，去掉我们多余的注释行。

三项必查（对上游新增行做了检索）：GMT+8——无日历/日期/时区代码；JSON 创建——无直接 `new JSONObject(raw)`；JSON 读取——无裸 `.get*()`。无新增例外。验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；十项回归全部通过。未真机验证、未打包。

### 2026-09-19（续）：版本伪装改回默认关闭，新增「验证记录」日志类型

**版本伪装的最终结论**：1.1.5 起默认开启，并经过诊断日志、`Flags` 重载、定向提前伪装等多轮修正。最后一份真机日志（未认证小号，18:01，184719 包，`提前伪装=开`）：开关就绪前 8 次读取是真实版本，`早期伪装(日志模块)2次`，之后 `已伪装173次`、`PackageInfoFlags重载1次`——从支付宝进程的角度看它读到的自身版本几乎全程是 10.6.58.8000，但弹出的仍是需对准图片的滑块。结论：**这台设备/账号上，通过 `PackageManager` 伪装版本不能让服务端改发“滑到最右”的验证码**；类型更可能由服务端按账号风险/设备指纹决定（未认证小号本来就容易走更难的验证），或者服务端看的版本来自我们没伪装的渠道（请求头/UA）。因此改回默认关闭；诊断日志、`Flags` 重载、调用方识别等代码保留。
- 一次性回退：`defaultOffApplied` 标记区分“1.1.5 自动写入的默认值”和“用户改过的配置”，见上方摘要；这是启发式，在 1.1.5 手动开启且没改默认值的用户会被关一次。
- 已知小问题未修：日志“实际版本”行显示伪装值（疑似系统缓存 `PackageInfo` 被就地改写）。
- 没做的：自己处理对准图片的滑块（需要图像识别找缺口，工作量大、成功率不确定）；遇到验证就暂停触发它的功能——用户明确说触发验证的功能（庄园使用美食/亲密家庭请客）**不能暂停**，手动验证一次后功能就恢复，所以只做统计，不做暂停。

**「验证记录」日志类型**：用户要求统计哪些功能会触发弹出验证以便优化，并要求单独一个日志类型方便查看。
- 接入点（对照金豆记录）：`AppConfig.enableCaptchaLog`（默认开）、`Log.captchaLogger`/`runtimeCaptchaLogger`/`Log.captcha`、`FileUtil.getCaptchaLogFile`、`LogType.CAPTCHA`（日志页）、首页 `LogsTab` 的开关行；运行日志里以 `CAPTCHA` tag 出现，可用 tag 筛选。`Log.captcha` 不调用 `countModuleLog`，避免验证弹窗让“本轮无操作”提示失效。
- 事件内容：`验证码弹窗🔍类型[…]#来源[…]#运行中模块[…]#最近请求[…]#文字[…]`。类型按界面文字判定：含“向右滑动验证”=可自动处理；含拼图/对准/缺口/拖动/滑块/图片=对准图片拼图（无法自动处理）；其它=未识别。来源=`CaptchaDialog`（安全 SDK 的弹窗，`SimplePageManager` 已有的 `show()` hook 之后调用）或 `Activity:<类名>`（处理器在 Activity 里没找到“向右滑动验证”但界面有验证相关文字，可能是 H5 里的拼图）。同来源同类型 30 秒去重（处理器重试、Activity 反复 resume）。`RpcRequestGuard` 构造时记录最近 8 个请求方法与时间，`recentRequests(n)` 取最近 n 个。
- 局限：归因是推断——弹窗前最近的请求和运行中的模块只是嫌疑对象；手动操作触发的验证显示为“无运行中模块”；统计计数进程重启清零（每个事件本身在日志里可再统计）；依赖 `CaptchaDialog.show()` hook 与 Activity 文字扫描，若拼图验证走了别的类且界面没有“验证/拼图/滑块/缺口”文字则记不到。未真机验证。
- 三项必查：无日期/JSON 创建，读取无 JSON；GMT+8 无关。回归：十项通过（含 `check_rpc_guard`、`check_standalone_no_xposed_class`）。

### 2026-09-19（续）：多阶段饲料任务改为先做完再领、伪装调用方识别修复、日志页切换 tag 回顶部

**1. 庄园多阶段饲料任务（`0c409ef7`）**：用户反馈饲料任务停在 180/240，AG 会到 240/240 并一次领 240g。日志（1.1.5，18:18 编译）里庄园阶段只有“还有待领取的饲料”，没有任何“饲料任务🧾完成”。原因：上一版按轮执行只是 GR/M 的“做一阶段→领一阶段（30g）”加了循环，`receiveFarmTaskAward` 用 `awardCount + foodStock > foodStockLimit` 判断容量，饲料快满时领不了就停，任务卡在“有奖没领、也不做下一阶段”。对照 AG（`getMultiStageAccumulatedAward`、`mapPhase`）：AG 把“领奖”和“做下一阶段”分开，领不了就先做后面的阶段，奖励累积，最终 240/240 一次领。
- 现：按轮执行时，FINISHED 状态且 `rightsTimesLimit>1`、`rightsTimes<limit` 的多阶段任务先 `doFarmTask` 继续做阶段（用户明确要求“完成任务就行，不用马上领，用了饲料再领”），阶段做不了才退回领奖；全部做满或喂鸡腾出容量后由领奖路径/既有 `checkUnReceiveTaskAward` 一起领。`checkUnReceiveTaskAward` 的单独领奖遍历不变。
- 待领额改用 `pendingAward = awardCount − alreadyReceiveStageAwardCount`（AG `getMultiStageAccumulatedAward`），原先拿累计总额（如 240g）判断容量，会把放得下的待领奖励误判成超上限；差值为 0 时退回 `awardCount`，单阶段任务行为不变。
- `alreadyTried` 按“动作(do/receive/stage)+bizKey+taskId+状态+rightsTimes+待领额”去重；最大轮数 10→20（8 阶段再加领奖轮）。
- 不确定：服务端是否允许有待领奖励时继续 `doFarmTask`（AG 走这条路径，M 未实测，所以保留“做不了才退回领奖”的兜底）；`alreadyReceiveStageAwardCount` 的语义按 AG 的用法推断。未真机验证，`AntFarm` 过大无回归覆盖。三项必查：无日期/JSON 创建，读取全为 `opt*`。

**2. 版本伪装“提前伪装”没生效的原因（`03b52936`）**：`runtime.2026-09-19.<账号>` 日志（18:18 编译）显示 `提前伪装=开`、开关就绪前早读 12 次、`早期伪装(日志模块)0次`，来源栏又变回 `VectorChain/VectorNativeHooker` 框架帧。`callerFrames` 只在栈顶 24 帧里找最后一个 hook 机制帧，LSPatch 加载器等多层 hook 嵌套时框架帧超过窗口，把框架帧当成调用方，`fromLoggingModule` 认不出 `com.alipay.mobile.common.logging.`。改为扫描整个栈、按类名跳过 hook 机制帧（`org.matrix.vector`/`org.lsposed`/`LSPatch_`/`libxposed`）、反射/`ApplicationPackageManager` 帧和本模块帧（R8 短名类不含 `.`、`io.github.aw1y2z` 包）；来源样本相同的只留一条，每类上限 3→6。仍是假设：日志模块被识别并改写后，验证码类型是否会变，取决于 `LogContextImpl` 缓存的版本是否就是决定因素。

**3. 日志查看器切换 tag 回顶部（`dddb5807`）**：`LogScreen` 里 `listState` 与筛选条件互不相干，过滤结果变了滚动位置仍保留。加 `LaunchedEffect(selectedTag, searchQuery) { listState.requestScrollToItem(0) }`；搜索输入每敲一个字也回顶，属预期。无回归覆盖（Compose UI）。

### 2026-09-19（续）：版本伪装排查、庄园饲料任务按轮执行、导出日志文件名带账号

**1. 版本伪装为什么没让验证码变成“滑到最右”（排查过程与结论）**
- 背景：1.1.5 把 `VersionHook` 默认开启为 10.6.58.8000/1881，目的是让服务端下发只需向右滑到最右的简单滑块（`BaseCaptchaHandler` 只识别“向右滑动验证”文字，对需要对准图片的拼图滑块没有任何处理能力，日志里也从未出现过 `滑动验证🆘`）。用户实测弹出的仍是对准图片的滑块。
- 第一层原因：≤1.1.4 自动建的 `version_config.json`（关闭、版本名空、版本号 0）不会被“新建文件才写默认值”的逻辑改写，老用户仍是关闭。`loadVersionConfig` 现把这种未改动的旧默认迁移为默认开启并保存。
- 第二层原因（诊断日志发现）：配置显示“已伪装”，但只有配置加载之后的读取才被改写。新增每轮打印的 `版本伪装诊断` 行（早读/已伪装/关闭/`PackageInfoFlags` 计数与读取来源）。首轮真机日志：开关就绪前支付宝读自身版本 11 次，全是真实版本 12.12.20.8000，全走 `int` 重载；来源栏因 R8 把 hook 框架类混淆成 `yb2/ac2` 而全是框架帧，改为越过 hook 机制帧（LSPosed/Vector/LSPatch/libxposed）取调用方后，第二轮日志显示来源是 `com.alipay.mobile.common.logging.ContextInfo.b < LogContextImpl.<init>`、`logging.util.perf.Judge.<init>`（日志/上下文模块，启动时读一次并缓存，最可能就是发给服务端的“应用版本”）和 `com.alipay.mobile.quinox.startup.UpgradeHelper.getUpdatedTimeFromPackageInfo < upgrade`（升级检查）。
- 修复：① 两个 `getPackageInfo` 重载（`int` 与 API 33+ 的 `PackageInfoFlags`）共用 `handleRead` 改写，`int` 重载内部委托到 `Flags` 时跳过；② `sInIntOverload` 只对支付宝自己的包名置位（探测未安装的微信/QQ 会抛 `NameNotFoundException`，`XHelpers` 此时不执行 `afterHookedMethod`，标志会残留在常驻线程上）；③ `saveVersionConfig` 写回 `earlyEnable`（否则用户手写的会被覆盖抹掉）；④ **定向提前伪装**：`sEarlyFake` 缺省为开（读不到配置就按默认开启、默认 10.6.58.8000，只有配置明确关闭才关），配置加载前对“紧邻 2 个调用帧属于 `com.alipay.mobile.common.logging.`”的读取改写版本，`UpgradeHelper` 等其它早读保持真实版本；`earlyEnable` 缺省为真，写 `false` 可关；⑤ 模块自己记录“实际版本”改用 `readRealVersionName` 绕过伪装。
- 取舍：不做“全部早读都伪装”——`UpgradeHelper` 拿版本做升级判断，读到假版本可能让支付宝以为自己升级/降级而触发重装或清缓存，主力手机风险不小；先只伪装日志模块，若下一轮日志显示日志模块已被伪装而验证码仍是拼图，再逐步放宽。
- 未验证：`LogContextImpl` 缓存的版本是否就是决定验证码类型的那个；`Judge`/`quinox` 也可能才是。生效需要**重启支付宝**（日志模块启动时才读），换号不算。看诊断行里 `提前伪装=开`、`早期伪装(日志模块)N次` 是否大于 0，再看弹出的验证码类型。`audit_regressions` 中伪装取值断言改指向 `earlyName()/earlyCode()`。

**2. 庄园饲料任务按轮执行，全部完成后当天不再查询（对照 AG）**
- 现象：日志里 `试玩庄园火爆小游戏`（每次 30g）在 09:30/09:59/10:04/10:59/11:11/16:17 各做一次，原先每次执行 `listFarmTask(TODO)` + `listFarmTask(FINISHED)` 各一次，多次任务每轮只推进一次，完成后每轮仍继续查询；AG 用 `TaskFlowEngine` 多轮做到没有进展，并用 `FLAG_FARM_TASK_FINISHED` 等标记，且没做完不记完成。
- 做法（不搬 AG 的整套 `TaskFlowEngine`，只取核心）：`runFarmTaskRounds` 每轮一次 `listFarmTask(null)` 同时处理 TODO 与 FINISHED，返回 {需要处理数, 推进数}：需要处理为 0 → 记当日标记 `antFarm::farmTaskAllDone`（`Status` 按账号存储、次日清）；有任务但无推进 → 停、不记标记；有推进 → 再来一轮，最多 10 轮。`alreadyTried` 按“bizKey+taskId+状态+rightsTimes”去重（对照 AG `actionKey`），避免服务端返回成功但状态不变的任务白跑满 10 轮。`receiveFarmTaskAward` 对非饲料奖励 RPC 成功后改返回 true（原返回 false 会让它一直被当成没做完）；不支持 RPC 完成的 bizKey 抽成 `isUnsupportedFarmTask`，不计入需要处理数。
- 代价：当天新出现的任务要等次日；饲料满领不了奖的任务会让标记迟迟不记（与原先每轮都查一致）。范围仅庄园饲料任务，其它模块的一次性任务未动。三项必查：无日历/日期代码、无 JSON 创建、读取全为 `opt*`。未真机验证，`AntFarm` 过大无回归覆盖。

**3. 导出日志文件名带账号**：`FileUtil.exportFile` 对 `log/<userId>/` 下的文件在扩展名前插入 userId（`runtime.2026-09-19.log` → `runtime.2026-09-19.2088702045701743.log`），多账号导出到同一下载目录不再重名/覆盖，与 `rpc-failures.日期.账号.json` 命名一致；文件名已含账号或取不到账号（`default`）时保持原名。日志**内容**仍不写 uid/昵称，只是文件名带账号，分享文件时要注意；用 uid 而非昵称/序号是因为独立 App 进程读不到昵称映射，而账号目录名就是 uid。未真机验证，`exportFile` 依赖 `Environment` 无回归覆盖。

**文档改动汇总补充**：`CHANGELOG.md` 新增本节；此前“本日文档改动汇总”所列 `doc/MyFix.md` 第 5 条与 `AGENTS.md` 一行不变。

### 2026-09-19（续）：没开通/未认证的功能一天最多请求一次；文档改动汇总

**问题**：`runtime.2026-09-19.log`（单账号）196 次失败响应里，150 次是 `com.alipay.antfarm.collectManurePot` 返回 `G04`“肥料已经存满了，去开通芭芭农场种果树吧”：庄园每次同步小鸡状态，肥料罐 ≥100 就去领，两个罐子各请求一次；小号没实名认证开不了芭芭农场，肥料永远存不进去，所以每轮都失败。其余 46 次失败分散在 25 个接口里（48/3 网络错误 30 余次、`receiveFarmTaskAward` 102 六次等），均已被现有退避覆盖。

**过程与取舍**：
- 最初只有用户口述的一句提示，源码里搜不到该文案，误判为芭芭农场任务列表，在 `AntOrchard` 里给失败任务加了当日标记；拿到运行日志后确认来源是庄园肥料罐，撤回该改动（未提交）。随后在 `AntFarm` 里对 `G04` 记当日标记，用户明确要求的是“所有没开通的功能都最多一天一次”，逐点加标记覆盖不了，也撤回（未提交）。
- 最终改在所有 RPC 请求收口的 `RpcRequestGuard`：`isNotOpened(message)` 命中“去开通/请先开通/请先认证/请先实名/未认证/未实名”即该请求暂停 24 小时（`DAY`），按账号隔离，与既有暂停机制、请求保护日志一致。只匹配对本人的提示：文案含“好友”或“对方”一律不算（外部审查指出“对方未实名认证”这类陈述句会被“未实名/未认证”误命中，好友互动接口的请求键又不含目标好友，会把整个接口对所有好友停一天），避免一个好友的状态停掉整个方法。
- 既有回归断言随之调整：`check_rpc_guard.py` 中会员 `NOT_CERTIFIED`“请先实名认证”原为 4 次调用发出 3 次（5/5/30 分钟阶梯），现为 1 次。属有意的行为变化：未认证账号会员相关请求的重试次数减少。
- 新增回归：肥料罐 `G04` 后 `DAY-1` 内跳过、`DAY` 后恢复；“好友未开通该功能”不暂停。

**局限**：暂停 24 小时而非自然日；只有日志里出现过的 `G04` 有实测样本，其它接口的“未开通”措辞需在日志中出现后再补关键词；各模块自己打印的“绿色经营未开通/芝麻信用未开通”是本地判断，不经此规则；未真机验证。

**三项必查**：GMT+8——沿用 guard 既有毫秒计时，无新增日期计算；JSON 创建/读取——本次只读取已有的 `message`/`code` 字符串，无新增 JSON 构造与裸 `.get*()`。

**本日文档改动汇总**（按要求一并记录）：
- `CHANGELOG.md`：顶部 09-19 摘要与本节及“移植找能量”“合并 MIUIX-api102（两次）”“第三个账号日报”等详细记录。
- `doc/MyFix.md`：新增硬性规则第 5 条——`GeminiAI`（含 `AnswerAIInterface`）海外用户正在使用，合并时不能删除，配置 id `useGeminiAI`/`useGeminiAIToken` 不能改。
- `AGENTS.md`：合并说明里加一行“合并时不要删 `GeminiAI`”，指向 `doc/MyFix.md` 第 5 条。
- `gradle.properties` 版本号 `1.1.2` → `1.1.5`（用户已提交为 `2357cd25 v1.1.5`）。

### 2026-09-19（续）：合并 MIUIX-api102 至 93140f06

上游 1 个提交：光盘行动图片「清空」跨进程失效修复（`TokenConfig` 读取数量前先从磁盘重载）、异常捕获收紧、日志用新增的 `StringUtil.truncate` 截断（`BaseModel`、`AntMember`、`AntOcean`、`AntFarm`、`AnswerAI`），`PermissionUtil`、`ExtensionsHandle` 小改。

冲突及取舍：
- `TokenConfig`：my_dev 在 `getDishImageCount` 前新增了 `writeDishImage`/`writeDishImageWithRandomIds`（对齐 GR2026），上游把 `getDishImageCount` 改为 `synchronized` 并先 `reloadDishImageList()`。两侧改动位置相邻，属假冲突：保留 my_dev 两个新方法，`getDishImageCount` 取上游写法。
- `BaseModel`：上游 `catch (JSONException e)` + `e.printStackTrace()` + 截断日志；my_dev 已把解析换成 `MyUtils.newJSONObject`，不再抛 `JSONException`，若照搬会因“从未抛出的受检异常”编译失败。保留 `catch (Throwable e)` + `Log.printStackTrace(e)`，日志采用上游的 `StringUtil.truncate(..., 200)`。
- 自动合并的 `AnswerAI`（`trimForLog` 改用 `StringUtil.truncate`）已核对：`GeminiAI`/`useGeminiAI`/`useGeminiAIToken` 与 AI 类型选项完整保留；`AntMember` 里 `kuaidiForestAward` 改动保留。

三项必查（对上游新增行做了检索）：GMT+8——无日历/日期/时区代码；JSON 创建——无直接 `new JSONObject(raw)`；JSON 读取——无裸 `.get*()`，无对 `opt*` 结果的未判空链式调用。无新增例外。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；十项回归（含 `check_standalone_no_xposed_class.py`）全部通过。未真机验证、未打包。

### 2026-09-19（续）：合并 MIUIX-api102 至 d51b841f

**更正（合并提交 `458b043a` 之后）**：下文“`GeminiAI`/`TongyiAI` 跟随上游删除”以及“`audit_regressions` 移除两项检查、`CustomAI.parseAnswerIndex` 无覆盖”的处理是错误的——`GeminiAI` 海外正在使用。已在后续提交中恢复 `GeminiAI`、`AnswerAIInterface`、被移除的两项回归检查与 `Answers.java.in`，`AnswerAI` 同时支持 GEMINI 与自定义AI（`CustomAI`），`GeminiAI` 的答案匹配重新有回归覆盖；只有通义千问保持删除。教训：上游删除文件时要先确认该文件是否仍有人在用，而不是只看仓库内有没有引用。

上游 7 个提交：庄园捐蛋排位赛不存在时 fallback 到公益捐蛋、农场施肥场景显示名（main→果树、yeb→金钱树）、亲密家庭若干修复、森林合种浇水顺序与空值保护、保护合种浇水量计算、AI 答题重构为可自填的通用接口（`CustomAI`）、日志路径触达 libxposed 类导致 App 闪退。

冲突及取舍（按分叉点 4f975462 双方状态判断，多数为“my_dev 已把 `.get*()` 改成 `.opt*()`/`MyUtils`，上游同处又改了逻辑”，取上游逻辑 + my_dev 的读取方式）：
- `GeminiAI`/`TongyiAI`：my_dev 改过（JSON 创建、OkHttp 单例、选项匹配），上游删除并由 `CustomAI` 取代。确认全仓库已无引用后跟随上游删除，`AnswerAIInterface` 同删。
- `AntFarm`：① 20:01 后捐蛋排位跳过分支——上游机械把 `return;` 改成 `return false;`，但 `competition()` 现在 `false` 表示“排位赛不存在，fallback 到公益捐蛋”，20:01 后属于“排位赛存在只是过了截止时间”，保留 my_dev 的 `return true`，避免 20:01 后又去公益捐蛋；② 庄园答题取上游的空选项提示 + my_dev 的 `opt*` 与空指针防护，`AnswerAI` 已内置兜底取第一项，去掉重复兜底；③ 家庭“顶梁柱特权”采用上游对 `RandomUtil.nextInt` 右开区间的修正（上界传 `size()`/`length()`，原 `size()-1` 永远取不到最后一个），保留 my_dev 的空列表/空对象防护与 `MyUtils`；④ `familyFeedFriendAnimal`：上游删除，全仓库无其它调用，跟随删除；⑤ 家庭分享 `invitedCount` 计数取上游。
- `AntForestV2`：组队合种浇水改用上游的 `queryTeamHomePage()`/`getTeamId`/`isTeam`，真爱合种改用上游按队伍名浇水的写法；四处创建 JSON 保持 `MyUtils.newJSONObject`。
- `AntOcean` 海洋答题：交给 `AnswerAI` 作答（上游），读取用 `opt*`，选项为空/缺失时跳过。
- `AntOrchard`：场景显示名用上游 `getSceneDisplayName`，读取 `optString`。
- `ProtectEcology`：保护合种取上游“未勾选日浇水量则静默跳过”，`waterDayLimit` 保持 `optInt`。
- `ReadingDada`：`opt*` + 空选项提示 + 兜底取第一项。
- `ExtensionsHandle`：取上游合种提示文案（无类型时只显示“可以合种”）。

三项必查（对合并结果中来自上游的全部新增行做了检索）：
- GMT+8：上游新增内容无日历/日期格式化/时区相关代码，无需处理。
- JSON 创建：发现三处直接 `new JSONObject(raw)`——`AntForestV2.queryTeamHomePage`、`CustomAI.requestOnce`、`CustomAI.parseJsonOrNull`，均已改为 `MyUtils.newJSONObject`。`parseJsonOrNull` 原靠解析异常返回 null，改为空对象视为失败并返回 null，语义不变。
- JSON 读取：上游新增内容无裸 `.get*()`，无对 `optJSONObject`/`optJSONArray` 结果的未判空链式调用。
- 例外/遗留：无新增例外。上游 `CustomAI` 是 479 行的新解析逻辑，本次只做了三项规范检查，未逐行审计其解析行为，也未做真机验证。

回归：`checks/audit_regressions/run.py` 原有两项检查绑定在已删除的 `GeminiAI` 上（`getAnswer` 选项匹配规则：精确优先、含小数、歧义拒绝；`getAnswerStr` 不含 `replaceAll` 且 `return answer.trim()`），已一并移除并删除 `Answers.java.in`。**遗留：`CustomAI.parseAnswerIndex` 目前没有回归覆盖**，它依赖多个静态辅助方法，需要单独提取后再补。其余九项及 `check_standalone_no_xposed_class.py` 均通过，`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过。未真机验证、未打包。

### 2026-09-19（续）：移植「找能量」，评估朋友文件的其它新功能

**来源**：朋友给的 `找能量AntForestV2.java`（GR 系反编译代码，包名 `io.github.lazyimmortal.sesame`，6250 行，不能与 M 直接 diff）。按 `ModelField` id 对比，多出 10 项：找能量 `findEnergyCollect`、收能量个人模式 `forcePersonalMode`、升级发财树领红包 `autoMoneyTree`、养绿植赢免单 `autoPlant`、养绿植会员积分 `autoPlantMemberPoint`、IP联名馆 `autoExchangeIPProps`、活力值秒杀 `autoExchangeVitalitySeckill`、不复活能量好友列表 `dontProtectList`、组队动态浇水 `enableRankingTopUp`、能量雨组队模式 `energyRainInTeam`。已有功能的逻辑改动未逐处比对。在 AG、GR2026、Sure-Xu、M 中，只有「找能量」在 AG 有实现；养绿植、IP联名馆、活力值秒杀、能量雨组队四项四处都搜不到。

**移植的「找能量」**（以 AG `collectEnergyByTakeLook` / `AntForestRpcCall.takeLook` 为准，不搬朋友文件的反编译实现）：
- `AntForestRpcCall.takeLook(skipUsers, takeLookStart)`：方法 `alipay.antforest.forest.h5.takeLook`，`source` 取 AG 默认的 `chInfo_ch_appid-60000002`，版本档位对照 AG（>10.6.10 用 `20260107`，>10.5.88 用 `20240403`，其余 `20230501`），跟随伪装后的版本号。M 的 `RpcEntity` 没有 headers，AG 请求头里的 `source`/`ags-source` 未带。
- `AntForestV2.findAndCollectEnergy()`：最多 50 次；服务端返回空 `friendId`、非 `FRIEND` 动作、`takeLookEnd`、连续 3 次重复/自己即停；`hasErrorWait` 或响应失败即停；对每个好友调用现有 `collectUserEnergy(friendId, home, "ordinary")`，因此炸弹阈值、能量罩、`dontCollectMap` 全部复用；有能量罩的好友与 `dontCollectMap` 一起放进 `skipUsers`（值 `baohuzhao`，同 AG/朋友文件），让服务端不再推荐；每个好友间隔 500ms。挂在 `run()` 好友排行榜遍历之后。开关默认关闭，且要求「收集能量」已开。
- 审查后修正（外部审查意见逐条核对，均成立；另按第二轮意见：主页为空对象时不计入浏览数也不清零重复计数，重复/自己跳过前停顿 300ms；`collectUserEnergy` 在“全收”下每次多查一次 `queryHomePage` 是原有逻辑，未改）：`repeat` 连续重复计数在成功浏览后未清零，会被累计到 3 次提前结束，已清零；`hasShield` 在好友主页缺 `now` 时会把已过期的罩判为生效，改为 `hasActiveProp`，`now<=0` 退回本机时间；炸弹卡好友与能量罩好友一样加入 `skipUsers`（AG 为 `hasShield||hasBomb`），避免被反复推荐；`findEnergyCollect` 加 `setDependsOn("collectEnergy")`，关闭「收集能量」时配置页隐藏该开关。
- 跳过的：AG 的 `queryCombineBiz` 预曝光、`takeLookEnd` 结束上报、结束后 `take_look_end_task_list` 领奖、80 次上限（取朋友文件的 50）、异常冷却。原因：没有验证这些对收益有实质影响，先保证主流程；需要时按 AG 补。
- 朋友文件的 `findEnergyCollectDirect` 没搬：它自己重写了炸弹/能量罩判断并使用裸 `getLong/getString`，且依赖的 `shield_skip::` 标记在 M 里没有任何代码写入。

**发财树未移植**：AG 的「摇钱树」（`AntOrchard.receiveMoneyTreeReward`，`moneyTree.trigger`）M 已有等价实现（`AntOrchard.queryYebRevenueDetail` → `AntOrchardRpcCall.triggerYebMoneyTree`，同一接口和参数），无需移植。朋友文件里的「升级发财树领红包」是另一个模块 `model.task.moneyTree.MoneyTreeManager`，源码不在该文件内，GR2026/AG 里也没有，无法移植；需朋友提供 `MoneyTreeManager` 及其 RpcCall。

**三项必查**：GMT+8——无新增日期计算；JSON 创建——`MyUtils.newJSONObject`，响应用 `MessageUtil.checkResultCode` 校验；JSON 读取——全部 `opt*`，无裸 `.get*()`；`skipUsers.put`/`takeLook` 请求体构造属于创建，`JSONException` 由外层 `catch (Throwable)` 处理。无新增例外。

**验证**：编译通过。未做真机验证——`takeLook` 在 M 版本档位下是否可用、返回字段（`friendId`/`actionType`/`takeLookEnd`）是否与 AG 一致均未验证；未新增回归检查（`AntForestV2` 过大，现有 stub 方式不适合）。找能量是主动请求，可能提高风控概率，故默认关闭。

### 2026-09-19：异常日报复核、风控验证拉起、版本伪装默认开启、庄园/运动子任务隔离

**日报复核（两个账号 70+21 次）**：
- 庄园 `receiveFarmTaskAward` 102“服务器正在开小差”，同五个任务连续多日每天 8~12 次：同任务当天第 5 次起退避改 6 小时，前 4 次仍 5/5/30/30 分钟，不永久拉黑（`RpcRequestGuard`）。
- 我的快递 `KUAIDI_VITALITY` 领奖失败（无原因，共 15 次）：失败后当天不再重复领（`Status` 标记 `antMember::kuaidiForestAward`，`AntMember.java`），成功行为不变；`RPC_SKIPPED`（被 guard 暂停）不计为失败。
- 暂不处理：48 网络错误（已有退避）；`energyRain*` 1009（风控，已暂停 24 小时）；`donation` 218（配置项问题，证据不足）；`walk.go` “走慢一点”（业务限速）；`B_FREE_SEAT`、`TARGET_USER_PROTECT_BY_ENERGY_SHIELD`（正常业务提示）；金豆/`ORCHARD`/`loanpromoweb signin.query` 无原因 1~3 次（证据不足）。

**风控验证拉起**：`RpcRequestGuard` 遇 1009/“验证后继续”/“滑动验证”/`cheating traffic` 暂停 24 小时后，调用 `ApplicationHook.showVerification()` 把支付宝首页拉到前台，让验证界面弹出；账号切换中不拉，10 分钟内只拉一次。此前只暂停不拉起，后台时验证页不出现。验证页出现后的处理沿用既有 `SimplePageManager` → `Captcha1Handler`/`Captcha2Handler` → `BaseCaptchaHandler`（仅识别“向右滑动验证”，直接拖到最右）。`check_rpc_guard.py` 新增：首次触发拉起、已暂停不重复、48 网络错误不拉起。**未验证**：拉起后验证页是否出现在 `XRiverActivity`/`AlipayLogin`；验证通过后 24 小时暂停不会提前解除。

**版本伪装默认开启**：`VersionHook` 默认 `10.6.58.8000` / `1881`，与 GR2026 `AppConfig` 默认值一致。GR `strings2.xml` 写明“自动过简单滑块需要支付宝版本在 10.6.58 及以下”，所以这是该功能声称支持的最高版本（不是最低版本；新接口最低版本 10.3.96.8100 与验证码无关）。AG 无此功能。取舍：
- 只对**新建**的 `version_config.json` 生效；已存在的文件（含旧默认 `enableVersionHook=false`）不改，需在扩展页手动打开或删除文件。静态初始值仍为 false，配置加载前的 `getPackageInfo` 不会被误伪装。
- 改版本后需重启支付宝（版本号在进程启动时读取），不能遇到 1009 时临时伪装。
- 伪装只影响下发哪种验证码，不保证不触发风控；也没有验证过服务端对该版本一定下发简单滑块，也可能因版本与其它请求头不一致引入新的风控信号。**未真机验证**。

**第三个账号日报（2088942846628038，09-18，50 次）**：`transferEnergy` `ENERGY_INSUFFICIENT` 36 次（00:57~23:01，已修，见上）；`receiveFarmTaskAward` 102 `IP_chouchoule_juankuan` 3 次（已被 5/5/30 分钟退避覆盖，未到第 5 次，不触发 6 小时档）；`donation` 218 1 次（与另两个账号同一配置问题，暂不处理）；`greenmatrix.love.teamWater` `WATER_ENERGY_NOT_ENOUGH` 3 次、`walk.joinPath` “路线未授权” 3 次、`donate.walk.exchange` `AE0310515401` 2 次、`queryPointCert` `INVALID_MEMBER_GRADE` 1 次：次数少、属业务状态，暂不处理；`neverland.queryItemList` 1009“系统繁忙”1 次：确认该 1009 并非验证提示，收窄 `showVerification()` 触发条件（`check_rpc_guard.py` 增加该断言）。日报文件中文在终端显示乱码，按方法名/错误码及 UTF-8 解码判读。

**子任务隔离与步数同步**：
- 庄园 `AntFarm.run()`、运动 `AntSports.run()` 各子任务分别隔离（新增 `step()`），单个子任务抛异常只记日志并跳过自己。
- `RpcRequestGuard` 请求键对 `enterFarm` 补充 `userId`/`farmId`，好友庄园 enterFarm 失败不再暂停自己庄园。
- 运动同步步数：当前步数超过 18000 不再同步（`readDailyStep` hook 与主动推送均跳过）；`steps.query` 失败/被保护暂停不再让整轮提前 return，推送遇临时异常下一轮重试（仅接口不存在才标记当天跳过）。

**三项必查**：GMT+8——无新增日期计算，日标记沿用 `Status.hasFlagToday`/`flagToday`；JSON 创建——新增解析均走 `MyUtils.newJSONObject`，并用 `RpcRequestGuard.isFailure` 校验失败；JSON 读取——新增均为 `optString`，无裸 `.get*()`；版本配置读写沿用 `opt*`。无新增例外。

**验证**：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；九项 Python 回归在最后一次代码改动后全部通过（其中 `check_merge_config`、`check_rpc_guard`、`audit_regressions` 因内存问题重跑过一次）。中途一次因系统虚拟内存提交额度不足（errno=1455）失败，停掉 Gradle daemon 后重跑通过。未跑 `assembleNormalRelease`、未真机验证、未提交。

### 2026-09-17（续）：每日异常报告复核及绿色经营签到场景隔离

按 `doc/每日异常反馈.txt` 分析当日报告：36 类、123 次失败（GMT+8，00:15–09:30），网络错误 48 次、无原因 42 次、庄园繁忙 25 次、饲料槽满 2 次、行走限制 3 次、安全验证 2 次、能量罩 1 次。报告只有失败次数及首末时间，没有成功响应、每次失败时间或安装版本，不能据此判定现有退避没有生效。

确认并修复：M 与本地 GR 的绿色经营均调用 `signInQuery` 查询 `PLAY102632271`、`PLAY102232206` 两个场景。M 的共享 `RpcRequestGuard` 请求键遗漏 `sceneId`，导致两个场景共用失败累计和冷却，一个场景成功会清掉另一个场景的历史；日报同样遗漏该字段，将 19 次查询失败合在一起。请求键对非空 `sceneId` 追加字段名和值；无此字段的请求保持旧键，保留已有冷却。日报白名单新增 `sceneId`，继续脱敏、截断并按场景分别累计。不改变请求参数，不新增永久跳过规则。旧日报无法反推场景，旧版混合冷却不迁移到任一具体场景；升级后按新键重新累计。回归先复现跨场景成功清空失败，修复后验证三次失败后的暂停、另一场景仍可请求及日报分组。

其他处理结论：

- 庄园 `cclyx_3bei_dgls_2`（8 次）、`cclyx_wdhysj_1cV2`（9 次）、`IP_chouchoule_juankuan`（8 次）均为领奖 `102`“服务器正在开小差”。当前共享 guard 已按账号/场景/任务执行 5/5/30 分钟退避，本地 GR 源码检索未找到这三个 ID 的明确失效规则；给既有回归补入两个新 ID，保留到期重试，不永久拉黑。
- `48` 网络错误已走现有核心/非核心请求退避，不能从跨模块网络失败推断具体任务失效。`cook`、`familyEatTogether` 的 `1009` 已走对应请求 24 小时暂停，需用户在支付宝正常完成安全验证，不绕过验证。
- `monthlyCard` 的 `331` 是饲料槽满，森林采集的能量罩属于业务状态，不停用整个领奖/收能量功能。
- `walk.go` 的“走慢一点”3 次：M 与 GR 都在失败时返回 false 结束本轮行走循环；报告不足以确定服务端限制条件，不新增永久规则。
- 无原因 42 次：绿色经营查询 19 次；`KUAIDI_VITALITY` 13 次；`10021/104322` 1 次；金豆 `GOLDENBEAN_GAME_ZH0_XDDQ`、`GOLDENBEAN_GAME_ZH0_NCDDP_V31`、`GOLDEN_BEAN_TASK_WAKUANG` 各 1 次，`XLIGHT_MIXED_COMPETITION_TASK`、`XLIGHT_MIXED_TASK_DENGHUODOUDI` 各 3 次。M/GR 快递领取参数一致，均在查询后尝试领奖；未见报告任务 ID 对应的 GR 明确失效证据。无法仅凭“无原因”区分未达标、重复领取、接口变化或空响应，暂不拉黑，后续需同时间段脱敏原始响应/任务状态。

三项必查：GMT+8——日期格式化仍显式 GMT+8，冷却继续毫秒计时，无新增日期计算；JSON 创建——将 guard 解析失败后的空对象统一为 `MyUtils.newJSONObject("{}")`，数组解析继续保留异常防护；JSON 读取——新增使用 `optString`，日报仍用 `opt` 并校验标量类型，无裸 JSON get。无新增规则例外；上述证据不足项继续观察。

验证：九项 Python 回归、`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 及 `git diff --check` 全部通过，包含签到场景冷却隔离和日报分组。配置回归首次因缺少 normalDebug 编译产物未能启动，完成编译后重跑通过。未真机验证、未打包、未提交、未推送。

### 2026-09-17（续）：修复合并后的数量编辑及配置子页面恢复

复查未推送的 MIUIX-api102 合并结果后修复两处问题：

- 四级选择页此前只为 `waterFriendList` / `wateredFriendList` 读取数量，其他 `SELECT_AND_COUNT` 字段修改勾选并保存时会将原数量重置为 1。改为所有计数字段均保留原数量并显示数量编辑；`SELECT_AND_COUNT_ONE` 同样支持编辑，初始化仍按 KVNode 读取，不能误走 Map 分支。沿用字段已有范围，浇水仍为 1~3，不因展示滑块改写未编辑的值。
- 三级/四级 Activity 被系统单独恢复时，不能依赖父页面已初始化静态配置。复用 `ConfigPreload` 新增 `ensurePrepared`：模型为空、配置未初始化或账号不匹配才初始化并加载，两个子页面在读取字段前调用；正常同账号跳转保留未落盘的内存修改，空字符串和 null 均视作默认配置。

三项必查：GMT+8——本次无新增日期、日历或时间格式化；JSON 创建——无新增 JSON 创建，继续复用原配置加载；JSON 读取——无新增 org.json 读取，数量读取对象为 Map/KVNode，不属于 JSON API。无新增规则例外或本次修复遗留项；独立 App 继续复用原有模型/配置初始化路径，未新增 ApplicationHook/XposedModule 调用。

回归：扩展 `checks/check_merge_config.py`，运行生产 Kotlin 初始化/保存分支及 Java 初始化门禁，覆盖普通计数字段保值（含 0 和大数量）、修改数量、单选替换、冷恢复、账号变化、默认配置和保留未保存修改；恢复旧数量门禁后检查按预期失败。Java/Kotlin 编译、九项 Python 回归及 `git diff --check` 全通过。并行检查时三项 JVM 启动曾因 Windows 提交内存不足失败，串行重跑全部通过。未真机验证、未打包、未提交、未推送。

### 2026-09-17（续）：合并 MIUIX-api102 至 0dae0755

从 `42b7a1fa`（自动切号冷却不再冻结账号任务）执行 `git merge origin/MIUIX-api102`，合入 `d0755133`"日志页新增搜索/过滤/分享功能，各模块日志增加tag"、`0dae0755`"新增 Miuix 配置三级/四级界面并统一保存策略"两个提交。三个文件冲突：

- `Log.java`：my_dev 侧早先已把各模块 logger 从静态字段改成按账号懒加载的 `getUserLogger()` 工厂方法（多账号日志分目录）；upstream 侧新增了运行日志按 tag 过滤所需的专用 sub-logger（`runtimeForestLogger` 等，写入 runtime.log 但用各模块 tag）以及 `forest()`/`goldenBeans()`/`farm()`/`other()` 的双路写入逻辑（运行日志按查看开关写、模块专属日志按模块开关写，不再经过 `record()` 转发，顺带修掉了 `record()` 内部 `countModuleLog()` 导致的重复计数）。两边都是真实功能、位置重叠但意图不冲突：把 upstream 新增的四个 runtime-tag sub-logger 也改成 `getUserLogger()` 工厂方法（type 都是 `"runtime"`，tag 不同），发现 `getUserLogger()` 原缓存 key 只有 `type::userId`，同一 `type="runtime"` 不同 tag 会互相覆盖缓存拿错 logger——顺手把 tag 也编进缓存 key（`type::tag::userId`）修掉这个新引入的隐患。`forest`/`goldenBeans`/`farm`/`other` 四个方法体采用 upstream 的双路写入结构，logger 调用换成账号感知的工厂方法；`forestLogger()`/`goldenBeansLogger()`/`farmLogger()`/`otherLogger()` 的 flattener 也按 upstream 的新版本加上 `{t}:` 前缀，配合日志页新的按 tag 过滤。
- `MiuixLogViewerActivity.kt`：my_dev 侧是已用 `checks/check_log_follow.py` 验证过的 FileObserver 实时刷新 + 列表不反转（最新在顶部）；upstream 侧新增搜索框、Runtime 页 tag 过滤 Chip、分享按钮。两者互不冲突但改的是同一批代码（`LogScreen`/`LogTopBar`），手动以 my_dev 的刷新/排序结构为骨架，把 upstream 的搜索/tag 过滤/分享功能整体嫁接进去：搜索与 tag 过滤在 my_dev 的 `entries` 基础上派生出 `filteredEntries` 供渲染，`onShare`/`onExportSummary` 两个可选回调都保留。同时清理了合并遗留的重复 `.padding(padding)`。
- `MiuixSettingsActivity.kt`：upstream 是整体架构重写（三级分组页 `MiuixGroupFieldsActivity`、四级选择编辑页 `MiuixSelectionEditActivity` 拆成独立 Activity、字段改原地展开、退出统一落盘策略），my_dev 只有两处局部 bug 修复（整数字段读取改用 `field.configValue` 而非反射转型 `MultiplyIntegerModelField`；单选替换而非累加选中集合）落在被整体替换掉的旧代码里。确认 upstream 这次重写没有带上 my_dev 这两处修复后，直接采纳 upstream 全文件重写，把两处修复移植到新位置：整数字段读取修复重新打在新版 `FieldItem()`（该函数继续留在 `MiuixSettingsActivity.kt`，被三级页复用，还是同一处旧 bug）；单选修复核对后发现 upstream 拆出的新 `MiuixSelectionEditActivity.kt` 本身已经是 `if (single) RadioButtonPreference(...sel = setOf(opt.id)...) else CheckboxPreference(...)` 结构，等价于要修的效果，不需要重复打。
- 自动合并未提示冲突的 `AntForestV2.java`（浇水字段范围 1~3）、`ConfigV2.java`（放开 `getModelFields`/`removeModelFields`）、`ModelConfig.java`（补充 `getCode`）、`SelectAndCountModelField.java`（新增 `valueRangeMin`/`Max`）：核对 upstream 改动，未见裸 JSON get/裸 `Calendar`。

`checks/check_log_follow.py`、`checks/check_merge_config.py` 里断言旧代码具体写法（`itemsIndexed(entries.asReversed()`、`sel = if (single) setOf(opt.id) else sel + opt.id`、`counts.filterKeys { it in sel }`）随上面两处重构失效，按新代码的等价写法更新断言，语义不变（列表仍是不反转+asReversed 渲染；单选仍是替换而非累加；保存仍只落选中项的数量，只是从 `filterKeys` 换成对 `sel` 做 `forEach` 达到同样效果）。

三项必查：合并未引入新的时间/日历逻辑；无新增 JSON 创建；JSON 读取无变化。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过；`checks/` 下九项 Python 回归（`account_lifecycle`、`audit_regressions`、`check_reward_cooldown`、`check_log_follow`、`check_merge_config`、`check_rpc_guard`、`check_gr_followups`、`check_manifest_permissions`、`check_account_switch`）全部通过。未真机验证，未打包，未推送。commit `a1f58a20`。

### 2026-09-17（续）：最终确认切回 A 即开始切号冷却，任务独立运行

按用户最新确认简化起算点：确认返回起始账号 A 且配置初始化成功后立即开始默认7200秒/自定义的切号冷却，同时解冻并恢复任务。无需等待 A 首轮完成再计时；A 的正常周期任务不受冷却影响，也不重置计时。到期后仍须等待运行任务结束，并满足首页有焦点、空闲15秒才切换。删除上一版专为等待首轮完成添加的 ModelTask 世代完成标记、Host.tasksEnabled 和等待状态，保持任务调度与切号计时独立。

回归验证返回 A 立即计时且不持有 freeze、冷却期间任务可执行、到期仍等待任务及首页、冷却不重复启动任务；九项 Python 检查、Java/Kotlin 编译及 diff 检查通过。三项必查：沿用单调时钟，无新增日期/时区或 JSON 创建读取逻辑，无例外。未真机验证、未打包、未提交、未推送。此条覆盖下方“等 A 本轮完成后起算”及“冷却期间冻结”的旧行为记录。

### 2026-09-17（续）：冷却只限制切号，回到 A 本轮完成后起算

按用户确认纠正此前“冷却期间冻结全部任务”的设计：A→B→C→A 后立即 releaseFreeze 并恢复 A 的正常任务；等待 A 返回后的本轮任务真正完成，再开始默认7200秒（可自定义）的切号冷却。冷却期间 A 的森林、庄园等周期任务照常调度，后续任务不会重置或延长冷却计时；冷却到期也不额外重启任务，只在首页有焦点、任务空闲15秒后允许下一次自动切号。冻结仅保留在实际切号和初始化窗口。

复用 ModelTask 的完成监听，发布“当前账号世代至少完成过一轮”标记，避免把 host.resume 后等待调度启动的短暂空闲误判为 A 已完成；旧世代完成标记不能满足新账号。基础模块关闭、无任务可调度时直接进入切号冷却，避免永远等待不存在的任务。关闭/重新激活开关清空等待和冷却；手动切号仍由原账号重载流程同步配置，控制器不再在未冻结时自行初始化。

验证：新增“返回 A 立即恢复”断言在旧实现失败，修改后通过；回归覆盖等待 A 完成后才计时、冷却期间任务可准入、后续任务不重置冷却、到期不重复恢复任务、到期仍等待运行任务和首页、关闭/重启开关、手动账号同步以及基础模块关闭。完成监听回归覆盖世代标记发布和账号隔离。九项 Python 检查及 Java/Kotlin 编译通过；未真机验证、未打包、未提交、未推送。此条覆盖下方旧记录中“冷却期间冻结”的行为说明。

三项必查：冷却沿用 elapsedRealtime，未新增日历/默认时区逻辑；无新增 JSON 创建或读取，无规则例外。

### 2026-09-17（续）：自动切号仅在支付宝首页有焦点时准入

用户要求操作庄园、农场、森林等二级页面时不得自动切号。此前只有登录/身份验证/验证码页面黑名单，普通二级页面仍可通过。控制器新增首页准入：顶层 Activity 必须精确匹配既有 AlipayLogin 首页类名、支付宝处于前台且该窗口有焦点；页面未知、二级 Activity、后台或无焦点均等待，并显示“等待返回支付宝首页，二级页面不切号”。非首页轮询清空15秒计时，返回首页且业务任务空闲后重新等待，不强制跳首页、不要求重跑任务。

冻结后及登录工作线程调用宿主接口前再次检查首页；准备期间离开首页则取消本次发起、解冻并重新等待，不将其当作切号失败永久暂停。已发出的宿主登录不能撤销；结果确认继续沿用独立的登录/验证页判断，避免把新首页准入条件套到正在进行的切号确认上。SimplePageManager.topActivity 改为 volatile，供后台控制线程读取最新页面引用。

验证：控制器回归覆盖未知页面、普通二级 Activity、后台、首页无焦点、返回首页重新计时，以及冻结后才导航离开的窗口；原轮转/冷却/生命周期回归保持通过。九项 Python 检查和 Java/Kotlin 编译通过，未打包、未提交、未推送；页面识别仍需真机验证，类名判断不区分同一首页 Activity 内部的标签页。

三项必查：计时继续使用 elapsedRealtime，不新增日历/时区逻辑；无新增 JSON 创建与读取路径，无 JSON 规则例外。

### 2026-09-17：返回起始账号后冷却，补齐异步游戏生命周期计数

修正自动切号轮次边界：原逻辑是 A→B→C→在 C 冷却→A；现在包括 C→A 在内的轮内切换都在任务空闲后等待15秒，确认返回开启开关的起始账号、初始化成功后才开始整轮冷却。冷却沿用自定义秒数（默认7200），期间保留 TaskLifecycle 冻结，不启动 A 的下一轮任务；到期解冻并触发 A 执行，然后继续轮转。关闭开关清空冷却并恢复任务；冷却中手动换号时先核对宿主身份并加载真实账号配置，避免恢复旧账号配置。

在现有未提交的 GameTask.report / WhackMole.start 生命周期改动上继续修正：Work 在提交异步任务前取得，排队、线程启动、实际执行全程计数；正常结束、执行异常和提交失败均释放。这样游戏上报/打地鼠尚未结束时，完成监听不能判空闲，自动切号的 freezeIfIdle 也不能通过。没有新增主线程等待或固定 sleep。此处修复的是已确认的任务漏计数和切号竞态，无真机 ANR 堆栈，不能据此宣称所有卡顿根因已经消除。

回归：账号状态检查改为验证返回后冷却，直接编译当前生产源码；补充实际控制器配合隔离宿主/时钟的 A→B→C→A、重复轮转、自定义冷却、期间冻结、到期恢复、关闭清空、手动切号和运行任务阻止切号场景。生命周期检查提取真实游戏异步入口，以可控排队/拒绝执行器验证提交前计数、执行/提交异常释放和冻结拒绝。AGENTS.md 列出的九项 Python 回归及 Java/Kotlin 编译全部通过。

三项必查：冷却沿用 SystemClock.elapsedRealtime 单调时钟，不新增日历/默认时区依赖；本次新增修改逻辑没有 JSON 创建或读取，无直接 JSONObject 构造、裸 JSON get 或新增判空例外。未打包、未提交、未推送，ANR 消失与否待真机复测。

### 2026-09-16（续）：合并 MIUIX-api102 至 fab57956

从 `47f099f3`（自动切号轮内15秒/整轮2小时冷却）执行 `git merge origin/MIUIX-api102`，合入 `3c516ddc`"会员积分兑换：获取列表不受开关限制，仅兑换用户勾选的权益"、`fab57956`"设置页添加文件权限引导并修复闪退"两个提交。四个文件冲突：

- `AntMember.java` 三处小冲突是纯风格差异（upstream 沿用未转换前的裸 `get*()`/`new JSONObject()`），保留 my_dev 侧 `MyUtils.newJSONObject` + `opt*()` 写法。`memberPointExchangeBenefit()` 是真实功能冲突：upstream 新增多 deliveryId 依次尝试 + 导航分类码备用接口 `fetchBenefitsFromNavi`、权益列表无条件保存（不再受开关限制）；my_dev 侧已经独立做了"仅兑换用户勾选的权益"（`memberPointExchangeBenefitList` 选择集）但列表刷新仍被开关挡住（调用方 `if (memberPointExchangeBenefit.getValue())` 才调用整个方法）——这正是 upstream commit message 要修的问题。采纳 upstream 的完整结构（多 deliveryId、导航备用、调用方去掉开关前置判断，方法内部先无条件刷新列表、只在开关关闭时提前返回不兑换），按硬性规则把其中裸 `get*()`/`new JSONObject()` 全部换成 `opt*()` + 空指针防护 + `MyUtils.newJSONObject`，包括自动合并未标冲突但同样违规的 `fetchBenefitsFromNavi()`。
- `AntOcean.java` 一处冲突同属风格差异（`seaAreaExtraCollectVO`/`ExtrafishVOs` 取值），保留 my_dev 侧空指针防护写法。
- `MiuixMainActivity.kt`、`PermissionUtil.java` 的冲突是 upstream 把电量权限申请路径回退到了 2026-09-15 已经定位修复的旧版本（独立 App 进程直接构造 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 跳转、无异常兜底；`checkBatteryPermissions()` 无参版本内部会碰 `ApplicationHook` 导致独立进程 `NoClassDefFoundError` 崩溃，见上方硬性规则第 4 条背景）。保留 my_dev 侧 `checkOrRequestBatteryPermissions(context)` 全套异常兜底 + `openBatterySettings` 回退。

三项必查：合并未引入新的时间/日历逻辑；JSON 创建统一改为 `MyUtils.newJSONObject`；JSON 读取统一改为 `opt*()` + 判空，包括自动合并未标冲突但违规的代码。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过；现有 8 套本地回归检查（`check_account_switch`、`check_gr_followups`、`check_log_follow`、`check_manifest_permissions`、`check_merge_config`、`check_reward_cooldown`、`check_rpc_guard`、`check_standalone_no_xposed_class`）全部通过。未真机验证，未打包，未推送。commit `8eb74ad2`。

### 2026-09-16（续）：自动切号支持轮内15秒快速切换与整轮冷却机制

原切号机制将每次切号间隔硬限制为最低 2 小时，导致多账号轮询时每个账号执行完后都要在当前账号空闲硬等 2 小时才切换下一个账号，体感完全没有切号效果。

改造切号状态机与控制器，实现“开启账号即为首个账号、轮内账号间隔15秒切换、一轮结束冷却2小时、手动关闭清空冷却”：
1. **首个账号锚点（`roundStartAccount`）**：在哪个账号打开开关（或 `activation` 递增），以当前登录的 UID 作为本轮起始账号。
2. **轮内15秒切换与整轮冷却分离**：
   - 目标账号非首个账号时（`next != roundStartAccount`），属于轮内切换，账号任务执行完成空闲后仅等待 **15 秒**即切换到下一个账号；
   - 目标账号为首个账号时（`next == roundStartAccount`），代表本轮全部账号已遍历完毕，进入**整轮冷却**（默认 7200 秒 / 2 小时，允许配置 15–86400 秒）。
3. **关闭开关即清空冷却**：手动关闭开关时，清空 `roundStartAccount`、`idleSince` 及全部计时状态，冷却立即解除；再次开启时重新以当前所在账号作为新一轮循环起点。
4. **状态与空闲判断修正**：任务正在执行中（`!idle`）不提前预跑空闲时间，状态显示为 `WAIT_TASKS`（等待当前业务任务结束）；任务完成后细分展示 `COUNTDOWN`（本账号任务已完成，等待切换下一个账号（15秒））与 `ROUND_COOLDOWN`（本轮全部账号已完成，正在整轮冷却）。

三项必查：本次未新增时区计算，沿用单调时钟与毫秒计时；无裸 JSON get；设置保存继续沿用现有原子文件与安全解析。
验证：新增 `checks/check_account_switch.py`，完整覆盖起始锚点、轮内 15 秒切换、整轮 2 小时冷却、关闭清空状态、状态文案与输入限制；十项本地回归与 Java/Kotlin 编译全部通过。

**复核**：对照用户诉求（开哪个号即首轮起点、轮内15秒、整轮2小时冷却、手动关闭立即清空冷却）逐项核对代码与 `checks/check_account_switch.py`，均一致，跑测试确认 PASS。唯一发现 `AccountSwitchState.waitPhase()` 里一处 if/else 两分支返回值完全相同（`roundEnd ? "ROUND_COOLDOWN" : "COUNTDOWN"`），属死代码非功能性 bug；已删除冗余分支与未使用的 `required` 变量，重新编译该文件替换类并重跑 `checks/check_account_switch.py` 仍 PASS，行为不变。commit `47f099f3`。

### 2026-09-16（续）：根据每日异常报告补齐庄园领奖繁忙退避

报告共 10 次失败、9 类记录，集中在约一分钟内。三个庄园抽奖领奖任务 `cclyx_3bei_xjcmx_2`、`cclyx_sgbhsd_1c_zm3c`、`IP_chouchoule_juankuan` 返回 `102` 和“服务器正在开小差”。对照 M/GR 调用及 GR MyUtils，未找到这三个 ID 的明确失效规则；单次繁忙也不足以永久拉黑。

在新旧 RPC 桥共用的 RpcRequestGuard 中，仅将 `com.alipay.antfarm.receiveFarmTaskAward` 的 `102` 且提示以“服务器正在开小差”开头的失败接入既有系统繁忙退避：前两次各 5 分钟，连续第三次起 30 分钟，到期允许重试。复用接口、场景、任务 ID 和账号隔离，不暂停整个庄园，不新增永久黑名单。错误码读取同时修正 `error=0` 遮蔽失败 `resultCode` 的边界，真实非零 error 仍优先。

暂不处理：浇水次数上限已有业务处理；`GYG-huangjinxiaoji` 的 `331` 是饲料槽已满，不代表任务失效；两条 `48` 网络错误已有退避；租赁能量 `generateEnergy` 的“系统异常”和 `KUAIDI_VITALITY` 的无原因失败证据不足，等待后续日报，不据此停用任务。

三项必查：本次未新增日历或日期计算，冷却沿用毫秒时长；生产代码 JSON 创建仍用 MyUtils；错误码读取使用 optString，无裸 JSON get。回归补充三个实际 ID 的冷却、到期恢复、账号/场景/任务隔离、其他接口和普通业务错误不误伤，以及 error=0 回退。未真机验证、未打包、未提交、未推送。

验证：新增场景修复前失败、修复后通过；九项本地回归及 Java/Kotlin 编译全部通过。首次并行检查遇到 JVM 内存不足、配置检查所依赖的编译产物正在更新，待编译完成并限制测试 JVM 堆大小后，两项重跑通过。`git diff --check` 通过。

### 2026-09-16（续）：电量权限申请统一回退到支付宝应用设置

用户反馈魅族 20 PRO 点击电量权限申请完全无跳转、无提示，暂无 Flyme 版本和设备堆栈，不能确认系统静默拦截的具体原因。源码确认旧回退错误：标准申请失败后再次使用 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，却移除了必需的 package URI；异常仅写日志，界面没有反馈。

修复：按用户要求不区分手机品牌，所有手机先走标准申请，启动异常时统一回退到支付宝应用详情并显示操作提示；应用详情也打不开时提示去系统设置手动操作。系统静默吞掉跳转且不抛异常时无法自动判定，使用手动入口直接打开应用详情。权限开关下新增“手动设置支付宝后台权限”，供标准跳转静默无效果或仍需厂商后台授权时使用。始终针对支付宝包名；系统服务不可用时不再误报已授权，启动设置页不等于权限已授予。独立 App 路径继续只使用传入 Context，不调用 ApplicationHook。

验证：扩展独立 App 回归，执行生产方法验证统一应用详情回退、手动入口、已授权短路、服务缺失、目标包名及提示；此前九项回归通过；按用户意见简化回退后，电量权限回归与 Java/Kotlin 编译再次通过。三项必查：本次无时间及 JSON 处理变更。未在魅族真机验证、未打包、未提交。

### 2026-09-16（续）：新增按账号每日异常请求统计文件

在新旧 RPC 桥共用的 `RpcRequestGuard.record()` 接入 `RpcFailureJournal`，在冷却和核心任务豁免返回之前记录真实失败，包含庄园 `resultCode=102` 等未触发退避的失败。成功响应及 `RPC_SKIPPED` 不计数，不修改现有退避、不自动新增黑名单。请求发出时捕获账号日志目录，避免迟到响应记入切换后的账号。统计独立于运行/异常日志显示开关，从新版运行后开始采集，不回填旧日志。

每账号每天生成 `log/<账号>/rpc-failures.yyyy-MM-dd.<账号>.json`，按接口、任务定位字段、各错误码及提示合并，记录次数与首次/最近时间。只保留 sceneCode、taskSceneCode、taskId、taskType、bizKey、recordId、activityId 等白名单字段，不保存完整参数/响应；常见 UID 在字段文本中遮蔽，文件名保留账号标识便于区分。复用 `AtomicConfigFile` 原子替换写入，重启后继续累计；每天最多 1000 类错误，额外未列出的错误计入 `unlistedFailureCount`。I/O 失败只写提示，不中断 RPC 处理。

“查看异常日志”页新增“导出统计”，导出当前账号当天报告到 `Download/sesame-M/`；原始逐条日志导出仍保留。用户每天提供该 JSON，后续按任务 ID 人工确认跳过规则。历史日期报告可从账号日志目录取出。

三项检查：报告日期与时间显式 GMT+8；JSONObject 解析使用 MyUtils；字段读取用 opt 并判空，JSONArray 输入解析保留异常防护。九项回归及 Java/Kotlin 编译通过，新回归覆盖聚合、任务区分、隐去非定位参数、成功/跳过排除、跨天、账号归属及写入失败隔离。Windows 测试替换 rename 操作以模拟 Android 已有原子覆盖语义。未真机验证、未打包、未提交。

### 2026-09-16（续）：日志卡片支持长按自由选择复制

共用 `LogEntryCard` 使用 Compose 原生 `SelectionContainer` 包含标签、时间及正文，全部七类日志支持在单条卡片内长按、拖动选择范围并复制，无新增依赖。九项本地回归和 Java/Kotlin 编译通过，未做真机手势验证，未打包、未提交。本次没有时间或 JSON 代码变更。

同时核对用户截图：前三条完整错误均为 `com.alipay.antfarm.receiveFarmTaskAward`，三个不同 taskId 的抽奖次数奖励请求返回 `success=false`、`resultCode=102`，memo 为“服务器正在开小差，请稍后再试”。当前 guard 将此视为未分类的庄园失败而不暂停；并非 RPC_SKIPPED，也不是截图证明同一个 taskId 重试三次。截图底部果园响应不完整，不能推断同一错误码。本次仅定位，未更改 RPC 退避。

### 2026-09-16（续）：将合并三项必查写入 AI 必读规则

`AGENTS.md` 明确每次合并必须核对 GMT+8、MyUtils JSON 创建、`.opt*()` 读取及判空，覆盖自动合并文件；保留严格解析失败语义和协议时间语义，并要求记录处理结果、例外及遗留项。同步更新本页长期约束，纠正 TimeUtil 仍未统一时区的过时描述。本次仅修改文档，未宣称已修复全部历史代码问题；检查 `git diff --check`，不运行代码回归。

### 2026-09-16（续）：合并 MIUIX-api102 至 ea6dd5e8

从干净的 `my_dev`（408fdd33）执行 merge，合入 `92a0d6b2`、`f38a48ff`、`cf4c1247`、`ea6dd5e8` 四个提交。包含选择弹窗固定标题/按钮和搜索过滤、减少重复配置加载/统计刷新，以及 Xposed 模块仓库发布时按原始提交时间创建附注标签的工作流调整。

冲突按分叉点 `0651e79a` 核对：主页保留 my_dev 的账号标题和 LaunchedEffect；首次文件权限申请保留，授权后统计刷新交给上游 Activity 路径，避免组合阶段重复加载。整数配置保留 my_dev 已有的 `configValue.toIntOrNull()` 与 `setConfigValue()`，覆盖上游同目的单位修复并保留安全解析。BaseActivity 删除重复 `AppConfig.load()`，配置仍在 `attachBaseContext` 调用的 `LanguageUtil.setLocal()` 中加载。

审查修复上游选择弹窗的单选回归：选中新的单选项时替换整个选择集合，多选仍追加；保存只输出已选项的数量。给现有配置回归补充对应源码约束。未认证账号修复、任务清理、本轮结束提示等原有提交保持不变。

验证：九项本地回归、Java/Kotlin 编译通过。未改构建/签名配置，未打包；发布工作流未在 GitHub 执行，未推送远端，未做真机界面验证。

### 2026-09-16（续）：本轮结束日志不再等待未来定时任务

“🏁全部任务已执行完成”原先要求子任务队列完全清空，森林蹲点等未来任务会一直挡住提示。完成监听改为等待生命周期中的运行任务及已到期的未取消子任务；未来定时任务不阻挡本轮结束，也不会被删除或取消。保留原日志文本、运行日志开关、每轮去重、停止和账号世代失效保护。

核对自动切号：`AccountSwitchController.tick()` 使用 `ModelTask.isAllTaskIdle()` 和 `TaskLifecycle.freezeIfIdle()`，原本就只检查已准入的运行任务，不依赖完成日志或子任务队列清空；此次漏打印不会直接造成自动切号失效，切号逻辑不变。

验证：完成日志回归加入一小时后的定时任务，修复前监听无法结束，修复后通过，并确认定时任务仍保留。九项本地回归及 Java/Kotlin 编译通过，覆盖立即待执行/运行中子任务、重叠轮次、停止及切号隔离。未做真机验证，未打包。

### 2026-09-16（续）：删除信用2101/视频红包，黄金票与青春特权入口去重

用户确认“2021”指信用2101，仅删除该任务、配置项和专用 RPC，保留“其他任务”里的好家无忧卡及其请求保护。删除视频红包完整模块、注册、VideoPageObserver/PageSubmissionProbe 和专用 `sesame-page-state.js`；提前调度仍被账号切换使用，保留。原视频回归中的共享福利冷却检查移到 `checks/check_reward_cooldown.py`，删除视频专用检查，同步更新 `AGENTS.md` 的检查命令。

对照本地 `E:\Work\Gr\Sesame-GR2026`：黄金票原在会员，但 GR/M 的 `goldTicket()` 已只剩注释，是失效空入口。删除 M 的旧开关、空方法、旧收取方法和无调用的黄金票 RPC，保留有实际实现的 `WeeklyWelfare`，将其分组由“其他”改为“会员”。类名、字段、查询间隔、当日尝试及冷却键不变，原每周福利配置继续有效；不把旧失效开关自动转换为真实领奖授权。

青春特权按 GR 保留森林 `youthPrivilege` 道具入口，删除独立 `YouthPrivilege` 签到模块及现金活动只读查询。GR/M 森林中的 `studentSignInRedEnvelope()` 调用本来就是注释，本次没有额外开启，所以保留的森林入口仅领取道具，不包含自动签到。用户旧配置文件不作主动清除，已移除模块不再注册或执行。

验证：九项本地回归（视频检查替换为共享福利冷却检查）及 Java/Kotlin 编译全部通过，`git diff --check` 通过。源码检查未发现信用2101、视频红包或独立青春特权残留调用；未做真机验证，未打包、未提交。

### 2026-09-16（续）：RPC guard 空错误码回退修复

审查确认 `record()` 的 `optString("error", resultCode)` 在 `error` 显式为空串时不回退，导致 `resultCode` 中的风控/系统错误等代码丢失。改为先读 `error`，为空再读 `resultCode`，非空 `error` 保持优先。未更改退避档位；guard 原本没有单独处理 `BUSINESS_REJECTED`，不能将这一策略缺口误称为空码回退已经解决。

`checks/check_rpc_guard.py` 增加普通/核心请求中 error 缺失、空串、JSON null 的状态一致性检查，覆盖 `1009`、`SYSTEM_ERROR`、`3000`、`48`、`2000`、`RPC_SKIPPED`，并检查非空 error 优先级与暂停到期。修复前在空串 + 1009 场景按预期失败。上一条关于会员冷却与主调度重登的修复仍保留在当前未提交工作区，并非仅修改底层 guard。

验证：修复后九项本地回归及 Java/Kotlin 编译全部通过，未做设备验证。

### 2026-09-16：修复会员业务拒绝/冷却被误判为全局掉线、反复拉起登录页

**反馈**：未开启自动切号，未认证小号仍反复提示“执行失败：检查超时”，并出现卡死/关闭 App；GR、AG 无同样表现。尚无设备日志，不能据此确认真实响应码或 ANR/崩溃原因。

**代码根因与 fork 对照**：`MAIN_TASK` 每轮都调用会员 `queryPointCert` 做全局检查，与自动切号开关无关。GR 的新 RPC 桥只要响应包含 `success`/`isSuccess` 就不置 `hasError`，M 在 `bf3d67cb` 引入 `RpcRequestGuard.isFailure` 后，`success:false` 的业务拒绝也会置错；但 `AntMemberRpcCall.check()` 仍用 `!hasError` 判断能否运行，导致会员资格/认证等业务失败中断全部任务。累计失败触发的 `RPC_SKIPPED` 也继续被当成检查失败。主分发将所有 false 都写成“检查超时”，随后无条件 `reLogin()`；该方法拉起登录 Activity，恢复回调又 `finish()`，形成反复打开/关闭页面的路径。AG 当前 `runMainTaskLogic()` 核对 UID 后分发任务，没有这条会员接口前置检查。

**修复**：保留现有前置检查，但区分 RPC 错误与有效的业务拒绝；会员接口本地冷却只限制会员请求，不阻断其它模块。无响应、错误 JSON、真实 RPC 错误或已离线仍不通过。检查不通过/异常/真实超时只按执行间隔重试，不再据此强制打开登录页；RPC 层对明确登录过期的处理及“超时重启”开关不变。分开记录中断与超时，保留线程中断标记，并在退出检查时取消 FutureTask，避免可中断的超时检查线程继续持有账号生命周期准入。

**回归**：扩展 `checks/check_rpc_guard.py`，通过生产新旧 RPC 桥、guard 和实际 `check()` 方法构造业务拒绝与连续三次失败后的冷却，旧实现按预期失败、修复后通过（`NOT_CERTIFIED` 仅是模拟测试码，非设备抓包结果）。抽取生产主分发检查代码验证普通失败/异常/超时不会拉起 Activity、超时取消并释放准入、中断不重试。原八套本地检查及独立 App 无 Xposed 引用检查均通过；`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过（仅既有弃用/unchecked 警告）。另验证真实 `error=2000` 仍标记离线，重登广播遵守“超时重启”开关。未做支付宝实机回归，实际卡死/闪退仍需设备日志验证。

### 2026-09-15（续）：修复森林/庄园/金豆/其他日志仍按时间正序显示

**根因**：四类分类日志在 `Log.java` 中写成 `HH:mm:ss.SSS 正文`，而 `MiuixLogViewerActivity.loadLogEntries()` 只识别带 `TAG:` 的格式。分类日志因此被当成续行合并进同一卡片，列表的 `asReversed()` 无法反转卡片内部的记录；运行/异常/抓包日志带标签，所以不受影响。此前仅检查列表反转便判断七类都倒序，遗漏了写入格式与解析格式的差异。

**修复**：共用解析器将标签前缀改为可选，无标签时保留 `null`，由已有卡片标题回退为「日志」。旧文件也能直接按时间戳拆分，继续用现有倒序列表展示；真正无时间戳的续行仍合并到上一条。不改日志写入格式。

**验证**：`checks/check_log_follow.py` 新增四类无标签日志的两条记录及续行场景，修复前因记录未拆分失败，修复后通过；原有带标签日志、500 条上限、实时刷新和滚动跟随检查保持通过。其余七项本地回归检查全部通过，`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过。未安装到真机验证。

### 2026-09-15（续）：电量权限崩溃真根因 + 深色模式/跟随系统开关体验修复

**电量权限崩溃排查了两轮**：第一轮看到崩溃堆栈里全是短名字的类（`bb1`/`nx0`/`p9`/`ip`/`v31`/`ff0`/`xo`/`n5`），误判成是支付宝那边被 R8 混淆坏了引用，把 `hook.**` 包从只精细 keep `ApplicationHook` 一个类改成整包 `-keep`（commit `56cc9a38`）——事后验证 `mapping.txt` 确认 `hook` 包一直就没被重命名过，这次"修复"方向从一开始就错了，重装后同样崩溃复现证明了这点。第二轮才找对地方：这个崩溃堆栈其实全程发生在**独立 App 自己的进程**里（那些短名字是本 App 自己被 R8 混淆后的 Compose 内部调用链，不是支付宝的类），根因是 `PermissionUtil.checkBatteryPermissions()`（无参版本）内部调用了 `ApplicationHook.isHooked()`/`getContext()`——已经写进上面「硬性规则」第 3 条。修法（commit `1f6ec478`）：给 `checkBatteryPermissions` 加一个接收 `Context` 的重载，独立 App 这条路径（`checkOrRequestBatteryPermissions` 已经有调用方传入的 `Context`）直接用新重载，完全不碰 `ApplicationHook`；原来的无参版本保留给唯一另一处调用方（`ApplicationHook.java` 内部、真实注入进程里的调用），内部改成先拿 `ApplicationHook.getContext()` 再委托给新重载，行为不变。新增 `checks/check_standalone_no_xposed_class.py` 静态守住这条路径不会再引用 `ApplicationHook`。

**深色模式/跟随系统设置体验问题**（用户连续反馈了三轮，逐层修）：
1. `c7aa63f3`：`MiuixBaseActivity.setAppContent()` 判断主题时 `followSystem`（默认开）优先级比 `darkMode` 高，单独点"深色模式"开关不会有任何效果，只会 `activity.recreate()` 一次让页面闪一下——改成点深色模式时如果跟随系统还开着就顺手关掉。
2. `4eed8650`：上一条只处理了单向联动，补上反方向——开"跟随系统设置"时也顺手关掉"深色模式"，两个开关做成真正的双向互斥，开关显示状态跟实际生效的优先级逻辑保持一致。
3. `dc009afe`：即使联动关系理顺了，仍然存在"最终效果没变也无条件重建"的情况（比如系统当前就是浅色、跟随系统开着，这时候切换不会改变任何视觉效果）。加了 `effectiveDark(follow, dark)` 帮助函数，用 `isSystemInDarkTheme()` 拿系统当前深浅色，切换前后各算一次最终生效的深浅色，只有真的变了才 `activity.recreate()`，两次算出来一样就跳过。

### 2026-09-15：MIUIX-api102 三轮合并 + 账号切换/日志/RPC 一批修复

一整天里 `MIUIX-api102`（原分支/PR 主线）陆续推了新提交，`my_dev` 分三轮 `git merge` 合入，中间穿插自己在这条线上做的功能和 bug 修复。commit 顺序（旧→新）：`2e350ce6` → `81236b9e` → `5268cfcf`（merge）→ `e1a42342`（merge）→ `bf3d67cb` → `ae2b620c` → `92ec28e6` → `3acad3db`（merge）→ `c12a7be2`。

**合并冲突的通用处理原则**：每次冲突都先查 `git show <merge-base commit>` 确认双方各自改了什么，而不是直接二选一。实测下来几乎所有冲突都属于两类：(a) `my_dev` 在 base 之后新加的功能/修复，upstream 那一侧其实是分叉前就没变过的旧代码，纯属改动位置相邻导致的假冲突——直接取 `my_dev` 侧；(b) upstream 做的是真清理（删除死代码/未使用资源），先用 `grep` 确认 `my_dev` 这边也真的没人再用，确认了才跟着删，不盲目信任任何一侧。

**第一轮合并**（`5268cfcf`，合入 upstream `bded0848`"项目清理与优化"+`335047d8`"优化配置及R8压缩启用"）：
- upstream 删除了水印原生库（`libsesame.so`+JNI 相关）、`AntInsurance`/`AntInsuranceRpcCall`（已废弃保险模块）、一大批旧 Compose 迁移前的 UI 资源（drawable、多语言 strings，从未被新 UI 引用）——确认无引用后全部跟进删除。
- `BaseModel` 的 `batteryPerm`（后台运行权限）、`recordLog`（记录日志）两个 `ModelField` 被 upstream 删掉；确认这两个字段在 `my_dev` 这边也早就是死代码（`batteryPerm` 的真实读取点是 `AppConfig.INSTANCE.getBatteryPerm()`，跟 `BaseModel` 这份重复申报的字段完全没关系；`recordLog` 全仓库没有任何 getter 调用点）——跟进删除，`closeCaptchaDialogVPN` 字段是这次会话自己加的真实功能，保留。
- `app/build.gradle` 的 ABI 拆分（只出 arm64-v8a）、`proguard-rules.pro`（精细化 `allowoptimization,allowobfuscation` 版本）两处冲突，保留 `my_dev` 已经用 `assembleNormalRelease` 实测能正常出签名包的版本，没有换成 upstream 那版更粗放的"整包 keep"写法。
- `AppConfig.java` 的 getter 冲突是双方各自新增了不同配置项（`closeVerification`/`closeErrorFunction`/`closeUnRpc` vs `batteryPerm`/`setBatteryPerm`），两边都保留。

**第二轮合并**（`e1a42342`，合入 upstream `ad353056`"账号切换延迟+日志查看器优化(PR#2)"+`c3bf75da`"R8精简及代码清理"）：
- `MiuixLogViewerActivity.kt` upstream 独立实现了一套几乎同功能的日志跟随机制（`revision` 计数器+`isScrollInProgress` 判断+前台 `Lifecycle` 门控+`stamp` 轮询），保留 `my_dev` 这边已经用 `checks/check_log_follow.py` 验证过的 `FileObserver` 事件驱动版本（upstream 版本有个潜在时序缝隙：手势进行中新数据到达时 `browsingHistory` 取的是滞后值）。
- **自动合并里揪出的真回归**：`ApplicationHook.java` 里账号切换那段，upstream 把 `initHandler(true)` 包了一层 `postDelayed(1000ms)`，但这层延迟跑在原来 `TaskLifecycle.Work` 的 try-with-resources 作用域**外**——包着它的 work 在方法 `return` 时就关掉了，1 秒后真正执行时完全脱离本次会话加的账号切换并发保护。补了同代际校验（复用 `execDelayedHandler` 已有的 generation 模式），在回调里重新 `TaskLifecycle.enter(switchGeneration)`。
- `proguard-rules.pro` upstream 又重写了一版（仍是粗放"章节化 keep everything"风格），继续沿用 `my_dev` 精细化版本。
- `AntMember.getWuaByReflection()`（25 行反射调 `AntOrchard` 私有方法）被 upstream 删掉改成 `new AntOrchard().getWua()` 直接调用，配合 `AntOrchard.getWua()` 私有改 public——干净的质量改进，跟进。
- 合并产生了 `import kotlinx.coroutines.{Dispatchers,delay,withContext}` 重复引入（一份来自 `my_dev` 原有位置、一份来自 upstream 新增位置），顺手去重。

**账号切换权限与配置迁移**（`bf3d67cb`，upstream 侧改动，会话内 review 后确认正确性）：
- `AppConfig.batteryPerm` 默认值从 `true` 改 `null`（区分"未设置"和"显式关闭"）后，`ApplicationHook` 里原来 `if (AppConfig.INSTANCE.getBatteryPerm() && ...)` 对没碰过这个新开关的用户会直接 `Boolean` 拆箱空指针崩溃。新增 `AppConfig.shouldRequestBatteryPermission()`：新开关设过就用新值，没设过回退读旧账号 `BaseModel.batteryPerm` 字段在 `config_v2.json` 里的历史值（哪怕这个 Java 字段本身已经在第一轮合并里删掉，JSON 里的旧值还在），UI 显示和实际权限检测两处都切过去了。
- `ConfigV2` 保存配置时，`BaseModel` 不再声明 `batteryPerm` 字段这件事本身会导致旧账号 JSON 里的历史值在下次保存时被静默丢弃——加了段兼容逻辑，没被当前模型声明的字段原样保留在保存结果里，配合上一条的读取兜底，不会丢用户历史选择。
- `AntOrchard` 农场施肥从"全局统一次数"字段改成 `SelectAndCountModelField`（每个场景各自配次数），`ConfigV2` 配了旧数据（纯场景名列表+全局次数）到新数据（场景→次数 Map）的迁移代码。
- `MiuixSettingsActivity.kt` 整数配置编辑框原来用 `field.value`/`setObjectValue()` 直接读写内部值，绕过了 `toConfigValue`/`fromConfigValue` 转换层——`IntegerModelField.MultiplyIntegerModelField`（比如执行间隔，界面显示分钟、内部按毫秒存）这类字段被这么改过一次后保存值就是错的。换成 `field.configValue`/`setConfigValue(string)` 走完整的字符串往返路径。
- 偷榜/霸榜"提前分钟数=0"原来等于开关虽开但永远不触发（数学上 `startTime==targetTime` 区间必空），是个不易察觉的死区；改成 0=全天霸榜 / 0=20:00 准时偷榜，范围上限同步从 240 放宽到 1200 分钟。
- 新增 `RpcRequestGuard`：按账号隔离的请求去重 key（剔除随机 `outBizNo`/时间戳等噪音字段）+ 分级退避（森林/庄园"核心"请求更宽松，其它请求更严格）+ GR 快照里已知异常任务的硬黑名单，新旧两套 `RpcBridge` 统一接入。顺带修了三个既有并发 bug：
  1. `RpcEntity.setResponseObject` 不清 `hasError`，重试第二次成功后仍会读到第一次失败留下的标记；
  2. `NewRpcBridge.newAsyncRequest` 用单次 `wait(30_000)` 判断回调，`Object.wait` 超时返回不等于条件真的满足（虚假唤醒），改成 `while(!hasResult) wait(remaining)` 真正的条件循环；
  3. 任务取消触发的 `InterruptedException` 原来统一走 `catch(Throwable)`，会被当成一次 RPC 失败记录进新加的 guard 里，还会继续 `sleep` 重试而不及时退出——单独识别中断直接 `return`，这条直接关系到本次会话早前加的 `TaskLifecycle` 取消语义会不会被这层新代码破坏。
  4. `newAsyncRequest` 之前一直没调 `RpcIntervalLimit.enterIntervalLimit`，同步版本有限流、异步版本没有，这次补齐一致。
- `AntForestV2` 收能量逻辑加了对 guard 跳过时返回的 `RPC_SKIPPED` 哨兵值的识别，避免把"没真的发请求"误判成服务器返回的真实错误去跑等待/拉黑逻辑。
- 广告类 RPC（`com.alipay.adexchange.*`）的调试日志改用 `RpcLog` 摘要+脱敏，不再整包 payload（可能几十 KB、含 session 信息）落盘。
- 日志页从"最新在底部"（`reverseLayout` + 滚到底部）改成"最新在顶部"（不反转布局，`entries` 本身倒序），是用户明确要求的调整，不是 bug。

**日志昵称显示 + 小鸡睡觉 + 非好友噪音**（`ae2b620c`）：
- `MyUtils.recordUserName()` 的内存缓存 `mUidMap` 是个自己写自己读的死代码：只有已经命中缓存才会顺手回写 `SharedPreferences`，但从来没人真正往缓存里塞过昵称，导致运行日志"开始执行:xxx"这类位置的 xxx 从建仓库以来就一直是裸账号 UID，从没显示过好友昵称。改法：`UserIdMap.add()`（账号信息任何来源被加载/更新时都会走这里）顺手把昵称写进 `SharedPreferences` 持久缓存；`recordUserName()` 优先取内存里最新的 `UserIdMap` 记录，没有再退化到持久缓存，都没有才落回裸 UID。空昵称更新不覆盖已缓存的好名字，相同昵称不重复写盘。
- 小鸡"自动睡觉"整个功能删掉（对应支付宝接口已不可用，不是这次评估要不要保留的重点），只留自动起床，相关字段/日志文案改名去掉"睡觉"歧义。
- 访问已经解除好友关系的庄园时，`enterFarm` 返回 `memo=非好友,resultCode=302`——这个情况每天必然稳定复现且没法修，之前每次运行都当成普通错误刷一条 error 日志，纯噪音。新增 `RpcRequestGuard.isNonFriend()` 识别后静默跳过，不影响后续对其它好友的正常赠送流程，其它失败原因不受影响仍照常记录。

**第三轮合并**（`3acad3db`，合入 upstream `0651e79a`"修正 IntegerModelField 范围限制"）：
- `advanceTime` 下限从 `Integer.MIN_VALUE` 改 0、`ornamentsDressUpDays` 加 1-30 天范围、`closeShopTime` 加 1-1440 分钟范围——三处直接干净合并，默认值不变。
- `AntOrchard.java` 一处冲突：upstream 想把 `orchardSpreadManureCount`（单一全局施肥次数字段）上限从 100 放宽到 200，但这个字段在第二轮合并涉及的功能里已经被拆成按场景各自配次数的 `SelectAndCountModelField`，字段本身不存在了——保留 `my_dev` 侧结构。upstream"上限放宽到 200"这个诉求目前没有对应落点：每场景次数用的是 `MiuixSettingsActivity.kt` 里 `SelectionDialog` 通用组件的滑杆，写死 `0..100f`，这个滑杆是 `rpcRequestList` 等好几个字段共用的，不是 orchard 专属，没有顺带改，只在这里记一笔，以后有需要再单独给 `SelectAndCountModelField` 加个可配置上限参数。

**账号切换电量权限崩溃 + UI 状态丢失**（`c12a7be2`）：
- `AndroidManifest.xml` 一直没声明 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`——设置页"立即申请权限"按钮跳系统电量优化豁免弹窗时直接崩溃，补上声明。
- 深色模式/跟随系统开关会调 `activity.recreate()` 重建 Activity，`MainScreen` 里的 `selectedTab` 用的是 `remember`（不跨重建存活），一重建就被清零，界面从当前 tab 跳回首页——改用 `rememberSaveable`。
- 顶栏"当前账号"信息原来是每个 tab 各自的 `TabTitleRow` 内部单独 `remember`+起一个轮询 `LaunchedEffect`，切 tab 相当于该 `TabTitleRow` 实例重新进入组合，状态从"未知账号"重新读起，读到真实昵称前会有一瞬间的闪烁。把轮询提到 `MainScreen` 一级共享一份状态，四个 tab 都吃同一份 `currentAccount`，不再各自归零重来。

**UI 显示格式微调**（会话内做了但截至写这条记录时还没提交，跟着下一次提交走）：
- 顶栏/配置列表账号显示格式从 `showName: account` 改成 `showName(account)`（如 `C176(17608062578)`）。
- 配置列表每个账号条目下面的 `UID: xxx` 那一行，从跟标题拼在一起的同号大小文字改成 `ArrowPreference` 自带的 `summary` 参数（小字副标题样式，跟"已选 N 项"那类提示一致）。

**新增的本地回归检查**（这一天累计新增 5 个，加上之前已有的凑成完整一套）：`checks/check_merge_config.py`（配置迁移/电量权限/分钟语义回归，编译产物直接验证）、`checks/check_rpc_guard.py`（编译生产 `RpcRequestGuard`/两套 `RpcBridge`/`RpcEntity` 代码，用字节码级源码替换把 `System.currentTimeMillis()` 换成可控时钟，隔离账号状态跑真实退避/黑名单/跨账号隔离场景）、`checks/check_gr_followups.py`（昵称缓存刷新链路+非好友静默跳过后不影响后续赠送）、`checks/check_manifest_permissions.py`（守住权限声明和实际调用点一致，防止重复踩 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 这类坑）。全部跟已有的 `checks/account_lifecycle`、`checks/check_log_follow.py`、`checks/check_video_rewards.py`、`checks/audit_regressions` 一起构成完整回归套件，每次改动后都跑齐这八套 + 编译 + `:app:assembleNormalRelease`（R8+签名）确认没有回归。

### 2026-09-14：修复 AG / GR / XU / 新版 GR 快照审查确认的 17 项问题

基于 `e28f3db4` 的只读审查后，按用户要求修复全部 17 项。以下记录补充并更正旧条目中关于“切号只靠 BUSY 足够”“恢复 boot 即可启用 VPN 弹窗开关”的结论；旧记录保留。

| 编号 | 问题 | 实际修复 |
|---|---|---|
| 1 | 自动切号仍可能与旧任务并发 | 新增共用 `TaskLifecycle` 准入计数，主分发、模型、正在执行的子任务及初始化均纳入；仅空闲时冻结，初始化结束后恢复，旧代延迟回调失效。取消请求不会提前释放仍在运行的任务。初始化失败保持冻结，避免在错误账号继续请求。 |
| 2 | 芝麻粒换豆回查失败漏记额度 | 服务端确认兑换成功后立即累计当日额度，再执行回查。 |
| 3 | 视频提速清掉拒绝冷却 | 公共福利基类拆分普通查询时间与服务端冷却；提速只清普通间隔。首次迁移保留旧共享键截止时间，版本变更不清服务端冷却。旧键不能区分两类等待，因此升级首次可能保留一次普通等待。 |
| 4 | Android 8～9 日志监听构造函数不兼容 | 改用支持最低 API 26 的路径字符串构造函数，Lint 的该项 `NewApi` 错误消除。 |
| 5 | 独立 App 日志账号错位 | 宿主原子发布当前日志 UID；读取端跟随该 UID，详情页每秒检查账号/日期变化后重新监听；目录名做边界校验。 |
| 6 | 账号子目录日志清理失效 | 清理遍历根目录历史日志及一层账号目录；今日超限和指定类别使用清空文件，保留正在写入的文件句柄。日志日期统一 GMT+8。 |
| 7 | VPN 弹窗 Hook 缺初始化 | `BaseModel.boot` 先调用 `CaptchaHook.setupHook(classLoader)`，再同步配置开关。 |
| 8 | 绿色金融重复请求第 0 页 | 处理当前页后判断末页；缺失、负数或不前进的游标停止分页，同时修复原先跳过末页好友的问题。 |
| 9 | 生态/古树捐赠超过配置总额 | 追加捐赠扣除历史累计量及本轮首捐；未知累计量不推定为零，配置缺失或首捐失败停止。差额使用 long 避免减法溢出。 |
| 10 | 金豆漏领奖、失败后整日不再重试 | 完成任务后继续领奖，失败保留未解决状态；取消整个模块的当日完成短路，各业务按服务端状态及独立额度去重。 |
| 11 | 单开视频预约不执行 | 预约开关加入任务查询前置条件。 |
| 12 | 视频提前调度不唤醒任务 | 清普通间隔后请求主调度，合并重复请求；正在执行分发或模型时延后，账号代数变化丢弃旧请求。 |
| 13 | 暂停/结束帧覆盖达标证据 | 仅合格播放快照替换缓存；暂停、未达阈值等无效帧不覆盖，读取仍校验新鲜度和账号。观察异步回调绑定原账号。 |
| 14 | 鱼塘签到日期使用设备时区 | 独立日期格式化器显式使用 GMT+8、Locale.ROOT。 |
| 15 | 留空的伪装版本没有进入 Hook | PackageInfo Hook 与展示统一读取已有默认版本 getter。 |
| 16 | Gemini 清洗改变小数/误选 | 保留回答原文与标点；优先精确匹配，多选项包含匹配时拒绝猜测。 |
| 17 | 运动币气泡检查错 JSON 层级 | 从每条气泡读取并校验 `assetId`，不再检查父对象。 |

回归检查使用生产类或抽取的实际方法，配合最小模拟依赖，不请求支付宝接口。运行命令：

```text
python checks/account_lifecycle/run.py
python checks/audit_regressions/run.py
python checks/check_video_rewards.py
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleNormalDebug :app:lintNormalDebug --console=plain
```

验证结果：以上三组回归检查全部通过；`account_lifecycle/run.py --baseline` 在旧生产实现上按预期复现 `queued MAIN_TASK escaped admission`，当前实现通过。最终独立 `:app:assembleNormalDebug` exit 0，生成 `app/build/outputs/apk/normal/debug/app-normal-arm64-v8a-debug.apk`。`git diff --check` 通过。

Lint 已重新运行，日志页 API 错误已消除；全库仍有 **2 errors / 286 warnings / 2 hints**，失败项是原有的 `MiuixMainActivity.kt:187` 广播注册缺少导出标志（`UnspecifiedRegisterReceiverFlag`）和 `values/strings.xml:144` 的 `module_description` 缺中文翻译（`MissingTranslation`），均不在本次 17 项中，未顺带修改。报告：`app/build/reports/lint-results-normalDebug.html`；本地命令日志：`build/audit-assemble-final.log`、`build/audit-verification-final.log`。未做支付宝实机回归；模拟检查不代表服务端业务已实测成功。本次修改尚未提交。

### 2026-09-14：`Privilege.java`/`FriendWatch.java` 收尾修复，全仓库 `.get*()` → `.opt*()` 转换任务完成

全仓库最终扫尾审计（`grep -rn` 排除注释行）额外发现两处此前遗漏的调用点：

- `Privilege.java`：`getForestTasks` 原来包一层 `try { return ....getJSONArray("forestTasksNew"); } catch (JSONException e) { ...; return null; }`，内部已经是 `optJSONArray`（返回 null 语义与 catch 分支等价），直接去掉整个 try/catch，改为一行直接 `return`。顺带清理了该文件里一段此前遗留的重复 import 块（`JSONArray`/`JSONObject`/`JSONException` 各多导入一次），因为 `JSONException` 不再使用一并删除。
- `FriendWatch.java`：`updateDay()` 里 `joFriendWatch.getJSONObject(id)` 改 `optJSONObject(id)`，加 `if (joSingle == null) { continue; }` 空指针防护。

之后做了一次全仓库地毯式复查：`grep -rn` 匹配 `.get(String|Int|Long|Double|Boolean|JSONObject|JSONArray)\(`，逐条人工分类剩余匹配，确认全部属于范围外（`android.os.Bundle`、`Context.getString(int)` 资源串、`SharedPreferences.getBoolean/getString`、项目自有 `RuntimeInfo.getLong(...)`）或纯注释/已确认的死代码块，**没有任何遗漏的可执行 `org.json.JSONObject`/`JSONArray` 裸 `.get*()` 调用**。

至此，本次跨会话的全仓库 `.get*()` → `.opt*()` + 空指针防护任务（原始统计约 1986 处调用点）全部完成，覆盖 AntFarm/AntForestV2/AntSports/AntStall/AntMember/AntOcean/AntOrchard/AntDodo/ProtectEcology/AntBookRead/OmegakoiTown/ForestChouChouLe/GreenFinance/AncientTree/AntInsurance/ExtensionsHandle 共 16 个主文件，以及 FishTask/ReadingDada/BaseTaskRpcCall/AntSportsRpcCall/AreaCode/MessageUtil/WhackMole/BaseModel/JsonUtil/RuntimeInfo/Privilege/FriendWatch 等零散小文件。编译通过（`compileNormalDebugJavaWithJavac` 全程 exit 0）；**全程仅做了编译期验证，未在设备上做过任何运行时测试**，合并/使用前建议实机走一遍关键流程（森林/庄园/保护地/新村文旅等常用任务）。

### 2026-09-14：`ExtensionsHandle.java` `.get*()` → `.opt*()` + 空指针防护，第十六个文件全部完成

第十六个文件（248 行，原~23 处调用点，一次会话内全部转完）。覆盖 `getNewTreeItems`/`queryTreeForExchange`/`getTreeItems`/`getTreeCurrentBudget`/`queryAreaTrees`/`getUnlockTreeItems`（新树上苗提醒、树苗余量查询、未解锁地区/项目提醒等扩展信息展示功能）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到剩余的小文件（10 处以下），逐个处理。

### 2026-09-14：`AntInsurance.java` `.get*()` → `.opt*()` + 空指针防护，第十五个文件全部完成

第十五个文件（137 行，原~25 处调用点，一次会话内全部转完）。覆盖 `gainSumInsured`（保障金领取，含遍历 JSON 动态字段用的 `jo.get(key)` 也一并改成 `jo.opt(key)`——这是本轮唯一一处不带类型后缀的裸 `get`，同样会在字段缺失时抛异常，判断属于同一类问题一并处理）/`gainMyAndFamilySumInsured`、`lotteryDraw`（天天领取保障福利）、`beanSignIn`/`beanExchange`（安心豆签到与兑换，含四层嵌套取值链 `result.rspContext.params.exchangeDetail`）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `ExtensionsHandle.java`（约 23 处调用点）。

### 2026-09-14：`AncientTree.java` `.get*()` → `.opt*()` + 空指针防护，第十四个文件全部完成

第十四个文件（146 行，原~26 处调用点，一次会话内全部转完）。覆盖 `ancientTreeProtect`/`districtDetail`（区划信息与古树列表遍历、保护动作前的能量与配额校验）。`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntInsurance.java`（约 25 处调用点）。

### 2026-09-14：`GreenFinance.java` `.get*()` → `.opt*()` + 空指针防护，第十三个文件全部完成

第十三个文件（474 行，原~32 处调用点，一次会话内全部转完）。覆盖 `run`（首页解析与分批收集）、`batchSelfCollect`/`signIn`/`doTick`（签到与打卡）、`donation`（快过期金币捐助，含 `mcaDonationProjectResult.[0]` 路径取值改用先取数组元素再传路径查询）、`prizes`（评级奖品）、`batchStealFriend`（收好友金币）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AncientTree.java`（约 26 处调用点）。

### 2026-09-14：`ForestChouChouLe.java` `.get*()` → `.opt*()` + 空指针防护，第十二个文件全部完成

第十二个文件（1209 行，原~33 处调用点，一次会话内全部转完）。覆盖 `chouChouLe`/`chouChouLescene`（森林抽抽乐任务列表遍历、已完成任务领奖、活力值兑换、统一完成任务分支、抽奖执行循环）、`shareComponentRecall`/`confirmShareRecall`（好友助力解析）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `GreenFinance.java`（约 32 处调用点）。

### 2026-09-14：`OmegakoiTown.java` `.get*()` → `.opt*()` + 空指针防护，第十一个文件全部完成

第十一个文件（190 行，小文件一次性全部转完）。覆盖 `getUserTasks`/`getSignInStatus`/`houseProduct`（小镇任务领取、每日签到、房屋收金三个流程）。`grep` 确认代码里已无可执行的裸 `.get*()`（`org.json` 相关）调用，唯一剩余匹配是 `RuntimeInfo.getInstance().getLong(...)`，与前一文件同理不在范围内。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `ForestChouChouLe.java`（约 33 处调用点）。

### 2026-09-14：`AntBookRead.java` `.get*()` → `.opt*()` + 空指针防护，第十个文件全部完成

第十个文件（220 行，原~30 余处调用点，小文件一次性全部转完）。覆盖 `queryTaskCenterPage`（听书阅读能量，含 `dynamicCardList[0].data.bookList` 这种多层嵌套取值链，每层加了 null 判断和空数组判断）、`queryTask`（任务列表，含 `READ_MULTISTAGE` 子任务遍历）、`collectTaskPrize`/`queryTreasureBox`（领奖与开宝箱）。

`grep` 确认代码里已无可执行的裸 `.get*()`（`org.json` 相关）调用，唯一剩余匹配是 `RuntimeInfo.getInstance().getLong(...)`——`RuntimeInfo` 是完全不同的一套 API，不是 `org.json.JSONObject`，不在本次任务范围内，故意不动。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `OmegakoiTown.java`（约 34 处调用点）。

### 2026-09-14：`ProtectEcology.java` `.get*()` → `.opt*()` + 空指针防护，第九个文件全部完成

按调用点数量排序的第九个文件（778 行，原~98 处调用点）。覆盖 `initForest`/`initOcean`/`cooperateWater`/`queryCooperatePlant`/`cooperateWater(4参)`（森林/海洋项目列表同步、合种浇水）、`getEnergySummation`/`queryTreeItemsForExchange`/`queryTreeForExchange`/`exchangeTree`/`applyGoldAnimalCert`（树木兑换核心：证书数量与能量前置检查）、`protectCarbon`/`marathonQueryActivity`/`carbonQueryActivity`/`carbonCharityActivity`/`queryCultivationList`（碳中和马拉松/古树医生助力）、`protectReserveMinNum`/`protectBeachMinNum`/`protectBeach`/`queryCultivationDetail`/`oceanExchangeTree`（保护地/海滩最低数量保底兑换与海洋兑换收尾）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过（`BaseModel.java` 的 Lombok `@Getter` 命名冲突提示是本会话开始前就存在的良性 note，非本次改动引入，确认过 exit code 为 0）；仅做了编译期验证，未做设备/运行时测试。至此全库调用点数量排名前 9 的文件全部转换完毕，下一步按调用点数量排序转到 `AntBookRead.java`（约 43 处调用点）。

### 2026-09-14：`AntDodo.java` `.get*()` → `.opt*()` + 空指针防护，第八个文件全部完成

按调用点数量排序的第八个文件（921 行，原~103 处调用点）。覆盖 `initAntDodoTaskListMap`/`getEndDateTime`/`collect`/`collectAnimalCard`/`taskList`（神奇物种任务列表与每日抽卡）、`propList`/`usePropUniversalCard`/`queryUniversalAnimal`（道具自动使用与万能卡最优动物选择）、`consumeProp`（两个重载）/`collectToFriend`/`generateBookMedal`（消耗道具、帮好友抽卡、图鉴勋章合成）、`checkAnimalAndGiftToFriend`/`giftToFriend`（三个重载：入口/按 bookId 遍历/单张赠送，赠送稀有卡片给好友）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。至此全库调用点数量排名前 8 的文件全部转换完毕（AntFarm/AntForestV2/AntSports/AntStall/AntMember/AntOcean/AntOrchard/AntDodo），下一步按调用点数量排序转到 `ProtectEcology.java`（约 98 处调用点）。

### 2026-09-14：`AntOrchard.java` `.get*()` → `.opt*()` + 空指针防护，第七个文件全部完成

按调用点数量排序的第七个文件（1395 行，原~106 处调用点）。覆盖 `checkOrchardOpen`/`queryOptionalPlay`（农场乐园限定活动）/`initAntOrchardTaskListMap`、`initPlantScene`/`handleEnableScenes`/`handleTaobaoData`（场景与果树状态解析）、`doSpreadManure`/`canSpreadManure`（主场景+余额宝场景两条施肥前置检查）/`querySpreadManureActivity`、`orchardListTask`/`handleSignTask`/`handleTaskList`/`finishOrchardTask`（农场任务列表与签到）、`triggerTbTask`/`drawLotteryPlus`（七日礼包）/`extraInfoGet`（每日肥料包）、`querySubplotsActivity`/`handleWishActivity`/`handleCampTakeoverActivity`（许愿与营地接管子场景活动）/`queryYebRevenueDetail`（余额宝摇钱树收益）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。改的过程中发现一处局部变量名与内层 `optJSONObject("result")` 取的临时变量重名（`queryYebRevenueDetail` 里外层已有 `String result`），编译报错后重命名为 `resultObj` 解决，纯粹是命名冲突不是逻辑改动。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntDodo.java`（约 103 处调用点）。

### 2026-09-14：`AntOcean.java` `.get*()` → `.opt*()` + 空指针防护，第六个文件全部完成

按调用点数量排序的第六个文件（1570 行，原~108 处调用点）。覆盖 `queryOceanStatus`/`initAntOceanAntiepTaskListMap`（普通任务/摸鱼任务列表同步）、`queryHomePage`/`collectEnergy`/`cleanOcean`/`autocleanOcean`/`ipOpenSurprise`/`combineFish`/`checkReward`（能量收取与清理海域主流程）、`queryReplicaHome`/`unLockReplicaPhase`/`queryReplicaTaskList`/`receiveReplicaTaskAward`/`queryMiscInfo`（副本任务）、`querySeaAreaDetailList`/`openWAIT_FOR_UNLOCK`/`switchOceanChapter`/`queryUserRanking`（神秘海域拼图合成与章节切换）、`cleanFriendOcean`（两个重载）/`queryTaskList`（帮好友清理海域、日常任务）、`finishOceanTask`/`answerQuestion`/`exchangeUniversalPiece`（两个重载）/`useUniversalPiece`（两个重载）（答题任务、重复拼图兑换万能拼图、使用万能拼图迎回鱼类）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntOrchard.java`（约 106 处调用点）。

### 2026-09-14：`AntMember.java` `.get*()` → `.opt*()` + 空指针防护，第五个文件全部完成

按调用点数量排序的第五个文件（1483 行，原~131 处调用点）。覆盖 `initMemberTaskListMap`（会员任务/芝麻信用任务列表同步）、`memberSign`/`queryPointCert`（签到与积分领取）、`signPageTaskList`/`queryAllStatusTaskList`（做任务赚积分）、`promise`/`querySingleTemplate`（生活记录：构建加入请求体时对 `canSelectValues` 等四段规则数组做了长度校验，原代码假设数组必然非空，现在为空时直接返回 null 而不是让 `.getString(0)` 抛异常）/`promiseJoin`、`doBrowseTask`（两个重载：批量/单任务提交）、`goldBillCollect`/`batchReceivePointBall`/`dailySignIn`/`processTask`/`queryAndProcessTaskList`/`queryTaskList`（游戏中心任务体系）、`queryPointBallList`/`checkAndDoSignIn`/`memberPointExchangeBenefit`（玩乐豆签到与会员积分兑换）、`collectSesame`（芝麻信用收芝麻粒：领任务/完成任务/收 feedback 三段流程）、`RecommendTask`/`OrdinaryTask`（我的快递任务，顺带给 `taskMaterial` 可能为 null 的既有隐患加了守卫）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 1 处在注释里。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntOcean.java`（约 108 处调用点）。

### 2026-09-14：`AntStall.java` `.get*()` → `.opt*()` + 空指针防护，第四个文件全部完成

按调用点数量排序的第四个文件（1280 行，原~134 处调用点）。覆盖 `querySelfHome`/`selfHomeHandler`/`initAntStallTaskListMap`/`settleReceivable`（新村主页解析与结算入口）、`sendBack`（4参与 seatsMap 重载，请走小摊）/`inviteOpenShop`/`settle`（经营所得结算）、`closeShop`（两个重载）/`openShop`（两个重载）/`rankCoinDonate`/`friendHomeOpenShop`（收摊、摆摊、邀请好友摆摊全流程）、`taskList`/`doStallTask`（新村任务列表与各类型任务执行分支）、`signToday`/`receiveTaskAward`/`inviteRegister`/`shareP2P`/`assistFriend`（签到、领奖、邀请注册、人传人助力）、`projectList`/`projectDetail`/`projectDonate`/`canDonateToday`/`unlockNewVillage`/`canUnlockNewVillage`（公益捐赠与解锁下一村）、`collectManure`/`throwManure`（两个重载）/`pasteTicket`（两个重载）（收肥料、丢肥料反击、贴罚单）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用（全部转完，无残留注释死代码）。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntMember.java`（约 131 处调用点）。

### 2026-09-14：`AntSports.java` `.get*()` → `.opt*()` + 空指针防护，第三个文件全部完成

按调用点数量排序的第三个文件（2551 行，原~302 处调用点）。分多轮覆盖：`initSportsTaskListMap`/`sportsTasks`/`signInCoinTask`/`receiveCoinAsset`（运动任务与签到）、`getWalkPathMinCompleteCount`（世界地图/城市/路线三层嵌套查询）、`isNeedJoinNewPath`/`hasTreasureBox`/`walkGo`（两个重载）/`parseRewardsByJSONObjectData`（行走核心逻辑）、`queryWorldMap`/`queryCityPath`/`queryPath`/`openTreasureBox`/`receiveEvent`/`parseRewardsByJSONArrayRewards`/`queryGoingPathId`（路线查询与奖励解析）、`queryJoinPathId`/`checkJoinPathId`/`joinPath`（加入新路线）、`canDonateCharityCoinToday`/`queryProjectList`/`donate`（公益捐赠）、`canDonateWalkExchangeToday`/`queryWalkStep`（捐步做公益）、`userTaskGroupQuery`/`participate`/`userTaskRightsReceive`（文体中心日常任务/走路挑战赛）、`pathFeatureQuery`/`pathMapHomepage`/`tiyubizGo`（体育线路宝箱）、`queryClubHome`（抢好友大战：收能量球/购买好友/训练好友/蹲点训练四段逻辑）、`trainMember`/`queryMemberPriceRanking`/`queryClubMember`/`buyMember`（训练与抢购好友）、`coinExchangeItem`/`receiveSpecialPrize`/`signIn`/`receiveTaskReward`/`completeTask`（悦动健康任务体系）、`walkGrid`/`build`（能量泵前进/建造）、`receiveBrowseReward`/`receiveOfflineReward`/`parseRewards`/`receiveBubbleReward`（浏览/离线/气泡奖励）、`queryBaseInfoAndProcess`（能量泵主流程，普通岛+活动岛两条分支+活动岛奖励领取）、`queryAndProcessBubbleTasks`（气泡任务状态机）、`exchangeBenefits`（悦动健康权益商店兑换）、`canWalkGrid`/`canBuild`/`processSignIn`/`processTaskCenter`/`processBrowseTasks`/`queryUserEnergy`/`collectBubble`/`queryMapListSwitch`/`checkAuth`（收尾的能量泵前置检查、签到、任务中心、地图切换、权限检查）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 3 处在注释里（`//jo.getJSONObject(...)`/`//jo.getLong(...)`/`//if (TaskHelper...)`）。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到下一个文件（`AntMember.java`/`AntDodo.java`/`AntStall.java`/`AntOcean.java`/`AntOrchard.java`/`ProtectEcology.java` 等剩余约 20 个文件）。

### 2026-09-14：`AntForestV2.java` `.get*()` → `.opt*()` + 空指针防护，第二个文件全部完成

按调用点数量排序的第二个文件（4470 行，原~330 处调用点，全库最大单文件）。分多轮覆盖：`run()` 主流程能量球收取（浇水/复活/回赠金球、道具、动物偷能量）、`ForestEnergyInfo()` 日/周/总榜查询、`collectPKEnergy`、`querySelfHome`/`queryFriendHome`、`collectUserEnergy`（核心：能量罩/炸弹卡检测、气泡收取判断）、`collectFriendsEnergy`、`collectGiftBox`/`protectFriendEnergy`、`collectEnergy`（核心结果解析：`bubbles`/`bombCardEffect`）、`updateUsingPropsEndTime`/`collectRobExpandEnergy`、`queryForestEnergy`/`produceForestEnergy`/`harvestForestEnergy`（统一了 `jo.getJSONObject("data").getJSONObject("response")` 这种两层链式取值模式，改成两次 `optJSONObject` + null 短路）、`initAntForestTaskListMap` 活力值/抽抽乐任务列表同步、`greenRent`/`retrieveCurrentActivity`/`sendEnergyByAction`（森林集市/绿色租赁）、`popupTask`（过期能量签到）、`waterFriendEnergy`/`returnFriendWater`（好友浇水）、`vantiepSign`/`queryCommonSign`/`vitalitySign`（三种签到）、`doForsetTaskList`/`queryTaskList(firstTaskType)`/`doChildTask`（活力值任务领取）、`startEnergyRain`/`energyRain`/`checkAndDoEndGameTask`/`doforestgame`（能量雨与森林乐园宝箱）、`queryOptionalPlay`（乐园限定活动）、`continuousUseAndExchangeCard`/`continuousUseCardCheak`/`useRobExpandCardFactor`/`chooseContinuousLIMITTIMECard`（限时道具卡自动续期选卡）、`useDoubleCard`、`giveProp`（赠送道具）、`ecoLife`/`ecoLifeTick`/`photoGuangPan`（绿色行动/光盘打卡）、`queryUserPatrol`/`patrolKeepGoing`（巡护森林）、`queryAnimalPropList`/`consumeAnimalProp`/`queryAnimalAndPiece`/`combineAnimalPiece`（动物伙伴派遣与碎片合成）、`forFriendCollectEnergy`、`getForestPropVOList`/`getPropGroup`/`getForestPropVO`/`consumeProp`（背包道具查找，顺带移除了因转换后不可达的一处 `catch(JSONException)`）、`getAllSkuInfo`/`getSkuInfoBySpuId`/`getSkuInfoByItemInfoVO`/`exchangeBenefit`（活力值商店兑换）、`loveteam`/`loveteamWater`（合种浇水）、`updateUserConfigEnergyPvp`/`queryPvpHomeInfo`/`receivePvpRewards`（1V1 能量挑战）、`getDressDetail`/`checkDressDetail`/`queryUserDressForBackpack`（装扮保护）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 2 处在注释掉的死代码块内（`drawGameCenterAward` 里一段、`letsGetChickenFeedTogether` 注释块），故意不动。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步转到 `AntSports.java`（约 340 处调用点）。

### 2026-09-14：`AntFarm.java` `.get*()` → `.opt*()` + 空指针防护，第一个文件全部完成

接续上一条记录，本次把 `AntFarm.java` 剩余约一半（2213~4458 行）全部转完。覆盖 `notifyFriend`/`parseSyncAnimalStatusResponse`（关键：填充 `ownerFarmId`/`foodStock`/`animals[]` 等类字段，`animalJsonObject` 为 null 时该条 `animals[i]` 保留空壳对象跳过字段填充，不影响数组长度语义）、`collectDailyFoodMaterial`/`collectDailyLimitedFoodMaterial`/`cook`/`useFarmFood`/`drawLotteryPlus`/`visitFriend`/`acceptGift`/`queryChickenDiary(List)`/`visitAnimal`/`queryOptionalPlay`/`getAllSkuInfo`/`getSkuInfoByItemInfoVO`/`BuyMallItem`/`drawMachineGroups`/`doFarmDrawTask`/`hireAnimal`/`hireAnimalAction`/`drawGameCenterAward`/`ornamentsDressUp`/`saveOrnaments`/`getOrnamentsSets`/`family()`（家庭功能核心方法，`assignRights`/`eatTogetherConfig`/`familyInteractActions` 等）/`autoExchangeFamilyDecoration`/`assignFamilyMember`/`familyFeedFriendAnimal`/`animalWakeUpNow`/`familyAwardList`/`queryRecentFarmFood`/`familyShareToFriends`/`deliverMsgSend` 里 `deliverSubjectRecommend`/`DeliverContentExpand` 响应字段解析等。

文件里仍残留的 6 处 `.get*()`（`drawGameCenterAward` 里一段、`letsGetChickenFeedTogether` 一段）确认全部位于 `/* ... */` 注释块内的死代码，不是运行时会执行到的路径，故意不动——改了也不会被编译，反而混淆"这段代码是否生效"的判断。

至此 `AntFarm.java`（4458 行，原~352 处调用点）全部转换完毕，`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntForestV2.java`（约363处，全库最大单文件）。

用户明确要求把 `org.json.JSONObject`/`JSONArray` 的 `.get*()`（抛异常）手动改成 `.opt*()`（返回默认值/null），并且改完要补上对应的空指针判断，不是简单正则替换。范围限定为 JSON 读取，不碰 `android.os.Bundle` 的同名方法（`AntFarm.java` 未 import `Bundle`，整个文件都在范围内）。

全库统计约 1986 处调用点，`AntFarm.java`（4458 行，~352 处调用点）风险最高，按用户选择"从最高风险文件开始，一个个文件改"的节奏推进。本次覆盖 `enterFarm()`（关键：解析 `dynamicGlobalConfig`/`farmVO`/`masterUserInfoVO` 时对缺字段提前 return 并记录日志，因为它填充的 `ownerFarmId`/`ownerUserId`/`ownerGroupId`/`foodStock`/`foodStockLimit`/`harvestBenevolenceScore` 等字段贯穿全类）、`hasSleepToday`/`animalSleepNow`、`syncAnimalStatusAtOtherFarm`（顺带消除了一处对同一数组下标的重复解析）、`rewardFriend`/`recallAnimal`/`sendBackAnimal`、`receiveToolTaskReward`（逐项加 null 判断的循环重写）、`harvestProduce`、任务列表同步（`AntFarmDoFarmTaskListMap`/`AntFarmDrawMachineTaskListMap`）、捐蛋相关的 `donation()`/`competition()`/`stealRank()`/`receiveReward()`/`receiveDonationCompetitionProgressAward()`、`canDonationToday`/`recordFarmGame`/`listFarmTask`/`sign`/`doVideoTask`/`doAnswerTask`/`savePreviewQuestion`/`doFarmTask`/`receiveFarmTaskAward`/`feedAnimal`/`listFarmTool`/`useFarmTool`/`feedFriend`/`feedFriendAnimal`，约覆盖到文件 2213 行（总 4458 行的一半左右）。每完成一批就跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过，未做设备/运行时验证。剩余部分（捐赠礼物、小鸡互动、家庭功能、道具等约后半部分）留到下一轮继续，之后按调用点数量排序转到 `AntForestV2.java`（约 363 处，全库最大单文件）等其余文件。

### 2026-09-14：全代码库 `new JSONObject(x)` 统一换成 `MyUtils.newJSONObject(x)`（31 个文件）；`.get*()` 全局改 `.opt*()` 不做

用户指出 `MyUtils.newJSONObject` 本来就是为了兼容替代 `new JSONObject(...)` 设计的，应该全局替换，不该只改前面几条记录里点名的那几处。这个判断是对的，之前几次只改被具体问题点名的调用点，属于保守而非有意排除。这次做了两件性质完全不同的事——一件全局改了，另一件没有，原因分开说：

**`new JSONObject(x)` → `MyUtils.newJSONObject(x)`：已全局替换**

用脚本对 `app/src/main/java` 下所有 `.java` 文件做正则替换（排除 `MyUtils.java` 自己，它的内部实现要保留原始 `new JSONObject`），只匹配单参数形式 `new JSONObject(参数)`（用 `new JSONObject\([^)]` 排除无参 `new JSONObject()`——无参构造是"造一个空对象来 `.put()`"，跟"解析一个字符串"是两码事，不能动）。共 31 个文件、769 处 `new JSONObject(` 里排除 87 处无参后命中替换。这个替换之所以能批量做而不用逐个人工判断，是因为 Java 的静态类型系统天然兜底：`MyUtils.newJSONObject` 只接受 `String` 参数，如果哪处原本调用的是 `new JSONObject(Map)`/`new JSONObject(JSONObject, String[])` 这类其它重载，替换后编译直接报错，不会是静默的运行时问题——所以第一步替换完直接编译，让报错精确定位所有需要人工复核的点，不需要靠人工通读筛出这几百处里哪些安全哪些不安全。

编译报错定位到 6 个真实需要处理的点，全部是"原来的 `try { ... } catch (JSONException e) { ... }` 里，try 块的内容替换后已经不会再抛 `JSONException`，导致 Java 判定这个 catch 子句不可达"：
- [FriendWatch.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/FriendWatch.java) `load()`：确认唯一调用点（`ApplicationHook.java:465`）是裸语句调用，不读返回值，直接去掉 try/catch，函数永远返回 `true`。
- [Privilege.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/Privilege.java) 三处（`handleYouthTaskAward`/`processStudentSignIn`/`executeStudentSignIn`）：try 块里除了 `MyUtils.newJSONObject`/`.optXxx()` 没有其它会抛异常的调用，去掉 try/catch；外层各自的调用链上仍有更外层的 `catch (Exception e)` 兜底（`studentSignInRedEnvelope()`），删除内层这层不改变最终容错行为。
- [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java) `queryAndCollect()`：原来分开 `catch (JSONException e)` 和 `catch (Exception e)` 两层，第一层已不可达，删掉，保留通用的 `catch (Exception e)`。
- [OldRpcBridge.java](../app/src/main/java/io/github/aw1y2z/sesame/rpc/bridge/OldRpcBridge.java) `requestObject()`：这处顺带挖出一个跟本次改动无关、原来就存在的死代码——`msg.contains("MMTPException")` 分支里 `return rpcEntity;` 后面还跟着一段 `retryInterval` 判断 + `continue;`，这段代码从写下来那天起就从没执行过（`return` 已经是这个 if 分支里的最后一条可执行语句，之前能编译通过是因为这段代码"看起来"在 catch 块的保护范围内，实际控制流早就走不到）。顺着这条线往下，方法末尾 `} while (count < tryCount); return null;` 里最后这个 `return null;` 同理也是先天不可达（这个 `do-while` 循环体内每条路径都会 `return`，从来没有真正循环过，`tryCount` 参数名义上存在但从未生效）。这次只删掉了这两处编译器指出的、真正不可达的死语句，**没有**顺手"修复"这个循环从未真正重试的问题——那是超出本次改动范围的独立行为变更，需要单独评估要不要让它真的重试。

**`.get*()` 全局改 `.opt*()`：不做，这个和上面那条性质不一样**

`new JSONObject(x)` → `MyUtils.newJSONObject(x)` 之所以能批量做，是因为两者在"输入合法"时行为完全等价，只在"输入为 null/非法"这一种明确、单一的边界情况上从抛异常改成返回空对象——而且这个新行为已经是全代码库几百处已经在用的既定模式（空对象 → 下游 `checkResultCode`/`checkMemo` 判失败 → 走已有的失败日志分支），不是引入新行为，只是让其它调用点也享受同一个已验证的兜底。

`.getXxx()` → `.optXxx()`不是这种性质。`get` 系列在字段缺失/类型不对时抛异常，`opt` 系列会返回一个"安全默认值"（`optString` 返回 `""`、`optInt` 返回 `0`、`optBoolean` 返回 `false`、`optJSONObject`/`optJSONArray` 返回 `null`）——但这个默认值本身在业务上往往不安全：比如一个 `get` 用在读取兑换金额、任务奖励数量这类字段时，字段缺失说明接口返回结构和代码预期不一致（协议变了，或者遇到了未见过的响应），这时候抛异常让外层 catch 接住、跳过这一轮操作，是**正确**的保护性行为；换成 `opt` 拿到静默的 `0`/`""`/`false` 之后，代码会**带着错误数据继续往下执行**，而不是停下来——这在写日志、展示文案这类场景问题不大，但在涉及数额判断、状态流转的地方，读到静默默认值后继续执行可能比抛异常更危险。这 700 多处 `get`/`opt` 混用不是随手写的，是"这个字段值不对就该整体失败"和"这个字段拿不到给个默认值继续就行"两种不同意图的体现，本次没有做全局替换，需要哪个具体调用点你觉得不对，我可以单独看。

**验证**：`./gradlew compileNormalDebugJavaWithJavac compileNormalDebugKotlin -q` 编译通过（改的过程中反复出现过 PowerShell `-Encoding UTF8` 写文件自动加 BOM 导致 javac 报"非法字符"的问题，另写脚本剥掉了 31 个文件的 BOM 才编译通过，这个坑记一下，以后用 PowerShell 批量改 Java 源文件要用 `System.Text.UTF8Encoding($false)` 显式无 BOM 编码）。`./gradlew assembleNormalRelease -q` 打包成功（2,821,533 字节）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**未做真机验证**：这是本次会话里改动文件数最多的一次（31 个文件），虽然每一步都靠编译器的静态类型检查兜底，但"编译通过"只能证明类型正确、控制流可达，不能证明这几百处 RPC 响应解析在真实数据下行为符合预期——尤其是把 6 处 `catch (JSONException)` 直接删掉后，这几个函数在极端情况下（比如 `MyUtils.newJSONObject` 内部吞掉的畸形 JSON）会不会因为读取空对象上不存在的字段而抛出新的（未被捕获的）`JSONException`/`NullPointerException`，没有主动构造过异常输入去验证。

### 2026-09-14：修三个 M 自己的 bug——用户明确只管 M，不追究 XU 是否也修了

上一条 GMT+8 记录之外，核对 XU 自己 `doc/MyFix.md` 提到的几个待修项时，发现两个 XU 文档说"待修改"但**它自己当前代码也没真改**的问题，同时另外自己挖出一个新的：

1. **`AntFarm.listFarmTask()` 未知任务状态中断整批处理**（已修）：[AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 原 1675 行 `TaskStatus.valueOf(jo.getString("taskStatus"))` 裸调用，`TaskStatus` 枚举只有 `TODO`/`FINISHED`/`RECEIVED` 三个值（4392 行），服务端一旦返回没见过的状态直接抛 `IllegalArgumentException`，只被循环外层的 `catch` 兜住——意味着**这一轮循环里排在后面的所有庄园任务都不会被处理**，不是只跳过那一项。改成每项单独 `try/catch IllegalArgumentException`，未知状态记日志后 `continue` 跳过当前项，不影响其余项目。顺手把循环体内复用外层变量名 `jo`（原来重新赋值会覆盖外层 `jo`）改成独立的 `taskJo`，避免变量名混淆，没有引入行为变化。
2. **`ApplicationHook.onPackageReady()` 用包名冒充进程名**（已修）：原 199 行 `lpparam.processName = param.getPackageName();`——libxposed 102 的 `PackageReadyParam` 不直接暴露真实进程名，之前图省事直接拿包名顶替，导致 `handleLoadPackage()` 里"主进程跑业务、子进程只装抓包"的分流判断在目标包的 `:xxx` 子进程触发这个回调时收到的还是主包名，分不清是不是子进程。新增 `getRealProcessName()`：优先反射调用 `ActivityThread.currentProcessName()`（API 28+ 的标准做法），拿不到则退回读 `/proc/self/cmdline`（覆盖 M `minSdk 26-27` 这段 API 28 以下的设备），两条路都失败才退回包名兜底——保留原来的行为下限，不会比之前更差。
3. **`AntForestV2` 收能量 `collectEnergy` 忽略空响应**（已修）：`jiaoshui`/`baohuhuizeng` 两个 `case` 分支（原 466、501 行）里 `new JSONObject(AntForestRpcCall.collectEnergy(...))` 不检查返回值就直接构造，请求失败/离线时 `collectEnergy` 可能返回 `null`，`new JSONObject(null)` 抛异常，被外层 `catch` 吞掉，中断本轮剩余金球的收取。改用本会话早前就在用的 `MyUtils.newJSONObject(...)`（`null`/非 `{` 开头输入返回空对象），失败时下游 `MessageUtil.checkResultCode` 在空对象上自然判失败，走已有的失败日志分支，不会再抛异常中断循环。

**明确没做的**：`new JSONObject(RpcCallXxx(...))` 不检查返回值这个写法在 M 全代码库里是**普遍写法**，`AntForestV2` 这两处只是被 XU 自己的审计文档点了名才顺手查到、修了；没有对整个代码库做一次系统性的 null 安全审计——那个工作量和 TimeUtil 改造类似甚至更大（要过一遍所有 RPC 调用点，逐个判断"这里失败了会不会真的中断不该中断的批量循环"），本次只按用户实际问到的范围处理。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告。`./gradlew assembleNormalRelease -q` 打包成功（2,824,420 字节，跟上一条记录基本一致）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**未做真机验证**：`getRealProcessName()` 依赖的 `ActivityThread.currentProcessName()`/`/proc/self/cmdline` 两条路径都需要在真实支付宝多进程环境（尤其带 `:xxx` 子进程的场景，比如抓包/推送相关子进程）里跑一次才能确认真的拿到了子进程自己的进程名而不是主进程名；`listFarmTask`/`collectEnergy` 的容错分支需要服务端真的返回未知状态/空响应才能触发，没有主动构造过这类异常输入去验证 catch 分支本身的正确性。

### 2026-09-14：TimeUtil 全面改成 GMT+8——对齐 Sure-Xu 的做法，不再是挨个补丁

之前几次记录里陆续在具体调用点（道早安/请客吃饭/好友刷新/ApplicationHook 三处/鱼塘时段）把 `Calendar.getInstance()` 换成 `MyUtils.getInstance()`，属于打补丁式的逐点修复。用户问"MyUtils.getInstance 不就是干这个的，全局替换不就行了，GR 不是这么做的吗"，核实后发现：GR 自己也不是这么做的——GR 只在少数几处手动改了，`updateDay()` 里跨天重新赋值那行仍然是裸 `Calendar.getInstance()`，本次会话早前已经指出并修过这个 GR 自己的不一致。真正一次性、干净的做法是 Sure-Xu 那种：**只改 `TimeUtil.java` 内部构造 `Calendar`/`DateFormat` 的几个源头方法**，所有调用 `TimeUtil.getNow()`/`isAfterTimeStr()`/`getToday()` 等方法的地方自动跟着拿到 GMT+8，不用逐个调用点手工替换——这是"改一处、所有调用者自动生效"的根因修复，不是分散打补丁。

**做法**：[TimeUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java) 新增私有常量 `GMT8 = TimeZone.getTimeZone("GMT+8")`，把类内部全部 `Calendar.getInstance()`（`isCompareTimeStr`/`getCalendarByTimeMillis`/`getDateStr`/`getToday`/`getNow`/`getWeekNumber` 共 6 处）改成 `Calendar.getInstance(GMT8)`；另外发现 `getTimeStr`/`getDateStr`/`getCommonDateFormat`/`getCommonDateFormatS` 这几个用 `DateFormat`/`SimpleDateFormat` 格式化输出的方法有个更隐蔽的坑——即使传入的 `Calendar` 已经是 GMT+8，`DateFormat` 格式化 `Date` 对象时默认仍然使用系统时区渲染字符串（`Date` 本身不带时区，只是绝对时刻），必须显式 `format.setTimeZone(GMT8)` 才能让最终输出的文本也是北京时间，这一步 Sure-Xu 的参考实现里也做了但容易被忽略。`getInstanceGMT8()`（此前几次记录加的委托方法）简化成 `getNow()` 的别名，不再重复实现。

**顺带清理干净了全代码库剩余的裸调用**：改完 `TimeUtil.java` 后 `grep -r "Calendar.getInstance()"` 复查，又挖出 6 个当时不在 family/道早安范围内、没顺手改的点，这次一并处理：
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 3 处（捐蛋排位 20:01 截止时间判断、偷榜定时目标时间计算、`isStealRankTime` 判断），是本次会话第一次做 SO 移除/GMT+8 审计时就发现但因为"不在 family 范围"特意没动的，这次一起改成 `TimeUtil.getNow()`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 自定义唤醒时间段的 `nowCalendar`——这是更早一条记录里明确写"故意没改"的点，因为当时 `TimeUtil.getTodayCalendarByTimeStr` 还是系统时区，两边只改一边会产生新的错位；现在 `TimeUtil` 已经整体 GMT+8 了，这个顾虑不存在，直接改成 `TimeUtil.getNow()`。
- [Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)、[Statistics.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Statistics.java) 的 `save()` 无参重载——追进去看了 `save(Calendar)` 内部实际只取 `nowCalendar.getTimeInMillis()`（时间戳本身不受时区影响）传给 `TimeUtil.isLessThanSecondOfDays`，严格说不改也不会错，但为了不让读者需要再验证一遍"这里安不安全"，统一换成 `TimeUtil.getNow()`。
- [MiuixMainActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixMainActivity.kt) 的 `Statistics.updateDay(Calendar.getInstance())`，同上。
- [Privilege.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/Privilege.java) 两处（`isSignInTimeValid()` 学生签到窗口判断、`executeStudentSignIn()` 判断 double/single 文案），换成 `TimeUtil.getNow()`。

改完之后 `grep -rn "Calendar\.getInstance()" app/src/main/java` 只剩两行历史注释（记录之前 bug 的说明文字），代码里已经没有裸调用。

**验证**：`./gradlew compileNormalDebugJavaWithJavac compileNormalDebugKotlin -q` 编译通过，无新增警告。`./gradlew assembleNormalRelease -q` 打包成功（2,824,154 字节，跟上一条混淆记录的体积基本一致，符合预期——这次只是时区逻辑改动，不影响代码体积）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**依然没有做真机验证**——`DateFormat.setTimeZone` 这类格式化输出变化尤其需要肉眼确认日志/统计页面显示的时间字符串确实是北京时间而不是乱码或时区错位；`Status`/`Statistics` 的跨天重置逻辑理论上无行为变化（只是显式化），但没有在非东八区设备/模拟器上跑过对照组确认。

### 2026-09-12：Release 开启混淆（minifyEnabled/shrinkResources），对照 GR2026/Sesame-AG/Sure-Xu 三份规则写 proguard-rules.pro

用户明确要求正式包必须开混淆。三个参考项目里 GR2026 和 Sesame-AG 都是"整包 keep 自己代码"（`-keep class io.github.lazyimmortal.sesame.** { *; }` / `-keep class io.github.aoguai.sesameag.** { *; }`）——技术上混淆开了，但自己的业务代码一行都没真正混淆，只混淆了第三方库。Sure-Xu（`E:\Work\Sure-Xu\app\proguard-rules.pro`）是唯一一个做了外科手术式规则的，而且 Sure-Xu 和 M 同为 libxposed 102 架构（GR 是传统 Xposed API，AG 虽然也是 libxposed 102 但选了偷懒的整包 keep），参照对象选了 Sure-Xu 这份，不是简单照抄 GR/AG 的省事做法。

**做法**：只保留三类必须不能被 R8 动的东西，其余全部允许真正混淆/内联/删除：

1. **反射调用点**——逐个找出源码里实际存在的反射用法，只保留刚好够用的规则：
   - `Model.initAllModel()`（[Model.java](../app/src/main/java/io/github/aw1y2z/sesame/data/Model.java)）用反射调用每个任务模块的无参构造函数，且简单类名本身就是持久化配置的 key（存量用户 `config.json` 里已经写死了类名字符串），所以 `model/**` 下所有 `extends Model` 的类**类名必须保留**，不能只保留构造函数——这点容易漏，之前理解为"只要构造函数能反射调用就行"是错的，实际测过 mapping.txt 才确认 `-keep class X extends Model { public <init>(); }` 这个语法本身就同时保留了类名，不需要额外加 `-keepnames`。
   - [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java) 的 `getWuaByReflection()`：`Class.forName("...AntOrchard")` + `getDeclaredMethod("getWua")` 跨类反射调用 [AntOrchard.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antOrchard/AntOrchard.java) 的私有方法——这是本次逐行核对 Sure-Xu 规则时才确认 M 也有一模一样的反射调用点（`AntMember.java:809` 起），如果不知道 Sure-Xu 已经踩过这个坑，光看 M 自己代码不容易联想到要为这一个私有方法单独写 keep 规则。
   - [ExtensionsHandle.java](../app/src/main/java/io/github/aw1y2z/sesame/model/extensions/ExtensionsHandle.java) 用 `Class.forName` 探测可选的 `ExtensionsHandleAlpha` 类是否存在（M 代码库里没有这个类，是预留的可选扩展点），保留主类 + 对不存在的 Alpha 类 `-dontwarn`。
   - Xposed 模块入口 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`META-INF/xposed/java_init.list` 里按字符串记录入口类全限定名。参照 Sesame-AG 的官方写法 `-keep,allowoptimization,allowobfuscation ... extends XposedModule { public <init>(); }` 配 `-adaptresourcefilecontents META-INF/xposed/java_init.list`——这个写法允许类名本身被混淆（实测 mapping.txt 显示 `ApplicationHook -> ah`），同时 R8 会自动同步改写 `java_init.list` 资源文件里的类名字符串，比 GR/AG 那种整包不混淆入口类更彻底。
2. **Jackson 反射需要的边界**：`data/*`（含 `AppConfig`/`TokenConfig`/`ConfigV2` 等）、`data/modelFieldExt/**`、`entity/**`、`hook/RpcRequest`、`util/Status`、`util/Statistics` 整体保留——这些类靠字段名/getter/setter 名做 JSON 序列化，改名会导致读不出存量用户已保存的配置和统计数据。同时补了 Jackson 库自身内部反射构造函数/枚举字段的 keep 规则，及桌面版 `java.beans.ConstructorProperties`/`Transient` 缺失警告的 `-dontwarn`（Android 上这两个类本来就不存在，Jackson 自己会处理，只是编译期会警告）。
3. **第三方库**：OkHttp3/Okio 标准 keep 规则（对齐 GR/Sure-Xu 都有的写法）、NanoHTTPD、`compileOnly` 的 `io.github.libxposed.api.**`（运行时由宿主 LSPosed 框架提供，混淆期这些类不在场，只需要 `-dontwarn` 消警告，不需要 keep）。

**没有照抄的东西**：GR/AG proguard 文件里各自的自定义混淆词典（`-obfuscationdictionary proguard-sxbk.txt` 等 GR 品牌相关）、AG 的 Shizuku/cmd-android/logback 相关规则（M 没有这些依赖）都没搬，照抄会引用不存在的文件/类导致构建失败或规则空转。

**验证**：`./gradlew assembleNormalRelease -q` 全程零警告零错误打包成功。产物体积从上一条记录的 14,778,120 字节**降到 2,824,113 字节**（约 5.2 倍收缩，主要是 `shrinkResources` 删掉了未引用资源、R8 删掉了未引用代码，不是签名或功能损失）。读取生成的 `app/build/outputs/mapping/normalRelease/mapping.txt` 逐条核对：`ApplicationHook` 按预期被重命名（`-> ah`，配合资源文件自动改写）；`AppConfig`/`AntSports` 等按预期保持原名未混淆；`MyUtils` 等普通工具类按预期被真正混淆（`-> a11` 这类短名）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**完全没有做运行时验证**——这是本次改动里风险最高的一项：mapping.txt 只能证明"R8 按规则做了它认为该做的重命名/内联"，不能证明"重命名之后模块在真实支付宝进程里跑起来仍然正常"。尤其是 Jackson 反序列化存量用户的旧 `config.json`/`status.json`、`AntMember` 反射调用 `AntOrchard.getWua()`、Xposed 入口加载这三个反射相关的关键路径，必须在真机/模拟器上装一次跑一轮完整流程才能确认没有因为混淆漏保留某个反射目标而在运行时崩溃或静默失效——这类问题编译期和 R8 都不会报错，只会在运行时才暴露。

### 2026-09-12：VersionHook 滑块初始化时序补全——对齐 GR，用户明确要求把这个已知不完整点补上

上一条 VersionHook 记录里写明了一个已知不完整点：GR 把 `initSimplePageManager()`（滑块验证初始化）从 `attach` 钩子挪到了 Service.onCreate 版本覆盖之后，确保滑块初始化吃到的是（可能已伪装的）版本号；M 当时没有跟着挪，理由是这是全体用户都会走的初始化路径，为一个默认关闭的小众功能重排时序风险收益不对等。用户明确要求补上，这次照做：

- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`attach` 钩子里删掉 `initSimplePageManager();` 调用（连同已经注释掉的旧异步线程代码一起清掉，那段注释本来就是死代码）；在 Service.onCreate 里 `VersionHook` 版本覆盖判断结束之后（`if (VersionHook.isVersionHookEnabled()) { ... }` 这段之后）新增调用，跟 GR 的调用点位置一致。

**影响范围说明，不是回避**：这个改动确实影响全体用户，不只是开启版本伪装的人——`initSimplePageManager()` 的执行时机从"支付宝 Application.attach() 时"挪到了"支付宝前台 Service.onCreate 时"，onCreate 在 attach 之后触发，两者之间间隔通常很短（同一次进程启动流程内），按 GR 的实际使用情况看这个时序差影响面很小，但这确实是一次全局行为变更，不是只在开启 VersionHook 时才生效的隔离改动。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。`./gradlew assembleNormalRelease -q` 打包成功（14,778,120 字节）。**未做运行时验证**——`initSimplePageManager()` 时序变化对滑块验证初始化的实际影响、以及 attach 到 Service.onCreate 之间这段间隔期间滑块验证相关状态是否有其它代码路径依赖"已经初始化"这个前提，都没有在真实支付宝进程里跑过确认。

### 2026-09-12：福气鱼塘（FishTask）移植——以 Sure-Xu 的 Java 版为底，对照 Sesame-AG 修正

用户要求把福气鱼塘做了，指定用 Sure-Xu 的 Java 实现打底（Sure-Xu 已从 GR 原版修过 6 个已知缺陷，见其 `doc/GR-Sync.md`），如果 XU 版本有问题再参照 Sesame-AG（Kotlin 实现，用户更信任的参考）修。

**为什么不直接参照 AG**：AG 的 `task/antFishPond/AntFishPond.kt`（1473 行）绑定在它自研的通用任务状态机框架（`TaskFlowEngine`/`TaskFlowAdapter`/`TaskFlowPhase`/`TaskFlowDecision` 等）上，这套框架 M 完全没有，其它任何模块也不用；照抄意味着要先把整套状态机框架搬进来，工作量和架构侵入性都远超这一个功能本身需要的范围。Sure-Xu 的 `model/task/fish/FishTask.java`（2370 行）是普通过程式 Java，`extends ModelTask` 后 `getName/getGroup/getFields/check/run` 的方法签名和 M 的 `ModelTask`/`Model` 抽象类完全一致（两边本来就是同源分支），机械翻译包名风险低得多。

**移植内容**（新增 3 个文件 + 2 处基础设施 + 1 处注册）：

- [model/task/fish/FishConfig.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/fish/FishConfig.java)：设置项（钓鱼/任务两个总开关、自动黑名单开关、Token 输入框、黑名单多选、独立执行间隔），逐行对照 XU 版，`ModelField` 子类构造函数签名在 M 和 XU 里完全一致，纯机械翻译包名。
- [model/task/fish/FishTask.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/fish/FishTask.java)：任务本体。RPC 请求全部用 `String.format` 拼 JSON 字符串直接传给 `ApplicationHook.requestString(method, args)`（沿用 XU 的写法，不是 GR/AG 那种单独 RpcCall 类），业务逻辑（签到、每日宝箱、明日钓竿、钓鱼活动、任务列表六种任务类型分支、自动钓鱼主循环、收杆定位、双倍广告）逐行照抄 XU 版，字符串常量、等待时长、重试次数都没有改动——这些是抓包得出的经验值，没有 M 自己的真机验证依据去调整。丢弃了 XU 文件里确认没有任何调用点的死代码（`hasPendingTask`/`finishTask(JSONObject)` 旧版本/`receiveGiftBoxAward`/`isBeforeFourAM`/`extractPwPreBizIdFromUrl`/`extractBizIdFromUrl`/`API_BATCH_INVITE`），逐个 grep 确认过确实无调用才删，不是猜的。
- [util/idMap/AntFishpondTaskListMap.java](../app/src/main/java/io/github/aw1y2z/sesame/util/idMap/AntFishpondTaskListMap.java)：新增，照抄 M 已有的 `AntForestHuntTaskListMap.java` 模板（同样的 load/save/get/add 结构），存运行时发现的鱼塘任务 ID→显示名映射，供黑名单设置项下拉选择。
- [util/FileUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/FileUtil.java)：新增 `getAntFishpondTaskListMapFile()`，跟其它 idMap 文件路径方法同一模式。
- [util/Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)：新增 `fishLastExecTime` 字段（`@Data` 生成 getter/setter），鱼塘用自己独立的执行间隔，不跟随全局任务间隔——这是本会话早前 GR `main_my` 47 提交审计时就识别出的需求（提交 `73507243`"鱼塘使用fishLastExecTime"），当时因为整个鱼塘功能没做而搁置，这次一起补上。
- [model/base/ModelOrder.java](../app/src/main/java/io/github/aw1y2z/sesame/model/base/ModelOrder.java)：`clazzList` 里注册 `FishTask.class`。

**对照 AG 修的一处**：`isAllowedTime()`（判断当前是否在 05:00-23:00 运行时段）原版用 `TimeUtil.getNow()`，跟本次会话里 `deliverMsgSend`/`familyEatTogether`/`FriendWatch.needUpdateAll`/`ApplicationHook` 三处早前修过的 GMT+8 bug 是同一个根因——系统默认时区而非北京时间。改成 `MyUtils.getInstance()`。这是唯一一处主动对照 AG 发现的问题；`FISH_ACTIVITY`/`GIFT_BOX`/`TOMORROW_ROD` 等具体状态判断分支 AG 和 XU 写法不同（AG 用统一的 claimable-status 集合 + extend.status 兜底，XU 分散在几个方法里用不同的时机判断），**没有逐条位重新审计这些分支的等价性**——那相当于把整个 AG 1473 行再读一遍逐句比对，工作量堪比本次移植本身，本次没有做，如果实测发现某个子活动（宝箱/明日钓竿/钓鱼活动奖励）长期不触发，需要单独排查。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。实际跑了 `./gradlew assembleNormalRelease -q`，成功打包（14,778,084 字节）。**完全没有做运行时验证**——鱼塘的抓包字段、任务类型、等待时长这些经验值必须在真实支付宝进程里跑过才能确认还有效（抓包时效性本身也是风险，`VERSION = "20260211.01"` 这类版本号字段服务端随时可能不认），黑名单/Token 输入框等设置项 UI 展示也未在模拟器里点过。

### 2026-09-12：处理上次审计留的三个候选——writeDishImage 做了，VersionHook 做了（默认关），福气鱼塘查了源头还未做

**福气鱼塘**：检查了 `E:\Work\Sesame-AG`（Kotlin，`task/antFishPond/AntFishPond.kt` + `AntFishPondRpcCall.kt`）和 `E:\Work\Sure-Xu`（Java，`model/task/fish/FishTask.java` + `FishConfig.java`，与 GR 文件名/结构几乎一致）——**两边都已经有这个功能**，其中 Sure-Xu 是从 GR 移植过来的 Java 版本，且 Sure-Xu 自己的 `doc/GR-Sync.md` 记录了移植时顺带修过的 6 个 GR 原版缺陷（成功兑换也写失败标记、循环末尾无条件清零失败计数、自动黑名单开关未接入、状态查询与广告处理互相重入、任务等待忽略中断、浏览循环次数用 `max` 未限制上限等）。~~结论：以后真做这个功能时……本次没有动手移植~~——已经做了，见上面（更晚）的"福气鱼塘（FishTask）移植"记录，用的正是这里说的 Sure-Xu Java 版本打底。

**VersionHook 版本伪装——确认判断错了，已移植，默认关闭**：上次审计只读了 `VersionHook.java` 本身就下判断"个人偏好，不移植"，这次用户要求查清楚具体干什么、有没有用，深挖了实际生效路径才发现：这不是简单的"跳过本地判断分支"，而是**向支付宝服务端主动谎报一个更低的客户端版本号**，目的是规避服务端对高版本客户端才触发的拼图验证码风控（GR 原注释："使其认为安装了低版本，从而避免高版本特有的拼图验证"）。用户知悉这个真实性质后仍要求移植，默认关闭。

移植内容：

- 新增 [hook/ext/VersionHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ext/VersionHook.java)：API 从传统 Xposed（`XposedHelpers`/`de.robv.android.xposed.XC_MethodHook`）换成 M 自己的兼容层（`XHelpers`/`compat.XC_MethodHook`）——这套兼容层本来就是 `util/compat/` 下为了在 libxposed 102 上运行传统 Xposed 风格代码而做的，本次是第一次真正用它承接一个"新" hook（此前只是承载既有代码）。有一处不能照抄 GR：GR 用 `XposedHelpers.findAndHookMethod(className, null, ...)` 传 `null` classloader 依赖传统 Xposed 的兜底解析，M 的 `XHelpers.findClass` 没有这个兜底（`cl.loadClass()` 对 null 会直接 NPE），改成传实际的 `classLoader`（支付宝进程的 classloader，能正常解析到 `android.app.ApplicationPackageManager` 这个框架类）。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：新增 `realAlipayVersion` 字段和 `getEffectiveVersion()` 方法；`attach` 钩子里记录真实版本号的同时注册 `VersionHook.initVersionHook(classLoader)`（必须在 attach 钩子真正触发、读取 `getPackageInfo` 之前完成注册）；Service `onCreate` 里在文件系统就绪后 `ensureVersionConfig`/`loadVersionConfig`，开关打开则用伪装版本覆盖 `alipayVersion`；运行日志的"应用版本："那行改用 `getEffectiveVersion()`，开关打开时额外标注真实版本。
- [MiuixExtensionsActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixExtensionsActivity.kt) 新增"版本伪装（谨慎使用）"卡片：开关 + 伪装版本名/版本号两个输入框，文案里直接写清楚"这是主动欺骗服务端的行为"，不是包装成人畜无害的小功能。配置存在跟支付宝注入进程共享的 `version_config.json` 里，UI 运行在 App 自己的进程（不是注入进程），改动后需要重新注入/重启支付宝才会在那边生效，UI 上有对应提示。

~~这次移植比 GR 窄一块……这是已知的不完整点~~：已在下一条（更晚的）"VersionHook 滑块初始化时序补全"记录里对齐 GR，`initSimplePageManager()` 挪到了 Service.onCreate 的版本覆盖之后执行。

**writeDishImage/writeDishImageWithRandomIds——确认有用，已移植**：M 现有的森林设置项文案自己就写着"光盘行动需要先手动完成一次"，说明这个手动种子的痛点本来就存在，是文档化的真实摩擦点，不是臆测的需求。

- [TokenConfig.java](../app/src/main/java/io/github/aw1y2z/sesame/data/TokenConfig.java) 新增 `writeDishImage(beforeMealsId, afterMealsId)` 和 `writeDishImageWithRandomIds()`，复用已有的 `checkDishImage`/`saveDishImage`，没有重复校验逻辑。
- [MiuixExtensionsActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixExtensionsActivity.kt)"其他"分组里加了"手动补光盘行动图片"入口，弹窗两个输入框 + "随机生成"/"写入"两个按钮，跟已有的"清空光盘行动图片"按钮并排，复用同一套 `Dialog`/`ConfirmDialog` 风格，没有另起一套 UI 模式。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `compileNormalDebugKotlin -q` 均无输出（编译通过，无新增警告）。额外跑了一次 `./gradlew assembleNormalRelease -q` 确认整个打包流程（含新增的 hook 注册代码）不会导致构建失败，产物 14,760,295 字节。**未做任何运行时验证**——VersionHook 这类东西必须在真实支付宝进程里触发 `getPackageInfo` 调用链才能看到效果，编译通过完全不能证明 hook 本身注册成功、`ApplicationPackageManager.getPackageInfo` 方法签名匹配、字段反射（`packageName`/`versionName`/`versionCode`）在当前系统版本上有效；也没有验证开启后是否真的能避开拼图验证码，或者是否会因为版本号与其它请求头/数据不一致而触发其它风控信号——用户需要自行在测试环境验证后再决定是否长期开启。

### 2026-09-12：GR2026 `main_my` 全部 47 个提交最终逐一核对——找到并修了 3 处新的 GMT+8 bug，其余确认为已覆盖/不适用

用户要求把 GR2026 自己改的代码里还有哪些功能没参考移植的过一遍。逐个重新过了 `git log --oneline main..main_my` 的全部 47 个提交（含之前几条记录已经处理过的），结果：

**新发现并修了 3 处同类 GMT+8 bug**（和 `deliverMsgSend`/`familyEatTogether` 那两处根因完全一样：用裸 `Calendar.getInstance()` 而不是 GMT+8，对齐 GR `7ce920e8`"通过AI同步旧逻辑"这个最早的批量同步提交里的改法）：

- [FriendWatch.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/FriendWatch.java) `needUpdateAll()`：判断好友列表是否需要整体刷新（"隔天 + 周一"触发全量更新），原来两个 `Calendar.getInstance()` 全部改成 `MyUtils.getInstance()`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 三处：Service `onCreate` 里 `dayCalendar` 的初始值、`setWakenAtTimeAlarm()` 里计算"明天 00:00"固定午夜唤醒闹钟的 `calendar`、`updateDay()` 里判断跨天用的 `nowCalendar`，全部改成 `MyUtils.getInstance()`。

**这处修复比 GR 自己做得更完整，不是照抄**：核对 GR 当前代码发现它自己都没改全——`dayCalendar` 在 Service `onCreate` 处确实用了 `MyUtils.getInstance()`（GMT+8），但 `updateDay()` 里跨天时重新赋值 `dayCalendar = (Calendar) nowCalendar.clone()` 用的 `nowCalendar` 仍然是裸 `Calendar.getInstance()`——意味着 GR 自己的模块跑过第一次跨天之后，`dayCalendar` 会静默从 GMT+8 语义退回系统时区语义，这是 GR 自己遗留的不一致 bug。这次没有照抄 GR 的半成品，`onCreate` 初始化和 `updateDay()` 跨天重新赋值两处一起改成 GMT+8，保持 `dayCalendar` 全生命周期语义一致。

**发现但没有动、需要以后单独处理的同类风险**：[ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) `setWakenAtTimeAlarm()` 里自定义唤醒时间段那部分（`Calendar nowCalendar = Calendar.getInstance()`，用于跟 `TimeUtil.getTodayCalendarByTimeStr(wakenAtTime)` 比较判断这个自定义闹钟今天是否还没过）**故意没有改**——`getTodayCalendarByTimeStr` 内部同样用裸 `Calendar.getInstance()` 构造"今天 HH:MM"，如果只把这里的 `nowCalendar` 单独改成 GMT+8 而不动 `TimeUtil` 本身，两边用不同时区反而会产生新的、更隐蔽的比较错位。`doc/GR-Sync.md` 早前已经点出 `TimeUtil` 大部分方法仍是系统默认时区、改动面很大，这次维持"不做半吊子修复"的原则，把这处留给以后专门做 `TimeUtil` 统一 GMT+8 改造时一起处理，不在这次顺手改一半。

**核对过、确认已经覆盖或不适用，不是遗漏**：

- **FishTask（福气鱼塘）**：`36d110b9`/`c3ae5375`/`549fcd7b`/`6c8cb85b`/`73507243` 等 9 个提交，`doc/GR-Sync.md` 早前已列为同步候选，本次重新确认这块工作量（独立 RPC 层 + 任务列表 + 黑名单 + 设置项注册）没有变化，仍未移植，维持"以后单独做一次完整任务"的结论，不在本次顺手做。
- **VersionHook 版本伪装**（`3013cb36` 新增 237 行 `VersionHook.java` + `cea6d5f9` 给 `AppConfig` 加 `enableFakeVersionSlider`/`fakeVersionName`/`fakeVersionCode`）：读了 `VersionHook.java` 全文确认——这就是拦截 `PackageManager.getPackageInfo()` 伪造支付宝版本号的功能，且用的是传统 Xposed API（`de.robv.android.xposed.XC_MethodHook`/`XposedHelpers`），M 现在只依赖 `io.github.libxposed:api:102.0.0`，连编译都过不了。这正是 `doc/GR-Sync.md` 一开始就点名的"GR 的版本伪装……属于具体版本/个人偏好，不直接覆盖"那一条，本次读源码确认判断依然成立，不是漏做。
- **扩展页面重写**（`41e74688`，559 行 `ExtensionsActivity.java` + 对应布局 XML）：内容基本就是上面版本伪装功能的开关 UI，外加下面 `writeDishImage` 手动工具的入口，两者本身都不搬，这个 XML UI 重写自然也不适用——M 的扩展页面是 `ui/miuix/MiuixExtensionsActivity.kt`（Compose），架构完全不同，没有直接对应关系。
- **`writeDishImage`/`writeDishImageWithRandomIds`**（`2a6d496b`，`TokenConfig.java` 新增两个方法）：光盘行动（森林任务的一种，上传餐前餐后照片换能量）手动补图片 ID 的便捷工具，只在上面提到的扩展页面里被调用（手动点按钮用）。M 的 `TokenConfig.java` 已经有 `saveDishImage`/`checkDishImage`/`clearDishImage` 这套底层能力，缺的只是这两个方便手动调用的包装方法和对应 UI 入口。**这是本次审计里唯一一个"未评估过、可能有用但没做"的候选**——是否需要在 Compose 扩展页面里加个手动补光盘图片 ID 的入口，需要用户确认后再做，本次没有主动加。
- **其余提交**（`560281dc`/`9a0c4408`/`48820e4c`/`51fbc6db`/`083587dd`/`189010b6` 等）：纯版本号/更新日志/构建脚本版本号文本变化，符合用户之前定的"版本号更新记录不用看"的范围，跳过。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。未运行模拟器/真机验证这几处时区修复后的实际调度效果（好友列表周一全量刷新、午夜固定唤醒闹钟、跨天状态重置），这几处都是全局调度逻辑，风险比单个任务模块的时区 bug 更高，建议后续找机会实机验证一次日期跨天时机是否符合预期。

### 2026-09-12：Release 只保留 arm64-v8a 产物，不再打 armeabi-v7a 和 universal

用户要求只保留 arm64 的 lib，其它不要。[app/build.gradle](../app/build.gradle) 的 `splits.abi` 从 `include "armeabi-v7a", "arm64-v8a"` + `universalApk true` 改成 `include "arm64-v8a"` + `universalApk false`——armeabi-v7a（32 位）和 universal（全架构兜底包）都不再产出。输出文件命名逻辑（`androidComponents.onVariants` 里的 abiTag 拼接）不用改，少了 universal 之后自然只剩一个带 `-arm64-v8a` 后缀的产物，没有走到原来的 "-universal" 兜底分支。

**验证**：`./gradlew assembleNormalRelease -q` 重新打包，`app/build/outputs/apk/normal/release/` 下只有 `Sesame-M-Normal-arm64-v8a-1.0.0.apk` 一个文件（14,753,721 字节），`apksigner verify --print-certs` 验证通过、指纹不变。上一条记录里"Release 打包后自动归档"的任务本身不用改（原来就是遍历输出目录下所有 `.apk`，产物变少了自动就只归档这一个），顺手清理了 `APK/Release/` 下这次之前几轮验证时产生的 armeabi-v7a/universal 旧归档文件（本地产物，不是仓库跟踪文件）。未验证 32 位设备上是否还有人需要 armeabi-v7a 包——这是用户明确要求去掉的，不是本次自行判断的取舍。

### 2026-09-12：彻底移除 libsesame.so 及其加载/桥接代码——比 GR 更进一步，对齐 Sure-Xu 的做法

用户要求参考 GR 去掉 SO 调用："本来就能直接调用的庄园功能，不需要调用 so"。核查后发现 M 当时的实际状态：

- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 原 295 行 `System.load(LibraryUtil.getLibSesamePath(context))` 在支付宝 Service `onCreate` 时**无条件强制加载** SO——这是 `doc/GR-Sync.md` 早前点名过的"M 现在还在真调用它"的那处。
- 但 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 原 `doFarmTask()` 里唯一会调用 native 方法的分支（`LibraryUtil.doFarmTask(task)`）**早就是整段注释掉的死代码**，实际生效的是紧接着的 `AntFarmRpcCall.doFarmTask(bizKey)` 这条纯 Java RPC 路径。换句话说：**SO 被强制加载进内存，但没有任何地方真正调用它的 native 方法**——纯粹是加载开销和 APK 体积的浪费，用户的判断是对的。

`doc/GR-Sync.md` 之前记录 GR 自己的处理是"已注释这一加载，并用 Java 实现庄园任务；其仓库仍保留 SO，旧配置 UI 也仍尝试加载，所以 GR 本身并非完全无 SO"——只是注释掉调用，SO 文件和桥接类都还留着。这次没有照 GR 这个"半吊子"做法抄，而是按 Sure-Xu 在其 `MyFix.md` 里记录的更彻底方式（"本次已删除四个架构的 libsesame.so、旧包名 LibraryUtil 桥接类、强制加载"）直接整个删掉：

- 删除 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 里的 `System.load(...)` 调用及其 `LibraryUtil` import。
- 删除 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 里那段已经注释掉的 `LibraryUtil.doFarmTask` 死代码。
- 整个删除 `app/src/main/java/io/github/lazyimmortal/` 目录（`util/LibraryUtil.java` + 转发用的 `BuildConfig.java`）——这是仅有的两个还在用旧包名 `io.github.lazyimmortal.sesame` 的文件，删除前确认过没有其它文件引用这个包（`grep -rl "io.github.lazyimmortal"` 只命中这两个文件加 `ApplicationHook.java` 的 import 行，import 已一并删除）。
- 删除 `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsesame.so` 四个架构的二进制文件。
- [app/build.gradle](../app/build.gradle) 里原来指向这个目录的 `sourceSets { main { jniLibs.srcDirs = ['src/main/jniLibs'] } }` 也一并删掉（参考 Sure-Xu `app/build.gradle`，那边压根没有这条自定义 jniLibs 目录声明）；`packaging { jniLibs { useLegacyPackaging = true } }` 保留不动，这条是给 AndroidX/Compose 自带的 native 库（比如 `libandroidx.graphics.path.so`）用的，跟本次删的东西无关。

**没有动的**：`app/src/main/cpp/watermark.cpp` 和 `CMakeLists.txt`——这是另一个独立的 native 产物（水印相关），`doc/GR-Sync.md` 早前已确认它从未接入 Gradle 构建（不产生任何 .so，`WatermarkUtil` 一直走 Java 回退值），跟这次"庄园功能调用 SO"是两回事。既然不产生二进制、不影响 APK 体积，本次没有顺手删，需要的话应该单独确认。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。实际跑了一次 `./gradlew assembleNormalRelease`：三个产物体积从上一次归档记录的 `arm64-v8a 15,168,233` / `armeabi-v7a 15,146,501` / `universal 16,397,519` 字节，降到本次 `14,753,727` / `14,753,411` / `14,767,934` 字节（universal 减少约 1.6MB，两个 ABI 专属包各减少约 400KB——两次构建之间还有其它改动，不是纯粹的 SO 体积差，但方向和量级符合预期）。`apksigner verify --print-certs` 对新产物验证通过，签名指纹与此前一致。未运行模拟器/真机验证移除 SO 加载后模块整体功能是否正常（尤其是曾经依赖 SO 才能工作、后来才切换到 Java RPC 路径的庄园饲料任务，这次只确认了编译期没有残留引用，没有做运行期回归测试）。

### 2026-09-12：家庭功能六项子功能逐一对照 Sesame-AG——修了两处，其余记录为已知差异

用户要求把 family() 剩余六项子功能（签到/顶梁柱/领奖/喂鸡/请客/分享好友）也逐一对照 Sesame-AG 的 `AntFarmFamily.kt` 查一遍。结果：

**修了 2 处**：

1. **`familyEatTogether`（请客吃饭）同款 GMT+8 时区 bug**（原 3843-3853 行）：用 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 判断当前处于早/午/晚餐哪个时段，这两个方法内部用系统默认时区的 `Calendar.getInstance()` 构造时间边界（见 [TimeUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java) `isCompareTimeStr`/`getCalendarByTimeMillis`），跟上一条记录里 `deliverMsgSend` 的时区 bug 是同一根因、同一个 `TimeUtil` 系统时区问题的另一处命中。改成直接用 `MyUtils.getInstance().get(Calendar.HOUR_OF_DAY)` 取 GMT+8 小时数比较，不再经过 `TimeUtil` 的字符串时间比较。**没有**顺手给 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 本身加 GMT+8 重载——那样改动面更大（`doc/GR-Sync.md` 早前统计过这两个方法在全项目还有很多其它调用点），只在这一处绕开。
2. **`assignFamilyMember`（顶梁柱）空列表保护缺失**（原 3778 行）：`jsonObject.getJSONArray("assignConfigList")` 取到空/缺失数组时直接往下 `RandomUtil.nextInt(0, assignConfigList.length() - 1)` 会传入非法区间（`0, -1`）抛异常。虽然外层 `try/catch` 会吞掉（不炸整个 `family()`），但对齐 Sesame-AG `assignFamilyMember` 的显式判空提前返回，改成 `optJSONArray` + 判空/判 0 长度提前 return 并打日志，问题原因更清楚，不是"莫名其妙这一轮顶梁柱没执行"。

**对照过，判断不需要改的差异**（AG 更完善，但不构成 M 这边的实际缺陷）：

- **`familySign`（签到）没有本地当日去重 flag**：AG 有 `Status.hasFlagToday(FLAG_FARM_FAMILY_SIGNED)` 双重保险，M 完全依赖服务端下发的 `familySignTips` 布尔值（`family()` 里 `if (familySignTips && ...) familySign();`）。服务端已经会在签到后把 `familySignTips` 置 false，本地没有二次保险不算错误，只是少一层防御，没有改。
- **`familyAwardList`（领奖）没有饲料容量检查**：AG 对 `awardType == "ALLPURPOSE"` 的奖励会先查 `prepareFarmAwardCapacity(count)`，容量不够就跳过保留下次领取。M 直接无条件领取所有可领奖励。M 代码库里没有现成的"饲料容量查询/预留"基础设施，这是要新增能力而不是照抄一行判断，本次没有做，是否需要防止饲料溢出浪费需要用户确认后再单独处理。
- **`familyFeedFriendAnimal`（喂鸡）**：AG 对错误码 `388`/"小鸡太小" 有专门的静默日志分支，且循环内命中当日总上限会提前整体退出（省 RPC）；M 都会落到通用的"喂食失败"日志分支，且达到上限后仍会对剩余动物逐个尝试（每个都失败一次 391，不会执行成功但会多打几条失败日志、多打几次 RPC）。不影响最终结果，只是效率和日志噪音差异，没有改。
- **`familyShareToFriends`（分享好友）里 `user.getId() != UserIdMap.getCurrentUid()`**：用 `!=` 比较 `String` 是可疑写法，但这行前面已经有 `!familyUserIds.contains(user.getId())`（family 成员本来就包含自己）先把自己过滤掉了，这个 `!=` 判断实际上是从未真正生效过的冗余条件，不是导致"把自己分享进去"这类实际错误的原因，判断为无害死代码，没有单独修。M 用的是 `batchInviteP2P` 逐个邀请，AG 用的是完全不同的 `FriendSelectionModelField`/`inviteFriendVisitFamily` 批量邀请架构（含"选中邀请"/"选中不邀请"模式），两边分享好友这部分本来就是不同世代的实现，没有可比性，不评估细节差异。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误（仅剩既有 `Status.java` unchecked 警告，与本次无关）。未运行模拟器/真机验证 `familyEatTogether` 修复后在非东八区设备上的实际触发时机，未验证 `assignFamilyMember` 空列表分支的真实触发场景（正常情况下 `assignConfigList` 应该不会为空，这是防御性修复）。

### 2026-09-12：修复道早安（deliverMsgSend）两个实际缺陷——参考 Sesame-AG，不是 GR

用户反馈"之前就是道早安有问题"，所以 GR2026 才整体改用从 Sesame-AG 移植来的 `AntFarmFamily.kt`（上一条家庭功能记录里已经确认 M 的 Java `family()` 没有照抄 GR 的 Kotlin 重写）。这次直接对照 Sesame-AG 当前的 `AntFarmFamily.kt#deliverMsgSend`（`E:\Work\Sesame-AG\app\src\main\java\io\github\aoguai\sesameag\task\antFarm\AntFarmFamily.kt:1023-1184`）逐行核对 M 的 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 同名方法，找到两个真实缺陷并修了：

1. **时间窗口用了系统默认时区，不是北京时间**（AntFarm.java 原 3909-3920 行）：AG 用 `MyUtils.getInstance()`（GMT+8）判断"是否在 06:00-10:00"，M 原来是裸 `Calendar.getInstance()`。宿主设备系统时区不是东八区时，这个窗口判断会整体偏移，导致道早安要么提前不执行要么错过窗口——这类问题 `doc/GR-Sync.md` 早前就点名过 M 的 `TimeUtil` 大部分方法仍是系统默认时区，这是其中一个具体命中的实例。改成 `MyUtils.getInstanceGMT8()`（通过 `MyUtils.getInstance()`，本会话早前加的委托）。
2. **`QueryExpandContent` 调用失败或响应字段对不上就直接放弃整次道早安**（AntFarm.java 原 4018-4028 行）：原来 `resp3.getString("content")` 只认一个字段名，`MessageUtil.checkMemo` 校验不过或者字段名对不上（`getString` 抛 `JSONException`）就整个方法 return，即使上一步 `DeliverContentExpand`（resp2）已经拿到了可用文案也不会用。AG 的实现把 `QueryExpandContent` 当作"可选二次校验"（doc comment 原话），失败时回退用 resp2 的文案，且用多个候选字段名（`content`/`expandContent`/`deliverContent`/`msgContent`/`text`，顶层找不到再进 `data` 里找）尽量取值，不会因为一个字段名对不上就整体判失败。移植了 AG 的 `extractGreetingContent()` 辅助方法（新增私有静态方法，同名），并把 `resp3` 处理改成失败/取不到内容时用 `extractGreetingContent(resp2)` 兜底，两边都取不到才真正放弃。

**没有动的**：`family()` 其余六项子功能（签到/顶梁柱/领奖/喂鸡/请客/分享好友）这次没有逐个跟 AG 对照——用户这次问题明确指向道早安，没有要求全量审计；如果这几项也有类似问题，需要单独排查。`AntFarm.java` 里还有 3 处裸 `Calendar.getInstance()`（1217/1285/1459 行），但都在"捐蛋排位"相关代码里，不属于 family 功能范围，这次没有顺手改。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。未运行模拟器/真机验证修复后道早安在真实非东八区设备上的实际执行时机，未验证 `QueryExpandContent` 真实失败场景下 fallback 文案的实际可用性（无法验证服务端在 06:00-10:00 窗口外的响应行为）。

### 2026-09-12：小鸡家庭补上"装修金购买家具"——之前的评估搞错了 M 已有的家庭功能规模

用户要求把 GR2026 `main_my` 提交 `c9287afd`（"1.9.3修改道早安调用"，标题误导，实际引入了一整个 `AntFarmFamily.kt` 家庭功能）里能移植的部分移过来。上一条相关记录（"合并 GR2026 `main_my` 分支的个人功能提交"）当时把这个当成"未来值得单独移植的大候选项"跳过了，理由是"718 行新 Kotlin + 3 个新工具类"，**这个理由是错的**：只看了 GR 新文件的行数，没有去核对 M 自己 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 里早就有一份独立演化的 Java 实现（`family()` 方法，3564 行起），已经覆盖了签到（`familySign`）、顶梁柱（`assignFamilyMember`）、领取奖励（`familyAwardList`）、帮喂成员（`familyFeedFriendAnimal`）、请客吃饭（`familyEatTogether`）、道早安（`deliverMsgSend`，OpenAIPrivatePolicy→deliverSubjectRecommend→deliverContentExpand→QueryExpandContent→deliverMsgSend 五步 RPC 链完整都在）、分享给好友（`familyShareToFriends`）——GR 新 Kotlin 文件里的七项功能，M 已经有六项，用的是不同实现但等价。

**真正缺的只有一项：装修金购买家具**（GR `autoExchangeFamilyDecoration()`）。移植内容：

- [AntFarmRpcCall.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarmRpcCall.java)：新增 `queryRecentFarmFood(int)`、`getFitmentItemList(String,int,String,int)`、`exchangeBenefit(String,String,String)`（3 参新重载，M 原有的是 5 参版本，两者场景不同不冲突）；`inviteFriendVisitFamily(JSONArray)` 用 GR 现在的活跃实现（`bizType:FAMILY_SHARE`）补上——M 原来这个方法和 GR 旧版一样是整段注释掉的死代码，两边都保留旧注释不动，新增的是一个新方法。
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java)：新增私有方法 `autoExchangeFamilyDecoration()`（逐分类分页遍历装修金商城家具列表，买得起且未拥有的就兑换，`seat3` 分类处理完后余额不足 49 装修金就提前终止），在 `family()` 末尾加了 `if (familyOptions.getValue().contains("ExchangeFamilyDecoration")) autoExchangeFamilyDecoration();`。逻辑照抄 GR，只是把 Kotlin 换成 Java、`GlobalThreadPools.sleepCompat(2000)` 直接换成 M 已有的 `TimeUtil.sleep(2000)`（没有为一行委托新建一个工具类）。
- [CustomOption.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/CustomOption.java) 的 `getAntFarmFamilyOptions()`：新增 `("ExchangeFamilyDecoration", "兑换家庭装饰(消耗装修金)")` 选项，默认不勾选（跟 M 其余家庭选项一样走 `familyOptions` 这个 `SelectModelField`，不是新开一个开关）。
- [ResChecker.java](../app/src/main/java/io/github/aw1y2z/sesame/util/ResChecker.java)：新增工具类，GR 原样搬（success/isSuccess/resultCode/memo 任一命中即成功，对"人数过多""小鸡睡觉"等已知非错误状态静默跳过不打日志）。这是真正新增的能力——M 现有的 `MessageUtil.checkSuccess`/`checkResultCode` 逻辑形状不同（忽略关键词列表、失败时打印调用栈来源都是 GR 这版特有的），不是重复造轮子。只用在新增的装修购买功能里，没有替换 M 其余地方已经在用的 `MessageUtil`。
- [Log.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Log.java)：补了 `record(tag,msg)`/`error(tag,msg)`/`printStackTrace(tag,msg,throwable)` 三个双/三参重载（`ResChecker`/`autoExchangeFamilyDecoration` 要用），实现是薄委托到 M 已有的单参版本，M 各日志开关（运行日志/异常日志等）的门控逻辑不受影响。
- [Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)：补了 `setFlagToday(tag)`，就是 `flagToday(tag)` 的别名（GR 也是这么实现的），不是新逻辑。

**没有移植/没有创建的东西**（判断为不需要，不是漏做）：

- `AntFarmFamily.kt` 整个文件——不移植，因为 M 已有的 Java `family()` 覆盖了其中六项功能，重写成 Kotlin 单例只是平白引入一次大改动和回归风险，不换。
- `UserMap.kt`——GR 这个类只是 `UserIdMap.getMyUid()`/`getUserIdSet()`/`getMaskName()` 的薄包装，M 的 `UserIdMap` 本来就直接有这三个方法（`getCurrentUid()` 由 `@Getter` 生成），没必要包一层再包一层。
- `GlobalThreadPools.java`——GR 这个类整个就是 `TimeUtil.sleep(millis)` 的一行委托，M 直接调用自己的 `TimeUtil.sleep()`，不建这个类。
- `extensions/JSONExtensions.kt`——只用到其中 `toJSONArray()` 这一个一行扩展函数（`JSONArray(this)`），没必要为一行代码移植一整个扩展函数文件，直接在用到的地方写 `new JSONArray(familyUserIds)`（本次没有实际用到，因为没有整体移植 Kotlin 文件）。
- `ModelField.java` 的 `value` 字段 `protected`→`public`——不需要，见上面的附录修正说明。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `./gradlew compileNormalDebugKotlin -q` 均无输出（编译通过，本次改动零新增警告/错误；仓库里同一批还有其它未关联改动导致 Lombok 警告数在 1～5 条间波动，属已知噪音，不是本次引入）。未运行模拟器/真机验证装修金购买的真实 RPC 交互效果，未验证家具分类遍历在真实账号余额下的实际购买行为，`decorationCoinActivityId` 默认兜底值 `"20250808"` 是否已过期未核实。

### 2026-09-12：Release 打包后自动归档 APK 到项目根目录

参考 Sure-Xu `app/build.gradle` 的 `assembleLegacyRelease` 归档任务写法（`tasks.matching { it.name == '...' }.configureEach { ... doLast { Files.copy(...) } }`，按北京时间加时间戳），GR2026 `app/build.gradle` 的 `applicationVariants.configureEach { ... assemble.doLast { project.copy { ... } } }` 也是同类思路，但用的是 AGP 老版 Variant API（`applicationVariants`），M 和 Sure-Xu 一样是 AGP 9.2.1，这套 API 在新版本上已不推荐/可能不可用，所以照 Sure-Xu 的新 Variant API 写法来，没有照抄 GR 那版。

**和 Sure-Xu 的关键差异**：Sure-Xu 只有单一 Legacy 产物，固定文件名直接拷贝；M 在 [app/build.gradle](../app/build.gradle) 里本来就有 ABI 拆分（`armeabi-v7a`/`arm64-v8a`/`universal` 三个产物，见 `splits { abi { ... universalApk true } }`），一次 Release 构建会产出 3 个 APK。改成监听 `assembleNormalRelease` 任务，`doLast` 里遍历 `outputs/apk/normal/release/` 目录下所有 `.apk` 文件逐个拷贝到 `APK/Release/`（文件名保留原样，仅追加 `_yyyyMMdd_HHmmss` 时间戳），而不是 Sure-Xu 那样固定单一文件名。

**验证**：实际跑了一次 `./gradlew assembleNormalRelease`（不是只看配置阶段），确认：
- 编译通过，5 条既有 Lombok 警告（`BaseModel`/`ViewAppInfo` 的 `@Getter` 冲突），无新增警告或错误。
- `APK/Release/` 下生成了 `Sesame-M-Normal-arm64-v8a-1.0.0_20260912_195300.apk`、`Sesame-M-Normal-armeabi-v7a-1.0.0_20260912_195300.apk`、`Sesame-M-Normal-universal-1.0.0_20260912_195300.apk` 三个文件，和 `app/build/outputs/apk/normal/release/` 下的原始产物大小一致。
- `apksigner verify --print-certs` 对归档出来的 universal 包验证通过，证书指纹（`SHA-256: 46cca38d...62e712e2`）与本次会话早前 `signingReport` 得到的一致，确认签名链路没问题。

`APK/` 目录本身没有单独加 `.gitignore` 规则——`.gitignore` 里已有的 `*.apk` 通配规则本来就会匹配到这个目录下的文件，不需要额外处理。

### 2026-09-12：GeminiAI 全量迁移 + 四个 TAB 与运行日志补充编译时间显示

用户明确要求这两项必须做，补上此前 main_my 合并记录里被跳过/只做一半的部分。

**GeminiAI 全量迁移**（对齐 GR2026 `main_my` 当前 `GeminiAI.java`，提交 `6ec46dd7` 及其后续）：[GeminiAI.java](../app/src/main/java/io/github/aw1y2z/sesame/model/normal/answerAI/GeminiAI.java) 整个 `getAnswerStr()` 换成 GR 现在的实现——模型从 `gemini-1.5-flash` 换成 `gemini-2.5-flash`；请求体加 `tools: [{"google_search": {}}]`（GR 注释说明：不开启联网搜索答不了蚂蚁庄园这类时效性常识题）；Prompt 从"只回答答案 X"换成"直接给出答案文字，严禁解释，不要标点符号。题目：X"；返回文本额外做了 `trim()` + 正则清理标点（`[。，.！!？? "'“”]`）减少候选项匹配时的干扰；请求/响应的 JSON 构造统一换成 `MyUtils.newJSONObject()`（`MyUtils.newJSONObject().put(...)` 链式写法，对齐 GR 用法）。M 原来的实现（`gemini-1.5-flash`、无联网搜索、无标点清理）整块替换，没有保留旧分支或开关——用户是明确要求"必须改"，不是可选项，所以没有做成可回退的兼容层。

**四个 TAB 编译时间显示**（参考 Sure-Xu `MyFix.md` 第 10 项的做法："已在 Miuix 主页面共用的固定顶部……显示 BuildConfig.VERSION_NAME 和 BuildConfig.BUILD_TIME"；GR 侧对应 `main_my` 提交 `66338683`，但 GR 是改 XML `MainActivity.java`，M 没有这个文件，实现方式必须重新设计）：M 的 [MiuixMainActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixMainActivity.kt) 里首页/日志/配置/设置四个 TAB 各自有独立的大标题 `Text`，**没有**像 Sure-Xu 那样共用一个真正跨 TAB 固定的顶部栏。选择了对 M 现状影响最小的做法：新增一个共用的 `TabTitleRow(title)` composable（标题左对齐、`Modifier.fillMaxWidth()` + `Arrangement.SpaceBetween` 把 12sp 的 `"${BuildConfig.VERSION_NAME}  ${BuildConfig.BUILD_TIME}"` 推到右侧），替换掉四处 `HomeTab`/`LogsTab`/`ConfigTab`/`SettingsTab` 里原来各自手写的标题 `Text`。`BuildConfig.BUILD_TIME` 在 M 的 [app/build.gradle](../app/build.gradle) 里本来就用 `TimeZone.getTimeZone("Asia/Shanghai")` 生成，天然是北京时间，不需要额外处理时区。颜色用了本文件里已经在用的 `MiuixTheme.colorScheme.primary`（没有凭空猜一个不存在的 token 名导致编译失败）。

**运行日志里补编译时间**：GR2026 `ApplicationHook.java:347` 在 MAIN_TASK 循环的"应用版本/模块版本"日志之后还有一行 `Log.record("编译时间：" + BuildConfig.BUILD_TIME);`（GR 同一处原本的"开始执行"日志被注释掉换成了这行）。M 的 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 同一位置补了这一行日志，但**保留**了已有的"开始执行" + `MyUtils.recordUserName` 那行（两者不冲突，没有照抄 GR 那样二选一）。只加在这一处——GR 全仓库搜索 `BUILD_TIME` 只有 `ApplicationHook.java`/`MainActivity.java`/`WatermarkUtil.java` 三处，`Log.java` 本身不会给每条日志自动打编译时间戳，不是"所有日志行都带编译时间"，只是运行时在日志里额外记一行。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `./gradlew compileNormalDebugKotlin -q` 均无输出（编译通过、无新增警告，仅剩 1 条与本次无关的既有 Lombok 警告）。未运行模拟器验证 Gemini 新请求的真实联网搜索效果、四个 TAB 标题栏在真机上的实际排版、以及运行日志里新增这行的实际显示效果。

### 2026-09-12：合并 GR2026 `main_my` 分支的个人功能提交（空指针/超时/单例等健壮性修复）

参考：`E:\Work\Gr\Sesame-GR2026` `main_my` 分支相对 `main` 的 47 个提交（`git log --oneline main..main_my`）。只看功能性提交，纯版本号/更新日志类提交（`更新日志：*`、`v1.9.*fix`、`新版本日志`）不看、不记录。

**已移植（均为把 `getXxx()`/`getJSONXxx()` 换成对应的 `optXxx()`/`optJSONXxx()`，防止字段缺失时抛 `JSONException`/空指针，逐处核对过 M 当前代码里存在完全相同的一行才改，不是批量替换）**：

- [AntForestV2.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java)：`resultDesc`（3 处，收能量/浇水/帮收）、`resultCode`（3 处：收能量流程 2 处 + `forFriendCollectEnergy` 1 处）。对齐 GR `fc05046c`、`e61005f1`。
- [AntSports.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antSports/AntSports.java)：`build()` 里 `endStageInfo` 判空后 `return 0`（原来 `data.getJSONObject("endStageInfo")` 在字段缺失时直接抛异常）、`treasureBoxList` 判空、`userExchangeRecords` 判空（M 用字面量 `"userExchangeRecords"`，未引入 GR 的 `MyUtils._OPT_USER_EXCHANGE_RECORDS` 常量——没必要为一个字符串字面量加依赖）、`itemId`（`getGiftItem` 附近）。对齐 GR `81258ed6`、`e6fc2655`、`c0013c97`。`reward.optString("itemId")`/`optString("name","")` 两处 M 已经是安全写法，不是这次改的。
- [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java)：三处 `getBoolean("success")` → `optBoolean("success")`（`trigger`/`queryOrdinaryTask`/`sendtrigger` 三个响应）。对齐 GR `e6fc2655`。
- [AntOcean.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antOcean/AntOcean.java)：两处 `fishVO`/`ExtrafishVO` 数组改 `optJSONArray` 判空后再循环。对齐 GR `e6fc2655`。
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java)：`resultCode`（"3D16"/"100" 两处）、`taskConfigResultVO` 判空（原来 `optJSONObject` 取出后直接 `.getString("awardType")`，对象为 null 时必炸；改成 `taskConfigResultVO == null ? "null" : taskConfigResultVO.optString(...)`，M 其余 `awardType` 调用点本来就已经是 `optString`，未改动）。对齐 GR `e61005f1`、`c0013c97`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`checkTask.get(10, TimeUnit.SECONDS)` → `30` 秒（对齐 GR `72384f8c`，理由是 10 秒在网络稍慢时容易误判超时）；加载成功 Toast 附带版本号（`"芝麻粒加载成功:" + modelVersion`，只搬了"带上版本号"这个点子，没搬 GR 那次顺带把品牌名改成"芝麻粒GR"的部分，对齐 GR `66338683` 但保留 M 自己的名字）。
- [GeminiAI.java](../app/src/main/java/io/github/aw1y2z/sesame/model/normal/answerAI/GeminiAI.java)：`OkHttpClient` 从每次请求 `new OkHttpClient().newBuilder().build()` 改成类级别单例 `CLIENT`（连接池/线程池复用），对齐 GR `cd72d38b`。**只搬了单例这一处**，GR 同一提交里对请求体/超时策略的其余改动没有搬（那属于下面"明确没有移植"的 GeminiAI 大改的一部分，两次提交内容有重叠，拆开处理）。`TongyiAI.java` 有一模一样的 `new OkHttpClient()` 反模式，但 GR 这次提交没碰过它，不在本次移植范围内，没有顺手改。
- [WatermarkUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/WatermarkUtil.java)：native 库未加载时的水印兜底文案从固定的"免费模块 交流QQ群:xxx"改成 `BuildConfig.VERSION_NAME + "  " + BuildConfig.BUILD_TIME`。对齐 GR `e22079f9`。**这个改动在 M 上实际意义更大**：`doc/GR-Sync.md` 已经确认 M 的 `watermark.cpp`/CMakeLists.txt 是死代码、没接入 Gradle 构建，`isLibraryLoaded` 恒为 `false`，也就是说这个"兜底"文案其实是 M 当前唯一会走到的路径，不是极端情况兜底。

**明确没有移植（评估过，判断当前不适合硬搬）**：

- **`model/task/fish`（福气鱼塘）**：`main_my` 里有独立的 `FishTask`/`AntFishpondTaskListMap` 等一整套模块（提交 `8b01744e` 起 9 个提交）。`doc/GR-Sync.md` 之前已经把这个列为同步候选，本次没有再重复评估细节；工作量和风险都不小（RPC 层、任务列表、黑名单、设置项注册全套），按"宁可不做也不要做一半"的原则本次不动，需要时应单独作为一次完整任务来做。
- ~~`AntFarmFamily.kt`（小鸡家庭）~~：这条评估是错的，见下一条记录——当时只看了 GR 的新 Kotlin 文件有多大，没有去核对 M 自己的 `AntFarm.java` 其实已经有一份更成熟的 Java 实现覆盖了签到/顶梁柱/喂鸡/请客/道早安/分享好友六项，规模判断严重失真。真正缺的只有"装修金购买家具"一项，已在下一条记录里补上。
- ~~GeminiAI 请求改造~~：用户后续明确要求必须做，已在下一条记录里补上完整迁移，不再是"没有移植"。
- ~~MainActivity.java 显示编译时间~~：用户后续明确要求必须做，已在下一条记录里以 Miuix Compose 的等价方式补上，不再是"跳过"。
- **`strings2.xml`/`strings_common.xml` 更新说明文案**（提交 `7e4fb5aa`）：纯 GR 自己的更新日志文本内容，与 M 无关。
- **ProGuard 混淆规则**（`cd72d38b` 里 `proguard-rules.pro`/`proguard/my-proguard-rules.pro` 的 OkHttp keep 规则）：M 的 `release` buildType 当前 `minifyEnabled false`，没有开混淆，这些 keep 规则暂时无意义，跳过；哪天 M 开混淆了需要重新评估。
- ~~`ModelField.java` 的 `value` 字段 `protected`→`public`~~：这条也是错的，见下一条记录——M 的 `ModelField` 本来就有公开的 `getValue()`，Kotlin 对 Java getter 会自动合成 `.value` 属性语法，`familyOptions.value` 这种写法不需要把字段本身改成 `public` 也能编译，GR 那次顺手把字段公开纯粹是它自己没用 `getValue()`，不是 M 需要跟进的改动，压根不用做。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过（1 条与本次无关的既有 Lombok 警告，无新增警告或错误）。未做模拟器/真机验证，未确认这些 null 安全修复对应的字段缺失场景在 M 当前环境下是否真的会被触发到（这些都是防御性修复，即便触发不到也不是坏事）。

### 2026-09-12：接入 MyUtils 调用点（此前只加了基础设施，没有真正生效）

上一条记录（"移植 MyUtils 通用部分"）只新增了 `MyUtils.java` 本身，没有任何调用点，属于死代码。本条把能对应上的调用点实际接进去。

**`recordUserName()`**：[ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 的"开始执行"（MAIN_TASK 循环内，改用 `getUserId()`）和"开始加载"（`userId` 已在作用域内）两处日志，对齐 GR `ApplicationHook.java:346,357,739` 的用法。

**`newJSONObject()`**：[RuntimeInfo.java](../app/src/main/java/io/github/aw1y2z/sesame/data/RuntimeInfo.java) 构造函数里原来 `new JSONObject(content)` 配 `try/catch(Exception)` 兜底的写法，换成 `MyUtils.newJSONObject(content)`，对齐 GR `RuntimeInfo.java:41-44` 的同一处理。行为上等价（原来的 catch-all 已经能吞掉 null 导致的 NPE），只是把"空输入返回空对象"的意图显式化。

**`closeVerification()`**：[AntForestV2.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java) 的 `whackMole()`（6秒拼手速）整体套了 `if (MyUtils.closeVerification()) return;`，以及主循环里 `queryAnimalPropList()`/`giveProp()` 两处调用改成 `if (!MyUtils.closeVerification())` 才执行——这三处 M 之前是**无条件执行**的，对齐 GR `AntForestV2.java:632,644,1638` 的逻辑后，默认行为变成"跳过"（因为 `AppConfig.closeVerification` 默认 `true`）。**这是一次真实的行为变更，不只是加基础设施**：以后如果发现森林伙伴/拼手速功能"不工作了"，先检查这个配置项，而不是当成 bug 去查。

**`closeUnRpc()`**：[AntForestV2.finishTask()](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java) 补了 GR 同名方法里对 `GYG_BK_XYK`/`GYG_jinritoutiao`/`GYG_huabeikaitong` 三类不支持 RPC 完成的任务类型的跳过分支（对齐 GR `AntForestV2.java:2467-2474`），M 之前完全没有这段过滤，会对这些任务类型发起注定失败的请求。

**没接的**：`closeErrorFunction()` 在 GR 里对应的两个例子（`goldTicket()`、`AntFarm.visitFriend()` 的"非好友"跳过）分别是已失效的死函数和与 M 现有实现形状不同的代码，没有强行套用；`getSpFunctionError`/`setSpFunctionError` 机制本身可用但还没找到 M 里对应的具体调用点（GR 那几个 key 绑定的是 `MerchantService`/`ConsumeGold`，M 已经删掉了这两个模块）。

**验证**：`./gradlew compileNormalDebugJavaWithJavac` 编译通过（只有 1 条与本次改动无关的既有 Lombok 警告）。未跑模拟器验证森林伙伴巡护/拼手速跳过后的实际游戏内效果，未验证 `finishTask` 新增分支对真实任务列表的过滤效果。

### 2026-09-12：同步 GR2026 发布签名密钥

参考：`E:\Work\Gr\Sesame-GR2026\app\build.gradle`（`signingConfigs` 块）与 `E:\Work\Gr\Sesame-GR2026\xqe.jks`。

**安全前提，先说明白**：GR2026 把 `xqe.jks` 提交进了它自己的 Git 仓库，`storePassword`/`keyPassword` 直接明文硬编码在其 `app/build.gradle`（值为 `xqe123456`）——这把签名私钥已经随 GR2026 仓库公开/泄露，不是仅本项目知道的秘密。用户已知晓这一点，明确要求 Sesame-M 直接复用同一把 GR 的 `xqe.jks`（而非自己新生成一把独立密钥），本次按此执行。这意味着任何拿到 GR2026 仓库（或这把 `.jks`）的人理论上都能签出可以通过 Sesame-M 签名校验的安装包；`applicationId` 不同（`io.github.aw1y2z.sesame` vs GR 的 `kt.gr.sesame`）能避免 Android 层面的同包覆盖安装风险，但不能防止有人拿同一把钥匙伪造一个自称"Sesame-M"发布的、`applicationId` 也改成一样的安装包。

**做了什么**：

- 把 GR2026 的 `xqe.jks`（SHA-256 `2c019576a5b746b30074057249735a9f7780876e3d34eb06672a6764d849cda9`）复制到 `E:\Work\Sesame-M\xqe.jks`（项目根目录，与 GR 存放位置一致）。
- **没有**照抄 GR 把密码硬编码进 `build.gradle` 的做法（那正是 GR 自身这把钥匙泄露的直接原因）。改为新增未跟踪的 `keystore.properties`（`storeFile`/`storePassword`/`keyAlias`/`keyPassword` 四个字段，值与 GR 一致），[app/build.gradle](../app/build.gradle) 里新增的 `signingConfigs` 块在构建时读取这个文件；文件不存在时（比如 CI 或其他贡献者本地未配置）跳过 release 签名配置，构建仍能跑完，只是产物不签名，不会因为缺文件而炸构建。
- [.gitignore](../.gitignore) 原来 `*.jks` 是被注释掉的（`#*.jks`），本次取消注释并新增 `keystore.properties` 忽略规则，确保这两个文件不会重演 GR 那次"密钥进 Git"的问题。`git status --short` 确认这两个文件不出现在待跟踪列表，`git check-ignore -v xqe.jks keystore.properties` 确认命中新增的忽略规则。

**验证**：`./gradlew signingReport` 的 `normalRelease` variant 正确解析出证书指纹（`SHA-256: 46:CC:...:12:E2`，`Valid until` 2124 年），确认签名配置生效、密钥文件可被正常加载。未执行 `assembleRelease` 完整打包，未验证产物 `apksigner verify` 通过；未检查这把私钥是否也被 Sure-Xu 或其他同源 fork 使用（不排除同一把钥匙已经被多个 fork 共用）。

**后续如果要改成独立密钥**：删除 `xqe.jks`/`keystore.properties`，用 `keytool -genkeypair` 新生成一把，`keystore.properties` 格式不用改（同样四个字段），`build.gradle` 的读取逻辑不用动。

### 2026-09-12：移植 MyUtils 通用部分，新增 `util/MyUtils.java`

参考：`E:\Work\Gr\Sesame-GR2026\app\src\main\java\io\github\lazyimmortal\sesame\util\MyUtils.java`（GR2026 当前基线，`main_my` 分支）。

**移植了什么**（新增 [MyUtils.java](../app/src/main/java/io/github/aw1y2z/sesame/util/MyUtils.java)，方法名与 GR 保持一致，便于以后按名字映射调用点）：

- `getInstance()`：GMT+8 日历，委托给新增的 [TimeUtil.getInstanceGMT8()](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java)，没有重复实现。M 原有的 `TimeUtil` 其余方法仍用系统默认时区 `Calendar.getInstance()`（95/152/168/176/185/202 等行），本次**没有**把全部日期计算改成 GMT+8——那是更大范围的改动（参考 Sure-Xu 的 GMT+8 修复），只是先把统一入口补上，供新合并的代码使用。
- `newJSONObject()` / `newJSONObject(String)`：null/非法 JSON 输入返回空 `JSONObject`，原样保留 GR 的容错语义（含"吞掉解析异常"这一点，调用方如果需要感知解析失败，不能直接用这个方法）。
- `recordUserName()` / `mUidMap`：UID 转可读昵称，纯日志可读性。SharedPreferences 名称改成 `sesame_m_myutils`（GR 用的是遗留命名 `XQE_UID`，没有照搬，避免和其它同源分支的本地存储混淆）。
- `getSpFunctionError()` / `setSpFunctionError()`（对应 GR 的 `getSp功能异常`/`setSp功能异常`）：按 App 版本隔离的一次性功能异常标记机制本身移植了，但**没有**移植 GR 那些具体的 `_访问被拒绝*`/`_系统出错正在排查*` key 常量——因为 M 还没有对应的 `MerchantService`/`ConsumeGold` 等调用点，等以后真的合并这些模块时再按需加 key。
- `closeVerification()` / `closeErrorFunction()` / `closeUnRpc()`：GR 里是硬编码 `return true`，这里改成读取新增的 `AppConfig.closeVerification/closeErrorFunction/closeUnRpc`（默认 `true`，与 GR 行为一致，但用户可在配置里关闭）——**没有**原样抄硬编码函数，理由见附录第二节。

**明确没有移植**：

- `getAppTitleExt()`（按包名前缀 `"kt"` 返回 `"GR"` 后缀）——GR 品牌相关，M 的 applicationId 不匹配，无意义。
- `_不是有效的入参` 硬编码任务 ID 黑名单——版本/账号特定快照，直接抄会过期甚至误伤。
- `encryptData()`/`decryptData()`——GR 的 `MyUtils` 版本是直通 no-op，真实加解密在 `AESUtil.java`（`//CHANGE BY KT` 标记），没有合并对象所以本次不涉及；下次合并任何调用了这两个名字的代码时，必须先确认源头是哪一个版本，见附录表格。

**当前状态**：`MyUtils.java`、`AppConfig` 新增字段、`TimeUtil.getInstanceGMT8()` 已加入，`./gradlew compileNormalDebugJavaWithJavac` 编译通过（仅有 5 条与本次改动无关的既有 Lombok `@Getter` 冲突警告）。写下本条记录时**尚未接入任何调用点**，属于死代码；已在同日的下一条记录「接入 MyUtils 调用点」里补上了 `recordUserName`/`newJSONObject`/`closeVerification`/`closeUnRpc` 的实际调用点，`closeErrorFunction` 和 `getSpFunctionError`/`setSpFunctionError` 仍未接入，见该条说明。
