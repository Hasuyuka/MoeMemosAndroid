package me.mudkip.moememos.util

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.mudkip.moememos.data.model.LocalAccount
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.data.model.MemosAccount
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.data.model.UserData
import me.mudkip.moememos.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SettingsSerializerTest {

    private fun read(text: String): Settings =
        runBlocking { SettingsSerializer.readFrom(ByteArrayInputStream(text.toByteArray())) }

    private fun write(settings: Settings): String {
        val out = ByteArrayOutputStream()
        runBlocking { SettingsSerializer.writeTo(settings, out) }
        return out.toString("UTF-8")
    }

    private fun sampleSettings() = Settings(
        usersList = listOf(
            UserData(
                accountKey = "memos:https://memos.example.com:users/1",
                memosV1 = MemosAccount(
                    host = "https://memos.example.com",
                    name = "tester",
                    remoteIdentifier = "users/1",
                ),
                settings = UserSettings(
                    draft = "还没发出去的草稿",
                    editGesture = MemoEditGesture.DOUBLE,
                    autosave = true,
                ),
            ),
            UserData(accountKey = "local", local = LocalAccount()),
        ),
        currentUser = "local",
        appLockEnabled = true,
    )

    /** 引入版本字段之前写下的文件：没有 schemaVersion 键，但账户列表必须完整保留。 */
    private val legacyJson = """
        {
          "usersList": [
            {
              "accountKey": "memos:https://memos.example.com:users/1",
              "memosV1": {
                "host": "https://memos.example.com",
                "name": "tester",
                "remoteIdentifier": "users/1"
              },
              "settings": { "draft": "旧草稿", "editGesture": "LONG", "autosave": true }
            },
            { "accountKey": "local", "local": { "enabled": true, "startDateEpochSecond": 0 } }
          ],
          "currentUser": "local",
          "appLockEnabled": true
        }
    """.trimIndent()

    @Test
    fun `空文件回退默认值`() {
        val settings = read("")
        assertEquals(Settings(), settings)
        assertEquals(Settings.CURRENT_SCHEMA_VERSION, settings.schemaVersion)
    }

    @Test
    fun `只有空白字符的文件也回退默认值`() {
        assertEquals(Settings(), read("   \n\t "))
    }

    @Test
    fun `旧文件没有版本字段时账户列表完整保留`() {
        val settings = read(legacyJson)

        assertEquals(2, settings.usersList.size)
        assertEquals("local", settings.currentUser)
        assertTrue(settings.appLockEnabled)

        val remote = settings.usersList.first()
        assertEquals("https://memos.example.com", remote.memosV1?.host)
        assertEquals("旧草稿", remote.settings.draft)
        assertEquals(MemoEditGesture.LONG, remote.settings.editGesture)
        assertTrue(remote.settings.autosave)

        assertEquals("local", settings.usersList[1].accountKey)
        // 旧文件没有版本号，解码后应当落到当前版本（尚无结构变更，故等于 v1）
        assertEquals(Settings.CURRENT_SCHEMA_VERSION, settings.schemaVersion)
    }

    @Test
    fun `写入读回保持账户与偏好一致`() {
        val original = sampleSettings()
        val restored = read(write(original))
        assertEquals(original, restored)
        assertEquals(Settings.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun `文件版本高于当前时不清空数据且保留较高版本号`() {
        // 模拟「用户装过更新版本的应用又降级回来」：带一个未来才有的字段和更高的版本号
        val futureJson = """
            {
              "schemaVersion": 999,
              "usersList": [
                { "accountKey": "local", "local": { "enabled": true } }
              ],
              "currentUser": "local",
              "appLockEnabled": false,
              "someFutureField": { "nested": [1, 2, 3] }
            }
        """.trimIndent()

        val settings = read(futureJson)

        // 关键：不能因为版本不认识就把用户数据抹掉
        assertEquals(1, settings.usersList.size)
        assertEquals("local", settings.usersList.first().accountKey)
        // 版本号必须原样保留，否则降级运行写回后会让新版应用误判结构
        assertEquals(999, settings.schemaVersion)
    }

    @Test
    fun `无法解析的文件抛 CorruptionException 交给 corruptionHandler`() {
        // 以前这里会静默返回默认值，把用户的账户列表直接丢掉且毫无信号。
        // 现在必须抛出，好让 corruptionHandler 有机会先把原文件另存一份。
        assertThrows(CorruptionException::class.java) {
            read("{ 这不是 JSON")
        }
    }

    @Test
    fun `迁移管线按顺序逐级升级并统一盖版本号`() {
        val steps: Map<Int, (JsonObject) -> JsonObject> = mapOf(
            1 to { obj ->
                buildJsonObject {
                    obj.forEach { (key, value) -> put(key, value) }
                    put("addedInV2", JsonPrimitive(true))
                }
            },
            2 to { obj ->
                buildJsonObject {
                    obj.forEach { (key, value) -> put(key, value) }
                    put("addedInV3", JsonPrimitive(true))
                }
            },
        )
        val root = buildJsonObject { put("accountKey", JsonPrimitive("local")) }

        val result = SettingsMigrations.migrate(root, from = 1, to = 3, steps = steps)

        assertTrue(result.containsKey("addedInV2"))
        assertTrue(result.containsKey("addedInV3"))
        assertEquals("local", result["accountKey"]?.toString()?.trim('"'))
        assertEquals(3, result[Settings.SCHEMA_VERSION_KEY]?.toString()?.toInt())
    }

    @Test
    fun `迁移步骤缺失时抛异常而不是跳级`() {
        val root = buildJsonObject { put("accountKey", JsonPrimitive("local")) }

        assertThrows(IllegalStateException::class.java) {
            SettingsMigrations.migrate(root, from = 1, to = 2, steps = emptyMap())
        }
    }

    @Test
    fun `无需迁移时原样返回`() {
        val root = buildJsonObject { put("accountKey", JsonPrimitive("local")) }
        val result = SettingsMigrations.migrate(root, from = 1, to = 1, steps = emptyMap())
        assertEquals(root, result)
    }
}
