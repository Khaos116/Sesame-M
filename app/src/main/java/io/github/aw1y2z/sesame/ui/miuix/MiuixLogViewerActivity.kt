package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
 * 列表从底部开始排(reverseLayout),而列表初始位置就是最新一条,
 * 所以一打开页面看到的就是最新日志;之后每 [LOG_REFRESH_INTERVAL_MS] 检查一次日志文件,
 * 有新内容就刷新并跟到最新;上滑翻历史时暂停跟随(不会被新日志顶跑),滑回最新后自动恢复。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogScreen(activity: MiuixLogViewerActivity, logType: LogType) {
    val context = LocalContext.current
    // 增量读取器:只在文件真有新增时解析新增部分,避免运行日志每秒整份重读重解析
    val logReader = remember(logType) { LogTailReader() }
    var entries by remember(logType) { mutableStateOf(logReader.poll(logType.file)) }
    var revision by remember(logType) { mutableStateOf(0) }
    var browsingHistory by remember(logType) { mutableStateOf(false) }
    // 搜索文本
    var searchQuery by remember { mutableStateOf("") }
    // Runtime 页面额外支持 tag 过滤
    var selectedTag by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // 列表停下时按位置判断是否停在最新(索引 0):还能往回滚就说明用户翻到上面看历史了
    LaunchedEffect(listState, logType) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect { browsingHistory = listState.canScrollBackward }
    }

    // 定时刷新:只读新增的字节并增量解析;有新条目才更新列表与滚动标记
    LaunchedEffect(logType) {
        while (true) {
            delay(LOG_REFRESH_INTERVAL_MS)
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                continue
            }
            val file = logType.file
            val updated = withContext(Dispatchers.IO) { logReader.poll(file) }
            // 无新增时读取器返回原实例:据此跳过重组(否则每秒都要重建整份列表)
            if (updated !== entries) {
                entries = updated
                revision++
            }
        }
    }

    // 有新日志时把视角钉回最新一条
    LaunchedEffect(revision) {
        if (!browsingHistory && entries.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 计算可见条目:根据搜索文本和 tag 过滤
    val filteredEntries = entries.filter { e ->
        val matchSearch = searchQuery.isBlank() ||
                (e.tag?.contains(searchQuery, ignoreCase = true) == true) ||
                e.body.contains(searchQuery, ignoreCase = true)
        val matchTag = selectedTag == null || e.tag == selectedTag
        matchSearch && matchTag
    }

    // Runtime 页面:统计各 tag 出现次数,用于 Chip 显示
    val tagCounts = remember(entries) {
        entries.mapNotNull { it.tag }.groupBy { it }.mapValues { it.value.size }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = logType.displayName,
                onBack = { activity.finish() },
                onExport = {
                    val exported = FileUtil.exportFile(logType.file)
                    if (exported != null) {
                        ToastUtil.show(context, "已导出: " + exported.path)
                    } else {
                        ToastUtil.show(context, "导出失败")
                    }
                },
                onClear = {
                    if (FileUtil.clearFile(logType.file)) {
                        entries = logReader.reload(logType.file)
                        searchQuery = ""
                        selectedTag = null
                        ToastUtil.show(context, "已清空")
                    }
                },
                onShare = if (logType == LogType.RUNTIME) {
                    {
                        val file = logType.file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, file.name)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(Intent.createChooser(shareIntent, "分享日志"))
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
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
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
private const val MAX_TAIL_BYTES = 1024 * 1024L

/** 日志查看时最多展示的条目数 */
private const val MAX_LOG_ENTRIES = 500

/**
 * 从文件尾部读取文本,最多 maxBytes 字节。
 * 若非从头读取,会丢弃首个可能被截断的行。
 */
private fun readTailText(file: File, maxBytes: Long): String {
    val length = file.length()
    if (length <= 0L) {
        return ""
    }
    val start = maxOf(0L, length - maxBytes)
    RandomAccessFile(file, "r").use { raf ->
        raf.seek(start)
        val bytes = ByteArray((length - start).toInt())
        raf.readFully(bytes)
        var text = String(bytes, Charsets.UTF_8)
        if (start > 0L) {
            val idx = text.indexOf('\n')
            text = if (idx >= 0) text.substring(idx + 1) else ""
        }
        return text
    }
}

/** 日志自动刷新间隔(毫秒) */
private const val LOG_REFRESH_INTERVAL_MS = 1000L

// 两种行格式：
// runtime/system/debug: 13:34:10.251 RUNTIME: 执行结束-庄园
// forest/farm/goldenbeans/other: 19:37:33.150 森林签到...
private val TIME_REGEX = Regex("^(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+(?:(\\w+):\\s*)?(.*)$")

/**
 * 日志增量读取器：记住已解析到的文件偏移,只解析新增的字节。
 *
 * <p>日志由注入进程持续追加,整份重读会让运行日志页每秒重读 1MB、重解析 500 条并让整
 * 列表重组。这里只读新增部分;无新增时返回上一次的同一个列表实例,调用方据此跳过重组。
 *
 * <p>文件被清空或轮转(长度回退)时自动退回整份重读。
 */
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
        if (!initialized || length < consumed) {
            // 首次打开,或文件被清空/轮转:整份重读尾部
            reset()
            loadTail(target)
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
        val text = readTailText(target, MAX_TAIL_BYTES)
        val cut = text.lastIndexOf('\n')
        if (cut < 0) {
            pending = text
        } else {
            appendLines(text.substring(0, cut + 1))
            pending = text.substring(cut + 1)
        }
        consumed = if (target.exists()) target.length() else 0L
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
                if (length <= consumed) {
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