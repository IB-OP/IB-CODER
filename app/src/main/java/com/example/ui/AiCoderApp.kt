package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.R
import com.example.data.remote.CloudVaultSyncStatus
import com.example.domain.model.PrivilegeBackend
import com.example.ui.components.CyberWindowGlyphs
import com.example.ui.components.cyberScanlineGrid
import com.example.ui.navigation.ChatRoute
import com.example.ui.navigation.DeviceControlRoute
import com.example.ui.navigation.GitHubRoute
import com.example.ui.navigation.McpServersRoute
import com.example.ui.navigation.ProjectsRoute
import com.example.ui.navigation.ProvidersRoute
import com.example.ui.navigation.SessionsRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.TerminalRoute
import com.example.ui.navigation.WorkspaceRoute
import com.example.ui.screens.auth.GoogleSignInOutcome
import com.example.ui.screens.auth.SignInGateScreen
import com.example.ui.screens.auth.performInteractiveGoogleSignIn
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.device.DeviceControlScreen
import com.example.ui.screens.device.DeviceControlViewModel
import com.example.ui.screens.github.GitHubHubTab
import com.example.ui.screens.github.GitHubScreen
import com.example.ui.screens.github.GitHubViewModel
import com.example.ui.screens.mcp.McpServersScreen
import com.example.ui.screens.mcp.McpServersViewModel
import com.example.ui.screens.projects.ProjectsScreen
import com.example.ui.screens.providers.ProvidersScreen
import com.example.ui.screens.providers.ProvidersViewModel
import com.example.ui.screens.sessions.SessionsScreen
import com.example.ui.screens.sessions.SessionsViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.terminal.TerminalScreen
import com.example.ui.screens.terminal.TerminalViewModel
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.ui.screens.workspace.WorkspaceViewModel
import com.example.ui.theme.BrandWordmarkStyle
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SilkscreenFamily
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

private data class SidebarNavOption(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badge: String? = null,
    val testTag: String
)

@Composable
fun AiCoderApp(container: AppContainer) {
    val currentUser by container.cloudAccountVaultRepository.authState.collectAsStateWithLifecycle()
    val guestModeState by container.settingsDataStore.guestModeFlow.collectAsStateWithLifecycle(initialValue = null)

    if (currentUser == null) {
        when (guestModeState) {
            null -> {
                // Wait a frame for DataStore Guest preference to avoid flashing SignInGateScreen
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
                return
            }
            false -> {
                SignInGateScreen(container = container)
                return
            }
            true -> Unit
        }
    }

    val signedInUser = currentUser
    val isGuestMode = signedInUser == null
    key(signedInUser?.uid ?: "guest") {
        LaunchedEffect(signedInUser?.uid) {
            if (signedInUser != null) {
                container.cloudAccountVaultRepository.startSyncForUser(signedInUser)
            }
        }
        AuthenticatedAiCoderShell(
            container = container,
            user = signedInUser,
            isGuestMode = isGuestMode
        )
    }
}

@Composable
private fun AuthenticatedAiCoderShell(
    container: AppContainer,
    user: FirebaseUser?,
    isGuestMode: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container))
    val workspaceViewModel: WorkspaceViewModel = viewModel(factory = WorkspaceViewModel.Factory(container))
    val terminalViewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory(container))
    val gitHubViewModel: GitHubViewModel = viewModel(factory = GitHubViewModel.Factory(container))
    val sessionsViewModel: SessionsViewModel = viewModel(factory = SessionsViewModel.Factory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val providersViewModel: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory(container))
    val mcpServersViewModel: McpServersViewModel = viewModel(factory = McpServersViewModel.Factory(container))
    val deviceControlViewModel: DeviceControlViewModel = viewModel(factory = DeviceControlViewModel.Factory(container))

    val workspaceUiState by workspaceViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val terminalUiState by terminalViewModel.uiState.collectAsStateWithLifecycle()
    val deviceUiState by deviceControlViewModel.uiState.collectAsStateWithLifecycle()
    val cloudSyncStatus by container.cloudAccountVaultRepository.syncStatus.collectAsStateWithLifecycle()

    var drawerSignInBusy by remember { mutableStateOf(false) }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val workspaceItems = listOf(
        SidebarNavOption(
            route = WorkspaceRoute,
            label = "Code Workbench",
            selectedIcon = Icons.Rounded.Code,
            unselectedIcon = Icons.Outlined.Code,
            testTag = "drawer_nav_workspace"
        ),
        SidebarNavOption(
            route = TerminalRoute,
            label = "Terminal // sh",
            selectedIcon = Icons.Rounded.Terminal,
            unselectedIcon = Icons.Outlined.Terminal,
            badge = if (terminalUiState.session.isRunning) "RUN" else "SH",
            testTag = "drawer_nav_terminal"
        ),
        SidebarNavOption(
            route = ProjectsRoute,
            label = "Projects & Files",
            selectedIcon = Icons.Rounded.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            badge = workspaceUiState.projects.size.takeIf { it > 0 }?.toString(),
            testTag = "drawer_nav_projects"
        ),
        SidebarNavOption(
            route = ChatRoute,
            label = "AI Chat",
            selectedIcon = Icons.Rounded.Forum,
            unselectedIcon = Icons.Outlined.Forum,
            testTag = "drawer_nav_chat"
        ),
        SidebarNavOption(
            route = SessionsRoute,
            label = "Chat Sessions",
            selectedIcon = Icons.Rounded.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "drawer_nav_sessions"
        ),
        SidebarNavOption(
            route = DeviceControlRoute,
            label = "Phone Control",
            selectedIcon = Icons.Rounded.PhoneAndroid,
            unselectedIcon = Icons.Outlined.PhoneAndroid,
            badge = when (deviceUiState.probe.activeBackend) {
                PrivilegeBackend.ROOT -> "ROOT"
                PrivilegeBackend.SHIZUKU -> "SHIZ"
                else -> "OFF"
            },
            testTag = "drawer_nav_device_control"
        )
    )

    val integrationItems = listOf(
        SidebarNavOption(
            route = ProvidersRoute,
            label = "AI Providers & APIs",
            selectedIcon = Icons.Rounded.Key,
            unselectedIcon = Icons.Outlined.Key,
            badge = settingsUiState.providers.size.toString(),
            testTag = "drawer_nav_providers"
        ),
        SidebarNavOption(
            route = GitHubRoute,
            label = "GitHub Hub & CI",
            selectedIcon = Icons.Rounded.CloudSync,
            unselectedIcon = Icons.Outlined.CloudSync,
            badge = if (settingsUiState.gitHubToken.isNotBlank()) "ON" else null,
            testTag = "drawer_nav_github"
        ),
        SidebarNavOption(
            route = McpServersRoute,
            label = "MCP Servers",
            selectedIcon = Icons.Rounded.Hub,
            unselectedIcon = Icons.Outlined.Hub,
            badge = settingsUiState.mcpEnabledCount.takeIf { it > 0 }?.toString(),
            testTag = "drawer_nav_mcp"
        ),
        SidebarNavOption(
            route = SettingsRoute,
            label = "Settings & Sync",
            selectedIcon = Icons.Rounded.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "drawer_nav_settings"
        )
    )

    val mainContent: @Composable (onOpenSidebar: (() -> Unit)?) -> Unit = { onOpenSidebar ->
        NavHost(
            navController = navController,
            startDestination = WorkspaceRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            composable<WorkspaceRoute> {
                WorkspaceScreen(
                    viewModel = workspaceViewModel,
                    chatViewModel = chatViewModel,
                    onAskAgent = { navigateTopLevel(ChatRoute) },
                    onOpenTerminal = { navigateTopLevel(TerminalRoute) },
                    onOpenProviders = { navigateTopLevel(ProvidersRoute) },
                    onOpenMcpServers = { navigateTopLevel(McpServersRoute) },
                    onOpenSettings = { navigateTopLevel(SettingsRoute) },
                    onOpenGitHubAccount = {
                        gitHubViewModel.selectTab(GitHubHubTab.ACCOUNT)
                        navigateTopLevel(GitHubRoute)
                    },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<TerminalRoute> {
                TerminalScreen(
                    viewModel = terminalViewModel,
                    onWorkspaceMutated = { workspaceViewModel.refreshTree() },
                    onOpenWorkspace = { navigateTopLevel(WorkspaceRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<ProjectsRoute> {
                ProjectsScreen(
                    workspaceViewModel = workspaceViewModel,
                    sessionsViewModel = sessionsViewModel,
                    gitHubViewModel = gitHubViewModel,
                    onOpenCode = { navigateTopLevel(WorkspaceRoute) },
                    onOpenChat = { navigateTopLevel(ChatRoute) },
                    onLaunchAiFix = { projectId, prompt ->
                        chatViewModel.promptAiToFixBug(projectId, prompt)
                        navigateTopLevel(ChatRoute)
                    },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<ChatRoute> {
                ChatScreen(
                    viewModel = chatViewModel,
                    onOpenProviders = { navController.navigate(ProvidersRoute) },
                    onOpenWorkspace = { navigateTopLevel(WorkspaceRoute) },
                    onOpenMcpServers = { navController.navigate(McpServersRoute) },
                    onOpenGitHub = { navigateTopLevel(GitHubRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<SessionsRoute> {
                SessionsScreen(
                    viewModel = sessionsViewModel,
                    activeProjectName = workspaceUiState.activeProject?.name,
                    onOpenChat = { navigateTopLevel(ChatRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<GitHubRoute> {
                GitHubScreen(
                    viewModel = gitHubViewModel,
                    onLaunchAiFix = { projectId, prompt ->
                        chatViewModel.promptAiToFixBug(projectId, prompt)
                        navigateTopLevel(ChatRoute)
                    },
                    onOpenWorkspace = { navigateTopLevel(WorkspaceRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<ProvidersRoute> {
                ProvidersScreen(
                    viewModel = providersViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<McpServersRoute> {
                McpServersScreen(
                    viewModel = mcpServersViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onOpenProviders = { navController.navigate(ProvidersRoute) },
                    onOpenMcpServers = { navController.navigate(McpServersRoute) },
                    onOpenGitHub = { navigateTopLevel(GitHubRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
            composable<DeviceControlRoute> {
                DeviceControlScreen(
                    viewModel = deviceControlViewModel,
                    onOpenChat = { navigateTopLevel(ChatRoute) },
                    onOpenSidebar = onOpenSidebar
                )
            }
        }
    }

    val handleSignInClick: () -> Unit = {
        scope.launch {
            drawerSignInBusy = true
            when (
                performInteractiveGoogleSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    cloudAccountVaultRepository = container.cloudAccountVaultRepository
                )
            ) {
                GoogleSignInOutcome.Success -> drawerState.close()
                GoogleSignInOutcome.Cancelled,
                is GoogleSignInOutcome.Error -> Unit
            }
            drawerSignInBusy = false
        }
    }

    val handleExitOrSignOut: () -> Unit = {
        scope.launch {
            drawerState.close()
            if (isGuestMode) {
                container.settingsDataStore.setGuestMode(false)
            } else {
                container.cloudAccountVaultRepository.signOutAccount(credentialManager)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val usePermanentSidebar = maxWidth >= 840.dp

        if (usePermanentSidebar) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.width(276.dp)
                    ) {
                        IbCoderSidebarContent(
                            user = user,
                            isGuestMode = isGuestMode,
                            isSigningIn = drawerSignInBusy,
                            activeProjectName = workspaceUiState.activeProject?.name,
                            syncStatus = cloudSyncStatus,
                            currentDestination = currentDestination,
                            workspaceItems = workspaceItems,
                            integrationItems = integrationItems,
                            onSelectRoute = { route -> navigateTopLevel(route) },
                            onSyncNow = { scope.launch { container.cloudAccountVaultRepository.syncNow() } },
                            onSignInWithGoogle = handleSignInClick,
                            onSignOut = handleExitOrSignOut
                        )
                    }
                }
            ) {
                mainContent(null)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.width(292.dp)
                    ) {
                        IbCoderSidebarContent(
                            user = user,
                            isGuestMode = isGuestMode,
                            isSigningIn = drawerSignInBusy,
                            activeProjectName = workspaceUiState.activeProject?.name,
                            syncStatus = cloudSyncStatus,
                            currentDestination = currentDestination,
                            workspaceItems = workspaceItems,
                            integrationItems = integrationItems,
                            onSelectRoute = { route ->
                                scope.launch {
                                    drawerState.close()
                                    navigateTopLevel(route)
                                }
                            },
                            onSyncNow = { scope.launch { container.cloudAccountVaultRepository.syncNow() } },
                            onSignInWithGoogle = handleSignInClick,
                            onSignOut = handleExitOrSignOut
                        )
                    }
                }
            ) {
                mainContent {
                    scope.launch { drawerState.open() }
                }
            }
        }
    }
}

@Composable
private fun IbCoderSidebarContent(
    user: FirebaseUser?,
    isGuestMode: Boolean,
    isSigningIn: Boolean,
    activeProjectName: String?,
    syncStatus: CloudVaultSyncStatus,
    currentDestination: NavDestination?,
    workspaceItems: List<SidebarNavOption>,
    integrationItems: List<SidebarNavOption>,
    onSelectRoute: (Any) -> Unit,
    onSyncNow: () -> Unit,
    onSignInWithGoogle: () -> Unit,
    onSignOut: () -> Unit
) {
    val displayName = if (isGuestMode || user == null) {
        "Guest Developer"
    } else {
        user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Developer"
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .cyberScanlineGrid(accentColor = MaterialTheme.colorScheme.primary, scanlineAlpha = 0.03f, gridAlpha = 0.025f)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Brand & Account Header Card (Cloud Synced or Guest Local)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(
                1.25.dp,
                if (isGuestMode) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ib_coder_logo),
                        contentDescription = "IB CODER",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "IB CODER",
                                style = BrandWordmarkStyle.copy(fontSize = 15.sp),
                                maxLines = 1
                            )
                            CyberWindowGlyphs(
                                accentColor = if (isGuestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isGuestMode) "GUEST // LOCAL STORAGE" else (user?.email ?: "Signed in"),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = if (isGuestMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!isGuestMode) {
                        IconButton(
                            onClick = onSyncNow,
                            enabled = syncStatus !is CloudVaultSyncStatus.Syncing,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("drawer_sync_now_button")
                        ) {
                            if (syncStatus is CloudVaultSyncStatus.Syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Rounded.Sync,
                                    contentDescription = "Sync account vault",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (isGuestMode) {
                    Text(
                        text = "Keys & repos stay on-device. Sign in anytime to sync across devices.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val (statusIcon, statusText, statusColor) = when (syncStatus) {
                        is CloudVaultSyncStatus.Synced -> Triple(
                            Icons.Rounded.CloudDone,
                            "Vault synced • ${syncStatus.syncedKeysCount} keys",
                            MaterialTheme.colorScheme.primary
                        )
                        is CloudVaultSyncStatus.Syncing -> Triple(
                            Icons.Rounded.Sync,
                            syncStatus.detail,
                            MaterialTheme.colorScheme.secondary
                        )
                        is CloudVaultSyncStatus.Error -> Triple(
                            Icons.Rounded.CloudOff,
                            syncStatus.message,
                            MaterialTheme.colorScheme.error
                        )
                        CloudVaultSyncStatus.SignedOut -> Triple(
                            Icons.Rounded.CloudOff,
                            "Offline",
                            MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!activeProjectName.isNullOrBlank()) {
                    Text(
                        text = "Active: $activeProjectName",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Scrollable Navigation Groups
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SidebarSectionHeader("WORKBENCH")
            workspaceItems.forEach { item ->
                val selected = isRouteSelected(currentDestination, item.route)
                SidebarDrawerItem(
                    item = item,
                    selected = selected,
                    onClick = { onSelectRoute(item.route) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SidebarSectionHeader("INTEGRATIONS & CLOUD")
            integrationItems.forEach { item ->
                val selected = isRouteSelected(currentDestination, item.route)
                SidebarDrawerItem(
                    item = item,
                    selected = selected,
                    onClick = { onSelectRoute(item.route) }
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        if (isGuestMode) {
            Button(
                onClick = onSignInWithGoogle,
                enabled = !isSigningIn,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_google_signin_button")
            ) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isSigningIn) "Signing in..." else "Sign in with Google", fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
        }

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (isGuestMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            ),
            border = BorderStroke(
                1.dp,
                if (isGuestMode) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isGuestMode) "Exit Guest Mode" else "Sign Out", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SidebarSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = SilkscreenFamily,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun SidebarDrawerItem(
    item: SidebarNavOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = item.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                contentDescription = item.label
            )
        },
        badge = item.badge?.let { badgeText ->
            {
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        shape = RoundedCornerShape(4.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .padding(vertical = 1.dp)
            .testTag(item.testTag)
    )
}

private fun isRouteSelected(currentDestination: NavDestination?, route: Any): Boolean {
    return when (route) {
        WorkspaceRoute -> currentDestination?.hasRoute<WorkspaceRoute>() == true
        TerminalRoute -> currentDestination?.hasRoute<TerminalRoute>() == true
        ProjectsRoute -> currentDestination?.hasRoute<ProjectsRoute>() == true
        ChatRoute -> currentDestination?.hasRoute<ChatRoute>() == true
        SessionsRoute -> currentDestination?.hasRoute<SessionsRoute>() == true
        ProvidersRoute -> currentDestination?.hasRoute<ProvidersRoute>() == true
        GitHubRoute -> currentDestination?.hasRoute<GitHubRoute>() == true
        McpServersRoute -> currentDestination?.hasRoute<McpServersRoute>() == true
        SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
        DeviceControlRoute -> currentDestination?.hasRoute<DeviceControlRoute>() == true
        else -> false
    }
}
