package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityTest {

    @Test
    fun `默认不压缩`() {
        // 用户不动这个设置就看不到任何变化。
        assertEquals(ImageQuality.ORIGINAL, Settings().imageQuality)
        assertFalse(ImageQuality.ORIGINAL.enabled)
    }

    @Test
    fun `档位越高长边越大`() {
        val dims = ImageQuality.entries.filter { it.enabled }.map { it.maxDimension!! }
        assertEquals(dims.sortedDescending(), dims)
    }

    // ---------- 尺寸计算 ----------

    @Test
    fun `横图按长边缩放且保持比例`() {
        assertEquals(1000 to 500, scaledDimensions(4000, 2000, 1000))
    }

    @Test
    fun `竖图也按长边缩放`() {
        // 竖图的长边是高度——按宽度算会把竖图压得比预期小得多。
        assertEquals(500 to 1000, scaledDimensions(2000, 4000, 1000))
    }

    @Test
    fun `本来就够小的图不动`() {
        assertEquals(800 to 600, scaledDimensions(800, 600, 2048))
    }

    @Test
    fun `长边恰好等于上限时不动`() {
        assertEquals(2048 to 1000, scaledDimensions(2048, 1000, 2048))
    }

    @Test
    fun `极端长条不会被压成 0`() {
        val (w, h) = scaledDimensions(10000, 3, 1000)
        assertEquals(1000, w)
        assertTrue("高度至少要留 1 像素，否则解码会失败", h >= 1)
    }

    @Test
    fun `非法尺寸原样返回`() {
        assertEquals(0 to 0, scaledDimensions(0, 0, 1000))
    }

    // ---------- 格式判定 ----------

    @Test
    fun `JPEG 与 PNG 可以压缩`() {
        assertEquals(ImageFormat.JPEG, compressibleImageFormat("image/jpeg"))
        assertEquals(ImageFormat.PNG, compressibleImageFormat("image/png"))
        assertEquals(ImageFormat.JPEG, compressibleImageFormat("image/jpeg; charset=binary"))
        assertEquals(ImageFormat.PNG, compressibleImageFormat("IMAGE/PNG"))
    }

    @Test
    fun `GIF 不压缩——重编码会丢掉动画`() {
        assertNull(compressibleImageFormat("image/gif"))
    }

    @Test
    fun `拿不准的格式一律不碰`() {
        assertNull(compressibleImageFormat(null))
        assertNull(compressibleImageFormat("application/pdf"))
        assertNull(compressibleImageFormat("image/heic"))
        assertNull(compressibleImageFormat("image/webp"))
        assertNull(compressibleImageFormat(""))
    }
}
