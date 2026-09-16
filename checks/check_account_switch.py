"""Regression check for multi-account round rotation and cooldown logic."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/hook"

classes = ROOT / "app/build/intermediates/javac/normalDebug/compileNormalDebugJavaWithJavac/classes"

TEST_CODE = r'''
package io.github.aw1y2z.sesame.hook;

import java.util.Arrays;
import java.util.List;

public class AccountSwitchCheck {
    public static void main(String[] args) {
        testDraftIntervalResolution();
        testRoundCycleAndCooldown();
        testToggleResetAndNewAnchor();
        testStatusMessages();
        System.out.println("PASS: Account rotation with 15s intra-round switch and 2h round cooldown passed");
    }

    private static void testDraftIntervalResolution() {
        assert AccountSwitchIntervalDraft.ACCOUNT_INTERVAL_SECONDS == 15;
        assert AccountSwitchIntervalDraft.DEFAULT_SECONDS == 7200;
        assert AccountSwitchIntervalDraft.MIN_SECONDS == 15;
        assert AccountSwitchIntervalDraft.MAX_SECONDS == 86400;

        assert AccountSwitchIntervalDraft.resolve(true, "", 7200) == 7200;
        assert AccountSwitchIntervalDraft.resolve(true, "   ", 7200) == 7200;
        assert AccountSwitchIntervalDraft.resolve(true, "15", 7200) == 15;
        assert AccountSwitchIntervalDraft.resolve(true, "3600", 7200) == 3600;
        assert AccountSwitchIntervalDraft.resolve(false, "invalid", 3600) == 3600;

        try {
            AccountSwitchIntervalDraft.resolve(true, "14", 7200);
            throw new AssertionError("Should reject < 15s");
        } catch (NumberFormatException expected) { }

        try {
            AccountSwitchIntervalDraft.resolve(true, "86401", 7200);
            throw new AssertionError("Should reject > 86400s");
        } catch (NumberFormatException expected) { }
    }

    private static void testRoundCycleAndCooldown() {
        AccountSwitchState state = new AccountSwitchState();
        List<String> accounts = Arrays.asList("account_A", "account_B", "account_C");

        // User activates switch while on Account B
        state.onActivate("account_B");
        assert "account_B".equals(state.getRoundStartAccount()) : "start account should be B";

        long t0 = 100000L;
        // B is running tasks (not idle)
        assert !state.ready("account_B", true, false, false, t0, 7200, "account_C") : "not ready when running";
        assert "WAIT_TASKS".equals(state.waitPhase(t0, 7200, false, "account_C")) : "phase should be WAIT_TASKS";

        // B finishes tasks (idle)
        assert !state.ready("account_B", true, true, false, t0, 7200, "account_C") : "not ready immediately at t0";
        assert "COUNTDOWN".equals(state.waitPhase(t0, 7200, true, "account_C")) : "phase should be COUNTDOWN at t0, got " + state.waitPhase(t0, 7200, true, "account_C");

        // Intra-round: after 14 seconds, still not ready
        assert !state.ready("account_B", true, true, false, t0 + 14000L, 7200, "account_C") : "not ready at 14s";
        assert "COUNTDOWN".equals(state.waitPhase(t0 + 14000L, 7200, true, "account_C")) : "phase should be COUNTDOWN at 14s";

        // After 15 seconds, B is ready to switch to C!
        assert state.ready("account_B", true, true, false, t0 + 15000L, 7200, "account_C") : "ready at 15s";

        // Switched to C
        long t1 = t0 + 20000L;
        state.onRound("account_C");
        assert "account_B".equals(state.getRoundStartAccount()) : "start account remains B";

        // C finishes tasks, next is A
        assert "account_A".equals(AccountSwitchState.next(accounts, "account_C")) : "next after C is A";
        assert !state.ready("account_C", true, true, false, t1, 7200, "account_A") : "C not ready at t1";
        assert "COUNTDOWN".equals(state.waitPhase(t1, 7200, true, "account_A")) : "C phase COUNTDOWN";

        // C waits 15s and is ready to switch to A
        assert state.ready("account_C", true, true, false, t1 + 15000L, 7200, "account_A") : "C ready at 15s";

        // Switched to A
        long t2 = t1 + 30000L;
        state.onRound("account_A");
        assert "account_B".equals(state.getRoundStartAccount()) : "start account remains B on A";

        // A finishes tasks, next is B (the round start account!)
        assert "account_B".equals(AccountSwitchState.next(accounts, "account_A")) : "next after A is B";
        assert state.isRoundEnd("account_B") : "isRoundEnd should be true for B";

        // At t2 (0s after A finishes idle): starts countdown, not ready
        assert !state.ready("account_A", true, true, false, t2, 7200, "account_B") : "A not ready at t2";
        assert "ROUND_COOLDOWN".equals(state.waitPhase(t2, 7200, true, "account_B")) : "phase should be ROUND_COOLDOWN at t2";

        // At 15s after A finishes: NOT ready, phase must be ROUND_COOLDOWN!
        assert !state.ready("account_A", true, true, false, t2 + 15000L, 7200, "account_B") : "A not ready at 15s";
        assert "ROUND_COOLDOWN".equals(state.waitPhase(t2 + 15000L, 7200, true, "account_B")) : "phase should be ROUND_COOLDOWN at 15s";

        // At 7199s after A finishes: still cooling down
        assert !state.ready("account_A", true, true, false, t2 + 7199000L, 7200, "account_B") : "A not ready at 7199s";
        assert "ROUND_COOLDOWN".equals(state.waitPhase(t2 + 7199000L, 7200, true, "account_B")) : "phase should be ROUND_COOLDOWN at 7199s";

        // At 7200s (2 hours): round cooldown complete, ready to switch to B!
        assert state.ready("account_A", true, true, false, t2 + 7200000L, 7200, "account_B") : "A ready at 7200s";

        // Switched back to B for new round!
        long t3 = t2 + 7210000L;
        state.onRound("account_B");
        assert "account_B".equals(state.getRoundStartAccount()) : "start account remains B in round 2";

        // B finishes tasks in new round: next is C (!= B), interval is 15s again!
        assert !state.ready("account_B", true, true, false, t3, 7200, "account_C") : "B round 2 not ready at t3";
        assert "COUNTDOWN".equals(state.waitPhase(t3, 7200, true, "account_C")) : "B round 2 phase COUNTDOWN";
        assert state.ready("account_B", true, true, false, t3 + 15000L, 7200, "account_C") : "B round 2 ready at 15s";
    }

    private static void testToggleResetAndNewAnchor() {
        AccountSwitchState state = new AccountSwitchState();
        state.onActivate("account_A");
        assert "account_A".equals(state.getRoundStartAccount());

        // Toggle disabled: clears all state and cooldown
        state.disabled();
        assert state.getRoundStartAccount() == null;
        assert !state.ready("account_A", false, true, false, 100000L, 7200, "account_B");

        // Reactivate on Account C: C becomes the new anchor!
        state.onActivate("account_C");
        assert "account_C".equals(state.getRoundStartAccount());
    }

    private static void testStatusMessages() {
        assert "本账号任务已完成，等待切换下一个账号（15秒）".equals(AccountSwitchStatus.message("COUNTDOWN"));
        assert "本轮全部账号已完成，正在整轮冷却".equals(AccountSwitchStatus.message("ROUND_COOLDOWN"));
        assert "已关闭".equals(AccountSwitchStatus.message("DISABLED"));
    }
}
'''

with tempfile.TemporaryDirectory(prefix="sesame-switch-check-") as tempdir:
    temp_dir = Path(tempdir)
    pkg_dir = temp_dir / "io/github/aw1y2z/sesame/hook"
    pkg_dir.mkdir(parents=True)
    test_file = pkg_dir / "AccountSwitchCheck.java"
    test_file.write_text(TEST_CODE, encoding="utf-8")

    classpath = os.pathsep.join([str(classes), str(temp_dir)])
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(temp_dir), str(test_file)], check=True)
    subprocess.run(["java", "-ea", "-cp", classpath, "io.github.aw1y2z.sesame.hook.AccountSwitchCheck"], check=True)
