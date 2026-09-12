package io.github.aw1y2z.sesame.util;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * 通用 RPC 响应成功判定：success/isSuccess/resultCode/memo 任一命中即视为成功，
 * 对"人数过多""小鸡睡觉"等已知的非错误状态静默跳过（不打印错误日志）。
 */
public class ResChecker {
    private static final String TAG = ResChecker.class.getSimpleName();

    private static boolean core(String TAG, JSONObject jo) {
        try {
            if (jo.optBoolean("success") || jo.optBoolean("isSuccess")) {
                return true;
            }
            Object resCode = jo.opt("resultCode");
            if (resCode != null) {
                if (resCode instanceof Integer && (Integer) resCode == 200) {
                    return true;
                } else if (resCode instanceof String &&
                        Pattern.matches("(?i)SUCCESS|100", (String) resCode)) {
                    return true;
                }
            }
            if ("SUCCESS".equalsIgnoreCase(jo.optString("memo", ""))) {
                return true;
            }

            String resultDesc = jo.optString("resultDesc", "");
            String memo = jo.optString("memo", "");
            String desc = jo.optString("desc", "");
            String resultCode = jo.optString("resultCode", "");

            // 已知的非错误系统状态，忽略但不打印错误日志
            String[] ignoreKeywords = {
                    "当前参与人数过多", "请稍后再试", "手速太快", "频繁", "操作过于频繁",
                    "我的小鸡在睡觉中", "小鸡在睡觉", "无法操作", "有人抢在你",
                    "饲料槽已满", "当日达到上限", "适可而止", "不支持rpc完成的任务", "不支持rpc调用", "任务全局配置不存在",
                    "庄园的小鸡太多了", "同一好友新村，只能摆一个小摊哦", "今日助力次数已用完", "收摊成功",
            };
            for (String keyword : ignoreKeywords) {
                if (resultDesc.contains(keyword) || memo.contains(keyword) || desc.contains(keyword)) {
                    return false;
                }
            }
            if ("I07".equals(resultCode) || "ILLEGAL_ARGUMENT".equals(resultCode) || "I09".equals(resultCode)) {
                return false;
            }
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String callerInfo = getString(stackTrace);
            Log.error(TAG, "Check failed: [来源: " + callerInfo + "] " + jo);
            return false;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, "Error checking JSON success:", t);
            return false;
        }
    }

    @NonNull
    private static String getString(StackTraceElement[] stackTrace) {
        StringBuilder callerInfo = new StringBuilder();
        int foundCount = 0;
        final int MAX_STACK_DEPTH = 4;
        final String PROJECT_PACKAGE = "io.github.aw1y2z.sesame";

        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.startsWith(PROJECT_PACKAGE) && !className.contains("ResChecker")) {
                String relativeClassName = className.substring(PROJECT_PACKAGE.length() + 1);
                if (foundCount > 0) {
                    callerInfo.append(" <- ");
                }
                callerInfo.append(relativeClassName)
                        .append(".")
                        .append(element.getMethodName())
                        .append(":")
                        .append(element.getLineNumber());

                foundCount++;
                if (foundCount >= MAX_STACK_DEPTH) {
                    break;
                }
            }
        }

        return callerInfo.toString();
    }

    public static boolean checkRes(String TAG, JSONObject jo) {
        return core(TAG, jo);
    }

    public static boolean checkRes(String TAG, String jsonStr) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(jsonStr);
        return checkRes(TAG, jo);
    }
}
