package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.IdAndName;
import io.github.aw1y2z.sesame.entity.OtherEntity;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class ForestExpiringProps {
    private static final String RECEIPT = "forestExpiringPropReceipt", ATTEMPTS = "forestExpiringPropAttempts";
    private static final long HOUR = 3600000L;

    static List<IdAndName> getOptions() {
        return List.of(new OtherEntity("shield", "临期保护罩"), new OtherEntity("stealthCard", "临期限时隐身卡"),
                new OtherEntity("energyBombCard", "临期炸弹卡（无罩且未生效时）"), new OtherEntity("robExpandCard", "临期N倍卡（保留现有效果）"),
                new OtherEntity("doubleClick", "临期31天双击卡"), new OtherEntity("boost", "临期加速器（05:00-07:10且有待成熟球）"));
    }

    static boolean hasUnconfirmed(String group) {
        String raw = RuntimeInfo.getInstance().getString(RECEIPT);
        if (raw.isEmpty()) return false;
        Object saved = MyUtils.newJSONObject(raw).opt("group");
        return !(saved instanceof String) || !Set.of("shield", "stealthCard", "energyBombCard", "robExpandCard", "doubleClick", "boost").contains(saved)
                || group == null || group.equals(saved);
    }

    /** Caller holds the existing prop lock and refreshes own home after any confirmed consumption. */
    static int consume(boolean enabled, Set<String> categories, int cutoffHours, int dailyLimit) {
        if (!enabled || categories == null || categories.isEmpty() || cutoffHours <= 0 || cutoffHours > 24 || dailyLimit <= 0) return 0;
        String uid = UserIdMap.getCurrentUid();
        int day = day(), done = 0, limit = Math.min(dailyLimit, 10);
        if (uid == null || uid.isEmpty()) return 0;
        try {
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            if (!runtime.getString(RECEIPT).isEmpty()) return 0;
            Set<String> available = new HashSet<>(categories);
            // ponytail: fresh inventory/effects after every card; at most ten daily attempts, no stale batch mutation.
            for (int i = 0; i < limit; i++) {
                TimeUtil.sleep(0);
                if (!current(uid, day)) break;
                JSONArray bag = inventory();
                Map<String, JSONObject> effects = effects();
                if (bag == null || effects == null) break;
                long now = System.currentTimeMillis();
                JSONObject card = choose(bag, effects, available, cutoffHours * HOUR, now);
                if (card == null) break;
                String group = card.optString("propGroup"), type = card.optString("propType"), id = card.optJSONArray("propIdList").optString(0);
                long before = stock(bag, group, type), end = end(effects, group);
                Map<Long, Long> waiting = null;
                JSONObject beforeHome = null;
                if ("boost".equals(group)) {
                    beforeHome = home();
                    waiting = waiting(beforeHome);
                    if (waiting == null) break;
                    if (waiting.isEmpty()) { available.remove("boost"); i--; continue; }
                }
                if (!eligible(card, effects, cutoffHours * HOUR, System.currentTimeMillis())) break;
                if (!reserve(runtime, group, type, id, before, end, uid, day, limit)) break;
                boolean confirmed;
                JSONObject ack = payload(AntForestRpcCall.consumeProp(group, id, type, false));
                String status = ack == null ? "" : ack.optString("usePropStatus");
                if (status.startsWith("NEED_CONFIRM") || "REPLACE".equals(status)) {
                    JSONArray freshBag = inventory();
                    Map<String, JSONObject> freshEffects = effects();
                    if (end == 0 || "boost".equals(group) || "energyBombCard".equals(group) || freshEffects == null
                            || end(freshEffects, group) != end || stock(freshBag, group, type) != before || !has(freshBag, group, type, id)
                            || !eligible(card, freshEffects, cutoffHours * HOUR, System.currentTimeMillis())
                            || !sameFactor(effects.get(group), freshEffects.get(group))) break;
                    TimeUtil.sleep(0);
                    if (!current(uid, day)) break;
                    ack = payload(AntForestRpcCall.consumeProp(group, id, type, true));
                }
                JSONArray afterBag = inventory();
                Map<String, JSONObject> afterEffects = effects();
                confirmed = ack != null && afterEffects != null && stock(afterBag, group, type) == before - 1 && !has(afterBag, group, type, id);
                if ("boost".equals(group)) confirmed &= advanced(waiting, home(), number(beforeHome, "now"));
                else confirmed &= afterEffects != null && end(afterEffects, group) > Math.max(end, System.currentTimeMillis());
                if ("robExpandCard".equals(group)) confirmed &= afterEffects != null
                        && Math.abs(factor(afterEffects.get(group)) - factor(card.optJSONObject("propConfigVO"))) < 0.0001;
                TimeUtil.sleep(0);
                if (!confirmed || !current(uid, day) || !runtime.putVerified(RECEIPT, null)) break;
                done++;
                Log.forest("临期道具🍈库存及实际效果回查成功：" + group);
                TimeUtil.sleep(1500);
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("ForestExpiringProps", "临期道具消耗失败", e); }
        return done;
    }

    private static boolean reserve(RuntimeInfo runtime, String group, String type, String id, long stock, long end, String uid, int day, int limit) throws Exception {
        TimeUtil.sleep(0);
        String raw = runtime.getString(ATTEMPTS);
        JSONObject ledger = raw.isEmpty() ? MyUtils.newJSONObject() : MyUtils.newJSONObject(raw);
        long savedDay = raw.isEmpty() ? 0 : number(ledger, "day"), used = raw.isEmpty() ? 0 : number(ledger, "used");
        int status = Status.getIntFlagToday("forest::expiringPropAttempts");
        if (savedDay < 0 || savedDay > day || used < 0 || status < 0) return false;
        if (savedDay != day) used = 0;
        used = Math.max(used, status);
        String flag = "forest::expiringProp::" + id;
        if (used >= limit || Status.hasFlagToday(flag) || !runtime.getString(RECEIPT).isEmpty() || !current(uid, day)) return false;
        if (!runtime.putVerified(ATTEMPTS, MyUtils.newJSONObject().put("day", day).put("used", used + 1).toString())) return false;
        if (!current(uid, day) || !runtime.putVerified(RECEIPT, MyUtils.newJSONObject().put("group", group).put("type", type)
                .put("id", id).put("stock", stock).put("end", end).put("day", day).toString())) return false;
        TimeUtil.sleep(0);
        if (!current(uid, day)) return false;
        Status.flagToday(flag);
        Status.setIntFlagToday("forest::expiringPropAttempts", (int) used + 1);
        if ("boost".equals(group)) Status.flagToday("forest::boostAttempt::" + id);
        if ("robExpandCard".equals(group)) Status.flagToday("forest::robExpandCardAttempt::" + id);
        if ("doubleClick".equals(group)) {
            Status.flagToday("forest::smartDoubleCard::" + id);
            Status.setIntFlagToday("forest::smartDoubleAttempts", Status.getIntFlagToday("forest::smartDoubleAttempts") + 1);
        }
        return true;
    }

    private static JSONObject choose(JSONArray bag, Map<String, JSONObject> effects, Set<String> groups, long cutoff, long now) {
        JSONObject best = null;
        long expiry = Long.MAX_VALUE;
        for (int i = 0; i < bag.length(); i++) {
            JSONObject row = bag.optJSONObject(i);
            if (!groups.contains(row.optString("propGroup")) || !eligible(row, effects, cutoff, now)) continue;
            String id = row.optJSONArray("propIdList").optString(0);
            if (Status.hasFlagToday("forest::expiringProp::" + id) || Status.hasFlagToday("forest::boostAttempt::" + id)
                    || Status.hasFlagToday("forest::robExpandCardAttempt::" + id) || Status.hasFlagToday("forest::smartDoubleCard::" + id)) continue;
            long at = number(row, "recentExpireTime");
            if (at < expiry) { best = row; expiry = at; }
        }
        return best;
    }

    private static boolean eligible(JSONObject card, Map<String, JSONObject> effects, long cutoff, long now) {
        String group = card.optString("propGroup"), type = card.optString("propType");
        long expiry = number(card, "recentExpireTime"), end = end(effects, group);
        if (expiry <= now + 10000 || expiry - now > cutoff || number(card, "holdsNum") <= 0) return false;
        JSONObject config = card.optJSONObject("propConfigVO");
        long duration = number(config, "durationTime");
        if ("doubleClick".equals(group)) return "ENERGY_DOUBLE_CLICK_31DAYS".equals(type)
                && RuntimeInfo.getInstance().getString("forestPropDoubleReceipt").isEmpty()
                && ForestPropSupport.doubleRenewAllowed(end, true, now);
        if ("shield".equals(group)) {
            if (!Set.of("LIMIT_TIME_ENERGY_SHIELD", "LIMIT_TIME_ENERGY_SHIELD_TREE").contains(type)
                    || end - now > 7 * 24 * HOUR || end(effects, "energyBombCard") > 0) return false;
        } else if ("stealthCard".equals(group)) {
            if (!"LIMIT_TIME_STEALTH_CARD".equals(type)) return false;
        } else if ("energyBombCard".equals(group)) {
            return "ENERGY_BOMB_CARD".equals(type) && end == 0 && end(effects, "shield") == 0;
        } else if ("robExpandCard".equals(group)) {
            double expected = "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS".equals(type) ? 1.5 : "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS".equals(type) ? 1.1 : 0;
            if (expected == 0 || Math.abs(factor(config) - expected) > 0.0001 || end > now && factor(effects.get(group)) > expected + 0.0001) return false;
        } else if ("boost".equals(group)) {
            Calendar c = MyUtils.getInstance(); int clock = c.get(Calendar.HOUR_OF_DAY) * 100 + c.get(Calendar.MINUTE);
            return "LIMIT_TIME_ENERGY_BUBBLE_BOOST".equals(type) && clock >= 500 && clock < 711;
        } else return false;
        // Active cards stay manual when duration is unknown or replacement could shorten their effect.
        return end == 0 || duration > 0 && duration <= Long.MAX_VALUE / 1000 && duration * 1000 >= end - now;
    }

    private static JSONArray inventory() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.queryPropList(false));
        JSONArray rows = data == null ? null : data.optJSONArray("forestPropVOList");
        if (rows == null || rows.length() > 100) return null;
        JSONArray normalized = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i), config = row == null ? null : row.optJSONObject("propConfigVO");
            Object type = row == null ? null : row.has("propType") ? row.opt("propType") : config == null ? null : config.opt("propType");
            if (!(type instanceof String) || config != null && config.has("propType") && !type.equals(config.opt("propType"))) return null;
            normalized.put(MyUtils.newJSONObject(row.toString()).put("propType", type));
        }
        return ForestPropSupport.usableInventory(normalized, System.currentTimeMillis());
    }

    private static Map<String, JSONObject> effects() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.queryMiscInfo()), map = data == null ? null : data.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray rows = using == null ? null : using.optJSONArray("userPropVOS");
        if (rows == null || rows.length() > 100) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            Object group = row == null ? null : row.opt("propGroup");
            long end = number(row, "endTime");
            if (!(group instanceof String) || ((String) group).isEmpty() || end < 0) return null;
            if (end <= System.currentTimeMillis()) continue;
            if (result.put((String) group, row) != null || "robExpandCard".equals(group) && factor(row) <= 0) return null;
        }
        return result;
    }

    private static JSONObject home() throws Exception { TimeUtil.sleep(0); return payload(AntForestRpcCall.queryHomePage()); }
    private static Map<Long, Long> waiting(JSONObject home) {
        long now = number(home, "now"); JSONArray rows = home == null ? null : home.optJSONArray("bubbles");
        if (now <= 0 || rows == null || rows.length() > 100) return null;
        Map<Long, Long> result = new LinkedHashMap<>(); Set<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); long id = number(row, "id"), at = number(row, "produceTime");
            if (id <= 0 || !seen.add(id)) return null;
            if ("WAITING".equals(row.optString("collectStatus")) && number(row, "fullEnergy") > 0 && at > now) result.put(id, at);
        }
        return result;
    }
    private static boolean advanced(Map<Long, Long> before, JSONObject home, long previousNow) {
        if (before == null || home == null) return false;
        Map<Long, Long> after = waiting(home);
        if (after == null) return false;
        JSONArray rows = home.optJSONArray("bubbles"); long now = number(home, "now");
        if (now < previousNow) return false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); Long original = before.get(number(row, "id"));
            if (original != null && now < original && ("WAITING".equals(row.optString("collectStatus"))
                    && number(row, "produceTime") > now && number(row, "produceTime") < original
                    || "AVAILABLE".equals(row.optString("collectStatus")) && number(row, "remainEnergy") > 0)) return true;
        }
        return false;
    }
    private static long stock(JSONArray bag, String group, String type) {
        if (bag == null) return -1; long count = 0;
        for (int i = 0; i < bag.length(); i++) { JSONObject row = bag.optJSONObject(i); if (group.equals(row.optString("propGroup")) && type.equals(row.optString("propType"))) count += number(row, "holdsNum"); }
        return count;
    }
    private static boolean has(JSONArray bag, String group, String type, String id) {
        if (bag == null) return false;
        for (int i = 0; i < bag.length(); i++) { JSONObject row = bag.optJSONObject(i); if (!group.equals(row.optString("propGroup")) || !type.equals(row.optString("propType"))) continue;
            JSONArray ids = row.optJSONArray("propIdList"); for (int j = 0; ids != null && j < ids.length(); j++) if (id.equals(ids.opt(j))) return true; }
        return false;
    }
    private static long end(Map<String, JSONObject> effects, String group) { return effects.containsKey(group) ? number(effects.get(group), "endTime") : 0; }
    private static double factor(JSONObject row) {
        JSONObject detail = row == null ? null : row.optJSONObject("detail"); Object value = detail == null ? null : detail.opt("factor");
        if (!(value instanceof String) && !(value instanceof Number)) return 0;
        try { double n = Double.parseDouble(value.toString()); return Double.isFinite(n) && n > 0 ? n : 0; } catch (NumberFormatException e) { return 0; }
    }
    private static boolean sameFactor(JSONObject before, JSONObject after) { return Math.abs(factor(before) - factor(after)) < 0.0001; }
    private static long number(JSONObject row, String key) { return row == null ? -1 : AntForestV2.forestFeatureLong(row, key); }
    private static JSONObject payload(String raw) {
        JSONObject root = MyUtils.newJSONObject(raw), data = root.optJSONObject("resData");
        if (rejected(root) || data != null && rejected(data)) return null;
        return AntForestV2.forestSignPayload(root);
    }
    private static boolean rejected(JSONObject root) {
        return root.has("success") && !Boolean.TRUE.equals(root.opt("success")) || root.has("isSuccess") && !Boolean.TRUE.equals(root.opt("isSuccess"))
                || root.has("resultCode") && !Set.of("SUCCESS", "100", "200").contains(root.optString("resultCode"))
                || root.has("retCode") && !"0".equals(root.optString("retCode"));
    }
    private static int day() { Calendar c = MyUtils.getInstance(); return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR); }
    private static boolean current(String uid, int day) { return uid.equals(UserIdMap.getCurrentUid()) && day == day(); }
}
