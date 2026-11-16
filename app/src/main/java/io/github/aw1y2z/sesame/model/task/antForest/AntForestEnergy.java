package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.CollectEnergyEntity;
import io.github.aw1y2z.sesame.entity.FriendWatch;
import io.github.aw1y2z.sesame.entity.RpcEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.Toast;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RandomUtil;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestEnergy {

    private static final String TAG = AntForestEnergy.class.getSimpleName();
    private final AntForestV2 host;

    AntForestEnergy(AntForestV2 host) {
        this.host = host;
    }

    void ForestEnergyInfo() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray bubbles = jo.optJSONArray("bubbles");
            int bubblesNumber = bubbles != null ? bubbles.length() : 0;

            JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
            if (userBaseInfo == null) {
                return;
            }
            int currentEnergy = userBaseInfo.optInt("currentEnergy", 0);
            int totalCertCount = userBaseInfo.optInt("totalCertCount", 0);
            JSONObject userVitalityInfo = jo.optJSONObject("userVitalityInfo");
            if (userVitalityInfo == null) {
                return;
            }
            int totalVitalityAmount = userVitalityInfo.optInt("totalVitalityAmount", 0);

            jo = MyUtils.newJSONObject(AntForestRpcCall.queryDynamicsIndex());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject todayEnergySummary = jo.optJSONObject("todayEnergySummary");
            if (todayEnergySummary == null) {
                return;
            }
            int obtainTotal = todayEnergySummary.optInt("obtainTotal", 0);
            int robbedTotal = todayEnergySummary.optInt("robbedTotal", 0);

            //获取能量日榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int dayenergySummation = myself.optInt("energySummation", 0);
            int dayrank = myself.optInt("rank", 0);

            //获取能量周榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int weekenergySummation = myself.optInt("energySummation", 0);
            int weekrank = myself.optInt("rank", 0);

            //获取能量总榜top
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("energyRank", "total"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            myself = jo.optJSONObject("myself");
            if (myself == null) {
                return;
            }
            int totalenergySummation = myself.optInt("energySummation", 0);
            int totalrank = myself.optInt("rank", 0);

            //获取偷我日榜top
            String dayenergySummationtop3 = "偷我日榜top3:";
            String userId;
            int energySummation;
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "day"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray friendRankings = jo.optJSONArray("friendRanking");
            for (int i = 0; friendRankings != null && i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.optJSONObject(i);
                if (friendRanking == null) {
                    continue;
                }
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                dayenergySummationtop3 = dayenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }

            //获取偷我周榜top
            String weekenergySummationtop3 = "偷我周榜top3:";
            jo = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyRanking("robRank", "week"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            friendRankings = jo.optJSONArray("friendRanking");
            for (int i = 0; friendRankings != null && i < (Math.max(friendRankings.length(), 3)); i++) {
                JSONObject friendRanking = friendRankings.optJSONObject(i);
                if (friendRanking == null) {
                    continue;
                }
                energySummation = friendRanking.optInt("energySummation", 0);
                if (energySummation == 0) {
                    break;
                }
                userId = friendRanking.optString("userId", null);
                weekenergySummationtop3 = weekenergySummationtop3 + "[" + UserIdMap.getShowName(userId) + "]" + energySummation + "g;";
            }
            String ForestInfo = "森林榜单🌳收取" + obtainTotal + "g;被收" + robbedTotal + "g;能量球" + bubblesNumber + "个;活力值" + totalVitalityAmount + ";当前能量" + currentEnergy + "g;证书" + totalCertCount + ";😡" + dayenergySummationtop3 + weekenergySummationtop3 + "😁日榜第" + dayrank + "名:" + dayenergySummation + "g;周榜第" + weekrank + "名:" + weekenergySummation + "g;总榜第" + totalrank + "名:" + totalenergySummation + "g;";
            Log.record(ForestInfo);

        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "ForestEnergyInfo err:", th);
        }

    }

    void collectPKEnergy() {
        try {
            JSONObject pkObject = MyUtils.newJSONObject(AntForestRpcCall.queryTopEnergyChallengeRanking());
            if (!MessageUtil.checkResultCode(TAG + "获取PK排行榜失败:", pkObject)) {
                Log.error("获取PK排行榜失败: " + RpcRequestGuard.errorMessage(pkObject));
            } else {
                if (!"JOIN".equals(pkObject.optString("rankMemberStatus"))) {
                    Log.record("未加入PK排行榜");
                    return;
                }
                collectFriendsEnergy(pkObject, "PK");
                //继续处理靠后的PK好友
                JSONArray totalData = pkObject.optJSONArray("totalData");
                if (totalData == null || totalData.length() == 0) {
                    Log.record("PK好友排行榜为空，跳过");
                    return;
                }
                List<String> pkIdList = new ArrayList<>();
                for (int pos = 20; pos < totalData.length(); pos++) {
                    JSONObject pkFriend = totalData.optJSONObject(pos);
                    if (pkFriend == null) {
                        continue;
                    }
                    String userId = pkFriend.optString("userId");
                    if (Objects.equals(userId, host.selfId)) {
                        continue; //如果是自己则跳过
                    }
                    pkIdList.add(userId);
                    if (pkIdList.size() == 20) {
                        collectFriendsEnergy(pkIdList, "PK");
                        pkIdList.clear();
                    }
                }
                if (!pkIdList.isEmpty()) {
                    collectFriendsEnergy(pkIdList, "PK");
                }
                Log.forest("收取PK能量🧾完成！");
            }
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(TAG, e);
        }
    }

    void notifyMain() {
        if (host.taskCount.decrementAndGet() < 1) {
            synchronized (host) {
                host.notifyAll();
            }
        }
    }

    JSONObject querySelfHome() {
        if (isRevivedSelfQuietTime()) return null;
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) return null;
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.optLong("now");
            if (userHomeObject.opt("now") instanceof Number && serverTime > 0L) {
                AntForestV2.offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            }
            //兼容组队模式
            if (AntForestTeam.isTeam(userHomeObject)) {
                JSONObject teamHomeResult = userHomeObject.optJSONObject("teamHomeResult");
                JSONObject mainMember = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
                //取出组队模式下的selfHomeObject
                if (mainMember != null) {
                    Iterator<String> keyIterator = mainMember.keys();
                    while (keyIterator.hasNext()) {
                        String key = keyIterator.next();
                        Object value = mainMember.opt(key);
                        //将道具卡详情存为一般森林主页格式，以便统一解析
                        if (key.equals("usingUserProps")) {
                            key = "usingUserPropsNew";
                        }
                        // 核心方法：put()
                        // 效果：存在该 key 则覆盖原值，不存在则新增键值对
                        userHomeObject.put(key, value);
                    }
                }
                //userHomeObject = teamHomeResult != null ? teamHomeResult.optJSONObject("mainMember") : null;
            }
        } catch (Throwable t) {
            if (t instanceof io.github.aw1y2z.sesame.util.TaskCancelledException) throw (io.github.aw1y2z.sesame.util.TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    JSONObject queryFriendHome(String userId) {
        JSONObject userHomeObject = null;
        try {
            long start = System.currentTimeMillis();
            userHomeObject = MyUtils.newJSONObject(AntForestRpcCall.queryFriendHomePage(userId));
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) return null;
            long end = System.currentTimeMillis();
            long serverTime = userHomeObject.optLong("now");
            if (userHomeObject.opt("now") instanceof Number && serverTime > 0L) {
                AntForestV2.offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));
            }
        } catch (Throwable t) {
            if (t instanceof io.github.aw1y2z.sesame.util.TaskCancelledException) throw (io.github.aw1y2z.sesame.util.TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return userHomeObject;
    }

    /**
     * 找能量：反复请求服务端推荐的好友，进主页交给 collectUserEnergy 收取。
     * 对照 AG AntForest.collectEnergyByTakeLook 与 GR 朋友版 findAndCollectEnergy；
     * 先查询推荐预曝光，扫描结束后上报并领取服务端提示的已完成奖励。
     */
    void findAndCollectEnergy() {
        boolean started = false, cancelled = false;
        try {
            Log.record("找能量：开始");
            JSONObject skipUsers = MyUtils.newJSONObject();
            for (String userId : host.dontCollectMap) {
                skipUsers.put(userId, "baohuzhao");
            }
            String exposed = findEnergyExposedFriend(skipUsers);
            Set<String> visited = new HashSet<>();
            int repeat = 0;
            int browsed = 0;
            for (int i = 0; i < 50 && !AntForestV2.hasErrorWait; i++) {
                TimeUtil.sleep(0);
                started = true;
                JSONObject result = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLook(skipUsers, i == 0, exposed)));
                if (result == null) {
                    break;
                }
                String friendId = result.opt("friendId") instanceof String ? result.optString("friendId") : "";
                String actionType = result.optString("actionType");
                boolean ended = result.optBoolean("takeLookEnd");
                if (friendId.isEmpty() || (!actionType.isEmpty() && !"FRIEND".equals(actionType))) {
                    break;
                }
                if (Objects.equals(friendId, host.selfId) || !visited.add(friendId)) {
                    if (ended || ++repeat >= 3) {
                        break;
                    }
                    TimeUtil.sleep(300);
                    continue;
                }
                if (!allowCollectByWhiteList(friendId)) {
                    skipUsers.put(friendId, "baohuzhao");
                    if (ended) break;
                    TimeUtil.sleep(300);
                    continue;
                }
                JSONObject userHomeObject = queryFriendHome(friendId);
                // queryFriendHome 解析失败时返回空对象而非 null，按 length 判断才是真的打开了主页
                if (userHomeObject != null && userHomeObject.length() > 0) {
                    browsed++;
                    repeat = 0; // 只统计连续重复，成功浏览后清零（对照 AG consecutiveEmpty）
                    collectUserEnergy(friendId, userHomeObject, "ordinary");
                    // 能量罩/炸弹卡好友的能量可能没被摘走，不告诉服务端会被反复推荐（对照 AG hasShield||hasBomb）
                    if (AntForestProp.hasActiveProp(userHomeObject, "shield") || AntForestProp.hasActiveProp(userHomeObject, "energyBombCard")) {
                        skipUsers.put(friendId, "baohuzhao");
                    }
                }
                if (ended) {
                    break;
                }
                TimeUtil.sleep(500);
            }
            Log.record("找能量：完成，共浏览 " + browsed + " 个好友");
        } catch (TaskCancelledException e) {
            cancelled = true;
            throw e;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        } finally {
            if (started && !cancelled && !AntForestV2.hasErrorWait) finishFindEnergy();
        }
    }

    void scanForestRankings() {
        TimeUtil.sleep(0);
        JSONObject friends = MyUtils.newJSONObject(AntForestRpcCall.queryEnergyRanking());
        if (!MessageUtil.checkResultCode(TAG, friends)) return;
        collectFriendsEnergy(friends, "ordinary");
        JSONArray rows = friends.optJSONArray("totalDatas");
        List<String> ids = new ArrayList<>();
        for (int i = 20; rows != null && i < rows.length(); i++) {
            TimeUtil.sleep(0);
            JSONObject friend = rows.optJSONObject(i);
            if (friend != null && friend.opt("userId") instanceof String && !friend.optString("userId").isEmpty()) ids.add(friend.optString("userId"));
            if ((i + 1) % 20 == 0) { collectFriendsEnergy(ids, "ordinary"); ids.clear(); }
        }
        if (!ids.isEmpty()) collectFriendsEnergy(ids, "ordinary");
    }





    String findEnergyExposedFriend(JSONObject skipUsers) {
        if (AntForestV2.hasErrorWait) return "";
        try {
            TimeUtil.sleep(0);
            JSONObject payload = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookCombineBiz(skipUsers)));
            JSONObject handlers = payload == null ? null : payload.optJSONObject("combineHandlerVOMap");
            JSONObject expose = handlers == null ? null : handlers.optJSONObject("takeLookExpose");
            Object value = expose == null ? null : expose.opt("friendUserId");
            if (!(value instanceof String)) return "";
            String id = (String) value;
            if (id.isEmpty() || id.length() > 64 || !id.equals(id.trim()) || id.chars().anyMatch(Character::isISOControl)) return "";
            if (host.selfId.equals(id) || host.dontCollectMap.contains(id) || !allowCollectByWhiteList(id)) return "";
            return id;
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Exception e) {
            Log.record("找能量预曝光未取得有效目标，继续普通推荐");
            return "";
        }
    }

    static Map<String, JSONObject> takeLookRewardTasks(JSONObject payload) {
        if (payload == null) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        java.util.ArrayDeque<JSONObject> queue = new java.util.ArrayDeque<>();
        queue.add(payload);
        // ponytail: cap the whole grouped/child tree at 200 nodes; unknown or oversized trees stop.
        int count = 0;
        while (!queue.isEmpty()) {
            if (++count > 200) return null;
            JSONObject node = queue.remove();
            for (String list : new String[]{"forestTasksNew", "taskGroupInfoList", "taskInfoList", "childTaskTypeList"}) {
                if (!node.has(list)) continue;
                JSONArray rows = node.optJSONArray(list);
                if (rows == null || rows.length() > 200 || queue.size() + rows.length() > 200) return null;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) return null;
                    queue.add(row);
                }
            }
            if (!node.has("taskBaseInfo")) continue;
            JSONObject base = node.optJSONObject("taskBaseInfo");
            if (base == null || !(base.opt("sceneCode") instanceof String) || !(base.opt("taskType") instanceof String)
                    || !(base.opt("taskStatus") instanceof String)) return null;
            String scene = base.optString("sceneCode"), type = base.optString("taskType");
            if (scene.isEmpty() || type.isEmpty() || base.optString("taskStatus").isEmpty()
                    || result.put(scene + "#" + type, base) != null) return null;
        }
        if (!payload.has("forestTasksNew") && !payload.has("taskGroupInfoList") && !payload.has("taskInfoList")) return null;
        return result;
    }

    void finishFindEnergy() {
        try {
            TimeUtil.sleep(0);
            JSONObject end = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLookEnd()));
            receiveFindEnergyRewards(end, "chInfo_ch_appid-60000002");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    void finishEnergyRainFlow() {
        try {
            TimeUtil.sleep(0);
            if (!host.energyRain.getValue() || AntForestV2.hasErrorWait) return;
            JSONObject end = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.takeLookEnd("backFromEnergyRain")));
            JSONObject ext = end == null ? null : end.optJSONObject("extInfoInTakeLookEnd");
            boolean hint = false;
            for (String name : new String[]{"energyGrant", "energyPlay"}) {
                JSONObject item = ext == null ? null : ext.optJSONObject(name);
                Object title = item == null ? null : item.opt("title");
                if (title instanceof String && (((String) title).contains("还能收取") || ((String) title).contains("还可收取"))) hint = true;
            }
            if (hint && host.collectEnergy.getValue() && !isRevivedSelfQuietTime()) findAndCollectEnergy();
            receiveFindEnergyRewards(end, "backFromEnergyRain");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    void receiveFindEnergyRewards(JSONObject end, String source) {
        try {
            if (end == null || !host.receiveForestTaskAward.getValue() || !Boolean.TRUE.equals(end.opt("showTaskList"))) return;
            TimeUtil.sleep(0);
            Map<String, JSONObject> tasks = takeLookRewardTasks(AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookEndTaskList(source))));
            if (tasks == null) return;
            int attempts = 0;
            for (String key : new ArrayList<>(tasks.keySet())) {
                JSONObject task = tasks.get(key);
                if (task == null || !("FINISHED".equals(task.optString("taskStatus")) || "COMPLETE".equals(task.optString("taskStatus")))) continue;
                String flag = "forest::findRewardAttempt::" + key;
                if (Status.hasFlagToday(flag)) continue;
                if (++attempts > 50) return;
                TimeUtil.sleep(0);
                if (!host.receiveForestTaskAward.getValue() || AntForestV2.hasErrorWait) return;
                Status.flagToday(flag);
                JSONObject accepted = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAward(task.optString("sceneCode"), task.optString("taskType"))));
                TimeUtil.sleep(0);
                tasks = takeLookRewardTasks(AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryTakeLookEndTaskList(source))));
                JSONObject fresh = tasks == null ? null : tasks.get(key);
                if (accepted == null || fresh == null || !"RECEIVED".equals(fresh.optString("taskStatus"))) {
                    Log.record("找能量奖励：领取未确认，当天不重复[" + task.optString("taskType") + "]");
                    return;
                }
                Log.forest("找能量奖励🎖️领取状态回查成功[" + task.optString("taskType") + "]");
                long energy = AntForestV2.forestFeatureLong(accepted, "incAwardCount");
                if (accepted.opt("returnData") instanceof String && accepted.optString("returnData").contains("Energy")
                        && energy > 0 && energy <= Integer.MAX_VALUE) Statistics.addData(Statistics.DataType.COLLECTED, (int) energy);
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.printStackTrace(TAG, t); }
    }

    JSONObject collectSelfEnergy() {
        try {
            JSONObject selfHomeObject = querySelfHome();
            if (selfHomeObject != null) {
                if (host.whackModeName.getValue() == whackModeNames.CLOSE) {
                    JSONObject propertiesObject = selfHomeObject.optJSONObject("properties");
                    if (propertiesObject != null) {
                        if (Objects.equals("Y", propertiesObject.optString("whackMole"))) {
                            if (io.github.aw1y2z.sesame.model.task.antForest.WhackMole.closeWhackMole()) {
                                Log.forest("6秒拼手速🧾关闭成功");
                            } else {
                                Log.forest("6秒拼手速⚠️关闭失败");
                            }
                        }
                    }
                }
                String nextAction = selfHomeObject.optString("nextAction");
                if ("WhackMole".equalsIgnoreCase(nextAction)) {
                    Log.record("检测到6秒拼手速强制弹窗，先执行拼手速");
                    host.gameHelper.checkAndHandleWhackMole();
                }
                return collectUserEnergy(UserIdMap.getCurrentUid(), selfHomeObject, "ordinary");
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return null;
    }

    JSONObject collectFriendEnergy(String userId, String getType) {
        if (AntForestV2.hasErrorWait) {
            return null;
        }
        try {
            JSONObject userHomeObject = queryFriendHome(userId);
            if (userHomeObject != null) {
                return collectUserEnergy(userId, userHomeObject, getType);
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.printStackTrace(t);
        }
        return null;
    }

    JSONObject collectUserEnergy(String userId, JSONObject userHomeObject, String getType) {
        try {
            if (!MessageUtil.checkResultCode(TAG, userHomeObject)) {
                return userHomeObject;
            }

            long serverTime = userHomeObject.optLong("now");
            boolean isSelf = Objects.equals(userId, host.selfId);
            String userName;
            boolean isCollectEnergy;
            //默认收炸弹能量
            boolean isBombCollectenergy = true;
            if (getType.equals("PK")) {
                JSONObject userBaseInfo = userHomeObject.optJSONObject("userBaseInfo");
                userName = (userBaseInfo != null ? userBaseInfo.optString("displayName") : "") + "(PK森友)";
                isCollectEnergy = allowCollectByWhiteList(userId);
            } else {
                userName = UserIdMap.getMaskName(userId);
                isCollectEnergy = host.collectEnergy.getValue() && !host.dontCollectMap.contains(userId) && allowCollectByWhiteList(userId);
            }

            if (isSelf) {
                Log.record("进入[" + userName + "]的蚂蚁森林");
                updateUsingPropsEndTime(userHomeObject);
            } else {
                if (isCollectEnergy) {
                    JSONArray jaProps = userHomeObject.optJSONArray("usingUserPropsNew");
                    for (int i = 0; jaProps != null && i < jaProps.length(); i++) {
                        JSONObject joProp = jaProps.optJSONObject(i);
                        if (joProp == null) {
                            continue;
                        }
                        if (Objects.equals("shield", joProp.optString("propGroup"))) {
                            if (joProp.optLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]能量罩保护到[" + TimeUtil.getCommonDateS(joProp.optLong("endTime")) + "]");
                                isCollectEnergy = false;
                                JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                                for (int ii = 0; jaBubbles != null && ii < jaBubbles.length(); ii++) {
                                    JSONObject canbubble = jaBubbles.optJSONObject(ii);
                                    if (canbubble == null) {
                                        continue;
                                    }
                                    long bubbleId = canbubble.optLong("id");
                                    switch (CollectStatus.valueOf(canbubble.optString("collectStatus"))) {
                                        case AVAILABLE:
                                            break;
                                        case WAITING:
                                            long produceTime = canbubble.optLong("produceTime");
                                            //如果保护罩不能覆盖能量成熟时间
                                            if (produceTime < joProp.optLong("endTime")) {
                                                break;
                                            }
                                            if (host.checkIntervalInt + host.checkIntervalInt / 2 > produceTime - serverTime) {
                                                if (host.hasChildTask(getBubbleTimerTid(userId, bubbleId))) {
                                                    break;
                                                }
                                                host.addChildTask(host.new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(canbubble.opt("canBeRobbedTwice"))));
                                                Log.record("[" + userName + "]能量保护罩时间[" + TimeUtil.getCommonDate(joProp.optLong("endTime")) + "]#未覆盖能量球成熟时间[" + TimeUtil.getCommonDate(produceTime) + "]");
                                                Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                            } else {
                                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                                            }
                                            break;
                                    }
                                }
                                break;
                            }
                        }
                        if (Objects.equals("energyBombCard", joProp.optString("propGroup"))) {
                            if (joProp.optLong("endTime") > serverTime) {
                                Log.record("[" + userName + "]使用了炸弹卡");
                                if (userHomeObject.has("bubbles")) {
                                    JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                                    for (int ii = 0; jaBubbles != null && ii < jaBubbles.length(); ii++) {
                                        JSONObject Bombubble = jaBubbles.optJSONObject(ii);
                                        if (Bombubble == null) {
                                            continue;
                                        }
                                        int remainEnergy = Bombubble.optInt("remainEnergy");
                                        //存在小于预设值
                                        if (remainEnergy < host.CollectBombEnergyLimit.getValue()) {
                                            isBombCollectenergy = false;
                                        } else {
                                            Log.record("[" + userName + "]炸弹能量[" + remainEnergy + "g]>设定值[" + host.CollectBombEnergyLimit.getValue() + "g]");
                                            isBombCollectenergy = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!isBombCollectenergy) {
                isCollectEnergy = false;
            }

            if (isCollectEnergy) {
                JSONArray jaBubbles = userHomeObject.optJSONArray("bubbles");
                List<Long> bubbleIdList = new ArrayList<>();
                for (int i = 0; jaBubbles != null && i < jaBubbles.length(); i++) {
                    JSONObject bubble = jaBubbles.optJSONObject(i);
                    if (bubble == null) {
                        continue;
                    }
                    if (isSelf && !allowRevivedSelfBubble(bubble, "WAITING".equals(bubble.optString("collectStatus")))) {
                        host.removeChildTask(getBubbleTimerTid(userId, bubble.optLong("id")));
                        continue;
                    }
                    int remainEnergy = bubble.optInt("remainEnergy");
                    long bubbleId = bubble.optLong("id");
                    switch (CollectStatus.valueOf(bubble.optString("collectStatus"))) {
                        case AVAILABLE:
                            //用阈值判断单个能量球需收取情况
                            if (host.CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                bubbleIdList.add(bubbleId);
                            } else if ((host.CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= host.CollectSelfEnergyThreshold.getValue())) {
                                bubbleIdList.add(bubbleId);
                            } else if (((host.CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= host.CollectSelfEnergyThreshold.getValue()))) {
                                bubbleIdList.add(bubbleId);
                            }
                            break;
                        case WAITING:
                            long produceTime = bubble.optLong("produceTime");
                            if (host.checkIntervalInt + host.checkIntervalInt / 2 > produceTime - serverTime) {
                                if (host.hasChildTask(getBubbleTimerTid(userId, bubbleId))) {
                                    break;
                                }
                                if (host.CollectSelfEnergyType.getValue() == CollectSelfType.ALL) {
                                    host.addChildTask(host.new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if ((host.CollectSelfEnergyType.getValue() == CollectSelfType.OVER_THRESHOLD) && (remainEnergy >= host.CollectSelfEnergyThreshold.getValue())) {
                                    host.addChildTask(host.new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                } else if (((host.CollectSelfEnergyType.getValue() == CollectSelfType.BELOW_THRESHOLD) && (remainEnergy <= host.CollectSelfEnergyThreshold.getValue()))) {
                                    host.addChildTask(host.new BubbleTimerTask(userId, bubbleId, produceTime, userName, Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice"))));
                                    Log.record("添加蹲点收取🪂[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
                                }
                            } else {
                                Log.i("用户[" + userName + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
                            }
                            break;
                    }
                }
                // 组队模式下走逐个收取，避免批量收取在合种场景下异常；
                // 批量仍只接受旧协议明确的Team入口；个人Cultivate不放宽收取条件。
                if (host.batchRobEnergy.getValue() && (host.CollectSelfEnergyType.getValue() == CollectSelfType.ALL)
                        && "Team".equals(userHomeObject.optString("nextAction")) && !AntForestTeam.isTeam(userHomeObject)) {
                    Iterator<Long> iterator = bubbleIdList.iterator();
                    List<Long> batchBubbleIdList = new ArrayList<>();
                    while (iterator.hasNext()) {
                        batchBubbleIdList.add(iterator.next());
                        if (batchBubbleIdList.size() >= 6) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                            batchBubbleIdList = new ArrayList<>();
                        }
                    }
                    int size = batchBubbleIdList.size();
                    if (size > 0) {
                        if (size == 1) {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, batchBubbleIdList.get(0))), userName);
                        } else {
                            collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)), userName);
                        }
                    }
                } else {
                    for (Long bubbleId : bubbleIdList) {
                        collectEnergy(new CollectEnergyEntity(userId, userHomeObject, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), userName);
                    }
                }
            }

            return userHomeObject;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "collectUserEnergy err:", t);
        }
        return null;
    }

    void collectFriendsEnergy(List<String> idList, String getType) {
        try {
            if (AntForestV2.hasErrorWait) {
                return;
            }
            collectFriendsEnergy(MyUtils.newJSONObject(AntForestRpcCall.fillUserRobFlag(new JSONArray(idList).toString())), getType);
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    void collectFriendsEnergy(JSONObject friendsObject, String getType) {
        if (AntForestV2.hasErrorWait) {
            return;
        }
        try {
            JSONArray jaFriendRanking = friendsObject.optJSONArray("friendRanking");
            if (jaFriendRanking == null) {
                return;
            }
            for (int i = 0, len = jaFriendRanking.length(); i < len; i++) {
                try {
                    JSONObject friendObject = jaFriendRanking.optJSONObject(i);
                    if (friendObject == null) {
                        continue;
                    }
                    String userId = friendObject.optString("userId");
                    if (Objects.equals(userId, host.selfId)) {
                        continue;
                    }
                    JSONObject userHomeObject = null;
                    if (getType.equals("PK")) {
                        boolean collectEnergy = true;
                        if (!friendObject.optBoolean("canCollectEnergy")) {
                            long canCollectLaterTime = friendObject.optLong("canCollectLaterTime");
                            if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > host.checkIntervalInt)) {
                                collectEnergy = false;
                            }
                        }
                        if (collectEnergy) {
                            userHomeObject = collectFriendEnergy(userId, getType);
                        }
                    } else {
                        if (host.collectEnergy.getValue() && !host.dontCollectMap.contains(userId) && allowCollectByWhiteList(userId)) {
                            boolean collectEnergy = true;
                            if (!friendObject.optBoolean("canCollectEnergy")) {
                                long canCollectLaterTime = friendObject.optLong("canCollectLaterTime");
                                if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > host.checkIntervalInt)) {
                                    collectEnergy = false;
                                }
                            }
                            if (collectEnergy) {
                                userHomeObject = collectFriendEnergy(userId, getType);
                            } /* else {
                  Log.i("不收取[" + UserIdMap.getNameById(userId) + "], userId=" + userId);
              }*/
                        }

                        if (host.helpFriendCollectType.getValue() != HelpFriendCollectType.NONE && friendObject.optBoolean("canProtectBubble") && !Status.hasFlagToday("forest::protectBubble")) {
                            boolean isHelpCollect = host.helpFriendCollectList.getValue().contains(userId);
                            if (host.helpFriendCollectType.getValue() != HelpFriendCollectType.HELP) {
                                isHelpCollect = !isHelpCollect;
                            }
                            if (isHelpCollect) {
                                if (userHomeObject == null) {
                                    userHomeObject = queryFriendHome(userId);
                                }
                                if (userHomeObject != null) {
                                    protectFriendEnergy(userHomeObject);
                                }
                            }
                        }
                        if (host.collectGiftBox.getValue() && friendObject.optBoolean("canCollectGiftBox")) {
                            if (userHomeObject == null) {
                                userHomeObject = queryFriendHome(userId);
                            }
                            if (userHomeObject != null) {
                                collectGiftBox(userHomeObject);
                            }
                        }
                    }
                } catch (Exception t) {
                    if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
                    Log.err(TAG, "collectFriendEnergy err:", t);
                }
            }
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    void collectGiftBox(JSONObject userHomeObject) {
        try {
            JSONObject giftBoxInfo = userHomeObject.optJSONObject("giftBoxInfo");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (giftBoxInfo != null) {
                JSONArray giftBoxList = giftBoxInfo.optJSONArray("giftBoxList");
                if (giftBoxList != null && giftBoxList.length() > 0) {
                    for (int ii = 0; ii < giftBoxList.length(); ii++) {
                        try {
                            JSONObject giftBox = giftBoxList.optJSONObject(ii);
                            if (giftBox == null) {
                                continue;
                            }
                            String giftBoxId = giftBox.optString("giftBoxId");
                            String title = giftBox.optString("title");
                            JSONObject giftBoxResult = MyUtils.newJSONObject(AntForestRpcCall.collectFriendGiftBox(giftBoxId, userId));
                            if (!MessageUtil.checkResultCode(TAG, giftBoxResult)) {
                                continue;
                            }
                            int energy = giftBoxResult.optInt("energy", 0);
                            Log.forest("礼盒能量🎁[" + UserIdMap.getMaskName(userId) + "-" + title + "]#" + energy + "g");
                            Statistics.addData(Statistics.DataType.COLLECTED, energy);
                        } catch (Throwable t) {
                            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
                            Log.printStackTrace(t);
                            break;
                        } finally {
                            TimeUtil.sleep(500);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    void protectFriendEnergy(JSONObject userHomeObject) {
        try {
            JSONArray wateringBubbles = userHomeObject.optJSONArray("wateringBubbles");
            JSONObject userEnergy = userHomeObject.optJSONObject("userEnergy");
            String userId = userEnergy == null ? UserIdMap.getCurrentUid() : userEnergy.optString("userId");
            if (wateringBubbles != null && wateringBubbles.length() > 0) {
                for (int j = 0; j < wateringBubbles.length(); j++) {
                    try {
                        JSONObject wateringBubble = wateringBubbles.optJSONObject(j);
                        if (wateringBubble == null) {
                            continue;
                        }
                        if (!Objects.equals("fuhuo", wateringBubble.optString("bizType"))) {
                            continue;
                        }
                        JSONObject extInfo = wateringBubble.optJSONObject("extInfo");
                        if (extInfo != null && extInfo.optInt("restTimes", 0) == 0) {
                            Status.flagToday("forest::protectBubble");
                        }
                        if (!wateringBubble.optBoolean("canProtect")) {
                            continue;
                        }
                        int fullEnergy = wateringBubble.optInt("fullEnergy", 0);
                        if (fullEnergy < host.helpFriendCollectListLimit.getValue()) {
                            continue;
                        }
                        JSONObject joProtect = MyUtils.newJSONObject(AntForestRpcCall.protectBubble(userId));
                        if (!MessageUtil.checkResultCode(TAG, joProtect)) {
                            continue;
                        }
                        int vitalityAmount = joProtect.optInt("vitalityAmount", 0);

                        String str = "复活能量🧾[" + UserIdMap.getMaskName(userId) + "-" + fullEnergy + "g]" + (vitalityAmount > 0 ? "#活力值+" + vitalityAmount : "");
                        Log.forest(str);
                        AntForestV2.totalHelpCollected += fullEnergy;
                        Statistics.addData(Statistics.DataType.HELPED, fullEnergy);

                        break;
                    } catch (Throwable t) {
                        if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
                        Log.printStackTrace(t);
                        break;
                    } finally {
                        TimeUtil.sleep(500);
                    }
                }
            }
        } catch (Exception e) {
            if (e instanceof TaskCancelledException) throw (TaskCancelledException) e;
            Log.printStackTrace(e);
        }
    }

    boolean allowCollectByWhiteList(String userId) {
        if (userId == null || userId.isEmpty()) return false;
        if (!host.collectWhiteListMode.getValue() || Objects.equals(userId, host.selfId)) return true;
        Set<String> allowed = host.collectWhiteList.getValue();
        return allowed != null && allowed.contains(userId) && !host.dontCollectMap.contains(userId);
    }

    boolean isRevivedSelfQuietTime() {
        if (!host.onlyCollectRevivedSelfEnergy.getValue()) return false;
        java.util.Calendar now = MyUtils.getInstance();
        return now.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.MONDAY
                && now.get(java.util.Calendar.HOUR_OF_DAY) == 7;
    }

    boolean allowRevivedSelfBubble(JSONObject bubble, boolean waiting) {
        if (!host.onlyCollectRevivedSelfEnergy.getValue()) return true;
        if (bubble == null) return false;
        long energy = AntForestV2.forestFeatureLong(bubble, "fullEnergy");
        if (energy <= 0) return false;
        JSONObject business = bubble.optJSONObject("business");
        if (!waiting && business != null && "fuhuonengliang".equals(business.optString("secondScene"))) return true;
        int max = host.revivedSelfOrdinaryMaxEnergy.getValue();
        return max > 0 && energy <= max;
    }

    boolean allowRevivedSelfRequest(CollectEnergyEntity entity) {
        if (!host.onlyCollectRevivedSelfEnergy.getValue()) return true;
        if (isRevivedSelfQuietTime()) return false;
        if (!Objects.equals(entity.getUserId(), host.selfId)) return true;
        try {
            TimeUtil.sleep(0);
            RpcEntity rpc = entity.getRpcEntity();
            JSONArray args = rpc == null ? null : new JSONArray(rpc.getRequestData());
            JSONObject request = args == null || args.length() != 1 ? null : args.optJSONObject(0);
            JSONArray ids = request == null ? null : request.optJSONArray("bubbleIds");
            if (request == null || !host.selfId.equals(request.optString("userId")) || ids == null || ids.length() == 0 || ids.length() > 6) return false;
            JSONObject raw = querySelfHome();
            JSONObject home = raw == null ? null : AntForestV2.forestSignPayload(raw);
            JSONArray rows = home == null ? null : home.optJSONArray("bubbles");
            if (rows == null) return false;
            Map<Long, JSONObject> current = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long id = row == null ? -1 : AntForestV2.forestFeatureLong(row, "id");
                if (id <= 0 || current.put(id, row) != null) return false;
            }
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < ids.length(); i++) {
                Object value = ids.opt(i);
                if (!(value instanceof Number)) return false;
                long id = new java.math.BigDecimal(value.toString()).longValueExact();
                JSONObject bubble = current.get(id);
                if (id <= 0 || !seen.add(id) || bubble == null
                        || !("AVAILABLE".equals(bubble.optString("collectStatus")) || "WAITING".equals(bubble.optString("collectStatus")))
                        || !allowRevivedSelfBubble(bubble, "WAITING".equals(bubble.optString("collectStatus")))) return false;
            }
            TimeUtil.sleep(0);
            return !isRevivedSelfQuietTime();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { return false; }
    }

    void collectEnergy(CollectEnergyEntity collectEnergyEntity, String username) {
        collectEnergy(collectEnergyEntity, false, username);
    }

    void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread, String username) {
        if (AntForestV2.hasErrorWait || !allowCollectByWhiteList(collectEnergyEntity.getUserId())) {
            return;
        }
        Runnable runnable = () -> {
            try {
                String userId = collectEnergyEntity.getUserId();
                if (!allowCollectByWhiteList(userId)) return;
                RpcEntity rpcEntity = collectEnergyEntity.getRpcEntity();
                boolean needDouble = collectEnergyEntity.getNeedDouble();
                boolean needRetry = collectEnergyEntity.getNeedRetry();
                int tryCount = collectEnergyEntity.addTryCount();
                int collected = 0;
                long startTime;
                synchronized (host.collectEnergyLockLimit) {
                    long sleep;
                    if (needDouble) {
                        collectEnergyEntity.unsetNeedDouble();
                        sleep = host.doubleCollectIntervalEntity.getInterval() - System.currentTimeMillis() + host.collectEnergyLockLimit.get();
                    } else if (needRetry) {
                        collectEnergyEntity.unsetNeedRetry();
                        sleep = host.retryIntervalInt - System.currentTimeMillis() + host.collectEnergyLockLimit.get();
                    } else {
                        sleep = host.collectIntervalEntity.getInterval() - System.currentTimeMillis() + host.collectEnergyLockLimit.get();
                    }
                    if (sleep > 0) {
                        TimeUtil.sleep(sleep);
                    }
                    startTime = System.currentTimeMillis();
                    host.collectEnergyLockLimit.setForce(startTime);
                }
                if (!allowCollectByWhiteList(userId)) return;
                if (!allowRevivedSelfRequest(collectEnergyEntity)) return;
                ApplicationHook.requestObject(rpcEntity, 0, 0);
                long spendTime = System.currentTimeMillis() - startTime;
                if (host.balanceNetworkDelay.getValue()) {
                    host.delayTimeMath.nextInteger((int) (spendTime / 3));
                }
                JSONObject jo = MyUtils.newJSONObject(rpcEntity.getResponseString());
                String errorCode = jo.optString("error");
                if ("RPC_SKIPPED".equals(errorCode)) return;
                if (rpcEntity.getHasError() && !errorCode.isEmpty()) {
                    if (Objects.equals("1004", errorCode)) {
                        if (BaseModel.getWaitWhenException().getValue() > 0) {
                            long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                            RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                            NotificationUtil.updateStatusText("异常");
                            Log.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                            AntForestV2.hasErrorWait = true;
                            return;
                        }
                        TimeUtil.sleep(600 + RandomUtil.delay());
                    }
                    if (tryCount < host.tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }
                String resultCode = jo.optString("resultCode");
                if (!"SUCCESS".equalsIgnoreCase(resultCode)) {
                    if ("PARAM_ILLEGAL2".equals(resultCode)) {
                        Log.forest("[" + username + "]" + "能量已被收取,取消重试 错误:" + jo.optString("resultDesc"));
                        return;
                    }
                    Log.forest("[" + username + "]" + jo.optString("resultDesc"));
                    if (tryCount < host.tryCountInt) {
                        collectEnergyEntity.setNeedRetry();
                        collectEnergy(collectEnergyEntity, username);
                    }
                    return;
                }

                JSONArray jaBubbles = jo.optJSONArray("bubbles");
                if (jaBubbles == null) {
                    jaBubbles = new JSONArray();
                }

                int jaBubbleLength = jaBubbles.length();
                if (jaBubbleLength > 1) {
                    List<Long> newBubbleIdList = new ArrayList<>();
                    for (int i = 0; i < jaBubbleLength; i++) {
                        JSONObject bubble = jaBubbles.optJSONObject(i);
                        if (bubble == null) {
                            continue;
                        }
                        if (bubble.optBoolean("canBeRobbedAgain")) {
                            newBubbleIdList.add(bubble.optLong("id"));
                        }
                        collected += bubble.optInt("collectedEnergy");
                    }
                    if (collected > 0) {
                        FriendWatch.friendWatch(userId, collected);
                        String str;
                        JSONObject bombCardEffect = jo.optJSONObject("bombCardEffect");
                        if (bombCardEffect != null) {
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "一键收取🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "一键收取🪂[" + username + "]#" + collected + "g";
                        }
                        if (needDouble) {
                            Log.forest(str + "[双击]");
                            Log.i("收取耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str);
                            Log.i("收取耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        AntForestV2.totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.forest("一键收取[" + username + "]的能量失败，好友：" + userId + "，气泡：" + newBubbleIdList);
                    }
                    if (!newBubbleIdList.isEmpty()) {
                        collectEnergyEntity.setRpcEntity(AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, newBubbleIdList));
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                    }
                } else if (jaBubbleLength == 1) {
                    JSONObject bubble = jaBubbles.optJSONObject(0);
                    if (bubble == null) {
                        return;
                    }
                    collected += bubble.optInt("collectedEnergy");
                    FriendWatch.friendWatch(userId, collected);
                    if (collected > 0) {
                        String str;
                        JSONObject bombCardEffect = jo.optJSONObject("bombCardEffect");
                        if (bombCardEffect != null) {
                            int explodeEnergy = bombCardEffect.optInt("explodeEnergy", 0);
                            str = "收取能量🪂[" + username + "]#" + collected + "g被炸" + explodeEnergy + "g";
                        } else {
                            str = "收取能量🪂[" + username + "]#" + collected + "g";
                        }

                        if (needDouble) {
                            Log.forest(str + "[双击]");
                            Log.i("收取耗时[" + spendTime + "]ms[双击]");
                            Toast.show(str + "[双击]");
                        } else {
                            Log.forest(str);
                            Log.i("收取耗时[" + spendTime + "]ms");
                            Toast.show(str);
                        }
                        AntForestV2.totalCollected += collected;
                        Statistics.addData(Statistics.DataType.COLLECTED, collected);
                    } else {
                        Log.forest("收取[" + username + "]的能量失败");
                        Log.i("，UserID：" + userId + "，BubbleId：" + bubble.optLong("id"));
                    }
                    if (bubble.optBoolean("canBeRobbedAgain")) {
                        collectEnergyEntity.setNeedDouble();
                        collectEnergyEntity.resetTryCount();
                        collectEnergy(collectEnergyEntity, username);
                        return;
                    }
                    JSONObject userHome = collectEnergyEntity.getUserHome();
                    if (userHome == null) {
                        return;
                    }
                    String bizNo = userHome.optString("bizNo");
                    if (bizNo.isEmpty()) {
                        return;
                    }
                    if (host.returnWater.getValue()) {
                        int returnCount = 0;
                        if (collected >= host.returnWater33.getValue()) {
                            returnCount = 33;
                        } else if (collected >= host.returnWater18.getValue()) {
                            returnCount = 18;
                        } else if (collected >= host.returnWater10.getValue()) {
                            returnCount = 10;
                        }
                        if (returnCount > 0) {
                            host.waterHelper.returnFriendWater(userId, bizNo, 1, returnCount);
                        }
                    }
                }
            } catch (TaskCancelledException e) {
                throw e;
            } catch (Exception e) {
                Log.err(TAG, "collectEnergy err:", e);
            } finally {
                Statistics.save();
                notifyMain();
            }
        };
        host.taskCount.incrementAndGet();
        if (joinThread) {
            runnable.run();
        } else {
            host.addChildTask(new ChildModelTask("CE|" + collectEnergyEntity.getUserId() + "|" + runnable.hashCode(), "CE", runnable));
        }
    }

    void updateUsingPropsEndTime() throws JSONException {
        JSONObject joHomePage = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
        TimeUtil.sleep(100);
        updateUsingPropsEndTime(joHomePage);
    }

    void updateUsingPropsEndTime(JSONObject joHomePage) {
        try {
            JSONArray ja = joHomePage.optJSONArray("loginUserUsingPropNew");
            if (ja == null || ja.length() == 0) {
                ja = joHomePage.optJSONArray("usingUserPropsNew");
            }
            if (ja == null) {
                return;
            }
            for (int i = 0; i < ja.length(); i++) {
                JSONObject jo = ja.optJSONObject(i);
                if (jo == null) {
                    continue;
                }
                String propGroup = jo.optString("propGroup");
                Long endTime = jo.optLong("endTime");
                String propId = jo.optString("propId");
                String propType = jo.optString("propType");
                AntForestV2.usingProps.put(propGroup, endTime);
            }
            host.forestExtensions();
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "updateUsingPropsEndTime err:", th);
        }
    }

    /**
     * 收取倍卡额外能量（本轮收尾调用）。倍卡额度是本轮收取行为累积出来的，
     * 先收只能收到上一轮攒下的部分，本轮领到的要等到下一轮；且好友能量是异步收取，
     * 必须等子任务跑完再查，否则查到的仍是未累积的旧值。
     */
    void collectRobExpandEnergy() {
        if (!host.collectRobExpandEnergyEnable.getValue()) {
            return;
        }
        try {
            TimeUtil.sleep(0);
            JSONObject joHomePage = querySelfHome();
            if (joHomePage != null) {
                collectRobExpandEnergy(joHomePage);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    /** 从首页响应里取出在用的倍卡并收取 */
    void collectRobExpandEnergy(JSONObject joHomePage) {
        try {
            Set<String> handled = new HashSet<>();
            // 个人版与组队版字段可能并存，同一道具只收取一次。
            for (String field : new String[]{"loginUserUsingPropNew", "usingUserPropsNew", "usingUserProps"}) {
                JSONArray ja = joHomePage.optJSONArray(field);
                for (int i = 0; ja != null && i < ja.length(); i++) {
                    JSONObject jo = ja.optJSONObject(i);
                    if (jo != null && PropGroup.robExpandCard.name().equals(jo.optString("propGroup"))
                            && jo.opt("propId") instanceof String && jo.opt("propType") instanceof String
                            && !jo.optString("propId").trim().isEmpty() && !jo.optString("propType").trim().isEmpty()
                            && handled.add(jo.optString("propId") + "\u0000" + jo.optString("propType"))) {
                        TimeUtil.sleep(0);
                        collectRobExpandEnergy(jo.optString("extInfo"), jo.optString("propId"), jo.optString("propType"));
                    }
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    void collectRobExpandEnergy(String extInfo, String propId, String propType) {
        if (extInfo.isEmpty()) {
            return;
        }
        // 关闭"倍卡额外能量"开关：不收取额外能量
        if (!host.collectRobExpandEnergyEnable.getValue()) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(extInfo);
            double leftEnergy = jo.optDouble("leftEnergy", Double.NaN);
            if (!Double.isFinite(leftEnergy) || leftEnergy < 0) {
                Log.record("额外能量：在用倍卡回执缺少有效剩余能量，本轮不收取");
                return;
            }
            // 开关开启：受数值限制，只有大于"倍卡额外能量"阈值才收取
            if (leftEnergy > host.collectRobExpandEnergy.getValue()) {
                collectRobExpandEnergy(propId, propType);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    void collectRobExpandEnergy(String propId, String propType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.collectRobExpandEnergy(propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int collectEnergy = jo.optInt("collectEnergy");
                Log.forest("额外能量🎄收取[" + collectEnergy + "g]");
                AntForestV2.totalCollected += collectEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectEnergy);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "collectRobExpandEnergy err:", th);
        }
    }

    void queryForestEnergy(String scene) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryForestEnergy(scene));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject data = jo.optJSONObject("data");
            jo = data != null ? data.optJSONObject("response") : null;
            if (jo == null) {
                return;
            }
            JSONArray ja = jo.optJSONArray("energyGeneratedList");
            if (ja == null) {
                ja = new JSONArray();
            }
            if (ja.length() > 0) {
                harvestForestEnergy(scene, ja);
            }
            int remainBubble = jo.optInt("remainBubble");
            for (int i = 0; i < remainBubble; i++) {
                ja = produceForestEnergy(scene);
                if (ja.length() == 0 || !harvestForestEnergy(scene, ja)) {
                    return;
                }
                TimeUtil.sleep(1000);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "queryForestEnergy err:", th);
        }
    }

    JSONArray produceForestEnergy(String scene) {
        JSONArray energyGeneratedList = new JSONArray();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.produceForestEnergy(scene));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONObject data = jo.optJSONObject("data");
                jo = data != null ? data.optJSONObject("response") : null;
                if (jo == null) {
                    return energyGeneratedList;
                }
                JSONArray list = jo.optJSONArray("energyGeneratedList");
                energyGeneratedList = list != null ? list : new JSONArray();
                if (energyGeneratedList.length() > 0) {
                    String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                    int cumulativeEnergy = jo.optInt("cumulativeEnergy");
                    Log.forest("医疗健康🚑完成[" + title + "]#产生[" + cumulativeEnergy + "g能量]");
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "produceForestEnergy err:", th);
        }
        return energyGeneratedList;
    }

    Boolean harvestForestEnergy(String scene, JSONArray bubbles) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.harvestForestEnergy(scene, bubbles));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            jo = data != null ? data.optJSONObject("response") : null;
            if (jo == null) {
                return false;
            }
            int collectedEnergy = jo.optInt("collectedEnergy");
            if (collectedEnergy > 0) {
                String title = scene.equals("FEEDS") ? "绿色医疗" : "电子小票";
                Log.forest("医疗健康🧾收取[" + title + "]#获得[" + collectedEnergy + "g能量]");
                AntForestV2.totalCollected += collectedEnergy;
                Statistics.addData(Statistics.DataType.COLLECTED, collectedEnergy);
                return true;
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "harvestForestEnergy err:", th);
        }
        return false;
    }

    public static String getBubbleTimerTid(String ui, long bi) {
        return "BT|" + ui + "|" + bi;
    }
}
