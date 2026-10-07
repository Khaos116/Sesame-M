package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

final class SesameAchievements {
    static void run() {
        try {
            TimeUtil.sleep(0);
            JSONObject home = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryAccomplishmentHome(null)));
            Map<String, JSONObject> tabs = AntMember.memberRowsById(home == null ? null : home.optJSONArray("tabs"), "tabCode");
            if (tabs == null || tabs.size() > 20) return;
            Set<String> seen = new HashSet<>();
            int attempted = 0;
            for (String tab : tabs.keySet()) {
                TimeUtil.sleep(0);
                JSONObject page = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryAccomplishmentHome(tab)));
                JSONArray categories = page == null ? null : page.optJSONArray("themeCategories");
                if (categories == null || categories.length() > 100) return;
                JSONArray all = new JSONArray();
                for (int i = 0; i < categories.length(); i++) {
                    JSONObject category = categories.optJSONObject(i);
                    JSONArray rows = category == null ? null : category.optJSONArray("seriesList");
                    if (rows == null || rows.length() > 200) return;
                    for (int j = 0; j < rows.length(); j++) all.put(rows.opt(j));
                }
                Map<String, JSONObject> medals = AntMember.memberRowsById(all, "medalSeriesCode");
                if (medals == null) return;
                for (Map.Entry<String, JSONObject> entry : medals.entrySet()) {
                    if (!seen.add(entry.getKey()) || !mayHaveReward(entry.getValue())) continue;
                    // ponytail: 每轮最多50个成就系列；剩余候选等下一轮。
                    if (++attempted > 50) return;
                    claimSeries(entry.getKey());
                }
            }
        } catch (TaskCancelledException e) { throw e; }
        catch (Exception e) { Log.err("SesameAchievements", "成就馆处理失败", e); }
    }

    static long exactNonNegative(Object value) {
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try {
            long result = new java.math.BigDecimal(value.toString()).longValueExact();
            return result < 0 ? -1 : result;
        } catch (NumberFormatException | ArithmeticException e) { return -1; }
    }

    private static boolean ready(JSONObject row) {
        if (row == null || exactNonNegative(row.opt("claimedAt")) != 0) return false;
        if ("UNCLAIMED".equals(row.optString("displayState"))) return true;
        long threshold = exactNonNegative(row.opt("threshold"));
        return threshold > 0 && exactNonNegative(row.opt("currentValue")) >= threshold;
    }

    private static boolean mayHaveReward(JSONObject row) {
        if (exactNonNegative(row.opt("levelNo")) <= 0) return false;
        if (ready(row)) return true;
        long next = exactNonNegative(row.opt("nextThreshold"));
        return exactNonNegative(row.opt("claimedAt")) > 0 && next > 0 && exactNonNegative(row.opt("currentValue")) >= next;
    }

    private static Map<Long, JSONObject> levels(String series) throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.queryAccomplishmentDetail(series)));
        JSONArray rows = data == null ? null : data.optJSONArray("levels");
        if (rows == null || rows.length() == 0 || rows.length() > 100) return null;
        Map<Long, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            long level = row == null ? -1 : exactNonNegative(row.opt("levelNo"));
            if (level <= 0 || exactNonNegative(row.opt("claimedAt")) < 0 || result.put(level, row) != null) return null;
        }
        return result;
    }

    private static void claimSeries(String series) throws Exception {
        Map<Long, JSONObject> before = levels(series);
        if (before == null) return;
        long target = 0;
        for (Map.Entry<Long, JSONObject> entry : before.entrySet()) {
            if (ready(entry.getValue())) target = Math.max(target, entry.getKey());
        }
        if (target == 0) return;
        String key = "member::sesameAchievement::" + series + ":" + target;
        if (Status.hasFlagToday(key)) return;
        TimeUtil.sleep(0);
        Status.flagToday(key);
        JSONObject accepted = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.claimAccomplishment(series)));
        Map<Long, JSONObject> after = levels(series);
        JSONObject confirmed = after == null ? null : after.get(target);
        if (accepted != null && confirmed != null && exactNonNegative(confirmed.opt("claimedAt")) > 0) {
            Log.other("芝麻成就馆🏅领取回查成功#" + series + " 等级" + target);
        } else Log.record("芝麻成就馆领取未确认，本日不重复尝试#" + series + " 等级" + target);
    }
}
