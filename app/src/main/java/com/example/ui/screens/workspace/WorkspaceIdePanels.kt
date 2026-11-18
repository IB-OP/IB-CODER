package com.example.ui.screens.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.ChatRole
import com.example.domain.model.FileNode
import com.example.domain.model.GitHubSyncStatus
import com.example.domain.model.SessionMode
import com.example.domain.model.WorkspaceProject
import com.example.ui.components.CyberWindowGlyphs
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.components.cyberScanlineGrid
import com.example.ui.screens.chat.ChatUiState
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.IdeCodeGold
import com.example.ui.theme.IdeFolderAmber
import com.example.ui.theme.IdeFolderCyan
import com.example.ui.theme.IdeHtmlCoral
import com.example.ui.theme.IdeStyleBlue
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SilkscreenFamily

internal enum class IdeSidebarSection(val label: String) {
    EXPLORER("FILES"),
    SEARCH("SEARCH"),
    SOURCE_CONTROL("GITHUB")
}

@Composable
internal fun IdeTopBar(
    activeProject: WorkspaceProject?,
    allProjects: List<WorkspaceProject>,
    providerId: String,
    modelId: String,
    onSelectProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onRenameProject: () -> Unit,
    onDeleteProject: () -> Unit,
    onRefreshTree: () -> Unit,
    onSwitchModel: () -> Unit,
    onOpenSidebar: (() -> Unit)? = null
) {
    var projectMenuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        BoxWithConstraints {
            val showWordmark = maxWidth >= 430.dp

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onOpenSidebar != null) {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("ide_menu_button")
                        ) {
                            Icon(
                                Icons.Rounded.Menu,
                                contentDescription = "Open navigation sidebar",
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    Image(
                        painter = painterResource(R.drawable.ib_coder_logo),
                        contentDescription = "IB CODER",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    if (showWordmark) {
                        Text(
                            text = "IB CODER",
                            style = BrandWordmarkStyle.copy(fontSize = 15.sp),
                            maxLines = 1
                        )
                    }

                    // Active Project Selector Pill (fluid width so portrait phones never clip)
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            onClick = { projectMenuOpen = true },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .widthIn(min = 84.dp, max = 220.dp)
                                .testTag("ide_project_selector")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = activeProject?.name ?: "No Project",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = JetBrainsMonoFamily,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    Icons.Rounded.ExpandMore,
                                    contentDescription = "Switch project",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = projectMenuOpen,
                            onDismissRequest = { projectMenuOpen = false }
                        ) {
                            allProjects.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.name, fontFamily = JetBrainsMonoFamily) },
                                    leadingIcon = {
                                        if (proj.id == activeProject?.id) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        onSelectProject(proj.id)
                                        projectMenuOpen = false
                                    }
                                )
                            }
                            if (allProjects.isNotEmpty()) HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ New Project") },
                                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                onClick = {
                                    projectMenuOpen = false
                                    onNewProject()
                                }
                            )
                            if (activeProject != null) {
                                DropdownMenuItem(
                                    text = { Text("Rename Project") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = {
                                        projectMenuOpen = false
                                        onRenameProject()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (activeProject.isDeviceFolder) "Unlink Folder" else "Delete Project") },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = {
                                        projectMenuOpen = false
                                        onDeleteProject()
                                    }
                                )
                            }
                        }
                    }

                    // Active AI Model Pill
                    Surface(
                        onClick = onSwitchModel,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .widthIn(max = 148.dp)
                            .testTag("ide_model_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Public,
                                contentDescription = "Live Web + Model",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = modelId.ifBlank { providerId }.take(16),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (activeProject != null) {
                        IconButton(
                            onClick = onRefreshTree,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("workspace_refresh_button")
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Refresh files",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun WorkspaceQuickActionBar(
    hasActiveProject: Boolean,
    fileCount: Int,
    filesOpen: Boolean,
    hasOpenFile: Boolean,
    openFileName: String?,
    onToggleFiles: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    onNewFile: () -> Unit,
    onCommitGitHub: () -> Unit,
    onNewSession: () -> Unit,
    onOpenFullChat: () -> Unit,
    onCloseOpenFile: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onToggleFiles,
                    shape = RoundedCornerShape(4.dp),
                    colors = if (filesOpen) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("workspace_show_files_button")
                ) {
                    Icon(
                        Icons.Rounded.FolderCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (hasActiveProject && fileCount > 0) "Files ($fileCount)" else "Files",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onOpenTerminal,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("workspace_open_terminal_button")
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Terminal", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }

                if (hasActiveProject) {
                    OutlinedButton(
                        onClick = onCommitGitHub,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("workspace_push_github_button")
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Commit", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onNewFile,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("workspace_new_file_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("New File", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }

                AssistChip(
                    onClick = onNewSession,
                    shape = RoundedCornerShape(4.dp),
                    label = { Text("New Session", maxLines = 1) },
                    leadingIcon = {
                        Icon(Icons.Rounded.AddComment, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("drawer_new_session_button")
                )

                AssistChip(
                    onClick = onOpenFullChat,
                    shape = RoundedCornerShape(4.dp),
                    label = { Text("Full Chat", maxLines = 1) },
                    leadingIcon = {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier.testTag("drawer_open_full_chat_button")
                )

                if (hasOpenFile && !openFileName.isNullOrBlank()) {
                    AssistChip(
                        onClick = onCloseOpenFile,
                        shape = RoundedCornerShape(4.dp),
                        label = { Text("Close $openFileName", maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("workspace_close_file_chip")
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun WorkspaceFilesExplorerContent(
    section: IdeSidebarSection,
    onSelectSection: (IdeSidebarSection) -> Unit,
    activeProject: WorkspaceProject?,
    nodes: List<FileNode>,
    selectedFilePath: String?,
    searchQuery: String,
    searchResult: String,
    state: WorkspaceUiState,
    onClosePanel: (() -> Unit)? = null,
    onNewProject: () -> Unit,
    onPickDeviceFolder: () -> Unit,
    onSelectFile: (String) -> Unit,
    onDeleteNode: (FileNode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRunSearch: () -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportZip: () -> Unit,
    onExportZip: () -> Unit,
    onCommitGitHub: () -> Unit,
    onSyncNow: () -> Unit,
    onPullGitHub: () -> Unit,
    onToggleDirectGitHub: (Boolean) -> Unit,
    onOpenGitHubAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var moreMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Section Selector Header (FILES | SEARCH | GITHUB)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IdeSidebarSection.entries.forEach { target ->
                val isSelected = section == target
                Surface(
                    onClick = { onSelectSection(target) },
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.testTag("files_tab_${target.name.lowercase()}")
                ) {
                    Text(
                        text = target.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box {
                IconButton(
                    onClick = { moreMenuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Rounded.MoreHoriz, contentDescription = "More project options", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = moreMenuOpen,
                    onDismissRequest = { moreMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Project") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        onClick = {
                            moreMenuOpen = false
                            onNewProject()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Pick Files App Folder") },
                        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                        onClick = {
                            moreMenuOpen = false
                            onPickDeviceFolder()
                        }
                    )
                    if (activeProject != null) {
                        DropdownMenuItem(
                            text = { Text("New File") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null) },
                            onClick = {
                                moreMenuOpen = false
                                onNewFile()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Folder") },
                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                moreMenuOpen = false
                                onNewFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Zip") },
                            onClick = {
                                moreMenuOpen = false
                                onExportZip()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Import Zip") },
                        onClick = {
                            moreMenuOpen = false
                            onImportZip()
                        }
                    )
                }
            }

            if (onClosePanel != null) {
                IconButton(
                    onClick = onClosePanel,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("sidebar_collapse_button")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Hide Files", modifier = Modifier.size(18.dp))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (section) {
                IdeSidebarSection.EXPLORER -> {
                    if (activeProject == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "No project selected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onNewProject,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Create New Project")
                            }
                            OutlinedButton(
                                onClick = onPickDeviceFolder,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pick Files App Folder")
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeProject.name.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (activeProject.isDeviceFolder) {
                                    Icon(
                                        Icons.Rounded.PhoneAndroid,
                                        contentDescription = "Files App Folder",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                TextButton(
                                    onClick = onNewFile,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text("+ File", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            if (nodes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Workspace is empty.\nTap + File or ask IB_AI to create files.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    items(nodes, key = { it.relativePath }) { node ->
                                        val isSelected = node.relativePath == selectedFilePath
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 44.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                    else Color.Transparent
                                                )
                                                .clickable { if (!node.isDirectory) onSelectFile(node.relativePath) }
                                                .padding(
                                                    start = (12 + node.depth * 14).dp,
                                                    end = 6.dp,
                                                    top = 6.dp,
                                                    bottom = 6.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = fileNodeTint(node),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = node.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = JetBrainsMonoFamily,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onDeleteNode(node) },
                                                modifier = Modifier.size(34.dp)
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
                    }
                }

                IdeSidebarSection.SEARCH -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search code...", fontSize = 13.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sidebar_search_input")
                        )
                        Button(
                            onClick = onRunSearch,
                            enabled = searchQuery.isNotBlank() && activeProject != null,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sidebar_search_button")
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Search Project")
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val lines = searchResult.lines().filter { it.isNotBlank() }
                            items(lines) { line ->
                                val candidatePath = line.substringBefore(':').trim()
                                val canOpen = candidatePath.isNotEmpty() &&
                                    !candidatePath.startsWith("Found ") &&
                                    !candidatePath.startsWith("No matches")
                                Surface(
                                    onClick = { if (canOpen) onSelectFile(candidatePath) },
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                IdeSidebarSection.SOURCE_CONTROL -> {
                    val ghCfg = state.activeProjectGitHubConfig
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (ghCfg != null && ghCfg.repo.isNotBlank()) {
                                "${ghCfg.owner}/${ghCfg.repo} (${ghCfg.branch.ifBlank { "main" }})"
                            } else {
                                "Not linked to GitHub"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = onCommitGitHub,
                            enabled = activeProject != null,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Commit & Push")
                        }
                        if (ghCfg != null && ghCfg.repo.isNotBlank()) {
                            OutlinedButton(
                                onClick = onSyncNow,
                                enabled = state.activeProjectSyncStatus !is GitHubSyncStatus.Syncing,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (val sync = state.activeProjectSyncStatus) {
                                        is GitHubSyncStatus.Syncing -> "Syncing..."
                                        is GitHubSyncStatus.Synced -> "Synced ${sync.commitSha.take(6)}"
                                        is GitHubSyncStatus.Error -> "Retry Sync"
                                        GitHubSyncStatus.Idle -> "Sync Now"
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = onPullGitHub,
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pull ${ghCfg.branch.ifBlank { "main" }}")
                            }
                            OutlinedButton(
                                onClick = { onToggleDirectGitHub(!ghCfg.directGitHubMode) },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (ghCfg.directGitHubMode) "Direct Live: ON" else "Enable Direct Live",
                                    maxLines = 1
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenGitHubAccount,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("GitHub Account & CI")
                        }
                    }
                }
            }
        }
    }
}

private fun fileNodeTint(node: FileNode): Color {
    if (node.isDirectory) {
        return if (node.name.equals("public", true) || node.name.equals("src", true)) IdeFolderCyan
        else IdeFolderAmber
    }
    val ext = node.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js", "ts", "jsx", "tsx", "kt", "kts" -> IdeCodeGold
        "html", "xml", "svg" -> IdeHtmlCoral
        "css", "scss" -> IdeStyleBlue
        "json", "yml", "yaml" -> IdeFolderAmber
        else -> Color(0xFF94A3B8)
    }
}

@Composable
internal fun WorkspaceFileViewerPane(
    openTabs: List<String>,
    selectedFilePath: String,
    content: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onCloseViewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(selectedFilePath) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // Read-Only File Viewer Tab Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabsToRender = if (openTabs.isEmpty()) listOf(selectedFilePath) else openTabs
                    tabsToRender.forEach { tabPath ->
                        val isActive = tabPath == selectedFilePath
                        val fileName = tabPath.substringAfterLast('/')
                        Surface(
                            onClick = { onSelectTab(tabPath) },
                            color = if (isActive) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.fillMaxHeight()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(start = 10.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    IconButton(
                                        onClick = { onCloseTab(tabPath) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Close $fileName",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(content))
                        copied = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("file_viewer_copy_button")
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy file contents",
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }

                TextButton(
                    onClick = onCloseViewer,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.testTag("file_viewer_close_button")
                ) {
                    Text("Hide", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Read-Only Line-Numbered Syntax-Highlighted File View (No Code Editor)
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val ext = selectedFilePath.substringAfterLast('.', "")
        val highlighted = remember(content, ext, isDark) {
            SyntaxHighlighter.highlight(content, ext, isDark)
        }
        val lineCount = remember(content) {
            maxOf(1, content.count { it == '\n' } + 1)
        }
        val lineNumbersText = remember(lineCount) {
            (1..lineCount).joinToString("\n")
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .cyberScanlineGrid(
                    accentColor = MaterialTheme.colorScheme.secondary,
                    scanlineAlpha = 0.025f,
                    gridAlpha = 0.02f
                )
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = lineNumbersText,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .width(42.dp)
                    .padding(vertical = 10.dp, horizontal = 8.dp)
            )
            VerticalDivider(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp, horizontal = 12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = if (content.isEmpty()) AnnotatedString("// Empty file") else highlighted,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        softWrap = false,
                        color = if (content.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                    )
                }
            }
        }
    }
}

@Composable
internal fun IbAiWorkspaceConsole(
    chatState: ChatUiState,
    fullHeightMode: Boolean,
    expanded: Boolean,
    promptDraft: String,
    hasActiveProject: Boolean,
    onPromptDraftChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onNewSessionClick: () -> Unit,
    onExpandFullChat: () -> Unit,
    onSendPrompt: () -> Unit,
    onStopGeneration: () -> Unit,
    onFixWorkflowAlert: () -> Unit,
    onPickFilesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedMessages = remember(chatState.messages, chatState.streamingMessage) {
        val base = chatState.messages.filter { it.role != ChatRole.SYSTEM && it.role != ChatRole.TOOL }
        if (chatState.streamingMessage != null) base + chatState.streamingMessage else base
    }
    val listState = rememberLazyListState()
    val showBody = fullHeightMode || expanded

    LaunchedEffect(displayedMessages.size, chatState.streamingMessage?.content?.length, showBody) {
        if (showBody && displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Cyber Header ("IB_AI // WORKSPACE")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = !fullHeightMode, onClick = onToggleExpanded)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "IB_AI // CONSOLE",
                        fontFamily = SilkscreenFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1
                    )
                    val mode = chatState.currentSessionPrompt?.mode ?: chatState.currentSession?.mode
                    Text(
                        text = when (mode) {
                            SessionMode.NORMAL -> "Normal Q&A"
                            SessionMode.SPECIAL -> chatState.currentSessionPrompt?.presetTitle ?: "Special"
                            null -> "Web Live"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chatState.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CyberWindowGlyphs(accentColor = MaterialTheme.colorScheme.tertiary)
                    IconButton(
                        onClick = onNewSessionClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.AddComment, contentDescription = "New session", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = onExpandFullChat,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInFull, contentDescription = "Open full Chat", modifier = Modifier.size(15.dp))
                    }
                    if (!fullHeightMode) {
                        IconButton(
                            onClick = onToggleExpanded,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("drawer_toggle_button")
                        ) {
                            Icon(
                                if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                contentDescription = if (expanded) "Collapse chat" else "Expand chat",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (showBody) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                chatState.pendingWorkflowAlert?.let { alert ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CI Fail: ${alert.summary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onFixWorkflowAlert,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Fix with AI", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                chatState.errorBanner?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                val feedModifier = if (fullHeightMode) {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .cyberScanlineGrid(
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            scanlineAlpha = 0.03f,
                            gridAlpha = 0.02f
                        )
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 180.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .cyberScanlineGrid(
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            scanlineAlpha = 0.03f,
                            gridAlpha = 0.02f
                        )
                }

                Box(modifier = feedModifier) {
                    if (displayedMessages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (hasActiveProject) {
                                    "[IB_AI]: Ready — build code, run terminal commands/installs, search live web, or tap Files above."
                                } else {
                                    "[IB_AI]: Ready — ask questions, search the live web, run shell tools, or tap Files to open a project."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (fullHeightMode) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(onClick = onPickFilesClick, shape = RoundedCornerShape(4.dp)) {
                                        Icon(Icons.Rounded.FolderCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Show Files")
                                    }
                                    AssistChip(
                                        onClick = { onPromptDraftChanged("Inspect the workspace and summarize the architecture") },
                                        shape = RoundedCornerShape(4.dp),
                                        label = { Text("Inspect workspace") }
                                    )
                                    AssistChip(
                                        onClick = { onPromptDraftChanged("Search the web for the latest docs on ") },
                                        shape = RoundedCornerShape(4.dp),
                                        label = { Text("Web search") }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedMessages, key = { it.id }) { msg ->
                                val isUser = msg.role == ChatRole.USER
                                val prefix = if (isUser) "[USER]: " else "[IB_AI]: "
                                val prefixColor = if (isUser) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                                Surface(
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        prefixColor.copy(alpha = if (isUser) 0.55f else 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = prefix,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = prefixColor
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = msg.content.ifBlank {
                                                if (msg.isStreaming) "Working..." else ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (msg.toolCalls.isNotEmpty()) {
                                            val toolSummary = msg.toolCalls.joinToString(", ") { it.name }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "tools: $toolSummary",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = JetBrainsMonoFamily,
                                                color = MaterialTheme.colorScheme.secondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Composer Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp, max = 120.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (promptDraft.isEmpty()) {
                                Text(
                                    text = "Ask IB_AI...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = promptDraft,
                                onValueChange = onPromptDraftChanged,
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("drawer_chat_input")
                            )
                        }
                    }

                    if (chatState.isGenerating) {
                        IconButton(
                            onClick = onStopGeneration,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("drawer_stop_button")
                        ) {
                            Icon(
                                Icons.Rounded.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onSendPrompt,
                            enabled = promptDraft.isNotBlank(),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("drawer_send_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send to IB_AI",
                                tint = if (promptDraft.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
