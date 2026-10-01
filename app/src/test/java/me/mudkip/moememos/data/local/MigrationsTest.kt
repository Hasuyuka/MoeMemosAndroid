package me.mudkip.moememos.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据库版本号与迁移列表的**配套性**检查。
 *
 * 拦的是最常见也最致命的一种疏忽：**把 [MoeMemosDatabase.VERSION] 加一却忘了写迁移**。
 * 这种改动能编译、能通过所有其它测试，只在用户升级时抛
 * `IllegalStateException` 炸掉整个数据库打开流程。
 *
 * 说明本检查的边界：它只验证「配套性」，不验证迁移本身是否正确。
 * 真正的迁移正确性需要 `MigrationTestHelper`，而那需要 instrumentation 或 Robolectric
 * （本仓库两者都没有，CI 也不跑 instrumentation 测试）。这一点在文档里已记为待补项，
 * 不假装已经覆盖。
 *
 * 之所以能在普通 JVM 单元测试里跑：[MoeMemosDatabase.VERSION] 是 `const val`，
 * 编译期内联，因此不会触发加载继承自 `RoomDatabase` 的类。
 */
class MigrationsTest {

    @Test
    fun `从 v1 到当前版本的每一级都必须有迁移`() {
        val covered = Migrations.ALL.map { it.startVersion }.toSet()
        val required = (1 until MoeMemosDatabase.VERSION).toSet()
        val missing = required - covered

        assertTrue(
            "数据库版本已是 ${MoeMemosDatabase.VERSION}，但以下版本缺少迁移：$missing。" +
                "请在 Migrations.ALL 里补上，否则用户升级时会崩溃。",
            missing.isEmpty(),
        )
    }

    @Test
    fun `每条迁移都必须是逐级升级`() {
        for (migration in Migrations.ALL) {
            assertEquals(
                "迁移 ${migration.startVersion} -> ${migration.endVersion} 必须逐级（只跨一个版本），" +
                    "跨级迁移会让中间版本的用户无法升级。",
                migration.startVersion + 1,
                migration.endVersion,
            )
        }
    }

    @Test
    fun `迁移不能有重复的起始版本`() {
        val starts = Migrations.ALL.map { it.startVersion }
        assertEquals("存在重复的迁移起始版本：$starts", starts.size, starts.toSet().size)
    }
}
