package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class GonkaBrokerAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "gonka_broker",
        displayName = "Gonka Broker",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://proxy.gonkabroker.com/v1",
        description = "Decentralized OpenAI-compatible inference broker."
    )
}
