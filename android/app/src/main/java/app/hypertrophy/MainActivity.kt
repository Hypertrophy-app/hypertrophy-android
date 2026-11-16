package app.hypertrophy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.hypertrophy.core.designsystem.theme.HypertrophyTheme
import app.hypertrophy.navigation.HypertrophyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HypertrophyTheme {
                HypertrophyApp()
            }
        }
    }
}
