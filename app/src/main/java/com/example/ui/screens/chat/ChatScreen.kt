package com.example.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.SessionMode
import com.example.domain.model.ToolCallRecord
import com.example.domain.model.ToolExecutionStatus
import com.example.domain.model.ToolSupportStatus
import com.example.domain.prompt.SystemPromptPresets
import com.example.ui.components.CreateNewSessionDialog
import com.example.ui.components.CreateProjectDialog
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenMcpServers: () -> Unit,
    onOpenGitHub: () -> Unit = {},
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var promptText by rememberSaveable { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showEditPromptDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.createProjectFromDeviceFolder(uri)
    }

    val displayedMessages = remember(state.messages, state.streamingMessage) {
        val base = state.messages.filter { it.role != ChatRole.SYSTEM && it.role != ChatRole.TOOL }
        if (state.streamingMessage != null) base + state.streamingMessage!! else base
    }

    LaunchedEffect(displayedMessages.size, state.streamingMessage?.content?.length) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ib_coder_logo),
                            contentDescription = "IB CODER logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                text = state.currentSession?.title ?: "IB CODER",
                                style = if (state.currentSession == null) BrandWordmarkStyle else MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${state.selectedProviderId} / ${state.selectedModelId.ifBlank { "no model" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                state.currentSession?.let { s ->
                                    if (s.totalTokens > 0) {
                                        Text(
                                            text = "• ${s.totalTokens} tok",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("chat_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showModelDialog = true },
                        modifier = Modifier.testTag("chat_switch_model_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Switch provider or model")
                    }
                    IconButton(
                        onClick = { showNewSessionDialog = true },
                        modifier = Modifier.testTag("chat_new_session_button")
                    ) {
                        Icon(Icons.Rounded.AddComment, contentDescription = "New chat session")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 860.dp)
            ) {
                // Workspace + Model Status Ribbon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        AssistChip(
                            onClick = { showProjectMenu = true },
                            label = {
                                Text(
                                    state.activeProject?.name ?: "No Project Selected",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("chat_project_chip")
                        )
                        DropdownMenu(
                            expanded = showProjectMenu,
                            onDismissRequest = { showProjectMenu = false }
                        ) {
                            state.allProjects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.name) },
                                    onClick = {
                                        viewModel.selectActiveProject(proj.id)
                                        showProjectMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ New Project") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    showProjectMenu = false
                                    showNewProjectDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Workspace Files") },
                                leadingIcon = { Icon(Icons.Rounded.Code, contentDescription = null) },
                                onClick = {
                                    showProjectMenu = false
                                    onOpenWorkspace()
                                }
                            )
                        }
                    }

                    AssistChip(
                        onClick = { showModelDialog = true },
                        label = {
                            Text(
                                state.selectedModelId.ifBlank { "Pick Model" },
                                fontFamily = JetBrainsMonoFamily
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chat_model_chip")
                    )

                    AssistChip(
                        onClick = onOpenProviders,
                        label = { Text("Providers & Keys") },
                        modifier = Modifier.testTag("chat_providers_chip")
                    )

                    AssistChip(
                        onClick = onOpenMcpServers,
                        label = {
                            Text(
                                if (state.mcpToolCount > 0) "MCP (${state.mcpToolCount})"
                                else if (state.mcpEnabledServerCount > 0) "MCP (${state.mcpEnabledServerCount})"
                                else "MCP"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chat_mcp_chip")
                    )

                    AssistChip(
                        onClick = { showEditPromptDialog = true },
                        label = {
                            val mode = state.currentSessionPrompt?.mode ?: state.currentSession?.mode
                            val title = state.currentSessionPrompt?.presetTitle ?: state.currentSession?.presetTitle
                            Text(
                                text = when (mode) {
                                    SessionMode.NORMAL -> "Normal Q&A"
                                    SessionMode.SPECIAL -> "Special: ${title ?: "Prompt"}"
                                    null -> "Session Mode"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.QuestionAnswer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("chat_session_mode_chip")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)),
                        modifier = Modifier.testTag("chat_web_live_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = "Live internet search enabled",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Web Live",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    if (state.activeGitHubConfig != null) {
                        val syncLabel = when (val s = state.activeSyncStatus) {
                            is GitHubSyncStatus.Syncing -> "Syncing..."
                            is GitHubSyncStatus.Synced -> "Synced ${s.commitSha.take(7)}"
                            is GitHubSyncStatus.Error -> "Sync Error"
                            GitHubSyncStatus.Idle -> if (state.activeGitHubConfig!!.autoSyncEnabled) "Auto-Sync On" else "GitHub Sync"
                        }
                        AssistChip(
                            onClick = onOpenGitHub,
                            label = { Text(syncLabel) },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("chat_github_sync_chip")
                        )
                    }
                }

                // Failed Workflow Alert Banner with 1-tap AI Auto-Fix
                state.pendingWorkflowAlert?.let { alert ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Actions Failure: ${alert.summary}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "${alert.owner}/${alert.repo} (${alert.branch})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Button(
                                onClick = viewModel::fixPendingWorkflowAlert,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("chat_fix_workflow_ai_button")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fix with AI")
                            }
                            IconButton(
                                onClick = viewModel::dismissWorkflowAlert,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss workflow alert",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Tool calling unsupported warning banner
                AnimatedVisibility(visible = state.toolSupportStatus == ToolSupportStatus.UNSUPPORTED) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "This model does not support tool calling; file read/write/edit tools are disabled for this turn.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Error Banner
                state.errorBanner?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissError,
                                modifier = Modifier.size(32.dp)
                            ) {
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

                // Message List or Empty State
                if (displayedMessages.isEmpty()) {
                    EmptyChatState(
                        hasProject = state.activeProject != null,
                        hasModel = state.selectedModelId.isNotBlank(),
                        sessionMode = state.currentSessionPrompt?.mode ?: state.currentSession?.mode,
                        presetTitle = state.currentSessionPrompt?.presetTitle ?: state.currentSession?.presetTitle,
                        onPromptSelected = { prompt -> promptText = prompt },
                        onOpenProviders = onOpenProviders,
                        onCreateProject = { showNewProjectDialog = true },
                        onNewSession = { showNewSessionDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayedMessages, key = { it.id }) { message ->
                            ChatMessageBubble(message = message)
                        }
                    }
                }

                // Session Token Usage Footer
                state.currentSession?.let { s ->
                    if (s.totalTokens > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Session tokens: ${s.promptTokens} in / ${s.completionTokens} out / ${s.totalTokens} total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Composer Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = promptText,
                            onValueChange = { promptText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp, max = 140.dp)
                                .testTag("chat_prompt_input"),
                            placeholder = {
                                Text(
                                    if (state.activeProject != null) {
                                        "Ask IB_AI to build, research live web, or edit workspace..."
                                    } else {
                                        "Ask anything, search the live web, or build any project..."
                                    }
                                )
                            },
                            shape = RoundedCornerShape(6.dp),
                            maxLines = 5
                        )

                        if (state.isGenerating) {
                            FilledIconButton(
                                onClick = viewModel::stopGeneration,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_stop_button"),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop generation")
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    val msg = promptText
                                    promptText = ""
                                    viewModel.sendMessage(msg)
                                },
                                enabled = promptText.isNotBlank(),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_send_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send message"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete File Confirmation Dialog (Required by Agent Tools)
    state.pendingDeletion?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.resolveDeletionConfirmation(false) },
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Confirm Deletion") },
            text = {
                Text(
                    "The AI agent wants to permanently delete ${if (pending.isDirectory) "folder" else "file"}:\n\n${pending.relativePath}\n\nAllow this action?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.resolveDeletionConfirmation(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_agent_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.resolveDeletionConfirmation(false) },
                    modifier = Modifier.testTag("cancel_agent_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick Provider / Model Switcher Dialog
    if (showModelDialog) {
        SwitchProviderModelDialog(
            currentProviderId = state.selectedProviderId,
            currentModelId = state.selectedModelId,
            options = state.providerOptions,
            onConfirm = { provId, modId ->
                viewModel.selectProviderAndModel(provId, modId)
                showModelDialog = false
            },
            onOpenProviderSettings = {
                showModelDialog = false
                onOpenProviders()
            },
            onDismiss = { showModelDialog = false }
        )
    }

    if (showNewProjectDialog) {
        CreateProjectDialog(
            isGitHubSignedIn = state.isGitHubSignedIn,
            defaultGitHubOwner = state.defaultGitHubOwner,
            repositories = state.availableGitHubRepos,
            isLoadingRepos = state.isLoadingGitHubRepos,
            isBusy = state.isCreatingProject,
            onPickDeviceFolder = {
                showNewProjectDialog = false
                openFolderLauncher.launch(null)
            },
            onCreateLocalProject = { name ->
                viewModel.createProjectAndSelect(name)
                showNewProjectDialog = false
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
                showNewProjectDialog = false
            },
            onLoadRepositories = viewModel::loadGitHubRepositories,
            onOpenGitHubAccount = {
                showNewProjectDialog = false
                onOpenGitHub()
            },
            onDismiss = { showNewProjectDialog = false }
        )
    }

    if (showNewSessionDialog) {
        CreateNewSessionDialog(
            hasActiveProject = state.activeProject != null,
            activeProjectName = state.activeProject?.name,
            onStartSession = { mode, presetId, presetTitle, prompt, attachProject ->
                viewModel.startNewConfiguredSession(
                    mode = mode,
                    presetId = presetId,
                    presetTitle = presetTitle,
                    systemPrompt = prompt,
                    attachActiveProject = attachProject
                )
                showNewSessionDialog = false
            },
            onDismiss = { showNewSessionDialog = false }
        )
    }

    if (showEditPromptDialog) {
        val currentPrompt = state.currentSessionPrompt?.systemPrompt
            ?: if ((state.currentSession?.mode ?: SessionMode.SPECIAL) == SessionMode.NORMAL) {
                SystemPromptPresets.NORMAL_QA_PROMPT
            } else {
                SystemPromptPresets.PRESETS.first().prompt
            }
        var promptDraft by remember(state.currentSession?.id, currentPrompt) {
            mutableStateOf(currentPrompt)
        }
        AlertDialog(
            onDismissRequest = { showEditPromptDialog = false },
            title = { Text("Active Session System Prompt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.currentSession == null) {
                            "Start a New Session to pick Normal Q&A or a Special System Prompt."
                        } else {
                            "Edit the system instructions used for this session:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = promptDraft,
                        onValueChange = { promptDraft = it },
                        enabled = state.currentSession != null,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 260.dp)
                            .testTag("chat_edit_session_prompt_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateActiveSessionSystemPrompt(promptDraft)
                        showEditPromptDialog = false
                    },
                    enabled = state.currentSession != null && promptDraft.isNotBlank()
                ) {
                    Text("Save Prompt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPromptDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun EmptyChatState(
    hasProject: Boolean,
    hasModel: Boolean,
    sessionMode: SessionMode?,
    presetTitle: String?,
    onPromptSelected: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onCreateProject: () -> Unit,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
            modifier = Modifier.size(76.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ib_coder_logo),
                contentDescription = "IB CODER emblem",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "IB CODER",
            style = MaterialTheme.typography.displaySmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (sessionMode) {
                SessionMode.NORMAL -> "Normal Q&A & Live Internet Research session"
                SessionMode.SPECIAL -> "Special Session • ${presetTitle ?: "Custom System Prompt"}"
                null -> "Web, Python, Android, automation, docs, and live internet research — all in one AI workbench."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onNewSession,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("empty_new_session_button")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("New Session")
            }
            if (!hasModel) {
                OutlinedButton(
                    onClick = onOpenProviders,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("empty_configure_provider_button")
                ) {
                    Text("Configure Provider")
                }
            }
            if (!hasProject) {
                OutlinedButton(
                    onClick = onCreateProject,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("empty_create_project_button")
                ) {
                    Text("Create Project")
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Search the live web for the latest Kotlin or React documentation and summarize key changes.",
                "Build a responsive React/HTML dashboard or Python FastAPI service right inside the active workspace.",
                "Find OpenAI-compatible API documentation for a provider and explain the endpoint & auth headers.",
                "Inspect the active workspace files and help me fix bugs or add new features."
            ).forEach { suggestion ->
                Surface(
                    onClick = { onPromptSelected(suggestion) },
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessageUi) {
    val isUser = message.role == ChatRole.USER
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isUser) "[USER]" else "[IB_AI] • ${message.modelId ?: "AI"}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                if (message.isStreaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Streaming", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (message.reasoning.isNotBlank()) {
                var showThinking by remember { mutableStateOf(false) }
                Surface(
                    onClick = { showThinking = !showThinking },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Reasoning", style = MaterialTheme.typography.labelMedium)
                            Icon(
                                if (showThinking) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle reasoning",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(visible = showThinking) {
                            Text(
                                text = message.reasoning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            if (message.content.isNotBlank()) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge
                    )
                } else {
                    MarkdownMessageContent(content = message.content)
                }
            }

            if (message.toolCalls.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    message.toolCalls.forEach { tc ->
                        InlineToolCallCard(toolCall = tc)
                    }
                }
            }

            message.errorMessage?.let { err ->
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
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineToolCallCard(toolCall: ToolCallRecord) {
    var expanded by remember { mutableStateOf(false) }

    val (icon, tint, statusLabel) = when (toolCall.status) {
        ToolExecutionStatus.RUNNING -> Triple(
            Icons.Rounded.HourglassTop,
            MaterialTheme.colorScheme.primary,
            "Running"
        )
        ToolExecutionStatus.AWAITING_CONFIRMATION -> Triple(
            Icons.Rounded.WarningAmber,
            MaterialTheme.colorScheme.secondary,
            "Confirm"
        )
        ToolExecutionStatus.SUCCESS -> Triple(
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.tertiary,
            "Done"
        )
        ToolExecutionStatus.CANCELLED -> Triple(
            Icons.Rounded.Close,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Cancelled"
        )
        ToolExecutionStatus.ERROR -> Triple(
            Icons.Rounded.ErrorOutline,
            MaterialTheme.colorScheme.error,
            "Error"
        )
    }

    Surface(
        onClick = { expanded = !expanded },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Build,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = toolCall.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = JetBrainsMonoFamily,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    icon,
                    contentDescription = statusLabel,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle tool output",
                    modifier = Modifier.size(18.dp)
                )
            }

            if (toolCall.argumentsJson.isNotBlank() && toolCall.argumentsJson != "{}") {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = toolCall.argumentsJson.take(180),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 12 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(visible = expanded && toolCall.resultText.isNotBlank()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = toolCall.resultText.take(3000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchProviderModelDialog(
    currentProviderId: String,
    currentModelId: String,
    options: List<ProviderOptionUi>,
    onConfirm: (String, String) -> Unit,
    onOpenProviderSettings: () -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedOption?.metadata?.displayName ?: selectedProviderId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = {
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "Select provider")
                        },
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
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        options.forEach { opt ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = opt.metadata.displayName,
                                            fontWeight = if (opt.config.enabled) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (opt.config.enabled) {
                                            Text(
                                                "READY",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    selectedProviderId = opt.metadata.id
                                    providerMenuOpen = false
                                }
                            )
                        }
                    }
                }

                val fetchedModels = selectedOption?.config?.fetchedModels.orEmpty()
                if (fetchedModels.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("Model ID (${fetchedModels.size} available)") },
                            trailingIcon = {
                                IconButton(onClick = { modelMenuOpen = true }) {
                                    Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose fetched model")
                                }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                        )
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            fetchedModels.take(60).forEach { id ->
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
                } else {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        label = { Text("Model ID") },
                        placeholder = { Text(selectedOption?.metadata?.modelPlaceholder ?: "Enter model ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily)
                    )
                }

                TextButton(onClick = onOpenProviderSettings) {
                    Text("Configure API Keys & Fetch /models")
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
