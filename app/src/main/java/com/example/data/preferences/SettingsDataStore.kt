package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.KeystoreCryptoManager
import com.example.domain.model.AppSettings
import com.example.domain.model.ProviderConfig
import com.example.domain.model.ThemeMode
import com.example.domain.prompt.DefaultSystemPrompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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
    }

    private fun secretKeyFor(providerId: String): Preferences.Key<String> =
        stringPreferencesKey("api_key_$providerId")

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
}
