package com.example.ui.screens.workspace

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.ui.components.SyntaxHighlighter
import com.example.ui.screens.chat.ChatUiState
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.IdeCodeGold
import com.example.ui.theme.IdeFolderAmber
import com.example.ui.theme.IdeFolderCyan
import com.example.ui.theme.IdeHtmlCoral
import com.example.ui.theme.IdeStyleBlue
import com.example.ui.theme.JetBrainsMonoFamily

internal enum class IdeSidebarSection(val label: String) {
    EXPLORER("PROJECTS"),
    SEARCH("SEARCH"),
    SOURCE_CONTROL("SOURCE CONTROL")
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
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onOpenSidebar != null) {
                    IconButton(
                        onClick = onOpenSidebar,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("ide_menu_button")
                    ) {
                        Icon(
                            Icons.Rounded.Menu,
                            contentDescription = "Open navigation sidebar",
                            modifier = Modifier.size(20.dp)
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
                Text(
                    text = "IB CODER",
                    style = BrandWordmarkStyle.copy(fontSize = 16.sp),
                    maxLines = 1
                )

                // Active Project Selector Pill
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Surface(
                        onClick = { projectMenuOpen = true },
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.testTag("ide_project_selector")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
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
                                modifier = Modifier.width(105.dp)
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
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

                Spacer(Modifier.weight(1f))

                // Active AI Model Pill
                Surface(
                    onClick = onSwitchModel,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("ide_model_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            text = modelId.ifBlank { providerId }.take(18),
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

@Composable
internal fun IdeActivityRail(
    activeSection: IdeSidebarSection,
    sidebarVisible: Boolean,
    onSelectSection: (IdeSidebarSection) -> Unit,
    onOpenProviders: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(46.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                RailIconButton(
                    icon = Icons.Rounded.FolderCopy,
                    contentDescription = "Project Explorer",
                    selected = sidebarVisible && activeSection == IdeSidebarSection.EXPLORER,
                    onClick = { onSelectSection(IdeSidebarSection.EXPLORER) },
                    testTag = "rail_explorer_button"
                )
                RailIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search Workspace",
                    selected = sidebarVisible && activeSection == IdeSidebarSection.SEARCH,
                    onClick = { onSelectSection(IdeSidebarSection.SEARCH) },
                    testTag = "rail_search_button"
                )
                RailIconButton(
                    icon = Icons.Rounded.CloudSync,
                    contentDescription = "Source Control & GitHub",
                    selected = sidebarVisible && activeSection == IdeSidebarSection.SOURCE_CONTROL,
                    onClick = { onSelectSection(IdeSidebarSection.SOURCE_CONTROL) },
                    testTag = "rail_source_control_button"
                )
                RailIconButton(
                    icon = Icons.Rounded.Key,
                    contentDescription = "Providers & Custom APIs",
                    selected = false,
                    onClick = onOpenProviders,
                    testTag = "rail_providers_button"
                )
                RailIconButton(
                    icon = Icons.Rounded.Hub,
                    contentDescription = "MCP Servers",
                    selected = false,
                    onClick = onOpenMcp,
                    testTag = "rail_mcp_button"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                RailIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    selected = false,
                    onClick = onOpenSettings,
                    testTag = "rail_settings_button"
                )
            }
        }
    }
}

@Composable
private fun RailIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(2.5.dp)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
internal fun IdeSidebarPane(
    section: IdeSidebarSection,
    activeProject: WorkspaceProject?,
    nodes: List<FileNode>,
    selectedFilePath: String?,
    searchQuery: String,
    searchResult: String,
    state: WorkspaceUiState,
    onCollapse: () -> Unit,
    onNewProject: () -> Unit,
    onPickDeviceFolder: () -> Unit,
    onSelectFile: (String) -> Unit,
    onDeleteNode: (FileNode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRunSearch: () -> Unit,
    onNewFile: () -> Unit,
    onCommitGitHub: () -> Unit,
    onSyncNow: () -> Unit,
    onPullGitHub: () -> Unit,
    onToggleDirectGitHub: (Boolean) -> Unit,
    onOpenGitHubAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Section Title Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNewProject,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("sidebar_new_project_button")
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "New Project",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("sidebar_collapse_button")
                    ) {
                        Icon(
                            Icons.Rounded.ChevronLeft,
                            contentDescription = "Collapse sidebar",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Main Section Content
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
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No project selected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onNewProject,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("New Project")
                                }
                                OutlinedButton(
                                    onClick = onPickDeviceFolder,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Files App")
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Root Project Accordion Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = activeProject.name.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
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
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                if (nodes.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Workspace is empty.\nTap New File or ask IB_AI below.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(nodes, key = { it.relativePath }) { node ->
                                            val isSelected = node.relativePath == selectedFilePath
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable { if (!node.isDirectory) onSelectFile(node.relativePath) }
                                                    .padding(
                                                        start = (8 + node.depth * 12).dp,
                                                        end = 4.dp,
                                                        top = 5.dp,
                                                        bottom = 5.dp
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (node.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                                                    contentDescription = null,
                                                    tint = fileNodeTint(node),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = node.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = JetBrainsMonoFamily,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { onDeleteNode(node) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.Delete,
                                                        contentDescription = "Delete ${node.name}",
                                                        modifier = Modifier.size(13.dp),
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
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Search code...", fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sidebar_search_input")
                            )
                            Button(
                                onClick = onRunSearch,
                                enabled = searchQuery.isNotBlank() && activeProject != null,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sidebar_search_button")
                            ) {
                                Text("Search Project")
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(6.dp)
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
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (ghCfg != null && ghCfg.repo.isNotBlank()) {
                                    "${ghCfg.owner}/${ghCfg.repo} (${ghCfg.branch.ifBlank { "main" }})"
                                } else {
                                    "Not linked to GitHub"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Button(
                                onClick = onCommitGitHub,
                                enabled = activeProject != null,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Commit & Push")
                            }
                            if (ghCfg != null && ghCfg.repo.isNotBlank()) {
                                OutlinedButton(
                                    onClick = onSyncNow,
                                    enabled = state.activeProjectSyncStatus !is GitHubSyncStatus.Syncing,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
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
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Pull ${ghCfg.branch.ifBlank { "main" }}")
                                }
                                OutlinedButton(
                                    onClick = { onToggleDirectGitHub(!ghCfg.directGitHubMode) },
                                    shape = RoundedCornerShape(4.dp),
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
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("GitHub Account & CI")
                            }
                        }
                    }
                }
            }

            // Sticky Sidebar Footer matching reference image ("PROJECTS" -> "New File" | "✓ Commit")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "PROJECTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.6.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onNewFile,
                        enabled = activeProject != null,
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .testTag("workspace_new_file_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.NoteAdd,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "New File",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        onClick = onCommitGitHub,
                        enabled = activeProject != null,
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .testTag("workspace_push_github_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Commit",
                                style = MaterialTheme.typography.labelSmall,
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
internal fun LineNumberedCodeEditorPane(
    openTabs: List<String>,
    selectedFilePath: String?,
    content: String,
    isEditing: Boolean,
    hasActiveProject: Boolean,
    sidebarVisible: Boolean,
    onShowSidebar: () -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onSave: (String, String) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onImportZip: () -> Unit,
    onExportZip: () -> Unit,
    onOpenFullChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(selectedFilePath, content) { mutableStateOf(content) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // Tab Bar Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!sidebarVisible) {
                    IconButton(
                        onClick = onShowSidebar,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("editor_open_sidebar_button")
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = "Show project sidebar",
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (openTabs.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Column(modifier = Modifier.fillMaxHeight()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "CODE EDITOR",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = JetBrainsMonoFamily
                                    )
                                }
                            }
                        }
                    } else {
                        openTabs.forEach { tabPath ->
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
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "Close $fileName",
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                // Edit / Save / More controls
                if (selectedFilePath != null) {
                    if (isEditing) {
                        TextButton(
                            onClick = {
                                draft = content
                                onToggleEdit(false)
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(
                            onClick = { onSave(selectedFilePath, draft) },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("code_viewer_save_button")
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = "Save file",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { onToggleEdit(true) },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("code_viewer_edit_button")
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Edit file",
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { moreMenuOpen = true },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("code_editor_more_button")
                    ) {
                        Icon(
                            Icons.Rounded.MoreHoriz,
                            contentDescription = "Editor actions",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuOpen,
                        onDismissRequest = { moreMenuOpen = false }
                    ) {
                        if (hasActiveProject) {
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
                        DropdownMenuItem(
                            text = { Text("Open Full Chat") },
                            leadingIcon = { Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                            onClick = {
                                moreMenuOpen = false
                                onOpenFullChat()
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Code Content Area
        if (selectedFilePath == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (hasActiveProject) {
                            "Select a file from PROJECTS or ask IB_AI below to generate code."
                        } else {
                            "Open a folder, GitHub repo, or local project — or chat directly with IB_AI below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!sidebarVisible) {
                        OutlinedButton(onClick = onShowSidebar, shape = RoundedCornerShape(4.dp)) {
                            Text("Open Projects Sidebar")
                        }
                    }
                }
            }
        } else if (isEditing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .testTag("code_editor_input"),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                shape = RoundedCornerShape(4.dp)
            )
        } else {
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
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Line Number Gutter
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
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp, horizontal = 12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = highlighted,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DockedIbAiChatDrawer(
    chatState: ChatUiState,
    expanded: Boolean,
    promptDraft: String,
    onPromptDraftChanged: (String) -> Unit,
    onToggleExpanded: () -> Unit,
    onNewSessionClick: () -> Unit,
    onExpandFullChat: () -> Unit,
    onSendPrompt: () -> Unit,
    onStopGeneration: () -> Unit,
    onFixWorkflowAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayedMessages = remember(chatState.messages, chatState.streamingMessage) {
        val base = chatState.messages.filter { it.role != ChatRole.SYSTEM && it.role != ChatRole.TOOL }
        if (chatState.streamingMessage != null) base + chatState.streamingMessage else base
    }
    val listState = rememberLazyListState()

    LaunchedEffect(displayedMessages.size, chatState.streamingMessage?.content?.length, expanded) {
        if (expanded && displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.lastIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            // Drawer Title Header ("IB_AI CHAT")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "IB_AI CHAT",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                    val mode = chatState.currentSessionPrompt?.mode ?: chatState.currentSession?.mode
                    Text(
                        text = when (mode) {
                            SessionMode.NORMAL -> "Normal Q&A"
                            SessionMode.SPECIAL -> chatState.currentSessionPrompt?.presetTitle ?: "Special"
                            null -> "Web Live"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (chatState.isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNewSessionClick,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("drawer_new_session_button")
                    ) {
                        Icon(
                            Icons.Rounded.AddComment,
                            contentDescription = "New session",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onExpandFullChat,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("drawer_open_full_chat_button")
                    ) {
                        Icon(
                            Icons.Rounded.OpenInFull,
                            contentDescription = "Open full Chat",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("drawer_toggle_button")
                    ) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                            contentDescription = if (expanded) "Minimize chat drawer" else "Expand chat drawer",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                chatState.pendingWorkflowAlert?.let { alert ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
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
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(13.dp))
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
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                // Compact Stream Feed matching [USER]: / [IB_AI]: look
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 82.dp, max = 135.dp)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (displayedMessages.isEmpty()) {
                        Text(
                            text = "[IB_AI]: Ready — ask questions, search the live internet, or build code in your workspace.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(displayedMessages, key = { it.id }) { msg ->
                                val isUser = msg.role == ChatRole.USER
                                val prefix = if (isUser) "[USER]: " else "[IB_AI]: "
                                val prefixColor = if (isUser) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                }
                                Column {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            text = prefix,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = prefixColor
                                        )
                                        Text(
                                            text = msg.content.ifBlank {
                                                if (msg.isStreaming) "Working..." else ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (msg.toolCalls.isNotEmpty()) {
                                        val toolSummary = msg.toolCalls.joinToString(", ") { it.name }
                                        Text(
                                            text = "  tools: $toolSummary",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Cyan-bordered "Ask IB_AI..." Composer Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
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
                                singleLine = true,
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
                                .size(44.dp)
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
                                .size(44.dp)
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

@Composable
internal fun IdeRightUtilityStrip(
    chatExpanded: Boolean,
    onToggleChat: () -> Unit,
    onNewSession: () -> Unit,
    onSwitchModel: () -> Unit,
    onNewFile: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(38.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onToggleChat, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.SmartToy,
                    contentDescription = "Toggle IB_AI Chat",
                    tint = if (chatExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onNewSession, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.AddComment,
                    contentDescription = "New session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onSwitchModel, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = "Switch model",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onNewFile, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.AutoMirrored.Rounded.NoteAdd,
                    contentDescription = "New file",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
