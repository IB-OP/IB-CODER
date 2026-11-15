package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.agent.AgentToolExecutor
import com.example.data.local.db.AppDatabase
import com.example.data.network.ProviderRegistry
import com.example.data.workspace.WorkspaceManager
import com.example.domain.model.ToolSupportStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
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
}
