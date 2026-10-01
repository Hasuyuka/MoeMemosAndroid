package me.mudkip.moememos.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import me.mudkip.moememos.data.local.dao.MemoDao
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * DAO 的**行为**测试，跑在 Robolectric 提供的 Android 框架上（不需要设备）。
 *
 * 为什么需要它：这个项目此前只能验证到「编译通过 + Room 编译期校验查询」。
 * 但 SQL 的语义问题——大小写敏感性、LIMIT 与排序的交互、随机查询的范围——
 * 编译期一概看不出来，只有真的执行一次才知道。
 *
 * 覆盖的都是「改的时候本机无法验证」的那几处：
 *
 * - `getMemosFiltered`：第 10 轮加的查询，用 `instr` 而非 `LIKE` 是为了保住大小写敏感，
 *   这里用 `#work` / `#Work` 两条数据把这件事钉死。
 * - `getRandomMemo`：`ORDER BY RANDOM() LIMIT 1` 的范围是否与 `listMemos` 一致。
 * - `getAllMemosWithResources` / `getArchivedMemosWithResources`：第 6 轮把 N+1 换成
 *   一次 JOIN 之后，资源到底有没有正确挂上去——这是当时只能靠推理的那个结论。
 *
 * `@Config(sdk = [34])` 是必需的：本项目 compileSdk/targetSdk 都是 37，
 * 而 Robolectric 支持的 SDK 上限更低，不固定就会直接报不支持的版本。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MemoDaoTest {

    private lateinit var db: MoeMemosDatabase
    private lateinit var dao: MemoDao

    private val accountKey = "acct"
    private val otherAccount = "other"
    private val base = Instant.parse("2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, MoeMemosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun memo(
        id: String,
        content: String,
        pinned: Boolean = false,
        archived: Boolean = false,
        deleted: Boolean = false,
        daysAfterBase: Long = 0,
        account: String = accountKey,
    ) = MemoEntity(
        identifier = id,
        accountKey = account,
        content = content,
        date = base.plusSeconds(daysAfterBase * 86_400L),
        visibility = MemoVisibility.PRIVATE,
        pinned = pinned,
        archived = archived,
        isDeleted = deleted,
    )

    private fun resource(id: String, memoId: String) = ResourceEntity(
        identifier = id,
        accountKey = accountKey,
        date = base,
        filename = "$id.png",
        uri = "https://example.com/$id.png",
        mimeType = "image/png",
        memoId = memoId,
    )

    // ---------- 范围：未归档、未删除、当前账户 ----------

    @Test
    fun `筛选查询只返回未归档且未删除的当前账户备忘`() = runBlocking {
        dao.insertMemo(memo("a", "普通"))
        dao.insertMemo(memo("b", "已归档", archived = true))
        dao.insertMemo(memo("c", "已删除", deleted = true))
        dao.insertMemo(memo("d", "别的账户", account = otherAccount))

        val result = dao.getMemosFiltered(accountKey, null, false, 50)
        assertEquals(listOf("a"), result.map { it.identifier })
    }

    // ---------- 排序与截断 ----------

    @Test
    fun `置顶优先其次是时间倒序`() = runBlocking {
        dao.insertMemo(memo("old", "旧的"))
        dao.insertMemo(memo("new", "新的", daysAfterBase = 10))
        dao.insertMemo(memo("pin", "置顶的", pinned = true, daysAfterBase = 1))

        val result = dao.getMemosFiltered(accountKey, null, false, 50)
        assertEquals(listOf("pin", "new", "old"), result.map { it.identifier })
    }

    @Test
    fun `limit 在 SQL 层生效且取的是排序后的前几条`() = runBlocking {
        repeat(10) { index ->
            dao.insertMemo(memo("m$index", "内容 $index", daysAfterBase = index.toLong()))
        }
        val result = dao.getMemosFiltered(accountKey, null, false, 3)
        assertEquals(listOf("m9", "m8", "m7"), result.map { it.identifier })
    }

    @Test
    fun `pinnedOnly 为真时只返回置顶`() = runBlocking {
        dao.insertMemo(memo("a", "普通"))
        dao.insertMemo(memo("b", "置顶", pinned = true))

        assertEquals(
            listOf("b"),
            dao.getMemosFiltered(accountKey, null, true, 50).map { it.identifier },
        )
        assertEquals(
            listOf("b", "a"),
            dao.getMemosFiltered(accountKey, null, false, 50).map { it.identifier },
        )
    }

    // ---------- 标签：这里正是 instr 与 LIKE 的分水岭 ----------

    @Test
    fun `标签匹配区分大小写`() = runBlocking {
        dao.insertMemo(memo("lower", "今天 #work"))
        dao.insertMemo(memo("upper", "今天 #Work"))
        dao.insertMemo(memo("none", "今天没有标签"))

        // SQLite 的 LIKE 对 ASCII 默认不区分大小写；若这里改成 LIKE，
        // upper 会被一并选中，这条断言就会失败。这正是当初选择 instr 的原因。
        assertEquals(
            listOf("lower"),
            dao.getMemosFiltered(accountKey, "work", false, 50).map { it.identifier },
        )
        assertEquals(
            listOf("upper"),
            dao.getMemosFiltered(accountKey, "Work", false, 50).map { it.identifier },
        )
    }

    @Test
    fun `标签匹配覆盖层级标签`() = runBlocking {
        dao.insertMemo(memo("hier", "记录 #work/sub"))
        assertEquals(
            listOf("hier"),
            dao.getMemosFiltered(accountKey, "work", false, 50).map { it.identifier },
        )
    }

    @Test
    fun `已知缺陷 D-26 在 SQL 侧同样存在——修 D-26 时这里会失败并提醒改 SQL`() = runBlocking {
        dao.insertMemo(memo("over", "健身 #workout"))

        // 与 contentHasTag 的「包含」语义一致：按 work 会命中 #workout。
        // 这条断言的作用不是"这是期望行为"，而是把当前口径固定住：
        // 一旦 D-26 被修成 token 语义，Kotlin 侧那条绊线测试和这条会同时失败，
        // 明确告诉改动者「DAO 里那份 SQL 必须一起改」。
        assertEquals(
            listOf("over"),
            dao.getMemosFiltered(accountKey, "work", false, 50).map { it.identifier },
        )
    }

    @Test
    fun `tag 为 null 时不按标签过滤`() = runBlocking {
        dao.insertMemo(memo("a", "没有任何标签"))
        assertEquals(1, dao.getMemosFiltered(accountKey, null, false, 50).size)
    }

    // ---------- 随机查询 ----------

    @Test
    fun `没有可用备忘时随机查询返回 null`() = runBlocking {
        assertNull(dao.getRandomMemo(accountKey))
        dao.insertMemo(memo("archived", "已归档", archived = true))
        assertNull(dao.getRandomMemo(accountKey))
    }

    @Test
    fun `随机查询只在未归档未删除的当前账户备忘里取`() = runBlocking {
        dao.insertMemo(memo("a", "甲"))
        dao.insertMemo(memo("b", "乙"))
        dao.insertMemo(memo("c", "已删除", deleted = true))
        dao.insertMemo(memo("d", "别的账户", account = otherAccount))

        repeat(20) {
            val picked = dao.getRandomMemo(accountKey)
            assertNotNull(picked)
            assertTrue(
                "随机查询取到了不该出现的备忘: ${picked?.identifier}",
                picked?.identifier in setOf("a", "b"),
            )
        }
    }

    // ---------- JOIN（第 6 轮把 N+1 换成的写法） ----------

    @Test
    fun `JOIN 查询把资源挂到对应的备忘上`() = runBlocking {
        dao.insertMemo(memo("with", "带图"))
        dao.insertMemo(memo("without", "不带图"))
        dao.insertResource(resource("r1", "with"))
        dao.insertResource(resource("r2", "with"))

        val result = dao.getAllMemosWithResources(accountKey)

        assertEquals(listOf("with", "without"), result.map { it.memo.identifier })
        val with = result.first { it.memo.identifier == "with" }
        assertEquals(setOf("r1", "r2"), with.resources.map { it.identifier }.toSet())
        val without = result.first { it.memo.identifier == "without" }
        assertTrue(without.resources.isEmpty())
    }

    @Test
    fun `归档的 JOIN 查询返回归档备忘及其资源`() = runBlocking {
        dao.insertMemo(memo("arch", "归档的", archived = true))
        dao.insertMemo(memo("normal", "普通的"))
        dao.insertResource(resource("r1", "arch"))

        val result = dao.getArchivedMemosWithResources(accountKey)

        assertEquals(listOf("arch"), result.map { it.memo.identifier })
        assertEquals(listOf("r1"), result.first().resources.map { it.identifier })
    }
}
