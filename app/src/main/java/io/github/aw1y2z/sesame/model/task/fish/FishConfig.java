package io.github.aw1y2z.sesame.model.task.fish;

import java.util.*;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 福气鱼塘设置项。对齐 Sure-Xu model/task/fish/FishConfig.java（已从 GR 原版修过若干缺陷，见 docs/MyFix.md）。
 */
public class FishConfig {
    private static BooleanModelField autoFishTaskBlacklist;
    private static BooleanModelField enableFishAuto;
    private static BooleanModelField enableFishTaskAuto;
    private static SelectModelField fishTaskBlacklist;
    private static StringModelField fishpondToken;
    private static IntegerModelField.MultiplyIntegerModelField fishCheckInterval;

    public static void registerFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("enableFishAuto", "福气鱼塘 | 钓鱼", false);
        enableFishAuto = booleanModelField;
        modelFields.addField(booleanModelField);
        BooleanModelField booleanModelField2 = new BooleanModelField("enableFishTaskAuto", "福气鱼塘 | 任务", false);
        enableFishTaskAuto = booleanModelField2;
        modelFields.addField(booleanModelField2);
        BooleanModelField booleanModelField3 = new BooleanModelField("autoFishTaskBlacklist", "福气鱼塘 | 自动黑名单", true);
        autoFishTaskBlacklist = booleanModelField3;
        modelFields.addField(booleanModelField3);
        StringModelField stringModelField = new StringModelField("fishpondToken", "福气鱼塘 | Token", "");
        fishpondToken = stringModelField;
        modelFields.addField(stringModelField);
        SelectModelField selectModelField = new SelectModelField("fishTaskBlacklist", "福气鱼塘 | 黑名单", new LinkedHashSet<>(), new SelectModelField.SelectListFunc() {
            @Override
            public List<FishTask.FishTaskItem> getList() {
                return FishTask.getTaskList();
            }
        });
        fishTaskBlacklist = selectModelField;
        modelFields.addField(selectModelField);
        IntegerModelField.MultiplyIntegerModelField multiplyIntegerModelField = new IntegerModelField.MultiplyIntegerModelField("fishCheckInterval", "福气鱼塘 | 独立执行间隔(分钟)", 60, 1, 12 * 60, 60_000);
        fishCheckInterval = multiplyIntegerModelField;
        modelFields.addField(multiplyIntegerModelField);
    }

    public static boolean isEnableFishAuto() {
        BooleanModelField booleanModelField = enableFishAuto;
        return booleanModelField != null && booleanModelField.getValue();
    }

    public static boolean isEnableFishTaskAuto() {
        BooleanModelField booleanModelField = enableFishTaskAuto;
        return booleanModelField != null && booleanModelField.getValue();
    }

    public static boolean isAutoFishTaskBlacklist() {
        BooleanModelField booleanModelField = autoFishTaskBlacklist;
        return booleanModelField != null && booleanModelField.getValue();
    }

    public static int getFishCheckInterval() {
        IntegerModelField.MultiplyIntegerModelField multiplyIntegerModelField = fishCheckInterval;
        return multiplyIntegerModelField == null ? 60 * 60_000 : multiplyIntegerModelField.getValue();
    }

    public static String getFishpondToken() {
        StringModelField stringModelField = fishpondToken;
        return stringModelField == null ? "" : stringModelField.getValue();
    }

    public static Set<String> getFishTaskBlacklist() {
        SelectModelField selectModelField = fishTaskBlacklist;
        return selectModelField == null ? new LinkedHashSet<>() : selectModelField.getValue();
    }

    public static void setFishpondToken(String str) {
        if (fishpondToken == null || str == null) {
            return;
        }
        fishpondToken.setValue(str.trim());
        ConfigV2.save(UserIdMap.getCurrentUid(), false);
    }

    public static void addToFishTaskBlacklist(String str) {
        if (fishTaskBlacklist == null || str == null || str.isEmpty()) {
            return;
        }
        Set<String> value = fishTaskBlacklist.getValue();
        if (value == null) {
            value = new LinkedHashSet<>();
        }
        value.add(str);
        fishTaskBlacklist.setValue(value);
        ConfigV2.save(UserIdMap.getCurrentUid(), false);
    }
}
