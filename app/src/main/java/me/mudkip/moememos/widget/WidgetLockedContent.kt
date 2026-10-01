package me.mudkip.moememos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import me.mudkip.moememos.R

/**
 * 应用锁开启且当前处于锁定状态时，小组件显示的占位内容。
 *
 * 存在的理由：小组件的渲染路径不经过 AppLockGate，此前完全不受应用锁约束——
 * 用户开启应用锁后，锁屏状态下桌面仍明文显示备忘正文。
 *
 * 这里刻意什么都不显示：没有备忘内容，也没有刷新按钮（刷新只会得到同样的占位，
 * 但会白跑一次数据库查询）。整块可点击，点按打开应用触发解锁。
 */
@Composable
internal fun WidgetLockedContent(context: Context, openAppIntent: Intent) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .clickable(actionStartActivity(openAppIntent))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            provider = ImageProvider(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = GlanceModifier.size(32.dp)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = context.getString(R.string.moe_memos),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = context.getString(R.string.widget_locked_message),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp
            )
        )
    }
}
