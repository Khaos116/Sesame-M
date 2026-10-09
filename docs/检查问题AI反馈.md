# 检查问题 AI 反馈（第四轮）

检查对象：`my_dev` 分支，HEAD `f6039def` 加工作区未提交改动。
检查方式：只读审查。77 个回归脚本全部通过，Debug Java/Kotlin 编译成功。Release/R8 未独立复跑，未做实机验证。

本文件只列仍存在的问题，已解决的不再列出。

**本轮没有发现需要修改代码的问题。**

## 仍待实机确认（无法通过静态检查或 JVM 回归验证）

路径前缀省略为 `app/src/main/java/io/github/aw1y2z/sesame/`。

1. **金豆商城订单字段名**（`model/task/goldenbeans/GoldenBeansMall.java` `reconcile()`）
   按 `orderInfos[].orderNo` 匹配订单号，源码已标注无实机样本。字段名不符时，带订单号的回执只能走"同日 + 订单数增加 + 可兑次数减少"确认，跨天后需人工解除。拿到一次真实兑换的响应和订单列表后核对并更新注释。

2. **福利金 `TO_RECEIVE` 阶段码**（`model/task/antMember/WelfareFund.java`）
   用 `stageCode=receive` 提交，出处为 SJ 3.6.67 反编译代码，未经第二方核对。需要一次真实 `TO_RECEIVE` 任务的请求和响应确认。

3. **小镇场景任务**（`model/task/omegakoiTown/OmegakoiTown.java` `completeQuests()`）
   `getUserQuests` 的响应结构和 `questDone()` 识别的状态字段均无实机样本。模块此前在 M 中未注册，本次合并后首次可用。

4. **金豆商城兑换全流程**
   扣豆、落单、`canBuy` 语义、`userDayLeftAmount` 是否随兑换递减，均只有模拟夹具覆盖。

## 未覆盖范围

- APK 反编译产物未对照。
- `:app:assembleNormalRelease`（R8/签名）本轮未独立复跑。
- 本轮改动尚未提交。
