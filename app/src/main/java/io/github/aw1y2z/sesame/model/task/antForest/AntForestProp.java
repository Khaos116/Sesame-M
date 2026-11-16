package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.BubbleTimerTask;

final class AntForestProp {

    private static final String TAG = AntForestProp.class.getSimpleName();
    private final AntForestV2 host;

    AntForestProp(AntForestV2 host) {
        this.host = host;
    }

    /** 指定道具（shield 能量罩 / energyBombCard 炸弹卡）是否仍在生效；serverTime 取好友主页的 now，缺失时退回本机时间 */
    static boolean hasActiveProp(JSONObject userHomeObject, String propGroup) {
        long serverTime = userHomeObject.optLong("now");
        if (serverTime <= 0) {
            serverTime = System.currentTimeMillis();
        }
        JSONArray props = userHomeObject.optJSONArray("usingUserPropsNew");
        for (int i = 0; props != null && i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop != null && propGroup.equals(prop.optString("propGroup")) && prop.optLong("endTime") > serverTime) {
                return true;
            }
        }
        return false;
    }

    Map<Long, Long> waitingBoostBubbles(JSONObject home) {
        long now = AntForestV2.forestFeatureLong(home, "now");
        JSONArray rows = home.optJSONArray("bubbles");
        if (now <= 0 || rows == null) return null;
        Map<Long, Long> result = new LinkedHashMap<>();
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            long id = row == null ? -1 : AntForestV2.forestFeatureLong(row, "id");
            if (id <= 0 || !seen.add(id)) return null;
            if (!"WAITING".equals(row.optString("collectStatus"))) continue;
            long energy = AntForestV2.forestFeatureLong(row, "fullEnergy"), at = AntForestV2.forestFeatureLong(row, "produceTime");
            if (energy <= 0 || at <= now) continue;
            int type = host.CollectSelfEnergyType.getValue(), threshold = host.CollectSelfEnergyThreshold.getValue();
            if (type != CollectSelfType.ALL && !(type == CollectSelfType.OVER_THRESHOLD && energy >= threshold)
                    && !(type == CollectSelfType.BELOW_THRESHOLD && energy <= threshold)) continue;
            result.put(id, at);
        }
        return result;
    }

    static JSONObject selectBoostProp(JSONObject bag, int mode, long now) throws JSONException {
        JSONArray rows = bag == null ? null : bag.optJSONArray("forestPropVOList");
        if (rows == null) return null;
        JSONObject best = null;
        int bestRank = Integer.MAX_VALUE;
        long bestExpiry = Long.MAX_VALUE;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject prop = rows.optJSONObject(i);
            if (prop == null) return null;
            if (!"boost".equals(prop.optString("propGroup"))) continue;
            long count = AntForestV2.forestFeatureLong(prop, "holdsNum");
            JSONArray ids = prop.optJSONArray("propIdList");
            if (count < 0 || ids == null) return null;
            for (int j = 0; j < ids.length(); j++) {
                Object id = ids.opt(j);
                if (!(id instanceof String) || ((String) id).isEmpty() || !seen.add((String) id)) return null;
            }
            if (count == 0 || ids.length() == 0) continue;
            JSONObject config = prop.optJSONObject("propConfigVO");
            Object rawType = prop.has("propType") ? prop.opt("propType") : config == null ? null : config.opt("propType");
            if (!(rawType instanceof String)) continue;
            String type = (String) rawType;
            boolean limited = "LIMIT_TIME_ENERGY_BUBBLE_BOOST".equals(type);
            if (!limited && !"BUBBLE_BOOST".equals(type)) continue;
            if (mode == UsePropType.ONLY_LIMIT_TIME && !limited) continue;
            long expiry = prop.has("recentExpireTime") ? AntForestV2.forestFeatureLong(prop, "recentExpireTime") : 0;
            if (expiry < 0 || limited && expiry <= now || !limited && expiry > 0 && expiry <= now) continue;
            if (expiry == 0) expiry = Long.MAX_VALUE;
            int rank = limited ? 0 : 1;
            if (rank < bestRank || rank == bestRank && expiry < bestExpiry) {
                best = MyUtils.newJSONObject(prop.toString()).put("propType", type);
                bestRank = rank; bestExpiry = expiry;
            }
        }
        return best;
    }

    static long boostStockForId(JSONObject bag, String id) {
        JSONArray rows = bag == null ? null : bag.optJSONArray("forestPropVOList");
        if (rows == null) return -1;
        long result = 0;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject prop = rows.optJSONObject(i);
            if (prop == null) return -1;
            if (!"boost".equals(prop.optString("propGroup"))) continue;
            long count = AntForestV2.forestFeatureLong(prop, "holdsNum");
            JSONArray ids = prop.optJSONArray("propIdList");
            if (count < 0 || ids == null) return -1;
            for (int j = 0; j < ids.length(); j++) {
                Object value = ids.opt(j);
                if (!(value instanceof String) || ((String) value).isEmpty() || !seen.add((String) value)) return -1;
                if (id.equals(value)) result = count;
            }
        }
        return result;
    }

    JSONObject useBubbleBoostCard() {
        synchronized (host.usePropLockObj) {
            if (!host.boostTimeAllowed(System.currentTimeMillis())) return null;
            return useBubbleBoostCardLocked();
        }
    }







    JSONObject useBubbleBoostCardLocked() {
        if (ForestExpiringProps.hasUnconfirmed("boost")) return null;
        if (host.onlyCollectRevivedSelfEnergy.getValue()) return null;
        int mode = host.bubbleBoostCard.getValue(), limit = host.bubbleBoostDailyLimit.getValue();
        if ((mode != UsePropType.ALL && mode != UsePropType.ONLY_LIMIT_TIME) || limit <= 0 || !host.collectEnergy.getValue()
                || Status.getIntFlagToday("forest::boostAttempts") >= limit) return null;
        try {
            TimeUtil.sleep(0);
            JSONObject rawHome = host.energyHelper.querySelfHome();
            JSONObject home = rawHome == null ? null : AntForestV2.forestSignPayload(rawHome);
            if (home == null) return null;
            Map<Long, Long> waiting = waitingBoostBubbles(home);
            if (waiting == null || waiting.isEmpty()) return null;
            JSONObject bag = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
            JSONObject prop = selectBoostProp(bag, mode, AntForestV2.forestFeatureLong(home, "now"));
            if (prop == null) {
                JSONArray refilled = refillForestProp("boost");
                if (refilled != null) { bag = MyUtils.newJSONObject().put("forestPropVOList", refilled);prop = selectBoostProp(bag, mode, AntForestV2.forestFeatureLong(home, "now")); }
            }
            if (prop == null) return null;
            TimeUtil.sleep(0);
            rawHome = host.energyHelper.querySelfHome();
            home = rawHome == null ? null : AntForestV2.forestSignPayload(rawHome);
            if (home == null) return null;
            waiting = waitingBoostBubbles(home);
            if (waiting == null || waiting.isEmpty()) return null;
            prop = selectBoostProp(bag, mode, AntForestV2.forestFeatureLong(home, "now"));
            if (prop == null) return null;
            String id = prop.optJSONArray("propIdList").optString(0);
            String flag = "forest::boostAttempt::" + id;
            if (Status.hasFlagToday(flag)) return null;
            TimeUtil.sleep(0);
            if (host.bubbleBoostCard.getValue() != mode || !host.collectEnergy.getValue() || host.onlyCollectRevivedSelfEnergy.getValue()
                    || !host.boostTimeAllowed(System.currentTimeMillis())
                    || Status.getIntFlagToday("forest::boostAttempts") >= host.bubbleBoostDailyLimit.getValue()) return null;
            String pointFlag = host.boostPointFlag(System.currentTimeMillis());
            if (!pointFlag.isEmpty()) Status.flagToday(pointFlag);
            Status.flagToday(flag);
            Status.setIntFlagToday("forest::boostAttempts", Status.getIntFlagToday("forest::boostAttempts") + 1);
            JSONObject accepted = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.consumeProp("boost", id, prop.optString("propType"), false)));
            TimeUtil.sleep(0);
            JSONObject afterRaw = host.energyHelper.querySelfHome();
            JSONObject after = afterRaw == null ? null : AntForestV2.forestSignPayload(afterRaw);
            JSONObject afterBag = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
            long stock = boostStockForId(afterBag, id);
            if (accepted == null || after == null || stock < 0 || stock >= AntForestV2.forestFeatureLong(prop, "holdsNum")) return null;
            long now = AntForestV2.forestFeatureLong(after, "now");
            JSONArray rows = after.optJSONArray("bubbles");
            if (rows == null || now < AntForestV2.forestFeatureLong(home, "now")) return null;
            Set<Long> seen = new HashSet<>(), changed = new HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long bubble = row == null ? -1 : AntForestV2.forestFeatureLong(row, "id");
                if (bubble <= 0 || !seen.add(bubble)) return null;
                Long original = waiting.get(bubble);
                if (original == null) continue;
                long at = AntForestV2.forestFeatureLong(row, "produceTime");
                if ("WAITING".equals(row.optString("collectStatus")) && at > 0 && at < original
                        || "AVAILABLE".equals(row.optString("collectStatus")) && now < original && AntForestV2.forestFeatureLong(row, "remainEnergy") > 0) changed.add(bubble);
            }
            if (changed.isEmpty()) {
            Log.record("时光加速器效果未确认，本日不重复尝试此卡");
                return null;
            }
            TimeUtil.sleep(0);
            for (Long bubble : changed) host.removeChildTask(AntForestEnergy.getBubbleTimerTid(host.selfId, bubble));
            Log.forest("时光加速器🌪库存及加速效果回查成功#" + changed.size() + "个能量球");
            return host.energyHelper.collectUserEnergy(host.selfId, afterRaw, "ordinary");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err(TAG, "useBubbleBoostCard", e); }
        return null;
    }

    void continuousUseCardOptions() {
        //双击卡
        continuousUseAndExchangeCard("doubleClick", "SK20240805004754");
        //收能量倍卡
        continuousUseAndExchangeCard("robExpandCard", "");
        //保护罩：自动续用为独立开关，不受「连续兑换使用道具卡片」选项限制
        if (host.autoUseShieldCard.getValue()) {
            continuousUseAndExchangeCard("shield", "CR20230516000370");
        }
        //隐身卡
        continuousUseAndExchangeCard("stealthCard", "SK20230521000206");
        //炸弹卡
        //continuousUseAndExchangeCard("energyBombCard", "SK20250219006517");
    }

    void continuousUseAndExchangeCard(String propGroupType, String exchangeProp) {
        if (ForestExpiringProps.hasUnconfirmed(propGroupType) || !canUseContinuousCard(propGroupType)) return;
        try {
            if ("doubleClick".equals(propGroupType) && host.smartDoubleCard.getValue()) {
                useSmartDoubleCard();
                return;
            }
            if (propGroupType.equals("shield") || host.continuousUseCardOptions.contains(propGroupType)) {
                if ("robExpandCard".equals(propGroupType) && (host.robExpandCardReplaceRemainDays.getValue() > 0
                        || host.robExpandCardForceReplaceExpireDays.getValue() > 0)) {
                    refillForestProp(propGroupType);
                    usePreferredRobExpandCard();
                    return;
                }
                long continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                // M 支持续罩和倍卡续用，生效中仍按原阈值处理。
                if (continuousUseCardSecond >= 0) {
                    TimeUtil.sleep(500);
                    JSONObject rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                    if (rightCard == null) {
                        if (host.forestPropRefill.getValue()) {
                            refillForestProp(propGroupType);
                        } else if (exchangeProp != null) {
                            host.mallHelper.exchangeBenefit(exchangeProp);
                            TimeUtil.sleep(500);
                        }
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                    }
                    int holdsNum = rightCard.optInt("holdsNum");
                    if (holdsNum == 0) {
                        return;
                    }
                    int loopCount = 0; // 循环次数计数
                    final int MAX_LOOP = 10; // 最大循环次数，避免死循环
                    do {
                        rightCard = chooseContinuousLIMITTIMECard(propGroupType);
                        if (rightCard == null) {
                            return;
                        }
                        holdsNum = rightCard.optInt("holdsNum");
                        if (holdsNum == 0) {
                            return;
                        }
                        if (!rightCard.has("propIdList")) {
                            return;
                        }
                        JSONArray propIdList = rightCard.optJSONArray("propIdList");
                        if (propIdList == null || propIdList.length() == 0 || !(propIdList.opt(0) instanceof String) || propIdList.optString(0).trim().isEmpty()) {
                            return;
                        }
                        if (!canUseContinuousCard(propGroupType)) return;
                        String propId = propIdList.optString(0);
                        String propType = rightCard.optString("propType");
                        JSONObject propConfigVO = rightCard.optJSONObject("propConfigVO");
                        String propName = propConfigVO != null ? propConfigVO.optString("propName") : "";
                        JSONObject joResult = null;
                        switch (propGroupType) {
                            case "doubleClick":
                            case "shield":
                            case "robExpandCard":
                                if (continuousUseCardSecond > 0) {
                                    joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, true));
                                } else {
                                    joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType, false));
                                }
                                holdsNum--;
                                TimeUtil.sleep(500);
                                break;

                            case "stealthCard":
                                joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                break;
                            /*case "energyBombCard":
                                joResult = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroupType, propId, propType,false));
                                holdsNum--;
                                TimeUtil.sleep(1000);
                                if (MessageUtil.checkResultCode(TAG, joResult)) {
                                    Log.forest("使用道具🎭[" + propName + "]");
                                }
                                break;*/
                        }
                        boolean used = joResult != null && MessageUtil.checkResultCode(TAG, joResult)
                                && !RpcRequestGuard.isFailure(joResult);
                        if (used) {
                            recordContinuousCardUse(propGroupType);
                            Log.forest("使用道具🎭[" + propName + "]");
                        }
                        continuousUseCardSecond = continuousUseCardCheak(propGroupType);
                        if (continuousUseCardSecond < 0) {
                            return;
                        }
                        TimeUtil.sleep(500);
                    } while (holdsNum > 0 && ++loopCount < MAX_LOOP);
                }
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "continuousUseAndExchangeCard err:", th);
        }
    }

    boolean canUseContinuousCard(String group) {
        Integer limit = host.continuousUseCardOptions.get(group);
        return limit == null || limit == 0 || limit > 0
                && Status.getIntFlagToday("continuousUseCard::count::" + group) < limit;
    }

    void recordContinuousCardUse(String group) {
        String key = "continuousUseCard::count::" + group;
        Status.setIntFlagToday(key, Status.getIntFlagToday(key) + 1);
    }

    boolean smartDoubleEnabled() {
        return host.smartDoubleCard.getValue() && host.collectEnergy.getValue() && !AntForestV2.hasErrorWait
                && host.continuousUseCardOptions.contains("doubleClick") && !host.energyHelper.isRevivedSelfQuietTime()
                && host.smartDoubleCardDailyLimit.getValue() > 0;
    }

    List<BubbleTimerTask> smartDoubleTargets(long now) {
        List<BubbleTimerTask> targets = new ArrayList<>();
        List<ChildModelTask> queued = host.forestChildSnapshot();
        if (queued.size() > 2000) return targets;
        for (ChildModelTask child : queued) {
            if (!(child instanceof BubbleTimerTask) || !Boolean.FALSE.equals(child.getIsCancel())) continue;
            BubbleTimerTask task = (BubbleTimerTask) child;
            if (task.canDouble && !host.selfId.equals(task.userId) && !host.dontCollectMap.contains(task.userId)
                    && host.energyHelper.allowCollectByWhiteList(task.userId) && task.produceTime >= now + 10000
                    && task.produceTime <= now + 290000) targets.add(task);
        }
        return targets;
    }

    List<BubbleTimerTask> confirmSmartDoubleTargets(List<BubbleTimerTask> queued) {
        Map<String, List<BubbleTimerTask>> friends = new LinkedHashMap<>();
        for (BubbleTimerTask task : queued) friends.computeIfAbsent(task.userId, ignored -> new ArrayList<>()).add(task);
        if (friends.size() > 50) return null;
        List<BubbleTimerTask> targets = new ArrayList<>();
        for (Map.Entry<String, List<BubbleTimerTask>> friend : friends.entrySet()) {
            TimeUtil.sleep(0);
            JSONObject raw = host.energyHelper.queryFriendHome(friend.getKey());
            JSONObject home = raw == null ? null : AntForestV2.forestSignPayload(raw);
            if (home == null) return null;
            for (String key : new String[]{"userInfo", "userBaseInfo"}) {
                if (!home.has(key)) continue;
                JSONObject identity = home.optJSONObject(key);
                if (identity == null || identity.has("userId") && (!(identity.opt("userId") instanceof String)
                        || !friend.getKey().equals(identity.optString("userId")))) return null;
            }
            if (home.has("usingUserPropsNew")) {
                JSONArray props = home.optJSONArray("usingUserPropsNew");
                if (props == null || props.length() > 100) return null;
                for (int i = 0; i < props.length(); i++) {
                    JSONObject prop = props.optJSONObject(i);
                    if (prop == null || !(prop.opt("propGroup") instanceof String) || AntForestV2.forestFeatureLong(prop, "endTime") < 0) return null;
                }
            }
            if (hasActiveProp(home, "shield") || hasActiveProp(home, "energyBombCard")) continue;
            JSONArray rows = home.optJSONArray("bubbles");
            if (rows == null || rows.length() > 200) return null;
            Map<Long, JSONObject> bubbles = new HashMap<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                long id = row == null ? -1 : AntForestV2.forestFeatureLong(row, "id");
                if (id <= 0 || bubbles.put(id, row) != null) return null;
            }
            long now = System.currentTimeMillis();
            for (BubbleTimerTask task : friend.getValue()) {
                JSONObject bubble = bubbles.get(task.bubbleId);
                if (bubble != null && "WAITING".equals(bubble.optString("collectStatus"))
                        && Boolean.TRUE.equals(bubble.opt("canBeRobbedTwice")) && AntForestV2.forestFeatureLong(bubble, "fullEnergy") > 0
                        && AntForestV2.forestFeatureLong(bubble, "produceTime") == task.produceTime && task.produceTime >= now + 10000
                        && task.produceTime <= now + 290000 && Boolean.FALSE.equals(task.getIsCancel())
                        && !host.dontCollectMap.contains(task.userId) && host.energyHelper.allowCollectByWhiteList(task.userId)) targets.add(task);
            }
        }
        return targets;
    }

    long querySmartDoubleEnd() {
        TimeUtil.sleep(0);
        JSONObject data = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo()));
        JSONObject map = data == null ? null : data.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray rows = using == null ? null : using.optJSONArray("userPropVOS");
        if (rows == null || rows.length() > 100) return -1;
        long active = 0, now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String)) return -1;
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            long end = AntForestV2.forestFeatureLong(row, "endTime");
            if (end < 0 || end > now && active > 0) return -1;
            if (end > now) active = end;
        }
        return active;
    }

    JSONArray querySmartDoubleInventory() {
        TimeUtil.sleep(0);
        JSONObject data = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false)));
        JSONArray rows = data == null ? null : data.optJSONArray("forestPropVOList");
        if (rows == null || rows.length() > 100) return null;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String)) return null;
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            long count = AntForestV2.forestFeatureLong(row, "holdsNum");
            JSONArray list = row.optJSONArray("propIdList");
            if (!(row.opt("propType") instanceof String) || row.optString("propType").isEmpty() || count < 0 || count > 1000
                    || list == null && count > 0 || list != null && list.length() != count
                    || row.has("recentExpireTime") && AntForestV2.forestFeatureLong(row, "recentExpireTime") < 0) return null;
            for (int j = 0; list != null && j < list.length(); j++) {
                Object id = list.opt(j);
                if (!(id instanceof String) || ((String) id).isEmpty() || ((String) id).length() > 256 || !ids.add((String) id)) return null;
            }
        }
        return rows;
    }

    JSONObject chooseSmartDoubleCard(JSONArray rows) {
        JSONObject chosen = null;
        long expiry = Long.MAX_VALUE, now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (!"doubleClick".equals(row.optString("propGroup")) || !"LIMIT_TIME_ENERGY_DOUBLE_CLICK".equals(row.optString("propType"))
                    || AntForestV2.forestFeatureLong(row, "holdsNum") <= 0) continue;
            long until = row.has("recentExpireTime") ? AntForestV2.forestFeatureLong(row, "recentExpireTime") : 0;
            if (until > 0 && until <= now + 10000 || until < 0) continue;
            JSONArray ids = row.optJSONArray("propIdList");
            if (ids == null || ids.length() == 0 || Status.hasFlagToday("forest::smartDoubleCard::" + ids.optString(0))) continue;
            long priority = until == 0 ? Long.MAX_VALUE : until;
            if (chosen == null || priority < expiry) { chosen = row; expiry = priority; }
        }
        return chosen;
    }

    static long smartDoubleStock(JSONArray rows, String type) {
        if (rows == null) return -1;
        long count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if ("doubleClick".equals(row.optString("propGroup")) && type.equals(row.optString("propType"))) count += AntForestV2.forestFeatureLong(row, "holdsNum");
        }
        return count;
    }

    static boolean smartDoubleHasId(JSONArray rows, String id) {
        for (int i = 0; i < rows.length(); i++) {
            JSONArray ids = rows.optJSONObject(i).optJSONArray("propIdList");
            for (int j = 0; ids != null && j < ids.length(); j++) if (id.equals(ids.opt(j))) return true;
        }
        return false;
    }

    void useSmartDoubleCard() {
        if (ForestExpiringProps.hasUnconfirmed("doubleClick") || !canUseContinuousCard("doubleClick")) return;
        TimeUtil.sleep(0);
        if (!smartDoubleEnabled()) return;
        synchronized (host.usePropLockObj) {
            if (!smartDoubleEnabled() || !host.selfId.equals(UserIdMap.getCurrentUid()) || Status.hasFlagToday("forest::smartDoubleUnconfirmed")
                    || Status.getIntFlagToday("forest::smartDoubleAttempts") >= host.smartDoubleCardDailyLimit.getValue()) return;
            List<BubbleTimerTask> targets = smartDoubleTargets(System.currentTimeMillis());
            if (targets.size() < host.smartDoubleCardThreshold.getValue()) return;
            if (host.smartDoublePermanent.getValue() || host.smartDouble31Days.getValue() || host.forestPropRefill.getValue()) {
                useAdvancedSmartDouble(targets);
                return;
            }
            try {
                if (querySmartDoubleEnd() != 0) return;
                targets = confirmSmartDoubleTargets(targets);
                if (targets == null || targets.size() < host.smartDoubleCardThreshold.getValue()) return;
                JSONArray before = querySmartDoubleInventory();
                JSONObject prop = before == null ? null : chooseSmartDoubleCard(before);
                if (prop == null || querySmartDoubleEnd() != 0) return;
                long now = System.currentTimeMillis();
                long expiry = prop.has("recentExpireTime") ? AntForestV2.forestFeatureLong(prop, "recentExpireTime") : 0;
                if (expiry > 0 && expiry <= now + 10000) return;
                targets.removeIf(task -> task.produceTime < now + 10000 || task.produceTime > now + 290000
                        || !Boolean.FALSE.equals(task.getIsCancel()) || !host.energyHelper.allowCollectByWhiteList(task.userId) || host.dontCollectMap.contains(task.userId));
                if (!smartDoubleEnabled() || !host.selfId.equals(UserIdMap.getCurrentUid()) || targets.size() < host.smartDoubleCardThreshold.getValue()) return;
                String type = prop.optString("propType"), id = prop.optJSONArray("propIdList").optString(0);
                long stock = smartDoubleStock(before, type);
                TimeUtil.sleep(0);
                Status.flagToday("forest::smartDoubleCard::" + id);
                Status.setIntFlagToday("forest::smartDoubleAttempts", Status.getIntFlagToday("forest::smartDoubleAttempts") + 1);
                Status.flagToday("forest::smartDoubleUnconfirmed");
                JSONObject ack = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.consumeProp("doubleClick", id, type, false)));
                JSONArray after = querySmartDoubleInventory();
                long end = querySmartDoubleEnd();
                if (ack != null && after != null && stock > 0 && smartDoubleStock(after, type) == stock - 1
                        && !smartDoubleHasId(after, id) && end > System.currentTimeMillis()) {
                    Status.clearFlag("forest::smartDoubleUnconfirmed");
                    recordContinuousCardUse("doubleClick");
                    Log.forest("智能双击卡🎭[未来5分钟" + targets.size() + "个可双击好友球]#库存扣减及生效确认");
                }
            } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
            } catch (Throwable e) { Log.err(TAG, "smartDoubleCard err:", e); }
        }
    }

    void scheduleSmartDoubleCheck() {
        TimeUtil.sleep(0);
        synchronized (host.usePropLockObj) {
            long now = System.currentTimeMillis(), earliest = Long.MAX_VALUE;
            if (smartDoubleEnabled() && host.selfId.equals(UserIdMap.getCurrentUid()) && !Status.hasFlagToday("forest::smartDoubleUnconfirmed")
                    && Status.getIntFlagToday("forest::smartDoubleAttempts") < host.smartDoubleCardDailyLimit.getValue()) {
                List<ChildModelTask> queued = host.forestChildSnapshot();
                if (queued.size() <= 2000) for (ChildModelTask child : queued) {
                    if (!(child instanceof BubbleTimerTask) || !Boolean.FALSE.equals(child.getIsCancel())) continue;
                    BubbleTimerTask task = (BubbleTimerTask) child;
                    if (task.canDouble && !host.selfId.equals(task.userId) && task.produceTime > now + 10000
                            && host.energyHelper.allowCollectByWhiteList(task.userId) && !host.dontCollectMap.contains(task.userId)) earliest = Math.min(earliest, task.produceTime);
                }
            }
            long at = earliest == Long.MAX_VALUE ? 0 : Math.max(now + 30000, earliest - 290000);
            ChildModelTask current = host.smartDoubleCheckId == null ? null : host.getChildTask(host.smartDoubleCheckId);
            if (at > 0 && current != null && Boolean.FALSE.equals(current.getIsCancel()) && current.getExecTime() > now && current.getExecTime() <= at) return;
            if (host.smartDoubleCheckId != null) { host.removeChildTask(host.smartDoubleCheckId); host.smartDoubleCheckId = null; }
            if (at == 0) return;
            String uid = host.selfId, id = "SMART_DOUBLE|" + uid + "|" + System.nanoTime();
            if (host.addChildTask(new ChildModelTask(id, "antForest", () -> {
                TimeUtil.sleep(0);
                synchronized (host.usePropLockObj) {
                    if (!id.equals(host.smartDoubleCheckId) || !uid.equals(UserIdMap.getCurrentUid())) return;
                    host.smartDoubleCheckId = null;
                    useSmartDoubleCard();
                    scheduleSmartDoubleCheck();
                }
            }, at))) host.smartDoubleCheckId = id;
        }
    }

    JSONArray refillForestProp(String group) {
        if (!host.forestPropRefill.getValue() || host.forestPropRefillBudget.getValue() <= 0 || AntForestV2.hasErrorWait
                || !host.selfId.equals(UserIdMap.getCurrentUid())) return null;
        TimeUtil.sleep(0);
        host.mallHelper.getAllSkuInfo();
        return ForestPropSupport.replenish(group, true, host.vitality_ExchangeBenefitList.getValue(), host.skuInfo, host.forestPropRefillBudget.getValue());
    }

    void useAdvancedSmartDouble(List<BubbleTimerTask> targets) {
        if (ForestExpiringProps.hasUnconfirmed("doubleClick") || !canUseContinuousCard("doubleClick")) return;
        try {
            long end = querySmartDoubleEnd();
            if (!ForestPropSupport.doubleRenewAllowed(end, host.smartDouble31Days.getValue() && host.smartDoubleRenew31.getValue(), System.currentTimeMillis())) return;
            targets = confirmSmartDoubleTargets(targets);
            if (targets == null || targets.size() < host.smartDoubleCardThreshold.getValue()) return;
            JSONArray rows = querySmartDoubleInventory();
            JSONObject prop = ForestPropSupport.chooseDouble(rows, host.smartDoublePermanent.getValue(), host.smartDouble31Days.getValue(), System.currentTimeMillis(), end);
            if (prop == null && end == 0) {
                rows = refillForestProp("doubleClick");
                prop = ForestPropSupport.chooseDouble(rows, host.smartDoublePermanent.getValue(), host.smartDouble31Days.getValue(), System.currentTimeMillis(), end);
            }
            long now = System.currentTimeMillis();
            targets.removeIf(t -> t.produceTime < now + 10000 || t.produceTime > now + 290000 || !Boolean.FALSE.equals(t.getIsCancel())
                    || !host.energyHelper.allowCollectByWhiteList(t.userId) || host.dontCollectMap.contains(t.userId));
            if (prop == null || !smartDoubleEnabled() || !host.selfId.equals(UserIdMap.getCurrentUid()) || targets.size() < host.smartDoubleCardThreshold.getValue()) return;
            if (ForestPropSupport.consumeDouble(prop, end, host.smartDouble31Days.getValue() && host.smartDoubleRenew31.getValue(), host.smartDoubleCardDailyLimit.getValue())) {
                recordContinuousCardUse("doubleClick");
                Log.forest("智能双击卡🎭[未来5分钟" + targets.size() + "个可双击好友球]#库存及生效回查成功");
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Exception e) { Log.err(TAG, "advancedSmartDouble", e); }
    }

    /** null 表示查询失败/状态不明；空对象表示确认没有生效中的 N 倍卡。 */
    JSONObject queryRobExpandCardState() {
        JSONObject response = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
        if (!"SUCCESS".equals(response.optString("resultCode")) || RpcRequestGuard.isFailure(response)) return null;
        JSONObject map = response.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray props = using == null ? null : using.optJSONArray("userPropVOS");
        if (props == null) return null;
        JSONObject active = null;
        long now = System.currentTimeMillis();
        for (int i = 0; i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop == null || prop.optString("propGroup").isEmpty()) return null;
            if (!"robExpandCard".equals(prop.optString("propGroup"))) continue;
            long end = AntForestV2.forestFeatureLong(prop, "endTime");
            if (end < 0) return null;
            if (end <= now) continue;
            if (active != null || robExpandCardFactor(prop.optJSONObject("detail")) <= 0) return null;
            active = prop;
        }
        return active == null ? MyUtils.newJSONObject("{}") : active;
    }

    static double robExpandCardFactor(JSONObject detail) {
        Object value = detail == null ? null : detail.opt("factor");
        if (!(value instanceof Number) && !(value instanceof String)) return 0;
        try {
            double factor = new java.math.BigDecimal(value.toString()).doubleValue();
            return Double.isFinite(factor) && factor > 0 ? factor : 0;
        } catch (NumberFormatException e) { return 0; }
    }

    /** 复用 AG 策略：临期高倍率优先；普通替换不缩短有效期；否则仅同倍率续用。 */
    static JSONObject choosePreferredRobExpandCard(JSONArray props, JSONObject active, long now,
                                                          int replaceDays, int urgentDays) {
        if (props == null || active == null) return null;
        long remaining = active.length() == 0 ? 0 : AntForestV2.forestFeatureLong(active, "endTime") - now;
        double current = robExpandCardFactor(active.optJSONObject("detail"));
        if (active.length() > 0 && (remaining <= 0 || current <= 0)) return null;
        JSONObject best = null;
        int bestRank = -1;
        double bestFactor = 0;
        long bestExpiry = Long.MAX_VALUE, bestDuration = Long.MAX_VALUE;
        for (int i = 0; i < props.length(); i++) {
            JSONObject prop = props.optJSONObject(i);
            if (prop == null || !"robExpandCard".equals(prop.optString("propGroup"))
                    || AntForestV2.forestFeatureLong(prop, "holdsNum") <= 0) continue;
            JSONArray ids = prop.optJSONArray("propIdList");
            JSONObject config = prop.optJSONObject("propConfigVO");
            if (ids == null || !(ids.opt(0) instanceof String) || ids.optString(0).isEmpty() || config == null
                    || !(prop.opt("propType") instanceof String || !prop.has("propType") && config.opt("propType") instanceof String)
                    || prop.optString("propType", config.optString("propType")).isEmpty()) continue;
            double factor = robExpandCardFactor(config.optJSONObject("detail"));
            long expiry = AntForestV2.forestFeatureLong(prop, "recentExpireTime");
            long seconds = AntForestV2.forestFeatureLong(config, "durationTime");
            long duration = seconds > 0 && seconds <= Long.MAX_VALUE / 1000 ? seconds * 1000 : 0;
            if (factor <= 0 || expiry <= now) continue;
            int rank;
            if (active.length() == 0) rank = 1;
            else if (factor > current + 0.0001) {
                if (urgentDays > 0 && expiry - now <= TimeUnit.DAYS.toMillis(urgentDays)) rank = 2;
                else if (replaceDays > 0 && remaining <= TimeUnit.DAYS.toMillis(replaceDays)
                        && duration >= remaining) rank = 1;
                else continue;
            } else if (Math.abs(factor - current) <= 0.0001 && remaining < TimeUnit.DAYS.toMillis(30)) rank = 0;
            else continue;
            // 临期/同倍率先到期先用，普通替换先倍率；相同条件用较短有效期。
            boolean better = rank > bestRank || rank == bestRank && (rank == 1
                    ? factor > bestFactor + 0.0001 || Math.abs(factor - bestFactor) <= 0.0001 && expiry < bestExpiry
                    : expiry < bestExpiry || expiry == bestExpiry && factor > bestFactor + 0.0001);
            if (!better && rank == bestRank && Math.abs(factor - bestFactor) <= 0.0001 && expiry == bestExpiry)
                better = (duration == 0 ? Long.MAX_VALUE : duration) < bestDuration;
            if (better) {
                best = prop; bestRank = rank; bestFactor = factor; bestExpiry = expiry;
                bestDuration = duration == 0 ? Long.MAX_VALUE : duration;
            }
        }
        return best;
    }

    void usePreferredRobExpandCard() {
        if (ForestExpiringProps.hasUnconfirmed("robExpandCard") || !canUseContinuousCard("robExpandCard")) return;
        try {
            TimeUtil.sleep(0);
            JSONObject before = queryRobExpandCardState();
            if (before == null) { Log.record("N倍卡：生效状态不明，跳过替换"); return; }
            JSONArray props = getForestPropVOList();
            JSONObject card = choosePreferredRobExpandCard(props, before, System.currentTimeMillis(),
                    host.robExpandCardReplaceRemainDays.getValue(), host.robExpandCardForceReplaceExpireDays.getValue());
            if (card == null) { Log.record("N倍卡：没有符合替换/续用条件的限时卡"); return; }
            String id = card.optJSONArray("propIdList").optString(0);
            JSONObject config = card.optJSONObject("propConfigVO");
            String type = card.optString("propType", config.optString("propType"));
            String flag = "forest::robExpandCardAttempt::" + id;
            // ponytail: 同一道具编号每天最多提交一次；不确定结果留待次日或人工核验。
            if (Status.hasFlagToday(flag)) { Log.record("N倍卡：本日已提交该道具，避免重复消耗"); return; }
            TimeUtil.sleep(0);
            Status.flagToday(flag);
            JSONObject result = MyUtils.newJSONObject(AntForestRpcCall.consumeProp("robExpandCard", id, type, false));
            JSONObject data = result.optJSONObject("resData");
            String status = (data == null ? result : data).optString("usePropStatus");
            if ("SUCCESS".equals(result.optString("resultCode")) && !RpcRequestGuard.isFailure(result)
                    && (status.startsWith("NEED_CONFIRM") || "REPLACE".equals(status))) {
                // 仅在二次确认协议明确要求且状态未变时覆盖，防止手动用卡/并行任务竞态。
                JSONObject fresh = queryRobExpandCardState();
                if (fresh == null || fresh.length() == 0 || before.length() == 0
                        || AntForestV2.forestFeatureLong(fresh, "endTime") != AntForestV2.forestFeatureLong(before, "endTime")
                        || Math.abs(robExpandCardFactor(fresh.optJSONObject("detail"))
                        - robExpandCardFactor(before.optJSONObject("detail"))) > 0.0001
                        || choosePreferredRobExpandCard(new JSONArray().put(card), fresh, System.currentTimeMillis(),
                        host.robExpandCardReplaceRemainDays.getValue(), host.robExpandCardForceReplaceExpireDays.getValue()) == null) {
                    Log.record("N倍卡：确认前状态改变，停止替换"); return;
                }
                TimeUtil.sleep(0);
                result = MyUtils.newJSONObject(AntForestRpcCall.consumeProp("robExpandCard", id, type, true));
            }
            JSONObject after = queryRobExpandCardState();
            double expected = robExpandCardFactor(config.optJSONObject("detail"));
            if (after != null && after.length() > 0 && Math.abs(robExpandCardFactor(after.optJSONObject("detail")) - expected) <= 0.0001
                    && (before.length() == 0 || robExpandCardFactor(after.optJSONObject("detail"))
                    > robExpandCardFactor(before.optJSONObject("detail")) + 0.0001
                    || AntForestV2.forestFeatureLong(after, "endTime") > AntForestV2.forestFeatureLong(before, "endTime"))) {
                recordContinuousCardUse("robExpandCard");
                Log.forest("使用道具🎭[" + config.optString("propName", "N倍卡") + "]，已回查生效");
            } else Log.record("N倍卡：使用结果未确认，本日不重复提交该道具，code=" + result.optString("resultCode", "缺失"));
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "usePreferredRobExpandCard", t); }
    }

    /**
     * 该道具类型在"当前没在使用（接口里没出现 / 已经过期）"时，是否应该启用一张新的。
     * <p>只有保护罩需要这样处理：它的意义是让能量球不被偷，过期后必须再动一张新的，否则等于没保护。
     * 上述两种情况都返回 -1 会让过期后永远不再续用，表现为"不自动使用保护罩"。其余道具维持原行为：
     * 隐身卡固定不使用；双击卡/倍率卡按阈值接续，改动会额外消耗道具。
     */
    static boolean canStartWhenNotInUse(String propGroupType) {
        return "shield".equals(propGroupType);
    }

    long continuousUseCardCheak(String propGroupType) {
        try {
            JSONObject joMiscHomes = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return -1;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return -1;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap == null || !combineHandlerVOMap.has("usingProp")) {
                // 当前没有任何道具在使用：保护罩要能从头用一张，其余道具保持原行为（不可用）
                return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : -1;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (usingProp == null || !usingProp.has("userPropVOS")) {
                return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : -1;
            }
            JSONArray userPropVOS = usingProp.optJSONArray("userPropVOS");
            for (int i = 0; userPropVOS != null && i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.optJSONObject(i);
                if (userPropVO == null) {
                    continue;
                }
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals(propGroupType)) {
                    long endTime = userPropVO.optLong("endTime");
                    long duringTime = endTime - now;
                    if (duringTime < 0) {
                        // 用过的道具已过期（接口里仍留着这条记录）：保护罩应重新启用一张
                        return canStartWhenNotInUse(propGroupType) ? 0 : -1;
                    }
                    switch (propGroupType) {
                        case "doubleClick":
                            if (duringTime / (1000 * 60) < 60 * 24 * 31) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "robExpandCard":
                            if (duringTime / (1000 * 60) < 60 * 24 * 30) {
                                return duringTime;
                            } else {
                                return -1;
                            }
                        case "stealthCard":
                            return -1;
                        case "shield":
                            // 在保护中且剩余<阈值：返回 >0 表示"需续用延长"；充足则返回 -1 不处理
                            return duringTime / (1000 * 60) < 60 * host.continuousUseShieldHour.getValue()
                                    ? duringTime : -1;
                        /*case "energyBombCard":
                            if (duringTime / (1000 * 60) < 3*60 * 24) {
                                Log.forest("duringTime");
                                return duringTime;
                            }
                            else {
                                return -1;
                            }*/

                    }
                }
            }
            // usingProp 列表不含该道具（保护罩不在其中），改按主页真实到期时间判断
            return canStartWhenNotInUse(propGroupType) ? shieldFallback(now) : 0;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return -1;
    }

    /**
     * 保护罩在道具接口里查不到时的兜底：按个人主页的到期时间判断是否续用
     * （返回值同 {@link #continuousUseCardCheak}：-1 不可用 / 0 无保护可用 / >0 剩余毫秒）。
     */
    long shieldFallback(long now) {
        long endTime = queryShieldEndTime();
        if (endTime < 0) return -1;
        long duringTime = endTime - now;
        if (duringTime <= 0) {
            return 0;
        }
        return duringTime / (1000 * 60) < 60 * host.continuousUseShieldHour.getValue() ? duringTime : -1;
    }

    /**
     * 保护罩的真实到期时间（毫秒），未在保护中返回 0。
     * <p>{@code queryMiscInfo} 的 usingProp 不含 shield，只能从个人主页的 usingUserPropsNew 取。
     */
    long queryShieldEndTime() {
        try {
            JSONObject joHomePage = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, joHomePage)) {
                return -1;
            }
            JSONArray ja = joHomePage.optJSONArray("loginUserUsingPropNew");
            if (ja == null || ja.length() == 0) {
                ja = joHomePage.optJSONArray("usingUserPropsNew");
            }
            if (ja == null) {
                return 0;
            }
            long endTime = 0;
            for (int i = 0; i < ja.length(); i++) {
                JSONObject prop = ja.optJSONObject(i);
                if (prop == null) continue;
                if ("shield".equals(prop.optString("propGroup"))) {
                    if (!(prop.opt("endTime") instanceof Number)) return -1;
                    endTime = Math.max(endTime, prop.optLong("endTime"));
                }
            }
            return endTime;
        } catch (Throwable th) {
            Log.err(TAG, "queryShieldEndTime err:", th);
        }
        return -1;
    }

    String useRobExpandCardFactor() {
        try {
            JSONObject joMiscHomes = MyUtils.newJSONObject(AntForestRpcCall.queryMiscInfo());
            if (!MessageUtil.checkResultCode(TAG, joMiscHomes)) {
                return null;
            }
            if (!joMiscHomes.has("combineHandlerVOMap")) {
                return null;
            }
            long now = System.currentTimeMillis();
            JSONObject combineHandlerVOMap = joMiscHomes.optJSONObject("combineHandlerVOMap");
            if (combineHandlerVOMap == null || !combineHandlerVOMap.has("usingProp")) {
                return null;
            }
            JSONObject usingProp = combineHandlerVOMap.optJSONObject("usingProp");
            if (usingProp == null || !usingProp.has("userPropVOS")) {
                return null;
            }
            JSONArray userPropVOS = usingProp.optJSONArray("userPropVOS");
            for (int i = 0; userPropVOS != null && i < userPropVOS.length(); i++) {
                JSONObject userPropVO = userPropVOS.optJSONObject(i);
                if (userPropVO == null) {
                    continue;
                }
                String propGroup = userPropVO.optString("propGroup");
                if (propGroup.equals("robExpandCard")) {
                    if (!userPropVO.has("detail")) {
                        return null;
                    }
                    JSONObject detail = userPropVO.optJSONObject("detail");
                    return detail != null ? detail.optString("factor") : null;
                }
            }
            return null;
        } catch (Throwable th) {
            Log.err(TAG, "UserobExpandCardFactor err:", th);
        }
        return null;
    }

    JSONObject chooseContinuousLIMITTIMECard(String propGroupType) {
        try {
            JSONArray forestPropVOList = getForestPropVOList();
            JSONObject rightCard = null;
            String useFactor = useRobExpandCardFactor();
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestBagProp = forestPropVOList.optJSONObject(i);
                if (forestBagProp == null) {
                    continue;
                }
                String propGroup = forestBagProp.optString("propGroup");
                if (forestBagProp.has("recentExpireTime") && propGroup.equals(propGroupType)) {
                    switch (propGroup) {
                        case "stealthCard":
                        case "shield":
                        case "doubleClick":
                            //case "energyBombCard":
                            if (rightCard != null) {
                                long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                    rightCard = forestBagProp;
                                }
                            } else {
                                rightCard = forestBagProp;
                            }
                            break;
                        case "robExpandCard":
                            //有在用倍卡，选择同倍率快到期卡
                            if (useFactor != null) {
                                String factorforestBagProp = JsonUtil.getValueByPath(forestBagProp, "propConfigVO.detail.factor");
                                if (factorforestBagProp == null) continue;
                                if (factorforestBagProp.equals(useFactor)) {
                                    if (rightCard != null) {
                                        String factorrightCard = JsonUtil.getValueByPath(rightCard, "propConfigVO.detail.factor");
                                        long recentExpireTimerightCard = rightCard.optLong("recentExpireTime");
                                        long recentExpireTimeforestBagProp = forestBagProp.optLong("recentExpireTime");
                                        if (Float.parseFloat(factorrightCard) == Float.parseFloat(factorforestBagProp)) {
                                            if (recentExpireTimerightCard > recentExpireTimeforestBagProp) {
                                                rightCard = forestBagProp;
                                            }
                                        }
                                    } else {
                                        rightCard = forestBagProp;
                                    }
                                }
                            }
                            //没有在用倍卡，选择最高倍率卡
                            else {
                                if (rightCard != null) {
                                    String factorrightCard = JsonUtil.getValueByPath(rightCard, "propConfigVO.detail.factor");
                                    String factorforestBagProp = JsonUtil.getValueByPath(forestBagProp, "propConfigVO.detail.factor");
                                if (factorforestBagProp == null) continue;
                                    if (Float.parseFloat(factorrightCard) < Float.parseFloat(factorforestBagProp)) {
                                        rightCard = forestBagProp;
                                    }
                                } else {
                                    rightCard = forestBagProp;
                                }
                            }
                    }
                }
            }
            // 保护罩的永久卡（ENERGY_SHIELD）服务端不下发 recentExpireTime，会被上面按到期时间的筛选排除，
            // 导致背包里有罩也一张都用不了，故补一次兜底；限时保护罩仍优先于它
            if (rightCard == null && "shield".equals(propGroupType)) {
                for (int i = 0; i < forestPropVOList.length(); i++) {
                    JSONObject forestBagProp = forestPropVOList.optJSONObject(i);
                    if (forestBagProp == null) continue;
                    if ("shield".equals(forestBagProp.optString("propGroup")) && !forestBagProp.has("recentExpireTime")) {
                        rightCard = forestBagProp;
                        break;
                    }
                }
            }
            return rightCard;
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
        return null;
    }

    void usePropBeforeCollectEnergy(String userId) {
        if (Objects.equals(host.selfId, userId)) {
            return;
        }
        if (needDoubleClick()) {
            synchronized (host.usePropLockObj) {
                if (needDoubleClick()) {
                    useDoubleCard(getForestPropVOList());
                }
            }
        }
    }

    Boolean needDoubleClick() {
        // 双击卡配置已停用（addField 被注释）→ 字段为 null，按"关闭"处理，避免 NPE
        if (host.doubleClickType == null || host.doubleClickType.getValue() == UsePropType.CLOSE) {
            return false;
        }
        Long doubleClickEndTime = AntForestV2.usingProps.get(PropGroup.doubleClick.name());
        if (doubleClickEndTime == null) {
            return true;
        }
        return doubleClickEndTime < System.currentTimeMillis();
    }

    void useDoubleCard(JSONArray forestPropVOList) {
        try {
            if (hasDoubleCardTime() && Status.canDoubleToday()) {
                // 背包查找 能量双击卡
                JSONObject jo = null;
                List<JSONObject> list = getPropGroup(forestPropVOList, PropGroup.doubleClick.name());
                if (!list.isEmpty()) {
                    jo = list.get(0);
                }
                if (jo == null || !jo.has("recentExpireTime")) {
                    // 配置已停用则字段为 null，按"关闭"处理
                    if (host.doubleCardConstant != null && host.doubleCardConstant.getValue()) {
                        // 商店兑换 限时能量双击卡
                        if (host.mallHelper.exchangeBenefit("SK20240805004754")) {
                            jo = getForestPropVO(getForestPropVOList(), "ENERGY_DOUBLE_CLICK_31DAYS");
                        } else if (host.mallHelper.exchangeBenefit("CR20230516000363")) {
                            jo = getForestPropVO(getForestPropVOList(), "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
                        }
                    }
                }
                if (jo == null) {
                    return;
                }
                if (!jo.has("recentExpireTime")
                        && (host.doubleClickType == null || host.doubleClickType.getValue() == UsePropType.ONLY_LIMIT_TIME)) {
                    return;
                }
                // 使用能量双击卡
                if (consumeProp(jo)) {
                    JSONObject propConfigVO = jo.optJSONObject("propConfigVO");
                    long durationTime = propConfigVO != null ? propConfigVO.optLong("durationTime") : 0;
                    Long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime);
                    AntForestV2.usingProps.put(PropGroup.doubleClick.name(), endTime);
                    Status.DoubleToday();
                } else {
                    host.energyHelper.updateUsingPropsEndTime();
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "useDoubleCard err:", th);
        }
    }

    boolean hasDoubleCardTime() {
        // 配置已停用（addField 被注释）→ 字段为 null，视为"不在使用时间范围"
        if (host.doubleCardTime == null) {
            return false;
        }
        long currentTimeMillis = System.currentTimeMillis();
        return TimeUtil.checkInTimeRange(currentTimeMillis, host.doubleCardTime.getValue());
    }

    /* 赠送道具 */
    void giveProp() {
        Set<String> set = host.whoYouWantToGiveTo.getValue();
        if (set.isEmpty()) {
            return;
        }
        for (String userId : set) {
            if (UserIdMap.getCurrentUid() == null || Objects.equals(UserIdMap.getCurrentUid(), userId)) {
                continue;
            }
            giveProp(userId);
            break;
        }
    }

    void giveProp(String targetUserId) {
        final int maxGiveRounds = 100;
        Map<String, Long> lastStockByPropId = new HashMap<>();
        try {
            for (int round = 0; round < maxGiveRounds; round++) {
                if (RunGeneration.isStale()) throw new TaskCancelledException();
                try {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPropList(true));
                    if (!MessageUtil.checkResultCode(TAG, jo)) {
                        return;
                    }
                    JSONArray forestPropVOList = jo.optJSONArray("forestPropVOList");
                    if (forestPropVOList != null && forestPropVOList.length() > 0) {
                        jo = forestPropVOList.optJSONObject(0);
                        if (jo == null) {
                            return;
                        }
                        JSONObject giveConfigVO = jo.optJSONObject("giveConfigVO");
                        JSONObject propConfigVO = jo.optJSONObject("propConfigVO");
                        JSONArray propIdList = jo.optJSONArray("propIdList");
                        if (giveConfigVO == null || propConfigVO == null || propIdList == null || propIdList.length() == 0) {
                            Log.record("赠送道具#道具信息不完整，本轮停止");
                            return;
                        }
                        String giveConfigId = giveConfigVO.opt("giveConfigId") instanceof String ? giveConfigVO.optString("giveConfigId") : "";
                        long holdsNum = forestFeatureLong(jo, "holdsNum");
                        String propName = propConfigVO.optString("propName");
                        String propId = propIdList.opt(0) instanceof String ? propIdList.optString(0) : "";
                        if (giveConfigId.isEmpty() || propId.isEmpty() || holdsNum <= 0) {
                            Log.record("赠送道具#道具标识或库存无效，本轮停止");
                            return;
                        }
                        // 同一ID可能代表一叠道具，只有库存严格减少才允许继续赠送。
                        Long previousStock = lastStockByPropId.put(propId, holdsNum);
                        if (previousStock != null && holdsNum >= previousStock) {
                            Log.record("赠送道具#赠送后库存未推进，本轮停止");
                            return;
                        }
                        jo = MyUtils.newJSONObject(AntForestRpcCall.giveProp(giveConfigId, propId, targetUserId));
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            Log.forest("赠送道具🎭[" + UserIdMap.getMaskName(targetUserId) + "]#" + propName);
                            if (holdsNum > 1 || forestPropVOList.length() > 1) {
                                continue;
                            }
                        }
                    }
                } finally {
                    TimeUtil.sleep(1500);
                }
                return;
            }
            Log.record("赠送道具#本轮已达" + maxGiveRounds + "次上限，剩余道具留待下一轮");
        } catch (TaskCancelledException e) {
            throw e;
        } catch (Throwable th) {
            Log.err(TAG, "giveProp err:", th);
        }
    }

    int forFriendCollectEnergy(String targetUserId, long bubbleId) {
        int helped = 0;
        try {
            String s = AntForestRpcCall.forFriendCollectEnergy(targetUserId, bubbleId);
            JSONObject jo = MyUtils.newJSONObject(s);
            if ("SUCCESS".equals(jo.optString("resultCode"))) {
                JSONArray jaBubbles = jo.optJSONArray("bubbles");
                for (int i = 0; jaBubbles != null && i < jaBubbles.length(); i++) {
                    jo = jaBubbles.optJSONObject(i);
                    if (jo != null) {
                        helped += jo.optInt("collectedEnergy");
                    }
                }
                if (helped > 0) {
                    Log.forest("帮收能量🧺[" + UserIdMap.getMaskName(targetUserId) + "]#" + helped + "g");
                    AntForestV2.totalHelpCollected += helped;
                    Statistics.addData(Statistics.DataType.HELPED, helped);
                } else {
                    Log.forest("帮[" + UserIdMap.getMaskName(targetUserId) + "]收取失败");
                    Log.i(TAG, "帮收取失败详情: UserID=" + targetUserId + ", BubbleId=" + bubbleId);
                }
            } else {
                Log.forest("[" + UserIdMap.getMaskName(targetUserId) + "]" + jo.optString("resultDesc"));
                Log.i(s);
            }
        } catch (Throwable t) {
            Log.err(TAG, "forFriendCollectEnergy err:", t);
        }
        return helped;
    }

    public static JSONArray getForestPropVOList() {
        JSONArray forestPropVOList = new JSONArray();
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryPropList(false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                JSONArray list = jo.optJSONArray("forestPropVOList");
                if (list != null) {
                    forestPropVOList = list;
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "getForestPropVOList err:", th);
        }
        return forestPropVOList;
    }

    public static List<JSONObject> getPropGroup(JSONArray forestPropVOList, String propGroup) {
        List<JSONObject> list = new ArrayList<>();
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.optJSONObject(i);
                if (forestPropVO != null && propGroup.equals(forestPropVO.optString("propGroup"))) {
                    list.add(forestPropVO);
                }
            }
            Collections.sort(list, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject jsonObject1, JSONObject jsonObject2) {
                    JSONObject propConfigVO1 = jsonObject1.optJSONObject("propConfigVO");
                    JSONObject propConfigVO2 = jsonObject2.optJSONObject("propConfigVO");
                    int durationTime1 = propConfigVO1 != null ? propConfigVO1.optInt("durationTime") : 0;
                    int durationTime2 = propConfigVO2 != null ? propConfigVO2.optInt("durationTime") : 0;
                    boolean hasExpireTime1 = jsonObject1.has("recentExpireTime");
                    boolean hasExpireTime2 = jsonObject2.has("recentExpireTime");
                    if (hasExpireTime1 && hasExpireTime2) {
                        long endTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(durationTime1);
                        long recentExpireTime = jsonObject2.optLong("recentExpireTime");
                        if (endTime < recentExpireTime) {
                            return -1;
                        } else {
                            return durationTime2 - durationTime1;
                        }
                    } else if (!hasExpireTime1 && !hasExpireTime2) {
                        return durationTime1 - durationTime2;
                    } else {
                        return hasExpireTime1 ? -1 : 1;
                    }
                }
            });
        } catch (Throwable th) {
            Log.err(TAG, "getPropGroup err:", th);
        }
        return list;
    }

    /*
     * 查找背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    JSONObject getForestPropVO(JSONArray forestPropVOList, String propType) {
        try {
            for (int i = 0; i < forestPropVOList.length(); i++) {
                JSONObject forestPropVO = forestPropVOList.optJSONObject(i);
                if (forestPropVO != null && propType.equals(forestPropVO.optString("propType"))) {
                    return forestPropVO;
                }
            }
        } catch (Throwable th) {
            Log.err(TAG, "getForestPropVO err:", th);
        }
        return null;
    }

    /*
     * 使用背包道具
     * prop
     * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
     */
    public static Boolean consumeProp(JSONObject prop) {
        try {
            // 使用道具
            JSONArray propIdList = prop.optJSONArray("propIdList");
            JSONObject propConfigVO = prop.optJSONObject("propConfigVO");
            if (propIdList == null || propIdList.length() == 0 || propConfigVO == null) {
                return false;
            }
            String propId = propIdList.optString(0);
            String propType = prop.optString("propType");
            String propGroup = prop.optString("propGroup");
            String propName = propConfigVO.optString("propName");
            return consumeProp(propGroup, propId, propType, propName);
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }

    static Boolean consumeProp(String propGroup, String propId, String propType, String propName) {
        if (ForestExpiringProps.hasUnconfirmed(propGroup)) return false;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroup, propId, propType));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("使用道具🎭[" + propName + "]");
                return true;
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "consumeProp err:", th);
        }
        return false;
    }
}
