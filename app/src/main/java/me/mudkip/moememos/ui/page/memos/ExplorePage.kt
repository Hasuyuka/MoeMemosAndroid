package me.mudkip.moememos.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.model.Settings
import me.mudkip.moememos.ext.settingsDataStore
import me.mudkip.moememos.ext.settingsState
import me.mudkip.moememos.ext.string

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorePage(
    drawerState: DrawerState? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = context.settingsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.explore.string) },
                navigationIcon = {
                    if (drawerState != null) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    }
                },
                actions = {
                    // 点一次换下一档（大卡片 → 两列 → 三列 → 大卡片），选择会被记住。
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                context.settingsDataStore.updateData { existing ->
                                    existing.copy(exploreLayout = existing.exploreLayout.next())
                                }
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.GridView,
                            contentDescription = stringResource(R.string.change_layout),
                        )
                    }
                }
            )
        },

        content = { innerPadding ->
            ExploreList(
                contentPadding = innerPadding,
                layout = settings.exploreLayout,
            )
        }
    )
}
