package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.RuntimeFirebaseManager
import com.example.data.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseConfigTest {

    private val sampleGoogleServicesJson = """
        {
          "project_info": {
            "project_number": "1234567890",
            "project_id": "sample-coder-project",
            "storage_bucket": "sample-coder-project.firebasestorage.app"
          },
          "client": [
            {
              "client_info": {
                "mobilesdk_app_id": "1:1234567890:android:abcdef123456",
                "android_client_info": {
                  "package_name": "com.example.aicoder"
                }
              },
              "oauth_client": [
                {
                  "client_id": "1234567890-android.apps.googleusercontent.com",
                  "client_type": 1,
                  "android_info": {
                    "package_name": "com.example.aicoder",
                    "certificate_hash": "001122334455"
                  }
                },
                {
                  "client_id": "1234567890-webclient.apps.googleusercontent.com",
                  "client_type": 3
                }
              ],
              "api_key": [
                {
                  "current_key": "AIzaSampleKey123"
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `bundled google-services xml resolves non-blank default_web_client_id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clientId = RuntimeFirebaseManager.resolveBundledWebClientId(context)
        assertNotNull(clientId)
        assertTrue(clientId!!.endsWith(".apps.googleusercontent.com"))
    }

    @Test
    fun `runtime firebase manager parses raw json and base64 encoded google-services`() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val fromRaw = RuntimeFirebaseManager.parseGoogleServicesJson(
            rawInput = sampleGoogleServicesJson,
            preferredPackageName = "com.example.aicoder",
            json = json
        )
        assertTrue(fromRaw.isSuccess)
        val cfg = fromRaw.getOrThrow()
        assertEquals("sample-coder-project", cfg.projectId)
        assertEquals("1:1234567890:android:abcdef123456", cfg.applicationId)
        assertEquals("AIzaSampleKey123", cfg.apiKey)
        assertEquals("1234567890-webclient.apps.googleusercontent.com", cfg.webClientId)

        val encoded = Base64.encodeToString(sampleGoogleServicesJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val fromBase64 = RuntimeFirebaseManager.parseGoogleServicesJson(
            rawInput = encoded,
            preferredPackageName = "com.example.aicoder",
            json = json
        )
        assertTrue(fromBase64.isSuccess)
        assertEquals(cfg, fromBase64.getOrThrow())
    }

    @Test
    fun `settings datastore persists runtime firebase config across sign-out`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
        val fakeCrypto = object : KeystoreCryptoManager() {
            override suspend fun encrypt(plaintext: String): String =
                Base64.encodeToString(plaintext.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            override suspend fun decrypt(encoded: String): String =
                if (encoded.isBlank()) "" else Base64.decode(encoded, Base64.NO_WRAP).toString(Charsets.UTF_8)
        }
        val store = SettingsDataStore(context, fakeCrypto, json)

        val cfg = RuntimeFirebaseManager.parseGoogleServicesJson(
            rawInput = sampleGoogleServicesJson,
            preferredPackageName = "com.example.aicoder",
            json = json
        ).getOrThrow()

        store.saveRuntimeFirebaseConfig(cfg)
        assertEquals(cfg, store.runtimeFirebaseConfigFlow.first())

        store.clearAccountVaultSecretsForSignOut()
        assertEquals(cfg, store.getRuntimeFirebaseConfig())

        store.clearRuntimeFirebaseConfig()
    }
}
