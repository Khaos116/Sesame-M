# 检查问题 AI 反馈

检查对象：`my_dev` 分支，基于 `8806405f` 的未提交修复（`AntOrchard.java`、`MiniProgramHook.java` 和 3 个回归脚本）。
检查方式：只读审查，没有改代码。

验证结果：`check_upstream_oct20`、`check_orchard_draw`、`check_forest_task_policy` 都通过。

当前没有必须修改的代码问题。上一轮的 2 个问题都已处理。

## 上一轮问题的核对结果

### 问题 1：已修复

新增了 `attemptOrchardAward`。每一档领奖的失败次数用 `Status` 当日整型标记计数，所以按账号、按东八区的业务日分开统计。

- **上限：** 每档每天最多失败 3 次，达到后当天停止领这一档，并留下一条日志。第二天自动恢复。
- **成功或已确认：** 领奖成功，或者按任务列表确认已经领到，失败计数都会清零。
- **取消和异常：** 取消不消耗次数，取消异常会继续往外抛。其它异常会先计一次失败，再往外抛。
- **计数的 key：**
  - 抽抽乐：`activityId + 场景 + taskType + rightsTimes`
  - 农场乐园：`attemptKey + alreadyReceiveAwardCount`

  领成功之后档位会变，key 也跟着变，所以后面的档位不受上一档 3 次上限的影响。
- **日志：** `TaskAward` 的日志回调改成传 null，失败时只由 `recordOrchardAwardFailure` 打一条日志，不再重复。
- **农场乐园：** 达到上限后不会再设置 `progressed`，所以不会触发本轮重查。

### 问题 2：已修复

`loadClass` 的钩子单独放在 `sDeferredUnhooks` 里。两个网络类都装好钩子之后，`markNetworkHookInstalled` 会把它卸掉，并保留 `deferredNetworkHookInstalled=true`，这样配置重载时不会再装一次。下面几种情况也都处理到了：

- **装钩时类已加载：** 两个类在 `install()` 时就已经加载好的话，会直接标记完成，跳过延迟监听。
- **并发：** 两个 `loadClass` 钩子装到一半时如果目标类已经装好，后一个钩子会被 `addUnhook` 拒掉并当场卸载。
- **卸载：** `uninstall()` 会一起清掉延迟钩子和已装记录。

所有状态变更都在 `sUnhooks` 锁里进行，`deferredNetworkHookInstalled` 已改成 volatile。
