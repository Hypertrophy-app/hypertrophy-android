package app.hypertrophy.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors

@Composable
fun AuthGateRoute(modifier: Modifier = Modifier) {
    val viewModel: AuthFlowViewModel = viewModel()
    AuthGateScreen(
        state = viewModel.state,
        onEmailChange = viewModel::onEmailChange,
        onCodeChange = viewModel::onCodeChange,
        onSubmitEmail = viewModel::requestEmailCode,
        onSubmitCode = viewModel::verifyEmailCode,
        onResendCode = viewModel::requestEmailCode,
        onEditEmail = viewModel::editEmail,
        onModeChange = viewModel::setMode,
        onGoogle = viewModel::signInWithGoogle,
        onPasskey = viewModel::signInWithPasskey,
        modifier = modifier,
    )
}

@Composable
fun AuthGateScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitEmail: () -> Unit,
    onSubmitCode: () -> Unit,
    onResendCode: () -> Unit,
    onEditEmail: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onGoogle: () -> Unit,
    onPasskey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    val busy = state.isBusy
    val isSignUp = state.mode == AuthMode.SignUp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .navigationBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "HYPERTROPHY",
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isSignUp) "Forge your account." else "Train. Track. Triumph.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (state.step == AuthStep.EnterEmail) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            PhoenixIgnitionButton(
                text = when {
                    busy -> "Sending code..."
                    isSignUp -> "Create my account"
                    else -> "Email me a sign-in code"
                },
                onClick = onSubmitEmail,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = {
                    onModeChange(if (isSignUp) AuthMode.SignIn else AuthMode.SignUp)
                },
                enabled = !busy,
            ) {
                Text(
                    text = if (isSignUp) {
                        "Already have an account? Sign in"
                    } else {
                        "New here? Create an account"
                    },
                    color = colors.emberOrange,
                )
            }
        } else {
            Text(
                text = if (isSignUp) {
                    "Enter the code sent to ${state.email} to finish creating your account"
                } else {
                    "Enter the code sent to ${state.email}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text("6-digit code") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            PhoenixIgnitionButton(
                text = when {
                    busy -> "Verifying..."
                    isSignUp -> "Verify and create account"
                    else -> "Verify and sign in"
                },
                onClick = onSubmitCode,
                enabled = !busy && state.code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onResendCode, enabled = !busy) {
                Text("Resend code", color = colors.emberOrange)
            }
            TextButton(onClick = onEditEmail, enabled = !busy) {
                Text("Use a different email", color = colors.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        PhoenixIgnitionButton(
            text = "Continue with Google",
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        // Passkeys only exist for accounts that already registered one, so the
        // button is only meaningful while signing in.
        if (!isSignUp) {
            Spacer(modifier = Modifier.height(12.dp))
            PhoenixIgnitionButton(
                text = "Sign in with Passkey",
                onClick = onPasskey,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.error?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = colors.formErrorCrimson,
            )
        }
        state.notice?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = colors.emberOrange,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Authentication is handled by Clerk. Your workout data stays on this device and syncs across your signed-in devices.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}
