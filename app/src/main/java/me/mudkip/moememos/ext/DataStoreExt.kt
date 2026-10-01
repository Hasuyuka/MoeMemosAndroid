package me.mudkip.moememos.ext

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.dataStoreFile
import me.mudkip.moememos.MoeMemosApp
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.util.SettingsSerializer
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 注意：文件名里的 "v3" 是历史遗留（早年手工加的版本后缀），与
 * [Settings.CURRENT_SCHEMA_VERSION] 无关——结构版本由文件内的 schemaVersion 字段承担。
 *
 * **不要重命名这个文件**：重命名会让所有既有用户的账户列表与偏好凭空消失。
 */
val Context.settingsDataStore: DataStore<Settings> by dataStore(
    fileName = SETTINGS_FILE_NAME,
    serializer = SettingsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler(settingsCorruptionRecovery())
)

private const val SETTINGS_FILE_NAME = "settings_v3.json"

/**
 * 文件损坏时 DataStore 会用这里返回的默认值覆盖它。覆盖**之前**先把原始字节另存一份，
 * 否则用户的账户列表与草稿就永久消失，连人工抢救的机会都没有。
 *
 * 为什么用 [MoeMemosApp.CONTEXT] 而不是 lambda 接收者：这个 lambda 是
 * ReplaceFileCorruptionHandler 的构造参数（SAM 转换），其中没有可用的 Context 接收者——
 * 直接调用 Context 的扩展函数会编译成 Unresolved reference。
 * 改用 App 里已有的全局应用上下文：它在 attachBaseContext 中最早赋值，
 * 而任何位置访问 settingsDataStore 都远晚于此，因此不存在未初始化窗口。
 */
private fun settingsCorruptionRecovery(): (CorruptionException) -> Settings =
    { cause ->
        preserveCorruptSettingsFile(MoeMemosApp.CONTEXT.applicationContext, cause)
        Settings()
    }

private fun preserveCorruptSettingsFile(context: Context, cause: Throwable) {
    val source = runCatching { context.dataStoreFile(SETTINGS_FILE_NAME) }.getOrNull() ?: return
    if (!source.exists()) {
        return
    }
    try {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(source.parentFile, "$SETTINGS_FILE_NAME.corrupt-$stamp")
        source.copyTo(target, overwrite = false)
        Timber.e(cause, "settings 文件无法解析，已另存到 %s 并回退默认值", target.absolutePath)
    } catch (exception: Exception) {
        Timber.e(exception, "settings 文件无法解析，且另存失败")
    }
}
