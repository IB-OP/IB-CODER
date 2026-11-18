package com.example.data.terminal

import android.content.Context
import com.example.data.workspace.WorkspaceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

enum class TerminalLineKind {
    COMMAND,
    STDOUT,
    STDERR,
    SYSTEM,
    SUCCESS
}

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val kind: TerminalLineKind = TerminalLineKind.STDOUT,
    val timestamp: Long = System.currentTimeMillis()
)

data class TerminalSessionState(
    val cwdRelative: String = ".",
    val isRunning: Boolean = false,
    val activeCommand: String? = null,
    val lines: List<TerminalLine> = listOf(
        TerminalLine(
            text = "IB_CODER CYBER-SHELL v2.0 // Android /system/bin/sh + ibpkg (npm/react, pip, gradle wrapper)",
            kind = TerminalLineKind.SYSTEM
        ),
        TerminalLine(
            text = "Type 'help' for built-in installers or run any Linux/Android shell command.",
            kind = TerminalLineKind.SYSTEM
        )
    )
)

data class TerminalCommandResult(
    val exitCode: Int,
    val output: String,
    val mutatesWorkspace: Boolean = false
)

class TerminalExecutor(
    context: Context,
    private val workspaceManager: WorkspaceManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val npmRegistryBaseUrl: String = "https://registry.npmjs.org",
    private val pypiBaseUrl: String = "https://pypi.org",
    private val gradleDistBaseUrl: String = "https://services.gradle.org/distributions"
) {
    private val appContext = context.applicationContext
    private val toolchainsRoot: File = File(appContext.filesDir, "toolchains").apply { mkdirs() }
    private val terminalHomeRoot: File = File(appContext.filesDir, "terminal_home").apply { mkdirs() }

    private val _sessionState = MutableStateFlow(TerminalSessionState())
    val sessionState: StateFlow<TerminalSessionState> = _sessionState.asStateFlow()

    @Volatile
    private var activeProcess: Process? = null

    private var relativeSubdir: String = "."

    fun clearOutput() {
        _sessionState.update {
            it.copy(
                lines = listOf(
                    TerminalLine(
                        text = "Terminal buffer cleared.",
                        kind = TerminalLineKind.SYSTEM
                    )
                )
            )
        }
    }

    fun stopCurrentProcess() {
        activeProcess?.let { proc ->
            runCatching { proc.destroyForcibly() }
            appendLine("[Interrupted by user]", TerminalLineKind.STDERR)
        }
        activeProcess = null
        _sessionState.update { it.copy(isRunning = false, activeCommand = null) }
    }

    private fun appendLine(text: String, kind: TerminalLineKind) {
        _sessionState.update { state ->
            val updated = (state.lines + TerminalLine(text = text, kind = kind)).takeLast(MAX_HISTORY_LINES)
            state.copy(lines = updated)
        }
    }

    suspend fun execute(
        command: String,
        projectId: String?,
        timeoutSeconds: Int = 90
    ): TerminalCommandResult = withContext(Dispatchers.IO) {
        val raw = command.trim()
        if (raw.isEmpty()) {
            return@withContext TerminalCommandResult(0, "")
        }

        val blockedReason = checkBlockedCommand(raw)
        if (blockedReason != null) {
            appendLine("$ $raw", TerminalLineKind.COMMAND)
            appendLine(blockedReason, TerminalLineKind.STDERR)
            return@withContext TerminalCommandResult(1, blockedReason)
        }

        val workDirLabel = resolveWorkingLabel(projectId)
        appendLine("[$workDirLabel]$ $raw", TerminalLineKind.COMMAND)
        _sessionState.update { it.copy(isRunning = true, activeCommand = raw) }

        try {
            val result = dispatchCommand(raw, projectId, timeoutSeconds.coerceIn(5, 300))
            return@withContext result
        } catch (ce: CancellationException) {
            activeProcess?.let { runCatching { it.destroyForcibly() } }
            appendLine("Command cancelled.", TerminalLineKind.STDERR)
            throw ce
        } catch (e: Exception) {
            val msg = "Terminal error: ${e.message ?: e::class.simpleName}"
            appendLine(msg, TerminalLineKind.STDERR)
            return@withContext TerminalCommandResult(1, msg)
        } finally {
            activeProcess = null
            _sessionState.update {
                it.copy(isRunning = false, activeCommand = null, cwdRelative = relativeSubdir)
            }
        }
    }

    private suspend fun dispatchCommand(
        raw: String,
        projectId: String?,
        timeoutSeconds: Int
    ): TerminalCommandResult {
        val tokens = tokenizeCommand(raw)
        if (tokens.isEmpty()) return TerminalCommandResult(0, "")
        val head = tokens.first()

        return when {
            head == "clear" -> {
                clearOutput()
                TerminalCommandResult(0, "Cleared terminal output.")
            }

            head == "help" || (head == "ibpkg" && tokens.getOrNull(1) == "help") -> {
                val msg = buildHelpText()
                msg.lines().forEach { appendLine(it, TerminalLineKind.SYSTEM) }
                TerminalCommandResult(0, msg)
            }

            head == "cd" -> {
                val target = tokens.getOrNull(1) ?: "."
                handleCd(target, projectId)
            }

            head == "ibpkg" -> {
                handleIbPkg(tokens.drop(1), projectId)
            }

            head == "npm" && tokens.getOrNull(1) in setOf("install", "i", "add") -> {
                val pkgs = tokens.drop(2).filterNot { it.startsWith("-") }
                installNpmPackages(pkgs, projectId)
            }

            head == "npx" && tokens.getOrNull(1)?.contains("create-vite") == true -> {
                val appName = tokens.drop(2).firstOrNull { !it.startsWith("-") } ?: "react-app"
                scaffoldReactViteProject(appName, projectId)
            }

            head == "npx" && tokens.getOrNull(1)?.contains("create-react-app") == true -> {
                val appName = tokens.drop(2).firstOrNull { !it.startsWith("-") } ?: "react-app"
                scaffoldReactViteProject(appName, projectId)
            }

            (head == "pip" || head == "pip3") && tokens.getOrNull(1) == "install" -> {
                val pkgs = tokens.drop(2).filterNot { it.startsWith("-") }
                installPyPiPackages(pkgs, projectId)
            }

            (head == "gradle" || head == "./gradlew" || head == "gradlew") &&
                tokens.getOrNull(1) == "wrapper" -> {
                val version = tokens.dropWhile { it != "--gradle-version" && it != "--version" }
                    .getOrNull(1)
                    ?: DEFAULT_GRADLE_VERSION
                installGradleToolchain(version = version, writeWrapperToWorkspace = true, projectId = projectId)
            }

            head == "gradle" || head == "./gradlew" || head == "gradlew" -> {
                handleGradleInvocation(tokens.drop(1), projectId, timeoutSeconds)
            }

            else -> {
                runSystemShell(raw, projectId, timeoutSeconds)
            }
        }
    }

    private suspend fun handleCd(targetArg: String, projectId: String?): TerminalCommandResult {
        val baseDir = resolveLocalBaseDir(projectId)
        val current = if (relativeSubdir == ".") baseDir else File(baseDir, relativeSubdir)
        val next = if (targetArg == "." || targetArg == "~" || targetArg == "/") {
            baseDir
        } else {
            File(current, targetArg).canonicalFile
        }
        val baseCanonical = baseDir.canonicalPath
        val nextCanonical = next.canonicalPath
        if (nextCanonical != baseCanonical && !nextCanonical.startsWith(baseCanonical + File.separator)) {
            val err = "cd: permission denied outside workspace root."
            appendLine(err, TerminalLineKind.STDERR)
            return TerminalCommandResult(1, err)
        }
        if (!next.exists() || !next.isDirectory) {
            val err = "cd: $targetArg: No such directory"
            appendLine(err, TerminalLineKind.STDERR)
            return TerminalCommandResult(1, err)
        }
        relativeSubdir = if (nextCanonical == baseCanonical) "." else {
            next.relativeTo(baseDir.canonicalFile).path.replace('\\', '/')
        }
        val msg = "Changed directory to $relativeSubdir"
        appendLine(msg, TerminalLineKind.SYSTEM)
        return TerminalCommandResult(0, msg)
    }

    private suspend fun handleIbPkg(args: List<String>, projectId: String?): TerminalCommandResult {
        if (args.isEmpty()) {
            val msg = buildHelpText()
            msg.lines().forEach { appendLine(it, TerminalLineKind.SYSTEM) }
            return TerminalCommandResult(0, msg)
        }
        return when (args[0].lowercase()) {
            "list", "ls" -> {
                val sb = StringBuilder()
                val toolDirs = toolchainsRoot.listFiles()?.filter { it.isDirectory }.orEmpty()
                sb.appendLine("Installed Toolchains (${toolchainsRoot.absolutePath}):")
                if (toolDirs.isEmpty()) {
                    sb.appendLine("  (none yet — run: ibpkg install gradle)")
                } else {
                    toolDirs.forEach { sb.appendLine("  - ${it.name}") }
                }
                val baseDir = resolveLocalBaseDir(projectId)
                val nodeMods = File(baseDir, "node_modules")
                if (nodeMods.exists() && nodeMods.isDirectory) {
                    val pkgs = nodeMods.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.map { it.name }
                        .orEmpty()
                    sb.appendLine("Workspace node_modules (${pkgs.size}): ${pkgs.joinToString(", ").ifBlank { "(empty)" }}")
                }
                val venvLib = File(baseDir, ".venv/lib")
                if (venvLib.exists()) {
                    val pyPkgs = venvLib.listFiles()?.map { it.name }.orEmpty()
                    sb.appendLine("Workspace .venv packages (${pyPkgs.size}): ${pyPkgs.joinToString(", ")}")
                }
                val text = sb.toString().trimEnd()
                text.lines().forEach { appendLine(it, TerminalLineKind.STDOUT) }
                TerminalCommandResult(0, text)
            }

            "install", "add" -> {
                val rest = args.drop(1)
                if (rest.isEmpty()) {
                    val err = "Usage: ibpkg install <react|gradle|npm:pkg|pip:pkg> [version]"
                    appendLine(err, TerminalLineKind.STDERR)
                    return TerminalCommandResult(1, err)
                }
                val first = rest[0].lowercase()
                when {
                    first == "gradle" -> {
                        val ver = rest.getOrNull(1) ?: DEFAULT_GRADLE_VERSION
                        installGradleToolchain(version = ver, writeWrapperToWorkspace = true, projectId = projectId)
                    }
                    first == "react" || first == "react-app" -> {
                        scaffoldReactViteProject(rest.getOrNull(1) ?: ".", projectId)
                    }
                    first.startsWith("pip:") -> {
                        installPyPiPackages(listOf(first.removePrefix("pip:")) + rest.drop(1), projectId)
                    }
                    else -> {
                        val cleaned = rest.map { it.removePrefix("npm:") }
                        installNpmPackages(cleaned, projectId)
                    }
                }
            }

            else -> {
                val err = "Unknown ibpkg subcommand '${args[0]}'. Try 'ibpkg help'."
                appendLine(err, TerminalLineKind.STDERR)
                TerminalCommandResult(1, err)
            }
        }
    }

    private suspend fun installNpmPackages(
        requestedPackages: List<String>,
        projectId: String?
    ): TerminalCommandResult {
        val packagesToInstall = if (requestedPackages.isNotEmpty()) {
            requestedPackages
        } else {
            val baseDir = resolveLocalBaseDir(projectId)
            val pkgJsonFile = File(baseDir, "package.json")
            if (pkgJsonFile.exists()) {
                parseDepsFromPackageJson(pkgJsonFile.readText())
            } else {
                listOf("react", "react-dom")
            }
        }

        if (packagesToInstall.isEmpty()) {
            val msg = "package.json has no dependencies listed."
            appendLine(msg, TerminalLineKind.SYSTEM)
            return TerminalCommandResult(0, msg)
        }

        val out = StringBuilder()
        val installed = linkedMapOf<String, String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()

        packagesToInstall.forEach { rawSpec ->
            val (pkgName, _) = splitPackageSpec(rawSpec)
            queue.addLast(pkgName to 0)
        }

        val proj = projectId?.let { workspaceManager.getProject(it) }
        val useWorkspaceWrite = proj != null && proj.isDeviceFolder
        val localRoot = resolveLocalBaseDir(projectId)

        var fileCount = 0
        while (queue.isNotEmpty()) {
            val (pkgName, depth) = queue.removeFirst()
            if (!visited.add(pkgName)) continue

            appendLine("npm GET $npmRegistryBaseUrl/$pkgName", TerminalLineKind.SYSTEM)
            val meta = fetchNpmMetadata(pkgName)
            if (meta == null) {
                val warn = "Warning: Could not resolve npm package '$pkgName' from $npmRegistryBaseUrl"
                appendLine(warn, TerminalLineKind.STDERR)
                out.appendLine(warn)
                continue
            }

            appendLine("Downloading ${meta.name}@${meta.version} ...", TerminalLineKind.STDOUT)
            val tgzBytes = downloadBytes(meta.tarballUrl)
            if (tgzBytes == null) {
                val warn = "Failed downloading tarball: ${meta.tarballUrl}"
                appendLine(warn, TerminalLineKind.STDERR)
                out.appendLine(warn)
                continue
            }

            val pkgRelBase = "node_modules/${meta.name}"
            val pendingSafWrites = if (useWorkspaceWrite) mutableListOf<Pair<String, String>>() else null
            val extracted = extractTarGz(tgzBytes) { entryName, bytes ->
                // npm tarballs wrap contents inside "package/..."
                val trimmed = entryName.removePrefix("./").removePrefix("package/").trimStart('/')
                if (trimmed.isEmpty()) return@extractTarGz
                val relPath = "$pkgRelBase/$trimmed"
                if (pendingSafWrites != null) {
                    val isText = !trimmed.endsWith(".png") && !trimmed.endsWith(".wasm")
                    if (isText) {
                        pendingSafWrites += relPath to bytes.toString(Charsets.UTF_8)
                    }
                } else {
                    val dest = File(localRoot, relPath)
                    val rootCanonical = localRoot.canonicalPath
                    if (dest.canonicalPath.startsWith(rootCanonical + File.separator)) {
                        dest.parentFile?.mkdirs()
                        dest.writeBytes(bytes)
                    }
                }
            }
            if (pendingSafWrites != null && projectId != null) {
                for ((relPath, content) in pendingSafWrites) {
                    runCatching { workspaceManager.writeFile(projectId, relPath, content) }
                }
            }

            fileCount += extracted
            installed[meta.name] = "^${meta.version}"
            appendLine("+ ${meta.name}@${meta.version} ($extracted files extracted)", TerminalLineKind.SUCCESS)
            out.appendLine("+ ${meta.name}@${meta.version} ($extracted files)")

            // Follow direct runtime dependencies up to depth 2 so react brings scheduler/loose-envify/js-tokens cleanly
            if (depth < 2) {
                meta.dependencies.keys.take(10).forEach { depName ->
                    if (depName !in visited) queue.addLast(depName to (depth + 1))
                }
            }
        }

        if (installed.isEmpty()) {
            val err = "No npm packages were installed. Check package names or network access."
            appendLine(err, TerminalLineKind.STDERR)
            return TerminalCommandResult(1, out.toString() + "\n" + err)
        }

        // Update or create package.json in workspace
        updatePackageJson(localRoot, projectId, useWorkspaceWrite, installed)
        val summary = "Installed ${installed.size} npm package(s) ($fileCount total files) into node_modules/ and updated package.json."
        appendLine(summary, TerminalLineKind.SUCCESS)
        out.appendLine(summary)
        return TerminalCommandResult(0, out.toString().trim(), mutatesWorkspace = true)
    }

    private suspend fun scaffoldReactViteProject(
        targetName: String,
        projectId: String?
    ): TerminalCommandResult {
        val cleanPrefix = if (targetName == "." || targetName.isBlank()) "" else "$targetName/"
        val starterFiles = mapOf(
            "${cleanPrefix}index.html" to """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>React Cyber App</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.jsx"></script>
                  </body>
                </html>
            """.trimIndent(),
            "${cleanPrefix}src/main.jsx" to """
                import React from 'react';
                import { createRoot } from 'react-dom/client';
                import App from './App.jsx';
                import './index.css';

                createRoot(document.getElementById('root')).render(
                  <React.StrictMode>
                    <App />
                  </React.StrictMode>
                );
            """.trimIndent(),
            "${cleanPrefix}src/App.jsx" to """
                import React, { useState } from 'react';

                export default function App() {
                  const [count, setCount] = useState(0);
                  return (
                    <main className="app-shell">
                      <h1>IB CODER // React Starter</h1>
                      <p>Edit <code>src/App.jsx</code> or ask IB_AI to build your components.</p>
                      <button onClick={() => setCount((c) => c + 1)}>
                        Counter: {count}
                      </button>
                    </main>
                  );
                }
            """.trimIndent(),
            "${cleanPrefix}src/index.css" to """
                :root {
                  font-family: system-ui, -apple-system, sans-serif;
                  background: #060910;
                  color: #eaf4ff;
                }
                .app-shell {
                  max-width: 720px;
                  margin: 3rem auto;
                  padding: 1.5rem;
                  border: 1px solid #22e4ff;
                  border-radius: 6px;
                }
                button {
                  background: #22e4ff;
                  color: #031921;
                  border: none;
                  padding: 0.6rem 1.2rem;
                  font-weight: 700;
                  cursor: pointer;
                }
            """.trimIndent()
        )

        val localRoot = resolveLocalBaseDir(projectId)
        val proj = projectId?.let { workspaceManager.getProject(it) }
        val useWorkspaceWrite = proj != null && proj.isDeviceFolder

        starterFiles.forEach { (rel, content) ->
            if (useWorkspaceWrite) {
                workspaceManager.writeFile(projectId!!, rel, content)
            } else {
                val f = File(localRoot, rel)
                f.parentFile?.mkdirs()
                f.writeText(content, Charsets.UTF_8)
            }
            appendLine("created $rel", TerminalLineKind.STDOUT)
        }

        val installRes = installNpmPackages(listOf("react", "react-dom"), projectId)
        return installRes.copy(mutatesWorkspace = true)
    }

    private suspend fun installPyPiPackages(
        packages: List<String>,
        projectId: String?
    ): TerminalCommandResult {
        if (packages.isEmpty()) {
            val err = "Usage: pip install <package_name>"
            appendLine(err, TerminalLineKind.STDERR)
            return TerminalCommandResult(1, err)
        }

        val out = StringBuilder()
        val localRoot = resolveLocalBaseDir(projectId)
        val proj = projectId?.let { workspaceManager.getProject(it) }
        val useWorkspaceWrite = proj != null && proj.isDeviceFolder

        var installedCount = 0
        for (rawPkg in packages) {
            val pkg = rawPkg.substringBefore("==").substringBefore(">=").trim()
            appendLine("pip GET $pypiBaseUrl/pypi/$pkg/json", TerminalLineKind.SYSTEM)
            val jsonText = fetchText("$pypiBaseUrl/pypi/${URLEncoder.encode(pkg, "UTF-8")}/json")
            if (jsonText == null) {
                val warn = "ERROR: Could not find package '$pkg' on PyPI."
                appendLine(warn, TerminalLineKind.STDERR)
                out.appendLine(warn)
                continue
            }

            val whlInfo = runCatching {
                val root = json.parseToJsonElement(jsonText).jsonObject
                val version = root["info"]?.jsonObject?.get("version")?.jsonPrimitive?.content ?: "latest"
                val urls = (root["urls"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                val whlObj = urls.mapNotNull { it as? JsonObject }.firstOrNull { obj ->
                    val fn = obj["filename"]?.jsonPrimitive?.content.orEmpty()
                    fn.endsWith("none-any.whl")
                } ?: urls.mapNotNull { it as? JsonObject }.firstOrNull { obj ->
                    obj["filename"]?.jsonPrimitive?.content.orEmpty().endsWith(".whl")
                }
                val whlUrl = whlObj?.get("url")?.jsonPrimitive?.content
                version to whlUrl
            }.getOrNull()

            if (whlInfo?.second == null) {
                val warn = "Warning: No universal pure-Python wheel (.whl) available for '$pkg'."
                appendLine(warn, TerminalLineKind.STDERR)
                out.appendLine(warn)
                continue
            }

            val whlBytes = downloadBytes(whlInfo.second!!)
            if (whlBytes == null) {
                val warn = "Failed downloading wheel for $pkg."
                appendLine(warn, TerminalLineKind.STDERR)
                out.appendLine(warn)
                continue
            }

            var extracted = 0
            ZipInputStream(ByteArrayInputStream(whlBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/').trimStart('/')
                    if (!entry.isDirectory && name.isNotEmpty() && !name.contains("..")) {
                        val rel = ".venv/lib/$name"
                        val bytes = zis.readBytes()
                        if (useWorkspaceWrite) {
                            if (name.endsWith(".py") || name.endsWith(".txt") || name.endsWith(".json")) {
                                workspaceManager.writeFile(projectId!!, rel, bytes.toString(Charsets.UTF_8))
                            }
                        } else {
                            val dest = File(localRoot, rel)
                            if (dest.canonicalPath.startsWith(localRoot.canonicalPath + File.separator)) {
                                dest.parentFile?.mkdirs()
                                dest.writeBytes(bytes)
                            }
                        }
                        extracted++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            installedCount++
            val msg = "Successfully installed $pkg-${whlInfo.first} ($extracted files into .venv/lib/)"
            appendLine(msg, TerminalLineKind.SUCCESS)
            out.appendLine(msg)
        }

        return TerminalCommandResult(
            exitCode = if (installedCount > 0) 0 else 1,
            output = out.toString().trim(),
            mutatesWorkspace = installedCount > 0
        )
    }

    private suspend fun installGradleToolchain(
        version: String,
        writeWrapperToWorkspace: Boolean,
        projectId: String?
    ): TerminalCommandResult {
        val cleanVersion = version.trim().ifBlank { DEFAULT_GRADLE_VERSION }
            .replace(Regex("[^0-9A-Za-z._-]"), "")
        val distUrl = "$gradleDistBaseUrl/gradle-$cleanVersion-bin.zip"
        val installDir = File(toolchainsRoot, "gradle-$cleanVersion")

        val out = StringBuilder()
        if (!installDir.exists() || installDir.listFiles().isNullOrEmpty()) {
            appendLine("Downloading Gradle $cleanVersion distribution from $distUrl ...", TerminalLineKind.SYSTEM)
            val bytes = downloadBytes(distUrl)
            if (bytes != null && bytes.size > 1024) {
                installDir.mkdirs()
                var fileCount = 0
                val rootCanon = installDir.canonicalPath
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val stripped = entry.name.replace('\\', '/').trimStart('/')
                            .substringAfter('/', missingDelimiterValue = "")
                        if (stripped.isNotEmpty() && !stripped.contains("..")) {
                            val target = File(installDir, stripped)
                            if (target.canonicalPath.startsWith(rootCanon + File.separator)) {
                                if (entry.isDirectory) {
                                    target.mkdirs()
                                } else {
                                    target.parentFile?.mkdirs()
                                    target.outputStream().use { zis.copyTo(it) }
                                    fileCount++
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                val binGradle = File(installDir, "bin/gradle")
                if (binGradle.exists()) binGradle.setExecutable(true)
                val msg = "Installed Gradle $cleanVersion ($fileCount files) -> ${installDir.absolutePath}"
                appendLine(msg, TerminalLineKind.SUCCESS)
                out.appendLine(msg)
            } else {
                val warn = "Note: Could not download full Gradle zip ($distUrl). Configuring lightweight Gradle Wrapper files in workspace instead."
                appendLine(warn, TerminalLineKind.SYSTEM)
                out.appendLine(warn)
            }
        } else {
            val msg = "Gradle $cleanVersion already present in toolchains cache."
            appendLine(msg, TerminalLineKind.SYSTEM)
            out.appendLine(msg)
        }

        var mutated = false
        if (writeWrapperToWorkspace) {
            val wrapperProps = """
                distributionBase=GRADLE_USER_HOME
                distributionPath=wrapper/dists
                distributionUrl=https\://services.gradle.org/distributions/gradle-$cleanVersion-bin.zip
                networkTimeout=10000
                validateDistributionUrl=true
                zipStoreBase=GRADLE_USER_HOME
                zipStorePath=wrapper/dists
            """.trimIndent() + "\n"

            val gradlewScript = """
                #!/bin/sh
                # Gradle wrapper generated by IB CODER
                APP_HOME=${'$'}(cd "${'$'}(dirname "${'$'}0")" && pwd -P)
                exec gradle "${'$'}@"
            """.trimIndent() + "\n"

            val localRoot = resolveLocalBaseDir(projectId)
            val proj = projectId?.let { workspaceManager.getProject(it) }
            val useWorkspaceWrite = proj != null && proj.isDeviceFolder

            if (useWorkspaceWrite) {
                workspaceManager.writeFile(projectId!!, "gradle/wrapper/gradle-wrapper.properties", wrapperProps)
                workspaceManager.writeFile(projectId, "gradlew", gradlewScript)
            } else {
                val propsFile = File(localRoot, "gradle/wrapper/gradle-wrapper.properties")
                propsFile.parentFile?.mkdirs()
                propsFile.writeText(wrapperProps, Charsets.UTF_8)
                val gradlewFile = File(localRoot, "gradlew")
                gradlewFile.writeText(gradlewScript, Charsets.UTF_8)
                gradlewFile.setExecutable(true)
            }
            mutated = true
            val wrapMsg = "Configured gradlew & gradle/wrapper/gradle-wrapper.properties (Gradle $cleanVersion)."
            appendLine(wrapMsg, TerminalLineKind.SUCCESS)
            out.appendLine(wrapMsg)
        }

        return TerminalCommandResult(0, out.toString().trim(), mutatesWorkspace = mutated)
    }

    private suspend fun handleGradleInvocation(
        args: List<String>,
        projectId: String?,
        timeoutSeconds: Int
    ): TerminalCommandResult {
        val javaBin = locateJavaBinary()
        val gradleBin = findInstalledGradleBinary()

        if (javaBin != null && gradleBin != null) {
            appendLine("Detected JVM ($javaBin) + Gradle (${gradleBin.name}). Running Gradle...", TerminalLineKind.SYSTEM)
            val cmdLine = "\"${gradleBin.absolutePath}\" ${args.joinToString(" ")}"
            return runSystemShell(cmdLine, projectId, timeoutSeconds, extraPathDirs = listOfNotNull(javaBin.parent, gradleBin.parent))
        }

        // Honest Android constraint check & workspace Gradle diagnostics
        val localRoot = resolveLocalBaseDir(projectId)
        val hasBuildGradle = File(localRoot, "build.gradle.kts").exists() ||
            File(localRoot, "build.gradle").exists() ||
            File(localRoot, "app/build.gradle.kts").exists()
        val hasWrapperProps = File(localRoot, "gradle/wrapper/gradle-wrapper.properties").exists()

        val sb = StringBuilder()
        sb.appendLine("Gradle JDK check:")
        sb.appendLine("  • Installed Gradle distribution: ${gradleBin?.parentFile?.parentFile?.name ?: "none (run 'ibpkg install gradle')"}")
        sb.appendLine("  • On-device JVM (java): ${javaBin?.absolutePath ?: "not found in Android runtime PATH"}")
        sb.appendLine("  • Workspace Gradle build files: ${if (hasBuildGradle) "detected" else "not found"}")
        sb.appendLine("  • Gradle Wrapper properties: ${if (hasWrapperProps) "detected" else "not configured (run 'gradle wrapper')"}")
        sb.appendLine()
        sb.appendLine("Why full JDK compilation isn't bundled: Android apps cannot ship a full desktop JDK inside the APK. Use GitHub Hub → Actions ('Build Android Debug APK') to compile APKs/AABs cleanly in CI, or install java in Termux if using a rooted/dev device.")
        val msg = sb.toString().trim()
        msg.lines().forEach { appendLine(it, TerminalLineKind.SYSTEM) }
        return TerminalCommandResult(if (javaBin == null) 1 else 0, msg)
    }

    private suspend fun runSystemShell(
        command: String,
        projectId: String?,
        timeoutSeconds: Int,
        extraPathDirs: List<String> = emptyList()
    ): TerminalCommandResult {
        val baseDir = resolveLocalBaseDir(projectId)
        val cwd = if (relativeSubdir == ".") baseDir else File(baseDir, relativeSubdir).takeIf { it.isDirectory } ?: baseDir

        val shellBin = resolveShellExecutable()
        val pb = ProcessBuilder(shellBin, "-c", command)
        pb.directory(cwd)
        val env = pb.environment()
        val toolchainBins = toolchainsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> File(dir, "bin").takeIf { it.isDirectory }?.absolutePath }
            .orEmpty()
        val extraPaths = (extraPathDirs + toolchainBins + listOf(
            "/data/data/com.termux/files/usr/bin",
            "${cwd.absolutePath}/node_modules/.bin"
        )).filter { File(it).exists() }
        val existingPath = env["PATH"] ?: "/system/bin:/system/xbin:/vendor/bin"
        if (extraPaths.isNotEmpty()) {
            env["PATH"] = (extraPaths + existingPath).distinct().joinToString(":")
        }
        env["HOME"] = terminalHomeRoot.absolutePath
        env["WORKSPACE_DIR"] = baseDir.absolutePath

        val process = try {
            pb.start()
        } catch (t: Throwable) {
            val errMsg = "Failed starting shell ($shellBin): ${t.message}"
            appendLine(errMsg, TerminalLineKind.STDERR)
            return TerminalCommandResult(127, errMsg)
        }
        activeProcess = process

        val combined = StringBuilder()
        val finishedInTime = withTimeoutOrNull(timeoutSeconds * 1000L) {
            val stdoutThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(combined) { combined.appendLine(line) }
                            appendLine(line, TerminalLineKind.STDOUT)
                        }
                    }
                }
            }.apply { start() }

            val stderrThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(combined) { combined.appendLine(line) }
                            appendLine(line, TerminalLineKind.STDERR)
                        }
                    }
                }
            }.apply { start() }

            val exitCode = process.waitFor()
            stdoutThread.join(1500)
            stderrThread.join(1500)
            exitCode
        }

        if (finishedInTime == null) {
            runCatching { process.destroyForcibly() }
            val timeoutMsg = "Command timed out after ${timeoutSeconds}s."
            appendLine(timeoutMsg, TerminalLineKind.STDERR)
            synchronized(combined) { combined.appendLine(timeoutMsg) }
            return TerminalCommandResult(124, combined.toString().trim().take(MAX_TOOL_OUTPUT_CHARS))
        }

        return TerminalCommandResult(
            exitCode = finishedInTime,
            output = combined.toString().trim().ifEmpty { "(exit code $finishedInTime)" }.take(MAX_TOOL_OUTPUT_CHARS)
        )
    }

    private suspend fun resolveLocalBaseDir(projectId: String?): File {
        if (!projectId.isNullOrBlank()) {
            val proj = workspaceManager.getProject(projectId)
            if (proj != null && !proj.isDeviceFolder) {
                val f = File(proj.rootPath)
                if (!f.exists()) f.mkdirs()
                return f
            }
        }
        return terminalHomeRoot
    }

    private fun resolveShellExecutable(): String = when {
        File("/system/bin/sh").exists() -> "/system/bin/sh"
        File("/bin/sh").exists() -> "/bin/sh"
        else -> "sh"
    }

    private suspend fun resolveWorkingLabel(projectId: String?): String {
        val projName = projectId?.let { workspaceManager.getProject(it)?.name } ?: "shell"
        return if (relativeSubdir == ".") projName else "$projName/$relativeSubdir"
    }

    private fun locateJavaBinary(): File? {
        val candidates = listOf(
            "/data/data/com.termux/files/usr/bin/java",
            "/system/bin/java"
        )
        candidates.forEach { path ->
            val f = File(path)
            if (f.exists() && f.canExecute()) return f
        }
        val pathEnv = System.getenv("PATH").orEmpty().split(':')
        for (dir in pathEnv) {
            if (dir.isBlank()) continue
            val f = File(dir, "java")
            if (f.exists() && f.canExecute()) return f
        }
        return null
    }

    private fun findInstalledGradleBinary(): File? {
        return toolchainsRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("gradle-") }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "bin/gradle") }
            ?.firstOrNull { it.exists() }
    }

    private fun fetchNpmMetadata(packageName: String): NpmPackageMeta? {
        val encoded = if (packageName.startsWith("@") && packageName.contains('/')) {
            val parts = packageName.split('/', limit = 2)
            "${parts[0]}%2F${URLEncoder.encode(parts[1], "UTF-8")}"
        } else {
            URLEncoder.encode(packageName, "UTF-8")
        }
        val body = fetchText("$npmRegistryBaseUrl/$encoded") ?: return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val name = root["name"]?.jsonPrimitive?.content ?: packageName
            val latestTag = root["dist-tags"]?.jsonObject?.get("latest")?.jsonPrimitive?.content
            val versionsObj = root["versions"]?.jsonObject
            val versionObj = if (latestTag != null && versionsObj != null) {
                versionsObj[latestTag]?.jsonObject
            } else {
                versionsObj?.values?.lastOrNull()?.jsonObject
            }
            val version = versionObj?.get("version")?.jsonPrimitive?.content ?: latestTag ?: "0.0.0"
            val tarball = versionObj?.get("dist")?.jsonObject?.get("tarball")?.jsonPrimitive?.content
                ?: return@runCatching null
            val deps = versionObj["dependencies"]?.jsonObject?.entries?.associate { (k, v) ->
                k to (runCatching { v.jsonPrimitive.content }.getOrDefault("*"))
            }.orEmpty()
            NpmPackageMeta(name, version, tarball, deps)
        }.getOrNull()
    }

    private fun fetchText(url: String): String? {
        return runCatching {
            val client = okHttpClient.newBuilder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    private fun downloadBytes(url: String): ByteArray? {
        return runCatching {
            val client = okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        }.getOrNull()
    }

    private suspend fun updatePackageJson(
        localRoot: File,
        projectId: String?,
        useWorkspaceWrite: Boolean,
        newlyInstalled: Map<String, String>
    ) {
        val pkgFile = File(localRoot, "package.json")
        val existingObj = if (!useWorkspaceWrite && pkgFile.exists()) {
            runCatching { json.parseToJsonElement(pkgFile.readText()).jsonObject }.getOrNull()
        } else if (useWorkspaceWrite && projectId != null) {
            runCatching {
                json.parseToJsonElement(workspaceManager.readRawFile(projectId, "package.json")).jsonObject
            }.getOrNull()
        } else {
            null
        }

        val existingDeps = existingObj?.get("dependencies")?.jsonObject?.entries?.associate { (k, v) ->
            k to runCatching { v.jsonPrimitive.content }.getOrDefault("*")
        }.orEmpty()
        val mergedDeps = existingDeps + newlyInstalled

        val updatedJson = buildJsonObject {
            put("name", existingObj?.get("name")?.jsonPrimitive?.content ?: "ib-coder-project")
            put("version", existingObj?.get("version")?.jsonPrimitive?.content ?: "0.1.0")
            put("private", true)
            put("type", "module")
            putJsonObject("scripts") {
                put("dev", "vite")
                put("build", "vite build")
            }
            putJsonObject("dependencies") {
                mergedDeps.forEach { (k, v) -> put(k, v) }
            }
        }.toString()

        if (useWorkspaceWrite && projectId != null) {
            workspaceManager.writeFile(projectId, "package.json", updatedJson)
        } else {
            pkgFile.parentFile?.mkdirs()
            pkgFile.writeText(updatedJson, Charsets.UTF_8)
        }
    }

    private fun parseDepsFromPackageJson(raw: String): List<String> {
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val deps = obj["dependencies"]?.jsonObject?.keys.orEmpty()
            val devDeps = obj["devDependencies"]?.jsonObject?.keys.orEmpty()
            (deps + devDeps).distinct()
        }.getOrDefault(emptyList())
    }

    private fun splitPackageSpec(spec: String): Pair<String, String?> {
        val trimmed = spec.trim()
        if (trimmed.startsWith("@")) {
            val secondAt = trimmed.indexOf('@', startIndex = 1)
            return if (secondAt > 0) {
                trimmed.substring(0, secondAt) to trimmed.substring(secondAt + 1)
            } else {
                trimmed to null
            }
        }
        val atIdx = trimmed.indexOf('@')
        return if (atIdx > 0) {
            trimmed.substring(0, atIdx) to trimmed.substring(atIdx + 1)
        } else {
            trimmed to null
        }
    }

    /**
     * Pure-Kotlin POSIX/USTAR tar.gz extractor (zero external dependency).
     */
    private fun extractTarGz(
        tgzBytes: ByteArray,
        onFile: (name: String, content: ByteArray) -> Unit
    ): Int {
        var count = 0
        GZIPInputStream(BufferedInputStream(ByteArrayInputStream(tgzBytes))).use { gzis ->
            val header = ByteArray(512)
            while (true) {
                if (!readFully(gzis, header)) break
                if (header.all { it == 0.toByte() }) break

                val rawName = readAscii(header, 0, 100)
                val prefix = readAscii(header, 345, 155)
                val fullName = if (prefix.isNotEmpty()) "$prefix/$rawName" else rawName
                val sizeStr = readAscii(header, 124, 12).trim()
                val fileSize = sizeStr.toLongOrNull(8) ?: 0L
                val typeFlag = header[156].toInt().toChar()

                val dataBytes = if (fileSize > 0L) {
                    val buf = ByteArray(fileSize.toInt())
                    if (!readFully(gzis, buf)) break
                    buf
                } else {
                    ByteArray(0)
                }

                // Skip 512-byte alignment padding
                val remainder = (fileSize % 512L).toInt()
                if (remainder > 0) {
                    val skipBuf = ByteArray(512 - remainder)
                    readFully(gzis, skipBuf)
                }

                val isRegularFile = typeFlag == '0' || typeFlag == '\u0000'
                if (isRegularFile && fullName.isNotBlank() && !fullName.contains("..")) {
                    onFile(fullName, dataBytes)
                    count++
                }
            }
        }
        return count
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) return offset == buffer.size
            offset += read
        }
        return true
    }

    private fun readAscii(buf: ByteArray, offset: Int, len: Int): String {
        val baos = ByteArrayOutputStream()
        for (i in offset until (offset + len).coerceAtMost(buf.size)) {
            val b = buf[i]
            if (b == 0.toByte()) break
            baos.write(b.toInt())
        }
        return baos.toString(Charsets.UTF_8.name()).trim()
    }

    private fun checkBlockedCommand(cmd: String): String? {
        val lower = cmd.lowercase()
        val blockedPatterns = listOf(
            Regex("""\brm\s+-[a-z]*[rf][a-z]*\s+/($|\s|\*)"""),
            Regex("""\bmkfs\b"""),
            Regex("""\bdd\s+.*\bof=/dev/"""),
            Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:""")
        )
        return if (blockedPatterns.any { it.containsMatchIn(lower) }) {
            "Blocked potentially destructive system command for device safety."
        } else {
            null
        }
    }

    private fun tokenizeCommand(input: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        for (ch in input) {
            when {
                quote != null -> {
                    if (ch == quote) quote = null else current.append(ch)
                }
                ch == '\'' || ch == '"' -> quote = ch
                ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        out += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private fun buildHelpText(): String = """
        IB_CODER Built-In Terminal & Toolchain Manager
        -----------------------------------------------
        • npm install <pkg...>    Install npm packages (e.g. 'npm install react react-dom') into node_modules/
        • npx create-vite [dir]   Scaffold a Vite + React project & install react + react-dom
        • pip install <pkg>       Install pure-Python wheels from PyPI into .venv/lib/
        • ibpkg install gradle    Download Gradle toolchain cache + write gradlew & gradle-wrapper.properties
        • gradle wrapper          Configure Gradle wrapper for the active workspace
        • ibpkg list              Show installed toolchains, node_modules, and Python packages
        • cd <dir> | clear        Change workspace subdirectory or clear terminal buffer
        • Any Linux shell cmd     Runs in /system/bin/sh inside the active workspace (ls, cat, grep, uname, etc.)
    """.trimIndent()

    private data class NpmPackageMeta(
        val name: String,
        val version: String,
        val tarballUrl: String,
        val dependencies: Map<String, String>
    )

    companion object {
        const val DEFAULT_GRADLE_VERSION = "8.11.1"
        private const val MAX_HISTORY_LINES = 1500
        private const val MAX_TOOL_OUTPUT_CHARS = 16000
    }
}
