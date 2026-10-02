package me.mudkip.moememos.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.R
import me.mudkip.moememos.ext.string
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

/** 排序方式选择对话框。 */
@Composable
fun MemoSortDialog(
    current: MemoSortMode,
    onSelect: (MemoSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(R.string.sort.string) },
        text = {
            Column {
                MemoSortMode.entries.forEach { mode ->
                    Text(
                        text = mode.titleResource.string,
                        color = if (mode == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(R.string.close.string) }
        },
    )
}

private val MemoSortMode.titleResource: Int
    get() = when (this) {
        MemoSortMode.UPDATED -> R.string.sort_updated
        MemoSortMode.CREATED -> R.string.sort_created
        MemoSortMode.TITLE -> R.string.sort_title
    }