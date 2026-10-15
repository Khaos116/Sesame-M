# 检查问题 AI 反馈

检查对象：`my_dev` 分支，HEAD `448979d5` 加工作区未提交改动（信用宝箱、科学放生、真爱双奖励确认）。
检查方式：只读审查，未改代码。对照 Seedum FluxV15 5406 动态模块（semper.js 20261012，`.git/seedum-fluxv15-5406/runtime-cloud`）的字节码：信用宝箱看函数 2698（查询和判断）、3430（领取），科学放生看函数 3268（查询和判断）。

验证结果：`check_seedum_features`、`check_ocean_friend_flow`、`check_love_reward_probe` 通过。

用户反馈：科学放生实机流程正常；信用宝箱因没有宝箱，尚未实测领取。

**更正上一轮第 2 项**：上一轮建议信用宝箱改用会员场景的三个条件（`needReceive`、`taskPrizePopup`、`needReceiveTaskCertCnt`），这是推测，撤回。Seedum 的 2698 对会员场景和信用场景共用同一个函数，只读 `data.taskModule.needReceive`，且要求它是布尔 `true` 才调用领取；`taskPrizePopup`、`needReceiveTaskCertCnt` 在整个模块里都没有被读取。M 只认 `needReceive` 是对的。

## 问题 1：两条入口诊断日志每轮都写，开关关闭也写

位置：`AntMember.java` `run()` 开头的 `Log.i("信用宝箱[入口]：当前账号开关=...")`；`AntOcean.java` `run()` 开头的 `Log.i("科学放生[入口]：...")`。

`Log.i` 写运行日志，两行都不判断开关，每个账号每轮各打一行。科学放生已实测，这行可以删除；信用宝箱的入口日志等实测确认后删除，或先改成只在开关开启时打印。

## 问题 2：缺少 needReceive 字段时，M 当成接口失败，Seedum 当成没有宝箱

位置：`AntMember.java` `creditGameCenterPending` / `creditGameCenterTaskPrize`。

Seedum 2698（0x00c5–0x00e9）在 `taskModule` 为 null 时用空对象代替，之后 `needReceive` 不是布尔 `true` 就直接跳过领取，不报错。

M 在 `needReceive` 缺失或不是布尔值时，写“接口失败或待领状态无效”并调用 `Log.markUnresolved()`。如果服务端在没有宝箱时不返回这个字段，每轮都会多一行失败日志，会员模块每轮都被标为未完成。

处理：查询本身成功（`memberFeaturePayload` 通过）但 `taskModule` 或 `needReceive` 缺失时，按“暂无待领宝箱”处理，与 Seedum 一致；只有查询失败才记失败。

## 与 Seedum 的行为差异（M 的取舍，不算缺陷）

- **信用宝箱不产生宝箱**：Seedum 2698 在 `dailyTaskCertCnt < dailyTaskCertCntUpperLimit` 时会先替用户完成游戏任务（0x0165 开始的分支，信用场景走 h4l/l4l/p4l/q4l 几项），宝箱是这样产生的。M 按设计只领取已有的宝箱，不玩游戏，所以不手动玩游戏就不会有宝箱。这也是这次测不到的原因。
- **信用宝箱连续领取**：Seedum 领取成功后重新查询，`needReceive` 仍为 true 就再领一次，直到没有。M 每轮只领一次，回查仍有待领就留到下一轮。
- **科学放生**：Seedum 3268 只要任一阶段 `exchangeStatus === "canExchange"` 就调用兑换，兑换成功就重新查询再来，没有次数上限，也不核对阶段是否推进。M 加了每轮 10 次上限和阶段推进核对，比 Seedum 严格；`phaseList` 缺失时 Seedum 静默跳过，M 记失败，影响同问题 2，但实测接口正常返回，暂不需要改。

## 已核对、没问题的点

- 请求参数与 Seedum 一致：信用宝箱 `sceneId=zmyxzx_XCJNCE`、`source=zmxy_mc_xddpcz00`、`deviceLevel`/`unityDeviceLevel=high`；科学放生 `source=ANT_OCEAN` 和 `uniqueId`。
- 真爱双奖励：`dailyRewardCode` 只接受一条已知每日奖励，加至多一条在 `pageProperties.awards` 目录里有记录的首次阶段奖励；其它组合都拒绝。
- 三项规则：没有新增日期计算；新载荷用 `MyUtils.newJSONObject()` 创建；读取都用 `opt*` 并判空。

## Codex 处理记录（2026-10-15）

- 问题1：删除科学放生的临时入口诊断；信用宝箱入口移到开关开启分支，关闭时不写。
- 问题2：已用回归复现成功空查询误标未完成；初查缺少/null的taskModule或needReceive改按暂无奖励处理。错误类型、失败查询仍报未确认，领取后的回查仍要求明确布尔状态，避免缺字段被误报为领取成功。
- 保留只领取已有信用宝箱、每轮一次批量领取及已实测科学放生流程，不恢复自动游戏推进。最终92/92全量回归、Normal Debug Java/Kotlin编译及diff空白检查通过；本轮未重新打Release包，信用宝箱实际领取仍待有奖励时验证。
