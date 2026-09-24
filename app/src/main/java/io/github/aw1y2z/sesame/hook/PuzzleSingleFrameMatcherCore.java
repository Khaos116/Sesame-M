package io.github.aw1y2z.sesame.hook;

import java.util.Arrays;
import java.util.Locale;

/** Matches only sparse oriented contour edges, so scenery inside the piece cannot dominate. */
final class PuzzleSingleFrameMatcherCore {
    private static final int REFERENCE_WIDTH = 1264;
    private static final int REFERENCE_HEIGHT = 2780;

    private PuzzleSingleFrameMatcherCore() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static PuzzleSliderMatcherCore.Result estimate(int width, int height, float sliderScreenY,
            int screenTop, PuzzleSliderMatcherCore.PixelReader reader, long timeoutMs,
            int sourceLeft) {
        long started = System.nanoTime();
        long deadline = started + Math.max(0L, timeoutMs) * 1_000_000L;
        if (width <= 0 || height <= 0 || reader == null || timeoutMs <= 0 || sourceLeft < 0) {
            return PuzzleSliderMatcherCore.Result.failure("invalid single-frame input", elapsedMs(started));
        }

        float scaleX = width / (float) REFERENCE_WIDTH;
        float scaleY = height / (float) REFERENCE_HEIGHT;
        int sliderY = Math.round(sliderScreenY - screenTop);
        int templateWidth = Math.max(96, Math.round(190 * scaleX));
        int templateHeight = Math.max(96, Math.round(190 * scaleY));
        int sourceTopMin = Math.max(3, sliderY - Math.round(760 * scaleY));
        int sourceTopMax = Math.min(height - templateHeight - 3,
                sliderY - Math.round(250 * scaleY));
        int minDx = Math.round(250 * scaleX);
        int maxDx = Math.min(Math.round(800 * scaleX), width - sourceLeft - templateWidth - 3);
        int roiLeft = Math.max(1, sourceLeft);
        int roiTop = Math.max(1, sourceTopMin - 4);
        int roiRight = Math.min(width - 1, sourceLeft + maxDx + templateWidth + 3);
        int roiBottom = Math.min(height - 1, sourceTopMax + templateHeight + 4);
        int roiWidth = roiRight - roiLeft;
        int roiHeight = roiBottom - roiTop;
        if (sourceTopMax < sourceTopMin || maxDx <= minDx || roiWidth <= templateWidth
                || roiHeight <= templateHeight) {
            return PuzzleSliderMatcherCore.Result.failure(
                    "single-frame region is outside snapshot bounds", elapsedMs(started));
        }

        int[] colors = new int[roiWidth * roiHeight];
        try {
            reader.read(roiLeft, roiTop, roiWidth, roiHeight, colors);
        } catch (Throwable t) {
            return PuzzleSliderMatcherCore.Result.failure(
                    "single-frame pixel extraction failed: " + t.getClass().getSimpleName(),
                    elapsedMs(started));
        }
        float[] gray = grayscale(colors);
        float[] gx = new float[gray.length];
        float[] gy = new float[gray.length];
        float[] magnitude = gradients(gray, roiWidth, roiHeight, gx, gy);
        double[] integral = integral(gray, roiWidth, roiHeight);

        int sampleStep = Math.max(2, Math.round(4 * scaleX));
        int sourceStep = Math.max(6, Math.round(12 * scaleY));
        int dxStep = Math.max(2, Math.round(4 * scaleX));
        int dyStep = Math.max(2, Math.round(4 * scaleY));
        int dyLimit = Math.max(dyStep, Math.round(8 * scaleY));
        int hitRadius = Math.max(1, Math.round(2 * scaleX));
        int minimumPoints = Math.max(45,
                Math.round(90 * scaleX * scaleY * 16f / (sampleStep * sampleStep)));
        Candidate best = null;
        float[] sampled = new float[((templateWidth + sampleStep - 1) / sampleStep)
                * ((templateHeight + sampleStep - 1) / sampleStep)];
        Point[] points = new Point[sampled.length];

        for (int sourceTop = sourceTopMin; sourceTop <= sourceTopMax; sourceTop += sourceStep) {
            if (timedOut(deadline)) {
                return PuzzleSliderMatcherCore.Result.failure("matching timed out", elapsedMs(started));
            }
            int localTop = sourceTop - roiTop;
            int sampledCount = 0;
            for (int y = 0; y < templateHeight; y += sampleStep) {
                int row = (localTop + y) * roiWidth;
                for (int x = 0; x < templateWidth; x += sampleStep) {
                    sampled[sampledCount++] = magnitude[row + x];
                }
            }
            Arrays.sort(sampled, 0, sampledCount);
            float threshold = Math.max(30f, sampled[Math.min(sampledCount - 1,
                    Math.round(sampledCount * 0.88f))]);
            int pointCount = 0;
            for (int y = 0; y < templateHeight; y += sampleStep) {
                int row = (localTop + y) * roiWidth;
                for (int x = 0; x < templateWidth; x += sampleStep) {
                    int index = row + x;
                    float strength = magnitude[index];
                    if (strength < threshold) continue;
                    points[pointCount++] = new Point(x, y, gx[index] / strength, gy[index] / strength);
                }
            }
            if (pointCount < minimumPoints) continue;

            for (int dx = minDx; dx <= maxDx; dx += dxStep) {
                for (int dy = -dyLimit; dy <= dyLimit; dy += dyStep) {
                    float dark = darkContrast(integral, roiWidth, roiHeight,
                            sourceLeft + dx - roiLeft, localTop + dy,
                            templateWidth, templateHeight, scaleX, scaleY);
                    if (dark < 10f) continue;
                    float score = contourScore(magnitude, gx, gy, roiWidth, roiHeight,
                            sourceLeft - roiLeft + dx, localTop + dy, points, pointCount,
                            threshold, hitRadius);
                    if (best == null || score > best.score) {
                        best = new Candidate(dx, dy, sourceTop, score, dark,
                                threshold, points, pointCount);
                    }
                }
            }
        }
        if (best == null || best.score < 0.40f) {
            return PuzzleSliderMatcherCore.Result.failure(
                    "no reliable sparse contour", elapsedMs(started));
        }

        best = refine(best, magnitude, gx, gy, integral, roiWidth, roiHeight,
                sourceLeft - roiLeft, roiTop, minDx, maxDx, templateWidth, templateHeight,
                scaleX, scaleY, hitRadius, deadline);
        if (timedOut(deadline)) {
            return PuzzleSliderMatcherCore.Result.failure("matching timed out", elapsedMs(started));
        }
        int displacement = best.dx;
        if (best.score >= 0.80f && best.dx >= Math.round(700 * scaleX)) {
            displacement = refineDarkLeadingEdge(best, integral, roiWidth, roiHeight,
                    sourceLeft - roiLeft, roiTop, maxDx, templateWidth, templateHeight,
                    scaleX, scaleY);
        }
        return PuzzleSliderMatcherCore.Result.success(displacement, displacement,
                Math.min(1f, best.score), String.format(Locale.ROOT,
                        "single-frame-sparse-contour(score=%.3f,dark=%.1f)",
                        best.score, best.dark), maxDx, elapsedMs(started), sourceLeft,
                best.sourceTop, sourceLeft + displacement, best.sourceTop + best.dy,
                templateWidth, templateHeight);
    }

    private static Candidate refine(Candidate coarse, float[] magnitude, float[] gx, float[] gy,
            double[] integral, int width, int height, int sourceX, int roiTop, int minDx,
            int maxDx, int templateWidth, int templateHeight, float scaleX, float scaleY,
            int hitRadius, long deadline) {
        Candidate best = coarse;
        int radius = Math.max(2, Math.round(5 * scaleX));
        int dyRadius = Math.max(2, Math.round(4 * scaleY));
        int localTop = coarse.sourceTop - roiTop;
        for (int dx = Math.max(minDx, coarse.dx - radius);
             dx <= Math.min(maxDx, coarse.dx + radius); dx++) {
            for (int dy = coarse.dy - dyRadius; dy <= coarse.dy + dyRadius; dy++) {
                if (timedOut(deadline)) return best;
                float dark = darkContrast(integral, width, height, sourceX + dx,
                        localTop + dy, templateWidth, templateHeight, scaleX, scaleY);
                if (dark < 10f) continue;
                float score = contourScore(magnitude, gx, gy, width, height,
                        sourceX + dx, localTop + dy, coarse.points, coarse.pointCount,
                        coarse.threshold, hitRadius);
                if (score > best.score) {
                    best = new Candidate(dx, dy, coarse.sourceTop, score, dark,
                            coarse.threshold, coarse.points, coarse.pointCount);
                }
            }
        }
        return best;
    }

    private static int refineDarkLeadingEdge(Candidate candidate, double[] integral,
            int width, int height, int sourceX, int roiTop, int maxDx, int templateWidth,
            int templateHeight, float scaleX, float scaleY) {
        int from = Math.max(candidate.dx - Math.round(4 * scaleX), 0);
        int to = Math.min(maxDx, candidate.dx + Math.round(28 * scaleX));
        float maximum = Float.NEGATIVE_INFINITY;
        float[] contrasts = new float[to - from + 1];
        int localTop = candidate.sourceTop - roiTop + candidate.dy;
        for (int dx = from; dx <= to; dx++) {
            float contrast = darkContrast(integral, width, height, sourceX + dx,
                    localTop, templateWidth, templateHeight, scaleX, scaleY);
            contrasts[dx - from] = contrast;
            maximum = Math.max(maximum, contrast);
        }
        if (maximum < 20f) return candidate.dx;
        float plateau = maximum * 0.93f;
        for (int dx = from; dx <= to; dx++) {
            if (contrasts[dx - from] >= plateau) return dx;
        }
        return candidate.dx;
    }

    private static float contourScore(float[] magnitude, float[] gx, float[] gy,
            int width, int height, int targetLeft, int targetTop, Point[] points,
            int pointCount, float sourceThreshold, int radius) {
        int[] histogram = new int[32];
        float sum = 0f;
        float targetScale = Math.max(35f, sourceThreshold * 0.6f);
        for (int i = 0; i < pointCount; i++) {
            Point point = points[i];
            float best = 0f;
            for (int offset = -radius; offset <= radius; offset += radius) {
                best = Math.max(best, orientedHit(magnitude, gx, gy, width, height,
                        targetLeft + point.x + offset, targetTop + point.y,
                        point.gx, point.gy, targetScale));
                if (offset != 0) {
                    best = Math.max(best, orientedHit(magnitude, gx, gy, width, height,
                            targetLeft + point.x, targetTop + point.y + offset,
                            point.gx, point.gy, targetScale));
                }
            }
            sum += best;
            histogram[Math.min(histogram.length - 1, (int) (best * histogram.length))]++;
        }
        int quantileTarget = Math.max(1, Math.round(pointCount * 0.55f));
        int seen = 0;
        int quantileBin = 0;
        for (; quantileBin < histogram.length; quantileBin++) {
            seen += histogram[quantileBin];
            if (seen >= quantileTarget) break;
        }
        float quantile = Math.min(histogram.length - 1, quantileBin)
                / (float) (histogram.length - 1);
        return quantile + 0.2f * sum / pointCount;
    }

    private static float orientedHit(float[] magnitude, float[] gx, float[] gy,
            int width, int height, int x, int y, float sourceGx, float sourceGy,
            float targetScale) {
        if (x < 1 || x >= width - 1 || y < 1 || y >= height - 1) return 0f;
        int index = y * width + x;
        float strength = magnitude[index];
        if (strength <= 0f) return 0f;
        float dot = Math.abs(sourceGx * gx[index] / strength
                + sourceGy * gy[index] / strength);
        return dot * Math.min(1f, strength / targetScale);
    }

    private static float darkContrast(double[] integral, int width, int height,
            int left, int top, int templateWidth, int templateHeight,
            float scaleX, float scaleY) {
        if (left < 0 || top < 0 || left + templateWidth > width
                || top + templateHeight > height) return Float.NEGATIVE_INFINITY;
        int outer = Math.max(8, Math.round(15 * Math.min(scaleX, scaleY)));
        int inner = Math.max(outer + 2, Math.round(20 * Math.min(scaleX, scaleY)));
        double borderSum = rectSum(integral, width, left, top,
                left + templateWidth, top + templateHeight)
                - rectSum(integral, width, left + outer, top + outer,
                left + templateWidth - outer, top + templateHeight - outer);
        int borderCount = templateWidth * templateHeight
                - (templateWidth - 2 * outer) * (templateHeight - 2 * outer);
        double insideSum = rectSum(integral, width, left + inner, top + inner,
                left + templateWidth - inner, top + templateHeight - inner);
        int insideCount = (templateWidth - 2 * inner) * (templateHeight - 2 * inner);
        if (borderCount <= 0 || insideCount <= 0) return Float.NEGATIVE_INFINITY;
        return (float) (borderSum / borderCount - insideSum / insideCount);
    }

    private static double rectSum(double[] integral, int width, int left, int top,
            int right, int bottom) {
        int stride = width + 1;
        return integral[bottom * stride + right] - integral[top * stride + right]
                - integral[bottom * stride + left] + integral[top * stride + left];
    }

    private static double[] integral(float[] values, int width, int height) {
        double[] result = new double[(width + 1) * (height + 1)];
        for (int y = 0; y < height; y++) {
            double row = 0d;
            for (int x = 0; x < width; x++) {
                row += values[y * width + x];
                result[(y + 1) * (width + 1) + x + 1]
                        = result[y * (width + 1) + x + 1] + row;
            }
        }
        return result;
    }

    private static float[] grayscale(int[] colors) {
        float[] gray = new float[colors.length];
        for (int i = 0; i < colors.length; i++) {
            int color = colors[i];
            gray[i] = (77 * ((color >>> 16) & 255) + 150 * ((color >>> 8) & 255)
                    + 29 * (color & 255)) / 256f;
        }
        return gray;
    }

    private static float[] gradients(float[] gray, int width, int height,
            float[] gx, float[] gy) {
        float[] magnitude = new float[gray.length];
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int index = y * width + x;
                float xx = gray[index + 1] - gray[index - 1];
                float yy = gray[index + width] - gray[index - width];
                gx[index] = xx;
                gy[index] = yy;
                magnitude[index] = (float) Math.sqrt(xx * xx + yy * yy);
            }
        }
        return magnitude;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static boolean timedOut(long deadline) {
        return System.nanoTime() >= deadline || Thread.currentThread().isInterrupted();
    }

    private static final class Point {
        final int x;
        final int y;
        final float gx;
        final float gy;

        Point(int x, int y, float gx, float gy) {
            this.x = x;
            this.y = y;
            this.gx = gx;
            this.gy = gy;
        }
    }

    private static final class Candidate {
        final int dx;
        final int dy;
        final int sourceTop;
        final float score;
        final float dark;
        final float threshold;
        final Point[] points;
        final int pointCount;

        Candidate(int dx, int dy, int sourceTop, float score, float dark,
                float threshold, Point[] points, int pointCount) {
            this.dx = dx;
            this.dy = dy;
            this.sourceTop = sourceTop;
            this.score = score;
            this.dark = dark;
            this.threshold = threshold;
            this.points = Arrays.copyOf(points, pointCount);
            this.pointCount = pointCount;
        }
    }
}
