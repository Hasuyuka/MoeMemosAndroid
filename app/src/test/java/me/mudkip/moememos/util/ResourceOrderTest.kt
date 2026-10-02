package me.mudkip.moememos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ResourceOrderTest {

    private val base = Instant.parse("2026-10-02T01:00:00Z")

    @Test
    fun `没有历史附件就用当前时间`() {
        assertEquals(base, nextResourceDate(null, base))
    }

    @Test
    fun `比上一张新就用当前时间`() {
        val previous = base
        val now = base.plusMillis(500)
        assertEquals(now, nextResourceDate(previous, now))
    }

    @Test
    fun `同一毫秒也不允许并列`() {
        // 这正是 bug 的来源：一次上传十几张图，循环里的时间戳全都一样，
        // 排序时分不出先后，顺序就交给数据库随意决定了。
        val first = base
        val second = nextResourceDate(first, base)
        assertTrue("必须严格变大", second.isAfter(first))
        assertEquals(base.plusMillis(1), second)
    }

    @Test
    fun `连续同刻会依次递增`() {
        var previous: Instant? = null
        val generated = (1..8).map {
            val now = base // 模拟八次上传全落在同一毫秒
            previous = nextResourceDate(previous, now)
            previous!!
        }
        assertEquals(generated.sorted(), generated)
        assertEquals(generated.distinct(), generated)
    }

    @Test
    fun `时钟回拨也能保证递增`() {
        // 系统时间被改早、或跨时区调整，都可能出现 now < previous。
        val previous = base
        val now = base.minusSeconds(60)
        val next = nextResourceDate(previous, now)
        assertTrue(next.isAfter(previous))
        assertEquals(previous.plusMillis(1), next)
    }

    // ---- 按列表顺序重新盖章 ----

    private data class Item(val id: String, val date: Instant)

    private fun restamp(items: List<Item>) = restampDatesInOrder(
        items = items,
        dateOf = { it.date },
        withDate = { item, date -> item.copy(date = date) },
    )

    @Test
    fun `重新盖章后顺序与列表一致`() {
        // 三张图时间戳完全相同（一次上传的常见情形），此时只有列表顺序能表达先后。
        val items = listOf("a", "b", "c").map { Item(it, base) }
        val ordered = restamp(items)
        assertEquals(listOf("a", "b", "c"), ordered.map { it.id })
        assertEquals(ordered.map { it.date }.sorted(), ordered.map { it.date })
        assertEquals(3, ordered.map { it.date }.distinct().size)
    }

    @Test
    fun `重新盖章会按新顺序改写日期`() {
        val items = listOf("a", "b", "c").map { Item(it, base) }
        val reordered = listOf(items[2], items[0], items[1]) // 用户把 c 调到了第一位
        val stamped = restamp(reordered)
        assertEquals(listOf("c", "a", "b"), stamped.map { it.id })
        assertTrue("c 现在应该最早", stamped.first().date < stamped[1].date)
    }

    @Test
    fun `日期本来就递增时保持原样`() {
        val items = listOf("a", "b", "c").mapIndexed { i, id -> Item(id, base.plusMillis(i.toLong())) }
        assertEquals(items.map { it.date }, restamp(items).map { it.date })
    }

    @Test
    fun `空列表与单项不会出问题`() {
        assertEquals(emptyList<Item>(), restamp(emptyList()))
        val one = listOf(Item("a", base))
        assertEquals(one.map { it.date }, restamp(one).map { it.date })
    }
}