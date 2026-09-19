package io.github.aw1y2z.sesame.model.normal.answerAI;

import io.github.aw1y2z.sesame.util.MyUtils;

import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;


public class TongyiAI implements AnswerAIInterface {

    private final String TAG = TongyiAI.class.getSimpleName();

    private final String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /** 复用同一个 OkHttpClient（自带连接池与线程），避免每次请求都新建 */
    private static final OkHttpClient CLIENT = new OkHttpClient();

    private final String token;

    public TongyiAI(String token) {
        if (token != null && !token.isEmpty()) {
            this.token = token;
        } else {
            this.token = "";
        }
        /*if (cUrl != null && !cUrl.isEmpty()) {
            url = cUrl.trim().replaceAll("/$", "");
        }*/
    }

    /**
     * 获取AI回答结果
     *
     * @param text 问题内容
     * @return AI回答结果
     */
    @Override
    public String getAnswerStr(String text) {
        String result = "";
        try {
            JSONObject contentObject = new JSONObject();
            contentObject.put("role", "user");
            contentObject.put("content", text);
            JSONArray messageArray = new JSONArray();
            messageArray.put(contentObject);
            JSONObject bodyObject = new JSONObject();
            bodyObject.put("model", "qwen-turbo");
            bodyObject.put("messages", messageArray);
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
                    return result;
                }
                String json = responseBody.string();
                if (!response.isSuccessful()) {
                    Log.other("Tongyi请求失败");
                    Log.i("Tongyi接口异常：" + json);
                    return result;
                }
                JSONObject jsonObject = MyUtils.newJSONObject(json);
                result = JsonUtil.getValueByPath(jsonObject, "choices.[0].message.content");
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return result;
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
        int size = answerList.size();
        StringBuilder answerStr = new StringBuilder();
        for (int i = 0; i < size; i++) {
            answerStr.append(i + 1).append(".[").append(answerList.get(i)).append("]\n");
        }
        String answerResult = getAnswerStr("问题：" + title + "\n\n" + "答案列表：\n\n" + answerStr + "\n\n" + "请只返回答案列表中的序号");
        if (answerResult != null && !answerResult.isEmpty()) {
            try {
                int index = Integer.parseInt(answerResult) - 1;
                if (index >= 0 && index < size) {
                    return index;
                }
            } catch (Exception e) {
                Log.record("AI🧠回答，返回数据：" + answerResult);
            }
            for (int i = 0; i < size; i++) {
                if (answerResult.contains(answerList.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

}
