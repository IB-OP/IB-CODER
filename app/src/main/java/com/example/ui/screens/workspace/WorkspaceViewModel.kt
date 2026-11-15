package com.example.ui.screens.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.preferences.SettingsDataStore
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.FileNode
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val projects: List<WorkspaceProject> = emptyList(),
    val activeProject: WorkspaceProject? = null,
    val fileTree: List<FileNode> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedFileContent: String = "",
    val isEditingFile: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

class WorkspaceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val fileTreeState = MutableStateFlow<List<FileNode>>(emptyList())
    private val selectedFilePathState = MutableStateFlow<String?>(null)
    private val selectedFileContentState = MutableStateFlow("")
    private val isEditingState = MutableStateFlow(false)
    private val statusState = MutableStateFlow<String?>(null)
    private val errorState = MutableStateFlow<String?>(null)
    private val busyState = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var lastLoadedProjectId: String? = null

    init {
        viewModelScope.launch {
            val partialFlow = combine(
                workspaceManager.observeProjects(),
                settingsDataStore.activeProjectIdFlow,
                fileTreeState,
                selectedFilePathState,
                selectedFileContentState
            ) { projects, activeId, tree, filePath, fileContent ->
                val active = projects.firstOrNull { it.id == activeId } ?: projects.firstOrNull()
                if (active != null && active.id != activeId) {
                    settingsDataStore.setActiveProjectId(active.id)
                }
                if (active?.id != lastLoadedProjectId) {
                    lastLoadedProjectId = active?.id
                    refreshTreeInternal(active?.id)
                }
                PartialWorkspaceState(projects, active, tree, filePath, fileContent)
            }
            val flagsFlow = combine(
                isEditingState,
                statusState,
                errorState,
                busyState
            ) { editing, status, err, busy ->
                FlagsState(editing, status, err, busy)
            }
            combine(partialFlow, flagsFlow) { p, f ->
                WorkspaceUiState(
                    projects = p.projects,
                    activeProject = p.active,
                    fileTree = p.tree,
                    selectedFilePath = p.filePath,
                    selectedFileContent = p.fileContent,
                    isEditingFile = f.editing,
                    statusMessage = f.status,
                    errorMessage = f.err,
                    isBusy = f.busy
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun refreshTree() {
        viewModelScope.launch {
            refreshTreeInternal(_uiState.value.activeProject?.id)
        }
    }

    private suspend fun refreshTreeInternal(projectId: String?) {
        if (projectId == null) {
            fileTreeState.value = emptyList()
            selectedFilePathState.value = null
            selectedFileContentState.value = ""
            return
        }
        fileTreeState.value = workspaceManager.buildFileTree(projectId)
    }

    fun selectProject(projectId: String) {
        viewModelScope.launch {
            settingsDataStore.setActiveProjectId(projectId)
            selectedFilePathState.value = null
            selectedFileContentState.value = ""
            isEditingState.value = false
            refreshTreeInternal(projectId)
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            busyState.value = true
            runCatching {
                val created = workspaceManager.createProject(name)
                settingsDataStore.setActiveProjectId(created.id)
                refreshTreeInternal(created.id)
                statusState.value = "Created project '${created.name}'."
            }.onFailure {
                errorState.value = it.message ?: "Could not create project."
            }
            busyState.value = false
        }
    }

    fun renameActiveProject(newName: String) {
        val id = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            runCatching {
                workspaceManager.renameProject(id, newName)
                statusState.value = "Renamed project to '$newName'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to rename project."
            }
        }
    }

    fun deleteActiveProject() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            runCatching {
                workspaceManager.deleteProject(project.id)
                settingsDataStore.setActiveProjectId(null)
                selectedFilePathState.value = null
                selectedFileContentState.value = ""
                statusState.value = "Deleted project '${project.name}'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to delete project."
            }
        }
    }

    fun openFile(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            runCatching {
                val text = workspaceManager.readRawFile(projectId, relativePath)
                selectedFilePathState.value = relativePath
                selectedFileContentState.value = text
                isEditingState.value = false
            }.onFailure {
                errorState.value = it.message ?: "Cannot open file."
            }
        }
    }

    fun closeFileViewer() {
        selectedFilePathState.value = null
        selectedFileContentState.value = ""
        isEditingState.value = false
    }

    fun setEditing(editing: Boolean) {
        isEditingState.value = editing
    }

    fun saveFileContent(relativePath: String, content: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            runCatching {
                workspaceManager.writeFile(projectId, relativePath, content)
                selectedFileContentState.value = content
                isEditingState.value = false
                refreshTreeInternal(projectId)
                statusState.value = "Saved '$relativePath'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to save file."
            }
        }
    }

    fun createNewFile(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val clean = relativePath.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                workspaceManager.writeFile(projectId, clean, "")
                refreshTreeInternal(projectId)
                openFile(clean)
                isEditingState.value = true
                statusState.value = "Created '$clean'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to create file."
            }
        }
    }

    fun createNewFolder(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        val clean = relativePath.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                workspaceManager.createFolder(projectId, clean)
                refreshTreeInternal(projectId)
                statusState.value = "Created folder '$clean'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to create folder."
            }
        }
    }

    fun deleteFileNode(relativePath: String) {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            runCatching {
                workspaceManager.deletePath(projectId, relativePath)
                if (selectedFilePathState.value == relativePath) {
                    closeFileViewer()
                }
                refreshTreeInternal(projectId)
                statusState.value = "Deleted '$relativePath'."
            }.onFailure {
                errorState.value = it.message ?: "Failed to delete '$relativePath'."
            }
        }
    }

    fun importZip(context: Context, uri: Uri) {
        viewModelScope.launch {
            busyState.value = true
            runCatching {
                val fileName = resolveDisplayName(context, uri)
                    ?.removeSuffix(".zip")
                    ?.ifBlank { "Imported Project" }
                    ?: "Imported Project"
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read selected zip file.")
                val project = stream.use { workspaceManager.importProjectFromZip(fileName, it) }
                settingsDataStore.setActiveProjectId(project.id)
                refreshTreeInternal(project.id)
                statusState.value = "Imported '${project.name}' from zip."
            }.onFailure {
                errorState.value = "Zip import failed: ${it.message}"
            }
            busyState.value = false
        }
    }

    fun exportZip(context: Context, uri: Uri) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            busyState.value = true
            runCatching {
                val out = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Cannot open destination zip file.")
                out.use { workspaceManager.exportProjectToZip(project.id, it) }
                statusState.value = "Exported '${project.name}.zip'."
            }.onFailure {
                errorState.value = "Zip export failed: ${it.message}"
            }
            busyState.value = false
        }
    }

    fun clearBanner() {
        statusState.value = null
        errorState.value = null
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private data class PartialWorkspaceState(
        val projects: List<WorkspaceProject>,
        val active: WorkspaceProject?,
        val tree: List<FileNode>,
        val filePath: String?,
        val fileContent: String
    )

    private data class FlagsState(
        val editing: Boolean,
        val status: String?,
        val err: String?,
        val busy: Boolean
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceViewModel(
                workspaceManager = container.workspaceManager,
                settingsDataStore = container.settingsDataStore
            ) as T
        }
    }
}
