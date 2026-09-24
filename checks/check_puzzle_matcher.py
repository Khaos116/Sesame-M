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
            // 请求区域可能比夹具 ROI 略大（模板内缩后右边界外移）：交集内取夹具像素，其余补黑
            java.util.Arrays.fill(dst, 0, width * height, 0xff000000);
            int x0 = Math.max(left, l), x1 = Math.min(left + width, l + w);
            int y0 = Math.max(top, t), y1 = Math.min(top + height, t + h);
            for (int y = y0; y < y1; y++)
                System.arraycopy(pixels, (y - t) * w + (x0 - l), dst, (y - top) * width + (x0 - left), Math.max(0, x1 - x0));
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
        assert Math.abs(r.displacement - expected) <= tol : name + ": displacement " + r.displacement
                + " expected " + expected + " method=" + r.method + " score=" + r.bestScore;
        assert r.elapsedMs < 3500L : name + ": too slow " + r.elapsedMs;
        System.out.println("PASS " + name + " displacement=" + r.displacement + " method=" + r.method + " " + r.elapsedMs + "ms");
    }

    static void replaySingleFrameFolder(Path root) throws Exception {
        java.util.Map<String, Integer> corrected = new java.util.HashMap<>();
        corrected.put("1790225685550", 402);
        corrected.put("1790225690358", 762);
        corrected.put("1790192429152", 686);
        corrected.put("1790192435203", 748);
        corrected.put("1790224098925", 768);
        int cases = 0;
        int failures = 0;
        java.util.List<Path> files;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".png"))
                    .sorted().collect(java.util.stream.Collectors.toList());
        }
        assert files.size() == 56 : "expected 56 external PNG records, got " + files.size();
        for (Path path : files) {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(path.toFile());
            assert image != null && image.getWidth() > 0 && image.getHeight() > 0
                    : "unreadable PNG " + path;
            if (path.getFileName().toString().matches("puzzle-\\d+-matched-d\\d+\\.png")) {
                String name = path.getFileName().toString();
                String timestamp = name.substring("puzzle-".length(), name.indexOf("-matched-d"));
                int recorded = Integer.parseInt(name.substring(name.lastIndexOf("-d") + 2, name.length() - 4));
                int expected = corrected.getOrDefault(timestamp, recorded);
                int width = image.getWidth(), height = image.getHeight();
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                PuzzleSliderMatcherCore.PixelReader reader = (left, top, w, h, dst) -> {
                    for (int y = 0; y < h; y++)
                        System.arraycopy(pixels, (top + y) * width + left, dst, y * w, w);
                };
                PuzzleSliderMatcherCore.Result result = PuzzleSliderMatcherCore.estimateSingleFrame(
                        width, height, 1846f, 0, reader, 3500L, 171);
                boolean pass = result.success && Math.abs(result.displacement - expected) <= 8;
                System.out.println((pass ? "PASS " : "FAIL ") + "single-frame/"
                        + path.getParent().getFileName() + "/" + timestamp
                        + " displacement=" + (result.success ? result.displacement : result.error)
                        + " expected=" + expected + " method=" + result.method
                        + " score=" + result.bestScore + " sourceTop=" + result.sourceTop
                        + " targetTop=" + result.targetTop);
                if (!pass) {
                    failures++;
                }
                cases++;
            }
        }
        assert cases == 15 : "expected 15 single-frame samples, got " + cases;
        assert failures == 0 : failures + " single-frame samples failed";
        System.out.println("PASS single-frame replay: " + cases
                + " labeled cases, " + files.size() + " PNG records readable");
    }

    static PuzzleSliderMatcherCore.Result singleFrame(int width, int height, float sliderY,
            PuzzleSliderMatcherCore.PixelReader reader, int sourceLeft) {
        return PuzzleSliderMatcherCore.estimateSingleFrame(
                width, height, sliderY, 0, reader, 3500L, sourceLeft);
    }

    static void replayPhoto(Path path, String name, float sliderY, int sourceLeft,
            int expected, int tolerance) throws Exception {
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(path.toFile());
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        within(name, singleFrame(width, height, sliderY,
                (left, top, w, h, dst) -> {
                    for (int y = 0; y < h; y++)
                        System.arraycopy(pixels, (top + y) * width + left, dst, y * w, w);
                }, sourceLeft), expected, tolerance);
    }

    public static void main(String[] args) throws Exception {
        dir = Paths.get(args[0]);
        if (args.length > 1) replaySingleFrameFolder(Paths.get(args[1]));
        // 整屏 1264x2780 的 ROI (164,1046,984,668)
        Roi success = load("sample-success.rgba.gz", 164, 1046, 984, 668);
        within("success", singleFrame(W, 2780, 1787, success, -1), 668, 12);
        within("runtime-source-position", PuzzleSliderMatcherCore.estimate(W, 2780, 1787, 0, success, 3500L, 166), 668, 12);
        within("live-short", singleFrame(W, 2780, 1787,
                load("sample-live-short.rgba.gz", 164, 1046, 984, 668), -1), 394, 12);

        // 异步截图帧：只会匹配到照片边框，必须拒绝
        PuzzleSliderMatcherCore.Result async = singleFrame(W, 2780, 1787,
                load("sample-async.rgba.gz", 164, 1046, 984, 668), -1);
        assert !async.success && async.error.startsWith("ambiguous image match") : "async frame must be rejected: " + async.error;
        System.out.println("PASS async frame rejected: " + async.error);

        // 整屏 1264x2649 的 ROI (167,1081,984,637)，滑块 Y=1786.5，源位置提示 169
        within("swan-border", singleFrame(W, 2649, 1786.5f,
                load("sample-swan-border.rgba.gz", 167, 1081, 984, 637), 169), 474, 8);
        PuzzleSliderMatcherCore.Result arrow = singleFrame(W, 2649, 1786.5f,
                load("sample-arrow-right-edge.rgba.gz", 167, 1081, 984, 637), 169);
        within("arrow-beyond-initial-range", arrow, 751, 6);
        assert arrow.method.endsWith("extended-range") : arrow.method;

        String[] names = {"video-sunset-heart", "video-mountain-triangle"};
        int[] expected = {661, 448};
        for (int i = 0; i < names.length; i++) {
            PuzzleSliderMatcherCore.Result r = singleFrame(W, 2649, 1786.5f,
                    load("sample-" + names[i] + ".rgba.gz", 167, 1081, 984, 637), 169);
            within(names[i], r, expected[i], 8);
            assert r.method.contains("interior-texture-consensus") : r.method;
        }
        PuzzleSliderMatcherCore.Result snow = singleFrame(W, 2649, 1786.5f,
                load("sample-live-snow-cloud.rgba.gz", 167, 1081, 984, 637), 169);
        assert snow != null && snow.success && Math.abs(snow.displacement - 464) <= 12 : "snow-cloud";
        System.out.println("PASS snow-cloud displacement=" + snow.displacement);

        // 被遮挡的粉色三角：轮廓共识分支
        PuzzleSliderMatcherCore.Result occluded = singleFrame(W, 2649, 1786.5f,
                load("sample-live-occluded-pink-triangle.rgba.gz", 167, 1081, 984, 637), 169);
        within("occluded-pink-triangle", occluded, 703, 4);
        // 模板内缩后旧边缘匹配直接就能给出接近的位移，不再走到轮廓分支；轮廓分支单独回放，保持覆盖
        PuzzleSliderMatcherCore.Result contour = PuzzleOccludedContourMatcher.estimate(W, 2649, 1786.5f, 0,
                load("sample-live-occluded-pink-triangle.rgba.gz", 167, 1081, 984, 637), 3500L, 169);
        within("occluded-pink-triangle-contour", contour, 703, 4);
        assert contour.method.equals("occluded-contour-consensus") : contour.method;

        // 真机未压缩截图（WebView 整屏 1280x2720，滑块中心 (239,1846)，按钮左缘 171）。
        // 火焰：旧版给 751/0.39（模板左缘压在照片边界上），真缺口 672（把滑块图叠上去验证过）；芽形：610
        for (String[] real : new String[][]{{"real-flame-d751.png", "672"}, {"real-sprout-d610.png", "610"}}) {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(dir.resolve(real[0]).toFile());
            int rw = img.getWidth(), rh = img.getHeight();
            int[] all = img.getRGB(0, 0, rw, rh, null, 0, rw);
            PuzzleSliderMatcherCore.PixelReader realReader = (left, top, width, height, dst) -> {
                for (int y = 0; y < height; y++) System.arraycopy(all, (top + y) * rw + left, dst, y * width, width);
            };
            PuzzleSliderMatcherCore.Result r = PuzzleSliderMatcherCore.estimateSingleFrame(rw, rh, 1846f, 0,
                    realReader, 3500L, 171);
            within(real[0], r, Integer.parseInt(real[1]), 6);
        }

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

        if (args.length > 2) {
            replayPhoto(Paths.get(args[2]), "extra-mountain-photo", 869f, 80, 252, 12);
        }
        if (args.length > 3) {
            replayPhoto(Paths.get(args[3]), "latest-initial-photo", 869f, 80, 343, 8);
        }
        System.out.println("PASS: puzzle matcher replays recorded fixtures");
    }
}
'''


def main():
    solver_source = (HOOK / "PuzzleCaptchaSolver.java").read_text(encoding="utf-8")
    assert '"[新版]"' in solver_source and '"[旧版]"' in solver_source, \
        "puzzle logs must identify the new or old matcher"
    assert solver_source.count("Log.captcha(") == 1, \
        "all puzzle solver logs must use the version-tagged helper"
    assert "PuzzleSliderMatcher.estimateSingleFrame(" in solver_source, \
        "new matcher must identify displacement from the initial screenshot"
    fallback_branch = solver_source.split("if (useNewMatcher && !recognized.success) {", 1)[1] \
        .split("final PuzzleSliderMatcher.Result match = recognized;", 1)[0]
    assert "recognized = PuzzleSliderMatcher.estimate(" in fallback_branch and \
        solver_source.index("if (useNewMatcher && !recognized.success)") < \
        solver_source.index("if (!match.success)"), \
        "new matcher failure must retry the old M matcher before giving up"
    assert "estimateAfterProbe" not in solver_source and '"matched_submit-probe-d"' not in solver_source, \
        "runtime must not depend on a second probe screenshot"
    with tempfile.TemporaryDirectory(prefix="sesame-puzzle-check-") as work:
        work = Path(work)
        probe = work / "PuzzleMatcherCheck.java"
        probe.write_text(HARNESS, encoding="utf-8")
        sources = [HOOK / name for name in (
            "PuzzleSliderMatcherCore.java", "PuzzleTextureMatcherCore.java",
            "PuzzleOccludedContourMatcher.java", "PuzzleSingleFrameMatcherCore.java",
            "PuzzleSliderGeometry.java")]
        subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(work), *map(str, sources), str(probe)], check=True)
        command = ["java", "-ea", "-cp", str(work),
                   "io.github.aw1y2z.sesame.hook.PuzzleMatcherCheck", str(FIXTURES)]
        if len(sys.argv) > 1:
            command.append(sys.argv[1])
        if len(sys.argv) > 2:
            command.append(sys.argv[2])
        if len(sys.argv) > 3:
            command.append(sys.argv[3])
        subprocess.run(command, check=True, timeout=180)


if __name__ == "__main__":
    sys.exit(main())
