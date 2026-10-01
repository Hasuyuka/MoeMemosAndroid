package me.mudkip.moememos.ui.component

import android.text.TextUtils
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.mudkip.moememos.data.model.Memo

@Composable
fun ExploreMemoCard(
    memo: Memo,
    /** 网格里用：收紧内外边距，正文只留摘要。 */
    dense: Boolean = false,
    /** 是否显示附件图片。三列档关掉，省下下载与解码。 */
    showImages: Boolean = true,
) {
    Card(
        modifier = Modifier
            .padding(
                horizontal = if (dense) 4.dp else 15.dp,
                vertical = if (dense) 4.dp else 10.dp,
            )
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (dense) 10.dp else 15.dp,
                        top = if (dense) 10.dp else 15.dp,
                        bottom = 10.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    DateUtils.getRelativeTimeSpanString(memo.date.toEpochMilli(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )

                if (memo.creator != null && !TextUtils.isEmpty(memo.creator.name)) {
                    Text(
                        "@${memo.creator.name}",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            MemoContent(memo, previewMode = dense, showResources = showImages)
        }
    }
}
