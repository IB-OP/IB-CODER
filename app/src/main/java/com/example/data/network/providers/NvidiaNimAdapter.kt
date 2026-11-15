package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class NvidiaNimAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "nvidia_nim",
        displayName = "NVIDIA NIM",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://integrate.api.nvidia.com/v1",
        description = "NVIDIA hosted foundation models with free developer credits.",
        signupUrl = "https://build.nvidia.com"
    )
}
