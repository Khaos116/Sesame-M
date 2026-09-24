package io.github.aw1y2z.sesame.ui.miuix

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.entity.UserEntity
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.LanguageUtil
import io.github.aw1y2z.sesame.util.JsonUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.Statistics
import io.github.aw1y2z.sesame.util.Statistics.DataType
import io.github.aw1y2z.sesame.util.Statistics.TimeType
import io.github.aw1y2z.sesame.util.TimeUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.Calendar

class MiuixMainActivity : MiuixBaseActivity() {

    var runTypeText by mutableStateOf("")

    /** 可观察的激活状态：HomeTab 等页面订阅它，onServiceBind/广播更新时自动重组刷新 */
    var uiRunType by mutableStateOf<RunType>(RunType.DISABLE)
    var statisticsText by mutableStateOf("")
    var hasPermission by mutableStateOf(false)

    /** 统计版本号：load / 广播刷新后自增,首页 StatisticsTable 订阅它来触发重组 */
    var statisticsVersion by mutableStateOf(0)

    private val handler = Handler(Looper.getMainLooper())
    private var isClick = false
    // 标记是否已通过系统设置页请求过权限，用于 onResume 中检测用户是否已授权
    var hasRequestedPermission by mutableStateOf(false)

    /** 激活探测已重试次数,上限见 MAX_RUN_TYPE_PROBE_TIMES */
    private var runTypeProbeTimes = 0

    private lateinit var titleRunner: Runnable

    init {
        /**
         * 激活探测:仅当真实状态仍为 DISABLE 时才显示未激活,并在超时前周期性重试,
         * 避免覆盖晚到的 onServiceBind 激活信号,也避免 XposedService 绑定较慢时误报未激活
         */
        titleRunner = Runnable {
            if (ViewAppInfo.getRunType() == RunType.DISABLE) {
                runTypeProbeTimes++
                updateSubTitle(RunType.DISABLE)
                if (runTypeProbeTimes < MAX_RUN_TYPE_PROBE_TIMES) {
                    sendQueryBroadcast()
                    handler.postDelayed(titleRunner, 3000)
                }
            } else {
                runTypeProbeTimes = 0
            }
        }
    }

    companion object {
        /** 最多探测次数:每次间隔 3 秒,共约 15 秒,覆盖 XposedService 冷启动绑定晚于 Activity 的情况 */
        private const val MAX_RUN_TYPE_PROBE_TIMES = 5

        /**
         * 设备显示名:优先读市场名(如 Xiaomi 13)。
         * 小米/红米及多数云手机、模拟器的 ro.product.model 只是内部型号编号(如 2211133C),
         * 多设备会显示相同,须用 ro.product.marketname 才有人类可读名称。
         */
        fun getDeviceDisplayName(): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java)
                val market = get.invoke(null, "ro.product.marketname") as? String
                if (!market.isNullOrBlank()) {
                    return market
                }
            } catch (_: Throwable) {
            }
            return Build.MODEL ?: Build.DEVICE ?: ""
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.i("view broadcast action:" + action + " intent:" + intent)
            if (action != null) {
                when (action) {
                    "io.github.aw1y2z.sesame.status" -> {
                        // 模块已被 LSPosed 启用并注入支付宝，标记为已激活
                        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode())
                        runTypeProbeTimes = 0
                        handler.removeCallbacks(titleRunner)
                        updateSubTitle(RunType.MODEL)
                        if (isClick) {
                            ToastUtil.show(context, "芝麻粒-M加载状态正常")
                            isClick = false
                        }
                    }

                    "io.github.aw1y2z.sesame.update" -> {
                        refreshStatistics()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // runType 被模块置为 MODEL（onModuleLoaded）时立即刷新界面，无需手动加载配置
        ViewAppInfo.setRunTypeListener {
            runOnUiThread {
                handler.removeCallbacks(titleRunner)
                updateSubTitle(ViewAppInfo.getRunType())
            }
        }
        ViewAppInfo.checkRunType()
        updateSubTitle(ViewAppInfo.getRunType())
        val intentFilter = IntentFilter()
        intentFilter.addAction("io.github.aw1y2z.sesame.status")
        intentFilter.addAction("io.github.aw1y2z.sesame.update")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        setAppContent {
            MainScreen(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // 激活状态探测独立于存储权限：防止 titleRunner 重复累积，先清空再启动
        if (RunType.DISABLE == ViewAppInfo.getRunType()) {
            handler.removeCallbacks(titleRunner)
            runTypeProbeTimes = 0
            sendQueryBroadcast()
            handler.postDelayed(titleRunner, 3000)
        }
        checkPermissionAndRefresh()
    }

    /** 检查文件权限，若已授权则刷新统计；同时处理首次请求权限的场景 */
    private fun checkPermissionAndRefresh() {
        if (hasRequestedPermission) {
            hasRequestedPermission = false
            if (PermissionUtil.checkFilePermissions(this)) {
                hasPermission = true
                refreshStatistics()
            }
        } else if (!hasPermission && PermissionUtil.checkFilePermissions(this)) {
            // 首次进入或权限刚被授予
            hasPermission = true
            refreshStatistics()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (granted) refreshStatistics()
        }
    }

    /**
     * 重新从统计文件加载并通知首页表格刷新。
     * 基于可观察的 statisticsVersion 触发 Compose 重组,解决"首次进入首页统计为 0、
     * 必须进入配置返回后才刷新"的问题(此前 StatisticsTable 直接读静态单例,单例变化不会重组)。
     */
    fun refreshStatistics() {
        if (!hasPermission) {
            Statistics.unload()
            statisticsVersion++
            return
        }
        try {
            val userId = FileUtil.getPublishedUserId()
            if (userId == null) {
                Statistics.unload()
            } else {
                Statistics.load(userId)
                Statistics.updateDay(TimeUtil.getNow())
            }
            statisticsVersion++
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
    }

    fun updateSubTitle(runType: RunType) {
        uiRunType = runType
        runTypeText = when (runType) {
            RunType.DISABLE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.disable) + "】"
            RunType.MODEL -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.activated) + "】"
            RunType.PACKAGE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.loading) + "】"
        }
    }

    fun sendStatus() {
        try {
            isClick = true
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 向支付宝进程查询本模块注入状态（不弹 Toast），由 titleRunner 周期性调用 */
    fun sendQueryBroadcast() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 通知支付宝进程重载共享配置（日志开关等），使开关在注入进程中即时生效 */
    /**
     * 让注入进程整体重启（重新初始化并重挂 hook）。
     * 适用于改完必须重新初始化的开关，比如「使用新接口」要重挂 RPC bridge；
     * 只是重载 AppConfig 的 broadcastReloadConfig() 不够用。
     */
    fun broadcastRestart() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.restart"))
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    fun broadcastReloadConfig() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast reloadConfig err:")
            Log.printStackTrace(th)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(this, "无法打开链接")
        }
    }

    fun toggleLanguage() {
        val appConfig = AppConfig.INSTANCE
        appConfig.languageSimplifiedChinese = !appConfig.languageSimplifiedChinese
        if (AppConfig.save()) {
            LanguageUtil.setLocal(this)
            recreate()
        }
    }

    fun isIconHidden(): Boolean {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        return packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun toggleHideIcon() {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        val state = packageManager.getComponentEnabledSetting(alias)
        val newState = if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
    }

    fun exportStatistics(): Uri? {
        val userId = FileUtil.getPublishedUserId() ?: return null
        val exported = FileUtil.getExportedStatisticsFile(userId) ?: return null
        return if (FileUtil.copyTo(FileUtil.getStatisticsFile(userId), exported)) Uri.fromFile(exported) else null
    }

    fun importStatistics(): Boolean {
        val userId = FileUtil.getPublishedUserId() ?: return false
        val src = FileUtil.getExportedStatisticsFile(userId)
        if (src != null && FileUtil.copyTo(src, FileUtil.getStatisticsFile(userId))) {
            refreshStatistics()
            statisticsText = Statistics.getText(this)
            return true
        }
        return false
    }

    override fun onPause() {
        super.onPause()
        // 离开前台即停止状态轮询，避免后台无谓广播与泄漏
        handler.removeCallbacks(titleRunner)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(titleRunner)
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun MainScreen(activity: MiuixMainActivity) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // 提到 MainScreen 一级,跨 tab 共享,切 tab 不会因为重新进入组合而闪回"未知账号"
    var currentAccount by remember { mutableStateOf("未知账号") }
    LaunchedEffect(Unit) {
        var previousFolder: String? = null
        while (true) {
            val folder = withContext(Dispatchers.IO) { FileUtil.getRuntimeLogFile().parentFile?.name ?: "default" }
            currentAccount = if (folder == "default") "未知账号" else accountDisplayName(folder)
            if (folder != previousFolder) {
                previousFolder = folder
                activity.refreshStatistics()
            }
            delay(1000)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = Icons.Filled.Home,
                    label = "首页"
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = Icons.Filled.Description,
                    label = "日志"
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = Icons.Filled.Tune,
                    label = "配置"
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = Icons.Filled.Settings,
                    label = "设置"
                )
            }
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (selectedTab) {
                0 -> HomeTab(activity, currentAccount)
                1 -> LogsTab(activity, currentAccount)
                2 -> ConfigTab(activity, currentAccount)
                3 -> SettingsTab(activity, currentAccount)
            }
        }
    }
}

@Composable
fun HomeTab(activity: MiuixMainActivity, currentAccount: String) {
    val context = LocalContext.current
    // 订阅 Compose state：onServiceBind / 状态广播到达时会自动重组刷新首页状态
    val activated = activity.uiRunType == RunType.MODEL
    val appTitle = ViewAppInfo.getAppTitle()
    val version = ViewAppInfo.getAppVersion()

    // 保留首次权限申请；授权后的统计刷新统一由 Activity 处理。
    DisposableEffect(Unit) {
        if (!PermissionUtil.checkFilePermissions(activity)) {
            activity.hasPermission = false
            activity.hasRequestedPermission = true
            PermissionUtil.checkOrRequestFilePermissions(activity)
        }
        onDispose { }
    }

    TabTitleRow(title = "芝麻粒M", account = currentAccount)
    Spacer(Modifier.height(16.dp))

    Box(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (activated) "已激活" else "已关闭",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$version (${io.github.aw1y2z.sesame.BuildConfig.VERSION_CODE})",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "API 102",
                    style = MiuixTheme.textStyles.body2,
                    color = Color(0xFF2E7D32)
                )
            }
            if (activated) {
                Image(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    alignment = Alignment.Center,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF2E7D32))
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "运行环境")
    CardColumn {
        // 不再放「模块状态」「版本」两行：顶部绿色 banner 已经显示激活状态与版本，重复
        StatusRow("SDK API", Build.VERSION.SDK_INT.toString())
        StatusRow("设备", MiuixMainActivity.getDeviceDisplayName())
        StatusRow("系统架构", Build.SUPPORTED_ABIS?.firstOrNull() ?: "")
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "数据统计")
    CardColumn {
        StatisticsTable(activity)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun StatusRow(label: String, value: String) {
    // 左右补 16dp，对齐设置页的行（SwitchPreference/ArrowPreference 自带 insideMargin 的左右留白）；
    // 上下**故意**保持 8dp：首页 5 行的行距拉到 16dp 会多出 ~80dp，首屏又会显示不全
    // 字号字重取 miuix 行样式 token（标题 headline1、摘要 body2），与配置/设置页的 preference 行一致
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.headline1,
            color = MiuixTheme.colorScheme.onBackground
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.primary
        )
    }
}

@Composable
fun StatisticsTable(activity: MiuixMainActivity) {
    // 左右补 16dp：卡片( CardColumn )自带的 16dp 之外，再补上行内边距，
    // 让表格文字与「模块状态」那些行的标签左边界对齐（都是 48dp），否则整块会贴着卡片边缘更靠左

    // 订阅 statisticsVersion：load / 广播刷新后自增,触发本表重组读取最新单例数据
    activity.statisticsVersion
    val rows = listOf(
        "收" to listOf(DataType.COLLECTED),
        "帮" to listOf(DataType.HELPED),
        "浇" to listOf(DataType.WATERED),
        "被水" to listOf(DataType.WATEREDCOUNT),
        "浇水" to listOf(DataType.WATERINGCOUNT)
    )
    val columns = listOf(TimeType.DAY, TimeType.MONTH, TimeType.YEAR)
    val headers = listOf("今日", "本月", "今年")

    Column(
        Modifier
            .fillMaxWidth()
            // 上下补 16dp：卡片本身不带内边距（各部分自备留白），不补的话表头会贴住卡片上边缘
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f))
            headers.forEach { header ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // 列标题：与行内说明同级（body2），不再用硬编码 13sp
                    Text(
                        text = header,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        rows.forEach { (label, types) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    // 行标签用 headline1：与「模块状态」等 preference 行的标题同级
                    Text(text = label, style = MiuixTheme.textStyles.headline1, color = MiuixTheme.colorScheme.onBackground)
                }
                columns.forEach { timeType ->
                    val value = types.sumOf { Statistics.getData(timeType, it) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        // 数值用 body2：与 preference 行的摘要/值同级（原来硬编码 15sp，夹在 14sp/17sp 之间最显割裂）
                        Text(text = value.toString(), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}

@Composable
fun LogsTab(activity: MiuixMainActivity, currentAccount: String) {
    TabTitleRow(title = "日志", account = currentAccount)

    SmallTitle(text = "分类记录")
    CardColumn {
        var forest by remember { mutableStateOf(AppConfig.INSTANCE.enableForestLog ?: true) }
        LogSwitchRow("森林记录", forest, onClick = { openLog(activity, LogType.FOREST) }) {
            forest = it
            AppConfig.INSTANCE.enableForestLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("forest")
        }
        var farm by remember { mutableStateOf(AppConfig.INSTANCE.enableFarmLog ?: true) }
        LogSwitchRow("庄园记录", farm, onClick = { openLog(activity, LogType.FARM) }) {
            farm = it
            AppConfig.INSTANCE.enableFarmLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("farm")
        }
        var goldenBeans by remember { mutableStateOf(AppConfig.INSTANCE.enableGoldenBeansLog ?: true) }
        LogSwitchRow("金豆记录", goldenBeans, onClick = { openLog(activity, LogType.GOLDENBEANS) }) {
            goldenBeans = it
            AppConfig.INSTANCE.enableGoldenBeansLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("goldenbeans")
        }
        var other by remember { mutableStateOf(AppConfig.INSTANCE.enableOtherLog ?: true) }
        LogSwitchRow("其他记录", other, onClick = { openLog(activity, LogType.OTHER) }) {
            other = it
            AppConfig.INSTANCE.enableOtherLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("other")
        }
        var captcha by remember { mutableStateOf(AppConfig.INSTANCE.enableCaptchaLog ?: true) }
        LogSwitchRow("验证记录", captcha, onClick = { openLog(activity, LogType.CAPTCHA) }) {
            captcha = it
            AppConfig.INSTANCE.enableCaptchaLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("captcha")
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "系统记录")
    CardColumn {
        var debug by remember { mutableStateOf(AppConfig.INSTANCE.enableDebugLog ?: false) }
        LogSwitchRow("抓包记录", debug, onClick = { openLog(activity, LogType.DEBUG) }) {
            debug = it
            AppConfig.INSTANCE.enableDebugLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            // 关闭时**不清空** debug 日志：抓到的包是排查证据，要清空请到日志页点「删除」
        }
        var error by remember { mutableStateOf(AppConfig.INSTANCE.enableViewErrorLog ?: true) }
        LogSwitchRow("查看异常日志", error, onClick = { openLog(activity, LogType.ERROR) }) {
            error = it
            AppConfig.INSTANCE.enableViewErrorLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("error")
        }
        var runtime by remember { mutableStateOf(AppConfig.INSTANCE.enableViewRuntimeLog ?: true) }
        LogSwitchRow("查看运行日志", runtime, onClick = { openLog(activity, LogType.RUNTIME) }) {
            runtime = it
            AppConfig.INSTANCE.enableViewRuntimeLog = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("runtime")
        }
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * 日志条目行：**点按整行**进入对应日志详情，右侧开关控制是否记录。
 * 用库的 SwitchPreference 渲染，字体（样式/字重/颜色）与设置页的行由同一组件保证一致；
 * 它的 insideMargin 覆写为上下 8dp（库默认 16dp）以尽量贴近日志页原来的行距；
 * 它没有 onClick 参数，所以外层再套一层可点区域实现"点整行"。
 */
@Composable
fun LogSwitchRow(title: String, checked: Boolean, onClick: () -> Unit, onCheckedChange: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        SwitchPreference(
            title = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
            // 覆写库默认的 16dp 上下内边距，尽量贴近日志页原来的行距（左右仍是 16dp，与设置页一致）
            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        )
        // 库的 preference 行自带 clickable/ripple，会把触摸吞掉（实测套在外层的 clickable 收不到事件），
        // 所以压在它**上层**盖一层透明可点区域：只盖标题侧，右侧给开关留出 72dp，
        // 这样"点标题进日志、点开关只切开关"
        Box(
            Modifier
                .matchParentSize()
                .padding(end = 72.dp)
                .clickable(onClick = onClick)
                // 这层盖在库的 SwitchPreference 之上，miuix 0.9.4 的 semantics 合并会把下层的行标题吞掉
                // （无障碍树里读不到「森林记录」等标题），这里把标题补回语义
                .semantics { contentDescription = title }
        )
    }
}

/** 打开日志查看器(显示指定日志类型的全部条目) */
fun openLog(activity: MiuixMainActivity, logType: LogType) {
    try {
        activity.startActivity(
            Intent(activity, MiuixLogViewerActivity::class.java)
                .putExtra(LogType.EXTRA_LOG_TYPE, logType.name)
        )
    } catch (t: Throwable) {
        Log.printStackTrace(t)
    }
}

@Composable
fun ConfigTab(activity: MiuixMainActivity, currentAccount: String) {
    val context = LocalContext.current
    val items = remember {
        val list = ArrayList<Pair<String?, String>>()
        list.add(null to "默认")
        try {
            val dir = FileUtil.CONFIG_DIRECTORY_FILE
            dir.listFiles()?.forEach { configDir ->
                if (configDir.isDirectory) {
                    val userId = configDir.name
                    val name = accountDisplayName(userId)
                    list.add(userId to name)
                }
            }
        } catch (e: Exception) {
            Log.printStackTrace(e)
        }
        list
    }

    TabTitleRow(title = "配置", account = currentAccount)

    SmallTitle(text = "配置管理")
    CardColumn {
        items.forEach { (userId, name) ->
            ArrowPreference(
                title = name,
                summary = if (userId == null) null else "UID: $userId",
                onClick = {
                    val intent = Intent(context, MiuixSettingsActivity::class.java)
                    if (userId != null) intent.putExtra("userId", userId)
                    context.startActivity(intent)
                }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    // 模块功能：全局配置（不分账号），与上面的「按账号配置」并列放在配置页更合理
    SmallTitle(text = "模块功能")
    CardColumn {
        // 这几项原先是「按账号」存在账号配置里，现改为全局配置 AppConfig（模块级，不分账号）
        var newRpc by remember { mutableStateOf(AppConfig.INSTANCE.newRpc ?: true) }
        BooleanSwitch("使用新接口", newRpc, summary = "最低支持 v10.3.96.8100") {
            AppConfig.INSTANCE.newRpc = it
            AppConfig.save()
            newRpc = it
            // 换接口要重挂 RPC bridge，必须让注入进程整体重启（只重载配置不够）
            activity.broadcastRestart()
        }
        var showToast by remember { mutableStateOf(AppConfig.INSTANCE.showToast ?: true) }
        BooleanSwitch("气泡提示", showToast) {
            AppConfig.INSTANCE.showToast = it
            AppConfig.save()
            showToast = it
            activity.broadcastReloadConfig()
        }
        // 气泡纵向偏移：一级界面没有整数控件，用 ArrowPreference 展开输入框，输入即保存
        var toastOffsetY by remember { mutableStateOf((AppConfig.INSTANCE.toastOffsetY ?: 0).toString()) }
        var offsetExpanded by remember { mutableStateOf(false) }
        ArrowPreference(
            title = "气泡纵向偏移",
            summary = if (toastOffsetY.isEmpty()) "0 px（正数向下）" else "$toastOffsetY px（正数向下）",
            onClick = { offsetExpanded = !offsetExpanded }
        )
        if (offsetExpanded) {
            top.yukonga.miuix.kmp.basic.TextField(
                value = toastOffsetY,
                onValueChange = { text ->
                    // 只接受整数（允许开头一个负号），改完立刻写回并让注入进程重载
                    val filtered = text.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }
                    toastOffsetY = filtered
                    filtered.toIntOrNull()?.let { value ->
                        AppConfig.INSTANCE.toastOffsetY = value
                        AppConfig.save()
                        activity.broadcastReloadConfig()
                    }
                },
                // 不要 label：它会作为浮动小标题显示在输入框内部（与上方行标题重复）；单位说明放到上面的 summary 里
                label = "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        var enableOnGoing by remember { mutableStateOf(AppConfig.INSTANCE.enableOnGoing ?: false) }
        BooleanSwitch("开启状态栏禁删", enableOnGoing) {
            AppConfig.INSTANCE.enableOnGoing = it
            AppConfig.save()
            enableOnGoing = it
            activity.broadcastReloadConfig()
        }
        var closeCaptchaDialog by remember { mutableStateOf(AppConfig.INSTANCE.closeCaptchaDialog ?: true) }
        BooleanSwitch("屏蔽部分弹窗", closeCaptchaDialog) {
            AppConfig.INSTANCE.closeCaptchaDialog = it
            AppConfig.save()
            closeCaptchaDialog = it
            activity.broadcastReloadConfig()
        }
        var autoPuzzleSlider by remember { mutableStateOf(AppConfig.INSTANCE.autoPuzzleSlider ?: true) }
        BooleanSwitch("自动处理拼图滑块验证", autoPuzzleSlider, summary = "关闭后不自动拖动图片拼图滑块") {
            AppConfig.INSTANCE.autoPuzzleSlider = it
            AppConfig.save()
            autoPuzzleSlider = it
            activity.broadcastReloadConfig()
        }
        var newPuzzleSlider by remember { mutableStateOf(AppConfig.INSTANCE.newPuzzleSlider ?: true) }
        BooleanSwitch("使用新版拼图识别", newPuzzleSlider, summary = "关闭后使用原有 M 版识别和滑动逻辑") {
            AppConfig.INSTANCE.newPuzzleSlider = it
            AppConfig.save()
            newPuzzleSlider = it
            activity.broadcastReloadConfig()
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun SettingsTab(activity: MiuixMainActivity, currentAccount: String) {
    val context = LocalContext.current

    TabTitleRow(title = "设置", account = currentAccount)

    SmallTitle(text = "功能设置")
    CardColumn {
        ArrowPreference(
            title = "好友统计",
            onClick = { context.startActivity(Intent(context, MiuixFriendStatsActivity::class.java)) }
        )
        ArrowPreference(
            title = "扩展功能",
            onClick = { context.startActivity(Intent(context, MiuixExtensionsActivity::class.java)) }
        )
        ArrowPreference(
            title = "账号轮询",
            onClick = { io.github.aw1y2z.sesame.ui.AccountSwitchSettingsDialog.show(context) }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "系统设置")
    CardColumn {
        // 文件权限申请引导
        val hasFilePerm = activity.hasPermission
        if (!hasFilePerm) {
            ArrowPreference(
                title = "申请文件权限",
                summary = "模块需要文件权限才能正常运行",
                onClick = {
                    try {
                        PermissionUtil.checkOrRequestFilePermissions(activity)
                        activity.hasRequestedPermission = true
                    } catch (e: Exception) {
                        ToastUtil.show(context, "申请权限失败")
                    }
                }
            )
        }
        var iconHidden by remember { mutableStateOf(activity.isIconHidden()) }
        BooleanSwitch("隐藏图标", iconHidden) {
            activity.toggleHideIcon()
            iconHidden = activity.isIconHidden()
        }
        var darkMode by remember { mutableStateOf(AppConfig.INSTANCE.darkMode ?: false) }
        var followSystem by remember { mutableStateOf(AppConfig.INSTANCE.followSystem ?: true) }
        val systemDark = isSystemInDarkTheme()
        // 跟随系统开着时实际生效的是系统当前深浅色，不是 darkMode 那个值；只有这个"最终效果"
        // 变了才值得 recreate()，两个开关状态对不上导致效果没变时不闪页面。
        fun effectiveDark(follow: Boolean, dark: Boolean) = if (follow) systemDark else dark
        BooleanSwitch("深色模式", darkMode) {
            val before = effectiveDark(followSystem, darkMode)
            AppConfig.INSTANCE.darkMode = it
            // 手动选深/浅色就是明确不想跟随系统了，不然"跟随系统"默认开着，这个开关切了
            // 也不会生效（MiuixBaseActivity 里 followSystem 优先级更高）。
            if (followSystem) {
                AppConfig.INSTANCE.followSystem = false
                followSystem = false
            }
            AppConfig.save()
            darkMode = it
            if (effectiveDark(followSystem, darkMode) != before) activity.recreate()
        }
        BooleanSwitch("跟随系统设置", followSystem) {
            val before = effectiveDark(followSystem, darkMode)
            AppConfig.INSTANCE.followSystem = it
            // 跟深色模式开关互斥：开了跟随系统，深色模式这个手动选择就没意义了，关掉它，
            // 免得用户以为两个开关都生效、实际只有先设置优先级更高的那个说了算。
            if (it && darkMode) {
                AppConfig.INSTANCE.darkMode = false
                darkMode = false
            }
            AppConfig.save()
            followSystem = it
            if (effectiveDark(followSystem, darkMode) != before) activity.recreate()
        }
        var batteryPerm by remember { mutableStateOf(AppConfig.shouldRequestBatteryPermission()) }
        BooleanSwitch("为支付宝申请后台运行权限", batteryPerm) {
            AppConfig.INSTANCE.batteryPerm = it
            AppConfig.save()
            activity.broadcastReloadConfig()
            batteryPerm = it
        }
        if (batteryPerm) {
            val hasPerm = try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                pm?.isIgnoringBatteryOptimizations("com.eg.android.AlipayGphone") == true
            } catch (e: Exception) {
                false
            }
            if (!hasPerm) {
                ArrowPreference(
                    title = "立即申请权限",
                    onClick = {
                        try {
                            if (PermissionUtil.checkOrRequestBatteryPermissions(context)) {
                                ToastUtil.show(context, "支付宝已获准忽略电池优化")
                            }
                        } catch (e: Exception) {
                            ToastUtil.show(context, "申请权限失败")
                        }
                    }
                )
            }
            ArrowPreference(
                title = "手动设置支付宝后台权限",
                onClick = { PermissionUtil.openBatterySettings(context) }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "关于")
    CardColumn {
        ArrowPreference(
            title = "关于应用",
            onClick = { context.startActivity(Intent(context, MiuixAboutActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

}

@Composable
fun BooleanSwitch(title: String, checked: Boolean, summary: String? = null, onCheckedChange: (Boolean) -> Unit) {
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

private fun accountDisplayName(folderOrUid: String): String {
    // 日志目录现在以账号名命名（如 C176），先由目录里的 .uid 标记找回 uid 再读账号信息
    val userId = FileUtil.uidOfLogFolder(folderOrUid) ?: folderOrUid
    return try {
        val body = FileUtil.readFromFile(FileUtil.getSelfIdFile(userId))
        val user = JsonUtil.parseObject(body, UserEntity.UserDto::class.java)?.toEntity()
        user?.let {
            val name = it.showName?.takeIf { s -> s.isNotEmpty() }
            val acc = it.account?.takeIf { s -> s.isNotEmpty() }
            when {
                name != null && acc != null -> "$name($acc)"
                name != null -> name
                acc != null -> acc
                else -> folderOrUid
            }
        } ?: folderOrUid
    } catch (_: Exception) {
        folderOrUid
    }
}

/** 各 TAB 共用的标题行，显示版本、编译时间和当前日志账号。account 由 MainScreen 统一轮询下发，
 *  切 tab 时不会重新从"未知账号"开始，避免闪烁。 */
@Composable
fun TabTitleRow(title: String, account: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground
        )
        Text(
            text = "${io.github.aw1y2z.sesame.BuildConfig.VERSION_NAME}  ${io.github.aw1y2z.sesame.BuildConfig.BUILD_TIME}\n$account",
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp, bottom = 4.dp)
        )
    }
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Card 只传 modifier：preference 行直接作为子项，行的左右缩进交给行自身的 insideMargin。
    // 之前给 Card 传 insideMargin 会把所有行整体往里缩，行自带的方形按压高亮就成了"悬在卡片里的方框"；
    // 让行顶满卡片宽度后，高亮是一条通栏色带，圆角由卡片自身裁剪处理。
    Card(modifier = modifier.fillMaxWidth()) {
        content()
    }
}
