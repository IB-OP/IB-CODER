package com.example.ui.screens.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.device.DeviceController
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.DeviceActionLogEntry
import com.example.domain.model.DeviceControlSettings
import com.example.domain.model.DeviceTelemetry
import com.example.domain.model.PrivilegeBackend
import com.example.domain.model.PrivilegeProbeResult
import com.example.domain.model.UiNodeElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

enum class DeviceControlTab(val label: String) {
    CONTROLLER("Controller"),
    INSPECTOR("UI Inspector"),
    APPS("Apps"),
    SHELL("Shell & Logs")
}

data class DeviceControlUiState(
    val probe: PrivilegeProbeResult = PrivilegeProbeResult(),
    val settings: DeviceControlSettings = DeviceControlSettings(),
    val telemetry: DeviceTelemetry? = null,
    val uiNodes: List<UiNodeElement> = emptyList(),
    val installedApps: List<String> = emptyList(),
    val actionLogs: List<DeviceActionLogEntry> = emptyList(),
    val lastScreenshotPath: String? = null,
    val isBusy: Boolean = false,
    val isLoadingApps: Boolean = false,
    val statusBanner: String? = null
)

class DeviceControlViewModel(
    private val deviceController: DeviceController,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val isBusyState = MutableStateFlow(false)
    private val isLoadingAppsState = MutableStateFlow(false)
    private val installedAppsState = MutableStateFlow<List<String>>(emptyList())
    private val statusBannerState = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(DeviceControlUiState())
    val uiState: StateFlow<DeviceControlUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseFlow = combine(
                deviceController.probeStatus,
                settingsDataStore.deviceControlSettingsFlow,
                deviceController.telemetry,
                deviceController.uiNodes,
                deviceController.actionLogs
            ) { probe, settings, telemetry, nodes, logs ->
                CoreBundle(probe, settings, telemetry, nodes, logs)
            }

            val auxFlow = combine(
                deviceController.lastScreenshotPath,
                isBusyState,
                isLoadingAppsState,
                installedAppsState,
                statusBannerState
            ) { shot, busy, loadingApps, apps, banner ->
                AuxBundle(shot, busy, loadingApps, apps, banner)
            }

            combine(baseFlow, auxFlow) { core, aux ->
                DeviceControlUiState(
                    probe = core.probe,
                    settings = core.settings,
                    telemetry = core.telemetry,
                    uiNodes = core.nodes,
                    installedApps = aux.apps,
                    actionLogs = core.logs,
                    lastScreenshotPath = aux.screenshotPath,
                    isBusy = aux.isBusy,
                    isLoadingApps = aux.isLoadingApps,
                    statusBanner = aux.banner
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        // Refresh immediately on startup and whenever Shizuku binder/permission state changes
        viewModelScope.launch {
            refreshProbe(fetchTelemetry = true)
        }
        viewModelScope.launch {
            deviceController.shizukuEventTick
                .drop(1)
                .collect {
                    refreshProbe(fetchTelemetry = true)
                }
        }
        viewModelScope.launch {
            settingsDataStore.deviceControlSettingsFlow
                .distinctUntilChanged { a, b -> a.preferredBackend == b.preferredBackend }
                .drop(1)
                .collect {
                    refreshProbe(fetchTelemetry = true)
                }
        }
    }

    fun refreshProbe(fetchTelemetry: Boolean = true) {
        viewModelScope.launch {
            isBusyState.value = true
            val probe = deviceController.refreshStatus()
            if (fetchTelemetry && probe.isReady) {
                deviceController.fetchTelemetry()
            }
            isBusyState.value = false
        }
    }

    fun requestShizukuPermission() {
        deviceController.requestShizukuPermission()
    }

    fun setPreferredBackend(backend: PrivilegeBackend) {
        viewModelScope.launch {
            settingsDataStore.setPreferredPrivilegeBackend(backend)
            statusBannerState.value = "Preferred backend set to ${backend.displayName}."
        }
    }

    fun setAiPhoneControlEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setAiPhoneControlEnabled(enabled)
        }
    }

    fun setConfirmSensitiveActions(confirm: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setConfirmSensitiveDeviceActions(confirm)
        }
    }

    fun tap(x: Int, y: Int) {
        runAction("Tapped ($x, $y)") {
            val res = deviceController.tap(x, y)
            if (res.exitCode != 0 && res.output.isNotBlank()) statusBannerState.value = res.output
        }
    }

    fun swipePreset(direction: String) {
        runAction("Swipe $direction") {
            val (w, h) = parseScreenDimensions(_uiState.value.telemetry?.screenResolution)
            val cx = w / 2
            val cy = h / 2
            val res = when (direction) {
                "UP" -> deviceController.swipe(cx, (h * 0.72f).toInt(), cx, (h * 0.28f).toInt(), 320)
                "DOWN" -> deviceController.swipe(cx, (h * 0.30f).toInt(), cx, (h * 0.76f).toInt(), 320)
                "LEFT" -> deviceController.swipe((w * 0.82f).toInt(), cy, (w * 0.18f).toInt(), cy, 280)
                "RIGHT" -> deviceController.swipe((w * 0.18f).toInt(), cy, (w * 0.82f).toInt(), cy, 280)
                "NOTIFICATIONS" -> deviceController.swipe(cx, 10, cx, (h * 0.65f).toInt(), 350)
                else -> deviceController.swipe(cx, (h * 0.7f).toInt(), cx, (h * 0.3f).toInt(), 320)
            }
            if (res.exitCode != 0 && res.output.isNotBlank()) statusBannerState.value = res.output
        }
    }

    fun inputText(text: String) {
        if (text.isBlank()) return
        runAction("Typed text") {
            val res = deviceController.inputText(text)
            if (res.exitCode != 0 && res.output.isNotBlank()) statusBannerState.value = res.output
        }
    }

    fun pressKey(key: String) {
        runAction("Key: $key") {
            val res = deviceController.pressKey(key)
            if (res.exitCode != 0 && res.output.isNotBlank()) statusBannerState.value = res.output
        }
    }

    fun setQuickSetting(setting: String, value: String) {
        runAction("$setting = $value") {
            val res = deviceController.setQuickSetting(setting, value)
            statusBannerState.value = if (res.exitCode == 0) {
                "Updated $setting -> $value"
            } else {
                res.output.ifBlank { "Failed updating $setting" }
            }
        }
    }

    fun dumpUiHierarchy() {
        viewModelScope.launch {
            isBusyState.value = true
            deviceController.dumpUiHierarchy().fold(
                onSuccess = { nodes ->
                    statusBannerState.value = "Captured ${nodes.size} UI element(s)."
                },
                onFailure = { err ->
                    statusBannerState.value = err.message ?: "UI dump failed."
                }
            )
            isBusyState.value = false
        }
    }

    fun captureScreenshot() {
        viewModelScope.launch {
            isBusyState.value = true
            deviceController.captureScreenshot().fold(
                onSuccess = { file ->
                    statusBannerState.value = "Screenshot saved (${file.name})."
                },
                onFailure = { err ->
                    statusBannerState.value = err.message ?: "Screenshot failed."
                }
            )
            isBusyState.value = false
        }
    }

    fun loadInstalledApps(includeSystem: Boolean, query: String) {
        viewModelScope.launch {
            isLoadingAppsState.value = true
            deviceController.listInstalledApps(includeSystem, query).fold(
                onSuccess = { pkgs ->
                    installedAppsState.value = pkgs
                    if (pkgs.isEmpty()) statusBannerState.value = "No matching apps found."
                },
                onFailure = { err ->
                    statusBannerState.value = err.message ?: "Failed listing installed apps."
                }
            )
            isLoadingAppsState.value = false
        }
    }

    fun launchApp(packageName: String) {
        runAction("Launch $packageName") {
            val res = deviceController.launchApp(packageName)
            statusBannerState.value = if (res.exitCode == 0) {
                "Launched $packageName"
            } else {
                res.output.ifBlank { "Could not launch $packageName" }
            }
        }
    }

    fun forceStopApp(packageName: String) {
        runAction("Force-stopped $packageName") {
            val res = deviceController.forceStopApp(packageName)
            statusBannerState.value = if (res.exitCode == 0) {
                "Force-stopped $packageName"
            } else {
                res.output.ifBlank { "Failed stopping $packageName" }
            }
        }
    }

    fun runPrivilegedShell(command: String) {
        val clean = command.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            isBusyState.value = true
            val res = deviceController.runShell(clean)
            statusBannerState.value = if (res.exitCode == 0) {
                "[${res.backendUsed.name}] Exit 0"
            } else {
                "[${res.backendUsed.name}] Exit ${res.exitCode}: ${res.output.take(120)}"
            }
            isBusyState.value = false
        }
    }

    fun clearLogs() {
        deviceController.clearLogs()
    }

    fun dismissBanner() {
        statusBannerState.value = null
    }

    private fun runAction(successBanner: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            isBusyState.value = true
            block()
            if (successBanner != null && statusBannerState.value == null) {
                statusBannerState.value = successBanner
            }
            isBusyState.value = false
        }
    }

    private fun parseScreenDimensions(resolution: String?): Pair<Int, Int> {
        val parts = resolution.orEmpty().split('x')
        val w = parts.getOrNull(0)?.toIntOrNull() ?: 1080
        val h = parts.getOrNull(1)?.toIntOrNull() ?: 2400
        return w to h
    }

    private data class CoreBundle(
        val probe: PrivilegeProbeResult,
        val settings: DeviceControlSettings,
        val telemetry: DeviceTelemetry?,
        val nodes: List<UiNodeElement>,
        val logs: List<DeviceActionLogEntry>
    )

    private data class AuxBundle(
        val screenshotPath: String?,
        val isBusy: Boolean,
        val isLoadingApps: Boolean,
        val apps: List<String>,
        val banner: String?
    )

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DeviceControlViewModel(
                deviceController = container.deviceController,
                settingsDataStore = container.settingsDataStore
            ) as T
        }
    }
}
