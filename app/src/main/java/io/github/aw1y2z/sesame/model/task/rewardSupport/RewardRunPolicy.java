package io.github.aw1y2z.sesame.model.task.rewardSupport;

import java.util.Objects;

/** Pure policy shared by the new opt-in reward tasks. 移植自 GR 分支，见 doc/MyFix.md。 */
public final class RewardRunPolicy {
    private RewardRunPolicy() { }

    public static boolean sameAccount(String expected, String actual) {
        return expected != null && !expected.isEmpty() && Objects.equals(expected, actual);
    }

    public static boolean mayQuery(long now, long nextQuery) {
        return now >= nextQuery;
    }

    public static boolean mayAttempt(String day, String attemptedDay) {
        return day != null && !day.isEmpty() && !day.equals(attemptedDay);
    }

    public static boolean mayCheckIn(String action) {
        return "CHECK_IN".equals(action);
    }
}
