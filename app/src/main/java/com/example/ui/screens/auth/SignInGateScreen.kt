package com.example.ui.screens.auth

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.AppContainer
import com.example.R
import com.example.domain.model.AppAccountSession
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch

@Composable
fun SignInGateScreen(
    container: AppContainer,
    session: AppAccountSession
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var githubToken by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }
    var localUser by remember(session.username) { mutableStateOf(session.username.ifBlank { "Developer" }) }
    var localPasscode by remember { mutableStateOf("") }
    var hasPasscodeStored by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        hasPasscodeStored = container.settingsDataStore.hasLocalPasscode()
        val existingToken = container.settingsDataStore.getGitHubToken()
        if (existingToken.isNotBlank()) {
            githubToken = existingToken
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
                    .widthIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(84.dp)
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
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sign in with your GitHub account or unlock your local developer profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
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

                // GitHub Token Sign-In Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("GitHub Account Sign-In", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = githubToken,
                            onValueChange = { githubToken = it },
                            label = { Text("GitHub Personal Access Token") },
                            placeholder = { Text("ghp_... or github_pat_...") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showToken) "Hide token" else "Show token"
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gate_github_token_input")
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        errorMessage = null
                                        val clean = githubToken.trim()
                                        container.gitHubPushClient.verifyToken(clean).fold(
                                            onSuccess = { profile ->
                                                container.settingsDataStore.saveGitHubToken(clean)
                                                container.settingsDataStore.saveGitHubUserProfile(profile)
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.message ?: "GitHub sign-in failed."
                                            }
                                        )
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy && githubToken.isNotBlank(),
                                modifier = Modifier.testTag("gate_github_signin_button")
                            ) {
                                if (isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text("Sign In with GitHub")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://github.com/settings/tokens/new?scopes=repo,workflow,delete_repo,read:user&description=IB+CODER+Android")
                                            )
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Token")
                            }
                        }
                    }
                }

                // Local Profile Unlock Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Text("Local Profile Unlock", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = localUser,
                            onValueChange = { localUser = it },
                            label = { Text("Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = localPasscode,
                            onValueChange = { localPasscode = it },
                            label = {
                                Text(if (hasPasscodeStored) "Enter App Passcode" else "App Passcode (optional)")
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gate_local_passcode_input")
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    errorMessage = null
                                    if (hasPasscodeStored) {
                                        val ok = container.settingsDataStore.verifyLocalPasscode(localPasscode)
                                        if (!ok) {
                                            errorMessage = "Incorrect local passcode."
                                            return@launch
                                        }
                                    }
                                    container.settingsDataStore.signInLocalAccount(localUser, localPasscode)
                                }
                            },
                            enabled = localUser.isNotBlank() && !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("gate_local_unlock_button")
                        ) {
                            Text(if (hasPasscodeStored) "Unlock App" else "Continue as Local User")
                        }
                    }
                }

                if (!hasPasscodeStored) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                container.settingsDataStore.setRequireSignInOnLaunch(false)
                            }
                        }
                    ) {
                        Text("Disable launch sign-in requirement")
                    }
                }
            }
        }
    }
}
