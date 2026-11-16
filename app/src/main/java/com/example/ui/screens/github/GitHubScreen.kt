package com.example.ui.screens.github

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(
    viewModel: GitHubViewModel,
    onLaunchAiFix: (projectId: String?, prompt: String) -> Unit,
    onOpenWorkspace: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var editingRepo by remember { mutableStateOf<GitHubRepositorySummary?>(null) }
    var deletingRepo by remember { mutableStateOf<GitHubRepositorySummary?>(null) }
    var cloningRepo by remember { mutableStateOf<GitHubRepositorySummary?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GitHub Hub", style = MaterialTheme.typography.titleLarge)
                        state.userProfile?.let { prof ->
                            Text(
                                text = "@${prof.login} • Full Account & Actions Control",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
            ) {
                if (state.isBusy || state.isLoadingRepos || state.isLoadingActions || state.isSigningIn) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                PrimaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                    GitHubHubTab.entries.forEach { tab ->
                        Tab(
                            selected = state.selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.title) },
                            modifier = Modifier.testTag("github_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                state.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::dismissBanners, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                state.bannerMessage?.let { banner ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = viewModel::dismissBanners, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss notice",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.selectedTab) {
                        GitHubHubTab.ACCOUNT -> {
                            GitHubAccountTab(
                                state = state,
                                onSignInWithToken = viewModel::signInWithGitHubToken,
                                onStartDeviceFlow = viewModel::startDeviceFlow,
                                onCancelDeviceFlow = viewModel::cancelDeviceFlow,
                                onSignInLocal = viewModel::signInLocal,
                                onSignOut = viewModel::signOut,
                                onToggleRequireSignIn = viewModel::setRequireSignInOnLaunch
                            )
                        }

                        GitHubHubTab.REPOS -> {
                            GitHubRepositoriesTab(
                                state = state,
                                onSearchChanged = viewModel::setRepoSearch,
                                onFilterChanged = viewModel::setRepoFilter,
                                onRefresh = { viewModel.refreshRepositories(silent = false) },
                                onOpenCreateRepo = { showCreateRepoDialog = true },
                                onEditRepo = { editingRepo = it },
                                onDeleteRepo = { deletingRepo = it },
                                onCloneRepo = { cloningRepo = it },
                                onSelectRepoForActions = { viewModel.selectRepository(it, openActionsTab = true) },
                                onGoToSignIn = { viewModel.selectTab(GitHubHubTab.ACCOUNT) }
                            )
                        }

                        GitHubHubTab.ACTIONS -> {
                            GitHubActionsAndSyncTab(
                                state = state,
                                onSelectBranch = viewModel::selectBranch,
                                onCreateBranch = viewModel::createBranch,
                                onRefreshActions = { viewModel.loadRepositoryActionsAndBranches() },
                                onLinkWorkspace = viewModel::linkExistingWorkspaceToSelectedRepo,
                                onPullIntoProject = viewModel::pullSelectedRepoIntoProject,
                                onToggleAutoSync = viewModel::toggleProjectAutoSync,
                                onToggleAutoFix = viewModel::toggleProjectAutoFix,
                                onSyncNow = viewModel::syncWorkspaceNow,
                                onToggleBuildPlayBundle = viewModel::setBuildPlayBundleOnDispatch,
                                onDispatchWorkflow = viewModel::dispatchWorkflow,
                                onRerunWorkflow = viewModel::rerunWorkflow,
                                onCancelWorkflow = viewModel::cancelWorkflow,
                                onInspectLogs = viewModel::inspectRunLogs,
                                onFixRunWithAi = { run ->
                                    viewModel.prepareAiFixForRun(run, onLaunchAiFix)
                                },
                                onFixDispatchFailureWithAi = { failure ->
                                    viewModel.prepareAiFixForDispatchError(failure, onLaunchAiFix)
                                },
                                onDismissDispatchFailure = viewModel::dismissDispatchFailure,
                                onPickRepositoryTab = { viewModel.selectTab(GitHubHubTab.REPOS) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateRepoDialog) {
        CreateRepositoryDialog(
            onConfirm = { name, desc, isPrivate ->
                showCreateRepoDialog = false
                viewModel.createRepository(name, desc, isPrivate)
            },
            onDismiss = { showCreateRepoDialog = false }
        )
    }

    editingRepo?.let { repo ->
        EditRepositoryDialog(
            repo = repo,
            onConfirm = { newName, desc, isPrivate, defaultBranch ->
                editingRepo = null
                viewModel.updateRepository(repo, newName, desc, isPrivate, defaultBranch)
            },
            onDismiss = { editingRepo = null }
        )
    }

    deletingRepo?.let { repo ->
        DeleteRepositoryDialog(
            repo = repo,
            onConfirm = {
                deletingRepo = null
                viewModel.deleteRepository(repo)
            },
            onDismiss = { deletingRepo = null }
        )
    }

    cloningRepo?.let { repo ->
        CloneRepositoryDialog(
            repo = repo,
            onConfirm = { branch, autoSync, autoFix ->
                cloningRepo = null
                viewModel.cloneRepoToWorkspace(
                    repo = repo,
                    branch = branch,
                    enableAutoSync = autoSync,
                    enableAutoFix = autoFix,
                    onOpenWorkspace = onOpenWorkspace
                )
            },
            onDismiss = { cloningRepo = null }
        )
    }

    state.inspectedRun?.let { run ->
        InspectWorkflowLogsDialog(
            run = run,
            logs = state.inspectedLogs,
            isLoading = state.isLoadingLogs,
            onFixWithAi = {
                viewModel.closeLogsDialog()
                viewModel.prepareAiFixForRun(run, onLaunchAiFix)
            },
            onDismiss = viewModel::closeLogsDialog
        )
    }
}

@Composable
private fun CreateRepositoryDialog(
    onConfirm: (name: String, description: String, isPrivate: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New GitHub Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    placeholder = { Text("my-android-app") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth().testTag("github_create_repo_name")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), description.trim(), isPrivate) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("github_create_repo_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditRepositoryDialog(
    repo: GitHubRepositorySummary,
    onConfirm: (newName: String, description: String, isPrivate: Boolean, defaultBranch: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(repo.id) { mutableStateOf(repo.name) }
    var description by remember(repo.id) { mutableStateOf(repo.description) }
    var defaultBranch by remember(repo.id) { mutableStateOf(repo.defaultBranch.ifBlank { "main" }) }
    var isPrivate by remember(repo.id) { mutableStateOf(repo.isPrivate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Repository") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Repository Name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = defaultBranch,
                    onValueChange = { defaultBranch = it },
                    label = { Text("Default Branch") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Private repository", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name.trim(), description.trim(), isPrivate, defaultBranch.trim())
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteRepositoryDialog(
    repo: GitHubRepositorySummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${repo.fullName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This permanently deletes the repository on GitHub (requires delete_repo token scope). Type '${repo.name}' to confirm.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text("Type repository name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmation.trim() == repo.name,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Repo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloneRepositoryDialog(
    repo: GitHubRepositorySummary,
    onConfirm: (branch: String, enableAutoSync: Boolean, enableAutoFix: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var branch by remember(repo.id) { mutableStateOf(repo.defaultBranch.ifBlank { "main" }) }
    var autoSync by remember { mutableStateOf(true) }
    var autoFix by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clone ${repo.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Sync to GitHub", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Automatically commit & push file edits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoSync, onCheckedChange = { autoSync = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Prompt AI on CI Bugs", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Offer 1-tap AI fix if a pushed workflow fails",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoFix, onCheckedChange = { autoFix = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(branch.trim().ifBlank { "main" }, autoSync, autoFix) },
                enabled = branch.isNotBlank()
            ) {
                Text("Clone & Open")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun InspectWorkflowLogsDialog(
    run: GitHubWorkflowRunSummary,
    logs: List<GitHubWorkflowJobLog>,
    isLoading: Boolean,
    onFixWithAi: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${run.name} #${run.runNumber} Logs") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Downloading job logs...", style = MaterialTheme.typography.bodySmall)
                    }
                } else if (logs.isEmpty()) {
                    Text(
                        "No detailed job logs were returned for this run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    logs.forEach { job ->
                        Text(
                            text = "${job.jobName} (${job.conclusion ?: "unknown"})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (job.failedSteps.isNotEmpty()) {
                            Text(
                                text = "Failed steps: ${job.failedSteps.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (job.logExcerpt.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = job.logExcerpt,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onFixWithAi,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Fix Bug with AI")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
