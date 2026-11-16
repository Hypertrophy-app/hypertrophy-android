package app.hypertrophy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.hypertrophy.feature.workout.WorkoutRoute
import app.hypertrophy.home.HomeScreen

@Composable
fun HypertrophyApp() {
    val navController: NavHostController = rememberNavController()
    var currentTab by remember { mutableStateOf("today") }

    MainScaffold(
        currentRoute = currentTab,
        onTabSelected = { route ->
            currentTab = route
            when (route) {
                "workouts" -> navController.navigate("workout") { launchSingleTop = true }
            }
        },
        onQuickWorkout = { navController.navigate("workout") },
        onStartScheduled = { navController.navigate("workout") },
        onHostJoinSquad = { navController.navigate("workout") },
    ) {
        NavHost(navController = navController, startDestination = "today") {
            composable("today") {
                HomeScreen(onStartWorkout = { navController.navigate("workout") })
            }
            composable("workout") {
                WorkoutRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
