#!/usr/bin/env python3
"""拼图验证码截图文件管理：只保留拖动过的 matched，其余放 tmp/ 并在验证结束时删掉。

直接编译生产代码 hook/PuzzleSampleFiles.java（纯 java.io），用临时目录回放：
- matched 放账号目录、每号最多 10 张、按时间轮换；旧版本遗留的无关文件不占 matched 名额；
- no-slider / no-track / match-failed 放 tmp/；
- 清理后只剩 matched，tmp/ 目录被移除，旧版本散落在主目录里的无关文件也被删；重复清理是空操作。
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/hook/PuzzleSampleFiles.java"

HARNESS = r'''
package io.github.aw1y2z.sesame.hook;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

public class PuzzleSampleFilesCheck {
    static void touch(File f, long t) throws Exception {
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), new byte[]{1});
        f.setLastModified(t);
    }

    /** 模拟 saveSample：按生产代码决定的路径写文件，再轮换。 */
    static File save(File account, String tag, long time) throws Exception {
        File file = PuzzleSampleFiles.fileFor(account, tag, time);
        touch(file, time);
        PuzzleSampleFiles.rotate(account, tag);
        return file;
    }

    public static void main(String[] args) throws Exception {
        File account = Files.createTempDirectory("puzzle-samples").toFile();

        // 路径规则
        assert PuzzleSampleFiles.targetDir(account, "matched-d300").equals(account);
        for (String tag : new String[]{"no-slider", "no-track", "match-failed"}) {
            assert PuzzleSampleFiles.targetDir(account, tag).equals(new File(account, "tmp")) : tag;
        }
        assert PuzzleSampleFiles.fileFor(account, "matched-d300", 5L).getName().contains("-matched-");

        // 旧版本遗留在主目录的无关文件（比部分 matched 更新，不能挤掉 matched）
        touch(new File(account, "puzzle-1-no-slider.png"), 20000);
        touch(new File(account, "puzzle-2-match-failed.png"), 20001);

        // 12 张 matched → 只留最新 10 张
        for (int i = 0; i < 12; i++) save(account, "matched-d" + (300 + i), 10000 + i);
        long matched = Arrays.stream(account.listFiles()).filter(f -> f.getName().contains("-matched-")).count();
        assert matched == 10 : "matched must be capped at 10, got " + matched;
        assert !new File(account, "puzzle-10000-matched-d300.png").exists() : "oldest matched must rotate out";
        assert new File(account, "puzzle-10011-matched-d311.png").exists() : "newest matched must stay";

        // 过程中的无验证码截图放 tmp/，且有兜底上限
        for (int i = 0; i < 20; i++) save(account, i % 2 == 0 ? "no-slider" : "no-track", 30000 + i);
        File[] tmp = new File(account, "tmp").listFiles();
        assert tmp != null && tmp.length == PuzzleSampleFiles.KEEP_TMP : "tmp cap, got " + (tmp == null ? -1 : tmp.length);
        save(account, "match-failed", 40000);

        // 验证结束：清理后只剩 matched
        // match-failed 也在 tmp/（与其它无验证码截图共用 16 张上限），另有 2 张旧版本遗留
        int deleted = PuzzleSampleFiles.deleteNonMatched(account);
        assert deleted == PuzzleSampleFiles.KEEP_TMP + 2 : "tmp files + 2 legacy, got " + deleted;
        String[] left = account.list();
        assert left.length == 10 : "only matched must remain: " + Arrays.toString(left);
        for (String name : left) assert name.contains("-matched-") : name;
        assert !new File(account, "tmp").exists() : "tmp dir must be removed";
        assert PuzzleSampleFiles.deleteNonMatched(account) == 0 : "second cleanup must be a no-op";
        System.out.println("PASS: only matched kept (max 10 per account), tmp and legacy non-matched removed");
    }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sesame-puzzle-samples-") as work:
        work = Path(work)
        probe = work / "PuzzleSampleFilesCheck.java"
        probe.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), str(SOURCE), str(probe)], check=True)
        subprocess.run(["java", "-ea", "-cp", str(work),
                        "io.github.aw1y2z.sesame.hook.PuzzleSampleFilesCheck"], check=True, timeout=60)


if __name__ == "__main__":
    sys.exit(main())
