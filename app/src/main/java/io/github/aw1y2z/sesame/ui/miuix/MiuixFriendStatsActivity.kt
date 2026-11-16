package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.entity.FriendWatch
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import io.github.aw1y2z.sesame.util.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 待打开好友标记文件名，与宿主侧 ApplicationHook.PENDING_PROFILE_FILE 保持一致 */
private const val PENDING_PROFILE_FILE = "pending_profile.txt"

/** 宿主包名 */
private const val HOST_PACKAGE = "com.eg.android.AlipayGphone"

private val profileMarkerLock = Any()

/** 好友统计二级页:展示单向好友列表及其互动记录,与其它二级页面统一风格(LogTopBar)。 */
class MiuixFriendStatsActivity : MiuixBaseActivity() {

    var currentUserId by mutableStateOf<String?>(null)
    private val profileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var accountJob: Job? = null
    private var profileJob: Job? = null
    private var profileOpening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            FriendStatsScreen(this)
        }
    }

    fun openFriendProfile(userId: String) {
        if (profileOpening) return
        profileOpening = true
        val marker = "$userId\n${System.currentTimeMillis()}"
        val pendingFile = File(FileUtil.MAIN_DIRECTORY_FILE, PENDING_PROFILE_FILE)
        profileJob = profileScope.launch {
            var launched = false
            try {
                val saved = withContext(Dispatchers.IO) {
                    synchronized(profileMarkerLock) {
                        ensureActive()
                        FileUtil.write2File(marker, pendingFile)
                    }
                }
                if (!saved) {
                    ToastUtil.show(this@MiuixFriendStatsActivity, "好友跳转标记保存失败，请重试")
                    return@launch
                }
                val intent = packageManager.getLaunchIntentForPackage(HOST_PACKAGE)
                if (intent == null) {
                    ToastUtil.show(this@MiuixFriendStatsActivity, "无法打开支付宝")
                    return@launch
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                startActivity(intent)
                launched = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.printStackTrace(e)
                ToastUtil.show(this@MiuixFriendStatsActivity, "无法打开好友主页")
            } finally {
                try {
                    // A cancelled click must not leave its unused marker for a later host launch.
                    if (!launched) withContext(NonCancellable + Dispatchers.IO) {
                        try {
                            synchronized(profileMarkerLock) {
                                if (FileUtil.readFromFile(pendingFile) == marker) pendingFile.delete()
                            }
                        } catch (e: Exception) {
                            Log.printStackTrace(e)
                        }
                    }
                } finally {
                    profileOpening = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accountJob?.cancel()
        accountJob = profileScope.launch {
            try {
                currentUserId = withContext(Dispatchers.IO) { FileUtil.getPublishedUserId() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.printStackTrace(e)
            }
        }
    }

    override fun onPause() {
        accountJob?.cancel()
        profileJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        profileScope.cancel()
        super.onDestroy()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FriendStatsScreen(activity: MiuixFriendStatsActivity) {
    val userId = activity.currentUserId
    var friends by remember(userId) { mutableStateOf<List<FriendWatch>?>(null) }
    var query by remember { mutableStateOf("") }
    var sortKey by remember { mutableStateOf(FriendSort.WEEK) }
    var ascending by remember { mutableStateOf(false) }
    LaunchedEffect(userId) {
        friends = withContext(Dispatchers.IO) {
            try {
                if (userId != null) FriendWatch.getList(userId) else emptyList()
            } catch (e: Throwable) {
                // getList() 内部只兜了解析异常，读盘异常会穿透到这里；
                // 不接住就会永远停在「加载中…」，比报错更难察觉
                Log.printStackTrace(e)
                emptyList()
            }
        }
    }

    val listState = rememberLazyListState()

    val filtered = remember(friends, query) {
        friends?.let { all ->
            if (query.isBlank()) all else all.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    // 数据层已只统计通讯录好友，这里的按名字过滤只是兜底：
    // 万一还有历史遗留的无名记录，点开是空白页，不显示更合适。
    val shown = remember(filtered, sortKey, ascending) {
        filtered
            ?.filter { it.name.isNotBlank() }
            ?.let { list ->
                val byValue = when (sortKey) {
                    FriendSort.WEEK -> if (ascending) compareBy<FriendWatch> { it.weekGet }
                    else compareByDescending { it.weekGet }
                    FriendSort.ALL -> if (ascending) compareBy<FriendWatch> { it.allGet }
                    else compareByDescending { it.allGet }
                }
                // 数值相同时按名字排，避免每次重组顺序抖动
                list.sortedWith(byValue.thenBy { it.name })
            }
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = "好友统计",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ── 搜索框 ──────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "",
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索",
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Text(
                                "×",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable { query = "" }
                            )
                        }
                    }
                )
            }

            // ── 排序 ────────────────────────────────────────────────
            // 再点一次当前字段切升/降序；换字段时回到降序（默认想看收得多的）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // requestScrollToItem 只是登记，下一次测量时才生效，因此能和排序状态在同一帧落地：
                // 若放到 LaunchedEffect 里滚，会先按新顺序在旧滚动位置画一帧再跳回顶部，那一帧错位就是看到的闪
                SortChip("周收", sortKey == FriendSort.WEEK, ascending) {
                    listState.requestScrollToItem(0)
                    if (sortKey == FriendSort.WEEK) {
                        ascending = !ascending
                    } else {
                        sortKey = FriendSort.WEEK
                        ascending = false
                    }
                }
                SortChip("总收", sortKey == FriendSort.ALL, ascending) {
                    listState.requestScrollToItem(0)
                    if (sortKey == FriendSort.ALL) {
                        ascending = !ascending
                    } else {
                        sortKey = FriendSort.ALL
                        ascending = false
                    }
                }
            }

            if (shown == null || shown.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            shown == null -> "加载中…"
                            query.isNotEmpty() -> "(无匹配好友)"
                            else -> "(空)"
                        },
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(items = shown, key = { it.id }) { fw ->
                        FriendRow(fw) { activity.openFriendProfile(fw.id) }
                    }
                }
            }
        }
    }
}

/** 好友统计的排序字段。 */
private enum class FriendSort {
    WEEK, ALL
}

/** 排序芯片：箭头只在选中时出现，避免未选中的 chip 看起来已排好序 */
@Composable
private fun SortChip(label: String, selected: Boolean, ascending: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MiuixTheme.colorScheme.primaryContainer
    else MiuixTheme.colorScheme.surfaceContainer
    val fg = if (selected) MiuixTheme.colorScheme.onPrimaryContainer
    else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = fg)
        if (selected) {
            Text(text = if (ascending) "↑" else "↓", fontSize = 12.sp, color = fg)
        }
    }
}

/** 好友行：点一下打开该好友的支付宝主页，具体流程见 openFriendProfile()。 */
@Composable
private fun FriendRow(fw: FriendWatch, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = fw.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "开始统计: ${fw.startTime}",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "周收: ${fw.weekGet} g   总收: ${fw.allGet} g",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
        }
    }
}
