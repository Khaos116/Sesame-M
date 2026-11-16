package io.github.aw1y2z.sesame.model.task.antForest;

import io.github.aw1y2z.sesame.util.DailyTask;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy;
import io.github.aw1y2z.sesame.data.task.TaskAward;
import io.github.aw1y2z.sesame.data.task.TaskAttemptPolicy.Outcome;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarm.TaskStatus;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestTask {

    private static final String TAG = AntForestTask.class.getSimpleName();
    private final AntForestV2 host;

    AntForestTask(AntForestV2 host) {
        this.host = host;
    }

    public boolean initAntForestTaskListMap(boolean AutoAntForestVitalityTaskList, boolean AutoAntForestHuntTaskList, boolean receiveForestTaskAward, boolean ForestHunt) {
        try {
            if (!receiveForestTaskAward && !ForestHunt) return true;

            //初始化AntForestVitalityTaskListMap
            AntForestVitalityTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注：不再预置拉黑，全部交由自动拉黑机制判定
            // 预置黑名单登记在 MessageUtil（单一真相，配置页据此标注"默认"）
            Set<String> blackList = MessageUtil.presetBlackList("AntForestV2", "AntForestVitalityTaskList");
            // 可继续添加更多黑名单任务

            Set<String> whiteList = new HashSet<>();
            whiteList.add("逛农场得落叶肥料");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestVitalityTaskListMap.add(task, task);
            }

            if (receiveForestTaskAward) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                    if (forestTasksNew == null) return false;
                    if (forestTasksNew != null && forestTasksNew.length() != 0) {
                        for (int i = 0; i < forestTasksNew.length(); i++) {
                            JSONObject forestTask = forestTasksNew.optJSONObject(i);
                            if (forestTask == null) return false;
                            JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                            if (taskInfoList == null) return false;
                            if (taskInfoList != null && taskInfoList.length() != 0) {
                                for (int j = 0; j < taskInfoList.length(); j++) {
                                    JSONObject taskInfo = taskInfoList.optJSONObject(j);
                                    JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                    if (taskBaseInfo == null) return false;
                                    JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                    String taskType = taskBaseInfo.optString("taskType");
                                    String taskTitle = bizInfo.optString("taskTitle", taskType);
                                    if (taskTitle.isEmpty()) return false;
                                    AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                                }
                            }
                        }
                    }
                } else return false;

                jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen());
                if (MessageUtil.checkResultCode(TAG, jo)) {
                    // 添加安全的空值判断
                    if (jo.optJSONArray("taskInfoList") == null) return false;
                    if (jo.has("taskInfoList")) {
                        JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                        if (taskInfoList != null && taskInfoList.length() != 0) {
                            for (int j = 0; j < taskInfoList.length(); j++) {
                                JSONObject taskInfo = taskInfoList.optJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                if (taskBaseInfo == null) return false;
                                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                String taskType = taskBaseInfo.optString("taskType");
                                String taskTitle = bizInfo.optString("taskTitle", taskType);
                                if (taskTitle.isEmpty()) return false;
                                AntForestVitalityTaskListMap.add(taskTitle, taskTitle);
                            }
                        }
                    }
                } else return false;

                //保存任务到配置文件
                AntForestVitalityTaskListMap.save();
                Log.record("同步任务🉑森林活力值任务列表");

                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestVitalityTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestVitalityTaskList = (SelectModelField) AntForestV2.get("AntForestVitalityTaskList");
                    if (AntForestVitalityTaskList == null) {
                        return false;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林活力值任务", "AntForestVitalityTaskList", blackList, whiteList, AntForestVitalityTaskList);
                }
            }

            //初始化AntForestHuntTaskListMap
            AntForestHuntTaskListMap.load();
            // 1. 定义黑名单（需要添加的任务）和白名单（需要移除的任务）
            // 注：抽抽乐里的游戏/开宝箱类不再预置拉黑，交由自动拉黑机制判定
            blackList = MessageUtil.presetBlackList("AntForestV2", "AntForestHuntTaskList");
            // 可继续添加更多黑名单任务

            whiteList = new HashSet<>();
            whiteList.add("消耗活力值得机会");
            // 可继续添加更多白名单任务
            for (String task : blackList) {
                AntForestHuntTaskListMap.add(task, task);
            }

            if (ForestHunt) {
                JSONObject resData = MyUtils.newJSONObject(AntForestRpcCall.enterDrawActivityopengreen("", "ANTFOREST_NORMAL_DRAW", "task_entry"));
                if (MessageUtil.checkSuccess(TAG, resData)) {
                    JSONArray drawSceneGroups = resData.optJSONArray("drawSceneGroups");
                    if (drawSceneGroups == null) return false;
                    for (int i = 0; drawSceneGroups != null && i < drawSceneGroups.length(); i++) {
                        JSONObject drawScene = drawSceneGroups.optJSONObject(i);
                        JSONObject drawActivity = drawScene != null ? drawScene.optJSONObject("drawActivity") : null;
                        if (drawActivity == null) return false;
                        String sceneCode = drawActivity.optString("sceneCode");
                        if (sceneCode.isEmpty()) return false;
                        JSONObject listTaskopengreen = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen(sceneCode + "_TASK", "task_entry"));
                        if (MessageUtil.checkSuccess(TAG, listTaskopengreen)) {
                            JSONArray taskList = listTaskopengreen.optJSONArray("taskInfoList");
                            if (taskList == null) return false;
                            for (int j = 0; taskList != null && j < taskList.length(); j++) {
                                JSONObject taskInfo = taskList.optJSONObject(j);
                                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                                if (taskBaseInfo == null) return false;
                                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                                String taskName = bizInfo.optString("title");
                                if (taskName.isEmpty()) return false;
                                AntForestHuntTaskListMap.add(taskName, taskName);
                            }
                        } else return false;
                    }
                } else return false;
                AntForestHuntTaskListMap.save();
                Log.record("同步任务🉑森林抽抽乐任务列表");
                //自动按模块初始化设定调整黑名单和白名单
                if (AutoAntForestHuntTaskList) {
                    // 初始化黑白名单（使用集合统一操作）
                    ConfigV2 config = ConfigV2.INSTANCE;
                    ModelFields AntForestV2 = config.getModelFieldsMap().get("AntForestV2");
                    SelectModelField AntForestHuntTaskList = (SelectModelField) AntForestV2.get("AntForestHuntTaskList");
                    if (AntForestHuntTaskList == null) {
                        return false;
                    }

                    // 2~4. 批量写回黑/白名单并保存
                    MessageUtil.syncTaskBlackList("森林抽抽乐任务", "AntForestHuntTaskList", blackList, whiteList, AntForestHuntTaskList);
                }
            }
            return true;
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "initAntForestTaskListMap err:", t);
            return false;
        }
    }

    static java.util.SortedSet<java.time.LocalDate> missingForestSignDates(JSONArray rows, java.time.LocalDate today) {
        if (rows == null) return null;
        java.util.SortedSet<java.time.LocalDate> missing = new java.util.TreeSet<>();
        Map<String, String> states = new HashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("signKey") instanceof String)) continue;
            String key = row.optString("signKey");
            java.time.LocalDate date;
            try { date = java.time.LocalDate.parse(key); } catch (java.time.DateTimeException e) { continue; }
            if (!date.toString().equals(key) || !date.isBefore(today) || date.isBefore(today.minusDays(30))) continue;
            String state = String.valueOf(row.opt("signed")) + "/" + row.opt("makeUpSigned") + "/" + row.opt("canMakeUpSign");
            String previous = states.put(key, state);
            if (previous != null && !previous.equals(state)) return null;
            if (Boolean.FALSE.equals(row.opt("signed")) && Boolean.FALSE.equals(row.opt("makeUpSigned"))
                    && (!row.has("canMakeUpSign") || Boolean.TRUE.equals(row.opt("canMakeUpSign")))) missing.add(date);
        }
        return missing;
    }

    JSONArray queryForestSignMonth(java.time.YearMonth month) throws JSONException {
        TimeUtil.sleep(0);
        java.time.LocalDate first = month.atDay(1);
        java.time.LocalDate begin = first.minusDays(first.getDayOfWeek().getValue() - 1);
        JSONObject data = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.queryForestSignMonth(month.toString(), begin.toString(), begin.plusDays(41).toString())));
        return data == null ? null : data.optJSONArray("signModelList");
    }

    int forestMakeupCardCount() throws JSONException {
        TimeUtil.sleep(0);
        JSONObject data = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.listForestMakeupCards()));
        JSONObject stock = data == null ? null : data.optJSONObject("userSCAssetsVO");
        JSONArray ids = stock == null ? null : stock.optJSONArray("canUseSCAssetsIdList");
        if (ids == null) return -1;
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object id = ids.opt(i);
            if (!(id instanceof String) || ((String) id).isEmpty() || !unique.add((String) id)) return -1;
        }
        return unique.size();
    }

    void autoMakeUpSign() {
        if (!host.autoMakeUpSign.getValue()) return;
        try {
            if (forestMakeupCardCount() <= 0) return;
            Calendar day = MyUtils.getInstance();
            java.time.LocalDate today = java.time.LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH));
            java.util.SortedSet<java.time.LocalDate> missing = new java.util.TreeSet<>();
            java.time.YearMonth last = java.time.YearMonth.from(today);
            // A 30-day window can span three months (March 1 in a non-leap year).
            for (java.time.YearMonth month = java.time.YearMonth.from(today.minusDays(30)); !month.isAfter(last); month = month.plusMonths(1)) {
                java.util.SortedSet<java.time.LocalDate> dates = missingForestSignDates(queryForestSignMonth(month), today);
                if (dates == null) { Log.record("森林补签：日历状态未知，停止"); return; }
                missing.addAll(dates);
            }
            for (java.time.LocalDate date : missing) {
                TimeUtil.sleep(0);
                String flag = "forest::makeupAttempt::" + date;
                if (Status.hasFlagToday(flag)) continue;
                java.util.SortedSet<java.time.LocalDate> fresh = missingForestSignDates(queryForestSignMonth(java.time.YearMonth.from(date)), today);
                if (fresh == null) return;
                if (!fresh.contains(date)) continue;
                if (forestMakeupCardCount() <= 0) return;
                Calendar current = MyUtils.getInstance();
                if (!today.equals(java.time.LocalDate.of(current.get(Calendar.YEAR), current.get(Calendar.MONTH) + 1, current.get(Calendar.DAY_OF_MONTH)))) return;
                String businessDay = day.get(Calendar.YEAR) + ":" + day.get(Calendar.DAY_OF_YEAR);
                if (!Status.flagTodayVerified(java.util.Collections.singleton(flag), businessDay)) return;
                current = MyUtils.getInstance();
                if (!(current.get(Calendar.YEAR) + ":" + current.get(Calendar.DAY_OF_YEAR)).equals(businessDay)) return;
                JSONObject response = AntForestV2.forestSignPayload(MyUtils.newJSONObject(AntForestRpcCall.manualMakeUpSign(date.toString())));
                JSONArray after = queryForestSignMonth(java.time.YearMonth.from(date));
                if (response == null || after == null) return;
                int matches = 0;
                boolean confirmed = false;
                for (int i = 0; i < after.length(); i++) {
                    JSONObject row = after.optJSONObject(i);
                    if (row != null && date.toString().equals(row.opt("signKey"))) {
                        matches++;
                        confirmed = Boolean.TRUE.equals(row.opt("signed")) || Boolean.TRUE.equals(row.opt("makeUpSigned"));
                    }
                }
                if (matches != 1 || !confirmed) { Log.record("森林补签：结果未确认，当天不重复提交[" + date + "]"); return; }
                Log.forest("森林补签📅[" + date + "]回查成功");
            }
        } catch (io.github.aw1y2z.sesame.util.TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "autoMakeUpSign", t); }
    }

    void vantiepSign() {
        if (DailyTask.skipFlag("forest::vantiepSign", "森林签到")) {
            return;
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            JSONArray forestSignVOList = jo.optJSONArray("forestSignVOList");
            JSONObject forestSignVO = forestSignVOList != null ? forestSignVOList.optJSONObject(0) : null;
            if (forestSignVO == null) {
                return;
            }
            if (!(forestSignVO.opt("currentSignKey") instanceof String)) return;
            String currentSignKey = forestSignVO.optString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.optString("signId"); // 签到ID
            String sceneCode = forestSignVO.optString("sceneCode"); // 场景代码
            if (currentSignKey.trim().isEmpty()) return;
            JSONArray signRecords = forestSignVO.optJSONArray("signRecords"); // 签到记录
            for (int i = 0; signRecords != null && i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.optJSONObject(i);
                if (signRecord == null) {
                    continue;
                }
                if (!(signRecord.opt("signKey") instanceof String)) continue;
                String signKey = signRecord.optString("signKey");
                int awardCount = signRecord.optInt("awardCount");
                if (signKey.equals(currentSignKey) && Boolean.TRUE.equals(signRecord.opt("signed"))) {
                    Status.flagToday("forest::vantiepSign");
                    break;
                }
                if (signKey.equals(currentSignKey) && Boolean.FALSE.equals(signRecord.opt("signed"))) {
                    if (!(forestSignVO.opt("signId") instanceof String) || !(forestSignVO.opt("sceneCode") instanceof String)
                            || signId.trim().isEmpty() || sceneCode.trim().isEmpty()) return;
                    JSONObject joSign = MyUtils.newJSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林签到失败:", joSign)) {
                        int continuousCount = joSign.optInt("continuousCount");
                        Log.forest("森林签到📆拯救第" + continuousCount + "天#复活[" + awardCount + "g能量]");
                        Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        // 回读确认：重新拉列表，本期 currentSignKey 已签才落当日标记
                        JSONObject verify = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
                        if (MessageUtil.checkResultCode(TAG, verify)) {
                            JSONArray verifyList = verify.optJSONArray("forestSignVOList");
                            JSONObject verifySign = verifyList == null ? null : verifyList.optJSONObject(0);
                            JSONArray verifyRecords = verifySign == null ? null : verifySign.optJSONArray("signRecords");
                            for (int j = 0; verifyRecords != null && j < verifyRecords.length(); j++) {
                                JSONObject verifyRecord = verifyRecords.optJSONObject(j);
                                if (verifyRecord != null && currentSignKey.equals(verifyRecord.optString("signKey"))
                                        && Boolean.TRUE.equals(verifyRecord.opt("signed"))) {
                                    Status.flagToday("forest::vantiepSign");
                                    break;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    void queryCommonSign() {
        if (DailyTask.skip("forest::sevenDaySign", "森林七日签到")) return;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryCommonSign("ANTFOREST_GIFT7TH_SIGN_202506"));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            }
            if (!jo.has("forestSignVO")) {
                if (!Status.hasFlagToday("forest::CommonSign")) {
                    Status.flagToday("forest::CommonSign");
                    Log.i("森林签到📆尚未检测到[森林7日签到数据]若出现数据立马为大人领取");
                }
                return;
            }
            JSONObject forestSignVO = jo.optJSONObject("forestSignVO");
            if (forestSignVO == null) {
                return;
            }
            if (!(forestSignVO.opt("currentSignKey") instanceof String)) return;
            String currentSignKey = forestSignVO.optString("currentSignKey"); // 当前签到的 key
            String signId = forestSignVO.optString("signId"); // 签到ID
            String sceneCode = forestSignVO.optString("sceneCode"); // 场景代码
            if (currentSignKey.trim().isEmpty()) return;
            JSONArray signRecords = forestSignVO.optJSONArray("signRecords"); // 签到记录
            for (int i = 0; signRecords != null && i < signRecords.length(); i++) { // 遍历签到记录
                JSONObject signRecord = signRecords.optJSONObject(i);
                if (signRecord == null) {
                    continue;
                }
                if (!(signRecord.opt("signKey") instanceof String)) continue;
                String signKey = signRecord.optString("signKey");
                int awardCount = signRecord.optInt("awardCount");
                String awardType = signRecord.optString("awardType");
                JSONObject extInfo = signRecord.optJSONObject("extInfo");
                String awardName = extInfo != null ? extInfo.optString("awardName") : "";
                if (signKey.equals(currentSignKey) && Boolean.TRUE.equals(signRecord.opt("signed"))) {
                    DailyTask.done("forest::sevenDaySign");
                    break;
                }
                if (signKey.equals(currentSignKey) && Boolean.FALSE.equals(signRecord.opt("signed"))) {
                    if (!(forestSignVO.opt("signId") instanceof String) || !(forestSignVO.opt("sceneCode") instanceof String)
                            || signId.trim().isEmpty() || sceneCode.trim().isEmpty()) return;
                    JSONObject joSign = MyUtils.newJSONObject(AntForestRpcCall.antiepSign(signId, UserIdMap.getCurrentUid(), sceneCode));
                    TimeUtil.sleep(300); // 等待300毫秒
                    if (MessageUtil.checkSuccess(TAG + "森林7日签到:", joSign)) {
                        DailyTask.done("forest::sevenDaySign");
                        int continuousCount = joSign.optInt("continuousCount");
                        Log.forest("森林签到📆第" + continuousCount + "天#7日签到[" + awardName + "*" + awardCount + "]");
                        if (awardType.equals("ENERGY")) {
                            Statistics.addData(Statistics.DataType.COLLECTED, awardCount);
                        }
                    }
                    break;
                }
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    void vitalitySign() {
        if (DailyTask.skip("forest::vitalitySign", "森林活力值签到")) return;
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.vitalitySign());
            TimeUtil.sleep(300);
            if (MessageUtil.checkResultCode(TAG, jo)) {
                int continuousCount = jo.optInt("continuousCount");
                int signAwardCount = jo.optInt("signAwardCount");
                Log.forest("森林任务📆签到[" + continuousCount + "天]奖励[" + signAwardCount + "活力值]");
                DailyTask.done("forest::vitalitySign");
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) {
            Log.err(TAG, "vitalitySign err:", t);
        }
    }

    void queryVitalityYouthTaskList() {
        for (YouthForestRoute route : AntForestV2.YOUTH_FOREST_ROUTES) {
            queryYouthForestTask(route);
        }
    }

    void queryTaskList() {
        try {
            boolean doubleCheck = true;
            int listPass = 0;
            while (doubleCheck && listPass++ < 5) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryTaskList());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    doubleCheck = false;
                    continue;
                }
                // 添加安全的空值判断
                if (!jo.has("forestTasksNew")) {
                    doubleCheck = false;
                    continue;
                }
                JSONArray forestTasksNew = jo.optJSONArray("forestTasksNew");
                if (forestTasksNew == null || forestTasksNew.length() == 0) {
                    doubleCheck = false;
                    continue;
                }
                for (int i = 0; i < forestTasksNew.length(); i++) {
                    JSONObject forestTask = forestTasksNew.optJSONObject(i);
                    if (forestTask == null) {
                        continue;
                    }
                    JSONArray taskInfoList = forestTask.optJSONArray("taskInfoList");
                    if (taskInfoList == null) {
                        continue;
                    }
                    doubleCheck = doForsetTaskList(taskInfoList);
                }
            }
            doubleCheck = true;
            // 复核最多 5 轮：领奖后要重取列表（新判定完成的子任务要能接着领），但服务端若对已领奖任务
            // 仍回 success，这里就会空转刷请求，故设上限兜底。
            int pass = 0;
            while (doubleCheck && pass++ < 5) {
                JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen());
                if (!MessageUtil.checkResultCode(TAG, jo)) {
                    return;
                }
                // 添加安全的空值判断
                if (!jo.has("taskInfoList")) {
                    return;
                }
                JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
                if (taskInfoList == null || taskInfoList.length() == 0) {
                    return;
                }
                doubleCheck = doForsetTaskList(taskInfoList);
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "queryTaskList err:", t);
        }
    }

    Boolean doForsetTaskList(JSONArray taskInfoList) {
        boolean doubleCheck = false;
        try {
            if (taskInfoList == null || taskInfoList.length() == 0) {
                return doubleCheck;
            }
            for (int j = 0; j < taskInfoList.length(); j++) {
                JSONObject taskInfo = taskInfoList.optJSONObject(j);
                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                if (taskBaseInfo == null) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                String taskStatus = taskBaseInfo.optString("taskStatus");
                JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                // 容器型父任务（taskNode=PARENT，如选教卡 ENERGY_XUANJIAO）：行为都在 childTaskTypeList 里，
                // 父任务自身只是一个任务组、没有完成接口。它的 taskStatus 常为 RECEIVED（任务组自身的奖已领），
                // 不能因此跳过子任务——否则子任务里「已完成但未领奖」的奖励永远领不到。
                if ("PARENT".equals(taskBaseInfo.optString("taskNode"))
                        && childTaskTypeList != null && childTaskTypeList.length() > 0) {
                    // 统一处理：先领「已完成但未领奖」的子任务，再按子任务自带的 autoCompleteTask
                    // 标记尝试待办项。不能按 taskType 白名单挑着做——未列举的容器会被静默跳过、
                    // 子任务永远没人领；也不能无条件尝试：行为子任务（缴费/出行等）需真实操作，
                    // 服务端只会回「不支持rpc调用」，尝试纯属白费请求。
                    boolean changed = awardFinishedChildren(childTaskTypeList);
                    changed = doChildTask(childTaskTypeList, taskTitle) || changed;
                    // 容器父任务自身的奖励同样要领：子任务可能都已领取，父任务却仍是 FINISHED 待领，
                    // 直接 continue 会把它永远漏掉。纯领奖、无副作用，不受开关约束。
                    if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                        changed = receiveTaskAward(sceneCode, taskType, taskTitle) || changed;
                    }
                    // 只有真动了才再核一遍。容器型父任务每轮都在列表里，无条件置 true 会让上层的
                    // while(doubleCheck) 空转。
                    if (changed) {
                        doubleCheck = true;
                    }
                    continue;
                }
                if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                    if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                        doubleCheck = true;
                    }
                } else if (TaskStatus.TODO.name().equals(taskStatus)) {
                    //黑名单任务跳过
                    if (host.AntForestVitalityTaskList.getValue().contains(taskTitle)) {
                        continue;
                    }
                    // 周期/持续型任务：由真实行为推进、常驻列表且无 RPC 可完成，跳过（不尝试、不拉黑）
                    if (TaskAttemptPolicy.isCyclicTask(taskBaseInfo)) {
                        // 周期任务常驻列表、由真实行为推进，每轮结果相同；静默跳过
                        continue;
                    }

                    // 站内行为类任务（浇水/收能量/能量雨）：完成条件是**服务端的行为计数**，
                    // 只能真做那个行为；上报与伪申报都推不动它，会白试一次后按"兜底未生效"拉黑
                    TaskAttemptPolicy.Forge forge = forestVitalityForge(taskType, sceneCode, taskTitle, bizInfo);
                    // 做不了的当天只试一次、临时故障留待下一轮（见 TaskAttemptPolicy）
                    Outcome outcome = TaskAttemptPolicy.handle("forest::" + sceneCode + "/" + taskType, taskTitle, null,
                            () -> attemptFinishTask(sceneCode, taskType, taskTitle), Log::forest,
                            new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务", taskType, sceneCode,
                                    TaskAlternative.DEFAULT_VERSION, forge,
                                    (k) -> probeForestVitalityStatus(sceneCode, taskType)).withScene(sceneCode).withSource("ANTFOREST"));
                    doubleCheck |= outcome == Outcome.DONE || outcome == Outcome.TRIGGERED;
                }
            }
            //可能是触发限时挑战奖励的
            AntForestRpcCall.batchQueryAndTouchopengreen();
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "doForsetTaskList err:", t);
        }
        return doubleCheck;
    }

    /**
     * 森林活力值任务的「站内行为」分派：能对上模块已有行为的才返回 forge，其余返回 null。
     * <p>判据取**任务名/描述**而非 {@code taskCategorization}：后者会把站内浇水这类行为也标成
     * {@code Exchange}，按它判会把这些任务当成交易类放过（既不伪造、又很快被拉黑）。
     * <p>真交易/履约类（缴费/出行/回收）由 {@code TaskAlternative.isTransactionTask} 在策略里先行拦下，
     * 到不了这里，故此处只需认得出"能做的事"。
     */
    TaskAttemptPolicy.Forge forestVitalityForge(String taskType, String sceneCode, String taskTitle,
                                                        JSONObject bizInfo) {
        // 原营销任务保持优先级；跳转元数据不能抢占真实浇水或能量雨。
        if (isTakeLookVitalityTask(taskType, taskTitle, null)
                || (!isWaterVitalityTask(taskType, taskTitle) && !isEnergyRainVitalityTask(taskType, taskTitle)
                    && isTakeLookVitalityTask(taskType, taskTitle, bizInfo))) {
            return () -> {
                try {
                    if (bizInfo != null && bizInfo.opt("taskJumpUrl") instanceof String
                            && !bizInfo.optString("taskJumpUrl").trim().isEmpty()) {
                        Log.forest("森林任务🧭[" + taskTitle + "]#外跳类，按'看一眼'上报");
                    }
                    AntForestRpcCall.takeLookEnd("chInfo_ch_appcenter__chsub_9patch");
                    return probeVitalityDone(sceneCode, taskType);
                } catch (org.json.JSONException e) {
                    Log.err(TAG, "takeLookEnd request err:", e);
                    return false;
                }
            };
        }
        // 真需要浇水的才走浇水，且要有可浇的对象：配置为"不浇水"或没选好友时浇不动，
        // 此时不伪造（否则等于谎报做过），留给下一轮
        if (isWaterVitalityTask(taskType, taskTitle) && host.waterHelper.canWaterFriend()) {
            return () -> {
                host.waterHelper.waterFriendEnergy();
                return probeVitalityDone(sceneCode, taskType);
            };
        }
        if (isEnergyRainVitalityTask(taskType, taskTitle)) {
            return () -> {
                host.gameHelper.startEnergyRain();
                return probeVitalityDone(sceneCode, taskType);
            };
        }
        return null;
    }

    /**
     * 「看一眼」类任务：标题里的动作（浇水/签到等）只是营销文案，真正的完成条件是
     * 上报一次"进过森林页面"。靠标题判会去做那件文案里的事——既白做，也不满足条件。
     * <p>除标题/类型外，还认服务端下发的两个明确信号：
     * <ul>
     *   <li>{@code taskAccEffectiveType=EFFECTIVE_TASK}：完成条件就是"进过页面"这个动作本身；</li>
     *   <li>{@code bizInfo} 带非空 {@code taskJumpUrl}：逛一逛/外跳类（如 {@code GYG_diantao} 淘宝）
     *       同为 EFFECTIVE_TASK，先按同一动作上报（模块打不开外部 H5，只能报"看过"）。</li>
     * </ul>
     */
    static boolean isTakeLookVitalityTask(String taskType, String taskTitle, JSONObject bizInfo) {
        String type = taskType == null ? "" : taskType.toLowerCase();
        if (type.startsWith("zhrw_")
                || (taskTitle != null && (taskTitle.contains("免费拿") || taskTitle.contains("绿植")))) {
            return true;
        }
        if (bizInfo == null) {
            return false;
        }
        return "EFFECTIVE_TASK".equals(bizInfo.optString("taskAccEffectiveType"))
                || (bizInfo.opt("taskJumpUrl") instanceof String
                    && !bizInfo.optString("taskJumpUrl").trim().isEmpty());
    }

    /**
     * 行为发出后等列表确认：行为类任务的完成要以任务列表为准，行为生效有延迟。
     * <p>不能无脑返回 true——那样会让策略立刻落当日标记，任务在**行为还没被服务端确认**时就被锁死，
     * 下一轮还会撞上"兜底未生效"的判据被拉黑。
     * <p>返回 false 只表示"本次没等到确认"，不代表行为没发出；日志据此如实措辞。
     */
    boolean probeVitalityDone(String sceneCode, String taskType) {
        for (int round = 0; round < AntForestV2.VITALITY_PROBE_ROUNDS; round++) {
            TimeUtil.sleep(AntForestV2.VITALITY_PROBE_INTERVAL_MS);
            if (TaskAttemptPolicy.isDone(probeForestVitalityStatus(sceneCode, taskType))) {
                return true;
            }
        }
        Log.forest("森林任务⏳[" + taskType + "]#行为已发但列表未确认，下次运行重试");
        return false;
    }

    /** 浇水类：{@code ZHRW_HUIYUANjiaoshui_202608}、一键浇水等 */
    static boolean isWaterVitalityTask(String taskType, String taskTitle) {
        String type = taskType == null ? "" : taskType.toLowerCase();
        return type.contains("jiaoshui") || type.contains("watering")
                || (taskTitle != null && (taskTitle.contains("浇水") || taskTitle.contains("一键浇水")));
    }

    /** 能量雨类 */
    static boolean isEnergyRainVitalityTask(String taskType, String taskTitle) {
        String type = taskType == null ? "" : taskType.toLowerCase();
        return type.contains("energyrain")
                || (taskTitle != null && taskTitle.contains("能量雨"));
    }

    void queryYouthForestTask(YouthForestRoute route) {
        if (DailyTask.skipFlag("vitalityTask::" + route.firstTaskType, "青春特权" + route.displayName)) {
            return;
        }
        String absentFlag = "vitalityTaskAbsent::" + route.firstTaskType;
        if (!DailyTask.isManual() && Status.hasFlagToday(absentFlag)) {
            Log.forest("青春特权道具[" + route.displayName + "]今日已确认无对应任务，自动调度跳过；可点击立即执行重新检查");
            return;
        }
        Status.clearFlag(absentFlag);
        try {
            String status = queryYouthForestTaskStatus(route);
            if (status == null) {
                Log.forest("青春特权道具[" + route.displayName + "]查询未确认，不写当日标记");
                return;
            }
            if (status.isEmpty()) {
                Status.flagToday(absentFlag);
                Log.forest("青春特权道具[" + route.displayName + "]今日无对应任务 taskType=" + route.awardTaskType);
                return;
            }
            boolean isReceived = TaskStatus.RECEIVED.name().equals(status);
            if (!isReceived && TaskStatus.FINISHED.name().equals(status)) {
                String award = AntForestRpcCall.receiveYouthPrivilegeTaskAward(route.source, route.awardTaskType);
                Log.forest("青春特权道具[" + route.displayName + "]领奖响应成功="
                        + MessageUtil.checkSuccess(TAG, MyUtils.newJSONObject(award)) + "，等待列表确认");
                TimeUtil.sleep(1000);
                status = queryYouthForestTaskStatus(route);
                isReceived = TaskStatus.RECEIVED.name().equals(status);
            }
            if (isReceived) {
                Status.flagToday("vitalityTask::" + route.firstTaskType);
                Log.forest("青春特权道具[" + route.displayName + "]已领取");
            } else {
                Log.forest("青春特权道具[" + route.displayName + "]未领取 状态=" + status);
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "queryYouthForestTask err:", t);
        }
    }

    /** null 表示查询/结构未确认，空串仅表示有效列表没有对应任务。 */
    String queryYouthForestTaskStatus(YouthForestRoute route) throws JSONException {
        JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.queryYouthPrivilegeTaskList(route.firstTaskType, route.source));
        if (!MessageUtil.checkResultCode(TAG, jo) && !MessageUtil.checkSuccess(TAG, jo)) return null;
        JSONObject payload = jo.has("resData") ? jo.optJSONObject("resData") : jo;
        if (payload == null) return null;
        List<JSONObject> tasks = new ArrayList<>();
        boolean complete = collectOpenGreenTaskInfos(payload, tasks);
        String status = "";
        for (JSONObject taskInfo : tasks) {
            JSONObject base = taskInfo.optJSONObject("taskBaseInfo");
            if (base == null || base.optString("taskType").isEmpty()) {
                complete = false;
                continue;
            }
            if (Objects.equals(route.awardTaskType, base.optString("taskType"))) {
                String found = base.optString("taskStatus");
                if (found.isEmpty() || (!status.isEmpty() && !status.equals(found))) return null;
                status = found;
            }
        }
        // 无关条目异常不能否定已找到的目标；只有判定“无任务”才要求完整列表。
        return !status.isEmpty() ? status : complete ? "" : null;
    }

    static boolean collectOpenGreenTaskInfos(JSONObject payload, List<JSONObject> tasks) {
        boolean found = false;
        boolean complete = true;
        if (payload.has("taskInfoList")) {
            found = true;
            complete &= appendTaskInfoList(tasks, payload.optJSONArray("taskInfoList"));
        }
        for (String key : new String[]{"taskGroupList", "forestTasksNew"}) {
            if (payload.has(key)) {
                found = true;
                complete &= appendTaskGroups(tasks, payload.optJSONArray(key));
            }
        }
        if (payload.has("result")) {
            JSONObject result = payload.optJSONObject("result");
            complete &= result != null && collectOpenGreenTaskInfos(result, tasks);
            found = true;
        }
        return found && complete;
    }

    static boolean appendTaskInfoList(List<JSONObject> tasks, JSONArray list) {
        if (list == null) return false;
        boolean complete = true;
        for (int i = 0; i < list.length(); i++) {
            JSONObject task = list.optJSONObject(i);
            if (task == null) complete = false;
            else tasks.add(task);
        }
        return complete;
    }

    static boolean appendTaskGroups(List<JSONObject> tasks, JSONArray groups) {
        if (groups == null) return false;
        boolean complete = true;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.optJSONObject(i);
            complete &= group != null && appendTaskInfoList(tasks, group.optJSONArray("taskInfoList"));
        }
        return complete;
    }

    Boolean receiveTaskAward(String sceneCode, String taskType, String taskTitle) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.receiveTaskAward(sceneCode, taskType));
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                int incAwardCount = jo.optInt("incAwardCount");
                String returnData = jo.optString("returnData").trim();
                // 权益型任务（选教卡子任务、TEST_LEAF_* 等）的响应形如
                // {"incAwardCount":1,"provideRightsSuccess":true,"returnData":"{}"}：**只有数量、没有单位**。
                // 按"不含 Energy 就当活力值"贴标签会把无量纲的数字记成活力值，故只记权益发放。
                if (returnData.isEmpty() || "{}".equals(returnData)) {
                    Log.forest("森林任务🎖️领取[" + taskTitle + "]#权益已发放[数量" + incAwardCount
                            + "]#响应未给单位");
                } else if (returnData.toUpperCase().contains("ENERGY")) {
                    Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "g能量]");
                    Statistics.addData(Statistics.DataType.COLLECTED, incAwardCount);
                } else {
                    Log.forest("森林任务🎖️领取[" + taskTitle + "]奖励#获得[" + incAwardCount + "活力值]");
                }
                return true;
            }
            // 领奖收口：先按任务列表复核"已领到"（该调用点不拉黑）
            TimeUtil.sleep(800);
            if (TaskAward.confirmReceivedOrBlackList("森林任务🎖️领取",
                    k -> {
                        TaskAttemptPolicy.ProbeResult result = probeForestVitalityStatus(sceneCode, taskType);
                        // 此入口也领取 forest.h5.queryTaskList 的奖励；固定活力值列表缺项不能证明另一列表已领。
                        return result == TaskAttemptPolicy.ProbeResult.GONE
                                ? TaskAttemptPolicy.ProbeResult.UNKNOWN : result;
                    }, taskTitle, taskTitle, null,
                    msg -> Log.forest(msg))) {
                return true;
            }
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "receiveTaskAward err:", t);
        }
        return false;
    }

    /** 森林任务完成上报：拉黑与否仍交给自动拉黑机制（400000040 已降级为连续确认） */
    Outcome attemptFinishTask(String sceneCode, String taskType, String taskTitle) {
        if (MyUtils.closeUnRpc() && "ANTFOREST_VITALITY_TASK".equals(sceneCode) && taskType != null) {
            if (taskType.startsWith("GYG_BK_XYK")
                    || taskType.startsWith("GYG_jinritoutiao")
                    || taskType.startsWith("GYG_huabeikaitong")) {
                // 这几类任务不支持通过 RPC 完成，直接跳过而不是让请求失败重试
                return Outcome.UNABLE;
            }
        }
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            //检查并标记黑名单任务
            MessageUtil.checkResultCodeAndMarkTaskBlackList("AntForestVitalityTaskList", AntForestV2.blackTaskKey(taskTitle), jo);
            TimeUtil.sleep(500);
            if (MessageUtil.checkSuccess(TAG, jo)) {
                Log.forest("森林任务🧾️上报已受理[" + taskTitle + "]");
                return Outcome.DONE;
            }
            if (MessageUtil.isRetryable(jo) || MessageUtil.isServerBusy(jo)) {
                return Outcome.RETRY;
            }
            // 不支持rpc调用（400000040）→ 由 TaskAttemptPolicy 代为伪申报：逛一逛类外部场景
            // （GYG_* / TAOBAO_* / NONGCHANG_* 等，如森林图书馆、淘宝人生、科普视频、点淘、淘宝特价版、
            // 芭芭农场、摇一摇、买菜、薅羊毛）；不再按 taskType 维护白名单
            if (TaskAlternative.hit(jo, sceneCode)) {
                return Outcome.UNSUPPORTED;
            }
            Log.forest("上报任务[" + taskTitle + "]失败");
            return Outcome.UNABLE;
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "finishTask err:", t);
        }
        return Outcome.RETRY;
    }

    /** 列表状态探针：重拉绿色行动任务列表，按 sceneCode+taskType 匹配该任务当前状态。 */
    TaskAttemptPolicy.ProbeResult probeForestVitalityStatus(String sceneCode, String taskType) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.listTaskopengreen());
            if (!MessageUtil.checkResultCode(TAG, jo) || !jo.has("taskInfoList")) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray taskInfoList = jo.optJSONArray("taskInfoList");
            if (taskInfoList == null) {
                return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            return forestVitalityStatus(taskInfoList, sceneCode, taskType);
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.err(TAG, "probeForestVitalityStatus err:", t);
            return TaskAttemptPolicy.ProbeResult.UNKNOWN;
        }
    }

    static TaskAttemptPolicy.ProbeResult forestVitalityStatus(JSONArray tasks, String scene, String type) {
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
            if (base == null) return TaskAttemptPolicy.ProbeResult.UNKNOWN;
            if (scene.equals(base.optString("sceneCode")) && type.equals(base.optString("taskType"))) {
                String status = base.optString("taskStatus");
                if ("FINISHED".equals(status)) return TaskAttemptPolicy.ProbeResult.FINISHED;
                if ("RECEIVED".equals(status)) return TaskAttemptPolicy.ProbeResult.RECEIVED;
                return "TODO".equals(status) ? TaskAttemptPolicy.ProbeResult.TODO : TaskAttemptPolicy.ProbeResult.UNKNOWN;
            }
            JSONArray children = task.optJSONArray("childTaskTypeList");
            if (children != null) {
                TaskAttemptPolicy.ProbeResult found = forestVitalityStatus(children, scene, type);
                if (found != TaskAttemptPolicy.ProbeResult.GONE) return found;
            }
        }
        return TaskAttemptPolicy.ProbeResult.GONE;
    }

    /**
     * 容器型父任务下「已完成但未领奖」的子任务领奖。
     * <p>这类容器的行为子任务被判定完成后不会自动发放，必须逐个 {@code receiveTaskAward}；
     * 只处理 TODO 子任务时，已完成的奖励会一直挂在列表里领不到（如 TEST_LEAF_TASK）。
     * <p>失败不记日志也不拉黑：与顶层 FINISHED 任务的领奖路径保持一致，避免把轮询变成噪声。
     *
     * @return 是否有子任务真领到奖励（供上层决定要不要再核一遍列表）
     */
    boolean awardFinishedChildren(JSONArray childTaskTypeList) {
        boolean awarded = false;
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.optJSONObject(i);
                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                if (taskBaseInfo == null || !TaskStatus.FINISHED.name().equals(taskBaseInfo.optString("taskStatus"))) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo", "{}"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", taskType);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                if (receiveTaskAward(sceneCode, taskType, taskTitle)) {
                    awarded = true;
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "awardFinishedChildren err:", th);
        }
        return awarded;
    }

    /** @return 本轮是否真上报了子任务（供上层判断要不要再核一遍列表） */
    boolean doChildTask(JSONArray childTaskTypeList, String title) {
        boolean changed = false;
        try {
            for (int i = 0; i < childTaskTypeList.length(); i++) {
                JSONObject taskInfo = childTaskTypeList.optJSONObject(i);
                JSONObject taskBaseInfo = taskInfo != null ? taskInfo.optJSONObject("taskBaseInfo") : null;
                if (taskBaseInfo == null) {
                    continue;
                }
                JSONObject bizInfo = MyUtils.newJSONObject(taskBaseInfo.optString("bizInfo"));
                String taskType = taskBaseInfo.optString("taskType");
                String taskTitle = bizInfo.optString("taskTitle", title);
                String sceneCode = taskBaseInfo.optString("sceneCode");
                String taskStatus = taskBaseInfo.optString("taskStatus");
                if (TaskStatus.TODO.name().equals(taskStatus)) {
                    if (bizInfo.optBoolean("autoCompleteTask")) {
                        // 走统一策略：400000040 时由 TaskAttemptPolicy 代为 doFarmTask 伪申报，
                        // 裸调 finishTask 会把 UNSUPPORTED 吞成 false，兜底永不触发
                        TaskAttemptPolicy.handle("forest::child::" + sceneCode + "/" + taskType, taskTitle, null,
                                () -> attemptFinishTask(sceneCode, taskType, taskTitle), Log::forest,
                                new TaskAttemptPolicy.Site("AntForestVitalityTaskList", "森林任务",
                                        taskType, sceneCode, (k) -> probeForestVitalityStatus(sceneCode, taskType))
                                        .withScene("ANTFOREST"));
                        changed = true;
                    }
                }
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "doChildTask err:", th);
        }
        return changed;
    }
}
