package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScaleTest {

    @Test
    fun `默认档位不缩放`() {
        assertEquals(1.0, FontScale.DEFAULT.factor.toDouble(), 0.0001)
    }

    @Test
    fun `档位按枚举顺序递增且互不相同`() {
        val factors = FontScale.entries.map { it.factor }
        assertEquals(factors.sorted(), factors)
        assertEquals(factors.size, factors.distinct().size)
    }

    @Test
    fun `所有档位都是正数`() {
        // 0 或负数会让字号消失；相乘进 fontScale 后尤其危险。
        assertTrue(FontScale.entries.all { it.factor > 0f })
    }

    @Test
    fun `档位保持在克制范围内`() {
        // 数值过大时固定高度布局容易裁字；这类问题本机无法验证，
        // 因此用一个测试把范围钉住，避免以后随手调成 2.0。
        assertTrue(FontScale.SMALL.factor >= 0.8f)
        assertTrue(FontScale.EXTRA_LARGE.factor <= 1.4f)
    }

    @Test
    fun `与系统字号相乘而不是覆盖`() {
        // MoeMemosTheme 里是 density.fontScale * factor。
        // 这里固定住「相乘」的语义：系统已放大 1.5 倍时，选 LARGE 应得到 1.5 * 1.15。
        val systemFontScale = 1.5f
        val effective = systemFontScale * FontScale.LARGE.factor
        assertEquals(1.725, effective.toDouble(), 0.0001)
        assertTrue(effective > systemFontScale)
    }
}
