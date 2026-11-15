package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ZAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "z_ai",
        displayName = "Z AI (Zhipu)",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.z.ai/api/paas/v4",
        description = "GLM models from Zhipu AI with free tier access.",
        signupUrl = "https://z.ai"
    )
}
