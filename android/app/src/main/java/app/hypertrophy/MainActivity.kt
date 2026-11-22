package app.hypertrophy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import app.hypertrophy.core.designsystem.theme.HypertrophyTheme
import app.hypertrophy.core.designsystem.theme.ThemePreferences
import app.hypertrophy.core.designsystem.theme.resolveDarkTheme
import app.hypertrophy.navigation.HypertrophyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePreferences.load(applicationContext)
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = ThemePreferences.mode.resolveDarkTheme(systemDarkTheme)
            HypertrophyTheme(darkTheme = darkTheme) {
                HypertrophyApp(initialInviteCode = inviteCodeFrom(intent))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity is recreated via composition when the caller re-enters with
        // a new deep link; reading the latest intent keeps join links working
        // without a navigation reset.
        setIntent(intent)
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
