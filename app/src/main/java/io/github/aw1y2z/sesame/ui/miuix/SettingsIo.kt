package io.github.aw1y2z.sesame.ui.miuix

import android.os.Handler
import android.os.Looper
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import java.util.concurrent.Executors

/** App 进程的配置单例只能有一个读写者。main 立即准入，不排队过期账号的保存。 */
internal object SettingsIo {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Sesame-SettingsIO").apply { isDaemon = true }
    }
    val listeners = linkedSetOf<() -> Unit>()
    var busy = false
        private set
    var loaded = false
        private set
    var account: String? = null
        private set
    var revision = 0
        private set

    fun owns(uid: String?): Boolean = loaded && account == uid.orEmpty() && ConfigV2.INSTANCE.isInit &&
        UserIdMap.getCurrentUid().orEmpty() == uid.orEmpty()

    /** 删除账号与编辑共用单工准入，退出页面不取消已开始的删除/卸载。 */
    fun submitExternal(work: () -> Boolean, complete: (Boolean) -> Unit): Boolean =
        submit(null, true, work, complete, external = true)

    fun invalidate() {
        loaded = false
        notifyChanged()
    }

    /** 仅用于导入在替换文件/重建模型之前失败；complete 的 finally 统一通知页面。 */
    fun restoreUnchanged(uid: String?) {
        if (account == uid.orEmpty() && ConfigV2.INSTANCE.isInit &&
            UserIdMap.getCurrentUid().orEmpty() == uid.orEmpty()) loaded = true
    }

    fun submit(uid: String?, reload: Boolean = false, work: () -> Boolean,
               complete: (Boolean) -> Unit, external: Boolean = false): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (busy || (!reload && !owns(uid))) return false
        val capturedUid = uid.orEmpty()
        busy = true
        if (reload) loaded = false
        notifyChanged()
        try {
            worker.execute {
                val success = try {
                    // Main 的账号删除也会 unload 单例，不能将另一账号的字段保存到旧目录。
                    (reload || UserIdMap.getCurrentUid().orEmpty() == capturedUid) && work()
                } catch (error: Throwable) {
                    report("work", error)
                    false
                }
                main.post {
                    if (reload && success && !external) {
                        account = capturedUid
                        loaded = true
                        revision++
                    }
                    busy = false
                    finish(complete, success && (reload || owns(uid)))
                }
            }
        } catch (error: Throwable) {
            report("dispatch", error)
            busy = false
            finish(complete, false)
        }
        return true
    }

    private fun finish(complete: (Boolean) -> Unit, success: Boolean) {
        try { complete(success) } catch (error: Throwable) { report("callback", error) }
        finally { notifyChanged() }
    }

    private fun notifyChanged() {
        listeners.toList().forEach { listener ->
            try { listener() } catch (error: Throwable) { report("listener", error) }
        }
    }

    private fun report(stage: String, error: Throwable) {
        // JSON 解析异常的 message 可能含用户正文，仅记录类型及调用栈。
        android.util.Log.e("SettingsIo", "$stage failed: ${error.javaClass.name}\n${error.stackTrace.joinToString("\n")}")
    }
}
