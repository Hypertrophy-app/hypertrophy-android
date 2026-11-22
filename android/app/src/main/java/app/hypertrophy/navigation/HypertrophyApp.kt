package app.hypertrophy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.hypertrophy.feature.coach.CoachRoute
import app.hypertrophy.feature.profile.ProfileRoute
import app.hypertrophy.feature.squad.SquadRoute
import app.hypertrophy.feature.today.TodayRoute
import app.hypertrophy.auth.AuthGateScreen
import app.hypertrophy.core.designsystem.component.FireTransitionContainer
import app.hypertrophy.feature.workout.WorkoutRoute
import app.hypertrophy.home.HomeScreen

@Composable
fun HypertrophyApp(initialInviteCode: String? = null) {
    val navController: NavHostController = rememberNavController()
    var currentTab by remember { mutableStateOf("today") }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val showChrome = backStackEntry?.destination?.route?.startsWith("coach") != true

    // hypertrophy://join/<CODE> deep links land here from MainActivity.
    LaunchedEffect(initialInviteCode) {
        if (!initialInviteCode.isNullOrBlank()) {
            currentTab = "squad"
            navController.navigate("join/${initialInviteCode.uppercase()}") {
                launchSingleTop = true
            }
        }
    }

    FireTransitionContainer(transitionKey = backStackEntry?.destination?.route ?: "today") {
        MainScaffold(
            currentRoute = currentTab,
            showBottomBar = showChrome,
            onTabSelected = { route ->
                currentTab = route
                when (route) {
                    "today" -> navController.navigate("today") { launchSingleTop = true }
                    "workouts" -> navController.navigate("builder") { launchSingleTop = true }
                    "squad" -> navController.navigate("squad") { launchSingleTop = true }
                    "you" -> navController.navigate("profile") { launchSingleTop = true }
                }
            },
            onQuickWorkout = { navController.navigate("coach") { launchSingleTop = true } },
            onStartScheduled = { navController.navigate("coach") { launchSingleTop = true } },
            onHostJoinSquad = { navController.navigate("squad") { launchSingleTop = true } },
        ) {
        NavHost(navController = navController, startDestination = "today") {
            composable("today") {
                TodayRoute(onOpenWorkout = { navController.navigate("workout") })
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
            composable("squad") {
                SquadRoute()
            }
            composable("profile") {
                ProfileRoute(onOpenAuth = { navController.navigate("auth") })
            }
            composable("auth") {
                val enterHome: () -> Unit = {
                    navController.navigate("home") {
                        launchSingleTop = true
                        popUpTo("auth") { inclusive = true }
                    }
                }
                AuthGateScreen(
                    onGoogleSignIn = enterHome,
                    onPasskeySignIn = enterHome,
                    onEmailSignIn = { _, _ -> enterHome() },
                )
            }
            composable("home") {
                HomeScreen(onStartWorkout = { navController.navigate("coach") })
            }
            composable(
                route = "join/{code}",
                arguments = listOf(navArgument("code") { type = NavType.StringType }),
            ) { entry ->
                val code = entry.arguments?.getString("code").orEmpty()
                SquadRoute(initialInviteCode = code)
            }
        }
        }
    }
}
