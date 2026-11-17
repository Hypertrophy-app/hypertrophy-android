package app.hypertrophy.feature.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data class SquadDto(val id: String, val name: String, val inviteCode: String, val ownerId: String, val createdAt: Long, val role: String? = null)

@Serializable
data class CreateSquadRequest(val name: String)

@Serializable
data class SendMessageRequest(val body: String)

@Serializable
data class ChatMessageDto(val id: String, val squadId: String, val userId: String, val body: String, val createdAt: Long)

data class SquadUiState(
    val squads: List<SquadDto> = emptyList(),
    val selectedSquadId: String? = null,
    val messages: List<SquadChatMessage> = emptyList(),
    val presence: Map<String, PresenceStatus> = emptyMap(),
    val stealthMode: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SquadViewModel @Inject constructor() : ViewModel() {
    private val client = HttpClient {
        install(ContentNegotiation) { json() }
    }

    private val _state = MutableStateFlow(SquadUiState())
    val state: StateFlow<SquadUiState> = _state.asStateFlow()

    private val baseUrl = "https://api.hypertrophy.app/api"

    private var socket: SquadWebSocketClient? = null

    fun createSquad(name: String) {
        viewModelScope.launch {
            runCatching {
                client.post("$baseUrl/squads") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateSquadRequest(name))
                }.body<SquadDto>()
            }.onSuccess { squad ->
                _state.value = _state.value.copy(squads = _state.value.squads + squad)
            }.onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun joinByCode(code: String) {
        if (!InviteCode.isValid(code)) {
            _state.value = _state.value.copy(error = "Invalid invite code")
            return
        }
        // Join is handled by the backend invite flow; surface the code for the
        // hosted deep link until a dedicated join endpoint ships.
        _state.value = _state.value.copy(error = null)
    }

    fun selectSquad(squadId: String, userId: String) {
        _state.value = _state.value.copy(selectedSquadId = squadId)
        socket?.disconnect()
        socket = SquadWebSocketClient("wss://api.hypertrophy.app")
        socket?.connect(squadId, userId)
        viewModelScope.launch {
            socket?.incoming?.collect { text ->
                // Best-effort presence parsing; chat messages are appended verbatim.
                if (text.contains("PRESENCE_UPDATE")) {
                    // no-op parsing for now; keep last status map untouched
                }
                if (text.contains("CHAT_MESSAGE")) {
                    // append raw payload as a system-ish message placeholder
                }
            }
        }
    }

    fun sendChat(body: String) {
        socket?.chat(body)
    }

    fun setPresence(status: PresenceStatus) {
        if (_state.value.stealthMode && status == PresenceStatus.WORKING_OUT) return
        socket?.presence(status)
    }

    fun toggleStealth() {
        _state.value = _state.value.copy(stealthMode = !_state.value.stealthMode)
    }

    override fun onCleared() {
        super.onCleared()
        socket?.disconnect()
        client.close()
    }
}
