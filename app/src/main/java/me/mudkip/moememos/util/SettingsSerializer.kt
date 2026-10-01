package me.mudkip.moememos.util

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.mudkip.moememos.data.model.Settings
import java.io.InputStream
import java.io.OutputStream

object SettingsSerializer : Serializer<Settings> {
    override val defaultValue: Settings = Settings()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): Settings {
        val content = input.readBytes().decodeToString()
        if (content.isBlank()) {
            return defaultValue
        }
        return try {
            decode(content)
        } catch (exception: SerializationException) {
            // 结构坏到无法解析。以前这里直接静默返回默认值，等于把用户的账户列表与草稿
            // 悄悄抹掉，而且没有任何信号。现在抛 CorruptionException，交给
            // settingsDataStore 注册的 corruptionHandler——它会在覆盖之前把原始文件
            // 另存一份，保留人工抢救的机会。
            throw CorruptionException("Cannot read settings data.", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptionException("Cannot read settings data.", exception)
        }
    }

    private fun decode(content: String): Settings {
        val root = json.parseToJsonElement(content).jsonObject
        val storedVersion = root[Settings.SCHEMA_VERSION_KEY]?.jsonPrimitive?.intOrNull
            ?: Settings.LEGACY_SCHEMA_VERSION

        if (storedVersion > Settings.CURRENT_SCHEMA_VERSION) {
            // 文件来自更新版本的应用。不做破坏性处理：按现有字段尽力读取
            //（ignoreUnknownKeys 会忽略多出来的字段），并把更高的版本号原样带回去，
            // 避免降级运行再写回时把版本号改小、让新版应用误判数据结构。
            return json.decodeFromString<Settings>(content)
                .copy(schemaVersion = storedVersion)
        }

        val upgraded = SettingsMigrations.migrate(root, storedVersion)
        return json.decodeFromString<Settings>(upgraded.toString())
    }

    override suspend fun writeTo(
        t: Settings,
        output: OutputStream
    ) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
