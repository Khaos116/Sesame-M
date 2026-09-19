package io.github.aw1y2z.sesame.model.normal.answerAI;

import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.TimeUtil;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 OpenAI 兼容接口：接口地址、模型名、令牌、输出长度都由用户在配置页自己填写。
 * <p>
 * 只要求服务端支持 {@code POST <根地址>/chat/completions} + {@code Authorization: Bearer <令牌>}，
 * 请求体 {@code {"model":..,"messages":[{role,content}],"stream":false,"max_tokens":..}}，响应取
 * {@code choices[0].message.content}。通义兼容模式、DeepSeek、硅基流动、自建网关等均适用。
 */
public class CustomAI {

    private static final String TAG = CustomAI.class.getSimpleName();

    /**
     * 单次请求超时。OkHttp 默认读超时只有 10 秒，而大模型首字延迟常见十几秒，
     * 不放宽会导致「配了 AI 却总是超时 → 走兜底答案」。
     */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 180;

    /** 失败重试：总尝试次数与重试间隔 */
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 800L;
    /** 可重试的 HTTP 状态码：限流与网关临时故障 */
    private static final Set<Integer> RETRY_CODES = new HashSet<>(Arrays.asList(429, 502, 503));

    /** 输出长度缺省值与上下限；0 表示不发送该参数（o1/gpt-5 等只认 max_completion_tokens） */
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final int MIN_MAX_TOKENS = 0;
    private static final int MAX_MAX_TOKENS = 8192;

    /** 日志里响应体与 URL 的最大长度 */
    private static final int MAX_LOG_RESPONSE_LENGTH = 500;
    private static final int MAX_LOG_URL_LENGTH = 200;

    /** OpenAI 兼容的对话补全路径：用户只填根地址时由 normalizeUrl 自动补上 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** 数字：整串匹配用于识别"模型只回了一个编号"，查找用于兜底取响应里第一个数字 */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

    /** 复用同一个 OkHttpClient（自带连接池与线程），避免每次请求都新建 */
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();

    private final String url;
    private final String model;
    private final String token;
    private final int maxTokens;

    public CustomAI(String url, String model, String token, Integer maxTokens) {
        this.url = normalizeUrl(url);
        this.model = model == null ? "" : model.trim();
        this.token = token == null ? "" : token.trim();
        int value = maxTokens == null ? DEFAULT_MAX_TOKENS : maxTokens;
        this.maxTokens = Math.max(MIN_MAX_TOKENS, Math.min(MAX_MAX_TOKENS, value));
    }

    /**
     * 接口地址归一化：允许只填根地址（如 {@code https://api.deepseek.com/v1}），自动补
     * {@code /chat/completions}；已经带了该路径的完整地址原样保留。末尾多余的斜杠会去掉。
     */
    private static String normalizeUrl(String raw) {
        String result = raw == null ? "" : raw.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty() || result.endsWith(CHAT_COMPLETIONS_PATH)) {
            return result;
        }
        return result + CHAT_COMPLETIONS_PATH;
    }

    /** 接口地址、模型名、令牌三项都填了才算配置完成 */
    public boolean isConfigured() {
        return !url.isEmpty() && !model.isEmpty() && !token.isEmpty();
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果，未配置或请求失败时返回空串
     */
    public String getAnswerStr(String text) {
        if (!isConfigured()) {
            // 防御：正常路径下 AnswerAI 已在 boot 时判过配置是否齐全并打过日志，这里不再重复记
            return "";
        }
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String result = requestOnce(text);
            if (result != null) {
                return result;
            }
            if (attempt < MAX_ATTEMPTS) {
                Log.record("AI🧠请求失败，第" + attempt + "次重试");
                TimeUtil.sleep(RETRY_DELAY_MS);
            }
        }
        Log.other("自定义AI请求最终失败（已尝试" + MAX_ATTEMPTS + "次）：" + safeUrl());
        return "";
    }

    /**
     * 单次请求。
     *
     * @return 成功返回模型内容（可能是空串）；属于可重试的失败返回 null
     */
    private String requestOnce(String text) {
        try {
            JSONArray messageArray = new JSONArray();
            messageArray.put(new JSONObject().put("role", "user").put("content", text));
            JSONObject bodyObject = new JSONObject();
            bodyObject.put("model", model);
            bodyObject.put("messages", messageArray);
            bodyObject.put("stream", false);
            if (maxTokens > 0) {
                bodyObject.put("max_tokens", maxTokens);
            }
            String contentType = "application/json";
            RequestBody body = RequestBody.create(bodyObject.toString(), MediaType.parse(contentType));
            Request request = new Request.Builder()
                    .url(url)
                    .method("POST", body)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", contentType)
                    .build();
            // try-with-resources：成功、提前 return、异常三条路径都会关闭 Response，连接归还连接池
            try (Response response = CLIENT.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    return null;
                }
                String json = responseBody.string();
                if (!response.isSuccessful()) {
                    Log.other("自定义AI请求失败[" + response.code() + "]：" + safeUrl());
                    Log.i("自定义AI接口异常：" + safeLog(json));
                    // 限流、网关临时故障可重试；其余（如 401 令牌错、404 地址错）重试无意义
                    return RETRY_CODES.contains(response.code()) ? null : "";
                }
                JSONObject jsonObject = MyUtils.newJSONObject(json);
                String content = JsonUtil.getValueByPath(jsonObject, "choices.[0].message.content");
                return content == null ? "" : content;
            }
        } catch (IOException e) {
            // 超时、连接中断等 IO 异常可重试
            Log.err(TAG, "requestOnce io err:", e);
            return null;
        } catch (Throwable t) {
            Log.err(TAG, "requestOnce err:", t);
            return "";
        }
    }

    /**
     * 获取答案
     *
     * @param title      问题
     * @param answerList 答案集合
     * @return 选项下标，未获取到返回 -1
     */
    public Integer getAnswer(String title, List<String> answerList) {
        String answerResult = getAnswerStr(buildQuestionPrompt(title, answerList));
        Integer index = parseAnswerIndex(answerResult, answerList);
        if (index != null) {
            return index;
        }
        if (answerResult != null && !answerResult.isEmpty()) {
            Log.record("AI🧠回答，返回数据：" + safeLog(answerResult));
        }
        return -1;
    }

    /** 组装单选题提问：要求只回一个 JSON，键名只是建议，解析侧并不依赖它 */
    private static String buildQuestionPrompt(String title, List<String> answerList) {
        StringBuilder optionText = new StringBuilder();
        for (int i = 0, size = answerList.size(); i < size; i++) {
            optionText.append(i + 1).append(".[").append(answerList.get(i)).append("]\n");
        }
        return "请回答下面这道单选题。\n"
                + "只输出一个 JSON，例如：{\"choice\":1}，其中 choice 是正确选项的编号（从 1 开始）。\n"
                + "除这个 JSON 之外不要输出任何其它内容。\n\n"
                + "题目：" + title + "\n"
                + "选项：\n"
                + optionText;
    }

    /**
     * 解析选项序号。
     * <p>
     * 刻意不依赖任何特定键名：JSON 对象里的每个值都试一遍——是数字就当编号，
     * 是文本就和选项做归一化比对。这样模型回 {@code index} / {@code answer} / {@code choice}
     * 或别的键都能接住，不会被绑死在某种返回约定上。
     * <p>
     * 依次尝试：① JSON 里所有键的数字值 → ② JSON 里所有键的文本值（归一化比对选项）
     * → ③ 整段响应与选项做归一化比对 → ④ 整段响应里最后一个合法数字。
     * <p>
     * 数字先于文本是必须的：模型常同时回 {@code {"answer":1,"explanation":"答案是北京…"}}，
     * 若按键序先撞上 explanation，文本比对会误命中"北京"，与真正的编号冲突。
     */
    private static Integer parseAnswerIndex(String answerResult, List<String> answerList) {
        if (answerResult == null) {
            return null;
        }
        String text = answerResult.trim();
        if (text.isEmpty() || answerList.isEmpty()) {
            return null;
        }
        int size = answerList.size();

        // 正常只有一个 JSON；模型若把提示词里的示例 {"choice":1} 也回显出来就会有两个，
        // 此时取最后一个能给出有效编号的（示例在前、真答案在后）
        Integer jsonIndex = null;
        for (JSONObject json : extractJsonObjects(text)) {
            Integer index = parseJsonNumbers(json, size);
            if (index == null) {
                index = parseJsonTexts(json, answerList);
            }
            if (index != null) {
                jsonIndex = index;
            }
        }
        if (jsonIndex != null) {
            return jsonIndex;
        }

        Integer index = toIndexByText(text, size);
        if (index == null) {
            index = matchByText(text, answerList);
        }
        if (index != null) {
            return index;
        }

        return matchLastNumber(text, size);
    }

    /**
     * 兜底：取响应里最后一个合法数字。
     * <p>
     * 散文式回答里编号通常在末尾（"第2题的答案是第1项"→1、"答案是2（第2项）"→2），
     * 取首个容易拿到题干里的题号反而选错；越界的数字（"答案3（共4项）"里的 4）会被跳过。
     */
    private static Integer matchLastNumber(String text, int size) {
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        Integer result = null;
        while (matcher.find()) {
            Integer index = toIndexByText(matcher.group(), size);
            if (index != null) {
                result = index;
            }
        }
        return result;
    }

    /** 扫 JSON 里所有键的数字值（含纯数字字符串），按 1 基序号取第一个合法的 */
    private static Integer parseJsonNumbers(JSONObject json, int size) {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            Object value = json.opt(keys.next());
            if (value instanceof Number) {
                Integer index = toIndex(((Number) value).intValue(), size);
                if (index != null) {
                    return index;
                }
            } else if (value instanceof String) {
                Integer index = toIndexByText(((String) value).trim(), size);
                if (index != null) {
                    return index;
                }
            }
        }
        return null;
    }

    /** 扫 JSON 里所有键的文本值，与选项做归一化比对 */
    private static Integer parseJsonTexts(JSONObject json, List<String> answerList) {
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            Object value = json.opt(keys.next());
            if (value instanceof String) {
                Integer index = matchByText((String) value, answerList);
                if (index != null) {
                    return index;
                }
            }
        }
        return null;
    }

    /** 数字序号（1 基）转下标，越界返回 null */
    private static Integer toIndex(int number, int size) {
        int index = number - 1;
        return (index >= 0 && index < size) ? index : null;
    }

    /** 字符串形式的序号：必须是纯数字，越界返回 null */
    private static Integer toIndexByText(String text, int size) {
        if (text == null || text.isEmpty() || !NUMBER_PATTERN.matcher(text).matches()) {
            return null;
        }
        try {
            return toIndex(Integer.parseInt(text), size);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 与选项做归一化比对：先去标点空白再比，能容忍模型带「答案：」「。」这类前后缀。
     * <p>
     * 完全相等优先；退化为互相包含时，若同时命中多个选项则判为不确定、放弃（交给上层兜底），
     * 避免"北京"误命中"北京市"这类情况选错。
     */
    private static Integer matchByText(String value, List<String> answerList) {
        String target = normalize(value);
        if (target.isEmpty()) {
            return null;
        }
        int size = answerList.size();
        for (int i = 0; i < size; i++) {
            if (target.equals(normalize(answerList.get(i)))) {
                return i;
            }
        }
        Integer hit = null;
        for (int i = 0; i < size; i++) {
            String option = normalize(answerList.get(i));
            if (option.isEmpty()) {
                continue;
            }
            if (target.contains(option) || option.contains(target)) {
                if (hit != null) {
                    return null;
                }
                hit = i;
            }
        }
        return hit;
    }

    /** 归一化：全角转半角、只保留字母与数字、统一小写，最大限度消除标点与空白的干扰 */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0, len = text.length(); i < len; i++) {
            char c = text.charAt(i);
            if (c >= 'Ａ' && c <= 'Ｚ') {
                // 全角大写字母 A-Z
                c -= 0xFEE0;
            } else if (c >= 'ａ' && c <= 'ｚ') {
                // 全角小写字母 a-z
                c -= 0xFEE0;
            } else if (c >= '０' && c <= '９') {
                // 全角数字 0-9
                c -= 0xFEE0;
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /**
     * 从响应里提取所有能解析成功的 JSON 对象：逐个 '{' 起点往后找配对的右括号，解析成功就收集。
     * 逐字符配对所以裸 JSON、```json 围栏、以及"示例 JSON + 答案 JSON"都能正确切分。
     * <p>
     * 正常只有一个；模型回显提示词里的示例 JSON 时会返回多个，选哪一个由调用方决定。
     */
    private static List<JSONObject> extractJsonObjects(String text) {
        List<JSONObject> result = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int start = text.indexOf('{', searchFrom);
            if (start < 0) {
                break;
            }
            int end = findObjectEnd(text, start);
            if (end > start) {
                JSONObject json = parseJsonOrNull(text.substring(start, end + 1));
                if (json != null) {
                    result.add(json);
                }
            }
            searchFrom = start + 1;
        }
        return result;
    }

    /** 从 start 处的 '{' 往右找配对的 '}'，跳过字符串字面量里的括号；找不到返回 -1 */
    private static int findObjectEnd(String text, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start, len = text.length(); i < len; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static JSONObject parseJsonOrNull(String text) {
        // MyUtils 对无效输入返回空对象；这里保留“解析失败返回 null”的语义，空对象同样视为失败
        JSONObject parsed = MyUtils.newJSONObject(text);
        return parsed.length() == 0 ? null : parsed;
    }

    /** 日志用：抹掉令牌并截断，避免密钥与超长响应污染日志 */
    private String safeLog(String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (!token.isEmpty()) {
            result = result.replace(token, "***");
        }
        if (result.length() > MAX_LOG_RESPONSE_LENGTH) {
            result = result.substring(0, MAX_LOG_RESPONSE_LENGTH) + "...";
        }
        return result;
    }

    /** 日志用：URL 去掉查询串并截断（部分网关会把令牌放在 query 里） */
    private String safeUrl() {
        String result = url;
        int queryIndex = result.indexOf('?');
        if (queryIndex >= 0) {
            result = result.substring(0, queryIndex);
        }
        if (result.length() > MAX_LOG_URL_LENGTH) {
            result = result.substring(0, MAX_LOG_URL_LENGTH) + "...";
        }
        return result;
    }

}
