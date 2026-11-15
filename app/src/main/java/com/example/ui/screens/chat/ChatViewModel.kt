package com.example.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.agent.CodingAgentOrchestrator
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ChatRepository
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatSession
import com.example.domain.model.PendingDeletionConfirmation
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.ToolSupportStatus
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class ProviderOptionUi(
    val metadata: ProviderMetadata,
    val config: ProviderConfig,
    val hasKey: Boolean
)

data class ChatUiState(
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val streamingMessage: ChatMessageUi? = null,
    val isGenerating: Boolean = false,
    val activeProject: WorkspaceProject? = null,
    val allProjects: List<WorkspaceProject> = emptyList(),
    val selectedProviderId: String = "coarena",
    val selectedModelId: String = "",
    val providerOptions: List<ProviderOptionUi> = emptyList(),
    val toolSupportStatus: ToolSupportStatus = ToolSupportStatus.SUPPORTED,
    val pendingDeletion: PendingDeletionConfirmation? = null,
    val errorBanner: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
    private val orchestrator: CodingAgentOrchestrator,
    private val chatRepository: ChatRepository,
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore,
    private val providerRegistry: ProviderRegistry
) : ViewModel() {

    private val streamingMessage = MutableStateFlow<ChatMessageUi?>(null)
    private val isGenerating = MutableStateFlow(false)
    private val selectedProviderOverride = MutableStateFlow<String?>(null)
    private val selectedModelOverride = MutableStateFlow<String?>(null)
    private val pendingDeletion = MutableStateFlow<PendingDeletionConfirmation?>(null)
    private val errorBanner = MutableStateFlow<String?>(null)

    private var activeGenerationJob: Job? = null
    private var deletionDeferred: CompletableDeferred<Boolean>? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sessionsFlow = chatRepository.observeSessions()
            val activeMessagesFlow = container.selectedSessionId.flatMapLatest { id ->
                if (id == null) flowOf(emptyList()) else chatRepository.observeMessages(id)
            }

            val sessionBundleFlow = combine(
                container.selectedSessionId,
                sessionsFlow,
                activeMessagesFlow,
                streamingMessage,
                isGenerating
            ) { sessionId, sessions, msgs, streaming, generating ->
                SessionBundle(
                    session = sessions.firstOrNull { it.id == sessionId },
                    messages = msgs,
                    streaming = streaming,
                    generating = generating
                )
            }
            val workspaceBundleFlow = combine(
                settingsDataStore.activeProjectIdFlow,
                workspaceManager.observeProjects(),
                settingsDataStore.settingsFlow,
                settingsDataStore.providerConfigsFlow
            ) { activeProjId, projects, settings, configs ->
                WorkspaceBundle(
                    activeProject = projects.firstOrNull { it.id == activeProjId } ?: projects.firstOrNull(),
                    projects = projects,
                    defaultProviderId = settings.defaultProviderId,
                    defaultModelId = settings.defaultModelId,
                    configs = configs
                )
            }
            val overrideBundleFlow = combine(
                selectedProviderOverride,
                selectedModelOverride,
                pendingDeletion,
                errorBanner
            ) { provOverride, modelOverride, deletion, err ->
                OverrideBundle(provOverride, modelOverride, deletion, err)
            }

            combine(sessionBundleFlow, workspaceBundleFlow, overrideBundleFlow) { sBundle, wBundle, oBundle ->
                val providerId = oBundle.providerId
                    ?: sBundle.session?.providerId?.takeIf { it.isNotBlank() }
                    ?: wBundle.defaultProviderId

                val adapter = providerRegistry.getAdapter(providerId)
                val config = wBundle.configs[providerId] ?: adapter?.defaultConfig()
                val modelId = oBundle.modelId
                    ?: sBundle.session?.modelId?.takeIf { it.isNotBlank() }
                    ?: config?.selectedModel?.takeIf { it.isNotBlank() }
                    ?: wBundle.defaultModelId

                val options = providerRegistry.adapters.map { ad ->
                    val cfg = wBundle.configs[ad.metadata.id] ?: ad.defaultConfig()
                    ProviderOptionUi(
                        metadata = ad.metadata,
                        config = cfg,
                        hasKey = !ad.metadata.requiresApiKey || cfg.enabled
                    )
                }

                val toolStatus = adapter?.modelToolSupport(modelId) ?: ToolSupportStatus.UNKNOWN

                ChatUiState(
                    currentSession = sBundle.session,
                    messages = sBundle.messages,
                    streamingMessage = sBundle.streaming,
                    isGenerating = sBundle.generating,
                    activeProject = wBundle.activeProject,
                    allProjects = wBundle.projects,
                    selectedProviderId = providerId,
                    selectedModelId = modelId,
                    providerOptions = options,
                    toolSupportStatus = toolStatus,
                    pendingDeletion = oBundle.deletion,
                    errorBanner = oBundle.error
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun startNewSession() {
        stopGeneration()
        container.selectedSessionId.value = null
        errorBanner.value = null
    }

    fun selectProviderAndModel(providerId: String, modelId: String) {
        selectedProviderOverride.value = providerId
        selectedModelOverride.value = modelId
        errorBanner.value = null
        viewModelScope.launch {
            val adapter = providerRegistry.getAdapter(providerId) ?: return@launch
            val currentCfg = settingsDataStore.getProviderConfig(providerId) ?: adapter.defaultConfig()
            if (modelId.isNotBlank() && currentCfg.selectedModel != modelId) {
                settingsDataStore.saveProviderConfig(currentCfg.copy(selectedModel = modelId))
            }
            container.selectedSessionId.value?.let { sessionId ->
                chatRepository.updateSessionProviderAndModel(sessionId, providerId, modelId)
            }
        }
    }

    fun selectActiveProject(projectId: String) {
        viewModelScope.launch {
            settingsDataStore.setActiveProjectId(projectId)
            container.selectedSessionId.value?.let { sessionId ->
                chatRepository.updateSessionProject(sessionId, projectId)
            }
        }
    }

    fun createProjectAndSelect(name: String) {
        viewModelScope.launch {
            val created = workspaceManager.createProject(name)
            settingsDataStore.setActiveProjectId(created.id)
            container.selectedSessionId.value?.let { sessionId ->
                chatRepository.updateSessionProject(sessionId, created.id)
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isGenerating.value) return

        val currentState = _uiState.value
        val providerId = currentState.selectedProviderId
        val modelId = currentState.selectedModelId.trim()
        if (modelId.isEmpty()) {
            errorBanner.value = "Select or enter a model ID for ${providerRegistry.getAdapter(providerId)?.metadata?.displayName ?: providerId} first."
            return
        }

        errorBanner.value = null
        isGenerating.value = true

        activeGenerationJob = viewModelScope.launch {
            try {
                val projectId = currentState.activeProject?.id
                val sessionId = container.selectedSessionId.value ?: run {
                    val created = chatRepository.createSession(
                        title = trimmed.lineSequence().firstOrNull()?.take(48) ?: "New Chat",
                        projectId = projectId,
                        providerId = providerId,
                        modelId = modelId
                    )
                    container.selectedSessionId.value = created.id
                    created.id
                }

                orchestrator.runAgentTurn(
                    sessionId = sessionId,
                    projectId = projectId,
                    providerId = providerId,
                    modelId = modelId,
                    userText = trimmed,
                    onStreamingMessageUpdated = { msg -> streamingMessage.value = msg },
                    confirmDeletion = { request ->
                        val deferred = CompletableDeferred<Boolean>()
                        deletionDeferred = deferred
                        pendingDeletion.value = request
                        try {
                            deferred.await()
                        } finally {
                            pendingDeletion.value = null
                            deletionDeferred = null
                        }
                    }
                )
            } catch (_: CancellationException) {
                // Stopped cleanly by user
            } catch (e: Exception) {
                errorBanner.value = e.message ?: "Failed to complete request."
            } finally {
                isGenerating.value = false
                streamingMessage.value = null
            }
        }
    }

    fun stopGeneration() {
        deletionDeferred?.complete(false)
        pendingDeletion.value = null
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        isGenerating.value = false
        streamingMessage.value = null
    }

    fun resolveDeletionConfirmation(approved: Boolean) {
        deletionDeferred?.complete(approved)
        pendingDeletion.value = null
    }

    fun dismissError() {
        errorBanner.value = null
    }

    private data class SessionBundle(
        val session: ChatSession?,
        val messages: List<ChatMessageUi>,
        val streaming: ChatMessageUi?,
        val generating: Boolean
    )

    private data class WorkspaceBundle(
        val activeProject: WorkspaceProject?,
        val projects: List<WorkspaceProject>,
        val defaultProviderId: String,
        val defaultModelId: String,
        val configs: Map<String, ProviderConfig>
    )

    private data class OverrideBundle(
        val providerId: String?,
        val modelId: String?,
        val deletion: PendingDeletionConfirmation?,
        val error: String?
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                container = container,
                orchestrator = container.agentOrchestrator,
                chatRepository = container.chatRepository,
                workspaceManager = container.workspaceManager,
                settingsDataStore = container.settingsDataStore,
                providerRegistry = container.providerRegistry
            ) as T
        }
    }
}
