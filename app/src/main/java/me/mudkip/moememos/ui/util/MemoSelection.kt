package me.mudkip.moememos.ui.util

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 列表多选（REQ-601 / REQ-604）。
 *
 * 集合运算写成纯函数、放在这里，是因为「多选」的错法都很隐蔽：筛选之后按全选、
 * 批量操作后没清空、选中项已经不在列表里了还在计数——这些都不会崩，只会让用户
 * 删错东西。纯函数能被测试覆盖，UI 里剩下的只是把它们接起来。
 */

/** 切换单个条目的选中状态。 */
fun toggleSelection(current: Set<String>, identifier: String): Set<String> =
    if (identifier in current) current - identifier else current + identifier

/**
 * 「全选 / 取消全选」，以**当前可见**的条目为准。
 *
 * 两个刻意的语义：
 * - 只动 [visible]，不碰不可见的选中项。否则在筛选状态下按全选会静默丢掉之前的勾选。
 * - [visible] 为空时原样返回。空列表上的「全选」应当什么也不做，而不是清空已有选择。
 */
fun toggleSelectAllOf(current: Set<String>, visible: Collection<String>): Set<String> {
    if (visible.isEmpty()) {
        return current
    }
    return if (visible.all { it in current }) {
        current - visible.toSet()
    } else {
        current + visible
    }
}

/**
 * 多选状态。
 *
 * [isSelecting] 与 [selected] 是**两个独立的维度**：进入多选模式时还没有勾选任何东西，
 * 但顶栏必须立刻切换成多选样式。用「selected 非空」当作进入条件会让用户看不到入口反馈。
 */
@Stable
class MemoSelectionState {
    var isSelecting: Boolean by mutableStateOf(false)
        private set

    var selected: Set<String> by mutableStateOf(emptySet())
        private set

    /** 当前选中项中**仍在列表里**的数量（见 [visibleCountOf] 的说明）。 */
    fun visibleCountIn(available: Collection<String>): Int = available.count { it in selected }

    fun start() {
        isSelecting = true
    }

    /** 退出多选并清空。批量操作完成后必须调用——否则下次进入会带着上次的选择。 */
    fun exit() {
        isSelecting = false
        selected = emptySet()
    }

    fun toggle(identifier: String) {
        selected = toggleSelection(selected, identifier)
    }

    fun selectAllOf(visible: Collection<String>) {
        selected = toggleSelectAllOf(selected, visible)
    }
}
