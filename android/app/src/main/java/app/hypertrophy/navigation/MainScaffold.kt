package app.hypertrophy.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
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

data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MainScaffold(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    onQuickWorkout: () -> Unit,
    onStartScheduled: () -> Unit,
    onHostJoinSquad: () -> Unit,
    showBottomBar: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalPhoenixColors.current
    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomAppBar(
                    containerColor = colors.surface,
                    actions = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tabs = listOf(
                                TabItem("today", "Today", Icons.Filled.Home),
                                TabItem("workouts", "Workouts", Icons.Filled.List),
                            )
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { onTabSelected(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                            // central spacer for the start FAB
                            Box(modifier = Modifier.size(72.dp))
                            val tabs2 = listOf(
                                TabItem("squad", "Squad", Icons.Filled.Star),
                                TabItem("you", "You", Icons.Filled.Person),
                            )
                            tabs2.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentRoute == tab.route,
                                    onClick = { onTabSelected(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = colors.crimsonPrimary,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Start",
                        tint = colors.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content()
            if (showBottomBar && fabExpanded) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 96.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PhoenixIgnitionButton(
                        text = "Start Scheduled",
                        onClick = { fabExpanded = false; onStartScheduled() },
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
