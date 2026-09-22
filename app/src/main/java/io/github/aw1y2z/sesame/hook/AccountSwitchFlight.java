package io.github.aw1y2z.sesame.hook;

import java.util.Objects;

/**
 * 一次登录切换调用的结果核对：等确认目标账号连续两次读到才算数，避免读到一次就误判。
 * 移植自 GR 分支，见 docs/MyFix.md。
 */
class AccountSwitchFlight {
    enum Outcome { WAIT, SUCCESS, REJECTED, LATE, CANCELLED }
    final String source, target;
    final long deadline;
    volatile boolean returned, accepted;
    volatile boolean pageBlocked;
    boolean timedOut, cancelled;
    int stable;

    AccountSwitchFlight(String source, String target, long start, long timeout) {
        this.source = source; this.target = target; deadline = start + timeout;
    }

    boolean updateTimeout(long now) {
        if (!timedOut && now >= deadline) { timedOut = true; return true; }
        return false;
    }

    Outcome observe(long now, String auth, String local, boolean validationPending) {
        updateTimeout(now);
        if (!returned || validationPending || !AccountSwitchState.validUid(auth) || !Objects.equals(auth, local)) {
            stable = 0; return Outcome.WAIT;
        }
        boolean switched = target.equals(auth);
        boolean stayed = source.equals(auth) && !accepted;
        if (!switched && !stayed) { stable = 0; return Outcome.WAIT; }
        if (++stable < 2) return Outcome.WAIT;
        if (cancelled) return Outcome.CANCELLED;
        if (timedOut) return Outcome.LATE;
        return switched && accepted ? Outcome.SUCCESS : Outcome.REJECTED;
    }
}
