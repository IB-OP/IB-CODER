package com.example.data.agent

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

data class ToolExecutionOutcome(
    val success: Boolean,
    val output: String,
    val wasCancelled: Boolean = false
)

class AgentToolExecutor(
    private val workspaceManager: WorkspaceManager,
    private val json: Json
) {
    val toolDefinitions: List<ToolDefinition> = listOf(
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
        )
    )

    suspend fun execute(
        projectId: String?,
        toolCallId: String,
        toolName: String,
        argumentsJson: String,
        confirmDeletion: suspend (PendingDeletionConfirmation) -> Boolean
    ): ToolExecutionOutcome {
        if (projectId.isNullOrBlank()) {
            return ToolExecutionOutcome(
                success = false,
                output = "Error: No active project workspace is selected. Ask the user to create or select a project in the Workspace tab."
            )
        }

        val args = try {
            if (argumentsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: Exception) {
            return ToolExecutionOutcome(
                success = false,
                output = "Error parsing tool arguments JSON: ${e.message}"
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
                    val target = workspaceManager.resolveSafeFile(projectId, path)
                    if (!target.exists()) {
                        return ToolExecutionOutcome(false, "Path does not exist: '$path'")
                    }
                    val approved = confirmDeletion(
                        PendingDeletionConfirmation(
                            toolCallId = toolCallId,
                            relativePath = path,
                            isDirectory = target.isDirectory
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
