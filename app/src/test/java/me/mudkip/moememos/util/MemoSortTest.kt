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
        assertEquals(listOf("new", "old"), sortMemos(list, MemoSortMode.CREATED, collator).map { it.identifier })
    }

    @Test
    fun `按修改时间新的在前`() {
        // 创建时间与修改时间相反，才能证明排的是修改时间而不是创建时间
        val list = listOf(
            memo("a", created = base.plusSeconds(100), modified = base),
            memo("b", created = base, modified = base.plusSeconds(100)),
        )
        assertEquals(listOf("b", "a"), sortMemos(list, MemoSortMode.UPDATED, collator).map { it.identifier })
    }

    @Test
    fun `按字母排序`() {
        val list = listOf(memo("c", content = "cherry"), memo("a", content = "apple"), memo("b", content = "banana"))
        assertEquals(
            listOf("a", "b", "c"),
            sortMemos(list, MemoSortMode.TITLE, collator).map { it.identifier }
        )
    }

    @Test
    fun `置顶的永远在最前`() {
        // 三种排序方式都必须保留置顶优先，否则置顶的备忘会沉底
        val list = listOf(
            memo("normal", created = base.plusSeconds(100)),
            memo("pinned", content = "aaa", created = base, pinned = true),
        )
        MemoSortMode.entries.forEach { mode ->
            assertEquals(
                "模式 $mode 下置顶项应在最前",
                listOf("pinned", "normal"),
                sortMemos(list, mode, collator).map { it.identifier },
            )
        }
    }

    @Test
    fun `排序键相同时用创建时间兜底顺序`() {
        // 两张时间戳完全一样（同一毫秒保存的），顺序也必须确定，不能每次刷新都换
        val list = listOf(memo("b"), memo("a"))
        MemoSortMode.entries.forEach { mode ->
            val once = sortMemos(list, mode, collator).map { it.identifier }
            val twice = sortMemos(list, mode, collator).map { it.identifier }
            assertEquals("模式 $mode 的结果应可复现", once, twice)
        }
    }

    @Test
    fun `空列表与单项`() {
        assertEquals(emptyList<MemoEntity>(), sortMemos(emptyList(), MemoSortMode.CREATED, collator))
        val one = listOf(memo("only"))
        assertEquals(one.map { it.identifier }, sortMemos(one, MemoSortMode.TITLE, collator).map { it.identifier })
    }
}