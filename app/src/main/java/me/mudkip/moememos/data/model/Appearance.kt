package me.mudkip.moememos.data.model

import kotlinx.serialization.Serializable

/**
 * 明暗模式偏好。
 *
 * 默认 [SYSTEM]，与引入本设置之前的行为一致——此前主题完全跟随系统，
 * 用户没有任何可选项（`ui/theme/Theme.kt` 里写死 `isSystemInDarkTheme()`）。
 */
@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /**
     * 解析「当前是否应当使用深色」。
     *
     * 这是一个纯函数，与 Android 无关，因此可以直接单元测试；
     * 系统当前是否处于深色由调用方传入（UI 层用 `isSystemInDarkTheme()`）。
     * 把判断放在这里而不是散在 UI 里，是为了让「跟随系统」这条分支也能被测试覆盖。
     */
    fun isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }
}

/**
 * 字号档位。
 *
 * [factor] 是相对于**系统字号**的倍数。实现上把它乘进 Compose 的 `fontScale`
 * （见 `MoeMemosTheme`），因此与系统字号设置是**相乘**关系而不是覆盖：
 * 用户已经在系统里调大过字号时，应用内的档位会在此基础上继续放大。
 *
 * 数值刻意保持克制（0.85 ~ 1.3）：档位再大时，卡片、工具栏这类高度固定的布局
 * 容易把文字裁掉，而这类问题在本机无法验证，需要真机逐页确认。
 */
@Serializable
enum class FontScale(val factor: Float) {
    SMALL(0.85f),
    DEFAULT(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

/**
 * 灵感页的布局。
 *
 * 三档的取舍是「一眼能看多少条」与「要不要图片预览」：
 * 三列档刻意不显示附件图片——列窄了图片基本看不清，却照样要下载和解码，
 * 反而让滚动变卡。
 */
@Serializable
enum class ExploreLayout {
    /** 现在的大卡片，一条一行，带图片。 */
    LARGE,

    /** 两列，保留图片预览。 */
    TWO_COLUMN,

    /** 三列，不显示图片，只有摘要。 */
    THREE_COLUMN,
    ;

    /** 点一次换下一档；顺序即按钮的循环顺序。 */
    fun next(): ExploreLayout = entries[(ordinal + 1) % entries.size]
}
