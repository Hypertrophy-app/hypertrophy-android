package app.hypertrophy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.hypertrophy.home.HomeScreen
import app.hypertrophy.feature.workout.WorkoutRoute

private object Routes {
    const val HOME = "home"
    const val WORKOUT = "workout"
}

@Composable
fun HypertrophyApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartWorkout = { navController.navigate(Routes.WORKOUT) },
            )
        }
        composable(Routes.WORKOUT) {
            WorkoutRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
