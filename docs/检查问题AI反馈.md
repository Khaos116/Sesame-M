# 检查问题 AI 反馈

本轮复查范围：基准 `a6f96c78`（合并前的 my_dev）→ 当前工作区，含合并提交 `71c19826`（合入上游 `da382fa0`）、`7ca16b25` 以及其后未提交的二十一轮修复。本轮生产代码只动了 `AntForestV2.java`（`teamCooperateWater` 的未知状态日志 + `teamState` 注释），其余 21 个文件未动。

## 三项必查（GMT+8 / JSON 创建 / JSON 读取）

- **GMT+8**：本轮新增代码不涉及日期/时间，整体合并结果重扫仍无业务日历使用 `Calendar.getInstance()` / `new Date()`；唯一的 `System.currentTimeMillis()`（`RpcRequestGuard.java:242`）只做毫秒窗口比较。
- **JSON 创建**：本轮无新增解析；整体仍无 `new JSONObject(raw)` 形式，`new JSONArray()` 只用于空数组或请求参数包装。
- **JSON 读取**：本轮新增的 `homeJo.opt("nextAction")`、`homeJo.optJSONObject("teamHomeResult")`、`teamHome.optJSONObject("mainMember")` 全部是安全读取并显式判空；`opt` 返回值区分 `null`（缺失）与 `JSONObject.NULL`（显式 null），`optString` 在 `teamState` 里对 `JSONObject.NULL` 会落到默认值 `""`，两处口径一致。整体扫出的 `.getString(` 仍全是 `RuntimeInfo.getString(key)`（非 JSON API）。
- **`GeminiAI` 保留**：`model/normal/answerAI/GeminiAI.java` 仍在。

## 上一轮问题的处理结果

| 上轮条目 | 结果 |
| --- | --- |
| 1.（次要）`-1` 分支已成为未知 `nextAction` 的常态结果，但日志不带任何可定位信息，协议一改就是静默停摆 | 已修。`AntForestV2.java:7325`-`:7338` 把判定依据打进日志：`nextAction` 实际取值、`teamHomeResult` 是否为有效对象、`mainMember` 是否为有效对象。取值做了分级脱敏——缺失记 `<缺失>`、显式 null 记 `<null>`、非字符串记 `<类型异常>`、字符串但不满足 `[A-Za-z0-9_.-]{1,64}` 记 `<非标准字符串>`，只有白名单字符且长度 ≤64 的才原样输出，不打印整个首页响应。`teamState`（`:7492`）的注释也改成「个人入口仅允许 Team/Cultivate，新取值需人工核实后加入」，明确这是白名单式 fail-closed。`docs/使用说明.md:206` 同步写明日志会附带这三项、且不输出完整首页内容，`CHANGELOG.md` 记了一行。 |

## 本轮发现的问题

无。

本轮改动逐项核对：

- 三种「无值」情形互不覆盖：`opt` 对缺失键返回 Java `null`，对显式 `"nextAction": null` 返回 `JSONObject.NULL`，两个分支的顺序正确（先 `== null` 再 `== JSONObject.NULL`），不会 NPE 也不会把缺失误记成 null。
- 脱敏白名单不会被绕过：长度上限写在正则量词 `{1,64}` 里，超长串整体匹配失败并归入 `<非标准字符串>`，不会把超长响应片段写进日志；字符集排除了空格、引号、冒号等会破坏日志行结构的字符。字符类里 `.-]` 的 `-` 位于末尾，是字面量，不构成意外范围。
- 日志与判定同源：`mainMember对象` 用的是 `teamHome != null && teamHome.optJSONObject("mainMember") != null`，与 `teamState` 返回 1 的条件完全一致，不会出现「日志说有 mainMember 但判定为未知」的自相矛盾。
- 判定逻辑本身未改：`teamState` 的 `mainMember`→1、`Team`/`Cultivate`→0、其余→-1 保持；`isTeam` 仍是 `teamState(homeObj) == 1`；`:1275` 的组队主页解包和 `:1785` 的批量收能量判据不变。
- `state < 0` 分支仍然只记日志后 `return`，不发 `updateUserConfiginTeam`、不多查首页、不写 `FLAG_TEAM_MODE_SWITCHED`、不预留浇水额度；`fixTeamModeIfNeeded()` 的自愈前提（只有切换成功才写标记）不受影响。
- 新增代码全在已有 `try` 块内，`TaskCancelledException` 的透传路径未改。

## 已逐项核对、未发现问题的部分

- `MemberBrowseTasks.java`（+647 行，二十余轮改动的主体）本轮未动，上轮结论继续有效。
- 其余 20 个 Java/Kotlin 文件本轮未动，整体合并复查的结论继续有效。

## 残留项（已确认为有意取舍，仅记录影响）

1. `AntForestV2.java:977`：能量明细翻页上限由 20 页放宽到 100 页（×200 条），大号单轮最多多出 80 次明细请求，靠 `reachedOlder` 提前结束。
2. `AntForestV2.java:802`、`:5930`-`:6080`：`monopolyTasks` 开启时 `initMonopolyTaskListMap()` 每轮同步；多区域巡护按区域逐个切换，每区多一次入口回查和（非原区域时）一次证书兑换查询；地图查询失败时证书路径仍独立重查。留意风控压力。
3. `AntForestV2.java:6124`-`:6129`：取消 / 切号 / 离线 / 代际作废时跳过恢复原区域，账号会停在被切换后的保护地，等下一轮纠正。
4. `MessageUtil.java:84`-`:101`：失败回执每次都整条写入错误日志，102「服务端繁忙」的限次只作用于运行日志摘要，错误日志体积按失败次数增长；`recordFailure` 的 memo 由原始文案改为脱敏且有界的摘要（可能是「响应未提供错误原因」/「[原因含敏感信息，已省略]」），`rpc-failures.<日期>.<账号>.json` 的聚合口径随之改变，分析时按 `docs/每日异常反馈.txt` 流程注意新旧不可直接对比。
5. `Status.java:119`-`:125`：`flagToday` 每次新标记都整份 `save()` 落盘。`MemberBrowseTasks.markAdHandled`（`:778`-`:786`）一次确认最多写 4 个标记，加上 `:202` 的终态标记为 5 次整份落盘。
6. `MemberBrowseTasks.java:131`-`:138`、`:191`：`observedActive` / `observedProcesses` 是剔除循环之前的快照，剔除后不重算；活跃行被剔除而留下过期行时，该 `memberAd::` 记录当天不清理。这是为了不把「执行候选」当成「账号全部真实状态」的刻意保护，也是第十九轮不采纳「具名流程回落空流程凭据」的依据。
7. `MemberBrowseTasks.java:229`-`:246`：同一身份不按过期/存活择优，由后查询的来源覆盖先查询的；靠后来源给出陈旧过期行时当轮跳过该任务。
8. `MemberBrowseTasks.java:655`：`adSnapshot` 把过期也算终态并立即短路，`matched` 的择优不再执行。
9. `MemberBrowseTasks.java:698`-`:711`、`:725`-`:745`：`adConfirmedKey` 的非受检异常调用点在 `load()` 的确认循环里，抛出会结束整轮会员任务；当前不可达（校验与 `adContract` 约束重合），属跨函数隐式耦合。
10. `MemberBrowseTasks.java:713`-`:733`：`advancedAfterTerminal` 对「未推进」一律按「已确认」处理，周期计数回退（周期重置）也算未推进，该流程当天后续阶段被静默跳过。
11. `MemberBrowseTasks.java:580`、`:945`-`:949`：浏览计时跨业务日时只记「记录不属于当前业务日」，当次真实完成不打印完成和积分日志；`confirmationDay`（`:163`）与 `logConfirmed`（`:946`）各取一次时间，跨午夜那次迭代会出现「标记按新一天写入、奖励日志被抑制」的瞬时不一致。
12. `MemberBrowseTasks.java:160`：广告行执行条件含 `!complete`，签到页以「重复返回最后一页」或单页失败结束翻页时，当轮所有广告任务不执行、已提交的也不确认。
13. `MemberBrowseTasks.java:349`-`:357`、`:139`-`:211`：`riskPause` 缺字段 fail-closed，升级前已有的暂停记录在其剩余窗口内（最长一天）任何一次普通退避都会停掉整轮会员任务；跨日全量清扫受 `validIds` 约束，签到页任意一行缺 `configId` 就整轮跳过，只剩 `:195` 对匹配项的收口。
14. 广告路径请求量：`adSnapshot` 走完整 `load()`，完成一个广告任务的成本是签到页全量翻页 ×2 + `currentMemberAdTasks` ×2 + `currentMemberProcesses` ×2，再乘 `run()` 轮次（最多 12）。理由是完成 RPC 改变服务端状态、快照不能复用。
15. `AntForestRpcCall.java:548`-`:554`、`:615`-`:625`：`listMonopolyTasks` 把 `regionCode` 从顶层移进 `extend`，证书相关接口 `source` 由 `monopoly_auto_exchange` 改为 `monopoly_exchange_mappage`。两处都是按服务端实际协议调整，代码侧无法自证，需实机确认任务列表与兑换仍正常。
