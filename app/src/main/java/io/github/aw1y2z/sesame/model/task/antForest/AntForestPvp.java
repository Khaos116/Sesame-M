package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Statistics;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestPvp {

    private static final String TAG = AntForestPvp.class.getSimpleName();
    private final AntForestV2 host;

    AntForestPvp(AntForestV2 host) {
        this.host = host;
    }

    static boolean updateUserConfigEnergyPvp() {
        try {
            String Str = AntForestRpcCall.queryUserTag();
            JSONObject Jo = MyUtils.newJSONObject(Str);
            if (!MessageUtil.checkResultCode(TAG, Jo)) {
                Log.record("queryUserTag 查询1V1状态返回异常");
                return false;
            }
            JSONObject tagMap = Jo.optJSONObject("tagMap");
            if (tagMap != null) {
                if (!tagMap.has("energyPvp")) {
                    Log.record("查询1V1无状态返回[无法参加]若出现标识立马为大人开启");
                    return false;
                }
                String energyPvp = tagMap.optString("energyPvp");
                if (energyPvp.equals("Y")) {
                    return true;
                }
            }
            //自动开启挑战
            String updateStr = AntForestRpcCall.updateUserConfigEnergyPvp(true);
            JSONObject updateJo = MyUtils.newJSONObject(updateStr);
            if (!MessageUtil.checkResultCode(TAG, updateJo)) {
                Log.record("updateUserConfigEnergyPvp 返回异常");
            } else {
                Log.forest("比赛情况🆚1V1能量挑战成功开启");
                return true;
            }
            return false;
        } catch (Throwable th) {
            Log.err(TAG, "updateUserConfigEnergyPvp err:", th);
        }
        return false;
    }

    static void queryPvpHomeInfo() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPvpHomeInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject currentEnergyPvpBattleRecord = jo.optJSONObject("currentEnergyPvpBattleRecord");
            if (currentEnergyPvpBattleRecord != null) {
                //获取1v1比赛情况
                String attackerDisplayName = currentEnergyPvpBattleRecord.optString("attackerDisplayName");
                int attackerEnergy = currentEnergyPvpBattleRecord.optInt("attackerEnergy");
                int attackerWinCount = currentEnergyPvpBattleRecord.optInt("attackerWinCount");
                String battleType = currentEnergyPvpBattleRecord.optString("battleType");
                String defenderDisplayName = currentEnergyPvpBattleRecord.optString("defenderDisplayName");
                int defenderEnergy = currentEnergyPvpBattleRecord.optInt("defenderEnergy");
                int defenderWinCount = currentEnergyPvpBattleRecord.optInt("defenderWinCount");
                String battleStatus = currentEnergyPvpBattleRecord.optString("battleStatus");
                Log.record("比赛情况🆚" + battleType + "赛" + battleStatus + "[" + attackerDisplayName + "]" + attackerWinCount + "胜(" + attackerEnergy + "g)VS[" + defenderDisplayName + "]" + attackerWinCount + "胜(" + defenderEnergy + "g)");
                //领取奖励
                if (currentEnergyPvpBattleRecord.optBoolean("hasReward")) {
                    jo = MyUtils.newJSONObject(AntForestRpcCall.receivePvpRewards());
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray receivedRewards = jo.optJSONArray("receivedRewards");
                    for (int i = 0; receivedRewards != null && i < receivedRewards.length(); i++) {
                        JSONObject reward = receivedRewards.optJSONObject(i);
                        if (reward == null) {
                            continue;
                        }
                        String rewardName = reward.optString("rewardName");
                        String rewardType = reward.optString("rewardType");
                        if ("energy".equals(rewardType)) {
                            int energy = reward.optInt("energy");
                            Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } else {
                            Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                        }
                    }

                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "queryPvpHomeInfo err:", th);
        }

    }

    static void receivePvpRewards() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.checkRewardqueryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject combineHandlerVOMap = jo.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap != null) {
                JSONObject energyPvpInfo = combineHandlerVOMap.optJSONObject("energyPvpInfo");
                if (energyPvpInfo != null) {
                    //领取奖励
                    if (energyPvpInfo.optBoolean("hasReward")) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.receivePvpRewards());
                        if (!MessageUtil.checkResultCode(TAG, jo)) {
                            return;
                        }
                        JSONArray receivedRewards = jo.optJSONArray("receivedRewards");
                        if (receivedRewards != null) {
                            for (int i = 0; i < receivedRewards.length(); i++) {
                                JSONObject reward = receivedRewards.optJSONObject(i);
                                if (reward == null) {
                                    continue;
                                }
                                String rewardName = reward.optString("rewardName");
                                String rewardType = reward.optString("rewardType");
                                if ("energy".equals(rewardType)) {
                                    int energy = reward.optInt("energy");
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                    Statistics.addData(Statistics.DataType.COLLECTED, energy);
                                } else {
                                    Log.forest("领取奖励🎖️1V1[" + rewardName + "]");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "receivePvpRewards err:", th);
        }
    }
}
