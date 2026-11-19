package app.hypertrophy.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthTokenPluginTest {
    @Test
    fun attachesBearerTokenWhenPresent() = runBlocking {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }
        val client = HttpClient(engine) {
            install(AuthTokenPlugin) {
                tokenProvider = TokenProvider { "jwt-abc" }
            }
        }

        client.get("https://api.example.test/rooms/squad-1")

        // The same hook covers HTTP calls and WebSocket handshakes.
        assertEquals(
            "Bearer jwt-abc",
            engine.requestHistory.single().headers[HttpHeaders.Authorization],
        )
    }

    @Test
    fun omitsHeaderWhenSignedOut() = runBlocking {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }
        val client = HttpClient(engine) {
            install(AuthTokenPlugin) {
                tokenProvider = TokenProvider { null }
            }
        }

        client.get("https://api.example.test/rooms/squad-1")

        assertNull(engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun tokenIsReadAtRequestTimeNotInstallTime() = runBlocking {
        val engine = MockEngine { respond("ok", HttpStatusCode.OK) }
        var current: String? = "first"
        val client = HttpClient(engine) {
            install(AuthTokenPlugin) {
                tokenProvider = TokenProvider { current }
            }
        }

        client.get("https://api.example.test/one")
        current = "second"
        client.get("https://api.example.test/two")

        val history = engine.requestHistory
        assertEquals("Bearer first", history[0].headers[HttpHeaders.Authorization])
        assertEquals("Bearer second", history[1].headers[HttpHeaders.Authorization])
    }
}
