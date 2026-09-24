package io.github.aw1y2z.sesame.data.modelFieldExt;


import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import io.github.aw1y2z.sesame.R;
import io.github.aw1y2z.sesame.data.ModelField;

public class ChoiceModelField extends ModelField<Integer> {

    private String[] choiceArray;

    public ChoiceModelField(String code, String name, Integer value) {
        super(code, name, value);
    }

    public ChoiceModelField(String code, String name, Integer value, String[] choiceArray) {
        super(code, name, value);
        this.choiceArray = choiceArray;
    }

    @Override
    public String getType() {
        return "CHOICE";
    }

    public String[] getExpandKey() {
        return choiceArray;
    }

    @Override
    public void clampValue() {
        // 越界选项回退默认值，避免运行期拿到非法下标
        if (value != null && choiceArray != null && choiceArray.length > 0
                && (value < 0 || value >= choiceArray.length)) {
            value = defaultValue;
        }
    }

    @Override
    public View getView(Context context) {
        return null;
    }

}
