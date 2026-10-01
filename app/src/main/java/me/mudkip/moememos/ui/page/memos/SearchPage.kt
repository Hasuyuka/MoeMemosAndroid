package me.mudkip.moememos.ui.page.memos

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.data.model.currentUserSettings
import me.mudkip.moememos.data.model.updateCurrentUserSettings
import me.mudkip.moememos.data.model.updateRecentSearches
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ui.component.ActionIconButton
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.LocalMemos

@Composable
fun SearchPage(navController: NavHostController) {
    val searchText = rememberTextFieldState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = LocalMemos.current
    val settings by context.settingsDataStore.data.collectAsStateWithLifecycle(initialValue = Settings())
    val recentSearches = settings.currentUserSettings().recentSearches
    val query = searchText.text.toString()
    var includeArchived by rememberSaveable { mutableStateOf(false) }

    fun recordSearch(value: String) {
        if (value.isBlank()) {
            return
        }
        scope.launch(Dispatchers.IO) {
            context.settingsDataStore.updateData { existing ->
                existing.updateCurrentUserSettings { user ->
                    user.copy(recentSearches = updateRecentSearches(user.recentSearches, value))
                }
            }
        }
    }

    fun clearSearchHistory() {
        scope.launch(Dispatchers.IO) {
            context.settingsDataStore.updateData { existing ->
                existing.updateCurrentUserSettings { it.copy(recentSearches = emptyList()) }
            }
        }
    }

    // 归档备忘按需加载：底层是本地 DAO 查询，但用户没要求时也没必要做。
    LaunchedEffect(includeArchived) {
        if (includeArchived) {
            viewModel.loadArchivedMemos()
        }
    }

    // 为 null 表示沿用 MemosList 自己的数据源（默认只搜当前列表）。
    val scopeMemos: List<MemoEntity>? = remember(
        includeArchived,
        viewModel.memos.toList(),
        viewModel.archivedMemos.toList(),
    ) {
        if (includeArchived) viewModel.memos + viewModel.archivedMemos else null
    }

    val header: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            SearchScopeRow(
                includeArchived = includeArchived,
                onIncludeArchivedChange = { includeArchived = it },
            )
            // 只在「还没开始输入」时展示历史：一旦有了查询词，用户关心的是结果而不是历史。
            if (query.isBlank() && recentSearches.isNotEmpty()) {
                RecentSearchesRow(
                    searches = recentSearches,
                    onSelect = { value -> searchText.edit { replace(0, length, value) } },
                    onClear = { clearSearchHistory() },
                )
            }
        }
    }

    MemoBrowser { onMemoClick ->
        Scaffold(
            topBar = {
                MemoSearchBar(
                    searchText = searchText,
                    onBack = {
                        navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                    },
                    onSubmit = { recordSearch(searchText.text.toString()) },
                )
            },
        ) { innerPadding ->
            MemosList(
                contentPadding = innerPadding,
                searchString = query,
                memos = scopeMemos,
                onMemoClick = { identifier ->
                    // 点开某条结果说明这次搜索确实有效，是最可靠的历史信号——
                    // 很多人在输入法上并不会去按搜索键。
                    recordSearch(query)
                    onMemoClick(identifier)
                },
                onTagClick = { tag ->
                    navController.navigate("${RouteName.TAG}/${Uri.encode(tag)}") {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                header = header,
            )
        }
    }
}

/**
 * 搜索范围。刻意只做**两态**（当前列表 / 含归档）而不是三态：
 * 「仅归档」的浏览已经由归档页负责，搜索页再加一档只会让「我现在搜的是什么范围」
 * 变得模糊。默认关闭，因此不改变既有行为。
 */
@Composable
private fun SearchScopeRow(
    includeArchived: Boolean,
    onIncludeArchivedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = includeArchived,
            onClick = { onIncludeArchivedChange(!includeArchived) },
            label = { Text(stringResource(R.string.include_archived)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoSearchBar(
    searchText: TextFieldState,
    onBack: () -> Unit,
    onSubmit: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    SearchBar(
        state = rememberSearchBarState(),
        modifier = Modifier
            .windowInsetsPadding(SearchBarDefaults.windowInsets)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                state = searchText,
                onSearch = {
                    focusManager.clearFocus()
                    onSubmit()
                },
                // Results occupy this route's list pane, rather than an overlay.
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search)) },
                leadingIcon = {
                    ActionIconButton(label = stringResource(R.string.back), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (searchText.text.isNotEmpty()) {
                        ActionIconButton(
                            label = stringResource(R.string.clear_search),
                            onClick = {
                                searchText.edit { replace(0, length, "") }
                                focusRequester.requestFocus()
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                },
            )
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearchesRow(
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.recent_searches),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear_search_history))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            searches.forEach { value ->
                AssistChip(
                    onClick = { onSelect(value) },
                    label = {
                        Text(
                            text = value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}
