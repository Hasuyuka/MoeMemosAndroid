package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SearchHistoryTest {

    @Test
    fun `空历史时记入一条`() {
        assertEquals(listOf("memos"), updateRecentSearches(emptyList(), "memos"))
    }

    @Test
    fun `最新一次置顶`() {
        val result = updateRecentSearches(listOf("a", "b"), "c")
        assertEquals(listOf("c", "a", "b"), result)
    }

    @Test
    fun `重复查询会被提到最前而不是新增一条`() {
        val result = updateRecentSearches(listOf("a", "b", "c"), "b")
        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun `去重不区分大小写`() {
        val result = updateRecentSearches(listOf("Memos", "b"), "memos")
        assertEquals(listOf("memos", "b"), result)
    }

    @Test
    fun `查询会被去掉首尾空白`() {
        assertEquals(listOf("memos"), updateRecentSearches(emptyList(), "  memos  "))
    }

    @Test
    fun `空白查询原样返回且不产生新条目`() {
        val existing = listOf("a")
        assertSame(existing, updateRecentSearches(existing, ""))
        assertSame(existing, updateRecentSearches(existing, "   "))
        assertSame(existing, updateRecentSearches(existing, "\n\t"))
    }

    @Test
    fun `超出上限时丢弃最旧的`() {
        val result = updateRecentSearches((1..MAX_RECENT_SEARCHES).map { "q$it" }, "new")
        assertEquals(MAX_RECENT_SEARCHES, result.size)
        assertEquals("new", result.first())
        // 最旧的一条（q1）被挤掉，q2 成为最后一条
        assertEquals("q2", result.last())
    }

    @Test
    fun `上限为 0 时不记录任何内容`() {
        val existing = listOf("a")
        assertSame(existing, updateRecentSearches(existing, "b", maxEntries = 0))
    }
}
