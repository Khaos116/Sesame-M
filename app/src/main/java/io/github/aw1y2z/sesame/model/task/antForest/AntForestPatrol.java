package io.github.aw1y2z.sesame.model.task.antForest;

import io.github.aw1y2z.sesame.util.DailyTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.task.antSports.AntSports;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestPatrol {

    private static final String TAG = AntForestPatrol.class.getSimpleName();
    private final AntForestV2 host;

    AntForestPatrol(AntForestV2 host) {
        this.host = host;
    }

    /**
     * 新版保护地任务列表初始化：把服务端下发的任务名同步到 {@link MonopolyTaskListMap}，
     * 作为配置页「新版保护地 | 黑名单列表」的候选项。
     * <p>没有预置黑白名单：新版保护地任务名由服务端动态下发，全部交给自动拉黑机制判定。
     */
    public void initMonopolyTaskListMap() {
        try {
            MonopolyTaskListMap.load();
            // regionCode 由 entryInfo 动态下发(区域轮换无需改代码),只拉当前区域任务作为黑名单候选项
            String regionCode = getMonopolyRegionCode();
            if (!regionCode.isEmpty()) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, AntForestV2.MONOPOLY_TASK_SCENE_HSDWY));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                    if (taskInfoList != null) {
                        for (int i = 0; i < taskInfoList.length(); i++) {
                            JSONObject task = taskInfoList.optJSONObject(i);
                            JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                            if (base == null) {
                                continue;
                            }
                            String taskType = base.optString("taskType");
                            if (taskType.isEmpty()) {
                                continue;
                            }
                            String title = AntForestV2.blackTaskKey(monopolyTaskTitle(base, taskType));
                            MonopolyTaskListMap.add(title, title);
                        }
                    }
                }
            }
            MonopolyTaskListMap.save();
            Log.record("同步任务🉑新版保护地任务列表");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "initMonopolyTaskListMap err:", t);
        }
    }

    void logLegacyPatrolState(String stage, String message) {
        String scope = UserIdMap.getCurrentUid() + "|"
                + String.format(java.util.Locale.ROOT, "%tY%<tm%<td", MyUtils.getInstance());
        String state = scope + "|" + message;
        if (!DailyTask.isManual() && state.equals(host.legacyPatrolLogs.get(stage))) return;
        Log.record(message);
        host.legacyPatrolLogs.put(stage, state);
    }

    void queryUserPatrol() {
        String day = String.format(java.util.Locale.ROOT, "%tY%<tm%<td", MyUtils.getInstance());
        String doneFlag = "forest::legacyPatrolDone::" + day;
        if (DailyTask.skipFlag(doneFlag, "旧版保护地巡护")) return;
        try {
            AntSports sports = host.getModel(AntSports.class);
            if (sports != null) sports.syncStepNow();
            TimeUtil.sleep(0);
            AntSports.queryLegacyPatrolSteps(ApplicationHook.getClassLoader());
            Set<String> attemptedPatrolStates = new HashSet<>();
            int rounds = 0;
            th:
            do {
                if (++rounds > 100) {
                    Log.record("旧版保护地：本轮巡护达到100轮上限，后续调度继续，不标记完成");
                    return;
                }
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryUserPatrol());
                TimeUtil.sleep(500);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    if (!"RPC_SKIPPED".equals(jo.optString("error")))
                        Log.record("旧版保护地：queryUserPatrol结果未确认成功，本轮不兑换、不标记完成");
                    return;
                }
                JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.queryMyPatrolRecord());
                TimeUtil.sleep(500);
                if (resData.optBoolean("canSwitch")) {
                    JSONArray records = resData.optJSONArray("records");
                    for (int i = 0; records != null && i < records.length(); i++) {
                        JSONObject record = records.optJSONObject(i);
                        JSONObject userPatrol = record != null ? record.optJSONObject("userPatrol") : null;
                        if (userPatrol == null) {
                            continue;
                        }
                        if (userPatrol.optInt("unreachedNodeCount") > 0) {
                            if ("silent".equals(userPatrol.optString("mode"))) {
                                JSONObject patrolConfig = record.optJSONObject("patrolConfig");
                                String patrolId = patrolConfig != null ? patrolConfig.optString("patrolId") : "";
                                if (patrolId.isEmpty() || !attemptedPatrolStates.add("switch:" + patrolId)) {
                                    Log.record("旧版保护地：切图后状态未推进，本轮停止，不标记完成");
                                    return;
                                }
                                resData = MyUtils.newJSONObject(AntForestRpcCall.switchUserPatrol(patrolId));
                                TimeUtil.sleep(500);
                                if (MessageUtil.checkResultCode(TAG, resData)) {
                                    Log.i("巡护⚖️-切换地图至" + patrolId);
                                    continue th;
                                }
                                if (!"RPC_SKIPPED".equals(resData.optString("error")))
                                    Log.record("旧版保护地：switchUserPatrol结果未确认成功，本轮停止，不标记完成");
                                return;
                            }
                            break;
                        }
                    }
                }

                JSONObject userPatrol = jo.optJSONObject("userPatrol");
                if (userPatrol == null) {
                    Log.record("旧版保护地：queryUserPatrol缺少userPatrol，本轮停止，不标记完成");
                    return;
                }
                int currentNode = userPatrol.optInt("currentNode");
                String currentStatus = userPatrol.optString("currentStatus");
                int patrolId = userPatrol.optInt("patrolId");
                JSONObject chance = userPatrol.optJSONObject("chance");
                if (chance == null) {
                    Log.record("旧版保护地：queryUserPatrol缺少chance，本轮停止，不标记完成");
                    return;
                }
                int leftChance = chance.optInt("leftChance", -1);
                int leftStep = chance.optInt("leftStep", -1);
                int usedStep = chance.optInt("usedStep", -1);
                String state = patrolId + ":" + currentNode + ":" + currentStatus + ":" + leftChance + ":" + leftStep + ":" + usedStep;
                if (!attemptedPatrolStates.add(state)) {
                    Log.record("旧版保护地：操作后巡护状态未推进，本轮停止，不标记完成");
                    return;
                }
                logLegacyPatrolState("query", "旧版保护地[查询]：可用步数=" + leftStep + "，已兑换步数=" + usedStep
                        + "，剩余巡护次数=" + leftChance + "，状态="
                        + ("STANDING".equals(currentStatus) ? "站立" : "GOING".equals(currentStatus) ? "巡护中" : "未知")
                        + "，日期匹配=" + day.equals(chance.optString("dayString")));
                if ("STANDING".equals(currentStatus)) {
                    if (leftChance == 0 && usedStep >= 10000
                            && day.equals(chance.optString("dayString"))
                            && MessageUtil.checkResultCode(TAG, resData)) {
                        Status.flagToday(doneFlag);
                        Log.forest("旧版保护地：今日5次步数兑换及巡护已完成，今日不再检查");
                        Log.record("旧版保护地：今日5次步数兑换及巡护已完成，今日不再检查");
                        return;
                    }
                    if (leftChance > 0) {
                        Log.record("旧版保护地：剩余巡护次数=" + leftChance + "，先执行巡护，再检查兑换条件");
                        jo = MyUtils.newJSONObject(AntForestRpcCall.patrolGo(currentNode, patrolId));
                        TimeUtil.sleep(500);
                        if (!MessageUtil.checkResultCode(TAG, jo)) {
                            if (!"RPC_SKIPPED".equals(jo.optString("error")))
                                Log.record("旧版保护地：patrolGo结果未确认成功，本轮停止，不标记完成");
                            return;
                        }
                        patrolKeepGoing(jo.toString(), currentNode, patrolId);
                        continue;
                    } else if (leftChance == 0 && leftStep >= 2000 && usedStep >= 0 && usedStep < 10000) {
                        jo = MyUtils.newJSONObject(AntForestRpcCall.exchangePatrolChance(leftStep));
                        TimeUtil.sleep(300);
                        if (MessageUtil.checkResultCode(TAG, jo)) {
                            int addedChance = jo.optInt("addedChance", 0);
                            Log.forest("步数兑换⚖️[巡护次数*" + addedChance + "]");
                            Log.record("旧版保护地[步数兑换]：exchangePatrolChance成功，提交步数=" + leftStep
                                    + "，实际扣除步数=" + jo.optInt("costStep", -1) + "，新增巡护次数=" + addedChance
                                    + "，继续查询并巡护（-1表示响应缺少该字段）");
                            continue;
                        }
                        if (!"RPC_SKIPPED".equals(jo.optString("error")))
                            Log.record("旧版保护地[步数兑换]：exchangePatrolChance结果未确认成功，提交步数=" + leftStep + "，不标记完成");
                    } else {
                        logLegacyPatrolState("skip", "旧版保护地[未兑换]：" + (leftChance < 0 || leftStep < 0 || usedStep < 0
                                ? "服务器次数或步数字段缺失/无效" : usedStep >= 10000
                                ? "今日已用满10000步兑换额度，但日期或记录未确认，不标记完成"
                                : "保护地可用步数不足2000，后续轮次继续查询，不标记完成"));
                    }
                } else if ("GOING".equals(currentStatus)) {
                    Log.record("旧版保护地：当前巡护进行中，继续推进，不因未兑换而停止巡护");
                    patrolKeepGoing(null, currentNode, patrolId);
                    continue;
                } else {
                    Log.record("旧版保护地：巡护状态未知，本轮不兑换、不标记完成");
                }
                break;
            } while (true);
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "queryUserPatrol err:", t);
        }
    }

    void patrolKeepGoing(String s, int nodeIndex, int patrolId) {
        try {
            Set<String> attemptedEvents = new HashSet<>();
            int rounds = 0;
            do {
                if (++rounds > 200) {
                    Log.record("旧版保护地：前行达到200次上限，后续调度继续，不标记完成");
                    return;
                }
                if (s == null) {
                    s = AntForestRpcCall.patrolKeepGoing(nodeIndex, patrolId, "image");
                }
                JSONObject jo = MyUtils.newJSONObject(s);
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                JSONArray jaEvents = jo.optJSONArray("events");
                if (jaEvents == null || jaEvents.length() == 0) {
                    return;
                }
                JSONObject userPatrol = jo.optJSONObject("userPatrol");
                int currentNode = userPatrol != null ? userPatrol.optInt("currentNode") : nodeIndex;
                JSONObject events = jaEvents.optJSONObject(0);
                if (events == null) {
                    return;
                }
                JSONObject rewardInfo = events.optJSONObject("rewardInfo");
                if (rewardInfo != null) {
                    JSONObject animalProp = rewardInfo.optJSONObject("animalProp");
                    if (animalProp != null) {
                        JSONObject animal = animalProp.optJSONObject("animal");
                        if (animal != null) {
                            Log.forest("巡护森林🏇🏻[" + animal.optString("name") + "碎片]");
                        }
                    }
                }
                if (!"GOING".equals(jo.optString("currentStatus"))) {
                    return;
                }
                JSONObject materialInfo = events.optJSONObject("materialInfo");
                String materialType = materialInfo != null ? materialInfo.optString("materialType", "image") : "image";
                // 同节点的 material→reward 是正常推进，不能仅按素材类型判重复。
                String eventState = currentNode + ":" + materialType + ":" + events.optString("eventType")
                        + ":" + events.optString("reacted") + ":" + (materialInfo != null ? materialInfo.optString("materialId") : "");
                if (!attemptedEvents.add(eventState)) {
                    Log.record("旧版保护地：patrolKeepGoing节点事件未推进，本轮停止，不标记完成");
                    return;
                }
                s = AntForestRpcCall.patrolKeepGoing(currentNode, patrolId, materialType);
                TimeUtil.sleep(100);
            } while (true);
        } catch (Throwable t) {
            Log.err(TAG, "patrolKeepGoing err:", t);
        }
    }

    /* 新版保护地：遍历所有已开放的保护地，逐区域巡护（解决多个保护地并行时的覆盖问题） */
    void monopolyPatrol() {
        long generation = TaskLifecycle.generation();
        String uid = UserIdMap.getCurrentUid();
        String originalRegion = "", activeRegion = "";
        boolean switchedRegion = false, cancelled = false;
        try {
            JSONObject mapPage = queryMonopolyMapPage();
            if (host.monopolyExchangeCertificate.getValue()) exchangeMonopolyCertificate(mapPage);
            if (mapPage == null) {
                Log.record("新版保护地：地图查询失败，停止本轮多区域巡护");
                return;
            }
            Object regions = mapPage.opt("regionList");
            if (regions == null || regions == JSONObject.NULL) {
                patrolMonopolyRegion("", true);
                return;
            }
            if (!(regions instanceof JSONArray)) {
                Log.record("新版保护地：区域列表格式无效，停止本轮巡护");
                return;
            }
            JSONArray regionList = (JSONArray) regions;
            if (regionList.length() == 0) {
                patrolMonopolyRegion("", true);
                return;
            }
            for (int i = 0; i < regionList.length(); i++) {
                JSONObject region = regionList.optJSONObject(i);
                if (region != null && Boolean.TRUE.equals(region.opt("currentRegion"))
                        && region.opt("regionCode") instanceof String) {
                    String current = region.optString("regionCode").trim();
                    if (!activeRegion.isEmpty() && !activeRegion.equals(current)) {
                        Log.record("新版保护地：当前区域不唯一，停止切换");
                        return;
                    }
                    activeRegion = current;
                }
            }
            if (activeRegion.isEmpty()) activeRegion = getMonopolyRegionCode();
            if (activeRegion.isEmpty()) {
                Log.record("新版保护地：无法确认原区域，不切换，重新读取当前区域巡护");
                patrolMonopolyRegion("", true);
                return;
            }
            originalRegion = activeRegion;
            Set<String> visited = new HashSet<>();
            for (int i = 0; i < regionList.length(); i++) {
                if (Thread.currentThread().isInterrupted() || RunGeneration.isStale()
                        || !Objects.equals(uid, UserIdMap.getCurrentUid())) throw new TaskCancelledException();
                JSONObject region = regionList.optJSONObject(i);
                Object code = region == null ? null : region.opt("regionCode");
                if (!(code instanceof String) || ((String) code).trim().isEmpty()) continue;
                String regionCode = ((String) code).trim();
                if ("PREPARING".equals(region.optString("regionAvailability"))
                        || isMonopolyClosed(region) || !visited.add(regionCode)) continue;
                if (!regionCode.equals(activeRegion)) {
                    switchedRegion = true;
                    JSONObject switched = monopolyResponse(AntForestRpcCall.switchMonopolyRegion(regionCode));
                    if (!MessageUtil.checkResultCode(TAG, switched)) {
                        Log.record("新版保护地：切换失败，区域=" + regionCode + "，停止本轮以免沿用旧区域");
                        return;
                    }
                    activeRegion = regionCode;
                }
                String regionName = region.optString("regionName", "");
                JSONObject displayInfo = region.optJSONObject("displayInfo");
                if (regionName.isEmpty() && displayInfo != null) regionName = displayInfo.optString("regionNameText", "");
                Log.record("新版保护地🌲开始巡护[" + (regionName.isEmpty() ? regionCode : regionName) + "]");
                patrolMonopolyRegion(regionCode, regionCode.equals(originalRegion));
            }
        } catch (TaskCancelledException e) { cancelled = true; throw e;
        } catch (Throwable t) {
            Log.err(TAG, "monopolyPatrol err:", t);
        } finally {
            if (switchedRegion && !originalRegion.isEmpty()) {
                if (cancelled || Thread.currentThread().isInterrupted() || RunGeneration.isStale()
                        || ApplicationHook.isOffline() || !Objects.equals(uid, UserIdMap.getCurrentUid())) {
                    Log.record("新版保护地：本轮已中止或账号变化，不再发送恢复区域请求，原区域=" + originalRegion);
                } else {
                    try (TaskLifecycle.Work work = TaskLifecycle.enter(generation)) {
                        if (work == null) {
                            Log.record("新版保护地：账号生命周期已变化，不再恢复旧区域");
                        } else if (originalRegion.equals(activeRegion) && originalRegion.equals(getMonopolyRegionCode())) {
                            Log.record("新版保护地：回查仍在原区域[" + originalRegion + "]，无需恢复");
                        } else {
                            JSONObject restored = monopolyResponse(AntForestRpcCall.switchMonopolyRegion(originalRegion));
                            boolean accepted = MessageUtil.checkResultCode(TAG, restored);
                            String currentRegion = accepted ? getMonopolyRegionCode() : "";
                            if (accepted && originalRegion.equals(currentRegion)) {
                                Log.record("新版保护地：已恢复原区域[" + originalRegion + "]");
                            } else {
                                Log.record("新版保护地：恢复原区域未确认，目标=" + originalRegion
                                        + "，当前=" + currentRegion + "，code=" + restored.optString("resultCode", "缺失"));
                            }
                        }
                    } catch (TaskCancelledException e) { throw e;
                    } catch (Throwable t) {
                        Log.record("新版保护地：恢复原区域请求异常，结果未确认");
                        Log.err(TAG, "restoreMonopolyRegion err:", t);
                    }
                }
            }
        }
    }

    JSONObject queryMonopolyMapPage() throws JSONException {
        try {
            JSONObject response = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyMapPage());
            JSONObject data = response.optJSONObject("resData");
            if (response.has("resData") && data == null) {
                Log.record("新版保护地：地图响应封装格式无效，停止本轮");
                return null;
            }
            boolean nestedFailure = data != null && data.length() > 0 && RpcRequestGuard.isFailure(data);
            if (RpcRequestGuard.isFailure(response) || nestedFailure) {
                MessageUtil.checkResultCode(TAG, nestedFailure ? data : response);
                return null;
            }
            // 有的通道仅外层携带成功状态，不能解包后把它当作无效响应。
            JSONObject status = response.has("resultCode") ? response : data;
            if (status == null || !MessageUtil.checkResultCode(TAG, status)) return null;
            return data == null ? response : data;
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.record("新版保护地：地图查询异常，未取得有效地图");
            Log.err(TAG, "queryMonopolyMapPage err:", t);
            return null;
        }
    }

    /* 每次切换后先核对入口区域，再领取机会、做任务和掷骰。 */
    void patrolMonopolyRegion(String expectedRegion, boolean certificateHandled) {
        try {
            JSONObject state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            JSONObject mapInfo = state.optJSONObject("mapInfo");
            JSONObject userInfo = state.optJSONObject("userInfo");
            if (regionInfo == null || mapInfo == null || userInfo == null) {
                Log.record("新版保护地🌲入口缺少区域、地图或用户状态，跳过本轮");
                return;
            }
            if (isMonopolyClosed(regionInfo) || isMonopolyClosed(mapInfo)) {
                Log.record("新版保护地🌲当前区域或地图不在开放期，停止本轮");
                return;
            }
            Object region = regionInfo.opt("regionCode");
            if (!(region instanceof String) || ((String) region).trim().isEmpty()) {
                Log.record("新版保护地：入口区域码缺失或无效，停止本轮");
                return;
            }
            String regionCode = ((String) region).trim();
            if (!expectedRegion.isEmpty() && !expectedRegion.equals(regionCode)) {
                Log.record("新版保护地：切换回查未确认，目标=" + expectedRegion + "，当前=" + regionCode);
                return;
            }
            boolean tasksEnabled = host.monopolyTasks.getValue();
            // 掷骰会解锁新任务，所以骰子用完后需要再补跑一次任务
            boolean tasksMayHaveChanged = false;
            if (tasksEnabled) {
                monopolyTask(regionCode);
            }
            if (host.monopolyExchangeCertificate.getValue() && !certificateHandled) {
                exchangeMonopolyCertificate();
            }
            // 首次进入地图时服务端要求带引导参数掷骰
            boolean guideRoll = userInfo.optBoolean("firstEnterMonopoly");
            JSONObject pendingEvent = state.optJSONObject("eventInfo");

            // 首页道具：领取当日巡护机会（骰子）
            JSONObject props = MyUtils.newJSONObject(AntForestRpcCall.triggerMonopolyHomeProps());
            if (!MessageUtil.checkResultCode(TAG, props)) {
                return;
            }
            state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return;
            }
            if (pendingEvent == null) {
                pendingEvent = state.optJSONObject("eventInfo");
            }

            Set<String> confirmedEvents = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                JSONObject event = pendingEvent;
                if (event != null && event.optBoolean("needConfirm")) {
                    String eventId = event.optString("eventId");
                    String eventType = event.optString("eventType");
                    String actionKey = "CHARITY".equals(eventType) ? "confirm" : ("SPECIAL".equals(eventType) ? "skip" : null);
                    JSONObject displayInfo = event.optJSONObject("displayInfo");
                    if (eventId.isEmpty() || actionKey == null || displayInfo == null
                            || !"SINGLE_ACTION_CONFIRM".equals(displayInfo.optString("flowType"))) {
                        Log.record("新版保护地🌲事件缺少可执行决策，保留当前事件[" + eventId + "]");
                        return;
                    }
                    if (confirmedEvents.contains(eventId)) {
                        Log.record("新版保护地🌲事件[" + eventId + "]已提交确认，等待后续调度刷新");
                        return;
                    }
                    JSONObject confirmation = MyUtils.newJSONObject(AntForestRpcCall.confirmMonopolyEvent(eventId, actionKey));
                    if (!MessageUtil.checkResultCode(TAG, confirmation)) {
                        return;
                    }
                    confirmedEvents.add(eventId);
                    String eventTitle = displayInfo.optString("titleText", displayInfo.optString("title", ""));
                    if (eventTitle.isEmpty()) eventTitle = "CHARITY".equals(eventType) ? "公益事件" : "特殊事件";
                    Log.forest("新版保护地🌲" + eventTitle + ("confirm".equals(actionKey) ? "已确认" : "已跳过"));
                    state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                    if (!MessageUtil.checkResultCode(TAG, state)) {
                        return;
                    }
                    pendingEvent = state.optJSONObject("eventInfo");
                    continue;
                }

                int diceCount = state.optInt("totalDiceCount", -1);
                if (diceCount < 0) {
                    Log.record("新版保护地🌲响应缺少可用骰子数量，跳过本轮");
                    return;
                }
                if (diceCount == 0) {
                    if (tasksEnabled && tasksMayHaveChanged) {
                        monopolyTask(regionCode);
                        tasksMayHaveChanged = false;
                        state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                        if (!MessageUtil.checkResultCode(TAG, state)) {
                            return;
                        }
                        pendingEvent = state.optJSONObject("eventInfo");
                        continue;
                    }
                    Log.record("新版保护地🌲当前无可用骰子，后续调度继续查询");
                    return;
                }

                JSONObject currentUserInfo = state.optJSONObject("userInfo");
                int previousSteps = currentUserInfo == null ? -1 : currentUserInfo.optInt("stepCount", -1);
                JSONObject roll = MyUtils.newJSONObject(AntForestRpcCall.rollMonopolyDice(guideRoll));
                if (!MessageUtil.checkResultCode(TAG, roll)) {
                    return;
                }
                guideRoll = false;
                pendingEvent = roll.optJSONObject("eventInfo");
                JSONObject rollUserInfo = roll.optJSONObject("userInfo");
                int steps = rollUserInfo == null ? -1 : rollUserInfo.optInt("stepCount", -1);
                // 骰子数、步数、事件三者都没变化，说明服务端没受理，留到下次调度重试
                if (roll.optInt("totalDiceCount", -1) == diceCount && steps == previousSteps && pendingEvent == null) {
                    Log.record("新版保护地🌲掷骰后未确认状态变化，保留后续调度");
                    return;
                }
                tasksMayHaveChanged = true;
                Log.forest("新版保护地🌲掷骰[" + roll.optInt("diceNumber") + "]剩余" + roll.optInt("totalDiceCount") + "次");
                TimeUtil.sleep(500);
                state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
                if (!MessageUtil.checkResultCode(TAG, state)) {
                    return;
                }
                if (pendingEvent == null) {
                    pendingEvent = state.optJSONObject("eventInfo");
                }
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "patrolMonopolyRegion err:", t);
        }
    }

    /* 新版保护地：区域/地图是否未开放或已结束（缺少日期字段视为一直开放） */
    boolean isMonopolyClosed(JSONObject activity) {
        long now = System.currentTimeMillis();
        long start = parseMonopolyDate(activity.optString("startDate"));
        long end = parseMonopolyDate(activity.optString("endDate"));
        return (start > 0 && now < start) || (end > 0 && now >= end);
    }

    /* 解析新版保护地的 "yyyy-MM-dd HH:mm:ss" 时间串（东八区），解析失败返回 0 */
    long parseMonopolyDate(String date) {
        if (StringUtil.isEmpty(date)) {
            return 0L;
        }
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            format.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            format.setLenient(false);
            Date parsed = format.parse(date);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    /* 新版保护地部分接口的响应会被包在 resData 里 */
    JSONObject monopolyResponse(String raw) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(raw);
        JSONObject resData = jo.optJSONObject("resData");
        return resData == null ? jo : resData;
    }

    /** 当前保护地区域码：由 entryInfo 动态下发，区域轮换无需改代码；取不到返回空串 */
    String getMonopolyRegionCode() {
        try {
            JSONObject state = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, state)) {
                return "";
            }
            JSONObject regionInfo = state.optJSONObject("regionInfo");
            Object region = regionInfo == null ? null : regionInfo.opt("regionCode");
            return region instanceof String ? ((String) region).trim() : "";
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "getMonopolyRegionCode err:", t);
            return "";
        }
    }

    /* 新版保护地：完成可自动执行的任务并领取奖励 */
    boolean monopolyTask(String regionCode) {
        // sceneCode 为跨保护地区域固定的任务场景标识；regionCode 由 entryInfo 动态下发(区域轮换无需改代码)。
        // 服务端按 regionCode 返回该区域任务,请求带固定 sceneCode 即可,拿不到任务时按空列表跳过。
        String sceneCode = AntForestV2.MONOPOLY_TASK_SCENE_HSDWY;
        boolean changed = false;
        Set<String> attempted = new HashSet<>();
        try {
            // 同一任务快照本轮只尝试一次；状态或计数推进后仍可继续，最多20轮。
            for (int round = 0; round < 20; round++) {
                JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, sceneCode));
                if (!MessageUtil.checkSuccess(TAG, jo)) {
                    return changed;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null) {
                    Log.record("新版保护地🌲任务列表为空，跳过");
                    return changed;
                }
                boolean acted = false, repeated = false;
                for (int i = 0; i < taskInfoList.length() && !acted; i++) {
                    JSONObject task = taskInfoList.optJSONObject(i);
                    JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                    // 用任务自带的真实 sceneCode 做完成/领奖(服务端按 regionCode 返回,sceneCode 以任务为准)
                    Object scene = base == null ? null : base.opt("sceneCode");
                    if (!(scene instanceof String) || ((String) scene).trim().isEmpty()) {
                        continue;
                    }
                    String taskSceneCode = (String) scene;
                    Object type = base.opt("taskType");
                    if (!(type instanceof String) || ((String) type).trim().isEmpty()) continue;
                    String taskType = (String) type;
                    String title = monopolyTaskTitle(base, taskType);
                    String status = base.optString("taskStatus");
                    // 黑名单任务静默跳过；已完成待领奖的仍把奖励领掉（与森林任务一致）
                    if (isMonopolyTaskBlacklisted(title) && !"FINISHED".equals(status)) {
                        continue;
                    }
                    boolean actionable = "FINISHED".equals(status) || "TODO".equals(status)
                            && "NORMAL".equals(base.optString("taskMode"))
                            && "VISIT_FLOAT_BALL".equals(base.optString("taskProdPlayType"));
                    if (!actionable) continue;
                    if (!attempted.add(base.toString())) {
                        repeated = true;
                        continue;
                    }
                    if ("FINISHED".equals(status)) {
                        acted = true;
                        changed |= monopolyReceiveTaskAward(regionCode, taskType, taskSceneCode, title);
                    } else if ("TODO".equals(status) && "NORMAL".equals(base.optString("taskMode"))
                            && "VISIT_FLOAT_BALL".equals(base.optString("taskProdPlayType"))) {
                        // 只自动做"浏览浮球"这类纯等待任务，其余需要真实业务动作的任务不碰
                        acted = true;
                        changed |= monopolyFinishTask(base, taskType, taskSceneCode, title);
                    }
                }
                if (!acted) {
                    if (repeated) Log.record("新版保护地：本轮已尝试的任务状态未推进，后续轮次回查");
                    return changed;
                }
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "monopolyTask err:", t);
        }
        return changed;
    }

    /* 新版保护地任务展示名：bizInfo.title → bizInfo.taskTitle → 任务类型（各接口键名不统一） */
    String monopolyTaskTitle(JSONObject base, String taskType) {
        String bizInfo = base.optString("bizInfo");
        if (!bizInfo.isEmpty()) {
            try {
                JSONObject biz = MyUtils.newJSONObject(bizInfo);
                String title = biz.optString("title");
                if (title.isEmpty()) {
                    title = biz.optString("taskTitle");
                }
                if (!title.isEmpty()) {
                    return title;
                }
            } catch (Throwable t) {
                Log.err(TAG, "monopolyTaskTitle err:", t);
            }
        }
        return taskType;
    }

    /* 新版保护地任务是否在配置的黑名单里（键统一剥掉标题末尾的 "(n/N)" 次数后缀） */
    boolean isMonopolyTaskBlacklisted(String title) {
        Set<String> blackList = host.MonopolyTaskList.getValue();
        return blackList != null && blackList.contains(AntForestV2.blackTaskKey(title));
    }

    /* 失败响应按统一判据自动拉黑（可重试错误不会拉黑），受「自动黑名单」开关控制 */
    void markMonopolyTaskBlackList(String title, JSONObject jo) {
        if (Boolean.TRUE.equals(host.AutoMonopolyTaskList.getValue())) {
            MessageUtil.checkResultCodeAndMarkTaskBlackList("MonopolyTaskList", AntForestV2.blackTaskKey(title), jo);
        }
    }

    /* 新版保护地：按服务端下发的时长等待后提交任务完成 */
    boolean monopolyFinishTask(JSONObject base, String taskType, String sceneCode, String title) {
        try {
            if (TaskAlternative.isTransactionTask(taskType)) {
                String skipFlag = "transactionSkip::monopoly::" + sceneCode + "::" + taskType;
                if (!Status.hasFlagToday(skipFlag)) {
                    Status.flagToday(skipFlag);
                    Log.forest("新版保护地🌲⏭️交易/履约类[" + title + "]#不申报，不修改黑名单");
                }
                return false;
            }
            long seconds = 0L;
            String prodPlayParam = base.optString("prodPlayParam");
            if (!prodPlayParam.isEmpty()) {
                seconds = MyUtils.newJSONObject(prodPlayParam).optLong("timeCount", 0L);
            }
            if (seconds <= 0 || seconds > AntForestV2.MONOPOLY_MAX_TASK_SECONDS) {
                Log.record("新版保护地🌲任务[" + title + "]下发时长异常(" + seconds + "s)，跳过");
                return false;
            }
            TimeUtil.sleep(TimeUnit.SECONDS.toMillis(seconds));
            JSONObject jo = monopolyResponse(AntForestRpcCall.finishMonopolyTask(taskType, sceneCode));
            markMonopolyTaskBlackList(title, jo);
            // 不支持rpc调用（400000040）→ 换另一种实现方案（doFarmTask 伪申报）；
            // 响应不可信，成败仍以任务列表为准（下一轮列表 FINISHED 即去领奖）
            if (TaskAlternative.hit(jo, sceneCode)) {
                // 本家值：保护地任务场景 + ANTFOREST
                TaskAlternative.trigger(null, null, title, taskType, sceneCode,
                        TaskAlternative.DEFAULT_VERSION, sceneCode, "ANTFOREST",
                        "新版保护地🌲", Log::forest);
                return true;
            }
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("新版保护地🌲任务完成[" + title + "]");
                return true;
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "monopolyFinishTask err:", t);
        }
        return false;
    }

    /* 新版保护地：领取任务奖励 */
    boolean monopolyReceiveTaskAward(String regionCode, String taskType, String sceneCode, String title) {
        try {
            // 领奖响应没有明确的能量到账量字段，只能用账户余额差值估算真实到账量
            int energyBefore = queryCurrentEnergy();
            JSONObject jo = monopolyResponse(AntForestRpcCall.receiveMonopolyTask(taskType, sceneCode));
            boolean ok = MessageUtil.checkSuccess(TAG, jo);
            // 领奖收口：先按任务列表复核"已领到"，未确认才交自动拉黑（顺序由 TaskAward 固定）
            if (!ok && TaskAward.confirmReceivedOrBlackList("新版保护地🌲任务奖励",
                    k -> probeMonopolyStatus(regionCode, taskType, sceneCode), taskType, title,
                    () -> markMonopolyTaskBlackList(title, jo),
                    msg -> Log.forest(msg))) {
                recordMonopolyAward(title, energyBefore);
                return true;
            }
            if (ok) {
                markMonopolyTaskBlackList(title, jo);
                Log.forest("新版保护地🌲任务奖励[" + title + "]");
                recordMonopolyAward(title, energyBefore);
                return true;
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "monopolyReceiveTaskAward err:", t);
        }
        return false;
    }

    /**
     * 领取保护地奖励后按账户能量余额差值记账。
     *
     * <p>保护地领奖响应没有可直接读取的到账量字段（不同于森林集市的 {@code receivedEnergyAmount} /
     * 绿色租赁的 {@code zulinshangpinliulan}），因此以领奖前后 {@code currentEnergy} 的差值作为
     * 实际到账量。差值需为正才计入，避免服务端并发扣减导致负数误记。
     *
     * @param energyBefore 领奖前查询到的能量余额；{@code < 0} 表示查询失败，放弃记账
     */
    void recordMonopolyAward(String title, int energyBefore) {
        if (energyBefore < 0) {
            return;
        }
        int energyAfter = queryCurrentEnergy();
        if (energyAfter < 0) {
            return;
        }
        int gained = energyAfter - energyBefore;
        if (gained <= 0) {
            return;
        }
        Log.forest("新版保护地🌲任务奖励[" + title + "]到账[" + gained + "g能量]");
        AntForestV2.totalCollected += gained;
        Statistics.addData(Statistics.DataType.COLLECTED, gained);
    }

    /**
     * 查询账户当前能量余额（{@code userBaseInfo.currentEnergy}）。
     *
     * @return 余额；查询失败返回 {@code -1}
     */
    int queryCurrentEnergy() {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryHomePage());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return -1;
            }
            JSONObject userBaseInfo = jo.optJSONObject("userBaseInfo");
            if (userBaseInfo == null || !userBaseInfo.has("currentEnergy")) {
                return -1;
            }
            return userBaseInfo.optInt("currentEnergy", -1);
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "queryCurrentEnergy err:", t);
            return -1;
        }
    }

    /* 从原任务列表按 taskType 与任务场景共同回查，避免跨场景误判消失或已领。 */
    TaskAttemptPolicy.ProbeResult probeMonopolyStatus(String regionCode, String taskType, String sceneCode) {
        try {
            JSONObject jo = monopolyResponse(AntForestRpcCall.listMonopolyTasks(regionCode, AntForestV2.MONOPOLY_TASK_SCENE_HSDWY));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
            if (taskInfoList == null) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            for (int i = 0; i < taskInfoList.length(); i++) {
                JSONObject task = taskInfoList.optJSONObject(i);
                JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                if (base == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                Object returnedType = base.opt("taskType");
                Object returnedScene = base.opt("sceneCode");
                if (!(returnedType instanceof String) || ((String) returnedType).trim().isEmpty()
                        || !(returnedScene instanceof String) || ((String) returnedScene).trim().isEmpty()) {
                    return TaskAttemptPolicy.ProbeResult.UNKNOWN;
                }
                if (!taskType.equals(returnedType) || !sceneCode.equals(returnedScene)) {
                    continue;
                }
                String status = base.optString("taskStatus");
                if ("FINISHED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.FINISHED;
                }
                if ("RECEIVED".equals(status)) {
                    return TaskAttemptPolicy.ProbeResult.RECEIVED;
                }
                return "TODO".equals(status) ? TaskAttemptPolicy.ProbeResult.TODO : TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            // 任务已从列表消失：视为已完成且已领
            return TaskAttemptPolicy.ProbeResult.GONE;
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "probeMonopolyStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    /* 新版动物伙伴：领取已产生的派遣能量 */
    void collectMonopolyAnimalEnergy() {
        try {
            String uid = UserIdMap.getCurrentUid();
            if (StringUtil.isEmpty(uid)) {
                return;
            }
            JSONObject jo = monopolyResponse(AntForestRpcCall.queryUsingCreatureInfo(uid));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONObject creature = jo.optJSONObject("userCreatureVO");
            if (creature == null) {
                return;
            }
            JSONObject energy = creature.optJSONObject("robEnergyVO");
            if (energy == null) {
                Log.record("新版动物伙伴🦩缺少能量状态，跳过");
                return;
            }
            if (energy.optBoolean("energyIsCollect")) {
                return;
            }
            if (energy.optInt("yesterdayRobEnergy", 0) <= 0) {
                // 昨日没有产出能量，属于正常情况，不打扰用户
                return;
            }
            String creatureCode = creature.optString("creatureCode");
            String shortDay = energy.optString("yesterdayShortDay");
            if (!energy.has("energyIsCollect") || creatureCode.isEmpty() || shortDay.isEmpty()) {
                Log.record("新版动物伙伴🦩能量缺少领取标识，跳过");
                return;
            }
            JSONObject collect = monopolyResponse(AntForestRpcCall.collectMonopolyCreatureEnergy(creatureCode, shortDay));
            if (!MessageUtil.checkResultCode(TAG, collect)) {
                return;
            }
            int collected = collect.optInt("collectedEnergy", -1);
            if (collected < 0) {
                Log.forest("新版动物伙伴🦩领取成功但缺少实际到账量，不计入统计");
                return;
            }
            if (collected > 0) {
                Statistics.addData(Statistics.DataType.COLLECTED, collected);
            }
            Log.forest("新版动物伙伴🦩[" + creature.optString("creatureName", creatureCode) + "]派遣能量[" + collected + "g]");
        } catch (Throwable t) {
            Log.err(TAG, "collectMonopolyAnimalEnergy err:", t);
        }
    }

    void exchangeMonopolyCertificate() {
        exchangeMonopolyCertificate(null);
    }

    void exchangeMonopolyCertificate(JSONObject knownMapPage) {
        try {
            TimeUtil.sleep(0);
            JSONObject entry = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!"SUCCESS".equals(entry.optString("resultCode")) || RpcRequestGuard.isFailure(entry)) { Log.record("保护证书：入口查询失败，" + RpcRequestGuard.errorMessage(entry)); return; }
            JSONObject display = entry.optJSONObject("displayInfo");
            JSONObject map = display == null ? null : display.optJSONObject("mapDisplay");
            JSONObject guide = map == null ? null : map.optJSONObject("protectionGuideDisplay");
            String url = guide == null ? "" : guide.optString("redirectUrl");
            if (url.isEmpty()) {
                // 首次地图失败不作缓存，仅证书缺少入口时独立重查一次。
                JSONObject page = knownMapPage != null ? knownMapPage : queryMonopolyMapPage();
                if (page == null) { Log.record("保护证书：地图查询失败，停止兑换"); return; }
                JSONArray maps = page.optJSONArray("currentRegionMapList");
                if (maps == null) { Log.record("保护证书：地图列表缺失，停止兑换"); return; }
                JSONObject candidate = null;
                for (int i = 0; i < maps.length(); i++) {
                    JSONObject current = maps.optJSONObject(i);
                    if (current == null || !Boolean.TRUE.equals(current.opt("currentMap"))
                            || !"PENDING".equals(current.optString("certificateStatus"))) continue;
                    if (candidate != null) { Log.record("保护证书：当前地图不唯一，停止兑换"); return; }
                    candidate = current;
                }
                JSONObject currentDisplay = candidate == null ? null : candidate.optJSONObject("mapDisplay");
                url = currentDisplay == null ? "" : currentDisplay.optString("lockedRedirectUrl", "");
            }
            if (url == null || url.isEmpty()) { Log.record("保护证书：当前地图没有证书项目"); return; }
            String id = monopolyCertificateProjectId(url);
            if (id == null || !id.matches("[0-9]+")) { Log.record("保护证书：入口缺少有效项目编号，跳过"); return; }
            JSONObject before = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyCertificate(id));
            if (!"SUCCESS".equals(before.optString("resultCode")) || RpcRequestGuard.isFailure(before)) { Log.record("保护证书：资格查询失败，" + RpcRequestGuard.errorMessage(before)); return; }
            JSONObject project = before.optJSONObject("exchangeableTree");
            if (project == null || AntForestV2.forestFeatureLong(project, "projectId") != Long.parseLong(id)) { Log.record("保护证书：项目数据缺失或不符，跳过"); return; }
            long certCount = AntForestV2.forestFeatureLong(project, "certCount");
            if (certCount < 0) { Log.record("保护证书：缺少持有数量，跳过"); return; }
            if (!"AVAILABLE".equals(before.optString("applyAction")) || certCount > 0) { Log.record("保护证书：已持有或当前不可兑换"); return; }
            long cost = AntForestV2.forestFeatureLong(project, "energy"), balance = AntForestV2.forestFeatureLong(before, "currentEnergy");
            if (cost < 0 || balance < cost || project.optBoolean("overLimit") || !project.optBoolean("hasBudget", true)) {
                Log.record("保护证书：能量或额度不足，成本=" + cost + "g，余额=" + balance + "g"); return;
            }
            String flag = "forest::certificateUnconfirmed::" + id;
            if (Status.hasFlagToday(flag)) { Log.record("保护证书：本日已提交但结果未确认，避免重复扣能量"); return; }
            // 请求发出后即占用一次预算；响应丢失时靠回查确认，不重复提交。
            Status.flagToday(flag);
            JSONObject exchanged = MyUtils.newJSONObject(AntForestRpcCall.exchangeMonopolyCertificate(Long.parseLong(id)));
            JSONObject after = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyCertificate(id));
            JSONObject afterProject = after.optJSONObject("exchangeableTree");
            if ("SUCCESS".equals(after.optString("resultCode")) && !RpcRequestGuard.isFailure(after) && afterProject != null && AntForestV2.forestFeatureLong(afterProject, "projectId") == Long.parseLong(id)
                    && AntForestV2.forestFeatureLong(afterProject, "certCount") > certCount) {
                String message = "保护证书：兑换成功[" + project.optString("projectName", "当前地图") + "]，成本=" + cost + "g";
                Log.record(message); Log.forest(message);
            } else { Log.record("保护证书：兑换未确认，code=" + exchanged.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(exchanged)); }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("保护证书：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "exchangeMonopolyCertificate", t); }
    }

    static String monopolyCertificateProjectId(String url) {
        for (int depth = 0; depth < 4 && url != null && !url.isEmpty(); depth++) {
            android.net.Uri uri = android.net.Uri.parse(url);
            String id = uri.getQueryParameter("projectId");
            if (id != null) return id.matches("[0-9]+") ? id : "";
            url = uri.getQueryParameter("url");
        }
        return "";
    }

    void waterMemberPlant() {
        try {
            JSONObject response = MyUtils.newJSONObject(AntForestRpcCall.memberForestSignin("plant_black_v2"));
            // ponytail: 每轮最多50次浇水；需要更大额度时由下一轮继续。
            for (int round = 0; round < 50; round++) {
                TimeUtil.sleep(0);
                if (!Boolean.TRUE.equals(response.opt("success")) || !"SUCCESS".equals(response.optString("resultCode"))) {
                    Log.record("会员绿植：状态查询失败，" + RpcRequestGuard.errorMessage(response)); return;
                }
                JSONObject data = response.optJSONObject("resultData");
                JSONObject channel = data == null ? null : data.optJSONObject("plantChannelResponse");
                JSONObject plant = data == null ? null : data.optJSONObject("plantInfo");
                if (channel == null || !Boolean.TRUE.equals(channel.opt("access")) || plant == null || !"in_progress".equals(plant.optString("status"))) {
                    Log.record("会员绿植：活动未开通或当前没有待浇水绿植"); return;
                }
                JSONObject info = plant.optJSONObject("plantInfoMap");
                JSONObject water = info == null ? null : info.optJSONObject("WATER");
                JSONObject pot = info == null ? null : info.optJSONObject("WATERING_POT");
                JSONObject ext = pot == null ? null : pot.optJSONObject("extInfo");
                JSONObject progress = info == null ? null : info.optJSONObject("PLANT_PROGRESS_V2");
                progress = progress == null ? null : progress.optJSONObject("extInfo");
                if (water == null || ext == null || progress == null) { Log.record("会员绿植：缺少水滴或进度数据，停止"); return; }
                long cost = AntForestV2.forestFeatureLong(ext, "costPerWater"), balance = AntForestV2.forestFeatureLong(water, "value"), before = AntForestV2.forestFeatureLong(progress, "totalWaterG");
                if (cost <= 0 || balance < 0 || before < 0) { Log.record("会员绿植：水滴或成本数据无效，停止"); return; }
                JSONArray pots = new JSONArray(ext.optString("availablePots", "[]"));
                boolean five = false, one = false;
                for (int i = 0; i < pots.length(); i++) { five |= "five".equals(pots.optString(i)); one |= "one".equals(pots.optString(i)); }
                String speed = five && balance / cost >= 5 ? "five" : one && balance >= cost ? "one" : "";
                if (speed.isEmpty()) { Log.record("会员绿植：水滴不足或无可用浇水档位，结束"); return; }
                JSONObject awarded = MyUtils.newJSONObject(AntForestRpcCall.plantAward(speed));
                response = MyUtils.newJSONObject(AntForestRpcCall.memberForestSignin(water.optString("darwinVersion", "plant_black_v2")));
                JSONObject afterData = response.optJSONObject("resultData");
                JSONObject afterPlant = afterData == null ? null : afterData.optJSONObject("plantInfo");
                JSONObject afterInfo = afterPlant == null ? null : afterPlant.optJSONObject("plantInfoMap");
                JSONObject afterProgress = afterInfo == null ? null : afterInfo.optJSONObject("PLANT_PROGRESS_V2");
                afterProgress = afterProgress == null ? null : afterProgress.optJSONObject("extInfo");
                JSONObject afterWater = afterInfo == null ? null : afterInfo.optJSONObject("WATER");
                long total = afterProgress == null ? -1 : AntForestV2.forestFeatureLong(afterProgress, "totalWaterG");
                long remaining = afterWater == null ? -1 : AntForestV2.forestFeatureLong(afterWater, "value");
                if (!Boolean.TRUE.equals(response.opt("success")) || !"SUCCESS".equals(response.optString("resultCode")) || total <= before || remaining < 0 || remaining >= balance) {
                    Log.record("会员绿植：浇水后进度或扣水未确认，停止；code=" + awarded.optString("resultCode", "缺失") + "，原因=" + RpcRequestGuard.errorMessage(awarded)); return;
                }
                String message = "会员绿植：浇水成功，累计" + before + "→" + total + "，水滴" + balance + "→" + remaining;
                Log.record(message); Log.forest(message); TimeUtil.sleep(300);
            }
            Log.record("会员绿植：达到本轮50次上限，下轮继续");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.record("会员绿植：执行异常，" + t.getClass().getSimpleName()); Log.err(TAG, "waterMemberPlant", t); }
    }

    /* 在工作的伙伴只有确认到期时才允许替换。 */
    boolean dispatchMonopolyAnimal(boolean canConsumeAnimalProp) {
        try {
            TimeUtil.sleep(0);
            if (!canConsumeAnimalProp) {
                return false;
            }
            int consumePropType = host.consumeAnimalPropType.getValue();
            if (consumePropType == ConsumeAnimalPropType.NONE) {
                Log.record("新版动物伙伴🦩未开启派遣动物伙伴，跳过");
                return false;
            }
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            Object using = jo.opt("usingMonopolyCreature");
            JSONArray creatureList = jo.optJSONArray("creatureList");
            if (!(using instanceof Boolean) || creatureList == null) {
                Log.record("新版动物伙伴：缺少占用状态或动物列表，本轮不派遣");
                return true;
            }
            boolean replacing = Boolean.TRUE.equals(using);
            if (replacing) {
                JSONObject current = null;
                for (int i = 0; i < creatureList.length(); i++) {
                    JSONObject creature = creatureList.optJSONObject(i);
                    if (creature != null && "using".equals(creature.optString("status"))) { current = creature; break; }
                }
                if (!isMonopolyCreatureExpired(current)) {
                    Log.record("新版动物伙伴：当前伙伴未确认到期，保留当前伙伴");
                    return true;
                }
                Log.record("新版动物伙伴：当前伙伴已到期，尝试派遣新伙伴");
            }
            List<JSONObject> candidates = new ArrayList<>();
            boolean allHasInitialRobEnergy = true;
            for (int i = 0; i < creatureList.length(); i++) {
                JSONObject creature = creatureList.optJSONObject(i);
                if (creature == null || !isMonopolyCreatureIdle(creature)) {
                    continue;
                }
                candidates.add(creature);
                if (creature.isNull("initialRobEnergy")) {
                    allHasInitialRobEnergy = false;
                }
            }
            if (candidates.isEmpty()) {
                Log.record("新版动物伙伴🦩当前没有可派遣的动物");
                return replacing;
            }
            // 按"派遣动物伙伴"配置选人：默认顺序取第一个；按最多能量派遣时新版按预计产出最高选
            JSONObject selected = candidates.get(0);
            if (consumePropType == ConsumeAnimalPropType.QUANTITY && allHasInitialRobEnergy) {
                for (JSONObject candidate : candidates) {
                    if (candidate.optInt("initialRobEnergy") > selected.optInt("initialRobEnergy")) {
                        selected = candidate;
                    }
                }
            }
            String creatureCode = selected.optString("creatureCode");
            if (creatureCode.isEmpty()) {
                Log.record("新版动物伙伴🦩候选缺少 creatureCode，跳过派遣");
                return false;
            }
            JSONObject assigned = MyUtils.newJSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode));
            String code = assigned.optString("resultCode");
            if ("MONOPOLY_CREATURE_USE_CONFIRM".equals(code) || "ANIMAL_PROP_IN_USE_CONFIRM".equals(code)) {
                if (!replacing) {
                    Log.record("新版动物伙伴：派遣时占用状态变化，保留现有伙伴，下轮重查");
                    return true;
                }
                assigned = MyUtils.newJSONObject(AntForestRpcCall.assignMonopolyCreature(creatureCode, true));
            }
            if (MessageUtil.checkResultCode(TAG, assigned)) {
                JSONObject after = MyUtils.newJSONObject(AntForestRpcCall.queryUsingCreatureInfo(UserIdMap.getCurrentUid()));
                JSONObject active = after.optJSONObject("userCreatureVO");
                if ("SUCCESS".equals(after.optString("resultCode")) && Boolean.TRUE.equals(after.opt("usingMonopolyCreature"))
                        && active != null && creatureCode.equals(active.optString("creatureCode"))) {
                    String message = "新版动物伙伴：" + (replacing ? "到期替换" : "派遣") + "成功[" + selected.optString("creatureName", creatureCode) + "]";
                    Log.record(message); Log.forest(message);
                } else {
                    Log.record("新版动物伙伴：派遣请求已接受，回查未确认新伙伴在工作；下轮重查");
                }
                return true;
            }
            Log.record("新版动物伙伴：派遣失败，code=" + assigned.optString("resultCode", "缺失") + "，原因=" + assigned.optString("resultDesc", "应答无效"));
            if (replacing) return true;
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "dispatchMonopolyAnimal err:", t);
        }
        return false;
    }

    boolean isMonopolyCreatureExpired(JSONObject creature) {
        JSONObject energy = creature == null ? null : creature.optJSONObject("robEnergyVO");
        if (energy == null) return false;
        Object remain = energy.opt("robRemainDays");
        Object worked = energy.opt("alreadyWorkDays");
        Object max = energy.opt("maxWorkDays");
        return (remain instanceof Number && ((Number) remain).doubleValue() == energy.optInt("robRemainDays") && energy.optInt("robRemainDays") <= 0)
                || (worked instanceof Number && max instanceof Number && ((Number) max).doubleValue() == energy.optInt("maxWorkDays")
                && ((Number) worked).doubleValue() == energy.optInt("alreadyWorkDays") && energy.optInt("maxWorkDays") > 0
                && energy.optInt("alreadyWorkDays") >= energy.optInt("maxWorkDays"));
    }

    /* 新版动物是否空闲可派遣 */
    boolean isMonopolyCreatureIdle(JSONObject creature) {
        if (StringUtil.isEmpty(creature.optString("creatureCode"))) {
            return false;
        }
        if ("using".equals(creature.optString("status")) || creature.optLong("assignTime", 0L) > 0) {
            return false;
        }
        JSONObject energy = creature.optJSONObject("robEnergyVO");
        if (energy == null) {
            return true;
        }
        if (energy.has("robRemainDays") && energy.optInt("robRemainDays") <= 0) {
            return false;
        }
        int maxWorkDays = energy.optInt("maxWorkDays", 0);
        return maxWorkDays <= 0 || energy.optInt("alreadyWorkDays", 0) < maxWorkDays;
    }

    boolean queryAnimalPropList() {
        try {
            boolean sequence = host.consumeAnimalPropType.getValue() == ConsumeAnimalPropType.SEQUENCE;
            JSONObject animalProp = null;
            String nextCursor = null;
            Set<String> cursors = new HashSet<>();
            for (int page = 0; page < 20; page++) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryAnimalPropList(nextCursor));
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    Log.record("巡护派遣：第" + (page + 1) + "页查询未确认，暂不派遣");
                    return false;
                }
                JSONArray animalProps = jo.optJSONArray("animalProps");
                if (animalProps == null) {
                    Log.record("巡护派遣：第" + (page + 1) + "页缺少动物列表，暂不派遣");
                    return false;
                }
                if (animalProps != null) {
                    for (int i = 0; i < animalProps.length(); i++) {
                        JSONObject prop = animalProps.optJSONObject(i);
                        if (prop == null) {
                            continue;
                        }
                        if (animalProp == null) {
                            animalProp = prop;
                        } else if (!sequence && animalPropEnergy(prop) > animalPropEnergy(animalProp)) {
                            animalProp = prop;
                        }
                    }
                }
                if (jo.has("hasMore") && !(jo.opt("hasMore") instanceof Boolean)) {
                    Log.record("巡护派遣：第" + (page + 1) + "页分页状态无效，暂不派遣");
                    return false;
                }
                if (sequence && animalProp != null || !jo.optBoolean("hasMore")) {
                    return consumeAnimalProp(animalProp);
                }
                Object rawCursor = jo.opt("nextCursor");
                if (!(rawCursor instanceof String)) {
                    Log.record("巡护派遣：后续分页游标无效，暂不派遣");
                    return false;
                }
                String cursor = (String) rawCursor;
                if (cursor.trim().isEmpty() || !cursors.add(cursor)) {
                    Log.record("巡护派遣：后续分页游标为空或重复，暂不派遣");
                    return false;
                }
                nextCursor = cursor;
            }
            Log.record("巡护派遣：达到20页查询上限但列表尚未结束，暂不派遣，下轮重查");
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "queryAnimalPropList err:", t);
        }
        return false;
    }

    int animalPropEnergy(JSONObject animalProp) {
        JSONObject partner = animalProp.optJSONObject("partner");
        if (partner == null) {
            return 0;
        }
        JSONObject robAbility = partner.optJSONObject("robAbility");
        return robAbility == null ? 0 : robAbility.optInt("robEnergyInDaily", 0);
    }

    boolean consumeAnimalProp(JSONObject animalProp) {
        if (animalProp == null) {
            return false;
        }
        try {
            JSONObject main = animalProp.optJSONObject("main");
            JSONObject partner = animalProp.optJSONObject("partner");
            if (main == null || partner == null) {
                return false;
            }
            String propGroup = main.optString("propGroup");
            String propType = main.optString("propType");
            String name = partner.optString("name");
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.consumeProp(propGroup, propType, false));
            if (MessageUtil.checkResultCode(TAG, jo)) {
                Log.forest("巡护派遣🐆[" + name + "]");
                return true;
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "consumeAnimalProp err:", t);
        }
        return false;
    }
}
