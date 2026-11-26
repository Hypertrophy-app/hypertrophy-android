package app.hypertrophy.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.feature.coach.CoachViewModel

data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    onQuickWorkout: () -> Unit,
    onStartScheduled: (String) -> Unit,
    onHostJoinSquad: () -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    var fabExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = showBottomBar && fabExpanded) {
        fabExpanded = false
    }

    val selectTab: (String) -> Unit = { route ->
        fabExpanded = false
        onTabSelected(route)
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomAppBar(
                    containerColor = colors.surface,
                    actions = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tabs = listOf(
                                TabItem("today", "Today", Icons.Filled.Home),
                                TabItem("workouts", "Workouts", Icons.AutoMirrored.Filled.List),
                            )
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { selectTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            // The start button lives in the middle slot instead of
                            // leaving the centre of the bottom bar empty.
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                FloatingActionButton(
                                    onClick = { fabExpanded = !fabExpanded },
                                    containerColor = colors.crimsonPrimary,
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Start",
                                        tint = colors.onCrimson,
                                    )
                                }
                            }
                            val tabs2 = listOf(
                                TabItem("squad", "Squad", Icons.Filled.Star),
                                TabItem("you", "You", Icons.Filled.Person),
                            )
                            tabs2.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { selectTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content()
            if (showBottomBar && fabExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClickLabel = "Dismiss start menu") {
                            fabExpanded = false
                        },
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhoenixIgnitionButton(
                        text = "Start Scheduled",
                        onClick = {
                            fabExpanded = false
                            // The sentinel resolves the first incomplete
                            // non-rest session for the current day.
                            onStartScheduled(CoachViewModel.TODAY_SENTINEL)
                        },
                    )
                    PhoenixIgnitionButton(
                        text = "Quick Workout",
                        onClick = { fabExpanded = false; onQuickWorkout() },
                    )
                    PhoenixIgnitionButton(
                        text = "Host/Join Squad Session",
                        onClick = { fabExpanded = false; onHostJoinSquad() },
                    )
                }
            }
        }
    }
}
