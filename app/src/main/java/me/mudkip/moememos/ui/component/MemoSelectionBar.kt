package me.mudkip.moememos.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.util.MemoSelectionState

/**
 * 列表多选（批量操作）的共用部件。
 *
 * 主列表、标签页、归档页都要「进入多选 / 全选 / 逐条执行 / 删除确认」这一套，
 * 三处各抄一遍必然会走偏（归档页原本就有一套，行为已经是对的），
 * 所以收在这里，让三个页面共用同一份行为与文案。
 */

/**
 * 返回一个「对当前选中的条目逐条执行 [onItem]」的函数。
 *
 * 执行前先取快照：批量操作会把条目逐个移出列表，边遍历边读列表会漏掉后面的条目。
 */
@Composable
fun rememberRunOnSelection(
    scope: CoroutineScope,
    selection: MemoSelectionState,
    visibleIds: List<String>,
    onItem: suspend (String) -> Unit,
    onFinished: suspend () -> Unit = {},
): () -> Unit {
    val selected = selection.selected
    val ids = visibleIds
    val runner = scope
    return {
        // 先取快照：批量操作会逐个把条目移出列表，边遍历边读列表会漏掉后面的条目。
        val identifiers = ids.filter { it in selected }
        if (identifiers.isNotEmpty()) {
            runner.launch {
                identifiers.forEach { onItem(it) }
                onFinished()
                selection.exit()
            }
        }
    }
}

/**
 * 多选模式下的顶栏：标题显示已选数量，左上角退出，右侧是全选、页面自己的操作、删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selection: MemoSelectionState,
    visibleIds: List<String>,
    selectedCount: Int,
    onDeleteRequest: () -> Unit,
    extraActions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = { selection.exit() }) {
                Icon(Icons.Filled.Close, contentDescription = R.string.cancel.string)
            }
        },
        actions = {
            IconButton(onClick = { selection.selectAllOf(visibleIds) }) {
                Icon(Icons.Outlined.SelectAll, contentDescription = R.string.select_all.string)
            }
            extraActions()
            IconButton(
                enabled = selectedCount > 0,
                onClick = onDeleteRequest,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = R.string.delete.string)
            }
        }
    )
}

/**
 * 批量删除的二次确认。
 *
 * 文案里写明「此操作无法撤销」：撤销能力尚未实现，与其让用户以为有后悔药，不如说清楚。
 */
@Composable
fun DeleteSelectedDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.delete.string) },
        text = { Text(stringResource(R.string.delete_selected_confirm, count)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(R.string.cancel.string)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(R.string.delete.string)
            }
        }
    )
}