package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import android.os.FileObserver
import androidx.core.content.FileProvider
import java.io.RandomAccessFile
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import io.github.aw1y2z.sesame.util.diagnostics.RpcFailureJournal
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

/**
 * 二级日志页支持的日志类型(与一级页面日志类目一一对应)。
 * 一级页点击哪个类目,二级页就只展示该类。
 */
enum class LogType(val displayName: String) {
    FOREST("森林记录"),
    GOLDENBEANS("金豆记录"),
    FARM("庄园记录"),
    OTHER("其他记录"),
    CAPTCHA("验证记录"),
    DEBUG("抓包记录"),
    ERROR("查看异常日志"),
    RUNTIME("查看运行日志");

    /** 每次访问都重新取当日文件,避免跨天后路径过期 */
    val file: File
        get() = when (this) {
            FOREST -> FileUtil.getForestLogFile()
            GOLDENBEANS -> FileUtil.getGoldenBeansLogFile()
            FARM -> FileUtil.getFarmLogFile()
            OTHER -> FileUtil.getOtherLogFile()
            CAPTCHA -> FileUtil.getCaptchaLogFile()
            DEBUG -> FileUtil.getDebugLogFile()
            ERROR -> FileUtil.getErrorLogFile()
            RUNTIME -> FileUtil.getRuntimeLogFile()
        }

    companion object {
        /** 一级页跳转时携带的 extra key,值为 LogType.name */
        const val EXTRA_LOG_TYPE = "sesame_log_type"

        fun fromIntent(intent: Intent?): LogType {
            val name = intent?.getStringExtra(EXTRA_LOG_TYPE)
            return entries.firstOrNull { it.name == name } ?: RUNTIME
        }
    }
}

/** 单条日志条目(可能含多行正文) */
data class LogEntry(
    val lineNumber: Int,
    val time: String?,
    val tag: String?,
    val body: String
)

enum class LogViewMode { BY_ENTRY, BY_RUN }

/** 一轮执行聚合:从"开始执行"到下一轮之前的所有单条日志 */
data class LogRunEntry(
    val runIndex: Int,
    val startTime: String?,
    val startLineNumber: Int,
    val entries: List<LogEntry>
)

/**
 * 运行日志的轮次开始行正则。覆盖三种形态:
 * "[账号N]开始执行"、"[账号N]开始执行分组【xx】任务"、"[账号N]开始执行全部任务"。
 * 用前缀+tag 双重限定,避免"森林阅读📖开始执行..."、"黄金票[开始执行]"等任务日志被误判为轮次边界。
 */
private val RUN_START_REGEX = Regex("""^\[账号\d+\]开始执行(?:\s*$|分组【[^】]*】任务\s*$|全部任务\s*$)""")

/**
 * 读取运行日志全文,提取所有轮次开始行("[账号N]开始执行...")的时间戳作为全局锚点。
 * 读取全文而非受 [MAX_LOG_ENTRIES]/[MAX_TAIL_BYTES] 约束的尾部片段,确保全天各轮次的
 * 开始标记都能作为锚点,使森林/庄园/金豆/其他记录也能按统一执行周期正确划分轮次。
 */
private fun readRuntimeAnchors(file: File): List<String> {
    if (!file.exists()) return emptyList()
    val anchors = mutableListOf<String>()
    try {
        file.forEachLine(Charsets.UTF_8) { line ->
            val m = TIME_REGEX.find(line) ?: return@forEachLine
            val tag = m.groupValues[2].takeIf { it.isNotEmpty() }
            val body = m.groupValues[3]
            if (tag == "RUNTIME" && RUN_START_REGEX.containsMatchIn(body)) {
                m.groupValues[1].takeIf { it.isNotEmpty() }?.let { anchors.add(it) }
            }
        }
    } catch (_: Throwable) {
        // 读取失败时退化为无锚点(各页按单条显示)
    }
    return anchors.distinct().sorted()
}

/**
 * 将单条日志按"执行轮次"聚合。轮次边界取自运行日志的"开始执行"时间(全局锚点),
 * 每条日志按其时间戳归入"最近一次开始执行之后、下一次之前"的轮次。
 * 这样森林/庄园/金豆/其他记录等没有自身轮次标记的类目,也能按统一的执行周期聚合显示。
 * 返回顺序:最早轮在前(runIndex=1 为最早一轮,与时间顺序一致)。时间基于 "HH:mm:ss.SSS" 字典序比较(同日有效)。
 */
private fun aggregateByRun(source: List<LogEntry>, anchors: List<String>): List<LogRunEntry> {
    if (anchors.isEmpty()) {
        // 无锚点(从未运行过):退化为单轮,全部日志归一轮
        if (source.isEmpty()) return emptyList()
        return listOf(LogRunEntry(1, source.first().time, source.first().lineNumber, source))
    }
    val buckets = MutableList(anchors.size) { mutableListOf<LogEntry>() }
    for (e in source) {
        val t = e.time
        // 找到 <= t 的最大锚点索引;早于所有锚点的散落日志归入第 1 轮(最早)
        val k = if (t == null) -1 else anchors.indexOfLast { it <= t }
        val b = if (k < 0) 0 else k
        buckets[b].add(e)
    }
    return buckets.mapIndexedNotNull { index, list ->
        if (list.isEmpty()) null
        else LogRunEntry(
            runIndex = index + 1,
            startTime = list.first().time,
            startLineNumber = list.first().lineNumber,
            entries = list.toList()
        )
    }
}

class MiuixLogViewerActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            LogScreen(this, LogType.fromIntent(intent))
        }
    }
}

/**
 * 日志详情页:展示指定类目的全部条目卡片。
 * 仿 LSPosed 日志界面:每条目一张卡(标签 + 时间 + 正文)。
 *
 * 最新一条排在列表最前面(顶部),打开页面不用滚动就能看到最新日志;
 * 文件被写入时(FileObserver)重新加载尾部并跟回顶部;下滑翻历史时暂停跟随
 * (不会被新日志顶跑),滑回顶部后自动恢复。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(activity: MiuixLogViewerActivity, logType: LogType) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var sharing by remember(logType) { mutableStateOf(false) }
    var file by remember(logType) { mutableStateOf(logType.file) }
    LaunchedEffect(logType) {
        while (true) {
            file = withContext(Dispatchers.IO) { logType.file }
            delay(1000)
        }
    }
    val logReader = remember(logType) { LogTailReader() }
    var entries by remember(logType) { mutableStateOf(emptyList<LogEntry>()) }
    val listState = rememberLazyListState()
    val viewMode = remember(logType) {
        if (AppConfig.INSTANCE.logViewByRun == true) LogViewMode.BY_RUN else LogViewMode.BY_ENTRY
    }
    var runAnchors by remember(file) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(file, viewMode) {
        if (viewMode != LogViewMode.BY_RUN) return@LaunchedEffect
        val runtimeFile = File(file.parentFile, "runtime." + file.name.substringAfter('.'))
        var length = -1L
        var modified = -1L
        while (true) {
            // shortcut: 按轮模式文件变化时后台重扫锚点；超大日志刷新变慢时改增量扫描。
            val updated = withContext(Dispatchers.IO) {
                val size = runtimeFile.length()
                val stamp = runtimeFile.lastModified()
                if (size == length && stamp == modified) null else {
                    val anchors = readRuntimeAnchors(runtimeFile)
                    length = size
                    modified = stamp
                    anchors
                }
            }
            if (updated != null) runAnchors = updated
            delay(1000)
        }
    }
    // 搜索文本
    var searchQuery by remember { mutableStateOf("") }
    // Runtime 页面额外支持 tag 过滤
    var selectedTag by remember { mutableStateOf<String?>(null) }
    // 切换 tag 筛选或修改搜索文本后回到列表顶部（最新一条）：过滤结果变了，原来的滚动位置已经没有意义，
    // 保留的话会停在结果中间，或者结果很少时露出空白
    LaunchedEffect(selectedTag, searchQuery) { listState.requestScrollToItem(0) }
    fun updateEntries(updated: List<LogEntry>, reset: Boolean = false) {
        // 在替换数据前读取位置；index 0 是最新一条，排在列表顶部。
        val followTop = reset || entries.isEmpty() || !listState.canScrollBackward
        if (!reset && updated === entries) return
        entries = updated
        if (followTop) listState.requestScrollToItem(0)
    }
    // 实时刷新：文件被写入时重新加载尾部，做到打开日志页能看到正在执行的过程，
    // 不用手动关闭重开。参照 Sesame-AG 的 FileObserver + debounce 思路，
    // 只解析新增的完整行，保留跟随、历史阅读和账号切换行为。
    val fileUpdateChannel = remember(logType) { Channel<Unit>(Channel.CONFLATED) }
    LaunchedEffect(fileUpdateChannel, file) {
        updateEntries(withContext(Dispatchers.IO) { logReader.poll(file) }, reset = true)
        fileUpdateChannel.receiveAsFlow().collect {
            updateEntries(withContext(Dispatchers.IO) { logReader.poll(file) })
            delay(300)
        }
    }
    DisposableEffect(file) {
        val parent = file.parentFile
        val observer = if (parent != null) object : FileObserver(
            parent.path,
            MODIFY or CREATE or CLOSE_WRITE or MOVED_TO
        ) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || path == file.name) {
                    fileUpdateChannel.trySend(Unit)
                }
            }
        } else null
        observer?.startWatching()
        onDispose { observer?.stopWatching() }
    }

    // 计算可见条目:根据搜索文本和 tag 过滤
    val filteredEntries = entries.filter { e ->
        val matchSearch = searchQuery.isBlank() ||
                (e.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                e.body.contains(searchQuery, ignoreCase = true)
        val matchTag = selectedTag == null || e.tag == selectedTag
        matchSearch && matchTag
    }

    val filteredRuns = remember(filteredEntries, runAnchors) { aggregateByRun(filteredEntries, runAnchors) }

    // Runtime 页面:统计各 tag 出现次数,用于 Chip 显示
    val tagCounts = remember(entries) {
        entries.mapNotNull { it.tag }.groupBy { it }.mapValues { it.value.size }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = logType.displayName,
                onBack = { activity.finish() },
                onExportSummary = if (logType == LogType.ERROR) ({
                    val report = RpcFailureJournal.fileFor(logType.file.parentFile, System.currentTimeMillis())
                    if (!report.isFile) {
                        ToastUtil.show(context, "当前账号今日暂无异常请求统计")
                    } else {
                        val exported = FileUtil.exportFile(report)
                        ToastUtil.show(context, if (exported != null) "已导出: ${exported.path}" else "导出失败")
                    }
                }) else null,
                onExport = {
                    val exported = FileUtil.exportFile(logType.file)
                    if (exported != null) {
                        ToastUtil.show(context, "已导出: " + exported.path)
                    } else {
                        ToastUtil.show(context, "导出失败")
                    }
                },
                onClear = {
                    if (FileUtil.clearFile(file)) {
                        updateEntries(logReader.reload(file), reset = true)
                        searchQuery = ""
                        selectedTag = null
                        ToastUtil.show(context, "已清空")
                    }
                },
                onShare = if (logType == LogType.RUNTIME || logType == LogType.DEBUG) {
                    {
                        if (!sharing) {
                            sharing = true
                            val source = file
                            ToastUtil.show(context, "正在准备日志分享")
                            shareScope.launch {
                                try {
                                    // 抓包文件可能很大，复制完整且带账号名的副本不能阻塞界面。
                                    val shared = withContext(Dispatchers.IO) {
                                        FileUtil.copyForShare(source, File(context.cacheDir, "share"))
                                    }
                                    if (shared == null) {
                                        ToastUtil.show(context, "分享失败")
                                    } else {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            shared
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, shared.name)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        activity.startActivity(Intent.createChooser(shareIntent, "分享日志"))
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    ToastUtil.show(context, "分享失败")
                                } finally {
                                    sharing = false
                                }
                            }
                        }
                    }
                } else null
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── 搜索框 ──────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "",
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                "×",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(end = 12.dp).clickable { searchQuery = "" }
                            )
                        }
                    }
                )
            }
            // ── Runtime tag 过滤条 ──────────────────────────────────────
            if (logType == LogType.RUNTIME && tagCounts.isNotEmpty()) {
                val tags = tagCounts.keys.sorted()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 全部 Chip
                    TagChip(
                        label = "全部",
                        count = entries.size,
                        selected = selectedTag == null,
                        onClick = { selectedTag = null }
                    )
                    // 各 tag Chip
                    for (tag in tags) {
                        TagChip(
                            label = tag,
                            count = tagCounts[tag]!!,
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag }
                        )
                    }
                }
            }
            // ── 日志列表 ────────────────────────────────────────────────
            if (filteredEntries.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty() || selectedTag != null) "无匹配日志" else "(空)",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (viewMode == LogViewMode.BY_RUN) {
                        itemsIndexed(filteredRuns.asReversed(), key = { _, run -> "r-${run.runIndex}-${run.startLineNumber}" }) { _, run ->
                            RunEntryCard(run)
                        }
                    } else {
                        itemsIndexed(
                            filteredEntries.asReversed(),
                            key = { _, e -> "${e.lineNumber}-${e.hashCode()}" }
                        ) { _, entry ->
                            LogEntryCard(entry)
                        }
                    }
                }
            }
        }
    }
}

/** Tag 过滤 Chip:圆角小药片,选中时高亮 */
@Composable
private fun TagChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.primaryContainer
        else MiuixTheme.colorScheme.surfaceContainer
    val fg = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
        Text(
            text = count.toString(),
            fontSize = 11.sp,
            color = fg.copy(alpha = 0.7f)
        )
    }
}

/** 单条日志卡片:标题(TAG)+ 右上时间戳 + 下方正文 */
@Composable
fun LogEntryCard(entry: LogEntry) {
    SelectionContainer {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.tag ?: "日志",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = entry.time ?: "",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            if (entry.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.body,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun RunEntryCard(run: LogRunEntry) {
    val shown = run.entries
    var expanded by remember { mutableStateOf(true) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${run.runIndex} 轮" + (run.startTime?.let { "  $it" } ?: ""),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.primary
            )
            Text(
                text = shown.size.toString() + " 条" + if (expanded) " ▾" else " ▸",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            val text = shown.joinToString("\n") { e ->
                buildString {
                    if (e.time != null) append(e.time).append(" ")
                    if (e.tag != null) append(e.tag).append(": ")
                    append(e.body)
                }
            }
            SelectionContainer {
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * 通用顶部栏:返回图标 + 可选的操作图标(导入/导出/删除) + 横向 marquee 滚动的标题。
 * 仿 LSPosed 日志页样式。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogTopBar(
    title: String,
    onBack: () -> Unit,
    onImport: (() -> Unit)? = null,
    onExport: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    onExportSummary: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExecute: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1
            )
            actions()
            if (onImport != null) {
                IconButton(onClick = onImport) {
                    // 导入图标:把 Upload 旋转 180°(朝下)与导出(朝上)区分
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "导入",
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.rotate(180f)
                    )
                }
            }
            if (onExportSummary != null) {
                TextButton(text = "导出统计", onClick = onExportSummary)
            }
            if (onExport != null) {
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = "导出",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (onClear != null) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "分享",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
            if (onExecute != null) {
                IconButton(onClick = onExecute) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "执行",
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }
    }
}

/** 日志查看时最多从文件尾部读取的字节数(避免大文件全量加载导致卡顿) */
private const val MAX_TAIL_BYTES = 4 * 1024 * 1024L

/** 日志查看时最多展示的条目数 */
private const val MAX_LOG_ENTRIES = 5000

/**
 * 从文件尾部读取文本,最多 maxBytes 字节。
 * 若非从头读取,会丢弃首个可能被截断的行。
 */
private fun readTailText(file: File, maxBytes: Long): Pair<Long, String> {
    if (!file.exists()) return 0L to ""
    RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        if (length <= 0L) return 0L to ""
        val start = maxOf(0L, length - maxBytes)
        raf.seek(start)
        val bytes = ByteArray((length - start).toInt())
        raf.readFully(bytes)
        var text = String(bytes, Charsets.UTF_8)
        if (start > 0L) {
            val idx = text.indexOf('\n')
            text = if (idx >= 0) text.substring(idx + 1) else ""
        }
        return length to text
    }
}

/**
 * 读取日志文件并按行解析为条目;无时间戳的行合并到上一条(多行日志聚合为同一卡片)。
 * 仅读取文件尾部,并限制最大条目数,保证大文件也能快速打开。
 */
private val TIME_REGEX = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(?:(\\w+):\\s*)?(.*)$")

private class LogTailReader {
    private val entries = ArrayDeque<LogEntry>()

    /** 已解析到的文件偏移,下次从这里继续读 */
    private var consumed = 0L

    /** 尚无换行的尾部残行:写入方可能只写了一半,留到下次与新增内容拼接 */
    private var pending = ""
    private var lineNumber = 0
    private var initialized = false
    private var currentFile: File? = null
    private var snapshot: List<LogEntry> = emptyList()

    /** 读取新增内容并返回条目快照;无新增时返回上一次的同一个实例 */
    fun poll(target: File): List<LogEntry> {
        if (target != currentFile) {
            // 跨天等导致目标文件变化:重新整份加载
            currentFile = target
            initialized = false
        }
        val length = if (target.exists()) target.length() else 0L
        if (!initialized || length < consumed || length - consumed > MAX_TAIL_BYTES) {
            // 首次打开,或文件被清空/轮转:整份重读尾部
            reset()
            try {
                loadTail(target)
            } catch (e: Exception) {
                initialized = false
                return snapshot
            }
            initialized = true
            return publish()
        }
        if (length == consumed) {
            return snapshot
        }
        val appended = readAppended(target) ?: return snapshot
        consumed += appended.first
        appendLines(pending + appended.second)
        pending = ""
        return publish()
    }

    /** 强制整份重读(如日志被清空后) */
    fun reload(target: File): List<LogEntry> {
        initialized = false
        return poll(target)
    }

    private fun reset() {
        entries.clear()
        snapshot = emptyList()
        consumed = 0L
        pending = ""
        lineNumber = 0
    }

    private fun publish(): List<LogEntry> {
        snapshot = entries.toList()
        return snapshot
    }

    /** 整份读取文件尾部,并把「还没写完的最后一行」留作 pending */
    private fun loadTail(target: File) {
        val (length, text) = readTailText(target, MAX_TAIL_BYTES)
        val cut = text.lastIndexOf('\n')
        if (cut < 0) {
            pending = text
        } else {
            appendLines(text.substring(0, cut + 1))
            pending = text.substring(cut + 1)
        }
        consumed = length
    }

    /**
     * 从 consumed 读到文件末尾,返回(已消费字节数, 文本);还没有完整的一行时返回 null。
     *
     * <p>只取到最后一个换行为止:换行字节不会出现在多字节序列中间,这样既不会把半个字符
     * 解成乱码,也不会丢掉「写到一半的那一行」。
     */
    private fun readAppended(target: File): Pair<Long, String>? {
        return try {
            RandomAccessFile(target, "r").use { raf ->
                val length = raf.length()
                if (length <= consumed || length - consumed > MAX_TAIL_BYTES) {
                    return null
                }
                raf.seek(consumed)
                val bytes = ByteArray((length - consumed).toInt())
                raf.readFully(bytes)
                val newline = bytes.lastIndexOf('\n'.code.toByte())
                if (newline < 0) {
                    return null
                }
                (newline + 1).toLong() to String(bytes, 0, newline + 1, Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            null
        }
    }

    /** 按行解析并追加;无时间戳的行视为上一条的续行,合并到同一张卡片 */
    private fun appendLines(text: String) {
        if (text.isEmpty()) {
            return
        }
        val lines = text.split('\n')
        // 结尾的换行不构成一行,split 会多出一个空串,直接跳过
        val count = if (lines.last().isEmpty()) lines.size - 1 else lines.size
        for (i in 0 until count) {
            val line = lines[i].removeSuffix("\r")
            lineNumber++
            val match = TIME_REGEX.find(line)
            if (match != null) {
                entries.addLast(
                    LogEntry(
                        lineNumber = lineNumber,
                        time = match.groupValues[1],
                        tag = match.groupValues[2].takeIf { it.isNotEmpty() },
                        body = match.groupValues[3]
                    )
                )
            } else if (entries.isNotEmpty()) {
                val last = entries.removeLast()
                entries.addLast(last.copy(body = last.body + "\n" + line))
            } else {
                entries.addLast(LogEntry(lineNumber, null, null, line))
            }
            // 超出上限时丢弃最早的条目,保证内存占用可控
            while (entries.size > MAX_LOG_ENTRIES) {
                entries.removeFirst()
            }
        }
    }
}
