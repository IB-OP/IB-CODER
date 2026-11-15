package com.example.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

enum class ProviderCategory {
    FREE_TIER,
    PAID_POPULAR,
    LOCAL,
    CUSTOM
}

enum class ToolSupportStatus {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN
}

@Serializable
data class ProviderConfig(
    val providerId: String,
    val baseUrl: String,
    val selectedModel: String = "",
    val enabled: Boolean = false,
    val accountId: String = "",
    val deploymentName: String = "",
    val fetchedModels: List<String> = emptyList()
)

data class ProviderMetadata(
    val id: String,
    val displayName: String,
    val category: ProviderCategory,
    val defaultBaseUrl: String,
    val description: String,
    val signupUrl: String? = null,
    val requiresApiKey: Boolean = true,
    val requiresAccountId: Boolean = false,
    val requiresDeploymentName: Boolean = false,
    val supportsModelsEndpoint: Boolean = true,
    val supportsToolCallingByDefault: Boolean = true,
    val modelPlaceholder: String = "Enter model ID"
)

data class ProviderModel(
    val id: String,
    val displayName: String = id,
    val supportsToolCalling: ToolSupportStatus = ToolSupportStatus.SUPPORTED
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0L,
    val modelsCount: Int? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JsonObject
)

@Serializable
data class ToolCallDto(
    val id: String,
    val name: String,
    val argumentsJson: String
)

data class ChatMessageDto(
    val role: ChatRole,
    val content: String,
    val toolCalls: List<ToolCallDto> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    val tools: List<ToolDefinition> = emptyList(),
    val temperature: Double = 0.2,
    val maxTokens: Int = 8192
)

sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent
    data class ReasoningDelta(val text: String) : StreamEvent
    data class ToolCallCompleted(val toolCall: ToolCallDto) : StreamEvent
    data class UsageReported(val usage: TokenUsage) : StreamEvent
    data class Error(
        val message: String,
        val httpCode: Int? = null,
        val retryAfterSeconds: Int? = null
    ) : StreamEvent
    data object Done : StreamEvent
}
