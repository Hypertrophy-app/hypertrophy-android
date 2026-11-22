package app.hypertrophy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import app.hypertrophy.core.designsystem.theme.HypertrophyTheme
import app.hypertrophy.core.designsystem.theme.ThemePreferences
import app.hypertrophy.core.designsystem.theme.resolveDarkTheme
import app.hypertrophy.navigation.HypertrophyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val inviteCodeState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.load(applicationContext)
        inviteCodeState.value = inviteCodeFrom(intent)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = ThemePreferences.mode.resolveDarkTheme(systemDarkTheme)
            LaunchedEffect(darkTheme) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            HypertrophyTheme(darkTheme = darkTheme) {
                HypertrophyApp(initialInviteCode = inviteCodeState.value)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The activity is reused while it is alive, so the observed state must
        // be updated for the join link to reach the composition.
        inviteCodeState.value = inviteCodeFrom(intent)
    }

    companion object {
        /** Extracts the code from `hypertrophy://join/<CODE>` URIs. */
        fun inviteCodeFrom(intent: Intent?): String? {
            val data = intent?.data ?: return null
            if (data.scheme != "hypertrophy" || data.host != "join") return null
            return data.lastPathSegment?.takeIf { it.isNotBlank() }
        }
    }
}
