package me.mudkip.moememos.util

import me.mudkip.moememos.data.local.entity.MemoEntity
import java.text.Collator

/** 备忘列表的排序方式。 */
enum class MemoSortMode {
    /** 按最后修改时间。 */
    UPDATED,

    /** 按创建时间（此前的行为，也是默认）。 */
    CREATED,

    /** 按正文文字。 */
    TITLE,
}

/**
 * 排序方向。
 *
 * 升/降序是**独立于排序方式**的第二个维度：「按创建时间」升序是最旧的在前，
 * 「按字母」升序则是 A-Z / 拼音正序。
 */
enum class MemoSortDirection {
    ASCENDING,
    DESCENDING,
}

/**
 * 按 [mode] + [direction] 排序。
 *
 * 置顶的备忘永远排在最前——这个行为此前由 SQL 的 `ORDER BY pinned DESC` 提供。
 * 改成内存排序后必须自己保留，否则用户置顶的备忘会沉到列表中间。
 *
 * 字母排序用 [Collator] 而不是 `String.compareTo`：中文按 Unicode 码点排出来是按
 * 笔画的，几乎没人期待那个顺序。Collator 按当前语言环境处理（中文环境按拼音、
 * 英文环境按字母）。它是可注入的，测试里传固定实例就不受运行环境影响。
 *
 * 排序键相同时用另一个时间字段兜底，保证结果是**确定的**——否则同一屏数据
 * 每次刷新可能换一个顺序，用户会觉得列表在乱跳。
 */
fun sortMemos(
    memos: List<MemoEntity>,
    mode: MemoSortMode,
    direction: MemoSortDirection = MemoSortDirection.DESCENDING,
    collator: Collator = Collator.getInstance(),
): List<MemoEntity> {
    // 显式标注类型：否则 compareBy(collator) 的两个重载会推错，
    // 把 Collator 当成 Comparator<MemoEntity> 传给另一个重载。
    val byMode: Comparator<MemoEntity> = when (mode) {
        MemoSortMode.UPDATED -> compareBy<MemoEntity> { it.lastModified }
        MemoSortMode.CREATED -> compareBy<MemoEntity> { it.date }
        MemoSortMode.TITLE -> compareBy(collator) { it.content }
    }
    val directed = if (direction == MemoSortDirection.DESCENDING) byMode.reversed() else byMode
    val tieBreak = when (direction) {
        MemoSortDirection.DESCENDING -> compareByDescending<MemoEntity> { it.date }
        MemoSortDirection.ASCENDING -> compareBy<MemoEntity> { it.date }
    }
    return memos.sortedWith(
        compareByDescending<MemoEntity> { it.pinned }
            .then(directed)
            .then(tieBreak)
    )
}