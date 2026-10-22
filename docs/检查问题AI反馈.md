# 检查问题 AI 反馈

检查对象：合并提交 `3148566b`，合入上游 `e1a08dbe`。上游的两个提交是 `814bce8d`（会员广告任务）和 `fdf58306`（无纸阅读复用登录凭据、修正请求头）。比较基准是 `859417ec`。
检查范围：`AntMemberRpcCall.java`、`MemberBrowseTasks.java`、`forestRead/ReadForestRpcCall.java`、`forestRead/ReadForestTask.java`。
检查方式：只读审查，没有改代码。

已跑的回归：`check_ag_task_sync`、`check_forest_read`、`check_upstream_merge`、`check_merge_review`、`check_standalone_no_xposed_class`，全部通过。

当前没有需要修改的代码问题。

## 核对要点

**会员广告任务**
- 只有满足以下条件的任务，才走 `performAd`：
  - `adTask=true`；
  - 没有 `targetBusiness`；
  - 不是交易类任务；
  - `lightsAdExtMap` 里的 `bizId` 和 `adId` 有效；
  - 是纯浏览任务，浏览时间至少 15 秒。
- 申请和完成都会先把记录写盘，再发请求：
  - 只有明确失败或 `RPC_SKIPPED` 时才撤销记录，结果不确定时不会重复提交；
  - 计时不会超过任务规定的时长；
  - 完成前会重新查一次同一任务，确认任务参数没变、还在同一阶段。
- 积分以列表回查到的完成或进度推进为准，不会提前记配置里写的积分。
- 被业务拒绝后，用当天的 `agReject` 标记停止再试。
- 记录按 GMT+8 的业务日清理。
- 新增的 JSON 对象都通过 MyUtils 创建，读取都用 `opt` 并做类型校验。

**无纸阅读**
- Token 存在 `RuntimeInfo`，这份数据按 uid 分开保存，不会串到别的账号。
- 每轮开始先用 `/authorization` 校验 Token：
  - 失效（HTTP 401/403，或业务返回提示需要登录）时，清掉缓存，重新授权登录；
  - 网络等其他错误时，本轮停止，下一轮再校验。
- `login` 前会先清空旧 Token。日志里的 Token 和 auth_code 继续脱敏。
- 删掉了 `alipayminimark` 请求头，新加的请求头和 UA 与抓包一致。
- 读取网络类型和屏幕规格的方法出错时，返回默认值。
- M 原有的这些规则都保留了：15 分钟运行上限、严格进度判断、取消和代际作废后继续往外抛。
