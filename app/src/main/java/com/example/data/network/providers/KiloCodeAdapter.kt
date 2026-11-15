package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class KiloCodeAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "kilo_code",
        displayName = "Kilo Code",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.kilo.ai/api/gateway",
        description = "Kilo Code AI gateway with free model access."
    )
}
