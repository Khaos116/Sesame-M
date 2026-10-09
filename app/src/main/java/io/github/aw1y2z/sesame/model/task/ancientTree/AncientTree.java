package io.github.aw1y2z.sesame.model.task.ancientTree;

import io.github.aw1y2z.sesame.util.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AreaCode;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.TimeUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashSet;

public class AncientTree extends ModelTask {
    private static final String TAG = AncientTree.class.getSimpleName();

    @Override
    public String getName() {
        return "古树";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.PROTECT;
    }

    private BooleanModelField ancientTreeOnlyWeek;
    private SelectModelField ancientTreeCityCodeList;

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(ancientTreeOnlyWeek = new BooleanModelField("ancientTreeOnlyWeek", "仅星期一、三、五运行保护古树",
                false));
        modelFields.addField(ancientTreeCityCodeList = new SelectModelField("ancientTreeCityCodeList", "古树区划代码列表",
                new LinkedHashSet<>(), AreaCode::getList));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (!TaskCommon.IS_ENERGY_TIME && TaskCommon.IS_AFTER_8AM) {
            if (!ancientTreeOnlyWeek.getValue()) {
                return true;
            }
            int week = MyUtils.getInstance().get(Calendar.DAY_OF_WEEK);
            return week == Calendar.MONDAY || week == Calendar.WEDNESDAY || week == Calendar.FRIDAY;
        } return false;
    }

    @Override
    public void run() {
        try {
            Log.record("开始检测古树保护"); ancientTree(ancientTreeCityCodeList.getValue());
        } catch (Throwable t) {
            if (t instanceof TaskCancelledException) throw (TaskCancelledException) t;
            Log.i(TAG, "start.run err:"); Log.printStackTrace(TAG, t);
        }
    }

    private static void ancientTree(Collection<String> ancientTreeCityCodeList) {
        try {
            for (String cityCode : ancientTreeCityCodeList) {
                if (!Status.canAncientTreeToday(cityCode))
                    continue; ancientTreeProtect(cityCode); TimeUtil.sleep(1000L);
            }
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.i(TAG, "ancientTree err:"); Log.printStackTrace(TAG, th);
        }
    }

    private static void ancientTreeProtect(String cityCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AncientTreeRpcCall.homePage(cityCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return;
            } JSONObject data = jo.optJSONObject("data"); if (data == null || !data.has("districtBriefInfoList")) {
                return;
            } JSONArray districtBriefInfoList = data.optJSONArray("districtBriefInfoList");
            if (districtBriefInfoList == null) return;
            for (int i = 0; i < districtBriefInfoList.length(); i++) {
                JSONObject districtBriefInfo = districtBriefInfoList.optJSONObject(i);
                if (districtBriefInfo == null) {
                    return;
                }
                if (!districtBriefInfo.has("userCanProtectTreeNum")) continue;
                int userCanProtectTreeNum = new BigDecimal(String.valueOf(districtBriefInfo.opt("userCanProtectTreeNum"))).intValueExact();
                if (userCanProtectTreeNum < 0) return;
                if (userCanProtectTreeNum < 1)
                    continue; JSONObject districtInfo = districtBriefInfo.optJSONObject("districtInfo");
                if (districtInfo == null) {
                    return;
                }
                String districtCode = districtInfo.optString("districtCode");
                if (districtCode.isEmpty() || !districtDetail(districtCode)) return;
                TimeUtil.sleep(1000L);
            } Status.ancientTreeToday(cityCode);
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.i(TAG, "ancientTreeProtect err:"); Log.printStackTrace(TAG, th);
        }
    }

    private static boolean districtDetail(String districtCode) {
        try {
            JSONObject jo = MyUtils.newJSONObject(AncientTreeRpcCall.districtDetail(districtCode));
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            } JSONObject data = jo.optJSONObject("data"); if (data == null || !data.has("ancientTreeList")) {
                return false;
            } JSONObject districtInfo = data.optJSONObject("districtInfo");
            if (districtInfo == null) {
                return false;
            }
            String cityCode = districtInfo.optString("cityCode"); String cityName = districtInfo.optString("cityName");
            if (cityCode.isEmpty()) return false;
            String districtName = districtInfo.optString("districtName");
            JSONArray ancientTreeList = data.optJSONArray("ancientTreeList");
            if (ancientTreeList == null) return false;
            for (int i = 0; i < ancientTreeList.length(); i++) {
                JSONObject ancientTreeItem = ancientTreeList.optJSONObject(i);
                if (ancientTreeItem == null) return false;
                if (ancientTreeItem.has("hasProtected") && !(ancientTreeItem.opt("hasProtected") instanceof Boolean)) return false;
                if (ancientTreeItem.optBoolean("hasProtected"))
                    continue;
                JSONObject ancientTreeControlInfo = ancientTreeItem.optJSONObject("ancientTreeControlInfo");
                if (ancientTreeControlInfo == null) {
                    return false;
                }
                int quota = ancientTreeControlInfo.has("quota") ? new BigDecimal(String.valueOf(ancientTreeControlInfo.opt("quota"))).intValueExact() : 0;
                int useQuota = ancientTreeControlInfo.has("useQuota") ? new BigDecimal(String.valueOf(ancientTreeControlInfo.opt("useQuota"))).intValueExact() : 0;
                if (quota < 0 || useQuota < 0) return false;
                if (!ancientTreeControlInfo.has("quota") || !ancientTreeControlInfo.has("useQuota")) continue;
                if (quota <= useQuota)
                    continue; String itemId = ancientTreeItem.optString("projectId");
                if (itemId.isEmpty()) return false;
                JSONObject ancientTreeDetail = MyUtils.newJSONObject(AncientTreeRpcCall.projectDetail(itemId, cityCode));
                if (!MessageUtil.checkResultCode(TAG, ancientTreeDetail)) {
                    return false;
                }
                data = ancientTreeDetail.optJSONObject("data");
                if (data == null) return false;
                if (!data.has("canProtect")) continue;
                if (!(data.opt("canProtect") instanceof Boolean)) return false;
                if (data.optBoolean("canProtect")) {
                    if (!data.has("currentEnergy")) continue;
                    int currentEnergy = new BigDecimal(String.valueOf(data.opt("currentEnergy"))).intValueExact();
                    if (currentEnergy < 0) return false;
                    JSONObject ancientTree = data.optJSONObject("ancientTree");
                    if (ancientTree == null) {
                        return false;
                    }
                    String activityId = ancientTree.optString("activityId");
                    String projectId = ancientTree.optString("projectId");
                    if (activityId.isEmpty() || projectId.isEmpty()) return false;
                    JSONObject ancientTreeInfo = ancientTree.optJSONObject("ancientTreeInfo");
                    if (ancientTreeInfo == null) {
                        return false;
                    }
                    String name = ancientTreeInfo.optString("name"); int age = ancientTreeInfo.optInt("age");
                    if (!ancientTreeInfo.has("protectExpense")) continue;
                    int protectExpense = new BigDecimal(String.valueOf(ancientTreeInfo.opt("protectExpense"))).intValueExact();
                    cityCode = ancientTreeInfo.optString("cityCode");
                    if (protectExpense < 0 || cityCode.isEmpty()) return false;
                    if (currentEnergy < protectExpense)
                        break; TimeUtil.sleep(200);
                    jo = MyUtils.newJSONObject(AncientTreeRpcCall.protect(activityId, projectId, cityCode));
                    if (MessageUtil.checkResultCode(TAG, jo)) {
                        Log.forest("保护古树🎐[" + cityName + "-" + districtName + "]#" + age + "年" + name + ",消耗能量" + protectExpense + "g");
                    } else {
                        return false;
                    }
                } TimeUtil.sleep(500L);
            }
            return true;
        } catch (Throwable th) {
            if (th instanceof TaskCancelledException) throw (TaskCancelledException) th;
            Log.i(TAG, "districtDetail err:"); Log.printStackTrace(TAG, th);
        }
        return false;
    }
}
