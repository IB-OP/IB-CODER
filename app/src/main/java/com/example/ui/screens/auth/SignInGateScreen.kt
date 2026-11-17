package com.example.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.AppContainer
import com.example.R
import com.example.ui.theme.BrandWordmarkStyle
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun SignInGateScreen(
    container: AppContainer
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isBusy by remember { mutableStateOf(false) }
    var isCheckingSilent by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun handleGoogleCredentialResult(credentialData: android.os.Bundle) {
        try {
            val googleCred = GoogleIdTokenCredential.createFrom(credentialData)
            container.cloudAccountVaultRepository.signInWithGoogleIdToken(googleCred.idToken).fold(
                onSuccess = {
                    errorMessage = null
                },
                onFailure = { err ->
                    errorMessage = err.message ?: "Firebase authentication failed."
                }
            )
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("Auth", "Invalid Google ID token", e)
            errorMessage = "Could not parse Google Sign-In response."
        }
    }

    // Silent auto sign-in check on launch for already-authorized accounts
    LaunchedEffect(Unit) {
        val activity = context.findActivity()
        if (activity != null) {
            try {
                val clientId = context.getString(R.string.default_web_client_id)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(clientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    handleGoogleCredentialResult(cred.data)
                }
            } catch (_: NoCredentialException) {
                // First time or signed out; user taps Sign in with Google button.
            } catch (_: GetCredentialCancellationException) {
                // Ignored on silent launch check.
            } catch (e: Exception) {
                Log.w("Auth", "Silent sign-in skipped: ${e.message}")
            }
        }
        isCheckingSilent = false
    }

    fun launchInteractiveGoogleSignIn() {
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = "Cannot start Google Sign-In without an active screen."
            return
        }
        scope.launch {
            isBusy = true
            errorMessage = null
            try {
                val clientId = context.getString(R.string.default_web_client_id)
                val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val result = credentialManager.getCredential(activity, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    handleGoogleCredentialResult(cred.data)
                } else {
                    errorMessage = "Unsupported credential type returned."
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "User cancelled Google Sign-In", e)
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Credential Manager sign-in error", e)
                errorMessage = e.message ?: "Google Sign-In failed. Ensure a Google account is signed in on this device."
            } catch (e: Exception) {
                Log.e("Auth", "Unexpected sign-in error", e)
                errorMessage = e.message ?: "Sign-in failed."
            } finally {
                isBusy = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    modifier = Modifier.size(88.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ib_coder_logo),
                        contentDescription = "IB CODER logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = "IB CODER",
                    style = BrandWordmarkStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sign in to link your AI provider API keys, custom endpoints, GitHub tokens, and MCP servers across all your devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Cross-Device Account Vault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        VaultFeatureRow(
                            icon = Icons.Rounded.Key,
                            text = "Sync AI provider API keys & custom provider URLs"
                        )
                        VaultFeatureRow(
                            icon = Icons.Rounded.CloudSync,
                            text = "Restore GitHub token, repo links & auto-sync preferences"
                        )
                        VaultFeatureRow(
                            icon = Icons.Rounded.Hub,
                            text = "Keep MCP servers & session prompts linked to your account"
                        )
                        VaultFeatureRow(
                            icon = Icons.Rounded.Security,
                            text = "Zero-trust user-isolated storage scoped to your account"
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Button(
                            onClick = { launchInteractiveGoogleSignIn() },
                            enabled = !isBusy && !isCheckingSilent,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("gate_google_signin_button")
                        ) {
                            if (isBusy || isCheckingSilent) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (isCheckingSilent) "Checking session..." else "Signing in...")
                            } else {
                                Icon(
                                    Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Sign in with Google",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultFeatureRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
