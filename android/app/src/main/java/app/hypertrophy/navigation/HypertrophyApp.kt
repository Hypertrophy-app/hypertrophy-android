package app.hypertrophy.navigation

import android.net.Uri
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
import app.hypertrophy.auth.AuthGateRoute
import app.hypertrophy.auth.SessionStore
import app.hypertrophy.core.designsystem.component.FireTransitionContainer
import app.hypertrophy.feature.coach.CoachRoute
import app.hypertrophy.feature.profile.ProfileRoute
import app.hypertrophy.feature.squad.SquadRoute
import app.hypertrophy.feature.today.DashboardCollapseSignal
import app.hypertrophy.feature.today.TodayRoute
import app.hypertrophy.feature.workout.ui.WorkoutBuilderRoute
import app.hypertrophy.home.HomeScreen

@Composable
fun HypertrophyApp(initialInviteCode: String? = null) {
    val session = SessionStore.session

    // No active Clerk session means no dashboard, workout or squad screen: the
    // gate is the first surface the app shows.
    if (session == null) {
        AuthGateRoute()
        return
    }

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

    // Every entry into the coach passes an explicit workout id. A blank id is
    // a quick workout; the today sentinel resolves the first unfinished
    // scheduled session; any other id opens that planned session.
    val openCoach: (String) -> Unit = { workoutId ->
        if (workoutId.isBlank()) {
            navController.navigate("coach") { launchSingleTop = true }
        } else {
            navController.navigate("coach?workoutId=${Uri.encode(workoutId)}") {
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
            onQuickWorkout = {
                // Starting a quick workout leaves the dashboard collapsed, so
                // Today comes back compact instead of four full cards.
                DashboardCollapseSignal.requestCollapse()
                openCoach("")
            },
            onStartScheduled = openCoach,
            onHostJoinSquad = { navController.navigate("squad") { launchSingleTop = true } },
        ) {
        NavHost(navController = navController, startDestination = "today") {
            composable("today") {
                TodayRoute(
                    onOpenQuickWorkout = {
                        DashboardCollapseSignal.requestCollapse()
                        openCoach("")
                    },
                    onStartWorkout = openCoach,
                )
            }
            composable("builder") {
                WorkoutBuilderRoute(onStartWorkout = openCoach)
            }
            composable(
                route = "coach?workoutId={workoutId}",
                arguments = listOf(
                    navArgument("workoutId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                CoachRoute(
                    workoutId = entry.arguments?.getString("workoutId"),
                    onBack = { navController.popBackStack() },
                )
            }
            composable("squad") {
                SquadRoute()
            }
            composable("profile") {
                ProfileRoute(
                    accountEmail = session.email,
                    onSignOut = { SessionStore.signOut() },
                )
            }
            composable("home") {
                HomeScreen(onStartWorkout = { openCoach("") })
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
