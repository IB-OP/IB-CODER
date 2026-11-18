package com.example.ui.screens.device

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.device.AndroidPrivilegeShellExecutor
import com.example.domain.model.PrivilegeBackend
import com.example.domain.model.ShizukuBinderState
import com.example.ui.components.CyberWindowPanel
import com.example.ui.components.cyberScanlineGrid
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SilkscreenFamily
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceControlScreen(
    viewModel: DeviceControlViewModel,
    onOpenChat: () -> Unit = {},
    onOpenSidebar: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(DeviceControlTab.CONTROLLER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "DEVICE // ROOT & SHIZUKU",
                                fontFamily = SilkscreenFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.probe.statusSummary,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = if (state.probe.isReady) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                            modifier = Modifier.testTag("device_menu_button")
                        ) {
                            Icon(Icons.Rounded.Menu, contentDescription = "Open navigation sidebar")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.testTag("device_open_chat_button")
                    ) {
                        Icon(Icons.Rounded.Forum, contentDescription = "Ask AI to control device")
                    }
                    IconButton(
                        onClick = { viewModel.refreshProbe(fetchTelemetry = true) },
                        enabled = !state.isBusy,
                        modifier = Modifier.testTag("device_refresh_probe_button")
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Root and Shizuku status")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cyberScanlineGrid(
                    accentColor = MaterialTheme.colorScheme.primary,
                    scanlineAlpha = 0.03f,
                    gridAlpha = 0.02f
                )
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.statusBanner?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = JetBrainsMonoFamily,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = viewModel::dismissBanner,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Dismiss status message",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Privilege Bridge Card (Root + Shizuku Status, Backend Preference, AI Safety Toggles)
                CyberWindowPanel(
                    title = "PRIVILEGE BRIDGE",
                    accentColor = if (state.probe.isReady) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    badgeText = state.probe.activeBackend.name,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Root & Shizuku live cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatusIndicatorBox(
                                label = "ROOT (su)",
                                value = if (state.probe.rootAvailable) {
                                    "READY uid=${state.probe.rootUid ?: 0}"
                                } else {
                                    "NOT DETECTED"
                                },
                                isActive = state.probe.rootAvailable,
                                modifier = Modifier.weight(1f)
                            )
                            StatusIndicatorBox(
                                label = "SHIZUKU",
                                value = when (state.probe.shizukuBinderState) {
                                    ShizukuBinderState.READY -> "READY uid=${state.probe.shizukuUid ?: 2000}"
                                    ShizukuBinderState.PERMISSION_REQUIRED -> "PERM NEEDED"
                                    ShizukuBinderState.STOPPED -> "STOPPED"
                                    ShizukuBinderState.NOT_INSTALLED -> "NOT INSTALLED"
                                },
                                isActive = state.probe.shizukuBinderState == ShizukuBinderState.READY,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Action row for Shizuku authorization / launch / probe
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.probe.shizukuRunning && !state.probe.shizukuPermissionGranted) {
                                Button(
                                    onClick = { viewModel.requestShizukuPermission() },
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("device_authorize_shizuku_button")
                                ) {
                                    Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Authorize Shizuku")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!openShizukuApp(context)) {
                                        viewModel.refreshProbe()
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("device_open_shizuku_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.probe.shizukuInstalled) "Open Shizuku" else "Install Shizuku")
                            }

                            OutlinedButton(
                                onClick = { viewModel.refreshProbe(fetchTelemetry = true) },
                                enabled = !state.isBusy,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("device_probe_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Verify Access")
                            }
                        }

                        // Backend selector chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MODE:",
                                fontFamily = SilkscreenFamily,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            listOf(
                                PrivilegeBackend.AUTO to "Auto",
                                PrivilegeBackend.ROOT to "Root (su)",
                                PrivilegeBackend.SHIZUKU to "Shizuku"
                            ).forEach { (backend, label) ->
                                FilterChip(
                                    selected = state.settings.preferredBackend == backend,
                                    onClick = { viewModel.setPreferredBackend(backend) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("device_backend_${backend.name.lowercase()}")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // AI Control & Confirmation Switches
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow AI Agent to Control Phone",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Exposes tap, swipe, UI dump, launch app & privileged shell to IB_AI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.aiPhoneControlEnabled,
                                onCheckedChange = viewModel::setAiPhoneControlEnabled,
                                modifier = Modifier.testTag("device_ai_control_switch")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Confirm Risky AI Shell Commands",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Ask before AI executes arbitrary Root/Shizuku shell commands",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.confirmSensitiveDeviceActions,
                                onCheckedChange = viewModel::setConfirmSensitiveActions,
                                modifier = Modifier.testTag("device_confirm_risky_switch")
                            )
                        }
                    }
                }

                // Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    DeviceControlTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("device_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        DeviceControlTab.CONTROLLER -> ControllerTab(state, viewModel)
                        DeviceControlTab.INSPECTOR -> UiInspectorTab(state, viewModel)
                        DeviceControlTab.APPS -> AppsTab(state, viewModel)
                        DeviceControlTab.SHELL -> ShellAndLogsTab(state, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicatorBox(
    label: String,
    value: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accent, RoundedCornerShape(1.dp))
                )
                Text(
                    text = label,
                    fontFamily = SilkscreenFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControllerTab(
    state: DeviceControlUiState,
    viewModel: DeviceControlViewModel
) {
    var tapXInput by rememberSaveable { mutableStateOf("540") }
    var tapYInput by rememberSaveable { mutableStateOf("1200") }
    var textInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Telemetry strip if available
        state.telemetry?.let { t ->
            CyberWindowPanel(
                title = "TELEMETRY",
                accentColor = MaterialTheme.colorScheme.primary,
                badgeText = t.screenResolution,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Foreground: ${t.foregroundApp}",
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            if (t.deviceModel.isNotBlank()) append("${t.deviceModel} • ")
                            append(t.androidRelease)
                            t.batteryLevel?.let { lvl ->
                                append(" • Battery $lvl%")
                                if (t.batteryCharging == true) append(" ⚡")
                            }
                        },
                        fontFamily = JetBrainsMonoFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Hardware & System Navigation Keys
        CyberWindowPanel(
            title = "HARDWARE & NAV KEYS",
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            FlowRow(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val keys = listOf(
                    Triple("BACK", "Back", Icons.AutoMirrored.Rounded.ArrowBack),
                    Triple("HOME", "Home", Icons.Rounded.Home),
                    Triple("RECENTS", "Recents", Icons.Rounded.Layers),
                    Triple("VOLUME_DOWN", "Vol -", Icons.AutoMirrored.Rounded.VolumeDown),
                    Triple("VOLUME_UP", "Vol +", Icons.AutoMirrored.Rounded.VolumeUp),
                    Triple("POWER", "Power", Icons.Rounded.PowerSettingsNew),
                    Triple("MEDIA_PLAY_PAUSE", "Media", Icons.Rounded.PlayArrow),
                    Triple("ENTER", "Enter", Icons.AutoMirrored.Rounded.KeyboardReturn)
                )
                keys.forEach { (keyCode, label, icon) ->
                    FilledTonalButton(
                        onClick = { viewModel.pressKey(keyCode) },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("device_key_${keyCode.lowercase()}")
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
            }
        }

        // Gestures & Quick System Actions
        CyberWindowPanel(
            title = "GESTURES & QUICK ACTIONS",
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.swipePreset("UP") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.SwipeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scroll Down")
                    }
                    OutlinedButton(
                        onClick = { viewModel.swipePreset("DOWN") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.SwipeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scroll Up")
                    }
                    OutlinedButton(
                        onClick = { viewModel.swipePreset("LEFT") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.SwipeLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Swipe Left")
                    }
                    OutlinedButton(
                        onClick = { viewModel.swipePreset("RIGHT") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.SwipeRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Swipe Right")
                    }
                    OutlinedButton(
                        onClick = { viewModel.swipePreset("NOTIFICATIONS") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Shade")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.captureScreenshot() },
                        enabled = !state.isBusy,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("device_screenshot_button")
                    ) {
                        Icon(Icons.Rounded.Screenshot, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Screenshot")
                    }
                    FilledTonalButton(
                        onClick = { viewModel.setQuickSetting("wifi", "on") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Wi-Fi ON")
                    }
                    OutlinedButton(
                        onClick = { viewModel.setQuickSetting("wifi", "off") },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Wi-Fi OFF")
                    }
                }
            }
        }

        // Coordinate Tap + Text Input
        CyberWindowPanel(
            title = "TOUCH & TEXT INJECTOR",
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tapXInput,
                        onValueChange = { tapXInput = it.filter(Char::isDigit) },
                        label = { Text("X") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_tap_x_input")
                    )
                    OutlinedTextField(
                        value = tapYInput,
                        onValueChange = { tapYInput = it.filter(Char::isDigit) },
                        label = { Text("Y") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_tap_y_input")
                    )
                    Button(
                        onClick = {
                            val x = tapXInput.toIntOrNull() ?: 0
                            val y = tapYInput.toIntOrNull() ?: 0
                            viewModel.tap(x, y)
                        },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("device_tap_execute_button")
                    ) {
                        Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tap")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        label = { Text("Type text on focused field") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("device_type_text_input")
                    )
                    Button(
                        onClick = {
                            val payload = textInput
                            textInput = ""
                            viewModel.inputText(payload)
                        },
                        enabled = textInput.isNotBlank(),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .testTag("device_type_text_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Type text")
                    }
                }
            }
        }

        // Screenshot Preview if captured
        state.lastScreenshotPath?.let { path ->
            val bitmap = remember(path) {
                runCatching {
                    val f = File(path)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
                }.getOrNull()
            }
            if (bitmap != null) {
                CyberWindowPanel(
                    title = "LAST SCREENSHOT",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Captured device screenshot",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .padding(8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun UiInspectorTab(
    state: DeviceControlUiState,
    viewModel: DeviceControlViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.dumpUiHierarchy() },
                enabled = !state.isBusy,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("device_dump_ui_button")
            ) {
                Icon(Icons.Rounded.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.isBusy) "Dumping UI..." else "Dump Screen UI")
            }
            Text(
                text = "${state.uiNodes.size} element(s)",
                fontFamily = JetBrainsMonoFamily,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.uiNodes.isEmpty()) {
            CyberWindowPanel(
                title = "UI HIERARCHY",
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tap 'Dump Screen UI' while Root or Shizuku is active to capture live clickable/editable elements and exact (x, y) coordinates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.uiNodes, key = { it.index }) { node ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "[${node.index}] ${node.displayLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "tap=(${node.centerX}, ${node.centerY}) ${node.bounds}" +
                                        if (node.clickable) " • clickable" else "",
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                if (node.resourceId.isNotBlank()) {
                                    Text(
                                        text = node.resourceId,
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { viewModel.tap(node.centerX, node.centerY) },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Tap")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsTab(
    state: DeviceControlUiState,
    viewModel: DeviceControlViewModel
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var includeSystem by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Filter package") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("device_apps_search_input")
            )
            Button(
                onClick = { viewModel.loadInstalledApps(includeSystem, searchQuery) },
                enabled = !state.isLoadingApps,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("device_load_apps_button")
            ) {
                if (state.isLoadingApps) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Load")
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !includeSystem,
                onClick = { includeSystem = false },
                label = { Text("User Apps") }
            )
            FilterChip(
                selected = includeSystem,
                onClick = { includeSystem = true },
                label = { Text("All Packages") }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.installedApps, key = { it }) { pkg ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = pkg,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { viewModel.launchApp(pkg) },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Open")
                        }
                        OutlinedButton(
                            onClick = { viewModel.forceStopApp(pkg) },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = "Force stop $pkg", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellAndLogsTab(
    state: DeviceControlUiState,
    viewModel: DeviceControlViewModel
) {
    var shellCommand by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = shellCommand,
                onValueChange = { shellCommand = it },
                label = { Text("Privileged shell (Root / Shizuku)") },
                placeholder = { Text("e.g. id | dumpsys battery | pm list packages -3") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                modifier = Modifier
                    .weight(1f)
                    .testTag("device_shell_input")
            )
            Button(
                onClick = {
                    val cmd = shellCommand
                    shellCommand = ""
                    viewModel.runPrivilegedShell(cmd)
                },
                enabled = shellCommand.isNotBlank() && !state.isBusy,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .testTag("device_shell_run_button")
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = "Execute privileged command")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ACTION LOG (${state.actionLogs.size})",
                fontFamily = SilkscreenFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.actionLogs.isNotEmpty()) {
                TextButton(onClick = viewModel::clearLogs) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(state.actionLogs, key = { it.id }) { entry ->
                val accent = if (entry.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (entry.success) Icons.Rounded.CheckCircle else Icons.Rounded.Close,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "[${entry.backend.name}] ${entry.action} (exit=${entry.exitCode})",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                        }
                        Text(
                            text = "$ ${entry.command}",
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (entry.output.isNotBlank()) {
                            Text(
                                text = entry.output,
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openShizukuApp(context: Context): Boolean {
    val intent = context.packageManager.getLaunchIntentForPackage(AndroidPrivilegeShellExecutor.SHIZUKU_PACKAGE_NAME)
        ?: return false
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
