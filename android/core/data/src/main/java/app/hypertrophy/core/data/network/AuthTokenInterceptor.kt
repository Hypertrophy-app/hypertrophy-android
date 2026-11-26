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

    /**
     * Clerk user id (`sub`) for the active session. Every local row and every
     * sync payload is keyed by this value, so the app has no shared local-user
     * identity. The app layer writes it when Clerk reports an active session.
     */
    @Volatile
    var userId: String? = null

    /**
     * The signed-in user id. Screens are gated behind Clerk, so a feature view
     * model is only constructed after [userId] has been populated.
     */
    val requireUserId: String
        get() = userId ?: error("No authenticated Clerk user is active")

    /**
     * Live source for the session JWT, wired by the app layer to Clerk. Keeping
     * this as a lambda means the database/network modules never depend on Clerk,
     * and every request gets a freshly minted token instead of one that may have
     * expired since sign-in.
     */
    @Volatile
    var tokenRefresher: (suspend () -> String?)? = null

    /** Reads a fresh token when a refresher is wired, otherwise the cached one. */
    suspend fun currentToken(): String? = tokenRefresher?.invoke() ?: token
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
