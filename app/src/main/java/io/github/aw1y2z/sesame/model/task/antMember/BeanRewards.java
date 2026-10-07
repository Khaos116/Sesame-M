package io.github.aw1y2z.sesame.model.task.antMember;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.rpc.intervallimit.RpcRequestGuard;
import io.github.aw1y2z.sesame.data.RuntimeInfo;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.BeanRightIdMap;

final class BeanRewards {
    private static final String TAG = "BeanRewards";

    private static JSONObject response(String raw) {
        JSONObject root = MyUtils.newJSONObject(raw);
        JSONObject checked = AntMember.memberFeaturePayload(root);
        if (checked == null) return null;
        JSONObject result = checked.optJSONObject("result");
        if (result == null && checked != root) result = checked;
        if (result == null || RpcRequestGuard.isFailure(result)
                || result.has("success") && !Boolean.TRUE.equals(result.opt("success"))
                || result.has("resultCode") && !Set.of("SUCCESS", "100").contains(result.optString("resultCode"))) return null;
        return result;
    }

    private static Map<String, JSONObject> awards(JSONObject data) {
        JSONArray grades = data == null ? null : data.optJSONArray("gradeSkuAwardsList");
        if (grades == null || grades.length() > 50) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < grades.length(); i++) {
            JSONObject grade = grades.optJSONObject(i);
            JSONArray rows = grade == null ? null : grade.optJSONArray("skuAwardList");
            if (rows == null || rows.length() > 100) return null;
            for (int j = 0; j < rows.length(); j++) {
                JSONObject row = rows.optJSONObject(j);
                if (row == null || !(row.opt("skuId") instanceof String) || row.optString("skuId").isEmpty()
                        || !(row.opt("status") instanceof String) || row.optString("status").isEmpty()
                        || !(row.opt("spuType") instanceof String) || row.optString("spuType").isEmpty()) return null;
                if (result.put(row.optString("skuId"), row) != null || result.size() > 200) return null;
                if ("MARKETING_PRIZE".equals(row.optString("spuType")) && SesameAchievements.exactNonNegative(row.opt("beanQuantity")) < 0) return null;
            }
        }
        return result;
    }

    private static long balance() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = response(AntMemberRpcCall.queryAccountSummaryPoint());
        return data == null ? -1 : SesameAchievements.exactNonNegative(data.opt("effectPoint"));
    }

    private static void sign() throws Exception {
        TimeUtil.sleep(0);
        JSONObject state = response(AntMemberRpcCall.querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN"));
        String flag = "member::beanSignAttempt";
        if (state == null || !Boolean.TRUE.equals(state.opt("canPush")) || Status.hasFlagToday(flag)) return;
        TimeUtil.sleep(0);
        Status.flagToday(flag);
        JSONObject accepted = response(AntMemberRpcCall.signInTrigger("AP16242232", "INS_BLUE_BEAN_SIGN"));
        TimeUtil.sleep(0);
        JSONObject after = response(AntMemberRpcCall.querySignInProcess("AP16242232", "INS_BLUE_BEAN_SIGN"));
        if (accepted != null && after != null && Boolean.FALSE.equals(after.opt("canPush"))) Log.other("安心豆🫘签到状态回查成功");
        else Log.record("安心豆：签到未确认，当天不重复");
    }

    private static void guardian() throws Exception {
        TimeUtil.sleep(0);
        Map<String, JSONObject> snapshot = awards(response(AntMemberRpcCall.queryGuardianGradeAwards()));
        if (snapshot == null) return;
        int attempts = 0;
        for (String id : snapshot.keySet()) {
            JSONObject award = snapshot.get(id);
            long count = SesameAchievements.exactNonNegative(award.opt("beanQuantity"));
            String flag = "member::beanGuardianAttempt::" + id;
            if (!"MARKETING_PRIZE".equals(award.optString("spuType")) || !"AVAILABLE".equals(award.optString("status"))
                    || count <= 0 || Status.hasFlagToday(flag)) continue;
            if (++attempts > 50) return;
            TimeUtil.sleep(0);
            Map<String, JSONObject> current = awards(response(AntMemberRpcCall.queryGuardianGradeAwards()));
            JSONObject fresh = current == null ? null : current.get(id);
            if (current == null) return;
            if (fresh == null || !"AVAILABLE".equals(fresh.optString("status"))
                    || !"MARKETING_PRIZE".equals(fresh.optString("spuType")) || count != SesameAchievements.exactNonNegative(fresh.opt("beanQuantity"))) continue;
            long before = balance();
            if (before < 0) return;
            TimeUtil.sleep(0);
            Status.flagToday(flag);
            JSONObject accepted = response(AntMemberRpcCall.guardianAwardSend(id));
            TimeUtil.sleep(0);
            Map<String, JSONObject> after = awards(response(AntMemberRpcCall.queryGuardianGradeAwards()));
            JSONObject remaining = after == null ? null : after.get(id);
            long now = balance();
            if (accepted == null || after == null || remaining != null && ("AVAILABLE".equals(remaining.optString("status"))
                    || !"MARKETING_PRIZE".equals(remaining.optString("spuType")) || count != SesameAchievements.exactNonNegative(remaining.opt("beanQuantity")))
                    || now < before || now - before != count) {
                Log.record("安心豆：等级奖励未确认，当天不重复[" + id + "]");
                return;
            }
            Log.other("安心豆🫘等级奖励回查成功[" + count + "豆]");
        }
    }

    private static Map<String, JSONObject> browseTasks() throws Exception {
        TimeUtil.sleep(0);
        JSONObject data = response(AntMemberRpcCall.beanTaskCenterConsult());
        JSONArray pending = data == null ? null : data.optJSONArray("taskDetailList");
        if (pending == null || pending.length() > 100) return null;
        JSONArray done = data.optJSONArray("doneTaskDetailList");
        if (data.has("doneTaskDetailList") && done == null || done != null && done.length() > 100) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (JSONArray rows : new JSONArray[]{pending, done}) {
            for (int i = 0; rows != null && i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null || !(row.opt("taskId") instanceof String) || row.optString("taskId").isEmpty()
                        || !(row.opt("taskProcessStatus") instanceof String) || row.optString("taskProcessStatus").isEmpty()
                        || row.has("taskOrderId") && !(row.opt("taskOrderId") instanceof String)
                        || result.put(row.optString("taskId"), row) != null) return null;
            }
        }
        return result;
    }

    private static boolean browseOrderConfirmed(JSONObject task, String order) {
        if (task == null || order.isEmpty()) return false;
        JSONArray rows = task.optJSONArray("sendPrizeSendOrderList");
        if (rows == null || rows.length() > 100) return false;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i), ext = row == null ? null : row.optJSONObject("extInfo");
            if (row != null && "SUCCESS".equals(row.optString("sendStatus")) && ext != null
                    && ext.opt("TASK_ORDER_ID") instanceof String && order.equals(ext.optString("TASK_ORDER_ID"))) return true;
        }
        return false;
    }

    private static boolean beanBrowseContract(JSONObject task) {
        if (task == null || !(task.opt("taskCenterId") instanceof String) || !"AP15241780".equals(task.optString("taskCenterId"))) return false;
        JSONObject display = task.optJSONObject("taskDisplayInfo"), custom = display == null ? null : display.optJSONObject("customInfo");
        JSONObject config = task.optJSONObject("taskConfig");
        return custom != null && "BROWSE_PAGE".equals(custom.optString("taskType")) && "BROWSE_TASK".equals(custom.optString("taskOperationType"))
                && !"TRANSFER".equals(task.optString("taskCategory")) && !"TRANSFER".equals(custom.optString("taskCategorize"))
                && !Set.of("ISSUED_TASK", "EXPLAIN_INTELLIGENCE").contains(task.optString("taskMainType"))
                && config != null && config.opt("appletId") instanceof String && !config.optString("appletId").isEmpty();
    }

    static void runBrowse(Set<String> blacklist) {
        try {
            Map<String, JSONObject> tasks = browseTasks();
            if (tasks == null) return;
            int attempts = 0;
            for (String id : new ArrayList<>(tasks.keySet())) {
                JSONObject task = tasks.get(id);
                if (!beanBrowseContract(task)) continue;
                JSONObject display = task.optJSONObject("taskDisplayInfo"), custom = display == null ? null : display.optJSONObject("customInfo");
                JSONObject config = task.optJSONObject("taskConfig");
                if (blacklist.contains(id) || blacklist.contains(custom.optString("taskMainTitle"))) continue;
                String applet = config.optString("appletId"), key = "memberBeanBrowseOrder::AP15241780::" + id;
                TimeUtil.sleep(0);
                RuntimeInfo runtime = RuntimeInfo.getInstance();
                String saved = runtime.getString(key), serverOrder = task.optString("taskOrderId");
                if (!saved.isEmpty() && browseOrderConfirmed(task, saved)) {
                    if (!runtime.putVerified(key, null)) return;
                    continue;
                }
                String status = task.optString("taskProcessStatus");
                if (!Set.of("NONE_SIGNUP", "SIGNUP_COMPLETE", "TODO", "NOT_DONE", "WAIT_COMPLETE").contains(status)) continue;
                if (!saved.isEmpty() && serverOrder.isEmpty() && "NONE_SIGNUP".equals(status)) {
                    Log.record("安心豆浏览：旧订单仍待确认，新报名保留[" + id + "]");
                    return;
                }
                if (!saved.isEmpty() && !serverOrder.isEmpty() && !saved.equals(serverOrder)) {
                    Log.record("安心豆浏览：已保存订单与服务端不一致，保留待查[" + id + "]");
                    return;
                }
                String order = saved.isEmpty() ? serverOrder : saved;
                if (order.isEmpty()) {
                    String signupFlag = "member::beanBrowseSignupAttempt::" + id;
                    if (!"NONE_SIGNUP".equals(status) || Status.hasFlagToday(signupFlag)) continue;
                    if (++attempts > 50) return;
                    TimeUtil.sleep(0);
                    Status.flagToday(signupFlag);
                    JSONObject signed = response(AntMemberRpcCall.beanTaskTrigger(applet, "signup"));
                    if (signed == null || !(signed.opt("taskOrderId") instanceof String) || signed.optString("taskOrderId").isEmpty()) return;
                    order = signed.optString("taskOrderId");
                    if (!runtime.putVerified(key, order)) return;
                    tasks = browseTasks();
                    task = tasks == null ? null : tasks.get(id);
                    config = task == null ? null : task.optJSONObject("taskConfig");
                    if (!beanBrowseContract(task) || !applet.equals(config.optString("appletId"))
                            || !order.equals(task.optString("taskOrderId"))) return;
                } else if (saved.isEmpty() && !runtime.putVerified(key, order)) return;
                if (!beanBrowseContract(task) || blacklist.contains(id)) return;
                custom = task.optJSONObject("taskDisplayInfo").optJSONObject("customInfo");
                if (blacklist.contains(custom.optString("taskMainTitle"))) return;
                String sendFlag = "member::beanBrowseSendAttempt::" + id + "::" + order;
                if (Status.hasFlagToday(sendFlag)) continue;
                if (++attempts > 50) return;
                TimeUtil.sleep(0);
                Status.flagToday(sendFlag);
                JSONObject accepted = response(AntMemberRpcCall.beanTaskTrigger(applet, "send"));
                if (accepted == null || accepted.has("taskOrderId") && (!(accepted.opt("taskOrderId") instanceof String) || !order.equals(accepted.optString("taskOrderId")))) return;
                tasks = browseTasks();
                JSONObject after = tasks == null ? null : tasks.get(id);
                JSONObject afterConfig = after == null ? null : after.optJSONObject("taskConfig");
                if (afterConfig == null || !applet.equals(afterConfig.optString("appletId"))
                        || !"AP15241780".equals(after.optString("taskCenterId")) || !browseOrderConfirmed(after, order)) {
                    Log.record("安心豆浏览：同订单发奖未确认，保留订单且当天不重复[" + id + "]");
                    return;
                }
                if (!runtime.putVerified(key, null)) return;
                Log.other("安心豆🫘浏览发奖订单回查成功[" + id + "]");
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "runBrowse", t); }
    }

    private static String beanRightId(JSONObject row) {
        if (row == null) return "";
        String id = "";
        for (String field : new String[]{"rightsId", "rightsCode", "itemId", "id"}) {
            if (!row.has(field)) continue;
            if (!(row.opt(field) instanceof String)) return "";
            String value = row.optString(field);
            if (value.length() > 512) return "";
            if (!id.isEmpty() && "rightsCode".equals(field) && !value.isEmpty() && !id.equals(value)) return "";
            if (id.isEmpty()) id = value;
        }
        return id;
    }

    private static List<JSONObject> beanRightRows(JSONObject data) {
        if (data == null) return null;
        List<JSONObject> result = new ArrayList<>();
        boolean found = false;
        for (String field : new String[]{"preExchangeDetailList", "couponRightsDTOList", "rightsList", "flowList"}) {
            if (!data.has(field)) continue;
            JSONArray rows = data.optJSONArray(field);
            if (rows == null || rows.length() > 100) return null;
            found = true;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (beanRightId(row).isEmpty()) return null;
                result.add(row);
            }
        }
        return found ? result : null;
    }

    private static Map<String, JSONObject> beanCatalogue() throws Exception {
        TimeUtil.sleep(0);
        JSONObject properties = response(AntMemberRpcCall.filterValidBizProperty());
        JSONArray categories = properties == null ? null : properties.optJSONArray("validBizPropertyList");
        if (categories == null || categories.length() > 20) return null;
        List<String> codes = new ArrayList<>();
        codes.add("");
        for (int i = 0; i < categories.length(); i++) {
            JSONObject category = categories.optJSONObject(i);
            if (category == null || !(category.opt("bizPropertyCode") instanceof String) || category.optString("bizPropertyCode").isEmpty()) return null;
            if (!codes.contains(category.optString("bizPropertyCode"))) codes.add(category.optString("bizPropertyCode"));
        }
        if (codes.size() > 20) return null;
        Map<String, JSONObject> result = new LinkedHashMap<>();
        int requests = 0;
        for (int c = 0; c <= codes.size(); c++) {
            int offset = 0;
            Set<String> seen = new HashSet<>();
            while (true) {
                // ponytail: 最多20分类/50页；不完整时保留旧目录，停止本轮兑换。
                if (++requests > 50) return null;
                TimeUtil.sleep(0);
                JSONObject data = response(c == codes.size() ? AntMemberRpcCall.queryRightsPreExchangeFlows(offset) : AntMemberRpcCall.rightsRecommend(offset, codes.get(c)));
                List<JSONObject> rows = beanRightRows(data);
                if (rows == null || !(data.opt("hasNext") instanceof Boolean)) return null;
                int previous = seen.size();
                for (JSONObject row : rows) {
                    String id = beanRightId(row);
                    seen.add(id);
                    JSONObject old = result.get(id);
                    if (old != null && (!old.optString("assetAmount").equals(row.optString("assetAmount"))
                            || !old.optString("pointNum").equals(row.optString("pointNum"))
                            || !old.optString("rightsMetaSubType").equals(row.optString("rightsMetaSubType")))) return null;
                    result.putIfAbsent(id, row);
                }
                if (result.size() > 2000) return null;
                if (!Boolean.TRUE.equals(data.opt("hasNext"))) break;
                long next = SesameAchievements.exactNonNegative(data.opt("pageEndIndex"));
                if (next <= offset || next > Integer.MAX_VALUE || seen.size() == previous) return null;
                offset = (int) next;
            }
        }
        return result;
    }

    private static long beanRightCost(JSONObject row) {
        if (row == null || beanRightId(row).isEmpty() || !"OTHER".equals(row.optString("showType"))
                || !(row.opt("cash") instanceof Number) && !(row.opt("cash") instanceof String)
                || SesameAchievements.exactNonNegative(row.opt("lackPointNum")) != 0) return -1;
        for (String key : new String[]{"rightsMetaSubType", "rightsSubType"})
            if (row.has(key) && !(row.opt(key) instanceof String)) return -1;
        String subtype = row.optString("rightsMetaSubType", row.optString("rightsSubType"));
        if (row.has("rightsMetaSubType") && row.has("rightsSubType") && !row.optString("rightsMetaSubType").equals(row.optString("rightsSubType"))) return -1;
        JSONObject request = row.optJSONObject("exchangeRequest"), ext = row.optJSONObject("extInfo");
        if (row.has("exchangeRequest") && request == null || row.has("extInfo") && ext == null) return -1;
        Object order = row.has("needOrder") ? row.opt("needOrder") : request == null ? null : request.opt("needOrder");
        Object amount = row.has("assetAmount") ? row.opt("assetAmount") : request != null && request.has("assetAmount") ? request.opt("assetAmount") : row.opt("pointNum");
        long price = SesameAchievements.exactNonNegative(amount);
        if (SesameAchievements.exactNonNegative(order) != 0 || price <= 0
                || row.has("pointNum") && SesameAchievements.exactNonNegative(row.opt("pointNum")) != price
                || request != null && request.has("assetAmount") && SesameAchievements.exactNonNegative(request.opt("assetAmount")) != price
                || request != null && request.has("needOrder") && SesameAchievements.exactNonNegative(request.opt("needOrder")) != 0) return -1;
        if (row.has("status") && (!(row.opt("status") instanceof String) || !Set.of("", "AVAILABLE").contains(row.optString("status")))) return -1;
        for (JSONObject obj : new JSONObject[]{row, request, ext}) {
            if (obj == null) continue;
            for (String field : new String[]{"cash", "yuan", "cashAmount", "cashPrice", "moneyPrice", "channelPrice"}) {
                if (!obj.has(field)) continue;
                Object value = obj.opt(field);
                if (!(value instanceof Number) && !(value instanceof String) || value.toString().length() > 64) return -1;
                try { if (new java.math.BigDecimal(value.toString()).signum() != 0) return -1; }
                catch (NumberFormatException e) { return -1; }
            }
        }
        String text = (row.optString("title") + row.optString("simpleTitle") + row.optString("rightsName") + row.optString("itemName")
                + row.optString("rightsUseLink") + row.optString("jumpUrl") + row.optString("supplyType") + (ext == null ? "" : ext.toString())).toLowerCase(Locale.ROOT);
        text += subtype.toLowerCase(Locale.ROOT);
        for (String word : new String[]{"http:", "https:", "alipays:", "下单", "实付", "支付", "邮寄", "快递", "订单", "邮费", "实物", "商品", "优惠券", "券", "小程序", "goods", "cash", "order", "online_shopping", "coupon"})
            if (text.contains(word)) return -1;
        return price;
    }

    private static long beanHistoryCount(String id) throws Exception {
        long count = 0;
        int offset = 0;
        Set<String> pages = new HashSet<>();
        for (int page = 0; page < 50; page++) {
            TimeUtil.sleep(0);
            JSONObject data = response(AntMemberRpcCall.queryRightsExchangeFlows(offset));
            List<JSONObject> rows = beanRightRows(data);
            if (rows == null || !(data.opt("hasNext") instanceof Boolean)) return -1;
            for (JSONObject row : rows) if (id.equals(beanRightId(row))) {
                long total = SesameAchievements.exactNonNegative(row.opt("exchangeTotalNum"));
                if (total < 0) return -1;
                count = Math.max(count, total);
            }
            if (!Boolean.TRUE.equals(data.opt("hasNext"))) return count;
            long next = SesameAchievements.exactNonNegative(data.opt("pageEndIndex"));
            if (rows.isEmpty() || next <= offset || next > Integer.MAX_VALUE || !pages.add(rows.toString())) return -1;
            offset = (int) next;
        }
        return -1;
    }

    static void runExchange(Set<String> selected, int budget) {
        try {
            TimeUtil.sleep(0);
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) return;
            int day = beanExchangeDay();
            BeanRightIdMap.load(uid);
            if (!Status.hasFlagToday("member::beanCatalogue")) {
                Map<String, JSONObject> catalogue = beanCatalogue();
                if (catalogue == null) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                for (Map.Entry<String, JSONObject> entry : catalogue.entrySet()) {
                    JSONObject item = entry.getValue();
                    String title = item.optString("title", item.optString("simpleTitle", item.optString("rightsName", entry.getKey())));
                    BeanRightIdMap.add(entry.getKey(), title + "（" + item.optString("assetAmount", item.optString("pointNum", "?")) + "安心豆）"
                            + (beanRightCost(item) < 0 ? "[仅查看]" : ""));
                }
                if (!BeanRightIdMap.save(uid)) return;
                Status.flagToday("member::beanCatalogue");
            }
            if (selected == null || selected.isEmpty() || budget <= 0) return;
            int attempts = 0;
            for (String id : new ArrayList<>(selected)) {
                if (++attempts > 50) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                String flag = "member::beanExchangeAttempt::" + id;
                if (id == null || id.isEmpty() || !BeanRightIdMap.getMap().containsKey(id) || Status.hasFlagToday(flag)) continue;
                String receiptKey = "member::beanExchangeReceipt::" + id;
                if (!RuntimeInfo.getInstance().getString(receiptKey).isEmpty()) continue;
                TimeUtil.sleep(0);
                JSONObject detail = response(AntMemberRpcCall.queryRightsDetail(id));
                if (!id.equals(beanRightId(detail))) continue;
                long price = beanRightCost(detail), total = SesameAchievements.exactNonNegative(detail.opt("exchangeTotalNum"));
                int spent = Status.getIntFlagToday("member::beanExchangeSpent");
                if (price < 0 || total < 0 || total == Long.MAX_VALUE || spent < 0 || price > budget - (long) spent) continue;
                if (beanHistoryCount(id) != total) return;
                long before = balance();
                if (before < price) return;
                TimeUtil.sleep(0);
                JSONObject fresh = response(AntMemberRpcCall.queryRightsDetail(id));
                if (!id.equals(beanRightId(fresh)) || beanRightCost(fresh) != price
                        || SesameAchievements.exactNonNegative(fresh.opt("exchangeTotalNum")) != total) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                JSONObject receipt = MyUtils.newJSONObject().put("uid", uid).put("day", day).put("rightsId", id)
                        .put("price", price).put("before", before).put("count", total);
                if (!RuntimeInfo.getInstance().putVerified(receiptKey, receipt.toString())) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                Status.setIntFlagToday("member::beanExchangeSpent", spent + (int) price);
                Status.flagToday(flag);
                JSONObject accepted = response(AntMemberRpcCall.rightsExchange(id, (int) price));
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                long now = balance(), history = beanHistoryCount(id);
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()) return;
                JSONObject after = response(AntMemberRpcCall.queryRightsDetail(id));
                if (accepted == null || now != before - price || history != total + 1 || !id.equals(beanRightId(after))
                        || SesameAchievements.exactNonNegative(after.opt("exchangeTotalNum")) != total + 1) {
                    Log.record("安心豆兑换：详情/记录/扣减未同时确认，预算及回执保留且不重复[" + id + "]");
                    return;
                }
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid()) || day != beanExchangeDay()
                        || !RuntimeInfo.getInstance().putVerified(receiptKey, null)) return;
                Log.other("安心豆🛒原生纯豆权益兑换记录新增与扣减回查成功[" + price + "豆]，实际生效以支付宝为准");
            }
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "runExchange", t); }
    }

    private static int beanExchangeDay() {
        Calendar c = MyUtils.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static boolean quizCompleted(JSONObject question) {
        return question != null && ("SENT".equals(question.optString("awardStatus"))
                || Set.of("ANSWERED", "ANSWER_SUCCESS", "SUCCESS", "ANSWER_COMPLETED", "COMPLETED").contains(question.optString("answerResult")));
    }

    private static Map<String, JSONObject> quizQuestions(JSONObject data) {
        JSONArray rows = data == null ? null : data.optJSONArray("userQuestionDramaAnswers");
        if (rows == null || rows.length() > 20 || !(data.opt("dramaId") instanceof String) || data.optString("dramaId").isEmpty()
                || data.has("nextScriptId") && !(data.opt("nextScriptId") instanceof String)) return null;
        List<JSONObject> sorted = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !(row.opt("scriptId") instanceof String) || row.optString("scriptId").isEmpty()
                    || !ids.add(row.optString("scriptId")) || !(row.opt("userDramaId") instanceof String) || row.optString("userDramaId").isEmpty()
                    || row.has("answerResult") && !(row.opt("answerResult") instanceof String)
                    || row.has("awardStatus") && !(row.opt("awardStatus") instanceof String)
                    || SesameAchievements.exactNonNegative(row.opt("sort")) < 0) return null;
            if (!quizCompleted(row) && (!(row.opt("rightAnswer") instanceof String) || row.optString("rightAnswer").isEmpty()
                    || !Set.of("", "UNANSWERED", "ANSWER_PENDING", "PENDING").contains(row.optString("answerResult")))) return null;
            sorted.add(row);
        }
        sorted.sort(Comparator.comparingLong(row -> SesameAchievements.exactNonNegative(row.opt("sort"))));
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (JSONObject row : sorted) result.put(row.optString("scriptId"), row);
        String next = data.optString("nextScriptId");
        if (!next.isEmpty() && (!result.containsKey(next) || quizCompleted(result.get(next)))) return null;
        return result;
    }

    static void runQuiz() {
        try {
            TimeUtil.sleep(0);
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) return;
            JSONObject consult = response(AntMemberRpcCall.guardianAnswerConsult());
            if (consult == null || !"ANSWER_PENDING".equals(consult.optString("answerStatus"))) return;
            TimeUtil.sleep(0);
            JSONObject data = response(AntMemberRpcCall.queryUserQuestionDrama(uid));
            Map<String, JSONObject> questions = quizQuestions(data);
            if (questions == null) return;
            String drama = data.optString("dramaId");
            boolean progressed = false;
            for (int count = 0; count < 20; count++) {
                String id = data.optString("nextScriptId");
                if (id.isEmpty()) {
                    for (String candidate : questions.keySet()) if (!quizCompleted(questions.get(candidate))) { id = candidate; break; }
                }
                if (id.isEmpty()) break;
                JSONObject question = questions.get(id);
                String flag = "member::beanQuizAttempt::" + drama + "::" + id;
                if (question == null || quizCompleted(question) || Status.hasFlagToday(flag)) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid())) return;
                JSONObject freshData = response(AntMemberRpcCall.queryUserQuestionDrama(uid));
                Map<String, JSONObject> freshQuestions = quizQuestions(freshData);
                JSONObject fresh = freshQuestions == null ? null : freshQuestions.get(id);
                if (fresh == null || !drama.equals(freshData.optString("dramaId")) || quizCompleted(fresh)
                        || !question.optString("userDramaId").equals(fresh.optString("userDramaId"))
                        || !question.optString("rightAnswer").equals(fresh.optString("rightAnswer"))
                        || !freshData.optString("nextScriptId").isEmpty() && !id.equals(freshData.optString("nextScriptId"))) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid())) return;
                Status.flagToday(flag);
                // The AG protocol records a known correct answer; neither ACK nor allCorrect proves server progress.
                JSONObject recorded = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.addAskAnswerRecord(id, uid)));
                if (recorded == null) return;
                TimeUtil.sleep(0);
                if (!uid.equals(UserIdMap.getCurrentUid())) return;
                JSONObject answered = AntMember.memberFeaturePayload(MyUtils.newJSONObject(AntMemberRpcCall.answerQuestionDrama(drama, id, fresh.optString("userDramaId"), uid)));
                if (answered == null) return;
                TimeUtil.sleep(0);
                data = response(AntMemberRpcCall.queryUserQuestionDrama(uid));
                questions = quizQuestions(data);
                JSONObject after = questions == null ? null : questions.get(id);
                if (after == null || !drama.equals(data.optString("dramaId")) || !quizCompleted(after)
                        || !fresh.optString("userDramaId").equals(after.optString("userDramaId"))) {
                    Log.record("安心豆闯关：同题进度未确认，当天不重复");
                    return;
                }
                progressed = true;
            }
            if (!progressed) return;
            TimeUtil.sleep(0);
            JSONObject after = response(AntMemberRpcCall.guardianAnswerConsult());
            if (after != null && Set.of("ANSWERED", "ANSWER_SUCCESS", "RECEIVE_SUCCESS", "ANSWER_COMPLETED").contains(after.optString("answerStatus")))
                Log.other("安心豆🫘保险知识闯关闭环回查完成");
            else Log.record("安心豆闯关：逐题进度已推进，最终完成状态未确认");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "runQuiz", t); }
    }

    static void runDraw() {
        try {
            TimeUtil.sleep(0);
            String flag = "member::beanDrawAttempt", plan = "INSP29990111";
            if (Status.hasFlagToday(flag)) return;
            JSONObject camp = response(AntMemberRpcCall.beanCampConsult(plan));
            if (camp == null || !Boolean.TRUE.equals(camp.opt("consultResult"))
                    || !(camp.opt("campId") instanceof String) || camp.optString("campId").isEmpty()) return;
            String id = camp.optString("campId");
            if (balance() < 1) return;
            TimeUtil.sleep(0);
            JSONObject fresh = response(AntMemberRpcCall.beanCampConsult(plan));
            if (fresh == null || !Boolean.TRUE.equals(fresh.opt("consultResult"))
                    || !(fresh.opt("campId") instanceof String) || !id.equals(fresh.optString("campId"))) return;
            long before = balance();
            if (before < 1) return;
            TimeUtil.sleep(0);
            Status.flagToday(flag);
            JSONObject drawn = response(AntMemberRpcCall.beanTriggerDrawPrize(id, plan, System.currentTimeMillis()));
            long after = balance();
            if (drawn == null || !(drawn.opt("triggerResult") instanceof Boolean) || after != before - 1) {
                Log.record("安心豆抽奖：结果或1豆扣减未确认，当天不重复");
                return;
            }
            if (Boolean.TRUE.equals(drawn.opt("triggerResult"))) {
                Object amount = drawn.opt("prizeAmount");
                if (!(amount instanceof Number) && !(amount instanceof String) || amount.toString().length() > 64) return;
                java.math.BigDecimal prize;
                try { prize = new java.math.BigDecimal(amount.toString()); }
                catch (NumberFormatException e) { return; }
                if (prize.signum() < 0 || prize.precision() > 16 || Math.abs((long) prize.scale()) > 16) return;
                Log.other("安心豆🎰抽奖扣减1豆回查成功，接口返回奖励[" + prize.toPlainString() + "元]");
            } else Log.other("安心豆🎰抽奖扣减1豆回查成功，接口返回未中奖");
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "runDraw", t); }
    }

    static void run() {
        try {
            sign();
            guardian();
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable t) { Log.err(TAG, "run", t); }
    }
}
