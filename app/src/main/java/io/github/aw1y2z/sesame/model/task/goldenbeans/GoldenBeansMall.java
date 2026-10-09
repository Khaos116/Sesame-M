package io.github.aw1y2z.sesame.model.task.goldenbeans;

import io.github.aw1y2z.sesame.util.MyUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.entity.AlipayGoldenBeansMallItem;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansMallItemMap;

/**
 * 金豆商城（芭芭农场金豆罐兑换页）的权益兑换。
 * <p>
 * 纯金豆判据：多数商品带 {@code moneyPrice}（0.01 元附加费），纯金豆商品没有该字段。
 * 所需罐数 = {@code price.cent} / 100，余额取主页 {@code jarInfo.jarCount}。
 * 兑换按 {@code itemInfoVOList[].skuModelList[]} 的 spuId + skuId 提交。
 */
public final class GoldenBeansMall {

    /** 候选同步的当日标记：可兑状态每天变化，无需每轮重扫 */
    private static final String FLAG_CATALOGUE = "goldenBeans::mallCatalogue";
    /** 每日兑换次数标记前缀（按商品名） */
    private static final String FLAG_EXCHANGE_PREFIX = "goldenBeans::mallExchange::";
    /** 单轮兑换次数封顶，防止勾选过多时连续下单 */
    private static final int MAX_EXCHANGE_PER_RUN = 5;
    private static final int PAGE_SIZE = 20;

    private GoldenBeansMall() {
    }

    private static int nonNegativeInt(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        if (!(value instanceof Number) && !(value instanceof String)) return -1;
        try {
            int number = new BigDecimal(value.toString()).intValueExact();
            return number >= 0 ? number : -1;
        } catch (NumberFormatException | ArithmeticException invalid) {
            return -1;
        }
    }

    /** 模块运行时每日同步候选；开启兑换且有勾选项时使用实时列表。 */
    public static boolean run(int interval, Map<String, Integer> selected, boolean exchangeEnabled) {
        try {
            boolean exchangeRequested = exchangeEnabled && selected != null && !selected.isEmpty();
            if (!exchangeRequested && Status.hasFlagToday(FLAG_CATALOGUE)) return true;
            JSONArray items = fetchItems(interval);
            if (items == null) {
                return false;
            }
            int added = syncCatalogue(items);
            if (!exchangeEnabled) {
                Log.goldenBeans("金豆商城🛒兑换已关闭#仅更新可兑列表[新增" + added + "]");
                return true;
            }
            if (selected == null || selected.isEmpty()) {
                Log.goldenBeans("金豆商城🛒未选择要兑换的权益#本轮只同步列表");
                return true;
            }
            int exchanged = 0;
            int[] submitted = {0};
            JSONArray[] orderSnapshot = {null};
            for (int i = 0; i < items.length() && submitted[0] < MAX_EXCHANGE_PER_RUN; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("spuName", "");
                if (name.isEmpty() || !selected.containsKey(name)) {
                    continue;
                }
                if (exchange(interval, item, selected.get(name), orderSnapshot, submitted)) {
                    exchanged++;
                }
            }
            Log.goldenBeans("金豆商城🛒本轮提交[" + submitted[0] + "]项#确认兑换[" + exchanged + "]项");
            return true;
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.record("金豆商城：查询/同步异常=" + th.getClass().getSimpleName());
            return false;
        }
    }

    /** shortcut: 候选仅首20条且每个SPU首规格，需支持更多商品时再扩展分页和规格选择。 */
    private static JSONArray fetchItems(int interval) throws Exception {
        GoldenBeansSupport.pause(interval);
        JSONObject jo = GoldenBeansSupport.parse(goldenbeansRpcCall.mallItems(0, PAGE_SIZE));
        if (!GoldenBeansSupport.ok(jo)) {
            Log.goldenBeans("金豆商城⚠️商品列表获取失败#code="
                    + (jo == null ? "EMPTY" : jo.optString("resultCode", jo.optString("code", "UNKNOWN"))));
            return null;
        }
        JSONArray itemList = jo.optJSONArray("itemInfoVOList");
        if (itemList == null) {
            Log.goldenBeans("金豆商城⚠️商品列表结构异常#无 itemInfoVOList");
            return null;
        }
        JSONArray result = new JSONArray();
        for (int i = 0; i < itemList.length(); i++) {
            JSONObject spu = itemList.optJSONObject(i);
            if (spu == null) {
                continue;
            }
            JSONArray skuList = spu.optJSONArray("skuModelList");
            if (skuList == null || skuList.length() == 0) {
                continue;
            }
            JSONObject sku = skuList.optJSONObject(0);
            if (sku == null) {
                continue;
            }
            JSONObject price = sku.optJSONObject("price");
            JSONObject moneyPrice = sku.optJSONObject("moneyPrice");
            JSONObject view = MyUtils.newJSONObject();
            view.put("spuName", spu.optString("spuName", ""));
            view.put("spuId", spu.optString("spuId", ""));
            view.put("skuId", sku.optString("skuId", ""));
            view.put("skuName", sku.optString("skuName", ""));
            int cents = nonNegativeInt(price, "cent");
            view.put("cost", cents >= 0 && cents % 100 == 0 ? cents / 100 : -1);
            view.put("needMoney", sku.has("moneyPrice")
                    && nonNegativeInt(moneyPrice, "cent") != 0);
            view.put("dayLeft", nonNegativeInt(sku, "userDayLeftAmount"));
            result.put(view);
        }
        return result;
    }

    /** 登记可兑商品为配置候选；用 spuName 作键，避免每日次数变化导致勾选失效 */
    private static int syncCatalogue(JSONArray items) {
        GoldenBeansMallItemMap.load();
        int added = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String name = item.optString("spuName", "");
            if (name.isEmpty() || GoldenBeansMallItemMap.get(name) != null) {
                continue;
            }
            GoldenBeansMallItemMap.add(name, name);
            added++;
        }
        if (GoldenBeansMallItemMap.save()) {
            if (added > 0) AlipayGoldenBeansMallItem.clear();
            Status.flagToday(FLAG_CATALOGUE);
            if (added > 0) Log.goldenBeans("同步权益🉑金豆商城可兑列表[新增" + added + "]");
        }
        return added;
    }

    /** 由现有手动任务入口串行执行，用户核对后只解除所选商品，不发送兑换请求。 */
    public static void clearSelectedReceipts(int interval, Map<String, Integer> selected) {
        if (selected == null || selected.isEmpty()) {
            Log.goldenBeans("金豆商城⏭️未选择商品，未清理回执");
            return;
        }
        try {
            JSONArray items = fetchItems(interval);
            if (items == null) {
                Log.goldenBeans("金豆商城⚠️商品列表未确认，未清理任何回执");
                return;
            }
            RuntimeInfo runtime = RuntimeInfo.getInstance();
            int cleared = 0;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || !selected.containsKey(item.optString("spuName"))
                        || item.optString("spuId").isEmpty() || item.optString("skuId").isEmpty()) continue;
                String key = "goldenBeans.mall.pending::" + item.optString("spuId") + "::" + item.optString("skuId");
                if (runtime.getString(key).isEmpty()) continue;
                boolean saved = runtime.putVerified(key, "");
                if (saved) cleared++;
                Log.goldenBeans((saved ? "✅ " : "⚠️ ") + "金豆商城[" + item.optString("spuName") + "]#"
                        + (saved ? "已按人工核对解除回执，未发送下单请求；后续按兑换开关和次数执行" : "回执清理保存失败，保持原状态"));
            }
            Log.goldenBeans("金豆商城📋人工解除回执[" + cleared + "]项；仅处理当前商品列表中选中的规格");
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.record("金豆商城：清理回执异常=" + th.getClass().getSimpleName() + "，未发送下单请求");
        }
    }

    /** @param dailyLimit 该商品每日兑换次数，0 表示不限 */
    private static boolean exchange(int interval, JSONObject item, int dailyLimit, JSONArray[] orderSnapshot, int[] submitted) throws Exception {
        String name = item.optString("spuName", "");
        String spuId = item.optString("spuId", "");
        String skuId = item.optString("skuId", "");
        int cost = nonNegativeInt(item, "cost");
        int dayLeft = nonNegativeInt(item, "dayLeft");
        if (cost < 0 || dayLeft < 0 || dailyLimit < 0) {
            Log.goldenBeans("金豆商城⏭️价格或剩余次数未确认[" + name + "]#本轮不兑换");
            return false;
        }
        if (spuId.isEmpty() || skuId.isEmpty()) {
            return false;
        }
        String receiptKey = "goldenBeans.mall.pending::" + spuId + "::" + skuId;
        RuntimeInfo runtime = RuntimeInfo.getInstance();
        String stored = runtime.getString(receiptKey);
        if (!stored.isEmpty()) {
            JSONObject receipt = MyUtils.newJSONObject(stored);
            return reconcile(interval, item, receiptKey, receipt, orderSnapshot);
        }
        if (item.optBoolean("needMoney", false)) {
            Log.goldenBeans("金豆商城⏭️跳过需附加人民币商品[" + name + "]");
            return false;
        }
        if (dayLeft == 0) {
            Log.goldenBeans("金豆商城⏭️今日已兑完[" + name + "]");
            return false;
        }
        if (Status.hasFlagToday(FLAG_EXCHANGE_PREFIX + name + "::rejected")) return false;
        if (dailyLimit > 0) {
            int done = Status.getIntFlagToday(FLAG_EXCHANGE_PREFIX + name);
            if (done >= dailyLimit) {
                Log.goldenBeans("金豆商城⏭️已达每日兑换次数[" + name + "#" + done + "/" + dailyLimit + "]");
                return false;
            }
        }
        if (cost > 0) {
            int jars = jarCount(interval);
            if (jars < 0 || jars < cost) {
                Log.goldenBeans("金豆商城⏭️金豆罐不足[" + name + "]#需[" + cost + "罐]余[" + jars + "罐]");
                return false;
            }
        }
        try {
            JSONArray before = orderSnapshot[0] != null ? orderSnapshot[0] : fetchOrders(interval);
            if (before == null) {
                Log.goldenBeans("金豆商城⏭️订单基线未确认[" + name + "]#本轮不兑换");
                return false;
            }
            GoldenBeansSupport.pause(interval);
            JSONObject receipt = MyUtils.newJSONObject().put("beforeCount", before.length())
                    .put("dayLeft", dayLeft).put("day", Math.floorDiv(System.currentTimeMillis() + 28800000L, 86400000L))
                    .put("doneBefore", Status.getIntFlagToday(FLAG_EXCHANGE_PREFIX + name));
            if (!runtime.putVerified(receiptKey, receipt.toString())) {
                Log.goldenBeans("金豆商城⚠️提交前回执保存失败[" + name + "]#本轮未发送兑换请求");
                return false;
            }
            // 只有已确认兑换后的完整快照可以作为下一商品基线，异常提交不得复用旧快照。
            orderSnapshot[0] = null;
            submitted[0]++;
            JSONObject jo = GoldenBeansSupport.parse(goldenbeansRpcCall.mallExchange(spuId, skuId));
            if (!GoldenBeansSupport.ok(jo)) {
                boolean rejected = jo != null && Boolean.FALSE.equals(jo.opt("success"))
                        && !MessageUtil.isRetryable(jo) && !MessageUtil.isServerBusy(jo);
                boolean cleared = rejected && runtime.putVerified(receiptKey, "");
                if (rejected) {
                    Status.flagToday(FLAG_EXCHANGE_PREFIX + name + "::rejected");
                }
                Log.goldenBeans("金豆商城⚠️[" + name + "]#" + (cleared ? "服务端明确拒绝，已解除未确认回执"
                        : rejected ? "服务端明确拒绝，清理回执失败" : "响应未确认，保留回执待查")
                        + "#code=" + (jo == null ? "EMPTY" : jo.optString("resultCode", jo.optString("code", "UNKNOWN"))));
                return false;
            }
            if (!jo.optBoolean("canBuy", false)) {
                if (Boolean.FALSE.equals(jo.opt("canBuy"))) {
                    Log.goldenBeans("金豆商城⏭️服务端判定不可兑[" + name + "]");
                    runtime.putVerified(receiptKey, "");
                    Status.flagToday(FLAG_EXCHANGE_PREFIX + name + "::rejected");
                } else {
                    Log.goldenBeans("金豆商城❓[" + name + "]#缺少可兑确认，保留回执待查");
                }
                return false;
            }
            receipt.put("orderNo", jo.optString("orderNo", ""));
            runtime.putVerified(receiptKey, receipt.toString());
            return reconcile(interval, item, receiptKey, receipt, orderSnapshot);
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.record("金豆商城[" + name + "]：兑换/回查异常=" + th.getClass().getSimpleName() + "，保留回执待查");
            return false;
        }
    }

    private static boolean reconcile(int interval, JSONObject item, String receiptKey, JSONObject receipt,
                                     JSONArray[] orderSnapshot) throws Exception {
        orderSnapshot[0] = null;
        String name = item.optString("spuName");
        String orderNo = receipt.optString("orderNo");
        long today = Math.floorDiv(System.currentTimeMillis() + 28800000L, 86400000L);
        boolean sameDay = receipt.optLong("day", -1L) == today;
        if (orderNo.isEmpty() && (!sameDay || receipt.optInt("beforeCount", -1) < 0)) {
            String flag = FLAG_EXCHANGE_PREFIX + name + "::manualReceipt";
            if (!Status.hasFlagToday(flag)) {
                Status.flagToday(flag);
                Log.goldenBeans("🕓 金豆商城[" + name + "]#旧回执无订单号或同日基线，无法自动确认，本轮未查询订单、未下单"
                        + "；请核对订单和扣豆记录后使用「清理所选商品回执」");
            }
            return false;
        }
        JSONArray orders = fetchOrders(interval);
        sameDay = receipt.optLong("day", -1L)
                == Math.floorDiv(System.currentTimeMillis() + 28800000L, 86400000L);
        // shortcut: 列表 orderNo 字段尚无实机样本；缺失时不确认，待真实响应验证协议字段。
        boolean confirmed = false;
        for (int i = 0; orders != null && i < orders.length() && !orderNo.isEmpty(); i++) {
            JSONObject order = orders.optJSONObject(i);
            if (order != null && orderNo.equals(order.optString("orderNo"))) confirmed = true;
        }
        // 无订单号时，需要同日该规格可兑次数减少及完整订单数增加两项证据。
        if (!confirmed && orders != null && sameDay && receipt.optInt("beforeCount", -1) >= 0
                && orders.length() > receipt.optInt("beforeCount")) {
            JSONArray items = fetchItems(interval);
            sameDay = receipt.optLong("day", -1L)
                    == Math.floorDiv(System.currentTimeMillis() + 28800000L, 86400000L);
            for (int i = 0; sameDay && items != null && i < items.length(); i++) {
                JSONObject current = items.optJSONObject(i);
                if (current != null && item.optString("spuId").equals(current.optString("spuId"))
                        && item.optString("skuId").equals(current.optString("skuId"))
                        && current.optInt("dayLeft", -1) >= 0
                        && current.optInt("dayLeft") < receipt.optInt("dayLeft", -1)) confirmed = true;
            }
        }
        if (!confirmed) {
            Log.goldenBeans("🕓 金豆商城[" + name + "]#已回查订单但兑换结果未确认，不重复下单"
                    + "；请核对商城订单和扣豆记录，再在金豆夺宝选择该商品，使用「清理所选商品回执」"
                    + (receipt.length() == 0 ? "（旧回执无基线）" : ""));
            return false;
        }
        if (sameDay) {
            Status.setIntFlagToday(FLAG_EXCHANGE_PREFIX + name,
                    Math.max(Status.getIntFlagToday(FLAG_EXCHANGE_PREFIX + name), receipt.optInt("doneBefore", 0) + 1));
        }
        boolean cleared = RuntimeInfo.getInstance().putVerified(receiptKey, "");
        orderSnapshot[0] = orders;
        Log.goldenBeans("✅ 金豆商城兑换[" + name + "]#订单已确认" + (sameDay ? "，已计入今日次数" : "，属于历史兑换")
                + (cleared ? "，未确认回执已解除" : "，回执清理失败，下轮只回查"));
        return true;
    }

    /** 完整分页才可作为基线；失败/重复页一律保留未确认状态。 */
    private static JSONArray fetchOrders(int interval) throws Exception {
        JSONArray all = new JSONArray();
        Set<String> pages = new HashSet<>();
        // shortcut: 最多查 100 页，超过时停止兑换；更大订单历史需服务端按订单号查询接口。
        for (int page = 1; page <= 100; page++) {
            GoldenBeansSupport.pause(interval);
            JSONObject jo = GoldenBeansSupport.parse(goldenbeansRpcCall.mallOrders(page, PAGE_SIZE));
            if (!GoldenBeansSupport.ok(jo)) return null;
            JSONArray orders = jo.optJSONArray("orderInfos");
            if (orders == null || !pages.add(orders.toString())) return null;
            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.optJSONObject(i);
                if (order == null) return null;
                all.put(order);
            }
            if (orders.length() < PAGE_SIZE) return all;
        }
        Log.goldenBeans("金豆商城⚠️订单超过100页，查询未完整，本轮不兑换");
        return null;
    }

    /** 当前持有的金豆罐数；查询失败返回 -1，此时不提交兑换。 */
    private static int jarCount(int interval) throws Exception {
        GoldenBeansSupport.pause(interval);
        JSONObject jo = GoldenBeansSupport.parse(goldenbeansRpcCall.home());
        if (!GoldenBeansSupport.ok(jo)) {
            return -1;
        }
        JSONObject jarInfo = GoldenBeansSupport.findObject(jo, "jarInfo");
        return nonNegativeInt(jarInfo, "jarCount");
    }
}
