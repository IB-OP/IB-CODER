package com.example.ui.screens.terminal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.terminal.TerminalLineKind
import com.example.ui.components.CyberWindowPanel
import com.example.ui.components.cyberScanlineGrid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SilkscreenFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onWorkspaceMutated: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var commandInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.session.lines.size) {
        if (state.session.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.session.lines.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "TERMINAL // SH",
                                fontFamily = SilkscreenFamily,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                            Text(
                                text = buildString {
                                    append(state.activeProject?.name ?: "shell-home")
                                    if (state.session.cwdRelative != ".") {
                                        append("/${state.session.cwdRelative}")
                                    }
                                    state.lastExitCode?.let { append(" • exit=$it") }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.testTag("terminal_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenWorkspace,
                        modifier = Modifier.testTag("terminal_open_workspace_button")
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = "Open Code Workbench")
                    }
                    IconButton(
                        onClick = viewModel::clearTerminal,
                        modifier = Modifier.testTag("terminal_clear_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear terminal")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick-Install & Shell Utility Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickCommands = listOf(
                        "+ React Vite" to "npx create-vite .",
                        "+ npm react" to "npm install react react-dom",
                        "+ Gradle Wrapper" to "ibpkg install gradle",
                        "device status" to "device status",
                        "ibpkg list" to "ibpkg list",
                        "ls -la" to "ls -la",
                        "help" to "help"
                    )
                    quickCommands.forEach { (label, cmd) ->
                        AssistChip(
                            onClick = { viewModel.runCommand(cmd, onWorkspaceMutated) },
                            enabled = !state.session.isRunning,
                            label = {
                                Text(
                                    text = label,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp
                                )
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                    }
                }

                // Cyber-CRT Framed Console Output Window
                CyberWindowPanel(
                    title = if (state.session.isRunning) {
                        "RUNNING // ${state.session.activeCommand.orEmpty()}"
                    } else {
                        "TERMINAL // CORE_01"
                    },
                    accentColor = MaterialTheme.colorScheme.primary,
                    badgeText = if (state.activeProject != null) state.activeProject!!.name else "LOCAL",
                    headerActions = {
                        if (state.session.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .cyberScanlineGrid(accentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("terminal_output_list"),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(state.session.lines, key = { it.id }) { line ->
                                    val color = when (line.kind) {
                                        TerminalLineKind.COMMAND -> MaterialTheme.colorScheme.primary
                                        TerminalLineKind.STDOUT -> MaterialTheme.colorScheme.onSurface
                                        TerminalLineKind.STDERR -> MaterialTheme.colorScheme.error
                                        TerminalLineKind.SYSTEM -> MaterialTheme.colorScheme.secondary
                                        TerminalLineKind.SUCCESS -> MaterialTheme.colorScheme.tertiary
                                    }
                                    Text(
                                        text = line.text,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = if (line.kind == TerminalLineKind.COMMAND) FontWeight.SemiBold else FontWeight.Normal,
                                        color = color
                                    )
                                }
                            }
                        }
                    }

                    // Recent command history recall strip
                    if (state.commandHistory.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HIST:",
                                fontFamily = SilkscreenFamily,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.commandHistory.take(8).forEach { histCmd ->
                                Surface(
                                    onClick = { commandInput = histCmd },
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(3.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = histCmd,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Command Input Bar
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.25.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (commandInput.isEmpty()) {
                                Text(
                                    text = "npm install react | ibpkg install gradle | ls -la",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (commandInput.isNotBlank() && !state.session.isRunning) {
                                            val cmd = commandInput
                                            commandInput = ""
                                            viewModel.runCommand(cmd, onWorkspaceMutated)
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("terminal_command_input")
                            )
                        }

                        if (state.session.isRunning) {
                            FilledIconButton(
                                onClick = viewModel::stopCommand,
                                shape = RoundedCornerShape(4.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("terminal_stop_button")
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop process")
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    val cmd = commandInput
                                    commandInput = ""
                                    viewModel.runCommand(cmd, onWorkspaceMutated)
                                },
                                enabled = commandInput.isNotBlank(),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("terminal_run_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Execute command"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
