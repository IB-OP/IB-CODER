package com.example.data.agent

import com.example.data.network.GitHubSyncManager
import com.example.data.network.McpManager
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ChatRepository
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ChatCompletionRequest
import com.example.domain.model.ChatMessageDto
import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.PendingDeletionConfirmation
import com.example.domain.model.SessionMode
import com.example.domain.model.StreamEvent
import com.example.domain.model.ToolCallDto
import com.example.domain.model.ToolCallRecord
import com.example.domain.model.ToolExecutionStatus
import com.example.domain.model.ToolSupportStatus
import com.example.domain.prompt.SystemPromptPresets
import kotlinx.coroutines.CancellationException
import java.util.UUID

class CodingAgentOrchestrator(
    private val providerRegistry: ProviderRegistry,
    private val settingsDataStore: SettingsDataStore,
    private val chatRepository: ChatRepository,
    private val toolExecutor: AgentToolExecutor,
    private val workspaceManager: WorkspaceManager,
    private val mcpManager: McpManager,
    private val gitHubSyncManager: GitHubSyncManager
) {
    suspend fun runAgentTurn(
        sessionId: String,
        projectId: String?,
        providerId: String,
        modelId: String,
        userText: String,
        onStreamingMessageUpdated: (ChatMessageUi?) -> Unit,
        confirmDeletion: suspend (PendingDeletionConfirmation) -> Boolean
    ) {
        val adapter = providerRegistry.getAdapter(providerId)
            ?: throw IllegalStateException("Unknown provider: $providerId")

        val config = settingsDataStore.getProviderConfig(providerId) ?: adapter.defaultConfig()
        val apiKey = settingsDataStore.getApiKey(providerId)
        val settings = settingsDataStore.getSettings()
        val sessionPromptCfg = settingsDataStore.getSessionPromptConfig(sessionId)

        // Auto-title new session from the first user prompt
        val existingMessages = chatRepository.getMessages(sessionId)
        if (existingMessages.isEmpty()) {
            val autoTitle = userText.trim().lineSequence().firstOrNull()
                ?.take(48)?.ifBlank { "New Chat" } ?: "New Chat"
            chatRepository.updateSessionTitle(sessionId, autoTitle)
        }
        chatRepository.updateSessionProviderAndModel(sessionId, providerId, modelId)

        val userMessage = ChatMessageUi(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            role = ChatRole.USER,
            content = userText,
            providerId = providerId,
            modelId = modelId
        )
        chatRepository.saveMessage(userMessage)

        val resolvedMcpTools = mcpManager.getEnabledResolvedTools()
        val mcpLookup = resolvedMcpTools.associateBy { it.exposedName }

        val basePrompt = when {
            sessionPromptCfg != null && sessionPromptCfg.systemPrompt.isNotBlank() ->
                sessionPromptCfg.systemPrompt
            sessionPromptCfg?.mode == SessionMode.NORMAL ->
                SystemPromptPresets.NORMAL_QA_PROMPT
            else ->
                settings.systemPrompt
        }

        val projectSummary = buildWorkspaceSummary(projectId, sessionPromptCfg?.mode)
        val mcpSummary = if (resolvedMcpTools.isNotEmpty()) {
            buildString {
                appendLine("Connected MCP Tools (callable alongside workspace & internet tools):")
                for (t in resolvedMcpTools) {
                    appendLine("  - ${t.exposedName} (${t.serverName}): ${t.description}")
                }
            }.trimEnd()
        } else {
            ""
        }

        val fullSystemPrompt = buildString {
            append(basePrompt)
            append("\n\n")
            append(projectSummary)
            if (mcpSummary.isNotBlank()) {
                append("\n\n")
                append(mcpSummary)
            }
        }

        val toolSupport = adapter.modelToolSupport(modelId)
        val toolsToSend = if (toolSupport == ToolSupportStatus.UNSUPPORTED) {
            emptyList()
        } else {
            toolExecutor.toolDefinitions + mcpManager.toToolDefinitions(resolvedMcpTools)
        }

        val maxLoops = 24
        var workspaceModified = false
        try {
            for (step in 0 until maxLoops) {
            val history = chatRepository.getMessages(sessionId)
            val dtoMessages = buildList {
                add(ChatMessageDto(role = ChatRole.SYSTEM, content = fullSystemPrompt))
                for (msg in history) {
                    if (msg.role == ChatRole.USER) {
                        add(ChatMessageDto(role = ChatRole.USER, content = msg.content))
                    } else if (msg.role == ChatRole.ASSISTANT) {
                        if (msg.errorMessage != null && msg.content.isBlank() && msg.toolCalls.isEmpty()) {
                            continue
                        }
                        add(
                            ChatMessageDto(
                                role = ChatRole.ASSISTANT,
                                content = msg.content,
                                toolCalls = msg.toolCalls.map {
                                    ToolCallDto(it.id, it.name, it.argumentsJson)
                                }
                            )
                        )
                        for (tc in msg.toolCalls) {
                            add(
                                ChatMessageDto(
                                    role = ChatRole.TOOL,
                                    content = tc.resultText.ifBlank { "No output" },
                                    toolCallId = tc.id,
                                    toolName = tc.name
                                )
                            )
                        }
                    }
                }
            }

            val assistantMsgId = UUID.randomUUID().toString()
            val textBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            val toolRecords = mutableListOf<ToolCallRecord>()
            var streamError: String? = null

            fun currentUiMessage(streaming: Boolean): ChatMessageUi = ChatMessageUi(
                id = assistantMsgId,
                sessionId = sessionId,
                role = ChatRole.ASSISTANT,
                content = textBuilder.toString(),
                reasoning = reasoningBuilder.toString(),
                toolCalls = toolRecords.toList(),
                providerId = providerId,
                modelId = modelId,
                errorMessage = streamError,
                isStreaming = streaming
            )

            onStreamingMessageUpdated(currentUiMessage(streaming = true))

            try {
                val request = ChatCompletionRequest(
                    model = modelId,
                    messages = dtoMessages,
                    tools = toolsToSend
                )
                adapter.streamChat(request, config, apiKey).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            textBuilder.append(event.text)
                            onStreamingMessageUpdated(currentUiMessage(streaming = true))
                        }
                        is StreamEvent.ReasoningDelta -> {
                            reasoningBuilder.append(event.text)
                            onStreamingMessageUpdated(currentUiMessage(streaming = true))
                        }
                        is StreamEvent.ToolCallCompleted -> {
                            toolRecords += ToolCallRecord(
                                id = event.toolCall.id,
                                name = event.toolCall.name,
                                argumentsJson = event.toolCall.argumentsJson,
                                status = ToolExecutionStatus.RUNNING
                            )
                            onStreamingMessageUpdated(currentUiMessage(streaming = true))
                        }
                        is StreamEvent.UsageReported -> {
                            chatRepository.addTokenUsage(
                                sessionId = sessionId,
                                prompt = event.usage.promptTokens,
                                completion = event.usage.completionTokens,
                                total = event.usage.totalTokens
                            )
                        }
                        is StreamEvent.Error -> {
                            streamError = event.message
                            onStreamingMessageUpdated(currentUiMessage(streaming = false))
                        }
                        StreamEvent.Done -> Unit
                    }
                }
            } catch (ce: CancellationException) {
                if (textBuilder.isNotEmpty() || reasoningBuilder.isNotEmpty() || toolRecords.isNotEmpty()) {
                    val stoppedMsg = currentUiMessage(streaming = false).copy(
                        errorMessage = "Generation stopped by user."
                    )
                    chatRepository.saveMessage(stoppedMsg)
                }
                onStreamingMessageUpdated(null)
                throw ce
            }

            if (streamError != null) {
                chatRepository.saveMessage(currentUiMessage(streaming = false))
                onStreamingMessageUpdated(null)
                return
            }

            if (toolRecords.isEmpty()) {
                chatRepository.saveMessage(currentUiMessage(streaming = false))
                onStreamingMessageUpdated(null)
                return
            }

            // Execute each requested tool inline
            for (idx in toolRecords.indices) {
                val tc = toolRecords[idx]
                val mcpTool = mcpLookup[tc.name]
                if (mcpTool != null) {
                    val (ok, output) = mcpManager.executeMcpTool(mcpTool, tc.argumentsJson)
                    toolRecords[idx] = tc.copy(
                        status = if (ok) ToolExecutionStatus.SUCCESS else ToolExecutionStatus.ERROR,
                        resultText = output
                    )
                } else {
                    if (tc.name == "delete_file") {
                        toolRecords[idx] = tc.copy(status = ToolExecutionStatus.AWAITING_CONFIRMATION)
                        onStreamingMessageUpdated(currentUiMessage(streaming = true))
                    }
                    val outcome = toolExecutor.execute(
                        projectId = projectId,
                        toolCallId = tc.id,
                        toolName = tc.name,
                        argumentsJson = tc.argumentsJson,
                        confirmDeletion = confirmDeletion
                    )
                    val finalStatus = when {
                        outcome.wasCancelled -> ToolExecutionStatus.CANCELLED
                        outcome.success -> {
                            if (tc.name in MUTATING_TOOLS) workspaceModified = true
                            ToolExecutionStatus.SUCCESS
                        }
                        else -> ToolExecutionStatus.ERROR
                    }
                    toolRecords[idx] = tc.copy(
                        status = finalStatus,
                        resultText = outcome.output
                    )
                }
                onStreamingMessageUpdated(currentUiMessage(streaming = true))
            }

            chatRepository.saveMessage(currentUiMessage(streaming = false))
            onStreamingMessageUpdated(null)
            }
        } finally {
            if (workspaceModified && !projectId.isNullOrBlank()) {
                val summary = userText.trim().lineSequence().firstOrNull()?.take(60)
                    ?.ifBlank { "IB CODER changes" } ?: "IB CODER changes"
                gitHubSyncManager.scheduleAutoSync(
                    projectId = projectId,
                    commitReason = "IB CODER: $summary",
                    immediate = true
                )
            }
        }
    }

    companion object {
        private val MUTATING_TOOLS = setOf("write_file", "edit_file", "create_folder", "delete_file")
    }

    private suspend fun buildWorkspaceSummary(
        projectId: String?,
        sessionMode: SessionMode?
    ): String {
        if (projectId.isNullOrBlank()) {
            return if (sessionMode == SessionMode.NORMAL) {
                "Active Workspace: None (Normal Q&A & Live Internet mode — use web_search and fetch_url for online information)."
            } else {
                "Active Workspace: None selected. You can answer questions and use web_search/fetch_url, or ask the user to open/create a project in Projects if they want file edits."
            }
        }
        val project = workspaceManager.getProject(projectId)
            ?: return "Active Workspace: None selected."
        val nodes = workspaceManager.buildFileTree(projectId)
        val preview = if (nodes.isEmpty()) {
            "(empty workspace)"
        } else {
            nodes.take(60).joinToString("\n") { node ->
                if (node.isDirectory) "  [DIR] ${node.relativePath}/" else "  ${node.relativePath}"
            } + if (nodes.size > 60) "\n  ... (${nodes.size - 60} more items)" else ""
        }
        return "Active Workspace: '${project.name}'\nWorkspace Files:\n$preview"
    }
}
