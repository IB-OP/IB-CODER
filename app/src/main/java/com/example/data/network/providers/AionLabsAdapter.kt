package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class AionLabsAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "aion_labs",
        displayName = "Aion Labs",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.aionlabs.ai/v1",
        description = "OpenAI-compatible endpoint by Aion Labs."
    )
}
