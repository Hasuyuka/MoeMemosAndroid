package me.mudkip.moememos.util

import me.mudkip.moememos.data.model.MemoVisibility
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 搜索语法（REQ-302）。
 *
 * 解析结果 [MemoQuery] 是纯数据，[matches] 是纯函数，因此整套语义可以被完整单测——
 * 这一点很重要：搜索语法最容易出的问题不是崩溃，而是「某个条件悄悄不生效」，
 * 用户只会觉得搜出来的东西莫名其妙。
 *
 * 支持的写法：
 *
 * | 写法 | 含义 |
 * |---|---|
 * | `tag:work` | 含标签 `work`（层级标签 `#work/sub` 也命中） |
 * | `is:pinned` | 仅置顶 |
 * | `is:archived` | 仅归档 |
 * | `visibility:private` \| `protected` \| `public` \| `space` | 按可见性 |
 * | `after:2026-01-01` | 创建于该日（含）之后 |
 * | `before:2026-02-01` | 创建于该日之前 |
 * | 其余文本 | 关键字（大小写不敏感的子串匹配） |
 *
 * 两条刻意的设计：
 *
 * 1. **不认识的前缀降级成普通文本**，而不是报错或变成空结果。输入 `foo:bar` 仍按关键字
 *    `foo:bar` 匹配——打错一个前缀就得到「什么都没有」是最糟的失败方式。
 * 2. **日期按 UTC 当天零点解释**。用户输入的是日期、备忘存的是 `Instant`，
 *    这里选择了可预测而非"跟随本机时区"：跟随本机时区会让同一份输入在不同设备上
 *    得到不同结果，也让测试变得依赖运行环境。
 */
data class MemoQuery(
    val keyword: String = "",
    val tag: String? = null,
    val pinnedOnly: Boolean = false,
    val archivedOnly: Boolean = false,
    val visibility: MemoVisibility? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
) {
    /** 是否含任何结构化条件；调用方据此判断"这是一次语法搜索"而不是普通文本搜索。 */
    val hasFilters: Boolean
        get() = tag != null || pinnedOnly || archivedOnly || visibility != null ||
            createdAfter != null || createdBefore != null
}

private val whitespacePattern = Regex("\\s+")

/** 解析搜索输入。空输入返回「匹配全部」。 */
fun parseMemoQuery(raw: String): MemoQuery {
    val tokens = raw.trim().split(whitespacePattern).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) {
        return MemoQuery()
    }

    var tag: String? = null
    var pinnedOnly = false
    var archivedOnly = false
    var visibility: MemoVisibility? = null
    var createdAfter: Instant? = null
    var createdBefore: Instant? = null
    val keywordParts = mutableListOf<String>()

    for (token in tokens) {
        val prefix = token.substringBefore(':', missingDelimiterValue = "")
        val value = token.substringAfter(':', missingDelimiterValue = "")
        when {
            prefix.isEmpty() -> keywordParts += token

            prefix.equals("tag", ignoreCase = true) -> {
                val normalized = value.removePrefix("#").trim()
                if (normalized.isEmpty()) keywordParts += token else tag = normalized
            }

            prefix.equals("is", ignoreCase = true) -> when (value.lowercase()) {
                "pinned" -> pinnedOnly = true
                "archived" -> archivedOnly = true
                else -> keywordParts += token
            }

            prefix.equals("visibility", ignoreCase = true) -> {
                val parsed = MemoVisibility.entries.firstOrNull { it.name.equals(value, true) }
                if (parsed == null) keywordParts += token else visibility = parsed
            }

            prefix.equals("after", ignoreCase = true) -> {
                val parsed = parseDateBoundary(value)
                if (parsed == null) keywordParts += token else createdAfter = parsed
            }

            prefix.equals("before", ignoreCase = true) -> {
                val parsed = parseDateBoundary(value)
                if (parsed == null) keywordParts += token else createdBefore = parsed
            }

            // 任何其它前缀都不认识，按普通文本处理（见类注释第 1 条）。
            else -> keywordParts += token
        }
    }

    return MemoQuery(
        keyword = keywordParts.joinToString(" "),
        tag = tag,
        pinnedOnly = pinnedOnly,
        archivedOnly = archivedOnly,
        visibility = visibility,
        createdAfter = createdAfter,
        createdBefore = createdBefore,
    )
}

/**
 * 判断一条备忘是否满足查询条件。
 *
 * 参数刻意是拆开的字段而不是 `MemoEntity`：这样这个函数不依赖任何实体/Android 类型，
 * 测试可以直接构造边界情况，不必为了造一条备忘去凑齐十几个字段。
 *
 * `after:D` 含当天，`before:D` 不含当天（即"早于那一天的零点"）——
 * 一含一不含，`after:2026-01-01 before:2026-02-01` 恰好是 1 月。
 */
fun MemoQuery.matches(
    content: String,
    pinned: Boolean,
    archived: Boolean,
    memoVisibility: MemoVisibility,
    createdDate: Instant,
): Boolean {
    if (keyword.isNotEmpty() && !contentMatchesKeyword(content, keyword)) {
        return false
    }
    tag?.let { if (!contentHasTag(content, it)) return false }
    if (pinnedOnly && !pinned) return false
    if (archivedOnly && !archived) return false
    visibility?.let { if (it != memoVisibility) return false }
    createdAfter?.let { if (createdDate.isBefore(it)) return false }
    createdBefore?.let { if (!createdDate.isBefore(it)) return false }
    return true
}

/** `YYYY-MM-DD`（按 UTC 当天零点）或完整的 ISO-8601 时间戳；都解析不出则返回 null。 */
private fun parseDateBoundary(value: String): Instant? {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) {
        return null
    }
    runCatching { LocalDate.parse(trimmed) }.getOrNull()?.let { date ->
        return date.atStartOfDay(ZoneOffset.UTC).toInstant()
    }
    return runCatching { Instant.parse(trimmed) }.getOrNull()
}
