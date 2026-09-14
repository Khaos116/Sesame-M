package io.github.aw1y2z.sesame.model.task.readingDada;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.model.normal.answerAI.AnswerAI;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.StringUtil;

/**
 * @author Constanline
 * @since 2023/08/22
 */
public class ReadingDada {
    private static final String TAG = ReadingDada.class.getSimpleName();

    public ModelGroup getGroup() {
        return ModelGroup.STALL;
    }

    public static boolean answerQuestion(JSONObject bizInfo) {
        try {
            String taskJumpUrl = bizInfo.optString("taskJumpUrl");
            if (StringUtil.isEmpty(taskJumpUrl)) {
                taskJumpUrl = bizInfo.optString("targetUrl");
            }
            String activityId = taskJumpUrl.split("activityId%3D")[1].split("%26")[0];
            String outBizId;
            if (taskJumpUrl.contains("outBizId%3D")) {
                outBizId = taskJumpUrl.split("outBizId%3D")[1].split("%26")[0];
            } else {
                outBizId = "";
            }
            String s = ReadingDadaRpcCall.getQuestion(activityId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if ("200".equals(jo.optString("resultCode"))) {
                JSONArray jsonArray = jo.optJSONArray("options");
                if (jsonArray == null || jsonArray.length() == 0) {
                    Log.record("获取问题失败");
                    return false;
                }
                String answer = AnswerAI.getAnswer(jo.optString("title"), JsonUtil.jsonArrayToList(jsonArray));
                if (answer == null || answer.isEmpty()) {
                    answer = jsonArray.optString(0);
                }
                s = ReadingDadaRpcCall.submitAnswer(activityId, outBizId, jo.optString("questionId"), answer);
                jo = MyUtils.newJSONObject(s);
                if ("200".equals(jo.optString("resultCode"))) {
                    Log.record("答题完成");
                    return true;
                } else {
                    Log.record("答题失败");
                }
            } else {
                Log.record("获取问题失败");
            }
        } catch (Throwable e) {
            Log.i(TAG, "answerQuestion err:");
            Log.printStackTrace(TAG, e);
        }
        return false;
    }
}