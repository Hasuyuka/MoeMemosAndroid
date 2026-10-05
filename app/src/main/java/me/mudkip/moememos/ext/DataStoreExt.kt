package me.mudkip.moememos.ext

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import androidx.datastore.dataStoreFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.mudkip.moememos.MoeMemosApp
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.util.SettingsSerializer
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

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
 * 进程内最后一次**真正从 DataStore 读到**的设置。
 *
 * DataStore 的第一次发射是异步的，而各处此前都拿 `Settings()`（编造出来的默认值）当
 * `collectAsStateWithLifecycle` 的 `initialValue`。于是每次列表页重新进入组合
 * （打开备忘返回、切回标签页、面板被重建），**第一帧**都会按默认排序（创建时间）和
 * 默认布局（大卡片）渲染，随后才被真实设置改回去。
 *
 * 这一帧足以把整个列表顺序换掉，也足以让 `layout` 看起来"被切换过"。
 * beta.16 的真机埋点把它拍了下来——同一个源列表、同一个 rev：
 *
 *     SORT 重算 rev=1 src=136599319 srcN=73 mode=CREATED dir=DESCENDING
 *     SORT 重算 rev=1 src=136599319 srcN=73 mode=TITLE    dir=DESCENDING
 *
 * 前者是那一帧的谎，后者才是用户的真实设置。用户看到的"返回后随机跳到一个地方"
 * （列表按错的顺序渲染了一帧）和"跳回顶部"（布局被误判为切换过）都是它的后果。
 *
 * 拿上一次的真实值当初始值，新建的收集者第一帧就是对的；只有冷启动的第一次才会
 * 落到默认值，而那时本来也没有滚动位置需要保住。
 */
private val latestKnownSettings = AtomicReference<Settings?>(null)

/**
 * 读设置。UI 一律用它，不要再直接写
 * `settingsDataStore.data.collectAsStateWithLifecycle(initialValue = Settings())`
 * ——那等于每次重新收集都先撒一帧谎，而列表恰好对那一帧非常敏感。
 */
@Composable
fun Context.settingsState(): Settings {
    val settings by settingsDataStore.data.collectAsStateWithLifecycle(
        initialValue = latestKnownSettings.get() ?: Settings()
    )
    // 给下一次收集用（可能是另一个页面，也可能是组合被重建后的自己）。
    latestKnownSettings.set(settings)
    return settings
}

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
