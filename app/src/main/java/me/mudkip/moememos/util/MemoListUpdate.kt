package me.mudkip.moememos.util

import me.mudkip.moememos.data.local.entity.MemoEntity

/**
 * 列表的原地更新方案。
 *
 * @param removeFromTail 先从尾部删掉几条（列表缩短时）
 * @param edits 之后再逐条进行的编辑，`index` 落在现有长度内是替换、超出是追加
 */
data class MemoListUpdate(
    val removeFromTail: Int,
    val edits: List<MemoListEdit>,
)

/** 单条编辑：把 [index] 位置改成 [memo]。 */
data class MemoListEdit(val index: Int, val memo: MemoEntity)

/**
 * 算出「怎么把 [current] 原地改成 [latest]」。
 *
 * 之所以要专门算一遍：直接 `clear()` 再 `addAll()` 会让列表先清空、再填满，
 * Compose 只能把所有卡片全部销毁重建——同步一次就是一次全量重绘，图片请求也全部重来。
 * 备忘和图片多起来之后，光这一下就足以让界面卡住。
 *
 * 关键在于**内容没变的行保留原来的对象引用**：Compose 判断重组看的是引用而不是相等，
 * 保留引用就等于告诉它"这一条没动"。
 *
 * 这里刻意不做更聪明的 diff（比如保留移动行的引用）——列表顺序本来就是按时间排的，
 * 一条备忘被改后通常只是移到前面，不值得为它引入一套 LCS。
 */
fun planMemoListUpdate(current: List<MemoEntity>, latest: List<MemoEntity>): MemoListUpdate {
    val byIdentifier = HashMap<String, MemoEntity>(current.size.coerceAtLeast(1))
    current.forEach { byIdentifier[it.identifier] = it }

    val merged = latest.map { incoming ->
        val existing = byIdentifier[incoming.identifier]
        if (existing != null && existing == incoming) existing else incoming
    }

    // 公共前缀里引用相同的那一段完全不用动
    var common = 0
    while (common < current.size && common < merged.size && current[common] === merged[common]) {
        common++
    }

    val removeFromTail = (current.size - merged.size).coerceAtLeast(0)
    val edits = (common until merged.size).map { index ->
        MemoListEdit(index, merged[index])
    }
    return MemoListUpdate(removeFromTail, edits)
}