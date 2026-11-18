package com.example.ui.screens.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.CloudAccountVaultRepository
import com.example.data.remote.RuntimeFirebaseConfig
import com.example.data.remote.RuntimeFirebaseManager
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.serialization.json.Json

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

suspend fun resolveDefaultWebClientId(
    context: Context,
    settingsDataStore: SettingsDataStore? = null
): String? {
    val bundled = RuntimeFirebaseManager.resolveBundledWebClientId(context)
    if (bundled != null) {
        RuntimeFirebaseManager.ensureFirebaseInitialized(context, null)
        return bundled
    }
    val runtimeConfig = settingsDataStore?.getRuntimeFirebaseConfig() ?: return null
    val initialized = RuntimeFirebaseManager.ensureFirebaseInitialized(context, runtimeConfig)
    return if (initialized) runtimeConfig.webClientId else null
}

suspend fun isGoogleSignInConfigured(
    context: Context,
    settingsDataStore: SettingsDataStore? = null
): Boolean = resolveDefaultWebClientId(context, settingsDataStore) != null

suspend fun importAndInitializeFirebaseConfig(
    context: Context,
    rawJsonOrBase64: String,
    settingsDataStore: SettingsDataStore,
    cloudAccountVaultRepository: CloudAccountVaultRepository,
    json: Json
): Result<RuntimeFirebaseConfig> {
    val parsed = RuntimeFirebaseManager.parseGoogleServicesJson(
        rawInput = rawJsonOrBase64,
        preferredPackageName = context.packageName,
        json = json
    ).getOrElse { return Result.failure(it) }

    val initialized = RuntimeFirebaseManager.ensureFirebaseInitialized(context, parsed)
    if (!initialized) {
        return Result.failure(IllegalStateException("Could not initialize Firebase with this configuration."))
    }
    settingsDataStore.saveRuntimeFirebaseConfig(parsed)
    cloudAccountVaultRepository.refreshFirebaseBindings()
    return Result.success(parsed)
}

sealed interface GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class MissingConfig(val message: String) : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

suspend fun performInteractiveGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    cloudAccountVaultRepository: CloudAccountVaultRepository,
    settingsDataStore: SettingsDataStore? = null
): GoogleSignInOutcome {
    val activity = context.findActivity()
        ?: return GoogleSignInOutcome.Error("Cannot start Google Sign-In without an active screen.")

    val clientId = resolveDefaultWebClientId(context, settingsDataStore)
        ?: return GoogleSignInOutcome.MissingConfig(
            "This APK was built without app/google-services.json (for example, a public GitHub Actions build missing the GOOGLE_SERVICES_JSON secret). Import google-services.json below to enable Google Sign-In, or Continue as Guest."
        )

    cloudAccountVaultRepository.refreshFirebaseBindings()

    return try {
        val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        val result = credentialManager.getCredential(activity, request)
        val cred = result.credential
        if (cred is CustomCredential &&
            cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
            val signInRes = cloudAccountVaultRepository.signInWithGoogleIdToken(googleCred.idToken)
            signInRes.fold(
                onSuccess = { GoogleSignInOutcome.Success },
                onFailure = { err ->
                    GoogleSignInOutcome.Error(err.message ?: "Firebase authentication failed.")
                }
            )
        } else {
            GoogleSignInOutcome.Error("Unsupported credential type returned.")
        }
    } catch (_: GetCredentialCancellationException) {
        GoogleSignInOutcome.Cancelled
    } catch (e: Exception) {
        GoogleSignInOutcome.Error(e.message ?: "Google Sign-In failed.")
    }
}
