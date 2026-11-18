package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.domain.model.AppAccountSession
import com.example.domain.model.AppSettings
import com.example.domain.model.CustomProviderDefinition
import com.example.domain.model.DeviceControlSettings
import com.example.domain.model.GitHubProjectConfig
import com.example.domain.model.GitHubUserProfile
import com.example.domain.model.McpServerConfig
import com.example.domain.model.PrivilegeBackend
import com.example.domain.model.ProviderConfig
import com.example.domain.model.SessionPromptConfig
import com.example.domain.model.ThemeMode
import com.example.domain.prompt.DefaultSystemPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

sealed class CloudVaultMutation {
    data object GlobalSettingsOrGitHubAccount : CloudVaultMutation()
    data class ProviderChanged(val providerId: String) : CloudVaultMutation()
    data class CustomProviderUpserted(val definition: CustomProviderDefinition) : CloudVaultMutation()
    data class CustomProviderDeleted(val providerId: String) : CloudVaultMutation()
    data class McpServerUpserted(val serverId: String) : CloudVaultMutation()
    data class McpServerDeleted(val serverId: String) : CloudVaultMutation()
    data class GitHubProjectChanged(val projectId: String) : CloudVaultMutation()
    data class SessionConfigUpserted(val sessionId: String) : CloudVaultMutation()
    data class SessionConfigDeleted(val sessionId: String) : CloudVaultMutation()
}

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
        val CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
        val SESSION_PROMPT_CONFIGS = stringPreferencesKey("session_prompt_configs_json")
        val GITHUB_TOKEN_SECRET = stringPreferencesKey("github_pat")
        val LOCAL_PASSCODE_HASH = stringPreferencesKey("local_passcode_hash")
        val GUEST_MODE = booleanPreferencesKey("guest_mode_enabled")
        val PREFERRED_PRIVILEGE_BACKEND = stringPreferencesKey("preferred_privilege_backend")
        val AI_PHONE_CONTROL_ENABLED = booleanPreferencesKey("ai_phone_control_enabled")
        val CONFIRM_SENSITIVE_DEVICE_ACTIONS = booleanPreferencesKey("confirm_sensitive_device_actions")
    }

    private val _mutations = MutableSharedFlow<CloudVaultMutation>(extraBufferCapacity = 64)
    val cloudVaultMutations: Flow<CloudVaultMutation> = _mutations.asSharedFlow()

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
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
    }

    suspend fun setDefaultProviderAndModel(providerId: String, modelId: String) {
        context.settingsStore.edit {
            it[Keys.DEFAULT_PROVIDER] = providerId
            it[Keys.DEFAULT_MODEL] = modelId
        }
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
    }

    suspend fun setSystemPrompt(prompt: String) {
        context.settingsStore.edit {
            it[Keys.SYSTEM_PROMPT] = prompt
        }
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
    }

    suspend fun resetSystemPrompt() {
        context.settingsStore.edit {
            it.remove(Keys.SYSTEM_PROMPT)
        }
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
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
        _mutations.tryEmit(CloudVaultMutation.ProviderChanged(config.providerId))
    }

    suspend fun getProviderConfig(providerId: String): ProviderConfig? {
        return providerConfigsFlow.first()[providerId]
    }

    suspend fun getAllProviderConfigs(): Map<String, ProviderConfig> = providerConfigsFlow.first()

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
        _mutations.tryEmit(CloudVaultMutation.ProviderChanged(providerId))
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

    suspend fun getAllStoredApiKeys(): Map<String, String> {
        val prefs = context.secretsStore.data.first()
        val result = mutableMapOf<String, String>()
        prefs.asMap().forEach { (prefKey, rawVal) ->
            val keyName = prefKey.name
            if (keyName.startsWith("api_key_") && rawVal is String && rawVal.isNotBlank()) {
                val providerId = keyName.removePrefix("api_key_")
                val plain = cryptoManager.decrypt(rawVal)
                if (plain.isNotBlank()) {
                    result[providerId] = plain
                }
            }
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

    val guestModeFlow: Flow<Boolean> = context.settingsStore.data.map { prefs ->
        prefs[Keys.GUEST_MODE] ?: false
    }

    suspend fun setGuestMode(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.GUEST_MODE] = enabled
        }
    }

    val deviceControlSettingsFlow: Flow<DeviceControlSettings> = context.settingsStore.data.map { prefs ->
        val backend = prefs[Keys.PREFERRED_PRIVILEGE_BACKEND]?.let { raw ->
            runCatching { PrivilegeBackend.valueOf(raw) }.getOrNull()
        } ?: PrivilegeBackend.AUTO
        DeviceControlSettings(
            preferredBackend = backend,
            aiPhoneControlEnabled = prefs[Keys.AI_PHONE_CONTROL_ENABLED] ?: true,
            confirmSensitiveDeviceActions = prefs[Keys.CONFIRM_SENSITIVE_DEVICE_ACTIONS] ?: true
        )
    }

    suspend fun setPreferredPrivilegeBackend(backend: PrivilegeBackend) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.PREFERRED_PRIVILEGE_BACKEND] = backend.name
        }
    }

    suspend fun setAiPhoneControlEnabled(enabled: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.AI_PHONE_CONTROL_ENABLED] = enabled
        }
    }

    suspend fun setConfirmSensitiveDeviceActions(confirm: Boolean) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.CONFIRM_SENSITIVE_DEVICE_ACTIONS] = confirm
        }
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
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
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
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
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
        _mutations.tryEmit(CloudVaultMutation.GlobalSettingsOrGitHubAccount)
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
        _mutations.tryEmit(CloudVaultMutation.GitHubProjectChanged(projectId))
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
        _mutations.tryEmit(CloudVaultMutation.McpServerUpserted(server.id))
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
        _mutations.tryEmit(CloudVaultMutation.McpServerDeleted(serverId))
    }

    suspend fun saveMcpAuthToken(serverId: String, token: String) {
        val trimmed = token.trim()
        val encrypted = if (trimmed.isEmpty()) "" else cryptoManager.encrypt(trimmed)
        context.secretsStore.edit { prefs ->
            val key = mcpSecretKeyFor(serverId)
            if (encrypted.isEmpty()) prefs.remove(key) else prefs[key] = encrypted
        }
        _mutations.tryEmit(CloudVaultMutation.McpServerUpserted(serverId))
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

    // --- User-Created Custom Providers ---

    val customProvidersFlow: Flow<List<CustomProviderDefinition>> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[Keys.CUSTOM_PROVIDERS] ?: return@map emptyList()
            runCatching {
                json.decodeFromString<List<CustomProviderDefinition>>(raw)
            }.getOrDefault(emptyList())
        }

    suspend fun getCustomProviders(): List<CustomProviderDefinition> = customProvidersFlow.first()

    suspend fun saveCustomProvider(definition: CustomProviderDefinition) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_PROVIDERS]?.let { raw ->
                runCatching { json.decodeFromString<List<CustomProviderDefinition>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.toMutableList()
            val idx = updated.indexOfFirst { it.id == definition.id }
            if (idx >= 0) updated[idx] = definition else updated.add(definition)
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(updated)
        }
        _mutations.tryEmit(CloudVaultMutation.CustomProviderUpserted(definition))
    }

    suspend fun deleteCustomProvider(providerId: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_PROVIDERS]?.let { raw ->
                runCatching { json.decodeFromString<List<CustomProviderDefinition>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(current.filterNot { it.id == providerId })

            val cfgMap = prefs[Keys.PROVIDER_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ProviderConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            if (providerId in cfgMap) {
                prefs[Keys.PROVIDER_CONFIGS] = json.encodeToString(cfgMap - providerId)
            }
        }
        context.secretsStore.edit { prefs ->
            prefs.remove(secretKeyFor(providerId))
        }
        _mutations.tryEmit(CloudVaultMutation.CustomProviderDeleted(providerId))
    }

    // --- Per-Session System Prompt / Mode Configs ---

    val sessionPromptConfigsFlow: Flow<Map<String, SessionPromptConfig>> =
        context.settingsStore.data.map { prefs ->
            val raw = prefs[Keys.SESSION_PROMPT_CONFIGS] ?: return@map emptyMap()
            runCatching {
                json.decodeFromString<Map<String, SessionPromptConfig>>(raw)
            }.getOrDefault(emptyMap())
        }

    suspend fun getSessionPromptConfig(sessionId: String): SessionPromptConfig? =
        sessionPromptConfigsFlow.first()[sessionId]

    suspend fun getAllSessionPromptConfigs(): Map<String, SessionPromptConfig> =
        sessionPromptConfigsFlow.first()

    suspend fun saveSessionPromptConfig(sessionId: String, config: SessionPromptConfig) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.SESSION_PROMPT_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, SessionPromptConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[Keys.SESSION_PROMPT_CONFIGS] = json.encodeToString(current + (sessionId to config))
        }
        _mutations.tryEmit(CloudVaultMutation.SessionConfigUpserted(sessionId))
    }

    suspend fun deleteSessionPromptConfig(sessionId: String) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.SESSION_PROMPT_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, SessionPromptConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            if (sessionId in current) {
                prefs[Keys.SESSION_PROMPT_CONFIGS] = json.encodeToString(current - sessionId)
            }
        }
        _mutations.tryEmit(CloudVaultMutation.SessionConfigDeleted(sessionId))
    }

    // --- Non-emitting Cloud Vault Restore & Sign-Out Clear Helpers ---

    suspend fun applyCloudGlobalSettings(
        themeMode: ThemeMode?,
        defaultProviderId: String?,
        defaultModelId: String?,
        systemPrompt: String?,
        githubToken: String?,
        githubDefaultOwner: String?,
        githubUserProfileJson: String?
    ) {
        context.settingsStore.edit { prefs ->
            if (themeMode != null) prefs[Keys.THEME_MODE] = themeMode.name
            if (!defaultProviderId.isNullOrBlank()) prefs[Keys.DEFAULT_PROVIDER] = defaultProviderId
            if (defaultModelId != null) prefs[Keys.DEFAULT_MODEL] = defaultModelId
            if (!systemPrompt.isNullOrBlank()) prefs[Keys.SYSTEM_PROMPT] = systemPrompt
            if (!githubDefaultOwner.isNullOrBlank()) prefs[Keys.GITHUB_DEFAULT_OWNER] = githubDefaultOwner
            if (!githubUserProfileJson.isNullOrBlank()) {
                prefs[Keys.GITHUB_USER_PROFILE] = githubUserProfileJson
            }
        }
        if (githubToken != null) {
            val clean = githubToken.trim()
            val enc = if (clean.isEmpty()) "" else cryptoManager.encrypt(clean)
            context.secretsStore.edit { prefs ->
                if (enc.isEmpty()) prefs.remove(Keys.GITHUB_TOKEN_SECRET)
                else prefs[Keys.GITHUB_TOKEN_SECRET] = enc
            }
        }
    }

    suspend fun applyCloudProviderEntries(
        configs: Map<String, ProviderConfig>,
        apiKeys: Map<String, String>
    ) {
        if (configs.isNotEmpty()) {
            context.settingsStore.edit { prefs ->
                val current = prefs[Keys.PROVIDER_CONFIGS]?.let { raw ->
                    runCatching { json.decodeFromString<Map<String, ProviderConfig>>(raw) }
                        .getOrDefault(emptyMap())
                } ?: emptyMap()
                prefs[Keys.PROVIDER_CONFIGS] = json.encodeToString(current + configs)
            }
        }
        if (apiKeys.isNotEmpty()) {
            context.secretsStore.edit { prefs ->
                apiKeys.forEach { (providerId, rawKey) ->
                    val trimmed = rawKey.trim()
                    val key = secretKeyFor(providerId)
                    if (trimmed.isEmpty()) prefs.remove(key)
                    else prefs[key] = cryptoManager.encrypt(trimmed)
                }
            }
        }
    }

    suspend fun applyCloudCustomProviders(customProviders: List<CustomProviderDefinition>) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_PROVIDERS]?.let { raw ->
                runCatching { json.decodeFromString<List<CustomProviderDefinition>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val mergedById = linkedMapOf<String, CustomProviderDefinition>()
            current.forEach { mergedById[it.id] = it }
            customProviders.forEach { mergedById[it.id] = it }
            prefs[Keys.CUSTOM_PROVIDERS] = json.encodeToString(mergedById.values.toList())
        }
    }

    suspend fun applyCloudMcpServers(
        servers: List<McpServerConfig>,
        tokens: Map<String, String>
    ) {
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.MCP_SERVERS]?.let { raw ->
                runCatching { json.decodeFromString<List<McpServerConfig>>(raw) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            val merged = linkedMapOf<String, McpServerConfig>()
            current.forEach { merged[it.id] = it }
            servers.forEach { incoming ->
                val existing = merged[incoming.id]
                merged[incoming.id] = incoming.copy(
                    discoveredTools = existing?.discoveredTools.orEmpty(),
                    serverInfoName = existing?.serverInfoName.orEmpty(),
                    protocolVersion = existing?.protocolVersion.orEmpty()
                )
            }
            prefs[Keys.MCP_SERVERS] = json.encodeToString(merged.values.toList())
        }
        if (tokens.isNotEmpty()) {
            context.secretsStore.edit { prefs ->
                tokens.forEach { (serverId, token) ->
                    val trimmed = token.trim()
                    val key = mcpSecretKeyFor(serverId)
                    if (trimmed.isEmpty()) prefs.remove(key)
                    else prefs[key] = cryptoManager.encrypt(trimmed)
                }
            }
        }
    }

    suspend fun applyCloudGitHubProjects(configs: Map<String, GitHubProjectConfig>) {
        if (configs.isEmpty()) return
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.GITHUB_PROJECT_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, GitHubProjectConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[Keys.GITHUB_PROJECT_CONFIGS] = json.encodeToString(current + configs)
        }
    }

    suspend fun applyCloudSessionPromptConfigs(configs: Map<String, SessionPromptConfig>) {
        if (configs.isEmpty()) return
        context.settingsStore.edit { prefs ->
            val current = prefs[Keys.SESSION_PROMPT_CONFIGS]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, SessionPromptConfig>>(raw) }
                    .getOrDefault(emptyMap())
            } ?: emptyMap()
            prefs[Keys.SESSION_PROMPT_CONFIGS] = json.encodeToString(current + configs)
        }
    }

    suspend fun clearAccountVaultSecretsForSignOut() {
        context.secretsStore.edit { prefs ->
            val keysToRemove = prefs.asMap().keys.filter { k ->
                k.name.startsWith("api_key_") ||
                    k.name.startsWith("mcp_token_") ||
                    k == Keys.GITHUB_TOKEN_SECRET
            }
            keysToRemove.forEach { prefs.remove(it) }
        }
        context.settingsStore.edit { prefs ->
            prefs.remove(Keys.GITHUB_USER_PROFILE)
            prefs.remove(Keys.GITHUB_DEFAULT_OWNER)
            prefs.remove(Keys.PROVIDER_CONFIGS)
            prefs.remove(Keys.CUSTOM_PROVIDERS)
            prefs.remove(Keys.MCP_SERVERS)
            prefs.remove(Keys.GITHUB_PROJECT_CONFIGS)
            prefs.remove(Keys.SESSION_PROMPT_CONFIGS)
        }
        saveAppAccountSession(AppAccountSession())
    }
}
