package me.mudkip.moememos.util

/**
 * 列表筛选的匹配口径，集中在一处并配有单元测试。
 *
 * 为什么单独抽出来：这些规则此前是 `MemosList` 与小组件里各写一遍的内联 `contains` 表达式，
 * **没有任何测试**；而 REQ-202 要把它们下推到 DAO 的 SQL。若不先把口径用测试钉死，
 * 「下推」就会变成「悄悄改变筛选行为」——这类改动不会报错、不会崩，只会让搜索结果慢慢变得不对。
 *
 * 本文件**逐字保留**重构前的行为（见下），包括其中已知的缺陷。缺陷的说明见 D-26。
 */

/**
 * [content] 是否带有 [tag] 这个标签。
 *
 * [tag] 允许带前导 `#`（调用方有时拿到的是带 `#` 的形式）。
 *
 * 实现与重构前等价：`content.contains("#$tag") || content.contains("#$tag/")`。
 * 第二个条件其实被第一个完全包含（任何含 `#work/` 的字符串必然含 `#work`），
 * 保留它是为了让这次重构**可逐字核对**——重构不该顺手改语义。
 *
 * ## 已知缺陷（D-26，本文件不修）
 *
 * 这个「包含」语义比标签本身的定义**宽**：`util/Markdown.kt` 的 [extractCustomTags]
 * 用 `#([^\s#]+)` 解析标签，是 token 语义。两者不一致会产生两个方向的错误：
 *
 * 1. **前缀误匹配**：按 `work` 筛选会命中 `#workout`、`#workshop`、`#worklog`，
 *    而抽屉里那些备忘的标签并不是 `work`。
 * 2. **反过来**，因为标签以空白或 `#` 终止，`#work，然后休息`（中文里标点紧跟标签很常见）
 *    在抽屉里会被解析成一个叫 `work，然后休息` 的标签——这本身是标签解析的粗糙之处。
 *
 * 之所以不在这里单方面「修」成 token 语义：那会让第 2 种场景**变差**
 * （旧行为尚能命中 `work`，token 语义会失配）。真正正确的修法是给标签解析引入
 * 标点终止符，并让筛选与解析共用同一套定义——那会同时改动抽屉标签列表、
 * 标签页与小组件筛选，属于需要真机验证的独立改动，不该夹带在这次重构里。
 */
fun contentHasTag(content: String, tag: String): Boolean {
    val normalized = tag.removePrefix("#")
    if (normalized.isEmpty()) {
        return false
    }
    return content.contains("#$normalized") || content.contains("#$normalized/")
}

/**
 * 关键字匹配：大小写不敏感的**子串**匹配。
 *
 * 空关键字视为「不过滤」，返回 true——调用方因此不必再自己判断，
 * 也就不会出现「空关键字把列表清空」这种错误。
 */
fun contentMatchesKeyword(content: String, keyword: String): Boolean =
    keyword.isEmpty() || content.contains(keyword, ignoreCase = true)
