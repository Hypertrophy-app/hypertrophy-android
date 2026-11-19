package app.hypertrophy.feature.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.network.TokenProvider
import app.hypertrophy.core.data.network.createAuthenticatedHttpClient
import app.hypertrophy.core.data.squad.SquadLiveBus
import app.hypertrophy.core.data.squad.TeammateSetProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SquadDto(
    val id: String,
    val name: String,
    val inviteCode: String = "",
    val ownerId: String = "",
    val createdAt: Long = 0L,
    val role: String? = null,
)

@Serializable
data class CreateSquadRequest(val name: String)

@Serializable
data class JoinSquadRequest(val inviteCode: String)

@Serializable
data class JoinSquadResponse(val squadId: String, val name: String, val joinedAt: Long)

@Serializable
data class SquadListResponse(val squads: List<SquadDto>)

data class SquadUiState(
    val squads: List<SquadDto> = emptyList(),
    val selectedSquadId: String? = null,
    val messages: List<SquadChatMessage> = emptyList(),
    val presence: Map<String, PresenceStatus> = emptyMap(),
    val stealthMode: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SquadViewModel @Inject constructor(
    private val tokenProvider: TokenProvider,
) : ViewModel() {
    private val client = createAuthenticatedHttpClient(tokenProvider) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val _state = MutableStateFlow(SquadUiState())
    val state: StateFlow<SquadUiState> = _state.asStateFlow()

    private val baseUrl = "https://api.hypertrophy.app/api"

    private var socket: SquadWebSocketClient? = null
    private var socketJob: Job? = null

    init {
        loadSquads()
    }

    fun loadSquads() {
        viewModelScope.launch {
            runCatching {
                client.get("$baseUrl/squads").body<SquadListResponse>()
            }.onSuccess { response ->
                _state.value = _state.value.copy(squads = response.squads, error = null)
            }
            // Offline: keep whatever list we already have; Room-style cache for
            // squads lands with the squad DAO, and a silent miss beats a
            // scary error on app open.
        }
    }

    fun createSquad(name: String) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(error = "Squad name required")
            return
        }
        viewModelScope.launch {
            runCatching {
                client.post("$baseUrl/squads") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateSquadRequest(name.trim()))
                }.body<SquadDto>()
            }.onSuccess { squad ->
                _state.value = _state.value.copy(
                    squads = _state.value.squads.filterNot { it.id == squad.id } + squad,
                    error = null,
                )
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun joinByCode(code: String) {
        val normalized = InviteCode.normalize(code)
        if (!InviteCode.isValid(normalized)) {
            _state.value = _state.value.copy(error = "Invalid invite code")
            return
        }
        viewModelScope.launch {
            runCatching {
                client.post("$baseUrl/squads/join") {
                    contentType(ContentType.Application.Json)
                    setBody(JoinSquadRequest(normalized))
                }.body<JoinSquadResponse>()
            }.onSuccess { response ->
                val squad = SquadDto(
                    id = response.squadId,
                    name = response.name,
                    inviteCode = normalized,
                    createdAt = response.joinedAt,
                    role = "athlete",
                )
                _state.value = _state.value.copy(
                    squads = _state.value.squads.filterNot { it.id == squad.id } + squad,
                    error = null,
                )
                selectSquad(response.squadId)
            }.onFailure {
                _state.value = _state.value.copy(
                    error = it.message ?: "Could not join squad",
                )
            }
        }
    }

    fun selectSquad(squadId: String) {
        _state.value = _state.value.copy(
            selectedSquadId = squadId,
            messages = emptyList(),
            presence = emptyMap(),
        )
        socket?.disconnect()
        socket = SquadWebSocketClient("wss://api.hypertrophy.app", tokenProvider)
        socket?.connect(squadId)
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socket?.incoming?.collect { text -> applyEvent(SquadWireParser.parse(text)) }
        }
    }

    fun sendChat(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        socket?.chat(trimmed)
    }

    fun setPresence(status: PresenceStatus) {
        if (_state.value.stealthMode && status == PresenceStatus.WORKING_OUT) return
        socket?.presence(status)
    }

    fun toggleStealth() {
        _state.value = _state.value.copy(stealthMode = !_state.value.stealthMode)
    }

    private fun applyEvent(event: SquadEvent) {
        when (event) {
            is SquadEvent.History -> {
                _state.value = _state.value.copy(messages = event.messages)
            }

            is SquadEvent.Presence -> {
                _state.value = _state.value.copy(
                    presence = _state.value.presence + (event.userId to event.status),
                )
            }

            is SquadEvent.Chat -> {
                val current = _state.value.messages
                if (current.none { it.id == event.message.id }) {
                    _state.value = _state.value.copy(messages = current + event.message)
                }
            }

            is SquadEvent.LiveSetProgress -> {
                SquadLiveBus.publish(
                    TeammateSetProgress(
                        userId = event.userId,
                        exerciseId = event.exerciseId,
                        setIndex = event.setIndex,
                        reps = event.reps,
                        weightKg = event.weightKg,
                        isPr = event.isPr,
                        at = event.at,
                    ),
                )
            }

            SquadEvent.Unknown -> Unit
        }
    }

    override fun onCleared() {
        super.onCleared()
        socketJob?.cancel()
        socket?.disconnect()
        client.close()
    }
}
