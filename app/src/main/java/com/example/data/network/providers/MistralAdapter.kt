package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class MistralAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "mistral",
        displayName = "Mistral",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.mistral.ai/v1",
        description = "Mistral AI models including Codestral & Mistral Large.",
        signupUrl = "https://console.mistral.ai/api-keys"
    )
}
