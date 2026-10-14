# 检查问题 AI 反馈

检查对象：`my_dev` 合并提交 `a8c56f4c`（合入上游 `6b195926`，上游新增 `489021fc`、`0e2e6650`、`8ec8ab21`、`82e6e28b` 及合并提交）。
检查方式：只读审查，未改代码。逐项对照上游原始提交、合并前的 `24955ca5` 和合并结果，并检查涉及功能的完整调用链。

验证结果：`check_upstream_merge`、`check_farm_family`、`check_forest_farm_progress`、`check_forest_task_policy`、`check_upstream_energy_manure`、`check_daily_task`、`check_account_switch` 通过。

当前无待处理的代码问题。

## 逐项核对

### 配置保存不触发执行（`ApplicationHook`、`NotificationUtil`）

- `exec` 参数从 `initHandler` 经 `runInit` 传到 `initializeHandler`，M 的 `TaskLifecycle.Freeze` 所有者和后台初始化线程都保留。
- `sesame.restart` 广播默认 `exec=false`。发送方只有配置保存（字段页、设置页、配置导入、`broadcastRestart`），以及前台服务被销毁时的 `restartByBroadcast`，后者显式带 `exec=true`。手动执行走另一条广播，不受影响；首启、切号仍调用 `initHandler(true)`，会立即执行。
- 不执行时调用 `execDelayedHandler(max(检查间隔, 60 秒))` 续上排期，并用 `updateIdleText` 刷新通知；`nextExecTime` 由 `execDelayedHandler` 先写入，通知能显示下次执行时间。

### 小鸡乐园开箱与刷任务（`AntFarm.drawGameCenterAward`）

- 合并结果比上游更严格：额度字段缺失时停止；开箱回执次数缺失或没有减少时停止；刷任务前重新查询真实额度作为基线。
- `remainToTask = limit - beforeUsed`，取自开箱后的回查结果；上游用的是开箱前的值，合并结果更准确。
- 当日重试计数用 `Status.getIntFlagToday/setIntFlagToday`，按 GMT+8 跨天清空。旧标记 `antFarm::gameDrawTaskSkip` 已没有引用。
- 补了 `TaskCancelledException` 的向上抛出。

### 家庭道早安（`AntFarm.deliverMsgSend`）

与上游一致。只有发送成功才记当天完成；任务列表为空或不是 TODO 时只跳过本轮。函数只在 06:00–10:00（GMT+8）运行，不会全天反复查询。

### 森林救援队（`AntForestV2.checkAndDoEndGameTask`）

- 上报后等待 3 秒再回查，只有 `FINISHED` 或 `RECEIVED` 才记当天完成。上游的条件是“非空，且不是 TODO 或 NOT_TRIGGER”，合并结果更严格。
- 只在 `canPlayGame` 为真且当天未完成时调用；未知状态只写一行日志，不会重复上报。

### 活力兑换系统繁忙

与上游一致，按商品记当日次数，满 3 次才停止当天兑换。

### 能量统计只计收入

`energy <= 0` 的记录跳过，也不计入 `scanned`。`energy` 字段必须是数字的类型校验仍在前面，分页完整性、溢出、账号和跨日检查都保留。

### 移除选教卡场景任务

`energySceneTask` 字段、`doEnergySceneTask`、`attemptSceneTask` 已删除，源码、检查和文档中没有残留引用。容器任务统一走 `awardFinishedChildren` 和 `doChildTask`，已完成的奖励照常领取。

## 行为变化（上游设计，非缺陷）

- 任务正在运行时保存配置：重载会先停止当前这一轮（原来就是这样），但之后不再立即重跑，要等一个检查间隔。改完配置想马上看效果，需要手动执行。
