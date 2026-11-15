package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class GroqAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "groq",
        displayName = "Groq",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        description = "Ultra-fast LPU inference with generous free tier.",
        signupUrl = "https://console.groq.com/keys"
    )
}
