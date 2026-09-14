# MyFix：合并 fork 代码到本地的修改事项记录

本页记录 Sesame-M 每次把上游/同源 fork（GR2026、Sure-Xu、Sesame-AG，本地路径分别为 `E:\Work\Gr\Sesame-GR2026`、`E:\Work\Sure-Xu`、`E:\Work\Sesame-AG`）代码合并进本地后，实际做了哪些改写、跳过了什么、为什么。按时间倒序追加新记录，不要覆盖旧记录。背景资料（GR2026 `MyUtils.java` 的完整拆解）放在文末「附录」，供后续合并对照。

## 变更记录

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

上一条 GMT+8 记录之外，核对 XU 自己 `doc/MyFix.md` 提到的几个待修项时，发现两个 XU 文档说"待修改"但**它自己当前代码也没真改**的问题，同时另外自己挖出一个新的：

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

用户明确要求正式包必须开混淆。三个参考项目里 GR2026 和 Sesame-AG 都是"整包 keep 自己代码"（`-keep class io.github.lazyimmortal.sesame.** { *; }` / `-keep class io.github.aoguai.sesameag.** { *; }`）——技术上混淆开了，但自己的业务代码一行都没真正混淆，只混淆了第三方库。Sure-Xu（`E:\Work\Sure-Xu\app\proguard-rules.pro`）是唯一一个做了外科手术式规则的，而且 Sure-Xu 和 M 同为 libxposed 102 架构（GR 是传统 Xposed API，AG 虽然也是 libxposed 102 但选了偷懒的整包 keep），参照对象选了 Sure-Xu 这份，不是简单照抄 GR/AG 的省事做法。

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

用户要求把福气鱼塘做了，指定用 Sure-Xu 的 Java 实现打底（Sure-Xu 已从 GR 原版修过 6 个已知缺陷，见其 `doc/GR-Sync.md`），如果 XU 版本有问题再参照 Sesame-AG（Kotlin 实现，用户更信任的参考）修。

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

**福气鱼塘**：检查了 `E:\Work\Sesame-AG`（Kotlin，`task/antFishPond/AntFishPond.kt` + `AntFishPondRpcCall.kt`）和 `E:\Work\Sure-Xu`（Java，`model/task/fish/FishTask.java` + `FishConfig.java`，与 GR 文件名/结构几乎一致）——**两边都已经有这个功能**，其中 Sure-Xu 是从 GR 移植过来的 Java 版本，且 Sure-Xu 自己的 `doc/GR-Sync.md` 记录了移植时顺带修过的 6 个 GR 原版缺陷（成功兑换也写失败标记、循环末尾无条件清零失败计数、自动黑名单开关未接入、状态查询与广告处理互相重入、任务等待忽略中断、浏览循环次数用 `max` 未限制上限等）。~~结论：以后真做这个功能时……本次没有动手移植~~——已经做了，见上面（更晚）的"福气鱼塘（FishTask）移植"记录，用的正是这里说的 Sure-Xu Java 版本打底。

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

**发现但没有动、需要以后单独处理的同类风险**：[ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) `setWakenAtTimeAlarm()` 里自定义唤醒时间段那部分（`Calendar nowCalendar = Calendar.getInstance()`，用于跟 `TimeUtil.getTodayCalendarByTimeStr(wakenAtTime)` 比较判断这个自定义闹钟今天是否还没过）**故意没有改**——`getTodayCalendarByTimeStr` 内部同样用裸 `Calendar.getInstance()` 构造"今天 HH:MM"，如果只把这里的 `nowCalendar` 单独改成 GMT+8 而不动 `TimeUtil` 本身，两边用不同时区反而会产生新的、更隐蔽的比较错位。`doc/GR-Sync.md` 早前已经点出 `TimeUtil` 大部分方法仍是系统默认时区、改动面很大，这次维持"不做半吊子修复"的原则，把这处留给以后专门做 `TimeUtil` 统一 GMT+8 改造时一起处理，不在这次顺手改一半。

**核对过、确认已经覆盖或不适用，不是遗漏**：

- **FishTask（福气鱼塘）**：`36d110b9`/`c3ae5375`/`549fcd7b`/`6c8cb85b`/`73507243` 等 9 个提交，`doc/GR-Sync.md` 早前已列为同步候选，本次重新确认这块工作量（独立 RPC 层 + 任务列表 + 黑名单 + 设置项注册）没有变化，仍未移植，维持"以后单独做一次完整任务"的结论，不在本次顺手做。
- **VersionHook 版本伪装**（`3013cb36` 新增 237 行 `VersionHook.java` + `cea6d5f9` 给 `AppConfig` 加 `enableFakeVersionSlider`/`fakeVersionName`/`fakeVersionCode`）：读了 `VersionHook.java` 全文确认——这就是拦截 `PackageManager.getPackageInfo()` 伪造支付宝版本号的功能，且用的是传统 Xposed API（`de.robv.android.xposed.XC_MethodHook`/`XposedHelpers`），M 现在只依赖 `io.github.libxposed:api:102.0.0`，连编译都过不了。这正是 `doc/GR-Sync.md` 一开始就点名的"GR 的版本伪装……属于具体版本/个人偏好，不直接覆盖"那一条，本次读源码确认判断依然成立，不是漏做。
- **扩展页面重写**（`41e74688`，559 行 `ExtensionsActivity.java` + 对应布局 XML）：内容基本就是上面版本伪装功能的开关 UI，外加下面 `writeDishImage` 手动工具的入口，两者本身都不搬，这个 XML UI 重写自然也不适用——M 的扩展页面是 `ui/miuix/MiuixExtensionsActivity.kt`（Compose），架构完全不同，没有直接对应关系。
- **`writeDishImage`/`writeDishImageWithRandomIds`**（`2a6d496b`，`TokenConfig.java` 新增两个方法）：光盘行动（森林任务的一种，上传餐前餐后照片换能量）手动补图片 ID 的便捷工具，只在上面提到的扩展页面里被调用（手动点按钮用）。M 的 `TokenConfig.java` 已经有 `saveDishImage`/`checkDishImage`/`clearDishImage` 这套底层能力，缺的只是这两个方便手动调用的包装方法和对应 UI 入口。**这是本次审计里唯一一个"未评估过、可能有用但没做"的候选**——是否需要在 Compose 扩展页面里加个手动补光盘图片 ID 的入口，需要用户确认后再做，本次没有主动加。
- **其余提交**（`560281dc`/`9a0c4408`/`48820e4c`/`51fbc6db`/`083587dd`/`189010b6` 等）：纯版本号/更新日志/构建脚本版本号文本变化，符合用户之前定的"版本号更新记录不用看"的范围，跳过。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。未运行模拟器/真机验证这几处时区修复后的实际调度效果（好友列表周一全量刷新、午夜固定唤醒闹钟、跨天状态重置），这几处都是全局调度逻辑，风险比单个任务模块的时区 bug 更高，建议后续找机会实机验证一次日期跨天时机是否符合预期。

### 2026-09-12：Release 只保留 arm64-v8a 产物，不再打 armeabi-v7a 和 universal

用户要求只保留 arm64 的 lib，其它不要。[app/build.gradle](../app/build.gradle) 的 `splits.abi` 从 `include "armeabi-v7a", "arm64-v8a"` + `universalApk true` 改成 `include "arm64-v8a"` + `universalApk false`——armeabi-v7a（32 位）和 universal（全架构兜底包）都不再产出。输出文件命名逻辑（`androidComponents.onVariants` 里的 abiTag 拼接）不用改，少了 universal 之后自然只剩一个带 `-arm64-v8a` 后缀的产物，没有走到原来的 "-universal" 兜底分支。

**验证**：`./gradlew assembleNormalRelease -q` 重新打包，`app/build/outputs/apk/normal/release/` 下只有 `Sesame-M-Normal-arm64-v8a-1.0.0.apk` 一个文件（14,753,721 字节），`apksigner verify --print-certs` 验证通过、指纹不变。上一条记录里"Release 打包后自动归档"的任务本身不用改（原来就是遍历输出目录下所有 `.apk`，产物变少了自动就只归档这一个），顺手清理了 `APK/Release/` 下这次之前几轮验证时产生的 armeabi-v7a/universal 旧归档文件（本地产物，不是仓库跟踪文件）。未验证 32 位设备上是否还有人需要 armeabi-v7a 包——这是用户明确要求去掉的，不是本次自行判断的取舍。

### 2026-09-12：彻底移除 libsesame.so 及其加载/桥接代码——比 GR 更进一步，对齐 Sure-Xu 的做法

用户要求参考 GR 去掉 SO 调用："本来就能直接调用的庄园功能，不需要调用 so"。核查后发现 M 当时的实际状态：

- [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 原 295 行 `System.load(LibraryUtil.getLibSesamePath(context))` 在支付宝 Service `onCreate` 时**无条件强制加载** SO——这是 `doc/GR-Sync.md` 早前点名过的"M 现在还在真调用它"的那处。
- 但 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 原 `doFarmTask()` 里唯一会调用 native 方法的分支（`LibraryUtil.doFarmTask(task)`）**早就是整段注释掉的死代码**，实际生效的是紧接着的 `AntFarmRpcCall.doFarmTask(bizKey)` 这条纯 Java RPC 路径。换句话说：**SO 被强制加载进内存，但没有任何地方真正调用它的 native 方法**——纯粹是加载开销和 APK 体积的浪费，用户的判断是对的。

`doc/GR-Sync.md` 之前记录 GR 自己的处理是"已注释这一加载，并用 Java 实现庄园任务；其仓库仍保留 SO，旧配置 UI 也仍尝试加载，所以 GR 本身并非完全无 SO"——只是注释掉调用，SO 文件和桥接类都还留着。这次没有照 GR 这个"半吊子"做法抄，而是按 Sure-Xu 在其 `MyFix.md` 里记录的更彻底方式（"本次已删除四个架构的 libsesame.so、旧包名 LibraryUtil 桥接类、强制加载"）直接整个删掉：

- 删除 [ApplicationHook.java](../app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java) 里的 `System.load(...)` 调用及其 `LibraryUtil` import。
- 删除 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 里那段已经注释掉的 `LibraryUtil.doFarmTask` 死代码。
- 整个删除 `app/src/main/java/io/github/lazyimmortal/` 目录（`util/LibraryUtil.java` + 转发用的 `BuildConfig.java`）——这是仅有的两个还在用旧包名 `io.github.lazyimmortal.sesame` 的文件，删除前确认过没有其它文件引用这个包（`grep -rl "io.github.lazyimmortal"` 只命中这两个文件加 `ApplicationHook.java` 的 import 行，import 已一并删除）。
- 删除 `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libsesame.so` 四个架构的二进制文件。
- [app/build.gradle](../app/build.gradle) 里原来指向这个目录的 `sourceSets { main { jniLibs.srcDirs = ['src/main/jniLibs'] } }` 也一并删掉（参考 Sure-Xu `app/build.gradle`，那边压根没有这条自定义 jniLibs 目录声明）；`packaging { jniLibs { useLegacyPackaging = true } }` 保留不动，这条是给 AndroidX/Compose 自带的 native 库（比如 `libandroidx.graphics.path.so`）用的，跟本次删的东西无关。

**没有动的**：`app/src/main/cpp/watermark.cpp` 和 `CMakeLists.txt`——这是另一个独立的 native 产物（水印相关），`doc/GR-Sync.md` 早前已确认它从未接入 Gradle 构建（不产生任何 .so，`WatermarkUtil` 一直走 Java 回退值），跟这次"庄园功能调用 SO"是两回事。既然不产生二进制、不影响 APK 体积，本次没有顺手删，需要的话应该单独确认。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误。实际跑了一次 `./gradlew assembleNormalRelease`：三个产物体积从上一次归档记录的 `arm64-v8a 15,168,233` / `armeabi-v7a 15,146,501` / `universal 16,397,519` 字节，降到本次 `14,753,727` / `14,753,411` / `14,767,934` 字节（universal 减少约 1.6MB，两个 ABI 专属包各减少约 400KB——两次构建之间还有其它改动，不是纯粹的 SO 体积差，但方向和量级符合预期）。`apksigner verify --print-certs` 对新产物验证通过，签名指纹与此前一致。未运行模拟器/真机验证移除 SO 加载后模块整体功能是否正常（尤其是曾经依赖 SO 才能工作、后来才切换到 Java RPC 路径的庄园饲料任务，这次只确认了编译期没有残留引用，没有做运行期回归测试）。

### 2026-09-12：家庭功能六项子功能逐一对照 Sesame-AG——修了两处，其余记录为已知差异

用户要求把 family() 剩余六项子功能（签到/顶梁柱/领奖/喂鸡/请客/分享好友）也逐一对照 Sesame-AG 的 `AntFarmFamily.kt` 查一遍。结果：

**修了 2 处**：

1. **`familyEatTogether`（请客吃饭）同款 GMT+8 时区 bug**（原 3843-3853 行）：用 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 判断当前处于早/午/晚餐哪个时段，这两个方法内部用系统默认时区的 `Calendar.getInstance()` 构造时间边界（见 [TimeUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/TimeUtil.java) `isCompareTimeStr`/`getCalendarByTimeMillis`），跟上一条记录里 `deliverMsgSend` 的时区 bug 是同一根因、同一个 `TimeUtil` 系统时区问题的另一处命中。改成直接用 `MyUtils.getInstance().get(Calendar.HOUR_OF_DAY)` 取 GMT+8 小时数比较，不再经过 `TimeUtil` 的字符串时间比较。**没有**顺手给 `TimeUtil.isAfterTimeStr`/`isBeforeTimeStr` 本身加 GMT+8 重载——那样改动面更大（`doc/GR-Sync.md` 早前统计过这两个方法在全项目还有很多其它调用点），只在这一处绕开。
2. **`assignFamilyMember`（顶梁柱）空列表保护缺失**（原 3778 行）：`jsonObject.getJSONArray("assignConfigList")` 取到空/缺失数组时直接往下 `RandomUtil.nextInt(0, assignConfigList.length() - 1)` 会传入非法区间（`0, -1`）抛异常。虽然外层 `try/catch` 会吞掉（不炸整个 `family()`），但对齐 Sesame-AG `assignFamilyMember` 的显式判空提前返回，改成 `optJSONArray` + 判空/判 0 长度提前 return 并打日志，问题原因更清楚，不是"莫名其妙这一轮顶梁柱没执行"。

**对照过，判断不需要改的差异**（AG 更完善，但不构成 M 这边的实际缺陷）：

- **`familySign`（签到）没有本地当日去重 flag**：AG 有 `Status.hasFlagToday(FLAG_FARM_FAMILY_SIGNED)` 双重保险，M 完全依赖服务端下发的 `familySignTips` 布尔值（`family()` 里 `if (familySignTips && ...) familySign();`）。服务端已经会在签到后把 `familySignTips` 置 false，本地没有二次保险不算错误，只是少一层防御，没有改。
- **`familyAwardList`（领奖）没有饲料容量检查**：AG 对 `awardType == "ALLPURPOSE"` 的奖励会先查 `prepareFarmAwardCapacity(count)`，容量不够就跳过保留下次领取。M 直接无条件领取所有可领奖励。M 代码库里没有现成的"饲料容量查询/预留"基础设施，这是要新增能力而不是照抄一行判断，本次没有做，是否需要防止饲料溢出浪费需要用户确认后再单独处理。
- **`familyFeedFriendAnimal`（喂鸡）**：AG 对错误码 `388`/"小鸡太小" 有专门的静默日志分支，且循环内命中当日总上限会提前整体退出（省 RPC）；M 都会落到通用的"喂食失败"日志分支，且达到上限后仍会对剩余动物逐个尝试（每个都失败一次 391，不会执行成功但会多打几条失败日志、多打几次 RPC）。不影响最终结果，只是效率和日志噪音差异，没有改。
- **`familyShareToFriends`（分享好友）里 `user.getId() != UserIdMap.getCurrentUid()`**：用 `!=` 比较 `String` 是可疑写法，但这行前面已经有 `!familyUserIds.contains(user.getId())`（family 成员本来就包含自己）先把自己过滤掉了，这个 `!=` 判断实际上是从未真正生效过的冗余条件，不是导致"把自己分享进去"这类实际错误的原因，判断为无害死代码，没有单独修。M 用的是 `batchInviteP2P` 逐个邀请，AG 用的是完全不同的 `FriendSelectionModelField`/`inviteFriendVisitFamily` 批量邀请架构（含"选中邀请"/"选中不邀请"模式），两边分享好友这部分本来就是不同世代的实现，没有可比性，不评估细节差异。

**验证**：`./gradlew compileNormalDebugJavaWithJavac -q` 编译通过，无新增警告或错误（仅剩既有 `Status.java` unchecked 警告，与本次无关）。未运行模拟器/真机验证 `familyEatTogether` 修复后在非东八区设备上的实际触发时机，未验证 `assignFamilyMember` 空列表分支的真实触发场景（正常情况下 `assignConfigList` 应该不会为空，这是防御性修复）。

### 2026-09-12：修复道早安（deliverMsgSend）两个实际缺陷——参考 Sesame-AG，不是 GR

用户反馈"之前就是道早安有问题"，所以 GR2026 才整体改用从 Sesame-AG 移植来的 `AntFarmFamily.kt`（上一条家庭功能记录里已经确认 M 的 Java `family()` 没有照抄 GR 的 Kotlin 重写）。这次直接对照 Sesame-AG 当前的 `AntFarmFamily.kt#deliverMsgSend`（`E:\Work\Sesame-AG\app\src\main\java\io\github\aoguai\sesameag\task\antFarm\AntFarmFamily.kt:1023-1184`）逐行核对 M 的 [AntFarm.java](../app/src/main/java/io/github/aw1y2z/sesame/model/task/antFarm/AntFarm.java) 同名方法，找到两个真实缺陷并修了：

1. **时间窗口用了系统默认时区，不是北京时间**（AntFarm.java 原 3909-3920 行）：AG 用 `MyUtils.getInstance()`（GMT+8）判断"是否在 06:00-10:00"，M 原来是裸 `Calendar.getInstance()`。宿主设备系统时区不是东八区时，这个窗口判断会整体偏移，导致道早安要么提前不执行要么错过窗口——这类问题 `doc/GR-Sync.md` 早前就点名过 M 的 `TimeUtil` 大部分方法仍是系统默认时区，这是其中一个具体命中的实例。改成 `MyUtils.getInstanceGMT8()`（通过 `MyUtils.getInstance()`，本会话早前加的委托）。
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
- [WatermarkUtil.java](../app/src/main/java/io/github/aw1y2z/sesame/util/WatermarkUtil.java)：native 库未加载时的水印兜底文案从固定的"免费模块 交流QQ群:xxx"改成 `BuildConfig.VERSION_NAME + "  " + BuildConfig.BUILD_TIME`。对齐 GR `e22079f9`。**这个改动在 M 上实际意义更大**：`doc/GR-Sync.md` 已经确认 M 的 `watermark.cpp`/CMakeLists.txt 是死代码、没接入 Gradle 构建，`isLibraryLoaded` 恒为 `false`，也就是说这个"兜底"文案其实是 M 当前唯一会走到的路径，不是极端情况兜底。

**明确没有移植（评估过，判断当前不适合硬搬）**：

- **`model/task/fish`（福气鱼塘）**：`main_my` 里有独立的 `FishTask`/`AntFishpondTaskListMap` 等一整套模块（提交 `8b01744e` 起 9 个提交）。`doc/GR-Sync.md` 之前已经把这个列为同步候选，本次没有再重复评估细节；工作量和风险都不小（RPC 层、任务列表、黑名单、设置项注册全套），按"宁可不做也不要做一半"的原则本次不动，需要时应单独作为一次完整任务来做。
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

## 附录：GR2026 MyUtils.java 逐项说明（背景参考，非本次改动记录）

`MyUtils.java` 混合三类内容：① GMT+8 日历、null 安全 JSON 构造、版本隔离的异常标记存储等通用工具；② 作者账号上观察到的"已知会失败/已知会触发风控"硬编码开关（`_关闭XX` 系列、`_访问被拒绝*`/`_系统出错正在排查*` key、`_不是有效的入参` 任务 ID 黑名单）；③ GR 品牌相关（`getAppTitleExt`）。GR2026 内 44 个文件引用 `MyUtils.*`，覆盖 `hook/ApplicationHook.java`、几乎所有 `model/task/*` 模块等——任何整段照搬 GR 的 task/model 代码都会连带引入这个依赖。

| 成员 | GR2026 中的实际作用（示例调用点） | 合并到 M 时的处理建议 |
|---|---|---|
| `getInstance()` | 统一 GMT+8 日历入口，`entity/FriendWatch.java:121-123`、`hook/ApplicationHook.java:427,635` 等广泛使用 | 已移植（见上）。M 现有 `TimeUtil` 其余方法仍是系统默认时区，后续合并依赖 GMT+8 的具体业务逻辑时要显式改用 `getInstanceGMT8()`，不要假设默认时区已经是东八区 |
| `newJSONObject(String)` / `newJSONObject()` | 空/非 `{` 开头输入返回空 JSONObject，解析异常吞掉返回空对象；`data/RuntimeInfo.java:41-54` 用于状态文件读取容错 | 已移植，语义原样保留（含吞异常这一点） |
| `getSp功能异常` / `setSp功能异常` | 按 `errorMessage` 是否包含"访问被拒绝"/"系统出错"做一次性熔断，key 按 App 版本隔离；调用点如 `AntMember.java:1521-1525`、`MerchantService.java:20-67`、`ConsumeGold.java:177-182` | 机制已移植（`getSpFunctionError`/`setSpFunctionError`），具体 key 常量未移植——合并对应模块时再按需添加 |
| `closeVerification()` / `closeErrorFunction()` / `closeUnRpc()` | 恒返回 `true`，分别用于跳过拼手速/派遣动物等触发验证码的操作（`AntForestV2.java:633,645,1638`、`AntMember.java:159`）、跳过已知会报错的功能（`AntFarm.java:2495`、`AntMember.java:679`）、跳过不支持 RPC 完成的任务（`AntForestV2.java:2468`） | 已移植但改为读 `AppConfig`（默认 true）而非硬编码——函数名读起来像"可配置开关"，GR 里实际是硬编码，容易被当成已验证的安全跳过直接复制；M 版本是真的可配置 |
| `_不是有效的入参`（任务 ID 黑名单） | 硬编码若干活动任务 ID，`AntMember.java:1290` 命中则跳过 | 未移植，强版本/账号特定，会过期 |
| `getAppTitleExt()` | 按包名前缀 `"kt"` 返回 `"GR"` 品牌后缀，`data/ViewAppInfo.java:34` | 未移植，GR 品牌相关 |
| `recordUserName()` / `mUidMap` | 日志里把 UID 替换成可读昵称，`ApplicationHook.java:346,357,739` | 已移植，SharedPreferences 命名改为 M 自己的 |
| `encryptData()` / `decryptData()` | 在 `MyUtils` 里是直通（no-op）；GR2026 同时存在 `util/AESUtil.java` 里 `//CHANGE BY KT` 标记的同名真实加解密实现（`AESUtil.java:93,107`） | 未移植（无调用对象）。以后合并涉及这两个名字的代码，必须先确认源头是哪个版本，不能等价替换 |

### 每次合并 GR 代码的检查清单

1. `grep -n "MyUtils\." <待合并文件>`：命中的先查上表落在哪一类，已移植的直接接到本项目 `MyUtils.xxx`；未移植的按建议处理，不要囫囵抄。
2. `closeVerification()`/`closeErrorFunction()`/`closeUnRpc()` 在本项目已经是真开关（读 `AppConfig`），合并时确认默认值（`true`）符合预期，不需要额外处理；如果 GR 新增了同类恒真函数（新方法名），要按同样思路加成可配置项，而不是原样抄硬编码。
3. 不要迁移新出现的 `_访问被拒绝*`/`_系统出错正在排查*`/黑名单类硬编码字符串或 ID 常量本身，只迁移使用 `getSpFunctionError`/`setSpFunctionError` 机制的调用方式。
4. 涉及"加密/解密"命名的调用，先确认源头是 GR 的 `MyUtils`（直通）还是 `AESUtil`（真实实现）。
5. 合并后跑一次 `./gradlew compileNormalDebugJavaWithJavac` 确认编译通过，再补一条本页记录（改了什么、跳过了什么、为什么）。
