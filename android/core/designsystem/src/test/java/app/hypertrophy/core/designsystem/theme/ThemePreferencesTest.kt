package app.hypertrophy.core.designsystem.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferencesTest {
    @Test
    fun darkModeNeverFollowsTheDevice() {
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemDarkTheme = false))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(systemDarkTheme = true))
    }

    @Test
    fun lightModeNeverFollowsTheDevice() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemDarkTheme = false))
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(systemDarkTheme = true))
    }

    @Test
    fun systemModeStillFollowsTheDevice() {
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(systemDarkTheme = true))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(systemDarkTheme = false))
    }
}
