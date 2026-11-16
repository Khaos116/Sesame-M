package io.github.aw1y2z.sesame.ui.miuix

import android.app.ProgressDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.ui.HostBridge
import io.github.aw1y2z.sesame.util.ToastUtil

/** 三层配置页共享准入；持久操作不绑定 Activity 生命周期，销毁只撤销 UI 回调。 */
open class MiuixConfigActivity : MiuixBaseActivity() {
    private var editorUid: String? = null
    private var resumed = false
    private var waitingForPrepare = false
    private var forcePrepare = false
    private var ioVersion by mutableStateOf(0)
    private var progress: ProgressDialog? = null
    private val ioListener: () -> Unit = {
        ioVersion++
        updateProgress()
        if (resumed && waitingForPrepare && !SettingsIo.busy && !isDestroyed && !isFinishing) prepareEditor()
    }

    protected fun setEditorContent(uid: String?, forceReload: Boolean = false, content: @Composable () -> Unit) {
        editorUid = uid
        forcePrepare = forceReload
        SettingsIo.listeners.add(ioListener)
        setAppContent {
            @Suppress("UNUSED_VARIABLE") val version = ioVersion
            if (SettingsIo.owns(uid)) {
                key(SettingsIo.revision) { content() }
            } else {
                top.yukonga.miuix.kmp.basic.Text("配置加载中，若账号已变化请返回重新打开")
            }
        }
        prepareEditor()
    }

    private fun prepareEditor() {
        if (SettingsIo.busy) { waitingForPrepare = true; return }
        waitingForPrepare = false
        val force = forcePrepare
        forcePrepare = false
        if (!force && SettingsIo.owns(editorUid)) return
        val uid = editorUid
        runEditorIo(reload = true, work = {
            if (force) {
                Model.initAllModel()
                ConfigPreload.prepare(uid)
            } else ConfigPreload.ensurePrepared(uid)
            ConfigV2.INSTANCE.isInit
        }, onSuccess = {}, onFailure = { ToastUtil.show(this, "配置加载失败，请返回重试") })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        updateProgress()
        // 同账号子页返回不重载，保留内存中尚未保存的字段。
        prepareEditor()
    }

    override fun onPause() {
        resumed = false
        progress?.dismiss()
        progress = null
        super.onPause()
    }

    override fun onDestroy() {
        SettingsIo.listeners.remove(ioListener)
        progress?.dismiss()
        progress = null
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun updateProgress() {
        if (resumed && SettingsIo.busy && !isFinishing && !isDestroyed) {
            if (progress == null) progress = ProgressDialog(this).apply {
                setMessage("正在处理配置…")
                setCancelable(false)
                setCanceledOnTouchOutside(false)
                show()
            }
        } else {
            progress?.dismiss()
            progress = null
        }
    }

    internal fun canEditConfig(): Boolean {
        return !SettingsIo.busy && SettingsIo.owns(editorUid)
    }

    internal fun closeEditor() {
        if (SettingsIo.busy) return
        if (!SettingsIo.owns(editorUid)) finish() else saveConfig { finish() }
    }

    internal fun runEditorIo(reload: Boolean = false, work: () -> Boolean,
                             onSuccess: () -> Unit,
                             onFailure: () -> Unit = { ToastUtil.show(this, "操作失败，请重试") }) {
        if (isFinishing || isDestroyed) return
        val uid = editorUid
        SettingsIo.submit(uid, reload, work, complete = { success ->
            if (!isDestroyed && !isFinishing && editorUid == uid) {
                if (success) onSuccess() else onFailure()
            }
        })
    }

    internal fun saveConfig(restart: Boolean = true, onSaved: () -> Unit) {
        val uid = editorUid
        val context = applicationContext
        runEditorIo(work = {
            if (!ConfigV2.INSTANCE.isInit) false
            else if (!ConfigV2.hasFieldChanges()) true
            else if (ConfigV2.save(uid, true)) {
                // 已开始的保存即使页面销毁仍完成，并通知宿主重载。
                if (restart && !uid.isNullOrEmpty()) HostBridge.send(context, "restart", uid, null, false)
                true
            } else false
        }, onSuccess = onSaved, onFailure = { ToastUtil.show(this, "保存失败，改动未落盘") })
    }
}
