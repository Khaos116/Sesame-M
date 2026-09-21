#!/usr/bin/env python3
"""对准图片滑块（拼图）的图像匹配：用 GR 记录的真实脱敏样本离线回放生产代码。

编译 hook/ 下纯 Java 的匹配类（无 Android 依赖），逐个样本断言位移落在标注值附近，
并断言歧义/纯色/过期预算会被拒绝而不是乱匹配。样本来自 GR 的回归夹具，位移是图像
几何标注，不代表服务端接受。
"""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
HOOK = ROOT / "app/src/main/java/io/github/aw1y2z/sesame/hook"
FIXTURES = ROOT / "checks/fixtures/puzzle-slider"

HARNESS = r'''
package io.github.aw1y2z.sesame.hook;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPInputStream;

public class PuzzleMatcherCheck {
    static Path dir;
    static final int W = 1264;

    static final class Roi implements PuzzleSliderMatcherCore.PixelReader {
        final int[] pixels; final int l, t, w, h;
        Roi(int[] pixels, int l, int t, int w, int h) { this.pixels = pixels; this.l = l; this.t = t; this.w = w; this.h = h; }
        public void read(int left, int top, int width, int height, int[] dst) {
            if (left != l || top != t || width != w || height != h) throw new IllegalStateException("ROI changed");
            System.arraycopy(pixels, 0, dst, 0, pixels.length);
        }
    }

    static Roi load(String name, int l, int t, int w, int h) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(dir.resolve(name)));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            byte[] rgba = out.toByteArray();
            if (rgba.length != w * h * 4) throw new IOException("bad fixture length " + name);
            int[] px = new int[w * h];
            for (int i = 0, o = 0; i < px.length; i++, o += 4)
                px[i] = ((rgba[o + 3] & 255) << 24) | ((rgba[o] & 255) << 16) | ((rgba[o + 1] & 255) << 8) | (rgba[o + 2] & 255);
            return new Roi(px, l, t, w, h);
        }
    }

    static void within(String name, PuzzleSliderMatcherCore.Result r, int expected, int tol) {
        assert r.success : name + ": " + r.error;
        assert Math.abs(r.displacement - expected) <= tol : name + ": displacement " + r.displacement + " expected " + expected;
        assert r.elapsedMs < 3500L : name + ": too slow " + r.elapsedMs;
        System.out.println("PASS " + name + " displacement=" + r.displacement + " method=" + r.method + " " + r.elapsedMs + "ms");
    }

    public static void main(String[] args) throws Exception {
        dir = Paths.get(args[0]);
        // 整屏 1264x2780 的 ROI (164,1046,984,668)
        Roi success = load("sample-success.rgba.gz", 164, 1046, 984, 668);
        within("success", PuzzleSliderMatcherCore.estimate(W, 2780, 1787, 0, success, 3500L), 668, 12);
        within("runtime-source-position", PuzzleSliderMatcherCore.estimate(W, 2780, 1787, 0, success, 3500L, 166), 668, 12);
        within("live-short", PuzzleSliderMatcherCore.estimate(W, 2780, 1787, 0,
                load("sample-live-short.rgba.gz", 164, 1046, 984, 668), 3500L), 394, 12);

        // 异步截图帧：只会匹配到照片边框，必须拒绝
        PuzzleSliderMatcherCore.Result async = PuzzleSliderMatcherCore.estimate(W, 2780, 1787, 0,
                load("sample-async.rgba.gz", 164, 1046, 984, 668), 3500L);
        assert !async.success && async.error.startsWith("ambiguous image match") : "async frame must be rejected: " + async.error;
        System.out.println("PASS async frame rejected: " + async.error);

        // 整屏 1264x2649 的 ROI (167,1081,984,637)，滑块 Y=1786.5，源位置提示 169
        within("swan-border", PuzzleSliderMatcherCore.estimate(W, 2649, 1786.5f, 0,
                load("sample-swan-border.rgba.gz", 167, 1081, 984, 637), 3500L, 169), 474, 8);
        PuzzleSliderMatcherCore.Result arrow = PuzzleSliderMatcherCore.estimate(W, 2649, 1786.5f, 0,
                load("sample-arrow-right-edge.rgba.gz", 167, 1081, 984, 637), 3500L, 169);
        within("arrow-beyond-initial-range", arrow, 751, 6);
        assert arrow.method.endsWith("extended-range") : arrow.method;

        String[] names = {"video-sunset-heart", "video-mountain-triangle"};
        int[] expected = {661, 448};
        for (int i = 0; i < names.length; i++) {
            PuzzleSliderMatcherCore.Result r = PuzzleSliderMatcherCore.estimateOptimized(W, 2649, 1786.5f, 0,
                    load("sample-" + names[i] + ".rgba.gz", 167, 1081, 984, 637), 3500L, 169);
            within(names[i], r, expected[i], 8);
            assert r.method.contains("interior-texture-consensus") : r.method;
        }
        PuzzleSliderMatcherCore.Result snow = PuzzleTextureMatcherCore.estimate(W, 2649, 1786.5f, 0,
                load("sample-live-snow-cloud.rgba.gz", 167, 1081, 984, 637), 3500L, 169);
        assert snow != null && snow.success && Math.abs(snow.displacement - 464) <= 12 : "snow-cloud";
        System.out.println("PASS snow-cloud displacement=" + snow.displacement);

        // 被遮挡的粉色三角：轮廓共识分支
        PuzzleSliderMatcherCore.Result occluded = PuzzleSliderMatcherCore.estimateOptimized(W, 2649, 1786.5f, 0,
                load("sample-live-occluded-pink-triangle.rgba.gz", 167, 1081, 984, 637), 3500L, 169);
        within("occluded-pink-triangle", occluded, 703, 4);
        assert occluded.method.equals("occluded-contour-consensus") : occluded.method;

        // 纯色照片只有一条水平边框：不能匹配
        PuzzleSliderMatcherCore.Result uniform = PuzzleSliderMatcherCore.estimate(W, 2649, 1786.5f, 0,
                (left, top, width, height, dst) -> {
                    for (int y = 0; y < height; y++)
                        for (int x = 0; x < width; x++) dst[y * width + x] = top + y < 1678 ? 0xff202020 : 0xffffffff;
                }, 1000L, 169);
        assert !uniform.success : "uniform photo must be rejected";
        System.out.println("PASS uniform photo rejected: " + uniform.error);

        // 预算已耗尽：不读像素、直接超时
        PuzzleSliderMatcherCore.Result expired = PuzzleSliderMatcherCore.estimateOptimized(W, 2649, 1786.5f, 0,
                (left, top, width, height, dst) -> { throw new AssertionError("pixels read after deadline"); }, 0L, 169);
        assert !expired.success : "expired budget must fail";
        System.out.println("PASS expired budget: " + expired.error);

        // 几何：像素位移就是触摸位移，超出轨道则截断到终点
        PuzzleSliderGeometry.Mapping m = PuzzleSliderGeometry.map(668, 236f, 1027f);
        assert m.success && Math.abs(m.endX - 904f) < 0.01f && !m.clamped : "captured successful swipe 236->904";
        PuzzleSliderGeometry.Mapping clamp = PuzzleSliderGeometry.map(900, 40f, 640f);
        assert clamp.success && Math.abs(clamp.endX - 640f) < 0.01f && clamp.clamped : "overshoot must clamp to track end";
        assert !PuzzleSliderGeometry.map(-1, 0f, 100f).success && !PuzzleSliderGeometry.map(10, 100f, 100f).success;
        System.out.println("PASS geometry");
        System.out.println("PASS: puzzle matcher replays recorded fixtures");
    }
}
'''


def main():
    with tempfile.TemporaryDirectory(prefix="sesame-puzzle-check-") as work:
        work = Path(work)
        probe = work / "PuzzleMatcherCheck.java"
        probe.write_text(HARNESS, encoding="utf-8")
        sources = [HOOK / name for name in (
            "PuzzleSliderMatcherCore.java", "PuzzleTextureMatcherCore.java",
            "PuzzleOccludedContourMatcher.java", "PuzzleSliderGeometry.java")]
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *map(str, sources), str(probe)], check=True)
        subprocess.run(["java", "-ea", "-cp", str(work),
                        "io.github.aw1y2z.sesame.hook.PuzzleMatcherCheck", str(FIXTURES)], check=True, timeout=180)


if __name__ == "__main__":
    sys.exit(main())
