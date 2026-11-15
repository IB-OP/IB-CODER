package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class OpenAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "openai",
        displayName = "OpenAI",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.openai.com/v1",
        description = "Official OpenAI API (GPT & o-series models).",
        signupUrl = "https://platform.openai.com/api-keys"
    )
}
