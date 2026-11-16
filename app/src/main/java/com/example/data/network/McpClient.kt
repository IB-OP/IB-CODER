package com.example.data.network

import com.example.BuildConfig
import com.example.data.preferences.SettingsDataStore
import com.example.domain.model.McpServerConfig
import com.example.domain.model.McpToolInfo
import com.example.domain.model.McpTransport
import com.example.domain.model.ResolvedMcpTool
import com.example.domain.model.ToolDefinition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class McpSyncResult(
    val serverInfoName: String,
    val protocolVersion: String,
    val tools: List<McpToolInfo>
)

class McpClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val idCounter = AtomicInteger(1)

    suspend fun syncServer(server: McpServerConfig, authToken: String): Result<McpSyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = server.endpointUrl.trim()
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "MCP server URL must start with http:// or https://"
                }

                when (server.transport) {
                    McpTransport.STREAMABLE_HTTP -> syncViaStreamableHttp(server, authToken)
                    McpTransport.LEGACY_SSE -> syncViaLegacySse(server, authToken)
                    McpTransport.AUTO -> {
                        val streamResult = runCatching { syncViaStreamableHttp(server, authToken) }
                        streamResult.getOrElse { err ->
                            val msg = err.message.orEmpty()
                            val shouldFallback = msg.contains("404") ||
                                msg.contains("405") ||
                                msg.contains("406") ||
                                url.endsWith("/sse", ignoreCase = true)
                            if (shouldFallback) {
                                syncViaLegacySse(server, authToken)
                            } else {
                                throw err
                            }
                        }
                    }
                }
            }
        }

    suspend fun callTool(
        server: McpServerConfig,
        authToken: String,
        toolName: String,
        argumentsJson: String
    ): String = withContext(Dispatchers.IO) {
        val argsObj = runCatching {
            if (argumentsJson.isBlank()) JsonObject(emptyMap())
            else json.parseToJsonElement(argumentsJson).jsonObject
        }.getOrElse { JsonObject(emptyMap()) }

        when (server.transport) {
            McpTransport.STREAMABLE_HTTP -> callViaStreamableHttp(server, authToken, toolName, argsObj)
            McpTransport.LEGACY_SSE -> callViaLegacySse(server, authToken, toolName, argsObj)
            McpTransport.AUTO -> {
                val attempt = runCatching { callViaStreamableHttp(server, authToken, toolName, argsObj) }
                attempt.getOrElse { err ->
                    val msg = err.message.orEmpty()
                    if (msg.contains("404") || msg.contains("405") || msg.contains("406") ||
                        server.endpointUrl.trim().endsWith("/sse", ignoreCase = true)
                    ) {
                        callViaLegacySse(server, authToken, toolName, argsObj)
                    } else {
                        throw err
                    }
                }
            }
        }
    }

    private fun syncViaStreamableHttp(server: McpServerConfig, authToken: String): McpSyncResult {
        var sessionId: String? = null
        val initId = idCounter.getAndIncrement()
        val initReq = buildInitializeJson(initId)
        val (initResp, newSessionId) = postStreamableJsonRpc(server, authToken, sessionId, initReq, initId)
        sessionId = newSessionId ?: sessionId

        val initResult = extractResultOrThrow(initResp, "initialize")
        val protocolVersion = initResult["protocolVersion"]?.jsonPrimitive?.content ?: "2025-03-26"
        val serverInfoName = initResult["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()

        // Send notifications/initialized (best-effort)
        runCatching {
            postNotification(server, authToken, sessionId, buildInitializedNotificationJson())
        }

        val allTools = mutableListOf<McpToolInfo>()
        var cursor: String? = null
        var guard = 0
        do {
            guard++
            val listId = idCounter.getAndIncrement()
            val listReq = buildToolsListJson(listId, cursor)
            val (listResp, sid) = postStreamableJsonRpc(server, authToken, sessionId, listReq, listId)
            sessionId = sid ?: sessionId
            val listResult = extractResultOrThrow(listResp, "tools/list")
            allTools += parseTools(listResult)
            cursor = listResult["nextCursor"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } while (cursor != null && guard < 10)

        return McpSyncResult(
            serverInfoName = serverInfoName.ifBlank { server.name },
            protocolVersion = protocolVersion,
            tools = allTools
        )
    }

    private fun callViaStreamableHttp(
        server: McpServerConfig,
        authToken: String,
        toolName: String,
        argsObj: JsonObject
    ): String {
        var sessionId: String? = null
        val initId = idCounter.getAndIncrement()
        val (initResp, newSessionId) = postStreamableJsonRpc(
            server,
            authToken,
            null,
            buildInitializeJson(initId),
            initId
        )
        sessionId = newSessionId
        extractResultOrThrow(initResp, "initialize")

        runCatching {
            postNotification(server, authToken, sessionId, buildInitializedNotificationJson())
        }

        val callId = idCounter.getAndIncrement()
        val callPayload = buildToolCallJson(callId, toolName, argsObj)
        val (callResp, _) = postStreamableJsonRpc(server, authToken, sessionId, callPayload, callId)
        val callResult = extractResultOrThrow(callResp, "tools/call:$toolName")
        return formatToolCallResult(callResult)
    }

    private suspend fun syncViaLegacySse(server: McpServerConfig, authToken: String): McpSyncResult {
        val sse = LegacySseSession(server, authToken)
        return try {
            sse.connect()
            val initId = idCounter.getAndIncrement()
            val initResp = sse.request(initId, buildInitializeJson(initId))
            val initResult = extractResultOrThrow(initResp, "initialize")
            val protocolVersion = initResult["protocolVersion"]?.jsonPrimitive?.content ?: "2024-11-05"
            val serverInfoName = initResult["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content.orEmpty()

            runCatching { sse.notify(buildInitializedNotificationJson()) }

            val allTools = mutableListOf<McpToolInfo>()
            var cursor: String? = null
            var guard = 0
            do {
                guard++
                val listId = idCounter.getAndIncrement()
                val listResp = sse.request(listId, buildToolsListJson(listId, cursor))
                val listResult = extractResultOrThrow(listResp, "tools/list")
                allTools += parseTools(listResult)
                cursor = listResult["nextCursor"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            } while (cursor != null && guard < 10)

            McpSyncResult(
                serverInfoName = serverInfoName.ifBlank { server.name },
                protocolVersion = protocolVersion,
                tools = allTools
            )
        } finally {
            sse.close()
        }
    }

    private suspend fun callViaLegacySse(
        server: McpServerConfig,
        authToken: String,
        toolName: String,
        argsObj: JsonObject
    ): String {
        val sse = LegacySseSession(server, authToken)
        return try {
            sse.connect()
            val initId = idCounter.getAndIncrement()
            val initResp = sse.request(initId, buildInitializeJson(initId))
            extractResultOrThrow(initResp, "initialize")

            runCatching { sse.notify(buildInitializedNotificationJson()) }

            val callId = idCounter.getAndIncrement()
            val callResp = sse.request(callId, buildToolCallJson(callId, toolName, argsObj))
            val callResult = extractResultOrThrow(callResp, "tools/call:$toolName")
            formatToolCallResult(callResult)
        } finally {
            sse.close()
        }
    }

    private inner class LegacySseSession(
        private val server: McpServerConfig,
        private val authToken: String
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val endpointDeferred = CompletableDeferred<String>()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        private var sseCall: okhttp3.Call? = null
        private var readerJob: kotlinx.coroutines.Job? = null
        private var messageEndpointUrl: String = ""

        suspend fun connect() {
            val req = applyAuthHeader(
                Request.Builder()
                    .url(server.endpointUrl.trim())
                    .header("Accept", "text/event-stream")
                    .header("Cache-Control", "no-cache")
                    .get(),
                server,
                authToken
            ).build()

            val call = okHttpClient.newCall(req)
            sseCall = call
            readerJob = scope.launch {
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            val body = response.body?.string().orEmpty().take(240)
                            val err = IOException("MCP SSE HTTP ${response.code}: $body")
                            endpointDeferred.completeExceptionally(err)
                            failAllPending(err)
                            return@use
                        }
                        val source = response.body?.source() ?: run {
                            val err = IOException("Empty MCP SSE body")
                            endpointDeferred.completeExceptionally(err)
                            failAllPending(err)
                            return@use
                        }

                        var currentEvent = "message"
                        val dataBuf = StringBuilder()

                        fun dispatchEvent() {
                            val payload = dataBuf.toString().trim()
                            dataBuf.setLength(0)
                            val evtName = currentEvent
                            currentEvent = "message"
                            if (payload.isEmpty()) return

                            if (evtName == "endpoint") {
                                val resolved = resolveRelativeUrl(server.endpointUrl.trim(), payload)
                                if (!endpointDeferred.isCompleted) {
                                    endpointDeferred.complete(resolved)
                                }
                                return
                            }

                            runCatching {
                                val obj = json.parseToJsonElement(payload).jsonObject
                                val msgId = obj["id"]?.jsonPrimitive?.intOrNull
                                if (msgId != null) {
                                    pending.remove(msgId)?.complete(obj)
                                }
                            }
                        }

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> dispatchEvent()
                                line.startsWith("event:") -> currentEvent = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> {
                                    if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                                    dataBuf.append(line.removePrefix("data:").trimStart())
                                }
                            }
                        }
                        if (dataBuf.isNotEmpty()) dispatchEvent()
                    }
                } catch (e: Exception) {
                    if (!endpointDeferred.isCompleted) endpointDeferred.completeExceptionally(e)
                    failAllPending(e)
                }
            }

            messageEndpointUrl = withTimeout(12_000) { endpointDeferred.await() }
        }

        suspend fun request(id: Int, payload: String): JsonObject {
            val deferred = CompletableDeferred<JsonObject>()
            pending[id] = deferred

            val postReq = applyAuthHeader(
                Request.Builder()
                    .url(messageEndpointUrl)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .post(payload.toRequestBody(jsonMediaType)),
                server,
                authToken
            ).build()

            okHttpClient.newCall(postReq).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    pending.remove(id)
                    error("MCP POST HTTP ${response.code}: ${body.take(240)}")
                }
                if (body.trimStart().startsWith("{")) {
                    val immediate = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    if (immediate != null && immediate["id"]?.jsonPrimitive?.intOrNull == id) {
                        pending.remove(id)
                        return immediate
                    }
                }
            }

            return withTimeout(45_000) { deferred.await() }
        }

        fun notify(payload: String) {
            val postReq = applyAuthHeader(
                Request.Builder()
                    .url(messageEndpointUrl)
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType)),
                server,
                authToken
            ).build()
            okHttpClient.newCall(postReq).execute().close()
        }

        private fun failAllPending(err: Throwable) {
            pending.values.forEach { it.completeExceptionally(err) }
            pending.clear()
        }

        suspend fun close() {
            sseCall?.cancel()
            readerJob?.cancelAndJoin()
        }
    }

    private fun postStreamableJsonRpc(
        server: McpServerConfig,
        authToken: String,
        sessionId: String?,
        payload: String,
        expectedId: Int
    ): Pair<JsonObject, String?> {
        val builder = Request.Builder()
            .url(server.endpointUrl.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toRequestBody(jsonMediaType))

        if (!sessionId.isNullOrBlank()) {
            builder.header("Mcp-Session-Id", sessionId)
        }
        applyAuthHeader(builder, server, authToken)

        okHttpClient.newCall(builder.build()).execute().use { response ->
            val returnedSessionId = response.header("Mcp-Session-Id")
            val contentType = response.header("Content-Type").orEmpty().lowercase()

            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                error("HTTP ${response.code}: ${errBody.take(300)}")
            }

            if (contentType.contains("text/event-stream")) {
                val source = response.body?.source() ?: error("Empty SSE response from MCP server.")
                val dataBuf = StringBuilder()
                var matched: JsonObject? = null
                var lastObj: JsonObject? = null

                fun checkPayload() {
                    val raw = dataBuf.toString().trim()
                    dataBuf.setLength(0)
                    if (raw.isEmpty() || raw == "[DONE]") return
                    runCatching {
                        val obj = json.parseToJsonElement(raw).jsonObject
                        lastObj = obj
                        if (obj["id"]?.jsonPrimitive?.intOrNull == expectedId) {
                            matched = obj
                        }
                    }
                }

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        checkPayload()
                        if (matched != null) break
                    } else if (line.startsWith("data:")) {
                        if (dataBuf.isNotEmpty()) dataBuf.append('\n')
                        dataBuf.append(line.removePrefix("data:").trimStart())
                    }
                }
                if (matched == null && dataBuf.isNotEmpty()) checkPayload()
                val obj = matched ?: lastObj ?: error("MCP SSE stream ended without a JSON-RPC response.")
                return obj to returnedSessionId
            }

            val body = response.body?.string().orEmpty().trim()
            if (body.isEmpty()) error("MCP server returned an empty body.")
            val obj = json.parseToJsonElement(body).jsonObject
            return obj to returnedSessionId
        }
    }

    private fun postNotification(
        server: McpServerConfig,
        authToken: String,
        sessionId: String?,
        payload: String
    ) {
        val builder = Request.Builder()
            .url(server.endpointUrl.trim())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(payload.toRequestBody(jsonMediaType))
        if (!sessionId.isNullOrBlank()) {
            builder.header("Mcp-Session-Id", sessionId)
        }
        applyAuthHeader(builder, server, authToken)
        okHttpClient.newCall(builder.build()).execute().close()
    }

    private fun applyAuthHeader(
        builder: Request.Builder,
        server: McpServerConfig,
        authToken: String
    ): Request.Builder {
        val token = authToken.trim()
        if (token.isNotEmpty()) {
            val headerName = server.authHeaderName.trim().ifBlank { "Authorization" }
            val headerValue = if (
                headerName.equals("Authorization", ignoreCase = true) &&
                !token.contains(' ')
            ) {
                "Bearer $token"
            } else {
                token
            }
            builder.header(headerName, headerValue)
        }
        return builder
    }

    private fun buildInitializeJson(id: Int): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", "initialize")
        putJsonObject("params") {
            put("protocolVersion", "2025-03-26")
            putJsonObject("capabilities") {}
            putJsonObject("clientInfo") {
                put("name", "IB CODER")
                put("version", BuildConfig.VERSION_NAME)
            }
        }
    }.toString()

    private fun buildInitializedNotificationJson(): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "notifications/initialized")
    }.toString()

    private fun buildToolsListJson(id: Int, cursor: String?): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", "tools/list")
        putJsonObject("params") {
            if (!cursor.isNullOrBlank()) put("cursor", cursor)
        }
    }.toString()

    private fun buildToolCallJson(id: Int, toolName: String, arguments: JsonObject): String =
        buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            putJsonObject("params") {
                put("name", toolName)
                put("arguments", arguments)
            }
        }.toString()

    private fun extractResultOrThrow(response: JsonObject, action: String): JsonObject {
        response["error"]?.let { errElem ->
            val errObj = runCatching { errElem.jsonObject }.getOrNull()
            val code = errObj?.get("code")?.jsonPrimitive?.intOrNull
            val message = errObj?.get("message")?.jsonPrimitive?.content ?: errElem.toString()
            error("MCP $action error${if (code != null) " ($code)" else ""}: $message")
        }
        return response["result"]?.jsonObject
            ?: error("MCP $action response did not contain a 'result' object.")
    }

    private fun parseTools(resultObj: JsonObject): List<McpToolInfo> {
        val arr = resultObj["tools"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull { elem ->
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val desc = obj["description"]?.jsonPrimitive?.content.orEmpty()
            val schema = normalizeSchema(obj["inputSchema"])
            McpToolInfo(name = name, description = desc, inputSchemaJson = schema)
        }
    }

    private fun normalizeSchema(element: JsonElement?): String {
        val obj = runCatching { element?.jsonObject }.getOrNull()
        if (obj == null) {
            return """{"type":"object","properties":{}}"""
        }
        return obj.toString()
    }

    private fun formatToolCallResult(resultObj: JsonObject): String {
        val isError = resultObj["isError"]?.jsonPrimitive?.booleanOrNull == true
        val contentArr = resultObj["content"]?.jsonArray
        val parts = contentArr?.mapNotNull { elem ->
            val obj = runCatching { elem.jsonObject }.getOrNull() ?: return@mapNotNull elem.toString()
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> obj["text"]?.jsonPrimitive?.content
                "resource" -> {
                    val res = obj["resource"]?.jsonObject
                    val uri = res?.get("uri")?.jsonPrimitive?.content.orEmpty()
                    val text = res?.get("text")?.jsonPrimitive?.content ?: res.toString()
                    if (uri.isNotBlank()) "[$uri]\n$text" else text
                }
                "image" -> "[Image returned (${obj["mimeType"]?.jsonPrimitive?.content ?: "image"})]"
                else -> obj.toString()
            }
        }?.filter { it.isNotBlank() }.orEmpty()

        val text = if (parts.isNotEmpty()) parts.joinToString("\n\n") else resultObj.toString()
        return if (isError) "MCP Tool Error:\n$text" else text
    }

    private fun resolveRelativeUrl(baseUrl: String, endpointPath: String): String {
        if (endpointPath.startsWith("http://") || endpointPath.startsWith("https://")) {
            return endpointPath
        }
        val base = baseUrl.toHttpUrlOrNull() ?: return endpointPath
        return base.resolve(endpointPath)?.toString() ?: endpointPath
    }
}

class McpManager(
    private val settingsDataStore: SettingsDataStore,
    private val mcpClient: McpClient,
    private val json: Json
) {
    suspend fun syncServer(serverId: String): Result<McpServerConfig> {
        val servers = settingsDataStore.getMcpServers()
        val server = servers.firstOrNull { it.id == serverId }
            ?: return Result.failure(IllegalArgumentException("MCP server not found."))
        val token = settingsDataStore.getMcpAuthToken(serverId)
        val syncResult = mcpClient.syncServer(server, token)
        return syncResult.fold(
            onSuccess = { synced ->
                val updated = server.copy(
                    discoveredTools = synced.tools,
                    serverInfoName = synced.serverInfoName,
                    protocolVersion = synced.protocolVersion,
                    lastCheckedAt = System.currentTimeMillis(),
                    lastError = null
                )
                settingsDataStore.saveMcpServer(updated)
                Result.success(updated)
            },
            onFailure = { err ->
                val updated = server.copy(
                    lastCheckedAt = System.currentTimeMillis(),
                    lastError = err.message ?: "Connection failed"
                )
                settingsDataStore.saveMcpServer(updated)
                Result.failure(err)
            }
        )
    }

    suspend fun getEnabledResolvedTools(): List<ResolvedMcpTool> {
        val servers = settingsDataStore.getMcpServers().filter { it.enabled }
        val usedNames = mutableSetOf<String>()
        val resolved = mutableListOf<ResolvedMcpTool>()

        for (server in servers) {
            val serverSlug = sanitizeSlug(server.name).ifBlank { "server" }.take(18)
            for (tool in server.discoveredTools) {
                val toolSlug = sanitizeSlug(tool.name).ifBlank { "tool" }.take(34)
                var candidate = "mcp__${serverSlug}__${toolSlug}".take(64)
                var suffix = 2
                while (!usedNames.add(candidate)) {
                    val tag = "_$suffix"
                    candidate = ("mcp__${serverSlug}__${toolSlug}").take(64 - tag.length) + tag
                    suffix++
                }
                val fullDesc = buildString {
                    append("[MCP: ${server.name}] ")
                    append(tool.description.ifBlank { "Execute MCP tool '${tool.name}'." })
                }
                resolved += ResolvedMcpTool(
                    exposedName = candidate,
                    serverId = server.id,
                    serverName = server.name,
                    originalToolName = tool.name,
                    description = fullDesc,
                    parametersSchemaJson = tool.inputSchemaJson
                )
            }
        }
        return resolved
    }

    fun toToolDefinitions(tools: List<ResolvedMcpTool>): List<ToolDefinition> {
        return tools.map { tool ->
            val schema = runCatching {
                json.parseToJsonElement(tool.parametersSchemaJson).jsonObject
            }.getOrElse {
                buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {}
                }
            }
            ToolDefinition(
                name = tool.exposedName,
                description = tool.description,
                parametersSchema = schema
            )
        }
    }

    suspend fun executeMcpTool(
        resolvedTool: ResolvedMcpTool,
        argumentsJson: String
    ): Pair<Boolean, String> {
        val server = settingsDataStore.getMcpServers().firstOrNull { it.id == resolvedTool.serverId }
            ?: return false to "MCP server '${resolvedTool.serverName}' is no longer configured."
        if (!server.enabled) {
            return false to "MCP server '${server.name}' is disabled."
        }
        val token = settingsDataStore.getMcpAuthToken(server.id)
        return try {
            val output = mcpClient.callTool(server, token, resolvedTool.originalToolName, argumentsJson)
            val isErr = output.startsWith("MCP Tool Error:")
            !isErr to output
        } catch (e: Exception) {
            false to "MCP tool '${resolvedTool.originalToolName}' (${server.name}) failed: ${e.message ?: e::class.simpleName}"
        }
    }

    private fun sanitizeSlug(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
    }
}
