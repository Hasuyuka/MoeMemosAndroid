package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsAccessTest {

    private val remoteKey = "memos:https://a.example.com:users/1"

    private fun sampleSettings() = Settings(
        usersList = listOf(
            UserData(
                accountKey = remoteKey,
                memosV1 = MemosAccount(host = "https://a.example.com", remoteIdentifier = "users/1"),
                settings = UserSettings(
                    draft = "A 的草稿",
                    editGesture = MemoEditGesture.DOUBLE,
                    autosave = true,
                ),
            ),
            UserData(
                accountKey = "local",
                local = LocalAccount(),
                settings = UserSettings(draft = "本地草稿"),
            ),
        ),
        currentUser = "local",
    )

    @Test
    fun `按 accountKey 查账户`() {
        val settings = sampleSettings()
        assertEquals("A 的草稿", settings.userData(remoteKey)?.settings?.draft)
        assertEquals("本地草稿", settings.userData("local")?.settings?.draft)
    }

    @Test
    fun `账户不存在时返回 null 而不是抛异常`() {
        assertNull(sampleSettings().userData("并不存在的账户"))
    }

    @Test
    fun `解析当前账户`() {
        assertEquals("local", sampleSettings().currentUserData()?.accountKey)
    }

    @Test
    fun `当前账户为空时返回默认偏好而不是崩溃`() {
        val settings = sampleSettings().copy(currentUser = "")
        assertNull(settings.currentUserData())
        assertEquals(UserSettings(), settings.currentUserSettings())
    }

    @Test
    fun `更新当前账户偏好只影响当前账户`() {
        val before = sampleSettings()
        val after = before.updateCurrentUserSettings {
            it.copy(draft = "改过的草稿", autosave = true)
        }

        assertEquals("改过的草稿", after.userData("local")?.settings?.draft)
        assertEquals(true, after.userData("local")?.settings?.autosave)

        // 另一个账户必须原封不动
        assertEquals("A 的草稿", after.userData(remoteKey)?.settings?.draft)
        assertEquals(true, after.userData(remoteKey)?.settings?.autosave)
        assertEquals(MemoEditGesture.DOUBLE, after.userData(remoteKey)?.settings?.editGesture)

        // 不可变：原对象不受影响
        assertEquals("本地草稿", before.userData("local")?.settings?.draft)
    }

    @Test
    fun `更新指定账户不影响其它账户与列表顺序`() {
        val before = sampleSettings()
        val after = before.updateUserData(remoteKey) {
            it.copy(settings = it.settings.copy(autosave = false))
        }

        assertEquals(listOf(remoteKey, "local"), after.usersList.map { it.accountKey })
        assertEquals(false, after.userData(remoteKey)?.settings?.autosave)
        assertEquals(false, after.userData("local")?.settings?.autosave)
        // 其它字段保持
        assertEquals("A 的草稿", after.userData(remoteKey)?.settings?.draft)
    }

    @Test
    fun `更新不存在的账户时原样返回同一个实例`() {
        val settings = sampleSettings()
        val after = settings.updateUserData("拼错的账户 key") { it.copy(accountKey = "被改坏") }

        // 关键：不能因为找不到就新建一条，也不能碰其它账户
        assertSame(settings, after)
        assertEquals(2, after.usersList.size)
        assertEquals(listOf(remoteKey, "local"), after.usersList.map { it.accountKey })
    }

    @Test
    fun `没有当前账户时更新偏好是空操作`() {
        val settings = sampleSettings().copy(currentUser = "")
        assertSame(settings, settings.updateCurrentUserSettings { it.copy(draft = "不该出现") })
    }
}
