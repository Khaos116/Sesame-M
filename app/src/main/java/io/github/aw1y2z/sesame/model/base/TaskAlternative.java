package io.github.aw1y2z.sesame.model.base;

import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.TimeUtil;

/**
 * 完成任务的另一种实现方案：完成接口被 400000040「不支持rpc调用」拒绝时，换
 * {@code com.alipay.antfarm.doFarmTask} 再试。
 *
 * <p>该接口的响应不可信（常回 102「服务器正在开小差」而任务其实已生效），所以只发不判，
 * 成败一律以任务列表为准（{@link #verify} 或下一轮列表）。
 */
public final class TaskAlternative {

    /** 庄园路径验证过的 version；服务端不校验 version，各模块可沿用自己那份。 */
    public static final String DEFAULT_VERSION = "1.8.2302070202.46";

    /**
     * 交易/履约类任务关键词：**只认 bizKey**（服务端稳定字段），这类任务只能靠真实交易完成，
     * 用 doFarmTask 伪申报会被判风险操作（服务端回 1009 风控），一律不发，交自动黑名单。
     *
     * <p>关键词逐条来自实际日志里出现过的 bizKey：
     * <pre>
     * OFFLINE_PAY / ONLINE_PAY / MYZY_pay_* / ORCHARD_NORMAL_XIANXIAZHIFU100  → 支付
     * LSHS_xiadan_202509 / SHANGOU_xiadan / HANGOU_xiadan                   → 下单（回收/闪购）
     * ORCHARD_NCLY_CHARGE1_XDDQ / ORCHARD_NORMAL_CHONGZHI9                  → 充值
     * GOLDENBEAN_GAME_CZ_XDDQ_AI / GROUP_DAILY_GAME_CZ / 2026cc_cz6ylyb_fz  → 游戏充值
     * ORCHARD_NORMAL_KUAIDI100 / ORCHARD_NORMAL_JIUYIHUISHOU_VISIT          → 寄件/回收
     * </pre>
     *
     * <p><b>不要再加 {@code taobao} 这类宽泛词、也不要匹配任务标题</b>：标题/宽泛词会把外跳类
     * 任务误伤成交易类而拒绝申报（如 {@code ORCHARD_NORMAL_TAOBAOTAOLIPAI_VISIT} 曾被 taobao
     * 误拦，随后又被自动拉黑）——外跳类本应去尝试。
     */
    private static final String[] TRANSACTION_KEYWORDS = {
            // 支付 / 下单 / 缴费
            "xiadan", "zhifu", "pay", "goumai", "jiaofei", "huankuan",
            // 充值（拼音 chongzhi + 英文 charge）
            "chongzhi", "charge",
            // 游戏内充值（bizKey 用 CZ，如 GOLDENBEAN_GAME_CZ_XDDQ_AI）
            "game_cz", "_cz",
            // 履约：寄件 / 回收
            "kuaidi", "huishou",
            // 租赁 / 出行 / 酒店机票
            "zulin", "zuche", "dache", "jiudian", "jipiao", "yuebao",
            // 既有特殊项
            "babafarm_tb", "70000",
    };

    /** bizKey 命中交易/履约类关键词，即视为交易/履约类。 */
    public static boolean isTransactionTask(String bizKey) {
        if (bizKey == null || bizKey.isEmpty()) {
            return false;
        }
        String key = bizKey.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : TRANSACTION_KEYWORDS) {
            // 纯数字关键词按「整段数字」匹配：直接 contains 时 70000 会命中 appId
            // （如 2060170000359285 里的 "170000"），把「玩游戏」这类任务误判成交易类
            if (keyword.matches("[0-9]+")) {
                if (Pattern.compile("(?<![0-9])" + keyword + "(?![0-9])").matcher(key).find()) {
                    return true;
                }
            } else if (key.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 日志出口（{@code Log.farm/forest/other/goldenBeans}）。 */
    public interface LogSink {
        void log(String message);
    }

    private TaskAlternative() {
    }

    /** 唯一的 doFarmTask payload，返回原始响应。 */
    public static String request(String bizKey, String taskSceneCode, String version) {
        if (bizKey == null || bizKey.isEmpty() || taskSceneCode == null || version == null || version.isEmpty()) {
            throw new IllegalArgumentException("doFarmTask missing request fields");
        }
        if (isTransactionTask(bizKey)) {
            Log.i("doFarmTask⏭️跳过交易/履约类任务#bizKey=" + bizKey + "，不自动申报");
            return "{}";
        }
        String args;
        try {
            args = new org.json.JSONArray().put(MyUtils.newJSONObject().put("bizKey", bizKey)
                    .put("requestType", "RPC").put("sceneCode", "ANTFARM").put("source", "H5")
                    .put("taskSceneCode", taskSceneCode).put("version", version)).toString();
        } catch (JSONException e) {
            throw new IllegalArgumentException("doFarmTask invalid request");
        }
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args);
    }

    public static JSONObject doFarmTask(String bizKey, String taskSceneCode, String version) throws JSONException {
        String raw = request(bizKey, taskSceneCode, version);
        if (raw == null) {
            throw new JSONException("doFarmTask empty response");
        }
        JSONObject result = MyUtils.newJSONObject(raw);
        if (result.length() == 0 && !"{}".equals(raw.trim())) {
            throw new JSONException("doFarmTask invalid response");
        }
        return result;
    }

    /** 日志片段 {@code resultCode/memo}（{@code desc}、{@code resultDesc} 兜底）。 */
    public static String describe(JSONObject jo) {
        if (jo == null) {
            return "无响应";
        }
        String code = jo.optString("resultCode", jo.optString("code", ""));
        String memo = jo.optString("memo", jo.optString("desc", jo.optString("resultDesc", "")));
        if (code.isEmpty() && memo.isEmpty()) {
            return "无响应";
        }
        return code + "/" + memo;
    }

    /** 是否该换另一种实现方案：400000040 且 taskSceneCode 非空。 */
    public static boolean hit(JSONObject failJo, String taskSceneCode) {
        return taskSceneCode != null && !taskSceneCode.trim().isEmpty()
                && MessageUtil.isUnsupportedRpc(failJo);
    }

    /** version 取 {@link #DEFAULT_VERSION}。 */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String logPrefix, LogSink sink) {
        return trigger(pending, taskId, taskTitle, bizKey, taskSceneCode, DEFAULT_VERSION, logPrefix, sink);
    }

    /**
     * 发请求 + 打日志 + 登记 {@code taskId -> taskTitle}（{@code pending} 可为 null）；不做成败判定。
     *
     * @return doFarmTask 的响应；调用异常返回 null（按未触发处理）
     */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String version,
                                     String logPrefix, LogSink sink) {
        try {
            if (isTransactionTask(bizKey)) {
                Log.i(logPrefix + "⏭️跳过[" + taskTitle + "]#bizKey=" + bizKey + "，交易/履约类不自动申报");
                // 跳过 = 该任务不会完成：同样登记进同轮核对，由调用方的核对机制把它拉黑。
                // 否则只跳过、不拉黑，任务每天被反复跳过（会员游戏中心/金豆夺宝走的就是这条路）。
                if (pending != null && taskId != null && !taskId.isEmpty()) {
                    pending.put(taskId, taskTitle);
                }
                return null;
            }
            JSONObject doFarmJo = doFarmTask(bizKey, taskSceneCode, version);
            if (pending != null && taskId != null && !taskId.isEmpty()) {
                pending.put(taskId, taskTitle);
            }
            String message = logPrefix + "🕓已触发[" + taskTitle + "]#doFarmTask=" + describe(doFarmJo)
                    + "，结果以任务列表为准";
            if (sink != null) {
                sink.log(message);
            } else {
                Log.other(message);
            }
            return doFarmJo;
        } catch (Throwable t) {
            Log.err("TaskAlternative", "trigger err:", t);
            return null;
        }
    }

    // ==================== 同轮核对 ====================

    /** 同轮核对配置。{@code blacklistByTitle} true=拉黑写任务标题，false=写 taskId。 */
    public static final class VerifyConfig {
        private final String moduleName;
        private final String taskListField;
        private final String listDisplay;
        private final String logPrefix;
        private final String doneTag;
        private final boolean blacklistByTitle;
        private final LogSink sink;

        public VerifyConfig(String moduleName, String taskListField, String listDisplay,
                            String logPrefix, String doneTag, boolean blacklistByTitle, LogSink sink) {
            this.moduleName = moduleName;
            this.taskListField = taskListField;
            this.listDisplay = listDisplay;
            this.logPrefix = logPrefix;
            this.doneTag = doneTag;
            this.blacklistByTitle = blacklistByTitle;
            this.sink = sink;
        }
    }

    /** 重拉任务列表并返回仍未完成的 id；拉取失败返回 null。 */
    public interface TaskListSnapshot {
        Set<String> stillTodo() throws Exception;
    }

    /**
     * 同轮核对：等 3 秒 → 重拉列表 → 仍未完成才拉黑，否则记「完成（已按任务列表核对）」。
     *
     * @return 是否有任务确认完成
     */
    public static boolean verify(Map<String, String> pending, VerifyConfig cfg, TaskListSnapshot snapshot) {
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        Map<String, String> batch = new LinkedHashMap<>(pending);
        pending.clear();
        try {
            TimeUtil.sleep(3000);
            Set<String> stillTodo = snapshot.stillTodo();
            if (stillTodo == null) {
                return false;
            }
            boolean changed = false;
            for (Map.Entry<String, String> item : batch.entrySet()) {
                String key = item.getKey();
                String title = item.getValue();
                if (stillTodo.contains(key)) {
                    cfg.sink.log(cfg.logPrefix + "⚠️未完成[" + title + "]#doFarmTask 未生效，已交给自动拉黑机制");
                    MessageUtil.MarkTaskBlackList(cfg.moduleName, cfg.taskListField, cfg.listDisplay,
                            cfg.blacklistByTitle ? title : key);
                } else {
                    cfg.sink.log(cfg.logPrefix + cfg.doneTag + "[" + title + "]#doFarmTask（已按任务列表核对）");
                    changed = true;
                }
            }
            return changed;
        } catch (Throwable t) {
            Log.err("TaskAlternative", "verify err:", t);
            return false;
        }
    }
}
