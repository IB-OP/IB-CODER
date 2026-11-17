package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class CustomOpenAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json,
    override val metadata: ProviderMetadata = ProviderMetadata(
        id = "custom_openai",
        displayName = "Custom OpenAI-Compatible",
        category = ProviderCategory.CUSTOM,
        defaultBaseUrl = "https://api.example.com/v1",
        description = "Any custom vLLM, LiteLLM, LM Studio, or OpenAI-compatible server.",
        requiresApiKey = false
    )
) : OpenAiCompatibleAdapter(okHttpClient, json)
