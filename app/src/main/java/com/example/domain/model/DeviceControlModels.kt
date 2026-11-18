package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PrivilegeBackend(val displayName: String) {
    AUTO("Auto (Root / Shizuku)"),
    ROOT("Root (su)"),
    SHIZUKU("Shizuku"),
    NONE("None")
}

enum class ShizukuBinderState(val label: String) {
    NOT_INSTALLED("Not Installed"),
    STOPPED("Service Stopped"),
    PERMISSION_REQUIRED("Permission Needed"),
    READY("Authorized")
}

data class PrivilegeProbeResult(
    val rootAvailable: Boolean = false,
    val rootUid: Int? = null,
    val rootBinaryPath: String? = null,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val shizukuUid: Int? = null,
    val shizukuVersion: Int? = null,
    val shizukuUserServiceConnected: Boolean = false,
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.AUTO,
    val activeBackend: PrivilegeBackend = PrivilegeBackend.NONE,
    val statusSummary: String = "Probing Root & Shizuku..."
) {
    val shizukuBinderState: ShizukuBinderState
        get() = when {
            !shizukuInstalled && !shizukuRunning -> ShizukuBinderState.NOT_INSTALLED
            !shizukuRunning -> ShizukuBinderState.STOPPED
            !shizukuPermissionGranted -> ShizukuBinderState.PERMISSION_REQUIRED
            else -> ShizukuBinderState.READY
        }

    val isReady: Boolean
        get() = activeBackend == PrivilegeBackend.ROOT || activeBackend == PrivilegeBackend.SHIZUKU
}

data class DeviceTelemetry(
    val privilegeSummary: String = "Unavailable",
    val screenResolution: String = "Unknown",
    val batteryLevel: Int? = null,
    val batteryCharging: Boolean? = null,
    val foregroundApp: String = "Unknown",
    val androidRelease: String = "",
    val deviceModel: String = ""
)

data class UiNodeElement(
    val index: Int,
    val text: String = "",
    val contentDesc: String = "",
    val resourceId: String = "",
    val className: String = "",
    val packageName: String = "",
    val bounds: String = "",
    val centerX: Int = 0,
    val centerY: Int = 0,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val focused: Boolean = false
) {
    val displayLabel: String
        get() = when {
            text.isNotBlank() -> text
            contentDesc.isNotBlank() -> contentDesc
            resourceId.isNotBlank() -> resourceId.substringAfterLast('/')
            else -> className.substringAfterLast('.')
        }
}

data class DeviceActionLogEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val backend: PrivilegeBackend,
    val action: String,
    val command: String,
    val exitCode: Int,
    val output: String,
    val success: Boolean
)

data class PendingDeviceActionConfirmation(
    val toolCallId: String,
    val toolName: String,
    val actionTitle: String,
    val commandPreview: String,
    val reason: String
)

data class DeviceControlSettings(
    val preferredBackend: PrivilegeBackend = PrivilegeBackend.AUTO,
    val aiPhoneControlEnabled: Boolean = true,
    val confirmSensitiveDeviceActions: Boolean = true
)
