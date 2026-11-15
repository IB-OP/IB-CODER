package com.example.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassTop
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.ToolCallRecord
import com.example.domain.model.ToolExecutionStatus
import com.example.domain.model.ToolSupportStatus
import com.example.ui.components.MarkdownMessageContent
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenProviders: () -> Unit,
    onOpenWorkspace: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var promptText by rememberSaveable { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(false) }
    var showProjectMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

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
                    Column {
                        Text(
                            text = state.currentSession?.title ?: "AI Coder",
                            style = MaterialTheme.typography.titleMedium,
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
                                color = MaterialTheme.colorScheme.primary,
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
                },
                actions = {
                    IconButton(
                        onClick = { showModelDialog = true },
                        modifier = Modifier.testTag("chat_switch_model_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Switch provider or model")
                    }
                    IconButton(
                        onClick = viewModel::startNewSession,
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
                        onPromptSelected = { prompt -> promptText = prompt },
                        onOpenProviders = onOpenProviders,
                        onCreateProject = { showNewProjectDialog = true },
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
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                        "Ask agent to inspect, edit, or build code..."
                                    } else {
                                        "Message agent or ask to build an Android app..."
                                    }
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5
                        )

                        if (state.isGenerating) {
                            FilledIconButton(
                                onClick = viewModel::stopGeneration,
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
        var projectName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewProjectDialog = false },
            title = { Text("New Workspace Project") },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    placeholder = { Text("e.g. TaskTrackerApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createProjectAndSelect(projectName)
                        showNewProjectDialog = false
                    },
                    enabled = projectName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewProjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyChatState(
    hasProject: Boolean,
    hasModel: Boolean,
    onPromptSelected: (String) -> Unit,
    onOpenProviders: () -> Unit,
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "On-Device AI Coding Agent",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Read, write, edit, search, and scaffold complete Android Gradle apps directly in your workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (!hasModel || !hasProject) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasModel) {
                    Button(
                        onClick = onOpenProviders,
                        modifier = Modifier.testTag("empty_configure_provider_button")
                    ) {
                        Text("Configure Provider")
                    }
                }
                if (!hasProject) {
                    OutlinedButton(
                        onClick = onCreateProject,
                        modifier = Modifier.testTag("empty_create_project_button")
                    ) {
                        Text("Create Project")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Inspect the current workspace with list_files and summarize the architecture.",
                "Build a complete Jetpack Compose Material 3 Android app in this workspace with Gradle Kotlin DSL, MainActivity, Theme, and a working screen.",
                "Search across project files for any TODOs or potential bugs and fix them."
            ).forEach { suggestion ->
                Surface(
                    onClick = { onPromptSelected(suggestion) },
                    shape = RoundedCornerShape(12.dp),
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
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(
            1.dp,
            if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
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
                    text = if (isUser) "You" else "Agent (${message.modelId ?: "AI"})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
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
