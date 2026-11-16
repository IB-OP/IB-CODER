package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.domain.model.AppAccountSession
import com.example.domain.model.AppSettings
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubUserProfile
import com.example.domain.model.McpServerConfig
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ThemeMode
import com.example.domain.prompt.DefaultSystemPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_coder_settings"
)

private val Context.secretsStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_coder_secrets"
)

class SettingsDataStore(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager,
    private val json: Json
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_PROVIDER = stringPreferencesKey("default_provider")
        val DEFAULT_MODEL = stringPreferencesKey("default_model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val ACTIVE_PROJECT_ID = stringPreferencesKey("active_project_id")
        val PROVIDER_CONFIGS = stringPreferencesKey("provider_configs_json")
        val GITHUB_PROJECT_CONFIGS = stringPreferencesKey("github_project_configs_json")
        val GITHUB_DEFAULT_OWNER = stringPreferencesKey("github_default_owner")
        val GITHUB_USER_PROFILE = stringPreferencesKey("github_user_profile_json")
        val GITHUB_OAUTH_CLIENT_ID = stringPreferencesKey("github_oauth_client_id")
        val APP_ACCOUNT_SESSION = stringPreferencesKey("app_account_session_json")
        val MCP_SERVERS = stringPreferencesKey("mcp_servers_json")
        val GITHUB_TOKEN_SECRET = stringPreferencesKey("github_pat")
        val LOCAL_PASSCODE_HASH = stringPreferencesKey("local_passcode_hash")
    }

    private fun secretKeyFor(providerId: String): Preferences.Key<String> =
        stringPreferencesKey("api_key_$providerId")

    private fun mcpSecretKeyFor(serverId: String): Preferences.Key<String> =
        stringPreferencesKey("mcp_token_$serverId")

    val settingsFlow: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { name ->
            runCatching { ThemeMode.valueOf(name) }.getOrNull()
        } ?: ThemeMode.DARK

        val prompt = prefs[Keys.SYSTEM_PROMPT]
            ?.takeIf { it.isNotBlank() }
            ?: DefaultSystemPrompt.TEXT

        AppSettings(
            themeMode = theme,
            defaultProviderId = prefs[Keys.DEFAULT_PROVIDER] ?: "coarena",
            defaultModelId = prefs[Keys.DEFAULT_MODEL] ?: "",
            systemPrompt = prompt
        )
    }

    val activeProjectIdFlow: Flow<String?> = context.settingsStore.data.map { prefs ->
        prefs[Keys.ACTIVE_PROJECT_ID]?.takeIf { it.isNotBlank() }
    }

    val providerConfigsFlow: Flow<Map<String, ProviderConfig>> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[Keys.PROVIDER_CONFIGS] ?: return@map emptyMap()
            runCatching {
                json.decodeFromString<Map<String, ProviderConfig>>(raw)
            }.getOrDefault(emptyMap())
        }

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDefaultProviderAndModel(providerId: String, modelId: String) {
        context.settingsStore.edit {
            it[Keys.DEFAULT_PROVIDER] = providerId
            it[Keys.DEFAULT_MODEL] = modelId
        }
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.settingsStore.edit {
            it[Keys.SYSTEM_PROMPT] = prompt
        }
    }

    suspend fun resetSystemPrompt() {
        context.settingsStore.edit {
            it.remove(Keys.SYSTEM_PROMPT)
        }
    }

    suspend fun setActiveProjectId(projectId: String?) {
        context.settingsStore.edit {
            if (projectId.isNullOrBlank()) {
                it.remove(Keys.ACTIVE_PROJECT_ID)
            } else {
                it[Keys.ACTIVE_PROJECT_ID] = projectId
            }
        }
    }

    suspend fun saveProviderConfig(config: ProviderConfig) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.PROVIDER_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ProviderConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = current.toMutableMap().apply {
                put(config.providerId, config)
            }
            prefs[Keys.PROVIDER_CONFIGS] = json.encodeToString(updated)
        }
    }

    suspend fun getProviderConfig(providerId: String): ProviderConfig? {
        return providerConfigsFlow.first()[providerId]
    }

    suspend fun saveApiKey(providerId: String, apiKey: String) {
        val trimmed = apiKey.trim()
        val encrypted = if (trimmed.isEmpty()) "" else cryptoManager.encrypt(trimmed)
        context.secretsStore.edit { prefs ->
            val key = secretKeyFor(providerId)
            if (encrypted.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = encrypted
            }
        }
    }

    suspend fun getApiKey(providerId: String): String {
        val prefs = context.secretsStore.data.first()
        val encrypted = prefs[secretKeyFor(providerId)] ?: return ""
        return cryptoManager.decrypt(encrypted)
    }

    suspend fun getAllApiKeys(providerIds: List<String>): Map<String, String> {
        val prefs = context.secretsStore.data.first()
        val result = mutableMapOf<String, String>()
        for (id in providerIds) {
            val enc = prefs[secretKeyFor(id)]
            result[id] = if (enc.isNullOrBlank()) "" else cryptoManager.decrypt(enc)
        }
        return result
    }

    // --- GitHub Integration ---

    val hasGitHubTokenFlow: Flow<Boolean> = context.secretsStore.data.map { prefs ->
        !prefs[Keys.GITHUB_TOKEN_SECRET].isNullOrBlank()
    }

    val githubDefaultOwnerFlow: Flow<String> = context.settingsStore.data.map { prefs ->
        prefs[Keys.GITHUB_DEFAULT_OWNER].orEmpty()
    }

    val githubOAuthClientIdFlow: Flow<String> = context.settingsStore.data.map { prefs ->
        prefs[Keys.GITHUB_OAUTH_CLIENT_ID].orEmpty()
    }

    val githubUserProfileFlow: Flow<GitHubUserProfile?> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.GITHUB_USER_PROFILE] ?: return@map null
        runCatching { json.decodeFromString<GitHubUserProfile>(raw) }.getOrNull()
    }

    val appAccountSessionFlow: Flow<AppAccountSession> = context.settingsStore.data.map { prefs ->
        val raw = prefs[Keys.APP_ACCOUNT_SESSION] ?: return@map AppAccountSession()
        runCatching { json.decodeFromString<AppAccountSession>(raw) }.getOrDefault(AppAccountSession())
    }

    val githubProjectConfigsFlow: Flow<Map<String, GitHubProjectConfig>> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[Keys.GITHUB_PROJECT_CONFIGS] ?: return@map emptyMap()
            runCatching {
                json.decodeFromString<Map<String, GitHubProjectConfig>>(raw)
            }.getOrDefault(emptyMap())
        }

    suspend fun getGitHubProjectConfigs(): Map<String, GitHubProjectConfig> =
        githubProjectConfigsFlow.first()

    suspend fun getGitHubProjectConfig(projectId: String): GitHubProjectConfig? =
        githubProjectConfigsFlow.first()[projectId]

    suspend fun saveGitHubToken(token: String) {
        val trimmed = token.trim()
        val encrypted = if (trimmed.isEmpty()) "" else cryptoManager.encrypt(trimmed)
        context.secretsStore.edit { prefs ->
            if (encrypted.isEmpty()) {
                prefs.remove(Keys.GITHUB_TOKEN_SECRET)
            } else {
                prefs[Keys.GITHUB_TOKEN_SECRET] = encrypted
            }
        }
    }

    suspend fun getGitHubToken(): String {
        val prefs = context.secretsStore.data.first()
        val encrypted = prefs[Keys.GITHUB_TOKEN_SECRET] ?: return ""
        return cryptoManager.decrypt(encrypted)
    }

    suspend fun saveGitHubOAuthClientId(clientId: String) {
        context.settingsStore.edit { prefs ->
            val clean = clientId.trim()
            if (clean.isEmpty()) prefs.remove(Keys.GITHUB_OAUTH_CLIENT_ID)
            else prefs[Keys.GITHUB_OAUTH_CLIENT_ID] = clean
        }
    }

    suspend fun saveGitHubDefaultOwner(owner: String) {
        context.settingsStore.edit { prefs ->
            val clean = owner.trim()
            if (clean.isEmpty()) prefs.remove(Keys.GITHUB_DEFAULT_OWNER)
            else prefs[Keys.GITHUB_DEFAULT_OWNER] = clean
        }
    }

    suspend fun saveGitHubUserProfile(profile: GitHubUserProfile?) {
        context.settingsStore.edit { prefs ->
            if (profile == null) {
                prefs.remove(Keys.GITHUB_USER_PROFILE)
            } else {
                prefs[Keys.GITHUB_USER_PROFILE] = json.encodeToString(profile)
                prefs[Keys.GITHUB_DEFAULT_OWNER] = profile.login
            }
        }
        if (profile != null) {
            val current = appAccountSessionFlow.first()
            saveAppAccountSession(
                current.copy(
                    isSignedIn = true,
                    username = profile.login,
                    displayName = profile.name.ifBlank { profile.login },
                    authProvider = "GitHub",
                    signedInAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun saveAppAccountSession(session: AppAccountSession) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.APP_ACCOUNT_SESSION] = json.encodeToString(session)
        }
    }

    suspend fun signInLocalAccount(username: String, passcode: String) {
        val cleanUser = username.trim().ifBlank { "Developer" }
        if (passcode.isNotBlank()) {
            val hash = sha256(passcode)
            context.secretsStore.edit { it[Keys.LOCAL_PASSCODE_HASH] = hash }
        }
        val current = appAccountSessionFlow.first()
        saveAppAccountSession(
            current.copy(
                isSignedIn = true,
                username = cleanUser,
                displayName = cleanUser,
                authProvider = "Local",
                signedInAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun hasLocalPasscode(): Boolean {
        val prefs = context.secretsStore.data.first()
        return !prefs[Keys.LOCAL_PASSCODE_HASH].isNullOrBlank()
    }

    suspend fun verifyLocalPasscode(passcode: String): Boolean {
        val prefs = context.secretsStore.data.first()
        val stored = prefs[Keys.LOCAL_PASSCODE_HASH] ?: return true
        return stored == sha256(passcode)
    }

    suspend fun signOutApp(clearGitHubToken: Boolean = true) {
        val current = appAccountSessionFlow.first()
        saveAppAccountSession(
            current.copy(
                isSignedIn = false,
                signedInAt = 0L
            )
        )
        if (clearGitHubToken) {
            saveGitHubToken("")
            saveGitHubUserProfile(null)
        }
    }

    suspend fun setRequireSignInOnLaunch(required: Boolean) {
        val current = appAccountSessionFlow.first()
        saveAppAccountSession(current.copy(requireSignInOnLaunch = required))
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun saveGitHubProjectConfig(projectId: String, config: GitHubProjectConfig) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.GITHUB_PROJECT_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, GitHubProjectConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            val updated = current.toMutableMap().apply { put(projectId, config) }
            prefs[Keys.GITHUB_PROJECT_CONFIGS] = json.encodeToString(updated)
            if (config.owner.isNotBlank()) {
                prefs[Keys.GITHUB_DEFAULT_OWNER] = config.owner.trim()
            }
        }
    }

    // --- MCP Servers ---

    val mcpServersFlow: Flow<List<McpServerConfig>> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[Keys.MCP_SERVERS] ?: return@map emptyList()
            runCatching {
                json.decodeFromString<List<McpServerConfig>>(raw)
            }.getOrDefault(emptyList())
        }

    suspend fun getMcpServers(): List<McpServerConfig> = mcpServersFlow.first()

    suspend fun saveMcpServer(server: McpServerConfig) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.MCP_SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<McpServerConfig>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.toMutableList()
            val idx = updated.indexOfFirst { it.id == server.id }
            if (idx >= 0) updated[idx] = server else updated.add(server)
            prefs[Keys.MCP_SERVERS] = json.encodeToString(updated)
        }
    }

    suspend fun deleteMcpServer(serverId: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.MCP_SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<McpServerConfig>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == serverId }
            prefs[Keys.MCP_SERVERS] = json.encodeToString(updated)
        }
        context.secretsStore.edit { prefs ->
            prefs.remove(mcpSecretKeyFor(serverId))
        }
    }

    suspend fun saveMcpAuthToken(serverId: String, token: String) {
        val trimmed = token.trim()
        val encrypted = if (trimmed.isEmpty()) "" else cryptoManager.encrypt(trimmed)
        context.secretsStore.edit { prefs ->
            val key = mcpSecretKeyFor(serverId)
            if (encrypted.isEmpty()) prefs.remove(key) else prefs[key] = encrypted
        }
    }

    suspend fun getMcpAuthToken(serverId: String): String {
        val prefs = context.secretsStore.data.first()
        val encrypted = prefs[mcpSecretKeyFor(serverId)] ?: return ""
        return cryptoManager.decrypt(encrypted)
    }

    suspend fun getAllMcpAuthTokens(serverIds: List<String>): Map<String, String> {
        val prefs = context.secretsStore.data.first()
        val result = mutableMapOf<String, String>()
        for (id in serverIds) {
            val enc = prefs[mcpSecretKeyFor(id)]
            result[id] = if (enc.isNullOrBlank()) "" else cryptoManager.decrypt(enc)
        }
        return result
    }
}
