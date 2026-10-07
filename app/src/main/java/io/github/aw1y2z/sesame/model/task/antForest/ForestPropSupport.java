package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class ForestPropSupport {
    private static final long DAY = 86400000L;
    private static final String DOUBLE_RECEIPT = "forestPropDoubleReceipt";
    private static final String REFILL_RECEIPT = "forestPropRefillReceipt";

    static JSONArray usableInventory(JSONArray rows, long now) {
        if (rows == null || rows.length() > 100) return null;
        JSONArray result = new JSONArray();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String) || !(row.opt("propType") instanceof String)) return null;
            long count = number(row, "holdsNum"), expiry = row.has("recentExpireTime") ? number(row, "recentExpireTime") : 0;
            JSONArray ids = row.optJSONArray("propIdList");
            if (count < 0 || count > 1000 || expiry < 0 || ids == null && count > 0 || ids != null && ids.length() != count) return null;
            for (int j = 0; ids != null && j < ids.length(); j++) {
                Object id = ids.opt(j);
                if (!(id instanceof String) || ((String) id).isEmpty() || ((String) id).length() > 256 || !seen.add((String) id)) return null;
            }
            if (count > 0 && (expiry == 0 || expiry > now)) result.put(row);
        }
        return result;
    }

    static JSONObject chooseDouble(JSONArray rows, boolean permanent, boolean card31, long now, long activeEnd) {
        JSONArray usable = usableInventory(rows, now + 10000);
        if (usable == null) return null;
        JSONObject chosen = null;
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < usable.length(); i++) {
            JSONObject row = usable.optJSONObject(i);
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            String type = row.optString("propType");
            if (activeEnd > now && !"ENERGY_DOUBLE_CLICK_31DAYS".equals(type)) continue;
            if (!"LIMIT_TIME_ENERGY_DOUBLE_CLICK".equals(type) && !(permanent && "ENERGY_DOUBLE_CLICK".equals(type))
                    && !(card31 && "ENERGY_DOUBLE_CLICK_31DAYS".equals(type))) continue;
            String id = row.optJSONArray("propIdList").optString(0);
            if (Status.hasFlagToday("forest::smartDoubleCard::" + id)) continue;
            long expiry = row.has("recentExpireTime") ? number(row, "recentExpireTime") : Long.MAX_VALUE;
            if (expiry == 0) expiry = Long.MAX_VALUE;
            if (chosen == null || expiry < earliest) { chosen = row; earliest = expiry; }
        }
        return chosen;
    }

    static boolean doubleRenewAllowed(long end, boolean renew31, long now) {
        return end == 0 || end > now && renew31 && end - now <= 31 * DAY;
    }

    static boolean consumeDouble(JSONObject selected, long expectedEnd, boolean renew31, int dailyLimit) {
        if (selected == null || dailyLimit <= 0) return false;
        String uid = UserIdMap.getCurrentUid();
        int day = day();
        if (uid == null || uid.isEmpty()) return false;
        try {
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            if (!runtime.getString(DOUBLE_RECEIPT).isEmpty() || Status.hasFlagToday("forest::smartDoubleUnconfirmed")
                    || Status.getIntFlagToday("forest::smartDoubleAttempts") >= Math.min(dailyLimit, 100)) return false;
            JSONArray before = inventory();
            String type = selected.optString("propType");
            JSONArray ids = selected.optJSONArray("propIdList");
            String id = ids == null ? "" : ids.optString(0);
            if (!Set.of("ENERGY_DOUBLE_CLICK", "ENERGY_DOUBLE_CLICK_31DAYS", "LIMIT_TIME_ENERGY_DOUBLE_CLICK").contains(type)
                    || id.isEmpty() || !"doubleClick".equals(selected.optString("propGroup")) || !has(before, id, type)) return false;
            long end = doubleEnd();
            boolean renewal = "ENERGY_DOUBLE_CLICK_31DAYS".equals(type) && renew31;
            if (end != expectedEnd || !doubleRenewAllowed(end, renewal, System.currentTimeMillis())) return false;
            long stock = stock(before, "doubleClick", type);
            String flag = "forest::smartDoubleCard::" + id;
            if (stock <= 0 || Status.hasFlagToday(flag)) return false;
            TimeUtil.sleep(0);
            if (!current(uid, day)) return false;
            JSONObject receipt = MyUtils.newJSONObject().put("propId", id).put("propType", type).put("stock", stock).put("end", end).put("day", day);
            if (!runtime.putVerified(DOUBLE_RECEIPT, receipt.toString())) return false;
            TimeUtil.sleep(0);
            if (!current(uid, day)) return false;
            Status.flagToday(flag);
            Status.flagToday("forest::smartDoubleUnconfirmed");
            Status.setIntFlagToday("forest::smartDoubleAttempts", Status.getIntFlagToday("forest::smartDoubleAttempts") + 1);
            JSONObject ack = payload(AntForestRpcCall.consumeProp("doubleClick", id, type, false));
            String status = ack == null ? "" : ack.optString("usePropStatus");
            if (status.startsWith("NEED_CONFIRM") || "REPLACE".equals(status)) {
                JSONArray checked = inventory();
                if (!renewal || end == 0 || stock(checked, "doubleClick", type) != stock || !has(checked, id, type) || doubleEnd() != end) return false;
                TimeUtil.sleep(0);
                if (!current(uid, day)) return false;
                ack = payload(AntForestRpcCall.consumeProp("doubleClick", id, type, true));
            }
            JSONArray after = inventory();
            long afterEnd = doubleEnd();
            TimeUtil.sleep(0);
            if (ack == null || !current(uid, day) || stock(after, "doubleClick", type) != stock - 1 || has(after, id, type)
                    || afterEnd <= Math.max(end, System.currentTimeMillis())) return false;
            if (!runtime.putVerified(DOUBLE_RECEIPT, null)) return false;
            Status.clearFlag("forest::smartDoubleUnconfirmed");
            return true;
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("ForestPropSupport", "双击卡使用失败", e); return false; }
    }

    static JSONArray replenish(String group, boolean enabled, Map<String, Integer> selected, Map<String, JSONObject> catalog, int pointBudget) {
        if (!enabled || selected == null || selected.isEmpty() || catalog == null || pointBudget <= 0
                || !Set.of("doubleClick", "shield", "stealthCard", "robExpandCard", "boost").contains(group)) return null;
        String uid = UserIdMap.getCurrentUid();
        int day = day();
        if (uid == null || uid.isEmpty()) return null;
        try {
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            if (!runtime.getString(REFILL_RECEIPT).isEmpty()) return null;
            JSONArray before = inventory();
            if (before == null || stock(before, group, null) != 0) return null;
            int attempts = 0;
            // ponytail: at most one verified refill per invocation and 50 inspected selected SKUs.
            for (Map.Entry<String, Integer> entry : selected.entrySet()) {
                if (++attempts > 50) return null;
                String skuId = entry.getKey();
                if (skuId == null || !skuId.matches("[A-Za-z0-9_-]{1,256}")) continue;
                Integer limit = entry.getValue();
                JSONObject cached = catalog.get(skuId);
                if (limit == null || limit <= 0 || cached == null || Status.hasFlagToday("forest::propRefillAttempt::" + skuId)
                        || !Status.canVitalityExchangeBenefitToday(skuId, limit)) continue;
                String spuId = cached.optString("spuId");
                if (!spuId.matches("[A-Za-z0-9_-]{1,256}")) continue;
                JSONObject sku = sku(spuId, skuId);
                String type = assetType(sku);
                if (!group.equals(group(type))) continue;
                String name = sku.optString("skuName");
                if (name.isEmpty() || Set.of("皮肤", "装扮", "主题", "挂件", "背景", "红包", "优惠券", "券").stream().anyMatch(name::contains)) continue;
                JSONArray states = sku.optJSONArray("itemStatusList");
                JSONObject price = sku.optJSONObject("price");
                long cost = price == null ? -1 : number(price, "amount"), count = number(sku, "exchangedCount");
                int spent = Status.getIntFlagToday("forest::propRefillSpent");
                if (spent < 0 || states == null || states.length() != 0 || cost <= 0 || count < 0 || cost > pointBudget - (long) spent) continue;
                long points = points();
                if (points < cost) continue;
                before = inventory();
                if (before == null || stock(before, group, null) != 0) return null;
                TimeUtil.sleep(0);
                if (!current(uid, day)) return null;
                String order = UUID.randomUUID().toString();
                JSONObject receipt = MyUtils.newJSONObject().put("requestId", order).put("spuId", spuId).put("skuId", skuId)
                        .put("type", type).put("price", cost).put("exchangedCount", count).put("points", points).put("day", day);
                if (!runtime.putVerified(REFILL_RECEIPT, receipt.toString())) return null;
                TimeUtil.sleep(0);
                if (!current(uid, day)) return null;
                Status.flagToday("forest::exchangeLimit::" + skuId);
                Status.flagToday("forest::propRefillAttempt::" + skuId);
                Status.setIntFlagToday("forest::propRefillSpent", (int) (spent + cost));
                TimeUtil.sleep(0);
                if (!current(uid, day)) return null;
                JSONObject args = MyUtils.newJSONObject().put("sceneCode", "ANTFOREST_VITALITY").put("requestId", order)
                        .put("spuId", spuId).put("skuId", skuId).put("source", "GOOD_DETAIL");
                JSONObject ack = payload(ApplicationHook.requestString("com.alipay.antcommonweal.exchange.h5.exchangeBenefit", new JSONArray().put(args).toString()));
                JSONObject afterSku = sku(spuId, skuId);
                long afterPoints = points();
                JSONArray after = inventory();
                TimeUtil.sleep(0);
                if (ack == null || !current(uid, day) || afterSku == null || !type.equals(assetType(afterSku))
                        || number(afterSku, "exchangedCount") != count + 1 || afterPoints != points - cost || stock(after, group, type) <= 0) return null;
                if (!runtime.putVerified(REFILL_RECEIPT, null)) return null;
                Status.vitalityExchangeBenefitToday(skuId);
                Status.clearFlag("forest::exchangeLimit::" + skuId);
                Log.forest("森林缺货补兑🍃库存、兑换次数及活力值扣减回查成功");
                return after;
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("ForestPropSupport", "森林缺货补兑失败", e); }
        return null;
    }

    static boolean hasUnconfirmedRefill(String skuId) {
        String saved = RuntimeInfo.getInstance().getString(REFILL_RECEIPT);
        if (saved.isEmpty()) return false;
        JSONObject receipt = MyUtils.newJSONObject(saved);
        return receipt.optString("skuId").isEmpty() || skuId.equals(receipt.optString("skuId"));
    }

    private static String assetType(JSONObject sku) {
        JSONObject batch = sku == null ? null : sku.optJSONObject("rightsBatchConfigVO");
        JSONArray rights = batch == null ? null : batch.optJSONArray("rightsConfigVOList");
        if (rights == null || rights.length() != 1) return "";
        JSONObject right = rights.optJSONObject(0);
        Object raw = right == null ? null : right.opt("extend");
        JSONObject extend = raw instanceof JSONObject ? (JSONObject) raw : raw instanceof String ? MyUtils.newJSONObject((String) raw) : null;
        return extend == null ? "" : extend.optString("antiepScAssetsType");
    }

    private static String group(String type) {
        switch (type) {
            case "ENERGY_SHIELD": case "LIMIT_TIME_ENERGY_SHIELD": case "LIMIT_TIME_ENERGY_SHIELD_TREE":
            case "DFYC_ENERGY_SHIELD": case "FMQK_ENERGY_SHIELD": return "shield";
            case "ENERGY_DOUBLE_CLICK": case "LIMIT_TIME_ENERGY_DOUBLE_CLICK": case "ENERGY_DOUBLE_CLICK_31DAYS": return "doubleClick";
            case "STEALTH_CARD": case "LIMIT_TIME_STEALTH_CARD": return "stealthCard";
            case "SHAMO_ROB_EXPAND_CARD_1.5_1DAYS": case "VITALITY_ROB_EXPAND_CARD_1.1_3DAYS": return "robExpandCard";
            case "BUBBLE_BOOST": case "LIMIT_TIME_ENERGY_BUBBLE_BOOST": return "boost";
            default: return "";
        }
    }

    private static JSONObject sku(String spuId, String skuId) throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.itemDetail(spuId));
        JSONObject spu = data == null ? null : data.optJSONObject("spuItemInfoVO");
        JSONArray rows = spu == null ? null : spu.optJSONArray("skuModelList");
        if (rows == null || rows.length() > 100 || !spuId.equals(spu.optString("spuId"))) return null;
        JSONObject found = null;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && skuId.equals(row.optString("skuId"))) {
                if (found != null || row.has("spuId") && !spuId.equals(row.optString("spuId"))) return null;
                found = row;
            }
        }
        return found;
    }

    private static long points() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.queryVitalityStoreIndex());
        JSONObject info = data == null ? null : data.optJSONObject("userVitalityInfoVO");
        return info == null ? -1 : number(info, "totalVitalityAmount");
    }

    private static JSONArray inventory() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.queryPropList(false));
        return usableInventory(data == null ? null : data.optJSONArray("forestPropVOList"), System.currentTimeMillis());
    }

    private static long doubleEnd() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = payload(AntForestRpcCall.queryMiscInfo());
        JSONObject map = data == null ? null : data.optJSONObject("combineHandlerVOMap");
        JSONObject using = map == null ? null : map.optJSONObject("usingProp");
        JSONArray rows = using == null ? null : using.optJSONArray("userPropVOS");
        if (rows == null || rows.length() > 100) return -1;
        long result = 0, now = System.currentTimeMillis();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("propGroup") instanceof String)) return -1;
            if (!"doubleClick".equals(row.optString("propGroup"))) continue;
            long end = number(row, "endTime");
            if (end < 0 || end > now && result > 0) return -1;
            if (end > now) result = end;
        }
        return result;
    }

    private static boolean has(JSONArray rows, String id, String type) {
        if (rows == null) return false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (!type.equals(row.optString("propType"))) continue;
            JSONArray ids = row.optJSONArray("propIdList");
            for (int j = 0; ids != null && j < ids.length(); j++) if (id.equals(ids.opt(j))) return true;
        }
        return false;
    }

    private static long stock(JSONArray rows, String group, String type) {
        if (rows == null) return -1;
        long result = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (group.equals(row.optString("propGroup")) && (type == null || type.equals(row.optString("propType")))) result += number(row, "holdsNum");
        }
        return result;
    }

    private static long number(JSONObject row, String name) { return AntForestV2.forestFeatureLong(row, name); }
    private static JSONObject payload(String raw) {
        JSONObject root = MyUtils.newJSONObject(raw), data = root.optJSONObject("resData");
        if (rejected(root) || data != null && rejected(data)) return null;
        return AntForestV2.forestSignPayload(root);
    }
    private static boolean rejected(JSONObject root) {
        return root.has("success") && !Boolean.TRUE.equals(root.opt("success"))
                || root.has("resultCode") && !Set.of("SUCCESS", "100", "200").contains(root.optString("resultCode"))
                || root.has("retCode") && !"0".equals(root.optString("retCode"));
    }
    private static int day() { Calendar c = MyUtils.getInstance(); return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR); }
    private static boolean current(String uid, int day) { return uid.equals(UserIdMap.getCurrentUid()) && day == day(); }
}
