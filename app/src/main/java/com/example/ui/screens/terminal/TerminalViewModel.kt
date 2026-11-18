package com.example.ui.screens.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.preferences.SettingsDataStore
import com.example.data.terminal.TerminalExecutor
import com.example.data.terminal.TerminalSessionState
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TerminalUiState(
    val session: TerminalSessionState = TerminalSessionState(),
    val activeProject: WorkspaceProject? = null,
    val commandHistory: List<String> = emptyList(),
    val lastExitCode: Int? = null
)

class TerminalViewModel(
    private val terminalExecutor: TerminalExecutor,
    private val settingsDataStore: SettingsDataStore,
    private val workspaceManager: WorkspaceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                terminalExecutor.sessionState,
                settingsDataStore.activeProjectIdFlow,
                workspaceManager.observeProjects()
            ) { session, activeId, projects ->
                Triple(session, activeId, projects)
            }.collect { (session, activeId, projects) ->
                val proj = projects.firstOrNull { it.id == activeId }
                _uiState.update { current ->
                    current.copy(
                        session = session,
                        activeProject = proj
                    )
                }
            }
        }
    }

    fun runCommand(
        rawCommand: String,
        onWorkspaceMutated: () -> Unit = {}
    ) {
        val clean = rawCommand.trim()
        if (clean.isEmpty() || _uiState.value.session.isRunning) return

        _uiState.update { state ->
            val updatedHistory = (listOf(clean) + state.commandHistory.filterNot { it == clean }).take(25)
            state.copy(commandHistory = updatedHistory)
        }

        val projectId = _uiState.value.activeProject?.id
        activeJob = viewModelScope.launch {
            val result = terminalExecutor.execute(
                command = clean,
                projectId = projectId
            )
            _uiState.update { it.copy(lastExitCode = result.exitCode) }
            if (result.mutatesWorkspace) {
                onWorkspaceMutated()
            }
        }
    }

    fun stopCommand() {
        activeJob?.cancel()
        terminalExecutor.stopCurrentProcess()
    }

    fun clearTerminal() {
        terminalExecutor.clearOutput()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TerminalViewModel(
                terminalExecutor = container.terminalExecutor,
                settingsDataStore = container.settingsDataStore,
                workspaceManager = container.workspaceManager
            ) as T
        }
    }
}
