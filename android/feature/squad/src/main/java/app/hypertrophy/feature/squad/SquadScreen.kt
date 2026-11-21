package app.hypertrophy.feature.squad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hypertrophy.core.data.catalog.ExerciseCatalog
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

    SquadScreen(
        state = state,
        onCreateSquad = viewModel::createSquad,
        onJoinByCode = viewModel::joinByCode,
        onSelectSquad = viewModel::selectSquad,
        onSendChat = viewModel::sendChat,
        onToggleStealth = viewModel::toggleStealth,
        onCreateHostSession = viewModel::createHostSession,
        onStartHostSession = viewModel::startHostSession,
    )
}

@Composable
fun SquadScreen(
    state: SquadUiState,
    onCreateSquad: (String) -> Unit,
    onJoinByCode: (String) -> Unit,
    onSelectSquad: (String) -> Unit,
    onSendChat: (String) -> Unit,
    onToggleStealth: () -> Unit,
    onCreateHostSession: (Int) -> Unit,
    onStartHostSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var squadName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var chatDraft by remember { mutableStateOf("") }
    var restDraft by remember { mutableStateOf("90") }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "SQUADS",
            style = PhoenixMonoTextStyle.copy(fontSize = 28.sp, lineHeight = 30.sp),
            color = colors.textPrimary,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CREATE OR JOIN",
                style = PhoenixMonoTextStyle.copy(fontSize = 11.sp, lineHeight = 13.sp),
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = squadName,
                    onValueChange = { squadName = it },
                    label = { Text("Squad name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                GoldButton(
                    label = "Create",
                    onClick = { onCreateSquad(squadName) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase(Locale.US) },
                    label = { Text("Invite code") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                GoldButton(
                    label = "Join",
                    onClick = { onJoinByCode(inviteCode) },
                )
            }
        }

        state.error?.let { message ->
            Text(text = message, color = colors.formErrorCrimson)
        }

        if (state.squads.isEmpty()) {
            Text(
                text = "No squads yet. Create one or join with a six-character invite code.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        state.squads.forEach { squad ->
            val selected = squad.id == state.selectedSquadId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (selected) colors.elevated else colors.surface,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = squad.name, color = colors.textPrimary)
                    if (squad.inviteCode.isNotBlank()) {
                        Text(
                            text = "Code ${squad.inviteCode}" +
                                if (squad.role != null) "  |  ${squad.role}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                TextButton(onClick = { onSelectSquad(squad.id) }) {
                    Text(
                        text = if (selected) "Open" else "Connect",
                        color = colors.solarGold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        if (state.selectedSquadId != null) {
            SectionTitle("Workout together")
            HostLobbyCard(
                session = state.hostSession,
                restDraft = restDraft,
                onRestDraftChange = { restDraft = it.filter(Char::isDigit).take(3) },
                onCreateLobby = {
                    onCreateHostSession(restDraft.toIntOrNull() ?: 90)
                },
                onStartSession = onStartHostSession,
                timeFormat = timeFormat,
            )
        }

        SectionTitle("Live status")
        if (state.selectedSquadId == null) {
            Text(
                text = "Connect to a squad to see who is training.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        } else if (state.presence.isEmpty()) {
            Text(
                text = "Waiting for teammates to come online.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        state.presence.forEach { (userId, status) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val pillColor = when (status) {
                    PresenceStatus.WORKING_OUT -> colors.crimsonPrimary
                    PresenceStatus.RESTING -> colors.emberOrange
                    PresenceStatus.IDLE -> colors.solarGold
                    PresenceStatus.OFFLINE -> colors.textSecondary.copy(alpha = 0.4f)
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(pillColor, CircleShape),
                )
                Text(
                    text = "${presenceLabel(SquadMemberStatus(userId, status))} ($userId)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Stealth mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Hide working-out presence from the squad",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            Switch(checked = state.stealthMode, onCheckedChange = { onToggleStealth() })
        }

        if (state.selectedSquadId != null) {
            SectionTitle("Group chat")
            if (state.messages.isEmpty()) {
                Text(
                    text = "No messages yet. Say hello to your squad.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
            state.messages.forEach { message ->
                ChatBubble(message = message, timeFormat = timeFormat)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = chatDraft,
                    onValueChange = { chatDraft = it },
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                )
                GoldButton(
                    label = "Send",
                    onClick = {
                        onSendChat(chatDraft)
                        chatDraft = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun HostLobbyCard(
    session: HostSessionUi?,
    restDraft: String,
    onRestDraftChange: (String) -> Unit,
    onCreateLobby: () -> Unit,
    onStartSession: () -> Unit,
    timeFormat: SimpleDateFormat,
) {
    val colors = LocalPhoenixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (session == null) {
            Text(
                text = "Open a full-body lobby. Every teammate in this room sees the queue and rest timer.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = restDraft,
                    onValueChange = onRestDraftChange,
                    label = { Text("Rest seconds") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                GoldButton(label = "Create lobby", onClick = onCreateLobby)
            }
            return
        }

        Text(
            text = if (session.isHost) "YOUR LOBBY" else "HOSTED BY ${session.hostUserId.ifBlank { "TEAMMATE" }}",
            style = PhoenixMonoTextStyle.copy(fontSize = 11.sp, lineHeight = 13.sp),
            color = if (session.isHost) colors.solarGold else colors.textSecondary,
        )
        session.queue.forEach { exercise ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = exerciseLabel(exercise.exerciseId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${exercise.sets} x ${exercise.reps}",
                    style = PhoenixMonoTextStyle.copy(fontSize = 13.sp, lineHeight = 15.sp),
                    color = colors.textSecondary,
                )
            }
        }
        Text(
            text = "Rest ${session.restSeconds}s between sets",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
        when {
            session.startedAtMillis != null -> Text(
                text = "Session started at ${timeFormat.format(Date(session.startedAtMillis))}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.solarGold,
            )

            session.isHost -> GoldButton(label = "Start together", onClick = onStartSession)

            else -> Text(
                text = "Waiting for the host to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ChatBubble(message: SquadChatMessage, timeFormat: SimpleDateFormat) {
    val colors = LocalPhoenixColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (message.isSystem) colors.elevated else colors.surface,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (message.isSystem) {
                "SYSTEM"
            } else {
                "${message.userId}  ${timeFormat.format(Date(message.createdAtMillis))}"
            },
            style = PhoenixMonoTextStyle.copy(fontSize = 9.sp, lineHeight = 11.sp),
            color = if (message.isSystem) colors.solarGold else colors.textSecondary,
        )
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isSystem) colors.solarGold else colors.textPrimary,
        )
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit) {
    val colors = LocalPhoenixColors.current
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.solarGold,
            contentColor = colors.canvas,
        ),
    ) {
        Text(label)
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalPhoenixColors.current
    Text(
        text = text.uppercase(Locale.US),
        style = PhoenixMonoTextStyle.copy(fontSize = 12.sp, lineHeight = 14.sp),
        color = colors.textSecondary,
    )
}

private fun exerciseLabel(exerciseId: String): String =
    ExerciseCatalog.exercises.firstOrNull { it.id == exerciseId }?.name
        ?: exerciseId.removePrefix("ex_").replace('_', ' ')
