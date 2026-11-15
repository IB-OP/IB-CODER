package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class GeminiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "gemini",
        displayName = "Google Gemini",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
        description = "Google Gemini via OpenAI-compatible endpoint (free tier available).",
        signupUrl = "https://aistudio.google.com/apikey"
    )

    override fun resolveBaseUrl(config: ProviderConfig): Result<String> {
        val raw = config.baseUrl.trim().trimEnd('/')
        if (raw.isEmpty()) return Result.failure(IllegalArgumentException("Base URL cannot be empty."))
        val normalized = if (raw.endsWith("/v1beta")) "$raw/openai" else raw
        return Result.success(normalized)
    }
}
