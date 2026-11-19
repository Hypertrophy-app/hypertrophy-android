package app.hypertrophy.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Supplies the active Clerk session JWT (or null when signed out). Implemented
 * by the app layer; the database/network modules never depend on Clerk directly.
 */
fun interface TokenProvider {
    suspend fun currentToken(): String?
}

/**
 * Holds the active session JWT. Clerk sign-in (app layer) writes it and
 * [AuthTokenPlugin] reads it at request time, including WebSocket handshakes.
 * Null while signed out, which makes callers send no Authorization header.
 */
object AuthSessionStore {
    @Volatile
    var token: String? = null
}

class AuthTokenConfig {
    lateinit var tokenProvider: TokenProvider
}

val AuthTokenPlugin: ClientPlugin<AuthTokenConfig> = createClientPlugin(
    name = "AuthTokenPlugin",
    createConfiguration = { AuthTokenConfig() },
) {
    val tokenProvider = pluginConfig.tokenProvider
    onRequest { request, _ ->
        val token = tokenProvider.currentToken()
        if (!token.isNullOrBlank()) {
            request.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }
}

/**
 * Ktor client factory that attaches `Authorization: Bearer <jwt>` to every HTTP
 * request (and WebSocket handshakes) by reading the token from [tokenProvider]
 * at request time.
 */
fun createAuthenticatedHttpClient(
    tokenProvider: TokenProvider,
    configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient {
    configure()
    install(AuthTokenPlugin) {
        this.tokenProvider = tokenProvider
    }
}
