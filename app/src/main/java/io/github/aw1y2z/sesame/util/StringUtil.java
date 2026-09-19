package io.github.aw1y2z.sesame.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

public class StringUtil {
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String collectionJoinString(CharSequence conjunction, Collection<?> collection) {
        if (!collection.isEmpty()) {
            StringBuilder b = new StringBuilder();
            Iterator<?> iterator = collection.iterator();
            b.append(toStringOrEmpty(iterator.next()));
            while (iterator.hasNext()) {
                b.append(conjunction).append(toStringOrEmpty(iterator.next()));
            }
            return b.toString();
        }
        return "";
    }

    public static String arrayJoinString(CharSequence conjunction, Object... array) {
        int length = array.length;
        if (length > 0) {
            StringBuilder b = new StringBuilder();
            b.append(toStringOrEmpty(array[0]));
            for (int i = 1; i < length; i++) {
                b.append(conjunction).append(toStringOrEmpty(array[i]));
            }
            return b.toString();
        }
        return "";
    }

    public static String arrayToString(Object... array) {
        return arrayJoinString(",", array);
    }

    private static String toStringOrEmpty(Object obj) {
        return Objects.toString(obj, "");
    }

    public static String padLeft(int str, int totalWidth, char padChar) {
        return padLeft(String.valueOf(str), totalWidth, padChar);
    }

    public static String padRight(int str, int totalWidth, char padChar) {
        return padRight(String.valueOf(str), totalWidth, padChar);
    }

    public static String padLeft(String str, int totalWidth, char padChar) {
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < totalWidth) {
            sb.insert(0, padChar);
        }
        return sb.toString();
    }

    public static String padRight(String str, int totalWidth, char padChar) {
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < totalWidth) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    public static String getSubString(String text, String left, String right) {
        int leftIndex = isEmpty(left) ? 0 : text.indexOf(left);
        if (leftIndex == -1) {
            return "";
        } else if (!isEmpty(left)) {
            leftIndex += left.length();
        }
        int rightIndex = isEmpty(right) ? text.length() : text.indexOf(right, leftIndex);
        if (rightIndex == -1) {
            return "";
        }
        return text.substring(leftIndex, rightIndex);
    }

    /**
     * 截断超长文本（用于日志）：超过 maxLength 时保留前 maxLength 字并附上总字数。
     * <p>
     * 不做空白压缩，避免把 JSON、多行文案挤成一行而丢失结构；要压缩空白请先自行处理。
     * maxLength 非正数时原样返回，防止 substring 越界。
     *
     * @param text      原文本；为 null 时返回空串
     * @param maxLength 保留的最大字数
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "…(共" + text.length() + "字)";
    }

    /**
     * 剥掉标题末尾的 "(n/N)" 次数后缀，如「XX(2/10)」→「XX」。
     * <p>权限类任务上报时标题会被拼上次数后缀，而黑名单检查用的是纯标题；
     * 不归一化会导致写进黑名单的键永远匹配不上、拉黑失效。
     *
     * @param text 原标题；为 null 时返回 null
     */
    public static String stripCountSuffix(String text) {
        return text == null ? null : text.replaceAll("\\(\\d+/\\d+\\)$", "");
    }

    /**
     * 解析整数：null、空串、非数字（含溢出）都返回 null，不抛异常。
     * <p>服务端文案变化很常见，直接 Integer.parseInt 会中断整个模块。
     * <p>返回 null 而不是默认值，调用方才能区分「解析失败」与「解析出来的值本身」。
     */
    public static Integer parseIntOrNull(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 转义 JSON 字符串里的特殊字符（配合手工拼请求体使用）。
     * <p>请求体由各 RpcCall 手写拼接、桥接层原样透传，服务端文案或 AI 回答里只要出现
     * 引号、反斜杠或换行，拼出来的就不是合法 JSON，整次调用直接失败；拼入前统一走这里。
     * <p>只转义必要字符（引号、反斜杠、控制字符），不改动其它内容与普通字符。
     */
    public static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

}
