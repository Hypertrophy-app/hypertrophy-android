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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.designsystem.theme.PhoenixMonoTextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SquadRoute(
    initialInviteCode: String? = null,
    viewModel: SquadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialInviteCode) {
        if (!initialInviteCode.isNullOrBlank()) {
            viewModel.joinByCode(initialInviteCode)
        }
    }
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
    var chatDraft by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

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
        TextButton(onClick = { viewModel.toggleStealth() }) {
            Text("Toggle stealth", color = colors.solarGold)
        }

        state.error?.let { Text(text = it, color = colors.formErrorCrimson) }

        state.squads.forEach { squad ->
            val selected = squad.id == state.selectedSquadId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) colors.elevated else colors.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = squad.name, color = colors.textPrimary)
                    if (squad.inviteCode.isNotBlank()) {
                        Text(
                            text = "Code ${squad.inviteCode}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                TextButton(onClick = { viewModel.selectSquad(squad.id) }) {
                    Text(
                        text = if (selected) "Open" else "Connect",
                        color = colors.solarGold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Live status",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        if (state.selectedSquadId == null) {
            Text(
                text = "Connect to a squad to see who is training.",
                color = colors.textSecondary,
            )
        }
        state.presence.forEach { (userId, status) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pillColor = when (status) {
                    PresenceStatus.WORKING_OUT -> Color(0xFFD91E36)
                    PresenceStatus.RESTING -> Color(0xFF6B6B75)
                    PresenceStatus.IDLE -> Color(0xFFB8A23C)
                    PresenceStatus.OFFLINE -> Color(0xFF3A3A42)
                }
                Text(
                    text = "",
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .background(pillColor),
                )
                Text(
                    text = "${presenceLabel(SquadMemberStatus(userId, status))} ($userId)",
                    color = colors.textSecondary,
                )
            }
        }

        if (state.selectedSquadId != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Group chat",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            state.messages.forEach { message ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (message.isSystem) {
                            "SYSTEM"
                        } else {
                            "${message.userId}  ${timeFormat.format(Date(message.createdAtMillis))}"
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        text = message.body,
                        color = if (message.isSystem) colors.solarGold else colors.textPrimary,
                    )
                }
            }
            if (state.messages.isEmpty()) {
                Text(text = "No messages yet.", color = colors.textSecondary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = chatDraft,
                    onValueChange = { chatDraft = it },
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        viewModel.sendChat(chatDraft)
                        chatDraft = ""
                    },
                ) { Text("Send") }
            }
        }
    }
}
