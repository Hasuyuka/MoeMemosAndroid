package me.mudkip.moememos.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
import me.mudkip.moememos.util.MemoSortDirection
import me.mudkip.moememos.util.MemoSortMode

/** 顶栏上的排序入口。「灵感」页和标签页共用。 */
@Composable
fun MemoSortButton(
    current: MemoSortMode,
    onClick: () -> Unit,
) {
    ActionIconButton(label = R.string.sort.string, onClick = onClick) {
        Icon(Icons.Outlined.Sort, contentDescription = R.string.sort.string)
    }
}

/**
 * 排序设置对话框：**上面选排序方式，下面一行选升序/降序**。
 *
 * 两个维度分开呈现，因为它们互相独立：「按创建时间·升序」是最旧的在前，
 * 「按字母·降序」是 Z-A。合成一个六项长列表反而更难扫。
 *
 * 选中立即生效但**对话框不关闭**——两个维度常常要各调一次，留在原地能一眼看到
 * 当前组合（例如「按创建时间」和「降序」两项同时高亮）。
 */
@Composable
fun MemoSortDialog(
    current: MemoSortMode,
    currentDirection: MemoSortDirection,
    onSelectMode: (MemoSortMode) -> Unit,
    onSelectDirection: (MemoSortDirection) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.sort.string) },
        text = {
            Column {
                MemoSortMode.entries.forEach { mode ->
                    SortChoiceRow(
                        label = mode.titleResource.string,
                        selected = mode == current,
                        onClick = { onSelectMode(mode) },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MemoSortDirection.entries.forEach { direction ->
                        Text(
                            text = direction.titleResource.string,
                            color = if (direction == currentDirection) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectDirection(direction) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(R.string.close.string) }
        },
    )
}

@Composable
private fun SortChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
    )
}

private val MemoSortMode.titleResource: Int
    get() = when (this) {
        MemoSortMode.UPDATED -> R.string.sort_updated
        MemoSortMode.CREATED -> R.string.sort_created
        MemoSortMode.TITLE -> R.string.sort_title
    }

private val MemoSortDirection.titleResource: Int
    get() = when (this) {
        MemoSortDirection.ASCENDING -> R.string.sort_ascending
        MemoSortDirection.DESCENDING -> R.string.sort_descending
    }