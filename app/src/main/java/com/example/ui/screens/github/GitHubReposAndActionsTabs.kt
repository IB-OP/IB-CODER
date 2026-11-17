package com.example.ui.screens.github

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.GitHubRepositorySummary
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.GitHubWorkflowInfo
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun GitHubRepositoriesTab(
    state: GitHubHubUiState,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (RepoFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenCreateRepo: () -> Unit,
    onEditRepo: (GitHubRepositorySummary) -> Unit,
    onDeleteRepo: (GitHubRepositorySummary) -> Unit,
    onCloneRepo: (GitHubRepositorySummary) -> Unit,
    onSelectRepoForActions: (GitHubRepositorySummary) -> Unit,
    onGoToSignIn: () -> Unit
) {
    val context = LocalContext.current
    if (state.savedToken.isBlank()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sign In Required", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Connect your GitHub account in the Account tab to browse, edit, clone, and sync repositories.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onGoToSignIn) { Text("Go to Account Sign-In") }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.repoSearch,
                onValueChange = onSearchChanged,
                placeholder = { Text("Search your repositories...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.repoSearch.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("github_repo_search_input")
            )
            IconButton(onClick = onRefresh, enabled = !state.isLoadingRepos) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh repositories")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.repoFilter == RepoFilter.ALL,
                onClick = { onFilterChanged(RepoFilter.ALL) },
                label = { Text("All") }
            )
            FilterChip(
                selected = state.repoFilter == RepoFilter.PRIVATE,
                onClick = { onFilterChanged(RepoFilter.PRIVATE) },
                label = { Text("Private") }
            )
            FilterChip(
                selected = state.repoFilter == RepoFilter.PUBLIC,
                onClick = { onFilterChanged(RepoFilter.PUBLIC) },
                label = { Text("Public") }
            )
            OutlinedButton(onClick = onOpenCreateRepo, modifier = Modifier.testTag("github_new_repo_button")) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Repo")
            }
        }

        if (state.isLoadingRepos && state.repositories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.repositories.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No repositories matched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.repositories, key = { it.id }) { repo ->
                    val linkedProject = state.projectConfigs.entries.firstOrNull { (_, cfg) ->
                        cfg.owner.equals(repo.ownerLogin, ignoreCase = true) &&
                            cfg.repo.equals(repo.name, ignoreCase = true)
                    }?.let { entry -> state.workspaceProjects.firstOrNull { it.id == entry.key } }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("github_repo_card_${repo.name}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(
                            1.dp,
                            if (state.selectedRepo?.id == repo.id) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (repo.isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = repo.fullName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(if (repo.isPrivate) "Private" else "Public")
                                            append(" • branch: ${repo.defaultBranch}")
                                            if (repo.language.isNotBlank()) append(" • ${repo.language}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))) }
                                }) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open on GitHub")
                                }
                            }

                            if (repo.description.isNotBlank()) {
                                Text(
                                    text = repo.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (linkedProject != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Linked Workspace: ${linkedProject.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { onCloneRepo(repo) }) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Open Direct (${repo.defaultBranch.ifBlank { "main" }})")
                                }
                                OutlinedButton(onClick = { onSelectRepoForActions(repo) }) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Actions & Sync")
                                }
                                OutlinedButton(onClick = { onEditRepo(repo) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit")
                                }
                                IconButton(onClick = { onDeleteRepo(repo) }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete repository",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GitHubActionsAndSyncTab(
    state: GitHubHubUiState,
    onSelectBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit,
    onRefreshActions: () -> Unit,
    onLinkWorkspace: (projectId: String, autoSync: Boolean, autoFix: Boolean) -> Unit,
    onPullIntoProject: (projectId: String) -> Unit,
    onToggleAutoSync: (projectId: String, enabled: Boolean) -> Unit,
    onToggleDirectMode: (projectId: String, enabled: Boolean) -> Unit,
    onToggleAutoFix: (projectId: String, enabled: Boolean) -> Unit,
    onSyncNow: (projectId: String) -> Unit,
    onToggleBuildPlayBundle: (Boolean) -> Unit,
    onDispatchWorkflow: (GitHubWorkflowInfo) -> Unit,
    onRerunWorkflow: (GitHubWorkflowRunSummary, failedOnly: Boolean) -> Unit,
    onCancelWorkflow: (GitHubWorkflowRunSummary) -> Unit,
    onInspectLogs: (GitHubWorkflowRunSummary) -> Unit,
    onFixRunWithAi: (GitHubWorkflowRunSummary) -> Unit,
    onFixDispatchFailureWithAi: (WorkflowDispatchFailure) -> Unit,
    onDismissDispatchFailure: () -> Unit,
    onPickRepositoryTab: () -> Unit
) {
    val context = LocalContext.current
    val repo = state.selectedRepo
    if (repo == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select a Repository", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pick any repository from the Repositories tab to manage branches, Auto-Sync, and GitHub Actions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onPickRepositoryTab) { Text("Browse Repositories") }
            }
        }
        return
    }

    var branchMenuOpen by remember { mutableStateOf(false) }
    var newBranchInput by remember { mutableStateOf("") }
    var linkMenuOpen by remember { mutableStateOf(false) }

    val linkedEntry = state.projectConfigs.entries.firstOrNull { (_, cfg) ->
        cfg.owner.equals(repo.ownerLogin, ignoreCase = true) &&
            cfg.repo.equals(repo.name, ignoreCase = true)
    }
    val linkedProject = linkedEntry?.let { entry -> state.workspaceProjects.firstOrNull { it.id == entry.key } }
    val linkedConfig = linkedEntry?.value
    val syncStatus = linkedProject?.let { state.syncStatuses[it.id] } ?: GitHubSyncStatus.Idle

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Repo + Branch Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(repo.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Active Branch: ${state.selectedBranch}", style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFamily)
                        }
                        IconButton(onClick = onRefreshActions, enabled = !state.isLoadingActions) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Actions")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(onClick = { branchMenuOpen = true }) {
                                Text("Branch: ${state.selectedBranch}")
                            }
                            DropdownMenu(expanded = branchMenuOpen, onDismissRequest = { branchMenuOpen = false }) {
                                state.branches.forEach { br ->
                                    DropdownMenuItem(
                                        text = { Text(br.name, fontFamily = JetBrainsMonoFamily) },
                                        onClick = {
                                            onSelectBranch(br.name)
                                            branchMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = newBranchInput,
                            onValueChange = { newBranchInput = it },
                            placeholder = { Text("new-branch") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onCreateBranch(newBranchInput)
                                newBranchInput = ""
                            },
                            enabled = newBranchInput.isNotBlank() && !state.isBusy
                        ) {
                            Text("Branch")
                        }
                    }
                }
            }
        }

        // Workspace Auto-Sync & Pull Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Workspace Sync & Auto-Fix", style = MaterialTheme.typography.titleMedium)
                    }

                    if (linkedProject != null && linkedConfig != null) {
                        Text(
                            text = "Linked to '${linkedProject.name}'",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Direct GitHub mode (commit edits immediately to ${linkedConfig.branch.ifBlank { "main" }})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = linkedConfig.directGitHubMode,
                                onCheckedChange = { onToggleDirectMode(linkedProject.id, it) },
                                modifier = Modifier.testTag("github_direct_mode_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-sync edits & AI changes to GitHub", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = linkedConfig.autoSyncEnabled,
                                onCheckedChange = { onToggleAutoSync(linkedProject.id, it) },
                                modifier = Modifier.testTag("github_autosync_switch")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Auto-prompt AI if pushed workflow fails", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Switch(
                                checked = linkedConfig.autoFixWorkflowBugs,
                                onCheckedChange = { onToggleAutoFix(linkedProject.id, it) },
                                modifier = Modifier.testTag("github_autofix_switch")
                            )
                        }
                        when (syncStatus) {
                            is GitHubSyncStatus.Syncing -> Text(syncStatus.step, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            is GitHubSyncStatus.Synced -> Text("Last synced commit: ${syncStatus.commitSha.take(7)} (${syncStatus.filesUploaded} files)", style = MaterialTheme.typography.bodySmall)
                            is GitHubSyncStatus.Error -> Text("Sync error: ${syncStatus.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            GitHubSyncStatus.Idle -> Unit
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onSyncNow(linkedProject.id) }, enabled = syncStatus !is GitHubSyncStatus.Syncing) {
                                Text("Push / Sync Now")
                            }
                            OutlinedButton(onClick = { onPullIntoProject(linkedProject.id) }, enabled = !state.isBusy) {
                                Text("Pull Latest")
                            }
                        }
                    } else {
                        Text(
                            "Link an existing workspace project (or clone from Repositories tab) to auto-sync changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            OutlinedButton(
                                onClick = { linkMenuOpen = true },
                                enabled = state.workspaceProjects.isNotEmpty()
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Link Existing Workspace")
                            }
                            DropdownMenu(expanded = linkMenuOpen, onDismissRequest = { linkMenuOpen = false }) {
                                state.workspaceProjects.forEach { proj ->
                                    DropdownMenuItem(
                                        text = { Text(proj.name) },
                                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                        onClick = {
                                            linkMenuOpen = false
                                            onLinkWorkspace(proj.id, true, true)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dispatch / Startup Failure AI Auto-Fix Banner
        state.dispatchFailure?.let { failure ->
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = "Bug starting '${failure.workflowName}' on ${failure.branch}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(failure.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onFixDispatchFailureWithAi(failure) },
                                modifier = Modifier.testTag("github_fix_dispatch_ai_button")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Prompt AI to Fix")
                            }
                            TextButton(onClick = onDismissDispatchFailure) { Text("Dismiss") }
                        }
                    }
                }
            }
        }

        // Workflows List ("Push Actions")
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Workflows (Push Actions)", style = MaterialTheme.typography.titleMedium)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Build Play Store .aab (src/app-release.aab)",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Include signed Android App Bundle for Google Play Console (uses repo signing secrets)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.buildPlayBundleOnDispatch,
                            onCheckedChange = onToggleBuildPlayBundle,
                            modifier = Modifier.testTag("github_build_play_bundle_switch")
                        )
                    }
                }
            }
        }
        if (state.workflows.isEmpty()) {
            item {
                Text(
                    "No workflows found in .github/workflows/ yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.workflows, key = { it.id }) { wf ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(wf.name, style = MaterialTheme.typography.titleSmall)
                            Text(wf.path, style = MaterialTheme.typography.bodySmall, fontFamily = JetBrainsMonoFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { onDispatchWorkflow(wf) },
                            enabled = !state.isBusy,
                            modifier = Modifier.testTag("github_run_workflow_${wf.id}")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Run")
                        }
                    }
                }
            }
        }

        // Recent Workflow Runs
        item {
            Spacer(Modifier.height(4.dp))
            Text("Recent Workflow Runs", style = MaterialTheme.typography.titleMedium)
        }
        if (state.workflowRuns.isEmpty()) {
            item {
                Text(
                    "No Actions runs recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.workflowRuns, key = { it.id }) { run ->
                val isFailed = run.conclusion == "failure" || run.conclusion == "startup_failure" || run.conclusion == "timed_out"
                val isRunning = run.status == "queued" || run.status == "in_progress"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = if (isFailed) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${run.name} #${run.runNumber}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${run.status}${run.conclusion?.let { " • $it" } ?: ""} • ${run.headBranch}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl))) }
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open run on GitHub")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isFailed) {
                                Button(
                                    onClick = { onFixRunWithAi(run) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.testTag("github_fix_run_ai_${run.id}")
                                ) {
                                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fix Bug with AI")
                                }
                            }
                            if (isRunning) {
                                OutlinedButton(onClick = { onCancelWorkflow(run) }) {
                                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cancel")
                                }
                            } else {
                                OutlinedButton(onClick = { onRerunWorkflow(run, false) }) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Re-run")
                                }
                            }
                            OutlinedButton(onClick = { onInspectLogs(run) }) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Logs")
                            }
                        }
                    }
                }
            }
        }
    }
}
