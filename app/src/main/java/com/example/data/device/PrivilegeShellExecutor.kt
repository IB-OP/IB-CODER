package com.example.data.device

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import com.example.BuildConfig
import com.example.device.IDeviceShellService
import com.example.device.ShizukuShellUserService
import com.example.domain.model.PrivilegeBackend
import com.example.domain.model.PrivilegeProbeResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

data class PrivilegeExecResult(
    val exitCode: Int,
    val output: String,
    val backendUsed: PrivilegeBackend
)

interface PrivilegeShellExecutor {
    val shizukuEventTick: StateFlow<Int>

    suspend fun probeStatus(preferredBackend: PrivilegeBackend): PrivilegeProbeResult

    suspend fun executePrivileged(
        command: String,
        preferredBackend: PrivilegeBackend,
        timeoutSeconds: Int = 20
    ): PrivilegeExecResult

    fun requestShizukuPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE)

    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 4201
    }
}

class AndroidPrivilegeShellExecutor(
    context: Context
) : PrivilegeShellExecutor {

    private val appContext = context.applicationContext

    private val _shizukuEventTick = MutableStateFlow(0)
    override val shizukuEventTick: StateFlow<Int> = _shizukuEventTick.asStateFlow()

    @Volatile
    private var boundService: IDeviceShellService? = null

    @Volatile
    private var cachedRootPath: String? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _shizukuEventTick.update { it + 1 }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        boundService = null
        _shizukuEventTick.update { it + 1 }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult != PackageManager.PERMISSION_GRANTED) {
            boundService = null
        }
        _shizukuEventTick.update { it + 1 }
    }

    init {
        runCatching {
            if (Looper.myLooper() != null || Looper.getMainLooper() != null) {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
            }
        }
    }

    override fun requestShizukuPermission(requestCode: Int) {
        runCatching {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) {
                Shizuku.requestPermission(requestCode)
            }
        }
    }

    override suspend fun probeStatus(preferredBackend: PrivilegeBackend): PrivilegeProbeResult =
        withContext(Dispatchers.IO) {
            val rootCheck = probeRoot()
            val shizukuInstalled = isPackageInstalled(SHIZUKU_PACKAGE_NAME)
            val shizukuRunning = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            val shizukuVersion = if (shizukuRunning) {
                runCatching { Shizuku.getVersion() }.getOrNull()
            } else null
            val shizukuPermGranted = if (shizukuRunning) {
                runCatching {
                    !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }.getOrDefault(false)
            } else false

            var shizukuUid: Int? = null
            var serviceConnected = boundService?.asBinder()?.pingBinder() == true
            if (shizukuRunning && shizukuPermGranted) {
                val service = getOrBindShizukuService()
                if (service != null) {
                    serviceConnected = true
                    shizukuUid = runCatching { service.uid }.getOrNull()
                }
            }

            val shizukuReady = shizukuRunning && shizukuPermGranted
            val activeBackend = resolveActiveBackend(
                preferred = preferredBackend,
                rootAvailable = rootCheck.available,
                shizukuReady = shizukuReady
            )

            val summary = buildString {
                when (activeBackend) {
                    PrivilegeBackend.ROOT -> append("ROOT ACTIVE (uid=${rootCheck.uid ?: 0})")
                    PrivilegeBackend.SHIZUKU -> append(
                        "SHIZUKU ACTIVE (uid=${shizukuUid ?: 2000}, v${shizukuVersion ?: "?"})"
                    )
                    PrivilegeBackend.AUTO,
                    PrivilegeBackend.NONE -> {
                        when {
                            shizukuRunning && !shizukuPermGranted -> append("Shizuku running — tap Authorize")
                            shizukuInstalled && !shizukuRunning -> append("Shizuku installed — start Shizuku service")
                            else -> append("No Root (su) or Shizuku detected")
                        }
                    }
                }
            }

            PrivilegeProbeResult(
                rootAvailable = rootCheck.available,
                rootUid = rootCheck.uid,
                rootBinaryPath = rootCheck.binaryPath,
                shizukuInstalled = shizukuInstalled,
                shizukuRunning = shizukuRunning,
                shizukuPermissionGranted = shizukuPermGranted,
                shizukuUid = shizukuUid,
                shizukuVersion = shizukuVersion,
                shizukuUserServiceConnected = serviceConnected,
                preferredBackend = preferredBackend,
                activeBackend = activeBackend,
                statusSummary = summary
            )
        }

    override suspend fun executePrivileged(
        command: String,
        preferredBackend: PrivilegeBackend,
        timeoutSeconds: Int
    ): PrivilegeExecResult = withContext(Dispatchers.IO) {
        val cleanCmd = command.trim()
        if (cleanCmd.isEmpty()) {
            return@withContext PrivilegeExecResult(0, "", PrivilegeBackend.NONE)
        }

        val shizukuReady = runCatching {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        when (preferredBackend) {
            PrivilegeBackend.ROOT -> executeViaRoot(cleanCmd, timeoutSeconds)
            PrivilegeBackend.SHIZUKU -> executeViaShizuku(cleanCmd, timeoutSeconds)
            PrivilegeBackend.AUTO -> {
                val rootProbe = probeRoot()
                when {
                    rootProbe.available -> executeViaRoot(cleanCmd, timeoutSeconds, rootProbe.binaryPath)
                    shizukuReady -> executeViaShizuku(cleanCmd, timeoutSeconds)
                    else -> PrivilegeExecResult(
                        exitCode = 1,
                        output = "Neither Root (su) nor authorized Shizuku is active. Open Phone Control to verify Root or authorize Shizuku.",
                        backendUsed = PrivilegeBackend.NONE
                    )
                }
            }
            PrivilegeBackend.NONE -> PrivilegeExecResult(
                exitCode = 1,
                output = "Privilege backend is set to None.",
                backendUsed = PrivilegeBackend.NONE
            )
        }
    }

    private fun executeViaRoot(
        command: String,
        timeoutSeconds: Int,
        knownSuPath: String? = null
    ): PrivilegeExecResult {
        val suBin = knownSuPath ?: findSuBinary() ?: "su"
        return try {
            val process = ProcessBuilder(suBin, "-c", command)
                .redirectErrorStream(true)
                .start()

            val outputBuf = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (outputBuf.length < MAX_OUTPUT_CHARS) {
                                outputBuf.appendLine(line)
                            }
                        }
                    }
                }
            }.apply { start() }

            val finished = process.waitFor(timeoutSeconds.coerceIn(2, 120).toLong(), TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                readerThread.join(500)
                return PrivilegeExecResult(
                    exitCode = 124,
                    output = "Root command timed out after ${timeoutSeconds}s.\n${outputBuf.toString().trim()}".trim(),
                    backendUsed = PrivilegeBackend.ROOT
                )
            }

            readerThread.join(1000)
            val code = process.exitValue()
            PrivilegeExecResult(
                exitCode = code,
                output = outputBuf.toString().trim().take(MAX_OUTPUT_CHARS),
                backendUsed = PrivilegeBackend.ROOT
            )
        } catch (t: Throwable) {
            PrivilegeExecResult(
                exitCode = 127,
                output = "Root (su) unavailable or denied: ${t.message ?: t::class.simpleName}",
                backendUsed = PrivilegeBackend.ROOT
            )
        }
    }

    private suspend fun executeViaShizuku(
        command: String,
        timeoutSeconds: Int
    ): PrivilegeExecResult {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            return PrivilegeExecResult(
                exitCode = 1,
                output = "Shizuku service is not running. Start Shizuku via Wireless Debugging or Root and try again.",
                backendUsed = PrivilegeBackend.SHIZUKU
            )
        }
        val granted = runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!granted) {
            return PrivilegeExecResult(
                exitCode = 1,
                output = "Shizuku permission not granted yet. Tap 'Authorize Shizuku' in Phone Control.",
                backendUsed = PrivilegeBackend.SHIZUKU
            )
        }

        val service = getOrBindShizukuService()
            ?: return PrivilegeExecResult(
                exitCode = 1,
                output = "Timed out binding Shizuku shell UserService. Re-open Shizuku or restart the Shizuku service.",
                backendUsed = PrivilegeBackend.SHIZUKU
            )

        val rawResponse = runCatching {
            service.execCommand(command, timeoutSeconds.coerceIn(2, 120))
        }.getOrElse { err ->
            boundService = null
            return PrivilegeExecResult(
                exitCode = 1,
                output = "Shizuku IPC error: ${err.message ?: err::class.simpleName}",
                backendUsed = PrivilegeBackend.SHIZUKU
            )
        }

        val firstNewline = rawResponse.indexOf('\n')
        val header = if (firstNewline >= 0) rawResponse.substring(0, firstNewline) else rawResponse
        val exitCode = header.removePrefix("EXIT=").trim().toIntOrNull() ?: 0
        val body = if (firstNewline >= 0) rawResponse.substring(firstNewline + 1).trim() else ""
        return PrivilegeExecResult(
            exitCode = exitCode,
            output = body,
            backendUsed = PrivilegeBackend.SHIZUKU
        )
    }

    private suspend fun getOrBindShizukuService(): IDeviceShellService? {
        val current = boundService
        if (current != null && runCatching { current.asBinder().pingBinder() }.getOrDefault(false)) {
            return current
        }

        val deferred = CompletableDeferred<IDeviceShellService?>()
        val args = Shizuku.UserServiceArgs(
            ComponentName(appContext.packageName, ShizukuShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service != null && service.pingBinder()) {
                    val iface = IDeviceShellService.Stub.asInterface(service)
                    boundService = iface
                    deferred.complete(iface)
                } else {
                    deferred.complete(null)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }

        return try {
            withContext(Dispatchers.Main) {
                Shizuku.bindUserService(args, connection)
            }
            withTimeoutOrNull(5000L) { deferred.await() }
        } catch (_: Throwable) {
            null
        }
    }

    private data class RootProbe(
        val available: Boolean,
        val uid: Int?,
        val binaryPath: String?
    )

    private fun probeRoot(): RootProbe {
        val candidate = findSuBinary()
        val cmd = candidate ?: "su"
        return try {
            val process = ProcessBuilder(cmd, "-c", "id -u")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                return RootProbe(false, null, candidate)
            }
            val out = process.inputStream.bufferedReader().readText().trim()
            val uid = out.lineSequence().lastOrNull()?.trim()?.toIntOrNull()
            if (process.exitValue() == 0 && uid == 0) {
                cachedRootPath = candidate ?: "su"
                RootProbe(true, 0, cachedRootPath)
            } else {
                RootProbe(false, uid, candidate)
            }
        } catch (_: Throwable) {
            RootProbe(false, null, candidate)
        }
    }

    private fun findSuBinary(): String? {
        cachedRootPath?.let { path ->
            if (path == "su" || File(path).exists()) return path
        }
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/debug_ramdisk/su"
        )
        candidates.firstOrNull { File(it).exists() }?.let { return it }

        val pathDirs = System.getenv("PATH").orEmpty().split(':')
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            val f = File(dir, "su")
            if (f.exists()) return f.absolutePath
        }
        return null
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val MAX_OUTPUT_CHARS = 24000

        fun resolveActiveBackend(
            preferred: PrivilegeBackend,
            rootAvailable: Boolean,
            shizukuReady: Boolean
        ): PrivilegeBackend = when (preferred) {
            PrivilegeBackend.ROOT -> if (rootAvailable) PrivilegeBackend.ROOT else PrivilegeBackend.NONE
            PrivilegeBackend.SHIZUKU -> if (shizukuReady) PrivilegeBackend.SHIZUKU else PrivilegeBackend.NONE
            PrivilegeBackend.AUTO -> when {
                rootAvailable -> PrivilegeBackend.ROOT
                shizukuReady -> PrivilegeBackend.SHIZUKU
                else -> PrivilegeBackend.NONE
            }
            PrivilegeBackend.NONE -> PrivilegeBackend.NONE
        }
    }
}
