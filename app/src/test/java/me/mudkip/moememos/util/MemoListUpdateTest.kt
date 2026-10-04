package me.mudkip.moememos.util

import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.MemoVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class MemoListUpdateTest {

    private val base = Instant.parse("2026-10-04T01:00:00Z")

    private fun memo(
        id: String,
        content: String = id,
        date: Instant = base,
    ) = MemoEntity(
        identifier = id,
        remoteId = null,
        accountKey = "u",
        content = content,
        date = date,
        visibility = MemoVisibility.PRIVATE,
        pinned = false,
        archived = false,
        needsSync = false,
        isDeleted = false,
        lastModified = base,
        lastSyncedAt = null,
    )

    /** 按方案就地改写，返回改完的列表（用于验证方案本身，不涉及 Compose）。 */
    private fun apply(current: List<MemoEntity>, latest: List<MemoEntity>): List<MemoEntity> {
        val result = current.toMutableList()
        val plan = planMemoListUpdate(current, latest)
        repeat(plan.removeFromTail) { result.removeAt(result.size - 1) }
        plan.edits.forEach { edit ->
            if (edit.index < result.size) result[edit.index] = edit.memo else result.add(edit.memo)
        }
        return result
    }

    @Test
    fun `内容完全相同就没有任何编辑`() {
        val list = listOf(memo("a"), memo("b"), memo("c"))
        val plan = planMemoListUpdate(list, list.toList())
        assertEquals(0, plan.removeFromTail)
        assertEquals(emptyList<MemoListEdit>(), plan.edits)
    }

    @Test
    fun `未变化的条目保留同一个对象引用`() {
        // 这是整个方案的意义：Compose 靠引用判断重组，保留引用就等于"这一条没动"。
        val a = memo("a")
        val b = memo("b")
        val c = memo("c")
        val latest = listOf(a, b, c, memo("d", date = base.plusSeconds(1)))

        val result = apply(listOf(a, b, c), latest)
        assertEquals(4, result.size)
        assertSame("a 应保留引用", a, result[0])
        assertSame("b 应保留引用", b, result[1])
        assertSame("c 应保留引用", c, result[2])
    }

    @Test
    fun `改内容的那一条被替换`() {
        val old = memo("a", content = "old")
        val changed = memo("a", content = "new")
        val plan = planMemoListUpdate(listOf(old), listOf(changed))
        assertEquals(1, plan.edits.size)
        assertEquals(0, plan.edits[0].index)
        assertEquals(changed, plan.edits[0].memo)
    }

    @Test
    fun `新增的条目走追加`() {
        val a = memo("a")
        val added = memo("b")
        val result = apply(listOf(a), listOf(a, added))
        assertEquals(listOf("a", "b"), result.map { it.identifier })
    }

    @Test
    fun `删除时从尾部收缩`() {
        val list = listOf(memo("a"), memo("b"), memo("c"))
        val plan = planMemoListUpdate(list, listOf(memo("a"), memo("b")))
        assertEquals(1, plan.removeFromTail)
        val result = apply(list, listOf(memo("a"), memo("b")))
        assertEquals(listOf("a", "b"), result.map { it.identifier })
    }

    @Test
    fun `中间被删掉一条也能得到正确结果`() {
        // 尾部收缩 + 后续替换的组合：先删掉尾部那条，再把中间那条换掉
        val a = memo("a")
        val b = memo("b")
        val c = memo("c")
        val cNew = memo("c", content = "c 改了")
        val result = apply(listOf(a, b, c), listOf(a, cNew))
        assertEquals(listOf("a", "c"), result.map { it.identifier })
        assertEquals("c 改了", result[1].content)
        assertSame("a 不受影响", a, result[0])
    }

    @Test
    fun `顺序变化也能得到正确结果`() {
        val a = memo("a")
        val b = memo("b")
        val c = memo("c")
        val result = apply(listOf(a, b, c), listOf(c, b, a))
        assertEquals(listOf("c", "b", "a"), result.map { it.identifier })
    }

    @Test
    fun `空列表与空到有`() {
        assertEquals(emptyList<MemoEntity>(), apply(emptyList(), emptyList()))
        val result = apply(emptyList(), listOf(memo("a"), memo("b")))
        assertEquals(listOf("a", "b"), result.map { it.identifier })
    }

    @Test
    fun `全部清空`() {
        val list = listOf(memo("a"), memo("b"))
        assertEquals(emptyList<MemoEntity>(), apply(list, emptyList()))
    }
}