package io.github.aw1y2z.sesame.model.task.plantingFlowers;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.StringModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.hook.AuthCodeHelper;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

public class PlantingFlowers extends ModelTask {
    private static final String APP_ID = "2021005162668238";
    private static final String HOST = "https://plantingflowers.zhisanzhao.com";
    private BooleanModelField plantingFlowersSign;
    private BooleanModelField plantingFlowersTomorrowReward;
    private BooleanModelField plantingFlowersCloudReward;
    private BooleanModelField plantingFlowersBugReward;
    private BooleanModelField plantingFlowersCollectBeans;
    private IntegerModelField plantingFlowersCollectBeansDailyLimit;
    private BooleanModelField plantingFlowersWatering;
    private IntegerModelField plantingFlowersWateringDailyLimit;
    private BooleanModelField plantingFlowersNewUserReward;
    private BooleanModelField plantingFlowersWeeding;
    private BooleanModelField plantingFlowersFeedFactory;
    private BooleanModelField plantingFlowersDailyDraw;
    private IntegerModelField plantingFlowersDailyDrawLimit;
    private BooleanModelField plantingFlowersAcceleratorTasks;
    private BooleanModelField plantingFlowersUseAcceleratorCard;
    private BooleanModelField plantingFlowersDailyTasks;
    private BooleanModelField plantingFlowersAssist;
    private StringModelField plantingFlowersManualSecret;
    private StringModelField plantingFlowersAssistTargets;
    private IntegerModelField plantingFlowersAssistDailyLimit;
    private BooleanModelField plantingFlowersAssistPool;
    private StringModelField plantingFlowersAssistListUrl;
    private BooleanModelField plantingFlowersAssistUpload;
    private StringModelField plantingFlowersAssistPushUrl;
    private BooleanModelField plantingFlowersAssistRecordUpload;
    private StringModelField plantingFlowersRecordPushUrl;
    private JSONArray flowerAssistRecords;
    private String flowerWaterTaskId;
    private static final String D = "77122edad8d75adabe88c2b59fe3b4a1a12247f04cf0d1bac82e66abb6ae5bfa";
    /** AES 向量（hex，16 字节） */
    private static final String k = "cb252df83e58e8a2fab5a6eda437e9b1";
    private static final byte[] AES_KEY = hexToBytes(D);
    private static final byte[] AES_IV = hexToBytes(k);

    /** 专属对照字典：code "00"~"99" → 汉字 */
    private static final char[] DICT = {
            '爂', '蠄', '阅', '訇', '娈', '耋', '弋', '嬎', '蠔', '儕',
            '渙', '刚', '蔢', '紧', '琧', '圧', '鸨', '鄬', '刮', '踰',
            '圱', '漶', '蘺', '錽', '舾', '績', '栾', '湀', '詄', '癅',
            '腈', '晉', '湏', '豔', '捔', '鱕', '靖', '佘', '书', '筧',
            '獨', '奬', '虬', '牾', '鮁', '疁', '禃', '徆', '禇', '蒍',
            '鲎', '冐', '貑', '暕', '斗', '暘', '梚', '沛', '鶛', '媝',
            '妝', '皞', '醞', '碡', '犩', '莩', '暫', '龬', '颲', '侵',
            '亹', '咼', '嗀', '泀', '净', '鷁', '泂', '拂', '揂', '飃',
            '姄', '淒', '凔', '囕', '瓕', '飗', '裗', '嫘', '峘', '緙',
            '駟', '珢', '泣', '觧', '嗧', '泮', '鋰', '曷', '僻', '旾'
    };



    @Override public String getName() { return "种花大作战"; }
    @Override public ModelGroup getGroup() { return ModelGroup.OTHER; }
    @Override public ModelFields getFields() {
        ModelFields fields = new ModelFields();
        fields.addField(plantingFlowersSign = new BooleanModelField("plantingFlowersSign", "种花大作战｜签到", false));
        fields.addField(plantingFlowersTomorrowReward = new BooleanModelField("plantingFlowersTomorrowReward", "种花大作战｜昨日施肥奖励", false));
        fields.addField(plantingFlowersCloudReward = new BooleanModelField("plantingFlowersCloudReward", "种花大作战｜云朵奖励", false)
                .setDescription("明确当前可领才领取，每天至多一次尝试，资格与水滴增量回查。"));
        fields.addField(plantingFlowersBugReward = new BooleanModelField("plantingFlowersBugReward", "种花大作战｜虫害奖励", false)
                .setDescription("明确当前可领才领取，每天至多一次尝试，资格与水滴增量回查。"));
        fields.addField(plantingFlowersCollectBeans = new BooleanModelField("plantingFlowersCollectBeans", "种花大作战｜收取花豆", false));
        fields.addField(plantingFlowersCollectBeansDailyLimit = new IntegerModelField("plantingFlowersCollectBeansDailyLimit", "种花大作战｜每日收豆尝试上限（0不用）", 1, 0, 20));
        fields.addField(plantingFlowersWatering = new BooleanModelField("plantingFlowersWatering", "种花大作战｜浇水及到期预约", false)
                .setDescription("每次消耗100水滴；现有吸收期内只排期，不重复浇水。重新授权后执行预约，未知结果当天停止。"));
        fields.addField(plantingFlowersWateringDailyLimit = new IntegerModelField("plantingFlowersWateringDailyLimit", "种花大作战｜每日浇水尝试上限（0不用）", 1, 0, 20));
        fields.addField(plantingFlowersNewUserReward = new BooleanModelField("plantingFlowersNewUserReward", "种花大作战｜尝试免费新人卡奖励", false)
                .setDescription("上游未提供新人资格查询；每天仅尝试一次免费接口，必须回查加速卡增加。"));
        fields.addField(plantingFlowersWeeding = new BooleanModelField("plantingFlowersWeeding", "种花大作战｜尝试免费除草奖励", false)
                .setDescription("上游未提供除草资格查询；每天仅尝试一次免费接口，必须回查水滴增加。"));
        fields.addField(plantingFlowersFeedFactory = new BooleanModelField("plantingFlowersFeedFactory", "种花大作战｜到期加工厂奖励", false));
        fields.addField(plantingFlowersDailyDraw = new BooleanModelField("plantingFlowersDailyDraw", "种花大作战｜免费每日抽奖", false));
        fields.addField(plantingFlowersDailyDrawLimit = new IntegerModelField("plantingFlowersDailyDrawLimit", "种花大作战｜每日抽奖尝试上限（0不用）", 1, 0, 5));
        fields.addField(plantingFlowersAcceleratorTasks = new BooleanModelField("plantingFlowersAcceleratorTasks", "种花大作战｜领取加速卡任务奖励", false));
        fields.addField(plantingFlowersUseAcceleratorCard = new BooleanModelField("plantingFlowersUseAcceleratorCard", "种花大作战｜吸收期使用一张加速卡", false)
                .setDescription("每天至多一张，卡数量扣减与吸收状态同时回查；未知结果当天停止。"));
        fields.addField(plantingFlowersDailyTasks = new BooleanModelField("plantingFlowersDailyTasks", "种花大作战｜数据中心日常任务", false)
                .setDescription("每次至多20个，排除上游17–25类型，按服务端visitSeconds完整等待（超过300秒跳过）；只领取同账号任务水滴。"));
        fields.addField(plantingFlowersAssist = new BooleanModelField("plantingFlowersAssist", "种花大作战｜指定口令助力", false)
                .setDescription("只提交您填写或明确启用口令池提供的口令；每天最多3次尝试。上游无好友历史回查，成功ACK只记请求已提交、未确认，并持久冻结助力，跨日也不自动重试。"));
        fields.addField(plantingFlowersManualSecret = new StringModelField("plantingFlowersManualSecret", "种花大作战｜手动助力口令（每行一个）", ""));
        fields.addField(plantingFlowersAssistTargets = new StringModelField("plantingFlowersAssistTargets", "种花大作战｜指定UID与口令JSON列表", ""));
        plantingFlowersAssistTargets.setDescription("例如 [{\"userId\":\"目标支付宝UID\",\"shareId\":\"对方口令\"}]。UID必须配对口令；上游未提供按UID查询口令接口。不会上传您的口令。" );
        fields.addField(plantingFlowersAssistDailyLimit = new IntegerModelField("plantingFlowersAssistDailyLimit", "种花大作战｜每日助力尝试上限（0不用）", 3, 0, 3));
        fields.addField(plantingFlowersAssistPool = new BooleanModelField("plantingFlowersAssistPool", "种花大作战｜使用配置的口令池列表", false)
                .setDescription("另需开启指定口令助力并填写HTTPS地址。GET地址会追加 PlantingFlowersShare/当前UID，将UID提供给您配置的服务；不发送Token、签名、口令或执行记录。"));
        fields.addField(plantingFlowersAssistListUrl = new StringModelField("plantingFlowersAssistListUrl", "种花大作战｜口令池列表HTTPS地址前缀", ""));
        fields.addField(plantingFlowersAssistUpload = new BooleanModelField("plantingFlowersAssistUpload", "种花大作战｜向配置服务上传自己的口令", false)
                .setDescription("独立授权，每天至多一次尝试；将当前UID及动态查询到的自身口令发送给您配置的HTTPS服务。不会发送Token或签名。响应只记请求已提交，不确认服务端存储。"));
        fields.addField(plantingFlowersAssistPushUrl = new StringModelField("plantingFlowersAssistPushUrl", "种花大作战｜自身口令上传HTTPS地址前缀", ""));
        fields.addField(plantingFlowersAssistRecordUpload = new BooleanModelField("plantingFlowersAssistRecordUpload", "种花大作战｜向配置服务提交本轮助力记录", false)
                .setDescription("独立授权，每天至多一次尝试；将当前UID、本轮选择的目标UID/口令及提交状态发送给配置的HTTPS服务。未知结果state固定0，不冒充成功；不会发送Token或签名。"));
        fields.addField(plantingFlowersRecordPushUrl = new StringModelField("plantingFlowersRecordPushUrl", "种花大作战｜助力记录HTTPS地址前缀", ""));
        return fields;
    }
    @Override public Boolean check() {
        return isEnable() && !ApplicationHook.isOffline() && !TaskCommon.IS_ENERGY_TIME;
    }
    @Override public void run() {
        if (!check()) return;
        boolean sign = plantingFlowersSign.getValue() && !Status.hasFlagToday("flowers::signAttempt");
        boolean reward = plantingFlowersTomorrowReward.getValue() && !Status.hasFlagToday("flowers::tomorrowRewardAttempt");
        boolean cloud = plantingFlowersCloudReward.getValue() && !Status.hasFlagToday("flowers::cloudAttempt");
        boolean bug = plantingFlowersBugReward.getValue() && !Status.hasFlagToday("flowers::bugAttempt");
        boolean beans = flowerBudgetAvailable(false);
        boolean water = flowerBudgetAvailable(true);
        boolean newUser = flowerReady(plantingFlowersNewUserReward, "flowers::newUser");
        boolean weeding = flowerReady(plantingFlowersWeeding, "flowers::weeding");
        boolean factory = flowerReady(plantingFlowersFeedFactory, "flowers::factory") && !Status.hasFlagToday("flowers::factoryDone");
        boolean draw = flowerDrawAvailable();
        boolean cards = flowerReady(plantingFlowersAcceleratorTasks, "flowers::cardTasks") && !Status.hasFlagToday("flowers::cardTasksDone");
        boolean useCard = flowerReady(plantingFlowersUseAcceleratorCard, "flowers::useCard");
        boolean tasks = flowerReady(plantingFlowersDailyTasks, "flowers::tasks") && !Status.hasFlagToday("flowers::tasksDone");
        boolean assist = flowerAssistConfigured();
        boolean upload = flowerPublicationReady(plantingFlowersAssistUpload, plantingFlowersAssistPushUrl, "flowers::ownSecretUpload");
        if (!sign && !reward && !cloud && !bug && !beans && !water && !newUser && !weeding && !factory && !draw && !cards && !useCard && !tasks && !assist && !upload) return;
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || !uid.matches("[0-9]{6,32}")) return;
        try {
            checkAccount(uid);
            String code = AuthCodeHelper.getAuthCode(APP_ID);
            checkAccount(uid);
            if (code == null || code.trim().isEmpty()) {
                Log.record("种花大作战：当前账号授权未取得，跳过");
                return;
            }
            JSONObject login = request("/plantingFlowers/apiLogin/login?code=" + URLEncoder.encode(code, "UTF-8") + "&type=ALIPAY", null, null, uid);
            JSONObject user = login.optJSONObject("user");
            Object rawToken = login.opt("token");
            if (!(rawToken instanceof String) || ((String) rawToken).trim().isEmpty() || ((String) rawToken).length() > 4096
                    || ((String) rawToken).indexOf('\r') >= 0 || ((String) rawToken).indexOf('\n') >= 0 || user == null
                    || !(user.opt("alipayId") instanceof String) || !uid.equals(user.optString("alipayId"))) throw new IOException();
            if (sign) signIn((String) rawToken, uid);
            if (reward) tomorrowReward((String) rawToken, uid);
            if (cloud) collectTimedReward((String) rawToken, uid, true);
            if (bug) collectTimedReward((String) rawToken, uid, false);
            if (newUser) collectSingleReward((String) rawToken, uid, true);
            if (weeding) collectSingleReward((String) rawToken, uid, false);
            if (factory) collectFactory((String) rawToken, uid);
            if (draw) flowerDailyDraw((String) rawToken, uid);
            if (tasks) flowerDailyTasks((String) rawToken, uid);
            flowerAssistRecords = new JSONArray();
            if (assist) flowerAssist((String) rawToken, uid);
            if (flowerAssistRecords.length() > 0 && flowerPublicationReady(plantingFlowersAssistRecordUpload, plantingFlowersRecordPushUrl, "flowers::assistRecordUpload"))
                flowerPublish(plantingFlowersAssistRecordUpload, plantingFlowersRecordPushUrl, "flowers::assistRecordUpload", flowerAssistRecords.toString(), uid);
            if (upload) flowerOwnSecretUpload((String) rawToken, uid);
            if (cards) flowerCardTasks((String) rawToken, uid);
            if (beans) collectFlowerBeans((String) rawToken, uid);
            if (useCard) useFlowerCard((String) rawToken, uid);
            if (water) waterFlower((String) rawToken, uid);
        } catch (TaskCancelledException e) { throw e;
        } catch (Exception e) {
            Log.record("种花大作战：授权、网络或响应未通过校验（" + e.getClass().getSimpleName() + "），停止本轮");
        }
    }

    private void signIn(String token, String uid) throws Exception {
        int day = businessDay();
        JSONObject before = request("/plantingFlowers/api/signTask/querySignInStatus", null, token, uid);
        if (Boolean.TRUE.equals(before.opt("todayAlreadySign"))) {
            checkAccount(uid);
            if (businessDay() == day) Status.flagToday("flowers::signAttempt", uid);
            return;
        }
        if (!Boolean.FALSE.equals(before.opt("todayAlreadySign"))) return;
        before = request("/plantingFlowers/api/signTask/querySignInStatus", null, token, uid);
        if (!Boolean.FALSE.equals(before.opt("todayAlreadySign"))) return;
        checkAccount(uid);
        if (!plantingFlowersSign.getValue() || Status.hasFlagToday("flowers::signAttempt") || businessDay() != day) return;
        Status.flagToday("flowers::signAttempt", uid);
        request("/plantingFlowers/api/signTask/finish2", MyUtils.newJSONObject().put("rewardSource", 3), token, uid);
        JSONObject after = request("/plantingFlowers/api/signTask/querySignInStatus", null, token, uid);
        if (businessDay() == day && Boolean.TRUE.equals(after.opt("todayAlreadySign"))) Log.other("种花大作战🌸签到已回查确认");
        else Log.record("种花大作战：签到未确认，本日不重复提交");
    }

    private static int businessDay() {
        Calendar now = MyUtils.getInstance();
        return now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
    }

    private static long tomorrowRewardAmount(JSONObject state) {
        Object value = state.opt("yesterdayFertilizationReward");
        if (!(value instanceof Number)) return -1;
        double amount = ((Number) value).doubleValue();
        return Double.isFinite(amount) && amount >= 0 && amount <= 1000000000 && amount == Math.floor(amount) ? (long) amount : -1;
    }

    private void tomorrowReward(String token, String uid) throws Exception {
        String key = "flowers::tomorrowRewardAttempt";
        if (!plantingFlowersTomorrowReward.getValue() || Status.hasFlagToday(key)) return;
        int day = businessDay();
        JSONObject before = request("/plantingFlowers/api/waterTomorrowReward/query", null, token, uid);
        long amount = tomorrowRewardAmount(before);
        if (amount <= 0 || !(before.opt("todayIsReceived") instanceof Boolean)) return;
        if (Boolean.TRUE.equals(before.opt("todayIsReceived"))) {
            checkAccount(uid);
            if (businessDay() == day) Status.flagToday(key, uid);
            return;
        }
        JSONObject fresh = request("/plantingFlowers/api/waterTomorrowReward/query", null, token, uid);
        if (tomorrowRewardAmount(fresh) != amount || !Boolean.FALSE.equals(fresh.opt("todayIsReceived"))) return;
        checkAccount(uid);
        if (businessDay() != day || !plantingFlowersTomorrowReward.getValue() || Status.hasFlagToday(key)) return;
        Status.flagToday(key, uid);
        request("/plantingFlowers/api/waterTomorrowReward/receivedFeed", MyUtils.newJSONObject().put("rewardSource", 3), token, uid);
        JSONObject after = request("/plantingFlowers/api/waterTomorrowReward/query", null, token, uid);
        if (businessDay() == day && tomorrowRewardAmount(after) == amount && Boolean.TRUE.equals(after.opt("todayIsReceived"))) {
            Log.other("种花大作战🌸昨日施肥奖励领取状态已回查确认");
        } else Log.record("种花大作战：昨日施肥奖励未确认，本日不重复提交");
    }

    private static void checkAccount(String uid) {
        TimeUtil.sleep(0);
        if (!uid.equals(UserIdMap.getCurrentUid())) throw new TaskCancelledException();
    }

    private static long flowerInteger(JSONObject state, String name) {
        Object value = state == null ? null : state.opt(name);
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number >= 0 && number <= 1000000000 && number == Math.floor(number) ? (long) number : -1;
    }

    private static boolean flowerReady(BooleanModelField option, String key) {
        return option.getValue() && !Status.hasFlagToday(key + "Attempt") && Status.getIntFlagToday(key + "Unknown") == 0;
    }

    private static boolean flowerBegin(BooleanModelField option, String key, int day, String uid) {
        checkAccount(uid);
        if (businessDay() != day || !flowerReady(option, key)) return false;
        Status.flagToday(key + "Attempt", uid);
        Status.setIntFlagToday(key + "Unknown", 1);
        return true;
    }

    private static boolean flowerFinish(String key, int day, String uid, boolean confirmed) {
        checkAccount(uid);
        if (day != businessDay() || !confirmed) {
            Log.record("种花大作战：动作回查未确认，本日停止重复提交");
            return false;
        }
        Status.setIntFlagToday(key + "Unknown", 0);
        Log.other("种花大作战🌸" + key.substring("flowers::".length()) + "状态与库存已回查确认");
        return true;
    }

    private static long flowerCards(JSONObject state) {
        long primary = flowerInteger(state, "acceleratorCardNumber"), fallback = flowerInteger(state, "accelerateCard");
        if (state.has("acceleratorCardNumber") && primary < 0 || state.has("accelerateCard") && fallback < 0
                || primary > 0 && fallback > 0 && primary != fallback) return -1;
        return primary > 0 ? primary : fallback >= 0 ? fallback : primary;
    }

    private void collectSingleReward(String token, String uid, boolean newUser) throws Exception {
        BooleanModelField option = newUser ? plantingFlowersNewUserReward : plantingFlowersWeeding;
        String key = newUser ? "flowers::newUser" : "flowers::weeding";
        if (!flowerReady(option, key)) return;
        int day = businessDay();
        JSONObject before = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        long inventory = newUser ? flowerCards(before) : flowerInteger(before, "numberWater");
        if (inventory < 0) return;
        JSONObject fresh = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        if ((newUser ? flowerCards(fresh) : flowerInteger(fresh, "numberWater")) != inventory || !flowerBegin(option, key, day, uid)) return;
        JSONObject body = MyUtils.newJSONObject();
        if (!newUser) body.put("taskSource", 1);
        request(newUser ? "/plantingFlowers/api/acceleratorCard/newUserReward" : "/plantingFlowers/api/sendWeedingReward", body, token, uid);
        JSONObject after = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        flowerFinish(key, day, uid, (newUser ? flowerCards(after) : flowerInteger(after, "numberWater")) > inventory);
    }

    private static long factoryEnd(JSONObject state) {
        Object value = state.opt("endTime");
        if (!(value instanceof String) || !((String) value).matches("[0-9]{4}[-/][0-9]{2}[-/][0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}")) return -1;
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", java.util.Locale.ROOT);
        format.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
        format.setLenient(false);
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        String normalized = ((String) value).replace('-', '/');
        java.util.Date end = format.parse(normalized, position);
        return end != null && position.getIndex() == normalized.length() ? end.getTime() : -1;
    }

    private void collectFactory(String token, String uid) throws Exception {
        String path = "/plantingFlowers/api/feedFactory/info", global = "flowers::factory";
        int day = businessDay();
        // ponytail: the source factory allows five daily collections; no background poller for future batches.
        for (int step = 0; step < 5 && flowerReady(plantingFlowersFeedFactory, global); step++) {
            JSONObject before = request(path, null, token, uid);
            long count = flowerInteger(before, "todayCount"), end = factoryEnd(before);
            if (count >= 5) { checkAccount(uid); Status.flagToday("flowers::factoryDone", uid); return; }
            String key = global + "::" + count;
            if (count < 0 || end <= 0 || end > System.currentTimeMillis() || !flowerReady(plantingFlowersFeedFactory, key)) return;
            long water = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            JSONObject fresh = request(path, null, token, uid);
            if (water < 0 || flowerInteger(fresh, "todayCount") != count || factoryEnd(fresh) != end || !flowerBegin(plantingFlowersFeedFactory, key, day, uid)) return;
            Status.setIntFlagToday(global + "Unknown", 1);
            JSONObject accepted = request("/plantingFlowers/api/feedFactory/reward", MyUtils.newJSONObject().put("rewardSource", 1), token, uid);
            long feeds = flowerInteger(accepted, "feeds");
            JSONObject after = request(path, null, token, uid);
            long afterWater = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            if (!flowerFinish(key, day, uid, feeds > 0 && afterWater == water + feeds && flowerInteger(after, "todayCount") == count + 1)) return;
            Status.setIntFlagToday(global + "Unknown", 0);
        }
    }

    private boolean flowerDrawAvailable() {
        return flowerReady(plantingFlowersDailyDraw, "flowers::draw") && !Status.hasFlagToday("flowers::drawDone")
                && Status.getIntFlagToday("flowers::drawAttempts") < plantingFlowersDailyDrawLimit.getValue();
    }

    private void flowerDailyDraw(String token, String uid) throws Exception {
        String path = "/plantingFlowers/api/dailyDraw/activity/info", global = "flowers::draw";
        int day = businessDay();
        for (int step = 0; step < 5 && flowerDrawAvailable(); step++) {
            JSONObject before = request(path, null, token, uid);
            long used = flowerInteger(before, "activityUseNumber"), max = flowerInteger(before, "activityMaxNumber");
            if (used < 0 || max < 0 || max > 100 || used > max) return;
            if (used == max) { checkAccount(uid); Status.flagToday("flowers::drawDone", uid); return; }
            long water = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            JSONObject fresh = request(path, null, token, uid);
            String key = global + "::" + used;
            if (water < 0 || flowerInteger(fresh, "activityUseNumber") != used || flowerInteger(fresh, "activityMaxNumber") != max
                    || !flowerDrawAvailable() || !flowerBegin(plantingFlowersDailyDraw, key, day, uid)) return;
            Status.setIntFlagToday(global + "Unknown", 1);
            Status.setIntFlagToday("flowers::drawAttempts", Status.getIntFlagToday("flowers::drawAttempts") + 1);
            JSONObject accepted = request("/plantingFlowers/api/dailyDraw/lottery", null, token, uid);
            JSONObject after = request(path, null, token, uid);
            long price = flowerInteger(accepted, "awardPrice");
            long afterWater = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            if (!flowerFinish(key, day, uid, flowerInteger(after, "activityUseNumber") == used + 1 && flowerInteger(after, "activityMaxNumber") == max
                    && (!accepted.has("awardPrice") || price >= 0 && afterWater == water + price))) return;
            Status.setIntFlagToday(global + "Unknown", 0);
        }
    }

    private void flowerCardTasks(String token, String uid) throws Exception {
        String path = "/plantingFlowers/api/receive/acceleratorCard", global = "flowers::cardTasks";
        int day = businessDay();
        boolean allHandled = true;
        for (int type = 1; type <= 3 && flowerReady(plantingFlowersAcceleratorTasks, global); type++) {
            String key = "flowers::cardTask::" + type;
            if (!flowerReady(plantingFlowersAcceleratorTasks, key)) continue;
            JSONObject query = MyUtils.newJSONObject().put("type", type).put("isFinish", false);
            JSONObject before = request(path, query, token, uid);
            if (Boolean.TRUE.equals(before.opt("alreadyFinish"))) continue;
            if (!Boolean.TRUE.equals(before.opt("state")) || !Boolean.FALSE.equals(before.opt("alreadyFinish"))) { allHandled = false; continue; }
            long cards = flowerCards(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid));
            JSONObject fresh = request(path, query, token, uid);
            if (cards < 0 || !Boolean.TRUE.equals(fresh.opt("state")) || !Boolean.FALSE.equals(fresh.opt("alreadyFinish"))
                    || !flowerBegin(plantingFlowersAcceleratorTasks, key, day, uid)) { allHandled = false; continue; }
            Status.setIntFlagToday(global + "Unknown", 1);
            request(path, MyUtils.newJSONObject().put("type", type).put("isFinish", true), token, uid);
            JSONObject after = request(path, query, token, uid);
            long afterCards = flowerCards(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid));
            if (!flowerFinish(key, day, uid, Boolean.TRUE.equals(after.opt("alreadyFinish")) && afterCards > cards)) return;
            Status.setIntFlagToday(global + "Unknown", 0);
        }
        checkAccount(uid);
        if (allHandled && day == businessDay()) Status.flagToday("flowers::cardTasksDone", uid);
    }

    private void useFlowerCard(String token, String uid) throws Exception {
        String key = "flowers::useCard";
        if (!flowerReady(plantingFlowersUseAcceleratorCard, key)) return;
        int day = businessDay();
        JSONObject before = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        long cards = flowerCards(before), end = flowerWaterEnd(before);
        if (cards <= 0 || flowerInteger(before, "isWatering") != 1 || end <= System.currentTimeMillis()) return;
        JSONObject fresh = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        if (flowerCards(fresh) != cards || flowerInteger(fresh, "isWatering") != 1 || flowerWaterEnd(fresh) != end
                || !flowerBegin(plantingFlowersUseAcceleratorCard, key, day, uid)) return;
        request("/plantingFlowers/api/use/acceleratorCard", MyUtils.newJSONObject(), token, uid);
        JSONObject after = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        long active = flowerInteger(after, "isWatering"), afterEnd = flowerWaterEnd(after);
        flowerFinish(key, day, uid, flowerCards(after) == cards - 1 && (active == 0 || active == 1 && afterEnd > 0 && afterEnd < end));
    }

    private static JSONObject flowerTaskById(JSONObject payload, String id) {
        JSONArray rows = payload.optJSONArray("taskList");
        if (rows == null || rows.length() > 100) return null;
        JSONObject found = null;
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return null;
            Object raw = row.has("id") ? row.opt("id") : row.opt("taskId");
            if (!(raw instanceof String) || ((String) raw).isEmpty() || ((String) raw).length() > 256 || !ids.add((String) raw)) return null;
            if (id.equals(raw)) found = row;
        }
        return found;
    }

    private static Boolean flowerTaskDone(JSONObject task) {
        if (task == null) return null;
        if (task.has("alreadyFinish") && !(task.opt("alreadyFinish") instanceof Boolean)
                || task.has("finished") && !(task.opt("finished") instanceof Boolean)) return null;
        if (!task.has("alreadyFinish") && !task.has("finished")) return null;
        return Boolean.TRUE.equals(task.opt("alreadyFinish")) || Boolean.TRUE.equals(task.opt("finished"));
    }

    private void flowerDailyTasks(String token, String uid) throws Exception {
        String listPath = "/dataCenter/api/task/list?mark=PLANTING_FLOWERS_JJEGG_LIST&uid=" + URLEncoder.encode(uid, "UTF-8") + "&versions=2";
        String global = "flowers::tasks";
        int day = businessDay();
        JSONObject list = request(listPath, null, token, uid);
        Object outId = list.opt("userId");
        JSONArray rows = list.optJSONArray("taskList");
        if (!(outId instanceof String) || ((String) outId).isEmpty() || rows == null || rows.length() > 100) return;
        int attempted = 0;
        for (int i = 0; i < rows.length() && attempted < 20 && flowerReady(plantingFlowersDailyTasks, global); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) return;
            Object rawId = row.has("id") ? row.opt("id") : row.opt("taskId");
            if (!(rawId instanceof String)) return;
            String id = (String) rawId;
            JSONObject task = flowerTaskById(list, id);
            if (task == null) return;
            long type = flowerInteger(task, "taskType"), seconds = task != null && task.has("visitSeconds") ? flowerInteger(task, "visitSeconds") : 2;
            String key = "flowers::task::" + id;
            if (!Boolean.FALSE.equals(flowerTaskDone(task)) || type < 1 || type >= 17 && type <= 25 || seconds < 1 || seconds > 300 || !flowerReady(plantingFlowersDailyTasks, key)) continue;
            long water = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            JSONObject freshList = request(listPath, null, token, uid), fresh = flowerTaskById(freshList, id);
            if (water < 0 || !outId.equals(freshList.opt("userId")) || fresh == null || !task.toString().equals(fresh.toString())
                    || !flowerBegin(plantingFlowersDailyTasks, key, day, uid)) continue;
            Status.setIntFlagToday(global + "Unknown", 1);
            request("/dataCenter/api/task/click", MyUtils.newJSONObject().put("uid", uid).put("taskId", id).put("mark", "PLANTING_FLOWERS_JJEGG_LIST"), token, uid);
            for (int elapsed = 0; elapsed < seconds; elapsed++) { TimeUtil.sleep(1000); checkAccount(uid); }
            if (businessDay() != day || !plantingFlowersDailyTasks.getValue()) return;
            request("/dataCenter/api/task/action/finish?uid=" + URLEncoder.encode(uid, "UTF-8") + "&taskId=" + URLEncoder.encode(id, "UTF-8") + "&mark=PLANTING_FLOWERS_JJEGG_LIST", null, token, uid);
            JSONObject finishedList = request(listPath, null, token, uid);
            if (!outId.equals(finishedList.opt("userId")) || !Boolean.TRUE.equals(flowerTaskDone(flowerTaskById(finishedList, id)))) return;
            checkAccount(uid);
            if (day != businessDay() || !plantingFlowersDailyTasks.getValue()) return;
            request("/plantingFlowers/api/task/record", MyUtils.newJSONObject().put("isReward", true).put("taskId", id).put("taskType", type)
                    .put("outUserId", outId).put("positionMark", "PLANTING_FLOWERS_JJEGG_LIST"), token, uid);
            long afterWater = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
            if (!flowerFinish(key, day, uid, afterWater > water)) return;
            Status.setIntFlagToday(global + "Unknown", 0);
            attempted++;
        }
        checkAccount(uid);
        if (attempted < 20 && day == businessDay()) Status.flagToday("flowers::tasksDone", uid);
    }

    private boolean flowerBudgetAvailable(boolean water) {
        String prefix = water ? "flowers::water" : "flowers::beans";
        return (water ? plantingFlowersWatering.getValue() : plantingFlowersCollectBeans.getValue())
                && Status.getIntFlagToday(prefix + "Unconfirmed") == 0
                && Status.getIntFlagToday(prefix + "Attempts") < (water ? plantingFlowersWateringDailyLimit.getValue() : plantingFlowersCollectBeansDailyLimit.getValue());
    }

    private static long flowerBeanBalance(JSONObject state) {
        long primary = flowerInteger(state, "goldenBeansNumber"), fallback = flowerInteger(state, "numberCoffeeBeans");
        if (state.has("goldenBeansNumber") && primary < 0 || state.has("numberCoffeeBeans") && fallback < 0) return -1;
        if (primary > 0 && fallback > 0 && primary != fallback) return -1;
        return primary > 0 ? primary : fallback >= 0 ? fallback : primary;
    }

    private void collectFlowerBeans(String token, String uid) throws Exception {
        if (!flowerBudgetAvailable(false)) return;
        int day = businessDay();
        long before = flowerBeanBalance(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid));
        if (before < 0 || flowerBeanBalance(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid)) != before) return;
        checkAccount(uid);
        if (day != businessDay() || !flowerBudgetAvailable(false)) return;
        Status.setIntFlagToday("flowers::beansUnconfirmed", 1);
        Status.setIntFlagToday("flowers::beansAttempts", Status.getIntFlagToday("flowers::beansAttempts") + 1);
        request("/plantingFlowers/api/collect/goldenBeans", MyUtils.newJSONObject(), token, uid);
        long after = flowerBeanBalance(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid));
        checkAccount(uid);
        if (day == businessDay() && after > before) {
            Status.setIntFlagToday("flowers::beansUnconfirmed", 0);
            Log.other("种花大作战🌸收豆余额增量已回查确认");
        } else Log.record("种花大作战：收豆结果未确认，本日停止重复提交");
    }

    private static long flowerWaterEnd(JSONObject state) {
        Object value = state.opt("endWaterTime");
        if (!(value instanceof Number)) return -1;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) && number > 0 && number <= 10000000000000L && number == Math.floor(number) ? (long) number : -1;
    }

    private void waterFlower(String token, String uid) throws Exception {
        if (!flowerBudgetAvailable(true)) return;
        int day = businessDay();
        JSONObject before = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        long active = flowerInteger(before, "isWatering"), water = flowerInteger(before, "numberWater");
        if (active == 1) { scheduleFlowerWatering(uid, flowerWaterEnd(before)); return; }
        if (active != 0 || water < 100) return;
        JSONObject fresh = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        if (flowerInteger(fresh, "isWatering") != 0 || flowerInteger(fresh, "numberWater") != water) return;
        checkAccount(uid);
        if (day != businessDay() || !flowerBudgetAvailable(true)) return;
        Status.setIntFlagToday("flowers::waterUnconfirmed", 1);
        Status.setIntFlagToday("flowers::waterAttempts", Status.getIntFlagToday("flowers::waterAttempts") + 1);
        request("/plantingFlowers/api/water/coffeeTree", MyUtils.newJSONObject().put("isWaterTomorrow", true), token, uid);
        JSONObject after = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        checkAccount(uid);
        if (day == businessDay() && flowerInteger(after, "numberWater") == water - 100 && flowerInteger(after, "isWatering") == 1
                && flowerWaterEnd(after) > System.currentTimeMillis()) {
            Status.setIntFlagToday("flowers::waterUnconfirmed", 0);
            Log.other("种花大作战🌸浇水扣减及吸收状态已回查确认");
            scheduleFlowerWatering(uid, flowerWaterEnd(after));
        } else Log.record("种花大作战：浇水结果未确认，本日停止重复提交");
    }

    private synchronized void scheduleFlowerWatering(String uid, long end) {
        long now = System.currentTimeMillis();
        if (!isEnable() || !flowerBudgetAvailable(true) || !uid.equals(UserIdMap.getCurrentUid()) || end <= now || end > now + 86400000L) return;
        ChildModelTask current = flowerWaterTaskId == null ? null : getChildTask(flowerWaterTaskId);
        if (current != null && Boolean.FALSE.equals(current.getIsCancel()) && current.getExecTime() == end + 1000) return;
        if (flowerWaterTaskId != null) removeChildTask(flowerWaterTaskId);
        String id = "FLOWERS_WATER|" + uid + "|" + System.nanoTime();
        long generation = taskGeneration();
        if (addChildTask(new ChildModelTask(id, "plantingFlowers", () -> {
            synchronized (this) {
                if (!id.equals(flowerWaterTaskId)) return;
                flowerWaterTaskId = null;
            }
            boolean ran = runExclusiveChild(generation, () -> {
                checkAccount(uid);
                if (flowerBudgetAvailable(true)) run(); // 不捕获Token，到期使用当前账号重新授权。
            });
            if (!ran && generation == taskGeneration() && uid.equals(UserIdMap.getCurrentUid()))
                scheduleFlowerWatering(uid, System.currentTimeMillis() + 30000);
        }, end + 1000))) flowerWaterTaskId = id;
    }

    /** -1不明，0当前无需领取，正数为明确可领；云朵用1，虫害用服务端奖励次数。 */
    private static long timedRewardPending(JSONObject payload, boolean cloud) {
        JSONArray rows = payload == null ? null : payload.optJSONArray("currently");
        if (rows == null || rows.length() > 100) return -1;
        long eligible = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("currently") instanceof Boolean)) return -1;
            if (!Boolean.TRUE.equals(row.opt("currently"))) continue;
            String flag = cloud ? "isFinish" : "alreadyFinish";
            if (!(row.opt(flag) instanceof Boolean)) return -1;
            if (Boolean.TRUE.equals(row.opt(flag))) continue;
            long amount = cloud ? 1 : flowerInteger(row, "rewardNum");
            if (amount < 0 || eligible > 0 && amount > 0) return -1;
            if (amount > 0) eligible = amount;
        }
        return eligible;
    }

    private void collectTimedReward(String token, String uid, boolean cloud) throws Exception {
        BooleanModelField option = cloud ? plantingFlowersCloudReward : plantingFlowersBugReward;
        String key = cloud ? "flowers::cloudAttempt" : "flowers::bugAttempt";
        String path = "/plantingFlowers/api/" + (cloud ? "cloudRewardConfig" : "bugRewardConfig");
        if (!option.getValue() || Status.hasFlagToday(key)) return;
        int day = businessDay();
        long pending = timedRewardPending(request(path, null, token, uid), cloud);
        if (pending <= 0) return;
        JSONObject develop = request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid);
        long water = flowerInteger(develop, "numberWater");
        if (water < 0 || timedRewardPending(request(path, null, token, uid), cloud) != pending) return;
        checkAccount(uid);
        if (businessDay() != day || !option.getValue() || Status.hasFlagToday(key)) return;
        Status.flagToday(key, uid);
        JSONObject body = MyUtils.newJSONObject().put("rewardType", cloud ? 4 : 1);
        if (cloud) body.put("type", 2);
        JSONObject accepted = request("/plantingFlowers/api/bugReward", body, token, uid);
        long feeds = flowerInteger(accepted, "feeds");
        long remaining = timedRewardPending(request(path, null, token, uid), cloud);
        long afterWater = flowerInteger(request("/plantingFlowers/api/coffeeBeans/develop", null, token, uid), "numberWater");
        if (businessDay() == day && feeds > 0 && afterWater == water + feeds && remaining == 0)
            Log.other("种花大作战🌸" + (cloud ? "云朵" : "虫害") + "奖励状态与水滴增量已回查确认");
        else Log.record("种花大作战：限时奖励结果未确认，本日不重复提交");
    }

    private boolean flowerAssistConfigured() {
        return plantingFlowersAssist.getValue() && plantingFlowersAssistDailyLimit.getValue() > 0
                && (!plantingFlowersManualSecret.getValue().trim().isEmpty() || !plantingFlowersAssistTargets.getValue().trim().isEmpty()
                || plantingFlowersAssistPool.getValue() && !plantingFlowersAssistListUrl.getValue().trim().isEmpty());
    }

    private static String assistSecret(Object value) {
        if (!(value instanceof String)) return null;
        String s = ((String) value).trim();
        if (s.isEmpty() || s.length() > 512) return null;
        for (int i = 0; i < s.length(); i++) if (Character.isISOControl(s.charAt(i))) return null;
        return s;
    }

    /** The source has no friend-history contract: an ACK must never become a confirmed assist. */
    private static JSONObject assistState(RuntimeInfo runtime, int day) throws Exception {
        String raw = runtime.getString("flowers::assistState");
        if (raw.isEmpty()) return MyUtils.newJSONObject().put("day", day).put("attempts", 0).put("consumed", new JSONArray()).put("unknown", "").put("limited", false);
        JSONObject state = MyUtils.newJSONObject(raw);
        long savedDay = flowerInteger(state, "day"), attempts = flowerInteger(state, "attempts");
        JSONArray consumed = state.optJSONArray("consumed"); Object unknown = state.opt("unknown");
        if (savedDay < 2000001 || savedDay % 1000 < 1 || savedDay % 1000 > 366 || attempts < 0 || attempts > 3 || consumed == null || consumed.length() != attempts
                || !(unknown instanceof String) || !(state.opt("limited") instanceof Boolean)) return null;
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < consumed.length(); i++) {
            Object id = consumed.opt(i); if (!(id instanceof String) || !((String) id).matches("[0-9a-f]{64}") || !ids.add((String) id)) return null;
        }
        if (!((String) unknown).isEmpty() && !ids.contains(unknown)) return null;
        if (!((String) unknown).isEmpty()) return state; // No source readback exists: uncertainty survives midnight/restarts.
        if (savedDay > day) return null;
        return savedDay == day ? state : MyUtils.newJSONObject().put("day", day).put("attempts", 0).put("consumed", new JSONArray()).put("unknown", "").put("limited", false);
    }

    private static String assistOwnSecret(JSONObject response) {
        String found = null;
        for (JSONObject node : new JSONObject[]{response, response == null ? null : response.optJSONObject("data")}) {
            if (node == null) continue;
            for (String key : new String[]{"message", "obj"}) if (node.has(key) && !node.isNull(key)) {
                Object raw = node.opt(key);
                if (raw instanceof String && ((String) raw).trim().isEmpty()) continue;
                String value = assistSecret(raw);
                if (value == null || found != null && !found.equals(value)) return null;
                found = value;
            }
        }
        return found;
    }

    private static boolean appendAssistTargets(Map<String, String> targets, JSONArray rows, boolean requireUid) {
        if (rows == null || rows.length() > 100) return false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) return false;
            String secret = null;
            for (String key : new String[]{"shareId", "shareSecret", "secret"}) if (row.has(key) && !row.isNull(key)) {
                String value = assistSecret(row.opt(key)); if (value == null || secret != null && !secret.equals(value)) return false;
                secret = value;
            }
            if (secret == null) return false;
            String targetUid = "";
            if (row.has("userId")) {
                if (!(row.opt("userId") instanceof String)) return false;
                targetUid = row.optString("userId").trim();
                if (!targetUid.matches("[0-9]{6,32}")) return false;
            } else if (requireUid) return false;
            if (!targetUid.isEmpty()) for (Map.Entry<String, String> old : targets.entrySet())
                if (targetUid.equals(old.getValue()) && !secret.equals(old.getKey())) return false;
            String previous = targets.putIfAbsent(secret, targetUid);
            if (previous != null && !previous.isEmpty() && !targetUid.isEmpty() && !previous.equals(targetUid)) return false;
            if (targets.size() > 100) return false;
        }
        return true;
    }

    private static JSONArray assistArray(String raw) {
        if (raw == null || raw.length() > 65536 || !raw.trim().startsWith("[")) return null;
        try { return new JSONArray(raw); } catch (Exception invalid) { return null; }
    }

    private static URL flowerExternalUrl(String configured, String uid) throws Exception {
        URL prefix = new URL(configured);
        if (!"https".equalsIgnoreCase(prefix.getProtocol()) || prefix.getHost().isEmpty() || prefix.getUserInfo() != null
                || prefix.getQuery() != null || prefix.getRef() != null || prefix.getPort() != -1 && prefix.getPort() != 443) return null;
        String url = configured + (configured.endsWith("/") ? "" : "/") + "PlantingFlowersShare/" + uid;
        return new URL(url);
    }

    /** Explicit HTTPS services receive source-defined bodies/UID only, never business headers. */
    private static String flowerExternalRequest(URL url, String body, String uid) throws Exception {
        if (url == null) return null;
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        if (bytes != null && bytes.length > 65536) return null;
        checkAccount(uid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(5000); conn.setReadTimeout(8000); conn.setInstanceFollowRedirects(false); conn.setUseCaches(false);
            conn.setRequestMethod(body == null ? "GET" : "POST"); conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Cookie", ""); conn.setRequestProperty("Authorization", "");
            if (bytes != null) {
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setDoOutput(true); conn.setFixedLengthStreamingMode(bytes.length); checkAccount(uid);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            checkAccount(uid);
            int status = conn.getResponseCode();
            if (body == null ? status != HttpURLConnection.HTTP_OK : status < 200 || status > 299) throw new IOException();
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) {
                    checkAccount(uid); if (out.size() + n > 65536) throw new IOException(); out.write(buffer, 0, n);
                }
                checkAccount(uid); return out.toString("UTF-8");
            }
        } finally { conn.disconnect(); }
    }

    /** Xu's opt-in list contract: prefix + PlantingFlowersShare/current UID; no business credentials. */
    private JSONArray flowerAssistPool(String uid) throws Exception {
        String configured = plantingFlowersAssistListUrl.getValue().trim();
        if (!plantingFlowersAssistPool.getValue() || configured.isEmpty()) return new JSONArray();
        URL url = flowerExternalUrl(configured, uid);
        return url == null ? null : assistArray(flowerExternalRequest(url, null, uid));
    }

    private static boolean flowerPublicationReady(BooleanModelField option, StringModelField url, String key) {
        return option.getValue() && !url.getValue().trim().isEmpty()
                && !Integer.toString(businessDay()).equals(RuntimeInfo.getInstance().getString(key));
    }

    private void flowerOwnSecretUpload(String token, String uid) throws Exception {
        if (!flowerPublicationReady(plantingFlowersAssistUpload, plantingFlowersAssistPushUrl, "flowers::ownSecretUpload")) return;
        String ownSecret = assistOwnSecret(request("/plantingFlowers/api/getShareSecret", MyUtils.newJSONObject(), token, uid));
        if (ownSecret == null) return;
        flowerPublish(plantingFlowersAssistUpload, plantingFlowersAssistPushUrl, "flowers::ownSecretUpload",
                MyUtils.newJSONObject().put("shareId", ownSecret).toString(), uid);
    }

    private void flowerPublish(BooleanModelField option, StringModelField configuredUrl, String key, String body, String uid) throws Exception {
        int day = businessDay();
        if (!flowerPublicationReady(option, configuredUrl, key) || body.getBytes(StandardCharsets.UTF_8).length > 65536) return;
        URL url = flowerExternalUrl(configuredUrl.getValue().trim(), uid); if (url == null) return;
        checkAccount(uid); if (day != businessDay() || !option.getValue()) return;
        if (!RuntimeInfo.getInstance().putVerified(key, Integer.toString(day))) return;
        String ack = flowerExternalRequest(url, body, uid);
        checkAccount(uid);
        if (ack != null) Log.record("种花大作战：配置服务上传请求已提交，存储结果未确认，本日不重复提交");
    }

    private static String assistResponseState(JSONObject response) {
        String state = null;
        for (JSONObject node : new JSONObject[]{response, response == null ? null : response.optJSONObject("data")}) {
            if (node == null) continue;
            JSONObject payload = node.optJSONObject("obj");
            if (node.has("obj") && !node.isNull("obj") && payload == null) return null;
            Object[] candidates = {payload == null ? null : payload.opt("state"), node.opt("message")};
            for (Object raw : candidates) if (raw != null && raw != JSONObject.NULL) {
                if (!(raw instanceof String)) return null;
                String value = ((String) raw).trim(); if (value.isEmpty()) continue;
                if (state != null && !state.equals(value)) return null; state = value;
            }
        }
        return state;
    }

    private void flowerAssist(String token, String uid) throws Exception {
        if (!flowerAssistConfigured()) return;
        int day = businessDay(); RuntimeInfo runtime = RuntimeInfo.getInstance(); JSONObject state = assistState(runtime, day);
        if (state == null) { Log.record("种花大作战：助力持久状态未通过校验，停止提交"); return; }
        boolean unknown = !state.optString("unknown").isEmpty();
        if (!unknown && (Boolean.TRUE.equals(state.opt("limited")) || flowerInteger(state, "attempts") >= Math.min(3, plantingFlowersAssistDailyLimit.getValue()))) return;
        String ownSecret = assistOwnSecret(request("/plantingFlowers/api/getShareSecret", MyUtils.newJSONObject(), token, uid));
        if (ownSecret == null) return;
        if (unknown) {
            Log.record("种花大作战：此前助力请求结果未确认；仅查询本账号口令，跨日也不重复提交"); return;
        }
        Map<String, String> targets = new LinkedHashMap<>();
        String manual = plantingFlowersManualSecret.getValue(); if (manual.length() > 8192) return;
        for (String line : manual.split("\\r?\\n")) if (!line.trim().isEmpty()) {
            String secret = assistSecret(line); if (secret == null) return; targets.putIfAbsent(secret, "");
        }
        String mapped = plantingFlowersAssistTargets.getValue().trim();
        if (!mapped.isEmpty() && !appendAssistTargets(targets, assistArray(mapped), true)) return;
        if (plantingFlowersAssistPool.getValue() && !appendAssistTargets(targets, flowerAssistPool(uid), false)) return;
        if (targets.size() > 100) return;
        Set<String> consumed = new LinkedHashSet<>(); JSONArray consumedJson = state.optJSONArray("consumed");
        for (int i = 0; i < consumedJson.length(); i++) consumed.add(consumedJson.optString(i));
        for (Map.Entry<String, String> target : targets.entrySet()) {
            checkAccount(uid);
            if (!plantingFlowersAssist.getValue() || day != businessDay() || Boolean.TRUE.equals(state.opt("limited"))
                    || flowerInteger(state, "attempts") >= Math.min(3, plantingFlowersAssistDailyLimit.getValue())) return;
            if (target.getKey().equals(ownSecret) || uid.equals(target.getValue())) continue;
            String identity = bytesToHex(MessageDigest.getInstance("SHA-256").digest((uid + "\n" + target.getKey()).getBytes(StandardCharsets.UTF_8)));
            if (!consumed.add(identity)) continue;
            consumedJson.put(identity); state.put("attempts", consumedJson.length()).put("unknown", identity);
            checkAccount(uid);
            if (!runtime.putVerified("flowers::assistState", state.toString())) return;
            JSONObject reply = request("/plantingFlowers/api/clickFriendShare/addFriend", MyUtils.newJSONObject().put("shareSecret", target.getKey()), token, uid);
            checkAccount(uid); if (day != businessDay()) return;
            // Source taskRecords schema; no source readback proves a new success, so state never becomes 2.
            if (plantingFlowersAssistRecordUpload.getValue() && !plantingFlowersRecordPushUrl.getValue().trim().isEmpty())
                flowerAssistRecords.put(MyUtils.newJSONObject().put("userId", target.getValue().isEmpty() ? "friend" : target.getValue())
                        .put("shareId", target.getKey()).put("state", 0));
            String responseState = assistResponseState(reply);
            String message;
            if ("today_have_help".equals(responseState)) message = "当前口令服务端返回今日已助力，未记为本次成功";
            else if ("helper_to_limit".equals(responseState)) { message = "服务端返回今日助力次数已达上限"; state.put("limited", true); }
            else if ("inviter_to_upper_limit".equals(responseState)) message = "服务端返回该口令接收方已达上限";
            else if ("inviter_eq_helper".equals(responseState)) message = "服务端返回自身口令，已跳过";
            else {
                Log.record("种花大作战：助力请求已提交，结果未确认；上游未提供好友历史回查，已持久冻结助力，跨日也不重复提交"); return;
            }
            state.put("unknown", ""); checkAccount(uid);
            if (!runtime.putVerified("flowers::assistState", state.toString())) return;
            Log.record("种花大作战：" + message);
            TimeUtil.sleep(1000);
        }
    }

    private static JSONObject request(String path, JSONObject body, String token, String uid) throws Exception {
        checkAccount(uid);
        String host = path.startsWith("/dataCenter/") ? "https://datacenter.zhisanzhao.com" : HOST;
        HttpURLConnection conn = (HttpURLConnection) new URL(host + path).openConnection();
        try {
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setRequestMethod(body == null ? "GET" : "POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("channel", "fenxiang");
            if (token != null) {
                Calendar now = MyUtils.getInstance();
                String today = String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH));
                conn.setRequestProperty("token", token);
                conn.setRequestProperty("uid", uid);
                conn.setRequestProperty("dataCenterEncryption", digest(uid + "dj#i9a%u4j^2e#w*n43" + today));
                conn.setRequestProperty("coffeeEncryption", digest(uid + "R%v7j^4s&ug6c*8" + today));
                conn.setRequestProperty("aesEncryption5", flowerSignature(uid));
            }
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(bytes.length);
                checkAccount(uid);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            checkAccount(uid);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException();
            String raw;
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    checkAccount(uid);
                    // ponytail: 单响应最多1 MiB；实际目录超过此上限时再调整。
                    if (out.size() + n > 1024 * 1024) throw new IOException();
                    out.write(buffer, 0, n);
                }
                raw = out.toString("UTF-8");
            }
            checkAccount(uid);
            JSONObject result = MyUtils.newJSONObject(raw), data = result.optJSONObject("data");
            if (path.equals("/plantingFlowers/api/clickFriendShare/addFriend")) {
                boolean codeSeen = false;
                for (JSONObject node : new JSONObject[]{result, data}) {
                    if (node == null) continue;
                    if (node.has("success") && !(node.opt("success") instanceof Boolean)) throw new IOException();
                    if (node.has("code")) { if (flowerInteger(node, "code") < 0) throw new IOException(); codeSeen = true; }
                }
                if (!codeSeen) throw new IOException();
                return result; // Source-defined business states may accompany non-200 business codes.
            }
            for (JSONObject node : new JSONObject[]{result, data}) {
                if (node == null) continue;
                if (node.has("success") && !Boolean.TRUE.equals(node.opt("success"))) throw new IOException();
                if (node.has("code") && !"200".equals(String.valueOf(node.opt("code")))) throw new IOException();
            }
            if (!"200".equals(String.valueOf(result.opt("code"))) && (data == null || !"200".equals(String.valueOf(data.opt("code"))))) throw new IOException();
            if (path.equals("/plantingFlowers/api/getShareSecret")) return result;
            if (path.endsWith("/cloudRewardConfig") || path.endsWith("/bugRewardConfig") || path.startsWith("/dataCenter/api/task/list?")) {
                Object directArray = result.opt("obj"), nestedArray = data == null ? null : data.opt("obj");
                if (directArray != null && directArray != JSONObject.NULL && nestedArray != null && nestedArray != JSONObject.NULL
                        && !directArray.toString().equals(nestedArray.toString())) throw new IOException();
                Object value = directArray == null || directArray == JSONObject.NULL ? nestedArray : directArray;
                if (value instanceof JSONArray) return MyUtils.newJSONObject().put(path.startsWith("/dataCenter/") ? "taskList" : "currently", value);
            }
            JSONObject direct = result.optJSONObject("obj"), nested = data == null ? null : data.optJSONObject("obj");
            if (direct != null && nested != null && !direct.toString().equals(nested.toString())) throw new IOException();
            JSONObject payload = direct == null ? nested : direct;
            if (payload == null) {
                if (body == null && !path.startsWith("/dataCenter/api/task/action/finish?")
                        || result.has("obj") && !result.isNull("obj") || data != null && data.has("obj") && !data.isNull("obj")) throw new IOException();
                return MyUtils.newJSONObject(); // 提交ACK可以无obj，成功仍以随后签到状态回查为准。
            }
            return payload;
        } finally { conn.disconnect(); }
    }

    private static String shiftedDigits(String input) {
        StringBuilder result = new StringBuilder();
        int increment = 1;
        for (int i = 0; i < input.length(); i++) result.append(i % 2 == 0 ? (char) ('0' + (input.charAt(i) - '0' + increment++) % 10) : input.charAt(i));
        return result.toString();
    }

    /** Xu种花客户端的请求签名格式；只绑定本轮当前账号，不保存授权或令牌。 */
    private static String flowerSignature(String uid) throws Exception {
        String user = shiftedDigits(uid), timestamp = shiftedDigits(String.valueOf(System.currentTimeMillis() / 1000));
        int head = Math.min(user.length(), timestamp.length());
        StringBuilder mixed = new StringBuilder();
        for (int i = 0; i < head; i++) mixed.append(user.charAt(i)).append(timestamp.charAt(i));
        mixed.append(user.substring(head));
        for (int at : new int[]{20, 15, 10, 5}) mixed.insert(Math.min(at, mixed.length()), (int) (Math.random() * 10));
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < mixed.length(); i += 2) {
            int index = Integer.parseInt(mixed.substring(i, Math.min(i + 2, mixed.length())));
            plain.append(String.format(java.util.Locale.ROOT, "%04x", (int) DICT[index])).append((char) ('a' + (int) (Math.random() * 26)));
        }
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new IvParameterSpec(AES_IV));
        String encrypted = bytesToHex(cipher.doFinal(plain.toString().getBytes(StandardCharsets.UTF_8)));
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < encrypted.length(); i += 5) result.append(encrypted, i, Math.min(i + 5, encrypted.length())).append((char) ('a' + (int) (Math.random() * 26)));
        return result.toString();
    }

    private static String digest(String text) throws Exception {
        return bytesToHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(Character.forDigit((value >> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
        return result.toString();
    }
    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }
}
