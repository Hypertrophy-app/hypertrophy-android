package app.hypertrophy.auth

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The account signed in on this device. Only the email is stored. */
data class AuthSession(val email: String) {
    val displayName: String
        get() = email.substringBefore('@')
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .ifBlank { "Athlete" }
}

/**
 * Device-local sign-in state, persisted in SharedPreferences so the app only
 * asks for an account on a fresh install or after signing out.
 *
 * The backend only accepts Clerk session JWTs and the Clerk Android SDK is not
 * on the current build baseline, so this gate cannot verify credentials against
 * a server yet. It records which local account is active so the offline-first
 * screens can be reached; `AuthSessionStore.token` stays null, so network calls
 * remain unauthenticated until Clerk sign-in lands.
 */
object SessionStore {
    private const val PREFS_NAME = "hypertrophy_session"
    private const val KEY_EMAIL = "email"

    private var storedSession by mutableStateOf<AuthSession?>(null)

    val session: AuthSession? get() = storedSession

    /** Loads the persisted account once, on activity creation. */
    fun load(context: Context) {
        val email = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_EMAIL, null)
        storedSession = email?.takeIf { it.isNotBlank() }?.let(::AuthSession)
    }

    fun signIn(context: Context, email: String) {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EMAIL, normalized)
            .apply()
        storedSession = AuthSession(normalized)
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        storedSession = null
    }
}
