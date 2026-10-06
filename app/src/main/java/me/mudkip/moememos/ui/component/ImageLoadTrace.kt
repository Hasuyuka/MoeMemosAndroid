package me.mudkip.moememos.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import me.mudkip.moememos.data.model.ResourceRepresentable
import me.mudkip.moememos.util.FileTrace
import timber.log.Timber
import java.io.File

/**
 * 图片加载失败的**真实原因**暂存处（临时诊断）。
 *
 * 「加了 4 张图只显示 1 张」这件事上，只看到「有个格子是空的」是没用的——
 * 要区分的是两类完全不同的故障：
 *
 *   - 文件**不在**了（被谁删了）→ 是数据丢失，`fileState` 会显示「文件缺失」；
 *   - 文件在，但**解码/读取失败** → `ImageLoadTrace` 里会有真实的异常信息。
 *
 * 之前没有这一层，只能靠猜。定位完成后连同 [ImageDiagnosticsOverlay] 一起删掉。
 */
internal object ImageLoadTrace {
    private val errors = mutableStateMapOf<String, String>()

    fun recordError(url: String, error: Throwable) {
        val message = "${error.javaClass.simpleName}: ${error.message ?: "(无消息)"}"
        errors[url] = message
        Timber.w(error, "图片加载失败 url=%s", url)
    }

    fun errorFor(url: String): String? = errors[url]
}

/**
 * 详情页顶部的图片诊断面板（临时）。
 *
 * 每条图片附件一行：序号、文件状态/大小、真实的加载异常、文件名尾巴。
 */
@Composable
internal fun ImageDiagnosticsOverlay(
    resources: List<ResourceRepresentable>,
    modifier: Modifier = Modifier,
) {
    val images = remember(resources) {
        resources
            .filter { it.mimeType?.startsWith("image/") == true }
            .sortedWith(UPLOAD_ORDER)
    }
    if (images.isEmpty()) return

    Column(
        modifier = modifier
            .background(Color(0xE0000000), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Text(
            text = "图片附件 ${images.size} 条",
            color = Color(0xFFFFD479),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace
        )
        images.forEachIndexed { index, resource ->
            val url = resource.localUri ?: resource.uri
            val fileState = remember(url) { describeFile(url) }
            val error = ImageLoadTrace.errorFor(url)
            Text(
                text = "img$index $fileState${if (error == null) "" else " | $error"} | ${tail(resource.filename)}",
                color = if (error == null) Color(0xFF7CFF7C) else Color(0xFFFF8A80),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // 文件层的写入/删除流水。这是唯一能回答「文件是被谁删的，还是压根没写成功」
        // 的东西——只靠看代码，所有删文件的地方都同时删了数据库行，与"行还在"矛盾。
        val fileEvents = FileTrace.events
        if (fileEvents.isNotEmpty()) {
            Text(
                text = "── 文件流水（最近 ${fileEvents.size} 条）──",
                color = Color(0xFFFFD479),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 3.dp)
            )
            fileEvents.forEach { line ->
                Text(
                    text = line,
                    color = Color(0xFF9FD4FF),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/** 文件到底在不在、有多大——这是区分「被删了」和「读不出来」的唯一直接证据。 */
private fun describeFile(url: String): String {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return "url 解析失败"
    if (uri.scheme != "file") {
        return "${uri.scheme}:// ${uri.host ?: ""}"
    }
    val path = uri.path ?: return "无 path"
    val file = File(path)
    return if (file.exists()) "${file.length() / 1024}KB" else "文件缺失"
}

private fun tail(name: String): String =
    if (name.length <= 20) name else "…" + name.takeLast(19)
