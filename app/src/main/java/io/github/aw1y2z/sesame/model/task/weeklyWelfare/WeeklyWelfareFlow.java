package io.github.aw1y2z.sesame.model.task.weeklyWelfare;

import java.util.function.BooleanSupplier;

/** 签到与独立的第七日奖励领取控制，不依赖Android。移植自 GR 分支，见 docs/MyFix.md。 */
public final class WeeklyWelfareFlow {
    private WeeklyWelfareFlow() { }

    public static final class Offer {
        public final int day;
        public final boolean signed;
        public final String basePrize;
        public final String prize;

        public Offer(int day, boolean signed, String basePrize, String prize) {
            this.day = day;
            this.signed = signed;
            this.basePrize = basePrize;
            this.prize = prize;
        }
    }

    public interface Port {
        Offer query() throws Exception;
        boolean sign(Offer offer) throws Exception;
        boolean collect() throws Exception;
    }

    public enum Result { UNKNOWN_STATE, SIGN_DISABLED, SIGN_SKIPPED, SIGNED, ALREADY_SIGNED,
        SIGN_NOT_CONFIRMED, PRIZE_COLLECTED, PRIZE_SKIPPED }

    public static Result run(Port port, BooleanSupplier signEnabled, BooleanSupplier prizeEnabled) throws Exception {
        Offer offer = port.query();
        if (!validDay(offer)) return Result.UNKNOWN_STATE;
        boolean signedNow = false;
        if (!offer.signed) {
            if (!signEnabled.getAsBoolean()) return Result.SIGN_DISABLED;
            if (!amount(offer.basePrize) || !amount(offer.prize)) return Result.UNKNOWN_STATE;
            if (!port.sign(offer)) return Result.SIGN_SKIPPED;
            signedNow = true;
        }
        if (offer.day != 7 || !prizeEnabled.getAsBoolean()) {
            return signedNow ? Result.SIGNED : Result.ALREADY_SIGNED;
        }
        if (signedNow) {
            offer = port.query();
            if (!validDay(offer) || offer.day != 7 || !offer.signed) return Result.SIGN_NOT_CONFIRMED;
        }
        if (!prizeEnabled.getAsBoolean()) return Result.PRIZE_SKIPPED;
        return port.collect() ? Result.PRIZE_COLLECTED : Result.PRIZE_SKIPPED;
    }

    private static boolean validDay(Offer offer) { return offer != null && offer.day >= 1 && offer.day <= 7; }

    private static boolean amount(String value) {
        return value != null && value.matches("[0-9]{1,9}(\\.[0-9]{1,4})?");
    }
}
