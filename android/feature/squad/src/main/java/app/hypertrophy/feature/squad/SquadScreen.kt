package app.hypertrophy.feature.squad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle

@Composable
fun SquadRoute(viewModel: SquadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SquadScreen(state = state, viewModel = viewModel)
}

@Composable
fun SquadScreen(
    state: SquadUiState,
    viewModel: SquadViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var squadName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "SQUADS",
            style = PhoenixMonoTextStyle.copy(fontSize = 28.sp, lineHeight = 30.sp),
            color = colors.textPrimary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = squadName,
                onValueChange = { squadName = it },
                label = { Text("Squad name") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.createSquad(squadName) }) { Text("Create") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inviteCode,
                onValueChange = { inviteCode = it },
                label = { Text("Invite code") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.joinByCode(inviteCode) }) { Text("Join") }
        }

        Text(
            text = if (state.stealthMode) "Stealth mode on" else "Stealth mode off",
            color = colors.textSecondary,
        )
        TextButton(onClick = { viewModel.toggleStealth() }) { Text("Toggle stealth", color = colors.solarGold) }

        state.error?.let { Text(text = it, color = colors.formErrorCrimson) }

        state.squads.forEach { squad ->
            Text(
                text = "${squad.name} (${squad.inviteCode})",
                color = colors.textPrimary,
            )
            TextButton(onClick = { viewModel.selectSquad(squad.id, "local-user") }) {
                Text("Open", color = colors.solarGold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Live status",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        state.presence.forEach { (userId, status) ->
            Text(text = "$userId: ${presenceLabel(SquadMemberStatus(userId, status))}", color = colors.textSecondary)
        }
    }
}
