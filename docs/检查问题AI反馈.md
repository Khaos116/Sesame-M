# 检查问题 AI 反馈

检查对象：`my_dev` 分支，HEAD `ca51e2ae` 加工作区未提交改动（codex 收尾处理后的代码）。
检查方式：只读审查，未改代码。

上一轮两件事都已处理：空文件 `0)` 已删除，请求保护暂停的日志已补上。剩一个小问题。

## “GR已知异常任务”的本地跳过会被当成请求保护暂停输出

位置：`app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java:2901-2905`。

新加的判断用 `code=400000040` 区分“本地已知不支持”和“请求保护暂停”。但 `RpcRequestGuard.shouldSkip()` 只给两类跳过加这个 code：已学习的规则和精确匹配的完成接口（`rpc/intervallimit/RpcRequestGuard.java:240`）。第三类本地已知跳过没有这个 code，即 `isKnownUnsupported()` 里的硬编码规则，其中包含庄园 `doFarmTask` 的两个 `bizKey`：`_chouchoulechoukuan` 和 `TAO_GOLDEN_V2`（同文件 202-204）。

这两个任务出现在饲料任务列表时，每次执行都会输出：

> 庄园饲料任务[标题]本地请求保护暂停，未发送RPC；com.alipay.antfarm.doFarmTask：跳过GR已知异常任务

两处不对：它属于“本地已知不支持”，按要求应该静默；文案写的是“请求保护暂停”，实际不是暂停。

判断可以改成看跳过原因，或者让 `shouldSkip()` 给这一类也带上可识别的标记。

归档：上述修复与回归已提交为 `e64efc5b`；各轮“未提交”描述保留为当时核验状态。
