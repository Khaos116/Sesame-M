# 检查问题 AI 反馈

检查对象：`my_dev` 分支，HEAD `e14055ff`（信用宝箱与科学放生）、`954b2d0e`（真爱双奖励确认）。
检查方式：只读审查，未改代码。对照 Seedum FluxV15 5406 动态模块（semper.js 20261012）字节码函数 2698、3430、3268。

验证结果：`check_seedum_features`、`check_ocean_friend_flow` 通过。

当前无必须修改的代码问题。上一轮两项都已处理。

## 上一轮问题的核对结果

问题 1 已处理。科学放生的入口日志已删除；信用宝箱的入口日志移到开关开启的分支内，关闭时不写。

问题 2 已处理。`creditGameCenterPending(root, allowMissing)` 在首次查询时，`taskModule` 或 `needReceive` 缺失、为 null，都按“暂无待领宝箱”处理，与 Seedum 2698 一致；查询失败或字段类型错误仍报未确认。

## 实机时留意（低优先级，不阻塞）

1. **领取后回查仍要求 `needReceive` 明确为 false。** 回查时调用 `creditGameCenterPending(after, false)`，字段缺失返回 null，会记“回查失败或结构无效”并标记未完成。如果服务端在没有宝箱时不返回 `needReceive`，那么每次领取成功都会被记成未确认。Seedum 判断领取结果看的是领取回执（3430 读 `data.prizePopup.prizeItems`），不靠回查。有宝箱实测时看日志：如果领取确实到账却出现“回查失败或结构无效”，把回查改成与首次查询相同的缺省处理即可。
2. **查询成功但没有 `data` 字段时仍按失败处理**（`data == null` 直接返回 null）。Seedum 在这种情况下按没有宝箱处理。实际回执应当都有 `data`，只是边界差异。

## 与 Seedum 的行为差异（M 的取舍，不算缺陷）

- 信用宝箱只领取已有的宝箱，不替用户玩游戏任务；Seedum 在 `dailyTaskCertCnt < dailyTaskCertCntUpperLimit` 时会先完成游戏任务来产生宝箱。所以不手动玩游戏就不会有宝箱可领。
- 信用宝箱每轮只领一次；Seedum 领取成功后会重查并继续领取，直到没有。
- 科学放生每轮最多 10 次并核对阶段推进；Seedum 没有上限，也不核对推进。
