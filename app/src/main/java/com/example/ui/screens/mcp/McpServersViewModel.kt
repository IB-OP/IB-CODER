package com.example.ui.screens.mcp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.McpManager
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.McpServerConfig
import com.example.domain.model.McpTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

data class McpServerCardUi(
    val config: McpServerConfig,
    val authToken: String,
    val isSyncing: Boolean = false,
    val statusBanner: String? = null
)

data class McpServersUiState(
    val servers: List<McpServerCardUi> = emptyList(),
    val globalError: String? = null
)

class McpServersViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val mcpManager: McpManager
) : ViewModel() {

    private val authTokensState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val syncingIdsState = MutableStateFlow<Set<String>>(emptySet())
    private val statusBannersState = MutableStateFlow<Map<String, String>>(emptyMap())
    private val globalErrorState = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(McpServersUiState())
    val uiState: StateFlow<McpServersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.mcpServersFlow.collect { list ->
                val missing = list.map { it.id }.filterNot { authTokensState.value.containsKey(it) }
                if (missing.isNotEmpty()) {
                    val loaded = settingsDataStore.getAllMcpAuthTokens(missing)
                    authTokensState.value = authTokensState.value + loaded
                }
            }
        }

        viewModelScope.launch {
            val baseFlow = combine(
                settingsDataStore.mcpServersFlow,
                authTokensState,
                syncingIdsState,
                statusBannersState
            ) { servers, tokens, syncingIds, banners ->
                servers.map { srv ->
                    McpServerCardUi(
                        config = srv,
                        authToken = tokens[srv.id].orEmpty(),
                        isSyncing = srv.id in syncingIds,
                        statusBanner = banners[srv.id]
                    )
                }
            }

            combine(baseFlow, globalErrorState) { cards, globalErr ->
                McpServersUiState(servers = cards, globalError = globalErr)
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun upsertServer(
        existingId: String?,
        name: String,
        endpointUrl: String,
        transport: McpTransport,
        authHeaderName: String,
        authToken: String,
        syncImmediately: Boolean = true
    ) {
        val cleanName = name.trim().ifBlank { "MCP Server" }
        val cleanUrl = endpointUrl.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            globalErrorState.value = "Server URL must start with http:// or https://"
            return
        }

        globalErrorState.value = null
        val id = existingId ?: UUID.randomUUID().toString()

        viewModelScope.launch {
            val existing = settingsDataStore.getMcpServers().firstOrNull { it.id == id }
            val updated = if (existing != null) {
                existing.copy(
                    name = cleanName,
                    endpointUrl = cleanUrl,
                    transport = transport,
                    authHeaderName = authHeaderName.trim().ifBlank { "Authorization" }
                )
            } else {
                McpServerConfig(
                    id = id,
                    name = cleanName,
                    endpointUrl = cleanUrl,
                    transport = transport,
                    authHeaderName = authHeaderName.trim().ifBlank { "Authorization" },
                    enabled = true
                )
            }

            settingsDataStore.saveMcpServer(updated)
            settingsDataStore.saveMcpAuthToken(id, authToken)
            authTokensState.value = authTokensState.value + (id to authToken.trim())

            if (syncImmediately) {
                syncServer(id)
            }
        }
    }

    fun toggleEnabled(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDataStore.getMcpServers().firstOrNull { it.id == serverId } ?: return@launch
            settingsDataStore.saveMcpServer(current.copy(enabled = enabled))
        }
    }

    fun syncServer(serverId: String) {
        viewModelScope.launch {
            syncingIdsState.value = syncingIdsState.value + serverId
            statusBannersState.value = statusBannersState.value - serverId

            val result = mcpManager.syncServer(serverId)
            result.fold(
                onSuccess = { updated ->
                    val count = updated.discoveredTools.size
                    val info = updated.serverInfoName.ifBlank { updated.name }
                    statusBannersState.value = statusBannersState.value +
                        (serverId to "Connected to $info (${updated.protocolVersion}): $count tool(s) available.")
                },
                onFailure = { err ->
                    statusBannersState.value = statusBannersState.value +
                        (serverId to "Error: ${err.message ?: "Could not connect to MCP server."}")
                }
            )
            syncingIdsState.value = syncingIdsState.value - serverId
        }
    }

    fun deleteServer(serverId: String) {
        viewModelScope.launch {
            settingsDataStore.deleteMcpServer(serverId)
            authTokensState.value = authTokensState.value - serverId
            statusBannersState.value = statusBannersState.value - serverId
        }
    }

    fun clearGlobalError() {
        globalErrorState.value = null
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return McpServersViewModel(
                settingsDataStore = container.settingsDataStore,
                mcpManager = container.mcpManager
            ) as T
        }
    }
}
