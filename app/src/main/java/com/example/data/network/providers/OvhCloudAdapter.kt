package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class OvhCloudAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "ovhcloud",
        displayName = "OVHcloud AI Endpoints",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
        description = "European sovereign AI endpoints with free tier."
    )
}
