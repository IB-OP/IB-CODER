package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class AzureOpenAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "azure_openai",
        displayName = "Azure OpenAI",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://{resource}.openai.azure.com",
        description = "Microsoft Azure OpenAI service (requires resource endpoint & deployment name).",
        requiresDeploymentName = true,
        supportsModelsEndpoint = false,
        modelPlaceholder = "Deployment name (e.g. gpt-4o)"
    )

    override fun validateCredentials(config: ProviderConfig, apiKey: String): String? {
        if (config.baseUrl.isBlank() || config.baseUrl.contains("{resource}")) {
            return "Enter your Azure OpenAI resource endpoint URL."
        }
        if (config.deploymentName.isBlank() && config.selectedModel.isBlank()) {
            return "Enter your Azure OpenAI deployment name."
        }
        return super.validateCredentials(config, apiKey)
    }

    override fun applyAuthHeaders(builder: Request.Builder, apiKey: String) {
        if (apiKey.isNotBlank()) {
            builder.header("api-key", apiKey)
        }
    }

    override fun chatCompletionsEndpoint(resolvedBaseUrl: String): String {
        return "$resolvedBaseUrl/chat/completions?api-version=$AZURE_API_VERSION"
    }

    override fun resolveBaseUrl(config: ProviderConfig): Result<String> {
        val raw = config.baseUrl.trim().trimEnd('/')
        if (raw.isEmpty() || raw.contains("{resource}")) {
            return Result.failure(IllegalArgumentException("Enter your Azure OpenAI endpoint URL."))
        }
        val deployment = config.deploymentName.trim().ifBlank { config.selectedModel.trim() }
        if (raw.contains("/openai/deployments/")) {
            return Result.success(raw)
        }
        if (deployment.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your Azure deployment name."))
        }
        return Result.success("$raw/openai/deployments/$deployment")
    }

    companion object {
        private const val AZURE_API_VERSION = "2024-10-21"
    }
}
