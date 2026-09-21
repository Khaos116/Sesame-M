package io.github.aw1y2z.sesame.hook;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RandomUtil;

/**
 * 自动处理「对准图片」的拼图滑块验证码（H5 WebView 渲染）。
 * <p>流程：验证被要求时 {@link #arm} → 主线程每秒扫描一次窗口（最多 60 次）找 WebView → 对 WebView 截图（PixelCopy）
 * → 工作线程识别滑块按钮/轨道终点并做图像匹配得到缺口位移 → 主线程用触摸事件把滑块拖过去。
 * 图像匹配（{@link PuzzleSliderMatcher} 等）与 GR2026 一致，已用其真实样本离线回放（checks/check_puzzle_matcher.py）。
 * <p>约束（来自 GR 的经验，也是为了不加重风控）：每个验证码窗口最多自动拖动 N 次（配置项，默认 4，范围 1-5；拖错后
 * 等页面刷新出新图再重试，GR 只拖一次，这里按用户要求放宽）；识别置信度不够就不动手；
 * 只在验证被要求后的窗口期内扫描，不会在任意 H5 页面上乱点。
 * <p>坐标常量按 GR 记录的设备布局（参考宽度 1264）缩放，其它布局可能识别不到滑块——这时只会记日志并保存截图，
 * 不会拖动；截图在 sesame-M/puzzle/<账号名>/ 里，发给我用来校准。
 */
public final class PuzzleCaptchaSolver {
    private static final String TAG = "PuzzleCaptchaSolver";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final long POLL_MS = 1000L;
    private static final int MAX_POLLS = 60;
    private static final int PASSIVE_POLLS = 8;
    private static final int MAX_CAPTURES_PER_WINDOW = 12;
    private static final int DEFAULT_ATTEMPTS = 4;
    private static final int ATTEMPTS_LIMIT = 5;
    private static final int RETRY_POLLS = 30;
    private static final long MATCH_BUDGET_MS = 3500L;
    private static final long SLIDE_MIN_MS = 850L;
    private static final long SLIDE_MAX_MS = 950L;
    /** 每个账号最多保留多少张包含验证码的截图（目录按账号分）。 */

    private static final float REFERENCE_WIDTH = 1264f;
    private static final float START_X = 236f;
    private static final float START_Y = 1787f;
    // 同一验证码在不同设备有不同的水平留白：固定坐标只作宽松候选提示，真正的按钮靠颜色连通块识别
    private static final float START_TOLERANCE = 130f;
    private static final float VERTICAL_TOLERANCE = 700f;

    /**
     * 每个验证码 WebView 已经自动拖动的次数；失败后允许重试，但总数有上限（连续失败太多会加重风控）。
     * 按 WebView 而不是窗口计数：每次重新弹出验证码都是新的 WebView，从 0 开始；验证成功、换号、
     * 重启支付宝（进程重建）也都会归零。仅主线程访问。
     */
    private static final Map<View, Integer> ATTEMPTS = new WeakHashMap<>();
    private static long attemptsGeneration = -1;
    private static final Map<View, Integer> CAPTURES = new WeakHashMap<>();
    private static final Map<View, String> LAST_DIAG = new WeakHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SesamePuzzleSolver");
        thread.setDaemon(true);
        return thread;
    });

    // 以下仅主线程访问
    private static boolean polling;
    private static boolean busy;
    private static int polls;
    private static int maxPolls = MAX_POLLS;
    /** 扫描循环的令牌：每次（重新）开始换一个新值，旧循环下一次触发时发现令牌不符就自己退出，避免同时跑两条循环。 */
    private static int pollToken;
    private static WeakReference<Dialog> hintDialog = new WeakReference<>(null);
    /** 被动扫描（页面恢复触发）时不刷验证记录，直到真的识别到拼图滑块。工作线程也会读，所以 volatile。 */
    private static volatile boolean quiet;
    private static long generation;
    private static boolean disabledLogged;

    private PuzzleCaptchaSolver() {
    }

    /**
     * 验证被要求时调用（任意线程）：接口返回“请验证”、或验证码弹窗出现。重复调用只会把 60 秒窗口期重新计时。
     */
    public static void arm(String source) {
        arm(source, null);
    }

    /** dialog 是验证码弹窗对象时传进来，扫描时直接用它，不依赖窗口跟踪列表。 */
    public static void arm(String source, Dialog dialog) {
        long armedGeneration = TaskLifecycle.generation();
        MAIN.post(() -> {
            if (!isEnabled()) {
                if (!disabledLogged) {
                    disabledLogged = true;
                    Log.captcha("拼图验证⏸️自动图片滑块开关已关闭，不处理（来源[" + source + "]）");
                }
                return;
            }
            if (dialog != null) {
                hintDialog = new WeakReference<>(dialog);
            }
            quiet = false;
            maxPolls = MAX_POLLS;
            polls = 0;
            generation = armedGeneration;
            if (polling) {
                return;
            }
            cleanupNoSlider(); // 上次残留的没拖动过的截图
            Log.captcha("拼图验证🧩开始监视验证窗口，最长 " + MAX_POLLS + " 秒（来源[" + source + "]）");
            startPolling();
        });
    }

    /**
     * 被动触发（XRiver/首页页面恢复，GR 也在这两个页面挂验证码处理器）：只短时扫描几次，
     * 平时不写验证记录，识别到拼图滑块才转为正常流程。已经在监视时不打断也不延长。
     */
    public static void armPassive(String source) {
        long armedGeneration = TaskLifecycle.generation();
        MAIN.post(() -> {
            if (polling || !isEnabled()) {
                return;
            }
            quiet = true;
            maxPolls = PASSIVE_POLLS;
            polls = 0;
            generation = armedGeneration;
            startPolling();
        });
    }

    /** 开始一条新的扫描循环（主线程）：令牌加一，之前排着队的旧循环不再执行。 */
    private static void startPolling() {
        polling = true;
        pollToken++;
        schedulePoll(pollToken);
    }

    private static void schedulePoll(int token) {
        MAIN.postDelayed(() -> poll(token), POLL_MS);
    }

    private static boolean isEnabled() {
        try {
            return Boolean.TRUE.equals(BaseModel.getAutoPuzzleSlider().getValue());
        } catch (Throwable t) {
            // 配置还没加载好就按开启处理会误动手，宁可不动
            return false;
        }
    }

    private static void poll(int token) {
        // 拖动完成/窗口期结束/重新开始后，旧循环排着队的这一次不能再跑（否则会和新循环同时扫描、重复打日志）
        if (token != pollToken || !polling) {
            return;
        }
        try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
            if (work == null) {
                polling = false; // 账号切换中或已切走：旧账号的窗口期作废
                return;
            }
            polls++;
            if (!busy) {
                scanOnce();
            }
            if (polls >= maxPolls) {
                polling = false;
                if (!busy && !quiet) {
                    Log.captcha("拼图验证🧩监视窗口期结束，没有可处理的拼图窗口");
                }
                if (!quiet) {
                    // 最后一次扫描刚发起截图时分析还在进行，稍后才会存图：等它做完再清理，否则会漏掉最后一张
                    if (busy) {
                        MAIN.postDelayed(PuzzleCaptchaSolver::cleanupNoSlider, 6000L);
                    } else {
                        cleanupNoSlider();
                    }
                }
                return;
            }
            schedulePoll(token);
        } catch (Throwable t) {
            polling = false;
            Log.printStackTrace(TAG, t);
        }
    }

    private static final class Target {
        final View root;
        final Window window;

        Target(View root, Window window) {
            this.root = root;
            this.window = window;
        }
    }

    /** 当前可见窗口：栈顶 Activity + 被监控到的显示中对话框（对话框靠后，优先扫描）。 */
    private static List<Target> windows() {
        List<Target> targets = new ArrayList<>();
        Activity top = SimplePageManager.getTopActivity();
        if (top != null && !top.isFinishing() && top.getWindow() != null) {
            targets.add(new Target(top.getWindow().getDecorView(), top.getWindow()));
        }
        for (WeakReference<Dialog> ref : new ArrayList<>(SimplePageManager.getDialogs())) {
            Dialog dialog = ref.get();
            if (dialog != null && dialog.isShowing() && dialog.getWindow() != null) {
                targets.add(new Target(dialog.getWindow().getDecorView(), dialog.getWindow()));
            }
        }
        Dialog hinted = hintDialog.get();
        if (hinted != null && hinted.isShowing() && hinted.getWindow() != null) {
            View decor = hinted.getWindow().getDecorView();
            boolean present = false;
            for (Target target : targets) {
                if (target.root == decor) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                targets.add(new Target(decor, hinted.getWindow()));
            }
        }
        return targets;
    }

    private static void scanOnce() {
        List<Target> targets = windows();
        for (int i = targets.size() - 1; i >= 0; i--) {
            Target target = targets.get(i);
            View root = target.root;
            if (root == null || !root.isShown()) {
                continue;
            }
            View web = findWebView(root);
            if (web == null || !web.isShown() || web.getWidth() < 400 || web.getHeight() < 400
                    || attemptsOf(web) >= maxAttempts()) {
                continue;
            }
            int captures = CAPTURES.containsKey(root) ? CAPTURES.get(root) : 0;
            if (captures >= MAX_CAPTURES_PER_WINDOW) {
                continue;
            }
            CAPTURES.put(root, captures + 1);
            busy = true;
            capture(target, web, (bitmap, error) -> {
                if (bitmap == null) {
                    diag(root, "截图失败:" + error);
                    busy = false;
                    return;
                }
                submit(target, web, bitmap);
            });
            return;
        }
    }

    private static View findWebView(View view) {
        if (view == null) {
            return null;
        }
        View nested = null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View candidate = findWebView(group.getChildAt(i));
                if (candidate != null) {
                    nested = candidate;
                }
            }
        }
        if (nested != null) {
            return nested;
        }
        return isWebView(view) ? view : null;
    }

    /** 支付宝的验证码在 com.alipay.mywebview.sdk.WebView 里渲染，它不一定继承 android.webkit.WebView。 */
    private static boolean isWebView(View view) {
        if (view instanceof android.webkit.WebView) {
            return true;
        }
        String name = view.getClass().getName();
        if ("android.webkit.WebView".equals(name) || "com.alipay.mywebview.sdk.WebView".equals(name)) {
            return true;
        }
        CharSequence accessibility = view.getAccessibilityClassName();
        return accessibility != null && ("android.webkit.WebView".contentEquals(accessibility)
                || "com.alipay.mywebview.sdk.WebView".contentEquals(accessibility));
    }

    private interface CaptureCallback {
        void onCaptured(Bitmap bitmap, String error);
    }

    /** 主线程：对 WebView 实际渲染画面截图（PixelCopy；WebView 走硬件渲染，软件 draw 可能是空白）。 */
    private static void capture(Target target, View web, CaptureCallback callback) {
        if (web.getWidth() <= 0 || web.getHeight() <= 0) {
            callback.onCaptured(null, "invalid size");
            return;
        }
        Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(web.getWidth(), web.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            callback.onCaptured(null, "bitmap allocation failed: " + t);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && target.window != null) {
            int[] inWindow = new int[2];
            web.getLocationInWindow(inWindow);
            Rect source = new Rect(inWindow[0], inWindow[1], inWindow[0] + web.getWidth(), inWindow[1] + web.getHeight());
            try {
                PixelCopy.request(target.window, source, bitmap, result -> {
                    if (result == PixelCopy.SUCCESS) {
                        callback.onCaptured(bitmap, null);
                    } else {
                        bitmap.recycle();
                        callback.onCaptured(null, "PixelCopy result=" + result);
                    }
                }, MAIN);
                return;
            } catch (Throwable t) {
                bitmap.recycle();
                callback.onCaptured(null, "PixelCopy failed: " + t);
                return;
            }
        }
        try {
            web.draw(new Canvas(bitmap));
            callback.onCaptured(bitmap, null);
        } catch (Throwable t) {
            bitmap.recycle();
            callback.onCaptured(null, "draw failed: " + t);
        }
    }

    /** 主线程：把截图交给工作线程做识别与匹配。 */
    private static void submit(Target target, View web, Bitmap bitmap) {
        final long submitGeneration = generation;
        int[] location = new int[2];
        web.getLocationOnScreen(location);
        final float scale = web.getWidth() / REFERENCE_WIDTH;
        try {
            WORKER.execute(() -> {
                Runnable release = () -> busy = false;
                try (TaskLifecycle.Work work = TaskLifecycle.enter(submitGeneration)) {
                    if (work == null) {
                        bitmap.recycle();
                        MAIN.post(release);
                        return;
                    }
                    analyze(target, web, bitmap, location, scale, release);
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, t);
                    if (!bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                    MAIN.post(release);
                }
            });
        } catch (Throwable t) {
            bitmap.recycle();
            busy = false;
            Log.printStackTrace(TAG, t);
        }
    }

    /** 工作线程：识别滑块 → 图像匹配 → 结果回主线程执行拖动。 */
    private static void analyze(Target target, View web, Bitmap bitmap, int[] location, float scale, Runnable release) {
        View root = target.root;
        Slider slider = detectSlider(bitmap, scale);
        if (slider != null && quiet && slider.hasTrackEnd()) {
            quiet = false; // 按钮和轨道都识别到才像验证码；只有按钮多半是普通页面上的蓝/红按钮
            Log.captcha("拼图验证🧩被动扫描发现拼图滑块（未经接口/弹窗触发）：" + slider.describe());
        }
        if (slider == null) {
            diag(root, "未识别到拼图滑块按钮（图片可能还在加载，或布局与参考设备不同）");
            // 没识别到滑块的图（图片还没加载、窗口是别的页面…）先都存着，方便看过程；验证结束后
            // cleanupNoSlider() 统一删掉，只留包含验证码（识别到滑块）的截图
            if (!quiet) {
                saveSample(bitmap, "no-slider", false);
            }
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        if (!slider.atStart(scale)) {
            diag(root, "滑块不在轨道左端，跳过：" + slider.describe());
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        if (!slider.hasTrackEnd()) {
            diag(root, "未识别到轨道终点：" + slider.describe());
            saveSample(bitmap, "no-track", false);
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        float startX = location[0] + slider.centerX;
        float startY = location[1] + slider.centerY;
        float trackEndX = location[0] + slider.trackEndX;
        int sourceLeft = Math.round(slider.centerX - slider.buttonWidth / 2f);
        PuzzleSliderMatcher.Result match = PuzzleSliderMatcher.estimate(
                bitmap, startY, location[1], MATCH_BUDGET_MS, sourceLeft);
        if (!match.success) {
            diag(root, "缺口位移识别失败（不拖动）：" + match.error);
            saveSample(bitmap, "match-failed", false);
            bitmap.recycle();
            MAIN.post(release);
            return;
        }
        String sampleName = saveSample(bitmap, "matched-d" + match.displacement, false);
        bitmap.recycle();
        MAIN.post(() -> swipe(target, web, slider, match, startX, startY, trackEndX, sampleName, release));
    }

    /** 主线程：窗口仍然有效才拖动，并只拖一次。 */
    private static void swipe(Target target, View web, Slider slider, PuzzleSliderMatcher.Result match,
                              float startX, float startY, float trackEndX, String sampleName, Runnable release) {
        View root = target.root;
        if (!web.isShown() || !web.isAttachedToWindow() || attemptsOf(web) >= maxAttempts()) {
            release.run();
            return;
        }
        PuzzleSliderGeometry.Mapping mapping = PuzzleSliderGeometry.map(match.displacement, startX, trackEndX);
        if (!mapping.success || mapping.touchDistance <= 0f) {
            diag(root, "轨道换算失败：" + mapping.error);
            release.run();
            return;
        }
        final int attempt = attemptsOf(web) + 1;
        ATTEMPTS.put(web, attempt);
        long duration = SLIDE_MIN_MS + RandomUtil.nextInt(0, (int) (SLIDE_MAX_MS - SLIDE_MIN_MS + 1));
        Log.captcha(String.format(java.util.Locale.ROOT,
                "拼图验证🧩第%d/" + maxAttempts() + "次识别成功，开始拖动：缺口位移=%dpx 触摸距离=%.0fpx 终点=%.0f 轨道截断=%s 方法=%s 分数=%.3f 耗时=%dms 滑块=%s 截图=%s",
                attempt, match.displacement, mapping.touchDistance, mapping.endX, mapping.clamped, match.method,
                match.bestScore, match.elapsedMs, slider.describe(), sampleName));
        PuzzleSwipe.start(web, startX, startY, mapping.endX, startY, duration, 12f,
                () -> web.isShown() && web.isAttachedToWindow(),
                proceed -> saveBeforeReleaseShot(target, web, match.displacement, attempt, proceed),
                (sent, reason) -> {
                    Log.captcha("拼图验证🧩拖动" + (sent ? "已完成" : "未完成") + "（" + reason + "）");
                    polling = false; // 拖完先停止扫描，1.5 秒后按页面结果决定是结束还是重试
                    MAIN.postDelayed(() -> {
                        boolean closed = !web.isAttachedToWindow() || !web.isShown();
                        if (closed) {
                            // 窗口关了多半是通过了：解除接口的验证暂停，触发验证的功能不必再等到期。
                            // 若其实没通过，接口下次还会返回“请验证”，会重新暂停并重新监视
                            io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard.clearVerifyPause();
                            ATTEMPTS.remove(web); // 验证成功：尝试次数归零
                            Log.captcha("拼图验证🧩拖动 1.5 秒后：验证窗口已关闭，多半通过（尝试次数已归零）");
                            cleanupNoSlider(); // 验证结束：只留包含验证码的截图
                        } else if (attempt < maxAttempts()) {
                            Log.captcha("拼图验证🧩拖动 1.5 秒后：验证窗口仍在，第 " + attempt + " 次没通过，等页面刷新出新图后重试");
                            saveAfterShot(target, web, match.displacement, attempt);
                            retry(root);
                        } else {
                            Log.captcha("拼图验证🧩拖动 1.5 秒后：验证窗口仍在，已自动尝试 " + attempt + " 次不再重试，可手动完成");
                            saveAfterShot(target, web, match.displacement, attempt);
                            cleanupNoSlider();
                        }
                    }, 1500L);
                    release.run();
                });
    }

    /** 每个窗口最多自动尝试几次：读配置，限制在 1 到 5；读不到就用默认 4（连续失败太多会加重风控，所以有上限）。 */
    private static int maxAttempts() {
        try {
            Integer value = BaseModel.getPuzzleMaxAttempts().getValue();
            if (value != null) {
                return Math.max(1, Math.min(ATTEMPTS_LIMIT, value));
            }
        } catch (Throwable ignored) {
            // 配置没加载好：用默认值
        }
        return DEFAULT_ATTEMPTS;
    }

    /**
     * 拖完 1.5 秒窗口还在（多半没对准）时再截一张“拖动之后”的图，存成 matched-after-d<位移>-a<第几次>（属于 matched，会保留）：
     * 对照拖动前的 matched-d<位移> 就能看出滑块到底停在缺口的哪里，是位移算偏了还是页面进了别的状态。
     */
    private static void saveAfterShot(Target target, View web, int displacement, int attempt) {
        try {
            capture(target, web, (bitmap, error) -> {
                if (bitmap == null) {
                    return;
                }
                try {
                    WORKER.execute(() -> {
                        try {
                            saveSample(bitmap, "matched-after-d" + displacement + "-a" + attempt, false);
                        } finally {
                            bitmap.recycle();
                        }
                    });
                } catch (Throwable t) {
                    bitmap.recycle();
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    /**
     * 手指停在终点、抬起之前截一张（matched_submit-d<位移>-a<第几次>）：记录最终提交的位置，
     * 和拖动前的 matched-d<位移>、拖动后的 matched-after 对照。截到图（或失败）就 proceed 抬起，主线程调用。
     */
    private static void saveBeforeReleaseShot(Target target, View web, int displacement, int attempt, Runnable proceed) {
        try {
            capture(target, web, (bitmap, error) -> {
                proceed.run(); // 图已经拷出来了，不必等落盘
                if (bitmap == null) {
                    return;
                }
                try {
                    WORKER.execute(() -> {
                        try {
                            saveSample(bitmap, "matched_submit-d" + displacement + "-a" + attempt, false);
                        } finally {
                            bitmap.recycle();
                        }
                    });
                } catch (Throwable t) {
                    bitmap.recycle();
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            proceed.run();
        }
    }

    private static int attemptsOf(View web) {
        // 换号（TaskLifecycle 代数变化）时整体归零
        long current = TaskLifecycle.generation();
        if (current != attemptsGeneration) {
            ATTEMPTS.clear();
            attemptsGeneration = current;
        }
        Integer count = ATTEMPTS.get(web);
        return count == null ? 0 : count;
    }

    /**
     * 拖错后页面会刷新出新图：清掉这个窗口的截图计数和诊断去重，重新开始一轮监视（最多 RETRY_POLLS 秒）。
     * 新一轮仍要求滑块回到轨道左端、匹配可信才动手，所以页面还在刷新时不会乱拖。
     */
    private static void retry(View root) {
        CAPTURES.remove(root);
        LAST_DIAG.remove(root);
        quiet = false;
        maxPolls = RETRY_POLLS;
        polls = 0;
        startPolling(); // 总是换新令牌：拖动完成时 polling 已置 false，旧循环会在下一次触发时自己退出
    }

    /** 同一窗口同一原因只记一次，避免每秒刷屏。 */
    private static void diag(View root, String message) {
        if (quiet) {
            return;
        }
        String last = LAST_DIAG.get(root);
        if (message.equals(last)) {
            return;
        }
        LAST_DIAG.put(root, message);
        Log.captcha("拼图验证🧩" + message);
    }

    /**
     * 保存截图，返回相对文件名（保存失败返回 "未保存"）。放哪里、留几张见 {@link PuzzleSampleFiles}：
     * 只有拖动过的 matched 放账号 puzzle 目录，其余放 tmp/ 验证结束时删。
     */
    private static String saveSample(Bitmap bitmap, String tag, boolean ignored) {
        try {
            File accountDir = FileUtil.getCurrentUserPuzzleDirectory();
            File file = PuzzleSampleFiles.fileFor(accountDir, tag, System.currentTimeMillis());
            File parent = file.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                return "未保存";
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            PuzzleSampleFiles.rotate(accountDir, tag);
            return (parent.equals(accountDir) ? "" : PuzzleSampleFiles.TMP_DIR + "/") + file.getName();
        } catch (Throwable t) {
            return "未保存";
        }
    }

    /**
     * 验证结束（拖动完成/监视窗口期结束）后：只留拖动过的 matched 截图。开始新一轮监视前也会调用一次，清上次的残留。
     * 工作线程执行，避免主线程做文件 IO；删除了才写日志，方便确认清理真的执行了。
     */
    private static void cleanupNoSlider() {
        try {
            WORKER.execute(() -> {
                try {
                    int deleted = PuzzleSampleFiles.deleteNonMatched(FileUtil.getCurrentUserPuzzleDirectory());
                    if (deleted > 0) {
                        Log.captcha("拼图验证🧩已清理 " + deleted + " 张没有拖动过的截图，只保留 matched");
                    }
                } catch (Throwable t) {
                    Log.printStackTrace(TAG, t);
                }
            });
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    // ===================== 滑块按钮与轨道识别（移植自 GR BaseCaptchaHandler） =====================

    private static final class Slider {
        final float centerX;
        final float centerY;
        final float trackEndX;
        final int buttonWidth;

        Slider(float centerX, float centerY, float trackEndX, int buttonWidth) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.trackEndX = trackEndX;
            this.buttonWidth = buttonWidth;
        }

        boolean hasTrackEnd() {
            return Float.isFinite(trackEndX) && trackEndX > centerX;
        }

        boolean atStart(float scale) {
            return Math.abs(centerX - START_X * scale) <= START_TOLERANCE * scale;
        }

        String describe() {
            return "center=(" + Math.round(centerX) + "," + Math.round(centerY) + ") buttonWidth=" + buttonWidth
                    + " trackEnd=" + (hasTrackEnd() ? String.valueOf(Math.round(trackEndX)) : "无");
        }
    }

    private static Slider detectSlider(Bitmap bitmap, float scale) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        try {
            int scanTop = Math.max(0, Math.round((START_Y - VERTICAL_TOLERANCE) * scale));
            int scanBottom = Math.min(bitmap.getHeight(), Math.round((START_Y + VERTICAL_TOLERANCE) * scale));
            int scanLeft = Math.max(0, Math.round((START_X - 180f) * scale));
            int scanRight = Math.min(bitmap.getWidth(), Math.round((START_X + 220f) * scale));
            int[] c = findButtonComponent(bitmap, scanLeft, scanTop, scanRight, scanBottom, scale);
            if (c == null) {
                return null;
            }
            float trackEnd = detectTrackEnd(bitmap, c[0], c[1], c[2], c[3], scale);
            return new Slider((c[0] + c[2]) / 2f, (c[1] + c[3]) / 2f, trackEnd, c[2] - c[0] + 1);
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return null;
        }
    }

    /**
     * 取一个连通的彩色块（蓝/红按钮）而不是所有蓝/红像素的包围盒：错误页常带另一块彩色插图，
     * 合并会让坐标漂移。返回 {left, top, right, bottom}，找不到返回 null。
     */
    private static int[] findButtonComponent(Bitmap bitmap, int left, int top, int right, int bottom, float scale) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) {
            return null;
        }
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        int minWidth = Math.max(50, Math.round(60f * scale));
        int maxWidth = Math.max(minWidth, Math.round(180f * scale));
        int minHeight = Math.max(45, Math.round(55f * scale));
        int maxHeight = Math.max(minHeight, Math.round(180f * scale));
        int minPixels = Math.max(400, Math.round(1800f * scale * scale));
        float expectedX = START_X * scale;
        float expectedY = START_Y * scale;
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int index = 0; index < visited.length; index++) {
            if (visited[index]) {
                continue;
            }
            int x = left + index % width;
            int y = top + index / width;
            if (!isButtonColor(bitmap.getPixel(x, y))) {
                visited[index] = true;
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = index;
            visited[index] = true;
            int minX = x, maxX = x, minY = y, maxY = y;
            while (head < tail) {
                int point = queue[head++];
                int px = point % width;
                int py = point / width;
                minX = Math.min(minX, left + px);
                maxX = Math.max(maxX, left + px);
                minY = Math.min(minY, top + py);
                maxY = Math.max(maxY, top + py);
                if (px > 0 && !visited[point - 1] && isButtonColor(bitmap.getPixel(left + px - 1, top + py))) {
                    visited[point - 1] = true;
                    queue[tail++] = point - 1;
                }
                if (px + 1 < width && !visited[point + 1] && isButtonColor(bitmap.getPixel(left + px + 1, top + py))) {
                    visited[point + 1] = true;
                    queue[tail++] = point + 1;
                }
                if (py > 0 && !visited[point - width] && isButtonColor(bitmap.getPixel(left + px, top + py - 1))) {
                    visited[point - width] = true;
                    queue[tail++] = point - width;
                }
                if (py + 1 < height && !visited[point + width] && isButtonColor(bitmap.getPixel(left + px, top + py + 1))) {
                    visited[point + width] = true;
                    queue[tail++] = point + width;
                }
            }
            int componentWidth = maxX - minX + 1;
            int componentHeight = maxY - minY + 1;
            float centerX = (minX + maxX) / 2f;
            float centerY = (minY + maxY) / 2f;
            if (tail < minPixels || componentWidth < minWidth || componentWidth > maxWidth
                    || componentHeight < minHeight || componentHeight > maxHeight
                    || Math.abs(centerX - expectedX) > START_TOLERANCE * scale
                    || Math.abs(centerY - expectedY) > VERTICAL_TOLERANCE * scale) {
                continue;
            }
            float distance = Math.abs(centerX - expectedX) + Math.abs(centerY - expectedY) * 0.5f;
            if (best == null || distance < bestDistance) {
                best = new int[]{minX, minY, maxX, maxY};
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isButtonColor(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        return (blue >= 170 && green >= 60 && red <= 120 && blue - red >= 70)
                || (red >= 160 && green <= 140 && blue <= 140 && red - blue >= 70);
    }

    /** 轨道是紧挨按钮右侧的浅色低饱和矩形，右端减去半个按钮宽度就是按钮中心能到的终点。 */
    private static float detectTrackEnd(Bitmap bitmap, int buttonLeft, int buttonTop, int buttonRight,
                                        int buttonBottom, float scale) {
        int inferredWidth = buttonRight - buttonLeft + 1 + 2 * Math.max(2, Math.round(3f * scale));
        int minRail = Math.max(inferredWidth * 2, Math.round(300f * scale));
        int bestEnd = -1;
        int inset = Math.max(4, Math.round(16f * scale));
        for (int y = buttonTop + inset; y <= buttonBottom - inset; y += Math.max(2, Math.round(4f * scale))) {
            int x = buttonRight + 1;
            int searchLimit = Math.min(bitmap.getWidth() - 1, x + Math.max(12, Math.round(12f * scale)));
            while (x <= searchLimit && !isLightNeutral(bitmap.getPixel(x, y))) {
                x++;
            }
            int runStart = x;
            while (x < bitmap.getWidth() && isLightNeutral(bitmap.getPixel(x, y))) {
                x++;
            }
            int runEnd = x - 1;
            if (runEnd - runStart + 1 >= minRail) {
                bestEnd = Math.max(bestEnd, runEnd);
            }
        }
        return bestEnd < 0 ? Float.NaN : bestEnd + 1f - inferredWidth / 2f;
    }

    private static boolean isLightNeutral(int color) {
        int red = (color >>> 16) & 0xff;
        int green = (color >>> 8) & 0xff;
        int blue = color & 0xff;
        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= 225 && max <= 253 && max - min <= 5;
    }
}
