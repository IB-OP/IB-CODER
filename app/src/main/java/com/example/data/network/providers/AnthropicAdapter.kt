package com.example.data.network.providers

import com.example.data.network.AiProviderAdapter
import com.example.domain.model.ChatCompletionRequest
import com.example.domain.model.ChatRole
import com.example.domain.model.ConnectionTestResult
import com.example.domain.model.ProviderCategory
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ProviderMetadata
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException

class AnthropicAdapter(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : AiProviderAdapter {

    override val metadata = ProviderMetadata(
        id = "anthropic",
        displayName = "Anthropic (Claude)",
        category = ProviderCategory.PAID_POPULAR,
        defaultBaseUrl = "https://api.anthropic.com/v1",
        description = "Native Anthropic Messages API with streaming and tool use.",
        signupUrl = "https://console.anthropic.com/settings/keys"
    )

    override suspend fun fetchModels(
        config: ProviderConfig,
        apiKey: String
    ): Result<List<ProviderModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Anthropic API key is required."))
        }
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Base URL cannot be empty."))
        }

        val req = Request.Builder()
            .url("$baseUrl/models")
            .get()
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", "application/json")
            .build()

        try {
            okHttpClient.newCall(req).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            formatAnthropicError(response.code, raw, response.header("Retry-After"))
                        )
                    )
                }
                val root = json.parseToJsonElement(raw).jsonObject
                val data = root["data"] as? JsonArray ?: JsonArray(emptyList())
                val models = data.mapNotNull { el ->
                    val obj = el as? JsonObject ?: return@mapNotNull null
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val display = obj["display_name"]?.jsonPrimitive?.content ?: id
                    ProviderModel(
                        id = id,
                        displayName = display,
                        supportsToolCalling = modelToolSupport(id)
                    )
                }.sortedBy { it.id }

                if (models.isEmpty()) {
                    Result.failure(IllegalStateException("No models returned from Anthropic /models."))
                } else {
                    Result.success(models)
                }
            }
        } catch (e: IOException) {
            Result.failure(IllegalStateException(formatIoError(e)))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to parse Anthropic models: ${e.message}"))
        }
    }

    override suspend fun testConnection(
        config: ProviderConfig,
        apiKey: String
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val res = fetchModels(config, apiKey)
        val latency = System.currentTimeMillis() - start
        if (res.isSuccess) {
            val count = res.getOrNull()?.size ?: 0
            ConnectionTestResult(
                success = true,
                message = "Connected to Anthropic (${latency}ms) — $count model(s) listed.",
                latencyMs = latency,
                modelsCount = count
            )
        } else {
            ConnectionTestResult(
                success = false,
                message = res.exceptionOrNull()?.message ?: "Connection failed.",
                latencyMs = latency
            )
        }
    }

    override fun streamChat(
        request: ChatCompletionRequest,
        config: ProviderConfig,
        apiKey: String
    ): Flow<StreamEvent> = callbackFlow {
        var activeCall: Call? = null
        if (apiKey.isBlank()) {
            trySend(StreamEvent.Error("Anthropic API key is required."))
            close()
            return@callbackFlow
        }
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        if (baseUrl.isEmpty() || request.model.isBlank()) {
            trySend(StreamEvent.Error("Valid Base URL and model ID are required for Anthropic."))
            close()
            return@callbackFlow
        }

        val payload = buildAnthropicPayload(request)
        val req = Request.Builder()
            .url("$baseUrl/messages")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .build()

        val call = okHttpClient.newCall(req)
        activeCall = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    trySend(
                        StreamEvent.Error(
                            message = formatAnthropicError(
                                response.code,
                                errBody,
                                response.header("Retry-After")
                            ),
                            httpCode = response.code,
                            retryAfterSeconds = response.header("Retry-After")?.toIntOrNull()
                        )
                    )
                    close()
                    return@callbackFlow
                }

                val source = response.body?.source()
                if (source == null) {
                    trySend(StreamEvent.Error("Empty response stream from Anthropic."))
                    close()
                    return@callbackFlow
                }

                var currentEventType = ""
                var inputTokens = 0
                var outputTokens = 0
                val activeToolBlocks = mutableMapOf<Int, AnthropicToolAccumulator>()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (trimmed.startsWith("event:")) {
                        currentEventType = trimmed.removePrefix("event:").trim()
                        continue
                    }
                    if (!trimmed.startsWith("data:")) continue

                    val data = trimmed.removePrefix("data:").trim()
                    val obj = runCatching { json.parseToJsonElement(data).jsonObject }
                        .getOrNull() ?: continue

                    val eventType = obj["type"]?.jsonPrimitive?.content ?: currentEventType
                    when (eventType) {
                        "message_start" -> {
                            val usage = obj["message"]?.jsonObject?.get("usage") as? JsonObject
                            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: inputTokens
                        }
                        "content_block_start" -> {
                            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val block = obj["content_block"] as? JsonObject
                            if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                                val id = block["id"]?.jsonPrimitive?.content
                                    ?: "toolu_${System.currentTimeMillis()}_$index"
                                val name = block["name"]?.jsonPrimitive?.content ?: ""
                                activeToolBlocks[index] = AnthropicToolAccumulator(id = id, name = name)
                            }
                        }
                        "content_block_delta" -> {
                            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val delta = obj["delta"] as? JsonObject ?: continue
                            when (delta["type"]?.jsonPrimitive?.content) {
                                "text_delta" -> {
                                    delta["text"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                                        ?.let { trySend(StreamEvent.TextDelta(it)) }
                                }
                                "thinking_delta" -> {
                                    delta["thinking"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
                                        ?.let { trySend(StreamEvent.ReasoningDelta(it)) }
                                }
                                "input_json_delta" -> {
                                    val partial = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                    activeToolBlocks[index]?.inputJson?.append(partial)
                                }
                            }
                        }
                        "content_block_stop" -> {
                            val index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0
                            activeToolBlocks.remove(index)?.let { acc ->
                                if (acc.name.isNotBlank()) {
                                    trySend(
                                        StreamEvent.ToolCallCompleted(
                                            ToolCallDto(
                                                id = acc.id,
                                                name = acc.name,
                                                argumentsJson = acc.inputJson.toString().ifBlank { "{}" }
                                            )
                                        )
                                    )
                                }
                            }
                        }
                        "message_delta" -> {
                            val usage = obj["usage"] as? JsonObject
                            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: outputTokens
                        }
                        "error" -> {
                            val errObj = obj["error"] as? JsonObject
                            val msg = errObj?.get("message")?.jsonPrimitive?.content
                                ?: "Anthropic stream error"
                            trySend(StreamEvent.Error(msg))
                        }
                        "message_stop" -> break
                    }
                }

                if (inputTokens > 0 || outputTokens > 0) {
                    trySend(
                        StreamEvent.UsageReported(
                            TokenUsage(
                                promptTokens = inputTokens,
                                completionTokens = outputTokens,
                                totalTokens = inputTokens + outputTokens
                            )
                        )
                    )
                }
                trySend(StreamEvent.Done)
            }
        } catch (e: IOException) {
            if (activeCall?.isCanceled() != true) {
                trySend(StreamEvent.Error(formatIoError(e)))
            }
        } catch (e: Exception) {
            trySend(StreamEvent.Error("Anthropic error: ${e.message ?: e::class.simpleName}"))
        }

        close()
        awaitClose { activeCall?.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun buildAnthropicPayload(request: ChatCompletionRequest): String {
        val systemText = request.messages
            .filter { it.role == ChatRole.SYSTEM }
            .joinToString("\n\n") { it.content }

        val nonSystem = request.messages.filter { it.role != ChatRole.SYSTEM }
        val anthropicMessages = mutableListOf<JsonObject>()
        var i = 0
        while (i < nonSystem.size) {
            val msg = nonSystem[i]
            when (msg.role) {
                ChatRole.USER -> {
                    anthropicMessages += buildJsonObject {
                        put("role", "user")
                        put("content", msg.content)
                    }
                    i++
                }
                ChatRole.ASSISTANT -> {
                    anthropicMessages += buildJsonObject {
                        put("role", "assistant")
                        putJsonArray("content") {
                            if (msg.content.isNotBlank()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", msg.content)
                                })
                            }
                            for (tc in msg.toolCalls) {
                                val parsedInput = runCatching {
                                    json.parseToJsonElement(tc.argumentsJson).jsonObject
                                }.getOrDefault(JsonObject(emptyMap()))
                                add(buildJsonObject {
                                    put("type", "tool_use")
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("input", parsedInput)
                                })
                            }
                            if (msg.content.isBlank() && msg.toolCalls.isEmpty()) {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", "")
                                })
                            }
                        }
                    }
                    i++
                }
                ChatRole.TOOL -> {
                    val toolResults = mutableListOf<JsonObject>()
                    while (i < nonSystem.size && nonSystem[i].role == ChatRole.TOOL) {
                        val toolMsg = nonSystem[i]
                        toolResults += buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", toolMsg.toolCallId ?: "")
                            put("content", toolMsg.content)
                        }
                        i++
                    }
                    anthropicMessages += buildJsonObject {
                        put("role", "user")
                        put("content", JsonArray(toolResults))
                    }
                }
                ChatRole.SYSTEM -> i++
            }
        }

        val allowTools = request.tools.isNotEmpty() &&
            modelToolSupport(request.model) != ToolSupportStatus.UNSUPPORTED

        return buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("stream", true)
            if (systemText.isNotBlank()) {
                put("system", systemText)
            }
            put("messages", JsonArray(anthropicMessages))
            if (allowTools) {
                putJsonArray("tools") {
                    for (tool in request.tools) {
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parametersSchema)
                        })
                    }
                }
            }
        }.toString()
    }

    private fun formatAnthropicError(code: Int, body: String, retryAfter: String?): String {
        val msg = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            (root["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        val detail = msg?.let { ": $it" } ?: if (body.isNotBlank()) ": ${body.take(180)}" else ""
        return when (code) {
            401, 403 -> "Invalid Anthropic API key (HTTP $code)$detail"
            429 -> "Anthropic rate limit exceeded (HTTP 429). ${retryAfter?.let { "Retry after ${it}s." } ?: "Wait briefly before retrying."}$detail"
            404 -> "Anthropic model or endpoint not found (HTTP 404)$detail"
            else -> "Anthropic HTTP $code$detail"
        }
    }

    private fun formatIoError(e: IOException): String = when (e) {
        is UnknownHostException -> "Cannot reach api.anthropic.com. Check your internet connection."
        else -> "Network error talking to Anthropic: ${e.message ?: e::class.simpleName}"
    }

    private data class AnthropicToolAccumulator(
        val id: String,
        val name: String,
        val inputJson: StringBuilder = StringBuilder()
    )

    companion object {
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
