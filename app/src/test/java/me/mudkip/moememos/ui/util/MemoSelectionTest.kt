package me.mudkip.moememos.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoSelectionTest {

    // ---------- 单个切换 ----------

    @Test
    fun `切换会把未选中的加入选择`() {
        assertEquals(setOf("a"), toggleSelection(emptySet(), "a"))
        assertEquals(setOf("a", "b"), toggleSelection(setOf("a"), "b"))
    }

    @Test
    fun `再次切换会把已选中的移出选择`() {
        assertEquals(emptySet<String>(), toggleSelection(setOf("a"), "a"))
        assertEquals(setOf("b"), toggleSelection(setOf("a", "b"), "a"))
    }

    @Test
    fun `重复切换两次回到原状`() {
        val once = toggleSelection(setOf("a"), "b")
        assertEquals(setOf("a"), toggleSelection(once, "b"))
    }

    // ---------- 全选 / 取消全选 ----------

    @Test
    fun `可见项都没选中时全选会选中它们`() {
        assertEquals(setOf("a", "b"), toggleSelectAllOf(emptySet(), listOf("a", "b")))
        assertEquals(setOf("a", "b", "c"), toggleSelectAllOf(setOf("a"), listOf("b", "c")))
    }

    @Test
    fun `可见项全部已选中时取消全选`() {
        assertEquals(emptySet<String>(), toggleSelectAllOf(setOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun `部分选中时全选补齐而不是清空`() {
        assertEquals(setOf("a", "b", "c"), toggleSelectAllOf(setOf("a"), listOf("a", "b", "c")))
    }

    @Test
    fun `全选不碰不可见的选中项`() {
        // 筛选之后按全选，之前勾选但当前不可见的条目必须保留——否则用户的勾选会被静默丢掉。
        val result = toggleSelectAllOf(setOf("hidden"), listOf("a", "b"))
        assertEquals(setOf("hidden", "a", "b"), result)
    }

    @Test
    fun `可见项为空时全选什么也不做`() {
        assertEquals(setOf("a"), toggleSelectAllOf(setOf("a"), emptyList()))
        assertEquals(emptySet<String>(), toggleSelectAllOf(emptySet(), emptyList()))
    }

    // ---------- 状态持有者 ----------

    @Test
    fun `进入多选时还没有任何勾选`() {
        val state = MemoSelectionState()
        assertFalse(state.isSelecting)
        state.start()
        assertTrue(state.isSelecting)
        assertEquals(0, state.selected.size)
    }

    @Test
    fun `退出会同时清空选中项`() {
        // 批量操作后必须清空，否则下次进入多选会带着上一次的选择。
        val state = MemoSelectionState()
        state.start()
        state.toggle("a")
        state.toggle("b")
        assertEquals(2, state.selected.size)

        state.exit()
        assertFalse(state.isSelecting)
        assertEquals(0, state.selected.size)
    }

    @Test
    fun `只统计仍在列表里的选中项`() {
        val state = MemoSelectionState()
        state.start()
        state.toggle("a")
        state.toggle("gone")

        assertEquals(1, state.visibleCountIn(listOf("a", "b")))
        assertEquals(0, state.visibleCountIn(listOf("b")))
    }

    @Test
    fun `全选作用于当前可见项`() {
        val state = MemoSelectionState()
        state.start()
        state.selectAllOf(listOf("a", "b"))
        assertEquals(setOf("a", "b"), state.selected)
        state.selectAllOf(listOf("a", "b"))
        assertEquals(0, state.selected.size)
    }
}
