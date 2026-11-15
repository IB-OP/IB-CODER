package com.example.data.network

import com.example.domain.model.ChatCompletionRequest
import com.example.domain.model.ChatRole
import com.example.domain.model.ConnectionTestResult
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderModel
import com.example.domain.model.StreamEvent
import com.example.domain.model.TokenUsage
import com.example.domain.model.ToolCallDto
import com.example.domain.model.ToolSupportStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

abstract class OpenAiCompatibleAdapter(
    protected val okHttpClient: OkHttpClient,
    protected val json: Json
) : AiProviderAdapter {

    protected open val includeStreamUsageOption: Boolean = true
    protected open val extraHeaders: Map<String, String> = emptyMap()

    protected open fun resolveBaseUrl(config: ProviderConfig): Result<String> {
        val url = config.baseUrl.trim().trimEnd('/')
        if (url.isEmpty()) {
            return Result.failure(IllegalArgumentException("Base URL cannot be empty."))
        }
        return Result.success(url)
    }

    protected open fun validateCredentials(config: ProviderConfig, apiKey: String): String? {
        if (metadata.requiresApiKey && apiKey.isBlank()) {
            return "API key is required for ${metadata.displayName}. Configure it in Provider Settings."
        }
        return null
    }

    protected open fun modelsEndpoint(resolvedBaseUrl: String): String = "$resolvedBaseUrl/models"

    protected open fun chatCompletionsEndpoint(resolvedBaseUrl: String): String =
        "$resolvedBaseUrl/chat/completions"

    protected open fun applyAuthHeaders(builder: Request.Builder, apiKey: String) {
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
        }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
    }

    override suspend fun fetchModels(
        config: ProviderConfig,
        apiKey: String
    ): Result<List<ProviderModel>> = withContext(Dispatchers.IO) {
        if (!metadata.supportsModelsEndpoint) {
            return@withContext Result.failure(
                UnsupportedOperationException("${metadata.displayName} does not expose a /models endpoint. Enter a model ID manually.")
            )
        }
        validateCredentials(config, apiKey)?.let {
            return@withContext Result.failure(IllegalArgumentException(it))
        }
        val baseUrl = resolveBaseUrl(config).getOrElse {
            return@withContext Result.failure(it)
        }

        val reqBuilder = Request.Builder()
            .url(modelsEndpoint(baseUrl))
            .get()
            .header("Accept", "application/json")
        applyAuthHeaders(reqBuilder, apiKey)

        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            formatHttpError(
                                code = response.code,
                                body = rawBody,
                                retryAfter = response.header("Retry-After")
                            )
                        )
                    )
                }
                val models = parseModelsResponse(rawBody)
                if (models.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Endpoint returned 0 models. You can enter a model ID manually.")
                    )
                }
                Result.success(models)
            }
        } catch (e: IOException) {
            Result.failure(IllegalStateException(formatNetworkError(e)))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to parse models: ${e.message}"))
        }
    }

    protected open fun parseModelsResponse(rawBody: String): List<ProviderModel> {
        val root = json.parseToJsonElement(rawBody)
        val items: JsonArray = when {
            root is JsonObject && root["data"] is JsonArray -> root["data"]!!.jsonArray
            root is JsonObject && root["models"] is JsonArray -> root["models"]!!.jsonArray
            root is JsonArray -> root
            else -> return emptyList()
        }

        return items.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.content.takeIf { it.isNotBlank() }?.let { id ->
                    ProviderModel(
                        id = id,
                        displayName = id,
                        supportsToolCalling = modelToolSupport(id)
                    )
                }
                is JsonObject -> {
                    val id = el["id"]?.jsonPrimitive?.content
                        ?: el["name"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    val cleanId = id.removePrefix("models/")
                    ProviderModel(
                        id = cleanId,
                        displayName = cleanId,
                        supportsToolCalling = modelToolSupport(cleanId)
                    )
                }
                else -> null
            }
        }.distinctBy { it.id }.sortedBy { it.id.lowercase() }
    }

    override suspend fun testConnection(
        config: ProviderConfig,
        apiKey: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        validateCredentials(config, apiKey)?.let {
            return@withContext ConnectionTestResult(false, it)
        }
        val baseUrl = resolveBaseUrl(config).getOrElse {
            return@withContext ConnectionTestResult(false, it.message ?: "Invalid base URL")
        }

        val start = System.currentTimeMillis()
        if (metadata.supportsModelsEndpoint) {
            val modelsRes = fetchModels(config, apiKey)
            val latency = System.currentTimeMillis() - start
            if (modelsRes.isSuccess) {
                val count = modelsRes.getOrNull()?.size ?: 0
                return@withContext ConnectionTestResult(
                    success = true,
                    message = "Connected (${latency}ms) — $count model(s) available.",
                    latencyMs = latency,
                    modelsCount = count
                )
            }
            val errMsg = modelsRes.exceptionOrNull()?.message.orEmpty()
            if (config.selectedModel.isBlank() ||
                errMsg.contains("401") ||
                errMsg.contains("403")
            ) {
                return@withContext ConnectionTestResult(false, errMsg, latency)
            }
        }

        val probeModel = config.selectedModel.ifBlank { config.deploymentName }
        if (probeModel.isBlank()) {
            return@withContext ConnectionTestResult(
                success = false,
                message = "Enter a model ID first to test ${metadata.displayName}."
            )
        }

        val probeBody = buildJsonObject {
            put("model", probeModel)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", "ping")
                })
            }
            put("max_tokens", 1)
            put("stream", false)
        }.toString()

        val reqBuilder = Request.Builder()
            .url(chatCompletionsEndpoint(baseUrl))
            .post(probeBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Content-Type", "application/json")
        applyAuthHeaders(reqBuilder, apiKey)

        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    ConnectionTestResult(
                        success = true,
                        message = "Connected (${latency}ms) — model '$probeModel' responded.",
                        latencyMs = latency
                    )
                } else {
                    ConnectionTestResult(
                        success = false,
                        message = formatHttpError(
                            response.code,
                            body,
                            response.header("Retry-After")
                        ),
                        latencyMs = latency
                    )
                }
            }
        } catch (e: IOException) {
            ConnectionTestResult(false, formatNetworkError(e))
        }
    }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamEvent> = callbackFlow {
        var activeCall: Call? = null
        val credentialErr = validateCredentials(config, apiKey)
        if (credentialErr != null) {
            trySend(StreamEvent.Error(credentialErr))
            close()
            return@callbackFlow
        }
        val baseUrl = resolveBaseUrl(config).getOrElse {
            trySend(StreamEvent.Error(it.message ?: "Invalid Base URL"))
            close()
            return@callbackFlow
        }
        if (request.model.isBlank()) {
            trySend(StreamEvent.Error("No model selected for ${metadata.displayName}."))
            close()
            return@callbackFlow
        }

        val url = chatCompletionsEndpoint(baseUrl)
        val allowTools = request.tools.isNotEmpty() &&
            modelToolSupport(request.model) != ToolSupportStatus.UNSUPPORTED

        fun executeStream(withStreamOptions: Boolean): Int {
            val payload = buildRequestPayload(request, allowTools, withStreamOptions)
            val reqBuilder = Request.Builder()
                .url(url)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
            applyAuthHeaders(reqBuilder, apiKey)

            val call = okHttpClient.newCall(reqBuilder.build())
            activeCall = call

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    if (response.code == 400 &&
                        withStreamOptions &&
                        errorBody.contains("stream_options", ignoreCase = true)
                    ) {
                        return 400
                    }
                    trySend(
                        StreamEvent.Error(
                            message = formatHttpError(
                                code = response.code,
                                body = errorBody,
                                retryAfter = response.header("Retry-After")
                            ),
                            httpCode = response.code,
                            retryAfterSeconds = response.header("Retry-After")?.toIntOrNull()
                        )
                    )
                    return response.code
                }

                val source = response.body?.source()
                if (source == null) {
                    trySend(StreamEvent.Error("Empty response stream from ${metadata.displayName}."))
                    return 500
                }
                val toolAccumulators = linkedMapOf<Int, MutableToolAccumulator>()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                    if (!trimmed.startsWith("data:")) continue

                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val chunk = runCatching { json.parseToJsonElement(data).jsonObject }
                        .getOrNull() ?: continue

                    chunk["error"]?.let { errEl ->
                        val msg = (errEl as? JsonObject)?.get("message")?.jsonPrimitive?.content
                            ?: errEl.toString()
                        trySend(StreamEvent.Error(msg))
                        return 500
                    }

                    chunk["usage"]?.takeIf { it is JsonObject }?.jsonObject?.let { usageObj ->
                        val prompt = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                        val completion = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                        val total = usageObj["total_tokens"]?.jsonPrimitive?.intOrNull
                            ?: (prompt + completion)
                        if (prompt > 0 || completion > 0 || total > 0) {
                            trySend(StreamEvent.UsageReported(TokenUsage(prompt, completion, total)))
                        }
                    }

                    val choices = chunk["choices"] as? JsonArray ?: continue
                    val firstChoice = choices.firstOrNull() as? JsonObject ?: continue
                    val delta = firstChoice["delta"] as? JsonObject ?: continue

                    delta["reasoning_content"]?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { trySend(StreamEvent.ReasoningDelta(it)) }

                    delta["content"]?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { trySend(StreamEvent.TextDelta(it)) }

                    val deltaToolCalls = delta["tool_calls"] as? JsonArray
                    if (deltaToolCalls != null) {
                        for (tcEl in deltaToolCalls) {
                            val tcObj = tcEl as? JsonObject ?: continue
                            val idx = tcObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val acc = toolAccumulators.getOrPut(idx) { MutableToolAccumulator() }
                            tcObj["id"]?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
                                ?.takeIf { it.isNotBlank() }?.let { acc.id = it }
                            val fn = tcObj["function"] as? JsonObject
                            fn?.get("name")?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
                                ?.takeIf { it.isNotBlank() }?.let { acc.name += it }
                            fn?.get("arguments")?.takeIf { it is JsonPrimitive }?.jsonPrimitive?.content
                                ?.let { acc.arguments.append(it) }
                        }
                    }
                }

                toolAccumulators.entries.sortedBy { it.key }.forEach { (idx, acc) ->
                    if (acc.name.isNotBlank()) {
                        trySend(
                            StreamEvent.ToolCallCompleted(
                                ToolCallDto(
                                    id = acc.id.ifBlank { "call_${System.currentTimeMillis()}_$idx" },
                                    name = acc.name,
                                    argumentsJson = acc.arguments.toString().ifBlank { "{}" }
                                )
                            )
                        )
                    }
                }
                trySend(StreamEvent.Done)
                return 200
            }
        }

        try {
            val status = executeStream(includeStreamUsageOption)
            if (status == 400 && includeStreamUsageOption) {
                executeStream(withStreamOptions = false)
            }
        } catch (e: IOException) {
            if (activeCall?.isCanceled() != true) {
                trySend(StreamEvent.Error(formatNetworkError(e)))
            }
        } catch (e: Exception) {
            trySend(StreamEvent.Error("Unexpected error: ${e.message ?: e::class.simpleName}"))
        }

        close()
        awaitClose {
            activeCall?.cancel()
        }
    }.flowOn(Dispatchers.IO)

    protected open fun buildRequestPayload(
        request: ChatCompletionRequest,
        allowTools: Boolean,
        withStreamOptions: Boolean
    ): String = buildJsonObject {
        put("model", request.model)
        put("stream", true)
        put("temperature", request.temperature)
        put("max_tokens", request.maxTokens)

        if (withStreamOptions) {
            putJsonObject("stream_options") {
                put("include_usage", true)
            }
        }

        putJsonArray("messages") {
            for (msg in request.messages) {
                add(buildJsonObject {
                    when (msg.role) {
                        ChatRole.SYSTEM -> {
                            put("role", "system")
                            put("content", msg.content)
                        }
                        ChatRole.USER -> {
                            put("role", "user")
                            put("content", msg.content)
                        }
                        ChatRole.ASSISTANT -> {
                            put("role", "assistant")
                            if (msg.content.isNotEmpty() || msg.toolCalls.isEmpty()) {
                                put("content", msg.content)
                            }
                            if (msg.toolCalls.isNotEmpty()) {
                                putJsonArray("tool_calls") {
                                    for (tc in msg.toolCalls) {
                                        add(buildJsonObject {
                                            put("id", tc.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", tc.name)
                                                put("arguments", tc.argumentsJson)
                                            }
                                        })
                                    }
                                }
                            }
                        }
                        ChatRole.TOOL -> {
                            put("role", "tool")
                            put("tool_call_id", msg.toolCallId ?: "")
                            if (!msg.toolName.isNullOrBlank()) {
                                put("name", msg.toolName)
                            }
                            put("content", msg.content)
                        }
                    }
                })
            }
        }

        if (allowTools) {
            putJsonArray("tools") {
                for (tool in request.tools) {
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parametersSchema)
                        }
                    })
                }
            }
            put("tool_choice", "auto")
        }
    }.toString()

    protected fun formatHttpError(code: Int, body: String, retryAfter: String?): String {
        val apiMsg = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            (obj["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content
                ?: obj["message"]?.jsonPrimitive?.content
                ?: obj["detail"]?.jsonPrimitive?.content
        }.getOrNull()?.take(260)

        val detail = apiMsg?.let { ": $it" } ?: if (body.isNotBlank()) ": ${body.take(180)}" else ""
        return when (code) {
            401, 403 -> "Invalid or unauthorized API key (HTTP $code)$detail"
            404 -> "Endpoint or model not found (HTTP 404). Check your base URL and model ID$detail"
            429 -> {
                val retryHint = retryAfter?.let { " Retry after ${it}s." }
                    ?: " Wait briefly before retrying."
                "Rate limit exceeded (HTTP 429).$retryHint$detail"
            }
            400, 422 -> {
                val toolHint = if (detail.contains("tool", ignoreCase = true) ||
                    detail.contains("function", ignoreCase = true)
                ) {
                    " Note: This model may not support tool calling."
                } else ""
                "Bad request (HTTP $code)$detail$toolHint"
            }
            else -> "HTTP $code from ${metadata.displayName}$detail"
        }
    }

    protected fun formatNetworkError(e: IOException): String = when (e) {
        is UnknownHostException ->
            "DNS lookup failed for ${metadata.displayName}. Check your internet connection and base URL."
        is SocketTimeoutException ->
            "Request timed out contacting ${metadata.displayName}. Try again or check server load."
        is ConnectException ->
            if (metadata.category == ProviderCategory.LOCAL) {
                "Could not connect to local server (${e.message}). Ensure Ollama is running and reachable."
            } else {
                "Connection refused by ${metadata.displayName}: ${e.message}"
            }
        else -> "Network error: ${e.message ?: e::class.simpleName}"
    }

    private data class MutableToolAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
