package com.example.data.network

import com.example.data.preferences.SettingsDataStore
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubPushResult
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.WorkflowFailureAlert
import com.example.domain.model.WorkspaceProject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class GitHubSyncManager(
    private val settingsDataStore: SettingsDataStore,
    private val gitHubPushClient: GitHubPushClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val projectMutexes = ConcurrentHashMap<String, Mutex>()
    private val pendingJobs = ConcurrentHashMap<String, Job>()
    private val monitorJobs = ConcurrentHashMap<String, Job>()

    private val _syncStatuses = MutableStateFlow<Map<String, GitHubSyncStatus>>(emptyMap())
    val syncStatuses: StateFlow<Map<String, GitHubSyncStatus>> = _syncStatuses.asStateFlow()

    private val _workflowFailureAlerts = MutableSharedFlow<WorkflowFailureAlert>(extraBufferCapacity = 8)
    val workflowFailureAlerts: SharedFlow<WorkflowFailureAlert> = _workflowFailureAlerts.asSharedFlow()

    fun scheduleAutoSync(
        projectId: String,
        commitReason: String,
        immediate: Boolean = false
    ) {
        scope.launch {
            val cfg = settingsDataStore.getGitHubProjectConfig(projectId) ?: return@launch
            if (!cfg.autoSyncEnabled || cfg.repo.isBlank()) return@launch
            val token = settingsDataStore.getGitHubToken()
            if (token.isBlank()) return@launch

            val shouldImmediate = immediate || cfg.directGitHubMode
            pendingJobs[projectId]?.cancel()
            pendingJobs[projectId] = scope.launch {
                if (!shouldImmediate) {
                    delay(1500)
                }
                performSync(projectId, cfg, token, commitReason)
            }
        }
    }

    suspend fun syncNow(projectId: String, commitReason: String): Result<GitHubPushResult> {
        val cfg = settingsDataStore.getGitHubProjectConfig(projectId)
            ?: return Result.failure(IllegalStateException("This workspace is not linked to a GitHub repository yet."))
        if (cfg.repo.isBlank()) {
            return Result.failure(IllegalStateException("Repository name is not configured for this workspace."))
        }
        val token = settingsDataStore.getGitHubToken()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("Sign in to GitHub first."))
        }
        return performSync(projectId, cfg, token, commitReason)
    }

    private suspend fun performSync(
        projectId: String,
        cfg: GitHubProjectConfig,
        token: String,
        commitReason: String
    ): Result<GitHubPushResult> {
        val mutex = projectMutexes.getOrPut(projectId) { Mutex() }
        return mutex.withLock {
            updateStatus(projectId, GitHubSyncStatus.Syncing("Syncing to ${cfg.owner}/${cfg.repo}..."))
            val result = gitHubPushClient.pushProject(
                projectId = projectId,
                token = token,
                ownerInput = cfg.owner,
                repoInput = cfg.repo,
                branchInput = cfg.branch.ifBlank { "main" },
                commitMessageInput = commitReason.ifBlank { "Auto-sync from IB CODER" },
                isPrivate = cfg.isPrivate,
                createIfMissing = cfg.createIfMissing,
                includeApkWorkflow = cfg.includeApkWorkflow,
                onProgress = { step ->
                    updateStatus(projectId, GitHubSyncStatus.Syncing(step))
                }
            )

            result.fold(
                onSuccess = { pushed ->
                    val latestCfg = settingsDataStore.getGitHubProjectConfig(projectId) ?: cfg
                    settingsDataStore.saveGitHubProjectConfig(
                        projectId,
                        latestCfg.copy(
                            owner = pushed.owner,
                            repo = pushed.repo,
                            branch = pushed.branch,
                            lastCommitSha = pushed.commitSha,
                            lastRepoUrl = pushed.repoUrl,
                            lastActionsUrl = pushed.actionsUrl,
                            lastPushedAt = System.currentTimeMillis()
                        )
                    )
                    updateStatus(
                        projectId,
                        GitHubSyncStatus.Synced(
                            commitSha = pushed.commitSha,
                            timestamp = System.currentTimeMillis(),
                            filesUploaded = pushed.filesUploaded
                        )
                    )
                    if (latestCfg.autoFixWorkflowBugs) {
                        monitorLatestWorkflow(projectId, token, pushed.owner, pushed.repo, pushed.branch, pushed.commitSha)
                    }
                    Result.success(pushed)
                },
                onFailure = { err ->
                    updateStatus(
                        projectId,
                        GitHubSyncStatus.Error(err.message ?: "Auto-sync failed.")
                    )
                    Result.failure(err)
                }
            )
        }
    }

    private fun monitorLatestWorkflow(
        projectId: String,
        token: String,
        owner: String,
        repo: String,
        branch: String,
        expectedSha: String
    ) {
        monitorJobs[projectId]?.cancel()
        monitorJobs[projectId] = scope.launch {
            repeat(18) { attempt ->
                delay(if (attempt == 0) 8_000L else 12_000L)
                val runs = gitHubPushClient.listWorkflowRuns(token, owner, repo, perPage = 5)
                    .getOrNull() ?: return@repeat
                val match = runs.firstOrNull { it.headSha == expectedSha }
                    ?: runs.firstOrNull { it.headBranch == branch }
                    ?: return@repeat

                if (match.status == "completed") {
                    val conclusion = match.conclusion.orEmpty()
                    if (conclusion == "failure" || conclusion == "startup_failure" || conclusion == "timed_out") {
                        val logs = gitHubPushClient.fetchFailedWorkflowLogs(token, owner, repo, match.id)
                            .getOrDefault(emptyList())
                        val prompt = buildRunFailurePrompt(
                            owner = owner,
                            repo = repo,
                            branch = branch,
                            run = match,
                            jobLogs = logs,
                            dispatchError = null
                        )
                        _workflowFailureAlerts.tryEmit(
                            WorkflowFailureAlert(
                                projectId = projectId,
                                owner = owner,
                                repo = repo,
                                branch = branch,
                                workflowName = match.name,
                                summary = "${match.name} #${match.runNumber} ended with $conclusion",
                                aiFixPrompt = prompt
                            )
                        )
                    }
                    return@launch
                }
            }
        }
    }

    fun buildRunFailurePrompt(
        owner: String,
        repo: String,
        branch: String,
        run: GitHubWorkflowRunSummary?,
        jobLogs: List<GitHubWorkflowJobLog>,
        dispatchError: String?
    ): String = buildString {
        appendLine("Fix a GitHub Actions workflow bug in repository '$owner/$repo' on branch '$branch'.")
        appendLine()
        if (dispatchError != null) {
            appendLine("Phase: Starting / Dispatching Workflow")
            appendLine("Error returned by GitHub:")
            appendLine(dispatchError)
            appendLine()
            appendLine("Inspect `.github/workflows/` files in the workspace (create or update `workflow_dispatch:` trigger, syntax, permissions, or Gradle setup if broken), fix the bug, and explain what was wrong.")
        } else if (run != null) {
            appendLine("Workflow: ${run.name} (Run #${run.runNumber}, Conclusion: ${run.conclusion ?: run.status})")
            if (run.headSha.isNotBlank()) appendLine("Commit: ${run.headSha.take(10)}")
            appendLine()
            if (jobLogs.isNotEmpty()) {
                for (job in jobLogs) {
                    appendLine("### Job: ${job.jobName} (${job.conclusion ?: "unknown"})")
                    if (job.failedSteps.isNotEmpty()) {
                        appendLine("Failed step(s): ${job.failedSteps.joinToString(", ")}")
                    }
                    if (job.logExcerpt.isNotBlank()) {
                        appendLine("```")
                        appendLine(job.logExcerpt)
                        appendLine("```")
                    }
                    appendLine()
                }
            } else {
                appendLine("No detailed job logs were available (possible workflow YAML startup_failure). Inspect `.github/workflows/*.yml` and Gradle configuration.")
            }
            appendLine("Use workspace tools to inspect the relevant workflow YAML, Gradle files, or source code, fix the root cause with write_file/edit_file, and summarize the fix clearly.")
        }
    }

    suspend fun listRepositories(): Result<List<GitHubRepositorySummary>> {
        val token = settingsDataStore.getGitHubToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("Sign in to GitHub first."))
        }
        return gitHubPushClient.listRepositories(token)
    }

    suspend fun createDirectGitHubProject(
        workspaceManager: WorkspaceManager,
        ownerInput: String,
        repoInput: String,
        branchInput: String,
        isPrivate: Boolean,
        createNewRepo: Boolean,
        includeApkWorkflow: Boolean,
        autoFixWorkflowBugs: Boolean
    ): Result<WorkspaceProject> = runCatching {
        val token = settingsDataStore.getGitHubToken().trim()
        require(token.isNotEmpty()) { "Sign in to GitHub first." }

        val cleanRepo = repoInput.trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .trim('-')
        require(cleanRepo.isNotEmpty()) { "Repository name is required." }

        val cleanBranch = branchInput.trim().ifBlank { "main" }
        val resolvedOwner = ownerInput.trim().ifBlank {
            settingsDataStore.githubDefaultOwnerFlow.first().trim()
        }.ifBlank {
            gitHubPushClient.verifyToken(token).getOrThrow().login
        }

        val repoSummary = if (createNewRepo) {
            gitHubPushClient.createRepository(
                token = token,
                name = cleanRepo,
                description = "Managed directly from IB CODER",
                isPrivate = isPrivate,
                autoInit = true
            ).getOrThrow()
        } else {
            null
        }

        val finalOwner = repoSummary?.ownerLogin?.ifBlank { resolvedOwner } ?: resolvedOwner
        val finalRepo = repoSummary?.name?.ifBlank { cleanRepo } ?: cleanRepo
        val cloneRef = if (createNewRepo) {
            repoSummary?.defaultBranch?.ifBlank { "main" } ?: "main"
        } else {
            cleanBranch
        }

        val project = gitHubPushClient.cloneOrPullRepository(
            token = token,
            owner = finalOwner,
            repo = finalRepo,
            branch = cloneRef
        ).getOrThrow()

        var pushedCommitSha = ""
        if (createNewRepo && includeApkWorkflow) {
            val pushRes = gitHubPushClient.pushProject(
                projectId = project.id,
                token = token,
                ownerInput = finalOwner,
                repoInput = finalRepo,
                branchInput = cleanBranch,
                commitMessageInput = "Initialize IB CODER project on $cleanBranch",
                isPrivate = isPrivate,
                createIfMissing = true,
                includeApkWorkflow = true
            ).getOrThrow()
            pushedCommitSha = pushRes.commitSha
        }

        val repoUrl = repoSummary?.htmlUrl?.ifBlank { null }
            ?: "https://github.com/$finalOwner/$finalRepo"
        settingsDataStore.saveGitHubProjectConfig(
            project.id,
            GitHubProjectConfig(
                owner = finalOwner,
                repo = finalRepo,
                branch = cleanBranch,
                isPrivate = repoSummary?.isPrivate ?: isPrivate,
                createIfMissing = true,
                includeApkWorkflow = includeApkWorkflow,
                autoSyncEnabled = true,
                autoFixWorkflowBugs = autoFixWorkflowBugs,
                directGitHubMode = true,
                lastCommitSha = pushedCommitSha,
                lastRepoUrl = repoUrl,
                lastActionsUrl = "$repoUrl/actions",
                lastPushedAt = if (pushedCommitSha.isNotBlank()) System.currentTimeMillis() else 0L
            )
        )
        settingsDataStore.setActiveProjectId(project.id)
        if (pushedCommitSha.isNotBlank()) {
            updateStatus(project.id, GitHubSyncStatus.Synced(pushedCommitSha, System.currentTimeMillis(), 1))
        }
        project
    }

    suspend fun pullFromGitHub(projectId: String): Result<WorkspaceProject> {
        val cfg = settingsDataStore.getGitHubProjectConfig(projectId)
            ?: return Result.failure(IllegalStateException("This workspace is not linked to a GitHub repository yet."))
        if (cfg.owner.isBlank() || cfg.repo.isBlank()) {
            return Result.failure(IllegalStateException("GitHub owner/repository is not configured."))
        }
        val token = settingsDataStore.getGitHubToken().trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("Sign in to GitHub first."))
        }
        val branch = cfg.branch.ifBlank { "main" }
        updateStatus(projectId, GitHubSyncStatus.Syncing("Pulling '$branch' from ${cfg.owner}/${cfg.repo}..."))
        return gitHubPushClient.cloneOrPullRepository(
            token = token,
            owner = cfg.owner,
            repo = cfg.repo,
            branch = branch,
            existingProjectId = projectId
        ).onSuccess {
            updateStatus(projectId, GitHubSyncStatus.Idle)
        }.onFailure { err ->
            updateStatus(projectId, GitHubSyncStatus.Error(err.message ?: "Pull from GitHub failed."))
        }
    }

    suspend fun setDirectGitHubMode(projectId: String, enabled: Boolean) {
        val cur = settingsDataStore.getGitHubProjectConfig(projectId) ?: return
        settingsDataStore.saveGitHubProjectConfig(
            projectId,
            cur.copy(
                directGitHubMode = enabled,
                autoSyncEnabled = if (enabled) true else cur.autoSyncEnabled
            )
        )
    }

    private fun updateStatus(projectId: String, status: GitHubSyncStatus) {
        _syncStatuses.value = _syncStatuses.value + (projectId to status)
    }
}
