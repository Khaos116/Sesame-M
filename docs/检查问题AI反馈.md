# 检查问题 AI 反馈

检查对象：`my_dev` 分支，合并提交 `4a36095c`（合入上游 `fe02d4d4`，包含 `69489b1e` 和 `a8b9922c`）。
检查方式：只读审查，未改代码。对照上游原始提交，并检查了改动所在功能的完整流程，不只看 diff。

覆盖的功能：

- 森林寻宝活力值兑换：`ForestChouChouLe.chouChouLescene` 整个循环，包括 FINISHED 领奖分支、兑换分支、`probeChouChouLeStatus` 和 `TaskAward.confirmReceivedOrBlackList`。
- 森林乐园限定任务：`AntForestV2.queryOptionalPlay` 整个循环，包括开箱分支和 `openForestLeyuanBoxes`。
- `TaskAttemptPolicy.handle` 的回查、延迟拉黑和兜底流程，以及 `probeWithPolling`。
- 交易/履约拦截：`TaskAlternative.isTransactionTask` 的所有调用点。重点核对了庄园抽抽乐的三条路径：`doFarmTask` 两种重载和 `TaskAlternative.request`。

验证结果：`check_task_attempt_policy`、`check_forest_task_policy`、`check_upstream_oct15`、`check_farm_task_flow`、`check_forest_farm_progress`、`check_upstream_merge`、`check_merge_review` 全部通过。

当前没有必须修改的代码问题。

## 核对结论

1. 兑换流程没有照搬上游，保留了 M 的写法，结果正确。
   - 上游把兑换包进 `TaskAttemptPolicy.handle`，靠 30 秒轮询等待列表落定。M 直接调用 `attemptExchangeVitality`，并在每次兑换后立即领奖。
   - 下面几种状态的流程都已核对，没有重复兑换，也没有重复领奖：
     - 从 TODO 开始；
     - FINISHED 中间态（1/2）；
     - RECEIVED 中间态（1/2）；
     - 领奖后列表仍是旧的 FINISHED。
   - 进度缺失或无效时不兑换。原来 `rightsTimesLimit<=0` 会兜底兑换 1 次，这个兜底已取消。上游在这种情况下用 `getInt` 抛异常，也同样不会兑换，所以两边结果一致。
2. 开箱类乐园任务改为直接开箱。
   - 额度不足时返回 UNABLE，但不写当日标记，也不走伪申报。
   - 本轮用 `attempted` 去重，不会死循环。
   - 开箱后回查额度没有减少时，标记为未完成，并在本轮停止。
3. `TaskAttemptPolicy` 只在 DONE 时轮询，其他结果只单次回查。
   - `pr == null` 按 UNKNOWN 处理，所以不会拉黑，也不会写失败标记，这和 M 原有的保护一致。
   - `PROBE_ROUNDS` 已改为 10，与上游一致。
4. `choukuan` 拦截已生效。
   - 庄园抽抽乐的兜底分支会先用 `isTransactionTask(bizKey)` 跳过这类任务。
   - `TaskAlternative.request` 和 `doFarmTask(bizKey)` 内部也会拦截，返回 `{}`。
   - 返回的 `{}` 里没有“不支持rpc调用”文案，所以不会被误拉黑。

## 实机时留意（低优先级，不阻塞）

1. 兑换后 1.5 秒就领奖。如果服务端异步落定较慢，领奖会失败，回查结果仍是 TODO，本轮会停止后续兑换（日志：“兑换后领奖未确认，本轮不继续兑换”）。
   - 外层重拉列表时，就算状态已经变成 FINISHED，也会因为 `handledThisRun` 里已有 `taskType` 而跳过领奖。
   - 这一档奖励要等下一轮森林任务才领取，不会丢失。
   - 如果日志里经常出现这条提示，可以在领奖前加一次短轮询回查。
