package com.example.ui.screens.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.network.DeviceTokenPollResult
import com.example.data.network.GitHubPushClient
import com.example.data.network.GitHubSyncManager
import com.example.data.preferences.SettingsDataStore
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.AppAccountSession
import com.example.domain.model.GitHubBranchInfo
import com.example.domain.model.GitHubDeviceCodeResponse
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.GitHubUserProfile
import com.example.domain.model.GitHubWorkflowInfo
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class RepoFilter {
    ALL,
    PRIVATE,
    PUBLIC
}

enum class GitHubHubTab(val title: String) {
    ACCOUNT("Account"),
    REPOS("Repositories"),
    ACTIONS("Actions & Sync")
}

sealed class DeviceFlowUiState {
    data object Idle : DeviceFlowUiState()
    data object Requesting : DeviceFlowUiState()
    data class WaitingForUser(val info: GitHubDeviceCodeResponse) : DeviceFlowUiState()
    data class Error(val message: String) : DeviceFlowUiState()
}

data class WorkflowDispatchFailure(
    val workflowName: String,
    val branch: String,
    val errorMessage: String,
    val aiFixPrompt: String
)

data class GitHubHubUiState(
    val selectedTab: GitHubHubTab = GitHubHubTab.ACCOUNT,
    val accountSession: AppAccountSession = AppAccountSession(),
    val userProfile: GitHubUserProfile? = null,
    val savedToken: String = "",
    val oauthClientId: String = "",
    val deviceFlowState: DeviceFlowUiState = DeviceFlowUiState.Idle,
    val isSigningIn: Boolean = false,
    val repositories: List<GitHubRepositorySummary> = emptyList(),
    val repoSearch: String = "",
    val repoFilter: RepoFilter = RepoFilter.ALL,
    val isLoadingRepos: Boolean = false,
    val selectedRepo: GitHubRepositorySummary? = null,
    val branches: List<GitHubBranchInfo> = emptyList(),
    val selectedBranch: String = "main",
    val workflows: List<GitHubWorkflowInfo> = emptyList(),
    val workflowRuns: List<GitHubWorkflowRunSummary> = emptyList(),
    val isLoadingActions: Boolean = false,
    val buildPlayBundleOnDispatch: Boolean = false,
    val dispatchFailure: WorkflowDispatchFailure? = null,
    val inspectedRun: GitHubWorkflowRunSummary? = null,
    val inspectedLogs: List<GitHubWorkflowJobLog> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val workspaceProjects: List<WorkspaceProject> = emptyList(),
    val projectConfigs: Map<String, GitHubProjectConfig> = emptyMap(),
    val syncStatuses: Map<String, GitHubSyncStatus> = emptyMap(),
    val bannerMessage: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)

class GitHubViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val workspaceManager: WorkspaceManager,
    private val gitHubPushClient: GitHubPushClient,
    private val gitHubSyncManager: GitHubSyncManager
) : ViewModel() {

    private val selectedTabState = MutableStateFlow(GitHubHubTab.ACCOUNT)
    private val savedTokenState = MutableStateFlow("")
    private val deviceFlowState = MutableStateFlow<DeviceFlowUiState>(DeviceFlowUiState.Idle)
    private val isSigningInState = MutableStateFlow(false)
    private val repositoriesState = MutableStateFlow<List<GitHubRepositorySummary>>(emptyList())
    private val repoSearchState = MutableStateFlow("")
    private val repoFilterState = MutableStateFlow(RepoFilter.ALL)
    private val isLoadingReposState = MutableStateFlow(false)
    private val selectedRepoState = MutableStateFlow<GitHubRepositorySummary?>(null)
    private val branchesState = MutableStateFlow<List<GitHubBranchInfo>>(emptyList())
    private val selectedBranchState = MutableStateFlow("main")
    private val workflowsState = MutableStateFlow<List<GitHubWorkflowInfo>>(emptyList())
    private val workflowRunsState = MutableStateFlow<List<GitHubWorkflowRunSummary>>(emptyList())
    private val isLoadingActionsState = MutableStateFlow(false)
    private val buildPlayBundleOnDispatchState = MutableStateFlow(false)
    private val dispatchFailureState = MutableStateFlow<WorkflowDispatchFailure?>(null)
    private val inspectedRunState = MutableStateFlow<GitHubWorkflowRunSummary?>(null)
    private val inspectedLogsState = MutableStateFlow<List<GitHubWorkflowJobLog>>(emptyList())
    private val isLoadingLogsState = MutableStateFlow(false)
    private val bannerState = MutableStateFlow<String?>(null)
    private val errorState = MutableStateFlow<String?>(null)
    private val busyState = MutableStateFlow(false)

    private var devicePollJob: Job? = null

    private val _uiState = MutableStateFlow(GitHubHubUiState())
    val uiState: StateFlow<GitHubHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.hasGitHubTokenFlow.collect {
                val tok = settingsDataStore.getGitHubToken()
                savedTokenState.value = tok
                if (tok.isNotBlank() && repositoriesState.value.isEmpty()) {
                    refreshRepositories(silent = true)
                }
            }
        }

        viewModelScope.launch {
            val authBundle = combine(
                selectedTabState,
                settingsDataStore.appAccountSessionFlow,
                settingsDataStore.githubUserProfileFlow,
                savedTokenState,
                settingsDataStore.githubOAuthClientIdFlow
            ) { tab, session, profile, tok, clientId ->
                AuthBundle(tab, session, profile, tok, clientId)
            }

            val repoBundle = combine(
                repositoriesState,
                repoSearchState,
                repoFilterState,
                isLoadingReposState,
                selectedRepoState
            ) { repos, search, filter, loading, selected ->
                val filtered = repos.filter { r ->
                    val filterOk = when (filter) {
                        RepoFilter.ALL -> true
                        RepoFilter.PRIVATE -> r.isPrivate
                        RepoFilter.PUBLIC -> !r.isPrivate
                    }
                    val queryOk = search.isBlank() ||
                        r.fullName.contains(search, ignoreCase = true) ||
                        r.description.contains(search, ignoreCase = true)
                    filterOk && queryOk
                }
                RepoBundle(filtered, search, filter, loading, selected)
            }

            val actionsBundle = combine(
                branchesState,
                selectedBranchState,
                workflowsState,
                workflowRunsState,
                isLoadingActionsState
            ) { branches, branch, workflows, runs, loading ->
                ActionsBundle(branches, branch, workflows, runs, loading)
            }

            val workspaceBundle = combine(
                workspaceManager.observeProjects(),
                settingsDataStore.githubProjectConfigsFlow,
                gitHubSyncManager.syncStatuses,
                deviceFlowState,
                isSigningInState
            ) { projects, configs, syncMap, devFlow, signing ->
                WorkspaceSyncBundle(projects, configs, syncMap, devFlow, signing)
            }

            val detailBundle = combine(
                dispatchFailureState,
                inspectedRunState,
                inspectedLogsState,
                isLoadingLogsState,
                combine(bannerState, errorState, busyState, buildPlayBundleOnDispatchState) { b, e, busy, playBundle ->
                    FlagQuad(b, e, busy, playBundle)
                }
            ) { dispatchErr, run, logs, loadingLogs, flags ->
                DetailBundle(dispatchErr, run, logs, loadingLogs, flags.banner, flags.error, flags.busy, flags.buildPlayBundle)
            }

            combine(authBundle, repoBundle, actionsBundle, workspaceBundle, detailBundle) { a, r, act, w, d ->
                GitHubHubUiState(
                    selectedTab = a.tab,
                    accountSession = a.session,
                    userProfile = a.profile,
                    savedToken = a.token,
                    oauthClientId = a.clientId,
                    deviceFlowState = w.deviceFlow,
                    isSigningIn = w.isSigningIn,
                    repositories = r.repos,
                    repoSearch = r.search,
                    repoFilter = r.filter,
                    isLoadingRepos = r.loading,
                    selectedRepo = r.selected,
                    branches = act.branches,
                    selectedBranch = act.branch,
                    workflows = act.workflows,
                    workflowRuns = act.runs,
                    isLoadingActions = act.loading,
                    buildPlayBundleOnDispatch = d.buildPlayBundle,
                    dispatchFailure = d.dispatchErr,
                    inspectedRun = d.inspectedRun,
                    inspectedLogs = d.inspectedLogs,
                    isLoadingLogs = d.loadingLogs,
                    workspaceProjects = w.projects,
                    projectConfigs = w.configs,
                    syncStatuses = w.syncMap,
                    bannerMessage = d.banner,
                    errorMessage = d.error,
                    isBusy = d.busy
                )
            }.collect { _uiState.value = it }
        }
    }

    fun selectTab(tab: GitHubHubTab) {
        selectedTabState.value = tab
    }

    fun setRepoSearch(q: String) {
        repoSearchState.value = q
    }

    fun setRepoFilter(filter: RepoFilter) {
        repoFilterState.value = filter
    }

    // --- Authentication ---

    fun signInWithGitHubToken(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            errorState.value = "Paste a GitHub Personal Access Token first."
            return
        }
        viewModelScope.launch {
            isSigningInState.value = true
            errorState.value = null
            gitHubPushClient.verifyToken(clean).fold(
                onSuccess = { profile ->
                    settingsDataStore.saveGitHubToken(clean)
                    settingsDataStore.saveGitHubUserProfile(profile)
                    savedTokenState.value = clean
                    bannerState.value = "Signed in as @${profile.login}."
                    selectedTabState.value = GitHubHubTab.REPOS
                    refreshRepositories(silent = false)
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "GitHub sign-in failed."
                }
            )
            isSigningInState.value = false
        }
    }

    fun startDeviceFlow(clientId: String) {
        val cleanId = clientId.trim()
        if (cleanId.isEmpty()) {
            deviceFlowState.value = DeviceFlowUiState.Error("Enter your GitHub OAuth App Client ID first.")
            return
        }
        devicePollJob?.cancel()
        viewModelScope.launch {
            settingsDataStore.saveGitHubOAuthClientId(cleanId)
            deviceFlowState.value = DeviceFlowUiState.Requesting
            gitHubPushClient.startDeviceFlow(cleanId).fold(
                onSuccess = { info ->
                    deviceFlowState.value = DeviceFlowUiState.WaitingForUser(info)
                    pollForDeviceToken(cleanId, info)
                },
                onFailure = { err ->
                    deviceFlowState.value = DeviceFlowUiState.Error(err.message ?: "Could not start Device Flow.")
                }
            )
        }
    }

    private fun pollForDeviceToken(clientId: String, info: GitHubDeviceCodeResponse) {
        devicePollJob = viewModelScope.launch {
            var waitSeconds = info.interval.coerceAtLeast(5)
            val deadlineMs = System.currentTimeMillis() + info.expiresIn * 1000L
            while (System.currentTimeMillis() < deadlineMs) {
                delay(waitSeconds * 1000L)
                when (val poll = gitHubPushClient.pollDeviceFlowToken(clientId, info.deviceCode)) {
                    is DeviceTokenPollResult.Success -> {
                        deviceFlowState.value = DeviceFlowUiState.Idle
                        signInWithGitHubToken(poll.accessToken)
                        return@launch
                    }
                    is DeviceTokenPollResult.Pending -> {
                        if (poll.slowDown) waitSeconds += 5
                    }
                    is DeviceTokenPollResult.Failed -> {
                        deviceFlowState.value = DeviceFlowUiState.Error(poll.message)
                        return@launch
                    }
                }
            }
            deviceFlowState.value = DeviceFlowUiState.Error("Device code timed out. Request a new code.")
        }
    }

    fun cancelDeviceFlow() {
        devicePollJob?.cancel()
        deviceFlowState.value = DeviceFlowUiState.Idle
    }

    fun signInLocal(username: String, passcode: String) {
        viewModelScope.launch {
            settingsDataStore.signInLocalAccount(username, passcode)
            bannerState.value = "Signed in locally as '${username.trim().ifBlank { "Developer" }}'."
        }
    }

    fun signOut(clearGitHubToken: Boolean = true) {
        devicePollJob?.cancel()
        deviceFlowState.value = DeviceFlowUiState.Idle
        viewModelScope.launch {
            settingsDataStore.signOutApp(clearGitHubToken)
            if (clearGitHubToken) {
                savedTokenState.value = ""
                repositoriesState.value = emptyList()
                selectedRepoState.value = null
                branchesState.value = emptyList()
                workflowsState.value = emptyList()
                workflowRunsState.value = emptyList()
            }
            bannerState.value = "Signed out."
        }
    }

    fun setRequireSignInOnLaunch(required: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setRequireSignInOnLaunch(required)
        }
    }

    // --- Repositories ---

    fun refreshRepositories(silent: Boolean = false) {
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            isLoadingReposState.value = true
            if (!silent) errorState.value = null
            gitHubPushClient.listRepositories(token).fold(
                onSuccess = { list ->
                    repositoriesState.value = list
                    val cur = selectedRepoState.value
                    if (cur != null) {
                        selectedRepoState.value = list.firstOrNull { it.id == cur.id } ?: cur
                    }
                },
                onFailure = { err ->
                    if (!silent) errorState.value = err.message ?: "Failed to load repositories."
                }
            )
            isLoadingReposState.value = false
        }
    }

    fun selectRepository(repo: GitHubRepositorySummary, openActionsTab: Boolean = true) {
        selectedRepoState.value = repo
        selectedBranchState.value = repo.defaultBranch.ifBlank { "main" }
        dispatchFailureState.value = null
        inspectedRunState.value = null
        inspectedLogsState.value = emptyList()
        if (openActionsTab) {
            selectedTabState.value = GitHubHubTab.ACTIONS
        }
        loadRepositoryActionsAndBranches(repo)
    }

    fun loadRepositoryActionsAndBranches(repo: GitHubRepositorySummary? = selectedRepoState.value) {
        val target = repo ?: return
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            isLoadingActionsState.value = true
            gitHubPushClient.listBranches(token, target.ownerLogin, target.name).onSuccess {
                branchesState.value = it
            }
            gitHubPushClient.listWorkflows(token, target.ownerLogin, target.name).onSuccess {
                workflowsState.value = it
            }
            gitHubPushClient.listWorkflowRuns(token, target.ownerLogin, target.name).onSuccess {
                workflowRunsState.value = it
            }.onFailure { err ->
                errorState.value = err.message ?: "Failed to load Actions runs."
            }
            isLoadingActionsState.value = false
        }
    }

    fun createRepository(name: String, description: String, isPrivate: Boolean) {
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.createRepository(token, name, description, isPrivate).fold(
                onSuccess = { created ->
                    repositoriesState.value = listOf(created) + repositoriesState.value
                    bannerState.value = "Created repository ${created.fullName}."
                    selectRepository(created, openActionsTab = false)
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Could not create repository."
                }
            )
            busyState.value = false
        }
    }

    fun updateRepository(
        repo: GitHubRepositorySummary,
        newName: String,
        description: String,
        isPrivate: Boolean,
        defaultBranch: String
    ) {
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.updateRepository(
                token = token,
                owner = repo.ownerLogin,
                repo = repo.name,
                newName = newName,
                description = description,
                isPrivate = isPrivate,
                defaultBranch = defaultBranch
            ).fold(
                onSuccess = { updated ->
                    repositoriesState.value = repositoriesState.value.map {
                        if (it.id == repo.id) updated else it
                    }
                    if (selectedRepoState.value?.id == repo.id) {
                        selectedRepoState.value = updated
                    }
                    bannerState.value = "Updated ${updated.fullName}."
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Failed to update repository."
                }
            )
            busyState.value = false
        }
    }

    fun deleteRepository(repo: GitHubRepositorySummary) {
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.deleteRepository(token, repo.ownerLogin, repo.name).fold(
                onSuccess = {
                    repositoriesState.value = repositoriesState.value.filterNot { it.id == repo.id }
                    if (selectedRepoState.value?.id == repo.id) {
                        selectedRepoState.value = null
                    }
                    bannerState.value = "Deleted repository ${repo.fullName}."
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Failed to delete repository."
                }
            )
            busyState.value = false
        }
    }

    fun selectBranch(branchName: String) {
        selectedBranchState.value = branchName
    }

    fun createBranch(newBranchName: String) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        val baseSha = branchesState.value.firstOrNull { it.name == selectedBranchState.value }?.commitSha
            ?: branchesState.value.firstOrNull()?.commitSha
        if (baseSha.isNullOrBlank()) {
            errorState.value = "No base commit found to branch from."
            return
        }
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.createBranch(token, repo.ownerLogin, repo.name, newBranchName, baseSha).fold(
                onSuccess = { created ->
                    branchesState.value = branchesState.value + created
                    selectedBranchState.value = created.name
                    bannerState.value = "Created branch '${created.name}'."
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Failed to create branch."
                }
            )
            busyState.value = false
        }
    }

    // --- Clone / Link / Sync ---

    fun cloneRepoToWorkspace(
        repo: GitHubRepositorySummary,
        branch: String,
        enableAutoSync: Boolean,
        enableAutoFix: Boolean,
        onOpenWorkspace: () -> Unit = {}
    ) {
        val token = savedTokenState.value
        if (token.isBlank()) return
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.cloneOrPullRepository(token, repo.ownerLogin, repo.name, branch).fold(
                onSuccess = { project ->
                    settingsDataStore.setActiveProjectId(project.id)
                    settingsDataStore.saveGitHubProjectConfig(
                        project.id,
                        GitHubProjectConfig(
                            owner = repo.ownerLogin,
                            repo = repo.name,
                            branch = branch,
                            isPrivate = repo.isPrivate,
                            autoSyncEnabled = enableAutoSync,
                            autoFixWorkflowBugs = enableAutoFix,
                            lastRepoUrl = repo.htmlUrl,
                            lastActionsUrl = "${repo.htmlUrl}/actions"
                        )
                    )
                    bannerState.value = "Cloned ${repo.fullName} ($branch) into Workspace."
                    onOpenWorkspace()
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Clone failed."
                }
            )
            busyState.value = false
        }
    }

    fun linkExistingWorkspaceToSelectedRepo(
        projectId: String,
        autoSync: Boolean,
        autoFix: Boolean
    ) {
        val repo = selectedRepoState.value ?: return
        val branch = selectedBranchState.value
        viewModelScope.launch {
            val prev = settingsDataStore.getGitHubProjectConfig(projectId) ?: GitHubProjectConfig()
            settingsDataStore.saveGitHubProjectConfig(
                projectId,
                prev.copy(
                    owner = repo.ownerLogin,
                    repo = repo.name,
                    branch = branch,
                    isPrivate = repo.isPrivate,
                    autoSyncEnabled = autoSync,
                    autoFixWorkflowBugs = autoFix,
                    lastRepoUrl = repo.htmlUrl,
                    lastActionsUrl = "${repo.htmlUrl}/actions"
                )
            )
            bannerState.value = "Linked workspace to ${repo.fullName} ($branch)."
        }
    }

    fun pullSelectedRepoIntoProject(projectId: String) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        val branch = selectedBranchState.value
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.cloneOrPullRepository(token, repo.ownerLogin, repo.name, branch, existingProjectId = projectId).fold(
                onSuccess = {
                    settingsDataStore.setActiveProjectId(projectId)
                    bannerState.value = "Pulled latest '${branch}' from ${repo.fullName}."
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Pull failed."
                }
            )
            busyState.value = false
        }
    }

    fun toggleProjectAutoSync(projectId: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = settingsDataStore.getGitHubProjectConfig(projectId) ?: return@launch
            settingsDataStore.saveGitHubProjectConfig(projectId, cur.copy(autoSyncEnabled = enabled))
        }
    }

    fun toggleProjectAutoFix(projectId: String, enabled: Boolean) {
        viewModelScope.launch {
            val cur = settingsDataStore.getGitHubProjectConfig(projectId) ?: return@launch
            settingsDataStore.saveGitHubProjectConfig(projectId, cur.copy(autoFixWorkflowBugs = enabled))
        }
    }

    fun syncWorkspaceNow(projectId: String) {
        viewModelScope.launch {
            gitHubSyncManager.syncNow(projectId, "Manual sync from GitHub Hub").fold(
                onSuccess = { pushed ->
                    bannerState.value = "Pushed ${pushed.filesUploaded} files (${pushed.commitSha.take(7)})."
                    loadRepositoryActionsAndBranches()
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Sync failed."
                }
            )
        }
    }

    // --- Actions Dispatch & AI Auto-Fix ---

    fun setBuildPlayBundleOnDispatch(enabled: Boolean) {
        buildPlayBundleOnDispatchState.value = enabled
    }

    fun dispatchWorkflow(workflow: GitHubWorkflowInfo) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        val branch = selectedBranchState.value
        val includePlayBundle = buildPlayBundleOnDispatchState.value
        viewModelScope.launch {
            busyState.value = true
            dispatchFailureState.value = null
            gitHubPushClient.dispatchWorkflow(
                token = token,
                owner = repo.ownerLogin,
                repo = repo.name,
                workflowId = workflow.id,
                ref = branch,
                buildPlayBundle = includePlayBundle
            ).fold(
                onSuccess = {
                    val bundleNote = if (includePlayBundle) " (including Play Store src/app-release.aab)" else ""
                    bannerState.value = "Dispatched '${workflow.name}' on branch '$branch'$bundleNote."
                    delay(1800)
                    loadRepositoryActionsAndBranches(repo)
                },
                onFailure = { err ->
                    val errText = err.message ?: "Failed to start workflow."
                    val aiPrompt = gitHubSyncManager.buildRunFailurePrompt(
                        owner = repo.ownerLogin,
                        repo = repo.name,
                        branch = branch,
                        run = null,
                        jobLogs = emptyList(),
                        dispatchError = "Workflow '${workflow.name}' (${workflow.path}): $errText"
                    )
                    dispatchFailureState.value = WorkflowDispatchFailure(
                        workflowName = workflow.name,
                        branch = branch,
                        errorMessage = errText,
                        aiFixPrompt = aiPrompt
                    )
                }
            )
            busyState.value = false
        }
    }

    fun rerunWorkflow(run: GitHubWorkflowRunSummary, failedOnly: Boolean) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.rerunWorkflow(token, repo.ownerLogin, repo.name, run.id, failedOnly).fold(
                onSuccess = {
                    bannerState.value = "Re-running '${run.name}' #${run.runNumber}."
                    delay(1500)
                    loadRepositoryActionsAndBranches(repo)
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Failed to re-run workflow."
                }
            )
            busyState.value = false
        }
    }

    fun cancelWorkflow(run: GitHubWorkflowRunSummary) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        viewModelScope.launch {
            busyState.value = true
            gitHubPushClient.cancelWorkflowRun(token, repo.ownerLogin, repo.name, run.id).fold(
                onSuccess = {
                    bannerState.value = "Requested cancellation for #${run.runNumber}."
                    delay(1200)
                    loadRepositoryActionsAndBranches(repo)
                },
                onFailure = { err ->
                    errorState.value = err.message ?: "Cancel failed."
                }
            )
            busyState.value = false
        }
    }

    fun inspectRunLogs(run: GitHubWorkflowRunSummary) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        viewModelScope.launch {
            inspectedRunState.value = run
            inspectedLogsState.value = emptyList()
            isLoadingLogsState.value = true
            gitHubPushClient.fetchFailedWorkflowLogs(token, repo.ownerLogin, repo.name, run.id).fold(
                onSuccess = { logs -> inspectedLogsState.value = logs },
                onFailure = { err -> errorState.value = err.message ?: "Failed to fetch logs." }
            )
            isLoadingLogsState.value = false
        }
    }

    fun closeLogsDialog() {
        inspectedRunState.value = null
        inspectedLogsState.value = emptyList()
    }

    fun prepareAiFixForRun(
        run: GitHubWorkflowRunSummary,
        onPromptReady: (projectId: String?, prompt: String) -> Unit
    ) {
        val repo = selectedRepoState.value ?: return
        val token = savedTokenState.value
        val branch = run.headBranch.ifBlank { selectedBranchState.value }

        viewModelScope.launch {
            busyState.value = true
            val logs = gitHubPushClient.fetchFailedWorkflowLogs(token, repo.ownerLogin, repo.name, run.id)
                .getOrDefault(emptyList())
            val projectId = ensureLinkedWorkspaceForRepo(repo, branch)
            val prompt = gitHubSyncManager.buildRunFailurePrompt(
                owner = repo.ownerLogin,
                repo = repo.name,
                branch = branch,
                run = run,
                jobLogs = logs,
                dispatchError = null
            )
            busyState.value = false
            onPromptReady(projectId, prompt)
        }
    }

    fun prepareAiFixForDispatchError(
        failure: WorkflowDispatchFailure,
        onPromptReady: (projectId: String?, prompt: String) -> Unit
    ) {
        val repo = selectedRepoState.value ?: return
        viewModelScope.launch {
            busyState.value = true
            val projectId = ensureLinkedWorkspaceForRepo(repo, failure.branch)
            dispatchFailureState.value = null
            busyState.value = false
            onPromptReady(projectId, failure.aiFixPrompt)
        }
    }

    private suspend fun ensureLinkedWorkspaceForRepo(
        repo: GitHubRepositorySummary,
        branch: String
    ): String? {
        val configs = settingsDataStore.getGitHubProjectConfigs()
        val existingEntry = configs.entries.firstOrNull { (_, cfg) ->
            cfg.owner.equals(repo.ownerLogin, ignoreCase = true) &&
                cfg.repo.equals(repo.name, ignoreCase = true)
        }
        if (existingEntry != null) {
            return existingEntry.key
        }
        val token = savedTokenState.value
        if (token.isBlank()) return null
        return gitHubPushClient.cloneOrPullRepository(token, repo.ownerLogin, repo.name, branch)
            .getOrNull()
            ?.also { project ->
                settingsDataStore.setActiveProjectId(project.id)
                settingsDataStore.saveGitHubProjectConfig(
                    project.id,
                    GitHubProjectConfig(
                        owner = repo.ownerLogin,
                        repo = repo.name,
                        branch = branch,
                        isPrivate = repo.isPrivate,
                        autoSyncEnabled = true,
                        autoFixWorkflowBugs = true,
                        lastRepoUrl = repo.htmlUrl,
                        lastActionsUrl = "${repo.htmlUrl}/actions"
                    )
                )
            }?.id
    }

    fun dismissBanners() {
        bannerState.value = null
        errorState.value = null
    }

    fun dismissDispatchFailure() {
        dispatchFailureState.value = null
    }

    private data class AuthBundle(
        val tab: GitHubHubTab,
        val session: AppAccountSession,
        val profile: GitHubUserProfile?,
        val token: String,
        val clientId: String
    )

    private data class RepoBundle(
        val repos: List<GitHubRepositorySummary>,
        val search: String,
        val filter: RepoFilter,
        val loading: Boolean,
        val selected: GitHubRepositorySummary?
    )

    private data class ActionsBundle(
        val branches: List<GitHubBranchInfo>,
        val branch: String,
        val workflows: List<GitHubWorkflowInfo>,
        val runs: List<GitHubWorkflowRunSummary>,
        val loading: Boolean
    )

    private data class WorkspaceSyncBundle(
        val projects: List<WorkspaceProject>,
        val configs: Map<String, GitHubProjectConfig>,
        val syncMap: Map<String, GitHubSyncStatus>,
        val deviceFlow: DeviceFlowUiState,
        val isSigningIn: Boolean
    )

    private data class FlagQuad(
        val banner: String?,
        val error: String?,
        val busy: Boolean,
        val buildPlayBundle: Boolean
    )

    private data class DetailBundle(
        val dispatchErr: WorkflowDispatchFailure?,
        val inspectedRun: GitHubWorkflowRunSummary?,
        val inspectedLogs: List<GitHubWorkflowJobLog>,
        val loadingLogs: Boolean,
        val banner: String?,
        val error: String?,
        val busy: Boolean,
        val buildPlayBundle: Boolean
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GitHubViewModel(
                settingsDataStore = container.settingsDataStore,
                workspaceManager = container.workspaceManager,
                gitHubPushClient = container.gitHubPushClient,
                gitHubSyncManager = container.gitHubSyncManager
            ) as T
        }
    }
}
