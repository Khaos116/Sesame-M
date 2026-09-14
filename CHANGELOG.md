# 修改记录

简明改动清单，按时间倒序追加，方便快速查看每次改了什么。详细的移植/合并原委、
取舍理由见 `doc/MyFix.md`；本文件只记一行摘要 + 对应 commit。

## 2026-09-14

- 未提交 fix: 修复移植审查确认的 17 项问题（切号任务隔离、金豆额度与领奖、视频冷却与调度、
  日志兼容与账号同步、分页/捐赠边界、VPN Hook 初始化、鱼塘时区、版本默认值、Gemini 答案和运动币气泡）；
  新增本地 JVM 回归检查，详见 `doc/MyFix.md` 对应记录。
- `5759d512` feat: 从新版GR快照移植12个独立小额福利任务（dayDaySave/luckCard/factCheck/
  forestPlantRewards/dailyCash/promoprodRewards/wealthDay/youthPrivilege/weeklyWelfare/
  healthIslandRewards/myBankWelfare/other）+ videoRewards 视频红包真实观看验证
  （含新的 Activity.onResume 观察hook + WebView JS注入探针）
- `322e29ca` feat: 日志详情页支持实时刷新（FileObserver 监听文件写入，边执行边看）
- `137cf239` fix: 修复验证码VPN弹窗拦截开关从未生效的问题（`boot()` 整段被注释掉）
- `6a31c9e1` feat: 新增全局自动切号功能（账号轮询，最小间隔2小时）
