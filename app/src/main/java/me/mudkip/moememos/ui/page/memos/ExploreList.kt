package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.ExploreLayout
import me.mudkip.moememos.data.model.Memo
import me.mudkip.moememos.ui.component.ExploreMemoCard
import me.mudkip.moememos.ui.util.edgeToEdgeContentPadding
import me.mudkip.moememos.viewmodel.ExploreViewModel

@Composable
fun ExploreList(
    viewModel: ExploreViewModel = hiltViewModel(),
    contentPadding: PaddingValues,
    layout: ExploreLayout = ExploreLayout.LARGE,
) {
    ExploreListContent(viewModel.exploreMemos.collectAsLazyPagingItems(), contentPadding, layout)
}

@Composable
internal fun ExploreListContent(
    memos: LazyPagingItems<Memo>,
    contentPadding: PaddingValues,
    layout: ExploreLayout = ExploreLayout.LARGE,
) {
    when (layout) {
        ExploreLayout.LARGE -> LazyColumn(
            modifier = Modifier.consumeWindowInsets(contentPadding),
            contentPadding = edgeToEdgeContentPadding(contentPadding),
        ) {
            if (memos.loadState.refresh is LoadState.Loading || memos.loadState.refresh is LoadState.Error) {
                item(key = "refresh") {
                    PagingStatus(memos.loadState.refresh, memos::retry)
                }
            } else if (memos.itemCount == 0) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.no_memos),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            items(
                count = memos.itemCount,
                key = memos.itemKey { "memo:${it.remoteId}" },
                contentType = { "memo" },
            ) { index ->
                memos[index]?.let { ExploreMemoCard(it) }
            }
            item(key = "append") {
                PagingStatus(memos.loadState.append, memos::retry)
            }
        }

        ExploreLayout.TWO_COLUMN, ExploreLayout.THREE_COLUMN -> {
            // 三列档不显示图片：列窄了图片基本看不清，却照样要下载和解码。
            val showImages = layout.showsImages
            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.columns),
                modifier = Modifier.consumeWindowInsets(contentPadding),
                contentPadding = edgeToEdgeContentPadding(contentPadding),
            ) {
                // 状态行要占满整行，否则会被挤进一个格子里。
                if (memos.loadState.refresh is LoadState.Loading || memos.loadState.refresh is LoadState.Error) {
                    item(key = "refresh", span = { GridItemSpan(maxLineSpan) }) {
                        PagingStatus(memos.loadState.refresh, memos::retry)
                    }
                } else if (memos.itemCount == 0) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.no_memos),
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(
                    count = memos.itemCount,
                    key = memos.itemKey { "memo:${it.remoteId}" },
                    contentType = { "memo" },
                ) { index ->
                    memos[index]?.let {
                        ExploreMemoCard(it, dense = true, showImages = showImages)
                    }
                }
                item(key = "append", span = { GridItemSpan(maxLineSpan) }) {
                    PagingStatus(memos.loadState.append, memos::retry)
                }
            }
        }
    }
}

@Composable
internal fun PagingStatus(loadState: LoadState, onRetry: () -> Unit) {
    if (loadState is LoadState.NotLoading) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (loadState) {
            is LoadState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading))
            }
            is LoadState.Error -> {
                Text(stringResource(R.string.failed_to_load_memos))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is LoadState.NotLoading -> Unit
        }
    }
}
