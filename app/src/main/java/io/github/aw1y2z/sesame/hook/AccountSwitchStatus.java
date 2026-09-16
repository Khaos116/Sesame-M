package io.github.aw1y2z.sesame.hook;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.util.AtomicConfigFile;
import io.github.aw1y2z.sesame.util.FileUtil;

/**
 * 自动切号当前所处阶段，只是给设置弹窗显示用的诊断文案，不存账号身份信息。
 * 移植自 GR 分支并简化（去掉了原版的任务队列诊断字段），见 doc/MyFix.md。
 */
public final class AccountSwitchStatus {
    private static String last;
    private static long lastWrite;
    private static final Pattern RECORD = Pattern.compile("\\{\"phase\":\"([A-Z_]+)\",\"updatedAt\":([0-9]{1,19})\\}\\n?");
    private AccountSwitchStatus() { }
    private static File file() { return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_status.json"); }

    static void publish(String phase) {
        try {
            if (message(phase) == null) return;
            long now = System.currentTimeMillis();
            if (phase.equals(last) && now >= lastWrite && now - lastWrite < 30000) return;
            AtomicConfigFile.write("{\"phase\":\"" + phase + "\",\"updatedAt\":" + now + "}\n", file());
            last = phase; lastWrite = now;
        } catch (Throwable ignored) { }
    }

    public static String label() {
        try {
            File file = file();
            if (!file.isFile() || file.length() > 256) return "轮询状态：等待宿主更新";
            byte[] bytes = new byte[257]; int count = 0;
            try (FileInputStream input = new FileInputStream(file)) {
                int n; while (count < bytes.length && (n = input.read(bytes, count, bytes.length - count)) != -1) count += n;
            }
            return label(new String(bytes, 0, count, StandardCharsets.UTF_8), System.currentTimeMillis());
        } catch (Throwable ignored) { return "轮询状态：等待宿主更新"; }
    }

    static String label(String json, long now) {
        try {
            if (json.length() > 256) return "轮询状态：等待宿主更新";
            Matcher m = RECORD.matcher(json); if (!m.matches()) return "轮询状态：等待宿主更新";
            String message = message(m.group(1)); long updated = Long.parseLong(m.group(2));
            if (message == null || now < updated || now - updated > 90000) return "轮询状态：等待宿主更新";
            return "轮询状态：" + message;
        } catch (Exception ignored) { return "轮询状态：等待宿主更新"; }
    }

    static String message(String phase) {
        switch (phase) {
            case "DISABLED": return "已关闭";
            case "WAIT_HOST": return "等待支付宝服务就绪";
            case "WAIT_IDENTITY": return "等待账号信息同步";
            case "COUNTDOWN": return "本账号任务已完成，等待切换下一个账号（15秒）";
            case "ROUND_COOLDOWN": return "本轮全部账号已完成，正在整轮冷却";
            case "WAIT_TASKS": return "等待当前业务任务结束";
            case "WAIT_CAPTCHA": return "等待验证码或登录页面处理完成";
            case "WAIT_HISTORY": return "等待至少两个有效账号";
            case "PAUSED": return "切换异常已暂停，请关闭保存后再开启";
            case "SWITCHING": return "正在切换到下一个账号";
            case "CONFIRMING": return "等待宿主确认切换结果";
            default: return null;
        }
    }
}
