package io.github.aw1y2z.sesame.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号目录/导出文件名里用的账号名：优先用配置页账号列表里括号前面的名字（备注，没有就用昵称，如 C176），
 * 找不到才用 uid。日志目录 {@code log/<名字>/}、拼图截图目录 {@code puzzle/<名字>/}、导出文件名都用它。
 * <p>纯逻辑，文件读写通过 {@link Source}/{@link Store} 注入，方便在 JVM 里直接测（checks/check_account_folder.py）。
 * <ul>
 *   <li>名字只做文件名安全处理（非字母数字/下划线/连字符 → 下划线，最长 {@value #MAX_LABEL} 个字符）。</li>
 *   <li>两个账号同名时后来者加 uid 后 4 位区分（{@code C176-1743}），靠目录里的 {@code .uid} 标记文件判断归属。</li>
 *   <li>首次用名字时把旧的 uid 目录改名成名字目录（保留历史日志）。</li>
 *   <li>每个 uid 在一个进程里只解析一次并缓存：日志写入器按目录路径创建，进程中途换目录会把日志拆成两半；
 *       所以第一次没找到名字（新账号）本进程就一直用 uid，下次启动才用名字。</li>
 * </ul>
 */
public final class AccountFolderName {
    public static final String DEFAULT = "default";
    static final int MAX_LABEL = 24;

    /** 读取账号显示名（配置页账号列表括号前面的名字）；读不到返回 null。 */
    public interface Source {
        String displayName(String uid);
    }

    /** 目录归属与迁移。 */
    public interface Store {
        /** 名字目录当前属于哪个 uid（标记文件），没有返回 null。 */
        String owner(String folder);

        /** 把旧的 uid 目录改名成名字目录（名字目录还不存在时）。 */
        void migrate(String uid, String folder);

        /** 记下名字目录属于这个 uid。 */
        void claim(String folder, String uid);
    }

    private static volatile Source source;
    private static volatile Store store;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private AccountFolderName() {
    }

    public static void install(Source newSource, Store newStore) {
        source = newSource;
        store = newStore;
        CACHE.clear();
    }

    public static boolean isValidUid(String uid) {
        return uid != null && uid.matches("[A-Za-z0-9_-]{1,128}");
    }

    /** 从文件里读回来的目录名（current_log_user.txt）是否安全：不含路径分隔符等、不是 . / ..，长度受限。 */
    public static boolean isSafeFolder(String name) {
        if (name == null || name.isEmpty() || name.length() > 64 || name.equals(".") || name.equals("..")) {
            return false;
        }
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (!(Character.isLetterOrDigit(cp) || cp == '_' || cp == '-')) {
                return false;
            }
        }
        return true;
    }

    /** 文件名安全化：非字母数字/下划线/连字符替换成下划线，合并连续下划线，去首尾下划线，最长 MAX_LABEL 个字符。 */
    public static String sanitize(String label) {
        if (label == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean lastUnderscore = false;
        int count = 0;
        for (int i = 0; i < label.length() && count < MAX_LABEL; ) {
            int cp = label.codePointAt(i);
            i += Character.charCount(cp);
            boolean ok = Character.isLetterOrDigit(cp) || cp == '-';
            if (ok) {
                out.appendCodePoint(cp);
                lastUnderscore = false;
                count++;
            } else if (!lastUnderscore) {
                out.append('_');
                lastUnderscore = true;
                count++;
            }
        }
        String result = out.toString();
        int start = 0;
        int end = result.length();
        while (start < end && result.charAt(start) == '_') {
            start++;
        }
        while (end > start && result.charAt(end - 1) == '_') {
            end--;
        }
        return result.substring(start, end);
    }

    /** 只取显示用的名字：找不到返回 uid（导出文件名等不涉及目录的地方用，不迁移、不写标记）。 */
    public static String displayLabel(String uid) {
        if (!isValidUid(uid)) {
            return DEFAULT;
        }
        try {
            Source s = source;
            String label = s == null ? "" : sanitize(s.displayName(uid));
            return label.isEmpty() || label.equals(DEFAULT) ? uid : label;
        } catch (Throwable t) {
            return uid;
        }
    }

    /** 账号目录名：名字优先，找不到才用 uid；uid 不合法用 default。 */
    public static String resolve(String uid) {
        if (!isValidUid(uid)) {
            return DEFAULT;
        }
        String cached = CACHE.get(uid);
        if (cached != null) {
            return cached;
        }
        synchronized (AccountFolderName.class) {
            cached = CACHE.get(uid);
            if (cached == null) {
                cached = compute(uid);
                CACHE.put(uid, cached);
            }
            return cached;
        }
    }

    private static String compute(String uid) {
        String label = displayLabel(uid);
        if (label.equals(uid)) {
            return uid;
        }
        Store s = store;
        if (s == null) {
            return label;
        }
        try {
            String folder = label;
            String owner = s.owner(folder);
            if (owner != null && !owner.equals(uid)) {
                folder = label + "-" + uid.substring(Math.max(0, uid.length() - 4));
            }
            s.migrate(uid, folder);
            s.claim(folder, uid);
            return folder;
        } catch (Throwable t) {
            return uid;
        }
    }
}
