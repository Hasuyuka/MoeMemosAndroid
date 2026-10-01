package me.mudkip.moememos.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.util.MemoSelectionState
import me.mudkip.moememos.viewmodel.ArchivedMemoListViewModel
import me.mudkip.moememos.viewmodel.LocalMemos

/**
 * 归档页，支持多选批量操作（REQ-604）。
 *
 * 多选的入口刻意放在**顶栏的显式按钮**上，而不是长按卡片：
 * 卡片的 `LONG` 手势已经被「进入编辑」占用（且用户可配置），若多选也依赖长按，
 * 两者就会冲突（OPEN-7）。用顶栏入口，这个冲突根本不存在——不需要替用户决定
 * 长按手势该归谁。
 *
 * 批量删除带二次确认，且确认文案里明确写了「无法撤销」：撤销能力（REQ-603）尚未实现，
 * 与其让用户以为有后悔药，不如说清楚。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMemoPage(
    drawerState: DrawerState? = null,
    viewModel: ArchivedMemoListViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val memosViewModel = LocalMemos.current
    val selection = remember { MemoSelectionState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val memos = viewModel.memos
    val selectedCount = selection.visibleCountIn(memos.map { it.identifier })

    fun runOnSelection(action: suspend (String) -> Unit) {
        // 先取快照：批量操作会逐个把条目移出列表，边遍历边读列表会漏掉后面的条目。
        val identifiers = memos.map { it.identifier }.filter { it in selection.selected }
        if (identifiers.isEmpty()) {
            return
        }
        scope.launch {
            identifiers.forEach { action(it) }
            // 恢复出来的备忘要出现在主列表上，否则用户以为恢复失败了。
            memosViewModel.refreshLocalSnapshot()
            selection.exit()
        }
    }

    Scaffold(
        topBar = {
            if (selection.isSelecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, selectedCount)) },
                    navigationIcon = {
                        IconButton(onClick = { selection.exit() }) {
                            Icon(Icons.Filled.Close, contentDescription = R.string.cancel.string)
                        }
                    },
                    actions = {
                        IconButton(onClick = { selection.selectAllOf(memos.map { it.identifier }) }) {
                            Icon(
                                Icons.Outlined.SelectAll,
                                contentDescription = R.string.select_all.string
                            )
                        }
                        IconButton(
                            enabled = selectedCount > 0,
                            onClick = { runOnSelection { viewModel.restoreMemo(it) } },
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = R.string.restore.string)
                        }
                        IconButton(
                            enabled = selectedCount > 0,
                            onClick = { showDeleteDialog = true },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = R.string.delete.string)
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(text = R.string.archived.string) },
                    navigationIcon = {
                        if (drawerState != null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                            }
                        }
                    },
                    actions = {
                        // 归档为空时不给入口，免得进去只能看到 0 项。
                        if (memos.isNotEmpty()) {
                            IconButton(onClick = { selection.start() }) {
                                Icon(
                                    Icons.Outlined.Checklist,
                                    contentDescription = R.string.select.string
                                )
                            }
                        }
                    }
                )
            }
        },

        content = { innerPadding ->
            ArchivedMemoList(
                viewModel = viewModel,
                selection = selection,
                contentPadding = innerPadding
            )
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete.string) },
            text = { Text(stringResource(R.string.delete_selected_confirm, selectedCount)) },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(R.string.cancel.string)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    runOnSelection { viewModel.deleteMemo(it) }
                }) {
                    Text(R.string.delete.string)
                }
            }
        )
    }
}
