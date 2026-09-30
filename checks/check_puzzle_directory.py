#!/usr/bin/env python3
"""真实目录迁移：旧 puzzle 剪切到 .nomedia/puzzle，重名文件不丢失。"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/util/PuzzleDirectory.java"

HARNESS = r'''
package io.github.aw1y2z.sesame.util;

import java.io.File;
import java.nio.file.Files;

public class PuzzleDirectoryCheck {
    static void write(File file, String value) throws Exception {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), value);
    }

    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory("puzzle-dir").toFile();
        write(new File(base, "puzzle/C176/shot.png"), "old");
        write(new File(base, "puzzle/other/tmp/shot.png"), "tmp");
        File hidden = PuzzleDirectory.prepare(base);
        assert hidden.getName().equals("puzzle");
        assert hidden.getParentFile().getName().equals(".nomedia");
        assert new File(hidden, ".nomedia").isFile();
        assert Files.readString(new File(hidden, "C176/shot.png").toPath()).equals("old");
        assert new File(hidden, "other/tmp/shot.png").isFile();
        assert !new File(base, "puzzle").exists();
        assert PuzzleDirectory.prepare(base).equals(hidden);

        write(new File(base, "puzzle/C176/shot.png"), "new-old");
        PuzzleDirectory.prepare(base);
        assert Files.readString(new File(hidden, "C176/shot.png").toPath()).equals("old");
        File[] files = new File(hidden, "C176").listFiles();
        assert files != null && files.length == 2 : "different images must survive";
        assert Files.readString(new File(hidden, "C176/shot-old-1.png").toPath()).equals("new-old");
        assert !new File(base, "puzzle").exists();
        PuzzleDirectory.prepare(base);
        assert new File(hidden, "C176").listFiles().length == 2;

        File blocked = Files.createTempDirectory("puzzle-blocked").toFile();
        write(new File(blocked, "puzzle/C176/shot.png"), "keep");
        write(new File(blocked, ".nomedia"), "occupied");
        try {
            PuzzleDirectory.prepare(blocked);
            throw new AssertionError("blocked target must fail");
        } catch (java.io.IOException expected) {
            assert new File(blocked, "puzzle/C176/shot.png").isFile();
            assert new File(blocked, "puzzle/.nomedia").isFile();
        }
        System.out.println("PASS: puzzle screenshots move into .nomedia/puzzle without old copies or collisions");
    }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sesame-puzzle-dir-") as work:
        work = Path(work)
        probe = work / "PuzzleDirectoryCheck.java"
        probe.write_text(HARNESS, encoding="utf-8")
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), str(SOURCE), str(probe)], check=True)
        subprocess.run(["java", "-ea", "-cp", str(work),
                        "io.github.aw1y2z.sesame.util.PuzzleDirectoryCheck"], check=True, timeout=60)


if __name__ == "__main__":
    sys.exit(main())
