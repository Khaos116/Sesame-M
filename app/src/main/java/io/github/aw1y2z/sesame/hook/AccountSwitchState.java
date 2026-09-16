package io.github.aw1y2z.sesame.hook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 自动切号的纯策略判断：到没到切换间隔、当前是否空闲、要切到哪个账号。
 * 不碰任何 IO/反射，方便单独验证逻辑。移植自 GR 分支，见 doc/MyFix.md。
 */
final class AccountSwitchState {
    private String account;
    private String roundStartAccount;
    private boolean armed;
    private boolean failed;
    private long idleSince = -1;

    /** 开启开关或用户变更激活状态时调用，以当前账号为本轮首个账号并重置状态。 */
    synchronized void onActivate(String account) {
        if (account == null || account.isEmpty()) return;
        this.roundStartAccount = account;
        this.account = account;
        this.armed = true;
        this.failed = false;
        this.idleSince = -1;
    }

    /** 每次切号完成或进入新账号时调用。 */
    synchronized void onRound(String account) {
        if (account == null || account.isEmpty()) return;
        if (roundStartAccount == null) roundStartAccount = account;
        if (Objects.equals(this.account, account) && armed) return;
        this.account = account;
        armed = !failed;
        idleSince = -1;
    }

    /** 关闭开关：清空状态、清空轮次起始账号与冷却。 */
    synchronized void disabled() {
        account = null;
        roundStartAccount = null;
        armed = false;
        failed = false;
        idleSince = -1;
    }

    synchronized void fail() {
        armed = false;
        failed = true;
        idleSince = -1;
    }

    /** 判断下一个目标是否为本轮起始账号（即代表本轮全部账号已遍历完毕，即将开始新一轮）。 */
    synchronized boolean isRoundEnd(String next) {
        return roundStartAccount != null && roundStartAccount.equals(next);
    }

    synchronized String getRoundStartAccount() {
        return roundStartAccount;
    }

    /** 轮内切换固定15秒；整轮结束切回首个账号时冷却设定的整轮间隔（默认2小时）。 */
    synchronized long targetIntervalMillis(String next, int roundIntervalSeconds) {
        if (isRoundEnd(next)) {
            return intervalMillis(roundIntervalSeconds);
        }
        return AccountSwitchIntervalDraft.ACCOUNT_INTERVAL_SECONDS * 1000L;
    }

    synchronized boolean ready(String current, boolean enabled, boolean idle,
            boolean validationPending, long now, int roundIntervalSeconds, String next) {
        if (!enabled) { disabled(); return false; }
        if (!armed || failed) return false;
        if (!Objects.equals(account, current)) { armed = false; idleSince = -1; return false; }
        if (!idle || validationPending) {
            idleSince = -1;
            return false;
        }
        if (idleSince < 0 || now < idleSince) idleSince = now;
        if (now - idleSince < targetIntervalMillis(next, roundIntervalSeconds)) return false;
        armed = false;
        return true;
    }

    synchronized String waitPhase(long now, int roundIntervalSeconds, boolean idle, String next) {
        if (failed) return "PAUSED";
        if (!idle) return "WAIT_TASKS";
        return isRoundEnd(next) ? "ROUND_COOLDOWN" : "COUNTDOWN";
    }

    synchronized void defer() { if (!failed) armed = true; }

    static long intervalMillis(int seconds) {
        return Math.max(AccountSwitchIntervalDraft.MIN_SECONDS, Math.min(86400, seconds)) * 1000L;
    }

    static long timeoutMillis(int seconds) {
        return Math.max(15, Math.min(120, seconds)) * 1000L;
    }

    static boolean validUid(String uid) {
        return uid != null && uid.matches("[A-Za-z0-9_@.+:-]{1,128}");
    }

    /** 用排序后的固定顺序选下一个账号，避免账号列表顺序被支付宝重排后来回横跳。 */
    static String next(List<String> localHistory, String current) {
        if (localHistory == null || localHistory.size() > 32 || !validUid(current)) return null;
        List<String> candidates = new ArrayList<>();
        for (String uid : localHistory) {
            if (validUid(uid) && !candidates.contains(uid)) candidates.add(uid);
        }
        Collections.sort(candidates);
        if (candidates.size() < 2) return null;
        int at = candidates.indexOf(current);
        for (int step = 1; step <= candidates.size(); step++) {
            String uid = candidates.get((at + step) % candidates.size());
            if (!uid.equals(current)) return uid;
        }
        return null;
    }
}
