package io.github.aw1y2z.sesame.data.task;

import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

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
        UNKNOWN
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

    /** 领奖动作，返回 true 表示领取成功 */
    public interface Award {
        boolean run();
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

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, Forge forge) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, forge, null);
        }

        public Site(String listField, String logPrefix, String bizKey, String taskSceneCode, StatusProbe probe) {
            this(listField, logPrefix, bizKey, taskSceneCode, TaskAlternative.DEFAULT_VERSION, null, probe);
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
            this.listField = listField;
            this.logPrefix = logPrefix;
            this.bizKey = bizKey;
            this.taskSceneCode = taskSceneCode;
            this.version = version;
            this.forge = forge;
            this.probe = probe;
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
            boolean blacklistAllowed = site == null || site.probe == null;
            MessageUtil.beginDeferBlackList(); // 领奖触发的拉黑先入缓冲，列表确认已领到则丢弃

            try {
                claimed = award.run();
                if (site != null && site.probe != null) {
                    TimeUtil.sleep(1500);
                    ProbeResult result = site.probe.probe(key);
                    blacklistAllowed = result != ProbeResult.UNKNOWN;
                    claimed = isReceived(result);
                    if (claimed) {
                        log.accept("任务尝试🎁领奖已按列表确认[" + title + "]");
                        claimed = true;
                    }
                }
            } finally {
                MessageUtil.endDeferBlackList(!claimed && blacklistAllowed);
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
        // 交易/履约类（识别依据见 TaskAlternative.TRANSACTION_KEYWORDS）：**一律不发任何申报/伪造请求**
        // （伪申报会被服务端判风险操作、回 1009），并当场交自动黑名单停掉。
        // 不能指望下面"🧊兜底未生效"那条：跳过路径的 outcome 多为 UNABLE，永远不会 markTriggered，
        // 于是任务只会在每轮被反复跳过、永远进不了黑名单（实测农场饲料任务即如此）。
        if (site != null && site.bizKey != null && TaskAlternative.isTransactionTask(site.bizKey)) {
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
        if (isSameDayTriggered(key) && autoBlackList(site, title)) {
            clearTriggered(key);
            Status.flagToday(flag);
            log.accept("任务尝试🧊兜底未生效[" + title + "]#已交自动黑名单");
            return Outcome.UNABLE;
        }
        if (Status.hasFlagToday(flag)) {
            Log.i(TAG, "今日已试[" + title + "]#跳过");
            return Outcome.TRIED_TODAY;
        }
        // 响应触发的自动拉黑先入缓冲：等列表核对后决定落盘/丢弃（列表确认完成就不该拉黑）
        boolean listDone = false;
        boolean blacklistAllowed = site == null || site.probe == null;
        Outcome outcome = Outcome.RETRY;
        MessageUtil.beginDeferBlackList();
        try {
            outcome = attempt.run();
            // 列表复核：完成与否一律以任务列表为准——只有列表能宣布"完成"，响应不可信
            if (site != null && site.probe != null) {
                TimeUtil.sleep(1500);
                ProbeResult pr = site.probe.probe(key);
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
        } finally {
            MessageUtil.endDeferBlackList(!listDone && blacklistAllowed);
        }
        if (listDone) {
            log.accept("任务尝试✅列表已确认完成[" + title + "]");
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
            } else {
                // 伪申报：doFarmTask 响应不可信（常回 102 而任务已生效），成败一律以任务列表为准
                JSONObject triggered = TaskAlternative.trigger(null, null, title, site.bizKey, site.taskSceneCode,
                        site.version, site.logPrefix, log::accept);
                if (triggered == null) return Outcome.RETRY;
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
