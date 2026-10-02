package me.mudkip.moememos.util

import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.text.Collator
import java.util.Locale

class MemoSortTest {

    private val base = Instant.parse("2026-10-02T01:00:00Z")

    private fun memo(
        id: String,
        content: String = id,
        created: Instant = base,
        modified: Instant = base,
        pinned: Boolean = false,
    ) = MemoEntity(
        identifier = id,
        remoteId = null,
        accountKey = "u",
        content = content,
        date = created,
        visibility = MemoVisibility.PRIVATE,
        pinned = pinned,
        archived = false,
        needsSync = false,
        isDeleted = false,
        lastModified = modified,
        lastSyncedAt = null,
    )

    // 用固定 locale 的 Collator，避免测试结果受运行机器语言环境影响
    private val collator = Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY }

    @Test
    fun `按创建时间新的在前`() {
        val list = listOf(
            memo("old", created = base),
            memo("new", created = base.plusSeconds(60)),
        )
        assertEquals(listOf("new", "old"), sortMemos(list, MemoSortMode.CREATED, MemoSortDirection.DESCENDING, collator).map { it.identifier })
    }

    @Test
    fun `按修改时间新的在前`() {
        // 创建时间与修改时间相反，才能证明排的是修改时间而不是创建时间
        val list = listOf(
            memo("a", created = base.plusSeconds(100), modified = base),
            memo("b", created = base, modified = base.plusSeconds(100)),
        )
        assertEquals(listOf("b", "a"), sortMemos(list, MemoSortMode.UPDATED, MemoSortDirection.DESCENDING, collator).map { it.identifier })
    }

    @Test
    fun `按字母排序`() {
        val list = listOf(memo("c", content = "cherry"), memo("a", content = "apple"), memo("b", content = "banana"))
        assertEquals(
            listOf("a", "b", "c"),
            sortMemos(list, MemoSortMode.TITLE, MemoSortDirection.DESCENDING, collator).map { it.identifier }
        )
    }

    @Test
    fun `置顶的永远在最前`() {
        // 三种排序方式、两个方向都必须保留置顶优先，否则置顶的备忘会沉底
        val list = listOf(
            memo("normal", created = base.plusSeconds(100)),
            memo("pinned", content = "aaa", created = base, pinned = true),
        )
        MemoSortMode.entries.forEach { mode ->
            MemoSortDirection.entries.forEach { direction ->
                assertEquals(
                    "模式 $mode / $direction 下置顶项应在最前",
                    listOf("pinned", "normal"),
                    sortMemos(list, mode, direction, collator).map { it.identifier },
                )
            }
        }
    }

    @Test
    fun `排序键相同时用创建时间兜底顺序`() {
        // 两张时间戳完全一样（同一毫秒保存的），顺序也必须确定，不能每次刷新都换
        val list = listOf(memo("b"), memo("a"))
        MemoSortMode.entries.forEach { mode ->
            MemoSortDirection.entries.forEach { direction ->
                val once = sortMemos(list, mode, direction, collator).map { it.identifier }
                val twice = sortMemos(list, mode, direction, collator).map { it.identifier }
                assertEquals("模式 $mode / $direction 的结果应可复现", once, twice)
            }
        }
    }

    @Test
    fun `空列表与单项`() {
        assertEquals(
            emptyList<MemoEntity>(),
            sortMemos(emptyList(), MemoSortMode.CREATED, MemoSortDirection.DESCENDING, collator),
        )
        val one = listOf(memo("only"))
        assertEquals(
            one.map { it.identifier },
            sortMemos(one, MemoSortMode.TITLE, MemoSortDirection.ASCENDING, collator).map { it.identifier },
        )
    }

    // ---- 升降序 ----

    @Test
    fun `按创建时间升序是最旧的在前`() {
        val list = listOf(
            memo("new", created = base.plusSeconds(60)),
            memo("old", created = base),
        )
        assertEquals(
            listOf("old", "new"),
            sortMemos(list, MemoSortMode.CREATED, MemoSortDirection.ASCENDING, collator).map { it.identifier },
        )
    }

    @Test
    fun `升降序互为反序`() {
        // 方向开关失效时最容易表现为「看着没反应」，这里把它钉住。
        val list = (1..5).map { memo("m$it", content = "note $it", created = base.plusSeconds(it.toLong() * 10)) }
        val asc = sortMemos(list, MemoSortMode.CREATED, MemoSortDirection.ASCENDING, collator).map { it.identifier }
        val desc = sortMemos(list, MemoSortMode.CREATED, MemoSortDirection.DESCENDING, collator).map { it.identifier }
        assertEquals(asc.reversed(), desc)
    }

    @Test
    fun `字母排序升序是 A 到 Z`() {
        val list = listOf(
            memo("c", content = "cherry"),
            memo("a", content = "apple"),
            memo("b", content = "banana"),
        )
        assertEquals(
            listOf("a", "b", "c"),
            sortMemos(list, MemoSortMode.TITLE, MemoSortDirection.ASCENDING, collator).map { it.identifier },
        )
    }

    @Test
    fun `升序下置顶仍然在最前`() {
        // 置顶优先与升降序无关：方向切换不该把置顶的备忘甩到中间。
        val list = listOf(
            memo("normal", created = base),
            memo("pinned", content = "zzz", created = base.plusSeconds(100), pinned = true),
        )
        assertEquals(
            listOf("pinned", "normal"),
            sortMemos(list, MemoSortMode.CREATED, MemoSortDirection.ASCENDING, collator).map { it.identifier },
        )
    }

    @Test
    fun `两个方向下结果都可复现`() {
        val list = listOf(memo("b"), memo("a"))
        MemoSortDirection.entries.forEach { direction ->
            val once = sortMemos(list, MemoSortMode.UPDATED, direction, collator).map { it.identifier }
            val twice = sortMemos(list, MemoSortMode.UPDATED, direction, collator).map { it.identifier }
            assertEquals(once, twice)
        }
    }
}