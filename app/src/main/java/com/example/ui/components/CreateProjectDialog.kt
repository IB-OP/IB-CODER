package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.GitHubRepositorySummary
import com.example.ui.theme.JetBrainsMonoFamily

private enum class ProjectSourceMode(val label: String) {
    FILES_APP("Files App"),
    GITHUB_MAIN("GitHub main"),
    LOCAL("App Storage")
}

@Composable
fun CreateProjectDialog(
    isGitHubSignedIn: Boolean,
    defaultGitHubOwner: String,
    repositories: List<GitHubRepositorySummary>,
    isLoadingRepos: Boolean,
    isBusy: Boolean,
    onPickDeviceFolder: () -> Unit,
    onCreateLocalProject: (name: String) -> Unit,
    onCreateDirectGitHubProject: (
        owner: String,
        repo: String,
        branch: String,
        isPrivate: Boolean,
        createNewRepo: Boolean,
        includeWorkflow: Boolean,
        autoFixWorkflowBugs: Boolean
    ) -> Unit,
    onLoadRepositories: () -> Unit,
    onOpenGitHubAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(ProjectSourceMode.FILES_APP) }
    var localName by remember { mutableStateOf("") }

    var createNewRepo by remember { mutableStateOf(false) }
    var ghOwner by remember(defaultGitHubOwner) { mutableStateOf(defaultGitHubOwner) }
    var ghRepo by remember { mutableStateOf("") }
    var ghBranch by remember { mutableStateOf("main") }
    var ghPrivate by remember { mutableStateOf(true) }
    var ghIncludeWorkflow by remember { mutableStateOf(true) }
    var ghAutoFix by remember { mutableStateOf(true) }
    var repoDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(mode, isGitHubSignedIn) {
        if (mode == ProjectSourceMode.GITHUB_MAIN && isGitHubSignedIn && repositories.isEmpty()) {
            onLoadRepositories()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Create New Project")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProjectSourceMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = {
                                Text(
                                    option.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_project_mode_${option.name.lowercase()}")
                        )
                    }
                }

                when (mode) {
                    ProjectSourceMode.FILES_APP -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PhoneAndroid,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Open Folder from Files App",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Opens your Android Files app so you can pick any folder on your phone. That folder becomes your live project workspace for browsing, editing, AI tools, and GitHub sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onPickDeviceFolder,
                                    enabled = !isBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("create_project_pick_folder_button")
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Choose Folder in Files App")
                                }
                            }
                        }
                    }

                    ProjectSourceMode.GITHUB_MAIN -> {
                        if (!isGitHubSignedIn) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Connect GitHub First",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Sign in with your GitHub account to mount all project files directly from your repository's main branch.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenGitHubAccount()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Sign In to GitHub")
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "All files live on GitHub '${ghBranch.ifBlank { "main" }}' — edits & AI changes commit directly.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !createNewRepo,
                                    onClick = { createNewRepo = false },
                                    label = { Text("Existing Repo") }
                                )
                                FilterChip(
                                    selected = createNewRepo,
                                    onClick = { createNewRepo = true },
                                    label = { Text("Create New Repo") }
                                )
                            }

                            if (!createNewRepo) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = {
                                            if (repositories.isEmpty()) onLoadRepositories()
                                            repoDropdownOpen = true
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("create_project_pick_github_repo_button")
                                    ) {
                                        if (isLoadingRepos) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Loading your repositories...")
                                        } else {
                                            Text(
                                                if (repositories.isEmpty()) "Load Your GitHub Repos"
                                                else "Pick From Your Repos (${repositories.size})"
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = repoDropdownOpen && repositories.isNotEmpty(),
                                        onDismissRequest = { repoDropdownOpen = false },
                                        modifier = Modifier.heightIn(max = 260.dp)
                                    ) {
                                        repositories.forEach { summary ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(summary.fullName, fontWeight = FontWeight.Medium)
                                                        Text(
                                                            "branch: ${summary.defaultBranch}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    ghOwner = summary.ownerLogin
                                                    ghRepo = summary.name
                                                    ghBranch = summary.defaultBranch.ifBlank { "main" }
                                                    ghPrivate = summary.isPrivate
                                                    repoDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = ghOwner,
                                onValueChange = { ghOwner = it },
                                label = { Text("GitHub Owner (blank = auto)") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = ghRepo,
                                    onValueChange = { ghRepo = it },
                                    label = { Text("Repository") },
                                    placeholder = { Text("my-app") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .testTag("create_project_github_repo_input")
                                )
                                OutlinedTextField(
                                    value = ghBranch,
                                    onValueChange = { ghBranch = it },
                                    label = { Text("Branch") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("create_project_github_branch_input")
                                )
                            }

                            if (createNewRepo) {
                                CompactSwitchRow(
                                    label = "Private repository",
                                    checked = ghPrivate,
                                    onCheckedChange = { ghPrivate = it }
                                )
                                CompactSwitchRow(
                                    label = "Add APK & Play Store .aab workflow",
                                    checked = ghIncludeWorkflow,
                                    onCheckedChange = { ghIncludeWorkflow = it }
                                )
                            }
                            CompactSwitchRow(
                                label = "Auto-prompt AI if GitHub Actions fails",
                                checked = ghAutoFix,
                                onCheckedChange = { ghAutoFix = it }
                            )
                        }
                    }

                    ProjectSourceMode.LOCAL -> {
                        OutlinedTextField(
                            value = localName,
                            onValueChange = { localName = it },
                            label = { Text("Project Name") },
                            placeholder = { Text("e.g. TaskTrackerApp") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_project_local_name_input")
                        )
                        Text(
                            text = "Creates an isolated Android workspace inside IB CODER that you can export as zip or push to GitHub anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (mode) {
                ProjectSourceMode.FILES_APP -> Unit
                ProjectSourceMode.GITHUB_MAIN -> {
                    if (isGitHubSignedIn) {
                        Button(
                            onClick = {
                                onCreateDirectGitHubProject(
                                    ghOwner,
                                    ghRepo,
                                    ghBranch,
                                    ghPrivate,
                                    createNewRepo,
                                    ghIncludeWorkflow,
                                    ghAutoFix
                                )
                            },
                            enabled = !isBusy && ghRepo.isNotBlank(),
                            modifier = Modifier.testTag("create_project_github_direct_button")
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (createNewRepo) "Create on main" else "Open Direct")
                        }
                    }
                }
                ProjectSourceMode.LOCAL -> {
                    Button(
                        onClick = { onCreateLocalProject(localName) },
                        enabled = !isBusy && localName.isNotBlank(),
                        modifier = Modifier.testTag("create_project_blank_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CompactSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
