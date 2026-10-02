package me.mudkip.moememos.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import me.mudkip.moememos.R
import me.mudkip.moememos.data.local.entity.MemoEntity
import me.mudkip.moememos.data.model.Account
import me.mudkip.moememos.data.model.MemoEditGesture
import me.mudkip.moememos.ext.icon
import me.mudkip.moememos.ext.navigateToMemoEditor
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.ext.titleResource
import me.mudkip.moememos.ui.media.MediaViewerActivity
import me.mudkip.moememos.ui.page.common.LocalRootNavController
import me.mudkip.moememos.viewmodel.LocalMemos
import me.mudkip.moememos.viewmodel.LocalUserState

@Composable
fun MemosCard(
    memo: MemoEntity,
    onClick: (MemoEntity) -> Unit,
    editGesture: MemoEditGesture = MemoEditGesture.NONE,
    previewMode: Boolean = false,
    showSyncStatus: Boolean = false,
    /** 是否渲染附件图片。三列布局那种窄格子里关掉，省下下载与解码。 */
    showResources: Boolean = true,
    /** 网格/多列布局：收紧卡片内外边距。大卡片那种 15dp 在两列并排时会变成一道大沟。 */
    dense: Boolean = false,
    /**
     * 网格模式下卡片的固定高度，由调用方按缩略图尺寸算好传入。
     * 传 null 表示高度随内容走（大卡片模式）。固定高度 + 正文截断 = 视觉上整齐。
     */
    gridCardHeight: Dp? = null,
    /** 多选模式：整张卡片可点按勾选，右上角的操作菜单让位给复选框。 */
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null
) {
    val memosViewModel = LocalMemos.current
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()

    // 多选模式下点击是「勾选」，不再打开备忘；编辑手势也一并让位，免得两个含义打架。
    val toggleSelection = if (selectionMode) onToggleSelection else null

    val cardModifier = Modifier
        .padding(
            horizontal = if (dense) 4.dp else 15.dp,
            vertical = if (dense) 4.dp else 10.dp
        )
        .fillMaxWidth()
        // 网格模式：高度固定，与内容多少无关，网格才是视觉整齐的。
        .then(if (gridCardHeight != null) Modifier.height(gridCardHeight) else Modifier)
        .then(
            if (toggleSelection != null) {
                Modifier.clickable { toggleSelection() }
            } else {
                Modifier.combinedClickable(
                    onClick = {
                        if (editGesture == MemoEditGesture.SINGLE) {
                            rootNavController.navigateToMemoEditor(memo.identifier)
                        } else {
                            onClick(memo)
                        }
                    },
                    onLongClick = if (editGesture == MemoEditGesture.LONG) {
                        {
                            rootNavController.navigateToMemoEditor(memo.identifier)
                        }
                    } else {
                        null
                    },
                    onDoubleClick = if (editGesture == MemoEditGesture.DOUBLE) {
                        {
                            rootNavController.navigateToMemoEditor(memo.identifier)
                        }
                    } else {
                        null
                    }
                )
            }
        )

    Card(
        modifier = cardModifier,
        border = if (memo.pinned) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        }
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(start = if (dense) 8.dp else 15.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { toggleSelection?.invoke() },
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(
                        memo.date.toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.SECOND_IN_MILLIS
                    ).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                if (showSyncStatus && memo.needsSync) {
                    Icon(
                        imageVector = Icons.Outlined.CloudOff,
                        contentDescription = R.string.memo_sync_pending.string,
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (LocalUserState.current.currentUser?.defaultVisibility != memo.visibility) {
                    Icon(
                        memo.visibility.icon,
                        contentDescription = stringResource(memo.visibility.titleResource),
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (selectionMode) {
                    // 多选模式下让位给复选框，避免一边勾选一边误触菜单。
                    Spacer(modifier = Modifier.width(15.dp))
                } else {
                    MemosCardActionButton(memo)
                }
            }

            if (gridCardHeight != null) {
                // 网格模式：固定高度，所以正文必须截断、缩略图数量必须封顶，
                // 否则内容会溢出卡片。这里显示两行文字 + 最多 3 列 × 2 行缩略图，
                // 剩下的点开备忘录看全。
                CompactGridBody(memo = memo)
            } else {
                MemoContent(
                    memo,
                    previewMode = previewMode,
                    showResources = showResources,
                checkboxChange = { checked, startOffset, endOffset ->
                    scope.launch {
                        var text = memo.content.substring(startOffset, endOffset)
                        text = if (checked) {
                            text.replace("[ ]", "[x]")
                        } else {
                            text.replace("[x]", "[ ]")
                        }
                        memosViewModel.editMemo(
                            memo.identifier,
                            memo.content.replaceRange(startOffset, endOffset, text),
                            memo.resources,
                            memo.visibility
                        )
                    }
                },
                onViewMore = {
                    onClick(memo)
                },
                onTagClick = onTagClick
                )
            }
        }
    }
}

/** 网格卡片里的缩略图格子：3 列 × 最多 2 行。数量封顶是固定高度的前提。 */
internal const val GRID_THUMB_COLUMNS = 3
internal const val GRID_THUMB_ROWS = 2

/**
 * 网格模式下的卡片正文：**两行文字 + 3 列 × 2 行缩略图**。
 *
 * 卡片高度是固定的（由调用方按缩略图尺寸算出），所以这里的内容必须自己封顶：
 * 文字最多两行、超出省略；缩略图最多 [GRID_THUMB_COLUMNS]×[GRID_THUMB_ROWS] 张，
 * 还有多的就在最后一张角上标 "+N"。点开备忘录能看全部。
 *
 * 排序按上传先后（见 MemoResourceContent 里的同一套规则），这样卡片里的
 * 第一张就是用户最先选的那张。
 */
@Composable
private fun CompactGridBody(memo: MemoEntity) {
    val context = LocalContext.current
    val preview = remember(memo.content) { extractPreviewContent(memo.content).first }
    val images = remember(memo.resources) {
        memo.resources
            .filter { it.mimeType?.startsWith("image/") == true }
            .sortedWith(UPLOAD_ORDER)
    }
    val shown = images.take(GRID_THUMB_COLUMNS * GRID_THUMB_ROWS)
    val hiddenCount = images.size - shown.size

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            text = preview,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        shown.chunked(GRID_THUMB_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(modifier = Modifier.padding(bottom = 2.dp)) {
                row.forEachIndexed { indexInRow, resource ->
                    val globalIndex = rowIndex * GRID_THUMB_COLUMNS + indexInRow
                    Box(modifier = Modifier.weight(1f).padding(1.dp)) {
                        MemoImage(
                            url = resource.localUri ?: resource.uri,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(3.dp)),
                            resourceIdentifier = resource.identifier,
                            onClick = {
                                context.startActivity(
                                    Intent(context, MediaViewerActivity::class.java).apply {
                                        val urls = images.map { it.localUri ?: it.uri }.toTypedArray()
                                        putExtra(MediaViewerActivity.EXTRA_IMAGE_URLS, urls)
                                        putExtra(
                                            MediaViewerActivity.EXTRA_INITIAL_INDEX,
                                            globalIndex
                                        )
                                    }
                                )
                            }
                        )
                        if (hiddenCount > 0 && globalIndex == shown.lastIndex) {
                            Text(
                                text = "+$hiddenCount",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(
                                        Color.Black.copy(alpha = 0.55f),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                // 这一行没满就补空格，否则最后几格会被拉宽
                repeat(GRID_THUMB_COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f).padding(1.dp))
                }
            }
        }
    }
}

@Composable
fun MemosCardActionButton(
    memo: MemoEntity,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val memosViewModel = LocalMemos.current
    val userStateViewModel = LocalUserState.current
    val currentAccount by userStateViewModel.currentAccount.collectAsStateWithLifecycle()
    val rootNavController = LocalRootNavController.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val memoLabel = stringResource(R.string.memo)

    Box {
        ActionIconButton(label = stringResource(R.string.more_options), onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = null)
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            if (memo.pinned) {
                DropdownMenuItem(
                    text = { Text(R.string.unpin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, false).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PinDrop,
                            contentDescription = null
                        )
                    })
            } else {
                DropdownMenuItem(
                    text = { Text(R.string.pin.string) },
                    onClick = {
                        scope.launch {
                            memosViewModel.updateMemoPinned(memo.identifier, true).suspendOnSuccess {
                                menuExpanded = false
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                text = { Text(R.string.edit.string) },
                onClick = {
                    rootNavController.navigateToMemoEditor(memo.identifier)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.share.string) },
                onClick = {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, memo.content)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.copy.string) },
                onClick = {
                    clipboardManager?.setPrimaryClip(
                        ClipData.newPlainText(memoLabel, memo.content)
                    )
                    menuExpanded = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = null
                    )
                })
            if (currentAccount !is Account.Local) {
                DropdownMenuItem(
                    text = { Text(R.string.copy_link.string) },
                    onClick = {
                        memosViewModel.host.value?.let { host ->
                            val memoUrl = "$host/${memo.remoteId ?: memo.identifier}"
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, memoUrl)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Link,
                            contentDescription = null
                        )
                    })
            }
            DropdownMenuItem(
                text = { Text(R.string.archive.string) },
                onClick = {
                    scope.launch {
                        memosViewModel.archiveMemo(memo.identifier).suspendOnSuccess {
                            menuExpanded = false
                        }
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Archive,
                        contentDescription = null
                    )
                })
            DropdownMenuItem(
                text = { Text(R.string.delete.string) },
                onClick = {
                    showDeleteDialog = true
                    menuExpanded = false
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null
                    )
                })
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(R.string.delete_this_memo.string) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            memosViewModel.deleteMemo(memo.identifier).suspendOnSuccess {
                                showDeleteDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(R.string.confirm.string)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }
}
