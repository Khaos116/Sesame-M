package io.github.aw1y2z.sesame.ui;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import io.github.aw1y2z.sesame.hook.AccountSwitchAccountCount;
import io.github.aw1y2z.sesame.hook.AccountSwitchIntervalDraft;
import io.github.aw1y2z.sesame.hook.AccountSwitchSettings;
import io.github.aw1y2z.sesame.hook.AccountSwitchStatus;

/**
 * 账号轮询设置：全局开关，跟具体登录哪个账号无关，所以用独立弹窗而不是挂进
 * 按账号存储的 ModelField 配置系统。移植自 GR 分支，见 doc/MyFix.md。
 */
public final class AccountSwitchSettingsDialog {
    private AccountSwitchSettingsDialog() { }

    public static void show(Context context) {
        AccountSwitchSettings.Values saved = AccountSwitchSettings.read();
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);
        SwitchCompat enabled = new SwitchCompat(context);
        enabled.setText("自动切号");
        enabled.setChecked(saved.enabled);
        content.addView(enabled);
        TextView accountCount = new TextView(context);
        accountCount.setText(AccountSwitchAccountCount.label());
        accountCount.setPadding(0, padding / 2, 0, 0);
        content.addView(accountCount);
        TextView switchStatus = new TextView(context);
        switchStatus.setText(AccountSwitchStatus.label());
        content.addView(switchStatus);
        TextView description = new TextView(context);
        description.setText("开启时以当前账号为首个账号。每个账号执行完成后间隔15秒切换下一个；一轮全部账号执行完成后冷却设定的间隔（默认2小时），再切回首个账号开启下一轮。关闭开关可立即清空冷却。");
        description.setPadding(0, padding / 2, 0, padding);
        content.addView(description);
        TextView label = new TextView(context);
        label.setText("整轮冷却间隔（秒，15–86400，默认7200秒/2小时）");
        content.addView(label);
        EditText seconds = new EditText(context);
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setSingleLine(true);
        seconds.setHint("留空默认7200秒（2小时）");
        seconds.setText(Integer.toString(saved.seconds));
        content.addView(seconds);
        enabled.setOnCheckedChangeListener((button, checked) -> {
            seconds.setError(null);
            if (checked && seconds.getText().toString().trim().isEmpty()) {
                seconds.setText(Integer.toString(AccountSwitchIntervalDraft.DEFAULT_SECONDS));
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("账号轮询设置")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    try {
                        if (!AccountSwitchSettings.saveDraft(enabled.isChecked(), seconds.getText().toString())) {
                            Toast.makeText(context, "保存失败，请检查文件读写权限后重试", Toast.LENGTH_LONG).show();
                            return;
                        }
                    } catch (NumberFormatException invalid) {
                        seconds.setError("请输入15–86400之间的整数秒数");
                        seconds.requestFocus();
                        return;
                    }
                    Toast.makeText(context, "全局账号轮询设置已保存", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        Runnable refreshCount = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                accountCount.setText(AccountSwitchAccountCount.label());
                switchStatus.setText(AccountSwitchStatus.label());
                accountCount.postDelayed(this, 1000L);
            }
        };
        dialog.setOnDismissListener(ignored -> accountCount.removeCallbacks(refreshCount));
        dialog.show();
        accountCount.post(refreshCount);
    }
}
