# 检查问题 AI 反馈

检查对象：`my_dev` 分支，基于 `e06e0101` 的未提交修复（`AntOrchard.java`、`check_merge_review.py`）。
检查方式：只读审查，未改代码。

验证结果：`check_merge_review` 通过。

当前没有必须修改的代码问题。上一轮问题 1 已处理。

## 上一轮问题的核对结果

问题 1 已处理。

- 修改：未知动作兜底分支的 `pendingVerifyTasks.put` 已移到 `via != null` 里面，和 VISIT、TRIGGER 分支一致。
- 失败不会被拉黑：102、限流、坏响应、RPC_SKIPPED、网络异常这几种失败，都不会进入列表核对，也不会写当日标记，同一天还能重试。
- 400000040 仍然会核对：这种情况由 `finishTaskTwice` 自己放入核对，原有的列表核对保持不变。
- 回归：已改为走真实的 `handleTaskList`，会执行 `verifyPendingTasksByList`；测试替身的 `checkResultCodeAndMarkTaskBlackList` 也会像生产代码一样，跳过临时错误。

## 仍需实机留意（低优先级，沿用上一轮问题 2）

VISIT、TRIGGER 和兜底分支接口返回成功后，只等 3 秒重新拉取列表。如果任务仍是 TODO，就会被自动拉黑 3 天，核对时也不比较 `rightsTimes`。

这是上游有意的设计。如果日志里出现“肥料任务⚠️未完成[…]，已交给自动拉黑机制”，而这个任务原来能正常完成，再按上一轮的建议调整。
