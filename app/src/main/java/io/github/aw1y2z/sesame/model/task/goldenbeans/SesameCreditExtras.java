package io.github.aw1y2z.sesame.model.task.goldenbeans;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.LocalDate;
import java.time.DateTimeException;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.model.task.antGame.GameTask;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** Credit tasks are separate from GoldenBeanRpcManager's ZHIMA/lianjin entry. */
final class SesameCreditExtras {
    private static final String CREDIT = "com.antgroup.zmxy.zmmemberop.biz.rpc.";
    private static final String PLAY = "SwbtxJSo8OOUrymAU%2FHnY2jyFRc%2BkCJ3";
    private static final String CHANNEL = "ch_url-https://2021002135657012.hybrid.alipay-eco.com/index.html";
    private static final String REFER = "https://render.alipay.com/p/yuyan/180020010001288004/zmTree.html?caprMode=sync&chInfo=ch_zmzlzms__chsub_zlsy_icon";
    private static final String TRIGGER = "alipay.promoprod.play.trigger";

    private record TreeTask(String id, String status, boolean signup, boolean manual, String title,
                            String appletType, String channel, String chInfo, String refer, String play,
                            String appId, boolean rent, boolean external, String type, int seconds, String jumpUrl) {
        boolean terminal() { return List.of("DONE", "COMPLETE", "FINISHED", "RECEIVED").contains(status)
                || "RECEIVE_SUCCESS".equals(status) && !manual; }
        String stage() {
            if ("TO_RECEIVE".equals(status) || "RECEIVE_SUCCESS".equals(status) && manual) return "receive";
            boolean direct = type.isEmpty() || "CONTINUE_SIGN_TASK".equals(type)
                    || "BROWSER".equals(type) && seconds > 0 && !jumpUrl.isEmpty();
            if (signup && direct && List.of("NONE_SIGNUP", "UN_SIGNUP").contains(status)) return "signup";
            if (List.of("NOT_DONE", "SIGNUP_COMPLETE", "SIGNUP_COMPLETED").contains(status)
                    && direct && !"PUSH_MODEL".equals(appletType) && !external) return "send";
            return null;
        }
    }
    private record TreeAd(String id, String title, String space, int seconds, int reward) {}
    private record TreeDelegate(AlchemyTask task, String appId, String actionUrl) {}
    private record TreeState(JSONObject home, Map<String, TreeTask> tasks, Map<String, TreeAd> ads) {}
    private record CleanState(String tree, int score, int clicks, Set<String> areas) {}
    private record GameDuration(String appId, String source, int seconds) {}
    private record ChannelGame(String scene, String task, String source, String module, String guide) {}
    private record P2eEntry(Map<String, String> fields, List<String> types, List<String> requests) {}
    private record AlchemyTask(String id, String record, String title, String type, int current, int limit,
                               boolean finished, boolean external, String rewardType, String rewardId, String bizId, String space, int seconds,
                               String gameAppId, boolean gameEligible, GameDuration gameDuration, ChannelGame channelGame, P2eEntry p2eEntry) {
        boolean done() { return finished || current >= limit; }
        boolean browse() { return "LIFE_RECORD".equals(type) && title.contains("浏览") && !external
                && !id.equals("zml_zmdabiaoge") && !id.equals("zml_zmzl_cyz_erfang"); }
        boolean freeRent() { return "LIFE_RECORD".equals(type) && id.equals("zml_zmzl_cyz_erfang")
                && title.contains("浏览") && title.contains("芝麻租赁") && !external; }
        boolean directAd() { return "AD_TASK".equals(type) && !external && !bizId.isEmpty()
                && space.isEmpty() && !"LJCS".equals(rewardType) && title.contains("浏览") && title.contains("广告"); }
        boolean mappedGame() { return gameEligible && "LIFE_RECORD".equals(type) && GameTask.matchAppId(gameAppId) != null; }
        boolean durationGame() { return "LIFE_RECORD".equals(type) && gameDuration != null; }
        boolean channelGameTask() { return "LIFE_RECORD".equals(type) && channelGame != null; }
        boolean p2eGameTask() { return "LIFE_RECORD".equals(type) && p2eEntry != null; }
    }

    /** Run only explicitly enabled domains; the bridge supplies current account/client credentials. */
    static void run(boolean treeTasks, boolean purification, boolean alchemyTasks, boolean timeReward, boolean nextDayReward) {
        if (!treeTasks && !purification && !alchemyTasks && !timeReward && !nextDayReward) return;
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return;
        if (treeTasks) runDomain("treeTasks", uid, SesameCreditExtras::treeTasks);
        if (purification) runDomain("treeClean", uid, SesameCreditExtras::purification);
        if (alchemyTasks) runDomain("alchemyTasks", uid, SesameCreditExtras::alchemyTasks);
        if (timeReward) runDomain("alchemyTime", uid, SesameCreditExtras::timeReward);
        if (nextDayReward) runDomain("alchemyNextDay", uid, SesameCreditExtras::nextDayReward);
    }
    private interface Flow { void run(Guard guard) throws JSONException; }
    private static void runDomain(String domain, String uid, Flow flow) {
        Guard guard = new Guard(domain, uid);
        try {
            guard.check();
            if (!guard.blocked()) flow.run(guard);
        } catch (TaskCancelledException cancelled) {
            throw cancelled;
        } catch (Exception error) {
            Log.err("SesameCreditExtras", domain + "查询或动作异常", error);
        }
    }

    /** Unconfirmed submission remains frozen across restarts and business days. */
    private static final class Guard {
        final String prefix, uid, day;
        final RuntimeInfo runtime;
        Guard(String domain, String uid) {
            this.prefix = "golden::sesameExtra::" + domain + "::";
            this.uid = uid; this.day = day(); this.runtime = RuntimeInfo.getInstance();
        }
        void check() {
            TimeUtil.sleep(0);
            if (!uid.equals(UserIdMap.getCurrentUid())) throw new TaskCancelledException();
        }
        boolean blocked() { check(); return !runtime.getString(prefix + "unknown").isEmpty(); }
        boolean attempted(String key) { return day.equals(runtime.getString(prefix + "action::" + key)); }
        boolean begin(String key) throws JSONException {
            check();
            if (!day.equals(day()) || blocked() || attempted(key)) return false;
            String raw = runtime.getString(prefix + "budget"); Integer count = 0;
            if (!raw.isEmpty()) {
                JSONObject budget = MyUtils.newJSONObject(raw); String budgetDay = text(budget, "day");
                Integer previousCount = integer(budget, "count");
                if (!validBudgetDay(budgetDay, day) || previousCount == null || previousCount > 40) return false;
                if (day.equals(budgetDay)) count = previousCount;
            }
            if (count >= 40) return false;
            if (!runtime.putVerified(prefix + "budget", MyUtils.newJSONObject().put("day", day).put("count", count + 1).toString())) return false;
            if (!runtime.putVerified(prefix + "action::" + key, day)) return false;
            return runtime.putVerified(prefix + "unknown", day);
        }
        boolean finish(JSONObject ack, boolean confirmed) {
            return finish(ack != null, confirmed);
        }
        boolean finish(boolean accepted, boolean confirmed) {
            check();
            if (!day.equals(day()) || !accepted || !confirmed) {
                Log.record("芝麻额外任务：结果未确认，今日停止该流程重复提交");
                return false;
            }
            if (!runtime.putVerified(prefix + "unknown", "")) return false;
            Log.other("芝麻额外任务：服务端状态回查已确认");
            return true;
        }
        void waitSeconds(int seconds) {
            for (int elapsed = 0; elapsed < seconds; elapsed++) { TimeUtil.sleep(1000); check(); }
        }
        JSONObject request(String method, JSONObject args, boolean mutation) {
            check();
            if (!day.equals(day())) return null;
            String body = new JSONArray().put(args).toString();
            String raw = mutation ? ApplicationHook.requestString(method, body, 1, 0) : ApplicationHook.requestString(method, body);
            check();
            if (!day.equals(day())) return null;
            JSONObject root = MyUtils.newJSONObject(raw);
            if (!success(root, 0)) return null;
            return root.optJSONObject("resData") != null ? root.optJSONObject("resData") : root;
        }
    }
    private static String day() {
        Calendar c = MyUtils.getInstance();
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
    private static boolean validBudgetDay(String value, String today) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        try { LocalDate date = LocalDate.parse(value); return date.getYear() > 0 && !date.isAfter(LocalDate.parse(today)); }
        catch (DateTimeException malformed) { return false; }
    }
    private static String text(JSONObject o, String key) {
        Object value = o == null ? null : o.opt(key);
        if (!(value instanceof String)) return null;
        String s = ((String) value).trim();
        return s.isEmpty() || s.length() > 4096 || "null".equalsIgnoreCase(s) || s.startsWith("{") || s.startsWith("[") ? null : s;
    }
    private static Integer integer(JSONObject o, String key) {
        Object value = o == null ? null : o.opt(key);
        if (!(value instanceof Number)) return null;
        double n = ((Number) value).doubleValue();
        return Double.isFinite(n) && n >= 0 && n <= Integer.MAX_VALUE && n == Math.rint(n) ? (int) n : null;
    }
    private static Boolean bool(JSONObject o, String key) { Object v = o == null ? null : o.opt(key); return v instanceof Boolean ? (Boolean) v : null; }
    private static String first(String fallback, String key, JSONObject... objects) {
        for (JSONObject o : objects) { String value = text(o, key); if (value != null) return value; }
        return fallback;
    }
    private static boolean success(JSONObject root, int depth) {
        if (root == null || root.length() == 0 || depth > 3 || RpcRequestGuard.isFailure(root)) return false;
        boolean evidence = false;
        for (String key : new String[]{"success", "isSuccess"}) if (root.has(key)) {
            if (!Boolean.TRUE.equals(root.opt(key))) return false;
            evidence = true;
        }
        for (String key : new String[]{"resultCode", "code", "errCode", "errorCode"}) if (root.has(key) && !root.isNull(key)) {
            Object code = root.opt(key);
            String s = code instanceof String ? ((String) code).trim() : code instanceof Number && ((Number) code).doubleValue() == Math.rint(((Number) code).doubleValue()) ? code.toString() : "";
            if (!("SUCCESS".equals(s) || "200".equals(s) || "100".equals(s) || "0".equals(s))) return false;
            evidence = true;
        }
        JSONObject nested = root.optJSONObject("resData");
        if (nested != null) return success(nested, depth + 1);
        JSONObject data = root.optJSONObject("data");
        if (data != null && (data.has("success") || data.has("isSuccess") || data.has("resultCode") || data.has("errCode")) && !success(data, depth + 1)) return false;
        return evidence;
    }
    private static boolean unsafeTitle(String title) {
        return title == null || title.contains("邀请") || title.contains("助力") || title.contains("下单") || title.contains("开通")
                || title.contains("支付") || title.contains("购买") || title.contains("订阅") || title.contains("授权") || title.contains("签约");
    }

    private static JSONObject treeQuery(Guard g, boolean rent) throws JSONException {
        JSONObject ext = MyUtils.newJSONObject();
        if (rent) ext.put("batchId", "").put("chInfo", CHANNEL);
        JSONObject root = g.request(TRIGGER, MyUtils.newJSONObject().put("operationCode", rent ? "RENT_GREEN_TASK_LIST_QUERY" : "ZHIMA_TREE_HOME_PAGE")
                .put("playInfo", PLAY).put("refer", REFER).put("extInfo", ext), false);
        JSONObject info = root == null ? null : root.optJSONObject("extInfo");
        return info == null ? null : info.optJSONObject(rent ? "taskDetailList" : "zhimaTreeHomePageQueryResult");
    }
    private static TreeTask treeTask(JSONObject row, boolean rent, String uid) {
        JSONObject base = row == null ? null : row.optJSONObject("taskBaseInfo");
        if (base == null) return null;
        JSONObject material = row.optJSONObject("taskMaterial"), props = row.optJSONObject("taskExtProps"), morpho = null;
        if (row.has("taskMaterial") && material == null || material != null && material.has("taskType") && text(material, "taskType") == null) return null;
        String type = first("", "taskType", material);
        Integer seconds = integer(material, "browseTime");
        String jumpUrl = first("", "jumpUrl", material);
        if (props != null && props.has("TASK_MORPHO_DETAIL")) {
            Object value = props.opt("TASK_MORPHO_DETAIL");
            morpho = value instanceof JSONObject ? (JSONObject) value : value instanceof String ? MyUtils.newJSONObject((String) value) : null;
            if (morpho == null || morpho.length() == 0) return null;
        }
        String id = first(null, "appletId", base);
        if (id == null) id = first(null, "taskId", base);
        if (id == null) id = first(null, "appId", base);
        if (id == null) id = first(null, "taskId", row);
        if (id == null || id.length() > 256) return null;
        String status = text(row, "taskProcessStatus");
        Boolean signup = bool(row, "needSignUp"), manual = bool(row, "needManuallyReceiveAward");
        if (row.has("needSignUp") && signup == null || row.has("needManuallyReceiveAward") && manual == null) return null;
        if (signup == null && props != null && props.has("needSignUp")) { signup = bool(props, "needSignUp"); if (signup == null) return null; }
        String title = first(null, "title", material, morpho, base);
        if (title == null) title = first(id, "appletName", base);
        JSONObject participate = row.optJSONObject("taskParticipateExtInfo");
        for (JSONObject o : new JSONObject[]{row, base, participate}) if (o != null && o.has("userId")
                && (! (o.opt("userId") instanceof String) || text(o, "userId") == null)) return null;
        String owner = first(null, "userId", row, base, participate);
        if (owner != null && !owner.equals(uid)) return null;
        boolean external = unsafeTitle(title) || "EVENT_TRIGGER".equals(text(row, "sendCampTriggerType"))
                || "EVENT_TRIGGER".equals(text(base, "sendCampTriggerType"));
        return status == null ? null : new TreeTask(id, status, Boolean.TRUE.equals(signup), manual == null || manual, title,
                first("", "appletType", base), first("", "taskChannel", row, base, material, morpho),
                first(CHANNEL, "chInfo", row, base, material, morpho, participate),
                first(REFER, "refer", row, base, material, morpho, participate), first(PLAY, "playInfo", row, base, material, morpho, participate),
                first("", "appId", base, row, material, morpho), rent, external, type,
                seconds != null && seconds >= 1 && seconds <= 300 ? seconds : -1, jumpUrl);
    }
    private static boolean appendTree(Map<String, TreeTask> target, JSONArray rows, boolean rent, String uid) {
        if (rows == null || rows.length() > 200) return false;
        for (int i = 0; i < rows.length(); i++) {
            TreeTask task = treeTask(rows.optJSONObject(i), rent, uid);
            if (task == null) return false;
            TreeTask old = target.putIfAbsent(task.id, task);
            if (old != null && !old.equals(task)) return false;
        }
        return true;
    }
    private static TreeState treeState(Guard g) throws JSONException {
        JSONObject home = treeQuery(g, false), rent = treeQuery(g, true);
        if (home == null || rent == null) return null;
        Map<String, TreeTask> tasks = new LinkedHashMap<>();
        boolean found = false;
        for (String key : new String[]{"browseTaskList", "taskStatusList", "staticSceneGuideTaskList"}) if (home.has(key)) {
            found = true; if (!appendTree(tasks, home.optJSONArray(key), false, g.uid)) return null;
        }
        if (!found || !appendTree(tasks, rent.optJSONArray("taskDetailList"), true, g.uid) || tasks.size() > 200) return null;
        Map<String, TreeAd> ads = treeAds(rent);
        return ads == null ? null : new TreeState(home, tasks, ads);
    }
    private static JSONObject objectValue(Object value) {
        if (value instanceof JSONObject) return (JSONObject) value;
        if (value instanceof String) { JSONObject parsed = MyUtils.newJSONObject((String) value); return parsed.length() == 0 ? null : parsed; }
        return null;
    }
    private static String decode(String value) {
        if (value == null || value.length() > 8192) return "";
        for (int i = 0; i < 3; i++) {
            try { String next = java.net.URLDecoder.decode(value, "UTF-8"); if (next.equals(value)) break; value = next; }
            catch (Exception ignored) { return ""; }
        }
        return value;
    }
    private static String parameter(String url, String key) {
        String decoded = decode(url); String marker = key + "="; int start = decoded.indexOf(marker);
        if (start < 0) return ""; int end = decoded.indexOf('&', start + marker.length());
        return decode(decoded.substring(start + marker.length(), end < 0 ? decoded.length() : end));
    }
    private static int duration(String space) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("_duration=(\\d+)(?:$|[^0-9])").matcher(space);
        try { return m.find() ? Integer.parseInt(m.group(1)) : 0; } catch (NumberFormatException ignored) { return 0; }
    }
    private static int rewardNumber(JSONObject... objects) {
        for (JSONObject o : objects) for (String key : new String[]{"taskRewardAmount", "rewardNum"}) if (o != null && o.has(key)) {
            Integer n = integer(o, key); if (n != null && n <= 10000) return n;
            String s = text(o, key);
            if (s != null && s.matches("[0-9]{1,5}")) { int value = Integer.parseInt(s); return value <= 10000 ? value : 0; }
            return 0;
        }
        return 0;
    }
    private static Map<String, TreeAd> treeAds(JSONObject rent) {
        Map<String, TreeAd> ads = new LinkedHashMap<>();
        if (!rent.has("spaceResultList")) return ads;
        JSONArray spaces = rent.optJSONArray("spaceResultList"); if (spaces == null || spaces.length() > 100) return null;
        int count = 0;
        for (int i = 0; i < spaces.length(); i++) {
            JSONObject space = spaces.optJSONObject(i); JSONArray rows = space == null ? null : space.optJSONArray("spaceObjectList");
            if (rows == null || rows.length() > 200) return null;
            for (int j = 0; j < rows.length(); j++) {
                JSONObject row = rows.optJSONObject(j); if (row == null || ++count > 200) return null;
                JSONObject content = row.has("content") ? objectValue(row.opt("content")) : row;
                if (content == null) return null;
                JSONObject log = content.optJSONObject("logExtMap"), schema = content.has("schemaJson") ? objectValue(content.opt("schemaJson")) : null;
                if (content.has("schemaJson") && schema == null) return null;
                String url = first("", "clickThroughUrl", content); if (url.isEmpty()) url = first("", "url", schema);
                String id = first("", "bizId", log); if (id.isEmpty()) id = first("", "xlightBizId", content);
                if (id.isEmpty()) id = first("", "bizId", content); if (id.isEmpty()) id = first("", "adBizId", schema);
                if (id.isEmpty()) id = parameter(url, "bizId");
                String code = decode(first("", "renderConfigKey", log));
                if (code.isEmpty()) code = parameter(url, "renderConfigKey");
                if (code.isEmpty()) code = decode(first("", "spaceCode", log, space));
                String title = first("", "taskMainTitle", schema); if (title.isEmpty()) title = first("芝麻树广告浏览任务", "title", schema, content);
                int seconds = duration(code), reward = rewardNumber(schema, content, log);
                // An ad without a source duration/reward contract cannot be qualified for an inventory readback.
                if (id.isEmpty() || id.length() > 256) return null;
                TreeAd ad = new TreeAd(id, title, code, seconds, reward);
                TreeAd old = ads.putIfAbsent(id, ad); if (old != null && !old.equals(ad)) return null;
            }
        }
        return ads;
    }
    private static Integer cleanScore(JSONObject home) {
        Integer score = integer(home, "purificationScore");
        return score == null && home != null && !home.has("purificationScore") ? integer(home, "currentCleanNum") : score;
    }
    private static void treeTasks(Guard g) throws JSONException {
        for (int step = 0; step < 40; step++) {
            TreeState before = treeState(g); if (before == null) return;
            TreeTask chosen = null; String stage = null;
            for (TreeTask task : before.tasks.values()) {
                String candidate = task.stage();
                if (candidate != null && !task.terminal() && !g.attempted(task.id + "::" + candidate)
                        && ("receive".equals(candidate) || !task.external)) { chosen = task; stage = candidate; break; }
            }
            if (chosen == null) {
                Map<String, TreeDelegate> delegates = null;
                for (TreeTask task : before.tasks.values()) {
                    if (!task.rent || !"RENT".equalsIgnoreCase(task.channel) || !"PUSH_MODEL".equals(task.appletType)
                            || task.external || task.appId.isEmpty() || !task.title.contains("浏览")
                            || !List.of("NOT_DONE", "SIGNUP_COMPLETE", "SIGNUP_COMPLETED").contains(task.status)) continue;
                    if (delegates == null) delegates = treeDelegates(g);
                    if (delegates == null) return;
                    TreeDelegate delegate = delegates.get(task.appId);
                    if (delegate == null || delegate.task.done() || g.attempted(task.id + "::delegate::" + delegate.task.record)) continue;
                    if (!completeTreeDelegate(g, task, delegate)) return;
                    chosen = task;
                    break;
                }
                if (chosen != null) continue;
                TreeAd ad = null;
                for (TreeAd candidate : before.ads.values()) if (!g.attempted("ad::" + candidate.id) && !unsafeTitle(candidate.title)
                        && candidate.seconds >= 1 && candidate.seconds <= 300 && candidate.reward > 0) { ad = candidate; break; }
                if (ad == null) return;
                Integer score = cleanScore(before.home); TreeState fresh = treeState(g);
                if (score == null || fresh == null || !ad.equals(fresh.ads.get(ad.id)) || !score.equals(cleanScore(fresh.home))) return;
                if (!g.begin("ad::" + ad.id)) return;
                if (g.request("com.alipay.adtask.biz.mobilegw.service.applayer.query", MyUtils.newJSONObject().put("spaceCode", ad.space), false) == null) return;
                g.waitSeconds(ad.seconds);
                JSONObject ack = g.request("com.alipay.adtask.biz.mobilegw.service.task.finish", MyUtils.newJSONObject().put("bizId", ad.id), true);
                TreeState after = treeState(g); Integer newScore = after == null ? null : cleanScore(after.home);
                if (!g.finish(ack, after != null && !after.ads.containsKey(ad.id) && newScore != null
                        && (long) newScore == (long) score + ad.reward)) return;
                continue;
            }
            TreeState fresh = treeState(g);
            if (fresh == null || !chosen.equals(fresh.tasks.get(chosen.id))) return;
            JSONObject ext = MyUtils.newJSONObject().put("chInfo", chosen.chInfo).put("taskId", chosen.id).put("stageCode", stage);
            JSONObject args = MyUtils.newJSONObject().put("operationCode", "RENT_GREEN_TASK_FINISH").put("playInfo", chosen.play)
                    .put("refer", chosen.refer).put("taskId", chosen.id).put("stageCode", stage).put("extInfo", ext);
            if (chosen.rent && "RENT".equalsIgnoreCase(chosen.channel)) {
                if (chosen.appId.isEmpty()) return;
                ext.put("appId", chosen.appId).put("appletId", chosen.appId).put("userId", g.uid);
                args.put("appId", chosen.appId).put("appletId", chosen.appId).put("userId", g.uid);
            }
            if ("send".equals(stage) && "BROWSER".equals(chosen.type)) {
                // Official floatMark sends after the complete taskMaterial.browseTime countdown.
                g.waitSeconds(chosen.seconds);
                fresh = treeState(g);
                if (fresh == null || !chosen.equals(fresh.tasks.get(chosen.id))) return;
            }
            if (!g.begin(chosen.id + "::" + stage)) return;
            JSONObject ack = g.request(TRIGGER, args, true);
            if ("send".equals(stage) && chosen.type.isEmpty()) g.waitSeconds(16); // Legacy Sen contract without declared browser metadata.
            TreeState after = treeState(g); TreeTask updated = after == null ? null : after.tasks.get(chosen.id);
            boolean confirmed = updated != null && sameTreeContext(chosen, updated) && switch (stage) {
                case "signup" -> List.of("SIGNUP_COMPLETE", "SIGNUP_COMPLETED", "NOT_DONE", "TO_RECEIVE").contains(updated.status) || updated.terminal();
                case "send" -> "TO_RECEIVE".equals(updated.status) || "RECEIVE_SUCCESS".equals(updated.status) || updated.terminal();
                case "receive" -> updated.terminal();
                default -> false;
            };
            if (!g.finish(ack, confirmed)) return;
        }
    }
    private static boolean sameTreeContext(TreeTask a, TreeTask b) {
        return a.id.equals(b.id) && a.title.equals(b.title) && a.chInfo.equals(b.chInfo) && a.refer.equals(b.refer)
                && a.play.equals(b.play) && a.appId.equals(b.appId) && a.channel.equals(b.channel) && a.rent == b.rent && a.manual == b.manual
                && a.type.equals(b.type) && a.seconds == b.seconds && a.jumpUrl.equals(b.jumpUrl) && a.external == b.external
                && a.appletType.equals(b.appletType);
    }

    private static Map<String, TreeDelegate> treeDelegates(Guard g) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("chInfo", "ch_zhimahome__chsub_zml_doudi").put("deliverStatus", "")
                .put("deliveryTemplateId", "").put("sceneCode", "DAILY_MUST_DO_CARD").put("searchAddToHomeTask", true)
                .put("searchGuidePopFlag", true).put("searchShareAssistTask", true).put("searchSubscribeTask", true)
                .put("supportJumpAuth", true).put("version", "new");
        JSONObject root = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", args, false);
        JSONObject data = root == null ? null : root.optJSONObject("data"), daily = data == null ? null : data.optJSONObject("dailyTaskListVO");
        if (data == null || data.has("dailyTaskListVO") && daily == null) return null;
        Map<String, TreeDelegate> delegates = new LinkedHashMap<>(); boolean found = false; int count = 0;
        for (JSONObject container : new JSONObject[]{data, daily}) {
            if (container == null) continue;
            for (String key : new String[]{"toCompleteVOS", "waitJoinTaskVOS", "waitCompleteTaskVOS"}) {
                if (!container.has(key)) continue;
                found = true; JSONArray rows = container.optJSONArray(key);
                if (rows == null || rows.length() > 200) return null;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i); if (row == null || ++count > 200) return null;
                    if (!Boolean.TRUE.equals(bool(row, "jumpToPushModel"))) continue;
                    JSONObject rule = row.optJSONObject("strategyRule");
                    if (!"芝麻租赁".equals(text(rule, "merchantName"))) continue;
                    Map<String, AlchemyTask> parsed = new LinkedHashMap<>();
                    if (!appendAlchemy(parsed, new JSONArray().put(row)) || parsed.size() != 1) return null;
                    AlchemyTask task = parsed.values().iterator().next();
                    String url = text(row, "actionUrl"), appId = url == null ? "" : parameter(url, "appId");
                    if (row.has("appId")) {
                        String explicit = text(row, "appId");
                        if (explicit == null || !appId.isEmpty() && !appId.equals(explicit)) return null;
                        appId = explicit;
                    }
                    if (!task.freeRent() || task.record.isEmpty() || url == null || appId.isEmpty()) continue;
                    TreeDelegate previous = delegates.putIfAbsent(appId, new TreeDelegate(task, appId, url));
                    if (previous != null) return null;
                }
            }
        }
        return found ? delegates : null;
    }

    private static boolean completeTreeDelegate(Guard g, TreeTask tree, TreeDelegate delegate) throws JSONException {
        TreeState fresh = treeState(g); Map<String, TreeDelegate> records = treeDelegates(g);
        if (fresh == null || !tree.equals(fresh.tasks.get(tree.id)) || records == null || !delegate.equals(records.get(tree.appId))) return false;
        String key = tree.id + "::delegate::" + delegate.task.record;
        if (!g.begin(key)) return false;
        JSONObject feedback = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
                MyUtils.newJSONObject().put("actionType", "TO_COMPLETE").put("bizType", "LIFE_RECORD").put("sceneCode", "zml")
                        .put("templateId", delegate.task.id).put("version", "new"), true);
        if (feedback == null) return false;
        g.waitSeconds(15);
        fresh = treeState(g); records = treeDelegates(g);
        if (fresh == null || !tree.equals(fresh.tasks.get(tree.id)) || records == null || !delegate.equals(records.get(tree.appId))) return false;
        JSONObject action = g.request("com.alipay.creditapollon.biz.rpc.api.rent.RentRpcService.actionSubmit",
                MyUtils.newJSONObject().put("chInfo", "chInfo=ch_zmzltf__chsub_zmlrw").put("type", "actionSubmit"), true);
        if (action == null) return false;
        records = treeDelegates(g);
        if (records == null || !delegate.equals(records.get(tree.appId))) return false;
        JSONObject ack = g.request(CREDIT + "promise.PromiseRpcManager.pushActivity",
                MyUtils.newJSONObject().put("recordId", delegate.task.record), true);
        records = treeDelegates(g); fresh = treeState(g);
        TreeDelegate after = records == null ? null : records.get(tree.appId); TreeTask updated = fresh == null ? null : fresh.tasks.get(tree.id);
        return g.finish(ack, after != null && delegate.appId.equals(after.appId) && delegate.actionUrl.equals(after.actionUrl)
                && sameAlchemy(delegate.task, after.task) && delegate.task.record.equals(after.task.record) && after.task.done()
                && (after.task.current > delegate.task.current || !delegate.task.finished && after.task.finished)
                && updated != null && sameTreeContext(tree, updated) && ("TO_RECEIVE".equals(updated.status) || updated.terminal()));
    }

    private static CleanState cleanState(JSONObject home) {
        if (home == null) return null;
        Integer score = integer(home, "purificationScore"); if (score == null && !home.has("purificationScore")) score = integer(home, "currentCleanNum");
        JSONArray trees = home.optJSONArray("trees"); JSONObject tree = trees == null || trees.length() != 1 ? null : trees.optJSONObject(0);
        Integer clicks = integer(tree, "remainPurificationClickNum"); String code = text(tree, "treeCode");
        JSONObject map = home.optJSONObject("garbageMap");
        if (score == null || clicks == null || code == null || map == null || map.length() > 30) return null;
        Set<String> areas = new LinkedHashSet<>(); java.util.Iterator<String> keys = map.keys(); int rows = 0;
        while (keys.hasNext()) {
            JSONArray groups = map.optJSONArray(keys.next()); if (groups == null || groups.length() > 200) return null;
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i); if (group == null || ++rows > 200) return null;
                String area = text(group, "subType"); JSONArray sub = group.optJSONArray("garbageSubList");
                if (group.has("garbageSubList") && sub == null || sub != null && sub.length() > 200) return null;
                if (sub == null || sub.length() == 0) { if (area == null || !areas.add(area)) return null; }
                else for (int j = 0; j < sub.length(); j++) {
                    JSONObject garbage = sub.optJSONObject(j); if (garbage == null || ++rows > 200) return null;
                    String value = first(area, "trashSubType", garbage); if (value == null || !areas.add(value)) return null;
                }
            }
        }
        return new CleanState(code, score, clicks, areas);
    }
    private static void purification(Guard g) throws JSONException {
        for (int step = 0; step < 20; step++) {
            CleanState before = cleanState(treeQuery(g, false));
            if (before == null || before.score < 100 || before.clicks <= 0 || before.areas.isEmpty()) return;
            String area = before.areas.iterator().next();
            if (g.attempted(area) || !before.equals(cleanState(treeQuery(g, false)))) return;
            JSONObject ext = MyUtils.newJSONObject().put("clearArea", area).put("clickNum", "1").put("treeCode", before.tree);
            if (!g.begin(area)) return;
            JSONObject ack = g.request(TRIGGER, MyUtils.newJSONObject().put("operationCode", "ZHIMA_TREE_CLEAN_AND_PUSH")
                    .put("playInfo", PLAY).put("refer", REFER).put("extInfo", ext), true);
            CleanState after = cleanState(treeQuery(g, false));
            if (!g.finish(ack, after != null && before.tree.equals(after.tree) && !after.areas.contains(area)
                    && after.score == before.score - 100 && after.clicks == before.clicks - 1)) return;
        }
    }

    private static Map<String, AlchemyTask> alchemyState(Guard g) throws JSONException {
        JSONObject args = MyUtils.newJSONObject().put("chInfo", "").put("deliverStatus", "").put("deliveryTemplateId", "")
                .put("searchSubscribeTask", true).put("supportRewardLJCS", true).put("version", "alchemy");
        JSONObject root = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.queryListV3", args, false);
        JSONObject data = root == null ? null : root.optJSONObject("data"); if (data == null) return null;
        Map<String, AlchemyTask> tasks = new LinkedHashMap<>(); boolean found = false;
        if (data.has("toCompleteVOS")) { found = true; if (!appendAlchemy(tasks, data.optJSONArray("toCompleteVOS"))) return null; }
        JSONObject daily = data.optJSONObject("dailyTaskListVO");
        if (data.has("dailyTaskListVO") && daily == null) return null;
        if (daily != null) for (String key : new String[]{"waitJoinTaskVOS", "waitCompleteTaskVOS"}) if (daily.has(key)) {
            found = true; if (!appendAlchemy(tasks, daily.optJSONArray(key))) return null;
        }
        return found && tasks.size() <= 200 ? tasks : null;
    }
    private static boolean appendAlchemy(Map<String, AlchemyTask> target, JSONArray rows) {
        if (rows == null || rows.length() > 200) return false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) return false;
            String id = text(row, "templateId"), title = text(row, "title"), type = text(row, "bizType");
            Integer current = integer(row, "completedNum"), limit = integer(row, "needCompleteNum");
            Boolean finished = bool(row, "finishFlag"), assist = bool(row, "shareAssist");
            if (row.has("userId") && !UserIdMap.getCurrentUid().equals(text(row, "userId"))) return false;
            if (id == null || id.length() > 256 || title == null || type == null || current == null || limit == null || limit < 1 || limit > 100
                    || row.has("finishFlag") && finished == null || row.has("shareAssist") && assist == null) return false;
            JSONObject log = row.optJSONObject("logExtMap"); String reward = first("", "rewardType", row);
            if ("AD_TASK".equals(type) && (log == null || log.has("bizId") && text(log, "bizId") == null
                    || log.has("spaceCode") && text(log, "spaceCode") == null
                    || log.has("renderConfigKey") && text(log, "renderConfigKey") == null)) return false;
            String biz = first(null, "bizId", log); if (biz == null) biz = first("", "adTaskBizId", row);
            String space = first("", "renderConfigKey", log); if (space.isEmpty()) space = first("", "spaceCode", log);
            String rewardId = first(biz, "adTaskBizId", row);
            int seconds = 15;
            if ("AD_TASK".equals(type)) {
                if ("LJCS".equals(reward)) {
                    String ch = text(log, "ch"), position = text(log, "adPositionId");
                    space = ch == null || position == null ? "" : ch + "_" + position + "_duration=5"; seconds = 5;
                } else {
                    space = decode(space); seconds = space.isEmpty() ? 15 : duration(space);
                }
            }
            String actionUrl = first("", "actionUrl", row);
            String decodedUrl = decode(actionUrl);
            boolean p2eDeclared = row.toString().toLowerCase(Locale.ROOT).contains("p2e");
            boolean channelDeclared = decodedUrl.contains("durationTask") || decodedUrl.toLowerCase(Locale.ROOT).contains("p2e")
                    || decodedUrl.contains("floatingBallTypeList=");
            boolean external = p2eDeclared || channelDeclared || Boolean.TRUE.equals(assist) || unsafeTitle(title) || first("", "templateId", row).contains("dabiaoge")
                    || row.has("gameAppId") || row.has("game_id") || row.has("gameId") || row.has("gameModuleId") || row.has("p2eTask")
                    || title.contains("游戏") || title.contains("玩") || title.contains("浮球");
            String gameAppId = first("", "gameAppId", row);
            if (gameAppId.isEmpty()) gameAppId = first("", "appId", row);
            if (gameAppId.isEmpty()) gameAppId = parameter(first("", "actionUrl", row), "gameAppId");
            if (gameAppId.isEmpty()) gameAppId = parameter(first("", "actionUrl", row), "appId");
            if (gameAppId.isEmpty()) gameAppId = parameter(first("", "actionUrl", row), "game_id");
            external = external || GameTask.matchAppId(gameAppId) != null || row.has("gameTaskType")
                    || row.has("playTime") || row.has("duration") || row.has("needPlayTime")
                    || row.has("timeCount") || row.has("floatBallDuration") || row.has("requiredDuration") || row.has("vstTime")
                    || floatingBall(row) || floatingBall(log);
            boolean gameEligible = !p2eDeclared && !channelDeclared && !Boolean.TRUE.equals(assist) && !unsafeTitle(title) && !title.contains("浮球")
                    && !row.has("gameTaskType") && !row.has("game_id") && !row.has("gameId") && !row.has("gameModuleId") && !row.has("p2eTask")
                    && !row.has("playTime") && !row.has("duration") && !row.has("needPlayTime")
                    && !row.has("timeCount") && !row.has("floatBallDuration") && !row.has("requiredDuration") && !row.has("vstTime")
                    && !floatingBall(row) && !floatingBall(log)
                    && !row.has("jumpToPushModel") && !id.contains("dabiaoge") && !id.equals("zml_zmzl_cyz_erfang");
            if (row.has("gameAppId") && text(row, "gameAppId") == null || row.has("appId") && text(row, "appId") == null
                    || row.has("gameAppId") && row.has("appId") && !Objects.equals(text(row, "gameAppId"), text(row, "appId"))) gameEligible = false;
            AlchemyTask task = new AlchemyTask(id, first("", "recordId", row), title, type, current, limit,
                    Boolean.TRUE.equals(finished), external, reward, rewardId, biz, space, seconds, gameAppId, gameEligible,
                    p2eDeclared || channelDeclared || Boolean.TRUE.equals(assist) || unsafeTitle(title) || title.contains("浮球") || title.contains("悬浮")
                            || id.contains("dabiaoge") || row.has("jumpToPushModel") ? null : gameDuration(row, gameAppId),
                    p2eDeclared || Boolean.TRUE.equals(assist) || unsafeTitle(title) || id.contains("dabiaoge") || row.has("jumpToPushModel")
                            ? null : channelGame(row, actionUrl),
                    !p2eDeclared || Boolean.TRUE.equals(assist) || unsafeTitle(title) || id.contains("dabiaoge") || row.has("jumpToPushModel")
                            ? null : p2eEntry(row));
            AlchemyTask old = target.putIfAbsent(id, task); if (old != null && !old.equals(task)) return false;
        }
        return true;
    }
    private static boolean floatingBall(JSONObject row) {
        if (row == null) return false;
        for (String key : new String[]{"p2eTask", "gameId", "gameModuleId", "floatingBallTypeList", "passThrough", "sceneId", "moduleId", "guideType",
                "p2ePageRequests", "p2eHomePageRequest", "p2eTaskListRequest", "p2eFeedsGameListRequest"})
            if (row.has(key)) return true;
        return false;
    }
    private static GameDuration gameDuration(JSONObject row, String appId) {
        // ponytail: explicit flat duration contracts only; resolve nested page contracts when their execution flow is supported.
        String source = text(row, "source"), kind = text(row, "gameTaskType");
        if (source == null || appId.isEmpty() || row.has("needPlayTime") || floatingBall(row) || floatingBall(row.optJSONObject("logExtMap"))
                || row.has("gameTaskType") && (kind == null || !"shichang".equalsIgnoreCase(kind))
                || !row.has("gameAppId") && !"shichang".equalsIgnoreCase(kind)
                || row.has("gameAppId") && !appId.equals(text(row, "gameAppId"))
                || row.has("appId") && !appId.equals(text(row, "appId"))) return null;
        Integer seconds = null;
        for (String key : new String[]{"playTime", "timeCount", "floatBallDuration", "requiredDuration", "duration", "vstTime"}) {
            if (!row.has(key)) continue;
            Integer value = integer(row, key);
            if (value == null || value <= 0) return null;
            if ("vstTime".equals(key)) value = (int) (((long) value + 999) / 1000 + 1);
            if (value > 300 || seconds != null && !seconds.equals(value)) return null;
            seconds = value;
        }
        return seconds == null ? null : new GameDuration(appId, source, seconds);
    }
    private static Map<String, String> urlParameters(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        try {
            String query = java.net.URI.create(url).getRawQuery();
            if (query == null) return params;
            String[] pairs = query.split("&"); if (pairs.length > 32) return null;
            for (String pair : pairs) {
                int split = pair.indexOf('='); if (split < 1) return null;
                String key = java.net.URLDecoder.decode(pair.substring(0, split), "UTF-8");
                String value = java.net.URLDecoder.decode(pair.substring(split + 1), "UTF-8").trim();
                if (params.putIfAbsent(key, value) != null) return null;
            }
        } catch (Exception malformed) { return null; }
        return params;
    }
    private static ChannelGame channelGame(JSONObject row, String url) {
        Map<String, String> params = urlParameters(url); if (params == null) return null;
        if (!"durationTask".equals(params.get("taskType"))) return null;
        for (String key : new String[]{"p2eTask", "gameId", "gameModuleId", "floatingBallTypeList", "p2ePageRequests",
                "p2eHomePageRequest", "p2eTaskListRequest", "p2eFeedsGameListRequest", "passThrough"})
            if (row.has(key) || row.optJSONObject("logExtMap") != null && row.optJSONObject("logExtMap").has(key)
                    || params.containsKey(key)) return null;
        String scene = channelField(row, params, "sceneId"), task = channelField(row, params, "taskId"),
                source = channelField(row, params, "source"), module = channelField(row, params, "moduleId"),
                guide = channelField(row, params, "guideType");
        if (source == null && !row.has("source") && !params.containsKey("source")) source = channelField(row, params, "chInfo");
        return scene == null || task == null || source == null || module == null || guide == null
                ? null : new ChannelGame(scene, task, source, module, guide);
    }
    private static String channelField(JSONObject row, Map<String, String> params, String key) {
        String direct = text(row, key), fromUrl = params.get(key);
        if (row.has(key) && direct == null || direct != null && direct.length() > 256
                || fromUrl != null && (fromUrl.trim().isEmpty() || fromUrl.length() > 256 || "null".equalsIgnoreCase(fromUrl)
                || fromUrl.startsWith("{") || fromUrl.startsWith("["))
                || direct != null && fromUrl != null && !direct.equals(fromUrl)) return null;
        return direct == null ? fromUrl : direct;
    }
    private static String recommendedGame(Guard g, ChannelGame contract) throws JSONException {
        JSONObject root = g.request("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryRecommendGames",
                MyUtils.newJSONObject().put("__git", "9e159d58cce04c13a").put("sceneId", contract.scene).put("source", contract.source)
                        .put("creativeId", "").put("topGameId", "").put("deviceLevel", "high").put("unityDeviceLevel", "high"), false);
        JSONObject data = root == null ? null : root.optJSONObject("data");
        String chosen = null;
        for (String key : new String[]{"recentPlayGameVO", "todayRecommendVO"}) {
            JSONObject group = data == null ? null : data.optJSONObject(key); if (group == null) continue;
            JSONArray games = group.optJSONArray("gameList"); if (games == null || games.length() > 200) return null;
            for (int i = 0; i < games.length(); i++) {
                JSONObject game = games.optJSONObject(i); String app = text(game, "appId");
                if (app == null || game.has("userId") && !g.uid.equals(text(game, "userId"))) return null;
                if (chosen == null) chosen = app;
            }
        }
        return chosen;
    }
    private static void completeChannelGame(Guard g, AlchemyTask chosen) throws JSONException {
        ChannelGame contract = chosen.channelGame;
        String pass = MyUtils.newJSONObject().put("sceneId", contract.scene).put("taskId", contract.task).toString();
        JSONObject feedback = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
                MyUtils.newJSONObject().put("actionType", "TO_COMPLETE").put("bizType", "LIFE_RECORD").put("sceneCode", "alchemy")
                        .put("templateId", chosen.id).put("version", "alchemy"), true);
        if (feedback == null || g.request("com.alipay.gamecenteruprod.biz.rpc.external.gamecenter.queryHomePage",
                MyUtils.newJSONObject().put("__git", "9e159d58cce04c13a").put("channelTaskPassThrough", pass)
                        .put("sceneId", contract.scene).put("moduleId", contract.module).put("guideType", contract.guide)
                        .put("source", contract.source).put("deviceLevel", "high").put("unityDeviceLevel", "high"), false) == null) return;
        String app = recommendedGame(g, contract); if (app == null) return;
        JSONObject consultArgs = MyUtils.newJSONObject().put("__git", "9e159d58cce04c13a").put("passThrough", pass).put("source", contract.source);
        JSONObject consult = g.request("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.consult", consultArgs, false);
        Integer seconds = channelSeconds(consult); if (seconds == null) return;
        g.waitSeconds(seconds + 1);
        Map<String, AlchemyTask> fresh = alchemyState(g);
        if (fresh == null || !chosen.equals(fresh.get(chosen.id)) || !app.equals(recommendedGame(g, contract))
                || !seconds.equals(channelSeconds(g.request("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.consult", consultArgs, false)))) return;
        JSONObject durationAck = g.request("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
                MyUtils.newJSONObject().put("gameAppId", app).put("source", contract.source).put("playTime", seconds + 1).put("statisticTag", ""), true);
        if (durationAck == null) return;
        Map<String, AlchemyTask> afterDuration = alchemyState(g);
        if (alchemyProgress(chosen, afterDuration)) { g.finish(durationAck, true); return; }
        if (afterDuration == null || !chosen.equals(afterDuration.get(chosen.id))) return;
        JSONObject ack = g.request("com.alipay.gamecenteruprod.biz.rpc.channeltask.floatingball.complete",
                MyUtils.newJSONObject().put("passThrough", pass).put("sceneId", contract.scene), true);
        g.finish(ack, alchemyProgress(chosen, alchemyState(g)));
    }
    private static Integer channelSeconds(JSONObject consult) {
        JSONObject data = consult == null ? null : consult.optJSONObject("data"); Integer seconds = null;
        for (JSONObject object : new JSONObject[]{consult, data, data == null ? null : data.optJSONObject("floatingBallVO")}) {
            if (object == null || !object.has("timeSeconds")) continue;
            Integer value = integer(object, "timeSeconds");
            if (value == null || value < 1 || value > 299 || seconds != null && !seconds.equals(value)) return null;
            seconds = value;
        }
        return seconds;
    }
    private static String canonical(Object value, int depth) throws JSONException {
        if (depth > 12) return null;
        if (value == JSONObject.NULL || value == null) return "null";
        if (value instanceof String) return JSONObject.quote((String) value);
        if (value instanceof Boolean) return value.toString();
        if (value instanceof Number) return Double.isFinite(((Number) value).doubleValue()) ? JSONObject.numberToString((Number) value) : null;
        java.util.StringJoiner parts = new java.util.StringJoiner(",");
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value; if (object.length() > 128) return null;
            java.util.TreeSet<String> keys = new java.util.TreeSet<>(); object.keys().forEachRemaining(keys::add);
            for (String key : keys) {
                String item = canonical(object.opt(key), depth + 1); if (item == null) return null;
                parts.add(JSONObject.quote(key) + ":" + item); if (parts.length() > 16384) return null;
            }
            return "{" + parts + "}";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value; if (array.length() > 200) return null;
            for (int i = 0; i < array.length(); i++) {
                String item = canonical(array.opt(i), depth + 1); if (item == null) return null;
                parts.add(item); if (parts.length() > 16384) return null;
            }
            return "[" + parts + "]";
        }
        return null;
    }
    private static List<JSONObject> p2eObjects(JSONObject row) throws JSONException {
        List<JSONObject> objects = new java.util.ArrayList<>(); objects.add(row);
        for (int i = 0; i < objects.size(); i++) {
            JSONObject object = objects.get(i);
            if (objects.size() > 32 || object.has("userId") && !UserIdMap.getCurrentUid().equals(text(object, "userId"))) return null;
            for (String key : new String[]{"extend", "prodPlayParam", "taskDisplayConfig", "floatBallConfig", "task", "taskInfo",
                    "taskBaseInfo", "bizInfo", "taskCategorization", "categorizationParamModel", "p2ePageRequests", "p2ePageSession", "logExtMap"}) {
                if (!object.has(key)) continue;
                JSONObject nested = objectValue(object.opt(key)); if (nested == null) return null;
                if (!objects.contains(nested)) objects.add(nested);
            }
            for (String key : new String[]{"targetUrl", "actionUrl", "jumpUrl", "jumpLink", "pageUrl", "taskJumpUrl", "url", "sourceUrl", "schema", "query", "channelTaskPassThrough"}) {
                if (!object.has(key)) continue;
                JSONObject nested = objectValue(object.opt(key));
                if (nested != null) { if (!objects.contains(nested)) objects.add(nested); continue; }
                String url = text(object, key); if (url == null) return null;
                if ("query".equals(key) && url.contains("=") && !url.startsWith("?")) url = "?" + url;
                Map<String, String> params = urlParameters(url); if (params == null) return null;
                JSONObject parsed = MyUtils.newJSONObject();
                for (Map.Entry<String, String> parameter : params.entrySet()) parsed.put(parameter.getKey(), parameter.getValue());
                if (parsed.length() > 0) objects.add(parsed);
            }
        }
        return objects;
    }
    private static String p2eField(List<JSONObject> objects, String... keys) {
        String result = "";
        for (String key : keys) for (JSONObject object : objects) {
            if (!object.has(key)) continue;
            if (object.opt(key) instanceof String && ((String) object.opt(key)).trim().isEmpty()) continue;
            String value = text(object, key);
            if (value == null || value.length() > 256 || !result.isEmpty() && !result.equals(value)) return null;
            result = value;
        }
        return result;
    }
    private static List<String> p2eTypes(Object raw) {
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.startsWith("[")) { try { raw = new JSONArray(value); } catch (JSONException malformed) { return null; } }
            else raw = new JSONArray(java.util.Arrays.asList(value.split("[,|]")));
        }
        if (!(raw instanceof JSONArray)) return null;
        JSONArray array = (JSONArray) raw; if (array.length() < 1 || array.length() > 8) return null;
        List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i); if (!(item instanceof String)) return null;
            String value = ((String) item).trim();
            if (value.isEmpty() || value.length() > 128 || result.contains(value)) return null;
            result.add(value);
        }
        return result.contains("P2E_GAME_BROWSE_TASK_FLOATING_BALL") ? List.copyOf(result) : null;
    }
    private static P2eEntry p2eEntry(JSONObject row) {
        try {
            List<JSONObject> objects = p2eObjects(row); if (objects == null) return null;
            Map<String, String> fields = new LinkedHashMap<>();
            for (String key : new String[]{"sceneId", "taskId", "moduleId", "guideType", "source", "oriChInfo", "trafficDriverId",
                    "componentChannel", "componentScene", "gameAppId", "gameId", "gameModuleId", "gameVersion"}) {
                String value = switch (key) {
                    case "source", "oriChInfo" -> {
                        String direct = p2eField(objects, key);
                        yield "".equals(direct) ? p2eField(objects, "chInfo") : direct;
                    }
                    case "componentChannel" -> p2eField(objects, key, "p2eComponentChannel", "channel");
                    case "componentScene" -> p2eField(objects, key, "p2eComponentScene", "gameScene");
                    case "gameAppId" -> p2eField(objects, key, "appId");
                    default -> p2eField(objects, key);
                };
                if (value == null || value.isEmpty() && !key.startsWith("game")) return null;
                fields.put(key, value);
            }
            List<String> types = null;
            for (JSONObject object : objects) if (object.has("floatingBallTypeList")) {
                List<String> current = p2eTypes(object.opt("floatingBallTypeList"));
                if (current == null || types != null && !types.equals(current)) return null;
                types = current;
            }
            if (types == null) return null;
            List<String> requests = new java.util.ArrayList<>();
            for (String kind : new String[]{"HomePage", "TaskList", "FeedsGameList"}) {
                String captured = null;
                for (JSONObject object : objects) for (String key : new String[]{"p2e" + kind + "Request", "p2eQuery" + kind + "Request", "query" + kind + "Request"}) {
                    if (!object.has(key)) continue;
                    JSONObject request = objectValue(object.opt(key));
                    if (request == null || !fields.get("source").equals(text(request, "source"))
                            || request.has("userId") && !UserIdMap.getCurrentUid().equals(text(request, "userId"))) return null;
                    String raw = canonical(request, 0);
                    if (raw == null || captured != null && !captured.equals(raw)) return null;
                    captured = raw;
                }
                if (captured == null) return null;
                requests.add(captured);
            }
            return new P2eEntry(Map.copyOf(fields), types, List.copyOf(requests));
        } catch (JSONException malformed) { return null; }
    }
    private static boolean p2eCandidates(Object value, Map<String, String> entrance, Set<Map<String, String>> candidates,
                                         String uid, int depth, int[] count) {
        if (depth > 12 || ++count[0] > 2048) return false;
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            if (object.has("userId") && !uid.equals(text(object, "userId"))) return false;
            if ((object.has("gameAppId") || object.has("appId") || object.has("game_id")) && (object.has("gameId") || object.has("gid"))) {
                List<JSONObject> one = List.of(object); Map<String, String> game = new LinkedHashMap<>();
                game.put("gameAppId", p2eField(one, "gameAppId", "appId", "game_id"));
                game.put("gameId", p2eField(one, "gameId", "gid"));
                game.put("gameModuleId", p2eField(one, "gameModuleId", "moduleId"));
                game.put("gameVersion", p2eField(one, "gameVersion", "gameVer", "version"));
                if (game.values().stream().anyMatch(Objects::isNull) || game.get("gameAppId").isEmpty() || game.get("gameId").isEmpty()) return false;
                // An explicit entry may supply module/version omitted by page metadata; never invent them.
                for (String key : new String[]{"gameModuleId", "gameVersion"}) if (game.get(key).isEmpty()) game.put(key, entrance.get(key));
                boolean matches = true;
                for (String key : game.keySet()) if (!entrance.get(key).isEmpty() && !entrance.get(key).equals(game.get(key))) matches = false;
                if (matches && game.values().stream().noneMatch(String::isEmpty)) candidates.add(Map.copyOf(game));
            }
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) if (!p2eCandidates(object.opt(keys.next()), entrance, candidates, uid, depth + 1, count)) return false;
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) if (!p2eCandidates(array.opt(i), entrance, candidates, uid, depth + 1, count)) return false;
        }
        return true;
    }
    private static Map<String, String> p2eSession(Guard g, P2eEntry entry) {
        Set<Map<String, String>> candidates = new LinkedHashSet<>(); int[] count = {0};
        String[] methods = {"queryHomePage", "queryTaskList", "queryFeedsGameList"};
        for (int i = 0; i < methods.length; i++) {
            JSONObject response = g.request("com.alipay.gamecenteruprod.biz.rpc.p2e." + methods[i], MyUtils.newJSONObject(entry.requests.get(i)), false);
            if (response == null || !p2eCandidates(response, entry.fields, candidates, g.uid, 0, count)) return null;
        }
        if (candidates.size() != 1) return null;
        Map<String, String> fields = new LinkedHashMap<>(entry.fields); fields.putAll(candidates.iterator().next());
        return Map.copyOf(fields);
    }
    private static JSONObject p2eRequest(Guard g, String method, JSONObject args, boolean write) {
        JSONObject result = g.request(method, args, write);
        return result != null && Boolean.TRUE.equals(bool(result, "success")) ? result : null;
    }
    private static Integer p2eSeconds(JSONObject consult, List<String> types) {
        JSONObject data = consult == null ? null : consult.optJSONObject("data");
        for (JSONObject object : new JSONObject[]{consult, data, data == null ? null : data.optJSONObject("floatingBallVO")}) {
            if (object != null && object.has("floatingBallTypeList") && !types.equals(p2eTypes(object.opt("floatingBallTypeList")))) return null;
        }
        return channelSeconds(consult);
    }
    private static void completeP2eGame(Guard g, AlchemyTask chosen, Map<String, String> game) throws JSONException {
        String app = game.get("gameAppId"), source = game.get("source");
        JSONObject feedback = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
                MyUtils.newJSONObject().put("actionType", "TO_COMPLETE").put("bizType", "LIFE_RECORD").put("sceneCode", "alchemy")
                        .put("templateId", chosen.id).put("version", "alchemy"), true);
        if (feedback == null) return;
        JSONObject attributes = MyUtils.newJSONObject().put("ALIVE_ENTER", "0").put("CH_INFO", source).put("CPS_ID", "unknown")
                .put("GAME_VERSION", game.get("gameVersion")).put("PALADINX_VERSION", "2.2.5").put("PLAY_SCENE", "NORMAL").put("SCENE_ID", game.get("componentChannel"));
        String eventMethod = "com.alipay.gameevent.biz.rpc.submitEvent";
        if (g.request(eventMethod, MyUtils.newJSONObject().put("appId", app).put("eventAttrMap", attributes).put("eventId", "GAME_FIRST_FRAME")
                .put("idempotentNo", "platform_" + System.currentTimeMillis() + "_" + app + "_first_frame").put("source", source), true) == null) return;
        if (g.request("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserAction", MyUtils.newJSONObject().put("actionCode", "enterGame")
                .put("gameId", app).put("paladinxVersion", "2.2.5").put("source", "gameFramework"), true) == null) return;
        if (g.request("com.alipay.gamecenteruprod.biz.rpc.facade.assistant.GameAssistantRpc.consult", MyUtils.newJSONObject().put("appId", app)
                .put("assistantVersion", "3.0.0").put("deviceLevel", "high").put("sceneCode", "GAME_MSG").put("source", source), false) == null) return;
        if (g.request("com.alipay.gamecenteruprod.biz.rpc.consultGameComponent", MyUtils.newJSONObject().put("appId", app)
                .put("channel", game.get("componentChannel")).put("scene", game.get("componentScene")).put("source", source), false) == null) return;
        for (boolean head : new boolean[]{false, true}) {
            JSONObject args = MyUtils.newJSONObject().put("appId", app).put("chInfo", source).put("channel", game.get("componentChannel"))
                    .put("closeAd", false).put("closeCntMap", MyUtils.newJSONObject()).put("dayAdViewCnt", 0).put("deviceLevel", "high")
                    .put("monthAdViewCnt", 0).put("notUnityDeviceLevel", "high").put("reqType", head ? "" : "START_APP_FIRST_REQ")
                    .put("scene", game.get("componentScene")).put("setHead", head).put("sourceTab", "buoy").put("unityDeviceLevel", "high").put("virtualActivity", false);
            if (!head) args.put("panelLaunchableCheckMap", MyUtils.newJSONObject().put("SET_HEAD_TASK", true).put("SUBSCRIBE_TASK", false).put("THIRD_PARTY_GAME_ADD_DESKTOP", true));
            if (g.request("com.alipay.gamecenteruprod.biz.rpc.queryGameComponent", args, false) == null) return;
        }
        JSONObject consultArgs = MyUtils.newJSONObject().put("__git", "9e159d58cce04c13a").put("gameId", game.get("gameId"))
                .put("gameModuleId", game.get("gameModuleId")).put("source", source).put("trafficDriverId", game.get("trafficDriverId"));
        JSONObject consult = p2eRequest(g, "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult", consultArgs, false);
        List<String> types = chosen.p2eEntry.types;
        Integer seconds = p2eSeconds(consult, types); if (seconds == null) return;
        if (g.request(eventMethod, MyUtils.newJSONObject().put("appId", app).put("eventAttrMap", attributes).put("eventId", "loading_completed")
                .put("idempotentNo", "platform_" + System.currentTimeMillis() + "_" + app + "_1").put("source", source), true) == null) return;
        int remaining = seconds + 1, chunkLimit = 31;
        while (remaining > 0) {
            int chunk = Math.min(chunkLimit, remaining); g.waitSeconds(chunk);
            Map<String, AlchemyTask> fresh = alchemyState(g);
            if (fresh == null || !chosen.equals(fresh.get(chosen.id)) || !game.equals(p2eSession(g, chosen.p2eEntry))) return;
            JSONObject checked = p2eRequest(g, "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallConsult", consultArgs, false);
            if (!seconds.equals(p2eSeconds(checked, types))) return;
            if (p2eRequest(g, "com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
                    MyUtils.newJSONObject().put("gameAppId", app).put("playTime", chunk).put("source", source).put("statisticTag", ""), true) == null) return;
            remaining -= chunk; chunkLimit = 30;
        }
        Map<String, AlchemyTask> fresh = alchemyState(g);
        if (fresh == null || !chosen.equals(fresh.get(chosen.id)) || !game.equals(p2eSession(g, chosen.p2eEntry))) return;
        JSONObject ack = p2eRequest(g, "com.alipay.gamecenteruprod.biz.rpc.p2e.gameP2eFloatingBallComplete",
                MyUtils.newJSONObject().put("floatingBallTypeList", new JSONArray(types)).put("gameId", game.get("gameId"))
                        .put("gameModuleId", game.get("gameModuleId")).put("oriChInfo", game.get("oriChInfo"))
                        .put("source", source).put("trafficDriverId", game.get("trafficDriverId")), true);
        g.finish(ack, alchemyProgress(chosen, alchemyState(g)));
    }
    private static void alchemyTasks(Guard g) throws JSONException {
        for (int step = 0; step < 40; step++) {
            Map<String, AlchemyTask> tasks = alchemyState(g); if (tasks == null) return;
            AlchemyTask chosen = null;
            for (AlchemyTask t : tasks.values()) if (!t.done() && (!t.external || t.mappedGame() || t.durationGame() || t.channelGameTask() || t.p2eGameTask()) && !g.attempted(t.id + "::complete")
                    && (t.browse() || t.freeRent() || t.directAd() || t.mappedGame() || t.durationGame() || t.channelGameTask() || t.p2eGameTask()
                    || "AD_TASK".equals(t.type) && !t.bizId.isEmpty() && !t.space.isEmpty() && t.seconds >= 1 && t.seconds <= 300)) {
                chosen = t; break;
            }
            if (chosen == null) return;
            Map<String, AlchemyTask> fresh = alchemyState(g); if (fresh == null || !chosen.equals(fresh.get(chosen.id))) return;
            if ("AD_TASK".equals(chosen.type)) {
                if (!g.begin(chosen.id + "::complete")) return;
                if ("LJCS".equals(chosen.rewardType) && g.request(CREDIT + "promise.PromiseRpcManager.adRewardLjcs",
                        MyUtils.newJSONObject().put("adTaskBizId", chosen.rewardId), true) == null) return;
                if (!chosen.directAd() && g.request("com.alipay.adtask.biz.mobilegw.service.applayer.query", MyUtils.newJSONObject().put("spaceCode", chosen.space), false) == null) return;
                g.waitSeconds(chosen.seconds);
                Map<String, AlchemyTask> beforeFinish = alchemyState(g);
                if (beforeFinish == null || !chosen.equals(beforeFinish.get(chosen.id))) return;
                JSONObject finishArgs = MyUtils.newJSONObject().put("bizId", chosen.bizId);
                if (!chosen.directAd()) finishArgs.put("extendInfo", MyUtils.newJSONObject());
                JSONObject ack = g.request("com.alipay.adtask.biz.mobilegw.service.task.finish", finishArgs, true);
                Map<String, AlchemyTask> after = alchemyState(g);
                if (!g.finish(ack, alchemyProgress(chosen, after))) return;
                continue;
            }
            String record = chosen.record;
            if (record.isEmpty()) {
                if (!g.begin(chosen.id + "::join")) return;
                JSONObject joined = g.request(CREDIT + "promise.PromiseRpcManager.joinActivity", MyUtils.newJSONObject().put("chInfo", "seasameList")
                        .put("joinFromOuter", false).put("sceneCode", "alchemy").put("templateId", chosen.id), true);
                JSONObject data = joined == null ? null : joined.optJSONObject("data"); record = text(data, "recordId");
                Map<String, AlchemyTask> after = alchemyState(g); AlchemyTask joinedTask = after == null ? null : after.get(chosen.id);
                if (!g.finish(joined, record != null && joinedTask != null && sameAlchemy(chosen, joinedTask) && record.equals(joinedTask.record))) return;
                chosen = joinedTask;
            }
            if (chosen.p2eGameTask()) {
                Map<String, String> game = p2eSession(g, chosen.p2eEntry);
                Map<String, AlchemyTask> current = alchemyState(g);
                if (game == null || current == null || !chosen.equals(current.get(chosen.id))) return;
                if (!g.begin(chosen.id + "::complete")) return;
                completeP2eGame(g, chosen, game);
                if (g.blocked()) return;
                continue;
            }
            if (!g.begin(chosen.id + "::complete")) return;
            if (chosen.channelGameTask()) {
                completeChannelGame(g, chosen);
                if (g.blocked()) return;
                continue;
            }
            if (chosen.durationGame()) {
                GameDuration contract = chosen.gameDuration;
                g.waitSeconds(contract.seconds);
                Map<String, AlchemyTask> beforeDuration = alchemyState(g);
                if (beforeDuration == null || !chosen.equals(beforeDuration.get(chosen.id))) return;
                JSONObject durationAck = g.request("com.alipay.gamecenteruprod.biz.rpc.v3.submitUserPlayDurationAction",
                        MyUtils.newJSONObject().put("gameAppId", contract.appId).put("source", contract.source)
                                .put("playTime", contract.seconds).put("statisticTag", ""), true);
                if (durationAck == null) return;
                Map<String, AlchemyTask> afterDuration = alchemyState(g);
                if (alchemyProgress(chosen, afterDuration)) {
                    if (!g.finish(durationAck, true)) return;
                    continue;
                }
                if (afterDuration == null || !chosen.equals(afterDuration.get(chosen.id))) return;
            }
            if (chosen.mappedGame()) {
                GameTask game = GameTask.matchAppId(chosen.gameAppId);
                g.check();
                int submitted = game.reportSync("芝麻炼金:" + chosen.title, 1);
                g.check();
                if (submitted <= 0) return;
                Map<String, AlchemyTask> afterGame = alchemyState(g);
                if (alchemyProgress(chosen, afterGame)) {
                    g.finish(submitted > 0, true);
                    continue;
                }
                if (afterGame == null || !chosen.equals(afterGame.get(chosen.id))) return;
            }
            JSONObject feedback = g.request(CREDIT + "creditaccumulate.CreditAccumulateStrategyRpcManager.taskFeedback",
                    MyUtils.newJSONObject().put("actionType", "TO_COMPLETE").put("bizType", "LIFE_RECORD").put("sceneCode", "alchemy")
                            .put("templateId", chosen.id).put("version", "alchemy"), true);
            if (feedback == null) return;
            g.waitSeconds(15); // Sen-new processAlchemyTasks browser branch; never fabricate elapsed time.
            Map<String, AlchemyTask> beforePush = alchemyState(g); AlchemyTask same = beforePush == null ? null : beforePush.get(chosen.id);
            if (same == null || !chosen.equals(same) || same.done()) return;
            if (chosen.freeRent()) {
                JSONObject rentAck = g.request("com.alipay.creditapollon.biz.rpc.api.rent.RentRpcService.actionSubmit",
                        MyUtils.newJSONObject().put("chInfo", "chInfo=ch_zmzltf__chsub_zmlrw").put("type", "actionSubmit"), true);
                if (rentAck == null) return;
                Map<String, AlchemyTask> afterAction = alchemyState(g);
                if (afterAction == null || !chosen.equals(afterAction.get(chosen.id))) return;
            }
            JSONObject ack = g.request(CREDIT + "promise.PromiseRpcManager.pushActivity", MyUtils.newJSONObject().put("recordId", record), true);
            Map<String, AlchemyTask> after = alchemyState(g);
            if (!g.finish(ack, alchemyProgress(chosen, after))) return;
        }
    }
    private static boolean sameAlchemy(AlchemyTask a, AlchemyTask b) {
        return a.id.equals(b.id) && a.type.equals(b.type) && a.title.equals(b.title) && a.limit == b.limit && a.external == b.external
                && a.rewardType.equals(b.rewardType) && a.rewardId.equals(b.rewardId) && a.bizId.equals(b.bizId) && a.space.equals(b.space)
                && a.gameAppId.equals(b.gameAppId) && a.gameEligible == b.gameEligible && Objects.equals(a.gameDuration, b.gameDuration)
                && Objects.equals(a.channelGame, b.channelGame) && Objects.equals(a.p2eEntry, b.p2eEntry);
    }
    private static boolean alchemyProgress(AlchemyTask before, Map<String, AlchemyTask> after) {
        AlchemyTask task = after == null ? null : after.get(before.id);
        return task != null && sameAlchemy(before, task) && task.record.equals(before.record)
                && (task.current > before.current || !before.finished && task.finished);
    }

    private static JSONObject timeTask(Guard g) {
        JSONObject root = g.request(CREDIT + "pointtask.TimeLimitedTaskRpcManager.queryTask", MyUtils.newJSONObject(), false);
        JSONObject data = root == null ? null : root.optJSONObject("data");
        return data == null ? null : data.optJSONObject("timeLimitedTaskVO");
    }
    private static void timeReward(Guard g) throws JSONException {
        JSONObject before = timeTask(g); String id = text(before, "templateId"); Integer state = integer(before, "state");
        Boolean tomorrow = bool(before, "tomorrow"); Integer expected = integer(before, "rewardAmount");
        if (id == null || state == null || state != 1 || tomorrow == null || tomorrow || expected == null || expected <= 0 || g.attempted(id)) return;
        JSONObject fresh = timeTask(g);
        if (fresh == null || !id.equals(text(fresh, "templateId")) || !Objects.equals(state, integer(fresh, "state"))
                || !expected.equals(integer(fresh, "rewardAmount")) || !Boolean.FALSE.equals(bool(fresh, "tomorrow"))) return;
        if (!g.begin(id)) return;
        JSONObject ack = g.request(CREDIT + "pointtask.TimeLimitedTaskRpcManager.completeTask", MyUtils.newJSONObject().put("templateId", id), true);
        JSONObject after = timeTask(g); Integer newState = integer(after, "state");
        JSONObject reward = ack == null ? null : ack.optJSONObject("data"); Integer gained = integer(reward, "zmlNum");
        g.finish(ack, gained != null && gained > 0 && expected != null && gained.equals(expected)
                && after != null && id.equals(text(after, "templateId")) && newState != null && newState == 2
                && Boolean.FALSE.equals(bool(after, "tomorrow")));
    }
    private static JSONObject nextDayTask(Guard g) throws JSONException {
        JSONObject root = g.request(CREDIT + "AlchemyRpcManager.queryEntryList", MyUtils.newJSONObject().put("version", "2025-10-22"), false);
        JSONObject data = root == null ? null : root.optJSONObject("data"); JSONArray entries = data == null ? null : data.optJSONArray("entryList");
        if (entries == null || entries.length() > 100) return null;
        JSONObject result = null;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null) return null;
            if ("ALCHEMY_STAGE_REWARD".equals(text(entry, "entryCode"))) {
                if (result != null) return null; result = entry.optJSONObject("nextDayAwardDTO"); if (result == null) return null;
            }
        }
        return result;
    }
    private static void nextDayReward(Guard g) throws JSONException {
        JSONObject before = nextDayTask(g); String id = text(before, "awardId");
        if (id == null || !Boolean.TRUE.equals(bool(before, "awardAvailable")) || g.attempted(id)) return;
        JSONObject fresh = nextDayTask(g);
        if (fresh == null || !id.equals(text(fresh, "awardId")) || !Boolean.TRUE.equals(bool(fresh, "awardAvailable"))) return;
        if (!g.begin(id)) return;
        JSONObject ack = g.request(CREDIT + "AlchemyRpcManager.claimAward", MyUtils.newJSONObject().put("awardId", id), true);
        JSONObject after = nextDayTask(g);
        g.finish(ack, after != null && id.equals(text(after, "awardId")) && Boolean.FALSE.equals(bool(after, "awardAvailable")));
    }
}
