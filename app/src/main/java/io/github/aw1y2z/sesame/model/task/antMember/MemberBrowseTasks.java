package io.github.aw1y2z.sesame.model.task.antMember;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.task.TaskLifecycle;
import io.github.aw1y2z.sesame.model.common.GameTaskAction;
import io.github.aw1y2z.sesame.model.base.TaskAlternative;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcFailurePolicy;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 会员签到页/广告任务的新协议，复用主任务轮询和账号私有计时记录。 */
final class MemberBrowseTasks {
    private static final String TAG = "会员浏览任务";

    static boolean run(boolean memberEnabled, Set<String> memberBlacklist, boolean adEnabled, Set<String> adBlacklist) {
        if (!memberEnabled && !adEnabled) return true;
        try (TaskLifecycle.Work work = TaskLifecycle.enter()) {
            if (work == null) return false;
            GameTaskAction.cleanupDailyRecords();
            Set<String> attempted = new HashSet<>();
            for (int round = 0; round < 12; round++) {
                Map<String, JSONObject> tasks = load(memberEnabled, adEnabled);
                boolean changed = false;
                for (JSONObject task : tasks.values()) {
                    TimeUtil.sleep(0);
                    boolean ad = isAd(task);
                    if (ad ? !adEnabled : !memberEnabled) continue;
                    Set<String> blacklist = ad ? adBlacklist : memberBlacklist;
                    String id = configId(task), title = title(task);
                    if (title.isEmpty()) title = id;
                    if (id.isEmpty() || blacklist.contains(id) || blacklist.contains(title) || finished(task)) continue;
                    String snapshot = id + ":" + processId(task) + ":" + normalizeProgress(progress(task));
                    if (!attempted.add(snapshot)) continue;
                    try { changed |= perform(task, title); }
                    catch (TaskCancelledException e) { throw e; }
                    catch (MemberTaskPausedException e) { throw e; }
                    catch (Exception e) { Log.record(TAG + "[" + title + "]：执行异常，保留回查状态（" + e.getClass().getSimpleName() + "）"); }
                }
                if (!changed) return true;
                TimeUtil.sleep(500);
            }
            Log.record(TAG + "：达到本轮阶段上限，后续轮次继续");
        } catch (TaskCancelledException e) { throw e; }
        catch (MemberTaskPausedException e) { Log.record(TAG + "：请求触发风控、验证或未知暂停，停止本轮后续请求"); return false; }
        catch (Exception e) { Log.record(TAG + "：查询异常，后续轮次重试（" + e.getClass().getSimpleName() + "）"); }
        return true;
    }

    private static Map<String, JSONObject> load(boolean memberEnabled, boolean adEnabled) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        Set<String> pageIds = new HashSet<>();
        String session = UUID.randomUUID().toString();
        boolean complete = false;
        boolean validIds = true;
        boolean adIdentitiesComplete = true;
        int invalidPages = 0;
        Map<String, JSONObject> adContracts = new HashMap<>();
        Set<String> ambiguousAds = new HashSet<>();
        Set<String> mixedAdStages = new HashSet<>();
        for (int page = 1; page <= 64; page++) {
            TimeUtil.sleep(0);
            List<JSONObject> rows = rows(AntMemberRpcCall.signPageTaskList(page, session));
            if (rows == null) { Log.record(TAG + "：签到页第" + page + "页查询失败或结构无效，保留已取得的任务和回查记录"); break; }
            if (rows.isEmpty()) { complete = true; break; }
            boolean advanced = false, validPage = false;
            for (JSONObject row : rows) {
                String id = configId(row) + ":" + processId(row);
                if (configId(row).isEmpty()) {
                    validIds = false;
                    if (isAd(row)) adIdentitiesComplete = false;
                    continue;
                }
                validPage = true;
                if (pageIds.add(id)) advanced = true;
                add(result, row, adContracts, ambiguousAds, mixedAdStages);
            }
            if (validPage) {
                invalidPages = 0;
                // 重复页也可能是服务端忽略页码，不能据此授权广告或清理未知提交。
                if (!advanced) { Log.record(TAG + "：签到页任务重复，停止翻页"); break; }
            } else if (++invalidPages >= 2) {
                // 容忍一页脏数据；持续无有效身份时不按随机内容追满64页。
                Log.record(TAG + "：签到页连续2页缺少有效任务标识，停止翻页"); break;
            }
            TimeUtil.sleep(300);
        }
        if (!complete) Log.record(TAG + "：签到分页未完整，" + (adEnabled
                ? "暂停本轮广告操作及确认，保留未知提交和后续查询" : "保留已取得的普通任务和后续查询"));
        boolean adComplete = false, processComplete = false, adProcessLookup = false;
        for (int source = 0; source < 2; source++) {
            if (source == 0 && !adEnabled) continue;
            if (source == 1) {
                adProcessLookup = adEnabled && complete && adComplete
                        && needsAdProcesses(result, adContracts, ambiguousAds, mixedAdStages);
                if (!memberEnabled && !adProcessLookup) continue;
                if (!memberEnabled && adProcessLookup && !Status.hasFlagToday("memberAdProcessLookup")) {
                    Log.record(TAG + "：待确认广告在现有来源未确认，补查进行中列表；不执行普通任务");
                    Status.flagToday("memberAdProcessLookup");
                }
            }
            TimeUtil.sleep(0);
            String raw = source == 0 ? AntMemberRpcCall.currentMemberAdTasks() : AntMemberRpcCall.currentMemberProcesses();
            List<JSONObject> rows = rows(raw);
            if (rows == null) { Log.record(TAG + "：" + (source == 0 ? "广告" : adProcessLookup ? "进行中（含待确认广告）" : "进行中") + "列表查询失败或结构无效，仅保留可独立回查的任务"); continue; }
            if (source == 0) adComplete = true;
            else processComplete = true;
            for (JSONObject row : rows) {
                if (configId(row).isEmpty()) {
                    validIds = false;
                    if (isAd(row)) adIdentitiesComplete = false;
                    if (source == 0) adComplete = false;
                    else processComplete = false;
                }
                add(result, row, adContracts, ambiguousAds, mixedAdStages);
            }
        }
        boolean newTitles = false;
        for (JSONObject row : result.values()) {
            String title = title(row);
            if (!title.isEmpty() && !AntMemberTaskListMap.getMap().containsKey(title)) {
                AntMemberTaskListMap.add(title, title); newTitles = true;
            }
        }
        if (newTitles) AntMemberTaskListMap.save();
        // 报名后的 process 行替代同配置的未报名展示行，避免重复报名。
        // 过滤只限制执行，不能证明已观察到的活跃流程结束；未知提交仍需保护。
        Set<String> observedActive = new HashSet<>();
        Set<String> observedProcesses = new HashSet<>();
        for (JSONObject row : result.values()) if (!processId(row).isEmpty()) {
            observedProcesses.add(configId(row));
            if (!finished(row)) observedActive.add(configId(row));
        }
        result.values().removeIf(row -> processId(row).isEmpty() && observedActive.contains(configId(row)));
        Set<String> unresolvedAds = new HashSet<>();
        for (Iterator<Map.Entry<String, JSONObject>> rows = result.entrySet().iterator(); rows.hasNext();) {
            Map.Entry<String, JSONObject> entry = rows.next();
            JSONObject row = entry.getValue();
            if (adEnabled && isAd(row)) {
                String identity = configId(row) + ":" + processId(row);
                boolean conflict = ambiguousAds.contains(identity);
                JSONObject resolved = conflict ? null : resolveAdRow(row, adContracts.get(identity), !mixedAdStages.contains(identity));
                if (resolved == null) {
                    unresolvedAds.add("memberAd::" + configId(row));
                    String warning = "memberAdContract::" + configId(row) + ":" + (conflict ? "conflict" : "incomplete");
                    if (!Status.hasFlagToday(warning)) {
                        Log.record(TAG + "[" + title(row) + "]：" + (conflict ? "同一任务流程广告合同冲突" : "广告合同参数无效或分散且阶段不一致，无法合并")
                                + "，未执行，保留对应提交记录");
                        Status.flagToday(warning);
                    }
                    rows.remove(); continue;
                }
                row = resolved; entry.setValue(row);
            }
            // 进行中列表缺失时，不能把未显示 process 的配置当成尚未报名。
            if (isAd(row) ? !adEnabled || !complete || !adComplete || !adIdentitiesComplete || adProcessLookup && !processComplete
                    : !memberEnabled || !processComplete && processId(row).isEmpty()) rows.remove();
        }
        Calendar confirmationNow = MyUtils.getInstance();
        String confirmationDay = confirmationNow.get(Calendar.YEAR) + ":" + confirmationNow.get(Calendar.DAY_OF_YEAR);
        for (JSONObject row : result.values()) {
            String title = title(row);
            RuntimeInfo adStore = RuntimeInfo.getInstance();
            String adKey = "memberAd::" + configId(row);
            JSONObject adState = MyUtils.newJSONObject(adStore.getString(adKey));
            JSONObject contract = adContract(row);
            if (isAd(row) && confirmationDay.equals(adState.optString("day")) && adState.optBoolean("confirmed")) {
                markAdHandled(configId(row), adState, adState.optString("confirmedStage"));
                continue;
            }
            boolean adAdvanced = advanced(adState.optJSONObject("before"), row);
            boolean confirmedProgress = adState.has("stage")
                    ? (sameProgress(adState.optString("stage"), progress(row)) || adAdvanced) && (completed(row) || adAdvanced)
                    : !adState.optString("processId").isEmpty() && completed(row);
            boolean sameAdIdentity = contract != null && contract.toString().equals(adState.optString("contract"))
                    && adState.optString("processId").equals(processId(row))
                    && (!adState.optString("processId").isEmpty() || !observedProcesses.contains(configId(row)));
            boolean sameAdDay = confirmationDay.equals(adState.optString("day"));
            String cachedStage = adState.optString("stage", progress(row));
            boolean cachedProgress = sameAdIdentity && sameAdDay
                    && adState.optBoolean("accepted") && !adState.optBoolean("submitted")
                    && (adState.has("stage") ? sameProgress(cachedStage, progress(row)) : !adState.optString("processId").isEmpty())
                    && isAdConfirmed(configId(row), processId(row), cachedStage, adState.optString("contract"));
            if (cachedProgress && !confirmedProgress) {
                if (markAdHandled(configId(row), adState, cachedStage)) {
                    Log.record(TAG + "[" + title + "]：已有完成凭据，本次未发送完成请求，已清理待处理记录");
                }
            } else if (contract != null && expired(row)) {
                String savedProcess = adState.optString("processId");
                if (contract.toString().equals(adState.optString("contract"))
                        && (!savedProcess.isEmpty() ? savedProcess.equals(processId(row))
                        : !observedProcesses.contains(configId(row)) && processId(row).isEmpty()
                        && sameProgress(adState.optString("stage"), progress(row)))) {
                    logConfirmed(title, row, adState.optString("day")); adStore.put(adKey, null);
                }
            } else if (sameAdIdentity && confirmedProgress && !sameAdDay) {
                adStore.put(adKey, null); logConfirmed(title, row, adState.optString("day"));
            } else if (sameAdIdentity && sameAdDay && confirmedProgress) {
                String confirmedStage = adState.optString("stage", progress(row));
                boolean alreadyConfirmed = isAdConfirmed(configId(row), processId(row), confirmedStage, adState.optString("contract"));
                if (completed(row) && !processId(row).isEmpty() && (!adState.has("stage") || adState.has("requestProcessId"))) {
                    adState.put("confirmedTerminal", normalizeProgress(progress(row)));
                }
                boolean persisted = markAdHandled(configId(row), adState, confirmedStage);
                if (!alreadyConfirmed && (persisted || MyUtils.newJSONObject(adStore.getString(adKey)).optBoolean("confirmed"))) {
                    logConfirmed(title, row, adState.optString("day"));
                }
            }
            String key = "memberBrowse::" + processId(row);
            RuntimeInfo store = RuntimeInfo.getInstance();
            if (!processId(row).isEmpty() && finished(row) && !store.getString(key).isEmpty()) {
                logConfirmed(title, row, MyUtils.newJSONObject(store.getString(key)).optString("day")); store.put(key, null);
            }
        }
        boolean memberComplete = memberEnabled && complete && processComplete;
        boolean adsComplete = adEnabled && complete && adComplete && adIdentitiesComplete && (!adProcessLookup || processComplete);
        // 关闭开关不代表提交已结束；没有完整新列表就保留未知记录，重开后先回查。
        if ((memberComplete || adsComplete) && validIds) {
            Set<String> live = new HashSet<>();
            for (JSONObject row : result.values()) if (!finished(row)) live.add(processId(row));
            Calendar now = MyUtils.getInstance();
            String day = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR);
            RuntimeInfo store = RuntimeInfo.getInstance();
            for (String key : adsComplete ? store.keysStartingWith("memberAd::") : Collections.<String>emptyList()) {
                if (!unresolvedAds.contains(key) && !day.equals(MyUtils.newJSONObject(store.getString(key)).optString("day"))) store.put(key, null);
            }
            for (String key : memberComplete ? store.keysStartingWith("memberBrowse::") : Collections.<String>emptyList()) {
                // 完整列表已无此流程才清旧日记录；活跃流程的未知提交仍须回查。
                if (!live.contains(key.substring("memberBrowse::".length()))
                        && !day.equals(MyUtils.newJSONObject(store.getString(key)).optString("day"))) store.put(key, null);
            }
        }
        return result;
    }

    private static void add(Map<String, JSONObject> target, JSONObject row, Map<String, JSONObject> contracts,
                            Set<String> ambiguous, Set<String> mixedStages) throws org.json.JSONException {
        String id = configId(row);
        if (id.isEmpty()) return;
        String key = id + ":" + processId(row);
        JSONObject contract = adContract(row, true);
        if (contract != null) {
            // 显式参数只在同一身份内合并；阶段是否一致另外核对。
            JSONObject previous = contracts.putIfAbsent(key, contract);
            if (previous != null) for (Iterator<String> fields = contract.keys(); fields.hasNext();) {
                String field = fields.next();
                if (previous.has(field) && !Objects.equals(previous.opt(field), contract.opt(field))) ambiguous.add(key);
                else previous.put(field, contract.opt(field));
            }
            JSONObject selected = target.get(key);
            JSONObject selectedContract = selected == null ? null : adContract(selected, true);
            if (selectedContract != null && (!sameProgress(progress(selected), progress(row))
                    || finished(selected) != finished(row) || expired(selected) != expired(row))) mixedStages.add(key);
            if (selectedContract != null && selectedContract.length() > contract.length()
                    && sameProgress(progress(selected), progress(row)) && finished(selected) == finished(row)
                    && expired(selected) == expired(row)) return;
        }
        target.put(key, row);
    }

    private static JSONObject resolveAdRow(JSONObject row, JSONObject evidence, boolean sameStage) throws org.json.JSONException {
        JSONObject actual = adContract(row), explicit = adContract(row, true);
        if (actual == null || explicit == null) return null;
        if (evidence == null) return row;
        JSONObject expected = MyUtils.newJSONObject(actual.toString());
        boolean missing = false;
        for (Iterator<String> fields = evidence.keys(); fields.hasNext();) {
            String field = fields.next();
            if (explicit.has(field) && !Objects.equals(explicit.opt(field), evidence.opt(field))) return null;
            missing |= !explicit.has(field);
            expected.put(field, evidence.opt(field));
        }
        if (!missing) return row;
        if (!sameStage) return null;
        JSONObject resolved = MyUtils.newJSONObject(row.toString());
        JSONObject cfg = MyUtils.newJSONObject(config(row).toString());
        JSONObject award = cfg.optJSONObject("awardParam");
        if (award == null) award = MyUtils.newJSONObject();
        cfg.put("browseSeconds", expected.opt("seconds")).put("awardParam", award.put("awardParamPoint", expected.opt("awardNum")));
        resolved.put("simpleTaskConfig", cfg);
        JSONObject verified = adContract(resolved);
        return verified != null && expected.toString().equals(verified.toString()) ? resolved : null;
    }

    private static boolean needsAdProcesses(Map<String, JSONObject> rows, Map<String, JSONObject> contracts,
                                           Set<String> ambiguous, Set<String> mixedStages) throws org.json.JSONException {
        RuntimeInfo store = RuntimeInfo.getInstance();
        Calendar now = MyUtils.getInstance();
        String day = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR);
        for (String key : store.keysStartingWith("memberAd::")) {
            JSONObject state = MyUtils.newJSONObject(store.getString(key));
            String expected = state.optString("contract");
            if (!day.equals(state.optString("day")) || expected.isEmpty()
                    || !state.optBoolean("applied") && !state.optBoolean("submitted")) continue;
            if (state.optString("processId").isEmpty()) for (JSONObject row : rows.values()) {
                if (key.equals("memberAd::" + configId(row)) && isAd(row) && !processId(row).isEmpty()) return true;
            }
            boolean found = false;
            for (JSONObject row : rows.values()) {
                if (!key.equals("memberAd::" + configId(row)) || !isAd(row)) continue;
                String identity = configId(row) + ":" + processId(row);
                JSONObject resolved = ambiguous.contains(identity) ? null : resolveAdRow(row, contracts.get(identity), !mixedStages.contains(identity));
                JSONObject actual = resolved == null ? null : adContract(resolved);
                if (actual != null && expected.equals(actual.toString())
                        && state.optString("processId").equals(processId(resolved))
                        && (!state.has("stage") || sameProgress(state.optString("stage"), progress(resolved)) || advanced(state.optJSONObject("before"), resolved)) && (finished(resolved)
                        || advanced(state.optJSONObject("before"), resolved) || state.optBoolean("accepted") && !state.optBoolean("submitted"))) {
                    found = true; break;
                }
            }
            if (!found) return true;
        }
        return false;
    }

    private static final class MemberTaskPausedException extends Exception { }

    private static JSONObject checkedResponse(String raw) throws MemberTaskPausedException {
        JSONObject response = MyUtils.newJSONObject(raw);
        checkPaused(response);
        return response;
    }

    private static void checkPaused(JSONObject response) throws MemberTaskPausedException {
        String message = RpcRequestGuard.errorMessage(response);
        for (String key : new String[]{"error", "resultCode", "errorCode", "retCode", "code", "causeCode"}) {
            if (RpcFailurePolicy.isRiskDenied(response.optString(key), message)) throw new MemberTaskPausedException();
        }
        if (message.contains("验证后继续") || message.contains("滑动验证") || message.contains("cheating traffic")) {
            throw new MemberTaskPausedException();
        }
        // 旧记录只存错误码，同码也可能由拒绝文案触发；缺分类不能当普通退避。
        if (("RPC_SKIPPED".equals(response.optString("error")) || "RPC_SKIPPED".equals(response.optString("resultCode")))
                && !response.optBoolean("localRuleSkip") && (!Boolean.FALSE.equals(response.opt("riskPause"))
                || message.contains("验证") || message.contains("访问被拒绝") || message.contains("风控"))) {
            throw new MemberTaskPausedException();
        }
    }

    private static List<JSONObject> rows(String raw) throws MemberTaskPausedException {
        JSONObject response = checkedResponse(raw);
        if (!GameTaskAction.accepted(response)) return null;
        JSONObject data = response.optJSONObject("resultData");
        if (data == null) data = response;
        List<JSONObject> rows = new ArrayList<>();
        boolean found = false;
        for (JSONObject root : data == response ? new JSONObject[]{response} : new JSONObject[]{response, data}) {
            for (String key : new String[]{"availableTaskProcessList", "availableTaskConfigList", "taskProcessVOList", "taskHistoryVOList", "pureTaskList", "adTaskList", "alipayGrowthTaskList"}) {
                if (!root.has(key)) continue;
                found = true;
                if (!append(rows, root.optJSONArray(key))) return null;
            }
            for (String key : new String[]{"categoryTaskList", "categoryTaskVOList"}) {
                if (!root.has(key)) continue;
                found = true;
                JSONArray categories = root.optJSONArray(key);
                if (categories == null) return null;
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.optJSONObject(i);
                    if (category == null || !append(rows, category.has("taskProcessVOList") ? category.optJSONArray("taskProcessVOList") : category.optJSONArray("taskList"))) return null;
                }
            }
        }
        return found ? rows : null;
    }

    private static boolean append(List<JSONObject> rows, JSONArray array) {
        if (array == null || array.length() > 1000) return false;
        for (int i = 0; i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null) return false;
            rows.add(row);
        }
        return true;
    }

    private static JSONObject config(JSONObject task) {
        for (String key : new String[]{"simpleTaskConfig", "taskConfigInfo", "taskConfig"}) {
            JSONObject config = task.optJSONObject(key);
            if (config != null) return config;
        }
        return MyUtils.newJSONObject();
    }

    private static String title(JSONObject task) {
        String title = config(task).optString("title");
        return title.isEmpty() ? config(task).optString("name") : title;
    }

    private static String configId(JSONObject task) {
        for (Object id : new Object[]{task.opt("taskConfigId"), config(task).opt("configId"), config(task).opt("taskConfigId"), config(task).opt("id"), task.opt("id")}) {
            if ((id instanceof String || id instanceof Number) && !id.toString().trim().isEmpty()) return id.toString();
        }
        return "";
    }

    private static String processId(JSONObject task) {
        Object id = task.opt("processId");
        if (!(id instanceof String) || ((String) id).isEmpty()) id = task.opt("taskProcessId");
        return id instanceof String ? (String) id : "";
    }

    private static String progress(JSONObject task) {
        JSONObject ext = task.optJSONObject("extInfo");
        return task.optString("currentCount")
                + ":" + task.optString("targetCount") + ":" + (ext == null ? "" : ext.optString("PERIOD_CURRENT_COUNT") + ":" + ext.optString("PERIOD_TARGET_COUNT"));
    }

    private static JSONObject snapshotProgress(JSONObject task) throws org.json.JSONException {
        JSONObject before = MyUtils.newJSONObject().put("currentCount", task.opt("currentCount"));
        JSONObject ext = task.optJSONObject("extInfo");
        if (ext != null) before.put("extInfo", MyUtils.newJSONObject().put("PERIOD_CURRENT_COUNT", ext.opt("PERIOD_CURRENT_COUNT")));
        return before;
    }

    private static boolean advanced(JSONObject before, JSONObject after) {
        if (before == null || after == null || expired(after)) return false;
        for (String key : new String[]{"currentCount", "PERIOD_CURRENT_COUNT"}) {
            JSONObject a = key.equals("currentCount") ? before : before.optJSONObject("extInfo");
            JSONObject b = key.equals("currentCount") ? after : after.optJSONObject("extInfo");
            if (b == null) continue;
            try {
                String oldValue = a == null ? "" : a.optString(key);
                // 缺失的旧周期是未开始；主计数和显式坏值不补0。
                if (key.equals("PERIOD_CURRENT_COUNT") && oldValue.isEmpty()
                        && (a != null || !before.has("extInfo") || before.isNull("extInfo"))) oldValue = "0";
                long oldCount = new java.math.BigDecimal(oldValue).longValueExact();
                long newCount = new java.math.BigDecimal(b.optString(key)).longValueExact();
                if (oldCount >= 0 && newCount > oldCount) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private static boolean finished(JSONObject task) {
        return expired(task) || completed(task);
    }

    private static boolean expired(JSONObject task) {
        return task != null && ("EXPIRED".equalsIgnoreCase(task.optString("status"))
                || "EXPIRED".equalsIgnoreCase(task.optString("subStatus"))) && !hasCompletedStatus(task);
    }

    private static boolean hasCompletedStatus(JSONObject task) {
        if (task == null) return false;
        for (String key : new String[]{"status", "subStatus"})
            if (Set.of("AWARDED", "SUCCESS", "COMPLETE", "DONE", "FINISHED").contains(task.optString(key).toUpperCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean completed(JSONObject task) {
        if (task == null) return false;
        // 显式完成态优先于过期子状态；仅计数达标不能覆盖过期。
        if (hasCompletedStatus(task)) return true;
        if (expired(task)) return false;
        try {
            long current = new java.math.BigDecimal(task.optString("currentCount")).longValueExact();
            long target = new java.math.BigDecimal(task.optString("targetCount")).longValueExact();
            return target > 0 && current >= target;
        } catch (Exception e) { return false; }
    }

    private static JSONObject detail(String id, JSONObject baseline) throws Exception {
        TimeUtil.sleep(0);
        JSONObject response = checkedResponse(AntMemberRpcCall.currentMemberTaskDetail(id));
        if (!GameTaskAction.accepted(response)) return null;
        JSONObject data = response.optJSONObject("resultData");
        JSONObject row = (data == null ? response : data).optJSONObject("taskProcessVO");
        if (row == null || !id.equals(processId(row))) return null;
        if (!configId(row).isEmpty() && !configId(baseline).equals(configId(row))) return null;
        if (config(row).length() == 0) row.put("simpleTaskConfig", config(baseline));
        return row;
    }

    private static String[] target(JSONObject task) {
        for (Object raw : new Object[]{task.opt("targetBusiness"), config(task).opt("targetBusiness")}) {
            JSONArray targets = raw instanceof JSONArray ? (JSONArray) raw
                    : raw instanceof String ? new JSONArray().put(raw) : null;
            for (int i = 0; targets != null && i < targets.length(); i++) {
                if (!(targets.opt(i) instanceof String)) continue;
                String[] parts = targets.optString(i).split("#", -1);
                if (parts.length == 3 && "BROWSE".equals(parts[0]) && !parts[1].isEmpty() && !parts[2].isEmpty()) return parts;
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
                        && !Set.of("CALL_APP", "BROWSE").contains(parts[0])) return new String[]{"BROWSE", parts[0], parts[1]};
                // 明确非浏览的顶层合同不能靠回退改成浏览任务。
                if (raw instanceof String && ("CALL_APP".equals(parts[0])
                        || parts.length >= 3 && !"BROWSE".equals(parts[0]))) return null;
            }
            if (raw instanceof JSONArray) return null;
        }
        return null;
    }

    private static boolean perform(JSONObject listed, String title) throws Exception {
        if (isAd(listed)) return performAd(listed, title);
        String label = TAG + "[" + title + "]", id = configId(listed), process = processId(listed);
        RuntimeInfo store = RuntimeInfo.getInstance();
        if (process.isEmpty()) {
            if (target(listed) == null) return false;
            Calendar now = MyUtils.getInstance();
            String key = "memberApply::" + id;
            if (GameTaskAction.skipRejected(key, label)) return false;
            String dayPrefix = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR) + ":";
            String normalized = normalizeProgress(progress(listed));
            String stage = dayPrefix + normalized, previous = store.getString(key);
            if (previous.startsWith(dayPrefix) && normalizeProgress(previous.substring(dayPrefix.length())).equals(normalized)) {
                Log.record(label + "：已报名待列表确认，不重复报名"); return false;
            }
            TimeUtil.sleep(0);
            if (!store.putVerified(key, stage)) return false;
            boolean batch = "signInAd".equals(config(listed).optString("sourceBusiness"));
            Log.record(label + "：调用报名接口 " + (batch ? "batchApplyTask" : "applyTask"));
            JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.applyCurrentMemberTask(id, batch));
            if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("resultCode"))) store.putVerified(key, "");
            checkPaused(response);
            JSONObject data = response.optJSONObject("resultData");
            JSONObject resultMap = data == null ? null : data.optJSONObject("resultMap");
            boolean ok = GameTaskAction.accepted(response) && (!batch || resultMap != null && "SUCCESS".equals(resultMap.optString(id)));
            if (!ok) markRejected(listed, title, response);
            if (GameTaskAction.isBusinessRejection(response)) Status.flagToday("agReject::" + key);
            Log.record(label + (ok ? "：报名已受理，重新查询任务阶段" : "：报名未确认，code=" + response.optString("resultCode", "缺失")));
            JSONObject applied = data == null ? null : data.optJSONObject("taskProcessVO");
            if (ok && !batch && applied != null && !processId(applied).isEmpty()
                    && (configId(applied).isEmpty() || id.equals(configId(applied)))) {
                if (config(applied).length() == 0) applied.put("simpleTaskConfig", config(listed));
                perform(applied, title);
            }
            return ok;
        }
        JSONObject task = detail(process, listed);
        if (task == null) { Log.record(label + "：详情回查失败或任务不匹配，未执行"); return false; }
        String key = "memberBrowse::" + process;
        if (finished(task)) {
            if (!store.getString(key).isEmpty()) logConfirmed(title, task, MyUtils.newJSONObject(store.getString(key)).optString("day"));
            store.put(key, null); return false;
        }
        String[] target = target(task);
        if (target == null) return false;
        String stage = progress(task);
        JSONObject state = MyUtils.newJSONObject(store.getString(key));
        Calendar started = MyUtils.getInstance();
        String day = started.get(Calendar.YEAR) + ":" + started.get(Calendar.DAY_OF_YEAR);
        // 已回查同一流程尚未完成；旧日的计时、次数和提交状态不阻断新一天。
        if (!day.equals(state.optString("day"))) state = MyUtils.newJSONObject();
        if (state.has("stage") && !sameProgress(stage, state.optString("stage")) && !advanced(state.optJSONObject("before"), task)) {
            Log.record(label + "：进度变化未确认推进，保留回查状态"); return false;
        }
        if (!sameProgress(stage, state.optString("stage"))) {
            state = MyUtils.newJSONObject().put("stage", stage).put("before", snapshotProgress(task));
        }
        if (state.optBoolean("submitted")) { Log.record(label + "：同阶段已提交，详情尚未确认，不重复执行"); return false; }
        if (skipRejectedStage(key, stage, label)) return false;
        String contract = Arrays.toString(target) + ":" + config(task).optString("browseSeconds");
        if (state.has("contract") && !contract.equals(state.optString("contract"))) {
            store.put(key, null); return false;
        }
        state.put("contract", contract);
        if (!day.equals(state.optString("day"))) state.put("day", day).put("readyAt", 0);
        GameTaskAction game = GameTaskAction.parse(task);
        if (game.kind == GameTaskAction.Kind.DEFERRED || game.kind == GameTaskAction.Kind.COUNTDOWN) {
            return false;
        }
        if (game.kind != GameTaskAction.Kind.NONE) {
            if (!game.perform("member:" + process + ":" + stage, label, null)) return false;
        } else {
            JSONObject config = config(task);
            int seconds = config.has("browseSeconds") ? GameTaskAction.duration(config.opt("browseSeconds")) : 0;
            if (seconds < 0) { Log.record(label + "：浏览时长无效，未执行"); return false; }
            long readyAt = state.optLong("readyAt");
            if (readyAt <= 0) {
                readyAt = System.currentTimeMillis() + seconds * 1000L;
                state.put("readyAt", readyAt);
                if (!store.putVerified(key, state.toString())) return false;
            }
            long wait = readyAt - System.currentTimeMillis();
            if (wait > seconds * 1000L) { Log.record(label + "：计时记录异常，保留待检查"); return false; }
            Log.record(label + "：浏览计时，剩余" + Math.max(0, (wait + 999) / 1000) + "秒");
            TimeUtil.sleep(Math.max(0, wait));
        }
        JSONObject fresh = detail(process, task);
        if (fresh == null) return false;
        if (finished(fresh) || advanced(task, fresh)) { store.put(key, null); logConfirmed(title, fresh, day); return !expired(fresh); }
        Calendar now = MyUtils.getInstance();
        if (started.get(Calendar.YEAR) != now.get(Calendar.YEAR) || started.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR)
                || !sameProgress(stage, progress(fresh)) || !Arrays.equals(target, target(fresh))
                || !config(task).optString("browseSeconds").equals(config(fresh).optString("browseSeconds"))) return false;
        TimeUtil.sleep(0);
        state.put("submitted", true);
        if (!store.putVerified(key, state.toString())) return false;
        Log.record(label + "：计时结束，调用 executeTask，随后回查同一任务详情");
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.executeCurrentMemberTask(target[1], target[2]));
        if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("resultCode"))) {
            state.put("submitted", false); store.putVerified(key, state.toString());
        }
        checkPaused(response);
        TimeUtil.sleep(500);
        JSONObject after = detail(process, task);
        if (after != null && (finished(after) || advanced(task, after))) {
            store.put(key, null); logConfirmed(title, after, day); return !expired(after);
        }
        if (after != null && !GameTaskAction.accepted(response)) {
            if (GameTaskAction.isBusinessRejection(response)) Status.flagToday("agReject::" + key + ":" + normalizeProgress(stage));
            markRejected(task, title, response);
        }
        Log.record(label + "：执行后结果未确认，code=" + response.optString("resultCode", "缺失") + "，后续轮次继续回查");
        return false;
    }

    private static boolean isAd(JSONObject task) {
        if (!Boolean.TRUE.equals(task.opt("adTask"))) return false;
        for (Object target : new Object[]{task.opt("targetBusiness"), config(task).opt("targetBusiness")}) {
            if (target == null || target == JSONObject.NULL || target instanceof String && ((String) target).trim().isEmpty()
                    || target instanceof JSONArray && ((JSONArray) target).length() == 0) continue;
            return false;
        }
        return true;
    }

    private static JSONObject adContract(JSONObject task) throws org.json.JSONException {
        return adContract(task, false);
    }

    private static JSONObject adContract(JSONObject task, boolean explicitOnly) throws org.json.JSONException {
        if (!isAd(task) || TaskAlternative.isTransactionTask(task.optString("bizKey"))
                || TaskAlternative.isTransactionTask(config(task).optString("bizKey"))) return null;
        JSONObject ext = task.optJSONObject("lightsAdExtMap"), cfg = config(task);
        if (ext == null || !(ext.opt("bizId") instanceof String) || ext.optString("bizId").trim().isEmpty()
                || !(ext.opt("adId") instanceof String) || ext.optString("adId").trim().isEmpty()) return null;
        int seconds = cfg.has("browseSeconds") ? GameTaskAction.duration(cfg.opt("browseSeconds")) : 15;
        if (seconds < 0 || GameTaskAction.parse(task).kind != GameTaskAction.Kind.NONE) return null;
        JSONObject award = cfg.optJSONObject("awardParam");
        JSONArray stages = cfg.optJSONArray("stageVOList");
        JSONObject stage = stages == null ? null : stages.optJSONObject(0);
        if (award == null && (cfg.has("awardParam") || cfg.has("stageVOList") && stages == null
                || stages != null && stages.length() > 0 && stage == null)) return null;
        if (award == null && stage != null) {
            award = stage.optJSONObject("awardParam");
            if (stage.has("awardParam") && award == null) return null;
        }
        Object points = award == null ? null : award.opt("awardParamPoint");
        if (award != null && award.has("awardParamPoint") && points == JSONObject.NULL) return null;
        int count = 0;
        if (points != null && points != JSONObject.NULL) {
            if (!(points instanceof String || points instanceof Number)) return null;
            try { count = new java.math.BigDecimal(points.toString()).intValueExact(); }
            catch (NumberFormatException | ArithmeticException e) { return null; }
            if (count < 0) return null;
        }
        JSONObject contract = MyUtils.newJSONObject().put("bizId", ext.optString("bizId")).put("adId", ext.optString("adId"));
        if (!explicitOnly || cfg.has("browseSeconds")) contract.put("seconds", Math.max(15, seconds));
        if (!explicitOnly || points != null && points != JSONObject.NULL) contract.put("awardNum", count);
        return contract;
    }

    private static JSONObject adSnapshot(String id, JSONObject contract, JSONObject baseline) throws Exception {
        // 签到页可能是合同唯一来源；计时或写请求后的确认不能复用旧快照。
        JSONObject matched = null;
        boolean observedProcess = false;
        Set<String> candidates = new HashSet<>();
        JSONObject pending = MyUtils.newJSONObject(RuntimeInfo.getInstance().getString("memberAd::" + id));
        String expectedProcess = pending.optString("processId");
        if (expectedProcess.isEmpty()) expectedProcess = processId(baseline);
        boolean pendingStage = pending.optBoolean("accepted") && !pending.has("stage");
        for (JSONObject row : load(false, true).values()) {
            JSONObject actual = adContract(row);
            if (id.equals(configId(row)) && actual != null && contract.toString().equals(actual.toString())
                    && (expectedProcess.isEmpty() || expectedProcess.equals(processId(row)))) {
                if (expectedProcess.isEmpty() && !processId(row).isEmpty()) {
                    // 未绑定时只采用同阶段的未完成候选，不能借别的流程终态确认本次提交。
                    observedProcess = true;
                    if (finished(row) || !sameProgress(progress(row), progress(baseline))) continue;
                    candidates.add(processId(row));
                }
                if (!pendingStage && !sameProgress(progress(row), progress(baseline)) && !advanced(baseline, row)) continue;
                if (finished(row) && !expectedProcess.isEmpty()) return row;
                if (matched == null || !processId(row).isEmpty()) matched = row;
            }
        }
        if (candidates.size() > 1 || observedProcess && matched != null && processId(matched).isEmpty()) return null;
        return matched;
    }

    private static boolean sameProgress(String first, String second) {
        return normalizeProgress(first).equals(normalizeProgress(second));
    }

    private static String normalizeProgress(String stage) {
        String[] fields = stage.split(":", -1);
        if (fields.length < 2 || fields.length > 4) return stage;
        StringBuilder normalized = new StringBuilder(fields[0]).append(':').append(fields[1]);
        // 周期字段缺失或0都没有周期进度；主计数不套默认值，旧存档也不重写。
        for (int i = 2; i < 4; i++) {
            String raw = i < fields.length ? fields[i] : "";
            try {
                long count = raw.isEmpty() ? 0 : new java.math.BigDecimal(raw).longValueExact();
                if (count < 0) return stage;
                normalized.append(':').append(count);
            } catch (NumberFormatException | ArithmeticException e) { return stage; }
        }
        return normalized.toString();
    }

    private static String adConfirmedKey(String id, String process, String stage, String raw) throws java.security.NoSuchAlgorithmException {
        JSONObject contract = MyUtils.newJSONObject(raw);
        if (!(contract.opt("bizId") instanceof String) || contract.optString("bizId").trim().isEmpty()
                || !(contract.opt("adId") instanceof String) || contract.optString("adId").trim().isEmpty()) throw new IllegalArgumentException("广告确认合同身份无效");
        int seconds = new java.math.BigDecimal(contract.optString("seconds")).intValueExact();
        int award = new java.math.BigDecimal(contract.optString("awardNum")).intValueExact();
        if (seconds < 15 || seconds > 1800 || award < 0) throw new IllegalArgumentException("广告确认合同数值无效");
        String canonical = new JSONArray().put(id).put(process).put(normalizeProgress(stage))
                .put(contract.optString("bizId")).put(contract.optString("adId")).put(seconds).put(award).toString();
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder("memberAdConfirmed::");
        for (byte value : digest) key.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return key.toString();
    }

    private static boolean advancedAfterTerminal(String terminal, String stage) {
        String[] before = normalizeProgress(terminal).split(":", -1), after = normalizeProgress(stage).split(":", -1);
        if (before.length != 4 || after.length != 4) return false;
        for (int i : new int[]{0, 2}) try {
            long oldCount = new java.math.BigDecimal(before[i]).longValueExact(), newCount = new java.math.BigDecimal(after[i]).longValueExact();
            if (oldCount >= 0 && newCount > oldCount) return true;
            long oldTarget = new java.math.BigDecimal(before[i + 1]).longValueExact(), newTarget = new java.math.BigDecimal(after[i + 1]).longValueExact();
            if (oldCount >= 0 && newCount == oldCount && oldTarget > 0 && newTarget > oldTarget) return true;
        } catch (NumberFormatException | ArithmeticException ignored) { }
        return false;
    }

    private static boolean isAdConfirmed(String id, String process, String stage, String contract) throws java.security.NoSuchAlgorithmException {
        String key = adConfirmedKey(id, process, stage, contract);
        if (Status.hasFlagToday(key)) return true;
        if (!process.isEmpty()) {
            String terminalPrefix = adConfirmedKey(id, process, "", contract) + ":terminal:";
            for (String flag : Status.flagsStartingWith(terminalPrefix)) {
                if (!advancedAfterTerminal(flag.substring(terminalPrefix.length()), stage)) return true;
            }
        }
        // 只读兼容今天尚未过期的旧JSON键，新标记只写摘要。
        String prefix = "memberAdConfirmed::" + id + ":" + process + ":", suffix = ":" + contract;
        for (String flag : Status.flagsStartingWith(prefix)) {
            if (!flag.endsWith(suffix) || flag.length() < prefix.length() + suffix.length()) continue;
            String oldStage = flag.substring(prefix.length(), flag.length() - suffix.length());
            if (!oldStage.contains(":")) oldStage += ":";
            if (sameProgress(oldStage, stage)) {
                Status.flagToday(key); return true;
            }
        }
        return false;
    }

    private static boolean skipRejectedStage(String key, String stage, String label) {
        String normalized = normalizeProgress(stage), prefix = "agReject::" + key + ":";
        if (!Status.hasFlagToday(prefix + normalized)) {
            for (String flag : Status.flagsStartingWith(prefix)) {
                if (normalizeProgress(flag.substring(prefix.length())).equals(normalized)) {
                    Status.flagToday(prefix + normalized); break;
                }
            }
        }
        return GameTaskAction.skipRejected(key + ":" + normalized, label);
    }

    private static boolean rememberAdProcess(JSONObject state, JSONObject task, RuntimeInfo store, String key, String label) throws org.json.JSONException {
        String process = processId(task), saved = state.optString("processId");
        if (process.isEmpty() || process.equals(saved)) return true;
        if (!saved.isEmpty()) { Log.record(label + "：广告流程变化，保留原提交记录，未继续写请求"); return false; }
        state.put("processId", process);
        if (store.putVerified(key, state.toString())) return true;
        Log.record(label + "：广告流程身份写盘失败，未继续写请求"); return false;
    }

    private static boolean rememberAdStage(JSONObject state, JSONObject task, RuntimeInfo store, String key, String label) throws org.json.JSONException {
        if (state.has("stage")) return true;
        if (state.optString("processId").isEmpty() || !state.optString("processId").equals(processId(task)) || finished(task)) {
            Log.record(label + "：新报名流程的有效阶段尚未核实，保留申请和计时，未请求完成"); return false;
        }
        state.put("stage", progress(task)).put("before", snapshotProgress(task));
        if (store.putVerified(key, state.toString())) return true;
        Log.record(label + "：新报名流程阶段写盘失败，保留申请，未请求完成"); return false;
    }

    private static boolean markAdHandled(String id, JSONObject state, String stage) throws Exception {
        String contract = state.optString("contract"), key = "memberAd::" + id;
        Set<String> flags = new LinkedHashSet<>();
        flags.add(adConfirmedKey(id, "", stage, contract));
        if (!state.optString("processId").isEmpty()) flags.add(adConfirmedKey(id, state.optString("processId"), stage, contract));
        if (!state.optString("requestProcessId").isEmpty() && !state.optString("requestStage").isEmpty()) {
            flags.add(adConfirmedKey(id, state.optString("requestProcessId"), state.optString("requestStage"), contract));
            flags.add(adConfirmedKey(id, "", state.optString("requestStage"), contract));
        }
        if (state.has("confirmedTerminal")) {
            flags.add(adConfirmedKey(id, state.optString("processId"), "", contract) + ":terminal:" + state.optString("confirmedTerminal"));
        }
        RuntimeInfo store = RuntimeInfo.getInstance();
        state.put("confirmed", true).put("confirmedStage", stage);
        boolean confirmedStored = store.putVerified(key, state.toString());
        if (!Status.flagTodayVerified(flags, state.optString("day"))) {
            Log.record(TAG + "：广告防重标记未落盘或已跨日，保留" + (confirmedStored ? "已确认记录" : "原待确认状态")
                    + "，本轮不追加请求"); return false;
        }
        store.put(key, null);
        return true;
    }

    private static boolean hasReplyProgress(JSONObject task) {
        // 回执可能只回传部分维度，不能把未回传的周期当成0。
        if (!task.has("currentCount") || !task.has("targetCount")) return false;
        JSONObject ext = task.optJSONObject("extInfo");
        if (ext == null || !ext.has("PERIOD_CURRENT_COUNT") || !ext.has("PERIOD_TARGET_COUNT")) return false;
        for (int i = 0; i < 2; i++) {
            JSONObject source = i == 0 ? task : ext;
            String current = i == 0 ? "currentCount" : "PERIOD_CURRENT_COUNT", target = i == 0 ? "targetCount" : "PERIOD_TARGET_COUNT";
            for (String field : new String[]{current, target}) {
                Object value = source.opt(field);
                if (!(value instanceof String || value instanceof Number)) return false;
                try {
                    long count = new java.math.BigDecimal(value.toString()).longValueExact();
                    if (count < 0 || i == 0 && !value.toString().equals(Long.toString(count))) return false;
                }
                catch (NumberFormatException | ArithmeticException e) { return false; }
            }
        }
        return true;
    }

    private static boolean performAd(JSONObject listed, String title) throws Exception {
        if (expired(listed)) return false;
        JSONObject baseline = listed;
        String label = TAG + "[" + title + "]", id = configId(listed);
        JSONObject contract = adContract(listed);
        if (contract == null) { Log.record(label + "：广告参数无效或非纯浏览合同，未发送申请请求"); return false; }
        // 其他来源的旧展示行不能让已确认的同阶段任务重新申请。
        Calendar now = MyUtils.getInstance();
        String day = now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR), stage = progress(listed);
        String key = "memberAd::" + id;
        RuntimeInfo store = RuntimeInfo.getInstance();
        JSONObject state = MyUtils.newJSONObject(store.getString(key));
        if (day.equals(state.optString("day")) && state.optBoolean("confirmed")) {
            markAdHandled(id, state, state.optString("confirmedStage"));
            return false;
        }
        if (isAdConfirmed(id, processId(listed), progress(listed), contract.toString())) return false;
        if (day.equals(state.optString("day")) && !state.optString("processId").isEmpty()
                && !rememberAdProcess(state, listed, store, key, label)) return false;
        if (day.equals(state.optString("day")) && state.optBoolean("applied") && !state.has("stage")) {
            if (!contract.toString().equals(state.optString("contract"))) {
                Log.record(label + "：新报名流程合同不匹配，保留申请和计时，未请求完成"); return false;
            }
            if (!rememberAdStage(state, listed, store, key, label)) return false;
        }
        if (day.equals(state.optString("day")) && state.has("stage") && !sameProgress(stage, state.optString("stage"))
                && !advanced(state.optJSONObject("before"), listed)) {
            Log.record(label + "：广告进度变化未确认推进，保留原提交记录"); return false;
        }
        if (!day.equals(state.optString("day")) || !sameProgress(stage, state.optString("stage"))) {
            state = MyUtils.newJSONObject().put("day", day).put("stage", stage).put("contract", contract.toString())
                    .put("processId", processId(listed)).put("before", snapshotProgress(listed));
        }
        if (!contract.toString().equals(state.optString("contract"))) {
            Log.record(label + "：广告合同变化，等待后续列表确认"); return false;
        }
        if (!rememberAdProcess(state, listed, store, key, label)) return false;
        if (state.optBoolean("submitted")) { Log.record(label + "：广告完成请求已提交，列表尚未确认，不重复提交"); return false; }
        if (skipRejectedStage(key, stage, label)) return false;
        if (!state.optBoolean("applied")) {
            TimeUtil.sleep(0);
            state.put("applied", true);
            if (!store.putVerified(key, state.toString())) { Log.record(label + "：广告申请预留写盘失败，未发送申请请求"); return false; }
            JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.applyAdTask(contract.optString("bizId"), contract.optString("adId"),
                    contract.optInt("awardNum"), listed.optJSONObject("lightsAdExtMap")));
            if (!GameTaskAction.accepted(response)) {
                if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("error"))
                        || "RPC_SKIPPED".equals(response.optString("resultCode"))) state.put("applied", false);
                store.putVerified(key, state.toString());
                checkPaused(response);
                markRejected(listed, title, response);
                if (GameTaskAction.isBusinessRejection(response)) Status.flagToday("agReject::" + key + ":" + normalizeProgress(stage));
                Log.record(label + "：广告申请未确认，保留后续回查"); return false;
            }
            checkPaused(response);
            JSONObject data = response.optJSONObject("resultData");
            JSONObject applied = data == null ? null : data.optJSONObject("taskProcessVO");
            if (applied != null && !processId(applied).isEmpty()) {
                if (!configId(applied).isEmpty() && !id.equals(configId(applied))) {
                    Log.record(label + "：广告报名回执任务身份不匹配，保留申请记录，未请求完成"); return false;
                }
                if (!processId(applied).equals(state.optString("processId"))) {
                    String previousProcess = state.optString("processId");
                    if (!previousProcess.isEmpty()) {
                        state.put("requestProcessId", previousProcess).put("requestStage", state.optString("stage"));
                    }
                    state.put("processId", processId(applied));
                    if (!previousProcess.isEmpty()) {
                        if (hasReplyProgress(applied)) {
                            state.put("stage", progress(applied)).put("before", snapshotProgress(applied));
                            baseline = applied; stage = progress(applied);
                        }
                        else { state.remove("stage"); state.remove("before"); }
                        Log.record(label + "：报名回执已确认流程切换，保留本次申请和计时，核对新流程阶段");
                    }
                }
            }
            state.put("accepted", true).put("readyAt", System.currentTimeMillis() + contract.optInt("seconds") * 1000L);
            if (!store.putVerified(key, state.toString())) { Log.record(label + "：广告计时写盘失败，未请求完成"); return false; }
        }
        if (!state.optBoolean("accepted")) { Log.record(label + "：广告申请结果未知，等待列表回查，不重复申请"); return false; }
        long readyAt = state.optLong("readyAt");
        if (!(state.opt("readyAt") instanceof Number) || readyAt <= 0) {
            Log.record(label + "：广告计时记录无效，未请求完成"); return false;
        }
        long wait = readyAt - System.currentTimeMillis();
        if (wait > contract.optInt("seconds") * 1000L) { Log.record(label + "：广告计时超出合同范围，未请求完成"); return false; }
        Log.record(label + "：广告浏览计时，剩余" + Math.max(0, (wait + 999) / 1000) + "秒");
        TimeUtil.sleep(Math.max(0, wait));
        JSONObject fresh = adSnapshot(id, contract, baseline);
        if (fresh == null) { Log.record(label + "：广告计时后未核实相同合同、流程及有效阶段，未请求完成"); return false; }
        if (finished(fresh)) return !expired(fresh);
        if (!state.has("stage")) {
            // load可能已凭已有完成记录清理状态，不能在补阶段时重新写回。
            if (store.getString(key).isEmpty()) return false;
            Calendar phaseDay = MyUtils.getInstance();
            if (!day.equals(phaseDay.get(Calendar.YEAR) + ":" + phaseDay.get(Calendar.DAY_OF_YEAR))) return false;
            if (!rememberAdStage(state, fresh, store, key, label)) return false;
            baseline = fresh; stage = progress(fresh);
        }
        if (advanced(baseline, fresh)) return true;
        Calendar end = MyUtils.getInstance();
        if (!day.equals(end.get(Calendar.YEAR) + ":" + end.get(Calendar.DAY_OF_YEAR)) || !sameProgress(stage, progress(fresh))) return false;
        if (!rememberAdProcess(state, fresh, store, key, label)) return false;
        if (isAdConfirmed(id, state.optString("processId"), stage, contract.toString())) {
            if (!store.getString(key).isEmpty()) {
                if (markAdHandled(id, state, stage)) {
                    Log.record(label + "：已有完成凭据，本次未发送完成请求，已清理待处理记录");
                }
            }
            return false;
        }
        if (skipRejectedStage(key, stage, label)) return false;
        TimeUtil.sleep(0);
        state.put("submitted", true);
        if (!store.putVerified(key, state.toString())) { Log.record(label + "：广告完成预留写盘失败，未发送完成请求"); return false; }
        JSONObject response = MyUtils.newJSONObject(AntMemberRpcCall.finishAdTask(contract.optString("bizId")));
        if (Boolean.FALSE.equals(response.opt("success")) || "RPC_SKIPPED".equals(response.optString("error"))
                || "RPC_SKIPPED".equals(response.optString("resultCode"))) {
            state.put("submitted", false); store.putVerified(key, state.toString());
        }
        checkPaused(response);
        JSONObject after = adSnapshot(id, contract, baseline);
        if (after != null && (finished(after) || advanced(baseline, after))) return !expired(after);
        if (after != null) {
            markRejected(listed, title, response);
            if (GameTaskAction.isBusinessRejection(response)) Status.flagToday("agReject::" + key + ":" + normalizeProgress(stage));
        }
        Log.record(label + "：广告完成请求结果未确认，后续继续按列表回查，不预记配置积分");
        return false;
    }

    private static void markRejected(JSONObject task, String title, JSONObject response) {
        if (!GameTaskAction.accepted(response) && !"RPC_SKIPPED".equals(response.optString("resultCode"))
                && !"RPC_SKIPPED".equals(response.optString("error")))
            MessageUtil.checkResultCodeAndMarkTaskBlackList(isAd(task) ? "AntMemberAdTaskList" : "AntMemberTaskList", title, response);
    }

    private static void logConfirmed(String title, JSONObject task, String businessDay) {
        Calendar now = MyUtils.getInstance();
        if (!(now.get(Calendar.YEAR) + ":" + now.get(Calendar.DAY_OF_YEAR)).equals(businessDay)) {
            Log.record(TAG + "[" + title + "]：记录不属于当前业务日，只清理旧记录，不计入今日防重或奖励"); return;
        }
        if (expired(task)) { Log.record(TAG + "[" + title + "]：任务已过期，结束旧流程回查，未确认完成或奖励"); return; }
        String result = completed(task) ? "完成" : "阶段推进";
        Log.record(TAG + "[" + title + "]：详情已确认" + result);
        JSONObject ext = task.optJSONObject("extInfo");
        String points = ext == null ? "" : ext.optString("awardCurrentPoint");
        Log.other("会员任务🎖️" + result + "[" + title + "]" + (points.isEmpty() ? "" : "#详情奖励积分[" + points + "]"));
    }
}
