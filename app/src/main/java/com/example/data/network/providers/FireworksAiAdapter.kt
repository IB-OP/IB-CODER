package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class FireworksAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "fireworks",
        displayName = "Fireworks AI",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.fireworks.ai/inference/v1",
        description = "Low-latency serverless and fine-tuned models.",
        signupUrl = "https://fireworks.ai/account/api-keys"
    )
}
