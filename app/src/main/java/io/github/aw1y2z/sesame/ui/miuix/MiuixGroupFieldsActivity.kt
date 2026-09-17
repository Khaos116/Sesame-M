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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.ModelConfig
import io.github.aw1y2z.sesame.data.ModelField
import io.github.aw1y2z.sesame.data.ModelGroup
import io.github.aw1y2z.sesame.data.modelFieldExt.ChoiceModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.EmptyModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountOneModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectOneModelField
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/**
 * 配置字段页（三级）：显示某个分组下的所有配置字段。
 * 从 MiuixSettingsActivity 跳转进来，通过 Intent 传递 userId 和 groupCode。
 */
class MiuixGroupFieldsActivity : MiuixBaseActivity() {

    companion object {
        const val EXTRA_USER_ID = "userId"
        const val EXTRA_GROUP_CODE = "groupCode"
    }

    private var userId: String? = null
    internal var groupCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = intent.getStringExtra(EXTRA_USER_ID)
        groupCode = intent.getStringExtra(EXTRA_GROUP_CODE)
        setAppContent {
            groupCode?.let { code ->
                val group = ModelGroup.entries.find { it.name == code }
                if (group != null) {
                    GroupFieldsContent(activity = this, userId = userId, groupCode = code, group = group)
                } else {
                    top.yukonga.miuix.kmp.basic.Text("分组不存在: $code", color = MiuixTheme.colorScheme.error)
                }
            } ?: run {
                top.yukonga.miuix.kmp.basic.Text("缺少参数", color = MiuixTheme.colorScheme.error)
            }
        }
    }

    override fun onBackPressed() {
        save()
        super.onBackPressed()
    }

    /** 顶部返回按钮与系统返回统一入口：先保存再退出。 */
    fun saveAndFinish() {
        save()
        finish()
    }

    /**
     * 统一落盘入口：本页字段变更只写内存，只有真正退出时才调用这里写一次磁盘。
     * 先用 isModify() 判断是否有改动（无改动直接短路，不写盘、不提示），
     * 确认有改动后走 force=true，避免 ConfigV2.save() 内部再做一次全量序列化比较。
     */
    fun save() {
        if (userId == null) return
        if (!ConfigV2.isModify(userId)) return
        if (ConfigV2.save(userId, true)) {
            ToastUtil.show(this, "保存成功！")
        }
    }
}

/**
 * 扁平化后的列表行：把「模型标题」和「字段」都提升为 LazyColumn 的独立 item，
 * 让虚拟化真正下沉到字段级。
 *
 * 原先每个 ModelConfig 是一个 item、内部用 fields.forEach 组合全部字段，
 * 导致 Forest 组（77 个字段）一旦进入视口就要一次性组合、measure、layout 所有字段。
 */
private sealed interface GroupFieldsRow {
    val key: String

    data class Header(override val key: String, val title: String) : GroupFieldsRow

    data class Field(
        override val key: String,
        val modelCode: String,
        val field: ModelField<*>,
        val first: Boolean,
        val last: Boolean
    ) : GroupFieldsRow
}

@Composable
fun GroupFieldsContent(activity: MiuixGroupFieldsActivity, userId: String?, groupCode: String, group: ModelGroup) {
    // 只在分组变化时构建一次扁平行列表；字段对象由 ConfigV2 单例持有，引用稳定。
    val rows = remember(group) {
        val list = ArrayList<GroupFieldsRow>()
        Model.getGroupModelConfig(group).values.forEach { mc ->
            val fields = mc.fields.values.toList()
            if (fields.isEmpty()) return@forEach
            list.add(GroupFieldsRow.Header(key = "header:${mc.getCode()}", title = mc.name ?: ""))
            fields.forEachIndexed { index, field ->
                list.add(
                    GroupFieldsRow.Field(
                        key = "field:${mc.getCode()}:${field.code}",
                        modelCode = mc.getCode(),
                        field = field,
                        first = index == 0,
                        last = index == fields.lastIndex
                    )
                )
            }
        }
        list
    }

    Scaffold(
        topBar = {
            LogTopBar(
                title = group.getName(),
                onBack = { activity.saveAndFinish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is GroupFieldsRow.Header -> SmallTitle(text = row.title)
                    is GroupFieldsRow.Field -> GroupFieldRow(
                        activity = activity,
                        userId = userId,
                        groupCode = groupCode,
                        row = row
                    )
                }
            }
        }
    }
}

/**
 * 单个字段行。相邻行背景一致、圆角只在一组字段的首尾外露，
 * 因此视觉上仍是一张连续的卡片，但每一行都能被 LazyColumn 独立复用/回收。
 */
@Composable
private fun GroupFieldRow(
    activity: MiuixGroupFieldsActivity,
    userId: String?,
    groupCode: String,
    row: GroupFieldsRow.Field
) {
    val shape = when {
        row.first && row.last -> RoundedCornerShape(16.dp)
        row.first -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        row.last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        else -> RectangleShape
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainer, shape)
            .padding(horizontal = 16.dp)
            .padding(
                top = if (row.first) 8.dp else 0.dp,
                bottom = if (row.last) 16.dp else 0.dp
            )
    ) {
        val field = row.field
        when (field.type) {
            "SELECT", "SELECT_ONE", "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE" -> {
                ArrowPreference(
                    title = field.name ?: "",
                    onClick = {
                        activity.startActivity(
                            Intent(activity, MiuixSelectionEditActivity::class.java).apply {
                                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
                                putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, groupCode)
                                putExtra(MiuixSelectionEditActivity.EXTRA_FIELD_CODE, field.code)
                                putExtra(MiuixSelectionEditActivity.EXTRA_MODEL_CODE, row.modelCode)
                            }
                        )
                    }
                )
            }
            else -> {
                // 只写内存，落盘统一在 saveAndFinish() / onBackPressed() 完成
                FieldItem(field = field)
            }
        }
    }
}


