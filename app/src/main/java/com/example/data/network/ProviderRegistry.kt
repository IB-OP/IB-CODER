package com.example.data.network

import com.example.data.network.providers.AionLabsAdapter
import com.example.data.network.providers.AnthropicAdapter
import com.example.data.network.providers.AzureOpenAiAdapter
import com.example.data.network.providers.CloudflareWorkersAiAdapter
import com.example.data.network.providers.CoarenaAdapter
import com.example.data.network.providers.CustomOpenAiAdapter
import com.example.data.network.providers.DeepSeekAdapter
import com.example.data.network.providers.FireworksAiAdapter
import com.example.data.network.providers.GeminiAdapter
import com.example.data.network.providers.GonkaBrokerAdapter
import com.example.data.network.providers.GroqAdapter
import com.example.data.network.providers.KiloCodeAdapter
import com.example.data.network.providers.Llm7Adapter
import com.example.data.network.providers.MistralAdapter
import com.example.data.network.providers.NvidiaNimAdapter
import com.example.data.network.providers.OllamaAdapter
import com.example.data.network.providers.OpenAiAdapter
import com.example.data.network.providers.OpenRouterAdapter
import com.example.data.network.providers.OvhCloudAdapter
import com.example.data.network.providers.PerplexityAdapter
import com.example.data.network.providers.TogetherAiAdapter
import com.example.data.network.providers.XAiAdapter
import com.example.data.network.providers.ZAiAdapter
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

class ProviderRegistry(
    okHttpClient: OkHttpClient,
    json: Json
) {
    val adapters: List<AiProviderAdapter> = listOf(
        // Free-tier providers
        CoarenaAdapter(okHttpClient, json),
        GeminiAdapter(okHttpClient, json),
        GroqAdapter(okHttpClient, json),
        OpenRouterAdapter(okHttpClient, json),
        NvidiaNimAdapter(okHttpClient, json),
        CloudflareWorkersAiAdapter(okHttpClient, json),
        ZAiAdapter(okHttpClient, json),
        AionLabsAdapter(okHttpClient, json),
        GonkaBrokerAdapter(okHttpClient, json),
        KiloCodeAdapter(okHttpClient, json),
        Llm7Adapter(okHttpClient, json),
        OvhCloudAdapter(okHttpClient, json),
        // Paid / popular / local / custom providers
        AnthropicAdapter(okHttpClient, json),
        OpenAiAdapter(okHttpClient, json),
        MistralAdapter(okHttpClient, json),
        DeepSeekAdapter(okHttpClient, json),
        XAiAdapter(okHttpClient, json),
        TogetherAiAdapter(okHttpClient, json),
        FireworksAiAdapter(okHttpClient, json),
        PerplexityAdapter(okHttpClient, json),
        AzureOpenAiAdapter(okHttpClient, json),
        OllamaAdapter(okHttpClient, json),
        CustomOpenAiAdapter(okHttpClient, json)
    )

    private val byId: Map<String, AiProviderAdapter> = adapters.associateBy { it.metadata.id }

    fun getAdapter(providerId: String): AiProviderAdapter? = byId[providerId]
}
