package com.example.ui.screens.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.InternetToolClient
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.ConnectionTestResult
import com.example.domain.model.CustomProviderDefinition
import com.example.domain.model.ProviderApiDiscoveryResult
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.ProviderModel
import com.example.domain.model.ToolSupportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class ProviderFilter {
    ALL,
    FREE_TIER,
    PAID_LOCAL,
    CUSTOM,
    ENABLED
}

data class ProviderCardUiState(
    val metadata: ProviderMetadata,
    val config: ProviderConfig,
    val apiKey: String = "",
    val models: List<ProviderModel> = emptyList(),
    val isFetchingModels: Boolean = false,
    val modelsError: String? = null,
    val isManualModelEntry: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val toolSupportStatus: ToolSupportStatus = ToolSupportStatus.SUPPORTED,
    val isUserCreatedCustom: Boolean = false
)

data class ProvidersUiState(
    val cards: List<ProviderCardUiState> = emptyList(),
    val totalProviderCount: Int = 0,
    val searchQuery: String = "",
    val filter: ProviderFilter = ProviderFilter.ALL,
    val expandedProviderId: String? = "coarena",
    val apiDiscoveryQuery: String = "",
    val apiDiscoveryResults: List<ProviderApiDiscoveryResult> = InternetToolClient.KNOWN_PROVIDER_APIS.take(6),
    val isSearchingApiUrl: Boolean = false
)

class ProvidersViewModel(
    private val providerRegistry: ProviderRegistry,
    private val settingsDataStore: SettingsDataStore,
    private val internetToolClient: InternetToolClient
) : ViewModel() {

    private val apiKeysState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val liveModelsState = MutableStateFlow<Map<String, List<ProviderModel>>>(emptyMap())
    private val fetchingState = MutableStateFlow<Set<String>>(emptySet())
    private val modelsErrorState = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val manualModeState = MutableStateFlow<Set<String>>(emptySet())
    private val testingState = MutableStateFlow<Set<String>>(emptySet())
    private val testResultsState = MutableStateFlow<Map<String, ConnectionTestResult?>>(emptyMap())

    private val searchQuery = MutableStateFlow("")
    private val filterState = MutableStateFlow(ProviderFilter.ALL)
    private val expandedId = MutableStateFlow<String?>("coarena")

    private val discoveryQueryState = MutableStateFlow("")
    private val discoveryResultsState = MutableStateFlow(InternetToolClient.KNOWN_PROVIDER_APIS.take(6))
    private val searchingApiState = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val builtInIds = providerRegistry.adapters.map { it.metadata.id }
            val customIds = settingsDataStore.getCustomProviders().map { it.id }
            apiKeysState.value = settingsDataStore.getAllApiKeys(builtInIds + customIds)
        }

        viewModelScope.launch {
            val leftFlow = combine(
                settingsDataStore.providerConfigsFlow,
                apiKeysState,
                liveModelsState,
                fetchingState,
                modelsErrorState
            ) { configs, keys, modelsMap, fetching, modelErrors ->
                QuadState(configs, keys, modelsMap, fetching, modelErrors)
            }
            val rightFlow = combine(
                manualModeState,
                testingState,
                testResultsState,
                searchQuery,
                filterState
            ) { manual, testing, tests, query, filter ->
                FilterBundle(manual, testing, tests, query, filter)
            }
            val discoveryFlow = combine(
                providerRegistry.customAdaptersFlow,
                discoveryQueryState,
                discoveryResultsState,
                searchingApiState
            ) { customAdapters, dQuery, dResults, dSearching ->
                DiscoveryBundle(customAdapters.map { it.metadata.id }.toSet(), dQuery, dResults, dSearching)
            }

            combine(leftFlow, rightFlow, expandedId, discoveryFlow) { left, right, expanded, discovery ->
                val allAdapters = providerRegistry.allAdapters()
                val allCards = allAdapters.map { adapter ->
                    val id = adapter.metadata.id
                    val config = left.configs[id] ?: adapter.defaultConfig()
                    val cachedModels = left.modelsMap[id] ?: config.fetchedModels.map { modelId ->
                        ProviderModel(
                            id = modelId,
                            displayName = modelId,
                            supportsToolCalling = adapter.modelToolSupport(modelId)
                        )
                    }
                    val manual = !adapter.metadata.supportsModelsEndpoint || id in right.manual
                    val toolSupport = adapter.modelToolSupport(config.selectedModel)
                    ProviderCardUiState(
                        metadata = adapter.metadata,
                        config = config,
                        apiKey = left.keys[id].orEmpty(),
                        models = cachedModels,
                        isFetchingModels = id in left.fetching,
                        modelsError = left.modelErrors[id],
                        isManualModelEntry = manual,
                        isTesting = id in right.testing,
                        testResult = right.tests[id],
                        toolSupportStatus = toolSupport,
                        isUserCreatedCustom = id in discovery.customIds
                    )
                }

                val filtered = allCards.filter { card ->
                    val matchesFilter = when (right.filter) {
                        ProviderFilter.ALL -> true
                        ProviderFilter.FREE_TIER -> card.metadata.category == ProviderCategory.FREE_TIER
                        ProviderFilter.PAID_LOCAL -> card.metadata.category == ProviderCategory.PAID_POPULAR ||
                            card.metadata.category == ProviderCategory.LOCAL
                        ProviderFilter.CUSTOM -> card.metadata.category == ProviderCategory.CUSTOM
                        ProviderFilter.ENABLED -> card.config.enabled
                    }
                    val q = right.query.trim()
                    val matchesQuery = q.isEmpty() ||
                        card.metadata.displayName.contains(q, ignoreCase = true) ||
                        card.metadata.description.contains(q, ignoreCase = true) ||
                        card.config.baseUrl.contains(q, ignoreCase = true) ||
                        card.config.selectedModel.contains(q, ignoreCase = true)
                    matchesFilter && matchesQuery
                }

                ProvidersUiState(
                    cards = filtered,
                    totalProviderCount = allAdapters.size,
                    searchQuery = right.query,
                    filter = right.filter,
                    expandedProviderId = expanded,
                    apiDiscoveryQuery = discovery.query,
                    apiDiscoveryResults = discovery.results,
                    isSearchingApiUrl = discovery.searching
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: ProviderFilter) {
        filterState.value = filter
    }

    fun toggleExpanded(providerId: String) {
        expandedId.value = if (expandedId.value == providerId) null else providerId
    }

    fun findApiEndpoint(query: String) {
        discoveryQueryState.value = query
        viewModelScope.launch {
            searchingApiState.value = true
            val found = internetToolClient.discoverApiEndpoint(query)
            discoveryResultsState.value = found
            searchingApiState.value = false
        }
    }

    fun addCustomProvider(
        name: String,
        baseUrl: String,
        apiKey: String,
        defaultModel: String,
        docsUrl: String?,
        requiresApiKey: Boolean
    ) {
        val cleanName = name.trim().ifBlank { "Custom Provider" }
        val normalizedUrl = normalizeBaseUrl(baseUrl)
        if (normalizedUrl.isBlank()) return

        val id = "custom_${UUID.randomUUID().toString().take(8)}"
        val cleanModel = defaultModel.trim()
        val def = CustomProviderDefinition(
            id = id,
            displayName = cleanName,
            defaultBaseUrl = normalizedUrl,
            description = "Custom OpenAI-compatible API ($normalizedUrl)",
            signupUrl = docsUrl?.trim()?.takeIf { it.isNotBlank() },
            requiresApiKey = requiresApiKey,
            defaultModel = cleanModel
        )

        viewModelScope.launch {
            settingsDataStore.saveCustomProvider(def)
            val updatedDefs = settingsDataStore.getCustomProviders()
            providerRegistry.syncCustomProviders(updatedDefs)

            val cfg = ProviderConfig(
                providerId = id,
                baseUrl = normalizedUrl,
                selectedModel = cleanModel,
                enabled = true
            )
            settingsDataStore.saveProviderConfig(cfg)
            val cleanKey = apiKey.trim()
            if (cleanKey.isNotEmpty()) {
                apiKeysState.update { it + (id to cleanKey) }
                settingsDataStore.saveApiKey(id, cleanKey)
            }
            expandedId.value = id
            filterState.value = ProviderFilter.ALL
            searchQuery.value = ""

            fetchModels(id)
        }
    }

    fun deleteCustomProvider(providerId: String) {
        if (!providerId.startsWith("custom_")) return
        viewModelScope.launch {
            settingsDataStore.deleteCustomProvider(providerId)
            val updatedDefs = settingsDataStore.getCustomProviders()
            providerRegistry.syncCustomProviders(updatedDefs)
            apiKeysState.update { it - providerId }
            if (expandedId.value == providerId) expandedId.value = null
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    fun updateApiKey(providerId: String, newKey: String) {
        apiKeysState.update { it + (providerId to newKey) }
        viewModelScope.launch {
            settingsDataStore.saveApiKey(providerId, newKey)
        }
    }

    fun updateConfig(providerId: String, transform: (ProviderConfig) -> ProviderConfig) {
        viewModelScope.launch {
            val adapter = providerRegistry.getAdapter(providerId) ?: return@launch
            val current = settingsDataStore.getProviderConfig(providerId) ?: adapter.defaultConfig()
            val updated = transform(current)
            settingsDataStore.saveProviderConfig(updated)
            if (updated.enabled && updated.selectedModel.isNotBlank()) {
                val settings = settingsDataStore.getSettings()
                if (settings.defaultModelId.isBlank()) {
                    settingsDataStore.setDefaultProviderAndModel(providerId, updated.selectedModel)
                }
            }
        }
    }

    fun toggleManualModelEntry(providerId: String) {
        manualModeState.update { set ->
            if (providerId in set) set - providerId else set + providerId
        }
    }

    fun fetchModels(providerId: String) {
        viewModelScope.launch {
            val adapter = providerRegistry.getAdapter(providerId) ?: return@launch
            val config = settingsDataStore.getProviderConfig(providerId) ?: adapter.defaultConfig()
            val key = apiKeysState.value[providerId] ?: settingsDataStore.getApiKey(providerId)

            fetchingState.update { it + providerId }
            modelsErrorState.update { it - providerId }

            val result = adapter.fetchModels(config, key)
            fetchingState.update { it - providerId }

            result.onSuccess { models ->
                liveModelsState.update { it + (providerId to models) }
                manualModeState.update { it - providerId }
                val updatedModel = config.selectedModel.ifBlank { models.firstOrNull()?.id.orEmpty() }
                settingsDataStore.saveProviderConfig(
                    config.copy(
                        selectedModel = updatedModel,
                        fetchedModels = models.map { it.id }
                    )
                )
            }.onFailure { err ->
                modelsErrorState.update {
                    it + (providerId to (err.message ?: "Could not fetch /models; enter model ID manually."))
                }
                manualModeState.update { it + providerId }
            }
        }
    }

    fun testConnection(providerId: String) {
        viewModelScope.launch {
            val adapter = providerRegistry.getAdapter(providerId) ?: return@launch
            val config = settingsDataStore.getProviderConfig(providerId) ?: adapter.defaultConfig()
            val key = apiKeysState.value[providerId] ?: settingsDataStore.getApiKey(providerId)

            testingState.update { it + providerId }
            testResultsState.update { it - providerId }

            val result = adapter.testConnection(config, key)
            testingState.update { it - providerId }
            testResultsState.update { it + (providerId to result) }
        }
    }

    private data class QuadState(
        val configs: Map<String, ProviderConfig>,
        val keys: Map<String, String>,
        val modelsMap: Map<String, List<ProviderModel>>,
        val fetching: Set<String>,
        val modelErrors: Map<String, String?>
    )

    private data class FilterBundle(
        val manual: Set<String>,
        val testing: Set<String>,
        val tests: Map<String, ConnectionTestResult?>,
        val query: String,
        val filter: ProviderFilter
    )

    private data class DiscoveryBundle(
        val customIds: Set<String>,
        val query: String,
        val results: List<ProviderApiDiscoveryResult>,
        val searching: Boolean
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProvidersViewModel(
                providerRegistry = container.providerRegistry,
                settingsDataStore = container.settingsDataStore,
                internetToolClient = container.internetToolClient
            ) as T
        }
    }
}
