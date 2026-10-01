package me.mudkip.moememos.ui.page.resource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.ResourceEntity
import me.mudkip.moememos.ext.popBackStackIfLifecycleIsResumed
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ui.component.Attachment
import me.mudkip.moememos.ui.component.MemoImage
import me.mudkip.moememos.ui.media.MediaViewerActivity
import me.mudkip.moememos.ui.page.common.RouteName
import me.mudkip.moememos.viewmodel.ResourceListViewModel
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredGridItems

private enum class ResourceFilter {
    IMAGE,
    OTHER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceListPage(
    navController: NavHostController,
    viewModel: ResourceListViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var selectedFilter by rememberSaveable { mutableStateOf(ResourceFilter.IMAGE) }
    val imageResources = viewModel.resources.filter { it.mimeType?.startsWith("image/") == true }
    val otherResources = viewModel.resources.filterNot { it.mimeType?.startsWith("image/") == true }
    val imageUrls = imageResources.map { it.localUri ?: it.uri }

    fun openOwningMemo(resource: ResourceEntity) {
        val memoId = resource.memoId ?: return
        navController.navigate("${RouteName.MEMO_DETAIL}?memoId=${Uri.encode(memoId)}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = R.string.resources.string) },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = R.string.back.string)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selectedFilter == ResourceFilter.IMAGE,
                    onClick = { selectedFilter = ResourceFilter.IMAGE },
                    label = { Text(R.string.image.string) }
                )
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selectedFilter == ResourceFilter.OTHER,
                    onClick = { selectedFilter = ResourceFilter.OTHER },
                    label = { Text(R.string.other.string) }
                )
            }

            // 此前这里只有「有数据」一条分支：加载失败、加载中、空列表都渲染成一片空白，
            // 用户既不知道在加载、也不知道失败了、更不知道为什么什么都没有。
            val displayed = if (selectedFilter == ResourceFilter.IMAGE) imageResources else otherResources
            when {
                viewModel.isLoading && viewModel.resources.isEmpty() -> {
                    ResourceStatus {
                        Text(R.string.loading.string)
                    }
                }

                viewModel.errorMessage != null && viewModel.resources.isEmpty() -> {
                    ResourceStatus {
                        Text(
                            text = R.string.failed_to_load_resources.string,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { viewModel.loadResources() }) {
                            Text(R.string.retry.string)
                        }
                    }
                }

                displayed.isEmpty() -> {
                    ResourceStatus {
                        Text(
                            text = if (selectedFilter == ResourceFilter.IMAGE) {
                                R.string.no_images.string
                            } else {
                                R.string.no_resources.string
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                selectedFilter == ResourceFilter.IMAGE -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        staggeredGridItems(imageResources, key = { it.identifier }) { resource ->
                            MemoImage(
                                url = resource.localUri ?: resource.uri,
                                resourceIdentifier = resource.identifier,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                onClick = {
                                    // 走应用内查看器，与 MemoContent 里的行为一致：
                                    // 不传 onClick 时 MemoImage 会退回系统 ACTION_VIEW 打开磁盘缓存文件，
                                    // 资源页此前就是这样，点击图片会跳出应用、且看到的是缓存副本。
                                    context.startActivity(
                                        Intent(context, MediaViewerActivity::class.java).apply {
                                            putExtra(
                                                MediaViewerActivity.EXTRA_IMAGE_URLS,
                                                imageUrls.toTypedArray()
                                            )
                                            putExtra(
                                                MediaViewerActivity.EXTRA_INITIAL_INDEX,
                                                imageResources.indexOf(resource).coerceAtLeast(0)
                                            )
                                            putExtra(MediaViewerActivity.EXTRA_CAPTION, resource.filename)
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        lazyItems(otherResources, key = { it.identifier }) { resource ->
                            Attachment(
                                resource = resource,
                                onOpenMemo = { openOwningMemo(resource) },
                                showMenu = true
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadResources()
    }
}

/** 把加载中 / 失败 / 空态统一居中呈现，避免三处各写一遍布局。 */
@Composable
private fun ResourceStatus(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
