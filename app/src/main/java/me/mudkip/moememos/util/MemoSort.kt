package me.mudkip.moememos.util

import me.mudkip.moememos.data.local.entity.MemoEntity
import java.text.Collator

/** 备忘列表的排序方式。 */
enum class MemoSortMode {
    /** 按最后修改时间，最近改过的在前。 */
    UPDATED,

    /** 按创建时间，最新建的在前（此前的行为，也是默认）。 */
    CREATED,

    /** 按正文文字排序。 */
    TITLE,
}

/**
 * 按 [mode] 排序。
 *
 * 置顶的备忘永远排在最前——这个行为此前由 SQL 的 `ORDER BY pinned DESC` 提供。
 * 改成内存排序后必须自己保留，否则用户置顶的备忘会沉到列表中间。
 *
 * 字母排序用 [Collator] 而不是 `String.compareTo`：中文按 Unicode 码点排出来是按
 * 笔画的，几乎没人期待那个顺序。Collator 按当前语言环境处理（中文环境按拼音、
 * 英文环境按字母）。它是可注入的，测试里传固定实例就不受环境影响。
 *
 * 三种方式都以创建时间作为兜底键：两个备忘的排序键相同时，顺序仍然是确定的
 * （否则每次刷新可能换一个顺序）。
 */
fun sortMemos(
    memos: List<MemoEntity>,
    mode: MemoSortMode,
    collator: Collator = Collator.getInstance(),
): List<MemoEntity> {
    val comparator = when (mode) {
        MemoSortMode.UPDATED -> compareByDescending<MemoEntity> { it.lastModified }
        MemoSortMode.CREATED -> compareByDescending<MemoEntity> { it.date }
        MemoSortMode.TITLE -> compareBy(collator) { it.content }
    }
    return memos.sortedWith(
        compareByDescending<MemoEntity> { it.pinned }
            .then(comparator)
            .thenByDescending { it.date }
    )
}