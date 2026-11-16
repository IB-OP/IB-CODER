package com.example.ui.screens.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.FileNode
import com.example.domain.model.GitHubSyncStatus
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onAskAgent: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var projectMenuOpen by remember { mutableStateOf(false) }
    var showCreateProject by remember { mutableStateOf(false) }
    var showRenameProject by remember { mutableStateOf(false) }
    var showDeleteProject by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showGitHubPushDialog by remember { mutableStateOf(false) }
    var pendingDeleteNode by remember { mutableStateOf<FileNode?>(null) }

    val importZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importZip(context, uri)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportZip(context, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            modifier = Modifier
                                .clickable { projectMenuOpen = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Text(
                                text = state.activeProject?.name ?: "Workspace",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = projectMenuOpen,
                            onDismissRequest = { projectMenuOpen = false }
                        ) {
                            state.projects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.name) },
                                    onClick = {
                                        viewModel.selectProject(proj.id)
                                        projectMenuOpen = false
                                    }
                                )
                            }
                            if (state.projects.isNotEmpty()) HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("New Project") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    projectMenuOpen = false
                                    showCreateProject = true
                                }
                            )
                            if (state.activeProject != null) {
                                DropdownMenuItem(
                                    text = { Text("Rename Project") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = {
                                        projectMenuOpen = false
                                        showRenameProject = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Project") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        projectMenuOpen = false
                                        showDeleteProject = true
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                        modifier = Modifier.testTag("workspace_import_zip_button")
                    ) {
                        Icon(Icons.Rounded.Unarchive, contentDescription = "Import zip project")
                    }
                    if (state.activeProject != null) {
                        IconButton(
                            onClick = {
                                viewModel.resetGitHubDialogFeedback()
                                showGitHubPushDialog = true
                            },
                            modifier = Modifier.testTag("workspace_github_push_icon_button")
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = "Push project to GitHub")
                        }
                        IconButton(
                            onClick = {
                                val safeName = (state.activeProject?.name ?: "project")
                                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                                exportZipLauncher.launch("$safeName.zip")
                            },
                            modifier = Modifier.testTag("workspace_export_zip_button")
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = "Export project as zip")
                        }
                        IconButton(
                            onClick = viewModel::refreshTree,
                            modifier = Modifier.testTag("workspace_refresh_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh files")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            state.statusMessage?.let { msg ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearBanner) { Text("OK") }
                    }
                }
            }

            state.errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearBanner) { Text("Dismiss") }
                    }
                }
            }

            if (state.activeProject == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No Project Yet", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Create a workspace project or import an existing zip archive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showCreateProject = true },
                                modifier = Modifier.testTag("workspace_create_project_button")
                            ) {
                                Text("New Project")
                            }
                            OutlinedButton(
                                onClick = { importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) }
                            ) {
                                Text("Import Zip")
                            }
                        }
                    }
                }
            } else {
                // Action toolbar for active project
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.testTag("workspace_new_file_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New File")
                    }
                    OutlinedButton(
                        onClick = { showNewFolderDialog = true },
                        modifier = Modifier.testTag("workspace_new_folder_button")
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Folder")
                    }
                    Button(
                        onClick = onAskAgent,
                        modifier = Modifier.testTag("workspace_ask_agent_button")
                    ) {
                        Icon(Icons.Rounded.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ask Agent")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetGitHubDialogFeedback()
                            showGitHubPushDialog = true
                        },
                        modifier = Modifier.testTag("workspace_push_github_button")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.activeProjectGitHubConfig?.repo?.isNotBlank() == true) "GitHub"
                            else "Push to GitHub"
                        )
                    }
                    if (state.activeProjectGitHubConfig?.repo?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = viewModel::syncNow,
                            enabled = state.activeProjectSyncStatus !is GitHubSyncStatus.Syncing,
                            modifier = Modifier.testTag("workspace_sync_now_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when (val sync = state.activeProjectSyncStatus) {
                                    is GitHubSyncStatus.Syncing -> "Syncing..."
                                    is GitHubSyncStatus.Synced -> "Synced ${sync.commitSha.take(6)}"
                                    is GitHubSyncStatus.Error -> "Sync Error"
                                    GitHubSyncStatus.Idle ->
                                        if (state.activeProjectGitHubConfig?.autoSyncEnabled == true) "Auto-Sync On"
                                        else "Sync Now"
                                }
                            )
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val isWide = maxWidth >= 700.dp
                    if (isWide) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            FileTreePane(
                                nodes = state.fileTree,
                                selectedPath = state.selectedFilePath,
                                onSelectFile = viewModel::openFile,
                                onDeleteNode = { pendingDeleteNode = it },
                                modifier = Modifier
                                    .width(300.dp)
                                    .fillMaxHeight()
                            )
                            HorizontalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                            CodeViewerPane(
                                filePath = state.selectedFilePath,
                                content = state.selectedFileContent,
                                isEditing = state.isEditingFile,
                                onToggleEdit = viewModel::setEditing,
                                onSave = viewModel::saveFileContent,
                                onClose = null,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    } else {
                        if (state.selectedFilePath == null) {
                            FileTreePane(
                                nodes = state.fileTree,
                                selectedPath = null,
                                onSelectFile = viewModel::openFile,
                                onDeleteNode = { pendingDeleteNode = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CodeViewerPane(
                                filePath = state.selectedFilePath,
                                content = state.selectedFileContent,
                                isEditing = state.isEditingFile,
                                onToggleEdit = viewModel::setEditing,
                                onSave = viewModel::saveFileContent,
                                onClose = viewModel::closeFileViewer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateProject) {
        TextInputDialog(
            title = "Create Project",
            label = "Project Name",
            initialValue = "",
            onConfirm = {
                viewModel.createProject(it)
                showCreateProject = false
            },
            onDismiss = { showCreateProject = false }
        )
    }

    if (showRenameProject && state.activeProject != null) {
        TextInputDialog(
            title = "Rename Project",
            label = "Project Name",
            initialValue = state.activeProject!!.name,
            onConfirm = {
                viewModel.renameActiveProject(it)
                showRenameProject = false
            },
            onDismiss = { showRenameProject = false }
        )
    }

    if (showDeleteProject && state.activeProject != null) {
        AlertDialog(
            onDismissRequest = { showDeleteProject = false },
            title = { Text("Delete Project") },
            text = { Text("Permanently delete '${state.activeProject!!.name}' and all its files?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteActiveProject()
                        showDeleteProject = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProject = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFileDialog) {
        TextInputDialog(
            title = "New File",
            label = "Relative Path (e.g. app/src/main/Main.kt)",
            initialValue = "",
            onConfirm = {
                viewModel.createNewFile(it)
                showNewFileDialog = false
            },
            onDismiss = { showNewFileDialog = false }
        )
    }

    if (showNewFolderDialog) {
        TextInputDialog(
            title = "New Folder",
            label = "Relative Folder Path",
            initialValue = "",
            onConfirm = {
                viewModel.createNewFolder(it)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false }
        )
    }

    pendingDeleteNode?.let { node ->
        AlertDialog(
            onDismissRequest = { pendingDeleteNode = null },
            title = { Text("Delete ${if (node.isDirectory) "Folder" else "File"}") },
            text = { Text("Delete '${node.relativePath}' from the workspace?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFileNode(node.relativePath)
                        pendingDeleteNode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNode = null }) { Text("Cancel") }
            }
        )
    }

    if (showGitHubPushDialog && state.activeProject != null) {
        GitHubPushDialog(
            state = state,
            onVerifyToken = viewModel::verifyGitHubToken,
            onPush = viewModel::pushActiveProjectToGitHub,
            onCheckBuildStatus = viewModel::checkLatestGitHubWorkflowRun,
            onDismiss = { showGitHubPushDialog = false }
        )
    }
}

@Composable
private fun FileTreePane(
    nodes: List<FileNode>,
    selectedPath: String?,
    onSelectFile: (String) -> Unit,
    onDeleteNode: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Workspace is empty. Create a file or ask the AI agent to build an app here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(nodes, key = { it.relativePath }) { node ->
            val isSelected = node.relativePath == selectedPath
            Surface(
                onClick = { if (!node.isDirectory) onSelectFile(node.relativePath) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = RoundedCornerShape(10.dp),
                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = (10 + node.depth * 14).dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                        contentDescription = null,
                        tint = if (node.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = JetBrainsMonoFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!node.isDirectory) {
                        Text(
                            text = "${node.sizeBytes}B",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onDeleteNode(node) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete ${node.name}",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeViewerPane(
    filePath: String?,
    content: String,
    isEditing: Boolean,
    onToggleEdit: (Boolean) -> Unit,
    onSave: (String, String) -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (filePath == null) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Select a file from the tree to inspect or edit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    var draft by remember(filePath, content) { mutableStateOf(content) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val ext = filePath.substringAfterLast('.', "")
    val highlighted = remember(content, ext, isDark) {
        SyntaxHighlighter.highlight(content, ext, isDark)
    }

    Column(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("code_viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to file tree")
                    }
                }
                Text(
                    text = filePath,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                )
                if (isEditing) {
                    TextButton(onClick = {
                        draft = content
                        onToggleEdit(false)
                    }) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onSave(filePath, draft) },
                        modifier = Modifier.testTag("code_viewer_save_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onToggleEdit(true) },
                        modifier = Modifier.testTag("code_viewer_edit_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .testTag("code_editor_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                shape = RoundedCornerShape(12.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = highlighted,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
