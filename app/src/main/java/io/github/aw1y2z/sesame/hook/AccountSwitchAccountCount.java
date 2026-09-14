package io.github.aw1y2z.sesame.hook;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.util.AtomicConfigFile;
import io.github.aw1y2z.sesame.util.FileUtil;

/**
 * 本机可轮询账号数，只是给设置弹窗显示用的缓存，不存账号身份信息。
 * 移植自 GR 分支，见 doc/MyFix.md。
 */
public final class AccountSwitchAccountCount {
    private static final long FRESH_MS = 90_000L;
    private static final Pattern RECORD = Pattern.compile("\\{\"count\":(-1|[0-9]{1,2}),\"updatedAt\":([0-9]{1,19})\\}\\n?");
    private AccountSwitchAccountCount() { }
    private static File file() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_count.json");
    }
    static void publish(int count) {
        try { write(file(), count, System.currentTimeMillis()); }
        catch (Throwable unavailable) { /* 缓存写失败不能中断轮询 */ }
    }
    public static String label() { return label(file(), System.currentTimeMillis()); }

    static boolean write(File file, int count, long time) {
        if (count < -1 || count > 32 || time < 0) return false;
        try {
            AtomicConfigFile.write("{\"count\":" + count + ",\"updatedAt\":" + time + "}\n", file);
            return true;
        } catch (Exception unavailable) { return false; }
    }

    static String label(File file, long now) {
        String unknown = "本机可轮询账号：等待读取（请先打开支付宝）";
        try {
            if (!file.isFile() || file.length() > 256) return unknown;
            byte[] data = new byte[257]; int length = 0;
            try (FileInputStream input = new FileInputStream(file)) {
                int n;
                while (length < data.length && (n = input.read(data, length, data.length - length)) != -1) length += n;
            }
            if (length > 256) return unknown;
            Matcher record = RECORD.matcher(new String(data, 0, length, StandardCharsets.UTF_8));
            if (!record.matches()) return unknown;
            int count = Integer.parseInt(record.group(1)); long time = Long.parseLong(record.group(2));
            if (count < 0 || count > 32) return unknown;
            if (now < time || now - time > FRESH_MS) {
                return "上次读取可轮询账号：" + count + "个（等待支付宝更新）";
            }
            return "本机可轮询账号：" + count + "个" + (count < 2 ? "（至少2个才能轮询）" : "");
        } catch (Exception unavailable) { return unknown; }
    }
}
