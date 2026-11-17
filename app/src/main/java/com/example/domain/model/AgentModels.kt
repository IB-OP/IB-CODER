package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

@Serializable
enum class ToolExecutionStatus {
    RUNNING,
    AWAITING_CONFIRMATION,
    SUCCESS,
    CANCELLED,
    ERROR
}

@Serializable
data class ToolCallRecord(
    val id: String,
    val name: String,
    val argumentsJson: String,
    val status: ToolExecutionStatus,
    val resultText: String = ""
)

data class ChatMessageUi(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val reasoning: String = "",
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val providerId: String? = null,
    val modelId: String? = null,
    val errorMessage: String? = null,
    val isStreaming: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
enum class SessionMode {
    NORMAL,
    SPECIAL
}

@Serializable
data class SessionPromptConfig(
    val mode: SessionMode = SessionMode.NORMAL,
    val presetId: String = "normal",
    val presetTitle: String = "Normal Q&A",
    val systemPrompt: String = ""
)

data class ChatSession(
    val id: String,
    val title: String,
    val projectId: String?,
    val providerId: String,
    val modelId: String,
    val mode: SessionMode = SessionMode.NORMAL,
    val presetTitle: String? = null,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class WorkspaceProject(
    val id: String,
    val name: String,
    val rootPath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isDeviceFolder: Boolean
        get() = rootPath.startsWith("content://")
}

data class FileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val depth: Int = 0
)

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultProviderId: String = "coarena",
    val defaultModelId: String = "",
    val systemPrompt: String = ""
)

data class PendingDeletionConfirmation(
    val toolCallId: String,
    val relativePath: String,
    val isDirectory: Boolean
)
