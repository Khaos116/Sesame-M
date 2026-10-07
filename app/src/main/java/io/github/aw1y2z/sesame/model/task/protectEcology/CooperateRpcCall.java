package io.github.aw1y2z.sesame.model.task.protectEcology;

import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class CooperateRpcCall {
    private static final String VERSION = "20230501";

    public static String queryUserCooperatePlantList() {
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryUserCooperatePlantList", "[{}]");
    }

    public static String queryCooperatePlant(String cooperationId) throws JSONException {
        String args = new JSONArray().put(MyUtils.newJSONObject().put("cooperationId", cooperationId)).toString();
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryCooperatePlant", args);
    }

    public static String cooperateWater(String userId, String cooperationId, int energyCount) {
        String bizNo = userId + "_" + cooperationId + "_" + System.currentTimeMillis();
        String args =
                "[{\"bizNo\":\"" + bizNo + "\",\"cooperationId\":\"" + cooperationId + "\",\"energyCount\":" + energyCount + "}]";
        return ApplicationHook.requestString("alipay.antmember.forest.h5.cooperateWater", args);
    }

    /**
     * 获取合种浇水量排行
     *
     * @param bizType       参数：D/A,“D”为查询当天，“A”为查询所有
     * @param cooperationId 合种ID
     * @return requestString
     */
    public static String queryCooperateRank(String bizType, String cooperationId) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("bizType", bizType).put("cooperationId", cooperationId)
                .put("source", "chInfo_ch_url-https://render.alipay.com/p/yuyan/180020010001247580/home.html");
        return ApplicationHook.requestString("alipay.antmember.forest.h5.queryCooperateRank",
                new JSONArray().put(args).toString());

    }

    public static String sendCooperateBeckon(String userId, String cooperationId) throws JSONException, java.io.UnsupportedEncodingException {
        String id = java.net.URLEncoder.encode(cooperationId, "UTF-8");
        String url = java.net.URLEncoder.encode("/www/cooperation/index.htm?cooperationId=" + id + "&sourceName=card", "UTF-8");
        JSONObject args = MyUtils.newJSONObject().put("bizImage", "https://gw.alipayobjects.com/zos/rmsportal/gzYPfxdAxLrkzFUeVkiY.jpg")
                .put("link", "alipays://platformapi/startapp?appId=66666886&url=" + url)
                .put("midTitle", "快来给我们的树苗浇水，让它快快长大。")
                .put("noticeLink", "alipays://platformapi/startapp?appId=60000002&url=https%3A%2F%2Frender.alipay.com%2Fp%2Fc%2F17ussbd8vtfg%2Fmessage.html%3FsourceName%3Dcard&showOptionMenu=NO&transparentTitle=NO")
                .put("topTitle", "树苗需要你的呵护")
                .put("source", "chInfo_ch_url-https://render.alipay.com/p/yuyan/180020010001247580/home.html")
                .put("cooperationId", cooperationId).put("userId", userId);
        return ApplicationHook.requestString("alipay.antmember.forest.h5.sendCooperateBeckon", new JSONArray().put(args).toString());

    }
}
