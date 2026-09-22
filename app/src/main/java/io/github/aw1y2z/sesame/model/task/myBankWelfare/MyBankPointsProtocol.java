package io.github.aw1y2z.sesame.model.task.myBankWelfare;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import io.github.aw1y2z.sesame.util.JsonUtil;

/** 只读的SUPER930积分余额查询。移植自 GR 分支，见 docs/MyFix.md。 */
public final class MyBankPointsProtocol {
    public static final String METHOD = "com.alipay.loanpromoweb.promo.group.point.pointBanlance";
    private static final ObjectMapper JSON = JsonUtil.copyMapper();

    private MyBankPointsProtocol() { }

    public static final class Balance {
        public final Long total;
        public final Long yearExpiring;
        public final Long dateExpiring;
        private Balance(Long total, Long yearExpiring, Long dateExpiring) {
            this.total = total;
            this.yearExpiring = yearExpiring;
            this.dateExpiring = dateExpiring;
        }
    }

    /** 按GMT+8（Asia/Shanghai）计算下个月第一天。 */
    public static String nextMonthFirstDay(long now) {
        TimeZone zone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar calendar = Calendar.getInstance(zone);
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, 1);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setTimeZone(zone);
        return format.format(calendar.getTime());
    }

    public static String arguments(String cutoffDate) {
        dateKey(cutoffDate);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("queryExpireEndDate", cutoffDate);
        args.put("sceneCode", "SUPER930");
        return JsonUtil.toJsonString(Collections.singletonList(args));
    }

    public static Balance parse(String response, String cutoffDate) throws IOException {
        String key = dateKey(cutoffDate);
        if (response == null || response.length() > 262144) throw new IOException("invalid response size");
        JsonNode root = JSON.readTree(response);
        if (root == null || !root.isObject() || !root.path("success").isBoolean()
                || !root.path("success").booleanValue() || !root.path("result").isObject()) {
            throw new IOException("invalid point response");
        }
        JsonNode result = root.get("result");
        return new Balance(integer(result.get("pointBalance")), integer(result.get("currentYearExpirePoint")),
                integer(result.path("assignDateExpirePoint").get(key)));
    }

    private static Long integer(JsonNode value) throws IOException {
        if (value == null || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw new IOException("invalid point amount");
        }
        return value.longValue();
    }

    private static String dateKey(String date) {
        if (date == null || !date.matches("[0-9]{4}-(0[1-9]|1[0-2])-01")) {
            throw new IllegalArgumentException("expected next-month first day");
        }
        return date.replace("-", "");
    }
}
