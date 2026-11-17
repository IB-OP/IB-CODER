package com.example.ui.screens.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.WorkspaceProject
import com.example.ui.components.CreateProjectDialog
import com.example.ui.screens.github.GitHubScreen
import com.example.ui.screens.github.GitHubViewModel
import com.example.ui.screens.sessions.SessionsScreen
import com.example.ui.screens.sessions.SessionsViewModel
import com.example.ui.screens.workspace.WorkspaceViewModel
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.JetBrainsMonoFamily

private enum class ProjectsHubTab(val label: String) {
    WORKSPACES("Workspaces"),
    SESSIONS("Sessions"),
    GITHUB("GitHub")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectsScreen(
    workspaceViewModel: WorkspaceViewModel,
    sessionsViewModel: SessionsViewModel,
    gitHubViewModel: GitHubViewModel,
    onOpenCode: () -> Unit,
    onOpenChat: () -> Unit,
    onLaunchAiFix: (projectId: String?, prompt: String) -> Unit,
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableStateOf(ProjectsHubTab.WORKSPACES) }
    var showCreateProject by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<WorkspaceProject?>(null) }
    var projectToDelete by remember { mutableStateOf<WorkspaceProject?>(null) }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            workspaceViewModel.createProjectFromDeviceFolder(uri)
            onOpenCode()
        }
    }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            workspaceViewModel.importZip(context, uri)
            onOpenCode()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .consumeWindowInsets(WindowInsets.statusBars)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onOpenSidebar != null) {
                            IconButton(
                                onClick = onOpenSidebar,
                                modifier = Modifier.testTag("projects_menu_button")
                            ) {
                                Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PROJECTS & HUB",
                                style = BrandWordmarkStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${state.projects.size} workspace(s) • Sessions • GitHub CI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Button(
                        onClick = { showCreateProject = true },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("projects_hub_new_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Project")
                    }
                }

                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ProjectsHubTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        ProjectsHubTab.WORKSPACES -> Icons.Rounded.Folder
                                        ProjectsHubTab.SESSIONS -> Icons.Rounded.Forum
                                        ProjectsHubTab.GITHUB -> Icons.Rounded.CloudSync
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("projects_subtab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            when (selectedTab) {
                ProjectsHubTab.WORKSPACES -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 860.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openFolderLauncher.launch(null) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("projects_pick_files_folder_button")
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Files App Folder")
                            }
                            OutlinedButton(
                                onClick = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("projects_import_zip_button")
                            ) {
                                Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import Zip")
                            }
                            OutlinedButton(
                                onClick = { selectedTab = ProjectsHubTab.GITHUB },
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Direct GitHub main")
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (state.projects.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(52.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text("No Workspaces Yet", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Pick a folder from the Files app, connect directly to a GitHub main branch, or create a workspace.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(state.projects, key = { it.id }) { proj ->
                                    val isActive = proj.id == state.activeProject?.id
                                    val isGhLive = isActive && state.activeProjectGitHubConfig?.directGitHubMode == true

                                    Card(
                                        onClick = {
                                            workspaceViewModel.selectProject(proj.id)
                                            onOpenCode()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isActive) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerLow
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("project_card_${proj.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if (proj.isDeviceFolder) Icons.Rounded.PhoneAndroid else Icons.Rounded.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = proj.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontFamily = JetBrainsMonoFamily,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = when {
                                                            proj.isDeviceFolder -> "Files App Folder"
                                                            isGhLive -> "GitHub Live (${state.activeProjectGitHubConfig?.owner}/${state.activeProjectGitHubConfig?.repo})"
                                                            else -> "App Storage Workspace"
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isActive) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        workspaceViewModel.selectProject(proj.id)
                                                        onOpenCode()
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Code")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        workspaceViewModel.selectProject(proj.id)
                                                        onOpenChat()
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Chat")
                                                }
                                                Spacer(Modifier.weight(1f))
                                                IconButton(
                                                    onClick = {
                                                        workspaceViewModel.selectProject(proj.id)
                                                        projectToRename = proj
                                                    }
                                                ) {
                                                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename project")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        workspaceViewModel.selectProject(proj.id)
                                                        projectToDelete = proj
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = "Delete or unlink project",
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

                ProjectsHubTab.SESSIONS -> {
                    SessionsScreen(
                        viewModel = sessionsViewModel,
                        activeProjectName = state.activeProject?.name,
                        onOpenChat = onOpenChat
                    )
                }

                ProjectsHubTab.GITHUB -> {
                    GitHubScreen(
                        viewModel = gitHubViewModel,
                        onLaunchAiFix = onLaunchAiFix,
                        onOpenWorkspace = onOpenCode
                    )
                }
            }
        }
    }

    if (showCreateProject) {
        CreateProjectDialog(
            isGitHubSignedIn = state.savedGitHubToken.isNotBlank(),
            defaultGitHubOwner = state.defaultGitHubOwner,
            repositories = state.availableGitHubRepos,
            isLoadingRepos = state.isLoadingGitHubRepos,
            isBusy = state.isBusy,
            onPickDeviceFolder = {
                showCreateProject = false
                openFolderLauncher.launch(null)
            },
            onCreateLocalProject = { name ->
                workspaceViewModel.createProject(name)
                showCreateProject = false
                onOpenCode()
            },
            onCreateDirectGitHubProject = { owner, repo, branch, isPrivate, createNewRepo, includeWorkflow, autoFix ->
                workspaceViewModel.createDirectGitHubProject(
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    isPrivate = isPrivate,
                    createNewRepo = createNewRepo,
                    includeWorkflow = includeWorkflow,
                    autoFixWorkflowBugs = autoFix
                )
                showCreateProject = false
                onOpenCode()
            },
            onLoadRepositories = workspaceViewModel::loadGitHubRepositories,
            onOpenGitHubAccount = {
                showCreateProject = false
                selectedTab = ProjectsHubTab.GITHUB
            },
            onDismiss = { showCreateProject = false }
        )
    }

    projectToRename?.let { proj ->
        var newName by remember(proj.id) { mutableStateOf(proj.name) }
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceViewModel.renameActiveProject(newName)
                        projectToRename = null
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) { Text("Cancel") }
            }
        )
    }

    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text(if (proj.isDeviceFolder) "Unlink Device Folder" else "Delete Project") },
            text = {
                Text(
                    if (proj.isDeviceFolder) {
                        "Remove '${proj.name}' from IB CODER? Your real folder in the Files app will NOT be deleted."
                    } else {
                        "Permanently delete '${proj.name}' and all its files?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        workspaceViewModel.deleteActiveProject()
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (proj.isDeviceFolder) "Remove" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
