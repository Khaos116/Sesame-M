package io.github.aw1y2z.sesame.data;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Objects;

import io.github.aw1y2z.sesame.R;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ToastUtil;
import io.github.aw1y2z.sesame.util.TypeUtil;
import lombok.Data;

@Data
public class ModelField<T> implements Serializable {

    @JsonIgnore
    private final Type valueType;

    @JsonIgnore
    private String code;

    @JsonIgnore
    private String name;

    @JsonIgnore
    private String description;

    @JsonIgnore
    protected T defaultValue;

    protected volatile T value;

    public ModelField() {
        valueType = TypeUtil.getTypeArgument(this.getClass().getGenericSuperclass(), 0);
    }

    public ModelField(T value) {
        this(null, null, value);
    }

    public ModelField(String code, String name, T value) {
        this();
        this.code = code;
        this.name = name;
        this.defaultValue = value;
        this.description = null;
        setObjectValue(value);
    }

    public ModelField(String code, String name, T value, String description) {
        this();
        this.code = code;
        this.name = name;
        this.defaultValue = value;
        this.description = description;
        setObjectValue(value);
    }

    public void setObjectValue(Object objectValue) {
        if (objectValue == null) {
            reset();
            return;
        }
        value = JsonUtil.parseObject(objectValue, valueType);
    }

    @JsonIgnore
    public String getType() {
        return "DEFAULT";
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @JsonIgnore
    private String dependsOn;

    /**
     * 声明依赖的父字段 code：本字段仅在父字段“激活”时才在配置界面显示。
     * 用泛型自返回以支持链式调用（如 {@code new XxxField(...).setDependsOn("parent")}）。
     */
    public <F extends ModelField<T>> F setDependsOn(String parentCode) {
        this.dependsOn = parentCode;
        @SuppressWarnings("unchecked")
        F f = (F) this;
        return f;
    }

    public String getDependsOn() {
        return dependsOn;
    }

    /**
     * 是否应在配置界面显示。无依赖时恒为 true；有依赖时取决于父字段是否激活：
     * - BOOLEAN 父：值为 true
     * - CHOICE 父：值不等于默认值（如 NONE / CLOSE）
     * - SELECT/SELECT_AND_COUNT 父：值非空（含至少一项）
     * 父字段不存在时一律显示，避免配置项丢失。
     */
    @JsonIgnore
    public boolean isVisible(ModelConfig config) {
        if (dependsOn == null || dependsOn.isEmpty()) return true;
        ModelField<?> parent = config != null ? config.getModelField(dependsOn) : null;
        if (parent == null) return true;
        return isParentActive(parent);
    }

    private static boolean isParentActive(ModelField<?> parent) {
        switch (parent.getType()) {
            case "BOOLEAN":
                return Boolean.TRUE.equals(parent.getValue());
            case "CHOICE": {
                Object def = parent.getDefaultValue();
                Object val = parent.getValue();
                return val != null && !val.equals(def);
            }
            case "SELECT":
            case "SELECT_ONE":
            case "SELECT_AND_COUNT":
            case "SELECT_AND_COUNT_ONE": {
                Object v = parent.getValue();
                if (v == null) return false;
                if (v instanceof java.util.Set) return !((java.util.Set<?>) v).isEmpty();
                if (v instanceof java.util.Map) return !((java.util.Map<?, ?>) v).isEmpty();
                if (v instanceof String) return !((String) v).isEmpty();
                return false;
            }
            default:
                return true;
        }
    }

    public T getValue() {
        return value;
    }

    @JsonIgnore
    public Object getExpandKey() {
        return null;
    }

    @JsonIgnore
    public Object getExpandValue() {
        return null;
    }

    public Object toConfigValue(T value) {
        return value;
    }

    public Object fromConfigValue(String value) {
        return value;
    }

    @JsonIgnore
    public String getConfigValue() {
        return JsonUtil.toJsonString(toConfigValue(value));
    }

    @JsonIgnore
    public void setConfigValue(String configValue) {
        if (configValue == null || configValue.isEmpty()) {
            reset();
            return;
        }
        try {
            Object objectValue = fromConfigValue(configValue);
            if (Objects.equals(objectValue, configValue)) {
                value = JsonUtil.parseObject(configValue, valueType);
            } else {
                value = JsonUtil.parseObject(objectValue, valueType);
            }
        } catch (Exception e) {
            Log.printStackTrace(e);
            reset();
        }
    }

    public void reset() {
        value = defaultValue;
    }

    @JsonIgnore
    public View getView(Context context) {
        TextView btn = new TextView(context);
        btn.setText(getName());
        btn.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn.setTextColor(ContextCompat.getColor(context, R.color.button));
        btn.setBackground(ContextCompat.getDrawable(context, R.drawable.button));
        btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        btn.setMinHeight(150);
        btn.setMaxHeight(180);
        btn.setPaddingRelative(40, 0, 40, 0);
        btn.setAllCaps(false);
        btn.setOnClickListener(v -> ToastUtil.show(context, "无配置项"));
        return btn;
    }

}
