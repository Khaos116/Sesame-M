package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.idMap.SesameGiftIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

final class SesameGrainExchange {
    static void run(Set<String> selected, int budget) {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) return;
        try {
            TimeUtil.sleep(0);
            SesameGiftIdMap.load(uid);
            if (!Status.hasFlagToday("member::sesameGrainCatalogue")) {
                Map<String, JSONObject> catalogue = queryCatalogue();
                if (catalogue == null) return;
                for (Map.Entry<String, JSONObject> entry : catalogue.entrySet()) {
                    JSONObject item = entry.getValue();
                    SesameGiftIdMap.add(entry.getKey(), item.optString("awardName", entry.getKey())
                            + "（" + item.optString("point", "?") + "芝麻粒）" + (cost(item) < 0 ? "[不可自动兑换]" : ""));
                }
                if (!SesameGiftIdMap.save(uid)) return;
                Status.flagToday("member::sesameGrainCatalogue");
            }
            if (selected == null || selected.isEmpty() || budget <= 0) return;
            int count = 0;
            for (String id : selected) {
                if (++count > 50) return;
                String key = "member::sesameGrainAttempt::" + id;
                if (id == null || id.isEmpty() || !SesameGiftIdMap.getMap().containsKey(id) || Status.hasFlagToday(key)) continue;
                TimeUtil.sleep(0);
                JSONObject detail = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.querySesameGiftDetail(id)));
                JSONObject item = detail == null ? null : detail.optJSONObject("awardTemplateVO");
                if (item == null || !id.equals(item.opt("awardTemplateId"))) continue;
                long price = cost(item);
                int spent = Status.getIntFlagToday("member::sesameGrainSpent");
                if (price < 0 || spent < 0 || price > budget - (long) spent) continue;
                TimeUtil.sleep(0);
                Status.setIntFlagToday("member::sesameGrainSpent", spent + (int) price);
                Status.flagToday(key);
                JSONObject accepted = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.obtainSesameGift(id)));
                Object record = accepted == null ? null : accepted.opt("awardRecordId");
                if (!(record instanceof String) || ((String) record).isEmpty()) {
                    Log.record("芝麻粒兑换结果未确认，本日不重复#" + id);
                    return;
                }
                TimeUtil.sleep(0);
                JSONObject result = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryMySesameGift((String) record)));
                if (result == null || result.length() == 0
                        || (result.has("awardId") && !record.equals(result.opt("awardId")))
                        || (result.has("awardRecordId") && !record.equals(result.opt("awardRecordId")))
                        || (result.has("awardTemplateId") && !id.equals(result.opt("awardTemplateId")))) {
                    Log.record("芝麻粒兑换记录回查未确认，本日不重复#" + id);
                    return;
                }
                Log.other("芝麻粒兑换🛒记录回查成功#" + item.optString("awardName") + " 消耗" + price + "粒");
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("SesameGrainExchange", "芝麻粒兑换失败", e); }
    }

    private static Map<String, JSONObject> queryCatalogue() throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        List<String> tabs = new ArrayList<>();
        tabs.add("");
        int requests = 0;
        for (int t = 0; t < tabs.size(); t++) {
            String tab = tabs.get(t);
            Set<String> seen = new HashSet<>();
            for (int page = 1; ; page++) {
                // ponytail: 最多20标签/50页，本轮未完整时保留旧目录并停止兑换。
                if (++requests > 50 || tabs.size() > 20) return null;
                TimeUtil.sleep(0);
                JSONObject data = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.querySesameGiftList(page, tab)));
                if (data == null || !(data.opt("hasNext") instanceof Boolean)) return null;
                Map<String, JSONObject> rows = AntMember.memberRowsById(data.optJSONArray("awardTemplateList"), "awardTemplateId");
                if (rows == null || rows.size() > 20) return null;
                JSONArray discovered = data.optJSONArray("tabList");
                for (int i = 0; discovered != null && i < discovered.length(); i++) {
                    Object raw = discovered.opt(i);
                    if (raw instanceof JSONObject) raw = ((JSONObject) raw).opt("tab");
                    if (!(raw instanceof String)) return null;
                    String next = (String) raw;
                    if (!next.isEmpty() && !"all".equals(next) && !tabs.contains(next)) tabs.add(next);
                }
                int previous = seen.size();
                for (Map.Entry<String, JSONObject> entry : rows.entrySet()) {
                    seen.add(entry.getKey());
                    JSONObject old = result.get(entry.getKey());
                    if (old != null && (!old.optString("awardName").equals(entry.getValue().optString("awardName"))
                            || !old.optString("point").equals(entry.getValue().optString("point")))) return null;
                    result.putIfAbsent(entry.getKey(), entry.getValue());
                }
                if (!Boolean.TRUE.equals(data.opt("hasNext"))) break;
                if (seen.size() == previous) return null;
            }
        }
        return result;
    }

    private static long cost(JSONObject item) {
        if (item == null || !(item.opt("awardName") instanceof String) || item.optString("awardName").isEmpty()
                || !(item.opt("awardProdType") instanceof String) || item.optString("awardProdType").isEmpty()
                || !Boolean.FALSE.equals(item.opt("hasTaken")) || !Boolean.FALSE.equals(item.opt("hasFinished"))) return -1;
        if (SesameAchievements.exactNonNegative(item.opt("remainingBudget")) <= 0) return -1;
        JSONObject ext = item.optJSONObject("extInfo");
        if (ext == null) return -1;
        long now = System.currentTimeMillis();
        long start = item.has("sendStartTime") ? SesameAchievements.exactNonNegative(item.opt("sendStartTime")) : 0;
        long end = item.has("sendEndTime") ? SesameAchievements.exactNonNegative(item.opt("sendEndTime")) : 0;
        if (start < 0 || end < 0 || (start > 0 && now < start) || (end > 0 && now > end)
                || (start > 0 && end > 0 && start > end)) return -1;
        for (JSONObject obj : new JSONObject[]{item, ext}) {
            for (String field : new String[]{"yuan", "cashAmount", "moneyPrice", "channelPrice", "cashPrice"}) {
                if (!obj.has(field)) continue;
                Object value = obj.opt(field);
                if (!(value instanceof Number) && !(value instanceof String)) return -1;
                try { if (new java.math.BigDecimal(value.toString()).signum() != 0) return -1; }
                catch (NumberFormatException e) { return -1; }
            }
        }
        String text = (item.optString("awardName") + " " + item.optString("awardProdType") + " " + item.optString("awardTabLabel")
                + " " + ext.optString("awardTabLabel") + " " + ext.optString("toUseAddress") + " " + ext.optString("awardShouldKnow")).toLowerCase(Locale.ROOT);
        for (String word : new String[]{"收货", "发货", "下单", "实付", "支付", "邮寄", "快递", "订单", "付邮", "邮费", "包邮", "商品", "实物", "优惠券", "红包", "话费", "券", "小程序",
                "goods", "platformphysicalitem", "miniapp_itembase", "union_price", "deduct_cash", "coupon_purchase", "needsendcoupon", "recruitplatform", "online_shopping"}) {
            if (text.contains(word)) return -1;
        }
        return SesameAchievements.exactNonNegative(item.opt("point"));
    }
}
