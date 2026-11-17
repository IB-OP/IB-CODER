package com.example.data.network

import com.example.domain.model.ProviderApiDiscoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class InternetToolClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {

    suspend fun webSearch(
        query: String,
        maxResults: Int = 6
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Search query cannot be empty."))
        }
        val limit = maxResults.coerceIn(1, 12)

        // 1. Query DuckDuckGo HTML endpoint for live organic search results
        val htmlResults = runCatching { searchDuckDuckGoHtml(trimmed, limit) }.getOrDefault(emptyList())
        if (htmlResults.isNotEmpty()) {
            return@withContext Result.success(formatSearchResults(trimmed, htmlResults))
        }

        // 2. Fallback to DuckDuckGo Instant Answer JSON API
        val iaResults = runCatching { searchDuckDuckGoInstantAnswer(trimmed, limit) }.getOrDefault(emptyList())
        if (iaResults.isNotEmpty()) {
            return@withContext Result.success(formatSearchResults(trimmed, iaResults))
        }

        Result.success("No web results returned for '$trimmed'. Try a simpler or more specific query.")
    }

    suspend fun fetchUrl(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        raw: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val parsed = runCatching { URI(cleanUrl) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        if (parsed == null || (scheme != "http" && scheme != "https") || parsed.host.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid URL '$url'. Must start with http:// or https://.")
            )
        }

        val cleanMethod = method.trim().uppercase().ifBlank { "GET" }
        val reqBuilder = Request.Builder()
            .url(cleanUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/html, text/plain, */*;q=0.8")

        headers.forEach { (k, v) ->
            if (k.isNotBlank()) reqBuilder.header(k.trim(), v)
        }

        val requestBody = when (cleanMethod) {
            "GET", "HEAD" -> null
            else -> {
                val mediaType = (headers["Content-Type"] ?: headers["content-type"] ?: "application/json; charset=utf-8")
                    .toMediaTypeOrNull()
                (body ?: "").toRequestBody(mediaType)
            }
        }
        reqBuilder.method(cleanMethod, requestBody)

        try {
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                val rawBytes = readBoundedBytes(response.body?.byteStream(), MAX_FETCH_BYTES)
                val rawText = rawBytes.toString(StandardCharsets.UTF_8)
                val isHtml = contentType.contains("text/html", ignoreCase = true) ||
                    rawText.trimStart().startsWith("<!doctype html", ignoreCase = true) ||
                    rawText.trimStart().startsWith("<html", ignoreCase = true)

                val bodyOut = if (isHtml && !raw) {
                    htmlToReadableText(rawText)
                } else {
                    rawText
                }

                val truncated = if (bodyOut.length > MAX_RETURN_CHARS) {
                    bodyOut.take(MAX_RETURN_CHARS) + "\n\n...(truncated at $MAX_RETURN_CHARS chars)"
                } else {
                    bodyOut
                }

                Result.success(
                    buildString {
                        appendLine("URL: ${response.request.url}")
                        appendLine("Status: HTTP ${response.code} ${response.message}".trim())
                        if (contentType.isNotBlank()) appendLine("Content-Type: $contentType")
                        appendLine()
                        append(truncated.ifBlank { "(empty response body)" })
                    }.trimEnd()
                )
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Failed to fetch '$cleanUrl': ${e.message ?: e::class.simpleName}"))
        }
    }

    suspend fun discoverApiEndpoint(query: String): List<ProviderApiDiscoveryResult> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            val results = mutableListOf<ProviderApiDiscoveryResult>()

            if (q.isNotBlank()) {
                val lower = q.lowercase()
                KNOWN_PROVIDER_APIS.filter { entry ->
                    entry.name.lowercase().contains(lower) ||
                        entry.baseUrl.lowercase().contains(lower) ||
                        entry.note.lowercase().contains(lower)
                }.forEach { results += it }

                // If user pasted a direct URL/domain, offer OpenAI-style /v1 candidates cleanly
                if (lower.startsWith("http://") || lower.startsWith("https://") || lower.contains(".")) {
                    val normalized = if (q.startsWith("http://") || q.startsWith("https://")) {
                        q.trimEnd('/')
                    } else {
                        "https://${q.trim().trimEnd('/')}"
                    }
                    val v1Candidate = if (normalized.endsWith("/v1")) normalized else "$normalized/v1"
                    if (results.none { it.baseUrl.equals(v1Candidate, ignoreCase = true) }) {
                        results += ProviderApiDiscoveryResult(
                            name = parsedHostTitle(normalized),
                            baseUrl = v1Candidate,
                            docsUrl = normalized,
                            note = "Direct URL endpoint"
                        )
                    }
                }

                // Live web search to discover additional API endpoints / documentation URLs
                val liveHits = runCatching {
                    searchDuckDuckGoHtml("$q OpenAI compatible API base url v1", 5)
                }.getOrDefault(emptyList())

                for (hit in liveHits) {
                    val url = hit.url
                    val extractedV1 = extractApiBaseUrl(hit.snippet + " " + url)
                    val candidateBase = extractedV1 ?: guessApiBaseFromDocsUrl(url)
                    if (!candidateBase.isNullOrBlank() &&
                        results.none { it.baseUrl.equals(candidateBase, ignoreCase = true) }
                    ) {
                        results += ProviderApiDiscoveryResult(
                            name = hit.title.take(42),
                            baseUrl = candidateBase,
                            docsUrl = url,
                            note = hit.snippet.take(120)
                        )
                    }
                }
            } else {
                results += KNOWN_PROVIDER_APIS.take(8)
            }

            results.distinctBy { it.baseUrl.lowercase() }.take(10)
        }

    internal data class SearchHit(
        val title: String,
        val url: String,
        val snippet: String
    )

    private fun searchDuckDuckGoHtml(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .get()
            .build()

        val html = okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            readBoundedBytes(resp.body?.byteStream(), MAX_FETCH_BYTES).toString(StandardCharsets.UTF_8)
        }

        val linkRegex = Regex(
            """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val snippetRegex = Regex(
            """class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)</(?:a|div|span)>""",
            RegexOption.IGNORE_CASE
        )

        val links = linkRegex.findAll(html).toList()
        val snippets = snippetRegex.findAll(html).toList()

        val hits = mutableListOf<SearchHit>()
        for (i in links.indices) {
            if (hits.size >= limit) break
            val rawHref = decodeHtmlEntities(links[i].groupValues[1])
            val resolvedUrl = unwrapDuckDuckGoRedirect(rawHref)
            if (resolvedUrl.isBlank() || !resolvedUrl.startsWith("http")) continue
            val title = stripTagsAndWhitespace(links[i].groupValues[2])
            val snippet = snippets.getOrNull(i)?.let { stripTagsAndWhitespace(it.groupValues[1]) }.orEmpty()
            if (title.isNotBlank()) {
                hits += SearchHit(title = title, url = resolvedUrl, snippet = snippet)
            }
        }
        return hits
    }

    private fun searchDuckDuckGoInstantAnswer(query: String, limit: Int): List<SearchHit> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val req = Request.Builder()
            .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        val raw = okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            readBoundedBytes(resp.body?.byteStream(), 256 * 1024).toString(StandardCharsets.UTF_8)
        }

        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
        val hits = mutableListOf<SearchHit>()

        val abstractText = obj["AbstractText"]?.jsonPrimitive?.content.orEmpty()
        val abstractUrl = obj["AbstractURL"]?.jsonPrimitive?.content.orEmpty()
        val heading = obj["Heading"]?.jsonPrimitive?.content.orEmpty()
        if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
            hits += SearchHit(
                title = heading.ifBlank { query },
                url = abstractUrl,
                snippet = abstractText
            )
        }

        val related = obj["RelatedTopics"] as? JsonArray
        if (related != null) {
            for (item in related) {
                if (hits.size >= limit) break
                val entry = item as? JsonObject ?: continue
                val firstUrl = entry["FirstURL"]?.jsonPrimitive?.content.orEmpty()
                val text = entry["Text"]?.jsonPrimitive?.content.orEmpty()
                if (firstUrl.isNotBlank() && text.isNotBlank()) {
                    hits += SearchHit(
                        title = text.substringBefore(" - ").take(80),
                        url = firstUrl,
                        snippet = text
                    )
                }
            }
        }
        return hits
    }

    private fun formatSearchResults(query: String, hits: List<SearchHit>): String = buildString {
        appendLine("Web Search Results for: $query")
        appendLine()
        hits.forEachIndexed { idx, hit ->
            appendLine("${idx + 1}. ${hit.title}")
            appendLine("   URL: ${hit.url}")
            if (hit.snippet.isNotBlank()) {
                appendLine("   ${hit.snippet}")
            }
            appendLine()
        }
    }.trimEnd()

    private fun unwrapDuckDuckGoRedirect(href: String): String {
        val fixed = if (href.startsWith("//")) "https:$href" else href
        val idx = fixed.indexOf("uddg=")
        if (idx >= 0) {
            val encoded = fixed.substring(idx + 5).substringBefore('&')
            return runCatching { URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()) }.getOrDefault(fixed)
        }
        return fixed
    }

    private fun htmlToReadableText(html: String): String {
        val noScripts = html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<svg[\\s\\S]*?</svg>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<noscript[\\s\\S]*?</noscript>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?i)<(br|p|div|li|tr|h[1-6]|section|article)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(noScripts)
            .lines()
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    private fun stripTagsAndWhitespace(raw: String): String {
        val stripped = raw.replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(stripped).replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeHtmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    private fun extractApiBaseUrl(text: String): String? {
        val urlRegex = Regex("""https?://[A-Za-z0-9._-]+(?::\d+)?(?:/[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=-]*)?""")
        val matches = urlRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ';') }.toList()
        return matches.firstOrNull {
            it.contains("/v1", ignoreCase = true) ||
                it.contains("//api.", ignoreCase = true) ||
                it.contains("router", ignoreCase = true)
        }?.let { found ->
            val v1Idx = found.lowercase().indexOf("/v1")
            if (v1Idx >= 0) found.substring(0, v1Idx + 3) else found.trimEnd('/')
        }
    }

    private fun guessApiBaseFromDocsUrl(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val rootDomain = host.removePrefix("docs.").removePrefix("platform.").removePrefix("www.")
        return if (host.startsWith("api.")) {
            "https://$host/v1"
        } else {
            "https://api.$rootDomain/v1"
        }
    }

    private fun parsedHostTitle(url: String): String {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return host.removePrefix("api.").removePrefix("www.")
            .substringBefore('.')
            .replaceFirstChar { it.uppercaseChar() }
            .ifBlank { "Custom API" }
    }

    private fun readBoundedBytes(stream: java.io.InputStream?, maxBytes: Int): ByteArray {
        if (stream == null) return ByteArray(0)
        return stream.use { input ->
            val buf = ByteArray(8192)
            val baos = java.io.ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buf)
                if (read <= 0) break
                val allowed = minOf(read, maxBytes - total)
                if (allowed > 0) {
                    baos.write(buf, 0, allowed)
                    total += allowed
                }
                if (total >= maxBytes) break
            }
            baos.toByteArray()
        }
    }

    companion object {
        private const val USER_AGENT = "IB-CODER-Android/1.0"
        private const val MAX_FETCH_BYTES = 512 * 1024
        private const val MAX_RETURN_CHARS = 24_000

        val KNOWN_PROVIDER_APIS: List<ProviderApiDiscoveryResult> = listOf(
            ProviderApiDiscoveryResult(
                name = "Cerebras Inference",
                baseUrl = "https://api.cerebras.ai/v1",
                docsUrl = "https://cloud.cerebras.ai/",
                note = "Ultra-fast Llama/Qwen inference (OpenAI-compatible)"
            ),
            ProviderApiDiscoveryResult(
                name = "SambaNova Cloud",
                baseUrl = "https://api.sambanova.ai/v1",
                docsUrl = "https://cloud.sambanova.ai/",
                note = "Fast free-tier & hosted open weights API"
            ),
            ProviderApiDiscoveryResult(
                name = "DeepInfra",
                baseUrl = "https://api.deepinfra.com/v1/openai",
                docsUrl = "https://deepinfra.com/dash/api_keys",
                note = "OpenAI-compatible hosted models"
            ),
            ProviderApiDiscoveryResult(
                name = "Hyperbolic",
                baseUrl = "https://api.hyperbolic.xyz/v1",
                docsUrl = "https://app.hyperbolic.xyz/",
                note = "Open-source LLMs & reasoning models"
            ),
            ProviderApiDiscoveryResult(
                name = "Featherless AI",
                baseUrl = "https://api.featherless.ai/v1",
                docsUrl = "https://featherless.ai/account/api-keys",
                note = "Serverless HuggingFace models via OpenAI API"
            ),
            ProviderApiDiscoveryResult(
                name = "Chutes AI",
                baseUrl = "https://llm.chutes.ai/v1",
                docsUrl = "https://chutes.ai/",
                note = "Decentralized OpenAI-compatible inference"
            ),
            ProviderApiDiscoveryResult(
                name = "Hugging Face Router",
                baseUrl = "https://router.huggingface.co/v1",
                docsUrl = "https://huggingface.co/settings/tokens",
                note = "HF Inference Providers unified OpenAI API"
            ),
            ProviderApiDiscoveryResult(
                name = "GitHub Models",
                baseUrl = "https://models.inference.ai.azure.com",
                docsUrl = "https://github.com/marketplace/models",
                note = "Free/Pro GitHub PAT inference endpoint"
            ),
            ProviderApiDiscoveryResult(
                name = "Novita AI",
                baseUrl = "https://api.novita.ai/v3/openai",
                docsUrl = "https://novita.ai/settings/key-management",
                note = "OpenAI-compatible open weights API"
            ),
            ProviderApiDiscoveryResult(
                name = "LM Studio (Local LAN)",
                baseUrl = "http://10.0.2.2:1234/v1",
                docsUrl = "https://lmstudio.ai/docs/api",
                note = "Local LM Studio server on host machine"
            )
        )
    }
}
