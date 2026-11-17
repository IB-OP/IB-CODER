package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.remote.CloudVaultSyncStatus
import com.example.domain.model.ThemeMode
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenProviders: () -> Unit,
    onOpenMcpServers: () -> Unit,
    onOpenGitHub: () -> Unit = {},
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var promptDraft by remember(state.settings.systemPrompt) {
        mutableStateOf(state.settings.systemPrompt)
    }
    var defaultModelDraft by remember(state.settings.defaultModelId, state.settings.defaultProviderId) {
        mutableStateOf(
            state.settings.defaultModelId.ifBlank {
                state.providerConfigs[state.settings.defaultProviderId]?.selectedModel.orEmpty()
            }
        )
    }
    var selectedDefaultProvider by remember(state.settings.defaultProviderId) {
        mutableStateOf(state.settings.defaultProviderId)
    }
    var providerDropdownOpen by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var gitHubTokenDraft by remember(state.gitHubToken) { mutableStateOf(state.gitHubToken) }
    var gitHubOwnerDraft by remember(state.gitHubDefaultOwner) { mutableStateOf(state.gitHubDefaultOwner) }
    var showGitHubToken by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Account Sync", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("settings_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.savedBanner?.let { banner ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = viewModel::clearBanner) { Text("OK") }
                        }
                    }
                }

                // Signed-In Account & Cross-Device Cloud Vault Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ib_coder_logo),
                                contentDescription = "IB CODER badge",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.accountSession.displayName.ifBlank { "IB CODER Account" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = state.accountSession.username.ifBlank { "Google Account • Cross-Device Sync" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        when (val sync = state.cloudSyncStatus) {
                            is CloudVaultSyncStatus.Syncing -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(sync.detail, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            is CloudVaultSyncStatus.Synced -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Synced to your account: ${sync.syncedKeysCount} API key(s), ${sync.customProvidersCount} custom provider(s), ${sync.mcpCount} MCP server(s)" +
                                            if (sync.hasGitHubToken) ", GitHub token" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            is CloudVaultSyncStatus.Error -> {
                                Text(
                                    text = sync.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            CloudVaultSyncStatus.SignedOut -> {
                                Text(
                                    text = "Sign in on any device with the same Google account to restore keys and tokens.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.syncAccountVaultNow() },
                                enabled = state.cloudSyncStatus !is CloudVaultSyncStatus.Syncing,
                                modifier = Modifier.testTag("settings_cloud_sync_now_button")
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync Now")
                            }
                            OutlinedButton(
                                onClick = { viewModel.signOutAccount(context) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("settings_sign_out_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }

                // Providers & API Keys Entry Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Providers & Custom APIs", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Find API URLs, add your own custom providers, manage keys, & fetch /models",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onOpenProviders,
                            modifier = Modifier.testTag("settings_open_providers_button")
                        ) {
                            Text("Manage")
                        }
                    }
                }

                // MCP Servers Entry Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("MCP Servers", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${state.mcpEnabledCount} enabled • ${state.mcpToolCount} tool(s) available to the agent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onOpenMcpServers,
                            modifier = Modifier.testTag("settings_open_mcp_button")
                        ) {
                            Text("Connect")
                        }
                    }
                }

                // GitHub Integration Card
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
                            Icon(
                                Icons.Rounded.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("GitHub & APK Build Integration", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Push workspace projects to GitHub and build debug APKs via GitHub Actions without Play Store signing keys.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = gitHubTokenDraft,
                            onValueChange = { gitHubTokenDraft = it },
                            label = { Text("GitHub Personal Access Token") },
                            placeholder = { Text("ghp_... or github_pat_...") },
                            singleLine = true,
                            visualTransformation = if (showGitHubToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                androidx.compose.material3.IconButton(onClick = { showGitHubToken = !showGitHubToken }) {
                                    Icon(
                                        if (showGitHubToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showGitHubToken) "Hide token" else "Show token"
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_github_token_input")
                        )
                        OutlinedTextField(
                            value = gitHubOwnerDraft,
                            onValueChange = { gitHubOwnerDraft = it },
                            label = { Text("Default GitHub Owner / Username") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_github_owner_input")
                        )
                        state.gitHubStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (msg.startsWith("Error:")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.saveGitHubConfig(gitHubTokenDraft, gitHubOwnerDraft, verify = true)
                                },
                                enabled = !state.gitHubVerifying,
                                modifier = Modifier.testTag("settings_github_verify_button")
                            ) {
                                Text(if (state.gitHubVerifying) "Verifying..." else "Save & Verify")
                            }
                            OutlinedButton(
                                onClick = onOpenGitHub,
                                modifier = Modifier.testTag("settings_open_github_hub_button")
                            ) {
                                Text("GitHub Hub")
                            }
                        }
                    }
                }

                // Theme Card
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
                        Text("Appearance", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.settings.themeMode == ThemeMode.DARK,
                                onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                label = { Text("Dark (Default)") },
                                modifier = Modifier.testTag("theme_dark_chip")
                            )
                            FilterChip(
                                selected = state.settings.themeMode == ThemeMode.LIGHT,
                                onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                label = { Text("Light") },
                                modifier = Modifier.testTag("theme_light_chip")
                            )
                            FilterChip(
                                selected = state.settings.themeMode == ThemeMode.SYSTEM,
                                onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                label = { Text("System") },
                                modifier = Modifier.testTag("theme_system_chip")
                            )
                        }
                    }
                }

                // Default Provider & Model Card
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
                        Text("Default Provider & Model", style = MaterialTheme.typography.titleMedium)

                        val currentProvMeta = state.providers.firstOrNull { it.id == selectedDefaultProvider }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = currentProvMeta?.displayName ?: selectedDefaultProvider,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Default Provider") },
                                trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { providerDropdownOpen = true }
                            )
                            DropdownMenu(
                                expanded = providerDropdownOpen,
                                onDismissRequest = { providerDropdownOpen = false },
                                modifier = Modifier.heightIn(max = 320.dp)
                            ) {
                                state.providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov.displayName) },
                                        onClick = {
                                            selectedDefaultProvider = prov.id
                                            defaultModelDraft = state.providerConfigs[prov.id]?.selectedModel.orEmpty()
                                            providerDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = defaultModelDraft,
                            onValueChange = { defaultModelDraft = it },
                            label = { Text("Default Model ID") },
                            placeholder = { Text("e.g. model ID from Provider Settings") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_default_model_input"),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )

                        Button(
                            onClick = {
                                viewModel.setDefaultProviderAndModel(selectedDefaultProvider, defaultModelDraft)
                            },
                            modifier = Modifier.testTag("settings_save_default_button")
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Default")
                        }
                    }
                }

                // Editable System Prompt Card
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
                        Text("Agent System Prompt", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = promptDraft,
                            onValueChange = { promptDraft = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 300.dp)
                                .testTag("settings_system_prompt_input"),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.saveSystemPrompt(promptDraft) },
                                modifier = Modifier.testTag("settings_save_prompt_button")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Prompt")
                            }
                            OutlinedButton(
                                onClick = viewModel::resetSystemPrompt,
                                modifier = Modifier.testTag("settings_reset_prompt_button")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Reset Default")
                            }
                        }
                    }
                }

                // Privacy Policy & Version Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.PrivacyTip,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Privacy & Security", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) • ${BuildConfig.APPLICATION_ID}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.testTag("settings_privacy_button")
                        ) {
                            Text("Read Policy")
                        }
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy — IB CODER") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = """
• API Keys Stay On-Device: All API keys are encrypted via Android Keystore (AES-256-GCM) and stored only in app-private storage, excluded from cloud backups and device transfers. Keys are never logged.

• Direct Provider Transmission: Prompts, messages, and tool-accessed workspace files are transmitted directly from your device solely to the AI provider endpoint you select. IB CODER operates no developer backend, analytics, or telemetry servers.

• Free-Tier Provider Notice: Some free-tier providers may log, retain, review, or use prompts and responses for abuse monitoring or model training. Review the chosen provider's privacy terms before sending sensitive code, or use a local endpoint such as Ollama.
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) { Text("Close") }
            }
        )
    }
}
