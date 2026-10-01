package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 上传图片的品质。
 *
 * 只对 **JPEG / PNG** 生效，并且**跳过 GIF**——GIF 重编码会丢掉动画，
 * 那是用户特意上传它的原因。
 *
 * PNG 是无损格式，[jpegQuality] 对它无效，只会按 [maxDimension] 缩放。
 * 这一点是刻意的：PNG 多半是截图或带透明的图，转成 JPEG 会糊掉文字、丢掉透明。
 */
@Serializable
enum class ImageQuality(val maxDimension: Int?, val jpegQuality: Int?) {
    /** 原图，不做任何处理（默认，与引入该设置之前的行为一致）。 */
    ORIGINAL(null, null),

    /** 长边不超过 2048。 */
    HIGH(2048, 90),

    /** 长边不超过 1440。 */
    MEDIUM(1440, 80),

    /** 长边不超过 1080。 */
    LOW(1080, 70),
    ;

    val enabled: Boolean get() = maxDimension != null
}

/**
 * 按长边限制等比缩放后的目标尺寸。
 *
 * 纯函数：不碰 Bitmap，也不碰 Android，所以能被单元测试覆盖——
 * 尺寸算错是那种"看起来也能用"的错误，只会让图片被拉变形或白白多占空间。
 */
fun scaledDimensions(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) {
        return width to height
    }
    val longest = max(width, height)
    if (longest <= maxDimension) {
        return width to height
    }
    val ratio = maxDimension.toDouble() / longest
    return max(1, (width * ratio).roundToInt()) to max(1, (height * ratio).roundToInt())
}

/**
 * 这个 mimeType 是否应该压缩；返回 null 表示原样上传。
 *
 * 拿不准的格式一律不碰——猜错格式的后果（上传失败、或把动图压成静图）比省下的空间严重得多。
 */
fun compressibleImageFormat(mimeType: String?): ImageFormat? {
    val normalized = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
    return when (normalized) {
        "image/jpeg", "image/jpg" -> ImageFormat.JPEG
        "image/png" -> ImageFormat.PNG
        else -> null
    }
}

enum class ImageFormat { JPEG, PNG }
