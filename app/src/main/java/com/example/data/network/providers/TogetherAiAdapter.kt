package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class TogetherAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "together",
        displayName = "Together AI",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.together.xyz/v1",
        description = "Fast open-weights model hosting by Together AI.",
        signupUrl = "https://api.together.ai/settings/api-keys"
    )
}
