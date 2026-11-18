package com.example.data.agent

import com.example.data.network.InternetToolClient
import com.example.data.terminal.TerminalExecutor
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.PendingDeletionConfirmation
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
    private val terminalExecutor: TerminalExecutor? = null
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
        )
    )

    suspend fun execute(
        projectId: String?,
        toolCallId: String,
        toolName: String,
        argumentsJson: String,
        confirmDeletion: suspend (PendingDeletionConfirmation) -> Boolean
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
}
