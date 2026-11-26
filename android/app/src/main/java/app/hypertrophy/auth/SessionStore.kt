package app.hypertrophy.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.hypertrophy.core.data.network.AuthSessionStore
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The account signed in on this device, resolved from the active Clerk session. */
data class AuthSession(
    val email: String,
    val userId: String,
    val sessionId: String,
) {
    val displayName: String
        get() = email.substringBefore('@')
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .ifBlank { "Athlete" }
}

/**
 * Projects the Clerk client state into Compose state and keeps the network
 * layer's token hook pointed at the live session.
 *
 * Clerk owns credential storage and session refresh. This object only mirrors
 * "is there an active session?" for the UI and exposes a token refresher so
 * Ktor requests carry a current bearer token.
 */
object SessionStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    private var storedSession by mutableStateOf<AuthSession?>(null)

    val session: AuthSession? get() = storedSession

    /** Starts observing Clerk. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true

        AuthSessionStore.tokenRefresher = {
            when (val result = Clerk.auth.getToken()) {
                is ClerkResult.Success -> result.value
                is ClerkResult.Failure -> null
            }
        }

        combine(Clerk.sessionFlow, Clerk.userFlow) { session, user ->
            if (session?.status == Session.SessionStatus.ACTIVE && user != null) {
                AuthSession(
                    email = user.primaryEmailAddress?.emailAddress.orEmpty(),
                    userId = user.id,
                    sessionId = session.id,
                )
            } else {
                null
            }
        }
            .onEach { active ->
                // The user id is written before the Compose state so feature
                // view models can never be constructed with a stale identity.
                AuthSessionStore.userId = active?.userId
                storedSession = active
                // A cached token from the previous session must never leak into
                // the next one; the refresher mints a fresh one per request.
                AuthSessionStore.token = null
            }
            .launchIn(scope)
    }

    /** Ends the Clerk session; the observer above clears the UI state. */
    fun signOut() {
        scope.launch {
            Clerk.auth.signOut()
        }
    }
}
