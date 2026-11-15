package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class DeepSeekAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "deepseek",
        displayName = "DeepSeek",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.deepseek.com/v1",
        description = "DeepSeek-V3 and DeepSeek-R1 coding & reasoning models.",
        signupUrl = "https://platform.deepseek.com/api_keys"
    )
}
