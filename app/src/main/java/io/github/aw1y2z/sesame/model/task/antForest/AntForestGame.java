package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestGame {

    private static final String TAG = AntForestGame.class.getSimpleName();
    private static final String SLJYD_FLAG = "forest::sljydReported";
    private final AntForestV2 host;

    AntForestGame(AntForestV2 host) {
        this.host = host;
    }

    void checkAndHandleWhackMole() {
        if (MyUtils.closeVerification()) return;
        try {
            // 获取当前选择的索引 (0, 1, 或 2)
            int modeIndex = (host.whackModeName != null) ? host.whackModeName.getValue() : 0;

            // 如果索引为 0 (关闭)，直接返回
            if (modeIndex == 0) {
                return;
            }

            // 检查执行时间
            int hour = MyUtils.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            if (hour >= host.earliestwhackMoleTime.getValue()) {
                String whackMoleFlag = "forest::whackMole::executed";
                if (Status.hasFlagToday(whackMoleFlag)) {
                    return;
                }

                // 根据索引匹配模式
                switch (modeIndex) {
                    case 1: // 兼容模式
                        Log.record("触发任务🎮拼手速:兼容模式");
                        WhackMole.setTotalGames(1);
                        int defaultMoleCount = (host.whackModeCount != null) ? host.whackModeCount.getValue() : 15;
                        WhackMole.setMoleCount(defaultMoleCount);
                        WhackMole.start(WhackMole.Mode.COMPATIBLE);
                        break;

                    case 2: // 激进模式
                        Log.record("触发任务🎮拼手速:激进模式");
                        int configGames = (host.whackModeGames != null) ? host.whackModeGames.getValue() : 5;
                        WhackMole.setTotalGames(configGames);
                        WhackMole.start(WhackMole.Mode.AGGRESSIVE);
                        break;
                }
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.printStackTrace(TAG, t);
        }
    }

    int startEnergyRain() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.startEnergyRain());
            TimeUtil.sleep(500);
            if (!MessageUtil.checkResultCode(TAG, jo)) return -1;
            String token = jo.optString("token");
            JSONObject difficultyInfo = jo.optJSONObject("difficultyInfo");
            JSONArray bubbleEnergyList = difficultyInfo == null ? null : difficultyInfo.optJSONArray("bubbleEnergyList");
            if (!(jo.opt("token") instanceof String) || token.isEmpty()
                    || bubbleEnergyList == null || bubbleEnergyList.length() == 0) {
                Log.record("能量雨：开局回执缺少凭据或能量列表，本轮不结算、不标记完成");
                return -1;
            }
            int sum = 0;
            for (int i = 0; i < bubbleEnergyList.length(); i++) {
                Object value = bubbleEnergyList.opt(i);
                int amount = bubbleEnergyList.optInt(i, -1);
                if (!(value instanceof Number) || amount <= 0
                        || ((Number) value).doubleValue() != amount || amount > Integer.MAX_VALUE - sum) {
                    Log.record("能量雨：开局能量数值无效，本轮不结算、不标记完成");
                    return -1;
                }
                sum += amount;
            }
            TimeUtil.sleep(5000L + (long) (Math.random() * 5000L));
            jo = MyUtils.newJSONObject(AntForestRpcCall.energyRainSettlement(sum, token));
            if (!MessageUtil.checkResultCode(TAG, jo)) return -1;
            Toast.show("获得了[" + sum + "g]能量[能量雨]");
            Log.forest("收能量雨🌧️" + energyRainLabel(sum) + "[" + sum + "g]");
            AntForestV2.totalCollected += sum;
            Statistics.addData(Statistics.DataType.COLLECTED, sum);
            if (sum == 50) Status.flagToday("EnergyRain::PlayGame");
            TimeUtil.sleep(500);
            return sum;
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "startEnergyRain err:", th);
        }
        return -1;
    }

    private static String energyRainLabel(int sum) {
        return sum == 120 ? "普通局" : sum == 70 ? "次卡/赠送局" : sum == 50 ? "游戏局" : "";
    }

    private int drainEnergyRainQuota() {
        int played = 0;
        for (int round = 0; round < 6; round++) {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainHome());
            if (!MessageUtil.checkResultCode(TAG, jo) || !Boolean.TRUE.equals(jo.opt("canPlayToday"))) break;
            if (startEnergyRain() < 0) break;
            played++;
            TimeUtil.sleep(1000L + (long) (Math.random() * 4000L));
        }
        return played;
    }

    void useEnergyRainCard() {
        if (Status.hasFlagToday("forest::exchangeLimit::SK20250117005985")) return;
        try {
            // 商店兑换 限时能量雨卡
            host.mallHelper.exchangeBenefit("SK20250117005985");
            TimeUtil.sleep(2000);
            JSONObject jo;
            do {
                TimeUtil.sleep(1000);
                // 背包查找 能量雨卡
                jo = null;
                List<JSONObject> list = AntForestProp.getPropGroup(AntForestProp.getForestPropVOList(), PropGroup.energyRain.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null) {
                    break;
                }
                // 使用能量雨卡
            } while (AntForestProp.consumeProp(jo));
        } catch (Throwable th) {
            Log.err(TAG, "useEnergyRainCard err:", th);
        }
    }

    void energyRain() {
        try {
            boolean started = drainEnergyRainQuota() > 0;
            JSONObject home = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainHome());
            TimeUtil.sleep(500);
            if (MessageUtil.checkResultCode(TAG, home) && Boolean.TRUE.equals(home.opt("canGrantStatus"))) {
                JSONObject grantList = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainCanGrantList());
                if (MessageUtil.checkResultCode(TAG, grantList)) {
                    JSONArray grants = grantList.optJSONArray("grantInfos");
                    Set<String> selected = host.giveEnergyRainList.getValue();
                    for (int i = 0; grants != null && i < grants.length(); i++) {
                        JSONObject grant = grants.optJSONObject(i);
                        String uid = grant == null ? "" : grant.optString("userId");
                        if (uid.isEmpty() || !selected.contains(uid) || !Boolean.TRUE.equals(grant.opt("canGrantedStatus"))) continue;
                        JSONObject result = MyUtils.newJSONObject(AntForestRpcCall.grantEnergyRainChance(uid));
                        if (MessageUtil.checkResultCode(TAG, result)) {
                            Log.forest("送能量雨🌧️[" + UserIdMap.getMaskName(uid) + "]");
                            started |= drainEnergyRainQuota() > 0;
                        }
                        break;
                    }
                }
            }
            if (!Status.hasFlagToday("EnergyRain::PlayGame")) {
                if (!Status.hasFlagToday(SLJYD_FLAG)) {
                    checkAndDoEndGameTask();
                    TimeUtil.sleep(4000);
                }
                for (int round = 0; round < 3; round++) {
                    JSONObject chance = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainHome());
                    if (!MessageUtil.checkResultCode(TAG, chance)) break;
                    if (Boolean.TRUE.equals(chance.opt("canPlayToday"))) {
                        started |= drainEnergyRainQuota() > 0;
                        break;
                    }
                    TimeUtil.sleep(3000);
                }
            }
            if (started) host.energyHelper.finishEnergyRainFlow();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "energyRain err:", th);
        }
    }

    /** 森林救援队的状态必须来自成功且结构有效的查询。 */
    static String energyRainSljydStatus(JSONObject jo) {
        if (!MessageUtil.checkResultCode(TAG, jo)) return null;
        JSONObject group = jo.optJSONObject("energyRainEndGameGroupTask");
        if (group == null) {
            return !jo.has("energyRainEndGameGroupTask") && Boolean.FALSE.equals(jo.opt("needInitTask")) ? "MISSING" : null;
        }
        JSONArray tasks = group.optJSONArray("taskInfoList");
        if (tasks == null) return null;
        String status = "MISSING";
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
            if (base == null || !(base.opt("taskType") instanceof String) || !(base.opt("taskStatus") instanceof String)
                    || base.optString("taskType").isEmpty() || base.optString("taskStatus").isEmpty()) return null;
            if ("GAME_DONE_SLJYD".equals(base.optString("taskType"))) status = base.optString("taskStatus");
        }
        return status;
    }

    private static boolean isSljydDone(String status) {
        return "FINISHED".equals(status) || "RECEIVED".equals(status) || "MISSING".equals(status);
    }

    public static void checkAndDoEndGameTask() {
        if (Status.hasFlagToday(SLJYD_FLAG)) return;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainEndGameList());
            if (!MessageUtil.checkResultCode(TAG, jo)) return;
            boolean needInit = Boolean.TRUE.equals(jo.opt("needInitTask"));
            String status = energyRainSljydStatus(jo);
            if (needInit) {
                JSONObject init = MyUtils.newJSONObject(AntForestRpcCall.initTask("GAME_DONE_SLJYD"));
                if (!MessageUtil.checkResultCode(TAG, init)) return;
            }
            if (needInit || "TODO".equals(status) || "NOT_TRIGGER".equals(status)) {
                final String[] after = {status};
                int successes = GameTask.Forest_sljyd.reportSync("森林救援队", 1, null, () -> {
                    TimeUtil.sleep(2000);
                    after[0] = energyRainSljydStatus(MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainEndGameList()));
                    return isSljydDone(after[0]);
                });
                if (successes <= 0) {
                    Log.record("森林救援队🐱上报失败#下次运行重试");
                    return;
                }
                // 已确认上报受理与游戏局实际到账分开记账，避免状态延迟导致反复上报。
                Status.flagToday(SLJYD_FLAG);
                for (int round = 0; round < 2 && !isSljydDone(after[0]); round++) {
                    TimeUtil.sleep(2000);
                    after[0] = energyRainSljydStatus(MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRainEndGameList()));
                }
                Log.record("森林救援队🐱上报已受理[" + successes + "次]，回查状态=" + after[0]
                        + (isSljydDone(after[0]) ? "，等待能量雨额度下发" : "，结果尚未确认，后续仅查额度"));
                return;
            }
            if (isSljydDone(status)) {
                Status.flagToday(SLJYD_FLAG);
            } else if (!Status.hasFlagToday("forest::sljydNoStatus")) {
                Status.flagToday("forest::sljydNoStatus");
                Log.record("森林救援队🐱任务状态未确认(" + status + ")#稍后重试，不标记完成");
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.printStackTrace("执行能量雨后续任务出错:", th);
        }
    }

    private static int gameQuota(JSONObject rights, String key) {
        Object value = rights.opt(key);
        if (!(value instanceof Number)) return -1;
        try {
            int quota = new java.math.BigDecimal(value.toString()).intValueExact();
            return quota >= 0 ? quota : -1;
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    public void doforestgame() {
        try {
            String response = AntForestRpcCall.queryGameList();
            JSONObject jo = MyUtils.newJSONObject(response);

            // 验证请求是否成功
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                Log.error("queryGameList 失败: " + jo.optString("desc"));
                return;
            }

            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights != null) {
                int perTime = drawRights.has("quotaPerTime") ? gameQuota(drawRights, "quotaPerTime") : 100;
                int quotaCanUse = gameQuota(drawRights, "quotaCanUse");
                int quotaLimit = gameQuota(drawRights, "quotaLimit");
                int usedQuota = gameQuota(drawRights, "usedQuota");
                if (perTime <= 0 || quotaCanUse < 0 || quotaLimit < 0 || usedQuota < 0) {
                    Log.record("森林乐园：查询额度缺失或无效，本轮不执行开箱和任务上报");
                    return;
                }

                // 换算实际宝箱次数
                int canUseCount = quotaCanUse / perTime;
                int limitCount = quotaLimit / perTime;
                int usedCount = usedQuota / perTime;

                // 1. 处理待开启奖励 (批量开启)
                if (canUseCount > 0) {
                    Log.record("森林乐园正在一次性开启 " + canUseCount + " 个宝箱...");
                    JSONObject drawJo = MyUtils.newJSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
                    if (!MessageUtil.checkResultCode(drawJo)) {
                        return;
                    }
                    JSONArray awardList = drawJo.optJSONArray("gameCenterDrawAwardList");
                    int totalEnergy = 0;
                    List<String> otherAwards = new ArrayList<>();

                    if (awardList != null) {
                        for (int i = 0; i < awardList.length(); i++) {
                            JSONObject award = awardList.optJSONObject(i);
                            if (award == null) {
                                continue;
                            }
                            String type = award.optString("awardType");
                            String name = award.optString("awardName");
                            int count = award.optInt("awardCount");
                            Log.forest("森林乐园🎁开宝箱得[" + name + "*" + count + "]");
                            if ("ENERGY".equals(type)) {
                                totalEnergy += count;
                            } else {
                                otherAwards.add(name + "x" + count);
                            }
                        }
                    }
                    Statistics.addData(Statistics.DataType.COLLECTED, totalEnergy);
                    // 输出统计结果
                    StringBuilder logMsg = new StringBuilder("森林乐园🎁[开宝箱]共计");
                    if (totalEnergy > 0) {
                        logMsg.append("获得能量").append(totalEnergy).append("g");
                    }
                    if (!otherAwards.isEmpty()) {
                        if (totalEnergy > 0) {
                            logMsg.append(", ");
                        }
                        logMsg.append("其他: ").append(String.join("/", otherAwards));
                    }
                    Log.forest(logMsg.toString());
                    Toast.show(logMsg.toString());

                }

                // 2. 判断是否需要刷任务
                int remainToTask = limitCount - usedCount;
                if (remainToTask > 0) {
                    GameTask.Forest_slxcc.report("森林", remainToTask);
                } else {
                    Log.record("今日森林乐园游戏任务已满额");
                }
            } else {
                Log.record("森林乐园：查询响应缺少开箱额度，本轮不执行开箱和任务上报");
            }

        } catch (TaskCancelledException | CancellationException e) {
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace("doforestgame 流程异常", t);
        }
    }

    TaskAttemptPolicy.ProbeResult probeOptionalPlayStatus(String scene, String type, int previousRights) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo)) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            JSONObject info = jo.optJSONObject("taskTriggerPlayInfo");
            JSONArray tasks = info == null ? null : info.optJSONArray("taskList");
            if (tasks == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            TaskAttemptPolicy.ProbeResult result = TaskAttemptPolicy.ProbeResult.GONE;
            boolean matched = false;
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null || task.optString("sceneCode").isEmpty() || task.optString("taskType").isEmpty())
                    return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                if (!scene.equals(task.optString("sceneCode")) || !type.equals(task.optString("taskType"))) continue;
                if (matched) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                matched = true;
                String status = task.optString("taskStatus");
                if (TaskStatus.FINISHED.name().equals(status)) result = TaskAttemptPolicy.ProbeResult.FINISHED;
                else if (TaskStatus.RECEIVED.name().equals(status)) result = TaskAttemptPolicy.ProbeResult.RECEIVED;
                else if (TaskStatus.TODO.name().equals(status)) {
                    // 多阶段任务仍可为 TODO；只确认本次阶段，不能把下一阶段记为失败。
                    result = task.optInt("rightsTimes", previousRights) > previousRights
                            ? TaskAttemptPolicy.ProbeResult.FINISHED : TaskAttemptPolicy.ProbeResult.TODO;
                } else return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            return result;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "probeOptionalPlayStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    void queryOptionalPlay() {
        try {
            // 每个任务的每个阶段（taskType#rightsTimes）本轮最多动作一次：同一阶段重复动作会白跑，
            // 但阶段推进（领取后 rightsTimes 递增）要允许再做，故去重键带序号。
            Set<String> attempted = new HashSet<>();
            boolean boxesChecked = false;
            int round = 0;
            // 首轮按实际任务阶段数算定上限（见 optionalPlayRoundLimit），之后不再变
            int roundLimit = 0;
            while (round++ < roundLimit || roundLimit == 0) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryOptionalPlay());
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    Log.forest("森林乐园⚠️任务列表拉取失败[" + TaskAlternative.describe(jo) + "]");
                    return;
                }
                if (!jo.has("taskTriggerPlayInfo")) {
                    Log.forest("森林乐园⚠️任务列表缺少任务信息");
                    return;
                }
                JSONObject taskTriggerPlayInfo = jo.optJSONObject("taskTriggerPlayInfo");
                if (taskTriggerPlayInfo == null || !taskTriggerPlayInfo.has("taskList")) {
                    Log.forest("森林乐园⚠️任务列表缺少任务明细");
                    return;
                }
                JSONArray taskList = taskTriggerPlayInfo.optJSONArray("taskList");
                if (taskList == null) return;
                // 账号当前没有乐园限定任务，直接收工
                if (taskList.length() == 0) {
                    Log.forest("森林乐园🎈当前无限定任务");
                    return;
                }
                if (roundLimit == 0) {
                    roundLimit = optionalPlayRoundLimit(taskList);
                }
                // 本轮是否还有新的动作（不含已试过的），没有就收工
                boolean progressed = false;

                for (int j = 0; j < taskList.length(); j++) {
                    JSONObject task = taskList.optJSONObject(j);
                    if (task == null) {
                        continue;
                    }
                    String taskStatus = task.optString("taskStatus");
                    int alreadyReceiveAwardCount = task.optInt("alreadyReceiveAwardCount");
                    int awardCount = task.optInt("awardCount");
                    int awardCountForReceive = awardCount - alreadyReceiveAwardCount;
                    int rightsTimesLimit = task.optInt("rightsTimesLimit");
                    int rightsTimes = task.optInt("rightsTimes");
                    String awardType = task.optString("awardType", "能量");
                    JSONObject bizInfo = task.optJSONObject("bizInfo");
                    if (bizInfo == null) {
                        continue;
                    }
                    String title = bizInfo.optString("title");
                    String source = task.optString("source", "ch_appcenter__chsub_9patch");
                    String sceneCode = task.optString("sceneCode", "");
                    String taskType = task.optString("taskType", "");
                    String groupId = task.optString("groupId", "");
                    // 同一 taskType 可能分多阶段（rightsTimes 递增），用序号区分，避免误判为已试
                    if (taskType.isEmpty() || sceneCode.isEmpty() || title.isEmpty()) continue;
                    String attemptKey = sceneCode + "/" + taskType + "#" + rightsTimes;
                    String awardKey = "forest::optionalplay::award::" + attemptKey + "#" + awardCount;
                    String actionType = bizInfo.optString("actionType", "");

                    // 黑名单任务跳过
                    if (host.AntForestVitalityTaskList.getValue().contains(title)) {
                        continue;
                    }
                    // 底线：交易/支付/充值类一律不申报、直接跳过，绝不伪造。
                    // 必须同时查 groupId：充值类的 taskType 常无关键词，判据在 groupId（cz=充值）
                    if (TaskAlternative.isTransactionTask(taskType)
                            || TaskAlternative.isTransactionTask(groupId)
                            || TaskAlternative.isTransactionTask(sceneCode)) {
                        Log.forest("森林乐园⏭️交易/履约类[" + title + "]#不申报，未提交请求");
                        continue;
                    }

                    // 已完成待领：领奖。已领满（awardCountForReceive<=0）不再重复请求；
                    // 当日已领过的同一阶段也不再重复请求（避免重复累计能量）
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        if (awardCountForReceive > 0 && !Status.hasFlagToday(awardKey) && attempted.add(awardKey)) {
                            progressed = true;
                            JSONObject joReceived = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAwardopengreen(source, sceneCode, taskType));
                            if (MessageUtil.checkSuccess(TAG, joReceived)) {
                                Status.flagToday(awardKey);
                                int incAwardCount = joReceived.optInt("incAwardCount");
                                JSONObject taskConfigResultVO = joReceived.optJSONObject("taskConfigResultVO");
                                if (taskConfigResultVO != null) {
                                    awardType = taskConfigResultVO.optString("awardType", awardType);
                                }
                                // 能量统计
                                if ("能量".equals(awardType) || "ENERGY".equals(awardType)) {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + incAwardCount + "g能量]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                                } else {
                                    Log.forest("森林乐园🎖️领取[" + title + "]奖励[" + awardType + "*" + incAwardCount + "]");
                                }
                            }
                        }
                        continue;
                    }

                    // 待完成 / 多阶段未满：上报完成
                    if (TaskStatus.TODO.name().equals(taskStatus) || rightsTimes < rightsTimesLimit) {
                        if (!attempted.add(attemptKey)) {
                            continue;
                        }
                        String label = rightsTimesLimit > 1
                                ? title + "(" + (rightsTimes + 1) + "/" + rightsTimesLimit + ")" : title;
                        // 累计开箱受当天额度限制，不套用“一次做完”的完成/失败标记。
                        if (isUnwrapAction(actionType)) {
                            // 与常规开箱共用额度池；保留常规开箱关闭时的补做入口，每轮只查一次。
                            if (!boxesChecked) {
                                boxesChecked = true;
                                if (openForestLeyuanBoxes(title) == Outcome.DONE) progressed = true;
                            }
                            continue;
                        }
                        // 其余任务仍走统一上报和列表回查。
                        Outcome outcome = TaskAttemptPolicy.handle(
                                "forest::optionalplay::" + attemptKey, label, null,
                                () -> host.taskHelper.attemptFinishTask(sceneCode, taskType, label), Log::forest,
                                new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林乐园限定任务",
                                        taskType, sceneCode, TaskAlternative.DEFAULT_VERSION, null,
                                        (k) -> probeOptionalPlayStatus(sceneCode, taskType, rightsTimes),
                                        null,
                                        false).withScene("ANTFOREST"));
                        // 被当日标记挡下（已试过）不算推进，否则每轮都白转一遍
                        if (outcome == Outcome.DONE || outcome == Outcome.TRIGGERED || outcome == Outcome.AWARDED) {
                            progressed = true;
                        }
                    }
                }

                // 一整轮里既无可领也无可做：可重复任务已打满次数，收工
                if (!progressed) {
                    break;
                }
                // 服务端状态推进有延迟，留出落态时间再重拉
                TimeUtil.sleep(1500);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "queryOptionalPlay err:", th);
        }
    }

    /** 开箱类动作（累计开箱 N 个）：完成条件在服务端的开箱计数上，只能真开箱，申报无效 */
    static boolean isUnwrapAction(String actionType) {
        return "ACC_UNWRAP".equals(actionType) || "UNWRAP".equals(actionType);
    }

    /**
     * 开箱类乐园任务的尝试动作：把当前可开宝箱一次开完。
     * <p>只开**待开的**（{@code quotaCanUse}），没有可开的就直接返回 UNABLE——
     * 额度不足时保留任务，等待后续累计，不伪申报完成。
     */
    Outcome openForestLeyuanBoxes(String taskTitle) {
        try {
            TimeUtil.sleep(0);
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryGameList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return Outcome.RETRY;
            }
            JSONObject drawRights = jo.optJSONObject("gameCenterDrawRights");
            if (drawRights == null) {
                return Outcome.RETRY;
            }
            int perTime = gameQuota(drawRights, "quotaPerTime");
            int beforeQuota = gameQuota(drawRights, "quotaCanUse");
            if (perTime <= 0 || beforeQuota < 0) return Outcome.RETRY;
            int canUseCount = beforeQuota / perTime;
            if (canUseCount <= 0) {
                // 没有可开的箱子：完成不了是"箱子不够"，不是接口不支持，不能退到伪申报
                return Outcome.UNABLE;
            }
            TimeUtil.sleep(0);
            JSONObject drawJo = MyUtils.newJSONObject(AntForestRpcCall.drawGameCenterAward(canUseCount));
            if (!MessageUtil.checkResultCode(TAG, drawJo)) {
                return MessageUtil.isRetryable(drawJo) || MessageUtil.isServerBusy(drawJo)
                        ? Outcome.RETRY : Outcome.UNABLE;
            }
            JSONObject after = drawJo.optJSONObject("gameCenterDrawRights");
            int afterQuota = after == null ? -1 : gameQuota(after, "quotaCanUse");
            if (afterQuota < 0 || afterQuota >= beforeQuota) {
                Log.markUnresolved();
                Log.record("森林乐园[开箱回查]：额度缺失或未减少，本轮停止");
                return Outcome.RETRY;
            }
            Log.forest("森林乐园🎁开宝箱×" + canUseCount + "#用于[" + taskTitle + "]");
            return Outcome.DONE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "openForestLeyuanBoxes err:", t);
        }
        return Outcome.RETRY;
    }

    /**
     * 乐园限定任务单轮最大重拉次数：正常由「本轮无任何可执行/可领取动作」提前收工，
     * 此上限只防服务端恒回 TODO 时死循环。
     * <p>必须按**各任务阶段数之和**给足：每次重拉每个任务最多推进一档，多个多阶段任务
     * 串行推进，固定值会在任务没做满时就收工。
     */
    int optionalPlayRoundLimit(JSONArray taskList) {
        int total = 0;
        for (int i = 0; i < taskList.length(); i++) {
            JSONObject task = taskList.optJSONObject(i);
            if (task == null) {
                continue;
            }
            int limit = task.optInt("rightsTimesLimit", 1);
            total += Math.max(limit, 1);
        }
        // 至少 12，最多 60：给足阶段数，也防任务列表异常时长时间空转
        return Math.min(Math.max(total + 6, 12), 60);
    }
}
