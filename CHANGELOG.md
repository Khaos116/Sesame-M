# 修改记录

简明改动清单，按时间倒序追加，方便快速查看每次改了什么。上半部分为一行摘要 + 对应 commit；
文末「详细记录」保留原 `docs/MyFix.md` 迁入的历史取舍，并追加合并审查结果；历史记录不改写。`docs/MyFix.md` 只放规则。

写作约定：每天一节，每次改动一条（`- 类型 commit：一句话`），单条约一行、尽量不超 150 字，只写改了什么加关键取舍；
推理过程、日志证据、被否掉的方案不写入（要查时看 `git log` / 当次对话记录）。已写超的由整理人直接压缩，原文在 git 历史里可找回。

## 2026-10-07

- merge `35e0a8c6`：合入MIUIX-api102至`1763096c`共12个提交，补森林统计/通用游戏任务、会员宝箱、每日状态和场景施肥；保留M预算、账号保护及Gemini。

- fix `fe2e796b`：各类别“立即执行”标题行上下各8dp；仅兑换/仅钓鱼等快捷按钮区下方间距增加至16dp，隔开白色配置卡片。

- fix `b423b927`：缩小各类别“立即执行”的文字与内边距，保证48dp最小点击高度，标题行下方留8dp空隙，避免贴住配置卡片。

当前提交状态：阶段1～65及1.2.11版本更新已归档于`57f3a969`，快捷按钮横排已归档于`662f6093`；以下阶段条目的“未提交”保留当时记录。

- fix `662f6093`：配置页能量雨/打地鼠等快捷子动作改为横向排列，水平及换行间距8dp，窄屏自动换行；执行入口沿用原逻辑。

- feat `57f3a969`：提交阶段1～65移植、70项回归及1.2.11版本文档，覆盖112个源码/检查/说明文件，余额宝开通/转账/付款类排除。

- release（未提交）：版本1.2.10→1.2.11，汇总阶段1～65及正式包后的开发追加，按模块写入《版本更新说明》；开通/转账类明确排除，保留历史版本记录。

- fix（未提交）：按用户明确要求排除余额宝开通/开户/转账/付款类，标题/分类/操作过滤覆盖勾选、浏览标签、实时重查及已完成奖励列表，补生产回放。

- feat（未提交）：阶段63～65补家庭低贡献安排/分享名单、Sen S2目标名次及AG原生纯豆权益兑换；未知兑换回执持久防重。WebDAV后续按用户要求排除，后台/无合同项目跳过。

- feat（未提交）：阶段60～62按SJ反编译补花花卡任务signup/send/award与百次卡自动选择/报名受理续跑；按AG补余额宝主入口报名状态分流，受理与独立回查分开记录。

- fix（未提交）：阶段59按芝麻树官方前端补BROWSER实际时长、链接及类型合同，完整等待/重查后才send，明确签到不套浏览等待，其他明确类型不自动报名/发送。

- fix（未提交）：阶段58修复百次卡选定任务因预算中断后无法自行领奖；下一轮重查同任务待领奖状态及合同，只领取不重新报名、等待或发送，领取终态也校验上下文。
- feat（未提交）：阶段56～57补百次卡明确选定任务报名/状态回查/同日恢复，默认关闭；余额宝普通资产旧激活回执仅通过两次A查询恢复确认，兑换券未知保护保留。
- feat（未提交）：阶段55参考AG补炼金P2E当前页面会话、组件初始化及完整等待后的分片上报；每片重查合同、单次写调用、同记录进度确认，未知跨日冻结。
- feat（未提交）：阶段53～54参考AG补炼金明确游戏时长及普通频道浮球；完整等待、任务/游戏/时长重查、单次写RPC与同记录进度确认，沿用原关闭开关/预算，P2E仍独立待接。
- feat（未提交）：阶段52参考SJ补百次卡明确选定任务目录/完整等待/发送及同任务领奖回查，默认关闭/空目标/预算0只查询；每写预留原预算，未知跨日冻结、元数据漂移拒绝执行。
- feat（未提交）：阶段51参考AG/SJ区分equityNo/voucher与trialId激活，补本轮兑换券直接激活及按预算激活全部已有资产；单次写RPC，券只记受理并保留未知回执，修正先前过度要求ID映射的判断。
- feat（未提交）：参考AG/Sen new补芝麻租赁免费浏览/树委托、四款已映射游戏和无spaceCode免费广告，同任务进度回查/未知跨日冻结；SDK同步Token局部化及公共调用方取消传播，来源记录阶段50。
- feat（未提交）：参考Xu/SJ与3小时官方公开前端补动态CSR登录、今日捐步与进度回查，默认关闭/每日一次，凭据仅内存；运动公共step/run取消向上传播，来源记录阶段49。
- feat（未提交）：阶段45～48补余额宝券转换/指定兑换/已有资产激活、种花指定助力/外部池及可选上传、旧卡活动、农场剩余五类满仓工具；来源表记录实际接入范围及仍缺的终态协议。
- feat（未提交）：阶段36b、39b、41～44补森林/庄园/鱼塘手动子动作、持续追榜/动态美食补蛋、芝麻树与炼金奖励、好家缴费金、六类临期用卡；精确来源和未实现合同缺口写入来源表。
- feat（未提交）：阶段30～40接种花奖励/浇水与任务、账单积木、余额宝目录/别名、WebDAV、能量雨收尾、模块立即执行、旧缓存维护、排位预算与森林指定补兑，默认关闭/预算保护，范围及来源逐项记录。
- feat（未提交）：来源阶段27～29补AG找能量预曝光、Sen两套独立森林周期和AG/Sen加速器定点/禁止窗口，GMT+8排期共用主执行槽；默认保持旧行为，来源/差异文档同步。
- docs（未提交）：核对真实Sen new与M的无纸阅读和农场游戏时长，记录相同协议、入口与停止保护差异；Sen默认极速上报，M按任务时长等待，不改已有策略。

## 2026-10-06

- feat（未提交）：参考Xu补独立默认关闭的种花昨日施肥奖励，复用动态授权/签名，数量/资格重查与领取状态回查、未知日保护；不自动浇花/施肥，来源表记阶段26。
- feat（未提交）：参考Xu新增默认关闭的种花签到，动态授权/同账号登录、客户端签名/GMT+8日期、有界HTTPS、前后签到回查及日保护；其他种花/口令助力未接入，来源表记阶段25。
- feat（未提交）：参考AG补满仓加饭卡腾位，原开关/在家进食/未生效才用，共用普通用卡日两次尝试及库存/生效回查，未知当天停止；关闭新策略保留旧行为，来源表记阶段24。
- feat（未提交）：按Sen new有效调用移植未来蹲点数量触发双击卡，默认关闭/10球/日6次，只用限时库存，资格/库存/生效回查和合并子任务；修正克数阈值仅声明的误判，来源同步记录。
- docs（未提交）：补齐阶段1～22功能移植来源表，区分AG/Xu源码与准确APK文件名，记录代码位置、已移植范围和未完成分支，避免把相同功能误记成来源。
- feat（未提交）：接入合种队长召唤，独立默认关闭/勾选合种、东八区18点后，同队友每天一次/总20次；发送前后资格回查，修正来源链接及JSON转义。
- feat（未提交）：加入安心豆独立权益目录/勾选/日预算，默认关闭/预算0；纯豆黄金票详情、兑换计数/历史及余额扣减回查，实付/券/下单类仅查看。
- feat（未提交）：接入默认关闭的安心豆保险知识闯关，完整题目/明确答案/同题身份及下一题检查，逐题和最终状态回查、当日未知保护，不把提交ACK当完成。
- feat（未提交）：新增默认关闭的安心豆每日一次抽奖，资格/活动编号及余额重查，扣减1豆回查、未知当天不重试；奖励金额仅记录接口返回。
- feat（未提交）：接入独立默认关闭的安心豆浏览任务，报名订单原子保存后发奖，同订单成功记录确认后清理；跨重启/取消保留、黑名单及日保护，不混入答题/投保。
- feat（未提交）：接入默认关闭的安心豆签到/守护者等级奖励，签到回查、奖励资格及余额增量双确认，同项当日保护；浏览任务/兑换/抽奖独立后续。
- feat（未提交）：接入默认关闭的蚂蚁保保障金预热、签到/活动气泡及五类任务中心，已知浏览报名/发奖/回查，黑名单及日保护；不做投保/转账/手动业务。
- feat（未提交）：加入默认关闭的小号复活模式及小球例外，普通大球不收/不蹲点，延迟请求重查，GMT+8周一07～08点暂停；保留复活金球、不用时光加速器。
- feat（未提交）：补齐找能量结束上报和提示后的已完成奖励领取，沿用森林任务开关，分组/子任务校验、回查及当日保护；取消不发结束请求，预曝光仍后续。
- fix（未提交）：商家MORE积分任务续查透传服务端orderTaskCode，每轮独立重置，SERVICE保持独立；异常编号停止操作，转义/多轮回查回归通过。
- feat（未提交）：庄园加入默认关闭的满仓先用一张再领奖，支持原策略已开启的新蛋/加速/篱笆卡，库存及任务回查、未知日保护；奖励数量先校验，请求改JSON转义。
- feat（未提交）：加入芝麻粒独立目录/勾选兑换及预算，时光加速器未成熟能量用卡/库存效果回查/蹲点修正；默认关闭，不接实付下单或自动补兑。
- feat（未提交）：移植动物竞猜动态授权签到/查询、芝麻成就馆领取、体验金签到/已完成奖励及森林白名单；均默认关闭，回查确认，补签增加午夜边界保护。
- feat（未提交）：接入海洋推荐航行/赠送碎片、会员GMT+8定时抢兑和关键词目录补充；默认关闭/空，限定好友名单、勾选商品及预算，取消不重排。
- feat（未提交）：移植 AG 账单贴纸领取/升级/奖励及商家资格检查、签到、开门打卡、积分任务；默认关闭，状态回查与按项当日重复保护，版本仍为1.2.10。
- feat（未提交）：逐项补齐大表鸽炼金/持久领奖闭环、加速卡可配置日上限、森林最近30天漏签补签；特殊食品库存排序确认原本已有，版本保持1.2.10。
- feat（未提交）：逐步移植第2阶段黄金鸡/农场小鸡雇佣、满产领取重雇，默认关闭并回查到场/离场，普通赶鸡保留NPC；大表鸽依赖另列后续，版本保持1.2.10。
- feat（未提交）：逐步移植第1项 AG N倍卡高倍率/临期替换，默认关闭，二次确认前回查、使用后核验，同一道具编号每日限一次；版本保持 1.2.10。
- chore `a1cb12b8`：按用户要求将运动「健康岛｜开启」默认改为 true，保留已保存配置及各子功能开关；版本仍为 1.2.10。
- fix `a1cb12b8`：对照 AG 的删除及默认关闭记录，补新村外跳与三类游戏任务默认跳过；鱼塘保留且钓鱼/任务默认关闭，版本仍为 1.2.10。
- feat `0a8b3a6f`：参考 AG 逐项补动物到期替换、好友独立克数、努力流星与浏览任务，以及篱笆/公仔/日记/证书/绿植可选功能；版本保持 1.2.10。
- release（功能提交 `15f81147`）：汇总 1.2.8～1.2.10 的累计功能与升级说明，保留多账号功能介绍，去掉具体账号标识；同版本重打 1.2.10 正式包。
- fix `15f81147`：参考 XU 补庄园自动喂鸡限次重查，校验本次状态和进食时间，保留毫秒精度并补运行日志；版本保持 1.2.10。
- feat `15f81147`：参考 XU 增强现有农场抽抽乐，兼容新版活动/任务、已登记游戏上报回查与批量抽奖，补运行日志及离线回归。
- docs `b6d8dfd0`：主要参考 AG 与 `E:\Work\Xu`，GR2026 改为旧版历史对照；同步来源优先级和 XU 本地路径。
- release `v1.2.10`：恢复无排查后缀的正常版本，归档两项功能实机反馈和阅读持续获能优化；20 项回归、Debug 编译、Release/R8 与原签名校验通过。
- fix `517bcde2`：无纸阅读取消单轮 30 章限制，持续获能就读至服务端满额；保留无增长/异常停止，改为单调时钟约 15 分钟运行时限并记录未满额进度。
- fix `517bcde2`：无纸阅读兼容进度的合法整数字符串，畸形/溢出/零上限仍停止；查询失败补字段类型诊断，C158 实机已反馈能够阅读获能。
- release `v1.2.9`：版本 1.2.8 → 1.2.9，汇总农场游戏时长、无纸阅读、配置搜索状态与精确异常退避修复；20 项回归、Debug 编译和 Release/R8 构建通过，沿用原签名。
- fix `f62e26c2`：按 C158 日报跳过 6 条精确场景/任务的失效主完成接口，保留备用完成与领奖；对应 doFarmTask 的 102 繁忙按任务短退避，芝麻信用与零星状态失败不扩大封禁。
- fix `95e3c17f`：配置搜索列表按模型/字段身份绑定 Compose 状态，修复搜索与原位置开关显示不一致；补真实 Compose 筛选/排序/清空与写入回归。
- feat `66aee698`：农场游戏时长与森林无纸阅读默认开启；复用宿主授权、按账号记账，补运行状态/失败原因和 Token 获取指引；19 项回归及 Debug 编译通过，实机待验证。

## 2026-10-05

- docs：发布说明改名为 `docs/版本更新说明.md`，按版本倒序统一保存，保留 v1.2.8 内容；同步索引、README 与后续发布维护规则。
- release `v1.2.8`：版本 1.2.1 → 1.2.8，整理累计更新与升级说明；17 项回归、Debug 编译及 Release/R8 构建通过，APK 签名与旧版一致。
- merge `5b26bca5`：合并主线新增 7 个提交，更新芝麻粒任务闭环、权益分类分页、合种记账、施肥限额和捐蛋上限；补异常响应保护，17 项回归与 Debug 编译通过。
- merge `54fdca8a`：合并主线 25 个提交，保留 GeminiAI、账号隔离与按场景施肥；修正时区、JSON 安全解析及缺字段误判完成，17 项回归与 Debug 编译通过。

## 2026-09-30

- fix：拼图截图改存 `.nomedia/puzzle/<账号名>/`；首次访问数据目录时剪切旧 `puzzle`，目标已有内容则逐项移动、重名改名，不留旧目录副本；GMT+8、JSON 创建与读取无改动。
- fix：庄园 `CUISINE` 领奖成功日志改显示“爱心美食×N个”，数量取任务奖励字段，缺失时不报 0；回归覆盖 1–3 个。GMT+8、JSON 创建和安全读取无新增变动；具体菜品名待服务端字段确认。

## 2026-09-29

- fix：对照 AG，庄园领奖 RPC 补传 awardType（美食 CUISINE）并安全转义 taskId，取消当日“全部完成”跳过并在本轮末尾补领；15 项回归和 Debug 编译通过；GMT+8 无改动、JSON 创建沿用请求拼装、读取无新增裸 get（既有裸 get 仅在注释中）；待真机验证。

## 2026-09-28

- test：提交nodrag_puzzle完整样本及命名对照，原夹具迁入其puzzle-slider子目录并同步回归路径；无生产代码、时间或JSON改动。
- fix：拼图纹理超时不再降级误拖（盾牌653→623），窗口仍在改记结果未确认；40张PNG与9个RGBA样本离线核对通过（含1个错帧拒绝）；15项回归及Debug编译通过，三项规则无新增违规，未真机验证。
- fix aeac7e95：异常统计补录code/desc/errorCode，避免错误原因丢失；回归覆盖字段及账号隔离；GMT+8保持、无新增JSON创建、opt读取，15项回归及Debug编译通过。
- fix：小额直领补库存记账：1–3个按件计不再调add2FoodStock、成功记为个不记g；回归补970g场景；15项回归与实抽方法javac编译通过，未真机验证。
- fix：庄园饲料任务1–3小额奖励跳过槽容量直接领（饲料皆30倍数），30/60等保持原逻辑；回归加满仓直领/60g暂缓用例；15项回归与Debug编译通过；三项复核无时间/创建改动、opt读取，未真机验证。

## 2026-09-26

- chore：移除误跟踪的 Kotlin 会话缓存，清理项目内误生成的 Windows 缓存目录并补忽略规则；无业务代码、时间或 JSON 改动。
- fix：run() 结尾加“道具奖励补领”，任务结束后重查并尝试补领道具奖励；真实美食领取待设备验证；回归扩至真实饲料方法分支、重查链路与顺序断言；三项复核无时间改动、MyUtils 创建、opt 读取判空（桩直构 JSON 属隔离测试）；15 项回归与 Debug 编译通过，未真机验证。
- fix：补P2：庄园道具领取缺 awardType/数量/taskType 直接跳过不发无效请求，仅非空未知类型直接领；新增 check_antfarm_tool_reward 用真实方法隔离验证三种场景并注册进回归清单；15 项回归与 Debug 编译通过，未真机验证。
- fix：庄园道具领取未知奖励类型不再中断整轮（跳过满额判断直接领，farmTools 为空同理），饲料/道具领取失败补标题+类型+数量+memo 留痕以定位美食奖励；14 项回归与 Debug 编译通过，未真机验证。

## 2026-09-24

- fix：首页统计、好友统计和自动拉黑记录按 UID 存储并在切号后重载；旧混合文件保留不自动归属，补双账号回归；GMT+8、JSON 创建及读取复核无新增违规。
- fix：合并复查修正 RPC 切号后等待/重试仍可能发请求、S2 每周已捐标记跨天丢失；三项硬性规则复核无新增例外，13 项回归与 Debug 编译通过，未真机验证。
- merge：更新本地 `MIUIX-api102` 至 `c7d96293` 并并入 `my_dev`；保留账号切换准入、按场景施肥、GeminiAI，接入上游任务代际/单链排期/S2 活动；GMT+8、JSON 创建与读取已逐文件复查并修正，淘汰的自动睡觉代码未移入。
- chore：版本号 1.2.0 → 1.2.1，正式版包含拼图单帧识别、全局开关、失败复位及旧版识别兜底。
- fix：新版拼图最终识别失败时额外重试原有 M 版识别，旧版成功才拖动，并记录兜底日志；两版均失败仍不拖动。
- fix：新版拼图改为单帧纹理/边缘/稀疏轮廓联合识别并一次连续拖动；修正 5 组背景误匹配，28 个标注场景、13 项回归和 Debug 编译通过，未真机验证。
- fix：拼图验证码识别、拖动、结果、重试等日志统一增加 `[新版]`/`[旧版]` 标识，并补回归约束。
- feat：拼图自动处理/新版识别开关迁至全局（新版默认开）；新版只取首张截图识别，不再按住探测或二次截图，失败重试前点击滑槽中部复位；未真机验证。
- fix：简单滑动验证码失败后先点击滑槽中部复位再自动重试，补回归；13 项检查与 Debug 编译通过，未真机验证。

## 2026-09-22

- chore：版本号 1.1.9 → 1.2.0（tag `v1.2.0`），含 v1.1.9 之后 24 个提交（1 次上游合并：备用完成接口/通知重构/饲料分派等）+ 无损回写判定与 agy 审查 7 项修复。
- fix 2117cf04：修上游无损回写判定bug（对象丢key从忽略改为拦截，数组分支本就正确），删海洋/新村两处合并遗留冗余import；隔离探针8断言新码全过、旧码2挂；回归与编译通过。
- fix c185d797：修agy审查7项：会员核对失败返回null防误报完成、海洋补查从第20人起、调度即刷通知下次执行、计数改AtomicInteger、果园核对Map去static、空响应抛JSONException、删运动冗余import；回归与编译通过。
- test 5fae0c33：检查桩跟进上游 API（NotificationUtil/verifyPendingTasks/setRunning/Log）；三项必查无新增裸 get、无时区改动，TaskAlternative.doFarmTask 保留直接构造（trigger 靠异常判未触发）；13 项回归+Debug 编译通过。
- merge cacacbe5：合并 origin/MIUIX-api102（16 个上游提交：TaskAlternative 备用接口、通知重构、饲料 taskId 分派、施肥核算等），10 文件 31 处冲突；果园保按场景次数+批量核算改传参，其余取上游并转 opt/MyUtils。
- docs：AGENTS.md 补全回归清单（13 项检查+对照说明），新增代码与数据地图、合并速览两节；INDEX 同步。
- docs：使用说明补日志按账号分目录/AI 类型与 Gemini 令牌，删§10基础手抄表改指自动生成文档；README 文档表补全；GR-Sync 记 12 福利任务已移植。
- docs：CHANGELOG 上半部分 09-19/09-21/09-22 共 63 条压缩为一行一条，头部新增写作约定（单条约一行、只写改了什么加关键取舍），文末详细记录区冻结只读；09-17 及以前逐字未动。
- docs：`doc/` 并入 `docs/`（`MyFix.md`、`GR-Sync.md`、每日异常反馈.txt、`INDEX.md` 共 4 个文件），`AGENTS.md`/`CHANGELOG.md` 内链与约 50 处代码注释引用同步更新；纯文档改动，未跑回归与编译。
- chore：版本号 1.1.8 → 1.1.9（tag `v1.1.9`），含 v1.1.8 之后的 13 个提交（3 次上游合并）：昵称 null、主线程 NPE、配置迁移丢设置、合种浇水次数编辑等修复。
- merge `5bf5d90b`→`b76684c8`→`13ec4981`：三次合并 `origin/MIUIX-api102` + 自查：昵称 null 改 `mergeSelfEntity`、`initHandler` 拆箱 NPE、`closeCaptchaDialog` 改名加 `@JsonAlias`、`accountDisplayName` 判空、`withCount`/`defaultCount` 只限合种浇水字段；回归全过，Debug 编译通过，未真机验证。
- fix：`AntOrchard` 静态黑名单接入 `handleTaskList`（解决 `taskType=70000` 反复报错）；拼图截图失败补 `Log.captcha`；其余单次报错证据不足未处理。

## 2026-09-21

- chore `65c50523`：版本号 1.1.7 → 1.1.8（tag `v1.1.8`），8 个提交：拼图识别/截图/轨迹修复 + 上游界面修复。
- merge `f133ba53`：合并 `origin/MIUIX-api102`（3 个上游提交：界面内边距/输入框对齐/副标题 + `UserIdMap` 取自身信息），2 处冲突保留本分支；三项检查无新增，回归与编译通过。
- fix `5af6d005`：拼图拖动轨迹补 GR 随机效果（按下停 30~80ms、MOVE 抖 ±3/±2px，落点精确）；未真机验证。
- fix `5af6d005`：拼图模板框内缩 8px，真机火焰图错配 751→672（芽形 610）；手指提前提交问题未定位，需 `matched_submit` 截图；未真机验证。
- feat `5af6d005`：松手前加截 `matched_submit`（等 150ms，800ms 兜底），`matched`/`matched_submit` 各留 10 张；服务端判错原因未定；未真机验证。
- chore `3e920ab6`：版本号 1.1.6 → 1.1.7（tag `v1.1.7`），51 个提交：拼图增强、账号名目录与导出、上游配置保存与界面。
- ui `c1806e72`：主页 Tab 去掉“当前账号：”前缀；首页标题 `Sesame-M` 改为芝麻粒M（与 `AGENTS.md` 品牌名写法待统一）。
- merge `b9b10dde`：合并 `origin/MIUIX-api102`（17 个上游提交），6 处冲突取舍见文末详细记录；Debug 与 Release 构建成功，13 项回归通过。
- fix `e0fec4b2`：日志页“分享”改走 `copyForShare` 副本带账号名；导出/分享/统计各出口已核对均带账号名；未真机验证。
- fix `872cd694`：导出文件名 uid 目录补账号名、拼图扫描循环加令牌防双跑、被动扫描要求按钮+轨道同时识别；655 位移未通过原因未定；未真机验证。
- fix `45f82c05`：账号名读取顺序定为括号前名字→括号内账号→uid；补回归用例；未真机验证。
- feat `ffcffb21`：新增 `AccountFolderName`，日志/截图目录与导出文件名改用账号名（含安全化、同名加 uid 后 4 位、旧 uid 目录改名迁移）；未真机验证。
- fix `0ebf9d0e`：`puzzle/` 只保留拖动过的 `matched`（每号 10 张），其余进 `tmp/` 验证结束即删；`match-failed` 不再保留；未真机验证。
- fix `10875385`：`no-track` 改判无验证码随结束清理；H5 关键词收窄到 GR 指纹；未真机验证。
- fix `10875385`：拼图尝试计数改按 WebView，验证成功/重弹/换号重置；未真机验证。
- feat `5489f7a9`：拼图拖错允许重试，新增 `puzzleMaxAttempts`（默认 4，范围 1-5）；未真机验证。
- fix `188b96eb`：拼图截图每号保留 10 张（含验证码的，原为 6 张）。
- fix `76c6edbd` `61d52f05`：验证结束删无验证码截图（含码的留 10 张，无码的另设 16 张兜底）；“每窗口只存 1 张”曾被否。
- 真机验证：`2c005d55` 包手动触发拼图自动验证成功一次（整链路通，成功率不保证）。
- chore `5ed2af90`：版本号 1.1.5 → 1.1.6（tag `v1.1.6`）；之后提交不重打 tag。
- fix `2c005d55`：H5 触发补漏（intent 拼 extras 文本、补 GR 风控指纹、被动扫描类名改关键字包含）；未真机验证。
- fix `5be07610`：`CaptchaDialog` 钩子先记录后取细节 + 新增页面恢复被动扫描 `armPassive`；未真机验证。
- feat `b3026e22`：新增 `H5RiskTrigger`（`WebView.loadUrl` + `startActivity` 挂钩，命中指纹记验证记录并 arm）；关键词宽只记不拖；未真机验证。
- fix `e0a85a88`：拼图截图目录改到 `sesame-M/puzzle/<账号名>/`，不再放 log 下。
- fix `ee878329`：拼图拖动后窗口关闭即 `clearVerifyPause`，不再多等 5 分钟。
- feat `fcee963f`：新增拼图滑块自动处理（移植 GR 图像匹配 + 自研 solver/swipe/触发/截图/配置 `autoPuzzleSlider` 默认开）；9 样本离线回放通过；未真机验证。
- refactor `602eca3d`：删除版本伪装（真机验证伪装改不了服务端下发类型）；`docs/GR-Sync.md` 标注已删；回归与编译通过。
- merge `5e34fbb4`：合并 `origin/MIUIX-api102`（5 个上游提交），`SimplePageManager`/`BaseModel`/`AntFarm` 三处冲突取舍见文末详细记录；回归与编译通过。
- fix `0530538c`：`RpcRequestGuard` 遇需验证失败直接记验证记录（不依赖界面 Hook）；高版本弹窗形态仍拿不到；未真机验证。
- fix `0530538c`：退避修正——“人气大爆发”等按临时繁忙短退避；验证暂停只内存 5 分钟不落盘（v1→v2 旧键作废）；1009 繁忙不再当风控；未真机验证。
- docs `0530538c`：复核 09-21 账号日报（53 次），结论无需新增跳过规则（断网/102 退避中/已拉黑/良性结果/单次证据不足）。

## 2026-09-19

- merge `2544c90a`：合并 `origin/MIUIX-api102`（2 个上游提交），3 文件冲突；三项必查无问题，十项回归通过。
- feat `1533a213`：新增「验证记录」独立日志（`Log.captcha` + CAPTCHA tag + 开关），记类型/来源/运行中模块/最近 5 个 RPC；只观测不干预；未真机验证。
- fix `7f17e603`：版本伪装默认改回关闭（真机证明伪装改不了验证码类型，有风险无收益），1.1.5 默认值迁回关闭；“实际版本显示伪装值”未修。
- feat `d1d87b26`：庄园多阶段饲料任务每轮打印进度日志（仅日志）。
- fix `0c409ef7`：庄园多阶段任务先做完全部阶段再领奖，待领额按累计减已领算（对照 AG）；服务端是否允许待定；未真机验证。
- fix `03b52936`：定向提前伪装调用方识别改全栈扫描（LSPatch 多层嵌套下 24 帧窗口不够）；能否改变验证码类型仍是假设；未真机验证。
- fix `dddb5807`：日志查看器切换筛选/搜索后回到列表顶部；无回归覆盖。
- fix `53744e13`：版本伪装对日志模块早读定向提前伪装（`sEarlyFake` 缺省开，其余早读不动）；是否命中决定类型仍待验证；未真机验证。
- feat `2bedc9d7`：庄园饲料任务按轮执行（`runFarmTaskRounds`，最多 10 轮），做完当天不再查询；新任务次日才查；未真机验证。
- feat `99f33eb7`：导出的日志文件名带 uid（防多账号覆盖）；内容仍不写 uid；未真机验证。
- fix `2621d373`：版本伪装诊断来源改全栈找 hook 帧后 4 帧；仍需弹验证码那轮日志对照；未真机验证。
- fix `307c8080`：版本伪装补 Flags 重载 hook + `earlyEnable` 可选提前伪装；另修线程标志残留与保存丢开关；能否生效未验证。
- feat `307c8080`：版本伪装诊断日志（每轮打印开关/注册/读取次数与来源）；未真机验证。
- fix `307c8080`：版本伪装旧默认配置迁移为开启（用户改过的不动）；能否下发简单滑块未验证。
- fix `96e664f5`：`RpcRequestGuard` 加统一规则——未开通/未认证提示按账号暂停 24 小时；遗留：按 24h 非自然日；未真机验证。
- merge `e73337e7`：合并 `origin/MIUIX-api102`（1 个上游提交），2 文件冲突；三项必查无问题，十项回归通过。
- merge `458b043a`：合并 `origin/MIUIX-api102`（7 个上游提交，上游删 `GeminiAI`/`TongyiAI` 加 `CustomAI`），9 文件冲突；三项必查、九项回归 + 独立进程检查通过。
- fix `a2e8b421`：恢复被合并误删的 `GeminiAI`（海外用户在用），`AnswerAI` 同时提供 GEMINI=1 与 CUSTOM=0；规则写入 MyFix 与 AGENTS。
- fix `a352b1d1`：补看第三个账号日报——好友浇水能量不足即结束本轮；1009 系统繁忙不再拉起支付宝。
- feat `a352b1d1`：森林新增「找能量」`findEnergyCollect`（默认关）；发财树红包未移植。
- fix `ac8624fa`：复核两账号日报——农场 102 第 5 次起退避 6 小时；快递失败当天不再重领；其余已有退避/良性/证据不足。
- feat `ac8624fa`：版本伪装默认开启（仅新建配置；老用户由 `307c8080` 迁移）。
- feat `ac8624fa`：风控暂停时 `showVerification` 拉起支付宝（10 分钟一次；后收窄为仅含验证文案才拉）。
- fix `2b11076a`：庄园/运动各子任务 `step()` 隔离，单个异常只跳过自己。
- feat `2b11076a`：运动步数超 18000 不再同步。
- fix `2b11076a`：运动同步 `steps.query` 失败不再提前 return，临时异常下轮重试。
- fix `2b11076a`：`enterFarm` 请求键补 `userId`/`farmId`，好友失败不再暂停自家庄园；补回归。
- merge `6b8c1236`：合并 `origin/MIUIX-api102`，20 文件冲突；修 `AntFarm.competition()` 缺返回值编译错误；编译通过。

## 2026-09-17

- fix `302b1ad4`：复核每日异常 123 次，修复绿色经营签到 `sceneId` 缺失导致的跨场景退避串扰及日报合并；补充场景隔离和两个庄园繁忙任务回归，保留既有退避，无新增永久黑名单；九项回归与 Java/Kotlin 编译通过。
- fix `cb636a62`：修复合并后普通计数字段保存重置为 1、单选数量无法编辑，以及三级/四级配置页恢复后未初始化；保留同账号未落盘修改，按 MyFix 三项规则复查，九项回归与 Java/Kotlin 编译通过。
- fix `42b7a1fa`（最终确认）：切回 A 确认成功即开始切号冷却，任务立即恢复且独立运行；到期仍等任务结束、首页空闲15秒才切号，移除等待首轮完成的额外状态，九项回归与编译通过。
- fix `42b7a1fa`：冷却仅限制自动切号；返回 A 立即恢复任务，等 A 本轮完成后开始默认2小时/自定义冷却，期间周期任务正常运行且不重置冷却，到期仍检查首页和空闲15秒；九项回归与编译通过。
- fix `42b7a1fa`：自动切号仅允许支付宝首页处于前台且有焦点时发起；二级页面/未知页面/后台暂停并重置15秒计时，补充发起前复核、等待首页状态及回归，九项检查与编译通过。
- fix `42b7a1fa`：自动切号改为 A→B→C→A 后冷却，到期从 A 开始下一轮；游戏上报/打地鼠在异步提交前计入生命周期，修复提前结束提示和切号竞态，补充控制器与异步入口回归。九项检查及 Java/Kotlin 编译通过，ANR 待真机复测。

## 2026-09-16

- fix `7a1b0865`：根据每日异常报告，为庄园领奖 `102`“服务器正在开小差”补齐按账号/场景/任务隔离的 5/5/30 分钟退避；修正 error=0 遮蔽 resultCode，不新增永久黑名单。
- `3601388e` fix：修复电量权限申请的无效回退与无提示；不区分品牌，启动失败统一进入支付宝应用设置并提示，增加手动入口并补充回归，待实机验证。
- `02381191` feat: 新增按账号/GMT+8 日期聚合的 RPC 异常统计 JSON，异常日志页支持“导出统计”；记录任务 ID、错误及次数供人工完善黑名单，不改变现有请求策略。全部日志分类的单条卡片支持长按自由选择复制。
- `33ae1a56` docs：AI 必读规则新增每次合并必查 GMT+8、MyUtils JSON 创建及 `.opt*()` 安全读取，要求覆盖自动合并文件并记录处理结果与例外。
- `2deb0bf3` Merge `origin/MIUIX-api102` 至 `ea6dd5e8`（四个提交）：合入弹窗搜索、配置/统计加载优化及模块仓库发布工作流；保留本地账号标题与整数单位修复，修正上游单选追加旧选择的问题，九项回归及编译通过。
- `408fdd33` fix: 删除信用2101及视频红包（保留好家无忧卡）；黄金票仅保留每周福利并归入会员分组，青春特权仅保留森林道具入口，清理重复/失效实现及专用 Hook；顺带修复本轮结束日志被未来定时任务阻挡（保留已到期/运行中任务等待和切号隔离，自动切号判断不变）。
- `47370579` fix: 修复会员业务拒绝/请求冷却被误判为全局掉线导致未认证账号反复提示“检查超时”并拉起登录页；检查失败/超时/异常统一改为按执行间隔重试，不再强制拉起登录页，区分超时与中断并取消检查线程；顺带修复 RPC guard 在 `error` 为空串时未回退 `resultCode` 的 bug。

## 2026-09-15

- `55a6a696` fix: 日志解析兼容无 `TAG:` 的分类记录，修复森林/庄园/金豆/其他日志被合并为同一卡片而仍显示正序；补充回归检查。
- `dc009afe` fix: 深色模式/跟随系统开关最终效果没变时不再 `recreate()`，不闪页面（切换前后
  各算一次 `effectiveDark`，一样就跳过重建）。
- `4eed8650` fix: 深色模式和跟随系统设置改成双向互斥（开一个自动关另一个）。
- `c7aa63f3` fix: 深色模式开关本身不生效——`followSystem` 判断优先级比 `darkMode` 高，默认
  跟随系统开着时单独点深色模式没有效果，只会白白 `recreate()` 一次；开深色模式时顺手关闭
  跟随系统。
- `1f6ec478` fix: 电量权限按钮真正的闪退原因——`PermissionUtil.checkBatteryPermissions()`
  在独立 App 进程里引用了只有真被 LSPosed 注入进支付宝进程才存在的 `ApplicationHook`
  （其父类 `XposedModule` 是 compileOnly 依赖），触发 `NoClassDefFoundError`（`Error` 不是
  `Exception`，两层 `catch(Exception)` 都包不住）；加个接收 `Context` 的重载绕开。
- `56cc9a38` fix（诊断方向错误，已被 `1f6ec478` 取代真正修复，规则本身不算错保留未撤）：
  怀疑是 R8 混淆导致的崩溃，把 hook 包从只 keep `ApplicationHook` 一个类改成整包 `-keep`；
  重装后同样的崩溃复现，说明根因不在这里，见上一条真正的修复。
- `330c746d` docs: 补全 2026-09-15 三轮合并/修复记录到 `docs/MyFix.md`/`CHANGELOG.md`；新增
  `AGENTS.md`（Claude Code 和 Codex 都会读的项目须知）；顶栏/配置列表账号显示格式调整
  （`C176: 账号` 改 `C176(账号)`；配置列表 UID 一行改用 `ArrowPreference` 的 `summary`
  小字副标题，不再跟标题同号大小挤在一起）。
- `c12a7be2` fix: 电量权限申请崩溃（缺 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest 声明）、
  切深色模式跳回首页（`selectedTab` 未跨 `recreate()` 存活）、切 tab 顶部账号名闪烁（轮询状态
  提到 `MainScreen` 一级共享）。
- `3acad3db` Merge MIUIX-api102：合入上游 `0651e79a`（4 处 `IntegerModelField` 范围收紧/放宽）。
- `92ec28e6` chore: 本地默认版本号改为 1.0.8。
- `ae2b620c` fix: 运行日志"开始执行"等位置一直显示裸账号 UID、从未显示过昵称（`recordUserName`
  内存缓存自己写自己读，从没被真正写入过）；小鸡"自动睡觉"功能（对应接口已失效）整段删除，只留
  自动起床；访问已取关好友的庄园不再每天稳定刷一条"非好友"错误日志噪音。
- `bf3d67cb` feat/fix: 日志页最新条目改到列表顶部显示；电量权限开关迁移到 `AppConfig` 后未设置
  用户会拆箱 NPE 崩溃（新增 `shouldRequestBatteryPermission()` 兜底读旧账号历史值）；配置保存时
  `BaseModel` 精简掉的字段被静默丢弃、农场施肥场景次数迁移；整数配置编辑框绕过单位换算层导致
  保存值错误；偷榜/霸榜"0分钟"死区改为有意义的边界值；新增 `RpcRequestGuard` 统一 RPC 失败保护
  （按账号隔离退避 + GR 已知异常任务黑名单），顺带修了 `RpcEntity`/两套 `RpcBridge` 的三个既有
  并发 bug（`hasError` 未重置、`wait(30_000)` 虚假唤醒误判超时、线程中断被当 RPC 失败记录）。
- `e1a42342` Merge MIUIX-api102：合入上游 `ad353056`（账号切换延迟 1 秒执行 + 日志查看器优化）、
  `c3bf75da`（R8/proguard 精简、`AntMember` 反射调用改直接调用）。自动合并里发现真回归：延迟
  1 秒的账号切换回调跑在 `TaskLifecycle.Work` 作用域外，完全脱离本次会话加的并发保护，已补同
  代际校验修掉。
- `5268cfcf` Merge MIUIX-api102：合入上游 `bded0848`（删水印原生库/`AntInsurance` 模块/旧 UI
  遗留资源）、`335047d8`（R8 混淆启用、config 保存修复、`AntOrchard.getWua()` 改 public）。
- `81236b9e` feat: 顶栏版本/编译时间下面加当前账号一行，配置列表每个账号条目加 UID；`accountDisplayName()`
  改直接读 `self.json`，不再调用会清空全局共享 `userMap` 的 `UserIdMap.loadSelf()`。
- `2e350ce6` feat: 品牌名"芝麻粒"统一补齐"-M"后缀（4+3 处历史遗漏）；日志页改用
  `reverseLayout` + `canScrollBackward` 判断是否贴底跟随刷新；全部任务执行完成后运行日志打印
  "🏁全部任务已执行完成"（按账号世代跟踪，一轮只打一次）。

## 2026-09-14

- `c30facbc` fix: 修复移植审查确认的 17 项问题（切号任务隔离、金豆额度与领奖、视频冷却与调度、
  日志兼容与账号同步、分页/捐赠边界、VPN Hook 初始化、鱼塘时区、版本默认值、Gemini 答案和运动币气泡）；
  新增本地 JVM 回归检查，详见 `docs/MyFix.md` 对应记录。
- `5759d512` feat: 从新版GR快照移植12个独立小额福利任务（dayDaySave/luckCard/factCheck/
  forestPlantRewards/dailyCash/promoprodRewards/wealthDay/youthPrivilege/weeklyWelfare/
  healthIslandRewards/myBankWelfare/other）+ videoRewards 视频红包真实观看验证
  （含新的 Activity.onResume 观察hook + WebView JS注入探针）
- `322e29ca` feat: 日志详情页支持实时刷新（FileObserver 监听文件写入，边执行边看）
- `137cf239` fix: 修复验证码VPN弹窗拦截开关从未生效的问题（`boot()` 整段被注释掉）
- `6a31c9e1` feat: 新增全局自动切号功能（账号轮询，最小间隔2小时）

## 详细记录（自 docs/MyFix.md 迁移）

### 2026-09-21（再续）：合并 MIUIX-api102 至 3066428a

**上游 3 个提交（9 个文件）**：`2da16e9c` 统一 Miuix 页面水平内边距为 16dp（About/Extensions/FriendStats/GroupFields/LogViewer/Main/SelectionEdit/Settings）；`2b3448ac` STRING/TEXT/LIST 展开输入框宽度与行标题对齐（`MiuixSettingsActivity`，同时删掉了整数输入框下面的 `limitHint` 提示文字，是上游自己的取舍，照收）；`a08b6228` 配置页新用户副标题空白 + `UserIdMap` 新增 `buildSelfFromAccountModel()`（优先用 `ApplicationHook.getUserObject()` 取自身，不依赖好友列表，再用好友列表补全；`self.json` 更容易写出来，本分支的账号名日志目录/导出文件名都靠它，是正向收益）。

**冲突 2 处**
- `MiuixLogViewerActivity.kt` 的 `LazyColumn`：分叉点 `reverseLayout = true`；本分支已改为 `itemsIndexed(filteredEntries.asReversed())` + 手动跟随滚动（`check_log_follow`），不再用 `reverseLayout`。取本分支结构，只吸收 12dp→16dp。
- `MiuixMainActivity.kt` 的 `ConfigTab` 账号列表：本分支改成 `Pair` + `accountDisplayName(userId)`，副标题固定 `UID: <userId>`，从不为空；上游改的是旧 `Triple` 结构里 `summary` 为空时回退 userId。取本分支，无功能损失。

**三项检查（按最终合并结果的 9 个文件逐个核对）**：GMT+8——无日历/日期格式化改动；JSON 创建/读取——无 `org.json` 使用；`readField` 走反射取字段，不是 JSON。`MiuixLogViewerActivity` 里已有的 `System.currentTimeMillis()` 是本分支之前的代码（传给 `RpcFailureJournal.fileFor`，时区在那里处理），本次没动。`GeminiAI` 保留。

**验证**：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；`AGENTS.md` 列的 9 个 Python 回归 + `check_puzzle_matcher` + `check_puzzle_samples` 全部通过。UI 内边距/输入框对齐只做了编译验证，未真机看效果。

### 2026-09-21（续）：合并 MIUIX-api102 至 d7ec910c

上游 17 个提交、32 个文件（+1472/-421）。

**冲突与取舍（6 个文件）**
- `ModelTask`：两边各新增一个方法（my_dev 的 `hasPendingMainTask`/`isAllTaskIdle`，上游的 `startGroupTask`——配置页“执行”按钮通过广播交给注入进程，只跑指定分组），相邻插入产生的假冲突，两个都保留。
- `BaseModel`：上游把 `showToast`/`closeCaptchaDialogVPN`/`toastOffsetY`/`enableOnGoing` 迁到全局 `AppConfig`（模块级、不分账号），跟着删掉；my_dev 的 `autoPuzzleSlider`、`puzzleMaxAttempts` 保留在 `BaseModel`（仍按账号）。`boot()` 保留 my_dev 的 `CaptchaHook.setupHook(classLoader)`（`audit_regressions` 断言它在 `updateHooks` 之前），`updateHooks` 改读 `AppConfig.getCloseCaptchaDialogVPN()`。
- `FileUtil`：两边各新增一个 getter（my_dev 的 `getAntFishpondTaskListMapFile`，上游的 `getMonopolyTaskListMapFile`），都保留。
- `AntFarm`：my_dev 已把这段读取改成 opt*、并对未知 `taskStatus` 只跳过一项；上游在循环开头加了“本轮已遇 102（`farmTaskAwardBusy`）就 `break`，留到下一轮”。保留 my_dev 的健壮读取并加上上游那段。
- `AntForestV2`：① 上游整块删除了“合成动物碎片”（`combineAnimalPiece` 字段与 `queryAnimalAndPiece`/`combineAnimalPiece` 方法），my_dev 侧确认没有其它调用后跟着删；② `queryAnimalPropList` 改为返回 boolean，调用处合并为 `else if (!animalDispatched && !MyUtils.closeVerification())`——保留 my_dev 的“关闭验证相关功能”门控；③ 自动合并进来的 `consumeAnimalProp` 空值保护还是 `return;`，与上游改成 boolean 的签名不符，编译报错，改为 `return false;`。
- `MiuixMainActivity`（3 处）：上游 Tab 签名改为 `HomeTab(activity)` 等且标题换成新样式；my_dev 早已有“当前账号”标题行（`TabTitleRow`）和权限申请。保留 my_dev 的 `currentAccount` 参数、`TabTitleRow` 与权限申请，采用上游 `ConfigTab` 需要 `activity` 的签名（`ConfigTab(activity, currentAccount)`），核对合并后各 Tab 没有重复标题。

**自动合并带来的行为变化（不是冲突，但要知道）**
- `closeCaptchaDialogVPN` 现在是全局 `AppConfig` 字段，**默认开**（原 my_dev 按账号默认关，上一次合并时保留的“默认关”不再适用）。开着时含“VPN”或“代理”字样的验证码弹窗会被自动关闭；`CaptchaHook` 常开的验证监视钩子对这类弹窗同样跳过，不会启动拼图处理。旧的按账号取值未见迁移代码，等于被忽略。
- `showToast`/`toastOffsetY`/`enableOnGoing` 同样迁到全局；`debugMode` 按账号字段被移除，抓包记录开关统一为日志页开关。
- 庄园饲料领奖遇 102 时跳过本轮剩余领奖（上游）与 my_dev 的 `RpcRequestGuard` 对 `receiveFarmTaskAward` 102 的 5 分钟→30 分钟→6 小时退避是两层，互不冲突。
- 动物碎片合成功能被上游删除，配置项「合成动物碎片」不再存在。

**规范复查（GMT+8、JSON 创建、JSON 读取）**——对合并结果里所有新增/修改行逐项扫描，发现并处理：
- JSON 创建：新版保护地（大富翁）代码有 13 处 `new JSONObject(接口返回串)`（`monopolyPatrol`、`monopolyResponse`、`monopolyTaskTitle` 的 `bizInfo`、`monopolyFinishTask` 的 `prodPlayParam`、动物伙伴派遣等），统一改为 `MyUtils.newJSONObject(...)`。逐处核对：所有接口返回都接着 `MessageUtil.checkResultCode`/`checkSuccess`，空对象一律判失败；`bizInfo` 无效则标题回退到 `taskTitle`/任务类型；`prodPlayParam` 无效则时长为 0 被判“时长异常，跳过”；`markMonopolyTaskBlackList` 对空对象不会拉黑。
- JSON 读取：`selected.getString("creatureCode")` 改为 `optString` 并判空（空则记录并跳过派遣）。
- GMT+8：`parseMonopolyDate` 用 `Asia/Shanghai`（等价东八区，但不符合项目“显式 GMT+8”约定），改为 `TimeZone.getTimeZone("GMT+8")`。
- 其余新增文件（`MonopolyTaskListMap`、`FileUtil`、各 UI 文件、其它模块）没有新的裸 `get*()`、Calendar 或未走 `MyUtils` 的 JSON 构造；`GeminiAI` 未动。

**验证**
- Debug（Java + Kotlin）与 `assembleNormalRelease`（R8）构建成功（合并带 miuix 依赖升级，按规则跑了 Release）。
- 13 项回归全部通过；`account_lifecycle` 的 `ModelTask` 桩需要补 `ModelGroup`/`Model.getGroup()`（上游 `startGroupTask` 用到），已补。
- 提示：中途有一次编译其实失败了但被我的输出过滤漏掉（javac 中文报错），后来改成直接看 `BUILD SUCCESSFUL` 才发现；最终结果以 `BUILD SUCCESSFUL` 为准。
- 未真机验证：新版保护地、配置保存、分组页执行等上游功能本次只做了合并与静态检查。

### 2026-09-21：日报复核与退避调整、验证记录、合并 MIUIX-api102（2dda9ba3）、删除版本伪装、拼图滑块自动验证、v1.1.6

**1. 日报复核（账号 2088702045701743，53 次，GMT+8 00:40–09:31）+ 运行日志**
- 网络 48：18 类各 1 次，集中在 01:46–01:49（约 3 分钟断网），守卫已按 1/5 分钟退避，不处理。
- `receiveFarmTaskAward` 102“开小差”：6 个任务 ID 各 2–5 次，已有 5 分钟→30 分钟→6 小时退避，不处理。
- 金豆：`GOLDENBEAN_GAME_ZH0_LYJX_V1`/`ZH0_NCSCC` 已自动拉黑；`GOLDEN_BEAN_TASK_WAKUANG`“任务已完结”、`KUAIDI_VITALITY`“任务实例无效”各 1 次（后者此前每天 11+ 次，已收敛）。
- `walk.go` GO_STEP_NOT_ENOUGH、`loanpromoweb.promo.signin.query`（`sceneId` PLAY102632271，“人气大爆发”）：**原先都被停 24 小时——后者是误判，见第 3 条**。
- `donation` 218“自营项目没指定标的物”1 次，紧接着回退到公益捐蛋成功，属预期；`receiveFamilyAward` V07“权益已领取”、`collectEnergy` `TARGET_USER_PROTECT_BY_ENERGY_SHIELD`（列表后被加罩的竞态）是良性结果，只占日报计数，不处理（次数明显变多再考虑不计入失败统计）。
- `cook` 1009（“为了保障您的操作安全，请进行验证后继续。”，来源 `antfarmzuofanrw`）：当天唯一一次接口层风控验证，原先被停 24 小时。
- 暂不处理/证据不足：`isBusy` 的文案变体（“活动太火爆”“请稍候重试”等，没有日志证据）。

**2. 验证记录为什么为空（三轮，结论以最后一轮为准）**
- 第一轮（`0530538c`）：支付宝 12.12.20.8000、伪装关闭时 `initSimplePageManager` 在版本 > 10.6.58.99999 整体不启用，1533a213 的两个记录点（弹窗钩子、Activity 处理器）都不触发。改为在 `RpcRequestGuard` 遇“需验证”失败时 `CaptchaTriggerStats.recordRisk`，不依赖界面 Hook。
- 第二轮（`b3026e22`、`5be07610`）：用户装 1.1.6 后仍为空。1.1.6 运行日志（11:02–11:14，账号 2088702045701743）显示**没有任何接口返回 1009/“请验证”，也没有风控暂停**，说明这次验证码是宿主自己拉起的页面，不经过 `RpcRequestGuard`。同时发现自己的 `CaptchaDialog.show()` 钩子有静默失败路径：先 `getDialogInstance` 再 `collectDialogInfo`，任一步取不到就直接 `return`，一行日志都不写。改为先记录再取细节，并把弹窗对象直接交给 `PuzzleCaptchaSolver.arm(source, dialog)`；钩子与 `H5RiskTrigger` 都在运行日志写“已挂载/挂载失败”，以后能区分“没挂上”和“没触发”。
- 第三轮（`2c005d55`，对照 GR `H5RiskOpenHook` 与复查意见核实后修）：`Activity.startActivity` 只读 `getDataString()`（H5 容器的 URL 多在 extras 里）→ 改拼 action/data/component/extras；关键词补支付宝风控处置页指纹（模板 ID `180020010001270421`、`x-dispose-trace`、`disposeapplication`、`disposedname`、`disposename`）；页面恢复被动扫描由精确类名改为按关键字包含（xriver/nebula/h5activity/`.alipaylogin`）。**没采纳**：加 `security` 关键词（太宽，GR 也没有）。
- 现在的触发点共 5 类：接口返回“需验证”（`RpcRequestGuard`）、`CaptchaDialog.show()`（常开，含 VPN/代理字样的弹窗跳过）、H5 风险页打开（`H5RiskTrigger`：`WebView.loadUrl`×2、`Activity.startActivity/ForResult`）、XRiver/Nebula/H5Activity/登录页恢复时的被动静默扫描、（低版本）SimplePageManager 处理器。日志只写域名+路径，不写 URL 参数。
- 仍然没有覆盖：`loadUrl` 之外的打开方式（Nebula `H5Utils.startApp/openUrl` 等，GR 有钩）。手动触发的验证原先以为不覆盖，真机实测手动触发也被接住并自动验证成功（见第 8 条）。

**3. RPC 退避调整（`0530538c`，`ee878329`）**
- `errorMessage()` 补读 `resultView`：`signin.query` 的“人气大爆发，请稍后再试”原被当成“响应未提供错误原因”，非核心接口连续 3 次就走 `!core && failures>=3 → 24 小时`。新增 `isBusy`（人气大爆发/系统繁忙/请稍后再试），非核心接口按 5 分钟（前 2 次）/30 分钟退避，核心接口行为不变；1009 但文案为“系统繁忙，请稍后再试”（neverland）同样按临时繁忙处理，不再当风控。
- “请验证后继续”类（文案含“验证”或 cheating traffic）**不再停 24 小时，也不落盘**：只在内存暂停 5 分钟（`VERIFY_PAUSE`，按请求键 + `TaskLifecycle.generation()`），重启支付宝（进程重建）或切换账号（代数变化）即失效，自动滑动成功（`BaseCaptchaHandler`）或拼图窗口关闭后（`PuzzleCaptchaSolver`）调用 `RpcRequestGuard.clearVerifyPause()` 立即解除。用户的要求是“重启 APP 或换号就能重新弹出验证”，持久化状态会把暂停带过重启，所以不能落盘。中间还试过 30 分钟/2 小时/6 小时递增，被用户否掉，最终版是上面这个。
- 非验证类的风控拒绝（“访问被拒绝”、无文案的 1009）仍持久化，30 分钟/2 小时/6 小时递增。`RpcFailurePolicy.RISK_DENIED_MS`（24 小时）常量保留，`IsolatedRewardTask`/`OtherRequestGate` 的奖励请求层仍用它，未改。
- 请求键前缀 `RpcRequestGuard.v1.` → `v2.`：旧版本写入的所有暂停（含 05:49 那条 `cook` 24 小时、`signin.query` 误判）整体作废，其余合法暂停会在下一次失败时重新学到（各多请求一次）。
- `RpcRequestGuard`→`CaptchaTriggerStats`/`PuzzleCaptchaSolver` 的调用让 `check_rpc_guard.py` 需要桩类：已补桩并新增断言（验证暂停不落盘、5 分钟到期、代数变化解除、`clearVerifyPause`、1009 繁忙短退避、`resultView` 读取、人气大爆发连续 4 次不停一天、需验证会 arm 拼图处理）。

**4. 合并 origin/MIUIX-api102（b1293b40 → 2dda9ba3，`5e34fbb4`）**
- 5 个上游提交：小鸡睡觉/起床按空间类型取值判断（`70e364f6`）、使用说明与配置项说明文档与 README（`00e6589f`、`1e03f12c`）、VPN 弹窗屏蔽功能（`bb3226da`）、翻倍卡额外能量收取简化（`a376e531`）。
- 冲突 3 处：① `SimplePageManager`：上游删了 `CaptchaDialog.show()` 钩子（VPN 弹窗改由 `CaptchaHook` 统一挂钩），取上游。② `BaseModel`：my_dev 早在 `137cf239` 修过同一个 boot() 被注释的问题，上游又新增一份同 key 的 `closeCaptchaDialogVPN`（标签“屏蔽VPN/代理弹窗”，默认开），两份并存无法编译，删上游那份，保留 my_dev 的字段（默认关）与 boot 里 `setupHook` + `updateHooks`；`ApplicationHook` 里上游多加的 `CaptchaHook.setupHook(classLoader)` 保留（重复调用只重复打印日志，我后来给 `hookCaptchaDialogArm`/`H5RiskTrigger` 加了幂等保护）。③ `AntFarm`：my_dev 早已整段删除自动睡觉，不引入上游的 `animalSleepNow`；起床处保留 my_dev 的 opt 读取与 `countDown` 判断，只采用上游“比对 `spaceType` == `ChickFamily`”（上游自述未实机验证，若服务端家庭空间取值不是 ChickFamily，家庭起床将不再触发）。
- 自动合并进来的行为变化：`CaptchaHook` 把 VPN 判断放宽为含“VPN”或“代理”即关闭弹窗（原为整句精确匹配，开关默认关闭，开着时有误关真验证弹窗的风险）；`AntForestV2` 翻倍卡额外能量改为 `leftEnergy > 0` 即收，字段「倍卡额外能量(大于该值收取)」不再生效，字段暂留。
- 规范复查（GMT+8、JSON 创建、JSON 读取）：新增/修改代码无新的 Calendar/裸 JSON 构造/裸 `get*()`，`AntForestV2` 用 `MyUtils.newJSONObject`；`GeminiAI` 未动。9 项回归与 debug 编译通过。

**5. 删除「版本伪装」（`602eca3d`）**
- 依据：09-19 真机日志（伪装开启、提前伪装生效，`已伪装173次`）弹出的仍是需对准图片的滑块，伪装版本改变不了服务端下发的验证码类型；默认早已改回关闭，运行日志里“开关=关”。
- 范围：`VersionHook` 整个类、扩展功能页“版本伪装”卡片、`ApplicationHook` 里的注册/加载/日志、`version_config.json` 读写、`getEffectiveVersion`、`audit_regressions` 里对 `VersionHook.handleRead` 的断言；`alipayVersion` 现在始终是真实版本。`docs/GR-Sync.md` 该条改写为“最初不迁入→my_dev 移植→2026-09-21 验证无效后删除”。上游没有这个类，无合并冲突；旧设备上的 `version_config.json` 无人读取，可手动删除。

**6. 拼图滑块自动验证（移植 GR2026 `2609141630`，`fcee963f` 起）**
- 先核实：GR 的 `libsesame.so`（两个版本 4 个架构逐一比对，哈希相同）JNI 导出只有 AES 加解密、庄园饲料任务、`unlockSesame`、签名校验（`Validator`），没有任何验证码识别/图像符号；`guard.cpp` 是签名校验，`watermark.cpp` 是水印。识别全在 Java：`PixelCopy` 截图 → 边缘/纹理/被遮挡轮廓匹配 → `MotionEvent` 拖动。`Puzzle*` 五个类在两个 GR 版本里完全一致；流程类（`BaseCaptchaHandler`、`MotionEventSimulator`、`SimplePageManager`）有差异，参考较新的 `1630`。GR 与本项目同为 GPLv3。
- 移植：`PuzzleSliderMatcherCore`/`PuzzleTextureMatcherCore`/`PuzzleOccludedContourMatcher`/`PuzzleSliderGeometry`/`PuzzleSliderMatcher`（换包名，纯 Java）；流程重写为精简版 `PuzzleCaptchaSolver`（监视窗口→找 WebView→`PixelCopy`→工作线程按颜色连通块识别按钮与轨道终点→图像匹配→主线程拖动）和 `PuzzleSwipe`（触摸序列，屏幕坐标换算成视图内坐标）；新增配置「自动处理图片拼图滑块验证」（`BaseModel.autoPuzzleSlider`，默认开）；窗口监控（栈顶 Activity/对话框跟踪）不再受版本限制。
- 约束（沿用 GR 经验）：每个验证码窗口最多自动拖动 N 次（最初沿用 GR 只拖 1 次，用户反馈后改为可配置，默认 4，范围 1-5，见 2026-09-21 摘要）；识别不可信不动手；延迟回调与工作线程都用 `TaskLifecycle.enter(generation)`；被动扫描只静默 8 秒。截图与过程：截图存 `/sdcard/Android/media/com.eg.android.AlipayGphone/sesame-M/puzzle/<账号名>/`（只留最新 10 张；最初放在 `log/<账号ID>/puzzle/`，用户找不到且清日志会被删，`e0a85a88` 改到现位置；注意 `PACKAGE_NAME` 是支付宝包名，不是模块 App 包名），过程写进「验证记录」日志（`拼图验证🧩…`）。
- 截图保留规则（用户反馈后定，`76c6edbd`、`61d52f05`、`188b96eb`）：处理过程中每次截图都存（没识别到滑块的存成 `*-no-slider.png`，识别到滑块的存成 `no-track`/`match-failed`/`matched-d<位移>`）；验证结束（拖动后 1.5 秒检查完，或 60 秒监视窗口期结束）后由 `cleanupNoSlider` 在工作线程删除所有 `no-slider`，只留包含验证码的；每个账号最多保留最新 10 张包含验证码的（目录按账号分），无验证码的另设 16 张兜底上限，互不挤占；被动静默扫描不保存任何截图。曾短暂改成“每窗口只存 1 张 no-slider”，被用户否掉。用户遇到识别不了的验证码会把 `puzzle/<账号ID>/` 里的截图发来校准。
- 验证：匹配算法用 GR 记录的 9 个真实脱敏样本离线回放通过（`checks/check_puzzle_matcher.py`，夹具 `checks/fixtures/puzzle-slider/`，约 6.8MB，为 gzip 压缩的原始 RGBA 像素，不是 PNG）；**流程没有在真机上验证**。滑块按钮识别沿用 GR 参考设备布局（宽 1264、按钮约 (236,1787)）的颜色/位置常量，别的布局可能识别不到——识别不到只记日志并保存截图，不会乱拖。GR 自述“部分图片验证失败问题尚待定位”，成功率不保证。
- 没移植：悬浮“点击开始”控制按钮（`PuzzleControlOverlay`）、手动触摸中断监视（`ManualTouchMonitor`）、HTTP 抓包与页面探针、后台模式、原生对话框里的拼图路径、Nebula `H5Utils` 打开链路钩子。
- 复查（agy）采纳的：拼图拖动后窗口关闭时补 `clearVerifyPause`（`ee878329`）；H5 触发三条（见第 2 条）。其余对设计的确认没有改动。

**7. 版本与发布**
- `gradle.properties` 1.1.5 → 1.1.6（`5ed2af90`），tag `v1.1.6` 指向该提交并已推送；之后的提交（`e0a85a88` 起）不在 tag 内，用户要求不重打 tag。本地 Release 包（`Sesame-M-Normal-arm64-v8a-1.1.6.apk`，arm64-v8a，已签名）是用最新代码编译的，`versionCode` 由 git 提交数生成。
- `.kotlin/sessions/*.salive` 是 Kotlin 编译的临时文件，被跟踪但每次编译会变化，不提交。

**8. 真机验证结果（`2c005d55` 编译的包）**
- 用户手动触发验证码，拼图自动验证成功一次：截图、滑块识别、缺口匹配、触摸拖动整条链路在真机上走通。
- 没有导出那次的运行日志/验证记录/`puzzle/` 截图，所以不知道是哪个触发点接住的，也没有保存的位移与分数可以对照。
- 只有一次成功，不能当成功率：GR 自述部分图片验证失败问题待定位；滑块按钮识别沿用参考设备布局，别的验证码样式/分辨率仍可能识别不到。之后遇到失败请保留 `puzzle/` 截图和验证记录。

**9. 待观察**
- 装最新包后看运行日志开头有无“`CaptchaDialog.show()` 验证监视钩子已挂载”“H5 验证页监视已挂载 N 个钩子”；验证码一出现立刻导出运行日志、验证记录与 `puzzle/` 截图，据此判断是“没挂上”“没触发”还是“触发了但没识别到滑块”。
- `risk`/`verify` 关键词较宽，可能多记几条无关验证记录（只启动 60 秒监视，不会拖动）。

### 2026-09-19（续）：合并 MIUIX-api102 至 b1293b40

上游 2 个提交：① 广播来源校验（`ApplicationHook.isTrustedBroadcastSender`：Android 14+ 用 `getSentFromUid` 校验，白名单为本进程/模块 App `io.github.aw1y2z.sesame`/adb shell，取不到来源或低版本一律放行）、清理注释掉的旧代码、`BaseModel`/`FileUtil`/`ConfigV2` 统一日志截断；② 捐蛋排位 `competition()` 语义收紧：只有“接口成功但没有排位首页”才返回 false 触发公益捐蛋回退，接口异常/数据缺失/20:01 后跳过都返回 true，避免当天已为排位捐过蛋、晚上又捐一次公益。

冲突及取舍：
- `ApplicationHook`：上游在 `Application.attach` 里直接同步调用 `initSimplePageManager()`；my_dev 此前已把它挪到 `Service.onCreate`（版本伪装覆盖 `alipayVersion` 之后调用，对齐 GR）。保留 my_dev 做法，不在 attach 里再调一次，否则会调用两次。广播来源校验的新增代码自动合并，已核对 `MODULE_PACKAGE_NAME` 与 `app/build.gradle` 的 `applicationId`（`io.github.aw1y2z.sesame`）一致。
- `BaseModel`：上游 `getString` + `StringUtil.truncate`，my_dev 已是 `optString`；取 `optString` + 上游的截断日志。
- `AntFarm`：my_dev 把 `run()` 拆成了 `step()`，上游改的是老结构里的捐蛋回退，git 把两边错位对齐。整块取 my_dev 的 `step()` 结构，把上游对回退逻辑的改动手工搬进 `step("捐蛋")`（仅“确认当天没有排位活动”才回退，回退时打日志）；`competition()` 内部采用上游新语义，`getJSONObject` 改 `optJSONObject` 并对 null 按“有首页但缺榜单，不回退公益捐蛋”返回 true；20:01 后 `return true` 的处理两边一致，去掉我们多余的注释行。

三项必查（对上游新增行做了检索）：GMT+8——无日历/日期/时区代码；JSON 创建——无直接 `new JSONObject(raw)`；JSON 读取——无裸 `.get*()`。无新增例外。验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；十项回归全部通过。未真机验证、未打包。

### 2026-09-19（续）：版本伪装改回默认关闭，新增「验证记录」日志类型

**版本伪装的最终结论**：1.1.5 起默认开启，并经过诊断日志、`Flags` 重载、定向提前伪装等多轮修正。最后一份真机日志（未认证小号，18:01，184719 包，`提前伪装=开`）：开关就绪前 8 次读取是真实版本，`早期伪装(日志模块)2次`，之后 `已伪装173次`、`PackageInfoFlags重载1次`——从支付宝进程的角度看它读到的自身版本几乎全程是 10.6.58.8000，但弹出的仍是需对准图片的滑块。结论：**这台设备/账号上，通过 `PackageManager` 伪装版本不能让服务端改发“滑到最右”的验证码**；类型更可能由服务端按账号风险/设备指纹决定（未认证小号本来就容易走更难的验证），或者服务端看的版本来自我们没伪装的渠道（请求头/UA）。因此改回默认关闭；诊断日志、`Flags` 重载、调用方识别等代码保留。
- 一次性回退：`defaultOffApplied` 标记区分“1.1.5 自动写入的默认值”和“用户改过的配置”，见上方摘要；这是启发式，在 1.1.5 手动开启且没改默认值的用户会被关一次。
- 已知小问题未修：日志“实际版本”行显示伪装值（疑似系统缓存 `PackageInfo` 被就地改写）。
- 没做的：自己处理对准图片的滑块（需要图像识别找缺口，工作量大、成功率不确定）；遇到验证就暂停触发它的功能——用户明确说触发验证的功能（庄园使用美食/亲密家庭请客）**不能暂停**，手动验证一次后功能就恢复，所以只做统计，不做暂停。

**「验证记录」日志类型**：用户要求统计哪些功能会触发弹出验证以便优化，并要求单独一个日志类型方便查看。
- 接入点（对照金豆记录）：`AppConfig.enableCaptchaLog`（默认开）、`Log.captchaLogger`/`runtimeCaptchaLogger`/`Log.captcha`、`FileUtil.getCaptchaLogFile`、`LogType.CAPTCHA`（日志页）、首页 `LogsTab` 的开关行；运行日志里以 `CAPTCHA` tag 出现，可用 tag 筛选。`Log.captcha` 不调用 `countModuleLog`，避免验证弹窗让“本轮无操作”提示失效。
- 事件内容：`验证码弹窗🔍类型[…]#来源[…]#运行中模块[…]#最近请求[…]#文字[…]`。类型按界面文字判定：含“向右滑动验证”=可自动处理；含拼图/对准/缺口/拖动/滑块/图片=对准图片拼图（无法自动处理）；其它=未识别。来源=`CaptchaDialog`（安全 SDK 的弹窗，`SimplePageManager` 已有的 `show()` hook 之后调用）或 `Activity:<类名>`（处理器在 Activity 里没找到“向右滑动验证”但界面有验证相关文字，可能是 H5 里的拼图）。同来源同类型 30 秒去重（处理器重试、Activity 反复 resume）。`RpcRequestGuard` 构造时记录最近 8 个请求方法与时间，`recentRequests(n)` 取最近 n 个。
- 局限：归因是推断——弹窗前最近的请求和运行中的模块只是嫌疑对象；手动操作触发的验证显示为“无运行中模块”；统计计数进程重启清零（每个事件本身在日志里可再统计）；依赖 `CaptchaDialog.show()` hook 与 Activity 文字扫描，若拼图验证走了别的类且界面没有“验证/拼图/滑块/缺口”文字则记不到。未真机验证。
- 三项必查：无日期/JSON 创建，读取无 JSON；GMT+8 无关。回归：十项通过（含 `check_rpc_guard`、`check_standalone_no_xposed_class`）。

### 2026-09-19（续）：多阶段饲料任务改为先做完再领、伪装调用方识别修复、日志页切换 tag 回顶部

**1. 庄园多阶段饲料任务（`0c409ef7`）**：用户反馈饲料任务停在 180/240，AG 会到 240/240 并一次领 240g。日志（1.1.5，18:18 编译）里庄园阶段只有“还有待领取的饲料”，没有任何“饲料任务🧾完成”。原因：上一版按轮执行只是 GR/M 的“做一阶段→领一阶段（30g）”加了循环，`receiveFarmTaskAward` 用 `awardCount + foodStock > foodStockLimit` 判断容量，饲料快满时领不了就停，任务卡在“有奖没领、也不做下一阶段”。对照 AG（`getMultiStageAccumulatedAward`、`mapPhase`）：AG 把“领奖”和“做下一阶段”分开，领不了就先做后面的阶段，奖励累积，最终 240/240 一次领。
- 现：按轮执行时，FINISHED 状态且 `rightsTimesLimit>1`、`rightsTimes<limit` 的多阶段任务先 `doFarmTask` 继续做阶段（用户明确要求“完成任务就行，不用马上领，用了饲料再领”），阶段做不了才退回领奖；全部做满或喂鸡腾出容量后由领奖路径/既有 `checkUnReceiveTaskAward` 一起领。`checkUnReceiveTaskAward` 的单独领奖遍历不变。
- 待领额改用 `pendingAward = awardCount − alreadyReceiveStageAwardCount`（AG `getMultiStageAccumulatedAward`），原先拿累计总额（如 240g）判断容量，会把放得下的待领奖励误判成超上限；差值为 0 时退回 `awardCount`，单阶段任务行为不变。
- `alreadyTried` 按“动作(do/receive/stage)+bizKey+taskId+状态+rightsTimes+待领额”去重；最大轮数 10→20（8 阶段再加领奖轮）。
- 不确定：服务端是否允许有待领奖励时继续 `doFarmTask`（AG 走这条路径，M 未实测，所以保留“做不了才退回领奖”的兜底）；`alreadyReceiveStageAwardCount` 的语义按 AG 的用法推断。未真机验证，`AntFarm` 过大无回归覆盖。三项必查：无日期/JSON 创建，读取全为 `opt*`。

**2. 版本伪装“提前伪装”没生效的原因（`03b52936`）**：`runtime.2026-09-19.<账号>` 日志（18:18 编译）显示 `提前伪装=开`、开关就绪前早读 12 次、`早期伪装(日志模块)0次`，来源栏又变回 `VectorChain/VectorNativeHooker` 框架帧。`callerFrames` 只在栈顶 24 帧里找最后一个 hook 机制帧，LSPatch 加载器等多层 hook 嵌套时框架帧超过窗口，把框架帧当成调用方，`fromLoggingModule` 认不出 `com.alipay.mobile.common.logging.`。改为扫描整个栈、按类名跳过 hook 机制帧（`org.matrix.vector`/`org.lsposed`/`LSPatch_`/`libxposed`）、反射/`ApplicationPackageManager` 帧和本模块帧（R8 短名类不含 `.`、`io.github.aw1y2z` 包）；来源样本相同的只留一条，每类上限 3→6。仍是假设：日志模块被识别并改写后，验证码类型是否会变，取决于 `LogContextImpl` 缓存的版本是否就是决定因素。

**3. 日志查看器切换 tag 回顶部（`dddb5807`）**：`LogScreen` 里 `listState` 与筛选条件互不相干，过滤结果变了滚动位置仍保留。加 `LaunchedEffect(selectedTag, searchQuery) { listState.requestScrollToItem(0) }`；搜索输入每敲一个字也回顶，属预期。无回归覆盖（Compose UI）。

### 2026-09-19（续）：版本伪装排查、庄园饲料任务按轮执行、导出日志文件名带账号

**1. 版本伪装为什么没让验证码变成“滑到最右”（排查过程与结论）**
- 背景：1.1.5 把 `VersionHook` 默认开启为 10.6.58.8000/1881，目的是让服务端下发只需向右滑到最右的简单滑块（`BaseCaptchaHandler` 只识别“向右滑动验证”文字，对需要对准图片的拼图滑块没有任何处理能力，日志里也从未出现过 `滑动验证🆘`）。用户实测弹出的仍是对准图片的滑块。
- 第一层原因：≤1.1.4 自动建的 `version_config.json`（关闭、版本名空、版本号 0）不会被“新建文件才写默认值”的逻辑改写，老用户仍是关闭。`loadVersionConfig` 现把这种未改动的旧默认迁移为默认开启并保存。
- 第二层原因（诊断日志发现）：配置显示“已伪装”，但只有配置加载之后的读取才被改写。新增每轮打印的 `版本伪装诊断` 行（早读/已伪装/关闭/`PackageInfoFlags` 计数与读取来源）。首轮真机日志：开关就绪前支付宝读自身版本 11 次，全是真实版本 12.12.20.8000，全走 `int` 重载；来源栏因 R8 把 hook 框架类混淆成 `yb2/ac2` 而全是框架帧，改为越过 hook 机制帧（LSPosed/Vector/LSPatch/libxposed）取调用方后，第二轮日志显示来源是 `com.alipay.mobile.common.logging.ContextInfo.b < LogContextImpl.<init>`、`logging.util.perf.Judge.<init>`（日志/上下文模块，启动时读一次并缓存，最可能就是发给服务端的“应用版本”）和 `com.alipay.mobile.quinox.startup.UpgradeHelper.getUpdatedTimeFromPackageInfo < upgrade`（升级检查）。
- 修复：① 两个 `getPackageInfo` 重载（`int` 与 API 33+ 的 `PackageInfoFlags`）共用 `handleRead` 改写，`int` 重载内部委托到 `Flags` 时跳过；② `sInIntOverload` 只对支付宝自己的包名置位（探测未安装的微信/QQ 会抛 `NameNotFoundException`，`XHelpers` 此时不执行 `afterHookedMethod`，标志会残留在常驻线程上）；③ `saveVersionConfig` 写回 `earlyEnable`（否则用户手写的会被覆盖抹掉）；④ **定向提前伪装**：`sEarlyFake` 缺省为开（读不到配置就按默认开启、默认 10.6.58.8000，只有配置明确关闭才关），配置加载前对“紧邻 2 个调用帧属于 `com.alipay.mobile.common.logging.`”的读取改写版本，`UpgradeHelper` 等其它早读保持真实版本；`earlyEnable` 缺省为真，写 `false` 可关；⑤ 模块自己记录“实际版本”改用 `readRealVersionName` 绕过伪装。
- 取舍：不做“全部早读都伪装”——`UpgradeHelper` 拿版本做升级判断，读到假版本可能让支付宝以为自己升级/降级而触发重装或清缓存，主力手机风险不小；先只伪装日志模块，若下一轮日志显示日志模块已被伪装而验证码仍是拼图，再逐步放宽。
- 未验证：`LogContextImpl` 缓存的版本是否就是决定验证码类型的那个；`Judge`/`quinox` 也可能才是。生效需要**重启支付宝**（日志模块启动时才读），换号不算。看诊断行里 `提前伪装=开`、`早期伪装(日志模块)N次` 是否大于 0，再看弹出的验证码类型。`audit_regressions` 中伪装取值断言改指向 `earlyName()/earlyCode()`。

**2. 庄园饲料任务按轮执行，全部完成后当天不再查询（对照 AG）**
- 现象：日志里 `试玩庄园火爆小游戏`（每次 30g）在 09:30/09:59/10:04/10:59/11:11/16:17 各做一次，原先每次执行 `listFarmTask(TODO)` + `listFarmTask(FINISHED)` 各一次，多次任务每轮只推进一次，完成后每轮仍继续查询；AG 用 `TaskFlowEngine` 多轮做到没有进展，并用 `FLAG_FARM_TASK_FINISHED` 等标记，且没做完不记完成。
- 做法（不搬 AG 的整套 `TaskFlowEngine`，只取核心）：`runFarmTaskRounds` 每轮一次 `listFarmTask(null)` 同时处理 TODO 与 FINISHED，返回 {需要处理数, 推进数}：需要处理为 0 → 记当日标记 `antFarm::farmTaskAllDone`（`Status` 按账号存储、次日清）；有任务但无推进 → 停、不记标记；有推进 → 再来一轮，最多 10 轮。`alreadyTried` 按“bizKey+taskId+状态+rightsTimes”去重（对照 AG `actionKey`），避免服务端返回成功但状态不变的任务白跑满 10 轮。`receiveFarmTaskAward` 对非饲料奖励 RPC 成功后改返回 true（原返回 false 会让它一直被当成没做完）；不支持 RPC 完成的 bizKey 抽成 `isUnsupportedFarmTask`，不计入需要处理数。
- 代价：当天新出现的任务要等次日；饲料满领不了奖的任务会让标记迟迟不记（与原先每轮都查一致）。范围仅庄园饲料任务，其它模块的一次性任务未动。三项必查：无日历/日期代码、无 JSON 创建、读取全为 `opt*`。未真机验证，`AntFarm` 过大无回归覆盖。

**3. 导出日志文件名带账号**：`FileUtil.exportFile` 对 `log/<userId>/` 下的文件在扩展名前插入 userId（`runtime.2026-09-19.log` → `runtime.2026-09-19.2088702045701743.log`），多账号导出到同一下载目录不再重名/覆盖，与 `rpc-failures.日期.账号.json` 命名一致；文件名已含账号或取不到账号（`default`）时保持原名。日志**内容**仍不写 uid/昵称，只是文件名带账号，分享文件时要注意；用 uid 而非昵称/序号是因为独立 App 进程读不到昵称映射，而账号目录名就是 uid。未真机验证，`exportFile` 依赖 `Environment` 无回归覆盖。

**文档改动汇总补充**：`CHANGELOG.md` 新增本节；此前“本日文档改动汇总”所列 `docs/MyFix.md` 第 5 条与 `AGENTS.md` 一行不变。

### 2026-09-19（续）：没开通/未认证的功能一天最多请求一次；文档改动汇总

**问题**：`runtime.2026-09-19.log`（单账号）196 次失败响应里，150 次是 `com.alipay.antfarm.collectManurePot` 返回 `G04`“肥料已经存满了，去开通芭芭农场种果树吧”：庄园每次同步小鸡状态，肥料罐 ≥100 就去领，两个罐子各请求一次；小号没实名认证开不了芭芭农场，肥料永远存不进去，所以每轮都失败。其余 46 次失败分散在 25 个接口里（48/3 网络错误 30 余次、`receiveFarmTaskAward` 102 六次等），均已被现有退避覆盖。

**过程与取舍**：
- 最初只有用户口述的一句提示，源码里搜不到该文案，误判为芭芭农场任务列表，在 `AntOrchard` 里给失败任务加了当日标记；拿到运行日志后确认来源是庄园肥料罐，撤回该改动（未提交）。随后在 `AntFarm` 里对 `G04` 记当日标记，用户明确要求的是“所有没开通的功能都最多一天一次”，逐点加标记覆盖不了，也撤回（未提交）。
- 最终改在所有 RPC 请求收口的 `RpcRequestGuard`：`isNotOpened(message)` 命中“去开通/请先开通/请先认证/请先实名/未认证/未实名”即该请求暂停 24 小时（`DAY`），按账号隔离，与既有暂停机制、请求保护日志一致。只匹配对本人的提示：文案含“好友”或“对方”一律不算（外部审查指出“对方未实名认证”这类陈述句会被“未实名/未认证”误命中，好友互动接口的请求键又不含目标好友，会把整个接口对所有好友停一天），避免一个好友的状态停掉整个方法。
- 既有回归断言随之调整：`check_rpc_guard.py` 中会员 `NOT_CERTIFIED`“请先实名认证”原为 4 次调用发出 3 次（5/5/30 分钟阶梯），现为 1 次。属有意的行为变化：未认证账号会员相关请求的重试次数减少。
- 新增回归：肥料罐 `G04` 后 `DAY-1` 内跳过、`DAY` 后恢复；“好友未开通该功能”不暂停。

**局限**：暂停 24 小时而非自然日；只有日志里出现过的 `G04` 有实测样本，其它接口的“未开通”措辞需在日志中出现后再补关键词；各模块自己打印的“绿色经营未开通/芝麻信用未开通”是本地判断，不经此规则；未真机验证。

**三项必查**：GMT+8——沿用 guard 既有毫秒计时，无新增日期计算；JSON 创建/读取——本次只读取已有的 `message`/`code` 字符串，无新增 JSON 构造与裸 `.get*()`。

**本日文档改动汇总**（按要求一并记录）：
- `CHANGELOG.md`：顶部 09-19 摘要与本节及“移植找能量”“合并 MIUIX-api102（两次）”“第三个账号日报”等详细记录。
- `docs/MyFix.md`：新增硬性规则第 5 条——`GeminiAI`（含 `AnswerAIInterface`）海外用户正在使用，合并时不能删除，配置 id `useGeminiAI`/`useGeminiAIToken` 不能改。
- `AGENTS.md`：合并说明里加一行“合并时不要删 `GeminiAI`”，指向 `docs/MyFix.md` 第 5 条。
- `gradle.properties` 版本号 `1.1.2` → `1.1.5`（用户已提交为 `2357cd25 v1.1.5`）。

### 2026-09-19（续）：合并 MIUIX-api102 至 93140f06

上游 1 个提交：光盘行动图片「清空」跨进程失效修复（`TokenConfig` 读取数量前先从磁盘重载）、异常捕获收紧、日志用新增的 `StringUtil.truncate` 截断（`BaseModel`、`AntMember`、`AntOcean`、`AntFarm`、`AnswerAI`），`PermissionUtil`、`ExtensionsHandle` 小改。

冲突及取舍：
- `TokenConfig`：my_dev 在 `getDishImageCount` 前新增了 `writeDishImage`/`writeDishImageWithRandomIds`（对齐 GR2026），上游把 `getDishImageCount` 改为 `synchronized` 并先 `reloadDishImageList()`。两侧改动位置相邻，属假冲突：保留 my_dev 两个新方法，`getDishImageCount` 取上游写法。
- `BaseModel`：上游 `catch (JSONException e)` + `e.printStackTrace()` + 截断日志；my_dev 已把解析换成 `MyUtils.newJSONObject`，不再抛 `JSONException`，若照搬会因“从未抛出的受检异常”编译失败。保留 `catch (Throwable e)` + `Log.printStackTrace(e)`，日志采用上游的 `StringUtil.truncate(..., 200)`。
- 自动合并的 `AnswerAI`（`trimForLog` 改用 `StringUtil.truncate`）已核对：`GeminiAI`/`useGeminiAI`/`useGeminiAIToken` 与 AI 类型选项完整保留；`AntMember` 里 `kuaidiForestAward` 改动保留。

三项必查（对上游新增行做了检索）：GMT+8——无日历/日期/时区代码；JSON 创建——无直接 `new JSONObject(raw)`；JSON 读取——无裸 `.get*()`，无对 `opt*` 结果的未判空链式调用。无新增例外。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；十项回归（含 `check_standalone_no_xposed_class.py`）全部通过。未真机验证、未打包。

### 2026-09-19（续）：合并 MIUIX-api102 至 d51b841f

**更正（合并提交 `458b043a` 之后）**：下文“`GeminiAI`/`TongyiAI` 跟随上游删除”以及“`audit_regressions` 移除两项检查、`CustomAI.parseAnswerIndex` 无覆盖”的处理是错误的——`GeminiAI` 海外正在使用。已在后续提交中恢复 `GeminiAI`、`AnswerAIInterface`、被移除的两项回归检查与 `Answers.java.in`，`AnswerAI` 同时支持 GEMINI 与自定义AI（`CustomAI`），`GeminiAI` 的答案匹配重新有回归覆盖；只有通义千问保持删除。教训：上游删除文件时要先确认该文件是否仍有人在用，而不是只看仓库内有没有引用。

上游 7 个提交：庄园捐蛋排位赛不存在时 fallback 到公益捐蛋、农场施肥场景显示名（main→果树、yeb→金钱树）、亲密家庭若干修复、森林合种浇水顺序与空值保护、保护合种浇水量计算、AI 答题重构为可自填的通用接口（`CustomAI`）、日志路径触达 libxposed 类导致 App 闪退。

冲突及取舍（按分叉点 4f975462 双方状态判断，多数为“my_dev 已把 `.get*()` 改成 `.opt*()`/`MyUtils`，上游同处又改了逻辑”，取上游逻辑 + my_dev 的读取方式）：
- `GeminiAI`/`TongyiAI`：my_dev 改过（JSON 创建、OkHttp 单例、选项匹配），上游删除并由 `CustomAI` 取代。确认全仓库已无引用后跟随上游删除，`AnswerAIInterface` 同删。
- `AntFarm`：① 20:01 后捐蛋排位跳过分支——上游机械把 `return;` 改成 `return false;`，但 `competition()` 现在 `false` 表示“排位赛不存在，fallback 到公益捐蛋”，20:01 后属于“排位赛存在只是过了截止时间”，保留 my_dev 的 `return true`，避免 20:01 后又去公益捐蛋；② 庄园答题取上游的空选项提示 + my_dev 的 `opt*` 与空指针防护，`AnswerAI` 已内置兜底取第一项，去掉重复兜底；③ 家庭“顶梁柱特权”采用上游对 `RandomUtil.nextInt` 右开区间的修正（上界传 `size()`/`length()`，原 `size()-1` 永远取不到最后一个），保留 my_dev 的空列表/空对象防护与 `MyUtils`；④ `familyFeedFriendAnimal`：上游删除，全仓库无其它调用，跟随删除；⑤ 家庭分享 `invitedCount` 计数取上游。
- `AntForestV2`：组队合种浇水改用上游的 `queryTeamHomePage()`/`getTeamId`/`isTeam`，真爱合种改用上游按队伍名浇水的写法；四处创建 JSON 保持 `MyUtils.newJSONObject`。
- `AntOcean` 海洋答题：交给 `AnswerAI` 作答（上游），读取用 `opt*`，选项为空/缺失时跳过。
- `AntOrchard`：场景显示名用上游 `getSceneDisplayName`，读取 `optString`。
- `ProtectEcology`：保护合种取上游“未勾选日浇水量则静默跳过”，`waterDayLimit` 保持 `optInt`。
- `ReadingDada`：`opt*` + 空选项提示 + 兜底取第一项。
- `ExtensionsHandle`：取上游合种提示文案（无类型时只显示“可以合种”）。

三项必查（对合并结果中来自上游的全部新增行做了检索）：
- GMT+8：上游新增内容无日历/日期格式化/时区相关代码，无需处理。
- JSON 创建：发现三处直接 `new JSONObject(raw)`——`AntForestV2.queryTeamHomePage`、`CustomAI.requestOnce`、`CustomAI.parseJsonOrNull`，均已改为 `MyUtils.newJSONObject`。`parseJsonOrNull` 原靠解析异常返回 null，改为空对象视为失败并返回 null，语义不变。
- JSON 读取：上游新增内容无裸 `.get*()`，无对 `optJSONObject`/`optJSONArray` 结果的未判空链式调用。
- 例外/遗留：无新增例外。上游 `CustomAI` 是 479 行的新解析逻辑，本次只做了三项规范检查，未逐行审计其解析行为，也未做真机验证。

回归：`checks/audit_regressions/run.py` 原有两项检查绑定在已删除的 `GeminiAI` 上（`getAnswer` 选项匹配规则：精确优先、含小数、歧义拒绝；`getAnswerStr` 不含 `replaceAll` 且 `return answer.trim()`），已一并移除并删除 `Answers.java.in`。**遗留：`CustomAI.parseAnswerIndex` 目前没有回归覆盖**，它依赖多个静态辅助方法，需要单独提取后再补。其余九项及 `check_standalone_no_xposed_class.py` 均通过，`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过。未真机验证、未打包。

### 2026-09-19（续）：移植「找能量」，评估朋友文件的其它新功能

**来源**：朋友给的 `找能量AntForestV2.java`（GR 系反编译代码，包名 `io.github.lazyimmortal.sesame`，6250 行，不能与 M 直接 diff）。按 `ModelField` id 对比，多出 10 项：找能量 `findEnergyCollect`、收能量个人模式 `forcePersonalMode`、升级发财树领红包 `autoMoneyTree`、养绿植赢免单 `autoPlant`、养绿植会员积分 `autoPlantMemberPoint`、IP联名馆 `autoExchangeIPProps`、活力值秒杀 `autoExchangeVitalitySeckill`、不复活能量好友列表 `dontProtectList`、组队动态浇水 `enableRankingTopUp`、能量雨组队模式 `energyRainInTeam`。已有功能的逻辑改动未逐处比对。在 AG、GR2026、Sure-Xu、M 中，只有「找能量」在 AG 有实现；养绿植、IP联名馆、活力值秒杀、能量雨组队四项四处都搜不到。

**移植的「找能量」**（以 AG `collectEnergyByTakeLook` / `AntForestRpcCall.takeLook` 为准，不搬朋友文件的反编译实现）：
- `AntForestRpcCall.takeLook(skipUsers, takeLookStart)`：方法 `alipay.antforest.forest.h5.takeLook`，`source` 取 AG 默认的 `chInfo_ch_appid-60000002`，版本档位对照 AG（>10.6.10 用 `20260107`，>10.5.88 用 `20240403`，其余 `20230501`），跟随伪装后的版本号。M 的 `RpcEntity` 没有 headers，AG 请求头里的 `source`/`ags-source` 未带。
- `AntForestV2.findAndCollectEnergy()`：最多 50 次；服务端返回空 `friendId`、非 `FRIEND` 动作、`takeLookEnd`、连续 3 次重复/自己即停；`hasErrorWait` 或响应失败即停；对每个好友调用现有 `collectUserEnergy(friendId, home, "ordinary")`，因此炸弹阈值、能量罩、`dontCollectMap` 全部复用；有能量罩的好友与 `dontCollectMap` 一起放进 `skipUsers`（值 `baohuzhao`，同 AG/朋友文件），让服务端不再推荐；每个好友间隔 500ms。挂在 `run()` 好友排行榜遍历之后。开关默认关闭，且要求「收集能量」已开。
- 审查后修正（外部审查意见逐条核对，均成立；另按第二轮意见：主页为空对象时不计入浏览数也不清零重复计数，重复/自己跳过前停顿 300ms；`collectUserEnergy` 在“全收”下每次多查一次 `queryHomePage` 是原有逻辑，未改）：`repeat` 连续重复计数在成功浏览后未清零，会被累计到 3 次提前结束，已清零；`hasShield` 在好友主页缺 `now` 时会把已过期的罩判为生效，改为 `hasActiveProp`，`now<=0` 退回本机时间；炸弹卡好友与能量罩好友一样加入 `skipUsers`（AG 为 `hasShield||hasBomb`），避免被反复推荐；`findEnergyCollect` 加 `setDependsOn("collectEnergy")`，关闭「收集能量」时配置页隐藏该开关。
- 跳过的：AG 的 `queryCombineBiz` 预曝光、`takeLookEnd` 结束上报、结束后 `take_look_end_task_list` 领奖、80 次上限（取朋友文件的 50）、异常冷却。原因：没有验证这些对收益有实质影响，先保证主流程；需要时按 AG 补。
- 朋友文件的 `findEnergyCollectDirect` 没搬：它自己重写了炸弹/能量罩判断并使用裸 `getLong/getString`，且依赖的 `shield_skip::` 标记在 M 里没有任何代码写入。

**发财树未移植**：AG 的「摇钱树」（`AntOrchard.receiveMoneyTreeReward`，`moneyTree.trigger`）M 已有等价实现（`AntOrchard.queryYebRevenueDetail` → `AntOrchardRpcCall.triggerYebMoneyTree`，同一接口和参数），无需移植。朋友文件里的「升级发财树领红包」是另一个模块 `model.task.moneyTree.MoneyTreeManager`，源码不在该文件内，GR2026/AG 里也没有，无法移植；需朋友提供 `MoneyTreeManager` 及其 RpcCall。

**三项必查**：GMT+8——无新增日期计算；JSON 创建——`MyUtils.newJSONObject`，响应用 `MessageUtil.checkResultCode` 校验；JSON 读取——全部 `opt*`，无裸 `.get*()`；`skipUsers.put`/`takeLook` 请求体构造属于创建，`JSONException` 由外层 `catch (Throwable)` 处理。无新增例外。

**验证**：编译通过。未做真机验证——`takeLook` 在 M 版本档位下是否可用、返回字段（`friendId`/`actionType`/`takeLookEnd`）是否与 AG 一致均未验证；未新增回归检查（`AntForestV2` 过大，现有 stub 方式不适合）。找能量是主动请求，可能提高风控概率，故默认关闭。

### 2026-09-19：异常日报复核、风控验证拉起、版本伪装默认开启、庄园/运动子任务隔离

**日报复核（两个账号 70+21 次）**：
- 庄园 `receiveFarmTaskAward` 102“服务器正在开小差”，同五个任务连续多日每天 8~12 次：同任务当天第 5 次起退避改 6 小时，前 4 次仍 5/5/30/30 分钟，不永久拉黑（`RpcRequestGuard`）。
- 我的快递 `KUAIDI_VITALITY` 领奖失败（无原因，共 15 次）：失败后当天不再重复领（`Status` 标记 `antMember::kuaidiForestAward`，`AntMember.java`），成功行为不变；`RPC_SKIPPED`（被 guard 暂停）不计为失败。
- 暂不处理：48 网络错误（已有退避）；`energyRain*` 1009（风控，已暂停 24 小时）；`donation` 218（配置项问题，证据不足）；`walk.go` “走慢一点”（业务限速）；`B_FREE_SEAT`、`TARGET_USER_PROTECT_BY_ENERGY_SHIELD`（正常业务提示）；金豆/`ORCHARD`/`loanpromoweb signin.query` 无原因 1~3 次（证据不足）。

**风控验证拉起**：`RpcRequestGuard` 遇 1009/“验证后继续”/“滑动验证”/`cheating traffic` 暂停 24 小时后，调用 `ApplicationHook.showVerification()` 把支付宝首页拉到前台，让验证界面弹出；账号切换中不拉，10 分钟内只拉一次。此前只暂停不拉起，后台时验证页不出现。验证页出现后的处理沿用既有 `SimplePageManager` → `Captcha1Handler`/`Captcha2Handler` → `BaseCaptchaHandler`（仅识别“向右滑动验证”，直接拖到最右）。`check_rpc_guard.py` 新增：首次触发拉起、已暂停不重复、48 网络错误不拉起。**未验证**：拉起后验证页是否出现在 `XRiverActivity`/`AlipayLogin`；验证通过后 24 小时暂停不会提前解除。

**版本伪装默认开启**：`VersionHook` 默认 `10.6.58.8000` / `1881`，与 GR2026 `AppConfig` 默认值一致。GR `strings2.xml` 写明“自动过简单滑块需要支付宝版本在 10.6.58 及以下”，所以这是该功能声称支持的最高版本（不是最低版本；新接口最低版本 10.3.96.8100 与验证码无关）。AG 无此功能。取舍：
- 只对**新建**的 `version_config.json` 生效；已存在的文件（含旧默认 `enableVersionHook=false`）不改，需在扩展页手动打开或删除文件。静态初始值仍为 false，配置加载前的 `getPackageInfo` 不会被误伪装。
- 改版本后需重启支付宝（版本号在进程启动时读取），不能遇到 1009 时临时伪装。
- 伪装只影响下发哪种验证码，不保证不触发风控；也没有验证过服务端对该版本一定下发简单滑块，也可能因版本与其它请求头不一致引入新的风控信号。**未真机验证**。

**第三个账号日报（2088942846628038，09-18，50 次）**：`transferEnergy` `ENERGY_INSUFFICIENT` 36 次（00:57~23:01，已修，见上）；`receiveFarmTaskAward` 102 `IP_chouchoule_juankuan` 3 次（已被 5/5/30 分钟退避覆盖，未到第 5 次，不触发 6 小时档）；`donation` 218 1 次（与另两个账号同一配置问题，暂不处理）；`greenmatrix.love.teamWater` `WATER_ENERGY_NOT_ENOUGH` 3 次、`walk.joinPath` “路线未授权” 3 次、`donate.walk.exchange` `AE0310515401` 2 次、`queryPointCert` `INVALID_MEMBER_GRADE` 1 次：次数少、属业务状态，暂不处理；`neverland.queryItemList` 1009“系统繁忙”1 次：确认该 1009 并非验证提示，收窄 `showVerification()` 触发条件（`check_rpc_guard.py` 增加该断言）。日报文件中文在终端显示乱码，按方法名/错误码及 UTF-8 解码判读。

**子任务隔离与步数同步**：
- 庄园 `AntFarm.run()`、运动 `AntSports.run()` 各子任务分别隔离（新增 `step()`），单个子任务抛异常只记日志并跳过自己。
- `RpcRequestGuard` 请求键对 `enterFarm` 补充 `userId`/`farmId`，好友庄园 enterFarm 失败不再暂停自己庄园。
- 运动同步步数：当前步数超过 18000 不再同步（`readDailyStep` hook 与主动推送均跳过）；`steps.query` 失败/被保护暂停不再让整轮提前 return，推送遇临时异常下一轮重试（仅接口不存在才标记当天跳过）。

**三项必查**：GMT+8——无新增日期计算，日标记沿用 `Status.hasFlagToday`/`flagToday`；JSON 创建——新增解析均走 `MyUtils.newJSONObject`，并用 `RpcRequestGuard.isFailure` 校验失败；JSON 读取——新增均为 `optString`，无裸 `.get*()`；版本配置读写沿用 `opt*`。无新增例外。

**验证**：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 通过；九项 Python 回归在最后一次代码改动后全部通过（其中 `check_merge_config`、`check_rpc_guard`、`audit_regressions` 因内存问题重跑过一次）。中途一次因系统虚拟内存提交额度不足（errno=1455）失败，停掉 Gradle daemon 后重跑通过。未跑 `assembleNormalRelease`、未真机验证、未提交。

### 2026-09-17（续）：每日异常报告复核及绿色经营签到场景隔离

按 `docs/每日异常反馈.txt` 分析当日报告：36 类、123 次失败（GMT+8，00:15–09:30），网络错误 48 次、无原因 42 次、庄园繁忙 25 次、饲料槽满 2 次、行走限制 3 次、安全验证 2 次、能量罩 1 次。报告只有失败次数及首末时间，没有成功响应、每次失败时间或安装版本，不能据此判定现有退避没有生效。

确认并修复：M 与本地 GR 的绿色经营均调用 `signInQuery` 查询 `PLAY102632271`、`PLAY102232206` 两个场景。M 的共享 `RpcRequestGuard` 请求键遗漏 `sceneId`，导致两个场景共用失败累计和冷却，一个场景成功会清掉另一个场景的历史；日报同样遗漏该字段，将 19 次查询失败合在一起。请求键对非空 `sceneId` 追加字段名和值；无此字段的请求保持旧键，保留已有冷却。日报白名单新增 `sceneId`，继续脱敏、截断并按场景分别累计。不改变请求参数，不新增永久跳过规则。旧日报无法反推场景，旧版混合冷却不迁移到任一具体场景；升级后按新键重新累计。回归先复现跨场景成功清空失败，修复后验证三次失败后的暂停、另一场景仍可请求及日报分组。

其他处理结论：

- 庄园 `cclyx_3bei_dgls_2`（8 次）、`cclyx_wdhysj_1cV2`（9 次）、`IP_chouchoule_juankuan`（8 次）均为领奖 `102`“服务器正在开小差”。当前共享 guard 已按账号/场景/任务执行 5/5/30 分钟退避，本地 GR 源码检索未找到这三个 ID 的明确失效规则；给既有回归补入两个新 ID，保留到期重试，不永久拉黑。
- `48` 网络错误已走现有核心/非核心请求退避，不能从跨模块网络失败推断具体任务失效。`cook`、`familyEatTogether` 的 `1009` 已走对应请求 24 小时暂停，需用户在支付宝正常完成安全验证，不绕过验证。
- `monthlyCard` 的 `331` 是饲料槽满，森林采集的能量罩属于业务状态，不停用整个领奖/收能量功能。
- `walk.go` 的“走慢一点”3 次：M 与 GR 都在失败时返回 false 结束本轮行走循环；报告不足以确定服务端限制条件，不新增永久规则。
- 无原因 42 次：绿色经营查询 19 次；`KUAIDI_VITALITY` 13 次；`10021/104322` 1 次；金豆 `GOLDENBEAN_GAME_ZH0_XDDQ`、`GOLDENBEAN_GAME_ZH0_NCDDP_V31`、`GOLDEN_BEAN_TASK_WAKUANG` 各 1 次，`XLIGHT_MIXED_COMPETITION_TASK`、`XLIGHT_MIXED_TASK_DENGHUODOUDI` 各 3 次。M/GR 快递领取参数一致，均在查询后尝试领奖；未见报告任务 ID 对应的 GR 明确失效证据。无法仅凭“无原因”区分未达标、重复领取、接口变化或空响应，暂不拉黑，后续需同时间段脱敏原始响应/任务状态。

三项必查：GMT+8——日期格式化仍显式 GMT+8，冷却继续毫秒计时，无新增日期计算；JSON 创建——将 guard 解析失败后的空对象统一为 `MyUtils.newJSONObject("{}")`，数组解析继续保留异常防护；JSON 读取——新增使用 `optString`，日报仍用 `opt` 并校验标量类型，无裸 JSON get。无新增规则例外；上述证据不足项继续观察。

验证：九项 Python 回归、`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 及 `git diff --check` 全部通过，包含签到场景冷却隔离和日报分组。配置回归首次因缺少 normalDebug 编译产物未能启动，完成编译后重跑通过。未真机验证、未打包、未提交、未推送。

### 2026-09-17（续）：修复合并后的数量编辑及配置子页面恢复

复查未推送的 MIUIX-api102 合并结果后修复两处问题：

- 四级选择页此前只为 `waterFriendList` / `wateredFriendList` 读取数量，其他 `SELECT_AND_COUNT` 字段修改勾选并保存时会将原数量重置为 1。改为所有计数字段均保留原数量并显示数量编辑；`SELECT_AND_COUNT_ONE` 同样支持编辑，初始化仍按 KVNode 读取，不能误走 Map 分支。沿用字段已有范围，浇水仍为 1~3，不因展示滑块改写未编辑的值。
- 三级/四级 Activity 被系统单独恢复时，不能依赖父页面已初始化静态配置。复用 `ConfigPreload` 新增 `ensurePrepared`：模型为空、配置未初始化或账号不匹配才初始化并加载，两个子页面在读取字段前调用；正常同账号跳转保留未落盘的内存修改，空字符串和 null 均视作默认配置。

三项必查：GMT+8——本次无新增日期、日历或时间格式化；JSON 创建——无新增 JSON 创建，继续复用原配置加载；JSON 读取——无新增 org.json 读取，数量读取对象为 Map/KVNode，不属于 JSON API。无新增规则例外或本次修复遗留项；独立 App 继续复用原有模型/配置初始化路径，未新增 ApplicationHook/XposedModule 调用。

回归：扩展 `checks/check_merge_config.py`，运行生产 Kotlin 初始化/保存分支及 Java 初始化门禁，覆盖普通计数字段保值（含 0 和大数量）、修改数量、单选替换、冷恢复、账号变化、默认配置和保留未保存修改；恢复旧数量门禁后检查按预期失败。Java/Kotlin 编译、九项 Python 回归及 `git diff --check` 全通过。并行检查时三项 JVM 启动曾因 Windows 提交内存不足失败，串行重跑全部通过。未真机验证、未打包、未提交、未推送。

### 2026-09-17（续）：合并 MIUIX-api102 至 0dae0755

从 `42b7a1fa`（自动切号冷却不再冻结账号任务）执行 `git merge origin/MIUIX-api102`，合入 `d0755133`"日志页新增搜索/过滤/分享功能，各模块日志增加tag"、`0dae0755`"新增 Miuix 配置三级/四级界面并统一保存策略"两个提交。三个文件冲突：

- `Log.java`：my_dev 侧早先已把各模块 logger 从静态字段改成按账号懒加载的 `getUserLogger()` 工厂方法（多账号日志分目录）；upstream 侧新增了运行日志按 tag 过滤所需的专用 sub-logger（`runtimeForestLogger` 等，写入 runtime.log 但用各模块 tag）以及 `forest()`/`goldenBeans()`/`farm()`/`other()` 的双路写入逻辑（运行日志按查看开关写、模块专属日志按模块开关写，不再经过 `record()` 转发，顺带修掉了 `record()` 内部 `countModuleLog()` 导致的重复计数）。两边都是真实功能、位置重叠但意图不冲突：把 upstream 新增的四个 runtime-tag sub-logger 也改成 `getUserLogger()` 工厂方法（type 都是 `"runtime"`，tag 不同），发现 `getUserLogger()` 原缓存 key 只有 `type::userId`，同一 `type="runtime"` 不同 tag 会互相覆盖缓存拿错 logger——顺手把 tag 也编进缓存 key（`type::tag::userId`）修掉这个新引入的隐患。`forest`/`goldenBeans`/`farm`/`other` 四个方法体采用 upstream 的双路写入结构，logger 调用换成账号感知的工厂方法；`forestLogger()`/`goldenBeansLogger()`/`farmLogger()`/`otherLogger()` 的 flattener 也按 upstream 的新版本加上 `{t}:` 前缀，配合日志页新的按 tag 过滤。
- `MiuixLogViewerActivity.kt`：my_dev 侧是已用 `checks/check_log_follow.py` 验证过的 FileObserver 实时刷新 + 列表不反转（最新在顶部）；upstream 侧新增搜索框、Runtime 页 tag 过滤 Chip、分享按钮。两者互不冲突但改的是同一批代码（`LogScreen`/`LogTopBar`），手动以 my_dev 的刷新/排序结构为骨架，把 upstream 的搜索/tag 过滤/分享功能整体嫁接进去：搜索与 tag 过滤在 my_dev 的 `entries` 基础上派生出 `filteredEntries` 供渲染，`onShare`/`onExportSummary` 两个可选回调都保留。同时清理了合并遗留的重复 `.padding(padding)`。
- `MiuixSettingsActivity.kt`：upstream 是整体架构重写（三级分组页 `MiuixGroupFieldsActivity`、四级选择编辑页 `MiuixSelectionEditActivity` 拆成独立 Activity、字段改原地展开、退出统一落盘策略），my_dev 只有两处局部 bug 修复（整数字段读取改用 `field.configValue` 而非反射转型 `MultiplyIntegerModelField`；单选替换而非累加选中集合）落在被整体替换掉的旧代码里。确认 upstream 这次重写没有带上 my_dev 这两处修复后，直接采纳 upstream 全文件重写，把两处修复移植到新位置：整数字段读取修复重新打在新版 `FieldItem()`（该函数继续留在 `MiuixSettingsActivity.kt`，被三级页复用，还是同一处旧 bug）；单选修复核对后发现 upstream 拆出的新 `MiuixSelectionEditActivity.kt` 本身已经是 `if (single) RadioButtonPreference(...sel = setOf(opt.id)...) else CheckboxPreference(...)` 结构，等价于要修的效果，不需要重复打。
- 自动合并未提示冲突的 `AntForestV2.java`（浇水字段范围 1~3）、`ConfigV2.java`（放开 `getModelFields`/`removeModelFields`）、`ModelConfig.java`（补充 `getCode`）、`SelectAndCountModelField.java`（新增 `valueRangeMin`/`Max`）：核对 upstream 改动，未见裸 JSON get/裸 `Calendar`。

`checks/check_log_follow.py`、`checks/check_merge_config.py` 里断言旧代码具体写法（`itemsIndexed(entries.asReversed()`、`sel = if (single) setOf(opt.id) else sel + opt.id`、`counts.filterKeys { it in sel }`）随上面两处重构失效，按新代码的等价写法更新断言，语义不变（列表仍是不反转+asReversed 渲染；单选仍是替换而非累加；保存仍只落选中项的数量，只是从 `filterKeys` 换成对 `sel` 做 `forEach` 达到同样效果）。

三项必查：合并未引入新的时间/日历逻辑；无新增 JSON 创建；JSON 读取无变化。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过；`checks/` 下九项 Python 回归（`account_lifecycle`、`audit_regressions`、`check_reward_cooldown`、`check_log_follow`、`check_merge_config`、`check_rpc_guard`、`check_gr_followups`、`check_manifest_permissions`、`check_account_switch`）全部通过。未真机验证，未打包，未推送。commit `a1f58a20`。

### 2026-09-17（续）：最终确认切回 A 即开始切号冷却，任务独立运行

按用户最新确认简化起算点：确认返回起始账号 A 且配置初始化成功后立即开始默认7200秒/自定义的切号冷却，同时解冻并恢复任务。无需等待 A 首轮完成再计时；A 的正常周期任务不受冷却影响，也不重置计时。到期后仍须等待运行任务结束，并满足首页有焦点、空闲15秒才切换。删除上一版专为等待首轮完成添加的 ModelTask 世代完成标记、Host.tasksEnabled 和等待状态，保持任务调度与切号计时独立。

回归验证返回 A 立即计时且不持有 freeze、冷却期间任务可执行、到期仍等待任务及首页、冷却不重复启动任务；九项 Python 检查、Java/Kotlin 编译及 diff 检查通过。三项必查：沿用单调时钟，无新增日期/时区或 JSON 创建读取逻辑，无例外。未真机验证、未打包、未提交、未推送。此条覆盖下方“等 A 本轮完成后起算”及“冷却期间冻结”的旧行为记录。

### 2026-09-17（续）：冷却只限制切号，回到 A 本轮完成后起算

按用户确认纠正此前“冷却期间冻结全部任务”的设计：A→B→C→A 后立即 releaseFreeze 并恢复 A 的正常任务；等待 A 返回后的本轮任务真正完成，再开始默认7200秒（可自定义）的切号冷却。冷却期间 A 的森林、庄园等周期任务照常调度，后续任务不会重置或延长冷却计时；冷却到期也不额外重启任务，只在首页有焦点、任务空闲15秒后允许下一次自动切号。冻结仅保留在实际切号和初始化窗口。

复用 ModelTask 的完成监听，发布“当前账号世代至少完成过一轮”标记，避免把 host.resume 后等待调度启动的短暂空闲误判为 A 已完成；旧世代完成标记不能满足新账号。基础模块关闭、无任务可调度时直接进入切号冷却，避免永远等待不存在的任务。关闭/重新激活开关清空等待和冷却；手动切号仍由原账号重载流程同步配置，控制器不再在未冻结时自行初始化。

验证：新增“返回 A 立即恢复”断言在旧实现失败，修改后通过；回归覆盖等待 A 完成后才计时、冷却期间任务可准入、后续任务不重置冷却、到期不重复恢复任务、到期仍等待运行任务和首页、关闭/重启开关、手动账号同步以及基础模块关闭。完成监听回归覆盖世代标记发布和账号隔离。九项 Python 检查及 Java/Kotlin 编译通过；未真机验证、未打包、未提交、未推送。此条覆盖下方旧记录中“冷却期间冻结”的行为说明。

三项必查：冷却沿用 elapsedRealtime，未新增日历/默认时区逻辑；无新增 JSON 创建或读取，无规则例外。

### 2026-09-17（续）：自动切号仅在支付宝首页有焦点时准入

用户要求操作庄园、农场、森林等二级页面时不得自动切号。此前只有登录/身份验证/验证码页面黑名单，普通二级页面仍可通过。控制器新增首页准入：顶层 Activity 必须精确匹配既有 AlipayLogin 首页类名、支付宝处于前台且该窗口有焦点；页面未知、二级 Activity、后台或无焦点均等待，并显示“等待返回支付宝首页，二级页面不切号”。非首页轮询清空15秒计时，返回首页且业务任务空闲后重新等待，不强制跳首页、不要求重跑任务。

冻结后及登录工作线程调用宿主接口前再次检查首页；准备期间离开首页则取消本次发起、解冻并重新等待，不将其当作切号失败永久暂停。已发出的宿主登录不能撤销；结果确认继续沿用独立的登录/验证页判断，避免把新首页准入条件套到正在进行的切号确认上。SimplePageManager.topActivity 改为 volatile，供后台控制线程读取最新页面引用。

验证：控制器回归覆盖未知页面、普通二级 Activity、后台、首页无焦点、返回首页重新计时，以及冻结后才导航离开的窗口；原轮转/冷却/生命周期回归保持通过。九项 Python 检查和 Java/Kotlin 编译通过，未打包、未提交、未推送；页面识别仍需真机验证，类名判断不区分同一首页 Activity 内部的标签页。

三项必查：计时继续使用 elapsedRealtime，不新增日历/时区逻辑；无新增 JSON 创建与读取路径，无 JSON 规则例外。

### 2026-09-17：返回起始账号后冷却，补齐异步游戏生命周期计数

修正自动切号轮次边界：原逻辑是 A→B→C→在 C 冷却→A；现在包括 C→A 在内的轮内切换都在任务空闲后等待15秒，确认返回开启开关的起始账号、初始化成功后才开始整轮冷却。冷却沿用自定义秒数（默认7200），期间保留 TaskLifecycle 冻结，不启动 A 的下一轮任务；到期解冻并触发 A 执行，然后继续轮转。关闭开关清空冷却并恢复任务；冷却中手动换号时先核对宿主身份并加载真实账号配置，避免恢复旧账号配置。

在现有未提交的 GameTask.report / WhackMole.start 生命周期改动上继续修正：Work 在提交异步任务前取得，排队、线程启动、实际执行全程计数；正常结束、执行异常和提交失败均释放。这样游戏上报/打地鼠尚未结束时，完成监听不能判空闲，自动切号的 freezeIfIdle 也不能通过。没有新增主线程等待或固定 sleep。此处修复的是已确认的任务漏计数和切号竞态，无真机 ANR 堆栈，不能据此宣称所有卡顿根因已经消除。

回归：账号状态检查改为验证返回后冷却，直接编译当前生产源码；补充实际控制器配合隔离宿主/时钟的 A→B→C→A、重复轮转、自定义冷却、期间冻结、到期恢复、关闭清空、手动切号和运行任务阻止切号场景。生命周期检查提取真实游戏异步入口，以可控排队/拒绝执行器验证提交前计数、执行/提交异常释放和冻结拒绝。AGENTS.md 列出的九项 Python 回归及 Java/Kotlin 编译全部通过。

三项必查：冷却沿用 SystemClock.elapsedRealtime 单调时钟，不新增日历/默认时区依赖；本次新增修改逻辑没有 JSON 创建或读取，无直接 JSONObject 构造、裸 JSON get 或新增判空例外。未打包、未提交、未推送，ANR 消失与否待真机复测。

### 2026-09-16（续）：合并 MIUIX-api102 至 fab57956

从 `47f099f3`（自动切号轮内15秒/整轮2小时冷却）执行 `git merge origin/MIUIX-api102`，合入 `3c516ddc`"会员积分兑换：获取列表不受开关限制，仅兑换用户勾选的权益"、`fab57956`"设置页添加文件权限引导并修复闪退"两个提交。四个文件冲突：

- `AntMember.java` 三处小冲突是纯风格差异（upstream 沿用未转换前的裸 `get*()`/`new JSONObject()`），保留 my_dev 侧 `MyUtils.newJSONObject` + `opt*()` 写法。`memberPointExchangeBenefit()` 是真实功能冲突：upstream 新增多 deliveryId 依次尝试 + 导航分类码备用接口 `fetchBenefitsFromNavi`、权益列表无条件保存（不再受开关限制）；my_dev 侧已经独立做了"仅兑换用户勾选的权益"（`memberPointExchangeBenefitList` 选择集）但列表刷新仍被开关挡住（调用方 `if (memberPointExchangeBenefit.getValue())` 才调用整个方法）——这正是 upstream commit message 要修的问题。采纳 upstream 的完整结构（多 deliveryId、导航备用、调用方去掉开关前置判断，方法内部先无条件刷新列表、只在开关关闭时提前返回不兑换），按硬性规则把其中裸 `get*()`/`new JSONObject()` 全部换成 `opt*()` + 空指针防护 + `MyUtils.newJSONObject`，包括自动合并未标冲突但同样违规的 `fetchBenefitsFromNavi()`。
- `AntOcean.java` 一处冲突同属风格差异（`seaAreaExtraCollectVO`/`ExtrafishVOs` 取值），保留 my_dev 侧空指针防护写法。
- `MiuixMainActivity.kt`、`PermissionUtil.java` 的冲突是 upstream 把电量权限申请路径回退到了 2026-09-15 已经定位修复的旧版本（独立 App 进程直接构造 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 跳转、无异常兜底；`checkBatteryPermissions()` 无参版本内部会碰 `ApplicationHook` 导致独立进程 `NoClassDefFoundError` 崩溃，见上方硬性规则第 4 条背景）。保留 my_dev 侧 `checkOrRequestBatteryPermissions(context)` 全套异常兜底 + `openBatterySettings` 回退。

三项必查：合并未引入新的时间/日历逻辑；JSON 创建统一改为 `MyUtils.newJSONObject`；JSON 读取统一改为 `opt*()` + 判空，包括自动合并未标冲突但违规的代码。

验证：`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过；现有 8 套本地回归检查（`check_account_switch`、`check_gr_followups`、`check_log_follow`、`check_manifest_permissions`、`check_merge_config`、`check_reward_cooldown`、`check_rpc_guard`、`check_standalone_no_xposed_class`）全部通过。未真机验证，未打包，未推送。commit `8eb74ad2`。

### 2026-09-16（续）：自动切号支持轮内15秒快速切换与整轮冷却机制

原切号机制将每次切号间隔硬限制为最低 2 小时，导致多账号轮询时每个账号执行完后都要在当前账号空闲硬等 2 小时才切换下一个账号，体感完全没有切号效果。

改造切号状态机与控制器，实现“开启账号即为首个账号、轮内账号间隔15秒切换、一轮结束冷却2小时、手动关闭清空冷却”：
1. **首个账号锚点（`roundStartAccount`）**：在哪个账号打开开关（或 `activation` 递增），以当前登录的 UID 作为本轮起始账号。
2. **轮内15秒切换与整轮冷却分离**：
   - 目标账号非首个账号时（`next != roundStartAccount`），属于轮内切换，账号任务执行完成空闲后仅等待 **15 秒**即切换到下一个账号；
   - 目标账号为首个账号时（`next == roundStartAccount`），代表本轮全部账号已遍历完毕，进入**整轮冷却**（默认 7200 秒 / 2 小时，允许配置 15–86400 秒）。
3. **关闭开关即清空冷却**：手动关闭开关时，清空 `roundStartAccount`、`idleSince` 及全部计时状态，冷却立即解除；再次开启时重新以当前所在账号作为新一轮循环起点。
4. **状态与空闲判断修正**：任务正在执行中（`!idle`）不提前预跑空闲时间，状态显示为 `WAIT_TASKS`（等待当前业务任务结束）；任务完成后细分展示 `COUNTDOWN`（本账号任务已完成，等待切换下一个账号（15秒））与 `ROUND_COOLDOWN`（本轮全部账号已完成，正在整轮冷却）。

三项必查：本次未新增时区计算，沿用单调时钟与毫秒计时；无裸 JSON get；设置保存继续沿用现有原子文件与安全解析。
验证：新增 `checks/check_account_switch.py`，完整覆盖起始锚点、轮内 15 秒切换、整轮 2 小时冷却、关闭清空状态、状态文案与输入限制；十项本地回归与 Java/Kotlin 编译全部通过。

**复核**：对照用户诉求（开哪个号即首轮起点、轮内15秒、整轮2小时冷却、手动关闭立即清空冷却）逐项核对代码与 `checks/check_account_switch.py`，均一致，跑测试确认 PASS。唯一发现 `AccountSwitchState.waitPhase()` 里一处 if/else 两分支返回值完全相同（`roundEnd ? "ROUND_COOLDOWN" : "COUNTDOWN"`），属死代码非功能性 bug；已删除冗余分支与未使用的 `required` 变量，重新编译该文件替换类并重跑 `checks/check_account_switch.py` 仍 PASS，行为不变。commit `47f099f3`。

### 2026-09-16（续）：根据每日异常报告补齐庄园领奖繁忙退避

报告共 10 次失败、9 类记录，集中在约一分钟内。三个庄园抽奖领奖任务 `cclyx_3bei_xjcmx_2`、`cclyx_sgbhsd_1c_zm3c`、`IP_chouchoule_juankuan` 返回 `102` 和“服务器正在开小差”。对照 M/GR 调用及 GR MyUtils，未找到这三个 ID 的明确失效规则；单次繁忙也不足以永久拉黑。

在新旧 RPC 桥共用的 RpcRequestGuard 中，仅将 `com.alipay.antfarm.receiveFarmTaskAward` 的 `102` 且提示以“服务器正在开小差”开头的失败接入既有系统繁忙退避：前两次各 5 分钟，连续第三次起 30 分钟，到期允许重试。复用接口、场景、任务 ID 和账号隔离，不暂停整个庄园，不新增永久黑名单。错误码读取同时修正 `error=0` 遮蔽失败 `resultCode` 的边界，真实非零 error 仍优先。

暂不处理：浇水次数上限已有业务处理；`GYG-huangjinxiaoji` 的 `331` 是饲料槽已满，不代表任务失效；两条 `48` 网络错误已有退避；租赁能量 `generateEnergy` 的“系统异常”和 `KUAIDI_VITALITY` 的无原因失败证据不足，等待后续日报，不据此停用任务。

三项必查：本次未新增日历或日期计算，冷却沿用毫秒时长；生产代码 JSON 创建仍用 MyUtils；错误码读取使用 optString，无裸 JSON get。回归补充三个实际 ID 的冷却、到期恢复、账号/场景/任务隔离、其他接口和普通业务错误不误伤，以及 error=0 回退。未真机验证、未打包、未提交、未推送。

验证：新增场景修复前失败、修复后通过；九项本地回归及 Java/Kotlin 编译全部通过。首次并行检查遇到 JVM 内存不足、配置检查所依赖的编译产物正在更新，待编译完成并限制测试 JVM 堆大小后，两项重跑通过。`git diff --check` 通过。

### 2026-09-16（续）：电量权限申请统一回退到支付宝应用设置

用户反馈魅族 20 PRO 点击电量权限申请完全无跳转、无提示，暂无 Flyme 版本和设备堆栈，不能确认系统静默拦截的具体原因。源码确认旧回退错误：标准申请失败后再次使用 ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，却移除了必需的 package URI；异常仅写日志，界面没有反馈。

修复：按用户要求不区分手机品牌，所有手机先走标准申请，启动异常时统一回退到支付宝应用详情并显示操作提示；应用详情也打不开时提示去系统设置手动操作。系统静默吞掉跳转且不抛异常时无法自动判定，使用手动入口直接打开应用详情。权限开关下新增“手动设置支付宝后台权限”，供标准跳转静默无效果或仍需厂商后台授权时使用。始终针对支付宝包名；系统服务不可用时不再误报已授权，启动设置页不等于权限已授予。独立 App 路径继续只使用传入 Context，不调用 ApplicationHook。

验证：扩展独立 App 回归，执行生产方法验证统一应用详情回退、手动入口、已授权短路、服务缺失、目标包名及提示；此前九项回归通过；按用户意见简化回退后，电量权限回归与 Java/Kotlin 编译再次通过。三项必查：本次无时间及 JSON 处理变更。未在魅族真机验证、未打包、未提交。

### 2026-09-16（续）：新增按账号每日异常请求统计文件

在新旧 RPC 桥共用的 `RpcRequestGuard.record()` 接入 `RpcFailureJournal`，在冷却和核心任务豁免返回之前记录真实失败，包含庄园 `resultCode=102` 等未触发退避的失败。成功响应及 `RPC_SKIPPED` 不计数，不修改现有退避、不自动新增黑名单。请求发出时捕获账号日志目录，避免迟到响应记入切换后的账号。统计独立于运行/异常日志显示开关，从新版运行后开始采集，不回填旧日志。

每账号每天生成 `log/<账号>/rpc-failures.yyyy-MM-dd.<账号>.json`，按接口、任务定位字段、各错误码及提示合并，记录次数与首次/最近时间。只保留 sceneCode、taskSceneCode、taskId、taskType、bizKey、recordId、activityId 等白名单字段，不保存完整参数/响应；常见 UID 在字段文本中遮蔽，文件名保留账号标识便于区分。复用 `AtomicConfigFile` 原子替换写入，重启后继续累计；每天最多 1000 类错误，额外未列出的错误计入 `unlistedFailureCount`。I/O 失败只写提示，不中断 RPC 处理。

“查看异常日志”页新增“导出统计”，导出当前账号当天报告到 `Download/sesame-M/`；原始逐条日志导出仍保留。用户每天提供该 JSON，后续按任务 ID 人工确认跳过规则。历史日期报告可从账号日志目录取出。

三项检查：报告日期与时间显式 GMT+8；JSONObject 解析使用 MyUtils；字段读取用 opt 并判空，JSONArray 输入解析保留异常防护。九项回归及 Java/Kotlin 编译通过，新回归覆盖聚合、任务区分、隐去非定位参数、成功/跳过排除、跨天、账号归属及写入失败隔离。Windows 测试替换 rename 操作以模拟 Android 已有原子覆盖语义。未真机验证、未打包、未提交。

### 2026-09-16（续）：日志卡片支持长按自由选择复制

共用 `LogEntryCard` 使用 Compose 原生 `SelectionContainer` 包含标签、时间及正文，全部七类日志支持在单条卡片内长按、拖动选择范围并复制，无新增依赖。九项本地回归和 Java/Kotlin 编译通过，未做真机手势验证，未打包、未提交。本次没有时间或 JSON 代码变更。

同时核对用户截图：前三条完整错误均为 `com.alipay.antfarm.receiveFarmTaskAward`，三个不同 taskId 的抽奖次数奖励请求返回 `success=false`、`resultCode=102`，memo 为“服务器正在开小差，请稍后再试”。当前 guard 将此视为未分类的庄园失败而不暂停；并非 RPC_SKIPPED，也不是截图证明同一个 taskId 重试三次。截图底部果园响应不完整，不能推断同一错误码。本次仅定位，未更改 RPC 退避。

### 2026-09-16（续）：将合并三项必查写入 AI 必读规则

`AGENTS.md` 明确每次合并必须核对 GMT+8、MyUtils JSON 创建、`.opt*()` 读取及判空，覆盖自动合并文件；保留严格解析失败语义和协议时间语义，并要求记录处理结果、例外及遗留项。同步更新本页长期约束，纠正 TimeUtil 仍未统一时区的过时描述。本次仅修改文档，未宣称已修复全部历史代码问题；检查 `git diff --check`，不运行代码回归。

### 2026-09-16（续）：合并 MIUIX-api102 至 ea6dd5e8

从干净的 `my_dev`（408fdd33）执行 merge，合入 `92a0d6b2`、`f38a48ff`、`cf4c1247`、`ea6dd5e8` 四个提交。包含选择弹窗固定标题/按钮和搜索过滤、减少重复配置加载/统计刷新，以及 Xposed 模块仓库发布时按原始提交时间创建附注标签的工作流调整。

冲突按分叉点 `0651e79a` 核对：主页保留 my_dev 的账号标题和 LaunchedEffect；首次文件权限申请保留，授权后统计刷新交给上游 Activity 路径，避免组合阶段重复加载。整数配置保留 my_dev 已有的 `configValue.toIntOrNull()` 与 `setConfigValue()`，覆盖上游同目的单位修复并保留安全解析。BaseActivity 删除重复 `AppConfig.load()`，配置仍在 `attachBaseContext` 调用的 `LanguageUtil.setLocal()` 中加载。

审查修复上游选择弹窗的单选回归：选中新的单选项时替换整个选择集合，多选仍追加；保存只输出已选项的数量。给现有配置回归补充对应源码约束。未认证账号修复、任务清理、本轮结束提示等原有提交保持不变。

验证：九项本地回归、Java/Kotlin 编译通过。未改构建/签名配置，未打包；发布工作流未在 GitHub 执行，未推送远端，未做真机界面验证。

### 2026-09-16（续）：本轮结束日志不再等待未来定时任务

“🏁全部任务已执行完成”原先要求子任务队列完全清空，森林蹲点等未来任务会一直挡住提示。完成监听改为等待生命周期中的运行任务及已到期的未取消子任务；未来定时任务不阻挡本轮结束，也不会被删除或取消。保留原日志文本、运行日志开关、每轮去重、停止和账号世代失效保护。

核对自动切号：`AccountSwitchController.tick()` 使用 `ModelTask.isAllTaskIdle()` 和 `TaskLifecycle.freezeIfIdle()`，原本就只检查已准入的运行任务，不依赖完成日志或子任务队列清空；此次漏打印不会直接造成自动切号失效，切号逻辑不变。

验证：完成日志回归加入一小时后的定时任务，修复前监听无法结束，修复后通过，并确认定时任务仍保留。九项本地回归及 Java/Kotlin 编译通过，覆盖立即待执行/运行中子任务、重叠轮次、停止及切号隔离。未做真机验证，未打包。

### 2026-09-16（续）：删除信用2101/视频红包，黄金票与青春特权入口去重

用户确认“2021”指信用2101，仅删除该任务、配置项和专用 RPC，保留“其他任务”里的好家无忧卡及其请求保护。删除视频红包完整模块、注册、VideoPageObserver/PageSubmissionProbe 和专用 `sesame-page-state.js`；提前调度仍被账号切换使用，保留。原视频回归中的共享福利冷却检查移到 `checks/check_reward_cooldown.py`，删除视频专用检查，同步更新 `AGENTS.md` 的检查命令。

对照本地 `E:\Work\Gr\Sesame-GR2026`：黄金票原在会员，但 GR/M 的 `goldTicket()` 已只剩注释，是失效空入口。删除 M 的旧开关、空方法、旧收取方法和无调用的黄金票 RPC，保留有实际实现的 `WeeklyWelfare`，将其分组由“其他”改为“会员”。类名、字段、查询间隔、当日尝试及冷却键不变，原每周福利配置继续有效；不把旧失效开关自动转换为真实领奖授权。

青春特权按 GR 保留森林 `youthPrivilege` 道具入口，删除独立 `YouthPrivilege` 签到模块及现金活动只读查询。GR/M 森林中的 `studentSignInRedEnvelope()` 调用本来就是注释，本次没有额外开启，所以保留的森林入口仅领取道具，不包含自动签到。用户旧配置文件不作主动清除，已移除模块不再注册或执行。

验证：九项本地回归（视频检查替换为共享福利冷却检查）及 Java/Kotlin 编译全部通过，`git diff --check` 通过。源码检查未发现信用2101、视频红包或独立青春特权残留调用；未做真机验证，未打包、未提交。

### 2026-09-16（续）：RPC guard 空错误码回退修复

审查确认 `record()` 的 `optString("error", resultCode)` 在 `error` 显式为空串时不回退，导致 `resultCode` 中的风控/系统错误等代码丢失。改为先读 `error`，为空再读 `resultCode`，非空 `error` 保持优先。未更改退避档位；guard 原本没有单独处理 `BUSINESS_REJECTED`，不能将这一策略缺口误称为空码回退已经解决。

`checks/check_rpc_guard.py` 增加普通/核心请求中 error 缺失、空串、JSON null 的状态一致性检查，覆盖 `1009`、`SYSTEM_ERROR`、`3000`、`48`、`2000`、`RPC_SKIPPED`，并检查非空 error 优先级与暂停到期。修复前在空串 + 1009 场景按预期失败。上一条关于会员冷却与主调度重登的修复仍保留在当前未提交工作区，并非仅修改底层 guard。

验证：修复后九项本地回归及 Java/Kotlin 编译全部通过，未做设备验证。

### 2026-09-16：修复会员业务拒绝/冷却被误判为全局掉线、反复拉起登录页

**反馈**：未开启自动切号，未认证小号仍反复提示“执行失败：检查超时”，并出现卡死/关闭 App；GR、AG 无同样表现。尚无设备日志，不能据此确认真实响应码或 ANR/崩溃原因。

**代码根因与 fork 对照**：`MAIN_TASK` 每轮都调用会员 `queryPointCert` 做全局检查，与自动切号开关无关。GR 的新 RPC 桥只要响应包含 `success`/`isSuccess` 就不置 `hasError`，M 在 `bf3d67cb` 引入 `RpcRequestGuard.isFailure` 后，`success:false` 的业务拒绝也会置错；但 `AntMemberRpcCall.check()` 仍用 `!hasError` 判断能否运行，导致会员资格/认证等业务失败中断全部任务。累计失败触发的 `RPC_SKIPPED` 也继续被当成检查失败。主分发将所有 false 都写成“检查超时”，随后无条件 `reLogin()`；该方法拉起登录 Activity，恢复回调又 `finish()`，形成反复打开/关闭页面的路径。AG 当前 `runMainTaskLogic()` 核对 UID 后分发任务，没有这条会员接口前置检查。

**修复**：保留现有前置检查，但区分 RPC 错误与有效的业务拒绝；会员接口本地冷却只限制会员请求，不阻断其它模块。无响应、错误 JSON、真实 RPC 错误或已离线仍不通过。检查不通过/异常/真实超时只按执行间隔重试，不再据此强制打开登录页；RPC 层对明确登录过期的处理及“超时重启”开关不变。分开记录中断与超时，保留线程中断标记，并在退出检查时取消 FutureTask，避免可中断的超时检查线程继续持有账号生命周期准入。

**回归**：扩展 `checks/check_rpc_guard.py`，通过生产新旧 RPC 桥、guard 和实际 `check()` 方法构造业务拒绝与连续三次失败后的冷却，旧实现按预期失败、修复后通过（`NOT_CERTIFIED` 仅是模拟测试码，非设备抓包结果）。抽取生产主分发检查代码验证普通失败/异常/超时不会拉起 Activity、超时取消并释放准入、中断不重试。原八套本地检查及独立 App 无 Xposed 引用检查均通过；`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过（仅既有弃用/unchecked 警告）。另验证真实 `error=2000` 仍标记离线，重登广播遵守“超时重启”开关。未做支付宝实机回归，实际卡死/闪退仍需设备日志验证。

### 2026-09-15（续）：修复森林/庄园/金豆/其他日志仍按时间正序显示

**根因**：四类分类日志在 `Log.java` 中写成 `HH:mm:ss.SSS 正文`，而 `MiuixLogViewerActivity.loadLogEntries()` 只识别带 `TAG:` 的格式。分类日志因此被当成续行合并进同一卡片，列表的 `asReversed()` 无法反转卡片内部的记录；运行/异常/抓包日志带标签，所以不受影响。此前仅检查列表反转便判断七类都倒序，遗漏了写入格式与解析格式的差异。

**修复**：共用解析器将标签前缀改为可选，无标签时保留 `null`，由已有卡片标题回退为「日志」。旧文件也能直接按时间戳拆分，继续用现有倒序列表展示；真正无时间戳的续行仍合并到上一条。不改日志写入格式。

**验证**：`checks/check_log_follow.py` 新增四类无标签日志的两条记录及续行场景，修复前因记录未拆分失败，修复后通过；原有带标签日志、500 条上限、实时刷新和滚动跟随检查保持通过。其余七项本地回归检查全部通过，`:app:compileNormalDebugJavaWithJavac :app:compileNormalDebugKotlin` 编译通过。未安装到真机验证。

### 2026-09-15（续）：电量权限崩溃真根因 + 深色模式/跟随系统开关体验修复

**电量权限崩溃排查了两轮**：第一轮看到崩溃堆栈里全是短名字的类（`bb1`/`nx0`/`p9`/`ip`/`v31`/`ff0`/`xo`/`n5`），误判成是支付宝那边被 R8 混淆坏了引用，把 `hook.**` 包从只精细 keep `ApplicationHook` 一个类改成整包 `-keep`（commit `56cc9a38`）——事后验证 `mapping.txt` 确认 `hook` 包一直就没被重命名过，这次"修复"方向从一开始就错了，重装后同样崩溃复现证明了这点。第二轮才找对地方：这个崩溃堆栈其实全程发生在**独立 App 自己的进程**里（那些短名字是本 App 自己被 R8 混淆后的 Compose 内部调用链，不是支付宝的类），根因是 `PermissionUtil.checkBatteryPermissions()`（无参版本）内部调用了 `ApplicationHook.isHooked()`/`getContext()`——已经写进上面「硬性规则」第 3 条。修法（commit `1f6ec478`）：给 `checkBatteryPermissions` 加一个接收 `Context` 的重载，独立 App 这条路径（`checkOrRequestBatteryPermissions` 已经有调用方传入的 `Context`）直接用新重载，完全不碰 `ApplicationHook`；原来的无参版本保留给唯一另一处调用方（`ApplicationHook.java` 内部、真实注入进程里的调用），内部改成先拿 `ApplicationHook.getContext()` 再委托给新重载，行为不变。新增 `checks/check_standalone_no_xposed_class.py` 静态守住这条路径不会再引用 `ApplicationHook`。

**深色模式/跟随系统设置体验问题**（用户连续反馈了三轮，逐层修）：
1. `c7aa63f3`：`MiuixBaseActivity.setAppContent()` 判断主题时 `followSystem`（默认开）优先级比 `darkMode` 高，单独点"深色模式"开关不会有任何效果，只会 `activity.recreate()` 一次让页面闪一下——改成点深色模式时如果跟随系统还开着就顺手关掉。
2. `4eed8650`：上一条只处理了单向联动，补上反方向——开"跟随系统设置"时也顺手关掉"深色模式"，两个开关做成真正的双向互斥，开关显示状态跟实际生效的优先级逻辑保持一致。
3. `dc009afe`：即使联动关系理顺了，仍然存在"最终效果没变也无条件重建"的情况（比如系统当前就是浅色、跟随系统开着，这时候切换不会改变任何视觉效果）。加了 `effectiveDark(follow, dark)` 帮助函数，用 `isSystemInDarkTheme()` 拿系统当前深浅色，切换前后各算一次最终生效的深浅色，只有真的变了才 `activity.recreate()`，两次算出来一样就跳过。

### 2026-09-15：MIUIX-api102 三轮合并 + 账号切换/日志/RPC 一批修复

一整天里 `MIUIX-api102`（原分支/PR 主线）陆续推了新提交，`my_dev` 分三轮 `git merge` 合入，中间穿插自己在这条线上做的功能和 bug 修复。commit 顺序（旧→新）：`2e350ce6` → `81236b9e` → `5268cfcf`（merge）→ `e1a42342`（merge）→ `bf3d67cb` → `ae2b620c` → `92ec28e6` → `3acad3db`（merge）→ `c12a7be2`。

**合并冲突的通用处理原则**：每次冲突都先查 `git show <merge-base commit>` 确认双方各自改了什么，而不是直接二选一。实测下来几乎所有冲突都属于两类：(a) `my_dev` 在 base 之后新加的功能/修复，upstream 那一侧其实是分叉前就没变过的旧代码，纯属改动位置相邻导致的假冲突——直接取 `my_dev` 侧；(b) upstream 做的是真清理（删除死代码/未使用资源），先用 `grep` 确认 `my_dev` 这边也真的没人再用，确认了才跟着删，不盲目信任任何一侧。

**第一轮合并**（`5268cfcf`，合入 upstream `bded0848`"项目清理与优化"+`335047d8`"优化配置及R8压缩启用"）：
- upstream 删除了水印原生库（`libsesame.so`+JNI 相关）、`AntInsurance`/`AntInsuranceRpcCall`（已废弃保险模块）、一大批旧 Compose 迁移前的 UI 资源（drawable、多语言 strings，从未被新 UI 引用）——确认无引用后全部跟进删除。
- `BaseModel` 的 `batteryPerm`（后台运行权限）、`recordLog`（记录日志）两个 `ModelField` 被 upstream 删掉；确认这两个字段在 `my_dev` 这边也早就是死代码（`batteryPerm` 的真实读取点是 `AppConfig.INSTANCE.getBatteryPerm()`，跟 `BaseModel` 这份重复申报的字段完全没关系；`recordLog` 全仓库没有任何 getter 调用点）——跟进删除，`closeCaptchaDialogVPN` 字段是这次会话自己加的真实功能，保留。
- `app/build.gradle` 的 ABI 拆分（只出 arm64-v8a）、`proguard-rules.pro`（精细化 `allowoptimization,allowobfuscation` 版本）两处冲突，保留 `my_dev` 已经用 `assembleNormalRelease` 实测能正常出签名包的版本，没有换成 upstream 那版更粗放的"整包 keep"写法。
- `AppConfig.java` 的 getter 冲突是双方各自新增了不同配置项（`closeVerification`/`closeErrorFunction`/`closeUnRpc` vs `batteryPerm`/`setBatteryPerm`），两边都保留。

**第二轮合并**（`e1a42342`，合入 upstream `ad353056`"账号切换延迟+日志查看器优化(PR#2)"+`c3bf75da`"R8精简及代码清理"）：
- `MiuixLogViewerActivity.kt` upstream 独立实现了一套几乎同功能的日志跟随机制（`revision` 计数器+`isScrollInProgress` 判断+前台 `Lifecycle` 门控+`stamp` 轮询），保留 `my_dev` 这边已经用 `checks/check_log_follow.py` 验证过的 `FileObserver` 事件驱动版本（upstream 版本有个潜在时序缝隙：手势进行中新数据到达时 `browsingHistory` 取的是滞后值）。
- **自动合并里揪出的真回归**：`ApplicationHook.java` 里账号切换那段，upstream 把 `initHandler(true)` 包了一层 `postDelayed(1000ms)`，但这层延迟跑在原来 `TaskLifecycle.Work` 的 try-with-resources 作用域**外**——包着它的 work 在方法 `return` 时就关掉了，1 秒后真正执行时完全脱离本次会话加的账号切换并发保护。补了同代际校验（复用 `execDelayedHandler` 已有的 generation 模式），在回调里重新 `TaskLifecycle.enter(switchGeneration)`。
- `proguard-rules.pro` upstream 又重写了一版（仍是粗放"章节化 keep everything"风格），继续沿用 `my_dev` 精细化版本。
- `AntMember.getWuaByReflection()`（25 行反射调 `AntOrchard` 私有方法）被 upstream 删掉改成 `new AntOrchard().getWua()` 直接调用，配合 `AntOrchard.getWua()` 私有改 public——干净的质量改进，跟进。
- 合并产生了 `import kotlinx.coroutines.{Dispatchers,delay,withContext}` 重复引入（一份来自 `my_dev` 原有位置、一份来自 upstream 新增位置），顺手去重。

**账号切换权限与配置迁移**（`bf3d67cb`，upstream 侧改动，会话内 review 后确认正确性）：
- `AppConfig.batteryPerm` 默认值从 `true` 改 `null`（区分"未设置"和"显式关闭"）后，`ApplicationHook` 里原来 `if (AppConfig.INSTANCE.getBatteryPerm() && ...)` 对没碰过这个新开关的用户会直接 `Boolean` 拆箱空指针崩溃。新增 `AppConfig.shouldRequestBatteryPermission()`：新开关设过就用新值，没设过回退读旧账号 `BaseModel.batteryPerm` 字段在 `config_v2.json` 里的历史值（哪怕这个 Java 字段本身已经在第一轮合并里删掉，JSON 里的旧值还在），UI 显示和实际权限检测两处都切过去了。
- `ConfigV2` 保存配置时，`BaseModel` 不再声明 `batteryPerm` 字段这件事本身会导致旧账号 JSON 里的历史值在下次保存时被静默丢弃——加了段兼容逻辑，没被当前模型声明的字段原样保留在保存结果里，配合上一条的读取兜底，不会丢用户历史选择。
- `AntOrchard` 农场施肥从"全局统一次数"字段改成 `SelectAndCountModelField`（每个场景各自配次数），`ConfigV2` 配了旧数据（纯场景名列表+全局次数）到新数据（场景→次数 Map）的迁移代码。
- `MiuixSettingsActivity.kt` 整数配置编辑框原来用 `field.value`/`setObjectValue()` 直接读写内部值，绕过了 `toConfigValue`/`fromConfigValue` 转换层——`IntegerModelField.MultiplyIntegerModelField`（比如执行间隔，界面显示分钟、内部按毫秒存）这类字段被这么改过一次后保存值就是错的。换成 `field.configValue`/`setConfigValue(string)` 走完整的字符串往返路径。
- 偷榜/霸榜"提前分钟数=0"原来等于开关虽开但永远不触发（数学上 `startTime==targetTime` 区间必空），是个不易察觉的死区；改成 0=全天霸榜 / 0=20:00 准时偷榜，范围上限同步从 240 放宽到 1200 分钟。
- 新增 `RpcRequestGuard`：按账号隔离的请求去重 key（剔除随机 `outBizNo`/时间戳等噪音字段）+ 分级退避（森林/庄园"核心"请求更宽松，其它请求更严格）+ GR 快照里已知异常任务的硬黑名单，新旧两套 `RpcBridge` 统一接入。顺带修了三个既有并发 bug：
  1. `RpcEntity.setResponseObject` 不清 `hasError`，重试第二次成功后仍会读到第一次失败留下的标记；
  2. `NewRpcBridge.newAsyncRequest` 用单次 `wait(30_000)` 判断回调，`Object.wait` 超时返回不等于条件真的满足（虚假唤醒），改成 `while(!hasResult) wait(remaining)` 真正的条件循环；
  3. 任务取消触发的 `InterruptedException` 原来统一走 `catch(Throwable)`，会被当成一次 RPC 失败记录进新加的 guard 里，还会继续 `sleep` 重试而不及时退出——单独识别中断直接 `return`，这条直接关系到本次会话早前加的 `TaskLifecycle` 取消语义会不会被这层新代码破坏。
  4. `newAsyncRequest` 之前一直没调 `RpcIntervalLimit.enterIntervalLimit`，同步版本有限流、异步版本没有，这次补齐一致。
- `AntForestV2` 收能量逻辑加了对 guard 跳过时返回的 `RPC_SKIPPED` 哨兵值的识别，避免把"没真的发请求"误判成服务器返回的真实错误去跑等待/拉黑逻辑。
- 广告类 RPC（`com.alipay.adexchange.*`）的调试日志改用 `RpcLog` 摘要+脱敏，不再整包 payload（可能几十 KB、含 session 信息）落盘。
- 日志页从"最新在底部"（`reverseLayout` + 滚到底部）改成"最新在顶部"（不反转布局，`entries` 本身倒序），是用户明确要求的调整，不是 bug。

**日志昵称显示 + 小鸡睡觉 + 非好友噪音**（`ae2b620c`）：
- `MyUtils.recordUserName()` 的内存缓存 `mUidMap` 是个自己写自己读的死代码：只有已经命中缓存才会顺手回写 `SharedPreferences`，但从来没人真正往缓存里塞过昵称，导致运行日志"开始执行:xxx"这类位置的 xxx 从建仓库以来就一直是裸账号 UID，从没显示过好友昵称。改法：`UserIdMap.add()`（账号信息任何来源被加载/更新时都会走这里）顺手把昵称写进 `SharedPreferences` 持久缓存；`recordUserName()` 优先取内存里最新的 `UserIdMap` 记录，没有再退化到持久缓存，都没有才落回裸 UID。空昵称更新不覆盖已缓存的好名字，相同昵称不重复写盘。
- 小鸡"自动睡觉"整个功能删掉（对应支付宝接口已不可用，不是这次评估要不要保留的重点），只留自动起床，相关字段/日志文案改名去掉"睡觉"歧义。
- 访问已经解除好友关系的庄园时，`enterFarm` 返回 `memo=非好友,resultCode=302`——这个情况每天必然稳定复现且没法修，之前每次运行都当成普通错误刷一条 error 日志，纯噪音。新增 `RpcRequestGuard.isNonFriend()` 识别后静默跳过，不影响后续对其它好友的正常赠送流程，其它失败原因不受影响仍照常记录。

**第三轮合并**（`3acad3db`，合入 upstream `0651e79a`"修正 IntegerModelField 范围限制"）：
- `advanceTime` 下限从 `Integer.MIN_VALUE` 改 0、`ornamentsDressUpDays` 加 1-30 天范围、`closeShopTime` 加 1-1440 分钟范围——三处直接干净合并，默认值不变。
- `AntOrchard.java` 一处冲突：upstream 想把 `orchardSpreadManureCount`（单一全局施肥次数字段）上限从 100 放宽到 200，但这个字段在第二轮合并涉及的功能里已经被拆成按场景各自配次数的 `SelectAndCountModelField`，字段本身不存在了——保留 `my_dev` 侧结构。upstream"上限放宽到 200"这个诉求目前没有对应落点：每场景次数用的是 `MiuixSettingsActivity.kt` 里 `SelectionDialog` 通用组件的滑杆，写死 `0..100f`，这个滑杆是 `rpcRequestList` 等好几个字段共用的，不是 orchard 专属，没有顺带改，只在这里记一笔，以后有需要再单独给 `SelectAndCountModelField` 加个可配置上限参数。

**账号切换电量权限崩溃 + UI 状态丢失**（`c12a7be2`）：
- `AndroidManifest.xml` 一直没声明 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`——设置页"立即申请权限"按钮跳系统电量优化豁免弹窗时直接崩溃，补上声明。
- 深色模式/跟随系统开关会调 `activity.recreate()` 重建 Activity，`MainScreen` 里的 `selectedTab` 用的是 `remember`（不跨重建存活），一重建就被清零，界面从当前 tab 跳回首页——改用 `rememberSaveable`。
- 顶栏"当前账号"信息原来是每个 tab 各自的 `TabTitleRow` 内部单独 `remember`+起一个轮询 `LaunchedEffect`，切 tab 相当于该 `TabTitleRow` 实例重新进入组合，状态从"未知账号"重新读起，读到真实昵称前会有一瞬间的闪烁。把轮询提到 `MainScreen` 一级共享一份状态，四个 tab 都吃同一份 `currentAccount`，不再各自归零重来。

**UI 显示格式微调**（会话内做了但截至写这条记录时还没提交，跟着下一次提交走）：
- 顶栏/配置列表账号显示格式从 `showName: account` 改成 `showName(account)`（如 `C176(17608062578)`）。
- 配置列表每个账号条目下面的 `UID: xxx` 那一行，从跟标题拼在一起的同号大小文字改成 `ArrowPreference` 自带的 `summary` 参数（小字副标题样式，跟"已选 N 项"那类提示一致）。

**新增的本地回归检查**（这一天累计新增 5 个，加上之前已有的凑成完整一套）：`checks/check_merge_config.py`（配置迁移/电量权限/分钟语义回归，编译产物直接验证）、`checks/check_rpc_guard.py`（编译生产 `RpcRequestGuard`/两套 `RpcBridge`/`RpcEntity` 代码，用字节码级源码替换把 `System.currentTimeMillis()` 换成可控时钟，隔离账号状态跑真实退避/黑名单/跨账号隔离场景）、`checks/check_gr_followups.py`（昵称缓存刷新链路+非好友静默跳过后不影响后续赠送）、`checks/check_manifest_permissions.py`（守住权限声明和实际调用点一致，防止重复踩 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 这类坑）。全部跟已有的 `checks/account_lifecycle`、`checks/check_log_follow.py`、`checks/check_video_rewards.py`、`checks/audit_regressions` 一起构成完整回归套件，每次改动后都跑齐这八套 + 编译 + `:app:assembleNormalRelease`（R8+签名）确认没有回归。

### 2026-09-14：修复 AG / GR / XU / 新版 GR 快照审查确认的 17 项问题

基于 `e28f3db4` 的只读审查后，按用户要求修复全部 17 项。以下记录补充并更正旧条目中关于“切号只靠 BUSY 足够”“恢复 boot 即可启用 VPN 弹窗开关”的结论；旧记录保留。

| 编号 | 问题 | 实际修复 |
|---|---|---|
| 1 | 自动切号仍可能与旧任务并发 | 新增共用 `TaskLifecycle` 准入计数，主分发、模型、正在执行的子任务及初始化均纳入；仅空闲时冻结，初始化结束后恢复，旧代延迟回调失效。取消请求不会提前释放仍在运行的任务。初始化失败保持冻结，避免在错误账号继续请求。 |
| 2 | 芝麻粒换豆回查失败漏记额度 | 服务端确认兑换成功后立即累计当日额度，再执行回查。 |
| 3 | 视频提速清掉拒绝冷却 | 公共福利基类拆分普通查询时间与服务端冷却；提速只清普通间隔。首次迁移保留旧共享键截止时间，版本变更不清服务端冷却。旧键不能区分两类等待，因此升级首次可能保留一次普通等待。 |
| 4 | Android 8～9 日志监听构造函数不兼容 | 改用支持最低 API 26 的路径字符串构造函数，Lint 的该项 `NewApi` 错误消除。 |
| 5 | 独立 App 日志账号错位 | 宿主原子发布当前日志 UID；读取端跟随该 UID，详情页每秒检查账号/日期变化后重新监听；目录名做边界校验。 |
| 6 | 账号子目录日志清理失效 | 清理遍历根目录历史日志及一层账号目录；今日超限和指定类别使用清空文件，保留正在写入的文件句柄。日志日期统一 GMT+8。 |
| 7 | VPN 弹窗 Hook 缺初始化 | `BaseModel.boot` 先调用 `CaptchaHook.setupHook(classLoader)`，再同步配置开关。 |
| 8 | 绿色金融重复请求第 0 页 | 处理当前页后判断末页；缺失、负数或不前进的游标停止分页，同时修复原先跳过末页好友的问题。 |
| 9 | 生态/古树捐赠超过配置总额 | 追加捐赠扣除历史累计量及本轮首捐；未知累计量不推定为零，配置缺失或首捐失败停止。差额使用 long 避免减法溢出。 |
| 10 | 金豆漏领奖、失败后整日不再重试 | 完成任务后继续领奖，失败保留未解决状态；取消整个模块的当日完成短路，各业务按服务端状态及独立额度去重。 |
| 11 | 单开视频预约不执行 | 预约开关加入任务查询前置条件。 |
| 12 | 视频提前调度不唤醒任务 | 清普通间隔后请求主调度，合并重复请求；正在执行分发或模型时延后，账号代数变化丢弃旧请求。 |
| 13 | 暂停/结束帧覆盖达标证据 | 仅合格播放快照替换缓存；暂停、未达阈值等无效帧不覆盖，读取仍校验新鲜度和账号。观察异步回调绑定原账号。 |
| 14 | 鱼塘签到日期使用设备时区 | 独立日期格式化器显式使用 GMT+8、Locale.ROOT。 |
| 15 | 留空的伪装版本没有进入 Hook | PackageInfo Hook 与展示统一读取已有默认版本 getter。 |
| 16 | Gemini 清洗改变小数/误选 | 保留回答原文与标点；优先精确匹配，多选项包含匹配时拒绝猜测。 |
| 17 | 运动币气泡检查错 JSON 层级 | 从每条气泡读取并校验 `assetId`，不再检查父对象。 |

回归检查使用生产类或抽取的实际方法，配合最小模拟依赖，不请求支付宝接口。运行命令：

```text
python checks/account_lifecycle/run.py
python checks/audit_regressions/run.py
python checks/check_video_rewards.py
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleNormalDebug :app:lintNormalDebug --console=plain
```

验证结果：以上三组回归检查全部通过；`account_lifecycle/run.py --baseline` 在旧生产实现上按预期复现 `queued MAIN_TASK escaped admission`，当前实现通过。最终独立 `:app:assembleNormalDebug` exit 0，生成 `app/build/outputs/apk/normal/debug/app-normal-arm64-v8a-debug.apk`。`git diff --check` 通过。

Lint 已重新运行，日志页 API 错误已消除；全库仍有 **2 errors / 286 warnings / 2 hints**，失败项是原有的 `MiuixMainActivity.kt:187` 广播注册缺少导出标志（`UnspecifiedRegisterReceiverFlag`）和 `values/strings.xml:144` 的 `module_description` 缺中文翻译（`MissingTranslation`），均不在本次 17 项中，未顺带修改。报告：`app/build/reports/lint-results-normalDebug.html`；本地命令日志：`build/audit-assemble-final.log`、`build/audit-verification-final.log`。未做支付宝实机回归；模拟检查不代表服务端业务已实测成功。本次修改尚未提交。

### 2026-09-14：`Privilege.java`/`FriendWatch.java` 收尾修复，全仓库 `.get*()` → `.opt*()` 转换任务完成

全仓库最终扫尾审计（`grep -rn` 排除注释行）额外发现两处此前遗漏的调用点：

- `Privilege.java`：`getForestTasks` 原来包一层 `try { return ....getJSONArray("forestTasksNew"); } catch (JSONException e) { ...; return null; }`，内部已经是 `optJSONArray`（返回 null 语义与 catch 分支等价），直接去掉整个 try/catch，改为一行直接 `return`。顺带清理了该文件里一段此前遗留的重复 import 块（`JSONArray`/`JSONObject`/`JSONException` 各多导入一次），因为 `JSONException` 不再使用一并删除。
- `FriendWatch.java`：`updateDay()` 里 `joFriendWatch.getJSONObject(id)` 改 `optJSONObject(id)`，加 `if (joSingle == null) { continue; }` 空指针防护。

之后做了一次全仓库地毯式复查：`grep -rn` 匹配 `.get(String|Int|Long|Double|Boolean|JSONObject|JSONArray)\(`，逐条人工分类剩余匹配，确认全部属于范围外（`android.os.Bundle`、`Context.getString(int)` 资源串、`SharedPreferences.getBoolean/getString`、项目自有 `RuntimeInfo.getLong(...)`）或纯注释/已确认的死代码块，**没有任何遗漏的可执行 `org.json.JSONObject`/`JSONArray` 裸 `.get*()` 调用**。

至此，本次跨会话的全仓库 `.get*()` → `.opt*()` + 空指针防护任务（原始统计约 1986 处调用点）全部完成，覆盖 AntFarm/AntForestV2/AntSports/AntStall/AntMember/AntOcean/AntOrchard/AntDodo/ProtectEcology/AntBookRead/OmegakoiTown/ForestChouChouLe/GreenFinance/AncientTree/AntInsurance/ExtensionsHandle 共 16 个主文件，以及 FishTask/ReadingDada/BaseTaskRpcCall/AntSportsRpcCall/AreaCode/MessageUtil/WhackMole/BaseModel/JsonUtil/RuntimeInfo/Privilege/FriendWatch 等零散小文件。编译通过（`compileNormalDebugJavaWithJavac` 全程 exit 0）；**全程仅做了编译期验证，未在设备上做过任何运行时测试**，合并/使用前建议实机走一遍关键流程（森林/庄园/保护地/新村文旅等常用任务）。

### 2026-09-14：`ExtensionsHandle.java` `.get*()` → `.opt*()` + 空指针防护，第十六个文件全部完成

第十六个文件（248 行，原~23 处调用点，一次会话内全部转完）。覆盖 `getNewTreeItems`/`queryTreeForExchange`/`getTreeItems`/`getTreeCurrentBudget`/`queryAreaTrees`/`getUnlockTreeItems`（新树上苗提醒、树苗余量查询、未解锁地区/项目提醒等扩展信息展示功能）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到剩余的小文件（10 处以下），逐个处理。

### 2026-09-14：`AntInsurance.java` `.get*()` → `.opt*()` + 空指针防护，第十五个文件全部完成

第十五个文件（137 行，原~25 处调用点，一次会话内全部转完）。覆盖 `gainSumInsured`（保障金领取，含遍历 JSON 动态字段用的 `jo.get(key)` 也一并改成 `jo.opt(key)`——这是本轮唯一一处不带类型后缀的裸 `get`，同样会在字段缺失时抛异常，判断属于同一类问题一并处理）/`gainMyAndFamilySumInsured`、`lotteryDraw`（天天领取保障福利）、`beanSignIn`/`beanExchange`（安心豆签到与兑换，含四层嵌套取值链 `result.rspContext.params.exchangeDetail`）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `ExtensionsHandle.java`（约 23 处调用点）。

### 2026-09-14：`AncientTree.java` `.get*()` → `.opt*()` + 空指针防护，第十四个文件全部完成

第十四个文件（146 行，原~26 处调用点，一次会话内全部转完）。覆盖 `ancientTreeProtect`/`districtDetail`（区划信息与古树列表遍历、保护动作前的能量与配额校验）。`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntInsurance.java`（约 25 处调用点）。

### 2026-09-14：`GreenFinance.java` `.get*()` → `.opt*()` + 空指针防护，第十三个文件全部完成

第十三个文件（474 行，原~32 处调用点，一次会话内全部转完）。覆盖 `run`（首页解析与分批收集）、`batchSelfCollect`/`signIn`/`doTick`（签到与打卡）、`donation`（快过期金币捐助，含 `mcaDonationProjectResult.[0]` 路径取值改用先取数组元素再传路径查询）、`prizes`（评级奖品）、`batchStealFriend`（收好友金币）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AncientTree.java`（约 26 处调用点）。

### 2026-09-14：`ForestChouChouLe.java` `.get*()` → `.opt*()` + 空指针防护，第十二个文件全部完成

第十二个文件（1209 行，原~33 处调用点，一次会话内全部转完）。覆盖 `chouChouLe`/`chouChouLescene`（森林抽抽乐任务列表遍历、已完成任务领奖、活力值兑换、统一完成任务分支、抽奖执行循环）、`shareComponentRecall`/`confirmShareRecall`（好友助力解析）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `GreenFinance.java`（约 32 处调用点）。

### 2026-09-14：`OmegakoiTown.java` `.get*()` → `.opt*()` + 空指针防护，第十一个文件全部完成

第十一个文件（190 行，小文件一次性全部转完）。覆盖 `getUserTasks`/`getSignInStatus`/`houseProduct`（小镇任务领取、每日签到、房屋收金三个流程）。`grep` 确认代码里已无可执行的裸 `.get*()`（`org.json` 相关）调用，唯一剩余匹配是 `RuntimeInfo.getInstance().getLong(...)`，与前一文件同理不在范围内。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `ForestChouChouLe.java`（约 33 处调用点）。

### 2026-09-14：`AntBookRead.java` `.get*()` → `.opt*()` + 空指针防护，第十个文件全部完成

第十个文件（220 行，原~30 余处调用点，小文件一次性全部转完）。覆盖 `queryTaskCenterPage`（听书阅读能量，含 `dynamicCardList[0].data.bookList` 这种多层嵌套取值链，每层加了 null 判断和空数组判断）、`queryTask`（任务列表，含 `READ_MULTISTAGE` 子任务遍历）、`collectTaskPrize`/`queryTreasureBox`（领奖与开宝箱）。

`grep` 确认代码里已无可执行的裸 `.get*()`（`org.json` 相关）调用，唯一剩余匹配是 `RuntimeInfo.getInstance().getLong(...)`——`RuntimeInfo` 是完全不同的一套 API，不是 `org.json.JSONObject`，不在本次任务范围内，故意不动。编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `OmegakoiTown.java`（约 34 处调用点）。

### 2026-09-14：`ProtectEcology.java` `.get*()` → `.opt*()` + 空指针防护，第九个文件全部完成

按调用点数量排序的第九个文件（778 行，原~98 处调用点）。覆盖 `initForest`/`initOcean`/`cooperateWater`/`queryCooperatePlant`/`cooperateWater(4参)`（森林/海洋项目列表同步、合种浇水）、`getEnergySummation`/`queryTreeItemsForExchange`/`queryTreeForExchange`/`exchangeTree`/`applyGoldAnimalCert`（树木兑换核心：证书数量与能量前置检查）、`protectCarbon`/`marathonQueryActivity`/`carbonQueryActivity`/`carbonCharityActivity`/`queryCultivationList`（碳中和马拉松/古树医生助力）、`protectReserveMinNum`/`protectBeachMinNum`/`protectBeach`/`queryCultivationDetail`/`oceanExchangeTree`（保护地/海滩最低数量保底兑换与海洋兑换收尾）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过（`BaseModel.java` 的 Lombok `@Getter` 命名冲突提示是本会话开始前就存在的良性 note，非本次改动引入，确认过 exit code 为 0）；仅做了编译期验证，未做设备/运行时测试。至此全库调用点数量排名前 9 的文件全部转换完毕，下一步按调用点数量排序转到 `AntBookRead.java`（约 43 处调用点）。

### 2026-09-14：`AntDodo.java` `.get*()` → `.opt*()` + 空指针防护，第八个文件全部完成

按调用点数量排序的第八个文件（921 行，原~103 处调用点）。覆盖 `initAntDodoTaskListMap`/`getEndDateTime`/`collect`/`collectAnimalCard`/`taskList`（神奇物种任务列表与每日抽卡）、`propList`/`usePropUniversalCard`/`queryUniversalAnimal`（道具自动使用与万能卡最优动物选择）、`consumeProp`（两个重载）/`collectToFriend`/`generateBookMedal`（消耗道具、帮好友抽卡、图鉴勋章合成）、`checkAnimalAndGiftToFriend`/`giftToFriend`（三个重载：入口/按 bookId 遍历/单张赠送，赠送稀有卡片给好友）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。至此全库调用点数量排名前 8 的文件全部转换完毕（AntFarm/AntForestV2/AntSports/AntStall/AntMember/AntOcean/AntOrchard/AntDodo），下一步按调用点数量排序转到 `ProtectEcology.java`（约 98 处调用点）。

### 2026-09-14：`AntOrchard.java` `.get*()` → `.opt*()` + 空指针防护，第七个文件全部完成

按调用点数量排序的第七个文件（1395 行，原~106 处调用点）。覆盖 `checkOrchardOpen`/`queryOptionalPlay`（农场乐园限定活动）/`initAntOrchardTaskListMap`、`initPlantScene`/`handleEnableScenes`/`handleTaobaoData`（场景与果树状态解析）、`doSpreadManure`/`canSpreadManure`（主场景+余额宝场景两条施肥前置检查）/`querySpreadManureActivity`、`orchardListTask`/`handleSignTask`/`handleTaskList`/`finishOrchardTask`（农场任务列表与签到）、`triggerTbTask`/`drawLotteryPlus`（七日礼包）/`extraInfoGet`（每日肥料包）、`querySubplotsActivity`/`handleWishActivity`/`handleCampTakeoverActivity`（许愿与营地接管子场景活动）/`queryYebRevenueDetail`（余额宝摇钱树收益）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。改的过程中发现一处局部变量名与内层 `optJSONObject("result")` 取的临时变量重名（`queryYebRevenueDetail` 里外层已有 `String result`），编译报错后重命名为 `resultObj` 解决，纯粹是命名冲突不是逻辑改动。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntDodo.java`（约 103 处调用点）。

### 2026-09-14：`AntOcean.java` `.get*()` → `.opt*()` + 空指针防护，第六个文件全部完成

按调用点数量排序的第六个文件（1570 行，原~108 处调用点）。覆盖 `queryOceanStatus`/`initAntOceanAntiepTaskListMap`（普通任务/摸鱼任务列表同步）、`queryHomePage`/`collectEnergy`/`cleanOcean`/`autocleanOcean`/`ipOpenSurprise`/`combineFish`/`checkReward`（能量收取与清理海域主流程）、`queryReplicaHome`/`unLockReplicaPhase`/`queryReplicaTaskList`/`receiveReplicaTaskAward`/`queryMiscInfo`（副本任务）、`querySeaAreaDetailList`/`openWAIT_FOR_UNLOCK`/`switchOceanChapter`/`queryUserRanking`（神秘海域拼图合成与章节切换）、`cleanFriendOcean`（两个重载）/`queryTaskList`（帮好友清理海域、日常任务）、`finishOceanTask`/`answerQuestion`/`exchangeUniversalPiece`（两个重载）/`useUniversalPiece`（两个重载）（答题任务、重复拼图兑换万能拼图、使用万能拼图迎回鱼类）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntOrchard.java`（约 106 处调用点）。

### 2026-09-14：`AntMember.java` `.get*()` → `.opt*()` + 空指针防护，第五个文件全部完成

按调用点数量排序的第五个文件（1483 行，原~131 处调用点）。覆盖 `initMemberTaskListMap`（会员任务/芝麻信用任务列表同步）、`memberSign`/`queryPointCert`（签到与积分领取）、`signPageTaskList`/`queryAllStatusTaskList`（做任务赚积分）、`promise`/`querySingleTemplate`（生活记录：构建加入请求体时对 `canSelectValues` 等四段规则数组做了长度校验，原代码假设数组必然非空，现在为空时直接返回 null 而不是让 `.getString(0)` 抛异常）/`promiseJoin`、`doBrowseTask`（两个重载：批量/单任务提交）、`goldBillCollect`/`batchReceivePointBall`/`dailySignIn`/`processTask`/`queryAndProcessTaskList`/`queryTaskList`（游戏中心任务体系）、`queryPointBallList`/`checkAndDoSignIn`/`memberPointExchangeBenefit`（玩乐豆签到与会员积分兑换）、`collectSesame`（芝麻信用收芝麻粒：领任务/完成任务/收 feedback 三段流程）、`RecommendTask`/`OrdinaryTask`（我的快递任务，顺带给 `taskMaterial` 可能为 null 的既有隐患加了守卫）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 1 处在注释里。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntOcean.java`（约 108 处调用点）。

### 2026-09-14：`AntStall.java` `.get*()` → `.opt*()` + 空指针防护，第四个文件全部完成

按调用点数量排序的第四个文件（1280 行，原~134 处调用点）。覆盖 `querySelfHome`/`selfHomeHandler`/`initAntStallTaskListMap`/`settleReceivable`（新村主页解析与结算入口）、`sendBack`（4参与 seatsMap 重载，请走小摊）/`inviteOpenShop`/`settle`（经营所得结算）、`closeShop`（两个重载）/`openShop`（两个重载）/`rankCoinDonate`/`friendHomeOpenShop`（收摊、摆摊、邀请好友摆摊全流程）、`taskList`/`doStallTask`（新村任务列表与各类型任务执行分支）、`signToday`/`receiveTaskAward`/`inviteRegister`/`shareP2P`/`assistFriend`（签到、领奖、邀请注册、人传人助力）、`projectList`/`projectDetail`/`projectDonate`/`canDonateToday`/`unlockNewVillage`/`canUnlockNewVillage`（公益捐赠与解锁下一村）、`collectManure`/`throwManure`（两个重载）/`pasteTicket`（两个重载）（收肥料、丢肥料反击、贴罚单）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用（全部转完，无残留注释死代码）。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntMember.java`（约 131 处调用点）。

### 2026-09-14：`AntSports.java` `.get*()` → `.opt*()` + 空指针防护，第三个文件全部完成

按调用点数量排序的第三个文件（2551 行，原~302 处调用点）。分多轮覆盖：`initSportsTaskListMap`/`sportsTasks`/`signInCoinTask`/`receiveCoinAsset`（运动任务与签到）、`getWalkPathMinCompleteCount`（世界地图/城市/路线三层嵌套查询）、`isNeedJoinNewPath`/`hasTreasureBox`/`walkGo`（两个重载）/`parseRewardsByJSONObjectData`（行走核心逻辑）、`queryWorldMap`/`queryCityPath`/`queryPath`/`openTreasureBox`/`receiveEvent`/`parseRewardsByJSONArrayRewards`/`queryGoingPathId`（路线查询与奖励解析）、`queryJoinPathId`/`checkJoinPathId`/`joinPath`（加入新路线）、`canDonateCharityCoinToday`/`queryProjectList`/`donate`（公益捐赠）、`canDonateWalkExchangeToday`/`queryWalkStep`（捐步做公益）、`userTaskGroupQuery`/`participate`/`userTaskRightsReceive`（文体中心日常任务/走路挑战赛）、`pathFeatureQuery`/`pathMapHomepage`/`tiyubizGo`（体育线路宝箱）、`queryClubHome`（抢好友大战：收能量球/购买好友/训练好友/蹲点训练四段逻辑）、`trainMember`/`queryMemberPriceRanking`/`queryClubMember`/`buyMember`（训练与抢购好友）、`coinExchangeItem`/`receiveSpecialPrize`/`signIn`/`receiveTaskReward`/`completeTask`（悦动健康任务体系）、`walkGrid`/`build`（能量泵前进/建造）、`receiveBrowseReward`/`receiveOfflineReward`/`parseRewards`/`receiveBubbleReward`（浏览/离线/气泡奖励）、`queryBaseInfoAndProcess`（能量泵主流程，普通岛+活动岛两条分支+活动岛奖励领取）、`queryAndProcessBubbleTasks`（气泡任务状态机）、`exchangeBenefits`（悦动健康权益商店兑换）、`canWalkGrid`/`canBuild`/`processSignIn`/`processTaskCenter`/`processBrowseTasks`/`queryUserEnergy`/`collectBubble`/`queryMapListSwitch`/`checkAuth`（收尾的能量泵前置检查、签到、任务中心、地图切换、权限检查）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 3 处在注释里（`//jo.getJSONObject(...)`/`//jo.getLong(...)`/`//if (TaskHelper...)`）。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到下一个文件（`AntMember.java`/`AntDodo.java`/`AntStall.java`/`AntOcean.java`/`AntOrchard.java`/`ProtectEcology.java` 等剩余约 20 个文件）。

### 2026-09-14：`AntForestV2.java` `.get*()` → `.opt*()` + 空指针防护，第二个文件全部完成

按调用点数量排序的第二个文件（4470 行，原~330 处调用点，全库最大单文件）。分多轮覆盖：`run()` 主流程能量球收取（浇水/复活/回赠金球、道具、动物偷能量）、`ForestEnergyInfo()` 日/周/总榜查询、`collectPKEnergy`、`querySelfHome`/`queryFriendHome`、`collectUserEnergy`（核心：能量罩/炸弹卡检测、气泡收取判断）、`collectFriendsEnergy`、`collectGiftBox`/`protectFriendEnergy`、`collectEnergy`（核心结果解析：`bubbles`/`bombCardEffect`）、`updateUsingPropsEndTime`/`collectRobExpandEnergy`、`queryForestEnergy`/`produceForestEnergy`/`harvestForestEnergy`（统一了 `jo.getJSONObject("data").getJSONObject("response")` 这种两层链式取值模式，改成两次 `optJSONObject` + null 短路）、`initAntForestTaskListMap` 活力值/抽抽乐任务列表同步、`greenRent`/`retrieveCurrentActivity`/`sendEnergyByAction`（森林集市/绿色租赁）、`popupTask`（过期能量签到）、`waterFriendEnergy`/`returnFriendWater`（好友浇水）、`vantiepSign`/`queryCommonSign`/`vitalitySign`（三种签到）、`doForsetTaskList`/`queryTaskList(firstTaskType)`/`doChildTask`（活力值任务领取）、`startEnergyRain`/`energyRain`/`checkAndDoEndGameTask`/`doforestgame`（能量雨与森林乐园宝箱）、`queryOptionalPlay`（乐园限定活动）、`continuousUseAndExchangeCard`/`continuousUseCardCheak`/`useRobExpandCardFactor`/`chooseContinuousLIMITTIMECard`（限时道具卡自动续期选卡）、`useDoubleCard`、`giveProp`（赠送道具）、`ecoLife`/`ecoLifeTick`/`photoGuangPan`（绿色行动/光盘打卡）、`queryUserPatrol`/`patrolKeepGoing`（巡护森林）、`queryAnimalPropList`/`consumeAnimalProp`/`queryAnimalAndPiece`/`combineAnimalPiece`（动物伙伴派遣与碎片合成）、`forFriendCollectEnergy`、`getForestPropVOList`/`getPropGroup`/`getForestPropVO`/`consumeProp`（背包道具查找，顺带移除了因转换后不可达的一处 `catch(JSONException)`）、`getAllSkuInfo`/`getSkuInfoBySpuId`/`getSkuInfoByItemInfoVO`/`exchangeBenefit`（活力值商店兑换）、`loveteam`/`loveteamWater`（合种浇水）、`updateUserConfigEnergyPvp`/`queryPvpHomeInfo`/`receivePvpRewards`（1V1 能量挑战）、`getDressDetail`/`checkDressDetail`/`queryUserDressForBackpack`（装扮保护）。

`grep` 确认代码里已无可执行的裸 `.get*()` 调用，仅剩 2 处在注释掉的死代码块内（`drawGameCenterAward` 里一段、`letsGetChickenFeedTogether` 注释块），故意不动。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步转到 `AntSports.java`（约 340 处调用点）。

### 2026-09-14：`AntFarm.java` `.get*()` → `.opt*()` + 空指针防护，第一个文件全部完成

接续上一条记录，本次把 `AntFarm.java` 剩余约一半（2213~4458 行）全部转完。覆盖 `notifyFriend`/`parseSyncAnimalStatusResponse`（关键：填充 `ownerFarmId`/`foodStock`/`animals[]` 等类字段，`animalJsonObject` 为 null 时该条 `animals[i]` 保留空壳对象跳过字段填充，不影响数组长度语义）、`collectDailyFoodMaterial`/`collectDailyLimitedFoodMaterial`/`cook`/`useFarmFood`/`drawLotteryPlus`/`visitFriend`/`acceptGift`/`queryChickenDiary(List)`/`visitAnimal`/`queryOptionalPlay`/`getAllSkuInfo`/`getSkuInfoByItemInfoVO`/`BuyMallItem`/`drawMachineGroups`/`doFarmDrawTask`/`hireAnimal`/`hireAnimalAction`/`drawGameCenterAward`/`ornamentsDressUp`/`saveOrnaments`/`getOrnamentsSets`/`family()`（家庭功能核心方法，`assignRights`/`eatTogetherConfig`/`familyInteractActions` 等）/`autoExchangeFamilyDecoration`/`assignFamilyMember`/`familyFeedFriendAnimal`/`animalWakeUpNow`/`familyAwardList`/`queryRecentFarmFood`/`familyShareToFriends`/`deliverMsgSend` 里 `deliverSubjectRecommend`/`DeliverContentExpand` 响应字段解析等。

文件里仍残留的 6 处 `.get*()`（`drawGameCenterAward` 里一段、`letsGetChickenFeedTogether` 一段）确认全部位于 `/* ... */` 注释块内的死代码，不是运行时会执行到的路径，故意不动——改了也不会被编译，反而混淆"这段代码是否生效"的判断。

至此 `AntFarm.java`（4458 行，原~352 处调用点）全部转换完毕，`grep` 确认代码里已无可执行的裸 `.get*()` 调用。每批改完都跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过；仅做了编译期验证，未做设备/运行时测试。下一步按调用点数量排序转到 `AntForestV2.java`（约363处，全库最大单文件）。

用户明确要求把 `org.json.JSONObject`/`JSONArray` 的 `.get*()`（抛异常）手动改成 `.opt*()`（返回默认值/null），并且改完要补上对应的空指针判断，不是简单正则替换。范围限定为 JSON 读取，不碰 `android.os.Bundle` 的同名方法（`AntFarm.java` 未 import `Bundle`，整个文件都在范围内）。

全库统计约 1986 处调用点，`AntFarm.java`（4458 行，~352 处调用点）风险最高，按用户选择"从最高风险文件开始，一个个文件改"的节奏推进。本次覆盖 `enterFarm()`（关键：解析 `dynamicGlobalConfig`/`farmVO`/`masterUserInfoVO` 时对缺字段提前 return 并记录日志，因为它填充的 `ownerFarmId`/`ownerUserId`/`ownerGroupId`/`foodStock`/`foodStockLimit`/`harvestBenevolenceScore` 等字段贯穿全类）、`hasSleepToday`/`animalSleepNow`、`syncAnimalStatusAtOtherFarm`（顺带消除了一处对同一数组下标的重复解析）、`rewardFriend`/`recallAnimal`/`sendBackAnimal`、`receiveToolTaskReward`（逐项加 null 判断的循环重写）、`harvestProduce`、任务列表同步（`AntFarmDoFarmTaskListMap`/`AntFarmDrawMachineTaskListMap`）、捐蛋相关的 `donation()`/`competition()`/`stealRank()`/`receiveReward()`/`receiveDonationCompetitionProgressAward()`、`canDonationToday`/`recordFarmGame`/`listFarmTask`/`sign`/`doVideoTask`/`doAnswerTask`/`savePreviewQuestion`/`doFarmTask`/`receiveFarmTaskAward`/`feedAnimal`/`listFarmTool`/`useFarmTool`/`feedFriend`/`feedFriendAnimal`，约覆盖到文件 2213 行（总 4458 行的一半左右）。每完成一批就跑 `./gradlew compileNormalDebugJavaWithJavac -q` 验证，全部编译通过，未做设备/运行时验证。剩余部分（捐赠礼物、小鸡互动、家庭功能、道具等约后半部分）留到下一轮继续，之后按调用点数量排序转到 `AntForestV2.java`（约 363 处，全库最大单文件）等其余文件。

### 2026-09-14：全代码库 `new JSONObject(x)` 统一换成 `MyUtils.newJSONObject(x)`（31 个文件）；`.get*()` 全局改 `.opt*()` 不做

用户指出 `MyUtils.newJSONObject` 本来就是为了兼容替代 `new JSONObject(...)` 设计的，应该全局替换，不该只改前面几条记录里点名的那几处。这个判断是对的，之前几次只改被具体问题点名的调用点，属于保守而非有意排除。这次做了两件性质完全不同的事——一件全局改了，另一件没有，原因分开说：

**`new JSONObject(x)` → `MyUtils.newJSONObject(x)`：已全局替换**

用脚本对 `app/src/main/java` 下所有 `.java` 文件做正则替换（排除 `MyUtils.java` 自己，它的内部实现要保留原始 `new JSONObject`），只匹配单参数形式 `new JSONObject(参数)`（用 `new JSONObject\([^)]` 排除无参 `new JSONObject()`——无参构造是"造一个空对象来 `.put()`"，跟"解析一个字符串"是两码事，不能动）。共 31 个文件、769 处 `new JSONObject(` 里排除 87 处无参后命中替换。这个替换之所以能批量做而不用逐个人工判断，是因为 Java 的静态类型系统天然兜底：`MyUtils.newJSONObject` 只接受 `String` 参数，如果哪处原本调用的是 `new JSONObject(Map)`/`new JSONObject(JSONObject, String[])` 这类其它重载，替换后编译直接报错，不会是静默的运行时问题——所以第一步替换完直接编译，让报错精确定位所有需要人工复核的点，不需要靠人工通读筛出这几百处里哪些安全哪些不安全。

编译报错定位到 6 个真实需要处理的点，全部是"原来的 `try { ... } catch (JSONException e) { ... }` 里，try 块的内容替换后已经不会再抛 `JSONException`，导致 Java 判定这个 catch 子句不可达"：
- [FriendWatch.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/FriendWatch.java) `load()`：确认唯一调用点（`ApplicationHook.java:465`）是裸语句调用，不读返回值，直接去掉 try/catch，函数永远返回 `true`。
- [Privilege.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/Privilege.java) 三处（`handleYouthTaskAward`/`processStudentSignIn`/`executeStudentSignIn`）：try 块里除了 `MyUtils.newJSONObject`/`.optXxx()` 没有其它会抛异常的调用，去掉 try/catch；外层各自的调用链上仍有更外层的 `catch (Exception e)` 兜底（`studentSignInRedEnvelope()`），删除内层这层不改变最终容错行为。
- [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java) `queryAndCollect()`：原来分开 `catch (JSONException e)` 和 `catch (Exception e)` 两层，第一层已不可达，删掉，保留通用的 `catch (Exception e)`。
- [OldRpcBridge.java](../app/src/main/java/io/github/aw1y2z/sesame/rpc/bridge/OldRpcBridge.java) `requestObject()`：这处顺带挖出一个跟本次改动无关、原来就存在的死代码——`msg.contains("MMTPException")` 分支里 `return rpcEntity;` 后面还跟着一段 `retryInterval` 判断 + `continue;`，这段代码从写下来那天起就从没执行过（`return` 已经是这个 if 分支里的最后一条可执行语句，之前能编译通过是因为这段代码"看起来"在 catch 块的保护范围内，实际控制流早就走不到）。顺着这条线往下，方法末尾 `} while (count < tryCount); return null;` 里最后这个 `return null;` 同理也是先天不可达（这个 `do-while` 循环体内每条路径都会 `return`，从来没有真正循环过，`tryCount` 参数名义上存在但从未生效）。这次只删掉了这两处编译器指出的、真正不可达的死语句，**没有**顺手"修复"这个循环从未真正重试的问题——那是超出本次改动范围的独立行为变更，需要单独评估要不要让它真的重试。

**`.get*()` 全局改 `.opt*()`：不做，这个和上面那条性质不一样**

`new JSONObject(x)` → `MyUtils.newJSONObject(x)` 之所以能批量做，是因为两者在"输入合法"时行为完全等价，只在"输入为 null/非法"这一种明确、单一的边界情况上从抛异常改成返回空对象——而且这个新行为已经是全代码库几百处已经在用的既定模式（空对象 → 下游 `checkResultCode`/`checkMemo` 判失败 → 走已有的失败日志分支），不是引入新行为，只是让其它调用点也享受同一个已验证的兜底。

`.getXxx()` → `.optXxx()`不是这种性质。`get` 系列在字段缺失/类型不对时抛异常，`opt` 系列会返回一个"安全默认值"（`optString` 返回 `""`、`optInt` 返回 `0`、`optBoolean` 返回 `false`、`optJSONObject`/`optJSONArray` 返回 `null`）——但这个默认值本身在业务上往往不安全：比如一个 `get` 用在读取兑换金额、任务奖励数量这类字段时，字段缺失说明接口返回结构和代码预期不一致（协议变了，或者遇到了未见过的响应），这时候抛异常让外层 catch 接住、跳过这一轮操作，是**正确**的保护性行为；换成 `opt` 拿到静默的 `0`/`""`/`false` 之后，代码会**带着错误数据继续往下执行**，而不是停下来——这在写日志、展示文案这类场景问题不大，但在涉及数额判断、状态流转的地方，读到静默默认值后继续执行可能比抛异常更危险。这 700 多处 `get`/`opt` 混用不是随手写的，是"这个字段值不对就该整体失败"和"这个字段拿不到给个默认值继续就行"两种不同意图的体现，本次没有做全局替换，需要哪个具体调用点你觉得不对，我可以单独看。

**验证**：`./gradlew compileNormalDebugJavaWithJavac compileNormalDebugKotlin -q` 编译通过（改的过程中反复出现过 PowerShell `-Encoding UTF8` 写文件自动加 BOM 导致 javac 报"非法字符"的问题，另写脚本剥掉了 31 个文件的 BOM 才编译通过，这个坑记一下，以后用 PowerShell 批量改 Java 源文件要用 `System.Text.UTF8Encoding($false)` 显式无 BOM 编码）。`./gradlew assembleNormalRelease -q` 打包成功（2,821,533 字节）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**未做真机验证**：这是本次会话里改动文件数最多的一次（31 个文件），虽然每一步都靠编译器的静态类型检查兜底，但"编译通过"只能证明类型正确、控制流可达，不能证明这几百处 RPC 响应解析在真实数据下行为符合预期——尤其是把 6 处 `catch (JSONException)` 直接删掉后，这几个函数在极端情况下（比如 `MyUtils.newJSONObject` 内部吞掉的畸形 JSON）会不会因为读取空对象上不存在的字段而抛出新的（未被捕获的）`JSONException`/`NullPointerException`，没有主动构造过异常输入去验证。

### 2026-09-14：修三个 M 自己的 bug——用户明确只管 M，不追究 XU 是否也修了

上一条 GMT+8 记录之外，核对 XU 自己 `docs/MyFix.md` 提到的几个待修项时，发现两个 XU 文档说"待修改"但**它自己当前代码也没真改**的问题，同时另外自己挖出一个新的：

1. **`AntFarm.listFarmTask()` 未知任务状态中断整批处理**（已修）：[AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 原 1675 行 `TaskStatus.valueOf(jo.getString("taskStatus"))` 裸调用，`TaskStatus` 枚举只有 `TODO`/`FINISHED`/`RECEIVED` 三个值（4392 行），服务端一旦返回没见过的状态直接抛 `IllegalArgumentException`，只被循环外层的 `catch` 兜住——意味着**这一轮循环里排在后面的所有庄园任务都不会被处理**，不是只跳过那一项。改成每项单独 `try/catch IllegalArgumentException`，未知状态记日志后 `continue` 跳过当前项，不影响其余项目。顺手把循环体内复用外层变量名 `jo`（原来重新赋值会覆盖外层 `jo`）改成独立的 `taskJo`，避免变量名混淆，没有引入行为变化。
2. **`ApplicationHook.onPackageReady()` 用包名冒充进程名**（已修）：原 199 行 `lpparam.processName = param.getPackageName();`——libxposed 102 的 `PackageReadyParam` 不直接暴露真实进程名，之前图省事直接拿包名顶替，导致 `handleLoadPackage()` 里"主进程跑业务、子进程只装抓包"的分流判断在目标包的 `:xxx` 子进程触发这个回调时收到的还是主包名，分不清是不是子进程。新增 `getRealProcessName()`：优先反射调用 `ActivityThread.currentProcessName()`（API 28+ 的标准做法），拿不到则退回读 `/proc/self/cmdline`（覆盖 M `minSdk 26-27` 这段 API 28 以下的设备），两条路都失败才退回包名兜底——保留原来的行为下限，不会比之前更差。
3. **`AntForestV2` 收能量 `collectEnergy` 忽略空响应**（已修）：`jiaoshui`/`baohuhuizeng` 两个 `case` 分支（原 466、501 行）里 `new JSONObject(AntForestRpcCall.collectEnergy(...))` 不检查返回值就直接构造，请求失败/离线时 `collectEnergy` 可能返回 `null`，`new JSONObject(null)` 抛异常，被外层 `catch` 吞掉，中断本轮剩余金球的收取。改用本会话早前就在用的 `MyUtils.newJSONObject(...)`（`null`/非 `{` 开头输入返回空对象），失败时下游 `MessageUtil.checkResultCode` 在空对象上自然判失败，走已有的失败日志分支，不会再抛异常中断循环。

**明确没做的**：`new JSONObject(RpcCallXxx(...))` 不检查返回值这个写法在 M 全代码库里是**普遍写法**，`AntForestV2` 这两处只是被 XU 自己的审计文档点了名才顺手查到、修了；没有对整个代码库做一次系统性的 null 安全审计——那个工作量和 TimeUtil 改造类似甚至更大（要过一遍所有 RPC 调用点，逐个判断"这里失败了会不会真的中断不该中断的批量循环"），本次只按用户实际问到的范围处理。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告。`./gradlew assembleNormalRelease -q` 打包成功（2,824,420 字节，跟上一条记录基本一致）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**未做真机验证**：`getRealProcessName()` 依赖的 `ActivityThread.currentProcessName()`/`/proc/self/cmdline` 两条路径都需要在真实支付宝多进程环境（尤其带 `:xxx` 子进程的场景，比如抓包/推送相关子进程）里跑一次才能确认真的拿到了子进程自己的进程名而不是主进程名；`listFarmTask`/`collectEnergy` 的容错分支需要服务端真的返回未知状态/空响应才能触发，没有主动构造过这类异常输入去验证 catch 分支本身的正确性。

### 2026-09-14：TimeUtil 全面改成 GMT+8——对齐 Sure-Xu 的做法，不再是挨个补丁

之前几次记录里陆续在具体调用点（道早安/请客吃饭/好友刷新/ApplicationHook 三处/鱼塘时段）把 `Calendar.getInstance()` 换成 `MyUtils.getInstance()`，属于打补丁式的逐点修复。用户问"MyUtils.getInstance 不就是干这个的，全局替换不就行了，GR 不是这么做的吗"，核实后发现：GR 自己也不是这么做的——GR 只在少数几处手动改了，`updateDay()` 里跨天重新赋值那行仍然是裸 `Calendar.getInstance()`，本次会话早前已经指出并修过这个 GR 自己的不一致。真正一次性、干净的做法是 Sure-Xu 那种：**只改 `TimeUtil.java` 内部构造 `Calendar`/`DateFormat` 的几个源头方法**，所有调用 `TimeUtil.getNow()`/`isAfterTimeStr()`/`getToday()` 等方法的地方自动跟着拿到 GMT+8，不用逐个调用点手工替换——这是"改一处、所有调用者自动生效"的根因修复，不是分散打补丁。

**做法**：[TimeUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java) 新增私有常量 `GMT8 = TimeZone.getTimeZone("GMT+8")`，把类内部全部 `Calendar.getInstance()`（`isCompareTimeStr`/`getCalendarByTimeMillis`/`getDateStr`/`getToday`/`getNow`/`getWeekNumber` 共 6 处）改成 `Calendar.getInstance(GMT8)`；另外发现 `getTimeStr`/`getDateStr`/`getCommonDateFormat`/`getCommonDateFormatS` 这几个用 `DateFormat`/`SimpleDateFormat` 格式化输出的方法有个更隐蔽的坑——即使传入的 `Calendar` 已经是 GMT+8，`DateFormat` 格式化 `Date` 对象时默认仍然使用系统时区渲染字符串（`Date` 本身不带时区，只是绝对时刻），必须显式 `format.setTimeZone(GMT8)` 才能让最终输出的文本也是北京时间，这一步 Sure-Xu 的参考实现里也做了但容易被忽略。`getInstanceGMT8()`（此前几次记录加的委托方法）简化成 `getNow()` 的别名，不再重复实现。

**顺带清理干净了全代码库剩余的裸调用**：改完 `TimeUtil.java` 后 `grep -r "Calendar.getInstance()"` 复查，又挖出 6 个当时不在 family/道早安范围内、没顺手改的点，这次一并处理：
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 3 处（捐蛋排位 20:01 截止时间判断、偷榜定时目标时间计算、`isStealRankTime` 判断），是本次会话第一次做 SO 移除/GMT+8 审计时就发现但因为"不在 family 范围"特意没动的，这次一起改成 `TimeUtil.getNow()`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 自定义唤醒时间段的 `nowCalendar`——这是更早一条记录里明确写"故意没改"的点，因为当时 `TimeUtil.getTodayCalendarByTimeStr` 还是系统时区，两边只改一边会产生新的错位；现在 `TimeUtil` 已经整体 GMT+8 了，这个顾虑不存在，直接改成 `TimeUtil.getNow()`。
- [Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)、[Statistics.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Statistics.java) 的 `save()` 无参重载——追进去看了 `save(Calendar)` 内部实际只取 `nowCalendar.getTimeInMillis()`（时间戳本身不受时区影响）传给 `TimeUtil.isLessThanSecondOfDays`，严格说不改也不会错，但为了不让读者需要再验证一遍"这里安不安全"，统一换成 `TimeUtil.getNow()`。
- [MiuixMainActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixMainActivity.kt) 的 `Statistics.updateDay(Calendar.getInstance())`，同上。
- [Privilege.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/Privilege.java) 两处（`isSignInTimeValid()` 学生签到窗口判断、`executeStudentSignIn()` 判断 double/single 文案），换成 `TimeUtil.getNow()`。

改完之后 `grep -rn "Calendar\.getInstance()" app/src/main/java` 只剩两行历史注释（记录之前 bug 的说明文字），代码里已经没有裸调用。

**验证**：`./gradlew compileNormalDebugJavaWithJavac compileNormalDebugKotlin -q` 编译通过，无新增警告。`./gradlew assembleNormalRelease -q` 打包成功（2,824,154 字节，跟上一条混淆记录的体积基本一致，符合预期——这次只是时区逻辑改动，不影响代码体积）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**依然没有做真机验证**——`DateFormat.setTimeZone` 这类格式化输出变化尤其需要肉眼确认日志/统计页面显示的时间字符串确实是北京时间而不是乱码或时区错位；`Status`/`Statistics` 的跨天重置逻辑理论上无行为变化（只是显式化），但没有在非东八区设备/模拟器上跑过对照组确认。

### 2026-09-12：Release 开启混淆（minifyEnabled/shrinkResources），对照 GR2026/Sesame-AG/Sure-Xu 三份规则写 proguard-rules.pro

用户明确要求正式包必须开混淆。三个参考项目里 GR2026 和 Sesame-AG 都是"整包 keep 自己代码"（`-keep class io.github.lazyimmortal.sesame.** { *; }` / `-keep class io.github.aoguai.sesameag.** { *; }`）——技术上混淆开了，但自己的业务代码一行都没真正混淆，只混淆了第三方库。Sure-Xu（`E:\Work\Xu\app\proguard-rules.pro`）是唯一一个做了外科手术式规则的，而且 Sure-Xu 和 M 同为 libxposed 102 架构（GR 是传统 Xposed API，AG 虽然也是 libxposed 102 但选了偷懒的整包 keep），参照对象选了 Sure-Xu 这份，不是简单照抄 GR/AG 的省事做法。

**做法**：只保留三类必须不能被 R8 动的东西，其余全部允许真正混淆/内联/删除：

1. **反射调用点**——逐个找出源码里实际存在的反射用法，只保留刚好够用的规则：
   - `Model.initAllModel()`（[Model.java](../app/src/main/java/io/github/aw1y2z/sesame/data/Model.java)）用反射调用每个任务模块的无参构造函数，且简单类名本身就是持久化配置的 key（存量用户 `config.json` 里已经写死了类名字符串），所以 `model/**` 下所有 `extends Model` 的类**类名必须保留**，不能只保留构造函数——这点容易漏，之前理解为"只要构造函数能反射调用就行"是错的，实际测过 mapping.txt 才确认 `-keep class X extends Model { public <init>(); }` 这个语法本身就同时保留了类名，不需要额外加 `-keepnames`。
   - [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java) 的 `getWuaByReflection()`：`Class.forName("...AntOrchard")` + `getDeclaredMethod("getWua")` 跨类反射调用 [AntOrchard.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antOrchard/AntOrchard.java) 的私有方法——这是本次逐行核对 Sure-Xu 规则时才确认 M 也有一模一样的反射调用点（`AntMember.java:809` 起），如果不知道 Sure-Xu 已经踩过这个坑，光看 M 自己代码不容易联想到要为这一个私有方法单独写 keep 规则。
   - [ExtensionsHandle.java](../app/src/main/java/io/github/aw1y2z/sesame/model/extensions/ExtensionsHandle.java) 用 `Class.forName` 探测可选的 `ExtensionsHandleAlpha` 类是否存在（M 代码库里没有这个类，是预留的可选扩展点），保留主类 + 对不存在的 Alpha 类 `-dontwarn`。
   - Xposed 模块入口 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`META-INF/xposed/java_init.list` 里按字符串记录入口类全限定名。参照 Sesame-AG 的官方写法 `-keep,allowoptimization,allowobfuscation ... extends XposedModule { public <init>(); }` 配 `-adaptresourcefilecontents META-INF/xposed/java_init.list`——这个写法允许类名本身被混淆（实测 mapping.txt 显示 `ApplicationHook -> ah`），同时 R8 会自动同步改写 `java_init.list` 资源文件里的类名字符串，比 GR/AG 那种整包不混淆入口类更彻底。
2. **Jackson 反射需要的边界**：`data/*`（含 `AppConfig`/`TokenConfig`/`ConfigV2` 等）、`data/modelFieldExt/**`、`entity/**`、`hook/RpcRequest`、`util/Status`、`util/Statistics` 整体保留——这些类靠字段名/getter/setter 名做 JSON 序列化，改名会导致读不出存量用户已保存的配置和统计数据。同时补了 Jackson 库自身内部反射构造函数/枚举字段的 keep 规则，及桌面版 `java.beans.ConstructorProperties`/`Transient` 缺失警告的 `-dontwarn`（Android 上这两个类本来就不存在，Jackson 自己会处理，只是编译期会警告）。
3. **第三方库**：OkHttp3/Okio 标准 keep 规则（对齐 GR/Sure-Xu 都有的写法）、NanoHTTPD、`compileOnly` 的 `io.github.libxposed.api.**`（运行时由宿主 LSPosed 框架提供，混淆期这些类不在场，只需要 `-dontwarn` 消警告，不需要 keep）。

**没有照抄的东西**：GR/AG proguard 文件里各自的自定义混淆词典（`-obfuscationdictionary proguard-sxbk.txt` 等 GR 品牌相关）、AG 的 Shizuku/cmd-android/logback 相关规则（M 没有这些依赖）都没搬，照抄会引用不存在的文件/类导致构建失败或规则空转。

**验证**：`./gradlew assembleNormalRelease -q` 全程零警告零错误打包成功。产物体积从上一条记录的 14,778,120 字节**降到 2,824,113 字节**（约 5.2 倍收缩，主要是 `shrinkResources` 删掉了未引用资源、R8 删掉了未引用代码，不是签名或功能损失）。读取生成的 `app/build/outputs/mapping/normalRelease/mapping.txt` 逐条核对：`ApplicationHook` 按预期被重命名（`-> ah`，配合资源文件自动改写）；`AppConfig`/`AntSports` 等按预期保持原名未混淆；`MyUtils` 等普通工具类按预期被真正混淆（`-> a11` 这类短名）。`apksigner verify --print-certs` 签名验证通过，指纹不变。**完全没有做运行时验证**——这是本次改动里风险最高的一项：mapping.txt 只能证明"R8 按规则做了它认为该做的重命名/内联"，不能证明"重命名之后模块在真实支付宝进程里跑起来仍然正常"。尤其是 Jackson 反序列化存量用户的旧 `config.json`/`status.json`、`AntMember` 反射调用 `AntOrchard.getWua()`、Xposed 入口加载这三个反射相关的关键路径，必须在真机/模拟器上装一次跑一轮完整流程才能确认没有因为混淆漏保留某个反射目标而在运行时崩溃或静默失效——这类问题编译期和 R8 都不会报错，只会在运行时才暴露。

### 2026-09-12：VersionHook 滑块初始化时序补全——对齐 GR，用户明确要求把这个已知不完整点补上

上一条 VersionHook 记录里写明了一个已知不完整点：GR 把 `initSimplePageManager()`（滑块验证初始化）从 `attach` 钩子挪到了 Service.onCreate 版本覆盖之后，确保滑块初始化吃到的是（可能已伪装的）版本号；M 当时没有跟着挪，理由是这是全体用户都会走的初始化路径，为一个默认关闭的小众功能重排时序风险收益不对等。用户明确要求补上，这次照做：

- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`attach` 钩子里删掉 `initSimplePageManager();` 调用（连同已经注释掉的旧异步线程代码一起清掉，那段注释本来就是死代码）；在 Service.onCreate 里 `VersionHook` 版本覆盖判断结束之后（`if (VersionHook.isVersionHookEnabled()) { ... }` 这段之后）新增调用，跟 GR 的调用点位置一致。

**影响范围说明，不是回避**：这个改动确实影响全体用户，不只是开启版本伪装的人——`initSimplePageManager()` 的执行时机从"支付宝 Application.attach() 时"挪到了"支付宝前台 Service.onCreate 时"，onCreate 在 attach 之后触发，两者之间间隔通常很短（同一次进程启动流程内），按 GR 的实际使用情况看这个时序差影响面很小，但这确实是一次全局行为变更，不是只在开启 VersionHook 时才生效的隔离改动。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。`./gradlew assembleNormalRelease -q` 打包成功（14,778,120 字节）。**未做运行时验证**——`initSimplePageManager()` 时序变化对滑块验证初始化的实际影响、以及 attach 到 Service.onCreate 之间这段间隔期间滑块验证相关状态是否有其它代码路径依赖"已经初始化"这个前提，都没有在真实支付宝进程里跑过确认。

### 2026-09-12：福气鱼塘（FishTask）移植——以 Sure-Xu 的 Java 版为底，对照 Sesame-AG 修正

用户要求把福气鱼塘做了，指定用 Sure-Xu 的 Java 实现打底（Sure-Xu 已从 GR 原版修过 6 个已知缺陷，见其 `docs/GR-Sync.md`），如果 XU 版本有问题再参照 Sesame-AG（Kotlin 实现，用户更信任的参考）修。

**为什么不直接参照 AG**：AG 的 `task/antFishPond/AntFishPond.kt`（1473 行）绑定在它自研的通用任务状态机框架（`TaskFlowEngine`/`TaskFlowAdapter`/`TaskFlowPhase`/`TaskFlowDecision` 等）上，这套框架 M 完全没有，其它任何模块也不用；照抄意味着要先把整套状态机框架搬进来，工作量和架构侵入性都远超这一个功能本身需要的范围。Sure-Xu 的 `model/task/fish/FishTask.java`（2370 行）是普通过程式 Java，`extends ModelTask` 后 `getName/getGroup/getFields/check/run` 的方法签名和 M 的 `ModelTask`/`Model` 抽象类完全一致（两边本来就是同源分支），机械翻译包名风险低得多。

**移植内容**（新增 3 个文件 + 2 处基础设施 + 1 处注册）：

- [model/task/fish/FishConfig.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/fish/FishConfig.java)：设置项（钓鱼/任务两个总开关、自动黑名单开关、Token 输入框、黑名单多选、独立执行间隔），逐行对照 XU 版，`ModelField` 子类构造函数签名在 M 和 XU 里完全一致，纯机械翻译包名。
- [model/task/fish/FishTask.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/fish/FishTask.java)：任务本体。RPC 请求全部用 `String.format` 拼 JSON 字符串直接传给 `ApplicationHook.requestString(method, args)`（沿用 XU 的写法，不是 GR/AG 那种单独 RpcCall 类），业务逻辑（签到、每日宝箱、明日钓竿、钓鱼活动、任务列表六种任务类型分支、自动钓鱼主循环、收杆定位、双倍广告）逐行照抄 XU 版，字符串常量、等待时长、重试次数都没有改动——这些是抓包得出的经验值，没有 M 自己的真机验证依据去调整。丢弃了 XU 文件里确认没有任何调用点的死代码（`hasPendingTask`/`finishTask(JSONObject)` 旧版本/`receiveGiftBoxAward`/`isBeforeFourAM`/`extractPwPreBizIdFromUrl`/`extractBizIdFromUrl`/`API_BATCH_INVITE`），逐个 grep 确认过确实无调用才删，不是猜的。
- [util/idMap/AntFishpondTaskListMap.java](../app/src/main/java/io/github/aw1y2z/sesame/util/idMap/AntFishpondTaskListMap.java)：新增，照抄 M 已有的 `AntForestHuntTaskListMap.java` 模板（同样的 load/save/get/add 结构），存运行时发现的鱼塘任务 ID→显示名映射，供黑名单设置项下拉选择。
- [util/FileUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/FileUtil.java)：新增 `getAntFishpondTaskListMapFile()`，跟其它 idMap 文件路径方法同一模式。
- [util/Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)：新增 `fishLastExecTime` 字段（`@Data` 生成 getter/setter），鱼塘用自己独立的执行间隔，不跟随全局任务间隔——这是本会话早前 GR `main_my` 47 提交审计时就识别出的需求（提交 `73507243`"鱼塘使用fishLastExecTime"），当时因为整个鱼塘功能没做而搁置，这次一起补上。
- [model/base/ModelOrder.java](../app/src/main/java/io/github/aw1y2z/sesame/model/base/ModelOrder.java)：`clazzList` 里注册 `FishTask.class`。

**对照 AG 修的一处**：`isAllowedTime()`（判断当前是否在 05:00-23:00 运行时段）原版用 `TimeUtil.getNow()`，跟本次会话里 `deliverMsgSend`/`familyEatTogether`/`FriendWatch.needUpdateAll`/`ApplicationHook` 三处早前修过的 GMT+8 bug 是同一个根因——系统默认时区而非北京时间。改成 `MyUtils.getInstance()`。这是唯一一处主动对照 AG 发现的问题；`FISH_ACTIVITY`/`GIFT_BOX`/`TOMORROW_ROD` 等具体状态判断分支 AG 和 XU 写法不同（AG 用统一的 claimable-status 集合 + extend.status 兜底，XU 分散在几个方法里用不同的时机判断），**没有逐条位重新审计这些分支的等价性**——那相当于把整个 AG 1473 行再读一遍逐句比对，工作量堪比本次移植本身，本次没有做，如果实测发现某个子活动（宝箱/明日钓竿/钓鱼活动奖励）长期不触发，需要单独排查。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。实际跑了 `./gradlew assembleNormalRelease -q`，成功打包（14,778,084 字节）。**完全没有做运行时验证**——鱼塘的抓包字段、任务类型、等待时长这些经验值必须在真实支付宝进程里跑过才能确认还有效（抓包时效性本身也是风险，`VERSION = "20260211.01"` 这类版本号字段服务端随时可能不认），黑名单/Token 输入框等设置项 UI 展示也未在模拟器里点过。

### 2026-09-12：处理上次审计留的三个候选——writeDishImage 做了，VersionHook 做了（默认关），福气鱼塘查了源头还未做

**福气鱼塘**：检查了 `E:\Work\Sesame-AG`（Kotlin，`task/antFishPond/AntFishPond.kt` + `AntFishPondRpcCall.kt`）和 `E:\Work\Xu`（Java，`model/task/fish/FishTask.java` + `FishConfig.java`，与 GR 文件名/结构几乎一致）——**两边都已经有这个功能**，其中 Sure-Xu 是从 GR 移植过来的 Java 版本，且 Sure-Xu 自己的 `docs/GR-Sync.md` 记录了移植时顺带修过的 6 个 GR 原版缺陷（成功兑换也写失败标记、循环末尾无条件清零失败计数、自动黑名单开关未接入、状态查询与广告处理互相重入、任务等待忽略中断、浏览循环次数用 `max` 未限制上限等）。~~结论：以后真做这个功能时……本次没有动手移植~~——已经做了，见上面（更晚）的"福气鱼塘（FishTask）移植"记录，用的正是这里说的 Sure-Xu Java 版本打底。

**VersionHook 版本伪装——确认判断错了，已移植，默认关闭**：上次审计只读了 `VersionHook.java` 本身就下判断"个人偏好，不移植"，这次用户要求查清楚具体干什么、有没有用，深挖了实际生效路径才发现：这不是简单的"跳过本地判断分支"，而是**向支付宝服务端主动谎报一个更低的客户端版本号**，目的是规避服务端对高版本客户端才触发的拼图验证码风控（GR 原注释："使其认为安装了低版本，从而避免高版本特有的拼图验证"）。用户知悉这个真实性质后仍要求移植，默认关闭。

移植内容：

- 新增 [hook/ext/VersionHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ext/VersionHook.java)：API 从传统 Xposed（`XposedHelpers`/`de.robv.android.xposed.XC_MethodHook`）换成 M 自己的兼容层（`XHelpers`/`compat.XC_MethodHook`）——这套兼容层本来就是 `util/compat/` 下为了在 libxposed 102 上运行传统 Xposed 风格代码而做的，本次是第一次真正用它承接一个"新" hook（此前只是承载既有代码）。有一处不能照抄 GR：GR 用 `XposedHelpers.findAndHookMethod(className, null, ...)` 传 `null` classloader 依赖传统 Xposed 的兜底解析，M 的 `XHelpers.findClass` 没有这个兜底（`cl.loadClass()` 对 null 会直接 NPE），改成传实际的 `classLoader`（支付宝进程的 classloader，能正常解析到 `android.app.ApplicationPackageManager` 这个框架类）。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：新增 `realAlipayVersion` 字段和 `getEffectiveVersion()` 方法；`attach` 钩子里记录真实版本号的同时注册 `VersionHook.initVersionHook(classLoader)`（必须在 attach 钩子真正触发、读取 `getPackageInfo` 之前完成注册）；Service `onCreate` 里在文件系统就绪后 `ensureVersionConfig`/`loadVersionConfig`，开关打开则用伪装版本覆盖 `alipayVersion`；运行日志的"应用版本："那行改用 `getEffectiveVersion()`，开关打开时额外标注真实版本。
- [MiuixExtensionsActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixExtensionsActivity.kt) 新增"版本伪装（谨慎使用）"卡片：开关 + 伪装版本名/版本号两个输入框，文案里直接写清楚"这是主动欺骗服务端的行为"，不是包装成人畜无害的小功能。配置存在跟支付宝注入进程共享的 `version_config.json` 里，UI 运行在 App 自己的进程（不是注入进程），改动后需要重新注入/重启支付宝才会在那边生效，UI 上有对应提示。

~~这次移植比 GR 窄一块……这是已知的不完整点~~：已在下一条（更晚的）"VersionHook 滑块初始化时序补全"记录里对齐 GR，`initSimplePageManager()` 挪到了 Service.onCreate 的版本覆盖之后执行。

**writeDishImage/writeDishImageWithRandomIds——确认有用，已移植**：M 现有的森林设置项文案自己就写着"光盘行动需要先手动完成一次"，说明这个手动种子的痛点本来就存在，是文档化的真实摩擦点，不是臆测的需求。

- [TokenConfig.java](../app/src/main/java/io/github/aw1y2z/sesame/data/TokenConfig.java) 新增 `writeDishImage(beforeMealsId, afterMealsId)` 和 `writeDishImageWithRandomIds()`，复用已有的 `checkDishImage`/`saveDishImage`，没有重复校验逻辑。
- [MiuixExtensionsActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixExtensionsActivity.kt)"其他"分组里加了"手动补光盘行动图片"入口，弹窗两个输入框 + "随机生成"/"写入"两个按钮，跟已有的"清空光盘行动图片"按钮并排，复用同一套 `Dialog`/`ConfirmDialog` 风格，没有另起一套 UI 模式。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `compileNormalDebugKotlin -q` 均无输出（编译通过，无新增警告）。额外跑了一次 `./gradlew assembleNormalRelease -q` 确认整个打包流程（含新增的 hook 注册代码）不会导致构建失败，产物 14,760,295 字节。**未做任何运行时验证**——VersionHook 这类东西必须在真实支付宝进程里触发 `getPackageInfo` 调用链才能看到效果，编译通过完全不能证明 hook 本身注册成功、`ApplicationPackageManager.getPackageInfo` 方法签名匹配、字段反射（`packageName`/`versionName`/`versionCode`）在当前系统版本上有效；也没有验证开启后是否真的能避开拼图验证码，或者是否会因为版本号与其它请求头/数据不一致而触发其它风控信号——用户需要自行在测试环境验证后再决定是否长期开启。

### 2026-09-12：GR2026 `main_my` 全部 47 个提交最终逐一核对——找到并修了 3 处新的 GMT+8 bug，其余确认为已覆盖/不适用

用户要求把 GR2026 自己改的代码里还有哪些功能没参考移植的过一遍。逐个重新过了 `git log --oneline main..main_my` 的全部 47 个提交（含之前几条记录已经处理过的），结果：

**新发现并修了 3 处同类 GMT+8 bug**（和 `deliverMsgSend`/`familyEatTogether` 那两处根因完全一样：用裸 `Calendar.getInstance()` 而不是 GMT+8，对齐 GR `7ce920e8`"通过AI同步旧逻辑"这个最早的批量同步提交里的改法）：

- [FriendWatch.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/FriendWatch.java) `needUpdateAll()`：判断好友列表是否需要整体刷新（"隔天 + 周一"触发全量更新），原来两个 `Calendar.getInstance()` 全部改成 `MyUtils.getInstance()`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 三处：Service `onCreate` 里 `dayCalendar` 的初始值、`setWakenAtTimeAlarm()` 里计算"明天 00:00"固定午夜唤醒闹钟的 `calendar`、`updateDay()` 里判断跨天用的 `nowCalendar`，全部改成 `MyUtils.getInstance()`。

**这处修复比 GR 自己做得更完整，不是照抄**：核对 GR 当前代码发现它自己都没改全——`dayCalendar` 在 Service `onCreate` 处确实用了 `MyUtils.getInstance()`（GMT+8），但 `updateDay()` 里跨天时重新赋值 `dayCalendar = (Calendar) nowCalendar.clone()` 用的 `nowCalendar` 仍然是裸 `Calendar.getInstance()`——意味着 GR 自己的模块跑过第一次跨天之后，`dayCalendar` 会静默从 GMT+8 语义退回系统时区语义，这是 GR 自己遗留的不一致 bug。这次没有照抄 GR 的半成品，`onCreate` 初始化和 `updateDay()` 跨天重新赋值两处一起改成 GMT+8，保持 `dayCalendar` 全生命周期语义一致。

**发现但没有动、需要以后单独处理的同类风险**：[ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) `setWakenAtTimeAlarm()` 里自定义唤醒时间段那部分（`Calendar nowCalendar = Calendar.getInstance()`，用于跟 `TimeUtil.getTodayCalendarByTimeStr(wakenAtTime)` 比较判断这个自定义闹钟今天是否还没过）**故意没有改**——`getTodayCalendarByTimeStr` 内部同样用裸 `Calendar.getInstance()` 构造"今天 HH:MM"，如果只把这里的 `nowCalendar` 单独改成 GMT+8 而不动 `TimeUtil` 本身，两边用不同时区反而会产生新的、更隐蔽的比较错位。`docs/GR-Sync.md` 早前已经点出 `TimeUtil` 大部分方法仍是系统默认时区、改动面很大，这次维持"不做半吊子修复"的原则，把这处留给以后专门做 `TimeUtil` 统一 GMT+8 改造时一起处理，不在这次顺手改一半。

**核对过、确认已经覆盖或不适用，不是遗漏**：

- **FishTask（福气鱼塘）**：`36d110b9`/`c3ae5375`/`549fcd7b`/`6c8cb85b`/`73507243` 等 9 个提交，`docs/GR-Sync.md` 早前已列为同步候选，本次重新确认这块工作量（独立 RPC 层 + 任务列表 + 黑名单 + 设置项注册）没有变化，仍未移植，维持"以后单独做一次完整任务"的结论，不在本次顺手做。
- **VersionHook 版本伪装**（`3013cb36` 新增 237 行 `VersionHook.java` + `cea6d5f9` 给 `AppConfig` 加 `enableFakeVersionSlider`/`fakeVersionName`/`fakeVersionCode`）：读了 `VersionHook.java` 全文确认——这就是拦截 `PackageManager.getPackageInfo()` 伪造支付宝版本号的功能，且用的是传统 Xposed API（`de.robv.android.xposed.XC_MethodHook`/`XposedHelpers`），M 现在只依赖 `io.github.libxposed:api:102.0.0`，连编译都过不了。这正是 `docs/GR-Sync.md` 一开始就点名的"GR 的版本伪装……属于具体版本/个人偏好，不直接覆盖"那一条，本次读源码确认判断依然成立，不是漏做。
- **扩展页面重写**（`41e74688`，559 行 `ExtensionsActivity.java` + 对应布局 XML）：内容基本就是上面版本伪装功能的开关 UI，外加下面 `writeDishImage` 手动工具的入口，两者本身都不搬，这个 XML UI 重写自然也不适用——M 的扩展页面是 `ui/miuix/MiuixExtensionsActivity.kt`（Compose），架构完全不同，没有直接对应关系。
- **`writeDishImage`/`writeDishImageWithRandomIds`**（`2a6d496b`，`TokenConfig.java` 新增两个方法）：光盘行动（森林任务的一种，上传餐前餐后照片换能量）手动补图片 ID 的便捷工具，只在上面提到的扩展页面里被调用（手动点按钮用）。M 的 `TokenConfig.java` 已经有 `saveDishImage`/`checkDishImage`/`clearDishImage` 这套底层能力，缺的只是这两个方便手动调用的包装方法和对应 UI 入口。**这是本次审计里唯一一个"未评估过、可能有用但没做"的候选**——是否需要在 Compose 扩展页面里加个手动补光盘图片 ID 的入口，需要用户确认后再做，本次没有主动加。
- **其余提交**（`560281dc`/`9a0c4408`/`48820e4c`/`51fbc6db`/`083587dd`/`189010b6` 等）：纯版本号/更新日志/构建脚本版本号文本变化，符合用户之前定的"版本号更新记录不用看"的范围，跳过。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。未运行模拟器/真机验证这几处时区修复后的实际调度效果（好友列表周一全量刷新、午夜固定唤醒闹钟、跨天状态重置），这几处都是全局调度逻辑，风险比单个任务模块的时区 bug 更高，建议后续找机会实机验证一次日期跨天时机是否符合预期。

### 2026-09-12：Release 只保留 arm64-v8a 产物，不再打 armeabi-v7a 和 universal

用户要求只保留 arm64 的 lib，其它不要。[app/build.gradle](../app/build.gradle) 的 `splits.abi` 从 `include "armeabi-v7a", "arm64-v8a"` + `universalApk true` 改成 `include "arm64-v8a"` + `universalApk false`——armeabi-v7a（32 位）和 universal（全架构兜底包）都不再产出。输出文件命名逻辑（`androidComponents.onVariants` 里的 abiTag 拼接）不用改，少了 universal 之后自然只剩一个带 `-arm64-v8a` 后缀的产物，没有走到原来的 "-universal" 兜底分支。

**验证**：`./gradlew assembleNormalRelease -q` 重新打包，`app/build/outputs/apk/normal/release/` 下只有 `Sesame-M-Normal-arm64-v8a-1.0.0.apk` 一个文件（14,753,721 字节），`apksigner verify --print-certs` 验证通过、指纹不变。上一条记录里"Release 打包后自动归档"的任务本身不用改（原来就是遍历输出目录下所有 `.apk`，产物变少了自动就只归档这一个），顺手清理了 `APK/Release/` 下这次之前几轮验证时产生的 armeabi-v7a/universal 旧归档文件（本地产物，不是仓库跟踪文件）。未验证 32 位设备上是否还有人需要 armeabi-v7a 包——这是用户明确要求去掉的，不是本次自行判断的取舍。

### 2026-09-12：彻底移除 libsesame.so 及其加载/桥接代码——比 GR 更进一步，对齐 Sure-Xu 的做法

用户要求参考 GR 去掉 SO 调用："本来就能直接调用的庄园功能，不需要调用 so"。核查后发现 M 当时的实际状态：

- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 原 295 行 `System.load(LibraryUtil.getLibSesamePath(context))` 在支付宝 Service `onCreate` 时**无条件强制加载** SO——这是 `docs/GR-Sync.md` 早前点名过的"M 现在还在真调用它"的那处。
- 但 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 原 `doFarmTask()` 里唯一会调用 native 方法的分支（`LibraryUtil.doFarmTask(task)`）**早就是整段注释掉的死代码**，实际生效的是紧接着的 `AntFarmRpcCall.doFarmTask(bizKey)` 这条纯 Java RPC 路径。换句话说：**SO 被强制加载进内存，但没有任何地方真正调用它的 native 方法**——纯粹是加载开销和 APK 体积的浪费，用户的判断是对的。

`docs/GR-Sync.md` 之前记录 GR 自己的处理是"已注释这一加载，并用 Java 实现庄园任务；其仓库仍保留 SO，旧配置 UI 也仍尝试加载，所以 GR 本身并非完全无 SO"——只是注释掉调用，SO 文件和桥接类都还留着。这次没有照 GR 这个"半吊子"做法抄，而是按 Sure-Xu 在其 `MyFix.md` 里记录的更彻底方式（"本次已删除四个架构的 libsesame.so、旧包名 LibraryUtil 桥接类、强制加载"）直接整个删掉：

- 删除 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 里的 `System.load(...)` 调用及其 `LibraryUtil` import。
- 删除 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 里那段已经注释掉的 `LibraryUtil.doFarmTask` 死代码。
- 整个删除 `app/src/main/java/io/github/lazyimmortal/` 目录（`util/LibraryUtil.java` + 转发用的 `BuildConfig.java`）——这是仅有的两个还在用旧包名 `io.github.lazyimmortal.sesame` 的文件，删除前确认过没有其它文件引用这个包（`grep -rl "io.github.lazyimmortal"` 只命中这两个文件加 `ApplicationHook.java` 的 import 行，import 已一并删除）。
- 删除 `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsesame.so` 四个架构的二进制文件。
- [app/build.gradle](../app/build.gradle) 里原来指向这个目录的 `sourceSets { main { jniLibs.srcDirs = ['src/main/jniLibs'] } }` 也一并删掉（参考 Sure-Xu `app/build.gradle`，那边压根没有这条自定义 jniLibs 目录声明）；`packaging { jniLibs { useLegacyPackaging = true } }` 保留不动，这条是给 AndroidX/Compose 自带的 native 库（比如 `libandroidx.graphics.path.so`）用的，跟本次删的东西无关。

**没有动的**：`app/src/main/cpp/watermark.cpp` 和 `CMakeLists.txt`——这是另一个独立的 native 产物（水印相关），`docs/GR-Sync.md` 早前已确认它从未接入 Gradle 构建（不产生任何 .so，`WatermarkUtil` 一直走 Java 回退值），跟这次"庄园功能调用 SO"是两回事。既然不产生二进制、不影响 APK 体积，本次没有顺手删，需要的话应该单独确认。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。实际跑了一次 `./gradlew assembleNormalRelease`：三个产物体积从上一次归档记录的 `arm64-v8a 15,168,233` / `armeabi-v7a 15,146,501` / `universal 16,397,519` 字节，降到本次 `14,753,727` / `14,753,411` / `14,767,934` 字节（universal 减少约 1.6MB，两个 ABI 专属包各减少约 400KB——两次构建之间还有其它改动，不是纯粹的 SO 体积差，但方向和量级符合预期）。`apksigner verify --print-certs` 对新产物验证通过，签名指纹与此前一致。未运行模拟器/真机验证移除 SO 加载后模块整体功能是否正常（尤其是曾经依赖 SO 才能工作、后来才切换到 Java RPC 路径的庄园饲料任务，这次只确认了编译期没有残留引用，没有做运行期回归测试）。

### 2026-09-12：家庭功能六项子功能逐一对照 Sesame-AG——修了两处，其余记录为已知差异

用户要求把 family() 剩余六项子功能（签到/顶梁柱/领奖/喂鸡/请客/分享好友）也逐一对照 Sesame-AG 的 `AntFarmFamily.kt` 查一遍。结果：

**修了 2 处**：

1. **`familyEatTogether`（请客吃饭）同款 GMT+8 时区 bug**（原 3843-3853 行）：用 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 判断当前处于早/午/晚餐哪个时段，这两个方法内部用系统默认时区的 `Calendar.getInstance()` 构造时间边界（见 [TimeUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java) `isCompareTimeStr`/`getCalendarByTimeMillis`），跟上一条记录里 `deliverMsgSend` 的时区 bug 是同一根因、同一个 `TimeUtil` 系统时区问题的另一处命中。改成直接用 `MyUtils.getInstance().get(Calendar.HOUR_OF_DAY)` 取 GMT+8 小时数比较，不再经过 `TimeUtil` 的字符串时间比较。**没有**顺手给 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 本身加 GMT+8 重载——那样改动面更大（`docs/GR-Sync.md` 早前统计过这两个方法在全项目还有很多其它调用点），只在这一处绕开。
2. **`assignFamilyMember`（顶梁柱）空列表保护缺失**（原 3778 行）：`jsonObject.getJSONArray("assignConfigList")` 取到空/缺失数组时直接往下 `RandomUtil.nextInt(0, assignConfigList.length() - 1)` 会传入非法区间（`0, -1`）抛异常。虽然外层 `try/catch` 会吞掉（不炸整个 `family()`），但对齐 Sesame-AG `assignFamilyMember` 的显式判空提前返回，改成 `optJSONArray` + 判空/判 0 长度提前 return 并打日志，问题原因更清楚，不是"莫名其妙这一轮顶梁柱没执行"。

**对照过，判断不需要改的差异**（AG 更完善，但不构成 M 这边的实际缺陷）：

- **`familySign`（签到）没有本地当日去重 flag**：AG 有 `Status.hasFlagToday(FLAG_FARM_FAMILY_SIGNED)` 双重保险，M 完全依赖服务端下发的 `familySignTips` 布尔值（`family()` 里 `if (familySignTips && ...) familySign();`）。服务端已经会在签到后把 `familySignTips` 置 false，本地没有二次保险不算错误，只是少一层防御，没有改。
- **`familyAwardList`（领奖）没有饲料容量检查**：AG 对 `awardType == "ALLPURPOSE"` 的奖励会先查 `prepareFarmAwardCapacity(count)`，容量不够就跳过保留下次领取。M 直接无条件领取所有可领奖励。M 代码库里没有现成的"饲料容量查询/预留"基础设施，这是要新增能力而不是照抄一行判断，本次没有做，是否需要防止饲料溢出浪费需要用户确认后再单独处理。
- **`familyFeedFriendAnimal`（喂鸡）**：AG 对错误码 `388`/"小鸡太小" 有专门的静默日志分支，且循环内命中当日总上限会提前整体退出（省 RPC）；M 都会落到通用的"喂食失败"日志分支，且达到上限后仍会对剩余动物逐个尝试（每个都失败一次 391，不会执行成功但会多打几条失败日志、多打几次 RPC）。不影响最终结果，只是效率和日志噪音差异，没有改。
- **`familyShareToFriends`（分享好友）里 `user.getId() != UserIdMap.getCurrentUid()`**：用 `!=` 比较 `String` 是可疑写法，但这行前面已经有 `!familyUserIds.contains(user.getId())`（family 成员本来就包含自己）先把自己过滤掉了，这个 `!=` 判断实际上是从未真正生效过的冗余条件，不是导致"把自己分享进去"这类实际错误的原因，判断为无害死代码，没有单独修。M 用的是 `batchInviteP2P` 逐个邀请，AG 用的是完全不同的 `FriendSelectionModelField`/`inviteFriendVisitFamily` 批量邀请架构（含"选中邀请"/"选中不邀请"模式），两边分享好友这部分本来就是不同世代的实现，没有可比性，不评估细节差异。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误（仅剩既有 `Status.java` unchecked 警告，与本次无关）。未运行模拟器/真机验证 `familyEatTogether` 修复后在非东八区设备上的实际触发时机，未验证 `assignFamilyMember` 空列表分支的真实触发场景（正常情况下 `assignConfigList` 应该不会为空，这是防御性修复）。

### 2026-09-12：修复道早安（deliverMsgSend）两个实际缺陷——参考 Sesame-AG，不是 GR

用户反馈"之前就是道早安有问题"，所以 GR2026 才整体改用从 Sesame-AG 移植来的 `AntFarmFamily.kt`（上一条家庭功能记录里已经确认 M 的 Java `family()` 没有照抄 GR 的 Kotlin 重写）。这次直接对照 Sesame-AG 当前的 `AntFarmFamily.kt#deliverMsgSend`（`E:\Work\Sesame-AG\app\src\main\java\io\github\aoguai\sesameag\task\antFarm\AntFarmFamily.kt:1023-1184`）逐行核对 M 的 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 同名方法，找到两个真实缺陷并修了：

1. **时间窗口用了系统默认时区，不是北京时间**（AntFarm.java 原 3909-3920 行）：AG 用 `MyUtils.getInstance()`（GMT+8）判断"是否在 06:00-10:00"，M 原来是裸 `Calendar.getInstance()`。宿主设备系统时区不是东八区时，这个窗口判断会整体偏移，导致道早安要么提前不执行要么错过窗口——这类问题 `docs/GR-Sync.md` 早前就点名过 M 的 `TimeUtil` 大部分方法仍是系统默认时区，这是其中一个具体命中的实例。改成 `MyUtils.getInstanceGMT8()`（通过 `MyUtils.getInstance()`，本会话早前加的委托）。
2. **`QueryExpandContent` 调用失败或响应字段对不上就直接放弃整次道早安**（AntFarm.java 原 4018-4028 行）：原来 `resp3.getString("content")` 只认一个字段名，`MessageUtil.checkMemo` 校验不过或者字段名对不上（`getString` 抛 `JSONException`）就整个方法 return，即使上一步 `DeliverContentExpand`（resp2）已经拿到了可用文案也不会用。AG 的实现把 `QueryExpandContent` 当作"可选二次校验"（doc comment 原话），失败时回退用 resp2 的文案，且用多个候选字段名（`content`/`expandContent`/`deliverContent`/`msgContent`/`text`，顶层找不到再进 `data` 里找）尽量取值，不会因为一个字段名对不上就整体判失败。移植了 AG 的 `extractGreetingContent()` 辅助方法（新增私有静态方法，同名），并把 `resp3` 处理改成失败/取不到内容时用 `extractGreetingContent(resp2)` 兜底，两边都取不到才真正放弃。

**没有动的**：`family()` 其余六项子功能（签到/顶梁柱/领奖/喂鸡/请客/分享好友）这次没有逐个跟 AG 对照——用户这次问题明确指向道早安，没有要求全量审计；如果这几项也有类似问题，需要单独排查。`AntFarm.java` 里还有 3 处裸 `Calendar.getInstance()`（1217/1285/1459 行），但都在"捐蛋排位"相关代码里，不属于 family 功能范围，这次没有顺手改。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。未运行模拟器/真机验证修复后道早安在真实非东八区设备上的实际执行时机，未验证 `QueryExpandContent` 真实失败场景下 fallback 文案的实际可用性（无法验证服务端在 06:00-10:00 窗口外的响应行为）。

### 2026-09-12：小鸡家庭补上"装修金购买家具"——之前的评估搞错了 M 已有的家庭功能规模

用户要求把 GR2026 `main_my` 提交 `c9287afd`（"1.9.3修改道早安调用"，标题误导，实际引入了一整个 `AntFarmFamily.kt` 家庭功能）里能移植的部分移过来。上一条相关记录（"合并 GR2026 `main_my` 分支的个人功能提交"）当时把这个当成"未来值得单独移植的大候选项"跳过了，理由是"718 行新 Kotlin + 3 个新工具类"，**这个理由是错的**：只看了 GR 新文件的行数，没有去核对 M 自己 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 里早就有一份独立演化的 Java 实现（`family()` 方法，3564 行起），已经覆盖了签到（`familySign`）、顶梁柱（`assignFamilyMember`）、领取奖励（`familyAwardList`）、帮喂成员（`familyFeedFriendAnimal`）、请客吃饭（`familyEatTogether`）、道早安（`deliverMsgSend`，OpenAIPrivatePolicy→deliverSubjectRecommend→deliverContentExpand→QueryExpandContent→deliverMsgSend 五步 RPC 链完整都在）、分享给好友（`familyShareToFriends`）——GR 新 Kotlin 文件里的七项功能，M 已经有六项，用的是不同实现但等价。

**真正缺的只有一项：装修金购买家具**（GR `autoExchangeFamilyDecoration()`）。移植内容：

- [AntFarmRpcCall.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarmRpcCall.java)：新增 `queryRecentFarmFood(int)`、`getFitmentItemList(String,int,String,int)`、`exchangeBenefit(String,String,String)`（3 参新重载，M 原有的是 5 参版本，两者场景不同不冲突）；`inviteFriendVisitFamily(JSONArray)` 用 GR 现在的活跃实现（`bizType:FAMILY_SHARE`）补上——M 原来这个方法和 GR 旧版一样是整段注释掉的死代码，两边都保留旧注释不动，新增的是一个新方法。
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java)：新增私有方法 `autoExchangeFamilyDecoration()`（逐分类分页遍历装修金商城家具列表，买得起且未拥有的就兑换，`seat3` 分类处理完后余额不足 49 装修金就提前终止），在 `family()` 末尾加了 `if (familyOptions.getValue().contains("ExchangeFamilyDecoration")) autoExchangeFamilyDecoration();`。逻辑照抄 GR，只是把 Kotlin 换成 Java、`GlobalThreadPools.sleepCompat(2000)` 直接换成 M 已有的 `TimeUtil.sleep(2000)`（没有为一行委托新建一个工具类）。
- [CustomOption.java](../app/src/main/java/io/github/aw1y2z/sesame/entity/CustomOption.java) 的 `getAntFarmFamilyOptions()`：新增 `("ExchangeFamilyDecoration", "兑换家庭装饰(消耗装修金)")` 选项，默认不勾选（跟 M 其余家庭选项一样走 `familyOptions` 这个 `SelectModelField`，不是新开一个开关）。
- [ResChecker.java](../app/src/main/java/io/github/aw1y2z/sesame/util/ResChecker.java)：新增工具类，GR 原样搬（success/isSuccess/resultCode/memo 任一命中即成功，对"人数过多""小鸡睡觉"等已知非错误状态静默跳过不打日志）。这是真正新增的能力——M 现有的 `MessageUtil.checkSuccess`/`checkResultCode` 逻辑形状不同（忽略关键词列表、失败时打印调用栈来源都是 GR 这版特有的），不是重复造轮子。只用在新增的装修购买功能里，没有替换 M 其余地方已经在用的 `MessageUtil`。
- [Log.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Log.java)：补了 `record(tag,msg)`/`error(tag,msg)`/`printStackTrace(tag,msg,throwable)` 三个双/三参重载（`ResChecker`/`autoExchangeFamilyDecoration` 要用），实现是薄委托到 M 已有的单参版本，M 各日志开关（运行日志/异常日志等）的门控逻辑不受影响。
- [Status.java](../app/src/main/java/io/github/aw1y2z/sesame/util/Status.java)：补了 `setFlagToday(tag)`，就是 `flagToday(tag)` 的别名（GR 也是这么实现的），不是新逻辑。

**没有移植/没有创建的东西**（判断为不需要，不是漏做）：

- `AntFarmFamily.kt` 整个文件——不移植，因为 M 已有的 Java `family()` 覆盖了其中六项功能，重写成 Kotlin 单例只是平白引入一次大改动和回归风险，不换。
- `UserMap.kt`——GR 这个类只是 `UserIdMap.getMyUid()`/`getUserIdSet()`/`getMaskName()` 的薄包装，M 的 `UserIdMap` 本来就直接有这三个方法（`getCurrentUid()` 由 `@Getter` 生成），没必要包一层再包一层。
- `GlobalThreadPools.java`——GR 这个类整个就是 `TimeUtil.sleep(millis)` 的一行委托，M 直接调用自己的 `TimeUtil.sleep()`，不建这个类。
- `extensions/JSONExtensions.kt`——只用到其中 `toJSONArray()` 这一个一行扩展函数（`JSONArray(this)`），没必要为一行代码移植一整个扩展函数文件，直接在用到的地方写 `new JSONArray(familyUserIds)`（本次没有实际用到，因为没有整体移植 Kotlin 文件）。
- `ModelField.java` 的 `value` 字段 `protected`→`public`——不需要，见上面的附录修正说明。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `./gradlew compileNormalDebugKotlin -q` 均无输出（编译通过，本次改动零新增警告/错误；仓库里同一批还有其它未关联改动导致 Lombok 警告数在 1～5 条间波动，属已知噪音，不是本次引入）。未运行模拟器/真机验证装修金购买的真实 RPC 交互效果，未验证家具分类遍历在真实账号余额下的实际购买行为，`decorationCoinActivityId` 默认兜底值 `"20250808"` 是否已过期未核实。

### 2026-09-12：Release 打包后自动归档 APK 到项目根目录

参考 Sure-Xu `app/build.gradle` 的 `assembleLegacyRelease` 归档任务写法（`tasks.matching { it.name == '...' }.configureEach { ... doLast { Files.copy(...) } }`，按北京时间加时间戳），GR2026 `app/build.gradle` 的 `applicationVariants.configureEach { ... assemble.doLast { project.copy { ... } } }` 也是同类思路，但用的是 AGP 老版 Variant API（`applicationVariants`），M 和 Sure-Xu 一样是 AGP 9.2.1，这套 API 在新版本上已不推荐/可能不可用，所以照 Sure-Xu 的新 Variant API 写法来，没有照抄 GR 那版。

**和 Sure-Xu 的关键差异**：Sure-Xu 只有单一 Legacy 产物，固定文件名直接拷贝；M 在 [app/build.gradle](../app/build.gradle) 里本来就有 ABI 拆分（`armeabi-v7a`/`arm64-v8a`/`universal` 三个产物，见 `splits { abi { ... universalApk true } }`），一次 Release 构建会产出 3 个 APK。改成监听 `assembleNormalRelease` 任务，`doLast` 里遍历 `outputs/apk/normal/release/` 目录下所有 `.apk` 文件逐个拷贝到 `APK/Release/`（文件名保留原样，仅追加 `_yyyyMMdd_HHmmss` 时间戳），而不是 Sure-Xu 那样固定单一文件名。

**验证**：实际跑了一次 `./gradlew assembleNormalRelease`（不是只看配置阶段），确认：
- 编译通过，5 条既有 Lombok 警告（`BaseModel`/`ViewAppInfo` 的 `@Getter` 冲突），无新增警告或错误。
- `APK/Release/` 下生成了 `Sesame-M-Normal-arm64-v8a-1.0.0_20260912_195300.apk`、`Sesame-M-Normal-armeabi-v7a-1.0.0_20260912_195300.apk`、`Sesame-M-Normal-universal-1.0.0_20260912_195300.apk` 三个文件，和 `app/build/outputs/apk/normal/release/` 下的原始产物大小一致。
- `apksigner verify --print-certs` 对归档出来的 universal 包验证通过，证书指纹（`SHA-256: 46cca38d...62e712e2`）与本次会话早前 `signingReport` 得到的一致，确认签名链路没问题。

`APK/` 目录本身没有单独加 `.gitignore` 规则——`.gitignore` 里已有的 `*.apk` 通配规则本来就会匹配到这个目录下的文件，不需要额外处理。

### 2026-09-12：GeminiAI 全量迁移 + 四个 TAB 与运行日志补充编译时间显示

用户明确要求这两项必须做，补上此前 main_my 合并记录里被跳过/只做一半的部分。

**GeminiAI 全量迁移**（对齐 GR2026 `main_my` 当前 `GeminiAI.java`，提交 `6ec46dd7` 及其后续）：[GeminiAI.java](../app/src/main/java/io/github/aw1y2z/sesame/model/normal/answerAI/GeminiAI.java) 整个 `getAnswerStr()` 换成 GR 现在的实现——模型从 `gemini-1.5-flash` 换成 `gemini-2.5-flash`；请求体加 `tools: [{"google_search": {}}]`（GR 注释说明：不开启联网搜索答不了蚂蚁庄园这类时效性常识题）；Prompt 从"只回答答案 X"换成"直接给出答案文字，严禁解释，不要标点符号。题目：X"；返回文本额外做了 `trim()` + 正则清理标点（`[。，.！!？? "'“”]`）减少候选项匹配时的干扰；请求/响应的 JSON 构造统一换成 `MyUtils.newJSONObject()`（`MyUtils.newJSONObject().put(...)` 链式写法，对齐 GR 用法）。M 原来的实现（`gemini-1.5-flash`、无联网搜索、无标点清理）整块替换，没有保留旧分支或开关——用户是明确要求"必须改"，不是可选项，所以没有做成可回退的兼容层。

**四个 TAB 编译时间显示**（参考 Sure-Xu `MyFix.md` 第 10 项的做法："已在 Miuix 主页面共用的固定顶部……显示 BuildConfig.VERSION_NAME 和 BuildConfig.BUILD_TIME"；GR 侧对应 `main_my` 提交 `66338683`，但 GR 是改 XML `MainActivity.java`，M 没有这个文件，实现方式必须重新设计）：M 的 [MiuixMainActivity.kt](../app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixMainActivity.kt) 里首页/日志/配置/设置四个 TAB 各自有独立的大标题 `Text`，**没有**像 Sure-Xu 那样共用一个真正跨 TAB 固定的顶部栏。选择了对 M 现状影响最小的做法：新增一个共用的 `TabTitleRow(title)` composable（标题左对齐、`Modifier.fillMaxWidth()` + `Arrangement.SpaceBetween` 把 12sp 的 `"${BuildConfig.VERSION_NAME}  ${BuildConfig.BUILD_TIME}"` 推到右侧），替换掉四处 `HomeTab`/`LogsTab`/`ConfigTab`/`SettingsTab` 里原来各自手写的标题 `Text`。`BuildConfig.BUILD_TIME` 在 M 的 [app/build.gradle](../app/build.gradle) 里本来就用 `TimeZone.getTimeZone("Asia/Shanghai")` 生成，天然是北京时间，不需要额外处理时区。颜色用了本文件里已经在用的 `MiuixTheme.colorScheme.primary`（没有凭空猜一个不存在的 token 名导致编译失败）。

**运行日志里补编译时间**：GR2026 `ApplicationHook.java:347` 在 MAIN_TASK 循环的"应用版本/模块版本"日志之后还有一行 `Log.record("编译时间：" + BuildConfig.BUILD_TIME);`（GR 同一处原本的"开始执行"日志被注释掉换成了这行）。M 的 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 同一位置补了这一行日志，但**保留**了已有的"开始执行" + `MyUtils.recordUserName` 那行（两者不冲突，没有照抄 GR 那样二选一）。只加在这一处——GR 全仓库搜索 `BUILD_TIME` 只有 `ApplicationHook.java`/`MainActivity.java`/`WatermarkUtil.java` 三处，`Log.java` 本身不会给每条日志自动打编译时间戳，不是"所有日志行都带编译时间"，只是运行时在日志里额外记一行。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 和 `./gradlew compileNormalDebugKotlin -q` 均无输出（编译通过、无新增警告，仅剩 1 条与本次无关的既有 Lombok 警告）。未运行模拟器验证 Gemini 新请求的真实联网搜索效果、四个 TAB 标题栏在真机上的实际排版、以及运行日志里新增这行的实际显示效果。

### 2026-09-12：合并 GR2026 `main_my` 分支的个人功能提交（空指针/超时/单例等健壮性修复）

参考：`E:\Work\Gr\Sesame-GR2026` `main_my` 分支相对 `main` 的 47 个提交（`git log --oneline main..main_my`）。只看功能性提交，纯版本号/更新日志类提交（`更新日志：*`、`v1.9.*fix`、`新版本日志`）不看、不记录。

**已移植（均为把 `getXxx()`/`getJSONXxx()` 换成对应的 `optXxx()`/`optJSONXxx()`，防止字段缺失时抛 `JSONException`/空指针，逐处核对过 M 当前代码里存在完全相同的一行才改，不是批量替换）**：

- [AntForestV2.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java)：`resultDesc`（3 处，收能量/浇水/帮收）、`resultCode`（3 处：收能量流程 2 处 + `forFriendCollectEnergy` 1 处）。对齐 GR `fc05046c`、`e61005f1`。
- [AntSports.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antSports/AntSports.java)：`build()` 里 `endStageInfo` 判空后 `return 0`（原来 `data.getJSONObject("endStageInfo")` 在字段缺失时直接抛异常）、`treasureBoxList` 判空、`userExchangeRecords` 判空（M 用字面量 `"userExchangeRecords"`，未引入 GR 的 `MyUtils._OPT_USER_EXCHANGE_RECORDS` 常量——没必要为一个字符串字面量加依赖）、`itemId`（`getGiftItem` 附近）。对齐 GR `81258ed6`、`e6fc2655`、`c0013c97`。`reward.optString("itemId")`/`optString("name","")` 两处 M 已经是安全写法，不是这次改的。
- [AntMember.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antMember/AntMember.java)：三处 `getBoolean("success")` → `optBoolean("success")`（`trigger`/`queryOrdinaryTask`/`sendtrigger` 三个响应）。对齐 GR `e6fc2655`。
- [AntOcean.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antOcean/AntOcean.java)：两处 `fishVO`/`ExtrafishVO` 数组改 `optJSONArray` 判空后再循环。对齐 GR `e6fc2655`。
- [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java)：`resultCode`（"3D16"/"100" 两处）、`taskConfigResultVO` 判空（原来 `optJSONObject` 取出后直接 `.getString("awardType")`，对象为 null 时必炸；改成 `taskConfigResultVO == null ? "null" : taskConfigResultVO.optString(...)`，M 其余 `awardType` 调用点本来就已经是 `optString`，未改动）。对齐 GR `e61005f1`、`c0013c97`。
- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java)：`checkTask.get(10, TimeUnit.SECONDS)` → `30` 秒（对齐 GR `72384f8c`，理由是 10 秒在网络稍慢时容易误判超时）；加载成功 Toast 附带版本号（`"芝麻粒加载成功:" + modelVersion`，只搬了"带上版本号"这个点子，没搬 GR 那次顺带把品牌名改成"芝麻粒GR"的部分，对齐 GR `66338683` 但保留 M 自己的名字）。
- [GeminiAI.java](../app/src/main/java/io/github/aw1y2z/sesame/model/normal/answerAI/GeminiAI.java)：`OkHttpClient` 从每次请求 `new OkHttpClient().newBuilder().build()` 改成类级别单例 `CLIENT`（连接池/线程池复用），对齐 GR `cd72d38b`。**只搬了单例这一处**，GR 同一提交里对请求体/超时策略的其余改动没有搬（那属于下面"明确没有移植"的 GeminiAI 大改的一部分，两次提交内容有重叠，拆开处理）。`TongyiAI.java` 有一模一样的 `new OkHttpClient()` 反模式，但 GR 这次提交没碰过它，不在本次移植范围内，没有顺手改。
- [WatermarkUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/WatermarkUtil.java)：native 库未加载时的水印兜底文案从固定的"免费模块 交流QQ群:xxx"改成 `BuildConfig.VERSION_NAME + "  " + BuildConfig.BUILD_TIME`。对齐 GR `e22079f9`。**这个改动在 M 上实际意义更大**：`docs/GR-Sync.md` 已经确认 M 的 `watermark.cpp`/CMakeLists.txt 是死代码、没接入 Gradle 构建，`isLibraryLoaded` 恒为 `false`，也就是说这个"兜底"文案其实是 M 当前唯一会走到的路径，不是极端情况兜底。

**明确没有移植（评估过，判断当前不适合硬搬）**：

- **`model/task/fish`（福气鱼塘）**：`main_my` 里有独立的 `FishTask`/`AntFishpondTaskListMap` 等一整套模块（提交 `8b01744e` 起 9 个提交）。`docs/GR-Sync.md` 之前已经把这个列为同步候选，本次没有再重复评估细节；工作量和风险都不小（RPC 层、任务列表、黑名单、设置项注册全套），按"宁可不做也不要做一半"的原则本次不动，需要时应单独作为一次完整任务来做。
- ~~`AntFarmFamily.kt`（小鸡家庭）~~：这条评估是错的，见下一条记录——当时只看了 GR 的新 Kotlin 文件有多大，没有去核对 M 自己的 `AntFarm.java` 其实已经有一份更成熟的 Java 实现覆盖了签到/顶梁柱/喂鸡/请客/道早安/分享好友六项，规模判断严重失真。真正缺的只有"装修金购买家具"一项，已在下一条记录里补上。
- ~~GeminiAI 请求改造~~：用户后续明确要求必须做，已在下一条记录里补上完整迁移，不再是"没有移植"。
- ~~MainActivity.java 显示编译时间~~：用户后续明确要求必须做，已在下一条记录里以 Miuix Compose 的等价方式补上，不再是"跳过"。
- **`strings2.xml`/`strings_common.xml` 更新说明文案**（提交 `7e4fb5aa`）：纯 GR 自己的更新日志文本内容，与 M 无关。
- **ProGuard 混淆规则**（`cd72d38b` 里 `proguard-rules.pro`/`proguard/my-proguard-rules.pro` 的 OkHttp keep 规则）：M 的 `release` buildType 当前 `minifyEnabled false`，没有开混淆，这些 keep 规则暂时无意义，跳过；哪天 M 开混淆了需要重新评估。
- ~~`ModelField.java` 的 `value` 字段 `protected`→`public`~~：这条也是错的，见下一条记录——M 的 `ModelField` 本来就有公开的 `getValue()`，Kotlin 对 Java getter 会自动合成 `.value` 属性语法，`familyOptions.value` 这种写法不需要把字段本身改成 `public` 也能编译，GR 那次顺手把字段公开纯粹是它自己没用 `getValue()`，不是 M 需要跟进的改动，压根不用做。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过（1 条与本次无关的既有 Lombok 警告，无新增警告或错误）。未做模拟器/真机验证，未确认这些 null 安全修复对应的字段缺失场景在 M 当前环境下是否真的会被触发到（这些都是防御性修复，即便触发不到也不是坏事）。

### 2026-09-12：接入 MyUtils 调用点（此前只加了基础设施，没有真正生效）

上一条记录（"移植 MyUtils 通用部分"）只新增了 `MyUtils.java` 本身，没有任何调用点，属于死代码。本条把能对应上的调用点实际接进去。

**`recordUserName()`**：[ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 的"开始执行"（MAIN_TASK 循环内，改用 `getUserId()`）和"开始加载"（`userId` 已在作用域内）两处日志，对齐 GR `ApplicationHook.java:346,357,739` 的用法。

**`newJSONObject()`**：[RuntimeInfo.java](../app/src/main/java/io/github/aw1y2z/sesame/data/RuntimeInfo.java) 构造函数里原来 `new JSONObject(content)` 配 `try/catch(Exception)` 兜底的写法，换成 `MyUtils.newJSONObject(content)`，对齐 GR `RuntimeInfo.java:41-44` 的同一处理。行为上等价（原来的 catch-all 已经能吞掉 null 导致的 NPE），只是把"空输入返回空对象"的意图显式化。

**`closeVerification()`**：[AntForestV2.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java) 的 `whackMole()`（6秒拼手速）整体套了 `if (MyUtils.closeVerification()) return;`，以及主循环里 `queryAnimalPropList()`/`giveProp()` 两处调用改成 `if (!MyUtils.closeVerification())` 才执行——这三处 M 之前是**无条件执行**的，对齐 GR `AntForestV2.java:632,644,1638` 的逻辑后，默认行为变成"跳过"（因为 `AppConfig.closeVerification` 默认 `true`）。**这是一次真实的行为变更，不只是加基础设施**：以后如果发现森林伙伴/拼手速功能"不工作了"，先检查这个配置项，而不是当成 bug 去查。

**`closeUnRpc()`**：[AntForestV2.finishTask()](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antForest/AntForestV2.java) 补了 GR 同名方法里对 `GYG_BK_XYK`/`GYG_jinritoutiao`/`GYG_huabeikaitong` 三类不支持 RPC 完成的任务类型的跳过分支（对齐 GR `AntForestV2.java:2467-2474`），M 之前完全没有这段过滤，会对这些任务类型发起注定失败的请求。

**没接的**：`closeErrorFunction()` 在 GR 里对应的两个例子（`goldTicket()`、`AntFarm.visitFriend()` 的"非好友"跳过）分别是已失效的死函数和与 M 现有实现形状不同的代码，没有强行套用；`getSpFunctionError`/`setSpFunctionError` 机制本身可用但还没找到 M 里对应的具体调用点（GR 那几个 key 绑定的是 `MerchantService`/`ConsumeGold`，M 已经删掉了这两个模块）。

**验证**：`./gradlew compileNormalDebugJavaWithJavac` 编译通过（只有 1 条与本次改动无关的既有 Lombok 警告）。未跑模拟器验证森林伙伴巡护/拼手速跳过后的实际游戏内效果，未验证 `finishTask` 新增分支对真实任务列表的过滤效果。

### 2026-09-12：同步 GR2026 发布签名密钥

参考：`E:\Work\Gr\Sesame-GR2026\app\build.gradle`（`signingConfigs` 块）与 `E:\Work\Gr\Sesame-GR2026\xqe.jks`。

**安全前提，先说明白**：GR2026 把 `xqe.jks` 提交进了它自己的 Git 仓库，`storePassword`/`keyPassword` 直接明文硬编码在其 `app/build.gradle`（值为 `xqe123456`）——这把签名私钥已经随 GR2026 仓库公开/泄露，不是仅本项目知道的秘密。用户已知晓这一点，明确要求 Sesame-M 直接复用同一把 GR 的 `xqe.jks`（而非自己新生成一把独立密钥），本次按此执行。这意味着任何拿到 GR2026 仓库（或这把 `.jks`）的人理论上都能签出可以通过 Sesame-M 签名校验的安装包；`applicationId` 不同（`io.github.aw1y2z.sesame` vs GR 的 `kt.gr.sesame`）能避免 Android 层面的同包覆盖安装风险，但不能防止有人拿同一把钥匙伪造一个自称"Sesame-M"发布的、`applicationId` 也改成一样的安装包。

**做了什么**：

- 把 GR2026 的 `xqe.jks`（SHA-256 `2c019576a5b746b30074057249735a9f7780876e3d34eb06672a6764d849cda9`）复制到 `E:\Work\Sesame-M\xqe.jks`（项目根目录，与 GR 存放位置一致）。
- **没有**照抄 GR 把密码硬编码进 `build.gradle` 的做法（那正是 GR 自身这把钥匙泄露的直接原因）。改为新增未跟踪的 `keystore.properties`（`storeFile`/`storePassword`/`keyAlias`/`keyPassword` 四个字段，值与 GR 一致），[app/build.gradle](../app/build.gradle) 里新增的 `signingConfigs` 块在构建时读取这个文件；文件不存在时（比如 CI 或其他贡献者本地未配置）跳过 release 签名配置，构建仍能跑完，只是产物不签名，不会因为缺文件而炸构建。
- [.gitignore](../.gitignore) 原来 `*.jks` 是被注释掉的（`#*.jks`），本次取消注释并新增 `keystore.properties` 忽略规则，确保这两个文件不会重演 GR 那次"密钥进 Git"的问题。`git status --short` 确认这两个文件不出现在待跟踪列表，`git check-ignore -v xqe.jks keystore.properties` 确认命中新增的忽略规则。

**验证**：`./gradlew signingReport` 的 `normalRelease` variant 正确解析出证书指纹（`SHA-256: 46:CC:...:12:E2`，`Valid until` 2124 年），确认签名配置生效、密钥文件可被正常加载。未执行 `assembleRelease` 完整打包，未验证产物 `apksigner verify` 通过；未检查这把私钥是否也被 Sure-Xu 或其他同源 fork 使用（不排除同一把钥匙已经被多个 fork 共用）。

**后续如果要改成独立密钥**：删除 `xqe.jks`/`keystore.properties`，用 `keytool -genkeypair` 新生成一把，`keystore.properties` 格式不用改（同样四个字段），`build.gradle` 的读取逻辑不用动。

### 2026-09-12：移植 MyUtils 通用部分，新增 `util/MyUtils.java`

参考：`E:\Work\Gr\Sesame-GR2026\app\src\main\java\io\github\lazyimmortal\sesame\util\MyUtils.java`（GR2026 当前基线，`main_my` 分支）。

**移植了什么**（新增 [MyUtils.java](../app/src/main/java/io/github/aw1y2z/sesame/util/MyUtils.java)，方法名与 GR 保持一致，便于以后按名字映射调用点）：

- `getInstance()`：GMT+8 日历，委托给新增的 [TimeUtil.getInstanceGMT8()](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java)，没有重复实现。M 原有的 `TimeUtil` 其余方法仍用系统默认时区 `Calendar.getInstance()`（95/152/168/176/185/202 等行），本次**没有**把全部日期计算改成 GMT+8——那是更大范围的改动（参考 Sure-Xu 的 GMT+8 修复），只是先把统一入口补上，供新合并的代码使用。
- `newJSONObject()` / `newJSONObject(String)`：null/非法 JSON 输入返回空 `JSONObject`，原样保留 GR 的容错语义（含"吞掉解析异常"这一点，调用方如果需要感知解析失败，不能直接用这个方法）。
- `recordUserName()` / `mUidMap`：UID 转可读昵称，纯日志可读性。SharedPreferences 名称改成 `sesame_m_myutils`（GR 用的是遗留命名 `XQE_UID`，没有照搬，避免和其它同源分支的本地存储混淆）。
- `getSpFunctionError()` / `setSpFunctionError()`（对应 GR 的 `getSp功能异常`/`setSp功能异常`）：按 App 版本隔离的一次性功能异常标记机制本身移植了，但**没有**移植 GR 那些具体的 `_访问被拒绝*`/`_系统出错正在排查*` key 常量——因为 M 还没有对应的 `MerchantService`/`ConsumeGold` 等调用点，等以后真的合并这些模块时再按需加 key。
- `closeVerification()` / `closeErrorFunction()` / `closeUnRpc()`：GR 里是硬编码 `return true`，这里改成读取新增的 `AppConfig.closeVerification/closeErrorFunction/closeUnRpc`（默认 `true`，与 GR 行为一致，但用户可在配置里关闭）——**没有**原样抄硬编码函数，理由见附录第二节。

**明确没有移植**：

- `getAppTitleExt()`（按包名前缀 `"kt"` 返回 `"GR"` 后缀）——GR 品牌相关，M 的 applicationId 不匹配，无意义。
- `_不是有效的入参` 硬编码任务 ID 黑名单——版本/账号特定快照，直接抄会过期甚至误伤。
- `encryptData()`/`decryptData()`——GR 的 `MyUtils` 版本是直通 no-op，真实加解密在 `AESUtil.java`（`//CHANGE BY KT` 标记），没有合并对象所以本次不涉及；下次合并任何调用了这两个名字的代码时，必须先确认源头是哪一个版本，见附录表格。

**当前状态**：`MyUtils.java`、`AppConfig` 新增字段、`TimeUtil.getInstanceGMT8()` 已加入，`./gradlew compileNormalDebugJavaWithJavac` 编译通过（仅有 5 条与本次改动无关的既有 Lombok `@Getter` 冲突警告）。写下本条记录时**尚未接入任何调用点**，属于死代码；已在同日的下一条记录「接入 MyUtils 调用点」里补上了 `recordUserName`/`newJSONObject`/`closeVerification`/`closeUnRpc` 的实际调用点，`closeErrorFunction` 和 `getSpFunctionError`/`setSpFunctionError` 仍未接入，见该条说明。


### 2026-10-05：合并 MIUIX-api102（来源 54fdca8a）

- 合入 25 个主线提交：黄金票、农场抽抽乐、森林场景任务/保护罩续用、运动随机间隔、任务交易过滤、黑白名单语义、状态栏能量与运行模块、首页刷新及配置搜索/批量选择。23 个冲突文件按分叉点核对后组合处理；GeminiAI 与原配置 id/令牌、账号统计隔离、TaskLifecycle、庄园道具补领、按场景施肥及单选次数编辑保留。
- GMT+8：最终新增/修改代码逐文件检查；日志/文件日期与闹钟显示显式用 GMT+8，绿色经营带 Z 的服务端时间先按 UTC 解析，再由 GMT+8 周判断；没有新增默认时区业务日历。
- JSON 创建：黄金票、场景任务/保护罩和抽抽乐响应统一 MyUtils.newJSONObject，成功状态和必要字段缺失时停止/跳过。例外保留 TaskAlternative.doFarmTask 的严格构造（解析失败必须阻止登记已触发任务），以及 AntMemberRpcCall.check 的严格构造（解析失败返回 false，不得误判在线）；空数组和请求数组构造保留。隔离检查桩直接构造 JSON 不属于业务路径。
- JSON 读取：修改文件去除注释后未发现 org.json 裸 get*；组合保留已有 opt 判空，补场景/任务标识、保护罩到期时间、黄金票签到/任务结构/兑换数量及列表元素校验，避免缺字段误记完成或发无效请求。保留既有注释中的旧 get 示例。
- 合并复查补修：未知组队状态仍用三态判断，mainMember 优先判组队；本轮能量在切号卸载时清零并与累加同步；分组分发纳入 TaskLifecycle；S2 异常不回退公益捐蛋；金额与交易关键字不依赖设备语言。日志保留账号目录和验证码双写，模块结果按上游只入分类文件；白名单只拦自动添加，不删用户手动黑名单。
- 跳过：自动睡觉函数在本分支已移除，仅因上游改日志产生删除/修改冲突，未恢复；施肥全局每日次数及重复批量辅助函数未引入，沿用 M 按场景配置和已覆盖的批量逻辑。打包/签名文件无改动，未跑 Release。
- 验证：AGENTS.md 原 16 项检查加 check_upstream_merge 共 17 项通过，Debug Java/Kotlin 编译通过；新检查在上游缺字段实现上失败、合并修正后通过。重生成配置表并保留全局配置节（26 个模型、331 个 ModelField）。未真机验证黄金票、抽抽乐及保护罩服务端效果，未推送远端。


### 2026-10-05：再次合并 MIUIX-api102（来源 5b26bca5）

- 从 54fdca8a 合入新增 7 个提交；5 个冲突文件按分叉点核对，组合保留 M 的安全读取与上游新逻辑。芝麻粒任务采用 join → feedback → push，并回读服务端完成度；权益商店查询 5 个分类并翻页；真爱/组队合种修复重复浇水和当日记账；施肥限额覆盖单次/一键 5 次，负进度差不误记限额；偷榜增加捐献上限，蛋不足以超过第一名时跳过。
- GMT+8：逐文件核对最终 6 个生产代码文件；无新增默认时区日历或日期解析。庄园周日偷榜仍走 MyUtils.getInstance，TimeUtil 的日期显示显式用 GMT+8；没有新增服务端 UTC 时间解析路径。
- JSON 创建：新增/修改业务响应统一 MyUtils.newJSONObject。空/无效浇水应答及状态字段类型错误不扣当日额度、不保留真爱标记；施肥应答缺有效进度停止本轮，不误记当天受限。例外保留原 AntMemberRpcCall.check 的严格构造，解析异常必须返回 false，防止误判在线；森林 RPC 中既有空对象请求拼装保留。检查桩直接构造 JSON 不属于业务路径。
- JSON 读取：最终修改文件去除注释后无 org.json 裸 get*；Map/List/Calendar/RuntimeInfo 的 get 不作替换。补嵌套对象、数组元素及必要字段校验：权益缺 SPU/SKU 标识不入表，任务回读列表缺失/元素无效不报完成、不记停试和自动黑名单，最近记录必须匹配任务且明确未完成并有字符串 recordId，偷榜排行无有效第一名时不捐蛋。
- 保留与文档：GeminiAI/AnswerAIInterface、GEMINI 选项和原配置 id/令牌保持；M 的账号隔离、TaskLifecycle、按场景施肥配置保持。新增 competitionStealLimit 后重生成配置表并保留全局配置节（26 个模型、332 个 ModelField）；README/手册数量同步，分类日志说明按 M 实際输出保留 tag，验证码双写说明保留。
- 验证：17 项本地回归全部通过；check_upstream_merge 新增真实生产方法隔离检查，覆盖任务上报/回读/旧记录复用、5 分类分页与翻页上限、浇水成功/永久失败/可重试/畸形应答、批量施肥限额与缺进度、偷榜蛋不足/捐献上限。最后的响应类型补校验已重跑该检查及 Debug Java/Kotlin 编译通过。打包/签名文件无改动，未跑 Release；未真机验证服务端链路，未推送远端。


### 2026-10-05：准备 v1.2.8 正式版

- gradle.properties 的版本号由 1.2.1 改为 1.2.8；沿用基于 Git 提交数的 versionCode 和现有 arm64-v8a 签名打包流程。相对 v1.2.1 标签核对提交和最终源码，新增 docs/发布说明-v1.2.8.md，包含功能变更、旧混合统计保留/分账号从零统计、竞赛配置调整、日志分类与 HTTP 令牌要求；INDEX 登记。修正使用说明中此前数量替换误改的可重试错误码，按 MessageUtil 恢复为 3000。
- GMT+8、JSON 创建与 JSON 读取：本次只修改版本属性和文档，无业务日期处理及 JSON 生产代码变更，无新增例外。
- 验证：全量 17 项本地回归、Debug Java/Kotlin 编译及 assembleNormalRelease 通过，R8 混淆完成；apksigner 验证 APK v2 签名通过，证书 SHA-256 与旧 1.2.1 APK 一致；aapt2 确认 versionName=1.2.8、ABI=arm64-v8a。最终发布包从本次版本提交重新构建，以更新内嵌 Git 提交信息和 versionCode；未逐项真机验证新业务链路。


### 2026-10-05：版本更新说明统一归档

- 将独立发布说明重命名为 docs/版本更新说明.md，使用二级标题区分版本、三级标题区分更新类别；最新版本置顶，现有 v1.2.8 的内容完整保留。README/INDEX 更新入口，AGENTS/INDEX 记录以后每次更新版本在文件顶部追加说明、保留旧版的维护规则。
- 纯文档变更，GMT+8、JSON 创建与读取均无业务代码改动。核对版本倒序、原说明正文和引用通过，git diff --check 通过；未重复运行业务回归与构建，v1.2.8 标签和正式 APK 保持原发布提交。


### 2026-10-06：农场游戏时长任务与阅读授权分析（未提交）

- 来源：SJ 3.6.14 APK 本体未包含游戏时长与无纸阅读实现；外部导出目录标注另一个 Normal APK，不认定为 SJ 源码。新农场协议参考待核实 smali，结项的当前 userId、source、version 格式对照 SJ 本体既有 finishTask；源码差异和阅读前提见 docs/SJ功能移植分析.md。
- 接入：AntOrchard 新增默认关闭的 receiveOrchardGameStay，首页最多三页与投放位动态提取 FLOAT_BALL_TASK，按场景/任务/游戏去重；重复来源取较长时长，仅成功结项后记录当日完成。未照搬 3–4 秒极速模式，按下发时长等待，秒/毫秒上报一致；单任务超过 30 分钟跳过。每步失败停止当前任务，复用 RPC 保护、不另加退避或普通任务黑名单。奖励数量缺失时不报 0g。
- GMT+8：复用按账号加载、GMT+8 换日的 Status，不另造默认时区日期键。JSON 创建：新增对象均 MyUtils.newJSONObject，空/无效响应判失败。JSON 读取：新增均 opt* 与嵌套判空，任务标识必须为非空字符串、下发时长必须为有效整数，缺字段或类型异常跳过；无新增例外。
- 切号/取消：同步执行并持有 TaskLifecycle 准入，TimeUtil.sleep 检查代际，取消异常向上传递；无新线程。现有 submitUserAction 在全仓无业务调用，新流程接入并对齐事件中的 paladinx 版本、补安全转义；保留原固定 32 秒时长接口并增加按任务时长的重载。
- 阅读仅分析：导出代码请求 auth_base。M 授权助手自建宿主服务实例，已有记录指出未注入依赖；需验证正常小程序登录上下文或已初始化的真实服务实例，手动点授权不能保证修复模块获取链路。当前模拟器未装支付宝，未修改共享授权助手、未增加阅读开关或发送第三方登录请求。
- 验证：18 项本地回归通过，Debug Java/Kotlin 编译通过（既有弃用等警告）。新增 check_orchard_game_stay 编译生产任务/RPC/TaskLifecycle，验证分页、双来源去重、同游戏不同任务、单位和转义、账号/日期隔离、缺字段/类型错误、各步骤及结项失败、未知奖励数量、RPC_SKIPPED、取消/冻结；最终补类型校验后重跑该检查及 Debug 编译通过。配置表重新生成，手册与回归入口同步，git diff --check 通过。未实机验证领奖、未打包、未提交、未推送。

### 2026-10-06：农场游戏时长改为默认开启（未提交）

- 按用户要求将 receiveOrchardGameStay 默认值由 false 改为 true；已有账号保存的值沿用配置加载/合并机制，不强制覆盖。此条取代前一条记录中的默认关闭说明。同步配置表、使用说明、分析文档及回归默认值断言。
- GMT+8、JSON 创建与 JSON 读取：本次仅修改布尔默认值及文档，无相关处理改动，无新增例外。
- 验证：农场游戏时长回归、配置合并回归与 Debug Java/Kotlin 编译通过，配置表重新生成，git diff --check 通过；未打包、未提交。

### 2026-10-06：森林无纸阅读接入（未提交）

- 按用户要求新增 readForest（「无纸阅读」），默认开启、按账号保存，接在正常森林任务末尾；能量时段不执行。协议仅参考外部导出文件，SJ APK 本体没有此功能的佐证。接入登录、免费书筛选/去重、章节时长上报、最近阅读及服务端能量回查；保留导出每章 333–1000 秒的时长字段和短间隔，不等同于实际阅读或等待该时长。永久保存完读书籍，连续五章无增长、重复章节、三十章上限或请求失败停止本轮；仅服务端确认有效满额才记当天完成，取消 150 兜底。
- 授权根因：共享 AuthCodeHelper 从 RVProxy 获取宿主提供的 Oauth2AuthCodeService，替换自行 new 未注入 facade 的实现；请求模型仍自行创建、scope 保持 auth_base，不增加权限或手动 Token 配置。加入 TaskLifecycle 准入与代际检查，不缓存服务/凭据；授权失败只写脱敏类型，旧 HTTP 路由及 GameTask 注释同步更新（游戏业务行为未另行修改）。宿主代理的实际注册/初始化和第三方接口仍待验证。
- GMT+8：复用 Status 的按账号加载和 GMT+8 日切，完读书籍复用按账号 RuntimeInfo，不新增默认时区日期键。JSON 创建：新增业务对象统一 MyUtils.newJSONObject；无效响应立即失败，空 finishedBooks 仅表示无本地完读记录。JSON 读取：新增均 opt* 与嵌套判空；状态码必须数值 2000，token 必须非空字符串，章节必要字段/类型检查，current 为非负整数、total 为正整数，0/0 不误记完成。RuntimeInfo.getString 是非 JSON API；GameTask 本次仅改注释，原构造未作机械替换。无新增生产 JSON 例外。
- 生命周期/HTTP：同步持有 TaskLifecycle，无新线程；HTTP 前后及读取期间感知代际/中断，取消异常向上传递。凭据只在本轮客户端实例保存；禁自动重定向，10/20 秒连接/读取超时，4 MiB 响应上限，UTF-8 并关闭流/连接，不记录原始请求/响应或凭据。使用系统 UA 与宿主实际版本；第三方 HTTP 不属于 RpcBridge/RpcRequestGuard，本轮无自动重试。
- 验证：全量 19 项本地回归通过，最终 Debug Java/Kotlin 编译通过（既有弃用/Gradle 警告）。新增 check_forest_read 编译真实共享授权助手、任务、HTTP 与生命周期，模拟宿主代理及 HTTPS，覆盖基础 scope、免费筛选/跨书/去重、永久记录、满额/账号/日切、无增长/循环/上限、异常响应/类型/0 限额、各 POST/HTTP 失败、授权不可用、冻结/取消和资源释放；配置表重生成至 334 项，保留全局配置节，手册/来源文档/回归入口同步，git diff --check 通过。未访问真实登录接口，当前模拟器未安装支付宝；授权、miniMark 必需性及能量到账待实机验证。未改版本、未打包、未提交、未推送。

### 2026-10-06：两项移植功能的运行状态与 Token 指引（未提交）

- 按用户要求补运行日志：阅读的开始、登录/自动获取 Token 成功、能量增加、满额、未满额结束、授权/HTTP/业务/字段/网络失败、跳过和取消均写 Log.record，并保留森林分类。农场每个时长任务成功/奖励数量双写运行与农场分类；运行日志记录发现数量、失败任务/步骤/状态码/服务端原因、整轮成功失败数量、已完成/开关关闭/账号状态/取消等。首页或投放位缺列表/查询失败单独说明，不能报正常无任务；其它模块的全局日志分流保持。
- Token 指引：未取得授权码、登录未返回有效 Token、HTTP 401/403 或服务端提示凭据/登录/授权问题，附「打开对应阅读小程序（appId）→正常登录/授权→等待或重新运行森林任务→M 自动换取 Token，无需复制/填写」。宿主不兼容明确需要反馈日志，不能保证反复授权可解决；并未新增手动 Token 配置或改动实际授权范围。共享助手将当前调用线程的脱敏原因提供给阅读，区分未初始化、类/方法不兼容、服务未就绪和空结果，避免重复错误去重或其它游戏并发覆盖本轮诊断。
- 失败详情与隐私：阅读自己的结构/状态错误携带可展示的 ReadFailure 原因；网络异常按超时/DNS/TLS/连接失败映射为固定文案，不输出原始异常消息。服务端说明只取字符串字段，已知凭据及 token/auth_code/authorization 等赋值先脱敏后截断至 96 字；不记录原始响应、授权码、Token 或 miniMark。农场复用 RpcRequestGuard.errorMessage，不再在业务失败日志整段输出响应。
- GMT+8、JSON 创建与读取：沿用 Status/RuntimeInfo 账号与 GMT+8 日切，无业务日期变更；新增/修改解析仍用 MyUtils、opt* 和嵌套判空，新增查询列表缺失诊断，不新增 Gson/裸 JSON get 或构造例外。只改日志与失败诊断，不改时长上报、实际领奖条件或配置默认值；取消继续向上传递。
- 回归：19 项本地回归通过；原两项功能检查增加独立运行/分类日志通道断言、成功奖励/满额与失败阶段/服务端原因、Token 获取指引、网络错误和凭据脱敏（含 JSON 文案与长消息），不请求真实账号或服务器。首次全量检查/补充编译遇 Windows 页面文件不足导致 JVM 无法启动；仅本次命令限制 JVM 内存、采用 SerialGC 顺序重跑，全量通过，未修改项目 JVM 配置或终止其它 Java 进程。真实授权与到账仍待实机验证，未打包、未提交。
- 最终 Debug Java/Kotlin 编译通过：限制启动器内存并使用 --max-workers=1，复用既有 Gradle 守护进程；临时小内存单次守护进程曾因页面文件不足退出，未修改任何构建/签名配置。GMT+8/JSON 复查与 git diff --check 通过，清理本轮生成的 JVM 崩溃日志，保留原有崩溃日志。

### 2026-10-06：移植代码提交归档

- 上述四项实现阶段合并提交为 `66aee698b9716f7b3a43e58dc676a8f37a6ef497`（my_dev）。顶部摘要改为最终行为与实际提交号；前述「未提交/未推送」描述的是各阶段验证时的状态，阶段默认关闭由后续默认开启取代。此归档仅修改变更记录，业务源码与已通过的 19 项回归、Debug 编译保持一致。

### 2026-10-06：配置搜索与原位置开关状态不一致（未提交）

- 根因：三级配置页将字段放在 CardColumn 内按位置 forEach 渲染，没有为字段绑定 Compose key；搜索或依赖过滤改变顺序后，FieldItemBody 的 remember 开关状态会被其它字段继承。模拟配置中巡护实际为 false、前一个开关为 true，旧代码搜索后巡护错误显示 true，已用真实 Compose 运行时复现；不据此认定用户磁盘中的实际配置，也不把现象归因于服务端巡护接口。
- 修复：复用已有 modelCode 与 GroupFieldsRow.key，为模型卡片和字段行提供稳定身份，让开关、展开状态和编辑状态随对应字段移动。配置字段编码、默认值、setObjectValue 写入和返回保存流程保持不变；本次未修改森林 RPC 或兑换/动物派遣业务。
- GMT+8：仅调整 UI 组合身份，没有新增日期/时段处理。JSON 创建/读取：未新增或修改 JSON 操作，仍由原配置加载/保存机制负责；无例外与新增 Xposed 引用，GeminiAI 保留。
- 验证：新增 check_config_search_state 抽取生产字段循环和 Boolean 渲染代码，使用本机缓存的真实 Compose 运行时/编译插件，只隔离 Android 线程/trace/parcel 与界面边界；覆盖搜索、清空、重排、空结果、筛选后切换与不同字段写入隔离。修复前断言失败，修复后通过；移除字段 key 的反向检验仍复现同一问题。20 项本地回归和 Debug Java/Kotlin 编译通过（原有弃用/检查脚本警告），git diff --check 通过。尚未在用户设备上验证，未打包、未提交或推送。

### 2026-10-06：C158 异常日报 20 次失败（未提交）

- 来源：用户 Desktop/芝麻粒M配置/rpc-failures.2026-10-06.C158.json，GMT+8，20 条各 1 次。400000040 不支持 RPC 6 次；doFarmTask 的 102 开小差 3 次；promiseActivityExtCheck 入参校验 6 次；已有进行中记录 1 次；收能量限频、金豆任务已完结、快递任务实例无效、neverland 网络 48 各 1 次。报告没有新版保护地接口，不能据此证明其已执行或失败。
- 对照：GR 未提供这 6 个动态任务的明确失效名单；GR 的 Promise recordId 过滤是旧记录，不能套用到本日报的新记录。当前 M 的历史实测和 TaskAlternative 明确说明 400000040 只表示主接口不支持，备用 doFarmTask 回 102 仍可能生效。因此仅封主调用路径，任务保留备用完成与列表核对，不能把 102 当作整个任务永久失效。
- 精确规则：仅 com.alipay.antiep.finishTask + ANTFOREST_VITALITY_TASK/LSHS_huisho20_202508；ANTSTALL_TASK/ANTSTALL_TASK_XCXYX_zhuzhaishijie、ANTSTALL_TASK_XCXYX_zslxx；ANTFARM_DAILY_DRAW_TASK/cclyx_wdhysj_3c_10、cclyx_sgbhsd_3c_zm10c、cclyx_3bei_zslxx_2。受既有「关闭不支持 RPC 完成的任务」开关控制；不按标题/前缀扩大范围，不拦 receiveTaskAward 或 doFarmTask。
- 备用调用：本地跳过主接口仍以 error/resultCode=RPC_SKIPPED 标记，同时保留 code=400000040，让现有 TaskAlternative.hit 继续选择备用方案；本地跳过不写异常统计、不计自动黑名单失败。仅以上精确任务对应的 com.alipay.antfarm.doFarmTask + sceneCode=ANTFARM + taskSceneCode/bizKey，在 102 且“服务器正在开小差”时按任务/账号 5、5、30 分钟退避；时间到恢复，outBizNo 随机变化不能绕开暂停。其它备用任务不新增退避，庄园领奖既有 6 小时规则保持。
- 暂不处理：Promise 的 6 个 recordId 缺任务名称/templateId、前后完成度，现有 join/feedback/push 后回读、无进展当天停止及连续确认机制保留；PROMISE_HAS_PROCESSING_TEMPLATE 已有旧记录复用。KUAIDI_VITALITY 已有失败当天停止，报告仅 1 次；挖矿“任务已完结”仅 1 次，没有当前任务列表不判永久失效。neverland 网络 48 沿用退避；收能量限频仅 1 次，不新增整轮/全天停收。未修改用户账号配置文件。
- GMT+8：不新增日历，退避为绝对毫秒并沿用按账号状态和 GMT+8 日报；JSON 创建沿用 MyUtils，新增场景/任务读取为 optString，数组解析保留原判空和异常处理，无新 JSON 构造例外。无新线程、UI/Xposed 依赖或 GeminiAI 删除；MyFix.md 不写事件日志，按长期规则仅更新本 CHANGELOG。
- 验证：check_rpc_guard 新增真实生产守卫 + TaskAlternative.hit/isUnsupportedRpc 的精确规则、备用/领奖保留、无关路径/偏好关闭、统计排除与任务/场景/账号隔离、5/30 分钟到期回归；修复前因主接口仍被发送而断言失败，修复后通过。20 项全量检查通过；收窄备用退避范围后 RPC 检查重跑通过，最终 Debug Java/Kotlin 编译通过，git diff --check 通过（既有脚本注解处理/弃用警告）。未请求真实支付宝接口，未打包、提交或推送；之前配置搜索修复保留在工作区。

### 2026-10-06：v1.2.9 正式包与提交归档

- 用户授权提交、推送和重新打正式包。配置搜索修复提交为 `95e3c17f`，C158 精确主接口跳过与备用短退避提交为 `f62e26c2`，顶部摘要补齐提交号；前两节「未提交/未推送」是验证阶段的历史状态。
- 版本由 1.2.8 更新为 1.2.9，发布说明统一追加至 `docs/版本更新说明.md` 顶部。包内包含 `66aee698` 的两项默认开启功能及运行日志、授权指引，保留已有账号配置值；不将芝麻信用缺证据的校验失败扩大为永久封禁。
- 本轮重新执行 20 项完整回归均通过；更新版本后 Debug Java/Kotlin 编译与启用 R8 的 `assembleNormalRelease` 通过。apksigner 验证 v2 签名有效，证书 SHA-256 与 v1.2.8 一致；aapt2 核对包名、1.2.9 版本和唯一 arm64-v8a ABI，ZIP 完整性通过。发布提交后重新生成正式产物，以同步 git 提交号及 versionCode；产物由原任务按 GMT+8 归档至 `APK/Release/`。
- GMT+8：沿用现有构建时间及归档的东八区格式，无业务日历变更；JSON 创建/读取：发布阶段仅改版本值与文档，无新增 JSON 操作或例外。签名、混淆规则和项目 JVM 配置未改，GeminiAI 保留；仅本轮命令限制启动器内存并单 worker 构建。新功能授权兼容性和实际到账仍待实机确认。

### 2026-10-06：C158 无纸阅读首次进度查询停止（未提交）

- 实机证据：用户提供的 `runtime.2026-10-06.C158.log` 中，11:14:27.249 与 11:20:00.620 两次登录成功并自动取得 Token，随后均因 current/total 无效停止；日志没有字段原值或类型，没有进入书籍/章节/阅读上报。宿主 H5HttpUtils 缺失但登录成功，不能据此断言 miniMark 是本次进度失败的原因。农场两项已结项，各返回 1000g 肥料；用户补充后续仍在执行，农场代码不变。
- 对照发现：参考导出 Java/smali 对 data.current/total 使用 optInt，能读取数字字符串；M 移植时仅接受 Number，合法字符串也被拦住。离线生产代码回归复现该兼容缺口，但原日志不能证明实机恰好返回字符串。参考还在章节上下文查询进度，M 当前首次使用首页上下文；缺少服务端应答证据，本轮未推测改请求顺序/Referer。
- 处理：复用原流程，允许非负整数形式的字符串并归一化为数值；数值使用 BigDecimal.intValueExact 检查，拒绝小数、负数、越界与非数字，total 必须大于 0。失败仅输出 data/current/total 的固定类型名，不输出响应、字段原值、Token 或授权码；不能确认满额仍不记当天完成。
- GMT+8：无新增日历/日期操作，沿用 Status 日切及账号准入；JSON 创建使用 MyUtils.newJSONObject，读取为 opt/optJSONObject，嵌套对象判空，无直接构造例外。无新线程或独立 App/Xposed 依赖，GeminiAI 保留。
- 验证：新增数字字符串/混合数值/空白与前导零/整数上界、从 0 阅读到满额、精确小数/溢出/空值/布尔/对象/数组/零上限和日志脱敏检查；修改前因合法字符串被拒绝而失败，修复后阅读回归通过，农场回归也通过。Debug Java/Kotlin、Release/R8 构建及签名校验通过；排查包为 `APK/Release/Sesame-M-Normal-arm64-v8a-1.2.9-read-check_20261006_122802.apk`，证书与正式 1.2.9 一致，ZIP、版本和 arm64-v8a 核验通过，副本放桌面配置目录。以命令行版本 `1.2.9-read-check` 区分已发布 v1.2.9，未覆盖正式包、未改正式版本、未提交或推送。首次构建的未加引号 -Pversion 参数被 PowerShell 拆成任务名，退出后加引号重跑成功，未改构建逻辑；实机是否继续阅读及到账仍待验证。

### 2026-10-06：无纸阅读持续获能至满额（未提交）

- 用户提供实机「达到 30 章上限；今日 59/150」日志并要求优化。移除固定章数停止，仍按服务端实际 current/total 判断满额，不硬编码 150；保留连续五章无增长、章节循环、无免费书/章节、HTTP/业务异常、取消/切号等停止规则，未满额不记当天完成。
- 复用项目已有的 SystemClock.elapsedRealtime 单调时钟，单轮从授权开始约 15 分钟；进度查询后、首次/后续章节请求前和上报前检查时限，日志写已处理章数及当前/上限，下轮仍可尝试。已发出的 HTTP 请求仍沿用原连接/读取超时，回读已经满额时优先确认完成。无新配置字段、线程、重试层或阅读进度持久化机制；此前数字字符串兼容和诊断保留，农场未修改。
- GMT+8：运行时限使用单调经过时间，不依赖墙钟或设备时区；业务日切继续沿用 Status。JSON 创建仍用 MyUtils，读取使用 opt*，嵌套判空；无新增例外、独立 App/Xposed 依赖，GeminiAI 保留。用户手册和 SJ 接入说明同步当前停止条件，并补实机获能反馈，不改历史变更记录。
- 回归：生产阅读任务修改前，新增 59/150 连读 91 章至满额场景在 30 章处失败；修改后通过。另覆盖超过旧限制后连续五章无增长、首页查询后已超时不再查章节、读后超时不再查下一章、时限边界已满额仍记完成，保留原授权/账号/类型/HTTP/取消回归。完整 20 项检查通过：前 19 项通过，最后 upstream_merge 因本机原生内存不足退出，顺序限制 JVM 128m 重跑通过；清理仅本轮生成的 pid5080 两个崩溃/回放日志。用户要求正常版本后，仅停止本轮 read-full 构建启动器；未终止其它既有 JVM。正常 1.2.10 的 Debug Java/Kotlin 编译通过；512m 临时堆不足使 R8 失败，恢复项目原有堆设置并单 worker 重跑 Release/R8 成功，原项目 JVM 配置未改。签名与旧 1.2.9 相同，ZIP、ABI、版本和包内停止条件核验通过；最终发布提交后会重新生成 APK。

### 2026-10-06：v1.2.10 正常版本发布归档

- 用户确认农场游戏时长和阅读已正常，并要求去掉排查版本后缀；正式版本 1.2.9 → 1.2.10，保留原签名及 arm64-v8a，发布说明在统一文件顶部追加。阅读解析兼容和持续获能优化归档为 `517bcde2`，前两节「未提交」描述的是各验证阶段的历史状态。
- GMT+8、MyUtils JSON 创建、opt* 读取检查及 20 项回归结果见前节；本次发布阶段只改版本值和文档，无业务时间/JSON 新改动，无例外。Debug/Release/R8 和签名已核验，发布提交后重建产物以同步提交号及 versionCode，并沿用 GMT+8 归档任务；农场代码未改，GeminiAI 保留。

### 2026-10-06：参考项目目录与优先级更新

- 按用户说明，后续主要参考 AG（`E:\Work\Sesame-AG`）和 XU（`E:\Work\Xu`）；GR2026 是基本不再更新的旧版本，仅按需作历史对照。更新 AGENTS.md、docs/MyFix.md、docs/GR-Sync.md 的来源说明及本地路径，历史记录中的路径同步更新，历史移植来源名称保留。
- 仅修改文档，无业务时间、JSON 创建/读取、配置或打包改动；核对新目录及引用文件存在，旧本地路径无残留，执行差异格式检查，不运行业务回归或重新打包。

### 2026-10-06：参考 XU 增强农场抽抽乐（15f81147）

- 来源为本地 Xu 的 `4c7fb9e`（轮盘任务、游戏匹配及批量抽奖），对照当前 `d140eee` 源码；增强 M 现有 orchardChouChouLe，不另加轮盘开关，原默认值及账号黑名单配置保留。施肥次数、回访奖励、已实机正常的游戏时长和阅读不改。
- 活动解析统一兼容 drawSceneGroups、drawScene 和顶层 drawActivity，任务同步入口也复用；任务字段兼容 taskBaseInfo 与 iepTaskTracer。请求 appMode 对齐 XU 的 student，游戏匹配复用 M 的 GameTask，仅已登记游戏可上报，上报成功仍回查服务端任务，不直接认定抽奖权益到账。任务状态无进展时不重复尝试，保留七轮回查上限，未知游戏跳过但不自动拉黑。
- 同步并校验服务端 blance 后一次批量抽奖，移除原逐次 30 次上限；响应畸形、次数小数/负数/溢出或接口失败则停止该场景，不追加单抽兜底以避免重复消耗。运行日志记录开始、跳过、上报、状态未推进、领奖、抽奖及失败步骤/状态码/原因；成功记录同步农场分类，奖励明细缺失时明确说明。
- GMT+8：无新增业务日历/日期操作，outBizNo 沿用毫秒时间戳；JSON 创建使用 MyUtils.newJSONObject，读取 opt* 并判空，余额精确校验，无新例外。长任务使用 TaskLifecycle 准入，取消继续向上抛，无新线程、独立 App/Xposed 依赖；GeminiAI 保留，原单抽 RPC 留作兼容接口。
- 验证：新增 check_orchard_draw 编译生产流程与 RPC 构造，修改前顶层活动用例失败，修改后新旧结构、超过 30 次批量、请求转义、游戏回查/不推进、未知及黑名单游戏、错误次数、接口失败/余额错误和切号/取消检查通过。完整 21 项回归通过；Debug 首次编译指出 Android JSONObject.put 的受检异常，按现有 RPC 签名补齐声明后重跑抽抽乐/游戏时长检查和 Debug Java/Kotlin 编译均通过。按用户要求版本保持 1.2.10，不递增或添加排查后缀；无新增配置字段或签名/构建规则修改，未发布 APK，实际推进和到账待实机验证。

### 2026-10-06：庄园自动喂鸡限次重查（15f81147）

- 参考 XU e545bea 的自动喂鸡重查与进食时间保护，保留 M 原投喂开关和喂食后 10 秒检查；尚未恢复进食、查询失败、字段缺失、速度无效或剩余时间异常时每隔 30 秒重查，连续最多 5 次，随后等待下一轮庄园任务，不持续轮询。重查期间不重复创建同一条链，不直接重新喂食。
- 蹲点计算只读取本次成功响应，校验庄园和自家小鸡，避免失败时沿用缓存状态；毫秒计算保留小数精度并排除非有限值/溢出。延迟回调捕获账号与庄园，执行前复查开关及身份；使用现有 ChildModelTask 的 TaskLifecycle 准入，取消异常继续抛出。重查使用不同 ID，避免执行器旧回调按 ID 清理时误删新排期。运行日志记录原因、次数、恢复时间、达到上限或身份/开关变化。
- GMT+8：排期使用毫秒时间戳，显示复用显式 GMT+8 的 TimeUtil.getCommonDate，无默认时区日历；JSON 创建统一 MyUtils，读取 opt* 并判空/校验必要字段，无例外。无新增配置、独立进程依赖或打包/签名改动，GeminiAI 保留，版本仍为 1.2.10。
- 验证：新增 check_antfarm_auto_feed 编译实际生产排期方法，旧实现用例失败，新实现状态恢复、五次上限、不重复排期、失败/畸形返回、时间精度、账号/庄园/开关变化及取消场景通过；完整 22 项回归及 Debug Java/Kotlin 编译通过。实际接口状态恢复及投喂效果待实机验证，未发布 APK。

### 2026-10-06：1.2.10 同版本正式包重打与发布说明汇总

- 基于功能提交 15f81147，按用户要求版本继续使用 1.2.10，不添加排查后缀。版本更新说明顶部汇总 1.2.8～1.2.10 的森林、农场、庄园、会员、配置与稳定性改动，保留旧版本段落；按用户澄清保留多账号功能说明，仅去除具体账号标识，并检查 UID、手机号等信息无残留。
- 本轮重新跑完整 22 项回归及 Debug Java/Kotlin 编译通过；正式包沿用原 Release/R8、arm64-v8a 和签名配置，提交后构建以同步提交号与构建信息，并核验 APK 版本、签名及归档文件。无业务时间、JSON 或构建/签名规则新增改动，无例外；新流程仍待实机验证。

### 2026-10-06：参考 AG 逐项补森林、庄园和农场（0a8b3a6f）

- 对照本地 AG `c24e4908`。优先移植 `df02ce63` 的新版动物到期替换/二次确认、`125bc133` 的好友独立克数、`3ef1063b` 的努力流星/宫格访问，并按当前 AG 实际调用链补淘宝和 XLight 浏览；再接入 `a70a3127` 的篱笆卡、公仔补签，以及日记点赞、当前地图证书兑换和会员绿植浇水。协议及业务依据来自 AG 源码，按 M 原任务/配置/RPC 机制改写，不合并 AG 整个框架。
- 动物只有确认工作天数到期时才换；状态缺失保留当前伙伴，二次确认仅用于已确认到期替换，临时占用变化不强制替换。派遣后回查。好友独立克数新增独立映射，直接输入合法档位，0/未设置沿用原配置；原全局关闭、每日次数及剩余次数记账保留。
- 努力流星按独立 taskInfos/bizInfo 执行普通任务和具有有效游戏时长依据的任务，并同步到原农场黑名单选项；宫格/淘宝使用下发来源，广告使用 BROWSE 事件。动作后回查阶段或次数，只接受向前推进；次数兼容合法数字字符串，拒绝小数、负数和溢出。不支持、失败、不推进均记录原因，不自动拉黑这些新链路；保留已有用户黑名单。单任务20阶段、广告5页、单次等待30分钟上限。
- 篱笆、公仔、日记点赞、证书兑换、绿植浇水均默认关闭。篱笆效果尚在时不重复用卡；公仔按缺失月份与库存、所选顺序处理。消耗道具/证书请求先记未确认预算，再回查效果或持有量；响应不确定时当天停止重复消耗。日记完成所选范围后记当天标记，历史最多120个月；绿植每轮50次，必须确认进度增加且水滴减少。新增成功、失败与跳过运行日志，不输出令牌、授权码。
- GMT+8：月份枚举/历史回溯统一 MyUtils.getInstance，Calendar 按月递减，格式化显式 Locale.CHINA；毫秒时间戳不按本地时区解释，无新增默认时区路径。JSON 创建：新/修改请求与字符串对象统一 MyUtils；availablePots 数组无工厂，保留 JSONArray 严格解析与外围异常中止。JSON 读取：新增及修改路径使用 opt*、嵌套判空、成功状态/必要字段/整数成本和进度校验；未使用既有裸 get 的路径工具。无新增 Gson、业务裸 JSONObject 构造或裸 get 例外。
- 无新线程/延迟回调，执行复用 ModelTask 的 TaskLifecycle 准入；长循环设置取消检查点并重新抛出取消异常。独立 App 仅调整克数输入模式，不引用 Xposed；GeminiAI 保留。配置项说明重新生成，使用说明与同版本发布说明同步，明确旧正式包不含本次追加内容。构建/签名和版本号未改，本轮按用户要求提交推送源码，未打包，新服务端链路尚待实机验证。
- 验证：新增 check_ag_features 编译实际生产方法与 RPC，覆盖到期/未到期/缺状态/二次确认竞态、独立克数及剩余次数、证书余额/异常/重复扣减、绿植进度/成本整数、篱笆重复使用、公仔库存/顺序/回查、日记范围/分页/时区、流星任务完成与领奖/黑名单选项/缺执行依据、浏览事件/翻页/整数及数字字符串进度/错误/取消；配置回归补克数新选默认0、编辑18及保留66场景。提交前重新运行完整23项本地回归全部通过，Debug Java/Kotlin 编译通过（14秒），diff 空白检查通过。未执行 Release 构建，新接口效果待实机验证。

### 2026-10-06：对照 AG 删除、迁移和默认关闭功能

- 基于本地 AG c24e4908，核对任务目录、同名布尔字段及近期删除/默认关闭提交：5b62c782（鱼池）、7906f6f8（新村饿了么外跳）、76d8f448（运动旧路线）、5b9b09ac（森林未验证累计奖励）、09d687e7（青春特权迁移）、217def10（默认值）。删除记录不能直接推导为服务下线，源码静态核验未请求实际接口。
- 按用户补充，福气鱼塘是其它来源移植的独立实现，保留模块及配置，不跟 AG 删除；M 的 enableFishAuto / enableFishTaskAuto 原本均为 false，保持默认关闭，不覆盖用户已保存配置。
- M 的新村 taskTypeList 实为自动完成许可列表，饿了么条目会请求通用完成接口；不属于已跳过。删除该许可项，并在两个完成入口共用的 finishTask 中接入 closeUnRpc（原默认 true），精确跳过 ANTSTALL_ELEME_VISIT 与 ANTSTALL_TASK_XCXYX_langmancanting / qingyunjue / sijiwuyu。TODO 不请求完成/备用接口、不自动加黑名单，已完成状态仍沿原任务列表领奖；明确关闭该跳过配置时保留原行为，运行日志说明所需实际事件。
- 运动旧版 queryMyHomePage / openAndJoinFirst / queryBaseList 路径在 M 已无对应代码。M 的 openTreasureBox 实际调用新路线 receiveEvent，不能按同名函数误删；行走路线和健康岛相关业务开关已默认关闭。森林累计奖励的 deferredForestRights 链路在 M 不存在；青春特权森林道具在 AG 迁到独立模块且仍支持，M 原开关 false，保留实现。
- 同名布尔字段中，M 默认 true / AG 默认 false 的差异为 balanceNetworkDelay：M 实际用于提前收取时估计网络耗时，不是失效任务，保留现有默认。找能量、浇水动作和健康岛业务默认已关闭；阅读及农场游戏时长按用户先前要求保留默认开启。金豆功能在 AG 是模块迁移，AI 是框架替换，M 金豆和 GeminiAI 均保留；未移植 AG 通用框架，也未机械复制全部默认黑名单。
- GMT+8：无新增时间处理。JSON 创建/读取：新增跳过分支不创建或读取 JSON，原完成接口的 MyUtils/opt* 路径不变，无例外。无新线程、独立进程引用、配置字段或版本/构建/签名改动；不需重生成字段表。新增 check_ag_retirements 编译实际完成、任务列表与领奖方法：旧 HEAD 会请求饿了么接口而失败，新代码验证四类 TODO 跳过、已完成领奖、已领取跳过、显式关闭保护及其它任务/备用完成保留，并检查鱼塘实现/注册保留及两个业务默认关闭。
- 验证：check_ag_retirements、check_rpc_guard、check_ag_features 均通过，Debug Java/Kotlin 编译通过（50秒），git diff --check 通过。本轮未跑全量24项、未提交推送、未构建正式包；接口实机效果待验证。

### 2026-10-06：健康岛总开关默认开启（用户要求）

- 用户核对后明确要求运动「健康岛｜开启」默认开启，将 neverLand 默认值从 false 改为 true；这是对上条静态审查时默认值的后续调整。仅改变字段初始值，已保存 true/false 继续按账号配置加载；签到、任务、领奖、能量泵和自动切岛等子选项沿用原默认值与保存配置，不开启独立的「健康岛红包碎片兑换」模块。
- 配置项表重新生成，使用说明及同版本更新说明同步。无时间、JSON 创建/读取或线程路径修改，三项规则无新增问题，无例外；版本、构建、签名不变。Debug Java/Kotlin 编译通过（7秒），生成字段表核对 neverLand 为 true、其它字段未变化，git diff --check 通过；未提交推送或打包。
- 提交核验：按用户要求统一归档本节及上节改动，提交前完整24项回归全部通过、Debug Java/Kotlin 编译通过（8秒），新增/修改代码时间及 JSON 规则复查无问题，GeminiAI 保留、版本/构建/签名文件未改，diff 空白检查通过。回归可指定 --baseline c12ca1ae 复现修复前新村错误请求；之前「未提交」为当时验证状态。本次仅提交推送源码，未重打正式包。

### 2026-10-06：逐步移植第1项——N倍卡替换策略（未提交）

- 参考本地 AG c24e4908 的高倍率/临期替换与二次确认链，接入 M 原连续用卡入口。新增 robExpandCardReplaceRemainDays、robExpandCardForceReplaceExpireDays，默认0、范围0～365；仍要求选中原 N 倍卡选项，两项均0时保留旧流程。
- 普通高倍率替换只在剩余天数阈值内且新卡有效期不短于剩余时间时执行；临期高倍率卡按最早过期优先，可忽略普通窗口及有效期保护；无高倍率候选时仍按原30天窗口同倍率续用。始终拒绝低倍率、无库存、已过期、倍率/数量/状态不明的卡；不推断当前卡信息，不兑换补库，不消费没有到期时间的常驻卡。
- 先 secondConfirm=false，只有服务端明确 NEED_CONFIRM*/REPLACE 且回查当前倍率/到期时间未变才二次确认。成功日志以回查效果变化为准；请求前写按账号当日标记，同一道具编号每日最多尝试一次（包括失败/响应不确定），每轮只尝试一张。复用 ModelTask 的生命周期及原 RPC 退避，不加线程；新增取消检查并保留取消异常向上传播。
- GMT+8：阈值按毫秒时差与 TimeUnit.DAYS，跨天标记复用 Status/TimeUtil 的 GMT+8 重置。JSON 创建：新增字符串对象与修改的四参数用卡请求统一 MyUtils，数组使用 JSONArray；JSON读取：opt*判空，成功状态/必要字段/有限正倍率/整数数量与时间/溢出严格校验，无新增裸get/Gson例外。字段表、使用说明、同版本更新说明及桌面移植清单同步；GeminiAI、版本/构建/签名保持现状。
- 验证：check_rob_expand_card 编译实际生产方法，覆盖普通窗口/不缩短有效期、临期优先、不降倍率/同倍率续用、失效与未知字段/库存/整数溢出、空/过期/重复生效状态、二次确认竞态、失败/响应不明/重复提交、取消及请求转义；已加入 AGENTS 的检查清单。完整25项本地回归全部通过，Debug Java/Kotlin编译通过（最终4秒），diff空白检查通过。并行检查一度因宿主JVM默认堆分配不足失败，改为检查进程192MB堆后重跑通过。未提交推送、未打包；真实接口和用户配置启用后效果待实机验证。NPC雇佣、加速/饲料日限额及其余候选仍未移植。

### 2026-10-06：逐步移植第2阶段——普通NPC小鸡（未提交）

- 参考本地 AG c24e4908 的 NPC 配置、handleNpcAnimalLogic/checkNpcReward 及独立雇佣/遣返协议。新增 M 自有 farmNpcType：0关闭、1黄金鸡、2农场小鸡，默认0，不复用 AG 含大表鸽的 npcAnimalType 枚举序号；与好友雇佣设置独立。主任务每轮在好友雇佣前照料 NPC，复用 ModelTask 生命周期及原 RPC 退避，无新增线程/延迟任务。
- 有空闲名额时通过 HIRE_IN_SELF_FARM/isNpcAnimal 尝试雇佣，来源按 NPC 类型下发。资格与资源消耗由该接口校验，不套用好友雇佣的50g门槛或自行扣减饲料。所选NPC未满产时保留；满产才 receiveNPCReward=true 遣返领奖，成功响应且离场回查确认后重雇，再回查到场才记雇佣成功。每轮最多一轮遣返/雇佣，不驱赶好友工人；动作后重新核对名额，不覆盖新到场NPC。
- 切换仅处理无待领奖励且状态明确的黄金鸡/农场小鸡，有产出、满产或未知NPC保留；缺少/畸形/重复动物记录、错误庄园标识、未知类型、无效产出、满产字段不明均跳过。普通sendBackAnimal共用入口明确排除NPC，防止绕过领奖协议。请求前写按账号当日未确认标记，仅在操作响应及目标效果回查均通过时清除；失败/响应不明/离场或到场未确认时当天停止NPC动作，待次日或人工复核，不反复消耗资源。
- 大表鸽需要芝麻炼金的触发授权、88粒待收持久标记和会员侧收取/反馈闭环；M现有会员流程还把雇佣任务列入默认黑名单。本阶段未复制其孤立雇佣接口，单列后续依赖，不把普通NPC完成视为全部NPC功能完成。加速/食品策略及其余移植候选未改，主动移除功能不恢复。使用说明、同版本追加说明、自动配置表及桌面对照/进度/CSV同步；版本、构建、签名不变，GeminiAI保留。
- GMT+8：无新增日历/时间解析，未确认标记沿用Status/TimeUtil的按账号GMT+8每日重置。JSON创建：新增字符串响应、空请求对象统一MyUtils，参数数组使用JSONArray；JSON读取：opt*判空、成功响应/庄园标识/名额/动物身份校验，产出使用BigDecimal与有限数校验，拒绝将正数下溢当0而丢弃待领奖励。满产必须明确Boolean；无新增裸get/Gson例外。取消检查点覆盖查询及请求前，同时修正庄园step/run共用包装吞掉取消异常的问题，NPC取消经两个入口传给ModelTask收尾；普通步骤业务异常隔离保留。
- 验证：按Android测试技能先运行新增check_farm_npc，在缺少配置/入口时失败，再编译生产方法与RPC回放关闭/类型选择、黄金/农场雇佣、满产领取重雇、无产出切换、待领奖励保留、名额变化、缺失/错误/畸形状态与浮点下溢、离场/到场未确认、失败/重复请求保护、取消穿过真实step、请求转义及普通赶鸡排除NPC；下溢/包装吞取消均先确认失败再修正。最终完整26项本地回归、Debug Java/Kotlin编译及diff空白检查通过。未提交推送、未打包，真实资格、资源变化和奖励入账待实机验证。

### 2026-10-06：连续移植——大表鸽、加速卡上限、森林补签（未提交）

- 大表鸽追加farmNpcType=3，0～2兼容。查询会员炼金指定LIFE_RECORD任务后加入/反馈授权，再雇佣；专用任务按实际状态执行/领奖。满88粒或明确满产时先用RuntimeInfo.putVerified原子保存待收标记，再领取遣返；会员单领/一键领先绑定唯一待收芝麻粒反馈ID，回查该ID消失后清理标记。待收跨天/重启保留，写盘失败回滚并停止动作；反馈满20条、分页或多条候选无法确认时暂停，不推断已领。保留好友工人、未知NPC和待领奖励；不恢复原信用2101等主动移除业务，不为雇佣开新的会员总开关。
- 加速卡新增accelerateToolDailyLimit，默认8保持旧行为，-1不限、0不用；在共用useFarmTool入口检查，成功即保存现有按账号日计数，避免后续sleep取消导致漏记。保留服务端3D16限制和原喂鸡策略。核对发现M已有特殊食品库存降序，自己和家庭共用，且useSpecialFoodCountLimit=0已代表无限，不重复移植；AG按收益补蛋/动态批次是另一增量，尚未加入。
- 森林autoMakeUpSign默认false，参考当前目录Sen new的补签卡库存、signListPage和manualMakeUpSign协议，使用已有卡补最近30天最早漏签，不补今日/未来/已签/已补/显式不可补/状态未知日期，不补兑库存。范围可跨三个月；每项消费前重查库存与日历，之后确认目标日期已签/已补，同日期当日最多一次。森林run保留取消异常向上传播。
- GMT+8：日期由MyUtils.getInstance转LocalDate，日标记沿用Status按账号GMT+8重置；JSON创建用MyUtils、请求数组用JSONArray，读取opt*与判空，必须有成功信号及有效ID/记录/状态。无裸get/Gson例外；不新增线程或退避层，沿用ModelTask/RPC Bridge/RpcRequestGuard；保留GeminiAI、版本和签名。三个新增检查编译生产方法并模拟重启/跨日/写盘失败、资格/任务/分页、上限/成功计数、30天三个月边界、读回不变/失败保护、取消及转义；完整29项回归及Java/Kotlin编译通过，实机资格/入账待验证。

### 2026-10-06：连续移植——账单贴纸与商家服务（未提交）

- CollectStickers默认false：当前账单周期贴纸领取、已有贴纸免费升级、领取升级权益和免费抽奖；每项动作回查领取列表/级别/权益/抽奖额度，同项目当天不重复提交。每轮最多50张领取，100张升级候选/100次奖项上限；剩余下轮继续，未知或重复ID、失败、不推进均停止。账单拼贴世界为不同业务，尚未移植，不购买贴纸或新增消费。
- 商家三开关merchantSign/merchantKmdk/merchantMoreTask均false，独立MerchantService复用会员响应/ID校验及原RPC桥；只处理明确isOpened=true的账号，不自动开通。签到含招财金与积分球；开门打卡核对活动编号的业务日期，GMT+8上午6～12点签到并按服务端状态报名；积分任务使用MORE/SERVICE列表，接任务、浏览/答题入口、领奖后回查状态/进度；复用会员黑名单。每轮最多50个任务转换，跳过未闭环广告SYH_RTB_SHOW_TASK_INDEX_1与缺少动作的任务，不照搬AG整个TaskFlowEngine或Xu隐藏任务远程扩展。
- GMT+8：账单年月日和开门窗口显式MyUtils日历，Status按账号保存尝试标记。JSON统一MyUtils/JSONArray，opt*判空及成功/errCode冲突/ID重复/资格/已推进状态校验；无新增裸get/Gson例外。会员run保留取消异常；无线程/UI Hook引用改动，Gemini/版本/签名保持。两个新增回归先确认缺少入口/流程失败，再编译真实工作流/RPC回放资格、时段、领奖闭环、未知/重复/未推进、黑名单、取消及转义。完整31项本地回归、Java/Kotlin编译通过（8秒）；源码未提交/打包，真实接口与奖励待实机。

### 2026-10-06：连续移植——海洋好友与会员新策略（未提交）

- 海洋 recommendedSailing / giveFriendPiece 默认false；推荐航行使用专用来源，遵守原清理动作及好友名单，自己/重复好友跳过，每日最多20次推荐查询。清理成功后可选赠送碎片，同好友每日一次/总计20次，失败不重复，仅处理实际返回奖励。清理RPC统一JSON转义，清理与上层入口保留取消异常。
- 会员 memberPointExchangeSecKill 默认false，时间HH:mm默认10:00,20:00，每日积分预算默认0。复用ChildModelTask生命周期和共用兑换入口，只查询勾选商品详情，严格核对整点/纯积分价格/日预算；先预留积分后请求，不确定结果不重试，取消不排新任务。关键词默认空，最多10个/每个20项，仅补目录仍要勾选。Xu对应能力是搜索补充，不能解释成自动兑换全部匹配商品。商家秒杀独立未移植。
- GMT+8：排期及时段用MyUtils日历，Status按账号东八区换日；JSON创建MyUtils/JSONArray，读取opt*判空/身份/状态/整数价格校验，无裸get/Gson例外。无新线程或退避层，GeminiAI/版本/签名保持。三项新回归编译真实生产方法与RPC，覆盖名单/限额/奖励/取消/转义及排期/纯积分/预算/搜索边界；完整34项检查通过。海洋清理RPC补转义时遗漏Android JSONException声明，编译发现后补齐，针对性检查及Debug Java/Kotlin编译重新通过；真实接口待实机。

### 2026-10-06：连续移植——竞猜、成就馆、体验金与白名单（未提交）

- 动物竞猜新增ModelTask并注册，去冒险/去冲刺各自默认false；每轮复用AuthCodeHelper换当前账号令牌，本轮局部持有，不复制Xu手动令牌。签到前资格/状态校验，按小程序日尝试标记，回查已签数量恰增一才成功；只查询当前局，不投注。HTTP固定两个HTTPS域名，禁重定向，10/15秒超时和1MiB响应上限，finally断开、读写检查取消与账号，失败只输出异常类型，不输出凭据或原始消息。
- 芝麻成就馆在会员新增默认false开关，复用成功与列表校验；遍历分类、详情核对未领取/明确达标等级，处理初次领取和已领系列下一级，领取后回查目标claimedAt。UPGRADEABLE但未达nextThreshold不领取，重复ID/等级、未知数值及未确认结果停止或当天不再尝试；每轮最多50系列。不恢复信用2101，芝麻粒兑换独立后续。
- 余额宝体验金签到/已完成任务奖励两个默认false开关；只处理明确今日未签和主页面completeList，GMT+8换日停止，领取用任务trigger并回查列表消失，同项目当天只尝试一次，复用会员黑名单。自动任务、促销别名整合、体验金兑换和券转换/激活仍未移植，未把首阶段写成完整AG流程。
- 森林白名单默认false，独立好友列表；开启后空名单不收好友、黑名单优先，自己保留原行为。普通/PK/找能量/蹲点通过共用收取入口检查，等待后请求前再核对配置；不自动增加复活/浇水名单。森林补签新回归先复现查询期间过午夜后最早日期变成31天仍消费，再加请求前GMT+8日期一致性检查并通过。
- GMT+8：签到边界使用MyUtils日历，尝试状态沿用按账号Status；JSON统一MyUtils/JSONArray，opt*判空，状态/成功冲突/等级/整数溢出校验，无新裸get/Gson例外。沿用ModelTask生命周期，无新线程/退避框架；GeminiAI、版本、签名不变。四项新检查先在缺入口时失败，再编译生产代码/方法和RPC，通过签到回查、HTTP边界/隔离、勋章门槛、未知/重复/无推进、黑名单、取消及转义场景；完整38项回归及Debug Java/Kotlin编译通过，源码未提交/打包，实际资格及奖励待实机。

### 2026-10-06：连续移植——芝麻粒商品与时光加速器（未提交）

- 芝麻粒兑换独立默认false开关、勾选列表和每日预算（默认0仅刷新目录）；复用StringMapStore按账号保存sesameGift.json、ConfigPreload及IdAndName展示，不复用会员积分列表。目录遍历标签/分页最多20标签、50页，hasNext/重复/未推进异常保留旧目录并停止兑换；最多50个勾选项。详情复核ID、显式未领/未完成、库存、整数价格、有效窗口及现金字段；实付、下单、网购、券、实物等仅展示。请求前按账号预留芝麻粒预算和当天尝试标记，记录编号返回后查询记录，冲突/空记录/失败停止、不重复；不接下单或自动资源补货。
- 时光加速器使用原UsePropType枚举，默认关闭，ALL/ONLY_LIMIT_TIME选择，限时先/最早到期优先，未知类型/数量/ID/已过期拒绝；每日尝试上限默认1。只处理自己WAITING且正能量/未来成熟/符合原单球阈值的气泡，消费前再查，已成熟不浪费卡。复用四参数consumeProp(false)转义请求；库存减少且同ID气泡成熟时间提前/提前变AVAILABLE才确认效果，受影响旧蹲点取消后交原收取链重排。单卡日保护及失败计数；不补兑、不恢复青春模块，也未接AG定点/禁止时段策略。主任务每轮检查。
- GMT+8：无新日历，日预算/尝试复用Status东八区换日，活动及气泡时间为服务端毫秒。JSON统一MyUtils/JSONArray、opt*判空/严格类型/ID重复/金额/日期校验；querySelfHome顺带将遍历字段的JSONObject.get改opt并保留取消异常，无新增裸get/Gson例外。沿用ModelTask/RPC Bridge，无新调度框架/线程，UI目录路径不引Hook，Gemini/版本/构建/签名不变。两项新检查在缺入口时失败；加速卡查询期间能量先成熟的用卡浪费场景先复现，再加消费前重查修复，库存/effect/蹲点和纯资源/预算/分页/写盘/记录身份/取消/转义针对性回归通过，Debug编译通过（4秒）；真实接口待实机。

### 2026-10-06：连续移植——庄园满仓道具奖励（未提交）

- useFullRewardTool默认false；开启后对已启用的新蛋、加速、篱笆策略，先重查任务仍待领及库存，只需一张腾位且整份奖励能放下才用卡。自己小鸡必须在家，加速沿用原饲料/心情/日上限，腾位模式最多一张，不受连续用卡选项影响。回查库存恰减一、容量不变，再重查任务并领取，状态变RECEIVED才报成功；腾位/领奖分别按账号当天保护未知结果。其他类型保留奖励，不为腾位随意消耗偷吃等卡。关闭时保留原策略，但修复新蛋奖励数量缺失/小数时先用卡的问题。
- GMT+8：日标记沿用Status；JSON用MyUtils/JSONArray和opt，库存/容量/任务身份/整数数量校验，请求转义；无新增裸get/Gson例外。加速修复零消耗速度错误用卡，取消向上传播，沿用生命周期及RPC Bridge，Gemini/版本/签名不变。新增生产方法回放覆盖腾位/回查、两张不足不消费、旧任务不消费、日保护、加速上限/只用一张/零速度及取消；请求引号反斜杠先失败再修复。Debug Java/Kotlin编译通过；实际奖励/库存待实机。

### 2026-10-06：连续移植——商家续查与找能量结束闭环（未提交）

- 商家MORE查询透传最近一次有效响应的orderTaskCode，SERVICE保持独立空编号；每轮局部重置，不缓存跨账号令牌。编号非字符串或超过512字符停止。已有生产工作流回归先复现多轮仍传空，再修复并验证编号含引号/反斜杠的转义。
- 找能量会话结束后takeLookEnd一次，只有已开启森林任务且showTaskList显式true才查询take_look_end_task_list。复用森林响应验证，分组/子任务树最多200节点，重复ID/未知结构停止；仅FINISHED/COMPLETE领奖，每轮最多50次，沿用原receiveTaskAward参数并改JSON转义。请求前记账号当日尝试，回查同sceneCode/taskType变RECEIVED才记成功和明确返回的能量统计；TODO不自动完成。取消异常直接上传、不发旧会话结束；普通异常结束会话，hasErrorWait时停止。未接预曝光或能量雨提示自动再进场，不恢复主动删除模块。
- GMT+8：无新日历，Status日标记沿用东八区；JSON统一MyUtils/JSONArray、opt判空/响应成功/ID类型/树大小，现有takeLook也改MyUtils并验证冲突成功状态。无新裸get/Gson例外；新RPC任务版本对应现有宿主版本档位，沿用生命周期/Bridge，Gemini/版本/签名不变。针对性回放验证会话/开关、已完成奖励、分组子任务、未知/重复/未推进/当日保护、取消及转义，Debug Java/Kotlin编译通过（8秒），实际任务与奖励待实机。

### 2026-10-06：连续移植——小号复活能量策略（未提交）

- onlyCollectRevivedSelfEnergy默认false，参考当前目录Sen new当前Forest模块：普通大球保留等待复活、不添加蹲点，普通小球例外另设revivedSelfOrdinaryMaxEnergy默认0，正数才允许不超过该克数的球，仍符合M原单球规则。已成熟business.secondScene=fuhuonengliang的球可收；复活金球沿用collectRebornEnergy，在原收能量开启时模式可单独触发，其他金球在模式下保留。好友收能量仍沿用原设置。
- 周一东八区07:00至08:00启动森林任务时暂停，主页查询与共用延迟收取入口也拦截该窗口。Sen new旧AntForest路径为07～09点，当前Forest模块及文案为07～08点，本次参考后者，不引入系统默认时区。已排队普通/批量/双收/重试/蹲点在等待后重查当前主页、ID、能量与策略，未知/重复/缺失/不合规则整批停止；阻止改配置后旧请求消耗保留球。复活模式不使用时光加速器，避免浪费用卡或提前成熟；旧普通蹲点随主页遍历取消。不开新调度器。
- GMT+8用MyUtils.getInstance，JSON主页沿用MyUtils/成功校验，请求数组解析保留try-catch，opt判空及精确整数校验，无新裸get/Gson例外。共用收取回调保留TaskCancelledException向上传播；Gemini/版本/签名不变。生产策略/请求保护回放验证默认关闭、复活标志、普通/等待/小球、配置变化/批量/重复/未知、时区与周一边界、取消；加速器新增模式冲突回归及白名单回归通过，Debug Java/Kotlin编译通过（8秒），实际复活资格待实机。

### 2026-10-06：连续移植——蚂蚁保保障金（未提交）

- collectInsuredGold默认false，新增独立InsuredGold工作流复用会员成功校验、芝麻整数读取、Status和RPC Bridge。queryOpenAndAllowAndUpgrade/giftHomeRender/queryOpenAndAllow预热成功后，最多3轮/50项处理签到及活动气泡，发送前再查同流水仍可领；禁止helpGain/禁用项及畸形状态。原collectInsuredGold遗留RPC改为JSONArray包装，cfsy入口及七类rightNoList按AG；领取后可领列表变化，并确认原流水消失或明确gainSumInsuredYuan正数，不将“禁用”当作到账。签到缺少状态停止，未知按流水当天不重试。
- 五个AG任务中心接入查询、已知浏览任务signup/send、consult与列表回查，每轮总计50次状态转换。沿用会员ID/标题黑名单，拒绝ISSUED_TASK/EXPLAIN_INTELLIGENCE/TRANSFER及未知动作；已完成奖励只consult确认，不自动开通/投保/转账。返回任务ID必须匹配，明确终态/成功发奖记录才确认；signup必须回查推进，未确认按阶段当天保护。AG把FINISHED/COMPLETE也当已领，本次不照搬该判据；安心豆仍是独立后续业务。
- GMT+8：无新日历，日尝试沿用Status东八区按账号；JSON统一MyUtils/JSONArray，opt判空、成功冲突/整数/流水唯一/任务身份校验，无新裸get/Gson例外。取消向上传播，尝试标记在请求前保留，领取后取消亦不重复；Gemini/版本/签名不变。生产工作流/RPC回放覆盖预热、领取/报名/发奖/查询、日保护/未知/失败/重复、人工任务/黑名单、取消和转义；禁用误确认、签到状态缺失均先失败再修复。Debug Java/Kotlin编译通过（4秒），实机服务资格和到账待验证。

### 2026-10-06：连续移植——安心豆签到与等级奖励（未提交）

- beanSignIn默认false，BeanRewards复用会员响应校验/芝麻整数解析。签到canPush显式true才触发，回查false确认；守护者仅MARKETING_PRIZE/AVAILABLE/正整数beanQuantity候选，SKU全局去重、分组/项目/总量有界，动作前重查SKU类型/数量/资格及余额。领取后资格变化与余额恰增对应数量双确认，不将单独变成MONTH_COUNT_LIMIT当作到账；同签到/SKU按账号当天只尝试一次，每轮最多50奖励。浏览任务、答题、权益兑换和抽奖分阶段后续，不在此开关下额外消耗安心豆。
- GMT+8日尝试沿用Status，无新日历；JSON统一MyUtils/JSONArray，opt判空，result/data结构与成功冲突、ID/数量精确校验，无裸get/Gson例外。遗留签到RPC改JSON转义、补AG的checkMultiAccountFrequency参数；新增等级奖/余额查询RPC，沿用Bridge/生命周期，取消向上传播，Gemini/版本/签名不变。生产工作流/RPC回放验证签到/资格/余额、未知/重复/失败/当日保护及取消，状态变化未入账不报成功；实机资格与到账待验证。

### 2026-10-06：连续移植——安心豆浏览任务（未提交）

- beanBrowseTasks独立默认false，复用BeanRewards响应及RuntimeInfo.putVerified；只接AP15241780/AXD_TAK_LIST中明确BROWSE_PAGE+BROWSE_TASK，不接ISSUED_TASK/EXPLAIN_INTELLIGENCE/TRANSFER。任务ID与appletId分别核对，修正AG回查只用appletId匹配taskId的问题。报名返回订单先原子保存，再查服务端相同订单及业务身份，才send；已有服务端订单也先保存。保存失败、订单冲突、未知旧订单仍为NONE_SIGNUP时停止，不重新报名覆盖未确认流水。
- 发奖ACK的订单若返回必须一致，回查同服务端任务中sendStatus=SUCCESS且extInfo.TASK_ORDER_ID完全匹配才确认；持久订单确认后才清除，写盘失败保留。取消后同订单恢复，不重复报名；报名/发奖分别日尝试保护，每轮最多50动作，未知保留订单且不重复。ID/标题沿用会员黑名单，报名回查后再验证浏览条件及黑名单。答题、其他不明待领取任务、权益兑换/抽奖继续独立后续。
- GMT+8无新日历，日标记沿用Status，不复制AG的LocalDate.now默认时区；订单RuntimeInfo按账号跨日/重启保留。JSON统一MyUtils/JSONArray、opt判空/身份/状态/重复校验，无裸get/Gson例外。沿用Bridge与生命周期，不复制AG固定clientVersion头，不恢复版本伪装；Gemini/版本/签名不变。生产方法/RPC回放验证报名/保存/发奖/同订单奖单、保存失败恢复、取消恢复、旧订单、错订单/重复/人工/黑名单与转义；Java/Kotlin编译通过，实际接口与发奖待实机。

### 2026-10-06：连续移植——安心豆每日一次抽奖（未提交）

- beanDrawPrize独立默认false，复用BeanRewards响应/余额；INSP29990111活动两次确认consultResult显式true及相同campId，余额再查至少1豆后，Status标记在抽奖请求前。每账号每天最多一次尝试，失败/未知亦保留；余额恰减1豆且triggerResult为实际布尔才确认。中奖金额BigDecimal解析并限制长度/精度/尺度，不将接口金额视为现金到账；不连抽、不补充豆。按AG协议每次1豆，服务端费用若改变只能停止，客户端无法强制服务端消费上限。
- GMT+8日标记沿用Status，lastDrawTime为协议毫秒时间；JSON MyUtils/JSONArray、opt判空/类型/成功及活动身份校验，无裸get/Gson例外，取消向上传播。生产方法/RPC回放覆盖正常/未中奖、重复/不足/未知余额、活动变更、异常扣减、畸形金额/超大指数、失败/取消及转义，Gemini/版本/签名不变；实机资格及到账待验证。

### 2026-10-06：连续移植——安心豆保险知识闯关（未提交）

- beanGuardianQuiz默认false，复用BeanRewards成功解析/精确整数，ANSWER_PENDING才查询当前账号题目；最多20题，唯一scriptId、完整dramaId/userDramaId/rightAnswer、有效排序和下一题身份校验。已完成题不重复提交，每题请求前重查剧集/题目/明确答案/下一题，按AG协议addAskAnswerRecord(answerResult=rightAnswer)与answerQuestionDrama(answerResult=SUCCESS)上报；这些是来源协议值，不是客户端认定已完成。
- 请求前保留剧集/题目日尝试，记录失败/取消/未知不重复。回答后重新查询同剧集、同用户题目且完成/SENT才继续；不采用AG仅allCorrect结束/静态列表兜底重答已完成题。最终answerConsult四种明确完成状态才记录闭环，不将ACK或题目提交数当作奖励到账。题目/顺序/身份异常停止，不能代替真实前台答题展示。
- GMT+8日标记沿用Status，无默认时区；JSON统一MyUtils/JSONArray、opt判空、类型/重复/状态校验，无裸get/Gson例外。账号UID及生命周期取消检查，不复制AG固定clientVersion头、不恢复版本伪装；Gemini/版本/签名不变。生产方法/RPC回放验证顺序/已答跳过、完整答案/重复/身份变化、记录失败/无实际进度/最终未确认、取消/同日重复与转义；实机接口及闯关奖励待验证。

### 2026-10-06：连续移植——安心豆权益目录与黄金票勾选兑换（未提交）

- beanExchangeRight/beanExchangeRightList/beanExchangeRightBudget默认false/空/0，预算0只刷新；BeanRightIdMap复用StringMapStore、beanRight.json按账号独立缓存及ConfigPreload，独立UI只读缓存不发宿主RPC。动态有效分类、全部推荐及预兑换列表，最多20分类/50页/2000权益，明确hasNext及递增pageEndIndex、重复无进度/冲突/结构/保存失败停止并保留旧目录。未返回的已选ID不删除。
- 自动兑换仅明确GOLD_TICKET/OTHER、已知现金0/缺豆0、整数豆价、needOrder=0、无外跳/下单的原生黄金票，商品/现金/券等只展示。每轮最多50个选中目标；新rightsExchange参数走原生bean协议，不复用遗留bluebean_onestop拼接RPC、不自动补货。兑换前详情、完整历史exchangeTotalNum及余额确认，第二次详情同成本/计数；按日预留预算/权益尝试在RPC前。之后ACK、余额恰减成本、同权益详情与历史计数均恰增1才记记录新增，未知保留预算且当天不重试。实际到账/生效仍以支付宝为准；要求完整计数字段，不明结构不强行兑换，不将AG只查ACK当成到账。
- GMT+8沿用Status按账号，无新日历；JSON统一MyUtils/JSONArray、opt判空/成功/身份/精确金额及计数校验，无裸get/Gson例外。当前账号/生命周期取消检查，Gemini/版本/签名不变。生产方法/RPC回放验证目录、只勾选、独立币种、预算预留、成本/下单/外跳/未知分类、详情/历史/扣减、不完整分页/保存失败/重复/取消与转义；实机目录、详情与历史计数协议待验证。其他权益类型待明确协议再扩展。

### 2026-10-06：连续移植——合种队长召唤（未提交）

- cooperateSendCooperateBeckon独立默认false、cooperateBeckonList空，只作用于勾选的普通合种，挂ProtectEcology，不建新模块、不依赖cooperateWater。复用CooperationIdMap/StringMapStore，启用后可只读刷新目录，最多50个合种/每队200人；目录唯一ID、详细合种身份/队长、排名实际Boolean可召唤校验，18点前/未选中/非队长/自己/未知/重复不发送。
- GMT+8通过MyUtils.getInstance检查18点后，动作前重查队长和同队友canBeckon，复核配置/UID/时间；按账号同队同人当天一次，总20次尝试，在RPC前标记，失败/未知/取消不重复。ACK且同队友可召唤状态转false才记请求/资格确认，不能据此保证通知送达；不浇水、不消费能量。AG错误lipays链接修正为alipays，合作ID先内层再外层UTF-8编码，使用Android早期支持的URLEncoder(String,String)。
- JSON统一MyUtils/JSONArray、opt判空与成功冲突校验，无裸get/Gson例外；共用queryCooperatePlant/queryCooperateRank改JSON转义，原浇水调用保留参数/来源。取消向上传播；Gemini/版本/签名不变。生产方法/RPC隔离回放验证勾选/队长/18点/资格前后变化、日上限及边界、未知/重复/保存失败/错误状态/取消与JSON/双层URL转义，无真实通知；实际接口/通知送达待实机。

### 2026-10-06：功能移植来源记录（未提交）

- 按用户要求新增docs/功能移植来源记录.md，补齐阶段1～22的功能名/key、来源APK完整文件名或AG/Xu项目名、来源代码索引、M位置和已移植/未移植范围；索引与外部对照表/清单链接到该文档。
- 区分主要源码依据和APK功能对照，Sen old排除、M已有与主动移除项不算本轮新移植；阅读/农场时长不误记为来自真实SJ APK。未改业务代码、JSON、GMT+8、签名或Gemini，文档检查配置key与路径。

### 2026-10-06：连续移植——Sen new智能双击卡（未提交）

- 核对当前目录Sen new的Forest.onSmartPropDecision，实际按未来290秒支持双击的好友蹲点球数量触发；克数阈值只见字段/getter无执行读取，修正对照判断。来源表追加阶段23，完整APK名与源码索引保留。
- smartDoubleCard默认false，阈值10、每日尝试默认6/0不用，需原continuousUseCardOptions的doubleClick和collectEnergy。接原连续入口，启用新策略不走原叠加/兑换；只用LIMIT_TIME_ENERGY_DOUBLE_CLICK库存，不复活已注释的doubleClickType/老双击卡策略。31天/永久卡和补兑暂不移植。
- 记录实际canBeRobbedTwice，ModelTask仅加子任务列表副本，不建新调度器；非自己/未取消/白黑名单核对，建立一个合并检查回调，进入窗口后至少30秒一次，沿用ChildModelTask代际准入和唯一ID，停用/取消/切号不续排。
- 消耗前重查好友同球资格/保护道具、当前生效卡及库存，限制查询规模和合法ID。提交前记尝试和未确认保护，ACK且库存恰减1/同ID消失/生效才清保护，其余本日不再消耗。共用queryFriendHome补充取消向上传播，防止过期任务继续。
- JSON沿用MyUtils/JSONArray和opt判空、统一RPC Bridge；日计数沿用账号Status/GMT+8，无独立UI Hook、无签名/版本/Gemini改动。生产方法隔离回放覆盖资格/蹲点身份、不明库存/重复/即将过期、有效期、扣减/生效/失败、日保护、合并排期及取消/转义；未请求真实账号，接口字段及双击收取待实机。

### 2026-10-06：连续移植——AG满仓加饭卡分支（未提交）

- 来源为AG AntFarm.receiveToolTaskReward的BIG_EATER_TOOL分支、日两次使用统计和subFarmVO.useBigEaterTool生效状态，在功能来源表记阶段24（16b），不误记为APK复制。本轮无新开关，复用默认关闭的useFullRewardTool和原useBigEaterTool，未勾选加饭卡不消耗。
- 满仓且一张足够放下整份奖励时扩展原腾位流程；即时核对同一自家庄园、唯一主人小鸡在家进食及实际Boolean未生效标志，消费前再查资格/UID/配置；日两次尝试与未确认标志在RPC前写入，ACK+库存恰减1+同庄园生效标志为true才清保护。腾位不确认就不继续领奖，不仅以库存减少当作加饭生效。
- 共用useFarmTool入口在满仓策略启用时收口加饭卡的普通使用与腾位，避免同一轮两条路径重复使用；关闭新策略时保留旧加饭行为。偷吃/装饰等其他工具不自动消耗，需独立核对使用条件。
- syncAnimalStatus原字符串拼接改MyUtils/JSONArray转义，保留FEEDSYNC/原来源/所有参数；opt读取、整数校验、统一Bridge、取消向上传播，日保护沿用Status账号/GMT+8，无签名、Gemini和独立UI改动。扩展现有满仓回放，覆盖开关/进食/自家/生效漂移、扣减但无生效不领奖、日共享上限/未知/取消、旧策略保留及同步RPC转义；未请求真实账号，实机字段与生效待验证。

- 阶段1～24累计51项完整本地回归、Java/Kotlin编译及diff检查通过；来源文档共29条功能记录，位置与配置key核对，CSV同步103项。

### 2026-10-06：连续移植——Xu种花动态授权与签到（未提交）

- 来源是用户指定E:/Work/Xu的PlantingFlowers.java/PlantingFlowersRpcCall.java，不是从额外APK找的功能。新OTHER模块注册ModelOrder，plantingFlowersSign默认false，阶段25追加来源表。只移植动态授权/签到，收豆/除草/任务/浇花/奖励/抽奖/口令池/助力分支尚未接入，不触发外部上传。
- 当前账号即时AuthCodeHelper取appId2021005162668238授权，只请求固定HTTPS登录一次，不重用失败授权码做POST回退。返回alipayId必须等于当前UID，只接受合法串型令牌且不存文件/不记日志；不复制手填Token。每次请求/读取/响应后核对UID和代际，取消向上传播。
- 客户端签名格式按Xu五步数字/字典/AES协议移植重写，字典100字符对照源表哈希，没有引入新库。请求日期取MyUtils.getInstance的GMT+8字段，不用设备默认时区；HTTP10/15秒超时、不重定向、响应1MiB、流/连接保证关闭，无真实网络请求。
- 业务200/根层与嵌套失败冲突、登录身份与实际Boolean签到状态校验；提交前两次明确未签到才按rewardSource=3提交，日标记在提交前写入。ACK可无obj，但同日回查todayAlreadySign=true才记成功，失败/未知当日不重复，已签到当日不再登录查询。统一MyUtils/opt创建和读取JSON，签名、Gemini和独立UI不改。
- 生产类隔离HTTPS回放覆盖默认关闭/即时授权/同账号、源签名解密/字典/时间和GMT+8头、签到前后/已签/日保护/状态漂移/失败/空ACK、未知/错账号/转义/超长、禁重定向/断开/取消，未用真实账号。宿主授权/真实服务身份字段/签名有效性和签到入账待实机。

### 2026-10-06：连续移植——Xu种花昨日施肥奖励（未提交）

- 源自Xu PlantingFlowers.doTomorrowReward及waterTomorrowReward/query/receivedFeed协议，来源表追加阶段26。plantingFlowersTomorrowReward独立默认false，复用阶段25当轮动态授权/同账号/签名及有界HTTPS，签到完成不会阻止其他已开启分支。不浇花/施肥/调用口令池，不将其他种花奖励计为已完成。
- 必须yesterdayFertilizationReward是明确正整数且todayIsReceived实际Boolean=false，提交前重查同数量/状态/UID/配置/东八区日期，日标记写入后按rewardSource3提交；同日回查数量一致和领取状态true才记领取状态确认，不承诺奖励入账。未知/失败/取消当天不重复，不根据ACK直接日志宣称成功。
- 扩展同一种花隔离回放，覆盖签到关闭时单独领奖、日保护、已领、数量/状态未知及漂移、领奖失败/无效果/取消，共用请求在建连接前再次核对代际/账号。无新库/框架/签名/Gemini改动，JSON沿用MyUtils/opt，日标记沿用账号Status/GMT+8；未请求真实账号，奖励字段/入账待实机。

- 阶段1～26累计52项完整本地回归、Java/Kotlin编译及diff检查通过，来源表31条功能记录及代码位置/key核对完成，对照CSV同步103项。

### 2026-10-07：完成本轮有执行依据的后续移植（未提交）

- 阶段27～44及36b/39b：森林预曝光、周期及加速器时间规则，种花免费奖励/收豆/浇水预约/加工厂/抽奖/任务/加速卡，账单积木、余额宝指定任务及别名、WebDAV、能量雨收尾、模块与子动作按钮、缓存维护、排位预算/稳定持续追榜、美食动态批次和日周补蛋、芝麻树/炼金额外奖励、好家缴费金、指定补兑与六类临期用卡。每项实际来源、配置key、M位置及未接分支记在docs/功能移植来源记录.md，103项对照和CSV同步。
- 复用已有Bridge/RpcRequestGuard、任务执行槽与账号生命周期，无新框架或依赖。消费提交前持久预留/未知保护，按同一任务/库存/余额/效果回查，取消向上传播；手动入口先保存指定账号配置、宿主准入后重读，鱼塘仅绕过自动间隔，庄园只读初始化。
- GMT+8：周期/时间窗口、日周预算和缓存时点沿MyUtils/TimeUtil，种花结束时间及DAV文件名显式GMT+8；JSON使用MyUtils创建和opt/判空。严格数组解析保留异常失败语义；DAV沿现有Jackson解析，非org.json规则对象。共享forestSignPayload补null保护，临期加速后取消自身旧球蹲点、后续重新查询排期。
- 独立配置与DAV页面没有宿主Hook/Xposed类调用；API102、版本1.2.10、原签名和Gemini配置ID保留，未提交/打包，也未请求真实账号。真实活动资格、领奖到账、捐蛋排名及实机界面仍待验证。
- 明确遗留：余额宝兑换/券激活缺动态资格和选择预算合同；3小时捐步缺当前账号CSR签发/刷新，未复制固定凭据；旧花花卡/百次立减卡缺現行产品/类型及证书/券终态；种花外部池/助力缺账号范围，芝麻委托/游戏/租赁/无合同广告、庄园偷吃/分享/装扮主动消费未接。同类美食批次与简单积木摆放保留有界实现，不搬来源优化器。主动移除六项不恢复。
- 最终65项完整本地回归、NormalDebug Java/Kotlin编译及diff检查通过；配置说明重生441项，103项功能CSV同步。回归以生产代码隔离回放，不把编译通过等同实机成功。测试产生的__pycache__清理被执行策略拒绝，目录保留，不影响源码。

### 2026-10-07：补齐阶段45～48并重新全量验证（未提交）

- AG余额宝券全量转换、指定公开活动兑换及SJ已有资产激活接入AntMember；各自预算默认0/空选择，完整刷新延迟、库存/余额/同trialId状态A回查，保留兑换equityNo而不猜其资产映射。未知回执跨日冻结。
- Xu种花手动口令/UID配口令、外部HTTPS列表、自身口令与本轮记录上传接入；开关关闭/地址空、不附业务Token或签名，记录state0仅表示提交。助力无历史回查依据，unknown跨重启/跨日保留，不误报成功或重发。
- SJ百次卡签到/待领奖及花花卡免费翻卡/合卡接入；当前资格、同任务/卡位/发奖订单回查，共享日写预算0、各活动持久未知保护。缺正向浏览类型或send/award终态的未完成任务不自动执行。
- AG满仓蹭饭/救济/三等级装扮卡补齐后共支持十类；新五类总预算0、每类日一次，自家缺粮或未获得套装才提交，库存与实际效果回查，服务端选择去向/套装。消费前重查toolId/count/holdLimit，损坏账本不重置预算。
- GMT+8：新日预算仍由MyUtils/显式GMT8日期计算；券、旧卡、农场账本验证真实日期和未来日。JSON通过MyUtils创建、opt读取/类型判空；严格数组解析保留异常防护。未新增库/框架、UI宿主引用，保留API102、Gemini、1.2.10及原签名。
- 最终68项全量隔离回归、NormalDebug Java/Kotlin编译通过；配置说明466项、103项表格/CSV同步，来源精确到源码项目或当前目录APK及方法。三小时CSR签发/刷新、新兑换券到资产映射、旧卡未完成任务、芝麻真实委托/游戏/租赁及无合同广告仍缺协议；助力只能确认提交，真实资格与到账未实机验证。


### 2026-10-07：继续补齐阶段49～50（未提交）

- 三小时公益捐步：Xu/SJ业务入口结合公开官方前端v1.0.871，匿名CSRF初始化、当前宿主授权换本轮sid/token及到期时间，凭据仅内存。两次稳定查询当前步数与限额，每日最多一次提交，精确捐步/剩余扣减及ACK回查；未知跨日冻结。输出流打开后再验账号/取消，运动公共step/run取消向上传播。
- 芝麻免费分支：AG租赁动作/树委托与游戏SDK、Sen new非LJCS广告。仅指定免费租赁模板、唯一匹配的树/信用记录、四款已映射游戏、明确免费广告；完整等待与同任务/记录推进回查，委托双状态确认。SDK接受提交不等于奖励到账；同步Token改为本轮局部变量，UID/代际/取消检查、禁重定向、响应上限与秘密日志回归。未知跨日冻结，损坏/未来预算拒绝执行。金豆公共调用方取消向上传播。
- 来源登记到方法/路径；旧卡和余额宝剩余合同深查仍无正向资格或新券→资产关联，种花助力仍缺终态回查，其他商家/未知游戏/P2E及付款签约分支不计为已完成。主动移除功能不恢复。Sen与M无纸阅读/农场时长比较结论保留在专门文档。
- 69项全量隔离回归通过，SDK标签修正后专项再通过；最终NormalDebug Java/Kotlin编译通过，配置说明重生467项、103项表格与CSV同步，diff检查通过。没有真实账号接口调用，尚未提交或打包；版本/API102与Gemini配置ID保持现状。

### 2026-10-07：继续补齐阶段51～52（未提交）

- 余额宝参考AG/SJ补本轮兑换券直激活与按预算激活全部普通资产：新选项默认关闭，复用激活预算0。实际兑换equityNo作为couponId并显式equityType=voucher；普通trialId在两次稳定目录中选择、逐个提交与状态A回查。修正此前误以为必须先建立equityNo→trialId映射的判断。券ACK只记受理金额及计划日期，没有独立终态回查，未知回执跨日保留，不宣称收益到账。
- 百次立减卡参考SJ补选定任务目录、完整原时长等待、send及同任务待领→RECEIVED回查；开关关闭/目标[]/共享日预算0。空目标或预算0只列标题、秒数及可复制JSON，不自行抓包。身份/类型/标题/材料/链接等漂移及损坏账本拒绝写入；取消传播。来源600ms间隔不作为浏览时长，花花卡不借用百次卡终态。
- 修复预算保护的共同漏洞：余额宝转换/兑换/激活/签到/领奖/任务complete与forward、旧卡活动的所有写请求统一显式单次Bridge尝试；查询沿用原默认值，防止默认三次自动重试绕过预留/未知保护。隔离回放断言写调用只有一次，失败不重发。
- GMT+8：业务日/账本继续MyUtils.getInstance，校验旧日/未来日及原始计数；等待用单调时钟、不缩短源合同。JSON统一MyUtils/opt及类型判空，选定目标数组保留严格异常/尾部校验；没有新增框架/依赖或独立UI宿主调用，保留API102、1.2.10、签名、Gemini及原配置ID。
- 69项全量本地回归、最终NormalDebug Java/Kotlin编译通过；配置表471项、103项对照/CSV同步，精确来源及剩余缺口更新。未提交/打包/调用真实账号，活动资格及实际到账仍待实机。

### 2026-10-07：恢复M会话并继续阶段53～54（未提交）

- 原会话为XQE-OTHER目录的`01a11063-c672-7ae1-a441-98f418ba44f8`，实际代码在Sesame-M，保留全部已有工作区改动。参考AG的`GameCenterPlayRpcCall`及`AntSesameCredit`补明确游戏时长和普通频道浮球，沿用炼金关闭开关、持久日预算/未知状态，不新增配置或依赖。
- 明确平面合同要求当前游戏ID、source及正整数时长，1～300秒完整等待；普通频道要求durationTask的scene/task/module/guide/source，使用当前推荐游戏及consult时长，1～299秒加1秒完整等待，随后重查任务、游戏、时长。写请求均单次，只有同recordId进度才确认，不把反馈/时长/complete ACK算完成。
- GMT+8：沿用MyUtils.getInstance的业务日/原日预算；跨日不再写入。JSON创建使用MyUtils、读取使用opt并判类型/嵌套空值；URI查询使用JDK解析并拒绝重复、冲突及异常身份字段。新增逻辑仅在宿主任务域，不引入独立UI的Xposed依赖，保留Gemini/API102/版本1.2.10。
- 69项全量隔离回归、NormalDebug Java/Kotlin编译通过；两条执行分支关闭后的负向对照均使新断言失败。来源/清单/103项对照CSV同步。P2E页面会话/组件初始化/分片仍独立待接，种花助力/余额宝券的独立终态仍缺；未提交、未打包或调用真实账号。

### 2026-10-07：继续阶段55，P2E页面会话与分片（未提交）

- 参考AG `AntSesameCredit`及`GameCenterPlayRpcCall`补P2E，复用炼金默认关闭选项、原域预算和持久未知保护。只回放当前任务三份页面请求，保留未知字段与嵌套值；拒绝来源/UID/入口冲突，页面必须证明唯一gameAppId/gameId，模块与版本仅用页面或显式入口，不造推荐游戏/默认版本。
- 首帧、enterGame、助手/组件查询、P2E consult及加载事件按来源顺序；consult正整数1～299秒加1，首片最多31秒、后续最多30秒，各片完整真实等待后核对同recordId、入口请求、页面游戏和consult时长/类型合同，单次上报。任何分片失败停写且不重发，complete接受后还需同记录进度回查，ACK不记完成，不回落普通push/SDK。
- GMT+8：沿用MyUtils业务日/预算，跨日禁止后续请求。JSON采用MyUtils、opt及严格类型/嵌套校验，数组字符串保留异常防护；请求字段排序用于稳定合同比较，语义字段全部保留。无新增配置/依赖/框架或独立UI宿主引用，版本1.2.10/API102/Gemini/签名保持现状。
- 扩充原`check_sesame_tree.py`，验证1/30/31/90/299秒边界、分片及顺序、嵌套/编码/别名请求、动态页面和入口补全、单次写RPC、漂移/中途失败、取消/切号/跨日与持久未知。最后入口兼容修正后专项及最终源码69项全量隔离回归通过，最终NormalDebug Java/Kotlin编译通过，关闭P2E执行的负向对照断言失败。配置471项、103项对照/CSV同步。
- 重新核对AG/SJ/女神版/Xu的其余缺口：旧卡正向类型及报名后进展、花花卡完成/领取关联、种花助力历史、兑换券同equityNo独立终态、其他商家/玩法仍缺依据，来源表注明方法位置。未提交、未打包或调用真实账号，不能把协议缺口计为已完成。

### 2026-10-07：继续阶段56～57，选定报名与已有资产回执恢复（未提交）

- SJ百次卡报名请求已有明确applet.trigger/signup及NONE_SIGNUP/needSignUp依据。纠正此前因上游未实现回查就跳过整条报名分支的过度限制：新增`hundredCardSelectedSignup`默认关闭，沿用明确目标列表/原日预算，只有当前报名资格、完整1～300秒原合同、同任务/中心两次稳定查询才单次报名。进入本业务已知NOT_DONE/TODO且报名标志解除、其余上下文不变后才继续等待/发送/领奖。每步独立预留预算，已确认报名可同日恢复；新日不沿用旧资格，仅静态NO_AUTO无法验证进展仍不执行。未知跨日冻结，危险业务不处理，不自动挑选类型。
- 普通余额宝资产旧激活回执增加只读恢复：仍被选定/全部已有资产模式、正预算、UID/trialId/旧日/原非A严格校验，两次稳定查询同ID状态A才保存确认并解除冻结。恢复本轮不提交激活、转换或兑换，不退还原预留预算。公共券回执预存UID，资产查询可选owner字段严格校验；兑换券、转换/兑换、身份不全或损坏的旧回执不套用，独立券终态缺口保留。
- GMT+8沿用MyUtils的业务日/日预算，跨日停止后续动作；JSON统一MyUtils/opt及严格判型、嵌套判空，原目标数组异常防护保留。复用原Worker/Bridge/RuntimeInfo/任务生命周期，没有新框架、依赖或独立UI宿主引用，保留API102/Gemini/版本1.2.10及签名。
- 扩充原旧卡/余额宝隔离回放，覆盖报名后的状态与标志、ID/中心/上下文/owner漂移、预算不足后的恢复、取消/切号/跨日、ACK失败、损坏回执及原券边界。关闭报名和旧回执恢复分支的负向对照均使新断言失败。最终源码69项全量隔离回归、NormalDebug Java/Kotlin编译通过；配置说明生成472项，103项对照/CSV与来源/用户手册/版本说明同步，未提交、打包或调用真实账号。


### 2026-10-07：继续阶段58，选定任务待领奖续跑（未提交）

- 修复选定百次卡发送已确认、预算不够领取时，后续选定任务只匹配未完成状态而无法恢复的问题。复用当前任务目录与已知待领奖/RECEIVED状态，在原选定ID/中心内两次核对任务合同，只领取、不重新报名、浏览等待或发送；不需开启全部任务领奖。领取终态同时校验时长及上下文，漂移保留未知回执。
- 扩充原隔离JVM检查，覆盖三个待领奖状态、同日提高预算/次日原预算、报名后暂停续领、未执行过的已选待领奖任务、静态NO_AUTO仅领奖、字段/账号/合同漂移、未知ACK与取消。业务日期仍为MyUtils/GMT+8；JSON继续MyUtils/opt及嵌套类型判空，无新增配置/依赖或UI宿主引用。
- 另读取AG来源指向的官方余额宝公开页及两个脚本，当前页面为服务大厅，未包含equityNo/trialId/voucher终态协议；证据保存在XQE-OTHER的apk-analysis/yeb-public。这次查询不能作为兑换券终态完成依据，花花卡和种花助力缺口保留。
- 最终69项全量隔离回归、NormalDebug Java/Kotlin编译及diff检查通过；关闭待领奖续跑分支的负向对照使新增断言失败。配置仍472项，103项对照/CSV同步，未提交、打包或调用真实账号。


### 2026-10-07：继续阶段59，芝麻树当前浏览合同（未提交）

- 从AG/Sen的官方页面引用读取芝麻树两份公开前端，当前M refer对应180020010001288004页面及zmTree.366b2302.js：taskMaterial.taskType=BROWSER、browseTime秒、jumpUrl用于浮球；倒计时结束后才执行RENT_GREEN_TASK_FINISH/send。来源及SHA256已存当前目录apk-analysis/zhima-public；不能将芝麻树类型搬作旧卡/余额宝类型证据。
- 明确BROWSER按当前数字1～300秒完整等待，重查同ID、状态、时长、类型、链接及原上下文后才预留预算和单次发送，领取终态也比较合同。等待中取消/切号/跨日或合同漂移无写调用，不留下提交未知回执。明确CONTINUE_SIGN_TASK不套浏览等待；其他明确业务类型不自动报名/发送。无类型的既有AG/Sen合同仍按原16秒规则，BROWSER缺时长不能套默认。
- 扩充原生产类JVM回放：1/16/45/300秒、报名→完整等待、签到0秒、0/301/负数/小数/字符串/空值时长、链接缺失、其他类型、等待中取消/UID/跨日、类型/链接/时长漂移。GMT+8继续MyUtils业务日期，JSON使用MyUtils/opt及类型判空；无新增配置/依赖/UI宿主引用。
- 最终69项全量隔离回归、NormalDebug Java/Kotlin编译、diff及JSON/GMT+8/空白检查通过；禁用浏览完整等待和放开其他明确类型的两项负向对照均检出断言失败。配置仍472项，103项对照/CSV同步；未提交、打包或调用真实账号。
- 剩余终态/任务合同重新检索AG、Xu、GR历史、Sen new、SJ、女神版及XR导出，SJ/女神版原始DEX字符串仍仅有已知YEB资产/激活及花花卡queryV2/trigger/award接口；限定业务日志/抓包搜索未找到运行返回样本。官方芝麻树合同不能挪用于余额宝/旧卡/种花；已请求对应脱敏返回的本地路径，缺少材料时不能宣称全部完成。

### 2026-10-07：阶段60～62按来源补齐旧卡请求链及余额宝状态分流（未提交）

- 花花卡新开关huaHuaCardTasks默认关闭，沿用旧卡日预算0；queryV2→signup/send→award按SJ原参数执行，SIGNUP_COMPLETE免报名，跳过SCENE_TASK与付款/开通等任务。逐任务数组领取，接口奖品名存回执；只记录受理，不冒充独立到账。
- 百次卡hundredCardAutoTasks默认关闭，复用选定任务工作流和状态回查；自动选择当前明确时长任务，保留原分类排除及风险过滤。静态NO_AUTO报名受理后只允许本轮继续，按1～300秒完整等待而非截15秒；已有选定模式保持严格确认逻辑。
- 余额宝复用勾选任务/明确浏览资格，按AG的simplifiedStatus优先与主/促销来源路由，补not_sign/sign及原报名状态的task.trigger；不增加开通或转账调用。
- GMT+8沿用MyUtils业务日；分钟outBizNo是来源epoch分钟协议。JSON创建统一MyUtils、opt读取及类型/判空校验；OtherTask已触及的四处原直接JSONObject解析一并改为MyUtils，严格成功判定保留。未改版本/签名/API102/Gemini，未提交、打包或请求真实账号。
- 专项回放覆盖请求参数与顺序、受理后续跑、预算中断、单次写调用、持久未知、身份/跨日/取消、风险与损坏元数据；分别关闭花花任务链、百次自动入口、余额宝报名路由的三项负向对照均检出断言失败。配置表重生474项；全量最终验证另见来源记录。

### 2026-10-07：阶段63～65补可独立移植增量并结清范围（未提交）

- 家庭复用AG familyTreadMill及最低贡献排序，默认随机；分享新增邀请/排除选中名单，默认排除/空名单保留M旧行为，原不邀请名单优先。去除安排成员时对原共享列表的修改，RPC改JSON转义和单次写调用，日标记及UID/跨日/取消检查，家庭上层继续传播取消。
- S2复用Sen实际目标名次规则，1～100默认1；按目标行计算超过所需数量，已达到目标/数据缺失不捐，复用原资源补食、单次上限与启用的日周预算。日榜稳定星级策略保持，来源边界明确记录。
- 安心豆复用AG原生OTHER权益分类，去除GOLD_TICKET唯一限制；仍要求纯豆、cash=0、needOrder=0、无外跳，券/实物/现金/下单类只展示。兑换前原子保存回执及预算，写调用1/0；详情/历史计数增1/余额准确扣减同时确认才清理，未知跨日/重启保留。补目录及消费回查的UID/业务日检查。
- GMT+8复用rankingDay/Asia/Shanghai及MyUtils业务日，familyTreadMill参数显式Asia/Shanghai；新JSON创建全部MyUtils、读取opt及必要类型/空值校验，无新增例外或依赖。版本、签名、API102和Gemini保持；未提交、打包或调用真实账号。
- 新增最小生产方法JVM回放check_farm_family.py并扩充原排名/权益检查；70项全量回归、NormalDebug Java/Kotlin编译通过。名单过滤、目标行和非黄金票原生权益三项反向对照分别检出断言失败。配置表478项、103项对照/CSV同步。
- 按用户明确范围排除WebDAV ZIP/自动同步/远端清理；未知商家/游戏、余额宝无明确操作合同及XR后台脚本/批量好友平台跳过，不再要求用户日志作为迁移前提。原已接手动WebDAV及阶段1～62保留。

- 用户补充明确余额宝开通/转账类不能要：在YebExpGold共享分类规则中排除开户/开通/转账/付款等标题和操作分类，目录、选定别名及fresh重查/浏览资格、completeList统一过滤。回放验证浏览标签、报名状态、勾选/奖励不能覆盖，分类漂移停止；不新增金融接口或配置。

### 2026-10-07：版本1.2.11及累计更新说明（未提交）

- gradle.properties版本1.2.10改为1.2.11，沿用现有Git提交计数versionCode、arm64-v8a/API102和签名配置，不改业务逻辑。
- 在docs/版本更新说明.md顶部新增1.2.11，按森林/合种、庄园/家庭/捐蛋、会员/账单、余额宝、芝麻树/炼金/旧卡、种花/海洋/公益、运行工具整理本轮阶段1～65及正式包后开发追加；区分已接流程、接口受理与实机结果，说明默认关闭/预算0和升级方法。旧版本保留，原1.2.10开发追加标明已汇入1.2.11。
- 明确余额宝开通/开户/转账/付款等排除，WebDAV后续扩展及后台/无合同项目按用户范围跳过；同步手册当前版本。无新增时间/JSON业务逻辑，GMT+8、MyUtils创建及opt读取无变化。

- 本次版本更新后70/70全量隔离回归、NormalDebug Java/Kotlin编译及NormalRelease/R8/关键Lint通过。包内1.2.11、versionCode1909（原正式包1905）、仅arm64-v8a，签名验证通过且与1.2.10证书一致。APK已归档APK/Release/Sesame-M-Normal-arm64-v8a-1.2.11_20261007_163600.apk，输出和归档SHA256一致；未提交、推送、发布或调用真实账号。

### 2026-10-07：配置页快捷按钮横向排列

- 复用Compose FlowRow将森林能量雨/打地鼠及同一配置页的庄园、鱼塘快捷子动作横向排列，横向和换行间距均8dp，按钮区下方留8dp；无快捷动作的模块不增加空白。宽度不足时换行，保留原文字和点击回调，不新增状态或执行策略。GMT+8及JSON业务逻辑无改动。

- 快捷按钮调整后70/70全量隔离回归、配置搜索的真实Compose回放、手动任务回归及NormalDebug Java/Kotlin、NormalRelease/R8/关键Lint通过。新包APK/Release/Sesame-M-Normal-arm64-v8a-1.2.11_20261007_170027.apk（versionCode1910）签名校验通过且与1.2.10原证书一致，输出/归档一致；保留上一包作为历史。

### 2026-10-07：立即执行按钮尺寸与卡片间距

- 共用类别标题行底部留8dp；按钮文字14sp、内边距水平12dp/垂直6dp，最小高度48dp，保留字体缩放与原执行回调。快捷子动作仍横向排列、窄屏换行。
- GMT+8、JSON创建、JSON读取均无涉及；未增依赖或宿主Hook引用，Gemini及原签名保留。
- 最终70/70全量隔离回归、NormalDebug Java/Kotlin及NormalRelease/R8/关键Lint通过。新正式包APK/Release/Sesame-M-Normal-arm64-v8a-1.2.11_20261007_170919.apk（versionCode1912）签名有效且与1.2.10原证书一致，输出与归档SHA256一致。

### 2026-10-07：执行按钮与上下配置卡片间距

- 共用标题行从仅底部8dp改为上下各8dp，前一类别的白色配置卡片与按钮明确隔开；首个类别同样留空隙。按钮尺寸、横向快捷子动作和执行回调沿用现有实现。
- GMT+8、JSON创建及JSON读取无涉及，无新依赖或Hook引用，Gemini和原签名保留。
- 按用户追加要求，森林/庄园/鱼塘共用快捷按钮区底部间距从8dp增加至16dp，覆盖仅兑换、仅钓鱼、能量雨、打地鼠及庄园五项快捷动作；横向按钮及换行间距仍8dp。
- 本轮70/70全量隔离回归通过；快捷按钮底部追加调整后，配置页真实Compose回放与NormalDebug Java/Kotlin、NormalRelease/R8/关键Lint再验证通过。最终包APK/Release/Sesame-M-Normal-arm64-v8a-1.2.11_20261007_171441.apk（versionCode1914），原证书签名有效，输出与归档SHA256一致。


### 2026-10-07：合并 MIUIX-api102 至 1763096c（35e0a8c6）

- 来源：`origin/MIUIX-api102` 从 `5b26bca5` 到 `1763096c` 的12个提交；在 `my_dev` 普通 merge，不 rebase。7个冲突按共同祖先、双方实现处理，复查全部14个生产文件；保留M的任务分步执行、复活能量过滤、动态美食、日/周排位预算、目标名次、切号取消和Gemini配置。
- 森林：金球按每次响应累加并避免跨球重复，补动物、过期能量及打地鼠统计；能量雨按任务类型或已映射appId识别游戏，未知游戏跳过。删除无人读取的任务类型白名单及批量活力值原始调试日志。
- 庄园：普通自动捐蛋优先S2，未发送时允许公益回退；已发送而结果未知保留当日尝试标记，不再回退公益。项目回读需同账号同日、增量等于本次捐赠；保留原排位预算预留与未确认记录，自动尝试预算键改按GMT+8日期，避免旧周键阻挡次日。偷榜独立沿用原策略/预算。饲料槽331拒绝后本轮停喂，小鸡乐园同步上报后回读额度，无推进时当日停止刷任务。
- 会员/农场/其他：游戏中心改用首页与分页任务流，明确待领才领取宝箱，任务流不伪造完成；损坏的任务列表不能当作任务已完成。施肥透传main/yeb场景，P03/P14当日停止该场景。森林、农场、运动、新村、金豆及绿色经营补服务端确认后的每日标记；运动任务1009当天停止。新村签到字段异常只跳过签到，继续处理独立任务奖励。
- 清单/合种：活力值与乐园商品成功取到SKU才记当日查询；重复SKU也视为刷新成功，损坏响应和旧缓存不能冒充新获取。合种写请求前保留当日标记/额度，异常、超时、取消也保留；离线不发送、不记额度，取消向外传播。组队仍按既有当日目标总量，真爱每日一次。
- GMT+8审查：所有新增每日状态沿用按账号的Status跨天机制，排位日期沿用Asia/Shanghai；保留森林GMT+8格式化和绿色经营UTC协议解析后按GMT+8计算业务周，无新增默认时区调用。
- JSON创建审查：新增业务解析统一MyUtils并检查成功状态、必要对象/字段；保留两处既有严格解析例外：`TaskAlternative.doFarmTask`直接构造以抛解析异常、阻止把损坏响应当成功；`AntMemberRpcCall.check`直接构造并捕获异常返回失败。没有新增严格构造例外。
- JSON读取审查：14个文件可执行org.json读取使用opt系列并检查嵌套结构；新增会员/签到/回读判据补必要类型检查。RuntimeInfo的getString/getLong不属于JSON，原注释内旧get代码不参与运行。
- 验证：扩展现有检查，回放数字交易关键词边界、通用游戏映射、会员损坏列表/待领宝箱、重复SKU刷新/失败旧缓存、合种未知响应/离线/取消、S2未知回读/每日预算/切号。70项隔离回归与NormalDebug Java/Kotlin编译通过，无真实账号RPC。版本继续1.2.11；本次未修改打包/签名、未构建新APK，既有171441包不包含本次合并。发布说明与使用说明同步更新。
