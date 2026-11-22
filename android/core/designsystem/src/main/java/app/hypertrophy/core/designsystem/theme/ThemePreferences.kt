package app.hypertrophy.core.designsystem.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How the app chooses light or dark colours. */
enum class ThemeMode {
    /** Follow the device setting. */
    SYSTEM,

    /** Always use the light palette. */
    LIGHT,

    /** Always use the dark palette. */
    DARK,
}

/** Resolves a mode into the boolean the Material theme needs. */
fun ThemeMode.resolveDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Small app-wide appearance preference.
 *
 * The app is dark by default: it never silently flips to light just because
 * the device changed theme. Users can explicitly pick System, Light or Dark
 * from the profile screen. State is a Compose snapshot so changing the mode
 * recomposes the root theme immediately without an activity restart.
 */
object ThemePreferences {
    private const val PREFS_NAME = "hypertrophy_appearance"
    private const val KEY_THEME_MODE = "theme_mode"

    private var storedMode by mutableStateOf(ThemeMode.DARK)

    val mode: ThemeMode get() = storedMode

    /** Loads the persisted choice once, on activity creation. */
    fun load(context: Context) {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.DARK.name)
        storedMode = runCatching { ThemeMode.valueOf(stored ?: ThemeMode.DARK.name) }
            .getOrDefault(ThemeMode.DARK)
    }

    /** Persists and applies a new choice. */
    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.name)
            .apply()
        storedMode = mode
    }
}
