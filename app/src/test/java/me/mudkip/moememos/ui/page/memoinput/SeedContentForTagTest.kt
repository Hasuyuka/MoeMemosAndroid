package me.mudkip.moememos.ui.page.memoinput

import org.junit.Assert.assertEquals
import org.junit.Test

class SeedContentForTagTest {

    @Test
    fun `填成井号标签加一个空格`() {
        // 末尾那个空格是有意的：光标正好落在标签后面，用户可以直接接着打字。
        assertEquals("#汉堡 ", seedContentForTag("汉堡"))
    }

    @Test
    fun `带井号前缀也不会变成两个井号`() {
        assertEquals("#汉堡 ", seedContentForTag("#汉堡"))
    }

    @Test
    fun `去掉多余空白`() {
        assertEquals("#work ", seedContentForTag("  work  "))
    }

    @Test
    fun `没有标签就是空白正文`() {
        // 空的标签必须退回空白，而不是生成一个孤零零的 "#"。
        assertEquals("", seedContentForTag(null))
        assertEquals("", seedContentForTag(""))
        assertEquals("", seedContentForTag("   "))
        assertEquals("", seedContentForTag("#"))
    }

    @Test
    fun `标签里的中文照原样保留`() {
        assertEquals("#读书笔记 ", seedContentForTag("读书笔记"))
    }
}