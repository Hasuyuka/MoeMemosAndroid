package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import kotlinx.coroutines.flow.first
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.ExploreLayout
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.GRID_THUMB_COLUMNS
import me.mudkip.moememos.ui.component.GRID_THUMB_ROWS
import me.mudkip.moememos.ui.component.MemosCard
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.ui.util.edgeToEdgeContentPadding
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.ui.util.MemoSelectionState
import me.mudkip.moememos.util.MemoSortDirection
import me.mudkip.moememos.util.MemoSortMode
import me.mudkip.moememos.util.contentHasTag
import me.mudkip.moememos.util.matches
import me.mudkip.moememos.util.parseMemoQuery
import me.mudkip.moememos.util.sortMemos
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState
import me.mudkip.moememos.viewmodel.ManualSyncResult
import me.mudkip.moememos.viewmodel.MemoScrollAnchor
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemosList(
    contentPadding: PaddingValues,
    lazyListState: LazyListState = rememberLazyListState(),
    tag: String? = null,
    searchString: String? = null,
    additionalBottomPadding: Dp = 16.dp,
    onRefresh: (suspend () -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    onMemoClick: ((String) -> Unit)? = null,
    /** 数据源覆盖：为 null 时用 LocalMemos 的列表（默认行为，其它调用方不受影响）。 */
    memos: List<MemoEntity>? = null,
    /** 列表顶部的可选插槽（例如搜索页的最近搜索）。为 null 时行为与从前完全一致。 */
    header: (@Composable () -> Unit)? = null,
    /**
     * 卡片布局。大卡片是默认行为；两列/三列是顶栏那个布局按钮切出来的。
     * 不传就是大卡片，所以标签页、归档页、搜索页不受影响。
     */
    layout: ExploreLayout = ExploreLayout.LARGE,
    /** 多选。为 null 时列表完全不做多选处理（默认行为）。 */
    selection: MemoSelectionState? = null,
) {
    val context = LocalContext.current
    val navController = LocalRootNavController.current
    val viewModel = LocalMemos.current
    // 网格模式有自己独立的滚动状态（大卡片模式仍然用传进来的 lazyListState）。
    val gridState = rememberLazyGridState()
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val settings by context.settingsDataStore.data.collectAsStateWithLifecycle(initialValue = Settings())
    val editGesture = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.editGesture
    val currentSortMode = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.memoSortMode
        ?: MemoSortMode.CREATED
    val currentSortDirection = settings.usersList
        .firstOrNull { it.accountKey == settings.currentUser }
        ?.settings
        ?.memoSortDirection
        ?: MemoSortDirection.DESCENDING
    val refreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var syncAlert by remember { mutableStateOf<PullRefreshSyncAlert?>(null) }
    val sourceMemos = memos ?: viewModel.memos
    // 过滤和排序合成一次计算，key 全部是便宜的类型。
    //
    // 原来这里有两个 remember，其中一个拿 `sourceMemos.toList()` 当 key：为了让 key
    // 每次重组都"看起来变了"而做的整表复制，接着 Compose 又要把新旧两个 key 逐条
    // 比相等——几千条备忘时这一项就能吃掉一帧。现在用版本号（O(1)）加源列表自身的
    // 引用（同一个对象时 equals 立刻返回），内容真的变了才重新过滤排序。
    val sortedMemos = remember(
        sourceMemos,
        viewModel.listRevision,
        tag,
        searchString,
        currentSortMode,
        currentSortDirection,
    ) {
        var fullList = sourceMemos.filter { it.pinned } + sourceMemos.filter { !it.pinned }

        tag?.let { tag ->
            fullList = fullList.filter { memo -> contentHasTag(memo.content, tag) }
        }

        searchString?.let { searchString ->
            if (searchString.isNotBlank()) {
                // 支持 tag: / is: / visibility: / after: / before: 等前缀；
                // 不认识的前缀会降级成普通关键字（见 util/MemoQuery.kt）。
                val query = parseMemoQuery(searchString)
                fullList = fullList.filter { memo ->
                    query.matches(
                        content = memo.content,
                        pinned = memo.pinned,
                        archived = memo.archived,
                        memoVisibility = memo.visibility,
                        createdDate = memo.date,
                    )
                }
            }
        }

        // 置顶优先由 sortMemos 统一保证（上面那个 pinned + nonPinned 只是让过滤少做
        // 一点无谓的比较，最终顺序仍以这里为准）。
        sortMemos(fullList, currentSortMode, currentSortDirection)
    }
    val listContentPadding = edgeToEdgeContentPadding(
        contentPadding,
        additionalBottomPadding
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            scope.launch {
                if (onRefresh != null) {
                    onRefresh()
                } else {
                    when (val result = viewModel.refreshMemos()) {
                        ManualSyncResult.Completed -> Unit
                        is ManualSyncResult.Blocked -> {
                            syncAlert = PullRefreshSyncAlert.Blocked(result.message)
                        }
                        is ManualSyncResult.RequiresConfirmation -> {
                            syncAlert = PullRefreshSyncAlert.RequiresConfirmation(result.version, result.message)
                        }
                        is ManualSyncResult.Failed -> {
                            syncAlert = PullRefreshSyncAlert.Failed(result.message)
                        }
                    }
                }
                isRefreshing = false
            }
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        when (layout) {
        ExploreLayout.LARGE -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = listContentPadding
        ) {
            if (header != null) {
                item(key = "header") { header() }
            }

            // 此前列表的错误只写进日志（见下方 LaunchedEffect），用户在界面上完全看不到：
            // 同步失败时列表就是旧数据或空的，没有任何解释。现在在列表顶部直接展示，
            // 下次加载成功后 MemosViewModel 会把 errorMessage 置空，它自然消失。
            viewModel.errorMessage?.let { message ->
                item(key = "error") {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            if (sortedMemos.isEmpty()) {
                item(key = "empty") {
                    // 空列表有三种完全不同的成因，此前一律显示「No memos found」：
                    // 用户搜了三个字却看到「没有备忘」，会以为自己把备忘弄丢了。
                    val message = when {
                        !searchString.isNullOrEmpty() ->
                            stringResource(R.string.no_search_results, searchString)
                        tag != null -> stringResource(R.string.no_memos_with_tag)
                        else -> stringResource(R.string.no_memos)
                    }
                    Text(message, modifier = Modifier.padding(24.dp))
                }
            }
            items(sortedMemos, key = { it.identifier }) { memo ->
                MemosCard(
                    memo = memo,
                    onClick = { selectedMemo ->
                        if (onMemoClick != null) {
                            onMemoClick(selectedMemo.identifier)
                        } else navController.navigate(
                            "${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(selectedMemo.identifier)}"
                        )
                    },
                    editGesture = editGesture ?: MemoEditGesture.NONE,
                    previewMode = true,
                    showSyncStatus = currentAccount !is Account.Local,
                    selectionMode = selection?.isSelecting == true,
                    selected = selection?.selected?.contains(memo.identifier) == true,
                    onToggleSelection = {
                            // 长按：先进入多选，再选中这一条
                            selection?.start()
                            selection?.toggle(memo.identifier)
                        },
                    onTagClick = onTagClick
                )
            }
        }

        ExploreLayout.TWO_COLUMN, ExploreLayout.THREE_COLUMN -> {
            // 网格里两种档位都用同一套固定尺寸卡片（见 CompactGridBody）：
            // 两行文字 + 3 列 × 2 行缩略图，超出封顶。
            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.columns),
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(contentPadding),
                state = gridState,
                contentPadding = listContentPadding
            ) {
                // 非卡片行都要占满整行，否则会被挤进一个格子里。
                if (header != null) {
                    item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
                }
                viewModel.errorMessage?.let { message ->
                    item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
                if (sortedMemos.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        val message = when {
                            !searchString.isNullOrEmpty() ->
                                stringResource(R.string.no_search_results, searchString)
                            tag != null -> stringResource(R.string.no_memos_with_tag)
                            else -> stringResource(R.string.no_memos)
                        }
                        Text(message, modifier = Modifier.padding(24.dp))
                    }
                }
                items(count = sortedMemos.size, key = { sortedMemos[it].identifier }) { index ->
                    val memo = sortedMemos[index]
                    // 卡片高度固定 = 头部 + 两行文字 + 两行缩略图，与内容多少无关，
                    // 网格因此在视觉上是整齐的。缩略图是正方形，边长由格子宽度决定，
                    // 所以高度要按实际宽度算（BoxWithConstraints）。
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val thumb = (maxWidth - GridCardPadding) / GRID_THUMB_COLUMNS
                        MemosCard(
                        memo = memo,
                        onClick = { selectedMemo ->
                            if (onMemoClick != null) {
                                onMemoClick(selectedMemo.identifier)
                            } else navController.navigate(
                                "${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(selectedMemo.identifier)}"
                            )
                        },
                        editGesture = editGesture ?: MemoEditGesture.NONE,
                        showSyncStatus = currentAccount !is Account.Local,
                        // 多列时收紧卡片间距：大卡片那种 15dp 并排起来会变成一道大沟。
                        dense = true,
                        gridCardHeight = GridCardHeaderHeight + GridCardTextHeight + thumb * GRID_THUMB_ROWS,
                        selectionMode = selection?.isSelecting == true,
                        selected = selection?.selected?.contains(memo.identifier) == true,
                        onToggleSelection = {
                            // 长按：先进入多选，再选中这一条
                            selection?.start()
                            selection?.toggle(memo.identifier)
                        },
                        onTagClick = onTagClick
                        )
                    }
                }
            }
        }
        }
    }

    LaunchedEffect(viewModel.errorMessage) {
        viewModel.errorMessage?.let {
            // 保留日志用于排障；界面上的可见反馈见上方 LazyColumn 里的 error 项。
            Timber.d(it)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMemos()
    }

    // 只在**切换布局**时回到顶部。
    //
    // 之前这个副作用的触发条件里还包含"列表头那一篇变了"，结果是打开一篇备忘
    // 再返回就会跳回最上面（按修改时间排序时，刚看过的备忘变成了最新的那条，
    // 列表头随之改变）。数据变化——同步来了新备忘、排序方式换了——都不该把人
    // 拽回顶部。
    LaunchedEffect(layout) {
        if (layout == ExploreLayout.LARGE) lazyListState.scrollToItem(0) else gridState.scrollToItem(0)
    }

    // 恢复滚动位置。
    //
    // 放在保存锚点的副作用**之前**：副作用按声明顺序启动，声明在前才能保证
    // 恢复读到的是上一次真正记下的位置，而不是刚回到列表时那个"位置还是 0"的默认值。
    //
    // 判据是"当前停在顶部、但记着的位置不是顶部"——只有状态真的被重置过才会触发。
    //
    // 两个分支分开写：LazyListState 和 LazyGridState 若放进同一个 if/else 赋值，
    // 会被推断成共同父类 ScrollableState，而那上面没有 scrollToItem。
    // 本次进入列表后，锚点是否已经处理完。
    //
    // 返回列表的第一帧，滚动状态是 0——那是还没恢复的默认值，不是用户的位置。
    // 在锚点处理完之前，它不许覆盖记录下来的位置（上一版的 bug 就是这么丢的）。
    var anchorResolved by remember { mutableStateOf(false) }

    // 恢复滚动位置。
    //
    // 判据是"当前停在顶部、但记着的位置不是顶部"——只有状态真的被重置过才动手，
    // 正常浏览时不会打扰用户。key 里带着 sortedMemos：打开备忘再返回会触发一次同步，
    // 同步过程中列表可能被清空再填回把位置冲掉，那次变化会让本副作用重新跑一遍，
    // 于是又能把人放回去（用户反馈的"偶尔还是跳顶部"就是漏了这一手）。
    //
    // 滚动前要**等列表真的完成布局**：scrollToItem 作用在还没排版的列表上会被丢掉，
    // 之后列表按 0 排版——这是"偶尔"的另一个来源。
    //
    // 两个分支分开写：LazyListState 和 LazyGridState 若放进同一个 if/else 赋值，
    // 会被推断成共同父类 ScrollableState，而那上面没有 scrollToItem。
    LaunchedEffect(sortedMemos, layout) {
        try {
            val anchor = viewModel.scrollAnchor ?: return@LaunchedEffect
            if (anchor.layout != layout) return@LaunchedEffect
            val target = sortedMemos.indexOfFirst { it.identifier == anchor.identifier }
            if (target < 0) return@LaunchedEffect
            if (target == 0 && anchor.offset == 0) return@LaunchedEffect
            if (layout == ExploreLayout.LARGE) {
                val atTop = lazyListState.firstVisibleItemIndex == 0 &&
                    lazyListState.firstVisibleItemScrollOffset == 0
                if (!atTop) return@LaunchedEffect
                snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
                lazyListState.scrollToItem(target, anchor.offset)
            } else {
                val atTop = gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0
                if (!atTop) return@LaunchedEffect
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
                gridState.scrollToItem(target, anchor.offset)
            }
        } finally {
            // 无论走到哪个分支都算处理完：否则用户自己滚回顶部时，记录永远写不进去。
            anchorResolved = true
        }
    }

    // 滚动时更新锚点。
    //
    // 锚点处理完之前，"位置 = 0"一律不写——那是刚回到列表、还没恢复的状态，
    // 不是用户离开时的位置。
    LaunchedEffect(lazyListState, gridState, sortedMemos, layout) {
        snapshotFlow {
            if (layout == ExploreLayout.LARGE) {
                lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
            } else {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
        }.collect { (index, offset) ->
            if (index == 0 && offset == 0 && !anchorResolved) return@collect
            val memo = sortedMemos.getOrNull(index) ?: return@collect
            viewModel.saveScrollAnchor(MemoScrollAnchor(memo.identifier, offset, layout))
        }
    }

    // rememberUpdatedState 必须写在 DisposableEffect **外面**：它是 @Composable 函数，
// 而 effect 的块不是组合上下文，在里面调用编译不过。
val latestLayout by rememberUpdatedState(layout)
val latestMemos by rememberUpdatedState(sortedMemos)

    // 离开列表时无条件记一次：这里的值才是用户真正的"离开位置"，
    // 包括他自己滚回了顶部（上面那个副作用不会记录这种情况）。
    DisposableEffect(Unit) {
        onDispose {
            val index: Int
            val offset: Int
            if (latestLayout == ExploreLayout.LARGE) {
                index = lazyListState.firstVisibleItemIndex
                offset = lazyListState.firstVisibleItemScrollOffset
            } else {
                index = gridState.firstVisibleItemIndex
                offset = gridState.firstVisibleItemScrollOffset
            }
            latestMemos.getOrNull(index)?.let { memo ->
                viewModel.saveScrollAnchor(MemoScrollAnchor(memo.identifier, offset, latestLayout))
            }
        }
    }

    when (val alert = syncAlert) {
        null -> Unit
        is PullRefreshSyncAlert.Blocked -> {
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
        is PullRefreshSyncAlert.RequiresConfirmation -> {
            AlertDialog(
                onDismissRequest = { syncAlert = null },
                title = { Text(R.string.unsupported_memos_version_title.string) },
                text = { Text(alert.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            syncAlert = null
                            scope.launch {
                                when (val result = viewModel.refreshMemos(alert.version)) {
                                    ManualSyncResult.Completed -> Unit
                                    is ManualSyncResult.Blocked -> {
                                        syncAlert = PullRefreshSyncAlert.Blocked(result.message)
                                    }
                                    is ManualSyncResult.RequiresConfirmation -> {
                                        syncAlert = PullRefreshSyncAlert.RequiresConfirmation(result.version, result.message)
                                    }
                                    is ManualSyncResult.Failed -> {
                                        syncAlert = PullRefreshSyncAlert.Failed(result.message)
                                    }
                                }
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
        is PullRefreshSyncAlert.Failed -> {
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

// ---- 网格卡片尺寸 ----
// 卡片高度 = 固定的头部与文字高度 + 两行正方形缩略图。
// 缩略图边长随格子宽度变化，所以高度也跟着变；同一屏里所有列宽相同，
// 因此所有卡片高度一致，视觉上是整齐的。
private val GridCardHeaderHeight = 26.dp
private val GridCardTextHeight = 44.dp

/** 卡片自身的外边距 + 内边距，缩略图边长要减掉它。 */
private val GridCardPadding = 22.dp

private sealed class PullRefreshSyncAlert {
    data class Blocked(val message: String) : PullRefreshSyncAlert()
    data class RequiresConfirmation(val version: String, val message: String) : PullRefreshSyncAlert()
    data class Failed(val message: String) : PullRefreshSyncAlert()
}
