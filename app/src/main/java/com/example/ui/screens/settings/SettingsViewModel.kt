package com.example.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
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
    val providers: List<ProviderMetadata> = emptyList(),
    val providerConfigs: Map<String, ProviderConfig> = emptyMap(),
    val savedBanner: String? = null
)

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val providerRegistry: ProviderRegistry
) : ViewModel() {

    private val savedBanner = MutableStateFlow<String?>(null)
    private val _uiState = MutableStateFlow(
        SettingsUiState(providers = providerRegistry.adapters.map { it.metadata })
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsDataStore.settingsFlow,
                settingsDataStore.providerConfigsFlow,
                savedBanner
            ) { settings, configs, banner ->
                SettingsUiState(
                    settings = settings,
                    providers = providerRegistry.adapters.map { it.metadata },
                    providerConfigs = configs,
                    savedBanner = banner
                )
            }.collect { state ->
                _uiState.value = state
            }
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

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsDataStore = container.settingsDataStore,
                providerRegistry = container.providerRegistry
            ) as T
        }
    }
}
