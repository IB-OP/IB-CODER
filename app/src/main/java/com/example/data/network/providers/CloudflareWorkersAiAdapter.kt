package com.example.data.network.providers

import com.example.data.network.OpenAiCompatibleAdapter
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
import com.example.domain.model.ProviderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class CloudflareWorkersAiAdapter(
    okHttpClient: OkHttpClient,
    json: Json
) : OpenAiCompatibleAdapter(okHttpClient, json) {
    override val metadata = ProviderMetadata(
        id = "cloudflare",
        displayName = "Cloudflare Workers AI",
        category = ProviderCategory.FREE_TIER,
        defaultBaseUrl = "https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1",
        description = "Serverless AI inference at the edge (requires Account ID).",
        signupUrl = "https://dash.cloudflare.com",
        requiresAccountId = true,
        modelPlaceholder = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"
    )

    override fun validateCredentials(config: ProviderConfig, apiKey: String): String? {
        if (config.accountId.isBlank() && config.baseUrl.contains("{account_id}")) {
            return "Cloudflare Account ID is required."
        }
        return super.validateCredentials(config, apiKey)
    }

    override fun resolveBaseUrl(config: ProviderConfig): Result<String> {
        val raw = config.baseUrl.trim().trimEnd('/')
        if (raw.isEmpty()) {
            return Result.failure(IllegalArgumentException("Base URL cannot be empty."))
        }
        val acct = config.accountId.trim()
        if (raw.contains("{account_id}") && acct.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your Cloudflare Account ID."))
        }
        val resolved = raw.replace("{account_id}", acct)
        return Result.success(resolved)
    }

    override suspend fun fetchModels(
        config: ProviderConfig,
        apiKey: String
    ): Result<List<ProviderModel>> = withContext(Dispatchers.IO) {
        val standard = super.fetchModels(config, apiKey)
        if (standard.isSuccess) return@withContext standard

        val acct = config.accountId.trim()
        if (acct.isEmpty() || apiKey.isBlank()) return@withContext standard

        val searchUrl = "https://api.cloudflare.com/client/v4/accounts/$acct/ai/models/search"
        val req = Request.Builder()
            .url(searchUrl)
            .get()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext standard
                val root = json.parseToJsonElement(body).jsonObject
                val resultArr = root["result"] as? JsonArray ?: return@withContext standard
                val models = resultArr.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    ProviderModel(
                        id = name,
                        displayName = name,
                        supportsToolCalling = modelToolSupport(name)
                    )
                }.distinctBy { it.id }.sortedBy { it.id }
                if (models.isEmpty()) standard else Result.success(models)
            }
        } catch (_: IOException) {
            standard
        } catch (_: Exception) {
            standard
        }
    }
}
