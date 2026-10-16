package io.github.aw1y2z.sesame.data.task;

import org.json.JSONArray;
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
 * <p><b>核心原则：动作响应不可信——完成 / 领奖 / 拉黑一律以任务列表为准</b>（调用点经 {@link Site#probe} 提供列表状态探针）。
 * 本类只管**策略**，任务怎么完成、奖励怎么领仍由各模块决定：
 * <ul>
 *   <li><b>完成</b>：尝试后重拉列表，{@link #isDone} 才算完成；列表仍待办或拉取失败时，响应说"成功"也不认；</li>
 *   <li><b>领奖</b>：领奖响应失败时以 {@link #isReceived} 复核，避免 102 / 限流误判；</li>
 *   <li><b>当天只试一次</b>：做不了（{@link Outcome#UNABLE}）打当日标记，次日随 status.json 重置；</li>
 *   <li><b>失败分级</b>：{@link Outcome#RETRY}（102 / 限流 / 异常）不打标记，留待下一轮；</li>
 *   <li><b>兜底升级</b>：{@link Outcome#UNSUPPORTED}（400000040）由本类代为伪申报（{@code TaskAlternative.trigger}，
 *       交易/履约类已内部禁止）；传了 {@link Forge} 时优先做真实行为。伪申报后同一天仍在待办 ⇒ 交自动黑名单
 *       （{@link MessageUtil#MarkTaskBlackList}，照走"3 天解禁、累计 3 次永久"）。</li>
 * </ul>
 * <p>列表未确认完成时，响应触发的拉黑会被丢弃（延迟落盘），单次响应无法拉黑真任务。
 * <p>周期/持续型任务（见 {@link #isCyclicTask}）由真实行为推进、常驻列表，调用点应跳过。
 * <p>带同轮核对（{@code TaskAlternative.verify}）的调用点自行伪申报并返回 {@link Outcome#FORGED}，
 * {@link Site} 传 null 即可。
 */
public class TaskAttemptPolicy {

    private static final String TAG = TaskAttemptPolicy.class.getSimpleName();

    /** 当日已尝试标记前缀，实际键为 {@code attempt::<定位键>} */
    private static final String FLAG_PREFIX = "attempt::";

    /** 已交给兜底方案的记录：{定位键: 记录时间戳}，跨天留存，用于下一轮判定兜底是否生效 */
    private static final String KEY_TRIGGERED = "taskAttemptPolicy.triggeredFallback";

    /** 兜底记录保留时长：任务消失后不会有人来清理，超过此时长直接丢弃 */
    private static final long TRIGGERED_STALE = 604800000L;

    /**
     * 临时故障类（{@link Outcome#RETRY}）当日最多尝试次数。
     * <p>RETRY 的语义是"下一轮再试"，但服务端状态长期不推进时会变成整天重试：
     * 场景卡容器子任务数量多，单轮即可发出上百次请求。故按次计数，
     * 用尽即当日不再试（次日标记清空后自动恢复）。
     */
    private static final int RETRY_MAX_PER_DAY = 3;

    /**
     * 伪申报兜底当日最多几发。多发无用：同一发没让列表变完成，再发同样的请求也不会变。
     * <p>该计数**与当日标记独立**：调用方重拉列表会换新 key（多档任务带档位），
     * 只有计数才挡得住"换个键又把同一发打一遍"。
     */
    private static final int TRIGGER_MAX_PER_DAY = 1;

    /** 列表复核的轮询间隔与轮数：服务端异步落定的任务（如森林「消耗活力值得机会」）要十几秒才变完成，
     *  留足到 ~30s 以覆盖落定偏慢的情况（原先 6 轮≈18s 仍会把这类任务误判成失败）。 */
    private static final long PROBE_INTERVAL_MS = 3000L;
    private static final int PROBE_ROUNDS = 10;

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
         * <p>各模块列表的状态字符串一致（FINISHED/RECEIVED/其余视为 TODO），
         * 服务端新增状态时只在此处调整。
         */
        public static ProbeResult of(String taskStatus) {
            if ("FINISHED".equals(taskStatus)) {
                return FINISHED;
            }
            if ("RECEIVED".equals(taskStatus)) {
                return RECEIVED;
            }
            return TODO;
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
            JSONObject bizInfo = new JSONObject(bizInfoStr);
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
            JSONObject jo = new JSONObject(listSupplier.get());
            if (!check.ok(jo)) {
                return ProbeResult.UNKNOWN;
            }
            JSONArray ja = listOf.apply(jo);
            if (ja == null) {
                return ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject t = ja.optJSONObject(i);
                String status = t == null ? null : statusOf.apply(t);
                if (status != null) {
                    return ProbeResult.of(status);
                }
            }
            return ProbeResult.GONE;
        } catch (Throwable t) {
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

    /** 无打满判据时的站点（保持旧调用点的简明写法）。 */
    public static Site site(String listField, String logPrefix, String bizKey, String taskSceneCode,
                            StatusProbe probe) {
        return new Site(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe, null);
    }

    /**
     * 尝试动作。
     * <p>只应返回 {@link Outcome#DONE} / {@link Outcome#RETRY} / {@link Outcome#UNABLE}
     * / {@link Outcome#UNSUPPORTED}（交给本类伪申报）/ {@link Outcome#FORGED}（调用点自行伪申报）。
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
        /** 列表状态探针；null 表示调用点自行判定（沿用响应），非 null 时**完成 / 领奖 / 拉黑**都以列表为准 */
        final StatusProbe probe;
        /** 当日额度是否用尽；null 表示无此判据。命中只打当日标记、不进黑名单 */
        final ExhaustedProbe exhausted;
        /**
         * 该任务的完成条件是**服务端的真实行为计数**（如累计开箱数），申报类动作推不动它。
         * <p>为 true 时：伪申报失败**不进自动黑名单**——伪申报本来就不是它的完成路径，
         * 按"兜底未生效"拉黑会把它永久停掉（表现为奖励再也领不到）。
         */
        final boolean realAction;
        /**
         * 伪申报（doFarmTask）用的场景码：**必须是本任务所属模组的场景**（庄园 {@code ANTFARM}、
         * 农场 {@code ORCHARD}…），冒用会被判"场景不合法"。默认 {@link TaskAlternative#DEFAULT_SCENE_CODE}。
         */
        final String sceneCode;
        /**
         * 伪申报（doFarmTask）用的来源：取**本家 RPC 的 source**（农场 {@code ANTFARM_ORCHARD}、
         * 新村 {@code AST}…）。默认 {@link TaskAlternative#DEFAULT_SOURCE}。
         */
        final String source;
        /**
         * 信任响应：该任务是**多档里程碑**型（如森林「消耗活力值得机会」2/2——一次兑换只推进进度、
         * 列表状态不变，兑换满才 FINISHED）。
         * <p>为 true 时**跳过列表复核**、直接以响应为准：探针在这种中间态上永远判"未完成"，
         * 会把一次成功的兑换改判成做不了，进而挡住后续档位（实测卡在 1/2）。
         * <p>最终状态仍由调用方重拉列表核对（如抽抽乐每档后的 doublecheck）。
         */
        final boolean trustResponse;

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, Forge forge) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, forge, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe, null);
        }

        /** 带「当日打满」判据的简写形态（无 forge）。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode,
                    StatusProbe probe, ExhaustedProbe exhausted) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe, exhausted);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, null, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, null, probe, null);
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
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted, realAction,
                    TaskAlternative.DEFAULT_SCENE_CODE);
        }

        /** 完整形态 + 指定伪申报场景码（农场传 ORCHARD、森林传 ANTFOREST…），来源用默认 H5。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe, ExhaustedProbe exhausted, boolean realAction,
                    String sceneCode) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted, realAction,
                    sceneCode, TaskAlternative.DEFAULT_SOURCE);
        }

        /** 完整形态 + 指定伪申报场景码与来源。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe, ExhaustedProbe exhausted, boolean realAction,
                    String sceneCode, String source) {
            this(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted, realAction,
                    sceneCode, source, false);
        }

        /** 完整形态 + 信任响应（多档里程碑任务：列表中间态不作数，见 {@link #trustResponse}）。 */
        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, String version,
                    Forge forge, StatusProbe probe, ExhaustedProbe exhausted, boolean realAction,
                    String sceneCode, String source, boolean trustResponse) {
            this.listField = listField;
            this.logPrefix = logPrefix;
            this.bizKey = bizKey;
            this.taskSceneCode = taskSceneCode;
            this.version = version;
            this.forge = forge;
            this.probe = probe;
            this.exhausted = exhausted;
            this.realAction = realAction;
            this.sceneCode = sceneCode;
            this.source = source;
            this.trustResponse = trustResponse;
        }

        /** 返回一个只把伪申报场景码换掉的副本；用法 {@code new Site(...).withScene("ORCHARD")}。 */
        public Site withScene(String sceneCode) {
            return new Site(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted,
                    realAction, sceneCode, source, trustResponse);
        }

        /** 返回一个只把伪申报来源换掉的副本；用法 {@code new Site(...).withSource("AST")}。 */
        public Site withSource(String source) {
            return new Site(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted,
                    realAction, sceneCode, source, trustResponse);
        }

        /** 返回一个「信任响应」的副本；用法 {@code new Site(...).withTrustResponse()}。 */
        public Site withTrustResponse() {
            return new Site(listField, logPrefix, bizKey, taskSceneCode, version, forge, probe, exhausted,
                    realAction, sceneCode, source, true);
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
        // 领奖不受当日标记约束（否则当日漏领就没了）；成败以任务列表"已领到"为准，响应不可信（102/限流可能已发放）
        if (award != null) {
            boolean claimed = false;
            MessageUtil.beginDeferBlackList(); // 领奖触发的拉黑先入缓冲，列表确认已领到则丢弃

            try {
                claimed = award.run();
                if (!claimed && site != null && site.probe != null) {
                    TimeUtil.sleep(1500);
                    if (isReceived(site.probe.probe(key))) {
                        log.accept("任务尝试🎁领奖已按列表确认[" + title + "]");
                        claimed = true;
                    }
                }
            } finally {
                MessageUtil.endDeferBlackList(!claimed);
            }
            if (claimed) {
                clearTriggered(key);
                return Outcome.AWARDED;
            }
            log.accept("任务尝试⚠️领奖失败[" + title + "]");
            return Outcome.FAILED;
        }
        if (attempt == null) {
            return Outcome.SKIPPED;
        }
        String flag = FLAG_PREFIX + sanitize(key);
        // 交易/履约类（识别依据见 TaskAlternative 的关键词表）：**一律不发任何申报/伪造请求**
        // （伪申报会被服务端判风险操作、回 1009），并当场交自动黑名单停掉。
        // 不能指望下面"🧊兜底未生效"那条：跳过路径的 outcome 多为 UNABLE，永远不会 markTriggered，
        // 于是任务只会在每轮被反复跳过、永远进不了黑名单（实测农场饲料任务即如此）。
        if (site != null && site.bizKey != null && TaskAlternative.isTransactionTask(site.bizKey)) {
            // 这类任务常驻列表、每轮都在，拉黑是**一次性**动作；已处理过就直接静默跳过，
            // 否则每轮都重打一遍"永久拉黑"，日志被刷屏（且毫无新信息）。
            if (Status.hasFlagToday(flag)) {
                return Outcome.TRIED_TODAY;
            }
            // 底线：交易/支付类一次即**永久**拉黑（不进入"满 N 天解禁重试"的生命周期），绝不伪造
            boolean blacklisted = autoBlackList(site, title, true);
            Status.flagToday(flag);
            log.accept("任务尝试⏭️交易/履约类[" + title + "]"
                    + (blacklisted ? "#不申报，已永久拉黑" : "#不申报（自动黑名单列表未登记）"));
            return Outcome.UNABLE;
        }
        // **同一天内**又见到它（上次已伪申报、这轮仍在待办）⇒ 兜底没写成 ⇒ 按自动黑名单规则停掉。
        // 必须限定同一天：日任务次日会重新回到待办，那是新实例，据此拉黑会误伤能做的任务。
        // 可靠性依据：伪申报被服务端接受后任务会很快判定完成，故"同一天内再次出现"才是兜底没写成的可靠证据；
        // 跨天出现一律按新实例处理。
        // 真实行为类任务不走这条：伪申报不是它的完成路径，判"兜底未生效"是错怪
        if (isSameDayTriggered(key) && (site == null || !site.realAction) && autoBlackList(site, title)) {
            clearTriggered(key);
            Status.flagToday(flag);
            log.accept("任务尝试🧊兜底未生效[" + title + "]#已交自动黑名单");
            return Outcome.UNABLE;
        }
        if (Status.hasFlagToday(flag)) {
            Log.i(TAG, "今日已试[" + title + "]#跳过");
            return Outcome.TRIED_TODAY;
        }
        // 临时故障重试次数已用尽：当日不再尝试（次日标记清空后自动恢复）
        if (Status.getIntFlagToday("attemptRetry::" + sanitize(key)) >= RETRY_MAX_PER_DAY) {
            Log.i(TAG, "临时故障重试已用尽[" + title + "]#今日不再尝试");
            return Outcome.TRIED_TODAY;
        }
        // 当日额度已用尽（如 rightsTimes 已达上限）：只打当日标记、不进黑名单——
        // 这类任务每日重置，次日标记随 status.json 跨天清空即自动恢复尝试
        if (site != null && site.exhausted != null && site.exhausted.exhausted(key)) {
            Status.flagToday(flag);
            log.accept("任务尝试🎯当日已完成[" + title + "]#额度已用尽，今日不再尝试");
            return Outcome.UNABLE;
        }
        // 响应触发的自动拉黑先入缓冲：等列表核对后决定落盘/丢弃（列表确认完成就不该拉黑）
        boolean listDone = false;
        Outcome outcome = Outcome.RETRY;
        MessageUtil.beginDeferBlackList();
        try {
            outcome = attempt.run();
            // 列表复核：完成与否一律以任务列表为准——只有列表能宣布"完成"，响应不可信
            // （多档里程碑任务除外：中间态在列表上永远不完成，见 Site#trustResponse）
            if (site != null && site.probe != null && !site.trustResponse) {
                // 仅当响应成功时才长轮询等待异步落定（如森林「消耗活力值得机会」十几秒才变完成）；
                // 未成功的尝试无需空等，单次快速核对即可，避免把每个做不了的任务都拖满整段轮询窗口。
                ProbeResult pr = (outcome == Outcome.DONE)
                        ? probeWithPolling(site.probe, key)
                        : site.probe.probe(key);
                if (isDone(pr)) {
                    listDone = true;
                } else if (outcome == Outcome.DONE) {
                    // 响应说成功但列表没确认完成：响应不可信，一律不做数
                    outcome = pr == ProbeResult.TODO ? Outcome.UNABLE : Outcome.RETRY;
                }
            }
        } finally {
            MessageUtil.endDeferBlackList(!listDone);
        }
        if (listDone) {
            // 完全静默：核对是过程、不是结果，结果由调用方的「完成/领取」结果行表达（如 🧾完成、🎖️领取），
            // 这里再记一条只会与其重复。
            // 落当日标记：本日已完成的任务不必再核对。漏掉会让重拉列表的调用方
            // （如抽抽乐的多轮推进）每轮把它重跑一遍。
            Status.flagToday(flag);
            return Outcome.DONE;
        }
        // 行为伪造：把任务要求的真实行为用接口做出来；能伪造就不必退到伪申报
        if ((outcome == Outcome.UNABLE || outcome == Outcome.UNSUPPORTED)
                && site != null && site.forge != null && site.forge.run()) {
            markTriggered(key);
            Status.flagToday(flag);
            return Outcome.TRIGGERED;
        }
        if (outcome == Outcome.UNSUPPORTED) {
            if (site == null || site.bizKey == null) {
                outcome = Outcome.UNABLE;
            } else if (Status.getIntFlagToday("attemptTrigger::" + sanitize(key)) >= TRIGGER_MAX_PER_DAY) {
                // 伪申报当天已试过仍没做成 ⇒ 双保险没生效。按原有语义**收手并落当日标记**（进自动拉黑，
                // 避免每轮重打同一发）。触发计数与当日标记无关，故重拉列表换 key 时依然挡得住。
                log.accept("任务尝试⏳未完成[" + title + "]#今日不再尝试");
                Status.flagToday(flag);
                return Outcome.UNABLE;
            } else {
                // 伪申报：doFarmTask 响应不可信（常回 102 而任务已生效），成败一律以任务列表为准
                TaskAlternative.trigger(null, null, title, site.bizKey, site.taskSceneCode,
                        site.version, site.sceneCode, site.source, site.logPrefix, log::accept);
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
        if (outcome == Outcome.DONE) {
            return outcome;
        }
        // 本次已用掉一次伪申报兜底、列表却仍未确认完成：说明服务端确实没认这一发。
        // 此时**不落当日标记**——调用方（如多档任务重拉列表）会换新 key 再试，
        // 而那一发会被上面的伪申报去重计数挡住，既不会重复上报、也不会每轮重打。
        if (outcome == Outcome.UNSUPPORTED && Status.getIntFlagToday("attemptTrigger::" + sanitize(key)) > 0) {
            log.accept("任务尝试⏳未完成[" + title + "]#兜底已用过一次");
            return Outcome.UNABLE;
        }
        // 临时故障：计数后放行（次数上限由入口的前置检查把关，避免服务端状态不推进时整天重试）
        if (outcome == Outcome.RETRY) {
            String retryKey = "attemptRetry::" + sanitize(key);
            Status.setIntFlagToday(retryKey, Status.getIntFlagToday(retryKey) + 1);
            return outcome;
        }
        if (outcome == Outcome.UNABLE) {
            log.accept("任务尝试⏳未完成[" + title + "]#今日不再尝试");
        }
        Status.flagToday(flag);
        return outcome;
    }

    /**
     * 轮询探针直到列表宣布完成：有些任务服务端是**异步落定**的，响应回来时列表还是待办
     * （如森林「消耗活力值得机会」会在十几秒后才变完成），固定睡一次会把它误判成做不了。
     * <p>命中即返回；总等待上限 {@link #PROBE_ROUNDS} × {@link #PROBE_INTERVAL_MS}，
     * 未完成的常见情形仍只等一轮，不会拖慢日常。
     */
    private static ProbeResult probeWithPolling(StatusProbe probe, String key) {
        ProbeResult pr = null;
        for (int round = 1; round <= PROBE_ROUNDS; round++) {
            TimeUtil.sleep(PROBE_INTERVAL_MS);
            pr = probe.probe(key);
            if (isDone(pr)) {
                return pr;
            }
        }
        return pr;
    }

    /** 按自动黑名单规则记账（{@link MessageUtil#MarkTaskBlackList} 会跳过用户手动加入的条目与白名单项） */
    private static boolean autoBlackList(Site site, String title) {
        return autoBlackList(site, title, false);
    }

    /**
     * @param permanent true=交易/支付类：立即**永久**拉黑、永不自动解禁
     *                  （见 {@link MessageUtil#MarkTaskBlackListPermanent}）
     */
    private static boolean autoBlackList(Site site, String title, boolean permanent) {
        if (site == null || site.listField == null || site.listField.isEmpty()) {
            return false;
        }
        String[] target = MessageUtil.autoBlackListTarget(site.listField);
        if (target == null) {
            Log.i(TAG, "自动拉黑列表未登记:" + site.listField);
            return false;
        }
        if (permanent) {
            MessageUtil.MarkTaskBlackListPermanent(target[0], site.listField, target[1], title);
        } else {
            MessageUtil.MarkTaskBlackList(target[0], site.listField, target[1], title);
        }
        return true;
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
            return new JSONObject(RuntimeInfo.getInstance().getString(KEY_TRIGGERED));
        } catch (Throwable t) {
            return new JSONObject();
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

    /**
     * 定位键可能含 : / | 等字符，压成安全形式并限长（当日标记落进 status.json，伪申报记录落进 runtimeInfo.json）。
     * <p>超长时**头尾各留一段**：档位/序号在键尾（{@code #5}、{@code @2}），只截前段会让不同档位串成同一个键，
     * 首档的当日标记就会挡住后续档位（键越长的任务越容易踩到，实测最长已到 87 字符、余量不足 33）。
     */
    private static String sanitize(String key) {
        String safe = key.replaceAll("[^0-9A-Za-z_.\\-]", "_");
        if (safe.length() <= 120) {
            return safe;
        }
        // 头 40 + 分隔符 + 尾 79 = 120
        return safe.substring(0, 40) + "_" + safe.substring(safe.length() - 79);
    }
}
