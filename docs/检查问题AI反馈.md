# 检查问题 AI 反馈

检查对象：`15b39d84` 之后的未提交修复，重点是 `AntForestV2.probeMonopolyStatus()`。
检查方式：只读审查，没有改代码。

验证结果：`check_ag_features`、`check_forest_task_policy`、`check_merge_review`、`check_upstream_merge` 全部通过。

当前没有需要修改的代码问题。上一轮的问题已修复。

## 上一轮问题的核对结果

已修复。现在 `probeMonopolyStatus` 的做法是：
- **查列表：** 固定用 `MONOPOLY_TASK_SCENE_HSDWY`，和 `monopolyTask()` 查列表时一致。
- **找任务：** `taskType` 和任务自带的 `sceneCode` 都对上，才算找到这个任务。所以场景码不同时，列表找不到这个任务，不会再被误判为已领。
- **结果不确定时：** 下面这些情况返回 `UNKNOWN`，不当作已领：
  - 列表查询失败或缺失；
  - 列表里有条目缺少 `taskType` 或 `sceneCode`，或者这两个值的类型不对。
- **只有一个调用方：** `probeMonopolyStatus` 只被领奖回查调用，不影响别的流程。
