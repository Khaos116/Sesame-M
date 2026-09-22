package io.github.aw1y2z.sesame.util.diagnostics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 独立的每日诊断日志流，只落固定词汇的事件。移植自 GR 分支，见 docs/MyFix.md。 */
public final class ValidationDiagnosticJournal {
    private ValidationDiagnosticJournal() { }

    public static synchronized void appendPuzzle(File directory, long now, String event) throws IOException {
        String safe = CaptchaDiagnosticEvent.sanitize(event);
        if (safe == null || !safe.startsWith("SESAME_PUZZLE_EVENT ")) throw new IOException("invalid puzzle event");
        appendLine(directory, now, "puzzle", safe);
    }

    private static void appendLine(File directory, long now, String stream, String event) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("log directory unavailable");
        File root = directory.getCanonicalFile();
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
        date.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        time.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        File log = new File(root, stream + "." + date.format(new Date(now)) + ".log");
        if (!log.getCanonicalFile().equals(log.getAbsoluteFile())) throw new IOException("linked result log");
        String message = time.format(new Date(now)) + " " + event + "\n";
        // 即使宿主反复触发回调，也保持有界的每日流大小。
        try (FileOutputStream out = new FileOutputStream(log, log.length() < 2L * 1024 * 1024)) {
            out.write(message.getBytes(StandardCharsets.UTF_8));
        }
    }
}
