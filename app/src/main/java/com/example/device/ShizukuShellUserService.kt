package com.example.device

import android.os.Process
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Bound inside the Shizuku privileged process (UID 0 if rooted or UID 2000 ADB shell).
 */
class ShizukuShellUserService : IDeviceShellService.Stub() {

    override fun getUid(): Int = Process.myUid()

    override fun execCommand(command: String?, timeoutSeconds: Int): String {
        val raw = command?.trim().orEmpty()
        if (raw.isEmpty()) return "EXIT=0\n"

        val shellBin = when {
            File("/system/bin/sh").exists() -> "/system/bin/sh"
            File("/bin/sh").exists() -> "/bin/sh"
            else -> "sh"
        }

        val clampedTimeout = timeoutSeconds.coerceIn(2, 120)
        return try {
            val process = ProcessBuilder(shellBin, "-c", raw)
                .redirectErrorStream(true)
                .start()

            val outputBuffer = StringBuilder()
            val readerThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (outputBuffer.length < MAX_BINDER_OUTPUT_CHARS) {
                                outputBuffer.appendLine(line)
                            }
                        }
                    }
                }
            }.apply { start() }

            val finished = process.waitFor(clampedTimeout.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                runCatching { process.destroyForcibly() }
                readerThread.join(500)
                val partial = outputBuffer.toString().trim().take(MAX_BINDER_OUTPUT_CHARS)
                return "EXIT=124\nCommand timed out after ${clampedTimeout}s.\n$partial".trim()
            }

            readerThread.join(1000)
            val exitCode = process.exitValue()
            val body = outputBuffer.toString().trim().take(MAX_BINDER_OUTPUT_CHARS)
            "EXIT=$exitCode\n$body"
        } catch (t: Throwable) {
            "EXIT=1\nShizuku exec error: ${t.message ?: t::class.simpleName}"
        }
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        private const val MAX_BINDER_OUTPUT_CHARS = 24000
    }
}
