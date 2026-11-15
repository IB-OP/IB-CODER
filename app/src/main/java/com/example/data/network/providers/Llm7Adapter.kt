package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class Llm7Adapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "llm7",
        displayName = "LLM7.io",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.llm7.io/v1",
        description = "Free-tier OpenAI-compatible API endpoint.",
        requiresApiKey = false
    )
}
