# 修改记录

简明改动清单，按时间倒序追加，方便快速查看每次改了什么。详细的移植/合并原委、
取舍理由见 `doc/MyFix.md`；本文件只记一行摘要 + 对应 commit。

## 2026-09-16

- Merge `origin/MIUIX-api102` 至 `ea6dd5e8`（四个提交）：合入弹窗搜索、配置/统计加载优化及模块仓库发布工作流；保留本地账号标题与整数单位修复，修正上游单选追加旧选择的问题，九项回归及编译通过。
- 未提交：修复本轮结束日志被未来定时任务阻挡；保留已到期/运行中任务等待和切号隔离，自动切号判断不变，回归及编译通过。
- 未提交：删除信用2101及视频红包（保留好家无忧卡）；黄金票仅保留每周福利并归入会员分组，青春特权仅保留森林道具入口，清理重复/失效实现及专用 Hook。
- 未提交：修复 RPC guard 在 `error` 为空串时未回退 `resultCode`，补充错误码回退、优先级与冷却回归。
- 未提交：修复会员业务拒绝/请求冷却被误判为全局掉线；检查失败不再强制拉起登录页，区分超时与中断并取消检查线程，补充新旧 RPC 桥及调度回归。

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
