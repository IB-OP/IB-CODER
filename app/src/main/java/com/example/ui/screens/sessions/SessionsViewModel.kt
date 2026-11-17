package com.example.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ChatRepository
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ChatSession
import com.example.domain.model.SessionMode
import com.example.domain.model.SessionPromptConfig
import com.example.domain.model.WorkspaceProject
import com.example.domain.prompt.SystemPromptPresets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<ChatSession> = emptyList(),
    val projectsById: Map<String, WorkspaceProject> = emptyMap(),
    val activeSessionId: String? = null,
    val searchQuery: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModel(
    private val container: AppContainer,
    private val chatRepository: ChatRepository,
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SessionsUiState())
    val uiState: StateFlow<SessionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val filteredSessions = searchQuery.flatMapLatest { q ->
                chatRepository.observeSessions(q)
            }
            combine(
                filteredSessions,
                workspaceManager.observeProjects(),
                container.selectedSessionId,
                searchQuery,
                settingsDataStore.sessionPromptConfigsFlow
            ) { sessions, projects, activeId, query, promptConfigs ->
                val enriched = sessions.map { s ->
                    val cfg = promptConfigs[s.id]
                    if (cfg != null) {
                        s.copy(mode = cfg.mode, presetTitle = cfg.presetTitle)
                    } else {
                        s
                    }
                }
                SessionsUiState(
                    sessions = enriched,
                    projectsById = projects.associateBy { it.id },
                    activeSessionId = activeId,
                    searchQuery = query
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun openSession(sessionId: String) {
        container.selectedSessionId.value = sessionId
    }

    fun createNewSession(
        mode: SessionMode = SessionMode.NORMAL,
        presetId: String = "normal",
        presetTitle: String = "Normal Q&A",
        systemPrompt: String = SystemPromptPresets.NORMAL_QA_PROMPT
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.getSettings()
            val activeProjectId = if (mode == SessionMode.NORMAL) {
                null
            } else {
                settingsDataStore.activeProjectIdFlow.first()
            }
            val title = if (mode == SessionMode.NORMAL) "Normal Question" else presetTitle
            val created = chatRepository.createSession(
                title = title,
                projectId = activeProjectId,
                providerId = settings.defaultProviderId,
                modelId = settings.defaultModelId
            )
            settingsDataStore.saveSessionPromptConfig(
                created.id,
                SessionPromptConfig(
                    mode = mode,
                    presetId = presetId,
                    presetTitle = presetTitle,
                    systemPrompt = systemPrompt.ifBlank {
                        if (mode == SessionMode.NORMAL) SystemPromptPresets.NORMAL_QA_PROMPT
                        else settings.systemPrompt
                    }
                )
            )
            container.selectedSessionId.value = created.id
        }
    }

    fun renameSession(sessionId: String, newTitle: String) {
        viewModelScope.launch {
            chatRepository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (container.selectedSessionId.value == sessionId) {
                container.selectedSessionId.value = null
            }
            settingsDataStore.deleteSessionPromptConfig(sessionId)
            chatRepository.deleteSession(sessionId)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SessionsViewModel(
                container = container,
                chatRepository = container.chatRepository,
                workspaceManager = container.workspaceManager,
                settingsDataStore = container.settingsDataStore
            ) as T
        }
    }
}
