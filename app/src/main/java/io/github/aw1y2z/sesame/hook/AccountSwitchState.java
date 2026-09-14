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
    private boolean armed;
    private boolean failed;
    private long idleSince = -1;

    synchronized void onRound(String account) {
        if (account == null || account.isEmpty()) return;
        if (Objects.equals(this.account, account) && armed) return;
        this.account = account;
        armed = !failed;
        idleSince = -1;
    }

    synchronized void disabled() {
        armed = false;
        failed = false;
        idleSince = -1;
    }

    synchronized void fail() {
        armed = false;
        failed = true;
        idleSince = -1;
    }

    synchronized boolean ready(String current, boolean enabled, boolean idle,
            boolean validationPending, long now, int intervalSeconds) {
        if (!enabled) { disabled(); return false; }
        if (!armed || failed) return false;
        if (!Objects.equals(account, current)) { armed = false; idleSince = -1; return false; }
        if (idleSince < 0 || now < idleSince) idleSince = now;
        if (!idle || validationPending) return false;
        if (now - idleSince < intervalMillis(intervalSeconds)) return false;
        armed = false;
        return true;
    }

    synchronized String waitPhase(long now, int intervalSeconds, boolean idle) {
        if (failed) return "PAUSED";
        if (idleSince < 0 || now < idleSince || now - idleSince < intervalMillis(intervalSeconds)) return "COUNTDOWN";
        return idle ? "COUNTDOWN" : "WAIT_TASKS";
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
