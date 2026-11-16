package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.agent.AgentToolExecutor
import com.example.data.local.db.AppDatabase
import com.example.data.network.GitHubPushClient
import com.example.data.network.GitHubSyncManager
import com.example.data.network.McpClient
import com.example.data.network.ProviderRegistry
import com.example.data.preferences.SettingsDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.GitHubWorkflowJobLog
import com.example.domain.model.GitHubWorkflowRunSummary
import com.example.domain.model.McpServerConfig
import com.example.domain.model.McpTransport
import com.example.domain.model.ToolSupportStatus
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
}
