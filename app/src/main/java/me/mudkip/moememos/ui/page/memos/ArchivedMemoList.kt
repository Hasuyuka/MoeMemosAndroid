package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.mudkip.moememos.ui.component.ArchivedMemoCard
import me.mudkip.moememos.ui.util.MemoSelectionState
import me.mudkip.moememos.ui.util.edgeToEdgeContentPadding
import me.mudkip.moememos.viewmodel.ArchivedMemoListViewModel
import me.mudkip.moememos.viewmodel.LocalArchivedMemos

@Composable
fun ArchivedMemoList(
    viewModel: ArchivedMemoListViewModel = hiltViewModel(),
    selection: MemoSelectionState? = null,
    contentPadding: PaddingValues
) {
    val listContentPadding = edgeToEdgeContentPadding(contentPadding)

    CompositionLocalProvider(LocalArchivedMemos provides viewModel) {
        LazyColumn(
            modifier = Modifier.consumeWindowInsets(contentPadding),
            contentPadding = listContentPadding
        ) {
            items(viewModel.memos, key = { it.identifier }) { memo ->
                ArchivedMemoCard(
                    memo = memo,
                    selectionMode = selection?.isSelecting == true,
                    selected = selection?.selected?.contains(memo.identifier) == true,
                    onToggleSelection = { selection?.toggle(memo.identifier) },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMemos()
    }
}
