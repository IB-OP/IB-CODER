package com.example.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.repository.ChatRepository
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ChatSession
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val workspaceManager: WorkspaceManager
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
                searchQuery
            ) { sessions, projects, activeId, query ->
                SessionsUiState(
                    sessions = sessions,
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

    fun createNewSession() {
        container.selectedSessionId.value = null
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
            chatRepository.deleteSession(sessionId)
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SessionsViewModel(
                container = container,
                chatRepository = container.chatRepository,
                workspaceManager = container.workspaceManager
            ) as T
        }
    }
}
