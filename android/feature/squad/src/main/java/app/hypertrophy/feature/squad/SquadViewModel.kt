package app.hypertrophy.feature.squad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hypertrophy.core.data.local.SquadDao
import app.hypertrophy.core.data.network.TokenProvider
import app.hypertrophy.core.data.network.createAuthenticatedHttpClient
import app.hypertrophy.core.data.remote.HypertrophyApi
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
import kotlinx.coroutines.flow.collect
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
    val hostSession: HostSessionUi? = null,
    val stealthMode: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SquadViewModel @Inject constructor(
    private val tokenProvider: TokenProvider,
    private val squadDao: SquadDao,
) : ViewModel() {
    private val client = createAuthenticatedHttpClient(tokenProvider) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val _state = MutableStateFlow(SquadUiState())
    val state: StateFlow<SquadUiState> = _state.asStateFlow()

    private val baseUrl = "${HypertrophyApi.BASE_URL}/api"

    private var socket: SquadWebSocketClient? = null
    private var socketJob: Job? = null

    // The socket authenticates with the Clerk JWT, so the client does not know
    // its own backend user id up front. The first create echo teaches us the
    // host id and lets the host-only controls appear.
    private var awaitingHostCreate = false
    private var localHostUserId: String? = null

    init {
        viewModelScope.launch {
            squadDao.observeSquadsForUser(USER_ID).collect { cached ->
                val squads = cached.map { it.squad.toDto(it.role) }
                _state.value = _state.value.copy(
                    squads = squads,
                    selectedSquadId = _state.value.selectedSquadId
                        ?.takeIf { selected -> squads.any { it.id == selected } },
                )
            }
        }
        loadSquads()
    }

    /**
     * Refreshes the network snapshot. UI never reads this response directly:
     * it is written to Room and the cache flow above emits the new list. When
     * offline the cached rows stay visible.
     */
    fun loadSquads() {
        viewModelScope.launch {
            val response = runCatching {
                client.get("$baseUrl/squads").body<SquadListResponse>()
            }.getOrNull() ?: return@launch

            val now = System.currentTimeMillis()
            runCatching {
                squadDao.refreshSquadsForUser(
                    userId = USER_ID,
                    squads = response.squads.map { it.toEntity() },
                    members = response.squads.map { it.toMembership(USER_ID, now) },
                )
            }
            _state.value = _state.value.copy(error = null)
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
                cacheSquad(squad)
                loadSquads()
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
                cacheSquad(squad)
                selectSquad(response.squadId)
                loadSquads()
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
            hostSession = null,
            error = null,
        )
        awaitingHostCreate = false
        localHostUserId = null
        socketJob?.cancel()
        socket?.disconnect()
        socket = SquadWebSocketClient(HypertrophyApi.BASE_URL.replaceFirst("https://", "wss://"), tokenProvider)
        socketJob = viewModelScope.launch {
            socket?.incoming?.collect { text -> applyEvent(SquadWireParser.parse(text)) }
        }
        socket?.connect(squadId)
    }

    fun sendChat(body: String) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        if (_state.value.selectedSquadId == null) {
            _state.value = _state.value.copy(error = "Connect to a squad first")
            return
        }
        socket?.chat(trimmed)
    }

    /**
     * Opens a group-session lobby with a default full-body queue. Every member
     * connected to the room sees the queue and the host can start it.
     */
    fun createHostSession(restSeconds: Int) {
        if (_state.value.selectedSquadId == null) return
        val activeSocket = socket ?: return
        val rest = restSeconds.coerceIn(MIN_REST_SECONDS, MAX_REST_SECONDS)
        val queue = defaultGroupQueue(rest)
        awaitingHostCreate = true
        _state.value = _state.value.copy(
            hostSession = HostSessionUi(
                hostUserId = "",
                queue = queue,
                restSeconds = rest,
                createdAtMillis = System.currentTimeMillis(),
                isHost = true,
            ),
            error = null,
        )
        activeSocket.createHostSession(queue, rest)
    }

    fun startHostSession() {
        val session = _state.value.hostSession ?: return
        if (!session.isHost || session.started) return
        socket?.startHostSession()
    }

    fun setPresence(status: PresenceStatus) {
        if (_state.value.stealthMode && status == PresenceStatus.WORKING_OUT) return
        socket?.presence(status)
    }

    fun toggleStealth() {
        _state.value = _state.value.copy(stealthMode = !_state.value.stealthMode)
    }

    fun setStealthMode(enabled: Boolean) {
        _state.value = _state.value.copy(stealthMode = enabled)
    }

    private suspend fun cacheSquad(squad: SquadDto) {
        val now = System.currentTimeMillis()
        squadDao.upsertSquads(listOf(squad.toEntity()))
        squadDao.upsertSquadMembers(listOf(squad.toMembership(USER_ID, now)))
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

            is SquadEvent.HostSessionCreate -> {
                val isHost = if (awaitingHostCreate) {
                    awaitingHostCreate = false
                    localHostUserId = event.hostUserId
                    true
                } else {
                    event.hostUserId == localHostUserId
                }
                _state.value = _state.value.copy(
                    hostSession = HostSessionUi(
                        hostUserId = event.hostUserId,
                        queue = event.queue,
                        restSeconds = event.restSeconds,
                        createdAtMillis = event.createdAt,
                        isHost = isHost,
                    ),
                    error = null,
                )
            }

            is SquadEvent.HostSessionStart -> {
                val current = _state.value.hostSession
                val isHost = event.hostUserId.isNotBlank() && event.hostUserId == localHostUserId
                _state.value = _state.value.copy(
                    hostSession = if (current == null) {
                        HostSessionUi(
                            hostUserId = event.hostUserId,
                            queue = event.queue,
                            restSeconds = event.restSeconds,
                            createdAtMillis = event.createdAt,
                            startedAtMillis = event.startedAt,
                            isHost = isHost,
                        )
                    } else {
                        current.copy(
                            hostUserId = event.hostUserId.ifBlank { current.hostUserId },
                            queue = event.queue.ifEmpty { current.queue },
                            restSeconds = if (event.restSeconds > 0) {
                                event.restSeconds
                            } else {
                                current.restSeconds
                            },
                            createdAtMillis = if (event.createdAt > 0L) {
                                event.createdAt
                            } else {
                                current.createdAtMillis
                            },
                            startedAtMillis = event.startedAt,
                            isHost = current.isHost || isHost,
                        )
                    },
                    error = null,
                )
            }

            is SquadEvent.Error -> {
                if (event.code == "session_exists") {
                    // Our optimistic lobby lost to an existing host. Drop the
                    // local card so nobody can try to start a session we do
                    // not own.
                    awaitingHostCreate = false
                    _state.value = _state.value.copy(hostSession = null)
                }
                _state.value = _state.value.copy(error = squadErrorMessage(event.code))
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

    companion object {
        const val USER_ID = "local-user"
        const val MIN_REST_SECONDS = 15
        const val MAX_REST_SECONDS = 300

        fun defaultGroupQueue(restSeconds: Int): List<HostExercise> = listOf(
            HostExercise(
                exerciseId = "ex_back_squat",
                sets = 3,
                reps = 8,
                restSeconds = restSeconds,
            ),
            HostExercise(
                exerciseId = "ex_bench_press",
                sets = 3,
                reps = 8,
                restSeconds = restSeconds,
            ),
            HostExercise(
                exerciseId = "ex_deadlift",
                sets = 3,
                reps = 5,
                restSeconds = restSeconds,
            ),
        )
    }
}
