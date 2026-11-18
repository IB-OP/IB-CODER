package com.example.ui.screens.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.data.remote.CloudAccountVaultRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("DiscouragedApi")
fun resolveDefaultWebClientId(context: Context): String? {
    val resId = context.resources.getIdentifier(
        "default_web_client_id",
        "string",
        context.packageName
    )
    if (resId == 0) return null
    return runCatching { context.getString(resId).trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

sealed interface GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Error(val message: String) : GoogleSignInOutcome
}

suspend fun performInteractiveGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    cloudAccountVaultRepository: CloudAccountVaultRepository
): GoogleSignInOutcome {
    val activity = context.findActivity()
        ?: return GoogleSignInOutcome.Error("Cannot start Google Sign-In without an active screen.")

    val clientId = resolveDefaultWebClientId(context)
        ?: return GoogleSignInOutcome.Error(
            "Google Sign-In is unavailable in this build (missing app/google-services.json). Continue in Guest Mode instead."
        )

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
