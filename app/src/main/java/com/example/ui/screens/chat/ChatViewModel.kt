package com.example.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.agent.CodingAgentOrchestrator
import com.example.data.network.GitHubSyncManager
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ChatRepository
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatSession
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.PendingDeletionConfirmation
import com.example.domain.model.PendingDeviceActionConfirmation
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.SessionMode
import com.example.domain.model.SessionPromptConfig
import com.example.domain.model.ToolSupportStatus
import com.example.domain.model.WorkflowFailureAlert
import com.example.domain.model.WorkspaceProject
import com.example.domain.prompt.SystemPromptPresets
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
    val currentSessionPrompt: SessionPromptConfig? = null,
    val messages: List<ChatMessageUi> = emptyList(),
    val streamingMessage: ChatMessageUi? = null,
    val isGenerating: Boolean = false,
    val isCreatingProject: Boolean = false,
    val activeProject: WorkspaceProject? = null,
    val allProjects: List<WorkspaceProject> = emptyList(),
    val selectedProviderId: String = "coarena",
    val selectedModelId: String = "",
    val providerOptions: List<ProviderOptionUi> = emptyList(),
    val toolSupportStatus: ToolSupportStatus = ToolSupportStatus.SUPPORTED,
    val mcpEnabledServerCount: Int = 0,
    val mcpToolCount: Int = 0,
    val isGitHubSignedIn: Boolean = false,
    val defaultGitHubOwner: String = "",
    val availableGitHubRepos: List<GitHubRepositorySummary> = emptyList(),
    val isLoadingGitHubRepos: Boolean = false,
    val activeGitHubConfig: GitHubProjectConfig? = null,
    val activeSyncStatus: GitHubSyncStatus = GitHubSyncStatus.Idle,
    val pendingWorkflowAlert: WorkflowFailureAlert? = null,
    val pendingDeletion: PendingDeletionConfirmation? = null,
    val pendingDeviceAction: PendingDeviceActionConfirmation? = null,
    val errorBanner: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val container: AppContainer,
    private val orchestrator: CodingAgentOrchestrator,
    private val chatRepository: ChatRepository,
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore,
    private val providerRegistry: ProviderRegistry,
    private val gitHubSyncManager: GitHubSyncManager
) : ViewModel() {

    private val streamingMessage = MutableStateFlow<ChatMessageUi?>(null)
    private val isGenerating = MutableStateFlow(false)
    private val isCreatingProject = MutableStateFlow(false)
    private val selectedProviderOverride = MutableStateFlow<String?>(null)
    private val selectedModelOverride = MutableStateFlow<String?>(null)
    private val pendingDeletion = MutableStateFlow<PendingDeletionConfirmation?>(null)
    private val pendingDeviceAction = MutableStateFlow<PendingDeviceActionConfirmation?>(null)
    private val pendingWorkflowAlert = MutableStateFlow<WorkflowFailureAlert?>(null)
    private val availableGitHubRepos = MutableStateFlow<List<GitHubRepositorySummary>>(emptyList())
    private val isLoadingGitHubRepos = MutableStateFlow(false)
    private val errorBanner = MutableStateFlow<String?>(null)

    private var activeGenerationJob: Job? = null
    private var deletionDeferred: CompletableDeferred<Boolean>? = null
    private var deviceActionDeferred: CompletableDeferred<Boolean>? = null

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
            val confirmationFlow = combine(pendingDeletion, pendingDeviceAction) { del, dev -> del to dev }
            val overrideBundleFlow = combine(
                selectedProviderOverride,
                selectedModelOverride,
                confirmationFlow,
                errorBanner,
                settingsDataStore.mcpServersFlow
            ) { provOverride, modelOverride, confPair, err, mcpServers ->
                val enabledMcp = mcpServers.filter { it.enabled }
                OverrideBundle(
                    providerId = provOverride,
                    modelId = modelOverride,
                    deletion = confPair.first,
                    deviceAction = confPair.second,
                    error = err,
                    mcpServerCount = enabledMcp.size,
                    mcpToolCount = enabledMcp.sumOf { it.discoveredTools.size }
                )
            }

            val githubAuthFlow = combine(
                settingsDataStore.hasGitHubTokenFlow,
                settingsDataStore.githubDefaultOwnerFlow,
                availableGitHubRepos,
                isLoadingGitHubRepos,
                isCreatingProject
            ) { signedIn, defOwner, repos, loadingRepos, creating ->
                GitHubCatalogBundle(signedIn, defOwner, repos, loadingRepos, creating)
            }

            val githubCatalogBundleFlow = combine(
                githubAuthFlow,
                settingsDataStore.sessionPromptConfigsFlow,
                providerRegistry.customAdaptersFlow
            ) { catalog, promptMap, _ ->
                catalog to promptMap
            }

            val githubBundleFlow = combine(
                settingsDataStore.githubProjectConfigsFlow,
                gitHubSyncManager.syncStatuses,
                pendingWorkflowAlert,
                githubCatalogBundleFlow
            ) { ghConfigs, syncStatuses, alert, catalogPair ->
                GitHubFullBundle(ghConfigs, syncStatuses, alert, catalogPair.first, catalogPair.second)
            }

            combine(sessionBundleFlow, workspaceBundleFlow, overrideBundleFlow, githubBundleFlow) { sBundle, wBundle, oBundle, ghBundle ->
                val providerId = oBundle.providerId
                    ?: sBundle.session?.providerId?.takeIf { it.isNotBlank() }
                    ?: wBundle.defaultProviderId

                val adapter = providerRegistry.getAdapter(providerId)
                val config = wBundle.configs[providerId] ?: adapter?.defaultConfig()
                val modelId = oBundle.modelId
                    ?: sBundle.session?.modelId?.takeIf { it.isNotBlank() }
                    ?: config?.selectedModel?.takeIf { it.isNotBlank() }
                    ?: wBundle.defaultModelId

                val options = providerRegistry.allAdapters().map { ad ->
                    val cfg = wBundle.configs[ad.metadata.id] ?: ad.defaultConfig()
                    ProviderOptionUi(
                        metadata = ad.metadata,
                        config = cfg,
                        hasKey = !ad.metadata.requiresApiKey || cfg.enabled
                    )
                }

                val toolStatus = adapter?.modelToolSupport(modelId) ?: ToolSupportStatus.UNKNOWN
                val activeProjId = wBundle.activeProject?.id
                val activeGhCfg = activeProjId?.let { ghBundle.configs[it] }?.takeIf { it.repo.isNotBlank() }
                val activeSync = activeProjId?.let { ghBundle.syncStatuses[it] } ?: GitHubSyncStatus.Idle
                val promptCfg = sBundle.session?.id?.let { ghBundle.promptConfigs[it] }
                val enrichedSession = if (sBundle.session != null && promptCfg != null) {
                    sBundle.session.copy(mode = promptCfg.mode, presetTitle = promptCfg.presetTitle)
                } else {
                    sBundle.session
                }

                ChatUiState(
                    currentSession = enrichedSession,
                    currentSessionPrompt = promptCfg,
                    messages = sBundle.messages,
                    streamingMessage = sBundle.streaming,
                    isGenerating = sBundle.generating,
                    isCreatingProject = ghBundle.catalog.isCreatingProject,
                    activeProject = wBundle.activeProject,
                    allProjects = wBundle.projects,
                    selectedProviderId = providerId,
                    selectedModelId = modelId,
                    providerOptions = options,
                    toolSupportStatus = toolStatus,
                    mcpEnabledServerCount = oBundle.mcpServerCount,
                    mcpToolCount = oBundle.mcpToolCount,
                    isGitHubSignedIn = ghBundle.catalog.isSignedIn,
                    defaultGitHubOwner = ghBundle.catalog.defaultOwner,
                    availableGitHubRepos = ghBundle.catalog.repos,
                    isLoadingGitHubRepos = ghBundle.catalog.isLoadingRepos,
                    activeGitHubConfig = activeGhCfg,
                    activeSyncStatus = activeSync,
                    pendingWorkflowAlert = ghBundle.alert,
                    pendingDeletion = oBundle.deletion,
                    pendingDeviceAction = oBundle.deviceAction,
                    errorBanner = oBundle.error
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            gitHubSyncManager.workflowFailureAlerts.collect { alert ->
                pendingWorkflowAlert.value = alert
            }
        }
    }

    fun startNewSession() {
        stopGeneration()
        container.selectedSessionId.value = null
        errorBanner.value = null
    }

    fun startNewConfiguredSession(
        mode: SessionMode,
        presetId: String,
        presetTitle: String,
        systemPrompt: String,
        attachActiveProject: Boolean = mode == SessionMode.SPECIAL
    ) {
        stopGeneration()
        errorBanner.value = null
        viewModelScope.launch {
            val currentState = _uiState.value
            val providerId = currentState.selectedProviderId
            val modelId = currentState.selectedModelId
            val projectId = if (attachActiveProject) currentState.activeProject?.id else null
            val title = if (mode == SessionMode.NORMAL) "Normal Question" else presetTitle
            val created = chatRepository.createSession(
                title = title,
                projectId = projectId,
                providerId = providerId,
                modelId = modelId
            )
            settingsDataStore.saveSessionPromptConfig(
                created.id,
                SessionPromptConfig(
                    mode = mode,
                    presetId = presetId,
                    presetTitle = presetTitle,
                    systemPrompt = systemPrompt.ifBlank {
                        if (mode == SessionMode.NORMAL) SystemPromptPresets.NORMAL_QA_PROMPT
                        else settingsDataStore.getSettings().systemPrompt
                    }
                )
            )
            container.selectedSessionId.value = created.id
        }
    }

    fun updateActiveSessionSystemPrompt(prompt: String) {
        val sessionId = container.selectedSessionId.value ?: return
        viewModelScope.launch {
            val existing = settingsDataStore.getSessionPromptConfig(sessionId)
                ?: SessionPromptConfig(mode = SessionMode.SPECIAL, presetId = "custom", presetTitle = "Custom Prompt")
            settingsDataStore.saveSessionPromptConfig(
                sessionId,
                existing.copy(systemPrompt = prompt)
            )
        }
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
            isCreatingProject.value = true
            runCatching {
                val created = workspaceManager.createProject(name)
                settingsDataStore.setActiveProjectId(created.id)
                container.selectedSessionId.value?.let { sessionId ->
                    chatRepository.updateSessionProject(sessionId, created.id)
                }
            }.onFailure {
                errorBanner.value = it.message ?: "Could not create project."
            }
            isCreatingProject.value = false
        }
    }

    fun createProjectFromDeviceFolder(treeUri: Uri) {
        viewModelScope.launch {
            isCreatingProject.value = true
            runCatching {
                val attached = workspaceManager.attachDeviceFolderProject(treeUri)
                settingsDataStore.setActiveProjectId(attached.id)
                container.selectedSessionId.value?.let { sessionId ->
                    chatRepository.updateSessionProject(sessionId, attached.id)
                }
            }.onFailure {
                errorBanner.value = it.message ?: "Could not open selected device folder."
            }
            isCreatingProject.value = false
        }
    }

    fun loadGitHubRepositories() {
        if (isLoadingGitHubRepos.value) return
        viewModelScope.launch {
            isLoadingGitHubRepos.value = true
            gitHubSyncManager.listRepositories().onSuccess { repos ->
                availableGitHubRepos.value = repos
            }.onFailure { err ->
                errorBanner.value = err.message ?: "Could not load GitHub repositories."
            }
            isLoadingGitHubRepos.value = false
        }
    }

    fun createDirectGitHubProject(
        owner: String,
        repo: String,
        branch: String,
        isPrivate: Boolean,
        createNewRepo: Boolean,
        includeWorkflow: Boolean,
        autoFixWorkflowBugs: Boolean
    ) {
        viewModelScope.launch {
            isCreatingProject.value = true
            gitHubSyncManager.createDirectGitHubProject(
                workspaceManager = workspaceManager,
                ownerInput = owner,
                repoInput = repo,
                branchInput = branch,
                isPrivate = isPrivate,
                createNewRepo = createNewRepo,
                includeApkWorkflow = includeWorkflow,
                autoFixWorkflowBugs = autoFixWorkflowBugs
            ).onSuccess { project ->
                container.selectedSessionId.value?.let { sessionId ->
                    chatRepository.updateSessionProject(sessionId, project.id)
                }
            }.onFailure { err ->
                errorBanner.value = err.message ?: "Could not open GitHub repository."
            }
            isCreatingProject.value = false
        }
    }

    fun sendMessage(text: String) {
        sendMessageInternal(text = text, overrideProjectId = null)
    }

    fun promptAiToFixBug(projectId: String?, prompt: String) {
        stopGeneration()
        container.selectedSessionId.value = null
        pendingWorkflowAlert.value = null
        viewModelScope.launch {
            if (!projectId.isNullOrBlank()) {
                settingsDataStore.setActiveProjectId(projectId)
            }
            sendMessageInternal(text = prompt, overrideProjectId = projectId)
        }
    }

    fun fixPendingWorkflowAlert() {
        val alert = pendingWorkflowAlert.value ?: return
        promptAiToFixBug(alert.projectId, alert.aiFixPrompt)
    }

    fun dismissWorkflowAlert() {
        pendingWorkflowAlert.value = null
    }

    fun syncActiveProjectNow() {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            gitHubSyncManager.syncNow(projectId, "Manual sync from Chat").onFailure { err ->
                errorBanner.value = err.message ?: "GitHub sync failed."
            }
        }
    }

    private fun sendMessageInternal(text: String, overrideProjectId: String?) {
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
                val projectId = overrideProjectId ?: currentState.activeProject?.id
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
                    },
                    confirmDeviceAction = { request ->
                        val deferred = CompletableDeferred<Boolean>()
                        deviceActionDeferred = deferred
                        pendingDeviceAction.value = request
                        try {
                            deferred.await()
                        } finally {
                            pendingDeviceAction.value = null
                            deviceActionDeferred = null
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
        deviceActionDeferred?.complete(false)
        pendingDeletion.value = null
        pendingDeviceAction.value = null
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        isGenerating.value = false
        streamingMessage.value = null
    }

    fun resolveDeletionConfirmation(approved: Boolean) {
        deletionDeferred?.complete(approved)
        pendingDeletion.value = null
    }

    fun resolveDeviceActionConfirmation(approved: Boolean) {
        deviceActionDeferred?.complete(approved)
        pendingDeviceAction.value = null
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
        val deviceAction: PendingDeviceActionConfirmation?,
        val error: String?,
        val mcpServerCount: Int,
        val mcpToolCount: Int
    )

    private data class GitHubCatalogBundle(
        val isSignedIn: Boolean,
        val defaultOwner: String,
        val repos: List<GitHubRepositorySummary>,
        val isLoadingRepos: Boolean,
        val isCreatingProject: Boolean
    )

    private data class GitHubFullBundle(
        val configs: Map<String, GitHubProjectConfig>,
        val syncStatuses: Map<String, GitHubSyncStatus>,
        val alert: WorkflowFailureAlert?,
        val catalog: GitHubCatalogBundle,
        val promptConfigs: Map<String, SessionPromptConfig>
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
                providerRegistry = container.providerRegistry,
                gitHubSyncManager = container.gitHubSyncManager
            ) as T
        }
    }
}
