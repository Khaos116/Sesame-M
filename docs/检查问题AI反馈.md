# 检查问题 AI 反馈

检查对象：`my_dev` 分支，基于 `d04d4ce1` 的未提交修复（`AntForestV2.java`、`ForestChouChouLe.java`、`check_forest_task_policy.py`）。
检查方式：只读审查，未改代码。

验证结果：`check_forest_task_policy`、`check_task_attempt_policy`、`check_forest_farm_progress` 通过。

当前没有必须修改的代码问题。上一轮 3 项都已处理。

## 上一轮问题的核对结果

**问题 1 已处理。**`forestVitalityForge` 的判断顺序如下：

1. 原来的营销类判断仍然最先执行，即 `zhrw_` 前缀或标题含“免费拿/绿植”。判断时 `bizInfo` 传 null，只看类型和标题，所以 `zhrw_jiaoshui` 这类任务继续按“看一眼”上报。
2. 只有不是浇水、也不是能量雨的任务，才会根据 `EFFECTIVE_TASK` 或 `taskJumpUrl` 判为“看一眼”任务。
3. 浇水任务带跳转地址，但浇不了水（没开浇水或没选好友）时，返回 null，不会退回去上报“看一眼”。这和合并前一致。

新增的回归覆盖了以下情况：

- 带跳转地址或 `EFFECTIVE_TASK` 的浇水、能量雨任务，都执行真实动作；
- 浇水关闭时返回 null；
- `zhrw_` 营销任务仍然优先按“看一眼”处理。

**问题 2 已处理。**

- 任务为 FINISHED 但进度无效时，用 `handledThisRun` 做本轮防重。这种情况下只领奖、不兑换，也不写档位标记，下一轮还能重试。
- 场景码缺失时，仍然领奖和兑换都不做。
- 进度有效时，判重键和兑换循环的逻辑都没有变。

**问题 3 已处理。**仓库根目录的 `++polls[0]` 已删除。

## 说明（不需要修改）

如果服务端一直返回“FINISHED、但进度无效”，而且领取接口一直失败，那么之后每一轮森林任务都会再请求一次领取，这是有意保留的重试。回查结果只要是 RECEIVED 或 GONE，就能确认已经领到，不会被误拉黑。
