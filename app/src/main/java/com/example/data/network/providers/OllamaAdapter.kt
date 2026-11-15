package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class OllamaAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "ollama",
        displayName = "Ollama (Local)",
        category = ProviderCategory.LOCAL,
        defaultBaseUrl = "http://localhost:11434/v1",
        description = "Local Ollama server (on-device Termux or LAN host; no API key needed).",
        requiresApiKey = false,
        modelPlaceholder = "qwen2.5-coder:7b"
    )
}
