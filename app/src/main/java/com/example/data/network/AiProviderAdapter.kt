package com.example.data.network

import com.example.domain.model.ChatCompletionRequest
import com.example.domain.model.ConnectionTestResult
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.ProviderModel
import com.example.domain.model.StreamEvent
import com.example.domain.model.ToolSupportStatus
import kotlinx.coroutines.flow.Flow

interface AiProviderAdapter {
    val metadata: ProviderMetadata

    fun defaultConfig(): ProviderConfig = ProviderConfig(
        providerId = metadata.id,
        baseUrl = metadata.defaultBaseUrl,
        selectedModel = "",
        enabled = false
    )

    fun modelToolSupport(modelId: String): ToolSupportStatus {
        if (!metadata.supportsToolCallingByDefault) return ToolSupportStatus.UNSUPPORTED
        val lower = modelId.lowercase()
        if (lower.isBlank()) return ToolSupportStatus.UNKNOWN
        if (lower.contains("sonar") ||
            lower.contains("embedding") ||
            lower.contains("whisper") ||
            lower.contains("tts") ||
            lower.contains("dall-e") ||
            lower.contains("moderation") ||
            lower.startsWith("o1-mini") ||
            lower.startsWith("o1-preview")
        ) {
            return ToolSupportStatus.UNSUPPORTED
        }
        return ToolSupportStatus.SUPPORTED
    }

    suspend fun fetchModels(config: ProviderConfig, apiKey: String): Result<List<ProviderModel>>

    suspend fun testConnection(config: ProviderConfig, apiKey: String): ConnectionTestResult

    fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamEvent>
}
