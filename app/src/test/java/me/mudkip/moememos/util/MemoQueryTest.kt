package me.mudkip.moememos.util

import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoQueryTest {

    private fun Instant?.isSameAs(other: String) = this == Instant.parse(other)

    // ---------- 解析 ----------

    @Test
    fun `空输入不产生任何条件`() {
        val query = parseMemoQuery("")
        assertEquals(MemoQuery(), query)
        assertFalse(query.hasFilters)
        assertEquals("", query.keyword)
    }

    @Test
    fun `纯文本成为关键字`() {
        val query = parseMemoQuery("咖啡")
        assertEquals("咖啡", query.keyword)
        assertFalse(query.hasFilters)
    }

    @Test
    fun `多个词拼成同一个关键字`() {
        assertEquals("买了 咖啡", parseMemoQuery("  买了   咖啡  ").keyword)
    }

    @Test
    fun `tag 前缀`() {
        val query = parseMemoQuery("tag:work")
        assertEquals("work", query.tag)
        assertTrue(query.hasFilters)
        assertEquals("", query.keyword)
    }

    @Test
    fun `tag 值允许带井号`() {
        assertEquals("work", parseMemoQuery("tag:#work").tag)
    }

    @Test
    fun `tag 值为空时降级为普通文本`() {
        val query = parseMemoQuery("tag:")
        assertNull(query.tag)
        assertEquals("tag:", query.keyword)
    }

    @Test
    fun `is 前缀支持 pinned 与 archived 且不区分大小写`() {
        assertTrue(parseMemoQuery("is:pinned").pinnedOnly)
        assertTrue(parseMemoQuery("is:archived").archivedOnly)
        assertTrue(parseMemoQuery("IS:PINNED").pinnedOnly)
    }

    @Test
    fun `is 的不认识取值降级为普通文本`() {
        val query = parseMemoQuery("is:whatever")
        assertFalse(query.pinnedOnly)
        assertFalse(query.archivedOnly)
        assertEquals("is:whatever", query.keyword)
    }

    @Test
    fun `visibility 前缀`() {
        assertEquals(MemoVisibility.PUBLIC, parseMemoQuery("visibility:public").visibility)
        assertEquals(MemoVisibility.PRIVATE, parseMemoQuery("visibility:PRIVATE").visibility)
        assertEquals(MemoVisibility.SPACE, parseMemoQuery("visibility:space").visibility)
    }

    @Test
    fun `visibility 的不认识取值降级为普通文本`() {
        val query = parseMemoQuery("visibility:secret")
        assertNull(query.visibility)
        assertEquals("visibility:secret", query.keyword)
    }

    @Test
    fun `日期边界按 UTC 当天零点解释`() {
        assertTrue(parseMemoQuery("after:2026-01-01").createdAfter.isSameAs("2026-01-01T00:00:00Z"))
        assertTrue(parseMemoQuery("before:2026-02-01").createdBefore.isSameAs("2026-02-01T00:00:00Z"))
    }

    @Test
    fun `日期也接受完整时间戳`() {
        assertTrue(
            parseMemoQuery("after:2026-01-01T08:30:00Z").createdAfter
                .isSameAs("2026-01-01T08:30:00Z")
        )
    }

    @Test
    fun `无法解析的日期降级为普通文本`() {
        val query = parseMemoQuery("after:昨天")
        assertNull(query.createdAfter)
        assertEquals("after:昨天", query.keyword)
    }

    @Test
    fun `不认识的前缀降级为普通文本而不是空结果`() {
        // 这是刻意的设计：打错前缀只应让这个 token 变成普通关键字，
        // 而不是把整次搜索变成「什么都没有」。
        val query = parseMemoQuery("foo:bar")
        assertFalse(query.hasFilters)
        assertEquals("foo:bar", query.keyword)
    }

    @Test
    fun `带协议的关键字不会被当成前缀`() {
        val query = parseMemoQuery("https://example.com")
        assertFalse(query.hasFilters)
        assertEquals("https://example.com", query.keyword)
    }

    @Test
    fun `条件与关键字可以混用`() {
        val query = parseMemoQuery("tag:work is:pinned 咖啡")
        assertEquals("work", query.tag)
        assertTrue(query.pinnedOnly)
        assertEquals("咖啡", query.keyword)
    }

    // ---------- 匹配 ----------

    private val anyVisibility = MemoVisibility.PUBLIC
    private val someDate = Instant.parse("2026-01-15T12:00:00Z")

    private fun matches(
        raw: String,
        content: String = "今天 #work 喝了咖啡",
        pinned: Boolean = false,
        archived: Boolean = false,
        visibility: MemoVisibility = anyVisibility,
        date: Instant = someDate,
    ): Boolean = parseMemoQuery(raw).matches(content, pinned, archived, visibility, date)

    @Test
    fun `空查询匹配一切`() {
        assertTrue(matches(""))
    }

    @Test
    fun `关键字匹配不区分大小写`() {
        assertTrue(matches("COFFEE", content = "I like coffee"))
        assertFalse(matches("tea", content = "I like coffee"))
    }

    @Test
    fun `tag 条件按标签匹配`() {
        assertTrue(matches("tag:work"))
        assertFalse(matches("tag:rest"))
    }

    @Test
    fun `is_pinned 只留下置顶`() {
        assertTrue(matches("is:pinned", pinned = true))
        assertFalse(matches("is:pinned", pinned = false))
    }

    @Test
    fun `is_archived 只留下归档`() {
        assertTrue(matches("is:archived", archived = true))
        assertFalse(matches("is:archived", archived = false))
    }

    @Test
    fun `visibility 条件`() {
        assertTrue(matches("visibility:private", visibility = MemoVisibility.PRIVATE))
        assertFalse(matches("visibility:private", visibility = MemoVisibility.PUBLIC))
    }

    @Test
    fun `after 含当天`() {
        assertTrue(matches("after:2026-01-15", date = Instant.parse("2026-01-15T00:00:00Z")))
        assertFalse(matches("after:2026-01-15", date = Instant.parse("2026-01-14T23:59:59Z")))
    }

    @Test
    fun `before 不含当天`() {
        assertTrue(matches("before:2026-01-15", date = Instant.parse("2026-01-14T23:59:59Z")))
        assertFalse(matches("before:2026-01-15", date = Instant.parse("2026-01-15T00:00:00Z")))
    }

    @Test
    fun `after 与 before 组合恰好是一个自然月`() {
        val jan = Instant.parse("2026-01-20T00:00:00Z")
        val feb = Instant.parse("2026-02-01T00:00:00Z")
        assertTrue(matches("after:2026-01-01 before:2026-02-01", date = jan))
        assertFalse(matches("after:2026-01-01 before:2026-02-01", date = feb))
    }

    @Test
    fun `边界颠倒时不匹配任何时间`() {
        assertFalse(matches("after:2026-02-01 before:2026-01-01", date = someDate))
    }

    @Test
    fun `多个条件是并且关系`() {
        // tag 命中但 pinned 不命中 -> 整条不命中
        assertFalse(matches("tag:work is:pinned", pinned = false))
        assertTrue(matches("tag:work is:pinned", pinned = true))
    }

    @Test
    fun `只有条件没有关键字时按条件过滤`() {
        assertEquals("", parseMemoQuery("tag:work").keyword)
        assertTrue(matches("tag:work"))
    }
}
