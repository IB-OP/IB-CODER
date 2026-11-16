package com.example.ui.screens.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.GitHubPushClient
import com.example.data.network.GitHubSyncManager
import com.example.data.preferences.SettingsDataStore
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.FileNode
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubPushResult
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.GitHubUserProfile
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed class GitHubVerifyUiState {
    data object Idle : GitHubVerifyUiState()
    data object Verifying : GitHubVerifyUiState()
    data class Verified(val profile: GitHubUserProfile) : GitHubVerifyUiState()
    data class Error(val message: String) : GitHubVerifyUiState()
}

sealed class GitHubPushUiState {
    data object Idle : GitHubPushUiState()
    data class Pushing(val step: String) : GitHubPushUiState()
    data class Success(val result: GitHubPushResult) : GitHubPushUiState()
    data class Error(val message: String) : GitHubPushUiState()
}

data class WorkspaceUiState(
    val projects: List<WorkspaceProject> = emptyList(),
    val activeProject: WorkspaceProject? = null,
    val fileTree: List<FileNode> = emptyList(),
    val selectedFilePath: String? = null,
    val selectedFileContent: String = "",
    val isEditingFile: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
    val savedGitHubToken: String = "",
    val defaultGitHubOwner: String = "",
    val activeProjectGitHubConfig: GitHubProjectConfig? = null,
    val activeProjectSyncStatus: GitHubSyncStatus = GitHubSyncStatus.Idle,
    val gitHubVerifyState: GitHubVerifyUiState = GitHubVerifyUiState.Idle,
    val gitHubPushState: GitHubPushUiState = GitHubPushUiState.Idle,
    val latestWorkflowRun: GitHubWorkflowRunSummary? = null,
    val isCheckingWorkflowRun: Boolean = false,
    val workflowCheckError: String? = null
)

class WorkspaceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val settingsDataStore: SettingsDataStore,
    private val gitHubPushClient: GitHubPushClient,
    private val gitHubSyncManager: GitHubSyncManager
) : ViewModel() {

    private val fileTreeState = MutableStateFlow<List<FileNode>>(emptyList())
    private val selectedFilePathState = MutableStateFlow<String?>(null)
    private val selectedFileContentState = MutableStateFlow("")
    private val isEditingState = MutableStateFlow(false)
    private val statusState = MutableStateFlow<String?>(null)
    private val errorState = MutableStateFlow<String?>(null)
    private val busyState = MutableStateFlow(false)

    private val savedGitHubTokenState = MutableStateFlow("")
    private val gitHubVerifyState = MutableStateFlow<GitHubVerifyUiState>(GitHubVerifyUiState.Idle)
    private val gitHubPushState = MutableStateFlow<GitHubPushUiState>(GitHubPushUiState.Idle)
    private val latestWorkflowRunState = MutableStateFlow<GitHubWorkflowRunSummary?>(null)
    private val checkingWorkflowRunState = MutableStateFlow(false)
    private val workflowCheckErrorState = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var lastLoadedProjectId: String? = null

    init {
        viewModelScope.launch {
            settingsDataStore.hasGitHubTokenFlow.collect {
                savedGitHubTokenState.value = settingsDataStore.getGitHubToken()
            }
        }

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
                    gitHubPushState.value = GitHubPushUiState.Idle
                    latestWorkflowRunState.value = null
                    workflowCheckErrorState.value = null
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
            val gitHubConfigFlow = combine(
                savedGitHubTokenState,
                settingsDataStore.githubDefaultOwnerFlow,
                settingsDataStore.githubProjectConfigsFlow,
                gitHubVerifyState,
                gitHubPushState
            ) { token, defOwner, configs, verify, push ->
                GitHubConfigBundle(token, defOwner, configs, verify, push)
            }
            val workflowRunFlow = combine(
                latestWorkflowRunState,
                checkingWorkflowRunState,
                workflowCheckErrorState,
                gitHubSyncManager.syncStatuses
            ) { run, checking, err, syncMap ->
                WorkflowRunBundle(run, checking, err, syncMap)
            }

            combine(partialFlow, flagsFlow, gitHubConfigFlow, workflowRunFlow) { p, f, gh, wf ->
                WorkspaceUiState(
                    projects = p.projects,
                    activeProject = p.active,
                    fileTree = p.tree,
                    selectedFilePath = p.filePath,
                    selectedFileContent = p.fileContent,
                    isEditingFile = f.editing,
                    statusMessage = f.status,
                    errorMessage = f.err,
                    isBusy = f.busy,
                    savedGitHubToken = gh.token,
                    defaultGitHubOwner = gh.defaultOwner,
                    activeProjectGitHubConfig = p.active?.let { gh.configs[it.id] },
                    activeProjectSyncStatus = p.active?.let { wf.syncMap[it.id] } ?: GitHubSyncStatus.Idle,
                    gitHubVerifyState = gh.verify,
                    gitHubPushState = gh.push,
                    latestWorkflowRun = wf.run,
                    isCheckingWorkflowRun = wf.checking,
                    workflowCheckError = wf.err
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
                gitHubSyncManager.scheduleAutoSync(projectId, "Update $relativePath")
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
                gitHubSyncManager.scheduleAutoSync(projectId, "Create $clean")
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
                gitHubSyncManager.scheduleAutoSync(projectId, "Create folder $clean")
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
                gitHubSyncManager.scheduleAutoSync(projectId, "Delete $relativePath")
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

    // --- GitHub Push & Build ---

    fun verifyGitHubToken(token: String) {
        viewModelScope.launch {
            gitHubVerifyState.value = GitHubVerifyUiState.Verifying
            val res = gitHubPushClient.verifyToken(token)
            res.fold(
                onSuccess = { profile ->
                    settingsDataStore.saveGitHubToken(token)
                    settingsDataStore.saveGitHubUserProfile(profile)
                    savedGitHubTokenState.value = token.trim()
                    gitHubVerifyState.value = GitHubVerifyUiState.Verified(profile)
                },
                onFailure = { err ->
                    gitHubVerifyState.value = GitHubVerifyUiState.Error(err.message ?: "Verification failed.")
                }
            )
        }
    }

    fun pushActiveProjectToGitHub(
        token: String,
        owner: String,
        repo: String,
        branch: String,
        commitMessage: String,
        isPrivate: Boolean,
        createIfMissing: Boolean,
        includeApkWorkflow: Boolean,
        autoSyncEnabled: Boolean = false,
        autoFixWorkflowBugs: Boolean = false
    ) {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            gitHubPushState.value = GitHubPushUiState.Pushing("Connecting to GitHub...")
            workflowCheckErrorState.value = null
            latestWorkflowRunState.value = null

            settingsDataStore.saveGitHubToken(token)
            savedGitHubTokenState.value = token.trim()

            val result = gitHubPushClient.pushProject(
                projectId = project.id,
                token = token,
                ownerInput = owner,
                repoInput = repo,
                branchInput = branch,
                commitMessageInput = commitMessage,
                isPrivate = isPrivate,
                createIfMissing = createIfMissing,
                includeApkWorkflow = includeApkWorkflow,
                onProgress = { step ->
                    gitHubPushState.value = GitHubPushUiState.Pushing(step)
                }
            )

            result.fold(
                onSuccess = { pushed ->
                    settingsDataStore.saveGitHubProjectConfig(
                        project.id,
                        GitHubProjectConfig(
                            owner = pushed.owner,
                            repo = pushed.repo,
                            branch = pushed.branch,
                            isPrivate = isPrivate,
                            createIfMissing = createIfMissing,
                            includeApkWorkflow = includeApkWorkflow,
                            autoSyncEnabled = autoSyncEnabled,
                            autoFixWorkflowBugs = autoFixWorkflowBugs,
                            lastCommitSha = pushed.commitSha,
                            lastRepoUrl = pushed.repoUrl,
                            lastActionsUrl = pushed.actionsUrl,
                            lastPushedAt = System.currentTimeMillis()
                        )
                    )
                    refreshTreeInternal(project.id)
                    gitHubPushState.value = GitHubPushUiState.Success(pushed)
                    statusState.value = "Pushed ${pushed.filesUploaded} file(s) to ${pushed.owner}/${pushed.repo}."
                },
                onFailure = { err ->
                    gitHubPushState.value = GitHubPushUiState.Error(err.message ?: "Push failed.")
                }
            )
        }
    }

    fun syncNow() {
        val projectId = _uiState.value.activeProject?.id ?: return
        viewModelScope.launch {
            gitHubSyncManager.syncNow(projectId, "Manual sync from IB CODER").fold(
                onSuccess = { pushed ->
                    statusState.value = "Synced to ${pushed.owner}/${pushed.repo} (${pushed.commitSha.take(7)})."
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Sync failed."
                }
            )
        }
    }

    fun checkLatestGitHubWorkflowRun(token: String, owner: String, repo: String) {
        viewModelScope.launch {
            checkingWorkflowRunState.value = true
            workflowCheckErrorState.value = null
            gitHubPushClient.fetchLatestWorkflowRun(token, owner, repo).fold(
                onSuccess = { summary ->
                    latestWorkflowRunState.value = summary
                    if (summary == null) {
                        workflowCheckErrorState.value = "No Actions runs found yet. GitHub may still be queueing the run."
                    }
                },
                onFailure = { err ->
                    workflowCheckErrorState.value = err.message ?: "Failed to check build status."
                }
            )
            checkingWorkflowRunState.value = false
        }
    }

    fun resetGitHubDialogFeedback() {
        gitHubPushState.value = GitHubPushUiState.Idle
        workflowCheckErrorState.value = null
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

    private data class GitHubConfigBundle(
        val token: String,
        val defaultOwner: String,
        val configs: Map<String, GitHubProjectConfig>,
        val verify: GitHubVerifyUiState,
        val push: GitHubPushUiState
    )

    private data class WorkflowRunBundle(
        val run: GitHubWorkflowRunSummary?,
        val checking: Boolean,
        val err: String?,
        val syncMap: Map<String, GitHubSyncStatus>
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorkspaceViewModel(
                workspaceManager = container.workspaceManager,
                settingsDataStore = container.settingsDataStore,
                gitHubPushClient = container.gitHubPushClient,
                gitHubSyncManager = container.gitHubSyncManager
            ) as T
        }
    }
}
