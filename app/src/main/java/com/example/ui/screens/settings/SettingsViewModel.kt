package com.example.ui.screens.settings

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.GitHubPushClient
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.CloudAccountVaultRepository
import com.example.data.remote.CloudVaultSyncStatus
import com.example.domain.model.AppAccountSession
import com.example.domain.model.AppSettings
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val accountSession: AppAccountSession = AppAccountSession(),
    val cloudSyncStatus: CloudVaultSyncStatus = CloudVaultSyncStatus.SignedOut,
    val providers: List<ProviderMetadata> = emptyList(),
    val providerConfigs: Map<String, ProviderConfig> = emptyMap(),
    val mcpEnabledCount: Int = 0,
    val mcpToolCount: Int = 0,
    val gitHubToken: String = "",
    val gitHubDefaultOwner: String = "",
    val gitHubStatusMessage: String? = null,
    val gitHubVerifying: Boolean = false,
    val savedBanner: String? = null
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val providerRegistry: ProviderRegistry,
    private val gitHubPushClient: GitHubPushClient,
    private val cloudAccountVaultRepository: CloudAccountVaultRepository
) : ViewModel() {

    private val savedBanner = MutableStateFlow<String?>(null)
    private val gitHubTokenState = MutableStateFlow("")
    private val gitHubStatusState = MutableStateFlow<String?>(null)
    private val gitHubVerifyingState = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(
        SettingsUiState(providers = providerRegistry.allAdapters().map { it.metadata })
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.hasGitHubTokenFlow.collect {
                gitHubTokenState.value = settingsDataStore.getGitHubToken()
            }
        }

        viewModelScope.launch {
            val baseFlow = combine(
                settingsDataStore.settingsFlow,
                settingsDataStore.providerConfigsFlow,
                settingsDataStore.mcpServersFlow,
                savedBanner
            ) { settings, configs, mcpServers, banner ->
                val enabledServers = mcpServers.filter { it.enabled }
                BaseBundle(
                    settings = settings,
                    configs = configs,
                    mcpEnabledCount = enabledServers.size,
                    mcpToolCount = enabledServers.sumOf { it.discoveredTools.size },
                    banner = banner
                )
            }

            val gitHubFlow = combine(
                gitHubTokenState,
                settingsDataStore.githubDefaultOwnerFlow,
                gitHubStatusState,
                gitHubVerifyingState
            ) { token, owner, status, verifying ->
                GitHubBundle(token, owner, status, verifying)
            }

            val accountFlow = combine(
                settingsDataStore.appAccountSessionFlow,
                cloudAccountVaultRepository.syncStatus
            ) { session, syncStatus ->
                session to syncStatus
            }

            combine(baseFlow, gitHubFlow, accountFlow, providerRegistry.customAdaptersFlow) { b, gh, acct, _ ->
                SettingsUiState(
                    settings = b.settings,
                    accountSession = acct.first,
                    cloudSyncStatus = acct.second,
                    providers = providerRegistry.allAdapters().map { it.metadata },
                    providerConfigs = b.configs,
                    mcpEnabledCount = b.mcpEnabledCount,
                    mcpToolCount = b.mcpToolCount,
                    gitHubToken = gh.token,
                    gitHubDefaultOwner = gh.owner,
                    gitHubStatusMessage = gh.status,
                    gitHubVerifying = gh.verifying,
                    savedBanner = b.banner
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun syncAccountVaultNow() {
        viewModelScope.launch {
            cloudAccountVaultRepository.syncNow().fold(
                onSuccess = {
                    savedBanner.value = "Account vault synced across devices."
                },
                onFailure = { err ->
                    savedBanner.value = "Sync failed: ${err.message ?: "Check network connection."}"
                }
            )
        }
    }

    fun signOutAccount(context: Context) {
        viewModelScope.launch {
            val cm = CredentialManager.create(context)
            cloudAccountVaultRepository.signOutAccount(cm, clearLocalVaultSecrets = true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setDefaultProviderAndModel(providerId: String, modelId: String) {
        viewModelScope.launch {
            settingsDataStore.setDefaultProviderAndModel(providerId, modelId.trim())
            savedBanner.value = "Default provider updated."
        }
    }

    fun saveGitHubConfig(token: String, defaultOwner: String, verify: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveGitHubToken(token)
            settingsDataStore.saveGitHubDefaultOwner(defaultOwner)
            gitHubTokenState.value = token.trim()

            if (!verify || token.isBlank()) {
                gitHubStatusState.value = if (token.isBlank()) "GitHub token cleared." else "GitHub settings saved."
                return@launch
            }

            gitHubVerifyingState.value = true
            gitHubStatusState.value = null
            gitHubPushClient.verifyToken(token).fold(
                onSuccess = { profile ->
                    if (defaultOwner.isBlank()) {
                        settingsDataStore.saveGitHubDefaultOwner(profile.login)
                    }
                    gitHubStatusState.value = "Connected as @${profile.login}."
                },
                onFailure = { err ->
                    gitHubStatusState.value = "Error: ${err.message ?: "Token check failed."}"
                }
            )
            gitHubVerifyingState.value = false
        }
    }

    fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            settingsDataStore.setSystemPrompt(prompt)
            savedBanner.value = "System prompt saved."
        }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            settingsDataStore.resetSystemPrompt()
            savedBanner.value = "System prompt reset to default."
        }
    }

    fun clearBanner() {
        savedBanner.value = null
    }

    private data class BaseBundle(
        val settings: AppSettings,
        val configs: Map<String, ProviderConfig>,
        val mcpEnabledCount: Int,
        val mcpToolCount: Int,
        val banner: String?
    )

    private data class GitHubBundle(
        val token: String,
        val owner: String,
        val status: String?,
        val verifying: Boolean
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsDataStore = container.settingsDataStore,
                providerRegistry = container.providerRegistry,
                gitHubPushClient = container.gitHubPushClient,
                cloudAccountVaultRepository = container.cloudAccountVaultRepository
            ) as T
        }
    }
}
