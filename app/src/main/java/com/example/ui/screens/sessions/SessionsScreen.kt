package com.example.ui.screens.sessions

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ChatSession
import com.example.domain.model.SessionMode
import com.example.ui.components.CreateNewSessionDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    viewModel: SessionsViewModel,
    activeProjectName: String? = null,
    onOpenChat: () -> Unit,
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<ChatSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Sessions", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("sessions_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSessionDialog = true },
                modifier = Modifier.testTag("sessions_new_fab")
            ) {
                Icon(Icons.Rounded.AddComment, contentDescription = "Start new chat session")
            }
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
                    .widthIn(max = 860.dp)
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("sessions_search_input"),
                    placeholder = { Text("Search sessions and message history...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )

                if (state.sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (state.searchQuery.isBlank()) "No Saved Sessions" else "No Matching Sessions",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Start a conversation in Chat and it will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.sessions, key = { it.id }) { session ->
                            val isActive = session.id == state.activeSessionId
                            val projectName = session.projectId?.let { state.projectsById[it]?.name }
                            val dateStr = remember(session.updatedAt) {
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(session.updatedAt))
                            }

                            Card(
                                onClick = {
                                    viewModel.openSession(session.id)
                                    onOpenChat()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("session_card_${session.id}"),
                                shape = RoundedCornerShape(14.dp),
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
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = session.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${session.providerId} / ${session.modelId.ifBlank { "default" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = if (session.mode == SessionMode.NORMAL) {
                                                    "Normal Q&A"
                                                } else {
                                                    session.presetTitle ?: "Special Prompt"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            if (projectName != null) {
                                                Text(
                                                    text = projectName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            if (session.totalTokens > 0) {
                                                Text(
                                                    text = "${session.totalTokens} tok",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = dateStr,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { sessionToRename = session },
                                        modifier = Modifier.testTag("session_rename_${session.id}")
                                    ) {
                                        Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename session")
                                    }
                                    IconButton(
                                        onClick = { sessionToDelete = session },
                                        modifier = Modifier.testTag("session_delete_${session.id}")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete session",
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

    sessionToRename?.let { target ->
        var newTitle by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Rename Session") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameSession(target.id, newTitle)
                        sessionToRename = null
                    },
                    enabled = newTitle.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) { Text("Cancel") }
            }
        )
    }

    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session") },
            text = { Text("Delete '${target.title}' and all its messages?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(target.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showCreateSessionDialog) {
        CreateNewSessionDialog(
            hasActiveProject = !activeProjectName.isNullOrBlank(),
            activeProjectName = activeProjectName,
            onStartSession = { mode, presetId, presetTitle, prompt, _ ->
                viewModel.createNewSession(
                    mode = mode,
                    presetId = presetId,
                    presetTitle = presetTitle,
                    systemPrompt = prompt
                )
                showCreateSessionDialog = false
                onOpenChat()
            },
            onDismiss = { showCreateSessionDialog = false }
        )
    }
}
