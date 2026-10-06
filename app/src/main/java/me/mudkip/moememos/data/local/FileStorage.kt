package me.mudkip.moememos.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import me.mudkip.moememos.data.model.ImageFormat
import me.mudkip.moememos.data.model.ImageQuality
import me.mudkip.moememos.data.model.scaledDimensions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private fun accountDir(accountKey: String): File {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(accountKey.toByteArray(Charsets.UTF_8))
        return File(context.filesDir, "resources/$encoded").also { it.mkdirs() }
    }

    fun saveFile(accountKey: String, content: ByteArray, filename: String): Uri {
        return saveFile(accountKey, filename) { output ->
            output.write(content)
        }
    }

    /**
     * 按 [quality] 缩放并重新编码后保存，供「上传图片品质」使用。
     *
     * 返回 null 表示这张图没能处理（读不出来、尺寸非法、编码失败）。**调用方必须回退到
     * 原样保存**——宁可上传得大一点，也不能因为压缩失败而丢图。
     *
     * [format] 由调用方根据 mimeType 判定（见 `compressibleImageFormat`）：只有 JPEG / PNG
     * 会走到这里，GIF 不会——重编码会丢掉动画。
     */
    fun saveCompressedImage(
        accountKey: String,
        sourceUri: Uri,
        filename: String,
        format: ImageFormat,
        quality: ImageQuality,
    ): Uri? {
        val maxDimension = quality.maxDimension ?: return null
        return try {
            // 先只读尺寸，不解码像素：原图可能有几千像素宽，直接解码很容易 OOM。
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            val (targetWidth, targetHeight) =
                scaledDimensions(bounds.outWidth, bounds.outHeight, maxDimension)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val scaled = if (decoded.width == targetWidth && decoded.height == targetHeight) {
                decoded
            } else {
                Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { result ->
                    if (result !== decoded) {
                        decoded.recycle()
                    }
                }
            }

            val bytes = ByteArrayOutputStream().use { output ->
                val compressFormat = when (format) {
                    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                }
                // PNG 是无损的，compress 的 quality 参数会被忽略——这正是想要的：
                // 截图不转格式、不糊字、不丢透明。
                scaled.compress(compressFormat, quality.jpegQuality ?: 90, output)
                output.toByteArray()
            }
            scaled.recycle()

            saveFile(accountKey, bytes, filename)
        } catch (e: Throwable) {
            // 压缩是「尽力而为」的一步，任何异常都只是回到原图，不该让上传失败。
            null
        }
    }

    /** 只做 2 的幂次下采样，剩下的零头交给 createScaledBitmap，避免一次性把原图整张解进内存。 */
    private fun sampleSizeFor(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (targetWidth > 0 && targetHeight > 0 &&
            width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight
        ) {
            sample *= 2
        }
        return sample
    }

    fun saveFile(accountKey: String, sourceUri: Uri, filename: String): Uri {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalArgumentException("Unable to open URI for reading: $sourceUri")
        inputStream.use { input ->
            return saveFile(accountKey, input, filename)
        }
    }

    fun saveFile(accountKey: String, input: InputStream, filename: String): Uri {
        return saveFile(accountKey, filename) { output ->
            input.copyTo(output)
        }
    }

    private fun saveFile(accountKey: String, filename: String, writer: (java.io.OutputStream) -> Unit): Uri {
        val file = File(accountDir(accountKey), filename)
        file.outputStream().use(writer)
        return Uri.fromFile(file)
    }

    fun deleteFile(uri: Uri) {
        uri.path?.let { path ->
            File(path).delete()
        }
    }

    fun deleteAccountFiles(accountKey: String) {
        accountDir(accountKey).deleteRecursively()
    }
}
