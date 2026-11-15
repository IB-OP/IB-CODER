package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.ui.navigation.ChatRoute
import com.example.ui.navigation.ProvidersRoute
import com.example.ui.navigation.SessionsRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.navigation.WorkspaceRoute
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.providers.ProvidersScreen
import com.example.ui.screens.providers.ProvidersViewModel
import com.example.ui.screens.sessions.SessionsScreen
import com.example.ui.screens.sessions.SessionsViewModel
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.workspace.WorkspaceScreen
import com.example.ui.screens.workspace.WorkspaceViewModel

private data class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun AiCoderApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val tabs = listOf(
        TopLevelTab(ChatRoute, "Chat", Icons.Rounded.SmartToy, Icons.Outlined.SmartToy, "nav_chat"),
        TopLevelTab(WorkspaceRoute, "Workspace", Icons.Rounded.Folder, Icons.Outlined.Folder, "nav_workspace"),
        TopLevelTab(SessionsRoute, "Sessions", Icons.Rounded.Forum, Icons.Outlined.Forum, "nav_sessions"),
        TopLevelTab(SettingsRoute, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    val isProvidersRoute = currentDestination?.hasRoute<ProvidersRoute>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val chatViewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(container))
    val workspaceViewModel: WorkspaceViewModel = viewModel(factory = WorkspaceViewModel.Factory(container))
    val sessionsViewModel: SessionsViewModel = viewModel(factory = SessionsViewModel.Factory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
    val providersViewModel: ProvidersViewModel = viewModel(factory = ProvidersViewModel.Factory(container))

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useRail && !isProvidersRoute) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = when (tab.route) {
                            ChatRoute -> currentDestination?.hasRoute<ChatRoute>() == true
                            WorkspaceRoute -> currentDestination?.hasRoute<WorkspaceRoute>() == true
                            SessionsRoute -> currentDestination?.hasRoute<SessionsRoute>() == true
                            SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useRail && !isProvidersRoute) {
                NavigationRail {
                    tabs.forEach { tab ->
                        val selected = when (tab.route) {
                            ChatRoute -> currentDestination?.hasRoute<ChatRoute>() == true
                            WorkspaceRoute -> currentDestination?.hasRoute<WorkspaceRoute>() == true
                            SessionsRoute -> currentDestination?.hasRoute<SessionsRoute>() == true
                            SettingsRoute -> currentDestination?.hasRoute<SettingsRoute>() == true
                            else -> false
                        }
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.tag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = ChatRoute
                ) {
                    composable<ChatRoute> {
                        ChatScreen(
                            viewModel = chatViewModel,
                            onOpenProviders = { navController.navigate(ProvidersRoute) },
                            onOpenWorkspace = { navigateTopLevel(WorkspaceRoute) }
                        )
                    }
                    composable<WorkspaceRoute> {
                        WorkspaceScreen(
                            viewModel = workspaceViewModel,
                            onAskAgent = { navigateTopLevel(ChatRoute) }
                        )
                    }
                    composable<SessionsRoute> {
                        SessionsScreen(
                            viewModel = sessionsViewModel,
                            onOpenChat = { navigateTopLevel(ChatRoute) }
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onOpenProviders = { navController.navigate(ProvidersRoute) }
                        )
                    }
                    composable<ProvidersRoute> {
                        ProvidersScreen(
                            viewModel = providersViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
    }
}
