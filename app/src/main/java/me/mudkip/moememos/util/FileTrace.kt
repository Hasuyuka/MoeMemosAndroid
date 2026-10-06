package me.mudkip.moememos.util

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地附件文件的**写入 / 删除流水**（临时诊断）。
 *
 * 起因：真机上出现「新建备忘、5 张图里 2 张的文件不见了」——
 * 数据库行还在、`localUri` 也还是那条正确的 `file://` 路径，但文件本身 ENOENT。
 * 也就是说**文件被写过、后来又被删了**，而代码里所有删文件的地方都同时删数据库行，
 * 与「行还在」矛盾。
 *
 * 光看代码推不出来，所以把文件的每一次写和删都记下来（带时间戳和资源 id），
 * 复现一次就能直接看到：到底是谁删的，还是压根没写成功。
 *
 * 定位完成后连同 [me.mudkip.moememos.ui.component.ImageDiagnosticsOverlay] 里
 * 展示它的那几行一并删除。
 */
object FileTrace {
    private const val MAX_EVENTS = 24

    private val _events = mutableStateListOf<String>()
    val events: List<String> get() = _events

    fun record(line: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        _events.add("$stamp $line")
        while (_events.size > MAX_EVENTS) {
            _events.removeAt(0)
        }
    }
}
