package me.mudkip.moememos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这里同时承担两件事：
 * 1. 把重构前内联在 Composable 里的筛选口径钉成可执行规格，供 REQ-202 下推 DAO 时对照；
 * 2. **显式记录**其中已知的缺陷（D-26）——被测试固定住的错误行为至少不会继续漂移，
 *    而修它时必须让这里的用例一起改，改不动就说明改错了地方。
 */
class MemoContentMatchTest {

    // ---------- 标签：基本命中 ----------

    @Test
    fun `开头 中间 结尾的标签都能命中`() {
        assertTrue(contentHasTag("#work 买了咖啡", "work"))
        assertTrue(contentHasTag("今天 #work 很累", "work"))
        assertTrue(contentHasTag("今天很累 #work", "work"))
    }

    @Test
    fun `标签紧跟标点或括号也能命中`() {
        assertTrue(contentHasTag("(#work)", "work"))
        assertTrue(contentHasTag("做了 #work，然后休息", "work"))
        assertTrue(contentHasTag("#work\n下一行", "work"))
    }

    @Test
    fun `多个标签中命中其中之一`() {
        assertTrue(contentHasTag("#a #work #b", "work"))
        assertFalse(contentHasTag("#a #b #c", "work"))
    }

    // ---------- 标签：层级 ----------

    @Test
    fun `按父标签筛选会命中层级子标签`() {
        assertTrue(contentHasTag("#work/sub 会议记录", "work"))
    }

    @Test
    fun `子标签的完整形式也能作为查询词命中`() {
        assertTrue(contentHasTag("#work/sub", "work/sub"))
    }

    @Test
    fun `子标签不会被子串查询误命中`() {
        // "#work/sub" 里没有 "#sub"，所以按 sub 筛选不该命中
        assertFalse(contentHasTag("#work/sub", "sub"))
    }

    // ---------- 标签：已知缺陷 D-26 被显式固定 ----------

    @Test
    fun `已知缺陷_按 work 会误命中 workout 这类前缀相同的不同标签`() {
        // 这不是期望行为，而是当前行为。抽屉里的标签来自 token 语义的 extractCustomTags，
        // 并不会把这些备忘归到 work 下，所以「点标签看到的列表」与标签列表对不上。
        // 修它需要连同标签解析一起改（见 MemoContentMatch.kt 的说明与 D-26）。
        assertTrue(contentHasTag("#workout 健身", "work"))
        assertTrue(contentHasTag("#workshop 工作坊", "work"))
        assertTrue(contentHasTag("标签是 #worklog", "work"))
    }

    @Test
    fun `已知缺陷_子串查询也会误命中层级标签`() {
        // 同样来自「包含」语义："#work/sub" 含 "#wor"，于是按 wor 也能筛出来。
        assertTrue(contentHasTag("#work/sub", "wor"))
    }

    @Test
    fun `已知缺陷_代码块与链接里的标签同样会命中`() {
        // extractCustomTags 会把代码块与链接排除，因此这类标签不会出现在抽屉的标签列表里；
        // 筛选路径上不做 Markdown 解析（数千条备忘逐条解析 AST 的代价远高于收益），
        // 于是这里比抽屉「宽」。
        assertTrue(contentHasTag("```\n#work\n```", "work"))
        assertTrue(contentHasTag("https://example.com/#work", "work"))
    }

    // ---------- 标签：大小写与空值 ----------

    @Test
    fun `标签匹配区分大小写`() {
        assertFalse(contentHasTag("#Work", "work"))
        assertTrue(contentHasTag("#Work", "Work"))
    }

    @Test
    fun `空标签不命中任何内容`() {
        assertFalse(contentHasTag("#work", ""))
        assertFalse(contentHasTag("没有标签", ""))
    }

    @Test
    fun `查询词带前导井号时也能命中`() {
        assertTrue(contentHasTag("今天 #work", "#work"))
        assertTrue(contentHasTag("今天 #work/sub", "#work"))
    }

    @Test
    fun `没有标签的内容不会命中`() {
        assertFalse(contentHasTag("一条普通备忘", "work"))
        assertFalse(contentHasTag("", "work"))
    }

    // ---------- 关键字 ----------

    @Test
    fun `关键字匹配不区分大小写`() {
        assertTrue(contentMatchesKeyword("Hello World", "hello"))
        assertTrue(contentMatchesKeyword("hello world", "WORLD"))
    }

    @Test
    fun `关键字是子串匹配`() {
        assertTrue(contentMatchesKeyword("memoes 客户端", "memo"))
        assertTrue(contentMatchesKeyword("有一条备忘", "备忘"))
    }

    @Test
    fun `空关键字视为不过滤`() {
        assertTrue(contentMatchesKeyword("任意内容", ""))
        assertTrue(contentMatchesKeyword("", ""))
    }

    @Test
    fun `关键字不命中时返回 false`() {
        assertFalse(contentMatchesKeyword("Hello World", "memos"))
        assertFalse(contentMatchesKeyword("", "memos"))
    }

    // ---------- 与 DAO 下推的一致性（绊线） ----------

    @Test
    fun `DAO 下推用的 SQL 条件与 contentHasTag 等价`() {
        // MemoDao.getMemosFiltered 里的标签条件是 `instr(content, '#' || :tag) > 0`，
        // 也就是 Kotlin 的 content.contains("#$tag")——即 contentHasTag 的第一个分支
        // （第二个分支 `"#$tag/"` 被第一个完全包含，等价性见该类注释）。
        //
        // 这条用例把「两边口径一致」写成可执行断言：将来若有人改了 contentHasTag
        // （例如修 D-26 改成 token 语义），这里会失败，提醒他 DAO 里那份 SQL 必须一起改。
        // 这是唯一能在单元测试里守住的环节——SQL 本身要真机或 Room 测试才能跑。
        val cases = listOf(
            "#work 买了咖啡" to "work",
            "#workout 健身" to "work",
            "#work/sub 会议" to "work",
            "#Work" to "work",
            "今天 #work" to "work",
            "##work" to "work",
            "没有标签" to "work",
            "```\n#work\n```" to "work",
            "https://example.com/#work" to "work",
        )
        cases.forEach { (content, tag) ->
            assertEquals(
                "contentHasTag 与 DAO 的 SQL 条件在 [$content] / [$tag] 上不一致",
                content.contains("#$tag"),
                contentHasTag(content, tag),
            )
        }
    }
}
