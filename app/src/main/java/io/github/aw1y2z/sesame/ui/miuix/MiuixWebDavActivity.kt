package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.WebDavBackup
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.Executors

class MiuixWebDavActivity : MiuixBaseActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var client: WebDavBackup? = null
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var backups by mutableStateOf<List<String>>(emptyList())
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { WebDavScreen(this) }
    }

    fun clearBackups() { backups = emptyList() }
    fun cancelTransfer() { client?.cancel() }

    fun transfer(url: String, username: String, password: String, allowHttp: Boolean, scope: String, restore: String? = null, upload: Boolean = false) {
        if (busy) return
        val dav = try { WebDavBackup(url, username, password, allowHttp) }
        catch (e: Exception) { message = e.message ?: "WebDAV 地址无效"; return }
        client = dav
        busy = true
        message = "处理中…"
        worker.execute {
            var result: List<String>? = null
            val status = try {
                when {
                    upload -> "备份成功：${dav.upload(FileUtil.MAIN_DIRECTORY_FILE, scope)}"
                    restore != null -> {
                        val previous = dav.restore(FileUtil.MAIN_DIRECTORY_FILE, scope, restore)
                        if (scope == "app") AppConfig.load()
                        else sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.restart").apply {
                            putExtra("userId", if (scope == "default") "" else scope)
                        })
                        if (previous == null) "恢复成功；此前没有本地配置。请重启支付宝。"
                        else "恢复成功；原配置保存在 ${previous.name}。请重启支付宝。"
                    }
                    else -> {
                        result = dav.list(scope)
                        if (result.isNullOrEmpty()) "所选范围没有远程备份" else "请选择需要恢复的备份"
                    }
                }
            } catch (e: Exception) { e.message ?: "操作失败" }
            runOnUiThread {
                if (!isDestroyed) {
                    result?.let { backups = it }
                    message = status
                    busy = false
                    client = null
                }
            }
        }
    }

    override fun onDestroy() {
        client?.cancel()
        worker.shutdownNow()
        super.onDestroy()
    }
}

@Composable
private fun WebDavScreen(activity: MiuixWebDavActivity) {
    val prefs = remember { activity.getSharedPreferences("webdav", android.content.Context.MODE_PRIVATE) }
    var url by remember { mutableStateOf(prefs.getString("url", "") ?: "") }
    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var allowHttp by remember { mutableStateOf(false) }
    var scope by remember { mutableStateOf<String?>(null) }
    var restore by remember { mutableStateOf<String?>(null) }
    val accounts = remember {
        FileUtil.CONFIG_DIRECTORY_FILE.listFiles()?.filter { it.isDirectory && WebDavBackup.isAccountId(it.name) }
            ?.map { it.name }?.sorted() ?: emptyList()
    }
    fun resetList() { activity.clearBackups(); restore = null }

    Scaffold(topBar = { LogTopBar(title = "WebDAV 备份恢复", onBack = { activity.finish() }) },
        containerColor = MiuixTheme.colorScheme.surface) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("手动上传或恢复 M 配置 JSON；不会自动同步。请先停止支付宝中的任务，再恢复配置。")
            if (!activity.busy) {
                TextField(value = url, onValueChange = { url = it; resetList() }, label = "已存在的 WebDAV 目录 URL")
                TextField(value = username, onValueChange = { username = it; resetList() }, label = "用户名")
                Text("密码（仅本次页面使用，不保存）", style = MiuixTheme.textStyles.body2)
                BasicTextField(value = password, onValueChange = { password = it; resetList() }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = MiuixTheme.textStyles.body1.copy(color = MiuixTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp))
                SwitchPreference(title = "允许 HTTP", summary = "HTTP 会明文传输配置和认证信息；HTTPS 始终验证证书",
                    checked = allowHttp, onCheckedChange = { allowHttp = it; resetList() })
                TextButton(text = "保存地址和用户名", onClick = {
                    prefs.edit().putString("url", url).putString("username", username).apply()
                })
                SmallTitle("选择备份 / 恢复范围")
                CardColumn {
                    (listOf("default" to "默认业务配置", "app" to "全局 App 设置") + accounts.map { it to "账号：$it" }).forEach { (id, label) ->
                        RadioButtonPreference(title = label, selected = scope == id, onClick = { scope = id; resetList() })
                    }
                }
                scope?.let { selected ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(text = "上传备份", onClick = { activity.transfer(url, username, password, allowHttp, selected, upload = true) })
                        TextButton(text = "列出备份", onClick = { resetList(); activity.transfer(url, username, password, allowHttp, selected) })
                    }
                    CardColumn {
                        activity.backups.forEach { name -> ArrowPreference(title = name, onClick = { restore = name }) }
                    }
                    restore?.let { name ->
                        ConfirmDialog(title = "恢复所选配置", text = "将用 $name 覆盖当前所选范围，覆盖前保存本地 .prev.json。确认已停止支付宝任务？",
                            onConfirm = { restore = null; activity.transfer(url, username, password, allowHttp, selected, restore = name) },
                            onDismiss = { restore = null })
                    }
                }
            } else TextButton(text = "取消", onClick = { activity.cancelTransfer() })
            if (activity.message.isNotEmpty()) Text(activity.message)
        }
    }
}
