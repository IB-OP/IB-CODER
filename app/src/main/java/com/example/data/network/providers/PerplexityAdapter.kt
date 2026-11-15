package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class PerplexityAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "perplexity",
        displayName = "Perplexity",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.perplexity.ai",
        description = "Search-augmented Sonar models (does not support workspace tool calling).",
        signupUrl = "https://www.perplexity.ai/settings/api",
        supportsModelsEndpoint = false,
        supportsToolCallingByDefault = false,
        modelPlaceholder = "sonar-pro"
    )
}
