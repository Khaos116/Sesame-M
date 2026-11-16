package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestDress {

    private static final String TAG = AntForestDress.class.getSimpleName();
    private final AntForestV2 host;

    AntForestDress(AntForestV2 host) {
        this.host = host;
    }

    void dress() {
        String dressDetail = host.dressDetailList.getValue();
        if (dressDetail.isEmpty()) {
            setDressDetail(getDressDetail().toString());
        } else {
            checkDressDetail(dressDetail);
        }
    }

    JSONObject getDressDetail() {
        JSONObject dressDetail = MyUtils.newJSONObject();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            JSONObject indexDressVO = jo.optJSONObject("indexDressVO");
            JSONArray ja = indexDressVO != null ? indexDressVO.optJSONArray("dressDetailList") : null;
            for (int i = 0; ja != null && i < ja.length(); i++) {
                jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String position = jo.optString("position");
                String batchType = jo.optString("batchType");
                if (!position.isEmpty()) dressDetail.put(position, batchType);
            }
        } catch (Throwable th) {
            Log.err(TAG, "getDressDetail err:", th);
        }
        return dressDetail;
    }

    void setDressDetail(String dressDetail) {
        host.dressDetailList.setValue(dressDetail);
        if (ConfigV2.save(UserIdMap.getCurrentUid(), false)) {
            Log.forest("装扮保护🔐皮肤保存,芝麻粒-M将为你持续保护!");
        }
    }

    void removeDressDetail(String position) {
        JSONObject jo = getDressDetail();
        jo.remove(position);
        setDressDetail(jo.toString());
    }

    void checkDressDetail(String dressDetail) {
        String[] positions = {"tree__main", "bg__sky_0", "bg__sky_cloud", "bg__ground_a", "bg__ground_b", "bg__ground_c"};
        try {
            boolean isDressExchanged = false;
            JSONObject jo = MyUtils.newJSONObject(dressDetail);
            for (String position : positions) {
                String batchType = "";
                if (jo.has(position)) {
                    batchType = jo.optString(position);
                }
                if (queryUserDressForBackpack(AntForestV2.dressMap.get(position), batchType)) {
                    isDressExchanged = true;
                }
            }
            if (isDressExchanged) {
                Log.forest("装扮保护🔐皮肤修改,芝麻粒-M已为你自动恢复!");
            }
        } catch (Throwable th) {
            Log.err(TAG, "checkDressDetail err:", th);
        }
    }

    Boolean queryUserDressForBackpack(String positionType, String batchType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listUserDressForBackpack(positionType));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray userHoldDressVOList = jo.optJSONArray("userHoldDressVOList");
            boolean isTakeOff = false;
            for (int i = 0; userHoldDressVOList != null && i < userHoldDressVOList.length(); i++) {
                jo = userHoldDressVOList.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                if (jo.optInt("remainNum", 1) == 0) {
                    if (batchType.equals(jo.optString("batchType"))) {
                        return false;
                    }
                    JSONArray posList = jo.optJSONArray("posList");
                    if (posList == null || posList.length() == 0) {
                        continue;
                    }
                    String position = posList.optString(0);
                    isTakeOff = takeOffDress(jo.optString("dressType"), position);
                } else if (batchType.equals(jo.optString("batchType"))) {
                    return wearDress(jo.optString("dressType"));
                }
            }

            if (!batchType.isEmpty()) {
                removeDressDetail(AntForestV2.dressMap.get(positionType));
                Log.forest("装扮保护🔐皮肤过期,芝麻粒-M已为你恢复默认!");
            }
            return isTakeOff;
        } catch (Throwable th) {
            Log.err(TAG, "queryUserDressForBackpack err:", th);
        }
        return false;
    }

    Boolean wearDress(String dressType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.wearDress(dressType));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "wearDress err:", th);
        }
        return false;
    }

    Boolean takeOffDress(String dressType, String position) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.takeOffDress(dressType, position));
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            Log.err(TAG, "takeOffDress err:", th);
        }
        return false;
    }
}
