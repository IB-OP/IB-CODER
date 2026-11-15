package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class OpenRouterAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "openrouter",
        displayName = "OpenRouter",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        description = "Unified router with free :free models and paid models.",
        signupUrl = "https://openrouter.ai/keys"
    )

    override val extraHeaders: Map<String, String> = mapOf(
        "X-Title" to "AI Coder Android"
    )
}
