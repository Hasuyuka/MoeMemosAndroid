package me.mudkip.moememos.ui.page.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import me.mudkip.moememos.ui.component.ActionIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.page.common.RouteName

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
    val normalizedCurrentTag = remember(tag) { normalizeTag(tag) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tag) },
                navigationIcon = {
                    if (drawerState != null) {
                        ActionIconButton(label = R.string.menu.string, onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = R.string.menu.string)
                        }
                    }
                },
            )
        },

        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    // 把当前标签带进编辑器。标签就是正文里的 #xxx，所以用户留着它、
                    // 改掉它、或者全删掉，新备忘都会落到实际对应的标签页（或总列表）。
                    navController.navigate(
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
}

private fun normalizeTag(tag: String): String {
    return tag.removePrefix("#")
}

/** 和首页悬浮按钮留出的底部空间一致（见 MemosHomePage 里的同名常量）。 */
private val TagPageFabAvoidancePadding = 96.dp
