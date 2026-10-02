package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import me.mudkip.moememos.ui.component.ActionIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.DeleteSelectedDialog
import me.mudkip.moememos.ui.component.SelectionStartButton
import me.mudkip.moememos.ui.component.SelectionTopBar
import me.mudkip.moememos.ui.component.SyncStatusBadge
import me.mudkip.moememos.ui.component.rememberRunOnSelection
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.util.MemoSelectionState
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.ManualSyncResult
import java.net.URLEncoder

@Composable
fun MemosHomePage(
    drawerState: DrawerState? = null,
    navController: NavHostController
) {
    MemoBrowser { onMemoClick ->
        MemosHomePageContent(drawerState, navController, onMemoClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemosHomePageContent(
    drawerState: DrawerState? = null,
    navController: NavHostController,
    onMemoClick: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rootNavController = LocalRootNavController.current
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 顶栏那个布局按钮切换的就是它；「发现」页读的是同一份设置，两个页面保持一致。
    val settings by context.settingsDataStore.data.collectAsStateWithLifecycle(initialValue = Settings())
    val syncStatus by memosViewModel.syncStatus.collectAsStateWithLifecycle()

    val expandedFab by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }
    var syncAlert by remember { mutableStateOf<HomeSyncAlert?>(null) }

    // 批量操作（多选）：入口放在顶栏的显式按钮上，不用长按——卡片的 LONG 手势
    // 已经被「进入编辑」占用（且用户可配置），两者会冲突。
    val selection = remember { MemoSelectionState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val visibleIds = remember(memosViewModel.memos) { memosViewModel.memos.map { it.identifier } }
    val selectedCount = selection.visibleCountIn(visibleIds)
    val runOnSelection = rememberRunOnSelection(
        scope = scope,
        selection = selection,
        visibleIds = visibleIds,
        onItem = { memosViewModel.deleteMemo(it) },
        onFinished = { memosViewModel.refreshLocalSnapshot() },
    )

    suspend fun requestManualSync(allowHigherV1Version: String? = null) {
        when (val result = memosViewModel.refreshMemos(allowHigherV1Version)) {
            ManualSyncResult.Completed -> Unit
            is ManualSyncResult.Blocked -> {
                syncAlert = HomeSyncAlert.Blocked(result.message)
            }
            is ManualSyncResult.RequiresConfirmation -> {
                syncAlert = HomeSyncAlert.RequiresConfirmation(result.version, result.message)
            }
            is ManualSyncResult.Failed -> {
                syncAlert = HomeSyncAlert.Failed(result.message)
            }
        }
    }


    Scaffold(
        topBar = {
            if (selection.isSelecting) {
                SelectionTopBar(
                    selection = selection,
                    visibleIds = visibleIds,
                    selectedCount = selectedCount,
                    onDeleteRequest = { showDeleteDialog = true },
                )
            } else {
                TopAppBar(
                    title = { Text(text = R.string.memos.string) },
                    navigationIcon = {
                        if (drawerState != null) {
                            ActionIconButton(label = R.string.menu.string, onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                            }
                        }
                    },
                    actions = {
                        if (currentAccount !is Account.Local) {
                            SyncStatusBadge(
                                syncing = syncStatus.syncing,
                                unsyncedCount = syncStatus.unsyncedCount,
                                onSync = {
                                    scope.launch {
                                        requestManualSync()
                                    }
                                }
                            )
                        }
                        ActionIconButton(label = R.string.search.string, onClick = {
                            navController.navigate(RouteName.SEARCH)
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = R.string.search.string)
                        }
                    // 布局切换：点一次换下一档（大卡片 → 两列 → 三列 → 大卡片），选择会记住。
                    ActionIconButton(label = R.string.change_layout.string, onClick = {
                        scope.launch(Dispatchers.IO) {
                            context.settingsDataStore.updateData { existing ->
                                existing.copy(exploreLayout = existing.exploreLayout.next())
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.GridView, contentDescription = R.string.change_layout.string)
                    }
                    SelectionStartButton(selection = selection, visibleCount = visibleIds.size)
                    }
                )
            }
        },

        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    rootNavController.navigate(RouteName.INPUT)
                },
                expanded = expandedFab,
                text = { Text(R.string.new_memo.string) },
                icon = { Icon(Icons.Filled.Add, contentDescription = R.string.compose.string) }
            )
        },

        content = { innerPadding ->
            MemosList(
                onMemoClick = onMemoClick,
                lazyListState = listState,
                contentPadding = innerPadding,
                additionalBottomPadding = MemoListFabAvoidancePadding,
                layout = settings.exploreLayout,
                selection = selection,
                onRefresh = { requestManualSync() },
                onTagClick = { tag ->
                    navController.navigate("${RouteName.TAG}/${URLEncoder.encode(tag, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    )

    if (showDeleteDialog) {
        DeleteSelectedDialog(
            count = selectedCount,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                runOnSelection()
            },
        )
    }

    when (val alert = syncAlert) {
        null -> Unit
        is HomeSyncAlert.Blocked -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
        is HomeSyncAlert.RequiresConfirmation -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncAlert = null
                            scope.launch {
                                requestManualSync(allowHigherV1Version = alert.version)
                            }
                        }
                    ) {
                        Text(R.string.still_sync.string)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.cancel.string)
                    }
                }
            )
        }
        is HomeSyncAlert.Failed -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.sync_failed.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(onClick = { syncAlert = null }) {
                        Text(R.string.close.string)
                    }
                }
            )
        }
    }
}

private val MemoListFabAvoidancePadding = 96.dp

private sealed class HomeSyncAlert {
    data class Blocked(val message: String) : HomeSyncAlert()
    data class RequiresConfirmation(val version: String, val message: String) : HomeSyncAlert()
    data class Failed(val message: String) : HomeSyncAlert()
}
