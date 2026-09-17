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

        assert !state.ready("account_A", true, true, false, t2, 7200, "account_B");
        assert "COUNTDOWN".equals(state.waitPhase(t2, 7200, true, "account_B"));
        assert state.ready("account_A", true, true, false, t2 + 15000L, 7200, "account_B")
                : "return to start must take 15s, not 2 hours";

        long returned = t2 + 20000L;
        state.onRound("account_B");
        state.startCooldown(returned);
        assert state.isCoolingDown();
        assert "ROUND_COOLDOWN".equals(state.waitPhase(returned, 7200, true, "account_C"));
        assert !state.ready("account_B", true, true, false, returned, 7200, "account_C");
        assert state.cooldownPending(returned + 7199999L, 7200);
        assert !state.cooldownPending(returned + 7200000L, 7200);
        assert !state.isCoolingDown();
        assert !state.ready("account_B", true, false, false, returned + 7200000L, 7200, "account_C");
        assert !state.ready("account_B", true, true, false, returned + 7210000L, 7200, "account_C");
        assert state.ready("account_B", true, true, false, returned + 7225000L, 7200, "account_C");
        state.onRound("account_C");
        state.onRound("account_A");
        state.onRound("account_B");
        state.startCooldown(returned + 7300000L);
        assert state.cooldownPending(returned + 7359999L, 60);
        assert !state.cooldownPending(returned + 7360000L, 60);
    }

    private static void testToggleResetAndNewAnchor() {
        AccountSwitchState state = new AccountSwitchState();
        state.onActivate("account_A");
        assert "account_A".equals(state.getRoundStartAccount());

        state.startCooldown(100);
        // Toggle disabled: clears all state and cooldown
        state.disabled();
        assert state.getRoundStartAccount() == null;
        assert !state.isCoolingDown();
        assert !state.ready("account_A", false, true, false, 100000L, 7200, "account_B");

        // Reactivate on Account C: C becomes the new anchor!
        state.onActivate("account_C");
        assert "account_C".equals(state.getRoundStartAccount());
    }

    private static void testStatusMessages() {
        assert "本账号任务已完成，等待切换下一个账号（15秒）".equals(AccountSwitchStatus.message("COUNTDOWN"));
        assert "切号冷却中，当前账号任务正常运行".equals(AccountSwitchStatus.message("ROUND_COOLDOWN"));
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

    classpath = os.pathsep.join([str(temp_dir), str(classes)])
    subprocess.run(["javac", "-encoding", "UTF-8", "-cp", classpath, "-d", str(temp_dir), str(test_file), *[str(SOURCE / (name + ".java")) for name in ("AccountSwitchState", "AccountSwitchIntervalDraft", "AccountSwitchStatus")]], check=True)
    subprocess.run(["java", "-ea", "-cp", classpath, "io.github.aw1y2z.sesame.hook.AccountSwitchCheck"], check=True)

    # Compile the real controller and lifecycle with an isolated clock/host, no Android or RPC.
    import re
    controller_dir = temp_dir / "controller"
    controller_dir.mkdir()
    for name in ("AccountSwitchController", "AccountSwitchState", "AccountSwitchFlight", "AccountSwitchIntervalDraft", "AccountSwitchPagePolicy"):
        source = (SOURCE / (name + ".java")).read_text(encoding="utf-8")
        source = re.sub(r"^import (?:android|io\.github)\..*;\n", "", source, flags=re.M)
        (controller_dir / (name + ".java")).write_text(source, encoding="utf-8")
    lifecycle = (SOURCE.parent / "data/task/TaskLifecycle.java").read_text(encoding="utf-8")
    lifecycle = lifecycle.replace("package io.github.aw1y2z.sesame.data.task;", "package io.github.aw1y2z.sesame.hook;")
    (controller_dir / "TaskLifecycle.java").write_text(lifecycle, encoding="utf-8")
    template = ROOT / "checks/account_lifecycle/AccountSwitchControllerCheck.java.in"
    (controller_dir / "AccountSwitchControllerCheck.java").write_text(template.read_text(encoding="utf-8"), encoding="utf-8")
    (controller_dir / "Activity.java").write_text(
        "package android.app; public class Activity { public static boolean focus = true; public boolean hasWindowFocus() { return focus; } }", encoding="utf-8")
    (controller_dir / "AlipayLogin.java").write_text(
        "package com.eg.android.AlipayGphone; public class AlipayLogin extends android.app.Activity {}", encoding="utf-8")
    (controller_dir / "ClassUtil.java").write_text(
        'package io.github.aw1y2z.sesame.util; public class ClassUtil { public static String CURRENT_USING_ACTIVITY = "com.eg.android.AlipayGphone.AlipayLogin"; }', encoding="utf-8")
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(controller_dir),
                    *map(str, controller_dir.glob("*.java"))], check=True)
    subprocess.run(["java", "-ea", "-cp", str(controller_dir),
                    "io.github.aw1y2z.sesame.hook.AccountSwitchControllerCheck"], check=True, timeout=30)
