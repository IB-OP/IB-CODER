package com.example.data.local.db

import com.example.domain.model.ChatMessageUi
import com.example.domain.model.ChatRole
import com.example.domain.model.ChatSession
import com.example.domain.model.ToolCallRecord
import com.example.domain.model.WorkspaceProject
import kotlinx.serialization.json.Json

object DbMappers {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun ProjectEntity.toDomain(): WorkspaceProject = WorkspaceProject(
        id = id,
        name = name,
        rootPath = rootPath,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ChatSessionEntity.toDomain(): ChatSession = ChatSession(
        id = id,
        title = title,
        projectId = projectId,
        providerId = providerId,
        modelId = modelId,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ChatMessageEntity.toDomain(): ChatMessageUi {
        val calls = runCatching {
            json.decodeFromString<List<ToolCallRecord>>(toolCallsJson)
        }.getOrDefault(emptyList())
        val parsedRole = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.ASSISTANT)
        return ChatMessageUi(
            id = id,
            sessionId = sessionId,
            role = parsedRole,
            content = content,
            reasoning = reasoning,
            toolCalls = calls,
            providerId = providerId,
            modelId = modelId,
            errorMessage = errorMessage,
            isStreaming = false,
            createdAt = createdAt
        )
    }

    fun ChatMessageUi.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        reasoning = reasoning,
        toolCallsJson = json.encodeToString(toolCalls),
        providerId = providerId,
        modelId = modelId,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}
