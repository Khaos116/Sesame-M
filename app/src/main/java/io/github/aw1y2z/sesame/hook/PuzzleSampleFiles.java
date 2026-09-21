package io.github.aw1y2z.sesame.hook;

import java.io.File;
import java.util.Arrays;

/**
 * 拼图验证码截图的文件管理（纯 java.io，方便在 JVM 里直接测）。
 * <p>只有真正拖动过的截图（名字含 {@value #MATCHED_MARK}）放在账号的 puzzle 目录里，每个账号 matched 最多保留最新
 * {@link #KEEP_MATCHED} 张、matched_submit（松手前）最多保留 {@link #KEEP_SUBMIT} 张，合计最多 20 张；没识别到滑块/没轨道/匹配失败的放在 tmp/ 子目录，验证结束时整个删掉，
 * 所以平时看的目录里只有 matched。
 */
final class PuzzleSampleFiles {
    static final String TMP_DIR = "tmp";
    static final String MATCHED_MARK = "-matched-";
    /** 松手前那张（matched_submit-d…）单独一组，和 matched-d…/matched-after… 各留 KEEP 张，互不挤占。 */
    static final String SUBMIT_MARK = "-matched_submit-";
    static final String SUBMIT_TAG = "matched_submit";
    static final int KEEP_MATCHED = 10;
    static final int KEEP_SUBMIT = 10;
    /** tmp/ 里的兜底上限（一个窗口最多 12 次截图，再留一点余量）。 */
    static final int KEEP_TMP = 16;

    private PuzzleSampleFiles() {
    }

    /** 这张截图应该放的目录：matched 放账号目录，其余放 tmp/。 */
    static File targetDir(File accountDir, String tag) {
        return tag.startsWith("matched") ? accountDir : new File(accountDir, TMP_DIR);
    }

    /** 文件名：puzzle-<时间戳>-<tag>.png；matched 的 tag 形如 matched-d123，所以名字里含 -matched。 */
    static File fileFor(File accountDir, String tag, long timeMillis) {
        return new File(targetDir(accountDir, tag), "puzzle-" + timeMillis + "-" + tag + ".png");
    }

    /** 保存后轮换：matched、matched_submit 各按自己的名字标记统计（旧版本遗留的无关文件不占名额），tmp 用自己的上限。 */
    static void rotate(File accountDir, String tag) {
        boolean matched = tag.startsWith("matched");
        boolean submit = tag.startsWith(SUBMIT_TAG);
        prune(targetDir(accountDir, tag), submit ? KEEP_SUBMIT : matched ? KEEP_MATCHED : KEEP_TMP,
                submit ? SUBMIT_MARK : matched ? MATCHED_MARK : null);
    }

    private static boolean kept(String name) {
        return name.contains(MATCHED_MARK) || name.contains(SUBMIT_MARK);
    }

    /** 只保留 dir 里最新 keep 张 puzzle-*.png；mark 非空时只统计名字含 mark 的。 */
    static void prune(File dir, int keep, String mark) {
        File[] files = dir.listFiles((d, name) -> name.startsWith("puzzle-") && name.endsWith(".png")
                && (mark == null || name.contains(mark)));
        if (files == null || files.length <= keep) {
            return;
        }
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < files.length - keep; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }

    /**
     * 清掉没拖动过的截图：删除 tmp/ 整个目录，以及账号目录里旧版本留下的散落文件（名字里既不含 -matched- 也不含 -matched_submit- 的 puzzle-*.png）。
     * 返回删除张数。
     */
    static int deleteNonMatched(File accountDir) {
        int deleted = 0;
        File tmp = new File(accountDir, TMP_DIR);
        File[] tmpFiles = tmp.listFiles();
        if (tmpFiles != null) {
            for (File file : tmpFiles) {
                if (file.delete()) {
                    deleted++;
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        File[] legacy = accountDir.listFiles((d, name) -> name.startsWith("puzzle-") && name.endsWith(".png")
                && !kept(name));
        if (legacy != null) {
            for (File file : legacy) {
                if (file.delete()) {
                    deleted++;
                }
            }
        }
        return deleted;
    }
}
