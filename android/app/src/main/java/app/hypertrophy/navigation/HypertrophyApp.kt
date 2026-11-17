package app.hypertrophy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.hypertrophy.feature.coach.CoachRoute
import app.hypertrophy.feature.workout.WorkoutRoute
import app.hypertrophy.home.HomeScreen

@Composable
fun HypertrophyApp() {
    val navController: NavHostController = rememberNavController()
    var currentTab by remember { mutableStateOf("today") }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showChrome = backStackEntry?.destination?.route != "coach"

    MainScaffold(
        currentRoute = currentTab,
        showBottomBar = showChrome,
        onTabSelected = { route ->
            currentTab = route
            when (route) {
                "workouts" -> navController.navigate("builder") { launchSingleTop = true }
            }
        },
        onQuickWorkout = { navController.navigate("coach") { launchSingleTop = true } },
        onStartScheduled = { navController.navigate("coach") { launchSingleTop = true } },
        onHostJoinSquad = { navController.navigate("workout") { launchSingleTop = true } },
    ) {
        NavHost(navController = navController, startDestination = "today") {
            composable("today") {
                HomeScreen(
                    onStartWorkout = {
                        navController.navigate("coach") { launchSingleTop = true }
                    },
                )
            }
            composable("workout") {
                WorkoutRoute(onBack = { navController.popBackStack() })
            }
            composable("builder") {
                app.hypertrophy.feature.workout.ui.WorkoutBuilderRoute()
            }
            composable("coach") {
                CoachRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
