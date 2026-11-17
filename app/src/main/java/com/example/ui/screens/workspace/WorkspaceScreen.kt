package com.example.ui.screens.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.FileNode
import com.example.ui.components.CreateNewSessionDialog
import com.example.ui.components.CreateProjectDialog
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.chat.ProviderOptionUi
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    chatViewModel: ChatViewModel,
    onAskAgent: () -> Unit,
    onOpenProviders: () -> Unit = {},
    onOpenMcpServers: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenGitHubAccount: () -> Unit = {},
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var activeSidebarSection by rememberSaveable { mutableStateOf(IdeSidebarSection.EXPLORER) }
    var sidebarVisible by rememberSaveable { mutableStateOf(true) }
    var chatDrawerExpanded by rememberSaveable { mutableStateOf(true) }
    var drawerPromptDraft by rememberSaveable { mutableStateOf("") }

    var showCreateProject by remember { mutableStateOf(false) }
    var showRenameProject by remember { mutableStateOf(false) }
    var showDeleteProject by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showGitHubPushDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showSwitchModelDialog by remember { mutableStateOf(false) }
    var pendingDeleteNode by remember { mutableStateOf<FileNode?>(null) }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.createProjectFromDeviceFolder(uri)
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        IdeTopBar(
            activeProject = state.activeProject,
            allProjects = state.projects,
            providerId = chatState.selectedProviderId,
            modelId = chatState.selectedModelId,
            onSelectProject = viewModel::selectProject,
            onNewProject = { showCreateProject = true },
            onRenameProject = { showRenameProject = true },
            onDeleteProject = { showDeleteProject = true },
            onRefreshTree = viewModel::refreshTree,
            onSwitchModel = { showSwitchModelDialog = true },
            onOpenSidebar = onOpenSidebar
        )

        state.statusMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val sidebarWidth = if (isWideScreen) 232.dp else 184.dp

            Row(modifier = Modifier.fillMaxSize()) {
                // 1. Left IDE Activity Rail
                IdeActivityRail(
                    activeSection = activeSidebarSection,
                    sidebarVisible = sidebarVisible,
                    onSelectSection = { target ->
                        if (sidebarVisible && activeSidebarSection == target) {
                            sidebarVisible = false
                        } else {
                            activeSidebarSection = target
                            sidebarVisible = true
                        }
                    },
                    onOpenProviders = onOpenProviders,
                    onOpenMcp = onOpenMcpServers,
                    onOpenSettings = onOpenSettings
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 2. Collapsible Side Pane (PROJECTS / SEARCH / SOURCE CONTROL)
                if (sidebarVisible) {
                    IdeSidebarPane(
                        section = activeSidebarSection,
                        activeProject = state.activeProject,
                        nodes = state.fileTree,
                        selectedFilePath = state.selectedFilePath,
                        searchQuery = state.sidebarSearchQuery,
                        searchResult = state.sidebarSearchResult,
                        state = state,
                        onCollapse = { sidebarVisible = false },
                        onNewProject = { showCreateProject = true },
                        onPickDeviceFolder = { openFolderLauncher.launch(null) },
                        onSelectFile = { path -> viewModel.openFile(path) },
                        onDeleteNode = { node -> pendingDeleteNode = node },
                        onSearchQueryChanged = viewModel::setSidebarSearchQuery,
                        onRunSearch = { viewModel.runSidebarSearch() },
                        onNewFile = { showNewFileDialog = true },
                        onCommitGitHub = {
                            viewModel.resetGitHubDialogFeedback()
                            showGitHubPushDialog = true
                        },
                        onSyncNow = viewModel::syncNow,
                        onPullGitHub = viewModel::pullFromGitHub,
                        onToggleDirectGitHub = viewModel::toggleDirectGitHubMode,
                        onOpenGitHubAccount = onOpenGitHubAccount,
                        modifier = Modifier.width(sidebarWidth)
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // 3. Center Split: Tabbed Line-Numbered Code Editor + Docked IB_AI Chat Drawer
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    LineNumberedCodeEditorPane(
                        openTabs = state.openTabs,
                        selectedFilePath = state.selectedFilePath,
                        content = state.selectedFileContent,
                        isEditing = state.isEditingFile,
                        hasActiveProject = state.activeProject != null,
                        sidebarVisible = sidebarVisible,
                        onShowSidebar = { sidebarVisible = true },
                        onSelectTab = viewModel::openFile,
                        onCloseTab = viewModel::closeTab,
                        onToggleEdit = viewModel::setEditing,
                        onSave = viewModel::saveFileContent,
                        onNewFile = { showNewFileDialog = true },
                        onNewFolder = { showNewFolderDialog = true },
                        onImportZip = {
                            importZipLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                        },
                        onExportZip = {
                            val safeName = (state.activeProject?.name ?: "project")
                                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                            exportZipLauncher.launch("$safeName.zip")
                        },
                        onOpenFullChat = onAskAgent,
                        modifier = Modifier.weight(1f)
                    )

                    DockedIbAiChatDrawer(
                        chatState = chatState,
                        expanded = chatDrawerExpanded,
                        promptDraft = drawerPromptDraft,
                        onPromptDraftChanged = { drawerPromptDraft = it },
                        onToggleExpanded = { chatDrawerExpanded = !chatDrawerExpanded },
                        onNewSessionClick = { showNewSessionDialog = true },
                        onExpandFullChat = onAskAgent,
                        onSendPrompt = {
                            val msg = drawerPromptDraft
                            drawerPromptDraft = ""
                            chatViewModel.sendMessage(msg)
                        },
                        onStopGeneration = chatViewModel::stopGeneration,
                        onFixWorkflowAlert = {
                            chatViewModel.fixPendingWorkflowAlert()
                            onAskAgent()
                        }
                    )
                }

                // 4. Right Utility Strip (shown on tablets/landscape)
                if (isWideScreen) {
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    IdeRightUtilityStrip(
                        chatExpanded = chatDrawerExpanded,
                        onToggleChat = { chatDrawerExpanded = !chatDrawerExpanded },
                        onNewSession = { showNewSessionDialog = true },
                        onSwitchModel = { showSwitchModelDialog = true },
                        onNewFile = { if (state.activeProject != null) showNewFileDialog = true else showCreateProject = true }
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
                viewModel.createProject(name)
                showCreateProject = false
            },
            onCreateDirectGitHubProject = { owner, repo, branch, isPrivate, createNewRepo, includeWorkflow, autoFix ->
                viewModel.createDirectGitHubProject(
                    owner = owner,
                    repo = repo,
                    branch = branch,
                    isPrivate = isPrivate,
                    createNewRepo = createNewRepo,
                    includeWorkflow = includeWorkflow,
                    autoFixWorkflowBugs = autoFix
                )
                showCreateProject = false
            },
            onLoadRepositories = viewModel::loadGitHubRepositories,
            onOpenGitHubAccount = {
                showCreateProject = false
                onOpenGitHubAccount()
            },
            onDismiss = { showCreateProject = false }
        )
    }

    if (showNewSessionDialog) {
        CreateNewSessionDialog(
            hasActiveProject = state.activeProject != null,
            activeProjectName = state.activeProject?.name,
            onStartSession = { mode, presetId, presetTitle, prompt, attachProject ->
                chatViewModel.startNewConfiguredSession(
                    mode = mode,
                    presetId = presetId,
                    presetTitle = presetTitle,
                    systemPrompt = prompt,
                    attachActiveProject = attachProject
                )
                chatDrawerExpanded = true
                showNewSessionDialog = false
            },
            onDismiss = { showNewSessionDialog = false }
        )
    }

    if (showSwitchModelDialog) {
        SwitchIdeModelDialog(
            currentProviderId = chatState.selectedProviderId,
            currentModelId = chatState.selectedModelId,
            options = chatState.providerOptions,
            onConfirm = { provId, modId ->
                chatViewModel.selectProviderAndModel(provId, modId)
                showSwitchModelDialog = false
            },
            onOpenProviders = {
                showSwitchModelDialog = false
                onOpenProviders()
            },
            onDismiss = { showSwitchModelDialog = false }
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
        val activeProj = state.activeProject!!
        AlertDialog(
            onDismissRequest = { showDeleteProject = false },
            title = { Text(if (activeProj.isDeviceFolder) "Unlink Device Folder" else "Delete Project") },
            text = {
                Text(
                    if (activeProj.isDeviceFolder) {
                        "Remove '${activeProj.name}' from IB CODER? Your real folder and files in the Files app will NOT be deleted."
                    } else {
                        "Permanently delete '${activeProj.name}' and all its files?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteActiveProject()
                        showDeleteProject = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (activeProj.isDeviceFolder) "Remove" else "Delete")
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
            label = "Relative Path (e.g. src/app.js or app/Main.kt)",
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
private fun SwitchIdeModelDialog(
    currentProviderId: String,
    currentModelId: String,
    options: List<ProviderOptionUi>,
    onConfirm: (String, String) -> Unit,
    onOpenProviders: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedProviderId by remember { mutableStateOf(currentProviderId) }
    val selectedOption = options.firstOrNull { it.metadata.id == selectedProviderId }
    var modelInput by remember(selectedProviderId) {
        mutableStateOf(
            if (selectedProviderId == currentProviderId && currentModelId.isNotBlank()) {
                currentModelId
            } else {
                selectedOption?.config?.selectedModel.orEmpty()
            }
        )
    }
    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Switch Provider & Model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedOption?.metadata?.displayName ?: selectedProviderId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { Icon(Icons.Rounded.ExpandMore, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { providerMenuOpen = true }
                    )
                    DropdownMenu(
                        expanded = providerMenuOpen,
                        onDismissRequest = { providerMenuOpen = false },
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        options.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = opt.metadata.displayName,
                                        fontWeight = if (opt.config.enabled) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    selectedProviderId = opt.metadata.id
                                    providerMenuOpen = false
                                }
                            )
                        }
                    }
                }

                val fetched = selectedOption?.config?.fetchedModels.orEmpty()
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text(if (fetched.isNotEmpty()) "Model ID (${fetched.size} fetched)" else "Model ID") },
                        trailingIcon = {
                            if (fetched.isNotEmpty()) {
                                IconButton(onClick = { modelMenuOpen = true }) {
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Pick fetched model")
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = modelMenuOpen,
                        onDismissRequest = { modelMenuOpen = false },
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        fetched.take(50).forEach { id ->
                            DropdownMenuItem(
                                text = { Text(id, fontFamily = JetBrainsMonoFamily) },
                                onClick = {
                                    modelInput = id
                                    modelMenuOpen = false
                                }
                            )
                        }
                    }
                }

                TextButton(onClick = onOpenProviders) {
                    Text("Configure Keys & Custom Providers")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedProviderId, modelInput.trim()) },
                enabled = modelInput.isNotBlank()
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
