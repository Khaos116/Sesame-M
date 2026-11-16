package io.github.aw1y2z.sesame.model.task.antForest;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.MyUtils;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.VitalityBenefitIdMap;
import static io.github.aw1y2z.sesame.model.task.antForest.AntForestV2.*;

final class AntForestMall {

    private static final String TAG = AntForestMall.class.getSimpleName();
    private final AntForestV2 host;

    AntForestMall(AntForestV2 host) {
        this.host = host;
    }

    /**
     * 获取活力值商店所有商品信息：按 5 个 labelType 分别拉取并用响应里的 hasMore 翻页
     * （只查 SC_ASSETS 首页会漏掉大部分权益）。
     * <p>当日标记的判断放在方法内部，因为除了 vitalityExchangeBenefit() 外，
     * useEnergyRainCard()/兑换道具等路径也会直接调用它，那些路径同样需要"当日只拉一次"。
     *
     * @return 是否真的取到 SKU（取不到时留给下一轮重试，但当日重试次数有上限）
     */
    boolean getAllSkuInfo() {
        try {
            if (Status.hasFlagToday("forest::vitalitySkuList") && !host.skuInfo.isEmpty()) {
                return true;
            }
            // 重启后要重建内存清单，旧日标记不能证明本次拉取完整。
            Status.clearFlag("forest::vitalitySkuList");
            // 失败重试上限：服务端持续失败时避免每轮都重拉（单轮最多 5 分类 × 10 页）
            int retries = Status.getIntFlagToday(AntForestV2.FLAG_VITALITY_SKU_RETRY);
            // 各入口共用失败后的冷却，避免同轮用完重试次数。
            Long lastFailureNanos = host.vitalitySkuLastFailureNanos;
            if (retries >= AntForestV2.VITALITY_SKU_RETRY_MAX || retries > 0 && lastFailureNanos != null
                    && System.nanoTime() - lastFailureNanos < TimeUnit.MINUTES.toNanos(5)) {
                return false;
            }
            int got = 0;
            boolean gotSku = false;
            boolean complete = true;
            String uid = UserIdMap.getCurrentUid();
            if (VitalityBenefitIdMap.getMap().isEmpty()) VitalityBenefitIdMap.load(uid);
            Set<String> previous = new HashSet<>(VitalityBenefitIdMap.getMap().keySet());
            for (String labelType : AntForestV2.VITALITY_LABEL_TYPES) {
                int cnt = 0;
                for (int page = 0; page < AntForestV2.VITALITY_ITEM_MAX_PAGES; page++) {
                    JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.itemList(labelType, page * AntForestRpcCall.VITALITY_ITEM_PAGE_SIZE));
                    if (!MessageUtil.checkSuccess(TAG, jo)) {
                        complete = false;
                        break;
                    }
                    JSONArray itemInfoVOList = optItemInfoVOList(jo);
                    if (itemInfoVOList == null) {
                        complete = false;
                        break;
                    }
                    if (itemInfoVOList.length() == 0) break;
                    for (int i = 0; i < itemInfoVOList.length(); i++) {
                        JSONObject itemInfoVO = itemInfoVOList.optJSONObject(i);
                        if (itemInfoVO != null) {
                            gotSku |= getSkuInfoByItemInfoVO(itemInfoVO);
                            cnt++;
                        }
                    }
                    if (!hasMore(jo)) {
                        break;
                    }
                    if (page == AntForestV2.VITALITY_ITEM_MAX_PAGES - 1) complete = false;
                }
                got += cnt;
            }
            VitalityBenefitIdMap.save(uid);
            Set<String> added = new HashSet<>(VitalityBenefitIdMap.getMap().keySet());
            added.removeAll(previous);
            if (!added.isEmpty()) Log.i("活力值商店列表：新增" + added.size() + "项，清单共" + VitalityBenefitIdMap.getMap().size() + "条");
            if (gotSku && complete) {
                host.vitalitySkuLastFailureNanos = null;
                if (retries > 0) Status.setIntFlagToday(AntForestV2.FLAG_VITALITY_SKU_RETRY, 0);
                Status.flagToday("forest::vitalitySkuList");
                return true;
            }
            accountVitalitySkuRetry();
            return false;
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "getAllSkuInfo err:", th);
            accountVitalitySkuRetry();
            return false;
        }
    }

    /** 活力值商店列表拉取失败计数：达上限后当日不再重拉，避免每轮都重试 */
    void accountVitalitySkuRetry() {
        host.vitalitySkuLastFailureNanos = System.nanoTime();
        int tries = Status.getIntFlagToday(AntForestV2.FLAG_VITALITY_SKU_RETRY) + 1;
        Status.setIntFlagToday(AntForestV2.FLAG_VITALITY_SKU_RETRY, tries);
        if (tries >= AntForestV2.VITALITY_SKU_RETRY_MAX) {
            Log.i("活力值商店列表：连续" + tries + "次未取到，今日不再重试");
        }
    }

    /** 商品列表可能在顶层、也可能在 resData 下（抓包两层都出现过），两层都取 */
    static JSONArray optItemInfoVOList(JSONObject jo) {
        JSONArray list = jo.optJSONArray("itemInfoVOList");
        if (list == null) {
            JSONObject resData = jo.optJSONObject("resData");
            if (resData != null) {
                list = resData.optJSONArray("itemInfoVOList");
            }
        }
        return list;
    }

    /** hasMore 表示还有下一页，同样两层都取 */
    static boolean hasMore(JSONObject jo) {
        if (jo.has("hasMore")) {
            return jo.optBoolean("hasMore", false);
        }
        JSONObject resData = jo.optJSONObject("resData");
        return resData != null && resData.optBoolean("hasMore", false);
    }

    void getSkuInfoBySpuId(String spuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.itemDetail(spuId));
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return;
            }
            JSONObject spuItemInfoVo = jo.optJSONObject("spuItemInfoVO");
            if (spuItemInfoVo != null) {
                getSkuInfoByItemInfoVO(spuItemInfoVo);
                VitalityBenefitIdMap.save(UserIdMap.getCurrentUid());
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "getSkuInfoBySpuId err:", th);
        }
    }

    /** 单个 SPU 的 SKU 入表；落盘由调用方整批结束后统一 save（原先每个 SPU 都写一次文件） */
    boolean getSkuInfoByItemInfoVO(JSONObject spuItem) {
        try {
            if (!(spuItem.opt("spuId") instanceof String)) return false;
            String spuId = spuItem.optString("spuId");
            JSONArray skuModelList = spuItem.optJSONArray("skuModelList");
            if (spuId.isEmpty() || skuModelList == null) {
                return false;
            }
            boolean got = false;
            for (int i = 0; i < skuModelList.length(); i++) {
                JSONObject skuModel = skuModelList.optJSONObject(i);
                if (skuModel == null) {
                    continue;
                }
                if (!(skuModel.opt("skuId") instanceof String)) continue;
                String skuId = skuModel.optString("skuId");
                if (skuId.isEmpty()) {
                    continue;
                }
                got = true;
                String skuName = skuModel.optString("skuName");
                if (!skuModel.has("spuId")) {
                    skuModel.put("spuId", spuId);
                }
                host.skuInfo.put(skuId, skuModel);
                VitalityBenefitIdMap.add(skuId, skuName);
            }
            return got;
        } catch (Throwable th) {
            Log.err(TAG, "getSkuInfoByItemInfoVO err:", th);
        }
        return false;
    }

    /*
     * 兑换活力值商品
     * sku
     * spuId, skuId, skuName, exchangedCount, price[amount]
     * exchangedCount == 0......
     */
    Boolean exchangeBenefit(String skuId) {
        if (skuId == null || skuId.trim().isEmpty() || ForestPropSupport.hasUnconfirmedRefill(skuId)
                || Status.hasFlagToday("forest::exchangeSkuMissing::" + skuId)
                || Status.hasFlagToday("forest::exchangeLimit::" + skuId)) return false;
        if (host.skuInfo.isEmpty() || !Status.hasFlagToday("forest::vitalitySkuList")) {
            getAllSkuInfo();
        }
        JSONObject sku = host.skuInfo.get(skuId);
        if (sku == null) {
            if (host.skuInfo.isEmpty() || !Status.hasFlagToday("forest::vitalitySkuList")) return false;
            // 失效 skuId（权益下架/换号/服务端改 ID）：打当日标记，当天不再重试；
            // 否则每轮都会遍历到它并刷一条日志，既刷屏又拖累后续任务
            if (!Status.hasFlagToday("forest::exchangeSkuMissing::" + skuId)) {
                Log.record("活力兑换🎐找不到要兑换的权益！" + skuId + "#今日不再重试");
                Status.flagToday("forest::exchangeSkuMissing::" + skuId);
            }
            return false;
        }
        try {
            String skuName = sku.optString("skuName");
            JSONArray itemStatusList = sku.optJSONArray("itemStatusList");
            for (int i = 0; itemStatusList != null && i < itemStatusList.length(); i++) {
                String itemStatus = itemStatusList.optString(i);
                if (ItemStatus.REACH_LIMIT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_POINT.name().equals(itemStatus) || ItemStatus.NO_ENOUGH_STOCK.name().equals(itemStatus)) {
                    Log.record("活力兑换🎐[" + skuName + "]停止:" + ItemStatus.valueOf(itemStatus).nickName());
                    if (ItemStatus.REACH_LIMIT.name().equals(itemStatus)) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                    }
                    return false;
                }
            }
            String spuId = sku.optString("spuId");
            if (exchangeBenefit(spuId, skuId, skuName)) {
                return true;
            }
            getSkuInfoBySpuId(spuId);
        } catch (TaskCancelledException e) { throw e;
        } catch (Throwable th) {
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    public static Boolean exchangeBenefit(String spuId, String skuId, String skuName) {
        try {
            if (exchangeBenefit(spuId, skuId)) {
                Status.vitalityExchangeBenefitToday(skuId);
                int exchangedCount = Status.getVitalityExchangeBenefitCountToday(skuId);
                Log.forest("活力兑换🎐[" + skuName + "]#第" + exchangedCount + "次");
                return true;
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }

    static Boolean exchangeBenefit(String spuId, String skuId) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AntForestRpcCall.exchangeBenefit(spuId, skuId));
            if (jo.has("errorMessage")) {
                String errorMessage = jo.optString("errorMessage");
                // 系统繁忙是临时故障：按次计数，用尽才当日停（见 VITALITY_EXCHANGE_RETRY_MAX）
                if (errorMessage.equals("系统繁忙，请稍后再试。")) {
                    String retryKey = "forest::exchangeRetry::" + skuId;
                    int tries = Status.getIntFlagToday(retryKey) + 1;
                    Status.setIntFlagToday(retryKey, tries);
                    if (tries >= AntForestV2.VITALITY_EXCHANGE_RETRY_MAX) {
                        Status.flagToday("forest::exchangeLimit::" + skuId);
                        Log.record("活力兑换🎐系统繁忙连续" + tries + "次，今日不再重试");
                    }
                }
            }
            return MessageUtil.checkResultCode(TAG, jo);
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.err(TAG, "exchangeBenefit err:", th);
        }
        return false;
    }
}
