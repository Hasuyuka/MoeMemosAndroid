package me.mudkip.moememos.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.DeleteSelectedDialog
import me.mudkip.moememos.ui.component.SelectionTopBar
import me.mudkip.moememos.ui.component.rememberRunOnSelection
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
 * 顶栏、批量执行、删除确认这几块与主列表、标签页共用 `MemoSelectionBar`，
 * 三处各抄一份必然走偏。
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
    val visibleIds = remember(memos) { memos.map { it.identifier } }
    val selectedCount = selection.visibleCountIn(visibleIds)

    val runRestore = rememberRunOnSelection(
        scope = scope,
        selection = selection,
        visibleIds = visibleIds,
        onItem = { viewModel.restoreMemo(it) },
        // 恢复出来的备忘要出现在主列表上，否则用户以为恢复失败了。
        onFinished = { memosViewModel.refreshLocalSnapshot() },
    )
    val runDelete = rememberRunOnSelection(
        scope = scope,
        selection = selection,
        visibleIds = visibleIds,
        onItem = { viewModel.deleteMemo(it) },
        onFinished = { memosViewModel.refreshLocalSnapshot() },
    )

    Scaffold(
        topBar = {
            if (selection.isSelecting) {
                SelectionTopBar(
                    selection = selection,
                    visibleIds = visibleIds,
                    selectedCount = selectedCount,
                    onDeleteRequest = { showDeleteDialog = true },
                    extraActions = {
                        IconButton(
                            enabled = selectedCount > 0,
                            onClick = { runRestore() },
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = R.string.restore.string)
                        }
                    },
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
                        // 多选入口改成「长按卡片」，顶栏不再放按钮
                        // （否则和长按抢同一个手势，用户两边都记不住）。
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
        DeleteSelectedDialog(
            count = selectedCount,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                runDelete()
            },
        )
    }
}