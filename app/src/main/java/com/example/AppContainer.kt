package com.example

import android.content.Context
import com.example.data.agent.AgentToolExecutor
import com.example.data.agent.CodingAgentOrchestrator
import com.example.data.local.db.AppDatabase
import com.example.data.network.GitHubPushClient
import com.example.data.network.GitHubSyncManager
import com.example.data.network.InternetToolClient
import com.example.data.network.McpClient
import com.example.data.network.McpManager
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.CloudAccountVaultRepository
import com.example.data.repository.ChatRepository
import com.example.data.security.KeystoreCryptoManager
import com.example.data.workspace.WorkspaceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val selectedSessionId = MutableStateFlow<String?>(null)

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    // No HTTP logging interceptor so API keys and prompts are never logged.
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val cryptoManager = KeystoreCryptoManager()

    val settingsDataStore = SettingsDataStore(
        context = appContext,
        cryptoManager = cryptoManager,
        json = json
    )

    val cloudAccountVaultRepository = CloudAccountVaultRepository(
        context = appContext,
        settingsDataStore = settingsDataStore,
        json = json
    )

    val database: AppDatabase = AppDatabase.getInstance(appContext)

    val workspaceManager = WorkspaceManager(
        context = appContext,
        projectDao = database.projectDao()
    )

    val providerRegistry = ProviderRegistry(
        okHttpClient = okHttpClient,
        json = json
    )

    val internetToolClient = InternetToolClient(
        okHttpClient = okHttpClient,
        json = json
    )

    val gitHubPushClient = GitHubPushClient(
        okHttpClient = okHttpClient,
        workspaceManager = workspaceManager,
        json = json
    )

    val gitHubSyncManager = GitHubSyncManager(
        settingsDataStore = settingsDataStore,
        gitHubPushClient = gitHubPushClient
    )

    val mcpClient = McpClient(
        okHttpClient = okHttpClient,
        json = json
    )

    val mcpManager = McpManager(
        settingsDataStore = settingsDataStore,
        mcpClient = mcpClient,
        json = json
    )

    val toolExecutor = AgentToolExecutor(
        workspaceManager = workspaceManager,
        json = json,
        internetToolClient = internetToolClient
    )

    val chatRepository = ChatRepository(
        sessionDao = database.chatSessionDao(),
        messageDao = database.chatMessageDao()
    )

    val agentOrchestrator = CodingAgentOrchestrator(
        providerRegistry = providerRegistry,
        settingsDataStore = settingsDataStore,
        chatRepository = chatRepository,
        toolExecutor = toolExecutor,
        workspaceManager = workspaceManager,
        mcpManager = mcpManager,
        gitHubSyncManager = gitHubSyncManager
    )

    init {
        appScope.launch {
            settingsDataStore.customProvidersFlow.collect { customDefs ->
                providerRegistry.syncCustomProviders(customDefs)
            }
        }
    }
}
