package me.mudkip.moememos.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `跟随系统时透传系统当前状态`() {
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `浅色模式忽略系统状态`() {
        assertEquals(false, ThemeMode.LIGHT.isDark(systemInDarkTheme = true))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `深色模式忽略系统状态`() {
        assertEquals(true, ThemeMode.DARK.isDark(systemInDarkTheme = true))
        assertEquals(true, ThemeMode.DARK.isDark(systemInDarkTheme = false))
    }

    @Test
    fun `只有跟随系统这一档会随系统变化`() {
        val followsSystem = ThemeMode.entries.filter {
            it.isDark(true) != it.isDark(false)
        }
        assertEquals(listOf(ThemeMode.SYSTEM), followsSystem)
    }
}
