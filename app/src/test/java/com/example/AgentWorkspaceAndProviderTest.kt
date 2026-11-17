package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.agent.AgentToolExecutor
import com.example.data.local.db.AppDatabase
import com.example.data.network.GitHubPushClient
import com.example.data.network.GitHubSyncManager
import com.example.data.network.InternetToolClient
import com.example.data.network.McpClient
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.data.workspace.SafDocumentStorage
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.CustomProviderDefinition
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.McpServerConfig
import com.example.domain.model.McpTransport
import com.example.domain.model.SessionMode
import com.example.domain.model.SessionPromptConfig
import com.example.domain.model.ToolSupportStatus
import com.example.domain.model.WorkspaceProject
import com.example.domain.prompt.SystemPromptPresets
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AgentWorkspaceAndProviderTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var workspaceManager: WorkspaceManager
    private lateinit var toolExecutor: AgentToolExecutor
    private lateinit var registry: ProviderRegistry

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        workspaceManager = WorkspaceManager(context, db.projectDao())
        toolExecutor = AgentToolExecutor(workspaceManager, json)
        registry = ProviderRegistry(OkHttpClient(), json)
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `all 23 required providers are registered with expected default urls`() {
        assertEquals(23, registry.adapters.size)
        assertEquals("https://coarena.ai/api/v1", registry.getAdapter("coarena")?.metadata?.defaultBaseUrl)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/", registry.getAdapter("gemini")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.groq.com/openai/v1", registry.getAdapter("groq")?.metadata?.defaultBaseUrl)
        assertEquals("https://openrouter.ai/api/v1", registry.getAdapter("openrouter")?.metadata?.defaultBaseUrl)
        assertEquals("https://integrate.api.nvidia.com/v1", registry.getAdapter("nvidia_nim")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/v1", registry.getAdapter("cloudflare")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.z.ai/api/paas/v4", registry.getAdapter("z_ai")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.aionlabs.ai/v1", registry.getAdapter("aion_labs")?.metadata?.defaultBaseUrl)
        assertEquals("https://proxy.gonkabroker.com/v1", registry.getAdapter("gonka_broker")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.kilo.ai/api/gateway", registry.getAdapter("kilo_code")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.llm7.io/v1", registry.getAdapter("llm7")?.metadata?.defaultBaseUrl)
        assertEquals("https://oai.endpoints.kepler.ai.cloud.ovh.net/v1", registry.getAdapter("ovhcloud")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.anthropic.com/v1", registry.getAdapter("anthropic")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.openai.com/v1", registry.getAdapter("openai")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.mistral.ai/v1", registry.getAdapter("mistral")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.deepseek.com/v1", registry.getAdapter("deepseek")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.x.ai/v1", registry.getAdapter("xai")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.together.xyz/v1", registry.getAdapter("together")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.fireworks.ai/inference/v1", registry.getAdapter("fireworks")?.metadata?.defaultBaseUrl)
        assertEquals("https://api.perplexity.ai", registry.getAdapter("perplexity")?.metadata?.defaultBaseUrl)
        assertEquals("http://localhost:11434/v1", registry.getAdapter("ollama")?.metadata?.defaultBaseUrl)
    }

    @Test
    fun `tool support flags unsupported providers and models`() {
        assertEquals(
            ToolSupportStatus.UNSUPPORTED,
            registry.getAdapter("perplexity")?.modelToolSupport("sonar-pro")
        )
        assertEquals(
            ToolSupportStatus.UNSUPPORTED,
            registry.getAdapter("openai")?.modelToolSupport("o1-mini")
        )
        assertEquals(
            ToolSupportStatus.SUPPORTED,
            registry.getAdapter("anthropic")?.modelToolSupport("claude-sonnet-4-5")
        )
    }

    @Test
    fun `workspace tools create read edit search delete and zip roundtrip`() = runTest {
        val project = workspaceManager.createProject("SampleApp")

        val folderRes = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "1",
            toolName = "create_folder",
            argumentsJson = """{"path":"app/src/main"}""",
            confirmDeletion = { false }
        )
        assertTrue(folderRes.success)

        val writeRes = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "2",
            toolName = "write_file",
            argumentsJson = """{"path":"app/src/main/Main.kt","content":"fun main() {\n    println(\"Hello\")\n}"}""",
            confirmDeletion = { false }
        )
        assertTrue(writeRes.success)

        val editRes = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "3",
            toolName = "edit_file",
            argumentsJson = """{"path":"app/src/main/Main.kt","find":"Hello","replace":"AI Coder"}""",
            confirmDeletion = { false }
        )
        assertTrue(editRes.success)

        val searchRes = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "4",
            toolName = "search_files",
            argumentsJson = """{"query":"AI Coder"}""",
            confirmDeletion = { false }
        )
        assertTrue(searchRes.success)
        assertTrue(searchRes.output.contains("Main.kt:2"))

        // Traversal is blocked
        val traversalRes = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "5",
            toolName = "read_file",
            argumentsJson = """{"path":"../../etc/passwd"}""",
            confirmDeletion = { false }
        )
        assertFalse(traversalRes.success)

        // Zip export and re-import
        val zipOut = ByteArrayOutputStream()
        workspaceManager.exportProjectToZip(project.id, zipOut)
        val imported = workspaceManager.importProjectFromZip(
            "Reimported",
            ByteArrayInputStream(zipOut.toByteArray())
        )
        val raw = workspaceManager.readRawFile(imported.id, "app/src/main/Main.kt")
        assertTrue(raw.contains("AI Coder"))

        // Deletion cancelled vs approved
        val cancelDel = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "6",
            toolName = "delete_file",
            argumentsJson = """{"path":"app/src/main/Main.kt"}""",
            confirmDeletion = { false }
        )
        assertTrue(cancelDel.wasCancelled)

        val approveDel = toolExecutor.execute(
            projectId = project.id,
            toolCallId = "7",
            toolName = "delete_file",
            argumentsJson = """{"path":"app/src/main/Main.kt"}""",
            confirmDeletion = { true }
        )
        assertTrue(approveDel.success)
    }

    @Test
    fun `mcp client discovers and calls tools over Streamable HTTP`() = runTest {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val capturedAuth = CopyOnWriteArrayList<String>()
        val capturedSessionIds = CopyOnWriteArrayList<String>()

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/mcp") { exchange ->
            capturedAuth += exchange.requestHeaders.getFirst("Authorization").orEmpty()
            exchange.requestHeaders.getFirst("Mcp-Session-Id")?.let { capturedSessionIds += it }

            val reqBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val respBody = when {
                reqBody.contains("\"method\":\"initialize\"") -> {
                    exchange.responseHeaders.add("Mcp-Session-Id", "sess-42")
                    """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26","serverInfo":{"name":"TestMcp"},"capabilities":{"tools":{}}}}"""
                }
                reqBody.contains("\"method\":\"notifications/initialized\"") -> {
                    exchange.sendResponseHeaders(202, -1)
                    exchange.close()
                    return@createContext
                }
                reqBody.contains("\"method\":\"tools/list\"") -> {
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"echo_doc","description":"Echo a doc","inputSchema":{"type":"object","properties":{"q":{"type":"string"}},"required":["q"]}}]}}"""
                }
                reqBody.contains("\"method\":\"tools/call\"") -> {
                    """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"MCP says hi"}]}}"""
                }
                else -> """{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"Method not found"}}"""
            }

            val bytes = respBody.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val port = server.address.port
            val mcpClient = McpClient(OkHttpClient(), json)
            val cfg = McpServerConfig(
                id = "srv1",
                name = "Test Server",
                endpointUrl = "http://127.0.0.1:$port/mcp",
                transport = McpTransport.STREAMABLE_HTTP
            )

            val syncRes = mcpClient.syncServer(cfg, "secret-mcp-tok")
            assertTrue(syncRes.isSuccess)
            val synced = syncRes.getOrThrow()
            assertEquals("TestMcp", synced.serverInfoName)
            assertEquals(1, synced.tools.size)
            assertEquals("echo_doc", synced.tools.first().name)
            assertTrue(capturedAuth.all { it == "Bearer secret-mcp-tok" })
            assertTrue("sess-42" in capturedSessionIds)

            val output = mcpClient.callTool(cfg, "secret-mcp-tok", "echo_doc", """{"q":"hello"}""")
            assertEquals("MCP says hi", output)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `github client APK workflow template includes assembleDebug and artifact upload`() = runTest {
        val template = GitHubPushClient.APK_BUILD_WORKFLOW_TEMPLATE
        assertTrue(template.contains("assembleDebug"))
        assertTrue(template.contains("bundleRelease"))
        assertTrue(template.contains("upload-artifact@v4"))
        assertTrue(template.contains("app-debug-apk"))
        assertTrue(template.contains("src/app-test.apk"))
        assertTrue(template.contains("src/app-release.aab"))
    }

    @Test
    fun `github zipball import strips top-level archive folder and blocks traversal`() = runTest {
        val cleanZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("octocat-MyRepo-abc1234/"))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("octocat-MyRepo-abc1234/.github/workflows/ci.yml"))
                zos.write("name: CI\non: [push]\n".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("octocat-MyRepo-abc1234/app/src/Main.kt"))
                zos.write("fun main() {}\n".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val imported = workspaceManager.importGitHubZipball(
            projectName = "MyRepo",
            inputStream = ByteArrayInputStream(cleanZipBytes)
        )
        val ciContent = workspaceManager.readRawFile(imported.id, ".github/workflows/ci.yml")
        val mainContent = workspaceManager.readRawFile(imported.id, "app/src/Main.kt")
        assertTrue(ciContent.contains("name: CI"))
        assertTrue(mainContent.contains("fun main()"))

        val traversalZipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("octocat-MyRepo-abc1234/../../evil.txt"))
                zos.write("bad".toByteArray())
                zos.closeEntry()
            }
            baos.toByteArray()
        }
        var blocked = false
        try {
            workspaceManager.importGitHubZipball(
                projectName = "EvilRepo",
                inputStream = ByteArrayInputStream(traversalZipBytes)
            )
        } catch (_: SecurityException) {
            blocked = true
        }
        assertTrue("Expected Zip Slip traversal entry to throw SecurityException", blocked)
    }

    @Test
    fun `github sync manager builds clear AI fix prompts for dispatch bugs and failed job logs`() = runTest {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val settings = SettingsDataStore(context, KeystoreCryptoManager(), json)
        val ghClient = GitHubPushClient(OkHttpClient(), workspaceManager, json)
        val syncManager = GitHubSyncManager(settings, ghClient)

        val dispatchPrompt = syncManager.buildRunFailurePrompt(
            owner = "octocat",
            repo = "Sample",
            branch = "main",
            run = null,
            jobLogs = emptyList(),
            dispatchError = "Workflow 'Android APK' (.github/workflows/android.yml): Workflow does not have 'workflow_dispatch' trigger"
        )
        assertTrue(dispatchPrompt.contains("octocat/Sample"))
        assertTrue(dispatchPrompt.contains("workflow_dispatch"))

        val runPrompt = syncManager.buildRunFailurePrompt(
            owner = "octocat",
            repo = "Sample",
            branch = "main",
            run = GitHubWorkflowRunSummary(
                id = 99L,
                name = "Android APK",
                status = "completed",
                conclusion = "failure",
                htmlUrl = "https://github.com/octocat/Sample/actions/runs/99",
                createdAt = "2026-11-15T10:00:00Z",
                headBranch = "main",
                headSha = "deadbeef1234",
                runNumber = 7
            ),
            jobLogs = listOf(
                GitHubWorkflowJobLog(
                    jobId = 1L,
                    jobName = "build-debug",
                    conclusion = "failure",
                    failedSteps = listOf("Build Debug APK"),
                    logExcerpt = "Execution failed for task ':app:compileDebugKotlin'."
                )
            ),
            dispatchError = null
        )
        assertTrue(runPrompt.contains("Android APK (Run #7, Conclusion: failure)"))
        assertTrue(runPrompt.contains("Build Debug APK"))
        assertTrue(runPrompt.contains("compileDebugKotlin"))
    }

    @Test
    fun `app account sign-in verifies local passcode and sign-out updates session`() = runTest {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val settings = SettingsDataStore(context, KeystoreCryptoManager(), json)

        settings.setRequireSignInOnLaunch(true)
        settings.signInLocalAccount("devuser", "pass1234")

        val signedIn = settings.appAccountSessionFlow.first()
        assertTrue(signedIn.isSignedIn)
        assertTrue(signedIn.requireSignInOnLaunch)
        assertEquals("devuser", signedIn.username)
        assertTrue(settings.hasLocalPasscode())
        assertTrue(settings.verifyLocalPasscode("pass1234"))
        assertFalse(settings.verifyLocalPasscode("wrong"))

        settings.signOutApp(clearGitHubToken = false)
        val signedOut = settings.appAccountSessionFlow.first()
        assertFalse(signedOut.isSignedIn)
        assertTrue(signedOut.requireSignInOnLaunch)
    }

    @Test
    fun `saf storage blocks traversal and project detects device folder uri`() = runTest {
        val deviceProj = WorkspaceProject(
            id = "p1",
            name = "MyPhoneFolder",
            rootPath = "content://com.android.externalstorage.documents/tree/primary%3AMyApp"
        )
        val localProj = WorkspaceProject(
            id = "p2",
            name = "LocalApp",
            rootPath = "/data/user/0/com.example/files/workspaces/p2"
        )
        assertTrue(deviceProj.isDeviceFolder)
        assertFalse(localProj.isDeviceFolder)

        val saf = SafDocumentStorage(context)
        assertEquals(listOf("app", "src", "Main.kt"), saf.normalizeSegments("/app/./src\\Main.kt"))
        var blocked = false
        try {
            saf.normalizeSegments("app/../../etc/passwd")
        } catch (_: SecurityException) {
            blocked = true
        }
        assertTrue("Expected SAF normalization to block parent traversal", blocked)
    }

    @Test
    fun `statPath collectProjectFilesForSync and directGitHubMode behave correctly`() = runTest {
        val project = workspaceManager.createProject("DirectApp")
        workspaceManager.createFolder(project.id, "build/outputs")
        workspaceManager.writeFile(project.id, "build/outputs/ignored.txt", "skip")
        workspaceManager.writeFile(project.id, "gradlew", "#!/bin/sh\necho ok")
        workspaceManager.writeFile(project.id, "app/src/Main.kt", "fun main() {}")

        val statMain = workspaceManager.statPath(project.id, "app/src/Main.kt")
        assertTrue(statMain.exists)
        assertFalse(statMain.isDirectory)

        val statDir = workspaceManager.statPath(project.id, "app/src")
        assertTrue(statDir.exists)
        assertTrue(statDir.isDirectory)

        val syncFiles = workspaceManager.collectProjectFilesForSync(
            projectId = project.id,
            ignoredDirs = setOf(".git", ".gradle", "build", ".idea"),
            maxBlobBytes = 1024 * 1024
        )
        assertEquals(listOf("app/src/Main.kt", "gradlew"), syncFiles.map { it.relativePath })
        assertTrue(syncFiles.first { it.relativePath == "gradlew" }.isExecutable)
        assertFalse(syncFiles.first { it.relativePath == "app/src/Main.kt" }.isExecutable)

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val settings = SettingsDataStore(context, KeystoreCryptoManager(), json)
        val ghClient = GitHubPushClient(OkHttpClient(), workspaceManager, json)
        val syncManager = GitHubSyncManager(settings, ghClient)

        settings.saveGitHubProjectConfig(
            project.id,
            GitHubProjectConfig(owner = "octocat", repo = "DirectApp", branch = "main")
        )
        syncManager.setDirectGitHubMode(project.id, true)
        val updated = settings.getGitHubProjectConfig(project.id)!!
        assertTrue(updated.directGitHubMode)
        assertTrue(updated.autoSyncEnabled)
        assertEquals("main", updated.branch)
    }

    @Test
    fun `internet tool client fetches web pages, strips HTML, and runs without an active project`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/docs") { exchange ->
            val html = """
                <!doctype html>
                <html>
                  <head><script>console.log("ignore")</script></head>
                  <body><h1>API Docs</h1><p>Endpoint is /v1/chat/completions</p></body>
                </html>
            """.trimIndent().toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, html.size.toLong())
            exchange.responseBody.use { it.write(html) }
        }
        server.start()

        try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val internet = InternetToolClient(OkHttpClient(), json)
            val port = server.address.port

            val fetched = internet.fetchUrl("http://127.0.0.1:$port/docs")
            assertTrue(fetched.isSuccess)
            val text = fetched.getOrThrow()
            assertTrue(text.contains("HTTP 200"))
            assertTrue(text.contains("API Docs"))
            assertTrue(text.contains("/v1/chat/completions"))
            assertFalse(text.contains("console.log"))

            val emptySearch = internet.webSearch("   ")
            assertTrue(emptySearch.isFailure)

            val curated = internet.discoverApiEndpoint("cerebras")
            assertTrue(curated.any { it.baseUrl == "https://api.cerebras.ai/v1" })

            val directCandidate = internet.discoverApiEndpoint("my-llm.example.org")
            assertTrue(directCandidate.any { it.baseUrl == "https://my-llm.example.org/v1" })

            // AgentToolExecutor runs fetch_url with projectId = null, while blocking workspace tools cleanly
            val executor = AgentToolExecutor(workspaceManager, json, internet)
            val netOutcome = executor.execute(
                projectId = null,
                toolCallId = "net1",
                toolName = "fetch_url",
                argumentsJson = """{"url":"http://127.0.0.1:$port/docs"}""",
                confirmDeletion = { false }
            )
            assertTrue(netOutcome.success)
            assertTrue(netOutcome.output.contains("/v1/chat/completions"))

            val missingWorkspaceOutcome = executor.execute(
                projectId = null,
                toolCallId = "fs1",
                toolName = "list_files",
                argumentsJson = """{}""",
                confirmDeletion = { false }
            )
            assertFalse(missingWorkspaceOutcome.success)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `custom providers and session prompt configs persist and resolve in registry`() = runTest {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val settings = SettingsDataStore(context, KeystoreCryptoManager(), json)

        val customDef = CustomProviderDefinition(
            id = "custom_cerebras_test",
            displayName = "Cerebras Custom",
            defaultBaseUrl = "https://api.cerebras.ai/v1",
            signupUrl = "https://cloud.cerebras.ai/",
            defaultModel = "llama-3.3-70b"
        )
        settings.saveCustomProvider(customDef)
        val storedCustom = settings.customProvidersFlow.first()
        assertEquals(1, storedCustom.size)

        registry.syncCustomProviders(storedCustom)
        assertEquals(23, registry.adapters.size)
        assertEquals(24, registry.allAdapters().size)
        assertEquals("https://api.cerebras.ai/v1", registry.getAdapter("custom_cerebras_test")?.metadata?.defaultBaseUrl)

        settings.deleteCustomProvider("custom_cerebras_test")
        registry.syncCustomProviders(settings.customProvidersFlow.first())
        assertEquals(23, registry.allAdapters().size)

        // Normal vs Special Session Prompt Configs
        assertTrue(SystemPromptPresets.PRESETS.isNotEmpty())
        assertEquals("fullstack_web", SystemPromptPresets.findById("fullstack_web")?.id)

        val specialCfg = SessionPromptConfig(
            mode = SessionMode.SPECIAL,
            presetId = "fullstack_web",
            presetTitle = "Full-Stack Web",
            systemPrompt = "Build a Vite React app."
        )
        settings.saveSessionPromptConfig("sess_special_1", specialCfg)
        val loaded = settings.getSessionPromptConfig("sess_special_1")
        assertEquals(SessionMode.SPECIAL, loaded?.mode)
        assertEquals("Build a Vite React app.", loaded?.systemPrompt)

        settings.deleteSessionPromptConfig("sess_special_1")
        assertEquals(null, settings.getSessionPromptConfig("sess_special_1"))
    }

    @Test
    fun `cloud vault restore populates api keys github token custom providers and mcp tokens without echo and clears on sign out`() = runTest {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val testCrypto = object : KeystoreCryptoManager() {
            override suspend fun encrypt(plaintext: String): String =
                if (plaintext.isEmpty()) "" else "enc:$plaintext"

            override suspend fun decrypt(encoded: String): String =
                encoded.removePrefix("enc:")
        }
        val settings = SettingsDataStore(context, testCrypto, json)

        settings.applyCloudGlobalSettings(
            themeMode = com.example.domain.model.ThemeMode.LIGHT,
            defaultProviderId = "custom_synced",
            defaultModelId = "synced-model-v1",
            systemPrompt = "Synced system prompt",
            githubToken = "ghp_synced_device_token",
            githubDefaultOwner = "ibrahim-dev",
            githubUserProfileJson = null
        )

        settings.applyCloudProviderEntries(
            configs = mapOf(
                "openai" to com.example.domain.model.ProviderConfig(
                    providerId = "openai",
                    baseUrl = "https://api.openai.com/v1",
                    selectedModel = "gpt-4o",
                    enabled = true
                )
            ),
            apiKeys = mapOf("openai" to "sk-synced-openai-key")
        )

        settings.applyCloudCustomProviders(
            listOf(
                CustomProviderDefinition(
                    id = "custom_synced",
                    displayName = "Synced Custom",
                    defaultBaseUrl = "https://api.example.com/v1",
                    defaultModel = "synced-model-v1"
                )
            )
        )

        settings.applyCloudMcpServers(
            servers = listOf(
                McpServerConfig(
                    id = "mcp_synced_1",
                    name = "Synced MCP",
                    endpointUrl = "https://mcp.example.com/sse",
                    enabled = true
                )
            ),
            tokens = mapOf("mcp_synced_1" to "mcp-bearer-token-99")
        )

        val restoredSettings = settings.getSettings()
        assertEquals(com.example.domain.model.ThemeMode.LIGHT, restoredSettings.themeMode)
        assertEquals("custom_synced", restoredSettings.defaultProviderId)
        assertEquals("Synced system prompt", restoredSettings.systemPrompt)
        assertEquals("ghp_synced_device_token", settings.getGitHubToken())
        assertEquals("sk-synced-openai-key", settings.getApiKey("openai"))
        assertEquals("mcp-bearer-token-99", settings.getMcpAuthToken("mcp_synced_1"))
        assertEquals(1, settings.getCustomProviders().size)
        assertEquals(1, settings.getMcpServers().size)

        settings.clearAccountVaultSecretsForSignOut()
        assertEquals("", settings.getGitHubToken())
        assertEquals("", settings.getApiKey("openai"))
        assertEquals("", settings.getMcpAuthToken("mcp_synced_1"))
        assertTrue(settings.getCustomProviders().isEmpty())
        assertTrue(settings.getMcpServers().isEmpty())
    }
}
