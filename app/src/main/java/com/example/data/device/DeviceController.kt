package com.example.data.device

import android.content.Context
import android.os.Build
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

object DeviceCommandSafety {
    private val HARD_BLOCKED_PATTERNS = listOf(
        Regex("""\brm\s+-[a-z]*[rf][a-z]*\s+/($|\s|\*)"""),
        Regex("""\brm\s+-[a-z]*[rf][a-z]*\s+/(system|vendor|product|data|boot|dev)($|\s|/\*)"""),
        Regex("""\bmkfs(\.[a-z0-9]+)?\b"""),
        Regex("""\bdd\s+.*\bof=/dev/(block/)?"""),
        Regex("""\bflash_image\b"""),
        Regex("""\bwipe\s+data\b"""),
        Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:""")
    )

    private val SENSITIVE_PATTERNS = listOf(
        Regex("""\bpm\s+(uninstall|clear|disable|disable-user|hide)\b""") to "Modifies or clears installed app packages/data",
        Regex("""\bam\s+force-stop\b""") to "Force-stops a running application",
        Regex("""\b(reboot|poweroff|shutdown)\b""") to "Reboots or powers off the device",
        Regex("""\bsvc\s+power\s+shutdown\b""") to "Shuts down the device",
        Regex("""\bsettings\s+(put|delete)\b""") to "Writes Android system/secure/global settings",
        Regex("""\b(rm|chmod|chown|mv|dd|kill|killall|pkill)\b""") to "Deletes, terminates, or alters system files/processes"
    )

    fun checkHardBlocked(command: String): String? {
        val lower = command.lowercase()
        return if (HARD_BLOCKED_PATTERNS.any { it.containsMatchIn(lower) }) {
            "Blocked potentially catastrophic device command for system safety."
        } else {
            null
        }
    }

    fun classifySensitiveReason(command: String): String? {
        val lower = command.lowercase()
        return SENSITIVE_PATTERNS.firstOrNull { (regex, _) -> regex.containsMatchIn(lower) }?.second
    }
}

class DeviceController(
    context: Context,
    private val privilegeExecutor: PrivilegeShellExecutor,
    private val settingsDataStore: SettingsDataStore
) {
    private val capturesDir: File = File(context.applicationContext.filesDir, "device_captures").apply { mkdirs() }

    private val _probeStatus = MutableStateFlow(PrivilegeProbeResult())
    val probeStatus: StateFlow<PrivilegeProbeResult> = _probeStatus.asStateFlow()

    private val _telemetry = MutableStateFlow<DeviceTelemetry?>(null)
    val telemetry: StateFlow<DeviceTelemetry?> = _telemetry.asStateFlow()

    private val _uiNodes = MutableStateFlow<List<UiNodeElement>>(emptyList())
    val uiNodes: StateFlow<List<UiNodeElement>> = _uiNodes.asStateFlow()

    private val _actionLogs = MutableStateFlow<List<DeviceActionLogEntry>>(emptyList())
    val actionLogs: StateFlow<List<DeviceActionLogEntry>> = _actionLogs.asStateFlow()

    private val _lastScreenshotPath = MutableStateFlow<String?>(null)
    val lastScreenshotPath: StateFlow<String?> = _lastScreenshotPath.asStateFlow()

    val shizukuEventTick: StateFlow<Int> = privilegeExecutor.shizukuEventTick

    suspend fun getControlSettings(): DeviceControlSettings =
        settingsDataStore.deviceControlSettingsFlow.first()

    suspend fun refreshStatus(): PrivilegeProbeResult {
        val settings = getControlSettings()
        val probed = privilegeExecutor.probeStatus(settings.preferredBackend)
        _probeStatus.value = probed
        return probed
    }

    fun requestShizukuPermission() {
        privilegeExecutor.requestShizukuPermission()
    }

    fun clearLogs() {
        _actionLogs.value = emptyList()
    }

    suspend fun fetchTelemetry(): Result<DeviceTelemetry> {
        val status = refreshStatus()
        if (!status.isReady) {
            return Result.failure(IllegalStateException(status.statusSummary))
        }

        val cmd = """
            echo "RES=$(wm size 2>/dev/null | tail -n 1)";
            echo "BAT=$(dumpsys battery 2>/dev/null | grep -E 'level:|status:' | tr '\n' ' ')";
            echo "TOP=$(dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -n 1)"
        """.trimIndent().replace("\n", " ")

        val res = privilegeExecutor.executePrivileged(cmd, status.activeBackend, timeoutSeconds = 12)
        if (res.exitCode != 0 && res.output.isBlank()) {
            return Result.failure(IllegalStateException("Failed querying device telemetry."))
        }

        val lines = res.output.lines()
        val resLine = lines.firstOrNull { it.startsWith("RES=") }?.removePrefix("RES=").orEmpty()
        val resolution = Regex("""(\d+x\d+)""").find(resLine)?.groupValues?.getOrNull(1) ?: "Unknown"

        val batLine = lines.firstOrNull { it.startsWith("BAT=") }?.removePrefix("BAT=").orEmpty()
        val batteryLevel = Regex("""level:\s*(\d+)""").find(batLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val batteryStatus = Regex("""status:\s*(\d+)""").find(batLine)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val charging = when (batteryStatus) {
            2, 5 -> true
            3, 4 -> false
            else -> null
        }

        val topLine = lines.firstOrNull { it.startsWith("TOP=") }?.removePrefix("TOP=").orEmpty()
        val fgApp = Regex("""([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)""").find(topLine)?.groupValues?.getOrNull(1)
            ?: topLine.trim().ifBlank { "Unknown" }

        val info = DeviceTelemetry(
            privilegeSummary = status.statusSummary,
            screenResolution = resolution,
            batteryLevel = batteryLevel,
            batteryCharging = charging,
            foregroundApp = fgApp,
            androidRelease = "Android ${Build.VERSION.RELEASE ?: "?"} (API ${Build.VERSION.SDK_INT})",
            deviceModel = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim()
        )
        _telemetry.value = info
        return Result.success(info)
    }

    suspend fun tap(x: Int, y: Int): PrivilegeExecResult {
        val cmd = "input tap $x $y"
        return runLogged("Tap ($x, $y)", cmd)
    }

    suspend fun longPress(x: Int, y: Int, durationMs: Int = 800): PrivilegeExecResult {
        val dur = durationMs.coerceIn(250, 5000)
        val cmd = "input swipe $x $y $x $y $dur"
        return runLogged("Long press ($x, $y, ${dur}ms)", cmd)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 350): PrivilegeExecResult {
        val dur = durationMs.coerceIn(50, 5000)
        val cmd = "input swipe $x1 $y1 $x2 $y2 $dur"
        return runLogged("Swipe ($x1,$y1 -> $x2,$y2)", cmd)
    }

    suspend fun inputText(text: String): PrivilegeExecResult {
        val trimmed = text
        if (trimmed.isEmpty()) {
            return PrivilegeExecResult(1, "Text cannot be empty.", PrivilegeBackend.NONE)
        }
        // Android `input text` interprets `%s` as space; escape single quotes cleanly for sh.
        val formattedForInput = trimmed
            .replace("\r\n", "\n")
            .replace("\n", " ")
            .replace(" ", "%s")
        val shEscaped = "'" + formattedForInput.replace("'", "'\"'\"'") + "'"
        val cmd = "input text $shEscaped"
        return runLogged("Type text (${trimmed.take(24)})", cmd)
    }

    suspend fun pressKey(keyNameOrCode: String): PrivilegeExecResult {
        val resolved = resolveKeyCode(keyNameOrCode)
            ?: return PrivilegeExecResult(1, "Unsupported keyevent: '$keyNameOrCode'", PrivilegeBackend.NONE)
        val cmd = "input keyevent $resolved"
        return runLogged("Key: $resolved", cmd)
    }

    suspend fun dumpUiHierarchy(): Result<List<UiNodeElement>> {
        val tmpXml = "/data/local/tmp/ib_coder_ui.xml"
        val cmd = "uiautomator dump $tmpXml >/dev/null 2>&1 && cat $tmpXml && rm -f $tmpXml"
        val res = runLogged("Inspect UI Tree", cmd, timeoutSeconds = 20)
        if (res.exitCode != 0 && !res.output.contains("<hierarchy")) {
            return Result.failure(
                IllegalStateException(
                    res.output.ifBlank { "uiautomator dump failed (exit ${res.exitCode}). Ensure Root or Shizuku is active." }
                )
            )
        }
        val parsed = runCatching { UiAutomatorDumpParser.parse(res.output) }.getOrElse { emptyList() }
        _uiNodes.value = parsed
        return Result.success(parsed)
    }

    suspend fun captureScreenshot(): Result<File> {
        val remoteTmp = "/data/local/tmp/ib_coder_cap.png"
        val destFile = File(capturesDir, "screen_${System.currentTimeMillis()}.png")
        val cmd = "screencap -p $remoteTmp && base64 $remoteTmp && rm -f $remoteTmp"
        val res = runLogged("Screenshot", cmd, timeoutSeconds = 20, suppressLogOutput = true)
        if (res.exitCode != 0 || res.output.isBlank()) {
            return Result.failure(
                IllegalStateException(res.output.ifBlank { "screencap failed (exit ${res.exitCode})." })
            )
        }
        return runCatching {
            val cleanBase64 = res.output.replace(Regex("\\s+"), "")
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            destFile.writeBytes(bytes)
            _lastScreenshotPath.value = destFile.absolutePath
            destFile
        }
    }

    suspend fun listInstalledApps(includeSystem: Boolean = false, query: String = ""): Result<List<String>> {
        val flag = if (includeSystem) "" else "-3"
        val cmd = "pm list packages $flag".trim()
        val res = runLogged("List Apps", cmd, timeoutSeconds = 15, suppressLogOutput = true)
        if (res.exitCode != 0) {
            return Result.failure(IllegalStateException(res.output.ifBlank { "pm list packages failed" }))
        }
        val q = query.trim().lowercase()
        val pkgs = res.output.lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotEmpty() }
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .sorted()
            .toList()
        return Result.success(pkgs)
    }

    suspend fun launchApp(packageName: String): PrivilegeExecResult {
        val cleanPkg = sanitizePackageName(packageName)
            ?: return PrivilegeExecResult(1, "Invalid package name: '$packageName'", PrivilegeBackend.NONE)
        val cmd = """
            act=$(cmd package resolve-activity --brief $cleanPkg 2>/dev/null | tail -n 1);
            if echo "${'$'}act" | grep -q '/'; then am start -n "${'$'}act"; else monkey -p $cleanPkg -c android.intent.category.LAUNCHER 1; fi
        """.trimIndent().replace("\n", " ")
        return runLogged("Launch $cleanPkg", cmd, timeoutSeconds = 12)
    }

    suspend fun forceStopApp(packageName: String): PrivilegeExecResult {
        val cleanPkg = sanitizePackageName(packageName)
            ?: return PrivilegeExecResult(1, "Invalid package name: '$packageName'", PrivilegeBackend.NONE)
        return runLogged("Force Stop $cleanPkg", "am force-stop $cleanPkg")
    }

    suspend fun setQuickSetting(setting: String, value: String): PrivilegeExecResult {
        val key = setting.trim().lowercase()
        val v = value.trim().lowercase()
        val cmd = when (key) {
            "wifi" -> when (v) {
                "on", "true", "1", "enable" -> "svc wifi enable"
                "off", "false", "0", "disable" -> "svc wifi disable"
                else -> return PrivilegeExecResult(1, "Invalid wifi value '$value' (use on/off).", PrivilegeBackend.NONE)
            }
            "data", "mobile_data" -> when (v) {
                "on", "true", "1", "enable" -> "svc data enable"
                "off", "false", "0", "disable" -> "svc data disable"
                else -> return PrivilegeExecResult(1, "Invalid data value '$value' (use on/off).", PrivilegeBackend.NONE)
            }
            "bluetooth" -> when (v) {
                "on", "true", "1", "enable" -> "cmd bluetooth_manager enable || svc bluetooth enable"
                "off", "false", "0", "disable" -> "cmd bluetooth_manager disable || svc bluetooth disable"
                else -> return PrivilegeExecResult(1, "Invalid bluetooth value '$value' (use on/off).", PrivilegeBackend.NONE)
            }
            "stay_awake" -> when (v) {
                "on", "true", "1", "enable" -> "svc power stayon true"
                "off", "false", "0", "disable" -> "svc power stayon false"
                else -> return PrivilegeExecResult(1, "Invalid stay_awake value '$value' (use on/off).", PrivilegeBackend.NONE)
            }
            "auto_rotate" -> when (v) {
                "on", "true", "1", "enable" -> "settings put system accelerometer_rotation 1"
                "off", "false", "0", "disable" -> "settings put system accelerometer_rotation 0"
                else -> return PrivilegeExecResult(1, "Invalid auto_rotate value '$value' (use on/off).", PrivilegeBackend.NONE)
            }
            "brightness" -> {
                val level = v.toIntOrNull()?.coerceIn(1, 255)
                    ?: return PrivilegeExecResult(1, "Invalid brightness '$value' (use 1-255).", PrivilegeBackend.NONE)
                "settings put system screen_brightness $level"
            }
            else -> return PrivilegeExecResult(
                1,
                "Unknown quick setting '$setting'. Supported: wifi, data, bluetooth, stay_awake, auto_rotate, brightness.",
                PrivilegeBackend.NONE
            )
        }
        return runLogged("Setting $key=$v", cmd)
    }

    suspend fun runShell(command: String, timeoutSeconds: Int = 20): PrivilegeExecResult {
        val blocked = DeviceCommandSafety.checkHardBlocked(command)
        if (blocked != null) {
            recordLog(PrivilegeBackend.NONE, "Blocked Shell", command, 1, blocked, false)
            return PrivilegeExecResult(1, blocked, PrivilegeBackend.NONE)
        }
        return runLogged("Privileged Shell", command, timeoutSeconds = timeoutSeconds.coerceIn(2, 120))
    }

    private suspend fun runLogged(
        action: String,
        command: String,
        timeoutSeconds: Int = 20,
        suppressLogOutput: Boolean = false
    ): PrivilegeExecResult {
        val blocked = DeviceCommandSafety.checkHardBlocked(command)
        if (blocked != null) {
            recordLog(PrivilegeBackend.NONE, action, command, 1, blocked, false)
            return PrivilegeExecResult(1, blocked, PrivilegeBackend.NONE)
        }
        val settings = getControlSettings()
        val res = privilegeExecutor.executePrivileged(command, settings.preferredBackend, timeoutSeconds)
        val loggedOut = if (suppressLogOutput && res.exitCode == 0) {
            "OK (${res.output.lines().size} line(s))"
        } else {
            res.output.take(1200)
        }
        recordLog(
            backend = res.backendUsed,
            action = action,
            command = command,
            exitCode = res.exitCode,
            output = loggedOut,
            success = res.exitCode == 0
        )
        return res
    }

    private fun recordLog(
        backend: PrivilegeBackend,
        action: String,
        command: String,
        exitCode: Int,
        output: String,
        success: Boolean
    ) {
        _actionLogs.update { current ->
            val next = DeviceActionLogEntry(
                id = UUID.randomUUID().toString(),
                backend = backend,
                action = action,
                command = command.take(180),
                exitCode = exitCode,
                output = output,
                success = success
            )
            (listOf(next) + current).take(MAX_LOGS)
        }
    }

    companion object {
        private const val MAX_LOGS = 120

        private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

        fun sanitizePackageName(raw: String): String? {
            val clean = raw.trim()
            return if (PACKAGE_REGEX.matches(clean)) clean else null
        }

        fun resolveKeyCode(raw: String): String? {
            val trimmed = raw.trim().uppercase()
            if (trimmed.toIntOrNull() != null) return trimmed
            if (trimmed.startsWith("KEYCODE_") && trimmed.matches(Regex("^KEYCODE_[A-Z0-9_]+$"))) {
                return trimmed
            }
            return when (trimmed) {
                "HOME" -> "KEYCODE_HOME"
                "BACK" -> "KEYCODE_BACK"
                "RECENTS", "APP_SWITCH", "OVERVIEW" -> "KEYCODE_APP_SWITCH"
                "POWER", "LOCK" -> "KEYCODE_POWER"
                "WAKE", "WAKEUP" -> "KEYCODE_WAKEUP"
                "SLEEP" -> "KEYCODE_SLEEP"
                "VOL_UP", "VOLUME_UP" -> "KEYCODE_VOLUME_UP"
                "VOL_DOWN", "VOLUME_DOWN" -> "KEYCODE_VOLUME_DOWN"
                "MUTE", "VOLUME_MUTE" -> "KEYCODE_VOLUME_MUTE"
                "ENTER", "OK", "RETURN" -> "KEYCODE_ENTER"
                "DEL", "BACKSPACE", "DELETE" -> "KEYCODE_DEL"
                "TAB" -> "KEYCODE_TAB"
                "SPACE" -> "KEYCODE_SPACE"
                "ESC", "ESCAPE" -> "KEYCODE_ESCAPE"
                "SEARCH" -> "KEYCODE_SEARCH"
                "MENU" -> "KEYCODE_MENU"
                "SETTINGS" -> "KEYCODE_SETTINGS"
                "NOTIFICATIONS", "NOTIFICATION" -> "KEYCODE_NOTIFICATION"
                "PLAY_PAUSE", "MEDIA_PLAY_PAUSE" -> "KEYCODE_MEDIA_PLAY_PAUSE"
                "NEXT", "MEDIA_NEXT" -> "KEYCODE_MEDIA_NEXT"
                "PREV", "PREVIOUS", "MEDIA_PREVIOUS" -> "KEYCODE_MEDIA_PREVIOUS"
                "DPAD_UP", "UP" -> "KEYCODE_DPAD_UP"
                "DPAD_DOWN", "DOWN" -> "KEYCODE_DPAD_DOWN"
                "DPAD_LEFT", "LEFT" -> "KEYCODE_DPAD_LEFT"
                "DPAD_RIGHT", "RIGHT" -> "KEYCODE_DPAD_RIGHT"
                "DPAD_CENTER" -> "KEYCODE_DPAD_CENTER"
                "COPY" -> "KEYCODE_COPY"
                "PASTE" -> "KEYCODE_PASTE"
                else -> null
            }
        }
    }
}
