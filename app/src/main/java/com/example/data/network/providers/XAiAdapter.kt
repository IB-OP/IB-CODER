package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class XAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "xai",
        displayName = "xAI (Grok)",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.x.ai/v1",
        description = "Grok models via xAI's OpenAI-compatible API.",
        signupUrl = "https://console.x.ai"
    )
}
