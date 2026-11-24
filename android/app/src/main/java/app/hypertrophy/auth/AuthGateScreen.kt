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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.hypertrophy.core.designsystem.component.PhoenixIgnitionButton
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors

private const val CLERK_NOTICE =
    "Google and passkey sign-in need the Clerk app integration, which is not part of this build yet."

@Composable
fun AuthGateScreen(
    onEmailSignIn: (email: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPhoenixColors.current
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

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
            text = "Train. Track. Triumph.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                error = null
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        PhoenixIgnitionButton(
            text = "Sign in with email",
            onClick = {
                val trimmed = email.trim()
                val localPart = trimmed.substringBefore('@')
                val domain = trimmed.substringAfter('@', "")
                when {
                    localPart.isBlank() || domain.isBlank() || !domain.contains('.') -> {
                        error = "Enter a valid email address."
                        notice = null
                    }

                    else -> {
                        error = null
                        notice = null
                        onEmailSignIn(trimmed)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))
        PhoenixIgnitionButton(
            text = "Continue with Google",
            onClick = {
                error = null
                notice = CLERK_NOTICE
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        PhoenixIgnitionButton(
            text = "Sign in with Passkey",
            onClick = {
                error = null
                notice = CLERK_NOTICE
            },
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = colors.formErrorCrimson,
            )
        }
        notice?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "The account is stored on this device. Cloud sync unlocks once Clerk sign-in lands.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}
