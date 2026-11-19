package app.hypertrophy.feature.squad

import app.hypertrophy.core.data.network.TokenProvider
import app.hypertrophy.core.data.network.createAuthenticatedHttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class SquadWebSocketClient(
    private val baseUrl: String,
    tokenProvider: TokenProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // AuthTokenPlugin attaches the Clerk JWT to the handshake; the backend
    // verifies it with requireAuth before upgrading the socket.
    private val client = createAuthenticatedHttpClient(tokenProvider) {
        install(WebSockets)
    }

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    private val sendChannel = Channel<String>(Channel.BUFFERED)

    @Volatile
    private var running = false

    fun connect(squadId: String, userId: String) {
        if (running) return
        running = true
        scope.launch {
            try {
                client.webSocket("$baseUrl/rooms/$squadId?userId=$userId&squadId=$squadId") {
                    launch {
                        for (msg in sendChannel) {
                            send(Frame.Text(msg))
                        }
                    }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            _incoming.emit(frame.readText())
                        }
                    }
                }
            } catch (_: Throwable) {
                // connection closed; caller may reconnect
            } finally {
                running = false
            }
        }
    }

    fun send(text: String) {
        scope.launch { sendChannel.send(text) }
    }

    fun presence(status: PresenceStatus) {
        send("""{"type":"PRESENCE_UPDATE","status":"${status.wire}"}""")
    }

    fun chat(body: String) {
        send("""{"type":"CHAT_MESSAGE","body":${Json.encodeToString(serializer<String>(), body)}}""")
    }

    fun disconnect() {
        running = false
        client.close()
    }
}
