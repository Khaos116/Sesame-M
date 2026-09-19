package io.github.aw1y2z.sesame.model.normal.answerAI;

import static io.github.aw1y2z.sesame.util.JsonUtil.getValueByPath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import okhttp3.*;

/**
 * GenAI帮助类（@author Xiong）
 * <p>
 * Gemini 答题。<b>海外用户正在使用，不能删除</b>（上游 MIUIX-api102 重构为 CustomAI 时删掉了它，
 * 合并时不要跟着删；改用 CustomAI 只是另一个选项，见 {@code AnswerAI.AIType}）。
 */
public class GeminiAI implements AnswerAIInterface {
    private final String TAG = GeminiAI.class.getSimpleName();

    // OkHttpClient 应作为单例共享，复用连接池与线程池，避免每次答题重复创建
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private final String url = "https://api.genai.gd.edu.kg/google";

    private final String token;

    // 私有构造函数，防止外部实例化
    public GeminiAI(String token) {
        if (token != null && !token.isEmpty()) {
            this.token = token;
        } else {
            this.token = "";
        }
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    @Override
    public String getAnswerStr(String text) {
        Response response = null;
        try {
            JSONObject jsonReq = MyUtils.newJSONObject();
            // 针对选择题优化的 Prompt
            String fullPrompt = "只返回正确选项的原文，保留数字、小数点及标点，不要解释。题目：" + text;

            JSONArray contents = new JSONArray();
            contents.put(MyUtils.newJSONObject().put("parts", new JSONArray().put(MyUtils.newJSONObject().put("text", fullPrompt))));
            jsonReq.put("contents", contents);

            // 必须开启 google_search，否则无法回答最新的常识题（如蚂蚁庄园）
            jsonReq.put("tools", new JSONArray().put(MyUtils.newJSONObject().put("google_search", MyUtils.newJSONObject())));

            RequestBody body = RequestBody.create(jsonReq.toString(), MediaType.parse("application/json"));

            String modelName = "gemini-2.5-flash";
            String finalUrl = url + "/v1beta/models/" + modelName + ":generateContent?key=" + token;

            Request request = new Request.Builder().url(finalUrl).post(body).build();
            response = CLIENT.newCall(request).execute();

            if (response.body() != null) {
                String jsonStr = response.body().string();
                JSONObject resObj = MyUtils.newJSONObject(jsonStr);
                String answer = getValueByPath(resObj, "candidates.[0].content.parts.[0].text");

                if (answer != null) {
                    return answer.trim();
                }
            }
        } catch (Exception e) {
            Log.printStackTrace(TAG, e);
            Log.error("Gemini答题出错: " + e.getMessage());
        } finally {
            if (response != null) response.close();
        }
        return "";
    }

    /**
     * 获取答案
     *
     * @param title     问题
     * @param answerList 答案集合
     * @return 空没有获取到
     */
    @Override
    public Integer getAnswer(String title, List<String> answerList) {
        StringBuilder answerStr = new StringBuilder();
        for (String answer : answerList) {
            answerStr.append("[").append(answer).append("]");
        }
        String answerResult = getAnswerStr(title + "\n" + answerStr);
        if (answerResult != null && !answerResult.isEmpty()) {
            for (int i = 0; i < answerList.size(); i++) {
                if (answerResult.trim().equals(answerList.get(i))) return i;
            }
            int matched = -1;
            for (int i = 0, size = answerList.size(); i < size; i++) {
                String option = answerList.get(i);
                if (option != null && !option.isEmpty() && answerResult.contains(option)) {
                    if (matched != -1) return -1;
                    matched = i;
                }
            }
            return matched;
        }
        return -1;
    }
}
