package com.example.data.agent

import com.example.data.device.DeviceCommandSafety
import com.example.data.device.DeviceController
import com.example.data.device.UiAutomatorDumpParser
import com.example.data.network.InternetToolClient
import com.example.data.terminal.TerminalExecutor
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.PendingDeletionConfirmation
import com.example.domain.model.PendingDeviceActionConfirmation
import com.example.domain.model.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

data class ToolExecutionOutcome(
    val success: Boolean,
    val output: String,
    val wasCancelled: Boolean = false
)

class AgentToolExecutor(
    private val workspaceManager: WorkspaceManager,
    private val json: Json,
    private val internetToolClient: InternetToolClient = InternetToolClient(OkHttpClient(), json),
    private val terminalExecutor: TerminalExecutor? = null,
    private val deviceController: DeviceController? = null
) {
    val toolDefinitions: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "web_search",
            description = "Search the live internet for documentation, API endpoints, libraries, news, facts, or answers.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Search query string.")
                    }
                    putJsonObject("max_results") {
                        put("type", "integer")
                        put("description", "Maximum results to return (1-12, default 6).")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
            }
        ),
        ToolDefinition(
            name = "fetch_url",
            description = "Fetch any http:// or https:// web page, API documentation, JSON endpoint, or raw text file from the internet.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", "string")
                        put("description", "Full URL starting with http:// or https://.")
                    }
                    putJsonObject("method") {
                        put("type", "string")
                        put("description", "HTTP method (GET, POST, PUT, DELETE, HEAD). Defaults to GET.")
                    }
                    putJsonObject("headers") {
                        put("type", "object")
                        put("description", "Optional key-value HTTP headers.")
                    }
                    putJsonObject("body") {
                        put("type", "string")
                        put("description", "Optional request body for POST/PUT.")
                    }
                    putJsonObject("raw") {
                        put("type", "boolean")
                        put("description", "Set true to return raw HTML instead of extracted readable text.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("url")) })
            }
        ),
        ToolDefinition(
            name = "list_files",
            description = "List files and subdirectories inside a directory of the active project workspace.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path inside the project (use '.' for project root).")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            }
        ),
        ToolDefinition(
            name = "read_file",
            description = "Read the text contents of a file in the active project workspace with line numbers.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file.")
                    }
                    putJsonObject("start_line") {
                        put("type", "integer")
                        put("description", "Optional 1-based starting line number.")
                    }
                    putJsonObject("end_line") {
                        put("type", "integer")
                        put("description", "Optional 1-based ending line number.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            }
        ),
        ToolDefinition(
            name = "write_file",
            description = "Create a new file or overwrite an existing file in the active project workspace. Parent directories are created automatically.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path of the file to write.")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "Complete file content to write.")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("content"))
                })
            }
        ),
        ToolDefinition(
            name = "edit_file",
            description = "Modify an existing file by finding an exact text snippet and replacing it.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path to the file to edit.")
                    }
                    putJsonObject("find") {
                        put("type", "string")
                        put("description", "Exact text to find in the file.")
                    }
                    putJsonObject("replace") {
                        put("type", "string")
                        put("description", "Replacement text.")
                    }
                    putJsonObject("replace_all") {
                        put("type", "boolean")
                        put("description", "Set true to replace all occurrences; false requires a unique match.")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("path"))
                    add(JsonPrimitive("find"))
                    add(JsonPrimitive("replace"))
                })
            }
        ),
        ToolDefinition(
            name = "create_folder",
            description = "Create a folder (and any missing parent folders) in the active project workspace.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path of the folder to create.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            }
        ),
        ToolDefinition(
            name = "delete_file",
            description = "Delete a file or folder in the active project workspace. Always prompts the user for confirmation first.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Relative path of the file or folder to delete.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("path")) })
            }
        ),
        ToolDefinition(
            name = "search_files",
            description = "Search for a text or regex pattern across files in the active project workspace.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Text or regex pattern to search for.")
                    }
                    putJsonObject("path") {
                        put("type", "string")
                        put("description", "Optional relative directory or file path (defaults to '.').")
                    }
                    putJsonObject("regex") {
                        put("type", "boolean")
                        put("description", "If true, treat query as a regular expression.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("query")) })
            }
        ),
        ToolDefinition(
            name = "run_terminal",
            description = "Run a shell command in the built-in IB CODER terminal (/system/bin/sh in the active workspace) or install packages & toolchains (e.g. 'npm install react react-dom', 'npx create-vite', 'pip install <pkg>', 'ibpkg install gradle', 'gradle wrapper', 'ibpkg list').",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "Shell command or ibpkg/npm/pip/gradle command to execute.")
                    }
                    putJsonObject("timeout_seconds") {
                        put("type", "integer")
                        put("description", "Optional timeout in seconds (5-300, default 90).")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("command")) })
            }
        ),
        ToolDefinition(
            name = "device_status",
            description = "Check Root (su) and Shizuku authorization status, screen resolution, battery, and active foreground app on this Android phone.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            }
        ),
        ToolDefinition(
            name = "device_ui_dump",
            description = "Inspect the current Android screen hierarchy via UIAutomator over Root or Shizuku, returning visible labels, resource IDs, flags, and exact tap (x, y) coordinates.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("max_nodes") {
                        put("type", "integer")
                        put("description", "Maximum elements to return (default 80).")
                    }
                }
            }
        ),
        ToolDefinition(
            name = "device_tap",
            description = "Tap a screen coordinate (x, y) on the Android phone using Root or Shizuku input injection.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("x") {
                        put("type", "integer")
                        put("description", "X screen pixel coordinate.")
                    }
                    putJsonObject("y") {
                        put("type", "integer")
                        put("description", "Y screen pixel coordinate.")
                    }
                    putJsonObject("long_press_ms") {
                        put("type", "integer")
                        put("description", "Optional long-press hold duration in ms (omit for normal tap).")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("x"))
                    add(JsonPrimitive("y"))
                })
            }
        ),
        ToolDefinition(
            name = "device_swipe",
            description = "Perform a touch swipe gesture from (x1, y1) to (x2, y2) on the Android phone.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("x1") { put("type", "integer"); put("description", "Start X pixel.") }
                    putJsonObject("y1") { put("type", "integer"); put("description", "Start Y pixel.") }
                    putJsonObject("x2") { put("type", "integer"); put("description", "End X pixel.") }
                    putJsonObject("y2") { put("type", "integer"); put("description", "End Y pixel.") }
                    putJsonObject("duration_ms") {
                        put("type", "integer")
                        put("description", "Swipe duration in milliseconds (default 350).")
                    }
                }
                put("required", buildJsonArray {
                    add(JsonPrimitive("x1"))
                    add(JsonPrimitive("y1"))
                    add(JsonPrimitive("x2"))
                    add(JsonPrimitive("y2"))
                })
            }
        ),
        ToolDefinition(
            name = "device_type_text",
            description = "Type text into the currently focused Android text field via Root or Shizuku.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "Text string to type.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("text")) })
            }
        ),
        ToolDefinition(
            name = "device_keyevent",
            description = "Press an Android hardware or navigation key via Root or Shizuku (HOME, BACK, RECENTS, ENTER, DEL, POWER, VOLUME_UP, VOLUME_DOWN, MEDIA_PLAY_PAUSE, SEARCH).",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("key") {
                        put("type", "string")
                        put("description", "Key name (e.g. HOME, BACK, RECENTS, ENTER, VOLUME_UP) or numeric keycode.")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("key")) })
            }
        ),
        ToolDefinition(
            name = "device_list_apps",
            description = "List installed Android package names on the device (filtered optionally by search query).",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "Optional case-insensitive package substring filter.")
                    }
                    putJsonObject("include_system") {
                        put("type", "boolean")
                        put("description", "Set true to include system apps; false returns user-installed apps.")
                    }
                }
            }
        ),
        ToolDefinition(
            name = "device_launch_app",
            description = "Open/launch an installed Android application by package name using Root or Shizuku.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("package_name") {
                        put("type", "string")
                        put("description", "Android package ID (e.g. com.android.settings).")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("package_name")) })
            }
        ),
        ToolDefinition(
            name = "device_shell",
            description = "Run a privileged Android shell command via Root (su) or Shizuku (am, pm, settings, svc, dumpsys, logcat). Respects user confirmation policy.",
            parametersSchema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("command") {
                        put("type", "string")
                        put("description", "Privileged Android shell command to execute.")
                    }
                    putJsonObject("timeout_seconds") {
                        put("type", "integer")
                        put("description", "Timeout in seconds (2-120, default 20).")
                    }
                }
                put("required", buildJsonArray { add(JsonPrimitive("command")) })
            }
        )
    )

    suspend fun execute(
        projectId: String?,
        toolCallId: String,
        toolName: String,
        argumentsJson: String,
        confirmDeletion: suspend (PendingDeletionConfirmation) -> Boolean,
        confirmDeviceAction: suspend (PendingDeviceActionConfirmation) -> Boolean = { true }
    ): ToolExecutionOutcome {
        val args = try {
            if (argumentsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: Exception) {
            return ToolExecutionOutcome(
                success = false,
                output = "Error parsing tool arguments JSON: ${e.message}"
            )
        }

        // Internet tools work regardless of whether a workspace project is selected
        when (toolName) {
            "web_search" -> {
                val query = args["query"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: query")
                val maxResults = args["max_results"]?.jsonPrimitive?.intOrNull ?: 6
                val res = internetToolClient.webSearch(query, maxResults)
                return res.fold(
                    onSuccess = { ToolExecutionOutcome(true, it) },
                    onFailure = { ToolExecutionOutcome(false, it.message ?: "web_search failed.") }
                )
            }

            "fetch_url" -> {
                val url = args["url"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: url")
                val method = args["method"]?.jsonPrimitive?.content ?: "GET"
                val body = args["body"]?.jsonPrimitive?.content
                val raw = args["raw"]?.jsonPrimitive?.booleanOrNull ?: false
                val headersObj = args["headers"] as? JsonObject
                val headersMap = headersObj?.entries?.associate { (k, v) ->
                    k to (runCatching { v.jsonPrimitive.content }.getOrDefault(v.toString()))
                } ?: emptyMap()
                val res = internetToolClient.fetchUrl(
                    url = url,
                    method = method,
                    headers = headersMap,
                    body = body,
                    raw = raw
                )
                return res.fold(
                    onSuccess = { ToolExecutionOutcome(true, it) },
                    onFailure = { ToolExecutionOutcome(false, it.message ?: "fetch_url failed.") }
                )
            }

            "run_terminal" -> {
                val executor = terminalExecutor
                    ?: return ToolExecutionOutcome(false, "Terminal executor is not initialized in this environment.")
                val command = args["command"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: command")
                val timeout = args["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: 90
                val res = executor.execute(command = command, projectId = projectId, timeoutSeconds = timeout)
                val formatted = buildString {
                    appendLine("exit_code=${res.exitCode}")
                    if (res.output.isNotBlank()) append(res.output)
                }.trim()
                return ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = formatted
                )
            }

            "device_status",
            "device_ui_dump",
            "device_tap",
            "device_swipe",
            "device_type_text",
            "device_keyevent",
            "device_list_apps",
            "device_launch_app",
            "device_shell" -> {
                return executeDeviceTool(
                    toolCallId = toolCallId,
                    toolName = toolName,
                    args = args,
                    confirmDeviceAction = confirmDeviceAction
                )
            }
        }

        if (projectId.isNullOrBlank()) {
            return ToolExecutionOutcome(
                success = false,
                output = "Error: No active project workspace is selected. Select or create a project in Projects/Code, or use web_search / fetch_url for online tasks."
            )
        }

        return try {
            when (toolName) {
                "list_files" -> {
                    val path = args["path"]?.jsonPrimitive?.content ?: "."
                    ToolExecutionOutcome(true, workspaceManager.listDirectory(projectId, path))
                }

                "read_file" -> {
                    val path = args["path"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: path")
                    val startLine = args["start_line"]?.jsonPrimitive?.intOrNull
                    val endLine = args["end_line"]?.jsonPrimitive?.intOrNull
                    ToolExecutionOutcome(true, workspaceManager.readFile(projectId, path, startLine, endLine))
                }

                "write_file" -> {
                    val path = args["path"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: path")
                    val content = args["content"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: content")
                    ToolExecutionOutcome(true, workspaceManager.writeFile(projectId, path, content))
                }

                "edit_file" -> {
                    val path = args["path"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: path")
                    val find = args["find"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: find")
                    val replace = args["replace"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: replace")
                    val replaceAll = args["replace_all"]?.jsonPrimitive?.booleanOrNull ?: false
                    ToolExecutionOutcome(
                        true,
                        workspaceManager.editFile(projectId, path, find, replace, replaceAll)
                    )
                }

                "create_folder" -> {
                    val path = args["path"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: path")
                    ToolExecutionOutcome(true, workspaceManager.createFolder(projectId, path))
                }

                "delete_file" -> {
                    val path = args["path"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: path")
                    val stat = workspaceManager.statPath(projectId, path)
                    if (!stat.exists) {
                        return ToolExecutionOutcome(false, "Path does not exist: '$path'")
                    }
                    val approved = confirmDeletion(
                        PendingDeletionConfirmation(
                            toolCallId = toolCallId,
                            relativePath = path,
                            isDirectory = stat.isDirectory
                        )
                    )
                    if (!approved) {
                        ToolExecutionOutcome(
                            success = false,
                            output = "Deletion of '$path' was cancelled by the user.",
                            wasCancelled = true
                        )
                    } else {
                        ToolExecutionOutcome(true, workspaceManager.deletePath(projectId, path))
                    }
                }

                "search_files" -> {
                    val query = args["query"]?.jsonPrimitive?.content
                        ?: return ToolExecutionOutcome(false, "Missing required parameter: query")
                    val path = args["path"]?.jsonPrimitive?.content ?: "."
                    val regex = args["regex"]?.jsonPrimitive?.booleanOrNull ?: false
                    ToolExecutionOutcome(
                        true,
                        workspaceManager.searchFiles(projectId, query, path, regex)
                    )
                }

                else -> ToolExecutionOutcome(false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            ToolExecutionOutcome(false, "Tool '$toolName' failed: ${e.message ?: e::class.simpleName}")
        }
    }

    private suspend fun executeDeviceTool(
        toolCallId: String,
        toolName: String,
        args: JsonObject,
        confirmDeviceAction: suspend (PendingDeviceActionConfirmation) -> Boolean
    ): ToolExecutionOutcome {
        val controller = deviceController
            ?: return ToolExecutionOutcome(false, "Device controller is not initialized in this environment.")

        val controlSettings = controller.getControlSettings()
        if (!controlSettings.aiPhoneControlEnabled && toolName != "device_status") {
            return ToolExecutionOutcome(
                success = false,
                output = "AI Phone Control is currently disabled in Phone Control settings. Ask the user to enable 'Allow AI Agent to Control Phone' in the Phone Control sidebar screen."
            )
        }

        val probe = controller.refreshStatus()
        if (toolName == "device_status") {
            val telemetry = if (probe.isReady) controller.fetchTelemetry().getOrNull() else null
            val summary = buildString {
                appendLine("Privilege Status: ${probe.statusSummary}")
                appendLine("Active Backend: ${probe.activeBackend.name}")
                appendLine("Preferred Backend: ${controlSettings.preferredBackend.name}")
                appendLine("AI Phone Control Enabled: ${controlSettings.aiPhoneControlEnabled}")
                appendLine("Root (su): ${if (probe.rootAvailable) "AVAILABLE (uid=${probe.rootUid ?: 0}, bin=${probe.rootBinaryPath ?: "su"})" else "not detected"}")
                appendLine("Shizuku: ${probe.shizukuBinderState.label} (installed=${probe.shizukuInstalled}, running=${probe.shizukuRunning}, permission=${probe.shizukuPermissionGranted}, uid=${probe.shizukuUid ?: "n/a"})")
                if (telemetry != null) {
                    appendLine("Screen Resolution: ${telemetry.screenResolution}")
                    appendLine("Foreground App: ${telemetry.foregroundApp}")
                    telemetry.batteryLevel?.let { lvl ->
                        appendLine("Battery: $lvl%${if (telemetry.batteryCharging == true) " (charging)" else ""}")
                    }
                    if (telemetry.deviceModel.isNotBlank()) {
                        appendLine("Device: ${telemetry.deviceModel} • ${telemetry.androidRelease}")
                    }
                }
            }.trimEnd()
            return ToolExecutionOutcome(true, summary)
        }

        if (!probe.isReady) {
            return ToolExecutionOutcome(
                success = false,
                output = "Cannot run '$toolName': ${probe.statusSummary}. Guide the user to open 'Phone Control' in the sidebar to grant Root or authorize Shizuku."
            )
        }

        return when (toolName) {
            "device_ui_dump" -> {
                val maxNodes = (args["max_nodes"]?.jsonPrimitive?.intOrNull ?: 80).coerceIn(10, 200)
                controller.dumpUiHierarchy().fold(
                    onSuccess = { nodes ->
                        ToolExecutionOutcome(true, UiAutomatorDumpParser.formatForAgent(nodes, maxNodes))
                    },
                    onFailure = { err ->
                        ToolExecutionOutcome(false, err.message ?: "device_ui_dump failed.")
                    }
                )
            }

            "device_tap" -> {
                val x = args["x"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: x")
                val y = args["y"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: y")
                val holdMs = args["long_press_ms"]?.jsonPrimitive?.intOrNull
                val res = if (holdMs != null && holdMs >= 250) {
                    controller.longPress(x, y, holdMs)
                } else {
                    controller.tap(x, y)
                }
                ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = res.output.ifBlank {
                        "Executed [${res.backendUsed.name}] tap at ($x, $y)"
                    }
                )
            }

            "device_swipe" -> {
                val x1 = args["x1"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: x1")
                val y1 = args["y1"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: y1")
                val x2 = args["x2"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: x2")
                val y2 = args["y2"]?.jsonPrimitive?.intOrNull
                    ?: return ToolExecutionOutcome(false, "Missing required integer parameter: y2")
                val dur = args["duration_ms"]?.jsonPrimitive?.intOrNull ?: 350
                val res = controller.swipe(x1, y1, x2, y2, dur)
                ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = res.output.ifBlank {
                        "Executed [${res.backendUsed.name}] swipe ($x1,$y1)->($x2,$y2) in ${dur}ms"
                    }
                )
            }

            "device_type_text" -> {
                val text = args["text"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: text")
                val res = controller.inputText(text)
                ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = res.output.ifBlank { "Typed ${text.length} character(s) via ${res.backendUsed.name}." }
                )
            }

            "device_keyevent" -> {
                val key = args["key"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: key")
                val res = controller.pressKey(key)
                ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = res.output.ifBlank { "Sent keyevent '$key' via ${res.backendUsed.name}." }
                )
            }

            "device_list_apps" -> {
                val query = args["query"]?.jsonPrimitive?.content.orEmpty()
                val includeSystem = args["include_system"]?.jsonPrimitive?.booleanOrNull ?: false
                controller.listInstalledApps(includeSystem, query).fold(
                    onSuccess = { pkgs ->
                        val preview = pkgs.take(100).joinToString("\n")
                        val extra = if (pkgs.size > 100) "\n... (${pkgs.size - 100} more)" else ""
                        ToolExecutionOutcome(
                            true,
                            if (pkgs.isEmpty()) "No matching packages found." else "Packages (${pkgs.size}):\n$preview$extra"
                        )
                    },
                    onFailure = { err ->
                        ToolExecutionOutcome(false, err.message ?: "device_list_apps failed.")
                    }
                )
            }

            "device_launch_app" -> {
                val pkg = args["package_name"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: package_name")
                val res = controller.launchApp(pkg)
                ToolExecutionOutcome(
                    success = res.exitCode == 0,
                    output = res.output.ifBlank { "Launched $pkg via ${res.backendUsed.name}." }
                )
            }

            "device_shell" -> {
                val cmd = args["command"]?.jsonPrimitive?.content
                    ?: return ToolExecutionOutcome(false, "Missing required parameter: command")
                val blocked = DeviceCommandSafety.checkHardBlocked(cmd)
                if (blocked != null) {
                    return ToolExecutionOutcome(false, blocked)
                }
                val sensitiveReason = DeviceCommandSafety.classifySensitiveReason(cmd)
                    ?: "Executes privileged Android shell command (${probe.activeBackend.name})"
                if (controlSettings.confirmSensitiveDeviceActions) {
                    val approved = confirmDeviceAction(
                        PendingDeviceActionConfirmation(
                            toolCallId = toolCallId,
                            toolName = toolName,
                            actionTitle = "Run Privileged ${probe.activeBackend.name} Command",
                            commandPreview = cmd,
                            reason = sensitiveReason
                        )
                    )
                    if (!approved) {
                        return ToolExecutionOutcome(
                            success = false,
                            output = "Privileged device command cancelled by user.",
                            wasCancelled = true
                        )
                    }
                }
                val timeout = args["timeout_seconds"]?.jsonPrimitive?.intOrNull ?: 20
                val res = controller.runShell(cmd, timeout)
                val out = buildString {
                    appendLine("backend=${res.backendUsed.name} exit_code=${res.exitCode}")
                    if (res.output.isNotBlank()) append(res.output)
                }.trim()
                ToolExecutionOutcome(res.exitCode == 0, out)
            }

            else -> ToolExecutionOutcome(false, "Unknown device tool: $toolName")
        }
    }
}
