package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.aw1y2z.sesame.data.TokenConfig
import io.github.aw1y2z.sesame.hook.ext.VersionHook
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MiuixExtensionsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            ExtensionsScreen(this)
        }
    }

    fun sendItemsBroadcast(type: String, method: String, data: String?) {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
        intent.putExtra("type", type)
        intent.putExtra("method", method)
        intent.putExtra("data", data)
        sendBroadcast(intent)
    }
}

@Composable
fun ExtensionsScreen(activity: MiuixExtensionsActivity) {
    val context = LocalContext.current
    var showDishDialog by remember { mutableStateOf(false) }
    var inputMode by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var dishBeforeId by remember { mutableStateOf("") }
    var dishAfterId by remember { mutableStateOf("") }

    // 版本伪装配置存在与支付宝注入进程共享的 version_config.json 里，UI 在自己的 App 进程读写，
    // 需要显式 ensure/load 一次才能拿到当前值；保存后需要重新注入/重启支付宝才会在那边生效
    remember {
        VersionHook.ensureVersionConfig(context)
        VersionHook.loadVersionConfig()
        true
    }
    var versionHookEnabled by remember { mutableStateOf(VersionHook.isVersionHookEnabled()) }
    var fakeVersionName by remember { mutableStateOf(VersionHook.getCachedVersionName()) }
    var fakeVersionCode by remember { mutableStateOf(if (VersionHook.getCachedVersionCode() > 0) VersionHook.getCachedVersionCode().toString() else "") }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "扩展功能",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            SmallTitle(text = "森林查询")
            CardColumn {
                val forestButtons = listOf(
                    "查询浇水列表" to ("antForest" to "getWateredItems"),
                    "查询被浇列表" to ("antForest" to "getWateringItems"),
                    "查询古树列表" to ("antForest" to "getTreeItems"),
                    "查询新古树" to ("antForest" to "getNewTreeItems"),
                    "查询区域古树" to ("antForest" to "queryAreaTrees"),
                    "查询可解锁古树" to ("antForest" to "getUnlockTreeItems"),
                    "填入浇水好友" to ("antForest" to "fillWateredFriendList")
                )
                forestButtons.forEach { (label, pair) ->
                    ArrowPreference(
                        title = label,
                        onClick = {
                            activity.sendItemsBroadcast(pair.first, pair.second, null)
                            ToastUtil.show(context, "已发送查询请求，请在森林日志查看结果！")
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SmallTitle(text = "自定义走路路径")
            CardColumn {
                ArrowPreference(
                    title = "设置自定义走路路径(list)",
                    onClick = { inputMode = "list"; inputText = "" }
                )
                ArrowPreference(
                    title = "设置自定义走路路径(queue)",
                    onClick = { inputMode = "queue"; inputText = "" }
                )
            }
            Spacer(Modifier.height(12.dp))

            SmallTitle(text = "版本伪装（谨慎使用）")
            CardColumn {
                Text(
                    "向支付宝服务端伪造一个更低的客户端版本号，用来规避高版本才有的拼图验证码风控。" +
                        "这是主动欺骗服务端的行为，不是本地跳过判断，默认关闭；实测对验证码类型没有效果，不建议开启。修改后需重启支付宝生效。",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                SwitchPreference(
                    title = "启用版本伪装",
                    checked = versionHookEnabled,
                    onCheckedChange = {
                        versionHookEnabled = it
                        VersionHook.setEnableVersionHook(it)
                        VersionHook.saveVersionConfig()
                        ToastUtil.show(context, "已保存，需要重新注入/重启支付宝才会生效")
                    }
                )
                TextField(
                    value = fakeVersionName,
                    onValueChange = {
                        fakeVersionName = it
                        VersionHook.setVersionName(it)
                        VersionHook.saveVersionConfig()
                    },
                    label = "伪装版本名（留空用默认 10.6.58.8000）",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = fakeVersionCode,
                    onValueChange = { text ->
                        fakeVersionCode = text
                        VersionHook.setVersionCode(text.toLongOrNull() ?: 0L)
                        VersionHook.saveVersionConfig()
                    },
                    label = "伪装版本号（留空用默认 1881）",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(12.dp))

            SmallTitle(text = "其他")
            CardColumn {
                ArrowPreference(
                    title = "手动补光盘行动图片",
                    onClick = { inputMode = "dishImage"; inputText = "" }
                )
                ArrowPreference(
                    title = "清空光盘行动图片",
                    onClick = { showDishDialog = true }
                )
            }

            if (showDishDialog) {
                ConfirmDialog(
                    title = "清空光盘行动图片",
                    text = "确认清空 ${TokenConfig.getDishImageCount()} 组光盘行动图片？",
                    onConfirm = {
                        showDishDialog = false
                        if (TokenConfig.clearDishImage()) {
                            ToastUtil.show(context, "光盘行动图片清空成功")
                        } else {
                            ToastUtil.show(context, "光盘行动图片清空失败")
                        }
                    },
                    onDismiss = { showDishDialog = false }
                )
            }

            if (inputMode == "list" || inputMode == "queue") {
                Dialog(onDismissRequest = { inputMode = null }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(
                                if (inputMode == "list") "设置自定义走路路径(list)" else "设置自定义走路路径(queue)",
                                color = MiuixTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = "路径ID",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (inputMode == "queue") {
                                    TextButton(text = "清空队列", onClick = {
                                        activity.sendItemsBroadcast("setCustomWalkPathIdQueue", "clearCustomWalkPathIdQueue", null)
                                        inputMode = null
                                    })
                                    Spacer(Modifier.width(8.dp))
                                }
                                TextButton(text = "添加", onClick = {
                                    val text = inputText.trim()
                                    if (inputMode == "list") {
                                        activity.sendItemsBroadcast("setCustomWalkPathIdList", "addCustomWalkPathId", text)
                                    } else {
                                        activity.sendItemsBroadcast("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", text)
                                    }
                                    inputMode = null
                                })
                            }
                        }
                    }
                }
            }

            if (inputMode == "dishImage") {
                Dialog(onDismissRequest = { inputMode = null }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text("手动补光盘行动图片", color = MiuixTheme.colorScheme.onBackground)
                            Text(
                                "跳过需要先在支付宝里手动打卡一次的要求，直接补一组餐前/餐后图片 ID 供自动打卡复用",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = dishBeforeId,
                                onValueChange = { dishBeforeId = it },
                                label = "餐前图片 ID",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            TextField(
                                value = dishAfterId,
                                onValueChange = { dishAfterId = it },
                                label = "餐后图片 ID",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(text = "随机生成", onClick = {
                                    val ok = TokenConfig.writeDishImageWithRandomIds()
                                    ToastUtil.show(context, if (ok) "已随机写入一组图片 ID" else "写入失败")
                                    inputMode = null
                                })
                                Spacer(Modifier.width(8.dp))
                                TextButton(text = "写入", onClick = {
                                    val ok = TokenConfig.writeDishImage(dishBeforeId.trim(), dishAfterId.trim())
                                    ToastUtil.show(context, if (ok) "写入成功" else "写入失败：ID 为空或餐前餐后相同")
                                    if (ok) inputMode = null
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}