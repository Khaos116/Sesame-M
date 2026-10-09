package io.github.aw1y2z.sesame.model.task.other;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;

/**
 * 其他任务（好家无忧卡）。移植自 GR 分支，见 docs/MyFix.md。
 * <p>
 * 保留每轮请求上限、请求间隔；风控退避统一由RpcRequestGuard按账号和接口处理。
 */
public class OtherTask extends ModelTask {
    private static final String TAG = "OtherTask";

    @Override
    protected boolean supportsManualAction(String action) { return "clearFriendReceipts".equals(action); }

    @Override
    protected void runManualAction(String action) {
        if (supportsManualAction(action)) new FriendActivityTasks(new SjActivityTasks(new OtherRequestGate(), 0)).clearReceipts();
    }

    private BooleanModelField haojiaWuyou;
    private BooleanModelField haojiaCoinSign, haojiaCoinBrowse, haojiaCoinRewards;
    private IntegerModelField haojiaCoinBudget;
    private BooleanModelField hundredCardSign, hundredCardRewards, huaHuaCardFlip, huaHuaCardMerge, huaHuaCardTasks;
    private BooleanModelField hundredCardSelectedTasks, hundredCardSelectedSignup, hundredCardAutoTasks;
    private StringModelField hundredCardTaskTargets;
    private IntegerModelField legacyCardDailyBudget;
    private BooleanModelField shenQuanSign, shenQuanTasks, shenQuanDraw, mileageExchange, huaBeiIntimacy, gameCenterP2E, leiYouJiTasks, leiYouJiRide;
    private StringModelField shenQuanLocation, mileageExchangeCodes, mileageCityCode;
    private IntegerModelField sjActivityDailyBudget;
    private BooleanModelField gameCenterP2EBrowse, luckyCard;

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
        fields.addField(haojiaWuyou = new BooleanModelField("haojiaWuyou", "好家无忧卡", false));
        fields.addField(haojiaCoinSign = new BooleanModelField("haojiaCoinSign", "好家缴费金 | 签到", false));
        fields.addField(haojiaCoinBrowse = new BooleanModelField("haojiaCoinBrowse", "好家缴费金 | 浏览任务（按要求完整等待）", false));
        fields.addField(haojiaCoinRewards = new BooleanModelField("haojiaCoinRewards", "好家缴费金 | 已完成任务奖励", false));
        fields.addField(haojiaCoinBudget = new IntegerModelField("haojiaCoinBudget", "好家缴费金 | 每日操作尝试上限（0不执行）", 30, 0, 50));
        fields.addField(hundredCardSign = new BooleanModelField("hundredCardSign", "百次立减卡 | 当前活动签到", false));
        fields.addField(hundredCardRewards = new BooleanModelField("hundredCardRewards", "百次立减卡 | 已完成任务奖励", false));
        fields.addField(hundredCardSelectedTasks = new BooleanModelField("hundredCardSelectedTasks", "百次立减卡 | 明确选定任务发送及状态回查", false)
                .setDescription("空目标列表或旧卡预算0时只刷新任务目录，运行日志提供可复制目标JSON。正数预算只发送本人明确选定的任务并按服务端browseTime完整等待；不打开页面、不宣称实际浏览，排除付款、注册、邀请、开通等任务。"));
        fields.addField(hundredCardTaskTargets = new StringModelField("hundredCardTaskTargets", "百次立减卡 | 选定任务JSON列表", "[]"));
        hundredCardTaskTargets.setDescription("默认[]只刷新目录。可从运行日志复制格式：[{\"taskId\":\"当前任务ID\",\"taskCenId\":\"当前任务中心ID\"}]，最多20项。仅当前账号实时唯一匹配且等待1至300秒的任务；需报名时须另开选定任务报名，报名后先回查NOT_DONE/TODO及报名要求解除。报名、发送、领取共用旧卡每日预算，各计一次，未知回执停止写入。");
        fields.addField(hundredCardSelectedSignup = new BooleanModelField("hundredCardSelectedSignup", "百次立减卡 | 明确选定任务报名及回查", false)
                .setDescription("配合选定任务列表。只处理当前查询明确NONE_SIGNUP或需要报名的选定任务；同ID/中心及原任务合同不变，报名后转为NOT_DONE/TODO且needSignUp不再为true才继续完整等待。仅请求受理不算报名完成；每次报名预留一次预算，未确认跨日不重发。")
                .setDependsOn("hundredCardSelectedTasks"));
        fields.addField(hundredCardAutoTasks = new BooleanModelField("hundredCardAutoTasks", "百次立减卡 | 当前活动自动任务", false)
                .setDescription("按来源流程自动选择当前账号有明确1～300秒等待要求的任务，排除TRANSFORMER、COMMON_EVENT_TASK及付款、开通等业务。报名受理后仅本轮继续完整等待，发送及领奖回查同任务状态；每次报名、发送、领奖共用旧卡每日预算，未确认回执不自动重发。不代表实际打开页面浏览。"));
        fields.addField(huaHuaCardFlip = new BooleanModelField("huaHuaCardFlip", "花花卡 | 当前活动已有免费次数翻卡", false));
        fields.addField(huaHuaCardMerge = new BooleanModelField("huaHuaCardMerge", "花花卡 | 当前活动已有碎片合卡", false));
        fields.addField(huaHuaCardTasks = new BooleanModelField("huaHuaCardTasks", "花花卡 | 当前活动任务报名、上报及领奖", false)
                .setDescription("按原活动queryV2→signup/send→award流程处理当前账号任务，跳过SCENE_TASK及付款、开通等任务；每次请求各占一次旧卡每日预算。接口受理后本轮继续，保留回执且不自动重发；奖励到账以活动页面为准。"));
        fields.addField(legacyCardDailyBudget = new IntegerModelField("legacyCardDailyBudget", "旧卡活动 | 每日操作尝试预算（0不用）", 20, 0, 20)
                .setDescription("每次签到上报、选定任务报名、发送、领取、翻卡或合卡各计一次；只使用当前查询资格，不开通产品，不购买次数，未知回执跨日停止。"));
        fields.addField(shenQuanSign = new BooleanModelField("shenQuanSign", "神券团购 | 签到", false));
        fields.addField(shenQuanTasks = new BooleanModelField("shenQuanTasks", "神券团购 | 浏览/搜索任务与抽奖机会", false));
        fields.addField(shenQuanDraw = new BooleanModelField("shenQuanDraw", "神券团购 | 使用已有免费次数抽奖", false));
        fields.addField(shenQuanLocation = new StringModelField("shenQuanLocation", "神券团购 | 抽奖定位JSON（默认沿用SJ）", "{\"city\":\"泸州市\",\"cityAdcode\":\"510500\",\"district\":\"龙马潭区\",\"province\":\"四川省\",\"provinceAdcode\":\"510000\",\"latitude\":\"28.893704\",\"longitude\":\"105.421453\",\"poiNameForTitle\":\"金诺·御景山居\",\"walletVersion\":\"12.12.20\"}")
                .setDescription("按用户要求默认保持SJ的泸州固定定位，不读取设备GPS。需要调整时填写city/cityAdcode/district/province/provinceAdcode/latitude/longitude字符串；签到和任务不使用定位，海外接口效果尚待实际验证。")
                .setDependsOn("shenQuanDraw"));
        fields.addField(mileageExchange = new BooleanModelField("mileageExchange", "里程兑换 | 出行券", false));
        fields.addField(mileageExchangeCodes = new StringModelField("mileageExchangeCodes", "里程兑换 | 商品编码（逗号分隔）", "").setDependsOn("mileageExchange"));
        fields.addField(mileageCityCode = new StringModelField("mileageCityCode", "里程兑换 | 本人城市编码（6位）", "").setDependsOn("mileageExchange"));
        fields.addField(huaBeiIntimacy = new BooleanModelField("huaBeiIntimacy", "花呗亲密度 | 明确浏览任务", false)
                .setDescription("仅当前APPLET浏览/搜索任务，要求服务端提供1～300秒时长；完整等待后上报并回查。开通、借款、支付、外部App真实交互等任务不执行。"));
        fields.addField(gameCenterP2E = new BooleanModelField("gameCenterP2E", "游戏中心玩赚 | 每日签到", false));
        fields.addField(gameCenterP2EBrowse = new BooleanModelField("gameCenterP2EBrowse", "游戏中心玩赚 | 赚金币浏览任务", false)
                .setDescription("移植朋友源码，仅VIEW_TASK；报名、完整等待、上报、领金币。默认等待15秒，服务端明确提供时长时优先使用。共用SJ每日操作预算，不兑换现金。"));
        fields.addField(luckyCard = new BooleanModelField("luckyCard", "好运卡 | 签到、进度领卡与任务开卡", false)
                .setDescription("移植朋友源码，保留红包卡片；每次报名、领卡、推进、开卡占一次SJ每日操作预算。卡片持久保存，未知开卡回执不重复发送；不处理支付、开通、邀请任务。"));
        fields.addField(leiYouJiTasks = new BooleanModelField("leiYouJiTasks", "芝麻粒乐游记 | 明确浏览任务", false));
        fields.addField(leiYouJiRide = new BooleanModelField("leiYouJiRide", "芝麻粒乐游记 | 前台自动骑行", false)
                .setDescription("会打开乐游记页面，通过实际页面DOM骑行；仅亮屏解锁且游戏窗口处于前台时操作，每轮最多30步。关卡外跳/终点领奖需人工处理；不伪造页面会话或设备指纹。"));
        fields.addField(sjActivityDailyBudget = new IntegerModelField("sjActivityDailyBudget", "SJ新增活动 | 每日操作尝试预算（0不执行）", 30, 0, 50)
                .setDescription("签到、报名、上报、领奖、抽奖、兑换及骑行会话共用预算；写请求单次发送，账号/跨日/取消停止。未确认回执保留并停止该活动重发。"));
        return fields;
    }

    @Override
    public Boolean check() {
        if (!isEnable()) return false;
        if (ApplicationHook.isOffline()) {
            Log.record("其他任务：本轮未启动，支付宝离线");
            return false;
        }
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.record("其他任务：本轮未启动，当前为只收能量时段");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        if (!check()) return;
        gate = new OtherRequestGate();
        try {
            if (shenQuanSign.getValue() || shenQuanTasks.getValue() || shenQuanDraw.getValue()) {
                Log.record("其他任务：神券团购已列入本轮，先执行好家缴费金及卡片奖励，再执行神券团购");
            }
            if (haojiaCoinBudget.getValue() <= 0 && (haojiaCoinSign.getValue() || haojiaCoinBrowse.getValue() || haojiaCoinRewards.getValue())) {
                Log.record("好家缴费金：功能已开启，但每日操作尝试上限为0，本轮未执行");
            }
            HaoJiaPaymentCoin.run(new OtherRequestGate(), haojiaCoinSign.getValue(), haojiaCoinBrowse.getValue(), haojiaCoinRewards.getValue(), haojiaCoinBudget.getValue());
            LegacyCardRewards.run(new OtherRequestGate(), hundredCardSign.getValue(), hundredCardRewards.getValue(), huaHuaCardFlip.getValue(), huaHuaCardMerge.getValue(), legacyCardDailyBudget.getValue(), hundredCardSelectedTasks.getValue(), hundredCardTaskTargets.getValue(), hundredCardSelectedSignup.getValue(), huaHuaCardTasks.getValue(), hundredCardAutoTasks.getValue());
            if (sjActivityDailyBudget.getValue() > 0) {
                SjActivityTasks sj = new SjActivityTasks(gate, sjActivityDailyBudget.getValue());
                if (shenQuanSign.getValue() || shenQuanTasks.getValue() || shenQuanDraw.getValue()) sj.shenQuan(shenQuanSign.getValue(), shenQuanTasks.getValue(), shenQuanDraw.getValue(), shenQuanLocation.getValue());
                if (mileageExchange.getValue()) sj.mileage(mileageExchangeCodes.getValue(), mileageCityCode.getValue());
                if (huaBeiIntimacy.getValue()) sj.intimacy();
                if (gameCenterP2E.getValue()) sj.p2eSign();
                if (gameCenterP2EBrowse.getValue()) new FriendActivityTasks(sj).p2eBrowse();
                if (luckyCard.getValue()) new FriendActivityTasks(sj).luckyCard();
                if (leiYouJiTasks.getValue()) sj.leiYouJiTasks();
                if (leiYouJiRide.getValue()) SjGamePlay.ride(sj);
            } else if (shenQuanSign.getValue() || shenQuanTasks.getValue() || shenQuanDraw.getValue() || mileageExchange.getValue()
                    || huaBeiIntimacy.getValue() || gameCenterP2E.getValue() || gameCenterP2EBrowse.getValue() || luckyCard.getValue()
                    || leiYouJiTasks.getValue() || leiYouJiRide.getValue()) {
                Log.record("SJ新增活动：功能已开启，但每日操作尝试预算为0，本轮未执行");
            }
            if (haojiaWuyou.getValue()) {
                gate = new OtherRequestGate();
                runHaoJia();
            }
        } catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) {
            // 各业务组独立计请求数，SJ活动仍共用每日写操作预算。
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            Log.i(TAG, "其他任务执行异常");
            Log.printStackTrace(TAG, t);
        }
    }

    private void runHaoJia() {
        Log.record("好家无忧卡开始执行");
        try {
            JSONObject sign = MyUtils.newJSONObject(gate.call("查询好家无忧卡签到", HaoJiaRpcCall::querySignIn));
            Log.record("好家无忧卡签到查询：根成功校验=" + ok(sign) + "，resultCode=" + SjActivityTasks.responseField(sign, "resultCode")
                    + "，error=" + SjActivityTasks.responseField(sign, "error") + "，errorCode=" + SjActivityTasks.responseField(sign, "errorCode"));
            if (ok(sign)) {
                JSONObject component = component(sign, "independent_component_sign_in_00966139_independent_component_sign_in_recall");
                JSONObject content = component == null ? null : component.optJSONObject("content");
                JSONArray orders = content == null ? null : content.optJSONArray("playSignInOrderInfoList");
                Log.record("好家无忧卡签到资料：组件有效=" + (component != null) + "，content对象=" + (content != null)
                        + "，签到订单数=" + (orders == null ? -1 : orders.length()));
                if (orders != null && orders.length() > 0) {
                    JSONObject order = orders.optJSONObject(0);
                    JSONObject template = order == null ? null : order.optJSONObject("playSignInTemplateInfo");
                    String code = template == null ? "" : template.optString("code");
                    JSONArray records = order == null ? null : order.optJSONArray("signInRecordInfoList");
                    Log.record("好家无忧卡签到资格：模板code有效=" + !code.isEmpty() + "，签到记录数=" + (records == null ? -1 : records.length())
                            + "，今日已签到=" + hasSignedToday(records));
                    if (!code.isEmpty() && !hasSignedToday(records)) {
                        JSONObject result = MyUtils.newJSONObject(gate.call("好家无忧卡签到", () -> HaoJiaRpcCall.doSignIn(code)));
                        Log.record("好家无忧卡签到提交：接口成功校验=" + ok(result) + "，resultCode=" + SjActivityTasks.responseField(result, "resultCode")
                                + "，记录终态尚未回查");
                    }
                }
            }
            JSONObject tasks = MyUtils.newJSONObject(gate.call("查询好家无忧卡任务", HaoJiaRpcCall::queryTaskList));
            JSONObject component = component(tasks, "independent_component_task_reward_00793835_independent_component_task_reward_query");
            JSONObject content = component == null ? null : component.optJSONObject("content");
            JSONArray list = content == null ? null : content.optJSONArray("playTaskOrderInfoList");
            Log.record("好家无忧卡任务查询：根成功校验=" + ok(tasks) + "，resultCode=" + SjActivityTasks.responseField(tasks, "resultCode")
                    + "，组件有效=" + (component != null) + "，content对象=" + (content != null) + "，任务数=" + (list == null ? -1 : list.length()));
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null || !"init".equals(task.optString("taskStatus")) || "eventPush".equals(task.optString("advanceType"))) continue;
                JSONObject display = task.optJSONObject("displayInfo");
                String name = display == null ? "" : display.optString("activityName");
                if (containsRisk(name)) continue;
                String code = task.optString("code");
                int browseTime = display == null ? 0 : display.optInt("browseTime", 0);
                if (browseTime > 0) sleep(browseTime * 1000L);
                if (!code.isEmpty()) {
                    JSONObject result = MyUtils.newJSONObject(gate.call("好家无忧卡完成任务", () -> HaoJiaRpcCall.applyTask(code)));
                    Log.record("好家无忧卡任务[" + name + "]提交：接口成功校验=" + ok(result)
                            + "，resultCode=" + SjActivityTasks.responseField(result, "resultCode") + "，记录终态尚未回查");
                }
            }
        } catch (OtherRequestGate.BudgetExhausted | OtherRequestGate.Denied stopped) {
            // gate 已经记录过原因，这里不重复打日志。
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Throwable t) {
            Log.record("好家无忧卡流程异常（" + t.getClass().getSimpleName() + "），结果未确认，停止当前活动");
        }
    }

    private static JSONObject component(JSONObject root, String key) {
        if (!ok(root)) return null;
        JSONObject components = root.optJSONObject("components");
        JSONObject value = components == null ? null : components.optJSONObject(key);
        if (value != null && RpcRequestGuard.isFailure(value)) return null;
        return value;
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
        if (jo == null || RpcRequestGuard.isFailure(jo)) return false;
        return jo.optBoolean("success") || jo.optBoolean("isSuccess") || "SUCCESS".equalsIgnoreCase(jo.optString("resultCode")) || "200".equals(jo.optString("resultCode")) || "处理成功".equals(jo.optString("desc"));
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
