package me.mudkip.moememos.ui.page.memos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * 列表滚动位置的**临时**诊断。
 *
 * 只想回答一个问题：从备忘详情返回列表时，位置到底被谁改掉了。
 *
 * 记录按时间顺序滚动，所以「先看到什么、后看到什么」比任何单个字段都重要：
 * 例如 `POS 0:0` 出现在 `restore done` 之前还是之后，直接区分
 * 「恢复根本没跑」和「恢复跑了但随后又被谁冲掉」。
 *
 * 定位完成后把本文件和 MemosList 里所有 ScrollTrace.record 调用一并删掉。
 */
object ScrollTrace {
    /** 屏幕上最多保留多少行。截图能放下的行数是硬约束。 */
    private const val MAX_EVENTS = 14

    private val instanceCounter = AtomicLong(0)

    /**
     * MemosList 被**全新组合**过多少次。
     *
     * 每次点开备忘如果它 +1，说明列表页的组合真的被销毁重建了；
     * 不变则说明列表一直在组合里，跳顶是别的原因。
     */
    var composeCount: Long = 0
        private set

    private val _events = mutableStateListOf<String>()

    /** 只读给界面用；写入统一走 [record]。 */
    val events: List<String> get() = _events

    /** 一个新的 MemosList 实例（= 一次全新组合）拿到一个编号。 */
    fun newInstance(): Long {
        composeCount++
        return instanceCounter.incrementAndGet()
    }

    fun record(line: String) {
        // 同时进 logcat：真机上没有 adb，但用户把 logcat 贴回来更省事。
        Timber.i("SCROLLTRACE %s", line)
        _events.add(line)
        // 超出就从最早的开始丢：最近发生的事才是要看的。
        while (_events.size > MAX_EVENTS) {
            _events.removeAt(0)
        }
    }
}

/**
 * 浮在列表上方的一小块诊断面板。
 *
 * 放在 Box 里而不是列表的 item 里 —— item 会跟着滚动，跑两下就滚出屏幕，
 * 而要看的恰恰是「点开备忘之前那一刻」和「返回之后那一刻」。
 */
@Composable
internal fun ScrollTraceOverlay(
    anchorText: String,
    revision: Int,
    modifier: Modifier = Modifier
) {
    val lines = ScrollTrace.events
    if (lines.isEmpty()) return

    Column(
        modifier = modifier
            .background(Color(0xE0000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Text(
            text = "全新组合 ${ScrollTrace.composeCount} 次 | 锚点 $anchorText | rev=$revision",
            color = Color(0xFFFFD479),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        lines.forEach { line ->
            Text(
                text = line,
                color = Color(0xFF7CFF7C),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}