package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.agent.AgentToolExecutor
import com.example.data.device.AndroidPrivilegeShellExecutor
import com.example.data.device.DeviceCommandSafety
import com.example.data.device.DeviceController
import com.example.data.device.PrivilegeExecResult
import com.example.data.device.PrivilegeShellExecutor
import com.example.data.device.UiAutomatorDumpParser
import com.example.data.local.db.AppDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.data.terminal.TerminalExecutor
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.PrivilegeBackend
import com.example.domain.model.PrivilegeProbeResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceControlTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var json: Json

    private class FakePrivilegeShellExecutor(
        var rootAvailable: Boolean = true,
        var shizukuReady: Boolean = true
    ) : PrivilegeShellExecutor {
        override val shizukuEventTick: StateFlow<Int> = MutableStateFlow(0)
        val executedCommands = mutableListOf<Pair<String, PrivilegeBackend>>()
        var customHandler: ((String, PrivilegeBackend) -> PrivilegeExecResult)? = null

        override suspend fun probeStatus(preferredBackend: PrivilegeBackend): PrivilegeProbeResult {
            val active = AndroidPrivilegeShellExecutor.resolveActiveBackend(
                preferred = preferredBackend,
                rootAvailable = rootAvailable,
                shizukuReady = shizukuReady
            )
            return PrivilegeProbeResult(
                rootAvailable = rootAvailable,
                rootUid = if (rootAvailable) 0 else null,
                rootBinaryPath = if (rootAvailable) "/system/bin/su" else null,
                shizukuInstalled = true,
                shizukuRunning = shizukuReady,
                shizukuPermissionGranted = shizukuReady,
                shizukuUid = if (shizukuReady) 2000 else null,
                shizukuVersion = 13,
                shizukuUserServiceConnected = shizukuReady,
                preferredBackend = preferredBackend,
                activeBackend = active,
                statusSummary = when (active) {
                    PrivilegeBackend.ROOT -> "ROOT ACTIVE (uid=0)"
                    PrivilegeBackend.SHIZUKU -> "SHIZUKU ACTIVE (uid=2000)"
                    else -> "No Root (su) or Shizuku detected"
                }
            )
        }

        override suspend fun executePrivileged(
            command: String,
            preferredBackend: PrivilegeBackend,
            timeoutSeconds: Int
        ): PrivilegeExecResult {
            val active = AndroidPrivilegeShellExecutor.resolveActiveBackend(
                preferred = preferredBackend,
                rootAvailable = rootAvailable,
                shizukuReady = shizukuReady
            )
            executedCommands += command to active
            if (active == PrivilegeBackend.NONE) {
                return PrivilegeExecResult(1, "Unavailable", PrivilegeBackend.NONE)
            }
            customHandler?.invoke(command, active)?.let { return it }
            return PrivilegeExecResult(0, "ok", active)
        }

        override fun requestShizukuPermission(requestCode: Int) = Unit
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        workspaceManager = WorkspaceManager(context, db.projectDao())
        settingsDataStore = SettingsDataStore(context, KeystoreCryptoManager(), json)
        runBlocking {
            settingsDataStore.setPreferredPrivilegeBackend(PrivilegeBackend.AUTO)
            settingsDataStore.setAiPhoneControlEnabled(true)
            settingsDataStore.setConfirmSensitiveDeviceActions(true)
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `ui automator xml parser extracts interactive coordinates and ignores zero-size containers`() {
        val sampleXml = """
            <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
            <hierarchy rotation="0">
              <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.android.settings" content-desc="" clickable="false" scrollable="false" focused="false" bounds="[0,0][1080,2400]">
                <node index="0" text="Wi-Fi" resource-id="android:id/title" class="android.widget.TextView" package="com.android.settings" content-desc="" clickable="true" scrollable="false" focused="false" bounds="[100,400][500,520]" />
                <node index="1" text="" resource-id="com.android.settings:id/search_bar" class="android.widget.EditText" package="com.android.settings" content-desc="Search settings" clickable="true" scrollable="false" focused="true" bounds="[80,120][1000,240]" />
                <node index="2" text="Hidden" class="android.view.View" package="com.android.settings" bounds="[0,0][0,0]" clickable="true" />
              </node>
            </hierarchy>
        """.trimIndent()

        val nodes = UiAutomatorDumpParser.parse(sampleXml)
        assertEquals(2, nodes.size)
        assertEquals("Wi-Fi", nodes[0].text)
        assertEquals(300, nodes[0].centerX)
        assertEquals(460, nodes[0].centerY)
        assertTrue(nodes[0].clickable)

        assertEquals("Search settings", nodes[1].contentDesc)
        assertEquals(540, nodes[1].centerX)
        assertEquals(180, nodes[1].centerY)
        assertTrue(nodes[1].editable)
        assertTrue(nodes[1].focused)

        val formatted = UiAutomatorDumpParser.formatForAgent(nodes)
        assertTrue(formatted.contains("tap=(300,460)"))
        assertTrue(formatted.contains("Wi-Fi"))
    }

    @Test
    fun `safety guards block catastrophic commands and flag sensitive mutations`() {
        assertNotNull(DeviceCommandSafety.checkHardBlocked("rm -rf /"))
        assertNotNull(DeviceCommandSafety.checkHardBlocked("rm -rf /system"))
        assertNotNull(DeviceCommandSafety.checkHardBlocked("mkfs.ext4 /dev/block/sda1"))
        assertNotNull(DeviceCommandSafety.checkHardBlocked("dd if=/dev/zero of=/dev/block/bootdevice"))
        assertNull(DeviceCommandSafety.checkHardBlocked("input tap 200 300"))

        assertNotNull(DeviceCommandSafety.classifySensitiveReason("pm uninstall com.foo.bar"))
        assertNotNull(DeviceCommandSafety.classifySensitiveReason("am force-stop com.foo.bar"))
        assertNotNull(DeviceCommandSafety.classifySensitiveReason("settings put global airplane_mode_on 1"))
        assertNull(DeviceCommandSafety.classifySensitiveReason("dumpsys battery"))
    }

    @Test
    fun `backend resolver and settings datastore honor Auto Root and Shizuku preferences`() = runTest {
        assertEquals(
            PrivilegeBackend.ROOT,
            AndroidPrivilegeShellExecutor.resolveActiveBackend(PrivilegeBackend.AUTO, rootAvailable = true, shizukuReady = true)
        )
        assertEquals(
            PrivilegeBackend.SHIZUKU,
            AndroidPrivilegeShellExecutor.resolveActiveBackend(PrivilegeBackend.AUTO, rootAvailable = false, shizukuReady = true)
        )
        assertEquals(
            PrivilegeBackend.SHIZUKU,
            AndroidPrivilegeShellExecutor.resolveActiveBackend(PrivilegeBackend.SHIZUKU, rootAvailable = true, shizukuReady = true)
        )
        assertEquals(
            PrivilegeBackend.NONE,
            AndroidPrivilegeShellExecutor.resolveActiveBackend(PrivilegeBackend.ROOT, rootAvailable = false, shizukuReady = true)
        )

        val defaults = settingsDataStore.deviceControlSettingsFlow.first()
        assertEquals(PrivilegeBackend.AUTO, defaults.preferredBackend)
        assertTrue(defaults.aiPhoneControlEnabled)
        assertTrue(defaults.confirmSensitiveDeviceActions)

        settingsDataStore.setPreferredPrivilegeBackend(PrivilegeBackend.SHIZUKU)
        settingsDataStore.setAiPhoneControlEnabled(false)
        settingsDataStore.setConfirmSensitiveDeviceActions(false)

        val updated = settingsDataStore.deviceControlSettingsFlow.first()
        assertEquals(PrivilegeBackend.SHIZUKU, updated.preferredBackend)
        assertFalse(updated.aiPhoneControlEnabled)
        assertFalse(updated.confirmSensitiveDeviceActions)
    }

    @Test
    fun `device controller and agent tools execute tap swipe keys apps ui dump and respect confirmation gate`() = runTest {
        val fakeExec = FakePrivilegeShellExecutor(rootAvailable = false, shizukuReady = true).apply {
            customHandler = { cmd, active ->
                when {
                    cmd.contains("uiautomator dump") -> PrivilegeExecResult(
                        0,
                        """<hierarchy><node index="0" text="Open Browser" class="android.widget.Button" package="com.example" clickable="true" bounds="[100,200][300,300]"/></hierarchy>""",
                        active
                    )
                    cmd.startsWith("pm list packages") -> PrivilegeExecResult(
                        0,
                        "package:com.android.chrome\npackage:com.example.aicoder",
                        active
                    )
                    else -> PrivilegeExecResult(0, "executed:$cmd", active)
                }
            }
        }
        val controller = DeviceController(context, fakeExec, settingsDataStore)
        val agentTools = AgentToolExecutor(
            workspaceManager = workspaceManager,
            json = json,
            deviceController = controller
        )

        // Check device_status
        val statusRes = agentTools.execute(
            projectId = null,
            toolCallId = "d1",
            toolName = "device_status",
            argumentsJson = "{}",
            confirmDeletion = { false }
        )
        assertTrue(statusRes.success)
        assertTrue(statusRes.output.contains("SHIZUKU"))

        // Check device_ui_dump
        val dumpRes = agentTools.execute(
            projectId = null,
            toolCallId = "d2",
            toolName = "device_ui_dump",
            argumentsJson = "{}",
            confirmDeletion = { false }
        )
        assertTrue(dumpRes.success)
        assertTrue(dumpRes.output.contains("Open Browser"))
        assertTrue(dumpRes.output.contains("tap=(200,250)"))

        // Tap, swipe, type, keyevent, list apps, launch app
        val tapRes = agentTools.execute(
            projectId = null,
            toolCallId = "d3",
            toolName = "device_tap",
            argumentsJson = """{"x":200,"y":250}""",
            confirmDeletion = { false }
        )
        assertTrue(tapRes.success)
        assertTrue(fakeExec.executedCommands.any { it.first == "input tap 200 250" })

        val typeRes = agentTools.execute(
            projectId = null,
            toolCallId = "d4",
            toolName = "device_type_text",
            argumentsJson = """{"text":"hello world"}""",
            confirmDeletion = { false }
        )
        assertTrue(typeRes.success)
        assertTrue(fakeExec.executedCommands.any { it.first == "input text 'hello%sworld'" })

        val keyRes = agentTools.execute(
            projectId = null,
            toolCallId = "d5",
            toolName = "device_keyevent",
            argumentsJson = """{"key":"HOME"}""",
            confirmDeletion = { false }
        )
        assertTrue(keyRes.success)
        assertTrue(fakeExec.executedCommands.any { it.first == "input keyevent KEYCODE_HOME" })

        val appsRes = agentTools.execute(
            projectId = null,
            toolCallId = "d6",
            toolName = "device_list_apps",
            argumentsJson = """{"query":"chrome"}""",
            confirmDeletion = { false }
        )
        assertTrue(appsRes.success)
        assertTrue(appsRes.output.contains("com.android.chrome"))
        assertFalse(appsRes.output.contains("com.example.aicoder"))

        val launchRes = agentTools.execute(
            projectId = null,
            toolCallId = "d7",
            toolName = "device_launch_app",
            argumentsJson = """{"package_name":"com.android.chrome"}""",
            confirmDeletion = { false }
        )
        assertTrue(launchRes.success)

        // device_shell asks user confirmation when confirmSensitiveDeviceActions = true
        val deniedShell = agentTools.execute(
            projectId = null,
            toolCallId = "d8",
            toolName = "device_shell",
            argumentsJson = """{"command":"settings put system screen_brightness 180"}""",
            confirmDeletion = { false },
            confirmDeviceAction = { false }
        )
        assertTrue(deniedShell.wasCancelled)
        assertFalse(deniedShell.success)

        val approvedShell = agentTools.execute(
            projectId = null,
            toolCallId = "d9",
            toolName = "device_shell",
            argumentsJson = """{"command":"dumpsys battery"}""",
            confirmDeletion = { false },
            confirmDeviceAction = { true }
        )
        assertTrue(approvedShell.success)

        // When AI Phone Control is disabled, control tools refuse politely
        settingsDataStore.setAiPhoneControlEnabled(false)
        val disabledTap = agentTools.execute(
            projectId = null,
            toolCallId = "d10",
            toolName = "device_tap",
            argumentsJson = """{"x":10,"y":10}""",
            confirmDeletion = { false }
        )
        assertFalse(disabledTap.success)
        assertTrue(disabledTap.output.contains("disabled"))

        // Action logs recorded
        assertTrue(controller.actionLogs.value.isNotEmpty())
    }

    @Test
    fun `terminal executor routes device status and su shizuku commands`() = runTest {
        val fakeExec = FakePrivilegeShellExecutor(rootAvailable = true, shizukuReady = false)
        val controller = DeviceController(context, fakeExec, settingsDataStore)
        val terminal = TerminalExecutor(
            context = context,
            workspaceManager = workspaceManager,
            okHttpClient = OkHttpClient(),
            json = json,
            deviceController = controller
        )

        val statusOut = terminal.execute("device status", projectId = null)
        assertEquals(0, statusOut.exitCode)
        assertTrue(statusOut.output.contains("ROOT ACTIVE"))

        val suOut = terminal.execute("su id", projectId = null)
        assertEquals(0, suOut.exitCode)
        assertTrue(fakeExec.executedCommands.any { it.first == "id" && it.second == PrivilegeBackend.ROOT })
    }
}
