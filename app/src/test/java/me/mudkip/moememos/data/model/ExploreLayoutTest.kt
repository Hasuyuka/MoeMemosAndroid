package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreLayoutTest {

    @Test
    fun `默认是大卡片`() {
        // 与引入布局切换之前的行为一致：用户不动它就看不到任何变化。
        assertEquals(ExploreLayout.LARGE, Settings().exploreLayout)
    }

    @Test
    fun `点一次换下一档`() {
        assertEquals(ExploreLayout.TWO_COLUMN, ExploreLayout.LARGE.next())
        assertEquals(ExploreLayout.THREE_COLUMN, ExploreLayout.TWO_COLUMN.next())
        assertEquals(ExploreLayout.LARGE, ExploreLayout.THREE_COLUMN.next())
    }

    @Test
    fun `反复点不会卡在某一档`() {
        // 从任意档出发连点一圈，必须恰好回到起点——否则按钮会漏掉某一档。
        ExploreLayout.entries.forEach { start ->
            var current = start
            repeat(ExploreLayout.entries.size) { current = current.next() }
            assertEquals(start, current)
        }
    }

    @Test
    fun `每一档都能被点到`() {
        val reached = mutableSetOf(ExploreLayout.LARGE)
        var current = ExploreLayout.LARGE
        repeat(ExploreLayout.entries.size - 1) {
            current = current.next()
            reached += current
        }
        assertEquals(ExploreLayout.entries.size, reached.size)
        assertTrue(reached.containsAll(ExploreLayout.entries.toList()))
    }

    @Test
    fun `只有三列档不显示图片`() {
        // 这条规则原先在两个页面里各写了一遍 `layout == TWO_COLUMN`，重复的判断迟早跑偏，
        // 所以收进枚举里，并在这里钉住。
        assertTrue(ExploreLayout.LARGE.showsImages)
        assertTrue(ExploreLayout.TWO_COLUMN.showsImages)
        assertFalse(ExploreLayout.THREE_COLUMN.showsImages)
    }

    @Test
    fun `列数对得上档位`() {
        assertEquals(1, ExploreLayout.LARGE.columns)
        assertEquals(2, ExploreLayout.TWO_COLUMN.columns)
        assertEquals(3, ExploreLayout.THREE_COLUMN.columns)
    }
}
