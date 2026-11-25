package app.hypertrophy.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clerk.api.Clerk
import com.clerk.api.auth.types.VerificationType
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.verifyCode
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.sendEmailCode
import com.clerk.api.signup.verifyCode
import com.clerk.api.sso.OAuthResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class AuthStep {
    EnterEmail,
    EnterCode,
}

/** Whether the email the athlete typed belongs to an existing account or a new one. */
enum class AuthMode {
    SignIn,
    SignUp,
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val code: String = "",
    val step: AuthStep = AuthStep.EnterEmail,
    val isBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/**
 * Drives the Clerk email-code, Google and passkey flows. The screen swaps to
 * the dashboard as soon as [SessionStore] observes the activated session, so
 * this view model never stores credentials or tokens itself.
 *
 * Clerk keeps sign-in and sign-up apart: signing in with an email that has no
 * account fails with `form_identifier_not_found` and never creates the user.
 * [AuthMode.SignUp] therefore runs [Clerk.auth.signUp] followed by an email
 * code verification, which is what the create-account surface calls.
 */
class AuthFlowViewModel : ViewModel() {
    var state by mutableStateOf(AuthUiState())
        private set

    private var pendingSignIn: SignIn? = null
    private var pendingSignUp: SignUp? = null

    fun onEmailChange(value: String) {
        state = state.copy(email = value, error = null, notice = null)
    }

    fun onCodeChange(value: String) {
        state = state.copy(code = value.filter(Char::isDigit), error = null)
    }

    /** Switches between "sign in" and "create an account", clearing stale state. */
    fun setMode(mode: AuthMode) {
        if (mode == state.mode) return
        pendingSignIn = null
        pendingSignUp = null
        state = state.copy(
            mode = mode,
            step = AuthStep.EnterEmail,
            code = "",
            isBusy = false,
            error = null,
            notice = null,
        )
    }

    fun editEmail() {
        pendingSignIn = null
        pendingSignUp = null
        state = state.copy(
            step = AuthStep.EnterEmail,
            code = "",
            isBusy = false,
            error = null,
            notice = null,
        )
    }

    /**
     * Sends the email code for the current mode. On the code step this also
     * acts as "resend": a pending sign-up is reused so its verification state
     * is preserved instead of starting over.
     */
    fun requestEmailCode() {
        if (state.isBusy) return
        val email = state.email.trim()
        if (!isValidEmail(email)) {
            state = state.copy(error = "Enter a valid email address.")
            return
        }

        val mode = state.mode
        viewModelScope.launch {
            state = state.copy(isBusy = true, error = null, notice = null)
            if (!awaitClerk()) {
                state = state.copy(isBusy = false, error = CLERK_UNAVAILABLE)
                return@launch
            }

            if (mode == AuthMode.SignUp) {
                val existing = pendingSignUp
                if (existing != null) {
                    resendSignUpCode(existing, email)
                } else {
                    startSignUp(email)
                }
            } else {
                startSignIn(email)
            }
        }
    }

    fun verifyEmailCode() {
        if (state.isBusy) return
        val code = state.code.trim()
        if (code.length < 4) {
            state = state.copy(error = "Enter the code from your email.")
            return
        }

        val signUp = pendingSignUp
        val signIn = pendingSignIn
        if (signUp == null && signIn == null) {
            state = state.copy(step = AuthStep.EnterEmail, error = "Request a new code.")
            return
        }

        viewModelScope.launch {
            state = state.copy(isBusy = true, error = null)
            if (signUp != null) {
                when (val result = signUp.verifyCode(code, VerificationType.EMAIL)) {
                    is ClerkResult.Success -> completeSignUp(result.value)
                    is ClerkResult.Failure -> state = state.copy(isBusy = false, error = result.describe())
                }
            } else if (signIn != null) {
                when (val result = signIn.verifyCode(code)) {
                    is ClerkResult.Success -> completeSignIn(result.value)
                    is ClerkResult.Failure -> state = state.copy(isBusy = false, error = result.describe())
                }
            }
        }
    }

    fun signInWithGoogle() {
        if (state.isBusy) return
        viewModelScope.launch {
            state = state.copy(isBusy = true, error = null, notice = null)
            if (!awaitClerk()) {
                state = state.copy(isBusy = false, error = CLERK_UNAVAILABLE)
                return@launch
            }
            // transferable = true lets Clerk create the account and hand back a
            // sign-up, so Google works from both the sign-in and sign-up modes.
            when (val result = Clerk.auth.signInWithGoogleOneTap(transferable = true)) {
                is ClerkResult.Success -> completeOAuth(result.value)
                is ClerkResult.Failure -> state = state.copy(
                    isBusy = false,
                    error = result.describe(
                        "Google sign-in is not available yet. Enable the Google provider in the Clerk dashboard.",
                    ),
                )
            }
        }
    }

    fun signInWithPasskey() {
        if (state.isBusy) return
        viewModelScope.launch {
            state = state.copy(isBusy = true, error = null, notice = null)
            if (!awaitClerk()) {
                state = state.copy(isBusy = false, error = CLERK_UNAVAILABLE)
                return@launch
            }
            when (val result = Clerk.auth.signInWithPasskey()) {
                is ClerkResult.Success -> completeSignIn(result.value)
                is ClerkResult.Failure -> state = state.copy(
                    isBusy = false,
                    error = result.describe("Passkey sign-in is not available on this device."),
                )
            }
        }
    }

    private suspend fun startSignIn(email: String) {
        when (val result = Clerk.auth.signInWithOtp { this.email = email }) {
            is ClerkResult.Success -> {
                pendingSignIn = result.value
                state = state.copy(
                    isBusy = false,
                    step = AuthStep.EnterCode,
                    code = "",
                    notice = "We sent a 6-digit code to $email.",
                )
            }

            is ClerkResult.Failure -> {
                val message = if (result.isUnknownIdentifier()) {
                    "No account found for $email. Choose Create an account to sign up."
                } else {
                    result.describe()
                }
                state = state.copy(isBusy = false, error = message)
            }
        }
    }

    private suspend fun startSignUp(email: String) {
        when (val created = Clerk.auth.signUp { this.email = email }) {
            is ClerkResult.Success -> {
                pendingSignUp = created.value
                resendSignUpCode(created.value, email, "We sent a 6-digit code to $email to finish creating your account.")
            }

            is ClerkResult.Failure -> state = state.copy(isBusy = false, error = created.describe())
        }
    }

    private suspend fun resendSignUpCode(
        signUp: SignUp,
        email: String,
        notice: String = "We sent a new code to $email.",
    ) {
        when (val result = signUp.sendEmailCode()) {
            is ClerkResult.Success -> {
                pendingSignUp = result.value
                state = state.copy(
                    isBusy = false,
                    step = AuthStep.EnterCode,
                    code = "",
                    notice = notice,
                )
            }

            is ClerkResult.Failure -> state = state.copy(isBusy = false, error = result.describe())
        }
    }

    private suspend fun completeOAuth(result: OAuthResult) {
        when (val outcome = result.outcome) {
            is OAuthResult.Outcome.SignIn -> completeSignIn(outcome.signIn)
            is OAuthResult.Outcome.SignUp -> completeSignUp(outcome.signUp)
            OAuthResult.Outcome.Empty -> state = state.copy(
                isBusy = false,
                error = "Google sign-in returned no session. Try again.",
            )
        }
    }

    private suspend fun completeSignIn(signIn: SignIn) {
        when (signIn.status) {
            SignIn.Status.COMPLETE -> {
                val sessionId = signIn.createdSessionId
                if (sessionId == null) {
                    state = state.copy(isBusy = false, error = "Sign-in finished without a session.")
                } else {
                    activate(sessionId)
                }
            }

            SignIn.Status.NEEDS_SECOND_FACTOR -> state = state.copy(
                isBusy = false,
                error = "This account needs a second factor. Finish two-factor sign-in on the web, then retry.",
            )

            SignIn.Status.NEEDS_FIRST_FACTOR -> state = state.copy(
                isBusy = false,
                error = "This account needs a different first factor than an email code. In the Clerk dashboard, check that Email is an allowed sign-in identifier and that no password is required.",
            )

            else -> state = state.copy(
                isBusy = false,
                error = "Sign-in needs another step: ${signIn.status.name.lowercase().replace('_', ' ')}.",
            )
        }
    }

    private suspend fun completeSignUp(signUp: SignUp) {
        when (signUp.status) {
            SignUp.Status.COMPLETE -> {
                val sessionId = signUp.createdSessionId
                if (sessionId == null) {
                    state = state.copy(
                        isBusy = false,
                        error = "Account created, but no session was returned. Switch to Sign in and try again.",
                    )
                } else {
                    activate(sessionId)
                }
            }

            // Clerk reports every field the instance still expects. This app
            // collects an email address only, so anything else here means the
            // instance is configured for attributes the gate cannot supply.
            SignUp.Status.MISSING_REQUIREMENTS -> state = state.copy(
                isBusy = false,
                error = describeMissingFields(signUp.missingFields),
            )

            else -> state = state.copy(
                isBusy = false,
                error = "Account creation needs another step: ${signUp.status.name.lowercase().replace('_', ' ')}.",
            )
        }
    }

    private suspend fun activate(sessionId: String) {
        when (val result = Clerk.auth.setActive(sessionId)) {
            is ClerkResult.Success -> {
                pendingSignIn = null
                pendingSignUp = null
                state = AuthUiState()
            }

            is ClerkResult.Failure -> state = state.copy(isBusy = false, error = result.describe())
        }
    }

    private suspend fun awaitClerk(): Boolean =
        withTimeoutOrNull(CLERK_INIT_TIMEOUT_MS) {
            Clerk.isInitialized.first { it }
        } ?: false

    /**
     * Clerk splits its messages in two: `message` is a fragment such as
     * "is incorrect" and `longMessage` is the full sentence meant for display.
     * Prefer the long form so the UI never shows a dangling fragment.
     */
    private fun ClerkResult.Failure<ClerkErrorResponse>.describe(
        fallback: String = "Sign-in failed. Check your connection and try again.",
    ): String {
        val first = error?.errors?.firstOrNull()
        return first?.longMessage?.takeIf(String::isNotBlank)
            ?: first?.message?.takeIf(String::isNotBlank)
            ?: throwable?.message?.takeIf(String::isNotBlank)
            ?: fallback
    }

    /** Clerk's code for "this identifier is not attached to any account". */
    private fun ClerkResult.Failure<ClerkErrorResponse>.isUnknownIdentifier(): Boolean =
        error?.errors?.any { it.code == UNKNOWN_IDENTIFIER_CODE } == true

    /**
     * Turns Clerk's `missing_fields` into one actionable sentence. The gate
     * only asks for an email address, so every other field is a dashboard
     * mismatch rather than something the athlete can fix in the app.
     */
    private fun describeMissingFields(missing: List<String>): String {
        if (missing.isEmpty()) {
            return "Clerk needs more information to finish this account. Check User & Authentication in the Clerk dashboard."
        }
        val readable = missing.joinToString(", ") { field ->
            when (field) {
                "email_address" -> "email"
                "phone_number" -> "phone number"
                "username" -> "username"
                "password" -> "password"
                else -> field.replace('_', ' ')
            }
        }
        return "Clerk still requires $readable, but HYPERTROPHY signs up with an email code only. " +
            "In the Clerk dashboard open Configure > User & Authentication and switch off the " +
            "Phone number, Username and Password requirements so Email is the only identifier."
    }

    private fun isValidEmail(value: String): Boolean {
        val localPart = value.substringBefore('@')
        val domain = value.substringAfter('@', "")
        return localPart.isNotBlank() && domain.isNotBlank() && domain.contains('.')
    }

    private companion object {
        const val CLERK_INIT_TIMEOUT_MS = 15_000L
        const val UNKNOWN_IDENTIFIER_CODE = "form_identifier_not_found"
        const val CLERK_UNAVAILABLE =
            "Clerk is not configured on this build. Add CLERK_PUBLISHABLE_KEY to local.properties and rebuild."
    }
}
