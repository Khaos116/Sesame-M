package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONObject;
import java.util.Map;
import io.github.aw1y2z.sesame.entity.KVNode;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestWater {

    private static final String TAG = AntForestWater.class.getSimpleName();
    private final AntForestV2 host;

    AntForestWater(AntForestV2 host) {
        this.host = host;
    }

    void waterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[host.waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        Map<String, Integer> friendMap = host.waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (host.selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (waterCount > 3) {
                waterCount = 3;
            }
            // 只补浇「配置次数 - 当日已浇」的差额，避免部分失败后被重复浇满而超出配置
            int remainCount = waterCount - Status.getWaterFriendToday(uid);
            if (remainCount > 0) {
                try {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryFriendHomePage(uid));
                    TimeUtil.sleep(100);
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        String bizNo = jo.optString("bizNo");
                        int friendWaterEnergy = friendWaterEnergy(uid, waterEnergy);
                        if (friendWaterEnergy <= 0) continue;
                        KVNode<Integer, Boolean> waterCountKVNode = returnFriendWater(uid, bizNo, remainCount, friendWaterEnergy, taskUid);
                        int wateredCount = waterCountKVNode.getKey();
                        if (wateredCount > 0) {
                            Status.waterFriendToday(uid, wateredCount, taskUid);
                            Log.record("好友浇水：成功[" + UserIdMap.getMaskName(uid) + "]，本次" + wateredCount + "次，每次" + friendWaterEnergy + "g");
                        }
                        if (!waterCountKVNode.getValue()) {
                            break;
                        }
                    }
                } catch (TaskCancelledException e) {
                    throw e;
                } catch (Throwable t) {
                    Log.err(TAG, "waterFriendEnergy err:", t);
                }
            }
        }
    }

    KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy) {
        return returnFriendWater(userId, bizNo, count, waterEnergy, null);
    }

    private KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy,
                                                     String configuredTaskUid) {
        if (bizNo == null || bizNo.isEmpty()) {
            return new KVNode<>(0, true);
        }
        int wateredTimes = 0;
        boolean isContinue = true;
        try {
            String s;
            JSONObject jo;
            int energyId = getEnergyId(waterEnergy);
            label:
            for (int waterCount = 1; waterCount <= count; waterCount++) {
                s = AntForestRpcCall.transferEnergy(userId, bizNo, energyId, host.waterFriendEnergySendChat.getValue() ? "Y" : "N");
                TimeUtil.sleep(1500);
                jo = MyUtils.newJSONObject(s);

                String resultCode = jo.optString("resultCode");
                switch (resultCode) {
                    case "SUCCESS":
                        //记录浇水次数
                        Status.wateringFriendToday(userId);
                        Statistics.addData(Statistics.DataType.WATERINGCOUNT, 1);
                        JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
                        int currentEnergy = userBaseInfo != null ? userBaseInfo.optInt("currentEnergy") : 0;
                        Log.forest("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g#剩余能量[" + currentEnergy + "g]");
                        Toast.show("好友浇水🚿给[" + UserIdMap.getShowName(userId) + "]浇" + waterEnergy + "g");
                        wateredTimes++;
                        Statistics.addData(Statistics.DataType.WATERED, waterEnergy);
                        break;
                    case "WATERING_TIMES_LIMIT":
                        Log.forest("好友浇水🚿今日给[" + UserIdMap.getMaskName(userId) + "]浇水已达上限");
                        wateredTimes = 3;
                        break label;
                    case "ENERGY_INSUFFICIENT":
                        // 自己能量不够时后面的好友也必然失败（日报单账号 36 次），本轮停止浇水
                        Log.record("好友浇水🚿能量不足，本轮停止浇水");
                        isContinue = false;
                        break label;
                    case "WATERING_USER_LIMIT":
                        Log.forest("好友浇水🚿给[" + UserIdMap.getMaskName(userId) + "]浇水，" + jo.optString("resultDesc"));
                        wateredTimes = 3;
                        break label;
                    default:
                        // 未知失败不再重发：响应丢失但已生效时，用同一 bizNo 重发会重复扣能量
                        Log.forest("好友浇水🚿" + jo.optString("resultDesc"));
                        Log.i(jo.toString());
                        break label;
                }
            }
        } catch (TaskCancelledException e) {
            // 取消仍保留已确认次数，避免下一轮重复补浇；自动回浇不占配置次数。
            if (configuredTaskUid != null && wateredTimes > 0) {
                Status.waterFriendToday(userId, wateredTimes, configuredTaskUid);
            }
            throw e;
        } catch (Throwable t) {
            Log.err(TAG, "returnFriendWater err:", t);
        }
        return new KVNode<>(wateredTimes, isContinue);
    }

    int getEnergyId(int waterEnergy) {
        if (waterEnergy <= 0) {
            return 0;
        }
        if (waterEnergy >= 66) {
            return 42;
        }
        if (waterEnergy >= 33) {
            return 41;
        }
        if (waterEnergy >= 18) {
            return 40;
        }
        return 39;
    }

    void doubleWaterFriendEnergy() {
        String taskUid = UserIdMap.getCurrentUid();
        int waterEnergy = WaterFriendType.waterEnergy[host.waterFriendType.getValue()];
        if (waterEnergy == 0) {
            return;
        }
        boolean reSet = true;
        Map<String, Integer> friendMap = host.waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
            String uid = friendEntry.getKey();
            if (host.selfId.equals(uid)) {
                continue;
            }
            Integer waterCount = friendEntry.getValue();
            if (waterCount == null || waterCount <= 0) {
                continue;
            }
            if (Status.canWaterFriendToday(uid, 3)) {
                reSet = false;
            }
        }
        if (reSet) {
            for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
                String uid = friendEntry.getKey();
                if (host.selfId.equals(uid)) {
                    continue;
                }
                Integer waterCount = friendEntry.getValue();
                if (waterCount == null || waterCount <= 0) {
                    continue;
                }
                //重置浇水次数
                Status.resetWaterFriendToday(uid, taskUid);
            }
            Log.record("好友浇水🚿今日给好友浇水状态已重置！");
            Status.flagToday("Forest::doubleWaterFriendEnergy");
        }
    }

    /** 是否浇得动水：动作非"不浇水"且选了好友 */
    boolean canWaterFriend() {
        int type = host.waterFriendType.getValue();
        if (type < 0 || type >= WaterFriendType.waterEnergy.length
                || WaterFriendType.waterEnergy[type] == 0) {
            return false;
        }
        Map<String, Integer> friendMap = host.waterFriendList.getValue();
        return friendMap != null && !friendMap.isEmpty();
    }

    int friendWaterEnergy(String uid, int defaultGrams) {
        Integer grams = host.waterFriendGramList.getValue().get(uid);
        if (grams == null || grams == 0) return defaultGrams;
        if (grams == 10 || grams == 18 || grams == 33 || grams == 66) return grams;
        Log.record("好友浇水：独立克数无效[" + grams + "]，请填写10、18、33、66；跳过该好友");
        return 0;
    }
}
