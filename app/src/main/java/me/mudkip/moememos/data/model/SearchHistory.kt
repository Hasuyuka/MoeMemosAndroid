package me.mudkip.moememos.data.model

/** 搜索历史最多保留的条数。 */
const val MAX_RECENT_SEARCHES = 8

/**
 * 把一次搜索记入历史。
 *
 * 全是纯函数，因此可以像其它纯逻辑一样被普通单元测试覆盖，不必依赖 Compose 或设备。
 *
 * 规则：
 * - 空白查询**原样返回**——避免在输入法上连按几次搜索键就塞进一堆空串；
 * - 大小写不敏感去重，只保留最新的一条（不让 "Memos" 与 "memos" 并存）；
 * - 最新一次置顶；
 * - 只留 [maxEntries] 条，超出丢弃最旧的。
 *
 * [existing] 可能是同一实例（没有实际变化时），调用方不必据此判断，
 * 但要知道这个返回值不保证是新对象。
 */
fun updateRecentSearches(
    existing: List<String>,
    query: String,
    maxEntries: Int = MAX_RECENT_SEARCHES,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || maxEntries <= 0) {
        return existing
    }
    val withoutDuplicate = existing.filterNot { it.equals(trimmed, ignoreCase = true) }
    return (listOf(trimmed) + withoutDuplicate).take(maxEntries)
}
