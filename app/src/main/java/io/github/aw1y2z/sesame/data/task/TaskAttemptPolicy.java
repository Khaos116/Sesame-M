package io.github.aw1y2z.sesame.data.task;

import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.DailyTask;
import org.json.JSONArray;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;

/**
 * 任务尝试策略：服务端没有"保证成功"的完成接口、只能先尝试再以任务列表为准的一类任务（森林活力值、视频观看、
 * 新村木兰市集、神奇海洋、运动任务、游戏中心…）的统一处理。
 * <p>完成申报按列表确认；领奖接受业务成功响应，失败时由探针复核，查询失败不得拉黑。
 * 本类只管**策略**，任务怎么完成、奖励怎么领仍由各模块决定：
 * <ul>
 *   <li><b>完成</b>：尝试后重拉列表，{@link #isDone} 才算完成；列表仍待办或拉取失败时，响应说"成功"也不认；</li>
 *   <li><b>领奖</b>：领奖响应失败时以 {@link #isReceived} 复核，避免 102 / 限流误判；</li>
 *   <li><b>当天只试一次</b>：做不了（{@link Outcome#UNABLE}）打当日标记，次日随 status.json 重置；</li>
 *   <li><b>失败分级</b>：{@link Outcome#RETRY}（102 / 限流 / 异常）不打标记，留待下一轮；</li>
 *   <li><b>兜底升级</b>：{@link Outcome#UNSUPPORTED}（400000040）由本类代为伪申报（{@code TaskAlternative.trigger}，
 *       交易/履约类已内部禁止）；传了 {@link Forge} 时优先做真实行为。同日仍待办只防重，不据此拉黑。</li>
 * </ul>
 * <p>列表已完成、查询失败或仍需走后备完成路径时，丢弃普通尝试产生的拉黑记录。
 * <p>周期/持续型任务（见 {@link #isCyclicTask}）由真实行为推进、常驻列表，调用点应跳过。
 * <p>带同轮核对（{@code TaskAlternative.verify}）的调用点自行伪申报并返回 {@link Outcome#FORGED}，
 * {@link Site} 传 null 即可。
 */
public class TaskAttemptPolicy {

    private static final String TAG = TaskAttemptPolicy.class.getSimpleName();

    /** 当日已尝试标记前缀，实际键为 {@code attempt::<定位键>} */
    // 旧标记可能由验证/无响应误写；仅作废尝试标记，未确认上报回执独立保留。
    private static final String FLAG_PREFIX = "attemptV2::";

    /** 已交给兜底方案的记录：{定位键: 记录时间戳}，跨天留存，用于下一轮判定兜底是否生效 */
    private static final String KEY_TRIGGERED = "taskAttemptPolicy.triggeredFallback";

    /** 兜底记录保留时长：任务消失后不会有人来清理，超过此时长直接丢弃 */
    private static final long TRIGGERED_STALE = 604800000L;

    /**
     * 伪申报兜底当日最多几发。多发无用：同一发没让列表变完成，再发同样的请求也不会变。
     * <p>该计数**与当日标记独立**：调用方重拉列表会换新 key（多档任务带档位），
     * 只有计数才挡得住"换个键又把同一发打一遍"。
     */
    private static final int TRIGGER_MAX_PER_DAY = 1;

    /** 列表复核的轮询间隔与轮数：服务端异步落定的任务（如森林「消耗活力值得机会」）要十几秒才变完成 */
    private static final long PROBE_INTERVAL_MS = 3000L;
    private static final int PROBE_ROUNDS = 6;

    /** 处理结论 */
    public enum Outcome {
        /** 列表已确认完成（{@link #isDone}；完成任务后可能仍待领奖） */
        DONE,
        /** 临时故障（102 / 限流 / 异常）→ 不打标记，下一轮再试 */
        RETRY,
        /** 服务端明确表示客户端做不了（需真实操作）→ 打当日标记 */
        UNABLE,
        /** 400000040"不支持rpc调用"：由本类代为伪申报 */
        UNSUPPORTED,
        /** 调用点已自行伪申报（并已登记同轮核对） */
        FORGED,
        /** 返回给调用点：已伪申报，结果以任务列表为准 */
        TRIGGERED,
        /** 领奖成功（响应成功，或列表确认"奖励已领到"） */
        AWARDED,
        /** 领奖失败 */
        FAILED,
        /** 今日已试过，本轮跳过 */
        TRIED_TODAY,
        /** 无可执行动作，未处理 */
        SKIPPED
    }

    /**
     * 列表状态探针结果（{@link #isDone} / {@link #isReceived} 据此判定）。
     * <p>{@link #FINISHED}=已完成待领、{@link #RECEIVED}=已领到、{@link #TODO}=待办、
     * {@link #GONE}=已消失（视为完成且已领）、{@link #UNKNOWN}=拉取失败。
     */
    public enum ProbeResult {
        TODO,
        FINISHED,
        RECEIVED,
        GONE,
        UNKNOWN;

        /**
         * 把任务列表里的 {@code taskStatus} 映射为探针结果。
         * <p>只接受 FINISHED / RECEIVED / TODO，其余状态保留 UNKNOWN，
         * 服务端新增状态时只在此处调整。
         */
        public static ProbeResult of(String taskStatus) {
            if ("FINISHED".equals(taskStatus)) {
                return FINISHED;
            }
            if ("RECEIVED".equals(taskStatus)) {
                return RECEIVED;
            }
            return "TODO".equals(taskStatus) ? TODO : UNKNOWN;
        }
    }

    /** 是否算"完成"（已完成 / 已领到 / 已从列表消失，都算）。 */
    public static boolean isDone(ProbeResult r) {
        return r == ProbeResult.FINISHED || r == ProbeResult.RECEIVED || r == ProbeResult.GONE;
    }

    /** 是否算"奖励已领到"（仅已领到 / 已从列表消失）。 */
    public static boolean isReceived(ProbeResult r) {
        return r == ProbeResult.RECEIVED || r == ProbeResult.GONE;
    }

    /**
     * taskBaseInfo 是否表示"周期 / 持续型"任务（如"连续 N 天…"）。
     * <p>判据取 {@code bizInfo} 的 {@code countdown}/{@code deadline}/{@code curCycleFinishedBtn}。
     * <b>不要用 {@code taskBaseInfo.curCycleFinished}</b>——它几乎每个活力值任务都有，会误伤一大片。
     * <p>这类任务由真实行为推进、周期内常驻列表且无 RPC 可完成，调用点应跳过：不尝试、也不拉黑。
     */
    public static boolean isCyclicTask(JSONObject taskBaseInfo) {
        if (taskBaseInfo == null) {
            return false;
        }
        try {
            String bizInfoStr = taskBaseInfo.optString("bizInfo", "");
            if (bizInfoStr.isEmpty()) {
                return false;
            }
            JSONObject bizInfo = MyUtils.newJSONObject(bizInfoStr);
            return bizInfo.optBoolean("countdown", false)
                    || bizInfo.optBoolean("deadline", false)
                    || bizInfo.has("curCycleFinishedBtn");
        } catch (Throwable t) {
            return false;
        }
    }

    /** 列表状态探针：重拉对应模块任务列表，返回定位键对应任务的当前列表状态（找不到返回 {@link ProbeResult#GONE}）。 */
    public interface StatusProbe {
        ProbeResult probe(String key);
    }

    /** 拉取任务列表原始响应 */
    public interface RawSupplier {
        String get() throws Exception;
    }

    /** 列表响应是否可用（各模块校验方式不同：checkResultCode / checkMemo） */
    public interface ResponseCheck {
        boolean ok(JSONObject jo);
    }

    /**
     * 通用列表探针（顶层列表形态）：拉一次列表 → 用 {@code statusOf} 定位项并取状态。
     * <p>找不到该项即 {@link ProbeResult#GONE}（视为已完成且已领）；列表拉取或校验失败为
     * {@link ProbeResult#UNKNOWN}（调用方据此不做判定，避免误拉黑）。
     * <p>各模块差异只有三处：拉列表的 RPC、列表字段名、定位谓词，故统一到这里。
     * <p>{@code statusOf} 从列表项取出 {@code taskStatus}，返回 {@code null} 表示该项不匹配。
     */
    public static ProbeResult probeByList(String tag, RawSupplier listSupplier, String listField,
                                          ResponseCheck check, Function<JSONObject, String> statusOf) {
        return probeByNestedList(tag, listSupplier, check, jo -> jo.optJSONArray(listField), statusOf);
    }

    /**
     * 嵌套列表形态：任务数组在响应内层（如 {@code taskTriggerPlayInfo.taskList}）。
     * <p>与 {@link #probeByList} 同名会因函数式参数产生重载歧义，故单列方法名。
     *
     * @param listOf 从响应取出任务数组；取不到返回 null（视为 UNKNOWN）
     */
    public static ProbeResult probeByNestedList(String tag, RawSupplier listSupplier, ResponseCheck check,
                                                Function<JSONObject, JSONArray> listOf,
                                                Function<JSONObject, String> statusOf) {
        try {
            JSONObject jo = MyUtils.newJSONObject(listSupplier.get());
            if (!check.ok(jo)) {
                return ProbeResult.UNKNOWN;
            }
            JSONArray ja = listOf.apply(jo);
            if (ja == null) {
                return ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject t = ja.optJSONObject(i);
                if (t == null) return ProbeResult.UNKNOWN;
                String status = statusOf.apply(t);
                if (status != null) {
                    return ProbeResult.of(status);
                }
            }
            return ProbeResult.GONE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(tag, "probeByNestedList err:", t);
            return ProbeResult.UNKNOWN;
        }
    }

    /**
     * 当日额度是否已用尽（如 {@code rightsTimes >= rightsTimesLimit}）。
     * <p>与 {@link StatusProbe} 同源：探针本已拉到响应，次数信息顺手可取，不额外发请求。
     * <p>命中时只打**当日标记**、当日不再尝试；**不进黑名单**——这类任务是每日重置的，
     * 次日服务端次数归零、标记随 status.json 跨天清空，自然恢复尝试。
     * <p>判据取服务端的**名额**而非本地记账：`rightsTimes`（已用）/`rightsTimesLimit`（额度），
     * 二者相等即当日做满。服务端才是额度的权威，本地记账只用于避免重复请求。
     */
    public interface ExhaustedProbe {
        boolean exhausted(String key);
    }

    /** 领奖动作，返回 true 表示领取成功 */
    public interface Award {
        boolean run();
    }

    /**
     * 尝试动作。
     * <p>只应返回 {@link Outcome#DONE} / {@link Outcome#RETRY} / {@link Outcome#UNABLE}
     * / {@link Outcome#UNSUPPORTED}（交给本类伪申报）/ {@link Outcome#FORGED}（调用点自行伪申报）
     * / {@link Outcome#SKIPPED}（未发送请求，无需后备动作或列表核对）。
     */
    public interface Attempt {
        Outcome run();
    }

    /**
     * 行为伪造动作：把该任务要求的"真实行为"用接口做出来（浏览任务按 viewSec 等待、行走线路直接 map.go 等）。
     * <p>返回 true 表示已伪造出去，成败以任务列表为准。没有可用行为接口的任务不要传。
     */
    public interface Forge {
        boolean run();
    }

    /** 调用点信息：自动黑名单归属 + 伪申报参数 + 列表探针（都可为 null 表示"不参与"） */
    public static final class Site {
        /** 自动黑名单列表字段名（如 {@code AntOceanAntiepTaskList}）；null 表示调用点自行按规则记账 */
        final String listField;
        /** 伪申报日志前缀（如"海洋任务"） */
        final String logPrefix;
        /** 伪申报 bizKey（通常就是 taskType / taskId）；null 表示不代该调用点伪申报 */
        final String bizKey;
        final String taskSceneCode;
        final String version;
        /** 行为伪造动作；null 表示该任务没有可用的行为接口 */
        final Forge forge;
        /** 列表状态探针；null 表示调用点自行判定，领奖动作已经回查时不重复传入。 */
        final StatusProbe probe;
        /** 当日额度是否用尽；null 表示无此判据。命中只打当日标记、不进黑名单 */
        final ExhaustedProbe exhausted;
        /**
         * 该任务的完成条件是**服务端的真实行为计数**（如累计开箱数），申报类动作推不动它。
         * <p>为 true 时：伪申报失败**不进自动黑名单**——伪申报本来就不是它的完成路径，
         * 按"兜底未生效"拉黑会把它永久停掉（表现为奖励再也领不到）。
         */
        final boolean realAction;

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, Forge forge) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, forge, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe);
        }

        /** 带「当日打满」判据的简写形态（无 forge）。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode,
                    StatusProbe probe, ExhaustedProbe exhausted) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe, exhausted);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, null, probe);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, null);
        }

        /** 带「当日打满」判据的简写形态（无 forge、非真实行为类）。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe, ExhaustedProbe exhausted) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted, false);
        }

        /** 完整形态：{@code realAction=true} 表示完成条件在服务端真实行为计数上（如累计开箱） */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe, ExhaustedProbe exhausted, boolean realAction) {
            this.listField = listField;
            this.logPrefix = logPrefix;
            this.bizKey = bizKey;
            this.taskSceneCode = taskSceneCode;
            this.version = version;
            this.forge = forge;
            this.probe = probe;
            this.exhausted = exhausted;
            this.realAction = realAction;
        }
    }

    private TaskAttemptPolicy() {
    }

    /**
     * @param key     任务定位键，需跨轮稳定且同模块内唯一
     * @param title   任务展示名
     * @param award   领奖动作，null 表示当前无可领的奖
     * @param attempt 完成尝试动作，null 表示不可尝试
     * @param log     日志出口：森林传 {@code Log::forest}，其余传 {@code Log::other}
     * @param site    调用点信息，null 表示该调用点自行伪申报与记账（如已带同轮核对的模块）
     */
    public static Outcome handle(String key, String title, Award award, Attempt attempt,
                                 Consumer<String> log, Site site) {
        // 领奖不受尝试标记约束；成功响应不被异步列表覆盖，失败才回查。
        if (award != null) {
            boolean claimed = false;
            boolean checked = false;
            boolean blacklistAllowed = site == null || site.probe == null;
            MessageUtil.beginDeferBlackList(); // 领奖触发的拉黑先入缓冲，列表确认已领到则丢弃

            try {
                claimed = award.run();
                if (!claimed && site != null && site.probe != null) {
                    TimeUtil.sleep(1500);
                    ProbeResult result = site.probe.probe(key);
                    blacklistAllowed = result != ProbeResult.UNKNOWN;
                    claimed = isReceived(result);
                    if (claimed) {
                        log.accept("任务尝试🎁领奖已按列表确认[" + title + "]");
                        claimed = true;
                    }
                }
                checked = true;
            } finally {
                MessageUtil.endDeferBlackList(checked && !claimed && blacklistAllowed);
            }
            if (claimed) {
                clearTriggered(key);
                return Outcome.AWARDED;
            }
            log.accept("任务尝试⚠️领奖未确认[" + title + "]");
            return Outcome.FAILED;
        }
        if (attempt == null) {
            return Outcome.SKIPPED;
        }
        String flag = FLAG_PREFIX + sanitize(key);
        if (Status.hasFlagToday(flag) && !DailyTask.isManual()) {
            log.accept("任务尝试⏭️今日已尝试[" + title + "]#未标记成功，自动调度不重发；可点击立即执行重新检查");
            return Outcome.TRIED_TODAY;
        }
        // 关键词只能用于阻止交易申报，不能作为修改用户黑名单的证据。
        if (site != null && site.bizKey != null && TaskAlternative.isTransactionTask(site.bizKey)) {
            Status.flagToday(flag);
            log.accept("任务尝试⏭️交易/履约类[" + title + "]#不申报，不修改黑名单");
            return Outcome.UNABLE;
        }
        // 同日再次看到 TODO 可能只是异步结算；保留当日防重，不据此自动拉黑。
        if (isSameDayTriggered(key)) {
            Status.flagToday(flag);
            log.accept("任务尝试🕓上报后仍待确认[" + title + "]#今日不重复上报，不修改黑名单");
            return Outcome.TRIED_TODAY;
        }
        if (DailyTask.isManual() && Status.hasFlagToday(flag)) {
            Status.clearFlag(flag);
            log.accept("任务尝试🔄手动重试[" + title + "]#重新核对完成结果");
        }
        // 响应触发的自动拉黑先入缓冲：等列表核对后决定落盘/丢弃（列表确认完成就不该拉黑）
        boolean listDone = false;
        boolean checked = false;
        boolean blacklistAllowed = site == null || site.probe == null;
        Outcome outcome = Outcome.RETRY;
        MessageUtil.beginDeferBlackList();
        try {
            outcome = attempt.run();
            if (outcome == Outcome.SKIPPED) return outcome;
            // 列表复核：完成与否一律以任务列表为准——只有列表能宣布"完成"，响应不可信
            if (site != null && site.probe != null) {
                ProbeResult pr = probeWithPolling(site.probe, key);
                blacklistAllowed = pr != ProbeResult.UNKNOWN;
                if (isDone(pr)) {
                    listDone = true;
                } else if (pr == ProbeResult.UNKNOWN) {
                    outcome = Outcome.RETRY;
                    log.accept("任务尝试❓列表回查未确认[" + title + "]#本轮不追加完成请求、不写今日失败标记");
                } else if (outcome == Outcome.DONE) {
                    // 响应说成功但列表没确认完成：响应不可信，一律不做数
                    outcome = pr == ProbeResult.TODO ? Outcome.UNABLE : Outcome.RETRY;
                }
            }
            checked = true;
        } finally {
            MessageUtil.endDeferBlackList(checked && !listDone && blacklistAllowed
                    && !(outcome == Outcome.UNSUPPORTED && site != null
                        && (site.forge != null || (site.bizKey != null && !site.bizKey.isEmpty()
                            && site.taskSceneCode != null && site.version != null && !site.version.isEmpty())))
                    && !(outcome == Outcome.UNABLE && site != null && site.forge != null));
        }
        if (listDone) {
            return Outcome.DONE;
        }
        // 开箱依赖实际额度，尚未凑够时不走申报兜底，也不锁死当天后续检查。
        if (site != null && site.realAction) return outcome;
        // 行为伪造：把任务要求的真实行为用接口做出来；能伪造就不必退到伪申报
        if ((outcome == Outcome.UNABLE || outcome == Outcome.UNSUPPORTED)
                && site != null && site.forge != null && site.forge.run()) {
            markTriggered(key);
            Status.flagToday(flag);
            return Outcome.TRIGGERED;
        }
        if (outcome == Outcome.UNSUPPORTED) {
            if (site == null || site.bizKey == null || site.bizKey.isEmpty()
                    || site.taskSceneCode == null || site.version == null || site.version.isEmpty()) {
                outcome = Outcome.UNABLE;
            } else if (Status.getIntFlagToday("attemptTrigger::" + sanitize(key)) >= TRIGGER_MAX_PER_DAY) {
                // 伪申报当天已试过仍没做成 ⇒ 双保险没生效。按原有语义**收手并落当日标记**（进自动拉黑，
                // 避免每轮重打同一发）。触发计数与当日标记无关，故重拉列表换 key 时依然挡得住。
                log.accept("任务尝试⏳未完成[" + title + "]#今日不再尝试");
                Status.flagToday(flag);
                return Outcome.UNABLE;
            } else {
                // 伪申报：doFarmTask 响应不可信（常回 102 而任务已生效），成败一律以任务列表为准
                JSONObject triggered = TaskAlternative.trigger(null, null, title, site.bizKey, site.taskSceneCode,
                        site.version, site.logPrefix, log::accept);
                if (triggered == null) return Outcome.RETRY;
                String triggerKey = "attemptTrigger::" + sanitize(key);
                Status.setIntFlagToday(triggerKey, Status.getIntFlagToday(triggerKey) + 1);
                markTriggered(key);
                Status.flagToday(flag);
                return Outcome.TRIGGERED;
            }
        } else if (outcome == Outcome.FORGED) {
            markTriggered(key);
            Status.flagToday(flag);
            return Outcome.TRIGGERED;
        }
        if (outcome == Outcome.DONE || outcome == Outcome.RETRY) {
            return outcome;
        }
        if (outcome == Outcome.UNABLE) {
            log.accept("任务尝试⏳未完成[" + title + "]#今日不再尝试");
        }
        Status.flagToday(flag);
        return outcome;
    }

    private static ProbeResult probeWithPolling(StatusProbe probe, String key) {
        ProbeResult pr = null;
        for (int round = 1; round <= PROBE_ROUNDS; round++) {
            TimeUtil.sleep(PROBE_INTERVAL_MS);
            pr = probe.probe(key);
            if (pr == null || pr == ProbeResult.UNKNOWN || isDone(pr)) {
                return pr == null ? ProbeResult.UNKNOWN : pr;
            }
        }
        return pr;
    }

    /**
     * 上次伪申报是否发生在**同一天**；跨天则清掉记录并返回 false（日任务次日属新实例，不能据此判失败）。
     */
    private static boolean isSameDayTriggered(String key) {
        String safeKey = sanitize(key);
        JSONObject jo = readTriggered();
        if (!jo.has(safeKey)) {
            return false;
        }
        if (TimeUtil.isLessThanSecondOfDays(jo.optLong(safeKey), System.currentTimeMillis())) {
            clearTriggered(key);
            return false;
        }
        return true;
    }

    private static void markTriggered(String key) {
        try {
            JSONObject jo = readTriggered();
            pruneTriggered(jo);
            jo.put(sanitize(key), System.currentTimeMillis());
            RuntimeInfo.getInstance().put(KEY_TRIGGERED, jo.toString());
        } catch (Throwable t) {
            Log.err(TAG, "markTriggered err:", t);
        }
    }

    private static void clearTriggered(String key) {
        try {
            JSONObject jo = readTriggered();
            if (jo.remove(sanitize(key)) != null) {
                RuntimeInfo.getInstance().put(KEY_TRIGGERED, jo.toString());
            }
        } catch (Throwable t) {
            Log.err(TAG, "clearTriggered err:", t);
        }
    }

    private static JSONObject readTriggered() {
        try {
            return MyUtils.newJSONObject(RuntimeInfo.getInstance().getString(KEY_TRIGGERED));
        } catch (Throwable t) {
            return MyUtils.newJSONObject();
        }
    }

    /** 任务已不在列表里时不会有人来清理，故写记录时顺手丢掉过期的 */
    private static void pruneTriggered(JSONObject jo) {
        long now = System.currentTimeMillis();
        List<String> stale = new ArrayList<>();
        Iterator<String> keys = jo.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (now - jo.optLong(key) > TRIGGERED_STALE) {
                stale.add(key);
            }
        }
        for (String key : stale) {
            jo.remove(key);
        }
    }

    /** 定位键可能含 : / | 等字符，压成安全形式并限长（当日标记落进 status.json，伪申报记录落进 runtimeInfo.json） */
    private static String sanitize(String key) {
        String safe = key.replaceAll("[^0-9A-Za-z_.\\-]", "_");
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }
}
