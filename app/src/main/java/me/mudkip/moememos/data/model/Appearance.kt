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
