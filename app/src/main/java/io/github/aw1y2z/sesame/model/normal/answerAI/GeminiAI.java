package io.github.aw1y2z.sesame.model.normal.answerAI;

import static io.github.aw1y2z.sesame.util.JsonUtil.getValueByPath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import okhttp3.*;

/**
 * GenAI帮助类
 *
 * @author Xiong
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
            String fullPrompt = "直接给出答案文字，严禁解释，不要标点符号。题目：" + text;

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
                    // 清理所有可能干扰匹配的杂质
                    return answer.trim().replaceAll("[。，.！!？? \"'“”]", "");
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
            for (int i = 0, size = answerList.size(); i < size; i++) {
                if (answerResult.contains(answerList.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
