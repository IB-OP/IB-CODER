package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RuntimeFirebaseConfig(
    val projectId: String,
    val projectNumber: String = "",
    val applicationId: String,
    val apiKey: String,
    val storageBucket: String = "",
    val webClientId: String
)

object RuntimeFirebaseManager {

    fun parseGoogleServicesJson(
        rawInput: String,
        preferredPackageName: String,
        json: Json
    ): Result<RuntimeFirebaseConfig> {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Paste google-services.json or choose a .json file."))
        }

        val jsonText = if (trimmed.startsWith("{")) {
            trimmed
        } else {
            runCatching {
                val decoded = Base64.decode(trimmed.replace(Regex("\\s+"), ""), Base64.DEFAULT)
                decoded.toString(Charsets.UTF_8).trim()
            }.getOrElse {
                return Result.failure(
                    IllegalArgumentException("Input is neither valid JSON nor Base64-encoded google-services.json.")
                )
            }
        }

        if (!jsonText.startsWith("{")) {
            return Result.failure(IllegalArgumentException("Invalid google-services.json format."))
        }

        return runCatching {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val projectInfo = root["project_info"]?.jsonObject
                ?: error("Missing 'project_info' block in google-services.json.")
            val projectId = projectInfo["project_id"]?.jsonPrimitive?.content?.trim().orEmpty()
            val projectNumber = projectInfo["project_number"]?.jsonPrimitive?.content?.trim().orEmpty()
            val storageBucket = projectInfo["storage_bucket"]?.jsonPrimitive?.content?.trim().orEmpty()

            if (projectId.isBlank()) error("Missing project_info.project_id in google-services.json.")

            val clients = (root["client"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            if (clients.isEmpty()) error("Missing 'client' array in google-services.json.")

            val selectedClient = clients.firstOrNull { client ->
                val pkg = client["client_info"]?.jsonObject
                    ?.get("android_client_info")?.jsonObject
                    ?.get("package_name")?.jsonPrimitive?.content?.trim()
                pkg == preferredPackageName
            } ?: clients.first()

            val clientInfo = selectedClient["client_info"]?.jsonObject
            val appId = clientInfo?.get("mobilesdk_app_id")?.jsonPrimitive?.content?.trim().orEmpty()
            if (appId.isBlank()) error("Missing client_info.mobilesdk_app_id in google-services.json.")

            val apiKey = (selectedClient["api_key"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.firstNotNullOfOrNull { it["current_key"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotEmpty) }
                .orEmpty()
            if (apiKey.isBlank()) error("Missing api_key[0].current_key in google-services.json.")

            val primaryOAuthClients = (selectedClient["oauth_client"] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()
            val otherOAuthClients = (selectedClient["services"]?.jsonObject
                ?.get("appinvite_service")?.jsonObject
                ?.get("other_platform_oauth_client") as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                .orEmpty()

            val webClientId = (primaryOAuthClients + otherOAuthClients)
                .firstOrNull { obj -> obj["client_type"]?.jsonPrimitive?.intOrNull == 3 }
                ?.get("client_id")?.jsonPrimitive?.content?.trim()
                .orEmpty()

            if (webClientId.isBlank()) {
                error("Missing Web OAuth client (client_type: 3) in google-services.json. Enable Google Sign-In in Firebase Auth first.")
            }

            RuntimeFirebaseConfig(
                projectId = projectId,
                projectNumber = projectNumber,
                applicationId = appId,
                apiKey = apiKey,
                storageBucket = storageBucket,
                webClientId = webClientId
            )
        }
    }

    @SuppressLint("DiscouragedApi")
    fun resolveBundledWebClientId(context: Context): String? {
        val candidates = listOf(context, context.applicationContext).distinct()
        for (ctx in candidates) {
            val resId = ctx.resources.getIdentifier(
                "default_web_client_id",
                "string",
                ctx.packageName
            )
            if (resId != 0) {
                val value = runCatching { ctx.getString(resId).trim() }.getOrNull()
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    fun ensureFirebaseInitialized(
        context: Context,
        runtimeConfig: RuntimeFirebaseConfig?
    ): Boolean {
        val appContext = context.applicationContext
        if (runCatching { FirebaseApp.getApps(appContext).isNotEmpty() }.getOrDefault(false)) {
            return true
        }
        if (runtimeConfig == null) return false

        return runCatching {
            val builder = FirebaseOptions.Builder()
                .setProjectId(runtimeConfig.projectId)
                .setApplicationId(runtimeConfig.applicationId)
                .setApiKey(runtimeConfig.apiKey)
            if (runtimeConfig.projectNumber.isNotBlank()) {
                builder.setGcmSenderId(runtimeConfig.projectNumber)
            }
            if (runtimeConfig.storageBucket.isNotBlank()) {
                builder.setStorageBucket(runtimeConfig.storageBucket)
            }
            FirebaseApp.initializeApp(appContext, builder.build())
            true
        }.getOrDefault(false)
    }
}
