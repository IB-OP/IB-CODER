package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class CoarenaAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "coarena",
        displayName = "Coarena",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://coarena.ai/api/v1",
        description = "OpenAI-compatible gateway with $5/day free tier usage.",
        signupUrl = "https://coarena.ai/account/api"
    )
}
