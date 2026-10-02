package me.mudkip.moememos.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import me.mudkip.moememos.ui.component.ActionIconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.data.model.updateCurrentUserSettings
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.DeleteSelectedDialog
import me.mudkip.moememos.ui.component.MemoSortButton
import me.mudkip.moememos.ui.component.MemoSortDialog
import me.mudkip.moememos.ui.component.SelectionTopBar
import me.mudkip.moememos.ui.component.rememberRunOnSelection
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.util.MemoSelectionState
import me.mudkip.moememos.util.MemoSortDirection
import me.mudkip.moememos.util.MemoSortMode
import me.mudkip.moememos.util.contentHasTag
import me.mudkip.moememos.viewmodel.LocalMemos

@Composable
fun TagMemoPage(
    drawerState: DrawerState? = null,
    tag: String,
    navController: NavHostController
) {
    MemoBrowser { onMemoClick ->
        TagMemoPageContent(drawerState, tag, navController, onMemoClick)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagMemoPageContent(
    drawerState: DrawerState? = null,
    tag: String,
    navController: NavHostController,
    onMemoClick: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 必须在 composable 里取出来：onClick 不是 composable lambda，在里面读 CompositionLocal 编译不过。
    val rootNavController = LocalRootNavController.current
    val normalizedCurrentTag = remember(tag) { normalizeTag(tag) }
    val context = LocalContext.current
    val memosViewModel = LocalMemos.current
    val settings by context.settingsDataStore.data.collectAsStateWithLifecycle(initialValue = Settings())
    val selection = remember { MemoSelectionState() }
    var showSortDialog by remember { mutableStateOf(false) }
    val sortMode = settings.usersList.firstOrNull { it.accountKey == settings.currentUser }
        ?.settings?.memoSortMode ?: MemoSortMode.CREATED
    val sortDirection = settings.usersList.firstOrNull { it.accountKey == settings.currentUser }
        ?.settings?.memoSortDirection ?: MemoSortDirection.DESCENDING
    var showDeleteDialog by remember { mutableStateOf(false) }
    val visibleIds = remember(memosViewModel.memos, tag) {
        memosViewModel.memos.filter { contentHasTag(it.content, tag) }.map { it.identifier }
    }
    val selectedCount = selection.visibleCountIn(visibleIds)
    val runOnSelection = rememberRunOnSelection(
        scope = scope,
        selection = selection,
        visibleIds = visibleIds,
        onItem = { memosViewModel.deleteMemo(it) },
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
                )
            } else {
                TopAppBar(
                    title = { Text(tag) },
                    navigationIcon = {
                        if (drawerState != null) {
                            ActionIconButton(label = R.string.menu.string, onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                            }
                        }
                    },
                    actions = {
                        // 和「灵感」页保持一致：搜索、布局、多选三个入口。
                        ActionIconButton(label = R.string.search.string, onClick = {
                            navController.navigate(RouteName.SEARCH)
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = R.string.search.string)
                        }
                        ActionIconButton(label = R.string.change_layout.string, onClick = {
                            scope.launch(Dispatchers.IO) {
                                context.settingsDataStore.updateData { existing ->
                                    existing.copy(exploreLayout = existing.exploreLayout.next())
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.GridView, contentDescription = R.string.change_layout.string)
                        }
                        MemoSortButton(current = sortMode) { showSortDialog = true }
                    },
                )
            }
        },

        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // INPUT 路由注册在**根**导航图上，而这里是标签页自己的子图控制器；
                    // 用子图控制器跳一个不存在的目的地会直接抛 IllegalArgumentException（闪退）。
                    // 备忘详情页的跳转同理，所以这个页面一直用的是 rootNavController。
                    rootNavController.navigate(
                        "${RouteName.INPUT}?tag=${java.net.URLEncoder.encode(tag, "UTF-8")}"
                    )
                },
                text = { Text(R.string.new_memo.string) },
                icon = { Icon(Icons.Filled.Add, contentDescription = R.string.compose.string) }
            )
        },

        content = { innerPadding ->
            MemosList(
                onMemoClick = onMemoClick,
                contentPadding = innerPadding,
                tag = tag,
                // 别让最后一条被悬浮按钮盖住
                additionalBottomPadding = TagPageFabAvoidancePadding,
                layout = settings.exploreLayout,
                selection = selection,
                onTagClick = { clickedTag ->
                    if (normalizeTag(clickedTag) == normalizedCurrentTag) {
                        return@MemosList
                    }
                    navController.navigate("${RouteName.TAG}/${java.net.URLEncoder.encode(clickedTag, "UTF-8")}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    )

    if (showSortDialog) {
        MemoSortDialog(
            current = sortMode,
            currentDirection = sortDirection,
            onSelectMode = { mode ->
                scope.launch(Dispatchers.IO) {
                    context.settingsDataStore.updateData { existing ->
                        existing.updateCurrentUserSettings { it.copy(memoSortMode = mode) }
                    }
                }
            },
            onSelectDirection = { direction ->
                scope.launch(Dispatchers.IO) {
                    context.settingsDataStore.updateData { existing ->
                        existing.updateCurrentUserSettings { it.copy(memoSortDirection = direction) }
                    }
                }
            },
            onDismiss = { showSortDialog = false },
        )
    }

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
}

private fun normalizeTag(tag: String): String {
    return tag.removePrefix("#")
}

/** 和首页悬浮按钮留出的底部空间一致（见 MemosHomePage 里的同名常量）。 */
private val TagPageFabAvoidancePadding = 96.dp
