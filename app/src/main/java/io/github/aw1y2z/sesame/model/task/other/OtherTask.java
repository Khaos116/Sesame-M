package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.OtherEntity;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.Status;

/**
 * 其他任务（信用2101 + 好家无忧卡）。移植自 GR 分支，见 doc/MyFix.md。
 * <p>
 * 原版没有请求预算/风控冷却保护，这次移植按用户要求补上：每轮请求数上限、请求间隔、
 * 命中风控自动暂停 24 小时（见 {@link OtherRequestGate}），不改动原有的业务判断逻辑。
 */
public class OtherTask extends ModelTask {
    private static final String TAG = "OtherTask";

    private BooleanModelField credit2101;
    private SelectModelField credit2101Options;
    private SelectAndCountModelField creditEventOptions;
    private BooleanModelField haojiaWuyou;

    private static final List<OtherEntity> CREDIT_TASKS = Arrays.asList(
            new OtherEntity("AUTO_OPEN_CHEST", "自动开宝箱"),
            new OtherEntity("AUTO_SIGN_IN", "自动签到"),
            new OtherEntity("DAILY_TASKS", "自动完成任务"),
            new OtherEntity("UPGRADE_TALENT", "自动升级天赋"),
            new OtherEntity("CHAPTER_TASKS", "图鉴章节合成"));
    private static final List<OtherEntity> CREDIT_EVENTS = Arrays.asList(
            new OtherEntity("MINI_GAME_ELIMINATE", "消除小游戏"),
            new OtherEntity("MINI_GAME_COLLECTYJ", "收集小游戏"),
            new OtherEntity("MINI_GAME_MATCH3", "击杀小游戏"),
            new OtherEntity("GOLD_MARK", "金色印记"),
            new OtherEntity("BLACK_MARK", "黑色印记"),
            new OtherEntity("SPACE_TIME_GATE", "时空之门"));

    private OtherRequestGate gate;

    @Override
    public String getName() {
        return "其他任务";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(credit2101 = new BooleanModelField("credit2101", "信用2101", false));
        fields.addField(credit2101Options = new SelectModelField("credit2101Options", "信用2101 | 任务选项", new LinkedHashSet<>(), CREDIT_TASKS));
        fields.addField(creditEventOptions = new SelectAndCountModelField("CreditOptions", "信用2101 | 事件类型", new LinkedHashMap<>(), CREDIT_EVENTS, "设置运行次数(-1为不限制)"));
        fields.addField(haojiaWuyou = new BooleanModelField("haojiaWuyou", "好家无忧卡", false));
        return fields;
    }

    @Override
    public Boolean check() {
        return isEnable() && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME && !OtherRequestGate.isCoolingDown();
    }

    @Override
    public void run() {
        if (!check()) return;
        gate = new OtherRequestGate();
        try {
            if (credit2101.getValue()) {
                runCredit2101();
            }
            if (haojiaWuyou.getValue()) {
                runHaoJia();
            }
        } catch (Throwable t) {
            Log.i(TAG, "其他任务执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private boolean selected(String id) {
        return credit2101Options.getValue().contains(id);
    }

    private void runCredit2101() {
        Log.record("信用2101开始执行");
        try {
            JSONObject account = new JSONObject(gate.call("查询账户资产", Credit2101RpcCall::queryAccountAsset));
            if (!ok(account)) {
                return;
            }
            int lotteryNo = account.optInt("lotteryNo", 0);
            if (selected("AUTO_OPEN_CHEST") && lotteryNo > 0) {
                for (int i = 0; i < lotteryNo; i++) {
                    JSONObject chest = new JSONObject(gate.call("开宝箱", Credit2101RpcCall::triggerBenefit));
                    if (!ok(chest)) {
                        break;
                    }
                    Log.record("信用2101开宝箱 " + (i + 1) + "/" + lotteryNo);
                    sleep(800);
                }
            }
            if (selected("AUTO_SIGN_IN")) {
                creditSignIn();
            }
            if (selected("DAILY_TASKS")) {
                creditTasks();
            }
            if (selected("UPGRADE_TALENT")) {
                creditTalent();
            }
            if (selected("CHAPTER_TASKS")) {
                creditChapters();
            }
            creditGuardAndVisit();
            creditEvents(account);
        } catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) {
            // gate 已经记录过原因，这里不重复打日志。
        } catch (Throwable t) {
            Log.i(TAG, "信用2101执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private void creditSignIn() throws Exception {
        JSONObject data = new JSONObject(gate.call("查询签到数据", Credit2101RpcCall::querySignInData));
        if (!ok(data)) return;
        int total = data.optInt("totalLoginDays", 0);
        JSONArray signed = data.optJSONArray("signInDays");
        for (int i = 0; signed != null && i < signed.length(); i++) {
            if (signed.optInt(i, -1) == total) return;
        }
        if (total > 0 && ok(new JSONObject(gate.call("签到", () -> Credit2101RpcCall.userSignIn(total))))) {
            Log.record("信用2101签到成功");
        }
    }

    private void creditTasks() throws Exception {
        JSONObject data = new JSONObject(gate.call("查询用户任务", Credit2101RpcCall::queryUserTask));
        if (!ok(data)) return;
        JSONArray list = data.optJSONArray("taskList");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject task = list.optJSONObject(i);
            if (task == null) continue;
            String id = task.optString("taskConfigId");
            String name = task.optString("taskName", id);
            if (id.isEmpty()) continue;
            if ("INIT".equals(task.optString("taskStatus"))) {
                JSONObject claim = new JSONObject(gate.call("完成任务", () -> Credit2101RpcCall.operateTask("TASK_CLAIM", id)));
                if (ok(claim)) Log.record("信用2101完成任务 " + name);
            }
            if ("FINISH".equals(task.optString("taskStatus")) && "UNLOCKED".equals(task.optString("awardStatus"))) {
                JSONObject award = new JSONObject(gate.call("领取任务奖励", () -> Credit2101RpcCall.awardTask(id)));
                if (ok(award) || award.optBoolean("awardSuccess")) Log.record("信用2101领取任务奖励 " + name);
            }
            sleep(500);
        }
    }

    private void creditTalent() throws Exception {
        JSONObject data = new JSONObject(gate.call("查询天赋", Credit2101RpcCall::queryRelationTalent));
        if (!ok(data)) return;
        int points = data.optInt("availablePoint", 0);
        JSONArray list = data.optJSONArray("talentAttributeVOList");
        if (list == null) return;
        for (int i = 0; i < list.length() && points > 0; i++) {
            JSONObject talent = list.optJSONObject(i);
            if (talent == null || talent.optInt("attributeLevel", 5) >= 5) continue;
            String attr = talent.optString("attributeType");
            if (attr.isEmpty()) continue;
            String tree = attr.contains("_") ? attr.substring(0, attr.indexOf('_')) : attr;
            int nextLevel = talent.optInt("attributeLevel") + 1;
            JSONObject result = new JSONObject(gate.call("升级天赋", () -> Credit2101RpcCall.upgradeTalentAttribute(attr, tree, nextLevel)));
            if (ok(result) || result.optBoolean("success")) points--;
            sleep(500);
        }
    }

    private void creditChapters() throws Exception {
        JSONObject data = new JSONObject(gate.call("查询图鉴进度", Credit2101RpcCall::queryChapterProgress));
        if (!ok(data)) return;
        JSONArray list = data.optJSONArray("charterProgress");
        if (list == null) return;
        for (int i = 0; i < list.length(); i++) {
            JSONObject chapter = list.optJSONObject(i);
            if (chapter == null) continue;
            String id = chapter.optString("chapter");
            int obtained = chapter.optInt("obtainedCardCount", 0);
            int total = chapter.optInt("cardCount", 0);
            String awardStatus = chapter.optString("awardStatus");
            if (id.isEmpty()) continue;
            if ("LOCKED".equals(awardStatus) && total > 0 && obtained >= total) {
                if (ok(new JSONObject(gate.call("图鉴章节合成", () -> Credit2101RpcCall.completeChapterAction("CHAPTER_COMPLETE", id))))) Log.record("信用2101图鉴章节合成 " + id);
            } else if ("UNLOCKED".equals(awardStatus)) {
                if (ok(new JSONObject(gate.call("领取图鉴奖励", () -> Credit2101RpcCall.completeChapterAction("CHAPTER_AWARD", id))))) Log.record("信用2101领取图鉴奖励 " + id);
            }
            sleep(500);
        }
    }

    private void creditGuardAndVisit() throws Exception {
        JSONObject guard = new JSONObject(gate.call("查询守护印记", Credit2101RpcCall::queryGuardMarkList));
        if (ok(guard) && guard.optBoolean("hasClaimGuardMark")) {
            JSONObject claim = new JSONObject(gate.call("领取守护印记奖励", Credit2101RpcCall::claimGuardMarkAward));
            if (ok(claim)) Log.record("信用2101领取守护印记奖励 " + claim.optInt("cnt", 0));
        }
        JSONObject popup = new JSONObject(gate.call("查询回访弹窗", () -> Credit2101RpcCall.queryPopupView("1")));
        if (ok(popup)) {
            JSONObject view = popup.optJSONObject("popupViewVO");
            JSONObject result = view == null ? null : view.optJSONObject("resultMap");
            if (result != null) {
                int energy = result.optInt("energyRecover", 0);
                int explore = result.optInt("exploreRecover", 0);
                if (energy > 0 || explore > 0) Log.record("信用2101回访恢复 注能+" + energy + " 探索+" + explore);
            }
        }
    }

    private void creditEvents(JSONObject account) throws Exception {
        if (creditEventOptions.getValue().isEmpty()) return;
        JSONObject accountVo = account.optJSONObject("accountVO");
        String city = accountVo == null ? "" : accountVo.optString("cityCode");
        if (city.isEmpty()) return;
        double latitude = account.optDouble("latitude", accountVo == null ? 0d : accountVo.optDouble("latitude", 0d));
        double longitude = account.optDouble("longitude", accountVo == null ? 0d : accountVo.optDouble("longitude", 0d));
        if (latitude == 0d || longitude == 0d) {
            Log.record("信用2101事件跳过：当前响应没有有效经纬度，等待设备定位数据");
            return;
        }
        JSONObject energyVo = account.optJSONObject("energyStaminaVO");
        int energy = energyVo == null ? 0 : energyVo.optInt("staminaAvailable", 0);
        double lat = latitude, lon = longitude;
        JSONObject root = new JSONObject(gate.call("查询格子事件", () -> Credit2101RpcCall.queryGridEvent(city, lat, lon, false)));
        JSONArray events = root.optJSONArray("gridEventVOList");
        if (events == null || events.length() == 0) {
            JSONObject explored = new JSONObject(gate.call("探索格子事件", () -> Credit2101RpcCall.exploreGridEvent(city, lat, lon)));
            if (ok(explored)) root = new JSONObject(gate.call("查询格子事件", () -> Credit2101RpcCall.queryGridEvent(city, lat, lon, false)));
            events = root.optJSONArray("gridEventVOList");
        }
        if (events == null) return;
        Map<String, Integer> completed = new LinkedHashMap<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null || "FINISHED".equals(event.optString("eventStatus"))) continue;
            String type = event.optString("eventType");
            Integer configured = creditEventOptions.getValue().get(type);
            if (configured == null || configured == 0) continue;
            String eventFlag = "credit2101:event:" + type + ":" + event.optString("eventId");
            if (Status.hasFlagToday(eventFlag)) continue;
            int done = completed.containsKey(type) ? completed.get(type) : 0;
            if (configured > 0 && done >= configured) continue;
            int consumed = handleCreditEvent(type, event, city, latitude, longitude, energy);
            if (consumed >= 0) {
                completed.put(type, done + 1);
                Status.flagToday(eventFlag);
                if ("BLACK_MARK".equals(type)) energy = Math.max(0, energy - consumed);
            }
            sleep(800);
        }
        for (Map.Entry<String, Integer> entry : completed.entrySet()) Log.record("信用2101事件完成 " + entry.getKey() + " x" + entry.getValue());
    }

    private int handleCreditEvent(String type, JSONObject event, String city, double latitude, double longitude, int energy) throws Exception {
        String batchNo = event.optString("batchNo");
        String eventId = event.optString("eventId");
        if (eventId.isEmpty()) return -1;
        if ("GOLD_MARK".equals(type)) return ok(new JSONObject(gate.call("收集金色印记", () -> Credit2101RpcCall.collectCredit(batchNo, eventId, city, latitude, longitude)))) ? 0 : -1;
        if ("BLACK_MARK".equals(type)) return handleBlackMark(eventId, energy);
        if ("SPACE_TIME_GATE".equals(type)) return handleSpaceTimeGate(batchNo, eventId, city, latitude, longitude) ? 0 : -1;

        JSONObject config = event.optJSONObject("eventConfig");
        String stageId = config == null ? "" : config.optString("id");
        if (stageId.isEmpty() || !ok(new JSONObject(gate.call("开始小游戏", () -> Credit2101RpcCall.eventGameStart(batchNo, eventId, stageId))))) return -1;
        if ("MINI_GAME_COLLECTYJ".equals(type)) {
            JSONArray awards = config.optJSONArray("award");
            JSONObject award = awards == null ? null : awards.optJSONObject(0);
            int amount = parseInt(award == null ? "0" : award.optString("awardAmount"));
            return ok(new JSONObject(gate.call("完成收集小游戏", () -> Credit2101RpcCall.eventGameCompleteCollectYj(batchNo, eventId, stageId, amount)))) ? 0 : -1;
        }
        JSONObject ext = null;
        if ("MINI_GAME_MATCH3".equals(type)) {
            ext = new JSONObject();
            JSONArray awards = config.optJSONArray("award");
            JSONObject award = awards == null ? null : awards.optJSONObject(0);
            if (award != null && !award.optString("awardType").isEmpty()) ext.put(award.optString("awardType"), parseInt(award.optString("awardAmount")));
            String monsters = config.optString("monster");
            ext.put("killCount", monsters.isEmpty() ? 0 : monsters.split("&").length);
        }
        JSONObject extFinal = ext;
        return ok(new JSONObject(gate.call("完成小游戏", () -> Credit2101RpcCall.eventGameComplete(batchNo, eventId, stageId, extFinal)))) ? 0 : -1;
    }

    private int handleBlackMark(String eventId, int energy) throws Exception {
        JSONObject data = new JSONObject(gate.call("查询黑色印记", () -> Credit2101RpcCall.queryBlackMarkEvent(eventId)));
        if (!ok(data)) return -1;
        JSONObject assistant = data.optJSONObject("assistantVO");
        if (assistant == null) return -1;
        JSONArray users = assistant.optJSONArray("assistantUserInfoList");
        boolean self = false;
        for (int i = 0; users != null && i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);
            if (user != null && user.optBoolean("self")) self = true;
        }
        int remainingEnergy = energy;
        if (!self) {
            if (remainingEnergy < 10 || (users != null && users.length() >= 4)) return -1;
            if (!ok(new JSONObject(gate.call("加入黑色印记", () -> Credit2101RpcCall.joinBlackMarkEvent(10, eventId))))) return -1;
            remainingEnergy -= 10;
            data = new JSONObject(gate.call("查询黑色印记", () -> Credit2101RpcCall.queryBlackMarkEvent(eventId)));
            assistant = data.optJSONObject("assistantVO");
            if (assistant == null) return 10;
        }
        int need = assistant.optInt("totalAssistantCount", 0) - assistant.optInt("currAssistantCount", 0);
        if (need <= 0) return self ? 0 : 10;
        int needFinal = need;
        return remainingEnergy >= need && ok(new JSONObject(gate.call("充能黑色印记", () -> Credit2101RpcCall.chargeBlackMarkEvent(needFinal, eventId)))) ? (self ? need : need + 10) : -1;
    }

    private boolean handleSpaceTimeGate(String batchNo, String eventId, String city, double latitude, double longitude) throws Exception {
        if (!ok(new JSONObject(gate.call("查询时空之门", () -> Credit2101RpcCall.queryEventGate(batchNo, eventId, city, latitude, longitude))))) return false;
        int[] storyIds = {1001011,1001022,1001034,1001043,2001011,2001019,2001026,2001035,3001011,3001020,3001027,3001036,4001010,4001018,4001027,4001035,5001009,5001016,5001025,5001034,6001010,6001019,6001026,6001033,7001010,7001015,7001026,7001033};
        boolean completed = false;
        for (int storyId : storyIds) {
            JSONObject result = new JSONObject(gate.call("完成时空之门", () -> Credit2101RpcCall.completeEventGate(batchNo, eventId, city, latitude, longitude, String.valueOf(storyId))));
            if (ok(result)) completed = true;
        }
        return completed;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return 0; }
    }

    private void runHaoJia() {
        Log.record("好家无忧卡开始执行");
        try {
            JSONObject sign = new JSONObject(gate.call("查询好家无忧卡签到", HaoJiaRpcCall::querySignIn));
            if (ok(sign)) {
                JSONObject component = component(sign, "independent_component_sign_in_00966139_independent_component_sign_in_recall");
                JSONObject content = component == null ? null : component.optJSONObject("content");
                JSONArray orders = content == null ? null : content.optJSONArray("playSignInOrderInfoList");
                if (orders != null && orders.length() > 0) {
                    JSONObject order = orders.optJSONObject(0);
                    JSONObject template = order == null ? null : order.optJSONObject("playSignInTemplateInfo");
                    String code = template == null ? "" : template.optString("code");
                    JSONArray records = order == null ? null : order.optJSONArray("signInRecordInfoList");
                    if (!code.isEmpty() && !hasSignedToday(records)) {
                        JSONObject result = new JSONObject(gate.call("好家无忧卡签到", () -> HaoJiaRpcCall.doSignIn(code)));
                        if (ok(result)) Log.record("好家无忧卡签到完成");
                    }
                }
            }
            JSONObject tasks = new JSONObject(gate.call("查询好家无忧卡任务", HaoJiaRpcCall::queryTaskList));
            JSONObject component = component(tasks, "independent_component_task_reward_00793835_independent_component_task_reward_query");
            JSONObject content = component == null ? null : component.optJSONObject("content");
            JSONArray list = content == null ? null : content.optJSONArray("playTaskOrderInfoList");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null || !"init".equals(task.optString("taskStatus")) || "eventPush".equals(task.optString("advanceType"))) continue;
                JSONObject display = task.optJSONObject("displayInfo");
                String name = display == null ? "" : display.optString("activityName");
                if (containsRisk(name)) continue;
                String code = task.optString("code");
                int browseTime = display == null ? 0 : display.optInt("browseTime", 0);
                if (browseTime > 0) sleep(browseTime * 1000L);
                if (!code.isEmpty() && ok(new JSONObject(gate.call("好家无忧卡完成任务", () -> HaoJiaRpcCall.applyTask(code))))) Log.record("好家无忧卡完成任务 " + name);
            }
        } catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) {
            // gate 已经记录过原因，这里不重复打日志。
        } catch (Throwable t) {
            Log.i(TAG, "好家无忧卡执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private static JSONObject component(JSONObject root, String key) {
        JSONObject components = root == null ? null : root.optJSONObject("components");
        return components == null ? null : components.optJSONObject(key);
    }

    private static boolean containsRisk(String name) {
        return name.contains("流量") || name.contains("话费") || name.contains("理财") || name.contains("保险") || name.contains("购车") || name.contains("开通") || name.contains("办理") || name.contains("咨询") || name.contains("黄金");
    }

    private static boolean hasSignedToday(JSONArray records) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Shanghai"));
        String today = format.format(new Date());
        for (int i = 0; records != null && i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null) continue;
            String date = record.optString("date").replaceAll("[^0-9]", "");
            if (today.equals(date)) return true;
        }
        return false;
    }

    private static boolean ok(JSONObject jo) {
        return jo != null && (jo.optBoolean("success") || jo.optBoolean("isSuccess") || "SUCCESS".equalsIgnoreCase(jo.optString("resultCode")) || "200".equals(jo.optString("resultCode")) || "处理成功".equals(jo.optString("desc")));
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
