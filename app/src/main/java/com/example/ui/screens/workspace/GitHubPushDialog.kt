package com.example.ui.screens.workspace

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun GitHubPushDialog(
    state: WorkspaceUiState,
    onVerifyToken: (String) -> Unit,
    onPush: (
        token: String,
        owner: String,
        repo: String,
        branch: String,
        commitMessage: String,
        isPrivate: Boolean,
        createIfMissing: Boolean,
        includeApkWorkflow: Boolean,
        autoSyncEnabled: Boolean,
        autoFixWorkflowBugs: Boolean
    ) -> Unit,
    onCheckBuildStatus: (token: String, owner: String, repo: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val project = state.activeProject ?: return
    val savedCfg = state.activeProjectGitHubConfig

    val defaultRepoName = remember(project.id, savedCfg?.repo) {
        savedCfg?.repo?.ifBlank { null } ?: project.name.lowercase()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^a-z0-9._-]"), "-")
            .trim('-')
            .ifBlank { "android-project" }
    }

    var token by remember(project.id, state.savedGitHubToken) { mutableStateOf(state.savedGitHubToken) }
    var showToken by remember { mutableStateOf(false) }
    var owner by remember(project.id, savedCfg?.owner, state.defaultGitHubOwner) {
        mutableStateOf(savedCfg?.owner?.ifBlank { null } ?: state.defaultGitHubOwner)
    }
    var repo by remember(project.id, defaultRepoName) { mutableStateOf(defaultRepoName) }
    var branch by remember(project.id, savedCfg?.branch) { mutableStateOf(savedCfg?.branch ?: "main") }
    var commitMessage by remember(project.id) { mutableStateOf("Update ${project.name} from IB CODER") }
    var isPrivate by remember(project.id, savedCfg?.isPrivate) { mutableStateOf(savedCfg?.isPrivate ?: true) }
    var createIfMissing by remember(project.id, savedCfg?.createIfMissing) { mutableStateOf(savedCfg?.createIfMissing ?: true) }
    var includeApkWorkflow by remember(project.id, savedCfg?.includeApkWorkflow) {
        mutableStateOf(savedCfg?.includeApkWorkflow ?: true)
    }
    var autoSyncEnabled by remember(project.id, savedCfg?.autoSyncEnabled) {
        mutableStateOf(savedCfg?.autoSyncEnabled ?: false)
    }
    var autoFixWorkflowBugs by remember(project.id, savedCfg?.autoFixWorkflowBugs) {
        mutableStateOf(savedCfg?.autoFixWorkflowBugs ?: false)
    }

    LaunchedEffect(state.gitHubVerifyState) {
        val verified = state.gitHubVerifyState as? GitHubVerifyUiState.Verified
        if (verified != null && owner.isBlank()) {
            owner = verified.profile.login
        }
    }

    val isPushing = state.gitHubPushState is GitHubPushUiState.Pushing

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isPushing) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Push to GitHub & Build APK")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Push '${project.name}' to GitHub with a debug APK workflow so GitHub Actions builds your APK automatically (no Play Store keystore required).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Personal Access Token") },
                    placeholder = { Text("ghp_... or github_pat_...") },
                    supportingText = { Text("Needs 'repo' + 'workflow' scopes. Encrypted on-device.") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showToken) "Hide token" else "Show token"
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onVerifyToken(token) },
                        enabled = token.isNotBlank() && state.gitHubVerifyState !is GitHubVerifyUiState.Verifying,
                        modifier = Modifier.testTag("github_verify_button")
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.gitHubVerifyState is GitHubVerifyUiState.Verifying) "Checking..."
                            else "Verify"
                        )
                    }
                    TextButton(
                        onClick = {
                            openUrl("https://github.com/settings/tokens/new?scopes=repo,workflow,delete_repo,read:user&description=AI+Coder+Android")
                        }
                    ) {
                        Text("Create Token")
                    }
                    when (val v = state.gitHubVerifyState) {
                        is GitHubVerifyUiState.Verified -> {
                            Text(
                                text = "@${v.profile.login}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is GitHubVerifyUiState.Error -> {
                            Text(
                                text = v.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> Unit
                    }
                }

                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("GitHub Owner / Username (blank = auto)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_owner_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repo,
                        onValueChange = { repo = it },
                        label = { Text("Repository") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("github_repo_input")
                    )
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("github_branch_input")
                    )
                }

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    label = { Text("Commit Message") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ToggleRow(
                    title = "Private repository (if creating new)",
                    checked = isPrivate,
                    onCheckedChange = { isPrivate = it }
                )
                ToggleRow(
                    title = "Create repository if it doesn't exist",
                    checked = createIfMissing,
                    onCheckedChange = { createIfMissing = it }
                )
                ToggleRow(
                    title = "Add APK build workflow (.github/workflows/build-apk.yml)",
                    checked = includeApkWorkflow,
                    onCheckedChange = { includeApkWorkflow = it }
                )
                ToggleRow(
                    title = "Auto-sync future edits & AI changes to GitHub",
                    checked = autoSyncEnabled,
                    onCheckedChange = { autoSyncEnabled = it }
                )
                ToggleRow(
                    title = "Auto-prompt AI if pushed workflow fails",
                    checked = autoFixWorkflowBugs,
                    onCheckedChange = { autoFixWorkflowBugs = it }
                )

                when (val push = state.gitHubPushState) {
                    is GitHubPushUiState.Pushing -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(
                                    text = push.step,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    is GitHubPushUiState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = push.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    is GitHubPushUiState.Success -> {
                        val res = push.result
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Pushed ${res.filesUploaded} files (${res.commitSha.take(7)})",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                if (res.workflowAdded) {
                                    Text(
                                        text = "Added .github/workflows/build-apk.yml to build the debug APK.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { openUrl(res.repoUrl) }) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Repo")
                                    }
                                    Button(onClick = { openUrl(res.actionsUrl) }) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Actions / APK")
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onCheckBuildStatus(token, res.owner, res.repo) },
                                    enabled = !state.isCheckingWorkflowRun
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (state.isCheckingWorkflowRun) "Checking..." else "Check APK Build Status")
                                }
                            }
                        }
                    }

                    GitHubPushUiState.Idle -> {
                        val lastActions = savedCfg?.lastActionsUrl.orEmpty()
                        val lastRepo = savedCfg?.lastRepoUrl.orEmpty()
                        if (lastRepo.isNotBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { openUrl(lastRepo) }) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Last Repo")
                                }
                                if (lastActions.isNotBlank()) {
                                    OutlinedButton(onClick = { openUrl(lastActions) }) {
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Actions")
                                    }
                                }
                            }
                        }
                    }
                }

                state.latestWorkflowRun?.let { run ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Latest Run: ${run.name}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Status: ${run.status}${run.conclusion?.let { " • Result: $it" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = { openUrl(run.htmlUrl) }) {
                                Text("Open Build Run & Download APK")
                            }
                        }
                    }
                }

                state.workflowCheckError?.let { wfErr ->
                    Text(
                        text = wfErr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPush(
                        token,
                        owner,
                        repo,
                        branch,
                        commitMessage,
                        isPrivate,
                        createIfMissing,
                        includeApkWorkflow,
                        autoSyncEnabled,
                        autoFixWorkflowBugs
                    )
                },
                enabled = !isPushing && token.isNotBlank() && repo.isNotBlank(),
                modifier = Modifier.testTag("github_push_confirm_button")
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isPushing) "Pushing..." else "Push & Build APK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPushing) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
